package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

data class CompilerPrewarmEnvelope(
    val contractVersion: Int,
    val contractHash: String,
    val timeoutMs: Long,
)

data class CompilerPrewarmAdmission(
    val options: CompilerPrewarmEnvelope?,
    val primitiveMetadataOnly: Boolean,
    val metadataWithinBudget: Boolean,
)

data class CompilerPrewarmRejection(
    val errorCode: String,
    val errorMessage: String,
)

object CompilerPrewarmGate {

    fun validate(admission: CompilerPrewarmAdmission): CompilerPrewarmRejection? {
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
        return null
    }

    private fun rejected(
        code: String,
        message: String,
    ) = CompilerPrewarmRejection(
        errorCode = code,
        errorMessage = message,
    )
}
