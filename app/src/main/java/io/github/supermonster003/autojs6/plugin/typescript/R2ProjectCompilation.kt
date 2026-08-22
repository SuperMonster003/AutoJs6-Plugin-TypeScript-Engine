package io.github.supermonster003.autojs6.plugin.typescript

import java.util.Collections
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

private fun <T> immutableListCopy(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private const val TYPESCRIPT_ERROR_CATEGORY =
    TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR

internal enum class R2ProjectDiagnosticPhase(
    val protocolValue: String,
) {
    OPTIONS("options"),
    SYNTACTIC("syntactic"),
    GLOBAL("global"),
    RESOLUTION("resolution"),
    SEMANTIC("semantic"),
    EMIT("emit"),
    ;

    companion object {
        fun fromProtocolValue(value: String): R2ProjectDiagnosticPhase =
            entries.singleOrNull { phase -> phase.protocolValue == value }
                ?: error("Unsupported project diagnostic phase: $value")
    }
}

internal data class R2NodeResolutionDiagnosticEvidence(
    val policyCode: R2NodeResolutionFailureCode,
)

internal data class R2ProjectRelatedInformation(
    val category: Int,
    val code: Int,
    val message: String,
    val sourcePath: String?,
    val start: Int?,
    val length: Int?,
    val line: Int?,
    val column: Int?,
) {

    init {
        R2DiagnosticsSchemaV1.requireCategory(category)
        require(code > 0) {
            "Project related-information code is invalid."
        }
        require(message.length <= TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS) {
            "Project related-information message is too long."
        }
        requireValidDiagnosticLocation(
            label = "Project related information",
            sourcePath = sourcePath,
            start = start,
            length = length,
            line = line,
            column = column,
        )
    }
}

internal data class R2ProjectDiagnostic(
    val phase: R2ProjectDiagnosticPhase,
    val category: Int,
    val code: Int,
    val message: String,
    val sourcePath: String?,
    val start: Int?,
    val length: Int?,
    val line: Int?,
    val column: Int?,
    val nodeResolution: R2NodeResolutionDiagnosticEvidence? = null,
    val relatedInformation: List<R2ProjectRelatedInformation> = emptyList(),
) {

    init {
        R2DiagnosticsSchemaV1.requireCategory(category)
        require(code > 0) {
            "Project diagnostic code is invalid."
        }
        require(message.length <= TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS) {
            "Project diagnostic message is too long."
        }
        require(
            relatedInformation.size <=
                TypeScriptR2Contract.MAX_RELATED_INFORMATION_PER_DIAGNOSTIC,
        ) {
            "Project diagnostic carries too many related-information records."
        }
        requireValidDiagnosticLocation(
            label = "Project diagnostic",
            sourcePath = sourcePath,
            start = start,
            length = length,
            line = line,
            column = column,
        )
        val isResolution = phase == R2ProjectDiagnosticPhase.RESOLUTION
        require(isResolution == (nodeResolution != null)) {
            "Only a resolution diagnostic can carry node-resolution evidence."
        }
        if (isResolution) {
            val evidence = requireNotNull(nodeResolution)
            require(
                category == TYPESCRIPT_ERROR_CATEGORY &&
                    code == evidence.policyCode.diagnosticCode &&
                    start == null &&
                    length == null &&
                    line == null &&
                    column == null
            ) {
                "Resolution diagnostic identity or source range is invalid."
            }
        }
    }
}

private fun requireValidDiagnosticLocation(
    label: String,
    sourcePath: String?,
    start: Int?,
    length: Int?,
    line: Int?,
    column: Int?,
) {
    val position = listOf(start, length, line, column)
    require(
        position.all { value -> value == null } ||
            position.all { value -> value != null },
    ) {
        "$label contains a partial source position."
    }
    if (start != null) {
        val exactLength = requireNotNull(length)
        require(
            sourcePath != null &&
                start >= 0 &&
                exactLength >= 0 &&
                start <= Int.MAX_VALUE - exactLength &&
                requireNotNull(line) >= TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE &&
                requireNotNull(column) >= TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE,
        ) {
            "$label contains an invalid source position."
        }
    }
}

internal class R2ProjectOutput(
    val path: String,
    val text: String,
    val sha256: String,
    sourcePaths: List<String>,
) {
    val sourcePaths: List<String> = immutableListCopy(sourcePaths)

    val isSourceMap: Boolean
        get() = path.endsWith(".map")
}

internal data class R2ProjectEffectiveOptions(
    val allowJs: Boolean,
    val checkJs: Boolean,
    val ignoreDeprecations: String,
    val incremental: Boolean,
    val inlineSourceMap: Boolean,
    val inlineSources: Boolean,
    val module: Int,
    val moduleResolution: Int,
    val newLine: Int,
    val noEmitOnError: Boolean,
    val noLib: Boolean,
    val outDir: String,
    val rootDir: String,
    val skipLibCheck: Boolean,
    val sourceMap: Boolean,
    val strict: Boolean,
    val target: Int,
    val types: List<String>,
) {

    companion object {
        val PROVISIONAL_RHINO = R2ProjectEffectiveOptions(
            allowJs = false,
            checkJs = false,
            ignoreDeprecations = "6.0",
            incremental = false,
            inlineSourceMap = false,
            inlineSources = false,
            module = 1,
            moduleResolution = 2,
            newLine = 1,
            noEmitOnError = true,
            noLib = false,
            outDir = "/outputs",
            rootDir = "/sources",
            skipLibCheck = false,
            sourceMap = true,
            strict = true,
            target = 5,
            types = emptyList(),
        )

        val PROVISIONAL_NODE_NEXT = PROVISIONAL_RHINO.copy(
            module = 199,
            moduleResolution = 99,
        )
    }
}

internal class R2ProjectCompilation(
    val compilerApi: String,
    val compilerVersion: String,
    val targetProfile: String,
    val profileSchemaRevision: Int,
    val profileRevision: Int,
    val configSchemaRevision: Int,
    val configKind: String,
    val effectiveOptions: R2ProjectEffectiveOptions,
    controlledEffectiveOptions: R2ControlledTsConfigEffectiveOptions? = null,
    selectedRootNames: List<String> = emptyList(),
    diagnostics: List<R2ProjectDiagnostic>,
    outputs: List<R2ProjectOutput>,
    val emitSkipped: Boolean,
    val nodeRevisionTwoResultAuthority: R2NodeRevisionTwoResultAuthority? = null,
) {
    val controlledEffectiveOptions: R2ControlledTsConfigEffectiveOptions? =
        controlledEffectiveOptions
    val selectedRootNames: List<String> = immutableListCopy(selectedRootNames)
    val diagnostics: List<R2ProjectDiagnostic> = immutableListCopy(
        diagnostics.map { diagnostic ->
            diagnostic.copy(
                relatedInformation = immutableListCopy(diagnostic.relatedInformation),
            )
        },
    )
    val outputs: List<R2ProjectOutput> = immutableListCopy(outputs)

    val executableModules: List<R2ProjectOutput> =
        immutableListCopy(this.outputs.filterNot(R2ProjectOutput::isSourceMap))

    val sourceMaps: List<R2ProjectOutput> =
        immutableListCopy(this.outputs.filter(R2ProjectOutput::isSourceMap))

    val generatedModuleMappings: List<R2GeneratedModuleMapping> =
        nodeRevisionTwoResultAuthority?.generatedModuleMappings ?: emptyList()

    init {
        R2DiagnosticsSchemaV1.requireCounts(
            diagnosticCount = this.diagnostics.size,
            relatedInformationCount = this.diagnostics.sumOf { diagnostic ->
                diagnostic.relatedInformation.size
            },
        )
        val exactProfileAuthority = R2CompilationProfileRegistry.requireExactContractValue(
            contractValue = targetProfile,
            profileSchemaRevision = profileSchemaRevision,
            profileRevision = profileRevision,
        )
        val isNodeRevisionTwo = exactProfileAuthority ==
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        require(isNodeRevisionTwo == (nodeRevisionTwoResultAuthority != null)) {
            "Only an exact Node revision-2 compilation can carry its native result authority."
        }
        nodeRevisionTwoResultAuthority?.let { resultAuthority ->
            require(resultAuthority.profileAuthority == exactProfileAuthority) {
                "Node revision-2 compilation and native result authorities are split-brain."
            }
        }
        when (configKind) {
            TypeScriptR2Contract.CONFIG_KIND_NONE -> {
                require(controlledEffectiveOptions == null && this.selectedRootNames.isEmpty()) {
                    "Config-none compilation must not carry controlled root authority."
                }
            }
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG -> {
                require(
                    exactProfileAuthority ==
                        R2CompilationProfileRegistry.PROVISIONAL_RHINO &&
                        configSchemaRevision ==
                        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION &&
                        controlledEffectiveOptions != null &&
                        effectiveOptions == controlledEffectiveOptions.compilerOptions
                ) {
                    "Controlled compilation profile/effective-options authority is inconsistent."
                }
                require(
                    this.selectedRootNames.isNotEmpty() &&
                        this.selectedRootNames.size <= R2_FILE_BACKED_MAXIMUM_FILES &&
                        this.selectedRootNames == this.selectedRootNames.sorted() &&
                        this.selectedRootNames.distinct() == this.selectedRootNames &&
                        this.selectedRootNames.all(String::isControlledProjectRootPath)
                ) {
                    "Controlled compilation selected roots are not canonical."
                }
            }
            else -> throw IllegalArgumentException(
                "Project compilation config kind is not supported.",
            )
        }
        val hasResolutionDiagnostics = this.diagnostics.any { diagnostic ->
            diagnostic.phase == R2ProjectDiagnosticPhase.RESOLUTION
        }
        val hasBlockingDiagnostics = this.diagnostics.any { diagnostic ->
            diagnostic.category == TYPESCRIPT_ERROR_CATEGORY
        }
        require(
            !hasBlockingDiagnostics || emitSkipped && this.outputs.isEmpty()
        ) {
            "Blocking TypeScript diagnostics must skip emit without output."
        }
        require(hasBlockingDiagnostics || !emitSkipped) {
            "An error-free TypeScript result must not skip emit."
        }
        require(
            !hasResolutionDiagnostics ||
                (
                    configKind == TypeScriptR2Contract.CONFIG_KIND_TSCONFIG ||
                        isNodeRevisionTwo
                    ) &&
                emitSkipped &&
                this.outputs.isEmpty()
        ) {
            "Only a controlled compilation can return resolution diagnostics, without output."
        }
    }
}

private fun String.isControlledProjectRootPath(): Boolean =
    startsWith("/sources/") &&
        endsWith(".ts") &&
        length <= TypeScriptCompilerContract.MAX_SOURCE_URI_CHARS &&
        '\\' !in this &&
        all { character -> character.code in 0x20..0x7e } &&
        drop(1).split('/').all { segment ->
            segment.isNotEmpty() && segment != "." && segment != ".."
        }

internal data class R2ProjectCompilationOutcome(
    val compilation: R2ProjectCompilation?,
    val errorCode: String?,
    val errorMessage: String?,
    val elapsedMs: Long,
    val configPolicyCode: String? = null,
    internal val exactSnapshot: R2ProjectInputSnapshot? = null,
) {
    init {
        val isSuccess =
            compilation != null &&
                errorCode == null &&
                errorMessage == null
        val isFailure =
            compilation == null &&
                errorCode != null &&
                errorMessage != null
        require(isSuccess.xor(isFailure)) {
            "Project compilation outcome must contain exactly one success or error state."
        }
        require(exactSnapshot == null || isSuccess) {
            "Only a successful project result can carry an exact snapshot binding."
        }
        require(
            configPolicyCode == null ||
                isFailure &&
                errorCode == TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS &&
                configPolicyCode in R2ControlledTsConfigPolicy.policyCodes
        ) {
            "Only a controlled-config diagnostics failure can carry a policy code."
        }
    }

    val succeeded: Boolean
        get() = compilation != null

    internal fun bindToExactSnapshot(
        snapshot: R2ProjectInputSnapshot,
    ): R2ProjectCompilationOutcome {
        check(succeeded && exactSnapshot == null) {
            "Only one successful exact snapshot binding may be issued."
        }
        val exactCompilation = requireNotNull(compilation)
        exactCompilation.nodeRevisionTwoResultAuthority?.let { resultAuthority ->
            R2NodeRevisionTwoResultAuthority.requireExact(
                snapshot = snapshot,
                outputs = exactCompilation.outputs,
                claimed = resultAuthority,
            )
        }
        return copy(exactSnapshot = snapshot)
    }

    companion object {
        fun failed(
            code: String,
            message: String,
            elapsedMs: Long,
            configPolicyCode: String? = null,
        ) = R2ProjectCompilationOutcome(
            compilation = null,
            errorCode = code,
            errorMessage = message.take(4_096),
            elapsedMs = elapsedMs,
            configPolicyCode = configPolicyCode,
        )
    }
}
