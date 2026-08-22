package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal enum class R2PackageMetadataPolicyCode(
    val contractValue: String,
) {
    PATH_INVALID("PACKAGE_PATH_INVALID"),
    ROOT_NOT_ALLOWED("PACKAGE_ROOT_NOT_ALLOWED"),
    INVENTORY_INVALID("PACKAGE_INVENTORY_INVALID"),
    QUOTA_EXCEEDED("PACKAGE_QUOTA_EXCEEDED"),
    ENCODING_INVALID("PACKAGE_ENCODING_INVALID"),
    JSON_INVALID("PACKAGE_JSON_INVALID"),
    DUPLICATE_KEY("PACKAGE_DUPLICATE_KEY"),
    COMPLEXITY_EXCEEDED("PACKAGE_COMPLEXITY_EXCEEDED"),
    LIFECYCLE_FORBIDDEN("PACKAGE_LIFECYCLE_FORBIDDEN"),
}

internal class R2PackageMetadataPolicyException(
    val policyCode: R2PackageMetadataPolicyCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal data class R2PackageMetadataAuthority(
    val fingerprint: String,
    val policyRevision: Int = R2PackageMetadataPolicy.POLICY_REVISION,
    val policyFingerprint: String = R2PackageMetadataPolicy.fingerprint,
) {

    init {
        R2Digests.requireLowerSha256(fingerprint, "Package metadata inventory fingerprint")
        if (policyRevision != R2PackageMetadataPolicy.POLICY_REVISION) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                "Package metadata policy revision is not supported.",
            )
        }
        R2Digests.requireLowerSha256(
            policyFingerprint,
            "Package metadata policy fingerprint",
        )
        if (!R2Digests.equal(policyFingerprint, R2PackageMetadataPolicy.fingerprint)) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                "Package metadata policy fingerprint is not supported.",
            )
        }
    }
}

internal data class R2PackageMetadataFileDescriptor(
    val path: String,
    val size: Long,
    val sha256: String,
) {

    init {
        R2PackageMetadataPolicy.requirePackageMetadataPath(path)
        if (size !in 0L..R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.QUOTA_EXCEEDED,
                "Package metadata file exceeds the single-file byte limit: $path.",
            )
        }
        try {
            R2Digests.requireLowerSha256(sha256, "Package metadata file SHA-256")
        } catch (error: IllegalArgumentException) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                error.message.orEmpty(),
                error,
            )
        }
    }
}

