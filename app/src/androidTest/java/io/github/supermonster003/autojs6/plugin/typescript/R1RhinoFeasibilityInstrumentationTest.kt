package io.github.supermonster003.autojs6.plugin.typescript

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1RhinoFeasibilityInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val activityManager
        get() = context.getSystemService(ActivityManager::class.java)

    @Test
    fun realWebViewGoldenSucceedsWhileTwoColdRhinoProcessesStayUnsupported() {
        assumeRhinoArtifactPackaged()
        assertRealWebViewPrewarmGolden()
        terminateCachedRhinoProbeProcesses()

        val first = runColdParseProbe()
        assertRhinoUnsupported(first)
        logProbeEvidence("cold-1", first)
        terminateRhinoProbeProcess(first.getInt(R1RhinoProbeProtocol.KEY_PID))

        val second = runColdParseProbe()
        assertRhinoUnsupported(second)
        logProbeEvidence("cold-2", second)
        terminateRhinoProbeProcess(second.getInt(R1RhinoProbeProtocol.KEY_PID))

        assertNotEquals(
            "The feasibility gate must exercise two distinct cold Rhino processes.",
            first.getInt(R1RhinoProbeProtocol.KEY_PID),
            second.getInt(R1RhinoProbeProtocol.KEY_PID),
        )
        for (key in STABLE_FINGERPRINT_KEYS) {
            assertEquals(
                "Cold Rhino probe identity changed for $key.",
                first.getString(key),
                second.getString(key),
            )
        }
        assertEquals(
            R1RhinoProbeProtocol.PHASE_COMPILER_PARSE,
            first.getString(R1RhinoProbeProtocol.KEY_PHASE),
        )
        assertEquals(
            first.getString(R1RhinoProbeProtocol.KEY_PHASE),
            second.getString(R1RhinoProbeProtocol.KEY_PHASE),
        )
    }

    @Test
    fun uncooperativeRhinoParseIsKilledWithinBoundAndClientCanRebind() {
        assumeRhinoArtifactPackaged()
        terminateCachedRhinoProbeProcesses()

        val killedPid: Int
        val cancellationStartedAt: Long
        bindProbeService().use { session ->
            session.start(R1RhinoProbeProtocol.MODE_UNCOOPERATIVE_CANCELLATION)
            val started = session.awaitStarted()
            killedPid = started.getInt(R1RhinoProbeProtocol.KEY_PID)
            cancellationStartedAt = SystemClock.elapsedRealtime()
            session.cancel()
            assertTrue(
                "Uncooperative Rhino process did not die before the cancellation bound.",
                session.awaitBinderDeath(CANCELLATION_PROCESS_DEATH_SECONDS),
            )
        }
        val cancellationLatency = SystemClock.elapsedRealtime() - cancellationStartedAt
        assertTrue(
            "Cancellation watchdog latency $cancellationLatency ms exceeded the gate.",
            cancellationLatency in 0..MAX_CANCELLATION_LATENCY_MILLIS,
        )
        Log.i(
            TAG,
            "watchdog killedPid=$killedPid cancellationLatencyMs=$cancellationLatency " +
                "maximumLatencyMs=$MAX_CANCELLATION_LATENCY_MILLIS binderDeath=true",
        )
        awaitRhinoProbeProcessGone(killedPid)

        val recovered = runColdParseProbe()
        assertRhinoUnsupported(recovered)
        assertNotEquals(
            "Probe client rebound to the process killed by the cancellation watchdog.",
            killedPid,
            recovered.getInt(R1RhinoProbeProtocol.KEY_PID),
        )
        logProbeEvidence("watchdog-rebind", recovered)
        terminateRhinoProbeProcess(recovered.getInt(R1RhinoProbeProtocol.KEY_PID))
    }

    private fun assumeRhinoArtifactPackaged() {
        assumeTrue(
            "R1 Rhino feasibility tests require -Pr1RhinoProbeJar; " +
                "ordinary debug/connected builds intentionally skip them.",
            BuildConfig.R1_RHINO_PROBE_PACKAGED,
        )
    }

    private fun runColdParseProbe(): Bundle =
        bindProbeService().use { session ->
            session.start(R1RhinoProbeProtocol.MODE_PARSE)
            session.awaitResult()
        }

    @Suppress("DEPRECATION")
    private fun bindProbeService(): BoundProbeSession {
        val component = ComponentName(context, R1RhinoProbeService::class.java)
        val serviceInfo = context.packageManager.getServiceInfo(component, 0)
        assertFalse("Rhino probe service must not be exported.", serviceInfo.exported)
        assertEquals(RHINO_PROCESS_NAME, serviceInfo.processName)

        val connection = AwaitingServiceConnection()
        val bound = context.bindService(
            Intent().setComponent(component),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("Failed to bind debug-only Rhino probe service.", bound)
        try {
            return BoundProbeSession(
                context = context,
                connection = connection,
                rawBinder = connection.awaitBinder(),
            )
        } catch (error: Throwable) {
            context.unbindService(connection)
            throw error
        }
    }

    private fun assertRhinoUnsupported(result: Bundle) {
        assertFalse(result.getBoolean(R1RhinoProbeProtocol.KEY_SUPPORTED, true))
        assertFalse(result.getBoolean(R1RhinoProbeProtocol.KEY_FALLBACK_ELIGIBLE, true))
        assertEquals(
            R1RhinoProbeProtocol.PHASE_COMPILER_PARSE,
            result.getString(R1RhinoProbeProtocol.KEY_PHASE),
        )
        assertEquals(
            R1RhinoProbeProtocol.EXPECTED_LANGUAGE_VERSION_ES6,
            result.getInt(R1RhinoProbeProtocol.KEY_LANGUAGE_VERSION),
        )
        assertEquals(
            R1RhinoProbeProtocol.INTERPRETED_OPTIMIZATION_LEVEL,
            result.getInt(R1RhinoProbeProtocol.KEY_OPTIMIZATION_LEVEL),
        )
        assertEquals(
            R1RhinoProbeProtocol.SECURITY_PROFILE_SAFE_DENY_ALL_CLASSES,
            result.getString(R1RhinoProbeProtocol.KEY_SECURITY_PROFILE),
        )
        assertEquals(
            EXPECTED_RHINO_ARTIFACT,
            result.getString(R1RhinoProbeProtocol.KEY_ARTIFACT_NAME),
        )
        assertEquals(
            EXPECTED_RHINO_ARTIFACT_SHA256,
            result.getString(R1RhinoProbeProtocol.KEY_ARTIFACT_SHA256),
        )
        assertEquals(
            EXPECTED_RHINO_SOURCE_COMMIT,
            result.getString(R1RhinoProbeProtocol.KEY_RHINO_SOURCE_COMMIT),
        )
        assertEquals(
            EXPECTED_AUTOJS6_CONSUMER_COMMIT,
            result.getString(R1RhinoProbeProtocol.KEY_AUTOJS6_CONSUMER_COMMIT),
        )
        assertEquals(
            EXPECTED_COMPILER_SHA256,
            result.getString(R1RhinoProbeProtocol.KEY_COMPILER_ASSET_SHA256),
        )
        assertEquals(
            EXPECTED_DISTRIBUTION_FINGERPRINT,
            result.getString(R1RhinoProbeProtocol.KEY_DISTRIBUTION_FINGERPRINT),
        )
        assertEquals(
            EXPECTED_DECLARATIONS_FINGERPRINT,
            result.getString(R1RhinoProbeProtocol.KEY_DECLARATIONS_FINGERPRINT),
        )
        assertEquals(
            BuildConfig.TYPESCRIPT_VERSION,
            result.getString(R1RhinoProbeProtocol.KEY_TYPESCRIPT_VERSION),
        )
        assertTrue(result.getInt(R1RhinoProbeProtocol.KEY_PID) > 0)
        assertTrue(result.getLong(R1RhinoProbeProtocol.KEY_BASELINE_PSS_KIB) > 0L)
        assertTrue(result.getLong(R1RhinoProbeProtocol.KEY_FINAL_PSS_KIB) > 0L)
        assertTrue(result.getLong(R1RhinoProbeProtocol.KEY_ELAPSED_MS) >= 0L)

        val errorClass = result.getString(R1RhinoProbeProtocol.KEY_ERROR_CLASS).orEmpty()
        val errorMessage = result.getString(R1RhinoProbeProtocol.KEY_ERROR_MESSAGE).orEmpty()
        assertTrue("Expected a bounded Rhino parser failure.", errorClass.isNotBlank())
        assertTrue(errorMessage.isNotBlank())
        assertTrue(errorMessage.length <= MAX_ERROR_CHARS)

        val failureLine = result.getInt(R1RhinoProbeProtocol.KEY_ERROR_LINE, -1)
        val failureLineSource =
            result.getString(R1RhinoProbeProtocol.KEY_ERROR_LINE_SOURCE).orEmpty()
        assertTrue(
            "Rhino failure was not near the locked TypeScript syntax boundary: " +
                "line=$failureLine source=$failureLineSource " +
                "errorClass=$errorClass errorMessage=$errorMessage",
            failureLine in EXPECTED_FAILURE_LINE_RANGE ||
                failureLineSource.contains("for (const value of iter)"),
        )
    }

    private fun logProbeEvidence(
        label: String,
        result: Bundle,
    ) {
        Log.i(
            TAG,
            "$label pid=${result.getInt(R1RhinoProbeProtocol.KEY_PID)} " +
                "supported=${result.getBoolean(R1RhinoProbeProtocol.KEY_SUPPORTED)} " +
                "fallbackEligible=" +
                "${result.getBoolean(R1RhinoProbeProtocol.KEY_FALLBACK_ELIGIBLE)} " +
                "phase=${result.getString(R1RhinoProbeProtocol.KEY_PHASE)} " +
                "artifactSha256=" +
                "${result.getString(R1RhinoProbeProtocol.KEY_ARTIFACT_SHA256)} " +
                "compilerSha256=" +
                "${result.getString(R1RhinoProbeProtocol.KEY_COMPILER_ASSET_SHA256)} " +
                "distributionFingerprint=" +
                "${result.getString(R1RhinoProbeProtocol.KEY_DISTRIBUTION_FINGERPRINT)} " +
                "elapsedMs=${result.getLong(R1RhinoProbeProtocol.KEY_ELAPSED_MS)} " +
                "baselinePssKiB=" +
                "${result.getLong(R1RhinoProbeProtocol.KEY_BASELINE_PSS_KIB)} " +
                "finalPssKiB=${result.getLong(R1RhinoProbeProtocol.KEY_FINAL_PSS_KIB)} " +
                "errorClass=${result.getString(R1RhinoProbeProtocol.KEY_ERROR_CLASS)} " +
                "errorLine=${result.getInt(R1RhinoProbeProtocol.KEY_ERROR_LINE, -1)} " +
                "errorColumn=${result.getInt(R1RhinoProbeProtocol.KEY_ERROR_COLUMN, -1)}",
        )
    }

    private fun assertRealWebViewPrewarmGolden() {
        val discoveryIntent = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(discoveryIntent, 0)
        assertEquals("Expected exactly one production compiler service.", 1, matches.size)
        val serviceInfo = matches.single().serviceInfo
        assertNotNull(serviceInfo)
        val connection = AwaitingServiceConnection()
        val bound = context.bindService(
            Intent(discoveryIntent).setComponent(
                ComponentName(serviceInfo.packageName, serviceInfo.name),
            ),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("Failed to bind production WebView compiler service.", bound)
        try {
            val compiler = ITypeScriptCompilerPlugin.Stub.asInterface(
                connection.awaitBinder(),
            )
            assertNotNull(compiler)
            val result = compiler.prewarm(validPrewarmOptions())
            assertFalse(
                result.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE).orEmpty(),
                result.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(
                TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                result.getString(TypeScriptCompilerContract.KEY_BACKEND),
            )
            assertEquals(
                TypeScriptCompilerContract.STATUS_READY,
                result.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
            )
            assertFalse(
                result.getBoolean(
                    TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                    true,
                ),
            )
            assertEquals(
                EXPECTED_DISTRIBUTION_FINGERPRINT,
                result.getString(
                    TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                ),
            )

            val info = compiler.compilerInfo
            assertEquals(
                TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                info.getString(TypeScriptCompilerContract.KEY_BACKEND),
            )
            assertFalse(
                info.getBoolean(TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE, true),
            )
        } finally {
            context.unbindService(connection)
        }
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

    private fun terminateCachedRhinoProbeProcesses() {
        runningRhinoProbeProcesses().forEach { process ->
            Process.killProcess(process.pid)
            awaitRhinoProbeProcessGone(process.pid)
        }
    }

    private fun terminateRhinoProbeProcess(pid: Int) {
        assertNotEquals("Refusing to kill the instrumentation process.", Process.myPid(), pid)
        Process.killProcess(pid)
        awaitRhinoProbeProcessGone(pid)
    }

    private fun awaitRhinoProbeProcessGone(pid: Int) {
        val deadline = SystemClock.elapsedRealtime() +
            TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
        do {
            if (runningRhinoProbeProcesses().none { process -> process.pid == pid }) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_MILLIS)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Rhino probe process $pid remained visible after termination.")
    }

    private fun runningRhinoProbeProcesses(): List<ActivityManager.RunningAppProcessInfo> =
        activityManager.runningAppProcesses
            .orEmpty()
            .filter { process ->
                process.uid == Process.myUid() &&
                    process.processName == RHINO_PROCESS_NAME
            }

    private class BoundProbeSession(
        private val context: Context,
        private val connection: AwaitingServiceConnection,
        rawBinder: IBinder,
    ) : Closeable {

        private val callbackThread = HandlerThread("r1-rhino-probe-client").apply {
            start()
        }
        private val started = CountDownLatch(1)
        private val completed = CountDownLatch(1)
        private val binderDied = CountDownLatch(1)
        private val service = Messenger(rawBinder)
        private val replies = Messenger(
            object : Handler(callbackThread.looper) {
                override fun handleMessage(message: Message) {
                    when (message.what) {
                        R1RhinoProbeProtocol.MESSAGE_STARTED -> {
                            startedResult = Bundle(message.data)
                            started.countDown()
                        }
                        R1RhinoProbeProtocol.MESSAGE_RESULT -> {
                            terminalResult = Bundle(message.data)
                            completed.countDown()
                        }
                        else -> super.handleMessage(message)
                    }
                }
            },
        )

        @Volatile
        private var requestId: String? = null

        @Volatile
        private var startedResult: Bundle? = null

        @Volatile
        private var terminalResult: Bundle? = null

        private var bound = true

        init {
            rawBinder.linkToDeath({ binderDied.countDown() }, 0)
        }

        fun start(mode: String) {
            check(requestId == null) { "Probe session already started." }
            val id = "r1-rhino-${SystemClock.elapsedRealtimeNanos()}"
            requestId = id
            service.send(
                Message.obtain(null, R1RhinoProbeProtocol.MESSAGE_PROBE).apply {
                    data = Bundle().apply {
                        putString(R1RhinoProbeProtocol.KEY_REQUEST_ID, id)
                        putString(R1RhinoProbeProtocol.KEY_MODE, mode)
                    }
                    replyTo = replies
                },
            )
        }

        fun cancel() {
            val id = checkNotNull(requestId) { "Probe session has not started." }
            service.send(
                Message.obtain(null, R1RhinoProbeProtocol.MESSAGE_CANCEL).apply {
                    data = Bundle().apply {
                        putString(R1RhinoProbeProtocol.KEY_REQUEST_ID, id)
                    }
                    replyTo = replies
                },
            )
        }

        fun awaitStarted(): Bundle {
            assertTrue(
                "Timed out waiting for Rhino evaluateReader to start.",
                started.await(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(startedResult)
        }

        fun awaitResult(): Bundle {
            assertTrue(
                "Timed out waiting for Rhino feasibility result.",
                completed.await(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(terminalResult)
        }

        fun awaitBinderDeath(timeoutSeconds: Long): Boolean =
            binderDied.await(timeoutSeconds, TimeUnit.SECONDS)

        override fun close() {
            if (bound) {
                bound = false
                context.unbindService(connection)
            }
            callbackThread.quitSafely()
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
                "Timed out waiting for a service Binder.",
                connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(binder) { "Service returned a null binding." }
        }
    }

    private companion object {
        const val TAG = "R1RhinoProbe"
        const val RHINO_PROCESS_NAME =
            "io.github.supermonster003.autojs6.plugin.typescript:typescript_rhino_probe"
        const val EXPECTED_RHINO_ARTIFACT = "org-mozilla-rhino-2_0_0-SNAPSHOT.jar"
        const val EXPECTED_RHINO_ARTIFACT_SHA256 =
            "712a73d62edc56eb36a909a229eca87ac433afa7aaceb6d60e3acbbc75a9533a"
        const val EXPECTED_RHINO_SOURCE_COMMIT =
            "e0dfa6f316bfb5ed99fff3d0cf33310e18458d3d"
        const val EXPECTED_AUTOJS6_CONSUMER_COMMIT =
            "f78c281985618205d2dc3dd056cf9bf042cc5996"
        const val EXPECTED_COMPILER_SHA256 =
            "569177652966bd528c319171c7dd22860dbf72bde116cbc4f644f1d02bb12e39"
        const val EXPECTED_DISTRIBUTION_FINGERPRINT =
            "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658"
        const val EXPECTED_DECLARATIONS_FINGERPRINT =
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f"
        const val MAX_ERROR_CHARS = 4_096
        const val PROBE_TIMEOUT_SECONDS = 30L
        const val SERVICE_TIMEOUT_SECONDS = 10L
        const val PROCESS_DEATH_TIMEOUT_SECONDS = 10L
        const val CANCELLATION_PROCESS_DEATH_SECONDS = 5L
        const val MAX_CANCELLATION_LATENCY_MILLIS = 3_000L
        const val PROCESS_POLL_MILLIS = 50L
        val EXPECTED_FAILURE_LINE_RANGE = 2_320..2_360
        val STABLE_FINGERPRINT_KEYS = listOf(
            R1RhinoProbeProtocol.KEY_ARTIFACT_SHA256,
            R1RhinoProbeProtocol.KEY_COMPILER_ASSET_SHA256,
            R1RhinoProbeProtocol.KEY_DISTRIBUTION_FINGERPRINT,
            R1RhinoProbeProtocol.KEY_DECLARATIONS_FINGERPRINT,
            R1RhinoProbeProtocol.KEY_RHINO_SOURCE_COMMIT,
            R1RhinoProbeProtocol.KEY_AUTOJS6_CONSUMER_COMMIT,
        )
    }
}
