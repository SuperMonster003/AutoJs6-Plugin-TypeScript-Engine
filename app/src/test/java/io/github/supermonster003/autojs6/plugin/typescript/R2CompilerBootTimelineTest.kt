package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CompilerBootTimelineTest {

    @Test
    fun publishesOneBootLineOnTheFirstWorkerReady() {
        var uptime = 40L
        val timeline = R2CompilerBootTimeline { uptime }

        timeline.recordServiceCreateStarted()
        uptime = 55L
        timeline.recordDeclarationLoadMs(210L)
        uptime = 300L
        timeline.recordServiceCreateCompleted()
        uptime = 320L
        assertNull(timeline.recordSupervisorReady())
        uptime = 350L
        timeline.recordWebViewCreated()
        uptime = 700L
        timeline.recordPageReady()
        uptime = 1_500L
        val line = timeline.recordWorkerReady(bootstrapMs = 640L)

        assertEquals(
            "typescript-compiler-boot schema=1 processUptimeMs=1500 serviceCreateStartMs=40 " +
                "serviceCreateMs=260 declarationLoadMs=210 supervisorReadyAtMs=320 " +
                "webViewCreatedAtMs=350 pageReadyAtMs=700 workerReadyAtMs=1500 " +
                "workerBootstrapMs=640",
            line,
        )

        uptime = 9_000L
        assertNull(timeline.recordWorkerReady(bootstrapMs = 12L))
        val snapshot = timeline.snapshot()
        assertEquals(1_500L, snapshot.workerReadyAtMs)
        assertEquals(640L, snapshot.workerBootstrapMs)
    }

    @Test
    fun reportsLateSupervisorRegistrationAsAFollowUpLine() {
        var uptime = 100L
        val timeline = R2CompilerBootTimeline { uptime }

        val bootLine = timeline.recordWorkerReady(bootstrapMs = null)
        assertTrue(requireNotNull(bootLine).contains("supervisorReadyAtMs=na"))
        assertTrue(bootLine.contains("workerBootstrapMs=na"))

        uptime = 2_400L
        assertEquals(
            "typescript-compiler-boot schema=1 event=supervisorReady supervisorReadyAtMs=2400",
            timeline.recordSupervisorReady(),
        )
        assertNull(timeline.recordSupervisorReady())
    }

    @Test
    fun formatsWarmUpReportsAgainstTheProcessTimeline() {
        var uptime = 1_500L
        val timeline = R2CompilerBootTimeline { uptime }
        assertNotNull(timeline.recordWorkerReady(bootstrapMs = 640L))
        uptime = 2_100L
        val fields = linkedMapOf<String, Long?>(
            "sinceReadyMs" to 540L,
            "startedAtMs" to 1L,
            "loadedAtMs" to 330L,
            "completedAtMs" to null,
            "commonImportMs" to 90L,
            "commonVerifyMs" to 200L,
            "commonDigestMs" to 60L,
            "commonTokenMs" to 130L,
            "rhinoImportMs" to 20L,
            "rhinoVerifyMs" to 40L,
            "rhinoDigestMs" to 12L,
            "rhinoTokenMs" to 25L,
            "parsedFiles" to 31L,
            "parsedBytes" to 812_345L,
            "queuedFiles" to 52L,
            "parseMs" to 180L,
            "steps" to 16L,
            "deferredSteps" to 0L,
        )

        assertEquals(
            "typescript-compiler-warmup schema=1 processUptimeMs=2100 workerReadyAtMs=1500 " +
                "generation=1 state=parsing sinceReadyMs=540 startedAtMs=1 loadedAtMs=330 " +
                "completedAtMs=na commonImportMs=90 commonVerifyMs=200 commonDigestMs=60 " +
                "commonTokenMs=130 rhinoImportMs=20 rhinoVerifyMs=40 rhinoDigestMs=12 " +
                "rhinoTokenMs=25 parsedFiles=31 parsedBytes=812345 queuedFiles=52 " +
                "parseMs=180 steps=16 deferredSteps=0",
            timeline.warmUpLogLine(generation = 1, state = "parsing", fields = fields),
        )
        // Fields the Worker did not send are still printed, as `na`, before any Worker ready.
        val early = R2CompilerBootTimeline { 300L }
        val line = early.warmUpLogLine(generation = 2, state = "na", fields = emptyMap())
        assertEquals(
            "typescript-compiler-warmup schema=1 processUptimeMs=300 workerReadyAtMs=na " +
                "generation=2 state=na sinceReadyMs=na startedAtMs=na loadedAtMs=na " +
                "completedAtMs=na commonImportMs=na commonVerifyMs=na commonDigestMs=na " +
                "commonTokenMs=na rhinoImportMs=na rhinoVerifyMs=na rhinoDigestMs=na " +
                "rhinoTokenMs=na parsedFiles=na parsedBytes=na queuedFiles=na parseMs=na " +
                "steps=na deferredSteps=na",
            line,
        )
    }

    @Test
    fun keepsOnlyTheFirstObservationAndNeverGoesNegative() {
        var uptime = 10L
        val timeline = R2CompilerBootTimeline { uptime }

        timeline.recordServiceCreateCompleted()
        timeline.recordDeclarationLoadMs(-5L)
        timeline.recordDeclarationLoadMs(7L)
        uptime = 5L
        timeline.recordServiceCreateStarted()
        timeline.recordServiceCreateCompleted()
        uptime = 1L
        timeline.recordServiceCreateCompleted()
        timeline.recordWebViewCreated()
        uptime = 2L
        timeline.recordWebViewCreated()

        val snapshot = timeline.snapshot()
        assertEquals(5L, snapshot.serviceCreateStartedAtMs)
        assertEquals(0L, snapshot.serviceCreateMs)
        assertEquals(0L, snapshot.declarationLoadMs)
        assertEquals(1L, snapshot.webViewCreatedAtMs)
        assertNull(snapshot.workerReadyAtMs)
        assertFalse(snapshot.toLogLine().contains("requestId"))
    }
}
