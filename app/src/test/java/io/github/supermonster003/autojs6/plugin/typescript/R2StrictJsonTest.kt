package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2StrictJsonTest {

    @Test
    fun rejectsDuplicateKeysIncludingEscapedEquivalentKeys() {
        val documents = listOf(
            """{"value":1,"value":2}""",
            """{"value":1,"\u0076alue":2}""",
            """{"nested":{"value":1,"value":2}}""",
        )

        documents.forEach { document ->
            val failure = assertThrows(IllegalArgumentException::class.java) {
                parse(document)
            }
            assertTrue(failure.message.orEmpty().contains("duplicate key"))
        }
    }

    @Test
    fun duplicateKeyErrorsDoNotEchoAnAttackerControlledDocument() {
        val longKey = "k".repeat(16 * 1024)
        val failure = assertThrows(IllegalArgumentException::class.java) {
            parse("""{"$longKey":1,"$longKey":2}""")
        }

        assertTrue(failure.message.orEmpty().length < 256)
        assertTrue(failure.message.orEmpty().contains("16384 chars"))
    }

    @Test
    fun rejectsMalformedUtf8BomTrailingContentAndNonObjectDocuments() {
        assertThrows(IllegalArgumentException::class.java) {
            R2StrictJson.parseObject(
                byteArrayOf(0xc3.toByte(), 0x28),
            )
        }

        val bomDocument =
            byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) +
                "{}".toByteArray(StandardCharsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            R2StrictJson.parseObject(bomDocument)
        }

        listOf(
            "{} trailing",
            "{}{}",
            "[]",
            "null",
        ).forEach { document ->
            assertThrows(IllegalArgumentException::class.java) {
                parse(document)
            }
        }

        assertEquals(
            emptyMap<String, Any?>(),
            parse(" \t\r\n{}\n"),
        )
    }

    @Test
    fun enforcesDepthAndNodeBudgetsAtExactBoundaries() {
        val nested = """{"one":{"two":{}}}"""
        assertEquals(
            emptyMap<String, Any?>(),
            (
                R2StrictJson.parseObject(
                    nested.toByteArray(StandardCharsets.UTF_8),
                    maximumDepth = 3,
                )["one"] as Map<*, *>
                )["two"],
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2StrictJson.parseObject(
                nested.toByteArray(StandardCharsets.UTF_8),
                maximumDepth = 2,
            )
        }

        val threeValues = """{"one":[],"two":{},"three":true}"""
        assertEquals(
            true,
            R2StrictJson.parseObject(
                threeValues.toByteArray(StandardCharsets.UTF_8),
                maximumNodes = 3,
            )["three"],
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2StrictJson.parseObject(
                threeValues.toByteArray(StandardCharsets.UTF_8),
                maximumNodes = 2,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            R2StrictJson.parseObject(
                "{}".toByteArray(StandardCharsets.UTF_8),
                maximumDepth = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            R2StrictJson.parseObject(
                "{}".toByteArray(StandardCharsets.UTF_8),
                maximumNodes = 0,
            )
        }
    }

    @Test
    fun preservesCanonicalAndNonCanonicalNumberTokensWithoutRounding() {
        val parsed = parse(
            """
            {
              "zero": 0,
              "integer": 9223372036854775808,
              "negativeZero": -0,
              "fraction": 1.50,
              "exponent": 6.02e+23
            }
            """.trimIndent(),
        )

        assertEquals(R2StrictJson.NumberToken("0"), parsed["zero"])
        assertEquals(
            R2StrictJson.NumberToken("9223372036854775808"),
            parsed["integer"],
        )
        assertEquals(R2StrictJson.NumberToken("-0"), parsed["negativeZero"])
        assertEquals(R2StrictJson.NumberToken("1.50"), parsed["fraction"])
        assertEquals(R2StrictJson.NumberToken("6.02e+23"), parsed["exponent"])
    }

    @Test
    fun rejectsMalformedNumberTokens() {
        listOf(
            """{"value":01}""",
            """{"value":-}""",
            """{"value":1.}""",
            """{"value":1e}""",
            """{"value":1e+}""",
            """{"value":+1}""",
        ).forEach { document ->
            assertThrows(IllegalArgumentException::class.java) {
                parse(document)
            }
        }
    }

    @Test
    fun acceptsEscapedSurrogatePairButRejectsUnpairedSurrogates() {
        val pair = parse(jsonStringValue("\\uD83D\\uDE00"))
        assertEquals(
            String(Character.toChars(0x1f600)),
            pair["value"],
        )

        listOf(
            "\\uD800",
            "\\uDBFF",
            "\\uDC00",
            "\\uD83Dvalue",
        ).forEach { escapedValue ->
            assertThrows(IllegalArgumentException::class.java) {
                parse(jsonStringValue(escapedValue))
            }
        }
    }

    @Test
    fun parsesPrimitiveArraysAndDecodedEscapesWithoutMutableNumberCoercion() {
        val parsed = parse(
            """{"items":[true,false,null,"line\nfeed",42]}""",
        )
        val items = parsed["items"] as List<*>

        assertEquals(listOf(true, false, null, "line\nfeed"), items.take(4))
        assertEquals(R2StrictJson.NumberToken("42"), items.last())
    }

    private fun parse(source: String): Map<String, Any?> =
        R2StrictJson.parseObject(source.toByteArray(StandardCharsets.UTF_8))

    private fun jsonStringValue(escapedValue: String): String =
        """{"value":"$escapedValue"}"""
}
