package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.parser.TypeScriptParserContract as Contract

internal sealed interface ParserHostOutcome {
    data class Parsed(val tree: String, val nodeCount: Int, val diagnosticCount: Int, val elapsedMs: Long) : ParserHostOutcome
    data class Failed(val code: String, val message: String) : ParserHostOutcome
    data object TimedOut : ParserHostOutcome
}

/**
 * The WebView side of the TypeScript parser contract v1. It runs in the compiler process but owns a
 * separate page (`typescript-parser-r1`) and a dedicated parser Worker, so a parse never waits for or
 * interrupts a compilation, and the compiler's timeout recovery and session rebuilds leave it alone.
 * The page is created on the first request and destroyed after [IDLE_MILLIS] without requests, on
 * memory pressure or when its renderer goes away; a parse that misses its deadline restarts the Worker.
 *
 * Binder threads call [parse]; all WebView and port work happens on the main thread.
 */
internal class TypeScriptWebViewParserHost(
    context: Context,
    private val compilerVersion: String,
) {
    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    // Main thread only.
    private var webView: WebView? = null
    private var nativePort: WebMessagePort? = null

    // Guarded by lock.
    private var closed = false
    private var workerReady = false
    private var readyLatch = CountDownLatch(1)
    private var sessionFailure: String? = null
    private val pending = LinkedHashMap<String, Pending>()
    private var lastActivityAt = 0L

    private class Pending(val requestId: String) {
        val done = CountDownLatch(1)
        @Volatile var outcome: ParserHostOutcome? = null
        var expectedChunks = -1
        var treeChars = 0
        var nodeCount = 0
        var diagnosticCount = 0
        var elapsedMs = 0L
        val chunks = StringBuilder()
        var receivedChunks = 0

        fun finish(result: ParserHostOutcome) {
            if (outcome == null) {
                outcome = result
                done.countDown()
            }
        }
    }

    fun isBusyWith(requestId: String): Boolean = synchronized(lock) { requestId in pending }

    fun parse(request: ParserRequest): ParserHostOutcome {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Parses must not run on the main thread." }
        val deadline = SystemClock.elapsedRealtime() + request.timeoutMs
        val entry: Pending
        val ready: CountDownLatch
        synchronized(lock) {
            if (closed) return ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, "The parser service is closing.")
            if (request.requestId in pending) {
                return ParserHostOutcome.Failed(Contract.ERROR_INVALID_REQUEST, "A parse with this requestId is already running.")
            }
            entry = Pending(request.requestId)
            pending[request.requestId] = entry
            lastActivityAt = SystemClock.elapsedRealtime()
        }
        try {
            ready = runOnMainAndWait(deadline) { ensureSessionOnMain() }
                ?: return timedOut(entry)
            if (!ready.await(remaining(deadline), TimeUnit.MILLISECONDS)) return timedOut(entry)
            synchronized(lock) {
                sessionFailure?.let { failure -> return ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, failure) }
                if (!workerReady) return ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, "The parser Worker is not ready.")
            }
            mainHandler.post { sendRequestOnMain(entry, request) }
            if (!entry.done.await(remaining(deadline), TimeUnit.MILLISECONDS)) return timedOut(entry)
            return entry.outcome ?: ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, "The parse ended without a result.")
        } finally {
            synchronized(lock) {
                if (pending[request.requestId] === entry) pending.remove(request.requestId)
                lastActivityAt = SystemClock.elapsedRealtime()
            }
            mainHandler.postDelayed(::reclaimIfIdleOnMain, IDLE_MILLIS)
        }
    }

    /** Drops the page when nothing is parsing, for memory pressure in the compiler process. */
    fun discardIfIdle() {
        mainHandler.post {
            val idle = synchronized(lock) { pending.isEmpty() }
            if (idle) destroySessionOnMain("The parser page was released.")
        }
    }

    fun close() {
        synchronized(lock) { closed = true }
        mainHandler.post { destroySessionOnMain("The parser service is closing.") }
    }

    private fun remaining(deadline: Long): Long = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    private fun timedOut(entry: Pending): ParserHostOutcome {
        entry.finish(ParserHostOutcome.TimedOut)
        // A Worker past the deadline may be stuck in a pathological input: replace it.
        mainHandler.post { restartWorkerOnMain("A parse exceeded its deadline.") }
        return ParserHostOutcome.TimedOut
    }

    private fun <T> runOnMainAndWait(deadline: Long, block: () -> T): T? {
        var result: T? = null
        val done = CountDownLatch(1)
        mainHandler.post {
            try {
                result = block()
            } finally {
                done.countDown()
            }
        }
        return if (done.await(remaining(deadline), TimeUnit.MILLISECONDS)) result else null
    }

    private fun ensureSessionOnMain(): CountDownLatch {
        checkMainThread()
        synchronized(lock) {
            if (webView != null || closed) return readyLatch
            workerReady = false
            sessionFailure = null
            readyLatch = CountDownLatch(1)
        }
        try {
            createSessionOnMain()
        } catch (error: Exception) {
            failSessionOnMain("The parser page could not be created: ${error.safeMessage()}")
        }
        return synchronized(lock) { readyLatch }
    }

    private fun createSessionOnMain() {
        TypeScriptWebViewCompilerHost.prepareCompilerProcess()
        val assetLoader = ParserAssetOrigin.createAssetLoader(context, compilerVersion)
        val created = WebView(context)
        webView = created
        created.settings.apply {
            javaScriptEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            databaseEnabled = false
            domStorageEnabled = false
            javaScriptCanOpenWindowsAutomatically = false
            loadsImagesAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setGeolocationEnabled(false)
            setSupportMultipleWindows(false)
        }
        created.setDownloadListener { _, _, _, _, _ -> Unit }
        created.webChromeClient = RejectingChromeClient
        created.webViewClient = ParserWebViewClient(assetLoader)
        created.loadUrl(ParserAssetOrigin.ENTRY_URL)
    }

    private fun installPortOnMain(view: WebView) {
        checkMainThread()
        if (view !== webView || nativePort != null) return
        try {
            val ports = view.createWebMessageChannel()
            val native = ports[0]
            native.setWebMessageCallback(
                object : WebMessagePort.WebMessageCallback() {
                    override fun onMessage(port: WebMessagePort, message: WebMessage) {
                        val data = message.data ?: return
                        mainHandler.post { if (port === nativePort) handlePageMessageOnMain(data) }
                    }
                },
            )
            nativePort = native
            view.postWebMessage(WebMessage(ParserAssetOrigin.PORT_MESSAGE, arrayOf(ports[1])), Uri.parse(ParserAssetOrigin.ORIGIN))
        } catch (error: Exception) {
            failSessionOnMain("The parser message channel could not be installed: ${error.safeMessage()}")
        }
    }

    private fun sendRequestOnMain(entry: Pending, request: ParserRequest) {
        checkMainThread()
        val port = nativePort
        if (port == null || entry.outcome != null) {
            entry.finish(ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, "The parser page is not available."))
            return
        }
        val frames = TypeScriptParserRequests.frames(request.sourceText, FRAME_CHARS)
        try {
            val limits = JSONObject()
                .put("maxNodes", Contract.MAX_TREE_NODES)
                .put("maxDiagnostics", Contract.MAX_DIAGNOSTICS)
                .put("maxTreeChars", Contract.MAX_TREE_BYTES.coerceAtMost(Int.MAX_VALUE.toLong()))
            port.postMessage(
                WebMessage(
                    JSONObject()
                        .put("kind", "parseStart")
                        .put("requestId", request.requestId)
                        .put("fileName", request.fileName)
                        .put("chunkCount", frames.size)
                        .put("limits", limits)
                        .toString(),
                ),
            )
            frames.forEachIndexed { index, data ->
                port.postMessage(
                    WebMessage(
                        JSONObject().put("kind", "parseChunk").put("requestId", request.requestId).put("index", index).put("data", data).toString(),
                    ),
                )
            }
            port.postMessage(WebMessage(JSONObject().put("kind", "parseEnd").put("requestId", request.requestId).toString()))
        } catch (error: Exception) {
            entry.finish(ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, "The parse request could not be sent: ${error.safeMessage()}"))
        }
    }

    private fun handlePageMessageOnMain(raw: String) {
        checkMainThread()
        val message = runCatching { JSONObject(raw) }.getOrNull() ?: return
        when (message.optString("kind")) {
            "ready" -> {
                val version = message.optString("tsVersion")
                if (version != compilerVersion) {
                    failSessionOnMain("The parser Worker reports TypeScript $version instead of $compilerVersion.")
                    return
                }
                synchronized(lock) {
                    workerReady = true
                    readyLatch.countDown()
                }
            }
            "workerFailed" -> failSessionOnMain("The parser Worker failed: ${message.optString("message").take(Contract.MAX_ERROR_MESSAGE_CHARS)}")
            "parseError" -> {
                val entry = entryFor(message) ?: return
                val code = message.optString("code").takeIf { it in KNOWN_ERRORS } ?: Contract.ERROR_UNAVAILABLE
                entry.finish(ParserHostOutcome.Failed(code, message.optString("message").take(Contract.MAX_ERROR_MESSAGE_CHARS)))
            }
            "parseResultStart" -> {
                val entry = entryFor(message) ?: return
                val chunkCount = message.optInt("chunkCount", -1)
                val treeChars = message.optInt("treeChars", -1)
                if (chunkCount < 1 || treeChars < 0 || treeChars > Contract.MAX_TREE_BYTES || entry.expectedChunks != -1) {
                    entry.finish(ParserHostOutcome.Failed(Contract.ERROR_OUTPUT_TRANSPORT, "The parser announced a malformed result."))
                    return
                }
                entry.expectedChunks = chunkCount
                entry.treeChars = treeChars
                entry.nodeCount = message.optInt("nodeCount")
                entry.diagnosticCount = message.optInt("diagnosticCount")
                entry.elapsedMs = message.optLong("elapsedMs")
                entry.chunks.ensureCapacity(treeChars)
            }
            "parseResultChunk" -> {
                val entry = entryFor(message) ?: return
                val data = message.optString("data")
                if (entry.expectedChunks < 0 || message.optInt("index", -1) != entry.receivedChunks ||
                    entry.chunks.length + data.length > entry.treeChars
                ) {
                    entry.finish(ParserHostOutcome.Failed(Contract.ERROR_OUTPUT_TRANSPORT, "The parser result frames are out of order."))
                    return
                }
                entry.chunks.append(data)
                entry.receivedChunks += 1
            }
            "parseResultEnd" -> {
                val entry = entryFor(message) ?: return
                if (entry.receivedChunks != entry.expectedChunks || entry.chunks.length != entry.treeChars) {
                    entry.finish(ParserHostOutcome.Failed(Contract.ERROR_OUTPUT_TRANSPORT, "The parser result is incomplete."))
                    return
                }
                entry.finish(ParserHostOutcome.Parsed(entry.chunks.toString(), entry.nodeCount, entry.diagnosticCount, entry.elapsedMs))
            }
        }
    }

    private fun entryFor(message: JSONObject): Pending? {
        val requestId = message.optString("requestId")
        return synchronized(lock) { pending[requestId]?.takeIf { it.outcome == null } }
    }

    private fun restartWorkerOnMain(reason: String) {
        checkMainThread()
        val port = nativePort ?: return
        val dropped = synchronized(lock) {
            workerReady = false
            readyLatch = CountDownLatch(1)
            pending.values.filter { it.outcome == null }
        }
        dropped.forEach { it.finish(ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, reason)) }
        runCatching { port.postMessage(WebMessage(JSONObject().put("kind", "restartWorker").toString())) }
            .onFailure { failSessionOnMain("The parser Worker could not be restarted.") }
    }

    private fun failSessionOnMain(reason: String) {
        checkMainThread()
        val dropped = synchronized(lock) {
            sessionFailure = reason
            readyLatch.countDown()
            pending.values.filter { it.outcome == null }
        }
        dropped.forEach { it.finish(ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, reason)) }
        destroySessionOnMain(reason)
    }

    private fun destroySessionOnMain(reason: String) {
        checkMainThread()
        val view = webView ?: return
        val dropped = synchronized(lock) {
            workerReady = false
            if (sessionFailure == null) sessionFailure = reason
            readyLatch.countDown()
            pending.values.filter { it.outcome == null }
        }
        dropped.forEach { it.finish(ParserHostOutcome.Failed(Contract.ERROR_UNAVAILABLE, reason)) }
        nativePort?.let { port -> runCatching { port.close() } }
        nativePort = null
        webView = null
        runCatching {
            view.stopLoading()
            view.webViewClient = WebViewClient()
            view.destroy()
        }
    }

    private fun reclaimIfIdleOnMain() {
        val idle = synchronized(lock) {
            pending.isEmpty() && SystemClock.elapsedRealtime() - lastActivityAt >= IDLE_MILLIS
        }
        if (idle) destroySessionOnMain("The parser page was idle.")
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Parser WebView work must run on the main thread." }
    }

    private inner class ParserWebViewClient(
        private val assetLoader: androidx.webkit.WebViewAssetLoader,
    ) : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
            ParserAssetOrigin.intercept(assetLoader, request)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view === webView && !ParserAssetOrigin.isEntryUri(Uri.parse(url))) {
                view.stopLoading()
                failSessionOnMain("The parser page attempted an unexpected navigation.")
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (ParserAssetOrigin.isEntryUri(Uri.parse(url))) installPortOnMain(view)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view === webView && request.isForMainFrame) failSessionOnMain("The parser page failed to load: ${error.description}")
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
            if (view === webView && request.isForMainFrame) failSessionOnMain("The parser page returned HTTP ${errorResponse.statusCode}")
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            if (view === webView) failSessionOnMain("The parser page encountered an SSL error.")
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            handler.cancel()
        }

        override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
            request.cancel()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view === webView) failSessionOnMain("The parser renderer went away.")
            return true
        }
    }

    private object RejectingChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }
    }

    private companion object {
        const val FRAME_CHARS = 16 * 1024
        const val IDLE_MILLIS = 60_000L
        val KNOWN_ERRORS = setOf(
            Contract.ERROR_INVALID_REQUEST,
            Contract.ERROR_TREE_LIMIT,
            Contract.ERROR_UNAVAILABLE,
        )

        fun Throwable.safeMessage(): String = message?.take(Contract.MAX_ERROR_MESSAGE_CHARS) ?: javaClass.simpleName
    }
}
