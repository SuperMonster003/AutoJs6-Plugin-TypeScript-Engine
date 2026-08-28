package io.github.supermonster003.autojs6.plugin.typescript

import java.io.Closeable
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal enum class R2ProjectProfile(
    val contractValue: String,
) {
    PROVISIONAL_RHINO(TypeScriptCompilerContract.PROFILE_RHINO),
    PROVISIONAL_NODE_NEXT(TypeScriptCompilerContract.PROFILE_NODE),
}

internal data class R2ProjectTextFile(
    val path: String,
    val text: String,
)

internal data class R2ProjectFileMetadata(
    val path: String,
    val utf8Bytes: Long,
    val utf16CodeUnits: Int,
    val sha256: String,
)

/** Control-plane identity for the one controlled raw project configuration. */
internal data class R2RawProjectConfigAuthority(
    val size: Long,
    val sha256: String,
    val path: String = R2ControlledTsConfigPolicy.CONFIG_PATH,
    val policyRevision: Int = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
    val policyFingerprint: String = R2ControlledTsConfigPolicy.revision4Fingerprint,
) {

    init {
        require(path == R2ControlledTsConfigPolicy.CONFIG_PATH) {
            "Raw project config path is not supported."
        }
        require(size in 0L..TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong()) {
            "Raw project config exceeds the controlled byte limit."
        }
        R2Digests.requireLowerSha256(sha256, "Raw project config SHA-256")
        R2Digests.requireLowerSha256(
            policyFingerprint,
            "Raw project config policy fingerprint",
        )
        require(R2ControlledTsConfigPolicy.isSupportedPolicy(policyRevision, policyFingerprint)) {
            "Raw project config policy fingerprint is not supported."
        }
    }

    fun descriptor(): R2RawProjectConfigDescriptor = R2RawProjectConfigDescriptor(
        path = path,
        size = size,
        sha256 = sha256,
        policyRevision = policyRevision,
        policyFingerprint = policyFingerprint,
    )
}

/** Manifest/snapshot metadata for raw config bytes kept outside the text VFS inventory. */
internal data class R2RawProjectConfigDescriptor(
    val path: String,
    val size: Long,
    val sha256: String,
    val policyRevision: Int,
    val policyFingerprint: String,
) {

    init {
        require(path == R2ControlledTsConfigPolicy.CONFIG_PATH) {
            "Raw project config path is not supported."
        }
        require(size in 0L..TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong()) {
            "Raw project config exceeds the controlled byte limit."
        }
        R2Digests.requireLowerSha256(sha256, "Raw project config SHA-256")
        R2Digests.requireLowerSha256(
            policyFingerprint,
            "Raw project config policy fingerprint",
        )
        require(R2ControlledTsConfigPolicy.isSupportedPolicy(policyRevision, policyFingerprint)) {
            "Raw project config policy fingerprint is not supported."
        }
    }
}

/**
 * Immutable project inventory whose payload may be heap-backed or file-backed.
 *
 * Callers own the snapshot and must close it after the compiler and output provenance checks no
 * longer need it. Metadata remains readable after close so an exact snapshot identity can still
 * bind a successful result, but [openFile] must reject use after close for file-backed snapshots.
 */
internal interface R2ProjectInputSnapshot : Closeable {
    /**
     * Control-plane project identity accepted with this snapshot.
     *
     * Legacy/test-only snapshot seams may omit it. Any native result authority that derives
     * project-scoped generated URIs must require a non-null value.
     */
    val acceptedProjectId: String?
        get() = null
    /** Frozen input authority. Production archive adapters always supply it. */
    val acceptedInputAuthority: R2AcceptedInputAuthorityV1?
        get() = null
    val profileAuthority: R2CompilationProfileAuthority
    val rawConfigDescriptor: R2RawProjectConfigDescriptor?
        get() = null
    val packageMetadataInventory: R2PackageMetadataInventory?
        get() = null
    val dependencyLayerInventory: R2DependencyLayerInventory?
        get() = null
    val nodePackageFormatCatalog: R2NodePackageFormatCatalog?
        get() = null
    val nodePackageMetadataCatalog: R2NodePackageMetadataCatalog?
        get() = null
    val packageTargetInventory: R2PackageTargetInventory?
        get() = null
    val nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory?
        get() = null
    val fileInventory: List<R2ProjectFileMetadata>
    val filePaths: Set<String>
    val rootNames: List<String>
    val entryPath: String
    val totalUtf8Bytes: Long
    val largestFileUtf8Bytes: Long

    val profile: R2ProjectProfile
        get() = profileAuthority.profile

    fun openFile(fileIndex: Int): R2ProjectFileCursor

    fun openRawConfig(): R2ProjectFileCursor? = null

    fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor {
        throw IndexOutOfBoundsException(
            "Package metadata file index is outside its inventory.",
        )
    }

    fun openPackageTargetFile(fileIndex: Int): R2ProjectFileCursor {
        throw IndexOutOfBoundsException(
            "Package target file index is outside its inventory.",
        )
    }

    fun openDependencyLayerFile(fileIndex: Int): R2ProjectFileCursor {
        throw IndexOutOfBoundsException(
            "Dependency-layer file index is outside its inventory.",
        )
    }

    fun openNodePackageTargetV2File(fileIndex: Int): R2ProjectFileCursor {
        throw IndexOutOfBoundsException(
            "Node package-target v2 file index is outside its inventory.",
        )
    }

    fun utf16CodeUnits(path: String): Int? =
        fileInventory.firstOrNull { file -> file.path == path }?.utf16CodeUnits

    fun readText(path: String): String {
        val fileIndex = fileInventory.indexOfFirst { file -> file.path == path }
        require(fileIndex >= 0) {
            "Project snapshot does not contain $path."
        }
        val metadata = fileInventory[fileIndex]
        require(metadata.utf8Bytes <= Int.MAX_VALUE) {
            "Project file is too large to materialize as text: $path."
        }
        val bytes = ByteArray(metadata.utf8Bytes.toInt())
        try {
            openFile(fileIndex).use { cursor ->
                var offset = 0
                while (offset < bytes.size) {
                    val count = cursor.read(
                        destination = bytes,
                        destinationOffset = offset,
                        byteCount = bytes.size - offset,
                    )
                    check(count > 0) {
                        "Project file cursor ended before its indexed length."
                    }
                    offset += count
                }
                check(cursor.remainingBytes == 0L) {
                    "Project file cursor exceeds its indexed length."
                }
            }
            check(R2Digests.equal(R2Digests.sha256(bytes), metadata.sha256)) {
                "Project file digest changed after inventory validation: $path."
            }
            return R2Utf8.decode(bytes, "Project file $path")
        } finally {
            bytes.fill(0)
        }
    }
}

internal interface R2ProjectFileCursor : Closeable {
    val remainingBytes: Long

    fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int
}

