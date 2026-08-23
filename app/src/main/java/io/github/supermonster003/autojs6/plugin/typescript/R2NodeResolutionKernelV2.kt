package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal enum class R2NodeResolutionV2ConditionMode(
    val contractValue: String,
    internal val legacyKernelMode: R2NodeResolutionMode,
) {
    IMPORT("import", R2NodeResolutionMode.IMPORT),
    REQUIRE("require", R2NodeResolutionMode.REQUIRE),
    TYPES("types", R2NodeResolutionMode.TYPES),
}

internal data class R2NodeResolutionV2Request(
    val containingPath: String,
    val specifier: String,
)

/**
 * Exact ordinary snapshot identity consumed by resolver v2.
 *
 * The complete accepted project-file inventory is fingerprinted, while only TypeScript source and
 * declaration entries become resolution targets. This lets P2-6 derive the authority directly from
 * [R2ProjectInputSnapshot.fileInventory] without rebuilding an unbound list.
 */
internal class R2NodeResolutionV2SnapshotTargetInventory private constructor(
    val profileAuthority: R2CompilationProfileAuthority,
    files: List<R2ProjectFileMetadata>,
    resolutionTargets: List<R2NodeResolutionTarget>,
    val canonicalInput: String,
    val fingerprint: String,
) {

    val files: List<R2ProjectFileMetadata> =
        Collections.unmodifiableList(ArrayList(files))
    val resolutionTargets: List<R2NodeResolutionTarget> =
        Collections.unmodifiableList(ArrayList(resolutionTargets))
    val resolutionTargetPaths: Set<String> = Collections.unmodifiableSet(
        resolutionTargets.mapTo(LinkedHashSet(resolutionTargets.size), R2NodeResolutionTarget::path),
    )

    companion object {

        const val CANONICAL_DOMAIN =
            "autojs6.typescript.node-resolution.snapshot-targets.v2"

        fun create(
            profileAuthority: R2CompilationProfileAuthority,
            files: List<R2ProjectFileMetadata>,
        ): R2NodeResolutionV2SnapshotTargetInventory {
            require(
                profileAuthority ==
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            ) {
                "Resolver v2 snapshot targets require the exact registered Node revision-2 " +
                    "authority."
            }
            val ownedFiles = files.sortedBy(R2ProjectFileMetadata::path)
            val foldedPaths = HashSet<String>(ownedFiles.size)
            var previousPath: String? = null
            ownedFiles.forEach { file ->
                R2ProjectSnapshot.validateProjectPath(file.path, profileAuthority)
                require(file.utf8Bytes >= 0L && file.utf16CodeUnits >= 0) {
                    "Resolver v2 snapshot target has an invalid size: ${file.path}."
                }
                R2Digests.requireLowerSha256(file.sha256, "Resolver v2 snapshot target SHA-256")
                require(previousPath != file.path) {
                    "Resolver v2 snapshot target inventory contains a duplicate path."
                }
                require(foldedPaths.add(file.path.nodeResolutionV2AsciiCaseFold())) {
                    "Resolver v2 snapshot target inventory contains an ASCII case-fold collision."
                }
                previousPath = file.path
            }

            val resolutionTargets = ownedFiles.mapNotNull { file ->
                when {
                    file.path.endsWith(".d.ts") -> R2NodeResolutionTarget(
                        path = file.path,
                        kind = R2NodeResolutionTargetKind.DECLARATION,
                    )
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        file.path,
                        profileAuthority,
                    ) -> R2NodeResolutionTarget(
                        path = file.path,
                        kind = R2NodeResolutionTargetKind.PROJECT_SOURCE,
                    )
                    else -> null
                }
            }
            require(resolutionTargets.isNotEmpty()) {
                "Resolver v2 snapshot target inventory has no TypeScript resolution targets."
            }

            val canonicalInput = buildString {
                append(CANONICAL_DOMAIN)
                append("|profileSchemaRevision=")
                append(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
                append("|profileRevision=")
                append(profileAuthority.profileRevision)
                append("|nodeModuleOutputPolicyFingerprint=")
                append(R2NodeModuleOutputPolicy.fingerprint)
                ownedFiles.forEach { file ->
                    val target = resolutionTargets.firstOrNull { candidate ->
                        candidate.path == file.path
                    }
                    append("|file=")
                    append(file.path.nodeResolutionV2CanonicalPath())
                    append(',')
                    append(file.utf8Bytes)
                    append(',')
                    append(file.utf16CodeUnits)
                    append(',')
                    append(file.sha256)
                    append(',')
                    append(
                        when (target?.kind) {
                            R2NodeResolutionTargetKind.PROJECT_SOURCE -> "project-source"
                            R2NodeResolutionTargetKind.DECLARATION -> "declaration"
                            null -> "non-resolution"
                        },
                    )
                }
            }
            check(canonicalInput.all { character -> character.code in PRINTABLE_ASCII })
            val fingerprint = R2Digests.sha256(
                canonicalInput.toByteArray(StandardCharsets.US_ASCII),
            )
            return R2NodeResolutionV2SnapshotTargetInventory(
                profileAuthority = profileAuthority,
                files = ownedFiles,
                resolutionTargets = resolutionTargets,
                canonicalInput = canonicalInput,
                fingerprint = fingerprint,
            )
        }

        private val PRINTABLE_ASCII = 0x20..0x7e
    }
}

