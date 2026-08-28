package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Boundary selected by a capacity instrumentation test.
 *
 * STORED archives bind the container quota because ZIP headers make the container slightly larger
 * than its expanded payload. DEFLATED archives instead bind the expanded-byte quota while
 * retaining enough compression to remain below the container cap.
 */
internal enum class R2CapacityArchiveBoundary(
    val compression: R2CapacityZipCompression,
    internal val quotaDeltaBytes: Long,
) {
    STORED_CONTAINER_INCLUSIVE(
        compression = R2CapacityZipCompression.STORED,
        quotaDeltaBytes = 0L,
    ),
    STORED_CONTAINER_PLUS_ONE(
        compression = R2CapacityZipCompression.STORED,
        quotaDeltaBytes = 1L,
    ),
    DEFLATED_UNCOMPRESSED_INCLUSIVE(
        compression = R2CapacityZipCompression.DEFLATED,
        quotaDeltaBytes = 0L,
    ),
    DEFLATED_UNCOMPRESSED_PLUS_ONE(
        compression = R2CapacityZipCompression.DEFLATED,
        quotaDeltaBytes = 1L,
    ),
}

internal enum class R2CapacityZipCompression(
    internal val method: Int,
    internal val minimumVersion: Int,
) {
    STORED(
        method = 0,
        minimumVersion = 10,
    ),
    DEFLATED(
        method = 8,
        minimumVersion = 20,
    ),
}

/**
 * Small immutable evidence for one generated caller source.
 *
 * Source bodies are never retained here. [seed] and [size] identify the deterministic replayable
 * generator used by the writer, while CRC/SHA/compressed size bind both ZIP passes.
 */
internal data class R2CapacitySourceDescriptor(
    val path: String,
    val size: Long,
    val sha256: String,
    val crc32: Long,
    val compressedSize: Long,
    val seed: Int,
)

/**
 * A generated R2 input archive and the metadata needed to submit it through the private Binder
 * capacity gate.
 */
