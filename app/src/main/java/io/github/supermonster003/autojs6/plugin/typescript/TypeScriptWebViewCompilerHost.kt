package io.github.supermonster003.autojs6.plugin.typescript

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.webkit.ClientCertRequest
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.json.JSONObject

class TypeScriptWebViewCompilerHost(
    context: Context,
) {

    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateLock = Any()
    private val metadataResult = runCatching {
        CompilerDistributionMetadata.load(context)
    }
    private val metadata
        get() = metadataResult.getOrNull()

    @Volatile
    private var currentSnapshot = metadataResult.fold(
        onSuccess = { distribution ->
            CompilerHostSnapshot(
                backend = TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                status = TypeScriptCompilerContract.STATUS_BOOTSTRAP,
                compilerVersion = null,
                distributionFingerprint = distribution.distributionFingerprint,
                declarationsFingerprint = distribution.declarationsFingerprint,
                generatorFingerprint = distribution.generatorFingerprint,
                lastError = null,
            )
        },
        onFailure = { error ->
            CompilerHostSnapshot.failed(
                "Compiler distribution metadata is unavailable: " +
                    error.safeMessage(),
            )
        },
    )

    @Volatile
    private var closed = false

    @Volatile
    private var nativePort: WebMessagePort? = null
    private var pendingProbe: PendingProbe? = null
    private var timeoutRecoveryEpoch = 0
    private var timeoutRecoveryPending = false
    private var timeoutRecoveryAttempted = false

    @Volatile
    private var webView: WebView? = null
    private var webViewSessionGeneration = 0
    private var workerGeneration = 0

    @Volatile
    private var workerReady = false
    private var rendererTerminationsForTesting = 0
    private var serviceWorkerClientInstalled = false

    @Volatile
    private var closeMessagePostedForTesting = false

    @Volatile
    private var lastCloseReportForTesting: CompilerHostCloseReport? = null

    @Volatile
    private var lastCorrelationReportForTesting:
        CompilerHostCorrelationTestReport? = null

    fun snapshot(): CompilerHostSnapshot = currentSnapshot

    internal fun isRendererTerminationSupportedForTesting(): Boolean =
        BuildConfig.DEBUG &&
            WebViewFeature.isFeatureSupported(WebViewFeature.GET_WEB_VIEW_RENDERER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)

    internal fun armRendererTerminationsForTesting(count: Int) {
        checkMainThread()
        check(BuildConfig.DEBUG) {
            "Compiler renderer termination controls are unavailable in release builds."
        }
        require(count >= 0) {
            "Compiler renderer termination count must not be negative."
        }
        rendererTerminationsForTesting = count
    }

    internal fun remainingRendererTerminationsForTesting(): Int {
        checkMainThread()
        return rendererTerminationsForTesting
    }

    internal fun hasPendingProbeForTesting(): Boolean =
        synchronized(stateLock) {
            pendingProbe != null
        }

    internal fun lifecycleTelemetryForTesting(): CompilerHostLifecycleTelemetry {
        check(BuildConfig.DEBUG) {
            "Compiler lifecycle telemetry is unavailable in release builds."
        }
        return CompilerHostLifecycleTelemetry(
            closed = closed,
            hasWebView = webView != null,
            hasNativePort = nativePort != null,
            hasPendingProbe = hasPendingProbeForTesting(),
            workerReady = workerReady,
            closeMessagePosted = closeMessagePostedForTesting,
        )
    }

    internal fun closeReportForTesting(): CompilerHostCloseReport? {
        check(BuildConfig.DEBUG) {
            "Compiler lifecycle telemetry is unavailable in release builds."
        }
        return lastCloseReportForTesting
    }

    internal fun correlationReportForTesting(): CompilerHostCorrelationTestReport? {
        check(BuildConfig.DEBUG) {
            "Compiler correlation telemetry is unavailable in release builds."
        }
        return lastCorrelationReportForTesting
    }

    fun probe(timeoutMs: Long): CompilerHostProbeOutcome =
        probe(
            timeoutMs = timeoutMs,
            dispatchOnMain = true,
            correlationFaultForTesting = null,
        )

    internal fun probeWithWrongRequestIdFailureForTesting(
        timeoutMs: Long,
    ): CompilerHostProbeOutcome {
        check(BuildConfig.DEBUG) {
            "Compiler correlation controls are unavailable in release builds."
        }
        lastCorrelationReportForTesting = null
        return probe(
            timeoutMs = timeoutMs,
            dispatchOnMain = true,
            correlationFaultForTesting =
                CompilerCorrelationFaultForTesting.WRONG_REQUEST_ID_FAILURE,
        )
    }

    internal fun holdProbeUntilCloseForTesting(
        timeoutMs: Long,
    ): CompilerHostProbeOutcome {
        check(BuildConfig.DEBUG) {
            "Compiler lifecycle controls are unavailable in release builds."
        }
        return probe(
            timeoutMs = timeoutMs,
            dispatchOnMain = false,
            correlationFaultForTesting = null,
        )
    }

    private fun probe(
        timeoutMs: Long,
        dispatchOnMain: Boolean,
        correlationFaultForTesting: CompilerCorrelationFaultForTesting?,
    ): CompilerHostProbeOutcome {
        val startedAt = SystemClock.elapsedRealtime()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return CompilerHostProbeOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler probe must not block the WebView main thread.",
                elapsedMs = elapsedSince(startedAt),
            )
        }
        metadataResult.exceptionOrNull()?.let { error ->
            return CompilerHostProbeOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler distribution metadata is unavailable: " +
                    error.safeMessage(),
                elapsedMs = elapsedSince(startedAt),
            )
        }

        val pending = synchronized(stateLock) {
            when {
                closed -> return CompilerHostProbeOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler host has been destroyed.",
                    elapsedMs = elapsedSince(startedAt),
                )
                pendingProbe != null -> {
                    return CompilerHostProbeOutcome.failed(
                        code = TypeScriptCompilerContract.ERROR_BUSY,
                        message = "Compiler host already has an active probe.",
                        elapsedMs = elapsedSince(startedAt),
                    )
                }
                else -> PendingProbe(
                    requestId = UUID.randomUUID().toString(),
                    startedAt = startedAt,
                    correlationFaultForTesting = correlationFaultForTesting,
                ).also { pendingProbe = it }
            }
        }

        if (
            dispatchOnMain &&
            !mainHandler.post {
                ensureSessionAndDispatchOnMain()
            }
        ) {
            completePending(
                pending,
                CompilerHostProbeOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler WebView main thread is unavailable.",
                    elapsedMs = elapsedSince(startedAt),
                ),
            )
        }

        val completed = try {
            pending.completion.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!completed) {
            var terminalCompletionPending = false
            val recoveryEpoch = synchronized(stateLock) {
                if (pendingProbe !== pending || pending.outcome != null) {
                    terminalCompletionPending = pending.outcome != null
                    null
                } else {
                    pendingProbe = null
                    pending.outcome = CompilerHostProbeOutcome.failed(
                        code = TypeScriptCompilerContract.ERROR_TIMEOUT,
                        message = "Compiler Worker probe timed out.",
                        elapsedMs = elapsedSince(startedAt),
                    )
                    // A queued probe can time out before the main thread runs the existing
                    // recovery. That recovery already covers the session; a second runnable
                    // could arrive later and destroy the replacement session.
                    if (timeoutRecoveryPending) {
                        null
                    } else {
                        timeoutRecoveryPending = true
                        timeoutRecoveryEpoch =
                            if (timeoutRecoveryEpoch == Int.MAX_VALUE) {
                                1
                            } else {
                                timeoutRecoveryEpoch + 1
                            }
                        timeoutRecoveryEpoch
                    }
                }
            }
            if (
                terminalCompletionPending &&
                !pending.completion.awaitUninterruptibly(
                    TERMINAL_PUBLICATION_TIMEOUT_MILLIS,
                )
            ) {
                return CompilerHostProbeOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler probe terminal publication timed out.",
                    elapsedMs = elapsedSince(startedAt),
                )
            }
            if (recoveryEpoch != null) {
                if (
                    !mainHandler.post {
                        recoverAfterTimeoutOnMain(recoveryEpoch)
                    }
                ) {
                    synchronized(stateLock) {
                        if (timeoutRecoveryEpoch == recoveryEpoch) {
                            timeoutRecoveryPending = false
                        }
                    }
                }
            }
        }

        return pending.outcome ?: CompilerHostProbeOutcome.failed(
            code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            message = "Compiler probe ended without a terminal result.",
            elapsedMs = elapsedSince(startedAt),
        )
    }

    internal fun close(): CompilerHostCloseReport {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Compiler host must be destroyed on its WebView main thread."
        }
        val pending = synchronized(stateLock) {
            if (closed) {
                return requireNotNull(lastCloseReportForTesting) {
                    "Closed compiler host has no lifecycle report."
                }
            }
            closed = true
            timeoutRecoveryPending = false
            pendingProbe?.also { probe ->
                probe.outcome = CompilerHostProbeOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler service was destroyed.",
                    elapsedMs = elapsedSince(probe.startedAt),
                )
                pendingProbe = null
            }
        }

        val hadPendingProbe = pending != null
        val hadWebView = webView != null
        val hadNativePort = nativePort != null
        val hadReadyWorker = workerReady
        val hadServiceWorkerClient = serviceWorkerClientInstalled
        val closeMessagePosted =
            nativePort.postMessageSafely(CompilerHostProtocol.closeRequest())
        closeMessagePostedForTesting = closeMessagePosted
        val sessionRelease = destroySessionOnMain()
        val serviceWorkerClientCleared = clearServiceWorkerClientOnMain()

        timeoutRecoveryAttempted = false
        timeoutRecoveryEpoch = 0
        webViewSessionGeneration = 0
        rendererTerminationsForTesting = 0

        val handlerCallbacksCleared = runCatching {
            mainHandler.removeCallbacksAndMessages(null)
        }.isSuccess
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_FAILED,
            lastError = "Compiler service was destroyed.",
        )

        val report = CompilerHostCloseReport(
            hadPendingProbe = hadPendingProbe,
            hadWebView = hadWebView,
            hadNativePort = hadNativePort,
            hadReadyWorker = hadReadyWorker,
            hadServiceWorkerClient = hadServiceWorkerClient,
            closeMessagePosted = closeMessagePosted,
            nativePortClosed = sessionRelease.nativePortClosed,
            webViewDestroyed = sessionRelease.webViewDestroyed,
            serviceWorkerClientCleared = serviceWorkerClientCleared,
            handlerCallbacksCleared = handlerCallbacksCleared,
            closed = closed,
            pendingProbeReleased = synchronized(stateLock) {
                pendingProbe == null
            },
            webViewReleased = webView == null,
            nativePortReleased = nativePort == null,
            workerStateReset = !workerReady && workerGeneration == 0,
            sessionStateReset = webViewSessionGeneration == 0,
            timeoutRecoveryReset = synchronized(stateLock) {
                !timeoutRecoveryPending
            } && !timeoutRecoveryAttempted && timeoutRecoveryEpoch == 0,
        )
        lastCloseReportForTesting = report

        // The waiting Binder transaction must observe a fully released session and a published
        // close report. CountDownLatch also provides the required cross-thread happens-before.
        pending?.completion?.countDown()
        return report
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun createSessionOnMain() {
        checkMainThread()
        val distribution = requireNotNull(metadata)
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_STARTING,
            lastError = null,
        )
        webViewSessionGeneration =
            if (webViewSessionGeneration == Int.MAX_VALUE) {
                1
            } else {
                webViewSessionGeneration + 1
            }
        workerGeneration = 0
        workerReady = false
        timeoutRecoveryAttempted = false

        WebView.setWebContentsDebuggingEnabled(false)
        configureServiceWorkers()
        CookieManager.getInstance().apply {
            setAcceptCookie(false)
        }
        WebStorage.getInstance().deleteAllData()

        val assetLoader = CompilerAssetOrigin.createAssetLoader(
            context = context,
            compilerVersion = distribution.version,
        )
        val createdWebView = WebView(context)
        webView = createdWebView
        createdWebView.settings.apply {
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
        CookieManager.getInstance().setAcceptThirdPartyCookies(createdWebView, false)
        createdWebView.setDownloadListener { _, _, _, _, _ -> Unit }
        createdWebView.webChromeClient = RejectingWebChromeClient
        createdWebView.webViewClient = CompilerWebViewClient(assetLoader)
        createdWebView.loadUrl(CompilerAssetOrigin.ENTRY_URL)
    }

    private fun configureServiceWorkers() {
        ServiceWorkerController.getInstance().apply {
            serviceWorkerWebSettings.apply {
                allowContentAccess = false
                allowFileAccess = false
                blockNetworkLoads = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            setServiceWorkerClient(
                object : ServiceWorkerClient() {
                    override fun shouldInterceptRequest(
                        request: WebResourceRequest,
                    ): WebResourceResponse = CompilerAssetOrigin.forbiddenResponse()
                },
            )
            serviceWorkerClientInstalled = true
        }
    }

    private fun clearServiceWorkerClientOnMain(): Boolean {
        checkMainThread()
        if (!serviceWorkerClientInstalled) return true
        val cleared = runCatching {
            ServiceWorkerController.getInstance().setServiceWorkerClient(null)
        }.isSuccess
        if (cleared) {
            serviceWorkerClientInstalled = false
        }
        return cleared
    }

    private fun ensureSessionAndDispatchOnMain() {
        checkMainThread()
        val mayDispatch = synchronized(stateLock) {
            !closed && pendingProbe != null && !timeoutRecoveryPending
        }
        if (!mayDispatch) return
        if (webView == null) {
            try {
                createSessionOnMain()
            } catch (error: Throwable) {
                failPendingAndSession(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Failed to create the compiler WebView: ${error.safeMessage()}",
                )
                return
            }
        }
        synchronized(stateLock) {
            pendingProbe?.let { pending ->
                if (pending.webViewSessionGeneration == null) {
                    pending.webViewSessionGeneration = webViewSessionGeneration
                }
            }
        }
        dispatchPendingIfReadyOnMain()
    }

    private fun installMessagePortOnMain(view: WebView) {
        checkMainThread()
        if (view !== webView || nativePort != null) return
        val ports = try {
            view.createWebMessageChannel()
        } catch (error: Exception) {
            failPendingAndSession(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Failed to create the compiler message channel: " +
                    error.safeMessage(),
            )
            return
        }
        val native = ports[0]
        val page = ports[1]
        try {
            native.setWebMessageCallback(
                object : WebMessagePort.WebMessageCallback() {
                    override fun onMessage(
                        port: WebMessagePort,
                        message: WebMessage,
                    ) {
                        val data = message.data ?: return
                        mainHandler.post {
                            handlePortMessageOnMain(native, data)
                        }
                    }
                },
            )
            nativePort = native
            view.postWebMessage(
                WebMessage(
                    CompilerAssetOrigin.PORT_MESSAGE,
                    arrayOf(page),
                ),
                Uri.parse(CompilerAssetOrigin.ORIGIN),
            )
        } catch (error: Exception) {
            if (nativePort === native) {
                nativePort = null
            }
            runCatching { native.close() }
            runCatching { page.close() }
            failPendingAndSession(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Failed to install the compiler message channel: " +
                    error.safeMessage(),
            )
        }
    }

    private fun handlePortMessageOnMain(
        sourcePort: WebMessagePort,
        rawMessage: String,
    ) {
        checkMainThread()
        if (closed || webView == null || sourcePort !== nativePort) return
        val message = try {
            CompilerHostProtocol.parseMessage(rawMessage)
        } catch (error: Throwable) {
            failPendingAndSession(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler host protocol violation: ${error.safeMessage()}",
            )
            return
        }
        when (message) {
            is CompilerHostMessage.Ready -> handleWorkerReadyOnMain(message)
            is CompilerHostMessage.ProbeResult -> handleProbeResultOnMain(message)
            is CompilerHostMessage.Failure -> handleWorkerFailureOnMain(message)
        }
    }

    private fun handleWorkerReadyOnMain(message: CompilerHostMessage.Ready) {
        val distribution = requireNotNull(metadata)
        if (message.compilerVersion != distribution.version) {
            failPendingAndSession(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Runtime TypeScript version ${message.compilerVersion} " +
                    "does not match ${distribution.version}.",
            )
            return
        }
        val expectedGeneration = expectedWorkerGeneration()
        if (message.generation < expectedGeneration) return
        if (message.generation > expectedGeneration) {
            failPendingAndSession(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler Worker reported generation ${message.generation}; " +
                    "expected $expectedGeneration.",
            )
            return
        }
        workerGeneration = message.generation
        workerReady = true
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_READY,
            compilerVersion = message.compilerVersion,
            lastError = null,
        )
        dispatchPendingIfReadyOnMain()
    }

    private fun dispatchPendingIfReadyOnMain() {
        checkMainThread()
        val pending = synchronized(stateLock) {
            pendingProbe.takeUnless {
                closed || timeoutRecoveryPending
            }
        } ?: return
        if (!workerReady || pending.workerGeneration != null) return
        val port = nativePort ?: return
        val view = webView ?: return
        pending.workerGeneration = workerGeneration
        injectCorrelationFaultForTestingOnMain(port, pending)
        if (terminateRendererBeforeDispatchForTestingOnMain(view)) return
        if (!port.postMessageSafely(CompilerHostProtocol.probeRequest(pending.requestId))) {
            failPendingAndSession(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Failed to send the compiler probe to the host page.",
            )
        }
    }

    private fun injectCorrelationFaultForTestingOnMain(
        port: WebMessagePort,
        pending: PendingProbe,
    ) {
        checkMainThread()
        val fault = pending.correlationFaultForTesting ?: return
        if (pending.correlationFaultInjected) return
        check(BuildConfig.DEBUG) {
            "Compiler correlation controls are unavailable in release builds."
        }
        pending.correlationFaultInjected = true
        when (fault) {
            CompilerCorrelationFaultForTesting.WRONG_REQUEST_ID_FAILURE -> {
                val generation = requireNotNull(pending.workerGeneration)
                val rawFailure = JSONObject()
                    .put("kind", "probeError")
                    .put("requestId", "${pending.requestId}-wrong")
                    .put("generation", generation)
                    .put("pageHasTypeScript", false)
                    .put("message", "Injected wrong-request correlation fault.")
                    .toString()
                handlePortMessageOnMain(port, rawFailure)
            }
        }
        val ignored = synchronized(stateLock) {
            pendingProbe === pending && pending.outcome == null
        }
        lastCorrelationReportForTesting = CompilerHostCorrelationTestReport(
            faultInjected = true,
            faultIgnored = ignored,
        )
    }

    private fun handleProbeResultOnMain(message: CompilerHostMessage.ProbeResult) {
        val pending = synchronized(stateLock) { pendingProbe } ?: return
        if (
            message.requestId != pending.requestId ||
            message.generation != pending.workerGeneration
        ) {
            return
        }
        timeoutRecoveryAttempted = false
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_READY,
            compilerVersion = message.evidence.compilerVersion,
            lastError = null,
        )
        completePending(
            pending,
            CompilerHostProbeOutcome(
                succeeded = true,
                errorCode = null,
                errorMessage = null,
                elapsedMs = elapsedSince(pending.startedAt),
                evidence = message.evidence,
            ),
        )
    }

    private fun handleWorkerFailureOnMain(message: CompilerHostMessage.Failure) {
        val pending = synchronized(stateLock) { pendingProbe }
        if (message.requestId != null) {
            if (
                pending == null ||
                message.requestId != pending.requestId ||
                message.generation != pending.workerGeneration
            ) {
                return
            }
        } else if (message.generation != expectedWorkerGeneration()) {
            return
        }
        failPendingAndSession(
            code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            message = "Compiler Worker failed: ${message.message}",
        )
    }

    private fun recoverAfterTimeoutOnMain(recoveryEpoch: Int) {
        checkMainThread()
        val isCurrentRecovery = synchronized(stateLock) {
            timeoutRecoveryPending && timeoutRecoveryEpoch == recoveryEpoch
        }
        if (!isCurrentRecovery) return
        try {
            if (closed) return
            workerReady = false
            currentSnapshot = currentSnapshot.copy(
                status = TypeScriptCompilerContract.STATUS_DEGRADED,
                lastError = "Compiler Worker probe timed out.",
            )
            if (!timeoutRecoveryAttempted && nativePort != null) {
                timeoutRecoveryAttempted = true
                val nextGeneration = expectedWorkerGeneration() + 1
                workerGeneration = nextGeneration
                if (nativePort.postMessageSafely(CompilerHostProtocol.restartWorkerRequest())) {
                    currentSnapshot = currentSnapshot.copy(
                        status = TypeScriptCompilerContract.STATUS_STARTING,
                    )
                    return
                }
            }
            destroySessionOnMain()
            timeoutRecoveryAttempted = false
        } finally {
            val dispatchWaitingProbe = synchronized(stateLock) {
                if (
                    !timeoutRecoveryPending ||
                    timeoutRecoveryEpoch != recoveryEpoch
                ) {
                    false
                } else {
                    timeoutRecoveryPending = false
                    !closed && pendingProbe != null
                }
            }
            if (dispatchWaitingProbe) {
                ensureSessionAndDispatchOnMain()
            }
        }
    }

    private fun handleRendererGoneOnMain(view: WebView) {
        checkMainThread()
        if (view !== webView || closed) return
        val deadSessionGeneration = webViewSessionGeneration
        val pending = synchronized(stateLock) {
            pendingProbe?.takeIf { probe ->
                probe.webViewSessionGeneration == deadSessionGeneration
            }
        }
        val mayRetry = pending != null && pending.rendererRetryCount < 1
        if (mayRetry) {
            requireNotNull(pending).rendererRetryCount += 1
            pending.webViewSessionGeneration = null
            pending.workerGeneration = null
        }
        destroySessionOnMain()
        if (mayRetry) {
            ensureSessionAndDispatchOnMain()
        } else {
            pending?.let { failed ->
                completePending(
                    failed,
                    CompilerHostProbeOutcome.failed(
                        code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                        message = "Compiler WebView renderer exited.",
                        elapsedMs = elapsedSince(failed.startedAt),
                    ),
                )
            }
            currentSnapshot = currentSnapshot.copy(
                status = TypeScriptCompilerContract.STATUS_FAILED,
                lastError = "Compiler WebView renderer exited.",
            )
        }
    }

    private fun failPendingAndSession(
        code: String,
        message: String,
    ) {
        failPending(code, message)
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_FAILED,
            lastError = message,
        )
        destroySessionOnMain()
    }

    private fun failPending(
        code: String,
        message: String,
    ) {
        val pending = synchronized(stateLock) { pendingProbe } ?: return
        completePending(
            pending,
            CompilerHostProbeOutcome.failed(
                code = code,
                message = message,
                elapsedMs = elapsedSince(pending.startedAt),
            ),
        )
    }

    private fun completePending(
        pending: PendingProbe,
        outcome: CompilerHostProbeOutcome,
    ) {
        val completed = synchronized(stateLock) {
            if (pendingProbe !== pending || pending.outcome != null) {
                false
            } else {
                pending.outcome = outcome
                pendingProbe = null
                true
            }
        }
        if (completed) {
            pending.completion.countDown()
        }
    }

    private fun destroySessionOnMain(): CompilerSessionRelease {
        checkMainThread()
        val destroyedSessionGeneration = webViewSessionGeneration
        synchronized(stateLock) {
            pendingProbe
                ?.takeIf { pending ->
                    pending.webViewSessionGeneration == destroyedSessionGeneration
                }
                ?.let { pending ->
                    pending.webViewSessionGeneration = null
                    pending.workerGeneration = null
                }
        }
        workerReady = false
        workerGeneration = 0

        val port = nativePort
        nativePort = null
        val view = webView
        webView = null

        val nativePortClosed =
            port == null || runCatching { port.close() }.isSuccess
        val webViewDestroyed = if (view == null) {
            true
        } else {
            runCatching { view.stopLoading() }
            runCatching { view.clearHistory() }
            runCatching { view.clearCache(true) }
            runCatching { view.setDownloadListener(null) }
            runCatching { view.webChromeClient = null }
            runCatching { view.webViewClient = WebViewClient() }
            runCatching { view.removeAllViews() }
            runCatching { view.destroy() }.isSuccess
        }
        return CompilerSessionRelease(
            nativePortClosed = nativePortClosed,
            webViewDestroyed = webViewDestroyed,
        )
    }

    private fun terminateRendererBeforeDispatchForTestingOnMain(view: WebView): Boolean {
        checkMainThread()
        if (
            !BuildConfig.DEBUG ||
            rendererTerminationsForTesting <= 0 ||
            !isRendererTerminationSupportedForTesting()
        ) {
            return false
        }
        val renderer = WebViewCompat.getWebViewRenderProcess(view) ?: return false
        if (!renderer.terminate()) return false
        rendererTerminationsForTesting -= 1
        return true
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Compiler WebView lifecycle escaped the main thread."
        }
    }

    private fun WebMessagePort?.postMessageSafely(message: String): Boolean =
        runCatching {
            requireNotNull(this).postMessage(WebMessage(message))
        }.isSuccess

    private fun expectedWorkerGeneration(): Int =
        workerGeneration.takeIf { it > 0 } ?: 1

    private fun elapsedSince(startedAt: Long): Long =
        (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)

    private fun CountDownLatch.awaitUninterruptibly(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var interrupted = false
        try {
            while (count != 0L) {
                val remaining =
                    (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                if (remaining == 0L) return false
                try {
                    if (await(remaining, TimeUnit.MILLISECONDS)) return true
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
            return true
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private inner class CompilerWebViewClient(
        private val assetLoader: androidx.webkit.WebViewAssetLoader,
    ) : WebViewClient() {

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse = CompilerAssetOrigin.intercept(assetLoader, request)

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean = true

        override fun onPageStarted(
            view: WebView,
            url: String,
            favicon: Bitmap?,
        ) {
            if (view !== webView) return
            if (!CompilerAssetOrigin.isEntryUri(Uri.parse(url))) {
                view.stopLoading()
                failPendingAndSession(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler WebView attempted an unexpected navigation.",
                )
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (CompilerAssetOrigin.isEntryUri(Uri.parse(url))) {
                installMessagePortOnMain(view)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (view !== webView) return
            if (request.isForMainFrame) {
                failPendingAndSession(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler host page failed to load: ${error.description}",
                )
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (view !== webView) return
            if (request.isForMainFrame) {
                failPendingAndSession(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler host page returned HTTP " +
                        errorResponse.statusCode,
                )
            }
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: android.webkit.SslErrorHandler,
            error: SslError,
        ) {
            handler.cancel()
            if (view !== webView) return
            failPendingAndSession(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler host page encountered an SSL error.",
            )
        }

        override fun onReceivedHttpAuthRequest(
            view: WebView,
            handler: HttpAuthHandler,
            host: String,
            realm: String,
        ) {
            handler.cancel()
        }

        override fun onReceivedClientCertRequest(
            view: WebView,
            request: ClientCertRequest,
        ) {
            request.cancel()
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean {
            handleRendererGoneOnMain(view)
            return true
        }
    }

    private object RejectingWebChromeClient : WebChromeClient() {

        override fun onPermissionRequest(request: PermissionRequest) {
            request.deny()
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback,
        ) {
            callback.invoke(origin, false, false)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            filePathCallback.onReceiveValue(null)
            return true
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean = false

        override fun onJsAlert(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsConfirm(
            view: WebView,
            url: String,
            message: String,
            result: JsResult,
        ): Boolean {
            result.cancel()
            return true
        }

        override fun onJsPrompt(
            view: WebView,
            url: String,
            message: String,
            defaultValue: String,
            result: JsPromptResult,
        ): Boolean {
            result.cancel()
            return true
        }
    }

    private data class CompilerSessionRelease(
        val nativePortClosed: Boolean,
        val webViewDestroyed: Boolean,
    )

    private data class PendingProbe(
        val requestId: String,
        val startedAt: Long,
        val correlationFaultForTesting: CompilerCorrelationFaultForTesting?,
        val completion: CountDownLatch = CountDownLatch(1),
        var outcome: CompilerHostProbeOutcome? = null,
        var rendererRetryCount: Int = 0,
        var webViewSessionGeneration: Int? = null,
        var workerGeneration: Int? = null,
        var correlationFaultInjected: Boolean = false,
    )

    private enum class CompilerCorrelationFaultForTesting {
        WRONG_REQUEST_ID_FAILURE,
    }

    companion object {

        private const val TERMINAL_PUBLICATION_TIMEOUT_MILLIS = 5_000L

        @Volatile
        private var compilerProcessPrepared = false

        @Synchronized
        fun prepareCompilerProcess() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "WebView process configuration must run on the main thread."
            }
            if (compilerProcessPrepared) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WebView.setDataDirectorySuffix("typescript_compiler")
            }
            compilerProcessPrepared = true
        }

        private fun Throwable.safeMessage(): String =
            message?.take(4_096) ?: javaClass.simpleName
    }
}

data class CompilerHostSnapshot(
    val backend: String,
    val status: String,
    val compilerVersion: String?,
    val distributionFingerprint: String?,
    val declarationsFingerprint: String?,
    val generatorFingerprint: String?,
    val lastError: String?,
) {

    companion object {
        fun failed(message: String) = CompilerHostSnapshot(
            backend = TypeScriptCompilerContract.BACKEND_UNINITIALIZED,
            status = TypeScriptCompilerContract.STATUS_FAILED,
            compilerVersion = null,
            distributionFingerprint = null,
            declarationsFingerprint = null,
            generatorFingerprint = null,
            lastError = message,
        )
    }
}

data class CompilerHostProbeOutcome(
    val succeeded: Boolean,
    val errorCode: String?,
    val errorMessage: String?,
    val elapsedMs: Long,
    val evidence: CompilerProbeEvidence?,
) {

    companion object {
        fun failed(
            code: String,
            message: String,
            elapsedMs: Long,
        ) = CompilerHostProbeOutcome(
            succeeded = false,
            errorCode = code,
            errorMessage = message.take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS),
            elapsedMs = elapsedMs,
            evidence = null,
        )
    }
}

private fun Throwable.safeMessage(): String =
    message?.take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS)
        ?: javaClass.simpleName
