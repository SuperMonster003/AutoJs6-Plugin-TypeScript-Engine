package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2PackageTargetPolicyTest {

    @Test
    fun checkedInFixtureExactlyMirrorsThePolicyAuthority() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/r2-package-targets/policy-v1.json"),
        ).use { input -> input.readBytes() }
        val root = R2StrictJson.parseObject(bytes)

        assertEquals(EXACT_FIXTURE_KEYS, root.keys)
        assertEquals(R2PackageTargetPolicy.SCHEMA, root.getValue("schema"))
        assertNumber(R2PackageTargetPolicy.SCHEMA_REVISION, root.getValue("schemaRevision"))
        assertNumber(R2PackageTargetPolicy.POLICY_REVISION, root.getValue("policyRevision"))
        assertEquals(R2PackageTargetPolicy.fingerprint, root.getValue("fingerprint"))
        assertEquals(
            R2PackageTargetPolicy.DEPENDENCY_ROOT_PREFIX,
            root.getValue("dependencyRootPrefix"),
        )
        assertEquals(
            R2PackageTargetPolicy.INVENTORY_CANONICAL_DOMAIN,
            root.getValue("inventoryCanonicalDomain"),
        )
        assertEquals(
            R2PackageTargetPolicy.METADATA_BINDING_MODE,
            root.getValue("metadataBindingMode"),
        )
        assertEquals(
            R2PackageTargetPolicy.SHARED_AGGREGATE_BUDGET_MODE,
            root.getValue("sharedAggregateBudgetMode"),
        )
        assertEquals(
            R2PackageTargetPolicy.SAFE_RELATIVE_PATH_GRAMMAR,
            root.getValue("safeRelativePathGrammar"),
        )
        assertEquals(
            R2PackageTargetPolicy.allowedSourceSuffixes.sorted(),
            root.requireStringList("allowedSourceSuffixes"),
        )
        val budgets = root.requireObject("budgets")
        assertEquals(EXACT_BUDGET_KEYS, budgets.keys)
        assertNumber(R2PackageTargetPolicy.MAXIMUM_FILES, budgets.getValue("maximumFiles"))
        assertNumber(
            R2PackageTargetPolicy.MAXIMUM_PATH_CHARS,
            budgets.getValue("maximumPathChars"),
        )
        assertNumber(
            R2PackageTargetPolicy.MAXIMUM_AGGREGATE_PATH_CHARS,
            budgets.getValue("maximumAggregatePathChars"),
        )
        assertNumber(
            R2PackageTargetPolicy.MAXIMUM_FILE_BYTES,
            budgets.getValue("maximumFileBytes"),
        )
        assertNumber(
            R2PackageTargetPolicy.MAXIMUM_TOTAL_BYTES,
            budgets.getValue("maximumTotalBytes"),
        )
        assertEquals(
            R2PackageTargetPolicyCode.values().map { code -> code.contractValue },
            root.requireStringList("policyCodes"),
        )

        val metadata = metadataInventory("packages/pkg")
        val inventory = R2PackageTargetInventory.create(
            packageMetadataInventory = metadata,
            files = listOf(target("packages/pkg/index.ts")),
        )
        val example = root.requireObject("example")
        assertEquals(metadata.fingerprint, example.getValue("packageMetadataInventoryFingerprint"))
        assertEquals(inventory.canonicalInput, example.getValue("canonicalInput"))
        assertEquals(inventory.fingerprint, example.getValue("fingerprint"))
    }

    @Test
    fun policyCanonicalInputAndFingerprintAreStableAsciiAndIndependentFromC5() {
        val expected =
            "autojs6.typescript.package-target-policy.v1" +
                "|schemaRevision=1" +
                "|policyRevision=1" +
                "|dependencyRootPrefix=packages/" +
                "|maximumFiles=256" +
                "|maximumPathChars=1024" +
                "|maximumAggregatePathChars=32768" +
                "|maximumFileBytes=8388608" +
                "|maximumTotalBytes=8388608" +
                "|metadataBindingMode=exact-c5-inventory-fingerprint" +
                "|sharedAggregateBudgetMode=required-non-additive" +
                "|safeRelativePathGrammar=^[A-Za-z0-9@._/+~-]+$" +
                "|allowedSourceSuffixes=.d.ts,.ts"

        assertEquals(expected, R2PackageTargetPolicy.canonicalInput)
        assertTrue(expected.all { character -> character.code in 0x20..0x7e })
        assertEquals(POLICY_FINGERPRINT, R2PackageTargetPolicy.fingerprint)
        assertEquals(
            R2Digests.sha256(expected.toByteArray(StandardCharsets.US_ASCII)),
            R2PackageTargetPolicy.fingerprint,
        )
        assertEquals(C5_POLICY_FINGERPRINT, R2PackageMetadataPolicy.fingerprint)
        assertFalse(R2PackageMetadataPolicy.fingerprint == R2PackageTargetPolicy.fingerprint)
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (R2PackageTargetPolicy.allowedSourceSuffixes as MutableSet<String>).clear()
        }
    }

    @Test
    fun validInventoryFreezesFilesCanonicalIdentityAndAuthority() {
        val metadata = metadataInventory("packages/@scope/alpha", "packages/beta")
        val files = mutableListOf(
            target("packages/@scope/alpha/index.d.ts", 5),
            target("packages/@scope/alpha/index.ts", 7),
            target("packages/beta/nested/Value.ts", 11),
        )
        val inventory = R2PackageTargetInventory.create(metadata, files)
        val expectedCanonical =
            R2PackageTargetPolicy.INVENTORY_CANONICAL_DOMAIN +
                "|packageMetadataInventoryFingerprint=${metadata.fingerprint}" +
                "|file=packages/@scope/alpha/index.d.ts,5,$EMPTY_SHA256" +
                "|file=packages/@scope/alpha/index.ts,7,$EMPTY_SHA256" +
                "|file=packages/beta/nested/Value.ts,11,$EMPTY_SHA256"

        assertEquals(expectedCanonical, inventory.canonicalInput)
        assertTrue(expectedCanonical.all { character -> character.code in 0x20..0x7e })
        assertEquals(23L, inventory.totalBytes)
        assertEquals(files.sumOf { file -> file.path.length }, inventory.totalPathChars)
        assertEquals(
            R2Digests.sha256(expectedCanonical.toByteArray(StandardCharsets.US_ASCII)),
            inventory.fingerprint,
        )
        assertEquals(metadata.fingerprint, inventory.packageMetadataInventoryFingerprint)
        assertEquals(inventory.fingerprint, inventory.authority.fingerprint)
        assertEquals(
            metadata.fingerprint,
            inventory.authority.packageMetadataInventoryFingerprint,
        )
        assertEquals(R2PackageTargetPolicy.POLICY_REVISION, inventory.authority.policyRevision)
        assertEquals(R2PackageTargetPolicy.fingerprint, inventory.authority.policyFingerprint)
        assertEquals(files.map { file -> file.path }.toSet(), inventory.filePaths)
        assertTrue(
            R2PackageTargetPolicy.isExplicitlyAllowed(
                "packages/@scope/alpha/index.ts",
                inventory,
            ),
        )
        assertFalse(
            R2PackageTargetPolicy.isExplicitlyAllowed(
                "packages/@scope/alpha/other.ts",
                inventory,
            ),
        )
        assertEquals(
            "/packages/@scope/alpha/index.ts",
            R2PackageTargetPolicy.toVfsPath("packages/@scope/alpha/index.ts"),
        )

        files.clear()
        assertEquals(3, inventory.files.size)
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (inventory.files as MutableList<R2PackageTargetFileDescriptor>).clear()
        }
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (inventory.filePaths as MutableSet<String>).clear()
        }
    }

    @Test
    fun inventoryExplicitlyBindsTheExactC5MetadataIdentity() {
        val metadata = metadataInventory("packages/pkg")
        val expandedMetadata = metadataInventory("packages/other", "packages/pkg")
        val files = listOf(target("packages/pkg/index.ts"))
        val first = R2PackageTargetInventory.create(metadata, files)
        val second = R2PackageTargetInventory.create(expandedMetadata, files)

        assertFalse(first.packageMetadataInventoryFingerprint == second.packageMetadataInventoryFingerprint)
        assertFalse(first.canonicalInput == second.canonicalInput)
        assertFalse(first.fingerprint == second.fingerprint)
        assertPolicyFailure(R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID) {
            R2PackageTargetInventory.create(
                packageMetadataInventory = metadata,
                files = files,
                boundPackageMetadataInventoryFingerprint = "0".repeat(64),
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID) {
            R2PackageTargetInventory.create(
                packageMetadataInventory = metadata,
                files = files,
                boundPackageMetadataInventoryFingerprint = "not-a-sha256",
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            R2PackageTargetAuthority(
                fingerprint = first.fingerprint,
                packageMetadataInventoryFingerprint = metadata.fingerprint,
                policyRevision = R2PackageTargetPolicy.POLICY_REVISION + 1,
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            R2PackageTargetAuthority(
                fingerprint = first.fingerprint,
                packageMetadataInventoryFingerprint = metadata.fingerprint,
                policyFingerprint = "0".repeat(64),
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID) {
            R2PackageTargetAuthority(
                fingerprint = first.fingerprint,
                packageMetadataInventoryFingerprint = "invalid",
            )
        }
    }

    @Test
    fun pathsAreSafeAsciiRelativeAndUseOnlyTsOrDeclarationKinds() {
        val invalidPaths = listOf(
            "/packages/pkg/index.ts",
            "sources/index.ts",
            "packages/pkg/package.json",
            "packages/pkg/Package.JSON",
            "packages/pkg/node_modules/child.ts",
            "packages/pkg/Node_Modules/child.ts",
            "packages/pkg/./index.ts",
            "packages/pkg/../index.ts",
            "packages/pkg//index.ts",
            "packages/pkg\\index.ts",
            "packages/pkg/中文.ts",
            "packages/pkg/with space.ts",
            "packages/pkg/comma,.ts",
            "packages/pkg/pipe|.ts",
            "packages/pkg/query?.ts",
            "packages/pkg/http:target.ts",
        )
        invalidPaths.forEach { path ->
            assertPolicyFailure(R2PackageTargetPolicyCode.PATH_INVALID) {
                target(path)
            }
        }

        listOf(
            "packages/pkg/index",
            "packages/pkg/index.js",
            "packages/pkg/index.tsx",
            "packages/pkg/index.mts",
            "packages/pkg/index.cts",
            "packages/pkg/index.TS",
        ).forEach { path ->
            assertPolicyFailure(R2PackageTargetPolicyCode.SOURCE_KIND_UNSUPPORTED) {
                target(path)
            }
        }

        target("packages/pkg/index.ts")
        target("packages/pkg/index.d.ts")
        target("packages/pkg/Nested/Value.ts")
        val exactMaximumPath = "packages/pkg/" +
            "a".repeat(R2PackageTargetPolicy.MAXIMUM_PATH_CHARS - "packages/pkg/".length - 3) +
            ".ts"
        assertEquals(R2PackageTargetPolicy.MAXIMUM_PATH_CHARS, exactMaximumPath.length)
        target(exactMaximumPath)
        assertPolicyFailure(R2PackageTargetPolicyCode.PATH_INVALID) {
            target("packages/pkg/" + "a".repeat(exactMaximumPath.length) + ".ts")
        }
    }

    @Test
    fun targetsMustBelongToOneExactC5DependencyRoot() {
        val metadata = metadataInventory("packages/@scope/alpha", "packages/beta")
        val accepted = R2PackageTargetInventory.create(
            metadata,
            listOf(
                target("packages/@scope/alpha/index.d.ts"),
                target("packages/beta/nested/index.ts"),
            ),
        )
        assertEquals(2, accepted.files.size)

        listOf(
            "packages/@scope/other/index.ts",
            "packages/beta.ts",
            "packages/other/index.ts",
        ).forEach { path ->
            assertPolicyFailure(R2PackageTargetPolicyCode.ROOT_NOT_ALLOWED) {
                R2PackageTargetInventory.create(metadata, listOf(target(path)))
            }
        }
    }

    @Test
    fun inventoryRejectsOrderingDuplicatesAndAsciiCaseFoldCollisions() {
        val metadata = metadataInventory("packages/pkg")
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            R2PackageTargetInventory.create(
                metadata,
                listOf(
                    target("packages/pkg/z.ts"),
                    target("packages/pkg/a.ts"),
                ),
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            R2PackageTargetInventory.create(
                metadata,
                listOf(
                    target("packages/pkg/a.ts"),
                    target("packages/pkg/a.ts"),
                ),
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.CASE_COLLISION) {
            R2PackageTargetInventory.create(
                metadata,
                listOf(
                    target("packages/pkg/Nested/index.ts"),
                    target("packages/pkg/nested/index.ts"),
                ),
            )
        }
    }

    @Test
    fun fileCountAndAggregatePathBudgetsAreInclusive() {
        val metadata = metadataInventory("packages/pkg")
        val exactPathFiles = (0 until R2PackageTargetPolicy.MAXIMUM_FILES).map { index ->
            val path = "packages/pkg/${index.toString().padStart(3, '0')}" +
                "a".repeat(109) +
                ".ts"
            assertEquals(128, path.length)
            target(path)
        }
        val exact = R2PackageTargetInventory.create(metadata, exactPathFiles)
        assertEquals(R2PackageTargetPolicy.MAXIMUM_FILES, exact.files.size)
        assertEquals(R2PackageTargetPolicy.MAXIMUM_AGGREGATE_PATH_CHARS, exact.totalPathChars)

        val tooMany = (0..R2PackageTargetPolicy.MAXIMUM_FILES).map { index ->
            target("packages/pkg/${index.toString().padStart(3, '0')}.ts")
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            R2PackageTargetInventory.create(metadata, tooMany)
        }

        val tooManyPathChars = exactPathFiles.dropLast(1) +
            target(exactPathFiles.last().path.replace(".ts", "a.ts"))
        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            R2PackageTargetInventory.create(metadata, tooManyPathChars)
        }
    }

    @Test
    fun fileAndAggregateByteBudgetsAreInclusiveAndNonAdditive() {
        val metadata = metadataInventory("packages/pkg")
        val exact = R2PackageTargetInventory.create(
            metadata,
            listOf(target("packages/pkg/a.ts", R2PackageTargetPolicy.MAXIMUM_TOTAL_BYTES)),
        )
        assertEquals(R2PackageTargetPolicy.MAXIMUM_TOTAL_BYTES, exact.totalBytes)
        assertEquals("required-non-additive", R2PackageTargetPolicy.SHARED_AGGREGATE_BUDGET_MODE)

        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            target(
                "packages/pkg/a.ts",
                R2PackageTargetPolicy.MAXIMUM_FILE_BYTES + 1,
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            target("packages/pkg/a.ts", -1)
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            target("packages/pkg/a.ts", sha256 = "invalid")
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            R2PackageTargetInventory.create(
                metadata,
                listOf(
                    target("packages/pkg/a.ts", R2PackageTargetPolicy.MAXIMUM_TOTAL_BYTES),
                    target("packages/pkg/b.ts", 1),
                ),
            )
        }
    }

    @Test
    fun stableFailureCatalogUsesOnlyPackageTargetNamespace() {
        val expected = listOf(
            "PACKAGE_TARGET_PATH_INVALID",
            "PACKAGE_TARGET_ROOT_NOT_ALLOWED",
            "PACKAGE_TARGET_SOURCE_KIND_UNSUPPORTED",
            "PACKAGE_TARGET_INVENTORY_INVALID",
            "PACKAGE_TARGET_CASE_COLLISION",
            "PACKAGE_TARGET_QUOTA_EXCEEDED",
            "PACKAGE_TARGET_METADATA_IDENTITY_INVALID",
        )
        assertEquals(
            expected,
            R2PackageTargetPolicyCode.values().map { code -> code.contractValue },
        )
        assertTrue(expected.all { code -> code.startsWith("PACKAGE_TARGET_") })
    }

    private fun metadataInventory(vararg roots: String): R2PackageMetadataInventory {
        val sortedRoots = roots.toList().sorted()
        val files = buildList {
            add(R2PackageMetadataFileDescriptor("package.json", 0, EMPTY_SHA256))
            sortedRoots.forEach { root ->
                add(R2PackageMetadataFileDescriptor("$root/package.json", 0, EMPTY_SHA256))
            }
        }.sortedBy(R2PackageMetadataFileDescriptor::path)
        return R2PackageMetadataInventory.create(sortedRoots, files)
    }

    private fun target(
        path: String,
        size: Long = 0,
        sha256: String = EMPTY_SHA256,
    ) = R2PackageTargetFileDescriptor(path, size, sha256)

    private fun assertPolicyFailure(
        expected: R2PackageTargetPolicyCode,
        block: () -> Unit,
    ): R2PackageTargetPolicyException {
        try {
            block()
        } catch (error: R2PackageTargetPolicyException) {
            assertEquals(expected, error.policyCode)
            return error
        } catch (error: Throwable) {
            throw AssertionError(
                "Expected R2PackageTargetPolicyException, got ${error::class.java.name}.",
                error,
            )
        }
        fail("Expected R2PackageTargetPolicyException.")
        error("unreachable")
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.name}.", error)
        }
        fail("Expected ${T::class.java.name}.")
        error("unreachable")
    }

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    private fun assertNumber(
        expected: Int,
        actual: Any?,
    ) = assertEquals(expected.toString(), (actual as R2StrictJson.NumberToken).source)

    private fun assertNumber(
        expected: Long,
        actual: Any?,
    ) = assertEquals(expected.toString(), (actual as R2StrictJson.NumberToken).source)

    private companion object {
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val C5_POLICY_FINGERPRINT =
            "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97"
        const val POLICY_FINGERPRINT =
            "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf"
        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "schemaRevision",
            "policyRevision",
            "fingerprint",
            "dependencyRootPrefix",
            "inventoryCanonicalDomain",
            "metadataBindingMode",
            "sharedAggregateBudgetMode",
            "safeRelativePathGrammar",
            "allowedSourceSuffixes",
            "budgets",
            "policyCodes",
            "example",
        )
        val EXACT_BUDGET_KEYS = setOf(
            "maximumFiles",
            "maximumPathChars",
            "maximumAggregatePathChars",
            "maximumFileBytes",
            "maximumTotalBytes",
        )
    }
}
