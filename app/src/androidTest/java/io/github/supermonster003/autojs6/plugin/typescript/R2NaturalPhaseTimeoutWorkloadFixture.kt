package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Entry
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Deterministic ordinary-admission workload for request-start natural semantic timeout evidence.
 *
 * The first quarter of roots is deliberately small. Remaining roots import bounded declaration
 * helpers whose distributive types leave a long semantic tail after the Worker publishes sequence
 * two from real completed units, without a phase hook or a mutable deadline.
 */
internal object R2NaturalPhaseTimeoutWorkloadFixture {

    const val ROOT_COUNT = 256
    const val LIGHT_ROOT_COUNT = 64
    const val HELPER_COUNT = 148
    const val HELPER_ATOM_COUNT = 72
    const val HELPER_WHITESPACE_BYTES = 24 * 1024
    const val HEAVY_PRIVATE_FIELD_COUNT = 8

    const val TRUSTED_DECLARATION_FILE_COUNT = 108
    const val TRUSTED_DECLARATION_BYTES = 3_784_758L
    const val TRUSTED_DECLARATION_TOKENS = 86_329

    const val ROOT_BYTES = 178_722L
    const val ROOT_TOKENS = 36_928
    const val MAXIMUM_ROOT_TOKENS = 169

    const val HELPER_BYTES = 3_763_492L
    const val HELPER_TOKENS = 28_268
    const val MAXIMUM_HELPER_TOKENS = 191

    const val TOTAL_FILE_COUNT = 512
    const val TOTAL_BYTES = 7_726_972L
    const val TOTAL_TOKENS = 151_525

    const val OUTPUT_FILE_COUNT = 512
    const val JAVASCRIPT_OUTPUT_BYTES = 535_202L
    const val SOURCE_MAP_OUTPUT_BYTES = 185_664L
    const val OUTPUT_BYTES = 720_866L
    const val OUTPUT_AGGREGATE_SHA256 =
        "4b5651d65dc07b0fbaadcb7b31db3908a3de331829f21ce32fba56ac944b4cc1"

    val rootNames: List<String> =
        List(ROOT_COUNT) { index -> sourcePath(index) }

