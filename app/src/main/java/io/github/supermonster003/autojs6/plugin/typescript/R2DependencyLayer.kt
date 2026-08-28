package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal enum class R2DependencyLayerPolicyCode {
    PATH_INVALID,
    INVENTORY_INVALID,
    QUOTA_EXCEEDED,
    NATIVE_DEPENDENCY_UNSUPPORTED,
    INSTALL_LIFECYCLE_UNSUPPORTED,
}

internal class R2DependencyLayerPolicyException(
    val policyCode: R2DependencyLayerPolicyCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal data class R2DependencyLayerAuthority(
    val fingerprint: String,
    val inventoryFingerprint: String,
    val policyRevision: Int = R2DependencyLayerPolicy.POLICY_REVISION,
    val policyFingerprint: String = R2DependencyLayerPolicy.fingerprint,
) {
    init {
        R2Digests.requireLowerSha256(fingerprint, "Dependency-layer fingerprint")
        R2Digests.requireLowerSha256(
            inventoryFingerprint,
            "Dependency-layer inventory fingerprint",
        )
        if (policyRevision != R2DependencyLayerPolicy.POLICY_REVISION) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                "Dependency-layer policy revision is not supported.",
            )
        }
        R2Digests.requireLowerSha256(
            policyFingerprint,
            "Dependency-layer policy fingerprint",
        )
        if (!R2Digests.equal(policyFingerprint, R2DependencyLayerPolicy.fingerprint)) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                "Dependency-layer policy fingerprint is not supported.",
            )
        }
    }
}

internal data class R2DependencyLayerFileDescriptor(
    val path: String,
    val size: Long,
    val sha256: String,
) {
    init {
        R2DependencyLayerPolicy.requireArchivePath(path)
        if (size !in 0L..TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_SINGLE_FILE_BYTES) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.QUOTA_EXCEEDED,
                "Dependency-layer file exceeds the single-file byte limit: $path.",
            )
        }
        try {
            R2Digests.requireLowerSha256(sha256, "Dependency-layer file SHA-256")
        } catch (error: IllegalArgumentException) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                error.message.orEmpty(),
                error,
            )
        }
    }
}

