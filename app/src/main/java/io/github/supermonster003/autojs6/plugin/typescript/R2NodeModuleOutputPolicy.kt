package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal enum class R2ModuleFormat(
    val contractValue: String,
) {
    COMMONJS("commonjs"),
    ESM("esm"),
}

internal enum class R2PackageTypeState(
    val contractValue: String,
) {
    NO_BOUNDARY("no-boundary"),
    TYPE_ABSENT("missing"),
    COMMONJS("commonjs"),
    MODULE("module"),
}

internal enum class R2NodeModuleOutputPolicyCode(
    val contractValue: String,
) {
    PACKAGE_TYPE_INVALID("NODE_PACKAGE_TYPE_INVALID"),
}

internal class R2NodeModuleOutputPolicyException(
    val policyCode: R2NodeModuleOutputPolicyCode,
    message: String,
) : IllegalArgumentException(message)

/** R2-M2-P2-0 authority for Node module format, output pairing, URI and resolver-v2 semantics. */
internal object R2NodeModuleOutputPolicy {

    const val SCHEMA = "autojs6.typescript.node-module-output-policy"
    const val SCHEMA_REVISION = 1
    const val POLICY_REVISION = 1
    const val RHINO_PROFILE_REVISION = 1
    const val RHINO_TSX_PROFILE_REVISION = 2
    const val NODE_LEGACY_PROFILE_REVISION = 1
    const val NODE_PROFILE_REVISION = 2

    const val SOURCE_ROOT = "/sources"
    const val OUTPUT_ROOT = "/outputs"
    const val SOURCE_URI_SCHEME = "autojs"
    const val SOURCE_URI_AUTHORITY = "source"
    const val GENERATED_URI_AUTHORITY = "generated"
    const val PROJECT_NAMESPACE_PREFIX = "p-"
    const val PROJECT_NAMESPACE_MODE =
        "p-lowercase-sha256-strict-utf8-project-id"
    const val PATH_ENCODING_MODE =
        "rfc3986-segment-utf8-unreserved-uppercase-percent"
    const val MAXIMUM_RELATIVE_PATH_CHARS =
        TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES
    const val MAXIMUM_PATH_DEPTH = TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH
    const val MAXIMUM_URI_ASCII_BYTES = TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS
    const val COLLISION_MODE = "exact+ascii-case-fold"

    const val PACKAGE_TYPE_KEY = "type"

    const val PACKAGE_SCOPE = "project-root+explicit-c5-dependency-roots"
    const val PACKAGE_BOUNDARY_MODE = "longest-explicit-c5-prefix"
    const val PACKAGE_TYPE_POLICY =
        "no-boundary:commonjs;missing:commonjs;module:esm;commonjs:commonjs;other:reject"

    const val RESOLVER_POLICY_REVISION = 2
    const val RESOLVER_CONDITION_SOURCE = "implied-module-format"
    const val RESOLVER_V1_COMPATIBILITY = "unchanged-reject-new-kinds"

    const val OUTPUT_MATRIX =
        "rhino:.ts:commonjs:.js;node:.ts:package:.js;" +
            "node:.mts:esm:.mjs;node:.cts:commonjs:.cjs"

    val sourceExtensions: List<String> = listOf(".ts", ".mts", ".cts")

    val moduleFormats: List<String> =
        R2ModuleFormat.entries.map { format -> format.contractValue }

    val packageTypeStates: List<String> =
        R2PackageTypeState.entries.map { state -> state.contractValue }

    val policyCodes: List<String> =
        R2NodeModuleOutputPolicyCode.entries.map { code -> code.contractValue }

    val resolverSubstitutions: List<String> = listOf(
        ".js->.ts",
        ".mjs->.mts",
        ".cjs->.cts",
        ".d.ts->.d.ts",
        ".d.mts->.d.mts",
        ".d.cts->.d.cts",
    )

