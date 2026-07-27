package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import java.io.Reader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

/**
 * Debug-only probe for the exact Rhino artifact consumed by AutoJs6.
 *
 * The service intentionally lives outside the production source set and in its own process.
 * Every terminal response hard-codes [R1RhinoProbeProtocol.KEY_SUPPORTED] and
 * [R1RhinoProbeProtocol.KEY_FALLBACK_ELIGIBLE] to false. It cannot select or advertise a
 * production compiler backend.
 */
class R1RhinoProbeService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val messenger = Messenger(IncomingHandler())

    @Volatile
    private var activeProbe: ActiveProbe? = null

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        activeProbe?.let { probe ->
            probe.cancelRequested.set(true)
            probe.thread.interrupt()
        }
        activeProbe = null
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {

        override fun handleMessage(message: Message) {
            when (message.what) {
                R1RhinoProbeProtocol.MESSAGE_PROBE -> startProbe(message)
                R1RhinoProbeProtocol.MESSAGE_CANCEL -> cancelProbe(message)
                else -> super.handleMessage(message)
            }
        }
    }

    private fun startProbe(message: Message) {
        val replyTo = message.replyTo ?: return
        if (!isValidRequestBundle(message.data, PROBE_REQUEST_KEYS)) {
            sendResult(
                replyTo = replyTo,
                result = baseResult("", "").apply {
                    putString(R1RhinoProbeProtocol.KEY_PHASE, R1RhinoProbeProtocol.PHASE_ADMISSION)
                    putString(
                        R1RhinoProbeProtocol.KEY_ERROR_MESSAGE,
                        "Probe request must be a bounded primitive-only Bundle.",
                    )
                },
            )
            return
        }
        val requestId = message.data.getString(R1RhinoProbeProtocol.KEY_REQUEST_ID).orEmpty()
        val mode = message.data.getString(R1RhinoProbeProtocol.KEY_MODE)
            ?: R1RhinoProbeProtocol.MODE_PARSE
        if (
            requestId.isBlank() ||
            requestId.length > MAX_REQUEST_ID_CHARS ||
            mode !in SUPPORTED_MODES
        ) {
            sendResult(
                replyTo = replyTo,
                result = baseResult(requestId, mode).apply {
                    putString(R1RhinoProbeProtocol.KEY_PHASE, R1RhinoProbeProtocol.PHASE_ADMISSION)
                    putString(
                        R1RhinoProbeProtocol.KEY_ERROR_MESSAGE,
                        "Probe request metadata is invalid.",
                    )
                },
            )
            return
        }
        if (activeProbe != null) {
            sendResult(
                replyTo = replyTo,
                result = baseResult(requestId, mode).apply {
                    putString(R1RhinoProbeProtocol.KEY_PHASE, R1RhinoProbeProtocol.PHASE_ADMISSION)
                    putString(
                        R1RhinoProbeProtocol.KEY_ERROR_MESSAGE,
                        "A Rhino feasibility probe is already active.",
                    )
                },
            )
            return
        }

        lateinit var probe: ActiveProbe
        val thread = Thread(
            {
                executeProbe(probe)
            },
            "r1-rhino-feasibility-probe",
        )
        probe = ActiveProbe(
            requestId = requestId,
            mode = mode,
            replyTo = replyTo,
            thread = thread,
        )
        activeProbe = probe
        thread.start()
    }

    private fun cancelProbe(message: Message) {
        if (!isValidRequestBundle(message.data, CANCEL_REQUEST_KEYS)) return
        val requestId = message.data.getString(R1RhinoProbeProtocol.KEY_REQUEST_ID).orEmpty()
        val probe = activeProbe ?: return
        if (probe.requestId != requestId) return

        probe.cancelRequested.set(true)
        probe.thread.interrupt()
        mainHandler.postDelayed(
            {
                val current = activeProbe
                if (
                    current === probe &&
                    current.cancelRequested.get() &&
                    current.thread.isAlive
                ) {
                    // Rhino parsing has no cooperative cancellation contract. Contain a parser
                    // that ignores interruption by terminating only this debug probe process.
                    Process.killProcess(Process.myPid())
                }
            },
            UNCOOPERATIVE_WATCHDOG_MILLIS,
        )
    }

    private fun executeProbe(probe: ActiveProbe) {
        val startedAt = SystemClock.elapsedRealtime()
        val state = ProbeState(
            result = baseResult(probe.requestId, probe.mode).apply {
                putLong(R1RhinoProbeProtocol.KEY_BASELINE_PSS_KIB, Debug.getPss())
            },
        )
        try {
            evaluateCompilerAsset(probe, state)
            state.phase = R1RhinoProbeProtocol.PHASE_UNEXPECTED_SUCCESS
            state.result.putString(
                R1RhinoProbeProtocol.KEY_ERROR_MESSAGE,
                "Rhino unexpectedly parsed the TypeScript compiler; fallback remains forbidden.",
            )
        } catch (error: Throwable) {
            val root = unwrapReflectionFailure(error)
            state.phase = if (probe.cancelRequested.get()) {
                R1RhinoProbeProtocol.PHASE_CANCELLED
            } else {
                state.phase
            }
            recordFirstFailure(state.result, root)
        } finally {
            state.result.putString(R1RhinoProbeProtocol.KEY_PHASE, state.phase)
            state.result.putBoolean(
                R1RhinoProbeProtocol.KEY_CANCEL_REQUESTED,
                probe.cancelRequested.get(),
            )
            state.result.putLong(
                R1RhinoProbeProtocol.KEY_ELAPSED_MS,
                SystemClock.elapsedRealtime() - startedAt,
            )
            state.result.putLong(R1RhinoProbeProtocol.KEY_FINAL_PSS_KIB, Debug.getPss())
            mainHandler.post {
                if (activeProbe === probe) {
                    activeProbe = null
                    sendResult(probe.replyTo, state.result)
                    stopSelf()
                }
            }
        }
    }

    private fun evaluateCompilerAsset(
        probe: ActiveProbe,
        state: ProbeState,
    ) {
        state.phase = R1RhinoProbeProtocol.PHASE_RHINO_CLASS_LOAD
        val contextClass = Class.forName(RHINO_CONTEXT_CLASS)
        val scopeClass = Class.forName(RHINO_SCOPE_CLASS)
        val classShutterClass = Class.forName(RHINO_CLASS_SHUTTER_CLASS)
        val rhinoContext = contextClass.getMethod("enter").invoke(null)
        try {
            state.phase = R1RhinoProbeProtocol.PHASE_CONTEXT_CONFIGURATION
            val languageVersion = contextClass.getField("VERSION_ES6").getInt(null)
            check(languageVersion == R1RhinoProbeProtocol.EXPECTED_LANGUAGE_VERSION_ES6) {
                "Locked Rhino VERSION_ES6 changed to $languageVersion."
            }
            contextClass.getMethod("setLanguageVersion", Int::class.javaPrimitiveType)
                .invoke(rhinoContext, languageVersion)
            contextClass.getMethod("setOptimizationLevel", Int::class.javaPrimitiveType)
                .invoke(
                    rhinoContext,
                    R1RhinoProbeProtocol.INTERPRETED_OPTIMIZATION_LEVEL,
                )
            val denyAllClassShutter = Proxy.newProxyInstance(
                classShutterClass.classLoader,
                arrayOf(classShutterClass),
            ) { proxy, method, arguments ->
                when (method.name) {
                    "visibleToScripts" -> false
                    "toString" -> "R1DenyAllClassShutter"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === arguments?.firstOrNull()
                    else -> null
                }
            }
            contextClass.getMethod("setClassShutter", classShutterClass)
                .invoke(rhinoContext, denyAllClassShutter)
            val safeScope = contextClass.getMethod("initSafeStandardObjects")
                .invoke(rhinoContext)
            state.result.putInt(
                R1RhinoProbeProtocol.KEY_LANGUAGE_VERSION,
                languageVersion,
            )
            state.result.putInt(
                R1RhinoProbeProtocol.KEY_OPTIMIZATION_LEVEL,
                R1RhinoProbeProtocol.INTERPRETED_OPTIMIZATION_LEVEL,
            )
            state.result.putString(
                R1RhinoProbeProtocol.KEY_SECURITY_PROFILE,
                R1RhinoProbeProtocol.SECURITY_PROFILE_SAFE_DENY_ALL_CLASSES,
            )

            state.phase = R1RhinoProbeProtocol.PHASE_COMPILER_ASSET_DIGEST
            val metadata = CompilerDistributionMetadata.load(this)
            val compilerAssetPath =
                "typescript/${BuildConfig.TYPESCRIPT_VERSION}/lib/typescript.js"
            val compilerSha256 = assets.open(compilerAssetPath).use(::sha256)
            val declaredCompilerSha256 = loadDeclaredCompilerSha256()
            state.result.putString(
                R1RhinoProbeProtocol.KEY_COMPILER_ASSET_SHA256,
                compilerSha256,
            )
            state.result.putString(
                R1RhinoProbeProtocol.KEY_DISTRIBUTION_FINGERPRINT,
                metadata.distributionFingerprint,
            )
            state.result.putString(
                R1RhinoProbeProtocol.KEY_DECLARATIONS_FINGERPRINT,
                metadata.declarationsFingerprint,
            )
            check(compilerSha256 == declaredCompilerSha256) {
                "TypeScript compiler asset digest differs from distribution.json."
            }

            state.phase = R1RhinoProbeProtocol.PHASE_COMPILER_PARSE
            val evaluateReader = contextClass.getMethod(
                "evaluateReader",
                scopeClass,
                Reader::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                Any::class.java,
            )
            assets.open(compilerAssetPath)
                .bufferedReader(StandardCharsets.UTF_8)
                .use { assetReader ->
                    val evaluationReader = if (
                        probe.mode ==
                        R1RhinoProbeProtocol.MODE_UNCOOPERATIVE_CANCELLATION
                    ) {
                        UncooperativeCancellationReader(assetReader)
                    } else {
                        assetReader
                    }
                    sendStarted(probe, state.result)
                    evaluateReader.invoke(
                        rhinoContext,
                        safeScope,
                        evaluationReader,
                        "asset:///$compilerAssetPath",
                        1,
                        null,
                    )
                }
        } finally {
            contextClass.getMethod("exit").invoke(null)
        }
    }

    private fun loadDeclaredCompilerSha256(): String {
        val distributionPath =
            "typescript/${BuildConfig.TYPESCRIPT_VERSION}/distribution.json"
        val manifestText = assets.open(distributionPath).use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        }
        val compiler = JSONObject(manifestText)
            .getJSONObject("components")
            .getJSONObject("compiler")
        check(compiler.getString("path") == "lib/typescript.js")
        return compiler.getString("sha256").also { digest ->
            check(digest.matches(LOWER_SHA256))
        }
    }

    private fun isValidRequestBundle(
        bundle: Bundle,
        expectedKeys: Set<String>,
    ): Boolean {
        val validation = BinderMetadataValidator.validate(bundle)
        return validation.primitiveMetadataOnly &&
            validation.metadataWithinBudget &&
            bundle.keySet() == expectedKeys
    }

    private fun sendStarted(
        probe: ActiveProbe,
        result: Bundle,
    ) {
        val telemetry = Bundle(result).apply {
            putString(
                R1RhinoProbeProtocol.KEY_PHASE,
                R1RhinoProbeProtocol.PHASE_COMPILER_PARSE,
            )
        }
        send(probe.replyTo, R1RhinoProbeProtocol.MESSAGE_STARTED, telemetry)
    }

    private fun sendResult(
        replyTo: Messenger,
        result: Bundle,
    ) {
        send(replyTo, R1RhinoProbeProtocol.MESSAGE_RESULT, result)
    }

    private fun send(
        replyTo: Messenger,
        what: Int,
        payload: Bundle,
    ) {
        try {
            replyTo.send(
                Message.obtain(null, what).apply {
                    data = payload
                },
            )
        } catch (_: RemoteException) {
            // The test client may have disappeared while this disposable process was parsing.
        }
    }

    private fun baseResult(
        requestId: String,
        mode: String,
    ) = Bundle().apply {
        putString(R1RhinoProbeProtocol.KEY_REQUEST_ID, bounded(requestId))
        putString(R1RhinoProbeProtocol.KEY_MODE, bounded(mode))
        putBoolean(R1RhinoProbeProtocol.KEY_SUPPORTED, false)
        putBoolean(R1RhinoProbeProtocol.KEY_FALLBACK_ELIGIBLE, false)
        putInt(R1RhinoProbeProtocol.KEY_PID, Process.myPid())
        putString(
            R1RhinoProbeProtocol.KEY_ARTIFACT_NAME,
            BuildConfig.R1_RHINO_PROBE_ARTIFACT_NAME,
        )
        putString(
            R1RhinoProbeProtocol.KEY_ARTIFACT_SHA256,
            BuildConfig.R1_RHINO_PROBE_ARTIFACT_SHA256,
        )
        putString(
            R1RhinoProbeProtocol.KEY_RHINO_SOURCE_COMMIT,
            BuildConfig.R1_RHINO_PROBE_SOURCE_COMMIT,
        )
        putString(
            R1RhinoProbeProtocol.KEY_AUTOJS6_CONSUMER_COMMIT,
            BuildConfig.R1_RHINO_PROBE_CONSUMER_COMMIT,
        )
        putString(
            R1RhinoProbeProtocol.KEY_TYPESCRIPT_VERSION,
            BuildConfig.TYPESCRIPT_VERSION,
        )
    }

    private fun recordFirstFailure(
        result: Bundle,
        error: Throwable,
    ) {
        result.putString(R1RhinoProbeProtocol.KEY_ERROR_CLASS, bounded(error.javaClass.name))
        result.putString(
            R1RhinoProbeProtocol.KEY_ERROR_MESSAGE,
            bounded(error.message ?: error.toString()),
        )
        invokeStringMethod(error, "sourceName")?.let { source ->
            result.putString(R1RhinoProbeProtocol.KEY_ERROR_SOURCE, bounded(source))
        }
        invokeIntMethod(error, "lineNumber")?.let { line ->
            result.putInt(R1RhinoProbeProtocol.KEY_ERROR_LINE, line)
        }
        invokeIntMethod(error, "columnNumber")?.let { column ->
            result.putInt(R1RhinoProbeProtocol.KEY_ERROR_COLUMN, column)
        }
        invokeStringMethod(error, "lineSource")?.let { lineSource ->
            result.putString(
                R1RhinoProbeProtocol.KEY_ERROR_LINE_SOURCE,
                bounded(lineSource),
            )
        }
    }

    private fun invokeStringMethod(
        target: Throwable,
        name: String,
    ): String? = runCatching {
        target.javaClass.getMethod(name).invoke(target) as? String
    }.getOrNull()

    private fun invokeIntMethod(
        target: Throwable,
        name: String,
    ): Int? = runCatching {
        (target.javaClass.getMethod(name).invoke(target) as? Number)?.toInt()
    }.getOrNull()

    private fun unwrapReflectionFailure(error: Throwable): Throwable {
        var current = error
        repeat(MAX_UNWRAP_DEPTH) {
            current = when (current) {
                is InvocationTargetException -> current.targetException ?: return current
                is ExceptionInInitializerError -> current.exception ?: return current
                else -> return current
            }
        }
        return current
    }

    private fun bounded(value: String): String = value.take(MAX_ERROR_CHARS)

    private fun sha256(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DIGEST_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private data class ActiveProbe(
        val requestId: String,
        val mode: String,
        val replyTo: Messenger,
        val thread: Thread,
        val cancelRequested: AtomicBoolean = AtomicBoolean(false),
    )

    private data class ProbeState(
        val result: Bundle,
        var phase: String = R1RhinoProbeProtocol.PHASE_RHINO_CLASS_LOAD,
    )

    private class UncooperativeCancellationReader(
        private val delegate: Reader,
    ) : Reader() {

        override fun read(
            destination: CharArray,
            offset: Int,
            length: Int,
        ): Int {
            try {
                Thread.sleep(UNCOOPERATIVE_READ_DELAY_MILLIS)
            } catch (_: InterruptedException) {
                // This mode proves the process watchdog bounds an uncooperative Rhino parse.
            }
            return delegate.read(destination, offset, length)
        }

        override fun close() {
            delegate.close()
        }
    }

    private companion object {
        const val RHINO_CONTEXT_CLASS = "org.mozilla.javascript.Context"
        const val RHINO_SCOPE_CLASS = "org.mozilla.javascript.VarScope"
        const val RHINO_CLASS_SHUTTER_CLASS = "org.mozilla.javascript.ClassShutter"
        const val MAX_REQUEST_ID_CHARS = 128
        const val MAX_ERROR_CHARS = 4_096
        const val MAX_UNWRAP_DEPTH = 8
        const val DIGEST_BUFFER_BYTES = 32 * 1024
        const val UNCOOPERATIVE_READ_DELAY_MILLIS = 250L
        const val UNCOOPERATIVE_WATCHDOG_MILLIS = 750L
        val LOWER_SHA256 = Regex("[0-9a-f]{64}")
        val SUPPORTED_MODES = setOf(
            R1RhinoProbeProtocol.MODE_PARSE,
            R1RhinoProbeProtocol.MODE_UNCOOPERATIVE_CANCELLATION,
        )
        val PROBE_REQUEST_KEYS = setOf(
            R1RhinoProbeProtocol.KEY_REQUEST_ID,
            R1RhinoProbeProtocol.KEY_MODE,
        )
        val CANCEL_REQUEST_KEYS = setOf(R1RhinoProbeProtocol.KEY_REQUEST_ID)
    }
}
