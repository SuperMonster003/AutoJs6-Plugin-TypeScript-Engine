package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Private, provisional control plane for proving death of a poisoned compiler process.
 *
 * The public compiler Binder is reused only as an exact kernel-owned endpoint. A supervisor in a
 * different process asks that endpoint to terminate its own poisoned process epoch; it never sends
 * a signal to a caller-supplied numeric PID. The old Binder death is the primary proof and procfs
 * supplies an independent ABSENT/REUSED cross-check.
 */
internal object R2CompilerProcessSupervisorProtocol {

    const val KEY_TELEMETRY_ENABLED =
        "__autojs6_r2_debug_process_supervisor_telemetry_enabled"
    const val KEY_IDENTITY_AVAILABLE =
        "__autojs6_r2_debug_process_supervisor_identity_available"
    const val KEY_PROCESS_UID =
        "__autojs6_r2_debug_process_supervisor_uid"
    const val KEY_PROCESS_BOOT_ID =
        "__autojs6_r2_debug_process_supervisor_boot_id"
    const val KEY_PROCESS_PID =
        "__autojs6_r2_debug_process_supervisor_pid"
    const val KEY_PROCESS_START_TIME_TICKS =
        "__autojs6_r2_debug_process_supervisor_start_time_ticks"
    const val KEY_PROCESS_NAME =
        "__autojs6_r2_debug_process_supervisor_process_name"
    const val KEY_PROCESS_NONCE =
        "__autojs6_r2_debug_process_supervisor_process_nonce"
    const val KEY_REQUIRES_EXTERNAL_SUPERVISION =
        "__autojs6_r2_debug_process_supervisor_required"

    const val KEY_COMMAND =
        "__autojs6_r2_debug_process_supervisor_command"
    const val KEY_COMMAND_DEADLINE_ELAPSED_REALTIME_MS =
        "__autojs6_r2_debug_process_supervisor_command_deadline_elapsed_realtime_ms"
    const val KEY_TERMINATION_ACCEPTED =
        "__autojs6_r2_debug_process_supervisor_termination_accepted"
    const val KEY_REQUIRE_PREARM_RETRY_FOR_TESTING =
        "__autojs6_r2_debug_process_supervisor_require_prearm_retry"
    const val KEY_TERMINATION_DELAY_MILLIS_FOR_TESTING =
        "__autojs6_r2_debug_process_supervisor_termination_delay_ms"
    const val COMMAND_TERMINATE_POISONED_EPOCH =
        "terminate_poisoned_epoch"

    const val PROCESS_NONCE_BYTES = 32
    const val PROCESS_NONCE_CHARS = PROCESS_NONCE_BYTES * 2
    const val TERMINATION_DELAY_MILLIS = 100L
    const val MIN_COMMAND_EXECUTION_BUDGET_MILLIS = 200L
    const val DEATH_PROOF_RESERVE_MILLIS = 500L
    const val DEFAULT_TERMINATION_TIMEOUT_MILLIS = 10_000L
    const val MIN_TERMINATION_TIMEOUT_MILLIS = 1_000L
    const val MAX_TERMINATION_TIMEOUT_MILLIS = 30_000L
}

