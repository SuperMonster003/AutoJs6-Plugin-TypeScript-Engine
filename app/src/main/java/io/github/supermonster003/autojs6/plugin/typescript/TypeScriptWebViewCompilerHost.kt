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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

class TypeScriptWebViewCompilerHost(
    context: Context,
) {

    private val context = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val protocolExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(PROTOCOL_VERIFICATION_QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "typescript-compiler-protocol").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val resultExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(RESULT_VERIFICATION_QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "typescript-compiler-result").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val inputFrameExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(INPUT_FRAME_QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "typescript-compiler-input").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )
    private val stateLock = Any()
    private val metadataResult = runCatching {
        CompilerDistributionMetadata.load(context)
    }
    private val metadata
        get() = metadataResult.getOrNull()
    private val declarationProfileFingerprintResult = runCatching {
        R2TrustedDeclarations.loadForProfile(
            context,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profile,
        ).profileFingerprint
    }

    @Volatile
    private var currentSnapshot = metadataResult.fold(
        onSuccess = { distribution ->
            CompilerHostSnapshot(
                backend = TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                status = TypeScriptCompilerContract.STATUS_BOOTSTRAP,
                compilerVersion = null,
                distributionFingerprint = distribution.distributionFingerprint,
                declarationsFingerprint = distribution.declarationsFingerprint,
                declarationProfileFingerprint =
                    declarationProfileFingerprintResult.getOrNull(),
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
    private var resultFinalizersInFlight = 0
    private var resultFinalizerRunningForTesting = false
    private var resultFinalizerDelayMillisecondsForTesting = 0
    private var armedResultFinalizerGateForTesting: CountDownLatch? = null
    private var activeResultFinalizerGateForTesting: CountDownLatch? = null
    private var timeoutRecoveryEpoch = 0
    private var timeoutRecoveryPending = false
    private var timeoutRecoveryAttempted = false
    private var fatalTeardownPending = false
    private var externalTeardownReported = false
    private var externalTeardownObserver: (() -> Unit)? = null

    @Volatile
    private var webView: WebView? = null
    @Volatile
    private var currentSessionToken: CompilerSessionToken? = null
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

    @Volatile
    private var lastTerminatedGenerationForTestingValue: Int? = null

    @Volatile
    private var lastRetiredSessionGenerationForTestingValue: Int? = null

    @Volatile
    private var lastProjectInputTransportTelemetryForTestingValue:
        R2ProjectInputTransportTelemetry? = null

    @Volatile
    private var activeProjectInputTransportTelemetryForTestingValue:
        R2ProjectInputTransportTelemetry? = null

    @Volatile
    private var lastRendererGoneEvidenceForTestingValue:
        R2RendererGoneEvidence? = null

    @Volatile
    private var lastProjectPhaseTerminationEvidenceForTestingValue:
        R2ProjectPhaseTerminationEvidence? = null

    @Volatile
    private var lastProjectExecutionPhaseProgressHistoryForTestingValue:
        List<R2ProjectExecutionPhaseProgress> = emptyList()

    @Volatile
    private var lastProjectExecutionPhaseTimingEvidenceForTestingValue:
        List<R2ProjectExecutionPhaseTimingEvidence> = emptyList()

    @Volatile
    private var lastProjectCompilationEvidenceSnapshotValue:
        R2ProjectCompilationEvidenceSnapshot? = null

    fun snapshot(): CompilerHostSnapshot = currentSnapshot

    internal fun installExternalTeardownObserver(observer: () -> Unit) {
        val notifyImmediately = synchronized(stateLock) {
            check(externalTeardownObserver == null) {
                "Compiler external-teardown observer is already installed."
            }
            externalTeardownObserver = observer
            fatalTeardownPending && !externalTeardownReported
        }
        if (notifyImmediately) {
            notifyExternalTeardownObserver()
        }
    }

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

    internal fun projectExecutionPhaseForTesting(
        handle: R2HostProjectOperationHandle,
    ): R2ProjectExecutionPhase? {
        check(BuildConfig.DEBUG) {
            "Project execution phase telemetry is unavailable in release builds."
        }
        return synchronized(stateLock) {
            pendingProbe
                ?.takeIf { pending -> pending.operationHandle === handle }
                ?.activeProjectExecutionPhase
        }
    }

    internal fun projectExecutionPhaseHistoryForTesting(
        handle: R2HostProjectOperationHandle,
    ): List<R2ProjectExecutionPhase> {
        check(BuildConfig.DEBUG) {
            "Project execution phase telemetry is unavailable in release builds."
        }
        return synchronized(stateLock) {
            pendingProbe
                ?.takeIf { pending -> pending.operationHandle === handle }
                ?.enteredProjectExecutionPhases
                ?.toList()
                .orEmpty()
        }
    }

    internal fun projectExecutionPhaseProgressForTesting(
        handle: R2HostProjectOperationHandle,
    ): R2ProjectExecutionPhaseProgress? {
        check(BuildConfig.DEBUG) {
            "Project execution progress telemetry is unavailable in release builds."
        }
        return synchronized(stateLock) {
            pendingProbe
                ?.takeIf { pending -> pending.operationHandle === handle }
                ?.activeProjectExecutionPhaseProgress
        }
    }

    internal fun lastProjectPhaseTerminationEvidenceForTesting():
        R2ProjectPhaseTerminationEvidence? {
        check(BuildConfig.DEBUG) {
            "Project phase termination evidence is unavailable in release builds."
        }
        return lastProjectPhaseTerminationEvidenceForTestingValue
    }

    internal fun lastProjectExecutionPhaseProgressHistoryForTesting():
        List<R2ProjectExecutionPhaseProgress> {
        check(BuildConfig.DEBUG) {
            "Project execution progress history is unavailable in release builds."
        }
        return lastProjectExecutionPhaseProgressHistoryForTestingValue
    }

    internal fun lastProjectExecutionPhaseTimingEvidenceForTesting():
        List<R2ProjectExecutionPhaseTimingEvidence> {
        check(BuildConfig.DEBUG) {
            "Project execution timing evidence is unavailable in release builds."
        }
        return lastProjectExecutionPhaseTimingEvidenceForTestingValue
    }

    internal fun lastProjectCompilationEvidenceSnapshot():
        R2ProjectCompilationEvidenceSnapshot? {
        check(BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
            "External project compilation evidence is unavailable in this build."
        }
        return lastProjectCompilationEvidenceSnapshotValue
    }

    internal fun terminateActiveProjectRendererForTesting(
        handle: R2HostProjectOperationHandle,
        expectedPhase: R2ProjectExecutionPhase,
    ): Boolean {
        checkMainThread()
        check(BuildConfig.DEBUG) {
            "Active project renderer termination is unavailable in release builds."
        }
        if (!isRendererTerminationSupportedForTesting()) return false
        val view = webView ?: return false
        val renderer = WebViewCompat.getWebViewRenderProcess(view) ?: return false
        val pending = synchronized(stateLock) {
            val token = currentSessionToken
            pendingProbe?.takeIf { candidate ->
                candidate.operationHandle === handle &&
                    candidate.activeProjectExecutionPhase == expectedPhase &&
                    candidate.stopCause == null &&
                    candidate.projectOutcome == null &&
                    candidate.rendererTerminationRequestedAtPhase == null &&
                    candidate.sessionToken === token &&
                    token?.webView === view &&
                    webView === view
            }?.also { candidate ->
                candidate.rendererTerminationRequestedAtPhase = expectedPhase
            }
        } ?: return false
        lastRendererGoneEvidenceForTestingValue = null
        if (!renderer.terminate()) {
            synchronized(stateLock) {
                if (
                    pendingProbe === pending &&
                    pending.rendererTerminationRequestedAtPhase == expectedPhase
                ) {
                    pending.rendererTerminationRequestedAtPhase = null
                }
            }
            return false
        }
        return true
    }

    internal fun lastRendererGoneEvidenceForTesting(): R2RendererGoneEvidence? {
        check(BuildConfig.DEBUG) {
            "Renderer-gone evidence is unavailable in release builds."
        }
        return lastRendererGoneEvidenceForTestingValue
    }

    internal fun hasPendingProbeForTesting(): Boolean =
        synchronized(stateLock) {
            pendingProbe != null
        }

    internal fun armResultFinalizerDelayForTesting(delayMilliseconds: Int) {
        checkMainThread()
        check(BuildConfig.DEBUG) {
            "Compiler result finalizer controls are unavailable in release builds."
        }
        require(
            delayMilliseconds in
                0..MAX_RESULT_FINALIZER_DELAY_MILLISECONDS_FOR_TESTING,
        ) {
            "Compiler result finalizer delay is outside the test limit."
        }
        synchronized(stateLock) {
            check(resultFinalizersInFlight == 0) {
                "A compiler result finalizer is already active."
            }
            check(
                armedResultFinalizerGateForTesting == null &&
                    activeResultFinalizerGateForTesting == null,
            ) {
                "A compiler result finalizer gate is already armed."
            }
            resultFinalizerDelayMillisecondsForTesting = delayMilliseconds
            resultFinalizerRunningForTesting = false
        }
    }

    internal fun armResultFinalizerGateForTesting() {
        checkMainThread()
        check(BuildConfig.DEBUG) {
            "Compiler result finalizer controls are unavailable in release builds."
        }
        synchronized(stateLock) {
            check(resultFinalizersInFlight == 0) {
                "A compiler result finalizer is already active."
            }
            check(resultFinalizerDelayMillisecondsForTesting == 0) {
                "A compiler result finalizer delay is already armed."
            }
            check(
                armedResultFinalizerGateForTesting == null &&
                    activeResultFinalizerGateForTesting == null,
            ) {
                "A compiler result finalizer gate is already armed."
            }
            armedResultFinalizerGateForTesting = CountDownLatch(1)
            resultFinalizerRunningForTesting = false
        }
    }

    internal fun releaseResultFinalizerGateForTesting() {
        check(BuildConfig.DEBUG) {
            "Compiler result finalizer controls are unavailable in release builds."
        }
        val gates = synchronized(stateLock) {
            listOfNotNull(
                armedResultFinalizerGateForTesting,
                activeResultFinalizerGateForTesting,
            ).also {
                armedResultFinalizerGateForTesting = null
            }
        }
        gates.forEach(CountDownLatch::countDown)
    }

    internal fun isResultFinalizerRunningForTesting(): Boolean {
        check(BuildConfig.DEBUG) {
            "Compiler result finalizer telemetry is unavailable in release builds."
        }
        return synchronized(stateLock) {
            resultFinalizerRunningForTesting && resultFinalizersInFlight > 0
        }
    }

    internal fun isProjectStopTeardownProvenForTesting(
        handle: R2HostProjectOperationHandle,
    ): Boolean {
        check(BuildConfig.DEBUG) {
            "Compiler project-stop telemetry is unavailable in release builds."
        }
        return synchronized(stateLock) {
            pendingProbe?.let { pending ->
                pending.operationHandle === handle &&
                    pending.stopCause != null &&
                    pending.stopTeardownProven
            } == true
        }
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

    internal fun compileProject(
        snapshot: R2ProjectInputSnapshot,
        timeoutMs: Long,
    ): R2ProjectCompilationOutcome =
        compileProject(
            handle = newProjectOperationHandle(),
            snapshot = snapshot,
            timeoutMs = timeoutMs,
            dispatchOnMain = true,
            workerSpinMilliseconds = 0,
            workerSpinPhase = null,
        )

    internal fun newProjectOperationHandle(): R2HostProjectOperationHandle =
        R2HostProjectOperationHandle.create()

    internal fun compileProject(
        handle: R2HostProjectOperationHandle,
        snapshot: R2ProjectInputSnapshot,
        timeoutMs: Long,
    ): R2ProjectCompilationOutcome =
        compileProject(
            handle = handle,
            snapshot = snapshot,
            timeoutMs = timeoutMs,
            dispatchOnMain = true,
            workerSpinMilliseconds = 0,
            workerSpinPhase = null,
        )

    /**
     * Production coordinator seam that preserves its irreversible-admission absolute deadline.
     *
     * Reconstructing a deadline from a millisecond remainder can move the host deadline across the
     * coordinator timer by one scheduling tick. Passing the original monotonic nanosecond value
     * keeps stop selection and phase-termination evidence on the same clock boundary.
     */
    internal fun compileProjectUntilElapsedRealtimeDeadline(
        handle: R2HostProjectOperationHandle,
        snapshot: R2ProjectInputSnapshot,
        deadlineAtElapsedRealtimeNanos: Long,
        projectExecutionObserver: ((R2ProjectExecutionEvent) -> Unit)? = null,
    ): R2ProjectCompilationOutcome {
        val remainingNanos =
            deadlineAtElapsedRealtimeNanos -
                SystemClock.elapsedRealtimeNanos()
        val timeoutMs =
            if (remainingNanos <= 0L) {
                1L
            } else {
                Math.addExact(
                    remainingNanos,
                    TimeUnit.MILLISECONDS.toNanos(1L) - 1L,
                ) / TimeUnit.MILLISECONDS.toNanos(1L)
            }
        return compileProject(
            handle = handle,
            snapshot = snapshot,
            timeoutMs = timeoutMs,
            dispatchOnMain = true,
            workerSpinMilliseconds = 0,
            workerSpinPhase = null,
            absoluteDeadlineAtElapsedRealtimeNanos =
                deadlineAtElapsedRealtimeNanos,
            projectExecutionObserver = projectExecutionObserver,
        )
    }

    internal fun compileProjectWithSynchronousWorkerSpinForTesting(
        handle: R2HostProjectOperationHandle,
        snapshot: R2ProjectInputSnapshot,
        timeoutMs: Long,
        workerSpinMilliseconds: Int,
        workerSpinPhase: R2ProjectExecutionPhase =
            R2ProjectExecutionPhase.PARSE,
        projectExecutionObserver: ((R2ProjectExecutionEvent) -> Unit)? = null,
    ): R2ProjectCompilationOutcome {
        check(BuildConfig.DEBUG) {
            "Compiler Worker spin controls are unavailable in release builds."
        }
        return compileProject(
            handle = handle,
            snapshot = snapshot,
            timeoutMs = timeoutMs,
            dispatchOnMain = true,
            workerSpinMilliseconds = workerSpinMilliseconds,
            workerSpinPhase = workerSpinPhase,
            projectExecutionObserver = projectExecutionObserver,
        )
    }

    internal fun compileProjectWithExpandedWorkerCapacityForTesting(
        handle: R2HostProjectOperationHandle,
        snapshot: R2ProjectInputSnapshot,
        timeoutMs: Long,
        workerSpinMilliseconds: Int = 0,
        workerSpinPhase: R2ProjectExecutionPhase? = null,
        projectExecutionObserver: ((R2ProjectExecutionEvent) -> Unit)? = null,
    ): R2ProjectCompilationOutcome {
        check(
            BuildConfig.DEBUG ||
                BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE,
        ) {
            "Expanded compiler Worker capacity is unavailable outside debug/evidence builds."
        }
        return compileProject(
            handle = handle,
            snapshot = snapshot,
            timeoutMs = timeoutMs,
            dispatchOnMain = true,
            workerSpinMilliseconds = workerSpinMilliseconds,
            workerSpinPhase = workerSpinPhase,
            workerAdmissionLimits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
            workerInputQuotaAuthority = R2WorkerInputQuotaAuthority.TEST_ONLY_EXPANDED,
            projectExecutionObserver = projectExecutionObserver,
        )
    }

    internal fun requestStop(
        handle: R2HostProjectOperationHandle,
        cause: R2ProjectStopCause,
    ): Boolean {
        if (!handle.requestStop(cause)) return false
        var mayCompleteWithoutMainThread = false
        val pending = synchronized(stateLock) {
            pendingProbe?.takeIf { candidate ->
                candidate.operationHandle === handle &&
                    candidate.projectSnapshot != null &&
                    candidate.projectOutcome == null
            }?.also { candidate ->
                if (candidate.stopCause == null) {
                    selectAcceptedProjectStopLocked(candidate)
                }
                mayCompleteWithoutMainThread = !candidate.operationPosted
            }
        } ?: return true
        if (mayCompleteWithoutMainThread) {
            completeStoppedProjectBeforeDispatch(pending)
            return true
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            stopProjectOnMain(pending)
        } else {
            // Failure to enqueue cannot revoke terminal intent. Without an exact
            // teardown proof the active slot remains retained for process-level
            // supervision instead of publishing a synthetic terminal result.
            if (!mainHandler.post { stopProjectOnMain(pending) }) {
                markExternalTeardownRequired()
            }
        }
        return true
    }

    private fun selectAcceptedProjectStopLocked(
        pending: PendingProbe,
    ): R2ProjectStopCause {
        val handle = requireNotNull(pending.operationHandle)
        val acceptedCause = requireNotNull(handle.acceptedStopCause())
        check(pending.stopCause == null) {
            "Project stop selection was already recorded."
        }
        val stopSelectedAtElapsedRealtimeNanos =
            SystemClock.elapsedRealtimeNanos()
        pending.stopCause = acceptedCause
        pending.projectStopSelection = ProjectStopSelection(
            requestId = pending.requestId,
            stopCause = acceptedCause,
            sessionGeneration = pending.webViewSessionGeneration,
            workerGeneration = pending.workerGeneration,
            activePhase = pending.activeProjectExecutionPhase,
            activeProgress = pending.activeProjectExecutionPhaseProgress,
            enteredPhases = pending.enteredProjectExecutionPhases.toList(),
            completedPhases =
                pending.completedProjectExecutionPhases.toList(),
            deadlineAtElapsedRealtimeMillis = pending.deadlineAt,
            deadlineAtElapsedRealtimeNanos =
                pending.deadlineAtElapsedRealtimeNanos,
            stopSelectedAtElapsedRealtimeMillis =
                TimeUnit.NANOSECONDS.toMillis(
                    stopSelectedAtElapsedRealtimeNanos,
                ),
            stopSelectedAtElapsedRealtimeNanos =
                stopSelectedAtElapsedRealtimeNanos,
        )
        return acceptedCause
    }

    private fun completeStoppedProjectBeforeDispatch(pending: PendingProbe) {
        val completed = synchronized(stateLock) {
            if (
                pendingProbe !== pending ||
                pending.operationPosted ||
                pending.projectOutcome != null ||
                pending.stopCause == null
            ) {
                false
            } else {
                val cause = pending.operationHandle?.completeStopped()
                    ?: return@synchronized false
                pending.projectOutcome = R2ProjectCompilationOutcome.failed(
                    code = stopErrorCode(cause),
                    message = stopMessage(cause),
                    elapsedMs = elapsedSince(pending.startedAt),
                )
                pendingProbe = null
                true
            }
        }
        if (completed) {
            if (!releaseProjectInputOrPoison(pending.projectInputFramer)) {
                markProjectInputReleaseUnproven(pending)
            }
            pending.projectResultAccumulator?.release()
            pending.projectResultAccumulator = null
            pending.completion.countDown()
        }
    }

    internal fun isProjectSynchronousWorkStartedForTesting(
        handle: R2HostProjectOperationHandle,
    ): Boolean = synchronized(stateLock) {
            pendingProbe?.let { pending ->
                pending.operationHandle === handle &&
                    pending.projectInputComplete &&
                    pending.operationPosted &&
                    pending.stopCause == null
            } == true
    }

    internal fun lastTerminatedWorkerGenerationForTesting(): Int? =
        lastTerminatedGenerationForTestingValue

    internal fun lastRetiredSessionGenerationForTesting(): Int? {
        check(BuildConfig.DEBUG) {
            "Compiler session-retirement telemetry is unavailable in release builds."
        }
        return lastRetiredSessionGenerationForTestingValue
    }

    internal fun lastProjectInputTransportTelemetryForTesting():
        R2ProjectInputTransportTelemetry? {
        check(BuildConfig.DEBUG) {
            "Project input transport telemetry is unavailable in release builds."
        }
        return lastProjectInputTransportTelemetryForTestingValue
    }

    internal fun activeProjectInputTransportTelemetryForTesting(
        handle: R2HostProjectOperationHandle,
    ): R2ProjectInputTransportTelemetry? {
        check(BuildConfig.DEBUG) {
            "Live project input transport telemetry is unavailable in release builds."
        }
        val exactPending = synchronized(stateLock) {
            pendingProbe?.let { pending ->
                pending.operationHandle === handle &&
                    pending.projectOutcome == null &&
                    pending.requestId == handle.requestId
            } == true
        }
        if (!exactPending) return null
        return activeProjectInputTransportTelemetryForTestingValue
            ?.takeIf { telemetry -> telemetry.requestId == handle.requestId }
    }

    internal fun holdProjectUntilCloseForTesting(
        snapshot: R2ProjectInputSnapshot,
        timeoutMs: Long,
    ): R2ProjectCompilationOutcome {
        check(BuildConfig.DEBUG) {
            "Compiler lifecycle controls are unavailable in release builds."
        }
        return compileProject(
            handle = newProjectOperationHandle(),
            snapshot = snapshot,
            timeoutMs = timeoutMs,
            dispatchOnMain = false,
            workerSpinMilliseconds = 0,
            workerSpinPhase = null,
        )
    }

    private fun compileProject(
        handle: R2HostProjectOperationHandle,
        snapshot: R2ProjectInputSnapshot,
        timeoutMs: Long,
        dispatchOnMain: Boolean,
        workerSpinMilliseconds: Int,
        workerSpinPhase: R2ProjectExecutionPhase?,
        workerAdmissionLimits: R2ProjectSnapshotLimits? = null,
        workerInputQuotaAuthority: R2WorkerInputQuotaAuthority? = null,
        absoluteDeadlineAtElapsedRealtimeNanos: Long? = null,
        projectExecutionObserver: ((R2ProjectExecutionEvent) -> Unit)? = null,
    ): R2ProjectCompilationOutcome {
        val startedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        val startedAt =
            TimeUnit.NANOSECONDS.toMillis(startedAtElapsedRealtimeNanos)
        lastProjectInputTransportTelemetryForTestingValue = null
        activeProjectInputTransportTelemetryForTestingValue = null
        lastProjectPhaseTerminationEvidenceForTestingValue = null
        lastProjectExecutionPhaseProgressHistoryForTestingValue = emptyList()
        lastProjectExecutionPhaseTimingEvidenceForTestingValue = emptyList()
        lastProjectCompilationEvidenceSnapshotValue = null
        if (timeoutMs !in 1L..TypeScriptCompilerContract.MAX_TIMEOUT_MS) {
            return R2ProjectCompilationOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Project compilation timeout is outside the supported range.",
                elapsedMs = elapsedSince(startedAt),
            )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return R2ProjectCompilationOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project compilation must not block the WebView main thread.",
                elapsedMs = elapsedSince(startedAt),
            )
        }
        metadataResult.exceptionOrNull()?.let { error ->
            return R2ProjectCompilationOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler distribution metadata is unavailable: " +
                    error.safeMessage(),
                elapsedMs = elapsedSince(startedAt),
            )
        }
        val selectedWorkerInputQuotaAuthority = workerInputQuotaAuthority ?: snapshot
            .acceptedInputAuthority
            ?.inputQuotaRevision
            ?.let(R2WorkerInputQuotaAuthority::ordinaryForRevision)
            ?: R2WorkerInputQuotaAuthority.ORDINARY
        val selectedWorkerAdmissionLimits =
            workerAdmissionLimits ?: selectedWorkerInputQuotaAuthority.callerLimits
        val workerAdmission = R2ProjectSnapshot.ordinaryCompilationAdmission(
            snapshot = snapshot,
            limits = selectedWorkerAdmissionLimits,
        )
        check(
            selectedWorkerAdmissionLimits ==
                selectedWorkerInputQuotaAuthority.callerLimits,
        ) {
            "Host caller admission and Worker quota authorities must be identical."
        }
        workerAdmission.exceededDimension?.let { exceededDimension ->
            return R2ProjectCompilationOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                message = "Project is outside the ordinary compilation input limit: " +
                    exceededDimension.name.lowercase(),
                elapsedMs = elapsedSince(startedAt),
            )
        }

        if (!handle.claim()) {
            return R2ProjectCompilationOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Project operation handle has already been used.",
                elapsedMs = elapsedSince(startedAt),
            )
        }
        val inputFramer = runCatching {
            R2BatchedProjectInputFramer(
                R2ProjectInputFramer(
                    requestId = handle.requestId,
                    snapshot = snapshot,
                    workerSpinMilliseconds = workerSpinMilliseconds,
                    workerSpinPhase = workerSpinPhase,
                    workerInputQuotaAuthority = selectedWorkerInputQuotaAuthority,
                ),
            )
        }.getOrElse { error ->
            return completeUnregisteredProject(
                handle = handle,
                defaultOutcome = R2ProjectCompilationOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                    message = "Project compilation could not be framed: ${error.safeMessage()}",
                    elapsedMs = elapsedSince(startedAt),
                ),
            )
        }
        if (BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
            handle.markProjectInputTransportCreated()
        }
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        val deadlineAtElapsedRealtimeNanos =
            absoluteDeadlineAtElapsedRealtimeNanos
                ?: Math.addExact(
                    startedAtElapsedRealtimeNanos,
                    timeoutNanos,
                )
        val deadlineAt =
            TimeUnit.NANOSECONDS.toMillis(deadlineAtElapsedRealtimeNanos)
        if (
            SystemClock.elapsedRealtimeNanos() >=
            deadlineAtElapsedRealtimeNanos
        ) {
            releaseProjectInputOrPoison(inputFramer)
            if (BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
                handle.recordProjectInputTransportCleanup(
                    inputFramer.telemetryForTesting(),
                )
            }
            handle.requestStop(R2ProjectStopCause.TIMED_OUT)
            return completeUnregisteredProject(
                handle = handle,
                defaultOutcome = R2ProjectCompilationOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_TIMEOUT,
                    message = "Project compilation timed out while preparing the request.",
                    elapsedMs = elapsedSince(startedAt),
                ),
            )
        }

        var registrationFailure: R2ProjectCompilationOutcome? = null
        val pending = synchronized(stateLock) {
            when {
                closed -> {
                    registrationFailure = R2ProjectCompilationOutcome.failed(
                        code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                        message = "Compiler host has been destroyed.",
                        elapsedMs = elapsedSince(startedAt),
                    )
                    null
                }
                fatalTeardownPending -> {
                    registrationFailure = R2ProjectCompilationOutcome.failed(
                        code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                        message =
                            "Compiler process teardown requires external supervision.",
                        elapsedMs = elapsedSince(startedAt),
                    )
                    null
                }
                pendingProbe != null -> {
                    registrationFailure = R2ProjectCompilationOutcome.failed(
                        code = TypeScriptCompilerContract.ERROR_BUSY,
                        message = "Compiler host already has an active operation.",
                        elapsedMs = elapsedSince(startedAt),
                    )
                    null
                }
                else -> PendingProbe(
                    requestId = handle.requestId,
                    startedAt = startedAt,
                    startedAtElapsedRealtimeNanos =
                        startedAtElapsedRealtimeNanos,
                    correlationFaultForTesting = null,
                    encodedRequest = null,
                    projectSnapshot = snapshot,
                    operationHandle = handle,
                    projectInputFramer = inputFramer,
                    deadlineAt = deadlineAt,
                    deadlineAtElapsedRealtimeNanos =
                        deadlineAtElapsedRealtimeNanos,
                    stopCause = handle.acceptedStopCause(),
                    projectExecutionObserver = projectExecutionObserver,
                ).also { pendingProbe = it }
            }
        }
        if (pending == null) {
            releaseProjectInputOrPoison(inputFramer)
            if (BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
                handle.recordProjectInputTransportCleanup(
                    inputFramer.telemetryForTesting(),
                )
            }
            return completeUnregisteredProject(
                handle = handle,
                defaultOutcome = requireNotNull(registrationFailure),
            )
        }

        if (pending.stopCause != null) {
            completeStoppedProjectBeforeDispatch(pending)
        }

        if (
            pending.stopCause == null &&
            dispatchOnMain &&
            !mainHandler.post { ensureSessionAndDispatchOnMain() }
        ) {
            completePendingProject(
                pending,
                R2ProjectCompilationOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler WebView main thread is unavailable.",
                    elapsedMs = elapsedSince(startedAt),
                ),
            )
        }

        val completed =
            pending.completion.awaitUntilElapsedRealtimeNanos(
                deadlineAtElapsedRealtimeNanos,
            )
        if (!completed) {
            requestStop(
                handle = handle,
                cause = R2ProjectStopCause.TIMED_OUT,
            )
            pending.completion.awaitTerminalUninterruptibly()
        }

        lastProjectExecutionPhaseProgressHistoryForTestingValue =
            synchronized(stateLock) {
                pending.projectExecutionPhaseProgressHistory.toList()
            }
        lastProjectExecutionPhaseTimingEvidenceForTestingValue =
            synchronized(stateLock) {
                pending.projectExecutionPhaseTimingEvidence.toList()
            }
        if (synchronized(stateLock) { pending.projectInputReleaseUnproven }) {
            throw R2PrivateInputCleanupUnprovenException(
                "Project input framing cleanup could not be proven before terminal publication.",
            )
        }
        val inputTransportTelemetry = inputFramer.telemetryForTesting()
        lastProjectInputTransportTelemetryForTestingValue =
            inputTransportTelemetry
        if (BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
            lastProjectCompilationEvidenceSnapshotValue =
                R2ProjectCompilationEvidenceSnapshot(
                    requestId = pending.requestId,
                    projectStartedAtElapsedRealtimeNanos =
                        pending.startedAtElapsedRealtimeNanos,
                    phaseProgressHistory =
                        lastProjectExecutionPhaseProgressHistoryForTestingValue,
                    phaseTimingEvidence =
                        lastProjectExecutionPhaseTimingEvidenceForTestingValue,
                    phaseTerminationEvidence =
                        synchronized(stateLock) {
                            pending.projectPhaseTerminationEvidence
                        },
                    inputTransportTelemetry = inputTransportTelemetry,
                    sessionGeneration =
                        synchronized(stateLock) {
                            pending.webViewSessionGeneration
                        },
                    workerGeneration =
                        synchronized(stateLock) {
                            pending.workerGeneration
                        },
                )
        }
        if (BuildConfig.R2_EXTERNAL_CAPACITY_EVIDENCE) {
            handle.recordProjectInputTransportCleanup(inputTransportTelemetry)
        }
        activeProjectInputTransportTelemetryForTestingValue = null
        return requireNotNull(pending.projectOutcome) {
            "Project completion was signalled without a terminal result."
        }
    }

    private fun completeUnregisteredProject(
        handle: R2HostProjectOperationHandle,
        defaultOutcome: R2ProjectCompilationOutcome,
    ): R2ProjectCompilationOutcome =
        handle.completeUnregistered()?.let { cause ->
            R2ProjectCompilationOutcome.failed(
                code = stopErrorCode(cause),
                message = stopMessage(cause),
                elapsedMs = defaultOutcome.elapsedMs,
            )
        } ?: defaultOutcome

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

        val requestId = UUID.randomUUID().toString()
        val encodedRequest = CompilerHostProtocol.probeRequest(requestId)
        val pending = synchronized(stateLock) {
            when {
                closed -> return CompilerHostProbeOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler host has been destroyed.",
                    elapsedMs = elapsedSince(startedAt),
                )
                fatalTeardownPending -> return CompilerHostProbeOutcome.failed(
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Compiler process teardown requires external supervision.",
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
                    requestId = requestId,
                    startedAt = startedAt,
                    correlationFaultForTesting = correlationFaultForTesting,
                    encodedRequest = encodedRequest,
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
                if (probe.projectSnapshot != null) {
                    requireNotNull(probe.operationHandle)
                        .requestStop(R2ProjectStopCause.SERVICE_DESTROYED)
                    probe.stopCause = requireNotNull(
                        probe.operationHandle.acceptedStopCause(),
                    )
                }
            }
        }
        synchronized(stateLock) {
            armedResultFinalizerGateForTesting?.countDown()
            activeResultFinalizerGateForTesting?.countDown()
            armedResultFinalizerGateForTesting = null
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
        protocolExecutor.shutdownNow()
        resultExecutor.shutdown()

        timeoutRecoveryAttempted = false
        timeoutRecoveryEpoch = 0
        rendererTerminationsForTesting = 0

        if (sessionRelease.cleanupProven) {
            webViewSessionGeneration = 0
        }
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_FAILED,
            lastError = "Compiler service was destroyed.",
        )
        val completedPending = if (sessionRelease.cleanupProven) {
            detachClosedPendingAfterTeardownOnMain(pending)
        } else {
            null
        }
        val pendingRetained = synchronized(stateLock) {
            pending != null && pendingProbe === pending
        }
        if (!pendingRetained) {
            inputFrameExecutor.shutdown()
        }
        val executorTerminationDeadline =
            System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(HOST_EXECUTOR_TERMINATION_WAIT_MILLIS)
        val protocolExecutorTerminated =
            awaitExecutorTerminationUntil(protocolExecutor, executorTerminationDeadline)
        val resultExecutorTerminated =
            awaitExecutorTerminationUntil(resultExecutor, executorTerminationDeadline)
        val inputFrameExecutorTerminated =
            inputFrameExecutor.isShutdown &&
                awaitExecutorTerminationUntil(inputFrameExecutor, executorTerminationDeadline)
        val resultFinalizersReleased = synchronized(stateLock) {
            resultFinalizersInFlight == 0
        }
        val handlerCallbacksCleared =
            sessionRelease.cleanupProven &&
                protocolExecutorTerminated &&
                resultExecutorTerminated &&
                inputFrameExecutorTerminated &&
                runCatching {
                    mainHandler.removeCallbacksAndMessages(null)
                }.isSuccess

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
            protocolExecutorTerminated = protocolExecutorTerminated,
            resultExecutorTerminated = resultExecutorTerminated,
            resultFinalizersReleased = resultFinalizersReleased,
        )
        lastCloseReportForTesting = report

        if (sessionRelease.cleanupProven && !pendingRetained) {
            signalClosedPendingAfterTeardown(completedPending)
        } else {
            scheduleClosedSessionTeardownRetryOnMain(pending)
        }
        return report
    }

    private fun detachClosedPendingAfterTeardownOnMain(pending: PendingProbe?): PendingProbe? {
        checkMainThread()
        val candidate = synchronized(stateLock) {
            pending?.takeIf { probe -> pendingProbe === probe }
        } ?: return null
        if (!releaseProjectInputOrPoison(candidate.projectInputFramer)) {
            return null
        }
        candidate.projectResultAccumulator?.release()
        candidate.projectResultAccumulator = null
        return synchronized(stateLock) {
            candidate.takeIf { probe -> pendingProbe === probe }?.also { probe ->
                if (probe.projectSnapshot == null) {
                    probe.outcome = CompilerHostProbeOutcome.failed(
                        code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                        message = "Compiler service was destroyed.",
                        elapsedMs = elapsedSince(probe.startedAt),
                    )
                } else {
                    val cause = probe.operationHandle?.completeStopped()
                        ?: return@synchronized null
                    probe.projectOutcome = R2ProjectCompilationOutcome.failed(
                        code = stopErrorCode(cause),
                        message = stopMessage(cause),
                        elapsedMs = elapsedSince(probe.startedAt),
                    )
                }
                pendingProbe = null
            }
        }
    }

    private fun signalClosedPendingAfterTeardown(completed: PendingProbe?) {
        completed?.completion?.countDown()
    }

    private fun awaitExecutorTerminationUntil(
        executor: ThreadPoolExecutor,
        deadlineNanos: Long,
    ): Boolean {
        if (executor.isTerminated) return true
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0L) return executor.isTerminated
        return try {
            executor.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun releaseProjectInputFramerBlocking(
        framer: R2ProjectInputTransport?,
    ): Boolean {
        framer ?: return true
        val release: Future<*> = try {
            inputFrameExecutor.submit {
                framer.release()
                activeProjectInputTransportTelemetryForTestingValue =
                    framer.telemetryForTesting()
            }
        } catch (_: RejectedExecutionException) {
            return false
        }

        var interrupted = false
        val deadlineNanos =
            System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(PROJECT_INPUT_RELEASE_WAIT_MILLIS)
        try {
            while (true) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) return false
                try {
                    release.get(remainingNanos, TimeUnit.NANOSECONDS)
                    return true
                } catch (_: InterruptedException) {
                    interrupted = true
                } catch (_: ExecutionException) {
                    return false
                } catch (_: TimeoutException) {
                    return false
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun releaseProjectInputOrPoison(framer: R2ProjectInputTransport?): Boolean {
        val released = releaseProjectInputFramerBlocking(framer)
        if (!released) {
            markExternalTeardownRequired()
        }
        return released
    }

    private fun markProjectInputReleaseUnproven(pending: PendingProbe) {
        synchronized(stateLock) {
            pending.projectInputReleaseUnproven = true
        }
    }

    private fun scheduleClosedSessionTeardownRetryOnMain(pending: PendingProbe?) {
        checkMainThread()
        val scheduled = mainHandler.postDelayed(
            {
                if (!closed) return@postDelayed
                val release = destroySessionOnMain()
                if (!release.cleanupProven) {
                    scheduleClosedSessionTeardownRetryOnMain(pending)
                    return@postDelayed
                }
                webViewSessionGeneration = 0
                val completedPending = detachClosedPendingAfterTeardownOnMain(pending)
                val pendingRetained = synchronized(stateLock) {
                    pending != null && pendingProbe === pending
                }
                if (!pendingRetained) {
                    inputFrameExecutor.shutdown()
                }
                val protocolExecutorTerminated = protocolExecutor.isTerminated
                val resultExecutorTerminated = resultExecutor.isTerminated
                val inputFrameExecutorTerminated =
                    inputFrameExecutor.isShutdown &&
                        awaitExecutorTerminationUntil(
                            inputFrameExecutor,
                            System.nanoTime() +
                                TimeUnit.MILLISECONDS.toNanos(
                                    HOST_EXECUTOR_TERMINATION_WAIT_MILLIS,
                                ),
                        )
                val resultFinalizersReleased = synchronized(stateLock) {
                    resultFinalizersInFlight == 0
                }
                val callbacksCleared =
                    protocolExecutorTerminated &&
                        resultExecutorTerminated &&
                        inputFrameExecutorTerminated &&
                        runCatching {
                            mainHandler.removeCallbacksAndMessages(null)
                        }.isSuccess
                lastCloseReportForTesting = lastCloseReportForTesting?.copy(
                    nativePortClosed = release.nativePortClosed,
                    webViewDestroyed = true,
                    handlerCallbacksCleared = callbacksCleared,
                    pendingProbeReleased = synchronized(stateLock) {
                        pendingProbe == null
                    },
                    webViewReleased = webView == null,
                    nativePortReleased = nativePort == null,
                    workerStateReset = !workerReady && workerGeneration == 0,
                    sessionStateReset = true,
                    protocolExecutorTerminated = protocolExecutorTerminated,
                    resultExecutorTerminated = resultExecutorTerminated,
                    resultFinalizersReleased = resultFinalizersReleased,
                )
                if (pendingRetained) {
                    scheduleClosedSessionTeardownRetryOnMain(pending)
                } else {
                    signalClosedPendingAfterTeardown(completedPending)
                }
            },
            SESSION_TEARDOWN_RETRY_MILLIS,
        )
        if (!scheduled) {
            markExternalTeardownRequired()
        }
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
        currentSessionToken = CompilerSessionToken(
            generation = webViewSessionGeneration,
            webView = createdWebView,
        )
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
            !closed &&
                !fatalTeardownPending &&
                pendingProbe != null &&
                !timeoutRecoveryPending
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
                    pending.sessionToken = requireNotNull(currentSessionToken)
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
                            enqueuePortMessageForParsingOnMain(native, data)
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

    private fun enqueuePortMessageForParsingOnMain(
        sourcePort: WebMessagePort,
        rawMessage: String,
    ) {
        checkMainThread()
        if (closed || webView == null || sourcePort !== nativePort) return
        val expectedPending = synchronized(stateLock) { pendingProbe }
        val expectedSessionGeneration = webViewSessionGeneration
        val submitted = runCatching {
            protocolExecutor.execute {
                val parsedMessage = runCatching {
                    CompilerHostProtocol.parseMessage(rawMessage)
                }
                val posted = mainHandler.post {
                    handleParsedPortMessageOnMain(
                        sourcePort = sourcePort,
                        expectedSessionGeneration = expectedSessionGeneration,
                        expectedPending = expectedPending,
                        parsedMessage = parsedMessage,
                    )
                }
                if (!posted) {
                    releaseParsedPortMessage(parsedMessage)
                }
            }
        }.isSuccess
        if (!submitted) {
            failSessionIfPendingUnchanged(
                expectedPending = expectedPending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler protocol verifier is unavailable.",
            )
        }
    }

    private fun handleParsedPortMessageOnMain(
        sourcePort: WebMessagePort,
        expectedSessionGeneration: Int,
        expectedPending: PendingProbe?,
        parsedMessage: Result<CompilerHostMessage>,
    ) {
        checkMainThread()
        if (
            closed ||
            webView == null ||
            sourcePort !== nativePort ||
            expectedSessionGeneration != webViewSessionGeneration
        ) {
            releaseParsedPortMessage(parsedMessage)
            return
        }
        val message = parsedMessage.getOrElse { error ->
            failSessionIfPendingUnchanged(
                expectedPending = expectedPending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler host protocol violation: ${error.safeMessage()}",
            )
            return
        }
        when (message) {
            is CompilerHostMessage.Ready -> handleWorkerReadyOnMain(message)
            is CompilerHostMessage.ProbeResult -> handleProbeResultOnMain(message)
            is CompilerHostMessage.ProjectResult -> handleProjectResultOnMain(message)
            is CompilerHostMessage.ProjectInputAck ->
                handleProjectInputAckOnMain(message)
            is CompilerHostMessage.ProjectConfigError ->
                handleProjectConfigErrorOnMain(message)
            is CompilerHostMessage.ProjectPhase ->
                handleProjectPhaseOnMain(message)
            is CompilerHostMessage.ProjectPhaseProgress ->
                handleProjectPhaseProgressOnMain(message)
            is CompilerHostMessage.ProjectResultStart ->
                handleProjectResultStartOnMain(message)
            is CompilerHostMessage.ProjectResultChunk ->
                handleProjectResultChunkOnMain(message)
            is CompilerHostMessage.ProjectResultEnd ->
                handleProjectResultEndOnMain(message)
            is CompilerHostMessage.WorkerTerminated ->
                handleWorkerTerminatedOnMain(message)
            is CompilerHostMessage.Failure ->
                handleWorkerFailureOnMain(message, expectedPending)
        }
    }

    private fun releaseParsedPortMessage(
        parsedMessage: Result<CompilerHostMessage>,
    ) {
        (parsedMessage.getOrNull() as? CompilerHostMessage.ProjectResultChunk)
            ?.bytes
            ?.fill(0)
    }

    private fun handleWorkerReadyOnMain(message: CompilerHostMessage.Ready) {
        val distribution = requireNotNull(metadata)
        val stoppingGeneration = synchronized(stateLock) {
            pendingProbe
                ?.takeIf { pending ->
                    pending.stopCause != null && pending.terminationRequested
                }
                ?.workerGeneration
        }
        if (stoppingGeneration != null && message.generation <= stoppingGeneration) {
            return
        }
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
            pendingProbe?.takeUnless {
                closed || timeoutRecoveryPending || it.stopCause != null
            }
        } ?: return
        if (!workerReady || pending.workerGeneration != null) return
        val port = nativePort ?: return
        val view = webView ?: return
        val assigned = synchronized(stateLock) {
            if (
                pendingProbe !== pending ||
                closed ||
                timeoutRecoveryPending ||
                pending.stopCause != null ||
                pending.workerGeneration != null
            ) {
                false
            } else {
                pending.workerGeneration = workerGeneration
                true
            }
        }
        if (!assigned) return
        injectCorrelationFaultForTestingOnMain(pending)
        if (terminateRendererBeforeDispatchForTestingOnMain(view)) return
        if (pending.projectInputFramer != null) {
            scheduleProjectInputPreparationOnMain(
                pending = pending,
                acknowledgedSequence = null,
            )
            return
        }
        val encodedRequest = requireNotNull(pending.encodedRequest)
        val posted = synchronized(stateLock) {
            if (
                pendingProbe !== pending ||
                closed ||
                timeoutRecoveryPending ||
                pending.stopCause != null ||
                port !== nativePort ||
                view !== webView
            ) {
                null
            } else {
                port.postMessageSafely(encodedRequest)
            }
        }
        if (posted == false) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Failed to send the compiler operation to the host page.",
            )
        }
    }

    private fun injectCorrelationFaultForTestingOnMain(
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
                handleWorkerFailureOnMain(
                    message = CompilerHostMessage.Failure(
                        requestId = "${pending.requestId}-wrong",
                        generation = generation,
                        message = "Injected wrong-request correlation fault.",
                    ),
                    expectedPending = pending,
                )
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
            pending.projectSnapshot != null ||
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

    private fun handleProjectInputAckOnMain(
        message: CompilerHostMessage.ProjectInputAck,
    ) {
        val pending = currentProjectPending(
            requestId = message.requestId,
            generation = message.generation,
        ) ?: return
        val accepted = runCatching {
            synchronized(stateLock) {
                check(
                    pendingProbe === pending &&
                        pending.stopCause == null &&
                        !pending.projectInputPreparationInFlight &&
                        pending.projectInputExpectedAckSequence == message.sequence
                ) {
                    "Project input acknowledgement is unexpected or out of sequence."
                }
            }
        }.isSuccess
        if (!accepted) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project input acknowledgement is unexpected or out of sequence.",
            )
            return
        }
        scheduleProjectInputPreparationOnMain(
            pending = pending,
            acknowledgedSequence = message.sequence,
        )
    }

    private fun handleProjectPhaseOnMain(
        message: CompilerHostMessage.ProjectPhase,
    ) {
        val pending = currentProjectPending(
            requestId = message.requestId,
            generation = message.generation,
        ) ?: return
        var observedEvent: R2ProjectExecutionEvent.Phase? = null
        val accepted = runCatching {
            synchronized(stateLock) {
                check(pendingProbe === pending && pending.stopCause == null) {
                    "Project execution phase arrived after terminal intent."
                }
                when (message.state) {
                    R2ProjectExecutionPhaseState.STARTED -> {
                        val expected =
                            R2ProjectExecutionPhase.ordered.getOrNull(
                                pending.enteredProjectExecutionPhases.size,
                            )
                        check(
                            pending.activeProjectExecutionPhase == null &&
                                message.phase == expected,
                        ) {
                            "Project execution phase start is unexpected or out of sequence."
                        }
                        pending.activeProjectExecutionPhase = message.phase
                        pending.activeProjectExecutionPhaseProgress = null
                        pending.enteredProjectExecutionPhases += message.phase
                        observedEvent = R2ProjectExecutionEvent.Phase(
                            phase = message.phase,
                            state = message.state,
                            observedAtElapsedRealtimeNanos =
                                SystemClock.elapsedRealtimeNanos(),
                        )
                    }
                    R2ProjectExecutionPhaseState.COMPLETED -> {
                        val expected =
                            R2ProjectExecutionPhase.ordered.getOrNull(
                                pending.completedProjectExecutionPhases.size,
                            )
                        check(
                            pending.activeProjectExecutionPhase == message.phase &&
                                message.phase == expected &&
                                (
                                    message.phase != R2ProjectExecutionPhase.EMIT ||
                                        pending.projectResultAccumulator
                                            ?.hasAcceptedCompleteInventory() == true
                                    ),
                        ) {
                            "Project execution phase completion is unexpected or out of sequence."
                        }
                        pending.activeProjectExecutionPhase = null
                        pending.activeProjectExecutionPhaseProgress = null
                        pending.completedProjectExecutionPhases += message.phase
                        observedEvent = R2ProjectExecutionEvent.Phase(
                            phase = message.phase,
                            state = message.state,
                            observedAtElapsedRealtimeNanos =
                                SystemClock.elapsedRealtimeNanos(),
                        )
                        pending
                            .phaseSequenceTwoObservedAtElapsedRealtimeNanos
                            .remove(message.phase)
                            ?.let { sequenceTwoObservedAt ->
                                val completedAt =
                                    SystemClock.elapsedRealtimeNanos()
                                check(completedAt > sequenceTwoObservedAt) {
                                    "Project phase completion did not follow sequence two."
                                }
                                pending.projectExecutionPhaseTimingEvidence +=
                                    R2ProjectExecutionPhaseTimingEvidence(
                                        phase = message.phase,
                                        sequenceTwoElapsedRealtimeNanosSinceProjectStart =
                                            sequenceTwoObservedAt -
                                                pending.startedAtElapsedRealtimeNanos,
                                        completedElapsedRealtimeNanosSinceProjectStart =
                                            completedAt -
                                                pending.startedAtElapsedRealtimeNanos,
                                    )
                            }
                    }
                }
            }
        }.isSuccess
        if (!accepted) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project execution phase is unexpected or out of sequence.",
            )
        } else {
            observedEvent?.let { event ->
                runCatching {
                    pending.projectExecutionObserver?.invoke(event)
                }
            }
        }
    }

    private fun handleProjectPhaseProgressOnMain(
        message: CompilerHostMessage.ProjectPhaseProgress,
    ) {
        val pending = currentProjectPending(
            requestId = message.requestId,
            generation = message.generation,
        ) ?: return
        var observedEvent: R2ProjectExecutionEvent.Work? = null
        val accepted = runCatching {
            synchronized(stateLock) {
                check(pendingProbe === pending && pending.stopCause == null) {
                    "Project execution progress arrived after terminal intent."
                }
                val previous = pending.activeProjectExecutionPhaseProgress
                val expectedSequence = (previous?.sequence ?: 0) + 1
                val divisor = if (message.sequence == 1) 8 else 4
                val expectedCompletedUnits =
                    (message.totalUnits - 1) / divisor + 1
                check(
                    pending.activeProjectExecutionPhase == message.phase &&
                        message.phase != R2ProjectExecutionPhase.INGRESS &&
                        message.sequence == expectedSequence &&
                        message.sequence in 1..2 &&
                        message.totalUnits >= 8 &&
                        message.completedUnits == expectedCompletedUnits &&
                        message.completedUnits < message.totalUnits &&
                        (
                            previous == null ||
                                previous.phase == message.phase &&
                                previous.totalUnits == message.totalUnits
                            ),
                ) {
                    "Project execution progress is unexpected or out of sequence."
                }
                pending.activeProjectExecutionPhaseProgress =
                    R2ProjectExecutionPhaseProgress(
                        phase = message.phase,
                        sequence = message.sequence,
                        completedUnits = message.completedUnits,
                        totalUnits = message.totalUnits,
                    )
                pending.projectExecutionPhaseProgressHistory +=
                    requireNotNull(
                        pending.activeProjectExecutionPhaseProgress,
                    )
                observedEvent = R2ProjectExecutionEvent.Work(
                    progress = requireNotNull(
                        pending.activeProjectExecutionPhaseProgress,
                    ),
                    observedAtElapsedRealtimeNanos =
                        SystemClock.elapsedRealtimeNanos(),
                )
                if (message.sequence == 2) {
                    check(
                        pending
                            .phaseSequenceTwoObservedAtElapsedRealtimeNanos
                            .put(
                                message.phase,
                                SystemClock.elapsedRealtimeNanos(),
                            ) == null,
                    ) {
                        "Project execution phase sequence two was already observed."
                    }
                }
            }
        }.isSuccess
        if (!accepted) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message =
                    "Project execution progress is unexpected or out of sequence.",
            )
        } else {
            observedEvent?.let { event ->
                runCatching {
                    pending.projectExecutionObserver?.invoke(event)
                }
            }
        }
    }

    private fun handleProjectResultStartOnMain(
        message: CompilerHostMessage.ProjectResultStart,
    ) {
        val pending = currentProjectPending(
            requestId = message.requestId,
            generation = message.generation,
        ) ?: return
        val deferred = synchronized(stateLock) {
            if (
                pendingProbe === pending &&
                !pending.projectInputComplete &&
                pending.projectInputPreparationInFlight &&
                pending.projectInputEndAwaitingAcknowledgement &&
                pending.deferredProjectResultStart == null
            ) {
                pending.deferredProjectResultStart = message
                true
            } else {
                false
            }
        }
        if (deferred) return
        val failure = runCatching {
            synchronized(stateLock) {
                check(
                    pendingProbe === pending &&
                        pending.stopCause == null &&
                        pending.projectInputComplete &&
                        pending.activeProjectExecutionPhase ==
                            R2ProjectExecutionPhase.EMIT &&
                        pending.enteredProjectExecutionPhases ==
                            R2ProjectExecutionPhase.ordered &&
                        pending.completedProjectExecutionPhases ==
                            R2ProjectExecutionPhase.ordered.dropLast(1) &&
                        pending.projectResultAccumulator == null &&
                        !pending.projectResultFinalizing,
                ) {
                    "Project result started outside the accepted transport state."
                }
                pending.projectResultAccumulator = R2ProjectResultAccumulator(
                    requestId = message.requestId,
                    generation = message.generation,
                    byteLength = message.byteLength,
                    sha256 = message.sha256,
                    chunkCount = message.chunkCount,
                )
            }
            check(
                nativePort.postMessageSafely(
                    CompilerHostProtocol.projectResultAckRequest(
                        requestId = message.requestId,
                        expectedGeneration = message.generation,
                        sequence = -1,
                    ),
                ),
            ) {
                "Failed to acknowledge the project result header."
            }
        }.exceptionOrNull()
        if (failure != null) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project result transport failed: ${failure.safeMessage()}",
            )
        }
    }

    private fun handleProjectResultChunkOnMain(
        message: CompilerHostMessage.ProjectResultChunk,
    ) {
        try {
            val pending = currentProjectPending(
                requestId = message.requestId,
                generation = message.generation,
            ) ?: return
            val failure = runCatching {
                synchronized(stateLock) {
                    check(
                        pendingProbe === pending &&
                            pending.stopCause == null &&
                            pending.activeProjectExecutionPhase ==
                                R2ProjectExecutionPhase.EMIT &&
                            pending.completedProjectExecutionPhases ==
                                R2ProjectExecutionPhase.ordered.dropLast(1),
                    ) {
                        "Project result chunk arrived outside active emit transport."
                    }
                    requireNotNull(pending.projectResultAccumulator).append(
                        index = message.index,
                        chunk = message.bytes,
                    )
                }
                check(
                    nativePort.postMessageSafely(
                        CompilerHostProtocol.projectResultAckRequest(
                            requestId = message.requestId,
                            expectedGeneration = message.generation,
                            sequence = message.index,
                        ),
                    ),
                ) {
                    "Failed to acknowledge a project result chunk."
                }
            }.exceptionOrNull()
            if (failure != null) {
                failExpectedPendingAndSession(
                    expectedPending = pending,
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message = "Project result transport failed: ${failure.safeMessage()}",
                )
            }
        } finally {
            message.bytes.fill(0)
        }
    }

    private fun handleProjectResultEndOnMain(
        message: CompilerHostMessage.ProjectResultEnd,
    ) {
        val pending = currentProjectPending(
            requestId = message.requestId,
            generation = message.generation,
        ) ?: return
        var invalidPhaseState = false
        val accumulator = synchronized(stateLock) {
            if (
                pendingProbe !== pending ||
                pending.stopCause != null ||
                pending.projectResultFinalizing
            ) {
                null
            } else if (
                pending.activeProjectExecutionPhase != null ||
                pending.completedProjectExecutionPhases !=
                    R2ProjectExecutionPhase.ordered
            ) {
                invalidPhaseState = true
                null
            } else {
                val acceptedAccumulator =
                    pending.projectResultAccumulator ?: return@synchronized null
                pending.projectResultFinalizing = true
                pending.projectResultFinalizerInFlight = true
                pending.projectResultAccumulator = null
                resultFinalizersInFlight = Math.addExact(resultFinalizersInFlight, 1)
                acceptedAccumulator
            }
        }
        if (invalidPhaseState) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project result ended before emit transport completed.",
            )
            return
        }
        accumulator ?: return
        val submitted = runCatching {
            resultExecutor.execute {
                try {
                    val testControls = synchronized(stateLock) {
                        resultFinalizerRunningForTesting = true
                        val delay = resultFinalizerDelayMillisecondsForTesting
                        resultFinalizerDelayMillisecondsForTesting = 0
                        val gate = armedResultFinalizerGateForTesting
                        armedResultFinalizerGateForTesting = null
                        activeResultFinalizerGateForTesting = gate
                        ResultFinalizerTestControls(delay, gate)
                    }
                    if (testControls.delayMilliseconds > 0) {
                        Thread.sleep(testControls.delayMilliseconds.toLong())
                    }
                    testControls.gate?.await(
                        MAX_RESULT_FINALIZER_GATE_WAIT_MILLISECONDS_FOR_TESTING,
                        TimeUnit.MILLISECONDS,
                    )
                    val stillCurrent = synchronized(stateLock) {
                        pendingProbe === pending &&
                            pending.stopCause == null &&
                            pending.sessionToken === currentSessionToken
                    }
                    if (!stillCurrent) {
                        return@execute
                    }
                    val parsed = runCatching {
                        val payload = accumulator.finish(
                            endByteLength = message.byteLength,
                            endSha256 = message.sha256,
                            endChunkCount = message.chunkCount,
                        )
                        try {
                            CompilerHostProtocol.parseFramedProjectResult(
                                requestId = message.requestId,
                                generation = message.generation,
                                payload = payload,
                                exactSnapshot = requireNotNull(pending.projectSnapshot),
                            )
                        } finally {
                            payload.fill(0)
                        }
                    }
                    mainHandler.post {
                        if (
                            synchronized(stateLock) {
                                pendingProbe === pending && pending.stopCause == null
                            }
                        ) {
                            parsed.fold(
                                onSuccess = ::handleProjectResultOnMain,
                                onFailure = { error ->
                                    failExpectedPendingAndSession(
                                        expectedPending = pending,
                                        code =
                                        TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                                        message = "Project result verification failed: " +
                                            error.safeMessage(),
                                    )
                                },
                            )
                        }
                    }
                } finally {
                    releaseTransferredProjectResult(pending, accumulator)
                }
            }
        }.isSuccess
        if (!submitted) {
            releaseTransferredProjectResult(pending, accumulator)
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler protocol verifier is unavailable.",
            )
        }
    }

    private fun releaseTransferredProjectResult(
        pending: PendingProbe,
        accumulator: R2ProjectResultAccumulator,
    ) {
        accumulator.release()
        val resume = synchronized(stateLock) {
            check(resultFinalizersInFlight > 0) {
                "Project result finalizer accounting underflow."
            }
            check(pending.projectResultFinalizerInFlight) {
                "Exact project result finalizer accounting was lost."
            }
            resultFinalizersInFlight--
            pending.projectResultFinalizerInFlight = false
            resultFinalizerRunningForTesting = false
            activeResultFinalizerGateForTesting = null
            when {
                closed || pendingProbe !== pending -> null
                pending.stopCause != null && pending.stopTeardownProven ->
                    ResultFinalizerResume.Stopped
                pending.stopCause == null &&
                    pending.deferredProjectCompletion != null ->
                    ResultFinalizerResume.Deferred(
                        requireNotNull(pending.deferredProjectCompletion),
                    )
                else -> null
            }
        }
        if (resume != null) {
            val posted = mainHandler.post {
                when (resume) {
                    ResultFinalizerResume.Stopped ->
                        completeStoppedProjectAfterTeardownOnMain(pending)
                    is ResultFinalizerResume.Deferred ->
                        completePendingProject(
                            pending = pending,
                            outcome = resume.completion.outcome,
                            beforeSignal = resume.completion.beforeSignal,
                        )
                }
            }
            if (!posted) {
                markExternalTeardownRequired()
            }
        }
    }

    private fun scheduleProjectInputPreparationOnMain(
        pending: PendingProbe,
        acknowledgedSequence: Int?,
    ) {
        checkMainThread()
        val fence = synchronized(stateLock) {
            val sessionToken = pending.sessionToken
            val port = nativePort
            val generation = pending.workerGeneration
            if (
                pendingProbe !== pending ||
                closed ||
                timeoutRecoveryPending ||
                pending.stopCause != null ||
                pending.projectInputFramer == null ||
                pending.projectInputPreparationInFlight ||
                pending.projectInputComplete ||
                sessionToken == null ||
                sessionToken !== currentSessionToken ||
                port == null ||
                generation == null ||
                generation != workerGeneration
            ) {
                null
            } else {
                if (acknowledgedSequence == null) {
                    check(
                        !pending.operationPosted &&
                            pending.projectInputExpectedAckSequence == null
                    ) {
                        "Initial project input preparation was repeated."
                    }
                } else {
                    check(pending.projectInputExpectedAckSequence == acknowledgedSequence) {
                        "Project input acknowledgement does not match the outstanding frame."
                    }
                    pending.projectInputExpectedAckSequence = null
                }
                pending.projectInputPreparationInFlight = true
                pending.projectInputPreparationEpoch =
                    Math.addExact(pending.projectInputPreparationEpoch, 1)
                ProjectInputPreparationFence(
                    pending = pending,
                    sessionToken = sessionToken,
                    port = port,
                    workerGeneration = generation,
                    preparationEpoch = pending.projectInputPreparationEpoch,
                    acknowledgedSequence = acknowledgedSequence,
                )
            }
        } ?: return

        val submitted = runCatching {
            inputFrameExecutor.execute {
                val framer = requireNotNull(pending.projectInputFramer)
                val result = runCatching {
                    fence.acknowledgedSequence?.let(framer::acknowledge)
                    if (framer.isComplete) {
                        ProjectInputPreparation.Complete
                    } else {
                        val encoded = framer.nextFrame()
                        ProjectInputPreparation.Prepared(
                            sequence = requireNotNull(framer.outstandingSequence),
                            encoded = encoded,
                            completesOnAcknowledgement =
                                framer.outstandingCompletesTransport,
                        )
                    }
                }
                activeProjectInputTransportTelemetryForTestingValue =
                    framer.telemetryForTesting()
                if (
                    !mainHandler.post {
                        handleProjectInputPreparationOnMain(fence, result)
                    }
                ) {
                    runCatching { framer.release() }
                    markExternalTeardownRequired()
                }
            }
        }.isSuccess
        if (!submitted) {
            synchronized(stateLock) {
                if (
                    pendingProbe === pending &&
                    pending.projectInputPreparationEpoch == fence.preparationEpoch
                ) {
                    pending.projectInputPreparationInFlight = false
                }
            }
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project input framing executor is unavailable.",
            )
        }
    }

    private fun handleProjectInputPreparationOnMain(
        fence: ProjectInputPreparationFence,
        result: Result<ProjectInputPreparation>,
    ) {
        checkMainThread()
        val pending = fence.pending
        val current = synchronized(stateLock) {
            val matches =
                pendingProbe === pending &&
                    pending.projectInputPreparationInFlight &&
                    pending.projectInputPreparationEpoch == fence.preparationEpoch
            if (matches) {
                pending.projectInputPreparationInFlight = false
            }
            matches &&
                !closed &&
                !timeoutRecoveryPending &&
                pending.stopCause == null &&
                pending.sessionToken === fence.sessionToken &&
                currentSessionToken === fence.sessionToken &&
                pending.workerGeneration == fence.workerGeneration &&
                workerGeneration == fence.workerGeneration &&
                nativePort === fence.port
        }
        if (!current) return

        val preparation = result.getOrElse { error ->
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project input transport failed: ${error.safeMessage()}",
            )
            return
        }
        when (preparation) {
            ProjectInputPreparation.Complete -> {
                val deferredResultStart = synchronized(stateLock) {
                    if (
                        pendingProbe === pending &&
                        pending.stopCause == null &&
                        pending.sessionToken === fence.sessionToken &&
                        pending.workerGeneration == fence.workerGeneration
                    ) {
                        pending.projectInputComplete = true
                        pending.projectInputEndAwaitingAcknowledgement = false
                        pending.deferredProjectResultStart.also {
                            pending.deferredProjectResultStart = null
                        }
                    } else {
                        null
                    }
                }
                deferredResultStart?.let(::handleProjectResultStartOnMain)
            }
            is ProjectInputPreparation.Prepared -> {
                val posted = synchronized(stateLock) {
                    if (
                        pendingProbe !== pending ||
                        closed ||
                        timeoutRecoveryPending ||
                        pending.stopCause != null ||
                        pending.sessionToken !== fence.sessionToken ||
                        currentSessionToken !== fence.sessionToken ||
                        pending.workerGeneration != fence.workerGeneration ||
                        workerGeneration != fence.workerGeneration ||
                        nativePort !== fence.port ||
                        pending.projectInputExpectedAckSequence != null
                    ) {
                        null
                    } else {
                        fence.port.postMessageSafely(preparation.encoded).also { succeeded ->
                            if (succeeded) {
                                pending.operationPosted = true
                                pending.projectInputExpectedAckSequence = preparation.sequence
                                pending.projectInputEndAwaitingAcknowledgement =
                                    preparation.completesOnAcknowledgement
                            }
                        }
                    }
                }
                if (posted == false) {
                    failExpectedPendingAndSession(
                        expectedPending = pending,
                        code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                        message = "Failed to send the next project input frame.",
                    )
                }
            }
        }
    }

    private fun handleWorkerTerminatedOnMain(
        message: CompilerHostMessage.WorkerTerminated,
    ) {
        val pendingAndEvidence = synchronized(stateLock) {
            pendingProbe?.takeIf { candidate ->
                candidate.projectSnapshot != null &&
                    candidate.requestId == message.requestId &&
                    candidate.workerGeneration == message.terminatedGeneration &&
                    candidate.stopCause != null &&
                    candidate.terminationRequested
            }?.let { candidate ->
                val evidence = projectPhaseTerminationEvidence(
                    pending = candidate,
                    message = message,
                )
                candidate.projectPhaseTerminationEvidence = evidence
                candidate to evidence
            }
        } ?: return
        val (pending, evidence) = pendingAndEvidence
        workerReady = false
        workerGeneration = message.replacementGeneration
        lastTerminatedGenerationForTestingValue = message.terminatedGeneration
        if (evidence != null) {
            lastProjectPhaseTerminationEvidenceForTestingValue = evidence
        }
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_STARTING,
            lastError = stopMessage(requireNotNull(pending.stopCause)),
        )
        completeStoppedProjectAfterTeardownOnMain(pending)
    }

    private fun projectPhaseTerminationEvidence(
        pending: PendingProbe,
        message: CompilerHostMessage.WorkerTerminated,
    ): R2ProjectPhaseTerminationEvidence? {
        val selection = pending.projectStopSelection ?: return null
        val selectedPhase = selection.activePhase ?: return null
        val selectedProgress = selection.activeProgress ?: return null
        val selectedSessionGeneration = selection.sessionGeneration ?: return null
        val selectedWorkerGeneration = selection.workerGeneration ?: return null
        val selectedDeadlineAt = selection.deadlineAtElapsedRealtimeMillis ?: return null
        val selectedDeadlineAtNanos =
            selection.deadlineAtElapsedRealtimeNanos ?: return null
        if (
            selection.requestId != pending.requestId ||
            selection.stopCause != pending.stopCause ||
            (
                selection.stopCause == R2ProjectStopCause.TIMED_OUT &&
                    selection.stopSelectedAtElapsedRealtimeNanos <
                    selectedDeadlineAtNanos
                ) ||
            selectedProgress.sequence != 2 ||
            selectedProgress.phase != selectedPhase ||
            selection.enteredPhases.lastOrNull() != selectedPhase ||
            selection.completedPhases.contains(selectedPhase) ||
            selectedSessionGeneration != pending.webViewSessionGeneration ||
            selectedWorkerGeneration != message.terminatedGeneration ||
            message.phaseAtTermination != selectedPhase ||
            message.phaseProgressSequenceAtTermination !=
            selectedProgress.sequence ||
            message.phaseProgressCompletedUnitsAtTermination !=
            selectedProgress.completedUnits ||
            message.phaseProgressTotalUnitsAtTermination !=
            selectedProgress.totalUnits
        ) {
            return null
        }
        return R2ProjectPhaseTerminationEvidence(
            requestId = selection.requestId,
            stopCause = selection.stopCause,
            phaseAtHostStopSelection = selectedPhase,
            enteredPhasesAtHostStopSelection = selection.enteredPhases,
            completedPhasesAtHostStopSelection = selection.completedPhases,
            sessionGeneration = selectedSessionGeneration,
            workerGeneration = selectedWorkerGeneration,
            deadlineAtElapsedRealtimeMillis = selectedDeadlineAt,
            deadlineAtElapsedRealtimeNanos = selectedDeadlineAtNanos,
            stopSelectedAtElapsedRealtimeMillis =
                selection.stopSelectedAtElapsedRealtimeMillis,
            stopSelectedAtElapsedRealtimeNanos =
                selection.stopSelectedAtElapsedRealtimeNanos,
            phaseAtPageTermination = selectedPhase,
            phaseProgressSequenceAtPageTermination =
                message.phaseProgressSequenceAtTermination,
            phaseProgressCompletedUnitsAtPageTermination =
                message.phaseProgressCompletedUnitsAtTermination,
            phaseProgressTotalUnitsAtPageTermination =
                message.phaseProgressTotalUnitsAtTermination,
            terminatedWorkerGeneration = message.terminatedGeneration,
            replacementWorkerGeneration = message.replacementGeneration,
        )
    }

    private fun currentProjectPending(
        requestId: String,
        generation: Int,
    ): PendingProbe? = synchronized(stateLock) {
        pendingProbe?.takeIf { pending ->
            pending.projectSnapshot != null &&
                pending.requestId == requestId &&
                pending.workerGeneration == generation &&
                pending.stopCause == null
        }
    }

    private fun handleProjectResultOnMain(message: CompilerHostMessage.ProjectResult) {
        val pending = synchronized(stateLock) { pendingProbe } ?: return
        val snapshot = pending.projectSnapshot ?: return
        if (
            message.requestId != pending.requestId ||
            message.generation != pending.workerGeneration ||
            pending.stopCause != null
        ) {
            return
        }
        val profileAuthority = snapshot.profileAuthority
        val sourcePaths = snapshot.filePaths
            .filter { path ->
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    path,
                    profileAuthority,
                )
            }
            .toSet()
        val packageTargetPaths = HashSet<String>().apply {
            snapshot.packageTargetInventory?.files?.forEach { file ->
                add("/${file.path}")
            }
            snapshot.nodePackageTargetV2Inventory?.files?.forEach { file ->
                add("/${file.path}")
            }
        }
        val acceptedDiagnosticPaths = HashSet(snapshot.filePaths).apply {
            addAll(packageTargetPaths)
        }
        val compilation = message.compilation
        val controlledConfig = snapshot.rawConfigDescriptor != null
        val profileAuthorityValid =
            compilation.targetProfile == profileAuthority.profile.contractValue &&
                compilation.profileSchemaRevision == profileAuthority.profileSchemaRevision &&
                compilation.profileRevision == profileAuthority.profileRevision &&
                (
                    controlledConfig ||
                        compilation.effectiveOptions == profileAuthority.effectiveOptions
                    )
        if (!profileAuthorityValid) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project compiler result does not match its accepted profile authority.",
            )
            return
        }
        val selectedRootsValid =
            if (controlledConfig) {
                compilation.configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG &&
                    compilation.configSchemaRevision ==
                    TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION &&
                    compilation.controlledEffectiveOptions != null &&
                    compilation.selectedRootNames.isNotEmpty() &&
                    snapshot.entryPath in compilation.selectedRootNames &&
                    compilation.selectedRootNames.all(snapshot.filePaths::contains)
            } else {
                compilation.configKind == snapshot.profileAuthority.configKind &&
                    compilation.configSchemaRevision ==
                    snapshot.profileAuthority.configSchemaRevision &&
                    compilation.controlledEffectiveOptions == null &&
                    compilation.selectedRootNames.isEmpty()
            }
        if (!selectedRootsValid) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project compiler result does not match its accepted root authority.",
            )
            return
        }
        if (
            compilation.diagnostics.any { diagnostic ->
                val sourcePath = diagnostic.sourcePath ?: return@any false
                if (sourcePath !in acceptedDiagnosticPaths) return@any true
                if (
                    sourcePath in packageTargetPaths &&
                    diagnostic.phase != R2ProjectDiagnosticPhase.RESOLUTION
                ) {
                    return@any true
                }
                val start = diagnostic.start ?: return@any false
                val sourceCodeUnits =
                    snapshot.utf16CodeUnits(sourcePath) ?: return@any true
                val length = requireNotNull(diagnostic.length)
                start > sourceCodeUnits ||
                    length > sourceCodeUnits - start
            }
        ) {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Project diagnostic is outside its input snapshot source.",
            )
            return
        }
        if (!compilation.emitSkipped) {
            val moduleSources = compilation.executableModules
                .flatMap(R2ProjectOutput::sourcePaths)
                .toSet()
            val mapSources = compilation.sourceMaps
                .flatMap(R2ProjectOutput::sourcePaths)
                .toSet()
            val exactInventory = if (controlledConfig) {
                val selectedExecutableRoots = compilation.selectedRootNames
                    .filterNot { path -> path.endsWith(".d.ts") }
                    .toSet()
                compilation.executableModules.size == moduleSources.size &&
                    compilation.sourceMaps.size == mapSources.size &&
                    moduleSources == mapSources &&
                    moduleSources.containsAll(selectedExecutableRoots) &&
                    sourcePaths.containsAll(moduleSources) &&
                    snapshot.entryPath in moduleSources
            } else {
                compilation.executableModules.size == sourcePaths.size &&
                    moduleSources == sourcePaths &&
                    mapSources == sourcePaths
            }
            if (!exactInventory) {
                failExpectedPendingAndSession(
                    expectedPending = pending,
                    code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    message =
                        "Project compiler output does not match the accepted source inventory.",
                )
                return
            }
        }
        completeProjectAfterSessionTeardownOnMain(
            pending = pending,
            outcome = R2ProjectCompilationOutcome(
                compilation = compilation,
                errorCode = null,
                errorMessage = null,
                elapsedMs = elapsedSince(pending.startedAt),
            ),
            compilerVersion = compilation.compilerVersion,
        )
    }

    /**
     * A project result is not terminally published until its exact WebView realm has retired.
     *
     * The Worker already severs Program/VFS references in its `finally`, but WebView/V8 controls
     * when that realm's allocator pages are reclaimed. Retiring the exact session here gives every
     * project a deterministic realm boundary; the next operation lazily creates a fresh session.
     */
    private fun completeProjectAfterSessionTeardownOnMain(
        pending: PendingProbe,
        outcome: R2ProjectCompilationOutcome,
        compilerVersion: String,
    ) {
        checkMainThread()
        val exactSessionStillOwned = synchronized(stateLock) {
            pendingProbe === pending &&
                pending.projectOutcome == null &&
                pending.sessionToken != null &&
                pending.sessionToken === currentSessionToken &&
                pending.webViewSessionGeneration == webViewSessionGeneration
        }
        if (!exactSessionStillOwned) return

        val release = destroySessionOnMain()
        if (!release.cleanupProven) {
            if (!mainHandler.postDelayed(
                {
                    completeProjectAfterSessionTeardownOnMain(
                        pending = pending,
                        outcome = outcome,
                        compilerVersion = compilerVersion,
                    )
                },
                SESSION_TEARDOWN_RETRY_MILLIS,
            )) {
                markExternalTeardownRequired()
            }
            return
        }
        if (externalTeardownRequired()) return

        timeoutRecoveryAttempted = false
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_BOOTSTRAP,
            compilerVersion = compilerVersion,
            lastError = null,
        )
        completePendingProject(
            pending = pending,
            outcome = outcome,
        )
    }

    private fun handleProjectConfigErrorOnMain(
        message: CompilerHostMessage.ProjectConfigError,
    ) {
        checkMainThread()
        val pending = synchronized(stateLock) { pendingProbe } ?: return
        if (
            pending.projectSnapshot == null ||
            pending.stopCause != null ||
            message.requestId != pending.requestId ||
            message.generation != pending.workerGeneration
        ) {
            return
        }
        completeProjectAfterSessionTeardownOnMain(
            pending = pending,
            outcome = R2ProjectCompilationOutcome.failed(
                code = TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS,
                message =
                    "Controlled tsconfig was rejected (${message.policyCode}): " +
                        message.message,
                elapsedMs = elapsedSince(pending.startedAt),
                configPolicyCode = message.policyCode,
            ),
            compilerVersion =
                currentSnapshot.compilerVersion ?: BuildConfig.TYPESCRIPT_VERSION,
        )
    }

    private fun handleWorkerFailureOnMain(
        message: CompilerHostMessage.Failure,
        expectedPending: PendingProbe?,
    ) {
        val pending = synchronized(stateLock) { pendingProbe }
        if (pending?.stopCause != null) {
            return
        }
        if (message.requestId != null) {
            if (
                pending == null ||
                message.requestId != pending.requestId ||
                message.generation != pending.workerGeneration
            ) {
                return
            }
        } else if (
            message.generation != expectedWorkerGeneration() ||
            pending !== expectedPending
        ) {
            return
        }
        if (pending == null) {
            failSessionIfPendingUnchanged(
                expectedPending = null,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler Worker failed: ${message.message}",
            )
        } else {
            failExpectedPendingAndSession(
                expectedPending = pending,
                code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                message = "Compiler Worker failed: ${message.message}",
            )
        }
    }

    private fun stopProjectOnMain(pending: PendingProbe) {
        checkMainThread()
        val stopState = synchronized(stateLock) {
            if (
                closed ||
                pendingProbe !== pending ||
                pending.projectSnapshot == null ||
                pending.stopCause == null ||
                pending.projectOutcome != null
            ) {
                null
            } else {
                ProjectStopState(
                    operationPosted = pending.operationPosted,
                    workerGeneration = pending.workerGeneration,
                    terminationRequested = pending.terminationRequested,
                )
            }
        } ?: return
        if (!stopState.operationPosted || stopState.workerGeneration == null) {
            completeStoppedProjectAfterTeardownOnMain(pending)
            return
        }
        if (stopState.terminationRequested) return

        workerReady = false
        val terminationRequest = CompilerHostProtocol.terminateWorkerRequest(
            requestId = pending.requestId,
            expectedGeneration = stopState.workerGeneration,
        )
        val posted = synchronized(stateLock) {
            if (
                pendingProbe !== pending ||
                pending.stopCause == null ||
                pending.workerGeneration != stopState.workerGeneration ||
                pending.terminationRequested
            ) {
                null
            } else {
                pending.terminationRequested = true
                nativePort.postMessageSafely(terminationRequest)
            }
        }
        if (posted != true) {
            forceStopProjectOnMain(pending)
            return
        }
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_DEGRADED,
            lastError = stopMessage(requireNotNull(pending.stopCause)),
        )
        val fallbackScheduled = mainHandler.postDelayed(
            { forceStopProjectOnMain(pending) },
            PROJECT_STOP_ACK_TIMEOUT_MILLIS,
        )
        if (!fallbackScheduled) {
            forceStopProjectOnMain(pending)
        }
    }

    private fun forceStopProjectOnMain(pending: PendingProbe) {
        checkMainThread()
        val mustStop = synchronized(stateLock) {
            pendingProbe === pending &&
                pending.projectSnapshot != null &&
                pending.stopCause != null &&
                pending.projectOutcome == null
        }
        if (!mustStop) return
        val release = destroySessionOnMain()
        if (!release.cleanupProven) {
            if (!mainHandler.postDelayed(
                { forceStopProjectOnMain(pending) },
                SESSION_TEARDOWN_RETRY_MILLIS,
            )) {
                markExternalTeardownRequired()
            }
            return
        }
        if (externalTeardownRequired()) return
        timeoutRecoveryAttempted = false
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_STARTING,
            lastError = stopMessage(requireNotNull(pending.stopCause)),
        )
        completeStoppedProjectAfterTeardownOnMain(pending)
    }

    private fun completeStoppedProjectAfterTeardownOnMain(pending: PendingProbe) {
        checkMainThread()
        val cause = synchronized(stateLock) {
            if (
                pendingProbe !== pending ||
                pending.projectOutcome != null ||
                pending.stopCause == null
            ) {
                null
            } else {
                pending.stopTeardownProven = true
                if (pending.projectResultFinalizerInFlight) {
                    return@synchronized null
                }
                val acceptedCause = pending.operationHandle?.completeStopped()
                    ?: return@synchronized null
                pending.projectOutcome = R2ProjectCompilationOutcome.failed(
                    code = stopErrorCode(acceptedCause),
                    message = stopMessage(acceptedCause),
                    elapsedMs = elapsedSince(pending.startedAt),
                )
                pendingProbe = null
                acceptedCause
            }
        } ?: return
        if (!releaseProjectInputOrPoison(pending.projectInputFramer)) {
            markProjectInputReleaseUnproven(pending)
        }
        pending.projectResultAccumulator?.release()
        pending.projectResultAccumulator = null
        pending.completion.countDown()
        if (cause == R2ProjectStopCause.SERVICE_DESTROYED) {
            currentSnapshot = currentSnapshot.copy(
                status = TypeScriptCompilerContract.STATUS_FAILED,
                lastError = stopMessage(cause),
            )
        }
    }

    private fun stopErrorCode(cause: R2ProjectStopCause): String =
        when (cause) {
            R2ProjectStopCause.CANCELLED,
            R2ProjectStopCause.CALLBACK_DIED,
            -> TypeScriptCompilerContract.ERROR_CANCELLED
            R2ProjectStopCause.TIMED_OUT -> TypeScriptCompilerContract.ERROR_TIMEOUT
            R2ProjectStopCause.SERVICE_DESTROYED ->
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE
        }

    private fun stopMessage(cause: R2ProjectStopCause): String =
        when (cause) {
            R2ProjectStopCause.CANCELLED -> "Project compilation was cancelled."
            R2ProjectStopCause.TIMED_OUT -> "Project compilation timed out."
            R2ProjectStopCause.CALLBACK_DIED ->
                "Project compilation owner disconnected."
            R2ProjectStopCause.SERVICE_DESTROYED ->
                "Compiler service was destroyed."
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

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private fun handleRendererGoneOnMain(
        view: WebView,
        detail: RenderProcessGoneDetail,
    ) {
        checkMainThread()
        if (view !== webView) return
        val token = currentSessionToken?.takeIf { it.webView === view } ?: return
        val pending = synchronized(stateLock) {
            pendingProbe?.takeIf { probe ->
                probe.sessionToken === token
            }
        }
        lastRendererGoneEvidenceForTestingValue = R2RendererGoneEvidence(
            requestId = pending?.requestId,
            phase = pending?.activeProjectExecutionPhase,
            requestedAtPhase = pending?.rendererTerminationRequestedAtPhase,
            didCrash = detail.didCrash(),
            rendererPriorityAtExit = detail.rendererPriorityAtExit(),
            observedAtElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            sessionGeneration = token.generation,
            workerGeneration = pending?.workerGeneration,
        )
        val mayRetry =
            !closed &&
                pending != null &&
                pending.projectSnapshot == null &&
                pending.rendererRetryCount < 1
        if (mayRetry) {
            requireNotNull(pending).rendererRetryCount += 1
        }
        val release = releaseSessionAfterProof(
            ExactSessionTeardownProof.RendererGone(token),
        )
        if (!release.cleanupProven) return
        if (externalTeardownRequired()) return
        if (mayRetry) {
            ensureSessionAndDispatchOnMain()
        } else if (pending?.stopCause != null) {
            completeStoppedProjectAfterTeardownOnMain(pending)
        } else {
            pending?.let { failed ->
                if (failed.projectSnapshot == null) {
                    completePending(
                        failed,
                        CompilerHostProbeOutcome.failed(
                            code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                            message = "Compiler WebView renderer exited.",
                            elapsedMs = elapsedSince(failed.startedAt),
                        ),
                    )
                } else {
                    completePendingProject(
                        failed,
                        R2ProjectCompilationOutcome.failed(
                            code = TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                            message = "Compiler WebView renderer exited.",
                            elapsedMs = elapsedSince(failed.startedAt),
                        ),
                    )
                }
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
        val pending = synchronized(stateLock) { pendingProbe }
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_FAILED,
            lastError = message,
        )
        val release = destroySessionOnMain()
        if (!release.cleanupProven) {
            if (!mainHandler.postDelayed(
                { failPendingAndSession(code, message) },
                SESSION_TEARDOWN_RETRY_MILLIS,
            )) {
                markExternalTeardownRequired()
            }
            return
        }
        if (externalTeardownRequired()) return
        if (pending?.stopCause != null) {
            completeStoppedProjectAfterTeardownOnMain(pending)
        } else {
            failPending(code, message)
        }
    }

    private fun failExpectedPendingAndSession(
        expectedPending: PendingProbe,
        code: String,
        message: String,
    ) {
        val unchanged = synchronized(stateLock) {
            pendingProbe === expectedPending
        }
        if (!unchanged) return
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_FAILED,
            lastError = message,
        )
        val release = destroySessionOnMain()
        if (!release.cleanupProven) {
            if (!mainHandler.postDelayed(
                {
                    failExpectedPendingAndSession(
                        expectedPending = expectedPending,
                        code = code,
                        message = message,
                    )
                },
                SESSION_TEARDOWN_RETRY_MILLIS,
            )) {
                markExternalTeardownRequired()
            }
            return
        }
        if (externalTeardownRequired()) return
        if (expectedPending.stopCause != null) {
            completeStoppedProjectAfterTeardownOnMain(expectedPending)
        } else {
            failPending(expectedPending, code, message)
        }
    }

    private fun failSessionIfPendingUnchanged(
        expectedPending: PendingProbe?,
        code: String,
        message: String,
    ) {
        val unchanged = synchronized(stateLock) {
            pendingProbe === expectedPending
        }
        if (!unchanged) return
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_FAILED,
            lastError = message,
        )
        val release = destroySessionOnMain()
        if (!release.cleanupProven) {
            if (!mainHandler.postDelayed(
                {
                    failSessionIfPendingUnchanged(
                        expectedPending = expectedPending,
                        code = code,
                        message = message,
                    )
                },
                SESSION_TEARDOWN_RETRY_MILLIS,
            )) {
                markExternalTeardownRequired()
            }
            return
        }
        if (externalTeardownRequired()) return
        if (expectedPending?.stopCause != null) {
            completeStoppedProjectAfterTeardownOnMain(expectedPending)
        } else if (expectedPending != null) {
            failPending(expectedPending, code, message)
        }
    }

    private fun failPending(
        code: String,
        message: String,
    ) {
        val pending = synchronized(stateLock) { pendingProbe } ?: return
        failPending(pending, code, message)
    }

    private fun failPending(
        pending: PendingProbe,
        code: String,
        message: String,
    ): Boolean =
        if (pending.projectSnapshot == null) {
            completePending(
                pending,
                CompilerHostProbeOutcome.failed(
                    code = code,
                    message = message,
                    elapsedMs = elapsedSince(pending.startedAt),
                ),
            )
        } else {
            completePendingProject(
                pending,
                R2ProjectCompilationOutcome.failed(
                    code = code,
                    message = message,
                    elapsedMs = elapsedSince(pending.startedAt),
                ),
            )
        }

    private fun completePending(
        pending: PendingProbe,
        outcome: CompilerHostProbeOutcome,
    ): Boolean {
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
        return completed
    }

    private fun completePendingProject(
        pending: PendingProbe,
        outcome: R2ProjectCompilationOutcome,
        beforeSignal: () -> Unit = {},
    ): Boolean {
        var stopRequested = false
        var completed: DeferredProjectCompletion? = null
        synchronized(stateLock) {
            if (
                pendingProbe !== pending ||
                pending.projectOutcome != null
            ) {
                return@synchronized
            }
            val requestedCompletion = DeferredProjectCompletion(
                outcome = outcome,
                beforeSignal = beforeSignal,
            )
            if (pending.projectResultFinalizerInFlight) {
                if (pending.deferredProjectCompletion == null) {
                    pending.deferredProjectCompletion = requestedCompletion
                }
                return@synchronized
            }
            val selectedCompletion =
                pending.deferredProjectCompletion ?: requestedCompletion
            pending.deferredProjectCompletion = null
            val handle = requireNotNull(pending.operationHandle)
            val deadlineAtElapsedRealtimeNanos =
                requireNotNull(pending.deadlineAtElapsedRealtimeNanos)
            when (
                handle.tryCompleteBeforeElapsedRealtimeDeadline(
                    deadlineAtElapsedRealtimeNanos =
                        deadlineAtElapsedRealtimeNanos,
                    elapsedRealtimeNanos =
                        SystemClock::elapsedRealtimeNanos,
                )
            ) {
                R2HostProjectTerminalDecision.COMPLETED -> {
                    pending.projectOutcome =
                        if (selectedCompletion.outcome.succeeded) {
                            selectedCompletion.outcome.bindToExactSnapshot(
                                requireNotNull(pending.projectSnapshot),
                            )
                        } else {
                            selectedCompletion.outcome
                        }
                    pendingProbe = null
                    completed = selectedCompletion
                }
                R2HostProjectTerminalDecision.STOP_REQUESTED -> {
                    if (pending.stopCause == null) {
                        selectAcceptedProjectStopLocked(pending)
                    }
                    stopRequested = true
                }
                R2HostProjectTerminalDecision.ALREADY_TERMINAL -> Unit
            }
        }
        if (stopRequested) {
            dispatchAcceptedProjectStop(pending)
        }
        completed?.let { terminal ->
            if (!releaseProjectInputOrPoison(pending.projectInputFramer)) {
                markProjectInputReleaseUnproven(pending)
            }
            pending.projectResultAccumulator?.release()
            pending.projectResultAccumulator = null
            try {
                terminal.beforeSignal()
            } finally {
                pending.completion.countDown()
            }
        }
        return completed != null
    }

    private fun dispatchAcceptedProjectStop(pending: PendingProbe) {
        val canCompleteBeforeDispatch = synchronized(stateLock) {
            pendingProbe === pending &&
                pending.stopCause != null &&
                !pending.operationPosted
        }
        if (canCompleteBeforeDispatch) {
            completeStoppedProjectBeforeDispatch(pending)
        } else if (Looper.myLooper() == Looper.getMainLooper()) {
            stopProjectOnMain(pending)
        } else {
            if (!mainHandler.post { stopProjectOnMain(pending) }) {
                markExternalTeardownRequired()
            }
        }
    }

    private fun markExternalTeardownRequired() {
        val shouldNotify = synchronized(stateLock) {
            fatalTeardownPending = true
            !externalTeardownReported && externalTeardownObserver != null
        }
        currentSnapshot = currentSnapshot.copy(
            status = TypeScriptCompilerContract.STATUS_FAILED,
            lastError = "Compiler process teardown requires external supervision.",
        )
        if (shouldNotify) {
            notifyExternalTeardownObserver()
        }
    }

    private fun externalTeardownRequired(): Boolean =
        synchronized(stateLock) {
            fatalTeardownPending
        }

    private fun notifyExternalTeardownObserver() {
        val observer = synchronized(stateLock) {
            if (externalTeardownReported) {
                null
            } else {
                externalTeardownObserver?.also {
                    externalTeardownReported = true
                }
            }
        } ?: return
        observer()
    }

    private fun destroySessionOnMain(): CompilerSessionRelease {
        checkMainThread()
        workerReady = false
        val token = currentSessionToken
        val view = webView
        if (token == null) {
            check(view == null) { "Compiler session lost its exact WebView token." }
            check(
                synchronized(stateLock) {
                    pendingProbe?.operationPosted != true
                },
            ) {
                "An active project Worker has no exact session teardown token."
            }
            val port = nativePort
            val nativePortClosed =
                port == null || runCatching { port.close() }.isSuccess
            if (!nativePortClosed) {
                markExternalTeardownRequired()
                return CompilerSessionRelease(
                    nativePortClosed = false,
                    webViewDestroyed = true,
                )
            }
            nativePort = null
            workerGeneration = 0
            return CompilerSessionRelease(
                nativePortClosed = nativePortClosed,
                webViewDestroyed = true,
            )
        }
        check(view === token.webView) {
            "Compiler session token does not identify the current WebView."
        }
        val destroyed = runCatching { token.webView.destroy() }.isSuccess
        if (!destroyed) {
            // Preserve the exact view, port, clients and generation: an ACK or
            // renderer-gone callback may still supply the required proof.
            markExternalTeardownRequired()
            return CompilerSessionRelease(
                nativePortClosed = false,
                webViewDestroyed = false,
            )
        }
        return releaseSessionAfterProof(
            ExactSessionTeardownProof.WebViewDestroyed(token),
        )
    }

    private fun releaseSessionAfterProof(
        proof: ExactSessionTeardownProof,
    ): CompilerSessionRelease {
        checkMainThread()
        val token = proof.token
        check(
            currentSessionToken === token &&
                webView === token.webView &&
                webViewSessionGeneration == token.generation,
        ) {
            "Compiler session teardown proof does not identify the current session."
        }
        if (
            proof is ExactSessionTeardownProof.RendererGone &&
            runCatching { token.webView.destroy() }.isFailure
        ) {
            markExternalTeardownRequired()
            return CompilerSessionRelease(
                nativePortClosed = false,
                webViewDestroyed = false,
            )
        }
        val port = nativePort
        val nativePortClosed =
            port == null || runCatching { port.close() }.isSuccess
        if (!nativePortClosed) {
            markExternalTeardownRequired()
            return CompilerSessionRelease(
                nativePortClosed = false,
                webViewDestroyed = true,
            )
        }
        nativePort = null
        webView = null
        currentSessionToken = null
        lastRetiredSessionGenerationForTestingValue = token.generation
        workerReady = false
        workerGeneration = 0
        synchronized(stateLock) {
            pendingProbe
                ?.takeIf { pending -> pending.sessionToken === token }
                ?.let { pending ->
                    pending.sessionToken = null
                    pending.webViewSessionGeneration = null
                    pending.workerGeneration = null
                }
        }
        return CompilerSessionRelease(
            nativePortClosed = nativePortClosed,
            webViewDestroyed = true,
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

    private fun CountDownLatch.awaitUntilElapsedRealtimeNanos(
        deadlineAtElapsedRealtimeNanos: Long,
    ): Boolean {
        var interrupted = false
        try {
            while (count != 0L) {
                val remainingNanos =
                    deadlineAtElapsedRealtimeNanos -
                        SystemClock.elapsedRealtimeNanos()
                if (remainingNanos <= 0L) return false
                try {
                    if (await(remainingNanos, TimeUnit.NANOSECONDS)) {
                        return true
                    }
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

    private fun CountDownLatch.awaitTerminalUninterruptibly() {
        var interrupted = false
        try {
            while (count != 0L) {
                try {
                    await()
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
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
            handleRendererGoneOnMain(view, detail)
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
    ) {
        val cleanupProven: Boolean
            get() = nativePortClosed && webViewDestroyed
    }

    private class CompilerSessionToken(
        val generation: Int,
        val webView: WebView,
    )

    private sealed interface ExactSessionTeardownProof {
        val token: CompilerSessionToken

        data class WebViewDestroyed(
            override val token: CompilerSessionToken,
        ) : ExactSessionTeardownProof

        data class RendererGone(
            override val token: CompilerSessionToken,
        ) : ExactSessionTeardownProof
    }

    private data class ProjectStopState(
        val operationPosted: Boolean,
        val workerGeneration: Int?,
        val terminationRequested: Boolean,
    )

    private data class ProjectStopSelection(
        val requestId: String,
        val stopCause: R2ProjectStopCause,
        val sessionGeneration: Int?,
        val workerGeneration: Int?,
        val activePhase: R2ProjectExecutionPhase?,
        val activeProgress: R2ProjectExecutionPhaseProgress?,
        val enteredPhases: List<R2ProjectExecutionPhase>,
        val completedPhases: List<R2ProjectExecutionPhase>,
        val deadlineAtElapsedRealtimeMillis: Long?,
        val deadlineAtElapsedRealtimeNanos: Long?,
        val stopSelectedAtElapsedRealtimeMillis: Long,
        val stopSelectedAtElapsedRealtimeNanos: Long,
    )

    private data class PendingProbe(
        val requestId: String,
        val startedAt: Long,
        val startedAtElapsedRealtimeNanos: Long = 0L,
        val correlationFaultForTesting: CompilerCorrelationFaultForTesting?,
        val encodedRequest: String?,
        val projectSnapshot: R2ProjectInputSnapshot? = null,
        val operationHandle: R2HostProjectOperationHandle? = null,
        val projectInputFramer: R2ProjectInputTransport? = null,
        val deadlineAt: Long? = null,
        val deadlineAtElapsedRealtimeNanos: Long? = null,
        val projectExecutionObserver:
            ((R2ProjectExecutionEvent) -> Unit)? = null,
        val completion: CountDownLatch = CountDownLatch(1),
        var outcome: CompilerHostProbeOutcome? = null,
        var projectOutcome: R2ProjectCompilationOutcome? = null,
        var rendererRetryCount: Int = 0,
        var webViewSessionGeneration: Int? = null,
        var sessionToken: CompilerSessionToken? = null,
        var workerGeneration: Int? = null,
        var correlationFaultInjected: Boolean = false,
        var operationPosted: Boolean = false,
        var stopCause: R2ProjectStopCause? = null,
        var terminationRequested: Boolean = false,
        var projectInputPreparationEpoch: Int = 0,
        var projectInputPreparationInFlight: Boolean = false,
        var projectInputExpectedAckSequence: Int? = null,
        var projectInputEndAwaitingAcknowledgement: Boolean = false,
        var projectInputComplete: Boolean = false,
        var projectInputReleaseUnproven: Boolean = false,
        val enteredProjectExecutionPhases:
            MutableList<R2ProjectExecutionPhase> = mutableListOf(),
        val completedProjectExecutionPhases:
            MutableList<R2ProjectExecutionPhase> = mutableListOf(),
        var activeProjectExecutionPhase:
            R2ProjectExecutionPhase? = null,
        var activeProjectExecutionPhaseProgress:
            R2ProjectExecutionPhaseProgress? = null,
        var projectPhaseTerminationEvidence:
            R2ProjectPhaseTerminationEvidence? = null,
        val projectExecutionPhaseProgressHistory:
            MutableList<R2ProjectExecutionPhaseProgress> = mutableListOf(),
        val phaseSequenceTwoObservedAtElapsedRealtimeNanos:
            MutableMap<R2ProjectExecutionPhase, Long> = mutableMapOf(),
        val projectExecutionPhaseTimingEvidence:
            MutableList<R2ProjectExecutionPhaseTimingEvidence> =
            mutableListOf(),
        var projectStopSelection: ProjectStopSelection? = null,
        var rendererTerminationRequestedAtPhase:
            R2ProjectExecutionPhase? = null,
        var deferredProjectResultStart: CompilerHostMessage.ProjectResultStart? = null,
        var projectResultAccumulator: R2ProjectResultAccumulator? = null,
        var projectResultFinalizing: Boolean = false,
        var projectResultFinalizerInFlight: Boolean = false,
        var stopTeardownProven: Boolean = false,
        var deferredProjectCompletion: DeferredProjectCompletion? = null,
    )

    private data class DeferredProjectCompletion(
        val outcome: R2ProjectCompilationOutcome,
        val beforeSignal: () -> Unit,
    )

    private data class ResultFinalizerTestControls(
        val delayMilliseconds: Int,
        val gate: CountDownLatch?,
    )

    private data class ProjectInputPreparationFence(
        val pending: PendingProbe,
        val sessionToken: CompilerSessionToken,
        val port: WebMessagePort,
        val workerGeneration: Int,
        val preparationEpoch: Int,
        val acknowledgedSequence: Int?,
    )

    private sealed interface ProjectInputPreparation {

        data object Complete : ProjectInputPreparation

        data class Prepared(
            val sequence: Int,
            val encoded: String,
            val completesOnAcknowledgement: Boolean,
        ) : ProjectInputPreparation
    }

    private sealed interface ResultFinalizerResume {

        data object Stopped : ResultFinalizerResume

        data class Deferred(
            val completion: DeferredProjectCompletion,
        ) : ResultFinalizerResume
    }

    private enum class CompilerCorrelationFaultForTesting {
        WRONG_REQUEST_ID_FAILURE,
    }

    companion object {

        // Input frames are ACK-backpressured, but input-end can be followed by the
        // ingress completion/ACK, six compile-phase boundaries, up to six
        // bounded progress markers and result start as one Worker-to-page burst.
        // Keep that bounded burst clear of AbortPolicy.
        private const val PROTOCOL_VERIFICATION_QUEUE_CAPACITY = 16
        private const val RESULT_VERIFICATION_QUEUE_CAPACITY = 1
        private const val INPUT_FRAME_QUEUE_CAPACITY = 2
        private const val TERMINAL_PUBLICATION_TIMEOUT_MILLIS = 5_000L
        private const val PROJECT_STOP_ACK_TIMEOUT_MILLIS = 5_000L
        private const val SESSION_TEARDOWN_RETRY_MILLIS = 250L
        private const val HOST_EXECUTOR_TERMINATION_WAIT_MILLIS = 1_000L
        private const val PROJECT_INPUT_RELEASE_WAIT_MILLIS = 1_000L
        private const val MAX_RESULT_FINALIZER_DELAY_MILLISECONDS_FOR_TESTING = 750
        private const val MAX_RESULT_FINALIZER_GATE_WAIT_MILLISECONDS_FOR_TESTING = 30_000L

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

internal data class R2RendererGoneEvidence(
    val requestId: String?,
    val phase: R2ProjectExecutionPhase?,
    val requestedAtPhase: R2ProjectExecutionPhase?,
    val didCrash: Boolean,
    val rendererPriorityAtExit: Int,
    val observedAtElapsedRealtimeNanos: Long,
    val sessionGeneration: Int,
    val workerGeneration: Int?,
)

data class CompilerHostSnapshot(
    val backend: String,
    val status: String,
    val compilerVersion: String?,
    val distributionFingerprint: String?,
    val declarationsFingerprint: String?,
    val declarationProfileFingerprint: String?,
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
            declarationProfileFingerprint = null,
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
