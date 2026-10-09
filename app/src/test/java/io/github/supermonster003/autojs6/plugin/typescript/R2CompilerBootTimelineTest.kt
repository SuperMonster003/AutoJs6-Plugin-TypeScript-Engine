package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
