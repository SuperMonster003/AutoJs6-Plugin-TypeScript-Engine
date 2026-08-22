package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Collections
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1EntrySinkException
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Exception
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validator
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal data class R2ProjectArchiveAuthority(
    val requestId: String,
    val projectId: String,
    val entrySourceUri: String,
    val profile: R2ProjectProfile,
    val profileAuthority: R2CompilationProfileAuthority =
        R2CompilationProfileRegistry.require(profile),
    val compilationMode: String,
    val compilerVersion: String,
    val compilerDistributionFingerprint: String,
    val declarationsFingerprint: String,
    val inputArchiveSchemaRevision: Int,
    val declarationProfileFingerprint: String,
    val runtimeCapabilitiesFingerprint: String,
    val archiveSha256: String,
    val requestManifestSha256: String,
    val compilerOptionsJson: String =
        R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON,
    val rawConfigAuthority: R2RawProjectConfigAuthority? = null,
    val packageMetadataAuthority: R2PackageMetadataAuthority? = null,
    val packageTargetAuthority: R2PackageTargetAuthority? = null,
    val nodePackageTargetV2Authority: R2NodePackageTargetV2Authority? = null,
) {
    init {
        val isRhinoRevisionOne =
            profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO
        val isNodeRevisionOne =
            profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val isNodeRevisionTwo =
            profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        require(requestId.isNotBlank())
        require(requestId.length <= TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS)
        require(projectId.isNotBlank())
        require(projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS)
        require(
            entrySourceUri.startsWith(SOURCE_URI_PREFIX) &&
                entrySourceUri.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS,
        )
        require(
            profileAuthority.profile == profile &&
                R2CompilationProfileRegistry.containsExact(profileAuthority),
        ) {
            "Project archive profile authority is not an exact registered authority."
        }
        require(
            when {
                isRhinoRevisionOne -> true
                isNodeRevisionOne ->
                    rawConfigAuthority == null &&
                        packageMetadataAuthority == null &&
                        packageTargetAuthority == null &&
                        nodePackageTargetV2Authority == null
                isNodeRevisionTwo ->
                    rawConfigAuthority == null && packageTargetAuthority == null
                else -> false
            },
        ) {
            "Project archive input authorities are not allowed for this exact profile revision."
        }
        require(compilationMode in TypeScriptCompilerContract.COMPILATION_MODES)
        require(compilerVersion.isNotBlank() && compilerVersion.length <= MAXIMUM_VERSION_CHARS)
        R2Digests.requireLowerSha256(
            compilerDistributionFingerprint,
            "Compiler distribution fingerprint",
        )
        R2Digests.requireLowerSha256(
            declarationsFingerprint,
            "Declarations fingerprint",
        )
        require(inputArchiveSchemaRevision == R2InputArchiveSchemaV1.REVISION) {
            "Input archive schema revision is not supported."
        }
        R2Digests.requireLowerSha256(
            declarationProfileFingerprint,
            "Declaration profile fingerprint",
        )
        R2Digests.requireLowerSha256(
            runtimeCapabilitiesFingerprint,
            "Runtime capabilities fingerprint",
        )
        require(
            R2Digests.equal(
                runtimeCapabilitiesFingerprint,
                profileAuthority.runtimeCapabilitiesFingerprint,
            ),
        ) {
            "Project archive runtime capabilities do not match its exact profile authority."
        }
        R2Digests.requireLowerSha256(archiveSha256, "Input archive SHA-256")
        R2Digests.requireLowerSha256(
            requestManifestSha256,
            "Request manifest SHA-256",
        )
        require(
            compilerOptionsJson == R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON,
        ) {
            "The provisional archive adapter accepts only an empty compilerOptionsJson."
        }
        require(
            packageMetadataAuthority == null ||
                rawConfigAuthority != null ||
                isNodeRevisionTwo,
        ) {
            "Package metadata authority requires controlled raw project config authority or " +
                "the exact Node revision 2 config-none authority."
        }
        require(packageTargetAuthority == null || rawConfigAuthority != null) {
            "Package target authority requires controlled raw project config authority."
        }
        require(packageTargetAuthority == null || packageMetadataAuthority != null) {
            "Package target authority requires package metadata authority."
        }
        require(nodePackageTargetV2Authority == null || isNodeRevisionTwo) {
            "Node package-target v2 authority requires the exact Node revision 2 profile."
        }
        require(nodePackageTargetV2Authority == null || packageMetadataAuthority != null) {
            "Node package-target v2 authority requires package metadata authority."
        }
        require(packageTargetAuthority == null || nodePackageTargetV2Authority == null) {
            "Package target v1 and Node package-target v2 authorities are mutually exclusive."
        }
        require(
            packageTargetAuthority == null ||
                R2Digests.equal(
                    packageTargetAuthority.packageMetadataInventoryFingerprint,
                    requireNotNull(packageMetadataAuthority).fingerprint,
                )
        ) {
            "Package target authority is bound to different package metadata."
        }
        require(
            nodePackageTargetV2Authority == null ||
                (
                    nodePackageTargetV2Authority.profileAuthority == profileAuthority &&
                        R2Digests.equal(
                            nodePackageTargetV2Authority.packageMetadataInventoryFingerprint,
                            requireNotNull(packageMetadataAuthority).fingerprint,
                        )
                    )
        ) {
            "Node package-target v2 authority is bound to different profile or package metadata."
        }
    }

    private companion object {
        const val SOURCE_URI_PREFIX = "autojs://source/"
        const val MAXIMUM_VERSION_CHARS = 128
    }
}

internal data class R2ProjectArchiveFile(
    val path: String,
    val size: Long,
    val sha256: String,
)

/**
 * Parser/model for the formal [R2InputArchiveSchemaV1] wire.
 *
 * The historical Kotlin type name is retained only to avoid a broad mechanical rename; it no
 * longer accepts or emits the former feature-overloaded manifest revisions 2 through 7.
 */
