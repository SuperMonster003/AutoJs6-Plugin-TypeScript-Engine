package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2NodeModuleOutputPolicyTest {

    @Test
    fun checkedInFixtureExactlyMirrorsThePolicyAuthority() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/r2-node-module-output/policy-v1.json"),
        ).use { input -> input.readBytes() }
        val root = R2StrictJson.parseObject(bytes)

        assertEquals(EXACT_FIXTURE_KEYS, root.keys)
        assertEquals(R2NodeModuleOutputPolicy.SCHEMA, root.getValue("schema"))
        assertNumber(R2NodeModuleOutputPolicy.SCHEMA_REVISION, root.getValue("schemaRevision"))
        assertNumber(R2NodeModuleOutputPolicy.POLICY_REVISION, root.getValue("policyRevision"))
        assertEquals(R2NodeModuleOutputPolicy.fingerprint, root.getValue("fingerprint"))
        assertNumber(
            R2NodeModuleOutputPolicy.RHINO_PROFILE_REVISION,
            root.getValue("rhinoProfileRevision"),
        )
        assertNumber(
            R2NodeModuleOutputPolicy.NODE_LEGACY_PROFILE_REVISION,
            root.getValue("nodeLegacyProfileRevision"),
        )
        assertNumber(
            R2NodeModuleOutputPolicy.NODE_PROFILE_REVISION,
            root.getValue("nodeProfileRevision"),
        )
        assertEquals(R2NodeModuleOutputPolicy.SOURCE_ROOT, root.getValue("sourceRoot"))
        assertEquals(R2NodeModuleOutputPolicy.OUTPUT_ROOT, root.getValue("outputRoot"))
        assertEquals(
            R2NodeModuleOutputPolicy.SOURCE_URI_SCHEME,
            root.getValue("sourceUriScheme"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.SOURCE_URI_AUTHORITY,
            root.getValue("sourceUriAuthority"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.GENERATED_URI_AUTHORITY,
            root.getValue("generatedUriAuthority"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.PROJECT_NAMESPACE_PREFIX,
            root.getValue("projectNamespacePrefix"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.PROJECT_NAMESPACE_MODE,
            root.getValue("projectNamespaceMode"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.PATH_ENCODING_MODE,
            root.getValue("pathEncodingMode"),
        )
        assertNumber(
            R2NodeModuleOutputPolicy.MAXIMUM_RELATIVE_PATH_CHARS,
            root.getValue("maximumRelativePathChars"),
        )
        assertNumber(
            R2NodeModuleOutputPolicy.MAXIMUM_PATH_DEPTH,
            root.getValue("maximumPathDepth"),
        )
        assertNumber(
            R2NodeModuleOutputPolicy.MAXIMUM_URI_ASCII_BYTES,
            root.getValue("maximumUriAsciiBytes"),
        )
        assertEquals(R2NodeModuleOutputPolicy.COLLISION_MODE, root.getValue("collisionMode"))
        assertEquals(R2NodeModuleOutputPolicy.PACKAGE_SCOPE, root.getValue("packageScope"))
        assertEquals(
            R2NodeModuleOutputPolicy.PACKAGE_BOUNDARY_MODE,
            root.getValue("packageBoundaryMode"),
        )
        assertEquals(
            R2PackageMetadataPolicy.fingerprint,
            root.getValue("packageMetadataPolicyFingerprint"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.PACKAGE_TYPE_POLICY,
            root.getValue("packageTypePolicy"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.PACKAGE_TYPE_KEY,
            root.getValue("packageTypeKey"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.sourceExtensions,
            root.requireStringList("sourceExtensions"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.moduleFormats,
            root.requireStringList("moduleFormats"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.packageTypeStates,
            root.requireStringList("packageTypeStates"),
        )
        assertEquals(R2NodeModuleOutputPolicy.OUTPUT_MATRIX, root.getValue("outputMatrix"))
        assertNumber(
            R2NodeModuleOutputPolicy.RESOLVER_POLICY_REVISION,
            root.getValue("resolverPolicyRevision"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.resolverSubstitutions,
            root.requireStringList("resolverSubstitutions"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.RESOLVER_CONDITION_SOURCE,
            root.getValue("resolverConditionSource"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.RESOLVER_V1_COMPATIBILITY,
            root.getValue("resolverV1Compatibility"),
        )
        assertEquals(
            R2NodeModuleOutputPolicy.policyCodes,
            root.requireStringList("policyCodes"),
        )
    }

    @Test
    fun canonicalInputAndFingerprintFreezeTheFullP2ZeroAuthority() {
        val expected =
            "schema=autojs6.typescript.node-module-output-policy" +
                "|schemaRevision=1" +
                "|policyRevision=1" +
                "|rhinoProfileRevision=1" +
                "|nodeLegacyProfileRevision=1" +
                "|nodeProfileRevision=2" +
                "|sourceRoot=/sources" +
                "|outputRoot=/outputs" +
                "|sourceExtensions=.ts,.mts,.cts" +
                "|moduleFormats=commonjs,esm" +
                "|outputMatrix=rhino:.ts:commonjs:.js;node:.ts:package:.js;" +
                "node:.mts:esm:.mjs;node:.cts:commonjs:.cjs" +
                "|packageTypeKey=type" +
                "|packageTypeStates=no-boundary,missing,commonjs,module" +
                "|packageTypePolicy=no-boundary:commonjs;missing:commonjs;module:esm;" +
                "commonjs:commonjs;other:reject" +
                "|packageScope=project-root+explicit-c5-dependency-roots" +
                "|packageBoundaryMode=longest-explicit-c5-prefix" +
                "|packageMetadataPolicyFingerprint=" + R2PackageMetadataPolicy.fingerprint +
                "|sourceUriScheme=autojs" +
                "|sourceUriAuthority=source" +
                "|generatedUriAuthority=generated" +
                "|projectNamespacePrefix=p-" +
                "|projectNamespaceMode=p-lowercase-sha256-strict-utf8-project-id" +
                "|pathEncoding=rfc3986-segment-utf8-unreserved-uppercase-percent" +
                "|maximumRelativePathChars=1024" +
                "|maximumPathDepth=64" +
                "|maximumUriAsciiBytes=4096" +
                "|collisionMode=exact+ascii-case-fold" +
                "|resolverPolicyRevision=2" +
                "|resolverSubstitutions=.js->.ts,.mjs->.mts,.cjs->.cts," +
                ".d.ts->.d.ts,.d.mts->.d.mts,.d.cts->.d.cts" +
                "|resolverConditionSource=implied-module-format" +
                "|resolverV1Compatibility=unchanged-reject-new-kinds" +
                "|policyCodes=NODE_PACKAGE_TYPE_INVALID"

        assertEquals(expected, R2NodeModuleOutputPolicy.canonicalInput)
        assertTrue(expected.all { character -> character.code in 0x20..0x7e })
        assertEquals(POLICY_FINGERPRINT, R2NodeModuleOutputPolicy.fingerprint)
        assertEquals(
            R2Digests.sha256(expected.toByteArray(StandardCharsets.US_ASCII)),
            R2NodeModuleOutputPolicy.fingerprint,
        )
    }

    @Test
    fun exactPackageTypeValuesHaveStableModuleFormatStates() {
        assertEquals(
            R2PackageTypeState.NO_BOUNDARY,
            R2NodeModuleOutputPolicy.parsePackageType(null),
        )
        assertEquals(
            R2PackageTypeState.TYPE_ABSENT,
            R2NodeModuleOutputPolicy.parsePackageType(emptyMap()),
        )
        assertEquals(
            R2PackageTypeState.TYPE_ABSENT,
            R2NodeModuleOutputPolicy.parsePackageType(mapOf("name" to "pkg")),
        )
        assertEquals(
            R2PackageTypeState.COMMONJS,
            R2NodeModuleOutputPolicy.parsePackageType(mapOf("type" to "commonjs")),
        )
        assertEquals(
            R2PackageTypeState.MODULE,
            R2NodeModuleOutputPolicy.parsePackageType(mapOf("type" to "module")),
        )
        assertEquals("commonjs", R2ModuleFormat.COMMONJS.contractValue)
        assertEquals("esm", R2ModuleFormat.ESM.contractValue)
    }

    @Test
    fun invalidPackageTypeValuesFailClosedWithOneStableCode() {
        val invalidValues: List<Any?> = listOf(
            "",
            "Module",
            "invalid",
            7L,
            false,
            null,
            emptyList<Any?>(),
            emptyMap<String, Any?>(),
        )

        invalidValues.forEach { value ->
            val error = assertFails<R2NodeModuleOutputPolicyException> {
                R2NodeModuleOutputPolicy.parsePackageType(mapOf("type" to value))
            }
            assertEquals(
                R2NodeModuleOutputPolicyCode.PACKAGE_TYPE_INVALID,
                error.policyCode,
            )
            assertEquals("NODE_PACKAGE_TYPE_INVALID", error.policyCode.contractValue)
        }
    }

    @Test
    fun constantsFreezeRevisionUriResolverAndLimitBoundaries() {
        assertEquals(1, R2NodeModuleOutputPolicy.NODE_LEGACY_PROFILE_REVISION)
        assertEquals(2, R2NodeModuleOutputPolicy.NODE_PROFILE_REVISION)
        assertEquals(2, R2NodeModuleOutputPolicy.RESOLVER_POLICY_REVISION)
        assertEquals("p-", R2NodeModuleOutputPolicy.PROJECT_NAMESPACE_PREFIX)
        assertEquals(
            TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES,
            R2NodeModuleOutputPolicy.MAXIMUM_RELATIVE_PATH_CHARS,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH,
            R2NodeModuleOutputPolicy.MAXIMUM_PATH_DEPTH,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS,
            R2NodeModuleOutputPolicy.MAXIMUM_URI_ASCII_BYTES,
        )
        assertEquals(
            listOf(
                ".js->.ts",
                ".mjs->.mts",
                ".cjs->.cts",
                ".d.ts->.d.ts",
                ".d.mts->.d.mts",
                ".d.cts->.d.cts",
            ),
            R2NodeModuleOutputPolicy.resolverSubstitutions,
        )
        assertEquals(
            "unchanged-reject-new-kinds",
            R2NodeModuleOutputPolicy.RESOLVER_V1_COMPATIBILITY,
        )
    }

    private fun assertNumber(expected: Number, actual: Any?) {
        val token = actual as? R2StrictJson.NumberToken
            ?: throw AssertionError("Expected a JSON number token, got $actual")
        assertEquals(expected.toLong(), token.source.toLongOrNull())
    }

    private fun Map<String, Any?>.requireStringList(key: String): List<String> {
        val values = getValue(key) as? List<*>
            ?: throw AssertionError("Fixture field '$key' is not an array.")
        return values.map { value ->
            value as? String ?: throw AssertionError("'$key' contains a non-string.")
        }
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        fail("Expected ${T::class.java.simpleName}.")
        error("unreachable")
    }

    private companion object {
        const val POLICY_FINGERPRINT =
            "5c2294f0ee9d6d25bd2132b4ea24866d0a5f9caaa521a63bf81ffe1d897f6ce7"

        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "schemaRevision",
            "policyRevision",
            "fingerprint",
            "rhinoProfileRevision",
            "nodeLegacyProfileRevision",
            "nodeProfileRevision",
            "sourceRoot",
            "outputRoot",
            "sourceUriScheme",
            "sourceUriAuthority",
            "generatedUriAuthority",
            "projectNamespacePrefix",
            "projectNamespaceMode",
            "pathEncodingMode",
            "maximumRelativePathChars",
            "maximumPathDepth",
            "maximumUriAsciiBytes",
            "collisionMode",
            "packageScope",
            "packageBoundaryMode",
            "packageMetadataPolicyFingerprint",
            "packageTypeKey",
            "packageTypePolicy",
            "sourceExtensions",
            "moduleFormats",
            "packageTypeStates",
            "outputMatrix",
            "resolverPolicyRevision",
            "resolverSubstitutions",
            "resolverConditionSource",
            "resolverV1Compatibility",
            "policyCodes",
        )
    }
}
