package io.github.supermonster003.autojs6.plugin.typescript

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2AbsoluteDeadlineRegressionInstrumentationTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    /**
     * An interrupt is not a project deadline.
     *
     * This uses the existing no-dispatch lifecycle fixture so the compile thread owns a real
     * pending project and absolute deadline without depending on Worker or renderer timing.
     * Interrupting that thread must neither publish an early timeout nor lose its interrupt flag.
     */
    @Test
    fun interruptedDeadlineWaitDoesNotTimeoutEarlyAndRestoresInterruptStatus() {
        val snapshot = R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(
                    path = "/declarations/lib.d.ts",
                    text = "declare interface Array<T> { readonly length: number }\n",
                ),
                R2ProjectTextFile(
                    path = "/sources/main.ts",
                    text = "export const value = 1;\n",
                ),
            ),
            rootNames = listOf("/sources/main.ts"),
        )
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        val returned = CountDownLatch(1)
        val outcome = AtomicReference<R2ProjectCompilationOutcome?>()
        val interruptRestored = AtomicBoolean(false)
        val compilerThread = Thread(
            {
                try {
                    outcome.set(
                        host.holdProjectUntilCloseForTesting(
                            snapshot = snapshot,
                            timeoutMs = ABSOLUTE_DEADLINE_MILLIS,
                        ),
                    )
                    interruptRestored.set(Thread.currentThread().isInterrupted)
                } finally {
                    returned.countDown()
                }
            },
            "r2-absolute-deadline-interrupt-regression",
        )

        try {
            compilerThread.start()
            awaitPendingProject(host)
            compilerThread.interrupt()

            assertFalse(
                "Interrupting the compile thread published timeout before its absolute deadline.",
                returned.await(EARLY_RETURN_OBSERVATION_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertTrue(
                "The interrupted project did not complete at its absolute deadline.",
                returned.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            val timedOut = requireNotNull(outcome.get())
            assertFalse(timedOut.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_TIMEOUT,
                timedOut.errorCode,
            )
            assertNull(timedOut.compilation)
            assertTrue(
                "The absolute deadline returned before its requested timeout.",
                timedOut.elapsedMs >= ABSOLUTE_DEADLINE_MILLIS,
            )
            assertTrue(
                "The absolute deadline wait did not restore the compile thread interrupt flag.",
                interruptRestored.get(),
            )
            assertFalse(host.hasPendingProbeForTesting())
            assertNull(host.lastProjectPhaseTerminationEvidenceForTesting())
        } finally {
            if (compilerThread.isAlive) {
                compilerThread.interrupt()
                returned.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            onMain {
                host.close()
            }
            snapshot.close()
        }
    }

    private fun awaitPendingProject(host: TypeScriptWebViewCompilerHost) {
        val deadlineAt =
            SystemClock.elapsedRealtime() + PENDING_LOOKUP_TIMEOUT_MILLIS
        while (
            !host.hasPendingProbeForTesting() &&
            SystemClock.elapsedRealtime() < deadlineAt
        ) {
            SystemClock.sleep(PENDING_LOOKUP_INTERVAL_MILLIS)
        }
        assertTrue(
            "The no-dispatch project did not register its pending absolute deadline.",
            host.hasPendingProbeForTesting(),
        )
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>?>()
        instrumentation.runOnMainSync {
            result.set(runCatching(block))
        }
        return requireNotNull(result.get()).getOrThrow()
    }

    private companion object {
        const val ABSOLUTE_DEADLINE_MILLIS = 1_500L
        const val EARLY_RETURN_OBSERVATION_MILLIS = 250L
        const val PENDING_LOOKUP_INTERVAL_MILLIS = 5L
        const val PENDING_LOOKUP_TIMEOUT_MILLIS = 5_000L
        const val TEST_TIMEOUT_SECONDS = 10L
    }
}
