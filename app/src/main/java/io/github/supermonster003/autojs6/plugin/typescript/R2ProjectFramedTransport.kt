package io.github.supermonster003.autojs6.plugin.typescript

import java.security.MessageDigest
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Worker-retained input quota selected by trusted host code, never by the caller archive.
 *
 * [TEST_ONLY_EXPANDED] preserves the historical 72 MiB file-backed evidence path without making
 * that capacity selectable through the Public R2 manifest or control plane.
 */
internal enum class R2WorkerInputQuotaAuthority(
    val revision: Int,
    val limits: R2ProjectSnapshotLimits,
) {
    ORDINARY(
        revision = TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION,
        limits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION,
    ),
    TEST_ONLY_EXPANDED(
        revision = Int.MAX_VALUE,
        limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
    ),
}

/**
 * P3a R2 text-VFS transport.
 *
 * Native input stays file-backed and frames are produced through bounded cursor reads. The
 * current Worker still reconstructs a heap-backed VFS, so ordinary production dispatch keeps its
 * provisional 8 MiB admission limit. A DEBUG-only device gate can exercise the larger file-backed
 * envelope. Frames are sent stop-and-wait and every encoded envelope is independently bounded.
 */
internal class R2ProjectInputFramer(
    val requestId: String,
    private val snapshot: R2ProjectInputSnapshot,
    private val workerSpinMilliseconds: Int = 0,
    private val workerSpinPhase: R2ProjectExecutionPhase? = null,
    private val workerInputQuotaAuthority: R2WorkerInputQuotaAuthority =
        R2WorkerInputQuotaAuthority.ORDINARY,
) {

    private val files = snapshot.fileInventory.toList()
    private val rootNames = snapshot.rootNames.toList()
    private val entryPath = snapshot.entryPath
    private val profileAuthority = snapshot.profileAuthority
    private val rawConfig = snapshot.rawConfigDescriptor
    private val packageMetadata = snapshot.packageMetadataInventory
    private val nodePackageFormatCatalog = snapshot.nodePackageFormatCatalog
    private val packageMetadataFiles = packageMetadata?.files?.toList().orEmpty()
    private val packageTargets = snapshot.packageTargetInventory
    private val packageTargetFiles = packageTargets?.files?.toList().orEmpty()
    private val nodePackageTargetsV2 = snapshot.nodePackageTargetV2Inventory
    private val nodePackageTargetV2Files = nodePackageTargetsV2?.files?.toList().orEmpty()
    private val targetProfile = profileAuthority.profile.contractValue
    private val acceptedInputAuthority = snapshot.acceptedInputAuthority ?: run {
        require(snapshot.acceptedProjectId == null) {
            "Project-scoped transport requires frozen accepted-input authority."
        }
        R2InputArchiveSchemaV1.compatibilityAuthority(snapshot)
    }
    private val isNodeRevisionTwo =
        profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2

    init {
        // Reject delegated snapshot package-authority drift before deriving the resolver-v2
        // authority from that untrusted combination.
        R2ProjectSnapshot.requirePackageFormatCombination(
            profileAuthority = profileAuthority,
            hasRawConfig = rawConfig != null,
            packageMetadataInventory = packageMetadata,
            packageTargetInventory = packageTargets,
            nodePackageTargetV2Inventory = nodePackageTargetsV2,
            nodePackageFormatCatalog = nodePackageFormatCatalog,
        )
        require(
            acceptedInputAuthority.inputQuotaRevision ==
                R2WorkerInputQuotaAuthority.ORDINARY.revision,
        ) {
            "Accepted archive authority must retain the ordinary caller quota revision."
        }
        require(
            acceptedInputAuthority.capacity
                .admissionAgainst(workerInputQuotaAuthority.limits)
                .isEligible,
        ) {
            "Accepted input exceeds the trusted Worker quota authority."
        }
    }

    private val nodeResolutionV2SnapshotTargets = if (isNodeRevisionTwo) {
        R2NodeResolutionV2SnapshotTargetInventory.create(
            profileAuthority = profileAuthority,
            files = files,
        )
    } else {
        null
    }
    private val nodeResolutionV2Authority = nodeResolutionV2SnapshotTargets?.let { snapshotTargets ->
        R2NodeResolutionV2Authority(
            snapshotTargets = snapshotTargets,
            packageMetadata = snapshot.nodePackageMetadataCatalog,
            packageFormat = nodePackageFormatCatalog,
            packageTargets = nodePackageTargetsV2,
            profileAuthority = profileAuthority,
        )
    }
    private val declaredPackageMetadataBytes = packageMetadata?.totalBytes ?: 0L
    private val declaredPackageTargetBytes = packageTargets?.totalBytes ?: 0L
    private val declaredNodePackageTargetV2Bytes = nodePackageTargetsV2?.totalBytes ?: 0L
    private val transportedFileCount = Math.addExact(
        Math.addExact(
            Math.addExact(packageMetadataFiles.size, packageTargetFiles.size),
            nodePackageTargetV2Files.size,
        ),
        files.size,
    )
    private val declaredFileBytes = Math.addExact(
        Math.addExact(
            Math.addExact(snapshot.totalUtf8Bytes, declaredPackageMetadataBytes),
            declaredPackageTargetBytes,
        ),
        declaredNodePackageTargetV2Bytes,
    )
    private val declaredTotalBytes = Math.addExact(
        declaredFileBytes,
        rawConfig?.size ?: 0L,
    )
    private val inputAuthorityFields: Array<Pair<String, Any>> = arrayOf(
        "inputArchiveSchemaRevision" to acceptedInputAuthority.schemaRevision,
        "inputQuotaRevision" to workerInputQuotaAuthority.revision,
        "callerInventoryFingerprint" to acceptedInputAuthority.callerInventoryFingerprint,
        "retainedInventoryFingerprint" to acceptedInputAuthority.retainedInventoryFingerprint,
        "retainedFileCount" to acceptedInputAuthority.capacity.fileCount,
        "largestFileBytes" to acceptedInputAuthority.capacity.largestFileUtf8Bytes,
        "declarationProfileFingerprint" to
            acceptedInputAuthority.declarationProfileFingerprint,
        "declarationInventoryFingerprint" to
            acceptedInputAuthority.declarationInventoryFingerprint,
        "declarationFileCount" to acceptedInputAuthority.declarationFileCount,
        "declarationByteLength" to acceptedInputAuthority.declarationByteLength,
    )
    private val chunkBuffer = ByteArray(R2_PROJECT_FRAME_RAW_CHUNK_BYTES)

    private var stage = Stage.PROJECT_START
    private var sequence = 0
    private var configOffset = 0L
    private var configChunkIndex = 0
    private var fileIndex = 0
    private var fileOffset = 0L
    private var chunkIndex = 0
    private var awaitingAcknowledgement = false
    private var currentConfigCursor: R2ProjectFileCursor? = null
    private var currentConfigDigest: MessageDigest? = null
    private var currentFileCursor: R2ProjectFileCursor? = null
    private var currentFileDigest: MessageDigest? = null
    private var openedCursorCount = 0
    private var closedCursorCount = 0
    private var released = false

    var maximumEncodedFrameBytes: Int = 0
        private set

    var sentFrameCount: Int = 0
        private set

    val isComplete: Boolean
        get() = stage == Stage.COMPLETE

    val outstandingSequence: Int?
        get() = sequence.takeIf { awaitingAcknowledgement }

    val outstandingCompletesTransport: Boolean
        get() = awaitingAcknowledgement && stage == Stage.PROJECT_END

    fun telemetryForTesting(): R2ProjectInputTransportTelemetry {
        val acceptedConfigBytes = when (stage) {
            Stage.PROJECT_START,
            Stage.CONFIG_START -> 0L
            Stage.CONFIG_CHUNK,
            Stage.CONFIG_END -> configOffset
            else -> rawConfig?.size ?: 0L
        }
        val completedFileBytes = (0 until fileIndex.coerceAtMost(transportedFileCount))
            .sumOf { completedFileIndex -> transportedFile(completedFileIndex).byteLength }
        return R2ProjectInputTransportTelemetry(
            requestId = requestId,
            sentFrameCount = sentFrameCount,
            maximumEncodedFrameBytes = maximumEncodedFrameBytes,
            acceptedPayloadBytes = acceptedConfigBytes + completedFileBytes + fileOffset,
            totalPayloadBytes = declaredTotalBytes,
            fileIndex = fileIndex,
            fileOffset = fileOffset,
            stage = stage.name,
            complete = isComplete,
            openedCursorCount = openedCursorCount,
            closedCursorCount = closedCursorCount,
            activeCursor = currentConfigCursor != null || currentFileCursor != null,
            released = released,
        )
    }

    init {
        require(requestId.isNotEmpty() && requestId.length <= MAX_REQUEST_ID_CHARS) {
            "Project transport request ID is invalid."
        }
        require(transportedFileCount > 0 && transportedFileCount <= MAXIMUM_PROJECT_FILES) {
            "Project transport file count is outside the file-backed native limit."
        }
        require(files.sumOf(R2ProjectFileMetadata::utf8Bytes) == snapshot.totalUtf8Bytes) {
            "Project transport bytes do not match the snapshot budget."
        }
        require(
            acceptedInputAuthority.capacity.fileCount ==
                transportedFileCount + (if (rawConfig == null) 0 else 1) &&
                acceptedInputAuthority.capacity.totalUtf8Bytes == declaredTotalBytes,
        ) {
            "Project transport does not match its accepted retained-input authority."
        }
        require(R2CompilationProfileRegistry.containsExact(profileAuthority)) {
            "Project transport profile authority is not the exact local authority."
        }
        val filePaths = files.mapTo(HashSet(files.size), R2ProjectFileMetadata::path)
        when (profileAuthority.profile) {
            R2ProjectProfile.PROVISIONAL_RHINO -> require(
                filePaths.none { path -> path.startsWith(NODE_DECLARATION_PREFIX) },
            ) {
                "Rhino project transport contains the Node declaration layer."
            }
            R2ProjectProfile.PROVISIONAL_NODE_NEXT -> require(
                NODE_DECLARATION_ENTRY in filePaths &&
                    filePaths.none { path -> path.startsWith(RHINO_DECLARATION_PREFIX) },
            ) {
                "NodeNext project transport has no exact isolated Node declaration layer."
            }
        }
        R2ProjectSnapshot.requireAcceptedEntry(
            files = files,
            rootNames = rootNames,
            entryPath = entryPath,
            profileAuthority = profileAuthority,
        )
        require(declaredTotalBytes <= MAXIMUM_PROJECT_BYTES) {
            "Project transport exceeds the file-backed native byte limit."
        }
        require(packageMetadata == null || rawConfig != null || isNodeRevisionTwo) {
            "Package metadata transport requires controlled raw config or exact Node revision 2."
        }
        require(packageTargets == null || rawConfig != null) {
            "Package target transport requires controlled raw config authority."
        }
        require(packageTargets == null || packageMetadata != null) {
            "Package target transport requires controlled package metadata authority."
        }
        require(packageTargets == null || nodePackageTargetsV2 == null) {
            "Package target v1 and Node package-target v2 transports are mutually exclusive."
        }
        require(nodePackageTargetsV2 == null || isNodeRevisionTwo) {
            "Node package-target v2 transport requires the exact Node revision 2 profile."
        }
        require(nodePackageTargetsV2 == null || packageMetadata != null) {
            "Node package-target v2 transport requires controlled package metadata authority."
        }
        require(
            packageTargets == null ||
                R2Digests.equal(
                    packageTargets.packageMetadataInventoryFingerprint,
                    requireNotNull(packageMetadata).fingerprint,
                ),
        ) {
            "Package target transport is bound to different package metadata."
        }
        require(
            nodePackageTargetsV2 == null ||
                R2Digests.equal(
                    nodePackageTargetsV2.packageMetadataInventoryFingerprint,
                    requireNotNull(packageMetadata).fingerprint,
                ),
        ) {
            "Node package-target v2 transport is bound to different package metadata."
        }
        require(
            !isNodeRevisionTwo ||
                (
                    nodeResolutionV2SnapshotTargets != null &&
                        nodeResolutionV2Authority != null
                    ),
        ) {
            "Node revision 2 transport has no exact resolver-v2 identity."
        }
        require(workerSpinMilliseconds in 0..MAXIMUM_TEST_SPIN_MILLISECONDS) {
            "Project Worker test spin is outside the supported range."
        }
        require(
            (workerSpinMilliseconds == 0) == (workerSpinPhase == null),
        ) {
            "Project Worker test spin milliseconds and phase must be enabled together."
        }
    }

    fun nextFrame(): String {
        check(!released) { "Project input transport is released." }
        check(!isComplete) { "Project input transport is already complete." }
        check(!awaitingAcknowledgement) {
            "Project input transport cannot advance before its acknowledgement."
        }
        val frame = when (stage) {
            Stage.PROJECT_START -> if (isNodeRevisionTwo) {
                val catalog = nodePackageFormatCatalog
                val resolverSnapshot = requireNotNull(nodeResolutionV2SnapshotTargets)
                val resolverAuthority = requireNotNull(nodeResolutionV2Authority)
                if (packageMetadata == null) {
                    check(catalog == null && nodePackageTargetsV2 == null) {
                        "Node revision 2 no-boundary transport cannot carry package authority."
                    }
                    // Formal schema-v1 exact 30-field no-metadata Program start: the frozen
                    // 24-field transport identity plus six module-output/resolver fields.
                    R2FramedJson.objectValue(
                        *inputAuthorityFields,
                        "kind" to "projectInputStart",
                        "requestId" to requestId,
                        "sequence" to sequence,
                        "targetProfile" to targetProfile,
                        "profileSchemaRevision" to profileAuthority.profileSchemaRevision,
                        "profileRevision" to profileAuthority.profileRevision,
                        "configSchemaRevision" to profileAuthority.configSchemaRevision,
                        "configKind" to profileAuthority.configKind,
                        "entryPath" to entryPath,
                        "runtimeCapabilitiesFingerprint" to
                            profileAuthority.runtimeCapabilitiesFingerprint,
                        "fileCount" to transportedFileCount,
                        "totalBytes" to declaredTotalBytes,
                        "workerSpinMilliseconds" to workerSpinMilliseconds,
                        "workerSpinPhase" to
                            (workerSpinPhase?.protocolValue ?: WORKER_SPIN_PHASE_NONE),
                        "nodeModuleOutputPolicySchemaRevision" to
                            R2NodeModuleOutputPolicy.SCHEMA_REVISION,
                        "nodeModuleOutputPolicyRevision" to
                            R2NodeModuleOutputPolicy.POLICY_REVISION,
                        "nodeModuleOutputPolicyFingerprint" to
                            R2NodeModuleOutputPolicy.fingerprint,
                        "resolverPolicyRevision" to R2NodeResolutionKernelV2.POLICY_REVISION,
                        "snapshotTargetInventoryFingerprint" to resolverSnapshot.fingerprint,
                        "targetUniverseFingerprint" to resolverAuthority.targetUniverseFingerprint,
                    )
                } else {
                    val metadata = packageMetadata
                    val exactCatalog = requireNotNull(catalog) {
                        "Node revision 2 package metadata requires its exact catalog."
                    }
                    val targetFields = nodePackageTargetsV2
                    // Formal schema-v1 exact 40-field metadata-only or 47-field
                    // metadata+v2-target Program start. The 37-field transport identity
                    // precedes only additive resolver/v2-target identity.
                    val commonFields: Array<Pair<String, Any>> = arrayOf(
                        *inputAuthorityFields,
                        "kind" to "projectInputStart",
                        "requestId" to requestId,
                        "sequence" to sequence,
                        "targetProfile" to targetProfile,
                        "profileSchemaRevision" to profileAuthority.profileSchemaRevision,
                        "profileRevision" to profileAuthority.profileRevision,
                        "configSchemaRevision" to profileAuthority.configSchemaRevision,
                        "configKind" to profileAuthority.configKind,
                        "entryPath" to entryPath,
                        "runtimeCapabilitiesFingerprint" to
                            profileAuthority.runtimeCapabilitiesFingerprint,
                        "packageMetadataSchemaRevision" to
                            R2PackageMetadataPolicy.SCHEMA_REVISION,
                        "packageMetadataPolicyRevision" to metadata.authority.policyRevision,
                        "packageMetadataPolicyFingerprint" to
                            metadata.authority.policyFingerprint,
                        "packageMetadataCanonical" to metadata.canonicalInput,
                        "packageMetadataInventoryFingerprint" to metadata.fingerprint,
                        "packageMetadataFileCount" to packageMetadataFiles.size,
                        "packageMetadataByteLength" to declaredPackageMetadataBytes,
                        "nodePackageFormatSchemaRevision" to
                            R2NodePackageFormatCatalog.SCHEMA_REVISION,
                        "nodePackageFormatCatalogRevision" to
                            R2NodePackageFormatCatalog.CATALOG_REVISION,
                        "nodePackageFormatCatalogFingerprint" to exactCatalog.fingerprint,
                        "nodeModuleOutputPolicySchemaRevision" to
                            R2NodeModuleOutputPolicy.SCHEMA_REVISION,
                        "nodeModuleOutputPolicyRevision" to
                            R2NodeModuleOutputPolicy.POLICY_REVISION,
                        "nodeModuleOutputPolicyFingerprint" to
                            R2NodeModuleOutputPolicy.fingerprint,
                        "fileCount" to transportedFileCount,
                        "totalBytes" to declaredTotalBytes,
                        "workerSpinMilliseconds" to workerSpinMilliseconds,
                        "workerSpinPhase" to
                            (workerSpinPhase?.protocolValue ?: WORKER_SPIN_PHASE_NONE),
                        "resolverPolicyRevision" to R2NodeResolutionKernelV2.POLICY_REVISION,
                        "snapshotTargetInventoryFingerprint" to resolverSnapshot.fingerprint,
                        "targetUniverseFingerprint" to resolverAuthority.targetUniverseFingerprint,
                    )
                    if (targetFields == null) {
                        R2FramedJson.objectValue(*commonFields)
                    } else {
                        R2FramedJson.objectValue(
                            *commonFields,
                            "nodePackageTargetSchemaRevision" to
                                R2NodePackageTargetPolicyV2.SCHEMA_REVISION,
                            "nodePackageTargetPolicyRevision" to
                                R2NodePackageTargetPolicyV2.POLICY_REVISION,
                            "nodePackageTargetPolicyFingerprint" to
                                R2NodePackageTargetPolicyV2.fingerprint,
                            "nodePackageTargetCanonical" to targetFields.canonicalInput,
                            "nodePackageTargetFingerprint" to targetFields.fingerprint,
                            "nodePackageTargetFileCount" to nodePackageTargetV2Files.size,
                            "nodePackageTargetByteLength" to declaredNodePackageTargetV2Bytes,
                        )
                    }
                }
            } else packageTargets?.let { targets ->
                val config = requireNotNull(rawConfig)
                val metadata = requireNotNull(packageMetadata)
                R2FramedJson.objectValue(
                    *inputAuthorityFields,
                    "kind" to "projectInputStart",
                    "requestId" to requestId,
                    "sequence" to sequence,
                    "targetProfile" to targetProfile,
                    "profileSchemaRevision" to profileAuthority.profileSchemaRevision,
                    "profileRevision" to profileAuthority.profileRevision,
                    "configSchemaRevision" to
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
                    "configKind" to TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
                    "entryPath" to entryPath,
                    "configByteLength" to config.size,
                    "configSha256" to config.sha256,
                    "configPolicyRevision" to config.policyRevision,
                    "configPolicyFingerprint" to config.policyFingerprint,
                    "packageMetadataSchemaRevision" to
                        R2PackageMetadataPolicy.SCHEMA_REVISION,
                    "packageMetadataPolicyRevision" to metadata.authority.policyRevision,
                    "packageMetadataPolicyFingerprint" to
                        metadata.authority.policyFingerprint,
                    "packageMetadataCanonical" to metadata.canonicalInput,
                    "packageMetadataFingerprint" to metadata.fingerprint,
                    "packageMetadataFileCount" to packageMetadataFiles.size,
                    "packageMetadataByteLength" to declaredPackageMetadataBytes,
                    "packageTargetSchemaRevision" to R2PackageTargetPolicy.SCHEMA_REVISION,
                    "packageTargetPolicyRevision" to targets.authority.policyRevision,
                    "packageTargetPolicyFingerprint" to targets.authority.policyFingerprint,
                    "packageTargetCanonical" to targets.canonicalInput,
                    "packageTargetFingerprint" to targets.fingerprint,
                    "packageTargetFileCount" to packageTargetFiles.size,
                    "packageTargetByteLength" to declaredPackageTargetBytes,
                    "fileCount" to transportedFileCount,
                    "totalBytes" to declaredTotalBytes,
                    "workerSpinMilliseconds" to workerSpinMilliseconds,
                    "workerSpinPhase" to
                        (workerSpinPhase?.protocolValue ?: WORKER_SPIN_PHASE_NONE),
                )
            } ?: packageMetadata?.let { metadata ->
                val config = requireNotNull(rawConfig)
                R2FramedJson.objectValue(
                    *inputAuthorityFields,
                    "kind" to "projectInputStart",
                    "requestId" to requestId,
                    "sequence" to sequence,
                    "targetProfile" to targetProfile,
                    "profileSchemaRevision" to profileAuthority.profileSchemaRevision,
                    "profileRevision" to profileAuthority.profileRevision,
                    "configSchemaRevision" to
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
                    "configKind" to TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
                    "entryPath" to entryPath,
                    "configByteLength" to config.size,
                    "configSha256" to config.sha256,
                    "configPolicyRevision" to config.policyRevision,
                    "configPolicyFingerprint" to config.policyFingerprint,
                    "packageMetadataSchemaRevision" to
                        R2PackageMetadataPolicy.SCHEMA_REVISION,
                    "packageMetadataPolicyRevision" to metadata.authority.policyRevision,
                    "packageMetadataPolicyFingerprint" to
                        metadata.authority.policyFingerprint,
                    "packageMetadataCanonical" to metadata.canonicalInput,
                    "packageMetadataFingerprint" to metadata.fingerprint,
                    "packageMetadataFileCount" to packageMetadataFiles.size,
                    "packageMetadataByteLength" to declaredPackageMetadataBytes,
                    "fileCount" to transportedFileCount,
                    "totalBytes" to declaredTotalBytes,
                    "workerSpinMilliseconds" to workerSpinMilliseconds,
                    "workerSpinPhase" to
                        (workerSpinPhase?.protocolValue ?: WORKER_SPIN_PHASE_NONE),
                )
            } ?: rawConfig?.let { config ->
                R2FramedJson.objectValue(
                    *inputAuthorityFields,
                    "kind" to "projectInputStart",
                    "requestId" to requestId,
                    "sequence" to sequence,
                    "targetProfile" to targetProfile,
                    "profileSchemaRevision" to profileAuthority.profileSchemaRevision,
                    "profileRevision" to profileAuthority.profileRevision,
                    "configSchemaRevision" to
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
                    "configKind" to TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
                    "entryPath" to entryPath,
                    "configByteLength" to config.size,
                    "configSha256" to config.sha256,
                    "configPolicyRevision" to config.policyRevision,
                    "configPolicyFingerprint" to config.policyFingerprint,
                    "fileCount" to transportedFileCount,
                    "totalBytes" to declaredTotalBytes,
                    "workerSpinMilliseconds" to workerSpinMilliseconds,
                    "workerSpinPhase" to
                        (workerSpinPhase?.protocolValue ?: WORKER_SPIN_PHASE_NONE),
                )
            } ?: R2FramedJson.objectValue(
                *inputAuthorityFields,
                "kind" to "projectInputStart",
                "requestId" to requestId,
                "sequence" to sequence,
                "targetProfile" to targetProfile,
                "profileSchemaRevision" to profileAuthority.profileSchemaRevision,
                "profileRevision" to profileAuthority.profileRevision,
                "configSchemaRevision" to profileAuthority.configSchemaRevision,
                "configKind" to profileAuthority.configKind,
                "fileCount" to transportedFileCount,
                "totalBytes" to declaredTotalBytes,
                "workerSpinMilliseconds" to workerSpinMilliseconds,
                "workerSpinPhase" to
                    (workerSpinPhase?.protocolValue ?: WORKER_SPIN_PHASE_NONE),
            )
            Stage.CONFIG_START -> requireNotNull(rawConfig).let { config ->
                R2FramedJson.objectValue(
                    "kind" to "projectInputConfigStart",
                    "requestId" to requestId,
                    "sequence" to sequence,
                    "path" to config.path,
                    "byteLength" to config.size,
                    "sha256" to config.sha256,
                )
            }
            Stage.CONFIG_CHUNK -> requireNotNull(rawConfig).let { config ->
                val chunkBytes = readCurrentConfigChunk(config)
                try {
                    R2FramedJson.objectValue(
                        "kind" to "projectInputConfigChunk",
                        "requestId" to requestId,
                        "sequence" to sequence,
                        "chunkIndex" to configChunkIndex,
                        "data" to R2FramedBase64.encode(
                            bytes = chunkBuffer,
                            offset = 0,
                            length = chunkBytes,
                        ),
                    )
                } finally {
                    chunkBuffer.fill(0, 0, chunkBytes)
                }
            }
            Stage.CONFIG_END -> {
                verifyAndCloseCurrentConfig()
                R2FramedJson.objectValue(
                    "kind" to "projectInputConfigEnd",
                    "requestId" to requestId,
                    "sequence" to sequence,
                    "chunkCount" to configChunkIndex,
                )
            }
            Stage.FILE_START -> currentFile().let { file ->
                R2FramedJson.objectValue(
                    "kind" to "projectInputFileStart",
                    "requestId" to requestId,
                    "sequence" to sequence,
                    "fileIndex" to fileIndex,
                    "path" to file.path,
                    "byteLength" to file.byteLength,
                    "sha256" to file.sha256,
                )
            }
            Stage.FILE_CHUNK -> currentFile().let { file ->
                val chunkBytes = readCurrentFileChunk(file)
                try {
                    R2FramedJson.objectValue(
                        "kind" to "projectInputFileChunk",
                        "requestId" to requestId,
                        "sequence" to sequence,
                        "fileIndex" to fileIndex,
                        "chunkIndex" to chunkIndex,
                        "data" to R2FramedBase64.encode(
                            bytes = chunkBuffer,
                            offset = 0,
                            length = chunkBytes,
                        ),
                    )
                } finally {
                    chunkBuffer.fill(0, 0, chunkBytes)
                }
            }
            Stage.FILE_END -> {
                verifyAndCloseCurrentFile()
                R2FramedJson.objectValue(
                    "kind" to "projectInputFileEnd",
                    "requestId" to requestId,
                    "sequence" to sequence,
                    "fileIndex" to fileIndex,
                    "chunkCount" to chunkIndex,
                )
            }
            Stage.PROJECT_END -> R2FramedJson.objectValue(
                "kind" to "projectInputEnd",
                "requestId" to requestId,
                "sequence" to sequence,
                "fileCount" to transportedFileCount,
                "totalBytes" to declaredTotalBytes,
            )
            Stage.COMPLETE -> error("Project input transport is already complete.")
        }
        val encodedBytes = R2Utf8.encode(frame, "Project input frame").size
        val maximumFrameBytes =
            if (stage == Stage.PROJECT_START) {
                R2_PROJECT_MAXIMUM_START_FRAME_BYTES
            } else {
                R2_PROJECT_MAXIMUM_FRAME_BYTES
            }
        check(encodedBytes <= maximumFrameBytes) {
            "Project input frame exceeds $maximumFrameBytes bytes."
        }
        maximumEncodedFrameBytes = maxOf(maximumEncodedFrameBytes, encodedBytes)
        sentFrameCount++
        awaitingAcknowledgement = true
        return frame
    }

    fun acknowledge(acknowledgedSequence: Int) {
        check(!released) { "Project input transport is released." }
        check(awaitingAcknowledgement && acknowledgedSequence == sequence) {
            "Project input acknowledgement does not match the outstanding frame."
        }
        awaitingAcknowledgement = false
        when (stage) {
            Stage.PROJECT_START -> stage =
                if (rawConfig == null) Stage.FILE_START else Stage.CONFIG_START
            Stage.CONFIG_START -> {
                openCurrentConfig()
                stage =
                    if (requireNotNull(rawConfig).size == 0L) {
                        Stage.CONFIG_END
                    } else {
                        Stage.CONFIG_CHUNK
                    }
            }
            Stage.CONFIG_CHUNK -> {
                val config = requireNotNull(rawConfig)
                configChunkIndex++
                if (configOffset == config.size) {
                    stage = Stage.CONFIG_END
                }
            }
            Stage.CONFIG_END -> {
                check(currentConfigCursor == null && currentConfigDigest == null) {
                    "Project input config was not verified before its end acknowledgement."
                }
                stage = Stage.FILE_START
            }
            Stage.FILE_START -> {
                openCurrentFile()
                stage =
                    if (currentFile().byteLength == 0L) {
                        Stage.FILE_END
                    } else {
                        Stage.FILE_CHUNK
                    }
            }
            Stage.FILE_CHUNK -> {
                val file = currentFile()
                chunkIndex++
                if (fileOffset == file.byteLength) {
                    stage = Stage.FILE_END
                }
            }
            Stage.FILE_END -> {
                check(currentFileCursor == null && currentFileDigest == null) {
                    "Project input file was not verified before its end acknowledgement."
                }
                fileIndex++
                fileOffset = 0L
                chunkIndex = 0
                stage =
                    if (fileIndex == transportedFileCount) {
                        Stage.PROJECT_END
                    } else {
                        Stage.FILE_START
                    }
            }
            Stage.PROJECT_END -> {
                stage = Stage.COMPLETE
                release()
            }
            Stage.COMPLETE -> error("Project input transport is already complete.")
        }
        if (stage != Stage.COMPLETE) {
            sequence = Math.addExact(sequence, 1)
        }
    }

    fun release() {
        released = true
        chunkBuffer.fill(0)
        currentConfigDigest = null
        currentFileDigest = null
        val cursor = currentConfigCursor ?: currentFileCursor ?: return
        cursor.close()
        closedCursorCount = Math.addExact(closedCursorCount, 1)
        currentConfigCursor = null
        currentFileCursor = null
    }

    private fun openCurrentConfig() {
        check(currentConfigCursor == null && currentConfigDigest == null) {
            "Project input transport already has an open config."
        }
        val config = requireNotNull(rawConfig)
        val cursor = requireNotNull(snapshot.openRawConfig()) {
            "Project input snapshot did not expose its accepted raw config."
        }
        currentConfigCursor = cursor
        openedCursorCount = Math.addExact(openedCursorCount, 1)
        try {
            check(cursor.remainingBytes == config.size) {
                "Project input config cursor size does not match its accepted metadata."
            }
            currentConfigDigest = MessageDigest.getInstance("SHA-256")
        } catch (error: Throwable) {
            try {
                cursor.close()
                closedCursorCount = Math.addExact(closedCursorCount, 1)
                currentConfigCursor = null
            } catch (closeFailure: Throwable) {
                error.addSuppressed(closeFailure)
            }
            throw error
        }
    }

    private fun readCurrentConfigChunk(config: R2RawProjectConfigDescriptor): Int {
        val remaining = config.size - configOffset
        check(remaining > 0L) {
            "Project input config chunk has no remaining bytes."
        }
        val expected = minOf(
            R2_PROJECT_FRAME_RAW_CHUNK_BYTES.toLong(),
            remaining,
        ).toInt()
        val cursor = requireNotNull(currentConfigCursor) {
            "Project input config cursor is not open."
        }
        var accepted = 0
        try {
            while (accepted < expected) {
                val before = cursor.remainingBytes
                val read = cursor.read(
                    destination = chunkBuffer,
                    destinationOffset = accepted,
                    byteCount = expected - accepted,
                )
                check(read in 1..(expected - accepted)) {
                    "Project input config cursor ended before its accepted metadata."
                }
                check(cursor.remainingBytes == before - read) {
                    "Project input config cursor remaining-byte accounting is inconsistent."
                }
                accepted += read
            }
        } catch (error: Throwable) {
            chunkBuffer.fill(0)
            throw error
        }
        requireNotNull(currentConfigDigest).update(chunkBuffer, 0, accepted)
        configOffset += accepted
        check(configOffset <= config.size) {
            "Project input config cursor exceeded its accepted metadata."
        }
        return accepted
    }

    private fun verifyAndCloseCurrentConfig() {
        val config = requireNotNull(rawConfig)
        val cursor = requireNotNull(currentConfigCursor) {
            "Project input config cursor is not open."
        }
        val digest = requireNotNull(currentConfigDigest) {
            "Project input config digest is not active."
        }
        var failure: Throwable? = null
        var cursorClosed = false
        try {
            check(configOffset == config.size && cursor.remainingBytes == 0L) {
                "Project input config ended before its accepted metadata."
            }
            val actualDigest = digest.digest()
            try {
                check(R2Digests.equal(config.sha256, actualDigest.toLowerHex())) {
                    "Project input config digest changed after snapshot acceptance."
                }
            } finally {
                actualDigest.fill(0)
            }
        } catch (error: Throwable) {
            failure = error
        }
        try {
            cursor.close()
            cursorClosed = true
            closedCursorCount = Math.addExact(closedCursorCount, 1)
        } catch (closeFailure: Throwable) {
            if (failure == null) {
                failure = closeFailure
            } else {
                failure.addSuppressed(closeFailure)
            }
        } finally {
            if (cursorClosed) {
                currentConfigCursor = null
            }
            currentConfigDigest = null
        }
        failure?.let { throw it }
    }

    private fun currentFile(): TransportedFile = transportedFile(fileIndex)

    private fun transportedFile(index: Int): TransportedFile {
        packageMetadataFiles.getOrNull(index)?.let { descriptor ->
            return TransportedFile(
                path = R2PackageMetadataPolicy.toVfsPath(descriptor.path),
                byteLength = descriptor.size,
                sha256 = descriptor.sha256,
                packageMetadataIndex = index,
                packageTargetIndex = null,
                nodePackageTargetV2Index = null,
            )
        }
        val packageTargetIndex = index - packageMetadataFiles.size
        packageTargetFiles.getOrNull(packageTargetIndex)?.let { descriptor ->
            return TransportedFile(
                path = R2PackageTargetPolicy.toVfsPath(descriptor.path),
                byteLength = descriptor.size,
                sha256 = descriptor.sha256,
                packageMetadataIndex = null,
                packageTargetIndex = packageTargetIndex,
                nodePackageTargetV2Index = null,
            )
        }
        val nodePackageTargetV2Index = packageTargetIndex - packageTargetFiles.size
        nodePackageTargetV2Files.getOrNull(nodePackageTargetV2Index)?.let { descriptor ->
            return TransportedFile(
                path = "/${descriptor.path}",
                byteLength = descriptor.size,
                sha256 = descriptor.sha256,
                packageMetadataIndex = null,
                packageTargetIndex = null,
                nodePackageTargetV2Index = nodePackageTargetV2Index,
            )
        }
        val projectIndex = nodePackageTargetV2Index - nodePackageTargetV2Files.size
        val metadata = files.getOrNull(projectIndex)
            ?: error("Project input transport has no current file.")
        return TransportedFile(
            path = metadata.path,
            byteLength = metadata.utf8Bytes,
            sha256 = metadata.sha256,
            packageMetadataIndex = null,
            packageTargetIndex = null,
            nodePackageTargetV2Index = null,
        )
    }

    private fun openCurrentFile() {
        check(currentFileCursor == null && currentFileDigest == null) {
            "Project input transport already has an open file."
        }
        val file = currentFile()
        val cursor = when {
            file.packageMetadataIndex != null ->
                snapshot.openPackageMetadataFile(file.packageMetadataIndex)
            file.packageTargetIndex != null ->
                snapshot.openPackageTargetFile(file.packageTargetIndex)
            file.nodePackageTargetV2Index != null ->
                snapshot.openNodePackageTargetV2File(file.nodePackageTargetV2Index)
            else -> snapshot.openFile(
                fileIndex - packageMetadataFiles.size - packageTargetFiles.size -
                    nodePackageTargetV2Files.size,
            )
        }
        currentFileCursor = cursor
        openedCursorCount = Math.addExact(openedCursorCount, 1)
        try {
            check(cursor.remainingBytes == file.byteLength) {
                "Project input cursor size does not match its accepted inventory."
            }
            currentFileDigest = MessageDigest.getInstance("SHA-256")
        } catch (error: Throwable) {
            try {
                cursor.close()
                closedCursorCount = Math.addExact(closedCursorCount, 1)
                currentFileCursor = null
            } catch (closeFailure: Throwable) {
                error.addSuppressed(closeFailure)
            }
            throw error
        }
    }

    private fun readCurrentFileChunk(file: TransportedFile): Int {
        val remaining = file.byteLength - fileOffset
        check(remaining > 0L) {
            "Project input file chunk has no remaining bytes."
        }
        val expected = minOf(
            R2_PROJECT_FRAME_RAW_CHUNK_BYTES.toLong(),
            remaining,
        ).toInt()
        val cursor = requireNotNull(currentFileCursor) {
            "Project input file cursor is not open."
        }
        var accepted = 0
        try {
            while (accepted < expected) {
                val before = cursor.remainingBytes
                val read = cursor.read(
                    destination = chunkBuffer,
                    destinationOffset = accepted,
                    byteCount = expected - accepted,
                )
                check(read in 1..(expected - accepted)) {
                    "Project input cursor ended before its accepted inventory."
                }
                check(cursor.remainingBytes == before - read) {
                    "Project input cursor remaining-byte accounting is inconsistent."
                }
                accepted += read
            }
        } catch (error: Throwable) {
            chunkBuffer.fill(0)
            throw error
        }
        requireNotNull(currentFileDigest).update(chunkBuffer, 0, accepted)
        fileOffset += accepted
        check(fileOffset <= file.byteLength) {
            "Project input cursor exceeded its accepted inventory."
        }
        return accepted
    }

    private fun verifyAndCloseCurrentFile() {
        val file = currentFile()
        val cursor = requireNotNull(currentFileCursor) {
            "Project input file cursor is not open."
        }
        val digest = requireNotNull(currentFileDigest) {
            "Project input file digest is not active."
        }
        var failure: Throwable? = null
        var cursorClosed = false
        try {
            check(fileOffset == file.byteLength && cursor.remainingBytes == 0L) {
                "Project input file ended before its accepted inventory."
            }
            val actualDigest = digest.digest()
            try {
                check(R2Digests.equal(file.sha256, actualDigest.toLowerHex())) {
                    "Project input file digest changed after snapshot acceptance."
                }
            } finally {
                actualDigest.fill(0)
            }
        } catch (error: Throwable) {
            failure = error
        }
        try {
            cursor.close()
            cursorClosed = true
            closedCursorCount = Math.addExact(closedCursorCount, 1)
        } catch (closeFailure: Throwable) {
            if (failure == null) {
                failure = closeFailure
            } else {
                failure.addSuppressed(closeFailure)
            }
        } finally {
            if (cursorClosed) {
                currentFileCursor = null
            }
            currentFileDigest = null
        }
        failure?.let { throw it }
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private data class TransportedFile(
        val path: String,
        val byteLength: Long,
        val sha256: String,
        val packageMetadataIndex: Int?,
        val packageTargetIndex: Int?,
        val nodePackageTargetV2Index: Int?,
    )

    private enum class Stage {
        PROJECT_START,
        CONFIG_START,
        CONFIG_CHUNK,
        CONFIG_END,
        FILE_START,
        FILE_CHUNK,
        FILE_END,
        PROJECT_END,
        COMPLETE,
    }

    private companion object {
        const val NODE_DECLARATION_ENTRY = "/declarations/node/index.d.ts"
        const val NODE_DECLARATION_PREFIX = "/declarations/node/"
        const val RHINO_DECLARATION_PREFIX = "/declarations/rhino/"
        const val MAX_REQUEST_ID_CHARS = 128
        const val MAXIMUM_PROJECT_FILES = R2_FILE_BACKED_MAXIMUM_FILES
        const val MAXIMUM_PROJECT_BYTES = R2_FILE_BACKED_MAXIMUM_TOTAL_BYTES
        const val MAXIMUM_TEST_SPIN_MILLISECONDS = 10_000
        const val WORKER_SPIN_PHASE_NONE = "none"
    }
}

internal data class R2ProjectInputTransportTelemetry(
    val requestId: String,
    val sentFrameCount: Int,
    val maximumEncodedFrameBytes: Int,
    val acceptedPayloadBytes: Long,
    val totalPayloadBytes: Long,
    val fileIndex: Int,
    val fileOffset: Long,
    val stage: String,
    val complete: Boolean,
    val openedCursorCount: Int,
    val closedCursorCount: Int,
    val activeCursor: Boolean,
    val released: Boolean,
)

internal class R2ProjectResultAccumulator(
    val requestId: String,
    val generation: Int,
    private val byteLength: Int,
    private val sha256: String,
    private val chunkCount: Int,
) {

    private var bytes: ByteArray? = ByteArray(byteLength)
    private val digest = MessageDigest.getInstance("SHA-256")
    private var nextChunkIndex = 0
    private var acceptedBytes = 0
    private var finished = false

    init {
        require(byteLength in 1..MAXIMUM_PROJECT_RESULT_BYTES) {
            "Project result byte length is outside the provisional limit."
        }
        require(
            chunkCount ==
                (byteLength + R2_PROJECT_FRAME_RAW_CHUNK_BYTES - 1) /
                R2_PROJECT_FRAME_RAW_CHUNK_BYTES,
        ) {
            "Project result chunk count does not match its byte length."
        }
        R2Digests.requireLowerSha256(sha256, "Project result digest")
    }

    fun append(
        index: Int,
        chunk: ByteArray,
    ) {
        check(!finished && index == nextChunkIndex && index < chunkCount) {
            "Project result chunk is out of sequence."
        }
        val expectedBytes =
            if (index == chunkCount - 1) {
                byteLength - index * R2_PROJECT_FRAME_RAW_CHUNK_BYTES
            } else {
                R2_PROJECT_FRAME_RAW_CHUNK_BYTES
            }
        check(chunk.size == expectedBytes) {
            "Project result chunk has an unexpected decoded size."
        }
        chunk.copyInto(
            destination = requireNotNull(bytes),
            destinationOffset = acceptedBytes,
        )
        digest.update(chunk)
        acceptedBytes += chunk.size
        nextChunkIndex++
    }

    fun hasAcceptedCompleteInventory(): Boolean =
        !finished &&
            bytes != null &&
            nextChunkIndex == chunkCount &&
            acceptedBytes == byteLength

    fun finish(
        endByteLength: Int,
        endSha256: String,
        endChunkCount: Int,
    ): ByteArray {
        check(!finished) { "Project result transport is already complete." }
        check(
            endByteLength == byteLength &&
                endSha256 == sha256 &&
                endChunkCount == chunkCount &&
                nextChunkIndex == chunkCount &&
                acceptedBytes == byteLength,
        ) {
            "Project result end frame does not match its accepted inventory."
        }
        check(R2Digests.equal(sha256, digest.digest().toLowerHex())) {
            "Project result digest does not match its framed bytes."
        }
        finished = true
        return requireNotNull(bytes).also {
            bytes = null
        }
    }

    fun release() {
        bytes?.fill(0)
        bytes = null
        finished = true
    }

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private companion object {
        const val MAXIMUM_PROJECT_RESULT_BYTES = 6 * 1024 * 1024
    }
}

internal object R2FramedBase64 {

    fun encode(bytes: ByteArray): String = encode(bytes, 0, bytes.size)

    fun encode(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): String {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        if (length == 0) return ""
        val output = StringBuilder(((length + 2) / 3) * 4)
        var cursor = offset
        val end = offset + length
        while (cursor + 3 <= end) {
            val value =
                ((bytes[cursor].toInt() and 0xff) shl 16) or
                    ((bytes[cursor + 1].toInt() and 0xff) shl 8) or
                    (bytes[cursor + 2].toInt() and 0xff)
            output.append(ALPHABET[value ushr 18])
            output.append(ALPHABET[value ushr 12 and 0x3f])
            output.append(ALPHABET[value ushr 6 and 0x3f])
            output.append(ALPHABET[value and 0x3f])
            cursor += 3
        }
        val remaining = end - cursor
        if (remaining == 1) {
            val value = bytes[cursor].toInt() and 0xff
            output.append(ALPHABET[value ushr 2])
            output.append(ALPHABET[value shl 4 and 0x3f])
            output.append("==")
        } else if (remaining == 2) {
            val value =
                ((bytes[cursor].toInt() and 0xff) shl 8) or
                    (bytes[cursor + 1].toInt() and 0xff)
            output.append(ALPHABET[value ushr 10])
            output.append(ALPHABET[value ushr 4 and 0x3f])
            output.append(ALPHABET[value shl 2 and 0x3f])
            output.append('=')
        }
        return output.toString()
    }

    fun decode(value: String): ByteArray {
        require(value.length % 4 == 0) {
            "Framed Base64 length is not canonical."
        }
        if (value.isEmpty()) return ByteArray(0)
        val padding = when {
            value.endsWith("==") -> 2
            value.endsWith("=") -> 1
            else -> 0
        }
        require(
            value.dropLast(padding).all { character ->
                character.code < DECODE.size && DECODE[character.code] >= 0
            } &&
                value.takeLast(padding).all { character -> character == '=' } &&
                '=' !in value.dropLast(padding),
        ) {
            "Framed Base64 contains an invalid character or padding."
        }
        val output = ByteArray(value.length / 4 * 3 - padding)
        var sourceOffset = 0
        var outputOffset = 0
        while (sourceOffset < value.length) {
            val first = decodeCharacter(value[sourceOffset])
            val second = decodeCharacter(value[sourceOffset + 1])
            val third =
                if (value[sourceOffset + 2] == '=') 0 else decodeCharacter(value[sourceOffset + 2])
            val fourth =
                if (value[sourceOffset + 3] == '=') 0 else decodeCharacter(value[sourceOffset + 3])
            val combined =
                (first shl 18) or
                    (second shl 12) or
                    (third shl 6) or
                    fourth
            if (outputOffset < output.size) output[outputOffset++] = (combined ushr 16).toByte()
            if (outputOffset < output.size) output[outputOffset++] = (combined ushr 8).toByte()
            if (outputOffset < output.size) output[outputOffset++] = combined.toByte()
            sourceOffset += 4
        }
        require(encode(output) == value) {
            "Framed Base64 is not in canonical form."
        }
        return output
    }

    private fun decodeCharacter(character: Char): Int {
        require(character.code < DECODE.size && DECODE[character.code] >= 0) {
            "Framed Base64 contains an invalid character."
        }
        return DECODE[character.code]
    }

    private val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
    private val DECODE = IntArray(128) { -1 }.also { values ->
        ALPHABET.forEachIndexed { index, character -> values[character.code] = index }
    }
}

private object R2FramedJson {

    fun objectValue(vararg fields: Pair<String, Any>): String = buildString {
        append('{')
        fields.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            appendString(key)
            append(':')
            when (value) {
                is String -> appendString(value)
                is Int -> append(value)
                is Long -> append(value)
                else -> error("Unsupported framed JSON value.")
            }
        }
        append('}')
    }

    private fun StringBuilder.appendString(value: String) {
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
}

internal const val R2_PROJECT_MAXIMUM_FRAME_BYTES = 128 * 1024
// A maximum legal C5 inventory can exceed the ordinary envelope only in the
// metadata-bearing projectInputStart frame; every later stop-and-wait frame
// remains constrained by R2_PROJECT_MAXIMUM_FRAME_BYTES.
internal const val R2_PROJECT_MAXIMUM_START_FRAME_BYTES = 576 * 1024
internal const val R2_PROJECT_FRAME_RAW_CHUNK_BYTES = 64 * 1024