internal class R2NodeResolutionV2Authority(
    val snapshotTargets: R2NodeResolutionV2SnapshotTargetInventory,
    val packageMetadata: R2NodePackageMetadataCatalog? = null,
    val packageFormat: R2NodePackageFormatCatalog? = null,
    val packageTargets: R2NodePackageTargetV2Inventory? = null,
    val profileAuthority: R2CompilationProfileAuthority =
        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
) {

    val resolutionTargets: List<R2NodeResolutionTarget>
    val targetUniverseCanonicalInput: String
    val targetUniverseFingerprint: String
    private val resolutionTargetPaths: Set<String>

    init {
        require(
            profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        ) {
            "Resolver v2 requires the exact registered Node revision-2 profile authority."
        }
        require(snapshotTargets.profileAuthority == profileAuthority) {
            "Resolver v2 snapshot target profile authority is split-brain."
        }
        require((packageMetadata == null) == (packageFormat == null)) {
            "Resolver v2 package metadata and format authorities must be coherently present."
        }
        require(packageTargets == null || packageMetadata != null) {
            "Resolver v2 package targets require package metadata and format authority."
        }
        if (packageMetadata != null) {
            val exactPackageFormat = requireNotNull(packageFormat)
            require(
                packageMetadata.inventoryFingerprint ==
                    exactPackageFormat.packageMetadataInventoryFingerprint,
            ) {
                "Resolver v2 package metadata and format authorities are split-brain."
            }
            require(
                packageMetadata.dependencyRoots == exactPackageFormat.dependencyRoots,
            ) {
                "Resolver v2 dependency-root authorities do not match."
            }
            require(
                packageMetadata.records.map { record -> record.path } ==
                    exactPackageFormat.records.map { record -> record.packageMetadataPath } &&
                    packageMetadata.records.map { record -> record.size } ==
                    exactPackageFormat.records.map { record -> record.packageMetadataSize } &&
                    packageMetadata.records.map { record -> record.sha256 } ==
                    exactPackageFormat.records.map { record -> record.packageMetadataSha256 },
            ) {
                "Resolver v2 package-format records do not exactly cover parsed C5 metadata."
            }
            packageTargets?.let { exactPackageTargets ->
                require(
                    packageMetadata.inventoryFingerprint ==
                        exactPackageTargets.packageMetadataInventoryFingerprint &&
                        packageMetadata.dependencyRoots == exactPackageTargets.dependencyRoots,
                ) {
                    "Resolver v2 package-target authority is split-brain."
                }
                require(
                    exactPackageTargets.authority.profileAuthority == profileAuthority &&
                        exactPackageTargets.authority.policyRevision ==
                        R2NodePackageTargetPolicyV2.POLICY_REVISION &&
                        R2Digests.equal(
                            exactPackageTargets.authority.policyFingerprint,
                            R2NodePackageTargetPolicyV2.fingerprint,
                        ),
                ) {
                    "Resolver v2 package-target authority is not exact."
                }
            }
        }

        val mergedTargets = ArrayList<R2NodeResolutionTarget>(
            snapshotTargets.resolutionTargets.size + (packageTargets?.files?.size ?: 0),
        )
        mergedTargets += snapshotTargets.resolutionTargets
        packageTargets?.let { inventory -> mergedTargets += inventory.toResolutionTargets() }
        mergedTargets.sortBy(R2NodeResolutionTarget::path)
        val foldedPaths = HashSet<String>(mergedTargets.size)
        var previousPath: String? = null
        mergedTargets.forEach { target ->
            require(previousPath != target.path) {
                "Resolver v2 target universe contains an exact cross-inventory collision."
            }
            require(foldedPaths.add(target.path.nodeResolutionV2AsciiCaseFold())) {
                "Resolver v2 target universe contains an ASCII case-fold collision."
            }
            previousPath = target.path
        }
        resolutionTargets = Collections.unmodifiableList(mergedTargets)
        resolutionTargetPaths = Collections.unmodifiableSet(
            mergedTargets.mapTo(LinkedHashSet(mergedTargets.size), R2NodeResolutionTarget::path),
        )
        targetUniverseCanonicalInput = buildString {
            append(TARGET_UNIVERSE_CANONICAL_DOMAIN)
            append("|resolverPolicyRevision=")
            append(R2NodeResolutionKernelV2.POLICY_REVISION)
            append("|packageTargetPolicyRevision=")
            append(R2NodePackageTargetPolicyV2.POLICY_REVISION)
            append("|packageTargetPolicyFingerprint=")
            append(R2NodePackageTargetPolicyV2.fingerprint)
            append("|snapshotTargetInventoryFingerprint=")
            append(snapshotTargets.fingerprint)
            append("|packageTargetInventoryFingerprint=")
            append(packageTargets?.fingerprint ?: "none")
            mergedTargets.forEach { target ->
                append("|target=")
                append(target.path.nodeResolutionV2CanonicalPath())
                append(',')
                append(
                    when (target.kind) {
                        R2NodeResolutionTargetKind.PROJECT_SOURCE -> "project-source"
                        R2NodeResolutionTargetKind.DECLARATION -> "declaration"
                    },
                )
            }
        }
        check(targetUniverseCanonicalInput.all { character -> character.code in PRINTABLE_ASCII })
        targetUniverseFingerprint = R2Digests.sha256(
            targetUniverseCanonicalInput.toByteArray(StandardCharsets.US_ASCII),
        )
    }

    fun containsResolutionTarget(path: String): Boolean = path in resolutionTargetPaths

    private companion object {
        const val TARGET_UNIVERSE_CANONICAL_DOMAIN =
            "autojs6.typescript.node-resolution.target-universe.v2"
        val PRINTABLE_ASCII = 0x20..0x7e
    }
}