internal class R2PackageMetadataInventory private constructor(
    dependencyRoots: List<String>,
    files: List<R2PackageMetadataFileDescriptor>,
    val canonicalInput: String,
    val fingerprint: String,
    val totalBytes: Long,
) {

    val dependencyRoots: List<String> =
        Collections.unmodifiableList(ArrayList(dependencyRoots))
    val files: List<R2PackageMetadataFileDescriptor> =
        Collections.unmodifiableList(ArrayList(files))
    val filePaths: Set<String> = Collections.unmodifiableSet(
        files.mapTo(LinkedHashSet(files.size), R2PackageMetadataFileDescriptor::path),
    )
    val authority: R2PackageMetadataAuthority = R2PackageMetadataAuthority(fingerprint)

    companion object {

        fun create(
            dependencyRoots: List<String>,
            files: List<R2PackageMetadataFileDescriptor>,
        ): R2PackageMetadataInventory {
            val ownedRoots = ArrayList(dependencyRoots)
            val ownedFiles = ArrayList(files)
            requireDependencyRoots(ownedRoots)
            requireFiles(ownedRoots, ownedFiles)

            var totalBytes = 0L
            ownedFiles.forEach { file ->
                if (totalBytes > R2PackageMetadataPolicy.MAXIMUM_TOTAL_BYTES - file.size) {
                    packagePolicyFailure(
                        R2PackageMetadataPolicyCode.QUOTA_EXCEEDED,
                        "Package metadata inventory exceeds the aggregate byte limit.",
                    )
                }
                totalBytes += file.size
            }
            val canonicalInput = buildString {
                append(R2PackageMetadataPolicy.INVENTORY_CANONICAL_DOMAIN)
                ownedRoots.forEach { root ->
                    append("|root=")
                    append(root)
                }
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
                "Package metadata inventory canonical input is not printable ASCII."
            }
            val fingerprint = R2Digests.sha256(
                canonicalInput.toByteArray(StandardCharsets.US_ASCII),
            )
            return R2PackageMetadataInventory(
                dependencyRoots = ownedRoots,
                files = ownedFiles,
                canonicalInput = canonicalInput,
                fingerprint = fingerprint,
                totalBytes = totalBytes,
            )
        }

        private fun requireDependencyRoots(roots: List<String>) {
            if (roots.size > R2PackageMetadataPolicy.MAXIMUM_DEPENDENCY_ROOTS) {
                packagePolicyFailure(
                    R2PackageMetadataPolicyCode.QUOTA_EXCEEDED,
                    "Package metadata dependency-root count exceeds its limit.",
                )
            }
            var previous: String? = null
            roots.forEach { root ->
                R2PackageMetadataPolicy.requireDependencyRoot(root)
                previous?.let { prior ->
                    if (prior >= root) {
                        packagePolicyFailure(
                            R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                            "Package metadata dependency roots must be strictly sorted.",
                        )
                    }
                    if (root.startsWith("$prior/")) {
                        packagePolicyFailure(
                            R2PackageMetadataPolicyCode.ROOT_NOT_ALLOWED,
                            "Package metadata dependency roots must not overlap.",
                        )
                    }
                }
                previous = root
            }
        }

        private fun requireFiles(
            roots: List<String>,
            files: List<R2PackageMetadataFileDescriptor>,
        ) {
            if (files.isEmpty()) {
                packagePolicyFailure(
                    R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                    "Package metadata inventory must contain the project package.json.",
                )
            }
            if (files.size > R2PackageMetadataPolicy.MAXIMUM_FILES) {
                packagePolicyFailure(
                    R2PackageMetadataPolicyCode.QUOTA_EXCEEDED,
                    "Package metadata file count exceeds its limit.",
                )
            }
            val foldedPaths = HashSet<String>(files.size)
            var previous: String? = null
            files.forEach { file ->
                previous?.let { prior ->
                    if (prior >= file.path) {
                        packagePolicyFailure(
                            R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                            "Package metadata files must be strictly sorted.",
                        )
                    }
                }
                if (!foldedPaths.add(file.path.asciiCaseFold())) {
                    packagePolicyFailure(
                        R2PackageMetadataPolicyCode.PATH_INVALID,
                        "Package metadata files contain an ASCII case-fold collision.",
                    )
                }
                if (
                    file.path != R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH &&
                    roots.none { root -> file.path.startsWith("$root/") }
                ) {
                    packagePolicyFailure(
                        R2PackageMetadataPolicyCode.ROOT_NOT_ALLOWED,
                        "Package metadata file is outside the explicit dependency roots: " +
                            "${file.path}.",
                    )
                }
                previous = file.path
            }
            val paths = files.mapTo(HashSet(files.size), R2PackageMetadataFileDescriptor::path)
            if (R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH !in paths) {
                packagePolicyFailure(
                    R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                    "Package metadata inventory is missing the project package.json.",
                )
            }
            roots.forEach { root ->
                if ("$root/${R2PackageMetadataPolicy.PACKAGE_FILE_NAME}" !in paths) {
                    packagePolicyFailure(
                        R2PackageMetadataPolicyCode.INVENTORY_INVALID,
                        "Package metadata dependency root is missing its package.json: $root.",
                    )
                }
            }
        }

        private val PRINTABLE_ASCII = ' '.code..'~'.code
    }
}

/** R2-M2-C5 positive authority for bounded, inert package metadata transport. */
internal object R2PackageMetadataPolicy {

    const val SCHEMA = "autojs6.typescript.package-metadata-policy"
    const val SCHEMA_REVISION = 1
    const val POLICY_REVISION = 1
    const val PROJECT_PACKAGE_PATH = "package.json"
    const val PACKAGE_FILE_NAME = "package.json"
    const val DEPENDENCY_ROOT_PREFIX = "packages/"
    const val MAXIMUM_DEPENDENCY_ROOTS = 128
    const val MAXIMUM_FILES = 256
    const val MAXIMUM_FILE_BYTES = 64L * 1024L
    const val MAXIMUM_TOTAL_BYTES = 1024L * 1024L
    const val MAXIMUM_JSON_DEPTH = 16
    const val MAXIMUM_JSON_NODES = 4 * 1024
    const val INVENTORY_CANONICAL_DOMAIN =
        "autojs6.typescript.package-metadata.inventory.v1"
    const val SAFE_RELATIVE_PATH_GRAMMAR = "^[A-Za-z0-9@._/+~-]+$"
    const val JSON_MODE = "strict-utf8-duplicate-safe-raw"
    const val RESOLVER_MODE = "raw-not-resolver-authority"

    private val DEPENDENCY_ROOT_PATTERN = Regex(
        "packages/(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*",
    )
    private val SAFE_RELATIVE_PATH_PATTERN = Regex(SAFE_RELATIVE_PATH_GRAMMAR)
    private val PRINTABLE_ASCII = ' '.code..'~'.code

    val forbiddenTopLevelKeys: Set<String> =
        Collections.unmodifiableSet(linkedSetOf("scripts"))

