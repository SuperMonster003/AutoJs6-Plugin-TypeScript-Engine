package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Canonical authority for the frozen R2 diagnostics-document schema revision 1. */
internal object R2DiagnosticsSchemaV1 {

    const val SCHEMA = TypeScriptR2Contract.DIAGNOSTICS_SCHEMA
    const val REVISION = TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION
    const val QUOTA_REVISION = TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION
    const val POSITION_ENCODING = TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING
    const val LINE_BASE = TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE
    const val COLUMN_BASE = TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE

    fun requireSupportedIdentity(
        schema: String,
        revision: Int,
    ) {
        require(schema == SCHEMA && revision == REVISION) {
            "Diagnostics schema identity is not supported."
        }
    }

    fun requireCounts(
        diagnosticCount: Int,
        relatedInformationCount: Int,
    ) {
        require(diagnosticCount in 0..TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT) {
            "Diagnostics count is outside the frozen schema bound."
        }
        require(
            relatedInformationCount in
                0..TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL,
        ) {
            "Related-information count is outside the frozen schema bound."
        }
    }

    fun requireDocumentBytes(bytes: ByteArray) {
        require(
            bytes.size.toLong() <= TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES,
        ) {
            "Diagnostics document exceeds the frozen UTF-8 byte limit."
        }
    }

    fun requireCategory(category: Int) {
        require(
            category in
                TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_WARNING..
                TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
        ) {
            "Diagnostic category is not a frozen TypeScript DiagnosticCategory value."
        }
    }

    /**
     * Maps an accepted snapshot path to its project-scoped canonical source URI.
     *
     * The `p-<sha256(projectId UTF-8)>` namespace and per-segment percent encoding intentionally
     * match the existing generated-module original-source identity for the `/sources/` domain.
     * Declaration
     * and controlled package-target paths use the same collision-resistant namespace, so every
     * file-associated primary or related diagnostic has one stable URI independent of entry-file
     * selection and caller aliases.
     */
    fun sourceUri(
        projectId: String,
        sourcePath: String,
    ): String {
        require(
            projectId.isNotEmpty() &&
                projectId.length <= TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS,
        ) {
            "Diagnostic project ID is empty or exceeds its character limit."
        }
        val relativePath = requireCanonicalSourcePath(sourcePath)
        val projectBytes = R2Utf8.encode(projectId, "Diagnostic project ID")
        val namespace = try {
            R2NodeModuleOutputPolicy.PROJECT_NAMESPACE_PREFIX + R2Digests.sha256(projectBytes)
        } finally {
            projectBytes.fill(0)
        }
        val encodedPath = relativePath.split('/').joinToString("/") { segment ->
            percentEncodeSegment(segment)
        }
        val uri = "autojs://source/$namespace/$encodedPath"
        require(
            uri.length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS &&
                uri.all { character -> character.code in PRINTABLE_ASCII },
        ) {
            "Diagnostic source URI exceeds its bounded ASCII representation."
        }
        return uri
    }

    private fun requireCanonicalSourcePath(sourcePath: String): String {
        require(
            sourcePath.startsWith('/') &&
                (
                    sourcePath.startsWith("/sources/") ||
                        sourcePath.startsWith("/declarations/") ||
                        sourcePath.startsWith("/packages/")
                    ) &&
                sourcePath.length - 1 <=
                TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES &&
                '\\' !in sourcePath &&
                sourcePath.all { character -> character.code in PRINTABLE_ASCII },
        ) {
            "Diagnostic source path is outside the frozen snapshot URI domains."
        }
        val relativePath = sourcePath.removePrefix("/")
        val segments = relativePath.split('/')
        require(
            segments.size <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH &&
                segments.all { segment ->
                    segment.isNotEmpty() && segment != "." && segment != ".."
                },
        ) {
            "Diagnostic source path is not canonical."
        }
        return relativePath
    }

    private fun percentEncodeSegment(segment: String): String {
        val bytes = R2Utf8.encode(segment, "Diagnostic URI segment")
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

    private val PRINTABLE_ASCII = 0x20..0x7e
    private const val HEX_DIGITS = "0123456789ABCDEF"
}
