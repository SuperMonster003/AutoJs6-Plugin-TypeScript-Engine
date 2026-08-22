package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Bundle
import java.util.Collections

/**
 * Private, non-shipping receipt protocol for an exact external natural-phase timeout fixture.
 *
 * The receipt bridges the public Binder request ID to the private host request ID, but grants no
 * compilation authority. Production call sites must remain guarded by
 * [BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE].
 */
internal object R2ExternalNaturalPhaseTimeoutEvidenceProtocol {

    private const val PREFIX =
        "__autojs6_r2_external_natural_phase_timeout_"

    const val KEY_ENABLED = "${PREFIX}enabled"
    const val KEY_RECEIPT_AVAILABLE = "${PREFIX}receipt_available"
    const val KEY_REQUEST_ID = "${PREFIX}request_id"
    const val KEY_HOST_REQUEST_ID = "${PREFIX}host_request_id"
    const val KEY_OUTCOME = "${PREFIX}outcome"
    const val KEY_CREATED_BEFORE_CALLBACK =
        "${PREFIX}created_before_callback"
    const val KEY_PROJECT_START_NANOS_SINCE_REQUEST_START =
        "${PREFIX}project_start_nanos_since_request_start"

    const val KEY_PHASE_VALUES = "${PREFIX}phase_values"
    const val KEY_PHASE_SEQUENCE_TWO_NANOS_SINCE_REQUEST_START =
        "${PREFIX}phase_sequence_two_nanos_since_request_start"
    const val KEY_PHASE_COMPLETED_NANOS_SINCE_REQUEST_START =
        "${PREFIX}phase_completed_nanos_since_request_start"

    const val KEY_TERMINATION_AVAILABLE =
        "${PREFIX}termination_available"
    const val KEY_TERMINATION_STOP_CAUSE =
        "${PREFIX}termination_stop_cause"
    const val KEY_TERMINATION_PHASE_AT_HOST_STOP_SELECTION =
        "${PREFIX}termination_phase_at_host_stop_selection"
    const val KEY_TERMINATION_ENTERED_PHASES_AT_HOST_STOP_SELECTION =
        "${PREFIX}termination_entered_phases_at_host_stop_selection"
    const val KEY_TERMINATION_COMPLETED_PHASES_AT_HOST_STOP_SELECTION =
        "${PREFIX}termination_completed_phases_at_host_stop_selection"
    const val KEY_TERMINATION_PHASE_AT_PAGE_TERMINATION =
        "${PREFIX}termination_phase_at_page_termination"
    const val KEY_TERMINATION_DEADLINE_NANOS_SINCE_REQUEST_START =
        "${PREFIX}termination_deadline_nanos_since_request_start"
    const val KEY_TERMINATION_STOP_SELECTED_NANOS_SINCE_REQUEST_START =
        "${PREFIX}termination_stop_selected_nanos_since_request_start"
    const val KEY_TERMINATION_DEADLINE_OVERSHOOT_NANOS =
        "${PREFIX}termination_deadline_overshoot_nanos"
    const val KEY_TERMINATION_SESSION_GENERATION =
        "${PREFIX}termination_session_generation"
    const val KEY_TERMINATION_WORKER_GENERATION =
        "${PREFIX}termination_worker_generation"
    const val KEY_TERMINATION_PROGRESS_SEQUENCE =
        "${PREFIX}termination_progress_sequence"
    const val KEY_TERMINATION_PROGRESS_COMPLETED_UNITS =
        "${PREFIX}termination_progress_completed_units"
    const val KEY_TERMINATION_PROGRESS_TOTAL_UNITS =
        "${PREFIX}termination_progress_total_units"
    const val KEY_TERMINATION_TERMINATED_WORKER_GENERATION =
        "${PREFIX}termination_terminated_worker_generation"
    const val KEY_TERMINATION_REPLACEMENT_WORKER_GENERATION =
        "${PREFIX}termination_replacement_worker_generation"

