package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Production coordinator for the complete S2 -> S4 project pipeline.
 *
 * Capability advertisement remains disabled while the provisional archive/profile limits are
 * replaced. Both the public-shaped preview and the same-UID debug route use the explicit
 * post-return acknowledgement operation, so no callback can race the synchronous admission
 * ticket.
 */
internal class R2CompilationCoordinator(
    context: Context,
    private val compilerHost: TypeScriptWebViewCompilerHost,
    private val admissionArbiter: R2GlobalAdmissionArbiter,
) {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val pipelineExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "typescript-r2-compilation").apply {
                isDaemon = true
            }
        }
    private val deadlineExecutor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "typescript-r2-deadline").apply {
            isDaemon = true
        }
    }.apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
        continueExistingPeriodicTasksAfterShutdownPolicy = false
    }
    private var activeJob: R2CoordinatorJob? = null
    private var lastExternalCleanupPreAcknowledgement:
        R2ExternalPlusOneCleanupPreAcknowledgement? = null
    private var lastExternalCleanupTerminalReceipt:
        R2ExternalPlusOneCleanupTerminalReceipt? = null
    private var lastExternalNaturalPhaseTimeoutReceipt:
        R2ExternalNaturalPhaseTimeoutReceipt? = null
    private var closed = false

    fun hasSelectedStopForTesting(): Boolean = synchronized(lock) {
        activeJob?.stopCause != null
    }

    internal fun externalPlusOneCleanupEvidenceState():
        R2ExternalPlusOneCleanupEvidenceState {
        check(BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
            "External plus-one cleanup evidence is unavailable in this build."
        }
        return synchronized(lock) {
            /*
             * Capture lazily from post-admission metadata reads. During the synchronous
             * startCompilation Binder transaction API 24 can temporarily retain a second
             * transaction-owned duplicate of each incoming descriptor, and its teardown can race
             * the first metadata read even after the ticket has returned. Publish and retain only
             * a stable exact 1/1 ownership snapshot; a later read can recapture while the ticket
             * remains unacknowledged.
             */
            if (lastExternalCleanupPreAcknowledgement == null) {
                activeJob
                    ?.takeIf { job ->
                        job.externalCleanupEvidenceEnabled &&
                            !job.callbackArmed &&
                            job.phase ==
                            R2CoordinatorPhase.RESERVED_UNACKNOWLEDGED &&
                            admissionArbiter.isCurrent(job.lease)
                    }
                    ?.let { job ->
                        val candidate = runCatching {
                            R2ExternalPlusOneCleanupEvidence
                                .capturePreAcknowledgement(
                                    requestId = job.lease.requestId,
                                    jobToken = job.lease.jobToken,
                                    inputFd = job.inputArchive?.fd ?: -1,
                                    outputFd = job.outputArchive?.fd ?: -1,
                                    activeLease = true,
                                )
                        }.getOrNull()
                        lastExternalCleanupPreAcknowledgement = candidate?.takeIf {
                            it.exactPostTransactionOwnershipProven
                        }
                    }
            }
            val preAcknowledgement = lastExternalCleanupPreAcknowledgement
            val currentSnapshot = preAcknowledgement?.let { evidence ->
                runCatching {
                    R2ExternalPlusOneCleanupEvidence.captureCurrentSnapshot(
                        preAcknowledgement = evidence,
                        activeLeaseCount =
                            admissionArbiter.activeAcceptedJobCount(),
                        inputStagingDirectory = appContext.getDir(
                            R2PrivateTransientOrphanScavenger
                                .INPUT_STAGING_DIRECTORY_NAME,
                            Context.MODE_PRIVATE,
                        ),
                        outputStagingDirectory = appContext.getDir(
                            R2PrivateTransientOrphanScavenger
                                .OUTPUT_STAGING_DIRECTORY_NAME,
                            Context.MODE_PRIVATE,
                        ),
                    )
                }.getOrNull()
            }
            R2ExternalPlusOneCleanupEvidenceState(
                preAcknowledgement = preAcknowledgement,
                terminalReceipt = lastExternalCleanupTerminalReceipt,
                currentSnapshot = currentSnapshot,
            )
        }
    }

    internal fun externalNaturalPhaseTimeoutEvidenceState():
        R2ExternalNaturalPhaseTimeoutEvidenceState {
        check(BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
            "External natural phase-timeout evidence is unavailable in this build."
        }
        return synchronized(lock) {
            R2ExternalNaturalPhaseTimeoutEvidenceState(
                receipt = lastExternalNaturalPhaseTimeoutReceipt,
            )
        }
    }

    fun admit(
        request: R0CompilationEnvelope,
        callingUid: Int,
        inputArchive: ParcelFileDescriptor,
        outputArchive: ParcelFileDescriptor,
        callback: ITypeScriptCompilationCallback,
        controls: R2CompilationControls,
    ): R2CompilationAdmissionDecision {
        val callbackBinder = callback.asBinder()
        val authority = try {
            controls.authority(request)
        } catch (error: IllegalArgumentException) {
            return rejected(
                request.requestId,
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "R2 compilation authority is invalid: ${error.safeMessage()}",
            )
        }
        val hostHandle = try {
            compilerHost.newProjectOperationHandle()
        } catch (error: Throwable) {
            return rejected(
                request.requestId,
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "R2 host operation identity is unavailable: ${error.safeMessage()}",
            )
        }
        return synchronized(lock) {
            if (closed) {
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "R2 compilation coordinator has been destroyed.",
                )
            }
            if (!callbackBinder.isBinderAlive) {
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Compilation callback Binder is not alive.",
                )
            }
            val globalAdmission = admissionArbiter.admitJob(
                callingUid = callingUid,
                requestId = request.requestId,
                callbackIdentity = callbackBinder,
                lateUnacknowledgedAcknowledgementAccepted =
                    controls.origin == R2CompilationControlOrigin.DEBUG_PROBE,
            )
            val lease = globalAdmission.lease
                ?: return@synchronized rejected(
                    request.requestId,
                    globalAdmission.errorCode,
                    globalAdmission.errorMessage,
                )
            /*
             * Move slow, non-mutating output validation ahead of the admission boundary. Every
             * ordinary synchronous rejection below still precedes ftruncate, so rejected calls
             * leave caller output untouched.
             */
            val outputPreflightPassed = runCatching {
                val descriptor = outputArchive.fileDescriptor
                Os.fsync(descriptor)
                Os.lseek(descriptor, 0L, OsConstants.SEEK_CUR)
            }.isSuccess
            if (!outputPreflightPassed) {
                check(admissionArbiter.abortJobAdmission(lease))
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                    "R2 compilation output could not be prepared.",
                )
            }
            val deadlineRangeSupported = runCatching {
                Math.addExact(SystemClock.elapsedRealtime(), request.timeoutMs)
            }.isSuccess
            if (!deadlineRangeSupported) {
                check(admissionArbiter.abortJobAdmission(lease))
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Compilation deadline is outside the supported range.",
                )
            }
            if (deadlineExecutor.isShutdown || deadlineExecutor.isTerminated) {
                check(admissionArbiter.abortJobAdmission(lease))
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "R2 compilation deadline executor is unavailable.",
                )
            }
            val deathRecipient = IBinder.DeathRecipient {
                requestStop(
                    lease = lease,
                    cause = R2CoordinatorStopCause.CALLBACK_DIED,
                )
            }

            val linked = runCatching {
                callbackBinder.linkToDeath(deathRecipient, 0)
            }.isSuccess
            if (!linked || !callbackBinder.isBinderAlive) {
                runCatching { callbackBinder.unlinkToDeath(deathRecipient, 0) }
                check(admissionArbiter.abortJobAdmission(lease))
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Compilation callback Binder died during admission.",
                )
            }
            if (!callbackBinder.isBinderAlive) {
                runCatching { callbackBinder.unlinkToDeath(deathRecipient, 0) }
                check(admissionArbiter.abortJobAdmission(lease))
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Compilation callback Binder died during admission.",
                )
            }
            val outputTruncated = runCatching {
                Os.ftruncate(outputArchive.fileDescriptor, 0L)
            }.isSuccess
            if (!outputTruncated) {
                runCatching { callbackBinder.unlinkToDeath(deathRecipient, 0) }
                check(admissionArbiter.abortJobAdmission(lease))
                return@synchronized rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                    "R2 compilation output could not be truncated.",
                )
            }

            /*
             * Successful ftruncate is the irreversible admission boundary. Both server clocks
             * start here, after slow preflight and all ordinary rejection gates. A callback death
             * racing this boundary is handled as an accepted-job stop once this lock is released.
            */
            val startedAtElapsedRealtimeNanos =
                SystemClock.elapsedRealtimeNanos()
            val startedAt =
                TimeUnit.NANOSECONDS.toMillis(
                    startedAtElapsedRealtimeNanos,
                )
            val deadlineAt =
                if (startedAt <= Long.MAX_VALUE - request.timeoutMs) {
                    startedAt + request.timeoutMs
                } else {
                    Long.MAX_VALUE
                }
            val deadlineAtElapsedRealtimeNanos =
                Math.addExact(
                    startedAtElapsedRealtimeNanos,
                    TimeUnit.MILLISECONDS.toNanos(request.timeoutMs),
                )
            val job = R2CoordinatorJob(
                lease = lease,
                authority = authority,
                startedAt = startedAt,
                startedAtElapsedRealtimeNanos =
                    startedAtElapsedRealtimeNanos,
                deadlineAt = deadlineAt,
                deadlineAtElapsedRealtimeNanos =
                    deadlineAtElapsedRealtimeNanos,
                inputArchive = inputArchive,
                outputArchive = outputArchive,
                callback = callback,
                callbackBinder = callbackBinder,
                deathRecipient = deathRecipient,
                hostHandle = hostHandle,
                workerSpinMilliseconds = controls.workerSpinMilliseconds,
                workerSpinPhase = controls.workerSpinPhase,
                expandedWorkerCapacity = controls.expandedWorkerCapacity,
                terminalCleanupDelayMilliseconds =
                    controls.terminalCleanupDelayMilliseconds,
                externalCleanupEvidenceEnabled =
                    BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE &&
                        controls.externalCleanupEvidenceEnabled,
            )
            activeJob = job
            if (job.externalCleanupEvidenceEnabled) {
                lastExternalCleanupTerminalReceipt = null
                lastExternalCleanupPreAcknowledgement = null
                lastExternalNaturalPhaseTimeoutReceipt = null
            }
            val scheduled = runCatching {
                job.deadlineFuture = deadlineExecutor.schedule(
                    {
                        requestStop(
                            lease = lease,
                            cause = R2CoordinatorStopCause.TIMED_OUT,
                        )
                    },
                    request.timeoutMs,
                    TimeUnit.MILLISECONDS,
                )
                job.unacknowledgedFuture = deadlineExecutor.schedule(
                    {
                        requestStop(
                            lease = lease,
                            cause = R2CoordinatorStopCause.UNACKNOWLEDGED,
                        )
                    },
                    minOf(
                        request.timeoutMs,
                        TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
                    ),
                    TimeUnit.MILLISECONDS,
                )
            }.isSuccess
            if (!scheduled) {
                job.deadlineFuture?.cancel(false)
                job.unacknowledgedFuture?.cancel(false)
                job.stopCause = R2CoordinatorStopCause.UNACKNOWLEDGED
                job.phase = R2CoordinatorPhase.STOPPING
                job.pipelineScheduled = true
                val cleanupScheduled = runCatching {
                    pipelineExecutor.execute {
                        finalizeSelectedStop(job)
                    }
                }.isSuccess
                if (!cleanupScheduled) {
                    requireSupervision(
                        job,
                        "R2 admission clocks and cleanup could not be scheduled.",
                    )
                }
            }

            R2CompilationAdmissionDecision(
                requestId = request.requestId,
                accepted = true,
                jobToken = lease.jobToken,
                acknowledgementTimeoutMs = minOf(
                    request.timeoutMs,
                    TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
                ),
                errorCode = "",
                errorMessage = "",
            )
        }
    }

    /** Explicit post-return barrier shared by production and private debug admission. */
    fun acknowledge(
        callingUid: Int,
        requestId: String,
        jobToken: String,
    ): Boolean {
        val lease = admissionArbiter.authorizeActiveJob(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        ) ?: return admissionArbiter.acceptsTerminalAcknowledgement(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        )
        var callbackArmed = false
        var selectedCause: R2CoordinatorStopCause? = null
        var acknowledgementAccepted = true
        val job = synchronized(lock) {
            activeJob?.takeIf { candidate -> candidate.lease === lease }?.also { candidate ->
                if (
                    candidate.stopCause != null &&
                    !candidate.callbackArmed &&
                    !candidate.lease.lateUnacknowledgedAcknowledgementAccepted
                ) {
                    acknowledgementAccepted = false
                }
                if (
                    !candidate.callbackArmed &&
                    candidate.stopCause == null &&
                    candidate.phase == R2CoordinatorPhase.RESERVED_UNACKNOWLEDGED
                ) {
                    val now = SystemClock.elapsedRealtime()
                    val acknowledgementDeadlineAt = minOf(
                        candidate.deadlineAt,
                        candidate.startedAt +
                            TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
                    )
                    if (now >= acknowledgementDeadlineAt) {
                        selectedCause =
                            if (now >= candidate.deadlineAt) {
                                R2CoordinatorStopCause.TIMED_OUT
                            } else {
                                R2CoordinatorStopCause.UNACKNOWLEDGED
                        }
                        candidate.stopCause = selectedCause
                        candidate.phase = R2CoordinatorPhase.STOPPING
                        if (
                            !candidate.lease.lateUnacknowledgedAcknowledgementAccepted
                        ) {
                            acknowledgementAccepted = false
                        }
                    } else {
                        candidate.callbackArmed = true
                        candidate.unacknowledgedFuture?.cancel(false)
                        candidate.phase = R2CoordinatorPhase.ACTIVE
                        callbackArmed = true
                    }
                }
            }
        } ?: return admissionArbiter.acceptsTerminalAcknowledgement(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        )
        when {
            selectedCause != null -> stopHostAndSchedule(job, selectedCause)
            callbackArmed -> ensurePipelineScheduled(job)
        }
        return acknowledgementAccepted
    }

    fun cancel(
        callingUid: Int,
        requestId: String,
        jobToken: String,
    ): Boolean {
        val lease = admissionArbiter.authorizeActiveJob(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        )
        if (lease != null) {
            val ownedHere = synchronized(lock) {
                activeJob?.lease === lease
            }
            if (!ownedHere) {
                return admissionArbiter.matchesTerminalJob(
                    callingUid = callingUid,
                    requestId = requestId,
                    jobToken = jobToken,
                )
            }
            requestStop(lease, R2CoordinatorStopCause.CANCELLED)
            return true
        }
        return admissionArbiter.matchesTerminalJob(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        )
    }

    fun beginServiceShutdown() {
        var selectedCause: R2CoordinatorStopCause? = null
        val job = synchronized(lock) {
            if (closed) return
            closed = true
            activeJob?.also { candidate ->
                candidate.serviceDestroyed = true
                if (
                    candidate.stopCause == null &&
                    !candidate.commitGateWon &&
                    candidate.phase != R2CoordinatorPhase.PUBLISHABLE &&
                    candidate.phase != R2CoordinatorPhase.TERMINAL &&
                    candidate.phase != R2CoordinatorPhase.SUPERVISION_REQUIRED
                ) {
                    selectedCause =
                        if (SystemClock.elapsedRealtime() >= candidate.deadlineAt) {
                            R2CoordinatorStopCause.TIMED_OUT
                        } else {
                            R2CoordinatorStopCause.SERVICE_DESTROYED
                        }
                    candidate.stopCause = selectedCause
                    candidate.phase = R2CoordinatorPhase.STOPPING
                }
            }
        }
        if (job != null) {
            stopHostAndSchedule(job, selectedCause)
        }
    }

    fun finishServiceShutdown() {
        deadlineExecutor.shutdownNow()
        pipelineExecutor.shutdown()
        val job = synchronized(lock) { activeJob }
        if (job != null && !job.awaitShutdownBoundary(SERVICE_SHUTDOWN_WAIT_MS)) {
            requireSupervision(
                job,
                "R2 compilation did not settle within the service shutdown boundary.",
            )
        }
        if (job != null) {
            val cleanupProven = synchronized(lock) { job.cleanupProven }
            if (!cleanupProven) {
                throw R2ServiceShutdownCleanupUnprovenException(
                    synchronized(lock) {
                        job.supervisionReason
                            ?: "R2 service shutdown could not prove accepted-job cleanup."
                    },
                )
            }
        }
        pipelineExecutor.shutdownNow()
        val pipelineTerminated = awaitExecutorTermination(pipelineExecutor)
        val deadlineTerminated = awaitExecutorTermination(deadlineExecutor)
        if (!pipelineTerminated || !deadlineTerminated) {
            admissionArbiter.failClosed()
            throw R2ServiceShutdownCleanupUnprovenException(
                "R2 coordinator executor termination could not be proven: " +
                    "pipeline=$pipelineTerminated deadline=$deadlineTerminated.",
            )
        }
    }

    private fun requestStop(
        lease: R2AcceptedJobLease,
        cause: R2CoordinatorStopCause,
    ) {
        var selected = false
        var selectedCause: R2CoordinatorStopCause? = null
        val job = synchronized(lock) {
            val candidate = activeJob?.takeIf { active -> active.lease === lease }
                ?: return
            if (
                cause == R2CoordinatorStopCause.UNACKNOWLEDGED &&
                (
                    candidate.callbackArmed ||
                        candidate.phase != R2CoordinatorPhase.RESERVED_UNACKNOWLEDGED
                    )
            ) {
                return
            }
            if (cause == R2CoordinatorStopCause.CALLBACK_DIED) {
                candidate.callbackDied = true
            }
            if (cause == R2CoordinatorStopCause.SERVICE_DESTROYED) {
                candidate.serviceDestroyed = true
            }
            if (
                candidate.stopCause == null &&
                !candidate.commitGateWon &&
                candidate.phase != R2CoordinatorPhase.PUBLISHABLE &&
                candidate.phase != R2CoordinatorPhase.TERMINAL &&
                candidate.phase != R2CoordinatorPhase.SUPERVISION_REQUIRED
            ) {
                selectedCause =
                    if (SystemClock.elapsedRealtime() >= candidate.deadlineAt) {
                        R2CoordinatorStopCause.TIMED_OUT
                    } else {
                        cause
                    }
                candidate.stopCause = selectedCause
                candidate.phase = R2CoordinatorPhase.STOPPING
                selected = true
            }
            candidate
        }
        stopHostAndSchedule(
            job = job,
            selectedCause = if (selected) selectedCause else null,
        )
    }

    private fun stopHostAndSchedule(
        job: R2CoordinatorJob,
        selectedCause: R2CoordinatorStopCause?,
    ) {
        if (selectedCause != null) {
            val stopFailure = runCatching {
                compilerHost.requestStop(
                    handle = job.hostHandle,
                    cause = selectedCause.toHostStopCause(),
                )
            }.exceptionOrNull()
            if (stopFailure != null) {
                requireSupervision(
                    job,
                    "R2 host stop request failed: ${stopFailure.safeMessage()}",
                )
                return
            }
        }
        ensurePipelineScheduled(job)
    }

    private fun ensurePipelineScheduled(job: R2CoordinatorJob) {
        val submit = synchronized(lock) {
            if (
                activeJob !== job ||
                job.pipelineScheduled ||
                job.phase == R2CoordinatorPhase.TERMINAL ||
                job.phase == R2CoordinatorPhase.SUPERVISION_REQUIRED
            ) {
                false
            } else {
                job.pipelineScheduled = true
                true
            }
        }
        if (!submit) return
        val submitted = runCatching {
            pipelineExecutor.execute { runPipelineGuarded(job) }
        }.isSuccess
        if (!submitted) {
            requireSupervision(
                job,
                "R2 compilation pipeline executor is unavailable.",
            )
        }
    }

    private fun runPipelineGuarded(job: R2CoordinatorJob) {
        try {
            runPipeline(job)
        } catch (error: Throwable) {
            requireSupervision(
                job,
                "R2 compilation escaped its pipeline guard: ${error.safeMessage()}",
            )
        }
    }

    private fun runPipeline(job: R2CoordinatorJob) {
        if (hasSelectedStop(job)) {
            finalizeSelectedStop(job)
            return
        }

        val input = takeInput(job) ?: run {
            requireSupervision(job, "R2 compilation lost ownership of its input descriptor.")
            return
        }
        val adapted = try {
            R2ProjectArchiveLoader.load(
                context = appContext,
                inputArchive = input,
                authority = job.authority,
                trustedRuntimeCapabilitiesFingerprint =
                    job.authority.profileAuthority.runtimeCapabilitiesFingerprint,
            )
        } catch (error: Throwable) {
            if (error.containsInputCleanupUnproven()) {
                requireSupervision(
                    job,
                    "R2 private input cleanup could not be proven: ${error.safeMessage()}",
                )
            } else if (hasSelectedStop(job)) {
                finalizeSelectedStop(job)
            } else {
                finalizeFailure(job, error.toPipelineFailure())
            }
            return
        }
        if (hasSelectedStop(job)) {
            if (!closeAdaptedArchiveOrSupervise(job, adapted)) return
            finalizeSelectedStop(job)
            return
        }

        val remaining = remainingTime(job)
        if (remaining <= 0L) {
            if (!closeAdaptedArchiveOrSupervise(job, adapted)) return
            requestStop(job.lease, R2CoordinatorStopCause.TIMED_OUT)
            finalizeSelectedStop(job)
            return
        }
        val compilationAttempt = runCatching {
            if (job.expandedWorkerCapacity) {
                compilerHost.compileProjectWithExpandedWorkerCapacityForTesting(
                    handle = job.hostHandle,
                    snapshot = adapted.snapshot,
                    timeoutMs = remaining,
                    workerSpinMilliseconds = job.workerSpinMilliseconds,
                    workerSpinPhase = job.workerSpinPhase,
                    projectExecutionObserver = { event ->
                        publishProgress(job, event)
                    },
                )
            } else if (job.workerSpinMilliseconds == 0) {
                compilerHost.compileProjectUntilElapsedRealtimeDeadline(
                    handle = job.hostHandle,
                    snapshot = adapted.snapshot,
                    deadlineAtElapsedRealtimeNanos =
                        job.deadlineAtElapsedRealtimeNanos,
                    projectExecutionObserver = { event ->
                        publishProgress(job, event)
                    },
                )
            } else {
                compilerHost.compileProjectWithSynchronousWorkerSpinForTesting(
                    handle = job.hostHandle,
                    snapshot = adapted.snapshot,
                    timeoutMs = remaining,
                    workerSpinMilliseconds = job.workerSpinMilliseconds,
                    workerSpinPhase = requireNotNull(job.workerSpinPhase),
                    projectExecutionObserver = { event ->
                        publishProgress(job, event)
                    },
                )
            }
        }
        if (job.externalCleanupEvidenceEnabled) {
            val exactSnapshot = runCatching {
                compilerHost
                    .lastProjectCompilationEvidenceSnapshot()
                    ?.takeIf { snapshot ->
                        snapshot.requestId == job.hostHandle.requestId
                    }
            }.getOrNull()
            synchronized(lock) {
                if (activeJob === job) {
                    job.externalProjectCompilationEvidence =
                        exactSnapshot
                }
            }
        }
        if (
            !closeAdaptedArchiveOrSupervise(
                job = job,
                adapted = adapted,
                priorFailure = compilationAttempt.exceptionOrNull(),
            )
        ) {
            return
        }
        val compilationOutcome = compilationAttempt.getOrThrow()
        if (hasSelectedStop(job)) {
            finalizeSelectedStop(job)
            return
        }
        if (!compilationOutcome.succeeded) {
            val failure = R2PipelineFailure(
                errorCode = requireNotNull(compilationOutcome.errorCode),
                errorMessage = requireNotNull(compilationOutcome.errorMessage),
            )
            finalizeFailure(job, failure)
            return
        }

        val staged = try {
            R2PrivateOutputArchive.stage(
                privateDirectory = appContext.getDir(
                    PRIVATE_OUTPUT_STAGING_DIRECTORY,
                    Context.MODE_PRIVATE,
                ),
                acceptedArchive = adapted,
                compilationOutcome = compilationOutcome,
            )
        } catch (error: Throwable) {
            if (error.containsOutputCleanupUnproven()) {
                requireSupervision(
                    job,
                    "R2 private output cleanup could not be proven: ${error.safeMessage()}",
                )
            } else if (hasSelectedStop(job)) {
                finalizeSelectedStop(job)
            } else {
                finalizeFailure(job, error.toPipelineFailure())
            }
            return
        }

        var postStagingAction: R2PostStagingAction = R2PostStagingAction.None
        var stagedCleanupFailure: Throwable? = null
        try {
            if (hasSelectedStop(job)) {
                postStagingAction = R2PostStagingAction.SelectedStop(
                    outputConsumed = false,
                )
            } else {
                val output = takeOutput(job)
                if (output == null) {
                    requireSupervision(
                        job,
                        "R2 compilation lost ownership of its output descriptor.",
                    )
                } else {
                    postStagingAction = try {
                        R2PostStagingAction.Committed(
                            R2OutputFdCommitter.commit(
                                staged = staged,
                                outputArchive = output,
                                terminalGate = { claimCommitGate(job) },
                            ),
                        )
                    } catch (error: R2OutputCommitUnprovenException) {
                        requireSupervision(job, error.message.orEmpty())
                        R2PostStagingAction.None
                    } catch (error: Throwable) {
                        if (hasSelectedStop(job)) {
                            R2PostStagingAction.SelectedStop(
                                outputConsumed = true,
                            )
                        } else {
                            R2PostStagingAction.Failed(
                                error.toPipelineFailure(),
                            )
                        }
                    }
                }
            }
        } finally {
            try {
                staged.close()
            } catch (error: Throwable) {
                stagedCleanupFailure = error
            }
            stagedCleanupFailure?.let { cleanupFailure ->
                requireSupervision(
                    job,
                    "R2 private output cleanup could not be proven: " +
                        cleanupFailure.safeMessage(),
                )
            }
        }
        if (stagedCleanupFailure != null) return
        when (val action = postStagingAction) {
            is R2PostStagingAction.Committed ->
                publishCommitted(job, action.summary)
            is R2PostStagingAction.Failed ->
                finalizeFailureAfterOutputConsumed(job, action.failure)
            is R2PostStagingAction.SelectedStop ->
                if (action.outputConsumed) {
                    finalizeSelectedStopAfterOutputConsumed(job)
                } else {
                    finalizeSelectedStop(job)
                }
            R2PostStagingAction.None -> Unit
        }
    }

    private fun closeAdaptedArchiveOrSupervise(
        job: R2CoordinatorJob,
        adapted: R2AdaptedProjectArchive,
        priorFailure: Throwable? = null,
    ): Boolean = try {
        adapted.close()
        true
    } catch (cleanupFailure: Throwable) {
        priorFailure?.let(cleanupFailure::addSuppressed)
        requireSupervision(
            job,
            "R2 file-backed input cleanup could not be proven: " +
                cleanupFailure.safeMessage(),
        )
        false
    }

    private fun claimCommitGate(job: R2CoordinatorJob) {
        synchronized(lock) {
            check(activeJob === job) {
                "R2 output commit no longer owns the active admission."
            }
            installDeadlineStopIfDue(job)
            if (job.stopCause != null) {
                throw R2CommitSupersededException()
            }
            check(
                job.phase != R2CoordinatorPhase.PUBLISHABLE &&
                    job.phase != R2CoordinatorPhase.TERMINAL &&
                    job.phase != R2CoordinatorPhase.SUPERVISION_REQUIRED,
            ) {
                "R2 output commit reached an invalid terminal phase."
            }
            job.commitGateWon = true
            job.phase = R2CoordinatorPhase.COMMITTING
        }
    }

    private fun publishCommitted(
        job: R2CoordinatorJob,
        summary: R2OutputArchiveSummary,
    ) {
        val admissionTerminal = when (summary.outcome) {
            TypeScriptCompilerContract.OUTCOME_EMITTED -> R2AdmissionTerminal.EMITTED
            TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS -> R2AdmissionTerminal.DIAGNOSTICS
            else -> {
                requireSupervision(job, "R2 output packager produced an invalid outcome.")
                return
            }
        }
        publish(
            job = job,
            terminal = R2CoordinatorTerminal(
                admissionTerminal = admissionTerminal,
                outcome = summary.outcome,
                succeeded =
                    summary.outcome == TypeScriptCompilerContract.OUTCOME_EMITTED,
                errorCode = if (
                    summary.outcome == TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS
                ) {
                    TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS
                } else {
                    null
                },
                errorMessage = if (
                    summary.outcome == TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS
                ) {
                    "Project compilation produced diagnostics without executable output."
                } else {
                    null
                },
                diagnosticCount = summary.diagnosticCount,
                emittedModuleCount = summary.emittedModuleCount,
                outputManifestSha256 = summary.outputManifestSha256,
            ),
        )
    }

    private fun finalizeSelectedStop(job: R2CoordinatorJob) {
        if (job.terminalCleanupDelayMilliseconds > 0) {
            /*
             * Same-UID debug fault seam: expose the interval after a stop won but before output
             * cleanup/publication, so a late acknowledgement can be proven unable to re-arm the
             * callback.
             */
            SystemClock.sleep(job.terminalCleanupDelayMilliseconds.toLong())
        }
        val input = takeInput(job)
        val output = takeOutput(job)
        var failure: Throwable? = null
        if (input != null) {
            try {
                input.close()
            } catch (error: Throwable) {
                failure = error
            }
        }
        if (output != null) {
            try {
                R2OutputFdCommitter.clear(output)
            } catch (error: Throwable) {
                failure?.addSuppressed(error) ?: run { failure = error }
            }
        }
        if (failure != null) {
            requireSupervision(
                job,
                "R2 stopped-job cleanup could not be proven: ${failure.safeMessage()}",
            )
            return
        }
        finalizeSelectedStopAfterOutputConsumed(job)
    }

    private fun finalizeSelectedStopAfterOutputConsumed(job: R2CoordinatorJob) {
        val cause = synchronized(lock) {
            installDeadlineStopIfDue(job)
            job.stopCause
        } ?: run {
            finalizeFailureAfterOutputConsumed(
                job,
                R2PipelineFailure(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "R2 output commit was superseded without a terminal stop cause.",
                ),
            )
            return
        }
        publish(job, cause.toTerminal())
    }

    private fun finalizeFailure(
        job: R2CoordinatorJob,
        failure: R2PipelineFailure,
    ) {
        val input = takeInput(job)
        val output = takeOutput(job)
        var cleanupFailure: Throwable? = null
        if (input != null) {
            try {
                input.close()
            } catch (error: Throwable) {
                cleanupFailure = error
            }
        }
        if (output != null) {
            try {
                R2OutputFdCommitter.clear(output)
            } catch (error: Throwable) {
                cleanupFailure?.addSuppressed(error)
                    ?: run { cleanupFailure = error }
            }
        }
        if (cleanupFailure != null) {
            requireSupervision(
                job,
                "R2 failed-job cleanup could not be proven: " +
                    cleanupFailure.safeMessage(),
            )
            return
        }
        finalizeFailureAfterOutputConsumed(job, failure)
    }

    private fun finalizeFailureAfterOutputConsumed(
        job: R2CoordinatorJob,
        failure: R2PipelineFailure,
    ) {
        if (hasSelectedStop(job)) {
            finalizeSelectedStopAfterOutputConsumed(job)
            return
        }
        val outcome = when (failure.errorCode) {
            TypeScriptCompilerContract.ERROR_CANCELLED ->
                TypeScriptCompilerContract.OUTCOME_CANCELLED
            TypeScriptCompilerContract.ERROR_TIMEOUT ->
                TypeScriptCompilerContract.OUTCOME_TIMED_OUT
            else -> TypeScriptCompilerContract.OUTCOME_FAILED
        }
        val admissionTerminal = when (outcome) {
            TypeScriptCompilerContract.OUTCOME_CANCELLED ->
                R2AdmissionTerminal.CANCELLED
            TypeScriptCompilerContract.OUTCOME_TIMED_OUT ->
                R2AdmissionTerminal.TIMED_OUT
            else -> R2AdmissionTerminal.FAILED
        }
        publish(
            job,
            R2CoordinatorTerminal(
                admissionTerminal = admissionTerminal,
                outcome = outcome,
                succeeded = false,
                errorCode = failure.errorCode,
                errorMessage = failure.errorMessage,
                diagnosticCount = 0,
                emittedModuleCount = 0,
                outputManifestSha256 = null,
            ),
        )
    }

    private fun publish(
        job: R2CoordinatorJob,
        terminal: R2CoordinatorTerminal,
    ) {
        val selectedTerminal = synchronized(lock) {
            if (
                activeJob !== job ||
                job.phase == R2CoordinatorPhase.TERMINAL ||
                job.phase == R2CoordinatorPhase.SUPERVISION_REQUIRED
            ) {
                null
            } else {
                installDeadlineStopIfDue(job)
                val publicationTerminal =
                    if (!job.commitGateWon) {
                        job.stopCause?.toTerminal() ?: terminal
                    } else {
                        terminal
                    }
                job.phase = R2CoordinatorPhase.PUBLISHABLE
                job.deadlineFuture?.cancel(false)
                job.unacknowledgedFuture?.cancel(false)
                publicationTerminal
            }
        }
        if (selectedTerminal == null) return

        if (!unlinkCallbackDeathOrProveDead(job)) {
            requireSupervision(
                job,
                "R2 callback death-recipient cleanup could not be proven.",
            )
            return
        }
        val callback = synchronized(lock) {
            if (activeJob !== job || job.phase != R2CoordinatorPhase.PUBLISHABLE) {
                return
            }
            check(
                admissionArbiter.completeJob(
                    job.lease,
                    selectedTerminal.admissionTerminal,
                    acknowledgementWasArmed = job.callbackArmed,
                ),
            ) {
                "R2 coordinator lost its global lease before terminal publication."
            }
            job.phase = R2CoordinatorPhase.TERMINAL
            activeJob = null
            job.cleanupProven = true
            if (job.externalCleanupEvidenceEnabled) {
                lastExternalCleanupPreAcknowledgement
                    ?.takeIf { evidence ->
                        evidence.requestId == job.lease.requestId &&
                            R2Digests.equal(
                                evidence.jobTokenSha256,
                                R2Digests.sha256(
                                    job.lease.jobToken.toByteArray(
                                        java.nio.charset.StandardCharsets.UTF_8,
                                    ),
                                ),
                            )
                    }
                    ?.let { evidence ->
                        lastExternalCleanupTerminalReceipt =
                            runCatching {
                                R2ExternalPlusOneCleanupEvidence
                                    .captureTerminalReceiptBeforeCallback(
                                        preAcknowledgement = evidence,
                                        activeLeaseReleased =
                                            !admissionArbiter.isCurrent(job.lease) &&
                                                admissionArbiter
                                                    .activeAcceptedJobCount() == 0,
                                        cursor =
                                            job.hostHandle
                                                .cursorCleanupEvidence(),
                                        inputStagingDirectory = appContext.getDir(
                                            R2PrivateTransientOrphanScavenger
                                                .INPUT_STAGING_DIRECTORY_NAME,
                                            Context.MODE_PRIVATE,
                                        ),
                                        outputStagingDirectory = appContext.getDir(
                                            R2PrivateTransientOrphanScavenger
                                                .OUTPUT_STAGING_DIRECTORY_NAME,
                                            Context.MODE_PRIVATE,
                                        ),
                                    )
                            }.getOrNull()
                    }
                lastExternalNaturalPhaseTimeoutReceipt =
                    createExternalNaturalPhaseTimeoutReceipt(
                        job = job,
                        terminal = selectedTerminal,
                    )
            }
            job.shutdownBoundary.countDown()
            if (
                job.callbackArmed &&
                !job.callbackDied &&
                !job.serviceDestroyed &&
                job.callbackBinder.isBinderAlive
            ) {
                job.callback
            } else {
                null
            }
        }
        callback?.let { terminalCallback ->
            runCatching {
                terminalCallback.onCompleted(
                    selectedTerminal.toBundle(
                        requestId = job.lease.requestId,
                        elapsedMs = elapsedSince(job.startedAt),
                    ),
                )
            }
        }
    }

    /**
     * Publishes one already validated, bounded phase/work event through the frozen v1 callback.
     *
     * The host invokes this observer synchronously while the compile call is still active. The
     * pipeline therefore cannot enqueue the terminal callback until every earlier progress Binder
     * transaction has returned. Only primitive values and a monotonic offset from the irreversible
     * server request-start boundary are exposed.
     */
    private fun publishProgress(
        job: R2CoordinatorJob,
        event: R2ProjectExecutionEvent,
    ) {
        val elapsedNanos = Math.subtractExact(
            event.observedAtElapsedRealtimeNanos,
            job.startedAtElapsedRealtimeNanos,
        )
        check(elapsedNanos >= 0L) {
            "R2 progress preceded the public request-start boundary."
        }
        val callback = synchronized(lock) {
            if (
                activeJob !== job ||
                !job.callbackArmed ||
                job.callbackDied ||
                job.serviceDestroyed ||
                job.phase == R2CoordinatorPhase.TERMINAL ||
                job.phase == R2CoordinatorPhase.SUPERVISION_REQUIRED ||
                !job.callbackBinder.isBinderAlive
            ) {
                null
            } else {
                check(
                    job.publishedProgressEventCount <
                        TypeScriptR2Contract.MAX_PROGRESS_EVENTS_PER_COMPILATION,
                ) {
                    "R2 progress escaped its per-compilation event bound."
                }
                job.publishedProgressEventCount += 1
                job.callback
            }
        } ?: return
        val progress = Bundle().apply {
            putString(
                TypeScriptCompilerContract.KEY_REQUEST_ID,
                job.lease.requestId,
            )
            putInt(
                TypeScriptR2Contract.KEY_PROGRESS_SCHEMA_REVISION,
                TypeScriptR2Contract.PROGRESS_SCHEMA_REVISION,
            )
            putString(TypeScriptR2Contract.KEY_PHASE, event.phase.protocolValue)
            putLong(
                TypeScriptR2Contract
                    .KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START,
                elapsedNanos,
            )
            putLong(
                TypeScriptCompilerContract.KEY_ELAPSED_MS,
                TimeUnit.NANOSECONDS.toMillis(elapsedNanos),
            )
            when (event) {
                is R2ProjectExecutionEvent.Phase -> {
                    putString(
                        TypeScriptR2Contract.KEY_PROGRESS_KIND,
                        TypeScriptR2Contract.PROGRESS_KIND_PHASE,
                    )
                    putString(
                        TypeScriptR2Contract.KEY_PHASE_STATE,
                        event.state.protocolValue,
                    )
                }
                is R2ProjectExecutionEvent.Work -> {
                    putString(
                        TypeScriptR2Contract.KEY_PROGRESS_KIND,
                        TypeScriptR2Contract.PROGRESS_KIND_WORK,
                    )
                    putInt(
                        TypeScriptR2Contract.KEY_PROGRESS_SEQUENCE,
                        event.progress.sequence,
                    )
                    putInt(
                        TypeScriptR2Contract.KEY_COMPLETED_UNITS,
                        event.progress.completedUnits,
                    )
                    putInt(
                        TypeScriptR2Contract.KEY_TOTAL_UNITS,
                        event.progress.totalUnits,
                    )
                }
            }
        }
        runCatching { callback.onProgress(progress) }
    }

    private fun createExternalNaturalPhaseTimeoutReceipt(
        job: R2CoordinatorJob,
        terminal: R2CoordinatorTerminal,
    ): R2ExternalNaturalPhaseTimeoutReceipt? = runCatching {
        val snapshot =
            requireNotNull(job.externalProjectCompilationEvidence) {
                "External natural phase evidence lost its host snapshot."
            }
        require(snapshot.requestId == job.hostHandle.requestId) {
            "External natural phase evidence belongs to another host operation."
        }
        val outcome = when {
            terminal.outcome == TypeScriptCompilerContract.OUTCOME_EMITTED &&
                terminal.succeeded ->
                R2ExternalNaturalPhaseTimeoutOutcome.SUCCESS

            terminal.outcome == TypeScriptCompilerContract.OUTCOME_TIMED_OUT &&
                terminal.errorCode == TypeScriptCompilerContract.ERROR_TIMEOUT ->
                R2ExternalNaturalPhaseTimeoutOutcome.TIMED_OUT

            else -> error(
                "External natural phase evidence reached an unsupported terminal.",
            )
        }
        val projectStartOffsetNanos =
            Math.subtractExact(
                snapshot.projectStartedAtElapsedRealtimeNanos,
                job.startedAtElapsedRealtimeNanos,
            )
        R2ExternalNaturalPhaseTimeoutReceipt(
            requestId = job.lease.requestId,
            hostRequestId = snapshot.requestId,
            outcome = outcome,
            createdBeforeCallback = true,
            projectStartedElapsedRealtimeNanosSinceRequestStart =
                projectStartOffsetNanos,
            phaseTimings =
                snapshot.phaseTimingEvidence.map { evidence ->
                    R2ExternalNaturalPhaseTimingReceipt.from(
                        evidence = evidence,
                        projectStartedElapsedRealtimeNanosSinceRequestStart =
                            projectStartOffsetNanos,
                    )
                },
            termination =
                snapshot.phaseTerminationEvidence?.let { evidence ->
                    R2ExternalNaturalPhaseTerminationReceipt.from(
                        evidence = evidence,
                        requestStartedAtElapsedRealtimeNanos =
                            job.startedAtElapsedRealtimeNanos,
                    )
                },
            inputTelemetry =
                R2ExternalNaturalPhaseInputTelemetry.from(
                    snapshot.inputTransportTelemetry,
                ),
        )
    }.getOrNull()

    private fun requireSupervision(
        job: R2CoordinatorJob,
        message: String,
    ) {
        synchronized(lock) {
            if (
                activeJob !== job ||
                job.phase == R2CoordinatorPhase.TERMINAL ||
                job.phase == R2CoordinatorPhase.SUPERVISION_REQUIRED
            ) {
                return
            }
            job.phase = R2CoordinatorPhase.SUPERVISION_REQUIRED
            job.deadlineFuture?.cancel(false)
            job.unacknowledgedFuture?.cancel(false)
            job.supervisionReason =
                message.take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS)
            if (!admissionArbiter.poison(job.lease)) {
                admissionArbiter.failClosed()
                job.supervisionReason =
                    "R2 coordinator lost its global lease before poison."
            }
            job.shutdownBoundary.countDown()
        }
        runCatching {
            compilerHost.requestStop(
                handle = job.hostHandle,
                cause = R2ProjectStopCause.SERVICE_DESTROYED,
            )
        }
    }

    private fun R2CoordinatorJob.awaitShutdownBoundary(timeoutMs: Long): Boolean = try {
        shutdownBoundary.await(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun awaitExecutorTermination(
        executor: java.util.concurrent.ExecutorService,
    ): Boolean =
        try {
            executor.awaitTermination(
                EXECUTOR_TERMINATION_WAIT_MS,
                TimeUnit.MILLISECONDS,
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }

    private fun unlinkCallbackDeathOrProveDead(job: R2CoordinatorJob): Boolean {
        val unlinked = runCatching {
            job.callbackBinder.unlinkToDeath(job.deathRecipient, 0)
        }.getOrDefault(false)
        if (unlinked) return true
        return synchronized(lock) {
            job.callbackDied
        } || !job.callbackBinder.isBinderAlive
    }

    private fun hasSelectedStop(job: R2CoordinatorJob): Boolean = synchronized(lock) {
        if (activeJob !== job) return@synchronized true
        installDeadlineStopIfDue(job)
        job.stopCause != null
    }

    private fun remainingTime(job: R2CoordinatorJob): Long = synchronized(lock) {
        installDeadlineStopIfDue(job)
        (job.deadlineAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    private fun installDeadlineStopIfDue(job: R2CoordinatorJob) {
        if (
            job.stopCause == null &&
            !job.commitGateWon &&
            SystemClock.elapsedRealtime() >= job.deadlineAt
        ) {
            job.stopCause = R2CoordinatorStopCause.TIMED_OUT
            job.phase = R2CoordinatorPhase.STOPPING
        }
    }

    private fun takeInput(job: R2CoordinatorJob): ParcelFileDescriptor? = synchronized(lock) {
        job.inputArchive.also { job.inputArchive = null }
    }

    private fun takeOutput(job: R2CoordinatorJob): ParcelFileDescriptor? = synchronized(lock) {
        job.outputArchive.also { job.outputArchive = null }
    }

    private fun rejected(
        requestId: String,
        errorCode: String,
        errorMessage: String,
    ) = R2CompilationAdmissionDecision(
        requestId = requestId,
        accepted = false,
        jobToken = null,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )

    private fun Throwable.toPipelineFailure(): R2PipelineFailure = when (this) {
        is R2ProjectArchiveException -> R2PipelineFailure(
            errorCode = contractErrorCode,
            errorMessage = message.orEmpty(),
        )
        is R2OutputArchiveException -> R2PipelineFailure(
            errorCode = contractErrorCode,
            errorMessage = message.orEmpty(),
        )
        is R2OutputCommitException -> R2PipelineFailure(
            errorCode = contractErrorCode,
            errorMessage = message.orEmpty(),
        )
        else -> R2PipelineFailure(
            errorCode = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            errorMessage = "R2 compilation pipeline failed: ${safeMessage()}",
        )
    }

    private data class R2CoordinatorJob(
        val lease: R2AcceptedJobLease,
        val authority: R2ProjectArchiveAuthority,
        val startedAt: Long,
        val startedAtElapsedRealtimeNanos: Long,
        val deadlineAt: Long,
        val deadlineAtElapsedRealtimeNanos: Long,
        var inputArchive: ParcelFileDescriptor?,
        var outputArchive: ParcelFileDescriptor?,
        val callback: ITypeScriptCompilationCallback,
        val callbackBinder: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
        val hostHandle: R2HostProjectOperationHandle,
        val workerSpinMilliseconds: Int,
        val workerSpinPhase: R2ProjectExecutionPhase?,
        val expandedWorkerCapacity: Boolean,
        val terminalCleanupDelayMilliseconds: Int,
        val externalCleanupEvidenceEnabled: Boolean,
        var phase: R2CoordinatorPhase = R2CoordinatorPhase.RESERVED_UNACKNOWLEDGED,
        var stopCause: R2CoordinatorStopCause? = null,
        var callbackArmed: Boolean = false,
        var callbackDied: Boolean = false,
        var serviceDestroyed: Boolean = false,
        var pipelineScheduled: Boolean = false,
        var commitGateWon: Boolean = false,
        var deadlineFuture: ScheduledFuture<*>? = null,
        var unacknowledgedFuture: ScheduledFuture<*>? = null,
        var supervisionReason: String? = null,
        var cleanupProven: Boolean = false,
        var externalProjectCompilationEvidence:
            R2ProjectCompilationEvidenceSnapshot? = null,
        var publishedProgressEventCount: Int = 0,
        val shutdownBoundary: CountDownLatch = CountDownLatch(1),
    )

    private enum class R2CoordinatorPhase {
        RESERVED_UNACKNOWLEDGED,
        ACTIVE,
        STOPPING,
        COMMITTING,
        PUBLISHABLE,
        TERMINAL,
        SUPERVISION_REQUIRED,
    }

    private enum class R2CoordinatorStopCause {
        CANCELLED,
        TIMED_OUT,
        CALLBACK_DIED,
        SERVICE_DESTROYED,
        UNACKNOWLEDGED,
        ;

        fun toHostStopCause(): R2ProjectStopCause = when (this) {
            CANCELLED -> R2ProjectStopCause.CANCELLED
            TIMED_OUT -> R2ProjectStopCause.TIMED_OUT
            CALLBACK_DIED -> R2ProjectStopCause.CALLBACK_DIED
            SERVICE_DESTROYED,
            UNACKNOWLEDGED,
            -> R2ProjectStopCause.SERVICE_DESTROYED
        }

        fun toTerminal(): R2CoordinatorTerminal = when (this) {
            CANCELLED -> R2CoordinatorTerminal(
                admissionTerminal = R2AdmissionTerminal.CANCELLED,
                outcome = TypeScriptCompilerContract.OUTCOME_CANCELLED,
                succeeded = false,
                errorCode = TypeScriptCompilerContract.ERROR_CANCELLED,
                errorMessage = "Project compilation was cancelled.",
                diagnosticCount = 0,
                emittedModuleCount = 0,
                outputManifestSha256 = null,
            )
            TIMED_OUT -> R2CoordinatorTerminal(
                admissionTerminal = R2AdmissionTerminal.TIMED_OUT,
                outcome = TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                succeeded = false,
                errorCode = TypeScriptCompilerContract.ERROR_TIMEOUT,
                errorMessage = "Project compilation timed out.",
                diagnosticCount = 0,
                emittedModuleCount = 0,
                outputManifestSha256 = null,
            )
            CALLBACK_DIED -> R2CoordinatorTerminal(
                admissionTerminal = R2AdmissionTerminal.CALLBACK_DIED,
                outcome = TypeScriptCompilerContract.OUTCOME_FAILED,
                succeeded = false,
                errorCode = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                errorMessage = "Compilation callback Binder died.",
                diagnosticCount = 0,
                emittedModuleCount = 0,
                outputManifestSha256 = null,
            )
            SERVICE_DESTROYED -> R2CoordinatorTerminal(
                admissionTerminal = R2AdmissionTerminal.SERVICE_DESTROYED,
                outcome = TypeScriptCompilerContract.OUTCOME_FAILED,
                succeeded = false,
                errorCode = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                errorMessage = "Compiler service was destroyed.",
                diagnosticCount = 0,
                emittedModuleCount = 0,
                outputManifestSha256 = null,
            )
            UNACKNOWLEDGED -> R2CoordinatorTerminal(
                admissionTerminal = R2AdmissionTerminal.UNACKNOWLEDGED,
                outcome = TypeScriptCompilerContract.OUTCOME_FAILED,
                succeeded = false,
                errorCode = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                errorMessage = "Compilation admission was not acknowledged.",
                diagnosticCount = 0,
                emittedModuleCount = 0,
                outputManifestSha256 = null,
            )
        }
    }

    private data class R2CoordinatorTerminal(
        val admissionTerminal: R2AdmissionTerminal,
        val outcome: String,
        val succeeded: Boolean,
        val errorCode: String?,
        val errorMessage: String?,
        val diagnosticCount: Int,
        val emittedModuleCount: Int,
        val outputManifestSha256: String?,
    ) {

        fun toBundle(
            requestId: String,
            elapsedMs: Long,
        ) = Bundle().apply {
            putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
            putString(TypeScriptCompilerContract.KEY_OUTCOME, outcome)
            putBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, succeeded)
            errorCode?.let { putString(TypeScriptCompilerContract.KEY_ERROR_CODE, it) }
            errorMessage?.let {
                putString(
                    TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                    it.take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS),
                )
            }
            putInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, diagnosticCount)
            putInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, emittedModuleCount)
            putLong(TypeScriptCompilerContract.KEY_ELAPSED_MS, elapsedMs)
            outputManifestSha256?.let {
                putInt(
                    TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
                    TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
                )
                putInt(
                    TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION,
                    TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION,
                )
                putString(TypeScriptCompilerContract.KEY_OUTPUT_MANIFEST_SHA256, it)
            }
        }
    }

    private data class R2PipelineFailure(
        val errorCode: String,
        val errorMessage: String,
    )

    private sealed interface R2PostStagingAction {

        data object None : R2PostStagingAction

        data class Committed(
            val summary: R2OutputArchiveSummary,
        ) : R2PostStagingAction

        data class Failed(
            val failure: R2PipelineFailure,
        ) : R2PostStagingAction

        data class SelectedStop(
            val outputConsumed: Boolean,
        ) : R2PostStagingAction
    }

    private class R2CommitSupersededException : R2OutputCommitException(
        TypeScriptCompilerContract.ERROR_CANCELLED,
        "R2 output commit lost the terminal gate.",
    )

    private companion object {
        const val SERVICE_SHUTDOWN_WAIT_MS = 5_000L
        const val EXECUTOR_TERMINATION_WAIT_MS = 1_000L
        const val PRIVATE_OUTPUT_STAGING_DIRECTORY = "typescript-r2-output-staging"
    }
}