    fun createSnapshot(
        directory: File,
        declarations: R2TrustedDeclarations,
    ): R2FileBackedProjectSnapshot {
        check(declarations.files.size == TRUSTED_DECLARATION_FILE_COUNT) {
            "Natural-phase timeout declarations changed file count."
        }
        check(
            declarations.files.sumOf(R2TrustedDeclarationFile::size) ==
                TRUSTED_DECLARATION_BYTES,
        ) {
            "Natural-phase timeout declarations changed byte count."
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
                "Natural-phase timeout roots changed byte count: $rootBytes."
            }
            var helperBytes = 0L
            repeat(HELPER_COUNT) { index ->
                val bytes = helperText(index)
                    .toByteArray(StandardCharsets.US_ASCII)
                helperBytes = Math.addExact(
                    helperBytes,
                    bytes.size.toLong(),
                )
                records += spool.appendStoredAsciiFile(
                    archivePath = helperPath(index).removePrefix("/"),
                    bytes = bytes,
                )
            }
            check(helperBytes == HELPER_BYTES) {
                "Natural-phase timeout helpers changed byte count: $helperBytes."
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
        return "/sources/natural-timeout-phase-" +
            "${index.toString().padStart(3, '0')}.ts"
    }

    fun sourceText(index: Int): String {
        require(index in 0 until ROOT_COUNT)
        val prefix = index.toString().padStart(3, '0')
        val lightRoot = index < LIGHT_ROOT_COUNT
        return buildString {
            if (!lightRoot) {
                val helperPrefix = helperIndex(index)
                    .toString()
                    .padStart(3, '0')
                append("import type { NaturalTimeoutMatrix")
                append(helperPrefix)
                append(" } from \"./natural-timeout-helper-")
                append(helperPrefix)
                append("\";\n")
            }
            append("export interface NaturalTimeoutRecord")
            append(prefix)
            append("<T> { readonly id: number; readonly value: T; }\n")
            append("export const naturalTimeoutValue")
            append(prefix)
            append(": NaturalTimeoutRecord")
            append(prefix)
            append("<number> = { id: ")
            append(index)
            append(", value: ")
            append(index + 1)
            append(" };\n")
            if (lightRoot) {
                append("export type NaturalTimeoutAlias")
                append(prefix)
                append(" = NaturalTimeoutRecord")
                append(prefix)
                append("<number>;\n")
            } else {
                val helperPrefix = helperIndex(index)
                    .toString()
                    .padStart(3, '0')
                append("const naturalTimeoutProof")
                append(prefix)
                append(" = [\"a00\", \"a00\"] satisfies NaturalTimeoutMatrix")
                append(helperPrefix)
                append(";\n")
                append("class NaturalTimeoutEmitter")
                append(prefix)
                append(" {\n")
                repeat(HEAVY_PRIVATE_FIELD_COUNT) { fieldIndex ->
                    append("  #v")
                    append(fieldIndex.toString().padStart(2, '0'))
                    append(" = ")
                    append(index + fieldIndex)
                    append(";\n")
                }
                append("  value(): number { return ")
                repeat(HEAVY_PRIVATE_FIELD_COUNT) { fieldIndex ->
                    if (fieldIndex != 0) {
                        append(" + ")
                    }
                    append("this.#v")
                    append(fieldIndex.toString().padStart(2, '0'))
                }
                append("; }\n")
                append("}\n")
                append("export const naturalTimeoutEmitterValue")
                append(prefix)
                append(" = new NaturalTimeoutEmitter")
                append(prefix)
                append("().value();\n")
            }
            append("export function selectTimeout")
            append(prefix)
            append("(item: NaturalTimeoutRecord")
            append(prefix)
            append("<number>): number { return item.id + item.value; }\n")
        }
    }

    fun helperPath(index: Int): String {
        require(index in 0 until HELPER_COUNT)
        return "/sources/natural-timeout-helper-" +
            "${index.toString().padStart(3, '0')}.d.ts"
    }

    fun helperText(index: Int): String {
        require(index in 0 until HELPER_COUNT)
        val prefix = index.toString().padStart(3, '0')
        return buildString {
            append("export type NaturalTimeoutAtom")
            append(prefix)
            append(" = ")
            repeat(HELPER_ATOM_COUNT) { atomIndex ->
                if (atomIndex != 0) {
                    append(" | ")
                }
                append('"')
                append('a')
                append(atomIndex.toString().padStart(2, '0'))
                append('"')
            }
            append(";\n")
            append("export type NaturalTimeoutCross")
            append(prefix)
            append("<A extends string, B extends string> = ")
            append("A extends A ? B extends B ? readonly [A, B] : never : never;\n")
            append("export type NaturalTimeoutMatrix")
            append(prefix)
            append(" = NaturalTimeoutCross")
            append(prefix)
            append("<NaturalTimeoutAtom")
            append(prefix)
            append(", NaturalTimeoutAtom")
            append(prefix)
            append(">;\n")
            repeat(HELPER_WHITESPACE_BYTES) {
                append(' ')
            }
        }
    }

    fun aggregateOutputSha256(outputs: List<R2ProjectOutput>): String =
        R2NaturalPhaseWorkloadFixture.aggregateOutputSha256(outputs)

    fun outputUtf8Bytes(outputs: List<R2ProjectOutput>): Long =
        R2NaturalPhaseWorkloadFixture.outputUtf8Bytes(outputs)

    private fun helperIndex(rootIndex: Int): Int {
        require(rootIndex in LIGHT_ROOT_COUNT until ROOT_COUNT)
        return (rootIndex - LIGHT_ROOT_COUNT) % HELPER_COUNT
    }

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
