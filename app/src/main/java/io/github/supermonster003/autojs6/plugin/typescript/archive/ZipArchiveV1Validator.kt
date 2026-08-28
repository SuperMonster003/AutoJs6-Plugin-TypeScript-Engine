package io.github.supermonster003.autojs6.plugin.typescript.archive

import io.github.supermonster003.autojs6.plugin.typescript.R2Utf8
import io.github.supermonster003.autojs6.plugin.typescript.R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES
import io.github.supermonster003.autojs6.plugin.typescript.R2_INPUT_ARCHIVE_MAXIMUM_EXPANDED_BYTES
import io.github.supermonster003.autojs6.plugin.typescript.R2_INPUT_ARCHIVE_MAXIMUM_FILES
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Collections
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal enum class ZipArchiveV1FailureKind {
    FORMAT,
    INTEGRITY,
    PATH,
    QUOTA,

    ;

    val contractErrorCode: String
        get() = when (this) {
            FORMAT -> TypeScriptCompilerContract.ERROR_ARCHIVE_FORMAT
            INTEGRITY -> TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY
            PATH -> TypeScriptCompilerContract.ERROR_ARCHIVE_PATH
            QUOTA -> TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA
        }
}

internal class ZipArchiveV1Exception(
    val kind: ZipArchiveV1FailureKind,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal data class ZipArchiveV1Limits(
    val maximumContainerBytes: Long,
    val maximumFiles: Int,
    val maximumTotalUncompressedBytes: Long,
    val maximumSingleFileBytes: Long,
    val maximumEntryNameBytes: Int =
        TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES,
    val maximumPathDepth: Int =
        TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH,
    val maximumMetadataBytes: Long =
        TypeScriptCompilerContract.MAX_ZIP_METADATA_BYTES,
    val maximumCompressionRatio: Int =
        TypeScriptCompilerContract.MAX_ARCHIVE_COMPRESSION_RATIO,
) {

    init {
        require(maximumContainerBytes >= 0L)
        require(maximumFiles >= 0)
        require(maximumTotalUncompressedBytes >= 0L)
        require(maximumSingleFileBytes >= 0L)
        require(maximumEntryNameBytes > 0)
        require(maximumPathDepth > 0)
        require(maximumMetadataBytes >= 0L)
        require(maximumCompressionRatio > 0)
    }

    companion object {
        val INPUT = ZipArchiveV1Limits(
            maximumContainerBytes =
                R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES,
            maximumFiles = R2_INPUT_ARCHIVE_MAXIMUM_FILES,
            maximumTotalUncompressedBytes =
                R2_INPUT_ARCHIVE_MAXIMUM_EXPANDED_BYTES,
            maximumSingleFileBytes =
                TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
        )

        /**
         * Legacy heap-adapter budget retained for focused compatibility tests. Production R2-P3a
         * input uses [INPUT] with the transactional streaming sink instead.
         */
        val PROVISIONAL_MATERIALIZED_PROJECT = INPUT.copy(
            maximumFiles = 257,
            maximumTotalUncompressedBytes = 1L * 1024L * 1024L,
            maximumSingleFileBytes = 512L * 1024L,
        )

        val OUTPUT = ZipArchiveV1Limits(
            maximumContainerBytes =
                TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES,
            maximumFiles = TypeScriptCompilerContract.MAX_OUTPUT_FILES,
            maximumTotalUncompressedBytes =
                TypeScriptCompilerContract.MAX_OUTPUT_BYTES,
            maximumSingleFileBytes =
                TypeScriptCompilerContract.MAX_OUTPUT_BYTES,
        )
    }
}

internal data class ZipArchiveV1Entry(
    val path: String,
    val compressionMethod: Int,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val crc32: Long,
)

internal data class ZipArchiveV1Validation(
    val entries: List<ZipArchiveV1Entry>,
    val containerBytes: Long,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val metadataBytes: Long,
)

internal class ZipArchiveV1Contents internal constructor(
    val validation: ZipArchiveV1Validation,
    entries: LinkedHashMap<String, ByteArray>,
) {

    /**
     * Actual uncompressed entry bytes in central-directory order.
     *
     * The map shape is immutable. Each byte array is newly allocated for this validation pass and
     * is owned by the caller so a provisional adapter can decode or hash it without retaining the
     * staged container.
     */
    val entries: Map<String, ByteArray> = Collections.unmodifiableMap(entries)
}

/**
 * Transactional streaming destination for validated ZIP entry payloads.
 *
 * Entries arrive in physical local-record order. [write] borrows a validator-owned buffer only for
 * the duration of the callback, and each callback is bounded to 16 KiB. [end] proves that the
 * current entry's actual size, CRC-32 and compression ratio were validated, but the destination
 * must not publish any streamed data until the enclosing validation call returns successfully.
 *
 * [abort] is called once when validation or a sink callback fails. Implementations should discard
 * all partial state there. An abort failure is suppressed onto the primary failure.
 */
internal interface ZipArchiveV1EntrySink {

    fun begin(entry: ZipArchiveV1Entry)

    fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    )

    fun end(entry: ZipArchiveV1Entry)

    fun abort(failure: Throwable)
}

internal class ZipArchiveV1EntrySinkException(
    message: String,
    cause: Throwable,
) : IOException(message, cause)

/**
 * Structural validator for the frozen ZIP archive container v1.
 *
 * This layer deliberately knows nothing about request/output manifests or allowed project paths.
 * It validates raw ZIP records and actual inflated bytes instead of trusting ZipEntry metadata.
 */
internal object ZipArchiveV1Validator {

    fun validate(
        file: File,
        limits: ZipArchiveV1Limits = ZipArchiveV1Limits.INPUT,
    ): ZipArchiveV1Validation {
        try {
            val snapshot = readImmutableFileSnapshot(
                file = file,
                maximumContainerBytes = limits.maximumContainerBytes,
            )
            return ByteArraySource(snapshot).use { source ->
                validateSource(source, limits)
            }
        } catch (failure: ZipArchiveV1Exception) {
            throw failure
        } catch (failure: IOException) {
            throw ZipArchiveV1Exception(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP container could not be opened safely.",
                failure,
            )
        }
    }

    fun validate(
        bytes: ByteArray,
        limits: ZipArchiveV1Limits = ZipArchiveV1Limits.INPUT,
    ): ZipArchiveV1Validation =
        ByteArraySource(bytes).use { source ->
            validateSource(source, limits)
        }

