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

internal enum class R2ControlledTsConfigTarget(
    val configValue: String,
    val compilerValue: Int,
    val defaultLibrary: String,
) {
    ES2018("ES2018", 5, "lib.es2018.d.ts"),
    ES2019("ES2019", 6, "lib.es2019.d.ts"),
    ES2020("ES2020", 7, "lib.es2020.d.ts"),
    ES2021("ES2021", 8, "lib.es2021.d.ts"),
    ES2022("ES2022", 9, "lib.es2022.d.ts"),
    ;

    companion object {
        fun fromCompilerValue(value: Int): R2ControlledTsConfigTarget =
            entries.singleOrNull { target -> target.compilerValue == value }
                ?: throw IllegalArgumentException(
                    "Controlled tsconfig target is not supported: $value.",
                )
    }
}

/**
 * Controlled tsconfig.json policy used by the public Rhino and NodeNext compilation paths.
 *
 * Revision 2 remains byte-for-byte addressable for existing Rhino callers. Revision 3 is an
 * append-only overlay that adds profile-bounded target/lib values plus non-structural emit
 * controls. Revision 4 adds a bounded baseUrl/paths table whose targets are confined to the
 * accepted project snapshot. All three revisions remain independently addressable for their
 * admitted profiles. Unknown properties remain warning-only. Options that can reach outside the
 * project snapshot or install caller-provided transformers remain explicit rejection branches.
 */
internal object R2ControlledTsConfigPolicy {

    const val LEGACY_POLICY_REVISION = 2
    const val REVISION_3 = 3
    const val LATEST_POLICY_REVISION = 4

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
    const val PATH_ALIAS_BASE_URL_DEFAULT = "."
    const val PATH_ALIAS_RESOLUTION_DOMAIN = "accepted-sources-snapshot-only"
    const val PATH_ALIAS_MATCHING = "exact-then-longest-prefix-and-suffix-wildcard"
    const val PATH_ALIAS_EMIT_REWRITE = "typescript-before-transform-relative-output-specifier"

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

    val revision3AllowedBooleanCompilerOptions: Map<String, R2ControlledTsConfigDefault> =
        immutableMapOf(
            *allowedBooleanCompilerOptions.entries
                .map { entry -> entry.key to entry.value }
                .toTypedArray(),
            "preserveConstEnums" to R2ControlledTsConfigDefault.FALSE,
            "removeComments" to R2ControlledTsConfigDefault.FALSE,
        )

    val revision3AllowedValueCompilerOptions: Set<String> = immutableSetOf(
        "lib",
        "target",
    )

    val revision4AllowedValueCompilerOptions: Set<String> = immutableSetOf(
        "baseUrl",
        "lib",
        "paths",
        "target",
    )

    val rhinoRevision3Targets: Set<R2ControlledTsConfigTarget> =
        immutableSetOf(R2ControlledTsConfigTarget.ES2018)

    val nodeRevision3Targets: Set<R2ControlledTsConfigTarget> =
        immutableSetOf(*R2ControlledTsConfigTarget.entries.toTypedArray())

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

    val revision3ProfileManagedCompilerOptions: Set<String> = immutableSetOf(
        *profileManagedCompilerOptions.filterNot { name -> name == "target" }.toTypedArray(),
    )

    val revision3EscapeForbiddenCompilerOptions: Set<String> = immutableSetOf(
        *escapeForbiddenCompilerOptions.filterNot { name -> name == "lib" }.toTypedArray(),
    )

    val revision4EscapeForbiddenCompilerOptions: Set<String> = immutableSetOf(
        *escapeForbiddenCompilerOptions.filterNot { name ->
            name == "baseUrl" || name == "lib" || name == "paths"
        }.toTypedArray(),
    )

