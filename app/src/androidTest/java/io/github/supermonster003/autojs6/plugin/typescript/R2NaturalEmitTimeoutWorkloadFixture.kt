package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Entry
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Deterministic ordinary-admission workload for request-start natural emit timeout evidence.
 *
 * The first quarter of roots is deliberately small. The remaining roots contain bounded, valid
 * optional chains that TypeScript 6.0.3 must naturally downlevel for the fixed ES2018 profile.
 * Emit progress sequence two is therefore published after the four outputs belonging to the two
 * light roots, before the Worker transforms the six heavy roots, without a phase hook, spin, or
 * mutable deadline.
 */
internal object R2NaturalEmitTimeoutWorkloadFixture {

    const val ROOT_COUNT = 8
    const val LIGHT_ROOT_COUNT = 2
    const val HEAVY_ROOT_COUNT = 6
    const val OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT = 18
    const val OPTIONAL_CHAIN_DEPTH = 110

    const val TRUSTED_DECLARATION_FILE_COUNT = 108
    const val TRUSTED_DECLARATION_BYTES = 3_784_758L
    const val TRUSTED_DECLARATION_TOKENS = 86_329

    const val ROOT_BYTES = 57_074L
    const val ROOT_TOKENS = 24_310
    const val MAXIMUM_ROOT_BYTES = 9_502
    const val MAXIMUM_ROOT_TOKENS = 4_049

    const val TOTAL_FILE_COUNT = 116
    const val TOTAL_BYTES = 3_841_832L
    const val TOTAL_TOKENS = 110_639
    const val TOTAL_PATH_CHARACTERS = 4_145
    const val MAXIMUM_PATH_CHARACTERS = 46
    const val INPUT_TRANSPORT_FRAME_COUNT = 400

    const val OUTPUT_FILE_COUNT = 16
    const val JAVASCRIPT_OUTPUT_BYTES = 769_510L
    const val SOURCE_MAP_OUTPUT_BYTES = 196_204L
    const val OUTPUT_BYTES = 965_714L
    const val OUTPUT_AGGREGATE_SHA256 =
        "e7eac5f110b4dff7bbb732ea47c3f21c083b44fa215b740895a8e8bac9ba4fa6"

    val rootNames: List<String> =
        List(ROOT_COUNT) { index -> sourcePath(index) }

    fun createSnapshot(
        directory: File,
        declarations: R2TrustedDeclarations,
    ): R2FileBackedProjectSnapshot {
        check(LIGHT_ROOT_COUNT + HEAVY_ROOT_COUNT == ROOT_COUNT)
        check(
            (OUTPUT_FILE_COUNT - 1) / 4 + 1 ==
                LIGHT_ROOT_COUNT * OUTPUTS_PER_ROOT,
        )
        check(rootNames == rootNames.sorted())
        check(declarations.files.size == TRUSTED_DECLARATION_FILE_COUNT) {
            "Natural emit-timeout declarations changed file count."
        }
        check(
            declarations.files.sumOf(R2TrustedDeclarationFile::size) ==
                TRUSTED_DECLARATION_BYTES,
        ) {
            "Natural emit-timeout declarations changed byte count."
        }

        val spool = R2PrivateProjectSpool.create(directory)
        var ownershipTransferred = false
        try {
            val records = ArrayList<R2FileBackedProjectRecord>(TOTAL_FILE_COUNT)
            var rootBytes = 0L
            repeat(ROOT_COUNT) { index ->
                val bytes = sourceText(index)
                    .toByteArray(StandardCharsets.US_ASCII)
                rootBytes = Math.addExact(rootBytes, bytes.size.toLong())
                records += spool.appendStoredAsciiFile(
                    archivePath = sourcePath(index).removePrefix("/"),
                    bytes = bytes,
                )
            }
            check(rootBytes == ROOT_BYTES) {
                "Natural emit-timeout roots changed byte count: $rootBytes."
            }
            spool.finishArchiveExtraction()
            declarations.files.forEach { declaration ->
                records += spool.appendTrustedDeclaration(declaration)
            }
            return spool.seal(
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                projectRecords = records,
                rootNames = rootNames,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            ).also { snapshot ->
                check(snapshot.fileInventory.size == TOTAL_FILE_COUNT)
                check(snapshot.totalUtf8Bytes == TOTAL_BYTES)
                check(
                    snapshot.fileInventory.sumOf { file -> file.path.length } ==
                        TOTAL_PATH_CHARACTERS,
                )
                check(
                    snapshot.fileInventory.maxOf { file -> file.path.length } ==
                        MAXIMUM_PATH_CHARACTERS,
                )
                ownershipTransferred = true
            }
        } finally {
            if (!ownershipTransferred) {
                spool.close()
            }
        }
    }

    fun sourcePath(index: Int): String {
        require(index in 0 until ROOT_COUNT)
        return "/sources/natural-emit-timeout-" +
            "${index.toString().padStart(3, '0')}.ts"
    }

    fun sourceText(index: Int): String {
        require(index in 0 until ROOT_COUNT)
        val prefix = index.toString().padStart(3, '0')
        if (index < LIGHT_ROOT_COUNT) {
            return "export const e$prefix: number = $index;\n"
        }
        return buildString {
            append("declare const q: any;\n")
            append("export const e")
            append(prefix)
            append(": number = [\n")
            repeat(OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT) { chainIndex ->
                append("  q")
                repeat(OPTIONAL_CHAIN_DEPTH) { depthIndex ->
                    append("?.p")
                    append(depthIndex.toString(36))
                }
                append(" ?? ")
                append(chainIndex)
                append(",\n")
            }
            append("].length;\n")
        }
    }

    fun aggregateOutputSha256(outputs: List<R2ProjectOutput>): String =
        R2NaturalPhaseWorkloadFixture.aggregateOutputSha256(outputs)

    fun outputUtf8Bytes(outputs: List<R2ProjectOutput>): Long =
        R2NaturalPhaseWorkloadFixture.outputUtf8Bytes(outputs)

    private fun R2PrivateProjectSpool.appendStoredAsciiFile(
        archivePath: String,
        bytes: ByteArray,
    ): R2FileBackedProjectRecord {
        val entry = ZipArchiveV1Entry(
            path = archivePath,
            compressionMethod = ZIP_STORED,
            compressedBytes = bytes.size.toLong(),
            uncompressedBytes = bytes.size.toLong(),
            crc32 = CRC32().apply { update(bytes) }.value,
        )
        begin(entry)
        var offset = 0
        while (offset < bytes.size) {
            val byteCount = minOf(
                STORED_FILE_WRITE_CHUNK_BYTES,
                bytes.size - offset,
            )
            write(bytes, offset, byteCount)
            offset += byteCount
        }
        end(entry)
        val spooled = requireNotNull(archiveEntries[archivePath])
        return R2FileBackedProjectRecord(
            metadata = R2ProjectFileMetadata(
                path = "/$archivePath",
                utf8Bytes = spooled.byteLength,
                utf16CodeUnits = spooled.byteLength.toInt(),
                sha256 = spooled.sha256,
            ),
            offset = spooled.offset,
        )
    }

    private const val OUTPUTS_PER_ROOT = 2
    private const val ZIP_STORED = 0
    private const val STORED_FILE_WRITE_CHUNK_BYTES = 16 * 1024
}