internal class R2ProjectSnapshot private constructor(
    override val acceptedProjectId: String?,
    override val acceptedInputAuthority: R2AcceptedInputAuthorityV1?,
    override val profileAuthority: R2CompilationProfileAuthority,
    override val rawConfigDescriptor: R2RawProjectConfigDescriptor?,
    rawConfigBytes: ByteArray?,
    override val packageMetadataInventory: R2PackageMetadataInventory?,
    override val nodePackageFormatCatalog: R2NodePackageFormatCatalog?,
    override val nodePackageMetadataCatalog: R2NodePackageMetadataCatalog?,
    packageMetadataBytes: ArrayList<ByteArray>,
    override val dependencyLayerInventory: R2DependencyLayerInventory?,
    dependencyLayerBytes: ArrayList<ByteArray>,
    override val packageTargetInventory: R2PackageTargetInventory?,
    packageTargetBytes: ArrayList<ByteArray>,
    override val nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory?,
    nodePackageTargetV2Bytes: ArrayList<ByteArray>,
    files: LinkedHashMap<String, String>,
    rootNames: ArrayList<String>,
    override val entryPath: String,
    fileInventory: ArrayList<R2ProjectFileMetadata>,
    override val totalUtf8Bytes: Long,
    override val largestFileUtf8Bytes: Long,
) : R2ProjectInputSnapshot {

    private val payloadLock = Any()
    private var rawConfigBytes: ByteArray? = rawConfigBytes
    private var packageMetadataBytes: ArrayList<ByteArray>? = packageMetadataBytes
    private var dependencyLayerBytes: ArrayList<ByteArray>? = dependencyLayerBytes
    private var packageTargetBytes: ArrayList<ByteArray>? = packageTargetBytes
    private var nodePackageTargetV2Bytes: ArrayList<ByteArray>? = nodePackageTargetV2Bytes
    private var closed = false

    val files: Map<String, String> = Collections.unmodifiableMap(files)
    override val fileInventory: List<R2ProjectFileMetadata> =
        Collections.unmodifiableList(fileInventory)
    override val filePaths: Set<String> =
        Collections.unmodifiableSet(LinkedHashSet(files.keys))
    override val rootNames: List<String> = Collections.unmodifiableList(rootNames)

    override fun openFile(fileIndex: Int): R2ProjectFileCursor {
        val metadata = fileInventory.getOrNull(fileIndex)
            ?: throw IndexOutOfBoundsException("Project file index is outside its inventory.")
        val text = checkNotNull(files[metadata.path]) {
            "Project snapshot text is missing for ${metadata.path}."
        }
        val bytes = R2Utf8.encode(text, "Project file ${metadata.path}")
        check(
            bytes.size.toLong() == metadata.utf8Bytes &&
                R2Digests.equal(R2Digests.sha256(bytes), metadata.sha256)
        ) {
            "Project snapshot text changed after inventory validation."
        }
        return R2ByteArrayProjectFileCursor(bytes)
    }

    override fun openRawConfig(): R2ProjectFileCursor? = synchronized(payloadLock) {
        check(!closed) { "Project snapshot is closed." }
        rawConfigBytes?.let { bytes ->
            R2HeapRawProjectConfigCursor(
                snapshot = this,
                byteLength = bytes.size,
            )
        }
    }

    override fun openPackageMetadataFile(fileIndex: Int): R2ProjectFileCursor =
        synchronized(payloadLock) {
            check(!closed) { "Project snapshot is closed." }
            val bytes = packageMetadataBytes?.getOrNull(fileIndex)
                ?: throw IndexOutOfBoundsException(
                    "Package metadata file index is outside its inventory.",
                )
            R2HeapPackageMetadataCursor(
                snapshot = this,
                fileIndex = fileIndex,
                byteLength = bytes.size,
            )
        }

    override fun openPackageTargetFile(fileIndex: Int): R2ProjectFileCursor =
        synchronized(payloadLock) {
            check(!closed) { "Project snapshot is closed." }
            val bytes = packageTargetBytes?.getOrNull(fileIndex)
                ?: throw IndexOutOfBoundsException(
                    "Package target file index is outside its inventory.",
                )
            R2HeapPackageTargetCursor(
                snapshot = this,
                fileIndex = fileIndex,
                byteLength = bytes.size,
            )
        }

    override fun openDependencyLayerFile(fileIndex: Int): R2ProjectFileCursor =
        synchronized(payloadLock) {
            check(!closed) { "Project snapshot is closed." }
            val bytes = dependencyLayerBytes?.getOrNull(fileIndex)
                ?: throw IndexOutOfBoundsException(
                    "Dependency-layer file index is outside its inventory.",
                )
            R2HeapDependencyLayerCursor(
                snapshot = this,
                fileIndex = fileIndex,
                byteLength = bytes.size,
            )
        }

    override fun openNodePackageTargetV2File(fileIndex: Int): R2ProjectFileCursor =
        synchronized(payloadLock) {
            check(!closed) { "Project snapshot is closed." }
            val bytes = nodePackageTargetV2Bytes?.getOrNull(fileIndex)
                ?: throw IndexOutOfBoundsException(
                    "Node package-target v2 file index is outside its inventory.",
                )
            R2HeapNodePackageTargetV2Cursor(
                snapshot = this,
                fileIndex = fileIndex,
                byteLength = bytes.size,
            )
        }

    internal fun readRawConfigAt(
        offset: Int,
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ) = synchronized(payloadLock) {
        check(!closed) { "Project snapshot is closed." }
        val bytes = checkNotNull(rawConfigBytes) {
            "Project snapshot does not contain a raw config."
        }
        require(
            offset >= 0 &&
                destinationOffset >= 0 &&
                byteCount >= 0 &&
                offset <= bytes.size - byteCount &&
                destinationOffset <= destination.size - byteCount
        )
        bytes.copyInto(
            destination = destination,
            destinationOffset = destinationOffset,
            startIndex = offset,
            endIndex = offset + byteCount,
        )
    }


    internal fun readPackageMetadataAt(
        fileIndex: Int,
        offset: Int,
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ) = synchronized(payloadLock) {
        check(!closed) { "Project snapshot is closed." }
        val bytes = packageMetadataBytes?.getOrNull(fileIndex)
            ?: throw IndexOutOfBoundsException(
                "Package metadata file index is outside its inventory.",
            )
        require(
            offset >= 0 &&
                destinationOffset >= 0 &&
                byteCount >= 0 &&
                offset <= bytes.size - byteCount &&
                destinationOffset <= destination.size - byteCount
        )
        bytes.copyInto(
            destination = destination,
            destinationOffset = destinationOffset,
            startIndex = offset,
            endIndex = offset + byteCount,
        )
    }

    internal fun readPackageTargetAt(
        fileIndex: Int,
        offset: Int,
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ) = synchronized(payloadLock) {
        check(!closed) { "Project snapshot is closed." }
        val bytes = packageTargetBytes?.getOrNull(fileIndex)
            ?: throw IndexOutOfBoundsException(
                "Package target file index is outside its inventory.",
            )
        require(
            offset >= 0 &&
                destinationOffset >= 0 &&
                byteCount >= 0 &&
                offset <= bytes.size - byteCount &&
                destinationOffset <= destination.size - byteCount
        )
        bytes.copyInto(
            destination = destination,
            destinationOffset = destinationOffset,
            startIndex = offset,
            endIndex = offset + byteCount,
        )
    }

    internal fun readDependencyLayerAt(
        fileIndex: Int,
        offset: Int,
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ) = synchronized(payloadLock) {
        check(!closed) { "Project snapshot is closed." }
        val bytes = dependencyLayerBytes?.getOrNull(fileIndex)
            ?: throw IndexOutOfBoundsException(
                "Dependency-layer file index is outside its inventory.",
            )
        require(
            offset >= 0 &&
                destinationOffset >= 0 &&
                byteCount >= 0 &&
                offset <= bytes.size - byteCount &&
                destinationOffset <= destination.size - byteCount
        )
        bytes.copyInto(
            destination = destination,
            destinationOffset = destinationOffset,
            startIndex = offset,
            endIndex = offset + byteCount,
        )
    }

    internal fun readNodePackageTargetV2At(
        fileIndex: Int,
        offset: Int,
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ) = synchronized(payloadLock) {
        check(!closed) { "Project snapshot is closed." }
        val bytes = nodePackageTargetV2Bytes?.getOrNull(fileIndex)
            ?: throw IndexOutOfBoundsException(
                "Node package-target v2 file index is outside its inventory.",
            )
        require(
            offset >= 0 &&
                destinationOffset >= 0 &&
                byteCount >= 0 &&
                offset <= bytes.size - byteCount &&
                destinationOffset <= destination.size - byteCount
        )
        bytes.copyInto(
            destination = destination,
            destinationOffset = destinationOffset,
            startIndex = offset,
            endIndex = offset + byteCount,
        )
    }

    override fun close() = synchronized(payloadLock) {
        if (closed) return@synchronized
        rawConfigBytes?.fill(0)
        rawConfigBytes = null
        packageMetadataBytes?.forEach { bytes -> bytes.fill(0) }
        packageMetadataBytes?.clear()
        packageMetadataBytes = null
        dependencyLayerBytes?.forEach { bytes -> bytes.fill(0) }
        dependencyLayerBytes?.clear()
        dependencyLayerBytes = null
        packageTargetBytes?.forEach { bytes -> bytes.fill(0) }
        packageTargetBytes?.clear()
        packageTargetBytes = null
        nodePackageTargetV2Bytes?.forEach { bytes -> bytes.fill(0) }
        nodePackageTargetV2Bytes?.clear()
        nodePackageTargetV2Bytes = null
        closed = true
    }

    companion object {

        fun create(
            files: List<R2ProjectTextFile>,
            rootNames: List<String>,
            profile: R2ProjectProfile = R2ProjectProfile.PROVISIONAL_RHINO,
            entryPath: String = rootNames.firstOrNull().orEmpty(),
            acceptedProjectId: String? = null,
            acceptedInputAuthority: R2AcceptedInputAuthorityV1? = null,
        ): R2ProjectSnapshot = create(
            files = files,
            rootNames = rootNames,
            profile = profile,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WEB_MESSAGE,
            entryPath = entryPath,
            acceptedProjectId = acceptedProjectId,
            acceptedInputAuthority = acceptedInputAuthority,
        )

        internal fun create(
            files: List<R2ProjectTextFile>,
            rootNames: List<String>,
            profile: R2ProjectProfile = R2ProjectProfile.PROVISIONAL_RHINO,
            limits: R2ProjectSnapshotLimits,
            entryPath: String = rootNames.firstOrNull().orEmpty(),
            acceptedProjectId: String? = null,
            acceptedInputAuthority: R2AcceptedInputAuthorityV1? = null,
        ): R2ProjectSnapshot = create(
            files = files,
            rootNames = rootNames,
            profileAuthority = R2CompilationProfileRegistry.require(profile),
            limits = limits,
            entryPath = entryPath,
            acceptedProjectId = acceptedProjectId,
            acceptedInputAuthority = acceptedInputAuthority,
        )

        internal fun create(
            files: List<R2ProjectTextFile>,
            rootNames: List<String>,
            profileAuthority: R2CompilationProfileAuthority,
            limits: R2ProjectSnapshotLimits,
            entryPath: String = rootNames.firstOrNull().orEmpty(),
            acceptedProjectId: String? = null,
            rawConfigDescriptor: R2RawProjectConfigDescriptor? = null,
            rawConfigBytes: ByteArray? = null,
            packageMetadataInventory: R2PackageMetadataInventory? = null,
            packageMetadataBytes: List<ByteArray> = emptyList(),
            dependencyLayerInventory: R2DependencyLayerInventory? = null,
            dependencyLayerBytes: List<ByteArray> = emptyList(),
            packageTargetInventory: R2PackageTargetInventory? = null,
            packageTargetBytes: List<ByteArray> = emptyList(),
            nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory? = null,
            nodePackageTargetV2Bytes: List<ByteArray> = emptyList(),
            acceptedInputAuthority: R2AcceptedInputAuthorityV1? = null,
        ): R2ProjectSnapshot {
            require(R2CompilationProfileRegistry.containsExact(profileAuthority)) {
                "Project snapshot profile authority is not the exact local authority."
            }
            requireAcceptedProjectId(acceptedProjectId)
            requirePreCatalogPackageFormatCombination(
                profileAuthority = profileAuthority,
                hasRawConfig = rawConfigDescriptor != null,
                packageMetadataInventory = packageMetadataInventory,
                packageTargetInventory = packageTargetInventory,
                nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
            )

            val copiedFiles = LinkedHashMap<String, String>(files.size)
            val inventory = ArrayList<R2ProjectFileMetadata>(files.size)
            files.forEach { file ->
                val metadata = strictUtf8Metadata(
                    path = file.path,
                    text = file.text,
                    maximumBytes = limits.maximumSingleFileBytes,
                )
                copiedFiles[file.path] = file.text
                inventory += metadata
            }
            val validated = validateInventory(
                files = inventory,
                rootNames = rootNames,
                limits = limits,
                entryPath = entryPath,
                profileAuthority = profileAuthority,
            )

            require((rawConfigDescriptor == null) == (rawConfigBytes == null)) {
                "Raw project config metadata and bytes must be supplied together."
            }
            val ownedRawConfigBytes = rawConfigBytes?.let { bytes ->
                val descriptor = requireNotNull(rawConfigDescriptor)
                require(bytes.size.toLong() == descriptor.size) {
                    "Raw project config size does not match its descriptor."
                }
                require(R2Digests.equal(R2Digests.sha256(bytes), descriptor.sha256)) {
                    "Raw project config digest does not match its descriptor."
                }
                bytes.copyOf()
            }

            val ownedPackageMetadataBytes = ArrayList<ByteArray>(packageMetadataBytes.size)
            val ownedDependencyLayerBytes = ArrayList<ByteArray>(dependencyLayerBytes.size)
            val ownedPackageTargetBytes = ArrayList<ByteArray>(packageTargetBytes.size)
            val ownedNodePackageTargetV2Bytes =
                ArrayList<ByteArray>(nodePackageTargetV2Bytes.size)
            try {
                require(packageMetadataInventory != null || packageMetadataBytes.isEmpty()) {
                    "Package metadata bytes require an inventory."
                }
                require(
                    packageMetadataInventory == null ||
                        rawConfigDescriptor != null ||
                        profileAuthority ==
                            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                ) {
                    "Package metadata transport requires controlled raw config authority."
                }
                packageMetadataInventory?.let { packageInventory ->
                    require(packageInventory.files.size == packageMetadataBytes.size) {
                        "Package metadata bytes do not exactly cover their inventory."
                    }
                    packageInventory.files.forEachIndexed { index, descriptor ->
                        val ownedBytes = packageMetadataBytes[index].copyOf()
                        ownedPackageMetadataBytes += ownedBytes
                        require(ownedBytes.size.toLong() == descriptor.size) {
                            "Package metadata size does not match its descriptor: " +
                                "${descriptor.path}."
                        }
                        require(
                            R2Digests.equal(
                                R2Digests.sha256(ownedBytes),
                                descriptor.sha256,
                            ),
                        ) {
                            "Package metadata digest does not match its descriptor: " +
                                "${descriptor.path}."
                        }
                        require(
                            R2PackageMetadataPolicy.isExplicitlyAllowed(
                                descriptor.path,
                                packageInventory,
                            ),
                        ) {
                            "Package metadata path is outside its accepted inventory: " +
                                "${descriptor.path}."
                        }
                        R2PackageMetadataPolicy.validatePackageJson(ownedBytes)
                    }
                }
                require(dependencyLayerInventory != null || dependencyLayerBytes.isEmpty()) {
                    "Dependency-layer bytes require an inventory."
                }
                require(
                    dependencyLayerInventory == null ||
                        profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO ||
                        profileAuthority ==
                            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                ) {
                    "Dependency layers require the current Rhino or Node revision 2 profile."
                }
                dependencyLayerInventory?.let { layerInventory ->
                    val revalidatedInventory = R2DependencyLayerInventory.create(
                        identityKind = layerInventory.identityKind,
                        identityPath = layerInventory.identityPath,
                        identityFingerprint = layerInventory.identityFingerprint,
                        files = layerInventory.files,
                    )
                    require(
                        revalidatedInventory.files == layerInventory.files &&
                            revalidatedInventory.totalBytes == layerInventory.totalBytes &&
                            revalidatedInventory.totalPathBytes == layerInventory.totalPathBytes &&
                            R2Digests.equal(
                                revalidatedInventory.inventoryFingerprint,
                                layerInventory.inventoryFingerprint,
                            ) &&
                            R2Digests.equal(
                                revalidatedInventory.fingerprint,
                                layerInventory.fingerprint,
                            )
                    ) {
                        "Dependency-layer authority changed before snapshot indexing."
                    }
                    require(layerInventory.files.size == dependencyLayerBytes.size) {
                        "Dependency-layer bytes do not exactly cover their inventory."
                    }
                    layerInventory.files.forEachIndexed { index, descriptor ->
                        val ownedBytes = dependencyLayerBytes[index].copyOf()
                        ownedDependencyLayerBytes += ownedBytes
                        require(ownedBytes.size.toLong() == descriptor.size) {
                            "Dependency-layer size does not match its descriptor: " +
                                "${descriptor.path}."
                        }
                        require(
                            R2Digests.equal(
                                R2Digests.sha256(ownedBytes),
                                descriptor.sha256,
                            ),
                        ) {
                            "Dependency-layer digest does not match its descriptor: " +
                                "${descriptor.path}."
                        }
                        R2Utf8.decode(ownedBytes, "Dependency-layer file ${descriptor.path}")
                        R2DependencyLayerPolicy.validateDependencyFile(
                            descriptor.path,
                            ownedBytes,
                        )
                    }
                }
                require(packageTargetInventory != null || packageTargetBytes.isEmpty()) {
                    "Package target bytes require an inventory."
                }
                require(packageTargetInventory == null || rawConfigDescriptor != null) {
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
                        "Package target inventory authority changed before snapshot indexing."
                    }
                    require(targetInventory.files.size == packageTargetBytes.size) {
                        "Package target bytes do not exactly cover their inventory."
                    }
                    targetInventory.files.forEachIndexed { index, descriptor ->
                        val ownedBytes = packageTargetBytes[index].copyOf()
                        ownedPackageTargetBytes += ownedBytes
                        require(ownedBytes.size.toLong() == descriptor.size) {
                            "Package target size does not match its descriptor: ${descriptor.path}."
                        }
                        require(
                            R2Digests.equal(
                                R2Digests.sha256(ownedBytes),
                                descriptor.sha256,
                            ),
                        ) {
                            "Package target digest does not match its descriptor: " +
                                "${descriptor.path}."
                        }
                        require(
                            R2PackageTargetPolicy.isExplicitlyAllowed(
                                descriptor.path,
                                targetInventory,
                            ),
                        ) {
                            "Package target path is outside its accepted inventory: " +
                                "${descriptor.path}."
                        }
                        R2Utf8.decode(ownedBytes, "Package target ${descriptor.path}")
                    }
                }
                require(
                    nodePackageTargetV2Inventory != null || nodePackageTargetV2Bytes.isEmpty(),
                ) {
                    "Node package-target v2 bytes require an inventory."
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
                        "Node package-target v2 authority changed before snapshot indexing."
                    }
                    require(targetInventory.files.size == nodePackageTargetV2Bytes.size) {
                        "Node package-target v2 bytes do not exactly cover their inventory."
                    }
                    targetInventory.files.forEachIndexed { index, descriptor ->
                        val ownedBytes = nodePackageTargetV2Bytes[index].copyOf()
                        ownedNodePackageTargetV2Bytes += ownedBytes
                        require(ownedBytes.size.toLong() == descriptor.size) {
                            "Node package-target v2 size does not match its descriptor: " +
                                "${descriptor.path}."
                        }
                        require(
                            R2Digests.equal(
                                R2Digests.sha256(ownedBytes),
                                descriptor.sha256,
                            ),
                        ) {
                            "Node package-target v2 digest does not match its descriptor: " +
                                "${descriptor.path}."
                        }
                        R2Utf8.decode(ownedBytes, "Node package-target v2 ${descriptor.path}")
                    }
                }
                val packageMetadataBytesTotal =
                    packageMetadataInventory?.totalBytes ?: 0L
                val packageMetadataLargest =
                    packageMetadataInventory?.files?.maxOfOrNull { file -> file.size } ?: 0L
                val packageTargetBytesTotal = packageTargetInventory?.totalBytes ?: 0L
                val packageTargetLargest =
                    packageTargetInventory?.files?.maxOfOrNull { file -> file.size } ?: 0L
                val nodePackageTargetV2BytesTotal =
                    nodePackageTargetV2Inventory?.totalBytes ?: 0L
                val nodePackageTargetV2Largest =
                    nodePackageTargetV2Inventory?.files?.maxOfOrNull { file -> file.size } ?: 0L
                val acceptedCapacity = R2ProjectInputCapacity(
                    fileCount = Math.addExact(
                        Math.addExact(
                            Math.addExact(
                                inventory.size + if (rawConfigDescriptor == null) 0 else 1,
                                packageMetadataInventory?.files?.size ?: 0,
                            ),
                            packageTargetInventory?.files?.size ?: 0,
                        ),
                        nodePackageTargetV2Inventory?.files?.size ?: 0,
                    ),
                    largestFileUtf8Bytes = maxOf(
                        validated.largestFileUtf8Bytes,
                        rawConfigDescriptor?.size ?: 0L,
                        packageMetadataLargest,
                        packageTargetLargest,
                        nodePackageTargetV2Largest,
                    ),
                    totalUtf8Bytes = Math.addExact(
                        Math.addExact(
                            Math.addExact(
                                Math.addExact(
                                    validated.totalUtf8Bytes,
                                    rawConfigDescriptor?.size ?: 0L,
                                ),
                                packageMetadataBytesTotal,
                            ),
                            packageTargetBytesTotal,
                        ),
                        nodePackageTargetV2BytesTotal,
                    ),
                )
                val callerProjectFiles = inventory.filterNot { file ->
                    file.path.startsWith(DECLARATIONS_ROOT_PREFIX)
                }
                val acceptedCallerCapacity = R2ProjectInputCapacity(
                    fileCount = Math.addExact(
                        Math.addExact(
                            Math.addExact(
                                callerProjectFiles.size +
                                    if (rawConfigDescriptor == null) 0 else 1,
                                packageMetadataInventory?.files?.size ?: 0,
                            ),
                            packageTargetInventory?.files?.size ?: 0,
                        ),
                        nodePackageTargetV2Inventory?.files?.size ?: 0,
                    ),
                    largestFileUtf8Bytes = maxOf(
                        callerProjectFiles.maxOfOrNull { file -> file.utf8Bytes } ?: 0L,
                        rawConfigDescriptor?.size ?: 0L,
                        packageMetadataLargest,
                        packageTargetLargest,
                        nodePackageTargetV2Largest,
                    ),
                    totalUtf8Bytes = Math.addExact(
                        Math.addExact(
                            Math.addExact(
                                Math.addExact(
                                    callerProjectFiles.sumOf { file -> file.utf8Bytes },
                                    rawConfigDescriptor?.size ?: 0L,
                                ),
                                packageMetadataBytesTotal,
                            ),
                            packageTargetBytesTotal,
                        ),
                        nodePackageTargetV2BytesTotal,
                    ),
                )
                require(acceptedCapacity.admissionAgainst(limits).isEligible) {
                    "Project config, package metadata, package targets and files exceed the " +
                        "snapshot limits."
                }
                acceptedInputAuthority?.let { authority ->
                    require(
                        authority.capacity == acceptedCapacity &&
                            authority.callerCapacity == acceptedCallerCapacity,
                    ) {
                        "Accepted input authority capacity does not match the snapshot."
                    }
                }
                val nodePackageFormatCatalog = if (
                    profileAuthority ==
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 &&
                    packageMetadataInventory != null
                ) {
                    R2NodePackageFormatCatalog.create(
                        packageMetadataInventory,
                        ownedPackageMetadataBytes,
                    )
                } else {
                    null
                }
                val nodePackageMetadataCatalog = if (
                    profileAuthority ==
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 &&
                    packageMetadataInventory != null
                ) {
                    R2NodePackageMetadataCatalog.create(
                        packageMetadataInventory,
                        ownedPackageMetadataBytes,
                    )
                } else {
                    null
                }
                requirePackageFormatCombination(
                    profileAuthority = profileAuthority,
                    hasRawConfig = rawConfigDescriptor != null,
                    packageMetadataInventory = packageMetadataInventory,
                    packageTargetInventory = packageTargetInventory,
                    nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
                    nodePackageFormatCatalog = nodePackageFormatCatalog,
                )
                return R2ProjectSnapshot(
                    acceptedProjectId = acceptedProjectId,
                    acceptedInputAuthority = acceptedInputAuthority,
                    profileAuthority = profileAuthority,
                    rawConfigDescriptor = rawConfigDescriptor,
                    rawConfigBytes = ownedRawConfigBytes,
                    packageMetadataInventory = packageMetadataInventory,
                    nodePackageFormatCatalog = nodePackageFormatCatalog,
                    nodePackageMetadataCatalog = nodePackageMetadataCatalog,
                    packageMetadataBytes = ownedPackageMetadataBytes,
                    dependencyLayerInventory = dependencyLayerInventory,
                    dependencyLayerBytes = ownedDependencyLayerBytes,
                    packageTargetInventory = packageTargetInventory,
                    packageTargetBytes = ownedPackageTargetBytes,
                    nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
                    nodePackageTargetV2Bytes = ownedNodePackageTargetV2Bytes,
                    files = LinkedHashMap(copiedFiles),
                    rootNames = ArrayList(validated.rootNames),
                    entryPath = entryPath,
                    fileInventory = inventory,
                    totalUtf8Bytes = validated.totalUtf8Bytes,
                    largestFileUtf8Bytes = validated.largestFileUtf8Bytes,
                )
            } catch (failure: Throwable) {
                ownedNodePackageTargetV2Bytes.forEach { bytes -> bytes.fill(0) }
                ownedPackageTargetBytes.forEach { bytes -> bytes.fill(0) }
                ownedDependencyLayerBytes.forEach { bytes -> bytes.fill(0) }
                ownedPackageMetadataBytes.forEach { bytes -> bytes.fill(0) }
                ownedRawConfigBytes?.fill(0)
                throw failure
            }
        }

        internal fun requirePreCatalogPackageFormatCombination(
            profileAuthority: R2CompilationProfileAuthority,
            hasRawConfig: Boolean,
            packageMetadataInventory: R2PackageMetadataInventory?,
            packageTargetInventory: R2PackageTargetInventory?,
            nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory? = null,
        ) {
            require(packageTargetInventory == null || nodePackageTargetV2Inventory == null) {
                "Package target v1 and Node package-target v2 inventories are mutually exclusive."
            }
            when (profileAuthority) {
                R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1,
                R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_2,
                -> require(
                    nodePackageTargetV2Inventory == null,
                ) {
                    "Rhino does not accept Node package-target v2 authority."
                }
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1 -> require(
                    !hasRawConfig &&
                        packageMetadataInventory == null &&
                        packageTargetInventory == null &&
                        nodePackageTargetV2Inventory == null,
                ) {
                    "Node revision 1 accepts no raw config or package authority."
                }
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 -> require(
                    packageTargetInventory == null &&
                        (nodePackageTargetV2Inventory == null || packageMetadataInventory != null),
                ) {
                    "Node revision 2 accepts optional controlled config and only package-target " +
                        "v2 bound to metadata."
                }
                else -> throw IllegalArgumentException(
                    "Project snapshot profile authority is not registered for package formats.",
                )
            }
        }

        internal fun requireAcceptedProjectId(acceptedProjectId: String?) {
            if (acceptedProjectId == null) return
            require(
                acceptedProjectId.isNotBlank() &&
                    acceptedProjectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS,
            ) {
                "Accepted project ID is blank or exceeds the control-plane limit."
            }
        }

        internal fun requirePackageFormatCombination(
            profileAuthority: R2CompilationProfileAuthority,
            hasRawConfig: Boolean,
            packageMetadataInventory: R2PackageMetadataInventory?,
            packageTargetInventory: R2PackageTargetInventory?,
            nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory? = null,
            nodePackageFormatCatalog: R2NodePackageFormatCatalog?,
        ) {
            requirePreCatalogPackageFormatCombination(
                profileAuthority,
                hasRawConfig,
                packageMetadataInventory,
                packageTargetInventory,
                nodePackageTargetV2Inventory,
            )
            when (profileAuthority) {
                R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1,
                R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_2,
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1 -> require(
                    nodePackageFormatCatalog == null,
                ) {
                    "Rhino and Node revision 1 cannot carry a Node package-format catalog."
                }
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 -> {
                    require(
                        (packageMetadataInventory == null) ==
                            (nodePackageFormatCatalog == null),
                    ) {
                        "Node revision 2 metadata and package-format catalog must pair exactly."
                    }
                    if (packageMetadataInventory != null) {
                        requireCatalogMatchesInventory(
                            packageMetadataInventory,
                            requireNotNull(nodePackageFormatCatalog),
                        )
                    }
                }
                else -> error("Unreachable exact profile authority.")
            }
        }

        internal fun requireCatalogMatchesInventory(
            inventory: R2PackageMetadataInventory,
            catalog: R2NodePackageFormatCatalog,
        ) {
            require(
                catalog.dependencyRoots == inventory.dependencyRoots &&
                    catalog.packageMetadataInventoryCanonicalInput == inventory.canonicalInput &&
                    R2Digests.equal(
                        catalog.packageMetadataInventoryFingerprint,
                        inventory.fingerprint,
                    ) &&
                    catalog.records.map { record ->
                        Triple(
                            record.packageMetadataPath,
                            record.packageMetadataSize,
                            record.packageMetadataSha256,
                        )
                    } == inventory.files.map { descriptor ->
                        Triple(descriptor.path, descriptor.size, descriptor.sha256)
                    },
            ) {
                "Node package-format catalog does not match its exact C5 inventory."
            }
        }

        internal fun requireProvisionalWebMessageEligibility(
            snapshot: R2ProjectInputSnapshot,
        ) {
            val limits = R2ProjectSnapshotLimits.PROVISIONAL_WEB_MESSAGE
            val capacity = retainedInputCapacity(snapshot)
            require(capacity.fileCount <= limits.maximumFiles) {
                "Project snapshot exceeds the provisional WebMessage file-count limit."
            }
            require(capacity.largestFileUtf8Bytes <= limits.maximumSingleFileBytes) {
                "Project snapshot exceeds the provisional WebMessage single-file limit."
            }
            require(capacity.totalUtf8Bytes <= limits.maximumTotalBytes) {
                "Project snapshot exceeds the provisional WebMessage total-byte limit."
            }
        }

        internal fun requireOrdinaryCompilationEligibility(
            snapshot: R2ProjectInputSnapshot,
        ) {
            val admission = ordinaryCompilationAdmission(snapshot)
            admission.exceededDimension?.let { exceededDimension ->
                val message = when (exceededDimension) {
                    R2ProjectInputCapacityDimension.FILE_COUNT ->
                        "Project snapshot exceeds the ordinary compilation file-count limit."
                    R2ProjectInputCapacityDimension.SINGLE_FILE_BYTES ->
                        "Project snapshot exceeds the ordinary compilation single-file limit."
                    R2ProjectInputCapacityDimension.TOTAL_BYTES ->
                        "Project snapshot exceeds the ordinary compilation total-byte limit."
                }
                throw IllegalArgumentException(message)
            }
        }

        internal fun ordinaryCompilationAdmission(
            snapshot: R2ProjectInputSnapshot,
            limits: R2ProjectSnapshotLimits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
        ): R2ProjectInputAdmission {
            val derivedRetained = retainedInputCapacity(snapshot)
            val derivedCaller = callerInputCapacity(snapshot)
            val authority = snapshot.acceptedInputAuthority
            require(
                authority == null ||
                    authority.capacity == derivedRetained &&
                    authority.callerCapacity == derivedCaller,
            ) {
                "Accepted input authority capacity drifted after archive acceptance."
            }
            return (authority?.callerCapacity ?: derivedCaller).admissionAgainst(limits)
        }

        @Deprecated(
            message = "Use requireOrdinaryCompilationEligibility.",
            replaceWith = ReplaceWith("requireOrdinaryCompilationEligibility(snapshot)"),
        )
        internal fun requireProvisionalWorkerEligibility(
            snapshot: R2ProjectInputSnapshot,
        ) = requireOrdinaryCompilationEligibility(snapshot)

        @Deprecated(
            message = "Use ordinaryCompilationAdmission.",
            replaceWith = ReplaceWith("ordinaryCompilationAdmission(snapshot)"),
        )
        internal fun provisionalWorkerAdmission(
            snapshot: R2ProjectInputSnapshot,
        ): R2ProjectInputAdmission = ordinaryCompilationAdmission(snapshot)

        private fun retainedInputCapacity(
            snapshot: R2ProjectInputSnapshot,
        ): R2ProjectInputCapacity {
            val rawConfig = snapshot.rawConfigDescriptor
            val packageMetadata = snapshot.packageMetadataInventory
            val packageMetadataBytes = packageMetadata?.totalBytes ?: 0L
            val packageMetadataLargest = packageMetadata?.files?.maxOfOrNull { file ->
                file.size
            } ?: 0L
            val packageTargets = snapshot.packageTargetInventory
            val packageTargetBytes = packageTargets?.totalBytes ?: 0L
            val packageTargetLargest = packageTargets?.files?.maxOfOrNull { file ->
                file.size
            } ?: 0L
            val nodePackageTargetsV2 = snapshot.nodePackageTargetV2Inventory
            val nodePackageTargetV2Bytes = nodePackageTargetsV2?.totalBytes ?: 0L
            val nodePackageTargetV2Largest = nodePackageTargetsV2?.files?.maxOfOrNull { file ->
                file.size
            } ?: 0L
            return R2ProjectInputCapacity(
                fileCount = Math.addExact(
                    Math.addExact(
                        Math.addExact(
                            snapshot.fileInventory.size + if (rawConfig == null) 0 else 1,
                            packageMetadata?.files?.size ?: 0,
                        ),
                        packageTargets?.files?.size ?: 0,
                    ),
                    nodePackageTargetsV2?.files?.size ?: 0,
                ),
                largestFileUtf8Bytes = maxOf(
                    snapshot.largestFileUtf8Bytes,
                    rawConfig?.size ?: 0L,
                    packageMetadataLargest,
                    packageTargetLargest,
                    nodePackageTargetV2Largest,
                ),
                totalUtf8Bytes = Math.addExact(
                    Math.addExact(
                        Math.addExact(
                            Math.addExact(
                                snapshot.totalUtf8Bytes,
                                rawConfig?.size ?: 0L,
                            ),
                            packageMetadataBytes,
                        ),
                        packageTargetBytes,
                    ),
                    nodePackageTargetV2Bytes,
                ),
            )
        }

        private fun callerInputCapacity(
            snapshot: R2ProjectInputSnapshot,
        ): R2ProjectInputCapacity {
            val callerFiles = snapshot.fileInventory.filterNot { file ->
                file.path.startsWith(DECLARATIONS_ROOT_PREFIX)
            }
            val rawConfig = snapshot.rawConfigDescriptor
            val packageMetadata = snapshot.packageMetadataInventory
            val packageMetadataBytes = packageMetadata?.totalBytes ?: 0L
            val packageMetadataLargest = packageMetadata?.files?.maxOfOrNull { file ->
                file.size
            } ?: 0L
            val packageTargets = snapshot.packageTargetInventory
            val packageTargetBytes = packageTargets?.totalBytes ?: 0L
            val packageTargetLargest = packageTargets?.files?.maxOfOrNull { file ->
                file.size
            } ?: 0L
            val nodePackageTargetsV2 = snapshot.nodePackageTargetV2Inventory
            val nodePackageTargetV2Bytes = nodePackageTargetsV2?.totalBytes ?: 0L
            val nodePackageTargetV2Largest =
                nodePackageTargetsV2?.files?.maxOfOrNull { file -> file.size } ?: 0L
            return R2ProjectInputCapacity(
                fileCount = Math.addExact(
                    Math.addExact(
                        Math.addExact(
                            callerFiles.size + if (rawConfig == null) 0 else 1,
                            packageMetadata?.files?.size ?: 0,
                        ),
                        packageTargets?.files?.size ?: 0,
                    ),
                    nodePackageTargetsV2?.files?.size ?: 0,
                ),
                largestFileUtf8Bytes = maxOf(
                    callerFiles.maxOfOrNull { file -> file.utf8Bytes } ?: 0L,
                    rawConfig?.size ?: 0L,
                    packageMetadataLargest,
                    packageTargetLargest,
                    nodePackageTargetV2Largest,
                ),
                totalUtf8Bytes = Math.addExact(
                    Math.addExact(
                        Math.addExact(
                            Math.addExact(
                                callerFiles.sumOf { file -> file.utf8Bytes },
                                rawConfig?.size ?: 0L,
                            ),
                            packageMetadataBytes,
                        ),
                        packageTargetBytes,
                    ),
                    nodePackageTargetV2Bytes,
                ),
            )
        }

        internal fun validateInventory(
            files: List<R2ProjectFileMetadata>,
            rootNames: List<String>,
            limits: R2ProjectSnapshotLimits,
            entryPath: String = rootNames.firstOrNull().orEmpty(),
            profileAuthority: R2CompilationProfileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        ): R2ValidatedProjectInventory {
            require(files.size <= limits.maximumFiles) {
                "Project snapshot exceeds the input file-count limit."
            }
            val paths = LinkedHashSet<String>(files.size)
            val collisionPaths = HashMap<String, String>(files.size)
            var totalBytes = 0L
            var largestFileBytes = 0L
            files.forEach { file ->
                validateProjectPath(file.path, profileAuthority)
                require(file.utf8Bytes in 0L..limits.maximumSingleFileBytes) {
                    "Project file exceeds the single-file byte limit: ${file.path}."
                }
                require(file.utf16CodeUnits >= 0) {
                    "Project file has an invalid UTF-16 length: ${file.path}."
                }
                R2Digests.requireLowerSha256(file.sha256, "Project file SHA-256")
                require(paths.add(file.path)) {
                    "Project snapshot contains a duplicate path: ${file.path}."
                }
                val collisionKey = R2UnicodePathPolicy.asciiCaseFold(file.path)
                val previousPath = collisionPaths.putIfAbsent(collisionKey, file.path)
                require(previousPath == null) {
                    "Project snapshot contains a duplicate or ASCII case-fold collision: " +
                        "$previousPath and ${file.path}."
                }
                require(totalBytes <= limits.maximumTotalBytes - file.utf8Bytes) {
                    "Project snapshot exceeds the total input byte limit."
                }
                totalBytes += file.utf8Bytes
                largestFileBytes = maxOf(largestFileBytes, file.utf8Bytes)
            }

            require(paths.contains(REQUIRED_DEFAULT_LIBRARY)) {
                "Project snapshot must contain exactly one $REQUIRED_DEFAULT_LIBRARY."
            }
            require(rootNames.isNotEmpty()) {
                "Project snapshot must declare at least one root."
            }
            require(rootNames.size <= limits.maximumFiles) {
                "Project snapshot exceeds the root-count limit."
            }
            val copiedRootNames = ArrayList(rootNames)
            val uniqueRoots = LinkedHashSet<String>(copiedRootNames.size)
            copiedRootNames.forEach { rootName ->
                validateProjectPath(rootName, profileAuthority)
                require(
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        rootName,
                        profileAuthority,
                    ),
                ) {
                    "Project root must be an executable /sources path: $rootName."
                }
                require(paths.contains(rootName)) {
                    "Project root does not exist in the snapshot: $rootName."
                }
                require(uniqueRoots.add(rootName)) {
                    "Project root is declared more than once: $rootName."
                }
            }
            val requiredRoots = paths
                .filterTo(LinkedHashSet()) { path ->
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        path,
                        profileAuthority,
                    )
                }
            require(uniqueRoots == requiredRoots) {
                val missingRoots = requiredRoots - uniqueRoots
                val unexpectedRoots = uniqueRoots - requiredRoots
                "Every executable /sources file must be an explicit root; " +
                    "missing=$missingRoots, unexpected=$unexpectedRoots."
            }
            requireAcceptedEntry(
                files = files,
                rootNames = copiedRootNames,
                entryPath = entryPath,
                profileAuthority = profileAuthority,
            )
            copiedRootNames.sort()
            return R2ValidatedProjectInventory(
                rootNames = copiedRootNames,
                totalUtf8Bytes = totalBytes,
                largestFileUtf8Bytes = largestFileBytes,
            )
        }

        internal fun validateProjectPath(
            path: String,
            profileAuthority: R2CompilationProfileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        ) {
            require(R2UnicodePathPolicy.isCanonicalAbsolutePath(path)) {
                "Project VFS path must be a bounded canonical UTF-8 absolute path: $path."
            }

            val segments = path.drop(1).split('/')
            require(segments.size >= 2) {
                "Project VFS path must identify a file below an allowed root: $path."
            }
            require(segments.all(String::isNotEmpty)) {
                "Project VFS path must not contain an empty segment: $path."
            }
            require(segments.size <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH) {
                "Project VFS path exceeds the supported depth: $path."
            }
            require(segments.none { segment -> segment == "." || segment == ".." }) {
                "Project VFS path must not contain '.' or '..': $path."
            }
            require(segments.first() == SOURCES_ROOT || segments.first() == DECLARATIONS_ROOT) {
                "Project VFS path must be below /sources or /declarations: $path."
            }
            R2ProjectSourceKindAdmission.sourcePathRejection(
                path,
                profileAuthority,
            )?.let { rejection ->
                throw IllegalArgumentException(
                    "Project VFS path uses a reserved compiler escape path: " +
                        "${rejection.contractValue}: $path.",
                )
            }
            require(
                segments.first() != DECLARATIONS_ROOT || path.endsWith(".d.ts"),
            ) {
                "Project declaration path must end with .d.ts: $path."
            }
        }

        internal fun requireAcceptedEntry(
            files: List<R2ProjectFileMetadata>,
            rootNames: List<String>,
            entryPath: String,
            profileAuthority: R2CompilationProfileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        ) {
            requireCompleteRootInventory(files, rootNames, profileAuthority)
            validateProjectPath(entryPath, profileAuthority)
            require(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    entryPath,
                    profileAuthority,
                ),
            ) {
                "Project entry must be an executable non-declaration /sources path: " +
                    "$entryPath."
            }
            require(files.any { file -> file.path == entryPath }) {
                "Project entry does not exist in the snapshot: $entryPath."
            }
            require(rootNames.any { rootName -> rootName == entryPath }) {
                "Project entry is not part of the complete root inventory: $entryPath."
            }
        }

        private fun requireCompleteRootInventory(
            files: List<R2ProjectFileMetadata>,
            rootNames: List<String>,
            profileAuthority: R2CompilationProfileAuthority,
        ) {
            val paths = files.mapTo(LinkedHashSet(files.size)) { file -> file.path }
            require(rootNames.isNotEmpty()) {
                "Project snapshot must declare at least one root."
            }
            val uniqueRoots = LinkedHashSet<String>(rootNames.size)
            rootNames.forEach { rootName ->
                validateProjectPath(rootName, profileAuthority)
                require(
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        rootName,
                        profileAuthority,
                    ),
                ) {
                    "Project root must be an executable /sources path: $rootName."
                }
                require(paths.contains(rootName)) {
                    "Project root does not exist in the snapshot: $rootName."
                }
                require(uniqueRoots.add(rootName)) {
                    "Project root is declared more than once: $rootName."
                }
            }
            val requiredRoots = paths
                .filterTo(LinkedHashSet()) { path ->
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        path,
                        profileAuthority,
                    )
                }
            require(uniqueRoots == requiredRoots) {
                val missingRoots = requiredRoots - uniqueRoots
                val unexpectedRoots = uniqueRoots - requiredRoots
                "Every executable /sources file must be an explicit root; " +
                    "missing=$missingRoots, unexpected=$unexpectedRoots."
            }
        }

        private fun strictUtf8Metadata(
            path: String,
            text: String,
            maximumBytes: Long,
        ): R2ProjectFileMetadata {
            require(text.length.toLong() <= maximumBytes) {
                "Project file exceeds the single-file byte limit: $path."
            }
            val encoder = StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val bytes = try {
                val encoded = encoder.encode(CharBuffer.wrap(text))
                ByteArray(encoded.remaining()).also(encoded::get)
            } catch (error: CharacterCodingException) {
                throw IllegalArgumentException(
                    "Project file is not valid UTF-8 text: $path.",
                    error,
                )
            }
            require(bytes.size.toLong() <= maximumBytes) {
                "Project file exceeds the single-file byte limit: $path."
            }
            return R2ProjectFileMetadata(
                path = path,
                utf8Bytes = bytes.size.toLong(),
                utf16CodeUnits = text.length,
                sha256 = R2Digests.sha256(bytes),
            ).also {
                bytes.fill(0)
            }
        }

        internal fun String.isTypeScriptSourceRoot(): Boolean =
            R2ProjectSourceKindAdmission.isExecutableSourcePath(
                this,
                R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            )

        private const val SOURCES_ROOT = "sources"
        private const val DECLARATIONS_ROOT = "declarations"
        private const val DECLARATIONS_ROOT_PREFIX = "/declarations/"
        private const val REQUIRED_DEFAULT_LIBRARY = "/declarations/lib.d.ts"
    }
}

