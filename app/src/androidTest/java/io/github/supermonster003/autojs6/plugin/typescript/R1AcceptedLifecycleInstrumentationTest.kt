package io.github.supermonster003.autojs6.plugin.typescript

import android.app.ActivityManager
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R1AcceptedLifecycleInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val activityManager
        get() = context.getSystemService(ActivityManager::class.java)

    @Test
    fun acceptedTransportProbeReturnsBeforeExactlyOneTerminalCallback() {
        bindCompilerService().use { session ->
            val fdEvidence = captureCompilerFdEvidence("completion")
            val files = newArchivePair("exactly-one")
            val requestId = newRequestId("exactly-one")
            val terminalArbiter = R1ClientTerminalArbiter(requestId)
            val callback = RecordingCallback(terminalArbiter)
            val ticket = startTransportProbe(
                compiler = session.compiler,
                requestId = requestId,
                files = files,
                callback = callback,
                mode = CompilerTransportProbeProtocol.MODE_COMPLETE,
                timeoutMs = COMPLETION_RACE_TIMEOUT_MILLIS,
                completionDelayMs = COMPLETION_RACE_DELAY_MILLIS,
            )
            callback.markStartReturned()

            assertAccepted(ticket, requestId)
            assertTrue(
                session.compiler.cancelCompilation(
                    acknowledgement(
                        requestId,
                        requireNotNull(
                            ticket.getString(
                                TypeScriptCompilerContract.KEY_JOB_TOKEN,
                            ),
                        ),
                    ),
                ),
            )
            assertFalse(callback.callbackBeforeStartReturned)
            val result = callback.awaitTerminal()
            assertEquals(requestId, result.getString(TypeScriptCompilerContract.KEY_REQUEST_ID))
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_FAILED,
                result.getString(TypeScriptCompilerContract.KEY_OUTCOME),
            )
            assertFalse(result.getBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, true))
            assertEquals(
                TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
                result.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(1, callback.terminalCount.get())
            assertEquals(0, callback.progressCount.get())
            assertEquals(
                R1ClientTerminalArbiter.Origin.REMOTE_CALLBACK,
                terminalArbiter.awaitTerminal(
                    CALLBACK_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ).origin,
            )
            assertFalse(
                "A local service-death terminal arriving after a remote terminal must lose.",
                terminalArbiter.acceptServiceDeath(),
            )
            assertEquals(1, terminalArbiter.terminalCount.get())
            assertOutputRemainsEmpty(files.output)
            fdEvidence.assertReleased()

            SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
            assertEquals(1, callback.terminalCount.get())
            files.delete()
        }
    }

    @Test
    fun acceptedTransportProbeCancellationIsIdempotentAndExactlyOnce() {
        bindCompilerService().use { session ->
            val fdEvidence = captureCompilerFdEvidence("cancellation")
            val files = newArchivePair("cancel")
            val callback = RecordingCallback()
            val requestId = newRequestId("cancel")
            val ticket = startTransportProbe(
                compiler = session.compiler,
                requestId = requestId,
                files = files,
                callback = callback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = CANCEL_RACE_TIMEOUT_MILLIS,
            )
            callback.markStartReturned()
            assertAccepted(ticket, requestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                session.compiler.cancelCompilation(
                    acknowledgement(requestId, jobToken),
                ),
            )

            assertFalse(
                session.compiler.cancelCompilation(
                    cancellation(requestId, "$jobToken-wrong"),
                ),
            )
            SystemClock.sleep(CANCEL_RACE_DELAY_MILLIS)
            assertTrue(
                session.compiler.cancelCompilation(
                    cancellation(requestId, jobToken),
                ),
            )
            assertTrue(
                "A repeated cancellation of the same terminal job must remain idempotent.",
                session.compiler.cancelCompilation(
                    cancellation(requestId, jobToken),
                ),
            )

            val result = callback.awaitTerminal()
            assertFalse(callback.callbackBeforeStartReturned)
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_CANCELLED,
                result.getString(TypeScriptCompilerContract.KEY_OUTCOME),
            )
            assertEquals(
                TypeScriptCompilerContract.ERROR_CANCELLED,
                result.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(1, callback.terminalCount.get())
            assertOutputRemainsEmpty(files.output)
            fdEvidence.assertReleased()
            SystemClock.sleep(CANCEL_RACE_OBSERVATION_MILLIS)
            assertEquals(1, callback.terminalCount.get())
            files.delete()
        }
    }

    @Test
    fun boundedQueueRejectsSaturationWithoutMutatingCallerOutput() {
        bindCompilerService().use { session ->
            val fdEvidence = captureCompilerFdEvidence("capacity")
            val activeFiles = newArchivePair("capacity-active")
            val rejectedFiles = newArchivePair("capacity-rejected")
            val activeCallback = RecordingCallback()
            val rejectedCallback = RecordingCallback()
            val activeRequestId = newRequestId("capacity-active")
            var activeJobToken: String? = null
            try {
                val activeTicket = startTransportProbe(
                    compiler = session.compiler,
                    requestId = activeRequestId,
                    files = activeFiles,
                    callback = activeCallback,
                    mode = CompilerTransportProbeProtocol.MODE_HOLD,
                    timeoutMs = 10_000L,
                )
                activeCallback.markStartReturned()
                assertAccepted(activeTicket, activeRequestId)
                val admittedJobToken = requireNotNull(
                    activeTicket.getString(
                        TypeScriptCompilerContract.KEY_JOB_TOKEN,
                    ),
                )
                activeJobToken = admittedJobToken
                assertTrue(
                    session.compiler.cancelCompilation(
                        acknowledgement(activeRequestId, admittedJobToken),
                    ),
                )
                awaitActiveJobCount(session.compiler, expected = 1)
                fdEvidence.assertActive()

                val rejectedRequestId = newRequestId("capacity-rejected")
                val rejectedTicket = startTransportProbe(
                    compiler = session.compiler,
                    requestId = rejectedRequestId,
                    files = rejectedFiles,
                    callback = rejectedCallback,
                    mode = CompilerTransportProbeProtocol.MODE_HOLD,
                    timeoutMs = 10_000L,
                )
                rejectedCallback.markStartReturned()

                assertFalse(
                    rejectedTicket.getBoolean(
                        TypeScriptCompilerContract.KEY_ACCEPTED,
                        true,
                    ),
                )
                assertEquals(
                    TypeScriptCompilerContract.ERROR_BUSY,
                    rejectedTicket.getString(
                        TypeScriptCompilerContract.KEY_ERROR_CODE,
                    ),
                )
                assertFalse(
                    rejectedTicket.containsKey(
                        TypeScriptCompilerContract.KEY_JOB_TOKEN,
                    ),
                )
                assertEquals(
                    "A rejected admission must not mutate caller-owned output.",
                    OUTPUT_SENTINEL,
                    rejectedFiles.output.readText(),
                )
                assertFalse(
                    "A rejected admission must not send a terminal callback.",
                    rejectedCallback.terminal.await(
                        NO_CALLBACK_OBSERVATION_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ),
                )
                assertEquals(0, rejectedCallback.terminalCount.get())
                awaitActiveJobCount(session.compiler, expected = 1)

                assertTrue(
                    session.compiler.cancelCompilation(
                        cancellation(activeRequestId, admittedJobToken),
                    ),
                )
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_CANCELLED,
                    activeCallback.awaitTerminal().getString(
                        TypeScriptCompilerContract.KEY_OUTCOME,
                    ),
                )
                awaitActiveJobCount(session.compiler, expected = 0)
                fdEvidence.assertReleased()
            } finally {
                activeJobToken?.let { token ->
                    runCatching {
                        session.compiler.cancelCompilation(
                            cancellation(activeRequestId, token),
                        )
                    }
                }
                activeFiles.delete()
                rejectedFiles.delete()
            }
        }
    }

    @Test
    fun unacknowledgedTransportProbeIsReapedWithoutAnOutOfOrderCallback() {
        bindCompilerService().use { session ->
            val fdEvidence = captureCompilerFdEvidence("unacknowledged")
            val files = newArchivePair("unacknowledged")
            val callback = RecordingCallback()
            val requestId = newRequestId("unacknowledged")
            try {
                val ticket = startTransportProbe(
                    compiler = session.compiler,
                    requestId = requestId,
                    files = files,
                    callback = callback,
                    mode = CompilerTransportProbeProtocol.MODE_HOLD,
                    timeoutMs = UNACKNOWLEDGED_TIMEOUT_MILLIS,
                )
                callback.markStartReturned()
                assertAccepted(ticket, requestId)
                val jobToken = requireNotNull(
                    ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
                )

                fdEvidence.assertActive()
                awaitActiveJobCount(session.compiler, expected = 0)
                assertOutputRemainsEmpty(files.output)
                fdEvidence.assertReleased()
                assertFalse(
                    "An unacknowledged internal probe must be reaped without risking an " +
                        "out-of-order callback.",
                    callback.terminal.await(
                        NO_CALLBACK_OBSERVATION_MILLIS,
                        TimeUnit.MILLISECONDS,
                    ),
                )
                assertEquals(0, callback.terminalCount.get())
                assertTrue(
                    "Cancellation remains idempotent after the admission reaper wins.",
                    session.compiler.cancelCompilation(
                        cancellation(requestId, jobToken),
                    ),
                )
            } finally {
                files.delete()
            }
        }
    }

    @Test
    fun acceptedTransportProbeTimeoutClosesOutputBeforeExactlyOneCallback() {
        bindCompilerService().use { session ->
            val fdEvidence = captureCompilerFdEvidence("timeout")
            val files = newArchivePair("timeout")
            val callback = RecordingCallback()
            val requestId = newRequestId("timeout")
            val ticket = startTransportProbe(
                compiler = session.compiler,
                requestId = requestId,
                files = files,
                callback = callback,
                mode = CompilerTransportProbeProtocol.MODE_COMPLETE,
                timeoutMs = TIMEOUT_RACE_MILLIS,
                completionDelayMs = TIMEOUT_RACE_MILLIS,
            )
            callback.markStartReturned()
            assertAccepted(ticket, requestId)
            assertTrue(
                session.compiler.cancelCompilation(
                    acknowledgement(
                        requestId,
                        requireNotNull(
                            ticket.getString(
                                TypeScriptCompilerContract.KEY_JOB_TOKEN,
                            ),
                        ),
                    ),
                ),
            )

            val result = callback.awaitTerminal()
            assertFalse(callback.callbackBeforeStartReturned)
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                result.getString(TypeScriptCompilerContract.KEY_OUTCOME),
            )
            assertEquals(
                TypeScriptCompilerContract.ERROR_TIMEOUT,
                result.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(1, callback.terminalCount.get())
            assertOutputRemainsEmpty(files.output)
            fdEvidence.assertReleased()
            SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
            assertEquals(1, callback.terminalCount.get())
            files.delete()
        }
    }

    @Test
    fun gracefulServiceCloseAtomicallySuppressesDelayedCompletion() {
        val session = bindCompilerService()
        val files = newArchivePair("graceful-close")
        val callback = RecordingCallback()
        val requestId = newRequestId("graceful-close")
        val fdEvidence = captureCompilerFdEvidence("graceful-close")
        try {
            val ticket = startTransportProbe(
                compiler = session.compiler,
                requestId = requestId,
                files = files,
                callback = callback,
                mode = CompilerTransportProbeProtocol.MODE_COMPLETE,
                timeoutMs = SERVICE_CLOSE_DELAY_MILLIS,
                completionDelayMs = SERVICE_CLOSE_DELAY_MILLIS,
            )
            callback.markStartReturned()
            assertAccepted(ticket, requestId)
            assertTrue(
                session.compiler.cancelCompilation(
                    acknowledgement(
                        requestId,
                        requireNotNull(
                            ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
                        ),
                    ),
                ),
            )
            fdEvidence.assertActive()

            session.close()

            fdEvidence.awaitReleasedAfterLifecycleClose()
            assertFalse(
                "Service-close intent must suppress a later scheduled callback.",
                callback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, callback.terminalCount.get())
            assertOutputRemainsEmpty(files.output)
        } finally {
            session.close()
            files.delete()
        }

        bindCompilerService().use { rebound ->
            assertTrue(rebound.rawBinder.pingBinder())
            assertEquals(
                TypeScriptCompilerContract.VERSION,
                rebound.compiler.contractVersion,
            )
        }
    }

    @Test
    fun gracefulServiceCloseSuppressesCompletionClaimedBeforeCleanup() {
        val session = bindCompilerService()
        val files = newArchivePair("claimed-completion-close")
        val callback = RecordingCallback()
        val requestId = newRequestId("claimed-completion-close")
        val fdEvidence = captureCompilerFdEvidence("claimed-completion-close")
        try {
            val ticket = startTransportProbe(
                compiler = session.compiler,
                requestId = requestId,
                files = files,
                callback = callback,
                mode = CompilerTransportProbeProtocol.MODE_COMPLETE,
                timeoutMs = SERVICE_CLOSE_DELAY_MILLIS,
                completionDelayMs = CLAIMED_COMPLETION_DELAY_MILLIS,
                terminalCleanupDelayMs = CLAIMED_TERMINAL_CLEANUP_DELAY_MILLIS,
            )
            callback.markStartReturned()
            assertAccepted(ticket, requestId)
            assertTrue(
                session.compiler.cancelCompilation(
                    acknowledgement(
                        requestId,
                        requireNotNull(
                            ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
                        ),
                    ),
                ),
            )

            awaitTerminalIntentClaimed(session.compiler)
            fdEvidence.assertActive()
            session.close()

            fdEvidence.awaitReleasedAfterLifecycleClose()
            assertFalse(
                "Service teardown must suppress an already-claimed terminal callback.",
                callback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, callback.terminalCount.get())
            assertOutputRemainsEmpty(files.output)
        } finally {
            session.close()
            files.delete()
        }

        bindCompilerService().use { rebound ->
            assertTrue(rebound.rawBinder.pingBinder())
            assertEquals(
                TypeScriptCompilerContract.VERSION,
                rebound.compiler.contractVersion,
            )
        }
    }

    @Test
    fun callbackProcessDeathReleasesTheAcceptedJobWhileCompilerSurvives() {
        bindCompilerService().use { compilerSession ->
            val fdEvidence = captureCompilerFdEvidence("callback-death")
            val files = newArchivePair("callback-death")
            val helperConnection = AwaitingMessengerConnection()
            val helperBound = context.bindService(
                Intent(context, R1CallbackClientProbeService::class.java),
                helperConnection,
                Context.BIND_AUTO_CREATE,
            )
            assertTrue("Failed to bind the callback-owner helper process.", helperBound)
            try {
                val started = CountDownLatch(1)
                val startedResult = AtomicReference<Bundle>()
                val reply = Messenger(
                    Handler(Looper.getMainLooper()) { message ->
                        if (message.what == R1CallbackClientProbeService.MSG_STARTED) {
                            startedResult.set(Bundle(message.data))
                            started.countDown()
                            true
                        } else {
                            false
                        }
                    },
                )
                helperConnection.awaitMessenger().send(
                    Message.obtain(
                        null,
                        R1CallbackClientProbeService.MSG_START,
                    ).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putString(
                                R1CallbackClientProbeService.KEY_REQUEST_ID,
                                newRequestId("callback-death"),
                            )
                            putString(
                                R1CallbackClientProbeService.KEY_INPUT_PATH,
                                files.input.absolutePath,
                            )
                            putString(
                                R1CallbackClientProbeService.KEY_OUTPUT_PATH,
                                files.output.absolutePath,
                            )
                        }
                    },
                )
                assertTrue(
                    "Callback helper did not finish accepted admission.",
                    started.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                val result = requireNotNull(startedResult.get())
                assertTrue(
                    result.getString(R1CallbackClientProbeService.KEY_ERROR_MESSAGE),
                    result.getBoolean(R1CallbackClientProbeService.KEY_ACCEPTED, false),
                )
                assertNotNull(
                    result.getString(R1CallbackClientProbeService.KEY_JOB_TOKEN),
                )
                assertTrue(
                    "Callback helper did not acknowledge the accepted admission.",
                    result.getBoolean(
                        R1CallbackClientProbeService.KEY_ACKNOWLEDGED,
                        false,
                    ),
                )
                awaitActiveJobCount(compilerSession.compiler, expected = 1)
                fdEvidence.assertActive()

                val helperPid = result.getInt(
                    R1CallbackClientProbeService.KEY_HELPER_PID,
                    -1,
                )
                assertTrue(
                    "Callback helper did not report a distinct process.",
                    helperPid > 0 && helperPid != Process.myPid(),
                )
                Process.killProcess(helperPid)

                awaitActiveJobCount(compilerSession.compiler, expected = 0)
                assertTrue(
                    "Compiler service died with the callback owner.",
                    compilerSession.rawBinder.pingBinder(),
                )
                assertOutputRemainsEmpty(files.output)
                fdEvidence.assertReleased()
            } finally {
                runCatching { context.unbindService(helperConnection) }
                files.delete()
            }
        }
    }

    @Test
    fun abruptCompilerServiceDeathProducesOneLocalTerminalAndNoRemoteCallback() {
        val session = bindCompilerService()
        val fdEvidence = captureCompilerFdEvidence("service-death")
        val files = newArchivePair("service-death")
        try {
            val requestId = newRequestId("service-death")
            val terminalArbiter = R1ClientTerminalArbiter(requestId)
            val callback = RecordingCallback(terminalArbiter)
            val ticket = startTransportProbe(
                compiler = session.compiler,
                requestId = requestId,
                files = files,
                callback = callback,
                mode = CompilerTransportProbeProtocol.MODE_COMPLETE,
                timeoutMs = 30_000L,
                completionDelayMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
            )
            callback.markStartReturned()
            assertAccepted(ticket, requestId)
            assertTrue(
                session.compiler.cancelCompilation(
                    acknowledgement(
                        requestId,
                        requireNotNull(
                            ticket.getString(
                                TypeScriptCompilerContract.KEY_JOB_TOKEN,
                            ),
                        ),
                    ),
                ),
            )
            fdEvidence.assertActive()

            val compilerProcess = awaitCompilerProcess()
            val binderDeath = CountDownLatch(1)
            session.rawBinder.linkToDeath(
                {
                    terminalArbiter.acceptServiceDeath()
                    binderDeath.countDown()
                },
                0,
            )
            Process.killProcess(compilerProcess.pid)

            assertTrue(
                "The accepted job's compiler Binder did not die.",
                binderDeath.await(PROCESS_DEATH_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            val localTerminal = terminalArbiter.awaitTerminal(
                PROCESS_DEATH_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            assertEquals(
                R1ClientTerminalArbiter.Origin.SERVICE_DEATH,
                localTerminal.origin,
            )
            assertEquals(
                TypeScriptCompilerContract.OUTCOME_FAILED,
                localTerminal.result.getString(
                    TypeScriptCompilerContract.KEY_OUTCOME,
                ),
            )
            assertEquals(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                localTerminal.result.getString(
                    TypeScriptCompilerContract.KEY_ERROR_CODE,
                ),
            )
            assertEquals(1, terminalArbiter.terminalCount.get())
            assertFalse(
                "A dead service cannot promise a remote terminal callback.",
                callback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, callback.terminalCount.get())
            assertEquals(1, terminalArbiter.terminalCount.get())
            assertFalse(
                "A correlated remote terminal arriving after service death must lose.",
                terminalArbiter.acceptRemote(
                    remoteCompletionResult(requestId),
                ),
            )
            assertEquals(1, terminalArbiter.terminalCount.get())
            assertOutputRemainsEmpty(files.output)
            fdEvidence.awaitProcessGone()
        } finally {
            session.close()
            files.delete()
        }

        bindCompilerService().use { rebound ->
            assertTrue(rebound.rawBinder.pingBinder())
            assertEquals(
                TypeScriptCompilerContract.VERSION,
                rebound.compiler.contractVersion,
            )
        }
    }

    @Test
    fun cleanupUnprovenRequiresOldProcessDeathBeforeNewProcessAdmission() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        val poisonedSession = bindCompilerService()
        val poisonedFiles = newArchivePair("supervisor-poison")
        val poisonedCallback = RecordingCallback()
        val poisonedRequestId = newRequestId("supervisor-poison")
        val oldFdEvidence = captureCompilerFdEvidence("supervisor-poison")
        var oldEpoch: R2CompilerProcessEpochIdentity? = null
        var deathProven = false
        var quarantinedBinding: QuarantineBinding? = null
        try {
            val ticket = startTransportProbe(
                compiler = poisonedSession.compiler,
                requestId = poisonedRequestId,
                files = poisonedFiles,
                callback = poisonedCallback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
                forceCleanupUnproven = true,
            )
            poisonedCallback.markStartReturned()
            assertAccepted(ticket, poisonedRequestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    acknowledgement(poisonedRequestId, jobToken),
                ),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    cancellation(poisonedRequestId, jobToken),
                ),
            )

            awaitActiveJobCount(poisonedSession.compiler, expected = 1)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertFalse(
                "Cleanup-unproven must not publish an ordinary callback.",
                poisonedCallback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, poisonedCallback.terminalCount.get())

            val poisonInfo = poisonedSession.compiler.compilerInfo
            val capturedOldEpoch = R2CompilerProcessEpochIdentity.from(poisonInfo)
            oldEpoch = capturedOldEpoch
            assertEquals(Process.myUid(), capturedOldEpoch.uid)
            assertEquals(COMPILER_PROCESS_NAME, capturedOldEpoch.processName)
            assertEquals(oldFdEvidence.identity.pid, capturedOldEpoch.pid)
            assertEquals(
                oldFdEvidence.identity.startTimeTicks,
                capturedOldEpoch.startTimeTicks,
            )
            assertTrue(
                poisonInfo.getBoolean(
                    R2CompilerProcessSupervisorProtocol
                        .KEY_REQUIRES_EXTERNAL_SUPERVISION,
                    false,
                ),
            )

            val unavailable = poisonedSession.compiler.prewarm(validPrewarmOptions())
            assertEquals(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                unavailable.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            assertEquals(
                "Compiler admission requires external supervision.",
                unavailable.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE),
            )
            assertTrue(poisonedSession.rawBinder.isBinderAlive)

            val staleEpoch = capturedOldEpoch.copy(
                nonce = capturedOldEpoch.nonce.replaceFirst(
                    capturedOldEpoch.nonce.first(),
                    if (capturedOldEpoch.nonce.first() == 'a') 'b' else 'a',
                ),
            )
            val staleTerminationFailure = runCatching {
                R2CompilerProcessSupervisor(context).terminatePoisonedEpoch(
                    compiler = poisonedSession.compiler,
                    rawBinder = poisonedSession.rawBinder,
                    oldIdentity = staleEpoch,
                )
            }.exceptionOrNull()
            assertTrue(
                staleTerminationFailure is R2CompilerProcessSupervisionException,
            )
            assertTrue(poisonedSession.rawBinder.isBinderAlive)
            assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
            assertEquals(
                R2CompilerProcessObservation.SAME,
                R2CompilerProcessProcfs().observe(capturedOldEpoch),
            )

            val deathProofReached = CountDownLatch(1)
            val allowSuccessorFenceClear = CountDownLatch(1)
            val supervisorExecutor = Executors.newSingleThreadExecutor()
            val proofFuture = supervisorExecutor.submit<R2CompilerProcessTerminationProof> {
                R2CompilerProcessSupervisor(
                    context = context,
                    beforeSuccessorFenceClear = {
                        deathProofReached.countDown()
                        check(
                            allowSuccessorFenceClear.await(
                                CALLBACK_TIMEOUT_SECONDS,
                                TimeUnit.SECONDS,
                            ),
                        ) {
                            "Timed out holding the successor admission fence."
                        }
                    },
                ).terminatePoisonedEpoch(
                    compiler = poisonedSession.compiler,
                    rawBinder = poisonedSession.rawBinder,
                    oldIdentity = capturedOldEpoch,
                )
            }
            val proof = try {
                assertTrue(
                    "Supervisor did not reach old Binder/procfs death proof.",
                    deathProofReached.await(
                        CALLBACK_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                    ),
                )
                assertTrue(R2CompilerProcessQuarantine.hasMarker(context))
                quarantinedBinding = bindCompilerWhileQuarantined()
                allowSuccessorFenceClear.countDown()
                proofFuture.get(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } finally {
                allowSuccessorFenceClear.countDown()
                supervisorExecutor.shutdownNow()
            }
            assertTrue(proof.terminationCommandAccepted)
            assertTrue(proof.binderDeathObserved)
            assertTrue(proof.deathProven)
            assertTrue(proof.successorFenceCleared)
            assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
            val recoveredBinder = requireNotNull(quarantinedBinding).awaitBinder()
            assertTrue(recoveredBinder.pingBinder())
            val recoveredCompiler =
                ITypeScriptCompilerPlugin.Stub.asInterface(recoveredBinder)
            assertEquals(
                TypeScriptCompilerContract.VERSION,
                recoveredCompiler.contractVersion,
            )
            assertNotEquals(
                capturedOldEpoch,
                R2CompilerProcessEpochIdentity.from(
                    enableProcessSupervisorTelemetry(recoveredCompiler),
                ),
            )
            assertTrue(
                proof.finalObservation == R2CompilerProcessObservation.ABSENT ||
                    proof.finalObservation == R2CompilerProcessObservation.REUSED,
            )
            deathProven = true
            oldFdEvidence.awaitProcessGone()
            assertFalse(poisonedSession.rawBinder.isBinderAlive)
            assertEquals(0, poisonedCallback.terminalCount.get())
        } finally {
            val capturedEpoch = oldEpoch
            if (
                !deathProven &&
                capturedEpoch != null &&
                poisonedSession.rawBinder.isBinderAlive
            ) {
                runCatching {
                    R2CompilerProcessSupervisor(context).terminatePoisonedEpoch(
                        compiler = poisonedSession.compiler,
                        rawBinder = poisonedSession.rawBinder,
                        oldIdentity = capturedEpoch,
                    )
                }
            }
            quarantinedBinding?.close()
            poisonedSession.close()
            poisonedFiles.delete()
        }

        val replacementFiles = newArchivePair("supervisor-recovery")
        try {
            bindCompilerService().use { replacementSession ->
                val replacementFdEvidence =
                    captureCompilerFdEvidence("supervisor-recovery")
                val replacementCallback = RecordingCallback()
                val replacementRequestId = newRequestId("supervisor-recovery")
                val replacementTicket = startTransportProbe(
                    compiler = replacementSession.compiler,
                    requestId = replacementRequestId,
                    files = replacementFiles,
                    callback = replacementCallback,
                    mode = CompilerTransportProbeProtocol.MODE_HOLD,
                    timeoutMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
                )
                replacementCallback.markStartReturned()
                assertAccepted(replacementTicket, replacementRequestId)
                val replacementToken = requireNotNull(
                    replacementTicket.getString(
                        TypeScriptCompilerContract.KEY_JOB_TOKEN,
                    ),
                )
                val replacementInfo = replacementSession.compiler.compilerInfo
                val replacementEpoch =
                    R2CompilerProcessEpochIdentity.from(replacementInfo)
                assertNotEquals(requireNotNull(oldEpoch), replacementEpoch)
                assertNotEquals(
                    oldFdEvidence.identity,
                    replacementFdEvidence.identity,
                )
                assertFalse(
                    replacementInfo.getBoolean(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_REQUIRES_EXTERNAL_SUPERVISION,
                        true,
                    ),
                )
                val healthyTerminationFailure = runCatching {
                    R2CompilerProcessSupervisor(context).terminatePoisonedEpoch(
                        compiler = replacementSession.compiler,
                        rawBinder = replacementSession.rawBinder,
                        oldIdentity = replacementEpoch,
                    )
                }.exceptionOrNull()
                assertTrue(
                    healthyTerminationFailure is
                        R2CompilerProcessSupervisionException,
                )
                assertTrue(replacementSession.rawBinder.isBinderAlive)
                assertFalse(R2CompilerProcessQuarantine.hasMarker(context))

                assertTrue(
                    replacementSession.compiler.cancelCompilation(
                        acknowledgement(replacementRequestId, replacementToken),
                    ),
                )
                replacementFdEvidence.assertActive()
                assertTrue(
                    replacementSession.compiler.cancelCompilation(
                        cancellation(replacementRequestId, replacementToken),
                    ),
                )
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_CANCELLED,
                    replacementCallback.awaitTerminal().getString(
                        TypeScriptCompilerContract.KEY_OUTCOME,
                    ),
                )
                awaitActiveJobCount(replacementSession.compiler, expected = 0)
                replacementFdEvidence.assertReleased()
                assertOutputRemainsEmpty(replacementFiles.output)
            }
        } finally {
            replacementFiles.delete()
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        }
    }

    @Test
    fun cleanupUnprovenIsAutomaticallyRecoveredByProductionSupervisorOwner() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        val poisonedSession = bindCompilerService()
        val poisonedFiles = newArchivePair("automatic-supervisor-poison")
        val poisonedCallback = RecordingCallback()
        val poisonedRequestId = newRequestId("automatic-supervisor-poison")
        val oldFdEvidence =
            captureCompilerFdEvidence("automatic-supervisor-poison")
        var oldEpoch: R2CompilerProcessEpochIdentity? = null
        var automaticRecoveryVerified = false
        try {
            val registration = enableAutomaticProcessSupervisorForTesting(
                poisonedSession.compiler,
            )
            assertTrue(
                registration.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_REGISTRATION_READY,
                    false,
                ),
            )
            assertTrue(
                registration.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_AUTOMATIC_TERMINATION_ENABLED,
                    false,
                ),
            )
            val capturedOldEpoch =
                R2CompilerProcessEpochIdentity.from(registration)
            oldEpoch = capturedOldEpoch
            assertEquals(Process.myUid(), capturedOldEpoch.uid)
            assertEquals(COMPILER_PROCESS_NAME, capturedOldEpoch.processName)
            assertEquals(oldFdEvidence.identity.pid, capturedOldEpoch.pid)
            assertEquals(
                oldFdEvidence.identity.startTimeTicks,
                capturedOldEpoch.startTimeTicks,
            )

            val ticket = startTransportProbe(
                compiler = poisonedSession.compiler,
                requestId = poisonedRequestId,
                files = poisonedFiles,
                callback = poisonedCallback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
                forceCleanupUnproven = true,
            )
            poisonedCallback.markStartReturned()
            assertAccepted(ticket, poisonedRequestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    acknowledgement(poisonedRequestId, jobToken),
                ),
            )
            awaitActiveJobCount(poisonedSession.compiler, expected = 1)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)

            val binderDeath = CountDownLatch(1)
            poisonedSession.rawBinder.linkToDeath(
                { binderDeath.countDown() },
                0,
            )
            val cancellationOutcome = runCatching {
                poisonedSession.compiler.cancelCompilation(
                    cancellation(poisonedRequestId, jobToken),
                )
            }

            assertFalse(
                "Cleanup-unproven must not publish an ordinary callback.",
                poisonedCallback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, poisonedCallback.terminalCount.get())
            assertTrue(
                "The production supervisor owner did not kill the poisoned Binder.",
                binderDeath.await(
                    SUPERVISOR_OWNER_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            assertTrue(
                "Cleanup-unproven cancellation was neither accepted nor " +
                    "overtaken by exact Binder death.",
                cancellationOutcome.getOrNull() == true ||
                    cancellationOutcome.exceptionOrNull() is DeadObjectException,
            )
            assertFalse(poisonedSession.rawBinder.isBinderAlive)
            oldFdEvidence.awaitReleasedAfterLifecycleClose()
            oldFdEvidence.awaitProcessGone()
            val finalObservation =
                R2CompilerProcessProcfs().observe(capturedOldEpoch)
            assertTrue(
                "The old compiler epoch remained present in procfs: $finalObservation",
                finalObservation == R2CompilerProcessObservation.ABSENT ||
                    finalObservation == R2CompilerProcessObservation.REUSED,
            )
            awaitCompilerProcessQuarantineCleared()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertEquals(0, poisonedCallback.terminalCount.get())
            automaticRecoveryVerified = true
        } finally {
            if (!automaticRecoveryVerified) {
                val capturedOldEpoch = oldEpoch
                if (
                    capturedOldEpoch != null &&
                    poisonedSession.rawBinder.isBinderAlive
                ) {
                    /*
                     * Failure-only cleanup. The successful path must be owned entirely by the
                     * production supervisor control plane.
                     */
                    runCatching {
                        R2CompilerProcessSupervisor(context)
                            .terminatePoisonedEpoch(
                                compiler = poisonedSession.compiler,
                                rawBinder = poisonedSession.rawBinder,
                                oldIdentity = capturedOldEpoch,
                            )
                    }
                }
                if (poisonedSession.rawBinder.isBinderAlive) {
                    /*
                     * A failure before poison leaves the debug automatic-mode bit in this
                     * compiler epoch. Retire that epoch so later device tests start clean.
                     */
                    Process.killProcess(oldFdEvidence.identity.pid)
                }
                runCatching { oldFdEvidence.awaitProcessGone() }
                runCatching {
                    R2CompilerProcessSupervisor(context)
                        .reconcilePersistedQuarantine()
                }
                capturedOldEpoch?.let { identity ->
                    R2CompilerProcessQuarantine.clearExactOrAbsent(
                        context = context,
                        expected = identity,
                    )
                }
            }
            poisonedSession.close()
            poisonedFiles.delete()
        }

        try {
            assertFreshCompilerEpochAcceptsCancellation(
                oldEpoch = requireNotNull(oldEpoch),
                oldFdIdentity = oldFdEvidence.identity,
                label = "automatic-supervisor-recovery",
            )
        } finally {
            R2CompilerProcessQuarantine.clearExactOrAbsent(
                context = context,
                expected = oldEpoch,
            )
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        }
    }

    @Test
    fun poisonDuringProductionSupervisorRestartIsReplayedAfterRebind() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        val poisonedSession = bindCompilerService()
        val poisonedFiles = newArchivePair("supervisor-rebind-poison")
        val poisonedCallback = RecordingCallback()
        val poisonedRequestId = newRequestId("supervisor-rebind-poison")
        val oldFdEvidence =
            captureCompilerFdEvidence("supervisor-rebind-poison")
        var oldEpoch: R2CompilerProcessEpochIdentity? = null
        var reconnectPaused = false
        var automaticRecoveryVerified = false
        try {
            val registration = enableAutomaticProcessSupervisorForTesting(
                poisonedSession.compiler,
            )
            val capturedOldEpoch =
                R2CompilerProcessEpochIdentity.from(registration)
            oldEpoch = capturedOldEpoch
            assertEquals(oldFdEvidence.identity.pid, capturedOldEpoch.pid)
            assertEquals(
                oldFdEvidence.identity.startTimeTicks,
                capturedOldEpoch.startTimeTicks,
            )
            val oldOwner = registeredSupervisorOwner(registration)
            assertExactSupervisorOwnerLive(
                expected = oldOwner,
                compilerPid = capturedOldEpoch.pid,
            )
            val oldRegistry = observeSupervisorRegistryBinder()

            val ticket = startTransportProbe(
                compiler = poisonedSession.compiler,
                requestId = poisonedRequestId,
                files = poisonedFiles,
                callback = poisonedCallback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                forceCleanupUnproven = true,
            )
            poisonedCallback.markStartReturned()
            assertAccepted(ticket, poisonedRequestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    acknowledgement(poisonedRequestId, jobToken),
                ),
            )
            awaitActiveJobCount(poisonedSession.compiler, expected = 1)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)

            val pausedInfo = setSupervisorReconnectPausedForTesting(
                compiler = poisonedSession.compiler,
                paused = true,
            )
            reconnectPaused = true
            assertSupervisorControlState(
                info = pausedInfo,
                expectedRegistryConnected = true,
                expectedReady = true,
                expectedPaused = true,
                expectedRequiresExternalSupervision = false,
                expectedCompilerEpoch = capturedOldEpoch,
            )
            assertEquals(oldOwner, registeredSupervisorOwner(pausedInfo))

            val compilerBinderDeath = CountDownLatch(1)
            poisonedSession.rawBinder.linkToDeath(
                { compilerBinderDeath.countDown() },
                0,
            )
            killExactSupervisorOwner(
                expected = oldOwner,
                compilerPid = capturedOldEpoch.pid,
            )
            assertTrue(
                "The old production supervisor registry Binder did not die.",
                oldRegistry.death.await(
                    PROCESS_DEATH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            assertFalse(oldRegistry.binder.isBinderAlive)
            awaitSupervisorOwnerGone(oldOwner)

            val replacementOwner =
                awaitDifferentSupervisorOwner(oldOwner)
            assertExactSupervisorOwnerLive(
                expected = replacementOwner,
                compilerPid = capturedOldEpoch.pid,
            )
            val pausedRebindInfo = awaitSupervisorControlState(
                compiler = poisonedSession.compiler,
                expectedRegistryConnected = true,
                expectedReady = false,
                expectedPaused = true,
                expectedRequiresExternalSupervision = false,
                expectedCompilerEpoch = capturedOldEpoch,
            )
            assertFalse(
                pausedRebindInfo.containsKey(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_REGISTERED_SUPERVISOR_PID,
                ),
            )
            assertFalse(
                pausedRebindInfo.containsKey(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_REGISTERED_SUPERVISOR_START_TIME_TICKS,
                ),
            )
            assertTrue(poisonedSession.rawBinder.isBinderAlive)
            assertFalse(R2CompilerProcessQuarantine.hasMarker(context))

            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    cancellation(poisonedRequestId, jobToken),
                ),
            )
            assertFalse(
                "Cleanup-unproven must remain callback-silent while " +
                    "supervisor registration is paused.",
                poisonedCallback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, poisonedCallback.terminalCount.get())
            awaitSupervisorControlState(
                compiler = poisonedSession.compiler,
                expectedRegistryConnected = true,
                expectedReady = false,
                expectedPaused = true,
                expectedRequiresExternalSupervision = true,
                expectedCompilerEpoch = capturedOldEpoch,
            )
            assertTrue(poisonedSession.rawBinder.isBinderAlive)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertExactSupervisorOwnerLive(
                expected = replacementOwner,
                compilerPid = capturedOldEpoch.pid,
            )

            val resumeOutcome = runCatching {
                setSupervisorReconnectPausedForTesting(
                    compiler = poisonedSession.compiler,
                    paused = false,
                )
            }
            if (
                resumeOutcome.isSuccess ||
                resumeOutcome.exceptionOrNull() is DeadObjectException
            ) {
                reconnectPaused = false
            }
            resumeOutcome.getOrNull()?.let { resumedInfo ->
                assertFalse(
                    resumedInfo.getBoolean(
                        R2CompilerProcessSupervisorControlProtocol
                            .KEY_RECONNECT_PAUSED,
                        true,
                    ),
                )
            }
            assertTrue(
                "Releasing the registration gate neither returned nor was " +
                    "overtaken by exact compiler Binder death.",
                resumeOutcome.isSuccess ||
                    resumeOutcome.exceptionOrNull() is DeadObjectException,
            )
            assertTrue(
                "The replacement production supervisor did not terminate " +
                    "the replayed poisoned epoch.",
                compilerBinderDeath.await(
                    SUPERVISOR_OWNER_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            assertFalse(poisonedSession.rawBinder.isBinderAlive)
            oldFdEvidence.awaitReleasedAfterLifecycleClose()
            oldFdEvidence.awaitProcessGone()
            val finalObservation =
                R2CompilerProcessProcfs().observe(capturedOldEpoch)
            assertTrue(
                "The poisoned compiler epoch remained present in procfs: " +
                    finalObservation,
                finalObservation == R2CompilerProcessObservation.ABSENT ||
                    finalObservation == R2CompilerProcessObservation.REUSED,
            )
            awaitCompilerProcessQuarantineCleared()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertEquals(0, poisonedCallback.terminalCount.get())
            automaticRecoveryVerified = true
        } finally {
            if (poisonedSession.rawBinder.isBinderAlive) {
                runCatching {
                    setSupervisorReconnectPausedForTesting(
                        compiler = poisonedSession.compiler,
                        paused = false,
                    )
                }
                reconnectPaused = false
            }
            if (!automaticRecoveryVerified) {
                val capturedOldEpoch = oldEpoch
                if (
                    capturedOldEpoch != null &&
                    poisonedSession.rawBinder.isBinderAlive
                ) {
                    runCatching {
                        R2CompilerProcessSupervisor(context)
                            .terminatePoisonedEpoch(
                                compiler = poisonedSession.compiler,
                                rawBinder = poisonedSession.rawBinder,
                                oldIdentity = capturedOldEpoch,
                            )
                    }
                }
                if (poisonedSession.rawBinder.isBinderAlive) {
                    Process.killProcess(oldFdEvidence.identity.pid)
                }
                runCatching { oldFdEvidence.awaitProcessGone() }
                runCatching {
                    R2CompilerProcessSupervisor(context)
                        .reconcilePersistedQuarantine()
                }
                capturedOldEpoch?.let { identity ->
                    R2CompilerProcessQuarantine.clearExactOrAbsent(
                        context = context,
                        expected = identity,
                    )
                }
            }
            try {
                poisonedSession.close()
            } finally {
                poisonedFiles.delete()
                R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
            }
        }

        try {
            assertFreshCompilerEpochAcceptsCancellation(
                oldEpoch = requireNotNull(oldEpoch),
                oldFdIdentity = oldFdEvidence.identity,
                label = "supervisor-rebind-recovery",
            )
        } finally {
            R2CompilerProcessQuarantine.clearExactOrAbsent(
                context = context,
                expected = oldEpoch,
            )
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        }
    }

    @Test
    fun retiredSupervisorCallbacksCannotRetireReadySuccessorGeneration() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        val poisonedSession = bindCompilerService()
        val poisonedFiles = newArchivePair("retired-supervisor-callbacks")
        val poisonedCallback = RecordingCallback()
        val poisonedRequestId =
            newRequestId("retired-supervisor-callbacks")
        val oldFdEvidence =
            captureCompilerFdEvidence("retired-supervisor-callbacks")
        var oldEpoch: R2CompilerProcessEpochIdentity? = null
        var automaticRecoveryVerified = false
        try {
            val registration = enableAutomaticProcessSupervisorForTesting(
                poisonedSession.compiler,
            )
            val capturedOldEpoch =
                R2CompilerProcessEpochIdentity.from(registration)
            oldEpoch = capturedOldEpoch
            assertEquals(oldFdEvidence.identity.pid, capturedOldEpoch.pid)
            assertEquals(
                oldFdEvidence.identity.startTimeTicks,
                capturedOldEpoch.startTimeTicks,
            )
            val oldOwner = registeredSupervisorOwner(registration)
            assertExactSupervisorOwnerLive(
                expected = oldOwner,
                compilerPid = capturedOldEpoch.pid,
            )
            val oldGeneration =
                supervisorConnectionGeneration(registration)
            val oldRegistry = observeSupervisorRegistryBinder()

            val ticket = startTransportProbe(
                compiler = poisonedSession.compiler,
                requestId = poisonedRequestId,
                files = poisonedFiles,
                callback = poisonedCallback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                forceCleanupUnproven = true,
            )
            poisonedCallback.markStartReturned()
            assertAccepted(ticket, poisonedRequestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    acknowledgement(poisonedRequestId, jobToken),
                ),
            )
            awaitActiveJobCount(poisonedSession.compiler, expected = 1)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)

            val armedInfo = executeSupervisorRetiredCallbackCommandForTesting(
                compiler = poisonedSession.compiler,
                command =
                    R2CompilerProcessSupervisorControlProtocol
                        .COMMAND_ARM_RETIRED_CALLBACKS,
            )
            assertSupervisorControlState(
                info = armedInfo,
                expectedRegistryConnected = true,
                expectedReady = true,
                expectedPaused = false,
                expectedRequiresExternalSupervision = false,
                expectedCompilerEpoch = capturedOldEpoch,
            )
            assertTrue(
                armedInfo.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CALLBACK_REPLAY_ARMED,
                    false,
                ),
            )
            assertEquals(
                oldGeneration,
                supervisorConnectionGeneration(armedInfo),
            )
            assertEquals(oldOwner, registeredSupervisorOwner(armedInfo))

            killExactSupervisorOwner(
                expected = oldOwner,
                compilerPid = capturedOldEpoch.pid,
            )
            assertTrue(
                "The retired supervisor registry Binder did not die.",
                oldRegistry.death.await(
                    PROCESS_DEATH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            assertFalse(oldRegistry.binder.isBinderAlive)
            awaitSupervisorOwnerGone(oldOwner)

            val replacementOwner =
                awaitDifferentSupervisorOwner(oldOwner)
            assertExactSupervisorOwnerLive(
                expected = replacementOwner,
                compilerPid = capturedOldEpoch.pid,
            )
            val replacementInfo = awaitSupervisorControlState(
                compiler = poisonedSession.compiler,
                expectedRegistryConnected = true,
                expectedReady = true,
                expectedPaused = false,
                expectedRequiresExternalSupervision = false,
                expectedCompilerEpoch = capturedOldEpoch,
            )
            assertEquals(
                replacementOwner,
                registeredSupervisorOwner(replacementInfo),
            )
            assertTrue(
                replacementInfo.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CALLBACK_REPLAY_ARMED,
                    false,
                ),
            )
            val replacementGeneration =
                supervisorConnectionGeneration(replacementInfo)
            assertTrue(
                "The replacement supervisor connection did not advance " +
                    "generation: old=$oldGeneration, " +
                    "replacement=$replacementGeneration",
                replacementGeneration > oldGeneration,
            )
            val replacementRegistry = observeSupervisorRegistryBinder()
            assertTrue(replacementRegistry.binder.isBinderAlive)
            assertTrue(poisonedSession.rawBinder.isBinderAlive)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)

            val replayInfo = executeSupervisorRetiredCallbackCommandForTesting(
                compiler = poisonedSession.compiler,
                command =
                    R2CompilerProcessSupervisorControlProtocol
                        .COMMAND_REPLAY_RETIRED_CALLBACKS,
            )
            assertSupervisorControlState(
                info = replayInfo,
                expectedRegistryConnected = true,
                expectedReady = true,
                expectedPaused = false,
                expectedRequiresExternalSupervision = false,
                expectedCompilerEpoch = capturedOldEpoch,
            )
            assertFalse(
                replayInfo.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CALLBACK_REPLAY_ARMED,
                    true,
                ),
            )
            assertTrue(
                replayInfo.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CALLBACKS_REPLAYED,
                    false,
                ),
            )
            assertTrue(
                replayInfo.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CALLBACK_STATE_PRESERVED,
                    false,
                ),
            )
            assertEquals(
                oldGeneration,
                replayInfo.getLong(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CONNECTION_GENERATION,
                    -1L,
                ),
            )
            assertEquals(
                replacementGeneration,
                replayInfo.getLong(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_REPLAY_TARGET_CONNECTION_GENERATION,
                    -1L,
                ),
            )
            assertEquals(
                replacementGeneration,
                supervisorConnectionGeneration(replayInfo),
            )
            val expectedDeliveryCount =
                4 +
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 2 else 0) +
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) 2 else 0)
            assertEquals(
                expectedDeliveryCount,
                replayInfo.getInt(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_RETIRED_CALLBACK_DELIVERY_COUNT,
                    -1,
                ),
            )
            assertEquals(replacementOwner, registeredSupervisorOwner(replayInfo))
            assertTrue(replacementRegistry.binder.isBinderAlive)
            assertTrue(poisonedSession.rawBinder.isBinderAlive)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)

            assertSupervisorRegistrationRemainsStable(
                compiler = poisonedSession.compiler,
                expectedCompilerEpoch = capturedOldEpoch,
                expectedOwner = replacementOwner,
                expectedGeneration = replacementGeneration,
            )
            assertTrue(replacementRegistry.binder.isBinderAlive)
            assertTrue(poisonedSession.rawBinder.isBinderAlive)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)

            val compilerBinderDeath = CountDownLatch(1)
            poisonedSession.rawBinder.linkToDeath(
                { compilerBinderDeath.countDown() },
                0,
            )
            val cancellationOutcome = runCatching {
                poisonedSession.compiler.cancelCompilation(
                    cancellation(poisonedRequestId, jobToken),
                )
            }
            assertFalse(
                "Cleanup-unproven must not publish an ordinary callback.",
                poisonedCallback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, poisonedCallback.terminalCount.get())
            assertTrue(
                "The successor supervisor did not terminate the poisoned " +
                    "compiler after retired callback replay.",
                compilerBinderDeath.await(
                    SUPERVISOR_OWNER_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            assertTrue(
                "Cleanup-unproven cancellation was neither accepted nor " +
                    "overtaken by exact Binder death.",
                cancellationOutcome.getOrNull() == true ||
                    cancellationOutcome.exceptionOrNull() is DeadObjectException,
            )
            assertFalse(poisonedSession.rawBinder.isBinderAlive)
            oldFdEvidence.awaitReleasedAfterLifecycleClose()
            oldFdEvidence.awaitProcessGone()
            val finalObservation =
                R2CompilerProcessProcfs().observe(capturedOldEpoch)
            assertTrue(
                "The poisoned compiler epoch remained present in procfs: " +
                    finalObservation,
                finalObservation == R2CompilerProcessObservation.ABSENT ||
                    finalObservation == R2CompilerProcessObservation.REUSED,
            )
            awaitCompilerProcessQuarantineCleared()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertEquals(0, poisonedCallback.terminalCount.get())
            automaticRecoveryVerified = true
        } finally {
            if (poisonedSession.rawBinder.isBinderAlive) {
                runCatching {
                    executeSupervisorRetiredCallbackCommandForTesting(
                        compiler = poisonedSession.compiler,
                        command =
                            R2CompilerProcessSupervisorControlProtocol
                                .COMMAND_RESET_RETIRED_CALLBACKS,
                    )
                }
            }
            if (!automaticRecoveryVerified) {
                val capturedOldEpoch = oldEpoch
                if (
                    capturedOldEpoch != null &&
                    poisonedSession.rawBinder.isBinderAlive
                ) {
                    runCatching {
                        R2CompilerProcessSupervisor(context)
                            .terminatePoisonedEpoch(
                                compiler = poisonedSession.compiler,
                                rawBinder = poisonedSession.rawBinder,
                                oldIdentity = capturedOldEpoch,
                            )
                    }
                }
                if (poisonedSession.rawBinder.isBinderAlive) {
                    Process.killProcess(oldFdEvidence.identity.pid)
                }
                runCatching { oldFdEvidence.awaitProcessGone() }
                runCatching {
                    R2CompilerProcessSupervisor(context)
                        .reconcilePersistedQuarantine()
                }
                capturedOldEpoch?.let { identity ->
                    R2CompilerProcessQuarantine.clearExactOrAbsent(
                        context = context,
                        expected = identity,
                    )
                }
            }
            try {
                poisonedSession.close()
            } finally {
                poisonedFiles.delete()
                R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
            }
        }

        try {
            assertFreshCompilerEpochAcceptsCancellation(
                oldEpoch = requireNotNull(oldEpoch),
                oldFdIdentity = oldFdEvidence.identity,
                label = "retired-supervisor-callback-recovery",
            )
        } finally {
            R2CompilerProcessQuarantine.clearExactOrAbsent(
                context = context,
                expected = oldEpoch,
            )
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        }
    }

    @Test
    fun persistedQuarantineReconcilesAfterSupervisorRestartAndRecoversHeldBinding() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        val absentOldEpoch = R2CompilerProcessEpochIdentity(
            bootId = requireNotNull(
                R2CompilerProcessProcfs().capture(Process.myPid()),
            ).bootId,
            uid = Process.myUid(),
            pid = Int.MAX_VALUE,
            startTimeTicks = 1L,
            processName = COMPILER_PROCESS_NAME,
            nonce = "c".repeat(
                R2CompilerProcessSupervisorProtocol.PROCESS_NONCE_CHARS,
            ),
        )
        assertTrue(
            R2CompilerProcessQuarantine.markExact(context, absentOldEpoch),
        )
        var quarantinedBinding: QuarantineBinding? = null
        try {
            quarantinedBinding = bindCompilerWhileQuarantined()

            val recovery =
                R2CompilerProcessSupervisor(context).reconcilePersistedQuarantine()
            assertEquals(absentOldEpoch, recovery.oldIdentity)
            assertEquals(
                R2CompilerProcessObservation.ABSENT,
                recovery.finalObservation,
            )
            assertTrue(recovery.oldEpochGone)
            assertTrue(recovery.successorFenceCleared)
            assertFalse(R2CompilerProcessQuarantine.hasMarker(context))

            val recoveredBinder = quarantinedBinding.awaitBinder()
            assertTrue(recoveredBinder.pingBinder())
            val recoveredCompiler =
                ITypeScriptCompilerPlugin.Stub.asInterface(recoveredBinder)
            assertEquals(
                TypeScriptCompilerContract.VERSION,
                recoveredCompiler.contractVersion,
            )
            assertNotEquals(
                absentOldEpoch,
                R2CompilerProcessEpochIdentity.from(
                    enableProcessSupervisorTelemetry(recoveredCompiler),
                ),
            )
        } finally {
            quarantinedBinding?.close()
            R2CompilerProcessQuarantine.clearExactOrAbsent(
                context = context,
                expected = absentOldEpoch,
            )
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        }
    }

    @Test
    fun persistedQuarantineIsAutomaticallyReconciledBySystemJobOwner() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
        val absentOldEpoch = R2CompilerProcessEpochIdentity(
            bootId = requireNotNull(
                R2CompilerProcessProcfs().capture(Process.myPid()),
            ).bootId,
            uid = Process.myUid(),
            pid = Int.MAX_VALUE,
            startTimeTicks = 1L,
            processName = COMPILER_PROCESS_NAME,
            nonce = "d".repeat(
                R2CompilerProcessSupervisorProtocol.PROCESS_NONCE_CHARS,
            ),
        )
        assertTrue(
            R2CompilerProcessQuarantine.markExact(context, absentOldEpoch),
        )
        var quarantinedBinding: QuarantineBinding? = null
        try {
            quarantinedBinding = bindCompilerWhileQuarantined()

            val recoveredBinder = quarantinedBinding.awaitBinder()
            assertTrue(recoveredBinder.pingBinder())
            assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
            val recoveredCompiler =
                ITypeScriptCompilerPlugin.Stub.asInterface(recoveredBinder)
            val recoveredEpoch = R2CompilerProcessEpochIdentity.from(
                enableProcessSupervisorTelemetry(recoveredCompiler),
            )
            assertNotEquals(absentOldEpoch, recoveredEpoch)

            val owner = awaitSupervisorOwnerRecord(
                oldIdentity = absentOldEpoch,
                expectedOutcome =
                    R2CompilerProcessSupervisorOwnerOutcome.RECONCILED,
            )
            assertEquals(
                R2CompilerProcessSupervisorOwnerOutcome.RECONCILED,
                owner.outcome,
            )
            assertEquals(
                "${context.packageName}:typescript_supervisor",
                owner.owner.processName,
            )
            assertNotEquals(recoveredEpoch.pid, owner.ownerPid)
            assertEquals(absentOldEpoch.bootId, owner.oldBootId)
            assertEquals(absentOldEpoch.pid, owner.oldPid)
            assertEquals(
                absentOldEpoch.startTimeTicks,
                owner.oldStartTimeTicks,
            )
            assertEquals(
                R2CompilerProcessObservation.ABSENT,
                owner.observation,
            )
        } finally {
            quarantinedBinding?.close()
            R2CompilerProcessQuarantine.clearExactOrAbsent(
                context = context,
                expected = absentOldEpoch,
            )
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
            R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
        }
    }

    @Test
    fun systemJobOwnerNeverClearsAStillLiveCompilerEpoch() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
        var liveEpoch: R2CompilerProcessEpochIdentity? = null
        try {
            bindCompilerService().use { session ->
                val capturedLiveEpoch = R2CompilerProcessEpochIdentity.from(
                    enableProcessSupervisorTelemetry(session.compiler),
                )
                liveEpoch = capturedLiveEpoch
                assertTrue(
                    R2CompilerProcessQuarantine.markExact(
                        context,
                        capturedLiveEpoch,
                    ),
                )
                assertTrue(
                    R2CompilerProcessSupervisorJobScheduler
                        .scheduleIfQuarantined(context),
                )

                val owner = awaitSupervisorOwnerRecord(
                    oldIdentity = capturedLiveEpoch,
                    expectedOutcome =
                        R2CompilerProcessSupervisorOwnerOutcome.RETRY,
                )
                assertEquals(
                    R2CompilerProcessObservation.SAME,
                    owner.observation,
                )
                assertTrue(R2CompilerProcessQuarantine.hasMarker(context))
                assertTrue(session.rawBinder.isBinderAlive)
                assertEquals(
                    R2CompilerProcessObservation.SAME,
                    R2CompilerProcessProcfs().observe(capturedLiveEpoch),
                )
            }
        } finally {
            liveEpoch?.let { capturedLiveEpoch ->
                R2CompilerProcessQuarantine.clearExactOrAbsent(
                    context = context,
                    expected = capturedLiveEpoch,
                )
            }
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
            R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
        }
    }

    @Test
    fun prearmedSystemJobRecoversPoisonedEpochWithoutSuccessorBinding() {
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        awaitSupervisorJobAbsent()
        R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
        val poisonedSession = bindCompilerService()
        val poisonedFiles = newArchivePair("prearmed-owner-poison")
        val poisonedCallback = RecordingCallback()
        val poisonedRequestId = newRequestId("prearmed-owner-poison")
        val oldFdEvidence = captureCompilerFdEvidence("prearmed-owner-poison")
        val binderDeath = CountDownLatch(1)
        val deathRecipient = IBinder.DeathRecipient {
            binderDeath.countDown()
        }
        var oldEpoch: R2CompilerProcessEpochIdentity? = null
        var deathRecipientLinked = false
        var recoveryVerified = false
        try {
            val ticket = startTransportProbe(
                compiler = poisonedSession.compiler,
                requestId = poisonedRequestId,
                files = poisonedFiles,
                callback = poisonedCallback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
                forceCleanupUnproven = true,
            )
            poisonedCallback.markStartReturned()
            assertAccepted(ticket, poisonedRequestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    acknowledgement(poisonedRequestId, jobToken),
                ),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    cancellation(poisonedRequestId, jobToken),
                ),
            )
            awaitActiveJobCount(poisonedSession.compiler, expected = 1)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertFalse(
                poisonedCallback.terminal.await(
                    NO_CALLBACK_OBSERVATION_MILLIS,
                    TimeUnit.MILLISECONDS,
                ),
            )
            assertEquals(0, poisonedCallback.terminalCount.get())

            val capturedOldEpoch = R2CompilerProcessEpochIdentity.from(
                poisonedSession.compiler.compilerInfo,
            )
            oldEpoch = capturedOldEpoch
            assertEquals(oldFdEvidence.identity.pid, capturedOldEpoch.pid)
            assertEquals(
                oldFdEvidence.identity.startTimeTicks,
                capturedOldEpoch.startTimeTicks,
            )
            poisonedSession.rawBinder.linkToDeath(deathRecipient, 0)
            deathRecipientLinked = true

            val termination = poisonedSession.compiler.prewarm(
                validPrewarmOptions().apply {
                    putString(
                        R2CompilerProcessSupervisorProtocol.KEY_COMMAND,
                        R2CompilerProcessSupervisorProtocol
                            .COMMAND_TERMINATE_POISONED_EPOCH,
                    )
                    putLong(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_COMMAND_DEADLINE_ELAPSED_REALTIME_MS,
                        SystemClock.elapsedRealtime() +
                            PREARMED_TERMINATION_TIMEOUT_MILLIS,
                    )
                    putBoolean(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_REQUIRE_PREARM_RETRY_FOR_TESTING,
                        true,
                    )
                    putLong(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_TERMINATION_DELAY_MILLIS_FOR_TESTING,
                        PREARMED_TERMINATION_DELAY_MILLIS,
                    )
                    capturedOldEpoch.putInto(this)
                },
            )
            assertTrue(
                "The exact production termination path did not accept its " +
                    "pre-armed system Job.",
                termination.getBoolean(
                    R2CompilerProcessSupervisorProtocol
                        .KEY_TERMINATION_ACCEPTED,
                    false,
                ),
            )
            val prearmOwner = requireNotNull(
                R2CompilerProcessSupervisorOwnerTelemetry.read(context),
            )
            assertEquals(
                R2CompilerProcessSupervisorOwnerOutcome.PREARM_RETRY,
                prearmOwner.outcome,
            )
            assertEquals(capturedOldEpoch.bootId, prearmOwner.oldBootId)
            assertEquals(capturedOldEpoch.pid, prearmOwner.oldPid)
            assertEquals(
                capturedOldEpoch.startTimeTicks,
                prearmOwner.oldStartTimeTicks,
            )
            val prearmWorkTokenSha256 =
                requireNotNull(prearmOwner.workTokenSha256)
            val prearmNonceSha256 =
                requireNotNull(prearmOwner.oldNonceSha256)
            assertTrue(
                prearmOwner.observation ==
                    R2CompilerProcessObservation.SAME ||
                    prearmOwner.observation ==
                    R2CompilerProcessObservation.UNREADABLE,
            )
            assertEquals(
                capturedOldEpoch,
                R2CompilerProcessQuarantine.readExactIdentity(context),
            )
            assertTrue(
                R2CompilerProcessSupervisorJobScheduler
                    .hasJobForIdentityForTesting(context, capturedOldEpoch),
            )

            /*
             * Remove the only BIND_AUTO_CREATE client before self-kill. Otherwise AMS could
             * create a successor compiler whose onCreate() also schedules the recovery Job,
             * making the owner attribution ambiguous.
             */
            poisonedSession.close()
            assertTrue(
                "The pre-armed compiler process did not report Binder death.",
                binderDeath.await(
                    PROCESS_DEATH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            oldFdEvidence.awaitProcessGone()
            assertFalse(poisonedSession.rawBinder.isBinderAlive)
            val finalObservation =
                R2CompilerProcessProcfs().observe(capturedOldEpoch)
            assertTrue(
                finalObservation == R2CompilerProcessObservation.ABSENT ||
                    finalObservation == R2CompilerProcessObservation.REUSED,
            )

            val owner = awaitSupervisorOwnerRecord(
                oldIdentity = capturedOldEpoch,
                expectedOutcome =
                    R2CompilerProcessSupervisorOwnerOutcome.RECONCILED,
                requireCompilerAbsent = true,
            )
            assertTrue(
                owner.observation == R2CompilerProcessObservation.ABSENT ||
                    owner.observation == R2CompilerProcessObservation.REUSED,
            )
            assertEquals(
                prearmWorkTokenSha256,
                owner.workTokenSha256,
            )
            assertEquals(prearmNonceSha256, owner.oldNonceSha256)
            awaitCompilerProcessQuarantineCleared()
            assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
            assertEquals(0, poisonedCallback.terminalCount.get())
            assertOutputRemainsEmpty(poisonedFiles.output)
            recoveryVerified = true
        } finally {
            val capturedOldEpoch = oldEpoch
            poisonedSession.close()
            if (
                !recoveryVerified &&
                capturedOldEpoch != null &&
                poisonedSession.rawBinder.isBinderAlive
            ) {
                runCatching {
                    R2CompilerProcessSupervisor(context).terminatePoisonedEpoch(
                        compiler = poisonedSession.compiler,
                        rawBinder = poisonedSession.rawBinder,
                        oldIdentity = capturedOldEpoch,
                    )
                }
            }
            if (
                deathRecipientLinked &&
                poisonedSession.rawBinder.isBinderAlive
            ) {
                runCatching {
                    poisonedSession.rawBinder.unlinkToDeath(
                        deathRecipient,
                        0,
                    )
                }
            }
            val oldEpochGone = capturedOldEpoch == null ||
                R2CompilerProcessProcfs().observe(capturedOldEpoch).let {
                    it == R2CompilerProcessObservation.ABSENT ||
                        it == R2CompilerProcessObservation.REUSED
                }
            if (oldEpochGone) {
                oldEpoch?.let { goneEpoch ->
                    R2CompilerProcessQuarantine.clearExactOrAbsent(
                        context = context,
                        expected = goneEpoch,
                    )
                }
                R2CompilerProcessSupervisorJobScheduler
                    .cancelForTesting(context)
                runCatching { awaitSupervisorJobAbsent() }
            }
            R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
            poisonedFiles.delete()
        }

        assertFreshCompilerEpochAcceptsCancellation(
            oldEpoch = requireNotNull(oldEpoch),
            oldFdIdentity = oldFdEvidence.identity,
            label = "prearmed-owner-recovery",
        )
    }

    @Test
    fun runningSystemJobReplacementHandsOffToNewExactGeneration() {
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        awaitSupervisorJobAbsent()
        R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
        assertTrue(
            "Could not establish the Job lifecycle journal cutoff.",
            R2CompilerProcessSupervisorJobLifecycleJournal
                .clearForTesting(context),
        )
        val poisonedSession = bindCompilerService()
        val poisonedFiles = newArchivePair("running-job-replacement")
        val poisonedCallback = RecordingCallback()
        val poisonedRequestId = newRequestId("running-job-replacement")
        val oldFdEvidence = captureCompilerFdEvidence("running-job-replacement")
        val binderDeath = CountDownLatch(1)
        val deathRecipient = IBinder.DeathRecipient {
            binderDeath.countDown()
        }
        var oldEpoch: R2CompilerProcessEpochIdentity? = null
        var deathRecipientLinked = false
        var recoveryVerified = false
        try {
            val ticket = startTransportProbe(
                compiler = poisonedSession.compiler,
                requestId = poisonedRequestId,
                files = poisonedFiles,
                callback = poisonedCallback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
                forceCleanupUnproven = true,
            )
            poisonedCallback.markStartReturned()
            assertAccepted(ticket, poisonedRequestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    acknowledgement(poisonedRequestId, jobToken),
                ),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    cancellation(poisonedRequestId, jobToken),
                ),
            )
            awaitActiveJobCount(poisonedSession.compiler, expected = 1)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertEquals(0, poisonedCallback.terminalCount.get())

            val capturedOldEpoch = R2CompilerProcessEpochIdentity.from(
                poisonedSession.compiler.compilerInfo,
            )
            oldEpoch = capturedOldEpoch
            assertEquals(oldFdEvidence.identity.pid, capturedOldEpoch.pid)
            assertEquals(
                oldFdEvidence.identity.startTimeTicks,
                capturedOldEpoch.startTimeTicks,
            )
            poisonedSession.rawBinder.linkToDeath(deathRecipient, 0)
            deathRecipientLinked = true

            val retiredLease = requireNotNull(
                R2CompilerProcessSupervisorJobScheduler.prearmExact(
                    context = context,
                    identity = capturedOldEpoch,
                    commandDeadline =
                        SystemClock.elapsedRealtime() +
                            REPLACEMENT_PREARM_TIMEOUT_MILLIS,
                ),
            )
            val retiredTokenSha256 =
                workTokenSha256(retiredLease.work.workToken)
            val retiredOwner = awaitSupervisorOwnerRecord(
                oldIdentity = capturedOldEpoch,
                expectedOutcome =
                    R2CompilerProcessSupervisorOwnerOutcome.PREARM_RETRY,
                expectedWorkTokenSha256 = retiredTokenSha256,
            )
            assertTrue(
                retiredOwner.observation ==
                    R2CompilerProcessObservation.SAME ||
                    retiredOwner.observation ==
                    R2CompilerProcessObservation.UNREADABLE,
            )
            assertTrue(
                R2CompilerProcessSupervisorJobScheduler.hasExactJob(
                    context = context,
                    lease = retiredLease,
                ),
            )
            awaitJobLifecycleRecords(
                description = "running generation A start",
            ) { records ->
                records.any { record ->
                    record.event ==
                        R2CompilerProcessSupervisorJobLifecycleEvent
                            .START_CALLBACK &&
                        record.workTokenSha256 == retiredTokenSha256
                }
            }

            val termination = poisonedSession.compiler.prewarm(
                validPrewarmOptions().apply {
                    putString(
                        R2CompilerProcessSupervisorProtocol.KEY_COMMAND,
                        R2CompilerProcessSupervisorProtocol
                            .COMMAND_TERMINATE_POISONED_EPOCH,
                    )
                    putLong(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_COMMAND_DEADLINE_ELAPSED_REALTIME_MS,
                        SystemClock.elapsedRealtime() +
                            JOB_REPLACEMENT_TERMINATION_TIMEOUT_MILLIS,
                    )
                    putBoolean(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_REQUIRE_PREARM_RETRY_FOR_TESTING,
                        true,
                    )
                    putLong(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_TERMINATION_DELAY_MILLIS_FOR_TESTING,
                        JOB_REPLACEMENT_TERMINATION_DELAY_MILLIS,
                    )
                    capturedOldEpoch.putInto(this)
                },
            )
            assertTrue(
                "The production path did not accept replacement generation B.",
                termination.getBoolean(
                    R2CompilerProcessSupervisorProtocol
                        .KEY_TERMINATION_ACCEPTED,
                    false,
                ),
            )
            val replacementWork = requireNotNull(
                R2CompilerProcessSupervisorJobScheduler
                    .pendingWorkForTesting(context),
            )
            assertEquals(
                R2CompilerProcessSupervisorJobMode.PREARM_EXACT,
                replacementWork.mode,
            )
            assertTrue(
                replacementWork.identity.matchesExact(capturedOldEpoch),
            )
            val replacementTokenSha256 =
                workTokenSha256(replacementWork.workToken)
            assertNotEquals(retiredTokenSha256, replacementTokenSha256)
            assertFalse(
                R2CompilerProcessSupervisorJobScheduler.hasExactJob(
                    context = context,
                    lease = retiredLease,
                ),
            )

            /*
             * The accepted DEBUG command already waited for this exact B-token PREARM_RETRY
             * record before committing the marker. Read it synchronously so no owner/journal
             * polling can consume the bounded pre-kill window while BIND_AUTO_CREATE remains.
             */
            val replacementOwner = requireNotNull(
                R2CompilerProcessSupervisorOwnerTelemetry.read(context),
            )
            assertEquals(
                R2CompilerProcessSupervisorOwnerOutcome.PREARM_RETRY,
                replacementOwner.outcome,
            )
            assertEquals(replacementTokenSha256, replacementOwner.workTokenSha256)
            assertEquals(capturedOldEpoch.bootId, replacementOwner.oldBootId)
            assertEquals(capturedOldEpoch.pid, replacementOwner.oldPid)
            assertEquals(
                capturedOldEpoch.startTimeTicks,
                replacementOwner.oldStartTimeTicks,
            )
            assertTrue(
                replacementOwner.observation ==
                    R2CompilerProcessObservation.SAME ||
                    replacementOwner.observation ==
                    R2CompilerProcessObservation.UNREADABLE,
            )
            assertEquals(
                capturedOldEpoch,
                R2CompilerProcessQuarantine.readExactIdentity(context),
            )
            assertEquals(
                replacementWork,
                R2CompilerProcessSupervisorJobScheduler
                    .pendingWorkForTesting(context),
            )

            /*
             * Drop the only auto-create client before any asynchronous journal wait. Even if an
             * API level delays the retired onStop callback beyond the 5-second DEBUG kill delay,
             * AMS has no client for which it could create an ambiguous successor compiler.
             */
            poisonedSession.close()
            val handoff = awaitJobLifecycleRecords(
                description = "generation A stop and generation B start",
            ) { records ->
                records.any { record ->
                    record.event ==
                        R2CompilerProcessSupervisorJobLifecycleEvent
                            .START_CALLBACK &&
                        record.workTokenSha256 == retiredTokenSha256
                } &&
                    records.any { record ->
                        record.event ==
                            R2CompilerProcessSupervisorJobLifecycleEvent
                                .STOP_CALLBACK &&
                            record.workTokenSha256 == retiredTokenSha256 &&
                            (
                                record.stopDecision ==
                                    R2CompilerProcessSupervisorJobStopDecision
                                        .CURRENT ||
                                    record.stopDecision ==
                                    R2CompilerProcessSupervisorJobStopDecision
                                        .STALE
                                )
                    } &&
                    records.any { record ->
                        record.event ==
                            R2CompilerProcessSupervisorJobLifecycleEvent
                                .START_CALLBACK &&
                            record.workTokenSha256 == replacementTokenSha256
                    }
            }
            val retiredStops = handoff.filter { record ->
                record.event ==
                    R2CompilerProcessSupervisorJobLifecycleEvent
                        .STOP_CALLBACK &&
                    record.workTokenSha256 == retiredTokenSha256
            }
            assertTrue(retiredStops.isNotEmpty())
            retiredStops.forEach { retiredStop ->
                assertTrue(
                    retiredStop.stopDecision ==
                        R2CompilerProcessSupervisorJobStopDecision.CURRENT ||
                        retiredStop.stopDecision ==
                        R2CompilerProcessSupervisorJobStopDecision.STALE,
                )
                if (
                    retiredStop.stopDecision ==
                    R2CompilerProcessSupervisorJobStopDecision.STALE
                ) {
                    assertEquals(
                        replacementTokenSha256,
                        retiredStop.currentWorkTokenSha256,
                    )
                }
            }
            if (
                retiredStops.any { retiredStop ->
                    retiredStop.stopDecision ==
                        R2CompilerProcessSupervisorJobStopDecision.STALE
                }
            ) {
                assertTrue(
                    handoff.any { record ->
                        record.event ==
                            R2CompilerProcessSupervisorJobLifecycleEvent
                                .START_REPLACED_CURRENT &&
                            record.workTokenSha256 ==
                            replacementTokenSha256 &&
                            record.currentWorkTokenSha256 ==
                            retiredTokenSha256
                    },
                )
            }
            assertTrue(
                "The compiler did not report Binder death after Job handoff.",
                binderDeath.await(
                    PROCESS_DEATH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            oldFdEvidence.awaitProcessGone()
            assertFalse(poisonedSession.rawBinder.isBinderAlive)
            val finalObservation =
                R2CompilerProcessProcfs().observe(capturedOldEpoch)
            assertTrue(
                finalObservation == R2CompilerProcessObservation.ABSENT ||
                    finalObservation == R2CompilerProcessObservation.REUSED,
            )

            val owner = awaitSupervisorOwnerRecord(
                oldIdentity = capturedOldEpoch,
                expectedOutcome =
                    R2CompilerProcessSupervisorOwnerOutcome.RECONCILED,
                requireCompilerAbsent = true,
                expectedWorkTokenSha256 = replacementTokenSha256,
            )
            assertTrue(
                owner.observation == R2CompilerProcessObservation.ABSENT ||
                    owner.observation == R2CompilerProcessObservation.REUSED,
            )
            assertEquals(
                replacementOwner.oldNonceSha256,
                owner.oldNonceSha256,
            )
            awaitCompilerProcessQuarantineCleared()
            awaitSupervisorJobAbsent()
            SystemClock.sleep(JOB_REPLACEMENT_STABILITY_MILLIS)
            val stableJournal = requireNotNull(
                R2CompilerProcessSupervisorJobLifecycleJournal
                    .readForTesting(context),
            )
            assertEquals(
                1,
                stableJournal.count { record ->
                    record.event ==
                        R2CompilerProcessSupervisorJobLifecycleEvent
                            .START_CALLBACK &&
                        record.workTokenSha256 == retiredTokenSha256
                },
            )
            assertEquals(
                1,
                stableJournal.count { record ->
                    record.event ==
                        R2CompilerProcessSupervisorJobLifecycleEvent
                            .START_CALLBACK &&
                        record.workTokenSha256 == replacementTokenSha256
                },
            )
            assertEquals(0, poisonedCallback.terminalCount.get())
            assertOutputRemainsEmpty(poisonedFiles.output)
            recoveryVerified = true
        } finally {
            val capturedOldEpoch = oldEpoch
            poisonedSession.close()
            if (
                !recoveryVerified &&
                capturedOldEpoch != null &&
                poisonedSession.rawBinder.isBinderAlive
            ) {
                runCatching {
                    R2CompilerProcessSupervisor(context).terminatePoisonedEpoch(
                        compiler = poisonedSession.compiler,
                        rawBinder = poisonedSession.rawBinder,
                        oldIdentity = capturedOldEpoch,
                    )
                }
            }
            if (
                deathRecipientLinked &&
                poisonedSession.rawBinder.isBinderAlive
            ) {
                runCatching {
                    poisonedSession.rawBinder.unlinkToDeath(
                        deathRecipient,
                        0,
                    )
                }
            }
            val oldEpochGone = capturedOldEpoch == null ||
                R2CompilerProcessProcfs().observe(capturedOldEpoch).let {
                    it == R2CompilerProcessObservation.ABSENT ||
                        it == R2CompilerProcessObservation.REUSED
                }
            if (oldEpochGone) {
                capturedOldEpoch?.let { goneEpoch ->
                    R2CompilerProcessQuarantine.clearExactOrAbsent(
                        context = context,
                        expected = goneEpoch,
                    )
                }
                R2CompilerProcessSupervisorJobScheduler
                    .cancelForTesting(context)
                runCatching { awaitSupervisorJobAbsent() }
            }
            R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
            R2CompilerProcessSupervisorJobLifecycleJournal
                .clearForTesting(context)
            poisonedFiles.delete()
        }

        assertFreshCompilerEpochAcceptsCancellation(
            oldEpoch = requireNotNull(oldEpoch),
            oldFdIdentity = oldFdEvidence.identity,
            label = "running-job-replacement-recovery",
        )
    }

    @Test
    fun runningSystemJobIsRedeliveredAfterSupervisorProcessDeath() {
        runRetainedExactWorkOwnerDeathScenario(
            faultMode =
                RetainedExactWorkFaultMode.OWNER_THEN_COMPILER_AFTER_DEATH_PROOF,
            scenarioLabel = "running-job-owner-death",
            recoveryLabel = "running-job-owner-death-recovery",
            evidenceLogTag = S6H_EVIDENCE_LOG_TAG,
        )
    }

    @Test
    fun runningSystemJobIsRedeliveredAfterOverlappedSupervisorAndCompilerDeath() {
        runRetainedExactWorkOwnerDeathScenario(
            faultMode =
                RetainedExactWorkFaultMode.OVERLAPPED_OWNER_AND_COMPILER_DEATH,
            scenarioLabel = "running-job-overlapped-owner-compiler-death",
            recoveryLabel =
                "running-job-overlapped-owner-compiler-death-recovery",
            evidenceLogTag = S6I_EVIDENCE_LOG_TAG,
        )
    }

    private fun runRetainedExactWorkOwnerDeathScenario(
        faultMode: RetainedExactWorkFaultMode,
        scenarioLabel: String,
        recoveryLabel: String,
        evidenceLogTag: String,
    ) {
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        awaitSupervisorJobAbsent()
        R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
        assertTrue(
            "Could not establish the Job lifecycle journal cutoff.",
            R2CompilerProcessSupervisorJobLifecycleJournal
                .clearForTesting(context),
        )
        val poisonedSession = bindCompilerService()
        val poisonedFiles = newArchivePair(scenarioLabel)
        val poisonedCallback = RecordingCallback()
        val poisonedRequestId = newRequestId(scenarioLabel)
        val oldFdEvidence =
            captureCompilerFdEvidence(scenarioLabel)
        val binderDeath = CountDownLatch(1)
        val deathRecipient = IBinder.DeathRecipient {
            binderDeath.countDown()
        }
        var oldEpoch: R2CompilerProcessEpochIdentity? = null
        var jobLease: R2CompilerProcessSupervisorJobLease? = null
        var deathRecipientLinked = false
        var recoveryVerified = false
        var completedEvidence: String? = null
        try {
            val ticket = startTransportProbe(
                compiler = poisonedSession.compiler,
                requestId = poisonedRequestId,
                files = poisonedFiles,
                callback = poisonedCallback,
                mode = CompilerTransportProbeProtocol.MODE_HOLD,
                timeoutMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
                forceCleanupUnproven = true,
            )
            poisonedCallback.markStartReturned()
            assertAccepted(ticket, poisonedRequestId)
            val jobToken = requireNotNull(
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    acknowledgement(poisonedRequestId, jobToken),
                ),
            )
            assertTrue(
                poisonedSession.compiler.cancelCompilation(
                    cancellation(poisonedRequestId, jobToken),
                ),
            )
            awaitActiveJobCount(poisonedSession.compiler, expected = 1)
            oldFdEvidence.assertActive()
            assertOutputRemainsEmpty(poisonedFiles.output)
            assertEquals(0, poisonedCallback.terminalCount.get())

            val capturedOldEpoch = R2CompilerProcessEpochIdentity.from(
                poisonedSession.compiler.compilerInfo,
            )
            oldEpoch = capturedOldEpoch
            assertEquals(oldFdEvidence.identity.pid, capturedOldEpoch.pid)
            assertEquals(
                oldFdEvidence.identity.startTimeTicks,
                capturedOldEpoch.startTimeTicks,
            )
            poisonedSession.rawBinder.linkToDeath(deathRecipient, 0)
            deathRecipientLinked = true

            val commandDeadline =
                SystemClock.elapsedRealtime() +
                    JOB_OWNER_DEATH_PREARM_TIMEOUT_MILLIS
            val lease = requireNotNull(
                R2CompilerProcessSupervisorJobScheduler.prearmExact(
                    context = context,
                    identity = capturedOldEpoch,
                    commandDeadline = commandDeadline,
                ),
            )
            jobLease = lease
            val workTokenSha256 =
                workTokenSha256(lease.work.workToken)
            val prearmOwner = awaitSupervisorOwnerRecord(
                oldIdentity = capturedOldEpoch,
                expectedOutcome =
                    R2CompilerProcessSupervisorOwnerOutcome.PREARM_RETRY,
                expectedWorkTokenSha256 = workTokenSha256,
            )
            assertTrue(
                prearmOwner.observation ==
                    R2CompilerProcessObservation.SAME ||
                    prearmOwner.observation ==
                    R2CompilerProcessObservation.UNREADABLE,
            )
            val firstOwner = supervisorOwnerIdentity(prearmOwner)
            assertExactSupervisorOwnerLive(
                expected = firstOwner,
                compilerPid = capturedOldEpoch.pid,
            )
            awaitJobLifecycleRecords(
                description =
                    "generation W start in supervisor owner P1",
            ) { records ->
                records.any { record ->
                    record.event ==
                        R2CompilerProcessSupervisorJobLifecycleEvent
                            .START_CALLBACK &&
                        record.workTokenSha256 == workTokenSha256 &&
                        supervisorOwnerIdentity(record) == firstOwner
                }
            }
            assertTrue(
                R2CompilerProcessSupervisorJobScheduler.hasExactJob(
                    context = context,
                    lease = lease,
                ),
            )
            assertEquals(
                lease.work,
                R2CompilerProcessSupervisorJobScheduler
                    .pendingWorkForTesting(context),
            )

            /*
             * Commit the marker while W/P1 is still running. The marker identifies exact
             * compiler epoch C. Independently, the fixed-ID pending Job remains exact generation
             * W, whose extras carry C and W's token; the marker does not store or bind that token.
             */
            assertTrue(
                R2CompilerProcessQuarantine.markExactBeforeDeadline(
                    context = context,
                    identity = capturedOldEpoch,
                    commandDeadline = commandDeadline,
                ),
            )
            assertEquals(
                capturedOldEpoch,
                R2CompilerProcessQuarantine.readExactIdentity(context),
            )
            assertEquals(
                lease.work,
                R2CompilerProcessSupervisorJobScheduler
                    .pendingWorkForTesting(context),
            )
            assertTrue(
                R2CompilerProcessSupervisorJobScheduler.hasExactJob(
                    context = context,
                    lease = lease,
                ),
            )

            var ownerDeathProofNanos: Long? = null
            var overlappedKillEvidence: OverlappedProcessKillEvidence? = null
            val ownerKillInitiationNanos: Long
            val compilerKillInitiationNanos: Long
            val redeliveryBoundaryNanos: Long
            when (faultMode) {
                RetainedExactWorkFaultMode
                    .OWNER_THEN_COMPILER_AFTER_DEATH_PROOF -> {
                    ownerKillInitiationNanos =
                        SystemClock.elapsedRealtimeNanos()
                    assertTrue(ownerKillInitiationNanos > 0L)
                    killExactSupervisorOwner(
                        expected = firstOwner,
                        compilerPid = capturedOldEpoch.pid,
                    )
                    awaitSupervisorOwnerGone(firstOwner)
                    ownerDeathProofNanos =
                        SystemClock.elapsedRealtimeNanos()
                    assertEquals(
                        capturedOldEpoch,
                        R2CompilerProcessQuarantine
                            .readExactIdentity(context),
                    )

                    /*
                     * Drop the sole external compiler binding, then fail closed on procfs
                     * ambiguity before signalling the exact old compiler epoch. The
                     * compiler-side control binding may create P2 first; the claim below is only
                     * that JobScheduler redelivers W to P2.
                     */
                    poisonedSession.close()
                    compilerKillInitiationNanos =
                        SystemClock.elapsedRealtimeNanos()
                    killExactCompilerEpochIfStillLive(capturedOldEpoch)
                    redeliveryBoundaryNanos =
                        ownerKillInitiationNanos
                }
                RetainedExactWorkFaultMode
                    .OVERLAPPED_OWNER_AND_COMPILER_DEATH -> {
                    /*
                     * Remove the only external auto-create binding before firing either fault.
                     * Revalidate every durable/live input, then release two kill workers from one
                     * latch. Neither worker waits for the other process to disappear.
                     */
                    poisonedSession.close()
                    assertTrue(poisonedSession.rawBinder.isBinderAlive)
                    oldFdEvidence.assertActive()
                    assertExactSupervisorOwnerLive(
                        expected = firstOwner,
                        compilerPid = capturedOldEpoch.pid,
                    )
                    assertEquals(
                        R2CompilerProcessObservation.SAME,
                        R2CompilerProcessProcfs().observe(capturedOldEpoch),
                    )
                    assertEquals(
                        capturedOldEpoch,
                        R2CompilerProcessQuarantine
                            .readExactIdentity(context),
                    )
                    assertEquals(
                        lease.work,
                        R2CompilerProcessSupervisorJobScheduler
                            .pendingWorkForTesting(context),
                    )
                    val evidence = issueOverlappedExactProcessKills(
                        expectedOwner = firstOwner,
                        expectedCompiler = capturedOldEpoch,
                    )
                    overlappedKillEvidence = evidence
                    ownerKillInitiationNanos =
                        evidence.ownerKillInitiationNanos
                    compilerKillInitiationNanos =
                        evidence.compilerKillInitiationNanos
                    redeliveryBoundaryNanos =
                        evidence.bothSignalsReturnedNanos
                    awaitSupervisorOwnerGone(firstOwner)
                    ownerDeathProofNanos =
                        SystemClock.elapsedRealtimeNanos()
                }
            }
            assertTrue(
                "The old compiler did not report Binder death after its " +
                    "JobService owner was killed.",
                binderDeath.await(
                    PROCESS_DEATH_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            oldFdEvidence.awaitProcessGone()
            val compilerDeathProofNanos =
                SystemClock.elapsedRealtimeNanos()
            assertFalse(poisonedSession.rawBinder.isBinderAlive)
            val finalCompilerObservation =
                R2CompilerProcessProcfs().observe(capturedOldEpoch)
            assertTrue(
                finalCompilerObservation ==
                    R2CompilerProcessObservation.ABSENT ||
                    finalCompilerObservation ==
                    R2CompilerProcessObservation.REUSED,
            )

            fun isPostKillRedelivery(
                record: R2CompilerProcessSupervisorJobLifecycleRecord,
            ): Boolean =
                record.event ==
                    R2CompilerProcessSupervisorJobLifecycleEvent
                        .START_CALLBACK &&
                    record.workTokenSha256 == workTokenSha256 &&
                    record.elapsedRealtimeNanos >
                    redeliveryBoundaryNanos &&
                    supervisorOwnerIdentity(record) != firstOwner

            var forcedDispatchAttempt: ForcedJobDispatchAttempt? = null
            var redeliveryRecords = observeJobLifecycleRecords(
                timeoutSeconds =
                    JOB_OWNER_DEATH_NATURAL_REDELIVERY_TIMEOUT_SECONDS,
            ) { records ->
                records.any(::isPostKillRedelivery)
            }
            if (redeliveryRecords == null) {
                val racedRecords =
                    R2CompilerProcessSupervisorJobLifecycleJournal
                        .readForTesting(context)
                redeliveryRecords = racedRecords?.takeIf { records ->
                    records.any(::isPostKillRedelivery)
                }
            }
            if (redeliveryRecords == null) {
                /*
                 * A failure retry has no latest-runtime guarantee. In particular, API 35 OEM
                 * policy can leave the exact rescheduled Job pending well beyond its 10-second
                 * backoff. Revalidate the durable W/marker pair, then request forced eligibility
                 * for that fixed-ID Job. The later W callback is matched independently; this
                 * test seam does not schedule or replace W.
                 */
                val markerStillExact =
                    R2CompilerProcessQuarantine
                        .readExactIdentity(context) == capturedOldEpoch
                val workStillExact =
                    R2CompilerProcessSupervisorJobScheduler
                        .pendingWorkForTesting(context) == lease.work
                if (markerStillExact && workStillExact) {
                    val dispatch =
                        requestForcedEligibilityForSupervisorJob(lease.work)
                    forcedDispatchAttempt = dispatch
                    val forced = dispatch.output.trim() ==
                        FORCED_JOB_DISPATCH_SUCCESS
                    redeliveryRecords = observeJobLifecycleRecords(
                        timeoutSeconds = if (forced) {
                            JOB_OWNER_DEATH_REDELIVERY_TIMEOUT_SECONDS
                        } else {
                            JOB_OWNER_DEATH_FORCE_RACE_TIMEOUT_SECONDS
                        },
                    ) { records ->
                        records.any(::isPostKillRedelivery)
                    }
                    if (redeliveryRecords == null) {
                        throw AssertionError(
                            "No retained exact-work callback was observed after " +
                                "the eligibility attempt; attempt=$dispatch, marker=" +
                                R2CompilerProcessQuarantine
                                    .inspectForJob(
                                        context = context,
                                        prearmDeadline = null,
                                    ) +
                                ", pendingWork=" +
                                R2CompilerProcessSupervisorJobScheduler
                                    .pendingWorkForTesting(context) +
                                ", records=" +
                                R2CompilerProcessSupervisorJobLifecycleJournal
                                    .readForTesting(context) +
                                ".",
                        )
                    }
                } else {
                    /*
                     * Natural reconcile may have consumed both durable objects just before the
                     * fallback check. Accept only its positive post-kill callback evidence.
                     */
                    redeliveryRecords = awaitJobLifecycleRecords(
                        description =
                            "natural generation W redelivery racing the " +
                                "forced-eligibility precondition check",
                        timeoutSeconds =
                            JOB_OWNER_DEATH_FORCE_RACE_TIMEOUT_SECONDS,
                    ) { records ->
                        records.any(::isPostKillRedelivery)
                    }
                }
            }
            val observedRedeliveryRecords = requireNotNull(redeliveryRecords)
            assertTrue(
                observedRedeliveryRecords.any(::isPostKillRedelivery),
            )

            val reconciledOwner = awaitSupervisorOwnerRecord(
                oldIdentity = capturedOldEpoch,
                expectedOutcome =
                    R2CompilerProcessSupervisorOwnerOutcome.RECONCILED,
                requireCompilerAbsent = true,
                expectedWorkTokenSha256 = workTokenSha256,
                timeoutSeconds = JOB_OWNER_DEATH_REDELIVERY_TIMEOUT_SECONDS,
            )
            assertTrue(
                reconciledOwner.observation ==
                    R2CompilerProcessObservation.ABSENT ||
                    reconciledOwner.observation ==
                    R2CompilerProcessObservation.REUSED,
            )
            val secondOwner = supervisorOwnerIdentity(reconciledOwner)
            assertNotEquals(firstOwner, secondOwner)
            assertEquals(firstOwner.bootId, secondOwner.bootId)
            assertEquals(firstOwner.uid, secondOwner.uid)
            assertEquals(firstOwner.processName, secondOwner.processName)
            val finalLifecycleRecords = awaitJobLifecycleRecords(
                description =
                    "post-kill generation W start in final reconciler " +
                        secondOwner,
                timeoutSeconds =
                    JOB_OWNER_DEATH_REDELIVERY_TIMEOUT_SECONDS,
            ) { records ->
                records.any { record ->
                    isPostKillRedelivery(record) &&
                        supervisorOwnerIdentity(record) == secondOwner
                }
            }
            val classifiedStart = (
                observedRedeliveryRecords + finalLifecycleRecords
                ).asSequence()
                .filter(::isPostKillRedelivery)
                .minByOrNull { record -> record.elapsedRealtimeNanos }
            requireNotNull(classifiedStart)
            val forceBoundaryNanos =
                forcedDispatchAttempt?.commandBoundaryNanos
            val dispatchPath = when {
                forcedDispatchAttempt == null ->
                    JobOwnerDeathDispatchPath.NATURAL
                forcedDispatchAttempt.output.trim() !=
                    FORCED_JOB_DISPATCH_SUCCESS ||
                    forceBoundaryNanos == null ||
                    classifiedStart.elapsedRealtimeNanos <=
                    forceBoundaryNanos ->
                    JobOwnerDeathDispatchPath.NATURAL_FORCE_RACE
                else ->
                    JobOwnerDeathDispatchPath
                        .POST_FORCE_INVOCATION_BOUNDARY
            }
            assertEquals(
                prearmOwner.oldNonceSha256,
                reconciledOwner.oldNonceSha256,
            )
            awaitCompilerProcessQuarantineCleared()
            awaitSupervisorJobAbsent()
            assertEquals(0, poisonedCallback.terminalCount.get())
            assertOutputRemainsEmpty(poisonedFiles.output)
            completedEvidence =
                "dispatchPath=$dispatchPath, " +
                "forceOutput=" +
                (forcedDispatchAttempt?.output?.trim() ?: "<not-invoked>") +
                ", forceBoundaryNanos=" +
                (forceBoundaryNanos ?: "<none>") +
                ", classifiedStartNanos=" +
                classifiedStart.elapsedRealtimeNanos +
                ", classifiedStartOwner=" +
                supervisorOwnerIdentity(classifiedStart) +
                ", ownerKillInitiationNanos=$ownerKillInitiationNanos" +
                ", compilerKillInitiationNanos=$compilerKillInitiationNanos" +
                ", redeliveryBoundaryNanos=$redeliveryBoundaryNanos" +
                ", ownerDeathProofNanos=" +
                requireNotNull(ownerDeathProofNanos) +
                ", compilerDeathProofNanos=$compilerDeathProofNanos" +
                ", finalCompilerObservation=$finalCompilerObservation" +
                ", overlappedKillEvidence=" +
                (overlappedKillEvidence ?: "<not-overlapped>") +
                ", workTokenSha256=$workTokenSha256, " +
                "oldNonceSha256=${prearmOwner.oldNonceSha256}, " +
                "oldCompiler=$capturedOldEpoch, " +
                "firstOwner=$firstOwner, finalOwner=$secondOwner"
            recoveryVerified = true
        } finally {
            val capturedOldEpoch = oldEpoch
            val capturedJobLease = jobLease
            poisonedSession.close()
            if (
                !recoveryVerified &&
                capturedOldEpoch != null
            ) {
                if (poisonedSession.rawBinder.isBinderAlive) {
                    runCatching {
                        killExactCompilerEpochIfStillLive(capturedOldEpoch)
                    }
                }
                runCatching { oldFdEvidence.awaitProcessGone() }
            }
            if (
                deathRecipientLinked &&
                poisonedSession.rawBinder.isBinderAlive
            ) {
                runCatching {
                    poisonedSession.rawBinder.unlinkToDeath(
                        deathRecipient,
                        0,
                    )
                }
            }
            val oldEpochGone = capturedOldEpoch == null ||
                R2CompilerProcessProcfs().observe(capturedOldEpoch).let {
                    it == R2CompilerProcessObservation.ABSENT ||
                        it == R2CompilerProcessObservation.REUSED
                }
            if (
                capturedOldEpoch != null &&
                capturedJobLease != null &&
                oldEpochGone &&
                R2CompilerProcessQuarantine.clearExactOrAbsent(
                    context = context,
                    expected = capturedOldEpoch,
                )
            ) {
                R2CompilerProcessSupervisorJobScheduler
                    .retirePrearmBestEffort(
                        context = context,
                        lease = capturedJobLease,
                    )
            }
            R2CompilerProcessSupervisorOwnerTelemetry.clearForTesting(context)
            R2CompilerProcessSupervisorJobLifecycleJournal
                .clearForTesting(context)
            poisonedFiles.delete()
        }

        assertFreshCompilerEpochAcceptsCancellation(
            oldEpoch = requireNotNull(oldEpoch),
            oldFdIdentity = oldFdEvidence.identity,
            label = recoveryLabel,
        )
        Log.i(
            evidenceLogTag,
            "faultMode=$faultMode, " +
                requireNotNull(completedEvidence) +
                ", freshCompilerRecovery=VERIFIED",
        )
    }

    @Test
    fun staleExactClearCannotRemoveCurrentMarkerGeneration() {
        R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        assertFalse(R2CompilerProcessQuarantine.hasMarker(context))
        var currentEpoch: R2CompilerProcessEpochIdentity? = null
        try {
            bindCompilerService().use { session ->
                val capturedCurrentEpoch = R2CompilerProcessEpochIdentity.from(
                    enableProcessSupervisorTelemetry(session.compiler),
                )
                currentEpoch = capturedCurrentEpoch
                val staleEpoch = capturedCurrentEpoch.copy(
                    nonce = capturedCurrentEpoch.nonce.replaceFirst(
                        capturedCurrentEpoch.nonce.first(),
                        if (capturedCurrentEpoch.nonce.first() == 'a') 'b' else 'a',
                    ),
                )
                assertTrue(
                    R2CompilerProcessQuarantine.markExact(
                        context,
                        capturedCurrentEpoch,
                    ),
                )

                assertFalse(
                    R2CompilerProcessQuarantine.clearExact(
                        context,
                        staleEpoch,
                    ),
                )
                assertEquals(
                    capturedCurrentEpoch,
                    R2CompilerProcessQuarantine.readExactIdentity(context),
                )
                assertEquals(
                    R2CompilerProcessObservation.SAME,
                    R2CompilerProcessProcfs().observe(capturedCurrentEpoch),
                )
            }
        } finally {
            currentEpoch?.let { capturedCurrentEpoch ->
                R2CompilerProcessQuarantine.clearExactOrAbsent(
                    context = context,
                    expected = capturedCurrentEpoch,
                )
            }
            R2CompilerProcessSupervisorJobScheduler.cancelForTesting(context)
        }
    }

    private fun startTransportProbe(
        compiler: ITypeScriptCompilerPlugin,
        requestId: String,
        files: ArchivePair,
        callback: RecordingCallback,
        mode: String,
        timeoutMs: Long,
        completionDelayMs: Long =
            CompilerTransportProbeProtocol.DEFAULT_COMPLETION_DELAY_MS,
        terminalCleanupDelayMs: Long = 0L,
        forceCleanupUnproven: Boolean = false,
    ): Bundle {
        val input = ParcelFileDescriptor.open(
            files.input,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val output = ParcelFileDescriptor.open(
            files.output,
            ParcelFileDescriptor.MODE_READ_WRITE,
        )
        return try {
            compiler.startCompilation(
                validRequest(
                    requestId = requestId,
                    mode = mode,
                    timeoutMs = timeoutMs,
                    completionDelayMs = completionDelayMs,
                    terminalCleanupDelayMs = terminalCleanupDelayMs,
                    forceCleanupUnproven = forceCleanupUnproven,
                ),
                input,
                output,
                callback,
            )
        } finally {
            input.close()
            output.close()
        }
    }

    private fun validRequest(
        requestId: String,
        mode: String,
        timeoutMs: Long,
        completionDelayMs: Long,
        terminalCleanupDelayMs: Long,
        forceCleanupUnproven: Boolean,
    ) = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putInt(
            TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_PROJECT_ID, "r1-transport-probe")
        putString(
            TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
            "autojs://source/r1-transport-probe/main.ts",
        )
        putString(
            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            TypeScriptCompilerContract.PROFILE_RHINO,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILATION_MODE,
            TypeScriptCompilerContract.MODE_DEVELOPMENT,
        )
        putString(TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON, "{}")
        putLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, timeoutMs)
        putBoolean(CompilerTransportProbeProtocol.KEY_ENABLED, true)
        putString(CompilerTransportProbeProtocol.KEY_MODE, mode)
        putLong(
            CompilerTransportProbeProtocol.KEY_COMPLETION_DELAY_MS,
            completionDelayMs,
        )
        putLong(
            CompilerTransportProbeProtocol.KEY_TERMINAL_CLEANUP_DELAY_MS,
            terminalCleanupDelayMs,
        )
        putBoolean(
            CompilerTransportProbeProtocol.KEY_FORCE_CLEANUP_UNPROVEN,
            forceCleanupUnproven,
        )
    }

    private fun validPrewarmOptions() = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putLong(
            TypeScriptCompilerContract.KEY_TIMEOUT_MS,
            TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        )
    }

    private fun enableProcessSupervisorTelemetry(
        compiler: ITypeScriptCompilerPlugin,
    ): Bundle = compiler.prewarm(
        validPrewarmOptions().apply {
            putBoolean(
                R2CompilerProcessSupervisorProtocol.KEY_TELEMETRY_ENABLED,
                true,
            )
        },
    )

    private fun enableAutomaticProcessSupervisorForTesting(
        compiler: ITypeScriptCompilerPlugin,
    ): Bundle {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(SUPERVISOR_OWNER_TIMEOUT_SECONDS)
        var lastResult = Bundle()
        do {
            lastResult = compiler.prewarm(
                validPrewarmOptions().apply {
                    putBoolean(
                        R2CompilerProcessSupervisorProtocol
                            .KEY_TELEMETRY_ENABLED,
                        true,
                    )
                    putBoolean(
                        R2CompilerProcessSupervisorControlProtocol
                            .KEY_ENABLE_AUTOMATIC_TERMINATION_FOR_TESTING,
                        true,
                    )
                },
            )
            if (
                lastResult.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_REGISTRATION_READY,
                    false,
                ) &&
                lastResult.getBoolean(
                    R2CompilerProcessSupervisorControlProtocol
                        .KEY_AUTOMATIC_TERMINATION_ENABLED,
                    false,
                )
            ) {
                return lastResult
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Compiler process supervisor registration did not become ready " +
                "with automatic termination enabled: $lastResult",
        )
    }

    private fun setSupervisorReconnectPausedForTesting(
        compiler: ITypeScriptCompilerPlugin,
        paused: Boolean,
    ): Bundle = compiler.prewarm(
        validPrewarmOptions().apply {
            putBoolean(
                R2CompilerProcessSupervisorProtocol.KEY_TELEMETRY_ENABLED,
                true,
            )
            putBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_SET_RECONNECT_PAUSED_FOR_TESTING,
                paused,
            )
        },
    )

    private fun executeSupervisorRetiredCallbackCommandForTesting(
        compiler: ITypeScriptCompilerPlugin,
        command: String,
    ): Bundle = compiler.prewarm(
        validPrewarmOptions().apply {
            putBoolean(
                R2CompilerProcessSupervisorProtocol.KEY_TELEMETRY_ENABLED,
                true,
            )
            putString(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_RETIRED_CALLBACK_COMMAND_FOR_TESTING,
                command,
            )
        },
    )

    private fun supervisorConnectionGeneration(info: Bundle): Long {
        assertTrue(
            "Missing production supervisor connection generation: $info",
            info.containsKey(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_CONNECTION_GENERATION,
            ),
        )
        return info.getLong(
            R2CompilerProcessSupervisorControlProtocol
                .KEY_CONNECTION_GENERATION,
            -1L,
        ).also { generation ->
            assertTrue(
                "Invalid production supervisor connection generation: $info",
                generation > 0L,
            )
        }
    }

    private fun assertSupervisorRegistrationRemainsStable(
        compiler: ITypeScriptCompilerPlugin,
        expectedCompilerEpoch: R2CompilerProcessEpochIdentity,
        expectedOwner: SupervisorOwnerIdentity,
        expectedGeneration: Long,
    ) {
        val deadline = SystemClock.uptimeMillis() +
            RETIRED_CALLBACK_STABILITY_OBSERVATION_MILLIS
        var samples = 0
        do {
            val info = compiler.compilerInfo
            assertSupervisorControlState(
                info = info,
                expectedRegistryConnected = true,
                expectedReady = true,
                expectedPaused = false,
                expectedRequiresExternalSupervision = false,
                expectedCompilerEpoch = expectedCompilerEpoch,
            )
            assertEquals(expectedOwner, registeredSupervisorOwner(info))
            assertEquals(
                expectedGeneration,
                supervisorConnectionGeneration(info),
            )
            samples++
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        assertTrue(
            "Retired callback stability observation took too few samples.",
            samples >= SUPERVISOR_STABLE_SAMPLE_COUNT,
        )
    }

    private fun awaitSupervisorControlState(
        compiler: ITypeScriptCompilerPlugin,
        expectedRegistryConnected: Boolean,
        expectedReady: Boolean,
        expectedPaused: Boolean,
        expectedRequiresExternalSupervision: Boolean,
        expectedCompilerEpoch: R2CompilerProcessEpochIdentity,
    ): Bundle {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(SUPERVISOR_OWNER_TIMEOUT_SECONDS)
        var lastInfo = Bundle()
        do {
            lastInfo = compiler.compilerInfo
            if (
                supervisorControlStateMatches(
                    info = lastInfo,
                    expectedRegistryConnected = expectedRegistryConnected,
                    expectedReady = expectedReady,
                    expectedPaused = expectedPaused,
                    expectedRequiresExternalSupervision =
                        expectedRequiresExternalSupervision,
                    expectedCompilerEpoch = expectedCompilerEpoch,
                )
            ) {
                return lastInfo
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Compiler-side production supervisor control did not reach " +
                "connected=$expectedRegistryConnected, ready=$expectedReady, " +
                "paused=$expectedPaused, poisoned=" +
                "$expectedRequiresExternalSupervision: $lastInfo",
        )
    }

    private fun assertSupervisorControlState(
        info: Bundle,
        expectedRegistryConnected: Boolean,
        expectedReady: Boolean,
        expectedPaused: Boolean,
        expectedRequiresExternalSupervision: Boolean,
        expectedCompilerEpoch: R2CompilerProcessEpochIdentity,
    ) {
        assertTrue(
            "Missing production supervisor registry telemetry: $info",
            info.containsKey(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTRY_CONNECTED,
            ),
        )
        assertEquals(
            expectedRegistryConnected,
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTRY_CONNECTED,
                !expectedRegistryConnected,
            ),
        )
        assertEquals(
            expectedReady,
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTRATION_READY,
                !expectedReady,
            ),
        )
        assertEquals(
            expectedPaused,
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_RECONNECT_PAUSED,
                !expectedPaused,
            ),
        )
        assertTrue(
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_AUTOMATIC_TERMINATION_ENABLED,
                false,
            ),
        )
        assertEquals(
            expectedRequiresExternalSupervision,
            info.getBoolean(
                R2CompilerProcessSupervisorProtocol
                    .KEY_REQUIRES_EXTERNAL_SUPERVISION,
                !expectedRequiresExternalSupervision,
            ),
        )
        assertEquals(
            expectedCompilerEpoch,
            R2CompilerProcessEpochIdentity.from(info),
        )
    }

    private fun supervisorControlStateMatches(
        info: Bundle,
        expectedRegistryConnected: Boolean,
        expectedReady: Boolean,
        expectedPaused: Boolean,
        expectedRequiresExternalSupervision: Boolean,
        expectedCompilerEpoch: R2CompilerProcessEpochIdentity,
    ): Boolean =
        info.containsKey(
            R2CompilerProcessSupervisorControlProtocol.KEY_REGISTRY_CONNECTED,
        ) &&
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTRY_CONNECTED,
                !expectedRegistryConnected,
            ) == expectedRegistryConnected &&
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTRATION_READY,
                !expectedReady,
            ) == expectedReady &&
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_RECONNECT_PAUSED,
                !expectedPaused,
            ) == expectedPaused &&
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_AUTOMATIC_TERMINATION_ENABLED,
                false,
            ) &&
            info.getBoolean(
                R2CompilerProcessSupervisorProtocol
                    .KEY_REQUIRES_EXTERNAL_SUPERVISION,
                !expectedRequiresExternalSupervision,
            ) == expectedRequiresExternalSupervision &&
            runCatching {
                R2CompilerProcessEpochIdentity.from(info)
            }.getOrNull() == expectedCompilerEpoch

    private fun registeredSupervisorOwner(
        info: Bundle,
    ): SupervisorOwnerIdentity {
        assertTrue(
            "Supervisor registration is not ready: $info",
            info.getBoolean(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTRATION_READY,
                false,
            ),
        )
        assertTrue(
            "Missing registered supervisor PID: $info",
            info.containsKey(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTERED_SUPERVISOR_PID,
            ),
        )
        assertTrue(
            "Missing registered supervisor start time: $info",
            info.containsKey(
                R2CompilerProcessSupervisorControlProtocol
                    .KEY_REGISTERED_SUPERVISOR_START_TIME_TICKS,
            ),
        )
        val pid = info.getInt(
            R2CompilerProcessSupervisorControlProtocol
                .KEY_REGISTERED_SUPERVISOR_PID,
            -1,
        )
        val expectedStartTimeTicks = info.getLong(
            R2CompilerProcessSupervisorControlProtocol
                .KEY_REGISTERED_SUPERVISOR_START_TIME_TICKS,
            -1L,
        )
        val captured = requireNotNull(captureSupervisorOwner(pid)) {
            "Registered supervisor PID $pid was not readable in procfs."
        }
        assertEquals(expectedStartTimeTicks, captured.startTimeTicks)
        assertEquals(Process.myUid(), captured.uid)
        assertEquals(SUPERVISOR_PROCESS_NAME, captured.processName)
        return captured
    }

    private fun observeSupervisorRegistryBinder(): SupervisorRegistryObservation {
        val connection = AwaitingServiceConnection()
        val bound = context.bindService(
            Intent(
                context,
                R2CompilerProcessSupervisorService::class.java,
            ),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue(
            "Failed to bind the production supervisor registry observer.",
            bound,
        )
        return try {
            val binder = connection.awaitBinder()
            assertEquals(
                IR2CompilerProcessSupervisorRegistry.DESCRIPTOR,
                binder.interfaceDescriptor,
            )
            assertEquals(
                null,
                binder.queryLocalInterface(
                    IR2CompilerProcessSupervisorRegistry.DESCRIPTOR,
                ),
            )
            val death = CountDownLatch(1)
            binder.linkToDeath({ death.countDown() }, 0)
            SupervisorRegistryObservation(
                binder = binder,
                death = death,
            )
        } finally {
            context.unbindService(connection)
        }
    }

    private fun captureSupervisorOwner(
        pid: Int,
    ): SupervisorOwnerIdentity? =
        R2CompilerProcessProcfs().capture(pid)?.let { observed ->
            SupervisorOwnerIdentity(
                pid = pid,
                bootId = observed.bootId,
                uid = observed.uid,
                startTimeTicks = observed.startTimeTicks,
                processName = observed.processName,
            )
        }

    private fun assertExactSupervisorOwnerLive(
        expected: SupervisorOwnerIdentity,
        compilerPid: Int,
    ) {
        assertTrue(expected.pid > 0)
        assertNotEquals(Process.myPid(), expected.pid)
        assertNotEquals(compilerPid, expected.pid)
        assertEquals(Process.myUid(), expected.uid)
        assertEquals(SUPERVISOR_PROCESS_NAME, expected.processName)
        assertEquals(expected, captureSupervisorOwner(expected.pid))
    }

    private fun killExactSupervisorOwner(
        expected: SupervisorOwnerIdentity,
        compilerPid: Int,
    ) {
        assertExactSupervisorOwnerLive(expected, compilerPid)
        Process.killProcess(expected.pid)
    }

    private fun issueOverlappedExactProcessKills(
        expectedOwner: SupervisorOwnerIdentity,
        expectedCompiler: R2CompilerProcessEpochIdentity,
    ): OverlappedProcessKillEvidence {
        val ready = CountDownLatch(2)
        val fire = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val ownerKill = executor.submit<ProcessKillSignalTiming> {
                assertExactSupervisorOwnerLive(
                    expected = expectedOwner,
                    compilerPid = expectedCompiler.pid,
                )
                ready.countDown()
                check(
                    fire.await(
                        CALLBACK_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                    ),
                ) {
                    "Timed out waiting to fire the exact supervisor-owner kill."
                }
                val initiationNanos =
                    SystemClock.elapsedRealtimeNanos()
                assertTrue(initiationNanos > 0L)
                Process.killProcess(expectedOwner.pid)
                ProcessKillSignalTiming(
                    initiationNanos = initiationNanos,
                    returnedNanos = SystemClock.elapsedRealtimeNanos(),
                )
            }
            val compilerKill = executor.submit<ProcessKillSignalTiming> {
                assertExactCompilerEpochLive(expectedCompiler)
                ready.countDown()
                check(
                    fire.await(
                        CALLBACK_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                    ),
                ) {
                    "Timed out waiting to fire the exact compiler-epoch kill."
                }
                val initiationNanos =
                    SystemClock.elapsedRealtimeNanos()
                assertTrue(initiationNanos > 0L)
                Process.killProcess(expectedCompiler.pid)
                ProcessKillSignalTiming(
                    initiationNanos = initiationNanos,
                    returnedNanos = SystemClock.elapsedRealtimeNanos(),
                )
            }
            assertTrue(
                "The overlapped process-kill workers did not reach the shared fault boundary.",
                ready.await(
                    CALLBACK_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                ),
            )
            val faultBoundaryNanos =
                SystemClock.elapsedRealtimeNanos()
            assertTrue(faultBoundaryNanos > 0L)
            fire.countDown()
            val ownerKillTiming = ownerKill.get(
                PROCESS_DEATH_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            val compilerKillTiming = compilerKill.get(
                PROCESS_DEATH_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            val bothSignalsReturnedNanos =
                maxOf(
                    ownerKillTiming.returnedNanos,
                    compilerKillTiming.returnedNanos,
                )
            val initiationSpanNanos =
                maxOf(
                    ownerKillTiming.initiationNanos,
                    compilerKillTiming.initiationNanos,
                ) -
                    minOf(
                        ownerKillTiming.initiationNanos,
                        compilerKillTiming.initiationNanos,
                    )
            assertTrue(
                "A kill signal preceded the shared fault boundary.",
                ownerKillTiming.initiationNanos >= faultBoundaryNanos &&
                    compilerKillTiming.initiationNanos >= faultBoundaryNanos,
            )
            assertTrue(
                "A kill signal return preceded its initiation.",
                ownerKillTiming.returnedNanos >=
                    ownerKillTiming.initiationNanos &&
                    compilerKillTiming.returnedNanos >=
                    compilerKillTiming.initiationNanos,
            )
            OverlappedProcessKillEvidence(
                faultBoundaryNanos = faultBoundaryNanos,
                ownerKillInitiationNanos =
                    ownerKillTiming.initiationNanos,
                compilerKillInitiationNanos =
                compilerKillTiming.initiationNanos,
                bothSignalsReturnedNanos = bothSignalsReturnedNanos,
                initiationSpanNanos = initiationSpanNanos,
                compilerObservationAtValidationBarrier =
                    R2CompilerProcessObservation.SAME,
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun assertExactCompilerEpochLive(
        expected: R2CompilerProcessEpochIdentity,
    ) {
        assertTrue(expected.pid > 0)
        assertNotEquals(Process.myPid(), expected.pid)
        assertEquals(Process.myUid(), expected.uid)
        assertEquals(COMPILER_PROCESS_NAME, expected.processName)
        assertEquals(
            "The old compiler epoch was not exact-live at the overlapped fault boundary.",
            R2CompilerProcessObservation.SAME,
            R2CompilerProcessProcfs().observe(expected),
        )
    }

    private fun killExactCompilerEpochIfStillLive(
        expected: R2CompilerProcessEpochIdentity,
    ) {
        assertTrue(expected.pid > 0)
        assertNotEquals(Process.myPid(), expected.pid)
        assertEquals(Process.myUid(), expected.uid)
        assertEquals(COMPILER_PROCESS_NAME, expected.processName)
        when (R2CompilerProcessProcfs().observe(expected)) {
            R2CompilerProcessObservation.SAME ->
                Process.killProcess(expected.pid)
            R2CompilerProcessObservation.ABSENT,
            R2CompilerProcessObservation.REUSED -> Unit
            R2CompilerProcessObservation.UNREADABLE ->
                throw AssertionError(
                    "Could not exactly verify the old compiler epoch before kill: " +
                        expected,
                )
        }
    }

    private fun awaitSupervisorOwnerGone(
        expected: SupervisorOwnerIdentity,
    ) {
        val resourceProbe = RemoteProcessResourceProbe(context)
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
        do {
            val actual = captureSupervisorOwner(expected.pid)
            if (actual != null && actual != expected) return
            if (
                actual == null &&
                !resourceProbe.processDirectoryExists(expected.pid)
            ) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Production supervisor owner remained live after exact kill: " +
                expected,
        )
    }

    private fun awaitDifferentSupervisorOwner(
        previous: SupervisorOwnerIdentity,
    ): SupervisorOwnerIdentity {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(SUPERVISOR_OWNER_TIMEOUT_SECONDS)
        var stableCandidate: SupervisorOwnerIdentity? = null
        var stableSamples = 0
        var lastMatches = emptyList<SupervisorOwnerIdentity>()
        do {
            lastMatches = activityManager.runningAppProcesses
                .orEmpty()
                .asSequence()
                .filter { process ->
                    process.uid == Process.myUid() &&
                        process.processName == SUPERVISOR_PROCESS_NAME
                }
                .mapNotNull { process ->
                    captureSupervisorOwner(process.pid)?.takeIf { owner ->
                        owner.uid == process.uid &&
                            owner.processName == process.processName &&
                            owner != previous
                    }
                }
                .distinct()
                .toList()
            if (lastMatches.size == 1) {
                val candidate = lastMatches.single()
                if (candidate == stableCandidate) {
                    stableSamples++
                } else {
                    stableCandidate = candidate
                    stableSamples = 1
                }
                if (stableSamples >= SUPERVISOR_STABLE_SAMPLE_COUNT) {
                    return candidate
                }
            } else {
                stableCandidate = null
                stableSamples = 0
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Could not find one stable replacement production supervisor " +
                "owner after $previous; last matches=$lastMatches",
        )
    }

    @Suppress("DEPRECATION")
    private fun bindCompilerWhileQuarantined(): QuarantineBinding {
        val discoveryIntent = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val serviceInfo = context.packageManager
            .queryIntentServices(discoveryIntent, 0)
            .single()
            .serviceInfo
        val connection = QuarantineServiceConnection()
        val bound = context.bindService(
            Intent(discoveryIntent).setComponent(
                ComponentName(serviceInfo.packageName, serviceInfo.name),
            ),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("Failed to request a quarantined compiler binding.", bound)
        return QuarantineBinding(context, connection).also {
            awaitCompilerProcess()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                assertTrue(
                    "Quarantined compiler did not publish a null binding.",
                    connection.awaitNullBinding(),
                )
            }
            assertFalse(
                "A successor compiler Binder escaped before old-epoch death proof.",
                connection.awaitConnected(QUARANTINE_OBSERVATION_MILLIS),
            )
        }
    }

    private fun cancellation(
        requestId: String,
        jobToken: String,
    ) = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_JOB_TOKEN, jobToken)
    }

    private fun acknowledgement(
        requestId: String,
        jobToken: String,
    ) = cancellation(requestId, jobToken).apply {
        putString(
            CompilerTransportProbeProtocol.KEY_CANCELLATION_COMMAND,
            CompilerTransportProbeProtocol.COMMAND_ACKNOWLEDGE,
        )
    }

    private fun remoteCompletionResult(requestId: String) = Bundle().apply {
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(
            TypeScriptCompilerContract.KEY_OUTCOME,
            TypeScriptCompilerContract.OUTCOME_FAILED,
        )
        putBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, false)
        putString(
            TypeScriptCompilerContract.KEY_ERROR_CODE,
            TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
        )
        putString(
            TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
            "Synthetic late remote terminal for client-arbiter ordering verification.",
        )
        putInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, 0)
        putInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, 0)
        putLong(TypeScriptCompilerContract.KEY_ELAPSED_MS, 0L)
    }

    private fun assertAccepted(ticket: Bundle, requestId: String) {
        assertEquals(
            TypeScriptCompilerContract.VERSION,
            ticket.getInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION),
        )
        assertEquals(
            TypeScriptCompilerContract.HASH,
            ticket.getString(TypeScriptCompilerContract.KEY_CONTRACT_HASH),
        )
        assertEquals(requestId, ticket.getString(TypeScriptCompilerContract.KEY_REQUEST_ID))
        assertTrue(ticket.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE), ticket.getBoolean(
            TypeScriptCompilerContract.KEY_ACCEPTED,
            false,
        ))
        val token = ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN)
        assertNotNull(token)
        assertTrue(token.orEmpty().length <= TypeScriptCompilerContract.MAX_JOB_TOKEN_CHARS)
        assertFalse(ticket.containsKey(TypeScriptCompilerContract.KEY_ERROR_CODE))
    }

    private fun newArchivePair(label: String): ArchivePair {
        val input = File.createTempFile("r1-accepted-$label-input-", ".archive", context.cacheDir)
            .apply {
                writeText("R1 transport probe input is intentionally opaque.")
            }
        val output = File.createTempFile("r1-accepted-$label-output-", ".archive", context.cacheDir)
            .apply {
                writeText(OUTPUT_SENTINEL)
            }
        return ArchivePair(input = input, output = output)
    }

    private fun assertFreshCompilerEpochAcceptsCancellation(
        oldEpoch: R2CompilerProcessEpochIdentity,
        oldFdIdentity: RemoteProcessIdentity,
        label: String,
    ) {
        val replacementFiles = newArchivePair(label)
        try {
            bindCompilerService().use { replacementSession ->
                val replacementInfo = enableProcessSupervisorTelemetry(
                    replacementSession.compiler,
                )
                val replacementEpoch =
                    R2CompilerProcessEpochIdentity.from(replacementInfo)
                assertNotEquals(oldEpoch, replacementEpoch)
                val replacementFdEvidence =
                    captureCompilerFdEvidence(label)
                assertNotEquals(
                    oldFdIdentity,
                    replacementFdEvidence.identity,
                )

                val replacementCallback = RecordingCallback()
                val replacementRequestId = newRequestId(label)
                val replacementTicket = startTransportProbe(
                    compiler = replacementSession.compiler,
                    requestId = replacementRequestId,
                    files = replacementFiles,
                    callback = replacementCallback,
                    mode = CompilerTransportProbeProtocol.MODE_HOLD,
                    timeoutMs = SERVICE_DEATH_REMOTE_DELAY_MILLIS,
                )
                replacementCallback.markStartReturned()
                assertAccepted(replacementTicket, replacementRequestId)
                val replacementToken = requireNotNull(
                    replacementTicket.getString(
                        TypeScriptCompilerContract.KEY_JOB_TOKEN,
                    ),
                )
                assertTrue(
                    replacementSession.compiler.cancelCompilation(
                        acknowledgement(
                            replacementRequestId,
                            replacementToken,
                        ),
                    ),
                )
                replacementFdEvidence.assertActive()
                assertTrue(
                    replacementSession.compiler.cancelCompilation(
                        cancellation(
                            replacementRequestId,
                            replacementToken,
                        ),
                    ),
                )
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_CANCELLED,
                    replacementCallback.awaitTerminal().getString(
                        TypeScriptCompilerContract.KEY_OUTCOME,
                    ),
                )
                awaitActiveJobCount(
                    replacementSession.compiler,
                    expected = 0,
                )
                replacementFdEvidence.assertReleased()
                assertOutputRemainsEmpty(replacementFiles.output)
            }
        } finally {
            replacementFiles.delete()
        }
    }

    private fun assertOutputRemainsEmpty(output: File) {
        assertTrue("Transport probe output was not truncated: $output", output.exists())
        assertEquals("Transport probe output must remain empty.", 0L, output.length())
    }

    private fun awaitActiveJobCount(
        compiler: ITypeScriptCompilerPlugin,
        expected: Int,
    ) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(CALLBACK_TIMEOUT_SECONDS)
        do {
            val info = compiler.compilerInfo
            if (
                info.containsKey(CompilerTransportProbeProtocol.KEY_ACTIVE_JOB_COUNT) &&
                info.getInt(
                    CompilerTransportProbeProtocol.KEY_ACTIVE_JOB_COUNT,
                    -1,
                ) == expected
            ) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Compiler transport probe did not reach activeJobCount=$expected.",
        )
    }

    private fun awaitSupervisorOwnerRecord(
        oldIdentity: R2CompilerProcessEpochIdentity,
        expectedOutcome: R2CompilerProcessSupervisorOwnerOutcome,
        requireCompilerAbsent: Boolean = false,
        expectedWorkTokenSha256: String? = null,
        timeoutSeconds: Long = SUPERVISOR_OWNER_TIMEOUT_SECONDS,
    ): R2CompilerProcessSupervisorOwnerRecord {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(timeoutSeconds)
        var lastRecord: R2CompilerProcessSupervisorOwnerRecord? = null
        do {
            if (requireCompilerAbsent) {
                val liveCompilerProcesses =
                    activityManager.runningAppProcesses
                    .orEmpty()
                    .mapNotNull { process ->
                        if (
                            process.uid != Process.myUid() ||
                            process.processName != COMPILER_PROCESS_NAME
                        ) {
                            return@mapNotNull null
                        }
                        R2CompilerProcessProcfs().capture(process.pid)
                            ?.takeIf { observed ->
                                observed.uid == process.uid &&
                                    observed.processName == process.processName
                            }
                            ?.let { observed -> process.pid to observed }
                    }
                assertTrue(
                    "A successor compiler started before the system Job owner " +
                        "reconciled the pre-armed marker: " +
                        "$liveCompilerProcesses",
                    liveCompilerProcesses.isEmpty(),
                )
            }
            val record = R2CompilerProcessSupervisorOwnerTelemetry.read(context)
            lastRecord = record
            if (
                record?.outcome == expectedOutcome &&
                record.oldBootId == oldIdentity.bootId &&
                record.oldPid == oldIdentity.pid &&
                record.oldStartTimeTicks == oldIdentity.startTimeTicks &&
                (
                    expectedWorkTokenSha256 == null ||
                        record.workTokenSha256 ==
                        expectedWorkTokenSha256
                    )
            ) {
                return record
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "The system JobService owner did not reach $expectedOutcome; " +
                "lastRecord=$lastRecord, marker=" +
                R2CompilerProcessQuarantine.inspectForJob(
                    context = context,
                    prearmDeadline = null,
                ) +
                ", jobPresent=" +
                R2CompilerProcessSupervisorJobScheduler
                    .hasAnyJobForTesting(context) +
                ".",
        )
    }

    private fun awaitJobLifecycleRecords(
        description: String,
        timeoutSeconds: Long = SUPERVISOR_OWNER_TIMEOUT_SECONDS,
        predicate: (
            List<R2CompilerProcessSupervisorJobLifecycleRecord>,
        ) -> Boolean,
    ): List<R2CompilerProcessSupervisorJobLifecycleRecord> {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(timeoutSeconds)
        var lastRecords =
            emptyList<R2CompilerProcessSupervisorJobLifecycleRecord>()
        do {
            val records =
                R2CompilerProcessSupervisorJobLifecycleJournal
                    .readForTesting(context)
            if (records != null) {
                lastRecords = records
                if (predicate(records)) return records
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "The JobService lifecycle journal did not observe $description; " +
                "lastRecords=$lastRecords.",
        )
    }

    private fun observeJobLifecycleRecords(
        timeoutSeconds: Long,
        predicate: (
            List<R2CompilerProcessSupervisorJobLifecycleRecord>,
        ) -> Boolean,
    ): List<R2CompilerProcessSupervisorJobLifecycleRecord>? {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(timeoutSeconds)
        do {
            val records =
                R2CompilerProcessSupervisorJobLifecycleJournal
                    .readForTesting(context)
            if (records != null && predicate(records)) return records
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        return R2CompilerProcessSupervisorJobLifecycleJournal
            .readForTesting(context)
            ?.takeIf(predicate)
    }

    private fun supervisorOwnerIdentity(
        record: R2CompilerProcessSupervisorOwnerRecord,
    ): SupervisorOwnerIdentity =
        SupervisorOwnerIdentity(
            pid = record.ownerPid,
            bootId = record.owner.bootId,
            uid = record.owner.uid,
            startTimeTicks = record.owner.startTimeTicks,
            processName = record.owner.processName,
        )

    private fun supervisorOwnerIdentity(
        record: R2CompilerProcessSupervisorJobLifecycleRecord,
    ): SupervisorOwnerIdentity =
        SupervisorOwnerIdentity(
            pid = record.ownerPid,
            bootId = record.owner.bootId,
            uid = record.owner.uid,
            startTimeTicks = record.owner.startTimeTicks,
            processName = record.owner.processName,
        )

    private fun requestForcedEligibilityForSupervisorJob(
        expectedWork: R2CompilerProcessSupervisorJobWork,
    ): ForcedJobDispatchAttempt {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val component = ComponentName(
            context,
            R2CompilerProcessSupervisorJobService::class.java,
        )
        val matches = scheduler.allPendingJobs.filter { job ->
            job.service == component &&
                R2CompilerProcessSupervisorJobWork.from(job.extras) ==
                expectedWork
        }
        if (matches.size != 1) {
            return ForcedJobDispatchAttempt(
                matchingPendingJobs = matches.size,
                output = "",
                commandBoundaryNanos = null,
            )
        }
        val userId = Process.myUid() / ANDROID_UID_RANGE_PER_USER
        val commandBoundaryNanos = SystemClock.elapsedRealtimeNanos()
        val output = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(
                "cmd jobscheduler run -f -u $userId " +
                    "${context.packageName} ${matches.single().id}",
            ).use { descriptor ->
                ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                    .bufferedReader()
                    .use { reader -> reader.readText() }
            }
        return ForcedJobDispatchAttempt(
            matchingPendingJobs = matches.size,
            output = output,
            commandBoundaryNanos = commandBoundaryNanos,
        )
    }

    private fun workTokenSha256(workToken: String): String {
        val bytes = workToken.toByteArray(Charsets.US_ASCII)
        return try {
            R2Digests.sha256(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun awaitSupervisorJobForIdentity(
        identity: R2CompilerProcessEpochIdentity,
    ) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(SUPERVISOR_OWNER_TIMEOUT_SECONDS)
        do {
            if (
                R2CompilerProcessSupervisorJobScheduler
                    .hasJobForIdentityForTesting(context, identity)
            ) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "The exact pre-armed Job was not visible for the old compiler epoch.",
        )
    }

    private fun awaitSupervisorJobAbsent() {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(SUPERVISOR_OWNER_TIMEOUT_SECONDS)
        do {
            if (
                !R2CompilerProcessSupervisorJobScheduler
                    .hasAnyJobForTesting(context)
            ) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "The fixed supervisor Job did not become absent.",
        )
    }

    private fun awaitCompilerProcessQuarantineCleared() {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(SUPERVISOR_OWNER_TIMEOUT_SECONDS)
        do {
            if (!R2CompilerProcessQuarantine.hasMarker(context)) return
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "The production supervisor did not clear the old-epoch quarantine.",
        )
    }

    private fun awaitTerminalIntentClaimed(
        compiler: ITypeScriptCompilerPlugin,
    ) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(CALLBACK_TIMEOUT_SECONDS)
        do {
            val info = compiler.compilerInfo
            if (
                info.containsKey(
                    CompilerTransportProbeProtocol.KEY_TERMINAL_INTENT_CLAIMED,
                ) &&
                info.getBoolean(
                    CompilerTransportProbeProtocol.KEY_TERMINAL_INTENT_CLAIMED,
                    false,
                )
            ) {
                return
            }
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "Compiler transport probe did not claim a terminal intent.",
        )
    }

    private fun newRequestId(label: String) = "r1-$label-${UUID.randomUUID()}"

    @Suppress("DEPRECATION")
    private fun bindCompilerService(): BoundCompilerSession {
        val discoveryIntent = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val matches = context.packageManager.queryIntentServices(discoveryIntent, 0)
        assertEquals("Expected exactly one TypeScript compiler service.", 1, matches.size)
        val serviceInfo = matches.single().serviceInfo
        val connection = AwaitingServiceConnection()
        val bound = context.bindService(
            Intent(discoveryIntent).setComponent(
                ComponentName(serviceInfo.packageName, serviceInfo.name),
            ),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        assertTrue("Failed to bind the TypeScript compiler service.", bound)
        return try {
            val rawBinder = connection.awaitBinder()
            BoundCompilerSession(
                context = context,
                connection = connection,
                rawBinder = rawBinder,
                compiler = requireNotNull(
                    ITypeScriptCompilerPlugin.Stub.asInterface(rawBinder),
                ),
            )
        } catch (error: Throwable) {
            context.unbindService(connection)
            throw error
        }
    }

    private fun awaitCompilerProcess(): ActivityManager.RunningAppProcessInfo {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(PROCESS_DISCOVERY_TIMEOUT_SECONDS)
        do {
            val matches = activityManager.runningAppProcesses
                .orEmpty()
                .filter { process ->
                    process.processName == COMPILER_PROCESS_NAME &&
                        process.uid == Process.myUid()
                }
            if (matches.size == 1) return matches.single()
            SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Could not find exactly one live compiler process.")
    }

    private fun captureCompilerFdEvidence(label: String): CompilerFdEvidence {
        val compilerProcess = awaitCompilerProcess()
        val resourceProbe = RemoteProcessResourceProbe(context)
        val identity = resourceProbe.requireIdentity(compilerProcess.pid)
        resourceProbe.awaitStableFileDescriptors(
            pid = compilerProcess.pid,
            label = "$label accepted lifecycle baseline",
            expectedIdentity = identity,
        )
        return CompilerFdEvidence(
            label = label,
            resourceProbe = resourceProbe,
            identity = identity,
        )
    }

    private data class SupervisorOwnerIdentity(
        val pid: Int,
        val bootId: String,
        val uid: Int,
        val startTimeTicks: Long,
        val processName: String,
    )

    private enum class RetainedExactWorkFaultMode {
        OWNER_THEN_COMPILER_AFTER_DEATH_PROOF,
        OVERLAPPED_OWNER_AND_COMPILER_DEATH,
    }

    private data class OverlappedProcessKillEvidence(
        val faultBoundaryNanos: Long,
        val ownerKillInitiationNanos: Long,
        val compilerKillInitiationNanos: Long,
        val bothSignalsReturnedNanos: Long,
        val initiationSpanNanos: Long,
        val compilerObservationAtValidationBarrier:
            R2CompilerProcessObservation,
    )

    private data class ProcessKillSignalTiming(
        val initiationNanos: Long,
        val returnedNanos: Long,
    )

    private enum class JobOwnerDeathDispatchPath {
        NATURAL,
        POST_FORCE_INVOCATION_BOUNDARY,
        NATURAL_FORCE_RACE,
    }

    private data class ForcedJobDispatchAttempt(
        val matchingPendingJobs: Int,
        val output: String,
        val commandBoundaryNanos: Long?,
    )

    private data class SupervisorRegistryObservation(
        val binder: IBinder,
        val death: CountDownLatch,
    )

    private data class CompilerFdEvidence(
        val label: String,
        val resourceProbe: RemoteProcessResourceProbe,
        val identity: RemoteProcessIdentity,
    ) {

        fun assertActive() {
            resourceProbe.awaitStagingArchiveFileDescriptors(
                pid = identity.pid,
                label = "$label accepted lifecycle ownership",
                expectedCount = 2,
                expectedIdentity = identity,
            )
        }

        fun assertReleased() {
            resourceProbe.awaitStableFileDescriptors(
                pid = identity.pid,
                label = "$label accepted lifecycle terminal cleanup",
                expectedIdentity = identity,
            )
        }

        fun awaitReleasedAfterLifecycleClose() {
            val deadline = SystemClock.uptimeMillis() +
                TimeUnit.SECONDS.toMillis(CALLBACK_TIMEOUT_SECONDS)
            var lastFailure: AssertionError? = null
            do {
                try {
                    assertReleased()
                    return
                } catch (failure: AssertionError) {
                    val actualIdentity = resourceProbe.identityOrNull(identity.pid)
                    if (
                        (actualIdentity != null && actualIdentity != identity) ||
                        (
                            actualIdentity == null &&
                                !resourceProbe.processDirectoryExists(identity.pid)
                            )
                    ) {
                        /*
                         * Process exit or PID reuse is stronger release evidence than a stable
                         * descriptor sample: the old process can no longer own either archive.
                         */
                        return
                    }
                    lastFailure = failure
                }
                SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
            } while (SystemClock.uptimeMillis() < deadline)
            throw requireNotNull(lastFailure)
        }

        fun awaitProcessGone() {
            val deadline = SystemClock.uptimeMillis() +
                TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
            do {
                val actualIdentity = resourceProbe.identityOrNull(identity.pid)
                if (actualIdentity != null && actualIdentity != identity) return
                if (
                    actualIdentity == null &&
                    !resourceProbe.processDirectoryExists(identity.pid)
                ) {
                    return
                }
                SystemClock.sleep(PROCESS_POLL_INTERVAL_MILLIS)
            } while (SystemClock.uptimeMillis() < deadline)
            throw AssertionError(
                "$label compiler process identity remained live after Binder death.",
            )
        }
    }

    private data class ArchivePair(
        val input: File,
        val output: File,
    ) {
        fun delete() {
            input.delete()
            output.delete()
        }
    }

    private class RecordingCallback(
        private val terminalArbiter: R1ClientTerminalArbiter? = null,
    ) : ITypeScriptCompilationCallback.Stub() {

        val terminal = CountDownLatch(1)
        val terminalCount = AtomicInteger()
        val progressCount = AtomicInteger()
        private val terminalResult = AtomicReference<Bundle>()

        @Volatile
        private var startReturned = false

        @Volatile
        var callbackBeforeStartReturned = false
            private set

        override fun onProgress(progress: Bundle?) {
            progressCount.incrementAndGet()
        }

        override fun onCompleted(result: Bundle?) {
            if (!startReturned) {
                callbackBeforeStartReturned = true
            }
            terminalCount.incrementAndGet()
            terminalResult.compareAndSet(null, result)
            terminalArbiter?.acceptRemote(result)
            terminal.countDown()
        }

        fun markStartReturned() {
            startReturned = true
        }

        fun awaitTerminal(): Bundle {
            assertTrue(
                "Timed out waiting for the accepted transport probe terminal callback.",
                terminal.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(terminalResult.get())
        }
    }

    private class BoundCompilerSession(
        private val context: Context,
        private val connection: ServiceConnection,
        val rawBinder: IBinder,
        val compiler: ITypeScriptCompilerPlugin,
    ) : Closeable {

        private var bound = true

        override fun close() {
            if (!bound) return
            bound = false
            context.unbindService(connection)
        }
    }

    private class AwaitingServiceConnection : ServiceConnection {

        private val connected = CountDownLatch(1)

        @Volatile
        private var binder: IBinder? = null

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) {
            connected.countDown()
        }

        fun awaitBinder(): IBinder {
            assertTrue(
                "Timed out waiting for the compiler service.",
                connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(binder) { "Compiler service returned a null binding." }
        }
    }

    private class QuarantineServiceConnection : ServiceConnection {

        private val connected = CountDownLatch(1)
        private val nullBound = CountDownLatch(1)

        @Volatile
        private var binder: IBinder? = null

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            binder = service
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) {
            nullBound.countDown()
        }

        fun awaitConnected(timeoutMillis: Long): Boolean =
            connected.await(timeoutMillis, TimeUnit.MILLISECONDS)

        fun awaitNullBinding(): Boolean =
            nullBound.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        fun awaitBinder(): IBinder {
            assertTrue(
                "Timed out waiting for the quarantined binding to recover.",
                connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(binder) {
                "Recovered compiler service returned a null binding."
            }
        }
    }

    private class QuarantineBinding(
        private val context: Context,
        private val connection: QuarantineServiceConnection,
    ) : Closeable {

        override fun close() {
            runCatching { context.unbindService(connection) }
        }

        fun awaitBinder(): IBinder = connection.awaitBinder()
    }

    private class AwaitingMessengerConnection : ServiceConnection {

        private val connected = CountDownLatch(1)

        @Volatile
        private var messenger: Messenger? = null

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            messenger = Messenger(service)
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        override fun onNullBinding(name: ComponentName) {
            connected.countDown()
        }

        fun awaitMessenger(): Messenger {
            assertTrue(
                "Timed out waiting for the callback helper service.",
                connected.await(SERVICE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(messenger) {
                "Callback helper returned a null binding."
            }
        }
    }

    private companion object {
        const val CALLBACK_TIMEOUT_SECONDS = 10L
        const val SUPERVISOR_OWNER_TIMEOUT_SECONDS = 30L
        const val SERVICE_TIMEOUT_SECONDS = 10L
        const val PROCESS_DISCOVERY_TIMEOUT_SECONDS = 5L
        const val PROCESS_DEATH_TIMEOUT_SECONDS = 10L
        const val PREARMED_TERMINATION_TIMEOUT_MILLIS = 20_000L
        const val PREARMED_TERMINATION_DELAY_MILLIS = 1_500L
        const val REPLACEMENT_PREARM_TIMEOUT_MILLIS = 30_000L
        const val JOB_REPLACEMENT_TERMINATION_TIMEOUT_MILLIS = 30_000L
        const val JOB_REPLACEMENT_TERMINATION_DELAY_MILLIS = 5_000L
        const val JOB_REPLACEMENT_STABILITY_MILLIS = 1_000L
        const val JOB_OWNER_DEATH_PREARM_TIMEOUT_MILLIS = 120_000L
        const val JOB_OWNER_DEATH_NATURAL_REDELIVERY_TIMEOUT_SECONDS = 30L
        const val JOB_OWNER_DEATH_REDELIVERY_TIMEOUT_SECONDS = 90L
        const val JOB_OWNER_DEATH_FORCE_RACE_TIMEOUT_SECONDS = 10L
        const val FORCED_JOB_DISPATCH_SUCCESS = "Running job [FORCED]"
        const val ANDROID_UID_RANGE_PER_USER = 100_000
        const val S6H_EVIDENCE_LOG_TAG = "R2S6hJobDeath"
        const val S6I_EVIDENCE_LOG_TAG = "R2S6iDualDeath"
        const val PROCESS_POLL_INTERVAL_MILLIS = 50L
        const val SUPERVISOR_STABLE_SAMPLE_COUNT = 3
        const val EXACTLY_ONCE_OBSERVATION_MILLIS = 500L
        const val NO_CALLBACK_OBSERVATION_MILLIS = 750L
        const val RETIRED_CALLBACK_STABILITY_OBSERVATION_MILLIS = 1_000L
        const val QUARANTINE_OBSERVATION_MILLIS = 500L
        const val COMPLETION_RACE_TIMEOUT_MILLIS = 750L
        const val COMPLETION_RACE_DELAY_MILLIS = 500L
        const val CANCEL_RACE_TIMEOUT_MILLIS = 750L
        const val CANCEL_RACE_DELAY_MILLIS = 250L
        const val CANCEL_RACE_OBSERVATION_MILLIS = 750L
        const val TIMEOUT_RACE_MILLIS = 500L
        const val UNACKNOWLEDGED_TIMEOUT_MILLIS = 10_000L
        const val SERVICE_CLOSE_DELAY_MILLIS = 10_000L
        const val CLAIMED_COMPLETION_DELAY_MILLIS = 100L
        const val CLAIMED_TERMINAL_CLEANUP_DELAY_MILLIS = 3_000L
        const val SERVICE_DEATH_REMOTE_DELAY_MILLIS = 10_000L
        const val OUTPUT_SENTINEL =
            "This content must be truncated before a terminal callback."
        const val COMPILER_PROCESS_NAME =
            "io.github.supermonster003.autojs6.plugin.typescript:typescript_compiler"
        const val SUPERVISOR_PROCESS_NAME =
            "io.github.supermonster003.autojs6.plugin.typescript:typescript_supervisor"
    }
}
