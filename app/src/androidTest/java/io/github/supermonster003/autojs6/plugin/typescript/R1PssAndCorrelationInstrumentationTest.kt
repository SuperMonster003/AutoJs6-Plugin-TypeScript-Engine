package io.github.supermonster003.autojs6.plugin.typescript

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import java.util.concurrent.CountDownLatch
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
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1PssAndCorrelationInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun wrongRequestIdFailureIsIgnoredBeforeTheRealWorkerGoldenCompletes() {
        assumeCalibrationApi()
        bindCompilerService().use { session ->
            val baseline = session.compiler.prewarm(validPrewarmOptions())
            assertSuccessfulPrewarm(baseline)

            val correlated = session.compiler.prewarm(
                validPrewarmOptions().apply {
                    putBoolean(
                        CompilerServiceLifecycleTestProtocol
                            .KEY_INJECT_WRONG_REQUEST_ID_FAILURE,
                        true,
                    )
                },
            )

            assertSuccessfulPrewarm(correlated)
            assertTrue(
                "The debug correlation report was not returned.",
                correlated.getBoolean(
                    CompilerServiceLifecycleTestProtocol
                        .KEY_CORRELATION_REPORT_AVAILABLE,
                    false,
                ),
            )
            assertTrue(
                "The wrong-request failure was not injected.",
                correlated.getBoolean(
                    CompilerServiceLifecycleTestProtocol
                        .KEY_CORRELATION_FAULT_INJECTED,
                    false,
                ),
            )
            assertTrue(
                "The host did not prove that it ignored the wrong-request failure.",
                correlated.getBoolean(
                    CompilerServiceLifecycleTestProtocol
                        .KEY_CORRELATION_FAULT_IGNORED,
                    false,
                ),
            )
            assertEquals(
                baseline.getString(
                    TypeScriptCompilerContract
                        .KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                ),
                correlated.getString(
                    TypeScriptCompilerContract
                        .KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                ),
            )
            assertEquals(
                baseline.getString(
                    TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
                ),
                correlated.getString(
                    TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
                ),
            )
            Log.i(
                TAG,
                "Correlation gate PASS api=${Build.VERSION.SDK_INT} " +
                    "fault=wrong-request-id probe=real-worker-golden",
            )
        }
    }

    @Test
    fun compilerServiceAndItsSandboxRendererStayWithinCalibratedPssBounds() {
        assumeCalibrationApi()
        bindCompilerService().use { session ->
            logDeviceAndProvider()
            repeat(PSS_WARMUP_PROBE_COUNT) {
                assertSuccessfulPrewarm(
                    session.compiler.prewarm(validPrewarmOptions()),
                )
            }

            val processProbe = R1WebViewProcessMemoryProbe(context)
            val baselineProcesses = processProbe.awaitCompilerAndRenderer(
                session.serviceProcessName,
            )
            val baseline = processProbe.samplePss(
                processes = baselineProcesses,
                sampleCount = PSS_SAMPLE_COUNT,
                sampleIntervalMillis = PSS_SAMPLE_INTERVAL_MILLIS,
            )
            logPssEvidence(
                label = "baseline-after-$PSS_WARMUP_PROBE_COUNT-warmup",
                processes = baselineProcesses,
                batch = baseline,
            )
            assertAbsolutePssBounds("baseline", baseline)

            repeat(PSS_HOT_PROBE_COUNT) {
                assertSuccessfulPrewarm(
                    session.compiler.prewarm(validPrewarmOptions()),
                )
            }

            val finalProcesses = processProbe.requireUnchanged(
                expected = baselineProcesses,
                expectedCompilerProcessName = session.serviceProcessName,
            )
            val final = processProbe.samplePss(
                processes = finalProcesses,
                sampleCount = PSS_SAMPLE_COUNT,
                sampleIntervalMillis = PSS_SAMPLE_INTERVAL_MILLIS,
            )
            logPssEvidence(
                label = "final-after-$PSS_HOT_PROBE_COUNT-hot",
                processes = finalProcesses,
                batch = final,
            )
            assertAbsolutePssBounds("final", final)

            val combinedGrowthKiB =
                final.combinedMedianPssKiB - baseline.combinedMedianPssKiB
            assertTrue(
                "Combined compiler-service + renderer median PSS grew by " +
                    "$combinedGrowthKiB KiB; maximum allowed growth is " +
                    "$MAX_COMBINED_MEDIAN_GROWTH_KIB KiB. " +
                    "baseline=$baseline, final=$final",
                combinedGrowthKiB <= MAX_COMBINED_MEDIAN_GROWTH_KIB,
            )
            Log.i(
                TAG,
                "PSS gate PASS api=${Build.VERSION.SDK_INT} " +
                    "combinedMedianGrowthKiB=$combinedGrowthKiB " +
                    "maxGrowthKiB=$MAX_COMBINED_MEDIAN_GROWTH_KIB " +
                    "maxServiceKiB=$MAX_COMPILER_SERVICE_PSS_KIB " +
                    "maxRendererKiB=$MAX_SANDBOX_RENDERER_PSS_KIB " +
                    "maxCombinedKiB=$MAX_COMBINED_PSS_KIB",
            )
        }
    }

    private fun assertAbsolutePssBounds(
        label: String,
        batch: R1ProcessPssBatch,
    ) {
        assertTrue(
            "$label compiler-service PSS exceeded the calibrated upper bound: $batch",
            batch.compilerServiceMaximumPssKiB <=
                MAX_COMPILER_SERVICE_PSS_KIB,
        )
        assertTrue(
            "$label WebView sandbox renderer PSS exceeded the calibrated upper bound: $batch",
            batch.sandboxRendererMaximumPssKiB <=
                MAX_SANDBOX_RENDERER_PSS_KIB,
        )
        assertTrue(
            "$label combined PSS exceeded the calibrated upper bound: $batch",
            batch.combinedMaximumPssKiB <= MAX_COMBINED_PSS_KIB,
        )
    }

    private fun logPssEvidence(
        label: String,
        processes: R1CompilerWebViewProcesses,
        batch: R1ProcessPssBatch,
    ) {
        Log.i(
            TAG,
            "PSS $label compilerService=${processes.compilerService} " +
                "sandboxRenderer=${processes.sandboxRenderer} " +
                "serviceMedianKiB=${batch.compilerServiceMedianPssKiB} " +
                "rendererMedianKiB=${batch.sandboxRendererMedianPssKiB} " +
                "combinedMedianKiB=${batch.combinedMedianPssKiB} " +
                "combinedMaximumKiB=${batch.combinedMaximumPssKiB} " +
                "samples=${batch.samples}",
        )
    }

    private fun logDeviceAndProvider() {
        val provider = WebViewCompat.getCurrentWebViewPackage(context)
        assertNotNull("No current WebView provider is installed.", provider)
        Log.i(
            TAG,
            "PSS device api=${Build.VERSION.SDK_INT} " +
                "fingerprint=${Build.FINGERPRINT} " +
                "provider=${provider?.packageName}/${provider?.versionName}",
        )
    }

    private fun assertSuccessfulPrewarm(result: Bundle) {
        assertFalse(
            result.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE).orEmpty(),
            result.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE),
        )
        assertEquals(
            TypeScriptCompilerContract.STATUS_READY,
            result.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
        )
        assertTrue(
            result.getBoolean(
                TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                false,
            ),
        )
        assertFalse(
            result.getString(
                TypeScriptCompilerContract
                    .KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
            ).isNullOrBlank(),
        )
    }

    private fun assumeCalibrationApi() {
        assumeTrue(
            "The calibrated R1 PSS/correlation device gate targets API 31 and API 33.",
            Build.VERSION.SDK_INT == Build.VERSION_CODES.S ||
                Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU,
        )
    }

    private fun validPrewarmOptions() = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putLong(
            TypeScriptCompilerContract.KEY_TIMEOUT_MS,
            TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        )
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
                serviceProcessName = serviceInfo.processName,
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
        val serviceProcessName: String,
    ) : AutoCloseable {

        private var closed = false

        override fun close() {
            if (closed) return
            closed = true
            context.unbindService(connection)
        }
    }

    private companion object {
        const val TAG = "R1PssCorrelation"
        const val SERVICE_TIMEOUT_SECONDS = 5L
        const val PSS_WARMUP_PROBE_COUNT = 10
        const val PSS_HOT_PROBE_COUNT = 100
        const val PSS_SAMPLE_COUNT = 5
        const val PSS_SAMPLE_INTERVAL_MILLIS = 100L

        const val MAX_COMPILER_SERVICE_PSS_KIB = 128 * 1024
        const val MAX_SANDBOX_RENDERER_PSS_KIB = 192 * 1024
        const val MAX_COMBINED_PSS_KIB = 256 * 1024
        // Two clean-install calibration runs measured 30.9-31.0 MiB on API 33 and
        // 33.0-40.0 MiB on API 31 as V8 tiers up the fixed real-Worker golden.
        // Keep 20% headroom over the observed API 31 maximum while retaining a
        // materially tighter leak guard than the 256 MiB absolute combined cap.
        const val MAX_COMBINED_MEDIAN_GROWTH_KIB = 48 * 1024
    }
}
