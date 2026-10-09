package io.github.supermonster003.autojs6.plugin.typescript

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class R2CompilerAssetTelemetryTest {

    @Test
    fun reportsFirstReadAndEndOnceWithoutChangingTheBytes() {
        var now = 100L
        val lines = mutableListOf<String>()
        val payload = ByteArray(10_000) { index -> (index % 251).toByte() }
        val stream = R2CompilerAssetTelemetry.ObservedStream(
            asset = "typescript",
            servedAtMs = 90L,
            readyAtMs = 95L,
            stream = ByteArrayInputStream(payload),
            clock = { now },
            sink = lines::add,
        )

        now = 120L
        // One spare byte so the final read reaches EOF instead of an empty-range check.
        val copied = ByteArray(payload.size + 1)
        var offset = 0
        while (true) {
            val read = stream.read(copied, offset, minOf(4_096, copied.size - offset))
            if (read < 0) break
            offset += read
            now += 7L
        }
        stream.close()
        stream.close()

        assertEquals(payload.size, offset)
        assertArrayEquals(payload, copied.copyOf(payload.size))
        assertEquals(
            listOf(
                "typescript-compiler-asset schema=1 asset=typescript servedAtMs=90 " +
                    "readyAtMs=95 firstReadAtMs=120 endAtMs=141 bytes=10000",
            ),
            lines,
        )
    }

    @Test
    fun reportsOnCloseWhenWebViewStopsEarlyAndSwallowsSinkFailures() {
        val lines = mutableListOf<String>()
        val early = R2CompilerAssetTelemetry.ObservedStream(
            asset = "common",
            servedAtMs = 10L,
            readyAtMs = 40L,
            stream = ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            clock = { 50L },
            sink = lines::add,
        )
        assertEquals(1, early.read())
        early.close()
        assertEquals(
            listOf(
                "typescript-compiler-asset schema=1 asset=common servedAtMs=10 readyAtMs=40 " +
                    "firstReadAtMs=50 endAtMs=50 bytes=1",
            ),
            lines,
        )

        val throwingSink = R2CompilerAssetTelemetry.ObservedStream(
            asset = "rhino",
            servedAtMs = 1L,
            readyAtMs = 2L,
            stream = ByteArrayInputStream(byteArrayOf(9)),
            clock = { error("clock failure") },
            sink = { error("sink failure") },
        )
        assertEquals(9, throwingSink.read())
        assertEquals(-1, throwingSink.read())
        throwingSink.close()
    }

    @Test
    fun propagatesUnderlyingReadFailures() {
        val failing = object : InputStream() {
            override fun read(): Int = throw IOException("asset read failed")
        }
        val stream = R2CompilerAssetTelemetry.ObservedStream(
            asset = "typescript",
            servedAtMs = 0L,
            readyAtMs = 0L,
            stream = failing,
            clock = { 0L },
            sink = {},
        )
        try {
            stream.read()
            fail("An underlying read failure was swallowed.")
        } catch (_: IOException) {
            // Expected: telemetry never hides asset failures from WebView.
        }
    }
}
