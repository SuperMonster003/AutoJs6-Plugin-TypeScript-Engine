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
    val compilerOptions: R2ProjectEffectiveOptions,
    val explicitProfileDefaults: R2ControlledTsConfigExplicitProfileDefaults,
    explicitBooleanOptions: Map<String, Boolean>,
    resolvedBooleanOptions: Map<String, Boolean>,
    val canonicalJson: String,
    val fingerprint: String,
) {

    val explicitBooleanOptions: Map<String, Boolean> =
        immutableSortedBooleanMap(explicitBooleanOptions)

    val resolvedBooleanOptions: Map<String, Boolean> =
        immutableSortedBooleanMap(resolvedBooleanOptions)

    init {
        require(
            this.explicitBooleanOptions.keys.all(
                R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions::containsKey,
            ),
        ) {
            "Controlled tsconfig contains an option outside the C1 allowlist."
        }
        require(
            this.resolvedBooleanOptions.keys ==
                R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.keys,
        ) {
            "Controlled tsconfig effective options do not cover the complete C1 allowlist."
        }
        val base = R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptions
        val expectedCore = base.copy(
            strict = this.resolvedBooleanOptions.getValue("strict"),
            skipLibCheck = this.resolvedBooleanOptions.getValue("skipLibCheck"),
            types = Collections.unmodifiableList(ArrayList(base.types)),
        )
        require(compilerOptions == expectedCore) {
            "Controlled tsconfig effective options changed an immutable Rhino profile value."
        }
        require(
            explicitProfileDefaults ==
                R2ControlledTsConfigExplicitProfileDefaults.PROVISIONAL_RHINO,
        ) {
            "Controlled tsconfig changed an explicit Rhino profile default."
        }
        require(
            canonicalJson == canonicalize(
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

            val base = R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptions
            val compilerOptions = base.copy(
                strict = resolved.getValue("strict"),
                skipLibCheck = resolved.getValue("skipLibCheck"),
                types = Collections.unmodifiableList(ArrayList(base.types)),
            )
            val explicitProfileDefaults =
                R2ControlledTsConfigExplicitProfileDefaults.PROVISIONAL_RHINO
            val canonicalJson = canonicalize(
                compilerOptions,
                explicitProfileDefaults,
                resolved,
            )
            return R2ControlledTsConfigEffectiveOptions(
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
            compilerOptions: R2ProjectEffectiveOptions,
            explicitProfileDefaults: R2ControlledTsConfigExplicitProfileDefaults,
            resolvedBooleanOptions: Map<String, Boolean>,
        ): String {
            val values = linkedMapOf<String, Any>(
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
                "jsx" to requireNotNull(compilerOptions.jsx),
                "jsxFactory" to requireNotNull(compilerOptions.jsxFactory),
                "jsxFragmentFactory" to requireNotNull(compilerOptions.jsxFragmentFactory),
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
            resolvedBooleanOptions.forEach { (name, value) -> values[name] = value }
            require(values.keys.sorted() == canonicalOptionKeys) {
                "Controlled tsconfig canonical option inventory drifted."
            }
            return buildString {
                append('{')
                canonicalOptionKeys.forEachIndexed { index, name ->
                    if (index != 0) append(',')
                    appendJsonString(name)
                    append(':')
                    appendCanonicalValue(values.getValue(name))
                }
                append('}')
            }
        }

        private fun StringBuilder.appendCanonicalValue(value: Any) {
            when (value) {
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
