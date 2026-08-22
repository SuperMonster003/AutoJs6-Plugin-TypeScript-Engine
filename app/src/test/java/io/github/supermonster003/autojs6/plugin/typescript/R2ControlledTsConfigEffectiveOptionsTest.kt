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
        assertNumber(1, root.getValue("profileRevision"))
        assertNumber(2, root.getValue("configSchemaRevision"))
        assertEquals(TypeScriptR2Contract.CONFIG_KIND_TSCONFIG, root.getValue("configKind"))
        assertEquals(
            R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys,
            root.requireStringList("canonicalOptionKeys"),
        )
        assertEquals(41, R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys.size)

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
            assertEquals(41, canonicalObject.size)
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
        assertEquals(992, defaults.canonicalJson.toByteArray(StandardCharsets.UTF_8).size)
        assertEquals(
            "2d8daf5f2f3516c9684f8e4659f51dfb9b037d21849bc2bbafca2fbaf034bd42",
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

        val legacy = R2CompilationProfileRegistry.PROVISIONAL_RHINO
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

    private fun fixture(): Map<String, Any?> {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream(
                "/r2-controlled-tsconfig/effective-options-v1.json",
            ),
        ).use { input -> input.readBytes() }
        return R2StrictJson.parseObject(bytes)
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        getValue(key) as String

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

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