    const val KEY_INPUT_SENT_FRAME_COUNT =
        "${PREFIX}input_sent_frame_count"
    const val KEY_INPUT_MAXIMUM_ENCODED_FRAME_BYTES =
        "${PREFIX}input_maximum_encoded_frame_bytes"
    const val KEY_INPUT_ACCEPTED_PAYLOAD_BYTES =
        "${PREFIX}input_accepted_payload_bytes"
    const val KEY_INPUT_TOTAL_PAYLOAD_BYTES =
        "${PREFIX}input_total_payload_bytes"
    const val KEY_INPUT_FILE_INDEX = "${PREFIX}input_file_index"
    const val KEY_INPUT_FILE_OFFSET = "${PREFIX}input_file_offset"
    const val KEY_INPUT_STAGE = "${PREFIX}input_stage"
    const val KEY_INPUT_COMPLETE = "${PREFIX}input_complete"
    const val KEY_INPUT_OPENED_CURSOR_COUNT =
        "${PREFIX}input_opened_cursor_count"
    const val KEY_INPUT_CLOSED_CURSOR_COUNT =
        "${PREFIX}input_closed_cursor_count"
    const val KEY_INPUT_ACTIVE_CURSOR =
        "${PREFIX}input_active_cursor"
    const val KEY_INPUT_RELEASED = "${PREFIX}input_released"
}

internal enum class R2ExternalNaturalPhaseTimeoutOutcome(
    val protocolValue: String,
) {
    SUCCESS("success"),
    TIMED_OUT("timed_out"),
    ;

    companion object {

        fun fromProtocolValue(value: String):
            R2ExternalNaturalPhaseTimeoutOutcome =
            entries.singleOrNull { outcome ->
                outcome.protocolValue == value
            } ?: error("Unsupported external phase-timeout outcome: $value")
    }
}

internal data class R2ExternalNaturalPhaseTimingReceipt(
    val phase: R2ProjectExecutionPhase,
    val sequenceTwoElapsedRealtimeNanosSinceRequestStart: Long,
    val completedElapsedRealtimeNanosSinceRequestStart: Long,
) {

    init {
        require(phase != R2ProjectExecutionPhase.INGRESS) {
            "Ingress is not a compiler-side natural phase timing receipt."
        }
        require(sequenceTwoElapsedRealtimeNanosSinceRequestStart >= 0L) {
            "Sequence-two timing precedes request start."
        }
        require(
            completedElapsedRealtimeNanosSinceRequestStart >
                sequenceTwoElapsedRealtimeNanosSinceRequestStart,
        ) {
            "Phase completion must follow sequence-two progress."
        }
    }

    companion object {

        fun from(
            evidence: R2ProjectExecutionPhaseTimingEvidence,
            projectStartedElapsedRealtimeNanosSinceRequestStart: Long,
        ) = R2ExternalNaturalPhaseTimingReceipt(
            phase = evidence.phase,
            sequenceTwoElapsedRealtimeNanosSinceRequestStart =
                Math.addExact(
                    projectStartedElapsedRealtimeNanosSinceRequestStart,
                    evidence
                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                ),
            completedElapsedRealtimeNanosSinceRequestStart =
                Math.addExact(
                    projectStartedElapsedRealtimeNanosSinceRequestStart,
                    evidence.completedElapsedRealtimeNanosSinceProjectStart,
                ),
        )
    }
}

