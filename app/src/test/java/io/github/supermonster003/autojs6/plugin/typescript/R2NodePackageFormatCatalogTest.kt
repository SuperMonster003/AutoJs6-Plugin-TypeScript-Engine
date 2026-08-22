package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2NodePackageFormatCatalogTest {

    @Test
    fun sharedFixtureFreezesExactC5AndP2CatalogIdentity() {
        val fixture = fixture()
        assertEquals(EXACT_FIXTURE_KEYS, fixture.keys)
        assertEquals(FIXTURE_SCHEMA, fixture.string("schema"))
        assertEquals(1, fixture.integer("revision"))

        val authority = fixture.objectValue("catalogAuthority")
        assertEquals(EXACT_AUTHORITY_KEYS, authority.keys)
        assertEquals(R2NodePackageFormatCatalog.SCHEMA, authority.string("schema"))
        assertEquals(
            R2NodePackageFormatCatalog.SCHEMA_REVISION,
            authority.integer("schemaRevision"),
        )
        assertEquals(
            R2NodePackageFormatCatalog.CATALOG_REVISION,
            authority.integer("catalogRevision"),
        )
        assertEquals(
            R2NodePackageFormatCatalog.CANONICAL_DOMAIN,
            authority.string("canonicalDomain"),
        )
        assertEquals(
            TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            authority.integer("profileSchemaRevision"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.NODE_PROFILE_REVISION,
            authority.integer("profileRevision"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.SCHEMA_REVISION,
            authority.integer("nodeModuleOutputPolicySchemaRevision"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.POLICY_REVISION,
            authority.integer("nodeModuleOutputPolicyRevision"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.fingerprint,
            authority.string("nodeModuleOutputPolicyFingerprint"),
        )
        assertEquals(
            R2PackageMetadataPolicy.SCHEMA_REVISION,
            authority.integer("packageMetadataSchemaRevision"),
        )
        assertEquals(
            R2PackageMetadataPolicy.POLICY_REVISION,
            authority.integer("packageMetadataPolicyRevision"),
        )
        assertEquals(
            R2PackageMetadataPolicy.fingerprint,
            authority.string("packageMetadataPolicyFingerprint"),
        )

        val noBoundary = fixture.objectValue("noBoundary")
        assertEquals(EXACT_NO_BOUNDARY_KEYS, noBoundary.keys)
        assertEquals(
            R2PackageTypeState.NO_BOUNDARY.contractValue,
            noBoundary.string("typeState"),
        )
        assertNull(noBoundary["packageMetadataInventoryFingerprint"])
        assertNull(noBoundary["boundaryPath"])
        assertEquals(
            R2PackageTypeState.NO_BOUNDARY,
            R2NodeModuleOutputPolicy.parsePackageType(null),
        )

        val ids = LinkedHashSet<String>()
        fixture.objects("validCases").forEach { testCase ->
            assertEquals(EXACT_VALID_CASE_KEYS, testCase.keys)
            val id = testCase.string("id")
            assertTrue("Duplicate fixture case: $id", ids.add(id))
            val dependencyRoots = testCase.strings("dependencyRoots")
            val expectedFiles = testCase.objects("files")
            val packageMetadataBytes = expectedFiles.map { file ->
                assertEquals(EXACT_FILE_KEYS, file.keys)
                file.string("json").toByteArray(StandardCharsets.UTF_8).also { bytes ->
                    assertEquals(id, file.long("size"), bytes.size.toLong())
                    assertEquals(id, file.string("sha256"), R2Digests.sha256(bytes))
                }
            }
            val inventory = R2PackageMetadataInventory.create(
                dependencyRoots = dependencyRoots,
                files = expectedFiles.zip(packageMetadataBytes) { file, bytes ->
                    R2PackageMetadataFileDescriptor(
                        path = file.string("path"),
                        size = bytes.size.toLong(),
                        sha256 = R2Digests.sha256(bytes),
                    )
                },
            )
            assertEquals(id, testCase.string("inventoryCanonicalInput"), inventory.canonicalInput)
            assertEquals(
                id,
                testCase.string("packageMetadataInventoryFingerprint"),
                inventory.fingerprint,
            )

            val catalog = R2NodePackageFormatCatalog.create(
                packageMetadataInventory = inventory,
                packageMetadataBytes = packageMetadataBytes,
            )
            assertEquals(id, dependencyRoots, catalog.dependencyRoots)
            assertEquals(id, inventory.canonicalInput, catalog.packageMetadataInventoryCanonicalInput)
            assertEquals(id, inventory.fingerprint, catalog.packageMetadataInventoryFingerprint)
            assertEquals(id, testCase.string("catalogCanonicalInput"), catalog.canonicalInput)
            assertEquals(id, testCase.string("catalogFingerprint"), catalog.fingerprint)
            assertEquals(id, ROOT_PATH, catalog.records.first().packageMetadataPath)
            assertEquals(id, "/", catalog.records.first().packageBoundaryPath)

            val expectedRecords = expectedFiles.map { file ->
                R2NodePackageFormatRecord(
                    packageMetadataPath = file.string("path"),
                    packageBoundaryPath = file.string("boundaryPath"),
                    packageMetadataSize = file.long("size"),
                    packageMetadataSha256 = file.string("sha256"),
                    typeState = packageTypeState(file.string("typeState")),
                )
            }
            assertEquals(id, expectedRecords, catalog.records)
            expectedRecords.forEach { record ->
                assertEquals(id, record, catalog.recordAtBoundary(record.packageBoundaryPath))
            }
            testCase.objects("lookups").forEach { lookup ->
                assertEquals(EXACT_LOOKUP_KEYS, lookup.keys)
                val record = catalog.nearestRecord(lookup.string("path"))
                assertEquals(id, lookup.string("boundaryPath"), record?.packageBoundaryPath)
                assertEquals(id, lookup.string("typeState"), record?.typeState?.contractValue)
            }

            val canonicalBeforeCallerMutation = catalog.canonicalInput
            packageMetadataBytes.forEach { bytes -> bytes.fill(0) }
            assertEquals(id, canonicalBeforeCallerMutation, catalog.canonicalInput)
            assertFails<UnsupportedOperationException> {
                @Suppress("UNCHECKED_CAST")
                (catalog.dependencyRoots as MutableList<String>).clear()
            }
            assertFails<UnsupportedOperationException> {
                @Suppress("UNCHECKED_CAST")
                (catalog.records as MutableList<R2NodePackageFormatRecord>).clear()
            }
        }
        assertEquals(4, ids.size)
    }

    @Test
    fun presentTypeValuesOutsideTheExactSetFailWithNodePackageTypeInvalid() {
        val fixture = fixture()
        val ids = LinkedHashSet<String>()
        fixture.objects("invalidTypeCases").forEach { testCase ->
            assertEquals(EXACT_INVALID_TYPE_CASE_KEYS, testCase.keys)
            val id = testCase.string("id")
            assertTrue("Duplicate invalid-type case: $id", ids.add(id))
            val bytes = testCase.string("json").toByteArray(StandardCharsets.UTF_8)
            val error = assertFails<R2NodeModuleOutputPolicyException> {
                R2NodePackageFormatCatalog.create(
                    packageMetadataInventory = inventory(emptyList(), ROOT_PATH to bytes),
                    packageMetadataBytes = listOf(bytes),
                )
            }
            assertEquals(id, R2NodeModuleOutputPolicyCode.PACKAGE_TYPE_INVALID, error.policyCode)
            assertEquals(id, "NODE_PACKAGE_TYPE_INVALID", error.policyCode.contractValue)
        }
        assertEquals(8, ids.size)
    }

    @Test
    fun strictC5BodyValidationAndExactCoverageRemainFailClosed() {
        val invalidUtf8 = byteArrayOf(0xc3.toByte(), 0x28)
        assertEquals(
            R2PackageMetadataPolicyCode.ENCODING_INVALID,
            assertPackagePolicyFailure {
                R2NodePackageFormatCatalog.create(
                    inventory(emptyList(), ROOT_PATH to invalidUtf8),
                    listOf(invalidUtf8),
                )
            }.policyCode,
        )

        val duplicateKey = "{\"type\":\"module\",\"type\":\"commonjs\"}"
            .toByteArray(StandardCharsets.UTF_8)
        assertEquals(
            R2PackageMetadataPolicyCode.DUPLICATE_KEY,
            assertPackagePolicyFailure {
                R2NodePackageFormatCatalog.create(
                    inventory(emptyList(), ROOT_PATH to duplicateKey),
                    listOf(duplicateKey),
                )
            }.policyCode,
        )

        val lifecycle = "{\"scripts\":{}}".toByteArray(StandardCharsets.UTF_8)
        assertEquals(
            R2PackageMetadataPolicyCode.LIFECYCLE_FORBIDDEN,
            assertPackagePolicyFailure {
                R2NodePackageFormatCatalog.create(
                    inventory(emptyList(), ROOT_PATH to lifecycle),
                    listOf(lifecycle),
                )
            }.policyCode,
        )

        val accepted = "{}".toByteArray(StandardCharsets.UTF_8)
        val exactInventory = inventory(emptyList(), ROOT_PATH to accepted)
        assertFails<IllegalArgumentException> {
            R2NodePackageFormatCatalog.create(exactInventory, emptyList())
        }
        assertFails<IllegalArgumentException> {
            R2NodePackageFormatCatalog.create(
                exactInventory,
                listOf("[]".toByteArray(StandardCharsets.UTF_8)),
            )
        }
    }

    @Test
    fun C5OrderingCollisionAndCatalogBoundaryClaimsRemainFailClosed() {
        val bytes = "{}".toByteArray(StandardCharsets.UTF_8)
        val collisionFiles = listOf(
            ROOT_PATH to bytes,
            "packages/alpha/Foo/package.json" to bytes,
            "packages/alpha/foo/package.json" to bytes,
            "packages/alpha/package.json" to bytes,
        )
        assertEquals(
            R2PackageMetadataPolicyCode.PATH_INVALID,
            assertPackagePolicyFailure {
                inventory(listOf("packages/alpha"), *collisionFiles.toTypedArray())
            }.policyCode,
        )

        val sha256 = R2Digests.sha256(bytes)
        assertFails<IllegalArgumentException> {
            R2NodePackageFormatRecord(
                packageMetadataPath = ROOT_PATH,
                packageBoundaryPath = "/wrong",
                packageMetadataSize = bytes.size.toLong(),
                packageMetadataSha256 = sha256,
                typeState = R2PackageTypeState.TYPE_ABSENT,
            )
        }
        assertFails<IllegalArgumentException> {
            R2NodePackageFormatRecord(
                packageMetadataPath = ROOT_PATH,
                packageBoundaryPath = "/",
                packageMetadataSize = bytes.size.toLong(),
                packageMetadataSha256 = sha256,
                typeState = R2PackageTypeState.NO_BOUNDARY,
            )
        }

        val catalog = R2NodePackageFormatCatalog.create(
            inventory(emptyList(), ROOT_PATH to bytes),
            listOf(bytes),
        )
        val invalidLookupIds = LinkedHashSet<String>()
        fixture().objects("invalidLookupCases").forEach { testCase ->
            assertEquals(EXACT_INVALID_LOOKUP_CASE_KEYS, testCase.keys)
            val id = testCase.string("id")
            assertTrue("Duplicate invalid lookup case: $id", invalidLookupIds.add(id))
            assertFails<IllegalArgumentException> {
                catalog.nearestRecord(testCase.string("path"))
            }
        }
        assertEquals(5, invalidLookupIds.size)
        assertNull(catalog.recordAtBoundary("/missing"))
    }

    private fun inventory(
        dependencyRoots: List<String>,
        vararg files: Pair<String, ByteArray>,
    ): R2PackageMetadataInventory = R2PackageMetadataInventory.create(
        dependencyRoots = dependencyRoots,
        files = files.map { (path, bytes) ->
            R2PackageMetadataFileDescriptor(
                path = path,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        },
    )

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-node-package-format/catalog-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun packageTypeState(contractValue: String): R2PackageTypeState =
        R2PackageTypeState.entries.singleOrNull { state ->
            state.contractValue == contractValue
        } ?: error("Unknown package type state: $contractValue")

    private fun Map<String, Any?>.string(key: String): String =
        getValue(key) as? String ?: error("$key must be a string.")

    private fun Map<String, Any?>.integer(key: String): Int =
        long(key).toInt().also { value ->
            require(value.toLong() == long(key)) { "$key must fit an Int." }
        }

    private fun Map<String, Any?>.long(key: String): Long =
        (getValue(key) as? R2StrictJson.NumberToken)
            ?.source
            ?.toLongOrNull()
            ?: error("$key must be an integer.")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> =
        getValue(key) as? Map<String, Any?> ?: error("$key must be an object.")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> =
        (getValue(key) as? List<*>)?.map { value ->
            value as? Map<String, Any?> ?: error("$key must contain objects.")
        } ?: error("$key must be an array.")

    private fun Map<String, Any?>.strings(key: String): List<String> =
        (getValue(key) as? List<*>)?.map { value ->
            value as? String ?: error("$key must contain strings.")
        } ?: error("$key must be an array.")

    private fun assertPackagePolicyFailure(
        operation: () -> Unit,
    ): R2PackageMetadataPolicyException =
        assertFails(operation)

    private inline fun <reified T : Throwable> assertFails(operation: () -> Unit): T {
        try {
            operation()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.name}.", error)
        }
        fail("Expected ${T::class.java.name}.")
        error("unreachable")
    }

    private companion object {
        const val ROOT_PATH = R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH
        const val FIXTURE_SCHEMA =
            "autojs6.typescript.node-package-format-catalog-parity"
        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "revision",
            "catalogAuthority",
            "noBoundary",
            "validCases",
            "invalidTypeCases",
            "invalidLookupCases",
        )
        val EXACT_AUTHORITY_KEYS = setOf(
            "schema",
            "schemaRevision",
            "catalogRevision",
            "canonicalDomain",
            "profileSchemaRevision",
            "profileRevision",
            "nodeModuleOutputPolicySchemaRevision",
            "nodeModuleOutputPolicyRevision",
            "nodeModuleOutputPolicyFingerprint",
            "packageMetadataSchemaRevision",
            "packageMetadataPolicyRevision",
            "packageMetadataPolicyFingerprint",
        )
        val EXACT_NO_BOUNDARY_KEYS = setOf(
            "typeState",
            "packageMetadataInventoryFingerprint",
            "boundaryPath",
        )
        val EXACT_VALID_CASE_KEYS = setOf(
            "id",
            "dependencyRoots",
            "files",
            "inventoryCanonicalInput",
            "packageMetadataInventoryFingerprint",
            "catalogCanonicalInput",
            "catalogFingerprint",
            "lookups",
        )
        val EXACT_FILE_KEYS = setOf(
            "path",
            "json",
            "size",
            "sha256",
            "boundaryPath",
            "typeState",
        )
        val EXACT_LOOKUP_KEYS = setOf("path", "boundaryPath", "typeState")
        val EXACT_INVALID_TYPE_CASE_KEYS = setOf("id", "json")
        val EXACT_INVALID_LOOKUP_CASE_KEYS = setOf("id", "path")
    }
}
