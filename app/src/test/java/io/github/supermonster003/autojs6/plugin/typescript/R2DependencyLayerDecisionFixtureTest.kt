package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DependencyLayerDecisionFixtureTest {

    @Test
    fun fixtureFreezesTheIndependentNamespaceAndPolicyIdentity() {
        val fixture = fixture()
        assertEquals(ROOT_KEYS, fixture.keys.toList())
        assertEquals("autojs6.typescript.t2-1.dependency-layer", fixture.string("schema"))
        assertEquals(R2DependencyLayerPolicy.SCHEMA_REVISION, fixture.int("schemaRevision"))
        assertEquals(R2DependencyLayerPolicy.POLICY_REVISION, fixture.int("policyRevision"))
        assertEquals(R2DependencyLayerPolicy.fingerprint, fixture.string("policyFingerprint"))
        assertEquals(EXPECTED_POLICY_FINGERPRINT, fixture.string("policyFingerprint"))
        assertEquals(
            "full-compilation-relevant-pack-with-exclusions-and-independent-immutable-spool",
            fixture.string("decision"),
        )

        val namespaces = fixture.objectValue("namespaces")
        assertEquals(NAMESPACE_KEYS, namespaces.keys.toList())
        assertEquals("dependencyLayer", namespaces.string("manifestKey"))
        assertEquals(
            R2DependencyLayerPolicy.DEPENDENCY_ARCHIVE_PREFIX,
            namespaces.string("dependencyArchivePrefix"),
        )
        assertEquals(
            R2DependencyLayerPolicy.LOCKFILE_ARCHIVE_PREFIX,
            namespaces.string("lockfileArchivePrefix"),
        )
        assertEquals("/node_modules/", namespaces.string("futureVfsPrefix"))
        assertEquals(R2DependencyLayerPolicy.READ_MODE, namespaces.string("readMode"))
        assertFalse(namespaces.boolean("callerInventoryIncluded"))
        assertFalse(namespaces.boolean("ordinaryCallerQuotaCharged"))
        assertFalse(namespaces.boolean("workerFramedBeforeT2_2"))
    }

    @Test
    fun fixtureLimitsMatchThePublicAndTransportContracts() {
        val limits = fixture().objectValue("limits")
        assertEquals(LIMIT_KEYS, limits.keys.toList())
        assertEquals(TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_FILES, limits.int("maximumFiles"))
        assertEquals(
            TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_SINGLE_FILE_BYTES,
            limits.long("maximumSingleFileBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_BYTES,
            limits.long("maximumTotalBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_LOCKFILE_BYTES,
            limits.long("maximumLockfileBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_PATH_BYTES,
            limits.long("maximumPathBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_INPUT_ARCHIVE_WITH_DEPENDENCIES_FILES,
            limits.int("maximumCombinedArchiveFiles"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_INPUT_ARCHIVE_WITH_DEPENDENCIES_BYTES,
            limits.long("maximumCombinedArchiveContainerBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_INPUT_ARCHIVE_WITH_DEPENDENCIES_EXPANDED_BYTES,
            limits.long("maximumCombinedArchiveExpandedBytes"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES +
                TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_FILES + 1,
            limits.int("maximumCombinedArchiveFiles"),
        )
        assertEquals(
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES +
                TypeScriptR2Contract.MAX_DEPENDENCY_LAYER_BYTES +
                TypeScriptCompilerContract.MAX_MANIFEST_BYTES,
            limits.long("maximumCombinedArchiveExpandedBytes"),
        )
    }

    @Test
    fun fixtureBindsLockAndExactInventoryIntoTheCacheIdentity() {
        val identity = fixture().objectValue("identity")
        assertEquals(IDENTITY_KEYS, identity.keys.toList())
        assertEquals(
            listOf(
                R2DependencyLayerPolicy.IDENTITY_PACKAGE_LOCK,
                R2DependencyLayerPolicy.IDENTITY_PNPM_LOCK,
                R2DependencyLayerPolicy.IDENTITY_CONTENT,
            ),
            identity.stringList("priority"),
        )
        assertEquals(
            R2DependencyLayerPolicy.lockfileArchivePath(
                R2DependencyLayerPolicy.IDENTITY_PACKAGE_LOCK,
            ),
            identity.string("packageLockPath"),
        )
        assertEquals(
            R2DependencyLayerPolicy.lockfileArchivePath(
                R2DependencyLayerPolicy.IDENTITY_PNPM_LOCK,
            ),
            identity.string("pnpmLockPath"),
        )
        assertEquals("exact-inventory-fingerprint", identity.string("contentFallback"))
        assertTrue(identity.boolean("inventoryAlwaysBound"))
        assertEquals(CACHE_KEY_FIELDS, identity.stringList("cacheKeyIncludes"))
    }

    @Test
    fun fixtureFreezesFilteringAndThreePackageBinderAcceptance() {
        val filtering = fixture().objectValue("filtering")
        assertEquals(FILTERING_KEYS, filtering.keys.toList())
        assertEquals(ALLOWED_SUFFIXES, filtering.stringList("allowedSuffixes"))
        assertEquals(EXCLUDED_SUFFIXES, filtering.stringList("excludedSuffixes"))
        assertEquals(EXCLUDED_DIRECTORIES, filtering.stringList("excludedDirectories"))
        assertTrue(filtering.boolean("nestedNodeModulesAllowed"))
        assertFalse(filtering.boolean("symbolicLinkTraversalAllowed"))
        assertTrue(filtering.boolean("utf8Required"))
        assertEquals(
            "non-install-accepted-as-inert-install-lifecycle-rejected",
            filtering.string("packageScripts"),
        )

        val acceptance = fixture().objectValue("acceptance")
        assertEquals(ACCEPTANCE_KEYS, acceptance.keys.toList())
        assertEquals(listOf("dayjs", "lodash", "zod"), acceptance.stringList("directDependencies"))
        assertEquals(
            "host-archive-binder-plugin-validation-private-spool-compiler",
            acceptance.string("pipeline"),
        )
        assertFalse(acceptance.boolean("bareSpecifierImportsRequired"))
        assertEquals("T2-2", acceptance.string("bareSpecifierPhase"))
        assertEquals(
            "dependencyLayerWithThreeDirectPackagesCompilesThroughRealPluginBinder",
            acceptance.string("deviceTest"),
        )
    }

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/t2-dependency-layer/t2-1-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun Map<String, Any?>.string(key: String): String = getValue(key) as String
    private fun Map<String, Any?>.int(key: String): Int =
        (getValue(key) as R2StrictJson.NumberToken).source.toInt()
    private fun Map<String, Any?>.long(key: String): Long =
        (getValue(key) as R2StrictJson.NumberToken).source.toLong()
    private fun Map<String, Any?>.boolean(key: String): Boolean = getValue(key) as Boolean

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    private companion object {
        const val EXPECTED_POLICY_FINGERPRINT =
            "c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a"
        val ROOT_KEYS = listOf(
            "schema",
            "schemaRevision",
            "policyRevision",
            "policyFingerprint",
            "decision",
            "namespaces",
            "limits",
            "identity",
            "filtering",
            "acceptance",
        )
        val NAMESPACE_KEYS = listOf(
            "manifestKey",
            "dependencyArchivePrefix",
            "lockfileArchivePrefix",
            "futureVfsPrefix",
            "readMode",
            "callerInventoryIncluded",
            "ordinaryCallerQuotaCharged",
            "workerFramedBeforeT2_2",
        )
        val LIMIT_KEYS = listOf(
            "maximumFiles",
            "maximumSingleFileBytes",
            "maximumTotalBytes",
            "maximumLockfileBytes",
            "maximumPathBytes",
            "maximumCombinedArchiveFiles",
            "maximumCombinedArchiveContainerBytes",
            "maximumCombinedArchiveExpandedBytes",
        )
        val IDENTITY_KEYS = listOf(
            "priority",
            "packageLockPath",
            "pnpmLockPath",
            "contentFallback",
            "inventoryAlwaysBound",
            "cacheKeyIncludes",
        )
        val CACHE_KEY_FIELDS = listOf(
            "schema-revision",
            "policy-revision",
            "policy-fingerprint",
            "identity-kind",
            "identity-path",
            "identity-fingerprint",
            "inventory-fingerprint",
            "exact-file-inventory",
        )
        val FILTERING_KEYS = listOf(
            "allowedSuffixes",
            "excludedSuffixes",
            "excludedDirectories",
            "nestedNodeModulesAllowed",
            "symbolicLinkTraversalAllowed",
            "utf8Required",
            "packageScripts",
        )
        val ALLOWED_SUFFIXES =
            listOf(".cjs", ".cts", ".js", ".json", ".jsx", ".mjs", ".mts", ".ts", ".tsx")
        val EXCLUDED_SUFFIXES = listOf(".map", ".markdown", ".md")
        val EXCLUDED_DIRECTORIES = listOf(
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
        val ACCEPTANCE_KEYS = listOf(
            "directDependencies",
            "pipeline",
            "bareSpecifierImportsRequired",
            "bareSpecifierPhase",
            "deviceTest",
        )
    }
}