internal class R2ExternalNaturalPhaseTerminationReceipt(
    val hostRequestId: String,
    val stopCause: R2ProjectStopCause,
    val phaseAtHostStopSelection: R2ProjectExecutionPhase,
    enteredPhasesAtHostStopSelection: List<R2ProjectExecutionPhase>,
    completedPhasesAtHostStopSelection: List<R2ProjectExecutionPhase>,
    val phaseAtPageTermination: R2ProjectExecutionPhase,
    val deadlineElapsedRealtimeNanosSinceRequestStart: Long,
    val stopSelectedElapsedRealtimeNanosSinceRequestStart: Long,
    deadlineSelectionOvershootNanos: Long,
    val sessionGeneration: Int,
    val workerGeneration: Int,
    val phaseProgressSequenceAtPageTermination: Int,
    val phaseProgressCompletedUnitsAtPageTermination: Int,
    val phaseProgressTotalUnitsAtPageTermination: Int,
    val terminatedWorkerGeneration: Int,
    val replacementWorkerGeneration: Int,
) {

    val enteredPhasesAtHostStopSelection:
        List<R2ProjectExecutionPhase> =
        immutableSnapshot(enteredPhasesAtHostStopSelection)

    val completedPhasesAtHostStopSelection:
        List<R2ProjectExecutionPhase> =
        immutableSnapshot(completedPhasesAtHostStopSelection)

    val deadlineSelectionOvershootNanos: Long =
        deadlineSelectionOvershootNanos

    init {
        require(hostRequestId.isNotBlank()) {
            "Host request ID must be present."
        }
        require(stopCause == R2ProjectStopCause.TIMED_OUT) {
            "Natural phase-timeout evidence must describe a timeout."
        }
        require(deadlineElapsedRealtimeNanosSinceRequestStart >= 0L) {
            "Deadline precedes request start."
        }
        require(
            stopSelectedElapsedRealtimeNanosSinceRequestStart >=
                deadlineElapsedRealtimeNanosSinceRequestStart,
        ) {
            "Stop selection precedes the timeout deadline."
        }
        require(
            deadlineSelectionOvershootNanos ==
                stopSelectedElapsedRealtimeNanosSinceRequestStart -
                deadlineElapsedRealtimeNanosSinceRequestStart,
        ) {
            "Deadline overshoot does not match the monotonic timestamps."
        }
        require(sessionGeneration >= 0 && workerGeneration >= 0) {
            "Session and Worker generations must be non-negative."
        }
        require(
            phaseProgressSequenceAtPageTermination >= 0 &&
                phaseProgressCompletedUnitsAtPageTermination >= 0 &&
                phaseProgressTotalUnitsAtPageTermination > 0 &&
                phaseProgressCompletedUnitsAtPageTermination <=
                    phaseProgressTotalUnitsAtPageTermination,
        ) {
            "Page-termination phase progress is invalid."
        }
        require(
            terminatedWorkerGeneration >= 0 &&
                replacementWorkerGeneration > terminatedWorkerGeneration,
        ) {
            "Worker replacement generations are invalid."
        }
        require(
            enteredPhasesAtHostStopSelection.contains(
                phaseAtHostStopSelection,
            ),
        ) {
            "The host-selected phase was not entered."
        }
        require(
            enteredPhasesAtHostStopSelection.containsAll(
                completedPhasesAtHostStopSelection,
            ),
        ) {
            "A completed phase was not entered."
        }
    }

    companion object {

        fun from(
            evidence: R2ProjectPhaseTerminationEvidence,
            requestStartedAtElapsedRealtimeNanos: Long,
        ): R2ExternalNaturalPhaseTerminationReceipt {
            val deadlineSinceRequestStart =
                Math.subtractExact(
                    evidence.deadlineAtElapsedRealtimeNanos,
                    requestStartedAtElapsedRealtimeNanos,
                )
            val stopSelectedSinceRequestStart =
                Math.subtractExact(
                    evidence.stopSelectedAtElapsedRealtimeNanos,
                    requestStartedAtElapsedRealtimeNanos,
                )
            return R2ExternalNaturalPhaseTerminationReceipt(
                hostRequestId = evidence.requestId,
                stopCause = evidence.stopCause,
                phaseAtHostStopSelection =
                    evidence.phaseAtHostStopSelection,
                enteredPhasesAtHostStopSelection =
                    evidence.enteredPhasesAtHostStopSelection,
                completedPhasesAtHostStopSelection =
                    evidence.completedPhasesAtHostStopSelection,
                phaseAtPageTermination =
                    evidence.phaseAtPageTermination,
                deadlineElapsedRealtimeNanosSinceRequestStart =
                    deadlineSinceRequestStart,
                stopSelectedElapsedRealtimeNanosSinceRequestStart =
                    stopSelectedSinceRequestStart,
                deadlineSelectionOvershootNanos =
                    Math.subtractExact(
                        stopSelectedSinceRequestStart,
                        deadlineSinceRequestStart,
                    ),
                sessionGeneration = evidence.sessionGeneration,
                workerGeneration = evidence.workerGeneration,
                phaseProgressSequenceAtPageTermination =
                    evidence.phaseProgressSequenceAtPageTermination,
                phaseProgressCompletedUnitsAtPageTermination =
                    evidence.phaseProgressCompletedUnitsAtPageTermination,
                phaseProgressTotalUnitsAtPageTermination =
                    evidence.phaseProgressTotalUnitsAtPageTermination,
                terminatedWorkerGeneration =
                    evidence.terminatedWorkerGeneration,
                replacementWorkerGeneration =
                    evidence.replacementWorkerGeneration,
            )
        }
    }
}

