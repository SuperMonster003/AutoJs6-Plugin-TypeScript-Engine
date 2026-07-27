package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

data class R0CompilationEnvelope(
    val contractVersion: Int,
    val contractHash: String,
    val requestId: String,
    val projectId: String,
    val entrySourceUri: String,
    val targetProfile: String,
    val compilationMode: String,
    val compilerOptionsJson: String,
    val inputArchiveTransportVersion: Int,
    val outputArchiveTransportVersion: Int,
    val timeoutMs: Long,
)

data class R0CompilationAdmission(
    val request: R0CompilationEnvelope?,
    val primitiveMetadataOnly: Boolean,
    val metadataWithinBudget: Boolean,
    val inputArchiveValid: Boolean,
    val outputArchiveValid: Boolean,
    val callbackValid: Boolean,
    val backingFilesDistinct: Boolean = true,
)

data class R0CompilationDecision(
    val requestId: String,
    val accepted: Boolean,
    val errorCode: String,
    val errorMessage: String,
)

object R0CompilationGate {

    fun reject(admission: R0CompilationAdmission): R0CompilationDecision {
        val request = admission.request ?: return rejected(
            requestId = "",
            code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            message = "Compilation request is required and must use primitive metadata.",
        )
        if (!admission.primitiveMetadataOnly) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Compilation request contains unsupported Binder metadata.",
            )
        }
        if (!admission.metadataWithinBudget) {
            return rejected(
                requestId = request.requestId.take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Compilation request exceeds the Binder metadata budget.",
            )
        }
        if (
            request.contractVersion != TypeScriptCompilerContract.VERSION ||
            request.contractHash != TypeScriptCompilerContract.HASH
        ) {
            return rejected(
                requestId = request.requestId.take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                code = TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                message = "Compiler contract version or hash is not supported.",
            )
        }
        if (
            request.inputArchiveTransportVersion !=
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION ||
            request.outputArchiveTransportVersion !=
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION
        ) {
            return rejected(
                requestId = request.requestId.take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                code = TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                message = "Archive transport version is not supported.",
            )
        }
        if (
            request.requestId.isBlank() ||
            request.requestId.length > TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS
        ) {
            return rejected(
                requestId = request.requestId.take(TypeScriptCompilerContract.MAX_REQUEST_ID_CHARS),
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "requestId is missing or exceeds the supported length.",
            )
        }
        if (
            request.entrySourceUri.isBlank() ||
            request.entrySourceUri.length > TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS ||
            !request.entrySourceUri.startsWith("autojs://source/")
        ) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "entrySourceUri must be a bounded autojs://source/ URI.",
            )
        }
        if (
            request.projectId.isBlank() ||
            request.projectId.length > TypeScriptCompilerContract.MAX_PROJECT_ID_CHARS
        ) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "projectId is missing or exceeds the supported length.",
            )
        }
        if (request.targetProfile !in TypeScriptCompilerContract.TARGET_PROFILES) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Unknown target profile.",
            )
        }
        if (request.compilationMode !in TypeScriptCompilerContract.COMPILATION_MODES) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Unknown compilation mode.",
            )
        }
        if (
            request.compilerOptionsJson.toByteArray(Charsets.UTF_8).size >
            TypeScriptCompilerContract.MAX_COMPILER_OPTIONS_BYTES
        ) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "compilerOptionsJson exceeds the Binder metadata budget.",
            )
        }
        if (!isSupportedEntry(request.entrySourceUri, request.targetProfile)) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Entry source kind is not supported by ${request.targetProfile}.",
            )
        }
        if (
            !admission.inputArchiveValid ||
            !admission.outputArchiveValid ||
            !admission.backingFilesDistinct ||
            !admission.callbackValid
        ) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "Valid input/output file descriptors and callback are required.",
            )
        }
        if (request.timeoutMs !in 1L..TypeScriptCompilerContract.MAX_TIMEOUT_MS) {
            return rejected(
                requestId = request.requestId,
                code = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                message = "timeoutMs is outside the supported range.",
            )
        }
        return rejected(
            requestId = request.requestId,
            code = TypeScriptCompilerContract.ERROR_R0_NOT_AVAILABLE,
            message = "R1 prepares the compiler host; project compilation starts in R2.",
        )
    }

    private fun isSupportedEntry(sourceUri: String, targetProfile: String): Boolean {
        val path = sourceUri.substringBefore('?').substringBefore('#').lowercase()
        if (
            path.endsWith(".d.ts") ||
            path.endsWith(".d.mts") ||
            path.endsWith(".d.cts")
        ) {
            return false
        }
        return TypeScriptCompilerContract.supportedExtensions(targetProfile)
            .any(path::endsWith)
    }

    private fun rejected(
        requestId: String,
        code: String,
        message: String,
    ) = R0CompilationDecision(
        requestId = requestId,
        accepted = false,
        errorCode = code,
        errorMessage = message,
    )
}
