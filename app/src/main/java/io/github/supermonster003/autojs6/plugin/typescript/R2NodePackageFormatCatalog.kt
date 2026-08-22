package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/** One exact C5 package.json identity and its P2 top-level `type` interpretation. */
internal data class R2NodePackageFormatRecord(
    val packageMetadataPath: String,
    val packageBoundaryPath: String,
    val packageMetadataSize: Long,
    val packageMetadataSha256: String,
    val typeState: R2PackageTypeState,
) {

    init {
        R2PackageMetadataPolicy.requirePackageMetadataPath(packageMetadataPath)
        require(packageBoundaryPath == boundaryPathFor(packageMetadataPath)) {
            "Node package-format boundary does not match its package metadata path."
        }
        require(packageMetadataSize in 0L..R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES) {
            "Node package-format metadata exceeds the C5 single-file limit."
        }
        R2Digests.requireLowerSha256(
            packageMetadataSha256,
            "Node package-format metadata SHA-256",
        )
        require(typeState != R2PackageTypeState.NO_BOUNDARY) {
            "A package metadata record cannot represent the absence of a boundary."
        }
    }
}

/**
 * Pure P2 catalog derived from an exact C5 inventory and its validated package.json bytes.
 *
 * Absence of accepted package metadata is represented by a null catalog. Consequently every
 * record in a present catalog describes a real boundary and [R2PackageTypeState.NO_BOUNDARY]
 * never appears in [records]. The caller must derive NO_BOUNDARY only from catalog absence.
 */
