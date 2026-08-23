package io.github.supermonster003.autojs6.plugin.typescript

import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Entry
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1EntrySink
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

internal data class R2SpooledArchiveEntry(
    val path: String,
    val offset: Long,
    val byteLength: Long,
    val sha256: String,
)

internal data class R2FileBackedProjectRecord(
    val metadata: R2ProjectFileMetadata,
    val offset: Long,
)

internal data class R2FileBackedRawProjectConfigRecord(
    val descriptor: R2RawProjectConfigDescriptor,
    val offset: Long,
)

internal data class R2FileBackedPackageMetadataRecord(
    val metadata: R2PackageMetadataFileDescriptor,
    val offset: Long,
)

internal data class R2FileBackedPackageTargetRecord(
    val descriptor: R2PackageTargetFileDescriptor,
    val offset: Long,
)

internal data class R2FileBackedNodePackageTargetV2Record(
    val descriptor: R2NodePackageTargetV2FileDescriptor,
    val offset: Long,
)

internal interface R2PrivateProjectSpoolFileOperations {

    fun openOutput(file: File): FileOutputStream = FileOutputStream(file, false)

    fun afterOutputOpened() = Unit

    fun openReader(file: File): RandomAccessFile = RandomAccessFile(file, "r")

    fun delete(
        file: File,
        restoreWritable: Boolean,
    ) = deleteProjectSpool(file, restoreWritable)
}

private object R2SystemPrivateProjectSpoolFileOperations :
    R2PrivateProjectSpoolFileOperations

/**
 * Single app-private uncompressed VFS spool.
 *
 * ZIP payload callbacks append actual bytes transactionally without retaining entry-sized arrays;
 * nothing is published unless the enclosing validator succeeds. Only request-manifest.json may
 * later be read into a bounded byte array. Project sources and trusted declarations remain indexed
 * by offset and are exposed to the framer through bounded cursors.
 */
