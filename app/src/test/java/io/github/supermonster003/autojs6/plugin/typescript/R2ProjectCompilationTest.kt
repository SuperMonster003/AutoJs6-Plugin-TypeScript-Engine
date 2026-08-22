package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2ProjectCompilationTest {

    @Test
    fun defensivelyCopiesDiagnosticsOutputsAndSourcePaths() {
        val inputSourcePaths = mutableListOf(MAIN_SOURCE)
        val output = output(sourcePaths = inputSourcePaths)
        val inputRelatedInformation = mutableListOf(
            R2ProjectRelatedInformation(
                category = TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
                code = 6_500,
                message = "Related declaration.",
                sourcePath = "/declarations/lib.d.ts",
                start = 0,
                length = 1,
                line = 1,
                column = 1,
            ),
        )
        val inputDiagnostics = mutableListOf(
            diagnostic().copy(relatedInformation = inputRelatedInformation),
        )
        val inputOutputs = mutableListOf(output)
        val compilation = compilation(
            diagnostics = inputDiagnostics,
            outputs = inputOutputs,
        )

        inputSourcePaths += "/sources/replaced.ts"
        inputDiagnostics.clear()
        inputRelatedInformation.clear()
        inputOutputs.clear()

        assertEquals(listOf(MAIN_SOURCE), output.sourcePaths)
        assertEquals(1, compilation.diagnostics.size)
        assertEquals(1, compilation.diagnostics.single().relatedInformation.size)
        assertUnmodifiable(compilation.diagnostics.single().relatedInformation)
        assertEquals(1, compilation.outputs.size)
        assertSame(output, compilation.outputs.single())
    }

    @Test
    fun exposesRuntimeUnmodifiableCollections() {
        val output = output()
        val compilation = compilation(
            diagnostics = listOf(diagnostic()),
            outputs = listOf(
                output,
                output(path = "/outputs/main.js.map"),
            ),
        )

        assertUnmodifiable(compilation.diagnostics)
        assertUnmodifiable(compilation.outputs)
        assertUnmodifiable(output.sourcePaths)
        assertUnmodifiable(compilation.executableModules)
        assertUnmodifiable(compilation.sourceMaps)
    }

    @Test
    fun controlledCompilationFreezesEffectiveOptionsAndSelectedRoots() {
        val roots = mutableListOf(MAIN_SOURCE, "/sources/types.d.ts")
        val effective = R2ControlledTsConfigEffectiveOptions.resolve(emptyMap())
        val compilation = controlledCompilation(
            effectiveOptions = effective,
            selectedRootNames = roots,
        )

        roots.clear()

        assertSame(effective, compilation.controlledEffectiveOptions)
        assertEquals(listOf(MAIN_SOURCE, "/sources/types.d.ts"), compilation.selectedRootNames)
        assertUnmodifiable(compilation.selectedRootNames)
        assertFails<IllegalArgumentException> {
            controlledCompilation(effective, emptyList())
        }
        assertFails<IllegalArgumentException> {
            controlledCompilation(
                effective,
                listOf("/sources/z.ts", MAIN_SOURCE),
            )
        }
        assertFails<IllegalArgumentException> {
            controlledCompilation(
                effective,
                listOf("/sources/not-javascript.js"),
            )
        }
        assertFails<IllegalArgumentException> {
            controlledCompilation(
                effective,
                (0..R2_FILE_BACKED_MAXIMUM_FILES).map { index ->
                    "/sources/root-${index.toString().padStart(5, '0')}.ts"
                },
            )
        }
    }

    @Test
    fun controlledCompilationRequiresExactRhinoRevisionOneProfile() {
        val effective = R2ControlledTsConfigEffectiveOptions.resolve(emptyMap())
        controlledCompilation(effective, listOf(MAIN_SOURCE))

        assertFails<IllegalArgumentException> {
            controlledCompilationForProfile(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
                effectiveOptions = effective,
            )
        }

        val nodeRevisionTwoSnapshot = nodeRevisionTwoSnapshot()
        try {
            assertFails<IllegalArgumentException> {
                controlledCompilationForProfile(
                    profileAuthority =
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                    effectiveOptions = effective,
                    nodeRevisionTwoResultAuthority =
                        R2NodeRevisionTwoResultAuthority.derive(
                            snapshot = nodeRevisionTwoSnapshot,
                            outputs = emptyList(),
                        ),
                )
            }
        } finally {
            nodeRevisionTwoSnapshot.close()
        }
    }

    @Test
    fun diagnosticModelFreezesTheUnifiedNineFieldShape() {
        val diagnostics = unifiedPreEmitDiagnostics()

        assertEquals(
            listOf(
                R2ProjectDiagnosticPhase.OPTIONS,
                R2ProjectDiagnosticPhase.SYNTACTIC,
                R2ProjectDiagnosticPhase.GLOBAL,
                R2ProjectDiagnosticPhase.SEMANTIC,
            ),
            diagnostics.map(R2ProjectDiagnostic::phase),
        )
        diagnostics.forEach { diagnostic ->
            assertEquals(1, diagnostic.category)
            assertTrue(diagnostic.code > 0)
            assertTrue(diagnostic.message.isNotEmpty())
            assertEquals(null, diagnostic.nodeResolution)
            val hasLocation = diagnostic.phase == R2ProjectDiagnosticPhase.SYNTACTIC ||
                diagnostic.phase == R2ProjectDiagnosticPhase.SEMANTIC
            assertEquals(if (hasLocation) MAIN_SOURCE else null, diagnostic.sourcePath)
            assertEquals(if (hasLocation) 0 else null, diagnostic.start)
            assertEquals(if (hasLocation) 1 else null, diagnostic.length)
            assertEquals(if (hasLocation) 1 else null, diagnostic.line)
            assertEquals(if (hasLocation) 1 else null, diagnostic.column)
        }

        val located = diagnostic()
        assertFails<IllegalArgumentException> { located.copy(category = -1) }
        assertFails<IllegalArgumentException> { located.copy(category = 4) }
        assertFails<IllegalArgumentException> { located.copy(code = 0) }
        assertFails<IllegalArgumentException> { located.copy(sourcePath = null) }
        assertFails<IllegalArgumentException> { located.copy(start = null) }
        assertFails<IllegalArgumentException> { located.copy(length = -1) }
        assertFails<IllegalArgumentException> { located.copy(line = 0) }
        assertFails<IllegalArgumentException> { located.copy(column = 0) }
        located.copy(
            sourcePath = null,
            start = null,
            length = null,
            line = null,
            column = null,
        )
    }

    @Test
    fun unifiedPreEmitBlockingDiagnosticsFreezeNoEmitAndNonErrorsDoNotBlock() {
        unifiedPreEmitDiagnostics().forEach { blockingDiagnostic ->
            val diagnosticsOnly = compilation(
                diagnostics = listOf(blockingDiagnostic),
                outputs = emptyList(),
                emitSkipped = true,
            )
            assertTrue(diagnosticsOnly.emitSkipped)
            assertTrue(diagnosticsOnly.outputs.isEmpty())
            assertFails<IllegalArgumentException> {
                compilation(
                    diagnostics = listOf(blockingDiagnostic),
                    outputs = listOf(output()),
                    emitSkipped = true,
                )
            }
            assertFails<IllegalArgumentException> {
                compilation(
                    diagnostics = listOf(blockingDiagnostic),
                    outputs = emptyList(),
                    emitSkipped = false,
                )
            }
        }

        listOf(0, 2, 3).forEachIndexed { index, category ->
            val nonBlockingDiagnostic = diagnostic(
                category = category,
                code = 910_200 + index,
            )
            val emitted = compilation(
                diagnostics = listOf(nonBlockingDiagnostic),
                outputs = listOf(output()),
                emitSkipped = false,
            )
            assertFalse(emitted.emitSkipped)
            assertEquals(1, emitted.outputs.size)
            assertFails<IllegalArgumentException> {
                compilation(
                    diagnostics = listOf(nonBlockingDiagnostic),
                    outputs = emptyList(),
                    emitSkipped = true,
                )
            }
        }
    }

    @Test
    fun outcomeRequiresExactlyOneSuccessOrErrorState() {
        val compilation = compilation()
        val success = R2ProjectCompilationOutcome(
            compilation = compilation,
            errorCode = null,
            errorMessage = null,
            elapsedMs = 1,
        )
        val failure = R2ProjectCompilationOutcome.failed(
            code = "E_PROJECT_COMPILE",
            message = "Compilation failed.",
            elapsedMs = 2,
        )

        assertTrue(success.succeeded)
        assertSame(compilation, success.compilation)
        assertFalse(failure.succeeded)
        assertEquals("E_PROJECT_COMPILE", failure.errorCode)

        assertInvalidOutcome(
            compilation = null,
            errorCode = null,
            errorMessage = null,
        )
        assertInvalidOutcome(
            compilation = compilation,
            errorCode = "E_PROJECT_COMPILE",
            errorMessage = "Compilation failed.",
        )
        assertInvalidOutcome(
            compilation = null,
            errorCode = "E_PROJECT_COMPILE",
            errorMessage = null,
        )
        assertInvalidOutcome(
            compilation = null,
            errorCode = null,
            errorMessage = "Compilation failed.",
        )
        assertFails<IllegalArgumentException> {
            success.copy(
                errorCode = "E_PROJECT_COMPILE",
                errorMessage = "Compilation failed.",
            )
        }
    }

    @Test
    fun controlledConfigDiagnosticsPreserveTheTypedPolicyCodeOnlyOnThatFailure() {
        val policyCode = TypeScriptR2Contract.CONFIG_POLICY_CODE_DUPLICATE_KEY
        val outcome = R2ProjectCompilationOutcome.failed(
            code = TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS,
            message = "Controlled config was rejected.",
            elapsedMs = 3,
            configPolicyCode = policyCode,
        )

        assertEquals(policyCode, outcome.configPolicyCode)
        assertFails<IllegalArgumentException> {
            outcome.copy(errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST)
        }
        assertFails<IllegalArgumentException> {
            outcome.copy(configPolicyCode = "CONFIG_NOT_FROZEN")
        }
        assertFails<IllegalArgumentException> {
            R2ProjectCompilationOutcome(
                compilation = compilation(),
                errorCode = null,
                errorMessage = null,
                elapsedMs = 0,
                configPolicyCode = policyCode,
            )
        }
    }

    @Test
    fun nodeResolutionFailureCatalogHasExactBidirectionalDiagnosticIdentities() {
        assertEquals(18, R2NodeResolutionFailureCode.entries.size)
        R2NodeResolutionFailureCode.entries.forEachIndexed { index, failure ->
            assertEquals(900_001 + index, failure.diagnosticCode)
            assertSame(
                failure,
                R2NodeResolutionFailureCode.fromDiagnosticIdentityOrNull(
                    contractValue = failure.contractValue,
                    diagnosticCode = failure.diagnosticCode,
                ),
            )
            assertEquals(
                null,
                R2NodeResolutionFailureCode.fromDiagnosticIdentityOrNull(
                    contractValue = failure.contractValue,
                    diagnosticCode = failure.diagnosticCode + 1,
                ),
            )
        }
        assertEquals(
            null,
            R2NodeResolutionFailureCode.fromDiagnosticIdentityOrNull(
                contractValue = "NODE_RESOLUTION_NOT_FROZEN",
                diagnosticCode = 900_001,
            ),
        )
    }

    @Test
    fun resolutionDiagnosticsRequireExactEvidenceAndStopEveryOutput() {
        R2NodeResolutionFailureCode.entries.forEach { failure ->
            val diagnostic = resolutionDiagnostic(failure)
            assertEquals(R2ProjectDiagnosticPhase.RESOLUTION, diagnostic.phase)
            assertSame(failure, requireNotNull(diagnostic.nodeResolution).policyCode)
            controlledResolutionCompilation(diagnostic)
        }

        val valid = resolutionDiagnostic(R2NodeResolutionFailureCode.EXPORTS_NO_MATCH)
        assertFails<IllegalArgumentException> { valid.copy(category = 0) }
        assertFails<IllegalArgumentException> { valid.copy(code = valid.code + 1) }
        assertFails<IllegalArgumentException> { valid.copy(start = 0) }
        assertFails<IllegalArgumentException> { valid.copy(nodeResolution = null) }
        assertFails<IllegalArgumentException> {
            valid.copy(phase = R2ProjectDiagnosticPhase.SEMANTIC)
        }
        assertFails<IllegalArgumentException> {
            diagnostic().copy(
                nodeResolution = R2NodeResolutionDiagnosticEvidence(
                    R2NodeResolutionFailureCode.EXPORTS_NO_MATCH,
                ),
            )
        }
        assertFails<IllegalArgumentException> {
            compilation(
                diagnostics = listOf(valid),
                emitSkipped = true,
            )
        }
        assertFails<IllegalArgumentException> {
            controlledResolutionCompilation(
                diagnostic = valid,
                outputs = listOf(output()),
                emitSkipped = true,
            )
        }
        assertFails<IllegalArgumentException> {
            controlledResolutionCompilation(
                diagnostic = valid,
                emitSkipped = false,
            )
        }
    }

    @Test
    fun resolutionDiagnosticsAreAdmittedOnlyForControlledRhinoOrNodeRevisionTwo() {
        val failure = R2NodeResolutionFailureCode.EXPORTS_NO_MATCH
        val diagnostic = resolutionDiagnostic(failure)
        controlledResolutionCompilation(diagnostic)

        assertFails<IllegalArgumentException> {
            configNoneResolutionCompilation(
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                diagnostic = diagnostic,
            )
        }
        assertFails<IllegalArgumentException> {
            configNoneResolutionCompilation(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
                diagnostic = diagnostic,
            )
        }

        val nodeRevisionTwoSnapshot = nodeRevisionTwoSnapshot()
        try {
            val nodeRevisionTwo = configNoneResolutionCompilation(
                profileAuthority =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
                diagnostic = diagnostic.copy(sourcePath = null),
                nodeRevisionTwoResultAuthority =
                    R2NodeRevisionTwoResultAuthority.derive(
                        snapshot = nodeRevisionTwoSnapshot,
                        outputs = emptyList(),
                    ),
            )
            assertTrue(nodeRevisionTwo.emitSkipped)
            assertTrue(nodeRevisionTwo.outputs.isEmpty())
            assertSame(
                failure,
                requireNotNull(nodeRevisionTwo.diagnostics.single().nodeResolution).policyCode,
            )
        } finally {
            nodeRevisionTwoSnapshot.close()
        }
    }

    private fun assertInvalidOutcome(
        compilation: R2ProjectCompilation?,
        errorCode: String?,
        errorMessage: String?,
    ) {
        assertFails<IllegalArgumentException> {
            R2ProjectCompilationOutcome(
                compilation = compilation,
                errorCode = errorCode,
                errorMessage = errorMessage,
                elapsedMs = 0,
            )
        }
    }

    private fun diagnostic(
        phase: R2ProjectDiagnosticPhase = R2ProjectDiagnosticPhase.SEMANTIC,
        category: Int = 0,
        code: Int = 2_322,
        sourcePath: String? = MAIN_SOURCE,
        start: Int? = 0,
        length: Int? = 1,
        line: Int? = 1,
        column: Int? = 1,
    ) = R2ProjectDiagnostic(
        phase = phase,
        category = category,
        code = code,
        message = "${phase.protocolValue} diagnostic.",
        sourcePath = sourcePath,
        start = start,
        length = length,
        line = line,
        column = column,
    )

    private fun unifiedPreEmitDiagnostics(): List<R2ProjectDiagnostic> =
        listOf(
            R2ProjectDiagnosticPhase.OPTIONS,
            R2ProjectDiagnosticPhase.SYNTACTIC,
            R2ProjectDiagnosticPhase.GLOBAL,
            R2ProjectDiagnosticPhase.SEMANTIC,
        ).mapIndexed { index, phase ->
            val hasLocation = phase == R2ProjectDiagnosticPhase.SYNTACTIC ||
                phase == R2ProjectDiagnosticPhase.SEMANTIC
            diagnostic(
                phase = phase,
                category = 1,
                code = 910_100 + index,
                sourcePath = if (hasLocation) MAIN_SOURCE else null,
                start = if (hasLocation) 0 else null,
                length = if (hasLocation) 1 else null,
                line = if (hasLocation) 1 else null,
                column = if (hasLocation) 1 else null,
            )
        }

    private fun resolutionDiagnostic(
        failure: R2NodeResolutionFailureCode,
    ) = R2ProjectDiagnostic(
        phase = R2ProjectDiagnosticPhase.RESOLUTION,
        category = 1,
        code = failure.diagnosticCode,
        message = "Controlled Node resolution failed.",
        sourcePath = "/packages/dependency/index.d.ts",
        start = null,
        length = null,
        line = null,
        column = null,
        nodeResolution = R2NodeResolutionDiagnosticEvidence(failure),
    )

    private fun output(
        path: String = "/outputs/main.js",
        sourcePaths: List<String> = listOf(MAIN_SOURCE),
    ) = R2ProjectOutput(
        path = path,
        text = "export {};\n",
        sha256 = "0".repeat(64),
        sourcePaths = sourcePaths,
    )

    private fun compilation(
        diagnostics: List<R2ProjectDiagnostic> = emptyList(),
        outputs: List<R2ProjectOutput> = emptyList(),
        emitSkipped: Boolean = false,
    ) = R2ProjectCompilation(
        compilerApi = "createIncrementalProgram",
        compilerVersion = "6.0.3",
        targetProfile = "rhino",
        profileSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
        profileRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        configSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configSchemaRevision,
        configKind = R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
        effectiveOptions =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.effectiveOptions,
        diagnostics = diagnostics,
        outputs = outputs,
        emitSkipped = emitSkipped,
    )

    private fun controlledCompilation(
        effectiveOptions: R2ControlledTsConfigEffectiveOptions,
        selectedRootNames: List<String>,
    ) = R2ProjectCompilation(
        compilerApi = "createIncrementalProgram",
        compilerVersion = "6.0.3",
        targetProfile = "rhino",
        profileSchemaRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
        profileRevision =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        effectiveOptions = effectiveOptions.compilerOptions,
        controlledEffectiveOptions = effectiveOptions,
        selectedRootNames = selectedRootNames,
        diagnostics = emptyList(),
        outputs = emptyList(),
        emitSkipped = false,
    )

    private fun controlledCompilationForProfile(
        profileAuthority: R2CompilationProfileAuthority,
        effectiveOptions: R2ControlledTsConfigEffectiveOptions,
        nodeRevisionTwoResultAuthority: R2NodeRevisionTwoResultAuthority? = null,
    ) = R2ProjectCompilation(
        compilerApi = "createIncrementalProgram",
        compilerVersion = "6.0.3",
        targetProfile = profileAuthority.profile.contractValue,
        profileSchemaRevision = profileAuthority.profileSchemaRevision,
        profileRevision = profileAuthority.profileRevision,
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        effectiveOptions = effectiveOptions.compilerOptions,
        controlledEffectiveOptions = effectiveOptions,
        selectedRootNames = listOf(MAIN_SOURCE),
        diagnostics = emptyList(),
        outputs = emptyList(),
        emitSkipped = false,
        nodeRevisionTwoResultAuthority = nodeRevisionTwoResultAuthority,
    )

    private fun controlledResolutionCompilation(
        diagnostic: R2ProjectDiagnostic,
        outputs: List<R2ProjectOutput> = emptyList(),
        emitSkipped: Boolean = true,
    ): R2ProjectCompilation {
        val effectiveOptions = R2ControlledTsConfigEffectiveOptions.resolve(emptyMap())
        return R2ProjectCompilation(
            compilerApi = "createIncrementalProgram",
            compilerVersion = "6.0.3",
            targetProfile = "rhino",
            profileSchemaRevision =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileSchemaRevision,
            profileRevision =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
            configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
            effectiveOptions = effectiveOptions.compilerOptions,
            controlledEffectiveOptions = effectiveOptions,
            selectedRootNames = listOf(MAIN_SOURCE),
            diagnostics = listOf(diagnostic),
            outputs = outputs,
            emitSkipped = emitSkipped,
        )
    }

    private fun configNoneResolutionCompilation(
        profileAuthority: R2CompilationProfileAuthority,
        diagnostic: R2ProjectDiagnostic,
        nodeRevisionTwoResultAuthority: R2NodeRevisionTwoResultAuthority? = null,
    ) = R2ProjectCompilation(
        compilerApi = "createIncrementalProgram",
        compilerVersion = "6.0.3",
        targetProfile = profileAuthority.profile.contractValue,
        profileSchemaRevision = profileAuthority.profileSchemaRevision,
        profileRevision = profileAuthority.profileRevision,
        configSchemaRevision = profileAuthority.configSchemaRevision,
        configKind = profileAuthority.configKind,
        effectiveOptions = profileAuthority.effectiveOptions,
        diagnostics = listOf(diagnostic),
        outputs = emptyList(),
        emitSkipped = true,
        nodeRevisionTwoResultAuthority = nodeRevisionTwoResultAuthority,
    )

    private fun nodeRevisionTwoSnapshot(): R2ProjectSnapshot =
        R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(
                    "/declarations/lib.d.ts",
                    "declare interface Array<T> {}\n",
                ),
                R2ProjectTextFile(
                    "/declarations/node/index.d.ts",
                    "declare const process: {};\n",
                ),
                R2ProjectTextFile(MAIN_SOURCE, "export const value = 1;\n"),
            ),
            rootNames = listOf(MAIN_SOURCE),
            entryPath = MAIN_SOURCE,
            acceptedProjectId = "project-compilation-node-r2",
            profileAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> assertUnmodifiable(values: List<T>) {
        assertFails<UnsupportedOperationException> {
            (values as MutableList<T>).clear()
        }
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError(
                "Expected ${T::class.java.name}, got ${error::class.java.name}.",
                error,
            )
        }
        fail("Expected ${T::class.java.name}.")
        error("unreachable")
    }

    private companion object {
        const val MAIN_SOURCE = "/sources/main.ts"
    }
}
