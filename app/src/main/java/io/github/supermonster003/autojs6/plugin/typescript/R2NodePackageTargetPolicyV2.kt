package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal enum class R2NodePackageTargetKind(
    val contractValue: String,
) {
    EXECUTABLE_SOURCE("source"),
    DECLARATION("declaration"),
}

internal data class R2NodePackageTargetV2Authority(
    val fingerprint: String,
    val packageMetadataInventoryFingerprint: String,
    val profileAuthority: R2CompilationProfileAuthority =
        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
    val policyRevision: Int = R2NodePackageTargetPolicyV2.POLICY_REVISION,
    val policyFingerprint: String = R2NodePackageTargetPolicyV2.fingerprint,
) {

    init {
        if (
            profileAuthority !=
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        ) {
            nodeTargetV2Failure(
                R2PackageTargetPolicyCode.INVENTORY_INVALID,
                "Node package-target v2 requires the exact registered Node revision-2 authority.",
            )
        }
        requireNodeTargetV2Sha256(
            fingerprint,
            "Node package-target v2 inventory fingerprint",
            R2PackageTargetPolicyCode.INVENTORY_INVALID,
        )
        requireNodeTargetV2Sha256(
            packageMetadataInventoryFingerprint,
            "Node package-target v2 C5 inventory fingerprint",
            R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID,
        )
        if (policyRevision != R2NodePackageTargetPolicyV2.POLICY_REVISION) {
            nodeTargetV2Failure(
                R2PackageTargetPolicyCode.INVENTORY_INVALID,
                "Node package-target v2 policy revision is not supported.",
            )
        }
        requireNodeTargetV2Sha256(
            policyFingerprint,
            "Node package-target v2 policy fingerprint",
            R2PackageTargetPolicyCode.INVENTORY_INVALID,
        )
        if (!R2Digests.equal(policyFingerprint, R2NodePackageTargetPolicyV2.fingerprint)) {
            nodeTargetV2Failure(
                R2PackageTargetPolicyCode.INVENTORY_INVALID,
                "Node package-target v2 policy fingerprint is not supported.",
            )
        }
    }
}

internal data class R2NodePackageTargetV2FileDescriptor(
    val path: String,
    val size: Long,
    val sha256: String,
) {

    val kind: R2NodePackageTargetKind = R2NodePackageTargetPolicyV2.classify(path)

    init {
        if (size !in 0L..R2NodePackageTargetPolicyV2.MAXIMUM_FILE_BYTES) {
            nodeTargetV2Failure(
                R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                "Node package-target v2 exceeds the single-file byte limit: $path.",
            )
        }
        requireNodeTargetV2Sha256(
            sha256,
            "Node package-target v2 SHA-256",
            R2PackageTargetPolicyCode.INVENTORY_INVALID,
        )
    }
}

