package io.github.supermonster003.autojs6.plugin.typescript

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1BinderFdInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun compilerDiscoveryBindsTheRealRemoteR0ServiceAndReportsCapabilities() {
        withCompilerService { serviceInfo, rawBinder, compiler ->
            assertEquals(
                "${context.packageName}:typescript_compiler",
                serviceInfo.processName,
            )
            assertNull(
                "The compiler Binder must be a remote proxy from :typescript_compiler.",
                rawBinder.queryLocalInterface(ITypeScriptCompilerPlugin.DESCRIPTOR),
            )
            assertTrue(rawBinder.pingBinder())
            assertEquals(TypeScriptCompilerContract.VERSION, compiler.contractVersion)
            assertEquals(TypeScriptCompilerContract.HASH, compiler.contractHash)

            val info = compiler.compilerInfo
            assertEquals(
                TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                info.getString(TypeScriptCompilerContract.KEY_BACKEND),
            )
            assertFalse(
                info.getBoolean(TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE, true),
            )
            assertEquals(
                TypeScriptPluginActions.COMPILER,
                info.getString(TypeScriptPluginCapabilityKeys.COMPILER_SERVICE_ACTION),
            )
            assertEquals(
                TypeScriptCompilerContract.VERSION,
                info.getInt(TypeScriptPluginCapabilityKeys.COMPILER_CONTRACT_VERSION),
            )
            assertEquals(
                TypeScriptCompilerContract.HASH,
                info.getString(TypeScriptPluginCapabilityKeys.COMPILER_CONTRACT_HASH),
            )
        }
    }

    @Test
    fun prewarmRunsTheRealTypeScriptWorkerGoldenAndKeepsCompilationUnavailable() {
        withCompilerService { _, _, compiler ->
            repeat(HOT_PROBE_COUNT) {
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
                assertEquals(
                    EXPECTED_DISTRIBUTION_FINGERPRINT,
                    result.getString(
                        TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                    ),
                )
                assertEquals(
                    EXPECTED_DECLARATIONS_FINGERPRINT,
                    result.getString(TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT),
                )
                assertFalse(
                    result.getBoolean(
                        TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                        true,
                    ),
                )
                assertFalse(
                    result.getBoolean(
                        TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION,
                        true,
                    ),
                )
                assertFalse(
                    result.getBoolean(
                        TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS,
                        true,
                    ),
                )
                assertFalse(
                    result.getBoolean(
                        TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3,
                        true,
                    ),
                )
            }

            val info = compiler.compilerInfo
            assertEquals(
                TypeScriptCompilerContract.STATUS_READY,
                info.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
            )
            assertEquals(
                EXPECTED_DISTRIBUTION_FINGERPRINT,
                info.getString(
                    TypeScriptPluginCapabilityKeys.COMPILER_DISTRIBUTION_FINGERPRINT,
                ),
            )
        }
    }

    @Test
    fun timedOutProbeRebuildsTheWorkerAndTheNextProbeSucceeds() {
        withCompilerService { _, _, compiler ->
            val timedOut = compiler.prewarm(validPrewarmOptions(timeoutMs = 1L))
            assertEquals(
                TypeScriptCompilerContract.ERROR_TIMEOUT,
                timedOut.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )

            val recovered = compiler.prewarm(validPrewarmOptions())
            assertFalse(
                recovered.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE).orEmpty(),
                recovered.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(
                TypeScriptCompilerContract.STATUS_READY,
                recovered.getString(TypeScriptPluginCapabilityKeys.SERVICE_STATUS),
            )
            assertFalse(
                recovered.getBoolean(
                    TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                    true,
                ),
            )
        }
    }

    @Test
    fun seekableStagingFilesReachR0RejectWithoutCallbackAndCallerRetainsOwnership() {
        val inputBytes = "r0-input-staging".toByteArray()
        val outputBytes = "caller-output-sentinel".toByteArray()
        val replacementBytes = "caller-still-owns-output".toByteArray()
        val inputFile = newStagingFile("input", inputBytes)
        val outputFile = newStagingFile("output", outputBytes)

        try {
            ParcelFileDescriptor.open(
                inputFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { input ->
                ParcelFileDescriptor.open(
                    outputFile,
                    ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    val callback = RecordingCallback()
                    withCompilerService { _, _, compiler ->
                        val ticket = compiler.startCompilation(
                            validRequest("valid-staging"),
                            input,
                            output,
                            callback,
                        )

                        assertRejected(
                            ticket = ticket,
                            requestId = "valid-staging",
                            errorCode = TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
                        )
                    }

                    callback.assertNoCallback()
                    assertTrue(input.fileDescriptor.valid())
                    assertTrue(output.fileDescriptor.valid())
                    assertDescriptorContent(input, inputBytes)
                    assertDescriptorContent(output, outputBytes)

                    Os.ftruncate(output.fileDescriptor, 0L)
                    Os.lseek(output.fileDescriptor, 0L, OsConstants.SEEK_SET)
                    assertEquals(
                        replacementBytes.size,
                        Os.write(
                            output.fileDescriptor,
                            replacementBytes,
                            0,
                            replacementBytes.size,
                        ),
                    )
                    assertDescriptorContent(output, replacementBytes)
                }
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun pipeReadOnlyOutputAndSharedBackingFileAreRejectedWithoutCallbacks() {
        assertPipeInputRejected()
        assertReadOnlyOutputRejected()
        assertSharedBackingFileRejected()
    }

    private fun assertPipeInputRejected() {
        val outputFile = newStagingFile("pipe-output", ByteArray(0))
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            pipe[1].close()
            ParcelFileDescriptor.open(
                outputFile,
                ParcelFileDescriptor.MODE_READ_WRITE,
            ).use { output ->
                val callback = RecordingCallback()
                withCompilerService { _, _, compiler ->
                    val ticket = compiler.startCompilation(
                        validRequest("pipe-input"),
                        pipe[0],
                        output,
                        callback,
                    )
                    assertRejected(
                        ticket = ticket,
                        requestId = "pipe-input",
                        errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    )
                }
                callback.assertNoCallback()
                assertTrue(pipe[0].fileDescriptor.valid())
                assertTrue(output.fileDescriptor.valid())
            }
        } finally {
            pipe.forEach { descriptor -> runCatching { descriptor.close() } }
            outputFile.delete()
        }
    }

    private fun assertReadOnlyOutputRejected() {
        val inputFile = newStagingFile("readonly-input", "input".toByteArray())
        val outputFile = newStagingFile("readonly-output", "output".toByteArray())
        try {
            ParcelFileDescriptor.open(
                inputFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { input ->
                ParcelFileDescriptor.open(
                    outputFile,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                ).use { output ->
                    val callback = RecordingCallback()
                    withCompilerService { _, _, compiler ->
                        val ticket = compiler.startCompilation(
                            validRequest("readonly-output"),
                            input,
                            output,
                            callback,
                        )
                        assertRejected(
                            ticket = ticket,
                            requestId = "readonly-output",
                            errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        )
                    }
                    callback.assertNoCallback()
                    assertTrue(input.fileDescriptor.valid())
                    assertTrue(output.fileDescriptor.valid())
                }
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun assertSharedBackingFileRejected() {
        val archiveFile = newStagingFile("shared-archive", "archive".toByteArray())
        try {
            ParcelFileDescriptor.open(
                archiveFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { input ->
                ParcelFileDescriptor.open(
                    archiveFile,
                    ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    val callback = RecordingCallback()
                    withCompilerService { _, _, compiler ->
                        val ticket = compiler.startCompilation(
                            validRequest("shared-backing"),
                            input,
                            output,
                            callback,
                        )
                        assertRejected(
                            ticket = ticket,
                            requestId = "shared-backing",
                            errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        )
                    }
                    callback.assertNoCallback()
                    assertTrue(input.fileDescriptor.valid())
                    assertTrue(output.fileDescriptor.valid())
                }
            }
        } finally {
            archiveFile.delete()
        }
    }

    private fun validRequest(requestId: String) = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putInt(
            TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_PROJECT_ID, "instrumentation-project")
        putString(
            TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
            "autojs://source/instrumentation/main.ts",
        )
        putString(
            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            TypeScriptCompilerContract.PROFILE_RHINO,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILATION_MODE,
            TypeScriptCompilerContract.MODE_DEVELOPMENT,
        )
        putString(TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON, "{}")
        putLong(
            TypeScriptCompilerContract.KEY_TIMEOUT_MS,
            TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
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
        putLong(
            TypeScriptCompilerContract.KEY_TIMEOUT_MS,
            timeoutMs,
        )
    }

    private fun assertRejected(
        ticket: Bundle,
        requestId: String,
        errorCode: String,
    ) {
        assertFalse(ticket.getBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, true))
        assertEquals(
            TypeScriptCompilerContract.VERSION,
            ticket.getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION),
        )
        assertEquals(
            TypeScriptCompilerContract.HASH,
            ticket.getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH),
        )
        assertEquals(requestId, ticket.getString(TypeScriptCompilerContract.KEY_REQUEST_ID))
        assertEquals(errorCode, ticket.getString(TypeScriptCompilerContract.KEY_ERROR_CODE))
    }

    private fun assertDescriptorContent(
        descriptor: ParcelFileDescriptor,
        expected: ByteArray,
    ) {
        Os.lseek(descriptor.fileDescriptor, 0L, OsConstants.SEEK_SET)
        val actual = ByteArray(expected.size)
        var offset = 0
        while (offset < actual.size) {
            val read = Os.read(
                descriptor.fileDescriptor,
                actual,
                offset,
                actual.size - offset,
            )
            if (read <= 0) break
            offset += read
        }
        assertEquals(expected.size, offset)
        assertArrayEquals(expected, actual)
    }

    private fun newStagingFile(
        prefix: String,
        content: ByteArray,
    ): File = File.createTempFile("r1-$prefix-", ".archive", context.cacheDir).apply {
        writeBytes(content)
    }

    @Suppress("DEPRECATION")
    private fun withCompilerService(
        block: (
            android.content.pm.ServiceInfo,
            IBinder,
            ITypeScriptCompilerPlugin,
        ) -> Unit,
    ) {
        val discoveryIntent = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(discoveryIntent, 0)
        assertEquals("Expected exactly one TypeScript compiler service.", 1, matches.size)
        val serviceInfo = matches.single().serviceInfo
        assertNotNull(serviceInfo)
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        val connection = AwaitingServiceConnection()
        var bound = false
        try {
            bound = context.bindService(
                Intent(discoveryIntent).setComponent(component),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            assertTrue("Failed to bind $component.", bound)
            val rawBinder = connection.awaitBinder()
            val compiler = ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder)
            assertNotNull(compiler)
            block(serviceInfo, rawBinder, compiler)
        } finally {
            if (bound) {
                context.unbindService(connection)
            }
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

    private class RecordingCallback : ITypeScriptCompilationCallback.Stub() {

        private val callbackSignal = CountDownLatch(1)
        private val progressCount = AtomicInteger()
        private val completionCount = AtomicInteger()

        override fun onProgress(progress: Bundle?) {
            progressCount.incrementAndGet()
            callbackSignal.countDown()
        }

        override fun onCompleted(result: Bundle?) {
            completionCount.incrementAndGet()
            callbackSignal.countDown()
        }

        fun assertNoCallback() {
            assertFalse(
                "R0 rejection unexpectedly delivered a callback.",
                callbackSignal.await(NO_CALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertEquals(0, progressCount.get())
            assertEquals(0, completionCount.get())
        }
    }

    private companion object {
        const val HOT_PROBE_COUNT = 100
        const val SERVICE_TIMEOUT_SECONDS = 5L
        const val NO_CALLBACK_WINDOW_MILLIS = 250L
        const val EXPECTED_DECLARATIONS_FINGERPRINT =
            "6ee5c0bce41694b8ca86430b82abfe4fbb60bf96321fba43e99d9810a44a1c1f"
        const val EXPECTED_DISTRIBUTION_FINGERPRINT =
            "3855b04b968db8a9529b60c96dd8b8ec94fa1ee040cac10a5ab569aa6e33f658"
    }
}