internal class R2NodePackageFormatCatalog private constructor(
    dependencyRoots: List<String>,
    records: List<R2NodePackageFormatRecord>,
    val packageMetadataInventoryCanonicalInput: String,
    val packageMetadataInventoryFingerprint: String,
    val canonicalInput: String,
    val fingerprint: String,
) {

    val dependencyRoots: List<String> =
        Collections.unmodifiableList(ArrayList(dependencyRoots))
    val records: List<R2NodePackageFormatRecord> =
        Collections.unmodifiableList(ArrayList(records))
    private val recordsByBoundary: Map<String, R2NodePackageFormatRecord> =
        Collections.unmodifiableMap(
            records.associateByTo(LinkedHashMap(records.size)) { record ->
                record.packageBoundaryPath
            },
        )

    init {
        require(this.records.isNotEmpty()) {
            "A present Node package-format catalog must contain the project package boundary."
        }
        require(
            this.records.first().packageMetadataPath ==
                R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH &&
                this.records.first().packageBoundaryPath == "/",
        ) {
            "A present Node package-format catalog must start with the exact project boundary."
        }
        require(
            this.records.map(R2NodePackageFormatRecord::packageMetadataPath) ==
                this.records.map(R2NodePackageFormatRecord::packageMetadataPath).sorted(),
        ) {
            "Node package-format records must use canonical C5 inventory order."
        }
        require(recordsByBoundary.size == this.records.size) {
            "Node package-format catalog contains a duplicate boundary."
        }
        require(
            this.records.map { record -> record.packageBoundaryPath.asciiCaseFold() }
                .distinct().size == this.records.size,
        ) {
            "Node package-format catalog contains an ASCII case-fold boundary collision."
        }
        R2Digests.requireLowerSha256(
            packageMetadataInventoryFingerprint,
            "Node package-format C5 inventory fingerprint",
        )
        require(
            R2Digests.equal(
                R2Digests.sha256(
                    packageMetadataInventoryCanonicalInput.toByteArray(StandardCharsets.US_ASCII),
                ),
                packageMetadataInventoryFingerprint,
            ),
        ) {
            "Node package-format C5 inventory fingerprint does not match its canonical input."
        }
        require(canonicalInput.all { character -> character.code in PRINTABLE_ASCII }) {
            "Node package-format canonical input is not printable ASCII."
        }
        R2Digests.requireLowerSha256(fingerprint, "Node package-format catalog fingerprint")
        require(
            R2Digests.equal(
                R2Digests.sha256(canonicalInput.toByteArray(StandardCharsets.US_ASCII)),
                fingerprint,
            ),
        ) {
            "Node package-format catalog fingerprint does not match its canonical input."
        }
    }

    fun recordAtBoundary(boundaryPath: String): R2NodePackageFormatRecord? =
        recordsByBoundary[boundaryPath]

    fun nearestRecord(containingPath: String): R2NodePackageFormatRecord? {
        requireCanonicalContainingPath(containingPath)
        return records.asSequence()
            .filter { record ->
                record.packageBoundaryPath == "/" ||
                    containingPath.startsWith("${record.packageBoundaryPath}/")
            }
            .maxByOrNull { record -> record.packageBoundaryPath.length }
    }

    companion object {

        const val SCHEMA = "autojs6.typescript.node-package-format-catalog"
        const val SCHEMA_REVISION = 1
        const val CATALOG_REVISION = 1
        const val CANONICAL_DOMAIN =
            "autojs6.typescript.node-package-format.catalog.v1"

        fun create(
            packageMetadataInventory: R2PackageMetadataInventory,
            packageMetadataBytes: List<ByteArray>,
        ): R2NodePackageFormatCatalog {
            val exactInventory = R2PackageMetadataInventory.create(
                dependencyRoots = packageMetadataInventory.dependencyRoots,
                files = packageMetadataInventory.files,
            )
            require(
                exactInventory.dependencyRoots == packageMetadataInventory.dependencyRoots &&
                    exactInventory.files == packageMetadataInventory.files &&
                    exactInventory.canonicalInput == packageMetadataInventory.canonicalInput &&
                    R2Digests.equal(
                        exactInventory.fingerprint,
                        packageMetadataInventory.fingerprint,
                    ) &&
                    exactInventory.totalBytes == packageMetadataInventory.totalBytes &&
                    exactInventory.authority == packageMetadataInventory.authority,
            ) {
                "C5 package metadata inventory authority changed before format derivation."
            }
            require(exactInventory.files.size == packageMetadataBytes.size) {
                "Package metadata bytes do not exactly cover the C5 inventory."
            }

            val ownedBytes = ArrayList<ByteArray>(packageMetadataBytes.size)
            try {
                packageMetadataBytes.forEach { bytes -> ownedBytes += bytes.copyOf() }
                val metadataCatalog = R2NodePackageMetadataCatalog.create(
                    inventory = exactInventory,
                    packageMetadataBytes = ownedBytes,
                )
                require(
                    metadataCatalog.inventoryFingerprint == exactInventory.fingerprint &&
                        metadataCatalog.dependencyRoots == exactInventory.dependencyRoots &&
                        metadataCatalog.records.map { record -> record.path } ==
                        exactInventory.files.map { descriptor -> descriptor.path },
                ) {
                    "Parsed package metadata catalog drifted from its exact C5 inventory."
                }

                val records = metadataCatalog.records.map { record ->
                    R2NodePackageFormatRecord(
                        packageMetadataPath = record.path,
                        packageBoundaryPath = record.boundaryPath,
                        packageMetadataSize = record.size,
                        packageMetadataSha256 = record.sha256,
                        typeState = R2NodeModuleOutputPolicy.parsePackageType(record.root),
                    )
                }
                require(records.map { record -> record.packageMetadataPath }.distinct().size ==
                    records.size) {
                    "Node package-format catalog contains a duplicate metadata path."
                }
                require(records.map { record -> record.packageBoundaryPath }.distinct().size ==
                    records.size) {
                    "Node package-format catalog contains a duplicate boundary."
                }
                require(
                    records.map { record -> record.packageBoundaryPath.asciiCaseFold() }
                        .distinct().size == records.size,
                ) {
                    "Node package-format catalog contains an ASCII case-fold boundary collision."
                }

                val canonicalInput = canonicalInput(
                    packageMetadataInventoryFingerprint = exactInventory.fingerprint,
                    records = records,
                )
                return R2NodePackageFormatCatalog(
                    dependencyRoots = exactInventory.dependencyRoots,
                    records = records,
                    packageMetadataInventoryCanonicalInput = exactInventory.canonicalInput,
                    packageMetadataInventoryFingerprint = exactInventory.fingerprint,
                    canonicalInput = canonicalInput,
                    fingerprint = R2Digests.sha256(
                        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
                    ),
                )
            } finally {
                ownedBytes.forEach { bytes -> bytes.fill(0) }
                ownedBytes.clear()
            }
        }

        private fun canonicalInput(
            packageMetadataInventoryFingerprint: String,
            records: List<R2NodePackageFormatRecord>,
        ): String = buildString {
            append(CANONICAL_DOMAIN)
            append("|schemaRevision=")
            append(SCHEMA_REVISION)
            append("|catalogRevision=")
            append(CATALOG_REVISION)
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
            append("|packageMetadataSchemaRevision=")
            append(R2PackageMetadataPolicy.SCHEMA_REVISION)
            append("|packageMetadataPolicyRevision=")
            append(R2PackageMetadataPolicy.POLICY_REVISION)
            append("|packageMetadataPolicyFingerprint=")
            append(R2PackageMetadataPolicy.fingerprint)
            append("|packageMetadataInventoryFingerprint=")
            append(packageMetadataInventoryFingerprint)
            records.forEach { record ->
                append("|record=")
                append(record.packageMetadataPath)
                append(',')
                append(record.packageBoundaryPath)
                append(',')
                append(record.packageMetadataSize)
                append(',')
                append(record.packageMetadataSha256)
                append(',')
                append(record.typeState.contractValue)
            }
        }

        private fun requireCanonicalContainingPath(path: String) {
            require(
                path.startsWith('/') &&
                    path.length > 1 &&
                    path.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS &&
                    '\\' !in path &&
                    path.all { character -> character.code in PRINTABLE_ASCII },
            ) {
                "Node package-format lookup path is not bounded printable ASCII."
            }
            val segments = path.drop(1).split('/')
            require(
                segments.size <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH &&
                    segments.none { segment ->
                        segment.isEmpty() || segment == "." || segment == ".."
                    },
            ) {
                "Node package-format lookup path is not canonical."
            }
        }
    }
}

private fun boundaryPathFor(packageMetadataPath: String): String =
    if (packageMetadataPath == R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH) {
        "/"
    } else {
        "/" + packageMetadataPath.removeSuffix(
            "/${R2PackageMetadataPolicy.PACKAGE_FILE_NAME}",
        )
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

private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
private val PRINTABLE_ASCII = 0x20..0x7e
