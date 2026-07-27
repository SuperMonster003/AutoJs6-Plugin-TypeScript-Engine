package io.github.supermonster003.autojs6.plugin.typescript

import org.json.JSONObject

object CompilerHostProtocol {

    fun probeRequest(requestId: String): String {
        check(requestId.isNotEmpty() && requestId.length <= MAX_REQUEST_ID_CHARS)
        return JSONObject()
            .put("kind", "probe")
            .put("requestId", requestId)
            .put(
                "vfs",
                JSONObject()
                    .put("enumSourceText", ENUM_SOURCE_TEXT)
                    .put("enumSourceUri", ENUM_SOURCE_URI)
                    .put("syntaxSourceText", SYNTAX_SOURCE_TEXT)
                    .put("syntaxSourceUri", SYNTAX_SOURCE_URI),
            )
            .toString()
    }

    fun restartWorkerRequest(): String =
        JSONObject().put("kind", "restartWorker").toString()

    fun closeRequest(): String =
        JSONObject().put("kind", "close").toString()

    fun parseMessage(rawMessage: String): CompilerHostMessage {
        check(rawMessage.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES) {
            "Compiler host message exceeds $MAX_MESSAGE_BYTES bytes."
        }
        val message = JSONObject(rawMessage)
        val generation = message.getInt("generation")
        check(generation > 0) {
            "Compiler host message has an invalid Worker generation."
        }
        check(!message.getBoolean("pageHasTypeScript")) {
            "TypeScript escaped the dedicated Worker into the host page."
        }
        return when (val kind = message.getString("kind")) {
            "probeError",
            "workerError",
            -> CompilerHostMessage.Failure(
                requestId = message.optString("requestId").takeIf(String::isNotEmpty),
                generation = generation,
                message = message.getString("message").boundedErrorMessage(),
            )
            "probeResult" -> CompilerHostMessage.ProbeResult(
                requestId = message.getString("requestId").also(::validateRequestId),
                generation = generation,
                evidence = parseProbeEvidence(message.getJSONObject("result")),
            )
            "ready" -> CompilerHostMessage.Ready(
                generation = generation,
                compilerVersion = message.getString("tsVersion"),
            )
            else -> error("Unsupported compiler host message kind: $kind")
        }
    }

    private fun parseProbeEvidence(result: JSONObject): CompilerProbeEvidence {
        val emittedJavaScript = result.getJSONObject("emittedJavaScript")
        val diagnostic = result.getJSONObject("diagnostic")
        val scope = result.getJSONObject("scope")
        val sourceMap = result.getJSONObject("sourceMap")
        val mappings = sourceMap.getString("mappings")

        val evidence = CompilerProbeEvidence(
            compilerVersion = result.getString("tsVersion"),
            diagnosticCategory = diagnostic.getInt("category"),
            diagnosticCode = diagnostic.getInt("code"),
            diagnosticColumn = diagnostic.getInt("column"),
            diagnosticLength = diagnostic.getInt("length"),
            diagnosticLine = diagnostic.getInt("line"),
            diagnosticMessage = diagnostic.getString("message"),
            diagnosticSourceUri = diagnostic.getString("sourceUri"),
            diagnosticStart = diagnostic.getInt("start"),
            emittedEnumRuntimeAssignment =
                emittedJavaScript.getBoolean("enumRuntimeAssignmentPresent"),
            emittedJavaScriptParseDiagnosticCount =
                emittedJavaScript.getInt("parseDiagnosticCount"),
            emittedJavaScriptSha256 = emittedJavaScript.getString("sha256"),
            sourceMapHasInlineSources = sourceMap.getBoolean("hasInlineSources"),
            sourceMapMappings = mappings,
            sourceMapSha256 = sourceMap.getString("sha256"),
            sourceMapVersion = sourceMap.getInt("version"),
            syntaxEmitSha256 = result.getString("syntaxEmitSha256"),
            syntaxMapSha256 = result.getString("syntaxMapSha256"),
            workerDocumentAbsent = scope.getBoolean("documentAbsent"),
            workerNativeBridgeAbsent = scope.getBoolean("nativeBridgeAbsent"),
            workerWindowAbsent = scope.getBoolean("windowAbsent"),
            workerGlobal = scope.getBoolean("workerGlobal"),
        )
        evidence.verifyGolden()
        return evidence
    }

