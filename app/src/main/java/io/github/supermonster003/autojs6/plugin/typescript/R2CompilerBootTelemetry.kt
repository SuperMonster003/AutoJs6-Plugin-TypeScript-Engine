package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Immutable view of the dedicated compiler process boot timeline.
 *
 * Every `*AtMs` value is an offset in milliseconds since the compiler process started; every
 * `*Ms` value is a duration. `null` means the point was not observed before the snapshot.
 */
internal data class R2CompilerBootSnapshot(
    val processUptimeMs: Long,
    val serviceCreateStartedAtMs: Long?,
    val serviceCreateMs: Long?,
    val declarationLoadMs: Long?,
    val supervisorReadyAtMs: Long?,
    val webViewCreatedAtMs: Long?,
    val pageReadyAtMs: Long?,
    val workerReadyAtMs: Long?,
    val workerBootstrapMs: Long?,
) {

    fun toLogLine(): String = buildString {
        append(R2CompilerBootTelemetry.LOG_PREFIX)
        append(" schema=").append(R2CompilerBootTelemetry.SCHEMA_REVISION)
        append(" processUptimeMs=").append(processUptimeMs)
        append(" serviceCreateStartMs=").append(serviceCreateStartedAtMs.asLogValue())
        append(" serviceCreateMs=").append(serviceCreateMs.asLogValue())
        append(" declarationLoadMs=").append(declarationLoadMs.asLogValue())
        append(" supervisorReadyAtMs=").append(supervisorReadyAtMs.asLogValue())
        append(" webViewCreatedAtMs=").append(webViewCreatedAtMs.asLogValue())
        append(" pageReadyAtMs=").append(pageReadyAtMs.asLogValue())
        append(" workerReadyAtMs=").append(workerReadyAtMs.asLogValue())
        append(" workerBootstrapMs=").append(workerBootstrapMs.asLogValue())
    }
}

private fun Long?.asLogValue(): String = this?.toString() ?: "na"

/**
 * Records the first occurrence of each compiler process boot milestone.
 *
 * The timeline publishes exactly one boot line, when the first Worker of the process reports
 * `ready`. A supervisor registration that completes after that line is published as one
 * additional `event=supervisorReady` line so the two can be correlated. Later Worker
 * generations, renderer recoveries and repeated service creations never report again: the
 * timeline describes the process, not a session.
 */
