package io.github.supermonster003.autojs6.plugin.typescript

import java.security.SecureRandom
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Process-wide admission authority shared by every compiler entry point.
 *
 * This class owns only bounded identity and lifecycle state. Binder death links, file descriptors,
 * compiler teardown and callbacks stay with the coordinator that owns the returned lease. A lease
 * is released only after that coordinator has proved its cleanup boundary.
 */
internal class R2GlobalAdmissionArbiter(
    private val poisonObserver: () -> Unit = {},
    private val jobTokenGenerator: () -> String = SecureJobTokenGenerator()::next,
) {

    private val lock = Any()
    private val tombstones = LinkedHashMap<JobOwnerKey, R2TerminalJobTombstone>()
    private var activeLease: R2AdmissionLease? = null
    private var closed = false
    private var poisoned = false

    fun admitJob(
        callingUid: Int,
        requestId: String,
        callbackIdentity: Any,
        lateUnacknowledgedAcknowledgementAccepted: Boolean = true,
    ): R2JobAdmissionDecision = synchronized(lock) {
        val ownerKey = JobOwnerKey(callingUid, requestId)
        when {
            closed -> rejected(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Compiler admission has been destroyed.",
            )
            poisoned -> rejected(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Compiler admission requires external supervision.",
            )
            activeLease != null -> rejected(
                TypeScriptCompilerContract.ERROR_BUSY,
                "Compiler already has an active operation.",
            )
            ownerKey in tombstones -> rejected(
                TypeScriptCompilerContract.ERROR_BUSY,
                "The calling UID already used this requestId.",
            )
            tombstones.size >= MAX_TOMBSTONES -> rejected(
                TypeScriptCompilerContract.ERROR_BUSY,
                "Compiler admission history is full; process restart is required.",
            )
            else -> {
                val lease = R2AcceptedJobLease(
                    callingUid = callingUid,
                    requestId = requestId,
                    jobToken = uniqueJobToken(),
                    callbackIdentity = callbackIdentity,
                    lateUnacknowledgedAcknowledgementAccepted =
                        lateUnacknowledgedAcknowledgementAccepted,
                )
                activeLease = lease
                R2JobAdmissionDecision(
                    lease = lease,
                    errorCode = "",
                    errorMessage = "",
                )
            }
        }
    }

    fun admitInternal(ownerIdentity: Any): R2InternalAdmissionDecision = synchronized(lock) {
        when {
            closed -> internalRejected(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Compiler admission has been destroyed.",
            )
            poisoned -> internalRejected(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Compiler admission requires external supervision.",
            )
            activeLease != null -> internalRejected(
                TypeScriptCompilerContract.ERROR_BUSY,
                "Compiler already has an active operation.",
            )
            else -> {
                val lease = R2InternalOperationLease(ownerIdentity)
                activeLease = lease
                R2InternalAdmissionDecision(
                    lease = lease,
                    errorCode = "",
                    errorMessage = "",
                )
            }
        }
    }

    fun abortJobAdmission(lease: R2AcceptedJobLease): Boolean = synchronized(lock) {
        if (activeLease !== lease) {
            false
        } else {
            activeLease = null
            true
        }
    }

    fun releaseInternal(lease: R2InternalOperationLease): Boolean = synchronized(lock) {
        if (activeLease !== lease) {
            false
        } else {
            activeLease = null
            true
        }
    }

    fun completeJob(
        lease: R2AcceptedJobLease,
        terminal: R2AdmissionTerminal,
        acknowledgementWasArmed: Boolean = true,
    ): Boolean = synchronized(lock) {
        if (activeLease !== lease) {
            false
        } else {
            check(!poisoned) {
                "A poisoned compiler admission cannot publish an ordinary terminal."
            }
            activeLease = null
            tombstones[JobOwnerKey(lease.callingUid, lease.requestId)] =
                R2TerminalJobTombstone(
                    jobToken = lease.jobToken,
                    terminal = terminal,
                    lateUnacknowledgedAcknowledgementAccepted =
                        lease.lateUnacknowledgedAcknowledgementAccepted,
                    acknowledgementWasArmed = acknowledgementWasArmed,
                )
            true
        }
    }

    /**
     * Retains the exact active lease while denying all later admission.
     *
     * The slot must not be released without an external process-death or equivalent cleanup proof.
     */
    fun poison(lease: R2AcceptedJobLease): Boolean {
        var shouldNotifyObserver = false
        val poisonedCurrentLease = synchronized(lock) {
            if (activeLease !== lease) {
                false
            } else {
                if (!poisoned) {
                    poisoned = true
                    shouldNotifyObserver = true
                }
                true
            }
        }
        if (shouldNotifyObserver) {
            notifyPoisonObserver()
        }
        return poisonedCurrentLease
    }

    /**
     * Fails the whole process-local authority closed after an ownership invariant is lost.
     */
    fun failClosed() {
        val shouldNotifyObserver = synchronized(lock) {
            closed = true
            if (!poisoned) {
                poisoned = true
                true
            } else {
                false
            }
        }
        if (shouldNotifyObserver) {
            notifyPoisonObserver()
        }
    }

    fun authorizeActiveJob(
        callingUid: Int,
        requestId: String,
        jobToken: String,
    ): R2AcceptedJobLease? = synchronized(lock) {
        (activeLease as? R2AcceptedJobLease)?.takeIf { lease ->
            lease.callingUid == callingUid &&
                lease.requestId == requestId &&
                secureEquals(lease.jobToken, jobToken)
        }
    }

    fun matchesTerminalJob(
        callingUid: Int,
        requestId: String,
        jobToken: String,
    ): Boolean = synchronized(lock) {
        tombstones[JobOwnerKey(callingUid, requestId)]?.let { tombstone ->
            secureEquals(tombstone.jobToken, jobToken)
        } == true
    }

    /**
     * Public preview reports a missed acknowledgement as false; the private debug transport keeps
     * its historical idempotent result so existing lifecycle probes remain source-compatible.
     */
    fun acceptsTerminalAcknowledgement(
        callingUid: Int,
        requestId: String,
        jobToken: String,
    ): Boolean = synchronized(lock) {
        tombstones[JobOwnerKey(callingUid, requestId)]?.let { tombstone ->
            secureEquals(tombstone.jobToken, jobToken) &&
                (
                    tombstone.acknowledgementWasArmed ||
                        tombstone.lateUnacknowledgedAcknowledgementAccepted
                    )
        } == true
    }

    fun isCurrent(lease: R2AdmissionLease): Boolean = synchronized(lock) {
        activeLease === lease
    }

    fun activeAcceptedJobCount(): Int = synchronized(lock) {
        if (activeLease is R2AcceptedJobLease) 1 else 0
    }

    fun requiresExternalSupervision(): Boolean = synchronized(lock) {
        poisoned
    }

    fun close() {
        synchronized(lock) {
            closed = true
        }
    }

    /**
     * Starts a later Service lifecycle in the same process without discarding owner history.
     *
     * Poison is process-scoped and therefore can only be cleared by actual process death.
     */
    fun openServiceEpoch() {
        synchronized(lock) {
            if (!poisoned) {
                closed = false
            }
        }
    }

    private fun notifyPoisonObserver() {
        runCatching(poisonObserver)
    }

    private fun uniqueJobToken(): String {
        repeat(MAX_TOKEN_GENERATION_ATTEMPTS) {
            val token = jobTokenGenerator()
            require(
                token.isNotBlank() &&
                    token.length <= TypeScriptCompilerContract.MAX_JOB_TOKEN_CHARS,
            ) {
                "Generated compiler job token is outside the contract bounds."
            }
            val activeToken = (activeLease as? R2AcceptedJobLease)?.jobToken
            if (
                activeToken?.let { secureEquals(it, token) } != true &&
                tombstones.values.none { tombstone ->
                    secureEquals(tombstone.jobToken, token)
                }
            ) {
                return token
            }
        }
        error("Compiler job token generator repeatedly produced a collision.")
    }

    private fun rejected(
        errorCode: String,
        errorMessage: String,
    ) = R2JobAdmissionDecision(
        lease = null,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )

    private fun internalRejected(
        errorCode: String,
        errorMessage: String,
    ) = R2InternalAdmissionDecision(
        lease = null,
        errorCode = errorCode,
        errorMessage = errorMessage,
    )

    private data class JobOwnerKey(
        val callingUid: Int,
        val requestId: String,
    )

    private data class R2TerminalJobTombstone(
        val jobToken: String,
        val terminal: R2AdmissionTerminal,
        val lateUnacknowledgedAcknowledgementAccepted: Boolean,
        val acknowledgementWasArmed: Boolean,
    )

    private class SecureJobTokenGenerator {

        private val random = SecureRandom()

        fun next(): String {
            val bytes = ByteArray(JOB_TOKEN_BYTES)
            random.nextBytes(bytes)
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
    }

    private companion object {
        const val JOB_TOKEN_BYTES = 32
        const val MAX_TOMBSTONES = 256
        const val MAX_TOKEN_GENERATION_ATTEMPTS = 16
        const val HEX = "0123456789abcdef"
    }
}

/**
 * One admission authority for the lifetime of the compiler process, including Service recreation.
 */
internal object R2ProcessAdmissionAuthority {

    private val processArbiter = R2GlobalAdmissionArbiter(
        poisonObserver = {
            R2CompilerProcessSupervisorClient.onProcessPoisoned()
        },
    )

    fun openServiceEpoch(): R2GlobalAdmissionArbiter =
        processArbiter.also(R2GlobalAdmissionArbiter::openServiceEpoch)

    fun requiresExternalSupervision(): Boolean =
        processArbiter.requiresExternalSupervision()
}

internal sealed interface R2AdmissionLease

internal class R2AcceptedJobLease internal constructor(
    val callingUid: Int,
    val requestId: String,
    val jobToken: String,
    val callbackIdentity: Any,
    val lateUnacknowledgedAcknowledgementAccepted: Boolean,
) : R2AdmissionLease

internal class R2InternalOperationLease internal constructor(
    val ownerIdentity: Any,
) : R2AdmissionLease

internal data class R2JobAdmissionDecision(
    val lease: R2AcceptedJobLease?,
    val errorCode: String,
    val errorMessage: String,
) {
    val accepted: Boolean
        get() = lease != null
}

internal data class R2InternalAdmissionDecision(
    val lease: R2InternalOperationLease?,
    val errorCode: String,
    val errorMessage: String,
) {
    val accepted: Boolean
        get() = lease != null
}

internal enum class R2AdmissionTerminal {
    EMITTED,
    DIAGNOSTICS,
    CANCELLED,
    TIMED_OUT,
    FAILED,
    CALLBACK_DIED,
    SERVICE_DESTROYED,
    UNACKNOWLEDGED,
}

private fun secureEquals(
    expected: String,
    actual: String,
): Boolean {
    val expectedBytes = expected.toByteArray(Charsets.UTF_8)
    val actualBytes = actual.toByteArray(Charsets.UTF_8)
    return try {
        java.security.MessageDigest.isEqual(expectedBytes, actualBytes)
    } finally {
        expectedBytes.fill(0)
        actualBytes.fill(0)
    }
}