internal data class R2ValidatedProjectInventory(
    val rootNames: List<String>,
    val totalUtf8Bytes: Long,
    val largestFileUtf8Bytes: Long,
)

private class R2ByteArrayProjectFileCursor(
    private var bytes: ByteArray?,
) : R2ProjectFileCursor {

    private var offset = 0

    override val remainingBytes: Long
        get() = (bytes?.size?.minus(offset) ?: 0).toLong()

    override fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int {
        val source = checkNotNull(bytes) {
            "Project file cursor is closed."
        }
        require(
            destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount
        )
        if (byteCount == 0 || offset == source.size) return 0
        val count = minOf(byteCount, source.size - offset)
        source.copyInto(
            destination = destination,
            destinationOffset = destinationOffset,
            startIndex = offset,
            endIndex = offset + count,
        )
        offset += count
        return count
    }

    override fun close() {
        bytes?.fill(0)
        bytes = null
    }
}

private class R2HeapRawProjectConfigCursor(
    private val snapshot: R2ProjectSnapshot,
    private val byteLength: Int,
) : R2ProjectFileCursor {

    private var offset = 0
    private var closed = false

    override val remainingBytes: Long
        get() = if (closed) 0L else (byteLength - offset).toLong()

    override fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int {
        check(!closed) { "Raw project config cursor is closed." }
        require(
            destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount
        )
        if (byteCount == 0 || offset == byteLength) return 0
        val count = minOf(byteCount, byteLength - offset)
        snapshot.readRawConfigAt(
            offset = offset,
            destination = destination,
            destinationOffset = destinationOffset,
            byteCount = count,
        )
        offset += count
        return count
    }

    override fun close() {
        closed = true
        offset = byteLength
    }
}