internal data class R2ExternalNaturalPhaseInputTelemetry(
    val hostRequestId: String,
    val sentFrameCount: Int,
    val maximumEncodedFrameBytes: Int,
    val acceptedPayloadBytes: Long,
    val totalPayloadBytes: Long,
    val fileIndex: Int,
    val fileOffset: Long,
    val stage: String,
    val complete: Boolean,
    val openedCursorCount: Int,
    val closedCursorCount: Int,
    val activeCursor: Boolean,
    val released: Boolean,
) {

    init {
        require(hostRequestId.isNotBlank()) {
            "Host request ID must be present."
        }
        require(
            sentFrameCount > 0 &&
                maximumEncodedFrameBytes > 0 &&
                acceptedPayloadBytes > 0L &&
                acceptedPayloadBytes == totalPayloadBytes &&
                fileIndex >= 0 &&
                fileOffset >= 0L,
        ) {
            "Project input transport counters are invalid."
        }
        require(stage.isNotBlank() && complete) {
            "Project input transport did not complete."
        }
        require(
            openedCursorCount > 0 &&
                closedCursorCount == openedCursorCount &&
                !activeCursor &&
                released,
        ) {
            "Project input cursor cleanup is unproven."
        }
    }

    companion object {

        fun from(
            telemetry: R2ProjectInputTransportTelemetry,
        ) = R2ExternalNaturalPhaseInputTelemetry(
            hostRequestId = telemetry.requestId,
            sentFrameCount = telemetry.sentFrameCount,
            maximumEncodedFrameBytes =
                telemetry.maximumEncodedFrameBytes,
            acceptedPayloadBytes = telemetry.acceptedPayloadBytes,
            totalPayloadBytes = telemetry.totalPayloadBytes,
            fileIndex = telemetry.fileIndex,
            fileOffset = telemetry.fileOffset,
            stage = telemetry.stage,
            complete = telemetry.complete,
            openedCursorCount = telemetry.openedCursorCount,
            closedCursorCount = telemetry.closedCursorCount,
            activeCursor = telemetry.activeCursor,
            released = telemetry.released,
        )
    }
}

internal class R2ExternalNaturalPhaseTimeoutReceipt(
    val requestId: String,
    val hostRequestId: String,
    val outcome: R2ExternalNaturalPhaseTimeoutOutcome,
    val createdBeforeCallback: Boolean,
    val projectStartedElapsedRealtimeNanosSinceRequestStart: Long,
    phaseTimings: List<R2ExternalNaturalPhaseTimingReceipt>,
    val termination: R2ExternalNaturalPhaseTerminationReceipt?,
    val inputTelemetry: R2ExternalNaturalPhaseInputTelemetry,
) {

    val phaseTimings: List<R2ExternalNaturalPhaseTimingReceipt> =
        immutableSnapshot(phaseTimings)

    init {
        require(requestId.isNotBlank() && hostRequestId.isNotBlank()) {
            "Binder and host request IDs must be present."
        }
        require(createdBeforeCallback) {
            "The immutable receipt must be created before callback delivery."
        }
        require(
            projectStartedElapsedRealtimeNanosSinceRequestStart >= 0L,
        ) {
            "Project start precedes request start."
        }
        require(inputTelemetry.hostRequestId == hostRequestId) {
            "Input telemetry belongs to another host request."
        }
        require(
            phaseTimings.map { timing -> timing.phase }.distinct().size ==
                phaseTimings.size,
        ) {
            "Natural phase timings contain duplicate phases."
        }
        require(
            phaseTimings.map { timing -> timing.phase.ordinal } ==
                phaseTimings
                    .map { timing -> timing.phase.ordinal }
                    .sorted(),
        ) {
            "Natural phase timings are out of order."
        }
        require(
            phaseTimings.all { timing ->
                timing.sequenceTwoElapsedRealtimeNanosSinceRequestStart >=
                    projectStartedElapsedRealtimeNanosSinceRequestStart
            },
        ) {
            "Natural phase progress precedes project start."
        }
        when (outcome) {
            R2ExternalNaturalPhaseTimeoutOutcome.SUCCESS -> {
                require(termination == null) {
                    "A successful receipt cannot contain termination."
                }
                require(
                    phaseTimings.map { timing -> timing.phase } ==
                        listOf(
                            R2ProjectExecutionPhase.PARSE,
                            R2ProjectExecutionPhase.SEMANTIC,
                            R2ProjectExecutionPhase.EMIT,
                        ),
                ) {
                    "A successful receipt must contain all compiler phases."
                }
            }

            R2ExternalNaturalPhaseTimeoutOutcome.TIMED_OUT -> {
                require(termination != null) {
                    "A timed-out receipt requires termination evidence."
                }
                require(termination.hostRequestId == hostRequestId) {
                    "Termination evidence belongs to another host request."
                }
                require(
                    termination.completedPhasesAtHostStopSelection
                        .containsAll(
                            phaseTimings.map { timing -> timing.phase },
                        ),
                ) {
                    "A completed timing was absent at host stop selection."
                }
            }
        }
    }
}