internal class R2CapacityArchiveFixture private constructor(
    val requestId: String,
    val input: File,
    val inputArchiveSha256: String,
    val requestManifestSha256: String,
    val sourceDescriptors: List<R2CapacitySourceDescriptor>,
    val boundary: R2CapacityArchiveBoundary,
    val containerBytes: Long,
    val uncompressedBytes: Long,
    val compressedPayloadBytes: Long,
    val projectId: String,
    val entrySourceUri: String,
    val compilerDistributionFingerprint: String,
    val declarationsFingerprint: String,
    val declarationProfileFingerprint: String,
) : AutoCloseable {

    val archiveSha256: String
        get() = inputArchiveSha256

    val manifestSha256: String
        get() = requestManifestSha256

    override fun close() {
        check(!input.exists() || input.delete()) {
            "R2 capacity input fixture could not be deleted."
        }
        check(!input.exists()) {
            "R2 capacity input fixture still exists after deletion."
        }
    }

    companion object {

        fun create(
            context: Context,
            requestId: String,
            boundary: R2CapacityArchiveBoundary,
        ): R2CapacityArchiveFixture =
            Builder(
                context = context,
                requestId = requestId,
                boundary = boundary,
            ).build()

        fun storedContainerInclusive(
            context: Context,
            requestId: String,
        ): R2CapacityArchiveFixture =
            create(
                context = context,
                requestId = requestId,
                boundary = R2CapacityArchiveBoundary.STORED_CONTAINER_INCLUSIVE,
            )

        fun storedContainerPlusOne(
            context: Context,
            requestId: String,
        ): R2CapacityArchiveFixture =
            create(
                context = context,
                requestId = requestId,
                boundary = R2CapacityArchiveBoundary.STORED_CONTAINER_PLUS_ONE,
            )

        fun deflatedUncompressedInclusive(
            context: Context,
            requestId: String,
        ): R2CapacityArchiveFixture =
            create(
                context = context,
                requestId = requestId,
                boundary = R2CapacityArchiveBoundary.DEFLATED_UNCOMPRESSED_INCLUSIVE,
            )

        fun deflatedUncompressedPlusOne(
            context: Context,
            requestId: String,
        ): R2CapacityArchiveFixture =
            create(
                context = context,
                requestId = requestId,
                boundary = R2CapacityArchiveBoundary.DEFLATED_UNCOMPRESSED_PLUS_ONE,
            )

        private class Builder(
            private val context: Context,
            private val requestId: String,
            private val boundary: R2CapacityArchiveBoundary,
        ) {

            private val compilerMetadata = CompilerDistributionMetadata.load(context)
            private val sourceSpecs = capacitySourceSpecs()
            private val compression = boundary.compression

            init {
                require(requestId.isNotBlank()) {
                    "R2 capacity request ID must not be blank."
                }
                require(requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS) {
                    "R2 capacity request ID exceeds the contract limit."
                }
                check(sourceSpecs.map(SourceSpec::path) == sourceSpecs.map(SourceSpec::path).sorted()) {
                    "R2 capacity source inventory must be strictly sorted."
                }
                check(sourceSpecs.map(SourceSpec::path).distinct().size == sourceSpecs.size) {
                    "R2 capacity source inventory contains a duplicate path."
                }
            }

            fun build(): R2CapacityArchiveFixture {
                val resolved = resolveSourceBoundary()
                val manifestPayload = ByteArrayPayload(resolved.manifest)
                val manifestMetrics = measurePayload(
                    payload = manifestPayload,
                    compression = compression,
                )
                val archiveEntries = buildList {
                    add(
                        ArchiveEntry(
                            path = R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH,
                            payload = manifestPayload,
                            metrics = manifestMetrics,
                        ),
                    )
                    resolved.sources.forEach { source ->
                        add(
                            ArchiveEntry(
                                path = source.spec.path,
                                payload = source.payload,
                                metrics = source.metrics,
                            ),
                        )
                    }
                }
                val uncompressedBytes = archiveEntries.sumExact {
                    it.metrics.uncompressedBytes
                }
                val compressedPayloadBytes = archiveEntries.sumExact {
                    it.metrics.compressedBytes
                }
                val expectedContainerBytes = Math.addExact(
                    compressedPayloadBytes,
                    zipRecordOverheadBytes(archiveEntries.map(ArchiveEntry::path)),
                )
                assertResolvedBoundary(
                    uncompressedBytes = uncompressedBytes,
                    expectedContainerBytes = expectedContainerBytes,
                )

                val input = File.createTempFile(
                    "r2-capacity-${boundary.name.lowercase()}-",
                    ".archive",
                    context.cacheDir,
                )
                val archiveEvidence = try {
                    writeArchive(
                        file = input,
                        entries = archiveEntries,
                        compression = compression,
                    )
                } catch (failure: Throwable) {
                    input.delete()
                    throw failure
                }
                check(archiveEvidence.containerBytes == expectedContainerBytes) {
                    "R2 capacity ZIP bytes differ from the precomputed ZIP32 layout."
                }
                check(input.length() == expectedContainerBytes) {
                    "R2 capacity ZIP file length differs from its written bytes."
                }

                return R2CapacityArchiveFixture(
                    requestId = requestId,
                    input = input,
                    inputArchiveSha256 = archiveEvidence.sha256,
                    requestManifestSha256 = sha256(resolved.manifest),
                    sourceDescriptors = resolved.sources.map(SourceEntry::descriptor),
                    boundary = boundary,
                    containerBytes = expectedContainerBytes,
                    uncompressedBytes = uncompressedBytes,
                    compressedPayloadBytes = compressedPayloadBytes,
                    projectId = PROJECT_ID,
                    entrySourceUri = ENTRY_SOURCE_URI,
                    compilerDistributionFingerprint =
                        compilerMetadata.distributionFingerprint,
                    declarationsFingerprint = compilerMetadata.declarationsFingerprint,
                    declarationProfileFingerprint = R2TrustedDeclarations.loadForProfile(
                        context,
                        R2ProjectProfile.PROVISIONAL_RHINO,
                    ).profileFingerprint,
                )
            }

            private fun resolveSourceBoundary(): ResolvedSources {
                val adjustableSpec = sourceSpecs.single { spec ->
                    spec.fixedSize == null
                }
                val fixedSources = sourceSpecs.mapNotNull { spec ->
                    val fixedSize = spec.fixedSize ?: return@mapNotNull null
                    sourceEntry(
                        spec = spec,
                        size = fixedSize,
                    )
                }
                val fixedSourceBytes = fixedSources.sumExact {
                    it.metrics.uncompressedBytes
                }
                val targetPayloadBytes = when (compression) {
                    R2CapacityZipCompression.STORED -> {
                        val targetContainerBytes = Math.addExact(
                            R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES,
                            boundary.quotaDeltaBytes,
                        )
                        Math.subtractExact(
                            targetContainerBytes,
                            zipRecordOverheadBytes(
                                listOf(R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH) +
                                    sourceSpecs.map(SourceSpec::path),
                            ),
                        )
                    }
                    R2CapacityZipCompression.DEFLATED -> Math.addExact(
                        R2_INPUT_ARCHIVE_MAXIMUM_EXPANDED_BYTES,
                        boundary.quotaDeltaBytes,
                    )
                }

                var candidateSize = Math.subtractExact(
                    TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
                    INITIAL_MANIFEST_SIZE_ALLOWANCE_BYTES,
                )
                repeat(MAXIMUM_FIXED_POINT_ATTEMPTS) {
                    val lastSource = sourceEntry(
                        spec = adjustableSpec,
                        size = candidateSize,
                    )
                    val sources = (fixedSources + lastSource).sortedBy { source ->
                        source.spec.path
                    }
                    val manifest = renderManifest(sources)
                    val requiredSize = Math.subtractExact(
                        Math.subtractExact(targetPayloadBytes, fixedSourceBytes),
                        manifest.size.toLong(),
                    )
                    require(requiredSize >= lastSource.payload.minimumSize) {
                        "R2 capacity boundary leaves no room for a valid final TypeScript source."
                    }
                    require(
                        requiredSize <= TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
                    ) {
                        "R2 capacity boundary requires a source above the single-file limit."
                    }
                    if (requiredSize == candidateSize) {
                        return ResolvedSources(
                            sources = sources,
                            manifest = manifest,
                        )
                    }
                    candidateSize = requiredSize
                }
                error("R2 capacity manifest/source-size fixed point did not converge.")
            }

            private fun sourceEntry(
                spec: SourceSpec,
                size: Long,
            ): SourceEntry {
                val payload = CapacitySourcePayload(
                    spec = spec,
                    size = size,
                )
                return SourceEntry(
                    spec = spec,
                    payload = payload,
                    metrics = measurePayload(
                        payload = payload,
                        compression = compression,
                    ),
                )
            }

            private fun renderManifest(sources: List<SourceEntry>): ByteArray {
                val files = sources.map { source ->
                    R2ProjectArchiveFile(
                        path = source.spec.path,
                        size = source.metrics.uncompressedBytes,
                        sha256 = source.metrics.sha256,
                    )
                }
                val inventoryFingerprint =
                    R2ProvisionalRequestManifest.inventoryFingerprint(files)
                val declarationProfileFingerprint = R2TrustedDeclarations.loadForProfile(
                    context,
                    R2ProjectProfile.PROVISIONAL_RHINO,
                ).profileFingerprint
                val manifest = buildString(4 * 1024) {
                    append("{\"schema\":")
                    appendJsonString(R2InputArchiveSchemaV1.SCHEMA)
                    append(",\"revision\":")
                    append(R2InputArchiveSchemaV1.REVISION)
                    append(",\"inputQuotaRevision\":")
                    append(TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION)
                    append(",\"contractVersion\":")
                    append(TypeScriptCompilerContract.VERSION)
                    append(",\"contractHash\":")
                    appendJsonString(TypeScriptCompilerContract.HASH)
                    append(",\"inputArchiveTransportVersion\":")
                    append(TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION)
                    append(",\"requestId\":")
                    appendJsonString(requestId)
                    append(",\"projectId\":")
                    appendJsonString(PROJECT_ID)
                    append(",\"entrySourceUri\":")
                    appendJsonString(ENTRY_SOURCE_URI)
                    append(",\"entryPath\":")
                    appendJsonString(ENTRY_PATH)
                    append(",\"targetProfile\":")
                    appendJsonString(TypeScriptCompilerContract.PROFILE_RHINO)
                    append(",\"compilationMode\":")
                    appendJsonString(TypeScriptCompilerContract.MODE_DEVELOPMENT)
                    append(",\"profileSchemaRevision\":")
                    append(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
                    append(",\"profileRevision\":")
                    append(R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision)
                    append(",\"runtimeCapabilitySchemaRevision\":")
                    append(TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION)
                    append(",\"configSchemaRevision\":")
                    append(R2CompilationProfileRegistry.CONFIG_SCHEMA_REVISION)
                    append(",\"runtimeCapabilitiesFingerprint\":")
                    appendJsonString(R2RuntimeCapabilityMetadata.fingerprint)
                    append(",\"compilerPackage\":\"typescript\"")
                    append(",\"compilerVersion\":")
                    appendJsonString(compilerMetadata.version)
                    append(",\"compilerDistributionFingerprint\":")
                    appendJsonString(compilerMetadata.distributionFingerprint)
                    append(",\"declarationsFingerprint\":")
                    appendJsonString(compilerMetadata.declarationsFingerprint)
                    append(",\"declarationProfileFingerprint\":")
                    appendJsonString(declarationProfileFingerprint)
                    append(",\"configKind\":")
                    appendJsonString(R2CompilationProfileRegistry.CONFIG_KIND_NONE)
                    append(",\"callerInventoryFingerprint\":")
                    appendJsonString(inventoryFingerprint)
                    append(",\"rootPaths\":[")
                    files
                        .filterNot { file -> file.path.endsWith(".d.ts") }
                        .forEachIndexed { index, file ->
                        if (index > 0) append(',')
                        appendJsonString(file.path)
                    }
                    append("],\"files\":[")
                    files.forEachIndexed { index, file ->
                        if (index > 0) append(',')
                        append("{\"path\":")
                        appendJsonString(file.path)
                        append(",\"sha256\":")
                        appendJsonString(file.sha256)
                        append(",\"size\":")
                        append(file.size)
                        append('}')
                    }
                    append("]}")
                }
                return manifest.toByteArray(StandardCharsets.UTF_8)
            }

            private fun assertResolvedBoundary(
                uncompressedBytes: Long,
                expectedContainerBytes: Long,
            ) {
                when (compression) {
                    R2CapacityZipCompression.STORED -> {
                        val target = Math.addExact(
                            R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES,
                            boundary.quotaDeltaBytes,
                        )
                        check(expectedContainerBytes == target) {
                            "STORED R2 capacity fixture does not bind the container boundary."
                        }
                        check(
                            uncompressedBytes <=
                                R2_INPUT_ARCHIVE_MAXIMUM_EXPANDED_BYTES,
                        ) {
                            "STORED R2 capacity fixture unexpectedly exceeds expanded bytes."
                        }
                    }
                    R2CapacityZipCompression.DEFLATED -> {
                        val target = Math.addExact(
                            R2_INPUT_ARCHIVE_MAXIMUM_EXPANDED_BYTES,
                            boundary.quotaDeltaBytes,
                        )
                        check(uncompressedBytes == target) {
                            "DEFLATED R2 capacity fixture does not bind the expanded boundary."
                        }
                        check(
                            expectedContainerBytes <=
                                R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES,
                        ) {
                            "DEFLATED R2 capacity fixture also exceeds the container boundary."
                        }
                    }
                }
            }
        }
    }
}

private data class SourceSpec(
    val path: String,
    val ordinal: Int,
    val seed: Int,
    val kind: CapacitySourceKind,
    val fixedSize: Long?,
)

private enum class CapacitySourceKind {
    MAIN,
    PADDING_DECLARATION,
}

private data class SourceEntry(
    val spec: SourceSpec,
    val payload: CapacitySourcePayload,
    val metrics: PayloadMetrics,
) {
    val descriptor: R2CapacitySourceDescriptor
        get() = R2CapacitySourceDescriptor(
            path = spec.path,
            size = metrics.uncompressedBytes,
            sha256 = metrics.sha256,
            crc32 = metrics.crc32,
            compressedSize = metrics.compressedBytes,
            seed = spec.seed,
        )
}

private data class ResolvedSources(
    val sources: List<SourceEntry>,
    val manifest: ByteArray,
)

private data class ArchiveEntry(
    val path: String,
    val payload: ReplayablePayload,
    val metrics: PayloadMetrics,
)

private data class CentralRecord(
    val entry: ArchiveEntry,
    val name: ByteArray,
    val localOffset: Long,
)

private data class PayloadMetrics(
    val uncompressedBytes: Long,
    val compressedBytes: Long,
    val crc32: Long,
    val sha256: String,
)

private data class ArchiveEvidence(
    val containerBytes: Long,
    val sha256: String,
)

private interface ReplayablePayload {
    val size: Long

    fun replay(sink: ChunkSink)
}

private fun interface ChunkSink {
    fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    )
}

