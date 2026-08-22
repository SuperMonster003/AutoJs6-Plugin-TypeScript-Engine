package io.github.supermonster003.autojs6.plugin.typescript

import java.util.UUID

/**
 * Opaque host-operation identity. Public request IDs, Binder ownership and job tokens remain in
 * the future admission coordinator and are deliberately not used as the Worker teardown key.
 */
internal class R2HostProjectOperationHandle private constructor(
    internal val requestId: String,
) {

    private val stateLock = Any()
    private var claimed = false
    private var terminal = false
    private var stopCause: R2ProjectStopCause? = null
    private var projectInputTransportCreated = false
    private var projectInputTransportTelemetry:
        R2ProjectInputTransportTelemetry? = null

    internal fun claim(): Boolean = synchronized(stateLock) {
        if (claimed) {
            false
        } else {
            claimed = true
            true
        }
    }

    /**
     * Records terminal intent before or after the compile thread registers its pending slot.
     * The first cause wins; repeats remain idempotently accepted until terminal publication.
     */
    internal fun requestStop(cause: R2ProjectStopCause): Boolean = synchronized(stateLock) {
        if (terminal) {
            false
        } else {
            if (stopCause == null) {
                stopCause = cause
            }
            true
        }
    }

    internal fun acceptedStopCause(): R2ProjectStopCause? = synchronized(stateLock) {
        stopCause
    }

    internal fun markProjectInputTransportCreated() {
        synchronized(stateLock) {
            check(claimed && !terminal && !projectInputTransportCreated) {
                "Project input transport creation does not match the host operation."
            }
            projectInputTransportCreated = true
        }
    }

    internal fun recordProjectInputTransportCleanup(
        telemetry: R2ProjectInputTransportTelemetry,
    ) {
        synchronized(stateLock) {
            check(projectInputTransportCreated && telemetry.requestId == requestId) {
                "Project input transport cleanup does not match the host operation."
            }
            projectInputTransportTelemetry = telemetry
        }
    }

    internal fun cursorCleanupEvidence():
        R2HostProjectCursorCleanupEvidence = synchronized(stateLock) {
            if (!projectInputTransportCreated) {
                return@synchronized R2HostProjectCursorCleanupEvidence.notCreated()
            }
            val telemetry = projectInputTransportTelemetry
                ?: return@synchronized R2HostProjectCursorCleanupEvidence.unproven()
            if (
                telemetry.released &&
                !telemetry.activeCursor &&
                telemetry.openedCursorCount == telemetry.closedCursorCount
            ) {
                R2HostProjectCursorCleanupEvidence.released(
                    openedCursorCount = telemetry.openedCursorCount,
                    closedCursorCount = telemetry.closedCursorCount,
                )
            } else {
                R2HostProjectCursorCleanupEvidence.unproven(
                    openedCursorCount = telemetry.openedCursorCount,
                    closedCursorCount = telemetry.closedCursorCount,
                    released = telemetry.released,
                )
            }
        }

    /**
     * Linearizes deadline expiry, an already accepted stop, and successful completion.
     *
     * The monotonic clock supplier is deliberately invoked while [stateLock] is held. Sampling
     * before entering this critical section would leave a completion-vs-timeout TOCTOU in which
     * the caller could be descheduled across the deadline and still publish COMPLETED.
     */
    internal fun tryCompleteBeforeElapsedRealtimeDeadline(
        deadlineAtElapsedRealtimeNanos: Long,
        elapsedRealtimeNanos: () -> Long,
    ): R2HostProjectTerminalDecision = synchronized(stateLock) {
        when {
            terminal -> R2HostProjectTerminalDecision.ALREADY_TERMINAL
            stopCause != null -> R2HostProjectTerminalDecision.STOP_REQUESTED
            elapsedRealtimeNanos() >= deadlineAtElapsedRealtimeNanos -> {
                stopCause = R2ProjectStopCause.TIMED_OUT
                R2HostProjectTerminalDecision.STOP_REQUESTED
            }
            else -> {
                terminal = true
                R2HostProjectTerminalDecision.COMPLETED
            }
        }
    }

    internal fun completeStopped(): R2ProjectStopCause? = synchronized(stateLock) {
        if (terminal) {
            null
        } else {
            stopCause?.also {
                terminal = true
            }
        }
    }

    internal fun completeUnregistered(): R2ProjectStopCause? = synchronized(stateLock) {
        check(!terminal) { "Project operation is already terminal." }
        terminal = true
        stopCause
    }

    companion object {
        fun create(): R2HostProjectOperationHandle =
            R2HostProjectOperationHandle(UUID.randomUUID().toString())
    }
}

internal data class R2HostProjectCursorCleanupEvidence(
    val state: String,
    val openedCursorCount: Int,
    val closedCursorCount: Int,
    val released: Boolean,
) {

    val cleanupProven: Boolean
        get() =
            released &&
                openedCursorCount == closedCursorCount &&
                (
                    state ==
                        R2ExternalPlusOneCleanupEvidenceProtocol
                            .CURSOR_STATE_NOT_CREATED ||
                        state ==
                        R2ExternalPlusOneCleanupEvidenceProtocol
                            .CURSOR_STATE_RELEASED
                    )

    companion object {
        fun notCreated() = R2HostProjectCursorCleanupEvidence(
            state =
                R2ExternalPlusOneCleanupEvidenceProtocol
                    .CURSOR_STATE_NOT_CREATED,
            openedCursorCount = 0,
            closedCursorCount = 0,
            released = true,
        )

        fun released(
            openedCursorCount: Int,
            closedCursorCount: Int,
        ) = R2HostProjectCursorCleanupEvidence(
            state =
                R2ExternalPlusOneCleanupEvidenceProtocol
                    .CURSOR_STATE_RELEASED,
            openedCursorCount = openedCursorCount,
            closedCursorCount = closedCursorCount,
            released = true,
        )

        fun unproven(
            openedCursorCount: Int = 0,
            closedCursorCount: Int = 0,
            released: Boolean = false,
        ) = R2HostProjectCursorCleanupEvidence(
            state =
                R2ExternalPlusOneCleanupEvidenceProtocol
                    .CURSOR_STATE_UNPROVEN,
            openedCursorCount = openedCursorCount,
            closedCursorCount = closedCursorCount,
            released = released,
        )
    }
}

internal enum class R2HostProjectTerminalDecision {
    COMPLETED,
    STOP_REQUESTED,
    ALREADY_TERMINAL,
}

internal enum class R2ProjectStopCause {
    CANCELLED,
    TIMED_OUT,
    CALLBACK_DIED,
    SERVICE_DESTROYED,
}
