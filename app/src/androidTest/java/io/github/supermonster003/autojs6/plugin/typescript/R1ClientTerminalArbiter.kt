package io.github.supermonster003.autojs6.plugin.typescript

import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Representative client-side exactly-once arbiter for the R1 service-death instrumentation.
 *
 * AutoJs6 host integration is deliberately outside R1. This test-scoped helper nevertheless
 * exercises the rule that a remote terminal callback and a local Binder-death synthesis compete
 * for one terminal slot, with request correlation checked before a remote result can win.
 */
internal class R1ClientTerminalArbiter(
    private val requestId: String,
) {

    private val terminal = AtomicReference<Terminal>()
    private val terminalSignal = CountDownLatch(1)
    val terminalCount = AtomicInteger()

    fun acceptRemote(result: Bundle?): Boolean {
        val copy = result?.let(::Bundle) ?: return false
        if (
            copy.getString(TypeScriptCompilerContract.KEY_REQUEST_ID) !=
            requestId
        ) {
            return false
        }
        return complete(
            Terminal(
                origin = Origin.REMOTE_CALLBACK,
                result = copy,
            ),
        )
    }

    fun acceptServiceDeath(): Boolean = complete(
        Terminal(
            origin = Origin.SERVICE_DEATH,
            result = Bundle().apply {
                putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
                putString(
                    TypeScriptCompilerContract.KEY_OUTCOME,
                    TypeScriptCompilerContract.OUTCOME_FAILED,
                )
                putBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, false)
                putString(
                    TypeScriptCompilerContract.KEY_ERROR_CODE,
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                )
                putString(
                    TypeScriptCompilerContract.KEY_ERROR_MESSAGE,
                    "Compiler service Binder died before a remote terminal callback.",
                )
                putInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, 0)
                putInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, 0)
                putLong(TypeScriptCompilerContract.KEY_ELAPSED_MS, 0L)
            },
        ),
    )

    fun awaitTerminal(timeout: Long, unit: TimeUnit): Terminal {
        if (!terminalSignal.await(timeout, unit)) {
            throw AssertionError("Timed out waiting for the client terminal arbiter.")
        }
        return requireNotNull(terminal.get())
    }

    private fun complete(candidate: Terminal): Boolean {
        if (!terminal.compareAndSet(null, candidate)) return false
        terminalCount.incrementAndGet()
        terminalSignal.countDown()
        return true
    }

    internal data class Terminal(
        val origin: Origin,
        val result: Bundle,
    )

    internal enum class Origin {
        REMOTE_CALLBACK,
        SERVICE_DEATH,
    }
}
