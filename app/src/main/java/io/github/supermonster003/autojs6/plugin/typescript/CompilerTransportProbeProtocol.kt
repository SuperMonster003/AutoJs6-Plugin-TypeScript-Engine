package io.github.supermonster003.autojs6.plugin.typescript

/**
 * Debug-only controls for exercising the frozen Binder/FD job lifecycle before R2 enables
 * project compilation.
 *
 * The controls are intentionally outside the public compiler contract. The service accepts them
 * only in a debug build, from its own UID, and only after the ordinary R1 admission gate has
 * validated the complete public envelope and archive descriptors. No source archive is read and
 * no compilation output is produced.
 */
internal object CompilerTransportProbeProtocol {

    const val KEY_ENABLED = "__autojs6_r1_debug_transport_probe"
    const val KEY_MODE = "__autojs6_r1_debug_transport_probe_mode"
    const val KEY_COMPLETION_DELAY_MS =
        "__autojs6_r1_debug_transport_probe_completion_delay_ms"
    const val KEY_CANCELLATION_COMMAND =
        "__autojs6_r1_debug_transport_probe_cancellation_command"
    const val KEY_ACTIVE_JOB_COUNT =
        "__autojs6_r1_debug_transport_probe_active_job_count"

    const val MODE_COMPLETE = "complete"
    const val MODE_HOLD = "hold"
    const val COMMAND_ACKNOWLEDGE = "acknowledge"

    const val MIN_TIMEOUT_MS = 250L
    const val DEFAULT_COMPLETION_DELAY_MS = 250L
    const val MAX_COMPLETION_DELAY_MS = 30_000L
}

internal data class CompilerTransportProbeControls(
    val mode: String,
    val completionDelayMs: Long,
)