    /**
     * Validates an already-private immutable staging inode without copying either the complete
     * container or its uncompressed entry payloads into heap-owned byte arrays.
     *
     * The caller retains ownership of [file]. This method neither closes it nor preserves its
     * current cursor. The caller must guarantee that the opened inode cannot be modified for the
     * duration of validation.
     */
    fun validateImmutable(
        file: RandomAccessFile,
        limits: ZipArchiveV1Limits = ZipArchiveV1Limits.INPUT,
    ): ZipArchiveV1Validation {
        try {
            return validateSource(
                source = RandomAccessFileSource(file),
                limits = limits,
            )
        } catch (failure: ZipArchiveV1Exception) {
            throw failure
        } catch (failure: IOException) {
            throw ZipArchiveV1Exception(
                ZipArchiveV1FailureKind.INTEGRITY,
                "Immutable ZIP staging container could not be read safely.",
                failure,
            )
        }
    }

    /**
     * Validates and reads an already-private immutable staging file without first copying the
     * complete ZIP container into a ByteArray.
     *
     * Unlike [validate], this method does not detach itself from later writes to [file]. The caller
     * must guarantee that the opened inode cannot be modified for the duration of this call.
     */
    fun validateImmutableAndRead(
        file: File,
        limits: ZipArchiveV1Limits = ZipArchiveV1Limits.INPUT,
    ): ZipArchiveV1Contents {
        try {
            return RandomAccessFile(file, "r").use { source ->
                validateImmutableAndRead(source, limits)
            }
        } catch (failure: ZipArchiveV1Exception) {
            throw failure
        } catch (failure: IOException) {
            throw ZipArchiveV1Exception(
                ZipArchiveV1FailureKind.INTEGRITY,
                "Immutable ZIP staging container could not be opened safely.",
                failure,
            )
        }
    }

    /**
     * Random-access overload for a private immutable staging inode that is already open.
     *
     * The caller retains ownership of [file]; this method neither closes it nor captures a
     * container-sized byte snapshot.
     */
    fun validateImmutableAndRead(
        file: RandomAccessFile,
        limits: ZipArchiveV1Limits = ZipArchiveV1Limits.INPUT,
    ): ZipArchiveV1Contents {
        try {
            val entries = LinkedHashMap<String, ByteArray>()
            val validation = validateSource(
                source = RandomAccessFileSource(file),
                limits = limits,
                extractedEntries = entries,
            )
            check(entries.size == validation.entries.size) {
                "Validated ZIP payload inventory is incomplete."
            }
            val centralOrder = LinkedHashMap<String, ByteArray>(entries.size)
            validation.entries.forEach { entry ->
                centralOrder[entry.path] = checkNotNull(entries[entry.path]) {
                    "Validated ZIP payload is missing for ${entry.path}."
                }
            }
            return ZipArchiveV1Contents(
                validation = validation,
                entries = centralOrder,
            )
        } catch (failure: ZipArchiveV1Exception) {
            throw failure
        } catch (failure: IOException) {
            throw ZipArchiveV1Exception(
                ZipArchiveV1FailureKind.INTEGRITY,
                "Immutable ZIP staging container could not be read safely.",
                failure,
            )
        }
    }

    /**
     * Validates an already-private immutable staging inode while streaming each actual
     * uncompressed payload to [sink].
     *
     * The caller retains ownership of [file] and [sink]. This method neither closes them nor
     * captures container- or entry-sized byte arrays. The sink transaction may be committed only
     * after this method returns successfully; every failure calls [ZipArchiveV1EntrySink.abort].
     */
    fun validateImmutableTo(
        file: RandomAccessFile,
        sink: ZipArchiveV1EntrySink,
        limits: ZipArchiveV1Limits = ZipArchiveV1Limits.INPUT,
    ): ZipArchiveV1Validation {
        try {
            return validateSource(
                source = RandomAccessFileSource(file),
                limits = limits,
                entrySink = sink,
            )
        } catch (failure: Throwable) {
            try {
                sink.abort(failure)
            } catch (abortFailure: Throwable) {
                if (abortFailure !== failure) {
                    failure.addSuppressed(abortFailure)
                }
            }
            throw failure
        }
    }

    internal fun readImmutableFileSnapshot(
        file: File,
        maximumContainerBytes: Long,
    ): ByteArray {
        require(maximumContainerBytes >= 0L)
        return RandomAccessFile(file, "r").use { source ->
            val initialLength = source.length()
            if (initialLength > maximumContainerBytes || initialLength > Int.MAX_VALUE) {
                fail(
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP container exceeds $maximumContainerBytes bytes.",
                )
            }
            val snapshot = ByteArray(initialLength.toInt())
            source.seek(0L)
            source.readFully(snapshot)
            if (source.length() != initialLength) {
                fail(
                    ZipArchiveV1FailureKind.INTEGRITY,
                    "ZIP container changed while its immutable snapshot was captured.",
                )
            }
            snapshot
        }
    }

