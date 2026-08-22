package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Canonical authority for the frozen R2 output-archive schema revision 1. */
internal object R2OutputArchiveSchemaV1 {

    const val SCHEMA = TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA
    const val REVISION = TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION
    const val OUTPUT_QUOTA_REVISION =
        TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION
    const val MANIFEST_PATH = TypeScriptR2Contract.OUTPUT_ARCHIVE_MANIFEST_PATH
    const val DIAGNOSTICS_PATH = TypeScriptR2Contract.OUTPUT_ARCHIVE_DIAGNOSTICS_PATH

    const val KIND_DIAGNOSTICS = "diagnostics"
    const val KIND_JAVASCRIPT = "javascript"
    const val KIND_SOURCE_MAP = "source_map"

    private const val ARTIFACT_INVENTORY_PREFIX =
        "autojs6.typescript.output-artifact-inventory.v1\n"
    private const val MODULE_INVENTORY_PREFIX =
        "autojs6.typescript.output-module-inventory.v1\n"

    fun requireSupportedIdentity(
        schema: String,
        revision: Int,
    ) {
        require(schema == SCHEMA && revision == REVISION) {
            "Output archive schema identity is not supported."
        }
    }

    /**
     * Validates the exact non-manifest inventory encoded by a formal output manifest.
     *
     * The manifest itself is deliberately excluded to avoid a self-referential digest. Its bytes
     * remain independently bound by the completion Bundle's outputManifestSha256 value.
     */
    fun requireArtifactInventory(files: List<R2OutputArchiveFile>) {
        require(files.isNotEmpty()) { "Output artifact inventory is empty." }
        require(files == files.sortedBy(R2OutputArchiveFile::path)) {
            "Output artifact inventory is not in canonical path order."
        }
        val exactPaths = HashSet<String>(files.size)
        val foldedPaths = HashSet<String>(files.size)
        files.forEach { file ->
            require(
                file.path.isCanonicalOutputArchivePath() &&
                    exactPaths.add(file.path) &&
                    foldedPaths.add(file.path.asciiCaseFold()),
            ) {
                "Output artifact path is non-canonical or colliding."
            }
            require(file.size >= 0L) { "Output artifact size is negative." }
            R2Digests.requireLowerSha256(file.sha256, "Output artifact SHA-256")
            require(
                file.sourcePaths == file.sourcePaths.sorted() &&
                    file.sourcePaths.distinct() == file.sourcePaths &&
                    file.sourcePaths.all { sourcePath ->
                        sourcePath.isCanonicalOutputSourcePath()
                    },
            ) {
                "Output artifact source inventory is not canonical."
            }
            when (file.kind) {
                KIND_DIAGNOSTICS -> require(
                    file.path == DIAGNOSTICS_PATH &&
                        file.sourcePaths.all { sourcePath ->
                            sourcePath.startsWith("sources/") ||
                                sourcePath.startsWith("declarations/") ||
                                sourcePath.startsWith("packages/")
                        },
                ) {
                    "Diagnostics artifact authority is invalid."
                }
                KIND_JAVASCRIPT -> require(
                    file.path.startsWith("modules/") &&
                        file.path.hasJavaScriptExtension() &&
                        file.sourcePaths.size == 1 &&
                        file.sourcePaths.single().startsWith("sources/"),
                ) {
                    "JavaScript artifact authority is invalid."
                }
                KIND_SOURCE_MAP -> require(
                    file.path.startsWith("maps/") &&
                        file.path.removeSuffix(".map").hasJavaScriptExtension() &&
                        file.path.endsWith(".map") &&
                        file.sourcePaths.size == 1 &&
                        file.sourcePaths.single().startsWith("sources/"),
                ) {
                    "Source Map artifact authority is invalid."
                }
                else -> throw IllegalArgumentException(
                    "Output artifact kind is not supported: ${file.kind}",
                )
            }
        }
        require(
            files.count { file -> file.kind == KIND_DIAGNOSTICS } == 1 &&
                files.single { file -> file.kind == KIND_DIAGNOSTICS }.path == DIAGNOSTICS_PATH,
        ) {
            "Output artifact inventory must contain one exact diagnostics document."
        }
    }