    val canonicalInput: String = buildString {
        append("schema=")
        append(SCHEMA)
        append("|schemaRevision=")
        append(SCHEMA_REVISION)
        append("|policyRevision=")
        append(POLICY_REVISION)
        append("|rhinoProfileRevision=")
        append(RHINO_PROFILE_REVISION)
        append("|nodeLegacyProfileRevision=")
        append(NODE_LEGACY_PROFILE_REVISION)
        append("|nodeProfileRevision=")
        append(NODE_PROFILE_REVISION)
        append("|sourceRoot=")
        append(SOURCE_ROOT)
        append("|outputRoot=")
        append(OUTPUT_ROOT)
        append("|sourceExtensions=")
        append(sourceExtensions.joinToString(","))
        append("|moduleFormats=")
        append(moduleFormats.joinToString(","))
        append("|outputMatrix=")
        append(OUTPUT_MATRIX)
        append("|packageTypeKey=")
        append(PACKAGE_TYPE_KEY)
        append("|packageTypeStates=")
        append(packageTypeStates.joinToString(","))
        append("|packageTypePolicy=")
        append(PACKAGE_TYPE_POLICY)
        append("|packageScope=")
        append(PACKAGE_SCOPE)
        append("|packageBoundaryMode=")
        append(PACKAGE_BOUNDARY_MODE)
        append("|packageMetadataPolicyFingerprint=")
        append(R2PackageMetadataPolicy.fingerprint)
        append("|sourceUriScheme=")
        append(SOURCE_URI_SCHEME)
        append("|sourceUriAuthority=")
        append(SOURCE_URI_AUTHORITY)
        append("|generatedUriAuthority=")
        append(GENERATED_URI_AUTHORITY)
        append("|projectNamespacePrefix=")
        append(PROJECT_NAMESPACE_PREFIX)
        append("|projectNamespaceMode=")
        append(PROJECT_NAMESPACE_MODE)
        append("|pathEncoding=")
        append(PATH_ENCODING_MODE)
        append("|maximumRelativePathChars=")
        append(MAXIMUM_RELATIVE_PATH_CHARS)
        append("|maximumPathDepth=")
        append(MAXIMUM_PATH_DEPTH)
        append("|maximumUriAsciiBytes=")
        append(MAXIMUM_URI_ASCII_BYTES)
        append("|collisionMode=")
        append(COLLISION_MODE)
        append("|resolverPolicyRevision=")
        append(RESOLVER_POLICY_REVISION)
        append("|resolverSubstitutions=")
        append(resolverSubstitutions.joinToString(","))
        append("|resolverConditionSource=")
        append(RESOLVER_CONDITION_SOURCE)
        append("|resolverV1Compatibility=")
        append(RESOLVER_V1_COMPATIBILITY)
        append("|policyCodes=")
        append(policyCodes.joinToString(","))
    }

    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        check(canonicalInput.all { character -> character.code in 0x20..0x7e })
        check(PROJECT_NAMESPACE_PREFIX == "p-")
        check(sourceExtensions.size == sourceExtensions.toSet().size)
        check(moduleFormats.size == moduleFormats.toSet().size && moduleFormats.size == 2)
        check(packageTypeStates.size == packageTypeStates.toSet().size)
        check(policyCodes.size == policyCodes.toSet().size)
        R2Digests.requireLowerSha256(fingerprint, "Node module/output policy fingerprint")
    }

    /**
     * Interprets only the exact top-level package `type` authority. A null object means that no
     * accepted package boundary exists. Invalid values never inherit TypeScript's CommonJS
     * fallback; P2 deliberately fails closed.
     */
    fun parsePackageType(packageJson: Map<String, Any?>?): R2PackageTypeState {
        if (packageJson == null) return R2PackageTypeState.NO_BOUNDARY
        if (!packageJson.containsKey(PACKAGE_TYPE_KEY)) {
            return R2PackageTypeState.TYPE_ABSENT
        }
        return when (packageJson[PACKAGE_TYPE_KEY]) {
            R2PackageTypeState.COMMONJS.contractValue -> R2PackageTypeState.COMMONJS
            R2PackageTypeState.MODULE.contractValue -> R2PackageTypeState.MODULE
            else -> throw R2NodeModuleOutputPolicyException(
                R2NodeModuleOutputPolicyCode.PACKAGE_TYPE_INVALID,
                "Accepted package metadata contains an unsupported top-level type value.",
            )
        }
    }
}