    private fun validateSource(
        source: RandomAccessSource,
        limits: ZipArchiveV1Limits,
        extractedEntries: MutableMap<String, ByteArray>? = null,
        entrySink: ZipArchiveV1EntrySink? = null,
    ): ZipArchiveV1Validation {
        check(extractedEntries == null || entrySink == null) {
            "ZIP validation cannot materialize and stream entry payloads simultaneously."
        }
        try {
            if (source.size > limits.maximumContainerBytes) {
                fail(
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP container exceeds ${limits.maximumContainerBytes} bytes.",
                )
            }
            if (source.size < END_OF_CENTRAL_DIRECTORY_FIXED_BYTES) {
                fail(
                    ZipArchiveV1FailureKind.FORMAT,
                    "ZIP container has no complete end-of-central-directory record.",
                )
            }

            val eocd = readEndOfCentralDirectory(source)
            if (eocd.entryCount > limits.maximumFiles) {
                fail(
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP contains more than ${limits.maximumFiles} files.",
                )
            }

            var metadataBytes =
                END_OF_CENTRAL_DIRECTORY_FIXED_BYTES + eocd.commentLength.toLong()
            metadataBytes = enforceMetadataQuota(metadataBytes, limits)
            val centralEntries = readCentralDirectory(
                source = source,
                eocd = eocd,
                limits = limits,
                initialMetadataBytes = metadataBytes,
            )
            metadataBytes = centralEntries.metadataBytes

            val physicalEntries = centralEntries.entries.sortedBy(CentralEntry::localHeaderOffset)
            var expectedLocalOffset = 0L
            physicalEntries.forEach { entry ->
                if (entry.localHeaderOffset != expectedLocalOffset) {
                    fail(
                        ZipArchiveV1FailureKind.INTEGRITY,
                        "ZIP has a preamble, gap, overlap, or repeated local header near " +
                            entry.path,
                    )
                }
                expectedLocalOffset = readAndMatchLocalEntry(
                    source = source,
                    entry = entry,
                    limits = limits,
                    currentMetadataBytes = metadataBytes,
                ).also { result ->
                    metadataBytes = result.metadataBytes
                }.nextOffset
            }
            if (expectedLocalOffset != eocd.centralDirectoryOffset) {
                fail(
                    ZipArchiveV1FailureKind.INTEGRITY,
                    "ZIP local records do not end at the central directory.",
                )
            }

            var actualTotalBytes = 0L
            physicalEntries.forEach { entry ->
                val payload = validatePayload(
                    source = source,
                    entry = entry,
                    limits = limits,
                    currentTotalBytes = actualTotalBytes,
                    extractBytes = extractedEntries != null,
                    entrySink = entrySink,
                )
                payload.bytes?.let { bytes ->
                    checkNotNull(extractedEntries).also { entries ->
                        check(entries.put(entry.path, bytes) == null) {
                            "Validated ZIP payload was extracted more than once: ${entry.path}."
                        }
                    }
                }
                actualTotalBytes = checkedAdd(
                    actualTotalBytes,
                    payload.actualBytes,
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP actual uncompressed size overflows its quota.",
                )
            }
            if (actualTotalBytes != centralEntries.declaredUncompressedBytes) {
                fail(
                    ZipArchiveV1FailureKind.INTEGRITY,
                    "ZIP actual and declared total uncompressed sizes differ.",
                )
            }
            enforceCompressionRatio(
                compressedBytes = centralEntries.declaredCompressedBytes,
                uncompressedBytes = actualTotalBytes,
                maximumRatio = limits.maximumCompressionRatio,
                label = "ZIP archive",
            )

            val entriesByCentralOrder = centralEntries.entries.map { entry ->
                ZipArchiveV1Entry(
                    path = entry.path,
                    compressionMethod = entry.compressionMethod,
                    compressedBytes = entry.compressedSize,
                    uncompressedBytes = entry.uncompressedSize,
                    crc32 = entry.crc32,
                )
            }
            return ZipArchiveV1Validation(
                entries = entriesByCentralOrder,
                containerBytes = source.size,
                compressedBytes = centralEntries.declaredCompressedBytes,
                uncompressedBytes = actualTotalBytes,
                metadataBytes = metadataBytes,
            )
        } catch (failure: ZipArchiveV1EntrySinkException) {
            throw failure
        } catch (failure: ZipArchiveV1Exception) {
            throw failure
        } catch (failure: EOFException) {
            throw ZipArchiveV1Exception(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP record is truncated.",
                failure,
            )
        } catch (failure: IOException) {
            throw ZipArchiveV1Exception(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP container could not be read safely.",
                failure,
            )
        } catch (failure: ArithmeticException) {
            throw ZipArchiveV1Exception(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP record offset or size overflows.",
                failure,
            )
        }
    }

