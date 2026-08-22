package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal enum class R2PackageTargetPolicyCode(
    val contractValue: String,
) {
    PATH_INVALID("PACKAGE_TARGET_PATH_INVALID"),
    ROOT_NOT_ALLOWED("PACKAGE_TARGET_ROOT_NOT_ALLOWED"),
    SOURCE_KIND_UNSUPPORTED("PACKAGE_TARGET_SOURCE_KIND_UNSUPPORTED"),
    INVENTORY_INVALID("PACKAGE_TARGET_INVENTORY_INVALID"),
    CASE_COLLISION("PACKAGE_TARGET_CASE_COLLISION"),
    QUOTA_EXCEEDED("PACKAGE_TARGET_QUOTA_EXCEEDED"),
    METADATA_IDENTITY_INVALID("PACKAGE_TARGET_METADATA_IDENTITY_INVALID"),
}

internal class R2PackageTargetPolicyException(
    val policyCode: R2PackageTargetPolicyCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal data class R2PackageTargetAuthority(
    val fingerprint: String,
    val packageMetadataInventoryFingerprint: String,
    val policyRevision: Int = R2PackageTargetPolicy.POLICY_REVISION,
    val policyFingerprint: String = R2PackageTargetPolicy.fingerprint,
) {

    init {
        requireTargetSha256(
            fingerprint,
            "Package target inventory fingerprint",
            R2PackageTargetPolicyCode.INVENTORY_INVALID,
        )
        requireTargetSha256(
            packageMetadataInventoryFingerprint,
            "Bound package metadata inventory fingerprint",
            R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID,
        )
        if (policyRevision != R2PackageTargetPolicy.POLICY_REVISION) {
            packageTargetPolicyFailure(
                R2PackageTargetPolicyCode.INVENTORY_INVALID,
                "Package target policy revision is not supported.",
            )
        }
        requireTargetSha256(
            policyFingerprint,
            "Package target policy fingerprint",
            R2PackageTargetPolicyCode.INVENTORY_INVALID,
        )
        if (!R2Digests.equal(policyFingerprint, R2PackageTargetPolicy.fingerprint)) {
            packageTargetPolicyFailure(
                R2PackageTargetPolicyCode.INVENTORY_INVALID,
                "Package target policy fingerprint is not supported.",
            )
        }
    }
}

internal data class R2PackageTargetFileDescriptor(
    val path: String,
    val size: Long,
    val sha256: String,
) {

    init {
        R2PackageTargetPolicy.requirePackageTargetPath(path)
        if (size !in 0L..R2PackageTargetPolicy.MAXIMUM_FILE_BYTES) {
            packageTargetPolicyFailure(
                R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                "Package target exceeds the single-file byte limit: $path.",
            )
        }
        requireTargetSha256(
            sha256,
            "Package target SHA-256",
            R2PackageTargetPolicyCode.INVENTORY_INVALID,
        )
    }
}

internal class R2PackageTargetInventory private constructor(
    val packageMetadataInventoryFingerprint: String,
    files: List<R2PackageTargetFileDescriptor>,
    val canonicalInput: String,
    val fingerprint: String,
    val totalBytes: Long,
    val totalPathChars: Int,
) {

    val files: List<R2PackageTargetFileDescriptor> =
        Collections.unmodifiableList(ArrayList(files))
    val filePaths: Set<String> = Collections.unmodifiableSet(
        files.mapTo(LinkedHashSet(files.size), R2PackageTargetFileDescriptor::path),
    )
    val authority: R2PackageTargetAuthority = R2PackageTargetAuthority(
        fingerprint = fingerprint,
        packageMetadataInventoryFingerprint = packageMetadataInventoryFingerprint,
    )

    companion object {

        fun create(
            packageMetadataInventory: R2PackageMetadataInventory,
            files: List<R2PackageTargetFileDescriptor>,
            boundPackageMetadataInventoryFingerprint: String =
                packageMetadataInventory.fingerprint,
        ): R2PackageTargetInventory {
            requireTargetSha256(
                boundPackageMetadataInventoryFingerprint,
                "Bound package metadata inventory fingerprint",
                R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID,
            )
            if (
                !R2Digests.equal(
                    boundPackageMetadataInventoryFingerprint,
                    packageMetadataInventory.fingerprint,
                )
            ) {
                packageTargetPolicyFailure(
                    R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID,
                    "Package target inventory is bound to different package metadata.",
                )
            }

            val ownedFiles = ArrayList(files)
            requireFiles(packageMetadataInventory, ownedFiles)

            var totalBytes = 0L
            var totalPathChars = 0
            ownedFiles.forEach { file ->
                if (totalBytes > R2PackageTargetPolicy.MAXIMUM_TOTAL_BYTES - file.size) {
                    packageTargetPolicyFailure(
                        R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                        "Package target inventory exceeds the aggregate byte limit.",
                    )
                }
                totalBytes += file.size
                if (
                    totalPathChars >
                    R2PackageTargetPolicy.MAXIMUM_AGGREGATE_PATH_CHARS - file.path.length
                ) {
                    packageTargetPolicyFailure(
                        R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                        "Package target inventory exceeds the aggregate path-character limit.",
                    )
                }
                totalPathChars += file.path.length
            }

            val canonicalInput = buildString {
                append(R2PackageTargetPolicy.INVENTORY_CANONICAL_DOMAIN)
                append("|packageMetadataInventoryFingerprint=")
                append(packageMetadataInventory.fingerprint)
                ownedFiles.forEach { file ->
                    append("|file=")
                    append(file.path)
                    append(',')
                    append(file.size)
                    append(',')
                    append(file.sha256)
                }
            }
            check(canonicalInput.all { character -> character.code in PRINTABLE_ASCII }) {
                "Package target inventory canonical input is not printable ASCII."
            }
            val fingerprint = R2Digests.sha256(
                canonicalInput.toByteArray(StandardCharsets.US_ASCII),
            )
            return R2PackageTargetInventory(
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
            files: List<R2PackageTargetFileDescriptor>,
        ) {
            if (files.size > R2PackageTargetPolicy.MAXIMUM_FILES) {
                packageTargetPolicyFailure(
                    R2PackageTargetPolicyCode.QUOTA_EXCEEDED,
                    "Package target file count exceeds its limit.",
                )
            }
            var previous: String? = null
            val foldedPaths = HashSet<String>(files.size)
            files.forEach { file ->
                previous?.let { prior ->
                    if (prior >= file.path) {
                        packageTargetPolicyFailure(
                            R2PackageTargetPolicyCode.INVENTORY_INVALID,
                            "Package target files must be strictly sorted and unique.",
                        )
                    }
                }
                previous = file.path
                if (!foldedPaths.add(file.path.asciiCaseFold())) {
                    packageTargetPolicyFailure(
                        R2PackageTargetPolicyCode.CASE_COLLISION,
                        "Package target inventory contains an ASCII case-fold collision.",
                    )
                }
                val owningRoots = packageMetadataInventory.dependencyRoots.filter { root ->
                    file.path.startsWith("$root/")
                }
                if (owningRoots.size != 1) {
                    packageTargetPolicyFailure(
                        R2PackageTargetPolicyCode.ROOT_NOT_ALLOWED,
                        "Package target is outside the exact C5 dependency-root authority: " +
                            "${file.path}.",
                    )
                }
            }
        }

        private val PRINTABLE_ASCII = 0x20..0x7e
    }
}

/** R2-M2-C6-B authority for bounded, inert package target payloads. */
internal object R2PackageTargetPolicy {

    const val SCHEMA = "autojs6.typescript.package-target-policy"
    const val SCHEMA_REVISION = 1
    const val POLICY_REVISION = 1
    const val DEPENDENCY_ROOT_PREFIX = "packages/"
    const val MAXIMUM_FILES = 256
    const val MAXIMUM_PATH_CHARS = TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES
    const val MAXIMUM_AGGREGATE_PATH_CHARS = 32 * 1024
    const val MAXIMUM_FILE_BYTES = 8L * 1024L * 1024L
    const val MAXIMUM_TOTAL_BYTES = 8L * 1024L * 1024L
    const val INVENTORY_CANONICAL_DOMAIN = "autojs6.typescript.package-target.inventory.v1"
    const val METADATA_BINDING_MODE = "exact-c5-inventory-fingerprint"
    const val SHARED_AGGREGATE_BUDGET_MODE = "required-non-additive"
    const val SAFE_RELATIVE_PATH_GRAMMAR = "^[A-Za-z0-9@._/+~-]+$"

    val allowedSourceSuffixes: Set<String> = Collections.unmodifiableSet(
        linkedSetOf(".d.ts", ".ts"),
    )

    val canonicalInput: String = buildString {
        append("autojs6.typescript.package-target-policy.v1")
        append("|schemaRevision=")
        append(SCHEMA_REVISION)
        append("|policyRevision=")
        append(POLICY_REVISION)
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
        append("|allowedSourceSuffixes=")
        append(allowedSourceSuffixes.sorted().joinToString(","))
    }

    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        check(canonicalInput.all { character -> character.code in 0x20..0x7e })
        R2Digests.requireLowerSha256(fingerprint, "Package target policy fingerprint")
    }

    fun isExplicitlyAllowed(
        path: String,
        inventory: R2PackageTargetInventory,
    ): Boolean = path in inventory.filePaths

    fun toVfsPath(archivePath: String): String {
        requirePackageTargetPath(archivePath)
        return "/$archivePath"
    }

    internal fun requirePackageTargetPath(path: String) {
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
            packageTargetPolicyFailure(
                R2PackageTargetPolicyCode.PATH_INVALID,
                "Package target path is not a safe printable-ASCII relative path: $path.",
            )
        }
        val segments = path.split('/')
        if (
            segments.any { segment -> segment.isEmpty() || segment == "." || segment == ".." } ||
            segments.any { segment -> segment.asciiCaseFold() == NODE_MODULES_SEGMENT } ||
            segments.last().asciiCaseFold() == PACKAGE_METADATA_FILE_NAME
        ) {
            packageTargetPolicyFailure(
                R2PackageTargetPolicyCode.PATH_INVALID,
                "Package target path uses a reserved or non-canonical segment: $path.",
            )
        }
        if (
            !path.endsWith(".ts") ||
            path.endsWith(".mts") ||
            path.endsWith(".cts")
        ) {
            packageTargetPolicyFailure(
                R2PackageTargetPolicyCode.SOURCE_KIND_UNSUPPORTED,
                "Package target source kind is not supported: $path.",
            )
        }
    }

    private const val PACKAGE_METADATA_FILE_NAME = "package.json"
    private const val NODE_MODULES_SEGMENT = "node_modules"
    private val SAFE_RELATIVE_PATH_PATTERN = Regex(SAFE_RELATIVE_PATH_GRAMMAR)
    private val PRINTABLE_ASCII = 0x20..0x7e
}

private fun requireTargetSha256(
    value: String,
    label: String,
    code: R2PackageTargetPolicyCode,
) {
    try {
        R2Digests.requireLowerSha256(value, label)
    } catch (error: IllegalArgumentException) {
        packageTargetPolicyFailure(code, error.message.orEmpty(), error)
    }
}

private fun String.asciiCaseFold(): String = buildString(length) {
    this@asciiCaseFold.forEach { character ->
        append(
            if (character in 'A'..'Z') {
                (character.code + ASCII_CASE_OFFSET).toChar()
            } else {
                character
            },
        )
    }
}

private fun packageTargetPolicyFailure(
    code: R2PackageTargetPolicyCode,
    message: String,
    cause: Throwable? = null,
): Nothing = throw R2PackageTargetPolicyException(code, message, cause)

private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
