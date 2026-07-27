package io.github.supermonster003.autojs6.plugin.typescript

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.io.File
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1BinderMetadataAndFdOwnershipInstrumentationTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    @Test
    fun maliciousMetadataIsRejectedByTheRealRemoteServiceWithoutCallbacks() {
        withCompilerService { compiler ->
            metadataMutations().forEachIndexed { index, mutation ->
                val resources = mutableListOf<Closeable>()
                val inputFile = newStagingFile("metadata-input-$index", INPUT_BYTES)
                val outputFile = newStagingFile("metadata-output-$index", OUTPUT_BYTES)
                try {
                    ParcelFileDescriptor.open(
                        inputFile,
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    ).use { input ->
                        ParcelFileDescriptor.open(
                            outputFile,
                            ParcelFileDescriptor.MODE_READ_WRITE,
                        ).use { output ->
                            val requestId = "metadata-${mutation.name}-$index"
                            val request = validRequest(requestId)
                            mutation.apply(request, resources)
                            val callback = RecordingCallback()

                            val ticket = compiler.startCompilation(
                                request,
                                input,
                                output,
                                callback,
                            )

                            assertRejected(
                                label = mutation.name,
                                ticket = ticket,
                                requestId = if (mutation.name == "bundle-file-descriptor") {
                                    ""
                                } else {
                                    requestId
                                },
                            )
                            callback.assertNoCallback(mutation.name)
                            assertTrue(
                                "${mutation.name}: caller input descriptor was closed.",
                                input.fileDescriptor.valid(),
                            )
                            assertTrue(
                                "${mutation.name}: caller output descriptor was closed.",
                                output.fileDescriptor.valid(),
                            )
                        }
                    }
                } finally {
                    resources.forEach { resource -> runCatching { resource.close() } }
                    inputFile.delete()
                    outputFile.delete()
                }
            }
        }
    }

    @Test
    fun maliciousPrewarmMetadataIsRejectedWithoutRunningTheCompilerProbe() {
        withCompilerService { compiler ->
            metadataMutations().forEach { mutation ->
                val resources = mutableListOf<Closeable>()
                try {
                    val options = validPrewarmOptions()
                    mutation.apply(options, resources)

                    val result = compiler.prewarm(options)

                    assertEquals(
                        mutation.name,
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        result.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
                    )
                } finally {
                    resources.forEach { resource -> runCatching { resource.close() } }
                }
            }
        }
    }

    @Test
    fun unknownLegalStringArrayRemainsAppendOnlyCompatible() {
        val inputFile = newStagingFile("string-array-input", INPUT_BYTES)
        val outputFile = newStagingFile("string-array-output", OUTPUT_BYTES)
        try {
            ParcelFileDescriptor.open(
                inputFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { input ->
                ParcelFileDescriptor.open(
                    outputFile,
                    ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    withCompilerService { compiler ->
                        val requestId = "unknown-legal-string-array"
                        val request = validRequest(requestId).apply {
                            putStringArray(
                                "futureOptionalMetadata",
                                arrayOf("alpha", null, "omega"),
                            )
                        }
                        val callback = RecordingCallback()

                        val ticket = compiler.startCompilation(
                            request,
                            input,
                            output,
                            callback,
                        )

                        assertRejected(
                            label = requestId,
                            ticket = ticket,
                            requestId = requestId,
                            errorCode = TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
                        )
                        callback.assertNoCallback(requestId)
                        assertTrue(input.fileDescriptor.valid())
                        assertTrue(output.fileDescriptor.valid())
                    }
                }
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    @Test
    fun repeatedEmbeddedBundleFileDescriptorsAreRejectedAndReleasedBeforeUnmarshal() {
        val inputFile = newStagingFile("embedded-input", INPUT_BYTES)
        val outputFile = newStagingFile("embedded-output", OUTPUT_BYTES)
        val embeddedFile = newStagingFile("embedded-metadata", "embedded".toByteArray())
        try {
            ParcelFileDescriptor.open(
                inputFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { input ->
                ParcelFileDescriptor.open(
                    outputFile,
                    ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    withCompilerService { compiler ->
                        val compilerPid = awaitCompilerProcessPid()
                        val callback = RecordingCallback()
                        repeat(FD_REJECTION_REPEAT_COUNT) { index ->
                            ParcelFileDescriptor.open(
                                embeddedFile,
                                ParcelFileDescriptor.MODE_READ_ONLY,
                            ).use { embedded ->
                                val request = validRequest("embedded-fd-$index").apply {
                                    putParcelable("unexpectedMetadata", embedded)
                                }
                                val ticket = compiler.startCompilation(
                                    request,
                                    input,
                                    output,
                                    callback,
                                )
                                assertRejected(
                                    label = "embedded-fd-$index",
                                    ticket = ticket,
                                    requestId = "",
                                )
                                assertTrue(embedded.fileDescriptor.valid())
                            }
                            if ((index + 1) % FD_INSPECTION_INTERVAL == 0) {
                                awaitNoRemoteStagingTargets(
                                    compilerPid,
                                    inputFile,
                                    outputFile,
                                    embeddedFile,
                                )
                            }
                        }

                        callback.assertNoCallback("repeated embedded Bundle FDs")
                        awaitNoRemoteStagingTargets(
                            compilerPid,
                            inputFile,
                            outputFile,
                            embeddedFile,
                        )
                        assertTrue(input.fileDescriptor.valid())
                        assertTrue(output.fileDescriptor.valid())
                        assertDescriptorContent(input, INPUT_BYTES)
                        assertDescriptorContent(output, OUTPUT_BYTES)
                    }
                }
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
            embeddedFile.delete()
        }
    }

    @Test
    fun repeatedPrewarmAndCancellationBundleFileDescriptorsAreReleased() {
        val embeddedFile = newStagingFile("embedded-control", "embedded".toByteArray())
        try {
            withCompilerService { compiler ->
                val compilerPid = awaitCompilerProcessPid()
                repeat(FD_REJECTION_REPEAT_COUNT) { index ->
                    ParcelFileDescriptor.open(
                        embeddedFile,
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    ).use { embedded ->
                        val options = validPrewarmOptions().apply {
                            putParcelable("unexpectedMetadata", embedded)
                        }
                        val result = compiler.prewarm(options)
                        assertEquals(
                            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            result.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
                        )
                    }
                    ParcelFileDescriptor.open(
                        embeddedFile,
                        ParcelFileDescriptor.MODE_READ_ONLY,
                    ).use { embedded ->
                        val cancellation = Bundle().apply {
                            putParcelable("unexpectedMetadata", embedded)
                        }
                        assertFalse(compiler.cancelCompilation(cancellation))
                    }
                    if ((index + 1) % FD_INSPECTION_INTERVAL == 0) {
                        awaitNoRemoteStagingTargets(compilerPid, embeddedFile)
                    }
                }
                awaitNoRemoteStagingTargets(compilerPid, embeddedFile)
            }
        } finally {
            embeddedFile.delete()
        }
    }

    @Test
    fun repeatedR0RejectsLeaveNoRemoteStagingFileDescriptors() {
        val inputFile = newStagingFile("ownership-input", INPUT_BYTES)
        val outputFile = newStagingFile("ownership-output", OUTPUT_BYTES)
        try {
            ParcelFileDescriptor.open(
                inputFile,
                ParcelFileDescriptor.MODE_READ_ONLY,
            ).use { input ->
                ParcelFileDescriptor.open(
                    outputFile,
                    ParcelFileDescriptor.MODE_READ_WRITE,
                ).use { output ->
                    withCompilerService { compiler ->
                        val compilerPid = awaitCompilerProcessPid()
                        val initialTargets = remoteFileDescriptorTargets(compilerPid)
                        if (initialTargets == null) {
                            Log.w(
                                TAG,
                                "Skipping direct remote FD ownership assertion because neither " +
                                    "same-UID /proc nor run-as inspection is available.",
                            )
                            assumeTrue(
                                "Device policy prevents inspection of the compiler process FD table.",
                                false,
                            )
                        }
                        requireNotNull(initialTargets)
                        assertTrue(
                            "Remote FD inspection returned no descriptors for a live service.",
                            initialTargets.isNotEmpty(),
                        )
                        assertNoStagingTargets(initialTargets, inputFile, outputFile)

                        val callback = RecordingCallback()
                        repeat(FD_REJECTION_REPEAT_COUNT) { index ->
                            val requestId = "fd-ownership-$index"
                            val ticket = compiler.startCompilation(
                                validRequest(requestId),
                                input,
                                output,
                                callback,
                            )
                            assertRejected(
                                label = requestId,
                                ticket = ticket,
                                requestId = requestId,
                                errorCode = TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
                            )
                            if ((index + 1) % FD_INSPECTION_INTERVAL == 0) {
                                awaitNoRemoteStagingTargets(
                                    compilerPid,
                                    inputFile,
                                    outputFile,
                                )
                            }
                        }

                        callback.assertNoCallback("repeated R0 rejects")
                        awaitNoRemoteStagingTargets(
                            compilerPid,
                            inputFile,
                            outputFile,
                        )
                        assertTrue(input.fileDescriptor.valid())
                        assertTrue(output.fileDescriptor.valid())
                        assertDescriptorContent(input, INPUT_BYTES)
                        assertDescriptorContent(output, OUTPUT_BYTES)
                    }
                }
            }
        } finally {
            inputFile.delete()
            outputFile.delete()
        }
    }

    private fun metadataMutations(): List<MetadataMutation> = listOf(
        MetadataMutation("nested-bundle") { metadata, _ ->
            metadata.putBundle("unexpectedMetadata", Bundle())
        },
        MetadataMutation("binder") { metadata, _ ->
            metadata.putBinder("unexpectedMetadata", Binder())
        },
        MetadataMutation("parcelable") { metadata, _ ->
            metadata.putParcelable("unexpectedMetadata", Intent())
        },
        MetadataMutation("serializable") { metadata, _ ->
            metadata.putSerializable("unexpectedMetadata", Date(0L))
        },
        MetadataMutation("null-value") { metadata, _ ->
            metadata.putString("unexpectedMetadata", null)
        },
        MetadataMutation("empty-parcelable-array") { metadata, _ ->
            metadata.putParcelableArray(
                "unexpectedMetadata",
                emptyArray<Parcelable>(),
            )
        },
        MetadataMutation("null-parcelable-array") { metadata, _ ->
            metadata.putParcelableArray(
                "unexpectedMetadata",
                arrayOfNulls<Parcelable>(2),
            )
        },
        MetadataMutation("bundle-file-descriptor") { metadata, resources ->
            val file = newStagingFile("embedded-fd", "embedded".toByteArray())
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            resources += Closeable {
                runCatching { descriptor.close() }
                file.delete()
            }
            metadata.putParcelable("unexpectedMetadata", descriptor)
        },
        MetadataMutation("oversized") { metadata, _ ->
            metadata.putString(
                "unexpectedMetadata",
                "x".repeat(TypeScriptCompilerContract.MAX_METADATA_BYTES),
            )
        },
    )

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
        putString(TypeScriptCompilerContract.KEY_PROJECT_ID, "metadata-test-project")
        putString(
            TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
            "autojs://source/metadata/main.ts",
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

    private fun assertRejected(
        label: String,
        ticket: Bundle,
        requestId: String,
        errorCode: String = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
    ) {
        assertFalse(label, ticket.getBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, true))
        assertEquals(
            label,
            TypeScriptCompilerContract.VERSION,
            ticket.getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION),
        )
        assertEquals(
            label,
            TypeScriptCompilerContract.HASH,
            ticket.getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH),
        )
        assertEquals(
            label,
            requestId,
            ticket.getString(TypeScriptCompilerContract.KEY_REQUEST_ID),
        )
        assertEquals(
            label,
            errorCode,
            ticket.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
        )
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

    private fun assertNoStagingTargets(
        targets: List<String>,
        vararg stagingFiles: File,
    ) {
        stagingFiles.forEach { stagingFile ->
            assertFalse(
                "Compiler process retained ${stagingFile.name}: $targets",
                targets.any { target -> stagingFile.name in target },
            )
        }
    }

    private fun awaitNoRemoteStagingTargets(
        pid: Int,
        vararg stagingFiles: File,
    ) {
        var lastTargets: List<String>? = null
        repeat(FD_RELEASE_LOOKUP_ATTEMPTS) {
            val targets = requireNotNull(remoteFileDescriptorTargets(pid)) {
                "Remote FD inspection became unavailable while the compiler service was alive."
            }
            lastTargets = targets
            if (
                stagingFiles.none { stagingFile ->
                    targets.any { target -> stagingFile.name in target }
                }
            ) {
                return
            }
            Thread.sleep(FD_RELEASE_LOOKUP_INTERVAL_MILLIS)
        }
        assertNoStagingTargets(requireNotNull(lastTargets), *stagingFiles)
    }

    private fun awaitCompilerProcessPid(): Int {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val expectedName = "${context.packageName}:typescript_compiler"
        repeat(PROCESS_LOOKUP_ATTEMPTS) {
            activityManager.runningAppProcesses
                ?.firstOrNull { process -> process.processName == expectedName }
                ?.let { process -> return process.pid }
            Thread.sleep(PROCESS_LOOKUP_INTERVAL_MILLIS)
        }
        throw AssertionError("Could not find the live compiler process $expectedName.")
    }

    private fun remoteFileDescriptorTargets(pid: Int): List<String>? {
        val direct = runCatching {
            File("/proc/$pid/fd")
                .listFiles()
                ?.mapNotNull { descriptor ->
                    runCatching { Os.readlink(descriptor.path) }.getOrNull()
                }
                ?.takeIf { targets -> targets.isNotEmpty() }
        }.getOrNull()
        if (direct != null) return direct

        val command =
            "run-as ${context.packageName} ls -l /proc/$pid/fd 2>&1"
        val output = runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command),
            ).bufferedReader().use { reader -> reader.readText() }
        }.getOrNull() ?: return null
        return output.lineSequence()
            .mapNotNull { line ->
                line.substringAfter(" -> ", missingDelimiterValue = "")
                    .takeIf(String::isNotEmpty)
            }
            .toList()
            .takeIf { targets -> targets.isNotEmpty() }
    }

    private fun newStagingFile(
        prefix: String,
        content: ByteArray,
    ): File = File.createTempFile("r1-$prefix-", ".archive", context.cacheDir).apply {
        writeBytes(content)
    }

    private fun withCompilerService(
        block: (ITypeScriptCompilerPlugin) -> Unit,
    ) {
        val connection = AwaitingServiceConnection()
        var bound = false
        try {
            bound = context.bindService(
                Intent(context, TypeScriptCompilerPluginService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            )
            assertTrue("Failed to bind the compiler service.", bound)
            val rawBinder = connection.awaitBinder()
            assertTrue(rawBinder.pingBinder())
            assertEquals(
                "The metadata gate must run in the remote compiler process.",
                null,
                rawBinder.queryLocalInterface(ITypeScriptCompilerPlugin.DESCRIPTOR),
            )
            val compiler = ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder)
            assertNotNull(compiler)
            block(requireNotNull(compiler))
        } finally {
            if (bound) {
                context.unbindService(connection)
            }
        }
    }

    private data class MetadataMutation(
        val name: String,
        val apply: (Bundle, MutableList<Closeable>) -> Unit,
    )

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

        fun assertNoCallback(label: String) {
            assertFalse(
                "$label unexpectedly delivered a callback.",
                callbackSignal.await(NO_CALLBACK_WINDOW_MILLIS, TimeUnit.MILLISECONDS),
            )
            assertEquals(label, 0, progressCount.get())
            assertEquals(label, 0, completionCount.get())
        }
    }

    private companion object {
        val INPUT_BYTES = "r1-fd-ownership-input".toByteArray()
        val OUTPUT_BYTES = "r1-fd-ownership-output".toByteArray()
        const val FD_INSPECTION_INTERVAL = 10
        const val FD_REJECTION_REPEAT_COUNT = 100
        const val FD_RELEASE_LOOKUP_ATTEMPTS = 40
        const val FD_RELEASE_LOOKUP_INTERVAL_MILLIS = 25L
        const val NO_CALLBACK_WINDOW_MILLIS = 250L
        const val PROCESS_LOOKUP_ATTEMPTS = 40
        const val PROCESS_LOOKUP_INTERVAL_MILLIS = 50L
        const val SERVICE_TIMEOUT_SECONDS = 5L
        const val TAG = "R1BinderMetadataFd"
    }
}