    private fun CompilerProbeEvidence.verifyGolden() {
        check(compilerVersion == BuildConfig.TYPESCRIPT_VERSION)
        check(diagnosticCategory == 1)
        check(diagnosticCode == 1110)
        check(diagnosticColumn == 15)
        check(diagnosticLength == 1)
        check(diagnosticLine == 1)
        check(diagnosticMessage == "Type expected.")
        check(diagnosticSourceUri == SYNTAX_SOURCE_URI)
        check(diagnosticStart == 14)
        check(emittedEnumRuntimeAssignment)
        check(emittedJavaScriptParseDiagnosticCount == 0)
        check(emittedJavaScriptSha256 == ENUM_JAVASCRIPT_SHA256)
        check(sourceMapVersion == 3)
        check(sourceMapMappings.isNotEmpty())
        check(sourceMapMappings.length <= MAX_SOURCE_MAP_MAPPINGS_CHARS)
        check(!sourceMapHasInlineSources)
        check(sourceMapSha256 == ENUM_SOURCE_MAP_SHA256)
        check(syntaxEmitSha256 == SYNTAX_JAVASCRIPT_SHA256)
        check(syntaxMapSha256 == SYNTAX_SOURCE_MAP_SHA256)
        check(workerGlobal)
        check(workerDocumentAbsent)
        check(workerNativeBridgeAbsent)
        check(workerWindowAbsent)
    }

    private fun validateRequestId(requestId: String) {
        check(requestId.isNotEmpty() && requestId.length <= MAX_REQUEST_ID_CHARS) {
            "Compiler host message has an invalid request ID."
        }
    }

    private fun String.boundedErrorMessage(): String {
        check(length <= MAX_ERROR_MESSAGE_CHARS) {
            "Compiler host error message is too long."
        }
        return this
    }

    const val ENUM_JAVASCRIPT_SHA256 =
        "75c017bb059e4e246ed6db84c9ea19121df3e0473c428d4aa38e4d2fee1b51a9"
    const val ENUM_SOURCE_MAP_SHA256 =
        "f6b14de7b69b6cf5e52b247f6ab3f8c0625cc7070fb2a81d05b16e06331fb02f"
    const val SYNTAX_JAVASCRIPT_SHA256 =
        "6b57a203bc8cd6b27e7b8383c924ba13cb9365de40637f31a8a839385111ff75"
    const val SYNTAX_SOURCE_MAP_SHA256 =
        "1f5041e240885a38ac5ffcb0f88cae4c149d875805361a30f3a21078c8c38fea"

    private const val ENUM_SOURCE_TEXT =
        "enum Direction { Up, Down }\n" +
            "(self as any).__r1ProbeValue = Direction.Down;\n"
    private const val ENUM_SOURCE_URI = "autojs://source/r1-probe/enum.ts"
    private const val SYNTAX_SOURCE_TEXT = "const broken: = 1;\n"
    private const val SYNTAX_SOURCE_URI =
        "autojs://source/r1-probe/syntax-error.ts"
    private const val MAX_ERROR_MESSAGE_CHARS = 4_096
    private const val MAX_MESSAGE_BYTES = 32 * 1024
    private const val MAX_REQUEST_ID_CHARS = 128
    private const val MAX_SOURCE_MAP_MAPPINGS_CHARS = 16 * 1024
}

sealed interface CompilerHostMessage {

    val generation: Int

    data class Ready(
        override val generation: Int,
        val compilerVersion: String,
    ) : CompilerHostMessage

    data class ProbeResult(
        val requestId: String,
        override val generation: Int,
        val evidence: CompilerProbeEvidence,
    ) : CompilerHostMessage

    data class Failure(
        val requestId: String?,
        override val generation: Int,
        val message: String,
    ) : CompilerHostMessage
}

data class CompilerProbeEvidence(
    val compilerVersion: String,
    val diagnosticCategory: Int,
    val diagnosticCode: Int,
    val diagnosticColumn: Int,
    val diagnosticLength: Int,
    val diagnosticLine: Int,
    val diagnosticMessage: String,
    val diagnosticSourceUri: String,
    val diagnosticStart: Int,
    val emittedEnumRuntimeAssignment: Boolean,
    val emittedJavaScriptParseDiagnosticCount: Int,
    val emittedJavaScriptSha256: String,
    val sourceMapHasInlineSources: Boolean,
    val sourceMapMappings: String,
    val sourceMapSha256: String,
    val sourceMapVersion: Int,
    val syntaxEmitSha256: String,
    val syntaxMapSha256: String,
    val workerDocumentAbsent: Boolean,
    val workerNativeBridgeAbsent: Boolean,
    val workerWindowAbsent: Boolean,
    val workerGlobal: Boolean,
)