internal data class R2CompilationAdmissionDecision(
    val requestId: String,
    val accepted: Boolean,
    val jobToken: String?,
    val acknowledgementTimeoutMs: Long? = null,
    val errorCode: String,
    val errorMessage: String,
)

internal class R2ServiceShutdownCleanupUnprovenException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private fun Throwable.safeMessage(): String =
    (message ?: javaClass.simpleName).take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS)

private fun Throwable.containsInputCleanupUnproven(
    visited: MutableSet<Throwable> =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap()),
): Boolean {
    if (!visited.add(this)) return false
    if (this is R2PrivateInputCleanupUnprovenException) return true
    if (cause?.containsInputCleanupUnproven(visited) == true) return true
    return suppressed.any { error -> error.containsInputCleanupUnproven(visited) }
}

private fun Throwable.containsOutputCleanupUnproven(
    visited: MutableSet<Throwable> =
        java.util.Collections.newSetFromMap(java.util.IdentityHashMap()),
): Boolean {
    if (!visited.add(this)) return false
    if (this is R2PrivateOutputCleanupUnprovenException) return true
    if (cause?.containsOutputCleanupUnproven(visited) == true) return true
    return suppressed.any { error -> error.containsOutputCleanupUnproven(visited) }
}

private fun elapsedSince(startedAt: Long): Long =
    (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