internal data class R2NodeResolutionV2Evidence(
    val profileRevision: Int,
    val resolverPolicyRevision: Int,
    val impliedModuleFormat: R2ModuleFormat?,
    val conditionMode: R2NodeResolutionV2ConditionMode?,
    val snapshotTargetInventoryFingerprint: String,
    val packageTargetPolicyRevision: Int,
    val packageTargetPolicyFingerprint: String,
    val packageMetadataInventoryFingerprint: String?,
    val packageFormatFingerprint: String?,
    val packageTargetInventoryFingerprint: String?,
    val targetUniverseFingerprint: String,
)

internal data class R2NodeResolutionV2Outcome(
    val evidence: R2NodeResolutionV2Evidence,
    val result: R2NodeResolutionResult,
)

/**
 * Pure P2-5 resolver authority.
 *
 * This object has no Program, Worker, compiler-host, filesystem, network or default-resolver
 * dependency. Production Worker/Program wiring remains closed until P2-6. P2-6 owns the
 * integration after this policy is independently frozen.
 */
internal object R2NodeResolutionKernelV2 {

    const val POLICY_REVISION = R2NodeModuleOutputPolicy.RESOLVER_POLICY_REVISION
    const val CONDITION_SOURCE = R2NodeModuleOutputPolicy.RESOLVER_CONDITION_SOURCE
    const val TYPE_REFERENCE_CONDITION_SOURCE = "type-reference-hook"
    const val TRUSTED_CUSTOM_CONDITION_COUNT =
        R2NodePackageTargetPolicyV2.TRUSTED_CUSTOM_CONDITION_COUNT

