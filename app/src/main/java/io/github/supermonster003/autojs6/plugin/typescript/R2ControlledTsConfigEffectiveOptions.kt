package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

internal class R2ControlledTsConfigEffectiveOptionsException(
    val policyCode: String,
    message: String,
) : IllegalArgumentException(message)

internal data class R2ControlledTsConfigExplicitProfileDefaults(
    val allowSyntheticDefaultImports: Boolean,
    val alwaysStrict: Boolean,
    val esModuleInterop: Boolean,
    val forceConsistentCasingInFileNames: Boolean,
    val moduleDetection: Int,
    val resolveJsonModule: Boolean,
    val useDefineForClassFields: Boolean,
) {
    companion object {
        val PROVISIONAL_RHINO = R2ControlledTsConfigExplicitProfileDefaults(
            allowSyntheticDefaultImports = true,
            alwaysStrict = true,
            esModuleInterop = true,
            forceConsistentCasingInFileNames = true,
            moduleDetection = 2,
            resolveJsonModule = false,
            useDefineForClassFields = false,
        )

        fun revision3(
            profile: R2ProjectProfile,
            target: R2ControlledTsConfigTarget,
        ): R2ControlledTsConfigExplicitProfileDefaults = when (profile) {
            R2ProjectProfile.PROVISIONAL_RHINO -> {
                require(target == R2ControlledTsConfigTarget.ES2018)
                PROVISIONAL_RHINO
            }
            R2ProjectProfile.PROVISIONAL_NODE_NEXT ->
                R2ControlledTsConfigExplicitProfileDefaults(
                    allowSyntheticDefaultImports = true,
                    alwaysStrict = true,
                    esModuleInterop = true,
                    forceConsistentCasingInFileNames = true,
                    moduleDetection = 3,
                    resolveJsonModule = true,
                    useDefineForClassFields =
                        target.compilerValue >= R2ControlledTsConfigTarget.ES2022.compilerValue,
                )
        }
    }
}

/**
 * R2-M2-C3 authority for one resolved controlled-tsconfig option set.
 *
 * The historical config-none authority keeps its exact 18-key canonical JSON. This type is a
 * separate schema-2 overlay: it combines those fixed Rhino profile values with all 18 C1
 * user-controllable booleans, makes every strict-family value explicit, freezes seven previously
 * computed TypeScript 6.0.3 profile defaults plus the private TSX lowering contract, and owns the
 * resulting 44-key canonical JSON and
 * SHA-256.
 */
