package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class R0CompilationGateTest {

    @Test
    fun validEnvelopeIsExplicitlyRejectedUntilCompilerHostExists() {
        val decision = R0CompilationGate.reject(validAdmission())

        assertFalse(decision.accepted)
        assertEquals(
            TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
            decision.errorCode,
        )
    }

    @Test
    fun missingEnvelopeIsRejectedAsInvalidRequest() {
        val decision = R0CompilationGate.reject(
            validAdmission().copy(request = null),
        )

        assertFalse(decision.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, decision.errorCode)
    }

    @Test
    fun transportMismatchFailsBeforeCompilerAvailability() {
        val decision = R0CompilationGate.reject(
            validAdmission().withRequest {
                copy(inputArchiveTransportVersion = 999)
            },
        )

        assertFalse(decision.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH, decision.errorCode)
    }

    @Test
    fun compilerContractMismatchFailsBeforeCompilerAvailability() {
        val decision = R0CompilationGate.reject(
            validAdmission().withRequest {
                copy(contractHash = "unexpected")
            },
        )

        assertFalse(decision.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH, decision.errorCode)
    }

    @Test
    fun fileDescriptorsAndCallbackAreRequired() {
        val decision = R0CompilationGate.reject(
            validAdmission().copy(outputArchiveValid = false),
        )

        assertFalse(decision.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, decision.errorCode)
    }

    @Test
    fun inputAndOutputMustNotShareTheSameBackingFile() {
        val decision = R0CompilationGate.reject(
            validAdmission().copy(backingFilesDistinct = false),
        )

        assertFalse(decision.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, decision.errorCode)
    }

    @Test
    fun oversizedMetadataIsRejectedBeforeCompilerAvailability() {
        val decision = R0CompilationGate.reject(
            validAdmission().copy(metadataWithinBudget = false),
        )

        assertFalse(decision.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, decision.errorCode)
    }

    @Test
    fun inlineCompilerAuthorityMustBeTheExactEmptyObject() {
        listOf(
            " { } ",
            """{"plugins":[{"name":"caller-plugin"}]}""",
            """{"customTransformers":["caller-transformer.js"]}""",
        ).forEach { compilerOptionsJson ->
            val decision = R0CompilationGate.reject(
                validAdmission()
                    .withRequest { copy(compilerOptionsJson = compilerOptionsJson) }
                    .copy(inputArchiveValid = false, outputArchiveValid = false),
            )

            assertFalse(decision.accepted)
            assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, decision.errorCode)
            assertEquals(
                "compilerOptionsJson cannot establish executable compiler authority.",
                decision.errorMessage,
            )
        }
    }

    @Test
    fun declarationEntryIsNeverExecutable() {
        val decision = R0CompilationGate.reject(
            validAdmission().withRequest {
                copy(entrySourceUri = "autojs://source/project/types.d.ts")
            },
        )

        assertFalse(decision.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, decision.errorCode)
    }

    @Test
    fun mtsIsNodeOnlyInTheInitialProfileMatrix() {
        val rhinoDecision = R0CompilationGate.reject(
            validAdmission().withRequest {
                copy(entrySourceUri = "autojs://source/project/main.mts")
            },
        )
        val nodeDecision = R0CompilationGate.reject(
            validAdmission().withRequest {
                copy(
                    entrySourceUri = "autojs://source/project/main.mts",
                    targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
                )
            },
        )

        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, rhinoDecision.errorCode)
        assertEquals(TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE, nodeDecision.errorCode)
    }

    @Test
    fun tsxPassesTheEnvelopeGateOnlyForTheAdvertisedRhinoProfile() {
        val rhinoDecision = R0CompilationGate.reject(
            validAdmission().withRequest {
                copy(entrySourceUri = "autojs://source/project/main.tsx")
            },
        )
        val nodeDecision = R0CompilationGate.reject(
            validAdmission().withRequest {
                copy(
                    entrySourceUri = "autojs://source/project/main.tsx",
                    targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
                )
            },
        )

        assertEquals(TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE, rhinoDecision.errorCode)
        assertEquals(TypeScriptCompilerContract.ERROR_INVALID_REQUEST, nodeDecision.errorCode)
    }

    private fun validAdmission() = R0CompilationAdmission(
        request = R0CompilationEnvelope(
            contractVersion = TypeScriptCompilerContract.VERSION,
            contractHash = TypeScriptCompilerContract.HASH,
            requestId = "request-1",
            projectId = "project",
            entrySourceUri = "autojs://source/project/main.ts",
            targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
            compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
            compilerOptionsJson = R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON,
            inputArchiveTransportVersion =
                TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
            outputArchiveTransportVersion =
                TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        ),
        primitiveMetadataOnly = true,
        metadataWithinBudget = true,
        inputArchiveValid = true,
        outputArchiveValid = true,
        callbackValid = true,
    )

    private fun R0CompilationAdmission.withRequest(
        transform: R0CompilationEnvelope.() -> R0CompilationEnvelope,
    ) = copy(request = requireNotNull(request).transform())
}