private class R2HeapPackageMetadataCursor(
    private val snapshot: R2ProjectSnapshot,
    private val fileIndex: Int,
    private val byteLength: Int,
) : R2ProjectFileCursor {

    private var offset = 0
    private var closed = false

    override val remainingBytes: Long
        get() = if (closed) 0L else (byteLength - offset).toLong()

    override fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int {
        check(!closed) { "Package metadata cursor is closed." }
        require(
            destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount
        )
        if (byteCount == 0 || offset == byteLength) return 0
        val count = minOf(byteCount, byteLength - offset)
        snapshot.readPackageMetadataAt(
            fileIndex = fileIndex,
            offset = offset,
            destination = destination,
            destinationOffset = destinationOffset,
            byteCount = count,
        )
        offset += count
        return count
    }

    override fun close() {
        closed = true
        offset = byteLength
    }
}

private class R2HeapPackageTargetCursor(
    private val snapshot: R2ProjectSnapshot,
    private val fileIndex: Int,
    private val byteLength: Int,
) : R2ProjectFileCursor {

    private var offset = 0
    private var closed = false

    override val remainingBytes: Long
        get() = if (closed) 0L else (byteLength - offset).toLong()

    override fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int {
        check(!closed) { "Package target cursor is closed." }
        require(
            destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount
        )
        if (byteCount == 0 || offset == byteLength) return 0
        val count = minOf(byteCount, byteLength - offset)
        snapshot.readPackageTargetAt(
            fileIndex = fileIndex,
            offset = offset,
            destination = destination,
            destinationOffset = destinationOffset,
            byteCount = count,
        )
        offset += count
        return count
    }

    override fun close() {
        closed = true
        offset = byteLength
    }
}