/**
 * Exact-size valid TypeScript source.
 *
 * The body is one block comment followed by a tiny exported constant. Filler contains only a
 * 64-character ASCII alphabet without '*' or '/', so it cannot close the comment. Every 8 KiB
 * xorshift block is repeated immediately once. This keeps scanning/token count tiny while yielding
 * moderate, deterministic DEFLATE compression rather than a >100:1 compression bomb.
 */
private class CapacitySourcePayload(
    private val spec: SourceSpec,
    override val size: Long,
) : ReplayablePayload {

    private val prefix = when (spec.kind) {
        CapacitySourceKind.MAIN -> buildString {
            repeat(CAPACITY_PADDING_DECLARATION_COUNT) { index ->
                append("/// <reference path=\"./padding-")
                append(index.toString().padStart(2, '0'))
                append(".d.ts\" />\n")
            }
            append("/* autojs6 deterministic R2 capacity main source\n")
        }
        CapacitySourceKind.PADDING_DECLARATION ->
            "/* autojs6 deterministic R2 capacity declaration ${spec.ordinal}\n"
    }.toByteArray(StandardCharsets.US_ASCII)
    private val suffix = when (spec.kind) {
        CapacitySourceKind.MAIN ->
            "\n*/\nexport const capacityValue: number = 1;\n"
        CapacitySourceKind.PADDING_DECLARATION ->
            "\n*/\nexport declare const capacityDeclaration${spec.ordinal}: number;\n"
    }.toByteArray(StandardCharsets.US_ASCII)

    val minimumSize: Long = prefix.size.toLong() + suffix.size

    init {
        require(size >= minimumSize) {
            "R2 capacity source is too small for its valid TypeScript envelope."
        }
        require(size <= TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES) {
            "R2 capacity source exceeds the single-file limit."
        }
    }

    override fun replay(sink: ChunkSink) {
        sink.write(prefix, 0, prefix.size)
        var remaining = size - minimumSize
        var state = spec.seed.takeIf { it != 0 } ?: NON_ZERO_FALLBACK_SEED
        val uniqueBlock = ByteArray(MEDIUM_ENTROPY_BLOCK_BYTES)
        while (remaining > 0L) {
            val uniqueBytes = minOf(
                remaining,
                uniqueBlock.size.toLong(),
            ).toInt()
            repeat(uniqueBytes) { index ->
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                uniqueBlock[index] =
                    MEDIUM_ENTROPY_ALPHABET[state ushr 26 and 0x3f].code.toByte()
            }
            sink.write(uniqueBlock, 0, uniqueBytes)
            remaining -= uniqueBytes
            if (remaining > 0L) {
                val repeatedBytes = minOf(remaining, uniqueBytes.toLong()).toInt()
                sink.write(uniqueBlock, 0, repeatedBytes)
                remaining -= repeatedBytes
            }
        }
        uniqueBlock.fill(0)
        sink.write(suffix, 0, suffix.size)
    }
}