    val revision3OutputForbiddenCompilerOptions: Set<String> = immutableSetOf(
        *outputForbiddenCompilerOptions.filterNot { name -> name == "removeComments" }.toTypedArray(),
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
        append(LEGACY_POLICY_REVISION)
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

    val revision3CanonicalInput: String = buildString {
        append("autojs6.typescript.controlled-tsconfig-policy.v3")
        append("|revision=")
        append(REVISION_3)
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
            revision3AllowedBooleanCompilerOptions.entries
                .sortedBy { entry -> entry.key }
                .joinToString(",") { (name, default) ->
                    "$name:${default.contractValue}"
                },
        )
        appendSet("allowedValue", revision3AllowedValueCompilerOptions)
        append("|rhinoTargets=")
        append(rhinoRevision3Targets.map { target -> target.configValue }.sorted().joinToString(","))
        append("|nodeTargets=")
        append(nodeRevision3Targets.map { target -> target.configValue }.sorted().joinToString(","))
        append("|libValues=target-subset-defaults-to-target")
        appendSet("profileManaged", revision3ProfileManagedCompilerOptions)
        appendSet("escapeForbidden", revision3EscapeForbiddenCompilerOptions)
        appendSet("outputForbidden", revision3OutputForbiddenCompilerOptions)
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

    val revision3Fingerprint: String = R2Digests.sha256(
        revision3CanonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    /**
     * Revision 4 is deliberately encoded as an overlay on the immutable revision-3 identity.
     * This prevents a paths implementation from silently changing any target/lib, selector,
     * boolean-option, diagnostic, or fallback behavior already frozen by revision 3.
     */
    val revision4CanonicalInput: String = buildString {
        append("autojs6.typescript.controlled-tsconfig-policy.v4")
        append("|revision=")
        append(LATEST_POLICY_REVISION)
        append("|parentRevision=")
        append(REVISION_3)
        append("|parentFingerprint=")
        append(revision3Fingerprint)
        appendSet("allowedValue", revision4AllowedValueCompilerOptions)
        appendSet("escapeForbidden", revision4EscapeForbiddenCompilerOptions)
        append("|pathAliasBaseUrlDefault=")
        append(PATH_ALIAS_BASE_URL_DEFAULT)
        append("|pathAliasResolutionDomain=")
        append(PATH_ALIAS_RESOLUTION_DOMAIN)
        append("|pathAliasMatching=")
        append(PATH_ALIAS_MATCHING)
        append("|pathAliasEmitRewrite=")
        append(PATH_ALIAS_EMIT_REWRITE)
        append("|maxPathAliases=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_ALIASES)
        append("|maxPathTargetsPerAlias=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_TARGETS_PER_ALIAS)
        append("|maxPathTargets=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_TARGETS)
        append("|maxPathPatternUtf8Bytes=")
        append(TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_PATH_PATTERN_UTF8_BYTES)
        append("|baseUrlGrammar=dot-or-safe-ascii-project-relative-directory")
        append("|pathKeyGrammar=safe-ascii-bare-specifier-pattern-at-most-one-wildcard")
        append("|pathTargetGrammar=safe-ascii-project-relative-pattern-at-most-one-wildcard")
        append("|wildcardCardinality=key-and-each-target-equal")
        append("|canonicalOrdering=keys-code-unit-targets-input-order-deduplicated")
        append("|emptyPaths=allowed")
    }

    val revision4Fingerprint: String = R2Digests.sha256(
        revision4CanonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        require(TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION == LATEST_POLICY_REVISION)
        require(
            TypeScriptR2Contract.LEGACY_CONTROLLED_TSCONFIG_POLICY_REVISION ==
                LEGACY_POLICY_REVISION,
        )
        require(
            TypeScriptR2Contract.PREVIOUS_CONTROLLED_TSCONFIG_POLICY_REVISION ==
                REVISION_3,
        )
        require(allowedBooleanCompilerOptions.keys.intersect(profileManagedCompilerOptions).isEmpty())
        require(allowedBooleanCompilerOptions.keys.intersect(escapeForbiddenCompilerOptions).isEmpty())
        require(allowedBooleanCompilerOptions.keys.intersect(outputForbiddenCompilerOptions).isEmpty())
        require(profileManagedCompilerOptions.intersect(escapeForbiddenCompilerOptions).isEmpty())
        require(profileManagedCompilerOptions.intersect(outputForbiddenCompilerOptions).isEmpty())
        require(escapeForbiddenCompilerOptions.intersect(outputForbiddenCompilerOptions).isEmpty())
        require(
            revision3AllowedBooleanCompilerOptions.keys.intersect(
                revision3ProfileManagedCompilerOptions +
                    revision3EscapeForbiddenCompilerOptions +
                    revision3OutputForbiddenCompilerOptions,
            ).isEmpty(),
        )
        require(
            revision3AllowedValueCompilerOptions.intersect(
                revision3ProfileManagedCompilerOptions +
                    revision3EscapeForbiddenCompilerOptions +
                    revision3OutputForbiddenCompilerOptions,
            ).isEmpty(),
        )
        require(
            revision4AllowedValueCompilerOptions.intersect(
                revision3ProfileManagedCompilerOptions +
                    revision4EscapeForbiddenCompilerOptions +
                    revision3OutputForbiddenCompilerOptions,
            ).isEmpty(),
        )
        require(
            customTransformerCompilerOptions.intersect(
                allowedBooleanCompilerOptions.keys +
                    profileManagedCompilerOptions +
                    escapeForbiddenCompilerOptions +
                    outputForbiddenCompilerOptions,
            ).isEmpty(),
        )
        R2Digests.requireLowerSha256(fingerprint, "Controlled tsconfig policy fingerprint")
        R2Digests.requireLowerSha256(
            revision3Fingerprint,
            "Controlled tsconfig revision-3 policy fingerprint",
        )
        R2Digests.requireLowerSha256(
            revision4Fingerprint,
            "Controlled tsconfig revision-4 policy fingerprint",
        )
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
        policyRevision: Int = LEGACY_POLICY_REVISION,
    ): String? {
        val allowedBooleans = allowedBooleanCompilerOptions(policyRevision).keys
        val allowedValues = when (policyRevision) {
            LEGACY_POLICY_REVISION -> emptySet()
            REVISION_3 -> revision3AllowedValueCompilerOptions
            LATEST_POLICY_REVISION -> revision4AllowedValueCompilerOptions
            else -> throw IllegalArgumentException(
                "Controlled tsconfig policy revision is not supported: $policyRevision.",
            )
        }
        val profileManaged = if (policyRevision >= REVISION_3) {
            revision3ProfileManagedCompilerOptions
        } else {
            profileManagedCompilerOptions
        }
        val escapeForbidden = when (policyRevision) {
            LEGACY_POLICY_REVISION -> escapeForbiddenCompilerOptions
            REVISION_3 -> revision3EscapeForbiddenCompilerOptions
            LATEST_POLICY_REVISION -> revision4EscapeForbiddenCompilerOptions
            else -> error("Unsupported controlled tsconfig revision.")
        }
        val outputForbidden = if (policyRevision >= REVISION_3) {
            revision3OutputForbiddenCompilerOptions
        } else {
            outputForbiddenCompilerOptions
        }
        return when (name) {
            in allowedBooleans,
            in allowedValues,
            -> null
            in profileManaged ->
            TypeScriptR2Contract.CONFIG_POLICY_CODE_PROFILE_CONFLICT
            in escapeForbidden,
            in outputForbidden,
            -> TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE
            in customTransformerCompilerOptions ->
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNKNOWN_PROPERTY
            else -> null
        }
    }

    fun compilerOptionWarningDiagnosticCode(
        name: String,
        isKnownTypeScriptOption: Boolean,
        policyRevision: Int = LEGACY_POLICY_REVISION,
    ): Int? = TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION
        .takeIf {
            compilerOptionRejectionCode(name, isKnownTypeScriptOption, policyRevision) == null &&
                name !in allowedBooleanCompilerOptions(policyRevision) &&
                (
                    policyRevision == LEGACY_POLICY_REVISION ||
                        name !in if (policyRevision == REVISION_3) {
                            revision3AllowedValueCompilerOptions
                        } else {
                            revision4AllowedValueCompilerOptions
                        }
                )
        }

    fun isSupportedPolicy(
        revision: Int,
        policyFingerprint: String,
    ): Boolean = when (revision) {
        LEGACY_POLICY_REVISION -> R2Digests.equal(policyFingerprint, fingerprint)
        REVISION_3 -> R2Digests.equal(policyFingerprint, revision3Fingerprint)
        LATEST_POLICY_REVISION -> R2Digests.equal(policyFingerprint, revision4Fingerprint)
        else -> false
    }

    fun fingerprintForRevision(revision: Int): String = when (revision) {
        LEGACY_POLICY_REVISION -> fingerprint
        REVISION_3 -> revision3Fingerprint
        LATEST_POLICY_REVISION -> revision4Fingerprint
        else -> throw IllegalArgumentException(
            "Controlled tsconfig policy revision is not supported: $revision.",
        )
    }

    fun allowedBooleanCompilerOptions(revision: Int): Map<String, R2ControlledTsConfigDefault> =
        when (revision) {
            LEGACY_POLICY_REVISION -> allowedBooleanCompilerOptions
            REVISION_3,
            LATEST_POLICY_REVISION,
            -> revision3AllowedBooleanCompilerOptions
            else -> throw IllegalArgumentException(
                "Controlled tsconfig policy revision is not supported: $revision.",
            )
        }

    fun supportedTargets(profile: R2ProjectProfile): Set<R2ControlledTsConfigTarget> =
        when (profile) {
            R2ProjectProfile.PROVISIONAL_RHINO -> rhinoRevision3Targets
            R2ProjectProfile.PROVISIONAL_NODE_NEXT -> nodeRevision3Targets
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

private fun <T> immutableSetOf(vararg values: T): Set<T> =
    Collections.unmodifiableSet(linkedSetOf(*values))

private fun <V> immutableMapOf(vararg values: Pair<String, V>): Map<String, V> =
    Collections.unmodifiableMap(linkedMapOf(*values))
