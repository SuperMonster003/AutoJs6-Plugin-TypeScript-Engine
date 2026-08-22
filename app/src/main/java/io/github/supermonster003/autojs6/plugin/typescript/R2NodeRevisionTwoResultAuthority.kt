package io.github.supermonster003.autojs6.plugin.typescript

import java.util.Collections

/**
 * Native-only identity for one accepted Node revision-2 Worker result.
 *
 * The Worker result wire deliberately carries no project ID or generated URI claim. Native code
 * derives those values from the exact accepted snapshot, then verifies that every emitted module
 * and Source Map is the canonical output of [R2GeneratedModuleMappingPolicy].
 */
@ConsistentCopyVisibility
internal data class R2NodeRevisionTwoResultAuthority private constructor(
    val acceptedProjectId: String,
    val profileAuthority: R2CompilationProfileAuthority,
    val entryPath: String,
    val rootNames: List<String>,
    val nodeModuleOutputPolicySchemaRevision: Int,
    val nodeModuleOutputPolicyRevision: Int,
    val nodeModuleOutputPolicyFingerprint: String,
    val generatedMappingSchema: String,
    val generatedMappingSchemaRevision: Int,
    val generatedMappingPolicyRevision: Int,
    val resolverPolicyRevision: Int,
    val snapshotTargetInventoryFingerprint: String,
    val packageMetadataInventoryFingerprint: String?,
    val packageFormatCatalogFingerprint: String?,
    val packageTargetInventoryFingerprint: String?,
    val targetUniverseFingerprint: String,
    val generatedModuleMappings: List<R2GeneratedModuleMapping>,
) {

    init {
        R2ProjectSnapshot.requireAcceptedProjectId(acceptedProjectId)
        require(
            profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        ) {
            "Node revision-2 result authority requires the exact registered profile."
        }
        require(entryPath in rootNames) {
            "Node revision-2 result entry must be one of the exact root names."
        }
        require(rootNames.isNotEmpty() && rootNames.distinct().size == rootNames.size) {
            "Node revision-2 result root authority is empty or ambiguous."
        }
        require(
            nodeModuleOutputPolicySchemaRevision == R2NodeModuleOutputPolicy.SCHEMA_REVISION &&
                nodeModuleOutputPolicyRevision == R2NodeModuleOutputPolicy.POLICY_REVISION &&
                R2Digests.equal(
                    nodeModuleOutputPolicyFingerprint,
                    R2NodeModuleOutputPolicy.fingerprint,
                ),
        ) {
            "Node revision-2 result module/output policy authority is not exact."
        }
        require(
            generatedMappingSchema == R2GeneratedModuleMappingPolicy.SCHEMA &&
                generatedMappingSchemaRevision == R2GeneratedModuleMappingPolicy.SCHEMA_REVISION &&
                generatedMappingPolicyRevision == R2GeneratedModuleMappingPolicy.POLICY_REVISION,
        ) {
            "Node revision-2 result generated-mapping authority is not exact."
        }
        require(resolverPolicyRevision == R2NodeResolutionKernelV2.POLICY_REVISION) {
            "Node revision-2 result resolver authority is not exact."
        }
        R2Digests.requireLowerSha256(
            snapshotTargetInventoryFingerprint,
            "Node revision-2 result snapshot target fingerprint",
        )
        packageMetadataInventoryFingerprint?.let { fingerprint ->
            R2Digests.requireLowerSha256(
                fingerprint,
                "Node revision-2 result package metadata fingerprint",
            )
        }
        packageFormatCatalogFingerprint?.let { fingerprint ->
            R2Digests.requireLowerSha256(
                fingerprint,
                "Node revision-2 result package format fingerprint",
            )
        }
        packageTargetInventoryFingerprint?.let { fingerprint ->
            R2Digests.requireLowerSha256(
                fingerprint,
                "Node revision-2 result package-target fingerprint",
            )
        }
        require(
            (packageMetadataInventoryFingerprint == null) ==
                (packageFormatCatalogFingerprint == null) &&
                (packageTargetInventoryFingerprint == null ||
                    packageMetadataInventoryFingerprint != null),
        ) {
            "Node revision-2 result package authorities are split-brain."
        }
        R2Digests.requireLowerSha256(
            targetUniverseFingerprint,
            "Node revision-2 result target-universe fingerprint",
        )
        require(
            generatedModuleMappings ==
                generatedModuleMappings.sortedBy(R2GeneratedModuleMapping::sourcePath) &&
                generatedModuleMappings.map(R2GeneratedModuleMapping::sourcePath).distinct().size ==
                generatedModuleMappings.size,
        ) {
            "Node revision-2 generated mappings are not a canonical inventory."
        }
    }

    companion object {

        fun derive(
            snapshot: R2ProjectInputSnapshot,
            outputs: List<R2ProjectOutput>,
        ): R2NodeRevisionTwoResultAuthority {
            val exactProfile = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
            require(snapshot.profileAuthority == exactProfile) {
                "Node revision-2 result derivation requires the exact snapshot profile authority."
            }
            val projectId = requireNotNull(snapshot.acceptedProjectId) {
                "Node revision-2 result derivation requires an accepted project ID."
            }
            R2ProjectSnapshot.requireAcceptedProjectId(projectId)

            val snapshotTargets = R2NodeResolutionV2SnapshotTargetInventory.create(
                profileAuthority = exactProfile,
                files = snapshot.fileInventory,
            )
            val resolverAuthority = R2NodeResolutionV2Authority(
                snapshotTargets = snapshotTargets,
                packageMetadata = snapshot.nodePackageMetadataCatalog,
                packageFormat = snapshot.nodePackageFormatCatalog,
                packageTargets = snapshot.nodePackageTargetV2Inventory,
                profileAuthority = exactProfile,
            )
            val modules = outputs.filterNot(R2ProjectOutput::isSourceMap)
            val sourceMaps = outputs.filter(R2ProjectOutput::isSourceMap)
            val requests = modules.map { module ->
                require(module.sourcePaths.size == 1) {
                    "A Node revision-2 module must identify exactly one source."
                }
                val sourcePath = module.sourcePaths.single()
                require(
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        sourcePath,
                        exactProfile,
                    ) && resolverAuthority.containsResolutionTarget(sourcePath),
                ) {
                    "Node revision-2 output source is not an exact executable snapshot target."
                }
                R2GeneratedModuleMappingRequest(
                    projectId = projectId,
                    profileAuthority = exactProfile,
                    sourcePath = sourcePath,
                    impliedModuleFormat = R2NodeResolutionKernelV2.impliedModuleFormat(
                        containingPath = sourcePath,
                        packageFormat = resolverAuthority.packageFormat,
                    ),
                    packageFormatAuthority = sourcePath.packageFormatAuthority(
                        resolverAuthority.packageFormat,
                    ),
                )
            }
            val mappings = R2GeneratedModuleMappingPolicy.mapInventory(requests)
            requireExactOutputInventory(
                outputs = outputs,
                modules = modules,
                sourceMaps = sourceMaps,
                mappings = mappings,
            )
            return R2NodeRevisionTwoResultAuthority(
                acceptedProjectId = projectId,
                profileAuthority = exactProfile,
                entryPath = snapshot.entryPath,
                rootNames = Collections.unmodifiableList(ArrayList(snapshot.rootNames)),
                nodeModuleOutputPolicySchemaRevision = R2NodeModuleOutputPolicy.SCHEMA_REVISION,
                nodeModuleOutputPolicyRevision = R2NodeModuleOutputPolicy.POLICY_REVISION,
                nodeModuleOutputPolicyFingerprint = R2NodeModuleOutputPolicy.fingerprint,
                generatedMappingSchema = R2GeneratedModuleMappingPolicy.SCHEMA,
                generatedMappingSchemaRevision = R2GeneratedModuleMappingPolicy.SCHEMA_REVISION,
                generatedMappingPolicyRevision = R2GeneratedModuleMappingPolicy.POLICY_REVISION,
                resolverPolicyRevision = R2NodeResolutionKernelV2.POLICY_REVISION,
                snapshotTargetInventoryFingerprint = snapshotTargets.fingerprint,
                packageMetadataInventoryFingerprint =
                    resolverAuthority.packageMetadata?.inventoryFingerprint,
                packageFormatCatalogFingerprint = resolverAuthority.packageFormat?.fingerprint,
                packageTargetInventoryFingerprint = resolverAuthority.packageTargets?.fingerprint,
                targetUniverseFingerprint = resolverAuthority.targetUniverseFingerprint,
                generatedModuleMappings = mappings,
            )
        }

        fun requireExact(
            snapshot: R2ProjectInputSnapshot,
            outputs: List<R2ProjectOutput>,
            claimed: R2NodeRevisionTwoResultAuthority,
        ): R2NodeRevisionTwoResultAuthority {
            val expected = derive(snapshot, outputs)
            require(expected == claimed) {
                "Node revision-2 result authority changed across exact snapshot binding."
            }
            return expected
        }

        private fun requireExactOutputInventory(
            outputs: List<R2ProjectOutput>,
            modules: List<R2ProjectOutput>,
            sourceMaps: List<R2ProjectOutput>,
            mappings: List<R2GeneratedModuleMapping>,
        ) {
            require(modules.size == mappings.size && sourceMaps.size == mappings.size) {
                "Node revision-2 output does not contain one module and Source Map per mapping."
            }
            val mappingsBySource = mappings.associateBy(R2GeneratedModuleMapping::sourcePath)
            modules.forEach { module ->
                val sourcePath = module.sourcePaths.single()
                val mapping = requireNotNull(mappingsBySource[sourcePath]) {
                    "Node revision-2 output source has no canonical generated mapping."
                }
                require(module.path == mapping.generatedPath) {
                    "Node revision-2 module path does not match its generated mapping."
                }
                val sourceMap = sourceMaps.singleOrNull { candidate ->
                    candidate.path == mapping.sourceMapPath
                }
                require(sourceMap != null && sourceMap.sourcePaths == module.sourcePaths) {
                    "Node revision-2 Source Map does not exactly pair with its module."
                }
            }
            val expectedPaths = mappings.flatMap { mapping ->
                listOf(mapping.generatedPath, mapping.sourceMapPath)
            }.sorted()
            val actualPaths = outputs.map(R2ProjectOutput::path)
            require(
                actualPaths.distinct().size == actualPaths.size &&
                    actualPaths.map { path -> path.nodeRevisionTwoAsciiCaseFold() }
                        .distinct().size ==
                    actualPaths.size &&
                    actualPaths.sorted() == expectedPaths,
            ) {
                "Node revision-2 output inventory is not the exact generated mapping inventory."
            }
        }

        private fun String.packageFormatAuthority(
            catalog: R2NodePackageFormatCatalog?,
        ): R2GeneratedPackageFormatAuthority? = when {
            endsWith(".mts") || endsWith(".cts") -> null
            endsWith(".ts") -> {
                val record = catalog?.nearestRecord(this)
                if (record == null) {
                    R2GeneratedPackageFormatAuthority(
                        typeState = R2PackageTypeState.NO_BOUNDARY,
                        packageMetadataInventoryFingerprint = null,
                        boundaryPath = null,
                    )
                } else {
                    R2GeneratedPackageFormatAuthority(
                        typeState = record.typeState,
                        packageMetadataInventoryFingerprint =
                            requireNotNull(catalog).packageMetadataInventoryFingerprint,
                        boundaryPath = record.packageBoundaryPath,
                    )
                }
            }
            else -> error("Node revision-2 generated mapping source kind is not supported.")
        }

        private fun String.nodeRevisionTwoAsciiCaseFold(): String = buildString(length) {
            this@nodeRevisionTwoAsciiCaseFold.forEach { character ->
                append(
                    if (character in 'A'..'Z') {
                        (character.code + ASCII_CASE_OFFSET).toChar()
                    } else {
                        character
                    },
                )
            }
        }

        private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
    }
}