internal class R2ProvisionalRequestManifest private constructor(
    val requestId: String,
    val projectId: String,
    val entrySourceUri: String,
    val entryPath: String,
    val profileAuthority: R2CompilationProfileAuthority,
    val compilationMode: String,
    val compilerVersion: String,
    val compilerDistributionFingerprint: String,
    val declarationsFingerprint: String,
    val declarationProfileFingerprint: String,
    val runtimeCapabilitiesFingerprint: String,
    val callerInventoryFingerprint: String,
    val rawConfigDescriptor: R2RawProjectConfigDescriptor?,
    val packageMetadataInventory: R2PackageMetadataInventory?,
    val packageTargetInventory: R2PackageTargetInventory?,
    val nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory?,
    rootPaths: List<String>,
    files: List<R2ProjectArchiveFile>,
) {

    val profile: R2ProjectProfile
        get() = profileAuthority.profile

    val rootPaths: List<String> =
        Collections.unmodifiableList(ArrayList(rootPaths))
    val files: List<R2ProjectArchiveFile> =
        Collections.unmodifiableList(ArrayList(files))

    companion object {

        fun parse(
            bytes: ByteArray,
            authority: R2ProjectArchiveAuthority,
        ): R2ProvisionalRequestManifest {
            if (bytes.size.toLong() > MAXIMUM_MANIFEST_BYTES) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Input archive manifest exceeds its materialization limit.",
                )
            }
            val root = parseJson(bytes)
            val rawConfigAuthority = authority.rawConfigAuthority
            val packageMetadataAuthority = authority.packageMetadataAuthority
            val packageTargetAuthority = authority.packageTargetAuthority
            val nodePackageTargetV2Authority = authority.nodePackageTargetV2Authority
            root.requireExactKeys(
                when {
                    packageMetadataAuthority != null &&
                        (packageTargetAuthority != null || nodePackageTargetV2Authority != null) ->
                        if (rawConfigAuthority == null) {
                            TOP_LEVEL_KEYS_WITH_PACKAGE_TARGETS
                        } else {
                            TOP_LEVEL_KEYS_WITH_CONFIG_AND_PACKAGE_TARGETS
                        }
                    packageMetadataAuthority != null ->
                        if (rawConfigAuthority == null) {
                            TOP_LEVEL_KEYS_WITH_PACKAGE_METADATA
                        } else {
                            TOP_LEVEL_KEYS_WITH_CONFIG_AND_PACKAGE_METADATA
                        }
                    rawConfigAuthority != null -> TOP_LEVEL_KEYS_WITH_CONFIG
                    else -> TOP_LEVEL_KEYS
                },
                "request manifest",
            )

            root.requireString("schema").requireEqual(
                R2InputArchiveSchemaV1.SCHEMA,
                "Request manifest schema is not supported.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("revision").requireEqual(
                R2InputArchiveSchemaV1.REVISION,
                "Request manifest revision is not supported.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("inputQuotaRevision").requireEqual(
                R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION,
                "Request manifest input quota revision is not supported.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("contractVersion").requireEqual(
                TypeScriptCompilerContract.VERSION,
                "Request manifest contract version does not match.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireString("contractHash").requireEqual(
                TypeScriptCompilerContract.HASH,
                "Request manifest contract hash does not match.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("inputArchiveTransportVersion").requireEqual(
                TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
                "Request manifest input transport version does not match.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("profileSchemaRevision").requireEqual(
                authority.profileAuthority.profileSchemaRevision,
                "Request manifest profile schema revision does not match.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("profileRevision").requireEqual(
                authority.profileAuthority.profileRevision,
                "Request manifest profile revision is not supported.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("runtimeCapabilitySchemaRevision").requireEqual(
                TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
                "Request manifest runtime capability schema revision does not match.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            root.requireNonNegativeInt("configSchemaRevision").requireEqual(
                if (rawConfigAuthority == null) authority.profileAuthority.configSchemaRevision
                else TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
                "Request manifest config schema revision does not match.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )

            val requestId = root.requireString("requestId")
            val projectId = root.requireString("projectId")
            val entrySourceUri = root.requireString("entrySourceUri")
            val entryPath = root.requireString("entryPath")
            val targetProfile = root.requireString("targetProfile")
            val compilationMode = root.requireString("compilationMode")
            val compilerPackage = root.requireString("compilerPackage")
            val compilerVersion = root.requireString("compilerVersion")
            val compilerDistributionFingerprint =
                root.requireLowerSha256("compilerDistributionFingerprint")
            val declarationsFingerprint = root.requireLowerSha256("declarationsFingerprint")
            val declarationProfileFingerprint =
                root.requireLowerSha256("declarationProfileFingerprint")
            val runtimeCapabilitiesFingerprint =
                root.requireLowerSha256("runtimeCapabilitiesFingerprint")
            val callerInventoryFingerprint =
                root.requireLowerSha256("callerInventoryFingerprint")
            root.requireString("configKind").requireEqual(
                if (rawConfigAuthority == null) authority.profileAuthority.configKind
                else TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
                "Request manifest config kind does not match its revision.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            val rawConfigDescriptor = rawConfigAuthority?.let { expected ->
                val configObject = root["config"] as? Map<*, *> ?: fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest field 'config' must be an object.",
                )
                val config = configObject.toStringKeyMap("config")
                config.requireExactKeys(CONFIG_KEYS, "config")
                val path = config.requireString("path")
                path.requireEqual(
                    R2ControlledTsConfigPolicy.CONFIG_PATH,
                    "Request manifest raw config path is not supported.",
                    TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                )
                path.requireEqual(
                    expected.path,
                    "Request manifest raw config path does not match the control plane.",
                    TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                )
                val size = config.requireNonNegativeLong("size")
                if (size > TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong()) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                        "Request manifest raw config exceeds the controlled byte limit.",
                    )
                }
                if (size != expected.size) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest raw config size does not match the control plane.",
                    )
                }
                val sha256 = config.requireLowerSha256("sha256")
                if (!R2Digests.equal(sha256, expected.sha256)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest raw config SHA-256 does not match the control plane.",
                    )
                }
                val policyRevision = config.requireNonNegativeInt("policyRevision")
                if (
                    policyRevision != TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION ||
                    policyRevision != expected.policyRevision
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest raw config policy revision is not supported.",
                    )
                }
                val policyFingerprint = config.requireLowerSha256("policyFingerprint")
                if (
                    !R2Digests.equal(policyFingerprint, R2ControlledTsConfigPolicy.fingerprint) ||
                    !R2Digests.equal(policyFingerprint, expected.policyFingerprint)
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest raw config policy fingerprint is not supported.",
                    )
                }
                R2RawProjectConfigDescriptor(
                    path = path,
                    size = size,
                    sha256 = sha256,
                    policyRevision = policyRevision,
                    policyFingerprint = policyFingerprint,
                )
            }
            val packageMetadataInventory = packageMetadataAuthority?.let { expected ->
                val packageMetadataObject = root["packageMetadata"] as? Map<*, *> ?: fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest field 'packageMetadata' must be an object.",
                )
                val packageMetadata =
                    packageMetadataObject.toStringKeyMap("packageMetadata")
                packageMetadata.requireExactKeys(
                    PACKAGE_METADATA_KEYS,
                    "packageMetadata",
                )
                packageMetadata.requireNonNegativeInt("schemaRevision").requireEqual(
                    R2PackageMetadataPolicy.SCHEMA_REVISION,
                    "Request manifest package metadata schema revision is not supported.",
                    TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                )
                val policyRevision =
                    packageMetadata.requireNonNegativeInt("policyRevision")
                if (
                    policyRevision != R2PackageMetadataPolicy.POLICY_REVISION ||
                    policyRevision != expected.policyRevision
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest package metadata policy revision is not supported.",
                    )
                }
                val policyFingerprint =
                    packageMetadata.requireLowerSha256("policyFingerprint")
                if (
                    !R2Digests.equal(
                        policyFingerprint,
                        R2PackageMetadataPolicy.fingerprint,
                    ) ||
                    !R2Digests.equal(policyFingerprint, expected.policyFingerprint)
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest package metadata policy fingerprint is not supported.",
                    )
                }
                val declaredInventoryFingerprint =
                    packageMetadata.requireLowerSha256("inventoryFingerprint")
                val dependencyRoots =
                    packageMetadata.requireList("dependencyRoots").mapIndexed { index, value ->
                        value as? String ?: fail(
                            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            "Request manifest packageMetadata.dependencyRoots[$index] must be " +
                                "a string.",
                        )
                    }
                requireStrictlySortedUnique(
                    dependencyRoots,
                    "Request manifest packageMetadata.dependencyRoots",
                )
                val packageFiles =
                    packageMetadata.requireList("files").mapIndexed { index, value ->
                        val fileObject = value as? Map<*, *> ?: fail(
                            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            "Request manifest packageMetadata.files[$index] must be an object.",
                        )
                        val file = fileObject.toStringKeyMap(
                            "packageMetadata.files[$index]",
                        )
                        file.requireExactKeys(
                            PACKAGE_METADATA_FILE_KEYS,
                            "packageMetadata.files[$index]",
                        )
                        try {
                            R2PackageMetadataFileDescriptor(
                                path = file.requireString("path"),
                                size = file.requireNonNegativeLong("size"),
                                sha256 = file.requireLowerSha256("sha256"),
                            )
                        } catch (error: R2PackageMetadataPolicyException) {
                            failPackageMetadataPolicy(error)
                        }
                    }
                requireStrictlySortedUnique(
                    packageFiles.map(R2PackageMetadataFileDescriptor::path),
                    "Request manifest packageMetadata.files",
                )
                val inventory = try {
                    R2PackageMetadataInventory.create(dependencyRoots, packageFiles)
                } catch (error: R2PackageMetadataPolicyException) {
                    failPackageMetadataPolicy(error)
                }
                if (!R2Digests.equal(declaredInventoryFingerprint, inventory.fingerprint)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest package metadata inventory fingerprint does not " +
                            "match its dependency roots and files.",
                    )
                }
                if (!R2Digests.equal(declaredInventoryFingerprint, expected.fingerprint)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest package metadata inventory fingerprint does not " +
                            "match the control plane.",
                    )
                }
                inventory
            }
            val packageTargetInventory = packageTargetAuthority?.let { expected ->
                val metadataInventory = requireNotNull(packageMetadataInventory) {
                    "Package target revision requires parsed package metadata."
                }
                val packageTargetsObject = root["packageTargets"] as? Map<*, *> ?: fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest field 'packageTargets' must be an object.",
                )
                val packageTargets = packageTargetsObject.toStringKeyMap("packageTargets")
                packageTargets.requireExactKeys(PACKAGE_TARGET_KEYS, "packageTargets")
                packageTargets.requireNonNegativeInt("schemaRevision").requireEqual(
                    R2PackageTargetPolicy.SCHEMA_REVISION,
                    "Request manifest package target schema revision is not supported.",
                    TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                )
                val policyRevision = packageTargets.requireNonNegativeInt("policyRevision")
                if (
                    policyRevision != R2PackageTargetPolicy.POLICY_REVISION ||
                    policyRevision != expected.policyRevision
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest package target policy revision is not supported.",
                    )
                }
                val policyFingerprint = packageTargets.requireLowerSha256("policyFingerprint")
                if (
                    !R2Digests.equal(policyFingerprint, R2PackageTargetPolicy.fingerprint) ||
                    !R2Digests.equal(policyFingerprint, expected.policyFingerprint)
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest package target policy fingerprint is not supported.",
                    )
                }
                val metadataFingerprint =
                    packageTargets.requireLowerSha256("packageMetadataInventoryFingerprint")
                if (
                    !R2Digests.equal(metadataFingerprint, metadataInventory.fingerprint) ||
                    !R2Digests.equal(
                        metadataFingerprint,
                        expected.packageMetadataInventoryFingerprint,
                    )
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest package targets are bound to different package " +
                            "metadata.",
                    )
                }
                val declaredInventoryFingerprint =
                    packageTargets.requireLowerSha256("inventoryFingerprint")
                val targetFiles = packageTargets.requireList("files").mapIndexed { index, value ->
                    val fileObject = value as? Map<*, *> ?: fail(
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        "Request manifest packageTargets.files[$index] must be an object.",
                    )
                    val file = fileObject.toStringKeyMap("packageTargets.files[$index]")
                    file.requireExactKeys(
                        PACKAGE_TARGET_FILE_KEYS,
                        "packageTargets.files[$index]",
                    )
                    try {
                        R2PackageTargetFileDescriptor(
                            path = file.requireString("path"),
                            size = file.requireNonNegativeLong("size"),
                            sha256 = file.requireLowerSha256("sha256"),
                        )
                    } catch (error: R2PackageTargetPolicyException) {
                        failPackageTargetPolicy(error)
                    }
                }
                requireStrictlySortedUnique(
                    targetFiles.map(R2PackageTargetFileDescriptor::path),
                    "Request manifest packageTargets.files",
                )
                val inventory = try {
                    R2PackageTargetInventory.create(
                        packageMetadataInventory = metadataInventory,
                        files = targetFiles,
                        boundPackageMetadataInventoryFingerprint = metadataFingerprint,
                    )
                } catch (error: R2PackageTargetPolicyException) {
                    failPackageTargetPolicy(error)
                }
                if (!R2Digests.equal(declaredInventoryFingerprint, inventory.fingerprint)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest package target inventory fingerprint does not match " +
                            "its files.",
                    )
                }
                if (!R2Digests.equal(declaredInventoryFingerprint, expected.fingerprint)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest package target inventory fingerprint does not match " +
                            "the control plane.",
                    )
                }
                inventory
            }
            val nodePackageTargetV2Inventory = nodePackageTargetV2Authority?.let { expected ->
                val metadataInventory = requireNotNull(packageMetadataInventory) {
                    "Node package-target v2 revision requires parsed package metadata."
                }
                val packageTargetsObject = root["packageTargets"] as? Map<*, *> ?: fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest field 'packageTargets' must be an object.",
                )
                val packageTargets = packageTargetsObject.toStringKeyMap("packageTargets")
                packageTargets.requireExactKeys(PACKAGE_TARGET_KEYS, "packageTargets")
                packageTargets.requireNonNegativeInt("schemaRevision").requireEqual(
                    R2NodePackageTargetPolicyV2.SCHEMA_REVISION,
                    "Request manifest Node package-target v2 schema revision is not supported.",
                    TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                )
                val policyRevision = packageTargets.requireNonNegativeInt("policyRevision")
                if (
                    policyRevision != R2NodePackageTargetPolicyV2.POLICY_REVISION ||
                    policyRevision != expected.policyRevision
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest Node package-target v2 policy revision is not supported.",
                    )
                }
                val policyFingerprint = packageTargets.requireLowerSha256("policyFingerprint")
                if (
                    !R2Digests.equal(
                        policyFingerprint,
                        R2NodePackageTargetPolicyV2.fingerprint,
                    ) ||
                    !R2Digests.equal(policyFingerprint, expected.policyFingerprint)
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                        "Request manifest Node package-target v2 policy fingerprint is not supported.",
                    )
                }
                val metadataFingerprint =
                    packageTargets.requireLowerSha256("packageMetadataInventoryFingerprint")
                if (
                    !R2Digests.equal(metadataFingerprint, metadataInventory.fingerprint) ||
                    !R2Digests.equal(
                        metadataFingerprint,
                        expected.packageMetadataInventoryFingerprint,
                    )
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest Node package-target v2 is bound to different package metadata.",
                    )
                }
                val declaredInventoryFingerprint =
                    packageTargets.requireLowerSha256("inventoryFingerprint")
                val targetFiles = packageTargets.requireList("files").mapIndexed { index, value ->
                    val fileObject = value as? Map<*, *> ?: fail(
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        "Request manifest packageTargets.files[$index] must be an object.",
                    )
                    val file = fileObject.toStringKeyMap("packageTargets.files[$index]")
                    file.requireExactKeys(
                        PACKAGE_TARGET_FILE_KEYS,
                        "packageTargets.files[$index]",
                    )
                    try {
                        R2NodePackageTargetV2FileDescriptor(
                            path = file.requireString("path"),
                            size = file.requireNonNegativeLong("size"),
                            sha256 = file.requireLowerSha256("sha256"),
                        )
                    } catch (error: R2PackageTargetPolicyException) {
                        failPackageTargetPolicy(error)
                    }
                }
                requireStrictlySortedUnique(
                    targetFiles.map(R2NodePackageTargetV2FileDescriptor::path),
                    "Request manifest Node package-target v2 files",
                )
                val inventory = try {
                    R2NodePackageTargetV2Inventory.create(
                        packageMetadataInventory = metadataInventory,
                        files = targetFiles,
                        boundPackageMetadataInventoryFingerprint = metadataFingerprint,
                    )
                } catch (error: R2PackageTargetPolicyException) {
                    failPackageTargetPolicy(error)
                }
                if (!R2Digests.equal(declaredInventoryFingerprint, inventory.fingerprint)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest Node package-target v2 fingerprint does not match its files.",
                    )
                }
                if (!R2Digests.equal(declaredInventoryFingerprint, expected.fingerprint)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest Node package-target v2 fingerprint does not match the control plane.",
                    )
                }
                inventory
            }

            requestId.requireEqual(
                authority.requestId,
                "Request manifest requestId does not match the control plane.",
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            )
            projectId.requireEqual(
                authority.projectId,
                "Request manifest projectId does not match the control plane.",
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            )
            entrySourceUri.requireEqual(
                authority.entrySourceUri,
                "Request manifest entrySourceUri does not match the control plane.",
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            )
            targetProfile.requireEqual(
                authority.profile.contractValue,
                "Request manifest target profile does not match the control plane.",
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            )
            compilationMode.requireEqual(
                authority.compilationMode,
                "Request manifest compilation mode does not match the control plane.",
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            )
            compilerPackage.requireEqual(
                COMPILER_PACKAGE,
                "Request manifest compiler package is not supported.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            compilerVersion.requireEqual(
                authority.compilerVersion,
                "Request manifest compiler version does not match the trusted compiler.",
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            )
            compilerDistributionFingerprint.requireDigestEqual(
                authority.compilerDistributionFingerprint,
                "Request manifest compiler distribution does not match the trusted compiler.",
            )
            declarationsFingerprint.requireDigestEqual(
                authority.declarationsFingerprint,
                "Request manifest declarations do not match the trusted declaration set.",
            )
            declarationProfileFingerprint.requireDigestEqual(
                authority.declarationProfileFingerprint,
                "Request manifest declaration profile does not match the control plane.",
            )
            runtimeCapabilitiesFingerprint.requireDigestEqual(
                authority.runtimeCapabilitiesFingerprint,
                "Request manifest runtime capabilities do not match the control plane.",
            )

            validateSourceArchivePath(
                path = entryPath,
                label = "entryPath",
                profileAuthority = authority.profileAuthority,
            )
            if (
                authority.profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
            ) {
                val expectedEntrySourceUri = try {
                    R2GeneratedModuleMappingPolicy.originalSourceUri(
                        projectId = projectId,
                        sourcePath = "/$entryPath",
                    )
                } catch (error: R2GeneratedModuleMappingException) {
                    fail(
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        "Node revision 2 project/source identity cannot produce an exact " +
                            "original-source URI.",
                        error,
                    )
                }
                entrySourceUri.requireEqual(
                    expectedEntrySourceUri,
                    "Node revision 2 entrySourceUri does not match its exact project/source " +
                        "mapping.",
                    TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                )
            }
            val rootPaths = root.requireList("rootPaths").mapIndexed { index, value ->
                val rootPath = value as? String ?: fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest rootPaths[$index] must be a string.",
                )
                validateSourceRootPath(
                    path = rootPath,
                    label = "rootPaths[$index]",
                    profileAuthority = authority.profileAuthority,
                )
                rootPath
            }
            requireStrictlySortedUnique(rootPaths, "Request manifest rootPaths")
            if (rootPaths.isEmpty()) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest must contain at least one project root.",
                )
            }
            if (entryPath !in rootPaths) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest entryPath must be one of its rootPaths.",
                )
            }

            val files = root.requireList("files").mapIndexed { index, value ->
                val fileObject = value as? Map<*, *> ?: fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest files[$index] must be an object.",
                )
                val typedObject = fileObject.toStringKeyMap("files[$index]")
                typedObject.requireExactKeys(FILE_KEYS, "files[$index]")
                val path = typedObject.requireString("path")
                validateSourceArchivePath(
                    path = path,
                    label = "files[$index].path",
                    profileAuthority = authority.profileAuthority,
                )
                val size = typedObject.requireNonNegativeLong("size")
                if (size > MAXIMUM_SOURCE_FILE_BYTES) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                        "Request manifest file exceeds the archive transport single-file limit: " +
                            "$path.",
                    )
                }
                R2ProjectArchiveFile(
                    path = path,
                    size = size,
                    sha256 = typedObject.requireLowerSha256("sha256"),
                )
            }
            if (files.isEmpty()) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest file inventory must not be empty.",
                )
            }
            val reservedArchiveFiles =
                1 +
                    (if (rawConfigAuthority == null) 0 else 1) +
                    (packageMetadataInventory?.files?.size ?: 0) +
                    (packageTargetInventory?.files?.size ?: 0) +
                    (nodePackageTargetV2Inventory?.files?.size ?: 0)
            if (reservedArchiveFiles > TypeScriptCompilerContract.MAX_INPUT_FILES) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Request manifest package metadata leaves no source inventory capacity.",
                )
            }
            val maximumSourceFiles =
                TypeScriptCompilerContract.MAX_INPUT_FILES - reservedArchiveFiles
            if (files.size > maximumSourceFiles) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Request manifest file count exceeds the archive transport inventory limit.",
                )
            }
            requireStrictlySortedUnique(
                files.map(R2ProjectArchiveFile::path),
                "Request manifest files",
            )
            val expectedRoots = files
                .map(R2ProjectArchiveFile::path)
                .filter { path ->
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        path = path,
                        profileAuthority = authority.profileAuthority,
                    )
                }
            if (rootPaths != expectedRoots) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest rootPaths do not exactly cover the executable sources " +
                        "admitted by its exact profile.",
                )
            }
            val declaredTotal = files.fold(0L) { total, file ->
                if (total > MAXIMUM_SOURCE_BYTES - file.size) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                        "Request manifest files exceed the archive transport source-byte limit.",
                    )
                }
                total + file.size
            }
            if (declaredTotal > MAXIMUM_SOURCE_BYTES) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Request manifest files exceed the archive transport source-byte limit.",
                )
            }
            val calculatedInventoryFingerprint = R2InputArchiveSchemaV1.callerInventoryFingerprint(
                sources = files,
                rawConfig = rawConfigDescriptor,
                packageMetadata = packageMetadataInventory,
                packageTargets = packageTargetInventory,
                nodePackageTargetsV2 = nodePackageTargetV2Inventory,
            )
            if (!R2Digests.equal(callerInventoryFingerprint, calculatedInventoryFingerprint)) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                    "Request manifest caller inventory fingerprint does not match its exact " +
                        "caller-owned input domains.",
                )
            }

            return R2ProvisionalRequestManifest(
                requestId = requestId,
                projectId = projectId,
                entrySourceUri = entrySourceUri,
                entryPath = entryPath,
                profileAuthority = authority.profileAuthority,
                compilationMode = compilationMode,
                compilerVersion = compilerVersion,
                compilerDistributionFingerprint = compilerDistributionFingerprint,
                declarationsFingerprint = declarationsFingerprint,
                declarationProfileFingerprint = declarationProfileFingerprint,
                runtimeCapabilitiesFingerprint = runtimeCapabilitiesFingerprint,
                callerInventoryFingerprint = callerInventoryFingerprint,
                rawConfigDescriptor = rawConfigDescriptor,
                packageMetadataInventory = packageMetadataInventory,
                packageTargetInventory = packageTargetInventory,
                nodePackageTargetV2Inventory = nodePackageTargetV2Inventory,
                rootPaths = rootPaths,
                files = files,
            )
        }

        internal fun inventoryFingerprint(files: List<R2ProjectArchiveFile>): String =
            R2InputArchiveSchemaV1.callerInventoryFingerprint(
                sources = files,
                rawConfig = null,
                packageMetadata = null,
                packageTargets = null,
                nodePackageTargetsV2 = null,
            )

        private fun parseJson(bytes: ByteArray): Map<String, Any?> = try {
            R2StrictJson.parseObject(bytes)
        } catch (error: R2ProjectArchiveException) {
            throw error
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Input archive manifest is malformed: ${error.message.orEmpty()}",
                error,
            )
        }

        private fun validateSourceArchivePath(
            path: String,
            label: String,
            profileAuthority: R2CompilationProfileAuthority,
        ) {
            validateCanonicalArchivePath(path, label)
            if (!path.startsWith(SOURCES_ROOT)) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                    "$label must be below $SOURCES_ROOT.",
                )
            }
            R2ProjectSourceKindAdmission.sourcePathRejection(
                path = path,
                profileAuthority = profileAuthority,
            )?.let { rejection ->
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                    "$label uses a reserved compiler escape path: ${rejection.contractValue}.",
                )
            }
        }

        private fun validateSourceRootPath(
            path: String,
            label: String,
            profileAuthority: R2CompilationProfileAuthority,
        ) {
            validateSourceArchivePath(path, label, profileAuthority)
            if (
                !R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    path = path,
                    profileAuthority = profileAuthority,
                )
            ) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                    "$label must identify an executable source admitted by its exact profile.",
                )
            }
        }

        private fun validateCanonicalArchivePath(
            path: String,
            label: String,
        ) {
            if (
                path.isEmpty() ||
                path.length > TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES ||
                path.startsWith('/') ||
                '\\' in path ||
                path.any { character -> character.code !in PRINTABLE_ASCII }
            ) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                    "$label is not a bounded printable-ASCII relative path.",
                )
            }
            val segments = path.split('/')
            if (
                segments.size > TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH ||
                segments.any { segment ->
                    segment.isEmpty() || segment == "." || segment == ".."
                }
            ) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                    "$label is not a canonical relative path.",
                )
            }
        }

        private fun requireStrictlySortedUnique(
            paths: List<String>,
            label: String,
        ) {
            val folded = HashMap<String, String>(paths.size)
            var previous: String? = null
            paths.forEach { path ->
                previous?.let { previousPath ->
                    if (previousPath >= path) {
                        fail(
                            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                            "$label must be strictly sorted with no duplicates.",
                        )
                    }
                }
                val collisionKey = path.asciiCaseFold()
                val existing = folded.putIfAbsent(collisionKey, path)
                if (existing != null) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                        "$label contains an ASCII case-fold collision: $existing / $path.",
                    )
                }
                previous = path
            }
        }

        private fun Map<String, Any?>.requireExactKeys(
            expected: Set<String>,
            label: String,
        ) {
            if (keys != expected) {
                val missing = expected.filterNot(keys::contains)
                val extra = keys.filterNot(expected::contains)
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "$label keys do not match the provisional schema; " +
                        "missing=${missing.size}${missing.firstOrNull().describeSample()}, " +
                        "extra=${extra.size}${extra.firstOrNull().describeSample()}.",
                )
            }
        }

        private fun Map<String, Any?>.requireString(key: String): String =
            this[key] as? String ?: fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Request manifest field '$key' must be a string.",
            )

        private fun Map<String, Any?>.requireList(key: String): List<*> =
            this[key] as? List<*> ?: fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Request manifest field '$key' must be an array.",
            )

        private fun Map<String, Any?>.requireNonNegativeInt(key: String): Int {
            val value = requireNonNegativeLong(key)
            if (value > Int.MAX_VALUE) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest field '$key' exceeds Int range.",
                )
            }
            return value.toInt()
        }

        private fun Map<String, Any?>.requireNonNegativeLong(key: String): Long {
            val number = this[key] as? R2StrictJson.NumberToken ?: fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Request manifest field '$key' must be an integer.",
            )
            if (!NON_NEGATIVE_INTEGER.matches(number.source)) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest field '$key' must be a canonical non-negative integer.",
                )
            }
            return number.source.toLongOrNull() ?: fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Request manifest field '$key' exceeds Long range.",
            )
        }

        private fun Map<String, Any?>.requireLowerSha256(key: String): String {
            val value = requireString(key)
            return try {
                R2Digests.requireLowerSha256(value, "Request manifest field '$key'")
            } catch (error: IllegalArgumentException) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    error.message.orEmpty(),
                    error,
                )
            }
        }

        private fun Map<*, *>.toStringKeyMap(label: String): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>(size)
            forEach { (key, value) ->
                val stringKey = key as? String ?: fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    "Request manifest $label contains a non-string key.",
                )
                result[stringKey] = value
            }
            return result
        }

        private fun String.requireEqual(
            expected: String,
            message: String,
            errorCode: String,
        ) {
            if (this != expected) {
                fail(errorCode, message)
            }
        }

        private fun Int.requireEqual(
            expected: Int,
            message: String,
            errorCode: String,
        ) {
            if (this != expected) {
                fail(errorCode, message)
            }
        }

        private fun String?.describeSample(): String = when {
            this == null -> ""
            length <= MAXIMUM_ERROR_SAMPLE_CHARS -> ", first='$this'"
            else -> ", first='${take(MAXIMUM_ERROR_SAMPLE_CHARS)}...' ($length chars)"
        }

        private fun String.requireDigestEqual(
            expected: String,
            message: String,
        ) {
            if (!R2Digests.equal(expected, this)) {
                fail(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH, message)
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

        internal const val MAXIMUM_MANIFEST_BYTES = 512L * 1024L
        internal const val MAXIMUM_CONTROLLED_TSCONFIG_SOURCE_FILES =
            TypeScriptCompilerContract.MAX_INPUT_FILES - 2
        internal const val MAXIMUM_SOURCE_FILE_BYTES =
            TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES
        internal const val MAXIMUM_SOURCE_BYTES =
            TypeScriptCompilerContract.MAX_INPUT_BYTES
        private const val COMPILER_PACKAGE = "typescript"
        private const val SOURCES_ROOT = "sources/"
        private const val MAXIMUM_ERROR_SAMPLE_CHARS = 128
        private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
        private val PRINTABLE_ASCII = ' '.code..'~'.code
        private val NON_NEGATIVE_INTEGER = Regex("0|[1-9][0-9]*")
        private val FILE_KEYS = setOf("path", "sha256", "size")
        private val CONFIG_KEYS = setOf(
            "path",
            "size",
            "sha256",
            "policyRevision",
            "policyFingerprint",
        )
        private val PACKAGE_METADATA_FILE_KEYS = setOf("path", "sha256", "size")
        private val PACKAGE_TARGET_FILE_KEYS = setOf("path", "sha256", "size")
        private val PACKAGE_METADATA_KEYS = setOf(
            "schemaRevision",
            "policyRevision",
            "policyFingerprint",
            "inventoryFingerprint",
            "dependencyRoots",
            "files",
        )
        private val PACKAGE_TARGET_KEYS = setOf(
            "schemaRevision",
            "policyRevision",
            "policyFingerprint",
            "packageMetadataInventoryFingerprint",
            "inventoryFingerprint",
            "files",
        )
        private val TOP_LEVEL_KEYS = setOf(
            "schema",
            "revision",
            "inputQuotaRevision",
            "contractVersion",
            "contractHash",
            "inputArchiveTransportVersion",
            "requestId",
            "projectId",
            "entrySourceUri",
            "entryPath",
            "targetProfile",
            "compilationMode",
            "profileSchemaRevision",
            "profileRevision",
            "runtimeCapabilitySchemaRevision",
            "configSchemaRevision",
            "runtimeCapabilitiesFingerprint",
            "compilerPackage",
            "compilerVersion",
            "compilerDistributionFingerprint",
            "declarationsFingerprint",
            "declarationProfileFingerprint",
            "configKind",
            "callerInventoryFingerprint",
            "rootPaths",
            "files",
        )
        private val TOP_LEVEL_KEYS_WITH_CONFIG = TOP_LEVEL_KEYS + "config"
        private val TOP_LEVEL_KEYS_WITH_PACKAGE_METADATA =
            TOP_LEVEL_KEYS + "packageMetadata"
        private val TOP_LEVEL_KEYS_WITH_PACKAGE_TARGETS =
            TOP_LEVEL_KEYS_WITH_PACKAGE_METADATA + "packageTargets"
        private val TOP_LEVEL_KEYS_WITH_CONFIG_AND_PACKAGE_METADATA =
            TOP_LEVEL_KEYS_WITH_CONFIG + "packageMetadata"
        private val TOP_LEVEL_KEYS_WITH_CONFIG_AND_PACKAGE_TARGETS =
            TOP_LEVEL_KEYS_WITH_CONFIG_AND_PACKAGE_METADATA + "packageTargets"
    }
}

