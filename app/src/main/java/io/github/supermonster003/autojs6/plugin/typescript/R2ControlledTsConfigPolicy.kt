package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal enum class R2ControlledTsConfigDefault(
    val contractValue: String,
) {
    TRUE("true"),
    FALSE("false"),
    INHERIT_STRICT("inherit-strict"),
}

/**
 * Controlled tsconfig.json policy used by the public Rhino compilation path.
 *
 * Revision 2 preserves the C1 grammar, budgets, selector semantics, and 18 Boolean option subset,
 * but makes forward-compatible properties warning-only. Options that can change the frozen
 * profile, output topology, module resolution, host access, or transformer execution remain
 * explicit fail-closed branches.
 */
internal object R2ControlledTsConfigPolicy {

    const val CONFIG_PATH = "tsconfig.json"
    const val RAW_GRAMMAR = "typescript-jsonc-6.0.3"
    const val LOGICAL_PATH_DOMAIN = "nfc-logical-project-path"
    const val DEFAULT_INCLUDE_GLOB = "**/*"
    const val FILES_AND_INCLUDE_SEMANTICS = "union"
    const val EXCLUDE_AFFECTS = "include-only"
    const val JSON_DEPTH_COUNTING =
        "root-value-one-each-child-value-plus-one-inclusive"
    const val JSON_NODE_COUNTING =
        "every-json-value-root-included-property-names-excluded-inclusive"
    const val SELECTOR_COUNTING =
        "all-array-elements-before-validation-normalization-dedup-inclusive"
    const val SELECTOR_UTF8_MEASUREMENT =
        "max-of-decoded-json-string-and-nfc-normalized-string"
    const val UNKNOWN_TOP_LEVEL_FALLBACK = "warning-ignore"
    const val KNOWN_OPTION_FALLBACK = "warning-ignore"

    const val ALLOW_UTF8_BOM = true
    const val ALLOW_COMMENTS = true
    const val ALLOW_TRAILING_COMMA = true
    const val REJECT_DUPLICATE_KEYS = true

    val defaultExclude: List<String> = Collections.emptyList()

    val allowedTopLevelKeys: Set<String> = immutableSetOf(
        "\$schema",
        "compilerOptions",
        "exclude",
        "files",
        "include",
    )

    val unsupportedTopLevelKeys: Set<String> = immutableSetOf(
        "compileOnSave",
        "extends",
        "references",
        "typeAcquisition",
        "watchOptions",
    )

    val allowedBooleanCompilerOptions: Map<String, R2ControlledTsConfigDefault> =
        immutableMapOf(
            "strict" to R2ControlledTsConfigDefault.TRUE,
            "noImplicitAny" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "strictNullChecks" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "strictFunctionTypes" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "strictBindCallApply" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "strictPropertyInitialization" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "strictBuiltinIteratorReturn" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "noImplicitThis" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "useUnknownInCatchVariables" to R2ControlledTsConfigDefault.INHERIT_STRICT,
            "noUnusedLocals" to R2ControlledTsConfigDefault.FALSE,
            "noUnusedParameters" to R2ControlledTsConfigDefault.FALSE,
            "exactOptionalPropertyTypes" to R2ControlledTsConfigDefault.FALSE,
            "noImplicitReturns" to R2ControlledTsConfigDefault.FALSE,
            "noFallthroughCasesInSwitch" to R2ControlledTsConfigDefault.FALSE,
            "noUncheckedIndexedAccess" to R2ControlledTsConfigDefault.FALSE,
            "noImplicitOverride" to R2ControlledTsConfigDefault.FALSE,
            "noPropertyAccessFromIndexSignature" to R2ControlledTsConfigDefault.FALSE,
            "skipLibCheck" to R2ControlledTsConfigDefault.FALSE,
        )

