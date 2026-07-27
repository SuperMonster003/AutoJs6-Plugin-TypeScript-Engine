package io.github.supermonster003.autojs6.plugin.typescript

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
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

    private fun startTransportProbe(
        compiler: ITypeScriptCompilerPlugin,
        requestId: String,
        files: ArchivePair,
        callback: RecordingCallback,
        mode: String,
        timeoutMs: Long,
        completionDelayMs: Long =
            CompilerTransportProbeProtocol.DEFAULT_COMPLETION_DELAY_MS,
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

        fun awaitProcessGone() {
            val deadline = SystemClock.uptimeMillis() +
                TimeUnit.SECONDS.toMillis(PROCESS_DEATH_TIMEOUT_SECONDS)
            do {
                if (resourceProbe.identityOrNull(identity.pid) != identity) return
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
        const val SERVICE_TIMEOUT_SECONDS = 10L
        const val PROCESS_DISCOVERY_TIMEOUT_SECONDS = 5L
        const val PROCESS_DEATH_TIMEOUT_SECONDS = 10L
        const val PROCESS_POLL_INTERVAL_MILLIS = 50L
        const val EXACTLY_ONCE_OBSERVATION_MILLIS = 500L
        const val NO_CALLBACK_OBSERVATION_MILLIS = 750L
        const val COMPLETION_RACE_TIMEOUT_MILLIS = 750L
        const val COMPLETION_RACE_DELAY_MILLIS = 500L
        const val CANCEL_RACE_TIMEOUT_MILLIS = 750L
        const val CANCEL_RACE_DELAY_MILLIS = 250L
        const val CANCEL_RACE_OBSERVATION_MILLIS = 750L
        const val TIMEOUT_RACE_MILLIS = 500L
        const val UNACKNOWLEDGED_TIMEOUT_MILLIS = 10_000L
        const val SERVICE_DEATH_REMOTE_DELAY_MILLIS = 10_000L
        const val OUTPUT_SENTINEL =
            "This content must be truncated before a terminal callback."
        const val COMPILER_PROCESS_NAME =
            "io.github.supermonster003.autojs6.plugin.typescript:typescript_compiler"
    }
}