private class ByteArrayPayload(
    private val bytes: ByteArray,
) : ReplayablePayload {

    override val size: Long = bytes.size.toLong()

    override fun replay(sink: ChunkSink) {
        if (bytes.isNotEmpty()) {
            sink.write(bytes, 0, bytes.size)
        }
    }
}

private fun measurePayload(
    payload: ReplayablePayload,
    compression: R2CapacityZipCompression,
): PayloadMetrics {
    val crc = CRC32()
    val sha256 = MessageDigest.getInstance(SHA_256)
    var uncompressedBytes = 0L
    val deflater = if (compression == R2CapacityZipCompression.DEFLATED) {
        RawDeflater(ChunkSink { _, _, _ -> })
    } else {
        null
    }
    val compressedBytes: Long
    try {
        payload.replay(
            ChunkSink { bytes, offset, length ->
                requireChunk(bytes, offset, length)
                crc.update(bytes, offset, length)
                sha256.update(bytes, offset, length)
                uncompressedBytes = Math.addExact(
                    uncompressedBytes,
                    length.toLong(),
                )
                deflater?.write(bytes, offset, length)
            },
        )
        compressedBytes = deflater?.finish() ?: uncompressedBytes
    } finally {
        deflater?.close()
    }
    check(uncompressedBytes == payload.size) {
        "Replayable R2 capacity payload emitted an unexpected byte count."
    }
    return PayloadMetrics(
        uncompressedBytes = uncompressedBytes,
        compressedBytes = compressedBytes,
        crc32 = crc.value,
        sha256 = sha256.digest().toLowerHex(),
    )
}

