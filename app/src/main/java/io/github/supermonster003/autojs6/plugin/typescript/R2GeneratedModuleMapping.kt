package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

internal enum class R2GeneratedModuleMappingFailureCode(
    val contractValue: String,
) {
    INVALID_PROFILE_AUTHORITY("GENERATED_MAPPING_INVALID_PROFILE_AUTHORITY"),
    INVALID_PROJECT_ID("GENERATED_MAPPING_INVALID_PROJECT_ID"),
    INVALID_SOURCE_PATH("GENERATED_MAPPING_INVALID_SOURCE_PATH"),
    UNSUPPORTED_SOURCE_KIND("GENERATED_MAPPING_UNSUPPORTED_SOURCE_KIND"),
    PACKAGE_AUTHORITY_MISMATCH("GENERATED_MAPPING_PACKAGE_AUTHORITY_MISMATCH"),
    MODULE_FORMAT_MISMATCH("GENERATED_MAPPING_MODULE_FORMAT_MISMATCH"),
    URI_LIMIT_EXCEEDED("GENERATED_MAPPING_URI_LIMIT_EXCEEDED"),
    OUTPUT_PATH_LIMIT_EXCEEDED("GENERATED_MAPPING_OUTPUT_PATH_LIMIT_EXCEEDED"),
    CLAIM_MISMATCH("GENERATED_MAPPING_CLAIM_MISMATCH"),
    SOURCE_COLLISION("GENERATED_MAPPING_SOURCE_COLLISION"),
    GENERATED_PATH_COLLISION("GENERATED_MAPPING_GENERATED_PATH_COLLISION"),
    GENERATED_URI_COLLISION("GENERATED_MAPPING_GENERATED_URI_COLLISION"),
    ORIGINAL_SOURCE_URI_COLLISION("GENERATED_MAPPING_ORIGINAL_SOURCE_URI_COLLISION"),
    PROJECT_NAMESPACE_COLLISION("GENERATED_MAPPING_PROJECT_NAMESPACE_COLLISION"),
    MIXED_PROJECT_INVENTORY("GENERATED_MAPPING_MIXED_PROJECT_INVENTORY"),
}