internal class R2DependencyLayerInventory private constructor(
    val identityKind: String,
    val identityPath: String,
    val identityFingerprint: String,
    files: List<R2DependencyLayerFileDescriptor>,
    val inventoryFingerprint: String,
    val fingerprint: String,
    val totalBytes: Long,
    val totalPathBytes: Long,
) {
    val files: List<R2DependencyLayerFileDescriptor> =
        Collections.unmodifiableList(ArrayList(files))
    val filePaths: Set<String> = Collections.unmodifiableSet(
        files.mapTo(LinkedHashSet(files.size), R2DependencyLayerFileDescriptor::path),
    )
    val authority = R2DependencyLayerAuthority(
        fingerprint = fingerprint,
        inventoryFingerprint = inventoryFingerprint,
    )

    companion object {
        fun create(
            identityKind: String,
            identityPath: String,
            identityFingerprint: String,
            files: List<R2DependencyLayerFileDescriptor>,
        ): R2DependencyLayerInventory {
            if (identityKind !in R2DependencyLayerPolicy.IDENTITY_KINDS) {
                dependencyLayerFailure(
                    R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                    "Dependency-layer identity kind is not supported.",
                )
            }
            try {
                R2Digests.requireLowerSha256(
                    identityFingerprint,
                    "Dependency-layer identity fingerprint",
                )
            } catch (error: IllegalArgumentException) {
                dependencyLayerFailure(
                    R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                    error.message.orEmpty(),
                    error,
                )
            }
            if (files.isEmpty()) {
                dependencyLayerFailure(
                    R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                    "Dependency-layer inventory must not be empty.",
                )
            }
            if (files.size > TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_FILES) {
                dependencyLayerFailure(
                    R2DependencyLayerPolicyCode.QUOTA_EXCEEDED,
                    "Dependency-layer file count exceeds its independent limit.",
                )
            }
            val ownedFiles = ArrayList(files)
            val foldedPaths = HashSet<String>(ownedFiles.size)
            var previous: String? = null
            var totalBytes = 0L
            var totalPathBytes = 0L
            ownedFiles.forEach { file ->
                previous?.let { prior ->
                    if (prior >= file.path) {
                        dependencyLayerFailure(
                            R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                            "Dependency-layer files must be strictly sorted.",
                        )
                    }
                }
                if (!foldedPaths.add(file.path.asciiCaseFoldForDependencyLayer())) {
                    dependencyLayerFailure(
                        R2DependencyLayerPolicyCode.PATH_INVALID,
                        "Dependency-layer files contain an ASCII case-fold collision.",
                    )
                }
                if (totalBytes > TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_BYTES - file.size) {
                    dependencyLayerFailure(
                        R2DependencyLayerPolicyCode.QUOTA_EXCEEDED,
                        "Dependency-layer bytes exceed the independent aggregate limit.",
                    )
                }
                totalBytes += file.size
                val pathBytes = file.path.toByteArray(StandardCharsets.UTF_8).size.toLong()
                if (
                    totalPathBytes >
                    TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_PATH_BYTES - pathBytes
                ) {
                    dependencyLayerFailure(
                        R2DependencyLayerPolicyCode.QUOTA_EXCEEDED,
                        "Dependency-layer paths exceed the independent metadata limit.",
                    )
                }
                totalPathBytes += pathBytes
                previous = file.path
            }

            val inventoryFingerprint = inventoryFingerprint(ownedFiles)
            when (identityKind) {
                R2DependencyLayerPolicy.IDENTITY_CONTENT -> {
                    if (
                        identityPath.isNotEmpty() ||
                        !R2Digests.equal(identityFingerprint, inventoryFingerprint)
                    ) {
                        dependencyLayerFailure(
                            R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                            "Content-addressed dependency layer has inconsistent identity.",
                        )
                    }
                }
                R2DependencyLayerPolicy.IDENTITY_PACKAGE_LOCK,
                R2DependencyLayerPolicy.IDENTITY_PNPM_LOCK,
                -> {
                    val expectedPath = R2DependencyLayerPolicy.lockfileArchivePath(identityKind)
                    if (identityPath != expectedPath) {
                        dependencyLayerFailure(
                            R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                            "Dependency-layer lockfile identity path is not canonical.",
                        )
                    }
                    val lockfile = ownedFiles.firstOrNull { file -> file.path == identityPath }
                        ?: dependencyLayerFailure(
                            R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                            "Dependency-layer lockfile identity is absent from its inventory.",
                        )
                    if (!R2Digests.equal(lockfile.sha256, identityFingerprint)) {
                        dependencyLayerFailure(
                            R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                            "Dependency-layer lockfile identity does not match its file bytes.",
                        )
                    }
                    if (lockfile.size > TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_LOCKFILE_BYTES) {
                        dependencyLayerFailure(
                            R2DependencyLayerPolicyCode.QUOTA_EXCEEDED,
                            "Dependency-layer lockfile exceeds its byte limit.",
                        )
                    }
                }
            }
            val fingerprint = layerFingerprint(
                identityKind = identityKind,
                identityPath = identityPath,
                identityFingerprint = identityFingerprint,
                inventoryFingerprint = inventoryFingerprint,
            )
            return R2DependencyLayerInventory(
                identityKind = identityKind,
                identityPath = identityPath,
                identityFingerprint = identityFingerprint,
                files = ownedFiles,
                inventoryFingerprint = inventoryFingerprint,
                fingerprint = fingerprint,
                totalBytes = totalBytes,
                totalPathBytes = totalPathBytes,
            )
        }

        private fun inventoryFingerprint(files: List<R2DependencyLayerFileDescriptor>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(
                R2DependencyLayerPolicy.INVENTORY_DOMAIN.toByteArray(StandardCharsets.US_ASCII),
            )
            digest.update('\n'.code.toByte())
            files.forEach { file ->
                digest.update(file.path.toByteArray(StandardCharsets.UTF_8))
                digest.update(0)
                digest.update(file.size.toString().toByteArray(StandardCharsets.US_ASCII))
                digest.update(0)
                digest.update(file.sha256.toByteArray(StandardCharsets.US_ASCII))
                digest.update('\n'.code.toByte())
            }
            return digest.digest().toLowerHexForDependencyLayer()
        }

        private fun layerFingerprint(
            identityKind: String,
            identityPath: String,
            identityFingerprint: String,
            inventoryFingerprint: String,
        ): String {
            val canonical = buildString {
                append(R2DependencyLayerPolicy.FINGERPRINT_DOMAIN)
                append("|schemaRevision=")
                append(R2DependencyLayerPolicy.SCHEMA_REVISION)
                append("|policyRevision=")
                append(R2DependencyLayerPolicy.POLICY_REVISION)
                append("|policyFingerprint=")
                append(R2DependencyLayerPolicy.fingerprint)
                append("|identityKind=")
                append(identityKind)
                append("|identityPath=")
                append(identityPath)
                append("|identityFingerprint=")
                append(identityFingerprint)
                append("|inventoryFingerprint=")
                append(inventoryFingerprint)
            }
            check(canonical.all { character -> character.code in ' '.code..'~'.code })
            return R2Digests.sha256(canonical.toByteArray(StandardCharsets.US_ASCII))
        }
    }
}