private fun writeArchive(
    file: File,
    entries: List<ArchiveEntry>,
    compression: R2CapacityZipCompression,
): ArchiveEvidence {
    require(entries.isNotEmpty() && entries.size <= UINT16_MAX) {
        "R2 capacity ZIP entry count is outside ZIP32."
    }
    val archiveDigest = MessageDigest.getInstance(SHA_256)
    var finalPosition = 0L
    FileOutputStream(file, false).use { fileOutput ->
        val output = DigestingCountingOutput(
            delegate = fileOutput,
            digest = archiveDigest,
        )
        val centralRecords = ArrayList<CentralRecord>(entries.size)
        entries.forEach { entry ->
            val name = asciiZipName(entry.path)
            val localOffset = output.position
            output.putU32(LOCAL_FILE_HEADER_SIGNATURE)
            output.putU16(compression.minimumVersion)
            output.putU16(0)
            output.putU16(compression.method)
            output.putU16(0)
            output.putU16(0)
            output.putU32(entry.metrics.crc32)
            output.putU32(entry.metrics.compressedBytes)
            output.putU32(entry.metrics.uncompressedBytes)
            output.putU16(name.size)
            output.putU16(0)
            output.write(name)
            writeAndVerifyPayload(
                output = output,
                entry = entry,
                compression = compression,
            )
            val expectedEnd = Math.addExact(
                localOffset,
                Math.addExact(
                    LOCAL_FILE_HEADER_BYTES + name.size.toLong(),
                    entry.metrics.compressedBytes,
                ),
            )
            check(output.position == expectedEnd) {
                "R2 capacity ZIP local record has an unexpected length."
            }
            centralRecords += CentralRecord(
                entry = entry,
                name = name,
                localOffset = localOffset,
            )
        }

        val centralOffset = output.position
        centralRecords.forEach { record ->
            output.putU32(CENTRAL_DIRECTORY_SIGNATURE)
            output.putU16(compression.minimumVersion)
            output.putU16(compression.minimumVersion)
            output.putU16(0)
            output.putU16(compression.method)
            output.putU16(0)
            output.putU16(0)
            output.putU32(record.entry.metrics.crc32)
            output.putU32(record.entry.metrics.compressedBytes)
            output.putU32(record.entry.metrics.uncompressedBytes)
            output.putU16(record.name.size)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU16(0)
            output.putU32(0)
            output.putU32(record.localOffset)
            output.write(record.name)
        }
        val centralSize = output.position - centralOffset
        output.putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
        output.putU16(0)
        output.putU16(0)
        output.putU16(centralRecords.size)
        output.putU16(centralRecords.size)
        output.putU32(centralSize)
        output.putU32(centralOffset)
        output.putU16(0)
        output.flush()
        fileOutput.fd.sync()
        finalPosition = output.position
    }
    return ArchiveEvidence(
        containerBytes = finalPosition,
        sha256 = archiveDigest.digest().toLowerHex(),
    )
}

