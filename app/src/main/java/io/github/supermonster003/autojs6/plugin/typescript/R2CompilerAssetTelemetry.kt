package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observational transfer timeline of the large compiler Worker assets (startup-latency L2-2).
 *
 * The Worker only sees how long `importScripts("typescript.js")` took in total. This telemetry
 * splits that wait on the native side: when the asset origin handled the request (`servedAtMs`),
 * when the response was ready (`readyAtMs`, after native verification for declaration bundles),
 * when WebView pulled the first byte and when it reached the end. Every value is a millisecond
 * offset since the compiler process started, so it lines up with `typescript-compiler-boot`.
 * One line per transfer; lines carry fixed asset names and numbers only, and every entry point
 * swallows its own failures so telemetry can never change what the Worker receives.
 */
internal object R2CompilerAssetTelemetry {

    const val LOG_PREFIX = "typescript-compiler-asset"
    const val SCHEMA_REVISION = 1

    fun processUptimeMs(): Long = try {
        (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()).coerceAtLeast(0L)
    } catch (_: Throwable) {
        -1L
    }

    /** Wraps [stream] so its first read and its end are reported once; falls back to [stream]. */
    fun observe(
        asset: String,
        servedAtMs: Long,
        readyAtMs: Long,
        stream: InputStream,
    ): InputStream = try {
        ObservedStream(asset, servedAtMs, readyAtMs, stream, ::processUptimeMs) { line ->
            Log.i(R2CompilerBootTelemetry.LOG_TAG, line)
        }
    } catch (_: Throwable) {
        stream
    }

    internal fun logLine(
        asset: String,
        servedAtMs: Long,
        readyAtMs: Long,
        firstReadAtMs: Long?,
        endAtMs: Long,
        bytes: Long,
    ): String = buildString {
        append(LOG_PREFIX)
        append(" schema=").append(SCHEMA_REVISION)
        append(" asset=").append(asset)
        append(" servedAtMs=").append(servedAtMs)
        append(" readyAtMs=").append(readyAtMs)
        append(" firstReadAtMs=").append(firstReadAtMs?.toString() ?: "na")
        append(" endAtMs=").append(endAtMs)
        append(" bytes=").append(bytes)
    }

    internal class ObservedStream(
        private val asset: String,
        private val servedAtMs: Long,
        private val readyAtMs: Long,
        stream: InputStream,
        private val clock: () -> Long,
        private val sink: (String) -> Unit,
    ) : FilterInputStream(stream) {

        @Volatile
        private var firstReadAtMs: Long? = null

        @Volatile
        private var bytes = 0L
        private val reported = AtomicBoolean(false)

        override fun read(): Int {
            markFirstRead()
            val value = super.read()
            if (value < 0) report() else count(1)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            markFirstRead()
            val read = super.read(buffer, offset, length)
            if (read < 0) report() else count(read.toLong())
            return read
        }

        override fun close() {
            try {
                super.close()
            } finally {
                report()
            }
        }

        private fun markFirstRead() {
            if (firstReadAtMs == null) {
                firstReadAtMs = quietlyNow()
            }
        }

        private fun count(read: Long) {
            bytes += read
        }

        private fun report() {
            if (!reported.compareAndSet(false, true)) return
            try {
                sink(logLine(asset, servedAtMs, readyAtMs, firstReadAtMs, quietlyNow(), bytes))
            } catch (_: Throwable) {
                // Asset telemetry must never influence what the Worker receives.
            }
        }

        private fun quietlyNow(): Long = try {
            clock()
        } catch (_: Throwable) {
            -1L
        }
    }
}