internal data class R2CompilerProcessEpochIdentity(
    val bootId: String,
    val uid: Int,
    val pid: Int,
    val startTimeTicks: Long,
    val processName: String,
    val nonce: String,
) {

    init {
        require(isCanonicalBootId(bootId)) {
            "Compiler process boot ID must be a canonical lowercase UUID."
        }
        require(uid >= 0) { "Compiler process UID must not be negative." }
        require(pid > 0) { "Compiler process PID must be positive." }
        require(startTimeTicks > 0L) {
            "Compiler process start time must be positive."
        }
        require(processName.isNotBlank()) {
            "Compiler process name must not be blank."
        }
        require(
            nonce.length == R2CompilerProcessSupervisorProtocol.PROCESS_NONCE_CHARS &&
                nonce.all { character ->
                    character in '0'..'9' || character in 'a'..'f'
                },
        ) {
            "Compiler process nonce must be canonical lowercase hexadecimal."
        }
    }

    fun putInto(bundle: Bundle) {
        bundle.putBoolean(
            R2CompilerProcessSupervisorProtocol.KEY_TELEMETRY_ENABLED,
            true,
        )
        bundle.putBoolean(
            R2CompilerProcessSupervisorProtocol.KEY_IDENTITY_AVAILABLE,
            true,
        )
        bundle.putString(
            R2CompilerProcessSupervisorProtocol.KEY_PROCESS_BOOT_ID,
            bootId,
        )
        bundle.putInt(
            R2CompilerProcessSupervisorProtocol.KEY_PROCESS_UID,
            uid,
        )
        bundle.putInt(
            R2CompilerProcessSupervisorProtocol.KEY_PROCESS_PID,
            pid,
        )
        bundle.putLong(
            R2CompilerProcessSupervisorProtocol.KEY_PROCESS_START_TIME_TICKS,
            startTimeTicks,
        )
        bundle.putString(
            R2CompilerProcessSupervisorProtocol.KEY_PROCESS_NAME,
            processName,
        )
        bundle.putString(
            R2CompilerProcessSupervisorProtocol.KEY_PROCESS_NONCE,
            nonce,
        )
    }

    fun matchesExact(expected: R2CompilerProcessEpochIdentity): Boolean =
        secureEquals(bootId, expected.bootId) &&
            uid == expected.uid &&
            pid == expected.pid &&
            startTimeTicks == expected.startTimeTicks &&
            processName == expected.processName &&
            secureEquals(nonce, expected.nonce)

    fun matchesTerminationRequest(bundle: Bundle): Boolean {
        val requestedBootId = runCatching {
            bundle.getString(
                R2CompilerProcessSupervisorProtocol.KEY_PROCESS_BOOT_ID,
            )
        }.getOrNull()
        val requestedUid = runCatching {
            bundle.getInt(
                R2CompilerProcessSupervisorProtocol.KEY_PROCESS_UID,
                -1,
            )
        }.getOrDefault(-1)
        val requestedPid = runCatching {
            bundle.getInt(
                R2CompilerProcessSupervisorProtocol.KEY_PROCESS_PID,
                -1,
            )
        }.getOrDefault(-1)
        val requestedStartTime = runCatching {
            bundle.getLong(
                R2CompilerProcessSupervisorProtocol.KEY_PROCESS_START_TIME_TICKS,
                -1L,
            )
        }.getOrDefault(-1L)
        val requestedName = runCatching {
            bundle.getString(
                R2CompilerProcessSupervisorProtocol.KEY_PROCESS_NAME,
            )
        }.getOrNull()
        val requestedNonce = runCatching {
            bundle.getString(
                R2CompilerProcessSupervisorProtocol.KEY_PROCESS_NONCE,
            )
        }.getOrNull()
        return requestedBootId != null &&
            secureEquals(bootId, requestedBootId) &&
            uid == requestedUid &&
            pid == requestedPid &&
            startTimeTicks == requestedStartTime &&
            processName == requestedName &&
            requestedNonce != null &&
            secureEquals(nonce, requestedNonce)
    }

    companion object {

        internal fun isCanonicalBootId(value: String): Boolean =
            value.length == BOOT_ID_CHARS &&
                value.indices.all { index ->
                    if (index in BOOT_ID_HYPHEN_INDICES) {
                        value[index] == '-'
                    } else {
                        value[index] in '0'..'9' || value[index] in 'a'..'f'
                    }
                }

        fun from(bundle: Bundle): R2CompilerProcessEpochIdentity {
            require(
                bundle.getBoolean(
                    R2CompilerProcessSupervisorProtocol.KEY_TELEMETRY_ENABLED,
                    false,
                ) &&
                    bundle.getBoolean(
                        R2CompilerProcessSupervisorProtocol.KEY_IDENTITY_AVAILABLE,
                        false,
                    ),
            ) {
                "Compiler process supervisor identity telemetry is unavailable."
            }
            return R2CompilerProcessEpochIdentity(
                bootId = bundle.getString(
                    R2CompilerProcessSupervisorProtocol.KEY_PROCESS_BOOT_ID,
                ).orEmpty(),
                uid = bundle.getInt(
                    R2CompilerProcessSupervisorProtocol.KEY_PROCESS_UID,
                    -1,
                ),
                pid = bundle.getInt(
                    R2CompilerProcessSupervisorProtocol.KEY_PROCESS_PID,
                    -1,
                ),
                startTimeTicks = bundle.getLong(
                    R2CompilerProcessSupervisorProtocol.KEY_PROCESS_START_TIME_TICKS,
                    -1L,
                ),
                processName = bundle.getString(
                    R2CompilerProcessSupervisorProtocol.KEY_PROCESS_NAME,
                ).orEmpty(),
                nonce = bundle.getString(
                    R2CompilerProcessSupervisorProtocol.KEY_PROCESS_NONCE,
                ).orEmpty(),
            )
        }

        private const val BOOT_ID_CHARS = 36
        private val BOOT_ID_HYPHEN_INDICES = setOf(8, 13, 18, 23)
    }
}

internal enum class R2CompilerProcessObservation {
    SAME,
    REUSED,
    ABSENT,
    UNREADABLE,
}

internal data class R2CompilerProcessTerminationProof(
    val oldIdentity: R2CompilerProcessEpochIdentity,
    val terminationCommandAccepted: Boolean,
    val binderDeathObserved: Boolean,
    val finalObservation: R2CompilerProcessObservation,
    val successorFenceCleared: Boolean,
) {

    val deathProven: Boolean
        get() =
            binderDeathObserved &&
                (
                    finalObservation == R2CompilerProcessObservation.ABSENT ||
                        finalObservation == R2CompilerProcessObservation.REUSED
                    )
}

internal data class R2PersistedQuarantineRecoveryProof(
    val oldIdentity: R2CompilerProcessEpochIdentity,
    val finalObservation: R2CompilerProcessObservation,
    val successorFenceCleared: Boolean,
) {

    val oldEpochGone: Boolean
        get() =
            finalObservation == R2CompilerProcessObservation.ABSENT ||
                finalObservation == R2CompilerProcessObservation.REUSED
}

