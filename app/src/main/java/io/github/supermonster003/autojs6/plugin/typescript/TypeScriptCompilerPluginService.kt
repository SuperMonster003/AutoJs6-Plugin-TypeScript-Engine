package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

class TypeScriptCompilerPluginService : Service() {

    private lateinit var compilerHost: TypeScriptWebViewCompilerHost

    @Volatile
    private var transportProbeCoordinator: CompilationTransportProbeCoordinator? = null

    @Volatile
    private var lifecycleTelemetryEnabledForTesting = false

    @Volatile
    private var onDestroyEnteredForTesting = false

    @Volatile
    private var transportProbeTelemetryEnabledForTesting = false

    private val binder = object : ITypeScriptCompilerPlugin.Stub() {
        override fun onTransact(
            code: Int,
            data: Parcel,
            reply: Parcel?,
            flags: Int,
        ): Boolean {
            val originalPosition = data.dataPosition()
            val containsForbiddenFileDescriptors = try {
                when (code) {
                    TRANSACTION_PREWARM,
                    TRANSACTION_START_COMPILATION,
                    TRANSACTION_CANCEL_COMPILATION,
                    -> {
                        data.enforceInterface(ITypeScriptCompilerPlugin.DESCRIPTOR)
                        when (code) {
                            TRANSACTION_START_COMPILATION ->
                                data.firstTypedBundleContainsFileDescriptors()
                            else -> data.hasFileDescriptors()
                        }
                    }
                    else -> false
                }
            } finally {
                data.setDataPosition(originalPosition)
            }
            if (!containsForbiddenFileDescriptors) {
                return super.onTransact(code, data, reply, flags)
            }

            val response = requireNotNull(reply) {
                "Synchronous compiler transaction has no reply Parcel."
            }
            return when (code) {
                TRANSACTION_PREWARM -> {
                    response.writeBundleResult(
                        invalidMetadataPrewarmResult(),
                    )
                    true
                }
                TRANSACTION_START_COMPILATION -> {
                    response.writeBundleResult(
                        R0CompilationDecision(
                            requestId = "",
                            accepted = false,
                            errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            errorMessage =
                                "Compilation request contains forbidden file descriptors.",
                        ).toBundle(),
                    )
                    true
                }
                TRANSACTION_CANCEL_COMPILATION -> {
                    response.writeNoException()
                    response.writeInt(0)
                    true
                }
                else -> error("Unexpected compiler transaction code: $code")
            }
        }

        override fun getContractVersion() = TypeScriptCompilerContract.VERSION

        override fun getContractHash() = TypeScriptCompilerContract.HASH

        override fun getCompilerInfo() = compilerInfoWithLifecycleTelemetry()

        override fun prewarm(options: Bundle?): Bundle {
            val envelope = options.toPrewarmEnvelopeOrNull()
            val metadata = BinderMetadataValidator.validate(options)
            options.discardIfInvalid(metadata)
            val rejection = CompilerPrewarmGate.validate(
                CompilerPrewarmAdmission(
                    options = envelope,
                    primitiveMetadataOnly = metadata.primitiveMetadataOnly,
                    metadataWithinBudget = metadata.metadataWithinBudget,
                ),
            )
            if (rejection != null) {
                return compilerInfoWithLifecycleTelemetry().apply {
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_CODE,
                        rejection.errorCode,
                    )
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                        rejection.errorMessage,
                    )
                }
            }

