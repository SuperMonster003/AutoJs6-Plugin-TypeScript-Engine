package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Shared text and shape rules for caller-owned UTF-8 paths.
 *
 * Paths remain logical archive/VFS identities: this policy does not resolve them through the host
 * filesystem. Valid Unicode scalar text is accepted, while controls, platform separators,
 * absolute/drive-prefixed aliases and traversal segments remain fail-closed.
 */
internal object R2UnicodePathPolicy {

    fun isCanonicalRelativePath(
        path: String,
        maximumUtf8Bytes: Long =
            TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES.toLong(),
        maximumDepth: Int = TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH,
    ): Boolean {
        val byteCount = encodedByteCountOrNull(path) ?: return false
        if (
            path.isEmpty() ||
            byteCount > maximumUtf8Bytes ||
            path.startsWith('/') ||
            path.hasAsciiDrivePrefix() ||
            '\\' in path ||
            containsControlCharacter(path)
        ) {
            return false
        }
        val segments = path.split('/')
        return segments.size <= maximumDepth &&
            segments.all { segment ->
                segment.isNotEmpty() && segment != "." && segment != ".."
            }
    }

    fun isCanonicalAbsolutePath(
        path: String,
        maximumUtf16Chars: Int = TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS,
        maximumRelativeUtf8Bytes: Long =
            TypeScriptCompilerContract.MAX_ARCHIVE_ENTRY_NAME_BYTES.toLong(),
        maximumDepth: Int = TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH,
    ): Boolean =
        path.length <= maximumUtf16Chars &&
            path.startsWith('/') &&
            isCanonicalRelativePath(
                path = path.removePrefix("/"),
                maximumUtf8Bytes = maximumRelativeUtf8Bytes,
                maximumDepth = maximumDepth,
            )

    fun isSafeSegment(segment: String): Boolean =
        segment.isNotEmpty() &&
            segment != "." &&
            segment != ".." &&
            '/' !in segment &&
            '\\' !in segment &&
            !containsControlCharacter(segment) &&
            encodedByteCountOrNull(segment) != null

    fun encodedByteCount(
        text: String,
        label: String,
    ): Long = R2Utf8.encodedByteCount(text, label)

    fun asciiCaseFold(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                if (character in 'A'..'Z') {
                    (character.code + ASCII_CASE_OFFSET).toChar()
                } else {
                    character
                },
            )
        }
    }

    private fun encodedByteCountOrNull(text: String): Long? = try {
        encodedByteCount(text, "UTF-8 path")
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun containsControlCharacter(text: String): Boolean =
        text.any { character -> Character.isISOControl(character.code) }

    private fun String.hasAsciiDrivePrefix(): Boolean =
        length >= 2 && first().isAsciiLetter() && this[1] == ':'

    private fun Char.isAsciiLetter(): Boolean = this in 'A'..'Z' || this in 'a'..'z'

    private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
}
