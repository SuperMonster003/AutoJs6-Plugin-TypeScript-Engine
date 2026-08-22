package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class R2OutputArchiveSchemaV1Test {

    @Test
    fun formalIdentityIsSingleRevisionOneAndIndependentFromZipTransport() {
        assertEquals(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA, R2OutputArchiveSchemaV1.SCHEMA)
        assertEquals(1, R2OutputArchiveSchemaV1.REVISION)
        assertEquals(
            TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION,
            R2OutputArchiveSchemaV1.OUTPUT_QUOTA_REVISION,
        )
        assertEquals("compilation-manifest.json", R2OutputArchiveSchemaV1.MANIFEST_PATH)
        assertEquals("diagnostics.json", R2OutputArchiveSchemaV1.DIAGNOSTICS_PATH)
        R2OutputArchiveSchemaV1.requireSupportedIdentity(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA,
            1,
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2OutputArchiveSchemaV1.requireSupportedIdentity(
                "autojs6.typescript.compilation.provisional",
                2,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2OutputArchiveSchemaV1.requireSupportedIdentity(
                TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA,
                TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION + 1,
            )
        }
    }

    @Test
    fun emittedOutcomeBindsDistinctModuleAndArtifactInventories() {
        val files = emittedFiles()
        R2OutputArchiveSchemaV1.requireOutcomeInventory(
            outcome = TypeScriptCompilerContract.OUTCOME_EMITTED,
            emitSkipped = false,
            diagnosticCount = 0,
            emittedModuleCount = 1,
            files = files,
        )
        val artifactFingerprint =
            R2OutputArchiveSchemaV1.artifactInventoryFingerprint(files)
        val moduleFingerprint =
            R2OutputArchiveSchemaV1.moduleInventoryFingerprint(files)
        assertNotEquals(artifactFingerprint, moduleFingerprint)
        assertNotEquals(
            artifactFingerprint,
            R2OutputArchiveSchemaV1.artifactInventoryFingerprint(
                files.map { file ->
                    if (file.kind == R2OutputArchiveSchemaV1.KIND_JAVASCRIPT) {
                        file.copy(sha256 = sha256("changed javascript"))
                    } else {
                        file
                    }
                },
            ),
        )
        assertNotEquals(
            moduleFingerprint,
            R2OutputArchiveSchemaV1.moduleInventoryFingerprint(
                files.map { file ->
                    if (file.kind == R2OutputArchiveSchemaV1.KIND_JAVASCRIPT) {
                        file.copy(sourcePaths = listOf("sources/other.ts"))
                    } else {
                        file
                    }
                },
            ),
        )
    }

    @Test
    fun diagnosticsOutcomeHasOneDocumentAndNoExecutableArtifacts() {
        val diagnostics = listOf(diagnosticsFile())
        R2OutputArchiveSchemaV1.requireOutcomeInventory(
            outcome = TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
            emitSkipped = true,
            diagnosticCount = 1,
            emittedModuleCount = 0,
            files = diagnostics,
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2OutputArchiveSchemaV1.requireOutcomeInventory(
                outcome = TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                emitSkipped = true,
                diagnosticCount = 1,
                emittedModuleCount = 1,
                files = emittedFiles(),
            )
        }
    }

    @Test
    fun unknownKindsNonCanonicalOrderAndCaseFoldCollisionsFailClosed() {
        val files = emittedFiles()
        assertThrows(IllegalArgumentException::class.java) {
            R2OutputArchiveSchemaV1.requireArtifactInventory(files.reversed())
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2OutputArchiveSchemaV1.requireArtifactInventory(
                listOf(
                    diagnosticsFile(),
                    files[1],
                    files[2],
                    files[2].copy(path = "modules/MAIN.js"),
                ).sortedBy(R2OutputArchiveFile::path),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2OutputArchiveSchemaV1.requireArtifactInventory(
                listOf(diagnosticsFile().copy(kind = "unknown")),
            )
        }
    }

    private fun emittedFiles(): List<R2OutputArchiveFile> = listOf(
        diagnosticsFile(),
        R2OutputArchiveFile(
            path = "maps/main.js.map",
            kind = R2OutputArchiveSchemaV1.KIND_SOURCE_MAP,
            size = 96,
            sha256 = sha256("source map"),
            sourcePaths = listOf("sources/main.ts"),
        ),
        R2OutputArchiveFile(
            path = "modules/main.js",
            kind = R2OutputArchiveSchemaV1.KIND_JAVASCRIPT,
            size = 64,
            sha256 = sha256("javascript"),
            sourcePaths = listOf("sources/main.ts"),
        ),
    )

    private fun diagnosticsFile() = R2OutputArchiveFile(
        path = R2OutputArchiveSchemaV1.DIAGNOSTICS_PATH,
        kind = R2OutputArchiveSchemaV1.KIND_DIAGNOSTICS,
        size = 32,
        sha256 = sha256("diagnostics"),
        sourcePaths = emptyList(),
    )

    private fun sha256(text: String): String =
        R2Digests.sha256(text.toByteArray(StandardCharsets.UTF_8))
}