internal class R2NodePackageTargetV2Inventory private constructor(
    dependencyRoots: List<String>,
    val packageMetadataInventoryFingerprint: String,
    files: List<R2NodePackageTargetV2FileDescriptor>,
    val canonicalInput: String,
    val fingerprint: String,
    val totalBytes: Long,
    val totalPathChars: Int,
) {

    val dependencyRoots: List<String> =
        Collections.unmodifiableList(ArrayList(dependencyRoots))
    val files: List<R2NodePackageTargetV2FileDescriptor> =
        Collections.unmodifiableList(ArrayList(files))
    val filePaths: Set<String> = Collections.unmodifiableSet(
        files.mapTo(LinkedHashSet(files.size), R2NodePackageTargetV2FileDescriptor::path),
    )
    val authority: R2NodePackageTargetV2Authority = R2NodePackageTargetV2Authority(
        fingerprint = fingerprint,
        packageMetadataInventoryFingerprint = packageMetadataInventoryFingerprint,
    )

    fun toResolutionTargets(): List<R2NodeResolutionTarget> =
        Collections.unmodifiableList(
            files.map { file ->
                R2NodeResolutionTarget(
                    path = "/${file.path}",
                    kind = when (file.kind) {
                        R2NodePackageTargetKind.EXECUTABLE_SOURCE ->
                            R2NodeResolutionTargetKind.PROJECT_SOURCE
                        R2NodePackageTargetKind.DECLARATION ->
                            R2NodeResolutionTargetKind.DECLARATION
                    },
                )
            },
        )

    companion object {

        fun create(
            packageMetadataInventory: R2PackageMetadataInventory,
            files: List<R2NodePackageTargetV2FileDescriptor>,
            boundPackageMetadataInventoryFingerprint: String =
                packageMetadataInventory.fingerprint,
        ): R2NodePackageTargetV2Inventory {
            requireNodeTargetV2Sha256(
                boundPackageMetadataInventoryFingerprint,
                "Bound Node package-target v2 C5 inventory fingerprint",
                R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID,
            )
            if (
                !R2Digests.equal(
                    boundPackageMetadataInventoryFingerprint,
                    packageMetadataInventory.fingerprint,
                )
            ) {
                nodeTargetV2Failure(
                    R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID,
                    "Node package-target v2 inventory is bound to different package metadata.",
                )
            }

            val ownedFiles = ArrayList(files)
            requireFiles(packageMetadataInventory, ownedFiles)

            var totalBytes = 0L
            var totalPathChars = 0
            ownedFiles.forEach { file ->
                if (
                    totalBytes > R2NodePackageTargetPolicyV2.MAXIMUM_TOTAL_BYTES - file.size
                ) {
                    nodeTargetV2Failure(
                        R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                        "Node package-target v2 inventory exceeds the aggregate byte limit.",
                    )
                }
                totalBytes += file.size
                if (
                    totalPathChars >
                    R2NodePackageTargetPolicyV2.MAXIMUM_AGGREGATE_PATH_CHARS -
                    file.path.length
                ) {
                    nodeTargetV2Failure(
                        R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                        "Node package-target v2 inventory exceeds the aggregate " +
                            "path-character limit.",
                    )
                }
                totalPathChars += file.path.length
            }

            val canonicalInput = buildString {
                append(R2NodePackageTargetPolicyV2.INVENTORY_CANONICAL_DOMAIN)
                append("|packageMetadataInventoryFingerprint=")
                append(packageMetadataInventory.fingerprint)
                ownedFiles.forEach { file ->
                    append("|file=")
                    append(file.path)
                    append(',')
                    append(file.kind.contractValue)
                    append(',')
                    append(file.size)
                    append(',')
                    append(file.sha256)
                }
            }
            check(canonicalInput.all { character -> character.code in PRINTABLE_ASCII }) {
                "Node package-target v2 inventory canonical input is not printable ASCII."
            }
            val fingerprint = R2Digests.sha256(
                canonicalInput.toByteArray(StandardCharsets.US_ASCII),
            )
            return R2NodePackageTargetV2Inventory(
                dependencyRoots = packageMetadataInventory.dependencyRoots,
                packageMetadataInventoryFingerprint = packageMetadataInventory.fingerprint,
                files = ownedFiles,
                canonicalInput = canonicalInput,
                fingerprint = fingerprint,
                totalBytes = totalBytes,
                totalPathChars = totalPathChars,
            )
        }

        private fun requireFiles(
            packageMetadataInventory: R2PackageMetadataInventory,
            files: List<R2NodePackageTargetV2FileDescriptor>,
        ) {
            if (files.size > R2NodePackageTargetPolicyV2.MAXIMUM_FILES) {
                nodeTargetV2Failure(
                    R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                    "Node package-target v2 file count exceeds its limit.",
                )
            }
            var previous: String? = null
            val foldedPaths = HashSet<String>(files.size)
            files.forEach { file ->
                previous?.let { prior ->
                    if (prior >= file.path) {
                        nodeTargetV2Failure(
                            R2PackageTargetPolicyCode.INVENTORY_INVALID,
                            "Node package-target v2 files must be strictly sorted and unique.",
                        )
                    }
                }
                previous = file.path
                if (!foldedPaths.add(file.path.nodeTargetV2AsciiCaseFold())) {
                    nodeTargetV2Failure(
                        R2PackageTargetPolicyCode.CASE_COLLISION,
                        "Node package-target v2 inventory contains an ASCII case-fold collision.",
                    )
                }
                val owningRoots = packageMetadataInventory.dependencyRoots.filter { root ->
                    file.path.startsWith("$root/")
                }
                if (owningRoots.size != 1) {
                    nodeTargetV2Failure(
                        R2PackageTargetPolicyCode.ROOT_NOT_ALLOWED,
                        "Node package-target v2 is outside the exact C5 dependency-root " +
                            "authority: ${file.path}.",
                    )
                }
            }
        }

        private val PRINTABLE_ASCII = 0x20..0x7e
    }
}

