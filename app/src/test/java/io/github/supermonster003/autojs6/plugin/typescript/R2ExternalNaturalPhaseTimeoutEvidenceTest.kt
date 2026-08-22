package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ExternalNaturalPhaseTimeoutEvidenceTest {

    @Test
    fun successfulBaselineRoundTripsRequestStartPhaseTiming() {
        val state =
            R2ExternalNaturalPhaseTimeoutEvidenceState(
                receipt =
                    successReceipt(
                        mutableListOf(
                            timing(
                                R2ProjectExecutionPhase.PARSE,
                                1_100L,
                                1_300L,
                            ),
                            timing(
                                R2ProjectExecutionPhase.SEMANTIC,
                                1_500L,
                                1_900L,
                            ),
                            timing(
                                R2ProjectExecutionPhase.EMIT,
                                6_100L,
                                7_400L,
                            ),
                        ),
                    ),
            )
        val values = FakeValues()

        state.putInto(values)
        val decoded =
            R2ExternalNaturalPhaseTimeoutEvidenceState
                .fromValues(values)
        assertNotNull(decoded)
        val receipt = decoded!!.receipt

        assertNotNull(receipt)
        receipt!!
        assertEquals("binder-request", receipt.requestId)
        assertEquals("host-request", receipt.hostRequestId)
        assertSame(
            R2ExternalNaturalPhaseTimeoutOutcome.SUCCESS,
            receipt.outcome,
        )
        assertTrue(receipt.createdBeforeCallback)
        assertEquals(
            1_000L,
            receipt.projectStartedElapsedRealtimeNanosSinceRequestStart,
        )
        assertEquals(
            listOf(
                R2ProjectExecutionPhase.PARSE,
                R2ProjectExecutionPhase.SEMANTIC,
                R2ProjectExecutionPhase.EMIT,
            ),
            receipt.phaseTimings.map { timing -> timing.phase },
        )
        assertEquals(
            6_100L,
            receipt.phaseTimings.last()
                .sequenceTwoElapsedRealtimeNanosSinceRequestStart,
        )
        assertNull(receipt.termination)
        assertTrue(receipt.inputTelemetry.released)
    }

    @Test
    fun timedOutFaultRoundTripsTerminationAndExactOvershoot() {
        val receipt = timeoutReceipt()
        val values = FakeValues()

        R2ExternalNaturalPhaseTimeoutEvidenceState(receipt)
            .putInto(values)
        val decoded =
            R2ExternalNaturalPhaseTimeoutEvidenceState
                .fromValues(values)
                ?.receipt
        assertNotNull(decoded)
        val termination = decoded!!.termination

        assertNotNull(termination)
        termination!!
        assertSame(R2ProjectStopCause.TIMED_OUT, termination.stopCause)
        assertSame(
            R2ProjectExecutionPhase.EMIT,
            termination.phaseAtHostStopSelection,
        )
        assertSame(
            R2ProjectExecutionPhase.EMIT,
            termination.phaseAtPageTermination,
        )
        assertEquals(
            listOf(
                R2ProjectExecutionPhase.INGRESS,
                R2ProjectExecutionPhase.PARSE,
                R2ProjectExecutionPhase.SEMANTIC,
                R2ProjectExecutionPhase.EMIT,
            ),
            termination.enteredPhasesAtHostStopSelection,
        )
        assertEquals(6_000L, termination
            .deadlineElapsedRealtimeNanosSinceRequestStart)
        assertEquals(6_075L, termination
            .stopSelectedElapsedRealtimeNanosSinceRequestStart)
        assertEquals(75L, termination.deadlineSelectionOvershootNanos)
        assertEquals(3, termination.sessionGeneration)
        assertEquals(7, termination.workerGeneration)
        assertEquals(2, termination.phaseProgressSequenceAtPageTermination)
        assertEquals(4, termination.phaseProgressCompletedUnitsAtPageTermination)
        assertEquals(16, termination.phaseProgressTotalUnitsAtPageTermination)
        assertEquals(7, termination.terminatedWorkerGeneration)
        assertEquals(8, termination.replacementWorkerGeneration)
    }

    @Test
    fun semanticTimedOutFaultRoundTripsBundleAuthority() {
        val values = FakeValues()

        R2ExternalNaturalPhaseTimeoutEvidenceState(
            semanticTimeoutReceipt(),
        ).putInto(values)
        val decoded =
            R2ExternalNaturalPhaseTimeoutEvidenceState
                .fromValues(values)
                ?.receipt
        assertNotNull(decoded)
        decoded!!

        assertSame(
            R2ExternalNaturalPhaseTimeoutOutcome.TIMED_OUT,
            decoded.outcome,
        )
        assertTrue(decoded.createdBeforeCallback)
        assertEquals(
            listOf(R2ProjectExecutionPhase.PARSE),
            decoded.phaseTimings.map { timing -> timing.phase },
        )

        val termination = decoded.termination
        assertNotNull(termination)
        termination!!
        assertSame(R2ProjectStopCause.TIMED_OUT, termination.stopCause)
        assertSame(
            R2ProjectExecutionPhase.SEMANTIC,
            termination.phaseAtHostStopSelection,
        )
        assertSame(
            R2ProjectExecutionPhase.SEMANTIC,
            termination.phaseAtPageTermination,
        )
        assertEquals(
            listOf(
                R2ProjectExecutionPhase.INGRESS,
                R2ProjectExecutionPhase.PARSE,
                R2ProjectExecutionPhase.SEMANTIC,
            ),
            termination.enteredPhasesAtHostStopSelection,
        )
        assertEquals(
            listOf(
                R2ProjectExecutionPhase.INGRESS,
                R2ProjectExecutionPhase.PARSE,
            ),
            termination.completedPhasesAtHostStopSelection,
        )
        assertEquals(2, termination.phaseProgressSequenceAtPageTermination)
        assertEquals(
            64,
            termination.phaseProgressCompletedUnitsAtPageTermination,
        )
        assertEquals(
            256,
            termination.phaseProgressTotalUnitsAtPageTermination,
        )
        assertEquals(7, termination.workerGeneration)
        assertEquals(7, termination.terminatedWorkerGeneration)
        assertEquals(8, termination.replacementWorkerGeneration)

        val input = decoded.inputTelemetry
        assertEquals(512, input.openedCursorCount)
        assertEquals(512, input.closedCursorCount)
        assertFalse(input.activeCursor)
        assertTrue(input.released)
    }

    @Test
    fun malformedSemanticBundleMissingCompletedParseFailsClosed() {
        val values = FakeValues()
        R2ExternalNaturalPhaseTimeoutEvidenceState(
            semanticTimeoutReceipt(),
        ).putInto(values)
        values.putStringArray(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                .KEY_TERMINATION_COMPLETED_PHASES_AT_HOST_STOP_SELECTION,
            arrayOf(R2ProjectExecutionPhase.INGRESS.protocolValue),
        )

        assertNull(
            R2ExternalNaturalPhaseTimeoutEvidenceState.fromValues(values),
        )
    }

    @Test
    fun parseTimedOutFaultRoundTripsEmptyTimingAuthority() {
        val values = FakeValues()

        R2ExternalNaturalPhaseTimeoutEvidenceState(
            parseTimeoutReceipt(),
        ).putInto(values)
        val decoded =
            R2ExternalNaturalPhaseTimeoutEvidenceState
                .fromValues(values)
                ?.receipt
        assertNotNull(decoded)
        decoded!!

        assertSame(
            R2ExternalNaturalPhaseTimeoutOutcome.TIMED_OUT,
            decoded.outcome,
        )
        assertTrue(decoded.createdBeforeCallback)
        assertTrue(decoded.phaseTimings.isEmpty())

        val termination = decoded.termination
        assertNotNull(termination)
        termination!!
        assertSame(R2ProjectStopCause.TIMED_OUT, termination.stopCause)
        assertSame(
            R2ProjectExecutionPhase.PARSE,
            termination.phaseAtHostStopSelection,
        )
        assertSame(
            R2ProjectExecutionPhase.PARSE,
            termination.phaseAtPageTermination,
        )
        assertEquals(
            listOf(
                R2ProjectExecutionPhase.INGRESS,
                R2ProjectExecutionPhase.PARSE,
            ),
            termination.enteredPhasesAtHostStopSelection,
        )
        assertEquals(
            listOf(R2ProjectExecutionPhase.INGRESS),
            termination.completedPhasesAtHostStopSelection,
        )
        assertEquals(2, termination.phaseProgressSequenceAtPageTermination)
        assertEquals(
            8,
            termination.phaseProgressCompletedUnitsAtPageTermination,
        )
        assertEquals(
            32,
            termination.phaseProgressTotalUnitsAtPageTermination,
        )
        assertTrue(termination.terminatedWorkerGeneration > 0)
        assertEquals(
            termination.workerGeneration,
            termination.terminatedWorkerGeneration,
        )
        assertEquals(
            termination.terminatedWorkerGeneration + 1,
            termination.replacementWorkerGeneration,
        )

        val input = decoded.inputTelemetry
        assertEquals(1_598, input.sentFrameCount)
        assertEquals(8_177_717L, input.acceptedPayloadBytes)
        assertEquals(8_177_717L, input.totalPayloadBytes)
        assertEquals(512, input.openedCursorCount)
        assertEquals(512, input.closedCursorCount)
        assertFalse(input.activeCursor)
        assertTrue(input.released)
    }

    @Test
    fun malformedParseBundleWithCompletedParseTimingFailsClosed() {
        val values = FakeValues()
        R2ExternalNaturalPhaseTimeoutEvidenceState(
            parseTimeoutReceipt(),
        ).putInto(values)
        values.putStringArray(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol.KEY_PHASE_VALUES,
            arrayOf(R2ProjectExecutionPhase.PARSE.protocolValue),
        )
        values.putLongArray(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                .KEY_PHASE_SEQUENCE_TWO_NANOS_SINCE_REQUEST_START,
            longArrayOf(1_100L),
        )
        values.putLongArray(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                .KEY_PHASE_COMPLETED_NANOS_SINCE_REQUEST_START,
            longArrayOf(1_300L),
        )

        assertNull(
            R2ExternalNaturalPhaseTimeoutEvidenceState.fromValues(values),
        )
    }

    @Test
    fun mutableCallerListsCannotChangeAnImmutableReceipt() {
        val timings = mutableListOf(
            timing(R2ProjectExecutionPhase.PARSE, 1_100L, 1_300L),
            timing(R2ProjectExecutionPhase.SEMANTIC, 1_500L, 1_900L),
            timing(R2ProjectExecutionPhase.EMIT, 6_100L, 7_400L),
        )
        val entered = mutableListOf(
            R2ProjectExecutionPhase.INGRESS,
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        )
        val termination = termination(entered)
        val success = successReceipt(timings)

        timings.clear()
        entered.clear()

        assertEquals(3, success.phaseTimings.size)
        assertEquals(
            4,
            termination.enteredPhasesAtHostStopSelection.size,
        )
    }

    @Test
    fun absentUnavailableAndMalformedBundlesFailClosed() {
        assertNull(
            R2ExternalNaturalPhaseTimeoutEvidenceState.fromValues(
                FakeValues(),
            ),
        )

        val enabledWithoutReceipt = FakeValues().apply {
            putBoolean(
                R2ExternalNaturalPhaseTimeoutEvidenceProtocol.KEY_ENABLED,
                true,
            )
            putBoolean(
                R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                    .KEY_RECEIPT_AVAILABLE,
                false,
            )
        }
        val unavailable =
            R2ExternalNaturalPhaseTimeoutEvidenceState.fromValues(
                enabledWithoutReceipt,
            )
        assertNotNull(unavailable)
        assertNull(unavailable!!.receipt)

        val malformed = FakeValues()
        R2ExternalNaturalPhaseTimeoutEvidenceState(timeoutReceipt())
            .putInto(malformed)
        malformed.putLong(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                .KEY_TERMINATION_DEADLINE_OVERSHOOT_NANOS,
            74L,
        )
        assertNull(
            R2ExternalNaturalPhaseTimeoutEvidenceState.fromValues(
                malformed,
            ),
        )

        val wrongType = FakeValues()
        R2ExternalNaturalPhaseTimeoutEvidenceState(timeoutReceipt())
            .putInto(wrongType)
        wrongType.putString(
            R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                .KEY_INPUT_RELEASED,
            "true",
        )
        assertNull(
            R2ExternalNaturalPhaseTimeoutEvidenceState.fromValues(
                wrongType,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiptRejectsCrossRequestInputTelemetry() {
        R2ExternalNaturalPhaseTimeoutReceipt(
            requestId = "binder-request",
            hostRequestId = "host-request",
            outcome = R2ExternalNaturalPhaseTimeoutOutcome.SUCCESS,
            createdBeforeCallback = true,
            projectStartedElapsedRealtimeNanosSinceRequestStart = 1_000L,
            phaseTimings =
                listOf(
                    timing(
                        R2ProjectExecutionPhase.PARSE,
                        1_100L,
                        1_300L,
                    ),
                    timing(
                        R2ProjectExecutionPhase.SEMANTIC,
                        1_500L,
                        1_900L,
                    ),
                    timing(
                        R2ProjectExecutionPhase.EMIT,
                        6_100L,
                        7_400L,
                    ),
                ),
            termination = null,
            inputTelemetry = inputTelemetry("another-host-request"),
        )
    }

    @Test
    fun stateAlwaysMarksThePrivateChannelAndAvailability() {
        val values = FakeValues()

        R2ExternalNaturalPhaseTimeoutEvidenceState(receipt = null)
            .putInto(values)

        assertTrue(
            values.boolean(
                R2ExternalNaturalPhaseTimeoutEvidenceProtocol.KEY_ENABLED,
            ) == true,
        )
        assertFalse(
            values.boolean(
                R2ExternalNaturalPhaseTimeoutEvidenceProtocol
                    .KEY_RECEIPT_AVAILABLE,
            ) == true,
        )
    }

    private fun successReceipt(
        timings: List<R2ExternalNaturalPhaseTimingReceipt>,
    ) = R2ExternalNaturalPhaseTimeoutReceipt(
        requestId = "binder-request",
        hostRequestId = "host-request",
        outcome = R2ExternalNaturalPhaseTimeoutOutcome.SUCCESS,
        createdBeforeCallback = true,
        projectStartedElapsedRealtimeNanosSinceRequestStart = 1_000L,
        phaseTimings = timings,
        termination = null,
        inputTelemetry = inputTelemetry(),
    )

    private fun timeoutReceipt() =
        R2ExternalNaturalPhaseTimeoutReceipt(
            requestId = "binder-request",
            hostRequestId = "host-request",
            outcome = R2ExternalNaturalPhaseTimeoutOutcome.TIMED_OUT,
            createdBeforeCallback = true,
            projectStartedElapsedRealtimeNanosSinceRequestStart = 1_000L,
            phaseTimings =
                listOf(
                    timing(
                        R2ProjectExecutionPhase.PARSE,
                        1_100L,
                        1_300L,
                    ),
                    timing(
                        R2ProjectExecutionPhase.SEMANTIC,
                        1_500L,
                        1_900L,
                    ),
                ),
            termination =
                termination(
                    listOf(
                        R2ProjectExecutionPhase.INGRESS,
                        R2ProjectExecutionPhase.PARSE,
                        R2ProjectExecutionPhase.SEMANTIC,
                        R2ProjectExecutionPhase.EMIT,
                    ),
                ),
            inputTelemetry = inputTelemetry(),
        )

    private fun semanticTimeoutReceipt() =
        R2ExternalNaturalPhaseTimeoutReceipt(
            requestId = "binder-request",
            hostRequestId = "host-request",
            outcome = R2ExternalNaturalPhaseTimeoutOutcome.TIMED_OUT,
            createdBeforeCallback = true,
            projectStartedElapsedRealtimeNanosSinceRequestStart = 1_000L,
            phaseTimings =
                listOf(
                    timing(
                        R2ProjectExecutionPhase.PARSE,
                        1_100L,
                        1_300L,
                    ),
                ),
            termination = semanticTermination(),
            inputTelemetry = semanticInputTelemetry(),
        )

    private fun parseTimeoutReceipt() =
        R2ExternalNaturalPhaseTimeoutReceipt(
            requestId = "binder-request",
            hostRequestId = "host-request",
            outcome = R2ExternalNaturalPhaseTimeoutOutcome.TIMED_OUT,
            createdBeforeCallback = true,
            projectStartedElapsedRealtimeNanosSinceRequestStart = 1_000L,
            phaseTimings = emptyList(),
            termination = parseTermination(),
            inputTelemetry = parseInputTelemetry(),
        )

    private fun semanticTermination() =
        R2ExternalNaturalPhaseTerminationReceipt(
            hostRequestId = "host-request",
            stopCause = R2ProjectStopCause.TIMED_OUT,
            phaseAtHostStopSelection = R2ProjectExecutionPhase.SEMANTIC,
            enteredPhasesAtHostStopSelection =
                listOf(
                    R2ProjectExecutionPhase.INGRESS,
                    R2ProjectExecutionPhase.PARSE,
                    R2ProjectExecutionPhase.SEMANTIC,
                ),
            completedPhasesAtHostStopSelection =
                listOf(
                    R2ProjectExecutionPhase.INGRESS,
                    R2ProjectExecutionPhase.PARSE,
                ),
            phaseAtPageTermination = R2ProjectExecutionPhase.SEMANTIC,
            deadlineElapsedRealtimeNanosSinceRequestStart = 9_500L,
            stopSelectedElapsedRealtimeNanosSinceRequestStart = 9_575L,
            deadlineSelectionOvershootNanos = 75L,
            sessionGeneration = 3,
            workerGeneration = 7,
            phaseProgressSequenceAtPageTermination = 2,
            phaseProgressCompletedUnitsAtPageTermination = 64,
            phaseProgressTotalUnitsAtPageTermination = 256,
            terminatedWorkerGeneration = 7,
            replacementWorkerGeneration = 8,
        )

    private fun parseTermination() =
        R2ExternalNaturalPhaseTerminationReceipt(
            hostRequestId = "host-request",
            stopCause = R2ProjectStopCause.TIMED_OUT,
            phaseAtHostStopSelection = R2ProjectExecutionPhase.PARSE,
            enteredPhasesAtHostStopSelection =
                listOf(
                    R2ProjectExecutionPhase.INGRESS,
                    R2ProjectExecutionPhase.PARSE,
                ),
            completedPhasesAtHostStopSelection =
                listOf(R2ProjectExecutionPhase.INGRESS),
            phaseAtPageTermination = R2ProjectExecutionPhase.PARSE,
            deadlineElapsedRealtimeNanosSinceRequestStart = 9_500L,
            stopSelectedElapsedRealtimeNanosSinceRequestStart = 9_575L,
            deadlineSelectionOvershootNanos = 75L,
            sessionGeneration = 3,
            workerGeneration = 7,
            phaseProgressSequenceAtPageTermination = 2,
            phaseProgressCompletedUnitsAtPageTermination = 8,
            phaseProgressTotalUnitsAtPageTermination = 32,
            terminatedWorkerGeneration = 7,
            replacementWorkerGeneration = 8,
        )

    private fun termination(
        enteredPhases: List<R2ProjectExecutionPhase>,
    ) = R2ExternalNaturalPhaseTerminationReceipt(
        hostRequestId = "host-request",
        stopCause = R2ProjectStopCause.TIMED_OUT,
        phaseAtHostStopSelection = R2ProjectExecutionPhase.EMIT,
        enteredPhasesAtHostStopSelection = enteredPhases,
        completedPhasesAtHostStopSelection =
            listOf(
                R2ProjectExecutionPhase.INGRESS,
                R2ProjectExecutionPhase.PARSE,
                R2ProjectExecutionPhase.SEMANTIC,
            ),
        phaseAtPageTermination = R2ProjectExecutionPhase.EMIT,
        deadlineElapsedRealtimeNanosSinceRequestStart = 6_000L,
        stopSelectedElapsedRealtimeNanosSinceRequestStart = 6_075L,
        deadlineSelectionOvershootNanos = 75L,
        sessionGeneration = 3,
        workerGeneration = 7,
        phaseProgressSequenceAtPageTermination = 2,
        phaseProgressCompletedUnitsAtPageTermination = 4,
        phaseProgressTotalUnitsAtPageTermination = 16,
        terminatedWorkerGeneration = 7,
        replacementWorkerGeneration = 8,
    )

    private fun timing(
        phase: R2ProjectExecutionPhase,
        sequenceTwo: Long,
        completed: Long,
    ) = R2ExternalNaturalPhaseTimingReceipt(
        phase = phase,
        sequenceTwoElapsedRealtimeNanosSinceRequestStart = sequenceTwo,
        completedElapsedRealtimeNanosSinceRequestStart = completed,
    )

    private fun inputTelemetry(
        hostRequestId: String = "host-request",
    ) = R2ExternalNaturalPhaseInputTelemetry(
        hostRequestId = hostRequestId,
        sentFrameCount = 400,
        maximumEncodedFrameBytes = 32_768,
        acceptedPayloadBytes = 3_841_832L,
        totalPayloadBytes = 3_841_832L,
        fileIndex = 116,
        fileOffset = 57_074L,
        stage = "COMPLETE",
        complete = true,
        openedCursorCount = 116,
        closedCursorCount = 116,
        activeCursor = false,
        released = true,
    )

    private fun semanticInputTelemetry() =
        R2ExternalNaturalPhaseInputTelemetry(
            hostRequestId = "host-request",
            sentFrameCount = 1_588,
            maximumEncodedFrameBytes = 65_536,
            acceptedPayloadBytes = 7_726_972L,
            totalPayloadBytes = 7_726_972L,
            fileIndex = 512,
            fileOffset = 57_074L,
            stage = "COMPLETE",
            complete = true,
            openedCursorCount = 512,
            closedCursorCount = 512,
            activeCursor = false,
            released = true,
        )

    private fun parseInputTelemetry() =
        R2ExternalNaturalPhaseInputTelemetry(
            hostRequestId = "host-request",
            sentFrameCount = 1_598,
            maximumEncodedFrameBytes = 65_536,
            acceptedPayloadBytes = 8_177_717L,
            totalPayloadBytes = 8_177_717L,
            fileIndex = 512,
            fileOffset = 57_074L,
            stage = "COMPLETE",
            complete = true,
            openedCursorCount = 512,
            closedCursorCount = 512,
            activeCursor = false,
            released = true,
        )

    private class FakeValues :
        R2ExternalNaturalPhaseTimeoutEvidenceValues {

        private val values = mutableMapOf<String, Any>()

        override fun boolean(key: String): Boolean? =
            values[key] as? Boolean

        override fun int(key: String): Int? = values[key] as? Int

        override fun long(key: String): Long? = values[key] as? Long

        override fun string(key: String): String? =
            values[key] as? String

        override fun stringArray(key: String): Array<String>? {
            val value = values[key]
            if (value !is Array<*>) return null
            if (value.any { element -> element !is String }) return null
            @Suppress("UNCHECKED_CAST")
            return (value as Array<String>).copyOf()
        }

        override fun longArray(key: String): LongArray? =
            (values[key] as? LongArray)?.copyOf()

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun putLong(key: String, value: Long) {
            values[key] = value
        }

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun putStringArray(
            key: String,
            value: Array<String>,
        ) {
            values[key] = value.copyOf()
        }

        override fun putLongArray(key: String, value: LongArray) {
            values[key] = value.copyOf()
        }
    }
}