private class R2HeapDependencyLayerCursor(
    private val snapshot: R2ProjectSnapshot,
    private val fileIndex: Int,
    private val byteLength: Int,
) : R2ProjectFileCursor {

    private var offset = 0
    private var closed = false

    override val remainingBytes: Long
        get() = if (closed) 0L else (byteLength - offset).toLong()

    override fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int {
        check(!closed) { "Dependency-layer cursor is closed." }
        require(
            destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount
        )
        if (byteCount == 0 || offset == byteLength) return 0
        val count = minOf(byteCount, byteLength - offset)
        snapshot.readDependencyLayerAt(
            fileIndex = fileIndex,
            offset = offset,
            destination = destination,
            destinationOffset = destinationOffset,
            byteCount = count,
        )
        offset += count
        return count
    }

    override fun close() {
        closed = true
        offset = byteLength
    }
}

private class R2HeapNodePackageTargetV2Cursor(
    private val snapshot: R2ProjectSnapshot,
    private val fileIndex: Int,
    private val byteLength: Int,
) : R2ProjectFileCursor {

    private var offset = 0
    private var closed = false

    override val remainingBytes: Long
        get() = if (closed) 0L else (byteLength - offset).toLong()

    override fun read(
        destination: ByteArray,
        destinationOffset: Int,
        byteCount: Int,
    ): Int {
        check(!closed) { "Node package-target v2 cursor is closed." }
        require(
            destinationOffset >= 0 &&
                byteCount >= 0 &&
                destinationOffset <= destination.size - byteCount
        )
        if (byteCount == 0 || offset == byteLength) return 0
        val count = minOf(byteCount, byteLength - offset)
        snapshot.readNodePackageTargetV2At(
            fileIndex = fileIndex,
            offset = offset,
            destination = destination,
            destinationOffset = destinationOffset,
            byteCount = count,
        )
        offset += count
        return count
    }

    override fun close() {
        closed = true
        offset = byteLength
    }
}

