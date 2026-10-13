package io.github.supermonster003.autojs6.plugin.typescript

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import org.autojs.plugin.typescript.parser.ITypeScriptParserPlugin
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import org.autojs.plugin.typescript.parser.TypeScriptParserContract as Contract

/**
 * The TypeScript parser service (contract v1, action `org.autojs.plugin.typescript.PARSER`): parses one
 * source file with `ts.createSourceFile` and writes its syntax tree as JSON to the caller's descriptor.
 * It shares the compiler process but neither the compiler page nor its admission lease: at most
 * [Contract.MAX_CONCURRENT_PARSES] parses run at once, and further requests are answered with
 * [Contract.ERROR_BUSY] instead of waiting.
 */
class TypeScriptParserPluginService : Service() {

    private lateinit var host: TypeScriptWebViewParserHost
    private val slots = Semaphore(Contract.MAX_CONCURRENT_PARSES)

    override fun onCreate() {
        super.onCreate()
        TypeScriptWebViewCompilerHost.prepareCompilerProcess()
        host = TypeScriptWebViewParserHost(this, BuildConfig.TYPESCRIPT_VERSION)
    }

    override fun onBind(intent: Intent?): IBinder? {
        // A quarantined compiler process serves nothing, parses included.
        if (R2CompilerProcessQuarantine.hasMarker(this)) return null
        return binder
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) host.discardIfIdle()
    }

    override fun onDestroy() {
        host.close()
        super.onDestroy()
    }

    private val binder = object : ITypeScriptParserPlugin.Stub() {

        override fun getContractVersion(): Int = Contract.VERSION

        override fun getContractHash(): String = Contract.HASH

        override fun getParserInfo(): Bundle = Bundle().apply {
            putInt(Contract.KEY_CONTRACT_VERSION, Contract.VERSION)
            putString(Contract.KEY_CONTRACT_HASH, Contract.HASH)
            putInt(Contract.KEY_TREE_SCHEMA_REVISION, Contract.TREE_SCHEMA_REVISION)
            putString(Contract.KEY_STATUS, Contract.STATUS_READY)
            putString(Contract.KEY_COMPILER_VERSION, BuildConfig.TYPESCRIPT_VERSION)
            putStringArray(Contract.KEY_SUPPORTED_EXTENSIONS, Contract.SUPPORTED_EXTENSIONS)
            putInt(Contract.KEY_MAX_SOURCE_CHARS, Contract.MAX_SOURCE_CHARS)
            putInt(Contract.KEY_MAX_CONCURRENT_PARSES, Contract.MAX_CONCURRENT_PARSES)
        }

        override fun parse(request: Bundle?, treeOutput: ParcelFileDescriptor?): Bundle {
            try {
                if (request == null || treeOutput == null) {
                    return failure(Contract.ERROR_INVALID_REQUEST, "A request Bundle and a tree descriptor are required.")
                }
                val checked = TypeScriptParserRequests.check { key -> request.readValue(key) }
                val valid = when (checked) {
                    is ParserRequestCheck.Rejected -> return failure(checked.code, checked.message)
                    is ParserRequestCheck.Valid -> checked.request
                }
                if (!slots.tryAcquire()) {
                    return failure(Contract.ERROR_BUSY, "${Contract.MAX_CONCURRENT_PARSES} parses are already running.")
                }
                try {
                    return when (val outcome = host.parse(valid)) {
                        is ParserHostOutcome.Parsed -> writeTree(outcome, treeOutput)
                        is ParserHostOutcome.Failed -> failure(outcome.code, outcome.message)
                        ParserHostOutcome.TimedOut -> Bundle().apply {
                            putString(Contract.KEY_OUTCOME, Contract.OUTCOME_TIMED_OUT)
                            putString(Contract.KEY_ERROR_CODE, Contract.ERROR_TIMEOUT)
                            putString(Contract.KEY_ERROR_MESSAGE, "The parse did not finish within ${valid.timeoutMs} ms.")
                        }
                    }
                } finally {
                    slots.release()
                }
            } finally {
                runCatching { treeOutput?.close() }
            }
        }
    }

    private fun writeTree(outcome: ParserHostOutcome.Parsed, treeOutput: ParcelFileDescriptor): Bundle {
        val started = SystemClock.elapsedRealtime()
        val bytes = outcome.tree.toByteArray(Charsets.UTF_8)
        if (bytes.size > Contract.MAX_TREE_BYTES) {
            return failure(Contract.ERROR_TREE_LIMIT, "The tree has more than ${Contract.MAX_TREE_BYTES} bytes.")
        }
        try {
            FileOutputStream(treeOutput.fileDescriptor).use { stream ->
                stream.write(bytes)
                stream.flush()
            }
        } catch (error: Exception) {
            return failure(Contract.ERROR_OUTPUT_TRANSPORT, "The tree could not be written: ${error.message ?: error.javaClass.simpleName}")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Bundle().apply {
            putString(Contract.KEY_OUTCOME, Contract.OUTCOME_PARSED)
            putLong(Contract.KEY_TREE_BYTE_LENGTH, bytes.size.toLong())
            putString(Contract.KEY_TREE_SHA256, digest)
            putInt(Contract.KEY_NODE_COUNT, outcome.nodeCount)
            putInt(Contract.KEY_DIAGNOSTIC_COUNT, outcome.diagnosticCount)
            putLong(Contract.KEY_ELAPSED_MS, outcome.elapsedMs + (SystemClock.elapsedRealtime() - started))
        }
    }

    private fun failure(code: String, message: String) = Bundle().apply {
        putString(Contract.KEY_OUTCOME, Contract.OUTCOME_FAILED)
        putString(Contract.KEY_ERROR_CODE, code)
        putString(Contract.KEY_ERROR_MESSAGE, message.take(Contract.MAX_ERROR_MESSAGE_CHARS))
    }

    private companion object {
        @Suppress("DEPRECATION")
        fun Bundle.readValue(key: String): Any? = get(key)
    }
}
