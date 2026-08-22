package io.github.supermonster003.autojs6.plugin.typescript

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1ServiceDestructionInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun lastUnbindReleasesMatureHostAndTwoSubsequentBindsProbeSuccessfully() {
        val initialSession = bindCompilerService()
        val executor = Executors.newSingleThreadExecutor()
        val heldCallStarted = CountDownLatch(1)
        var initialSessionClosed = false
        try {
            assertSuccessfulProbe(
                initialSession.compiler.prewarm(validPrewarmOptions()),
            )

            val heldResult = executor.submit<Bundle> {
                heldCallStarted.countDown()
                initialSession.compiler.prewarm(heldPrewarmOptions())
            }
            assertTrue(
                "Held prewarm did not enter its Binder calling thread.",
                heldCallStarted.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )

            val maturePendingTelemetry =
                awaitMaturePendingTelemetry(initialSession.compiler)
            assertLiveMaturePendingTelemetry(maturePendingTelemetry)

            val busy = initialSession.compiler.prewarm(validPrewarmOptions())
            assertEquals(
                TypeScriptCompilerContract.ERROR_BUSY,
                busy.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(
                "Compiler already has an active operation.",
                busy.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE),
            )

            // This is the only ServiceConnection. The active Binder transaction does not count
            // as a binding, so this real last unbind must drive Service.onDestroy().
            initialSession.close()
            initialSessionClosed = true

            val destroyed = heldResult.get(
                DESTRUCTION_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            assertDestroyedOutcomeAndCloseReport(destroyed)
        } finally {
            if (!initialSessionClosed) {
                initialSession.close()
            }
            executor.shutdownNow()
        }

        repeat(REBIND_PROBE_COUNT) {
            bindCompilerService().use { rebound ->
                assertSuccessfulProbe(
                    rebound.compiler.prewarm(validPrewarmOptions()),
                )
            }
        }
    }

    private fun awaitMaturePendingTelemetry(
        compiler: ITypeScriptCompilerPlugin,
    ): Bundle {
        val deadline = SystemClock.elapsedRealtime() + TELEMETRY_TIMEOUT_MILLIS
        var latest = Bundle()
        while (SystemClock.elapsedRealtime() < deadline) {
            latest = compiler.compilerInfo
            if (
                latest.getBoolean(
                    CompilerServiceLifecycleTestProtocol.KEY_LIVE_TELEMETRY_ENABLED,
                    false,
                ) &&
                !latest.getBoolean(
                    CompilerServiceLifecycleTestProtocol.KEY_LIVE_CLOSED,
                    true,
                ) &&
                latest.getBoolean(
                    CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_WEB_VIEW,
                    false,
                ) &&
                latest.getBoolean(
                    CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_NATIVE_PORT,
                    false,
                ) &&
                latest.getBoolean(
                    CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_PENDING_PROBE,
                    false,
                ) &&
                latest.getBoolean(
                    CompilerServiceLifecycleTestProtocol.KEY_LIVE_WORKER_READY,
                    false,
                )
            ) {
                return latest
            }
            SystemClock.sleep(TELEMETRY_POLL_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "Timed out waiting for mature pending compiler telemetry: $latest",
        )
    }

    private fun assertLiveMaturePendingTelemetry(telemetry: Bundle) {
        assertTrue(
            telemetry.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_TELEMETRY_ENABLED,
                false,
            ),
        )
        assertFalse(
            telemetry.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_CLOSED,
                true,
            ),
        )
        assertTrue(
            telemetry.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_WEB_VIEW,
                false,
            ),
        )
        assertTrue(
            telemetry.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_NATIVE_PORT,
                false,
            ),
        )
        assertTrue(
            telemetry.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_PENDING_PROBE,
                false,
            ),
        )
        assertTrue(
            telemetry.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_WORKER_READY,
                false,
            ),
        )
        assertFalse(
            telemetry.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_CLOSE_MESSAGE_POSTED,
                true,
            ),
        )
    }

    private fun assertDestroyedOutcomeAndCloseReport(result: Bundle) {
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            result.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
        )
        assertEquals(
            "Compiler service was destroyed.",
            result.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE),
        )
        assertEquals(
            TypeScriptCompilerContract.STATUS_FAILED,
            result.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
        )
        REQUIRED_TRUE_CLOSE_REPORT_KEYS.forEach { key ->
            assertTrue(
                "Expected true lifecycle close report key: $key; result=$result",
                result.getBoolean(key, false),
            )
        }
        assertTrue(
            result.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_TELEMETRY_ENABLED,
                false,
            ),
        )
        assertTrue(
            result.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_CLOSED,
                false,
            ),
        )
        assertFalse(
            result.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_WEB_VIEW,
                true,
            ),
        )
        assertFalse(
            result.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_NATIVE_PORT,
                true,
            ),
        )
        assertFalse(
            result.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_HAS_PENDING_PROBE,
                true,
            ),
        )
        assertFalse(
            result.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_WORKER_READY,
                true,
            ),
        )
        assertTrue(
            result.getBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_LIVE_CLOSE_MESSAGE_POSTED,
                false,
            ),
        )
    }

    private fun assertSuccessfulProbe(result: Bundle) {
        assertFalse(
            result.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE).orEmpty(),
            result.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE),
        )
        assertEquals(
            TypeScriptCompilerContract.STATUS_READY,
            result.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
        )
    }

    private fun heldPrewarmOptions() =
        validPrewarmOptions(HELD_PROBE_TIMEOUT_MILLIS).apply {
            putBoolean(
                CompilerServiceLifecycleTestProtocol.KEY_HOLD_UNTIL_SERVICE_DESTROYED,
                true,
            )
        }

    private fun validPrewarmOptions(
        timeoutMs: Long = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
    ) = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, timeoutMs)
    }

    @Suppress("DEPRECATION")
    private fun bindCompilerService(): BoundCompilerSession {
        val discoveryIntent = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(discoveryIntent, 0)
        assertEquals("Expected exactly one TypeScript compiler service.", 1, matches.size)
        val serviceInfo = matches.single().serviceInfo
        assertNotNull(serviceInfo)
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val connection = AwaitingServiceConnection()
        val bound = context.bindService(
            Intent(discoveryIntent).setComponent(component),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("Failed to bind $component.", bound)
        return try {
            val rawBinder = connection.awaitBinder()
            assertNull(
                "The compiler Binder must remain a remote proxy.",
                rawBinder.queryLocalInterface(ITypeScriptCompilerPlugin.DESCRIPTOR),
            )
            val compiler = ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder)
            assertNotNull(compiler)
            BoundCompilerSession(
                context = context,
                connection = connection,
                compiler = compiler,
            )
        } catch (error: Throwable) {
            context.unbindService(connection)
            throw error
        }
    }

    private class AwaitingServiceConnection : ServiceConnection {

        private val connected = CountDownLatch(1)

        @Volatile
        private var binder: IBinder? = null

        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder,
        ) {
            binder = service
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) {
            connected.countDown()
        }

        fun awaitBinder(): IBinder {
            assertTrue(
                "Timed out waiting for the compiler service.",
                connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(binder) {
                "Compiler service returned a null binding."
            }
        }
    }

    private class BoundCompilerSession(
        private val context: Context,
        private val connection: ServiceConnection,
        val compiler: ITypeScriptCompilerPlugin,
    ) : AutoCloseable {

        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            context.unbindService(connection)
        }
    }

    private companion object {
        const val SERVICE_TIMEOUT_SECONDS = 5L
        const val DESTRUCTION_TIMEOUT_SECONDS = 15L
        const val HELD_PROBE_TIMEOUT_MILLIS = 20_000L
        const val TELEMETRY_TIMEOUT_MILLIS = 5_000L
        const val TELEMETRY_POLL_INTERVAL_MILLIS = 25L
        const val REBIND_PROBE_COUNT = 2

        val REQUIRED_TRUE_CLOSE_REPORT_KEYS = listOf(
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_REPORT_AVAILABLE,
            CompilerServiceLifecycleTestProtocol.KEY_SERVICE_ON_DESTROY_ENTERED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_PENDING_PROBE,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_WEB_VIEW,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_NATIVE_PORT,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_READY_WORKER,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HAD_SERVICE_WORKER_CLIENT,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_MESSAGE_POSTED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_NATIVE_PORT_CLOSED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_WEB_VIEW_DESTROYED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_SERVICE_WORKER_CLIENT_CLEARED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_HANDLER_CALLBACKS_CLEARED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_CLOSED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_PENDING_PROBE_RELEASED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_WEB_VIEW_RELEASED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_NATIVE_PORT_RELEASED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_WORKER_STATE_RESET,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_SESSION_STATE_RESET,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_TIMEOUT_RECOVERY_RESET,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_PROTOCOL_EXECUTOR_TERMINATED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_RESULT_EXECUTOR_TERMINATED,
            CompilerServiceLifecycleTestProtocol.KEY_CLOSE_RESULT_FINALIZERS_RELEASED,
        )
    }
}