internal class R2GeneratedModuleMappingException(
    val policyCode: R2GeneratedModuleMappingFailureCode,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** Accepted package-format identity used only for Node revision-2 `.ts` mapping. */
internal data class R2GeneratedPackageFormatAuthority(
    val typeState: R2PackageTypeState,
    val packageMetadataInventoryFingerprint: String?,
    val boundaryPath: String?,
) {

    init {
        when (typeState) {
            R2PackageTypeState.NO_BOUNDARY -> if (
                packageMetadataInventoryFingerprint != null || boundaryPath != null
            ) throw R2GeneratedModuleMappingException(
                R2GeneratedModuleMappingFailureCode.PACKAGE_AUTHORITY_MISMATCH,
                "A no-boundary package authority cannot carry boundary identity.",
            )
            else -> {
                if (packageMetadataInventoryFingerprint == null || boundaryPath != "/") {
                    throw R2GeneratedModuleMappingException(
                        R2GeneratedModuleMappingFailureCode.PACKAGE_AUTHORITY_MISMATCH,
                        "An accepted package boundary must carry exact root identity.",
                    )
                }
                try {
                    R2Digests.requireLowerSha256(
                        packageMetadataInventoryFingerprint,
                        "Generated mapping package metadata inventory fingerprint",
                    )
                } catch (error: IllegalArgumentException) {
                    throw R2GeneratedModuleMappingException(
                        R2GeneratedModuleMappingFailureCode.PACKAGE_AUTHORITY_MISMATCH,
                        "Accepted package boundary fingerprint is malformed.",
                        error,
                    )
                }
            }
        }
    }
}

internal data class R2GeneratedModuleMappingRequest(
    val projectId: String,
    val profileAuthority: R2CompilationProfileAuthority,
    val sourcePath: String,
    val impliedModuleFormat: R2ModuleFormat,
    val packageFormatAuthority: R2GeneratedPackageFormatAuthority? = null,
)

internal data class R2GeneratedModuleMapping(
    val sourcePath: String,
    val projectNamespace: String,
    val originalSourceUri: String,
    val moduleFormat: R2ModuleFormat,
    val outputExtension: String,
    val generatedPath: String,
    val sourceMapPath: String,
    val generatedUri: String,
    val canonicalEvidence: String,
    val fingerprint: String,
)

/**
 * Pure R2-M2-P2-2 source/module/output mapping authority.
 *
 * This helper deliberately does not participate in snapshot admission, resolution, Worker emit,
 * result parsing, or archive staging. Those integrations remain fail-closed behind later P2 slices.
 */
internal object R2GeneratedModuleMappingPolicy {

    const val SCHEMA = "autojs6.typescript.generated-module-mapping"
    const val SCHEMA_REVISION = 1
    const val POLICY_REVISION = 1

    fun map(request: R2GeneratedModuleMappingRequest): R2GeneratedModuleMapping {
        val projectNamespace = projectNamespace(request.projectId)
        val relativeSourcePath = validateSourcePath(request.sourcePath)
        val sourceExtension = sourceExtension(request.sourcePath)
        val outputExtension = requireMatrixAuthority(
            profileAuthority = request.profileAuthority,
            sourceExtension = sourceExtension,
            impliedModuleFormat = request.impliedModuleFormat,
            packageAuthority = request.packageFormatAuthority,
        )
        val generatedRelativePath = relativeSourcePath.removeSuffix(sourceExtension) +
            outputExtension
        val generatedPath = "${R2NodeModuleOutputPolicy.OUTPUT_ROOT}/$generatedRelativePath"
        val sourceMapPath = "$generatedPath.map"
        validateGeneratedPath(generatedPath)
        validateGeneratedPath(sourceMapPath)
        val originalSourceUri = buildOriginalSourceUri(
            projectNamespace = projectNamespace,
            relativeSourcePath = relativeSourcePath,
        )
        val generatedUri = buildUri(
            authority = R2NodeModuleOutputPolicy.GENERATED_URI_AUTHORITY,
            namespace = projectNamespace,
            rootedRelativePath = "outputs/$generatedRelativePath",
        )
        val packageState = request.packageFormatAuthority?.typeState?.contractValue ?: "none"
        val packageFingerprint =
            request.packageFormatAuthority?.packageMetadataInventoryFingerprint ?: "none"
        val packageBoundary = request.packageFormatAuthority?.boundaryPath ?: "none"
        val canonicalEvidence = buildString {
            append("schema=")
            append(SCHEMA)
            append("|schemaRevision=")
            append(SCHEMA_REVISION)
            append("|mappingPolicyRevision=")
            append(POLICY_REVISION)
            append("|moduleOutputPolicyRevision=")
            append(R2NodeModuleOutputPolicy.POLICY_REVISION)
            append("|moduleOutputPolicyFingerprint=")
            append(R2NodeModuleOutputPolicy.fingerprint)
            append("|profile=")
            append(percentEncodeToken(request.profileAuthority.profile.contractValue))
            append("|profileSchemaRevision=")
            append(request.profileAuthority.profileSchemaRevision)
            append("|profileRevision=")
            append(request.profileAuthority.profileRevision)
            append("|configSchemaRevision=")
            append(request.profileAuthority.configSchemaRevision)
            append("|configKind=")
            append(percentEncodeToken(request.profileAuthority.configKind))
            append("|effectiveOptionsFingerprint=")
            append(request.profileAuthority.effectiveOptionsFingerprint)
            append("|runtimeCapabilitiesFingerprint=")
            append(request.profileAuthority.runtimeCapabilitiesFingerprint)
            append("|projectNamespace=")
            append(percentEncodeToken(projectNamespace))
            append("|sourcePath=")
            append(percentEncodeToken(request.sourcePath))
            append("|originalSourceUri=")
            append(percentEncodeToken(originalSourceUri))
            append("|moduleFormat=")
            append(percentEncodeToken(request.impliedModuleFormat.contractValue))
            append("|packageTypeState=")
            append(percentEncodeToken(packageState))
            append("|packageMetadataInventoryFingerprint=")
            append(percentEncodeToken(packageFingerprint))
            append("|packageBoundary=")
            append(percentEncodeToken(packageBoundary))
            append("|outputExtension=")
            append(percentEncodeToken(outputExtension))
            append("|generatedPath=")
            append(percentEncodeToken(generatedPath))
            append("|sourceMapPath=")
            append(percentEncodeToken(sourceMapPath))
            append("|generatedUri=")
            append(percentEncodeToken(generatedUri))
        }
        check(canonicalEvidence.all { character -> character.code in PRINTABLE_ASCII })
        return R2GeneratedModuleMapping(
            sourcePath = request.sourcePath,
            projectNamespace = projectNamespace,
            originalSourceUri = originalSourceUri,
            moduleFormat = request.impliedModuleFormat,
            outputExtension = outputExtension,
            generatedPath = generatedPath,
            sourceMapPath = sourceMapPath,
            generatedUri = generatedUri,
            canonicalEvidence = canonicalEvidence,
            fingerprint = R2Digests.sha256(
                canonicalEvidence.toByteArray(StandardCharsets.US_ASCII),
            ),
        )
    }

    /**
     * Returns the exact original-source URI without requiring module-format authority.
     *
     * Request-manifest validation occurs before package-format derivation, so it must be able to
     * bind the project/source identity without inventing an implied module format. This function
     * deliberately shares the same project-ID, source-path, encoding and URI budgets as [map].
     */
    fun originalSourceUri(projectId: String, sourcePath: String): String =
        buildOriginalSourceUri(
            projectNamespace = projectNamespace(projectId),
            relativeSourcePath = validateSourcePath(sourcePath),
        )

    fun mapInventory(
        requests: List<R2GeneratedModuleMappingRequest>,
    ): List<R2GeneratedModuleMapping> {
        val sourcePaths = HashSet<String>()
        val foldedSourcePaths = HashSet<String>()
        val generatedPaths = HashSet<String>()
        val foldedGeneratedPaths = HashSet<String>()
        val generatedUris = HashSet<String>()
        val foldedGeneratedUris = HashSet<String>()
        val originalSourceUris = HashSet<String>()
        val foldedOriginalSourceUris = HashSet<String>()
        val namespaceOwners = HashMap<String, String>()
        var inventoryProjectId: String? = null
        val mappings = requests.map { request ->
            val mapping = map(request)
            val namespaceOwner = namespaceOwners.putIfAbsent(
                mapping.projectNamespace,
                request.projectId,
            )
            if (namespaceOwner != null && namespaceOwner != request.projectId) {
                reject(
                    R2GeneratedModuleMappingFailureCode.PROJECT_NAMESPACE_COLLISION,
                    "Different project IDs produced the same generated namespace.",
                )
            }
            val firstProjectId = inventoryProjectId
            if (firstProjectId == null) {
                inventoryProjectId = request.projectId
            } else if (firstProjectId != request.projectId) {
                reject(
                    R2GeneratedModuleMappingFailureCode.MIXED_PROJECT_INVENTORY,
                    "A generated mapping inventory cannot combine different projects.",
                )
            }
            if (
                !sourcePaths.add(mapping.sourcePath) ||
                !foldedSourcePaths.add(mapping.sourcePath.asciiCaseFold())
            ) {
                reject(
                    R2GeneratedModuleMappingFailureCode.SOURCE_COLLISION,
                    "Generated mapping inventory contains a source-path collision.",
                )
            }
            if (
                !originalSourceUris.add(mapping.originalSourceUri) ||
                !foldedOriginalSourceUris.add(mapping.originalSourceUri.asciiCaseFold())
            ) {
                reject(
                    R2GeneratedModuleMappingFailureCode.ORIGINAL_SOURCE_URI_COLLISION,
                    "Generated mapping inventory contains an original-source URI collision.",
                )
            }
            if (
                !generatedPaths.add(mapping.generatedPath) ||
                !foldedGeneratedPaths.add(mapping.generatedPath.asciiCaseFold())
            ) {
                reject(
                    R2GeneratedModuleMappingFailureCode.GENERATED_PATH_COLLISION,
                    "Generated mapping inventory contains an output-path collision.",
                )
            }
            if (
                !generatedUris.add(mapping.generatedUri) ||
                !foldedGeneratedUris.add(mapping.generatedUri.asciiCaseFold())
            ) {
                reject(
                    R2GeneratedModuleMappingFailureCode.GENERATED_URI_COLLISION,
                    "Generated mapping inventory contains a generated-URI collision.",
                )
            }
            mapping
        }
        return Collections.unmodifiableList(
            ArrayList(mappings.sortedBy(R2GeneratedModuleMapping::sourcePath)),
        )
    }

    fun requireExact(
        request: R2GeneratedModuleMappingRequest,
        claimed: R2GeneratedModuleMapping,
    ): R2GeneratedModuleMapping {
        val expected = map(request)
        if (claimed != expected) {
            reject(
                R2GeneratedModuleMappingFailureCode.CLAIM_MISMATCH,
                "Generated module mapping claim does not match the canonical authority.",
            )
        }
        return expected
    }

    private fun projectNamespace(projectId: String): String {
        if (projectId.isEmpty() || projectId.length > MAXIMUM_PROJECT_ID_UTF16_CHARS) {
            reject(
                R2GeneratedModuleMappingFailureCode.INVALID_PROJECT_ID,
                "Generated mapping project ID is empty or exceeds its UTF-16 limit.",
            )
        }
        val bytes = strictUtf8(projectId, "Generated mapping project ID")
        return try {
            R2NodeModuleOutputPolicy.PROJECT_NAMESPACE_PREFIX + R2Digests.sha256(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun validateSourcePath(sourcePath: String): String {
        val prefix = "${R2NodeModuleOutputPolicy.SOURCE_ROOT}/"
        if (
            !sourcePath.startsWith(prefix) ||
            !R2UnicodePathPolicy.isCanonicalAbsolutePath(
                path = sourcePath,
                maximumRelativeUtf8Bytes =
                    R2NodeModuleOutputPolicy.MAXIMUM_RELATIVE_PATH_CHARS.toLong(),
                maximumDepth = R2NodeModuleOutputPolicy.MAXIMUM_PATH_DEPTH,
            )
        ) {
            reject(
                R2GeneratedModuleMappingFailureCode.INVALID_SOURCE_PATH,
                "Generated mapping source path is not a canonical UTF-8 /sources path.",
            )
        }
        val relative = sourcePath.removePrefix(prefix)
        val segments = relative.split('/')
        if (
            relative.isEmpty() ||
            segments.size + 1 > R2NodeModuleOutputPolicy.MAXIMUM_PATH_DEPTH ||
            segments.any { segment ->
                segment.isEmpty() || segment == "." || segment == ".."
            }
        ) {
            reject(
                R2GeneratedModuleMappingFailureCode.INVALID_SOURCE_PATH,
                "Generated mapping source path has a non-canonical segment or exceeds a limit.",
            )
        }
        return relative
    }

    private fun sourceExtension(sourcePath: String): String = when {
        sourcePath.endsWith(".d.ts") ||
            sourcePath.endsWith(".d.mts") ||
            sourcePath.endsWith(".d.cts") -> reject(
                R2GeneratedModuleMappingFailureCode.UNSUPPORTED_SOURCE_KIND,
                "Declaration files cannot establish generated module mappings.",
            )
        sourcePath.endsWith(".mts") -> ".mts"
        sourcePath.endsWith(".cts") -> ".cts"
        sourcePath.endsWith(".ts") -> ".ts"
        else -> reject(
            R2GeneratedModuleMappingFailureCode.UNSUPPORTED_SOURCE_KIND,
            "Generated mapping source kind is not supported.",
        )
    }

    private fun validateGeneratedPath(path: String) {
        if (
            !path.startsWith("${R2NodeModuleOutputPolicy.OUTPUT_ROOT}/") ||
            !R2UnicodePathPolicy.isCanonicalAbsolutePath(
                path = path,
                maximumRelativeUtf8Bytes =
                    R2NodeModuleOutputPolicy.MAXIMUM_RELATIVE_PATH_CHARS.toLong(),
                maximumDepth = R2NodeModuleOutputPolicy.MAXIMUM_PATH_DEPTH,
            )
        ) {
            reject(
                R2GeneratedModuleMappingFailureCode.OUTPUT_PATH_LIMIT_EXCEEDED,
                "Generated output or source-map path exceeds an archive path limit.",
            )
        }
    }

    private fun requireMatrixAuthority(
        profileAuthority: R2CompilationProfileAuthority,
        sourceExtension: String,
        impliedModuleFormat: R2ModuleFormat,
        packageAuthority: R2GeneratedPackageFormatAuthority?,
    ): String = when (profileAuthority.profile) {
        R2ProjectProfile.PROVISIONAL_RHINO -> {
            if (
                profileAuthority != R2CompilationProfileRegistry.PROVISIONAL_RHINO ||
                sourceExtension != ".ts"
            ) {
                reject(
                    R2GeneratedModuleMappingFailureCode.INVALID_PROFILE_AUTHORITY,
                    "Rhino generated mapping requires revision 1 and a .ts source.",
                )
            }
            if (packageAuthority != null) rejectPackageAuthority()
            if (impliedModuleFormat != R2ModuleFormat.COMMONJS) rejectModuleFormat()
            ".js"
        }
        R2ProjectProfile.PROVISIONAL_NODE_NEXT -> {
            if (
                profileAuthority !=
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
            ) {
                reject(
                    R2GeneratedModuleMappingFailureCode.INVALID_PROFILE_AUTHORITY,
                    "Node generated mapping requires the revision-2 profile authority.",
                )
            }
            when (sourceExtension) {
                ".mts" -> {
                    if (packageAuthority != null) rejectPackageAuthority()
                    if (impliedModuleFormat != R2ModuleFormat.ESM) rejectModuleFormat()
                    ".mjs"
                }
                ".cts" -> {
                    if (packageAuthority != null) rejectPackageAuthority()
                    if (impliedModuleFormat != R2ModuleFormat.COMMONJS) rejectModuleFormat()
                    ".cjs"
                }
                ".ts" -> {
                    val acceptedPackage = packageAuthority ?: reject(
                        R2GeneratedModuleMappingFailureCode.PACKAGE_AUTHORITY_MISMATCH,
                        "Node .ts mapping requires an accepted package-format authority.",
                    )
                    val expectedFormat = when (acceptedPackage.typeState) {
                        R2PackageTypeState.MODULE -> R2ModuleFormat.ESM
                        R2PackageTypeState.NO_BOUNDARY,
                        R2PackageTypeState.TYPE_ABSENT,
                        R2PackageTypeState.COMMONJS,
                        -> R2ModuleFormat.COMMONJS
                    }
                    if (impliedModuleFormat != expectedFormat) rejectModuleFormat()
                    ".js"
                }
                else -> reject(
                    R2GeneratedModuleMappingFailureCode.UNSUPPORTED_SOURCE_KIND,
                    "Node generated mapping source kind is not supported.",
                )
            }
        }
    }

    private fun buildUri(
        authority: String,
        namespace: String,
        rootedRelativePath: String,
    ): String {
        val encodedPath = rootedRelativePath.split('/').joinToString("/") { segment ->
            percentEncodeSegment(segment)
        }
        val uri = "${R2NodeModuleOutputPolicy.SOURCE_URI_SCHEME}://$authority/" +
            "$namespace/$encodedPath"
        if (
            uri.length > R2NodeModuleOutputPolicy.MAXIMUM_URI_ASCII_BYTES ||
            uri.any { character -> character.code !in PRINTABLE_ASCII }
        ) {
            reject(
                R2GeneratedModuleMappingFailureCode.URI_LIMIT_EXCEEDED,
                "Generated mapping URI exceeds its bounded ASCII representation.",
            )
        }
        return uri
    }

    private fun buildOriginalSourceUri(
        projectNamespace: String,
        relativeSourcePath: String,
    ): String = buildUri(
        authority = R2NodeModuleOutputPolicy.SOURCE_URI_AUTHORITY,
        namespace = projectNamespace,
        rootedRelativePath = "sources/$relativeSourcePath",
    )

    private fun percentEncodeSegment(segment: String): String {
        val bytes = strictUtf8(segment, "Generated mapping URI segment")
        return try {
            buildString(bytes.size) {
                bytes.forEach { byte ->
                    val value = byte.toInt() and 0xff
                    if (
                        value in 'a'.code..'z'.code ||
                        value in 'A'.code..'Z'.code ||
                        value in '0'.code..'9'.code ||
                        value == '-'.code ||
                        value == '.'.code ||
                        value == '_'.code ||
                        value == '~'.code
                    ) {
                        append(value.toChar())
                    } else {
                        append('%')
                        append(HEX_DIGITS[value ushr 4])
                        append(HEX_DIGITS[value and 0x0f])
                    }
                }
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun percentEncodeToken(value: String): String = percentEncodeSegment(value)

    private fun strictUtf8(value: String, label: String): ByteArray {
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            val encoded = encoder.encode(CharBuffer.wrap(value))
            ByteArray(encoded.remaining()).also(encoded::get)
        } catch (error: CharacterCodingException) {
            reject(
                R2GeneratedModuleMappingFailureCode.INVALID_PROJECT_ID,
                "$label is not valid Unicode scalar text.",
                error,
            )
        }
    }

    private fun rejectPackageAuthority(): Nothing = reject(
        R2GeneratedModuleMappingFailureCode.PACKAGE_AUTHORITY_MISMATCH,
        "This generated mapping kind cannot carry package-format authority.",
    )

    private fun rejectModuleFormat(): Nothing = reject(
        R2GeneratedModuleMappingFailureCode.MODULE_FORMAT_MISMATCH,
        "Generated mapping implied module format does not match the policy matrix.",
    )

    private fun String.asciiCaseFold(): String = buildString(length) {
        this@asciiCaseFold.forEach { character ->
            append(
                if (character in 'A'..'Z') {
                    (character.code + ASCII_CASE_OFFSET).toChar()
                } else {
                    character
                },
            )
        }
    }

    private fun reject(
        code: R2GeneratedModuleMappingFailureCode,
        message: String,
        cause: Throwable? = null,
    ): Nothing = throw R2GeneratedModuleMappingException(code, message, cause)

    private const val MAXIMUM_PROJECT_ID_UTF16_CHARS = 256
    private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
    private const val HEX_DIGITS = "0123456789ABCDEF"
    private val PRINTABLE_ASCII = 0x20..0x7e
}
