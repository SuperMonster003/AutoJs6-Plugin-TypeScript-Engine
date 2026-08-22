package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Entry
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Deterministic ordinary-admission workload for request-start natural parse timeout evidence.
 *
 * The first two executable roots are deliberately minimal. Root two late-references one ambient
 * wildcard module declaration, and roots three through five contain bounded, unique type-only
 * imports. Every source lives below the same fixed eight-level directory. The imports therefore
 * perform real Node10 ancestor resolution only after the Worker publishes parse progress sequence
 * two from two completed executable roots, without a phase hook or mutable deadline.
 */
internal object R2NaturalParseTimeoutWorkloadFixture {

    const val ROOT_COUNT = 8
    const val SOURCE_DIRECTORY_DEPTH = 8
    const val SOURCE_PATH_DEPTH = 10
    const val REFERENCE_ROOT_INDEX = 2
    const val FIRST_IMPORT_ROOT_INDEX = 3
    const val IMPORT_ROOT_COUNT = 3
    const val IMPORTS_PER_ROOT = 5_700
    const val MAXIMUM_MODULE_INDEX = 17_099
    const val AMBIENT_DECLARATION_FILE_COUNT = 1

    const val TRUSTED_DECLARATION_FILE_COUNT = 108
    const val TRUSTED_DECLARATION_BYTES = 3_784_758L
    const val TRUSTED_DECLARATION_TOKENS = 86_329

    const val ROOT_BYTES = 1_094_708L
    const val ROOT_TOKENS = 171_048
    const val MAXIMUM_ROOT_BYTES = 364_832
    const val MAXIMUM_ROOT_TOKENS = 57_006

    const val AMBIENT_DECLARATION_BYTES = 61L
    const val AMBIENT_DECLARATION_TOKENS = 11

    const val TOTAL_FILE_COUNT = 117
    const val TOTAL_BYTES = 4_879_527L
    const val TOTAL_TOKENS = 257_388
    const val TOTAL_PATH_CHARACTERS = 4_434
    const val MAXIMUM_PATH_CHARACTERS = 65
    const val INPUT_TRANSPORT_FRAME_COUNT = 418

    const val OUTPUT_FILE_COUNT = 16
    const val JAVASCRIPT_OUTPUT_BYTES = 1_524L
    const val SOURCE_MAP_OUTPUT_BYTES = 1_814L
    const val OUTPUT_BYTES = 3_338L
    const val OUTPUT_AGGREGATE_SHA256 =
        "c16cf1e19354655321bc7bd13bea9840900f2f2b2bf67793b4d1b926ee42e6b3"

    val rootNames: List<String> =
        List(ROOT_COUNT) { index -> sourcePath(index) }

    fun createSnapshot(
        directory: File,
        declarations: R2TrustedDeclarations,
    ): R2FileBackedProjectSnapshot {
        val sequenceTwoCompletedRoots = (ROOT_COUNT - 1) / 4 + 1
        check(sequenceTwoCompletedRoots == REFERENCE_ROOT_INDEX)
        check(REFERENCE_ROOT_INDEX < FIRST_IMPORT_ROOT_INDEX)
        check(
            FIRST_IMPORT_ROOT_INDEX + IMPORT_ROOT_COUNT <= ROOT_COUNT,
        )
        check(
            sourceDirectoryPath()
                .split('/')
                .count(String::isNotEmpty) +
                1 == SOURCE_PATH_DEPTH,
        )
        check(rootNames == rootNames.sorted())
        check(
            rootNames.all { rootName ->
                rootName.substringBeforeLast('/') == sourceDirectoryPath()
            },
        )
        check(declarations.files.size == TRUSTED_DECLARATION_FILE_COUNT) {
            "Natural parse-timeout declarations changed file count."
        }
        check(
            declarations.files.sumOf(R2TrustedDeclarationFile::size) ==
                TRUSTED_DECLARATION_BYTES,
        ) {
            "Natural parse-timeout declarations changed byte count."
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
                "Natural parse-timeout roots changed byte count: $rootBytes."
            }
            val ambientDeclarationBytes = ambientDeclarationText()
                .toByteArray(StandardCharsets.US_ASCII)
            check(
                ambientDeclarationBytes.size.toLong() ==
                    AMBIENT_DECLARATION_BYTES,
            ) {
                "Natural parse-timeout ambient declaration changed byte count: " +
                    "${ambientDeclarationBytes.size}."
            }
            records += spool.appendStoredAsciiFile(
                archivePath = ambientDeclarationPath().removePrefix("/"),
                bytes = ambientDeclarationBytes,
            )
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
        return sourceDirectoryPath() +
            "/probe-parse-root-" +
            "${index.toString().padStart(3, '0')}.ts"
    }

    fun sourceDirectoryPath(): String =
        "/sources/" +
            List(SOURCE_DIRECTORY_DEPTH) { index ->
                "p${index.toString().padStart(2, '0')}"
            }.joinToString(separator = "/")

    fun sourceText(index: Int): String {
        require(index in 0 until ROOT_COUNT)
        val prefix = index.toString().padStart(3, '0')
        return buildString {
            if (index == REFERENCE_ROOT_INDEX) {
                append(
                    "/// <reference path=\"./probe-parse-ambient.d.ts\" />\n",
                )
            }
            if (
                index in
                FIRST_IMPORT_ROOT_INDEX until
                    FIRST_IMPORT_ROOT_INDEX + IMPORT_ROOT_COUNT
            ) {
                repeat(IMPORTS_PER_ROOT) { importIndex ->
                    val moduleIndex =
                        (index - FIRST_IMPORT_ROOT_INDEX) *
                            IMPORTS_PER_ROOT +
                            importIndex
                    check(moduleIndex <= MAXIMUM_MODULE_INDEX)
                    val moduleSuffix =
                        moduleIndex.toString().padStart(5, '0')
                    append("import type { ProbeType as ProbeType")
                    append(moduleSuffix)
                    append(" } from \"probe-")
                    append(moduleSuffix)
                    append("\"; ")
                }
            }
            append("export const probeValue")
            append(prefix)
            append(" = ")
            append(index)
            append("; ")
        }
    }

    fun ambientDeclarationPath(): String =
        sourceDirectoryPath() + "/probe-parse-ambient.d.ts"

    fun ambientDeclarationText(): String =
        "declare module \"probe-*\" { export type ProbeType = number; } "

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

    private const val ZIP_STORED = 0
    private const val STORED_FILE_WRITE_CHUNK_BYTES = 16 * 1024
}