private fun writeAndVerifyPayload(
    output: OutputStream,
    entry: ArchiveEntry,
    compression: R2CapacityZipCompression,
) {
    val crc = CRC32()
    val sha256 = MessageDigest.getInstance(SHA_256)
    var uncompressedBytes = 0L
    val deflater = if (compression == R2CapacityZipCompression.DEFLATED) {
        RawDeflater(
            ChunkSink { bytes, offset, length ->
                output.write(bytes, offset, length)
            },
        )
    } else {
        null
    }
    val compressedBytes: Long
    try {
        entry.payload.replay(
            ChunkSink { bytes, offset, length ->
                requireChunk(bytes, offset, length)
                crc.update(bytes, offset, length)
                sha256.update(bytes, offset, length)
                uncompressedBytes = Math.addExact(
                    uncompressedBytes,
                    length.toLong(),
                )
                if (deflater == null) {
                    output.write(bytes, offset, length)
                } else {
                    deflater.write(bytes, offset, length)
                }
            },
        )
        compressedBytes = deflater?.finish() ?: uncompressedBytes
    } finally {
        deflater?.close()
    }
    check(uncompressedBytes == entry.metrics.uncompressedBytes) {
        "R2 capacity ZIP replay changed the uncompressed byte count for ${entry.path}."
    }
    check(compressedBytes == entry.metrics.compressedBytes) {
        "R2 capacity ZIP replay changed the compressed byte count for ${entry.path}."
    }
    check(crc.value == entry.metrics.crc32) {
        "R2 capacity ZIP replay changed CRC-32 for ${entry.path}."
    }
    check(sha256.digest().toLowerHex() == entry.metrics.sha256) {
        "R2 capacity ZIP replay changed SHA-256 for ${entry.path}."
    }
}