internal class R2AdaptedProjectArchive(
    val snapshot: R2ProjectInputSnapshot,
    val manifest: R2ProvisionalRequestManifest,
    val archiveSha256: String,
    val requestManifestSha256: String,
) : Closeable {
    val acceptedInputAuthority: R2AcceptedInputAuthorityV1 =
        requireNotNull(snapshot.acceptedInputAuthority) {
            "Adapted project archive has no frozen accepted-input authority."
        }

    override fun close() {
        snapshot.close()
    }
}

internal inline fun <T> publishR2PrivateInputOwnership(
    ownedInput: Closeable,
    publish: () -> T,
): T = try {
    publish()
} catch (failure: Throwable) {
    try {
        ownedInput.close()
    } catch (cleanupFailure: Throwable) {
        throw R2PrivateInputCleanupUnprovenException(
            "R2 private input cleanup after ownership publication failure could not be proven.",
            failure,
        ).also { unproven ->
            unproven.addSuppressed(cleanupFailure)
        }
    }
    throw failure
}

internal object R2ProjectArchiveAdapter {

    fun adapt(
        authority: R2ProjectArchiveAuthority,
        actualArchiveSha256: String,
        archiveEntries: Map<String, ByteArray>,
        trustedDeclarations: R2TrustedDeclarations,
        trustedRuntimeCapabilitiesFingerprint: String,
    ): R2AdaptedProjectArchive {
        validateAuthority(
            authority = authority,
            actualArchiveSha256 = actualArchiveSha256,
            trustedDeclarations = trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                trustedRuntimeCapabilitiesFingerprint,
        )

        val materializationLimits = ZipArchiveV1Limits.PROVISIONAL_MATERIALIZED_PROJECT
        if (archiveEntries.size > materializationLimits.maximumFiles) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                "Input archive exceeds the provisional materialized file-count limit.",
            )
        }
        val ownedArchiveEntries = LinkedHashMap<String, ByteArray>(archiveEntries.size)
        try {
            var materializedBytes = 0L
            archiveEntries.forEach { (path, sourceBytes) ->
                if (sourceBytes.size.toLong() > materializationLimits.maximumSingleFileBytes) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                        "Input archive entry exceeds the provisional materialized file limit.",
                    )
                }
                if (
                    materializedBytes >
                    materializationLimits.maximumTotalUncompressedBytes - sourceBytes.size
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                        "Input archive exceeds the provisional materialized byte limit.",
                    )
                }
                materializedBytes += sourceBytes.size
                val ownedBytes = sourceBytes.copyOf()
                ownedArchiveEntries[path] = ownedBytes
            }

        val manifestBytes =
            ownedArchiveEntries[R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH]
            ?: fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Input archive does not contain request-manifest.json.",
            )
        val actualManifestSha256 = R2Digests.sha256(manifestBytes)
        if (!R2Digests.equal(authority.requestManifestSha256, actualManifestSha256)) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest SHA-256 does not match the control plane.",
            )
        }
        val manifest = R2ProvisionalRequestManifest.parse(manifestBytes, authority)
        validateActualArchiveCompilerEscapePaths(
            paths = ownedArchiveEntries.keys,
            profileAuthority = manifest.profileAuthority,
            packageMetadataInventory = manifest.packageMetadataInventory,
            packageTargetInventory = manifest.packageTargetInventory,
            nodePackageTargetV2Inventory = manifest.nodePackageTargetV2Inventory,
        )
        val expectedPaths = buildSet {
            add(R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH)
            manifest.rawConfigDescriptor?.let { config -> add(config.path) }
            manifest.packageMetadataInventory?.files?.forEach { file -> add(file.path) }
            manifest.packageTargetInventory?.files?.forEach { file -> add(file.path) }
            manifest.nodePackageTargetV2Inventory?.files?.forEach { file -> add(file.path) }
            manifest.files.forEach { file -> add(file.path) }
        }
        if (ownedArchiveEntries.keys != expectedPaths) {
            val missing = expectedPaths.filterNot(ownedArchiveEntries::containsKey)
            val extra = ownedArchiveEntries.keys.filterNot(expectedPaths::contains)
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest does not exactly cover the input archive; " +
                    "missing=${missing.size}${missing.firstOrNull().describeArchivePath()}, " +
                    "extra=${extra.size}${extra.firstOrNull().describeArchivePath()}.",
            )
        }

        val snapshotLimits = R2ProjectSnapshotLimits.ORDINARY_COMPILATION
        if (trustedDeclarations.files.size > snapshotLimits.maximumFiles) {
            fail(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Local declarations exceed the ordinary compilation file-count limit.",
            )
        }
        val callerPayloadCount =
            manifest.files.size +
                (manifest.packageMetadataInventory?.files?.size ?: 0) +
                (manifest.packageTargetInventory?.files?.size ?: 0) +
                (manifest.nodePackageTargetV2Inventory?.files?.size ?: 0) +
                if (manifest.rawConfigDescriptor == null) 0 else 1
        if (callerPayloadCount > snapshotLimits.maximumFiles - trustedDeclarations.files.size) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                "Project input and local declarations exceed the ordinary compilation " +
                    "file-count limit.",
            )
        }
        var snapshotBytes = 0L
        trustedDeclarations.files.forEach { declaration ->
            if (declaration.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "A local declaration exceeds the ordinary compilation single-file limit.",
                )
            }
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - declaration.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Local declarations exceed the ordinary compilation byte limit.",
                )
            }
            snapshotBytes += declaration.size
        }
        manifest.rawConfigDescriptor?.let { config ->
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - config.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Project input and local declarations exceed the ordinary compilation " +
                        "byte limit.",
                )
            }
            snapshotBytes += config.size
        }
        manifest.packageMetadataInventory?.files?.forEach { file ->
            if (file.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package metadata exceeds the ordinary compilation single-file limit.",
                )
            }
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package metadata, project files and local declarations exceed the " +
                        "ordinary compilation byte limit.",
                )
            }
            snapshotBytes += file.size
        }
        manifest.packageTargetInventory?.files?.forEach { file ->
            if (file.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package target exceeds the ordinary compilation single-file limit.",
                )
            }
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package metadata, package targets, project files and local declarations " +
                        "exceed the ordinary compilation byte limit.",
                )
            }
            snapshotBytes += file.size
        }
        manifest.nodePackageTargetV2Inventory?.files?.forEach { file ->
            if (file.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Node package-target v2 exceeds the ordinary compilation single-file limit.",
                )
            }
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Node package-target v2, project files and local declarations exceed the " +
                        "ordinary compilation byte limit.",
                )
            }
            snapshotBytes += file.size
        }
        manifest.files.forEach { file ->
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Project plus local declarations exceed the ordinary compilation byte limit.",
                )
            }
            snapshotBytes += file.size
        }

        val snapshotFiles = ArrayList<R2ProjectTextFile>(
            manifest.files.size + trustedDeclarations.files.size,
        )
        manifest.files.forEach { file ->
            val bytes = requireNotNull(ownedArchiveEntries[file.path])
            if (bytes.size.toLong() != file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                    "Request manifest size does not match actual bytes: ${file.path}.",
                )
            }
            val actualSha256 = R2Digests.sha256(bytes)
            if (!R2Digests.equal(file.sha256, actualSha256)) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                    "Request manifest SHA-256 does not match actual bytes: ${file.path}.",
                )
            }
            val text = try {
                R2Utf8.decode(bytes, "Project file ${file.path}")
            } catch (error: IllegalArgumentException) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    error.message.orEmpty(),
                    error,
                )
            }
            snapshotFiles += R2ProjectTextFile(path = "/${file.path}", text = text)
        }
        trustedDeclarations.files.forEach { declaration ->
            snapshotFiles += R2ProjectTextFile(
                path = "/${declaration.archivePath}",
                text = declaration.text,
            )
        }

        val rawConfigBytes = manifest.rawConfigDescriptor?.let { config ->
            val bytes = checkNotNull(ownedArchiveEntries[config.path]) {
                "Exact archive inventory was checked before raw config validation."
            }
            validateRawConfigEntry(
                descriptor = config,
                actualSize = bytes.size.toLong(),
                actualSha256 = R2Digests.sha256(bytes),
            )
            bytes
        }
        val packageMetadataBytes = manifest.packageMetadataInventory?.files?.map { file ->
            val bytes = checkNotNull(ownedArchiveEntries[file.path]) {
                "Exact archive inventory was checked before package metadata validation."
            }
            validatePackageMetadataEntry(
                descriptor = file,
                actualSize = bytes.size.toLong(),
                actualSha256 = R2Digests.sha256(bytes),
                bytes = bytes,
            )
            bytes
        }.orEmpty()
        val packageTargetBytes = manifest.packageTargetInventory?.files?.map { file ->
            val bytes = checkNotNull(ownedArchiveEntries[file.path]) {
                "Exact archive inventory was checked before package target validation."
            }
            validatePackageTargetEntry(
                descriptor = file,
                actualSize = bytes.size.toLong(),
                actualSha256 = R2Digests.sha256(bytes),
            )
            try {
                R2Utf8.decode(bytes, "Package target ${file.path}")
            } catch (error: IllegalArgumentException) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    error.message.orEmpty(),
                    error,
                )
            }
            bytes
        }.orEmpty()
        val nodePackageTargetV2Bytes = manifest.nodePackageTargetV2Inventory?.files?.map { file ->
            val bytes = checkNotNull(ownedArchiveEntries[file.path]) {
                "Exact archive inventory was checked before Node package-target v2 validation."
            }
            validateNodePackageTargetV2Entry(
                descriptor = file,
                actualSize = bytes.size.toLong(),
                actualSha256 = R2Digests.sha256(bytes),
            )
            try {
                R2Utf8.decode(bytes, "Node package-target v2 ${file.path}")
            } catch (error: IllegalArgumentException) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    error.message.orEmpty(),
                    error,
                )
            }
            bytes
        }.orEmpty()

        val acceptedInputAuthority = try {
            R2InputArchiveSchemaV1.acceptedAuthority(
                callerInventoryFingerprint = manifest.callerInventoryFingerprint,
                sources = manifest.files,
                trustedDeclarations = trustedDeclarations,
                rawConfig = manifest.rawConfigDescriptor,
                packageMetadata = manifest.packageMetadataInventory,
                packageTargets = manifest.packageTargetInventory,
                nodePackageTargetsV2 = manifest.nodePackageTargetV2Inventory,
            )
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Accepted input inventory authority could not be derived.",
                error,
            )
        }

        val snapshot = try {
            R2ProjectSnapshot.create(
                files = snapshotFiles,
                rootNames = manifest.rootPaths.map { path -> "/$path" },
                profileAuthority = manifest.profileAuthority,
                limits = snapshotLimits,
                entryPath = "/${manifest.entryPath}",
                acceptedProjectId = manifest.projectId,
                rawConfigDescriptor = manifest.rawConfigDescriptor,
                rawConfigBytes = rawConfigBytes,
                packageMetadataInventory = manifest.packageMetadataInventory,
                packageMetadataBytes = packageMetadataBytes,
                packageTargetInventory = manifest.packageTargetInventory,
                packageTargetBytes = packageTargetBytes,
                nodePackageTargetV2Inventory = manifest.nodePackageTargetV2Inventory,
                nodePackageTargetV2Bytes = nodePackageTargetV2Bytes,
                acceptedInputAuthority = acceptedInputAuthority,
            )
        } catch (error: R2NodeModuleOutputPolicyException) {
            fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Node revision 2 package format is not accepted: " +
                    "${error.policyCode.contractValue}.",
                error,
            )
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                "Archive could not be adapted to the project VFS: " +
                    error.message.orEmpty(),
                error,
            )
        }
            return R2AdaptedProjectArchive(
                snapshot = snapshot,
                manifest = manifest,
                archiveSha256 = actualArchiveSha256,
                requestManifestSha256 = actualManifestSha256,
            )
        } finally {
            ownedArchiveEntries.values.forEach { bytes -> bytes.fill(0) }
        }
    }

    fun adaptFileBacked(
        authority: R2ProjectArchiveAuthority,
        actualArchiveSha256: String,
        spool: R2PrivateProjectSpool,
        trustedDeclarations: R2TrustedDeclarations,
        trustedRuntimeCapabilitiesFingerprint: String,
    ): R2AdaptedProjectArchive {
        validateAuthority(
            authority = authority,
            actualArchiveSha256 = actualArchiveSha256,
            trustedDeclarations = trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                trustedRuntimeCapabilitiesFingerprint,
        )
        spool.finishArchiveExtraction()
        val archiveEntries = spool.archiveEntries
        val manifestEntry =
            archiveEntries[R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH]
            ?: fail(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                "Input archive does not contain request-manifest.json.",
            )
        if (!R2Digests.equal(authority.requestManifestSha256, manifestEntry.sha256)) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest SHA-256 does not match the control plane.",
            )
        }
        val manifestBytes = try {
            spool.readArchiveEntryBytes(
                path = R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH,
                maximumBytes =
                    R2ProvisionalRequestManifest.MAXIMUM_MANIFEST_BYTES,
            )
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                error.message.orEmpty(),
                error,
            )
        }
        val actualManifestSha256 = R2Digests.sha256(manifestBytes)
        if (!R2Digests.equal(authority.requestManifestSha256, actualManifestSha256)) {
            manifestBytes.fill(0)
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest bytes changed after they were spooled.",
            )
        }
        val manifest = try {
            R2ProvisionalRequestManifest.parse(manifestBytes, authority)
        } finally {
            manifestBytes.fill(0)
        }
        validateActualArchiveCompilerEscapePaths(
            paths = archiveEntries.keys,
            profileAuthority = manifest.profileAuthority,
            packageMetadataInventory = manifest.packageMetadataInventory,
            packageTargetInventory = manifest.packageTargetInventory,
            nodePackageTargetV2Inventory = manifest.nodePackageTargetV2Inventory,
        )
        val expectedPaths = buildSet {
            add(R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH)
            manifest.rawConfigDescriptor?.let { config -> add(config.path) }
            manifest.packageMetadataInventory?.files?.forEach { file -> add(file.path) }
            manifest.packageTargetInventory?.files?.forEach { file -> add(file.path) }
            manifest.nodePackageTargetV2Inventory?.files?.forEach { file -> add(file.path) }
            manifest.files.forEach { file -> add(file.path) }
        }
        if (archiveEntries.keys != expectedPaths) {
            val missing = expectedPaths.filterNot(archiveEntries::containsKey)
            val extra = archiveEntries.keys.filterNot(expectedPaths::contains)
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest does not exactly cover the input archive; " +
                    "missing=${missing.size}${missing.firstOrNull().describeArchivePath()}, " +
                    "extra=${extra.size}${extra.firstOrNull().describeArchivePath()}.",
            )
        }

        val deriveNodePackageFormat =
            manifest.profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 &&
                manifest.packageMetadataInventory != null
        val packageMetadataDerivationBytes = ArrayList<ByteArray>(
            manifest.packageMetadataInventory?.files?.size ?: 0,
        )
        val packageMetadataRecords = try {
            manifest.packageMetadataInventory?.files?.map { file ->
                val entry = checkNotNull(archiveEntries[file.path]) {
                    "Exact archive inventory was checked before package metadata indexing."
                }
                if (entry.byteLength != file.size) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest package metadata size does not match actual bytes: " +
                            "${file.path}.",
                    )
                }
                if (!R2Digests.equal(file.sha256, entry.sha256)) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Request manifest package metadata SHA-256 does not match actual bytes: " +
                            "${file.path}.",
                    )
                }
                val bytes = try {
                    spool.readArchiveEntryBytes(
                        path = file.path,
                        maximumBytes = file.size,
                    )
                } catch (error: IllegalArgumentException) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                        error.message.orEmpty(),
                        error,
                    )
                }
                var retainedForDerivation = false
                try {
                    validatePackageMetadataEntry(
                        descriptor = file,
                        actualSize = bytes.size.toLong(),
                        actualSha256 = R2Digests.sha256(bytes),
                        bytes = bytes,
                    )
                    if (deriveNodePackageFormat) {
                        packageMetadataDerivationBytes += bytes
                        retainedForDerivation = true
                    }
                } finally {
                    if (!retainedForDerivation) bytes.fill(0)
                }
                R2FileBackedPackageMetadataRecord(
                    metadata = file,
                    offset = entry.offset,
                )
            }.orEmpty()
        } catch (failure: Throwable) {
            packageMetadataDerivationBytes.forEach { bytes -> bytes.fill(0) }
            packageMetadataDerivationBytes.clear()
            throw failure
        }
        val nodePackageCatalogs = try {
            if (deriveNodePackageFormat) {
                try {
                    val metadataInventory = requireNotNull(manifest.packageMetadataInventory)
                    Pair(
                        R2NodePackageFormatCatalog.create(
                            packageMetadataInventory = metadataInventory,
                            packageMetadataBytes = packageMetadataDerivationBytes,
                        ),
                        R2NodePackageMetadataCatalog.create(
                            inventory = metadataInventory,
                            packageMetadataBytes = packageMetadataDerivationBytes,
                        ),
                    )
                } catch (error: R2NodeModuleOutputPolicyException) {
                    fail(
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        "Node revision 2 package format is not accepted: " +
                            "${error.policyCode.contractValue}.",
                        error,
                    )
                } catch (error: IllegalArgumentException) {
                    fail(
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        "Node revision 2 package-format catalog could not be derived from " +
                            "its exact package metadata inventory.",
                        error,
                    )
                }
            } else {
                Pair(null, null)
            }
        } finally {
            packageMetadataDerivationBytes.forEach { bytes -> bytes.fill(0) }
            packageMetadataDerivationBytes.clear()
        }
        val nodePackageFormatCatalog = nodePackageCatalogs.first
        val nodePackageMetadataCatalog = nodePackageCatalogs.second

        val packageTargetRecords =
            manifest.packageTargetInventory?.files?.map { file ->
                val entry = checkNotNull(archiveEntries[file.path]) {
                    "Exact archive inventory was checked before package target indexing."
                }
                validatePackageTargetEntry(
                    descriptor = file,
                    actualSize = entry.byteLength,
                    actualSha256 = entry.sha256,
                )
                try {
                    spool.strictUtf16CodeUnits(entry)
                } catch (error: IllegalArgumentException) {
                    fail(
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        error.message.orEmpty(),
                        error,
                    )
                }
                R2FileBackedPackageTargetRecord(
                    descriptor = file,
                    offset = entry.offset,
                )
            }.orEmpty()
        val nodePackageTargetV2Records =
            manifest.nodePackageTargetV2Inventory?.files?.map { file ->
                val entry = checkNotNull(archiveEntries[file.path]) {
                    "Exact archive inventory was checked before Node package-target v2 indexing."
                }
                validateNodePackageTargetV2Entry(
                    descriptor = file,
                    actualSize = entry.byteLength,
                    actualSha256 = entry.sha256,
                )
                try {
                    spool.strictUtf16CodeUnits(entry)
                } catch (error: IllegalArgumentException) {
                    fail(
                        TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                        error.message.orEmpty(),
                        error,
                    )
                }
                R2FileBackedNodePackageTargetV2Record(
                    descriptor = file,
                    offset = entry.offset,
                )
            }.orEmpty()

        val rawConfigRecord = manifest.rawConfigDescriptor?.let { config ->
            val entry = checkNotNull(archiveEntries[config.path]) {
                "Exact archive inventory was checked before raw config indexing."
            }
            validateRawConfigEntry(
                descriptor = config,
                actualSize = entry.byteLength,
                actualSha256 = entry.sha256,
            )
            R2FileBackedRawProjectConfigRecord(
                descriptor = config,
                offset = entry.offset,
            )
        }

        val snapshotLimits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE
        if (trustedDeclarations.files.size > R2_TRUSTED_DECLARATION_MAXIMUM_FILES) {
            fail(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Local declarations exceed the file-backed VFS file-count budget.",
            )
        }
        val callerPayloadCount =
            manifest.files.size + packageMetadataRecords.size + packageTargetRecords.size +
                nodePackageTargetV2Records.size +
                if (rawConfigRecord == null) 0 else 1
        if (callerPayloadCount > snapshotLimits.maximumFiles - trustedDeclarations.files.size) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                "Project config, package metadata, sources and local declarations exceed the " +
                    "file-backed input file-count limit.",
            )
        }
        var trustedBytes = 0L
        trustedDeclarations.files.forEach { declaration ->
            if (declaration.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "A local declaration exceeds the file-backed VFS file limit.",
                )
            }
            if (
                trustedBytes >
                R2_TRUSTED_DECLARATION_MAXIMUM_BYTES - declaration.size
            ) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Local declarations exceed the file-backed VFS byte budget.",
                )
            }
            trustedBytes += declaration.size
        }
        var snapshotBytes = trustedBytes
        manifest.rawConfigDescriptor?.let { config ->
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - config.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Project config and local declarations exceed the file-backed input byte " +
                        "limit.",
                )
            }
            snapshotBytes += config.size
        }
        manifest.packageMetadataInventory?.files?.forEach { file ->
            if (file.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package metadata exceeds the file-backed single-file input limit.",
                )
            }
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package metadata, project files and local declarations exceed the " +
                        "file-backed input byte limit.",
                )
            }
            snapshotBytes += file.size
        }
        manifest.packageTargetInventory?.files?.forEach { file ->
            if (file.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package target exceeds the file-backed single-file input limit.",
                )
            }
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package metadata, package targets, project files and local declarations " +
                        "exceed the file-backed input byte limit.",
                )
            }
            snapshotBytes += file.size
        }
        manifest.nodePackageTargetV2Inventory?.files?.forEach { file ->
            if (file.size > snapshotLimits.maximumSingleFileBytes) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Node package-target v2 exceeds the file-backed single-file input limit.",
                )
            }
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Node package-target v2, project files and local declarations exceed the " +
                        "file-backed input byte limit.",
                )
            }
            snapshotBytes += file.size
        }
        manifest.files.forEach { file ->
            if (snapshotBytes > snapshotLimits.maximumTotalBytes - file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                    "Package metadata, project files and local declarations exceed the " +
                        "file-backed input byte limit.",
                )
            }
            snapshotBytes += file.size
        }
        val records = ArrayList<R2FileBackedProjectRecord>(
            manifest.files.size + trustedDeclarations.files.size,
        )
        manifest.files.forEach { file ->
            val entry = checkNotNull(archiveEntries[file.path]) {
                "Exact archive inventory was checked before source spooling."
            }
            if (entry.byteLength != file.size) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                    "Request manifest size does not match actual bytes: ${file.path}.",
                )
            }
            if (!R2Digests.equal(file.sha256, entry.sha256)) {
                fail(
                    TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                    "Request manifest SHA-256 does not match actual bytes: ${file.path}.",
                )
            }
            val utf16CodeUnits = try {
                spool.strictUtf16CodeUnits(entry)
            } catch (error: IllegalArgumentException) {
                fail(
                    TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    error.message.orEmpty(),
                    error,
                )
            }
            records += R2FileBackedProjectRecord(
                metadata = R2ProjectFileMetadata(
                    path = "/${file.path}",
                    utf8Bytes = entry.byteLength,
                    utf16CodeUnits = utf16CodeUnits,
                    sha256 = entry.sha256,
                ),
                offset = entry.offset,
            )
        }
        trustedDeclarations.files.forEach { declaration ->
            records += try {
                spool.appendTrustedDeclaration(declaration)
            } catch (error: IllegalArgumentException) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Trusted declaration could not be written to the VFS spool.",
                    error,
                )
            }
        }
        val acceptedInputAuthority = try {
            R2InputArchiveSchemaV1.acceptedAuthority(
                callerInventoryFingerprint = manifest.callerInventoryFingerprint,
                sources = manifest.files,
                trustedDeclarations = trustedDeclarations,
                rawConfig = manifest.rawConfigDescriptor,
                packageMetadata = manifest.packageMetadataInventory,
                packageTargets = manifest.packageTargetInventory,
                nodePackageTargetsV2 = manifest.nodePackageTargetV2Inventory,
            )
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Accepted file-backed input inventory authority could not be derived.",
                error,
            )
        }
        val snapshot = try {
            spool.seal(
                profileAuthority = manifest.profileAuthority,
                projectRecords = records,
                rootNames = manifest.rootPaths.map { path -> "/$path" },
                limits = snapshotLimits,
                entryPath = "/${manifest.entryPath}",
                rawConfigRecord = rawConfigRecord,
                packageMetadataInventory = manifest.packageMetadataInventory,
                packageMetadataRecords = packageMetadataRecords,
                nodePackageFormatCatalog = nodePackageFormatCatalog,
                nodePackageMetadataCatalog = nodePackageMetadataCatalog,
                packageTargetInventory = manifest.packageTargetInventory,
                packageTargetRecords = packageTargetRecords,
                nodePackageTargetV2Inventory = manifest.nodePackageTargetV2Inventory,
                nodePackageTargetV2Records = nodePackageTargetV2Records,
                acceptedProjectId = manifest.projectId,
                acceptedInputAuthority = acceptedInputAuthority,
            )
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                "Archive could not be adapted to the file-backed project VFS: " +
                    error.message.orEmpty(),
                error,
            )
        }
        return publishR2PrivateInputOwnership(snapshot) {
            R2AdaptedProjectArchive(
                snapshot = snapshot,
                manifest = manifest,
                archiveSha256 = actualArchiveSha256,
                requestManifestSha256 = actualManifestSha256,
            )
        }
    }

    private fun validateRawConfigEntry(
        descriptor: R2RawProjectConfigDescriptor,
        actualSize: Long,
        actualSha256: String,
    ) {
        if (actualSize > TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong()) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                "Raw project config exceeds the controlled byte limit.",
            )
        }
        if (actualSize != descriptor.size) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest raw config size does not match actual bytes.",
            )
        }
        if (!R2Digests.equal(actualSha256, descriptor.sha256)) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest raw config SHA-256 does not match actual bytes.",
            )
        }
    }

    private fun validatePackageMetadataEntry(
        descriptor: R2PackageMetadataFileDescriptor,
        actualSize: Long,
        actualSha256: String,
        bytes: ByteArray,
    ) {
        if (actualSize != descriptor.size) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest package metadata size does not match actual bytes: " +
                    "${descriptor.path}.",
            )
        }
        if (!R2Digests.equal(actualSha256, descriptor.sha256)) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest package metadata SHA-256 does not match actual bytes: " +
                    "${descriptor.path}.",
            )
        }
        try {
            R2PackageMetadataPolicy.validatePackageJson(bytes)
        } catch (error: R2PackageMetadataPolicyException) {
            failPackageMetadataPolicy(error)
        }
    }

    private fun validatePackageTargetEntry(
        descriptor: R2PackageTargetFileDescriptor,
        actualSize: Long,
        actualSha256: String,
    ) {
        if (actualSize != descriptor.size) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest package target size does not match actual bytes: " +
                    "${descriptor.path}.",
            )
        }
        if (!R2Digests.equal(actualSha256, descriptor.sha256)) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest package target SHA-256 does not match actual bytes: " +
                    "${descriptor.path}.",
            )
        }
    }

    private fun validateNodePackageTargetV2Entry(
        descriptor: R2NodePackageTargetV2FileDescriptor,
        actualSize: Long,
        actualSha256: String,
    ) {
        if (actualSize != descriptor.size) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest Node package-target v2 size does not match actual bytes: " +
                    "${descriptor.path}.",
            )
        }
        if (!R2Digests.equal(actualSha256, descriptor.sha256)) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Request manifest Node package-target v2 SHA-256 does not match actual bytes: " +
                    "${descriptor.path}.",
            )
        }
    }

    private fun validateAuthority(
        authority: R2ProjectArchiveAuthority,
        actualArchiveSha256: String,
        trustedDeclarations: R2TrustedDeclarations,
        trustedRuntimeCapabilitiesFingerprint: String,
    ) {
        R2Digests.requireLowerSha256(actualArchiveSha256, "Staged archive SHA-256")
        if (!R2Digests.equal(authority.archiveSha256, actualArchiveSha256)) {
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                "Staged archive SHA-256 does not match the control plane.",
            )
        }
        try {
            R2Digests.requireLowerSha256(
                trustedRuntimeCapabilitiesFingerprint,
                "Trusted runtime capabilities fingerprint",
            )
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Trusted runtime capability metadata is invalid.",
                error,
            )
        }
        if (
            !R2Digests.equal(
                authority.runtimeCapabilitiesFingerprint,
                trustedRuntimeCapabilitiesFingerprint,
            )
        ) {
            fail(
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                "Control-plane runtime capabilities are not available locally.",
            )
        }
        if (
            !R2Digests.equal(
                authority.declarationsFingerprint,
                trustedDeclarations.fingerprint,
            )
        ) {
            fail(
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                "Control-plane declarations fingerprint is not available locally.",
            )
        }
        if (
            !R2Digests.equal(
                authority.declarationProfileFingerprint,
                trustedDeclarations.profileFingerprint,
            )
        ) {
            fail(
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                "Control-plane declaration profile fingerprint is not available locally.",
            )
        }
        val declarationProfileAvailable = when (authority.profile) {
            R2ProjectProfile.PROVISIONAL_RHINO ->
                trustedDeclarations.profile == null ||
                    trustedDeclarations.profile == R2DeclarationProfile.RHINO
            R2ProjectProfile.PROVISIONAL_NODE_NEXT ->
                trustedDeclarations.profile == R2DeclarationProfile.NODE
        }
        if (!declarationProfileAvailable) {
            fail(
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                "Control-plane declaration profile is not available locally.",
            )
        }
    }
}

