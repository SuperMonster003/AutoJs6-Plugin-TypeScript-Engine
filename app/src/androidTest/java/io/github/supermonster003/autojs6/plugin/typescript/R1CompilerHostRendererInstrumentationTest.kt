package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1CompilerHostRendererInstrumentationTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun consecutiveTimeoutsBeforeRecoveryDoNotDestroyTheNextQueuedProbe() {
        withCompilerHost(requireRendererTerminationSupport = false) { host ->
            val mainBlockEntered = CountDownLatch(1)
            val releaseMain = CountDownLatch(1)
            assertTrue(
                Handler(Looper.getMainLooper()).post {
                    mainBlockEntered.countDown()
                    releaseMain.await(
                        MAIN_THREAD_BLOCK_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                    )
                },
            )
            assertTrue(
                mainBlockEntered.await(
                    MAIN_THREAD_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )

            val queuedProbeExecutor = Executors.newSingleThreadExecutor()
            try {
                repeat(2) {
                    val timedOut = host.probe(CONSECUTIVE_TIMEOUT_MILLIS)
                    assertFalse(timedOut.succeeded)
                    assertEquals(
                        TypeScriptCompilerContract.ERROR_TIMEOUT,
                        timedOut.errorCode,
                    )
                }

                val queuedProbe = queuedProbeExecutor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                )
                awaitPendingProbe(host)
                releaseMain.countDown()

                val recovered = queuedProbe.get(
                    TEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
                assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.STATUS_READY,
                    host.snapshot().status,
                )
            } finally {
                releaseMain.countDown()
                queuedProbeExecutor.shutdownNow()
            }
        }
    }

    @Test
    fun oneRendererDeathRebuildsTheHostAndRetriesThePendingProbe() {
        withCompilerHost { host ->
            onMain {
                host.armRendererTerminationsForTesting(1)
            }

            val outcome = host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)

            assertTrue(outcome.errorMessage.orEmpty(), outcome.succeeded)
            assertEquals(
                TypeScriptCompilerContract.STATUS_READY,
                host.snapshot().status,
            )
            assertEquals(
                0,
                onMain(host::remainingRendererTerminationsForTesting),
            )
        }
    }

    @Test
    fun secondRendererDeathFailsTheProbeWithoutASecondRetryAndTheHostRecovers() {
        withCompilerHost { host ->
            onMain {
                host.armRendererTerminationsForTesting(2)
            }

            val failed = host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)

            assertFalse(failed.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                failed.errorCode,
            )
            assertEquals(
                0,
                onMain(host::remainingRendererTerminationsForTesting),
            )

            val recovered = host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)

            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
            assertEquals(
                TypeScriptCompilerContract.STATUS_READY,
                host.snapshot().status,
            )
        }
    }

    private fun assumeRendererTerminationSupport(host: TypeScriptWebViewCompilerHost) {
        assumeTrue(
            "The installed WebView provider cannot terminate its renderer.",
            onMain(host::isRendererTerminationSupportedForTesting),
        )
    }

    private fun awaitPendingProbe(host: TypeScriptWebViewCompilerHost) {
        repeat(PENDING_PROBE_LOOKUP_ATTEMPTS) {
            if (host.hasPendingProbeForTesting()) return
            Thread.sleep(PENDING_PROBE_LOOKUP_INTERVAL_MILLIS)
        }
        throw AssertionError("The queued compiler probe did not become pending.")
    }

    private fun withCompilerHost(
        requireRendererTerminationSupport: Boolean = true,
        block: (TypeScriptWebViewCompilerHost) -> Unit,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        try {
            if (requireRendererTerminationSupport) {
                assumeRendererTerminationSupport(host)
            }
            executor.submit(Callable { block(host) })
                .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            onMain {
                host.close()
            }
            executor.shutdownNow()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(Callable(block))
        instrumentation.runOnMainSync(task)
        return task.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val CONSECUTIVE_TIMEOUT_MILLIS = 1L
        const val MAIN_THREAD_BLOCK_TIMEOUT_SECONDS = 10L
        const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
        const val PENDING_PROBE_LOOKUP_ATTEMPTS = 100
        const val PENDING_PROBE_LOOKUP_INTERVAL_MILLIS = 10L
        const val TEST_TIMEOUT_SECONDS = 90L
    }
}