            val callingUid = Binder.getCallingUid()
            val lifecycleTestRequested =
                options.requestsHeldLifecycleProbeForTesting(callingUid)
            val correlationTestRequested =
                options.requestsWrongRequestIdFailureForTesting(
                    callingUid,
                )
            if (lifecycleTestRequested) {
                lifecycleTelemetryEnabledForTesting = true
            }
            val timeoutMs = requireNotNull(envelope).timeoutMs
            val outcome = when {
                lifecycleTestRequested ->
                    compilerHost.holdProbeUntilCloseForTesting(timeoutMs)
                correlationTestRequested ->
                    compilerHost.probeWithWrongRequestIdFailureForTesting(timeoutMs)
                else -> compilerHost.probe(timeoutMs)
            }
            return compilerInfoWithLifecycleTelemetry().apply {
                if (!outcome.succeeded) {
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_CODE,
                        outcome.errorCode,
                    )
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                        outcome.errorMessage,
                    )
                }
                if (lifecycleTestRequested) {
                    putCloseReportForTesting(compilerHost.closeReportForTesting())
                }
                if (correlationTestRequested) {
                    putCorrelationReportForTesting(
                        compilerHost.correlationReportForTesting(),
                    )
                }
            }
        }

        override fun startCompilation(
            request: Bundle?,
            inputArchive: ParcelFileDescriptor?,
            outputArchive: ParcelFileDescriptor?,
            callback: ITypeScriptCompilationCallback?,
        ): Bundle {
            var archiveOwnershipTransferred = false
            return try {
                val callingUid = Binder.getCallingUid()
                val controls = request.transportProbeControlsOrNull(callingUid)
                val envelope = request.toEnvelopeOrNull()
                val metadata = BinderMetadataValidator.validate(request)
                request.discardIfInvalid(metadata)
                val archiveDescriptors = ArchiveFileDescriptorValidator.validate(
                    inputArchive = inputArchive,
                    outputArchive = outputArchive,
                )
                val rejection = R0CompilationGate.reject(
                    R0CompilationAdmission(
                        request = envelope,
                        primitiveMetadataOnly = metadata.primitiveMetadataOnly,
                        metadataWithinBudget = metadata.metadataWithinBudget,
                        inputArchiveValid = archiveDescriptors.inputArchiveValid,
                        outputArchiveValid = archiveDescriptors.outputArchiveValid,
                        callbackValid = callback?.asBinder()?.isBinderAlive == true,
                        backingFilesDistinct = archiveDescriptors.backingFilesDistinct,
                    ),
                )
                if (
                    controls == null ||
                    rejection.errorCode != TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE
                ) {
                    return rejection.toBundle()
                }
                val coordinator =
                    transportProbeCoordinator ?: return rejection.toBundle()

                val admission = runCatching {
                    coordinator.admit(
                        request = requireNotNull(envelope),
                        callingUid = callingUid,
                        inputArchive = requireNotNull(inputArchive),
                        outputArchive = requireNotNull(outputArchive),
                        callback = requireNotNull(callback),
                        controls = controls,
                    )
                }.getOrElse { error ->
                    TransportProbeAdmissionDecision(
                        requestId = envelope?.requestId.orEmpty(),
                        accepted = false,
                        jobToken = null,
                        errorCode = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                        errorMessage = "Transport probe admission failed: ${error.safeMessage()}",
                    )
                }
                archiveOwnershipTransferred = admission.accepted
                if (admission.accepted) {
                    transportProbeTelemetryEnabledForTesting = true
                }
                admission.toBundle()
            } finally {
                if (!archiveOwnershipTransferred) {
                    inputArchive.closeQuietly()
                    outputArchive.closeQuietly()
                }
            }
        }

        override fun cancelCompilation(cancellation: Bundle?): Boolean {
            val callingUid = Binder.getCallingUid()
            if (!BuildConfig.DEBUG || callingUid != applicationInfo.uid) return false
            val coordinator = transportProbeCoordinator ?: return false
            val metadata = BinderMetadataValidator.validate(cancellation)
            cancellation.discardIfInvalid(metadata)
            if (!metadata.primitiveMetadataOnly || !metadata.metadataWithinBudget) return false
            val envelope = cancellation.toCancellationEnvelopeOrNull() ?: return false
            if (
                envelope.contractVersion != TypeScriptCompilerContract.VERSION ||
                envelope.contractHash != TypeScriptCompilerContract.HASH ||
                envelope.requestId.isBlank() ||
                envelope.requestId.length > TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS ||
                envelope.jobToken.isBlank() ||
                envelope.jobToken.length > TypeScriptCompilerContract.MAX_JOB_TOKEN_CHARS
            ) {
                return false
            }
            val probeCommand = runCatching {
                cancellation?.getString(
                    CompilerTransportProbeProtocol.KEY_CANCELLATION_COMMAND,
                )
            }.getOrNull()
            if (probeCommand == CompilerTransportProbeProtocol.COMMAND_ACKNOWLEDGE) {
                return coordinator.acknowledge(
                    callingUid = callingUid,
                    requestId = envelope.requestId,
                    jobToken = envelope.jobToken,
                )
            }
            return coordinator.cancel(
                callingUid = callingUid,
                requestId = envelope.requestId,
                jobToken = envelope.jobToken,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        TypeScriptWebViewCompilerHost.prepareCompilerProcess()
        compilerHost = TypeScriptWebViewCompilerHost(this)
        if (BuildConfig.DEBUG) {
            transportProbeCoordinator = CompilationTransportProbeCoordinator()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (BuildConfig.DEBUG) {
            onDestroyEnteredForTesting = true
        }
        try {
            val coordinator = transportProbeCoordinator
            transportProbeCoordinator = null
            coordinator?.close()
            if (::compilerHost.isInitialized) {
                compilerHost.close()
            }
        } finally {
            super.onDestroy()
        }
    }

    private fun invalidMetadataPrewarmResult() =
        compilerInfoWithLifecycleTelemetry().apply {
            putString(
                TypeScriptCompilerContract.KEY_ERROR_CODE,
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            )
            putString(
                TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                "Prewarm options contain forbidden file descriptors.",
            )
        }

    private fun compilerInfoWithLifecycleTelemetry(): Bundle =
        TypeScriptPluginMetadata.compilerInfo(compilerHost.snapshot()).apply {
            val coordinator = transportProbeCoordinator
            if (BuildConfig.DEBUG && lifecycleTelemetryEnabledForTesting) {
                putLifecycleTelemetryForTesting(
                    compilerHost.lifecycleTelemetryForTesting(),
                )
            }
            if (
                BuildConfig.DEBUG &&
                transportProbeTelemetryEnabledForTesting &&
                coordinator != null
            ) {
                putInt(
                    CompilerTransportProbeProtocol.KEY_ACTIVE_JOB_COUNT,
                    coordinator.activeJobCount(),
                )
            }
        }

    private fun Bundle?.requestsHeldLifecycleProbeForTesting(
        callingUid: Int,
    ): Boolean {
        if (
            !BuildConfig.DEBUG ||
            callingUid != applicationInfo.uid ||
            this == null
        ) {
            return false
        }
        return runCatching {
            getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_HOLD_UNTIL_SERVICE_DESTROYED,
                false,
            )
        }.getOrDefault(false)
    }

    private fun Bundle?.transportProbeControlsOrNull(
        callingUid: Int,
    ): CompilerTransportProbeControls? {
        if (
            !BuildConfig.DEBUG ||
            callingUid != applicationInfo.uid ||
            this == null
        ) {
            return null
        }
        return runCatching {
            if (!getBoolean(CompilerTransportProbeProtocol.KEY_ENABLED, false)) {
                return null
            }
            val timeoutMs = getLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, -1L)
            if (
                timeoutMs !in
                CompilerTransportProbeProtocol.MIN_TIMEOUT_MS..
                TypeScriptCompilerContract.MAX_TIMEOUT_MS
            ) {
                return null
            }
            val mode = getString(
                CompilerTransportProbeProtocol.KEY_MODE,
                CompilerTransportProbeProtocol.MODE_COMPLETE,
            )
            if (
                mode != CompilerTransportProbeProtocol.MODE_COMPLETE &&
                mode != CompilerTransportProbeProtocol.MODE_HOLD
            ) {
                return null
            }
            val completionDelayMs = getLong(
                CompilerTransportProbeProtocol.KEY_COMPLETION_DELAY_MS,
                CompilerTransportProbeProtocol.DEFAULT_COMPLETION_DELAY_MS,
            )
            if (
                completionDelayMs !in
                1L..CompilerTransportProbeProtocol.MAX_COMPLETION_DELAY_MS
            ) {
                return null
            }
            CompilerTransportProbeControls(
                mode = mode,
                completionDelayMs = completionDelayMs,
            )
        }.getOrNull()
    }

    private fun Bundle?.requestsWrongRequestIdFailureForTesting(
        callingUid: Int,
    ): Boolean {
        if (
            !BuildConfig.DEBUG ||
            callingUid != applicationInfo.uid ||
            this == null
        ) {
            return false
        }
        return runCatching {
            getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_INJECT_WRONG_REQUEST_ID_FAILURE,
                false,
            )
        }.getOrDefault(false)
    }

    private fun Bundle.putCorrelationReportForTesting(
        report: CompilerHostCorrelationTestReport?,
    ) {
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CORRELATION_REPORT_AVAILABLE,
            report != null,
        )
        report ?: return
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CORRELATION_FAULT_INJECTED,
            report.faultInjected,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CORRELATION_FAULT_IGNORED,
            report.faultIgnored,
        )
    }

    private fun Bundle.putLifecycleTelemetryForTesting(
        telemetry: CompilerHostLifecycleTelemetry,
    ) {
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_LIVE_TELEMETRY_ENABLED,
            true,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_LIVE_CLOSED,
            telemetry.closed,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_WEB_VIEW,
            telemetry.hasWebView,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_NATIVE_PORT,
            telemetry.hasNativePort,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_PENDING_PROBE,
            telemetry.hasPendingProbe,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_LIVE_WORKER_READY,
            telemetry.workerReady,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_LIVE_CLOSE_MESSAGE_POSTED,
            telemetry.closeMessagePosted,
        )
    }

    private fun Bundle.putCloseReportForTesting(
        report: CompilerHostCloseReport?,
    ) {
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_SERVICE_ON_DESTROY_ENTERED,
            onDestroyEnteredForTesting,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_REPORT_AVAILABLE,
            report != null,
        )
        report ?: return
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_PENDING_PROBE,
            report.hadPendingProbe,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_WEB_VIEW,
            report.hadWebView,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_NATIVE_PORT,
            report.hadNativePort,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_READY_WORKER,
            report.hadReadyWorker,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_SERVICE_WORKER_CLIENT,
            report.hadServiceWorkerClient,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_MESSAGE_POSTED,
            report.closeMessagePosted,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_NATIVE_PORT_CLOSED,
            report.nativePortClosed,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_WEB_VIEW_DESTROYED,
            report.webViewDestroyed,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_SERVICE_WORKER_CLIENT_CLEARED,
            report.serviceWorkerClientCleared,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HANDLER_CALLBACKS_CLEARED,
            report.handlerCallbacksCleared,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_CLOSED,
            report.closed,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_PENDING_PROBE_RELEASED,
            report.pendingProbeReleased,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_WEB_VIEW_RELEASED,
            report.webViewReleased,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_NATIVE_PORT_RELEASED,
            report.nativePortReleased,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_WORKER_STATE_RESET,
            report.workerStateReset,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_SESSION_STATE_RESET,
            report.sessionStateReset,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_TIMEOUT_RECOVERY_RESET,
            report.timeoutRecoveryReset,
        )
    }

    private fun Parcel.firstTypedBundleContainsFileDescriptors(): Boolean {
        val present = readInt()
        if (present == 0) return false
        check(present == 1) {
            "Compiler transaction has an invalid Bundle presence marker."
        }

        val bundleStart = dataPosition()
        val payloadLength = readInt()
        check(payloadLength >= 0 && payloadLength % Int.SIZE_BYTES == 0) {
            "Compiler transaction has an invalid Bundle payload length."
        }
        val bundleLength = if (payloadLength == 0) {
            Int.SIZE_BYTES
        } else {
            Math.addExact(payloadLength, Int.SIZE_BYTES * 2)
        }
        check(bundleStart <= dataSize() - bundleLength) {
            "Compiler transaction has a truncated Bundle payload."
        }

        val bundleParcel = Parcel.obtain()
        return try {
            bundleParcel.appendFrom(this, bundleStart, bundleLength)
            bundleParcel.hasFileDescriptors()
        } finally {
            bundleParcel.recycle()
        }
    }

    private fun Parcel.writeBundleResult(result: Bundle) {
        writeNoException()
        writeInt(1)
        result.writeToParcel(this, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
    }

    private fun Bundle?.toPrewarmEnvelopeOrNull(): CompilerPrewarmEnvelope? = runCatching {
        this ?: return null
        CompilerPrewarmEnvelope(
            contractVersion = getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, -1),
            contractHash = getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH).orEmpty(),
            timeoutMs = getLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, -1L),
        )
    }.getOrNull()

    private fun Bundle?.toEnvelopeOrNull(): R0CompilationEnvelope? = runCatching {
        this ?: return null
        R0CompilationEnvelope(
            contractVersion = getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, -1),
            contractHash = getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH).orEmpty(),
            requestId = getString(TypeScriptCompilerContract.KEY_REQUEST_ID).orEmpty(),
            projectId = getString(TypeScriptCompilerContract.KEY_PROJECT_ID).orEmpty(),
            entrySourceUri = getString(TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI).orEmpty(),
            targetProfile = getString(TypeScriptCompilerContract.KEY_TARGET_PROFILE).orEmpty(),
            compilationMode = getString(TypeScriptCompilerContract.KEY_COMPILATION_MODE).orEmpty(),
            compilerOptionsJson = getString(
                TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON,
                "{}",
            ),
            inputArchiveTransportVersion = getInt(
                TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
                -1,
            ),
            outputArchiveTransportVersion = getInt(
                TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
                -1,
            ),
            timeoutMs = getLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, -1L),
        )
    }.getOrNull()

    private fun Bundle?.toCancellationEnvelopeOrNull(): CancellationEnvelope? = runCatching {
        this ?: return null
        CancellationEnvelope(
            contractVersion = getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, -1),
            contractHash = getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH).orEmpty(),
            requestId = getString(TypeScriptCompilerContract.KEY_REQUEST_ID).orEmpty(),
            jobToken = getString(TypeScriptCompilerContract.KEY_JOB_TOKEN).orEmpty(),
        )
    }.getOrNull()

    private fun R0CompilationDecision.toBundle() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, accepted)
        putString(TypeScriptCompilerContract.KEY_ERROR_CODE, errorCode)
        putString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE, errorMessage)
    }

    private fun TransportProbeAdmissionDecision.toBundle() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, accepted)
        if (accepted) {
            putString(
                TypeScriptCompilerContract.KEY_JOB_TOKEN,
                requireNotNull(jobToken),
            )
        } else {
            putString(TypeScriptCompilerContract.KEY_ERROR_CODE, errorCode)
            putString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE, errorMessage)
        }
    }

    private fun Bundle?.discardIfInvalid(validation: BinderMetadataValidation) {
        if (
            validation.primitiveMetadataOnly &&
            validation.metadataWithinBudget
        ) {
            return
        }
        runCatching { this?.clear() }
    }

    private fun ParcelFileDescriptor?.closeQuietly() {
        runCatching { this?.close() }
    }

    private companion object {
        const val TRANSACTION_PREWARM = IBinder.FIRST_CALL_TRANSACTION + 3
        const val TRANSACTION_START_COMPILATION = IBinder.FIRST_CALL_TRANSACTION + 4
        const val TRANSACTION_CANCEL_COMPILATION = IBinder.FIRST_CALL_TRANSACTION + 5
    }
}

private data class CancellationEnvelope(
    val contractVersion: Int,
    val contractHash: String,
    val requestId: String,
    val jobToken: String,
)

private fun Throwable.safeMessage(): String =
    message?.take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS)
        ?: javaClass.simpleName