/** Pure P2-5 authority. It is intentionally not wired into archive, snapshot, frame or Worker. */
internal object R2NodePackageTargetPolicyV2 {

    const val SCHEMA = "autojs6.typescript.node-package-target-policy"
    const val SCHEMA_REVISION = 1
    const val POLICY_REVISION = 2
    const val INVENTORY_CANONICAL_DOMAIN =
        "autojs6.typescript.node-package-target.inventory.v2"
    const val DEPENDENCY_ROOT_PREFIX = R2PackageTargetPolicy.DEPENDENCY_ROOT_PREFIX
    const val MAXIMUM_FILES = R2PackageTargetPolicy.MAXIMUM_FILES
    const val MAXIMUM_PATH_CHARS = R2PackageTargetPolicy.MAXIMUM_PATH_CHARS
    const val MAXIMUM_AGGREGATE_PATH_CHARS =
        R2PackageTargetPolicy.MAXIMUM_AGGREGATE_PATH_CHARS
    const val MAXIMUM_FILE_BYTES = R2PackageTargetPolicy.MAXIMUM_FILE_BYTES
    const val MAXIMUM_TOTAL_BYTES = R2PackageTargetPolicy.MAXIMUM_TOTAL_BYTES
    const val METADATA_BINDING_MODE = R2PackageTargetPolicy.METADATA_BINDING_MODE
    const val SHARED_AGGREGATE_BUDGET_MODE =
        R2PackageTargetPolicy.SHARED_AGGREGATE_BUDGET_MODE
    const val SAFE_RELATIVE_PATH_GRAMMAR = R2PackageTargetPolicy.SAFE_RELATIVE_PATH_GRAMMAR
    const val SOURCE_KIND_MODE = "declaration-first-exact-lowercase"
    const val TRUSTED_CUSTOM_CONDITION_COUNT = 0

    val allowedExecutableSuffixes: List<String> =
        Collections.unmodifiableList(listOf(".ts", ".mts", ".cts"))
    val allowedDeclarationSuffixes: List<String> =
        Collections.unmodifiableList(listOf(".d.ts", ".d.mts", ".d.cts"))

    val canonicalInput: String = buildString {
        append("schema=")
        append(SCHEMA)
        append("|schemaRevision=")
        append(SCHEMA_REVISION)
        append("|policyRevision=")
        append(POLICY_REVISION)
        append("|profileSchemaRevision=")
        append(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
        append("|profileRevision=")
        append(R2NodeModuleOutputPolicy.NODE_PROFILE_REVISION)
        append("|nodeModuleOutputPolicySchemaRevision=")
        append(R2NodeModuleOutputPolicy.SCHEMA_REVISION)
        append("|nodeModuleOutputPolicyRevision=")
        append(R2NodeModuleOutputPolicy.POLICY_REVISION)
        append("|nodeModuleOutputPolicyFingerprint=")
        append(R2NodeModuleOutputPolicy.fingerprint)
        append("|resolverPolicyRevision=")
        append(R2NodeModuleOutputPolicy.RESOLVER_POLICY_REVISION)
        append("|resolverConditionSource=")
        append(R2NodeModuleOutputPolicy.RESOLVER_CONDITION_SOURCE)
        append("|resolverTrustedCustomConditionCount=")
        append(TRUSTED_CUSTOM_CONDITION_COUNT)
        append("|packageMetadataPolicyFingerprint=")
        append(R2PackageMetadataPolicy.fingerprint)
        append("|inventoryCanonicalDomain=")
        append(INVENTORY_CANONICAL_DOMAIN)
        append("|dependencyRootPrefix=")
        append(DEPENDENCY_ROOT_PREFIX)
        append("|maximumFiles=")
        append(MAXIMUM_FILES)
        append("|maximumPathChars=")
        append(MAXIMUM_PATH_CHARS)
        append("|maximumAggregatePathChars=")
        append(MAXIMUM_AGGREGATE_PATH_CHARS)
        append("|maximumFileBytes=")
        append(MAXIMUM_FILE_BYTES)
        append("|maximumTotalBytes=")
        append(MAXIMUM_TOTAL_BYTES)
        append("|metadataBindingMode=")
        append(METADATA_BINDING_MODE)
        append("|sharedAggregateBudgetMode=")
        append(SHARED_AGGREGATE_BUDGET_MODE)
        append("|safeRelativePathGrammar=")
        append(SAFE_RELATIVE_PATH_GRAMMAR)
        append("|sourceKindMode=")
        append(SOURCE_KIND_MODE)
        append("|executableSuffixes=")
        append(allowedExecutableSuffixes.joinToString(","))
        append("|declarationSuffixes=")
        append(allowedDeclarationSuffixes.joinToString(","))
        append("|v1Compatibility=")
        append(R2NodeModuleOutputPolicy.RESOLVER_V1_COMPATIBILITY)
    }

    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        check(canonicalInput.all { character -> character.code in 0x20..0x7e })
        check(allowedExecutableSuffixes.distinct().size == 3)
        check(allowedDeclarationSuffixes.distinct().size == 3)
        R2Digests.requireLowerSha256(fingerprint, "Node package-target v2 policy fingerprint")
    }