internal class R2CompilerBootTimeline(
    private val processUptimeMs: () -> Long,
) {

    private val lock = Any()
    private var serviceCreateStartedAtMs: Long? = null
    private var serviceCreateMs: Long? = null
    private var declarationLoadMs: Long? = null
    private var supervisorReadyAtMs: Long? = null
    private var webViewCreatedAtMs: Long? = null
    private var pageReadyAtMs: Long? = null
    private var workerReadyAtMs: Long? = null
    private var workerBootstrapMs: Long? = null
    private var bootLineReported = false

    fun recordServiceCreateStarted() = synchronized(lock) {
        if (serviceCreateStartedAtMs == null) {
            serviceCreateStartedAtMs = now()
        }
    }

    fun recordServiceCreateCompleted() = synchronized(lock) {
        val startedAt = serviceCreateStartedAtMs ?: return
        if (serviceCreateMs == null) {
            serviceCreateMs = (now() - startedAt).coerceAtLeast(0L)
        }
    }

    fun recordDeclarationLoadMs(durationMs: Long) = synchronized(lock) {
        if (declarationLoadMs == null) {
            declarationLoadMs = durationMs.coerceAtLeast(0L)
        }
    }

    /**
     * Returns a follow-up log line when the boot line was already published without this
     * milestone, otherwise `null`.
     */
    fun recordSupervisorReady(): String? = synchronized(lock) {
        if (supervisorReadyAtMs != null) return null
        val readyAt = now()
        supervisorReadyAtMs = readyAt
        if (!bootLineReported) return null
        R2CompilerBootTelemetry.LOG_PREFIX +
            " schema=" + R2CompilerBootTelemetry.SCHEMA_REVISION +
            " event=supervisorReady" +
            " supervisorReadyAtMs=" + readyAt
    }

    fun recordWebViewCreated() = synchronized(lock) {
        if (webViewCreatedAtMs == null) {
            webViewCreatedAtMs = now()
        }
    }

    fun recordPageReady() = synchronized(lock) {
        if (pageReadyAtMs == null) {
            pageReadyAtMs = now()
        }
    }

    /** Returns the boot log line exactly once per process, on the first Worker `ready`. */
    fun recordWorkerReady(bootstrapMs: Long?): String? = synchronized(lock) {
        if (workerReadyAtMs == null) {
            workerReadyAtMs = now()
            workerBootstrapMs = bootstrapMs?.takeIf { it >= 0L }
        }
        if (bootLineReported) return null
        bootLineReported = true
        snapshotLocked().toLogLine()
    }

    fun snapshot(): R2CompilerBootSnapshot = synchronized(lock) { snapshotLocked() }

    /**
     * Formats one Worker warm-up report against the process timeline. Every report is logged:
     * the Worker sends at most one per lifetime, and a renderer recovery starts a new Worker.
     */
    fun warmUpLogLine(
        generation: Int,
        state: String,
        fields: Map<String, Long?>,
    ): String = synchronized(lock) {
        buildString {
            append(R2CompilerBootTelemetry.WARM_UP_LOG_PREFIX)
            append(" schema=").append(R2CompilerBootTelemetry.WARM_UP_SCHEMA_REVISION)
            append(" processUptimeMs=").append(now())
            append(" workerReadyAtMs=").append(workerReadyAtMs.asLogValue())
            append(" generation=").append(generation)
            append(" state=").append(state)
            for (field in CompilerHostProtocol.DECLARATION_WARM_UP_FIELDS) {
                append(' ').append(field).append('=').append(fields[field].asLogValue())
            }
        }
    }

    private fun snapshotLocked() = R2CompilerBootSnapshot(
        processUptimeMs = now(),
        serviceCreateStartedAtMs = serviceCreateStartedAtMs,
        serviceCreateMs = serviceCreateMs,
        declarationLoadMs = declarationLoadMs,
        supervisorReadyAtMs = supervisorReadyAtMs,
        webViewCreatedAtMs = webViewCreatedAtMs,
        pageReadyAtMs = pageReadyAtMs,
        workerReadyAtMs = workerReadyAtMs,
        workerBootstrapMs = workerBootstrapMs,
    )

    private fun now(): Long = processUptimeMs().coerceAtLeast(0L)
}

/**
 * Process-wide boot telemetry for the `:typescript_compiler` process.
 *
 * Telemetry is observational only: every entry point swallows its own failures so a clock or
 * logging problem can never change compiler behaviour. Lines contain durations and offsets
 * only, never source text, paths, request identifiers or error messages.
 */
internal object R2CompilerBootTelemetry {

    const val LOG_TAG = "TypeScriptCompiler"
    const val LOG_PREFIX = "typescript-compiler-boot"
    const val SCHEMA_REVISION = 1
    const val WARM_UP_LOG_PREFIX = "typescript-compiler-warmup"
    const val WARM_UP_SCHEMA_REVISION = 1

    private val timeline = R2CompilerBootTimeline {
        SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
    }

    fun serviceCreateStarted() = quietly { timeline.recordServiceCreateStarted() }

    fun serviceCreateCompleted() = quietly { timeline.recordServiceCreateCompleted() }

    fun declarationLoaded(durationMs: Long) = quietly {
        timeline.recordDeclarationLoadMs(durationMs)
    }

    fun supervisorReady() = quietly {
        timeline.recordSupervisorReady()?.let { line -> Log.i(LOG_TAG, line) }
    }

    fun webViewCreated() = quietly { timeline.recordWebViewCreated() }

    fun pageReady() = quietly { timeline.recordPageReady() }

    fun workerReady(bootstrapMs: Long?) = quietly {
        timeline.recordWorkerReady(bootstrapMs)?.let { line -> Log.i(LOG_TAG, line) }
    }

    /** Startup-latency L2-7: the Worker's warm-up stand at its first project, one line each. */
    fun declarationWarmUp(message: CompilerHostMessage.DeclarationWarmUp) = quietly {
        Log.i(
            LOG_TAG,
            timeline.warmUpLogLine(message.generation, message.state, message.fields),
        )
    }

    private inline fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // Boot telemetry must never influence compiler availability.
        }
    }
}
