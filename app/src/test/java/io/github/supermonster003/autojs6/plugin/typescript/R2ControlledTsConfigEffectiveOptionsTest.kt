package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ControlledTsConfigEffectiveOptionsTest {

    @Test
    fun checkedInFixtureOwnsCanonicalJsonAndFingerprint() {
        val root = fixture()
        assertEquals(EXACT_FIXTURE_KEYS, root.keys)
        assertEquals(
            "autojs6.typescript.controlled-tsconfig-effective-options",
            root.getValue("schema"),
        )
        assertNumber(R2ControlledTsConfigEffectiveOptions.REVISION, root.getValue("revision"))
        assertNumber(1, root.getValue("profileSchemaRevision"))
        assertNumber(2, root.getValue("profileRevision"))
        assertNumber(2, root.getValue("configSchemaRevision"))
        assertEquals(TypeScriptR2Contract.CONFIG_KIND_TSCONFIG, root.getValue("configKind"))
        assertEquals(
            R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys,
            root.requireStringList("canonicalOptionKeys"),
        )
        assertEquals(44, R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys.size)

        root.requireObjectList("accepted").forEach { testCase ->
            val explicit = testCase.requireBooleanMap("explicitBooleanOptions")
            val resolved = R2ControlledTsConfigEffectiveOptions.resolve(explicit)
            val expectedCanonicalJson = testCase.requireString("canonicalJson")
            val expectedFingerprint = testCase.requireString("fingerprint")

            assertEquals(testCase.requireString("name"), expectedCanonicalJson, resolved.canonicalJson)
            assertEquals(testCase.requireString("name"), expectedFingerprint, resolved.fingerprint)
            assertEquals(
                expectedFingerprint,
                R2Digests.sha256(expectedCanonicalJson.toByteArray(StandardCharsets.US_ASCII)),
            )
            val canonicalObject = R2StrictJson.parseObject(
                expectedCanonicalJson.toByteArray(StandardCharsets.US_ASCII),
            )
            assertEquals(
                R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys,
                canonicalObject.keys.toList(),
            )
            assertEquals(44, canonicalObject.size)
            assertEquals(explicit.toSortedMap(), resolved.explicitBooleanOptions)
            assertEquals(18, resolved.resolvedBooleanOptions.size)
            assertEquals(
                R2ControlledTsConfigExplicitProfileDefaults.PROVISIONAL_RHINO,
                resolved.explicitProfileDefaults,
            )
        }
    }

    @Test
    fun strictChildrenAndFalseDefaultsUseFrozenPrecedence() {
        val defaults = R2ControlledTsConfigEffectiveOptions.resolve(emptyMap())
        assertEquals(1_072, defaults.canonicalJson.toByteArray(StandardCharsets.UTF_8).size)
        assertEquals(
            "5b529b507e94fafe30ccce6de4d69f89604bc0f1430b7b4ee8f12259fe03a807",
            defaults.fingerprint,
        )
        assertEquals(
            R2ControlledTsConfigExplicitProfileDefaults(
                allowSyntheticDefaultImports = true,
                alwaysStrict = true,
                esModuleInterop = true,
                forceConsistentCasingInFileNames = true,
                moduleDetection = 2,
                resolveJsonModule = false,
                useDefineForClassFields = false,
            ),
            defaults.explicitProfileDefaults,
        )
        assertTrue(defaults.resolvedBooleanOptions.getValue("strict"))
        STRICT_DEPENDENT_OPTIONS.forEach { name ->
            assertTrue("$name did not inherit strict=true", defaults.resolvedBooleanOptions.getValue(name))
        }
        FALSE_DEFAULT_OPTIONS.forEach { name ->
            assertFalse("$name did not retain its explicit false default", defaults.resolvedBooleanOptions.getValue(name))
        }

        val strictFalse = R2ControlledTsConfigEffectiveOptions.resolve(mapOf("strict" to false))
        STRICT_DEPENDENT_OPTIONS.forEach { name ->
            assertFalse("$name did not inherit strict=false", strictFalse.resolvedBooleanOptions.getValue(name))
        }
        val childOverride = R2ControlledTsConfigEffectiveOptions.resolve(
            mapOf("strict" to false, "noImplicitAny" to true),
        )
        assertTrue(childOverride.resolvedBooleanOptions.getValue("noImplicitAny"))
        assertFalse(childOverride.resolvedBooleanOptions.getValue("noImplicitThis"))
    }

    @Test
    fun TypeScriptInvalidStrictNullCombinationsAreTypedPolicyFailures() {
        fixture().requireObjectList("rejected").forEach { testCase ->
            val failure = assertThrows(
                R2ControlledTsConfigEffectiveOptionsException::class.java,
            ) {
                R2ControlledTsConfigEffectiveOptions.resolve(
                    testCase.requireBooleanMap("explicitBooleanOptions"),
                )
            }
            assertEquals(testCase.requireString("name"), testCase.requireString("policyCode"), failure.policyCode)
            assertEquals(TypeScriptR2Contract.CONFIG_POLICY_CODE_INVALID_VALUE, failure.policyCode)
        }
    }

    @Test
    fun authorityDefensivelyCopiesInputsAndKeepsConfigNoneShapeUntouched() {
        val explicit = linkedMapOf("strict" to false)
        val resolved = R2ControlledTsConfigEffectiveOptions.resolve(explicit)
        explicit["strict"] = true
        assertEquals(false, resolved.explicitBooleanOptions.getValue("strict"))

        @Suppress("UNCHECKED_CAST")
        assertThrows(UnsupportedOperationException::class.java) {
            (resolved.resolvedBooleanOptions as MutableMap<String, Boolean>)["strict"] = true
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2ControlledTsConfigEffectiveOptions.resolve(mapOf("futureOption" to true))
        }

        val legacy = R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1
        val legacyObject = R2StrictJson.parseObject(
            legacy.canonicalEffectiveOptionsJson.toByteArray(StandardCharsets.US_ASCII),
        )
        assertEquals(18, legacyObject.size)
        assertEquals(
            "76a173383972b1bd8d4ffa3fffe5d51f5adecf7b84e7b16f58554c14f3c08e0b",
            legacy.effectiveOptionsFingerprint,
        )
        assertEquals(1, legacy.configSchemaRevision)
        assertEquals(TypeScriptR2Contract.CONFIG_KIND_NONE, legacy.configKind)
    }

    @Test
    fun revision3ResolvesProfileBoundValueAndEmitOptions() {
        val rhino = R2ControlledTsConfigEffectiveOptions.resolveRevision3(
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            explicitBooleanOptions = mapOf(
                "noPropertyAccessFromIndexSignature" to true,
                "preserveConstEnums" to true,
                "removeComments" to true,
            ),
            target = R2ControlledTsConfigTarget.ES2018,
            libraries = listOf("lib.es2018.d.ts"),
        )
        val node = R2ControlledTsConfigEffectiveOptions.resolveRevision3(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            explicitBooleanOptions = mapOf(
                "exactOptionalPropertyTypes" to true,
                "preserveConstEnums" to true,
            ),
            target = R2ControlledTsConfigTarget.ES2022,
            libraries = listOf("lib.es2022.d.ts"),
        )

        assertEquals(3, rhino.revision)
        assertEquals(3, node.revision)
        assertEquals(47, rhino.canonicalOptionKeys.size)
        assertEquals(44, node.canonicalOptionKeys.size)
        assertEquals(5, rhino.compilerOptions.target)
        assertEquals(9, node.compilerOptions.target)
        assertEquals(1, rhino.compilerOptions.module)
        assertEquals(199, node.compilerOptions.module)
        assertEquals(listOf("lib.es2018.d.ts"), rhino.compilerOptions.lib)
        assertEquals(listOf("lib.es2022.d.ts"), node.compilerOptions.lib)
        assertTrue(rhino.compilerOptions.removeComments)
        assertTrue(rhino.compilerOptions.preserveConstEnums)
        assertTrue(node.compilerOptions.preserveConstEnums)
        assertFalse(node.compilerOptions.removeComments)
        assertFalse(rhino.explicitProfileDefaults.useDefineForClassFields)
        assertTrue(node.explicitProfileDefaults.useDefineForClassFields)
        assertEquals(
            listOf(
                "780e50f808f2eb399b794ed9a8f4e53c2aacfa01a75671e8fa05485f304eff85",
                "1a55e601b7c5b55764dc2219afc0320f99c9ada39b1d0dfa98d78687b338a52d",
            ),
            listOf(rhino.fingerprint, node.fingerprint),
        )
    }

    @Test
    fun checkedInRevision3FixtureBindsBothProfilesAndLegacyCompatibility() {
        val root = revision3Fixture()
        assertEquals(
            setOf(
                "schema",
                "revision",
                "configSchemaRevision",
                "configKind",
                "legacyCompatibility",
                "nodeProgramAuthority",
                "profiles",
                "rejected",
            ),
            root.keys,
        )
        assertNumber(R2ControlledTsConfigEffectiveOptions.REVISION_3, root.getValue("revision"))
        val legacy = root.requireObject("legacyCompatibility")
        assertNumber(
            R2ControlledTsConfigPolicy.LEGACY_POLICY_REVISION,
            legacy.getValue("policyRevision"),
        )
        assertEquals(R2ControlledTsConfigPolicy.fingerprint, legacy.getValue("policyFingerprint"))
        assertEquals(
            R2ControlledTsConfigEffectiveOptions.resolve(emptyMap()).fingerprint,
            legacy.getValue("defaultEffectiveOptionsFingerprint"),
        )

        val resolvedById = mapOf(
            "rhino-es2018" to R2ControlledTsConfigEffectiveOptions.resolveRevision3(
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                explicitBooleanOptions = mapOf(
                    "noPropertyAccessFromIndexSignature" to true,
                    "preserveConstEnums" to true,
                    "removeComments" to true,
                ),
                target = R2ControlledTsConfigTarget.ES2018,
                libraries = listOf("lib.es2018.d.ts"),
            ),
            "node-es2022" to R2ControlledTsConfigEffectiveOptions.resolveRevision3(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                explicitBooleanOptions = mapOf(
                    "exactOptionalPropertyTypes" to true,
                    "preserveConstEnums" to true,
                ),
                target = R2ControlledTsConfigTarget.ES2022,
                libraries = listOf("lib.es2022.d.ts"),
            ),
        )
        root.requireObjectList("profiles").forEach { profile ->
            val resolved = resolvedById.getValue(profile.requireString("id"))
            assertNumber(
                resolved.canonicalOptionKeys.size,
                profile.getValue("canonicalOptionCount"),
            )
            assertEquals(
                resolved.fingerprint,
                profile.getValue("effectiveOptionsFingerprint"),
            )
            assertEquals(
                resolved.compilerOptions.lib,
                profile.requireStringList("normalizedLibraries"),
            )
            assertEquals(
                R2ControlledTsConfigTarget.fromCompilerValue(resolved.compilerOptions.target)
                    .configValue,
                profile.getValue("normalizedTarget"),
            )
        }
        assertEquals(
            setOf(
                TypeScriptR2Contract.CONFIG_POLICY_CODE_INVALID_VALUE,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE,
            ),
            root.requireObjectList("rejected")
                .map { rejected -> rejected.requireString("policyCode") }
                .toSet(),
        )
        val nodeAuthority = root.requireObject("nodeProgramAuthority")
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
                .runtimeCapabilitiesFingerprint,
            nodeAuthority.getValue("runtimeCapabilitiesFingerprint"),
        )
    }

    @Test
    fun revision4BindsCanonicalProjectRelativeAliasesForBothProfiles() {
        val mutableTargets = mutableListOf("src/*")
        val mutablePaths = linkedMapOf(
            "@feature/*" to mutableListOf("src/features/*"),
            "@/*" to mutableTargets,
        )
        val rhino = R2ControlledTsConfigEffectiveOptions.resolveRevision4(
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            explicitBooleanOptions = emptyMap(),
            target = R2ControlledTsConfigTarget.ES2018,
            libraries = listOf("lib.es2018.d.ts"),
            baseUrl = ".",
            paths = mutablePaths,
        )
        val node = R2ControlledTsConfigEffectiveOptions.resolveRevision4(
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            explicitBooleanOptions = emptyMap(),
            target = R2ControlledTsConfigTarget.ES2022,
            libraries = listOf("lib.es2022.d.ts"),
            baseUrl = ".",
            paths = mutablePaths,
        )

        mutableTargets += "changed/*"
        mutablePaths["later/*"] = mutableListOf("later/*")
        assertEquals(R2ControlledTsConfigEffectiveOptions.REVISION_4, rhino.revision)
        assertEquals(R2ControlledTsConfigEffectiveOptions.REVISION_4, node.revision)
        assertEquals(49, rhino.canonicalOptionKeys.size)
        assertEquals(46, node.canonicalOptionKeys.size)
        assertEquals(listOf("@/*", "@feature/*"), rhino.compilerOptions.paths?.keys?.toList())
        assertEquals(listOf("src/*"), rhino.compilerOptions.paths?.get("@/*"))
        assertEquals(".", rhino.compilerOptions.baseUrl)
        assertEquals(".", node.compilerOptions.baseUrl)
        assertEquals(
            "b2da71259b531113171de449e3226a8b5d7870620063feb4ac81459dedc4146b",
            rhino.fingerprint,
        )
        assertEquals(
            "341d3f22c61fd7488bae6de0ed6003376e62cdfdf394ac153677d7dc3b67c546",
            node.fingerprint,
        )
        assertTrue(rhino.canonicalJson.contains("\"baseUrl\":\".\""))
        assertTrue(
            rhino.canonicalJson.contains(
                "\"paths\":{\"@/*\":[\"src/*\"]," +
                    "\"@feature/*\":[\"src/features/*\"]}",
            ),
        )
    }

    @Test
    fun revision4PathAliasesRejectEscapesAndDefaultBaseUrlToProjectRoot() {
        val defaulted = R2ControlledTsConfigEffectiveOptions.resolveRevision4(
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            explicitBooleanOptions = emptyMap(),
            paths = mapOf("@/*" to listOf("src/*")),
        )
        assertEquals(".", defaulted.compilerOptions.baseUrl)

        listOf(
            { R2ControlledPathAliasTable.normalize("../outside", mapOf("@/*" to listOf("src/*"))) },
            { R2ControlledPathAliasTable.normalize(null, mapOf("@/*" to listOf("../outside/*"))) },
            { R2ControlledPathAliasTable.normalize(null, mapOf("@/*" to listOf("src/exact"))) },
            { R2ControlledPathAliasTable.normalize(null, mapOf("./relative/*" to listOf("src/*"))) },
        ).forEach { operation ->
            assertThrows(IllegalArgumentException::class.java) { operation() }
        }
    }

    @Test
    fun checkedInRevision4FixtureBindsAliasEffectiveOptionsAndPreviousIdentity() {
        val root = revision4Fixture()
        assertEquals(
            setOf(
                "schema",
                "revision",
                "configSchemaRevision",
                "configKind",
                "previousCompatibility",
                "pathAliases",
                "profiles",
                "rejected",
            ),
            root.keys,
        )
        assertNumber(R2ControlledTsConfigEffectiveOptions.REVISION_4, root.getValue("revision"))
        val previous = root.requireObject("previousCompatibility")
        assertNumber(R2ControlledTsConfigPolicy.REVISION_3, previous.getValue("policyRevision"))
        assertEquals(
            R2ControlledTsConfigPolicy.revision3Fingerprint,
            previous.getValue("policyFingerprint"),
        )
        val aliases = root.requireObject("pathAliases")
        val paths = aliases.requireObject("paths")
            .mapValues { (_, value) -> (value as List<*>).map { target -> target as String } }
        val resolved = mapOf(
            "rhino" to R2ControlledTsConfigEffectiveOptions.resolveRevision4(
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                explicitBooleanOptions = emptyMap(),
                target = R2ControlledTsConfigTarget.ES2018,
                libraries = listOf("lib.es2018.d.ts"),
                baseUrl = aliases.requireString("baseUrl"),
                paths = paths,
            ),
            "node" to R2ControlledTsConfigEffectiveOptions.resolveRevision4(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                explicitBooleanOptions = emptyMap(),
                target = R2ControlledTsConfigTarget.ES2022,
                libraries = listOf("lib.es2022.d.ts"),
                baseUrl = aliases.requireString("baseUrl"),
                paths = paths,
            ),
        )
        root.requireObjectList("profiles").forEach { profile ->
            val effective = resolved.getValue(profile.requireString("targetProfile"))
            assertNumber(
                effective.canonicalOptionKeys.size,
                profile.getValue("canonicalOptionCount"),
            )
            assertEquals(
                effective.fingerprint,
                profile.getValue("effectiveOptionsFingerprint"),
            )
        }
        assertEquals(
            setOf(TypeScriptR2Contract.CONFIG_POLICY_CODE_PATH_INVALID),
            root.requireObjectList("rejected")
                .map { rejected -> rejected.requireString("policyCode") }
                .toSet(),
        )
    }

    private fun fixture(): Map<String, Any?> {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream(
                "/r2-controlled-tsconfig/effective-options-v1.json",
            ),
        ).use { input -> input.readBytes() }
        return R2StrictJson.parseObject(bytes)
    }

    private fun revision3Fixture(): Map<String, Any?> {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream(
                "/r2-controlled-tsconfig/effective-options-v3.json",
            ),
        ).use { input -> input.readBytes() }
        return R2StrictJson.parseObject(bytes)
    }

    private fun revision4Fixture(): Map<String, Any?> {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream(
                "/r2-controlled-tsconfig/effective-options-v4.json",
            ),
        ).use { input -> input.readBytes() }
        return R2StrictJson.parseObject(bytes)
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        getValue(key) as String

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObjectList(key: String): List<Map<String, Any?>> =
        (getValue(key) as List<*>).map { value -> value as Map<String, Any?> }

    private fun Map<String, Any?>.requireBooleanMap(key: String): Map<String, Boolean> =
        (getValue(key) as Map<*, *>).entries.associate { (name, value) ->
            name as String to (value as Boolean)
        }

    private fun assertNumber(
        expected: Int,
        actual: Any?,
    ) {
        assertEquals(expected.toString(), (actual as R2StrictJson.NumberToken).source)
    }

    private companion object {
        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "revision",
            "profileSchemaRevision",
            "profileRevision",
            "configSchemaRevision",
            "configKind",
            "canonicalOptionKeys",
            "accepted",
            "rejected",
        )
        val STRICT_DEPENDENT_OPTIONS = setOf(
            "noImplicitAny",
            "strictNullChecks",
            "strictFunctionTypes",
            "strictBindCallApply",
            "strictPropertyInitialization",
            "strictBuiltinIteratorReturn",
            "noImplicitThis",
            "useUnknownInCatchVariables",
        )
        val FALSE_DEFAULT_OPTIONS = setOf(
            "noUnusedLocals",
            "noUnusedParameters",
            "exactOptionalPropertyTypes",
            "noImplicitReturns",
            "noFallthroughCasesInSwitch",
            "noUncheckedIndexedAccess",
            "noImplicitOverride",
            "noPropertyAccessFromIndexSignature",
            "skipLibCheck",
        )
    }
}
