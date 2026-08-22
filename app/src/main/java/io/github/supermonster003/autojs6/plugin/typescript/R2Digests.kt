package io.github.supermonster003.autojs6.plugin.typescript

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object R2Digests {

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance(SHA_256)
            .digest(bytes)
            .toLowerHex()

    fun sha256(
        input: InputStream,
        maximumBytes: Long,
    ): R2DigestResult {
        require(maximumBytes >= 0L)
        val digest = MessageDigest.getInstance(SHA_256)
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var byteCount = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) {
                val singleByte = input.read()
                if (singleByte < 0) break
                check(byteCount < maximumBytes) {
                    "Input exceeds the SHA-256 byte budget."
                }
                digest.update(singleByte.toByte())
                byteCount++
                continue
            }
            check(byteCount <= maximumBytes - read) {
                "Input exceeds the SHA-256 byte budget."
            }
            digest.update(buffer, 0, read)
            byteCount += read
        }
        return R2DigestResult(
            byteCount = byteCount,
            sha256 = digest.digest().toLowerHex(),
        )
    }

    fun requireLowerSha256(
        value: String,
        label: String,
    ): String = value.also {
        require(LOWER_SHA_256.matches(it)) {
            "$label must be a lowercase SHA-256."
        }
    }

    fun equal(
        expected: String,
        actual: String,
    ): Boolean = MessageDigest.isEqual(
        expected.toByteArray(StandardCharsets.US_ASCII),
        actual.toByteArray(StandardCharsets.US_ASCII),
    )

    private fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private const val SHA_256 = "SHA-256"
    private const val STREAM_BUFFER_BYTES = 16 * 1024
    private val LOWER_SHA_256 = Regex("[0-9a-f]{64}")
}

internal data class R2DigestResult(
    val byteCount: Long,
    val sha256: String,
)
