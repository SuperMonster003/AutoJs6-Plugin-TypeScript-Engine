package io.github.supermonster003.autojs6.plugin.typescript

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.PersistableBundle
import android.os.Process
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * System-owned lifecycle for an exact pre-arm and its durable-quarantine reconciliation.
 *
 * This service deliberately does not terminate a live poisoned epoch. A PREARM_EXACT generation
 * may wait while that epoch commits its marker; after the marker exists, only ABSENT/REUSED may
 * exact-clear it; SAME/UNREADABLE remain quarantined under JobScheduler's exponential backoff.
 */
class R2CompilerProcessSupervisorJobService : JobService() {

    private val generations =
        R2CompilerProcessSupervisorJobGenerationGate<RunningJob>(
            generationToken = { job -> job.generationToken },
            retire = { job ->
                job.clearPhase.compareAndSet(
                    ClearPhase.RUNNING,
                    ClearPhase.STOPPED,
                )
                job.stopped.set(true)
            },
        )

    override fun onStartJob(params: JobParameters): Boolean {
        val job = RunningJob(
            parameters = params,
            work = R2CompilerProcessSupervisorJobWork.from(params.extras),
        )
        R2CompilerProcessSupervisorJobLifecycleJournal.recordAsync(
            context = this,
            event =
                R2CompilerProcessSupervisorJobLifecycleEvent
                    .START_CALLBACK,
            stopDecision = null,
            workToken = job.work?.workToken,
            currentWorkToken =
                generations.currentOrNull()?.work?.workToken,
        )
        val start = generations.start(job)
        when (start.decision) {
            R2CompilerProcessSupervisorJobStartDecision.DUPLICATE -> {
                /*
                 * JobScheduler serializes attempts for one persisted work item. Do not create a
                 * second worker if a framework anomaly delivers the same token concurrently.
                 */
                return false
            }
            R2CompilerProcessSupervisorJobStartDecision
                .REPLACED_CURRENT -> {
                val replaced = requireNotNull(start.currentAtDecision)
                R2CompilerProcessSupervisorJobLifecycleJournal.recordAsync(
                    context = this,
                    event =
                        R2CompilerProcessSupervisorJobLifecycleEvent
                            .START_REPLACED_CURRENT,
                    stopDecision = null,
                    workToken = job.work?.workToken,
                    currentWorkToken = replaced.work?.workToken,
                )
            }
            R2CompilerProcessSupervisorJobStartDecision.INSTALLED -> Unit
        }
        val started = runCatching {
            Thread(
                {
                    runCatching {
                        runJob(job)
                    }.onFailure {
                        finishWorker(job, wantsReschedule = true)
                    }
                },
                "typescript-r2-supervisor-owner",
            ).apply {
                isDaemon = true
                start()
            }
        }.isSuccess
        if (!started) {
            /*
             * Returning true first establishes an asynchronous job. Completing it from the main
             * queue then applies the configured JobScheduler backoff without blocking this
             * lifecycle callback.
             */
            finishWorker(job, wantsReschedule = true)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val stoppedWork =
            R2CompilerProcessSupervisorJobWork.from(params.extras)
        val stoppedToken =
            stoppedWork?.workToken ?: MALFORMED_GENERATION
        /*
         * The gate's retire callback contains the stop/clear linearization CAS. The journal
         * observer runs first, preserving callback evidence without participating in authority.
         */
        val stop = generations.stop(stoppedToken) { decision ->
            R2CompilerProcessSupervisorJobLifecycleJournal.recordAsync(
                context = this,
                event =
                    R2CompilerProcessSupervisorJobLifecycleEvent
                        .STOP_CALLBACK,
                stopDecision = decision.decision,
                workToken = stoppedWork?.workToken,
                currentWorkToken =
                    decision.currentAtDecision?.work?.workToken,
            )
        }
        if (
            stop.decision !=
            R2CompilerProcessSupervisorJobStopDecision.CURRENT
        ) {
            /*
             * Scheduling the fixed Job ID with a new work token may deliver the retired stop
             * after the replacement has started. It must not stop or reschedule that replacement.
             */
            return false
        }
        /*
         * Never perform filesystem I/O on the JobService main callback. A redundant reschedule is
         * harmless: the worker observes an absent marker and completes as NO_WORK.
         */
        return true
    }

    private fun runJob(
        job: RunningJob,
    ) {
        val work = job.work
        val inspection = R2CompilerProcessQuarantine.inspectForJob(
            context = this,
            prearmDeadline = work
                ?.takeIf {
                    it.mode ==
                        R2CompilerProcessSupervisorJobMode.PREARM_EXACT
                }
                ?.prearmDeadline,
        )
        when (val snapshot = inspection.snapshot) {
            R2CompilerProcessQuarantineSnapshot.CorruptOrUnreadable -> {
                recordAndFinish(
                    job = job,
                    outcome = R2CompilerProcessSupervisorOwnerOutcome.RETRY,
                    oldIdentity = null,
                    observation = null,
                    wantsReschedule = true,
                )
            }
            is R2CompilerProcessQuarantineSnapshot.Exact -> {
                when {
                    work == null -> {
                        recordAndFinish(
                            job = job,
                            outcome =
                                R2CompilerProcessSupervisorOwnerOutcome.RETRY,
                            oldIdentity = snapshot.identity,
                            observation = null,
                            wantsReschedule = true,
                        )
                    }
                    !work.identity.matchesExact(snapshot.identity) -> {
                        recordAndFinish(
                            job = job,
                            outcome =
                                R2CompilerProcessSupervisorOwnerOutcome.STALE_JOB,
                            oldIdentity = snapshot.identity,
                            observation = null,
                            wantsReschedule = false,
                        )
                    }
                    else -> reconcileExact(job, snapshot.identity)
                }
            }
            R2CompilerProcessQuarantineSnapshot.Absent -> {
                runWithoutMarker(
                    job = job,
                    work = work,
                    prearmWindowOpen = inspection.prearmWindowOpen,
                )
            }
        }
    }

    private fun runWithoutMarker(
        job: RunningJob,
        work: R2CompilerProcessSupervisorJobWork?,
        prearmWindowOpen: Boolean,
    ) {
        if (work == null) {
            recordAndFinish(
                job = job,
                /*
                 * With no marker there is no admission fence to protect and malformed/legacy
                 * extras cannot identify a pre-arm that might still commit one. Retire it so an
                 * old persisted schema cannot occupy the fixed Job ID forever after upgrade.
                 */
                outcome = R2CompilerProcessSupervisorOwnerOutcome.NO_WORK,
                oldIdentity = null,
                observation = null,
                wantsReschedule = false,
            )
            return
        }
        if (
            work.mode !=
            R2CompilerProcessSupervisorJobMode.PREARM_EXACT
        ) {
            recordAndFinish(
                job = job,
                outcome = R2CompilerProcessSupervisorOwnerOutcome.NO_WORK,
                oldIdentity = work.identity,
                observation = null,
                wantsReschedule = false,
            )
            return
        }
        val observation = R2CompilerProcessProcfs().observe(work.identity)
        when (
            R2CompilerProcessSupervisorPrearmDecision.decide(
                prearmWindowOpen = prearmWindowOpen,
                observation = observation,
            )
        ) {
            R2CompilerProcessSupervisorPrearmDecision.RETRY -> {
                if (
                    !recordCurrent(
                        job = job,
                        outcome =
                            R2CompilerProcessSupervisorOwnerOutcome
                                .PREARM_RETRY,
                        oldIdentity = work.identity,
                        observation = observation,
                    )
                ) {
                    return
                }
                awaitPrearmTransition(job, work)
            }
            R2CompilerProcessSupervisorPrearmDecision.NO_WORK -> {
                recordAndFinish(
                    job = job,
                    outcome =
                        R2CompilerProcessSupervisorOwnerOutcome.NO_WORK,
                    oldIdentity = work.identity,
                    observation = observation,
                    wantsReschedule = false,
                )
            }
            R2CompilerProcessSupervisorPrearmDecision.RECHECK_MARKER -> {
                /*
                 * The compiler may commit the marker between our first absent snapshot and this
                 * procfs death proof. Recheck under the marker lock before consuming the work.
                 */
                when (
                    val settled =
                        R2CompilerProcessQuarantine.inspectForJob(
                            context = this,
                            prearmDeadline = null,
                        ).snapshot
                ) {
                    R2CompilerProcessQuarantineSnapshot.Absent -> {
                        recordAndFinish(
                            job = job,
                            outcome =
                                R2CompilerProcessSupervisorOwnerOutcome.NO_WORK,
                            oldIdentity = work.identity,
                            observation = observation,
                            wantsReschedule = false,
                        )
                    }
                    R2CompilerProcessQuarantineSnapshot.CorruptOrUnreadable -> {
                        recordAndFinish(
                            job = job,
                            outcome =
                                R2CompilerProcessSupervisorOwnerOutcome.RETRY,
                            oldIdentity = work.identity,
                            observation = observation,
                            wantsReschedule = true,
                        )
                    }
                    is R2CompilerProcessQuarantineSnapshot.Exact -> {
                        if (
                            work.identity.matchesExact(settled.identity)
                        ) {
                            reconcileExact(job, settled.identity)
                        } else {
                            recordAndFinish(
                                job = job,
                                outcome =
                                    R2CompilerProcessSupervisorOwnerOutcome
                                        .STALE_JOB,
                                oldIdentity = settled.identity,
                                observation = observation,
                                wantsReschedule = false,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun awaitPrearmTransition(
        job: RunningJob,
        work: R2CompilerProcessSupervisorJobWork,
    ) {
        while (!job.stopped.get()) {
            val inspection = R2CompilerProcessQuarantine.inspectForJob(
                context = this,
                prearmDeadline = work.prearmDeadline,
            )
            when (val snapshot = inspection.snapshot) {
                R2CompilerProcessQuarantineSnapshot.CorruptOrUnreadable -> {
                    recordAndFinish(
                        job = job,
                        outcome =
                            R2CompilerProcessSupervisorOwnerOutcome.RETRY,
                        oldIdentity = work.identity,
                        observation = null,
                        wantsReschedule = true,
                    )
                    return
                }
                is R2CompilerProcessQuarantineSnapshot.Exact -> {
                    if (!work.identity.matchesExact(snapshot.identity)) {
                        recordAndFinish(
                            job = job,
                            outcome =
                                R2CompilerProcessSupervisorOwnerOutcome.STALE_JOB,
                            oldIdentity = snapshot.identity,
                            observation = null,
                            wantsReschedule = false,
                        )
                        return
                    }
                    val observation =
                        R2CompilerProcessProcfs().observe(work.identity)
                    if (
                        observation ==
                        R2CompilerProcessObservation.ABSENT ||
                        observation ==
                        R2CompilerProcessObservation.REUSED
                    ) {
                        reconcileExact(job, snapshot.identity)
                        return
                    }
                    if (
                        !R2CompilerProcessEpoch.hasUsableCommandDeadline(
                            requireNotNull(work.prearmDeadline),
                        )
                    ) {
                        recordAndFinish(
                            job = job,
                            outcome =
                                R2CompilerProcessSupervisorOwnerOutcome.RETRY,
                            oldIdentity = work.identity,
                            observation = observation,
                            wantsReschedule = true,
                        )
                        return
                    }
                }
                R2CompilerProcessQuarantineSnapshot.Absent -> {
                    if (!inspection.prearmWindowOpen) {
                        recordAndFinish(
                            job = job,
                            outcome =
                                R2CompilerProcessSupervisorOwnerOutcome.NO_WORK,
                            oldIdentity = work.identity,
                            observation = null,
                            wantsReschedule = false,
                        )
                        return
                    }
                    val observation =
                        R2CompilerProcessProcfs().observe(work.identity)
                    if (
                        observation ==
                        R2CompilerProcessObservation.ABSENT ||
                        observation ==
                        R2CompilerProcessObservation.REUSED
                    ) {
                        /*
                         * Recheck after the procfs proof so a marker committed between the first
                         * absent snapshot and process death is never consumed as NO_WORK.
                         */
                        when (
                            val settled =
                                R2CompilerProcessQuarantine.inspectForJob(
                                    context = this,
                                    prearmDeadline = null,
                                ).snapshot
                        ) {
                            R2CompilerProcessQuarantineSnapshot.Absent -> {
                                recordAndFinish(
                                    job = job,
                                    outcome =
                                        R2CompilerProcessSupervisorOwnerOutcome
                                            .NO_WORK,
                                    oldIdentity = work.identity,
                                    observation = observation,
                                    wantsReschedule = false,
                                )
                                return
                            }
                            R2CompilerProcessQuarantineSnapshot
                                .CorruptOrUnreadable -> {
                                recordAndFinish(
                                    job = job,
                                    outcome =
                                        R2CompilerProcessSupervisorOwnerOutcome
                                            .RETRY,
                                    oldIdentity = work.identity,
                                    observation = observation,
                                    wantsReschedule = true,
                                )
                                return
                            }
                            is R2CompilerProcessQuarantineSnapshot.Exact -> {
                                if (
                                    !work.identity.matchesExact(
                                        settled.identity,
                                    )
                                ) {
                                    recordAndFinish(
                                        job = job,
                                        outcome =
                                            R2CompilerProcessSupervisorOwnerOutcome
                                                .STALE_JOB,
                                        oldIdentity = settled.identity,
                                        observation = observation,
                                        wantsReschedule = false,
                                    )
                                    return
                                }
                                /*
                                 * Loop once more so exact reconciliation receives its normal
                                 * running/stop authorization fence.
                                 */
                            }
                        }
                    }
                }
            }
            SystemClock.sleep(PREARM_POLL_INTERVAL_MILLIS)
        }
    }

    private fun reconcileExact(
        job: RunningJob,
        oldIdentity: R2CompilerProcessEpochIdentity,
    ) {
        if (
            !recordCurrent(
                job = job,
                outcome = R2CompilerProcessSupervisorOwnerOutcome.RUNNING,
                oldIdentity = oldIdentity,
                observation = null,
            )
        ) {
            return
        }
        var observation: R2CompilerProcessObservation? =
            R2CompilerProcessProcfs().observe(oldIdentity)
        val recovered = runCatching {
            R2CompilerProcessSupervisor(
                context = this,
                beforeSuccessorFenceClear = {
                    check(
                        generations.isCurrent(job) &&
                        job.clearPhase.compareAndSet(
                            ClearPhase.RUNNING,
                            ClearPhase.AUTHORIZED,
                        ),
                    ) {
                        "Supervisor owner was stopped before successor-fence clear."
                    }
                },
            ).reconcilePersistedQuarantine(expectedIdentity = oldIdentity)
        }.onSuccess { proof ->
            observation = proof.finalObservation
        }.isSuccess
        val markerRemains = R2CompilerProcessQuarantine.hasMarker(this)
        val wantsReschedule = !recovered && markerRemains
        recordCurrent(
            job = job,
            outcome = when {
                recovered -> R2CompilerProcessSupervisorOwnerOutcome.RECONCILED
                markerRemains -> R2CompilerProcessSupervisorOwnerOutcome.RETRY
                else -> R2CompilerProcessSupervisorOwnerOutcome.NO_WORK
            },
            oldIdentity = oldIdentity,
            observation = observation,
        )
        finishWorker(job, wantsReschedule)
    }

    private fun recordAndFinish(
        job: RunningJob,
        outcome: R2CompilerProcessSupervisorOwnerOutcome,
        oldIdentity: R2CompilerProcessEpochIdentity?,
        observation: R2CompilerProcessObservation?,
        wantsReschedule: Boolean,
    ) {
        recordCurrent(
            job = job,
            outcome = outcome,
            oldIdentity = oldIdentity,
            observation = observation,
        )
        finishWorker(job, wantsReschedule)
    }

    private fun recordCurrent(
        job: RunningJob,
        outcome: R2CompilerProcessSupervisorOwnerOutcome,
        oldIdentity: R2CompilerProcessEpochIdentity?,
        observation: R2CompilerProcessObservation?,
    ): Boolean {
        if (!generations.isCurrent(job) || job.stopped.get()) return false
        R2CompilerProcessSupervisorOwnerTelemetry.record(
            context = this,
            outcome = outcome,
            oldIdentity = oldIdentity,
            observation = observation,
            workToken = job.work?.workToken,
        )
        return generations.isCurrent(job) && !job.stopped.get()
    }

    private fun finishWorker(
        job: RunningJob,
        wantsReschedule: Boolean,
    ) {
        if (!job.completionPosted.compareAndSet(false, true)) return
        /*
         * JobService callbacks and completion are serialized on the main looper. Keep this job
         * installed as the gate's current generation until the runnable executes, so onStopJob
         * cannot fall through a worker-CAS -> jobFinished gap. If the looper is already exiting,
         * process death leaves JobScheduler as the lifecycle owner and no stale completion is
         * emitted.
         */
        Handler(mainLooper).post {
            if (!generations.isCurrent(job)) return@post
            if (job.stopped.get()) {
                generations.compareAndClear(job)
                return@post
            }
            if (generations.compareAndClear(job)) {
                runCatching {
                    jobFinished(job.parameters, wantsReschedule)
                }
            }
        }
    }

    private class RunningJob(
        val parameters: JobParameters,
        val work: R2CompilerProcessSupervisorJobWork?,
        val stopped: AtomicBoolean = AtomicBoolean(false),
        val completionPosted: AtomicBoolean = AtomicBoolean(false),
        val clearPhase: AtomicReference<ClearPhase> =
            AtomicReference(ClearPhase.RUNNING),
    ) {
        val generationToken: String =
            work?.workToken ?: MALFORMED_GENERATION
    }

    private enum class ClearPhase {
        RUNNING,
        AUTHORIZED,
        STOPPED,
    }

    private companion object {
        const val MALFORMED_GENERATION = "<malformed>"
        const val PREARM_POLL_INTERVAL_MILLIS = 25L
    }
}

internal enum class R2CompilerProcessSupervisorJobStartDecision {
    INSTALLED,
    REPLACED_CURRENT,
    DUPLICATE,
}

internal data class R2CompilerProcessSupervisorJobStartResult<T : Any>(
    val decision: R2CompilerProcessSupervisorJobStartDecision,
    val currentAtDecision: T?,
)

internal data class R2CompilerProcessSupervisorJobStopResult<T : Any>(
    val decision: R2CompilerProcessSupervisorJobStopDecision,
    val currentAtDecision: T?,
)

/**
 * Token-generation ownership only; marker-clear authorization remains in each running job.
 *
 * Retiring before the current-generation CAS preserves the original stop/clear ordering: a
 * worker selected for replacement or stop first loses its RUNNING clear phase, then loses current
 * ownership. If stop wins the retire CAS, a marker-lock callback cannot authorize deletion; if
 * clear already won, its exact mutation remains authorized. Identity checks deliberately remain
 * referential after a token has selected a job.
 */
internal class R2CompilerProcessSupervisorJobGenerationGate<T : Any>(
    private val generationToken: (T) -> String,
    private val retire: (T) -> Unit,
) {

    private val current = AtomicReference<T?>(null)

    fun start(candidate: T): R2CompilerProcessSupervisorJobStartResult<T> {
        while (true) {
            val selected = current.get()
            if (selected == null) {
                if (current.compareAndSet(null, candidate)) {
                    return R2CompilerProcessSupervisorJobStartResult(
                        decision =
                            R2CompilerProcessSupervisorJobStartDecision
                                .INSTALLED,
                        currentAtDecision = null,
                    )
                }
                continue
            }
            if (generationToken(selected) == generationToken(candidate)) {
                return R2CompilerProcessSupervisorJobStartResult(
                    decision =
                        R2CompilerProcessSupervisorJobStartDecision
                            .DUPLICATE,
                    currentAtDecision = selected,
                )
            }
            retire(selected)
            if (current.compareAndSet(selected, candidate)) {
                return R2CompilerProcessSupervisorJobStartResult(
                    decision =
                        R2CompilerProcessSupervisorJobStartDecision
                            .REPLACED_CURRENT,
                    currentAtDecision = selected,
                )
            }
        }
    }

    fun stop(
        generationToken: String,
        beforeRetire:
            (R2CompilerProcessSupervisorJobStopResult<T>) -> Unit = {},
    ): R2CompilerProcessSupervisorJobStopResult<T> {
        val selected = current.get()
        val decision = when {
            selected == null ->
                R2CompilerProcessSupervisorJobStopDecision.NO_CURRENT
            this.generationToken(selected) != generationToken ->
                R2CompilerProcessSupervisorJobStopDecision.STALE
            else -> R2CompilerProcessSupervisorJobStopDecision.CURRENT
        }
        val result = R2CompilerProcessSupervisorJobStopResult(
            decision = decision,
            currentAtDecision = selected,
        )
        beforeRetire(result)
        if (
            decision ==
            R2CompilerProcessSupervisorJobStopDecision.CURRENT
        ) {
            val exact = requireNotNull(selected)
            retire(exact)
            current.compareAndSet(exact, null)
        }
        return result
    }

    fun currentOrNull(): T? = current.get()

    fun isCurrent(expected: T): Boolean = current.get() === expected

    fun compareAndClear(expected: T): Boolean =
        current.compareAndSet(expected, null)
}

internal enum class R2CompilerProcessSupervisorPrearmDecision {
    RETRY,
    RECHECK_MARKER,
    NO_WORK;

    companion object {

        fun decide(
            prearmWindowOpen: Boolean,
            observation: R2CompilerProcessObservation,
        ): R2CompilerProcessSupervisorPrearmDecision =
            when {
                !prearmWindowOpen -> NO_WORK
                observation == R2CompilerProcessObservation.SAME ||
                    observation == R2CompilerProcessObservation.UNREADABLE ->
                    RETRY
                else -> RECHECK_MARKER
            }
    }
}

internal enum class R2CompilerProcessSupervisorJobMode {
    PREARM_EXACT,
    RECOVERY_EXACT,
}

internal data class R2CompilerProcessSupervisorJobWork(
    val mode: R2CompilerProcessSupervisorJobMode,
    val workToken: String,
    val identity: R2CompilerProcessEpochIdentity,
    val prearmDeadline: Long?,
) {

    init {
        require(isCanonicalToken(workToken))
        require(
            when (mode) {
                R2CompilerProcessSupervisorJobMode.PREARM_EXACT ->
                    prearmDeadline != null && prearmDeadline > 0L
                R2CompilerProcessSupervisorJobMode.RECOVERY_EXACT ->
                    prearmDeadline == null
            },
        )
    }

    fun toExtras(): PersistableBundle =
        PersistableBundle().apply {
            putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            putString(KEY_MODE, mode.name)
            putString(KEY_WORK_TOKEN, workToken)
            putString(KEY_BOOT_ID, identity.bootId)
            putInt(KEY_UID, identity.uid)
            putInt(KEY_PID, identity.pid)
            putLong(KEY_START_TIME_TICKS, identity.startTimeTicks)
            putString(KEY_PROCESS_NAME, identity.processName)
            putString(KEY_NONCE, identity.nonce)
            prearmDeadline?.let {
                putLong(KEY_PREARM_DEADLINE, it)
            }
        }

    companion object {

        @Suppress("DEPRECATION")
        fun from(
            extras: PersistableBundle?,
        ): R2CompilerProcessSupervisorJobWork? {
            extras ?: return null
            return runCatching {
                val mode = (
                    extras.get(KEY_MODE) as? String
                    )?.let(R2CompilerProcessSupervisorJobMode::valueOf)
                    ?: return null
                val expectedKeys = when (mode) {
                    R2CompilerProcessSupervisorJobMode.PREARM_EXACT ->
                        COMMON_KEYS + KEY_PREARM_DEADLINE
                    R2CompilerProcessSupervisorJobMode.RECOVERY_EXACT ->
                        COMMON_KEYS
                }
                if (extras.keySet() != expectedKeys) return null
                if (
                    extras.get(KEY_SCHEMA_VERSION) as? Int !=
                    SCHEMA_VERSION
                ) {
                    return null
                }
                val workToken =
                    extras.get(KEY_WORK_TOKEN) as? String ?: return null
                if (!isCanonicalToken(workToken)) return null
                val identity = R2CompilerProcessEpochIdentity(
                    bootId =
                        extras.get(KEY_BOOT_ID) as? String ?: return null,
                    uid = extras.get(KEY_UID) as? Int ?: return null,
                    pid = extras.get(KEY_PID) as? Int ?: return null,
                    startTimeTicks =
                        extras.get(KEY_START_TIME_TICKS) as? Long
                            ?: return null,
                    processName =
                        extras.get(KEY_PROCESS_NAME) as? String ?: return null,
                    nonce =
                        extras.get(KEY_NONCE) as? String ?: return null,
                )
                if (
                    identity.uid != Process.myUid() ||
                    identity.processName !=
                    "${BuildConfig.APPLICATION_ID}:typescript_compiler"
                ) {
                    return null
                }
                R2CompilerProcessSupervisorJobWork(
                    mode = mode,
                    workToken = workToken,
                    identity = identity,
                    prearmDeadline = when (mode) {
                        R2CompilerProcessSupervisorJobMode.PREARM_EXACT ->
                            extras.get(KEY_PREARM_DEADLINE) as? Long
                                ?: return null
                        R2CompilerProcessSupervisorJobMode.RECOVERY_EXACT ->
                            null
                    },
                )
            }.getOrNull()
        }

        internal fun isCanonicalToken(value: String): Boolean =
            value.length == WORK_TOKEN_CHARS &&
                value.all { character ->
                    character in '0'..'9' || character in 'a'..'f'
                }

        private const val SCHEMA_VERSION = 1
        private const val KEY_SCHEMA_VERSION = "r2_schema"
        private const val KEY_MODE = "r2_mode"
        private const val KEY_WORK_TOKEN = "r2_work_token"
        private const val KEY_BOOT_ID = "r2_boot_id"
        private const val KEY_UID = "r2_uid"
        private const val KEY_PID = "r2_pid"
        private const val KEY_START_TIME_TICKS = "r2_start_time"
        private const val KEY_PROCESS_NAME = "r2_process_name"
        private const val KEY_NONCE = "r2_nonce"
        private const val KEY_PREARM_DEADLINE = "r2_prearm_deadline"
        private val COMMON_KEYS = setOf(
            KEY_SCHEMA_VERSION,
            KEY_MODE,
            KEY_WORK_TOKEN,
            KEY_BOOT_ID,
            KEY_UID,
            KEY_PID,
            KEY_START_TIME_TICKS,
            KEY_PROCESS_NAME,
            KEY_NONCE,
        )
        private const val WORK_TOKEN_CHARS = 64
    }
}

internal data class R2CompilerProcessSupervisorJobLease(
    val work: R2CompilerProcessSupervisorJobWork,
)

internal object R2CompilerProcessSupervisorJobScheduler {

    fun prearmExact(
        context: Context,
        identity: R2CompilerProcessEpochIdentity,
        commandDeadline: Long,
    ): R2CompilerProcessSupervisorJobLease? {
        if (
            !R2CompilerProcessEpoch.hasUsableCommandDeadline(
                commandDeadline,
            )
        ) {
            return null
        }
        val work = newWork(
            mode = R2CompilerProcessSupervisorJobMode.PREARM_EXACT,
            identity = identity,
            prearmDeadline = commandDeadline,
        )
        val lease = R2CompilerProcessSupervisorJobLease(work)
        return withJobLock(context, failure = null) {
            val scheduler = scheduler(context) ?: return@withJobLock null
            val component = component(context)
            val pending = runCatching {
                scheduler.getPendingJob(JOB_ID)
            }.getOrElse {
                return@withJobLock null
            }
            if (pending != null && pending.service != component) {
                return@withJobLock null
            }
            if (!schedule(scheduler, component, work)) {
                return@withJobLock null
            }
            val confirmed = runCatching {
                scheduler.getPendingJob(JOB_ID)
            }.getOrNull()
            lease.takeIf {
                confirmed.matches(component, work)
            }
        }
    }

    fun scheduleIfQuarantined(context: Context): Boolean {
        return withJobLock(context, failure = false) {
            val markerIdentity = (
                R2CompilerProcessQuarantine.inspectForJob(
                    context = context,
                    prearmDeadline = null,
                ).snapshot as?
                    R2CompilerProcessQuarantineSnapshot.Exact
                )?.identity ?: return@withJobLock false
            val scheduler = scheduler(context) ?: return@withJobLock false
            val component = component(context)
            val pending = runCatching {
                scheduler.getPendingJob(JOB_ID)
            }.getOrElse {
                return@withJobLock false
            }
            if (pending != null && pending.service != component) {
                return@withJobLock false
            }
            val pendingWork =
                pending?.let { R2CompilerProcessSupervisorJobWork.from(it.extras) }
            if (
                pending.matches(component, pendingWork) &&
                pendingWork != null &&
                pendingWork.identity.matchesExact(markerIdentity)
            ) {
                return@withJobLock true
            }
            schedule(
                scheduler = scheduler,
                component = component,
                work = newWork(
                    mode =
                        R2CompilerProcessSupervisorJobMode.RECOVERY_EXACT,
                    identity = markerIdentity,
                    prearmDeadline = null,
                ),
            )
        }
    }

    fun hasExactJob(
        context: Context,
        lease: R2CompilerProcessSupervisorJobLease,
    ): Boolean =
        withJobLock(context, failure = false) {
            val scheduler = scheduler(context) ?: return@withJobLock false
            val component = component(context)
            runCatching {
                scheduler.getPendingJob(JOB_ID)
            }.getOrNull().matches(component, lease.work)
        }

    fun hasJobForIdentityForTesting(
        context: Context,
        identity: R2CompilerProcessEpochIdentity,
    ): Boolean {
        check(BuildConfig.DEBUG)
        return withJobLock(context, failure = false) {
            val scheduler = scheduler(context) ?: return@withJobLock false
            val pending = runCatching {
                scheduler.getPendingJob(JOB_ID)
            }.getOrNull()
            val work = pending
                ?.takeIf { it.service == component(context) }
                ?.let { R2CompilerProcessSupervisorJobWork.from(it.extras) }
            work?.identity?.matchesExact(identity) == true
        }
    }

    fun hasAnyJobForTesting(context: Context): Boolean {
        check(BuildConfig.DEBUG)
        return withJobLock(context, failure = true) {
            val scheduler = scheduler(context) ?: return@withJobLock true
            runCatching {
                scheduler.getPendingJob(JOB_ID) != null
            }.getOrDefault(true)
        }
    }

    fun pendingWorkForTesting(
        context: Context,
    ): R2CompilerProcessSupervisorJobWork? {
        check(BuildConfig.DEBUG)
        return withJobLock(context, failure = null) {
            val scheduler = scheduler(context) ?: return@withJobLock null
            val pending = runCatching {
                scheduler.getPendingJob(JOB_ID)
            }.getOrNull() ?: return@withJobLock null
            if (pending.service != component(context)) {
                return@withJobLock null
            }
            R2CompilerProcessSupervisorJobWork.from(pending.extras)
        }
    }

    fun retirePrearmBestEffort(
        context: Context,
        lease: R2CompilerProcessSupervisorJobLease,
    ): Boolean =
        withJobLock(context, failure = false) {
            if (
                R2CompilerProcessQuarantine.inspectForJob(
                    context = context,
                    prearmDeadline = null,
                ).snapshot !== R2CompilerProcessQuarantineSnapshot.Absent
            ) {
                return@withJobLock false
            }
            val scheduler = scheduler(context) ?: return@withJobLock false
            val pending = runCatching {
                scheduler.getPendingJob(JOB_ID)
            }.getOrNull()
            if (!pending.matches(component(context), lease.work)) {
                return@withJobLock false
            }
            runCatching {
                scheduler.cancel(JOB_ID)
                true
            }.getOrDefault(false)
        }

    private fun schedule(
        scheduler: JobScheduler,
        component: ComponentName,
        work: R2CompilerProcessSupervisorJobWork,
    ): Boolean {
        val job = JobInfo.Builder(JOB_ID, component)
            .setExtras(work.toExtras())
            .setMinimumLatency(INITIAL_LATENCY_MILLIS)
            .setOverrideDeadline(INITIAL_DEADLINE_MILLIS)
            .setBackoffCriteria(
                INITIAL_BACKOFF_MILLIS,
                JobInfo.BACKOFF_POLICY_EXPONENTIAL,
            )
            .setPersisted(true)
            .build()
        return runCatching {
            scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
        }.getOrDefault(false)
    }

    internal fun cancelForTesting(context: Context) {
        check(BuildConfig.DEBUG)
        withJobLock(context, failure = Unit) {
            scheduler(context)?.cancel(JOB_ID)
        }
    }

    private fun newWork(
        mode: R2CompilerProcessSupervisorJobMode,
        identity: R2CompilerProcessEpochIdentity,
        prearmDeadline: Long?,
    ): R2CompilerProcessSupervisorJobWork {
        val tokenBytes = ByteArray(WORK_TOKEN_BYTES)
        RANDOM.nextBytes(tokenBytes)
        val token = try {
            buildString(tokenBytes.size * 2) {
                tokenBytes.forEach { value ->
                    val unsigned = value.toInt() and 0xff
                    append(HEX[unsigned ushr 4])
                    append(HEX[unsigned and 0x0f])
                }
            }
        } finally {
            tokenBytes.fill(0)
        }
        return R2CompilerProcessSupervisorJobWork(
            mode = mode,
            workToken = token,
            identity = identity,
            prearmDeadline = prearmDeadline,
        )
    }

    private fun JobInfo?.matches(
        expectedComponent: ComponentName,
        expectedWork: R2CompilerProcessSupervisorJobWork?,
    ): Boolean =
        this != null &&
            expectedWork != null &&
            service == expectedComponent &&
            R2CompilerProcessSupervisorJobWork.from(extras) == expectedWork

    private fun scheduler(context: Context): JobScheduler? =
        context.applicationContext
            .getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

    private fun component(context: Context): ComponentName =
        ComponentName(
            context.applicationContext,
            R2CompilerProcessSupervisorJobService::class.java,
        )

    private fun <T> withJobLock(
        context: Context,
        failure: T,
        block: () -> T,
    ): T =
        synchronized(PROCESS_LOCK) {
            try {
                RandomAccessFile(lockFile(context), "rw").use { file ->
                    val lock = file.channel.lock()
                    try {
                        block()
                    } finally {
                        runCatching { lock.release() }
                    }
                }
            } catch (_: Throwable) {
                failure
            }
        }

    private fun lockFile(context: Context): File =
        File(
            context.applicationContext.noBackupFilesDir,
            JOB_LOCK_FILE_NAME,
        )

    private const val JOB_ID = 0x52325336
    private const val INITIAL_LATENCY_MILLIS = 2_000L
    private const val INITIAL_DEADLINE_MILLIS = 5_000L
    private const val INITIAL_BACKOFF_MILLIS = 10_000L
    private const val JOB_LOCK_FILE_NAME =
        "r2-compiler-process-supervisor-job.lock"
    private const val WORK_TOKEN_BYTES = 32
    private const val HEX = "0123456789abcdef"
    private val RANDOM = SecureRandom()
    private val PROCESS_LOCK = Any()
}

internal enum class R2CompilerProcessSupervisorJobLifecycleEvent {
    START_CALLBACK,
    START_REPLACED_CURRENT,
    STOP_CALLBACK,
}

internal enum class R2CompilerProcessSupervisorJobStopDecision {
    CURRENT,
    STALE,
    NO_CURRENT,
}

internal data class R2CompilerProcessSupervisorJobLifecycleRecord(
    val processSequence: Long,
    val elapsedRealtimeNanos: Long,
    val event: R2CompilerProcessSupervisorJobLifecycleEvent,
    val stopDecision: R2CompilerProcessSupervisorJobStopDecision?,
    val workTokenSha256: String?,
    val currentWorkTokenSha256: String?,
    val owner: R2ObservedCompilerProcess,
    val ownerPid: Int,
)

internal data class R2CompilerProcessSupervisorJobLifecycleCutoff(
    val bootId: String,
    val elapsedRealtimeNanos: Long,
) {

    init {
        require(
            R2CompilerProcessEpochIdentity.isCanonicalBootId(bootId),
        )
        require(elapsedRealtimeNanos >= 0L)
    }
}

internal object R2CompilerProcessSupervisorJobLifecycleCutoffPolicy {

    fun forCurrentBoot(
        existing: R2CompilerProcessSupervisorJobLifecycleCutoff?,
        currentBootId: String,
    ): R2CompilerProcessSupervisorJobLifecycleCutoff {
        require(
            R2CompilerProcessEpochIdentity
                .isCanonicalBootId(currentBootId),
        )
        return existing?.takeIf { it.bootId == currentBootId }
            ?: R2CompilerProcessSupervisorJobLifecycleCutoff(
                bootId = currentBootId,
                elapsedRealtimeNanos = 0L,
            )
    }

    fun advanceForClear(
        existing: R2CompilerProcessSupervisorJobLifecycleCutoff?,
        currentBootId: String,
        elapsedRealtimeNanos: Long,
    ): R2CompilerProcessSupervisorJobLifecycleCutoff {
        require(elapsedRealtimeNanos > 0L)
        val current = forCurrentBoot(existing, currentBootId)
        return current.copy(
            elapsedRealtimeNanos = maxOf(
                current.elapsedRealtimeNanos,
                elapsedRealtimeNanos,
            ),
        )
    }

    fun accepts(
        cutoff: R2CompilerProcessSupervisorJobLifecycleCutoff,
        eventBootId: String,
        eventElapsedRealtimeNanos: Long,
    ): Boolean =
        eventBootId == cutoff.bootId &&
            eventElapsedRealtimeNanos > cutoff.elapsedRealtimeNanos
}

internal object R2CompilerProcessSupervisorJobLifecycleRecordPolicy {

    fun hasValidTokenRelation(
        event: R2CompilerProcessSupervisorJobLifecycleEvent,
        stopDecision: R2CompilerProcessSupervisorJobStopDecision?,
        workToken: String?,
        currentWorkToken: String?,
    ): Boolean {
        if (
            (event ==
                R2CompilerProcessSupervisorJobLifecycleEvent
                    .STOP_CALLBACK) !=
            (stopDecision != null)
        ) {
            return false
        }
        return when (event) {
            R2CompilerProcessSupervisorJobLifecycleEvent
                .START_CALLBACK -> true
            R2CompilerProcessSupervisorJobLifecycleEvent
                .START_REPLACED_CURRENT ->
                workToken != currentWorkToken
            R2CompilerProcessSupervisorJobLifecycleEvent
                .STOP_CALLBACK -> when (stopDecision) {
                    R2CompilerProcessSupervisorJobStopDecision.CURRENT ->
                        workToken == currentWorkToken
                    R2CompilerProcessSupervisorJobStopDecision.STALE ->
                        workToken != currentWorkToken
                    R2CompilerProcessSupervisorJobStopDecision.NO_CURRENT ->
                        currentWorkToken == null
                    null -> false
                }
        }
    }
}

/**
 * DEBUG-only, bounded and non-authoritative evidence for framework JobService callbacks.
 *
 * Callback threads only capture bounded scalar/token data and enqueue it in memory. A daemon
 * writer performs procfs capture, hashing, parsing and app-private file I/O. The AtomicFile state
 * carries a boot-scoped monotonic cutoff, so a batch captured before clear cannot be written back
 * afterward. Nothing in this journal participates in running-job selection, marker clear or
 * admission authorization.
 */
internal object R2CompilerProcessSupervisorJobLifecycleJournal {

    fun recordAsync(
        context: Context,
        event: R2CompilerProcessSupervisorJobLifecycleEvent,
        stopDecision: R2CompilerProcessSupervisorJobStopDecision?,
        workToken: String?,
        currentWorkToken: String?,
    ) {
        if (!BuildConfig.DEBUG) return
        if (
            (event ==
                R2CompilerProcessSupervisorJobLifecycleEvent.STOP_CALLBACK) !=
            (stopDecision != null)
        ) {
            return
        }
        val boundedWorkToken = workToken.canonicalWorkTokenOrNull()
        val boundedCurrentToken =
            currentWorkToken.canonicalWorkTokenOrNull()
        if (
            !R2CompilerProcessSupervisorJobLifecycleRecordPolicy
                .hasValidTokenRelation(
                    event = event,
                    stopDecision = stopDecision,
                    workToken = boundedWorkToken,
                    currentWorkToken = boundedCurrentToken,
                )
        ) {
            return
        }
        val sequence = PROCESS_SEQUENCE.incrementAndGet()
        if (sequence <= 0L) return
        val elapsedRealtimeNanos =
            SystemClock.elapsedRealtimeNanos()
        if (elapsedRealtimeNanos <= 0L) return
        val pending = PendingRecord(
            processSequence = sequence,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            event = event,
            stopDecision = stopDecision,
            workToken = boundedWorkToken,
            currentWorkToken = boundedCurrentToken,
            ownerPid = Process.myPid(),
        )
        val shouldStartWriter = synchronized(PENDING_LOCK) {
            while (PENDING.size >= MAX_PENDING_RECORDS) {
                PENDING.removeFirst()
            }
            PENDING.addLast(pending)
            if (writerActive) {
                false
            } else {
                writerActive = true
                true
            }
        }
        if (shouldStartWriter) {
            startWriter(context.applicationContext)
        }
    }

    fun readForTesting(
        context: Context,
    ): List<R2CompilerProcessSupervisorJobLifecycleRecord>? {
        check(BuildConfig.DEBUG)
        val currentBootId = captureCurrentBootId() ?: return null
        return withJournalLock(context, failure = null) {
            when (val read = readStateUnlocked(context)) {
                JournalStateRead.Missing -> emptyList()
                JournalStateRead.Invalid -> null
                is JournalStateRead.Valid ->
                    read.state.records.takeIf {
                        read.state.cutoff.bootId == currentBootId
                    } ?: emptyList()
            }
        }
    }

    fun clearForTesting(context: Context): Boolean {
        check(BuildConfig.DEBUG)
        val currentBootId = captureCurrentBootId() ?: return false
        var appliedCutoff = -1L
        val cleared = withJournalLock(context, failure = false) {
            val elapsedRealtimeNanos =
                SystemClock.elapsedRealtimeNanos()
            if (elapsedRealtimeNanos <= 0L) {
                return@withJournalLock false
            }
            val existing = when (
                val read = readStateUnlocked(context)
            ) {
                JournalStateRead.Missing,
                JournalStateRead.Invalid -> null
                is JournalStateRead.Valid -> read.state
            }
            val cutoff =
                R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                    .advanceForClear(
                        existing = existing?.cutoff,
                        currentBootId = currentBootId,
                        elapsedRealtimeNanos = elapsedRealtimeNanos,
                    )
            val records = existing
                ?.records
                .orEmpty()
                .filter { record ->
                    R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                        .accepts(
                            cutoff = cutoff,
                            eventBootId = record.owner.bootId,
                            eventElapsedRealtimeNanos =
                                record.elapsedRealtimeNanos,
                        )
                }
            if (
                !writeStateUnlocked(
                    context = context,
                    state = JournalState(
                        cutoff = cutoff,
                        records = records,
                    ),
                )
            ) {
                return@withJournalLock false
            }
            appliedCutoff = cutoff.elapsedRealtimeNanos
            true
        }
        if (!cleared) return false
        synchronized(PENDING_LOCK) {
            val retained = PENDING.filter { pending ->
                pending.elapsedRealtimeNanos > appliedCutoff
            }
            PENDING.clear()
            PENDING.addAll(retained)
        }
        return true
    }

    private fun startWriter(context: Context) {
        val started = runCatching {
            Thread(
                {
                    drainPending(context)
                },
                "typescript-r2-job-lifecycle-journal",
            ).apply {
                isDaemon = true
                start()
            }
        }.isSuccess
        if (!started) {
            synchronized(PENDING_LOCK) {
                writerActive = false
                PENDING.clear()
            }
        }
    }

    private fun drainPending(context: Context) {
        while (true) {
            val batch = synchronized(PENDING_LOCK) {
                if (PENDING.isEmpty()) {
                    writerActive = false
                    return
                }
                buildList(PENDING.size) {
                    while (PENDING.isNotEmpty()) {
                        add(PENDING.removeFirst())
                    }
                }
            }
            /*
             * Journal evidence is best effort. A malformed/transient batch must not strand
             * writerActive=true and prevent later framework callbacks from starting a drain.
             */
            runCatching {
                persist(context, batch)
            }
        }
    }

    private fun persist(
        context: Context,
        pending: List<PendingRecord>,
    ) {
        val ownerPid = Process.myPid()
        val owner = captureOwner(ownerPid) ?: return
        val records = pending
            .asSequence()
            .filter { it.ownerPid == ownerPid }
            .map { record ->
                R2CompilerProcessSupervisorJobLifecycleRecord(
                    processSequence = record.processSequence,
                    elapsedRealtimeNanos =
                        record.elapsedRealtimeNanos,
                    event = record.event,
                    stopDecision = record.stopDecision,
                    workTokenSha256 =
                        record.workToken?.sha256Ascii(),
                    currentWorkTokenSha256 =
                        record.currentWorkToken?.sha256Ascii(),
                    owner = owner,
                    ownerPid = ownerPid,
                )
            }
            .toList()
        if (records.isEmpty()) return
        withJournalLock(context, failure = Unit) {
            val existing = when (
                val read = readStateUnlocked(context)
            ) {
                JournalStateRead.Missing -> null
                JournalStateRead.Invalid ->
                    return@withJournalLock
                is JournalStateRead.Valid -> read.state
            }
            val cutoff =
                R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                    .forCurrentBoot(
                        existing = existing?.cutoff,
                        currentBootId = owner.bootId,
                    )
            val retained = existing
                ?.records
                .orEmpty()
                .filter { record ->
                    R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                        .accepts(
                            cutoff = cutoff,
                            eventBootId = record.owner.bootId,
                            eventElapsedRealtimeNanos =
                                record.elapsedRealtimeNanos,
                        )
                }
            val accepted = records.filter { record ->
                R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                    .accepts(
                        cutoff = cutoff,
                        eventBootId = record.owner.bootId,
                        eventElapsedRealtimeNanos =
                            record.elapsedRealtimeNanos,
                    )
            }
            val bounded = (retained + accepted)
                .takeLast(MAX_PERSISTED_RECORDS)
            writeStateUnlocked(
                context = context,
                state = JournalState(
                    cutoff = cutoff,
                    records = bounded,
                ),
            )
        }
    }

    /*
     * Do not preflight baseFile.exists(): API 24 AtomicFile may temporarily have only its .bak,
     * and openRead() is the operation that restores it. Invalid is distinct from Missing so a
     * writer never discards an unreadable cutoff and thereby accepts an old batch.
     */
    private fun readStateUnlocked(
        context: Context,
    ): JournalStateRead {
        val file = journalFile(context)
        val bytes = try {
            file.openRead().use { input ->
                val output = ByteArray(MAX_RECORD_BYTES + 1)
                var used = 0
                while (used < output.size) {
                    val read =
                        input.read(output, used, output.size - used)
                    if (read < 0) break
                    if (read == 0) continue
                    used += read
                }
                if (used > MAX_RECORD_BYTES) {
                    return JournalStateRead.Invalid
                }
                output.copyOf(used)
            }
        } catch (_: FileNotFoundException) {
            val base = file.baseFile
            return if (
                !base.exists() &&
                !File("${base.path}.bak").exists()
            ) {
                JournalStateRead.Missing
            } else {
                JournalStateRead.Invalid
            }
        } catch (_: Throwable) {
            return JournalStateRead.Invalid
        }
        return try {
            parseState(bytes)
                ?.let(JournalStateRead::Valid)
                ?: JournalStateRead.Invalid
        } finally {
            bytes.fill(0)
        }
    }

    private fun parseState(bytes: ByteArray): JournalState? {
        if (
            bytes.any { value ->
                val unsigned = value.toInt() and 0xff
                unsigned != '\n'.code &&
                    (unsigned < 0x20 || unsigned > 0x7e)
            }
        ) {
            return null
        }
        val lines =
            String(bytes, Charsets.US_ASCII).split('\n')
        if (
            lines.size < 3 ||
            lines.first() != FORMAT_HEADER ||
            lines.last().isNotEmpty()
        ) {
            return null
        }
        val payload = lines.subList(1, lines.lastIndex)
        val cutoff =
            parseCutoff(payload.first()) ?: return null
        val recordLines = payload.drop(1)
        if (recordLines.size > MAX_PERSISTED_RECORDS) return null
        val records = recordLines.map { line ->
            parseRecord(line) ?: return null
        }
        if (!hasStrictProcessOrder(records)) return null
        if (
            records.any { record ->
                !R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                    .accepts(
                        cutoff = cutoff,
                        eventBootId = record.owner.bootId,
                        eventElapsedRealtimeNanos =
                            record.elapsedRealtimeNanos,
                    )
            }
        ) {
            return null
        }
        return JournalState(
            cutoff = cutoff,
            records = records,
        )
    }

    private fun parseCutoff(
        line: String,
    ): R2CompilerProcessSupervisorJobLifecycleCutoff? {
        val fields = line.split(FIELD_SEPARATOR)
        if (
            fields.size != CUTOFF_FIELD_COUNT ||
            fields[0] != CUTOFF_RECORD_TYPE
        ) {
            return null
        }
        val bootId = fields[1]
        val elapsedRealtimeNanos =
            fields[2].toLongOrNull()?.takeIf { it >= 0L }
                ?: return null
        return runCatching {
            R2CompilerProcessSupervisorJobLifecycleCutoff(
                bootId = bootId,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
            )
        }.getOrNull()
    }

    private fun parseRecord(
        line: String,
    ): R2CompilerProcessSupervisorJobLifecycleRecord? {
        val fields = line.split(FIELD_SEPARATOR)
        if (
            fields.size != RECORD_FIELD_COUNT ||
            fields[0] != EVENT_RECORD_TYPE
        ) {
            return null
        }
        val processSequence =
            fields[1].toLongOrNull()?.takeIf { it > 0L }
                ?: return null
        val elapsedRealtimeNanos =
            fields[2].toLongOrNull()?.takeIf { it > 0L }
                ?: return null
        val event = runCatching {
            R2CompilerProcessSupervisorJobLifecycleEvent
                .valueOf(fields[3])
        }.getOrNull() ?: return null
        val stopDecision = fields[4].unlessAbsent()?.let {
            runCatching {
                R2CompilerProcessSupervisorJobStopDecision.valueOf(it)
            }.getOrNull() ?: return null
        }
        if (
            (event ==
                R2CompilerProcessSupervisorJobLifecycleEvent
                    .STOP_CALLBACK) !=
            (stopDecision != null)
        ) {
            return null
        }
        val workTokenSha256 =
            fields[5].canonicalDigestOrNull()
                ?.unlessAbsent()
        if (
            workTokenSha256 == null &&
            fields[5] != ABSENT_VALUE
        ) {
            return null
        }
        val currentWorkTokenSha256 =
            fields[6].canonicalDigestOrNull()
                ?.unlessAbsent()
        if (
            currentWorkTokenSha256 == null &&
            fields[6] != ABSENT_VALUE
        ) {
            return null
        }
        if (
            !R2CompilerProcessSupervisorJobLifecycleRecordPolicy
                .hasValidTokenRelation(
                    event = event,
                    stopDecision = stopDecision,
                    workToken = workTokenSha256,
                    currentWorkToken = currentWorkTokenSha256,
                )
        ) {
            return null
        }
        val ownerBootId = fields[7]
        val ownerUid =
            fields[8].toIntOrNull()?.takeIf { it >= 0 }
                ?: return null
        val ownerPid =
            fields[9].toIntOrNull()?.takeIf { it > 0 }
                ?: return null
        val ownerStartTimeTicks =
            fields[10].toLongOrNull()?.takeIf { it > 0L }
                ?: return null
        val ownerProcessName = fields[11]
        if (
            !R2CompilerProcessEpochIdentity
                .isCanonicalBootId(ownerBootId) ||
            ownerUid != Process.myUid() ||
            ownerProcessName !=
            "${BuildConfig.APPLICATION_ID}:typescript_supervisor"
        ) {
            return null
        }
        return R2CompilerProcessSupervisorJobLifecycleRecord(
            processSequence = processSequence,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            event = event,
            stopDecision = stopDecision,
            workTokenSha256 = workTokenSha256,
            currentWorkTokenSha256 = currentWorkTokenSha256,
            owner = R2ObservedCompilerProcess(
                bootId = ownerBootId,
                uid = ownerUid,
                startTimeTicks = ownerStartTimeTicks,
                processName = ownerProcessName,
            ),
            ownerPid = ownerPid,
        )
    }

    private fun hasStrictProcessOrder(
        records: List<R2CompilerProcessSupervisorJobLifecycleRecord>,
    ): Boolean {
        data class Position(
            val sequence: Long,
            val elapsedRealtimeNanos: Long,
        )
        val positions = mutableMapOf<String, Position>()
        records.forEach { record ->
            val key = buildString {
                append(record.owner.bootId)
                append(FIELD_SEPARATOR)
                append(record.ownerPid)
                append(FIELD_SEPARATOR)
                append(record.owner.startTimeTicks)
            }
            val previous = positions[key]
            if (
                previous != null &&
                (
                    record.processSequence <= previous.sequence ||
                        record.elapsedRealtimeNanos <
                        previous.elapsedRealtimeNanos
                    )
            ) {
                return false
            }
            positions[key] = Position(
                sequence = record.processSequence,
                elapsedRealtimeNanos =
                    record.elapsedRealtimeNanos,
            )
        }
        return true
    }

    private fun writeStateUnlocked(
        context: Context,
        state: JournalState,
    ): Boolean {
        if (
            state.records.size > MAX_PERSISTED_RECORDS ||
            !hasStrictProcessOrder(state.records) ||
            state.records.any { record ->
                !R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                    .accepts(
                        cutoff = state.cutoff,
                        eventBootId = record.owner.bootId,
                        eventElapsedRealtimeNanos =
                            record.elapsedRealtimeNanos,
                    ) ||
                    !R2CompilerProcessSupervisorJobLifecycleRecordPolicy
                        .hasValidTokenRelation(
                            event = record.event,
                            stopDecision = record.stopDecision,
                            workToken = record.workTokenSha256,
                            currentWorkToken =
                                record.currentWorkTokenSha256,
                        )
            }
        ) {
            return false
        }
        val bytes = buildString {
            append(FORMAT_HEADER).append('\n')
            append(CUTOFF_RECORD_TYPE)
                .append(FIELD_SEPARATOR)
                .append(state.cutoff.bootId)
                .append(FIELD_SEPARATOR)
                .append(state.cutoff.elapsedRealtimeNanos)
                .append('\n')
            state.records.forEach { record ->
                append(EVENT_RECORD_TYPE)
                    .append(FIELD_SEPARATOR)
                    .append(record.processSequence)
                    .append(FIELD_SEPARATOR)
                    .append(record.elapsedRealtimeNanos)
                    .append(FIELD_SEPARATOR)
                    .append(record.event.name)
                    .append(FIELD_SEPARATOR)
                    .append(
                        record.stopDecision?.name
                            ?: ABSENT_VALUE,
                    )
                    .append(FIELD_SEPARATOR)
                    .append(
                        record.workTokenSha256
                            ?: ABSENT_VALUE,
                    )
                    .append(FIELD_SEPARATOR)
                    .append(
                        record.currentWorkTokenSha256
                            ?: ABSENT_VALUE,
                    )
                    .append(FIELD_SEPARATOR)
                    .append(record.owner.bootId)
                    .append(FIELD_SEPARATOR)
                    .append(record.owner.uid)
                    .append(FIELD_SEPARATOR)
                    .append(record.ownerPid)
                    .append(FIELD_SEPARATOR)
                    .append(record.owner.startTimeTicks)
                    .append(FIELD_SEPARATOR)
                    .append(record.owner.processName)
                    .append('\n')
            }
        }.toByteArray(Charsets.US_ASCII)
        if (bytes.size > MAX_RECORD_BYTES) {
            bytes.fill(0)
            return false
        }
        val file = journalFile(context)
        try {
            for (attempt in 0 until RECORD_ATTEMPTS) {
                var output: FileOutputStream? = null
                try {
                    output = file.startWrite()
                    output.write(bytes)
                    file.finishWrite(output)
                    output = null
                    return true
                } catch (_: Throwable) {
                    output?.let(file::failWrite)
                    if (attempt + 1 < RECORD_ATTEMPTS) {
                        SystemClock.sleep(RECORD_RETRY_MILLIS)
                    }
                }
            }
        } finally {
            bytes.fill(0)
        }
        return false
    }

    private fun captureOwner(
        ownerPid: Int,
    ): R2ObservedCompilerProcess? {
        repeat(RECORD_ATTEMPTS) { attempt ->
            R2CompilerProcessProcfs().capture(ownerPid)?.let {
                return it
            }
            if (attempt + 1 < RECORD_ATTEMPTS) {
                SystemClock.sleep(RECORD_RETRY_MILLIS)
            }
        }
        return null
    }

    private fun captureCurrentBootId(): String? =
        captureOwner(Process.myPid())?.bootId

    private fun String?.canonicalWorkTokenOrNull(): String? =
        this?.takeIf {
            R2CompilerProcessSupervisorJobWork.isCanonicalToken(it)
        }

    private fun String.sha256Ascii(): String {
        val bytes = toByteArray(Charsets.US_ASCII)
        return try {
            R2Digests.sha256(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun String.canonicalDigestOrNull(): String? {
        if (this == ABSENT_VALUE) return ABSENT_VALUE
        return runCatching {
            R2Digests.requireLowerSha256(
                this,
                "Job lifecycle token digest",
            )
        }.getOrNull()
    }

    private fun String.unlessAbsent(): String? =
        takeUnless { it == ABSENT_VALUE }

    private fun journalFile(context: Context): AtomicFile =
        AtomicFile(
            File(
                context.applicationContext.noBackupFilesDir,
                RECORD_FILE_NAME,
            ),
        )

    private fun <T> withJournalLock(
        context: Context,
        failure: T,
        block: () -> T,
    ): T =
        synchronized(RECORD_PROCESS_LOCK) {
            try {
                RandomAccessFile(recordLockFile(context), "rw").use {
                        file ->
                    val lock = file.channel.lock()
                    try {
                        block()
                    } finally {
                        runCatching { lock.release() }
                    }
                }
            } catch (_: Throwable) {
                failure
            }
        }

    private fun recordLockFile(context: Context): File =
        File(
            context.applicationContext.noBackupFilesDir,
            RECORD_LOCK_FILE_NAME,
        )

    private data class PendingRecord(
        val processSequence: Long,
        val elapsedRealtimeNanos: Long,
        val event: R2CompilerProcessSupervisorJobLifecycleEvent,
        val stopDecision: R2CompilerProcessSupervisorJobStopDecision?,
        val workToken: String?,
        val currentWorkToken: String?,
        val ownerPid: Int,
    )

    private data class JournalState(
        val cutoff: R2CompilerProcessSupervisorJobLifecycleCutoff,
        val records:
            List<R2CompilerProcessSupervisorJobLifecycleRecord>,
    )

    private sealed interface JournalStateRead {

        data object Missing : JournalStateRead

        data object Invalid : JournalStateRead

        data class Valid(
            val state: JournalState,
        ) : JournalStateRead
    }

    private const val FORMAT_HEADER =
        "autojs6-r2-supervisor-job-lifecycle-journal-v2"
    private const val RECORD_FILE_NAME =
        "r2-compiler-process-supervisor-job-lifecycle-journal-v2"
    private const val RECORD_LOCK_FILE_NAME =
        "r2-compiler-process-supervisor-job-lifecycle-journal.lock"
    private const val ABSENT_VALUE = "-"
    private const val FIELD_SEPARATOR = '|'
    private const val CUTOFF_RECORD_TYPE = "B"
    private const val EVENT_RECORD_TYPE = "E"
    private const val CUTOFF_FIELD_COUNT = 3
    private const val RECORD_FIELD_COUNT = 12
    private const val MAX_PENDING_RECORDS = 32
    private const val MAX_PERSISTED_RECORDS = 64
    private const val MAX_RECORD_BYTES = 32 * 1024
    private const val RECORD_ATTEMPTS = 3
    private const val RECORD_RETRY_MILLIS = 10L
    private val PROCESS_SEQUENCE = AtomicLong(0L)
    private val PENDING_LOCK = Any()
    private val PENDING = ArrayDeque<PendingRecord>(
        MAX_PENDING_RECORDS,
    )
    private var writerActive = false
    private val RECORD_PROCESS_LOCK = Any()
}

internal enum class R2CompilerProcessSupervisorOwnerOutcome {
    RUNNING,
    PREARM_RETRY,
    RECONCILED,
    RETRY,
    STALE_JOB,
    NO_WORK,
}

internal data class R2CompilerProcessSupervisorOwnerRecord(
    val outcome: R2CompilerProcessSupervisorOwnerOutcome,
    val owner: R2ObservedCompilerProcess,
    val ownerPid: Int,
    val oldBootId: String?,
    val oldPid: Int?,
    val oldStartTimeTicks: Long?,
    val oldNonceSha256: String?,
    val workTokenSha256: String?,
    val observation: R2CompilerProcessObservation?,
)

/**
 * Best-effort app-private observability only. This record never authorizes a clear or admission.
 */
internal object R2CompilerProcessSupervisorOwnerTelemetry {

    @Synchronized
    fun record(
        context: Context,
        outcome: R2CompilerProcessSupervisorOwnerOutcome,
        oldIdentity: R2CompilerProcessEpochIdentity?,
        observation: R2CompilerProcessObservation?,
        workToken: String?,
    ) {
        val owner = captureOwner() ?: return
        val oldNonceSha256 = oldIdentity?.nonce?.sha256Ascii()
        val workTokenSha256 = workToken?.sha256Ascii()
        val bytes = buildString {
            append(FORMAT_HEADER).append('\n')
            append(outcome.name).append('\n')
            append(owner.bootId).append('\n')
            append(owner.uid).append('\n')
            append(Process.myPid()).append('\n')
            append(owner.startTimeTicks).append('\n')
            append(owner.processName).append('\n')
            append(oldIdentity?.bootId ?: ABSENT_VALUE).append('\n')
            append(oldIdentity?.pid ?: ABSENT_VALUE).append('\n')
            append(oldIdentity?.startTimeTicks ?: ABSENT_VALUE).append('\n')
            append(oldNonceSha256 ?: ABSENT_VALUE).append('\n')
            append(workTokenSha256 ?: ABSENT_VALUE).append('\n')
            append(observation?.name ?: ABSENT_VALUE).append('\n')
        }.toByteArray(Charsets.UTF_8)
        val file = file(context)
        try {
            withRecordLock(context, failure = Unit) {
                for (attempt in 0 until RECORD_ATTEMPTS) {
                    var output: FileOutputStream? = null
                    try {
                        output = file.startWrite()
                        output.write(bytes)
                        file.finishWrite(output)
                        output = null
                        break
                    } catch (_: Throwable) {
                        output?.let(file::failWrite)
                        if (attempt + 1 < RECORD_ATTEMPTS) {
                            SystemClock.sleep(RECORD_RETRY_MILLIS)
                        }
                    }
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized
    fun read(context: Context): R2CompilerProcessSupervisorOwnerRecord? =
        withRecordLock(context, failure = null) {
            readUnlocked(context)
        }

    private fun readUnlocked(
        context: Context,
    ): R2CompilerProcessSupervisorOwnerRecord? {
        val file = file(context)
        if (!file.baseFile.exists()) return null
        val bytes = try {
            file.openRead().use { input ->
                val output = ByteArray(MAX_RECORD_BYTES + 1)
                var used = 0
                while (used < output.size) {
                    val read = input.read(output, used, output.size - used)
                    if (read < 0) break
                    if (read == 0) continue
                    used += read
                }
                if (used > MAX_RECORD_BYTES) return null
                output.copyOf(used)
            }
        } catch (_: Throwable) {
            return null
        }
        return try {
            val lines = String(bytes, Charsets.UTF_8).split('\n')
            if (
                lines.size != EXPECTED_LINE_COUNT ||
                lines[0] != FORMAT_HEADER ||
                lines.last().isNotEmpty()
            ) {
                return null
            }
            val ownerBootId = lines[2]
            val ownerUid = lines[3].toInt()
            val ownerPid = lines[4].toInt()
            val ownerStartTimeTicks = lines[5].toLong()
            val ownerProcessName = lines[6]
            val oldBootId = lines[7].unlessAbsent()
            val oldPid = lines[8].unlessAbsent()?.toIntOrNull()
            val oldStartTimeTicks = lines[9].unlessAbsent()?.toLongOrNull()
            val oldNonceSha256 = lines[10].unlessAbsent()
            val workTokenSha256 = lines[11].unlessAbsent()
            if (
                !R2CompilerProcessEpochIdentity.isCanonicalBootId(ownerBootId) ||
                ownerUid < 0 ||
                ownerPid <= 0 ||
                ownerStartTimeTicks <= 0L ||
                ownerProcessName !=
                "${BuildConfig.APPLICATION_ID}:typescript_supervisor" ||
                (oldBootId == null) != (oldPid == null) ||
                (oldBootId == null) != (oldStartTimeTicks == null) ||
                (oldBootId == null) != (oldNonceSha256 == null)
            ) {
                return null
            }
            if (
                oldPid != null &&
                (
                    oldPid <= 0 ||
                        requireNotNull(oldStartTimeTicks) <= 0L
                    )
            ) {
                return null
            }
            oldBootId?.let {
                if (!R2CompilerProcessEpochIdentity.isCanonicalBootId(it)) return null
            }
            oldNonceSha256?.let {
                runCatching {
                    R2Digests.requireLowerSha256(it, "Supervisor telemetry nonce digest")
                }.getOrElse { return null }
            }
            workTokenSha256?.let {
                runCatching {
                    R2Digests.requireLowerSha256(
                        it,
                        "Supervisor telemetry work-token digest",
                    )
                }.getOrElse { return null }
            }
            R2CompilerProcessSupervisorOwnerRecord(
                outcome = R2CompilerProcessSupervisorOwnerOutcome.valueOf(lines[1]),
                owner = R2ObservedCompilerProcess(
                    bootId = ownerBootId,
                    uid = ownerUid,
                    startTimeTicks = ownerStartTimeTicks,
                    processName = ownerProcessName,
                ),
                ownerPid = ownerPid,
                oldBootId = oldBootId,
                oldPid = oldPid,
                oldStartTimeTicks = oldStartTimeTicks,
                oldNonceSha256 = oldNonceSha256,
                workTokenSha256 = workTokenSha256,
                observation = lines[12].unlessAbsent()
                    ?.let(R2CompilerProcessObservation::valueOf),
            )
        } catch (_: Throwable) {
            null
        } finally {
            bytes.fill(0)
        }
    }

    fun clearForTesting(context: Context) {
        check(BuildConfig.DEBUG)
        withRecordLock(context, failure = Unit) {
            file(context).delete()
        }
    }

    private fun String.unlessAbsent(): String? =
        takeUnless { it == ABSENT_VALUE }

    private fun String.sha256Ascii(): String {
        val bytes = toByteArray(Charsets.US_ASCII)
        return try {
            R2Digests.sha256(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun captureOwner(): R2ObservedCompilerProcess? {
        repeat(RECORD_ATTEMPTS) { attempt ->
            R2CompilerProcessProcfs().capture(Process.myPid())?.let {
                return it
            }
            if (attempt + 1 < RECORD_ATTEMPTS) {
                SystemClock.sleep(RECORD_RETRY_MILLIS)
            }
        }
        return null
    }

    private fun file(context: Context): AtomicFile =
        AtomicFile(
            File(
                context.applicationContext.noBackupFilesDir,
                RECORD_FILE_NAME,
            ),
        )

    private fun <T> withRecordLock(
        context: Context,
        failure: T,
        block: () -> T,
    ): T =
        synchronized(RECORD_PROCESS_LOCK) {
            try {
                RandomAccessFile(recordLockFile(context), "rw").use { file ->
                    val lock = file.channel.lock()
                    try {
                        block()
                    } finally {
                        runCatching { lock.release() }
                    }
                }
            } catch (_: Throwable) {
                failure
            }
        }

    private fun recordLockFile(context: Context): File =
        File(
            context.applicationContext.noBackupFilesDir,
            RECORD_LOCK_FILE_NAME,
        )

    private const val FORMAT_HEADER = "autojs6-r2-supervisor-owner-telemetry-v2"
    private const val RECORD_FILE_NAME = "r2-compiler-process-supervisor-owner-telemetry-v1"
    private const val RECORD_LOCK_FILE_NAME =
        "r2-compiler-process-supervisor-owner-telemetry.lock"
    private const val ABSENT_VALUE = "-"
    private const val MAX_RECORD_BYTES = 2 * 1024
    private const val EXPECTED_LINE_COUNT = 14
    private const val RECORD_ATTEMPTS = 3
    private const val RECORD_RETRY_MILLIS = 10L
    private val RECORD_PROCESS_LOCK = Any()
}
