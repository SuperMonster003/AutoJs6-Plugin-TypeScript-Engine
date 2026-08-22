package io.github.supermonster003.autojs6.plugin.typescript

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1ProcessRestartInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val activityManager
        get() = context.getSystemService(ActivityManager::class.java)

    private val processResourceProbe
        get() = RemoteProcessResourceProbe(context)

    @Test
    fun fingerprintsRemainStableAcrossTenCompilerProcessRestarts() {
        var baselineDistributionFingerprint: String? = null
        var baselineDeclarationsFingerprint: String? = null
        var baselineFileDescriptorCount: Int? = null

        terminateCachedCompilerProcessBeforeGate()

        repeat(PROCESS_RESTART_COUNT) { iteration ->
            bindCompilerService().use { session ->
                assertEquals(
                    COMPILER_PROCESS_NAME,
                    session.serviceProcessName,
                )
                assertNotNull(session.compiler)
                assertTrue(session.rawBinder.pingBinder())
                assertEquals(
                    null,
                    session.rawBinder.queryLocalInterface(ITypeScriptCompilerPlugin.DESCRIPTOR),
                )
                assertEquals(
                    TypeScriptCompilerContract.VERSION,
                    session.compiler.contractVersion,
                )
                assertEquals(
                    TypeScriptCompilerContract.HASH,
                    session.compiler.contractHash,
                )

                val process = awaitCompilerProcess()
                assertEquals(Process.myUid(), process.uid)
                assertTrue(
                    "Compiler service unexpectedly ran in the instrumentation process.",
                    Process.myPid() != process.pid,
                )
                val processIdentity = processResourceProbe.requireIdentity(process.pid)

                val result = session.compiler.prewarm(validPrewarmOptions())
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
                assertTrue(
                    result.getBoolean(
                        TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                        false,
                    ),
                )

                val distributionFingerprint = result.getString(
                    TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                )
                val declarationsFingerprint = result.getString(
                    TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
                )
                assertEquals(EXPECTED_DISTRIBUTION_FINGERPRINT, distributionFingerprint)
                assertEquals(EXPECTED_DECLARATIONS_FINGERPRINT, declarationsFingerprint)
                baselineDistributionFingerprint =
                    baselineDistributionFingerprint ?: distributionFingerprint
                baselineDeclarationsFingerprint =
                    baselineDeclarationsFingerprint ?: declarationsFingerprint
                assertEquals(baselineDistributionFingerprint, distributionFingerprint)
                assertEquals(baselineDeclarationsFingerprint, declarationsFingerprint)

                val fileDescriptors =
                    processResourceProbe.awaitStableFileDescriptors(
                        pid = processIdentity.pid,
                        label = "Compiler cold process ${iteration + 1}",
                        maximumCount = baselineFileDescriptorCount?.plus(
                            COLD_PROCESS_FD_COUNT_ALLOWANCE,
                        ),
                        expectedIdentity = processIdentity,
                    )
                baselineFileDescriptorCount =
                    baselineFileDescriptorCount ?: fileDescriptors.count
                Log.i(
                    TAG,
                    "Compiler cold process=${iteration + 1} identity=$processIdentity " +
                        "stableFdCount=${fileDescriptors.count}",
                )

                val binderDeath = CountDownLatch(1)
                session.rawBinder.linkToDeath(
                    { binderDeath.countDown() },
                    0,
                )

                // Remove BIND_AUTO_CREATE before killing the remote process. Otherwise Android may
                // restart the service while this iteration is still waiting for the old PID to die.
                session.close()
                killCompilerProcessIfStillRunning(process, processIdentity)

                assertTrue(
                    "Compiler Binder did not die during cold process ${iteration + 1}.",
                    binderDeath.await(PROCESS_DEATH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                awaitCompilerProcessIdentityGone(processIdentity)
                assertFalse(session.rawBinder.isBinderAlive)
            }
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

        try {
            val rawBinder = connection.awaitBinder()
            val compiler = ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder)
            assertNotNull(compiler)
            return BoundCompilerSession(
                context = context,
                connection = connection,
                rawBinder = rawBinder,
                compiler = compiler,
                serviceProcessName = serviceInfo.processName,
            )
        } catch (error: Throwable) {
            context.unbindService(connection)
            throw error
        }
    }

    private fun awaitCompilerProcess(): ActivityManager.RunningAppProcessInfo {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DISCOVERY_TIMEOUT_SECONDS)
        do {
            val matches = runningCompilerProcesses()
            if (matches.size == 1) {
                return matches.single()
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Expected exactly one $COMPILER_PROCESS_NAME process, " +
                "found ${runningCompilerProcesses().map { it.pid }}.",
        )
    }

    private fun killCompilerProcessIfStillRunning(
        expected: ActivityManager.RunningAppProcessInfo,
        expectedIdentity: RemoteProcessIdentity,
    ) {
        val matchingProcess = runningCompilerProcesses()
            .singleOrNull { process ->
                process.pid == expected.pid && process.uid == expected.uid
            }
        if (
            matchingProcess != null &&
            processResourceProbe.identityOrNull(matchingProcess.pid) == expectedIdentity
        ) {
            Process.killProcess(matchingProcess.pid)
        }
    }

    private fun terminateCachedCompilerProcessBeforeGate() {
        runningCompilerProcesses().forEach { process ->
            val identity = processResourceProbe.requireIdentity(process.pid)
            Process.killProcess(process.pid)
            awaitCompilerProcessIdentityGone(identity)
        }
        assertTrue(
            "A cached compiler process remained before the cold-start gate.",
            runningCompilerProcesses().isEmpty(),
        )
    }

    private fun awaitCompilerProcessIdentityGone(
        expectedIdentity: RemoteProcessIdentity,
    ) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
        do {
            val matchingCompilerProcess = runningCompilerProcesses()
                .firstOrNull { process -> process.pid == expectedIdentity.pid }
            val currentIdentity =
                processResourceProbe.directIdentityOrNull(expectedIdentity.pid)
            if (currentIdentity != null && currentIdentity != expectedIdentity) {
                // The kernel may legally reuse the PID after the old process is gone.
                return
            }
            if (matchingCompilerProcess == null && currentIdentity == null) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Compiler process identity $expectedIdentity remained visible after its Binder died.",
        )
    }

    private fun runningCompilerProcesses(): List<ActivityManager.RunningAppProcessInfo> =
        activityManager.runningAppProcesses
            .orEmpty()
            .filter { process ->
                process.processName == COMPILER_PROCESS_NAME &&
                    process.uid == Process.myUid()
            }

    private class BoundCompilerSession(
        private val context: Context,
        private val connection: ServiceConnection,
        val rawBinder: IBinder,
        val compiler: ITypeScriptCompilerPlugin,
        val serviceProcessName: String,
    ) : Closeable {

        private var bound = true

        override fun close() {
            if (!bound) return
            bound = false
            context.unbindService(connection)
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
            return requireNotNull(binder) { "Compiler service returned a null binding." }
        }
    }

    private companion object {
        const val TAG = "R1ProcessRestart"
        const val PROCESS_RESTART_COUNT = 10
        const val COLD_PROCESS_FD_COUNT_ALLOWANCE = 4
        const val PROCESS_DISCOVERY_TIMEOUT_SECONDS = 5L
        const val PROCESS_DEATH_TIMEOUT_SECONDS = 10L
        const val PROCESS_POLL_INTERVAL_MILLIS = 50L
        const val SERVICE_TIMEOUT_SECONDS = 10L
        const val EXPECTED_DECLARATIONS_FINGERPRINT =
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f"
        const val EXPECTED_DISTRIBUTION_FINGERPRINT =
            "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658"
        const val COMPILER_PROCESS_NAME =
            "io.github.supermonster003.autojs6.plugin.typescript:typescript_compiler"
    }
}