internal data class R2ProjectSnapshotLimits(
    val maximumFiles: Int,
    val maximumSingleFileBytes: Long,
    val maximumTotalBytes: Long,
) {

    init {
        require(maximumFiles > 0)
        require(maximumSingleFileBytes >= 0L)
        require(maximumTotalBytes >= 0L)
    }

    companion object {
        val PROVISIONAL_WEB_MESSAGE = R2ProjectSnapshotLimits(
            maximumFiles = 256,
            maximumSingleFileBytes = 256L * 1024L,
            maximumTotalBytes = 512L * 1024L,
        )

        /**
         * Ordinary compilation admission authority. P3a removes native aggregate materialization,
         * but the Worker still retains decoded strings and compiler AST state. Production applies
         * this public R2 quota before framing a file-backed snapshot.
         */
        val ORDINARY_COMPILATION = R2ProjectSnapshotLimits(
            maximumFiles = TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES,
            maximumSingleFileBytes =
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            maximumTotalBytes = TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        )

        /** Revision 2 limits used only when an already-compiled host sends a revision 2 manifest. */
        val ORDINARY_COMPILATION_REVISION_2 = R2ProjectSnapshotLimits(
            maximumFiles = TypeScriptR2Contract.LEGACY_MAX_ORDINARY_COMPILATION_INPUT_FILES,
            maximumSingleFileBytes =
                TypeScriptR2Contract.LEGACY_MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
            maximumTotalBytes =
                TypeScriptR2Contract.LEGACY_MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        )

        fun forInputQuotaRevision(revision: Int): R2ProjectSnapshotLimits? = when (revision) {
            TypeScriptR2Contract.LEGACY_ORDINARY_COMPILATION_INPUT_QUOTA_REVISION ->
                ORDINARY_COMPILATION_REVISION_2
            TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION ->
                ORDINARY_COMPILATION
            else -> null
        }

        /** Historical name retained as an exact alias, never as an independent quota. */
        @Deprecated(
            message = "Use ORDINARY_COMPILATION; the quota is no longer provisional.",
            replaceWith = ReplaceWith("ORDINARY_COMPILATION"),
        )
        val PROVISIONAL_WORKER = ORDINARY_COMPILATION

        /**
         * Historical internal name retained while P3b callers migrate to the explicit Worker name.
         * This is not the frozen archive or file-backed native capacity.
         */
        @Deprecated(
            message = "Use ORDINARY_COMPILATION; this is an ordinary compilation quota.",
            replaceWith = ReplaceWith("ORDINARY_COMPILATION"),
        )
        val PROVISIONAL_ARCHIVE = ORDINARY_COMPILATION

        /**
         * Native P3a spool capacity. The frozen archive quota includes request-manifest.json,
         * while the Worker VFS additionally receives plugin-owned declarations. P3a can retain
         * both on disk but ordinary compilation admission remains [ORDINARY_COMPILATION].
         */
        val FILE_BACKED_ARCHIVE = R2ProjectSnapshotLimits(
            maximumFiles = R2_FILE_BACKED_MAXIMUM_FILES,
            maximumSingleFileBytes = TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
            maximumTotalBytes = R2_FILE_BACKED_MAXIMUM_TOTAL_BYTES,
        )
    }
}

