package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.util.Base64
import java.security.SecureRandom
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
internal class CompilationTransportProbeCoordinator {

    private val lock = Any()
    private val secureRandom = SecureRandom()
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
    private val tombstones = LinkedHashMap<JobIdentity, ProbeTombstone>()
    private var closed = false

    fun activeJobCount(): Int = synchronized(lock) {
        activeJobs.size
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
        val jobToken = newJobToken()
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
            jobToken = jobToken,
            startedAt = startedAt,
            timeoutMs = request.timeoutMs,
            controls = controls,
            inputArchive = inputArchive,
            outputArchive = outputArchive,
            callback = callback,
            callbackBinder = callbackBinder,
            deathRecipient = deathRecipient,
        )

        return synchronized(lock) {
            when {
                closed -> rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Compiler transport probe coordinator has been destroyed.",
                )
                identity in activeJobs || identity in tombstones -> rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_BUSY,
                    "The calling UID already used this requestId.",
                )
                activeJobs.size >= MAX_ACTIVE_JOBS -> rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_BUSY,
                    "The bounded compiler transport probe queue is full.",
                )
                tombstones.size >= MAX_TOMBSTONES -> rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_BUSY,
                    "The bounded compiler transport probe history is full; restart the " +
                        "debug service before admitting another probe.",
                )
                !callbackBinder.isBinderAlive -> rejected(
                    request.requestId,
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Compilation callback Binder is not alive.",
                )
                else -> {
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
                        rejected(
                            request.requestId,
                            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            "Compilation callback Binder died during admission.",
                        )
                    } else {
                        activeJobs[identity] = job
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
        var schedulingFailure: Throwable? = null
        val acknowledged = synchronized(lock) {
            val job = activeJobs[identity] ?: return@synchronized false
            if (job.jobToken != jobToken) return@synchronized false
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
        val active = synchronized(lock) {
            activeJobs[identity]
        }
        if (active != null) {
            if (active.jobToken != jobToken) return false
            finish(
                identity = identity,
                expectedToken = jobToken,
                terminal = ProbeTerminal.CANCELLED,
                deliverCallback = true,
            )
            return true
        }
        return synchronized(lock) {
            tombstones[identity]?.jobToken == jobToken
        }
    }

    fun close() {
        val jobs = synchronized(lock) {
            if (closed) return
            closed = true
            activeJobs.values.toList()
        }
        jobs.forEach { job ->
            finish(
                identity = job.identity,
                expectedToken = job.jobToken,
                terminal = ProbeTerminal.SERVICE_DESTROYED,
                deliverCallback = false,
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
            val candidate = activeJobs[identity]
            if (
                candidate == null ||
                candidate.jobToken != expectedToken ||
                (requireUnarmed && candidate.terminalWorkArmed)
            ) {
                return
            }

            /*
             * Retain the sole bounded admission slot until both service-side archive
             * descriptors are closed. Otherwise a concurrent Binder admission could observe
             * an empty active map while this job was still closing and temporarily exceed the
             * coordinator-owned two-descriptor accepted-job bound. This does not count PFDs
             * still being unmarshalled or synchronously rejected by other Binder threads.
             * These are local cleanup syscalls; the remote callback remains outside the lock.
             */
            candidate.unacknowledgedFuture?.cancel(false)
            candidate.timeoutFuture?.cancel(false)
            candidate.completionFuture?.cancel(false)
            runCatching { Os.ftruncate(candidate.outputArchive.fileDescriptor, 0L) }
            candidate.inputArchive.closeQuietly()
            candidate.outputArchive.closeQuietly()
            runCatching {
                candidate.callbackBinder.unlinkToDeath(candidate.deathRecipient, 0)
            }
            activeJobs.remove(identity)
            tombstones[identity] = ProbeTombstone(
                jobToken = candidate.jobToken,
                terminal = terminal,
            )
            candidate
        }

        if (deliverCallback && job.callbackBinder.isBinderAlive) {
            runCatching {
                job.callback.onCompleted(
                    terminal.toResultBundle(
                        requestId = identity.requestId,
                        elapsedMs = (
                            SystemClock.elapsedRealtime() - job.startedAt
                            ).coerceAtLeast(0L),
                    ),
                )
            }
        }
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

    private fun newJobToken(): String {
        val bytes = ByteArray(JOB_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
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
        val jobToken: String,
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
    )

    private data class ProbeTombstone(
        val jobToken: String,
        val terminal: ProbeTerminal,
    )

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
    }

    private companion object {
        const val JOB_TOKEN_BYTES = 32
        const val MAX_ACTIVE_JOBS = 1
        const val MAX_UNACKNOWLEDGED_LIFETIME_MS = 5_000L
        const val MAX_TOMBSTONES = 256
    }
}

internal data class TransportProbeAdmissionDecision(
    val requestId: String,
    val accepted: Boolean,
    val jobToken: String?,
    val errorCode: String,
    val errorMessage: String,
)

private fun ParcelFileDescriptor.closeQuietly() {
    runCatching { close() }
}
