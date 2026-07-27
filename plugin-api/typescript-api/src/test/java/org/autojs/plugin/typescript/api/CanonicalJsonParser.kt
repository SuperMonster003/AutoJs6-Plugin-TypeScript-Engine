package org.autojs.plugin.typescript.api

internal object CanonicalJsonParser {

    fun parse(source: String): Any? = Parser(source).parse()

    private class Parser(
        private val source: String,
    ) {

        private var offset = 0

        fun parse(): Any? {
            val value = parseValue()
            skipWhitespace()
            require(offset == source.length) {
                "Unexpected JSON content at offset $offset."
            }
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            require(offset < source.length) { "Unexpected end of JSON." }
            return when (source[offset]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected JSON token at offset $offset.")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWhitespace()
            if (consume('}')) return emptyMap()

            val result = linkedMapOf<String, Any?>()
            while (true) {
                skipWhitespace()
                val key = parseString()
                require(key !in result) { "Duplicate JSON key '$key'." }
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (consume('}')) return result
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWhitespace()
            if (consume(']')) return emptyList()

            val result = mutableListOf<Any?>()
            while (true) {
                result += parseValue()
                skipWhitespace()
                if (consume(']')) return result
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (offset < source.length) {
                when (val character = source[offset++]) {
                    '"' -> return result.toString()
                    '\\' -> {
                        require(offset < source.length) { "Truncated JSON escape." }
                        when (val escaped = source[offset++]) {
                            '"', '\\', '/' -> result.append(escaped)
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000c')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> {
                                require(offset <= source.length - 4) {
                                    "Truncated JSON unicode escape."
                                }
                                val codePoint = source
                                    .substring(offset, offset + 4)
                                    .toInt(16)
                                result.append(codePoint.toChar())
                                offset += 4
                            }
                            else -> error("Invalid JSON escape '\\$escaped'.")
                        }
                    }
                    else -> {
                        require(character.code >= 0x20) {
                            "Unescaped control character in JSON string."
                        }
                        result.append(character)
                    }
                }
            }
            error("Unterminated JSON string.")
        }

        private fun parseNumber(): Number {
            val start = offset
            consume('-')
            if (consume('0')) {
                require(offset >= source.length || source[offset] !in '0'..'9') {
                    "Leading zero in JSON number."
                }
            } else {
                consumeDigits()
            }
            if (consume('.')) consumeDigits()
            if (consume('e') || consume('E')) {
                consume('+') || consume('-')
                consumeDigits()
            }
            val token = source.substring(start, offset)
            return if (token.contains('.') || token.contains('e', ignoreCase = true)) {
                token.toDouble()
            } else {
                token.toLong()
            }
        }

        private fun consumeDigits() {
            val start = offset
            while (offset < source.length && source[offset] in '0'..'9') {
                offset++
            }
            require(offset > start) { "Expected a JSON digit at offset $offset." }
        }

        private fun <T> parseLiteral(
            literal: String,
            value: T,
        ): T {
            require(source.startsWith(literal, offset)) {
                "Expected '$literal' at offset $offset."
            }
            offset += literal.length
            return value
        }

        private fun skipWhitespace() {
            while (
                offset < source.length &&
                source[offset] in charArrayOf(' ', '\t', '\r', '\n')
            ) {
                offset++
            }
        }

        private fun expect(expected: Char) {
            require(consume(expected)) {
                "Expected '$expected' at offset $offset."
            }
        }

        private fun consume(expected: Char): Boolean {
            if (offset >= source.length || source[offset] != expected) return false
            offset++
            return true
        }
    }
}