internal class R2CompilerProcessSupervisionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal class R2CompilerProcessSupervisor(
    context: Context,
    private val expectedUid: Int = Process.myUid(),
    private val expectedProcessName: String =
        "${BuildConfig.APPLICATION_ID}:typescript_compiler",
    private val procfs: R2CompilerProcessProcfs = R2CompilerProcessProcfs(),
    private val beforeSuccessorFenceClear: () -> Unit = {},
) {

    private val applicationContext = context.applicationContext

    fun terminatePoisonedEpoch(
        compiler: ITypeScriptCompilerPlugin,
        rawBinder: IBinder,
        oldIdentity: R2CompilerProcessEpochIdentity,
        timeoutMillis: Long =
            R2CompilerProcessSupervisorProtocol.DEFAULT_TERMINATION_TIMEOUT_MILLIS,
    ): R2CompilerProcessTerminationProof {
        validateTerminationScope(
            oldIdentity = oldIdentity,
            timeoutMillis = timeoutMillis,
        )
        require(compiler.asBinder() === rawBinder) {
            "Compiler interface and process Binder do not identify the same endpoint."
        }
        return terminatePoisonedEpoch(
            rawBinder = rawBinder,
            oldIdentity = oldIdentity,
            endpointDescriptor = ITypeScriptCompilerPlugin.DESCRIPTOR,
            timeoutMillis = timeoutMillis,
        ) { commandDeadline ->
            compiler.prewarm(
                terminationRequest(
                    oldIdentity = oldIdentity,
                    timeoutMillis = timeoutMillis,
                    commandDeadline = commandDeadline,
                ),
            ).getBoolean(
                R2CompilerProcessSupervisorProtocol.KEY_TERMINATION_ACCEPTED,
                false,
            )
        }
    }

    internal fun terminatePoisonedEpoch(
        rawBinder: IBinder,
        oldIdentity: R2CompilerProcessEpochIdentity,
        endpointDescriptor: String,
        timeoutMillis: Long =
            R2CompilerProcessSupervisorProtocol.DEFAULT_TERMINATION_TIMEOUT_MILLIS,
        terminationCommand: (commandDeadline: Long) -> Boolean,
    ): R2CompilerProcessTerminationProof {
        validateTerminationScope(
            oldIdentity = oldIdentity,
            timeoutMillis = timeoutMillis,
        )
        require(endpointDescriptor.isNotBlank()) {
            "Compiler process control endpoint descriptor must not be blank."
        }
        require(
            rawBinder.queryLocalInterface(endpointDescriptor) == null &&
                runCatching {
                    rawBinder.interfaceDescriptor
                }.getOrNull() == endpointDescriptor,
        ) {
            "The compiler process control endpoint must be a remote Binder " +
                "with the expected descriptor."
        }

        return superviseExactDeath(
            rawBinder = rawBinder,
            oldIdentity = oldIdentity,
            timeoutMillis = timeoutMillis,
            terminationCommand = terminationCommand,
        )
    }

    private fun validateTerminationScope(
        oldIdentity: R2CompilerProcessEpochIdentity,
        timeoutMillis: Long,
    ) {
        require(
            timeoutMillis in
                R2CompilerProcessSupervisorProtocol.MIN_TERMINATION_TIMEOUT_MILLIS..
                R2CompilerProcessSupervisorProtocol.MAX_TERMINATION_TIMEOUT_MILLIS,
        ) {
            "Compiler process termination timeout is outside the provisional bounds."
        }
        require(oldIdentity.uid == expectedUid) {
            "Refusing to supervise a compiler process owned by another UID."
        }
        require(oldIdentity.processName == expectedProcessName) {
            "Refusing to supervise an unexpected process name."
        }
        require(oldIdentity.pid != Process.myPid()) {
            "The compiler process supervisor must run outside the compiler process."
        }
    }

    private fun superviseExactDeath(
        rawBinder: IBinder,
        oldIdentity: R2CompilerProcessEpochIdentity,
        timeoutMillis: Long,
        terminationCommand: (commandDeadline: Long) -> Boolean,
    ): R2CompilerProcessTerminationProof {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        val commandDeadline =
            deadline -
                R2CompilerProcessSupervisorProtocol.DEATH_PROOF_RESERVE_MILLIS
        val binderDeath = CountDownLatch(1)
        val deathRecipient = IBinder.DeathRecipient { binderDeath.countDown() }
        var deathRecipientLinked = false
        try {
            rawBinder.linkToDeath(deathRecipient, 0)
            deathRecipientLinked = true
        } catch (error: Throwable) {
            if (rawBinder.isBinderAlive) {
                throw R2CompilerProcessSupervisionException(
                    "Could not pin the old compiler Binder before termination.",
                    error,
                )
            }
            binderDeath.countDown()
        }
        if (!rawBinder.isBinderAlive) {
            binderDeath.countDown()
        }

        var commandAccepted = false
        var commandInspected = false
        var explicitCommandRejection = false
        var commandFailure: Throwable? = null
        val commandCompleted = CountDownLatch(1)
        val commandResult = AtomicReference<Result<Boolean>?>(null)
        try {
            var latestObservation = procfs.observe(oldIdentity)
            val binderInitiallyDead = binderDeath.count == 0L
            if (
                !binderInitiallyDead &&
                latestObservation != R2CompilerProcessObservation.SAME
            ) {
                throw R2CompilerProcessSupervisionException(
                    "Live compiler Binder did not match its process epoch: " +
                        "$latestObservation.",
                )
            }

            if (!binderInitiallyDead) {
                if (!COMMAND_TRANSACTION_IN_FLIGHT.compareAndSet(false, true)) {
                    throw R2CompilerProcessSupervisionException(
                        "Another compiler termination command is still in flight.",
                    )
                }
                val commandThreadStarted = runCatching {
                    Thread(
                        {
                            try {
                                commandResult.set(
                                    runCatching {
                                        terminationCommand(commandDeadline)
                                    },
                                )
                            } finally {
                                COMMAND_TRANSACTION_IN_FLIGHT.set(false)
                                commandCompleted.countDown()
                            }
                        },
                        "typescript-r2-supervisor-command",
                    ).apply {
                        isDaemon = true
                        start()
                    }
                }.isSuccess
                if (!commandThreadStarted) {
                    COMMAND_TRANSACTION_IN_FLIGHT.set(false)
                    throw R2CompilerProcessSupervisionException(
                        "Could not start the bounded compiler termination command.",
                    )
                }
            }

            while (SystemClock.elapsedRealtime() < deadline) {
                if (!commandInspected && commandCompleted.count == 0L) {
                    commandInspected = true
                    val completedResult = commandResult.get()
                    val accepted = completedResult?.getOrNull()
                    commandFailure = completedResult?.exceptionOrNull()
                    if (accepted != null) {
                        commandAccepted = accepted
                        explicitCommandRejection = !commandAccepted
                    }
                }
                val binderDead =
                    binderDeath.count == 0L || !rawBinder.isBinderAlive
                if (binderDead) {
                    binderDeath.countDown()
                }
                latestObservation = procfs.observe(oldIdentity)
                if (explicitCommandRejection && !binderDead) {
                    throw R2CompilerProcessSupervisionException(
                        "Compiler did not accept its exact poisoned-epoch " +
                            "termination command.",
                    )
                }
                if (
                    binderDead &&
                    (
                        latestObservation == R2CompilerProcessObservation.ABSENT ||
                        latestObservation == R2CompilerProcessObservation.REUSED
                        )
                ) {
                    val successorFenceCleared =
                        R2CompilerProcessQuarantine.clearExactOrAbsent(
                            context = applicationContext,
                            expected = oldIdentity,
                            beforeMutation = beforeSuccessorFenceClear,
                        )
                    if (!successorFenceCleared) {
                        throw R2CompilerProcessSupervisionException(
                            "Old compiler died, but its successor admission fence " +
                                "could not be cleared exactly.",
                        )
                    }
                    return R2CompilerProcessTerminationProof(
                        oldIdentity = oldIdentity,
                        terminationCommandAccepted = commandAccepted,
                        binderDeathObserved = true,
                        finalObservation = latestObservation,
                        successorFenceCleared = true,
                    ).also { proof ->
                        check(proof.deathProven)
                    }
                }
                SystemClock.sleep(POLL_INTERVAL_MILLIS)
            }
            throw R2CompilerProcessSupervisionException(
                "Old compiler epoch did not reach Binder-death plus procfs-death proof; " +
                    "binderAlive=${rawBinder.isBinderAlive}, observation=$latestObservation, " +
                    "commandCompleted=${commandCompleted.count == 0L}.",
                commandFailure,
            )
        } finally {
            if (deathRecipientLinked) {
                runCatching {
                    rawBinder.unlinkToDeath(deathRecipient, 0)
                }
            }
        }
    }

    /**
     * Resumes a supervisor that died after the exact quarantine marker was made durable.
     *
     * A persisted exact PID/start-time tuple reaching ABSENT or REUSED is conclusive that the old
     * process epoch, and therefore every Binder it owned, is gone. SAME and UNREADABLE remain
     * fail-closed and never clear the admission fence.
     */
    fun reconcilePersistedQuarantine(
        expectedIdentity: R2CompilerProcessEpochIdentity? = null,
    ): R2PersistedQuarantineRecoveryProof {
        val oldIdentity =
            R2CompilerProcessQuarantine.readExactIdentity(applicationContext)
                ?: throw R2CompilerProcessSupervisionException(
                    if (R2CompilerProcessQuarantine.hasMarker(applicationContext)) {
                        "Persisted compiler quarantine marker is unreadable."
                    } else {
                        "No persisted compiler quarantine marker exists."
                    },
                )
        if (expectedIdentity != null && oldIdentity != expectedIdentity) {
            throw R2CompilerProcessSupervisionException(
                "Persisted compiler quarantine changed before reconciliation.",
            )
        }
        if (
            oldIdentity.uid != expectedUid ||
            oldIdentity.processName != expectedProcessName
        ) {
            throw R2CompilerProcessSupervisionException(
                "Persisted compiler quarantine identity is outside supervisor scope.",
            )
        }
        val observation = procfs.observe(oldIdentity)
        if (
            observation != R2CompilerProcessObservation.ABSENT &&
            observation != R2CompilerProcessObservation.REUSED
        ) {
            throw R2CompilerProcessSupervisionException(
                "Persisted compiler quarantine cannot be reconciled safely: $observation.",
            )
        }
        val cleared = R2CompilerProcessQuarantine.clearExact(
            context = applicationContext,
            expected = oldIdentity,
            beforeMutation = beforeSuccessorFenceClear,
        )
        if (!cleared) {
            throw R2CompilerProcessSupervisionException(
                "Persisted compiler quarantine could not be cleared exactly.",
            )
        }
        return R2PersistedQuarantineRecoveryProof(
            oldIdentity = oldIdentity,
            finalObservation = observation,
            successorFenceCleared = true,
        ).also { proof ->
            check(proof.oldEpochGone)
        }
    }

    private fun terminationRequest(
        oldIdentity: R2CompilerProcessEpochIdentity,
        timeoutMillis: Long,
        commandDeadline: Long,
    ) = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putLong(
            TypeScriptCompilerContract.KEY_TIMEOUT_MS,
            timeoutMillis,
        )
        putString(
            R2CompilerProcessSupervisorProtocol.KEY_COMMAND,
            R2CompilerProcessSupervisorProtocol.COMMAND_TERMINATE_POISONED_EPOCH,
        )
        putLong(
            R2CompilerProcessSupervisorProtocol
                .KEY_COMMAND_DEADLINE_ELAPSED_REALTIME_MS,
            commandDeadline,
        )
        oldIdentity.putInto(this)
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 25L
        val COMMAND_TRANSACTION_IN_FLIGHT = AtomicBoolean(false)
    }
}