internal enum class R2ProjectInputCapacityDimension {
    FILE_COUNT,
    SINGLE_FILE_BYTES,
    TOTAL_BYTES,
}

/**
 * Payload-independent capacity facts used by admission and P3b telemetry.
 *
 * Keeping this separate from [R2ProjectSnapshotLimits] makes the 72 MiB native envelope and the
 * 8 MiB ordinary compilation fence independently observable without changing dispatch behavior.
 */
internal data class R2ProjectInputCapacity(
    val fileCount: Int,
    val largestFileUtf8Bytes: Long,
    val totalUtf8Bytes: Long,
) {

    init {
        require(fileCount >= 0)
        require(largestFileUtf8Bytes >= 0L)
        require(totalUtf8Bytes >= 0L)
        require(largestFileUtf8Bytes <= totalUtf8Bytes)
    }

    fun admissionAgainst(limits: R2ProjectSnapshotLimits): R2ProjectInputAdmission {
        val exceededDimension = when {
            fileCount > limits.maximumFiles -> R2ProjectInputCapacityDimension.FILE_COUNT
            largestFileUtf8Bytes > limits.maximumSingleFileBytes ->
                R2ProjectInputCapacityDimension.SINGLE_FILE_BYTES
            totalUtf8Bytes > limits.maximumTotalBytes ->
                R2ProjectInputCapacityDimension.TOTAL_BYTES
            else -> null
        }
        return R2ProjectInputAdmission(
            capacity = this,
            limits = limits,
            exceededDimension = exceededDimension,
        )
    }
}

