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
                TypeScriptCompilerContract.BACKEND_UNINITIALIZED,
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
        const val SERVICE_TIMEOUT_SECONDS = 5L
        const val NO_CALLBACK_WINDOW_MILLIS = 250L
    }
}