internal class R2PrivateProjectSpool private constructor(
    private val file: File,
    private var output: FileOutputStream?,
    private val fileOperations: R2PrivateProjectSpoolFileOperations,
) : ZipArchiveV1EntrySink, Closeable {

    private val archiveEntriesMutable = LinkedHashMap<String, R2SpooledArchiveEntry>()
    private var currentEntry: PendingEntry? = null
    private var byteCount = 0L
    private var archiveFinished = false
    private var ownershipTransferred = false
    private var cleanupComplete = false

    val archiveEntries: Map<String, R2SpooledArchiveEntry>
        get() = Collections.unmodifiableMap(LinkedHashMap(archiveEntriesMutable))

    override fun begin(entry: ZipArchiveV1Entry) {
        check(!archiveFinished && !ownershipTransferred && currentEntry == null) {
            "R2 project spool received an out-of-sequence ZIP entry."
        }
        check(!archiveEntriesMutable.containsKey(entry.path)) {
            "R2 project spool received a duplicate ZIP entry: ${entry.path}."
        }
        currentEntry = PendingEntry(
            path = entry.path,
            offset = byteCount,
            expectedBytes = entry.uncompressedBytes,
            digest = MessageDigest.getInstance("SHA-256"),
        )
    }

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        require(
            offset >= 0 &&
                length >= 0 &&
                offset <= bytes.size - length
        )
        check(length <= MAXIMUM_STREAM_CHUNK_BYTES) {
            "R2 project spool received an oversized streaming chunk."
        }
        val pending = checkNotNull(currentEntry) {
            "R2 project spool received bytes without an active ZIP entry."
        }
        requireNotNull(output) {
            "R2 project spool writer is closed."
        }.write(bytes, offset, length)
        pending.digest.update(bytes, offset, length)
        pending.acceptedBytes = Math.addExact(
            pending.acceptedBytes,
            length.toLong(),
        )
        byteCount = Math.addExact(byteCount, length.toLong())
    }

    override fun end(entry: ZipArchiveV1Entry) {
        val pending = checkNotNull(currentEntry) {
            "R2 project spool ended a ZIP entry without starting it."
        }
        check(
            pending.path == entry.path &&
                pending.expectedBytes == entry.uncompressedBytes &&
                pending.acceptedBytes == pending.expectedBytes
        ) {
            "R2 project spool ZIP entry inventory changed while streaming."
        }
        val completed = R2SpooledArchiveEntry(
            path = pending.path,
            offset = pending.offset,
            byteLength = pending.acceptedBytes,
            sha256 = pending.digest.digest().toLowerHex(),
        )
        check(archiveEntriesMutable.put(completed.path, completed) == null)
        currentEntry = null
    }

    override fun abort(failure: Throwable) {
        try {
            close()
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
    }

    fun finishArchiveExtraction() {
        check(!archiveFinished && currentEntry == null && !ownershipTransferred) {
            "R2 project spool archive extraction is incomplete."
        }
        requireNotNull(output).flush()
        archiveFinished = true
    }

    fun readArchiveEntryBytes(
        path: String,
        maximumBytes: Long,
    ): ByteArray {
        check(archiveFinished && !ownershipTransferred)
        val entry = checkNotNull(archiveEntriesMutable[path]) {
            "R2 project spool does not contain $path."
        }
        require(maximumBytes in 0L..Int.MAX_VALUE.toLong())
        require(entry.byteLength <= maximumBytes) {
            "R2 project spool entry exceeds its materialization limit: $path."
        }
        return ByteArray(entry.byteLength.toInt()).also { bytes ->
            fileOperations.openReader(file).use { reader ->
                reader.seek(entry.offset)
                reader.readFully(bytes)
            }
        }
    }

    fun strictUtf16CodeUnits(entry: R2SpooledArchiveEntry): Int {
        check(archiveFinished && !ownershipTransferred)
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            fileOperations.openReader(file).use { reader ->
                reader.seek(entry.offset)
                val input = R2RandomAccessRangeInputStream(
                    reader = reader,
                    byteLength = entry.byteLength,
                )
                InputStreamReader(input, decoder).use { text ->
                    val buffer = CharArray(UTF16_COUNT_BUFFER_CHARS)
                    var codeUnits = 0L
                    while (true) {
                        val count = text.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        codeUnits = Math.addExact(codeUnits, count.toLong())
                        require(codeUnits <= Int.MAX_VALUE) {
                            "Project file UTF-16 length exceeds the supported range."
                        }
                    }
                    check(input.remainingBytes == 0L) {
                        "Project file UTF-8 validation did not consume its exact range."
                    }
                    codeUnits.toInt()
                }
            }
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException(
                "Project file is not valid UTF-8: ${entry.path}.",
                error,
            )
        }
    }

    fun appendTrustedDeclaration(
        declaration: R2TrustedDeclarationFile,
    ): R2FileBackedProjectRecord {
        check(archiveFinished && !ownershipTransferred && currentEntry == null)
        val bytes = R2Utf8.encode(
            declaration.text,
            "Trusted declaration ${declaration.archivePath}",
        )
        try {
            check(bytes.size.toLong() == declaration.size) {
                "Trusted declaration size changed while it was spooled."
            }
            check(R2Digests.equal(R2Digests.sha256(bytes), declaration.sha256)) {
                "Trusted declaration digest changed while it was spooled."
            }
            val offset = byteCount
            val writer = requireNotNull(output)
            var sourceOffset = 0
            while (sourceOffset < bytes.size) {
                val count = minOf(
                    MAXIMUM_STREAM_CHUNK_BYTES,
                    bytes.size - sourceOffset,
                )
                writer.write(bytes, sourceOffset, count)
                sourceOffset += count
            }
            byteCount = Math.addExact(byteCount, bytes.size.toLong())
            return R2FileBackedProjectRecord(
                metadata = R2ProjectFileMetadata(
                    path = "/${declaration.archivePath}",
                    utf8Bytes = declaration.size,
                    utf16CodeUnits = declaration.text.length,
                    sha256 = declaration.sha256,
                ),
                offset = offset,
            )
        } finally {
            bytes.fill(0)
        }
    }

    fun seal(
        profileAuthority: R2CompilationProfileAuthority,
        projectRecords: List<R2FileBackedProjectRecord>,
        rootNames: List<String>,
        limits: R2ProjectSnapshotLimits,
        entryPath: String = rootNames.firstOrNull().orEmpty(),
        rawConfigRecord: R2FileBackedRawProjectConfigRecord? = null,
        packageMetadataInventory: R2PackageMetadataInventory? = null,
        packageMetadataRecords: List<R2FileBackedPackageMetadataRecord> = emptyList(),
        nodePackageFormatCatalog: R2NodePackageFormatCatalog? = null,
        nodePackageMetadataCatalog: R2NodePackageMetadataCatalog? = null,
        packageTargetInventory: R2PackageTargetInventory? = null,
        packageTargetRecords: List<R2FileBackedPackageTargetRecord> = emptyList(),
        nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory? = null,
        nodePackageTargetV2Records: List<R2FileBackedNodePackageTargetV2Record> = emptyList(),
        acceptedProjectId: String? = null,
        acceptedInputAuthority: R2AcceptedInputAuthorityV1? = null,
    ): R2FileBackedProjectSnapshot {
        check(archiveFinished && !ownershipTransferred && currentEntry == null)
        R2ProjectSnapshot.requireAcceptedProjectId(acceptedProjectId)
        val inventory = R2ProjectSnapshot.validateInventory(
            files = projectRecords.map(R2FileBackedProjectRecord::metadata),
            rootNames = rootNames,
            limits = limits,
            entryPath = entryPath,
            profileAuthority = profileAuthority,
        )
        R2ProjectSnapshot.requirePackageFormatCombination(
            profileAuthority = profileAuthority,
            hasRawConfig = rawConfigRecord != null,
            packageMetadataInventory = packageMetadataInventory,
            packageTargetInventory = packageTargetInventory,
            nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
            nodePackageFormatCatalog = nodePackageFormatCatalog,
        )
        require(packageMetadataInventory != null || packageMetadataRecords.isEmpty()) {
            "Package metadata records require an inventory."
        }
        require(
            packageMetadataInventory == null ||
                rawConfigRecord != null ||
                profileAuthority ==
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        ) {
            "Package metadata transport requires controlled raw config authority."
        }
        packageMetadataInventory?.let { packageInventory ->
            val revalidatedInventory = R2PackageMetadataInventory.create(
                dependencyRoots = packageInventory.dependencyRoots,
                files = packageInventory.files,
            )
            require(
                revalidatedInventory.canonicalInput == packageInventory.canonicalInput &&
                    R2Digests.equal(
                        revalidatedInventory.fingerprint,
                        packageInventory.fingerprint,
                    ) &&
                    revalidatedInventory.totalBytes == packageInventory.totalBytes &&
                    revalidatedInventory.authority == packageInventory.authority
            ) {
                "Package metadata inventory authority changed before file-backed indexing."
            }
            require(
                packageInventory.files ==
                    packageMetadataRecords.map(R2FileBackedPackageMetadataRecord::metadata),
            ) {
                "Package metadata records do not exactly cover their inventory."
            }
        }
        require(packageTargetInventory != null || packageTargetRecords.isEmpty()) {
            "Package target records require an inventory."
        }
        require(packageTargetInventory == null || rawConfigRecord != null) {
            "Package target transport requires controlled raw config authority."
        }
        require(packageTargetInventory == null || packageMetadataInventory != null) {
            "Package target transport requires package metadata authority."
        }
        packageTargetInventory?.let { targetInventory ->
            val metadataInventory = requireNotNull(packageMetadataInventory)
            val revalidatedInventory = R2PackageTargetInventory.create(
                packageMetadataInventory = metadataInventory,
                files = targetInventory.files,
                boundPackageMetadataInventoryFingerprint =
                    targetInventory.packageMetadataInventoryFingerprint,
            )
            require(
                revalidatedInventory.canonicalInput == targetInventory.canonicalInput &&
                    R2Digests.equal(
                        revalidatedInventory.fingerprint,
                        targetInventory.fingerprint,
                    ) &&
                    revalidatedInventory.totalBytes == targetInventory.totalBytes &&
                    revalidatedInventory.totalPathChars == targetInventory.totalPathChars &&
                    revalidatedInventory.authority == targetInventory.authority
            ) {
                "Package target inventory authority changed before file-backed indexing."
            }
            require(
                targetInventory.files ==
                    packageTargetRecords.map(R2FileBackedPackageTargetRecord::descriptor),
            ) {
                "Package target records do not exactly cover their inventory."
            }
        }
        require(
            nodePackageTargetV2Inventory != null || nodePackageTargetV2Records.isEmpty(),
        ) {
            "Node package-target v2 records require an inventory."
        }
        require(packageTargetInventory == null || nodePackageTargetV2Inventory == null) {
            "Package target v1 and Node package-target v2 inventories are mutually exclusive."
        }
        require(
            nodePackageTargetV2Inventory == null ||
                profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        ) {
            "Node package-target v2 requires the exact Node revision 2 profile."
        }
        require(nodePackageTargetV2Inventory == null || packageMetadataInventory != null) {
            "Node package-target v2 requires package metadata authority."
        }
        nodePackageTargetV2Inventory?.let { targetInventory ->
            val metadataInventory = requireNotNull(packageMetadataInventory)
            val revalidatedInventory = R2NodePackageTargetV2Inventory.create(
                packageMetadataInventory = metadataInventory,
                files = targetInventory.files,
                boundPackageMetadataInventoryFingerprint =
                    targetInventory.packageMetadataInventoryFingerprint,
            )
            require(
                revalidatedInventory.canonicalInput == targetInventory.canonicalInput &&
                    R2Digests.equal(
                        revalidatedInventory.fingerprint,
                        targetInventory.fingerprint,
                    ) &&
                    revalidatedInventory.totalBytes == targetInventory.totalBytes &&
                    revalidatedInventory.totalPathChars == targetInventory.totalPathChars &&
                    revalidatedInventory.authority == targetInventory.authority
            ) {
                "Node package-target v2 authority changed before file-backed indexing."
            }
            require(
                targetInventory.files ==
                    nodePackageTargetV2Records.map(
                        R2FileBackedNodePackageTargetV2Record::descriptor,
                    ),
            ) {
                "Node package-target v2 records do not exactly cover their inventory."
            }
        }
        val packageMetadataLargest =
            packageMetadataInventory?.files?.maxOfOrNull { file -> file.size } ?: 0L
        val packageTargetLargest =
            packageTargetInventory?.files?.maxOfOrNull { file -> file.size } ?: 0L
        val nodePackageTargetV2Largest =
            nodePackageTargetV2Inventory?.files?.maxOfOrNull { file -> file.size } ?: 0L
        val acceptedCapacity = R2ProjectInputCapacity(
            fileCount = Math.addExact(
                Math.addExact(
                    Math.addExact(
                        projectRecords.size + if (rawConfigRecord == null) 0 else 1,
                        packageMetadataRecords.size,
                    ),
                    packageTargetRecords.size,
                ),
                nodePackageTargetV2Records.size,
            ),
            largestFileUtf8Bytes = maxOf(
                inventory.largestFileUtf8Bytes,
                rawConfigRecord?.descriptor?.size ?: 0L,
                packageMetadataLargest,
                packageTargetLargest,
                nodePackageTargetV2Largest,
            ),
            totalUtf8Bytes = Math.addExact(
                Math.addExact(
                    Math.addExact(
                        Math.addExact(
                            inventory.totalUtf8Bytes,
                            rawConfigRecord?.descriptor?.size ?: 0L,
                        ),
                        packageMetadataInventory?.totalBytes ?: 0L,
                    ),
                    packageTargetInventory?.totalBytes ?: 0L,
                ),
                nodePackageTargetV2Inventory?.totalBytes ?: 0L,
            ),
        )
        val callerProjectRecords = projectRecords.filterNot { record ->
            record.metadata.path.startsWith("/declarations/")
        }
        val acceptedCallerCapacity = R2ProjectInputCapacity(
            fileCount = Math.addExact(
                Math.addExact(
                    Math.addExact(
                        callerProjectRecords.size + if (rawConfigRecord == null) 0 else 1,
                        packageMetadataRecords.size,
                    ),
                    packageTargetRecords.size,
                ),
                nodePackageTargetV2Records.size,
            ),
            largestFileUtf8Bytes = maxOf(
                callerProjectRecords.maxOfOrNull { record -> record.metadata.utf8Bytes } ?: 0L,
                rawConfigRecord?.descriptor?.size ?: 0L,
                packageMetadataLargest,
                packageTargetLargest,
                nodePackageTargetV2Largest,
            ),
            totalUtf8Bytes = Math.addExact(
                Math.addExact(
                    Math.addExact(
                        Math.addExact(
                            callerProjectRecords.sumOf { record -> record.metadata.utf8Bytes },
                            rawConfigRecord?.descriptor?.size ?: 0L,
                        ),
                        packageMetadataInventory?.totalBytes ?: 0L,
                    ),
                    packageTargetInventory?.totalBytes ?: 0L,
                ),
                nodePackageTargetV2Inventory?.totalBytes ?: 0L,
            ),
        )
        require(acceptedCapacity.admissionAgainst(limits).isEligible) {
            "Project config, package metadata, package targets and files exceed the snapshot " +
                "limits."
        }
        acceptedInputAuthority?.let { authority ->
            require(
                authority.capacity == acceptedCapacity &&
                    authority.callerCapacity == acceptedCallerCapacity,
            ) {
                "Accepted input authority capacity does not match the file-backed snapshot."
            }
        }
        val writer = requireNotNull(output)
        var immutableReader: RandomAccessFile? = null
        try {
            writer.fd.sync()
            writer.close()
            output = null
            if (!file.setReadOnly()) {
                throw IOException("R2 project spool could not be made read-only.")
            }
            immutableReader = fileOperations.openReader(file)
            check(immutableReader.length() == byteCount) {
                "R2 project spool size changed before it became immutable."
            }
            val fallbackFile = if (SUPPORTS_UNLINK_WHILE_OPEN) {
                fileOperations.delete(file, restoreWritable = false)
                null
            } else {
                file
            }
            val snapshot = R2FileBackedProjectSnapshot(
                immutableReader = immutableReader,
                fallbackFile = fallbackFile,
                profileAuthority = profileAuthority,
                rawConfigRecord = rawConfigRecord,
                packageMetadataInventory = packageMetadataInventory,
                packageMetadataRecords = ArrayList(packageMetadataRecords),
                nodePackageFormatCatalog = nodePackageFormatCatalog,
                nodePackageMetadataCatalog = nodePackageMetadataCatalog,
                packageTargetInventory = packageTargetInventory,
                packageTargetRecords = ArrayList(packageTargetRecords),
                nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
                nodePackageTargetV2Records = ArrayList(nodePackageTargetV2Records),
                records = ArrayList(projectRecords),
                rootNames = ArrayList(inventory.rootNames),
                entryPath = entryPath,
                totalUtf8Bytes = inventory.totalUtf8Bytes,
                largestFileUtf8Bytes = inventory.largestFileUtf8Bytes,
                acceptedProjectId = acceptedProjectId,
                acceptedInputAuthority = acceptedInputAuthority,
            )
            immutableReader = null
            ownershipTransferred = true
            cleanupComplete = true
            return snapshot
        } catch (failure: Throwable) {
            val readerCloseFailure =
                runCatching { immutableReader?.close() }.exceptionOrNull()
            if (readerCloseFailure != null) {
                throw R2PrivateInputCleanupUnprovenException(
                    "R2 project spool reader cleanup after seal failure could not be proven.",
                    failure,
                ).also { unproven ->
                    unproven.addSuppressed(readerCloseFailure)
                }
            }
            throw failure
        }
    }

    override fun close() {
        if (cleanupComplete || ownershipTransferred) return
        var failure: Throwable? = null
        output?.let { writer ->
            try {
                writer.close()
                output = null
            } catch (closeFailure: Throwable) {
                failure = closeFailure
            }
        }
        try {
            fileOperations.delete(file, restoreWritable = true)
        } catch (deleteFailure: Throwable) {
            failure?.addSuppressed(deleteFailure)
                ?: run { failure = deleteFailure }
        }
        cleanupComplete = failure == null
        if (failure != null) {
            throw R2PrivateInputCleanupUnprovenException(
                "R2 project spool cleanup could not be proven.",
                failure,
            )
        }
    }

    private data class PendingEntry(
        val path: String,
        val offset: Long,
        val expectedBytes: Long,
        val digest: MessageDigest,
        var acceptedBytes: Long = 0L,
    )

    companion object {
        fun create(
            privateDirectory: File,
            fileOperations: R2PrivateProjectSpoolFileOperations =
                R2SystemPrivateProjectSpoolFileOperations,
        ): R2PrivateProjectSpool {
            require(privateDirectory.isDirectory) {
                "R2 project spool directory does not exist or is not a directory."
            }
            val file = File.createTempFile(
                SPOOL_FILE_PREFIX,
                SPOOL_FILE_SUFFIX,
                privateDirectory,
            )
            var createdOutput: FileOutputStream? = null
            try {
                createdOutput = fileOperations.openOutput(file)
                fileOperations.afterOutputOpened()
                val spool = R2PrivateProjectSpool(
                    file = file,
                    output = createdOutput,
                    fileOperations = fileOperations,
                )
                createdOutput = null
                return spool
            } catch (failure: Throwable) {
                var cleanupFailure: Throwable? = null
                createdOutput?.let { writer ->
                    try {
                        writer.close()
                    } catch (closeFailure: Throwable) {
                        cleanupFailure = closeFailure
                    }
                }
                try {
                    fileOperations.delete(file, restoreWritable = true)
                } catch (deleteFailure: Throwable) {
                    cleanupFailure?.addSuppressed(deleteFailure)
                        ?: run { cleanupFailure = deleteFailure }
                }
                if (cleanupFailure != null) {
                    throw R2PrivateInputCleanupUnprovenException(
                        "R2 project spool creation cleanup could not be proven.",
                        failure,
                    ).also { unproven ->
                        unproven.addSuppressed(cleanupFailure)
                    }
                }
                throw failure
            }
        }

        private const val MAXIMUM_STREAM_CHUNK_BYTES = 16 * 1024
        private const val UTF16_COUNT_BUFFER_CHARS = 8 * 1024
        private const val SPOOL_FILE_PREFIX = "r2-project-vfs-"
        private const val SPOOL_FILE_SUFFIX = ".spool"
        private val SUPPORTS_UNLINK_WHILE_OPEN = File.separatorChar != '\\'
    }
}