private class RawDeflater(
    private val sink: ChunkSink,
) : AutoCloseable {

    private val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    private val outputBuffer = ByteArray(DEFLATE_BUFFER_BYTES)
    private var compressedBytes = 0L
    private var finishing = false
    private var closed = false

    fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        check(!finishing && !closed) {
            "Raw DEFLATE stream is already finishing or closed."
        }
        requireChunk(bytes, offset, length)
        if (length == 0) return
        check(deflater.needsInput()) {
            "Raw DEFLATE stream retained prior input."
        }
        deflater.setInput(bytes, offset, length)
        while (!deflater.needsInput()) {
            drainOnce()
        }
    }

    fun finish(): Long {
        check(!finishing && !closed) {
            "Raw DEFLATE stream was finished more than once."
        }
        finishing = true
        deflater.finish()
        while (!deflater.finished()) {
            drainOnce()
        }
        return compressedBytes
    }

    private fun drainOnce() {
        val count = deflater.deflate(outputBuffer)
        if (count > 0) {
            sink.write(outputBuffer, 0, count)
            compressedBytes = Math.addExact(compressedBytes, count.toLong())
            return
        }
        check(deflater.needsInput() || deflater.finished()) {
            "Raw DEFLATE stream made no progress."
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        outputBuffer.fill(0)
        deflater.end()
    }
}

private class DigestingCountingOutput(
    private val delegate: OutputStream,
    private val digest: MessageDigest,
) : OutputStream() {

    var position: Long = 0L
        private set

    override fun write(value: Int) {
        delegate.write(value)
        digest.update(value.toByte())
        position = Math.addExact(position, 1L)
    }

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        requireChunk(bytes, offset, length)
        delegate.write(bytes, offset, length)
        digest.update(bytes, offset, length)
        position = Math.addExact(position, length.toLong())
    }

    override fun flush() {
        delegate.flush()
    }
}

private fun DigestingCountingOutput.putU16(value: Int) {
    require(value in 0..UINT16_MAX) {
        "ZIP16 field is outside its unsigned range."
    }
    write(value and 0xff)
    write(value ushr 8 and 0xff)
}

private fun DigestingCountingOutput.putU32(value: Long) {
    require(value in 0L..UINT32_MAX) {
        "ZIP32 field is outside its unsigned range."
    }
    write((value and 0xff).toInt())
    write((value ushr 8 and 0xff).toInt())
    write((value ushr 16 and 0xff).toInt())
    write((value ushr 24 and 0xff).toInt())
}