/**
 * Process-lifetime identity. The nonce survives Service recreation in one PID and changes only
 * after actual process death.
 */
internal object R2CompilerProcessEpoch {

    private val terminationScheduled = AtomicBoolean(false)
    private val identity: R2CompilerProcessEpochIdentity by lazy(
        LazyThreadSafetyMode.SYNCHRONIZED,
    ) {
        val pid = Process.myPid()
        val snapshot = R2CompilerProcessProcfs().capture(pid)
            ?: error("Could not capture the current compiler process identity.")
        R2CompilerProcessEpochIdentity(
            bootId = snapshot.bootId,
            uid = snapshot.uid,
            pid = pid,
            startTimeTicks = snapshot.startTimeTicks,
            processName = snapshot.processName,
            nonce = newNonce(),
        )
    }

    fun identity(): R2CompilerProcessEpochIdentity = identity

    @Synchronized
    fun scheduleExactSelfTermination(
        context: Context,
        request: Bundle,
    ): Boolean {
        if (!identity.matchesTerminationRequest(request)) return false
        val commandDeadline = runCatching {
            request.getLong(
                R2CompilerProcessSupervisorProtocol
                    .KEY_COMMAND_DEADLINE_ELAPSED_REALTIME_MS,
                -1L,
            )
        }.getOrDefault(-1L)
        val requirePrearmRetryForTesting =
            BuildConfig.DEBUG &&
                runCatching {
                    request.getBoolean(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_REQUIRE_PREARM_RETRY_FOR_TESTING,
                        false,
                    )
                }.getOrDefault(false)
        val terminationDelayMillis = if (BuildConfig.DEBUG) {
            runCatching {
                request.getLong(
                    R2CompilerProcessSupervisorProtocol
                        .KEY_TERMINATION_DELAY_MILLIS_FOR_TESTING,
                    R2CompilerProcessSupervisorProtocol
                        .TERMINATION_DELAY_MILLIS,
                )
            }.getOrDefault(
                R2CompilerProcessSupervisorProtocol
                    .TERMINATION_DELAY_MILLIS,
            )
        } else {
            R2CompilerProcessSupervisorProtocol.TERMINATION_DELAY_MILLIS
        }
        if (
            terminationDelayMillis !in
            R2CompilerProcessSupervisorProtocol.TERMINATION_DELAY_MILLIS..
            MAX_TEST_TERMINATION_DELAY_MILLIS
        ) {
            return false
        }
        return scheduleExactSelfTerminationInternal(
            context = context,
            expectedIdentity = identity,
            commandDeadline = commandDeadline,
            requirePrearmRetryForTesting = requirePrearmRetryForTesting,
            terminationDelayMillis = terminationDelayMillis,
        )
    }