    val profileManagedCompilerOptions: Set<String> = immutableSetOf(
        "allowJs",
        "allowSyntheticDefaultImports",
        "alwaysStrict",
        "checkJs",
        "esModuleInterop",
        "forceConsistentCasingInFileNames",
        "ignoreDeprecations",
        "module",
        "moduleDetection",
        "moduleResolution",
        "newLine",
        "noEmitOnError",
        "outDir",
        "resolveJsonModule",
        "rootDir",
        "sourceMap",
        "target",
        "useDefineForClassFields",
    )

    val escapeForbiddenCompilerOptions: Set<String> = immutableSetOf(
        "baseUrl",
        "customConditions",
        "lib",
        "moduleSuffixes",
        "paths",
        "plugins",
        "rootDirs",
        "traceResolution",
        "typeRoots",
        "types",
    )

    val outputForbiddenCompilerOptions: Set<String> = immutableSetOf(
        "declaration",
        "declarationDir",
        "declarationMap",
        "emitBOM",
        "emitDeclarationOnly",
        "importHelpers",
        "incremental",
        "inlineSourceMap",
        "inlineSources",
        "mapRoot",
        "noEmit",
        "noEmitHelpers",
        "outFile",
        "removeComments",
        "sourceRoot",
        "tsBuildInfoFile",
    )

    val customTransformerCompilerOptions: Set<String> = immutableSetOf(
        "customTransformer",
        "customTransformers",
        "transformers",
    )

    val policyCodes: Set<String> = immutableSetOf(
        TypeScriptR2Contract.CONFIG_POLICY_CODE_ENCODING_INVALID,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_SYNTAX_INVALID,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_DUPLICATE_KEY,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_UNKNOWN_PROPERTY,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_INVALID_VALUE,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_PROFILE_CONFLICT,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_PATH_INVALID,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_COMPLEXITY_EXCEEDED,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_NO_INPUTS,
        TypeScriptR2Contract.CONFIG_POLICY_CODE_ENTRY_NOT_SELECTED,
    )

