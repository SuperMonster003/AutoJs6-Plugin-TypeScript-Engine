package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompilerPrewarmGateTest {

    @Test
    fun validPrewarmIsAdmittedToTheR1Host() {
        val rejection = CompilerPrewarmGate.validate(validAdmission())

        assertNull(rejection)
    }

    @Test
    fun contractMismatchIsRejectedBeforeAvailability() {
        val rejection = CompilerPrewarmGate.validate(
            validAdmission().copy(
                options = validOptions().copy(contractHash = "unexpected"),
            ),
        )

        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            rejection?.errorCode,
        )
    }

    @Test
    fun nonPrimitiveOrOversizedMetadataIsRejected() {
        val nonPrimitive = CompilerPrewarmGate.validate(
            validAdmission().copy(primitiveMetadataOnly = false),
        )
        val oversized = CompilerPrewarmGate.validate(
            validAdmission().copy(metadataWithinBudget = false),
        )

        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            nonPrimitive?.errorCode,
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            oversized?.errorCode,
        )
    }

    private fun validAdmission() = CompilerPrewarmAdmission(
        options = validOptions(),
        primitiveMetadataOnly = true,
        metadataWithinBudget = true,
    )

    private fun validOptions() = CompilerPrewarmEnvelope(
        contractVersion = TypeScriptCompilerContract.VERSION,
        contractHash = TypeScriptCompilerContract.HASH,
        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
    )
}
