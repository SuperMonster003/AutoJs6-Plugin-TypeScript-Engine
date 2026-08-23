package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import java.util.Collections

internal enum class R2CompilerEscapeSourcePathRejection(
    val contractValue: String,
) {
    PACKAGE_METADATA("package-metadata"),
    PACKAGE_DEPENDENCY_TREE("package-dependency-tree"),
    DEFERRED_SOURCE_KIND("deferred-source-kind"),
}

/**
 * R2-M2-C4 authority for compiler escape surfaces that are independent of the C1 tsconfig
 * grammar. Keeping this authority separate gives host/Worker escape closure its own revisioned
 * identity while binding it to the current controlled-config policy fingerprint.
 */
internal object R2CompilerEscapePolicy {

    const val SCHEMA = "autojs6.typescript.compiler-escape-policy"
    const val REVISION = 2
    const val INLINE_COMPILER_OPTIONS_JSON = "{}"
    const val FIXED_WORKER_IMPORT_SCRIPT = "typescript.js"
    const val BINDER_METADATA_MODE = "primitive-additive-no-executable-authority"
    const val HOST_IO_MODE = "snapshot-only-no-ts-sys"

    val escapeForbiddenCompilerOptions: Set<String> =
        R2ControlledTsConfigPolicy.escapeForbiddenCompilerOptions

    val customTransformerOptionNames: Set<String> =
        R2ControlledTsConfigPolicy.customTransformerCompilerOptions

    val forbiddenPackageMetadataKeys: Set<String> = immutableSetOf(
        "scripts",
    )

    val forbiddenPackageFileNames: Set<String> = immutableSetOf(
        "package.json",
    )

    val forbiddenPackagePathSegments: Set<String> = immutableSetOf(
        "node_modules",
    )

    val deferredNodeSourceSuffixes: Set<String> = immutableSetOf(
        ".cts",
        ".mts",
    )

    val forbiddenWorkerNetworkGlobals: Set<String> = immutableSetOf(
        "EventSource",
        "RTCPeerConnection",
        "SharedWorker",
        "WebSocket",
        "WebTransport",
        "XMLHttpRequest",
        "fetch",
        "navigator.sendBeacon",
    )

    val canonicalInput: String = buildString {
        append("autojs6.typescript.compiler-escape-policy.v2")
        append("|revision=")
        append(REVISION)
        append("|configPolicyFingerprint=")
        append(R2ControlledTsConfigPolicy.fingerprint)
        append("|inlineCompilerOptionsJson=")
        append(INLINE_COMPILER_OPTIONS_JSON)
        appendValues("escapeForbidden", escapeForbiddenCompilerOptions)
        appendValues("customTransformers", customTransformerOptionNames)
        appendValues("packageMetadataKeys", forbiddenPackageMetadataKeys)
        appendValues("packageFiles", forbiddenPackageFileNames)
        appendValues("packageSegments", forbiddenPackagePathSegments)
        appendValues("deferredNodeSourceSuffixes", deferredNodeSourceSuffixes)
        append("|fixedWorkerImportScript=")
        append(FIXED_WORKER_IMPORT_SCRIPT)
        appendValues("networkGlobals", forbiddenWorkerNetworkGlobals)
        append("|binderMetadataMode=")
        append(BINDER_METADATA_MODE)
        append("|hostIoMode=")
        append(HOST_IO_MODE)
    }

    val fingerprint: String = R2Digests.sha256(
        canonicalInput.toByteArray(StandardCharsets.US_ASCII),
    )

    init {
        require(REVISION == 2)
        require(escapeForbiddenCompilerOptions.size == 10)
        require(canonicalInput.all { character -> character.code in 0x20..0x7e })
        R2Digests.requireLowerSha256(fingerprint, "Compiler escape policy fingerprint")
    }

    fun sourcePathRejection(path: String): R2CompilerEscapeSourcePathRejection? {
        val foldedSegments = path
            .split('/')
            .filter(String::isNotEmpty)
            .map { segment -> segment.asciiCaseFold() }
        val fileName = foldedSegments.lastOrNull().orEmpty()
        return when {
            fileName in forbiddenPackageFileNames ->
                R2CompilerEscapeSourcePathRejection.PACKAGE_METADATA
            foldedSegments.any { segment -> segment in forbiddenPackagePathSegments } ->
                R2CompilerEscapeSourcePathRejection.PACKAGE_DEPENDENCY_TREE
            deferredNodeSourceSuffixes.any { suffix -> fileName.endsWith(suffix) } ->
                R2CompilerEscapeSourcePathRejection.DEFERRED_SOURCE_KIND
            else -> null
        }
    }

    private fun StringBuilder.appendValues(
        label: String,
        values: Set<String>,
    ) {
        append('|')
        append(label)
        append('=')
        append(values.sorted().joinToString(","))
    }

    private fun String.asciiCaseFold(): String = buildString(length) {
        for (character in this@asciiCaseFold) {
            append(
                if (character in 'A'..'Z') {
                    (character.code + ASCII_CASE_OFFSET).toChar()
                } else {
                    character
                },
            )
        }
    }

    private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
}

private fun immutableSetOf(vararg values: String): Set<String> =
    Collections.unmodifiableSet(linkedSetOf(*values))
