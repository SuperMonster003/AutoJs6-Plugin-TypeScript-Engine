package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompilerHostCloseReportTest {

    @Test
    fun completeResourceReleaseIsCleanupProof() {
        assertTrue(completeReport().cleanupProven)
    }

    @Test
    fun everyResourceReleaseBoundaryIsRequired() {
        val report = completeReport()
        val incomplete = listOf(
            report.copy(nativePortClosed = false),
            report.copy(webViewDestroyed = false),
            report.copy(serviceWorkerClientCleared = false),
            report.copy(handlerCallbacksCleared = false),
            report.copy(closed = false),
            report.copy(pendingProbeReleased = false),
            report.copy(webViewReleased = false),
            report.copy(nativePortReleased = false),
            report.copy(workerStateReset = false),
            report.copy(sessionStateReset = false),
            report.copy(timeoutRecoveryReset = false),
            report.copy(protocolExecutorTerminated = false),
            report.copy(resultExecutorTerminated = false),
            report.copy(resultFinalizersReleased = false),
        )

        incomplete.forEach { candidate ->
            assertFalse(candidate.cleanupProven)
        }
    }

    private fun completeReport() = CompilerHostCloseReport(
        hadPendingProbe = true,
        hadWebView = true,
        hadNativePort = true,
        hadReadyWorker = true,
        hadServiceWorkerClient = true,
        closeMessagePosted = true,
        nativePortClosed = true,
        webViewDestroyed = true,
        serviceWorkerClientCleared = true,
        handlerCallbacksCleared = true,
        closed = true,
        pendingProbeReleased = true,
        webViewReleased = true,
        nativePortReleased = true,
        workerStateReset = true,
        sessionStateReset = true,
        timeoutRecoveryReset = true,
        protocolExecutorTerminated = true,
        resultExecutorTerminated = true,
        resultFinalizersReleased = true,
    )
}