internal object R2DependencyLayerPolicy {
    const val SCHEMA = "autojs6.typescript.dependency-layer"
    const val SCHEMA_REVISION = TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION
    const val POLICY_REVISION = TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION
    const val DEPENDENCY_ARCHIVE_PREFIX = "dependencies/node_modules/"
    const val LOCKFILE_ARCHIVE_PREFIX = "dependencies/lockfiles/"
    const val IDENTITY_PACKAGE_LOCK = "package-lock"
    const val IDENTITY_PNPM_LOCK = "pnpm-lock"
    const val IDENTITY_CONTENT = "content"
    const val INVENTORY_DOMAIN = "autojs6.typescript.dependency-layer.inventory.v1"
    const val FINGERPRINT_DOMAIN = "autojs6.typescript.dependency-layer.fingerprint.v1"
    const val READ_MODE = "immutable-file-backed-framed-readonly-t2-2"
    const val NATIVE_BINARY_SIGNAL = "native-binary"
    const val BINDING_GYP_SIGNAL = "binding-gyp"
    const val GYPFILE_SIGNAL = "gypfile"
    const val INSTALL_LIFECYCLE_SIGNAL = "install-lifecycle"

    val IDENTITY_KINDS: Set<String> = Collections.unmodifiableSet(
        linkedSetOf(IDENTITY_PACKAGE_LOCK, IDENTITY_PNPM_LOCK, IDENTITY_CONTENT),
    )

    private val EXCLUDED_DIRECTORY_NAMES = setOf(
        ".bin",
        ".git",
        ".github",
        "bench",
        "benchmark",
        "benchmarks",
        "coverage",
        "doc",
        "docs",
        "example",
        "examples",
        "test",
        "tests",
    )
    private val ALLOWED_SUFFIXES = setOf(
        ".cjs",
        ".cts",
        ".js",
        ".json",
        ".jsx",
        ".mjs",
        ".mts",
        ".ts",
        ".tsx",
    )
    private val EXCLUDED_SUFFIXES = setOf(".map", ".markdown", ".md")
    private val NATIVE_BINARY_SUFFIXES = setOf(".node")
    private val NATIVE_BUILD_FILE_NAMES = setOf("binding.gyp")
    private val NATIVE_METADATA_FLAGS = setOf("gypfile")
    val INSTALL_LIFECYCLE_KEYS: Set<String> = Collections.unmodifiableSet(
        linkedSetOf("preinstall", "install", "postinstall"),
    )
    private val BOUNDARY_PRIORITY = listOf(
        NATIVE_BINARY_SIGNAL,
        BINDING_GYP_SIGNAL,
        GYPFILE_SIGNAL,
        INSTALL_LIFECYCLE_SIGNAL,
    )

