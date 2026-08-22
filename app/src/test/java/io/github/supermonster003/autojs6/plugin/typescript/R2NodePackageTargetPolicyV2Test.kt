package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2NodePackageTargetPolicyV2Test {

    @Test
    fun checkedInRevisionTwoFixtureExactlyMirrorsIndependentAuthority() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/r2-package-targets/policy-v2.json"),
        ).use { input -> input.readBytes() }
        val root = R2StrictJson.parseObject(bytes)

        assertEquals(EXACT_FIXTURE_KEYS, root.keys)
        assertEquals(R2NodePackageTargetPolicyV2.SCHEMA, root.getValue("schema"))
        assertNumber(R2NodePackageTargetPolicyV2.SCHEMA_REVISION, root["schemaRevision"])
        assertNumber(R2NodePackageTargetPolicyV2.POLICY_REVISION, root["policyRevision"])
        assertEquals(R2NodePackageTargetPolicyV2.fingerprint, root["fingerprint"])
        assertNumber(
            org.autojs.plugin.typescript.api.TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            root["profileSchemaRevision"],
        )
        assertNumber(R2NodeModuleOutputPolicy.NODE_PROFILE_REVISION, root["profileRevision"])
        assertNumber(
            R2NodeModuleOutputPolicy.SCHEMA_REVISION,
            root["nodeModuleOutputPolicySchemaRevision"],
        )
        assertNumber(
            R2NodeModuleOutputPolicy.POLICY_REVISION,
            root["nodeModuleOutputPolicyRevision"],
        )
        assertEquals(R2NodeModuleOutputPolicy.fingerprint, root["nodeModuleOutputPolicyFingerprint"])
        assertNumber(
            R2NodeModuleOutputPolicy.RESOLVER_POLICY_REVISION,
            root["resolverPolicyRevision"],
        )
        assertEquals(
            R2NodeModuleOutputPolicy.RESOLVER_CONDITION_SOURCE,
            root["resolverConditionSource"],
        )
        assertNumber(
            R2NodePackageTargetPolicyV2.TRUSTED_CUSTOM_CONDITION_COUNT,
            root["resolverTrustedCustomConditionCount"],
        )
        assertEquals(R2PackageMetadataPolicy.fingerprint, root["packageMetadataPolicyFingerprint"])
        assertEquals(R2NodePackageTargetPolicyV2.DEPENDENCY_ROOT_PREFIX, root["dependencyRootPrefix"])
        assertEquals(
            R2NodePackageTargetPolicyV2.INVENTORY_CANONICAL_DOMAIN,
            root["inventoryCanonicalDomain"],
        )
        assertEquals(R2NodePackageTargetPolicyV2.METADATA_BINDING_MODE, root["metadataBindingMode"])
        assertEquals(
            R2NodePackageTargetPolicyV2.SHARED_AGGREGATE_BUDGET_MODE,
            root["sharedAggregateBudgetMode"],
        )
        assertEquals(
            R2NodePackageTargetPolicyV2.SAFE_RELATIVE_PATH_GRAMMAR,
            root["safeRelativePathGrammar"],
        )
        assertEquals(R2NodePackageTargetPolicyV2.SOURCE_KIND_MODE, root["sourceKindMode"])
        assertEquals(R2NodeModuleOutputPolicy.RESOLVER_V1_COMPATIBILITY, root["v1Compatibility"])
        assertEquals(
            R2NodePackageTargetPolicyV2.allowedExecutableSuffixes,
            root.requireStringList("allowedExecutableSuffixes"),
        )
        assertEquals(
            R2NodePackageTargetPolicyV2.allowedDeclarationSuffixes,
            root.requireStringList("allowedDeclarationSuffixes"),
        )
        assertEquals(
            R2PackageTargetPolicyCode.entries.map { code -> code.contractValue },
            root.requireStringList("policyCodes"),
        )
        val budgets = root.requireObject("budgets")
        assertEquals(
            setOf(
                "maximumFiles",
                "maximumPathChars",
                "maximumAggregatePathChars",
                "maximumFileBytes",
                "maximumTotalBytes",
            ),
            budgets.keys,
        )
        assertNumber(R2NodePackageTargetPolicyV2.MAXIMUM_FILES, budgets["maximumFiles"])
        assertNumber(R2NodePackageTargetPolicyV2.MAXIMUM_PATH_CHARS, budgets["maximumPathChars"])
        assertNumber(
            R2NodePackageTargetPolicyV2.MAXIMUM_AGGREGATE_PATH_CHARS,
            budgets["maximumAggregatePathChars"],
        )
        assertNumber(
            R2NodePackageTargetPolicyV2.MAXIMUM_FILE_BYTES.toInt(),
            budgets["maximumFileBytes"],
        )
        assertNumber(
            R2NodePackageTargetPolicyV2.MAXIMUM_TOTAL_BYTES.toInt(),
            budgets["maximumTotalBytes"],
        )

        val metadata = metadataInventory("packages/pkg")
        val inventory = R2NodePackageTargetV2Inventory.create(
            packageMetadataInventory = metadata,
            files = listOf(target("packages/pkg/index.mts")),
        )
        val example = root.requireObject("example")
        assertEquals(metadata.fingerprint, example["packageMetadataInventoryFingerprint"])
        assertEquals(inventory.canonicalInput, example["canonicalInput"])
        assertEquals(inventory.fingerprint, example["fingerprint"])

        assertEquals(POLICY_FINGERPRINT, R2NodePackageTargetPolicyV2.fingerprint)
        assertEquals(
            R2Digests.sha256(
                R2NodePackageTargetPolicyV2.canonicalInput.toByteArray(StandardCharsets.US_ASCII),
            ),
            R2NodePackageTargetPolicyV2.fingerprint,
        )
        assertFalse(R2PackageTargetPolicy.fingerprint == R2NodePackageTargetPolicyV2.fingerprint)
        assertFalse(
            R2PackageTargetPolicy.INVENTORY_CANONICAL_DOMAIN ==
                R2NodePackageTargetPolicyV2.INVENTORY_CANONICAL_DOMAIN,
        )
    }

    @Test
    fun revisionTwoAcceptsExactExecutableAndDeclarationKindsDeclarationFirst() {
        val paths = listOf(
            "packages/pkg/a.ts" to R2NodePackageTargetKind.EXECUTABLE_SOURCE,
            "packages/pkg/b.mts" to R2NodePackageTargetKind.EXECUTABLE_SOURCE,
            "packages/pkg/c.cts" to R2NodePackageTargetKind.EXECUTABLE_SOURCE,
            "packages/pkg/d.d.ts" to R2NodePackageTargetKind.DECLARATION,
            "packages/pkg/e.d.mts" to R2NodePackageTargetKind.DECLARATION,
            "packages/pkg/f.d.cts" to R2NodePackageTargetKind.DECLARATION,
        )
        paths.forEach { (path, expectedKind) ->
            assertEquals(expectedKind, target(path).kind)
        }

        val metadata = metadataInventory("packages/pkg")
        val files = paths.map { (path, _) -> target(path) }.sortedBy { file -> file.path }
        val inventory = R2NodePackageTargetV2Inventory.create(metadata, files)
        assertEquals(6, inventory.files.size)
        assertEquals(3, inventory.files.count { file ->
            file.kind == R2NodePackageTargetKind.DECLARATION
        })
        assertEquals(
            listOf(
                R2NodeResolutionTargetKind.PROJECT_SOURCE,
                R2NodeResolutionTargetKind.PROJECT_SOURCE,
                R2NodeResolutionTargetKind.PROJECT_SOURCE,
                R2NodeResolutionTargetKind.DECLARATION,
                R2NodeResolutionTargetKind.DECLARATION,
                R2NodeResolutionTargetKind.DECLARATION,
            ),
            inventory.toResolutionTargets()
                .sortedBy { resolutionTarget -> resolutionTarget.path.substringAfterLast('/') }
                .map(R2NodeResolutionTarget::kind),
        )
        assertEquals(metadata.fingerprint, inventory.authority.packageMetadataInventoryFingerprint)
        assertEquals(2, inventory.authority.policyRevision)
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            inventory.authority.profileAuthority,
        )
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (inventory.files as MutableList<R2NodePackageTargetV2FileDescriptor>).clear()
        }
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (R2NodePackageTargetPolicyV2.allowedExecutableSuffixes as MutableList<String>).clear()
        }
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (R2NodePackageTargetPolicyV2.allowedDeclarationSuffixes as MutableList<String>).clear()
        }
    }

    @Test
    fun revisionTwoRejectsRuntimeOutputsUppercaseAndUnsafePaths() {
        listOf(
            "packages/pkg/index.js",
            "packages/pkg/index.mjs",
            "packages/pkg/index.cjs",
            "packages/pkg/index.tsx",
            "packages/pkg/index.json",
            "packages/pkg/index.MTS",
            "packages/pkg/index.d.CTS",
        ).forEach { path ->
            assertPolicyFailure(R2PackageTargetPolicyCode.SOURCE_KIND_UNSUPPORTED) {
                target(path)
            }
        }
        listOf(
            "/packages/pkg/index.mts",
            "packages/pkg/../index.mts",
            "packages/pkg/node_modules/index.mts",
            "packages/pkg/package.json",
            "packages/pkg/index,one.mts",
            "packages/pkg/index one.mts",
        ).forEach { path ->
            assertPolicyFailure(R2PackageTargetPolicyCode.PATH_INVALID) {
                target(path)
            }
        }
    }

    @Test
    fun revisionOneStillRejectsEveryRevisionTwoOnlyKind() {
        listOf(
            "packages/pkg/index.mts",
            "packages/pkg/index.cts",
            "packages/pkg/index.mjs",
            "packages/pkg/index.cjs",
            "packages/pkg/index.d.mts",
            "packages/pkg/index.d.cts",
        ).forEach { path ->
            assertPolicyFailure(R2PackageTargetPolicyCode.SOURCE_KIND_UNSUPPORTED) {
                R2PackageTargetFileDescriptor(path, 0, EMPTY_SHA256)
            }
        }
        assertEquals(V1_POLICY_FINGERPRINT, R2PackageTargetPolicy.fingerprint)
        assertEquals(1, R2PackageTargetPolicy.POLICY_REVISION)
    }

    @Test
    fun revisionTwoPreservesIdentityOrderingCaseRootAndAuthorityFences() {
        val metadata = metadataInventory("packages/pkg")
        val otherMetadata = metadataInventory("packages/other")

        assertPolicyFailure(R2PackageTargetPolicyCode.METADATA_IDENTITY_INVALID) {
            R2NodePackageTargetV2Inventory.create(
                metadata,
                listOf(target("packages/pkg/a.mts")),
                otherMetadata.fingerprint,
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            R2NodePackageTargetV2Inventory.create(
                metadata,
                listOf(target("packages/pkg/z.mts"), target("packages/pkg/a.mts")),
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.CASE_COLLISION) {
            R2NodePackageTargetV2Inventory.create(
                metadata,
                listOf(target("packages/pkg/A.mts"), target("packages/pkg/a.mts")),
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.ROOT_NOT_ALLOWED) {
            R2NodePackageTargetV2Inventory.create(
                metadata,
                listOf(target("packages/other/a.mts")),
            )
        }

        val inventory = R2NodePackageTargetV2Inventory.create(
            metadata,
            listOf(target("packages/pkg/a.mts")),
        )
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            R2NodePackageTargetV2Authority(
                fingerprint = inventory.fingerprint,
                packageMetadataInventoryFingerprint = metadata.fingerprint,
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.INVENTORY_INVALID) {
            R2NodePackageTargetV2Authority(
                fingerprint = inventory.fingerprint,
                packageMetadataInventoryFingerprint = metadata.fingerprint,
                policyRevision = 1,
            )
        }
    }

    @Test
    fun revisionTwoBudgetsAreInclusiveAndUseTheFrozenSevenCodeCatalog() {
        val metadata = metadataInventory("packages/pkg")
        val exact = R2NodePackageTargetV2Inventory.create(
            metadata,
            listOf(
                target(
                    "packages/pkg/a.mts",
                    R2NodePackageTargetPolicyV2.MAXIMUM_TOTAL_BYTES,
                ),
            ),
        )
        assertEquals(R2NodePackageTargetPolicyV2.MAXIMUM_TOTAL_BYTES, exact.totalBytes)
        assertEquals("required-non-additive", R2NodePackageTargetPolicyV2.SHARED_AGGREGATE_BUDGET_MODE)

        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            target(
                "packages/pkg/a.mts",
                R2NodePackageTargetPolicyV2.MAXIMUM_FILE_BYTES + 1,
            )
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            R2NodePackageTargetV2Inventory.create(
                metadata,
                listOf(
                    target(
                        "packages/pkg/a.mts",
                        R2NodePackageTargetPolicyV2.MAXIMUM_TOTAL_BYTES,
                    ),
                    target("packages/pkg/b.mts", 1),
                ),
            )
        }
        val tooMany = (0..R2NodePackageTargetPolicyV2.MAXIMUM_FILES).map { index ->
            target("packages/pkg/${index.toString().padStart(3, '0')}.mts")
        }
        assertPolicyFailure(R2PackageTargetPolicyCode.QUOTA_EXCEEDED) {
            R2NodePackageTargetV2Inventory.create(metadata, tooMany)
        }
        assertEquals(
            listOf(
                "PACKAGE_TARGET_PATH_INVALID",
                "PACKAGE_TARGET_ROOT_NOT_ALLOWED",
                "PACKAGE_TARGET_SOURCE_KIND_UNSUPPORTED",
                "PACKAGE_TARGET_INVENTORY_INVALID",
                "PACKAGE_TARGET_CASE_COLLISION",
                "PACKAGE_TARGET_QUOTA_EXCEEDED",
                "PACKAGE_TARGET_METADATA_IDENTITY_INVALID",
            ),
            R2PackageTargetPolicyCode.entries.map { code -> code.contractValue },
        )
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
    ) = R2NodePackageTargetV2FileDescriptor(path, size, sha256)

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
            throw error
        }
        fail("Expected ${T::class.java.name}.")
        error("unreachable")
    }

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    private fun assertNumber(expected: Int, actual: Any?) =
        assertEquals(expected.toString(), (actual as R2StrictJson.NumberToken).source)

    private companion object {
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        const val POLICY_FINGERPRINT =
            "175cc694dae3e925d66eac3f77f308c95dc0db7b939238a33022d27812578224"
        const val V1_POLICY_FINGERPRINT =
            "e34f4825d8f75ba1b674849ff79c2be8f9c1b88fe940add86e6e466a139bbbaf"
        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "schemaRevision",
            "policyRevision",
            "fingerprint",
            "profileSchemaRevision",
            "profileRevision",
            "nodeModuleOutputPolicySchemaRevision",
            "nodeModuleOutputPolicyRevision",
            "nodeModuleOutputPolicyFingerprint",
            "resolverPolicyRevision",
            "resolverConditionSource",
            "resolverTrustedCustomConditionCount",
            "packageMetadataPolicyFingerprint",
            "dependencyRootPrefix",
            "inventoryCanonicalDomain",
            "metadataBindingMode",
            "sharedAggregateBudgetMode",
            "safeRelativePathGrammar",
            "sourceKindMode",
            "allowedExecutableSuffixes",
            "allowedDeclarationSuffixes",
            "v1Compatibility",
            "budgets",
            "policyCodes",
            "example",
        )
    }
}