internal class R2FileBackedProjectSnapshot internal constructor(
    private val immutableReader: RandomAccessFile,
    private val fallbackFile: File?,
    override val profileAuthority: R2CompilationProfileAuthority,
    private val rawConfigRecord: R2FileBackedRawProjectConfigRecord?,
    override val packageMetadataInventory: R2PackageMetadataInventory? = null,
    packageMetadataRecords: ArrayList<R2FileBackedPackageMetadataRecord> = arrayListOf(),
    override val nodePackageFormatCatalog: R2NodePackageFormatCatalog? = null,
    override val nodePackageMetadataCatalog: R2NodePackageMetadataCatalog? = null,
    override val packageTargetInventory: R2PackageTargetInventory? = null,
    packageTargetRecords: ArrayList<R2FileBackedPackageTargetRecord> = arrayListOf(),
    override val nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory? = null,
    nodePackageTargetV2Records: ArrayList<R2FileBackedNodePackageTargetV2Record> = arrayListOf(),
    records: ArrayList<R2FileBackedProjectRecord>,
    rootNames: ArrayList<String>,
    override val entryPath: String,
    override val totalUtf8Bytes: Long,
    override val largestFileUtf8Bytes: Long,
    override val acceptedProjectId: String? = null,
    override val acceptedInputAuthority: R2AcceptedInputAuthorityV1? = null,
) : R2ProjectInputSnapshot {

    private val lock = Any()
    private val records = Collections.unmodifiableList(ArrayList(records))
    private val packageMetadataRecords =
        Collections.unmodifiableList(ArrayList(packageMetadataRecords))
    private val packageTargetRecords =
        Collections.unmodifiableList(ArrayList(packageTargetRecords))
    private val nodePackageTargetV2Records =
        Collections.unmodifiableList(ArrayList(nodePackageTargetV2Records))
    private val ownedRootNames = Collections.unmodifiableList(ArrayList(rootNames))

    init {
        R2ProjectSnapshot.requireAcceptedProjectId(acceptedProjectId)
        R2ProjectSnapshot.requirePackageFormatCombination(
            profileAuthority = profileAuthority,
            hasRawConfig = rawConfigRecord != null,
            packageMetadataInventory = packageMetadataInventory,
            packageTargetInventory = packageTargetInventory,
            nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
            nodePackageFormatCatalog = nodePackageFormatCatalog,
        )
        this.records.forEach { record ->
            R2ProjectSnapshot.validateProjectPath(
                record.metadata.path,
                profileAuthority,
            )
        }
        ownedRootNames.forEach { rootName ->
            R2ProjectSnapshot.validateProjectPath(rootName, profileAuthority)
        }
        R2ProjectSnapshot.requireAcceptedEntry(
            files = this.records.map(R2FileBackedProjectRecord::metadata),
            rootNames = ownedRootNames,
            entryPath = entryPath,
            profileAuthority = profileAuthority,
        )
        require(packageMetadataInventory != null || this.packageMetadataRecords.isEmpty()) {
            "Package metadata records require an inventory."
        }
        require(
            packageMetadataInventory == null ||
                rawConfigRecord != null ||
                profileAuthority ==
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        ) {
            "Package metadata transport requires controlled raw config authority."
        }
        packageMetadataInventory?.let { packageInventory ->
            val revalidatedInventory = R2PackageMetadataInventory.create(
                dependencyRoots = packageInventory.dependencyRoots,
                files = packageInventory.files,
            )
            require(
                revalidatedInventory.canonicalInput == packageInventory.canonicalInput &&
                    R2Digests.equal(
                        revalidatedInventory.fingerprint,
                        packageInventory.fingerprint,
                    ) &&
                    revalidatedInventory.totalBytes == packageInventory.totalBytes &&
                    revalidatedInventory.authority == packageInventory.authority
            ) {
                "Package metadata inventory authority changed before file-backed cursor use."
            }
            require(
                packageInventory.files ==
                    this.packageMetadataRecords.map(
                        R2FileBackedPackageMetadataRecord::metadata,
                    ),
            ) {
                "Package metadata records do not exactly cover their inventory."
            }
            val verifiedPackageMetadataBytes = ArrayList<ByteArray>(
                this.packageMetadataRecords.size,
            )
            try {
                this.packageMetadataRecords.forEach { record ->
                    require(
                        R2PackageMetadataPolicy.isExplicitlyAllowed(
                            record.metadata.path,
                            packageInventory,
                        ),
                    ) {
                        "Package metadata record path is outside its accepted inventory: " +
                            "${record.metadata.path}."
                    }
                    require(record.offset >= 0L) {
                        "Package metadata record has a negative spool offset."
                    }
                    require(record.offset <= immutableReader.length() - record.metadata.size) {
                        "Package metadata record exceeds the immutable spool range."
                    }
                    R2Digests.requireLowerSha256(
                        record.metadata.sha256,
                        "Package metadata SHA-256",
                    )
                    val bytes = ByteArray(record.metadata.size.toInt())
                    verifiedPackageMetadataBytes += bytes
                    immutableReader.seek(record.offset)
                    immutableReader.readFully(bytes)
                    require(
                        R2Digests.equal(
                            R2Digests.sha256(bytes),
                            record.metadata.sha256,
                        ),
                    ) {
                        "Package metadata bytes do not match their immutable spool digest: " +
                            "${record.metadata.path}."
                    }
                    R2PackageMetadataPolicy.validatePackageJson(bytes)
                }
                if (
                    profileAuthority ==
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
                ) {
                    val suppliedCatalog = requireNotNull(nodePackageFormatCatalog)
                    val derivedCatalog = R2NodePackageFormatCatalog.create(
                        packageInventory,
                        verifiedPackageMetadataBytes,
                    )
                    require(
                        derivedCatalog.dependencyRoots == suppliedCatalog.dependencyRoots &&
                            derivedCatalog.records == suppliedCatalog.records &&
                            derivedCatalog.packageMetadataInventoryCanonicalInput ==
                            suppliedCatalog.packageMetadataInventoryCanonicalInput &&
                            R2Digests.equal(
                                derivedCatalog.packageMetadataInventoryFingerprint,
                                suppliedCatalog.packageMetadataInventoryFingerprint,
                            ) &&
                            derivedCatalog.canonicalInput == suppliedCatalog.canonicalInput &&
                            R2Digests.equal(
                                derivedCatalog.fingerprint,
                                suppliedCatalog.fingerprint,
                            ),
                    ) {
                        "Node package-format catalog was not derived from immutable spool bytes."
                    }
                    val suppliedMetadataCatalog = requireNotNull(nodePackageMetadataCatalog)
                    val derivedMetadataCatalog = R2NodePackageMetadataCatalog.create(
                        packageInventory,
                        verifiedPackageMetadataBytes,
                    )
                    require(
                        derivedMetadataCatalog.inventoryFingerprint ==
                            suppliedMetadataCatalog.inventoryFingerprint &&
                            derivedMetadataCatalog.dependencyRoots ==
                            suppliedMetadataCatalog.dependencyRoots &&
                            derivedMetadataCatalog.records.map { record ->
                                Triple(record.path, record.size, record.sha256)
                            } == suppliedMetadataCatalog.records.map { record ->
                                Triple(record.path, record.size, record.sha256)
                            },
                    ) {
                        "Node package metadata catalog was not derived from immutable spool bytes."
                    }
                }
            } finally {
                verifiedPackageMetadataBytes.forEach { bytes -> bytes.fill(0) }
                verifiedPackageMetadataBytes.clear()
            }
        }
        require(packageTargetInventory != null || this.packageTargetRecords.isEmpty()) {
            "Package target records require an inventory."
        }
        require(packageTargetInventory == null || rawConfigRecord != null) {
            "Package target transport requires controlled raw config authority."
        }
        require(packageTargetInventory == null || packageMetadataInventory != null) {
            "Package target transport requires package metadata authority."
        }
        packageTargetInventory?.let { targetInventory ->
            val metadataInventory = requireNotNull(packageMetadataInventory)
            val revalidatedInventory = R2PackageTargetInventory.create(
                packageMetadataInventory = metadataInventory,
                files = targetInventory.files,
                boundPackageMetadataInventoryFingerprint =
                    targetInventory.packageMetadataInventoryFingerprint,
            )
            require(
                revalidatedInventory.canonicalInput == targetInventory.canonicalInput &&
                    R2Digests.equal(
                        revalidatedInventory.fingerprint,
                        targetInventory.fingerprint,
                    ) &&
                    revalidatedInventory.totalBytes == targetInventory.totalBytes &&
                    revalidatedInventory.totalPathChars == targetInventory.totalPathChars &&
                    revalidatedInventory.authority == targetInventory.authority
            ) {
                "Package target inventory authority changed before file-backed cursor use."
            }
            require(
                targetInventory.files ==
                    this.packageTargetRecords.map(R2FileBackedPackageTargetRecord::descriptor),
            ) {
                "Package target records do not exactly cover their inventory."
            }
            this.packageTargetRecords.forEach { record ->
                require(
                    R2PackageTargetPolicy.isExplicitlyAllowed(
                        record.descriptor.path,
                        targetInventory,
                    ),
                ) {
                    "Package target record path is outside its accepted inventory: " +
                        "${record.descriptor.path}."
                }
                require(record.offset >= 0L) {
                    "Package target record has a negative spool offset."
                }
                require(record.offset <= immutableReader.length() - record.descriptor.size) {
                    "Package target record exceeds the immutable spool range."
                }
                requirePackageTargetDigest(record)
            }
        }
        require(
            nodePackageTargetV2Inventory != null || this.nodePackageTargetV2Records.isEmpty(),
        ) {
            "Node package-target v2 records require an inventory."
        }
        require(packageTargetInventory == null || nodePackageTargetV2Inventory == null) {
            "Package target v1 and Node package-target v2 inventories are mutually exclusive."
        }
        require(
            nodePackageTargetV2Inventory == null ||
                profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        ) {
            "Node package-target v2 requires the exact Node revision 2 profile."
        }
        nodePackageTargetV2Inventory?.let { targetInventory ->
            val metadataInventory = requireNotNull(packageMetadataInventory)
            val revalidatedInventory = R2NodePackageTargetV2Inventory.create(
                packageMetadataInventory = metadataInventory,
                files = targetInventory.files,
                boundPackageMetadataInventoryFingerprint =
                    targetInventory.packageMetadataInventoryFingerprint,
            )
            require(
                revalidatedInventory.canonicalInput == targetInventory.canonicalInput &&
                    R2Digests.equal(
                        revalidatedInventory.fingerprint,
                        targetInventory.fingerprint,
                    ) &&
                    revalidatedInventory.totalBytes == targetInventory.totalBytes &&
                    revalidatedInventory.totalPathChars == targetInventory.totalPathChars &&
                    revalidatedInventory.authority == targetInventory.authority
            ) {
                "Node package-target v2 authority changed before file-backed cursor use."
            }
            require(
                targetInventory.files ==
                    this.nodePackageTargetV2Records.map(
                        R2FileBackedNodePackageTargetV2Record::descriptor,
                    ),
            ) {
                "Node package-target v2 records do not exactly cover their inventory."
            }
            this.nodePackageTargetV2Records.forEach { record ->
                require(record.offset >= 0L) {
                    "Node package-target v2 record has a negative spool offset."
                }
                require(record.offset <= immutableReader.length() - record.descriptor.size) {
                    "Node package-target v2 record exceeds the immutable spool range."
                }
                requireNodePackageTargetV2Digest(record)
            }
        }
    }

    private var usable = true
    private var readerClosed = false
    private var cleanupComplete = false

    override val fileInventory: List<R2ProjectFileMetadata> =
        Collections.unmodifiableList(
            records.mapTo(ArrayList(records.size)) { record ->
                record.metadata
            },
        )
    override val filePaths: Set<String> =
        Collections.unmodifiableSet(
            fileInventory.mapTo(LinkedHashSet(fileInventory.size)) { file ->
                file.path
            },
        )
    override val rootNames: List<String> = ownedRootNames
    override val rawConfigDescriptor: R2RawProjectConfigDescriptor? =
        rawConfigRecord?.descriptor

    val namedPathRetained: Boolean
        get() = fallbackFile != null

    override fun openFile(fileIndex: Int): R2ProjectFileCursor = synchronized(lock) {
        check(usable && !readerClosed) {
            "R2 file-backed project snapshot is closed."
        }
        val record = records.getOrNull(fileIndex)
            ?: throw IndexOutOfBoundsException("Project file index is outside its inventory.")
        R2FileBackedProjectCursor(
            snapshot = this,
            offset = record.offset,
            byteLength = record.metadata.utf8Bytes,
        )
    }

    override fun openRawConfig(): R2ProjectFileCursor? = synchronized(lock) {
        check(usable && !readerClosed) {
            "R2 file-backed project snapshot is closed."
        }
        rawConfigRecord?.let { record ->
            R2FileBackedProjectCursor(
                snapshot = this,
                offset = record.offset,
                byteLength = record.descriptor.size,
            )
        }
    }

    override fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor =
        synchronized(lock) {
            check(usable && !readerClosed) {
                "R2 file-backed project snapshot is closed."
            }
            val record = packageMetadataRecords.getOrNull(fileIndex)
                ?: throw IndexOutOfBoundsException(
                    "Package metadata file index is outside its inventory.",
                )
            R2FileBackedProjectCursor(
                snapshot = this,
                offset = record.offset,
                byteLength = record.metadata.size,
            )
        }

    override fun openPackageTargetFile(fileIndex: Int): R2ProjectFileCursor =
        synchronized(lock) {
            check(usable && !readerClosed) {
                "R2 file-backed project snapshot is closed."
            }
            val record = packageTargetRecords.getOrNull(fileIndex)
                ?: throw IndexOutOfBoundsException(
                    "Package target file index is outside its inventory.",
                )
            R2FileBackedProjectCursor(
                snapshot = this,
                offset = record.offset,
                byteLength = record.descriptor.size,
            )
        }

    override fun openNodePackageTargetV2File(fileIndex: Int): R2ProjectFileCursor =
        synchronized(lock) {
            check(usable && !readerClosed) {
                "R2 file-backed project snapshot is closed."
            }
            val record = nodePackageTargetV2Records.getOrNull(fileIndex)
                ?: throw IndexOutOfBoundsException(
                    "Node package-target v2 file index is outside its inventory.",
                )
            R2FileBackedProjectCursor(
                snapshot = this,
                offset = record.offset,
                byteLength = record.descriptor.size,
            )
        }

    private fun requirePackageTargetDigest(record: R2FileBackedPackageTargetRecord) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(PACKAGE_TARGET_DIGEST_BUFFER_BYTES)
        try {
            immutableReader.seek(record.offset)
            var remaining = record.descriptor.size
            while (remaining > 0L) {
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                immutableReader.readFully(buffer, 0, count)
                digest.update(buffer, 0, count)
                buffer.fill(0, 0, count)
                remaining -= count
            }
            require(R2Digests.equal(digest.digest().toLowerHex(), record.descriptor.sha256)) {
                "Package target bytes do not match their immutable spool digest: " +
                    "${record.descriptor.path}."
            }
        } finally {
            buffer.fill(0)
        }
        requirePackageTargetUtf8(record)
    }

    private fun requirePackageTargetUtf8(record: R2FileBackedPackageTargetRecord) {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = R2RandomAccessRangeInputStream(
            reader = immutableReader,
            byteLength = record.descriptor.size,
        )
        val buffer = CharArray(PACKAGE_TARGET_UTF16_BUFFER_CHARS)
        try {
            immutableReader.seek(record.offset)
            InputStreamReader(input, decoder).use { text ->
                while (true) {
                    val count = text.read(buffer)
                    if (count < 0) break
                    buffer.fill('\u0000', 0, count)
                }
            }
            check(input.remainingBytes == 0L) {
                "Package target UTF-8 validation did not consume its exact spool range."
            }
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException(
                "Package target is not valid UTF-8: ${record.descriptor.path}.",
                error,
            )
        } finally {
            buffer.fill('\u0000')
        }
    }

    private fun requireNodePackageTargetV2Digest(
        record: R2FileBackedNodePackageTargetV2Record,
    ) {
        requirePackageTargetPayload(
            offset = record.offset,
            size = record.descriptor.size,
            sha256 = record.descriptor.sha256,
            path = record.descriptor.path,
            label = "Node package-target v2",
        )
    }

    private fun requirePackageTargetPayload(
        offset: Long,
        size: Long,
        sha256: String,
        path: String,
        label: String,
    ) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(PACKAGE_TARGET_DIGEST_BUFFER_BYTES)
        try {
            immutableReader.seek(offset)
            var remaining = size
            while (remaining > 0L) {
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                immutableReader.readFully(buffer, 0, count)
                digest.update(buffer, 0, count)
                buffer.fill(0, 0, count)
                remaining -= count
            }
            require(R2Digests.equal(digest.digest().toLowerHex(), sha256)) {
                "$label bytes do not match their immutable spool digest: $path."
            }
        } finally {
            buffer.fill(0)
        }
        requirePackageTargetUtf8(
            offset = offset,
            size = size,
            path = path,
            label = label,
        )
    }

    private fun requirePackageTargetUtf8(
        offset: Long,
        size: Long,
        path: String,
        label: String,
    ) {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val input = R2RandomAccessRangeInputStream(
            reader = immutableReader,
            byteLength = size,
        )
        val buffer = CharArray(PACKAGE_TARGET_UTF16_BUFFER_CHARS)
        try {
            immutableReader.seek(offset)
            InputStreamReader(input, decoder).use { text ->
                while (true) {
                    val count = text.read(buffer)
                    if (count < 0) break
                    buffer.fill('\u0000', 0, count)
                }
            }
            check(input.remainingBytes == 0L) {
                "$label UTF-8 validation did not consume its exact spool range."
            }
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException(
                "$label is not valid UTF-8: $path.",
                error,
            )
        } finally {
            buffer.fill('\u0000')
        }
    }

    internal fun readAt(
        offset: Long,
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ) = synchronized(lock) {
        check(usable && !readerClosed) {
            "R2 file-backed project snapshot is closed."
        }
        require(
            offset >= 0L &&
                destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount &&
                offset <= immutableReader.length() - byteCount
        )
        immutableReader.seek(offset)
        immutableReader.readFully(destination, destinationOffset, byteCount)
    }

    override fun close() {
        synchronized(lock) {
            if (cleanupComplete) return
            usable = false
            var failure: Throwable? = null
            if (!readerClosed) {
                try {
                    immutableReader.close()
                    readerClosed = true
                } catch (closeFailure: Throwable) {
                    failure = closeFailure
                }
            }
            fallbackFile?.let { file ->
                try {
                    deleteProjectSpool(file, restoreWritable = true)
                } catch (deleteFailure: Throwable) {
                    failure?.addSuppressed(deleteFailure)
                        ?: run { failure = deleteFailure }
                }
            }
            cleanupComplete = failure == null
            if (failure != null) {
                throw R2PrivateInputCleanupUnprovenException(
                    "R2 file-backed project snapshot cleanup could not be proven.",
                    failure,
                )
            }
        }
    }

    private companion object {
        const val PACKAGE_TARGET_DIGEST_BUFFER_BYTES = 16 * 1024
        const val PACKAGE_TARGET_UTF16_BUFFER_CHARS = 8 * 1024
    }
}

