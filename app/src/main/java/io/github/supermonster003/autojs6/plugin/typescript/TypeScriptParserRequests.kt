package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.parser.TypeScriptParserContract as Contract

/** One validated parse request of the TypeScript parser contract v1. */
internal data class ParserRequest(
    val requestId: String,
    val fileName: String,
    val sourceText: String,
    val timeoutMs: Long,
)

internal sealed interface ParserRequestCheck {
    data class Valid(val request: ParserRequest) : ParserRequestCheck
    data class Rejected(val code: String, val message: String) : ParserRequestCheck
}

/**
 * Validation of parse request Bundles, written over a key lookup so that it runs on the JVM. Unknown
 * keys are ignored, as the contract allows later revisions to add optional ones.
 */
internal object TypeScriptParserRequests {

    fun check(value: (String) -> Any?): ParserRequestCheck {
        val version = value(Contract.KEY_CONTRACT_VERSION)
        if (version != Contract.VERSION) {
            return rejected(Contract.ERROR_CONTRACT_MISMATCH, "Parser contract version ${Contract.VERSION} is required.")
        }
        val requestId = value(Contract.KEY_REQUEST_ID) as? String
        if (requestId == null || requestId.isEmpty() || requestId.length > Contract.MAX_REQUEST_ID_CHARS) {
            return invalid("requestId must be 1 to ${Contract.MAX_REQUEST_ID_CHARS} characters.")
        }
        val fileName = value(Contract.KEY_FILE_NAME) as? String
        if (fileName == null || fileName.isEmpty() || fileName.length > Contract.MAX_FILE_NAME_CHARS ||
            fileName.contains('/') || fileName.contains('\\') || fileName.any { it.code < 0x20 }
        ) {
            return invalid("fileName must be a plain file name of at most ${Contract.MAX_FILE_NAME_CHARS} characters.")
        }
        if (Contract.SUPPORTED_EXTENSIONS.none { fileName.lowercase().endsWith(it) }) {
            return invalid("fileName must end with one of ${Contract.SUPPORTED_EXTENSIONS.joinToString()}.")
        }
        val sourceText = value(Contract.KEY_SOURCE_TEXT) as? String
            ?: return invalid("sourceText is required.")
        if (sourceText.length > Contract.MAX_SOURCE_CHARS) {
            return invalid("sourceText exceeds ${Contract.MAX_SOURCE_CHARS} characters.")
        }
        val timeoutMs = when (val timeout = value(Contract.KEY_TIMEOUT_MS)) {
            null -> Contract.DEFAULT_TIMEOUT_MS
            is Long -> timeout
            is Int -> timeout.toLong()
            else -> return invalid("timeoutMs must be a number.")
        }
        if (timeoutMs !in Contract.MIN_TIMEOUT_MS..Contract.MAX_TIMEOUT_MS) {
            return invalid("timeoutMs must be ${Contract.MIN_TIMEOUT_MS} to ${Contract.MAX_TIMEOUT_MS}.")
        }
        return ParserRequestCheck.Valid(ParserRequest(requestId, fileName, sourceText, timeoutMs))
    }

    /**
     * Splits [text] into frames of at most [frameChars] characters, never between the two halves of a
     * surrogate pair (each frame crosses the port as its own string); an empty text is one empty frame.
     */
    fun frames(text: String, frameChars: Int): List<String> {
        require(frameChars > 1)
        if (text.isEmpty()) return listOf("")
        val frames = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(text.length, start + frameChars)
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end -= 1
            frames += text.substring(start, end)
            start = end
        }
        return frames
    }

    private fun invalid(message: String) = rejected(Contract.ERROR_INVALID_REQUEST, message)

    private fun rejected(code: String, message: String) = ParserRequestCheck.Rejected(code, message)
}
