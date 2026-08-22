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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.io.File
import java.util.UUID
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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2PrivateTransientOrphanScavengerInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val activityManager
        get() = context.getSystemService(ActivityManager::class.java)

    private val processResourceProbe
        get() = RemoteProcessResourceProbe(context)

    @Test
    fun coldSuccessorScavengesExactOrphansAndPreservesOtherChildren() {
        val service = discoverCompilerService()
        terminateCachedCompilerProcesses(service.processName)

        val predecessorIdentity = bindCompilerService(service).use { predecessor ->
            assertRemoteCompilerBinding(predecessor)
            processResourceProbe.requireIdentity(
                awaitCompilerProcess(service.processName).pid,
            )
        }
        terminateCompilerProcess(
            expected = predecessorIdentity,
            processName = service.processName,
        )
        awaitNoCompilerProcess(service.processName)

        val fixture = createOrphanFixture()
        var successor: BoundCompilerSession? = null
        try {
            successor = bindCompilerService(service)
            assertRemoteCompilerBinding(successor)
            val successorIdentity = processResourceProbe.requireIdentity(
                awaitCompilerProcess(service.processName).pid,
            )
            assertTrue(
                "The compiler service did not start a distinct successor epoch.",
                successorIdentity != predecessorIdentity,
            )

            // onCreate and its startup scavenger must finish before this Binder is published.
            fixture.assertScavengedAndPreserved()

            val prewarm = successor.compiler.prewarm(validPrewarmOptions())
            assertFalse(
                prewarm.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE).orEmpty(),
                prewarm.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(
                TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                prewarm.getString(TypeScriptCompilerContract.KEY_BACKEND),
            )
            assertEquals(
                TypeScriptCompilerContract.STATUS_READY,
                prewarm.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
            )
            fixture.assertScavengedAndPreserved()
        } finally {
            try {
                successor?.close()
            } finally {
                fixture.cleanup()
            }
        }
    }

    private fun createOrphanFixture(): OrphanFixture {
        val inputDirectory = context.getDir(
            R2PrivateTransientOrphanScavenger.INPUT_STAGING_DIRECTORY_NAME,
            Context.MODE_PRIVATE,
        )
        val outputDirectory = context.getDir(
            R2PrivateTransientOrphanScavenger.OUTPUT_STAGING_DIRECTORY_NAME,
            Context.MODE_PRIVATE,
        )
        val token = UUID.randomUUID().toString().replace("-", "")
        val orphanFiles = ArrayList<File>()
        val preservedFiles = ArrayList<File>()
        val preservedDirectories = ArrayList<File>()
        val nestedFiles = ArrayList<File>()
        try {
            orphanFiles += inputDirectory.createTestFile(
                name = "r2-private-input-instrumentation-$token.staging",
                readOnly = true,
            )
            orphanFiles += inputDirectory.createTestFile(
                name = "r2-project-vfs-instrumentation-$token.spool",
                readOnly = true,
            )
            orphanFiles += outputDirectory.createTestFile(
                name = "r2-private-output-instrumentation-$token.zip.staging",
                readOnly = true,
            )

            preservedFiles += inputDirectory.createTestFile(
                "r2-private-input-instrumentation-$token.staging.sentinel",
            )
            preservedFiles += inputDirectory.createTestFile(
                "r2-orphan-scavenger-$token.sentinel",
            )
            preservedFiles += outputDirectory.createTestFile(
                "r2-private-output-instrumentation-$token.zip.staging.sentinel",
            )

            preservedDirectories += inputDirectory.createTestDirectory(
                "r2-private-input-directory-$token.staging",
            )
            preservedDirectories += inputDirectory.createTestDirectory(
                "r2-project-vfs-directory-$token.spool",
            )
            preservedDirectories += outputDirectory.createTestDirectory(
                "r2-private-output-directory-$token.zip.staging",
            )
            nestedFiles += preservedDirectories[0].createTestFile(
                "r2-private-input-nested-$token.staging",
            )
            nestedFiles += preservedDirectories[1].createTestFile(
                "r2-project-vfs-nested-$token.spool",
            )
            nestedFiles += preservedDirectories[2].createTestFile(
                "r2-private-output-nested-$token.zip.staging",
            )
            return OrphanFixture(
                orphanFiles = orphanFiles,
                preservedFiles = preservedFiles,
                preservedDirectories = preservedDirectories,
                nestedFiles = nestedFiles,
            )
        } catch (failure: Throwable) {
            cleanupExactFixturePaths(
                orphanFiles = orphanFiles,
                preservedFiles = preservedFiles,
                preservedDirectories = preservedDirectories,
                nestedFiles = nestedFiles,
            )
            throw failure
        }
    }

    private fun File.createTestFile(
        name: String,
        readOnly: Boolean = false,
    ): File {
        val file = File(this, name)
        assertTrue("Test fixture already exists: ${file.absolutePath}", file.createNewFile())
        try {
            file.writeText("R2 private transient orphan instrumentation fixture")
            if (readOnly) {
                assertTrue(
                    "Could not make the orphan fixture read-only: ${file.absolutePath}",
                    file.setReadOnly(),
                )
            }
            return file
        } catch (failure: Throwable) {
            runCatching { file.setWritable(true, true) }
            runCatching { file.delete() }
            throw failure
        }
    }

    private fun File.createTestDirectory(name: String): File =
        File(this, name).also { directory ->
            assertTrue(
                "Test fixture directory already exists: ${directory.absolutePath}",
                directory.mkdir(),
            )
        }

    @Suppress("DEPRECATION")
    private fun discoverCompilerService(): CompilerServiceDescriptor {
        val discovery = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(discovery, 0)
        assertEquals("Expected exactly one TypeScript compiler service.", 1, matches.size)
        val serviceInfo = matches.single().serviceInfo
        assertNotNull(serviceInfo)
        return CompilerServiceDescriptor(
            intent = Intent(discovery).setComponent(
                ComponentName(serviceInfo.packageName, serviceInfo.name),
            ),
            processName = serviceInfo.processName,
        )
    }

    private fun bindCompilerService(
        service: CompilerServiceDescriptor,
    ): BoundCompilerSession {
        val connection = AwaitingServiceConnection()
        val bound = context.bindService(
            service.intent,
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("Failed to bind ${service.intent.component}.", bound)
        try {
            val rawBinder = connection.awaitBinder()
            return BoundCompilerSession(
                context = context,
                connection = connection,
                rawBinder = rawBinder,
                compiler = ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder),
            )
        } catch (failure: Throwable) {
            runCatching { context.unbindService(connection) }
            throw failure
        }
    }

    private fun assertRemoteCompilerBinding(session: BoundCompilerSession) {
        assertTrue(session.rawBinder.pingBinder())
        assertNull(
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

    private fun awaitCompilerProcess(
        processName: String,
    ): ActivityManager.RunningAppProcessInfo {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DISCOVERY_TIMEOUT_SECONDS)
        do {
            val matches = runningCompilerProcesses(processName)
            if (matches.size == 1) {
                return matches.single()
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Expected one live $processName process, found " +
                runningCompilerProcesses(processName).map { process -> process.pid },
        )
    }

    private fun terminateCachedCompilerProcesses(processName: String) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
        do {
            val processes = runningCompilerProcesses(processName)
            if (processes.isEmpty()) return
            processes.forEach { process ->
                val identity = processResourceProbe.identityOrNull(process.pid)
                if (identity != null) {
                    Process.killProcess(identity.pid)
                }
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Cached compiler processes remained before the isolated gate: " +
                runningCompilerProcesses(processName).map { process -> process.pid },
        )
    }

    private fun terminateCompilerProcess(
        expected: RemoteProcessIdentity,
        processName: String,
    ) {
        if (processResourceProbe.identityOrNull(expected.pid) == expected) {
            Process.killProcess(expected.pid)
        }
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
        do {
            val currentIdentity = processResourceProbe.directIdentityOrNull(expected.pid)
            if (currentIdentity == null || currentIdentity != expected) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Compiler predecessor $expected remained visible; current service PIDs=" +
                runningCompilerProcesses(processName).map { process -> process.pid },
        )
    }

    private fun awaitNoCompilerProcess(processName: String) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
        do {
            if (runningCompilerProcesses(processName).isEmpty()) return
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "A compiler process remained before orphan placement: " +
                runningCompilerProcesses(processName).map { process -> process.pid },
        )
    }

    private fun runningCompilerProcesses(
        processName: String,
    ): List<ActivityManager.RunningAppProcessInfo> =
        activityManager.runningAppProcesses
            .orEmpty()
            .filter { process ->
                process.uid == Process.myUid() &&
                    process.processName == processName
            }

    private data class CompilerServiceDescriptor(
        val intent: Intent,
        val processName: String,
    )

    private class BoundCompilerSession(
        private val context: Context,
        private val connection: ServiceConnection,
        val rawBinder: IBinder,
        val compiler: ITypeScriptCompilerPlugin,
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

        override fun onBindingDied(name: ComponentName) {
            connected.countDown()
        }

        fun awaitBinder(): IBinder {
            assertTrue(
                "Timed out waiting for the TypeScript compiler service.",
                connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(binder) {
                "TypeScript compiler service returned no Binder."
            }
        }
    }

    private data class OrphanFixture(
        val orphanFiles: List<File>,
        val preservedFiles: List<File>,
        val preservedDirectories: List<File>,
        val nestedFiles: List<File>,
    ) {

        fun assertScavengedAndPreserved() {
            orphanFiles.forEach { orphan ->
                assertFalse(
                    "Recognized orphan survived cold startup: ${orphan.absolutePath}",
                    orphan.exists(),
                )
            }
            preservedFiles.forEach { preserved ->
                assertTrue(
                    "Unknown/sentinel file was removed: ${preserved.absolutePath}",
                    preserved.isFile,
                )
            }
            preservedDirectories.forEach { preserved ->
                assertTrue(
                    "Recognized-name directory was removed: ${preserved.absolutePath}",
                    preserved.isDirectory,
                )
            }
            nestedFiles.forEach { nested ->
                assertTrue(
                    "Nested recognized file was removed recursively: ${nested.absolutePath}",
                    nested.isFile,
                )
            }
        }

        fun cleanup() {
            cleanupExactFixturePaths(
                orphanFiles = orphanFiles,
                preservedFiles = preservedFiles,
                preservedDirectories = preservedDirectories,
                nestedFiles = nestedFiles,
            )
        }
    }

    private companion object {
        const val PROCESS_DISCOVERY_TIMEOUT_SECONDS = 10L
        const val PROCESS_DEATH_TIMEOUT_SECONDS = 10L
        const val BIND_TIMEOUT_SECONDS = 10L
        const val PROCESS_POLL_INTERVAL_MILLIS = 50L

        fun cleanupExactFixturePaths(
            orphanFiles: List<File>,
            preservedFiles: List<File>,
            preservedDirectories: List<File>,
            nestedFiles: List<File>,
        ) {
            (orphanFiles + preservedFiles + nestedFiles)
                .asReversed()
                .forEach { file ->
                    runCatching { file.setWritable(true, true) }
                    runCatching { file.delete() }
                }
            preservedDirectories
                .asReversed()
                .forEach { directory ->
                    runCatching { directory.setWritable(true, true) }
                    runCatching { directory.delete() }
                }
        }
    }
}