internal data class R2ExternalNaturalPhaseTimeoutEvidenceState(
    val receipt: R2ExternalNaturalPhaseTimeoutReceipt?,
) {

    fun putInto(bundle: Bundle) {
        putInto(
            R2ExternalNaturalPhaseTimeoutAndroidBundleValues(bundle),
        )
    }

    internal fun putInto(
        values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
    ) {
        values.putBoolean(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol.KEY_ENABLED,
            true,
        )
        values.putBoolean(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                .KEY_RECEIPT_AVAILABLE,
            receipt != null,
        )
        receipt ?: return
        putReceipt(values, receipt)
    }

    private fun putReceipt(
        values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
        receipt: R2ExternalNaturalPhaseTimeoutReceipt,
    ) {
        val protocol = R2ExternalNaturalPhaseTimeoutEvidenceProtocol
        values.putString(protocol.KEY_REQUEST_ID, receipt.requestId)
        values.putString(
            protocol.KEY_HOST_REQUEST_ID,
            receipt.hostRequestId,
        )
        values.putString(
            protocol.KEY_OUTCOME,
            receipt.outcome.protocolValue,
        )
        values.putBoolean(
            protocol.KEY_CREATED_BEFORE_CALLBACK,
            receipt.createdBeforeCallback,
        )
        values.putLong(
            protocol.KEY_PROJECT_START_NANOS_SINCE_REQUEST_START,
            receipt.projectStartedElapsedRealtimeNanosSinceRequestStart,
        )
        values.putStringArray(
            protocol.KEY_PHASE_VALUES,
            receipt.phaseTimings
                .map { timing -> timing.phase.protocolValue }
                .toTypedArray(),
        )
        values.putLongArray(
            protocol.KEY_PHASE_SEQUENCE_TWO_NANOS_SINCE_REQUEST_START,
            receipt.phaseTimings
                .map { timing ->
                    timing
                        .sequenceTwoElapsedRealtimeNanosSinceRequestStart
                }
                .toLongArray(),
        )
        values.putLongArray(
            protocol.KEY_PHASE_COMPLETED_NANOS_SINCE_REQUEST_START,
            receipt.phaseTimings
                .map { timing ->
                    timing.completedElapsedRealtimeNanosSinceRequestStart
                }
                .toLongArray(),
        )
        putTermination(values, receipt.termination)
        putInput(values, receipt.inputTelemetry)
    }

    private fun putTermination(
        values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
        termination: R2ExternalNaturalPhaseTerminationReceipt?,
    ) {
        val protocol = R2ExternalNaturalPhaseTimeoutEvidenceProtocol
        values.putBoolean(
            protocol.KEY_TERMINATION_AVAILABLE,
            termination != null,
        )
        termination ?: return
        values.putString(
            protocol.KEY_TERMINATION_STOP_CAUSE,
            termination.stopCause.name,
        )
        values.putString(
            protocol.KEY_TERMINATION_PHASE_AT_HOST_STOP_SELECTION,
            termination.phaseAtHostStopSelection.protocolValue,
        )
        values.putStringArray(
            protocol
                .KEY_TERMINATION_ENTERED_PHASES_AT_HOST_STOP_SELECTION,
            termination.enteredPhasesAtHostStopSelection
                .map { phase -> phase.protocolValue }
                .toTypedArray(),
        )
        values.putStringArray(
            protocol
                .KEY_TERMINATION_COMPLETED_PHASES_AT_HOST_STOP_SELECTION,
            termination.completedPhasesAtHostStopSelection
                .map { phase -> phase.protocolValue }
                .toTypedArray(),
        )
        values.putString(
            protocol.KEY_TERMINATION_PHASE_AT_PAGE_TERMINATION,
            termination.phaseAtPageTermination.protocolValue,
        )
        values.putLong(
            protocol
                .KEY_TERMINATION_DEADLINE_NANOS_SINCE_REQUEST_START,
            termination.deadlineElapsedRealtimeNanosSinceRequestStart,
        )
        values.putLong(
            protocol
                .KEY_TERMINATION_STOP_SELECTED_NANOS_SINCE_REQUEST_START,
            termination
                .stopSelectedElapsedRealtimeNanosSinceRequestStart,
        )
        values.putLong(
            protocol.KEY_TERMINATION_DEADLINE_OVERSHOOT_NANOS,
            termination.deadlineSelectionOvershootNanos,
        )
        values.putInt(
            protocol.KEY_TERMINATION_SESSION_GENERATION,
            termination.sessionGeneration,
        )
        values.putInt(
            protocol.KEY_TERMINATION_WORKER_GENERATION,
            termination.workerGeneration,
        )
        values.putInt(
            protocol.KEY_TERMINATION_PROGRESS_SEQUENCE,
            termination.phaseProgressSequenceAtPageTermination,
        )
        values.putInt(
            protocol.KEY_TERMINATION_PROGRESS_COMPLETED_UNITS,
            termination.phaseProgressCompletedUnitsAtPageTermination,
        )
        values.putInt(
            protocol.KEY_TERMINATION_PROGRESS_TOTAL_UNITS,
            termination.phaseProgressTotalUnitsAtPageTermination,
        )
        values.putInt(
            protocol.KEY_TERMINATION_TERMINATED_WORKER_GENERATION,
            termination.terminatedWorkerGeneration,
        )
        values.putInt(
            protocol.KEY_TERMINATION_REPLACEMENT_WORKER_GENERATION,
            termination.replacementWorkerGeneration,
        )
    }

    private fun putInput(
        values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
        input: R2ExternalNaturalPhaseInputTelemetry,
    ) {
        val protocol = R2ExternalNaturalPhaseTimeoutEvidenceProtocol
        values.putInt(
            protocol.KEY_INPUT_SENT_FRAME_COUNT,
            input.sentFrameCount,
        )
        values.putInt(
            protocol.KEY_INPUT_MAXIMUM_ENCODED_FRAME_BYTES,
            input.maximumEncodedFrameBytes,
        )
        values.putLong(
            protocol.KEY_INPUT_ACCEPTED_PAYLOAD_BYTES,
            input.acceptedPayloadBytes,
        )
        values.putLong(
            protocol.KEY_INPUT_TOTAL_PAYLOAD_BYTES,
            input.totalPayloadBytes,
        )
        values.putInt(protocol.KEY_INPUT_FILE_INDEX, input.fileIndex)
        values.putLong(protocol.KEY_INPUT_FILE_OFFSET, input.fileOffset)
        values.putString(protocol.KEY_INPUT_STAGE, input.stage)
        values.putBoolean(protocol.KEY_INPUT_COMPLETE, input.complete)
        values.putInt(
            protocol.KEY_INPUT_OPENED_CURSOR_COUNT,
            input.openedCursorCount,
        )
        values.putInt(
            protocol.KEY_INPUT_CLOSED_CURSOR_COUNT,
            input.closedCursorCount,
        )
        values.putBoolean(
            protocol.KEY_INPUT_ACTIVE_CURSOR,
            input.activeCursor,
        )
        values.putBoolean(
            protocol.KEY_INPUT_RELEASED,
            input.released,
        )
    }

    companion object {

        fun fromBundle(
            bundle: Bundle,
        ): R2ExternalNaturalPhaseTimeoutEvidenceState? =
            fromValues(
                R2ExternalNaturalPhaseTimeoutAndroidBundleValues(bundle),
            )

        internal fun fromValues(
            values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
        ): R2ExternalNaturalPhaseTimeoutEvidenceState? {
            val protocol = R2ExternalNaturalPhaseTimeoutEvidenceProtocol
            if (values.boolean(protocol.KEY_ENABLED) != true) return null
            return when (
                values.boolean(protocol.KEY_RECEIPT_AVAILABLE)
            ) {
                false ->
                    R2ExternalNaturalPhaseTimeoutEvidenceState(
                        receipt = null,
                    )

                true ->
                    runCatching {
                        R2ExternalNaturalPhaseTimeoutEvidenceState(
                            receipt = parseReceipt(values),
                        )
                    }.getOrNull()

                null -> null
            }
        }

        private fun parseReceipt(
            values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
        ): R2ExternalNaturalPhaseTimeoutReceipt {
            val protocol = R2ExternalNaturalPhaseTimeoutEvidenceProtocol
            val hostRequestId =
                values.requiredString(protocol.KEY_HOST_REQUEST_ID)
            val phaseValues =
                values.requiredStringArray(protocol.KEY_PHASE_VALUES)
            val sequenceTwoTimings =
                values.requiredLongArray(
                    protocol
                        .KEY_PHASE_SEQUENCE_TWO_NANOS_SINCE_REQUEST_START,
                )
            val completionTimings =
                values.requiredLongArray(
                    protocol
                        .KEY_PHASE_COMPLETED_NANOS_SINCE_REQUEST_START,
                )
            check(
                phaseValues.size == sequenceTwoTimings.size &&
                    phaseValues.size == completionTimings.size,
            ) {
                "Parallel phase-timing arrays differ in length."
            }
            val phaseTimings = phaseValues.indices.map { index ->
                R2ExternalNaturalPhaseTimingReceipt(
                    phase =
                        R2ProjectExecutionPhase.fromProtocolValue(
                            phaseValues[index],
                        ),
                    sequenceTwoElapsedRealtimeNanosSinceRequestStart =
                        sequenceTwoTimings[index],
                    completedElapsedRealtimeNanosSinceRequestStart =
                        completionTimings[index],
                )
            }
            val termination =
                when (
                    values.requiredBoolean(
                        protocol.KEY_TERMINATION_AVAILABLE,
                    )
                ) {
                    true ->
                        parseTermination(
                            values = values,
                            hostRequestId = hostRequestId,
                        )

                    false -> null
                }
            return R2ExternalNaturalPhaseTimeoutReceipt(
                requestId =
                    values.requiredString(protocol.KEY_REQUEST_ID),
                hostRequestId = hostRequestId,
                outcome =
                    R2ExternalNaturalPhaseTimeoutOutcome
                        .fromProtocolValue(
                            values.requiredString(protocol.KEY_OUTCOME),
                        ),
                createdBeforeCallback =
                    values.requiredBoolean(
                        protocol.KEY_CREATED_BEFORE_CALLBACK,
                    ),
                projectStartedElapsedRealtimeNanosSinceRequestStart =
                    values.requiredLong(
                        protocol
                            .KEY_PROJECT_START_NANOS_SINCE_REQUEST_START,
                    ),
                phaseTimings = phaseTimings,
                termination = termination,
                inputTelemetry =
                    parseInput(values, hostRequestId),
            )
        }

        private fun parseTermination(
            values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
            hostRequestId: String,
        ) = R2ExternalNaturalPhaseTerminationReceipt(
            hostRequestId = hostRequestId,
            stopCause =
                R2ProjectStopCause.valueOf(
                    values.requiredString(
                        R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                            .KEY_TERMINATION_STOP_CAUSE,
                    ),
                ),
            phaseAtHostStopSelection =
                values.requiredPhase(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_PHASE_AT_HOST_STOP_SELECTION,
                ),
            enteredPhasesAtHostStopSelection =
                values.requiredPhases(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_ENTERED_PHASES_AT_HOST_STOP_SELECTION,
                ),
            completedPhasesAtHostStopSelection =
                values.requiredPhases(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_COMPLETED_PHASES_AT_HOST_STOP_SELECTION,
                ),
            phaseAtPageTermination =
                values.requiredPhase(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_PHASE_AT_PAGE_TERMINATION,
                ),
            deadlineElapsedRealtimeNanosSinceRequestStart =
                values.requiredLong(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_DEADLINE_NANOS_SINCE_REQUEST_START,
                ),
            stopSelectedElapsedRealtimeNanosSinceRequestStart =
                values.requiredLong(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_STOP_SELECTED_NANOS_SINCE_REQUEST_START,
                ),
            deadlineSelectionOvershootNanos =
                values.requiredLong(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_DEADLINE_OVERSHOOT_NANOS,
                ),
            sessionGeneration =
                values.requiredInt(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_SESSION_GENERATION,
                ),
            workerGeneration =
                values.requiredInt(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_WORKER_GENERATION,
                ),
            phaseProgressSequenceAtPageTermination =
                values.requiredInt(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_PROGRESS_SEQUENCE,
                ),
            phaseProgressCompletedUnitsAtPageTermination =
                values.requiredInt(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_PROGRESS_COMPLETED_UNITS,
                ),
            phaseProgressTotalUnitsAtPageTermination =
                values.requiredInt(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_PROGRESS_TOTAL_UNITS,
                ),
            terminatedWorkerGeneration =
                values.requiredInt(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_TERMINATED_WORKER_GENERATION,
                ),
            replacementWorkerGeneration =
                values.requiredInt(
                    R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                        .KEY_TERMINATION_REPLACEMENT_WORKER_GENERATION,
                ),
        )

        private fun parseInput(
            values: R2ExternalNaturalPhaseTimeoutEvidenceValues,
            hostRequestId: String,
        ): R2ExternalNaturalPhaseInputTelemetry {
            val protocol = R2ExternalNaturalPhaseTimeoutEvidenceProtocol
            return R2ExternalNaturalPhaseInputTelemetry(
                hostRequestId = hostRequestId,
                sentFrameCount =
                    values.requiredInt(
                        protocol.KEY_INPUT_SENT_FRAME_COUNT,
                    ),
                maximumEncodedFrameBytes =
                    values.requiredInt(
                        protocol.KEY_INPUT_MAXIMUM_ENCODED_FRAME_BYTES,
                    ),
                acceptedPayloadBytes =
                    values.requiredLong(
                        protocol.KEY_INPUT_ACCEPTED_PAYLOAD_BYTES,
                    ),
                totalPayloadBytes =
                    values.requiredLong(
                        protocol.KEY_INPUT_TOTAL_PAYLOAD_BYTES,
                    ),
                fileIndex =
                    values.requiredInt(protocol.KEY_INPUT_FILE_INDEX),
                fileOffset =
                    values.requiredLong(protocol.KEY_INPUT_FILE_OFFSET),
                stage =
                    values.requiredString(protocol.KEY_INPUT_STAGE),
                complete =
                    values.requiredBoolean(protocol.KEY_INPUT_COMPLETE),
                openedCursorCount =
                    values.requiredInt(
                        protocol.KEY_INPUT_OPENED_CURSOR_COUNT,
                    ),
                closedCursorCount =
                    values.requiredInt(
                        protocol.KEY_INPUT_CLOSED_CURSOR_COUNT,
                    ),
                activeCursor =
                    values.requiredBoolean(
                        protocol.KEY_INPUT_ACTIVE_CURSOR,
                    ),
                released =
                    values.requiredBoolean(protocol.KEY_INPUT_RELEASED),
            )
        }
    }
}

