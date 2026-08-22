package io.github.supermonster003.autojs6.plugin.typescript

import java.security.MessageDigest
import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.json.JSONObject

internal object CompilerHostProtocol {

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

    fun projectResultAckRequest(
        requestId: String,
        expectedGeneration: Int,
        sequence: Int,
    ): String {
        validateRequestId(requestId)
        check(expectedGeneration > 0)
        check(sequence >= -1)
        return JSONObject()
            .put("kind", "projectResultAck")
            .put("requestId", requestId)
            .put("expectedGeneration", expectedGeneration)
            .put("sequence", sequence)
            .toString()
            .also(::requireBoundedControlMessage)
    }

    fun terminateWorkerRequest(
        requestId: String,
        expectedGeneration: Int,
    ): String {
        validateRequestId(requestId)
        check(expectedGeneration > 0)
        return JSONObject()
            .put("kind", "terminateWorker")
            .put("requestId", requestId)
            .put("expectedGeneration", expectedGeneration)
            .toString()
            .also(::requireBoundedControlMessage)
    }

    fun closeRequest(): String =
        JSONObject().put("kind", "close").toString()

    fun parseMessage(rawMessage: String): CompilerHostMessage {
        check(rawMessage.length <= MAX_MESSAGE_BYTES) {
            "Compiler host message exceeds $MAX_MESSAGE_BYTES characters."
        }
        val messageBytes = rawMessage.toByteArray(Charsets.UTF_8).size
        check(messageBytes <= MAX_MESSAGE_BYTES) {
            "Compiler host message exceeds $MAX_MESSAGE_BYTES bytes."
        }
        val strictControlMessage = R2StrictJson.parseObject(
            bytes = rawMessage.toByteArray(Charsets.UTF_8),
            maximumDepth = TypeScriptCompilerContract.MAX_JSON_DEPTH,
        )
        val message by lazy { JSONObject(rawMessage) }
        val kind =
            strictControlMessage["kind"] as? String
                ?: message.getString("kind")
        check(kind != "projectResult") {
            "Legacy single-message project results are not supported."
        }
        val framedKind = kind in FRAMED_MESSAGE_KINDS
        val generation =
            strictControlMessage
                .takeIf { framedKind }
                ?.requirePositiveInt("generation")
                ?: message.getInt("generation")
        check(generation > 0) {
            "Compiler host message has an invalid Worker generation."
        }
        val pageHasTypeScript =
            strictControlMessage
                .takeIf { framedKind }
                ?.requireBoolean("pageHasTypeScript")
                ?: message.getBoolean("pageHasTypeScript")
        check(!pageHasTypeScript) {
            "TypeScript escaped the dedicated Worker into the host page."
        }
        if (framedKind) {
            return parseFramedMessage(
                message = strictControlMessage,
                kind = kind,
                generation = generation,
            )
        }
        return when (kind) {
            "probeError",
            "projectError",
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

    fun parseFramedProjectResult(
        requestId: String,
        generation: Int,
        payload: ByteArray,
        exactSnapshot: R2ProjectInputSnapshot? = null,
    ): CompilerHostMessage.ProjectResult {
        validateRequestId(requestId)
        check(generation > 0)
        check(payload.size in 1..MAX_FRAMED_PROJECT_RESULT_BYTES) {
            "Framed project result is outside the provisional byte limit."
        }
        val strictResult = R2StrictJson.parseObject(
            bytes = payload,
            maximumDepth = TypeScriptCompilerContract.MAX_JSON_DEPTH,
        )
        validateFramedProjectResultSchema(strictResult)
        return CompilerHostMessage.ProjectResult(
            requestId = requestId,
            generation = generation,
            compilation = parseProjectCompilation(strictResult, exactSnapshot),
        )
    }

    private fun parseFramedMessage(
        message: Map<String, Any?>,
        kind: String,
        generation: Int,
    ): CompilerHostMessage {
        check(message.requireBoolean("pageHasTypeScript").not()) {
            "TypeScript escaped the dedicated Worker into the host page."
        }
        check(message.requirePositiveInt("generation") == generation) {
            "Compiler host frame generation is ambiguous."
        }
        return when (kind) {
            "projectError" -> {
                message.requireExactKeys(
                    "kind",
                    "message",
                    "requestId",
                    "generation",
                    "pageHasTypeScript",
                )
                CompilerHostMessage.Failure(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    message = message.requireString("message").boundedErrorMessage(),
                )
            }
            "projectConfigError" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "stage",
                    "outcome",
                    "errorCode",
                    "policyCode",
                    "emittedModuleCount",
                    "message",
                    "generation",
                    "pageHasTypeScript",
                )
                check(
                    message.requireString("stage") ==
                        TypeScriptCompilerContract.STAGE_CONFIG &&
                        message.requireString("outcome") ==
                        TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS &&
                        message.requireString("errorCode") ==
                        TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS &&
                        message.requireNonNegativeInt("emittedModuleCount") == 0
                ) {
                    "Controlled-config error invariants are invalid."
                }
                val policyCode = message.requireString("policyCode")
                check(policyCode in R2ControlledTsConfigPolicy.policyCodes) {
                    "Controlled-config error has an unknown policy code."
                }
                CompilerHostMessage.ProjectConfigError(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    policyCode = policyCode,
                    message = message.requireString("message").boundedErrorMessage(),
                )
            }
            "projectInputAck" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "sequence",
                    "generation",
                    "pageHasTypeScript",
                )
                CompilerHostMessage.ProjectInputAck(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    sequence = message.requireNonNegativeInt("sequence"),
                )
            }
            "projectPhase" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "phase",
                    "state",
                    "generation",
                    "pageHasTypeScript",
                )
                CompilerHostMessage.ProjectPhase(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    phase = R2ProjectExecutionPhase.fromProtocolValue(
                        message.requireString("phase"),
                    ),
                    state = R2ProjectExecutionPhaseState.fromProtocolValue(
                        message.requireString("state"),
                    ),
                )
            }
            "projectPhaseProgress" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "phase",
                    "sequence",
                    "completedUnits",
                    "totalUnits",
                    "generation",
                    "pageHasTypeScript",
                )
                val phase = R2ProjectExecutionPhase.fromProtocolValue(
                    message.requireString("phase"),
                )
                val sequence = message.requirePositiveInt("sequence")
                val completedUnits = message.requirePositiveInt("completedUnits")
                val totalUnits = message.requirePositiveInt("totalUnits")
                validateProjectPhaseProgress(
                    phase = phase,
                    sequence = sequence,
                    completedUnits = completedUnits,
                    totalUnits = totalUnits,
                )
                CompilerHostMessage.ProjectPhaseProgress(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    phase = phase,
                    sequence = sequence,
                    completedUnits = completedUnits,
                    totalUnits = totalUnits,
                )
            }
            "projectResultStart" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "byteLength",
                    "sha256",
                    "chunkCount",
                    "generation",
                    "pageHasTypeScript",
                )
                CompilerHostMessage.ProjectResultStart(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    byteLength = message.requirePositiveInt("byteLength").also { bytes ->
                        check(bytes <= MAX_FRAMED_PROJECT_RESULT_BYTES) {
                            "Framed project result exceeds the provisional byte limit."
                        }
                    },
                    sha256 = message.requireString("sha256").also { sha256 ->
                        check(SHA256_PATTERN.matches(sha256)) {
                            "Framed project result has an invalid digest."
                        }
                    },
                    chunkCount = message.requirePositiveInt("chunkCount"),
                )
            }
            "projectResultChunk" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "index",
                    "data",
                    "generation",
                    "pageHasTypeScript",
                )
                val encoded = message.requireString("data")
                check(encoded.length <= MAX_BASE64_CHUNK_CHARS) {
                    "Framed project result chunk is oversized."
                }
                CompilerHostMessage.ProjectResultChunk(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    index = message.requireNonNegativeInt("index"),
                    bytes = R2FramedBase64.decode(encoded).also { decoded ->
                        check(decoded.isNotEmpty() &&
                            decoded.size <= R2_PROJECT_FRAME_RAW_CHUNK_BYTES
                        ) {
                            "Framed project result chunk has an invalid decoded size."
                        }
                    },
                )
            }
            "projectResultEnd" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "byteLength",
                    "sha256",
                    "chunkCount",
                    "generation",
                    "pageHasTypeScript",
                )
                CompilerHostMessage.ProjectResultEnd(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    byteLength = message.requirePositiveInt("byteLength").also { bytes ->
                        check(bytes <= MAX_FRAMED_PROJECT_RESULT_BYTES) {
                            "Framed project result exceeds the provisional byte limit."
                        }
                    },
                    sha256 = message.requireString("sha256").also { sha256 ->
                        check(SHA256_PATTERN.matches(sha256)) {
                            "Framed project result has an invalid digest."
                        }
                    },
                    chunkCount = message.requirePositiveInt("chunkCount"),
                )
            }
            "workerTerminated" -> {
                message.requireExactKeys(
                    "kind",
                    "requestId",
                    "terminatedGeneration",
                    "replacementGeneration",
                    "phaseAtTermination",
                    "phaseProgressSequenceAtTermination",
                    "phaseProgressCompletedUnitsAtTermination",
                    "phaseProgressTotalUnitsAtTermination",
                    "generation",
                    "pageHasTypeScript",
                )
                val terminatedGeneration =
                    message.requirePositiveInt("terminatedGeneration")
                val replacementGeneration =
                    message.requirePositiveInt("replacementGeneration")
                check(
                    replacementGeneration == Math.addExact(terminatedGeneration, 1) &&
                        generation == replacementGeneration,
                ) {
                    "Compiler Worker termination acknowledgement has invalid generations."
                }
                val phaseAtTermination =
                    message.requireString("phaseAtTermination").let { value ->
                        if (value == "none") {
                            null
                        } else {
                            R2ProjectExecutionPhase.fromProtocolValue(value)
                        }
                    }
                val phaseProgressSequenceAtTermination =
                    message.requireNonNegativeInt(
                        "phaseProgressSequenceAtTermination",
                    )
                val phaseProgressCompletedUnitsAtTermination =
                    message.requireNonNegativeInt(
                        "phaseProgressCompletedUnitsAtTermination",
                    )
                val phaseProgressTotalUnitsAtTermination =
                    message.requireNonNegativeInt(
                        "phaseProgressTotalUnitsAtTermination",
                    )
                if (phaseAtTermination == null) {
                    check(
                        phaseProgressSequenceAtTermination == 0 &&
                            phaseProgressCompletedUnitsAtTermination == 0 &&
                            phaseProgressTotalUnitsAtTermination == 0,
                    ) {
                        "Compiler Worker termination acknowledgement has an ambiguous phase receipt."
                    }
                } else {
                    validateProjectPhaseProgress(
                        phase = phaseAtTermination,
                        sequence = phaseProgressSequenceAtTermination,
                        completedUnits =
                            phaseProgressCompletedUnitsAtTermination,
                        totalUnits = phaseProgressTotalUnitsAtTermination,
                    )
                    check(phaseProgressSequenceAtTermination == 2) {
                        "Compiler Worker termination receipt is not a quarter-progress receipt."
                    }
                }
                CompilerHostMessage.WorkerTerminated(
                    requestId = message.requireRequestId(),
                    generation = generation,
                    terminatedGeneration = terminatedGeneration,
                    replacementGeneration = replacementGeneration,
                    phaseAtTermination = phaseAtTermination,
                    phaseProgressSequenceAtTermination =
                        phaseProgressSequenceAtTermination,
                    phaseProgressCompletedUnitsAtTermination =
                        phaseProgressCompletedUnitsAtTermination,
                    phaseProgressTotalUnitsAtTermination =
                        phaseProgressTotalUnitsAtTermination,
                )
            }
            else -> error("Unsupported compiler host frame kind: $kind")
        }
    }

    private fun parseProjectCompilation(
        result: Map<String, Any?>,
        exactSnapshot: R2ProjectInputSnapshot?,
    ): R2ProjectCompilation {
        val compilerApi = result.requireString("compilerApi")
        check(compilerApi == "createIncrementalProgram") {
            "Project compiler used an unsupported TypeScript API."
        }
        val compilerVersion = result.requireString("tsVersion")
        check(compilerVersion == BuildConfig.TYPESCRIPT_VERSION) {
            "Project compiler used an unexpected TypeScript version."
        }
        val targetProfile = result.requireString("targetProfile")
        val profileSchemaRevision = result.requireProtocolInt("profileSchemaRevision")
        val profileRevision = result.requireProtocolInt("profileRevision")
        val authority =
            R2CompilationProfileRegistry.requireWorkerResultAdmittedExactContractValue(
                contractValue = targetProfile,
                profileSchemaRevision = profileSchemaRevision,
                profileRevision = profileRevision,
            )
        exactSnapshot?.let { snapshot ->
            check(snapshot.profileAuthority == authority) {
                "Project result and exact snapshot profile authorities are split-brain."
            }
        }
        val isNodeRevisionTwo =
            authority == R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        check(!isNodeRevisionTwo || exactSnapshot != null) {
            "Node revision-2 result parsing requires its exact accepted snapshot."
        }
        val configSchemaRevision = result.requireProtocolInt("configSchemaRevision")
        val configKind = result.requireString("configKind")
        val controlledConfig = configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG
        check(
            targetProfile == authority.profile.contractValue &&
                profileSchemaRevision == authority.profileSchemaRevision &&
                profileRevision == authority.profileRevision &&
                (
                    controlledConfig &&
                        authority.profile == R2ProjectProfile.PROVISIONAL_RHINO &&
                        configSchemaRevision ==
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION ||
                        !controlledConfig &&
                        configSchemaRevision == authority.configSchemaRevision &&
                        configKind == authority.configKind
                    )
        ) {
            "Project compiler returned an unsupported profile authority."
        }

        val optionsJson = result.requireObject("options")
        val effectiveOptions = R2ProjectEffectiveOptions(
            allowJs = optionsJson.requireBoolean("allowJs"),
            checkJs = optionsJson.requireBoolean("checkJs"),
            ignoreDeprecations = optionsJson.requireString("ignoreDeprecations"),
            incremental = optionsJson.requireBoolean("incremental"),
            inlineSourceMap = optionsJson.requireBoolean("inlineSourceMap"),
            inlineSources = optionsJson.requireBoolean("inlineSources"),
            module = optionsJson.requireProtocolInt("module"),
            moduleResolution = optionsJson.requireProtocolInt("moduleResolution"),
            newLine = optionsJson.requireProtocolInt("newLine"),
            noEmitOnError = optionsJson.requireBoolean("noEmitOnError"),
            noLib = optionsJson.requireBoolean("noLib"),
            outDir = optionsJson.requireString("outDir"),
            rootDir = optionsJson.requireString("rootDir"),
            skipLibCheck = optionsJson.requireBoolean("skipLibCheck"),
            sourceMap = optionsJson.requireBoolean("sourceMap"),
            strict = optionsJson.requireBoolean("strict"),
            target = optionsJson.requireProtocolInt("target"),
            types = Collections.unmodifiableList(
                optionsJson.requireList("types").mapIndexed { index, value ->
                    value as? String
                        ?: error("Project compiler option types[$index] must be a string.")
                },
            ),
        )
        val controlledEffectiveOptions =
            if (controlledConfig) {
                parseControlledEffectiveOptions(result, optionsJson, effectiveOptions)
            } else {
                check(effectiveOptions == authority.effectiveOptions) {
                    "Project compiler violated the selected provisional option contract."
                }
                null
            }
        val selectedRootNames =
            if (controlledConfig) {
                val encodedRootNames = result.requireList("rootNames")
                check(encodedRootNames.size <= R2_FILE_BACKED_MAXIMUM_FILES) {
                    "Project compiler returned too many controlled root names."
                }
                Collections.unmodifiableList(
                    encodedRootNames.mapIndexed { index, value ->
                        value as? String
                            ?: error("Project compiler rootNames[$index] must be a string.")
                    },
                )
            } else {
                emptyList()
            }

        val diagnostics = parseProjectDiagnostics(
            diagnostics = result.requireList("diagnostics"),
            profileAuthority = authority,
            exactSnapshot = exactSnapshot,
        )
        val outputs = parseProjectOutputs(
            outputs = result.requireList("outputs"),
            profileAuthority = authority,
        )
        val emitSkipped = result.requireBoolean("emitSkipped")
        val hasErrors = diagnostics.any { diagnostic ->
            diagnostic.category == TYPESCRIPT_ERROR_CATEGORY
        }
        check(!hasErrors || emitSkipped && outputs.isEmpty()) {
            "noEmitOnError returned project output for a blocking diagnostic."
        }
        check(hasErrors || !emitSkipped) {
            "Project compiler skipped an error-free emit."
        }
        if (!emitSkipped) {
            val modules = outputs.filterNot(R2ProjectOutput::isSourceMap)
            val maps = outputs.filter(R2ProjectOutput::isSourceMap)
            check(modules.isNotEmpty() && modules.size == maps.size) {
                "Project compiler did not return one Source Map per module."
            }
            check(
                modules.map { output -> output.path }.toSet().all { modulePath ->
                    maps.any { sourceMap -> sourceMap.path == "$modulePath.map" }
                },
            ) {
                "Project compiler returned an unmatched module or Source Map."
            }
            check(
                modules.all { module ->
                    module.sourcePaths.size == 1 &&
                        (
                            isNodeRevisionTwo ||
                                module.path ==
                                module.sourcePaths.single().expectedLegacyOutputPath()
                            ) &&
                        maps.single { sourceMap ->
                            sourceMap.path == "${module.path}.map"
                        }.sourcePaths == module.sourcePaths
                },
            ) {
                "Project compiler output does not map one-to-one to its source."
            }
        }
        val nodeRevisionTwoResultAuthority = if (isNodeRevisionTwo) {
            R2NodeRevisionTwoResultAuthority.derive(
                snapshot = requireNotNull(exactSnapshot),
                outputs = outputs,
            )
        } else {
            null
        }
        return R2ProjectCompilation(
            compilerApi = compilerApi,
            compilerVersion = compilerVersion,
            targetProfile = targetProfile,
            profileSchemaRevision = profileSchemaRevision,
            profileRevision = profileRevision,
            configSchemaRevision = configSchemaRevision,
            configKind = configKind,
            effectiveOptions = effectiveOptions,
            controlledEffectiveOptions = controlledEffectiveOptions,
            selectedRootNames = selectedRootNames,
            diagnostics = diagnostics,
            outputs = outputs,
            emitSkipped = emitSkipped,
            nodeRevisionTwoResultAuthority = nodeRevisionTwoResultAuthority,
        )
    }

    private fun parseControlledEffectiveOptions(
        result: Map<String, Any?>,
        options: Map<String, Any?>,
        coreOptions: R2ProjectEffectiveOptions,
    ): R2ControlledTsConfigEffectiveOptions {
        check(
            result.requireProtocolInt("effectiveOptionsRevision") ==
                R2ControlledTsConfigEffectiveOptions.REVISION
        ) {
            "Controlled effective-options revision is not supported."
        }
        val resolvedBooleans =
            R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions.keys
                .associateWith { name -> options.requireBoolean(name) }
        val resolved = R2ControlledTsConfigEffectiveOptions.resolve(resolvedBooleans)
        val explicitDefaults = R2ControlledTsConfigExplicitProfileDefaults(
            allowSyntheticDefaultImports =
                options.requireBoolean("allowSyntheticDefaultImports"),
            alwaysStrict = options.requireBoolean("alwaysStrict"),
            esModuleInterop = options.requireBoolean("esModuleInterop"),
            forceConsistentCasingInFileNames =
                options.requireBoolean("forceConsistentCasingInFileNames"),
            moduleDetection = options.requireProtocolInt("moduleDetection"),
            resolveJsonModule = options.requireBoolean("resolveJsonModule"),
            useDefineForClassFields = options.requireBoolean("useDefineForClassFields"),
        )
        check(
            resolved.compilerOptions == coreOptions &&
                resolved.explicitProfileDefaults == explicitDefaults &&
                resolved.resolvedBooleanOptions == resolvedBooleans &&
                R2Digests.equal(
                    result.requireString("effectiveOptionsFingerprint"),
                    resolved.fingerprint,
                )
        ) {
            "Project compiler violated the controlled effective-options authority."
        }
        return resolved
    }

    private fun parseProjectDiagnostics(
        diagnostics: List<Any?>,
        profileAuthority: R2CompilationProfileAuthority,
        exactSnapshot: R2ProjectInputSnapshot?,
    ): List<R2ProjectDiagnostic> {
        check(diagnostics.size <= TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT) {
            "Project compiler returned too many diagnostics."
        }
        check(
            strictJsonEncodedByteCount(diagnostics) <=
                TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES,
        ) {
            "Project compiler returned oversized diagnostics."
        }
        var relatedInformationCount = 0
        val parsedDiagnostics = diagnostics.mapIndexed { index, value ->
            val diagnostic = value.requireObject("Project diagnostic[$index]")
            val phase = R2ProjectDiagnosticPhase.fromProtocolValue(
                diagnostic.requireString("phase"),
            )
            val category = diagnostic.requireProtocolInt("category").also { category ->
                R2DiagnosticsSchemaV1.requireCategory(category)
            }
            val code = diagnostic.requireProtocolInt("code").also { code ->
                check(code > 0) {
                    "Project diagnostic has an invalid code."
                }
            }
            val nodeResolution = parseNodeResolutionDiagnosticEvidence(
                diagnostic = diagnostic,
                phase = phase,
                diagnosticCode = code,
            )
            val sourcePath = diagnostic.requireNullableString("sourcePath")
            if (sourcePath != null) {
                validateProjectDiagnosticPath(
                    path = sourcePath,
                    phase = phase,
                    profileAuthority = profileAuthority,
                    exactSnapshot = exactSnapshot,
                )
            }
            val start = diagnostic.requireNullableProtocolInt("start")
            val length = diagnostic.requireNullableProtocolInt("length")
            val line = diagnostic.requireNullableProtocolInt("line")
            val column = diagnostic.requireNullableProtocolInt("column")
            check(
                listOf(start, length, line, column).all { value -> value == null } ||
                    listOf(start, length, line, column).all { value -> value != null },
            ) {
                "Project diagnostic contains a partial source position."
            }
            if (start != null) {
                check(
                    sourcePath != null &&
                        start >= 0 &&
                        requireNotNull(length) >= 0 &&
                        requireNotNull(line) > 0 &&
                        requireNotNull(column) > 0,
                ) {
                    "Project diagnostic contains an invalid source position."
                }
            }
            val message = diagnostic.requireString("message").also { message ->
                check(message.length <= TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS) {
                    "Project diagnostic message is too long."
                }
            }
            val relatedInformation = if ("relatedInformation" in diagnostic) {
                parseProjectRelatedInformation(
                    values = diagnostic.requireList("relatedInformation"),
                    phase = phase,
                    profileAuthority = profileAuthority,
                    exactSnapshot = exactSnapshot,
                    diagnosticIndex = index,
                )
            } else {
                emptyList()
            }
            relatedInformationCount += relatedInformation.size
            check(
                relatedInformationCount <=
                    TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL,
            ) {
                "Project compiler returned too much related information."
            }
            R2ProjectDiagnostic(
                phase = phase,
                category = category,
                code = code,
                message = message,
                sourcePath = sourcePath,
                start = start,
                length = length,
                line = line,
                column = column,
                nodeResolution = nodeResolution,
                relatedInformation = relatedInformation,
            )
        }
        R2DiagnosticsSchemaV1.requireCounts(
            diagnosticCount = parsedDiagnostics.size,
            relatedInformationCount = relatedInformationCount,
        )
        return parsedDiagnostics
    }

    private fun parseProjectRelatedInformation(
        values: List<Any?>,
        phase: R2ProjectDiagnosticPhase,
        profileAuthority: R2CompilationProfileAuthority,
        exactSnapshot: R2ProjectInputSnapshot?,
        diagnosticIndex: Int,
    ): List<R2ProjectRelatedInformation> {
        check(
            values.size <= TypeScriptR2Contract.MAX_RELATED_INFORMATION_PER_DIAGNOSTIC,
        ) {
            "Project diagnostic carries too much related information."
        }
        return values.mapIndexed { relatedIndex, value ->
            val label = "Project diagnostic[$diagnosticIndex] relatedInformation[$relatedIndex]"
            val related = value.requireObject(label)
            related.requireExactPayloadKeys(
                label = label,
                "category",
                "code",
                "column",
                "length",
                "line",
                "message",
                "sourcePath",
                "start",
            )
            val category = related.requireProtocolInt("category").also { category ->
                R2DiagnosticsSchemaV1.requireCategory(category)
            }
            val code = related.requireProtocolInt("code").also { code ->
                check(code > 0) {
                    "Project related-information code is invalid."
                }
            }
            val sourcePath = related.requireNullableString("sourcePath")
            if (sourcePath != null) {
                validateProjectDiagnosticPath(
                    path = sourcePath,
                    phase = phase,
                    profileAuthority = profileAuthority,
                    exactSnapshot = exactSnapshot,
                )
            }
            val start = related.requireNullableProtocolInt("start")
            val length = related.requireNullableProtocolInt("length")
            val line = related.requireNullableProtocolInt("line")
            val column = related.requireNullableProtocolInt("column")
            check(
                listOf(start, length, line, column).all { item -> item == null } ||
                    listOf(start, length, line, column).all { item -> item != null },
            ) {
                "Project related information contains a partial source position."
            }
            if (start != null) {
                val exactLength = requireNotNull(length)
                check(
                    sourcePath != null &&
                        start >= 0 &&
                        exactLength >= 0 &&
                        start <= Int.MAX_VALUE - exactLength &&
                        requireNotNull(line) >= TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE &&
                        requireNotNull(column) >= TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE,
                ) {
                    "Project related information contains an invalid source position."
                }
            }
            val message = related.requireString("message").also { message ->
                check(message.length <= TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS) {
                    "Project related-information message is too long."
                }
            }
            R2ProjectRelatedInformation(
                category = category,
                code = code,
                message = message,
                sourcePath = sourcePath,
                start = start,
                length = length,
                line = line,
                column = column,
            )
        }
    }

    private fun parseNodeResolutionDiagnosticEvidence(
        diagnostic: Map<String, Any?>,
        phase: R2ProjectDiagnosticPhase,
        diagnosticCode: Int,
    ): R2NodeResolutionDiagnosticEvidence? {
        if (phase != R2ProjectDiagnosticPhase.RESOLUTION) {
            check("nodeResolution" !in diagnostic) {
                "Only a resolution diagnostic can carry node-resolution evidence."
            }
            return null
        }
        val encoded = diagnostic.requireObject("nodeResolution")
        encoded.requireExactPayloadKeys(
            label = "Node-resolution diagnostic evidence",
            "policyCode",
        )
        val policyValue = encoded.requireString("policyCode")
        val policyCode = R2NodeResolutionFailureCode.fromDiagnosticIdentityOrNull(
            contractValue = policyValue,
            diagnosticCode = diagnosticCode,
        ) ?: error("Node-resolution diagnostic identity is not supported.")
        return R2NodeResolutionDiagnosticEvidence(policyCode)
    }

    private fun parseProjectOutputs(
        outputs: List<Any?>,
        profileAuthority: R2CompilationProfileAuthority,
    ): List<R2ProjectOutput> {
        check(outputs.size <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES) {
            "Project compiler returned too many output files."
        }
        val paths = HashSet<String>(outputs.size)
        val foldedPaths = HashSet<String>(outputs.size)
        var totalBytes = 0L
        return outputs.mapIndexed { index, value ->
            val output = value.requireObject("Project output[$index]")
            val path = output.requireString("path")
            validateProjectOutputPath(path, profileAuthority)
            check(paths.add(path) && foldedPaths.add(path.asciiLowercase())) {
                "Project compiler returned a duplicate output path."
            }
            val text = output.requireString("text")
            val textBytes = R2Utf8.encode(text, "Project output $path")
            val maximumBytes =
                if (path.endsWith(".map")) {
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES
                } else {
                    TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES
                }
            check(textBytes.size.toLong() <= maximumBytes) {
                "Project compiler output exceeds its per-file byte limit."
            }
            totalBytes += textBytes.size
            check(
                totalBytes <= TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
            ) {
                "Project compiler output exceeds its total byte limit."
            }
            val declaredSha256 = output.requireString("sha256")
            check(
                SHA256_PATTERN.matches(declaredSha256) &&
                    declaredSha256 == textBytes.sha256(),
            ) {
                "Project compiler output digest does not match its bytes."
            }
            val sourcePathsJson = output.requireList("sourcePaths")
            check(sourcePathsJson.isNotEmpty()) {
                "Project compiler output does not identify its source."
            }
            val sourcePaths = sourcePathsJson.mapIndexed { sourceIndex, sourcePath ->
                (sourcePath as? String
                    ?: error("Project output sourcePaths[$sourceIndex] must be a string."))
                    .also { path ->
                        validateProjectEmitSourcePath(path, profileAuthority)
                    }
            }
            check(sourcePaths.distinct().size == sourcePaths.size) {
                "Project compiler output repeats a source path."
            }
            if (path.endsWith(".map")) {
                val sourceMapBytes = R2Utf8.encode(text, "Project Source Map")
                val sourceMap = try {
                    R2StrictJson.parseObject(
                        bytes = sourceMapBytes,
                        maximumDepth = TypeScriptCompilerContract.MAX_JSON_DEPTH,
                    ).also(::validateFramedSourceMapSchema)
                } finally {
                    sourceMapBytes.fill(0)
                }
                val pairedModulePath = path.removeSuffix(".map")
                val sourceRoot = sourceMap.requireString("sourceRoot")
                val mapSources = sourceMap.requireList("sources")
                val resolvedSources = mapSources.mapIndexed { sourceIndex, source ->
                    resolveSourceMapSource(
                        mapPath = path,
                        sourceRoot = sourceRoot,
                        source = source as? String
                            ?: error("Project Source Map sources[$sourceIndex] must be a string."),
                        profileAuthority = profileAuthority,
                    )
                }
                check(
                    sourceMap.requireProtocolInt("version") == 3 &&
                        sourceMap.requireString("file") ==
                        pairedModulePath.substringAfterLast('/') &&
                        sourceMap.requireString("mappings").length <=
                            MAX_PROJECT_SOURCE_MAP_MAPPINGS_CHARS &&
                        resolvedSources == sourcePaths,
                ) {
                    "Project compiler returned an invalid Source Map v3."
                }
            }
            R2ProjectOutput(
                path = path,
                text = text,
                sha256 = declaredSha256,
                sourcePaths = sourcePaths,
            )
        }
    }

    private fun validateProjectDiagnosticPath(
        path: String,
        phase: R2ProjectDiagnosticPhase,
        profileAuthority: R2CompilationProfileAuthority,
        exactSnapshot: R2ProjectInputSnapshot?,
    ) {
        val isNodeRevisionTwo =
            profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        check(
            (
                path.startsWith("/declarations/") &&
                    path.isSupportedDeclarationPath() ||
                    path.startsWith("/sources/") &&
                    R2ProjectSourceKindAdmission.isExecutableSourcePath(
                        path,
                        profileAuthority,
                    ) ||
                    phase == R2ProjectDiagnosticPhase.RESOLUTION &&
                    path.startsWith("/packages/") &&
                    path.isSupportedResolutionTargetPath(isNodeRevisionTwo)
                ) &&
                path.isCanonicalProtocolPath() &&
                (exactSnapshot == null || exactSnapshot.containsAcceptedDiagnosticPath(path)),
        ) {
            "Project compiler returned an invalid diagnostic source path."
        }
    }

    private fun validateProjectEmitSourcePath(
        path: String,
        profileAuthority: R2CompilationProfileAuthority,
    ) {
        check(
            R2ProjectSourceKindAdmission.isExecutableSourcePath(path, profileAuthority) &&
                path.isCanonicalProtocolPath(),
        ) {
            "Project compiler returned an invalid source path."
        }
    }

    private fun String.expectedLegacyOutputPath(): String =
        "/outputs/" +
            removePrefix("/sources/")
                .removeSuffix(".ts") +
            ".js"

    private fun resolveSourceMapSource(
        mapPath: String,
        sourceRoot: String,
        source: String,
        profileAuthority: R2CompilationProfileAuthority,
    ): String {
        check(
            sourceRoot.isEmpty() &&
                source.isNotEmpty() &&
                !source.startsWith('/') &&
                '\\' !in source &&
                "://" !in source,
        ) {
            "Project Source Map contains an unsupported source path."
        }
        val resolved = mapPath
            .removePrefix("/")
            .split('/')
            .dropLast(1)
            .toMutableList()
        source.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    check(resolved.isNotEmpty()) {
                        "Project Source Map source escapes the VFS."
                    }
                    resolved.removeAt(resolved.lastIndex)
                }
                else -> resolved += segment
            }
        }
        return "/${resolved.joinToString("/")}".also { path ->
            validateProjectEmitSourcePath(path, profileAuthority)
        }
    }

    private fun validateProjectOutputPath(
        path: String,
        profileAuthority: R2CompilationProfileAuthority,
    ) {
        val isNodeRevisionTwo =
            profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        check(
            path.startsWith("/outputs/") &&
                (
                    path.endsWith(".js") || path.endsWith(".js.map") ||
                        isNodeRevisionTwo &&
                        (
                            path.endsWith(".mjs") || path.endsWith(".mjs.map") ||
                                path.endsWith(".cjs") || path.endsWith(".cjs.map")
                            )
                    ) &&
                path.isCanonicalProtocolPath(),
        ) {
            "Project compiler returned an invalid output path."
        }
    }

    private fun String.isSupportedDeclarationPath(): Boolean = endsWith(".d.ts")

    private fun String.isSupportedResolutionTargetPath(isNodeRevisionTwo: Boolean): Boolean =
        endsWith(".ts") ||
            isNodeRevisionTwo &&
            (
                endsWith(".mts") || endsWith(".cts") ||
                    endsWith(".d.mts") || endsWith(".d.cts")
                )

    private fun R2ProjectInputSnapshot.containsAcceptedDiagnosticPath(path: String): Boolean {
        if (path in filePaths) return true
        val relativePath = path.removePrefix("/")
        return packageTargetInventory?.files?.any { file -> file.path == relativePath } == true ||
            nodePackageTargetV2Inventory?.files?.any { file -> file.path == relativePath } == true
    }

    private fun String.isCanonicalProtocolPath(): Boolean =
        length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS &&
            startsWith("/") &&
            '\\' !in this &&
            all { character -> character.code in 0x20..0x7e } &&
            drop(1).split('/').let { segments ->
                segments.size <= TypeScriptCompilerContract.MAX_ARCHIVE_PATH_DEPTH &&
                    segments.all { segment ->
                        segment.isNotEmpty() && segment != "." && segment != ".."
                    }
            }

    private fun String.asciiLowercase(): String = buildString(length) {
        this@asciiLowercase.forEach { character ->
            append(
                if (character in 'A'..'Z') {
                    (character.code + ASCII_CASE_OFFSET).toChar()
                } else {
                    character
                },
            )
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
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

    private fun validateFramedProjectResultSchema(result: Map<String, Any?>) {
        val configKind = result.requireString("configKind")
        when (configKind) {
            TypeScriptR2Contract.CONFIG_KIND_NONE ->
                result.requireExactPayloadKeys(
                    label = "Framed project result",
                    "compilerApi",
                    "configKind",
                    "configSchemaRevision",
                    "diagnostics",
                    "emitSkipped",
                    "options",
                    "outputs",
                    "profileRevision",
                    "profileSchemaRevision",
                    "targetProfile",
                    "tsVersion",
                )
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG ->
                result.requireExactPayloadKeys(
                    label = "Controlled framed project result",
                    "compilerApi",
                    "configKind",
                    "configSchemaRevision",
                    "diagnostics",
                    "effectiveOptionsFingerprint",
                    "effectiveOptionsRevision",
                    "emitSkipped",
                    "options",
                    "outputs",
                    "profileRevision",
                    "profileSchemaRevision",
                    "rootNames",
                    "targetProfile",
                    "tsVersion",
                )
            else -> error("Framed project result config kind is not supported.")
        }
        result.requireString("compilerApi")
        result.requireString("targetProfile")
        result.requireString("tsVersion")
        result.requireProtocolInt("configSchemaRevision")
        result.requireProtocolInt("profileRevision")
        result.requireProtocolInt("profileSchemaRevision")
        result.requireBoolean("emitSkipped")

        val options = result.requireObject("options")
        if (configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG) {
            options.requireExactPayloadKeys(
                label = "Controlled framed project options",
                *R2ControlledTsConfigEffectiveOptions.canonicalOptionKeys.toTypedArray(),
            )
            result.requireString("effectiveOptionsFingerprint")
            result.requireProtocolInt("effectiveOptionsRevision")
            result.requireList("rootNames").forEachIndexed { index, value ->
                check(value is String) {
                    "Controlled framed project rootNames[$index] must be a string."
                }
            }
        } else {
            options.requireExactPayloadKeys(
                label = "Framed project options",
                "allowJs",
                "checkJs",
                "ignoreDeprecations",
                "incremental",
                "inlineSourceMap",
                "inlineSources",
                "module",
                "moduleResolution",
                "newLine",
                "noEmitOnError",
                "noLib",
                "outDir",
                "rootDir",
                "skipLibCheck",
                "sourceMap",
                "strict",
                "target",
                "types",
            )
        }
        options.requireBoolean("allowJs")
        options.requireBoolean("checkJs")
        options.requireString("ignoreDeprecations")
        options.requireBoolean("incremental")
        options.requireBoolean("inlineSourceMap")
        options.requireBoolean("inlineSources")
        options.requireProtocolInt("module")
        options.requireProtocolInt("moduleResolution")
        options.requireProtocolInt("newLine")
        options.requireBoolean("noEmitOnError")
        options.requireBoolean("noLib")
        options.requireString("outDir")
        options.requireString("rootDir")
        options.requireBoolean("skipLibCheck")
        options.requireBoolean("sourceMap")
        options.requireBoolean("strict")
        options.requireProtocolInt("target")
        options.requireList("types").forEachIndexed { index, value ->
            check(value is String) {
                "Framed project option types[$index] must be a string."
            }
        }
        if (configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG) {
            listOf(
                "allowSyntheticDefaultImports",
                "alwaysStrict",
                "esModuleInterop",
                "exactOptionalPropertyTypes",
                "forceConsistentCasingInFileNames",
                "noFallthroughCasesInSwitch",
                "noImplicitAny",
                "noImplicitOverride",
                "noImplicitReturns",
                "noImplicitThis",
                "noPropertyAccessFromIndexSignature",
                "noUncheckedIndexedAccess",
                "noUnusedLocals",
                "noUnusedParameters",
                "resolveJsonModule",
                "strictBindCallApply",
                "strictBuiltinIteratorReturn",
                "strictFunctionTypes",
                "strictNullChecks",
                "strictPropertyInitialization",
                "useDefineForClassFields",
                "useUnknownInCatchVariables",
            ).forEach { name -> options.requireBoolean(name) }
            options.requireProtocolInt("moduleDetection")
        }

        result.requireList("diagnostics").forEachIndexed { index, value ->
            val diagnostic = value.requireObject("Framed project diagnostic[$index]")
            val phase = R2ProjectDiagnosticPhase.fromProtocolValue(
                diagnostic.requireString("phase"),
            )
            val keys = mutableListOf(
                "category",
                "code",
                "column",
                "length",
                "line",
                "message",
                "phase",
                "sourcePath",
                "start",
            )
            if (phase == R2ProjectDiagnosticPhase.RESOLUTION) {
                keys += "nodeResolution"
            }
            if ("relatedInformation" in diagnostic) {
                keys += "relatedInformation"
            }
            diagnostic.requireExactPayloadKeys(
                label = "Framed project diagnostic[$index]",
                *keys.toTypedArray(),
            )
            diagnostic.requireProtocolInt("category")
            val diagnosticCode = diagnostic.requireProtocolInt("code")
            diagnostic.requireString("message")
            diagnostic.requireNullableString("sourcePath")
            diagnostic.requireNullableProtocolInt("start")
            diagnostic.requireNullableProtocolInt("length")
            diagnostic.requireNullableProtocolInt("line")
            diagnostic.requireNullableProtocolInt("column")
            if ("relatedInformation" in diagnostic) {
                diagnostic.requireList("relatedInformation")
                    .forEachIndexed { relatedIndex, relatedValue ->
                        val label =
                            "Framed project diagnostic[$index] " +
                                "relatedInformation[$relatedIndex]"
                        relatedValue.requireObject(label).requireExactPayloadKeys(
                            label = label,
                            "category",
                            "code",
                            "column",
                            "length",
                            "line",
                            "message",
                            "sourcePath",
                            "start",
                        )
                    }
            }
            parseNodeResolutionDiagnosticEvidence(
                diagnostic = diagnostic,
                phase = phase,
                diagnosticCode = diagnosticCode,
            )
        }

        result.requireList("outputs").forEachIndexed { index, value ->
            val output = value.requireObject("Framed project output[$index]")
            output.requireExactPayloadKeys(
                label = "Framed project output[$index]",
                "path",
                "sha256",
                "sourcePaths",
                "text",
            )
            output.requireString("path")
            output.requireString("sha256")
            output.requireString("text")
            output.requireList("sourcePaths").forEachIndexed { sourceIndex, sourcePath ->
                check(sourcePath is String) {
                    "Framed project output[$index] sourcePaths[$sourceIndex] must be a string."
                }
            }
        }
    }

    private fun validateFramedSourceMapSchema(sourceMap: Map<String, Any?>) {
        sourceMap.requireExactPayloadKeys(
            label = "Project Source Map",
            "file",
            "mappings",
            "names",
            "sourceRoot",
            "sources",
            "version",
        )
        sourceMap.requireProtocolInt("version")
        sourceMap.requireString("file")
        sourceMap.requireString("sourceRoot")
        sourceMap.requireString("mappings")
        sourceMap.requireList("names").forEachIndexed { index, name ->
            check(name is String) {
                "Project Source Map names[$index] must be a string."
            }
        }
        sourceMap.requireList("sources").forEachIndexed { index, source ->
            check(source is String) {
                "Project Source Map sources[$index] must be a string."
            }
        }
    }

    private fun requireBoundedControlMessage(message: String) {
        check(message.toByteArray(Charsets.UTF_8).size <= MAX_MESSAGE_BYTES) {
            "Compiler host control message exceeds $MAX_MESSAGE_BYTES bytes."
        }
    }

    private fun Map<String, Any?>.requireExactKeys(vararg expectedKeys: String) {
        check(keys == expectedKeys.toSet()) {
            "Compiler host frame contains missing or unknown fields."
        }
        check(requireString("kind") in FRAMED_MESSAGE_KINDS) {
            "Compiler host frame kind is invalid."
        }
    }

    private fun Map<String, Any?>.requireExactPayloadKeys(
        label: String,
        vararg expectedKeys: String,
    ) {
        check(keys == expectedKeys.toSet()) {
            "$label contains missing or unknown fields."
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        get(key) as? Map<String, Any?>
            ?: error("Compiler payload field '$key' must be an object.")

    @Suppress("UNCHECKED_CAST")
    private fun Any?.requireObject(label: String): Map<String, Any?> =
        this as? Map<String, Any?>
            ?: error("$label must be an object.")

    private fun Map<String, Any?>.requireList(key: String): List<Any?> =
        get(key) as? List<*>
            ?: error("Compiler payload field '$key' must be an array.")

    private fun Map<String, Any?>.requireNullableString(key: String): String? =
        get(key)?.let { value ->
            value as? String
                ?: error("Compiler payload field '$key' must be null or a string.")
        }

    private fun Map<String, Any?>.requireNullableProtocolInt(key: String): Int? =
        if (get(key) == null) null else requireProtocolInt(key)

    private fun strictJsonEncodedByteCount(value: Any?): Long =
        when (value) {
            null -> 4L
            is Boolean -> if (value) 4L else 5L
            is R2StrictJson.NumberToken -> value.source.length.toLong()
            is String -> {
                var bytes = Math.addExact(
                    2L,
                    R2Utf8.encodedByteCount(value, "Compiler payload string"),
                )
                value.forEach { character ->
                    bytes = when (character) {
                        '"',
                        '\\',
                        '\b',
                        '\u000c',
                        '\n',
                        '\r',
                        '\t',
                        -> Math.addExact(bytes, 1L)
                        else ->
                            if (character.code < 0x20) {
                                Math.addExact(bytes, 5L)
                            } else {
                                bytes
                            }
                    }
                }
                bytes
            }
            is List<*> -> value.foldIndexed(2L) { index, bytes, item ->
                Math.addExact(
                    Math.addExact(bytes, if (index == 0) 0L else 1L),
                    strictJsonEncodedByteCount(item),
                )
            }
            is Map<*, *> -> value.entries.foldIndexed(2L) { index, bytes, entry ->
                val key = entry.key as? String
                    ?: error("Compiler payload object key must be a string.")
                Math.addExact(
                    Math.addExact(
                        Math.addExact(
                            Math.addExact(bytes, if (index == 0) 0L else 1L),
                            strictJsonEncodedByteCount(key),
                        ),
                        1L,
                    ),
                    strictJsonEncodedByteCount(entry.value),
                )
            }
            else -> error("Compiler payload contains an unsupported JSON value.")
        }

    private fun Map<String, Any?>.requireRequestId(): String =
        requireString("requestId").also(::validateRequestId)

    private fun Map<String, Any?>.requireString(key: String): String =
        get(key) as? String
            ?: error("Compiler host frame field '$key' must be a string.")

    private fun Map<String, Any?>.requireBoolean(key: String): Boolean =
        get(key) as? Boolean
            ?: error("Compiler host frame field '$key' must be a boolean.")

    private fun Map<String, Any?>.requirePositiveInt(key: String): Int =
        requireProtocolInt(key).also { value ->
            check(value > 0) {
                "Compiler host frame field '$key' must be positive."
            }
        }

    private fun Map<String, Any?>.requireNonNegativeInt(key: String): Int =
        requireProtocolInt(key).also { value ->
            check(value >= 0) {
                "Compiler host frame field '$key' must be non-negative."
            }
        }

    private fun validateProjectPhaseProgress(
        phase: R2ProjectExecutionPhase,
        sequence: Int,
        completedUnits: Int,
        totalUnits: Int,
    ) {
        check(phase != R2ProjectExecutionPhase.INGRESS) {
            "Project ingress must not report synchronous-work progress."
        }
        check(sequence in 1..2) {
            "Project execution progress sequence is outside the bounded prefix."
        }
        check(totalUnits in MIN_PROJECT_PHASE_PROGRESS_UNITS..MAX_PROJECT_PHASE_PROGRESS_UNITS) {
            "Project execution progress total is outside the internal bound."
        }
        val divisor = if (sequence == 1) 8 else 4
        val expectedCompletedUnits = (totalUnits - 1) / divisor + 1
        check(
            completedUnits == expectedCompletedUnits &&
                completedUnits < totalUnits,
        ) {
            "Project execution progress does not match its fixed threshold."
        }
    }

    private fun Map<String, Any?>.requireProtocolInt(key: String): Int {
        val token = get(key) as? R2StrictJson.NumberToken
            ?: error("Compiler host frame field '$key' must be an integer.")
        check(NON_NEGATIVE_INTEGER_PATTERN.matches(token.source)) {
            "Compiler host frame field '$key' must be a canonical non-negative integer."
        }
        return token.source.toIntOrNull()
            ?: error("Compiler host frame field '$key' exceeds the integer range.")
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
    private const val MAX_MESSAGE_BYTES = R2_PROJECT_MAXIMUM_FRAME_BYTES
    private const val MAX_FRAMED_PROJECT_RESULT_BYTES = 6 * 1024 * 1024
    private const val MAX_PROJECT_SOURCE_MAP_MAPPINGS_CHARS = 512 * 1024
    private const val MAX_REQUEST_ID_CHARS = 128
    private const val MIN_PROJECT_PHASE_PROGRESS_UNITS = 8
    private const val MAX_PROJECT_PHASE_PROGRESS_UNITS = 2 * 4_607
    private const val MAX_BASE64_CHUNK_CHARS =
        (R2_PROJECT_FRAME_RAW_CHUNK_BYTES + 2) / 3 * 4
    private const val MAX_SOURCE_MAP_MAPPINGS_CHARS = 16 * 1024
    private const val TYPESCRIPT_ERROR_CATEGORY =
        TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR
    private const val ASCII_CASE_OFFSET = 'a'.code - 'A'.code
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    private val NON_NEGATIVE_INTEGER_PATTERN = Regex("0|[1-9][0-9]*")
    private val FRAMED_MESSAGE_KINDS = setOf(
        "projectConfigError",
        "projectError",
        "projectInputAck",
        "projectPhase",
        "projectPhaseProgress",
        "projectResultStart",
        "projectResultChunk",
        "projectResultEnd",
        "workerTerminated",
    )
}

internal sealed interface CompilerHostMessage {

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

    data class ProjectResult(
        val requestId: String,
        override val generation: Int,
        val compilation: R2ProjectCompilation,
    ) : CompilerHostMessage

    data class ProjectInputAck(
        val requestId: String,
        override val generation: Int,
        val sequence: Int,
    ) : CompilerHostMessage

    data class ProjectConfigError(
        val requestId: String,
        override val generation: Int,
        val policyCode: String,
        val message: String,
    ) : CompilerHostMessage

    data class ProjectPhase(
        val requestId: String,
        override val generation: Int,
        val phase: R2ProjectExecutionPhase,
        val state: R2ProjectExecutionPhaseState,
    ) : CompilerHostMessage

    data class ProjectPhaseProgress(
        val requestId: String,
        override val generation: Int,
        val phase: R2ProjectExecutionPhase,
        val sequence: Int,
        val completedUnits: Int,
        val totalUnits: Int,
    ) : CompilerHostMessage

    data class ProjectResultStart(
        val requestId: String,
        override val generation: Int,
        val byteLength: Int,
        val sha256: String,
        val chunkCount: Int,
    ) : CompilerHostMessage

    data class ProjectResultChunk(
        val requestId: String,
        override val generation: Int,
        val index: Int,
        val bytes: ByteArray,
    ) : CompilerHostMessage

    data class ProjectResultEnd(
        val requestId: String,
        override val generation: Int,
        val byteLength: Int,
        val sha256: String,
        val chunkCount: Int,
    ) : CompilerHostMessage

    data class WorkerTerminated(
        val requestId: String,
        override val generation: Int,
        val terminatedGeneration: Int,
        val replacementGeneration: Int,
        val phaseAtTermination: R2ProjectExecutionPhase?,
        val phaseProgressSequenceAtTermination: Int,
        val phaseProgressCompletedUnitsAtTermination: Int,
        val phaseProgressTotalUnitsAtTermination: Int,
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