private class R2FileBackedProjectCursor(
    private val snapshot: R2FileBackedProjectSnapshot,
    private val offset: Long,
    byteLength: Long,
) : R2ProjectFileCursor {

    private var position = 0L
    private var remaining = byteLength
    private var closed = false

    override val remainingBytes: Long
        get() = if (closed) 0L else remaining

    override fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int {
        check(!closed) {
            "R2 file-backed project cursor is closed."
        }
        require(
            destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount
        )
        if (byteCount == 0 || remaining == 0L) return 0
        val count = minOf(byteCount.toLong(), remaining).toInt()
        snapshot.readAt(
            offset = Math.addExact(offset, position),
            destination = destination,
            destinationOffset = destinationOffset,
            byteCount = count,
        )
        position += count
        remaining -= count
        return count
    }

    override fun close() {
        closed = true
        remaining = 0L
    }
}

private class R2RandomAccessRangeInputStream(
    private val reader: RandomAccessFile,
    byteLength: Long,
) : InputStream() {

    var remainingBytes: Long = byteLength
        private set

    override fun read(): Int {
        if (remainingBytes == 0L) return -1
        val value = reader.read()
        if (value < 0) {
            throw IOException("R2 project spool entry ended before its indexed length.")
        }
        remainingBytes--
        return value
    }

    override fun read(
        destination: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        require(
            offset >= 0 &&
                length >= 0 &&
                offset <= destination.size - length
        )
        if (length == 0) return 0
        if (remainingBytes == 0L) return -1
        val count = minOf(length.toLong(), remainingBytes).toInt()
        reader.readFully(destination, offset, count)
        remainingBytes -= count
        return count
    }
}

private fun deleteProjectSpool(
    file: File,
    restoreWritable: Boolean,
) {
    if (!file.exists()) return
    if (
        restoreWritable &&
        !file.setWritable(true, true) &&
        !file.canWrite()
    ) {
        throw IOException("R2 project spool file could not be made writable for deletion.")
    }
    if (!file.delete() && file.exists()) {
        throw IOException("R2 project spool file could not be deleted.")
    }
}

private fun ByteArray.toLowerHex(): String =
    joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
