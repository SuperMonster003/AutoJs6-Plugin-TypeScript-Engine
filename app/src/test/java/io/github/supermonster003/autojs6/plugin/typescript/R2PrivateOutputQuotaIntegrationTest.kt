package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validator
import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class R2PrivateOutputQuotaIntegrationTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun exactPerArtifactBoundariesAreAcceptedAfterRewrite() {
        val privateDirectory = temporaryFolder.newFolder("packaged-exact-boundary")
        val sourceMap = sourceMapOfSize(
            file = "main.js",
            source = "../sources/main.ts",
            targetBytes =
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES.toInt(),
        )
        val compilerJavaScript = compilerJavaScriptOfSize(
            file = "main.js",
            targetBytes =
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES.toInt() -
                    ROOT_REWRITE_DELTA_BYTES,
        )
        val compilation = compilation(
            listOf(
                output("/outputs/main.js", compilerJavaScript, "/sources/main.ts"),
                output("/outputs/main.js.map", sourceMap, "/sources/main.ts"),
            ),
        )

        R2PrivateOutputArchive.stageForTesting(
            privateDirectory = privateDirectory,
            authority = authority(),
            compilation = compilation,
        ).use { staged ->
            val contents = staged.withImmutableReader { reader ->
                ZipArchiveV1Validator.validateImmutableAndRead(
                    file = reader,
                    limits = ZipArchiveV1Limits.OUTPUT,
                )
            }
            val javascriptBytes = requireNotNull(contents.entries["modules/main.js"]).size.toLong()
            val sourceMapBytes = requireNotNull(contents.entries["maps/main.js.map"]).size.toLong()
            assertEquals(
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
                javascriptBytes,
            )
            assertEquals(
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
                sourceMapBytes,
            )
            assertEquals(
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES +
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
                javascriptBytes + sourceMapBytes,
            )
        }
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun rewriteThatPushesJavaScriptPastPerArtifactLimitFailsBeforeStaging() {
        val privateDirectory = temporaryFolder.newFolder("packaged-js-plus-one")
        val compilerJavaScript = compilerJavaScriptOfSize(
            file = "main.js",
            targetBytes =
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES.toInt(),
        )
        val sourceMap = sourceMap("main.js", "../sources/main.ts")
        val outputs = listOf(
            output("/outputs/main.js", compilerJavaScript, "/sources/main.ts"),
            output("/outputs/main.js.map", sourceMap, "/sources/main.ts"),
        )
        assertRawWorkerPrefilterEligible(outputs)

        val failure = assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority(),
                compilation = compilation(outputs),
            )
        }

        assertEquals(TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT, failure.contractErrorCode)
        assertTrue(
            failure.message,
            failure.message.orEmpty().contains("javascript"),
        )
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun rewriteThatPushesAggregatePastLimitFailsBeforeStaging() {
        val privateDirectory = temporaryFolder.newFolder("packaged-total-plus-one")
        val names = List(EMITTED_MODULE_COUNT) { index ->
            if (index == 0) "main" else "file${index.toString().padStart(3, '0')}"
        }
        val roots = names.map { name -> "/sources/$name.ts" }
        val maps = names.mapIndexed { index, name ->
            output(
                path = "/outputs/$name.js.map",
                text = sourceMap("$name.js", "../sources/$name.ts"),
                sourcePath = roots[index],
            )
        }
        val minimumJavaScript = names.map { name -> compilerJavaScriptOfSize("$name.js", null) }
        val minimumRawBytes = maps.sumOf { outputBytes(it) } +
            minimumJavaScript.sumOf { text -> utf8Bytes(text) }
        val rewriteBytes = EMITTED_MODULE_COUNT * ROOT_REWRITE_DELTA_BYTES
        val targetRawBytes =
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES - rewriteBytes + 1L
        var fillerBytes = targetRawBytes - minimumRawBytes
        require(fillerBytes >= 0L)
        val modules = names.mapIndexed { index, name ->
            val remainingModules = names.size - index
            val share = (fillerBytes / remainingModules).toInt()
            fillerBytes -= share
            output(
                path = "/outputs/$name.js",
                text = compilerJavaScriptOfSize(
                    "$name.js",
                    utf8Bytes(minimumJavaScript[index]).toInt() + share,
                ),
                sourcePath = roots[index],
            )
        }
        check(fillerBytes == 0L)
        val outputs = modules + maps
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
            outputs.size,
        )
        assertEquals(targetRawBytes, outputs.sumOf(::outputBytes))
        assertRawWorkerPrefilterEligible(outputs)

        val failure = assertThrows(R2OutputArchiveException::class.java) {
            R2PrivateOutputArchive.stageForTesting(
                privateDirectory = privateDirectory,
                authority = authority(),
                compilation = compilation(outputs),
                acceptedRootPaths = roots.toSet(),
            )
        }

        assertEquals(TypeScriptCompilerContract.ERROR_OUTPUT_TRANSPORT, failure.contractErrorCode)
        assertTrue(failure.message.orEmpty().contains("total_bytes"))
        assertTrue(privateDirectory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun nestedRewriteExpansionIsMeasuredByTheFinalQuotaAuthority() {
        val compilerPath = "/outputs/a/b/c/main.js"
        val archivePath = "modules/a/b/c/main.js"
        val compilerJavaScript = compilerJavaScriptOfSize(
            file = "main.js",
            targetBytes =
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES.toInt(),
        )
        val packaged = R2PrivateOutputArchive.rewriteSourceMappingUrlForOrdinaryQuota(
            compilerPath = compilerPath,
            moduleArchivePath = archivePath,
            source = compilerJavaScript,
        )
        val rawBytes = utf8Bytes(compilerJavaScript)
        val packagedBytes = utf8Bytes(packaged)

        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
            rawBytes,
        )
        assertTrue(packagedBytes > rawBytes)
        assertEquals(
            R2OrdinaryOutputQuotaDimension.JAVASCRIPT_BYTES,
            R2OrdinaryOutputQuota.admission(
                listOf(
                    R2OrdinaryOutputArtifact(
                        R2OrdinaryOutputArtifactKind.JAVASCRIPT,
                        packagedBytes,
                    ),
                ),
            ).exceededDimension,
        )
    }

    private fun assertRawWorkerPrefilterEligible(outputs: List<R2ProjectOutput>) {
        assertTrue(
            outputs.size <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
        )
        assertTrue(
            outputs.all { output ->
                outputBytes(output) <=
                    if (output.isSourceMap) {
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES
                    } else {
                        TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES
                    }
            },
        )
        assertTrue(
            outputs.sumOf(::outputBytes) <=
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
        )
    }

    private fun authority() = R2OutputArchiveAuthority(
        requestId = "request-output-quota",
        projectId = "project-output-quota",
        entrySourceUri = "autojs://source/output/main.ts",
        entryPath = "sources/main.ts",
        profile = R2ProjectProfile.PROVISIONAL_RHINO,
        profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
        compilerVersion = "6.0.3",
        compilerDistributionFingerprint = sha256("distribution"),
        declarationsFingerprint = sha256("declarations"),
        runtimeCapabilitiesFingerprint =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.runtimeCapabilitiesFingerprint,
        inputArchiveSha256 = sha256("input archive"),
        requestManifestSha256 = sha256("request manifest"),
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

    private fun compilation(outputs: List<R2ProjectOutput>) = R2ProjectCompilation(
        compilerApi = "createIncrementalProgram",
        compilerVersion = "6.0.3",
        targetProfile = "rhino",
        profileSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
        profileRevision = R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        configSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configSchemaRevision,
        configKind = R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
        effectiveOptions = R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptions,
        diagnostics = emptyList(),
        outputs = outputs,
        emitSkipped = false,
    )

    private fun output(
        path: String,
        text: String,
        sourcePath: String,
    ): R2ProjectOutput {
        val bytes = R2Utf8.encode(text, "D2 test output")
        return try {
            R2ProjectOutput(
                path = path,
                text = text,
                sha256 = R2Digests.sha256(bytes),
                sourcePaths = listOf(sourcePath),
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun compilerJavaScriptOfSize(
        file: String,
        targetBytes: Int?,
    ): String {
        val suffix = "\n//# sourceMappingURL=$file.map\n"
        val fillerBytes = targetBytes?.let { it - utf8Bytes(suffix).toInt() } ?: 0
        require(fillerBytes >= 0)
        return "x".repeat(fillerBytes) + suffix
    }

    private fun sourceMapOfSize(
        file: String,
        source: String,
        targetBytes: Int,
    ): String {
        val prefix =
            "{\"version\":3,\"file\":\"$file\",\"sourceRoot\":\"\",\"sources\":[\"$source\"]," +
                "\"names\":[],\"mappings\":\""
        val suffix = "\"}"
        val fillerBytes = targetBytes - utf8Bytes(prefix).toInt() - utf8Bytes(suffix).toInt()
        require(fillerBytes >= 0)
        return prefix + "A".repeat(fillerBytes) + suffix
    }

    private fun sourceMap(file: String, source: String): String =
        sourceMapOfSize(file, source, MINIMUM_SOURCE_MAP_BYTES)

    private fun outputBytes(output: R2ProjectOutput): Long = utf8Bytes(output.text)

    private fun utf8Bytes(text: String): Long =
        R2Utf8.encodedByteCount(text, "D2 test text")

    private fun sha256(text: String): String =
        R2Digests.sha256(text.toByteArray(StandardCharsets.UTF_8))

    private companion object {
        const val EMITTED_MODULE_COUNT = 256
        const val MINIMUM_SOURCE_MAP_BYTES = 128
        const val ROOT_REWRITE_DELTA_BYTES = 8
    }
}
