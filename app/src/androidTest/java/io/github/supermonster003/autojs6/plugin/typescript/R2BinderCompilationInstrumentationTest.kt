package io.github.supermonster003.autojs6.plugin.typescript

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Limits
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Exception
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1FailureKind
import io.github.supermonster003.autojs6.plugin.typescript.archive.ZipArchiveV1Validator
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.math.abs
import org.autojs.plugin.typescript.api.ITypeScriptCompilationCallback
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2BinderCompilationInstrumentationTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val activityManager
        get() = context.getSystemService(ActivityManager::class.java)

    @Test
    fun publicPreviewRequiresAdditiveMetadataAndPostReturnAcknowledgement() {
        bindCompilerService().use { session ->
            val fixture = createFixture(
                requestId = newRequestId("public-preview"),
                sources = linkedMapOf(
                    "sources/main.ts" to
                        "export const publicPreview: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
            )
            try {
                val compilerInfo = session.compiler.compilerInfo
                assertFalse(
                    compilerInfo.getBoolean(
                        TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                        true,
                    ),
                )
                assertFalse(
                    compilerInfo.getBoolean(
                        TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION,
                        true,
                    ),
                )
                assertFalse(
                    compilerInfo.getBoolean(
                        TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS,
                        true,
                    ),
                )
                assertFalse(
                    compilerInfo.getBoolean(
                        TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3,
                        true,
                    ),
                )
                assertEquals(
                    R2RuntimeCapabilityMetadata.fingerprint,
                    compilerInfo.getString(
                        TypeScriptCompilerContract
                            .KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
                    ),
                )
                assertEquals(
                    R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
                    compilerInfo.getInt(TypeScriptR2Contract.KEY_PROFILE_REVISION, -1),
                )
                assertEquals(
                    R2CompilationProfileRegistry.CONFIG_SCHEMA_REVISION,
                    compilerInfo.getInt(
                        TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
                        -1,
                    ),
                )
                assertEquals(
                    R2CompilationProfileRegistry.CONFIG_KIND_NONE,
                    compilerInfo.getString(TypeScriptR2Contract.KEY_CONFIG_KIND),
                )
                assertEquals(
                    TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
                    compilerInfo.getInt(
                        TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
                        -1,
                    ),
                )
                assertEquals(
                    TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
                    compilerInfo.getInt(
                        TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
                        -1,
                    ),
                )
                assertEquals(
                    fixture.declarationProfileFingerprint,
                    compilerInfo.getString(
                        TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
                    ),
                )

                val callback = RecordingCallback()
                val ticket = startPublicCompilationWhenSupervisorReady(
                    compiler = session.compiler,
                    fixture = fixture,
                    callback = callback,
                )
                callback.markStartReturned()
                val token = assertAccepted(ticket, fixture.requestId)
                assertEquals(
                    TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
                    ticket.getLong(
                        TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_TIMEOUT_MS,
                        -1L,
                    ),
                )
                assertFalse(
                    session.compiler.cancelCompilation(
                        publicAcknowledgement(
                            fixture.requestId,
                            "$token-wrong",
                        ),
                    ),
                )
                assertTrue(
                    session.compiler.cancelCompilation(
                        publicAcknowledgement(fixture.requestId, token),
                    ),
                )

                val terminal = callback.awaitTerminal()
                assertFalse(callback.callbackBeforeStartReturned)
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_EMITTED,
                    terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
                )
                assertEquals(1, callback.terminalCount.get())
            } finally {
                fixture.delete()
            }
        }
    }

    @Test
    fun emittedProjectCommitsValidatedOutputBeforeExactlyOneCallback() {
        bindCompilerService().use { session ->
            val fixture = createFixture(
                requestId = newRequestId("emitted"),
                sources = linkedMapOf(
                    "sources/helper.ts" to
                        "export const value: number = 41;\n",
                    "sources/main.ts" to
                        "import { value } from \"./helper\";\n" +
                        "export const answer: number = value + 1;\n",
                ),
                rootPaths = listOf("sources/helper.ts", "sources/main.ts"),
            )
            try {
                val callback = RecordingCallback()
                val ticket = startCompilation(
                    compiler = session.compiler,
                    fixture = fixture,
                    callback = callback,
                )
                callback.markStartReturned()
                val token = assertAccepted(ticket, fixture.requestId)
                assertTrue(
                    session.compiler.cancelCompilation(
                        acknowledgement(fixture.requestId, token),
                    ),
                )

                val terminal = callback.awaitTerminal()
                assertFalse(callback.callbackBeforeStartReturned)
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_EMITTED,
                    terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
                )
                assertTrue(
                    terminal.getBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, false),
                )
                assertEquals(
                    0,
                    terminal.getInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, -1),
                )
                assertEquals(
                    2,
                    terminal.getInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, -1),
                )
                val manifestSha = requireNotNull(
                    terminal.getString(
                        TypeScriptCompilerContract.KEY_OUTPUT_MANIFEST_SHA256,
                    ),
                )

                ZipArchiveV1Validator.validate(
                    fixture.output,
                    ZipArchiveV1Limits.OUTPUT,
                )
                ZipFile(fixture.output).use { archive ->
                    val names = archive.entries().asSequence().map { it.name }.toSet()
                    assertEquals(
                        setOf(
                            R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                            R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                            "modules/helper.js",
                            "modules/main.js",
                            "maps/helper.js.map",
                            "maps/main.js.map",
                        ),
                        names,
                    )
                    val manifest = archive.getInputStream(
                        archive.getEntry(R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH),
                    ).use { it.readBytes() }
                    assertEquals(manifestSha, R2Digests.sha256(manifest))
                    val authority = R2StrictJson.parseObject(manifest)
                    assertEquals(fixture.requestId, authority["requestId"])
                    assertEquals("binder-project", authority["projectId"])
                    assertEquals(
                        TypeScriptCompilerContract.PROFILE_RHINO,
                        authority["targetProfile"],
                    )
                    assertEquals(
                        fixture.compilerDistributionFingerprint,
                        authority["compilerDistributionFingerprint"],
                    )
                    assertEquals(
                        fixture.declarationsFingerprint,
                        authority["declarationsFingerprint"],
                    )
                    assertEquals(
                        R2RuntimeCapabilityMetadata.fingerprint,
                        authority["runtimeCapabilitiesFingerprint"],
                    )
                    assertEquals(
                        fixture.inputArchiveSha256,
                        authority["inputArchiveSha256"],
                    )
                    assertEquals(
                        fixture.requestManifestSha256,
                        authority["requestManifestSha256"],
                    )
                }
                assertEquals(1, callback.terminalCount.get())
                SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
                assertEquals(1, callback.terminalCount.get())
            } finally {
                fixture.delete()
            }
        }
    }

    @Test
    fun rhinoAndNodeRevisionOneProduceDeterministicBundlesThroughPublicShapedService() {
        bindCompilerService().use { session ->
            val compilerInfo = session.compiler.compilerInfo
            listOf(
                TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION,
                TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS,
                TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3,
                TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED,
            ).forEach { capability ->
                assertFalse(
                    "$capability must remain disabled while X1 uses private admission.",
                    compilerInfo.getBoolean(capability, true),
                )
            }

            listOf(
                "rhino" to R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                "node-r1" to
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            ).forEach { (label, profileAuthority) ->
                val helperSpecifier =
                    if (
                        profileAuthority ==
                        R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
                    ) {
                        "./helper.js"
                    } else {
                        "./helper"
                    }
                val fixtures = mutableListOf<CompilationFixture>()
                try {
                    listOf("a", "b").forEach { repetition ->
                        fixtures += createFixture(
                            requestId = newRequestId("x1-$label-$repetition"),
                            sources = linkedMapOf(
                                "sources/helper.ts" to
                                    "export const value: number = 41;\n",
                                "sources/main.ts" to
                                    "import { value } from \"$helperSpecifier\";\n" +
                                    "export const answer: number = value + 1;\n",
                            ),
                            rootPaths = listOf(
                                "sources/helper.ts",
                                "sources/main.ts",
                            ),
                            profileAuthority = profileAuthority,
                        )
                    }
                    assertEquals(fixtures[0].requestId.length, fixtures[1].requestId.length)
                    val bundles = fixtures.map { fixture ->
                        val terminal = compileAndAcknowledge(
                            compiler = session.compiler,
                            fixture = fixture,
                            request = fixture.x1PublicShapedRequest(),
                            publicShapedAcknowledgement = true,
                        )
                        assertEmittedTerminal(terminal)
                        assertEquals(
                            2,
                            terminal.getInt(
                                TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT,
                                -1,
                            ),
                        )
                        readStableServiceBundle(
                            fixture = fixture,
                            terminal = terminal,
                        )
                    }
                    assertStableServiceBundlesEqual(
                        profileAuthority = profileAuthority,
                        first = bundles[0],
                        second = bundles[1],
                    )
                    assertNoKnownR2TransientNames("X1 $label deterministic bundle")
                } finally {
                    fixtures.forEach(CompilationFixture::delete)
                }
            }
        }
    }

    @Test
    fun rhinoAndNodeRevisionOneStopEmitForSourceDiagnosticsThroughPublicShapedService() {
        bindCompilerService().use { session ->
            val compilerInfo = session.compiler.compilerInfo
            listOf(
                TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION,
                TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS,
                TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3,
                TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED,
            ).forEach { capability ->
                assertFalse(
                    "$capability must remain disabled while X2 uses private admission.",
                    compilerInfo.getBoolean(capability, true),
                )
            }

            val sourceCases = listOf(
                X2SourceDiagnosticCase(
                    label = "syntactic",
                    expectedPhase = R2ProjectDiagnosticPhase.SYNTACTIC,
                    expectedCode = 1_109,
                    expectedMessage = "Expression expected.",
                    expectedStart = 29,
                    expectedLength = 1,
                    expectedLine = 1,
                    expectedColumn = 30,
                    source = "export const value: number = ;\n",
                ),
                X2SourceDiagnosticCase(
                    label = "semantic",
                    expectedPhase = R2ProjectDiagnosticPhase.SEMANTIC,
                    expectedCode = 2_322,
                    expectedMessage =
                        "Type 'number' is not assignable to type 'string'.",
                    expectedStart = 6,
                    expectedLength = 5,
                    expectedLine = 1,
                    expectedColumn = 7,
                    source = "const value: string = 42;\nexport { value };\n",
                ),
            )
            listOf(
                "rhino" to R2CompilationProfileRegistry.PROVISIONAL_RHINO,
                "node-r1" to
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            ).forEach { (profileLabel, profileAuthority) ->
                sourceCases.forEach { sourceCase ->
                    val fixture = createFixture(
                        requestId = newRequestId(
                            "x2-$profileLabel-${sourceCase.label}",
                        ),
                        sources = linkedMapOf(
                            X2_SOURCE_PATH to sourceCase.source,
                        ),
                        rootPaths = listOf(X2_SOURCE_PATH),
                        profileAuthority = profileAuthority,
                    )
                    try {
                        val terminal = compileAndAcknowledge(
                            compiler = session.compiler,
                            fixture = fixture,
                            request = fixture.x1PublicShapedRequest(),
                            publicShapedAcknowledgement = true,
                        )
                        assertX2DiagnosticsOnlyTerminalAndArchive(
                            fixture = fixture,
                            terminal = terminal,
                            sourceCase = sourceCase,
                        )
                        awaitActiveJobCount(session.compiler, 0)
                        assertNoKnownR2TransientNames(
                            "X2 $profileLabel ${sourceCase.label} diagnostics-only",
                        )
                    } finally {
                        fixture.delete()
                    }
                }
            }
        }
    }

    @Test
    fun storedContainerCapacityAcceptsExact64MiBAndRejectsPlusOne() {
        exerciseCapacityBoundaryPair(
            rejectedBoundary =
                R2CapacityArchiveBoundary.STORED_CONTAINER_PLUS_ONE,
            acceptedBoundary =
                R2CapacityArchiveBoundary.STORED_CONTAINER_INCLUSIVE,
        )
    }

    @Test
    fun deflatedCapacityAcceptsExact64MiBExpandedAndRejectsPlusOne() {
        exerciseCapacityBoundaryPair(
            rejectedBoundary =
                R2CapacityArchiveBoundary.DEFLATED_UNCOMPRESSED_PLUS_ONE,
            acceptedBoundary =
                R2CapacityArchiveBoundary.DEFLATED_UNCOMPRESSED_INCLUSIVE,
        )
    }

    @Test
    fun repeatedExact64BinderJobsReachCompilerPssFdAndTransientCleanupPlateau() {
        bindCompilerService().use { session ->
            val processResourceProbe = RemoteProcessResourceProbe(context)
            val memoryProbe = R1WebViewProcessMemoryProbe(context)
            assertNoKnownR2TransientNames("initial")

            val warmFixture = createFixture(
                requestId = newRequestId("resource-warm"),
                sources = linkedMapOf(
                    "sources/main.ts" to
                        "export const resourceWarm: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
            )
            try {
                assertEmittedTerminal(
                    terminal = compileAndAcknowledge(
                        compiler = session.compiler,
                        fixture = warmFixture,
                    ),
                )
            } finally {
                warmFixture.delete()
            }
            awaitActiveJobCount(session.compiler, 0)
            assertNoKnownR2TransientNames("warm baseline")

            val compilerPid = awaitCompilerProcessPid(
                session.serviceProcessName,
            )
            val compilerIdentity =
                processResourceProbe.requireIdentity(compilerPid)
            val liveCompiler = R1LiveProcess(
                pid = compilerIdentity.pid,
                processName = session.serviceProcessName,
                startTimeTicks = compilerIdentity.startTimeTicks,
            )
            processResourceProbe.awaitR2TransientFileDescriptors(
                pid = compilerPid,
                label = "R2 warm baseline transient FD cleanup",
                expectedCount = 0,
                expectedIdentity = compilerIdentity,
            )
            val firstWarmPss = memoryProbe.samplePss(
                process = liveCompiler,
                sampleCount = RESOURCE_PSS_SAMPLE_COUNT,
                sampleIntervalMillis = RESOURCE_PSS_SAMPLE_INTERVAL_MILLIS,
            )
            SystemClock.sleep(RESOURCE_PSS_BASELINE_SETTLE_MILLIS)
            val warmPss = memoryProbe.samplePss(
                process = liveCompiler,
                sampleCount = RESOURCE_PSS_SAMPLE_COUNT,
                sampleIntervalMillis = RESOURCE_PSS_SAMPLE_INTERVAL_MILLIS,
            )
            assertTrue(
                "Adjacent warm compiler-service PSS batches did not settle: " +
                    "first=$firstWarmPss second=$warmPss",
                abs(
                    warmPss.medianPssKiB -
                        firstWarmPss.medianPssKiB,
                ) <= MAX_ADJACENT_WARM_MEDIAN_DELTA_KIB,
            )
            val warmFileDescriptors =
                processResourceProbe.awaitStableFileDescriptors(
                    pid = compilerPid,
                    label = "R2 post-PSS warm compiler FD baseline",
                    expectedIdentity = compilerIdentity,
                )
            Log.i(
                RESOURCE_LOG_TAG,
                "BINDER_BASELINE compiler=$liveCompiler " +
                    "medianPssKiB=${warmPss.medianPssKiB} " +
                    "maximumPssKiB=${warmPss.maximumPssKiB} " +
                    "samples=${warmPss.samplesPssKiB} " +
                    "fdCount=${warmFileDescriptors.count}",
            )

            val cyclePss = ArrayList<R1SingleProcessPssBatch>(
                REPEATED_RESOURCE_CYCLE_COUNT,
            )
            val cycleFileDescriptors = ArrayList<RemoteFileDescriptorSnapshot>(
                REPEATED_RESOURCE_CYCLE_COUNT,
            )
            repeat(REPEATED_RESOURCE_CYCLE_COUNT) { cycleIndex ->
                R2CapacityArchiveFixture.create(
                    context = context,
                    requestId = newRequestId(
                        "resource-exact64-${cycleIndex + 1}",
                    ),
                    boundary =
                        R2CapacityArchiveBoundary.STORED_CONTAINER_INCLUSIVE,
                ).use { fixture ->
                    assertEquals(
                        TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
                        fixture.containerBytes,
                    )
                    val output = createCapacityOutput()
                    try {
                        val callback = RecordingCallback()
                        val startedAt = SystemClock.elapsedRealtime()
                        val ticket = startCapacityCompilation(
                            compiler = session.compiler,
                            fixture = fixture,
                            output = output,
                            callback = callback,
                        )
                        callback.markStartReturned()
                        val token = assertAccepted(ticket, fixture.requestId)
                        assertTrue(
                            session.compiler.cancelCompilation(
                                acknowledgement(fixture.requestId, token),
                            ),
                        )
                        val terminal = callback.awaitTerminal(
                            CAPACITY_CALLBACK_TIMEOUT_SECONDS,
                        )
                        assertEmittedTerminal(terminal)
                        assertCapacityOutput(
                            fixture = fixture,
                            output = output,
                            terminal = terminal,
                        )
                        assertEquals(1, callback.terminalCount.get())
                        SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
                        assertEquals(1, callback.terminalCount.get())
                        Log.i(
                            RESOURCE_LOG_TAG,
                            "BINDER_CYCLE_TERMINAL cycle=${cycleIndex + 1} " +
                                "requestId=${fixture.requestId} " +
                                "containerBytes=${fixture.containerBytes} " +
                                "uncompressedBytes=${fixture.uncompressedBytes} " +
                                "elapsedMs=" +
                                "${SystemClock.elapsedRealtime() - startedAt}",
                        )
                    } finally {
                        deleteTestFile(
                            file = output,
                            label = "R2 exact64 output fixture",
                        )
                    }
                }

                awaitActiveJobCount(session.compiler, 0)
                assertNoKnownR2TransientNames(
                    "exact64 cycle ${cycleIndex + 1}",
                )
                assertEquals(
                    "The compiler service process identity changed across exact64 cycles.",
                    compilerIdentity,
                    processResourceProbe.requireIdentity(compilerPid),
                )
                processResourceProbe.awaitR2TransientFileDescriptors(
                    pid = compilerPid,
                    label =
                        "R2 exact64 cycle ${cycleIndex + 1} transient FD cleanup",
                    expectedCount = 0,
                    expectedIdentity = compilerIdentity,
                )
                val fileDescriptors =
                    processResourceProbe.awaitStableFileDescriptors(
                        pid = compilerPid,
                        label =
                            "R2 exact64 cycle ${cycleIndex + 1} stable FD state",
                        expectedIdentity = compilerIdentity,
                    )
                val pss = memoryProbe.samplePss(
                    process = liveCompiler,
                    sampleCount = RESOURCE_PSS_SAMPLE_COUNT,
                    sampleIntervalMillis =
                        RESOURCE_PSS_SAMPLE_INTERVAL_MILLIS,
                )
                cycleFileDescriptors += fileDescriptors
                cyclePss += pss
                val maximumGrowthFromWarmKiB =
                    if (
                        Build.VERSION.SDK_INT <=
                        Build.VERSION_CODES.N_MR1
                    ) {
                        MAX_LEGACY_SINGLE_PROCESS_COMPILER_MEDIAN_GROWTH_FROM_WARM_KIB
                    } else {
                        MAX_COMPILER_MEDIAN_GROWTH_FROM_WARM_KIB
                    }
                assertTrue(
                    "Compiler-service median PSS exceeded the provisional repeated-resource " +
                        "allowance after cycle ${cycleIndex + 1}: " +
                        "baseline=$warmPss cycle=$pss " +
                        "allowanceKiB=$maximumGrowthFromWarmKiB",
                    pss.medianPssKiB <=
                        warmPss.medianPssKiB +
                        maximumGrowthFromWarmKiB,
                )
                Log.i(
                    RESOURCE_LOG_TAG,
                    "BINDER_CYCLE_RESOURCE cycle=${cycleIndex + 1} " +
                        "compiler=$liveCompiler " +
                        "medianPssKiB=${pss.medianPssKiB} " +
                        "maximumPssKiB=${pss.maximumPssKiB} " +
                        "samples=${pss.samplesPssKiB} " +
                        "fdCount=${fileDescriptors.count}",
                )
            }

            assertNoUnexpectedFileDescriptorGrowth(
                baseline = warmFileDescriptors,
                current = cycleFileDescriptors.first(),
                expectedProcessPid = compilerPid,
            )
            assertEquals(
                "The mature compiler-service FD target multiset grew or churned after cycle 1.",
                cycleFileDescriptors.first().targets.sorted(),
                cycleFileDescriptors[1].targets.sorted(),
            )
            assertEquals(
                "The mature compiler-service FD target multiset grew or churned after cycle 1.",
                cycleFileDescriptors.first().targets.sorted(),
                cycleFileDescriptors.last().targets.sorted(),
            )
            cyclePss.drop(1).forEachIndexed { index, maturePss ->
                assertTrue(
                    "Compiler-service median PSS did not plateau after mature cycle 1: " +
                        "cycle=${index + 2} cycles=$cyclePss",
                    maturePss.medianPssKiB <=
                        cyclePss.first().medianPssKiB +
                        MAX_COMPILER_MEDIAN_GROWTH_AFTER_MATURE_CYCLE_KIB,
                )
            }

            val successorFixture = createFixture(
                requestId = newRequestId("resource-successor"),
                sources = linkedMapOf(
                    "sources/main.ts" to
                        "export const resourceSuccessor: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
            )
            try {
                assertEmittedTerminal(
                    terminal = compileAndAcknowledge(
                        compiler = session.compiler,
                        fixture = successorFixture,
                    ),
                )
            } finally {
                successorFixture.delete()
            }
            awaitActiveJobCount(session.compiler, 0)
            assertNoKnownR2TransientNames("small successor")
            processResourceProbe.awaitR2TransientFileDescriptors(
                pid = compilerPid,
                label = "R2 small successor transient FD cleanup",
                expectedCount = 0,
                expectedIdentity = compilerIdentity,
            )
            assertEquals(
                compilerIdentity,
                processResourceProbe.requireIdentity(compilerPid),
            )
            Log.i(
                RESOURCE_LOG_TAG,
                "BINDER_MATRIX_PASS api=${Build.VERSION.SDK_INT} " +
                    "webView=" +
                    "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                    "cycles=$REPEATED_RESOURCE_CYCLE_COUNT " +
                    "compilerPid=$compilerPid " +
                    "compilerStart=${compilerIdentity.startTimeTicks} " +
                    "warmMedianPssKiB=${warmPss.medianPssKiB} " +
                    "cycleMedianPssKiB=" +
                    cyclePss.joinToString(prefix = "[", postfix = "]") { batch ->
                        batch.medianPssKiB.toString()
                    } +
                    " matureFdCount=${cycleFileDescriptors.first().count}",
            )
        }
    }

    @Test
    fun exact64NaturalDeadlinePublishesTimeoutCleansBinderStateAndRecovers() {
        bindCompilerService().use { session ->
            val processResourceProbe = RemoteProcessResourceProbe(context)
            assertNoKnownR2TransientNames("natural-timeout initial")

            val warmFixture = createFixture(
                requestId = newRequestId("natural-timeout-warm"),
                sources = linkedMapOf(
                    "sources/main.ts" to
                        "export const naturalTimeoutWarm: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
            )
            try {
                assertEmittedTerminal(
                    terminal = compileAndAcknowledge(
                        compiler = session.compiler,
                        fixture = warmFixture,
                    ),
                )
            } finally {
                warmFixture.delete()
            }
            awaitActiveJobCount(session.compiler, 0)
            assertNoKnownR2TransientNames("natural-timeout warm baseline")

            val compilerPid = awaitCompilerProcessPid(session.serviceProcessName)
            val compilerIdentity =
                processResourceProbe.requireIdentity(compilerPid)
            processResourceProbe.awaitR2TransientFileDescriptors(
                pid = compilerPid,
                label = "R2 natural-timeout warm transient FD cleanup",
                expectedCount = 0,
                expectedIdentity = compilerIdentity,
            )

            val timeoutCallback = RecordingCallback()
            R2CapacityArchiveFixture.create(
                context = context,
                requestId = newRequestId("natural-ingress-timeout"),
                boundary =
                    R2CapacityArchiveBoundary.STORED_CONTAINER_INCLUSIVE,
            ).use { fixture ->
                assertEquals(
                    TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
                    fixture.containerBytes,
                )
                val output = createCapacityOutput()
                try {
                    val startedAt = SystemClock.elapsedRealtime()
                    val ticket = startCapacityCompilation(
                        compiler = session.compiler,
                        fixture = fixture,
                        output = output,
                        callback = timeoutCallback,
                        timeoutMs =
                            NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS,
                    )
                    timeoutCallback.markStartReturned()
                    val token = assertAccepted(ticket, fixture.requestId)
                    assertTrue(
                        session.compiler.cancelCompilation(
                            publicAcknowledgement(fixture.requestId, token),
                        ),
                    )

                    val terminal = timeoutCallback.awaitTerminal(
                        CAPACITY_CALLBACK_TIMEOUT_SECONDS,
                    )
                    assertFalse(timeoutCallback.callbackBeforeStartReturned)
                    assertEquals(
                        fixture.requestId,
                        terminal.getString(TypeScriptCompilerContract.KEY_REQUEST_ID),
                    )
                    assertEquals(
                        TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                        terminal.getString(
                            TypeScriptCompilerContract.KEY_OUTCOME,
                        ),
                    )
                    assertFalse(
                        terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED,
                            true,
                        ),
                    )
                    assertEquals(
                        TypeScriptCompilerContract.ERROR_TIMEOUT,
                        terminal.getString(
                            TypeScriptCompilerContract.KEY_ERROR_CODE,
                        ),
                    )
                    assertEquals(
                        0,
                        terminal.getInt(
                            TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT,
                            -1,
                        ),
                    )
                    assertEquals(
                        0,
                        terminal.getInt(
                            TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT,
                            -1,
                        ),
                    )
                    assertFalse(
                        terminal.containsKey(
                            TypeScriptCompilerContract
                                .KEY_OUTPUT_MANIFEST_SHA256,
                        ),
                    )
                    assertTrue(
                        "Natural ingress Binder timeout fired before its fixed deadline.",
                        terminal.getLong(
                            TypeScriptCompilerContract.KEY_ELAPSED_MS,
                            -1L,
                        ) >= NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS,
                    )
                    val wallElapsedMillis =
                        SystemClock.elapsedRealtime() - startedAt
                    assertTrue(
                        "Natural ingress Binder timeout returned before its wall-clock deadline.",
                        wallElapsedMillis >= NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS,
                    )
                    assertEquals(0L, output.length())
                    assertEquals(1, timeoutCallback.terminalCount.get())
                    SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
                    assertEquals(1, timeoutCallback.terminalCount.get())
                    Log.i(
                        NATURAL_TIMEOUT_LOG_TAG,
                        "BINDER_TIMEOUT_PASS api=${Build.VERSION.SDK_INT} " +
                            "requestId=${fixture.requestId} " +
                            "containerBytes=${fixture.containerBytes} " +
                            "timeoutMs=$NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS " +
                            "terminalElapsedMs=" +
                            terminal.getLong(
                                TypeScriptCompilerContract.KEY_ELAPSED_MS,
                            ) +
                            " wallElapsedMs=$wallElapsedMillis",
                    )
                } finally {
                    deleteTestFile(
                        file = output,
                        label = "R2 natural ingress timeout output fixture",
                    )
                }
            }

            awaitActiveJobCount(session.compiler, 0)
            assertNoKnownR2TransientNames("natural-timeout terminal")
            assertEquals(
                "The compiler service process changed across natural ingress timeout cleanup.",
                compilerIdentity,
                processResourceProbe.requireIdentity(compilerPid),
            )
            processResourceProbe.awaitR2TransientFileDescriptors(
                pid = compilerPid,
                label = "R2 natural-timeout terminal transient FD cleanup",
                expectedCount = 0,
                expectedIdentity = compilerIdentity,
            )

            val successorFixture = createFixture(
                requestId = newRequestId("natural-timeout-successor"),
                sources = linkedMapOf(
                    "sources/main.ts" to
                        "export const naturalTimeoutSuccessor: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
            )
            try {
                assertEmittedTerminal(
                    terminal = compileAndAcknowledge(
                        compiler = session.compiler,
                        fixture = successorFixture,
                    ),
                )
            } finally {
                successorFixture.delete()
            }
            awaitActiveJobCount(session.compiler, 0)
            assertNoKnownR2TransientNames("natural-timeout successor")
            processResourceProbe.awaitR2TransientFileDescriptors(
                pid = compilerPid,
                label = "R2 natural-timeout successor transient FD cleanup",
                expectedCount = 0,
                expectedIdentity = compilerIdentity,
            )
            assertEquals(
                compilerIdentity,
                processResourceProbe.requireIdentity(compilerPid),
            )
            assertEquals(1, timeoutCallback.terminalCount.get())
        }
    }

    @Test
    fun semanticFailureCommitsDiagnosticsOnlyArchive() {
        bindCompilerService().use { session ->
            val fixture = createFixture(
                requestId = newRequestId("diagnostics"),
                sources = linkedMapOf(
                    "sources/main.ts" to
                        "const value: string = 42;\nexport { value };\n",
                ),
                rootPaths = listOf("sources/main.ts"),
            )
            try {
                val callback = RecordingCallback()
                val ticket = startCompilation(session.compiler, fixture, callback)
                callback.markStartReturned()
                val token = assertAccepted(ticket, fixture.requestId)
                assertTrue(
                    session.compiler.cancelCompilation(
                        acknowledgement(fixture.requestId, token),
                    ),
                )

                val terminal = callback.awaitTerminal()
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                    terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
                )
                assertFalse(
                    terminal.getBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, true),
                )
                assertTrue(
                    terminal.getInt(
                        TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT,
                        0,
                    ) > 0,
                )
                assertEquals(
                    0,
                    terminal.getInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, -1),
                )
                ZipArchiveV1Validator.validate(
                    fixture.output,
                    ZipArchiveV1Limits.OUTPUT,
                )
                ZipFile(fixture.output).use { archive ->
                    assertEquals(
                        setOf(
                            R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                            R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                        ),
                        archive.entries().asSequence().map { it.name }.toSet(),
                    )
                }
            } finally {
                fixture.delete()
            }
        }
    }

    @Test
    fun cancelOwnsFullPipelineAndSharedSlotUntilEmptyOutputProof() {
        bindCompilerService().use { session ->
            val fixture = createFixture(
                requestId = newRequestId("cancel"),
                sources = linkedMapOf(
                    "sources/main.ts" to "export const value: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
                workerSpinMilliseconds = ACTIVE_WORKER_SPIN_MILLIS,
                timeoutMs = ACTIVE_JOB_TIMEOUT_MILLIS,
            )
            try {
                val callback = RecordingCallback()
                val ticket = startCompilation(session.compiler, fixture, callback)
                callback.markStartReturned()
                val token = assertAccepted(ticket, fixture.requestId)
                assertTrue(
                    session.compiler.cancelCompilation(
                        acknowledgement(fixture.requestId, token),
                    ),
                )

                val busyPrewarm = session.compiler.prewarm(validPrewarm())
                assertEquals(
                    TypeScriptCompilerContract.ERROR_BUSY,
                    busyPrewarm.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
                )
                assertFalse(
                    session.compiler.cancelCompilation(
                        cancellation(fixture.requestId, "$token-wrong"),
                    ),
                )
                assertTrue(
                    session.compiler.cancelCompilation(
                        cancellation(fixture.requestId, token),
                    ),
                )
                assertTrue(
                    session.compiler.cancelCompilation(
                        cancellation(fixture.requestId, token),
                    ),
                )

                val terminal = callback.awaitTerminal()
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_CANCELLED,
                    terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
                )
                assertEquals(0L, fixture.output.length())
                assertTrue(
                    "Terminal tombstone must preserve idempotent cancellation.",
                    session.compiler.cancelCompilation(
                        cancellation(fixture.requestId, token),
                    ),
                )
                awaitActiveJobCount(session.compiler, 0)
            } finally {
                fixture.delete()
            }
        }
    }

    @Test
    fun admissionDeadlineStopsWorkerAndPublishesOnlyAfterEmptyOutput() {
        bindCompilerService().use { session ->
            val fixture = createFixture(
                requestId = newRequestId("timeout"),
                sources = linkedMapOf(
                    "sources/main.ts" to "export const value: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
                workerSpinMilliseconds = ACTIVE_WORKER_SPIN_MILLIS,
                timeoutMs = ACTIVE_TIMEOUT_MILLIS,
            )
            try {
                val callback = RecordingCallback()
                val ticket = startCompilation(session.compiler, fixture, callback)
                callback.markStartReturned()
                val token = assertAccepted(ticket, fixture.requestId)
                assertTrue(
                    session.compiler.cancelCompilation(
                        acknowledgement(fixture.requestId, token),
                    ),
                )

                val terminal = callback.awaitTerminal()
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                    terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
                )
                assertEquals(
                    TypeScriptCompilerContract.ERROR_TIMEOUT,
                    terminal.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
                )
                assertEquals(0L, fixture.output.length())
                assertEquals(1, callback.terminalCount.get())
            } finally {
                fixture.delete()
            }
        }
    }

    @Test
    fun acknowledgementAfterUnacknowledgedDeadlineIsIdempotentAndNeverArmsCallback() {
        bindCompilerService().use { session ->
            val fixture = createFixture(
                requestId = newRequestId("late-ack"),
                sources = linkedMapOf(
                    "sources/main.ts" to "export const value: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
                timeoutMs = LATE_ACK_TIMEOUT_MILLIS,
                terminalCleanupDelayMilliseconds =
                    LATE_ACK_TERMINAL_CLEANUP_DELAY_MILLIS,
            )
            try {
                val callback = RecordingCallback()
                val ticket = startCompilation(session.compiler, fixture, callback)
                callback.markStartReturned()
                val token = assertAccepted(ticket, fixture.requestId)

                awaitStopCauseSelected(session.compiler)
                assertTrue(
                    "The exact late acknowledgement remains idempotently authorized.",
                    session.compiler.cancelCompilation(
                        acknowledgement(fixture.requestId, token),
                    ),
                )

                awaitActiveJobCount(session.compiler, 0)
                SystemClock.sleep(NO_CALLBACK_OBSERVATION_MILLIS)
                assertEquals(
                    "A late acknowledgement must not re-arm terminal delivery.",
                    0,
                    callback.terminalCount.get(),
                )
                assertEquals(0L, fixture.output.length())
            } finally {
                fixture.delete()
            }
        }
    }

    @Test
    fun invalidOuterAuthorityPreservesOutputAndDoesNotConsumeTheRequestOwner() {
        bindCompilerService().use { session ->
            val fixture = createFixture(
                requestId = newRequestId("invalid-authority"),
                sources = linkedMapOf(
                    "sources/main.ts" to "export const value: number = 42;\n",
                ),
                rootPaths = listOf("sources/main.ts"),
            )
            try {
                val nodeCallback = RecordingCallback()
                val nodeTicket = startCompilation(
                    compiler = session.compiler,
                    fixture = fixture,
                    callback = nodeCallback,
                    request = fixture.request().apply {
                        putString(
                            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
                            TypeScriptCompilerContract.PROFILE_NODE,
                        )
                    },
                )
                nodeCallback.markStartReturned()
                assertRejectedInvalid(nodeTicket, fixture.requestId)
                assertEquals(OUTPUT_SENTINEL, fixture.output.readText())

                val registeredRevisionTwo =
                    R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
                val revisionTwoCallback = RecordingCallback()
                val revisionTwoTicket = startCompilation(
                    compiler = session.compiler,
                    fixture = fixture,
                    callback = revisionTwoCallback,
                    request = fixture.request().apply {
                        putString(
                            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
                            TypeScriptCompilerContract.PROFILE_NODE,
                        )
                        putInt(
                            R2CompilationProbeProtocol.KEY_PROFILE_REVISION,
                            registeredRevisionTwo.profileRevision,
                        )
                        putString(
                            TypeScriptCompilerContract
                                .KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
                            registeredRevisionTwo.runtimeCapabilitiesFingerprint,
                        )
                    },
                )
                revisionTwoCallback.markStartReturned()
                assertRejectedInvalid(revisionTwoTicket, fixture.requestId)
                assertEquals(OUTPUT_SENTINEL, fixture.output.readText())

                val privateFallbackMutations: List<(Bundle) -> Unit> = listOf(
                    { request ->
                        request.putBoolean(R2CompilationProbeProtocol.KEY_ENABLED, false)
                    },
                    { request ->
                        request.putString(R2CompilationProbeProtocol.KEY_ENABLED, "true")
                    },
                    { request ->
                        request.putLong(R2CompilationProbeProtocol.KEY_PROFILE_REVISION, 1L)
                    },
                    { request ->
                        request.putString(
                            R2CompilationProbeProtocol.KEY_WORKER_SPIN_PHASE,
                            "none",
                        )
                    },
                )
                privateFallbackMutations.forEachIndexed { index, mutate ->
                    val fallbackCallback = RecordingCallback()
                    val fallbackRequest = fixture.publicRequest().also(mutate)
                    val fallbackTicket = startCompilation(
                        compiler = session.compiler,
                        fixture = fixture,
                        callback = fallbackCallback,
                        request = fallbackRequest,
                    )
                    fallbackCallback.markStartReturned()
                    assertRejectedInvalid(fallbackTicket, fixture.requestId)
                    assertEquals(
                        "Private debug fallback mutation $index changed caller output.",
                        OUTPUT_SENTINEL,
                        fixture.output.readText(),
                    )
                }

                val optionsCallback = RecordingCallback()
                val optionsTicket = startCompilation(
                    compiler = session.compiler,
                    fixture = fixture,
                    callback = optionsCallback,
                    request = fixture.request().apply {
                        putString(
                            TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON,
                            """{"strict":false}""",
                        )
                    },
                )
                optionsCallback.markStartReturned()
                assertRejectedInvalid(optionsTicket, fixture.requestId)
                assertEquals(OUTPUT_SENTINEL, fixture.output.readText())
                SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
                assertEquals(0, nodeCallback.terminalCount.get())
                assertEquals(0, revisionTwoCallback.terminalCount.get())
                assertEquals(0, optionsCallback.terminalCount.get())

                val validCallback = RecordingCallback()
                val validTicket = startCompilation(
                    compiler = session.compiler,
                    fixture = fixture,
                    callback = validCallback,
                )
                validCallback.markStartReturned()
                val token = assertAccepted(validTicket, fixture.requestId)
                assertTrue(
                    session.compiler.cancelCompilation(
                        acknowledgement(fixture.requestId, token),
                    ),
                )
                assertEquals(
                    TypeScriptCompilerContract.OUTCOME_EMITTED,
                    validCallback.awaitTerminal().getString(
                        TypeScriptCompilerContract.KEY_OUTCOME,
                    ),
                )
            } finally {
                fixture.delete()
            }
        }
    }

    private fun exerciseCapacityBoundaryPair(
        rejectedBoundary: R2CapacityArchiveBoundary,
        acceptedBoundary: R2CapacityArchiveBoundary,
    ) {
        bindCompilerService().use { session ->
            R2CapacityArchiveFixture.create(
                context = context,
                requestId = newRequestId(
                    "${rejectedBoundary.name.lowercase()}-rejected",
                ),
                boundary = rejectedBoundary,
            ).use { fixture ->
                assertCapacityBoundary(fixture)
                val output = createCapacityOutput()
                try {
                    val callback = RecordingCallback()
                    val startedAt = SystemClock.elapsedRealtime()
                    val ticket = startCapacityCompilation(
                        compiler = session.compiler,
                        fixture = fixture,
                        output = output,
                        callback = callback,
                    )
                    callback.markStartReturned()
                    val token = assertAccepted(ticket, fixture.requestId)
                    assertEquals(
                        TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
                        ticket.getLong(
                            TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_TIMEOUT_MS,
                            -1L,
                        ),
                    )
                    assertEquals(0L, output.length())
                    assertTrue(
                        session.compiler.cancelCompilation(
                            acknowledgement(fixture.requestId, token),
                        ),
                    )

                    val terminal = callback.awaitTerminal(
                        CAPACITY_CALLBACK_TIMEOUT_SECONDS,
                    )
                    assertFalse(callback.callbackBeforeStartReturned)
                    assertEquals(
                        TypeScriptCompilerContract.OUTCOME_FAILED,
                        terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
                    )
                    assertFalse(
                        terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED,
                            true,
                        ),
                    )
                    assertEquals(
                        TypeScriptCompilerContract.ERROR_ARCHIVE_QUOTA,
                        terminal.getString(
                            TypeScriptCompilerContract.KEY_ERROR_CODE,
                        ),
                    )
                    assertEquals(
                        0,
                        terminal.getInt(
                            TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT,
                            -1,
                        ),
                    )
                    assertEquals(
                        0,
                        terminal.getInt(
                            TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT,
                            -1,
                        ),
                    )
                    assertFalse(
                        terminal.containsKey(
                            TypeScriptCompilerContract.KEY_OUTPUT_MANIFEST_SHA256,
                        ),
                    )
                    assertEquals(0L, output.length())
                    assertEquals(1, callback.terminalCount.get())
                    SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
                    assertEquals(1, callback.terminalCount.get())
                    logCapacityEvidence(
                        disposition = "REJECTED",
                        fixture = fixture,
                        elapsedMs =
                            SystemClock.elapsedRealtime() - startedAt,
                    )
                } finally {
                    output.delete()
                }
            }

            R2CapacityArchiveFixture.create(
                context = context,
                requestId = newRequestId(
                    "${acceptedBoundary.name.lowercase()}-accepted",
                ),
                boundary = acceptedBoundary,
            ).use { fixture ->
                assertCapacityBoundary(fixture)
                val output = createCapacityOutput()
                try {
                    val callback = RecordingCallback()
                    val startedAt = SystemClock.elapsedRealtime()
                    val ticket = startCapacityCompilation(
                        compiler = session.compiler,
                        fixture = fixture,
                        output = output,
                        callback = callback,
                    )
                    callback.markStartReturned()
                    val token = assertAccepted(ticket, fixture.requestId)
                    assertEquals(
                        TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
                        ticket.getLong(
                            TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_TIMEOUT_MS,
                            -1L,
                        ),
                    )
                    assertEquals(0L, output.length())
                    assertTrue(
                        session.compiler.cancelCompilation(
                            acknowledgement(fixture.requestId, token),
                        ),
                    )

                    val terminal = callback.awaitTerminal(
                        CAPACITY_CALLBACK_TIMEOUT_SECONDS,
                    )
                    assertFalse(callback.callbackBeforeStartReturned)
                    assertEquals(
                        TypeScriptCompilerContract.OUTCOME_EMITTED,
                        terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
                    )
                    assertTrue(
                        terminal.getBoolean(
                            TypeScriptCompilerContract.KEY_SUCCEEDED,
                            false,
                        ),
                    )
                    assertEquals(
                        0,
                        terminal.getInt(
                            TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT,
                            -1,
                        ),
                    )
                    assertEquals(
                        1,
                        terminal.getInt(
                            TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT,
                            -1,
                        ),
                    )
                    assertCapacityOutput(
                        fixture = fixture,
                        output = output,
                        terminal = terminal,
                    )
                    assertEquals(1, callback.terminalCount.get())
                    SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
                    assertEquals(1, callback.terminalCount.get())
                    logCapacityEvidence(
                        disposition = "ACCEPTED",
                        fixture = fixture,
                        elapsedMs =
                            SystemClock.elapsedRealtime() - startedAt,
                    )
                } finally {
                    output.delete()
                }
            }
        }
    }

    private fun assertCapacityBoundary(
        fixture: R2CapacityArchiveFixture,
    ) {
        assertEquals(fixture.containerBytes, fixture.input.length())
        val sourceBytes =
            fixture.sourceDescriptors.sumOf { descriptor -> descriptor.size }
        val manifestBytes = fixture.uncompressedBytes - sourceBytes
        assertTrue(
            manifestBytes in 1L..TypeScriptCompilerContract.MAX_MANIFEST_BYTES,
        )
        fixture.sourceDescriptors.forEach { descriptor ->
            assertTrue(
                descriptor.size in
                    1L..TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
            )
        }
        val validationLimits =
            if (fixture.boundary.quotaDeltaBytes == 0L) {
                ZipArchiveV1Limits.INPUT
            } else {
                ZipArchiveV1Limits.INPUT.copy(
                    maximumContainerBytes =
                        TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES + 1L,
                    maximumTotalUncompressedBytes =
                        TypeScriptCompilerContract.MAX_INPUT_BYTES + 1L,
                )
            }
        val validation = RandomAccessFile(fixture.input, "r").use { input ->
            ZipArchiveV1Validator.validateImmutable(input, validationLimits)
        }
        assertEquals(fixture.containerBytes, validation.containerBytes)
        assertEquals(fixture.uncompressedBytes, validation.uncompressedBytes)
        assertEquals(
            fixture.compressedPayloadBytes,
            validation.compressedBytes,
        )
        assertEquals(
            fixture.containerBytes - fixture.compressedPayloadBytes,
            validation.metadataBytes,
        )
        assertEquals(
            listOf(R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH) +
                fixture.sourceDescriptors.map { descriptor -> descriptor.path },
            validation.entries.map { entry -> entry.path },
        )
        assertEquals(manifestBytes, validation.entries.first().uncompressedBytes)
        assertEquals(
            fixture.boundary.compression.method,
            validation.entries.first().compressionMethod,
        )
        validation.entries.drop(1)
            .zip(fixture.sourceDescriptors)
            .forEach { (entry, descriptor) ->
                assertEquals(fixture.boundary.compression.method, entry.compressionMethod)
                assertEquals(descriptor.size, entry.uncompressedBytes)
                assertEquals(descriptor.compressedSize, entry.compressedBytes)
                assertEquals(descriptor.crc32, entry.crc32)
            }
        if (fixture.boundary.quotaDeltaBytes > 0L) {
            val productionFailure = try {
                RandomAccessFile(fixture.input, "r").use { input ->
                    ZipArchiveV1Validator.validateImmutable(
                        input,
                        ZipArchiveV1Limits.INPUT,
                    )
                }
                null
            } catch (failure: ZipArchiveV1Exception) {
                failure
            }
            assertNotNull(productionFailure)
            assertEquals(
                ZipArchiveV1FailureKind.QUOTA,
                requireNotNull(productionFailure).kind,
            )
        }
        when (fixture.boundary.compression) {
            R2CapacityZipCompression.STORED -> assertEquals(
                TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES +
                    fixture.boundary.quotaDeltaBytes,
                fixture.containerBytes,
            )
            R2CapacityZipCompression.DEFLATED -> {
                assertEquals(
                    TypeScriptCompilerContract.MAX_INPUT_BYTES +
                        fixture.boundary.quotaDeltaBytes,
                    fixture.uncompressedBytes,
                )
                assertTrue(
                    fixture.containerBytes <=
                        TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES,
                )
            }
        }
    }

    private fun assertCapacityOutput(
        fixture: R2CapacityArchiveFixture,
        output: File,
        terminal: Bundle,
    ) {
        assertTrue(output.length() > 0L)
        ZipArchiveV1Validator.validate(output, ZipArchiveV1Limits.OUTPUT)
        ZipFile(output).use { archive ->
            assertEquals(
                setOf(
                    R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                    R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                    "modules/main.js",
                    "maps/main.js.map",
                ),
                archive.entries().asSequence().map { entry -> entry.name }.toSet(),
            )
            val manifest = archive.getInputStream(
                archive.getEntry(R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH),
            ).use { input -> input.readBytes() }
            assertEquals(
                terminal.getString(
                    TypeScriptCompilerContract.KEY_OUTPUT_MANIFEST_SHA256,
                ),
                R2Digests.sha256(manifest),
            )
            val authority = R2StrictJson.parseObject(manifest)
            assertEquals(fixture.requestId, authority["requestId"])
            assertEquals(fixture.projectId, authority["projectId"])
            assertEquals(
                fixture.inputArchiveSha256,
                authority["inputArchiveSha256"],
            )
            assertEquals(
                fixture.requestManifestSha256,
                authority["requestManifestSha256"],
            )
        }
    }

    private fun logCapacityEvidence(
        disposition: String,
        fixture: R2CapacityArchiveFixture,
        elapsedMs: Long,
    ) {
        Log.i(
            CAPACITY_LOG_TAG,
            "$disposition api=${Build.VERSION.SDK_INT} " +
                "webView=" +
                "${androidx.webkit.WebViewCompat.getCurrentWebViewPackage(context)?.versionName} " +
                "boundary=${fixture.boundary.name} " +
                "containerBytes=${fixture.containerBytes} " +
                "uncompressedBytes=${fixture.uncompressedBytes} " +
                "compressedPayloadBytes=${fixture.compressedPayloadBytes} " +
                "elapsedMs=$elapsedMs",
        )
    }

    private fun createFixture(
        requestId: String,
        sources: LinkedHashMap<String, String>,
        rootPaths: List<String>,
        profileAuthority: R2CompilationProfileAuthority =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        workerSpinMilliseconds: Int = 0,
        workerSpinPhase: R2ProjectExecutionPhase? =
            R2ProjectExecutionPhase.PARSE.takeIf {
                workerSpinMilliseconds > 0
            },
        timeoutMs: Long = DEFAULT_TIMEOUT_MILLIS,
        terminalCleanupDelayMilliseconds: Int = 0,
    ): CompilationFixture {
        val metadata = CompilerDistributionMetadata.load(context)
        val files = sources.map { (path, text) ->
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            R2ProjectArchiveFile(
                path = path,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        }
        val inventoryFingerprint =
            R2ProvisionalRequestManifest.inventoryFingerprint(files)
        val fileJson = files.joinToString(separator = ",") { file ->
            "{\"path\":\"${file.path}\",\"sha256\":\"${file.sha256}\"," +
                "\"size\":${file.size}}"
        }
        val rootJson = rootPaths.joinToString(separator = ",") { path -> "\"$path\"" }
        val declarationProfileFingerprint = R2TrustedDeclarations.loadForProfile(
            context,
            profileAuthority.profile,
        ).profileFingerprint
        val manifest = (
            "{\"schema\":\"${R2InputArchiveSchemaV1.SCHEMA}\"," +
                "\"revision\":${R2InputArchiveSchemaV1.REVISION}," +
                "\"inputQuotaRevision\":" +
                "${TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION}," +
                "\"contractVersion\":${TypeScriptCompilerContract.VERSION}," +
                "\"contractHash\":\"${TypeScriptCompilerContract.HASH}\"," +
                "\"inputArchiveTransportVersion\":" +
                "${TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION}," +
                "\"requestId\":\"$requestId\",\"projectId\":\"binder-project\"," +
                "\"entrySourceUri\":\"autojs://source/project/main.ts\"," +
                "\"entryPath\":\"sources/main.ts\",\"targetProfile\":\"" +
                "${profileAuthority.profile.contractValue}\"," +
                "\"compilationMode\":\"development\"," +
                "\"profileSchemaRevision\":" +
                "${profileAuthority.profileSchemaRevision}," +
                "\"profileRevision\":${profileAuthority.profileRevision}," +
                "\"runtimeCapabilitySchemaRevision\":" +
                "${TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION}," +
                "\"configSchemaRevision\":" +
                "${profileAuthority.configSchemaRevision}," +
                "\"runtimeCapabilitiesFingerprint\":\"" +
                "${profileAuthority.runtimeCapabilitiesFingerprint}\"," +
                "\"compilerPackage\":\"typescript\"," +
                "\"compilerVersion\":\"${metadata.version}\"," +
                "\"compilerDistributionFingerprint\":\"" +
                "${metadata.distributionFingerprint}\"," +
                "\"declarationsFingerprint\":\"${metadata.declarationsFingerprint}\"," +
                "\"declarationProfileFingerprint\":\"$declarationProfileFingerprint\"," +
                "\"configKind\":\"${profileAuthority.configKind}\"," +
                "\"callerInventoryFingerprint\":\"$inventoryFingerprint\"," +
                "\"rootPaths\":[$rootJson],\"files\":[$fileJson]}"
            ).toByteArray(StandardCharsets.UTF_8)
        val input = File.createTempFile("r2-binder-input-", ".archive", context.cacheDir)
        val entries = linkedMapOf(
            R2InputArchiveSchemaV1.REQUEST_MANIFEST_PATH to manifest,
        )
        sources.forEach { (path, text) ->
            entries[path] = text.toByteArray(StandardCharsets.UTF_8)
        }
        writeStoredZip(input, entries)
        val output = File.createTempFile("r2-binder-output-", ".archive", context.cacheDir)
            .apply { writeText(OUTPUT_SENTINEL) }
        return CompilationFixture(
            requestId = requestId,
            input = input,
            output = output,
            inputArchiveSha256 = R2Digests.sha256(input.readBytes()),
            requestManifestSha256 = R2Digests.sha256(manifest),
            compilerDistributionFingerprint = metadata.distributionFingerprint,
            declarationsFingerprint = metadata.declarationsFingerprint,
            declarationProfileFingerprint = declarationProfileFingerprint,
            profileAuthority = profileAuthority,
            workerSpinMilliseconds = workerSpinMilliseconds,
            workerSpinPhase = workerSpinPhase,
            timeoutMs = timeoutMs,
            terminalCleanupDelayMilliseconds =
                terminalCleanupDelayMilliseconds,
        )
    }

    private fun compileAndAcknowledge(
        compiler: ITypeScriptCompilerPlugin,
        fixture: CompilationFixture,
        request: Bundle = fixture.request(),
        publicShapedAcknowledgement: Boolean = false,
    ): Bundle {
        val callback = RecordingCallback()
        val ticket = startCompilation(
            compiler = compiler,
            fixture = fixture,
            callback = callback,
            request = request,
        )
        callback.markStartReturned()
        val token = assertAccepted(ticket, fixture.requestId)
        assertTrue(
            compiler.cancelCompilation(
                if (publicShapedAcknowledgement) {
                    publicAcknowledgement(fixture.requestId, token)
                } else {
                    acknowledgement(fixture.requestId, token)
                },
            ),
        )
        val terminal = callback.awaitTerminal()
        assertFalse(callback.callbackBeforeStartReturned)
        assertEquals(1, callback.terminalCount.get())
        SystemClock.sleep(EXACTLY_ONCE_OBSERVATION_MILLIS)
        assertEquals(1, callback.terminalCount.get())
        return terminal
    }

    private fun assertEmittedTerminal(terminal: Bundle) {
        assertEquals(
            TypeScriptCompilerContract.OUTCOME_EMITTED,
            terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
        )
        assertTrue(
            terminal.getBoolean(
                TypeScriptCompilerContract.KEY_SUCCEEDED,
                false,
            ),
        )
        assertEquals(
            0,
            terminal.getInt(
                TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT,
                -1,
            ),
        )
        assertTrue(
            terminal.getInt(
                TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT,
                0,
            ) > 0,
        )
    }

    private fun assertX2DiagnosticsOnlyTerminalAndArchive(
        fixture: CompilationFixture,
        terminal: Bundle,
        sourceCase: X2SourceDiagnosticCase,
    ) {
        assertEquals(
            TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
            terminal.getString(TypeScriptCompilerContract.KEY_OUTCOME),
        )
        assertFalse(
            terminal.getBoolean(TypeScriptCompilerContract.KEY_SUCCEEDED, true),
        )
        assertEquals(
            1,
            terminal.getInt(TypeScriptCompilerContract.KEY_DIAGNOSTIC_COUNT, -1),
        )
        assertEquals(
            0,
            terminal.getInt(TypeScriptCompilerContract.KEY_EMITTED_MODULE_COUNT, -1),
        )

        ZipArchiveV1Validator.validate(fixture.output, ZipArchiveV1Limits.OUTPUT)
        val entries = ZipFile(fixture.output).use { archive ->
            linkedMapOf<String, ByteArray>().apply {
                archive.entries().asSequence().forEach { entry ->
                    put(
                        entry.name,
                        archive.getInputStream(entry).use { input -> input.readBytes() },
                    )
                }
            }
        }
        try {
            assertEquals(
                listOf(
                    R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                    R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                ),
                entries.keys.toList(),
            )
            assertFalse(entries.keys.any { path -> path.startsWith("modules/") })
            assertFalse(entries.keys.any { path -> path.startsWith("maps/") })

            val manifestBytes = requireNotNull(
                entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH],
            )
            assertEquals(
                terminal.getString(TypeScriptCompilerContract.KEY_OUTPUT_MANIFEST_SHA256),
                R2Digests.sha256(manifestBytes),
            )
            val manifest = R2StrictJson.parseObject(manifestBytes)
            assertEquals(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA, manifest["schema"])
            assertEquals(
                TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION.toLong(),
                (manifest["revision"] as R2StrictJson.NumberToken).source.toLong(),
            )
            assertEquals(
                TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
                terminal.getInt(TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION, -1),
            )
            assertEquals(
                TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION.toLong(),
                (manifest["outputQuotaRevision"] as R2StrictJson.NumberToken).source.toLong(),
            )
            assertEquals(fixture.declarationProfileFingerprint, manifest["declarationProfileFingerprint"])
            assertEquals(fixture.requestId, manifest["requestId"])
            assertEquals("binder-project", manifest["projectId"])
            assertEquals(
                fixture.profileAuthority.profile.contractValue,
                manifest["targetProfile"],
            )
            assertEquals(
                fixture.profileAuthority.profileRevision.toLong(),
                (manifest["profileRevision"] as R2StrictJson.NumberToken)
                    .source.toLong(),
            )
            assertEquals(fixture.inputArchiveSha256, manifest["inputArchiveSha256"])
            assertEquals(fixture.requestManifestSha256, manifest["requestManifestSha256"])
            assertEquals(
                fixture.profileAuthority.runtimeCapabilitiesFingerprint,
                manifest["runtimeCapabilitiesFingerprint"],
            )
            assertEquals(
                fixture.profileAuthority.effectiveOptionsFingerprint,
                manifest["effectiveOptionsFingerprint"],
            )
            assertEquals(TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS, manifest["outcome"])
            assertEquals(true, manifest["emitSkipped"])
            assertEquals(null, manifest["entryModulePath"])
            assertEquals(
                1L,
                (manifest["diagnosticCount"] as R2StrictJson.NumberToken).source.toLong(),
            )
            assertEquals(
                0L,
                (manifest["emittedModuleCount"] as R2StrictJson.NumberToken).source.toLong(),
            )

            val diagnosticsBytes = requireNotNull(
                entries[R2PrivateOutputArchive.DIAGNOSTICS_PATH],
            )
            val outputFiles = (manifest["files"] as List<*>).map { rawFile ->
                val file = rawFile as Map<*, *>
                R2OutputArchiveFile(
                    path = file["path"] as String,
                    kind = file["kind"] as String,
                    size = (file["size"] as R2StrictJson.NumberToken).source.toLong(),
                    sha256 = file["sha256"] as String,
                    sourcePaths = (file["sourcePaths"] as List<*>).map { path ->
                        path as String
                    },
                )
            }
            val diagnosticsFile = outputFiles.single()
            assertEquals(R2PrivateOutputArchive.DIAGNOSTICS_PATH, diagnosticsFile.path)
            assertEquals("diagnostics", diagnosticsFile.kind)
            assertEquals(listOf(X2_SOURCE_PATH), diagnosticsFile.sourcePaths)
            assertEquals(diagnosticsBytes.size.toLong(), diagnosticsFile.size)
            assertEquals(R2Digests.sha256(diagnosticsBytes), diagnosticsFile.sha256)
            assertEquals(
                manifest["artifactInventoryFingerprint"],
                R2OutputArchiveSchemaV1.artifactInventoryFingerprint(outputFiles),
            )
            assertEquals(
                manifest["moduleInventoryFingerprint"],
                R2OutputArchiveSchemaV1.moduleInventoryFingerprint(outputFiles),
            )

            val diagnosticsEnvelope = R2StrictJson.parseObject(diagnosticsBytes)
            assertEquals(X2_DIAGNOSTICS_ENVELOPE_KEYS, diagnosticsEnvelope.keys)
            assertEquals(fixture.requestId, diagnosticsEnvelope["requestId"])
            assertEquals(
                fixture.profileAuthority.profile.contractValue,
                diagnosticsEnvelope["targetProfile"],
            )
            assertEquals(
                fixture.profileAuthority.profileRevision.toLong(),
                (diagnosticsEnvelope["profileRevision"] as R2StrictJson.NumberToken)
                    .source.toLong(),
            )
            assertEquals(
                fixture.profileAuthority.profileSchemaRevision.toLong(),
                (diagnosticsEnvelope["profileSchemaRevision"] as R2StrictJson.NumberToken)
                    .source.toLong(),
            )
            assertEquals(
                fixture.profileAuthority.configSchemaRevision.toLong(),
                (diagnosticsEnvelope["configSchemaRevision"] as R2StrictJson.NumberToken)
                    .source.toLong(),
            )
            assertEquals(
                fixture.profileAuthority.configKind,
                diagnosticsEnvelope["configKind"],
            )
            val diagnostic =
                (diagnosticsEnvelope["diagnostics"] as List<*>).single() as Map<*, *>
            assertEquals(X2_SOURCE_DIAGNOSTIC_KEYS, diagnostic.keys)
            assertEquals(sourceCase.expectedPhase.protocolValue, diagnostic["phase"])
            assertEquals(
                1L,
                (diagnostic["category"] as R2StrictJson.NumberToken).source.toLong(),
            )
            assertEquals(
                sourceCase.expectedCode.toLong(),
                (diagnostic["code"] as R2StrictJson.NumberToken).source.toLong(),
            )
            assertEquals(sourceCase.expectedMessage, diagnostic["message"])
            assertEquals(X2_SOURCE_PATH, diagnostic["sourcePath"])
            val start =
                (diagnostic["start"] as R2StrictJson.NumberToken).source.toInt()
            val length =
                (diagnostic["length"] as R2StrictJson.NumberToken).source.toInt()
            val line =
                (diagnostic["line"] as R2StrictJson.NumberToken).source.toInt()
            val column =
                (diagnostic["column"] as R2StrictJson.NumberToken).source.toInt()
            assertEquals(sourceCase.expectedStart, start)
            assertEquals(sourceCase.expectedLength, length)
            assertEquals(sourceCase.expectedLine, line)
            assertEquals(sourceCase.expectedColumn, column)
            assertTrue(start + length <= sourceCase.source.length)
        } finally {
            entries.values.forEach { bytes -> bytes.fill(0) }
        }
    }

    private fun readStableServiceBundle(
        fixture: CompilationFixture,
        terminal: Bundle,
    ): StableServiceBundle {
        ZipArchiveV1Validator.validate(fixture.output, ZipArchiveV1Limits.OUTPUT)
        val entries = ZipFile(fixture.output).use { archive ->
            linkedMapOf<String, ByteArray>().apply {
                archive.entries().asSequence().forEach { entry ->
                    put(
                        entry.name,
                        archive.getInputStream(entry).use { input -> input.readBytes() },
                    )
                }
            }
        }
        assertEquals(
            listOf(
                R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH,
                R2PrivateOutputArchive.DIAGNOSTICS_PATH,
                "maps/helper.js.map",
                "maps/main.js.map",
                "modules/helper.js",
                "modules/main.js",
            ),
            entries.keys.toList(),
        )

        val manifestBytes = requireNotNull(
            entries[R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH],
        )
        assertEquals(
            terminal.getString(TypeScriptCompilerContract.KEY_OUTPUT_MANIFEST_SHA256),
            R2Digests.sha256(manifestBytes),
        )
        val manifest = R2StrictJson.parseObject(manifestBytes)
        assertEquals(TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA, manifest["schema"])
        assertEquals(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION.toLong(),
            (manifest["revision"] as R2StrictJson.NumberToken).source.toLong(),
        )
        assertEquals(
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
            terminal.getInt(TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION, -1),
        )
        assertEquals(
            TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION.toLong(),
            (manifest["outputQuotaRevision"] as R2StrictJson.NumberToken).source.toLong(),
        )
        assertEquals(fixture.declarationProfileFingerprint, manifest["declarationProfileFingerprint"])
        assertEquals(fixture.requestId, manifest["requestId"])
        assertEquals("binder-project", manifest["projectId"])
        assertEquals(
            fixture.profileAuthority.profile.contractValue,
            manifest["targetProfile"],
        )
        assertEquals(
            fixture.profileAuthority.profileRevision.toLong(),
            (manifest["profileRevision"] as R2StrictJson.NumberToken).source.toLong(),
        )
        assertEquals(
            fixture.profileAuthority.runtimeCapabilitiesFingerprint,
            manifest["runtimeCapabilitiesFingerprint"],
        )
        assertEquals(fixture.inputArchiveSha256, manifest["inputArchiveSha256"])
        assertEquals(fixture.requestManifestSha256, manifest["requestManifestSha256"])
        assertEquals(
            fixture.profileAuthority.effectiveOptionsFingerprint,
            manifest["effectiveOptionsFingerprint"],
        )
        assertEquals("modules/main.js", manifest["entryModulePath"])
        assertEquals(
            2L,
            (manifest["emittedModuleCount"] as R2StrictJson.NumberToken).source.toLong(),
        )

        val outputFiles = (manifest["files"] as List<*>).map { rawFile ->
            val file = rawFile as Map<*, *>
            R2OutputArchiveFile(
                path = file["path"] as String,
                kind = file["kind"] as String,
                size = (file["size"] as R2StrictJson.NumberToken).source.toLong(),
                sha256 = file["sha256"] as String,
                sourcePaths = (file["sourcePaths"] as List<*>).map { path ->
                    path as String
                },
            )
        }
        outputFiles.forEach { file ->
            val bytes = requireNotNull(entries[file.path])
            assertEquals(file.size, bytes.size.toLong())
            assertEquals(file.sha256, R2Digests.sha256(bytes))
        }
        assertEquals(
            entries.keys.filter { path ->
                path != R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH
            },
            outputFiles.map(R2OutputArchiveFile::path),
        )
        assertEquals(
            manifest["artifactInventoryFingerprint"],
            R2OutputArchiveSchemaV1.artifactInventoryFingerprint(outputFiles),
        )
        assertEquals(
            manifest["moduleInventoryFingerprint"],
            R2OutputArchiveSchemaV1.moduleInventoryFingerprint(outputFiles),
        )

        val diagnosticsBytes = requireNotNull(
            entries[R2PrivateOutputArchive.DIAGNOSTICS_PATH],
        )
        val diagnostics = R2StrictJson.parseObject(diagnosticsBytes)
        assertEquals(fixture.requestId, diagnostics["requestId"])
        assertEquals(
            fixture.profileAuthority.profile.contractValue,
            diagnostics["targetProfile"],
        )
        assertTrue((diagnostics["diagnostics"] as List<*>).isEmpty())
        val normalizedRequestId = "x".repeat(fixture.requestId.length)
        val diagnosticsText = R2Utf8.decode(diagnosticsBytes, "X1 diagnostics")
        assertEquals(1, diagnosticsText.windowed(fixture.requestId.length).count {
            candidate -> candidate == fixture.requestId
        })
        val normalizedDiagnosticsBytes = R2Utf8.encode(
            diagnosticsText.replace(fixture.requestId, normalizedRequestId),
            "X1 normalized diagnostics",
        )
        val normalizedDiagnosticsSha256 = R2Digests.sha256(normalizedDiagnosticsBytes)
        val normalizedOutputFiles = outputFiles.map { file ->
            if (file.path == R2PrivateOutputArchive.DIAGNOSTICS_PATH) {
                file.copy(
                    size = normalizedDiagnosticsBytes.size.toLong(),
                    sha256 = normalizedDiagnosticsSha256,
                )
            } else {
                file
            }
        }
        val normalizedManifest = LinkedHashMap(manifest).apply {
            this["requestId"] = normalizedRequestId
            this["inputArchiveSha256"] = "0".repeat(64)
            this["requestManifestSha256"] = "0".repeat(64)
            this["artifactInventoryFingerprint"] =
                R2OutputArchiveSchemaV1.artifactInventoryFingerprint(normalizedOutputFiles)
            this["moduleInventoryFingerprint"] =
                R2OutputArchiveSchemaV1.moduleInventoryFingerprint(normalizedOutputFiles)
            this["files"] = normalizedOutputFiles.map { file ->
                linkedMapOf(
                    "path" to file.path,
                    "kind" to file.kind,
                    "size" to file.size,
                    "sha256" to file.sha256,
                    "sourcePaths" to file.sourcePaths,
                )
            }
        }
        val normalizedDiagnostics = LinkedHashMap(diagnostics).apply {
            this["requestId"] = normalizedRequestId
        }
        val payloads = entries
            .filterKeys { path ->
                path != R2PrivateOutputArchive.OUTPUT_MANIFEST_PATH &&
                    path != R2PrivateOutputArchive.DIAGNOSTICS_PATH
            }
            .mapValues { (_, bytes) -> bytes.copyOf() }
        normalizedDiagnosticsBytes.fill(0)
        entries.values.forEach { bytes -> bytes.fill(0) }
        return StableServiceBundle(
            entryNames = entries.keys.toList(),
            normalizedManifest = normalizedManifest,
            normalizedDiagnostics = normalizedDiagnostics,
            payloads = payloads,
        )
    }

    private fun assertStableServiceBundlesEqual(
        profileAuthority: R2CompilationProfileAuthority,
        first: StableServiceBundle,
        second: StableServiceBundle,
    ) {
        assertEquals(first.entryNames, second.entryNames)
        assertEquals(first.normalizedManifest, second.normalizedManifest)
        assertEquals(first.normalizedDiagnostics, second.normalizedDiagnostics)
        assertEquals(first.payloads.keys, second.payloads.keys)
        first.payloads.forEach { (path, firstBytes) ->
            val secondBytes = requireNotNull(second.payloads[path])
            assertTrue(
                "${profileAuthority.profile.contractValue} payload drifted at $path.",
                firstBytes.contentEquals(secondBytes),
            )
            assertEquals(R2Digests.sha256(firstBytes), R2Digests.sha256(secondBytes))
        }
        first.payloads.values.forEach { bytes -> bytes.fill(0) }
        second.payloads.values.forEach { bytes -> bytes.fill(0) }
    }

    private fun assertNoKnownR2TransientNames(label: String) {
        val inputDirectory = context.getDir(
            R2_INPUT_STAGING_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val outputDirectory = context.getDir(
            R2_OUTPUT_STAGING_DIRECTORY,
            Context.MODE_PRIVATE,
        )
        val inputChildren = inputDirectory.listFiles()
            ?: throw AssertionError(
                "$label could not enumerate ${inputDirectory.absolutePath}.",
            )
        val outputChildren = outputDirectory.listFiles()
            ?: throw AssertionError(
                "$label could not enumerate ${outputDirectory.absolutePath}.",
            )
        val known = (
            inputChildren.toList() +
                outputChildren.toList()
            ).filter { file ->
            (
                file.name.startsWith(R2_PRIVATE_INPUT_PREFIX) &&
                    file.name.endsWith(R2_PRIVATE_INPUT_SUFFIX)
                ) || (
                file.name.startsWith(R2_PROJECT_SPOOL_PREFIX) &&
                    file.name.endsWith(R2_PROJECT_SPOOL_SUFFIX)
                ) || (
                file.name.startsWith(R2_PRIVATE_OUTPUT_PREFIX) &&
                    file.name.endsWith(R2_PRIVATE_OUTPUT_SUFFIX)
                )
        }
        assertTrue(
            "$label retained named R2 transient files: " +
                known.map(File::getAbsolutePath),
            known.isEmpty(),
        )
    }

    private fun assertNoUnexpectedFileDescriptorGrowth(
        baseline: RemoteFileDescriptorSnapshot,
        current: RemoteFileDescriptorSnapshot,
        expectedProcessPid: Int,
    ) {
        val unmatchedBaselineTargets = baseline.targets.toMutableList()
        val addedTargets = buildList {
            current.targets.forEach { target ->
                val baselineIndex = unmatchedBaselineTargets.indexOf(target)
                if (baselineIndex >= 0) {
                    unmatchedBaselineTargets.removeAt(baselineIndex)
                } else {
                    add(target)
                }
            }
        }
        if (addedTargets.isEmpty()) return

        assertTrue(
            "Compiler-service FDs replaced warm-baseline targets: " +
                "removed=$unmatchedBaselineTargets added=$addedTargets",
            unmatchedBaselineTargets.isEmpty(),
        )
        assertEquals(
            "The only permitted first-cycle FD growth is one exact lazy-initialization pair.",
            LAZY_INITIALIZATION_FD_COUNT,
            current.count - baseline.count,
        )
        val procMatches = addedTargets.map(CHROMIUM_PROCESS_PROC_TARGET::matchEntire)
        val matchedProcTargets = procMatches.filterNotNull()
        val exactProcPair =
            matchedProcTargets.size == LAZY_INITIALIZATION_FD_COUNT &&
                matchedProcTargets
                    .map { match -> match.groupValues[1] }
                    .toSet() == setOf(expectedProcessPid.toString()) &&
                matchedProcTargets
                    .map { match -> match.groupValues[2] }
                    .toSet() == CHROMIUM_PROCESS_PROC_FILES
        val exactEventLoopPair =
            addedTargets.size == COMPILER_EVENT_LOOP_FD_TARGETS.size &&
                addedTargets.toSet() == COMPILER_EVENT_LOOP_FD_TARGETS
        assertTrue(
            "Unexpected compiler-service FDs beyond the warm baseline: $addedTargets",
            exactProcPair || exactEventLoopPair,
        )
    }

    private fun deleteTestFile(
        file: File,
        label: String,
    ) {
        assertTrue(
            "$label could not be deleted: ${file.absolutePath}",
            !file.exists() || file.delete(),
        )
        assertFalse(
            "$label remained after deletion: ${file.absolutePath}",
            file.exists(),
        )
    }

    private fun awaitCompilerProcessPid(expectedProcessName: String): Int {
        repeat(PROCESS_LOOKUP_ATTEMPTS) {
            val matches = activityManager.runningAppProcesses
                .orEmpty()
                .filter { process ->
                    process.processName == expectedProcessName &&
                        process.uid == Process.myUid()
                }
            if (matches.size == 1) {
                return matches.single().pid
            }
            SystemClock.sleep(PROCESS_LOOKUP_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "Could not find exactly one live compiler process $expectedProcessName.",
        )
    }

    private fun startCompilation(
        compiler: ITypeScriptCompilerPlugin,
        fixture: CompilationFixture,
        callback: RecordingCallback,
        request: Bundle = fixture.request(),
    ): Bundle {
        val input = ParcelFileDescriptor.open(
            fixture.input,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val output = ParcelFileDescriptor.open(
            fixture.output,
            ParcelFileDescriptor.MODE_READ_WRITE,
        )
        return try {
            compiler.startCompilation(
                request,
                input,
                output,
                callback,
            )
        } finally {
            input.close()
            output.close()
        }
    }

    private fun startCapacityCompilation(
        compiler: ITypeScriptCompilerPlugin,
        fixture: R2CapacityArchiveFixture,
        output: File,
        callback: RecordingCallback,
        timeoutMs: Long = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
    ): Bundle {
        val inputDescriptor = ParcelFileDescriptor.open(
            fixture.input,
            ParcelFileDescriptor.MODE_READ_ONLY,
        )
        val outputDescriptor = ParcelFileDescriptor.open(
            output,
            ParcelFileDescriptor.MODE_READ_WRITE,
        )
        return try {
            compiler.startCompilation(
                fixture.capacityRequest(timeoutMs),
                inputDescriptor,
                outputDescriptor,
                callback,
            )
        } finally {
            inputDescriptor.close()
            outputDescriptor.close()
        }
    }

    private fun R2CapacityArchiveFixture.capacityRequest(
        timeoutMs: Long = TypeScriptCompilerContract.MAX_TIMEOUT_MS,
    ) = Bundle().apply {
        putInt(
            TypeScriptCompilerContract.KEY_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptCompilerContract.KEY_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putInt(
            TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_PROJECT_ID, projectId)
        putString(
            TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
            entrySourceUri,
        )
        putString(
            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            TypeScriptCompilerContract.PROFILE_RHINO,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILATION_MODE,
            TypeScriptCompilerContract.MODE_DEVELOPMENT,
        )
        putString(TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON, "{}")
        putLong(
            TypeScriptCompilerContract.KEY_TIMEOUT_MS,
            timeoutMs,
        )
        putInt(
            TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
            TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION,
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
            compilerDistributionFingerprint,
        )
        putString(
            TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
            declarationsFingerprint,
        )
        putString(
            TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            declarationProfileFingerprint,
        )
        putString(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
            R2RuntimeCapabilityMetadata.fingerprint,
        )
        putBoolean(R2CompilationProbeProtocol.KEY_ENABLED, true)
        putBoolean(
            R2CompilationProbeProtocol.KEY_EXPANDED_WORKER_CAPACITY,
            true,
        )
        putString(
            R2CompilationProbeProtocol.KEY_INPUT_ARCHIVE_SHA256,
            inputArchiveSha256,
        )
        putString(
            R2CompilationProbeProtocol.KEY_REQUEST_MANIFEST_SHA256,
            requestManifestSha256,
        )
    }

    private fun createCapacityOutput(): File =
        File.createTempFile(
            "r2-binder-capacity-output-",
            ".archive",
            context.cacheDir,
        ).apply {
            writeText(OUTPUT_SENTINEL)
        }

    private fun CompilationFixture.request() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putInt(
            TypeScriptCompilerContract.KEY_INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_PROJECT_ID, "binder-project")
        putString(
            TypeScriptCompilerContract.KEY_ENTRY_SOURCE_URI,
            "autojs://source/project/main.ts",
        )
        putString(
            TypeScriptCompilerContract.KEY_TARGET_PROFILE,
            profileAuthority.profile.contractValue,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILATION_MODE,
            TypeScriptCompilerContract.MODE_DEVELOPMENT,
        )
        putString(TypeScriptCompilerContract.KEY_COMPILER_OPTIONS_JSON, "{}")
        putLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, timeoutMs)
        putInt(
            TypeScriptCompilerContract.KEY_PROFILE_SCHEMA_REVISION,
            TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITY_SCHEMA_REVISION,
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        )
        putString(
            TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
            compilerDistributionFingerprint,
        )
        putString(
            TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT,
            declarationsFingerprint,
        )
        putString(
            TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
            declarationProfileFingerprint,
        )
        putString(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
            profileAuthority.runtimeCapabilitiesFingerprint,
        )
        putBoolean(R2CompilationProbeProtocol.KEY_ENABLED, true)
        putString(
            R2CompilationProbeProtocol.KEY_INPUT_ARCHIVE_SHA256,
            inputArchiveSha256,
        )
        putString(
            R2CompilationProbeProtocol.KEY_REQUEST_MANIFEST_SHA256,
            requestManifestSha256,
        )
        putInt(
            R2CompilationProbeProtocol.KEY_PROFILE_REVISION,
            profileAuthority.profileRevision,
        )
        putInt(
            R2CompilationProbeProtocol.KEY_WORKER_SPIN_MILLISECONDS,
            workerSpinMilliseconds,
        )
        putString(
            R2CompilationProbeProtocol.KEY_WORKER_SPIN_PHASE,
            workerSpinPhase?.protocolValue ?: "none",
        )
        putInt(
            R2CompilationProbeProtocol.KEY_TERMINAL_CLEANUP_DELAY_MILLISECONDS,
            terminalCleanupDelayMilliseconds,
        )
    }

    private fun CompilationFixture.publicRequest() = request().apply {
        check(profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO) {
            "The external public preview remains Rhino-only before M5 promotion."
        }
        remove(R2CompilationProbeProtocol.KEY_ENABLED)
        remove(R2CompilationProbeProtocol.KEY_INPUT_ARCHIVE_SHA256)
        remove(R2CompilationProbeProtocol.KEY_REQUEST_MANIFEST_SHA256)
        remove(R2CompilationProbeProtocol.KEY_PROFILE_REVISION)
        remove(R2CompilationProbeProtocol.KEY_WORKER_SPIN_MILLISECONDS)
        remove(R2CompilationProbeProtocol.KEY_WORKER_SPIN_PHASE)
        remove(
            R2CompilationProbeProtocol
                .KEY_TERMINAL_CLEANUP_DELAY_MILLISECONDS,
        )
        putInt(
            TypeScriptR2Contract.KEY_PROFILE_REVISION,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        )
        putInt(
            TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
            R2CompilationProfileRegistry.CONFIG_SCHEMA_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_KIND,
            R2CompilationProfileRegistry.CONFIG_KIND_NONE,
        )
        putString(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SHA256,
            inputArchiveSha256,
        )
        putString(
            TypeScriptR2Contract.KEY_REQUEST_MANIFEST_SHA256,
            requestManifestSha256,
        )
    }

    private fun CompilationFixture.x1PublicShapedRequest() = request().apply {
        remove(R2CompilationProbeProtocol.KEY_WORKER_SPIN_MILLISECONDS)
        remove(R2CompilationProbeProtocol.KEY_WORKER_SPIN_PHASE)
        remove(R2CompilationProbeProtocol.KEY_EXPANDED_WORKER_CAPACITY)
        remove(R2CompilationProbeProtocol.KEY_TERMINAL_CLEANUP_DELAY_MILLISECONDS)
    }

    private fun validPrewarm() = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putLong(TypeScriptCompilerContract.KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MILLIS)
    }

    private fun cancellation(
        requestId: String,
        jobToken: String,
    ) = Bundle().apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_REQUEST_ID, requestId)
        putString(TypeScriptCompilerContract.KEY_JOB_TOKEN, jobToken)
    }

    private fun acknowledgement(
        requestId: String,
        jobToken: String,
    ) = cancellation(requestId, jobToken).apply {
        putString(
            CompilerTransportProbeProtocol.KEY_CANCELLATION_COMMAND,
            CompilerTransportProbeProtocol.COMMAND_ACKNOWLEDGE,
        )
    }

    private fun publicAcknowledgement(
        requestId: String,
        jobToken: String,
    ) = cancellation(requestId, jobToken).apply {
        putString(
            TypeScriptR2Contract.KEY_CANCELLATION_OPERATION,
            TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE,
        )
    }

    private fun startPublicCompilationWhenSupervisorReady(
        compiler: ITypeScriptCompilerPlugin,
        fixture: CompilationFixture,
        callback: RecordingCallback,
    ): Bundle {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(BIND_TIMEOUT_SECONDS)
        do {
            val ticket = startCompilation(
                compiler = compiler,
                fixture = fixture,
                callback = callback,
                request = fixture.publicRequest(),
            )
            if (
                ticket.getBoolean(
                    TypeScriptCompilerContract.KEY_ACCEPTED,
                    false,
                )
            ) {
                return ticket
            }
            assertEquals(
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
                ticket.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
            )
            SystemClock.sleep(25L)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(
            "R2 public preview admission did not observe a ready supervisor.",
        )
    }

    private fun assertAccepted(
        ticket: Bundle,
        requestId: String,
    ): String {
        assertEquals(requestId, ticket.getString(TypeScriptCompilerContract.KEY_REQUEST_ID))
        assertTrue(
            ticket.getString(TypeScriptCompilerContract.KEY_ERROR_MESSAGE),
            ticket.getBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, false),
        )
        assertTrue(
            ticket.getBoolean(
                TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_REQUIRED,
                false,
            ),
        )
        assertTrue(
            ticket.getLong(
                TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_TIMEOUT_MS,
                -1L,
            ) in 1L..TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS,
        )
        return requireNotNull(ticket.getString(TypeScriptCompilerContract.KEY_JOB_TOKEN))
            .also { token ->
                assertTrue(token.length <= TypeScriptCompilerContract.MAX_JOB_TOKEN_CHARS)
            }
    }

    private fun assertRejectedInvalid(
        ticket: Bundle,
        requestId: String,
    ) {
        assertEquals(requestId, ticket.getString(TypeScriptCompilerContract.KEY_REQUEST_ID))
        assertFalse(ticket.getBoolean(TypeScriptCompilerContract.KEY_ACCEPTED, true))
        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            ticket.getString(TypeScriptCompilerContract.KEY_ERROR_CODE),
        )
    }

    private fun awaitActiveJobCount(
        compiler: ITypeScriptCompilerPlugin,
        expected: Int,
    ) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(CALLBACK_TIMEOUT_SECONDS)
        do {
            val info = compiler.compilerInfo
            if (
                info.getInt(
                    CompilerTransportProbeProtocol.KEY_ACTIVE_JOB_COUNT,
                    -1,
                ) == expected
            ) {
                return
            }
            SystemClock.sleep(25L)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("R2 Binder job count did not become $expected.")
    }

    private fun awaitStopCauseSelected(
        compiler: ITypeScriptCompilerPlugin,
    ) {
        val deadline = SystemClock.uptimeMillis() +
            TimeUnit.SECONDS.toMillis(CALLBACK_TIMEOUT_SECONDS)
        do {
            val info = compiler.compilerInfo
            if (
                info.containsKey(R2CompilationProbeProtocol.KEY_STOP_CAUSE_SELECTED) &&
                info.getBoolean(
                    R2CompilationProbeProtocol.KEY_STOP_CAUSE_SELECTED,
                    false,
                )
            ) {
                return
            }
            SystemClock.sleep(25L)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("R2 Binder job did not select its unacknowledged stop.")
    }

    private fun writeStoredZip(
        file: File,
        entries: LinkedHashMap<String, ByteArray>,
    ) {
        FileOutputStream(file, false).use { output ->
            val records = ArrayList<StoredRecord>(entries.size)
            var position = 0L
            entries.forEach { (path, bytes) ->
                val name = path.toByteArray(StandardCharsets.US_ASCII)
                val crc = CRC32().apply { update(bytes) }.value
                records += StoredRecord(name, bytes, crc, position)
                output.putU32(LOCAL_FILE_HEADER_SIGNATURE)
                output.putU16(VERSION_STORED)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU32(crc)
                output.putU32(bytes.size.toLong())
                output.putU32(bytes.size.toLong())
                output.putU16(name.size)
                output.putU16(0)
                output.write(name)
                output.write(bytes)
                position += 30L + name.size + bytes.size
            }
            val centralOffset = position
            records.forEach { record ->
                output.putU32(CENTRAL_DIRECTORY_SIGNATURE)
                output.putU16(VERSION_STORED)
                output.putU16(VERSION_STORED)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU32(record.crc)
                output.putU32(record.bytes.size.toLong())
                output.putU32(record.bytes.size.toLong())
                output.putU16(record.name.size)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU16(0)
                output.putU32(0)
                output.putU32(record.localOffset)
                output.write(record.name)
                position += 46L + record.name.size
            }
            output.putU32(END_OF_CENTRAL_DIRECTORY_SIGNATURE)
            output.putU16(0)
            output.putU16(0)
            output.putU16(records.size)
            output.putU16(records.size)
            output.putU32(position - centralOffset)
            output.putU32(centralOffset)
            output.putU16(0)
            output.fd.sync()
        }
    }

    private fun FileOutputStream.putU16(value: Int) {
        write(value and 0xff)
        write(value ushr 8 and 0xff)
    }

    private fun FileOutputStream.putU32(value: Long) {
        write((value and 0xff).toInt())
        write((value ushr 8 and 0xff).toInt())
        write((value ushr 16 and 0xff).toInt())
        write((value ushr 24 and 0xff).toInt())
    }

    @Suppress("DEPRECATION")
    private fun bindCompilerService(): BoundCompilerSession {
        val discovery = Intent(TypeScriptPluginActions.COMPILER)
            .addCategory(TypeScriptPluginIds.ID)
            .setPackage(context.packageName)
        val serviceInfo = context.packageManager.queryIntentServices(discovery, 0)
            .single()
            .serviceInfo
        val connection = AwaitingServiceConnection()
        assertTrue(
            context.bindService(
                Intent(discovery).setComponent(
                    ComponentName(serviceInfo.packageName, serviceInfo.name),
                ),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        return BoundCompilerSession(
            context = context,
            connection = connection,
            compiler = ITypeScriptCompilerPlugin.Stub.asInterface(
                connection.awaitBinder(),
            ),
            serviceProcessName = serviceInfo.processName,
        )
    }

    private data class CompilationFixture(
        val requestId: String,
        val input: File,
        val output: File,
        val inputArchiveSha256: String,
        val requestManifestSha256: String,
        val compilerDistributionFingerprint: String,
        val declarationsFingerprint: String,
        val declarationProfileFingerprint: String,
        val profileAuthority: R2CompilationProfileAuthority,
        val workerSpinMilliseconds: Int,
        val workerSpinPhase: R2ProjectExecutionPhase?,
        val timeoutMs: Long,
        val terminalCleanupDelayMilliseconds: Int,
    ) {
        fun delete() {
            assertTrue(
                "R2 Binder input fixture could not be deleted: ${input.absolutePath}",
                !input.exists() || input.delete(),
            )
            assertFalse(
                "R2 Binder input fixture remained after deletion: ${input.absolutePath}",
                input.exists(),
            )
            assertTrue(
                "R2 Binder output fixture could not be deleted: ${output.absolutePath}",
                !output.exists() || output.delete(),
            )
            assertFalse(
                "R2 Binder output fixture remained after deletion: ${output.absolutePath}",
                output.exists(),
            )
        }
    }

    private data class StableServiceBundle(
        val entryNames: List<String>,
        val normalizedManifest: Map<String, Any?>,
        val normalizedDiagnostics: Map<String, Any?>,
        val payloads: Map<String, ByteArray>,
    )

    private data class X2SourceDiagnosticCase(
        val label: String,
        val expectedPhase: R2ProjectDiagnosticPhase,
        val expectedCode: Int,
        val expectedMessage: String,
        val expectedStart: Int,
        val expectedLength: Int,
        val expectedLine: Int,
        val expectedColumn: Int,
        val source: String,
    )

    private data class StoredRecord(
        val name: ByteArray,
        val bytes: ByteArray,
        val crc: Long,
        val localOffset: Long,
    )

    private class RecordingCallback : ITypeScriptCompilationCallback.Stub() {

        val terminalCount = AtomicInteger()
        private val terminal = CountDownLatch(1)
        private val result = AtomicReference<Bundle>()

        @Volatile
        private var startReturned = false

        @Volatile
        var callbackBeforeStartReturned = false
            private set

        fun markStartReturned() {
            startReturned = true
        }

        override fun onProgress(progress: Bundle?) = Unit

        override fun onCompleted(completion: Bundle?) {
            if (!startReturned) callbackBeforeStartReturned = true
            terminalCount.incrementAndGet()
            result.compareAndSet(null, completion ?: Bundle())
            terminal.countDown()
        }

        fun awaitTerminal(
            timeoutSeconds: Long = CALLBACK_TIMEOUT_SECONDS,
        ): Bundle {
            assertTrue(
                "R2 Binder compilation did not publish a terminal callback.",
                terminal.await(timeoutSeconds, TimeUnit.SECONDS),
            )
            return requireNotNull(result.get())
        }
    }

    private class AwaitingServiceConnection : ServiceConnection {

        private val connected = CountDownLatch(1)

        @Volatile
        private var binder: IBinder? = null

        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder,
        ) {
            binder = service
            connected.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit

        fun awaitBinder(): IBinder {
            assertTrue(
                "TypeScript compiler service did not bind.",
                connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            )
            return requireNotNull(binder)
        }
    }

    private class BoundCompilerSession(
        private val context: Context,
        private val connection: ServiceConnection,
        val compiler: ITypeScriptCompilerPlugin,
        val serviceProcessName: String,
    ) : Closeable {
        override fun close() {
            context.unbindService(connection)
        }
    }

    private fun newRequestId(label: String) = "r2-$label-${UUID.randomUUID()}"

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        const val ACTIVE_JOB_TIMEOUT_MILLIS = 15_000L
        const val ACTIVE_TIMEOUT_MILLIS = 750L
        const val ACTIVE_WORKER_SPIN_MILLIS = 5_000
        const val LATE_ACK_TIMEOUT_MILLIS = 500L
        const val LATE_ACK_TERMINAL_CLEANUP_DELAY_MILLIS = 3_000
        const val NO_CALLBACK_OBSERVATION_MILLIS = 750L
        const val OUTPUT_SENTINEL = "caller-output-sentinel"
        const val BIND_TIMEOUT_SECONDS = 10L
        const val CALLBACK_TIMEOUT_SECONDS = 30L
        const val CAPACITY_CALLBACK_TIMEOUT_SECONDS = 135L
        const val CAPACITY_LOG_TAG = "R2P3bBinderCapacity"
        const val RESOURCE_LOG_TAG = "R2P3bResource"
        const val NATURAL_TIMEOUT_LOG_TAG = "R2P3bNaturalTimeout"
        const val NATURAL_INGRESS_BINDER_TIMEOUT_MILLIS = 10_000L
        const val REPEATED_RESOURCE_CYCLE_COUNT = 3
        const val RESOURCE_PSS_SAMPLE_COUNT = 5
        const val RESOURCE_PSS_SAMPLE_INTERVAL_MILLIS = 100L
        const val RESOURCE_PSS_BASELINE_SETTLE_MILLIS = 500L
        const val MAX_ADJACENT_WARM_MEDIAN_DELTA_KIB = 16 * 1024
        const val MAX_COMPILER_MEDIAN_GROWTH_FROM_WARM_KIB = 48 * 1024
        const val MAX_LEGACY_SINGLE_PROCESS_COMPILER_MEDIAN_GROWTH_FROM_WARM_KIB =
            96 * 1024
        const val MAX_COMPILER_MEDIAN_GROWTH_AFTER_MATURE_CYCLE_KIB = 32 * 1024
        const val LAZY_INITIALIZATION_FD_COUNT = 2
        const val PROCESS_LOOKUP_ATTEMPTS = 50
        const val PROCESS_LOOKUP_INTERVAL_MILLIS = 100L
        const val R2_INPUT_STAGING_DIRECTORY = "typescript-r2-input-staging"
        const val R2_OUTPUT_STAGING_DIRECTORY = "typescript-r2-output-staging"
        const val R2_PRIVATE_INPUT_PREFIX = "r2-private-input-"
        const val R2_PRIVATE_INPUT_SUFFIX = ".staging"
        const val R2_PROJECT_SPOOL_PREFIX = "r2-project-vfs-"
        const val R2_PROJECT_SPOOL_SUFFIX = ".spool"
        const val R2_PRIVATE_OUTPUT_PREFIX = "r2-private-output-"
        const val R2_PRIVATE_OUTPUT_SUFFIX = ".zip.staging"
        const val EXACTLY_ONCE_OBSERVATION_MILLIS = 250L
        const val X2_SOURCE_PATH = "sources/main.ts"
        val X2_DIAGNOSTICS_ENVELOPE_KEYS = setOf(
            "schema",
            "revision",
            "requestId",
            "targetProfile",
            "profileSchemaRevision",
            "profileRevision",
            "configSchemaRevision",
            "configKind",
            "diagnostics",
        )
        val X2_SOURCE_DIAGNOSTIC_KEYS = setOf(
            "phase",
            "category",
            "code",
            "message",
            "sourcePath",
            "start",
            "length",
            "line",
            "column",
        )
        val CHROMIUM_PROCESS_PROC_FILES = setOf("statm", "status")
        val CHROMIUM_PROCESS_PROC_TARGET =
            Regex("""/proc/([1-9][0-9]*)/(statm|status)""")
        val COMPILER_EVENT_LOOP_FD_TARGETS =
            setOf("anon_inode:[eventpoll]", "anon_inode:[eventfd]")
        const val VERSION_STORED = 10
        const val LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50L
        const val CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50L
        const val END_OF_CENTRAL_DIRECTORY_SIGNATURE = 0x06054b50L
    }
}
