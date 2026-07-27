package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginIds

/**
 * Debug-only callback owner used to prove that the real compiler service observes callback Binder
 * death and releases an accepted transport probe.
 *
 * The instrumentation process kills only this helper process. The compiler process and the test
 * process survive, so the coordinator's callback death recipient is the only cleanup path.
 */
class R1CallbackClientProbeService : Service() {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "r1-callback-client-probe").apply {
            isDaemon = true
        }
    }
    private val messenger = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            if (message.what != MSG_START) return@Handler false
            val replyTo = message.replyTo ?: return@Handler true
            val request = Bundle(message.data)
            executor.execute {
                startAcceptedProbe(request, replyTo)
            }
            true
        },
    )

    @Volatile
    private var compilerConnection: ServiceConnection? = null

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        compilerConnection?.let { connection ->
            runCatching { unbindService(connection) }
        }
        compilerConnection = null
        executor.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun startAcceptedProbe(
        request: Bundle,
        replyTo: Messenger,
    ) {
        val result = Bundle().apply {
            putInt(KEY_HELPER_PID, Process.myPid())
        }
        runCatching {
            val requestId = requireNotNull(request.getString(KEY_REQUEST_ID))
            val inputPath = requireNotNull(request.getString(KEY_INPUT_PATH))
            val outputPath = requireNotNull(request.getString(KEY_OUTPUT_PATH))
            val discoveryIntent = Intent(TypeScriptPluginActions.COMPILER)
                .addCategory(TypeScriptPluginIds.ID)
                .setPackage(packageName)
            val serviceInfo = packageManager
                .queryIntentServices(discoveryIntent, 0)
                .single()
                .serviceInfo
            val connection = AwaitingConnection()
            check(
                bindService(
                    Intent(discoveryIntent).setComponent(
                        ComponentName(serviceInfo.packageName, serviceInfo.name),
                    ),
                    connection,
                    Context.BIND_AUTO_CREATE,
                ),
            ) {
                "Callback helper could not bind the compiler service."
            }
            compilerConnection = connection
            val compiler = ITypeScriptCompilerPlugin.Stub.asInterface(
                connection.awaitBinder(),
            )
            val input = ParcelFileDescriptor.open(
                File(inputPath),
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            val output = ParcelFileDescriptor.open(
                File(outputPath),
                ParcelFileDescriptor.MODE_READ_WRITE,
            )
            val ticket = try {
                compiler.startCompilation(
                    validRequest(requestId),
                    input,
                    output,
                    object : ITypeScriptCompilationCallback.Stub() {
                        override fun onProgress(progress: Bundle?) = Unit

                        override fun onCompleted(result: Bundle?) = Unit
                    },
                )
            } finally {
                input.close()
                output.close()
            }
            result.putBoolean(
                KEY_ACCEPTED,
                ticket.getBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, false),
            )
            result.putString(
                KEY_JOB_TOKEN,
                ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN),
            )
            result.putString(
                KEY_ERROR_CODE,
                ticket.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            result.putString(
                KEY_ERROR_MESSAGE,
                ticket.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE),
            )
            val jobToken = ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN)
            result.putBoolean(
                KEY_ACKNOWLEDGED,
                ticket.getBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, false) &&
                    jobToken != null &&
                    compiler.cancelCompilation(
                        acknowledgement(requestId, jobToken),
                    ),
            )
        }.onFailure { error ->
            result.putBoolean(KEY_ACCEPTED, false)
            result.putBoolean(KEY_ACKNOWLEDGED, false)
            result.putString(
                KEY_ERROR_MESSAGE,
                error.message?.take(TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS)
                    ?: error.javaClass.simpleName,
            )
        }
        replyTo.send(
            Message.obtain(null, MSG_STARTED).apply {
                data = result
            },
        )
    }

    private fun acknowledgement(
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
        putString(
            CompilerTransportProbeProtocol.KEY_CANCELLATION_COMMAND,
            CompilerTransportProbeProtocol.COMMAND_ACKNOWLEDGE,
        )
    }

    private fun validRequest(requestId: String) = Bundle().apply {
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
        putString(TypeScriptCompilerContract.KEY_PROJECT_ID, "r1-callback-death-probe")
        putString(
            TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
            "autojs://source/r1-callback-death-probe/main.ts",
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
        putLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, CALLBACK_PROBE_TIMEOUT_MS)
        putBoolean(CompilerTransportProbeProtocol.KEY_ENABLED, true)
        putString(
            CompilerTransportProbeProtocol.KEY_MODE,
            CompilerTransportProbeProtocol.MODE_HOLD,
        )
        putLong(
            CompilerTransportProbeProtocol.KEY_COMPLETION_DELAY_MS,
            CompilerTransportProbeProtocol.DEFAULT_COMPLETION_DELAY_MS,
        )
    }

    private class AwaitingConnection : ServiceConnection {

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
            check(connected.await(SERVICE_BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Callback helper timed out binding the compiler service."
            }
            return requireNotNull(binder) {
                "Callback helper received a null compiler binding."
            }
        }
    }

    companion object {
        const val MSG_START = 1
        const val MSG_STARTED = 2
        const val KEY_REQUEST_ID = "requestId"
        const val KEY_INPUT_PATH = "inputPath"
        const val KEY_OUTPUT_PATH = "outputPath"
        const val KEY_HELPER_PID = "helperPid"
        const val KEY_ACCEPTED = "accepted"
        const val KEY_ACKNOWLEDGED = "acknowledged"
        const val KEY_JOB_TOKEN = "jobToken"
        const val KEY_ERROR_CODE = "errorCode"
        const val KEY_ERROR_MESSAGE = "errorMessage"

        private const val CALLBACK_PROBE_TIMEOUT_MS = 30_000L
        private const val SERVICE_BIND_TIMEOUT_SECONDS = 10L
    }
}