    val canonicalInput: String = buildString {
        append("autojs6.typescript.dependency-layer-policy.v2")
        append("|schemaRevision=")
        append(SCHEMA_REVISION)
        append("|policyRevision=")
        append(POLICY_REVISION)
        append("|dependencyArchivePrefix=")
        append(DEPENDENCY_ARCHIVE_PREFIX)
        append("|lockfileArchivePrefix=")
        append(LOCKFILE_ARCHIVE_PREFIX)
        append("|identityKinds=")
        append(IDENTITY_KINDS.sorted().joinToString(","))
        append("|maximumFiles=")
        append(TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_FILES)
        append("|maximumSingleFileBytes=")
        append(TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_SINGLE_FILE_BYTES)
        append("|maximumBytes=")
        append(TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_BYTES)
        append("|maximumLockfileBytes=")
        append(TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_LOCKFILE_BYTES)
        append("|maximumPathBytes=")
        append(TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_PATH_BYTES)
        append("|excludedDirectories=")
        append(EXCLUDED_DIRECTORY_NAMES.sorted().joinToString(","))
        append("|allowedSuffixes=")
        append(ALLOWED_SUFFIXES.sorted().joinToString(","))
        append("|excludedSuffixes=")
        append(EXCLUDED_SUFFIXES.sorted().joinToString(","))
        append("|nativeBinarySuffixes=")
        append(NATIVE_BINARY_SUFFIXES.sorted().joinToString(","))
        append("|nativeBuildFiles=")
        append(NATIVE_BUILD_FILE_NAMES.sorted().joinToString(","))
        append("|nativeMetadataFlags=")
        append(NATIVE_METADATA_FLAGS.sorted().joinToString(","))
        append("|installLifecycleKeys=")
        append(INSTALL_LIFECYCLE_KEYS.sorted().joinToString(","))
        append("|boundaryPriority=")
        append(BOUNDARY_PRIORITY.joinToString(","))
        append("|packageScripts=non-install-inert-install-rejected")
        append("|readMode=")
        append(READ_MODE)
    }
    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        check(canonicalInput.all { character -> character.code in ' '.code..'~'.code })
        R2Digests.requireLowerSha256(fingerprint, "Dependency-layer policy fingerprint")
    }

    fun lockfileArchivePath(identityKind: String): String = when (identityKind) {
        IDENTITY_PACKAGE_LOCK -> "${LOCKFILE_ARCHIVE_PREFIX}package-lock.json"
        IDENTITY_PNPM_LOCK -> "${LOCKFILE_ARCHIVE_PREFIX}pnpm-lock.yaml"
        else -> ""
    }

    fun isExplicitlyAllowed(
        path: String,
        inventory: R2DependencyLayerInventory,
    ): Boolean = path in inventory.filePaths

    fun toFutureVfsPath(archivePath: String): String {
        requireArchivePath(archivePath)
        return when {
            archivePath.startsWith(DEPENDENCY_ARCHIVE_PREFIX) ->
                "/node_modules/${archivePath.removePrefix(DEPENDENCY_ARCHIVE_PREFIX)}"
            else -> "/.autojs6/${archivePath.removePrefix("dependencies/")}"
        }
    }

    fun validateDependencyFile(path: String, bytes: ByteArray) {
        nativeBoundarySignalForPath(path)?.let { signal ->
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.NATIVE_DEPENDENCY_UNSUPPORTED,
                "Dependency layer contains unsupported native signal '$signal': $path.",
            )
        }
        if (
            !path.startsWith(DEPENDENCY_ARCHIVE_PREFIX) ||
            path.substringAfterLast('/').asciiCaseFoldForDependencyLayer() != "package.json"
        ) {
            return
        }
        val root = try {
            R2StrictJson.parseObject(
                bytes = bytes,
                maximumDepth = MAXIMUM_PACKAGE_JSON_DEPTH,
                maximumNodes = MAXIMUM_PACKAGE_JSON_NODES,
            )
        } catch (error: IllegalArgumentException) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.INVENTORY_INVALID,
                "Dependency package metadata is not valid strict JSON: $path.",
                error,
            )
        }
        if (NATIVE_METADATA_FLAGS.any { flag -> root[flag] == true }) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.NATIVE_DEPENDENCY_UNSUPPORTED,
                "Dependency package metadata declares native build signal '$GYPFILE_SIGNAL': $path.",
            )
        }
        val scripts = root["scripts"] as? Map<*, *> ?: return
        val lifecycle = INSTALL_LIFECYCLE_KEYS.sorted().firstOrNull(scripts::containsKey) ?: return
        dependencyLayerFailure(
            R2DependencyLayerPolicyCode.INSTALL_LIFECYCLE_UNSUPPORTED,
            "Dependency package metadata declares unsupported lifecycle script '$lifecycle': $path.",
        )
    }

    fun nativeBoundarySignalForPath(path: String): String? {
        val foldedPath = path.asciiCaseFoldForDependencyLayer()
        val foldedName = foldedPath.substringAfterLast('/')
        return when {
            NATIVE_BINARY_SUFFIXES.any(foldedName::endsWith) -> NATIVE_BINARY_SIGNAL
            foldedName in NATIVE_BUILD_FILE_NAMES -> BINDING_GYP_SIGNAL
            else -> null
        }
    }

    internal fun requireArchivePath(path: String) {
        if (!R2UnicodePathPolicy.isCanonicalRelativePath(path)) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.PATH_INVALID,
                "Dependency-layer path is not canonical: $path.",
            )
        }
        if (path == lockfileArchivePath(IDENTITY_PACKAGE_LOCK) ||
            path == lockfileArchivePath(IDENTITY_PNPM_LOCK)
        ) {
            return
        }
        if (!path.startsWith(DEPENDENCY_ARCHIVE_PREFIX)) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.PATH_INVALID,
                "Dependency-layer path is outside node_modules: $path.",
            )
        }
        nativeBoundarySignalForPath(path)?.let { signal ->
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.NATIVE_DEPENDENCY_UNSUPPORTED,
                "Dependency-layer native signal '$signal' is unsupported: $path.",
            )
        }
        val relative = path.removePrefix(DEPENDENCY_ARCHIVE_PREFIX)
        val segments = relative.split('/')
        val foldedSegments = segments.map(String::asciiCaseFoldForDependencyLayer)
        if (
            segments.size < 2 ||
            foldedSegments.any(EXCLUDED_DIRECTORY_NAMES::contains)
        ) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.PATH_INVALID,
                "Dependency-layer path uses an excluded package asset directory: $path.",
            )
        }
        val name = foldedSegments.last()
        if (
            EXCLUDED_SUFFIXES.any(name::endsWith) ||
            ALLOWED_SUFFIXES.none(name::endsWith)
        ) {
            dependencyLayerFailure(
                R2DependencyLayerPolicyCode.PATH_INVALID,
                "Dependency-layer file kind is not compilation-relevant: $path.",
            )
        }
    }

    private const val MAXIMUM_PACKAGE_JSON_DEPTH = 32
    private const val MAXIMUM_PACKAGE_JSON_NODES = 64 * 1024
}

private fun String.asciiCaseFoldForDependencyLayer(): String = buildString(length) {
    this@asciiCaseFoldForDependencyLayer.forEach { character ->
        append(if (character in 'A'..'Z') (character.code + 32).toChar() else character)
    }
}

private fun ByteArray.toLowerHexForDependencyLayer(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun dependencyLayerFailure(
    code: R2DependencyLayerPolicyCode,
    message: String,
    cause: Throwable? = null,
): Nothing = throw R2DependencyLayerPolicyException(code, message, cause)