    private fun readEndOfCentralDirectory(
        source: RandomAccessSource,
    ): EndOfCentralDirectory {
        val searchBytes = minOf(
            source.size,
            END_OF_CENTRAL_DIRECTORY_FIXED_BYTES + MAXIMUM_ZIP_COMMENT_BYTES,
        ).toInt()
        val searchOffset = source.size - searchBytes
        val tail = source.readBytes(searchOffset, searchBytes)
        val candidates = mutableListOf<Int>()
        for (index in 0..tail.size - END_OF_CENTRAL_DIRECTORY_FIXED_BYTES.toInt()) {
            if (tail.u32(index) != END_OF_CENTRAL_DIRECTORY_SIGNATURE) continue
            val commentLength = tail.u16(index + 20)
            if (
                searchOffset + index +
                END_OF_CENTRAL_DIRECTORY_FIXED_BYTES +
                commentLength == source.size
            ) {
                candidates += index
            }
        }
        if (candidates.size != 1) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP must contain exactly one unambiguous terminal EOCD record.",
            )
        }

        val index = candidates.single()
        val diskNumber = tail.u16(index + 4)
        val centralDirectoryDisk = tail.u16(index + 6)
        val entriesOnDisk = tail.u16(index + 8)
        val entryCount = tail.u16(index + 10)
        val centralDirectorySize = tail.u32(index + 12)
        val centralDirectoryOffset = tail.u32(index + 16)
        val commentLength = tail.u16(index + 20)
        if (
            diskNumber != 0 ||
            centralDirectoryDisk != 0 ||
            entriesOnDisk != entryCount
        ) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "Multi-disk ZIP containers are not supported.",
            )
        }
        if (
            entriesOnDisk == UINT16_SENTINEL ||
            entryCount == UINT16_SENTINEL ||
            centralDirectorySize == UINT32_SENTINEL ||
            centralDirectoryOffset == UINT32_SENTINEL
        ) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP64 records and sentinel fields are not supported.",
            )
        }

        val eocdOffset = searchOffset + index
        val centralEnd = checkedAdd(
            centralDirectoryOffset,
            centralDirectorySize,
            ZipArchiveV1FailureKind.INTEGRITY,
            "ZIP central-directory range overflows.",
        )
        if (centralEnd != eocdOffset) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP central directory must immediately precede EOCD.",
            )
        }
        if (entryCount == 0 && (centralDirectoryOffset != 0L || centralDirectorySize != 0L)) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "Empty ZIP has unexpected data before EOCD.",
            )
        }
        return EndOfCentralDirectory(
            offset = eocdOffset,
            centralDirectoryOffset = centralDirectoryOffset,
            centralDirectorySize = centralDirectorySize,
            entryCount = entryCount,
            commentLength = commentLength,
        )
    }

    private fun readCentralDirectory(
        source: RandomAccessSource,
        eocd: EndOfCentralDirectory,
        limits: ZipArchiveV1Limits,
        initialMetadataBytes: Long,
    ): CentralDirectory {
        var position = eocd.centralDirectoryOffset
        var metadataBytes = initialMetadataBytes
        var declaredCompressedBytes = 0L
        var declaredUncompressedBytes = 0L
        val collisionNames = mutableMapOf<String, String>()
        val entries = ArrayList<CentralEntry>(eocd.entryCount)

        repeat(eocd.entryCount) {
            val header = source.readBytes(position, CENTRAL_DIRECTORY_HEADER_BYTES)
            if (header.u32(0) != CENTRAL_DIRECTORY_SIGNATURE) {
                fail(
                    ZipArchiveV1FailureKind.FORMAT,
                    "ZIP central directory contains an unsupported record.",
                )
            }
            val versionMadeBy = header.u16(4)
            val versionNeeded = header.u16(6)
            val flags = header.u16(8)
            val compressionMethod = header.u16(10)
            val modificationTime = header.u16(12)
            val modificationDate = header.u16(14)
            val crc32 = header.u32(16)
            val compressedSize = header.u32(20)
            val uncompressedSize = header.u32(24)
            val nameLength = header.u16(28)
            val extraLength = header.u16(30)
            val commentLength = header.u16(32)
            val diskStart = header.u16(34)
            val externalAttributes = header.u32(38)
            val localHeaderOffset = header.u32(42)

            if (
                compressedSize == UINT32_SENTINEL ||
                uncompressedSize == UINT32_SENTINEL ||
                localHeaderOffset == UINT32_SENTINEL ||
                diskStart == UINT16_SENTINEL
            ) {
                fail(
                    ZipArchiveV1FailureKind.FORMAT,
                    "ZIP64 entry fields are not supported.",
                )
            }
            if (diskStart != 0) {
                fail(
                    ZipArchiveV1FailureKind.FORMAT,
                    "ZIP entry starts on another disk.",
                )
            }
            validateVersionFlagsAndMethod(versionNeeded, flags, compressionMethod)
            if (extraLength != 0) {
                fail(
                    ZipArchiveV1FailureKind.FORMAT,
                    "ZIP v1 rejects all local and central extra fields.",
                )
            }
            if (nameLength == 0) {
                fail(
                    ZipArchiveV1FailureKind.PATH,
                    "ZIP entry path is empty.",
                )
            }
            if (nameLength > limits.maximumEntryNameBytes) {
                fail(
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP entry name exceeds ${limits.maximumEntryNameBytes} bytes.",
                )
            }

            val variableLength = checkedAdd(
                nameLength.toLong(),
                checkedAdd(
                    extraLength.toLong(),
                    commentLength.toLong(),
                    ZipArchiveV1FailureKind.INTEGRITY,
                    "ZIP central metadata length overflows.",
                ),
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP central metadata length overflows.",
            )
            val recordLength = checkedAdd(
                CENTRAL_DIRECTORY_HEADER_BYTES.toLong(),
                variableLength,
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP central record length overflows.",
            )
            val recordEnd = checkedRangeEnd(
                source = source,
                offset = position,
                length = recordLength,
                kind = ZipArchiveV1FailureKind.INTEGRITY,
                message = "ZIP central record is truncated.",
            )
            if (recordEnd > eocd.offset) {
                fail(
                    ZipArchiveV1FailureKind.INTEGRITY,
                    "ZIP central record escapes the central directory.",
                )
            }
            val rawName = source.readBytes(
                position + CENTRAL_DIRECTORY_HEADER_BYTES,
                nameLength,
            )
            val path = decodeAndValidatePath(
                rawName = rawName,
                flags = flags,
                maximumDepth = limits.maximumPathDepth,
            )
            val collisionKey = asciiLowercase(path)
            collisionNames.putIfAbsent(collisionKey, path)?.let { existing ->
                fail(
                    ZipArchiveV1FailureKind.PATH,
                    "ZIP paths collide after ASCII case folding: $existing / $path",
                )
            }
            validateRegularFile(
                path = path,
                versionMadeBy = versionMadeBy,
                externalAttributes = externalAttributes,
            )
            if (uncompressedSize > limits.maximumSingleFileBytes) {
                fail(
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP entry exceeds ${limits.maximumSingleFileBytes} bytes: $path",
                )
            }

            declaredCompressedBytes = checkedAdd(
                declaredCompressedBytes,
                compressedSize,
                ZipArchiveV1FailureKind.QUOTA,
                "ZIP compressed size overflows its quota.",
            )
            declaredUncompressedBytes = checkedAdd(
                declaredUncompressedBytes,
                uncompressedSize,
                ZipArchiveV1FailureKind.QUOTA,
                "ZIP uncompressed size overflows its quota.",
            )
            if (declaredUncompressedBytes > limits.maximumTotalUncompressedBytes) {
                fail(
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP expands beyond ${limits.maximumTotalUncompressedBytes} bytes.",
                )
            }
            enforceCompressionRatio(
                compressedBytes = compressedSize,
                uncompressedBytes = uncompressedSize,
                maximumRatio = limits.maximumCompressionRatio,
                label = "ZIP entry $path",
            )

            metadataBytes = enforceMetadataQuota(
                checkedAdd(
                    metadataBytes,
                    recordLength,
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP metadata size overflows.",
                ),
                limits,
            )
            entries += CentralEntry(
                path = path,
                rawName = rawName,
                versionNeeded = versionNeeded,
                flags = flags,
                compressionMethod = compressionMethod,
                modificationTime = modificationTime,
                modificationDate = modificationDate,
                crc32 = crc32,
                compressedSize = compressedSize,
                uncompressedSize = uncompressedSize,
                localHeaderOffset = localHeaderOffset,
            )
            position = recordEnd
        }
        if (position != eocd.offset) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP central-directory size/count contains trailing or unknown records.",
            )
        }
        return CentralDirectory(
            entries = entries,
            declaredCompressedBytes = declaredCompressedBytes,
            declaredUncompressedBytes = declaredUncompressedBytes,
            metadataBytes = metadataBytes,
        )
    }

    private fun readAndMatchLocalEntry(
        source: RandomAccessSource,
        entry: CentralEntry,
        limits: ZipArchiveV1Limits,
        currentMetadataBytes: Long,
    ): LocalEntryResult {
        val header = source.readBytes(entry.localHeaderOffset, LOCAL_FILE_HEADER_BYTES)
        if (header.u32(0) != LOCAL_FILE_HEADER_SIGNATURE) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP local file header is missing for ${entry.path}.",
            )
        }
        val versionNeeded = header.u16(4)
        val flags = header.u16(6)
        val compressionMethod = header.u16(8)
        val modificationTime = header.u16(10)
        val modificationDate = header.u16(12)
        val crc32 = header.u32(14)
        val compressedSize = header.u32(18)
        val uncompressedSize = header.u32(22)
        val nameLength = header.u16(26)
        val extraLength = header.u16(28)

        if (
            versionNeeded != entry.versionNeeded ||
            flags != entry.flags ||
            compressionMethod != entry.compressionMethod ||
            modificationTime != entry.modificationTime ||
            modificationDate != entry.modificationDate
        ) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP local/central headers disagree for ${entry.path}.",
            )
        }
        if (
            compressedSize == UINT32_SENTINEL ||
            uncompressedSize == UINT32_SENTINEL
        ) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP64 local entry fields are not supported.",
            )
        }
        if (extraLength != 0) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP v1 rejects all local and central extra fields.",
            )
        }
        if (nameLength != entry.rawName.size) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP local/central name lengths disagree for ${entry.path}.",
            )
        }
        val rawLocalName = source.readBytes(
            entry.localHeaderOffset + LOCAL_FILE_HEADER_BYTES,
            nameLength,
        )
        if (!rawLocalName.contentEquals(entry.rawName)) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP local/central names disagree for ${entry.path}.",
            )
        }

        val hasDataDescriptor = flags and FLAG_DATA_DESCRIPTOR != 0
        if (hasDataDescriptor) {
            if (crc32 != 0L || compressedSize != 0L || uncompressedSize != 0L) {
                fail(
                    ZipArchiveV1FailureKind.FORMAT,
                    "Descriptor-backed local entry fields must be zero: ${entry.path}.",
                )
            }
        } else if (
            crc32 != entry.crc32 ||
            compressedSize != entry.compressedSize ||
            uncompressedSize != entry.uncompressedSize
        ) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP local/central sizes or CRC disagree for ${entry.path}.",
            )
        }

        val dataOffset = checkedAdd(
            entry.localHeaderOffset,
            LOCAL_FILE_HEADER_BYTES + nameLength.toLong(),
            ZipArchiveV1FailureKind.INTEGRITY,
            "ZIP local data offset overflows for ${entry.path}.",
        )
        val dataEnd = checkedRangeEnd(
            source = source,
            offset = dataOffset,
            length = entry.compressedSize,
            kind = ZipArchiveV1FailureKind.INTEGRITY,
            message = "ZIP compressed data is truncated for ${entry.path}.",
        )
        var nextOffset = dataEnd
        var descriptorBytes = 0L
        if (hasDataDescriptor) {
            val descriptor = source.readBytes(dataEnd, DATA_DESCRIPTOR_BYTES)
            if (descriptor.u32(0) != DATA_DESCRIPTOR_SIGNATURE) {
                fail(
                    ZipArchiveV1FailureKind.FORMAT,
                    "ZIP v1 requires a signed 32-bit data descriptor for ${entry.path}.",
                )
            }
            if (
                descriptor.u32(4) != entry.crc32 ||
                descriptor.u32(8) != entry.compressedSize ||
                descriptor.u32(12) != entry.uncompressedSize
            ) {
                fail(
                    ZipArchiveV1FailureKind.INTEGRITY,
                    "ZIP data descriptor disagrees with central metadata for ${entry.path}.",
                )
            }
            descriptorBytes = DATA_DESCRIPTOR_BYTES.toLong()
            nextOffset = checkedAdd(
                nextOffset,
                descriptorBytes,
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP data descriptor range overflows for ${entry.path}.",
            )
        }

        entry.dataOffset = dataOffset
        val localMetadataBytes =
            LOCAL_FILE_HEADER_BYTES + nameLength.toLong() + descriptorBytes
        val metadataBytes = enforceMetadataQuota(
            checkedAdd(
                currentMetadataBytes,
                localMetadataBytes,
                ZipArchiveV1FailureKind.QUOTA,
                "ZIP metadata size overflows.",
            ),
            limits,
        )
        return LocalEntryResult(
            nextOffset = nextOffset,
            metadataBytes = metadataBytes,
        )
    }

    private fun validatePayload(
        source: RandomAccessSource,
        entry: CentralEntry,
        limits: ZipArchiveV1Limits,
        currentTotalBytes: Long,
        extractBytes: Boolean,
        entrySink: ZipArchiveV1EntrySink?,
    ): ValidatedPayload {
        val maximumRemainingTotal =
            limits.maximumTotalUncompressedBytes - currentTotalBytes
        val maximumEntryBytes = minOf(
            limits.maximumSingleFileBytes,
            maximumRemainingTotal,
            maximumExpandedBytes(
                compressedBytes = entry.compressedSize,
                maximumRatio = limits.maximumCompressionRatio,
            ),
        )
        val collector = if (extractBytes) {
            if (entry.uncompressedSize > Int.MAX_VALUE) {
                fail(
                    ZipArchiveV1FailureKind.QUOTA,
                    "ZIP entry is too large to return as bytes: ${entry.path}.",
                )
            }
            PayloadCollector(entry.uncompressedSize.toInt())
        } else {
            null
        }
        val streamedEntry = entrySink?.let { sink ->
            ZipArchiveV1Entry(
                path = entry.path,
                compressionMethod = entry.compressionMethod,
                compressedBytes = entry.compressedSize,
                uncompressedBytes = entry.uncompressedSize,
                crc32 = entry.crc32,
            ).also { sinkEntry ->
                invokeEntrySink(
                    sink = sink,
                    entry = sinkEntry,
                    operation = "begin",
                ) {
                    it.begin(sinkEntry)
                }
            }
        }
        val crc = CRC32()
        val actualBytes = when (entry.compressionMethod) {
            COMPRESSION_STORED -> validateStoredPayload(
                source = source,
                entry = entry,
                crc = crc,
                maximumBytes = maximumEntryBytes,
                collector = collector,
                sink = entrySink,
                sinkEntry = streamedEntry,
            )

            COMPRESSION_DEFLATED -> validateDeflatedPayload(
                source = source,
                entry = entry,
                crc = crc,
                maximumBytes = maximumEntryBytes,
                collector = collector,
                sink = entrySink,
                sinkEntry = streamedEntry,
            )

            else -> error("Compression method was validated before payload reading.")
        }
        if (actualBytes != entry.uncompressedSize) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP actual size differs from metadata for ${entry.path}.",
            )
        }
        if (crc.value != entry.crc32) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "ZIP CRC-32 mismatch for ${entry.path}.",
            )
        }
        enforceCompressionRatio(
            compressedBytes = entry.compressedSize,
            uncompressedBytes = actualBytes,
            maximumRatio = limits.maximumCompressionRatio,
            label = "ZIP entry ${entry.path}",
        )
        streamedEntry?.let { sinkEntry ->
            invokeEntrySink(
                sink = checkNotNull(entrySink),
                entry = sinkEntry,
                operation = "end",
            ) {
                it.end(sinkEntry)
            }
        }
        return ValidatedPayload(
            actualBytes = actualBytes,
            bytes = collector?.complete(),
        )
    }

    private fun validateStoredPayload(
        source: RandomAccessSource,
        entry: CentralEntry,
        crc: CRC32,
        maximumBytes: Long,
        collector: PayloadCollector?,
        sink: ZipArchiveV1EntrySink?,
        sinkEntry: ZipArchiveV1Entry?,
    ): Long {
        if (entry.compressedSize != entry.uncompressedSize) {
            fail(
                ZipArchiveV1FailureKind.INTEGRITY,
                "STORED entry sizes differ for ${entry.path}.",
            )
        }
        if (entry.uncompressedSize > maximumBytes) {
            fail(
                ZipArchiveV1FailureKind.QUOTA,
                "ZIP actual bytes exceed the remaining quota for ${entry.path}.",
            )
        }
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var position = entry.dataOffset
        var remaining = entry.compressedSize
        while (remaining > 0L) {
            val count = minOf(remaining, buffer.size.toLong()).toInt()
            source.readFully(position, buffer, 0, count)
            crc.update(buffer, 0, count)
            collector?.append(buffer, count)
            writeEntrySink(
                sink = sink,
                entry = sinkEntry,
                bytes = buffer,
                length = count,
            )
            position += count
            remaining -= count
        }
        return entry.uncompressedSize
    }

    private fun validateDeflatedPayload(
        source: RandomAccessSource,
        entry: CentralEntry,
        crc: CRC32,
        maximumBytes: Long,
        collector: PayloadCollector?,
        sink: ZipArchiveV1EntrySink?,
        sinkEntry: ZipArchiveV1Entry?,
    ): Long {
        val inflater = Inflater(true)
        val compressedBuffer = ByteArray(STREAM_BUFFER_BYTES)
        val outputBuffer = ByteArray(STREAM_BUFFER_BYTES)
        var sourcePosition = entry.dataOffset
        var compressedRemaining = entry.compressedSize
        var actualBytes = 0L
        try {
            while (!inflater.finished()) {
                if (inflater.needsDictionary()) {
                    fail(
                        ZipArchiveV1FailureKind.FORMAT,
                        "DEFLATED entry requires a dictionary: ${entry.path}.",
                    )
                }
                if (inflater.needsInput()) {
                    if (compressedRemaining == 0L) {
                        fail(
                            ZipArchiveV1FailureKind.INTEGRITY,
                            "DEFLATED stream is truncated for ${entry.path}.",
                        )
                    }
                    val count = minOf(
                        compressedRemaining,
                        compressedBuffer.size.toLong(),
                    ).toInt()
                    source.readFully(sourcePosition, compressedBuffer, 0, count)
                    sourcePosition += count
                    compressedRemaining -= count
                    inflater.setInput(compressedBuffer, 0, count)
                }

                val count = try {
                    inflater.inflate(outputBuffer)
                } catch (failure: DataFormatException) {
                    throw ZipArchiveV1Exception(
                        ZipArchiveV1FailureKind.INTEGRITY,
                        "DEFLATED stream is malformed for ${entry.path}.",
                        failure,
                    )
                }
                if (count > 0) {
                    actualBytes = checkedAdd(
                        actualBytes,
                        count.toLong(),
                        ZipArchiveV1FailureKind.QUOTA,
                        "ZIP actual size overflows for ${entry.path}.",
                    )
                    if (actualBytes > maximumBytes) {
                        fail(
                            ZipArchiveV1FailureKind.QUOTA,
                            "ZIP actual bytes exceed the remaining quota for ${entry.path}.",
                        )
                    }
                    crc.update(outputBuffer, 0, count)
                    collector?.append(outputBuffer, count)
                    writeEntrySink(
                        sink = sink,
                        entry = sinkEntry,
                        bytes = outputBuffer,
                        length = count,
                    )
                } else if (
                    !inflater.finished() &&
                    !inflater.needsInput() &&
                    !inflater.needsDictionary()
                ) {
                    fail(
                        ZipArchiveV1FailureKind.INTEGRITY,
                        "DEFLATED stream made no progress for ${entry.path}.",
                    )
                }
            }
            if (compressedRemaining != 0L || inflater.remaining != 0) {
                fail(
                    ZipArchiveV1FailureKind.INTEGRITY,
                    "DEFLATED stream has trailing compressed bytes for ${entry.path}.",
                )
            }
            return actualBytes
        } finally {
            inflater.end()
        }
    }

    private fun writeEntrySink(
        sink: ZipArchiveV1EntrySink?,
        entry: ZipArchiveV1Entry?,
        bytes: ByteArray,
        length: Int,
    ) {
        if (sink == null) {
            check(entry == null)
            return
        }
        val sinkEntry = checkNotNull(entry)
        check(length in 1..STREAM_BUFFER_BYTES)
        invokeEntrySink(
            sink = sink,
            entry = sinkEntry,
            operation = "write",
        ) {
            it.write(bytes, 0, length)
        }
    }

    private inline fun invokeEntrySink(
        sink: ZipArchiveV1EntrySink,
        entry: ZipArchiveV1Entry,
        operation: String,
        callback: (ZipArchiveV1EntrySink) -> Unit,
    ) {
        try {
            callback(sink)
        } catch (failure: ZipArchiveV1EntrySinkException) {
            throw failure
        } catch (failure: Exception) {
            throw ZipArchiveV1EntrySinkException(
                "ZIP entry sink failed during $operation for ${entry.path}.",
                failure,
            )
        }
    }

    private class PayloadCollector(
        size: Int,
    ) {
        private val bytes = ByteArray(size)
        private var position = 0
        private var overflowed = false

        fun append(
            source: ByteArray,
            count: Int,
        ) {
            val writable = minOf(count, bytes.size - position)
            if (writable > 0) {
                source.copyInto(
                    destination = bytes,
                    destinationOffset = position,
                    startIndex = 0,
                    endIndex = writable,
                )
                position += writable
            }
            if (writable != count) {
                overflowed = true
            }
        }

        fun complete(): ByteArray {
            check(!overflowed && position == bytes.size) {
                "Validated ZIP payload collection did not match its declared size."
            }
            return bytes
        }
    }

    private fun validateVersionFlagsAndMethod(
        versionNeeded: Int,
        flags: Int,
        compressionMethod: Int,
    ) {
        if (flags and FLAG_ENCRYPTED != 0) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "Encrypted ZIP entries are not supported.",
            )
        }
        if (flags and ALLOWED_FLAGS.inv() and UINT16_MASK != 0) {
            fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP entry uses unsupported general-purpose flags.",
            )
        }
        when (compressionMethod) {
            COMPRESSION_STORED -> {
                if (versionNeeded !in MINIMUM_STORED_VERSION..MAXIMUM_VERSION_NEEDED) {
                    fail(
                        ZipArchiveV1FailureKind.FORMAT,
                        "STORED entry has an unsupported version-needed field.",
                    )
                }
                if (flags and FLAG_DATA_DESCRIPTOR != 0) {
                    fail(
                        ZipArchiveV1FailureKind.FORMAT,
                        "STORED entries may not use a data descriptor in ZIP v1.",
                    )
                }
            }

            COMPRESSION_DEFLATED -> {
                if (versionNeeded !in MINIMUM_DEFLATED_VERSION..MAXIMUM_VERSION_NEEDED) {
                    fail(
                        ZipArchiveV1FailureKind.FORMAT,
                        "DEFLATED entry has an unsupported version-needed field.",
                    )
                }
            }

            else -> fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP compression method $compressionMethod is not supported.",
            )
        }
    }

    private fun decodeAndValidatePath(
        rawName: ByteArray,
        flags: Int,
        maximumDepth: Int,
    ): String {
        val containsNonAsciiByte = rawName.any { byte -> byte.toInt() and 0x80 != 0 }
        if (containsNonAsciiByte && flags and FLAG_UTF8 == 0) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry path contains non-ASCII bytes without the UTF-8 flag.",
            )
        }
        val path = try {
            R2Utf8.decode(rawName, "ZIP entry path")
        } catch (_: IllegalArgumentException) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry path is not valid UTF-8.",
            )
        }
        if (
            path.startsWith('/') ||
            path.length >= 2 &&
            path[0].isAsciiLetter() &&
            path[1] == ':'
        ) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry path is absolute or has a drive prefix: ${path.asPrintablePath()}",
            )
        }
        if ('\\' in path) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry path contains a backslash: ${path.asPrintablePath()}",
            )
        }
        if (path.any { character -> Character.isISOControl(character.code) }) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry path contains a control character: ${path.asPrintablePath()}",
            )
        }
        val segments = path.split('/')
        if (
            segments.any { segment ->
                segment.isEmpty() || segment == "." || segment == ".."
            }
        ) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry path is not a canonical relative path: ${path.asPrintablePath()}",
            )
        }
        if (segments.size > maximumDepth) {
            fail(
                ZipArchiveV1FailureKind.QUOTA,
                "ZIP entry path exceeds depth $maximumDepth: ${path.asPrintablePath()}",
            )
        }
        return path
    }

    private fun validateRegularFile(
        path: String,
        versionMadeBy: Int,
        externalAttributes: Long,
    ) {
        if (path.endsWith('/') || externalAttributes and DOS_DIRECTORY_ATTRIBUTE != 0L) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry is a directory rather than a regular file: $path",
            )
        }
        if (externalAttributes and DOS_VOLUME_LABEL_ATTRIBUTE != 0L) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry is a DOS volume label rather than a regular file: $path",
            )
        }
        if (externalAttributes and DOS_RESERVED_TYPE_ATTRIBUTES != 0L) {
            fail(
                ZipArchiveV1FailureKind.PATH,
                "ZIP entry uses reserved DOS type attributes: $path",
            )
        }
        val creatorSystem = versionMadeBy ushr 8
        when (creatorSystem) {
            CREATOR_FAT -> Unit
            CREATOR_UNIX -> {
                val unixMode = (externalAttributes ushr 16).toInt() and UINT16_MASK
                val fileType = unixMode and UNIX_FILE_TYPE_MASK
                if (fileType != 0 && fileType != UNIX_REGULAR_FILE) {
                    fail(
                        ZipArchiveV1FailureKind.PATH,
                        "ZIP entry declares a non-regular Unix file type: $path",
                    )
                }
            }

            else -> fail(
                ZipArchiveV1FailureKind.FORMAT,
                "ZIP entry uses unsupported creator system $creatorSystem: $path",
            )
        }
    }

    private fun asciiLowercase(path: String): String = buildString(path.length) {
        path.forEach { character ->
            append(
                if (character in 'A'..'Z') {
                    (character.code + ASCII_CASE_OFFSET).toChar()
                } else {
                    character
                },
            )
        }
    }

    private fun enforceCompressionRatio(
        compressedBytes: Long,
        uncompressedBytes: Long,
        maximumRatio: Int,
        label: String,
    ) {
        if (uncompressedBytes == 0L) return
        if (compressedBytes == 0L) {
            fail(
                ZipArchiveV1FailureKind.QUOTA,
                "$label has an unbounded compression ratio.",
            )
        }
        val maximumExpanded = maximumExpandedBytes(compressedBytes, maximumRatio)
        if (uncompressedBytes > maximumExpanded) {
            fail(
                ZipArchiveV1FailureKind.QUOTA,
                "$label exceeds compression ratio $maximumRatio:1.",
            )
        }
    }

    private fun maximumExpandedBytes(
        compressedBytes: Long,
        maximumRatio: Int,
    ): Long = if (compressedBytes > Long.MAX_VALUE / maximumRatio) {
        Long.MAX_VALUE
    } else {
        compressedBytes * maximumRatio
    }

    private fun enforceMetadataQuota(
        metadataBytes: Long,
        limits: ZipArchiveV1Limits,
    ): Long {
        if (metadataBytes > limits.maximumMetadataBytes) {
            fail(
                ZipArchiveV1FailureKind.QUOTA,
                "ZIP metadata exceeds ${limits.maximumMetadataBytes} bytes.",
            )
        }
        return metadataBytes
    }

    private fun checkedRangeEnd(
        source: RandomAccessSource,
        offset: Long,
        length: Long,
        kind: ZipArchiveV1FailureKind,
        message: String,
    ): Long {
        if (offset < 0L || length < 0L || offset > source.size - length) {
            fail(kind, message)
        }
        return offset + length
    }

    private fun checkedAdd(
        left: Long,
        right: Long,
        kind: ZipArchiveV1FailureKind,
        message: String,
    ): Long {
        if (left < 0L || right < 0L || left > Long.MAX_VALUE - right) {
            fail(kind, message)
        }
        return left + right
    }

    private fun fail(
        kind: ZipArchiveV1FailureKind,
        message: String,
    ): Nothing = throw ZipArchiveV1Exception(kind, message)

    private fun String.asPrintablePath(): String = buildString {
        this@asPrintablePath.forEach { character ->
            if (character.code in 0x20..0x7e) {
                append(character)
            } else {
                append("\\u")
                append(character.code.toString(16).padStart(4, '0'))
            }
        }
    }

    private fun Char.isAsciiLetter(): Boolean =
        this in 'A'..'Z' || this in 'a'..'z'

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.u32(offset: Int): Long =
        (
            (this[offset].toLong() and 0xffL) or
                ((this[offset + 1].toLong() and 0xffL) shl 8) or
                ((this[offset + 2].toLong() and 0xffL) shl 16) or
                ((this[offset + 3].toLong() and 0xffL) shl 24)
            ) and UINT32_SENTINEL

    private data class EndOfCentralDirectory(
        val offset: Long,
        val centralDirectoryOffset: Long,
        val centralDirectorySize: Long,
        val entryCount: Int,
        val commentLength: Int,
    )

    private data class CentralDirectory(
        val entries: List<CentralEntry>,
        val declaredCompressedBytes: Long,
        val declaredUncompressedBytes: Long,
        val metadataBytes: Long,
    )

    private data class CentralEntry(
        val path: String,
        val rawName: ByteArray,
        val versionNeeded: Int,
        val flags: Int,
        val compressionMethod: Int,
        val modificationTime: Int,
        val modificationDate: Int,
        val crc32: Long,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
        var dataOffset: Long = -1L,
    )

    private data class LocalEntryResult(
        val nextOffset: Long,
        val metadataBytes: Long,
    )

    private data class ValidatedPayload(
        val actualBytes: Long,
        val bytes: ByteArray?,
    )

    private interface RandomAccessSource : Closeable {
        val size: Long

        fun readFully(
            offset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        )

        fun readBytes(
            offset: Long,
            length: Int,
        ): ByteArray = ByteArray(length).also { bytes ->
            readFully(offset, bytes, 0, length)
        }
    }

    private class ByteArraySource(
        private val bytes: ByteArray,
    ) : RandomAccessSource {
        override val size: Long = bytes.size.toLong()

        override fun readFully(
            offset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ) {
            if (
                offset < 0L ||
                offset > Int.MAX_VALUE ||
                length < 0 ||
                destinationOffset < 0 ||
                destinationOffset > destination.size - length ||
                offset > size - length
            ) {
                throw EOFException("Read escapes ZIP container.")
            }
            bytes.copyInto(
                destination = destination,
                destinationOffset = destinationOffset,
                startIndex = offset.toInt(),
                endIndex = offset.toInt() + length,
            )
        }

        override fun close() = Unit
    }

    private class RandomAccessFileSource(
        private val file: RandomAccessFile,
    ) : RandomAccessSource {
        override val size: Long = file.length()

        override fun readFully(
            offset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ) {
            if (
                offset < 0L ||
                length < 0 ||
                destinationOffset < 0 ||
                destinationOffset > destination.size - length ||
                offset > size - length
            ) {
                throw EOFException("Read escapes ZIP container.")
            }
            file.seek(offset)
            file.readFully(destination, destinationOffset, length)
        }

        override fun close() = Unit
    }

    private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
    private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
    private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
    private const val DATA_DESCRIPTOR_SIGNATURE = 0x08074b50L

    private const val LOCAL_FILE_HEADER_BYTES = 30
    private const val CENTRAL_DIRECTORY_HEADER_BYTES = 46
    private const val END_OF_CENTRAL_DIRECTORY_FIXED_BYTES = 22L
    private const val DATA_DESCRIPTOR_BYTES = 16
    private const val MAXIMUM_ZIP_COMMENT_BYTES = 65_535L
    private const val STREAM_BUFFER_BYTES = 16 * 1024

    private const val COMPRESSION_STORED = 0
    private const val COMPRESSION_DEFLATED = 8
    private const val MINIMUM_STORED_VERSION = 10
    private const val MINIMUM_DEFLATED_VERSION = 20
    private const val MAXIMUM_VERSION_NEEDED = 20

    private const val FLAG_ENCRYPTED = 1
    private const val FLAG_DATA_DESCRIPTOR = 1 shl 3
    private const val FLAG_UTF8 = 1 shl 11
    private const val ALLOWED_FLAGS = FLAG_DATA_DESCRIPTOR or FLAG_UTF8

    private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code

    private const val CREATOR_FAT = 0
    private const val CREATOR_UNIX = 3
    private const val DOS_VOLUME_LABEL_ATTRIBUTE = 0x08L
    private const val DOS_DIRECTORY_ATTRIBUTE = 0x10L
    private const val DOS_RESERVED_TYPE_ATTRIBUTES = 0xc0L
    private const val UNIX_FILE_TYPE_MASK = 0xf000
    private const val UNIX_REGULAR_FILE = 0x8000
    private const val UINT16_MASK = 0xffff
    private const val UINT16_SENTINEL = 0xffff
    private const val UINT32_SENTINEL = 0xffff_ffffL
}
