package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Test

class R0PrewarmGateTest {

    @Test
    fun validPrewarmIsExplicitlyUnavailableInR0() {
        val decision = R0PrewarmGate.reject(validAdmission())

        assertEquals(TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE, decision.errorCode)
    }

    @Test
    fun contractMismatchIsRejectedBeforeAvailability() {
        val decision = R0PrewarmGate.reject(
            validAdmission().copy(
                options = validOptions().copy(contractHash = "unexpected"),
            ),
        )

        assertEquals(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH, decision.errorCode)
    }

    @Test
    fun nonPrimitiveOrOversizedMetadataIsRejected() {
        val nonPrimitive = R0PrewarmGate.reject(
            validAdmission().copy(primitiveMetadataOnly = false),
        )
        val oversized = R0PrewarmGate.reject(
            validAdmission().copy(metadataWithinBudget = false),
        )

        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, nonPrimitive.errorCode)
        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, oversized.errorCode)
    }

    private fun validAdmission() = R0PrewarmAdmission(
        options = validOptions(),
        primitiveMetadataOnly = true,
        metadataWithinBudget = true,
    )

    private fun validOptions() = R0PrewarmEnvelope(
        contractVersion = TypeScriptCompilerContract.VERSION,
        contractHash = TypeScriptCompilerContract.HASH,
        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
    )
}