    val canonicalInput: String = buildString {
        append("autojs6.typescript.controlled-tsconfig-policy.v2")
        append("|revision=")
        append(TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION)
        append("|configKind=")
        append(TypeScriptR2Contract.CONFIG_KIND_TSCONFIG)
        append("|configPath=")
        append(CONFIG_PATH)
        append("|grammar=")
        append(RAW_GRAMMAR)
        append("|bom=")
        append(ALLOW_UTF8_BOM)
        append("|comments=")
        append(ALLOW_COMMENTS)
        append("|trailingComma=")
        append(ALLOW_TRAILING_COMMA)
        append("|duplicateKeysRejected=")
        append(REJECT_DUPLICATE_KEYS)
        append("|logicalPathDomain=")
        append(LOGICAL_PATH_DOMAIN)
        append("|defaultInclude=")
        append(DEFAULT_INCLUDE_GLOB)
        append("|defaultExclude=")
        append(defaultExclude.joinToString(","))
        append("|filesAndInclude=")
        append(FILES_AND_INCLUDE_SEMANTICS)
        append("|excludeAffects=")
        append(EXCLUDE_AFFECTS)
        append("|jsonDepthCounting=")
        append(JSON_DEPTH_COUNTING)
        append("|jsonNodeCounting=")
        append(JSON_NODE_COUNTING)
        append("|selectorCounting=")
        append(SELECTOR_COUNTING)
        append("|selectorUtf8Measurement=")
        append(SELECTOR_UTF8_MEASUREMENT)
        append("|maxBytes=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES)
        append("|maxSelectors=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_SELECTORS)
        append("|maxSelectorUtf8Bytes=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_SELECTOR_UTF8_BYTES)
        append("|maxDepth=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_JSON_DEPTH)
        append("|maxNodes=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_JSON_NODES)
        appendSet("allowedTopLevel", allowedTopLevelKeys)
        appendSet("unsupportedTopLevel", unsupportedTopLevelKeys)
        append("|allowedBoolean=")
        append(
            allowedBooleanCompilerOptions.entries
                .sortedBy { entry -> entry.key }
                .joinToString(",") { (name, default) ->
                    "$name:${default.contractValue}"
                },
        )
        appendSet("profileManaged", profileManagedCompilerOptions)
        appendSet("escapeForbidden", escapeForbiddenCompilerOptions)
        appendSet("outputForbidden", outputForbiddenCompilerOptions)
        appendSet("customTransformers", customTransformerCompilerOptions)
        append("|unknownTopLevelFallback=")
        append(UNKNOWN_TOP_LEVEL_FALLBACK)
        append("|knownOptionFallback=")
        append(KNOWN_OPTION_FALLBACK)
        append("|ignoredTopLevelDiagnosticCode=")
        append(TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_TOP_LEVEL_PROPERTY)
        append("|ignoredCompilerOptionDiagnosticCode=")
        append(TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION)
        appendSet("policyCodes", policyCodes)
    }

    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        require(TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION == 2)
        require(allowedBooleanCompilerOptions.keys.intersect(profileManagedCompilerOptions).isEmpty())
        require(allowedBooleanCompilerOptions.keys.intersect(escapeForbiddenCompilerOptions).isEmpty())
        require(allowedBooleanCompilerOptions.keys.intersect(outputForbiddenCompilerOptions).isEmpty())
        require(profileManagedCompilerOptions.intersect(escapeForbiddenCompilerOptions).isEmpty())
        require(profileManagedCompilerOptions.intersect(outputForbiddenCompilerOptions).isEmpty())
        require(escapeForbiddenCompilerOptions.intersect(outputForbiddenCompilerOptions).isEmpty())
        require(
            customTransformerCompilerOptions.intersect(
                allowedBooleanCompilerOptions.keys +
                    profileManagedCompilerOptions +
                    escapeForbiddenCompilerOptions +
                    outputForbiddenCompilerOptions,
            ).isEmpty(),
        )
        R2Digests.requireLowerSha256(fingerprint, "Controlled tsconfig policy fingerprint")
    }

    fun topLevelRejectionCode(name: String): String? = when (name) {
        in allowedTopLevelKeys -> null
        in unsupportedTopLevelKeys ->
            TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE
        else -> null
    }

    fun topLevelWarningDiagnosticCode(name: String): Int? =
        TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_TOP_LEVEL_PROPERTY
            .takeIf {
                name !in allowedTopLevelKeys && name !in unsupportedTopLevelKeys
            }

    @Suppress("UNUSED_PARAMETER")
    fun compilerOptionRejectionCode(
        name: String,
        isKnownTypeScriptOption: Boolean,
    ): String? = when (name) {
        in allowedBooleanCompilerOptions -> null
        in profileManagedCompilerOptions ->
            TypeScriptR2Contract.CONFIG_POLICY_CODE_PROFILE_CONFLICT
        in escapeForbiddenCompilerOptions,
        in outputForbiddenCompilerOptions,
        -> TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE
        in customTransformerCompilerOptions ->
            TypeScriptR2Contract.CONFIG_POLICY_CODE_UNKNOWN_PROPERTY
        else -> null
    }

    fun compilerOptionWarningDiagnosticCode(
        name: String,
        isKnownTypeScriptOption: Boolean,
    ): Int? = TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION
        .takeIf {
            compilerOptionRejectionCode(name, isKnownTypeScriptOption) == null &&
                name !in allowedBooleanCompilerOptions
        }

    private fun StringBuilder.appendSet(
        label: String,
        values: Set<String>,
    ) {
        append('|')
        append(label)
        append('=')
        append(values.sorted().joinToString(","))
    }
}

private fun immutableSetOf(vararg values: String): Set<String> =
    Collections.unmodifiableSet(linkedSetOf(*values))

private fun <V> immutableMapOf(vararg values: Pair<String, V>): Map<String, V> =
    Collections.unmodifiableMap(linkedMapOf(*values))