    fun classify(path: String): R2NodePackageTargetKind {
        requireSafePath(path)
        return when {
            allowedDeclarationSuffixes.any(path::endsWith) ->
                R2NodePackageTargetKind.DECLARATION
            allowedExecutableSuffixes.any(path::endsWith) ->
                R2NodePackageTargetKind.EXECUTABLE_SOURCE
            else -> throw R2PackageTargetPolicyException(
                R2PackageTargetPolicyCode.SOURCE_KIND_UNSUPPORTED,
                "Node package-target v2 source kind is not supported: $path.",
            )
        }
    }

    private fun requireSafePath(path: String) {
        if (
            path.isEmpty() ||
            path.length > MAXIMUM_PATH_CHARS ||
            !path.startsWith(DEPENDENCY_ROOT_PREFIX) ||
            path.startsWith('/') ||
            '\\' in path ||
            '|' in path ||
            ',' in path ||
            path.any { character -> character.code !in PRINTABLE_ASCII } ||
            !SAFE_RELATIVE_PATH_PATTERN.matches(path)
        ) {
            throw R2PackageTargetPolicyException(
                R2PackageTargetPolicyCode.PATH_INVALID,
                "Node package-target v2 path is not a safe printable-ASCII relative path: $path.",
            )
        }
        val segments = path.split('/')
        if (
            segments.any { segment -> segment.isEmpty() || segment == "." || segment == ".." } ||
            segments.any { segment ->
                segment.nodeTargetV2AsciiCaseFold() == NODE_MODULES_SEGMENT
            } ||
            segments.last().nodeTargetV2AsciiCaseFold() == PACKAGE_METADATA_FILE_NAME
        ) {
            throw R2PackageTargetPolicyException(
                R2PackageTargetPolicyCode.PATH_INVALID,
                "Node package-target v2 path uses a reserved or non-canonical segment: $path.",
            )
        }
    }

    private const val PACKAGE_METADATA_FILE_NAME = "package.json"
    private const val NODE_MODULES_SEGMENT = "node_modules"
    private val SAFE_RELATIVE_PATH_PATTERN = Regex(SAFE_RELATIVE_PATH_GRAMMAR)
    private val PRINTABLE_ASCII = 0x20..0x7e
}

private fun requireNodeTargetV2Sha256(
    value: String,
    label: String,
    code: R2PackageTargetPolicyCode,
) {
    try {
        R2Digests.requireLowerSha256(value, label)
    } catch (error: IllegalArgumentException) {
        nodeTargetV2Failure(code, error.message.orEmpty(), error)
    }
}

private fun nodeTargetV2Failure(
    code: R2PackageTargetPolicyCode,
    message: String,
    cause: Throwable? = null,
): Nothing = throw R2PackageTargetPolicyException(code, message, cause)

private fun String.nodeTargetV2AsciiCaseFold(): String = buildString(length) {
    this@nodeTargetV2AsciiCaseFold.forEach { character ->
        append(
            if (character in 'A'..'Z') {
                (character.code + NODE_TARGET_V2_ASCII_CASE_OFFSET).toChar()
            } else {
                character
            },
        )
    }
}

private const val NODE_TARGET_V2_ASCII_CASE_OFFSET = 'a'.code - 'A'.code