    val canonicalInput: String = buildString {
        append("autojs6.typescript.package-metadata-policy.v1")
        append("|schemaRevision=")
        append(SCHEMA_REVISION)
        append("|policyRevision=")
        append(POLICY_REVISION)
        append("|projectPackagePath=")
        append(PROJECT_PACKAGE_PATH)
        append("|dependencyRootPrefix=")
        append(DEPENDENCY_ROOT_PREFIX)
        append("|maximumDependencyRoots=")
        append(MAXIMUM_DEPENDENCY_ROOTS)
        append("|maximumFiles=")
        append(MAXIMUM_FILES)
        append("|maximumFileBytes=")
        append(MAXIMUM_FILE_BYTES)
        append("|maximumTotalBytes=")
        append(MAXIMUM_TOTAL_BYTES)
        append("|maximumJsonDepth=")
        append(MAXIMUM_JSON_DEPTH)
        append("|maximumJsonNodes=")
        append(MAXIMUM_JSON_NODES)
        append("|forbiddenTopLevelKeys=")
        append(forbiddenTopLevelKeys.sorted().joinToString(","))
        append("|safeRelativePathGrammar=")
        append(SAFE_RELATIVE_PATH_GRAMMAR)
        append("|jsonMode=")
        append(JSON_MODE)
        append("|resolverMode=")
        append(RESOLVER_MODE)
    }

    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        check(canonicalInput.all { character -> character.code in PRINTABLE_ASCII })
        R2Digests.requireLowerSha256(fingerprint, "Package metadata policy fingerprint")
    }

    fun validatePackageJson(bytes: ByteArray) {
        if (bytes.size.toLong() > MAXIMUM_FILE_BYTES) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.QUOTA_EXCEEDED,
                "Package metadata exceeds the single-file byte limit.",
            )
        }
        val root = try {
            R2StrictJson.parseObject(
                bytes = bytes,
                maximumDepth = MAXIMUM_JSON_DEPTH,
                maximumNodes = MAXIMUM_JSON_NODES,
            )
        } catch (error: IllegalArgumentException) {
            val message = error.message.orEmpty()
            val code = when {
                "valid UTF-8" in message || "valid Unicode text" in message ->
                    R2PackageMetadataPolicyCode.ENCODING_INVALID
                "duplicate key" in message -> R2PackageMetadataPolicyCode.DUPLICATE_KEY
                "nesting exceeds" in message || "more than" in message ->
                    R2PackageMetadataPolicyCode.COMPLEXITY_EXCEEDED
                else -> R2PackageMetadataPolicyCode.JSON_INVALID
            }
            packagePolicyFailure(code, "Package metadata JSON is invalid.", error)
        }
        if (forbiddenTopLevelKeys.any(root::containsKey)) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.LIFECYCLE_FORBIDDEN,
                "Package metadata contains forbidden lifecycle authority.",
            )
        }
    }

    fun isExplicitlyAllowed(
        path: String,
        inventory: R2PackageMetadataInventory,
    ): Boolean = path in inventory.filePaths

    fun toVfsPath(archivePath: String): String {
        requirePackageMetadataPath(archivePath)
        return "/$archivePath"
    }

    internal fun requireDependencyRoot(root: String) {
        requireCanonicalRelativePath(root, "Package metadata dependency root")
        if (!DEPENDENCY_ROOT_PATTERN.matches(root)) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.ROOT_NOT_ALLOWED,
                "Package metadata dependency root is not canonical: $root.",
            )
        }
    }

    internal fun requirePackageMetadataPath(path: String) {
        requireCanonicalRelativePath(path, "Package metadata path")
        val segments = path.split('/')
        if (
            segments.any { segment -> segment.asciiCaseFold() == "node_modules" } ||
            path.asciiCaseFold().endsWith(".cts") ||
            path.asciiCaseFold().endsWith(".mts")
        ) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.PATH_INVALID,
                "Package metadata path uses a reserved compiler escape path: $path.",
            )
        }
        if (
            path != PROJECT_PACKAGE_PATH &&
            (
                !path.startsWith(DEPENDENCY_ROOT_PREFIX) ||
                    segments.size < 3 ||
                    segments.last() != PACKAGE_FILE_NAME
                )
        ) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.PATH_INVALID,
                "Package metadata path is not a controlled package.json path: $path.",
            )
        }
    }

    private fun requireCanonicalRelativePath(
        path: String,
        label: String,
    ) {
        if (
            path.isEmpty() ||
            path.length > TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES ||
            path.startsWith('/') ||
            '\\' in path ||
            '|' in path ||
            ',' in path ||
            path.any { character -> character.code !in PRINTABLE_ASCII } ||
            !SAFE_RELATIVE_PATH_PATTERN.matches(path)
        ) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.PATH_INVALID,
                "$label is not a safe printable-ASCII relative path.",
            )
        }
        val segments = path.split('/')
        if (
            segments.size > TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH ||
            segments.any { segment -> segment.isEmpty() || segment == "." || segment == ".." }
        ) {
            packagePolicyFailure(
                R2PackageMetadataPolicyCode.PATH_INVALID,
                "$label is not a canonical relative path.",
            )
        }
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

private fun packagePolicyFailure(
    code: R2PackageMetadataPolicyCode,
    message: String,
    cause: Throwable? = null,
): Nothing = throw R2PackageMetadataPolicyException(code, message, cause)

private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