    @Synchronized
    fun scheduleExactSelfTermination(
        context: Context,
        expectedIdentity: R2CompilerProcessEpochIdentity,
        commandDeadline: Long,
    ): Boolean =
        scheduleExactSelfTerminationInternal(
            context = context,
            expectedIdentity = expectedIdentity,
            commandDeadline = commandDeadline,
            requirePrearmRetryForTesting = false,
            terminationDelayMillis =
                R2CompilerProcessSupervisorProtocol.TERMINATION_DELAY_MILLIS,
        )

    private fun scheduleExactSelfTerminationInternal(
        context: Context,
        expectedIdentity: R2CompilerProcessEpochIdentity,
        commandDeadline: Long,
        requirePrearmRetryForTesting: Boolean,
        terminationDelayMillis: Long,
    ): Boolean {
        val currentIdentity = identity
        if (!currentIdentity.matchesExact(expectedIdentity)) return false
        if (!hasUsableCommandDeadline(commandDeadline)) return false
        if (!terminationScheduled.compareAndSet(false, true)) return false
        val prearm = runCatching {
            R2CompilerProcessSupervisorJobScheduler.prearmExact(
                context = context,
                identity = currentIdentity,
                commandDeadline = commandDeadline,
            )
        }.getOrNull()
        if (prearm == null) {
            terminationScheduled.set(false)
            return false
        }
        if (
            requirePrearmRetryForTesting &&
            !awaitExactPrearmRetryForTesting(
                context = context,
                lease = prearm,
                commandDeadline = commandDeadline,
            )
        ) {
            abandonPrearm(
                context = context,
                identity = currentIdentity,
                prearm = prearm,
            )
            return false
        }
        if (
            !R2CompilerProcessQuarantine.markExactBeforeDeadline(
                context = context,
                identity = currentIdentity,
                commandDeadline = commandDeadline,
            )
        ) {
            abandonPrearm(
                context = context,
                identity = currentIdentity,
                prearm = prearm,
            )
            return false
        }
        if (
            !awaitExactPrearmOwnership(
                context = context,
                prearm = prearm,
                commandDeadline = commandDeadline,
            ) ||
            !hasUsableCommandDeadline(commandDeadline)
        ) {
            abandonPrearm(
                context = context,
                identity = currentIdentity,
                prearm = prearm,
            )
            return false
        }
        val started = runCatching {
            Thread(
                {
                    SystemClock.sleep(
                        terminationDelayMillis,
                    )
                    if (SystemClock.elapsedRealtime() >= commandDeadline) {
                        abandonPrearm(
                            context = context,
                            identity = currentIdentity,
                            prearm = prearm,
                        )
                        return@Thread
                    }
                    Process.killProcess(Process.myPid())
                },
                "typescript-r2-process-termination",
            ).apply {
                isDaemon = true
                start()
            }
        }.isSuccess
        if (!started) {
            abandonPrearm(
                context = context,
                identity = currentIdentity,
                prearm = prearm,
            )
        }
        return started
    }

    private fun awaitExactPrearmOwnership(
        context: Context,
        prearm: R2CompilerProcessSupervisorJobLease,
        commandDeadline: Long,
    ): Boolean {
        val visibilityDeadline = minOf(
            commandDeadline,
            SystemClock.elapsedRealtime() +
                PREARM_POST_COMMIT_VISIBILITY_MILLIS,
        )
        do {
            if (
                R2CompilerProcessSupervisorJobScheduler.hasExactJob(
                    context = context,
                    lease = prearm,
                )
            ) {
                return true
            }
            SystemClock.sleep(PREARM_TEST_POLL_INTERVAL_MILLIS)
        } while (
            SystemClock.elapsedRealtime() < visibilityDeadline &&
            hasUsableCommandDeadline(commandDeadline)
        )
        return false
    }