/**
 * End-to-end provisional input boundary used before a project can reach the compiler host.
 *
 * The caller descriptor is consumed while it is copied. ZIP validation observes only the same
 * private read-only staging inode, then streams validated payloads into a second app-private VFS
 * spool used by manifest, digest and compiler framing. Android/Linux unlinks each name once its
 * read-only reader is held, while platforms that cannot unlink an open file retain a read-only
 * fallback path until that reader closes.
 */
internal object R2ProjectArchiveLoader {

    /**
     * Production-shaped entry: the staging directory and declarations both come from this signed
     * plugin's private state. The descriptor is consumed even if local trust-root loading fails.
     */
    fun load(
        context: Context,
        inputArchive: ParcelFileDescriptor,
        authority: R2ProjectArchiveAuthority,
        trustedRuntimeCapabilitiesFingerprint: String,
    ): R2AdaptedProjectArchive {
        val (trustedDeclarations, privateDirectory) = try {
            val distribution = try {
                CompilerDistributionMetadata.load(context)
            } catch (error: Exception) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Trusted compiler distribution metadata could not be loaded.",
                    error,
                )
            }
            if (
                distribution.version != authority.compilerVersion ||
                !R2Digests.equal(
                    distribution.distributionFingerprint,
                    authority.compilerDistributionFingerprint,
                ) ||
                !R2Digests.equal(
                    distribution.declarationsFingerprint,
                    authority.declarationsFingerprint,
                )
            ) {
                fail(
                    TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                    "Control-plane compiler or declarations do not match this plugin.",
                )
            }
            requireTrustedRuntimeCapabilities(
                authority,
                trustedRuntimeCapabilitiesFingerprint,
            )
            val declarations = try {
                R2TrustedDeclarations.loadForProfile(context, authority.profile)
            } catch (error: Exception) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "Trusted compiler declarations could not be loaded.",
                    error,
                )
            }
            declarations to context.getDir(
                PRIVATE_STAGING_DIRECTORY,
                Context.MODE_PRIVATE,
            )
        } catch (error: Throwable) {
            closeOwnedInputAfterPreStagingFailure(inputArchive, error)
        }
        return load(
            inputArchive = inputArchive,
            privateDirectory = privateDirectory,
            authority = authority,
            trustedDeclarations = trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint =
                trustedRuntimeCapabilitiesFingerprint,
        )
    }

    fun load(
        inputArchive: ParcelFileDescriptor,
        privateDirectory: File,
        authority: R2ProjectArchiveAuthority,
        trustedDeclarations: R2TrustedDeclarations,
        trustedRuntimeCapabilitiesFingerprint: String,
    ): R2AdaptedProjectArchive {
        try {
            requireTrustedRuntimeCapabilities(
                authority,
                trustedRuntimeCapabilitiesFingerprint,
            )
        } catch (error: Throwable) {
            closeOwnedInputAfterPreStagingFailure(inputArchive, error)
        }
        val staging = try {
            R2PrivateInputStaging.stage(
                inputArchive = inputArchive,
                privateDirectory = privateDirectory,
                maximumBytes = TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
            )
        } catch (error: Throwable) {
            throw mapStagingFailure(error)
        }
        return load(
            staging,
            privateDirectory,
            authority,
            trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint,
        )
    }

    /**
     * Pure-IO entry for JVM tests. It does not take ownership of [input].
     */
    fun load(
        input: InputStream,
        privateDirectory: File,
        authority: R2ProjectArchiveAuthority,
        trustedDeclarations: R2TrustedDeclarations,
        trustedRuntimeCapabilitiesFingerprint: String,
    ): R2AdaptedProjectArchive {
        requireTrustedRuntimeCapabilities(
            authority,
            trustedRuntimeCapabilitiesFingerprint,
        )
        val staging = try {
            R2PrivateInputStaging.stage(
                input = input,
                privateDirectory = privateDirectory,
                maximumBytes = TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
            )
        } catch (error: Throwable) {
            throw mapStagingFailure(error)
        }
        return load(
            staging,
            privateDirectory,
            authority,
            trustedDeclarations,
            trustedRuntimeCapabilitiesFingerprint,
        )
    }

    private fun load(
        staging: R2PrivateInputStaging,
        privateDirectory: File,
        authority: R2ProjectArchiveAuthority,
        trustedDeclarations: R2TrustedDeclarations,
        trustedRuntimeCapabilitiesFingerprint: String,
    ): R2AdaptedProjectArchive {
        val spool = try {
            R2PrivateProjectSpool.create(privateDirectory)
        } catch (error: Throwable) {
            try {
                staging.close()
            } catch (cleanupFailure: Throwable) {
                throw R2PrivateInputCleanupUnprovenException(
                    "R2 input cleanup after spool creation failure could not be proven.",
                    error,
                ).also { unproven ->
                    unproven.addSuppressed(cleanupFailure)
                }
            }
            throw R2ProjectArchiveException(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "The private file-backed project VFS could not be created.",
                error,
            )
        }
        return try {
            val verified = staging.use {
                if (
                    !R2Digests.equal(
                        authority.archiveSha256,
                        staging.fingerprint.sha256,
                    )
                ) {
                    fail(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                        "Private staged archive SHA-256 does not match the control plane.",
                    )
                }
                staging.withImmutableReader { immutableReader ->
                    val validation = try {
                        ZipArchiveV1Validator.validateImmutableTo(
                            file = immutableReader,
                            sink = spool,
                            limits = ZipArchiveV1Limits.INPUT,
                        )
                    } catch (error: ZipArchiveV1Exception) {
                        fail(error.kind.contractErrorCode, error.message.orEmpty(), error)
                    }
                    val verified = try {
                        staging.verifyUnchanged()
                    } catch (error: Throwable) {
                        throw mapStagingFailure(error)
                    }
                    if (
                        verified.byteCount != validation.containerBytes ||
                        !R2Digests.equal(authority.archiveSha256, verified.sha256)
                    ) {
                        fail(
                            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
                            "Private staged archive changed during ZIP validation.",
                        )
                    }
                    verified
                }
            }
            try {
                R2ProjectArchiveAdapter.adaptFileBacked(
                    authority = authority,
                    actualArchiveSha256 = verified.sha256,
                    spool = spool,
                    trustedDeclarations = trustedDeclarations,
                    trustedRuntimeCapabilitiesFingerprint =
                        trustedRuntimeCapabilitiesFingerprint,
                )
            } catch (error: IOException) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "The private file-backed project VFS could not be completed.",
                    error,
                )
            } catch (error: SecurityException) {
                fail(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    "The private file-backed project VFS could not be completed.",
                    error,
                )
            }
        } catch (error: Throwable) {
            try {
                spool.close()
            } catch (cleanupFailure: Throwable) {
                throw R2PrivateInputCleanupUnprovenException(
                    "R2 project spool cleanup after archive failure could not be proven.",
                    error,
                ).also { unproven ->
                    unproven.addSuppressed(cleanupFailure)
                }
            }
            throw mapStagingFailure(error)
        }
    }

    private fun mapStagingFailure(error: Throwable): R2ProjectArchiveException = when (error) {
        is R2ProjectArchiveException -> error
        is ZipArchiveV1EntrySinkException -> R2ProjectArchiveException(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            "Input archive payloads could not be written to the private project VFS.",
            error,
        )
        is R2PrivateInputStagingQuotaException -> R2ProjectArchiveException(
            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
            error.message.orEmpty(),
            error,
        )
        is R2PrivateInputStagingIntegrityException -> R2ProjectArchiveException(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            error.message.orEmpty(),
            error,
        )
        is IOException,
        is SecurityException,
        -> R2ProjectArchiveException(
            TypeScriptCompilerContract.ERROR_ARCHIVE_INTEGRITY,
            "Input archive could not be captured in private staging.",
            error,
        )
        else -> throw error
    }

    private fun closeOwnedInputAfterPreStagingFailure(
        inputArchive: ParcelFileDescriptor,
        failure: Throwable,
    ): Nothing {
        try {
            inputArchive.close()
        } catch (closeFailure: Throwable) {
            throw R2PrivateInputCleanupUnprovenException(
                "R2 caller input descriptor close could not be proven.",
                failure,
            ).also { unproven ->
                unproven.addSuppressed(closeFailure)
            }
        }
        throw failure
    }

    private fun requireTrustedRuntimeCapabilities(
        authority: R2ProjectArchiveAuthority,
        trustedRuntimeCapabilitiesFingerprint: String,
    ) {
        try {
            R2Digests.requireLowerSha256(
                trustedRuntimeCapabilitiesFingerprint,
                "Trusted runtime capabilities fingerprint",
            )
        } catch (error: IllegalArgumentException) {
            fail(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                "Trusted runtime capability metadata is invalid.",
                error,
            )
        }
        if (
            !R2Digests.equal(
                authority.runtimeCapabilitiesFingerprint,
                trustedRuntimeCapabilitiesFingerprint,
            )
        ) {
            fail(
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                "Control-plane runtime capabilities are not available locally.",
            )
        }
    }

    private const val PRIVATE_STAGING_DIRECTORY = "typescript-r2-input-staging"
}

