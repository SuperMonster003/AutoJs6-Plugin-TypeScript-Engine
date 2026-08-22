package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Entry
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2ProjectCompilerInstrumentationTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val fixtureContext
        get() = instrumentation.context

    @Test
    fun provisionalRhinoProjectEmitsGoldenAndTypeErrorEmitsNothing() {
        val fixture = loadFixture()
        assertEquals(1, fixture.getInt("fixtureRevision"))
        assertEquals(
            TypeScriptCompilerContract.PROFILE_RHINO,
            fixture.getString("targetProfile"),
        )

        withCompilerHost { host ->
            val successFixture = fixture.getJSONObject("success")
            val successSnapshot =
                fixture.snapshot(successFixture.getString("mainSource"))
            val success = host.compileProject(
                snapshot = successSnapshot,
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )

            assertTrue(success.errorMessage.orEmpty(), success.succeeded)
            assertTrue(success.exactSnapshot === successSnapshot)
            val emitted = requireNotNull(success.compilation)
            assertEquals("createIncrementalProgram", emitted.compilerApi)
            assertEquals(BuildConfig.TYPESCRIPT_VERSION, emitted.compilerVersion)
            assertFalse(emitted.emitSkipped)
            assertTrue(emitted.diagnostics.isEmpty())
            assertEquals(2, emitted.executableModules.size)
            assertEquals(2, emitted.sourceMaps.size)
            assertGoldenOutputs(successFixture, emitted)

            val errorFixture = fixture.getJSONObject("semanticError")
            val typeError = host.compileProject(
                snapshot = fixture.snapshot(errorFixture.getString("mainSource")),
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )

            assertTrue(typeError.errorMessage.orEmpty(), typeError.succeeded)
            val diagnosticsOnly = requireNotNull(typeError.compilation)
            assertTrue(diagnosticsOnly.emitSkipped)
            assertTrue(diagnosticsOnly.outputs.isEmpty())
            assertEquals(1, diagnosticsOnly.diagnostics.size)
            assertDiagnostic(
                expected = errorFixture.getJSONObject("diagnostic"),
                actual = diagnosticsOnly.diagnostics.singleOrNull(),
            )

            val declarationErrorFixture = fixture.getJSONObject("declarationError")
            val declarationError = host.compileProject(
                snapshot = declarationErrorFixture.declarationErrorSnapshot(),
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )

            assertTrue(declarationError.errorMessage.orEmpty(), declarationError.succeeded)
            val declarationDiagnostics = requireNotNull(declarationError.compilation)
            assertTrue(declarationDiagnostics.emitSkipped)
            assertTrue(declarationDiagnostics.outputs.isEmpty())
            assertEquals(1, declarationDiagnostics.diagnostics.size)
            assertDiagnostic(
                expected = declarationErrorFixture.getJSONObject("diagnostic"),
                actual = declarationDiagnostics.diagnostics.singleOrNull(),
            )

            val sourceDeclarationFixture = fixture.getJSONObject("sourceDeclarationError")
            val sourceDeclarationError = host.compileProject(
                snapshot = sourceDeclarationFixture.projectFilesSnapshot(fixture),
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )

            assertTrue(
                sourceDeclarationError.errorMessage.orEmpty(),
                sourceDeclarationError.succeeded,
            )
            val sourceDeclarationDiagnostics =
                requireNotNull(sourceDeclarationError.compilation)
            assertTrue(sourceDeclarationDiagnostics.emitSkipped)
            assertTrue(sourceDeclarationDiagnostics.outputs.isEmpty())
            assertEquals(1, sourceDeclarationDiagnostics.diagnostics.size)
            assertDiagnostic(
                expected = sourceDeclarationFixture.getJSONObject("diagnostic"),
                actual = sourceDeclarationDiagnostics.diagnostics.singleOrNull(),
            )

            val typeOnlyFixture = fixture.getJSONObject("typeOnlyRootImport")
            val typeOnly = host.compileProject(
                snapshot = typeOnlyFixture.projectFilesSnapshot(fixture),
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )

            assertTrue(typeOnly.errorMessage.orEmpty(), typeOnly.succeeded)
            val typeOnlyCompilation = requireNotNull(typeOnly.compilation)
            assertFalse(typeOnlyCompilation.emitSkipped)
            assertTrue(typeOnlyCompilation.diagnostics.isEmpty())
            assertEquals(2, typeOnlyCompilation.executableModules.size)
            assertEquals(2, typeOnlyCompilation.sourceMaps.size)
            val typeOnlyModelMap = requireNotNull(
                typeOnlyCompilation.outputs.singleOrNull { output ->
                    output.path == "/outputs/model.js.map"
                },
            )
            assertTrue(JSONObject(typeOnlyModelMap.text).getString("mappings").isEmpty())

            val r1Probe = host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
            assertTrue(r1Probe.errorMessage.orEmpty(), r1Probe.succeeded)
            assertNotNull(r1Probe.evidence)
        }
    }

    @Test
    fun packagedStandardLibrariesCrossTheFormerSingleMessageLimit() {
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val source = R2ProjectTextFile(
            path = MAIN_SOURCE_PATH,
            text =
            "export const framedValue: Promise<number> = Promise.resolve(42);\n",
        )
        val snapshot = R2ProjectSnapshot.create(
            files = declarations.files.map { declaration ->
                R2ProjectTextFile(
                    path = "/${declaration.archivePath}",
                    text = declaration.text,
                )
            } + source,
            rootNames = listOf(MAIN_SOURCE_PATH),
            limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
        )
        assertEquals(109, snapshot.files.size)
        assertTrue(snapshot.totalUtf8Bytes > 3L * 1024L * 1024L)

        withCompilerHost { host ->
            val outcome = host.compileProject(
                snapshot = snapshot,
                timeoutMs = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
            )
            assertTrue(outcome.errorMessage.orEmpty(), outcome.succeeded)
            val compilation = requireNotNull(outcome.compilation)
            assertFalse(compilation.emitSkipped)
            assertTrue(compilation.diagnostics.isEmpty())
            assertEquals(1, compilation.executableModules.size)
            assertEquals(1, compilation.sourceMaps.size)
        }
    }

    @Test
    fun successfulProjectRetiresExactSessionBeforeReturnAndSuccessorRecreates() {
        val fixture = loadFixture()
        val mainSource = fixture
            .getJSONObject("success")
            .getString("mainSource")

        withCompilerHost { host ->
            val first = host.compileProject(
                snapshot = fixture.snapshot(mainSource),
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )
            assertTrue(first.errorMessage.orEmpty(), first.succeeded)
            val firstGeneration = requireNotNull(
                host.lastRetiredSessionGenerationForTesting(),
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())

            val successor = host.compileProject(
                snapshot = fixture.snapshot(mainSource),
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )
            assertTrue(successor.errorMessage.orEmpty(), successor.succeeded)
            val successorGeneration = requireNotNull(
                host.lastRetiredSessionGenerationForTesting(),
            )
            assertTrue(
                "A successor project must use and retire a newer WebView session.",
                successorGeneration > firstGeneration,
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
        }
    }

    @Test
    fun expandedWorkerAcceptsExact64MiBCallerAndRetiresBeforeSmallSuccessor() {
        val capacityDirectory = context.getDir(
            CAPACITY_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)

        withCompilerHost(timeoutSeconds = CAPACITY_TEST_TIMEOUT_SECONDS) { host ->
            val boundarySnapshot = createExactCallerCapacitySnapshot(
                directory = capacityDirectory,
                declarations = declarations,
            )
            val declarationBytes = declarations.files.sumOf { declaration ->
                declaration.size
            }
            val boundaryTotalBytes =
                EXACT_CALLER_CAPACITY_BYTES + declarationBytes
            assertEquals(
                EXACT_CALLER_CAPACITY_BYTES,
                boundarySnapshot.totalUtf8Bytes - declarationBytes,
            )
            assertEquals(boundaryTotalBytes, boundarySnapshot.totalUtf8Bytes)
            assertEquals(
                EXACT_CALLER_FILE_COUNT + declarations.files.size,
                boundarySnapshot.fileInventory.size,
            )
            assertEquals(
                TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
                boundarySnapshot.largestFileUtf8Bytes,
            )
            assertFalse(boundarySnapshot.namedPathRetained)

            val boundary = try {
                host.compileProjectWithExpandedWorkerCapacityForTesting(
                    handle = host.newProjectOperationHandle(),
                    snapshot = boundarySnapshot,
                    timeoutMs = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                )
            } finally {
                boundarySnapshot.close()
            }

            assertTrue(
                boundary.errorMessage.orEmpty() + "; input=" +
                    host.lastProjectInputTransportTelemetryForTesting(),
                boundary.succeeded,
            )
            val inputTelemetry = requireNotNull(
                host.lastProjectInputTransportTelemetryForTesting(),
            )
            assertTrue(inputTelemetry.complete)
            assertEquals(boundaryTotalBytes, inputTelemetry.acceptedPayloadBytes)
            assertEquals(boundaryTotalBytes, inputTelemetry.totalPayloadBytes)
            assertTrue(
                inputTelemetry.maximumEncodedFrameBytes <=
                    R2_PROJECT_MAXIMUM_FRAME_BYTES,
            )
            assertReleasedInputCursors(
                telemetry = inputTelemetry,
                expectedComplete = true,
            )
            val compilation = requireNotNull(boundary.compilation)
            assertFalse(compilation.emitSkipped)
            assertTrue(compilation.diagnostics.isEmpty())
            assertEquals(1, compilation.executableModules.size)
            assertEquals(1, compilation.sourceMaps.size)
            val boundaryGeneration = requireNotNull(
                host.lastRetiredSessionGenerationForTesting(),
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
            Log.i(
                CAPACITY_LOG_TAG,
                "PASS api=${Build.VERSION.SDK_INT} " +
                    "webView=${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                    "callerBytes=$EXACT_CALLER_CAPACITY_BYTES " +
                    "trustedBytes=$declarationBytes " +
                    "totalBytes=$boundaryTotalBytes " +
                    "frames=${inputTelemetry.sentFrameCount} " +
                    "maximumEncodedFrameBytes=${inputTelemetry.maximumEncodedFrameBytes} " +
                    "elapsedMs=${boundary.elapsedMs} " +
                    "retiredGeneration=$boundaryGeneration",
            )
            assertTrue(
                "The file-backed capacity spool must be unlinked after snapshot release.",
                capacityDirectory.listFiles().orEmpty().none { file ->
                    file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
                },
            )

            val fixture = loadFixture()
            val successor = host.compileProject(
                snapshot = fixture.snapshot(
                    fixture.getJSONObject("success").getString("mainSource"),
                ),
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )
            assertTrue(successor.errorMessage.orEmpty(), successor.succeeded)
            assertTrue(
                requireNotNull(host.lastRetiredSessionGenerationForTesting()) >
                    boundaryGeneration,
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
        }
    }

    @Test
    fun repeatedExact64NaturalIngressCancellationReleasesCursorAndBoundsRendererPss() {
        val capacityDirectory = context.getDir(
            CAPACITY_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val boundarySnapshot = createExactCallerCapacitySnapshot(
            directory = capacityDirectory,
            declarations = declarations,
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        val processProbe = R1WebViewProcessMemoryProbe(context)
        val rendererPssBatches = ArrayList<R1SingleProcessPssBatch>(
            REPEATED_RESOURCE_CYCLE_COUNT,
        )
        val rendererIdentities = ArrayList<R1LiveProcess>(
            REPEATED_RESOURCE_CYCLE_COUNT,
        )
        var lastTerminatedGeneration = 0
        try {
            repeat(REPEATED_RESOURCE_CYCLE_COUNT) { cycleIndex ->
                val warm = executor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "The replacement Worker did not warm before resource cycle " +
                        "${cycleIndex + 1}: ${warm.errorMessage}",
                    warm.succeeded,
                )

                val handle = host.newProjectOperationHandle()
                val compilation = executor.submit(
                    Callable {
                        host.compileProjectWithExpandedWorkerCapacityForTesting(
                            handle = handle,
                            snapshot = boundarySnapshot,
                            timeoutMs = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                        )
                    },
                )
                val liveTelemetry = waitForActiveInputCursor(
                    host = host,
                    handle = handle,
                    minimumAcceptedBytes =
                        NATURAL_INGRESS_CURSOR_OBSERVATION_BYTES,
                )
                assertEquals(
                    R2ProjectExecutionPhase.INGRESS,
                    host.projectExecutionPhaseForTesting(handle),
                )
                assertFalse(liveTelemetry.complete)
                assertFalse(liveTelemetry.released)
                assertTrue(
                    liveTelemetry.acceptedPayloadBytes <
                        liveTelemetry.totalPayloadBytes,
                )

                val renderer =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        processProbe.awaitRendererForClient(
                            android.os.Process.myPid(),
                        )
                    } else {
                        null
                    }
                val rendererPss = renderer?.let { exactRenderer ->
                    processProbe.samplePss(
                        process = exactRenderer,
                        sampleCount = RESOURCE_PSS_SAMPLE_COUNT,
                        sampleIntervalMillis =
                            RESOURCE_PSS_SAMPLE_INTERVAL_MILLIS,
                    ).also { batch ->
                        rendererIdentities += exactRenderer
                        rendererPssBatches += batch
                    }
                }

                val stopTelemetry = waitForActiveInputCursor(
                    host = host,
                    handle = handle,
                    minimumAcceptedBytes =
                        NATURAL_INGRESS_CURSOR_OBSERVATION_BYTES,
                )
                assertTrue(
                    "The exact input cursor was no longer active before cancellation.",
                    stopTelemetry.activeCursor,
                )
                assertTrue(
                    host.requestStop(
                        handle = handle,
                        cause = R2ProjectStopCause.CANCELLED,
                    ),
                )
                val cancelled =
                    compilation.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertFalse(cancelled.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_CANCELLED,
                    cancelled.errorCode,
                )
                assertEquals(null, cancelled.compilation)

                val terminalTelemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                )
                assertEquals(handle.requestId, terminalTelemetry.requestId)
                assertReleasedInputCursors(
                    telemetry = terminalTelemetry,
                    expectedComplete = false,
                )
                assertTrue(
                    "Natural ingress cancellation completed the entire input unexpectedly.",
                    terminalTelemetry.acceptedPayloadBytes <
                        terminalTelemetry.totalPayloadBytes,
                )
                val terminatedGeneration = requireNotNull(
                    host.lastTerminatedWorkerGenerationForTesting(),
                )
                assertTrue(
                    "The repeated ingress fault did not terminate a newer Worker.",
                    terminatedGeneration > lastTerminatedGeneration,
                )
                lastTerminatedGeneration = terminatedGeneration
                Log.i(
                    RESOURCE_LOG_TAG,
                    "INGRESS_CYCLE_PASS cycle=${cycleIndex + 1} " +
                        "requestId=${handle.requestId} " +
                        "acceptedBytes=${terminalTelemetry.acceptedPayloadBytes} " +
                        "totalBytes=${terminalTelemetry.totalPayloadBytes} " +
                        "openedCursors=${terminalTelemetry.openedCursorCount} " +
                        "closedCursors=${terminalTelemetry.closedCursorCount} " +
                        "rendererPid=${renderer?.pid ?: "unavailable"} " +
                        "rendererStart=${renderer?.startTimeTicks ?: "unavailable"} " +
                        "rendererMedianPssKiB=" +
                        "${rendererPss?.medianPssKiB ?: "unavailable"} " +
                        "rendererMaximumPssKiB=" +
                        "${rendererPss?.maximumPssKiB ?: "unavailable"} " +
                        "workerGeneration=$terminatedGeneration",
                )
            }

            if (rendererPssBatches.isNotEmpty()) {
                assertEquals(
                    REPEATED_RESOURCE_CYCLE_COUNT,
                    rendererPssBatches.size,
                )
                val firstRendererPss =
                    rendererPssBatches.first().medianPssKiB
                val finalRendererPss =
                    rendererPssBatches.last().medianPssKiB
                assertTrue(
                    "Repeated active renderer PSS envelope grew by " +
                        "${finalRendererPss - firstRendererPss} KiB; " +
                        "first=$firstRendererPss final=$finalRendererPss " +
                        "batches=$rendererPssBatches identities=$rendererIdentities",
                    finalRendererPss <=
                        firstRendererPss +
                        MAX_REPEATED_RENDERER_MEDIAN_GROWTH_KIB,
                )
            }

            val fixture = loadFixture()
            val recovered = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = fixture.snapshot(
                            fixture.getJSONObject("success")
                                .getString("mainSource"),
                        ),
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
            Log.i(
                RESOURCE_LOG_TAG,
                "INGRESS_MATRIX_PASS api=${Build.VERSION.SDK_INT} " +
                    "webView=" +
                    "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                    "cycles=$REPEATED_RESOURCE_CYCLE_COUNT " +
                    "minimumObservedBytes=$NATURAL_INGRESS_CURSOR_OBSERVATION_BYTES " +
                    "rendererMedianPssKiB=" +
                    rendererPssBatches.joinToString(
                        prefix = "[",
                        postfix = "]",
                    ) { batch -> batch.medianPssKiB.toString() } +
                    " lastTerminatedWorkerGeneration=$lastTerminatedGeneration",
            )
        } finally {
            onMain {
                host.close()
            }
            executor.shutdownNow()
            boundarySnapshot.close()
        }
        assertTrue(
            "Repeated natural-ingress cancellation left a capacity spool file.",
            capacityDirectory.listFiles().orEmpty().none { file ->
                file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
            },
        )
    }

    @Test
    fun repeatedExact64NaturalIngressTimeoutReleasesCursorAndRecovers() {
        val capacityDirectory = context.getDir(
            CAPACITY_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val boundarySnapshot = createExactCallerCapacitySnapshot(
            directory = capacityDirectory,
            declarations = declarations,
        )
        val declarationBytes = declarations.files.sumOf { declaration ->
            declaration.size
        }
        assertEquals(
            EXACT_CALLER_CAPACITY_BYTES,
            boundarySnapshot.totalUtf8Bytes - declarationBytes,
        )
        assertEquals(
            EXACT_CALLER_FILE_COUNT + declarations.files.size,
            boundarySnapshot.fileInventory.size,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
            boundarySnapshot.largestFileUtf8Bytes,
        )

        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        val fixture = loadFixture()
        var closeReport: CompilerHostCloseReport? = null
        var lastTerminatedGeneration = 0
        try {
            val baseline = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = fixture.snapshot(
                            fixture.getJSONObject("success")
                                .getString("mainSource"),
                        ),
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(baseline.errorMessage.orEmpty(), baseline.succeeded)
            val baselineRetiredSessionGeneration = requireNotNull(
                host.lastRetiredSessionGenerationForTesting(),
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())

            repeat(REPEATED_RESOURCE_CYCLE_COUNT) { cycleIndex ->
                val warm = executor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "The replacement Worker did not warm before timeout cycle " +
                        "${cycleIndex + 1}: ${warm.errorMessage}",
                    warm.succeeded,
                )

                val handle = host.newProjectOperationHandle()
                val compilation = executor.submit(
                    Callable {
                        host.compileProjectWithExpandedWorkerCapacityForTesting(
                            handle = handle,
                            snapshot = boundarySnapshot,
                            timeoutMs = NATURAL_INGRESS_TIMEOUT_MILLIS,
                            workerSpinMilliseconds = 0,
                            workerSpinPhase = null,
                        )
                    },
                )
                val liveTelemetry = waitForActiveInputCursor(
                    host = host,
                    handle = handle,
                    minimumAcceptedBytes = 1L,
                    timeoutMillis =
                        NATURAL_INGRESS_TIMEOUT_MILLIS * 2L,
                )
                assertEquals(handle.requestId, liveTelemetry.requestId)
                assertEquals(
                    R2ProjectExecutionPhase.INGRESS,
                    host.projectExecutionPhaseForTesting(handle),
                )
                assertFalse(liveTelemetry.complete)
                assertFalse(liveTelemetry.released)
                assertTrue(
                    liveTelemetry.acceptedPayloadBytes <
                        liveTelemetry.totalPayloadBytes,
                )

                val timedOut =
                    compilation.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertFalse(timedOut.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_TIMEOUT,
                    timedOut.errorCode,
                )
                assertEquals(null, timedOut.compilation)
                assertTrue(
                    "Natural ingress timeout fired before its fixed deadline.",
                    timedOut.elapsedMs >= NATURAL_INGRESS_TIMEOUT_MILLIS,
                )

                val terminalTelemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                )
                assertEquals(handle.requestId, terminalTelemetry.requestId)
                assertReleasedInputCursors(
                    telemetry = terminalTelemetry,
                    expectedComplete = false,
                )
                assertTrue(
                    "Natural ingress timeout did not accept any exact input.",
                    terminalTelemetry.acceptedPayloadBytes > 0L,
                )
                assertTrue(
                    "Natural ingress timeout completed the entire input unexpectedly.",
                    terminalTelemetry.acceptedPayloadBytes <
                        terminalTelemetry.totalPayloadBytes,
                )

                val terminatedGeneration = requireNotNull(
                    host.lastTerminatedWorkerGenerationForTesting(),
                )
                assertTrue(
                    "The repeated ingress timeout did not terminate a newer Worker.",
                    terminatedGeneration > lastTerminatedGeneration,
                )
                lastTerminatedGeneration = terminatedGeneration
                Log.i(
                    RESOURCE_LOG_TAG,
                    "INGRESS_TIMEOUT_CYCLE_PASS cycle=${cycleIndex + 1} " +
                        "requestId=${handle.requestId} " +
                        "timeoutMs=$NATURAL_INGRESS_TIMEOUT_MILLIS " +
                        "workerSpinMilliseconds=0 workerSpinPhase=none " +
                        "outcome=${timedOut.errorCode} compilation=none " +
                        "acceptedBytes=${terminalTelemetry.acceptedPayloadBytes} " +
                        "totalBytes=${terminalTelemetry.totalPayloadBytes} " +
                        "openedCursors=${terminalTelemetry.openedCursorCount} " +
                        "closedCursors=${terminalTelemetry.closedCursorCount} " +
                        "activeCursor=${terminalTelemetry.activeCursor} " +
                        "released=${terminalTelemetry.released} " +
                        "workerGeneration=$terminatedGeneration",
                )
            }

            boundarySnapshot.close()
            assertTrue(
                "Repeated natural-ingress timeout left a capacity spool file.",
                capacityDirectory.listFiles().orEmpty().none { file ->
                    file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
                },
            )

            val recovered = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = fixture.snapshot(
                            fixture.getJSONObject("success")
                                .getString("mainSource"),
                        ),
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
            assertTrue(
                "The small successor did not retire a newer WebView session.",
                requireNotNull(host.lastRetiredSessionGenerationForTesting()) >
                    baselineRetiredSessionGeneration,
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
            Log.i(
                RESOURCE_LOG_TAG,
                "INGRESS_TIMEOUT_MATRIX_PASS api=${Build.VERSION.SDK_INT} " +
                    "webView=" +
                    "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                    "cycles=$REPEATED_RESOURCE_CYCLE_COUNT " +
                    "callerBytes=$EXACT_CALLER_CAPACITY_BYTES " +
                    "timeoutMs=$NATURAL_INGRESS_TIMEOUT_MILLIS " +
                    "workerSpinMilliseconds=0 workerSpinPhase=none " +
                    "lastTerminatedWorkerGeneration=$lastTerminatedGeneration",
            )
        } finally {
            try {
                closeReport = onMain {
                    host.close()
                }
            } finally {
                executor.shutdownNow()
                boundarySnapshot.close()
            }
        }
        assertTrue(
            "Natural-ingress timeout host close did not prove cleanup.",
            requireNotNull(closeReport).cleanupProven,
        )
        assertTrue(
            "Natural-ingress timeout cleanup left a capacity spool file.",
            capacityDirectory.listFiles().orEmpty().none { file ->
                file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
            },
        )
    }

    @Test
    fun phaseEntryHardStopCoversCancelAndTimeoutAcrossAllWorkerPhases() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        try {
            fun warmReplacementWorker(label: String) {
                val warm = executor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "The replacement Worker did not warm before $label: " +
                        warm.errorMessage,
                    warm.succeeded,
                )
            }

            var lastTerminatedGeneration = 0
            R2ProjectExecutionPhase.ordered.forEach { phase ->
                warmReplacementWorker(
                    "${phase.protocolValue} cancellation proof",
                )
                val handle = host.newProjectOperationHandle()
                val compilation = executor.submit(
                    Callable {
                        host.compileProjectWithSynchronousWorkerSpinForTesting(
                            handle = handle,
                            snapshot = snapshot,
                            timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                            workerSpinMilliseconds =
                                SYNCHRONOUS_WORKER_SPIN_MILLIS,
                            workerSpinPhase = phase,
                        )
                    },
                )
                waitForExecutionPhase(host, handle, phase)
                assertEquals(
                    R2ProjectExecutionPhase.ordered.take(phase.ordinal + 1),
                    host.projectExecutionPhaseHistoryForTesting(handle),
                )
                assertTrue(
                    host.requestStop(
                        handle = handle,
                        cause = R2ProjectStopCause.CANCELLED,
                    ),
                )
                val cancelled =
                    compilation.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertFalse(cancelled.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_CANCELLED,
                    cancelled.errorCode,
                )
                assertEquals(null, cancelled.compilation)
                assertReleasedInputCursors(
                    telemetry = requireNotNull(
                        host.lastProjectInputTransportTelemetryForTesting(),
                    ),
                    expectedComplete = true,
                )
                val terminated = requireNotNull(
                    host.lastTerminatedWorkerGenerationForTesting(),
                )
                assertTrue(terminated > lastTerminatedGeneration)
                lastTerminatedGeneration = terminated
            }

            R2ProjectExecutionPhase.ordered.forEach { phase ->
                warmReplacementWorker(
                    "${phase.protocolValue} timeout proof",
                )
                val handle = host.newProjectOperationHandle()
                val compilation = executor.submit(
                    Callable {
                        host.compileProjectWithSynchronousWorkerSpinForTesting(
                            handle = handle,
                            snapshot = snapshot,
                            timeoutMs = PHASE_TIMEOUT_MILLIS,
                            workerSpinMilliseconds =
                                PHASE_TIMEOUT_SPIN_MILLIS,
                            workerSpinPhase = phase,
                        )
                    },
                )
                waitForExecutionPhase(host, handle, phase)
                val timedOut =
                    compilation.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertFalse(timedOut.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_TIMEOUT,
                    timedOut.errorCode,
                )
                assertEquals(null, timedOut.compilation)
                assertReleasedInputCursors(
                    telemetry = requireNotNull(
                        host.lastProjectInputTransportTelemetryForTesting(),
                    ),
                    expectedComplete = true,
                )
                val terminated = requireNotNull(
                    host.lastTerminatedWorkerGenerationForTesting(),
                )
                assertTrue(terminated > lastTerminatedGeneration)
                lastTerminatedGeneration = terminated
            }

            warmReplacementWorker("post-fault recovery")
            val recovered = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
            Log.i(
                PHASE_LOG_TAG,
                "PHASE_ENTRY_PASS api=${Build.VERSION.SDK_INT} " +
                    "webView=" +
                    "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                    "cancelPhases=${R2ProjectExecutionPhase.ordered.size} " +
                    "timeoutPhases=${R2ProjectExecutionPhase.ordered.size} " +
                    "timeoutMs=$PHASE_TIMEOUT_MILLIS " +
                    "lastTerminatedWorkerGeneration=$lastTerminatedGeneration",
            )
        } finally {
            onMain {
                host.close()
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun naturalParseSemanticEmitCancellationUsesRealProgressAndRecovers() {
        val spoolDirectory = context.getDir(
            NATURAL_PHASE_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val workload = R2NaturalPhaseWorkloadFixture.createSnapshot(
            directory = spoolDirectory,
            declarations = declarations,
        )
        assertEquals(
            R2NaturalPhaseWorkloadFixture.TOTAL_FILE_COUNT,
            workload.fileInventory.size,
        )
        assertEquals(
            R2NaturalPhaseWorkloadFixture.TOTAL_BYTES,
            workload.totalUtf8Bytes,
        )
        assertEquals(
            R2NaturalPhaseWorkloadFixture.rootNames,
            workload.rootNames,
        )

        val fixture = loadFixture()
        val successorSnapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        var closeReport: CompilerHostCloseReport? = null
        var lastTerminatedWorkerGeneration = 0
        try {
            fun warmWorker(label: String) {
                val warm = executor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "The natural-phase Worker did not warm before $label: " +
                        warm.errorMessage,
                    warm.succeeded,
                )
            }

            warmWorker("the full-workload baseline")
            val baseline = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = workload,
                        timeoutMs = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                    )
                },
            ).get(CAPACITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(
                "The natural-phase baseline did not compile: " +
                    baseline.errorMessage,
                baseline.succeeded,
            )
            val baselineCompilation = requireNotNull(baseline.compilation)
            assertTrue(baselineCompilation.diagnostics.isEmpty())
            assertFalse(baselineCompilation.emitSkipped)
            assertEquals(
                R2NaturalPhaseWorkloadFixture.ROOT_COUNT,
                baselineCompilation.executableModules.size,
            )
            assertEquals(
                R2NaturalPhaseWorkloadFixture.ROOT_COUNT,
                baselineCompilation.sourceMaps.size,
            )
            assertEquals(
                R2NaturalPhaseWorkloadFixture.OUTPUT_FILE_COUNT,
                baselineCompilation.outputs.size,
            )
            assertEquals(
                R2NaturalPhaseWorkloadFixture.JAVASCRIPT_OUTPUT_BYTES,
                R2NaturalPhaseWorkloadFixture.outputUtf8Bytes(
                    baselineCompilation.executableModules,
                ),
            )
            assertEquals(
                R2NaturalPhaseWorkloadFixture.SOURCE_MAP_OUTPUT_BYTES,
                R2NaturalPhaseWorkloadFixture.outputUtf8Bytes(
                    baselineCompilation.sourceMaps,
                ),
            )
            assertEquals(
                R2NaturalPhaseWorkloadFixture.OUTPUT_BYTES,
                R2NaturalPhaseWorkloadFixture.outputUtf8Bytes(
                    baselineCompilation.outputs,
                ),
            )
            assertEquals(
                R2NaturalPhaseWorkloadFixture.OUTPUT_AGGREGATE_SHA256,
                R2NaturalPhaseWorkloadFixture.aggregateOutputSha256(
                    baselineCompilation.outputs,
                ),
            )
            assertEquals(
                expectedNaturalPhaseProgressHistory(),
                host.lastProjectExecutionPhaseProgressHistoryForTesting(),
            )
            assertReleasedInputCursors(
                telemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                ),
                expectedComplete = true,
            )
            val baselineRetiredSessionGeneration = requireNotNull(
                host.lastRetiredSessionGenerationForTesting(),
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())

            listOf(
                R2ProjectExecutionPhase.PARSE,
                R2ProjectExecutionPhase.SEMANTIC,
                R2ProjectExecutionPhase.EMIT,
            ).forEach { phase ->
                warmWorker("${phase.protocolValue} cancellation")
                val handle = host.newProjectOperationHandle()
                val compilation = executor.submit(
                    Callable {
                        host.compileProject(
                            handle = handle,
                            snapshot = workload,
                            timeoutMs =
                                TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                        )
                    },
                )
                val selectedProgress = waitForExecutionPhaseProgress(
                    host = host,
                    handle = handle,
                    phase = phase,
                    sequence = NATURAL_PHASE_STOP_PROGRESS_SEQUENCE,
                )
                assertEquals(
                    expectedNaturalPhaseTotalUnits(phase),
                    selectedProgress.totalUnits,
                )
                assertEquals(
                    expectedNaturalPhaseCompletedUnits(
                        phase = phase,
                        sequence = NATURAL_PHASE_STOP_PROGRESS_SEQUENCE,
                    ),
                    selectedProgress.completedUnits,
                )
                assertTrue(
                    "Natural ${phase.protocolValue} progress reached completion.",
                    selectedProgress.completedUnits <
                        selectedProgress.totalUnits,
                )
                assertEquals(
                    R2ProjectExecutionPhase.ordered.take(phase.ordinal + 1),
                    host.projectExecutionPhaseHistoryForTesting(handle),
                )
                assertTrue(
                    "Natural ${phase.protocolValue} cancellation was not accepted.",
                    host.requestStop(
                        handle = handle,
                        cause = R2ProjectStopCause.CANCELLED,
                    ),
                )

                val cancelled = compilation.get(
                    CAPACITY_TEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
                assertFalse(cancelled.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_CANCELLED,
                    cancelled.errorCode,
                )
                assertEquals(null, cancelled.compilation)
                assertEquals(
                    expectedNaturalPhaseProgressHistory().take(
                        phase.ordinal * 2,
                    ),
                    host.lastProjectExecutionPhaseProgressHistoryForTesting(),
                )
                val inputTelemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                )
                assertEquals(handle.requestId, inputTelemetry.requestId)
                assertEquals(
                    R2NaturalPhaseWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.acceptedPayloadBytes,
                )
                assertEquals(
                    R2NaturalPhaseWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.totalPayloadBytes,
                )
                assertReleasedInputCursors(
                    telemetry = inputTelemetry,
                    expectedComplete = true,
                )

                val evidence = requireNotNull(
                    host.lastProjectPhaseTerminationEvidenceForTesting(),
                )
                assertEquals(handle.requestId, evidence.requestId)
                assertEquals(R2ProjectStopCause.CANCELLED, evidence.stopCause)
                assertEquals(phase, evidence.phaseAtHostStopSelection)
                assertEquals(phase, evidence.phaseAtPageTermination)
                assertEquals(
                    R2ProjectExecutionPhase.ordered.take(phase.ordinal + 1),
                    evidence.enteredPhasesAtHostStopSelection,
                )
                assertEquals(
                    R2ProjectExecutionPhase.ordered.take(phase.ordinal),
                    evidence.completedPhasesAtHostStopSelection,
                )
                assertFalse(
                    evidence.completedPhasesAtHostStopSelection.contains(phase),
                )
                assertEquals(
                    selectedProgress.sequence,
                    evidence.phaseProgressSequenceAtPageTermination,
                )
                assertEquals(
                    selectedProgress.completedUnits,
                    evidence.phaseProgressCompletedUnitsAtPageTermination,
                )
                assertEquals(
                    selectedProgress.totalUnits,
                    evidence.phaseProgressTotalUnitsAtPageTermination,
                )
                assertTrue(
                    evidence.phaseProgressCompletedUnitsAtPageTermination <
                        evidence.phaseProgressTotalUnitsAtPageTermination,
                )
                assertTrue(evidence.sessionGeneration > 0)
                assertEquals(
                    evidence.workerGeneration,
                    evidence.terminatedWorkerGeneration,
                )
                assertEquals(
                    evidence.terminatedWorkerGeneration + 1,
                    evidence.replacementWorkerGeneration,
                )
                assertTrue(
                    evidence.deadlineAtElapsedRealtimeMillis >
                        evidence.stopSelectedAtElapsedRealtimeMillis,
                )
                assertTrue(
                    evidence.deadlineAtElapsedRealtimeNanos >
                        evidence.stopSelectedAtElapsedRealtimeNanos,
                )
                val terminatedWorkerGeneration = requireNotNull(
                    host.lastTerminatedWorkerGenerationForTesting(),
                )
                assertEquals(
                    evidence.terminatedWorkerGeneration,
                    terminatedWorkerGeneration,
                )
                assertTrue(
                    "Natural phase cancellation did not terminate a newer Worker.",
                    terminatedWorkerGeneration >
                        lastTerminatedWorkerGeneration,
                )
                lastTerminatedWorkerGeneration = terminatedWorkerGeneration
                Log.i(
                    NATURAL_PHASE_LOG_TAG,
                    "NATURAL_PHASE_CANCEL_PASS " +
                        "phase=${phase.protocolValue} " +
                        "requestId=${handle.requestId} " +
                        "progressSequence=${evidence.sequence} " +
                        "completedUnits=${evidence.completedUnits} " +
                        "totalUnits=${evidence.totalUnits} " +
                        "stopCause=${evidence.stopCause} " +
                        "outcome=${cancelled.errorCode} compilation=none " +
                        "workerSpinMilliseconds=0 workerSpinPhase=none " +
                        "openedCursors=${inputTelemetry.openedCursorCount} " +
                        "closedCursors=${inputTelemetry.closedCursorCount} " +
                        "released=${inputTelemetry.released} " +
                        "terminatedWorkerGeneration=" +
                        "${evidence.terminatedWorkerGeneration} " +
                        "replacementWorkerGeneration=" +
                        evidence.replacementWorkerGeneration,
                )
            }

            workload.close()
            assertTrue(
                "Natural phase cancellation left a project spool file.",
                spoolDirectory.listFiles().orEmpty().none { file ->
                    file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
                },
            )

            val successor = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = successorSnapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(successor.errorMessage.orEmpty(), successor.succeeded)
            assertTrue(
                "The natural-phase successor did not retire a newer session.",
                requireNotNull(host.lastRetiredSessionGenerationForTesting()) >
                    baselineRetiredSessionGeneration,
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
        } finally {
            try {
                closeReport = onMain {
                    host.close()
                }
            } finally {
                executor.shutdownNow()
                workload.close()
                successorSnapshot.close()
            }
        }
        assertTrue(
            "Natural phase cancellation host close did not prove cleanup.",
            requireNotNull(closeReport).cleanupProven,
        )
        assertTrue(
            "Natural phase cancellation cleanup left a project spool file.",
            spoolDirectory.listFiles().orEmpty().none { file ->
                file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
            },
        )
        Log.i(
            NATURAL_PHASE_LOG_TAG,
            "NATURAL_PHASE_CANCEL_MATRIX_PASS " +
                "api=${Build.VERSION.SDK_INT} " +
                "webView=" +
                "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                "phases=3 roots=${R2NaturalPhaseWorkloadFixture.ROOT_COUNT} " +
                "files=${R2NaturalPhaseWorkloadFixture.TOTAL_FILE_COUNT} " +
                "bytes=${R2NaturalPhaseWorkloadFixture.TOTAL_BYTES} " +
                "tokens=${R2NaturalPhaseWorkloadFixture.TOTAL_TOKENS} " +
                "outputBytes=${R2NaturalPhaseWorkloadFixture.OUTPUT_BYTES} " +
                "ordinaryAdmission=true " +
                "workerSpinMilliseconds=0 workerSpinPhase=none " +
                "hostCleanupProven=true spoolCleanupProven=true " +
                "lastTerminatedWorkerGeneration=" +
                lastTerminatedWorkerGeneration,
        )
    }

    @Test
    fun naturalSemanticRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers() {
        val spoolDirectory = context.getDir(
            NATURAL_PHASE_TIMEOUT_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val workload = R2NaturalPhaseTimeoutWorkloadFixture.createSnapshot(
            directory = spoolDirectory,
            declarations = declarations,
        )
        assertEquals(
            R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
            workload.fileInventory.size,
        )
        assertEquals(
            R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_BYTES,
            workload.totalUtf8Bytes,
        )
        assertEquals(
            R2NaturalPhaseTimeoutWorkloadFixture.rootNames,
            workload.rootNames,
        )

        val fixture = loadFixture()
        val successorSnapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        val observedNaturalPhases = listOf(
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        )
        val timeoutPhases = listOf(R2ProjectExecutionPhase.SEMANTIC)
        var closeReport: CompilerHostCloseReport? = null
        var baselineRetiredSessionGeneration = 0
        var timeoutSessionGeneration: Int? = null
        var expectedNextWorkerGeneration: Int? = null
        var calibrations:
            Map<R2ProjectExecutionPhase, NaturalPhaseDeadlineCalibration> =
            emptyMap()
        try {
            fun warmWorker(label: String) {
                val warm = executor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "The natural-phase timeout Worker did not warm before $label: " +
                        warm.errorMessage,
                    warm.succeeded,
                )
            }

            val calibrationRuns =
                mutableListOf<List<R2ProjectExecutionPhaseTimingEvidence>>()
            repeat(NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS) { runIndex ->
                warmWorker("deadline calibration ${runIndex + 1}")
                val baseline = executor.submit(
                    Callable {
                        host.compileProject(
                            snapshot = workload,
                            timeoutMs =
                                TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                        )
                    },
                ).get(CAPACITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "Natural-phase deadline calibration ${runIndex + 1} " +
                        "did not compile: ${baseline.errorMessage}",
                    baseline.succeeded,
                )
                val compilation = requireNotNull(baseline.compilation)
                assertTrue(compilation.diagnostics.isEmpty())
                assertFalse(compilation.emitSkipped)
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.OUTPUT_FILE_COUNT,
                    compilation.outputs.size,
                )
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.OUTPUT_BYTES,
                    R2NaturalPhaseTimeoutWorkloadFixture.outputUtf8Bytes(
                        compilation.outputs,
                    ),
                )
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture
                        .OUTPUT_AGGREGATE_SHA256,
                    R2NaturalPhaseTimeoutWorkloadFixture
                        .aggregateOutputSha256(
                        compilation.outputs,
                    ),
                )
                assertEquals(
                    expectedNaturalPhaseTimeoutProgressHistory(),
                    host.lastProjectExecutionPhaseProgressHistoryForTesting(),
                )
                val timingEvidence =
                    host.lastProjectExecutionPhaseTimingEvidenceForTesting()
                assertEquals(
                    observedNaturalPhases,
                    timingEvidence.map(
                        R2ProjectExecutionPhaseTimingEvidence::phase,
                    ),
                )
                timingEvidence.forEach { timing ->
                    assertTrue(
                        "Natural ${timing.phase.protocolValue} sequence two " +
                            "was not observed after project start.",
                        timing
                            .sequenceTwoElapsedRealtimeNanosSinceProjectStart >
                            0L,
                    )
                    assertTrue(
                        "Natural ${timing.phase.protocolValue} completion did " +
                            "not follow sequence two.",
                        timing.completedElapsedRealtimeNanosSinceProjectStart >
                            timing
                                .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                    )
                }
                calibrationRuns += timingEvidence
                val baselineInputTelemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                )
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    baselineInputTelemetry.openedCursorCount,
                )
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    baselineInputTelemetry.closedCursorCount,
                )
                assertReleasedInputCursors(
                    telemetry = baselineInputTelemetry,
                    expectedComplete = true,
                )
                baselineRetiredSessionGeneration = requireNotNull(
                    host.lastRetiredSessionGenerationForTesting(),
                )
                assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
                Log.i(
                    NATURAL_PHASE_TIMEOUT_LOG_TAG,
                    "NATURAL_PHASE_TIMEOUT_BASELINE_PASS " +
                        "run=${runIndex + 1} " +
                        "phases=${timingEvidence.size} " +
                        "timing=" +
                        timingEvidence.joinToString(separator = ",") { timing ->
                            "${timing.phase.protocolValue}:" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                                ) +
                                "/" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .completedElapsedRealtimeNanosSinceProjectStart,
                                )
                        } +
                        "ms " +
                        "outputs=${compilation.outputs.size} " +
                        "outputBytes=" +
                        R2NaturalPhaseTimeoutWorkloadFixture.OUTPUT_BYTES,
                )
            }

            calibrations = calibrateNaturalPhaseDeadlines(
                phases = timeoutPhases,
                timingRuns = calibrationRuns,
            )
            check(NATURAL_PHASE_TIMEOUT_FAULT_ATTEMPTS == 1) {
                "Natural phase deadline evidence must not retry a fault run."
            }

            timeoutPhases.forEach { phase ->
                warmWorker("${phase.protocolValue} absolute deadline")
                val calibration = requireNotNull(calibrations[phase])
                val handle = host.newProjectOperationHandle()
                val wallStartedAtNanos = System.nanoTime()
                val timedOut = executor.submit(
                    Callable {
                        host.compileProject(
                            handle = handle,
                            snapshot = workload,
                            timeoutMs = calibration.timeoutMillis,
                        )
                    },
                ).get(CAPACITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                val wallElapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - wallStartedAtNanos,
                )

                assertFalse(timedOut.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_TIMEOUT,
                    timedOut.errorCode,
                )
                assertEquals(null, timedOut.compilation)
                assertTrue(
                    "Natural ${phase.protocolValue} timeout fired before its " +
                        "request-start absolute deadline.",
                    timedOut.elapsedMs >= calibration.timeoutMillis &&
                        wallElapsedMillis >= calibration.timeoutMillis,
                )
                assertEquals(
                    expectedNaturalPhaseTimeoutProgressHistory().take(
                        phase.ordinal * 2,
                    ),
                    host.lastProjectExecutionPhaseProgressHistoryForTesting(),
                )
                assertEquals(
                    observedNaturalPhases.takeWhile { observed ->
                        observed != phase
                    },
                    host.lastProjectExecutionPhaseTimingEvidenceForTesting()
                        .map(R2ProjectExecutionPhaseTimingEvidence::phase),
                )

                val inputTelemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                )
                assertEquals(handle.requestId, inputTelemetry.requestId)
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.acceptedPayloadBytes,
                )
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.totalPayloadBytes,
                )
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    inputTelemetry.openedCursorCount,
                )
                assertEquals(
                    R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    inputTelemetry.closedCursorCount,
                )
                assertReleasedInputCursors(
                    telemetry = inputTelemetry,
                    expectedComplete = true,
                )

                val evidence = requireNotNull(
                    host.lastProjectPhaseTerminationEvidenceForTesting(),
                )
                assertEquals(handle.requestId, evidence.requestId)
                assertEquals(R2ProjectStopCause.TIMED_OUT, evidence.stopCause)
                assertEquals(phase, evidence.phaseAtHostStopSelection)
                assertEquals(phase, evidence.phaseAtPageTermination)
                assertEquals(
                    R2ProjectExecutionPhase.ordered.take(phase.ordinal + 1),
                    evidence.enteredPhasesAtHostStopSelection,
                )
                assertEquals(
                    R2ProjectExecutionPhase.ordered.take(phase.ordinal),
                    evidence.completedPhasesAtHostStopSelection,
                )
                assertFalse(
                    evidence.completedPhasesAtHostStopSelection.contains(phase),
                )
                assertEquals(
                    NATURAL_PHASE_TIMEOUT_PROGRESS_SEQUENCE,
                    evidence.phaseProgressSequenceAtPageTermination,
                )
                assertEquals(
                    expectedNaturalPhaseTimeoutCompletedUnits(
                        phase = phase,
                        sequence = NATURAL_PHASE_TIMEOUT_PROGRESS_SEQUENCE,
                    ),
                    evidence.phaseProgressCompletedUnitsAtPageTermination,
                )
                assertEquals(
                    expectedNaturalPhaseTimeoutTotalUnits(phase),
                    evidence.phaseProgressTotalUnitsAtPageTermination,
                )
                assertTrue(
                    evidence.phaseProgressCompletedUnitsAtPageTermination <
                        evidence.phaseProgressTotalUnitsAtPageTermination,
                )
                assertTrue(
                    "Natural ${phase.protocolValue} stop selection preceded " +
                        "the absolute deadline.",
                    evidence.stopSelectedAtElapsedRealtimeNanos >=
                        evidence.deadlineAtElapsedRealtimeNanos,
                )
                val deadlineOvershootMillis = TimeUnit.NANOSECONDS.toMillis(
                    evidence.stopSelectedAtElapsedRealtimeNanos -
                        evidence.deadlineAtElapsedRealtimeNanos,
                )
                assertTrue(
                    "Natural ${phase.protocolValue} deadline selection " +
                        "overshoot was unbounded: $deadlineOvershootMillis ms.",
                    deadlineOvershootMillis in
                        0..NATURAL_PHASE_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS,
                )
                assertTrue(evidence.sessionGeneration > 0)
                timeoutSessionGeneration?.let { sessionGeneration ->
                    assertEquals(sessionGeneration, evidence.sessionGeneration)
                }
                timeoutSessionGeneration = evidence.sessionGeneration
                expectedNextWorkerGeneration?.let { workerGeneration ->
                    assertEquals(workerGeneration, evidence.workerGeneration)
                }
                assertEquals(
                    evidence.workerGeneration,
                    evidence.terminatedWorkerGeneration,
                )
                assertEquals(
                    evidence.terminatedWorkerGeneration + 1,
                    evidence.replacementWorkerGeneration,
                )
                expectedNextWorkerGeneration =
                    evidence.replacementWorkerGeneration
                assertEquals(
                    evidence.terminatedWorkerGeneration,
                    host.lastTerminatedWorkerGenerationForTesting(),
                )

                Log.i(
                    NATURAL_PHASE_TIMEOUT_LOG_TAG,
                    "NATURAL_PHASE_TIMEOUT_PASS " +
                        "phase=${phase.protocolValue} " +
                        "requestId=${handle.requestId} " +
                        "calibratedTimeoutMs=${calibration.timeoutMillis} " +
                        "calibrationLowerMs=${calibration.minimumTimeoutMillis} " +
                        "calibrationUpperMs=${calibration.maximumTimeoutMillis} " +
                        "progressSequence=${evidence.sequence} " +
                        "completedUnits=${evidence.completedUnits} " +
                        "totalUnits=${evidence.totalUnits} " +
                        "stopCause=${evidence.stopCause} " +
                        "outcome=${timedOut.errorCode} compilation=none " +
                        "deadlineOvershootMs=$deadlineOvershootMillis " +
                        "workerSpinMilliseconds=0 workerSpinPhase=none " +
                        "openedCursors=${inputTelemetry.openedCursorCount} " +
                        "closedCursors=${inputTelemetry.closedCursorCount} " +
                        "released=${inputTelemetry.released} " +
                        "sessionGeneration=${evidence.sessionGeneration} " +
                        "terminatedWorkerGeneration=" +
                        "${evidence.terminatedWorkerGeneration} " +
                        "replacementWorkerGeneration=" +
                        evidence.replacementWorkerGeneration,
                )
            }

            workload.close()
            assertTrue(
                "Natural phase timeout left a project spool file.",
                spoolDirectory.listFiles().orEmpty().none { file ->
                    file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
                },
            )

            val successor = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = successorSnapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(successor.errorMessage.orEmpty(), successor.succeeded)
            assertTrue(
                "The natural-phase timeout successor did not retire a newer session.",
                requireNotNull(host.lastRetiredSessionGenerationForTesting()) >
                    baselineRetiredSessionGeneration,
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
        } finally {
            try {
                closeReport = onMain {
                    host.close()
                }
            } finally {
                executor.shutdownNow()
                workload.close()
                successorSnapshot.close()
            }
        }
        assertTrue(
            "Natural phase timeout host close did not prove cleanup.",
            requireNotNull(closeReport).cleanupProven,
        )
        assertTrue(
            "Natural phase timeout cleanup left a project spool file.",
            spoolDirectory.listFiles().orEmpty().none { file ->
                file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
            },
        )
        Log.i(
            NATURAL_PHASE_TIMEOUT_LOG_TAG,
            "NATURAL_PHASE_TIMEOUT_MATRIX_PASS " +
                "api=${Build.VERSION.SDK_INT} " +
                "webView=" +
                "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                "phases=1 calibrationRuns=" +
                "$NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS " +
                "semanticTimeoutMs=" +
                "${calibrations[R2ProjectExecutionPhase.SEMANTIC]?.timeoutMillis} " +
                "roots=${R2NaturalPhaseTimeoutWorkloadFixture.ROOT_COUNT} " +
                "files=${R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_FILE_COUNT} " +
                "bytes=${R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_BYTES} " +
                "tokens=${R2NaturalPhaseTimeoutWorkloadFixture.TOTAL_TOKENS} " +
                "ordinaryAdmission=true requestStartAbsoluteDeadline=true " +
                "workerSpinMilliseconds=0 workerSpinPhase=none " +
                "hostCleanupProven=true spoolCleanupProven=true " +
                "lastReplacementWorkerGeneration=" +
                expectedNextWorkerGeneration,
        )
    }

    @Test
    fun naturalParseRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers() {
        val spoolDirectory = context.getDir(
            NATURAL_PARSE_TIMEOUT_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val workload = R2NaturalParseTimeoutWorkloadFixture.createSnapshot(
            directory = spoolDirectory,
            declarations = declarations,
        )
        assertEquals(
            R2NaturalParseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
            workload.fileInventory.size,
        )
        assertEquals(
            R2NaturalParseTimeoutWorkloadFixture.TOTAL_BYTES,
            workload.totalUtf8Bytes,
        )
        assertEquals(
            R2NaturalParseTimeoutWorkloadFixture.rootNames,
            workload.rootNames,
        )

        val fixture = loadFixture()
        val successorSnapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        val observedNaturalPhases = listOf(
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        )
        var closeReport: CompilerHostCloseReport? = null
        var baselineRetiredSessionGeneration = 0
        var calibration: NaturalPhaseDeadlineCalibration? = null
        var replacementWorkerGeneration: Int? = null
        try {
            fun warmWorker(label: String) {
                val warm = executor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "The natural-parse timeout Worker did not warm before " +
                        "$label: ${warm.errorMessage}",
                    warm.succeeded,
                )
            }

            fun successfulNaturalParseRun(
                label: String,
            ): Pair<
                R2ProjectCompilation,
                List<R2ProjectExecutionPhaseTimingEvidence>,
                > {
                warmWorker(label)
                val result = executor.submit(
                    Callable {
                        host.compileProject(
                            snapshot = workload,
                            timeoutMs =
                                TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                        )
                    },
                ).get(CAPACITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "Natural-parse successful run $label did not compile: " +
                        result.errorMessage,
                    result.succeeded,
                )
                val compilation = requireNotNull(result.compilation)
                assertTrue(compilation.diagnostics.isEmpty())
                assertFalse(compilation.emitSkipped)
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture.OUTPUT_FILE_COUNT,
                    compilation.outputs.size,
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture
                        .JAVASCRIPT_OUTPUT_BYTES,
                    R2NaturalParseTimeoutWorkloadFixture.outputUtf8Bytes(
                        compilation.executableModules,
                    ),
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture
                        .SOURCE_MAP_OUTPUT_BYTES,
                    R2NaturalParseTimeoutWorkloadFixture.outputUtf8Bytes(
                        compilation.sourceMaps,
                    ),
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture.OUTPUT_BYTES,
                    R2NaturalParseTimeoutWorkloadFixture.outputUtf8Bytes(
                        compilation.outputs,
                    ),
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture
                        .OUTPUT_AGGREGATE_SHA256,
                    R2NaturalParseTimeoutWorkloadFixture
                        .aggregateOutputSha256(
                            compilation.outputs,
                        ),
                )
                assertEquals(
                    expectedNaturalParseTimeoutProgressHistory(),
                    host.lastProjectExecutionPhaseProgressHistoryForTesting(),
                )
                val timingEvidence =
                    host.lastProjectExecutionPhaseTimingEvidenceForTesting()
                assertEquals(
                    observedNaturalPhases,
                    timingEvidence.map(
                        R2ProjectExecutionPhaseTimingEvidence::phase,
                    ),
                )
                timingEvidence.forEach { timing ->
                    assertTrue(
                        "Natural-parse $label ${timing.phase.protocolValue} " +
                            "sequence two was not observed after project start.",
                        timing
                            .sequenceTwoElapsedRealtimeNanosSinceProjectStart >
                            0L,
                    )
                    assertTrue(
                        "Natural-parse $label ${timing.phase.protocolValue} " +
                            "completion did not follow sequence two.",
                        timing.completedElapsedRealtimeNanosSinceProjectStart >
                            timing
                                .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                    )
                }

                val inputTelemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.acceptedPayloadBytes,
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.totalPayloadBytes,
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture
                        .INPUT_TRANSPORT_FRAME_COUNT,
                    inputTelemetry.sentFrameCount,
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    inputTelemetry.openedCursorCount,
                )
                assertEquals(
                    R2NaturalParseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    inputTelemetry.closedCursorCount,
                )
                assertReleasedInputCursors(
                    telemetry = inputTelemetry,
                    expectedComplete = true,
                )
                baselineRetiredSessionGeneration = requireNotNull(
                    host.lastRetiredSessionGenerationForTesting(),
                )
                assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
                return Pair(compilation, timingEvidence)
            }

            check(NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS == 2) {
                "Natural parse evidence must use exactly two successful " +
                    "precondition runs."
            }
            repeat(NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS) {
                    preconditionIndex ->
                val (preconditionCompilation, preconditionTiming) =
                    successfulNaturalParseRun(
                        "calibration precondition ${preconditionIndex + 1}",
                    )
                Log.i(
                    NATURAL_PARSE_TIMEOUT_LOG_TAG,
                    "NATURAL_PARSE_TIMEOUT_PRECONDITION_PASS " +
                        "run=${preconditionIndex + 1} " +
                        "runs=$NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS " +
                        "phases=${preconditionTiming.size} timing=" +
                        preconditionTiming.joinToString(
                            separator = ",",
                        ) { timing ->
                            "${timing.phase.protocolValue}:" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                                ) +
                                "/" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .completedElapsedRealtimeNanosSinceProjectStart,
                                )
                        } +
                        "ms outputs=${preconditionCompilation.outputs.size} " +
                        "outputBytes=" +
                        R2NaturalParseTimeoutWorkloadFixture.OUTPUT_BYTES,
                )
            }

            val calibrationRuns =
                mutableListOf<List<R2ProjectExecutionPhaseTimingEvidence>>()
            repeat(NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS) { runIndex ->
                val (compilation, timingEvidence) =
                    successfulNaturalParseRun(
                        "parse deadline calibration ${runIndex + 1}",
                    )
                calibrationRuns += timingEvidence
                Log.i(
                    NATURAL_PARSE_TIMEOUT_LOG_TAG,
                    "NATURAL_PARSE_TIMEOUT_BASELINE_PASS " +
                        "run=${runIndex + 1} phases=${timingEvidence.size} " +
                        "timing=" +
                        timingEvidence.joinToString(separator = ",") { timing ->
                            "${timing.phase.protocolValue}:" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                                ) +
                                "/" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .completedElapsedRealtimeNanosSinceProjectStart,
                                )
                        } +
                        "ms outputs=${compilation.outputs.size} " +
                        "outputBytes=" +
                        R2NaturalParseTimeoutWorkloadFixture.OUTPUT_BYTES,
                )
            }

            calibration = calibrateNaturalParseDeadline(calibrationRuns)
            check(NATURAL_PARSE_TIMEOUT_FAULT_ATTEMPTS == 1) {
                "Natural parse deadline evidence must not retry its fault run."
            }

            warmWorker("the parse absolute deadline")
            val selectedCalibration = requireNotNull(calibration)
            val handle = host.newProjectOperationHandle()
            val wallStartedAtNanos = System.nanoTime()
            val timedOut = executor.submit(
                Callable {
                    host.compileProject(
                        handle = handle,
                        snapshot = workload,
                        timeoutMs = selectedCalibration.timeoutMillis,
                    )
                },
            ).get(CAPACITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val wallElapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - wallStartedAtNanos,
            )

            assertFalse(timedOut.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_TIMEOUT,
                timedOut.errorCode,
            )
            assertEquals(null, timedOut.compilation)
            assertTrue(
                "Natural parse timeout fired before its request-start " +
                    "absolute deadline.",
                timedOut.elapsedMs >= selectedCalibration.timeoutMillis &&
                    wallElapsedMillis >= selectedCalibration.timeoutMillis,
            )
            assertEquals(
                expectedNaturalParseTimeoutProgressHistory().take(2),
                host.lastProjectExecutionPhaseProgressHistoryForTesting(),
            )
            assertTrue(
                host.lastProjectExecutionPhaseTimingEvidenceForTesting()
                    .isEmpty(),
            )

            val inputTelemetry = requireNotNull(
                host.lastProjectInputTransportTelemetryForTesting(),
            )
            assertEquals(handle.requestId, inputTelemetry.requestId)
            assertEquals(
                R2NaturalParseTimeoutWorkloadFixture.TOTAL_BYTES,
                inputTelemetry.acceptedPayloadBytes,
            )
            assertEquals(
                R2NaturalParseTimeoutWorkloadFixture.TOTAL_BYTES,
                inputTelemetry.totalPayloadBytes,
            )
            assertEquals(
                R2NaturalParseTimeoutWorkloadFixture
                    .INPUT_TRANSPORT_FRAME_COUNT,
                inputTelemetry.sentFrameCount,
            )
            assertEquals(
                R2NaturalParseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                inputTelemetry.openedCursorCount,
            )
            assertEquals(
                R2NaturalParseTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                inputTelemetry.closedCursorCount,
            )
            assertReleasedInputCursors(
                telemetry = inputTelemetry,
                expectedComplete = true,
            )

            val evidence = requireNotNull(
                host.lastProjectPhaseTerminationEvidenceForTesting(),
            )
            assertEquals(handle.requestId, evidence.requestId)
            assertEquals(R2ProjectStopCause.TIMED_OUT, evidence.stopCause)
            assertEquals(
                R2ProjectExecutionPhase.PARSE,
                evidence.phaseAtHostStopSelection,
            )
            assertEquals(
                R2ProjectExecutionPhase.PARSE,
                evidence.phaseAtPageTermination,
            )
            assertEquals(
                listOf(
                    R2ProjectExecutionPhase.INGRESS,
                    R2ProjectExecutionPhase.PARSE,
                ),
                evidence.enteredPhasesAtHostStopSelection,
            )
            assertEquals(
                listOf(R2ProjectExecutionPhase.INGRESS),
                evidence.completedPhasesAtHostStopSelection,
            )
            assertEquals(
                NATURAL_PARSE_TIMEOUT_PROGRESS_SEQUENCE,
                evidence.phaseProgressSequenceAtPageTermination,
            )
            assertEquals(
                expectedNaturalParseTimeoutCompletedUnits(
                    phase = R2ProjectExecutionPhase.PARSE,
                    sequence = NATURAL_PARSE_TIMEOUT_PROGRESS_SEQUENCE,
                ),
                evidence.phaseProgressCompletedUnitsAtPageTermination,
            )
            assertEquals(
                R2NaturalParseTimeoutWorkloadFixture.ROOT_COUNT,
                evidence.phaseProgressTotalUnitsAtPageTermination,
            )
            assertTrue(
                evidence.phaseProgressCompletedUnitsAtPageTermination <
                    evidence.phaseProgressTotalUnitsAtPageTermination,
            )
            assertTrue(
                "Natural parse stop selection preceded the absolute deadline.",
                evidence.stopSelectedAtElapsedRealtimeNanos >=
                    evidence.deadlineAtElapsedRealtimeNanos,
            )
            val deadlineOvershootMillis = TimeUnit.NANOSECONDS.toMillis(
                evidence.stopSelectedAtElapsedRealtimeNanos -
                    evidence.deadlineAtElapsedRealtimeNanos,
            )
            assertTrue(
                "Natural parse deadline selection overshoot was unbounded: " +
                    "$deadlineOvershootMillis ms.",
                deadlineOvershootMillis in
                    0..NATURAL_PARSE_TIMEOUT_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS,
            )
            assertTrue(evidence.sessionGeneration > 0)
            assertEquals(
                evidence.workerGeneration,
                evidence.terminatedWorkerGeneration,
            )
            assertEquals(
                evidence.terminatedWorkerGeneration + 1,
                evidence.replacementWorkerGeneration,
            )
            replacementWorkerGeneration = evidence.replacementWorkerGeneration
            assertEquals(
                evidence.terminatedWorkerGeneration,
                host.lastTerminatedWorkerGenerationForTesting(),
            )
            Log.i(
                NATURAL_PARSE_TIMEOUT_LOG_TAG,
                "NATURAL_PARSE_TIMEOUT_PASS " +
                    "phase=parse requestId=${handle.requestId} " +
                    "calibratedTimeoutMs=${selectedCalibration.timeoutMillis} " +
                    "calibrationLowerMs=" +
                    "${selectedCalibration.minimumTimeoutMillis} " +
                    "calibrationUpperMs=" +
                    "${selectedCalibration.maximumTimeoutMillis} " +
                    "progressSequence=${evidence.sequence} " +
                    "completedUnits=${evidence.completedUnits} " +
                    "totalUnits=${evidence.totalUnits} " +
                    "stopCause=${evidence.stopCause} " +
                    "outcome=${timedOut.errorCode} compilation=none " +
                    "deadlineOvershootMs=$deadlineOvershootMillis " +
                    "workerSpinMilliseconds=0 workerSpinPhase=none " +
                    "inputFrames=${inputTelemetry.sentFrameCount} " +
                    "openedCursors=${inputTelemetry.openedCursorCount} " +
                    "closedCursors=${inputTelemetry.closedCursorCount} " +
                    "released=${inputTelemetry.released} " +
                    "sessionGeneration=${evidence.sessionGeneration} " +
                    "terminatedWorkerGeneration=" +
                    "${evidence.terminatedWorkerGeneration} " +
                    "replacementWorkerGeneration=" +
                    evidence.replacementWorkerGeneration,
            )

            workload.close()
            assertTrue(
                "Natural parse timeout left a project spool file.",
                spoolDirectory.listFiles().orEmpty().none { file ->
                    file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
                },
            )

            val successor = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = successorSnapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(successor.errorMessage.orEmpty(), successor.succeeded)
            assertTrue(
                "The natural parse timeout successor did not retire a newer " +
                    "session.",
                requireNotNull(host.lastRetiredSessionGenerationForTesting()) >
                    baselineRetiredSessionGeneration,
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
        } finally {
            try {
                closeReport = onMain {
                    host.close()
                }
            } finally {
                executor.shutdownNow()
                workload.close()
                successorSnapshot.close()
            }
        }
        assertTrue(
            "Natural parse timeout host close did not prove cleanup.",
            requireNotNull(closeReport).cleanupProven,
        )
        assertTrue(
            "Natural parse timeout cleanup left a project spool file.",
            spoolDirectory.listFiles().orEmpty().none { file ->
                file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
            },
        )
        Log.i(
            NATURAL_PARSE_TIMEOUT_LOG_TAG,
            "NATURAL_PARSE_TIMEOUT_MATRIX_PASS " +
                "api=${Build.VERSION.SDK_INT} " +
                "webView=" +
                "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                "phases=1 preconditionRuns=" +
                "$NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS " +
                "calibrationRuns=" +
                "$NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS " +
                "parseTimeoutMs=${calibration?.timeoutMillis} " +
                "roots=${R2NaturalParseTimeoutWorkloadFixture.ROOT_COUNT} " +
                "files=" +
                "${R2NaturalParseTimeoutWorkloadFixture.TOTAL_FILE_COUNT} " +
                "bytes=${R2NaturalParseTimeoutWorkloadFixture.TOTAL_BYTES} " +
                "tokens=${R2NaturalParseTimeoutWorkloadFixture.TOTAL_TOKENS} " +
                "inputFrames=" +
                "${R2NaturalParseTimeoutWorkloadFixture.INPUT_TRANSPORT_FRAME_COUNT} " +
                "pathDepth=" +
                "${R2NaturalParseTimeoutWorkloadFixture.SOURCE_PATH_DEPTH} " +
                "resolverDepth=" +
                "${R2NaturalParseTimeoutWorkloadFixture.SOURCE_DIRECTORY_DEPTH} " +
                "ordinaryAdmission=true requestStartAbsoluteDeadline=true " +
                "workerSpinMilliseconds=0 workerSpinPhase=none " +
                "hostCleanupProven=true spoolCleanupProven=true " +
                "lastReplacementWorkerGeneration=" +
                replacementWorkerGeneration,
        )
    }

    @Test
    fun naturalEmitRequestStartAbsoluteDeadlineTimeoutUsesCalibratedWindowAndRecovers() {
        val spoolDirectory = context.getDir(
            NATURAL_EMIT_TIMEOUT_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val workload = R2NaturalEmitTimeoutWorkloadFixture.createSnapshot(
            directory = spoolDirectory,
            declarations = declarations,
        )
        assertEquals(
            R2NaturalEmitTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
            workload.fileInventory.size,
        )
        assertEquals(
            R2NaturalEmitTimeoutWorkloadFixture.TOTAL_BYTES,
            workload.totalUtf8Bytes,
        )
        assertEquals(
            R2NaturalEmitTimeoutWorkloadFixture.rootNames,
            workload.rootNames,
        )

        val fixture = loadFixture()
        val successorSnapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        val observedNaturalPhases = listOf(
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        )
        var closeReport: CompilerHostCloseReport? = null
        var baselineRetiredSessionGeneration = 0
        var calibration: NaturalPhaseDeadlineCalibration? = null
        var replacementWorkerGeneration: Int? = null
        try {
            fun warmWorker(label: String) {
                val warm = executor.submit(
                    Callable {
                        host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "The natural-emit timeout Worker did not warm before " +
                        "$label: ${warm.errorMessage}",
                    warm.succeeded,
                )
            }

            fun successfulNaturalEmitRun(
                label: String,
            ): Pair<
                R2ProjectCompilation,
                List<R2ProjectExecutionPhaseTimingEvidence>,
                > {
                warmWorker(label)
                val result = executor.submit(
                    Callable {
                        host.compileProject(
                            snapshot = workload,
                            timeoutMs =
                                TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                        )
                    },
                ).get(CAPACITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(
                    "Natural-emit successful run $label did not compile: " +
                        result.errorMessage,
                    result.succeeded,
                )
                val compilation = requireNotNull(result.compilation)
                assertTrue(compilation.diagnostics.isEmpty())
                assertFalse(compilation.emitSkipped)
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture.OUTPUT_FILE_COUNT,
                    compilation.outputs.size,
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture
                        .JAVASCRIPT_OUTPUT_BYTES,
                    R2NaturalEmitTimeoutWorkloadFixture.outputUtf8Bytes(
                        compilation.executableModules,
                    ),
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture
                        .SOURCE_MAP_OUTPUT_BYTES,
                    R2NaturalEmitTimeoutWorkloadFixture.outputUtf8Bytes(
                        compilation.sourceMaps,
                    ),
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture.OUTPUT_BYTES,
                    R2NaturalEmitTimeoutWorkloadFixture.outputUtf8Bytes(
                        compilation.outputs,
                    ),
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture
                        .OUTPUT_AGGREGATE_SHA256,
                    R2NaturalEmitTimeoutWorkloadFixture
                        .aggregateOutputSha256(
                            compilation.outputs,
                        ),
                )
                assertEquals(
                    expectedNaturalEmitTimeoutProgressHistory(),
                    host.lastProjectExecutionPhaseProgressHistoryForTesting(),
                )
                val timingEvidence =
                    host.lastProjectExecutionPhaseTimingEvidenceForTesting()
                assertEquals(
                    observedNaturalPhases,
                    timingEvidence.map(
                        R2ProjectExecutionPhaseTimingEvidence::phase,
                    ),
                )
                timingEvidence.forEach { timing ->
                    assertTrue(
                        "Natural-emit $label ${timing.phase.protocolValue} " +
                            "sequence two was not observed after project start.",
                        timing
                            .sequenceTwoElapsedRealtimeNanosSinceProjectStart >
                            0L,
                    )
                    assertTrue(
                        "Natural-emit $label ${timing.phase.protocolValue} " +
                            "completion did not follow sequence two.",
                        timing.completedElapsedRealtimeNanosSinceProjectStart >
                            timing
                                .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                    )
                }

                val inputTelemetry = requireNotNull(
                    host.lastProjectInputTransportTelemetryForTesting(),
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.acceptedPayloadBytes,
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture.TOTAL_BYTES,
                    inputTelemetry.totalPayloadBytes,
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture
                        .INPUT_TRANSPORT_FRAME_COUNT,
                    inputTelemetry.sentFrameCount,
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    inputTelemetry.openedCursorCount,
                )
                assertEquals(
                    R2NaturalEmitTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                    inputTelemetry.closedCursorCount,
                )
                assertReleasedInputCursors(
                    telemetry = inputTelemetry,
                    expectedComplete = true,
                )
                baselineRetiredSessionGeneration = requireNotNull(
                    host.lastRetiredSessionGenerationForTesting(),
                )
                assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
                return Pair(compilation, timingEvidence)
            }

            check(NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS == 2) {
                "Natural emit evidence must use exactly two successful " +
                    "precondition runs."
            }
            repeat(NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS) {
                    preconditionIndex ->
                val (preconditionCompilation, preconditionTiming) =
                    successfulNaturalEmitRun(
                        "calibration precondition ${preconditionIndex + 1}",
                    )
                Log.i(
                    NATURAL_EMIT_TIMEOUT_LOG_TAG,
                    "NATURAL_EMIT_TIMEOUT_PRECONDITION_PASS " +
                        "run=${preconditionIndex + 1} " +
                        "runs=$NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS " +
                        "phases=${preconditionTiming.size} timing=" +
                        preconditionTiming.joinToString(
                            separator = ",",
                        ) { timing ->
                            "${timing.phase.protocolValue}:" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                                ) +
                                "/" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .completedElapsedRealtimeNanosSinceProjectStart,
                                )
                        } +
                        "ms outputs=${preconditionCompilation.outputs.size} " +
                        "outputBytes=" +
                        R2NaturalEmitTimeoutWorkloadFixture.OUTPUT_BYTES,
                )
            }

            val calibrationRuns =
                mutableListOf<List<R2ProjectExecutionPhaseTimingEvidence>>()
            repeat(NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS) { runIndex ->
                val (compilation, timingEvidence) =
                    successfulNaturalEmitRun(
                        "emit deadline calibration ${runIndex + 1}",
                    )
                calibrationRuns += timingEvidence
                Log.i(
                    NATURAL_EMIT_TIMEOUT_LOG_TAG,
                    "NATURAL_EMIT_TIMEOUT_BASELINE_PASS " +
                        "run=${runIndex + 1} phases=${timingEvidence.size} " +
                        "timing=" +
                        timingEvidence.joinToString(separator = ",") { timing ->
                            "${timing.phase.protocolValue}:" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                                ) +
                                "/" +
                                TimeUnit.NANOSECONDS.toMillis(
                                    timing
                                        .completedElapsedRealtimeNanosSinceProjectStart,
                                )
                        } +
                        "ms outputs=${compilation.outputs.size} " +
                        "outputBytes=" +
                        R2NaturalEmitTimeoutWorkloadFixture.OUTPUT_BYTES,
                )
            }

            calibration = calibrateNaturalEmitDeadline(calibrationRuns)
            check(NATURAL_EMIT_TIMEOUT_FAULT_ATTEMPTS == 1) {
                "Natural emit deadline evidence must not retry its fault run."
            }

            warmWorker("the emit absolute deadline")
            val selectedCalibration = requireNotNull(calibration)
            val handle = host.newProjectOperationHandle()
            val wallStartedAtNanos = System.nanoTime()
            val timedOut = executor.submit(
                Callable {
                    host.compileProject(
                        handle = handle,
                        snapshot = workload,
                        timeoutMs = selectedCalibration.timeoutMillis,
                    )
                },
            ).get(CAPACITY_TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            val wallElapsedMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - wallStartedAtNanos,
            )

            assertFalse(timedOut.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_TIMEOUT,
                timedOut.errorCode,
            )
            assertEquals(null, timedOut.compilation)
            assertTrue(
                "Natural emit timeout fired before its request-start " +
                    "absolute deadline.",
                timedOut.elapsedMs >= selectedCalibration.timeoutMillis &&
                    wallElapsedMillis >= selectedCalibration.timeoutMillis,
            )
            assertEquals(
                expectedNaturalEmitTimeoutProgressHistory(),
                host.lastProjectExecutionPhaseProgressHistoryForTesting(),
            )
            assertEquals(
                listOf(
                    R2ProjectExecutionPhase.PARSE,
                    R2ProjectExecutionPhase.SEMANTIC,
                ),
                host.lastProjectExecutionPhaseTimingEvidenceForTesting()
                    .map(R2ProjectExecutionPhaseTimingEvidence::phase),
            )

            val inputTelemetry = requireNotNull(
                host.lastProjectInputTransportTelemetryForTesting(),
            )
            assertEquals(handle.requestId, inputTelemetry.requestId)
            assertEquals(
                R2NaturalEmitTimeoutWorkloadFixture.TOTAL_BYTES,
                inputTelemetry.acceptedPayloadBytes,
            )
            assertEquals(
                R2NaturalEmitTimeoutWorkloadFixture.TOTAL_BYTES,
                inputTelemetry.totalPayloadBytes,
            )
            assertEquals(
                R2NaturalEmitTimeoutWorkloadFixture
                    .INPUT_TRANSPORT_FRAME_COUNT,
                inputTelemetry.sentFrameCount,
            )
            assertEquals(
                R2NaturalEmitTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                inputTelemetry.openedCursorCount,
            )
            assertEquals(
                R2NaturalEmitTimeoutWorkloadFixture.TOTAL_FILE_COUNT,
                inputTelemetry.closedCursorCount,
            )
            assertReleasedInputCursors(
                telemetry = inputTelemetry,
                expectedComplete = true,
            )

            val evidence = requireNotNull(
                host.lastProjectPhaseTerminationEvidenceForTesting(),
            )
            assertEquals(handle.requestId, evidence.requestId)
            assertEquals(R2ProjectStopCause.TIMED_OUT, evidence.stopCause)
            assertEquals(
                R2ProjectExecutionPhase.EMIT,
                evidence.phaseAtHostStopSelection,
            )
            assertEquals(
                R2ProjectExecutionPhase.EMIT,
                evidence.phaseAtPageTermination,
            )
            assertEquals(
                R2ProjectExecutionPhase.ordered,
                evidence.enteredPhasesAtHostStopSelection,
            )
            assertEquals(
                listOf(
                    R2ProjectExecutionPhase.INGRESS,
                    R2ProjectExecutionPhase.PARSE,
                    R2ProjectExecutionPhase.SEMANTIC,
                ),
                evidence.completedPhasesAtHostStopSelection,
            )
            assertEquals(
                NATURAL_EMIT_TIMEOUT_PROGRESS_SEQUENCE,
                evidence.phaseProgressSequenceAtPageTermination,
            )
            assertEquals(
                expectedNaturalEmitTimeoutCompletedUnits(
                    phase = R2ProjectExecutionPhase.EMIT,
                    sequence = NATURAL_EMIT_TIMEOUT_PROGRESS_SEQUENCE,
                ),
                evidence.phaseProgressCompletedUnitsAtPageTermination,
            )
            assertEquals(
                R2NaturalEmitTimeoutWorkloadFixture.OUTPUT_FILE_COUNT,
                evidence.phaseProgressTotalUnitsAtPageTermination,
            )
            assertTrue(
                evidence.phaseProgressCompletedUnitsAtPageTermination <
                    evidence.phaseProgressTotalUnitsAtPageTermination,
            )
            assertTrue(
                "Natural emit stop selection preceded the absolute deadline.",
                evidence.stopSelectedAtElapsedRealtimeNanos >=
                    evidence.deadlineAtElapsedRealtimeNanos,
            )
            val deadlineOvershootMillis = TimeUnit.NANOSECONDS.toMillis(
                evidence.stopSelectedAtElapsedRealtimeNanos -
                    evidence.deadlineAtElapsedRealtimeNanos,
            )
            assertTrue(
                "Natural emit deadline selection overshoot was unbounded: " +
                    "$deadlineOvershootMillis ms.",
                deadlineOvershootMillis in
                    0..NATURAL_EMIT_TIMEOUT_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS,
            )
            assertTrue(evidence.sessionGeneration > 0)
            assertEquals(
                evidence.workerGeneration,
                evidence.terminatedWorkerGeneration,
            )
            assertEquals(
                evidence.terminatedWorkerGeneration + 1,
                evidence.replacementWorkerGeneration,
            )
            replacementWorkerGeneration = evidence.replacementWorkerGeneration
            assertEquals(
                evidence.terminatedWorkerGeneration,
                host.lastTerminatedWorkerGenerationForTesting(),
            )
            Log.i(
                NATURAL_EMIT_TIMEOUT_LOG_TAG,
                "NATURAL_EMIT_TIMEOUT_PASS " +
                    "phase=emit requestId=${handle.requestId} " +
                    "calibratedTimeoutMs=${selectedCalibration.timeoutMillis} " +
                    "calibrationLowerMs=" +
                    "${selectedCalibration.minimumTimeoutMillis} " +
                    "calibrationUpperMs=" +
                    "${selectedCalibration.maximumTimeoutMillis} " +
                    "progressSequence=${evidence.sequence} " +
                    "completedUnits=${evidence.completedUnits} " +
                    "totalUnits=${evidence.totalUnits} " +
                    "stopCause=${evidence.stopCause} " +
                    "outcome=${timedOut.errorCode} compilation=none " +
                    "deadlineOvershootMs=$deadlineOvershootMillis " +
                    "workerSpinMilliseconds=0 workerSpinPhase=none " +
                    "inputFrames=${inputTelemetry.sentFrameCount} " +
                    "openedCursors=${inputTelemetry.openedCursorCount} " +
                    "closedCursors=${inputTelemetry.closedCursorCount} " +
                    "released=${inputTelemetry.released} " +
                    "sessionGeneration=${evidence.sessionGeneration} " +
                    "terminatedWorkerGeneration=" +
                    "${evidence.terminatedWorkerGeneration} " +
                    "replacementWorkerGeneration=" +
                    evidence.replacementWorkerGeneration,
            )

            workload.close()
            assertTrue(
                "Natural emit timeout left a project spool file.",
                spoolDirectory.listFiles().orEmpty().none { file ->
                    file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
                },
            )

            val successor = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = successorSnapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(successor.errorMessage.orEmpty(), successor.succeeded)
            assertTrue(
                "The natural emit timeout successor did not retire a newer " +
                    "session.",
                requireNotNull(host.lastRetiredSessionGenerationForTesting()) >
                    baselineRetiredSessionGeneration,
            )
            assertRetiredProjectSession(host.lifecycleTelemetryForTesting())
        } finally {
            try {
                closeReport = onMain {
                    host.close()
                }
            } finally {
                executor.shutdownNow()
                workload.close()
                successorSnapshot.close()
            }
        }
        assertTrue(
            "Natural emit timeout host close did not prove cleanup.",
            requireNotNull(closeReport).cleanupProven,
        )
        assertTrue(
            "Natural emit timeout cleanup left a project spool file.",
            spoolDirectory.listFiles().orEmpty().none { file ->
                file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
            },
        )
        Log.i(
            NATURAL_EMIT_TIMEOUT_LOG_TAG,
            "NATURAL_EMIT_TIMEOUT_MATRIX_PASS " +
                "api=${Build.VERSION.SDK_INT} " +
                "webView=" +
                "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                "phases=1 preconditionRuns=" +
                "$NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS " +
                "calibrationRuns=" +
                "$NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS " +
                "emitTimeoutMs=${calibration.timeoutMillis} " +
                "roots=${R2NaturalEmitTimeoutWorkloadFixture.ROOT_COUNT} " +
                "files=" +
                "${R2NaturalEmitTimeoutWorkloadFixture.TOTAL_FILE_COUNT} " +
                "bytes=${R2NaturalEmitTimeoutWorkloadFixture.TOTAL_BYTES} " +
                "tokens=${R2NaturalEmitTimeoutWorkloadFixture.TOTAL_TOKENS} " +
                "inputFrames=" +
                "${R2NaturalEmitTimeoutWorkloadFixture.INPUT_TRANSPORT_FRAME_COUNT} " +
                "optionalChainCount=" +
                "${R2NaturalEmitTimeoutWorkloadFixture.OPTIONAL_CHAIN_COUNT_PER_HEAVY_ROOT} " +
                "optionalChainDepth=" +
                "${R2NaturalEmitTimeoutWorkloadFixture.OPTIONAL_CHAIN_DEPTH} " +
                "lightRoots=" +
                "${R2NaturalEmitTimeoutWorkloadFixture.LIGHT_ROOT_COUNT} " +
                "heavyRoots=" +
                "${R2NaturalEmitTimeoutWorkloadFixture.HEAVY_ROOT_COUNT} " +
                "outputBytes=" +
                "${R2NaturalEmitTimeoutWorkloadFixture.OUTPUT_BYTES} " +
                "ordinaryAdmission=true requestStartAbsoluteDeadline=true " +
                "workerSpinMilliseconds=0 workerSpinPhase=none " +
                "hostCleanupProven=true spoolCleanupProven=true " +
                "lastReplacementWorkerGeneration=" +
                replacementWorkerGeneration,
        )
    }

    @Test
    fun activeLargeProjectRendererTerminationIsCorrelatedAndRecovers() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assumeTrue("Active renderer-loss evidence requires API 31 or newer.", false)
        }
        val capacityDirectory = context.getDir(
            CAPACITY_SPOOL_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val declarations =
            R2TrustedDeclarations.loadTypeScriptStandardLibraries(context)
        val fixture = loadFixture()
        val successorSnapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )

        withCompilerHost(
            timeoutSeconds = ACTIVE_RENDERER_CAPACITY_TEST_TIMEOUT_SECONDS,
        ) { host ->
            assertTrue(
                "The designated API 31+ provider must support renderer termination.",
                onMain(host::isRendererTerminationSupportedForTesting),
            )
            val boundarySnapshot = createExactCallerCapacitySnapshot(
                directory = capacityDirectory,
                declarations = declarations,
            )
            Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP boundary-snapshot-ready")
            val executor = Executors.newSingleThreadExecutor()
            try {
                val handle = host.newProjectOperationHandle()
                val compilation = executor.submit(
                    Callable {
                        host.compileProjectWithExpandedWorkerCapacityForTesting(
                            handle = handle,
                            snapshot = boundarySnapshot,
                            timeoutMs = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
                            workerSpinMilliseconds =
                                SYNCHRONOUS_WORKER_SPIN_MILLIS,
                            workerSpinPhase = R2ProjectExecutionPhase.SEMANTIC,
                        )
                    },
                )
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP compilation-submitted")
                waitForExecutionPhase(
                    host = host,
                    handle = handle,
                    phase = R2ProjectExecutionPhase.INGRESS,
                    timeoutSeconds = CAPACITY_PHASE_WAIT_SECONDS,
                )
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP ingress-active")
                val processProbe = R1WebViewProcessMemoryProbe(context)
                val oldRenderer = processProbe.awaitRendererForClient(
                    android.os.Process.myPid(),
                )
                Log.i(
                    PHASE_LOG_TAG,
                    "ACTIVE_RENDERER_STEP old-renderer-captured " +
                        "pid=${oldRenderer.pid} start=${oldRenderer.startTimeTicks}",
                )
                waitForExecutionPhase(
                    host = host,
                    handle = handle,
                    phase = R2ProjectExecutionPhase.SEMANTIC,
                    timeoutSeconds = CAPACITY_PHASE_WAIT_SECONDS,
                )
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP semantic-active")
                assertTrue(
                    "The exact active semantic-entry renderer could not be terminated.",
                    onMain {
                        host.terminateActiveProjectRendererForTesting(
                            handle = handle,
                            expectedPhase = R2ProjectExecutionPhase.SEMANTIC,
                        )
                    },
                )
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP terminate-accepted")

                val rendererLost = compilation.get(
                    TEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP unavailable-outcome-received")
                assertFalse(rendererLost.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                    rendererLost.errorCode,
                )
                assertEquals(null, rendererLost.compilation)
                assertReleasedInputCursors(
                    telemetry = requireNotNull(
                        host.lastProjectInputTransportTelemetryForTesting(),
                    ),
                    expectedComplete = true,
                )
                val evidence = requireNotNull(
                    host.lastRendererGoneEvidenceForTesting(),
                )
                assertEquals(handle.requestId, evidence.requestId)
                assertEquals(R2ProjectExecutionPhase.SEMANTIC, evidence.phase)
                assertEquals(
                    R2ProjectExecutionPhase.SEMANTIC,
                    evidence.requestedAtPhase,
                )
                assertFalse(evidence.didCrash)
                assertTrue(evidence.observedAtElapsedRealtimeNanos > 0L)
                assertNotNull(evidence.workerGeneration)
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP evidence-validated")
                processProbe.awaitProcessExit(oldRenderer)
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP old-renderer-exited")

                val successorHandle = host.newProjectOperationHandle()
                val successorFuture = executor.submit(
                    Callable {
                        host.compileProjectWithSynchronousWorkerSpinForTesting(
                            handle = successorHandle,
                            snapshot = successorSnapshot,
                            timeoutMs =
                                TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                            workerSpinMilliseconds =
                                SUCCESSOR_RENDERER_OBSERVATION_SPIN_MILLIS,
                            workerSpinPhase = R2ProjectExecutionPhase.PARSE,
                        )
                    },
                )
                waitForExecutionPhase(
                    host = host,
                    handle = successorHandle,
                    phase = R2ProjectExecutionPhase.PARSE,
                )
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP successor-parse-active")
                val successorRenderer = processProbe.awaitRendererForClient(
                    android.os.Process.myPid(),
                )
                Log.i(
                    PHASE_LOG_TAG,
                    "ACTIVE_RENDERER_STEP successor-renderer-captured " +
                        "pid=${successorRenderer.pid} " +
                        "start=${successorRenderer.startTimeTicks}",
                )
                assertTrue(
                    "The successor reused the terminated renderer identity.",
                    successorRenderer != oldRenderer,
                )
                val successor = successorFuture.get(
                    TEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS,
                )
                assertTrue(successor.errorMessage.orEmpty(), successor.succeeded)
                Log.i(PHASE_LOG_TAG, "ACTIVE_RENDERER_STEP successor-succeeded")
                assertTrue(
                    requireNotNull(
                        host.lastRetiredSessionGenerationForTesting(),
                    ) > evidence.sessionGeneration,
                )
                Log.i(
                    PHASE_LOG_TAG,
                    "ACTIVE_RENDERER_PASS api=${Build.VERSION.SDK_INT} " +
                        "webView=" +
                        "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                        "requestId=${evidence.requestId} " +
                        "phase=${evidence.phase?.protocolValue} " +
                        "didCrash=${evidence.didCrash} " +
                        "rendererPriorityAtExit=${evidence.rendererPriorityAtExit} " +
                        "oldRendererPid=${oldRenderer.pid} " +
                        "oldRendererStart=${oldRenderer.startTimeTicks} " +
                        "successorRendererPid=${successorRenderer.pid} " +
                        "successorRendererStart=${successorRenderer.startTimeTicks} " +
                        "elapsedMs=${rendererLost.elapsedMs}",
                )
            } finally {
                boundarySnapshot.close()
                executor.shutdownNow()
            }
            assertTrue(
                "The active-renderer capacity snapshot left a spool file.",
                capacityDirectory.listFiles().orEmpty().none { file ->
                    file.name.startsWith(CAPACITY_SPOOL_FILE_PREFIX)
                },
            )
        }
    }

    @Test
    fun exactHandleCancelsSynchronousWorkerBeforePublishingTerminalOutcome() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        try {
            val warm = executor.submit(
                Callable {
                    host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(warm.errorMessage.orEmpty(), warm.succeeded)

            val handle = host.newProjectOperationHandle()
            val compilation = executor.submit(
                Callable {
                    host.compileProjectWithSynchronousWorkerSpinForTesting(
                        handle = handle,
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                        workerSpinMilliseconds = SYNCHRONOUS_WORKER_SPIN_MILLIS,
                    )
                },
            )
            waitForSynchronousWork(host, handle)

            assertTrue(
                host.requestStop(
                    handle = host.newProjectOperationHandle(),
                    cause = R2ProjectStopCause.CANCELLED,
                ),
            )
            assertTrue(
                host.requestStop(
                    handle = handle,
                    cause = R2ProjectStopCause.CANCELLED,
                ),
            )
            val cancelled = compilation.get(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            assertFalse(cancelled.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_CANCELLED,
                cancelled.errorCode,
            )
            assertEquals(null, cancelled.compilation)
            assertNotNull(host.lastTerminatedWorkerGenerationForTesting())

            val recovered = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
        } finally {
            onMain {
                host.close()
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun stopIntentBeforeCompileRegistrationIsFirstWinsAndDoesNotDispatch() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        withCompilerHost { host ->
            val handle = host.newProjectOperationHandle()
            assertTrue(
                host.requestStop(
                    handle = handle,
                    cause = R2ProjectStopCause.CALLBACK_DIED,
                ),
            )
            assertTrue(
                host.requestStop(
                    handle = handle,
                    cause = R2ProjectStopCause.TIMED_OUT,
                ),
            )

            val stopped = host.compileProject(
                handle = handle,
                snapshot = snapshot,
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )
            assertFalse(stopped.succeeded)
            assertEquals(TypeScriptCompilerContract.ERROR_CANCELLED, stopped.errorCode)
            assertEquals("Project compilation owner disconnected.", stopped.errorMessage)
            assertEquals(null, stopped.compilation)

            val recovered = host.compileProject(
                snapshot = snapshot,
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
        }
    }

    @Test
    fun activeTimeoutTerminatesItsWorkerBeforeTheNextProjectCanRun() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        try {
            val warm = executor.submit(
                Callable {
                    host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(warm.errorMessage.orEmpty(), warm.succeeded)

            val handle = host.newProjectOperationHandle()
            val compilation = executor.submit(
                Callable {
                    host.compileProjectWithSynchronousWorkerSpinForTesting(
                        handle = handle,
                        snapshot = snapshot,
                        timeoutMs = ACTIVE_WORKER_TIMEOUT_MILLIS,
                        workerSpinMilliseconds = SYNCHRONOUS_WORKER_SPIN_MILLIS,
                    )
                },
            )
            waitForSynchronousWork(host, handle)
            val timedOut = compilation.get(
                TEST_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            assertFalse(timedOut.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_TIMEOUT,
                timedOut.errorCode,
            )
            assertEquals(null, timedOut.compilation)
            assertNotNull(host.lastTerminatedWorkerGenerationForTesting())

            val recovered = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
        } finally {
            onMain {
                host.close()
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun pendingProjectEnforcesBusyAndIsReleasedByClose() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newFixedThreadPool(2)
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        var closed = false
        try {
            val heldProject = executor.submit(
                Callable {
                    host.holdProjectUntilCloseForTesting(
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            )
            waitForPending(host)

            val busyProbe = executor.submit(
                Callable {
                    host.probe(TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS)
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertFalse(busyProbe.succeeded)
            assertEquals(TypeScriptCompilerContract.ERROR_BUSY, busyProbe.errorCode)

            val busyProject = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertFalse(busyProject.succeeded)
            assertEquals(TypeScriptCompilerContract.ERROR_BUSY, busyProject.errorCode)

            val closeReport = onMain {
                host.close()
            }
            closed = true
            val closedProject =
                heldProject.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertFalse(closedProject.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                closedProject.errorCode,
            )
            assertEquals(null, closedProject.compilation)
            assertTrue(closeReport.hadPendingProbe)
            assertTrue(closeReport.pendingProbeReleased)
        } finally {
            if (!closed) {
                onMain {
                    host.close()
                }
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun closeWaitsForInFlightResultFinalizerBeforePublishingCleanupProof() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context).also { compilerHost ->
                compilerHost.armResultFinalizerDelayForTesting(
                    RESULT_FINALIZER_DELAY_MILLISECONDS,
                )
            }
        }
        var closed = false
        try {
            val compilation = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            )
            waitForResultFinalizer(host)

            val closeReport = onMain {
                host.close()
            }
            closed = true
            val stopped = compilation.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertFalse(stopped.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                stopped.errorCode,
            )
            assertEquals(null, stopped.compilation)
            assertTrue(closeReport.cleanupProven)
            assertTrue(closeReport.protocolExecutorTerminated)
            assertTrue(closeReport.resultExecutorTerminated)
            assertTrue(closeReport.resultFinalizersReleased)
            assertTrue(closeReport.handlerCallbacksCleared)

            val replacement = onMain {
                TypeScriptWebViewCompilerHost(context)
            }
            try {
                val recovered = executor.submit(
                    Callable {
                        replacement.compileProject(
                            snapshot = snapshot,
                            timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                        )
                    },
                ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
            } finally {
                val replacementCloseReport = onMain {
                    replacement.close()
                }
                assertTrue(replacementCloseReport.cleanupProven)
            }
        } finally {
            if (!closed) {
                onMain {
                    host.close()
                }
            }
            executor.shutdownNow()
        }
    }

    @Test
    fun cancelWaitsForInFlightResultFinalizerAfterExactWorkerTeardown() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context).also { compilerHost ->
                compilerHost.armResultFinalizerGateForTesting()
            }
        }
        try {
            val handle = host.newProjectOperationHandle()
            val compilation = executor.submit(
                Callable {
                    host.compileProject(
                        handle = handle,
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            )
            waitForResultFinalizer(host)
            assertTrue(
                host.requestStop(
                    handle = handle,
                    cause = R2ProjectStopCause.CANCELLED,
                ),
            )
            waitForStopTeardownProof(host, handle)

            assertFalse(
                "Cancelled project published before its result finalizer released.",
                compilation.isDone,
            )
            host.releaseResultFinalizerGateForTesting()
            val cancelled = compilation.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            assertFalse(cancelled.succeeded)
            assertEquals(TypeScriptCompilerContract.ERROR_CANCELLED, cancelled.errorCode)
            assertEquals(null, cancelled.compilation)
            assertFalse(host.isResultFinalizerRunningForTesting())

            val recovered = executor.submit(
                Callable {
                    host.compileProject(
                        snapshot = snapshot,
                        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
                    )
                },
            ).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
        } finally {
            host.releaseResultFinalizerGateForTesting()
            val closeReport = onMain {
                host.close()
            }
            assertTrue(closeReport.cleanupProven)
            executor.shutdownNow()
        }
    }

    @Test
    fun projectRendererExitIsTerminalAndTheNextProjectUsesANewSession() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        withCompilerHost { host ->
            val supported = onMain {
                host.isRendererTerminationSupportedForTesting()
            }
            assumeTrue(
                "The installed WebView provider does not advertise renderer termination.",
                supported,
            )
            onMain {
                host.armRendererTerminationsForTesting(1)
            }

            val rendererExit = host.compileProject(
                snapshot = snapshot,
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )

            val remainingTerminations = onMain {
                host.remainingRendererTerminationsForTesting()
            }
            assumeTrue(
                "The installed WebView provider did not expose a renderer handle.",
                remainingTerminations == 0,
            )
            assertFalse(rendererExit.succeeded)
            assertEquals(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                rendererExit.errorCode,
            )
            assertEquals(
                0,
                remainingTerminations,
            )

            val recovered = host.compileProject(
                snapshot = snapshot,
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )
            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
            assertEquals(2, requireNotNull(recovered.compilation).executableModules.size)
        }
    }

    @Test
    fun timedOutProjectIsDiscardedAndTheWorkerRecoversForTheNextProject() {
        val fixture = loadFixture()
        val snapshot = fixture.snapshot(
            fixture.getJSONObject("success").getString("mainSource"),
        )
        withCompilerHost { host ->
            val mainBlockEntered = CountDownLatch(1)
            val releaseMain = CountDownLatch(1)
            try {
                assertTrue(
                    Handler(Looper.getMainLooper()).post {
                        mainBlockEntered.countDown()
                        releaseMain.await(
                            MAIN_THREAD_BLOCK_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS,
                        )
                    },
                )
                assertTrue(
                    mainBlockEntered.await(
                        MAIN_THREAD_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS,
                    ),
                )

                val timedOut = host.compileProject(
                    snapshot = snapshot,
                    timeoutMs = PROJECT_TIMEOUT_MILLIS,
                )

                assertFalse(timedOut.succeeded)
                assertEquals(
                    TypeScriptCompilerContract.ERROR_TIMEOUT,
                    timedOut.errorCode,
                )
                assertEquals(null, timedOut.compilation)
            } finally {
                releaseMain.countDown()
            }

            val recovered = host.compileProject(
                snapshot = snapshot,
                timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
            )

            assertTrue(recovered.errorMessage.orEmpty(), recovered.succeeded)
            assertEquals(2, requireNotNull(recovered.compilation).executableModules.size)
        }
    }

    private fun loadFixture(): JSONObject =
        fixtureContext.assets
            .open(FIXTURE_PATH)
            .bufferedReader()
            .use { reader -> JSONObject(reader.readText()) }

    private fun JSONObject.snapshot(mainSource: String): R2ProjectSnapshot {
        val files = mutableListOf<R2ProjectTextFile>()
        val commonFiles = getJSONArray("commonFiles")
        repeat(commonFiles.length()) { index ->
            val file = commonFiles.getJSONObject(index)
            files += R2ProjectTextFile(
                path = file.getString("path"),
                text = file.getString("text"),
            )
        }
        files += R2ProjectTextFile(MAIN_SOURCE_PATH, mainSource)
        val roots = getJSONArray("rootNames")
        return R2ProjectSnapshot.create(
            files = files,
            rootNames = List(roots.length(), roots::getString),
        )
    }

    private fun JSONObject.declarationErrorSnapshot(): R2ProjectSnapshot =
        R2ProjectSnapshot.create(
            files = listOf(
                R2ProjectTextFile(
                    path = DEFAULT_LIBRARY_PATH,
                    text = getString("libraryText"),
                ),
                R2ProjectTextFile(
                    path = MAIN_SOURCE_PATH,
                    text = getString("mainSource"),
                ),
            ),
            rootNames = getJSONArray("rootNames").stringList(),
        )

    private fun JSONObject.projectFilesSnapshot(fixture: JSONObject): R2ProjectSnapshot {
        val defaultLibrary = fixture.getJSONArray("commonFiles")
            .let { commonFiles ->
                List(commonFiles.length(), commonFiles::getJSONObject)
            }
            .single { file -> file.getString("path") == DEFAULT_LIBRARY_PATH }
        val files = mutableListOf(
            R2ProjectTextFile(
                path = defaultLibrary.getString("path"),
                text = defaultLibrary.getString("text"),
            ),
        )
        val fixtureFiles = getJSONArray("files")
        repeat(fixtureFiles.length()) { index ->
            val file = fixtureFiles.getJSONObject(index)
            files += R2ProjectTextFile(
                path = file.getString("path"),
                text = file.getString("text"),
            )
        }
        return R2ProjectSnapshot.create(
            files = files,
            rootNames = getJSONArray("rootNames").stringList(),
        )
    }

    private fun org.json.JSONArray.stringList(): List<String> =
        List(length(), this::getString)

    private fun waitForPending(host: TypeScriptWebViewCompilerHost) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
            MAIN_THREAD_TIMEOUT_SECONDS,
        )
        while (!host.hasPendingProbeForTesting() && System.nanoTime() < deadline) {
            Thread.yield()
        }
        assertTrue("Project operation did not enter pending state.", host.hasPendingProbeForTesting())
    }

    private fun waitForSynchronousWork(
        host: TypeScriptWebViewCompilerHost,
        handle: R2HostProjectOperationHandle,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
            MAIN_THREAD_TIMEOUT_SECONDS,
        )
        while (
            !host.isProjectSynchronousWorkStartedForTesting(handle) &&
            System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        assertTrue(
            "Project operation did not enter synchronous Worker work.",
            host.isProjectSynchronousWorkStartedForTesting(handle),
        )
    }

    private fun waitForExecutionPhase(
        host: TypeScriptWebViewCompilerHost,
        handle: R2HostProjectOperationHandle,
        phase: R2ProjectExecutionPhase,
        timeoutSeconds: Long = PHASE_ENTRY_WAIT_SECONDS,
    ) {
        val deadline =
            System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (
            host.projectExecutionPhaseForTesting(handle) != phase &&
            System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        assertEquals(
            "Project operation did not remain active at ${phase.protocolValue} entry.",
            phase,
            host.projectExecutionPhaseForTesting(handle),
        )
    }

    private fun waitForExecutionPhaseProgress(
        host: TypeScriptWebViewCompilerHost,
        handle: R2HostProjectOperationHandle,
        phase: R2ProjectExecutionPhase,
        sequence: Int,
        timeoutSeconds: Long = CAPACITY_PHASE_WAIT_SECONDS,
    ): R2ProjectExecutionPhaseProgress {
        val deadline =
            System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        var lastProgress: R2ProjectExecutionPhaseProgress? = null
        while (System.nanoTime() < deadline) {
            host.projectExecutionPhaseProgressForTesting(handle)?.let { progress ->
                lastProgress = progress
                if (progress.phase == phase && progress.sequence == sequence) {
                    return progress
                }
            }
            Thread.yield()
        }
        assertEquals(
            "Project operation did not remain active in the expected natural phase.",
            phase,
            host.projectExecutionPhaseForTesting(handle),
        )
        assertNotNull(
            "Project operation did not publish natural phase progress.",
            lastProgress,
        )
        assertEquals(phase, requireNotNull(lastProgress).phase)
        assertEquals(sequence, requireNotNull(lastProgress).sequence)
        return requireNotNull(lastProgress)
    }

    private fun expectedNaturalPhaseProgressHistory():
        List<R2ProjectExecutionPhaseProgress> =
        listOf(
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        ).flatMap { phase ->
            listOf(1, 2).map { sequence ->
                R2ProjectExecutionPhaseProgress(
                    phase = phase,
                    sequence = sequence,
                    completedUnits = expectedNaturalPhaseCompletedUnits(
                        phase = phase,
                        sequence = sequence,
                    ),
                    totalUnits = expectedNaturalPhaseTotalUnits(phase),
                )
            }
        }

    private fun expectedNaturalPhaseCompletedUnits(
        phase: R2ProjectExecutionPhase,
        sequence: Int,
    ): Int {
        require(sequence in 1..2)
        val totalUnits = expectedNaturalPhaseTotalUnits(phase)
        val divisor = if (sequence == 1) 8 else 4
        return (totalUnits - 1) / divisor + 1
    }

    private fun expectedNaturalPhaseTotalUnits(
        phase: R2ProjectExecutionPhase,
    ): Int = when (phase) {
        R2ProjectExecutionPhase.PARSE,
        R2ProjectExecutionPhase.SEMANTIC,
        -> R2NaturalPhaseWorkloadFixture.ROOT_COUNT
        R2ProjectExecutionPhase.EMIT ->
            R2NaturalPhaseWorkloadFixture.OUTPUT_FILE_COUNT
        R2ProjectExecutionPhase.INGRESS ->
            error("Natural TypeScript progress excludes ingress.")
    }

    private fun expectedNaturalPhaseTimeoutProgressHistory():
        List<R2ProjectExecutionPhaseProgress> =
        listOf(
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        ).flatMap { phase ->
            listOf(1, 2).map { sequence ->
                R2ProjectExecutionPhaseProgress(
                    phase = phase,
                    sequence = sequence,
                    completedUnits =
                        expectedNaturalPhaseTimeoutCompletedUnits(
                            phase = phase,
                            sequence = sequence,
                        ),
                    totalUnits =
                        expectedNaturalPhaseTimeoutTotalUnits(phase),
                )
            }
        }

    private fun expectedNaturalPhaseTimeoutCompletedUnits(
        phase: R2ProjectExecutionPhase,
        sequence: Int,
    ): Int {
        require(sequence in 1..2)
        val totalUnits = expectedNaturalPhaseTimeoutTotalUnits(phase)
        val divisor = if (sequence == 1) 8 else 4
        return (totalUnits - 1) / divisor + 1
    }

    private fun expectedNaturalPhaseTimeoutTotalUnits(
        phase: R2ProjectExecutionPhase,
    ): Int = when (phase) {
        R2ProjectExecutionPhase.PARSE,
        R2ProjectExecutionPhase.SEMANTIC,
        -> R2NaturalPhaseTimeoutWorkloadFixture.ROOT_COUNT
        R2ProjectExecutionPhase.EMIT ->
            R2NaturalPhaseTimeoutWorkloadFixture.OUTPUT_FILE_COUNT
        R2ProjectExecutionPhase.INGRESS ->
            error("Natural TypeScript progress excludes ingress.")
    }

    private fun expectedNaturalParseTimeoutProgressHistory():
        List<R2ProjectExecutionPhaseProgress> =
        listOf(
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        ).flatMap { phase ->
            listOf(1, 2).map { sequence ->
                R2ProjectExecutionPhaseProgress(
                    phase = phase,
                    sequence = sequence,
                    completedUnits =
                        expectedNaturalParseTimeoutCompletedUnits(
                            phase = phase,
                            sequence = sequence,
                        ),
                    totalUnits =
                        expectedNaturalParseTimeoutTotalUnits(phase),
                )
            }
        }

    private fun expectedNaturalParseTimeoutCompletedUnits(
        phase: R2ProjectExecutionPhase,
        sequence: Int,
    ): Int {
        require(sequence in 1..2)
        val totalUnits = expectedNaturalParseTimeoutTotalUnits(phase)
        val divisor = if (sequence == 1) 8 else 4
        return (totalUnits - 1) / divisor + 1
    }

    private fun expectedNaturalParseTimeoutTotalUnits(
        phase: R2ProjectExecutionPhase,
    ): Int = when (phase) {
        R2ProjectExecutionPhase.PARSE,
        R2ProjectExecutionPhase.SEMANTIC,
        -> R2NaturalParseTimeoutWorkloadFixture.ROOT_COUNT
        R2ProjectExecutionPhase.EMIT ->
            R2NaturalParseTimeoutWorkloadFixture.OUTPUT_FILE_COUNT
        R2ProjectExecutionPhase.INGRESS ->
            error("Natural TypeScript progress excludes ingress.")
    }

    private fun expectedNaturalEmitTimeoutProgressHistory():
        List<R2ProjectExecutionPhaseProgress> =
        listOf(
            R2ProjectExecutionPhase.PARSE,
            R2ProjectExecutionPhase.SEMANTIC,
            R2ProjectExecutionPhase.EMIT,
        ).flatMap { phase ->
            listOf(1, 2).map { sequence ->
                R2ProjectExecutionPhaseProgress(
                    phase = phase,
                    sequence = sequence,
                    completedUnits =
                        expectedNaturalEmitTimeoutCompletedUnits(
                            phase = phase,
                            sequence = sequence,
                        ),
                    totalUnits =
                        expectedNaturalEmitTimeoutTotalUnits(phase),
                )
            }
        }

    private fun expectedNaturalEmitTimeoutCompletedUnits(
        phase: R2ProjectExecutionPhase,
        sequence: Int,
    ): Int {
        require(sequence in 1..2)
        val totalUnits = expectedNaturalEmitTimeoutTotalUnits(phase)
        val divisor = if (sequence == 1) 8 else 4
        return (totalUnits - 1) / divisor + 1
    }

    private fun expectedNaturalEmitTimeoutTotalUnits(
        phase: R2ProjectExecutionPhase,
    ): Int = when (phase) {
        R2ProjectExecutionPhase.PARSE,
        R2ProjectExecutionPhase.SEMANTIC,
        -> R2NaturalEmitTimeoutWorkloadFixture.ROOT_COUNT
        R2ProjectExecutionPhase.EMIT ->
            R2NaturalEmitTimeoutWorkloadFixture.OUTPUT_FILE_COUNT
        R2ProjectExecutionPhase.INGRESS ->
            error("Natural TypeScript progress excludes ingress.")
    }

    private fun calibrateNaturalEmitDeadline(
        timingRuns: List<List<R2ProjectExecutionPhaseTimingEvidence>>,
    ): NaturalPhaseDeadlineCalibration {
        assertEquals(
            NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS,
            timingRuns.size,
        )
        val phase = R2ProjectExecutionPhase.EMIT
        val samples = timingRuns.map { timingRun ->
            timingRun.single { timing -> timing.phase == phase }
        }
        val nanosPerMillisecond = TimeUnit.MILLISECONDS.toNanos(1L)
        val minimumTimeoutMillis = samples.maxOf { timing ->
            TimeUnit.NANOSECONDS.toMillis(
                timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart,
            ) + 1L
        }
        val maximumTimeoutMillis = samples.minOf { timing ->
            (
                timing.completedElapsedRealtimeNanosSinceProjectStart - 1L
                ) / nanosPerMillisecond
        }
        assertTrue(
            "Natural emit did not expose a stable request-start deadline " +
                "window: $minimumTimeoutMillis..$maximumTimeoutMillis ms.",
            maximumTimeoutMillis - minimumTimeoutMillis >=
                NATURAL_EMIT_TIMEOUT_MINIMUM_MARGIN_MILLIS * 2L,
        )
        val timeoutMillis =
            minimumTimeoutMillis +
                (maximumTimeoutMillis - minimumTimeoutMillis) / 2L
        val leftMarginMillis = timeoutMillis - minimumTimeoutMillis
        val rightMarginMillis = maximumTimeoutMillis - timeoutMillis
        assertTrue(
            "Natural emit calibrated deadline did not retain both observed " +
                "margins.",
            leftMarginMillis >=
                NATURAL_EMIT_TIMEOUT_MINIMUM_MARGIN_MILLIS &&
                rightMarginMillis >=
                NATURAL_EMIT_TIMEOUT_MINIMUM_MARGIN_MILLIS,
        )
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        samples.forEach { timing ->
            assertTrue(
                timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart <
                    timeoutNanos,
            )
            assertTrue(
                timeoutNanos <
                    timing.completedElapsedRealtimeNanosSinceProjectStart,
            )
        }
        return NaturalPhaseDeadlineCalibration(
            phase = phase,
            minimumTimeoutMillis = minimumTimeoutMillis,
            maximumTimeoutMillis = maximumTimeoutMillis,
            timeoutMillis = timeoutMillis,
        ).also {
            Log.i(
                NATURAL_EMIT_TIMEOUT_LOG_TAG,
                "NATURAL_EMIT_TIMEOUT_WINDOW_PASS " +
                    "phase=emit lowerMs=$minimumTimeoutMillis " +
                    "upperMs=$maximumTimeoutMillis " +
                    "timeoutMs=$timeoutMillis " +
                    "leftMarginMs=$leftMarginMillis " +
                    "rightMarginMs=$rightMarginMillis",
            )
        }
    }

    private fun calibrateNaturalParseDeadline(
        timingRuns: List<List<R2ProjectExecutionPhaseTimingEvidence>>,
    ): NaturalPhaseDeadlineCalibration {
        assertEquals(
            NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS,
            timingRuns.size,
        )
        val phase = R2ProjectExecutionPhase.PARSE
        val samples = timingRuns.map { timingRun ->
            timingRun.single { timing -> timing.phase == phase }
        }
        val nanosPerMillisecond = TimeUnit.MILLISECONDS.toNanos(1L)
        val minimumTimeoutMillis = samples.maxOf { timing ->
            TimeUnit.NANOSECONDS.toMillis(
                timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart,
            ) + 1L
        }
        val maximumTimeoutMillis = samples.minOf { timing ->
            (
                timing.completedElapsedRealtimeNanosSinceProjectStart - 1L
                ) / nanosPerMillisecond
        }
        assertTrue(
            "Natural parse did not expose a stable request-start deadline " +
                "window: $minimumTimeoutMillis..$maximumTimeoutMillis ms.",
            maximumTimeoutMillis - minimumTimeoutMillis >=
                NATURAL_PARSE_TIMEOUT_MINIMUM_MARGIN_MILLIS * 2L,
        )
        val timeoutMillis =
            minimumTimeoutMillis +
                (maximumTimeoutMillis - minimumTimeoutMillis) / 2L
        val leftMarginMillis = timeoutMillis - minimumTimeoutMillis
        val rightMarginMillis = maximumTimeoutMillis - timeoutMillis
        assertTrue(
            "Natural parse calibrated deadline did not retain both observed " +
                "margins.",
            leftMarginMillis >=
                NATURAL_PARSE_TIMEOUT_MINIMUM_MARGIN_MILLIS &&
                rightMarginMillis >=
                NATURAL_PARSE_TIMEOUT_MINIMUM_MARGIN_MILLIS,
        )
        val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        samples.forEach { timing ->
            assertTrue(
                timing.sequenceTwoElapsedRealtimeNanosSinceProjectStart <
                    timeoutNanos,
            )
            assertTrue(
                timeoutNanos <
                    timing.completedElapsedRealtimeNanosSinceProjectStart,
            )
        }
        return NaturalPhaseDeadlineCalibration(
            phase = phase,
            minimumTimeoutMillis = minimumTimeoutMillis,
            maximumTimeoutMillis = maximumTimeoutMillis,
            timeoutMillis = timeoutMillis,
        ).also {
            Log.i(
                NATURAL_PARSE_TIMEOUT_LOG_TAG,
                "NATURAL_PARSE_TIMEOUT_WINDOW_PASS " +
                    "phase=parse lowerMs=$minimumTimeoutMillis " +
                    "upperMs=$maximumTimeoutMillis " +
                    "timeoutMs=$timeoutMillis " +
                    "leftMarginMs=$leftMarginMillis " +
                    "rightMarginMs=$rightMarginMillis",
            )
        }
    }

    private fun calibrateNaturalPhaseDeadlines(
        phases: List<R2ProjectExecutionPhase>,
        timingRuns: List<List<R2ProjectExecutionPhaseTimingEvidence>>,
    ): Map<R2ProjectExecutionPhase, NaturalPhaseDeadlineCalibration> {
        assertEquals(
            NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS,
            timingRuns.size,
        )
        val nanosPerMillisecond = TimeUnit.MILLISECONDS.toNanos(1L)
        return phases.associateWith { phase ->
            val samples = timingRuns.map { timingRun ->
                timingRun.single { timing -> timing.phase == phase }
            }
            val minimumTimeoutMillis = samples.maxOf { timing ->
                TimeUnit.NANOSECONDS.toMillis(
                    timing
                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart,
                ) + 1L
            }
            val maximumTimeoutMillis = samples.minOf { timing ->
                (
                    timing.completedElapsedRealtimeNanosSinceProjectStart -
                        1L
                    ) / nanosPerMillisecond
            }
            assertTrue(
                "Natural ${phase.protocolValue} did not expose a stable " +
                    "request-start deadline window: " +
                    "$minimumTimeoutMillis..$maximumTimeoutMillis ms.",
                maximumTimeoutMillis - minimumTimeoutMillis >=
                    NATURAL_PHASE_TIMEOUT_MINIMUM_MARGIN_MILLIS * 2L,
            )
            val timeoutMillis =
                minimumTimeoutMillis +
                    (maximumTimeoutMillis - minimumTimeoutMillis) / 2L
            val leftMarginMillis =
                timeoutMillis - minimumTimeoutMillis
            val rightMarginMillis =
                maximumTimeoutMillis - timeoutMillis
            assertTrue(
                "Natural ${phase.protocolValue} calibrated deadline did not " +
                    "retain both observed margins.",
                leftMarginMillis >=
                    NATURAL_PHASE_TIMEOUT_MINIMUM_MARGIN_MILLIS &&
                    rightMarginMillis >=
                    NATURAL_PHASE_TIMEOUT_MINIMUM_MARGIN_MILLIS,
            )
            val timeoutNanos =
                TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            samples.forEach { timing ->
                assertTrue(
                    timing
                        .sequenceTwoElapsedRealtimeNanosSinceProjectStart <
                        timeoutNanos,
                )
                assertTrue(
                    timeoutNanos <
                        timing.completedElapsedRealtimeNanosSinceProjectStart,
                )
            }
            NaturalPhaseDeadlineCalibration(
                phase = phase,
                minimumTimeoutMillis = minimumTimeoutMillis,
                maximumTimeoutMillis = maximumTimeoutMillis,
                timeoutMillis = timeoutMillis,
            ).also {
                Log.i(
                    NATURAL_PHASE_TIMEOUT_LOG_TAG,
                    "NATURAL_PHASE_TIMEOUT_WINDOW_PASS " +
                        "phase=${phase.protocolValue} " +
                        "lowerMs=$minimumTimeoutMillis " +
                        "upperMs=$maximumTimeoutMillis " +
                        "timeoutMs=$timeoutMillis " +
                        "leftMarginMs=$leftMarginMillis " +
                        "rightMarginMs=$rightMarginMillis",
                )
            }
        }
    }

    private data class NaturalPhaseDeadlineCalibration(
        val phase: R2ProjectExecutionPhase,
        val minimumTimeoutMillis: Long,
        val maximumTimeoutMillis: Long,
        val timeoutMillis: Long,
    )

    private fun waitForActiveInputCursor(
        host: TypeScriptWebViewCompilerHost,
        handle: R2HostProjectOperationHandle,
        minimumAcceptedBytes: Long,
        timeoutMillis: Long =
            TimeUnit.SECONDS.toMillis(CAPACITY_PHASE_WAIT_SECONDS),
    ): R2ProjectInputTransportTelemetry {
        val deadline =
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var lastTelemetry: R2ProjectInputTransportTelemetry? = null
        while (System.nanoTime() < deadline) {
            val telemetry =
                host.activeProjectInputTransportTelemetryForTesting(handle)
            if (telemetry != null) {
                lastTelemetry = telemetry
                if (
                    telemetry.activeCursor &&
                    !telemetry.complete &&
                    !telemetry.released &&
                    telemetry.acceptedPayloadBytes >= minimumAcceptedBytes &&
                    telemetry.acceptedPayloadBytes <
                    telemetry.totalPayloadBytes
                ) {
                    return telemetry
                }
            }
            Thread.sleep(LIVE_INPUT_TELEMETRY_POLL_MILLIS)
        }
        throw AssertionError(
            "Project input did not expose the exact active cursor threshold; " +
                "minimumAcceptedBytes=$minimumAcceptedBytes last=$lastTelemetry",
        )
    }

    private fun waitForResultFinalizer(host: TypeScriptWebViewCompilerHost) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
            MAIN_THREAD_TIMEOUT_SECONDS,
        )
        while (
            !host.isResultFinalizerRunningForTesting() &&
            System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        assertTrue(
            "Project result finalizer did not enter the delayed test window.",
            host.isResultFinalizerRunningForTesting(),
        )
    }

    private fun waitForStopTeardownProof(
        host: TypeScriptWebViewCompilerHost,
        handle: R2HostProjectOperationHandle,
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
            MAIN_THREAD_TIMEOUT_SECONDS,
        )
        while (
            !host.isProjectStopTeardownProvenForTesting(handle) &&
            System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        assertTrue(
            "Project stop did not reach its exact Worker/session teardown proof.",
            host.isProjectStopTeardownProvenForTesting(handle),
        )
    }

    private fun assertGoldenOutputs(
        successFixture: JSONObject,
        compilation: R2ProjectCompilation,
    ) {
        val expectedJson = successFixture.getJSONArray("outputs")
        val expected = List(expectedJson.length()) { index ->
            expectedJson.getJSONObject(index)
        }.associateBy { output -> output.getString("path") }
        val actual = compilation.outputs.associateBy(R2ProjectOutput::path)

        assertEquals(expected.keys, actual.keys)
        expected.forEach { (path, golden) ->
            val output = requireNotNull(actual[path])
            assertEquals(golden.getInt("bytes"), output.text.toByteArray().size)
            assertEquals(golden.getString("sha256"), output.sha256)
            if (output.isSourceMap) {
                val sourceMap = JSONObject(output.text)
                assertEquals(3, sourceMap.getInt("version"))
                assertTrue(sourceMap.getString("mappings").isNotEmpty())
                assertFalse(sourceMap.has("sourcesContent"))
            }
        }
    }

    private fun assertDiagnostic(
        expected: JSONObject,
        actual: R2ProjectDiagnostic?,
    ) {
        val diagnostic = requireNotNull(actual)
        assertEquals(expected.getString("phase"), diagnostic.phase.protocolValue)
        assertEquals(expected.getInt("category"), diagnostic.category)
        assertEquals(expected.getInt("code"), diagnostic.code)
        assertEquals(expected.getString("message"), diagnostic.message)
        assertEquals(expected.getString("sourcePath"), diagnostic.sourcePath)
        assertEquals(expected.getInt("start"), diagnostic.start)
        assertEquals(expected.getInt("length"), diagnostic.length)
        assertEquals(expected.getInt("line"), diagnostic.line)
        assertEquals(expected.getInt("column"), diagnostic.column)
    }

    private fun assertRetiredProjectSession(
        telemetry: CompilerHostLifecycleTelemetry,
    ) {
        assertFalse(telemetry.closed)
        assertFalse(telemetry.hasWebView)
        assertFalse(telemetry.hasNativePort)
        assertFalse(telemetry.hasPendingProbe)
        assertFalse(telemetry.workerReady)
    }

    private fun assertReleasedInputCursors(
        telemetry: R2ProjectInputTransportTelemetry,
        expectedComplete: Boolean,
    ) {
        assertEquals(expectedComplete, telemetry.complete)
        assertTrue(
            "Project input transport did not open any accepted file cursor: $telemetry",
            telemetry.openedCursorCount > 0,
        )
        assertEquals(
            "Project input transport did not close every opened cursor: $telemetry",
            telemetry.openedCursorCount,
            telemetry.closedCursorCount,
        )
        assertFalse(
            "Project input transport retained an active cursor: $telemetry",
            telemetry.activeCursor,
        )
        assertTrue(
            "Project input transport was not released: $telemetry",
            telemetry.released,
        )
    }

    private fun createExactCallerCapacitySnapshot(
        directory: File,
        declarations: R2TrustedDeclarations,
    ): R2FileBackedProjectSnapshot {
        val spool = R2PrivateProjectSpool.create(directory)
        var ownershipTransferred = false
        try {
            val projectRecords = ArrayList<R2FileBackedProjectRecord>(
                EXACT_CALLER_FILE_COUNT + declarations.files.size,
            )
            repeat(EXACT_CALLER_FILE_COUNT - 2) { index ->
                projectRecords += spool.appendExactAsciiBoundaryFile(
                    archivePath = "sources/capacity-${index.toString().padStart(2, '0')}.d.ts",
                    byteLength = TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
                    suffix = BOUNDARY_DECLARATION_SUFFIX,
                )
            }
            projectRecords += spool.appendExactAsciiBoundaryFile(
                archivePath = CAPACITY_TAIL_ARCHIVE_PATH,
                byteLength =
                    TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES -
                    BOUNDARY_MAIN_SOURCE.size,
                suffix = BOUNDARY_DECLARATION_SUFFIX,
            )
            projectRecords += spool.appendAsciiFile(
                archivePath = CAPACITY_MAIN_ARCHIVE_PATH,
                bytes = BOUNDARY_MAIN_SOURCE,
            )
            spool.finishArchiveExtraction()
            declarations.files.forEach { declaration ->
                projectRecords += spool.appendTrustedDeclaration(declaration)
            }
            return spool.seal(
                profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                projectRecords = projectRecords,
                rootNames = listOf("/$CAPACITY_MAIN_ARCHIVE_PATH"),
                limits = R2ProjectSnapshotLimits.FILE_BACKED_ARCHIVE,
            ).also {
                ownershipTransferred = true
            }
        } finally {
            if (!ownershipTransferred) {
                spool.close()
            }
        }
    }

    private fun R2PrivateProjectSpool.appendExactAsciiBoundaryFile(
        archivePath: String,
        byteLength: Long,
        suffix: ByteArray,
    ): R2FileBackedProjectRecord {
        val crc32 = CRC32()
        forEachBoundaryFileChunk(byteLength, suffix) { bytes, length ->
            crc32.update(bytes, 0, length)
        }
        val entry = ZipArchiveV1Entry(
            path = archivePath,
            compressionMethod = ZIP_STORED,
            compressedBytes = byteLength,
            uncompressedBytes = byteLength,
            crc32 = crc32.value,
        )
        begin(entry)
        forEachBoundaryFileChunk(byteLength, suffix) { bytes, length ->
            write(bytes, 0, length)
        }
        end(entry)
        return fileBackedRecord(archivePath)
    }

    private fun R2PrivateProjectSpool.appendAsciiFile(
        archivePath: String,
        bytes: ByteArray,
    ): R2FileBackedProjectRecord {
        val entry = ZipArchiveV1Entry(
            path = archivePath,
            compressionMethod = ZIP_STORED,
            compressedBytes = bytes.size.toLong(),
            uncompressedBytes = bytes.size.toLong(),
            crc32 = CRC32().apply { update(bytes) }.value,
        )
        begin(entry)
        write(bytes, 0, bytes.size)
        end(entry)
        return fileBackedRecord(archivePath)
    }

    private fun R2PrivateProjectSpool.fileBackedRecord(
        archivePath: String,
    ): R2FileBackedProjectRecord {
        val spooled = requireNotNull(archiveEntries[archivePath])
        return R2FileBackedProjectRecord(
            metadata = R2ProjectFileMetadata(
                path = "/$archivePath",
                utf8Bytes = spooled.byteLength,
                utf16CodeUnits = spooled.byteLength.toInt(),
                sha256 = spooled.sha256,
            ),
            offset = spooled.offset,
        )
    }

    private fun forEachBoundaryFileChunk(
        byteLength: Long,
        suffix: ByteArray,
        consume: (ByteArray, Int) -> Unit,
    ) {
        consume(BOUNDARY_COMMENT_PREFIX, BOUNDARY_COMMENT_PREFIX.size)
        var remaining = byteLength -
            BOUNDARY_COMMENT_PREFIX.size -
            suffix.size
        check(remaining >= 0L)
        while (remaining > 0L) {
            val count = minOf(BOUNDARY_FILLER.size.toLong(), remaining).toInt()
            consume(BOUNDARY_FILLER, count)
            remaining -= count
        }
        consume(suffix, suffix.size)
    }

    private fun withCompilerHost(
        timeoutSeconds: Long = TEST_TIMEOUT_SECONDS,
        block: (TypeScriptWebViewCompilerHost) -> Unit,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        val host = onMain {
            TypeScriptWebViewCompilerHost(context)
        }
        try {
            try {
                executor.submit(Callable { block(host) })
                    .get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (error: ExecutionException) {
                throw error.cause ?: error
            }
        } finally {
            onMain {
                host.close()
            }
            executor.shutdownNow()
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val task = FutureTask(Callable(block))
        instrumentation.runOnMainSync(task)
        return task.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val FIXTURE_PATH =
            "r2-project-compiler/provisional-rhino.json"
        const val DEFAULT_LIBRARY_PATH = "/declarations/lib.d.ts"
        const val MAIN_SOURCE_PATH = "/sources/main.ts"
        const val MAIN_THREAD_BLOCK_TIMEOUT_SECONDS = 10L
        const val MAIN_THREAD_TIMEOUT_SECONDS = 10L
        const val PHASE_ENTRY_WAIT_SECONDS = 30L
        const val PHASE_TIMEOUT_MILLIS = 5_000L
        const val PHASE_TIMEOUT_SPIN_MILLIS = 10_000
        const val NATURAL_INGRESS_TIMEOUT_MILLIS = 5_000L
        const val ACTIVE_WORKER_TIMEOUT_MILLIS = 5_000L
        const val PROJECT_TIMEOUT_MILLIS = 1L
        const val SYNCHRONOUS_WORKER_SPIN_MILLIS = 10_000
        const val RESULT_FINALIZER_DELAY_MILLISECONDS = 500
        const val TEST_TIMEOUT_SECONDS = 90L
        const val CAPACITY_TEST_TIMEOUT_SECONDS = 180L
        const val ACTIVE_RENDERER_CAPACITY_TEST_TIMEOUT_SECONDS = 300L
        const val REPEATED_RESOURCE_TEST_TIMEOUT_SECONDS = 900L
        const val CAPACITY_PHASE_WAIT_SECONDS = 150L
        const val SUCCESSOR_RENDERER_OBSERVATION_SPIN_MILLIS = 10_000
        const val EXACT_CALLER_FILE_COUNT = 9
        const val EXACT_CALLER_CAPACITY_BYTES = 64L * 1024L * 1024L
        const val REPEATED_RESOURCE_CYCLE_COUNT = 3
        const val RESOURCE_PSS_SAMPLE_COUNT = 5
        const val RESOURCE_PSS_SAMPLE_INTERVAL_MILLIS = 100L
        const val LIVE_INPUT_TELEMETRY_POLL_MILLIS = 5L
        const val NATURAL_PHASE_TIMEOUT_CALIBRATION_RUNS = 3
        const val NATURAL_PHASE_TIMEOUT_FAULT_ATTEMPTS = 1
        const val NATURAL_PHASE_TIMEOUT_MINIMUM_MARGIN_MILLIS = 100L
        const val NATURAL_PHASE_TIMEOUT_PROGRESS_SEQUENCE = 2
        const val NATURAL_PHASE_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS = 5_000L
        const val NATURAL_PARSE_TIMEOUT_CALIBRATION_RUNS = 3
        const val NATURAL_PARSE_TIMEOUT_PRECONDITION_RUNS = 2
        const val NATURAL_PARSE_TIMEOUT_FAULT_ATTEMPTS = 1
        const val NATURAL_PARSE_TIMEOUT_MINIMUM_MARGIN_MILLIS = 100L
        const val NATURAL_PARSE_TIMEOUT_PROGRESS_SEQUENCE = 2
        const val NATURAL_PARSE_TIMEOUT_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS =
            5_000L
        const val NATURAL_EMIT_TIMEOUT_CALIBRATION_RUNS = 3
        const val NATURAL_EMIT_TIMEOUT_PRECONDITION_RUNS = 2
        const val NATURAL_EMIT_TIMEOUT_FAULT_ATTEMPTS = 1
        const val NATURAL_EMIT_TIMEOUT_MINIMUM_MARGIN_MILLIS = 100L
        const val NATURAL_EMIT_TIMEOUT_PROGRESS_SEQUENCE = 2
        const val NATURAL_EMIT_TIMEOUT_MAXIMUM_DEADLINE_OVERSHOOT_MILLIS =
            5_000L
        const val NATURAL_INGRESS_CURSOR_OBSERVATION_BYTES =
            8L * 1024L * 1024L
        const val MAX_REPEATED_RENDERER_MEDIAN_GROWTH_KIB = 64 * 1024
        const val CAPACITY_SPOOL_DIRECTORY = "r2-p3b-capacity"
        const val NATURAL_PHASE_SPOOL_DIRECTORY = "r2-p3b-natural-phase"
        const val NATURAL_PHASE_TIMEOUT_SPOOL_DIRECTORY =
            "r2-p3b-natural-phase-timeout"
        const val NATURAL_PARSE_TIMEOUT_SPOOL_DIRECTORY =
            "r2-p3b-natural-parse-timeout"
        const val NATURAL_EMIT_TIMEOUT_SPOOL_DIRECTORY =
            "r2-p3b-natural-emit-timeout"
        const val CAPACITY_LOG_TAG = "R2P3bCapacity"
        const val PHASE_LOG_TAG = "R2P3bPhase"
        const val NATURAL_PHASE_LOG_TAG = "R2P3bNaturalPhase"
        const val NATURAL_PHASE_TIMEOUT_LOG_TAG =
            "R2P3bNaturalPhaseTimeout"
        const val NATURAL_PARSE_TIMEOUT_LOG_TAG =
            "R2P3bNaturalParseTimeout"
        const val NATURAL_EMIT_TIMEOUT_LOG_TAG =
            "R2P3bNaturalEmitTimeout"
        const val RESOURCE_LOG_TAG = "R2P3bResource"
        const val NATURAL_PHASE_STOP_PROGRESS_SEQUENCE = 2
        const val CAPACITY_SPOOL_FILE_PREFIX = "r2-project-vfs-"
        const val CAPACITY_MAIN_ARCHIVE_PATH = "sources/capacity-main.ts"
        const val CAPACITY_TAIL_ARCHIVE_PATH = "sources/capacity-07.d.ts"
        const val ZIP_STORED = 0
        val BOUNDARY_COMMENT_PREFIX = "/*".toByteArray(StandardCharsets.US_ASCII)
        val BOUNDARY_DECLARATION_SUFFIX = "*/\n".toByteArray(StandardCharsets.US_ASCII)
        val BOUNDARY_MAIN_SOURCE =
            "export const deviceCapacityBoundary: number = 42;\n"
                .toByteArray(StandardCharsets.US_ASCII)
        val BOUNDARY_FILLER = ByteArray(16 * 1024) { 'a'.code.toByte() }
    }
}
