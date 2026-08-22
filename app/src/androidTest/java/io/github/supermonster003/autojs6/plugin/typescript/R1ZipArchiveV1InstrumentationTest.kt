package io.github.supermonster003.autojs6.plugin.typescript

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Exception
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1FailureKind
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validator
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1ZipArchiveV1InstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun literalCorpusHasTheSameAcceptanceAndFailureKindsOnAndroid() {
        val validText = fixtureText("valid-golden.hex")
        val invalidText = fixtureText("invalid-golden.hex")
        assertEquals(VALID_SHA256, canonicalSha256(validText))
        assertEquals(INVALID_SHA256, canonicalSha256(invalidText))

        val valid = goldenSections(validText)
        assertEquals(
            setOf(
                "stored-ascii-comments",
                "deflated-ascii-utf8-signed-descriptor",
                "deflated-repetition-at-exact-100-to-1-bound",
                "empty",
            ),
            valid.keys,
        )
        valid.forEach { (label, archive) ->
            try {
                ZipArchiveV1Validator.validate(archive, ZipArchiveV1Limits.INPUT)
            } catch (error: Throwable) {
                throw AssertionError("Android rejected valid ZIP v1 fixture '$label'.", error)
            }
        }

        val exactRatio = valid.getValue(
            "deflated-repetition-at-exact-100-to-1-bound",
        )
        val ratioResult = ZipArchiveV1Validator.validate(
            exactRatio,
            ZipArchiveV1Limits.INPUT.copy(maximumCompressionRatio = 100),
        )
        assertEquals(1_200L, ratioResult.uncompressedBytes)
        assertEquals(12L, ratioResult.compressedBytes)
        assertRejected(
            expectedKind = ZipArchiveV1FailureKind.QUOTA,
            label = "exact 100:1 fixture under a 99:1 limit",
        ) {
            ZipArchiveV1Validator.validate(
                exactRatio,
                ZipArchiveV1Limits.INPUT.copy(maximumCompressionRatio = 99),
            )
        }

        val invalid = goldenSections(invalidText)
        assertEquals(
            "e\u0301.ts",
            ZipArchiveV1Validator.validate(
                invalid.getValue("non-ascii-with-utf8-flag"),
                ZipArchiveV1Limits.INPUT,
            ).entries.single().path,
        )
        val expected = linkedMapOf(
            "encrypted" to ZipArchiveV1FailureKind.FORMAT,
            "non-ascii-without-utf8-flag" to ZipArchiveV1FailureKind.PATH,
            "parent-traversal" to ZipArchiveV1FailureKind.PATH,
            "casefold-collision" to ZipArchiveV1FailureKind.PATH,
            "unix-symlink" to ZipArchiveV1FailureKind.PATH,
            "dos-volume-label" to ZipArchiveV1FailureKind.PATH,
            "dos-directory-attribute" to ZipArchiveV1FailureKind.PATH,
            "dos-reserved-type-bit" to ZipArchiveV1FailureKind.PATH,
            "local-central-name-mismatch" to ZipArchiveV1FailureKind.INTEGRITY,
            "crc-mismatch" to ZipArchiveV1FailureKind.INTEGRITY,
            "zip64-sentinel" to ZipArchiveV1FailureKind.FORMAT,
            "trailing-byte" to ZipArchiveV1FailureKind.FORMAT,
            "preamble" to ZipArchiveV1FailureKind.INTEGRITY,
            "extra-field" to ZipArchiveV1FailureKind.FORMAT,
            "unsigned-data-descriptor" to ZipArchiveV1FailureKind.FORMAT,
            "unsupported-method" to ZipArchiveV1FailureKind.FORMAT,
        )
        assertEquals(expected.keys + "non-ascii-with-utf8-flag", invalid.keys)
        expected.forEach { (label, kind) ->
            assertRejected(kind, label) {
                ZipArchiveV1Validator.validate(
                    invalid.getValue(label),
                    ZipArchiveV1Limits.INPUT,
                )
            }
        }
    }

    private fun fixtureText(fileName: String): String =
        context.assets.open("archive-v1/$fileName").bufferedReader().use { it.readText() }

    private fun goldenSections(text: String): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var currentName: String? = null
        val currentHex = StringBuilder()

        fun flush() {
            val name = currentName ?: return
            val hex = currentHex.toString()
            require(hex.length % 2 == 0) {
                "Fixture '$name' contains an odd number of hex digits."
            }
            result[name] = ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
            currentHex.setLength(0)
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith('#') -> Unit
                line.startsWith('[') && line.endsWith(']') -> {
                    flush()
                    currentName = line.substring(1, line.length - 1)
                    require(currentName.isNotBlank())
                    require(currentName !in result)
                }
                else -> {
                    require(currentName != null) {
                        "Hex bytes appeared before a fixture section."
                    }
                    require(line.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        "Fixture '$currentName' contains non-hex text."
                    }
                    currentHex.append(line)
                }
            }
        }
        flush()
        return result
    }

    private fun canonicalSha256(text: String): String {
        val canonical = text.replace("\r\n", "\n").replace('\r', '\n')
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }

    private fun assertRejected(
        expectedKind: ZipArchiveV1FailureKind,
        label: String,
        action: () -> Unit,
    ) {
        try {
            action()
            fail("ZIP v1 fixture '$label' was accepted.")
        } catch (error: ZipArchiveV1Exception) {
            assertEquals(label, expectedKind, error.kind)
        }
    }

    private companion object {
        const val VALID_SHA256 =
            "9dd0594ef28cd49d0903095b15b912142f7909b1ec8fffe2018052efd6c7af4f"
        const val INVALID_SHA256 =
            "513334e8a499743f6df53e4b155579192a572c677cfaedbaecd714047c7337ef"
    }
}