internal class R2ProjectArchiveException(
    val contractErrorCode: String,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(
    message.take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS),
    cause,
)

private fun validateActualArchiveCompilerEscapePaths(
    paths: Iterable<String>,
    profileAuthority: R2CompilationProfileAuthority,
    packageMetadataInventory: R2PackageMetadataInventory?,
    packageTargetInventory: R2PackageTargetInventory?,
    nodePackageTargetV2Inventory: R2NodePackageTargetV2Inventory?,
) {
    paths.forEach { path ->
        R2ProjectSourceKindAdmission.sourcePathRejection(
            path = path,
            profileAuthority = profileAuthority,
        )?.let { rejection ->
            if (
                packageMetadataInventory != null &&
                R2PackageMetadataPolicy.isExplicitlyAllowed(path, packageMetadataInventory)
            ) {
                return@let
            }
            if (
                packageTargetInventory != null &&
                R2PackageTargetPolicy.isExplicitlyAllowed(path, packageTargetInventory)
            ) {
                return@let
            }
            if (
                nodePackageTargetV2Inventory != null &&
                path in nodePackageTargetV2Inventory.filePaths
            ) {
                return@let
            }
            fail(
                TypeScriptCompilerContract.ERROR_ARCHIVE_PATH,
                "Input archive contains a reserved compiler escape path: " +
                    "${rejection.contractValue}.",
            )
        }
    }
}

