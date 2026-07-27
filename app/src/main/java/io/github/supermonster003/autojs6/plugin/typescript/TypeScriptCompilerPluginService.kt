package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.Intent
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
    private var lifecycleTelemetryEnabledForTesting = false

    @Volatile
    private var onDestroyEnteredForTesting = false

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

            val lifecycleTestRequested =
                options.requestsHeldLifecycleProbeForTesting()
            if (lifecycleTestRequested) {
                lifecycleTelemetryEnabledForTesting = true
            }
            val timeoutMs = requireNotNull(envelope).timeoutMs
            val outcome = if (lifecycleTestRequested) {
                compilerHost.holdProbeUntilCloseForTesting(timeoutMs)
            } else {
                compilerHost.probe(timeoutMs)
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
            }
        }

        override fun startCompilation(
            request: Bundle?,
            inputArchive: ParcelFileDescriptor?,
            outputArchive: ParcelFileDescriptor?,
            callback: ITypeScriptCompilationCallback?,
        ): Bundle = try {
            val envelope = request.toEnvelopeOrNull()
            val metadata = BinderMetadataValidator.validate(request)
            request.discardIfInvalid(metadata)
            val archiveDescriptors = ArchiveFileDescriptorValidator.validate(
                inputArchive = inputArchive,
                outputArchive = outputArchive,
            )
            R0CompilationGate.reject(
                R0CompilationAdmission(
                    request = envelope,
                    primitiveMetadataOnly = metadata.primitiveMetadataOnly,
                    metadataWithinBudget = metadata.metadataWithinBudget,
                    inputArchiveValid = archiveDescriptors.inputArchiveValid,
                    outputArchiveValid = archiveDescriptors.outputArchiveValid,
                    callbackValid = callback?.asBinder()?.isBinderAlive == true,
                    backingFilesDistinct = archiveDescriptors.backingFilesDistinct,
                ),
            ).toBundle()
        } finally {
            inputArchive.closeQuietly()
            outputArchive.closeQuietly()
        }

        override fun cancelCompilation(cancellation: Bundle?): Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        TypeScriptWebViewCompilerHost.prepareCompilerProcess()
        compilerHost = TypeScriptWebViewCompilerHost(this)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        if (BuildConfig.DEBUG) {
            onDestroyEnteredForTesting = true
        }
        try {
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
            if (BuildConfig.DEBUG && lifecycleTelemetryEnabledForTesting) {
                putLifecycleTelemetryForTesting(
                    compilerHost.lifecycleTelemetryForTesting(),
                )
            }
        }

    private fun Bundle?.requestsHeldLifecycleProbeForTesting(): Boolean {
        if (!BuildConfig.DEBUG || this == null) return false
        return runCatching {
            getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_HOLD_UNTIL_SERVICE_DESTROYED,
                false,
            )
        }.getOrDefault(false)
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

    private fun R0CompilationDecision.toBundle() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, accepted)
        putString(TypeScriptCompilerContract.KEY_ERROR_CODE, errorCode)
        putString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE, errorMessage)
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