/**
 * Small typed seam shared by the Android adapter and host-side JVM tests.
 *
 * Android's local unit-test stubs do not implement [Bundle], so testing the exact codec through
 * this seam avoids weakening the production parser with untyped defaults.
 */
internal interface R2ExternalNaturalPhaseTimeoutEvidenceValues {

    fun boolean(key: String): Boolean?

    fun int(key: String): Int?

    fun long(key: String): Long?

    fun string(key: String): String?

    fun stringArray(key: String): Array<String>?

    fun longArray(key: String): LongArray?

    fun putBoolean(key: String, value: Boolean)

    fun putInt(key: String, value: Int)

    fun putLong(key: String, value: Long)

    fun putString(key: String, value: String)

    fun putStringArray(key: String, value: Array<String>)

    fun putLongArray(key: String, value: LongArray)
}

private class R2ExternalNaturalPhaseTimeoutAndroidBundleValues(
    private val bundle: Bundle,
) : R2ExternalNaturalPhaseTimeoutEvidenceValues {

    override fun boolean(key: String): Boolean? = raw(key) as? Boolean

    override fun int(key: String): Int? = raw(key) as? Int

    override fun long(key: String): Long? = raw(key) as? Long

    override fun string(key: String): String? = raw(key) as? String

    override fun stringArray(key: String): Array<String>? {
        val value = raw(key)
        if (value !is Array<*>) return null
        if (value.any { element -> element !is String }) return null
        @Suppress("UNCHECKED_CAST")
        return (value as Array<String>).copyOf()
    }

    override fun longArray(key: String): LongArray? =
        (raw(key) as? LongArray)?.copyOf()

    override fun putBoolean(key: String, value: Boolean) =
        bundle.putBoolean(key, value)

    override fun putInt(key: String, value: Int) =
        bundle.putInt(key, value)

    override fun putLong(key: String, value: Long) =
        bundle.putLong(key, value)

    override fun putString(key: String, value: String) =
        bundle.putString(key, value)

    override fun putStringArray(
        key: String,
        value: Array<String>,
    ) = bundle.putStringArray(key, value.copyOf())

    override fun putLongArray(
        key: String,
        value: LongArray,
    ) = bundle.putLongArray(key, value.copyOf())

    @Suppress("DEPRECATION")
    private fun raw(key: String): Any? =
        if (bundle.containsKey(key)) bundle.get(key) else null
}

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues.requiredBoolean(
    key: String,
): Boolean = boolean(key) ?: error("Missing Boolean evidence value: $key")

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues.requiredInt(
    key: String,
): Int = int(key) ?: error("Missing Int evidence value: $key")

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues.requiredLong(
    key: String,
): Long = long(key) ?: error("Missing Long evidence value: $key")

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues.requiredString(
    key: String,
): String = string(key)
    ?.takeIf(String::isNotBlank)
    ?: error("Missing String evidence value: $key")

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues
    .requiredStringArray(
        key: String,
    ): Array<String> =
    stringArray(key) ?: error("Missing String-array evidence value: $key")

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues.requiredLongArray(
    key: String,
): LongArray =
    longArray(key) ?: error("Missing Long-array evidence value: $key")

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues.requiredPhase(
    key: String,
): R2ProjectExecutionPhase =
    R2ProjectExecutionPhase.fromProtocolValue(requiredString(key))

private fun R2ExternalNaturalPhaseTimeoutEvidenceValues.requiredPhases(
    key: String,
): List<R2ProjectExecutionPhase> =
    requiredStringArray(key).map(
        R2ProjectExecutionPhase::fromProtocolValue,
    )

private fun <T> immutableSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
