package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Entry
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Deterministic ordinary-admission workload for natural parse/semantic/emit progress evidence.
 *
 * Every source root has the same TypeScript token count. Runtime declarations make semantic
 * diagnostics and emit perform real per-root work, while type aliases add parser/checker work
 * without approaching the 1 MiB output budget.
 */
internal object R2NaturalPhaseWorkloadFixture {

    const val ROOT_COUNT = 192
    const val RUNTIME_VALUE_COUNT_PER_ROOT = 16
    const val TYPE_ALIAS_COUNT_PER_ROOT = 46

    const val TRUSTED_DECLARATION_FILE_COUNT = 108
    const val TRUSTED_DECLARATION_BYTES = 3_784_758L
    const val TRUSTED_DECLARATION_TOKENS = 86_329

    const val ROOT_BYTES = 810_241L
    const val ROOT_TOKENS = 145_920
    const val MAXIMUM_ROOT_TOKENS = 760

    const val TOTAL_FILE_COUNT = 300
    const val TOTAL_BYTES = 4_594_999L
    const val TOTAL_TOKENS = 232_249

    const val OUTPUT_FILE_COUNT = 384
    const val JAVASCRIPT_OUTPUT_BYTES = 290_497L
    const val SOURCE_MAP_OUTPUT_BYTES = 272_448L
    const val OUTPUT_BYTES = 562_945L
    const val OUTPUT_AGGREGATE_SHA256 =
        "2f4dc949984424bb6eff1d806efcac2fd21f4ae8ad87a0c44f03f2c086d1444d"

    val rootNames: List<String> =
        List(ROOT_COUNT) { index -> sourcePath(index) }

    fun createSnapshot(
        directory: File,
        declarations: R2TrustedDeclarations,
    ): R2FileBackedProjectSnapshot {
        check(declarations.files.size == TRUSTED_DECLARATION_FILE_COUNT) {
            "Natural-phase declarations changed file count."
        }
        check(
            declarations.files.sumOf(R2TrustedDeclarationFile::size) ==
                TRUSTED_DECLARATION_BYTES,
        ) {
            "Natural-phase declarations changed byte count."
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
                "Natural-phase roots changed byte count: $rootBytes."
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
        return "/sources/natural-phase-${index.toString().padStart(3, '0')}.ts"
    }

    fun sourceText(index: Int): String {
        require(index in 0 until ROOT_COUNT)
        val prefix = index.toString().padStart(3, '0')
        return buildString {
            append("export interface NaturalRecord")
            append(prefix)
            append("<T> { readonly id: number; readonly value: T; }\n")
            repeat(RUNTIME_VALUE_COUNT_PER_ROOT) { valueIndex ->
                append("export const naturalValue")
                append(prefix)
                append('_')
                append(valueIndex.toString().padStart(2, '0'))
                append(": NaturalRecord")
                append(prefix)
                append("<number> = { id: ")
                append(valueIndex)
                append(", value: ")
                append(index + valueIndex)
                append(" };\n")
            }
            repeat(TYPE_ALIAS_COUNT_PER_ROOT) { aliasIndex ->
                append("export type NaturalAlias")
                append(prefix)
                append('_')
                append(aliasIndex.toString().padStart(2, '0'))
                append(" = NaturalRecord")
                append(prefix)
                append("<number>;\n")
            }
            append("export function select")
            append(prefix)
            append("(item: NaturalRecord")
            append(prefix)
            append("<number>): number { return item.id + item.value; }\n")
        }
    }

    fun aggregateOutputSha256(outputs: List<R2ProjectOutput>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        outputs.sortedBy(R2ProjectOutput::path).forEach { output ->
            digest.update(output.path.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
            digest.update(output.text.toByteArray(StandardCharsets.UTF_8))
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    fun outputUtf8Bytes(outputs: List<R2ProjectOutput>): Long =
        outputs.sumOf { output ->
            output.text.toByteArray(StandardCharsets.UTF_8).size.toLong()
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
        write(bytes, 0, bytes.size)
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
}
