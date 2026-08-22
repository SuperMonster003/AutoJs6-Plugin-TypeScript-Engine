package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validator
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2OutputFdCommitterInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun validatedPrivateInodeCommitsAtFixedOffsetThenSyncsSeeksAndCloses() {
        withStagedOutput("success") { staged, directory ->
            val output = File.createTempFile("r2-output-success-", ".zip", context.cacheDir)
            RandomAccessFile(output, "rw").use { caller ->
                caller.write(OUTPUT_SENTINEL)
                caller.seek(SHARED_OFFSET)
                val serviceDescriptor = ParcelFileDescriptor.dup(caller.fd)
                val rawServiceDescriptor = serviceDescriptor.fileDescriptor
                val churn = AtomicBoolean(true)
                val churnThread = Thread {
                    var offset = 0L
                    while (churn.get()) {
                        caller.seek(offset % OUTPUT_SENTINEL.size)
                        offset++
                    }
                }.apply {
                    name = "r2-output-offset-churn"
                    start()
                }

                val committed = try {
                    R2OutputFdCommitter.commit(
                        staged = staged,
                        outputArchive = serviceDescriptor,
                        faultInjector = { stage ->
                            if (stage == R2OutputCommitStage.BEFORE_FINAL_SEEK) {
                                churn.set(false)
                                churnThread.join(CHURN_JOIN_TIMEOUT_MS)
                                assertFalse(churnThread.isAlive)
                            }
                        },
                    )
                } finally {
                    churn.set(false)
                    churnThread.join(CHURN_JOIN_TIMEOUT_MS)
                }

                assertEquals(staged.summary, committed)
                assertFalse(rawServiceDescriptor.valid())
                assertEquals(
                    "Commit must leave the shared output offset at zero.",
                    0L,
                    caller.filePointer,
                )
                assertEquals(staged.summary.containerBytes, caller.length())
                val contents = ZipArchiveV1Validator.validateImmutableAndRead(
                    file = caller,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
                assertEquals(
                    setOf(
                        R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                        R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                        "maps/main.js.map",
                        "modules/main.js",
                    ),
                    contents.entries.keys,
                )
                assertTrue(staged.verifyUnchanged())
                assertTrue(
                    "Android output staging must remain unlinked while its inode is held.",
                    directory.listFiles().orEmpty().isEmpty(),
                )
            }
            assertTrue(output.delete() || !output.exists())
        }
    }

    @Test
    fun partialCopyFailureRollsBackToSyncedEmptyOutputBeforeClosing() {
        withStagedOutput("rollback") { staged, _ ->
            val output = File.createTempFile("r2-output-rollback-", ".zip", context.cacheDir)
            RandomAccessFile(output, "rw").use { caller ->
                caller.write(OUTPUT_SENTINEL)
                caller.seek(SHARED_OFFSET)
                val serviceDescriptor = ParcelFileDescriptor.dup(caller.fd)
                val rawServiceDescriptor = serviceDescriptor.fileDescriptor

                val failure = assertThrows(R2OutputCommitException::class.java) {
                    R2OutputFdCommitter.commit(
                        staged = staged,
                        outputArchive = serviceDescriptor,
                        faultInjector = { stage ->
                            if (stage == R2OutputCommitStage.AFTER_FIRST_WRITE) {
                                throw IllegalStateException("Injected partial-copy failure.")
                            }
                        },
                    )
                }

                assertFalse(failure is R2OutputCommitUnprovenException)
                assertFalse(rawServiceDescriptor.valid())
                assertEquals(0L, caller.length())
                assertEquals(0L, caller.filePointer)
                assertTrue(staged.verifyUnchanged())
            }
            assertTrue(output.delete() || !output.exists())
        }
    }

    @Test
    fun terminalGateStopRollsBackCompleteCopyBeforeDescriptorClose() {
        withStagedOutput("terminal-gate") { staged, _ ->
            val output = File.createTempFile(
                "r2-output-terminal-gate-",
                ".zip",
                context.cacheDir,
            )
            RandomAccessFile(output, "rw").use { caller ->
                caller.write(OUTPUT_SENTINEL)
                caller.seek(SHARED_OFFSET)
                val serviceDescriptor = ParcelFileDescriptor.dup(caller.fd)
                val rawServiceDescriptor = serviceDescriptor.fileDescriptor

                val failure = assertThrows(R2OutputCommitException::class.java) {
                    R2OutputFdCommitter.commit(
                        staged = staged,
                        outputArchive = serviceDescriptor,
                        terminalGate = {
                            throw R2OutputCommitException(
                                TypeScriptCompilerContract.ERROR_CANCELLED,
                                "Injected stop at the terminal gate.",
                            )
                        },
                    )
                }

                assertEquals(
                    TypeScriptCompilerContract.ERROR_CANCELLED,
                    failure.contractErrorCode,
                )
                assertFalse(failure is R2OutputCommitUnprovenException)
                assertFalse(rawServiceDescriptor.valid())
                assertEquals(0L, caller.length())
                assertEquals(0L, caller.filePointer)
                assertTrue(staged.verifyUnchanged())
            }
            assertTrue(output.delete() || !output.exists())
        }
    }

    @Test
    fun descriptorClosedBeforeRollbackPoisonsCommitProof() {
        withStagedOutput("unproven") { staged, _ ->
            val output = File.createTempFile("r2-output-unproven-", ".zip", context.cacheDir)
            RandomAccessFile(output, "rw").use { caller ->
                caller.write(OUTPUT_SENTINEL)
                caller.seek(SHARED_OFFSET)
                val serviceDescriptor = ParcelFileDescriptor.dup(caller.fd)
                val rawServiceDescriptor = serviceDescriptor.fileDescriptor

                assertThrows(R2OutputCommitUnprovenException::class.java) {
                    R2OutputFdCommitter.commit(
                        staged = staged,
                        outputArchive = serviceDescriptor,
                        faultInjector = { stage ->
                            if (stage == R2OutputCommitStage.BEFORE_FINAL_SEEK) {
                                serviceDescriptor.close()
                                throw IllegalStateException(
                                    "Injected close-before-final-seek failure.",
                                )
                            }
                        },
                    )
                }

                assertFalse(rawServiceDescriptor.valid())
                assertEquals(staged.summary.containerBytes, caller.length())
                assertEquals(SHARED_OFFSET, caller.filePointer)
            }
            assertTrue(output.delete() || !output.exists())
        }
    }

    @Test
    fun cancelAndFailureFinalizerClearsSyncsSeeksAndCloses() {
        val output = File.createTempFile("r2-output-clear-", ".zip", context.cacheDir)
        RandomAccessFile(output, "rw").use { caller ->
            caller.write(OUTPUT_SENTINEL)
            caller.seek(SHARED_OFFSET)
            val serviceDescriptor = ParcelFileDescriptor.dup(caller.fd)
            val rawServiceDescriptor = serviceDescriptor.fileDescriptor

            R2OutputFdCommitter.clear(serviceDescriptor)

            assertFalse(rawServiceDescriptor.valid())
            assertEquals(0L, caller.length())
            assertEquals(0L, caller.filePointer)
        }
        assertTrue(output.delete() || !output.exists())
    }

    @Test
    fun appendModeOutputIsRejectedBeforeAdmission() {
        val input = File.createTempFile("r2-output-append-input-", ".zip", context.cacheDir)
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val output = File.createTempFile("r2-output-append-output-", ".zip", context.cacheDir)
            .apply { writeBytes(OUTPUT_SENTINEL) }
        val inputDescriptor = ParcelFileDescriptor.open(
            input,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val outputDescriptor = ParcelFileDescriptor.open(
            output,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_APPEND,
        )
        try {
            val validation = ArchiveFileDescriptorValidator.validate(
                inputArchive = inputDescriptor,
                outputArchive = outputDescriptor,
            )
            assertTrue(validation.inputArchiveValid)
            assertFalse(validation.outputArchiveValid)
            assertFalse(validation.backingFilesDistinct)
            assertTrue(OUTPUT_SENTINEL.contentEquals(output.readBytes()))
        } finally {
            inputDescriptor.close()
            outputDescriptor.close()
            assertTrue(input.delete() || !input.exists())
            assertTrue(output.delete() || !output.exists())
        }
    }

    private fun withStagedOutput(
        label: String,
        block: (R2StagedOutputArchive, File) -> Unit,
    ) {
        val directory = File(
            context.getDir(PRIVATE_DIRECTORY, Context.MODE_PRIVATE),
            "$label-${UUID.randomUUID()}",
        )
        assertTrue(directory.mkdir())
        try {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = directory,
                authority = authority(),
                compilation = emittedCompilation(),
            ).use { staged ->
                block(staged, directory)
            }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.listFiles().orEmpty().forEach { file ->
                file.setWritable(true, true)
                file.delete()
            }
            assertTrue(directory.delete() || !directory.exists())
        }
    }

    private fun authority() = R2OutputArchiveAuthority(
        requestId = "r2-output-instrumentation",
        projectId = "r2-output-project",
        entrySourceUri = "autojs://source/output/main.ts",
        entryPath = "sources/main.ts",
        profile = R2ProjectProfile.PROVISIONAL_RHINO,
        profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
        compilerVersion = BuildConfig.TYPESCRIPT_VERSION,
        compilerDistributionFingerprint = sha256("distribution"),
        declarationsFingerprint = sha256("declarations"),
        runtimeCapabilitiesFingerprint =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO
                .runtimeCapabilitiesFingerprint,
        inputArchiveSha256 = sha256("input"),
        requestManifestSha256 = sha256("manifest"),
        inputArchiveSchemaRevision = R2InputArchiveSchemaV1.REVISION,
        inputQuotaRevision = R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION,
        callerInventoryFingerprint = sha256("caller inventory"),
        retainedInventoryFingerprint = sha256("retained inventory"),
        declarationProfileFingerprint = sha256("declaration profile"),
        declarationInventoryFingerprint = sha256("declaration inventory"),
        declarationFileCount = 1,
        declarationByteLength = 128,
        retainedFileCount = 2,
        retainedByteLength = 256,
        largestRetainedFileBytes = 128,
    )

    private fun emittedCompilation(): R2ProjectCompilation {
        val sourceMap =
            """{"version":3,"file":"main.js","sourceRoot":"","sources":["../sources/main.ts"],""" +
                """"names":[],"mappings":"AAAA"}"""
        return R2ProjectCompilation(
            compilerApi = "createIncrementalProgram",
            compilerVersion = BuildConfig.TYPESCRIPT_VERSION,
            targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
            profileSchemaRevision =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
            profileRevision =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
            configSchemaRevision =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.configSchemaRevision,
            configKind = R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
            effectiveOptions =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptions,
            diagnostics = emptyList(),
            outputs = listOf(
                output("/outputs/main.js", JS_TEXT),
                output("/outputs/main.js.map", sourceMap),
            ),
            emitSkipped = false,
        )
    }

    private fun output(
        path: String,
        text: String,
    ): R2ProjectOutput {
        val bytes = R2Utf8.encode(text, "Instrumentation output")
        return try {
            R2ProjectOutput(
                path = path,
                text = text,
                sha256 = R2Digests.sha256(bytes),
                sourcePaths = listOf("/sources/main.ts"),
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun sha256(value: String): String =
        R2Digests.sha256(value.toByteArray(StandardCharsets.UTF_8))

    private companion object {
        const val PRIVATE_DIRECTORY = "r2-output-committer-tests"
        const val SHARED_OFFSET = 7L
        const val CHURN_JOIN_TIMEOUT_MS = 2_000L
        const val JS_TEXT =
            "\"use strict\";\nexports.answer = 42;\n//# sourceMappingURL=main.js.map\n"
        val OUTPUT_SENTINEL = "caller-output-sentinel".toByteArray()
    }
}