    private fun awaitExactPrearmRetryForTesting(
        context: Context,
        lease: R2CompilerProcessSupervisorJobLease,
        commandDeadline: Long,
    ): Boolean {
        check(BuildConfig.DEBUG)
        val tokenBytes =
            lease.work.workToken.toByteArray(Charsets.US_ASCII)
        val tokenSha256 = try {
            R2Digests.sha256(tokenBytes)
        } finally {
            tokenBytes.fill(0)
        }
        while (hasUsableCommandDeadline(commandDeadline)) {
            val record =
                R2CompilerProcessSupervisorOwnerTelemetry.read(context)
            if (
                record?.outcome ==
                R2CompilerProcessSupervisorOwnerOutcome.PREARM_RETRY &&
                record.oldBootId == lease.work.identity.bootId &&
                record.oldPid == lease.work.identity.pid &&
                record.oldStartTimeTicks ==
                lease.work.identity.startTimeTicks &&
                record.workTokenSha256 == tokenSha256 &&
                (
                    record.observation ==
                        R2CompilerProcessObservation.SAME ||
                        record.observation ==
                        R2CompilerProcessObservation.UNREADABLE
                    )
            ) {
                return true
            }
            SystemClock.sleep(PREARM_TEST_POLL_INTERVAL_MILLIS)
        }
        return false
    }

    private fun abandonPrearm(
        context: Context,
        identity: R2CompilerProcessEpochIdentity,
        prearm: R2CompilerProcessSupervisorJobLease,
    ) {
        val fenceRemoved = R2CompilerProcessQuarantine.clearExactOrAbsent(
            context = context,
            expected = identity,
        )
        if (fenceRemoved) {
            /*
             * JobScheduler has no conditional cancel primitive. This token-matched cleanup is
             * only best effort; correctness comes from the pre-arm deadline closing under the
             * marker lock, after which this epoch can no longer commit a new marker.
             */
            R2CompilerProcessSupervisorJobScheduler.retirePrearmBestEffort(
                context = context,
                lease = prearm,
            )
        }
        terminationScheduled.set(false)
    }

    internal fun hasUsableCommandDeadline(
        commandDeadline: Long,
        now: Long = SystemClock.elapsedRealtime(),
    ): Boolean =
        commandDeadline > now &&
            commandDeadline - now >=
            R2CompilerProcessSupervisorProtocol.MIN_COMMAND_EXECUTION_BUDGET_MILLIS