    fun resolve(
        request: R2NodeResolutionV2Request,
        authority: R2NodeResolutionV2Authority,
    ): R2NodeResolutionV2Outcome = resolveWithConditionMode(
        request = request,
        authority = authority,
        fixedConditionMode = null,
    )

    fun resolveTypeReference(
        request: R2NodeResolutionV2Request,
        authority: R2NodeResolutionV2Authority,
    ): R2NodeResolutionV2Outcome = resolveWithConditionMode(
        request = request,
        authority = authority,
        fixedConditionMode = R2NodeResolutionV2ConditionMode.TYPES,
    )

    private fun resolveWithConditionMode(
        request: R2NodeResolutionV2Request,
        authority: R2NodeResolutionV2Authority,
        fixedConditionMode: R2NodeResolutionV2ConditionMode?,
    ): R2NodeResolutionV2Outcome {
        val impliedModuleFormat = try {
            impliedModuleFormat(
                containingPath = request.containingPath,
                packageFormat = authority.packageFormat,
            )
        } catch (_: IllegalArgumentException) {
            return invalidContainingPathOutcome(authority)
        }
        if (!authority.containsResolutionTarget(request.containingPath)) {
            return invalidContainingPathOutcome(authority)
        }
        val conditionMode = fixedConditionMode ?: when (impliedModuleFormat) {
            R2ModuleFormat.ESM -> R2NodeResolutionV2ConditionMode.IMPORT
            R2ModuleFormat.COMMONJS -> R2NodeResolutionV2ConditionMode.REQUIRE
        }
        val result = R2NodeResolutionKernel.resolveVersionTwo(
            request = R2NodeResolutionRequest(
                containingPath = request.containingPath,
                specifier = request.specifier,
                mode = conditionMode.legacyKernelMode,
                conditions = emptyList(),
            ),
            packageMetadata = authority.packageMetadata,
            targets = authority.resolutionTargets,
        )
        return R2NodeResolutionV2Outcome(
            evidence = evidence(
                authority = authority,
                impliedModuleFormat = impliedModuleFormat,
                conditionMode = conditionMode,
            ),
            result = result,
        )
    }

    fun impliedModuleFormat(
        containingPath: String,
        packageFormat: R2NodePackageFormatCatalog?,
    ): R2ModuleFormat {
        requireCanonicalContainingPath(containingPath)
        return when {
            containingPath.endsWith(".d.mts") || containingPath.endsWith(".mts") ->
                R2ModuleFormat.ESM
            containingPath.endsWith(".d.cts") || containingPath.endsWith(".cts") ->
                R2ModuleFormat.COMMONJS
            containingPath.endsWith(".d.ts") || containingPath.endsWith(".ts") ->
                if (
                    packageFormat?.nearestRecord(containingPath)?.typeState ==
                    R2PackageTypeState.MODULE
                ) {
                    R2ModuleFormat.ESM
                } else {
                    R2ModuleFormat.COMMONJS
                }
            else -> throw IllegalArgumentException(
                "Resolver v2 containing source kind is not supported: $containingPath.",
            )
        }
    }

