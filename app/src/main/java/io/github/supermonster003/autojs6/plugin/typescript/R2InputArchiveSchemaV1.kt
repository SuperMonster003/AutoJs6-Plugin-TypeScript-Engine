package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Canonical production authority for the frozen R2 input-archive schema revision 1. */
internal object R2InputArchiveSchemaV1 {

    const val SCHEMA = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA
    const val REVISION = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION
    const val REQUEST_MANIFEST_PATH = TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH
    const val INPUT_QUOTA_REVISION = TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION

    private const val INVENTORY_PREFIX = "autojs6.typescript.input-inventory.v1\n"
    private const val SOURCE = "source"
    private const val DECLARATION = "declaration"
    private const val CONFIG = "config"
    private const val PACKAGE_METADATA = "package-metadata"
    private const val PACKAGE_TARGET_V1 = "package-target-v1"
    private const val PACKAGE_TARGET_V2 = "package-target-v2"

    internal data class Record(
        val domain: String,
        val path: String,
        val size: Long,
        val sha256: String,
    ) {
        init {
            require(domain in DOMAIN_ORDER) { "Input inventory domain is not supported." }
            require(path.isNotEmpty() && '\u0000' !in path && '\n' !in path) {
                "Input inventory path is not canonical."
            }
            require(size >= 0L) { "Input inventory size is negative." }
            R2Digests.requireLowerSha256(sha256, "Input inventory SHA-256")
        }
    }

    fun callerInventoryFingerprint(
        sources: List<R2ProjectArchiveFile>,
        rawConfig: R2RawProjectConfigDescriptor?,
        packageMetadata: R2PackageMetadataInventory?,
        packageTargets: R2PackageTargetInventory?,
        nodePackageTargetsV2: R2NodePackageTargetV2Inventory?,
    ): String = fingerprint(
        callerRecords(
            sources,
            rawConfig,
            packageMetadata,
            packageTargets,
            nodePackageTargetsV2,
        ),
    )

    fun acceptedAuthority(
        callerInventoryFingerprint: String,
        sources: List<R2ProjectArchiveFile>,
        trustedDeclarations: R2TrustedDeclarations,
        rawConfig: R2RawProjectConfigDescriptor?,
        packageMetadata: R2PackageMetadataInventory?,
        packageTargets: R2PackageTargetInventory?,
        nodePackageTargetsV2: R2NodePackageTargetV2Inventory?,
    ): R2AcceptedInputAuthorityV1 {
        R2Digests.requireLowerSha256(callerInventoryFingerprint, "Caller inventory fingerprint")
        val callerRecords = callerRecords(
            sources,
            rawConfig,
            packageMetadata,
            packageTargets,
            nodePackageTargetsV2,
        )
        require(R2Digests.equal(callerInventoryFingerprint, fingerprint(callerRecords))) {
            "Caller inventory fingerprint does not cover its exact input domains."
        }
        val declarationRecords = trustedDeclarations.files.map { file ->
            Record(
                domain = DECLARATION,
                path = "/${file.archivePath}",
                size = file.size,
                sha256 = file.sha256,
            )
        }
        val retainedRecords = callerRecords + declarationRecords
        val capacity = capacity(retainedRecords)
        return R2AcceptedInputAuthorityV1(
            schemaRevision = REVISION,
            callerInventoryFingerprint = callerInventoryFingerprint,
            retainedInventoryFingerprint = fingerprint(retainedRecords),
            declarationProfileFingerprint = trustedDeclarations.profileFingerprint,
            declarationInventoryFingerprint = fingerprint(declarationRecords),
            declarationFileCount = declarationRecords.size,
            declarationByteLength = declarationRecords.sumOf(Record::size),
            inputQuotaRevision = INPUT_QUOTA_REVISION,
            capacity = capacity,
        )
    }

    /**
     * Builds an authority for the pre-archive in-memory test seam. Production archive snapshots
     * supply the C8 profile fingerprint explicitly and never use this compatibility derivation.
     */
    internal fun compatibilityAuthority(snapshot: R2ProjectInputSnapshot): R2AcceptedInputAuthorityV1 {
        val projectRecords = snapshot.fileInventory.map { file ->
            Record(
                domain = if (file.path.startsWith("/declarations/")) DECLARATION else SOURCE,
                path = file.path,
                size = file.utf8Bytes,
                sha256 = file.sha256,
            )
        }
        val callerRecords = buildList {
            addAll(projectRecords.filter { record -> record.domain == SOURCE })
            snapshot.rawConfigDescriptor?.let { config ->
                add(Record(CONFIG, config.path, config.size, config.sha256))
            }
            snapshot.packageMetadataInventory?.files?.forEach { file ->
                add(
                    Record(
                        PACKAGE_METADATA,
                        R2PackageMetadataPolicy.toVfsPath(file.path),
                        file.size,
                        file.sha256,
                    ),
                )
            }
            snapshot.packageTargetInventory?.files?.forEach { file ->
                add(
                    Record(
                        PACKAGE_TARGET_V1,
                        R2PackageTargetPolicy.toVfsPath(file.path),
                        file.size,
                        file.sha256,
                    ),
                )
            }
            snapshot.nodePackageTargetV2Inventory?.files?.forEach { file ->
                add(Record(PACKAGE_TARGET_V2, "/${file.path}", file.size, file.sha256))
            }
        }
        val declarationRecords = projectRecords.filter { record -> record.domain == DECLARATION }
        require(declarationRecords.isNotEmpty()) {
            "Project input authority requires trusted declarations."
        }
        val retainedRecords = callerRecords + declarationRecords
        val declarationFingerprint = fingerprint(declarationRecords)
        return R2AcceptedInputAuthorityV1(
            schemaRevision = REVISION,
            callerInventoryFingerprint = fingerprint(callerRecords),
            retainedInventoryFingerprint = fingerprint(retainedRecords),
            declarationProfileFingerprint = declarationFingerprint,
            declarationInventoryFingerprint = declarationFingerprint,
            declarationFileCount = declarationRecords.size,
            declarationByteLength = declarationRecords.sumOf(Record::size),
            inputQuotaRevision = INPUT_QUOTA_REVISION,
            capacity = capacity(retainedRecords),
        )
    }