internal class R2ControlledTsConfigEffectiveOptions private constructor(
    val policyRevision: Int,
    val revision: Int,
    val profileAuthority: R2CompilationProfileAuthority,
    val compilerOptions: R2ProjectEffectiveOptions,
    val explicitProfileDefaults: R2ControlledTsConfigExplicitProfileDefaults,
    explicitBooleanOptions: Map<String, Boolean>,
    resolvedBooleanOptions: Map<String, Boolean>,
    val canonicalJson: String,
    val fingerprint: String,
) {

    val canonicalOptionKeys: List<String> = when (revision) {
        REVISION -> Companion.canonicalOptionKeys
        REVISION_3 -> canonicalOptionKeysRevision3(profileAuthority.profile)
        REVISION_4 -> canonicalOptionKeysRevision4(profileAuthority.profile)
        else -> error("Controlled tsconfig effective-options revision is not supported.")
    }

    val explicitBooleanOptions: Map<String, Boolean> =
        immutableSortedBooleanMap(explicitBooleanOptions)

    val resolvedBooleanOptions: Map<String, Boolean> =
        immutableSortedBooleanMap(resolvedBooleanOptions)

    init {
        require(
            this.explicitBooleanOptions.keys.all(
                R2ControlledTsConfigPolicy
                    .allowedBooleanCompilerOptions(policyRevision)::containsKey,
            ),
        ) {
            "Controlled tsconfig contains an option outside the C1 allowlist."
        }
        require(
            this.resolvedBooleanOptions.keys ==
                R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions(policyRevision).keys,
        ) {
            "Controlled tsconfig effective options do not cover the complete C1 allowlist."
        }
        val target = R2ControlledTsConfigTarget.fromCompilerValue(compilerOptions.target)
        val base = profileAuthority.effectiveOptions
        val expectedCore = when (policyRevision) {
            R2ControlledTsConfigPolicy.LEGACY_POLICY_REVISION -> {
                require(revision == REVISION)
                require(profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO)
                base.copy(
                    strict = this.resolvedBooleanOptions.getValue("strict"),
                    skipLibCheck = this.resolvedBooleanOptions.getValue("skipLibCheck"),
                    types = Collections.unmodifiableList(ArrayList(base.types)),
                )
            }
            R2ControlledTsConfigPolicy.REVISION_3,
            R2ControlledTsConfigPolicy.LATEST_POLICY_REVISION,
            -> {
                require(
                    revision == if (
                        policyRevision == R2ControlledTsConfigPolicy.REVISION_3
                    ) {
                        REVISION_3
                    } else {
                        REVISION_4
                    },
                )
                require(
                    target in R2ControlledTsConfigPolicy.supportedTargets(profileAuthority.profile),
                )
                val libraries = requireNotNull(compilerOptions.lib)
                require(libraries.isNotEmpty() && libraries == libraries.distinct().sorted())
                require(
                    libraries.all { library ->
                        R2ControlledTsConfigPolicy.supportedTargets(profileAuthority.profile)
                            .any { supported -> supported.defaultLibrary == library }
                    },
                )
                val aliases = if (revision == REVISION_4) {
                    R2ControlledPathAliasTable.normalize(
                        baseUrl = compilerOptions.baseUrl,
                        paths = requireNotNull(compilerOptions.paths),
                    )
                } else {
                    R2ControlledPathAliasTable.EMPTY
                }
                base.copy(
                    strict = this.resolvedBooleanOptions.getValue("strict"),
                    skipLibCheck = this.resolvedBooleanOptions.getValue("skipLibCheck"),
                    target = target.compilerValue,
                    types = Collections.unmodifiableList(ArrayList(base.types)),
                    lib = Collections.unmodifiableList(ArrayList(libraries)),
                    removeComments = this.resolvedBooleanOptions.getValue("removeComments"),
                    preserveConstEnums = this.resolvedBooleanOptions.getValue("preserveConstEnums"),
                    baseUrl = aliases.baseUrl,
                    paths = aliases.paths.takeIf { revision == REVISION_4 },
                )
            }
            else -> error("Controlled tsconfig policy revision is unsupported.")
        }
        require(compilerOptions == expectedCore) {
            "Controlled tsconfig effective options changed an immutable Rhino profile value."
        }
        require(
            explicitProfileDefaults ==
                if (revision == REVISION) {
                    R2ControlledTsConfigExplicitProfileDefaults.PROVISIONAL_RHINO
                } else {
                    R2ControlledTsConfigExplicitProfileDefaults.revision3(
                        profileAuthority.profile,
                        target,
                    )
                },
        ) {
            "Controlled tsconfig changed an explicit Rhino profile default."
        }
        require(
            canonicalJson == canonicalize(
                revision,
                profileAuthority.profile,
                compilerOptions,
                explicitProfileDefaults,
                this.resolvedBooleanOptions,
            ),
        ) {
            "Controlled tsconfig effective options are not canonical."
        }
        require(canonicalJson.all { character -> character.code in 0x20..0x7e }) {
            "Controlled tsconfig effective options must use printable ASCII canonical JSON."
        }
        R2Digests.requireLowerSha256(
            fingerprint,
            "Controlled tsconfig effective-options fingerprint",
        )
        require(
            R2Digests.equal(
                fingerprint,
                R2Digests.sha256(canonicalJson.toByteArray(StandardCharsets.US_ASCII)),
            ),
        ) {
            "Controlled tsconfig effective-options fingerprint does not match canonical JSON."
        }
    }

    companion object {

        const val REVISION = 2
        const val REVISION_3 = 3
        const val REVISION_4 = 4

        val canonicalOptionKeys: List<String> = Collections.unmodifiableList(
            listOf(
                "allowJs",
                "allowSyntheticDefaultImports",
                "alwaysStrict",
                "checkJs",
                "esModuleInterop",
                "exactOptionalPropertyTypes",
                "forceConsistentCasingInFileNames",
                "ignoreDeprecations",
                "incremental",
                "inlineSourceMap",
                "inlineSources",
                "jsx",
                "jsxFactory",
                "jsxFragmentFactory",
                "module",
                "moduleDetection",
                "moduleResolution",
                "newLine",
                "noEmitOnError",
                "noFallthroughCasesInSwitch",
                "noImplicitAny",
                "noImplicitOverride",
                "noImplicitReturns",
                "noImplicitThis",
                "noLib",
                "noPropertyAccessFromIndexSignature",
                "noUncheckedIndexedAccess",
                "noUnusedLocals",
                "noUnusedParameters",
                "outDir",
                "resolveJsonModule",
                "rootDir",
                "skipLibCheck",
                "sourceMap",
                "strict",
                "strictBindCallApply",
                "strictBuiltinIteratorReturn",
                "strictFunctionTypes",
                "strictNullChecks",
                "strictPropertyInitialization",
                "target",
                "types",
                "useDefineForClassFields",
                "useUnknownInCatchVariables",
            ),
        )

        val canonicalOptionKeysRevision3Rhino: List<String> =
            Collections.unmodifiableList(
                (canonicalOptionKeys + listOf("lib", "preserveConstEnums", "removeComments"))
                    .distinct()
                    .sorted(),
            )

        val canonicalOptionKeysRevision3Node: List<String> =
            Collections.unmodifiableList(
                canonicalOptionKeysRevision3Rhino.filterNot { name ->
                    name == "jsx" ||
                        name == "jsxFactory" ||
                        name == "jsxFragmentFactory"
                },
            )

        fun canonicalOptionKeysRevision3(profile: R2ProjectProfile): List<String> =
            when (profile) {
                R2ProjectProfile.PROVISIONAL_RHINO -> canonicalOptionKeysRevision3Rhino
                R2ProjectProfile.PROVISIONAL_NODE_NEXT -> canonicalOptionKeysRevision3Node
            }

        val canonicalOptionKeysRevision4Rhino: List<String> =
            Collections.unmodifiableList(
                (canonicalOptionKeysRevision3Rhino + listOf("baseUrl", "paths"))
                    .distinct()
                    .sorted(),
            )

        val canonicalOptionKeysRevision4Node: List<String> =
            Collections.unmodifiableList(
                (canonicalOptionKeysRevision3Node + listOf("baseUrl", "paths"))
                    .distinct()
                    .sorted(),
            )

        fun canonicalOptionKeysRevision4(profile: R2ProjectProfile): List<String> =
            when (profile) {
                R2ProjectProfile.PROVISIONAL_RHINO -> canonicalOptionKeysRevision4Rhino
                R2ProjectProfile.PROVISIONAL_NODE_NEXT -> canonicalOptionKeysRevision4Node
            }

        fun resolve(
            explicitBooleanOptions: Map<String, Boolean>,
        ): R2ControlledTsConfigEffectiveOptions {
            val explicit = immutableSortedBooleanMap(explicitBooleanOptions)
            require(
                explicit.keys.all(
                    R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions::containsKey,
                ),
            ) {
                "Controlled tsconfig contains an option outside the C1 allowlist."
            }

            val strict = explicit["strict"] ?: true
            val resolved = LinkedHashMap<String, Boolean>()
            R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.entries
                .sortedBy { entry -> entry.key }
                .forEach { (name, default) ->
                    resolved[name] = explicit[name] ?: when (default) {
                        R2ControlledTsConfigDefault.TRUE -> true
                        R2ControlledTsConfigDefault.FALSE -> false
                        R2ControlledTsConfigDefault.INHERIT_STRICT -> strict
                    }
                }

            validateStrictNullRelationships(resolved)

            val base = R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptions
            val compilerOptions = base.copy(
                strict = resolved.getValue("strict"),
                skipLibCheck = resolved.getValue("skipLibCheck"),
                types = Collections.unmodifiableList(ArrayList(base.types)),
            )
            val explicitProfileDefaults =
                R2ControlledTsConfigExplicitProfileDefaults.PROVISIONAL_RHINO
            val canonicalJson = canonicalize(
                revision = REVISION,
                profile = R2ProjectProfile.PROVISIONAL_RHINO,
                compilerOptions = compilerOptions,
                explicitProfileDefaults = explicitProfileDefaults,
                resolvedBooleanOptions = resolved,
            )
            return R2ControlledTsConfigEffectiveOptions(
                policyRevision = R2ControlledTsConfigPolicy.LEGACY_POLICY_REVISION,
                revision = REVISION,
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                compilerOptions = compilerOptions,
                explicitProfileDefaults = explicitProfileDefaults,
                explicitBooleanOptions = explicit,
                resolvedBooleanOptions = resolved,
                canonicalJson = canonicalJson,
                fingerprint = R2Digests.sha256(
                    canonicalJson.toByteArray(StandardCharsets.US_ASCII),
                ),
            )
        }

        fun resolveRevision3(
            profileAuthority: R2CompilationProfileAuthority,
            explicitBooleanOptions: Map<String, Boolean>,
            target: R2ControlledTsConfigTarget = R2ControlledTsConfigTarget.ES2018,
            libraries: List<String>? = null,
        ): R2ControlledTsConfigEffectiveOptions = resolveModern(
            policyRevision = R2ControlledTsConfigPolicy.REVISION_3,
            effectiveOptionsRevision = REVISION_3,
            profileAuthority = profileAuthority,
            explicitBooleanOptions = explicitBooleanOptions,
            target = target,
            libraries = libraries,
            pathAliases = R2ControlledPathAliasTable.EMPTY,
        )

        fun resolveRevision4(
            profileAuthority: R2CompilationProfileAuthority,
            explicitBooleanOptions: Map<String, Boolean>,
            target: R2ControlledTsConfigTarget = R2ControlledTsConfigTarget.ES2018,
            libraries: List<String>? = null,
            baseUrl: String? = null,
            paths: Map<String, List<String>> = emptyMap(),
        ): R2ControlledTsConfigEffectiveOptions = resolveModern(
            policyRevision = R2ControlledTsConfigPolicy.LATEST_POLICY_REVISION,
            effectiveOptionsRevision = REVISION_4,
            profileAuthority = profileAuthority,
            explicitBooleanOptions = explicitBooleanOptions,
            target = target,
            libraries = libraries,
            pathAliases = R2ControlledPathAliasTable.normalize(baseUrl, paths),
        )

        private fun resolveModern(
            policyRevision: Int,
            effectiveOptionsRevision: Int,
            profileAuthority: R2CompilationProfileAuthority,
            explicitBooleanOptions: Map<String, Boolean>,
            target: R2ControlledTsConfigTarget,
            libraries: List<String>?,
            pathAliases: R2ControlledPathAliasTable,
        ): R2ControlledTsConfigEffectiveOptions {
            require(
                policyRevision == R2ControlledTsConfigPolicy.REVISION_3 &&
                    effectiveOptionsRevision == REVISION_3 ||
                    policyRevision == R2ControlledTsConfigPolicy.LATEST_POLICY_REVISION &&
                    effectiveOptionsRevision == REVISION_4,
            ) {
                "Controlled tsconfig modern policy/effective revision pair is invalid."
            }
            require(
                profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO ||
                    profileAuthority ==
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            ) {
                "Modern controlled tsconfig requires the current Rhino or Node profile."
            }
            require(target in R2ControlledTsConfigPolicy.supportedTargets(profileAuthority.profile)) {
                "Controlled tsconfig target is outside the selected profile range."
            }
            val allowed = R2ControlledTsConfigPolicy.revision3AllowedBooleanCompilerOptions
            val explicit = immutableSortedBooleanMap(explicitBooleanOptions)
            require(explicit.keys.all(allowed::containsKey)) {
                "Controlled tsconfig contains an option outside the revision-3 allowlist."
            }

            val strict = explicit["strict"] ?: true
            val resolved = LinkedHashMap<String, Boolean>()
            allowed.entries.sortedBy { entry -> entry.key }.forEach { (name, default) ->
                resolved[name] = explicit[name] ?: when (default) {
                    R2ControlledTsConfigDefault.TRUE -> true
                    R2ControlledTsConfigDefault.FALSE -> false
                    R2ControlledTsConfigDefault.INHERIT_STRICT -> strict
                }
            }
            validateStrictNullRelationships(resolved)

            val supportedLibraries = R2ControlledTsConfigPolicy
                .supportedTargets(profileAuthority.profile)
                .mapTo(linkedSetOf()) { supported -> supported.defaultLibrary }
            val normalizedLibraries = (libraries ?: listOf(target.defaultLibrary))
                .distinct()
                .sorted()
            require(
                normalizedLibraries.isNotEmpty() &&
                    normalizedLibraries.all(supportedLibraries::contains),
            ) {
                "Controlled tsconfig lib contains an entry outside the selected profile subset."
            }

            val base = profileAuthority.effectiveOptions
            val compilerOptions = base.copy(
                strict = resolved.getValue("strict"),
                skipLibCheck = resolved.getValue("skipLibCheck"),
                target = target.compilerValue,
                types = Collections.unmodifiableList(ArrayList(base.types)),
                lib = Collections.unmodifiableList(ArrayList(normalizedLibraries)),
                removeComments = resolved.getValue("removeComments"),
                preserveConstEnums = resolved.getValue("preserveConstEnums"),
                baseUrl = pathAliases.baseUrl,
                paths = pathAliases.paths.takeIf { effectiveOptionsRevision == REVISION_4 },
            )
            val explicitProfileDefaults =
                R2ControlledTsConfigExplicitProfileDefaults.revision3(
                    profileAuthority.profile,
                    target,
                )
            val canonicalJson = canonicalize(
                revision = effectiveOptionsRevision,
                profile = profileAuthority.profile,
                compilerOptions = compilerOptions,
                explicitProfileDefaults = explicitProfileDefaults,
                resolvedBooleanOptions = resolved,
            )
            return R2ControlledTsConfigEffectiveOptions(
                policyRevision = policyRevision,
                revision = effectiveOptionsRevision,
                profileAuthority = profileAuthority,
                compilerOptions = compilerOptions,
                explicitProfileDefaults = explicitProfileDefaults,
                explicitBooleanOptions = explicit,
                resolvedBooleanOptions = resolved,
                canonicalJson = canonicalJson,
                fingerprint = R2Digests.sha256(
                    canonicalJson.toByteArray(StandardCharsets.US_ASCII),
                ),
            )
        }

        private fun canonicalize(
            revision: Int,
            profile: R2ProjectProfile,
            compilerOptions: R2ProjectEffectiveOptions,
            explicitProfileDefaults: R2ControlledTsConfigExplicitProfileDefaults,
            resolvedBooleanOptions: Map<String, Boolean>,
        ): String {
            val values = linkedMapOf<String, Any?>(
                "allowJs" to compilerOptions.allowJs,
                "allowSyntheticDefaultImports" to
                    explicitProfileDefaults.allowSyntheticDefaultImports,
                "alwaysStrict" to explicitProfileDefaults.alwaysStrict,
                "checkJs" to compilerOptions.checkJs,
                "esModuleInterop" to explicitProfileDefaults.esModuleInterop,
                "forceConsistentCasingInFileNames" to
                    explicitProfileDefaults.forceConsistentCasingInFileNames,
                "ignoreDeprecations" to compilerOptions.ignoreDeprecations,
                "incremental" to compilerOptions.incremental,
                "inlineSourceMap" to compilerOptions.inlineSourceMap,
                "inlineSources" to compilerOptions.inlineSources,
                "module" to compilerOptions.module,
                "moduleDetection" to explicitProfileDefaults.moduleDetection,
                "moduleResolution" to compilerOptions.moduleResolution,
                "newLine" to compilerOptions.newLine,
                "noEmitOnError" to compilerOptions.noEmitOnError,
                "noLib" to compilerOptions.noLib,
                "outDir" to compilerOptions.outDir,
                "resolveJsonModule" to explicitProfileDefaults.resolveJsonModule,
                "rootDir" to compilerOptions.rootDir,
                "skipLibCheck" to compilerOptions.skipLibCheck,
                "sourceMap" to compilerOptions.sourceMap,
                "strict" to compilerOptions.strict,
                "target" to compilerOptions.target,
                "types" to compilerOptions.types,
                "useDefineForClassFields" to explicitProfileDefaults.useDefineForClassFields,
            )
            if (profile == R2ProjectProfile.PROVISIONAL_RHINO) {
                values["jsx"] = requireNotNull(compilerOptions.jsx)
                values["jsxFactory"] = requireNotNull(compilerOptions.jsxFactory)
                values["jsxFragmentFactory"] =
                    requireNotNull(compilerOptions.jsxFragmentFactory)
            }
            if (revision == REVISION_3 || revision == REVISION_4) {
                values["lib"] = requireNotNull(compilerOptions.lib)
                values["removeComments"] = compilerOptions.removeComments
                values["preserveConstEnums"] = compilerOptions.preserveConstEnums
            }
            if (revision == REVISION_4) {
                values["baseUrl"] = compilerOptions.baseUrl
                values["paths"] = requireNotNull(compilerOptions.paths)
            }
            resolvedBooleanOptions.forEach { (name, value) -> values[name] = value }
            val expectedKeys = when (revision) {
                REVISION -> canonicalOptionKeys
                REVISION_3 -> canonicalOptionKeysRevision3(profile)
                REVISION_4 -> canonicalOptionKeysRevision4(profile)
                else -> error("Controlled tsconfig effective-options revision is unsupported.")
            }
            require(values.keys.sorted() == expectedKeys) {
                "Controlled tsconfig canonical option inventory drifted."
            }
            return buildString {
                append('{')
                expectedKeys.forEachIndexed { index, name ->
                    if (index != 0) append(',')
                    appendJsonString(name)
                    append(':')
                    appendCanonicalValue(values.getValue(name))
                }
                append('}')
            }
        }

        private fun validateStrictNullRelationships(
            resolved: Map<String, Boolean>,
        ) {
            if (
                !resolved.getValue("strictNullChecks") &&
                (
                    resolved.getValue("exactOptionalPropertyTypes") ||
                        resolved.getValue("strictPropertyInitialization")
                    )
            ) {
                throw R2ControlledTsConfigEffectiveOptionsException(
                    policyCode = TypeScriptR2Contract.CONFIG_POLICY_CODE_INVALID_VALUE,
                    message =
                        "Controlled tsconfig contains an invalid strict-null option combination.",
                )
            }
        }

        private fun StringBuilder.appendCanonicalValue(value: Any?) {
            when (value) {
                null -> append("null")
                is Boolean -> append(value)
                is Int -> append(value)
                is String -> appendJsonString(value)
                is List<*> -> {
                    append('[')
                    value.forEachIndexed { index, item ->
                        if (index != 0) append(',')
                        appendJsonString(item as? String ?: error("Option list must contain strings."))
                    }
                    append(']')
                }
                is Map<*, *> -> {
                    append('{')
                    value.entries.forEachIndexed { index, entry ->
                        if (index != 0) append(',')
                        appendJsonString(
                            entry.key as? String
                                ?: error("Option map keys must be strings."),
                        )
                        append(':')
                        appendCanonicalValue(entry.value)
                    }
                    append('}')
                }
                else -> error("Unsupported canonical compiler-option value: ${value.javaClass.name}")
            }
        }

        private fun StringBuilder.appendJsonString(value: String) {
            append('"')
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        require(character.code >= 0x20) {
                            "Canonical compiler option contains a control character."
                        }
                        append(character)
                    }
                }
            }
            append('"')
        }

        private fun immutableSortedBooleanMap(
            values: Map<String, Boolean>,
        ): Map<String, Boolean> = Collections.unmodifiableMap(
            values.entries
                .sortedBy { entry -> entry.key }
                .associateTo(LinkedHashMap()) { entry -> entry.key to entry.value },
        )
    }
}