    private fun newNonce(): String {
        val bytes = ByteArray(
            R2CompilerProcessSupervisorProtocol.PROCESS_NONCE_BYTES,
        )
        SecureRandom().nextBytes(bytes)
        return try {
            buildString(bytes.size * 2) {
                bytes.forEach { value ->
                    val unsigned = value.toInt() and 0xff
                    append(HEX[unsigned ushr 4])
                    append(HEX[unsigned and 0x0f])
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    private const val HEX = "0123456789abcdef"
    private const val PREARM_TEST_POLL_INTERVAL_MILLIS = 25L
    private const val PREARM_POST_COMMIT_VISIBILITY_MILLIS = 2_000L
    private const val MAX_TEST_TERMINATION_DELAY_MILLIS = 5_000L
}

internal sealed class R2CompilerProcessQuarantineSnapshot {

    data object Absent : R2CompilerProcessQuarantineSnapshot()

    data class Exact(
        val identity: R2CompilerProcessEpochIdentity,
    ) : R2CompilerProcessQuarantineSnapshot()

    data object CorruptOrUnreadable :
        R2CompilerProcessQuarantineSnapshot()
}

internal data class R2CompilerProcessQuarantineInspection(
    val snapshot: R2CompilerProcessQuarantineSnapshot,
    val prearmWindowOpen: Boolean,
)

/**
 * Cross-process admission fence for the narrow interval between self-termination and old-epoch
 * death proof. A successor Service must not open admission while this exact marker exists.
 */
internal object R2CompilerProcessQuarantine {

    fun hasMarker(context: Context): Boolean =
        marker(context).baseFile.exists()

    fun readExactIdentity(context: Context): R2CompilerProcessEpochIdentity? =
        withMarkerLock(context, failure = null) {
            readIdentity(marker(context))
        }

    fun markExact(
        context: Context,
        identity: R2CompilerProcessEpochIdentity,
    ): Boolean =
        withMarkerLock(context, failure = false) {
            markExactLocked(marker(context), identity)
        }

    fun markExactBeforeDeadline(
        context: Context,
        identity: R2CompilerProcessEpochIdentity,
        commandDeadline: Long,
    ): Boolean =
        withMarkerLock(context, failure = false) {
            if (
                !R2CompilerProcessEpoch.hasUsableCommandDeadline(
                    commandDeadline,
                )
            ) {
                return@withMarkerLock false
            }
            markExactLocked(marker(context), identity)
        }

    fun inspectForJob(
        context: Context,
        prearmDeadline: Long?,
    ): R2CompilerProcessQuarantineInspection =
        withMarkerLock(
            context = context,
            failure = R2CompilerProcessQuarantineInspection(
                snapshot =
                    R2CompilerProcessQuarantineSnapshot.CorruptOrUnreadable,
                prearmWindowOpen = false,
            ),
        ) {
            val marker = marker(context)
            val snapshot = when {
                !marker.baseFile.exists() ->
                    R2CompilerProcessQuarantineSnapshot.Absent
                else ->
                    readIdentity(marker)?.let {
                        R2CompilerProcessQuarantineSnapshot.Exact(it)
                    } ?: R2CompilerProcessQuarantineSnapshot.CorruptOrUnreadable
            }
            R2CompilerProcessQuarantineInspection(
                snapshot = snapshot,
                prearmWindowOpen =
                    snapshot === R2CompilerProcessQuarantineSnapshot.Absent &&
                        prearmDeadline != null &&
                        R2CompilerProcessEpoch.hasUsableCommandDeadline(
                            prearmDeadline,
                        ),
            )
        }

    private fun markExactLocked(
        marker: AtomicFile,
        identity: R2CompilerProcessEpochIdentity,
    ): Boolean {
        if (marker.baseFile.exists()) {
            return readIdentity(marker) == identity
        }
        val bytes = encode(identity)
        var output: FileOutputStream? = null
        return try {
            output = marker.startWrite()
            output.write(bytes)
            marker.finishWrite(output)
            output = null
            fsyncDirectory(marker.baseFile.parentFile) &&
                readIdentity(marker) == identity
        } catch (_: Throwable) {
            output?.let(marker::failWrite)
            false
        } finally {
            bytes.fill(0)
        }
    }

    fun clearExact(
        context: Context,
        expected: R2CompilerProcessEpochIdentity,
        beforeMutation: () -> Unit = {},
    ): Boolean =
        withMarkerLock(context, failure = false) {
            clearExactLocked(marker(context), expected, beforeMutation)
        }

    private fun clearExactLocked(
        marker: AtomicFile,
        expected: R2CompilerProcessEpochIdentity,
        beforeMutation: () -> Unit,
    ): Boolean {
        if (readIdentity(marker) != expected) return false
        beforeMutation()
        marker.delete()
        return !marker.baseFile.exists() &&
            fsyncDirectory(marker.baseFile.parentFile)
    }

    fun clearExactOrAbsent(
        context: Context,
        expected: R2CompilerProcessEpochIdentity,
        beforeMutation: () -> Unit = {},
    ): Boolean =
        withMarkerLock(context, failure = false) {
            val marker = marker(context)
            if (!marker.baseFile.exists()) {
                beforeMutation()
                true
            } else {
                clearExactLocked(marker, expected, beforeMutation)
            }
        }

    /**
     * AtomicFile protects a single write, but not a cross-process compare-and-delete sequence.
     * Pair a process-local monitor with an OS file lock so every exact read/mutation observes one
     * marker generation and an old clearer can never delete a newly written epoch (ABA).
     */
    private fun <T> withMarkerLock(
        context: Context,
        failure: T,
        block: () -> T,
    ): T =
        synchronized(PROCESS_LOCK) {
            try {
                RandomAccessFile(lockFile(context), "rw").use { randomAccessFile ->
                    val channel = randomAccessFile.channel
                    val lock = channel.lock()
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

    private fun marker(context: Context): AtomicFile =
        AtomicFile(
            File(
                context.applicationContext.noBackupFilesDir,
                MARKER_FILE_NAME,
            ),
        )

    private fun lockFile(context: Context): File =
        File(
            context.applicationContext.noBackupFilesDir,
            MARKER_LOCK_FILE_NAME,
        )

    private fun encode(identity: R2CompilerProcessEpochIdentity): ByteArray =
        buildString {
            append(FORMAT_HEADER).append('\n')
            append(identity.bootId).append('\n')
            append(identity.uid).append('\n')
            append(identity.pid).append('\n')
            append(identity.startTimeTicks).append('\n')
            append(identity.processName).append('\n')
            append(identity.nonce).append('\n')
        }.toByteArray(Charsets.UTF_8)

    private fun readIdentity(marker: AtomicFile): R2CompilerProcessEpochIdentity? {
        if (!marker.baseFile.exists()) return null
        val bytes = try {
            marker.openRead().use { input ->
                val output = ByteArray(MAX_MARKER_BYTES + 1)
                var used = 0
                while (used < output.size) {
                    val read = input.read(output, used, output.size - used)
                    if (read < 0) break
                    if (read == 0) continue
                    used += read
                }
                if (used > MAX_MARKER_BYTES) return null
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
            R2CompilerProcessEpochIdentity(
                bootId = lines[1],
                uid = lines[2].toInt(),
                pid = lines[3].toInt(),
                startTimeTicks = lines[4].toLong(),
                processName = lines[5],
                nonce = lines[6],
            )
        } catch (_: Throwable) {
            null
        } finally {
            bytes.fill(0)
        }
    }

    private fun fsyncDirectory(directory: File?): Boolean {
        directory ?: return false
        val descriptor = try {
            Os.open(
                directory.absolutePath,
                OsConstants.O_RDONLY,
                0,
            )
        } catch (_: Throwable) {
            return false
        }
        return try {
            Os.fsync(descriptor)
            true
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { Os.close(descriptor) }
        }
    }

    private const val FORMAT_HEADER = "autojs6-r2-compiler-quarantine-v2"
    /*
     * Keep the existing pathname while upgrading the payload format. A legacy v1 payload then
     * remains visible-but-unreadable and fails closed instead of being silently bypassed.
     */
    private const val MARKER_FILE_NAME = "r2-compiler-process-quarantine-v1"
    private const val MARKER_LOCK_FILE_NAME = "r2-compiler-process-quarantine.lock"
    private const val MAX_MARKER_BYTES = 1024
    private const val EXPECTED_LINE_COUNT = 8
    private val PROCESS_LOCK = Any()
}

internal data class R2ObservedCompilerProcess(
    val bootId: String,
    val uid: Int,
    val startTimeTicks: Long,
    val processName: String,
)

internal class R2CompilerProcessProcfs(
    private val bootIdProvider: (() -> String?)? = null,
) {

    fun capture(pid: Int): R2ObservedCompilerProcess? {
        val bootId = currentBootId()
            ?.takeIf(R2CompilerProcessEpochIdentity::isCanonicalBootId)
            ?: return null
        val firstStat = readBounded("/proc/$pid/stat", MAX_STAT_BYTES)
            ?: return null
        val firstStartTime = parseProcessStartTimeTicks(firstStat)
            ?: return null
        val directoryStat = runCatching { Os.stat("/proc/$pid") }
            .getOrNull()
            ?: return null
        val processName = readBounded("/proc/$pid/cmdline", MAX_CMDLINE_BYTES)
            ?.substringBefore('\u0000')
            ?.takeIf(String::isNotBlank)
            ?: return null
        val secondStat = readBounded("/proc/$pid/stat", MAX_STAT_BYTES)
            ?: return null
        val secondStartTime = parseProcessStartTimeTicks(secondStat)
            ?: return null
        if (firstStartTime != secondStartTime) return null
        return R2ObservedCompilerProcess(
            bootId = bootId,
            uid = directoryStat.st_uid,
            startTimeTicks = firstStartTime,
            processName = processName,
        )
    }

    fun observe(
        expected: R2CompilerProcessEpochIdentity,
    ): R2CompilerProcessObservation {
        val currentBootId = currentBootId()
            ?.takeIf(R2CompilerProcessEpochIdentity::isCanonicalBootId)
            ?: return R2CompilerProcessObservation.UNREADABLE
        if (!secureEquals(expected.bootId, currentBootId)) {
            return R2CompilerProcessObservation.REUSED
        }
        val firstStat = readBounded("/proc/${expected.pid}/stat", MAX_STAT_BYTES)
            ?: return unavailableObservation(expected.pid)
        val firstStartTime = parseProcessStartTimeTicks(firstStat)
            ?: return R2CompilerProcessObservation.UNREADABLE
        if (firstStartTime != expected.startTimeTicks) {
            return R2CompilerProcessObservation.REUSED
        }

        val directoryStat = try {
            Os.stat("/proc/${expected.pid}")
        } catch (error: ErrnoException) {
            return errnoObservation(error)
        } catch (_: Throwable) {
            return R2CompilerProcessObservation.UNREADABLE
        }
        val processName = readBounded(
            "/proc/${expected.pid}/cmdline",
            MAX_CMDLINE_BYTES,
        )?.substringBefore('\u0000')
            ?: return unavailableObservation(expected.pid)
        val secondStat = readBounded(
            "/proc/${expected.pid}/stat",
            MAX_STAT_BYTES,
        ) ?: return unavailableObservation(expected.pid)
        val secondStartTime = parseProcessStartTimeTicks(secondStat)
            ?: return R2CompilerProcessObservation.UNREADABLE

        return when {
            secondStartTime != expected.startTimeTicks ->
                R2CompilerProcessObservation.REUSED
            directoryStat.st_uid != expected.uid ->
                R2CompilerProcessObservation.REUSED
            processName != expected.processName ->
                R2CompilerProcessObservation.REUSED
            else -> R2CompilerProcessObservation.SAME
        }
    }

    private fun unavailableObservation(pid: Int): R2CompilerProcessObservation =
        try {
            Os.stat("/proc/$pid")
            R2CompilerProcessObservation.UNREADABLE
        } catch (error: ErrnoException) {
            errnoObservation(error)
        } catch (_: Throwable) {
            R2CompilerProcessObservation.UNREADABLE
        }

    private fun errnoObservation(error: ErrnoException): R2CompilerProcessObservation =
        if (
            error.errno == OsConstants.ENOENT ||
            error.errno == OsConstants.ESRCH
        ) {
            R2CompilerProcessObservation.ABSENT
        } else {
            R2CompilerProcessObservation.UNREADABLE
        }

    private fun readBounded(path: String, maximumBytes: Int): String? =
        try {
            FileInputStream(path).use { input ->
                val output = ByteArray(maximumBytes + 1)
                var used = 0
                while (used < output.size) {
                    val read = input.read(output, used, output.size - used)
                    if (read < 0) break
                    if (read == 0) continue
                    used += read
                }
                if (used > maximumBytes) {
                    null
                } else {
                    String(output, 0, used, Charsets.UTF_8)
                }
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }

    private fun readKernelBootId(): String? =
        readBounded(BOOT_ID_PATH, MAX_BOOT_ID_BYTES)
            ?.trim()
            ?.takeIf(R2CompilerProcessEpochIdentity::isCanonicalBootId)

    private fun currentBootId(): String? =
        bootIdProvider?.invoke() ?: readKernelBootId()

    companion object {

        internal fun parseProcessStartTimeTicks(stat: String): Long? {
            val processNameEnd = stat.lastIndexOf(')')
            if (processNameEnd < 0 || processNameEnd >= stat.lastIndex) return null
            val fieldsFromState = stat
                .substring(processNameEnd + 1)
                .trim()
                .split(WHITESPACE)
            return fieldsFromState
                .getOrNull(PROCESS_START_TIME_INDEX_FROM_STATE)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        }

        private val WHITESPACE = Regex("\\s+")
        private const val BOOT_ID_PATH = "/proc/sys/kernel/random/boot_id"
        private const val MAX_BOOT_ID_BYTES = 64
        private const val PROCESS_START_TIME_INDEX_FROM_STATE = 19
        private const val MAX_STAT_BYTES = 4 * 1024
        private const val MAX_CMDLINE_BYTES = 512
    }
}

private fun secureEquals(
    expected: String,
    actual: String,
): Boolean {
    val expectedBytes = expected.toByteArray(Charsets.UTF_8)
    val actualBytes = actual.toByteArray(Charsets.UTF_8)
    return try {
        MessageDigest.isEqual(expectedBytes, actualBytes)
    } finally {
        expectedBytes.fill(0)
        actualBytes.fill(0)
    }
}
