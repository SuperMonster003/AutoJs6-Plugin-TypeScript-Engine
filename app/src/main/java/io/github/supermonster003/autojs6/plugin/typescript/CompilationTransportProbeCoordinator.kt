package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * A debug-only transport lifecycle probe.
 *
 * This coordinator deliberately does not read the input archive or produce a compilation
 * archive. It exists so R1 can exercise accepted Binder ownership, exactly-once terminal
 * callbacks, cancellation, timeout, callback death, and service teardown against the real remote
 * compiler service without claiming that R2 project compilation is available.
 */
internal class CompilationTransportProbeCoordinator(
    private val admissionArbiter: R2GlobalAdmissionArbiter,
) {

    private val lock = Any()
    private val executor = ScheduledThreadPoolExecutor(1) { runnable ->
        Thread(runnable, "typescript-r1-transport-probe").apply {
            isDaemon = true
        }
    }.apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
        continueExistingPeriodicTasksAfterShutdownPolicy = false
    }
    private val activeJobs = LinkedHashMap<JobIdentity, ProbeJob>()
    private var closed = false

    fun activeJobCount(): Int = synchronized(lock) {
        activeJobs.size
    }

    fun hasClaimedTerminalForTesting(): Boolean = synchronized(lock) {
        activeJobs.values.any { job -> job.terminalIntent != null }
    }

    fun admit(
        request: R0CompilationEnvelope,
        callingUid: Int,
        inputArchive: ParcelFileDescriptor,
        outputArchive: ParcelFileDescriptor,
        callback: ITypeScriptCompilationCallback,
        controls: CompilerTransportProbeControls,
    ): TransportProbeAdmissionDecision {
        val identity = JobIdentity(callingUid, request.requestId)
        val callbackBinder = callback.asBinder()

        return synchronized(lock) {
            when {
                closed -> rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Compiler transport probe coordinator has been destroyed.",
                )
                !callbackBinder.isBinderAlive -> rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Compilation callback Binder is not alive.",
                )
                else -> {
                    val globalAdmission = admissionArbiter.admitJob(
                        callingUid = callingUid,
                        requestId = request.requestId,
                        callbackIdentity = callbackBinder,
                    )
                    val lease = globalAdmission.lease
                        ?: return@synchronized rejected(
                            request.requestId,
                            globalAdmission.errorCode,
                            globalAdmission.errorMessage,
                        )
                    val jobToken = lease.jobToken
                    val startedAt = SystemClock.elapsedRealtime()
                    val deathRecipient = IBinder.DeathRecipient {
                        finish(
                            identity = identity,
                            expectedToken = jobToken,
                            terminal = ProbeTerminal.CALLBACK_DIED,
                            deliverCallback = false,
                        )
                    }
                    val job = ProbeJob(
                        identity = identity,
                        lease = lease,
                        startedAt = startedAt,
                        timeoutMs = request.timeoutMs,
                        controls = controls,
                        inputArchive = inputArchive,
                        outputArchive = outputArchive,
                        callback = callback,
                        callbackBinder = callbackBinder,
                        deathRecipient = deathRecipient,
                    )
                    /*
                     * Admission is intentionally completed while holding the coordinator lock.
                     * This makes duplicate/capacity reservation atomic and ensures that no
                     * rejected race can truncate the caller's output. The job and its bounded
                     * unacknowledged-admission reaper are reserved before ftruncate; failures
                     * roll both back. Once ftruncate succeeds there is no later rejection, and
                     * callback death racing that point is an accepted-job terminal condition.
                     */
                    val linked = runCatching {
                        callbackBinder.linkToDeath(deathRecipient, 0)
                    }.isSuccess
                    if (!linked || !callbackBinder.isBinderAlive) {
                        runCatching {
                            callbackBinder.unlinkToDeath(deathRecipient, 0)
                        }
                        check(admissionArbiter.abortJobAdmission(lease)) {
                            "Transport probe lost its global admission lease."
                        }
                        rejected(
                            request.requestId,
                            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            "Compilation callback Binder died during admission.",
                        )
                    } else {
                        activeJobs[identity] = job
                        if (!callbackBinder.isBinderAlive) {
                            activeJobs.remove(identity)
                            check(admissionArbiter.abortJobAdmission(lease)) {
                                "Transport probe lost its global admission lease."
                            }
                            runCatching {
                                callbackBinder.unlinkToDeath(deathRecipient, 0)
                            }
                            return@synchronized rejected(
                                request.requestId,
                                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                                "Compilation callback Binder died during admission.",
                            )
                        }
                        val unacknowledgedReaperScheduled = runCatching {
                            job.unacknowledgedFuture = executor.schedule(
                                {
                                    finish(
                                        identity = identity,
                                        expectedToken = jobToken,
                                        terminal = ProbeTerminal.TIMED_OUT,
                                        deliverCallback = false,
                                        requireUnarmed = true,
                                    )
                                },
                                minOf(
                                    request.timeoutMs,
                                    MAX_UNACKNOWLEDGED_LIFETIME_MS,
                                ),
                                TimeUnit.MILLISECONDS,
                            )
                            true
                        }.getOrDefault(false)
                        if (!unacknowledgedReaperScheduled) {
                            activeJobs.remove(identity)
                            check(admissionArbiter.abortJobAdmission(lease)) {
                                "Transport probe lost its global admission lease."
                            }
                            runCatching {
                                callbackBinder.unlinkToDeath(deathRecipient, 0)
                            }
                            rejected(
                                request.requestId,
                                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                                "Transport probe could not arm its admission reaper.",
                            )
                        } else {
                            val outputPrepared = runCatching {
                                Os.ftruncate(outputArchive.fileDescriptor, 0L)
                                true
                            }.getOrDefault(false)
                            if (!outputPrepared) {
                                activeJobs.remove(identity)
                                job.unacknowledgedFuture?.cancel(false)
                                check(admissionArbiter.abortJobAdmission(lease)) {
                                    "Transport probe lost its global admission lease."
                                }
                                runCatching {
                                    callbackBinder.unlinkToDeath(deathRecipient, 0)
                                }
                                rejected(
                                    request.requestId,
                                    TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT,
                                    "Transport probe could not truncate the output archive.",
                                )
                            } else {
                                TransportProbeAdmissionDecision(
                                    requestId = request.requestId,
                                    accepted = true,
                                    jobToken = jobToken,
                                    errorCode = "",
                                    errorMessage = "",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Arms terminal work only from a transaction made after the caller received its admission
     * ticket. This is the explicit ordering barrier that prevents a fast executor from delivering
     * an oneway callback before the synchronous startCompilation reply reaches the caller.
     */
    fun acknowledge(
        callingUid: Int,
        requestId: String,
        jobToken: String,
    ): Boolean {
        val identity = JobIdentity(callingUid, requestId)
        val lease = admissionArbiter.authorizeActiveJob(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        ) ?: return admissionArbiter.matchesTerminalJob(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        )
        var schedulingFailure: Throwable? = null
        val acknowledged = synchronized(lock) {
            val job = activeJobs[identity] ?: return@synchronized false
            if (job.lease !== lease) return@synchronized false
            if (job.terminalIntent != null) return@synchronized true
            if (job.terminalWorkArmed) return@synchronized true

            job.terminalWorkArmed = true
            job.unacknowledgedFuture?.cancel(false)
            val elapsedMs = (
                SystemClock.elapsedRealtime() - job.startedAt
                ).coerceAtLeast(0L)
            val remainingTimeoutMs = (job.timeoutMs - elapsedMs).coerceAtLeast(0L)
            schedulingFailure = runCatching {
                job.timeoutFuture = executor.schedule(
                    {
                        finish(
                            identity = identity,
                            expectedToken = jobToken,
                            terminal = ProbeTerminal.TIMED_OUT,
                            deliverCallback = true,
                        )
                    },
                    remainingTimeoutMs,
                    TimeUnit.MILLISECONDS,
                )
                if (
                    job.controls.mode ==
                    CompilerTransportProbeProtocol.MODE_COMPLETE
                ) {
                    job.completionFuture = executor.schedule(
                        {
                            finish(
                                identity = identity,
                                expectedToken = jobToken,
                                terminal = ProbeTerminal.COMPLETED,
                                deliverCallback = true,
                            )
                        },
                        job.controls.completionDelayMs,
                        TimeUnit.MILLISECONDS,
                    )
                }
            }.exceptionOrNull()
            true
        }
        if (!acknowledged) {
            return admissionArbiter.matchesTerminalJob(
                callingUid = callingUid,
                requestId = requestId,
                jobToken = jobToken,
            )
        }
        if (schedulingFailure != null) {
            // Admission already returned before acknowledge can be called, so a stable failure
            // callback is now both ordered and required while both Binder peers remain alive.
            finish(
                identity = identity,
                expectedToken = jobToken,
                terminal = ProbeTerminal.SERVICE_DESTROYED,
                deliverCallback = true,
            )
        }
        return acknowledged
    }

    fun cancel(
        callingUid: Int,
        requestId: String,
        jobToken: String,
    ): Boolean {
        val identity = JobIdentity(callingUid, requestId)
        val lease = admissionArbiter.authorizeActiveJob(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        )
        if (lease != null) {
            val ownedHere = synchronized(lock) {
                activeJobs[identity]?.lease === lease
            }
            if (!ownedHere) {
                return admissionArbiter.matchesTerminalJob(
                    callingUid = callingUid,
                    requestId = requestId,
                    jobToken = jobToken,
                )
            }
            finish(
                identity = identity,
                expectedToken = jobToken,
                terminal = ProbeTerminal.CANCELLED,
                deliverCallback = true,
            )
            return true
        }
        return admissionArbiter.matchesTerminalJob(
            callingUid = callingUid,
            requestId = requestId,
            jobToken = jobToken,
        )
    }

    fun close() {
        val (jobs, claimed) = synchronized(lock) {
            if (closed) return
            closed = true
            val jobs = activeJobs.values.toList()
            jobs to jobs.mapNotNull { job ->
                claimFinishLocked(
                    identity = job.identity,
                    expectedToken = job.jobToken,
                    terminal = ProbeTerminal.SERVICE_DESTROYED,
                    deliverCallback = false,
                    requireUnarmed = false,
                )
            }
        }
        var cleanupDispatchFailure: Throwable? = null
        claimed.forEach { job ->
            try {
                executor.execute { finishClaimed(job) }
            } catch (error: Throwable) {
                cleanupDispatchFailure?.addSuppressed(error)
                    ?: run { cleanupDispatchFailure = error }
            }
        }
        val cleanupDeadlineNanos =
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SERVICE_SHUTDOWN_WAIT_MS)
        val cleanupProven = cleanupDispatchFailure == null && jobs.all { job ->
            val remainingNanos = cleanupDeadlineNanos - System.nanoTime()
            remainingNanos > 0L &&
                job.awaitCleanupBoundary(remainingNanos, TimeUnit.NANOSECONDS) &&
                synchronized(lock) { job.cleanupProven }
        }
        if (!cleanupProven || admissionArbiter.requiresExternalSupervision()) {
            /*
             * Do not interrupt a thread that may still own descriptors. Graceful shutdown only
             * prevents further work; process-level supervision owns recovery from this point.
             */
            executor.shutdown()
            admissionArbiter.failClosed()
            throw R2ServiceShutdownCleanupUnprovenException(
                "R1 transport probe cleanup requires process-level supervision.",
            )
        }
        executor.shutdownNow()
    }

    private fun finish(
        identity: JobIdentity,
        expectedToken: String,
        terminal: ProbeTerminal,
        deliverCallback: Boolean,
        requireUnarmed: Boolean = false,
    ) {
        val job = synchronized(lock) {
            claimFinishLocked(
                identity = identity,
                expectedToken = expectedToken,
                terminal = terminal,
                deliverCallback = deliverCallback,
                requireUnarmed = requireUnarmed,
            )
        } ?: return
        finishClaimed(job)
    }

    private fun claimFinishLocked(
        identity: JobIdentity,
        expectedToken: String,
        terminal: ProbeTerminal,
        deliverCallback: Boolean,
        requireUnarmed: Boolean,
    ): ProbeJob? {
        val candidate = activeJobs[identity]
        if (
            candidate == null ||
            candidate.jobToken != expectedToken ||
            (requireUnarmed && candidate.terminalWorkArmed) ||
            candidate.poisoned ||
            candidate.terminalIntent != null
        ) {
            return null
        }
        candidate.terminalIntent = terminal
        candidate.deliverTerminalCallback = deliverCallback
        candidate.unacknowledgedFuture?.cancel(false)
        candidate.timeoutFuture?.cancel(false)
        candidate.completionFuture?.cancel(false)
        return candidate
    }

    /**
     * Retains the global slot and local owner record while descriptor cleanup runs.
     */
    private fun finishClaimed(job: ProbeJob) {
        if (job.controls.terminalCleanupDelayMs > 0L) {
            /*
             * Same-UID debug fault seam: keep the job between terminal-intent selection and
             * descriptor cleanup so instrumentation can deterministically race Service teardown
             * against an already-claimed completion.
             */
            SystemClock.sleep(job.controls.terminalCleanupDelayMs)
        }
        if (job.controls.forceCleanupUnproven) {
            /*
             * Same-UID DEBUG fault seam for the process supervisor gate. This intentionally
             * models an owner that cannot prove descriptor cleanup: retain both descriptors,
             * the exact local job record and the exact process-wide lease, publish no callback,
             * and deny every later admission until the old process dies.
             */
            synchronized(lock) {
                if (
                    activeJobs[job.identity] !== job ||
                    job.terminalIntent == null
                ) {
                    job.poisoned = true
                    admissionArbiter.failClosed()
                } else {
                    job.poisoned = true
                    if (!admissionArbiter.poison(job.lease)) {
                        admissionArbiter.failClosed()
                    }
                }
                job.cleanupBoundary.countDown()
            }
            return
        }
        var cleanupFailure: Throwable? = null
        try {
            R2OutputFdCommitter.clear(job.outputArchive)
        } catch (error: Throwable) {
            cleanupFailure = error
        }
        try {
            job.inputArchive.close()
        } catch (error: Throwable) {
            cleanupFailure?.addSuppressed(error) ?: run { cleanupFailure = error }
        }
        runCatching {
            job.callbackBinder.unlinkToDeath(job.deathRecipient, 0)
        }

        val callback = synchronized(lock) {
            if (activeJobs[job.identity] !== job) {
                job.poisoned = true
                admissionArbiter.failClosed()
                job.cleanupBoundary.countDown()
                return@synchronized null
            }
            if (cleanupFailure != null) {
                job.poisoned = true
                if (!admissionArbiter.poison(job.lease)) {
                    admissionArbiter.failClosed()
                }
                job.cleanupBoundary.countDown()
                return@synchronized null
            }
            val terminal = requireNotNull(job.terminalIntent)
            if (!admissionArbiter.completeJob(job.lease, terminal.admissionTerminal)) {
                job.poisoned = true
                admissionArbiter.failClosed()
                job.cleanupBoundary.countDown()
                return@synchronized null
            }
            activeJobs.remove(job.identity)
            job.cleanupProven = true
            job.cleanupBoundary.countDown()
            if (
                !closed &&
                job.deliverTerminalCallback &&
                job.callbackBinder.isBinderAlive
            ) {
                job.callback
            } else {
                null
            }
        }

        callback?.let { terminalCallback ->
            val terminal = requireNotNull(job.terminalIntent)
            runCatching {
                terminalCallback.onCompleted(
                    terminal.toResultBundle(
                        requestId = job.identity.requestId,
                        elapsedMs = (
                            SystemClock.elapsedRealtime() - job.startedAt
                            ).coerceAtLeast(0L),
                    ),
                )
            }
        }
    }

    private fun ProbeJob.awaitCleanupBoundary(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = try {
        cleanupBoundary.await(timeout, unit)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    private fun ProbeTerminal.toResultBundle(
        requestId: String,
        elapsedMs: Long,
    ) = Bundle().apply {
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_OUTCOME, outcome)
        putBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, false)
        putString(TypeScriptCompilerContract.KEY_ERROR_CODE, errorCode)
        putString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE, errorMessage)
        putInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, 0)
        putInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, 0)
        putLong(TypeScriptCompilerContract.KEY_ELAPSED_MS, elapsedMs)
    }

    private fun rejected(
        requestId: String,
        errorCode: String,
        errorMessage: String,
    ) = TransportProbeAdmissionDecision(
        requestId = requestId,
        accepted = false,
        jobToken = null,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )

    private data class JobIdentity(
        val callingUid: Int,
        val requestId: String,
    )

    private data class ProbeJob(
        val identity: JobIdentity,
        val lease: R2AcceptedJobLease,
        val startedAt: Long,
        val timeoutMs: Long,
        val controls: CompilerTransportProbeControls,
        val inputArchive: ParcelFileDescriptor,
        val outputArchive: ParcelFileDescriptor,
        val callback: ITypeScriptCompilationCallback,
        val callbackBinder: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
        var terminalWorkArmed: Boolean = false,
        @Volatile var unacknowledgedFuture: ScheduledFuture<*>? = null,
        @Volatile var timeoutFuture: ScheduledFuture<*>? = null,
        @Volatile var completionFuture: ScheduledFuture<*>? = null,
        var terminalIntent: ProbeTerminal? = null,
        var deliverTerminalCallback: Boolean = false,
        var poisoned: Boolean = false,
        var cleanupProven: Boolean = false,
        val cleanupBoundary: CountDownLatch = CountDownLatch(1),
    ) {
        val jobToken: String
            get() = lease.jobToken
    }

    private enum class ProbeTerminal(
        val outcome: String,
        val errorCode: String,
        val errorMessage: String,
    ) {
        COMPLETED(
            TypeScriptCompilerContract.OUTCOME_FAILED,
            TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
            "R1 transport lifecycle probe completed without compiling a project.",
        ),
        CANCELLED(
            TypeScriptCompilerContract.OUTCOME_CANCELLED,
            TypeScriptCompilerContract.ERROR_CANCELLED,
            "R1 transport lifecycle probe was cancelled.",
        ),
        TIMED_OUT(
            TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
            TypeScriptCompilerContract.ERROR_TIMEOUT,
            "R1 transport lifecycle probe timed out.",
        ),
        CALLBACK_DIED(
            TypeScriptCompilerContract.OUTCOME_FAILED,
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            "R1 transport lifecycle probe callback Binder died.",
        ),
        SERVICE_DESTROYED(
            TypeScriptCompilerContract.OUTCOME_FAILED,
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            "R1 transport lifecycle probe service was destroyed.",
        ),
        ;

        val admissionTerminal: R2AdmissionTerminal
            get() = when (this) {
                COMPLETED -> R2AdmissionTerminal.FAILED
                CANCELLED -> R2AdmissionTerminal.CANCELLED
                TIMED_OUT -> R2AdmissionTerminal.TIMED_OUT
                CALLBACK_DIED -> R2AdmissionTerminal.CALLBACK_DIED
                SERVICE_DESTROYED -> R2AdmissionTerminal.SERVICE_DESTROYED
            }
    }

    private companion object {
        const val MAX_UNACKNOWLEDGED_LIFETIME_MS = 5_000L
        const val SERVICE_SHUTDOWN_WAIT_MS = 5_000L
    }
}

internal data class TransportProbeAdmissionDecision(
    val requestId: String,
    val accepted: Boolean,
    val jobToken: String?,
    val errorCode: String,
    val errorMessage: String,
)
