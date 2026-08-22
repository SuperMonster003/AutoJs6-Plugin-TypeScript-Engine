package io.github.supermonster003.autojs6.plugin.typescript

/**
 * Debug-only, app-internal observability for the real remote Service lifecycle gate.
 *
 * These keys are deliberately outside the public compiler contract. Release builds ignore the
 * control keys and never add the telemetry keys to a response.
 */
internal object CompilerServiceLifecycleTestProtocol {

    const val KEY_HOLD_UNTIL_SERVICE_DESTROYED =
        "__autojs6_r1_debug_hold_until_service_destroyed"
    const val KEY_INJECT_WRONG_REQUEST_ID_FAILURE =
        "__autojs6_r1_debug_inject_wrong_request_id_failure"

    const val KEY_CORRELATION_REPORT_AVAILABLE =
        "__autojs6_r1_debug_correlation_report_available"
    const val KEY_CORRELATION_FAULT_INJECTED =
        "__autojs6_r1_debug_correlation_fault_injected"
    const val KEY_CORRELATION_FAULT_IGNORED =
        "__autojs6_r1_debug_correlation_fault_ignored"

    const val KEY_LIVE_TELEMETRY_ENABLED =
        "__autojs6_r1_debug_live_telemetry_enabled"
    const val KEY_LIVE_CLOSED = "__autojs6_r1_debug_live_closed"
    const val KEY_LIVE_HAS_WEB_VIEW = "__autojs6_r1_debug_live_has_web_view"
    const val KEY_LIVE_HAS_NATIVE_PORT = "__autojs6_r1_debug_live_has_native_port"
    const val KEY_LIVE_HAS_PENDING_PROBE =
        "__autojs6_r1_debug_live_has_pending_probe"
    const val KEY_LIVE_WORKER_READY = "__autojs6_r1_debug_live_worker_ready"
    const val KEY_LIVE_CLOSE_MESSAGE_POSTED =
        "__autojs6_r1_debug_live_close_message_posted"

    const val KEY_CLOSE_REPORT_AVAILABLE =
        "__autojs6_r1_debug_close_report_available"
    const val KEY_SERVICE_ON_DESTROY_ENTERED =
        "__autojs6_r1_debug_service_on_destroy_entered"
    const val KEY_CLOSE_HAD_PENDING_PROBE =
        "__autojs6_r1_debug_close_had_pending_probe"
    const val KEY_CLOSE_HAD_WEB_VIEW = "__autojs6_r1_debug_close_had_web_view"
    const val KEY_CLOSE_HAD_NATIVE_PORT =
        "__autojs6_r1_debug_close_had_native_port"
    const val KEY_CLOSE_HAD_READY_WORKER =
        "__autojs6_r1_debug_close_had_ready_worker"
    const val KEY_CLOSE_HAD_SERVICE_WORKER_CLIENT =
        "__autojs6_r1_debug_close_had_service_worker_client"
    const val KEY_CLOSE_MESSAGE_POSTED =
        "__autojs6_r1_debug_close_message_posted"
    const val KEY_CLOSE_NATIVE_PORT_CLOSED =
        "__autojs6_r1_debug_close_native_port_closed"
    const val KEY_CLOSE_WEB_VIEW_DESTROYED =
        "__autojs6_r1_debug_close_web_view_destroyed"
    const val KEY_CLOSE_SERVICE_WORKER_CLIENT_CLEARED =
        "__autojs6_r1_debug_close_service_worker_client_cleared"
    const val KEY_CLOSE_HANDLER_CALLBACKS_CLEARED =
        "__autojs6_r1_debug_close_handler_callbacks_cleared"
    const val KEY_CLOSE_CLOSED = "__autojs6_r1_debug_close_closed"
    const val KEY_CLOSE_PENDING_PROBE_RELEASED =
        "__autojs6_r1_debug_close_pending_probe_released"
    const val KEY_CLOSE_WEB_VIEW_RELEASED =
        "__autojs6_r1_debug_close_web_view_released"
    const val KEY_CLOSE_NATIVE_PORT_RELEASED =
        "__autojs6_r1_debug_close_native_port_released"
    const val KEY_CLOSE_WORKER_STATE_RESET =
        "__autojs6_r1_debug_close_worker_state_reset"
    const val KEY_CLOSE_SESSION_STATE_RESET =
        "__autojs6_r1_debug_close_session_state_reset"
    const val KEY_CLOSE_TIMEOUT_RECOVERY_RESET =
        "__autojs6_r1_debug_close_timeout_recovery_reset"
    const val KEY_CLOSE_PROTOCOL_EXECUTOR_TERMINATED =
        "__autojs6_r1_debug_close_protocol_executor_terminated"
    const val KEY_CLOSE_RESULT_EXECUTOR_TERMINATED =
        "__autojs6_r1_debug_close_result_executor_terminated"
    const val KEY_CLOSE_RESULT_FINALIZERS_RELEASED =
        "__autojs6_r1_debug_close_result_finalizers_released"
}

internal data class CompilerHostLifecycleTelemetry(
    val closed: Boolean,
    val hasWebView: Boolean,
    val hasNativePort: Boolean,
    val hasPendingProbe: Boolean,
    val workerReady: Boolean,
    val closeMessagePosted: Boolean,
)

internal data class CompilerHostCorrelationTestReport(
    val faultInjected: Boolean,
    val faultIgnored: Boolean,
)

internal data class CompilerHostCloseReport(
    val hadPendingProbe: Boolean,
    val hadWebView: Boolean,
    val hadNativePort: Boolean,
    val hadReadyWorker: Boolean,
    val hadServiceWorkerClient: Boolean,
    val closeMessagePosted: Boolean,
    val nativePortClosed: Boolean,
    val webViewDestroyed: Boolean,
    val serviceWorkerClientCleared: Boolean,
    val handlerCallbacksCleared: Boolean,
    val closed: Boolean,
    val pendingProbeReleased: Boolean,
    val webViewReleased: Boolean,
    val nativePortReleased: Boolean,
    val workerStateReset: Boolean,
    val sessionStateReset: Boolean,
    val timeoutRecoveryReset: Boolean,
    val protocolExecutorTerminated: Boolean,
    val resultExecutorTerminated: Boolean,
    val resultFinalizersReleased: Boolean,
) {
    val cleanupProven: Boolean
        get() =
            nativePortClosed &&
                webViewDestroyed &&
                serviceWorkerClientCleared &&
                handlerCallbacksCleared &&
                closed &&
                pendingProbeReleased &&
                webViewReleased &&
                nativePortReleased &&
                workerStateReset &&
                sessionStateReset &&
                timeoutRecoveryReset &&
                protocolExecutorTerminated &&
                resultExecutorTerminated &&
                resultFinalizersReleased
}