private fun String?.describeArchivePath(): String = when {
    this == null -> ""
    length <= MAXIMUM_ARCHIVE_ERROR_SAMPLE_CHARS -> ", first='$this'"
    else ->
        ", first='${take(MAXIMUM_ARCHIVE_ERROR_SAMPLE_CHARS)}...' ($length chars)"
}

private fun failPackageMetadataPolicy(
    error: R2PackageMetadataPolicyException,
): Nothing {
    val contractErrorCode = when (error.policyCode) {
        R2PackageMetadataPolicyCode.PATH_INVALID,
        R2PackageMetadataPolicyCode.ROOT_NOT_ALLOWED,
        -> TypeScriptCompilerContract.ERROR_ARCHIVE_PATH

        R2PackageMetadataPolicyCode.QUOTA_EXCEEDED,
        R2PackageMetadataPolicyCode.COMPLEXITY_EXCEEDED,
        -> TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA

        R2PackageMetadataPolicyCode.INVENTORY_INVALID,
        R2PackageMetadataPolicyCode.ENCODING_INVALID,
        R2PackageMetadataPolicyCode.JSON_INVALID,
        R2PackageMetadataPolicyCode.DUPLICATE_KEY,
        R2PackageMetadataPolicyCode.LIFECYCLE_FORBIDDEN,
        -> TypeScriptCompilerContract.ERROR_INVALID_REQUEST
    }
    fail(contractErrorCode, error.message.orEmpty(), error)
}

private fun failPackageTargetPolicy(
    error: R2PackageTargetPolicyException,
): Nothing {
    val contractErrorCode = when (error.policyCode) {
        R2PackageTargetPolicyCode.PATH_INVALID,
        R2PackageTargetPolicyCode.ROOT_NOT_ALLOWED,
        R2PackageTargetPolicyCode.SOURCE_KIND_UNSUPPORTED,
        R2PackageTargetPolicyCode.CASE_COLLISION,
        -> TypeScriptCompilerContract.ERROR_ARCHIVE_PATH

        R2PackageTargetPolicyCode.QUOTA_EXCEEDED ->
            TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA

        R2PackageTargetPolicyCode.INVENTORY_INVALID,
        R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID,
        -> TypeScriptCompilerContract.ERROR_INVALID_REQUEST
    }
    fail(contractErrorCode, error.message.orEmpty(), error)
}

private fun fail(
    errorCode: String,
    message: String,
    cause: Throwable? = null,
): Nothing = throw R2ProjectArchiveException(errorCode, message, cause)

private const val MAXIMUM_ARCHIVE_ERROR_SAMPLE_CHARS = 128