    internal fun fingerprint(records: List<Record>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(INVENTORY_PREFIX.toByteArray(StandardCharsets.US_ASCII))
        records
            .sortedWith(
                compareBy<Record> { DOMAIN_ORDER.getValue(it.domain) }
                    .thenBy(Record::path),
            )
            .forEach { record ->
                digest.update(record.domain.toByteArray(StandardCharsets.US_ASCII))
                digest.update(0)
                digest.update(record.path.toByteArray(StandardCharsets.UTF_8))
                digest.update(0)
                digest.update(record.size.toString().toByteArray(StandardCharsets.US_ASCII))
                digest.update(0)
                digest.update(record.sha256.toByteArray(StandardCharsets.US_ASCII))
                digest.update('\n'.code.toByte())
            }
        return digest.digest().toLowerHex()
    }

    private fun callerRecords(
        sources: List<R2ProjectArchiveFile>,
        rawConfig: R2RawProjectConfigDescriptor?,
        packageMetadata: R2PackageMetadataInventory?,
        packageTargets: R2PackageTargetInventory?,
        nodePackageTargetsV2: R2NodePackageTargetV2Inventory?,
    ): List<Record> {
        require(packageTargets == null || nodePackageTargetsV2 == null) {
            "Input inventory cannot carry package-target v1 and v2 together."
        }
        return buildList {
            sources.forEach { file ->
                add(Record(SOURCE, "/${file.path}", file.size, file.sha256))
            }
            rawConfig?.let { config ->
                add(Record(CONFIG, config.path, config.size, config.sha256))
            }
            packageMetadata?.files?.forEach { file ->
                add(
                    Record(
                        PACKAGE_METADATA,
                        R2PackageMetadataPolicy.toVfsPath(file.path),
                        file.size,
                        file.sha256,
                    ),
                )
            }
            packageTargets?.files?.forEach { file ->
                add(
                    Record(
                        PACKAGE_TARGET_V1,
                        R2PackageTargetPolicy.toVfsPath(file.path),
                        file.size,
                        file.sha256,
                    ),
                )
            }
            nodePackageTargetsV2?.files?.forEach { file ->
                add(Record(PACKAGE_TARGET_V2, "/${file.path}", file.size, file.sha256))
            }
        }
    }

    private fun capacity(records: List<Record>): R2ProjectInputCapacity =
        R2ProjectInputCapacity(
            fileCount = records.size,
            largestFileUtf8Bytes = records.maxOfOrNull(Record::size) ?: 0L,
            totalUtf8Bytes = records.sumOf(Record::size),
        )

    private fun ByteArray.toLowerHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private val DOMAIN_ORDER: Map<String, Int> = Collections.unmodifiableMap(
        linkedMapOf(
            SOURCE to 0,
            DECLARATION to 1,
            CONFIG to 2,
            PACKAGE_METADATA to 3,
            PACKAGE_TARGET_V1 to 4,
            PACKAGE_TARGET_V2 to 5,
        ),
    )
}

internal data class R2AcceptedInputAuthorityV1(
    val schemaRevision: Int,
    val callerInventoryFingerprint: String,
    val retainedInventoryFingerprint: String,
    val declarationProfileFingerprint: String,
    val declarationInventoryFingerprint: String,
    val declarationFileCount: Int,
    val declarationByteLength: Long,
    val inputQuotaRevision: Int,
    val capacity: R2ProjectInputCapacity,
) {
    init {
        require(schemaRevision == R2InputArchiveSchemaV1.REVISION)
        require(inputQuotaRevision == R2InputArchiveSchemaV1.INPUT_QUOTA_REVISION)
        R2Digests.requireLowerSha256(callerInventoryFingerprint, "Caller inventory fingerprint")
        R2Digests.requireLowerSha256(retainedInventoryFingerprint, "Retained inventory fingerprint")
        R2Digests.requireLowerSha256(
            declarationProfileFingerprint,
            "Declaration profile fingerprint",
        )
        R2Digests.requireLowerSha256(
            declarationInventoryFingerprint,
            "Declaration inventory fingerprint",
        )
        require(declarationFileCount > 0 && declarationFileCount <= capacity.fileCount)
        require(declarationByteLength in 0L..capacity.totalUtf8Bytes)
    }
}
