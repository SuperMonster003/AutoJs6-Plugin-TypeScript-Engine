package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import org.autojs.plugin.typescript.api.ITypeScriptCompilerPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CompilerProcessSupervisorTest {

    @Test
    fun procStatParserUsesFieldAfterTheLastProcessNameParenthesis() {
        val fields = MutableList(20) { index -> (index + 1).toString() }
        fields[19] = "987654321"
        val stat = "42 (compiler worker (r2)) ${fields.joinToString(" ")}"

        assertEquals(
            987654321L,
            R2CompilerProcessProcfs.parseProcessStartTimeTicks(stat),
        )
    }

    @Test
    fun procStatParserRejectsMissingMalformedAndNonPositiveStartTime() {
        assertNull(R2CompilerProcessProcfs.parseProcessStartTimeTicks(""))
        assertNull(
            R2CompilerProcessProcfs.parseProcessStartTimeTicks(
                "42 compiler " + List(20) { "1" }.joinToString(" "),
            ),
        )
        assertNull(
            R2CompilerProcessProcfs.parseProcessStartTimeTicks(
                statWithStartTime("not-a-number"),
            ),
        )
        assertNull(
            R2CompilerProcessProcfs.parseProcessStartTimeTicks(
                statWithStartTime("0"),
            ),
        )
    }

    @Test
    fun deathProofRequiresBothBinderDeathAndAbsentOrReusedIdentity() {
        val identity = identity()

        assertTrue(
            R2CompilerProcessTerminationProof(
                oldIdentity = identity,
                terminationCommandAccepted = true,
                binderDeathObserved = true,
                finalObservation = R2CompilerProcessObservation.ABSENT,
                successorFenceCleared = true,
            ).deathProven,
        )
        assertTrue(
            R2CompilerProcessTerminationProof(
                oldIdentity = identity,
                terminationCommandAccepted = true,
                binderDeathObserved = true,
                finalObservation = R2CompilerProcessObservation.REUSED,
                successorFenceCleared = true,
            ).deathProven,
        )
        assertFalse(
            R2CompilerProcessTerminationProof(
                oldIdentity = identity,
                terminationCommandAccepted = true,
                binderDeathObserved = true,
                finalObservation = R2CompilerProcessObservation.UNREADABLE,
                successorFenceCleared = true,
            ).deathProven,
        )
        assertFalse(
            R2CompilerProcessTerminationProof(
                oldIdentity = identity,
                terminationCommandAccepted = true,
                binderDeathObserved = false,
                finalObservation = R2CompilerProcessObservation.ABSENT,
                successorFenceCleared = true,
            ).deathProven,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun processEpochRejectsNonCanonicalNonce() {
        identity(nonce = "A".repeat(64))
    }

    @Test(expected = IllegalArgumentException::class)
    fun processEpochRejectsNonCanonicalBootId() {
        identity(bootId = "not-a-kernel-boot-id")
    }

    @Test
    fun procfsTreatsAnotherKernelBootAsAnOldEpochGoneProof() {
        val oldBootId = "00000000-0000-4000-8000-000000000001"
        val currentBootId = "00000000-0000-4000-8000-000000000002"

        assertEquals(
            R2CompilerProcessObservation.REUSED,
            R2CompilerProcessProcfs(
                bootIdProvider = { currentBootId },
            ).observe(identity(bootId = oldBootId)),
        )
    }

    @Test
    fun procfsFailsClosedWhenKernelBootIdIsUnreadable() {
        assertEquals(
            R2CompilerProcessObservation.UNREADABLE,
            R2CompilerProcessProcfs(
                bootIdProvider = { null },
            ).observe(identity()),
        )
        assertEquals(
            R2CompilerProcessObservation.UNREADABLE,
            R2CompilerProcessProcfs(
                bootIdProvider = { "malformed-boot-id" },
            ).observe(identity()),
        )
    }

    @Test
    fun terminationCommandDeadlineRequiresEnoughMonotonicBudget() {
        val now = 10_000L
        val minimumBudget =
            R2CompilerProcessSupervisorProtocol.MIN_COMMAND_EXECUTION_BUDGET_MILLIS

        assertFalse(
            R2CompilerProcessEpoch.hasUsableCommandDeadline(
                commandDeadline = now - 1L,
                now = now,
            ),
        )
        assertFalse(
            R2CompilerProcessEpoch.hasUsableCommandDeadline(
                commandDeadline = now + minimumBudget - 1L,
                now = now,
            ),
        )
        assertTrue(
            R2CompilerProcessEpoch.hasUsableCommandDeadline(
                commandDeadline = now + minimumBudget,
                now = now,
            ),
        )
    }

    @Test
    fun prearmedJobRetriesOnlyWhileItsMarkerCommitWindowCanRemainOpen() {
        assertEquals(
            R2CompilerProcessSupervisorPrearmDecision.RETRY,
            R2CompilerProcessSupervisorPrearmDecision.decide(
                prearmWindowOpen = true,
                observation = R2CompilerProcessObservation.SAME,
            ),
        )
        assertEquals(
            R2CompilerProcessSupervisorPrearmDecision.RETRY,
            R2CompilerProcessSupervisorPrearmDecision.decide(
                prearmWindowOpen = true,
                observation = R2CompilerProcessObservation.UNREADABLE,
            ),
        )
        listOf(
            R2CompilerProcessObservation.ABSENT,
            R2CompilerProcessObservation.REUSED,
        ).forEach { observation ->
            assertEquals(
                R2CompilerProcessSupervisorPrearmDecision.RECHECK_MARKER,
                R2CompilerProcessSupervisorPrearmDecision.decide(
                    prearmWindowOpen = true,
                    observation = observation,
                ),
            )
        }
        R2CompilerProcessObservation.entries.forEach { observation ->
            assertEquals(
                R2CompilerProcessSupervisorPrearmDecision.NO_WORK,
                R2CompilerProcessSupervisorPrearmDecision.decide(
                    prearmWindowOpen = false,
                    observation = observation,
                ),
            )
        }
    }

    @Test
    fun currentStopBeforeReplacementLeavesTheNextGenerationCurrent() {
        val gate = generationGate()
        val generationA = TestJobGeneration("a")
        val generationB = TestJobGeneration("b")

        assertEquals(
            R2CompilerProcessSupervisorJobStartDecision.INSTALLED,
            gate.start(generationA).decision,
        )
        val stopA = gate.stop(generationA.token) { decision ->
            assertEquals(
                R2CompilerProcessSupervisorJobStopDecision.CURRENT,
                decision.decision,
            )
            assertTrue(decision.currentAtDecision === generationA)
            assertFalse(generationA.retired)
        }
        assertEquals(
            R2CompilerProcessSupervisorJobStopDecision.CURRENT,
            stopA.decision,
        )
        assertTrue(generationA.retired)
        assertNull(gate.currentOrNull())

        val startB = gate.start(generationB)
        assertEquals(
            R2CompilerProcessSupervisorJobStartDecision.INSTALLED,
            startB.decision,
        )
        assertNull(startB.currentAtDecision)
        assertTrue(gate.isCurrent(generationB))
        assertFalse(generationB.retired)
    }

    @Test
    fun lateRetiredStopAfterReplacementCannotAffectCurrentGeneration() {
        val gate = generationGate()
        val generationA = TestJobGeneration("a")
        val generationB = TestJobGeneration("b")

        assertEquals(
            R2CompilerProcessSupervisorJobStartDecision.INSTALLED,
            gate.start(generationA).decision,
        )
        val startB = gate.start(generationB)
        assertEquals(
            R2CompilerProcessSupervisorJobStartDecision.REPLACED_CURRENT,
            startB.decision,
        )
        assertTrue(startB.currentAtDecision === generationA)
        assertTrue(generationA.retired)
        assertTrue(gate.isCurrent(generationB))
        assertFalse(generationB.retired)

        val lateStopA = gate.stop(generationA.token)
        assertEquals(
            R2CompilerProcessSupervisorJobStopDecision.STALE,
            lateStopA.decision,
        )
        assertTrue(lateStopA.currentAtDecision === generationB)
        assertTrue(gate.isCurrent(generationB))
        assertFalse(generationB.retired)
    }

    @Test
    fun lifecycleJournalCutoffRejectsSameBootEventsAtOrBeforeBarrier() {
        val bootId = "00000000-0000-4000-8000-000000000001"
        val cutoff =
            R2CompilerProcessSupervisorJobLifecycleCutoff(
                bootId = bootId,
                elapsedRealtimeNanos = 10_000L,
            )

        assertFalse(
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy.accepts(
                cutoff = cutoff,
                eventBootId = bootId,
                eventElapsedRealtimeNanos = 9_999L,
            ),
        )
        assertFalse(
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy.accepts(
                cutoff = cutoff,
                eventBootId = bootId,
                eventElapsedRealtimeNanos = 10_000L,
            ),
        )
        assertTrue(
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy.accepts(
                cutoff = cutoff,
                eventBootId = bootId,
                eventElapsedRealtimeNanos = 10_001L,
            ),
        )
        assertEquals(
            cutoff,
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                .advanceForClear(
                    existing = cutoff,
                    currentBootId = bootId,
                    elapsedRealtimeNanos = 9_000L,
                ),
        )
        assertEquals(
            11_000L,
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                .advanceForClear(
                    existing = cutoff,
                    currentBootId = bootId,
                    elapsedRealtimeNanos = 11_000L,
                )
                .elapsedRealtimeNanos,
        )
    }

    @Test
    fun lifecycleJournalCutoffStartsANewEpochAfterBootChanges() {
        val oldBootId = "00000000-0000-4000-8000-000000000001"
        val currentBootId =
            "00000000-0000-4000-8000-000000000002"
        val oldCutoff =
            R2CompilerProcessSupervisorJobLifecycleCutoff(
                bootId = oldBootId,
                elapsedRealtimeNanos = 100_000L,
            )

        val currentCutoff =
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy
                .forCurrentBoot(
                    existing = oldCutoff,
                    currentBootId = currentBootId,
                )

        assertEquals(currentBootId, currentCutoff.bootId)
        assertEquals(0L, currentCutoff.elapsedRealtimeNanos)
        assertFalse(
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy.accepts(
                cutoff = currentCutoff,
                eventBootId = oldBootId,
                eventElapsedRealtimeNanos = 100_001L,
            ),
        )
        assertTrue(
            R2CompilerProcessSupervisorJobLifecycleCutoffPolicy.accepts(
                cutoff = currentCutoff,
                eventBootId = currentBootId,
                eventElapsedRealtimeNanos = 1L,
            ),
        )
    }

    @Test
    fun lifecycleJournalRecordPolicyFencesTokenRelationships() {
        val startReplaced =
            R2CompilerProcessSupervisorJobLifecycleEvent
                .START_REPLACED_CURRENT
        val stop =
            R2CompilerProcessSupervisorJobLifecycleEvent.STOP_CALLBACK
        val policy =
            R2CompilerProcessSupervisorJobLifecycleRecordPolicy

        assertTrue(
            policy.hasValidTokenRelation(
                event = startReplaced,
                stopDecision = null,
                workToken = "b",
                currentWorkToken = "a",
            ),
        )
        assertFalse(
            policy.hasValidTokenRelation(
                event = startReplaced,
                stopDecision = null,
                workToken = "a",
                currentWorkToken = "a",
            ),
        )
        assertTrue(
            policy.hasValidTokenRelation(
                event = startReplaced,
                stopDecision = null,
                workToken = null,
                currentWorkToken = "a",
            ),
        )
        assertFalse(
            policy.hasValidTokenRelation(
                event = startReplaced,
                stopDecision = null,
                workToken = null,
                currentWorkToken = null,
            ),
        )
        assertTrue(
            policy.hasValidTokenRelation(
                event = stop,
                stopDecision =
                    R2CompilerProcessSupervisorJobStopDecision.CURRENT,
                workToken = null,
                currentWorkToken = null,
            ),
        )
        assertFalse(
            policy.hasValidTokenRelation(
                event = stop,
                stopDecision =
                    R2CompilerProcessSupervisorJobStopDecision.CURRENT,
                workToken = "a",
                currentWorkToken = "b",
            ),
        )
        assertTrue(
            policy.hasValidTokenRelation(
                event = stop,
                stopDecision =
                    R2CompilerProcessSupervisorJobStopDecision.STALE,
                workToken = null,
                currentWorkToken = "b",
            ),
        )
        assertTrue(
            policy.hasValidTokenRelation(
                event = stop,
                stopDecision =
                    R2CompilerProcessSupervisorJobStopDecision.STALE,
                workToken = "a",
                currentWorkToken = null,
            ),
        )
        assertFalse(
            policy.hasValidTokenRelation(
                event = stop,
                stopDecision =
                    R2CompilerProcessSupervisorJobStopDecision.STALE,
                workToken = null,
                currentWorkToken = null,
            ),
        )
        assertTrue(
            policy.hasValidTokenRelation(
                event = stop,
                stopDecision =
                    R2CompilerProcessSupervisorJobStopDecision.NO_CURRENT,
                workToken = "a",
                currentWorkToken = null,
            ),
        )
        assertFalse(
            policy.hasValidTokenRelation(
                event = stop,
                stopDecision =
                    R2CompilerProcessSupervisorJobStopDecision.NO_CURRENT,
                workToken = "a",
                currentWorkToken = "b",
            ),
        )
    }

    @Test
    fun exactIdentityMatchRejectsAChangedProcessEpoch() {
        val current = identity()

        assertTrue(current.matchesExact(current.copy()))
        assertFalse(
            current.matchesExact(
                current.copy(
                    startTimeTicks = current.startTimeTicks + 1L,
                ),
            ),
        )
        assertFalse(
            current.matchesExact(
                current.copy(
                    nonce = "b".repeat(
                        R2CompilerProcessSupervisorProtocol.PROCESS_NONCE_CHARS,
                    ),
                ),
            ),
        )
    }

    @Test
    fun onlineCompilerCallerPolicyUsesBinderIdentityRoleAndSupervisorBoot() {
        val compiler = identity()
        val policy = R2CompilerProcessSupervisorOnlineIdentityPolicy

        assertTrue(
            policy.compilerCallerMatches(
                identity = compiler,
                callingUid = compiler.uid,
                callingPid = compiler.pid,
                applicationUid = compiler.uid,
                supervisorBootId = compiler.bootId,
                expectedCompilerProcessName = compiler.processName,
            ),
        )
        assertFalse(
            policy.compilerCallerMatches(
                identity = compiler,
                callingUid = compiler.uid + 1,
                callingPid = compiler.pid,
                applicationUid = compiler.uid,
                supervisorBootId = compiler.bootId,
                expectedCompilerProcessName = compiler.processName,
            ),
        )
        assertFalse(
            policy.compilerCallerMatches(
                identity = compiler,
                callingUid = compiler.uid,
                callingPid = compiler.pid + 1,
                applicationUid = compiler.uid,
                supervisorBootId = compiler.bootId,
                expectedCompilerProcessName = compiler.processName,
            ),
        )
        assertFalse(
            policy.compilerCallerMatches(
                identity = compiler.copy(processName = "${compiler.processName}_other"),
                callingUid = compiler.uid,
                callingPid = compiler.pid,
                applicationUid = compiler.uid,
                supervisorBootId = compiler.bootId,
                expectedCompilerProcessName = compiler.processName,
            ),
        )
        assertFalse(
            policy.compilerCallerMatches(
                identity = compiler,
                callingUid = compiler.uid,
                callingPid = compiler.pid,
                applicationUid = compiler.uid,
                supervisorBootId =
                    "00000000-0000-4000-8000-000000000002",
                expectedCompilerProcessName = compiler.processName,
            ),
        )
    }

    @Test
    fun onlineSupervisorChallengeBuildsIdentityWithoutCrossProcessProcfs() {
        val bootId = "00000000-0000-4000-8000-000000000001"
        val uid = 10001
        val pid = 5151
        val startTimeTicks = 9999L
        val processName =
            "io.github.supermonster003.autojs6.plugin.typescript:typescript_supervisor"
        val policy = R2CompilerProcessSupervisorOnlineIdentityPolicy

        fun observed(
            reportedBootId: String? = bootId,
            reportedUid: Int = uid,
            reportedPid: Int = pid,
            reportedStartTimeTicks: Long = startTimeTicks,
            reportedProcessName: String? = processName,
            callingUid: Int = uid,
            callingPid: Int = pid,
        ) = policy.supervisorIdentityOrNull(
            supervisorBootId = reportedBootId,
            supervisorUid = reportedUid,
            supervisorPid = reportedPid,
            supervisorStartTimeTicks = reportedStartTimeTicks,
            supervisorProcessName = reportedProcessName,
            callingUid = callingUid,
            callingPid = callingPid,
            compilerUid = uid,
            compilerBootId = bootId,
            expectedSupervisorProcessName = processName,
        )

        assertEquals(
            R2ObservedCompilerProcess(
                bootId = bootId,
                uid = uid,
                startTimeTicks = startTimeTicks,
                processName = processName,
            ),
            observed(),
        )
        assertNull(observed(callingUid = uid + 1))
        assertNull(observed(callingPid = pid + 1))
        assertNull(observed(reportedUid = uid + 1))
        assertNull(observed(reportedPid = 0, callingPid = 0))
        assertNull(observed(reportedStartTimeTicks = 0L))
        assertNull(observed(reportedProcessName = "${processName}_other"))
        assertNull(
            observed(
                reportedBootId =
                    "00000000-0000-4000-8000-000000000002",
            ),
        )
    }

    @Test
    fun supervisorKeepsLegacyAndTypedEndpointOverloads() {
        val overloads =
            R2CompilerProcessSupervisor::class.java.declaredMethods
                .filter { method ->
                    method.name.startsWith("terminatePoisonedEpoch") &&
                        !method.name.endsWith("\$default")
                }

        assertTrue(
            overloads.any { method ->
                method.parameterTypes.toList() ==
                    listOf(
                        ITypeScriptCompilerPlugin::class.java,
                        IBinder::class.java,
                        R2CompilerProcessEpochIdentity::class.java,
                        Long::class.javaPrimitiveType,
                    ) &&
                    method.returnType ==
                    R2CompilerProcessTerminationProof::class.java
            },
        )
        assertTrue(
            overloads.any { method ->
                method.parameterTypes.toList() ==
                    listOf(
                        IBinder::class.java,
                        R2CompilerProcessEpochIdentity::class.java,
                        String::class.java,
                        Long::class.javaPrimitiveType,
                        Function1::class.java,
                    ) &&
                    method.returnType ==
                    R2CompilerProcessTerminationProof::class.java
            },
        )
    }

    @Test
    fun processEpochKeepsBundleAdapterAndTypedOverload() {
        val overloads =
            R2CompilerProcessEpoch::class.java.declaredMethods
                .filter { method ->
                    method.name == "scheduleExactSelfTermination"
                }
                .map { method -> method.parameterTypes.toList() }

        assertTrue(
            listOf(
                Context::class.java,
                Bundle::class.java,
            ) in overloads,
        )
        assertTrue(
            listOf(
                Context::class.java,
                R2CompilerProcessEpochIdentity::class.java,
                Long::class.javaPrimitiveType,
            ) in overloads,
        )
    }

    private fun statWithStartTime(startTime: String): String {
        val fields = MutableList(20) { "1" }
        fields[19] = startTime
        return "42 (compiler) ${fields.joinToString(" ")}"
    }

    private fun generationGate() =
        R2CompilerProcessSupervisorJobGenerationGate<TestJobGeneration>(
            generationToken = { generation -> generation.token },
            retire = { generation -> generation.retired = true },
        )

    private fun identity(
        bootId: String = "00000000-0000-4000-8000-000000000001",
        nonce: String = "a".repeat(64),
    ) = R2CompilerProcessEpochIdentity(
        bootId = bootId,
        uid = 10001,
        pid = 4242,
        startTimeTicks = 1234L,
        processName =
            "io.github.supermonster003.autojs6.plugin.typescript:typescript_compiler",
        nonce = nonce,
    )

    private data class TestJobGeneration(
        val token: String,
        var retired: Boolean = false,
    )
}
