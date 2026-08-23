package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class R2ControlledTsConfigPolicyTest {

    @Test
    fun controlledScopeDefaultsAndStructuralFencesAreExact() {
        assertEquals(
            setOf("\$schema", "compilerOptions", "exclude", "files", "include"),
            R2ControlledTsConfigPolicy.allowedTopLevelKeys,
        )
        assertEquals(
            setOf("compileOnSave", "extends", "references", "typeAcquisition", "watchOptions"),
            R2ControlledTsConfigPolicy.unsupportedTopLevelKeys,
        )
        assertEquals(18, R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.size)
        assertEquals(
            R2ControlledTsConfigDefault.TRUE,
            R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.getValue("strict"),
        )
        STRICT_DEPENDENT_OPTIONS.forEach { name ->
            assertEquals(
                R2ControlledTsConfigDefault.INHERIT_STRICT,
                R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.getValue(name),
            )
        }
        FALSE_DEFAULT_OPTIONS.forEach { name ->
            assertEquals(
                R2ControlledTsConfigDefault.FALSE,
                R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.getValue(name),
            )
        }
        assertEquals("**/*", R2ControlledTsConfigPolicy.DEFAULT_INCLUDE_GLOB)
        assertEquals(emptyList<String>(), R2ControlledTsConfigPolicy.defaultExclude)
        assertEquals("union", R2ControlledTsConfigPolicy.FILES_AND_INCLUDE_SEMANTICS)
        assertEquals("include-only", R2ControlledTsConfigPolicy.EXCLUDE_AFFECTS)
        assertEquals("nfc-logical-project-path", R2ControlledTsConfigPolicy.LOGICAL_PATH_DOMAIN)
        assertEquals(
            "root-value-one-each-child-value-plus-one-inclusive",
            R2ControlledTsConfigPolicy.JSON_DEPTH_COUNTING,
        )
        assertEquals(
            "every-json-value-root-included-property-names-excluded-inclusive",
            R2ControlledTsConfigPolicy.JSON_NODE_COUNTING,
        )
        assertEquals(
            "all-array-elements-before-validation-normalization-dedup-inclusive",
            R2ControlledTsConfigPolicy.SELECTOR_COUNTING,
        )
        assertEquals(
            "max-of-decoded-json-string-and-nfc-normalized-string",
            R2ControlledTsConfigPolicy.SELECTOR_UTF8_MEASUREMENT,
        )
        assertEquals(
            setOf("customTransformer", "customTransformers", "transformers"),
            R2ControlledTsConfigPolicy.customTransformerCompilerOptions,
        )
        assertEquals("warning-ignore", R2ControlledTsConfigPolicy.UNKNOWN_TOP_LEVEL_FALLBACK)
        assertEquals("warning-ignore", R2ControlledTsConfigPolicy.KNOWN_OPTION_FALLBACK)
    }

    @Test
    fun structuralOptionsRejectWhileUnclassifiedPropertiesWarnAndIgnore() {
        R2ControlledTsConfigPolicy.allowedTopLevelKeys.forEach { name ->
            assertNull(R2ControlledTsConfigPolicy.topLevelRejectionCode(name))
        }
        R2ControlledTsConfigPolicy.unsupportedTopLevelKeys.forEach { name ->
            assertEquals(
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE,
                R2ControlledTsConfigPolicy.topLevelRejectionCode(name),
            )
        }
        assertNull(R2ControlledTsConfigPolicy.topLevelRejectionCode("futureUnknownTopLevel"))
        assertEquals(
            TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_TOP_LEVEL_PROPERTY,
            R2ControlledTsConfigPolicy.topLevelWarningDiagnosticCode("futureUnknownTopLevel"),
        )

        R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.keys.forEach { name ->
            assertNull(
                R2ControlledTsConfigPolicy.compilerOptionRejectionCode(
                    name,
                    isKnownTypeScriptOption = true,
                ),
            )
        }
        R2ControlledTsConfigPolicy.profileManagedCompilerOptions.forEach { name ->
            assertEquals(
                TypeScriptR2Contract.CONFIG_POLICY_CODE_PROFILE_CONFLICT,
                R2ControlledTsConfigPolicy.compilerOptionRejectionCode(
                    name,
                    isKnownTypeScriptOption = true,
                ),
            )
        }
        (
            R2ControlledTsConfigPolicy.escapeForbiddenCompilerOptions +
                R2ControlledTsConfigPolicy.outputForbiddenCompilerOptions
            ).forEach { name ->
            assertEquals(
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE,
                R2ControlledTsConfigPolicy.compilerOptionRejectionCode(
                    name,
                    isKnownTypeScriptOption = true,
                ),
            )
        }
        R2ControlledTsConfigPolicy.customTransformerCompilerOptions.forEach { name ->
            assertEquals(
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNKNOWN_PROPERTY,
                R2ControlledTsConfigPolicy.compilerOptionRejectionCode(
                    name,
                    isKnownTypeScriptOption = false,
                ),
            )
        }
        assertNull(
            R2ControlledTsConfigPolicy.compilerOptionRejectionCode(
                "experimentalDecorators",
                isKnownTypeScriptOption = true,
            ),
        )
        assertEquals(
            TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION,
            R2ControlledTsConfigPolicy.compilerOptionWarningDiagnosticCode(
                "experimentalDecorators",
                isKnownTypeScriptOption = true,
            ),
        )
        assertNull(
            R2ControlledTsConfigPolicy.compilerOptionRejectionCode(
                "definitelyNotATypeScriptOption",
                isKnownTypeScriptOption = false,
            ),
        )
        assertEquals(
            TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION,
            R2ControlledTsConfigPolicy.compilerOptionWarningDiagnosticCode(
                "definitelyNotATypeScriptOption",
                isKnownTypeScriptOption = false,
            ),
        )
    }

    @Test
    fun checkedInGoldenExactlyMirrorsThePolicyAuthority() {
        val fixtureBytes = requireNotNull(
            javaClass.getResourceAsStream("/r2-controlled-tsconfig/policy-v2.json"),
        ).use { input -> input.readBytes() }
        val root = R2StrictJson.parseObject(fixtureBytes)

        assertEquals(EXACT_ROOT_KEYS, root.keys)
        assertEquals(
            "autojs6.typescript.controlled-tsconfig-policy",
            root.getValue("schema"),
        )
        assertNumber(2, root.getValue("revision"))
        assertEquals(
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
            root.getValue("configKind"),
        )
        assertEquals(R2ControlledTsConfigPolicy.CONFIG_PATH, root.getValue("configPath"))
        assertEquals(R2ControlledTsConfigPolicy.fingerprint, root.getValue("fingerprint"))
        val grammar = root.requireObject("grammar")
        assertEquals(EXACT_GRAMMAR_KEYS, grammar.keys)
        assertEquals(R2ControlledTsConfigPolicy.RAW_GRAMMAR, grammar.getValue("name"))
        assertEquals(
            R2ControlledTsConfigPolicy.ALLOW_UTF8_BOM,
            grammar.getValue("allowUtf8Bom"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.ALLOW_COMMENTS,
            grammar.getValue("allowComments"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.ALLOW_TRAILING_COMMA,
            grammar.getValue("allowTrailingComma"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.REJECT_DUPLICATE_KEYS,
            grammar.getValue("rejectDuplicateKeys"),
        )
        val budgets = root.requireObject("budgets")
        assertEquals(EXACT_BUDGET_KEYS, budgets.keys)
        assertNumber(
            TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES,
            budgets.getValue("maximumBytes"),
        )
        assertNumber(
            TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_SELECTORS,
            budgets.getValue("maximumSelectors"),
        )
        assertNumber(
            TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_SELECTOR_UTF8_BYTES,
            budgets.getValue("maximumSelectorUtf8Bytes"),
        )
        assertNumber(
            TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_JSON_DEPTH,
            budgets.getValue("maximumJsonDepth"),
        )
        assertNumber(
            TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_JSON_NODES,
            budgets.getValue("maximumJsonNodes"),
        )
        val budgetSemantics = root.requireObject("budgetSemantics")
        assertEquals(EXACT_BUDGET_SEMANTIC_KEYS, budgetSemantics.keys)
        assertEquals(
            R2ControlledTsConfigPolicy.JSON_DEPTH_COUNTING,
            budgetSemantics.getValue("jsonDepthCounting"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.JSON_NODE_COUNTING,
            budgetSemantics.getValue("jsonNodeCounting"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.SELECTOR_COUNTING,
            budgetSemantics.getValue("selectorCounting"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.SELECTOR_UTF8_MEASUREMENT,
            budgetSemantics.getValue("selectorUtf8Measurement"),
        )
        val rootSelection = root.requireObject("rootSelection")
        assertEquals(EXACT_ROOT_SELECTION_KEYS, rootSelection.keys)
        assertEquals(
            R2ControlledTsConfigPolicy.LOGICAL_PATH_DOMAIN,
            rootSelection.getValue("logicalPathDomain"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.DEFAULT_INCLUDE_GLOB,
            rootSelection.getValue("defaultInclude"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.defaultExclude,
            rootSelection.requireStringList("defaultExclude"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.FILES_AND_INCLUDE_SEMANTICS,
            rootSelection.getValue("filesAndInclude"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.EXCLUDE_AFFECTS,
            rootSelection.getValue("excludeAffects"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.allowedTopLevelKeys.sorted(),
            root.requireStringList("allowedTopLevel"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.unsupportedTopLevelKeys.sorted(),
            root.requireStringList("unsupportedTopLevel"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions
                .mapValues { (_, default) -> default.contractValue },
            root.requireStringMap("allowedBooleanCompilerOptions"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.profileManagedCompilerOptions.sorted(),
            root.requireStringList("profileManagedCompilerOptions"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.escapeForbiddenCompilerOptions.sorted(),
            root.requireStringList("escapeForbiddenCompilerOptions"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.outputForbiddenCompilerOptions.sorted(),
            root.requireStringList("outputForbiddenCompilerOptions"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.customTransformerCompilerOptions.sorted(),
            root.requireStringList("customTransformerCompilerOptions"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.UNKNOWN_TOP_LEVEL_FALLBACK,
            root.getValue("unknownTopLevelFallback"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.policyCodes.toList(),
            root.requireStringList("policyCodes"),
        )
        assertEquals(
            R2ControlledTsConfigPolicy.KNOWN_OPTION_FALLBACK,
            root.getValue("knownOptionFallback"),
        )
        val warningCodes = root.requireObject("warningDiagnosticCodes")
        assertEquals(EXACT_WARNING_CODE_KEYS, warningCodes.keys)
        assertNumber(
            TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_TOP_LEVEL_PROPERTY,
            warningCodes.getValue("ignoredTopLevelProperty"),
        )
        assertNumber(
            TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION,
            warningCodes.getValue("ignoredCompilerOption"),
        )
    }

    @Test
    fun controlledConfigRemainsAnOverlayOnTheFrozenRhinoRuntimeProfile() {
        assertEquals(
            TypeScriptR2Contract.CONFIG_KIND_NONE,
            R2CompilationProfileRegistry.CONFIG_KIND_NONE,
        )
        assertEquals(
            TypeScriptR2Contract.CONFIG_KIND_NONE,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
        )
        assertEquals(1, R2CompilationProfileRegistry.CONFIG_SCHEMA_REVISION)
        assertNotEquals(
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
        )
    }

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    private fun Map<String, Any?>.requireStringMap(key: String): Map<String, String> =
        (getValue(key) as Map<*, *>).entries.associate { (name, value) ->
            name as String to value as String
        }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    private fun assertNumber(
        expected: Int,
        actual: Any?,
    ) {
        assertEquals(expected.toString(), (actual as R2StrictJson.NumberToken).source)
    }

    private companion object {
        val EXACT_ROOT_KEYS = setOf(
            "schema",
            "revision",
            "configKind",
            "configPath",
            "fingerprint",
            "grammar",
            "budgets",
            "budgetSemantics",
            "rootSelection",
            "allowedTopLevel",
            "unsupportedTopLevel",
            "allowedBooleanCompilerOptions",
            "profileManagedCompilerOptions",
            "escapeForbiddenCompilerOptions",
            "outputForbiddenCompilerOptions",
            "customTransformerCompilerOptions",
            "unknownTopLevelFallback",
            "knownOptionFallback",
            "warningDiagnosticCodes",
            "policyCodes",
        )
        val EXACT_GRAMMAR_KEYS = setOf(
            "name",
            "allowUtf8Bom",
            "allowComments",
            "allowTrailingComma",
            "rejectDuplicateKeys",
        )
        val EXACT_BUDGET_KEYS = setOf(
            "maximumBytes",
            "maximumSelectors",
            "maximumSelectorUtf8Bytes",
            "maximumJsonDepth",
            "maximumJsonNodes",
        )
        val EXACT_BUDGET_SEMANTIC_KEYS = setOf(
            "jsonDepthCounting",
            "jsonNodeCounting",
            "selectorCounting",
            "selectorUtf8Measurement",
        )
        val EXACT_ROOT_SELECTION_KEYS = setOf(
            "logicalPathDomain",
            "defaultInclude",
            "defaultExclude",
            "filesAndInclude",
            "excludeAffects",
        )
        val EXACT_WARNING_CODE_KEYS = setOf(
            "ignoredTopLevelProperty",
            "ignoredCompilerOption",
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
