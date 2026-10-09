package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.os.Process
import android.os.SystemClock
import android.system.Os
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

class TypeScriptCompilerPluginService : Service() {

    private lateinit var compilerHost: TypeScriptWebViewCompilerHost
    private lateinit var admissionArbiter: R2GlobalAdmissionArbiter

    @Volatile
    private var transportProbeCoordinator: CompilationTransportProbeCoordinator? = null

    @Volatile
    private var r2CompilationCoordinator: R2CompilationCoordinator? = null

    @Volatile
    private var lifecycleTelemetryEnabledForTesting = false

    @Volatile
    private var onDestroyEnteredForTesting = false

    @Volatile
    private var transportProbeTelemetryEnabledForTesting = false

    @Volatile
    private var quarantinedBySupervisorFence = false

    @Volatile
    private var supervisorRecoveryScheduled = false

    private val quarantineReleaseWatcherActive = AtomicBoolean(false)
    private val declarationProfileFingerprintCache =
        ConcurrentHashMap<R2ProjectProfile, String>()

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
            if (
                options.requestsProductionSupervisorOwnerForTesting(
                    callingUid,
                )
            ) {
                R2CompilerProcessSupervisorClient
                    .enableAutomaticTerminationForTesting()
                transportProbeTelemetryEnabledForTesting = true
            }
            options.supervisorReconnectPauseForTesting(callingUid)
                ?.let { paused ->
                    R2CompilerProcessSupervisorClient
                        .setReconnectPausedForTesting(paused)
                    transportProbeTelemetryEnabledForTesting = true
                }
            options.supervisorRetiredCallbackCommandForTesting(callingUid)
                ?.let { command ->
                    R2CompilerProcessSupervisorClient
                        .executeRetiredCallbackCommandForTesting(command)
                    transportProbeTelemetryEnabledForTesting = true
                    return compilerInfoWithLifecycleTelemetry()
                }
            val processSupervisorCommandRequested =
                options.requestsProcessSupervisorTerminationForTesting(callingUid)
            if (
                options.requestsProcessSupervisorTelemetryForTesting(callingUid) ||
                processSupervisorCommandRequested
            ) {
                transportProbeTelemetryEnabledForTesting = true
            }
            if (processSupervisorCommandRequested) {
                val terminationAccepted =
                    Binder.getCallingPid() != Process.myPid() &&
                        admissionArbiter.requiresExternalSupervision() &&
                        R2CompilerProcessEpoch.scheduleExactSelfTermination(
                            context = this@TypeScriptCompilerPluginService,
                            request = requireNotNull(options),
                        )
                return compilerInfoWithLifecycleTelemetry().apply {
                    putBoolean(
                        R2CompilerProcessSupervisorProtocol.KEY_TERMINATION_ACCEPTED,
                        terminationAccepted,
                    )
                }
            }
            if (
                !BuildConfig.DEBUG &&
                !R2CompilerProcessSupervisorClient.awaitReady(SUPERVISOR_READY_WAIT_MILLIS)
            ) {
                return compilerInfoWithLifecycleTelemetry().apply {
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_CODE,
                        TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    )
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                        "Compiler process supervisor registration is not ready.",
                    )
                }
            }
            val lifecycleTestRequested =
                options.requestsHeldLifecycleProbeForTesting(callingUid)
            val correlationTestRequested =
                options.requestsWrongRequestIdFailureForTesting(
                    callingUid,
                )
            if (lifecycleTestRequested) {
                lifecycleTelemetryEnabledForTesting = true
            }
            val globalAdmission = admissionArbiter.admitInternal(Any())
            val globalLease = globalAdmission.lease
                ?: return compilerInfoWithLifecycleTelemetry().apply {
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_CODE,
                        globalAdmission.errorCode,
                    )
                    putString(
                        TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                        globalAdmission.errorMessage,
                    )
                }
            val timeoutMs = requireNotNull(envelope).timeoutMs
            val outcome = try {
                when {
                    lifecycleTestRequested ->
                        compilerHost.holdProbeUntilCloseForTesting(timeoutMs)
                    correlationTestRequested ->
                        compilerHost.probeWithWrongRequestIdFailureForTesting(timeoutMs)
                    else -> compilerHost.probe(timeoutMs)
                }
            } finally {
                check(admissionArbiter.releaseInternal(globalLease)) {
                    "Compiler prewarm lost its global admission lease."
                }
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
                val transportControls = request.transportProbeControlsOrNull(callingUid)
                val debugR2Requested =
                    request.r2DebugCompilationRequested()
                val debugR2FormalAuthorityError =
                    if (debugR2Requested) {
                        request.r2DebugFormalInputAuthorityError(
                            callingUid = callingUid,
                        )
                    } else {
                        null
                    }
                val debugR2Controls =
                    request.r2DebugCompilationControlsOrNull(
                        callingUid = callingUid,
                    )
                val r2ControlPlaneRoute = request.r2CompilationControlPlaneRoute(
                    debugControlPlaneRequested = debugR2Requested,
                    debugControlPlaneAccepted = debugR2Controls != null,
                )
                val publicR2Requested =
                    r2ControlPlaneRoute == R2CompilationControlPlaneRoute.PUBLIC
                val publicR2Classification =
                    if (publicR2Requested) {
                        request.r2PublicCompilationClassification()
                    } else {
                        null
                    }
                val publicR2Controls =
                    publicR2Classification?.controls
                val externalEvidenceAttestation =
                    if (
                        BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE &&
                        publicR2Controls != null
                    ) {
                        r2ExternalEvidenceAttestationOrNull(
                            callingUid = callingUid,
                            inputArchive = inputArchive,
                        )
                    } else {
                        null
                    }
                val cleanupObservedPublicR2Controls =
                    externalEvidenceAttestation?.let { attestation ->
                        R2ExternalCapacityEvidenceProtocol
                            .cleanupEvidenceControlsOrNull(
                                baseControls = publicR2Controls,
                                attestation = attestation,
                            )
                    } ?: publicR2Controls
                val externalCapacityEvidenceRequested =
                    BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE &&
                        request.requestsR2ExternalCapacityEvidence()
                val externalCapacityEvidenceControls =
                    if (
                        publicR2Controls != null &&
                        externalCapacityEvidenceRequested
                    ) {
                        request.r2ExternalCapacityEvidenceControlsOrNull(
                            baseControls = publicR2Controls,
                            attestation = externalEvidenceAttestation,
                        )
                    } else {
                        null
                    }
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
                if (debugR2Requested && debugR2Controls == null) {
                    return R0CompilationDecision(
                        requestId = envelope?.requestId.orEmpty()
                            .take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                        accepted = false,
                        errorCode = debugR2FormalAuthorityError ?:
                            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        errorMessage =
                            "Debug R2 profile authority is incomplete, malformed, or inactive.",
                    ).toBundle()
                }
                if (r2ControlPlaneRoute == R2CompilationControlPlaneRoute.CONFLICT) {
                    return R0CompilationDecision(
                        requestId = envelope?.requestId.orEmpty()
                            .take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                        accepted = false,
                        errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        errorMessage =
                            "Debug and public R2 control planes cannot be combined.",
                    ).toBundle()
                }
                if (
                    publicR2Requested &&
                    publicR2Controls == null
                ) {
                    return R0CompilationDecision(
                        requestId = envelope?.requestId.orEmpty()
                            .take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                        accepted = false,
                        errorCode =
                            publicR2Classification?.errorCode
                                ?: TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        errorMessage =
                            publicR2Classification?.errorMessage
                                ?: "Public R2 metadata is incomplete or malformed.",
                    ).toBundle()
                }
                if (
                    publicR2Requested &&
                    externalCapacityEvidenceRequested &&
                    externalCapacityEvidenceControls == null
                ) {
                    return R0CompilationDecision(
                        requestId = envelope?.requestId.orEmpty()
                            .take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                        accepted = false,
                        errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        errorMessage =
                            "Public R2 metadata or external evidence authority is " +
                                "incomplete, malformed, or unsupported.",
                    ).toBundle()
                }
                val r2Controls = debugR2Controls ?:
                    externalCapacityEvidenceControls ?:
                    cleanupObservedPublicR2Controls
                if (
                    (transportControls == null && r2Controls == null) ||
                    (transportControls != null && r2Controls != null) ||
                    rejection.errorCode != TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE
                ) {
                    return rejection.toBundle()
                }
                if (r2Controls != null) {
                    if (
                        r2Controls.origin != R2CompilationControlOrigin.DEBUG_PROBE &&
                        !R2CompilerProcessSupervisorClient.awaitReady(
                            SUPERVISOR_READY_WAIT_MILLIS,
                        )
                    ) {
                        return R2CompilationAdmissionDecision(
                            requestId = requireNotNull(envelope).requestId,
                            accepted = false,
                            jobToken = null,
                            errorCode =
                                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                            errorMessage =
                                "Compiler process supervisor registration is not ready.",
                        ).toBundle()
                    }
                    val coordinator =
                        r2CompilationCoordinator ?: return rejection.toBundle()
                    val admission = runCatching {
                        coordinator.admit(
                            request = requireNotNull(envelope),
                            callingUid = callingUid,
                            inputArchive = requireNotNull(inputArchive),
                            outputArchive = requireNotNull(outputArchive),
                            callback = requireNotNull(callback),
                            controls = r2Controls,
                        )
                    }.getOrElse { error ->
                        R2CompilationAdmissionDecision(
                            requestId = envelope?.requestId.orEmpty(),
                            accepted = false,
                            jobToken = null,
                            errorCode = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                            errorMessage =
                                "R2 compilation admission failed: ${error.safeMessage()}",
                        )
                    }
                    archiveOwnershipTransferred = admission.accepted
                    if (admission.accepted) {
                        transportProbeTelemetryEnabledForTesting = true
                    }
                    return admission.toBundle()
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
                        controls = requireNotNull(transportControls),
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
            val r2Coordinator = r2CompilationCoordinator
            val debugSameUid =
                BuildConfig.DEBUG && callingUid == applicationInfo.uid
            val r1Coordinator =
                if (debugSameUid) transportProbeCoordinator else null
            if (r2Coordinator == null && r1Coordinator == null) return false
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
            val probeCommand =
                if (debugSameUid) {
                    runCatching {
                        cancellation?.getString(
                            CompilerTransportProbeProtocol.KEY_CANCELLATION_COMMAND,
                        )
                    }.getOrNull()
                } else {
                    null
                }
            if (
                probeCommand != null &&
                probeCommand != CompilerTransportProbeProtocol.COMMAND_ACKNOWLEDGE
            ) {
                return false
            }
            val publicOperation = runCatching {
                cancellation?.getString(
                    TypeScriptR2Contract.KEY_CANCELLATION_OPERATION,
                    TypeScriptR2Contract.CANCELLATION_OPERATION_CANCEL,
                )
            }.getOrNull() ?: return false
            if (
                probeCommand == CompilerTransportProbeProtocol.COMMAND_ACKNOWLEDGE
            ) {
                return r2Coordinator?.acknowledge(
                    callingUid = callingUid,
                    requestId = envelope.requestId,
                    jobToken = envelope.jobToken,
                ) == true ||
                    r1Coordinator?.acknowledge(
                        callingUid = callingUid,
                        requestId = envelope.requestId,
                        jobToken = envelope.jobToken,
                    ) == true
            }
            return when (publicOperation) {
                TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE ->
                    r2Coordinator?.acknowledge(
                        callingUid = callingUid,
                        requestId = envelope.requestId,
                        jobToken = envelope.jobToken,
                    ) == true
                TypeScriptR2Contract.CANCELLATION_OPERATION_CANCEL ->
                    r2Coordinator?.cancel(
                        callingUid = callingUid,
                        requestId = envelope.requestId,
                        jobToken = envelope.jobToken,
                    ) == true ||
                        r1Coordinator?.cancel(
                            callingUid = callingUid,
                            requestId = envelope.requestId,
                            jobToken = envelope.jobToken,
                        ) == true
                else -> false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        R2CompilerBootTelemetry.serviceCreateStarted()
        if (R2CompilerProcessQuarantine.hasMarker(this)) {
            quarantinedBySupervisorFence = true
            startQuarantineReleaseWatcher()
            return
        }
        R2CompilerProcessSupervisorClient.start(this)
        admissionArbiter = R2ProcessAdmissionAuthority.openServiceEpoch()
        if (admissionArbiter.requiresExternalSupervision()) {
            throw R2ServiceShutdownCleanupUnprovenException(
                "Compiler process poison survived a same-process service recreation.",
            )
        }
        try {
            R2PrivateTransientOrphanScavenger.scavenge(
                inputStagingDirectory = getDir(
                    R2PrivateTransientOrphanScavenger.INPUT_STAGING_DIRECTORY_NAME,
                    MODE_PRIVATE,
                ),
                outputStagingDirectory = getDir(
                    R2PrivateTransientOrphanScavenger.OUTPUT_STAGING_DIRECTORY_NAME,
                    MODE_PRIVATE,
                ),
            )
        } catch (failure: R2PrivateTransientCleanupUnprovenException) {
            admissionArbiter.failClosed()
            throw R2ServiceShutdownCleanupUnprovenException(
                "Compiler startup could not prove private transient cleanup.",
                failure,
            )
        }
        TypeScriptWebViewCompilerHost.prepareCompilerProcess()
        compilerHost = TypeScriptWebViewCompilerHost(this)
        compilerHost.installExternalTeardownObserver(
            admissionArbiter::failClosed,
        )
        r2CompilationCoordinator = R2CompilationCoordinator(
            context = this,
            compilerHost = compilerHost,
            admissionArbiter = admissionArbiter,
        )
        if (BuildConfig.DEBUG) {
            transportProbeCoordinator =
                CompilationTransportProbeCoordinator(admissionArbiter)
        }
        compilerHost.primeSessionForBoot()
        R2CompilerBootTelemetry.serviceCreateCompleted()
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (quarantinedBySupervisorFence) {
            /*
             * onCreate normally owns the watcher. Retrying here covers a transient
             * Thread.start() failure and every additional BIND_AUTO_CREATE attempt. Recovery
             * scheduling stays on that watcher: marker reconciliation may wait for the
             * cross-process marker lock, and a quarantined service must return its null binding
             * without blocking the main thread on that lock.
             */
            startQuarantineReleaseWatcher()
            return null
        }
        return binder
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::compilerHost.isInitialized) {
            compilerHost.discardResidentProjectSessionsForMemoryPressure()
        }
    }

    override fun onDestroy() {
        if (quarantinedBySupervisorFence) {
            quarantineReleaseWatcherActive.set(false)
            super.onDestroy()
            return
        }
        if (BuildConfig.DEBUG) {
            onDestroyEnteredForTesting = true
        }
        val r2Coordinator = r2CompilationCoordinator
        val r1Coordinator = transportProbeCoordinator

        var teardownFailure: Throwable? = null
        fun attemptTeardown(block: () -> Unit) {
            try {
                block()
            } catch (failure: Throwable) {
                teardownFailure?.addSuppressed(failure)
                    ?: run { teardownFailure = failure }
            }
        }
        attemptTeardown {
            if (::admissionArbiter.isInitialized) admissionArbiter.close()
        }
        attemptTeardown { r2Coordinator?.beginServiceShutdown() }
        attemptTeardown { r1Coordinator?.close() }
        attemptTeardown {
            if (::compilerHost.isInitialized) {
                val closeReport = compilerHost.close()
                if (!closeReport.cleanupProven) {
                    throw R2ServiceShutdownCleanupUnprovenException(
                        "Compiler host teardown did not prove complete resource release.",
                    )
                }
            }
        }
        attemptTeardown { r2Coordinator?.finishServiceShutdown() }
        attemptTeardown {
            if (
                ::admissionArbiter.isInitialized &&
                admissionArbiter.requiresExternalSupervision()
            ) {
                throw R2ServiceShutdownCleanupUnprovenException(
                    "Compiler process requires external supervision after service teardown.",
                )
            }
        }
        r2CompilationCoordinator = null
        transportProbeCoordinator = null
        attemptTeardown { super.onDestroy() }
        teardownFailure?.let { failure ->
            if (::admissionArbiter.isInitialized) {
                admissionArbiter.failClosed()
            }
            throw failure
        }
    }

    /**
     * A null binding cannot be upgraded in place. Once the external supervisor clears the durable
     * fence, recycle this inert successor exactly once so AMS can satisfy every still-active
     * BIND_AUTO_CREATE connection from a normally initialized process epoch.
     */
    private fun startQuarantineReleaseWatcher() {
        if (!quarantineReleaseWatcherActive.compareAndSet(false, true)) return
        val started = runCatching {
            Thread(
                {
                    var nextScheduleAttempt = 0L
                    while (quarantineReleaseWatcherActive.get()) {
                        if (!R2CompilerProcessQuarantine.hasMarker(this)) {
                            if (
                                quarantineReleaseWatcherActive.compareAndSet(
                                    true,
                                    false,
                                )
                            ) {
                                Process.killProcess(Process.myPid())
                            }
                            return@Thread
                        }
                        val now = SystemClock.elapsedRealtime()
                        if (
                            !supervisorRecoveryScheduled &&
                            now >= nextScheduleAttempt
                        ) {
                            scheduleSupervisorRecoveryIfNeeded()
                            nextScheduleAttempt =
                                now + SUPERVISOR_JOB_SCHEDULE_RETRY_MILLIS
                        }
                        SystemClock.sleep(QUARANTINE_RELEASE_POLL_MILLIS)
                    }
                },
                "typescript-r2-quarantine-release",
            ).apply {
                isDaemon = true
                start()
            }
        }.isSuccess
        if (!started) {
            quarantineReleaseWatcherActive.set(false)
        }
    }

    @Synchronized
    private fun scheduleSupervisorRecoveryIfNeeded() {
        if (supervisorRecoveryScheduled) return
        if (
            R2CompilerProcessSupervisorJobScheduler
                .scheduleIfQuarantined(this)
        ) {
            supervisorRecoveryScheduled = true
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
            if (!containsKey(TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT)) {
                // The host snapshot is filled by the boot preload; resolve through the shared
                // declaration cache when a caller arrives before that thread finishes.
                declarationProfileFingerprintOrNull(
                    R2CompilationProfileRegistry.PROVISIONAL_RHINO.profile,
                )?.let { fingerprint ->
                    putString(
                        TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
                        fingerprint,
                    )
                }
            }
            declarationProfileFingerprintOrNull(
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2.profile,
            )?.let { fingerprint ->
                putString(
                    TypeScriptR2Contract.KEY_NODE_DECLARATION_PROFILE_FINGERPRINT,
                    fingerprint,
                )
            }
            val coordinator = transportProbeCoordinator
            val r2Coordinator = r2CompilationCoordinator
            if (BuildConfig.DEBUG && lifecycleTelemetryEnabledForTesting) {
                putLifecycleTelemetryForTesting(
                    compilerHost.lifecycleTelemetryForTesting(),
                )
            }
            if (
                BuildConfig.DEBUG &&
                transportProbeTelemetryEnabledForTesting &&
                coordinator != null &&
                Binder.getCallingUid() == applicationInfo.uid
            ) {
                putInt(
                    CompilerTransportProbeProtocol.KEY_ACTIVE_JOB_COUNT,
                    admissionArbiter.activeAcceptedJobCount(),
                )
                putBoolean(
                    CompilerTransportProbeProtocol.KEY_TERMINAL_INTENT_CLAIMED,
                    coordinator.hasClaimedTerminalForTesting(),
                )
                putBoolean(
                    R2CompilationProbeProtocol.KEY_STOP_CAUSE_SELECTED,
                    r2Coordinator?.hasSelectedStopForTesting() == true,
                )
                R2CompilerProcessEpoch.identity().putInto(this)
                val supervisorTelemetry =
                    R2CompilerProcessSupervisorClient.telemetryForTesting()
                putBoolean(
                    R2CompilerProcessSupervisorProtocol.KEY_REQUIRES_EXTERNAL_SUPERVISION,
                    admissionArbiter.requiresExternalSupervision(),
                )
                putBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_REGISTRY_CONNECTED,
                    supervisorTelemetry.registryConnected,
                )
                putBoolean(
                    R2CompilerProcessSupervisorControlProtocol.KEY_REGISTRATION_READY,
                    supervisorTelemetry.registrationReady,
                )
                putBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_AUTOMATIC_TERMINATION_ENABLED,
                    supervisorTelemetry.automaticTerminationEnabled,
                )
                putBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RECONNECT_PAUSED,
                    supervisorTelemetry.reconnectPaused,
                )
                supervisorTelemetry.registeredSupervisorPid?.let { pid ->
                    putInt(
                        R2CompilerProcessSupervisorControlProtocol
                            .KEY_REGISTERED_SUPERVISOR_PID,
                        pid,
                    )
                }
                supervisorTelemetry.registeredSupervisorStartTimeTicks
                    ?.let { startTimeTicks ->
                        putLong(
                            R2CompilerProcessSupervisorControlProtocol
                                .KEY_REGISTERED_SUPERVISOR_START_TIME_TICKS,
                            startTimeTicks,
                        )
                    }
                supervisorTelemetry.connectionGeneration?.let { generation ->
                    putLong(
                        R2CompilerProcessSupervisorControlProtocol
                            .KEY_CONNECTION_GENERATION,
                        generation,
                    )
                }
                putBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CALLBACK_REPLAY_ARMED,
                    supervisorTelemetry.retiredCallbackReplayArmed,
                )
                supervisorTelemetry.retiredCallbacksReplayed?.let { replayed ->
                    putBoolean(
                        R2CompilerProcessSupervisorControlProtocol
                            .KEY_RETIRED_CALLBACKS_REPLAYED,
                        replayed,
                    )
                }
                supervisorTelemetry.retiredCallbackStatePreserved
                    ?.let { statePreserved ->
                        putBoolean(
                            R2CompilerProcessSupervisorControlProtocol
                                .KEY_RETIRED_CALLBACK_STATE_PRESERVED,
                            statePreserved,
                        )
                    }
                supervisorTelemetry.retiredConnectionGeneration
                    ?.let { generation ->
                        putLong(
                            R2CompilerProcessSupervisorControlProtocol
                                .KEY_RETIRED_CONNECTION_GENERATION,
                            generation,
                        )
                    }
                supervisorTelemetry.replayTargetConnectionGeneration
                    ?.let { generation ->
                        putLong(
                            R2CompilerProcessSupervisorControlProtocol
                                .KEY_REPLAY_TARGET_CONNECTION_GENERATION,
                            generation,
                        )
                    }
                supervisorTelemetry.retiredCallbackDeliveryCount
                    ?.let { deliveryCount ->
                        putInt(
                            R2CompilerProcessSupervisorControlProtocol
                                .KEY_RETIRED_CALLBACK_DELIVERY_COUNT,
                            deliveryCount,
                        )
                    }
            }
            if (BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
                val attestation = r2ExternalEvidenceAttestationOrNull(
                    callingUid = Binder.getCallingUid(),
                    inputArchive = null,
                )
                if (
                    attestation != null &&
                    R2ExternalCapacityEvidenceProtocol
                        .isExactEvidenceCaller(attestation)
                ) {
                    r2Coordinator
                        ?.externalPlusOneCleanupEvidenceState()
                        ?.putInto(this)
                    r2Coordinator
                        ?.externalNaturalPhaseTimeoutEvidenceState()
                        ?.putInto(this)
                }
            }
        }

    private fun Bundle?.requestsProductionSupervisorOwnerForTesting(
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
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_ENABLE_AUTOMATIC_TERMINATION_FOR_TESTING,
                false,
            )
        }.getOrDefault(false)
    }

    private fun Bundle?.supervisorReconnectPauseForTesting(
        callingUid: Int,
    ): Boolean? {
        if (
            !BuildConfig.DEBUG ||
            callingUid != applicationInfo.uid ||
            this == null ||
            !containsKey(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_SET_RECONNECT_PAUSED_FOR_TESTING,
            )
        ) {
            return null
        }
        return runCatching {
            getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_SET_RECONNECT_PAUSED_FOR_TESTING,
            )
        }.getOrNull()
    }

    private fun Bundle?.supervisorRetiredCallbackCommandForTesting(
        callingUid: Int,
    ): String? {
        if (
            !BuildConfig.DEBUG ||
            callingUid != applicationInfo.uid ||
            this == null
        ) {
            return null
        }
        return runCatching {
            getString(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_RETIRED_CALLBACK_COMMAND_FOR_TESTING,
            )?.takeIf { command ->
                command ==
                    R2CompilerProcessSupervisorControlProtocol
                        .COMMAND_ARM_RETIRED_CALLBACKS ||
                    command ==
                    R2CompilerProcessSupervisorControlProtocol
                        .COMMAND_REPLAY_RETIRED_CALLBACKS ||
                    command ==
                    R2CompilerProcessSupervisorControlProtocol
                        .COMMAND_RESET_RETIRED_CALLBACKS
            }
        }.getOrNull()
    }

    private fun Bundle?.requestsProcessSupervisorTelemetryForTesting(
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
                R2CompilerProcessSupervisorProtocol.KEY_TELEMETRY_ENABLED,
                false,
            )
        }.getOrDefault(false)
    }

    private fun Bundle?.requestsProcessSupervisorTerminationForTesting(
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
            getString(R2CompilerProcessSupervisorProtocol.KEY_COMMAND) ==
                R2CompilerProcessSupervisorProtocol.COMMAND_TERMINATE_POISONED_EPOCH
        }.getOrDefault(false)
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
            val terminalCleanupDelayMs = getLong(
                CompilerTransportProbeProtocol.KEY_TERMINAL_CLEANUP_DELAY_MS,
                0L,
            )
            if (
                terminalCleanupDelayMs !in
                0L..CompilerTransportProbeProtocol.MAX_TERMINAL_CLEANUP_DELAY_MS
            ) {
                return null
            }
            val forceCleanupUnproven = getBoolean(
                CompilerTransportProbeProtocol.KEY_FORCE_CLEANUP_UNPROVEN,
                false,
            )
            CompilerTransportProbeControls(
                mode = mode,
                completionDelayMs = completionDelayMs,
                terminalCleanupDelayMs = terminalCleanupDelayMs,
                forceCleanupUnproven = forceCleanupUnproven,
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

    private fun Bundle?.r2DebugCompilationControlsOrNull(
        callingUid: Int,
    ): R2CompilationControls? {
        if (
            !BuildConfig.DEBUG ||
            callingUid != applicationInfo.uid ||
            this == null
        ) {
            return null
        }
        return runCatching {
            R2CompilationProbeProtocol.requireEnabled(
                present = containsKey(R2CompilationProbeProtocol.KEY_ENABLED),
                value = rawDebugR2ValueOrNull(R2CompilationProbeProtocol.KEY_ENABLED),
            )
            val inputArchiveSchemaRevision = requireDebugR2Value<Int>(
                TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            )
            if (
                inputArchiveSchemaRevision !=
                TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION
            ) {
                return null
            }
            if (
                requireDebugR2Value<Int>(
                    TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
                ) !=
                TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION ||
                requireDebugR2Value<Int>(
                    TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION,
                ) != TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION
            ) {
                return null
            }
            val inputArchiveSha256 = requireDebugR2Value<String>(
                R2CompilationProbeProtocol.KEY_INPUT_ARCHIVE_SHA256,
            )
            val requestManifestSha256 = requireDebugR2Value<String>(
                R2CompilationProbeProtocol.KEY_REQUEST_MANIFEST_SHA256,
            )
            val compilerDistributionFingerprint = requireDebugR2Value<String>(
                TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
            )
            val declarationsFingerprint = requireDebugR2Value<String>(
                TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
            )
            val declarationProfileFingerprint = requireDebugR2Value<String>(
                TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            )
            val runtimeCapabilitiesFingerprint = requireDebugR2Value<String>(
                TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
            )
            val targetProfile =
                requireDebugR2Value<String>(TypeScriptCompilerContract.KEY_TARGET_PROFILE)
            val compatibilityProfileRevision =
                R2CompilationProfileRegistry.requireContractValue(targetProfile).profileRevision
            val profileAuthority = R2CompilationProfileRegistry.requireExactRuntimeAuthority(
                contractValue = targetProfile,
                profileSchemaRevision = requireDebugR2Value<Int>(
                    TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
                ),
                profileRevision = R2CompilationProbeProtocol.requireProfileRevision(
                    present = containsKey(R2CompilationProbeProtocol.KEY_PROFILE_REVISION),
                    value = rawDebugR2ValueOrNull(
                        R2CompilationProbeProtocol.KEY_PROFILE_REVISION,
                    ),
                    compatibilityRevision = compatibilityProfileRevision,
                ),
                runtimeCapabilitiesFingerprint = runtimeCapabilitiesFingerprint,
            )
            R2CompilationProfileRegistry.requireEndToEndAdmitted(profileAuthority)
            val supportedDeclarationProfileFingerprint =
                declarationProfileFingerprintOrNull(profileAuthority.profile)
            val workerSpinMilliseconds = optionalDebugR2Value(
                R2CompilationProbeProtocol.KEY_WORKER_SPIN_MILLISECONDS,
                0,
            )
            if (
                workerSpinMilliseconds !in
                0..R2CompilationProbeProtocol.MAX_WORKER_SPIN_MILLISECONDS
            ) {
                return null
            }
            val workerSpinPhase = when (workerSpinMilliseconds) {
                0 -> {
                    val phase = optionalDebugR2Value(
                        R2CompilationProbeProtocol.KEY_WORKER_SPIN_PHASE,
                        "",
                    )
                    if (phase !in setOf("", "none")) {
                        return null
                    }
                    null
                }
                else -> R2ProjectExecutionPhase.fromProtocolValue(
                    requireDebugR2Value<String>(
                        R2CompilationProbeProtocol.KEY_WORKER_SPIN_PHASE,
                    ),
                )
            }
            val expandedWorkerCapacity = optionalDebugR2Value(
                R2CompilationProbeProtocol.KEY_EXPANDED_WORKER_CAPACITY,
                false,
            )
            val terminalCleanupDelayMilliseconds = optionalDebugR2Value(
                R2CompilationProbeProtocol.KEY_TERMINAL_CLEANUP_DELAY_MILLISECONDS,
                0,
            )
            if (
                terminalCleanupDelayMilliseconds !in
                0..R2CompilationProbeProtocol.MAX_TERMINAL_CLEANUP_DELAY_MILLISECONDS
            ) {
                return null
            }
            R2Digests.requireLowerSha256(
                inputArchiveSha256,
                "R2 input archive SHA-256",
            )
            R2Digests.requireLowerSha256(
                requestManifestSha256,
                "R2 request manifest SHA-256",
            )
            R2Digests.requireLowerSha256(
                compilerDistributionFingerprint,
                "R2 compiler distribution fingerprint",
            )
            R2Digests.requireLowerSha256(
                declarationsFingerprint,
                "R2 declarations fingerprint",
            )
            R2Digests.requireLowerSha256(
                declarationProfileFingerprint,
                "R2 declaration profile fingerprint",
            )
            R2Digests.requireLowerSha256(
                runtimeCapabilitiesFingerprint,
                "R2 runtime capabilities fingerprint",
            )
            if (
                !R2Digests.equal(
                    runtimeCapabilitiesFingerprint,
                    profileAuthority.runtimeCapabilitiesFingerprint,
                ) ||
                supportedDeclarationProfileFingerprint == null ||
                !R2Digests.equal(
                    declarationProfileFingerprint,
                    supportedDeclarationProfileFingerprint,
                )
            ) {
                return null
            }
            R2CompilationControls(
                inputArchiveSchemaRevision = inputArchiveSchemaRevision,
                inputArchiveSha256 = inputArchiveSha256,
                requestManifestSha256 = requestManifestSha256,
                compilerDistributionFingerprint = compilerDistributionFingerprint,
                declarationsFingerprint = declarationsFingerprint,
                declarationProfileFingerprint = declarationProfileFingerprint,
                runtimeCapabilitiesFingerprint = runtimeCapabilitiesFingerprint,
                origin = R2CompilationControlOrigin.DEBUG_PROBE,
                profileAuthority = profileAuthority,
                workerSpinMilliseconds = workerSpinMilliseconds,
                workerSpinPhase = workerSpinPhase,
                expandedWorkerCapacity = expandedWorkerCapacity,
                terminalCleanupDelayMilliseconds =
                    terminalCleanupDelayMilliseconds,
            )
        }.getOrNull()
    }

    private fun Bundle?.r2DebugCompilationRequested(): Boolean {
        this ?: return false
        return runCatching {
            R2CompilationProbeProtocol.requestsControlPlane(keySet())
        }.getOrDefault(false)
    }

    private fun Bundle?.r2DebugFormalInputAuthorityError(
        callingUid: Int,
    ): String? {
        if (
            !BuildConfig.DEBUG ||
            callingUid != applicationInfo.uid ||
            this == null
        ) {
            return TypeScriptCompilerContract.ERROR_INVALID_REQUEST
        }
        return runCatching {
            val inputArchiveSchemaRevision = requireDebugR2Value<Int>(
                TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            )
            val declarationProfileFingerprint = requireDebugR2Value<String>(
                TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            )
            R2Digests.requireLowerSha256(
                declarationProfileFingerprint,
                "R2 declaration profile fingerprint",
            )
            val targetProfile = requireDebugR2Value<String>(
                TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            )
            val compatibilityProfileRevision =
                R2CompilationProfileRegistry.requireContractValue(targetProfile).profileRevision
            val profileAuthority = R2CompilationProfileRegistry.requireExactRuntimeAuthority(
                contractValue = targetProfile,
                profileSchemaRevision = requireDebugR2Value(
                    TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
                ),
                profileRevision = R2CompilationProbeProtocol.requireProfileRevision(
                    present = containsKey(R2CompilationProbeProtocol.KEY_PROFILE_REVISION),
                    value = rawDebugR2ValueOrNull(
                        R2CompilationProbeProtocol.KEY_PROFILE_REVISION,
                    ),
                    compatibilityRevision = compatibilityProfileRevision,
                ),
                runtimeCapabilitiesFingerprint = requireDebugR2Value(
                    TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
                ),
            )
            val supportedDeclarationProfileFingerprint =
                declarationProfileFingerprintOrNull(profileAuthority.profile)
            R2CompilationProbeProtocol.formalInputAuthorityError(
                inputArchiveSchemaRevision = inputArchiveSchemaRevision,
                declarationProfileFingerprint = declarationProfileFingerprint,
                expectedDeclarationProfileFingerprint =
                    supportedDeclarationProfileFingerprint,
            )
        }.getOrElse {
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST
        }
    }

    private fun declarationProfileFingerprintOrNull(
        profile: R2ProjectProfile,
    ): String? = declarationProfileFingerprintCache[profile] ?:
        compilerHost.snapshot().declarationProfileFingerprint
            ?.takeIf { profile == R2ProjectProfile.PROVISIONAL_RHINO }
            ?.also { fingerprint ->
                declarationProfileFingerprintCache.putIfAbsent(profile, fingerprint)
            } ?: runCatching {
                R2TrustedDeclarations.loadForProfile(
                    this@TypeScriptCompilerPluginService,
                    profile,
                ).profileFingerprint.also { fingerprint ->
                    declarationProfileFingerprintCache.putIfAbsent(profile, fingerprint)
                }
            }.getOrNull()

    @Suppress("DEPRECATION")
    private fun Bundle.rawDebugR2ValueOrNull(key: String): Any? =
        if (containsKey(key)) get(key) else null

    private inline fun <reified T> Bundle.requireDebugR2Value(key: String): T =
        rawDebugR2ValueOrNull(key) as? T
            ?: throw IllegalArgumentException(
                "Private R2 debug metadata '$key' is missing or has an invalid type.",
            )

    private inline fun <reified T> Bundle.optionalDebugR2Value(
        key: String,
        defaultValue: T,
    ): T = if (containsKey(key)) requireDebugR2Value(key) else defaultValue

    private fun Bundle?.r2CompilationControlPlaneRoute(
        debugControlPlaneRequested: Boolean,
        debugControlPlaneAccepted: Boolean,
    ): R2CompilationControlPlaneRoute {
        this ?: return R2CompilationControlPlaneRoute.NONE
        return runCatching {
            R2PublicCompilationProtocol.controlPlaneRoute(
                debugControlPlaneRequested = debugControlPlaneRequested,
                debugControlPlaneAccepted = debugControlPlaneAccepted,
                presentKeys = keySet(),
            )
        }.getOrDefault(R2CompilationControlPlaneRoute.NONE)
    }

    private fun Bundle?.requestsR2ExternalCapacityEvidence(): Boolean =
        runCatching {
            this != null &&
                containsKey(
                    BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY,
                )
        }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun Bundle?.r2ExternalCapacityEvidenceControlsOrNull(
        baseControls: R2CompilationControls,
        attestation: R2ExternalCapacityEvidenceAttestation?,
    ): R2CompilationControls? {
        this ?: return null
        return runCatching {
            R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                baseControls = baseControls,
                scope = getString(
                    BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE_SCOPE_KEY,
                ),
                storedContainerScope =
                    BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE_STORED_SCOPE,
                deflatedExpandedScope =
                    BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE_DEFLATED_SCOPE,
                projectId = getString(TypeScriptCompilerContract.KEY_PROJECT_ID),
                entrySourceUri = getString(
                    TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
                ),
                attestation = attestation ?: return null,
            )
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun r2ExternalEvidenceAttestationOrNull(
        callingUid: Int,
        inputArchive: ParcelFileDescriptor?,
    ): R2ExternalCapacityEvidenceAttestation? {
        if (!BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) return null
        return runCatching {
            val fixtureApplication = packageManager.getApplicationInfo(
                R2ExternalCapacityEvidenceProtocol.FIXTURE_PACKAGE,
                0,
            )
            val packages = packageManager.getPackagesForUid(callingUid)
                ?.toSet()
                .orEmpty()
            val inputArchiveBytes = inputArchive
                ?.fileDescriptor
                ?.takeIf { descriptor -> descriptor.valid() }
                ?.let { descriptor -> Os.fstat(descriptor).st_size }
            R2ExternalCapacityEvidenceAttestation(
                evidenceBuildEnabled =
                    BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE,
                applicationUid = applicationInfo.uid,
                callingUid = callingUid,
                fixtureUid = fixtureApplication.uid,
                callingPackages = packages,
                applicationTestOnly =
                    applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0,
                fixtureTestOnly =
                    fixtureApplication.flags and
                        android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0,
                signaturesMatch =
                    packageManager.checkSignatures(
                        applicationInfo.uid,
                        callingUid,
                    ) == PackageManager.SIGNATURE_MATCH,
                inputArchiveBytes = inputArchiveBytes,
            )
        }.getOrNull()
    }

    private fun Bundle?.r2PublicCompilationClassification():
        R2PublicCompilationClassification {
        this ?: return R2PublicCompilationProtocol.classify(
            metadata = null,
            expectedDeclarationProfileFingerprint = null,
        )
        return runCatching {
            val targetProfile = optionalPublicR2Value<String>(
                TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            )
            val supportedDeclarationProfileFingerprint = targetProfile
                ?.let { contractValue ->
                    runCatching {
                        R2CompilationProfileRegistry.requireContractValue(contractValue)
                    }.getOrNull()
                }
                ?.profile
                ?.let(::declarationProfileFingerprintOrNull)
            R2PublicCompilationProtocol.classify(
                R2PublicCompilationMetadata(
                    inputArchiveSchemaRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
                    ),
                    targetProfile = targetProfile,
                    profileSchemaRevision = optionalPublicR2Value<Int>(
                        TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
                    ),
                    profileRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_PROFILE_REVISION,
                    ),
                    configSchemaRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
                    ),
                    configKind = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_CONFIG_KIND,
                    ),
                    runtimeCapabilitySchemaRevision = optionalPublicR2Value<Int>(
                        TypeScriptCompilerContract
                            .KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION,
                    ),
                    inputArchiveSha256 = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SHA256,
                    ),
                    requestManifestSha256 = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_REQUEST_MANIFEST_SHA256,
                    ),
                    compilerDistributionFingerprint = optionalPublicR2Value<String>(
                        TypeScriptCompilerContract
                            .KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                    ),
                    declarationsFingerprint = optionalPublicR2Value<String>(
                        TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
                    ),
                    declarationProfileFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
                    ),
                    runtimeCapabilitiesFingerprint = optionalPublicR2Value<String>(
                        TypeScriptCompilerContract
                            .KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
                    ),
                    configByteLength = optionalPublicR2Value<Long>(
                        TypeScriptR2Contract.KEY_CONFIG_BYTE_LENGTH,
                    ),
                    configSha256 = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_CONFIG_SHA256,
                    ),
                    configPolicyRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
                    ),
                    configPolicyFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
                    ),
                    packageMetadataSchemaRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_PACKAGE_METADATA_SCHEMA_REVISION,
                    ),
                    packageMetadataPolicyRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_REVISION,
                    ),
                    packageMetadataPolicyFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_FINGERPRINT,
                    ),
                    packageMetadataInventoryFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_PACKAGE_METADATA_INVENTORY_FINGERPRINT,
                    ),
                    dependencyLayerSchemaRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_SCHEMA_REVISION,
                    ),
                    dependencyLayerPolicyRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_REVISION,
                    ),
                    dependencyLayerPolicyFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_FINGERPRINT,
                    ),
                    dependencyLayerInventoryFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_INVENTORY_FINGERPRINT,
                    ),
                    dependencyLayerFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_FINGERPRINT,
                    ),
                    dependencyResolverPolicyRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_REVISION,
                    ),
                    dependencyResolverPolicyFingerprint = optionalPublicR2Value<String>(
                        TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
                    ),
                    projectSessionRevision = optionalPublicR2Value<Int>(
                        TypeScriptR2Contract.KEY_PROJECT_SESSION_REVISION,
                    ),
                ),
                expectedDeclarationProfileFingerprint =
                    supportedDeclarationProfileFingerprint,
            )
        }.getOrElse {
            R2PublicCompilationProtocol.classify(
                metadata = null,
                expectedDeclarationProfileFingerprint = null,
            )
        }
    }

    @Suppress("DEPRECATION")
    private inline fun <reified T> Bundle.optionalPublicR2Value(key: String): T? {
        if (!containsKey(key)) return null
        return get(key) as? T
            ?: throw IllegalArgumentException("Public R2 metadata has an invalid field type.")
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
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_PROTOCOL_EXECUTOR_TERMINATED,
            report.protocolExecutorTerminated,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_RESULT_EXECUTOR_TERMINATED,
            report.resultExecutorTerminated,
        )
        putBoolean(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_RESULT_FINALIZERS_RELEASED,
            report.resultFinalizersReleased,
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
                R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON,
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

    private fun R2CompilationAdmissionDecision.toBundle() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, accepted)
        if (accepted) {
            putString(TypeScriptCompilerContract.KEY_JOB_TOKEN, requireNotNull(jobToken))
            putBoolean(
                TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_REQUIRED,
                true,
            )
            putLong(
                TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_TIMEOUT_MS,
                requireNotNull(acknowledgementTimeoutMs),
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
        const val QUARANTINE_RELEASE_POLL_MILLIS = 25L
        const val SUPERVISOR_JOB_SCHEDULE_RETRY_MILLIS = 1_000L

        /**
         * Release builds wait this long for the supervisor registration before a prewarm or
         * compilation is refused. Registration normally completes well inside the budget; the
         * bound wait only closes the window between a fast service creation and the supervisor
         * process handshake (startup-latency L1-4).
         */
        const val SUPERVISOR_READY_WAIT_MILLIS = 3_000L
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