private fun asciiZipName(path: String): ByteArray {
    require(
        path.isNotEmpty() &&
            path.length <= TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES &&
            path.all { character -> character.code in 0x20..0x7e },
    ) {
        "R2 capacity ZIP path must be bounded printable ASCII."
    }
    return path.toByteArray(StandardCharsets.US_ASCII)
}

private fun zipRecordOverheadBytes(paths: List<String>): Long {
    require(paths.isNotEmpty())
    return paths.fold(END_OF_CENTRAL_DIRECTORY_BYTES) { total, path ->
        val nameBytes = asciiZipName(path).size.toLong()
        Math.addExact(
            total,
            Math.addExact(
                LOCAL_FILE_HEADER_BYTES + CENTRAL_DIRECTORY_HEADER_BYTES,
                Math.multiplyExact(2L, nameBytes),
            ),
        )
    }
}

private inline fun <T> Iterable<T>.sumExact(selector: (T) -> Long): Long =
    fold(0L) { total, item ->
        Math.addExact(total, selector(item))
    }

private fun requireChunk(
    bytes: ByteArray,
    offset: Int,
    length: Int,
) {
    require(
        offset >= 0 &&
            length >= 0 &&
            offset <= bytes.size - length,
    ) {
        "R2 capacity stream chunk is outside its backing array."
    }
}

private fun capacitySourceSpecs(): List<SourceSpec> =
    listOf(
        SourceSpec(
            path = ENTRY_PATH,
            ordinal = 0,
            seed = CAPACITY_SEED_BASE.toInt(),
            kind = CapacitySourceKind.MAIN,
            fixedSize = MAIN_SOURCE_BYTES,
        ),
    ) + List(CAPACITY_PADDING_DECLARATION_COUNT) { index ->
        SourceSpec(
            path = "sources/padding-${index.toString().padStart(2, '0')}.d.ts",
            ordinal = index,
            seed = (
                CAPACITY_SEED_BASE xor
                    ((index + 1L) * CAPACITY_SEED_STEP)
                ).toInt(),
            kind = CapacitySourceKind.PADDING_DECLARATION,
            fixedSize = if (index + 1 == CAPACITY_PADDING_DECLARATION_COUNT) {
                null
            } else {
                TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES
            },
        )
    }

private fun StringBuilder.appendJsonString(value: String) {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
    append('"')
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance(SHA_256)
        .digest(bytes)
        .toLowerHex()

private fun ByteArray.toLowerHex(): String {
    val output = StringBuilder(size * 2)
    forEach { byte ->
        val value = byte.toInt() and 0xff
        output.append(HEX[value ushr 4])
        output.append(HEX[value and 0x0f])
    }
    return output.toString()
}

private const val PROJECT_ID = "capacity-project"
private const val ENTRY_PATH = "sources/main.ts"
private const val ENTRY_SOURCE_URI = "autojs://source/project/main.ts"
private const val CAPACITY_PADDING_DECLARATION_COUNT = 8
private const val MAIN_SOURCE_BYTES = 4L * 1024L
private const val INITIAL_MANIFEST_SIZE_ALLOWANCE_BYTES = 4L * 1024L
private const val MAXIMUM_FIXED_POINT_ATTEMPTS = 8

private const val MEDIUM_ENTROPY_BLOCK_BYTES = 8 * 1024
private const val MEDIUM_ENTROPY_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
private const val NON_ZERO_FALLBACK_SEED = 0x13579bdf
private const val CAPACITY_SEED_BASE = 0x6d2b79f5L
private const val CAPACITY_SEED_STEP = 0x9e3779b9L

private const val SHA_256 = "SHA-256"
private const val DEFLATE_BUFFER_BYTES = 16 * 1024
private const val UINT16_MAX = 0xffff
private const val UINT32_MAX = 0xffff_ffffL

private const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
private const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
private const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
private const val LOCAL_FILE_HEADER_BYTES = 30L
private const val CENTRAL_DIRECTORY_HEADER_BYTES = 46L
private const val END_OF_CENTRAL_DIRECTORY_BYTES = 22L

private val HEX = "0123456789abcdef".toCharArray()
