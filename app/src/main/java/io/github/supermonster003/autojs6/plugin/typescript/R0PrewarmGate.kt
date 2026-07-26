package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

data class R0PrewarmEnvelope(
    val contractVersion: Int,
    val contractHash: String,
    val timeoutMs: Long,
)

data class R0PrewarmAdmission(
    val options: R0PrewarmEnvelope?,
    val primitiveMetadataOnly: Boolean,
    val metadataWithinBudget: Boolean,
)

data class R0PrewarmDecision(
    val errorCode: String,
    val errorMessage: String,
)

object R0PrewarmGate {

    fun reject(admission: R0PrewarmAdmission): R0PrewarmDecision {
        val options = admission.options ?: return rejected(
            code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            message = "Prewarm options are required.",
        )
        if (!admission.primitiveMetadataOnly || !admission.metadataWithinBudget) {
            return rejected(
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Prewarm options contain unsupported or oversized Binder metadata.",
            )
        }
        if (
            options.contractVersion != TypeScriptCompilerContract.VERSION ||
            options.contractHash != TypeScriptCompilerContract.HASH
        ) {
            return rejected(
                code = TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                message = "Compiler contract version or hash is not supported.",
            )
        }
        if (options.timeoutMs !in 1L..TypeScriptCompilerContract.MAX_TIMEOUT_MS) {
            return rejected(
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "timeoutMs is outside the supported range.",
            )
        }
        return rejected(
            code = TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
            message = "R0 exposes compiler discovery but has no compiler host to prewarm.",
        )
    }

    private fun rejected(
        code: String,
        message: String,
    ) = R0PrewarmDecision(
        errorCode = code,
        errorMessage = message,
    )
}
