package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2PackageMetadataPolicyTest {

    @Test
    fun checkedInFixtureExactlyMirrorsThePolicyAuthority() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/r2-package-metadata/policy-v1.json"),
        ).use { input -> input.readBytes() }
        val root = R2StrictJson.parseObject(bytes)

        assertEquals(EXACT_FIXTURE_KEYS, root.keys)
        assertEquals(R2PackageMetadataPolicy.SCHEMA, root.getValue("schema"))
        assertNumber(R2PackageMetadataPolicy.SCHEMA_REVISION, root.getValue("schemaRevision"))
        assertNumber(R2PackageMetadataPolicy.POLICY_REVISION, root.getValue("policyRevision"))
        assertEquals(R2PackageMetadataPolicy.fingerprint, root.getValue("fingerprint"))
        assertEquals(
            R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH,
            root.getValue("projectPackagePath"),
        )
        assertEquals(
            R2PackageMetadataPolicy.DEPENDENCY_ROOT_PREFIX,
            root.getValue("dependencyRootPrefix"),
        )
        assertEquals(
            R2PackageMetadataPolicy.INVENTORY_CANONICAL_DOMAIN,
            root.getValue("inventoryCanonicalDomain"),
        )
        assertEquals(
            R2PackageMetadataPolicy.SAFE_RELATIVE_PATH_GRAMMAR,
            root.getValue("safeRelativePathGrammar"),
        )
        val budgets = root.requireObject("budgets")
        assertEquals(EXACT_BUDGET_KEYS, budgets.keys)
        assertNumber(
            R2PackageMetadataPolicy.MAXIMUM_DEPENDENCY_ROOTS,
            budgets.getValue("maximumDependencyRoots"),
        )
        assertNumber(R2PackageMetadataPolicy.MAXIMUM_FILES, budgets.getValue("maximumFiles"))
        assertNumber(
            R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES,
            budgets.getValue("maximumFileBytes"),
        )
        assertNumber(
            R2PackageMetadataPolicy.MAXIMUM_TOTAL_BYTES,
            budgets.getValue("maximumTotalBytes"),
        )
        assertNumber(
            R2PackageMetadataPolicy.MAXIMUM_JSON_DEPTH,
            budgets.getValue("maximumJsonDepth"),
        )
        assertNumber(
            R2PackageMetadataPolicy.MAXIMUM_JSON_NODES,
            budgets.getValue("maximumJsonNodes"),
        )
        assertEquals(
            R2PackageMetadataPolicy.forbiddenTopLevelKeys.sorted(),
            root.requireStringList("forbiddenTopLevelKeys"),
        )
        assertEquals(R2PackageMetadataPolicy.JSON_MODE, root.getValue("jsonMode"))
        assertEquals(R2PackageMetadataPolicy.RESOLVER_MODE, root.getValue("resolverMode"))
        assertEquals(
            R2PackageMetadataPolicyCode.values().map { code -> code.contractValue },
            root.requireStringList("policyCodes"),
        )
        val example = root.requireObject("emptyDependencyRootsExample")
        val inventory = R2PackageMetadataInventory.create(
            dependencyRoots = emptyList(),
            files = listOf(
                descriptor(
                    path = "package.json",
                    size = EMPTY_PACKAGE_JSON.size.toLong(),
                    sha256 = R2Digests.sha256(EMPTY_PACKAGE_JSON),
                ),
            ),
        )
        assertEquals(inventory.canonicalInput, example.getValue("canonicalInput"))
        assertEquals(inventory.fingerprint, example.getValue("fingerprint"))
    }

    @Test
    fun policyCanonicalInputAndFingerprintAreStablePrintableAscii() {
        val expected =
            "autojs6.typescript.package-metadata-policy.v1" +
                "|schemaRevision=1" +
                "|policyRevision=1" +
                "|projectPackagePath=package.json" +
                "|dependencyRootPrefix=packages/" +
                "|maximumDependencyRoots=128" +
                "|maximumFiles=256" +
                "|maximumFileBytes=65536" +
                "|maximumTotalBytes=1048576" +
                "|maximumJsonDepth=16" +
                "|maximumJsonNodes=4096" +
                "|forbiddenTopLevelKeys=scripts" +
                "|safeRelativePathGrammar=^[A-Za-z0-9@._/+~-]+$" +
                "|jsonMode=strict-utf8-duplicate-safe-raw" +
                "|resolverMode=raw-not-resolver-authority"

        assertEquals(expected, R2PackageMetadataPolicy.canonicalInput)
        assertTrue(expected.all { character -> character.code in 0x20..0x7e })
        assertEquals(
            "fab8fec29ed0f0a1de65594620e233c9a9bef2fa514dac16c3079d5570325e97",
            R2PackageMetadataPolicy.fingerprint,
        )
        assertEquals(
            R2Digests.sha256(expected.toByteArray(StandardCharsets.US_ASCII)),
            R2PackageMetadataPolicy.fingerprint,
        )
    }

    @Test
    fun validInventoryFreezesRootsFilesCanonicalIdentityAndAuthority() {
        val roots = mutableListOf("packages/@scope/alpha", "packages/beta")
        val files = mutableListOf(
            descriptor("package.json", 2),
            descriptor("packages/@scope/alpha/package.json", 3),
            descriptor("packages/@scope/alpha/sub/package.json", 5),
            descriptor("packages/beta/package.json", 7),
        )
        val inventory = R2PackageMetadataInventory.create(roots, files)
        val expectedCanonical =
            "autojs6.typescript.package-metadata.inventory.v1" +
                "|root=packages/@scope/alpha" +
                "|root=packages/beta" +
                "|file=package.json,2,$EMPTY_SHA256" +
                "|file=packages/@scope/alpha/package.json,3,$EMPTY_SHA256" +
                "|file=packages/@scope/alpha/sub/package.json,5,$EMPTY_SHA256" +
                "|file=packages/beta/package.json,7,$EMPTY_SHA256"

        assertEquals(expectedCanonical, inventory.canonicalInput)
        assertEquals(17L, inventory.totalBytes)
        assertEquals(
            R2Digests.sha256(expectedCanonical.toByteArray(StandardCharsets.US_ASCII)),
            inventory.fingerprint,
        )
        assertEquals(inventory.fingerprint, inventory.authority.fingerprint)
        assertEquals(R2PackageMetadataPolicy.POLICY_REVISION, inventory.authority.policyRevision)
        assertEquals(R2PackageMetadataPolicy.fingerprint, inventory.authority.policyFingerprint)
        assertEquals(files.map { file -> file.path }.toSet(), inventory.filePaths)
        assertTrue(R2PackageMetadataPolicy.isExplicitlyAllowed("package.json", inventory))
        assertTrue(
            R2PackageMetadataPolicy.isExplicitlyAllowed(
                "packages/@scope/alpha/sub/package.json",
                inventory,
            ),
        )
        assertFalse(
            R2PackageMetadataPolicy.isExplicitlyAllowed(
                "packages/@scope/alpha/other/package.json",
                inventory,
            ),
        )
        assertEquals("/package.json", R2PackageMetadataPolicy.toVfsPath("package.json"))

        roots.clear()
        files.clear()
        assertEquals(listOf("packages/@scope/alpha", "packages/beta"), inventory.dependencyRoots)
        assertEquals(4, inventory.files.size)
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (inventory.files as MutableList<R2PackageMetadataFileDescriptor>).clear()
        }
    }

    @Test
    fun rootsAreLowercaseCanonicalSortedUniqueAndBounded() {
        listOf(
            listOf("packages/Beta"),
            listOf("packages/-beta"),
            listOf("packages/@Scope/beta"),
            listOf("packages/@scope/-beta"),
            listOf("packages/beta/sub"),
            listOf("packages/beta", "packages/alpha"),
            listOf("packages/beta", "packages/beta"),
        ).forEach { roots ->
            assertPolicyFailure {
                R2PackageMetadataInventory.create(
                    dependencyRoots = roots,
                    files = listOf(descriptor("package.json", 2)),
                )
            }
        }

        val exactRoots = (0 until R2PackageMetadataPolicy.MAXIMUM_DEPENDENCY_ROOTS)
            .map { index -> "packages/pkg${index.toString().padStart(3, '0')}" }
        val exactFiles = buildList {
            add(descriptor("package.json", 2))
            exactRoots.forEach { root -> add(descriptor("$root/package.json", 2)) }
        }.sortedBy(R2PackageMetadataFileDescriptor::path)
        assertEquals(
            R2PackageMetadataPolicy.MAXIMUM_DEPENDENCY_ROOTS,
            R2PackageMetadataInventory.create(exactRoots, exactFiles).dependencyRoots.size,
        )

        val beyondRoots = exactRoots + "packages/zzzz"
        assertPolicyFailure(R2PackageMetadataPolicyCode.QUOTA_EXCEEDED) {
            R2PackageMetadataInventory.create(
                dependencyRoots = beyondRoots,
                files = listOf(descriptor("package.json", 2)),
            )
        }
    }

    @Test
    fun filesMustBeExactPackageJsonPathsInsideExplicitRoots() {
        val invalidPaths = listOf(
            "/package.json",
            "Package.json",
            "sources/package.json",
            "packages/pkg/Package.json",
            "packages/pkg/index.json",
            "packages/pkg/node_modules/child/package.json",
            "packages/pkg/Node_Modules/child/package.json",
            "packages/pkg/with space/package.json",
            "packages/pkg/query?/package.json",
            "packages/pkg/comma,/package.json",
            "packages/pkg/pipe|/package.json",
            "packages/pkg\\nested\\package.json",
            "packages/pkg/../package.json",
            "packages/pkg//package.json",
            "packages/pkg/main.cts",
            "packages/pkg/main.mts",
        )
        invalidPaths.forEach { path ->
            assertPolicyFailure(R2PackageMetadataPolicyCode.PATH_INVALID) {
                descriptor(path, 2)
            }
        }

        assertPolicyFailure(R2PackageMetadataPolicyCode.ROOT_NOT_ALLOWED) {
            R2PackageMetadataInventory.create(
                dependencyRoots = listOf("packages/allowed"),
                files = listOf(
                    descriptor("package.json", 2),
                    descriptor("packages/allowed/package.json", 2),
                    descriptor("packages/other/package.json", 2),
                ),
            )
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.INVENTORY_INVALID) {
            R2PackageMetadataInventory.create(
                dependencyRoots = listOf("packages/pkg"),
                files = listOf(descriptor("packages/pkg/package.json", 2)),
            )
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.INVENTORY_INVALID) {
            R2PackageMetadataInventory.create(
                dependencyRoots = listOf("packages/pkg"),
                files = listOf(descriptor("package.json", 2)),
            )
        }
    }

    @Test
    fun fileInventoryRejectsOrderingDuplicatesAndAsciiCaseFoldCollisions() {
        assertPolicyFailure(R2PackageMetadataPolicyCode.INVENTORY_INVALID) {
            R2PackageMetadataInventory.create(
                dependencyRoots = listOf("packages/pkg"),
                files = listOf(
                    descriptor("packages/pkg/package.json", 2),
                    descriptor("package.json", 2),
                ),
            )
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.INVENTORY_INVALID) {
            R2PackageMetadataInventory.create(
                dependencyRoots = emptyList(),
                files = listOf(
                    descriptor("package.json", 2),
                    descriptor("package.json", 2),
                ),
            )
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.PATH_INVALID) {
            R2PackageMetadataInventory.create(
                dependencyRoots = listOf("packages/pkg"),
                files = listOf(
                    descriptor("package.json", 2),
                    descriptor("packages/pkg/Nested/package.json", 2),
                    descriptor("packages/pkg/nested/package.json", 2),
                    descriptor("packages/pkg/package.json", 2),
                ).sortedBy(R2PackageMetadataFileDescriptor::path),
            )
        }
    }

    @Test
    fun fileAndAggregateBudgetsAreInclusive() {
        assertEquals(
            R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES,
            descriptor(
                "package.json",
                R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES,
            ).size,
        )
        assertPolicyFailure(R2PackageMetadataPolicyCode.QUOTA_EXCEEDED) {
            descriptor(
                "package.json",
                R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES + 1,
            )
        }

        val root = "packages/pkg"
        val exactFiles = buildList {
            add(descriptor("package.json", R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES))
            add(descriptor("$root/package.json", R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES))
            repeat(14) { index ->
                add(
                    descriptor(
                        "$root/n${index.toString().padStart(2, '0')}/package.json",
                        R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES,
                    ),
                )
            }
        }.sortedBy(R2PackageMetadataFileDescriptor::path)
        assertEquals(
            R2PackageMetadataPolicy.MAXIMUM_TOTAL_BYTES,
            R2PackageMetadataInventory.create(listOf(root), exactFiles).totalBytes,
        )
        assertPolicyFailure(R2PackageMetadataPolicyCode.QUOTA_EXCEEDED) {
            R2PackageMetadataInventory.create(
                listOf(root),
                (
                    exactFiles + descriptor("$root/zz/package.json", 1)
                    ).sortedBy(R2PackageMetadataFileDescriptor::path),
            )
        }

        val maximumFiles = buildList {
            add(descriptor("package.json", 0))
            add(descriptor("$root/package.json", 0))
            repeat(R2PackageMetadataPolicy.MAXIMUM_FILES - 2) { index ->
                add(descriptor("$root/f${index.toString().padStart(3, '0')}/package.json", 0))
            }
        }.sortedBy(R2PackageMetadataFileDescriptor::path)
        assertEquals(
            R2PackageMetadataPolicy.MAXIMUM_FILES,
            R2PackageMetadataInventory.create(listOf(root), maximumFiles).files.size,
        )
        assertPolicyFailure(R2PackageMetadataPolicyCode.QUOTA_EXCEEDED) {
            R2PackageMetadataInventory.create(
                listOf(root),
                (
                    maximumFiles + descriptor("$root/zzzz/package.json", 0)
                    ).sortedBy(R2PackageMetadataFileDescriptor::path),
            )
        }
    }

    @Test
    fun packageJsonParserIsStrictBoundedAndRejectsOnlyExactTopLevelScripts() {
        listOf(
            "{}",
            "{\"name\":\"pkg\",\"type\":\"module\"}",
            "{\"Scripts\":{\"install\":\"inert\"}}",
            "{\"metadata\":{\"scripts\":{\"install\":\"inert\"}}}",
            "{\"exports\":{\".\":{\"import\":\"./index.js\",\"default\":\"./index.cjs\"}}}",
            "{\"\$schema\":\"https://invalid.example/package.schema.json\"}",
            "{\"value\":\"\ufeff\"}",
            "{\"value\":1e400}",
        ).forEach { json ->
            R2PackageMetadataPolicy.validatePackageJson(json.toByteArray(StandardCharsets.UTF_8))
        }

        listOf(
            "{\"scripts\":{}}",
            "{\"\\u0073cripts\":{}}",
        ).forEach { json ->
            assertPolicyFailure(R2PackageMetadataPolicyCode.LIFECYCLE_FORBIDDEN) {
                R2PackageMetadataPolicy.validatePackageJson(
                    json.toByteArray(StandardCharsets.UTF_8),
                )
            }
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.DUPLICATE_KEY) {
            R2PackageMetadataPolicy.validatePackageJson(
                "{\"name\":\"one\",\"name\":\"two\"}".toByteArray(),
            )
        }
        listOf(
            "[]",
            "{\"name\":\"pkg\",}",
            "{/*comment*/\"name\":\"pkg\"}",
            "{} trailing",
            "\ufeff{}",
        ).forEach { json ->
            assertPolicyFailure(R2PackageMetadataPolicyCode.JSON_INVALID) {
                R2PackageMetadataPolicy.validatePackageJson(json.toByteArray(StandardCharsets.UTF_8))
            }
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.ENCODING_INVALID) {
            R2PackageMetadataPolicy.validatePackageJson(byteArrayOf(0xc3.toByte(), 0x28))
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.ENCODING_INVALID) {
            R2PackageMetadataPolicy.validatePackageJson(
                "{\"value\":\"\\uD800\"}".toByteArray(StandardCharsets.UTF_8),
            )
        }
        var tooDeep = "0"
        repeat(R2PackageMetadataPolicy.MAXIMUM_JSON_DEPTH) {
            tooDeep = "{\"value\":$tooDeep}"
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.COMPLEXITY_EXCEEDED) {
            R2PackageMetadataPolicy.validatePackageJson(tooDeep.toByteArray())
        }
        val exactNodeLimit = buildString {
            append("{\"values\":[")
            repeat(R2PackageMetadataPolicy.MAXIMUM_JSON_NODES - 1) { index ->
                if (index > 0) append(',')
                append('0')
            }
            append("]}")
        }
        R2PackageMetadataPolicy.validatePackageJson(exactNodeLimit.toByteArray())
        val tooManyNodes = exactNodeLimit.replace("]}", ",0]}")
        assertPolicyFailure(R2PackageMetadataPolicyCode.COMPLEXITY_EXCEEDED) {
            R2PackageMetadataPolicy.validatePackageJson(tooManyNodes.toByteArray())
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.QUOTA_EXCEEDED) {
            R2PackageMetadataPolicy.validatePackageJson(
                ByteArray(R2PackageMetadataPolicy.MAXIMUM_FILE_BYTES.toInt() + 1),
            )
        }
    }

    @Test
    fun authorityRejectsPolicyDriftButBindsTheExactInventoryFingerprint() {
        val inventory = R2PackageMetadataInventory.create(
            emptyList(),
            listOf(descriptor("package.json", 2)),
        )
        assertEquals(inventory.fingerprint, inventory.authority.fingerprint)
        assertPolicyFailure(R2PackageMetadataPolicyCode.INVENTORY_INVALID) {
            R2PackageMetadataAuthority(
                fingerprint = inventory.fingerprint,
                policyRevision = R2PackageMetadataPolicy.POLICY_REVISION + 1,
            )
        }
        assertPolicyFailure(R2PackageMetadataPolicyCode.INVENTORY_INVALID) {
            R2PackageMetadataAuthority(
                fingerprint = inventory.fingerprint,
                policyFingerprint = "0".repeat(64),
            )
        }
    }

    private fun descriptor(
        path: String,
        size: Long,
        sha256: String = EMPTY_SHA256,
    ) = R2PackageMetadataFileDescriptor(path, size, sha256)

    private fun assertPolicyFailure(
        expected: R2PackageMetadataPolicyCode? = null,
        block: () -> Unit,
    ): R2PackageMetadataPolicyException {
        try {
            block()
        } catch (error: R2PackageMetadataPolicyException) {
            if (expected != null) assertEquals(expected, error.policyCode)
            return error
        } catch (error: Throwable) {
            throw AssertionError(
                "Expected R2PackageMetadataPolicyException, got ${error::class.java.name}.",
                error,
            )
        }
        fail("Expected R2PackageMetadataPolicyException.")
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
        val EMPTY_PACKAGE_JSON = "{}".toByteArray(StandardCharsets.UTF_8)
        const val EMPTY_SHA256 =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "schemaRevision",
            "policyRevision",
            "fingerprint",
            "projectPackagePath",
            "dependencyRootPrefix",
            "inventoryCanonicalDomain",
            "safeRelativePathGrammar",
            "budgets",
            "forbiddenTopLevelKeys",
            "jsonMode",
            "resolverMode",
            "policyCodes",
            "emptyDependencyRootsExample",
        )
        val EXACT_BUDGET_KEYS = setOf(
            "maximumDependencyRoots",
            "maximumFiles",
            "maximumFileBytes",
            "maximumTotalBytes",
            "maximumJsonDepth",
            "maximumJsonNodes",
        )
    }
}