internal data class R2ProjectInputAdmission(
    val capacity: R2ProjectInputCapacity,
    val limits: R2ProjectSnapshotLimits,
    val exceededDimension: R2ProjectInputCapacityDimension?,
) {

    val isEligible: Boolean
        get() = exceededDimension == null
}

@Deprecated(
    message = "Use TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES.",
    replaceWith = ReplaceWith("TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES"),
)
internal const val R2_PROVISIONAL_WORKER_MAXIMUM_FILES =
    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES
@Deprecated(
    message = "Use TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES.",
    replaceWith = ReplaceWith(
        "TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES",
    ),
)
internal const val R2_PROVISIONAL_WORKER_MAXIMUM_SINGLE_FILE_BYTES =
    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES
@Deprecated(
    message = "Use TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES.",
    replaceWith = ReplaceWith("TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES"),
)
internal const val R2_PROVISIONAL_WORKER_MAXIMUM_TOTAL_BYTES =
    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES
internal const val R2_TRUSTED_DECLARATION_MAXIMUM_FILES = 512
internal const val R2_TRUSTED_DECLARATION_MAXIMUM_BYTES = 8L * 1024L * 1024L
internal const val R2_FILE_BACKED_MAXIMUM_FILES =
    TypeScriptCompilerContract.MAX_INPUT_FILES + R2_TRUSTED_DECLARATION_MAXIMUM_FILES
internal const val R2_FILE_BACKED_MAXIMUM_TOTAL_BYTES =
    TypeScriptCompilerContract.MAX_INPUT_BYTES + R2_TRUSTED_DECLARATION_MAXIMUM_BYTES