    fun requireOutcomeInventory(
        outcome: String,
        emitSkipped: Boolean,
        diagnosticCount: Int,
        emittedModuleCount: Int,
        files: List<R2OutputArchiveFile>,
    ) {
        requireArtifactInventory(files)
        require(diagnosticCount in 0..TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT) {
            "Output diagnostic count is outside the frozen bound."
        }
        val modules = files.filter { file -> file.kind == KIND_JAVASCRIPT }
        val sourceMaps = files.filter { file -> file.kind == KIND_SOURCE_MAP }
        require(modules.size == emittedModuleCount) {
            "Output module count does not match its artifact inventory."
        }
        when (outcome) {
            TypeScriptCompilerContract.OUTCOME_EMITTED -> {
                require(!emitSkipped && modules.isNotEmpty() && sourceMaps.size == modules.size) {
                    "Emitted output inventory is not representable."
                }
                val sourceMapPaths = sourceMaps.map(R2OutputArchiveFile::path).toHashSet()
                require(modules.all { module ->
                    val relative = module.path.removePrefix("modules/")
                    "maps/$relative.map" in sourceMapPaths
                }) {
                    "Emitted output inventory has an unpaired module artifact."
                }
            }
            TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS -> require(
                emitSkipped &&
                    diagnosticCount > 0 &&
                    emittedModuleCount == 0 &&
                    modules.isEmpty() &&
                    sourceMaps.isEmpty() &&
                    files.size == 1,
            ) {
                "Diagnostics-only output inventory is not representable."
            }
            else -> throw IllegalArgumentException(
                "Output archive cannot represent terminal outcome: $outcome",
            )
        }
    }

    fun artifactInventoryFingerprint(files: List<R2OutputArchiveFile>): String {
        requireArtifactInventory(files)
        return fingerprint(ARTIFACT_INVENTORY_PREFIX, files)
    }

    fun moduleInventoryFingerprint(files: List<R2OutputArchiveFile>): String {
        requireArtifactInventory(files)
        return fingerprint(
            MODULE_INVENTORY_PREFIX,
            files.filter { file -> file.kind == KIND_JAVASCRIPT },
        )
    }

    private fun fingerprint(
        prefix: String,
        files: List<R2OutputArchiveFile>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(prefix.toByteArray(StandardCharsets.US_ASCII))
        files.sortedBy(R2OutputArchiveFile::path).forEach { file ->
            digest.update(file.kind.toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
            digest.update(0)
            digest.update(file.sourcePaths.size.toString().toByteArray(StandardCharsets.US_ASCII))
            file.sourcePaths.forEach { sourcePath ->
                digest.update(0)
                digest.update(sourcePath.toByteArray(StandardCharsets.UTF_8))
            }
            digest.update('\n'.code.toByte())
        }
        return digest.digest().toLowerHex()
    }

    private fun String.isCanonicalOutputArchivePath(): Boolean {
        return R2UnicodePathPolicy.isCanonicalRelativePath(this)
    }

    private fun String.isCanonicalOutputSourcePath(): Boolean =
        isCanonicalOutputArchivePath() &&
            (
                startsWith("sources/") ||
                    startsWith("declarations/") ||
                    startsWith("packages/")
                )

    private fun String.hasJavaScriptExtension(): Boolean =
        endsWith(".js") || endsWith(".mjs") || endsWith(".cjs")

    private fun String.asciiCaseFold(): String = buildString(length) {
        this@asciiCaseFold.forEach { character ->
            append(
                if (character in 'A'..'Z') {
                    (character.code + ('a'.code - 'A'.code)).toChar()
                } else {
                    character
                },
            )
        }
    }

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
