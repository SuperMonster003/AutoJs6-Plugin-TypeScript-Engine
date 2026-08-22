package io.github.supermonster003.autojs6.plugin.typescript

/**
 * Ordered phases reported by the dedicated project Worker.
 *
 * These values are an internal host/Worker protocol, not part of the public Binder contract.
 * INGRESS is entered after the project-start frame reaches the Worker and before it acknowledges
 * that frame. The remaining phases are entered immediately before their TypeScript work begins.
 * EMIT stays active across builder.emit, output validation and hashing, result serialization, and
 * ACK-backpressured framed egress. It completes after the final result-chunk ACK and before
 * projectResultEnd; the native typed-result finalizer is outside EMIT. This boundary proves that
 * the complete output pipeline can be stopped, not instruction-level interruption inside
 * builder.emit.
 */
internal enum class R2ProjectExecutionPhase(
    val protocolValue: String,
) {
    INGRESS("ingress"),
    PARSE("parse"),
    SEMANTIC("semantic"),
    EMIT("emit"),
    ;

    companion object {

        val ordered: List<R2ProjectExecutionPhase> = entries

        fun fromProtocolValue(value: String): R2ProjectExecutionPhase =
            entries.singleOrNull { phase -> phase.protocolValue == value }
                ?: error("Unsupported project execution phase: $value")
    }
}

internal enum class R2ProjectExecutionPhaseState(
    val protocolValue: String,
) {
    STARTED("started"),
    COMPLETED("completed"),
    ;

    companion object {

        fun fromProtocolValue(value: String): R2ProjectExecutionPhaseState =
            entries.singleOrNull { state -> state.protocolValue == value }
                ?: error("Unsupported project execution phase state: $value")
    }
}

/**
 * Bounded internal progress reported after real synchronous TypeScript work.
 *
 * Sequence 1 is the first unit reaching ceil(total / 8); sequence 2 reaches
 * ceil(total / 4). The Worker reports no other progress values.
 */
internal data class R2ProjectExecutionPhaseProgress(
    val phase: R2ProjectExecutionPhase,
    val sequence: Int,
    val completedUnits: Int,
    val totalUnits: Int,
)

/**
 * One bounded native-observed event eligible for the additive public R2 progress callback.
 *
 * The timestamp is captured only after the host has validated exact request/session/Worker
 * correlation and phase ordering. It is process-monotonic and is converted by the coordinator to
 * a duration from the irreversible public request-start boundary; no absolute clock or source
 * identity crosses Binder.
 */
internal sealed interface R2ProjectExecutionEvent {

    val phase: R2ProjectExecutionPhase
    val observedAtElapsedRealtimeNanos: Long

    data class Phase(
        override val phase: R2ProjectExecutionPhase,
        val state: R2ProjectExecutionPhaseState,
        override val observedAtElapsedRealtimeNanos: Long,
    ) : R2ProjectExecutionEvent

    data class Work(
        val progress: R2ProjectExecutionPhaseProgress,
        override val observedAtElapsedRealtimeNanos: Long,
    ) : R2ProjectExecutionEvent {

        override val phase: R2ProjectExecutionPhase
            get() = progress.phase
    }
}

/**
 * Native-observed timing window for one fully completed natural Worker phase.
 *
 * Both values are monotonic durations from the exact project start. Tests may use several
 * successful ordinary compilations to select a request-start deadline strictly between the
 * second real-work progress receipt and phase completion. No mid-flight control is exposed.
 */
internal data class R2ProjectExecutionPhaseTimingEvidence(
    val phase: R2ProjectExecutionPhase,
    val sequenceTwoElapsedRealtimeNanosSinceProjectStart: Long,
    val completedElapsedRealtimeNanosSinceProjectStart: Long,
)

/**
 * One exact host-operation snapshot retained only by the non-shipping external evidence builds.
 *
 * The coordinator consumes this object immediately after [TypeScriptWebViewCompilerHost] returns.
 * Keeping the request identity, project-start clock, phase histories, termination selection, and
 * input telemetry in one immutable value prevents a later compilation from being combined with
 * stale global test telemetry.
 */
internal data class R2ProjectCompilationEvidenceSnapshot(
    val requestId: String,
    val projectStartedAtElapsedRealtimeNanos: Long,
    val phaseProgressHistory: List<R2ProjectExecutionPhaseProgress>,
    val phaseTimingEvidence: List<R2ProjectExecutionPhaseTimingEvidence>,
    val phaseTerminationEvidence: R2ProjectPhaseTerminationEvidence?,
    val inputTransportTelemetry: R2ProjectInputTransportTelemetry,
    val sessionGeneration: Int?,
    val workerGeneration: Int?,
)

/**
 * Request-correlated proof composed from the native stop-selection snapshot and
 * the host page's state immediately before it terminates the exact Worker.
 *
 * This is internal instrumentation evidence, not a public progress or Binder API.
 */
internal data class R2ProjectPhaseTerminationEvidence(
    val requestId: String,
    val stopCause: R2ProjectStopCause,
    val phaseAtHostStopSelection: R2ProjectExecutionPhase,
    val enteredPhasesAtHostStopSelection: List<R2ProjectExecutionPhase>,
    val completedPhasesAtHostStopSelection: List<R2ProjectExecutionPhase>,
    val sessionGeneration: Int,
    val workerGeneration: Int,
    val deadlineAtElapsedRealtimeMillis: Long,
    val deadlineAtElapsedRealtimeNanos: Long,
    val stopSelectedAtElapsedRealtimeMillis: Long,
    val stopSelectedAtElapsedRealtimeNanos: Long,
    val phaseAtPageTermination: R2ProjectExecutionPhase,
    val phaseProgressSequenceAtPageTermination: Int,
    val phaseProgressCompletedUnitsAtPageTermination: Int,
    val phaseProgressTotalUnitsAtPageTermination: Int,
    val terminatedWorkerGeneration: Int,
    val replacementWorkerGeneration: Int,
) {

    val phase: R2ProjectExecutionPhase
        get() = phaseAtPageTermination

    val sequence: Int
        get() = phaseProgressSequenceAtPageTermination

    val completedUnits: Int
        get() = phaseProgressCompletedUnitsAtPageTermination

    val totalUnits: Int
        get() = phaseProgressTotalUnitsAtPageTermination
}