    private fun invalidContainingPathOutcome(
        authority: R2NodeResolutionV2Authority,
    ): R2NodeResolutionV2Outcome = R2NodeResolutionV2Outcome(
        evidence = evidence(authority, impliedModuleFormat = null, conditionMode = null),
        result = R2NodeResolutionResult.Failure(
            code = R2NodeResolutionFailureCode.INVALID_CONTAINING_PATH,
            packageBoundary = null,
            packageMetadataPath = null,
            matchedKey = null,
            matchedConditions = emptyList(),
        ),
    )

    private fun evidence(
        authority: R2NodeResolutionV2Authority,
        impliedModuleFormat: R2ModuleFormat?,
        conditionMode: R2NodeResolutionV2ConditionMode?,
    ): R2NodeResolutionV2Evidence = R2NodeResolutionV2Evidence(
        profileRevision = authority.profileAuthority.profileRevision,
        resolverPolicyRevision = POLICY_REVISION,
        impliedModuleFormat = impliedModuleFormat,
        conditionMode = conditionMode,
        snapshotTargetInventoryFingerprint = authority.snapshotTargets.fingerprint,
        packageTargetPolicyRevision = R2NodePackageTargetPolicyV2.POLICY_REVISION,
        packageTargetPolicyFingerprint = R2NodePackageTargetPolicyV2.fingerprint,
        packageMetadataInventoryFingerprint = authority.packageMetadata?.inventoryFingerprint,
        packageFormatFingerprint = authority.packageFormat?.fingerprint,
        packageTargetInventoryFingerprint = authority.packageTargets?.fingerprint,
        targetUniverseFingerprint = authority.targetUniverseFingerprint,
    )

    private fun requireCanonicalContainingPath(path: String) {
        require(
            R2UnicodePathPolicy.isCanonicalAbsolutePath(
                path = path,
                maximumUtf16Chars = R2NodeModuleOutputPolicy.MAXIMUM_RELATIVE_PATH_CHARS + 1,
            ),
        ) {
            "Resolver v2 containing path is not canonical UTF-8 text."
        }
        val segments = path.drop(1).split('/')
        require(
            segments.size in 2..R2NodeModuleOutputPolicy.MAXIMUM_PATH_DEPTH &&
                segments.first() in ALLOWED_ROOTS &&
                segments.all { segment ->
                segment.isNotEmpty() &&
                        segment != "." &&
                        segment != ".." &&
                        !segment.equals("node_modules", ignoreCase = true)
                },
        ) {
            "Resolver v2 containing path escapes its closed snapshot roots."
        }
    }

    private val ALLOWED_ROOTS = setOf("sources", "packages", "declarations")
}

private fun String.nodeResolutionV2CanonicalPath(): String {
    val bytes = R2Utf8.encode(this, "Resolver v2 canonical path")
    return buildString(bytes.size) {
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            if (
                unsigned in 0x20..0x7e &&
                unsigned != '%'.code &&
                unsigned != '|'.code &&
                unsigned != ','.code
            ) {
                append(unsigned.toChar())
            } else {
                append('%')
                append(NODE_RESOLUTION_V2_HEX[unsigned ushr 4])
                append(NODE_RESOLUTION_V2_HEX[unsigned and 0x0f])
            }
        }
    }
}

private fun String.nodeResolutionV2AsciiCaseFold(): String = buildString(length) {
    this@nodeResolutionV2AsciiCaseFold.forEach { character ->
        append(
            if (character in 'A'..'Z') {
                (character.code + NODE_RESOLUTION_V2_ASCII_CASE_OFFSET).toChar()
            } else {
                character
            },
        )
    }
}

private const val NODE_RESOLUTION_V2_ASCII_CASE_OFFSET = 'a'.code - 'A'.code
private const val NODE_RESOLUTION_V2_HEX = "0123456789ABCDEF"
