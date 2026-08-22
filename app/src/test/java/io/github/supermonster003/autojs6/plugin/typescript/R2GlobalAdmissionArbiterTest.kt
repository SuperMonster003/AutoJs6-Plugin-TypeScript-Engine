package io.github.supermonster003.autojs6.plugin.typescript

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class R2GlobalAdmissionArbiterTest {

    @Test
    fun jobLeaseBindsUidRequestTokenAndCallbackUntilCleanupProof() {
        val sequence = AtomicInteger()
        val arbiter = R2GlobalAdmissionArbiter {
            "token-${sequence.incrementAndGet()}"
        }
        val callback = Any()
        val admitted = arbiter.admitJob(42, "request-a", callback)
        val lease = requireNotNull(admitted.lease)

        assertTrue(admitted.accepted)
        assertSame(callback, lease.callbackIdentity)
        assertSame(
            lease,
            arbiter.authorizeActiveJob(42, "request-a", "token-1"),
        )
        assertNull(arbiter.authorizeActiveJob(43, "request-a", "token-1"))
        assertNull(arbiter.authorizeActiveJob(42, "request-b", "token-1"))
        assertNull(arbiter.authorizeActiveJob(42, "request-a", "token-wrong"))
        assertEquals(1, arbiter.activeAcceptedJobCount())

        assertTrue(arbiter.completeJob(lease, R2AdmissionTerminal.EMITTED))
        assertEquals(0, arbiter.activeAcceptedJobCount())
        assertTrue(arbiter.matchesTerminalJob(42, "request-a", "token-1"))
        assertFalse(arbiter.matchesTerminalJob(43, "request-a", "token-1"))
        assertFalse(arbiter.matchesTerminalJob(42, "request-a", "token-wrong"))
        assertFalse(arbiter.completeJob(lease, R2AdmissionTerminal.EMITTED))
    }

    @Test
    fun publicMissedAcknowledgementIsObservableWhileDebugRemainsIdempotent() {
        val sequence = AtomicInteger()
        val arbiter = R2GlobalAdmissionArbiter {
            "token-${sequence.incrementAndGet()}"
        }
        val publicLease = requireNotNull(
            arbiter.admitJob(
                callingUid = 7,
                requestId = "public",
                callbackIdentity = Any(),
                lateUnacknowledgedAcknowledgementAccepted = false,
            ).lease,
        )
        assertTrue(
            arbiter.completeJob(
                publicLease,
                R2AdmissionTerminal.UNACKNOWLEDGED,
                acknowledgementWasArmed = false,
            ),
        )
        assertFalse(
            arbiter.acceptsTerminalAcknowledgement(
                callingUid = 7,
                requestId = "public",
                jobToken = publicLease.jobToken,
            ),
        )
        val acknowledgedPublicLease = requireNotNull(
            arbiter.admitJob(
                callingUid = 7,
                requestId = "public-acknowledged",
                callbackIdentity = Any(),
                lateUnacknowledgedAcknowledgementAccepted = false,
            ).lease,
        )
        assertTrue(
            arbiter.completeJob(
                acknowledgedPublicLease,
                R2AdmissionTerminal.TIMED_OUT,
                acknowledgementWasArmed = true,
            ),
        )
        assertTrue(
            arbiter.acceptsTerminalAcknowledgement(
                callingUid = 7,
                requestId = "public-acknowledged",
                jobToken = acknowledgedPublicLease.jobToken,
            ),
        )

        val debugLease = requireNotNull(
            arbiter.admitJob(7, "debug", Any()).lease,
        )
        assertTrue(
            arbiter.completeJob(
                debugLease,
                R2AdmissionTerminal.UNACKNOWLEDGED,
                acknowledgementWasArmed = false,
            ),
        )
        assertTrue(
            arbiter.acceptsTerminalAcknowledgement(
                callingUid = 7,
                requestId = "debug",
                jobToken = debugLease.jobToken,
            ),
        )
    }

    @Test
    fun oneGlobalSlotSerializesInternalAndAcceptedOperations() {
        val arbiter = R2GlobalAdmissionArbiter { "token" }
        val internal = requireNotNull(arbiter.admitInternal(Any()).lease)

        val busyJob = arbiter.admitJob(1, "request", Any())
        assertFalse(busyJob.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_BUSY, busyJob.errorCode)

        assertTrue(arbiter.releaseInternal(internal))
        val job = requireNotNull(arbiter.admitJob(1, "request", Any()).lease)
        val busyInternal = arbiter.admitInternal(Any())
        assertFalse(busyInternal.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_BUSY, busyInternal.errorCode)

        assertTrue(arbiter.abortJobAdmission(job))
        assertTrue(arbiter.admitInternal(Any()).accepted)
    }

    @Test
    fun duplicateRequestAndTerminalTokenRemainBoundedAndIdempotent() {
        val sequence = AtomicInteger()
        val arbiter = R2GlobalAdmissionArbiter {
            "token-${sequence.incrementAndGet()}"
        }
        val first = requireNotNull(
            arbiter.admitJob(7, "same-request", Any()).lease,
        )
        assertTrue(arbiter.completeJob(first, R2AdmissionTerminal.CANCELLED))

        val duplicate = arbiter.admitJob(7, "same-request", Any())
        assertFalse(duplicate.accepted)
        assertEquals(TypeScriptCompilerContract.ERROR_BUSY, duplicate.errorCode)

        val otherOwner = arbiter.admitJob(8, "same-request", Any())
        val otherLease = requireNotNull(otherOwner.lease)
        assertNotSame(first, otherLease)
        assertEquals("token-2", otherLease.jobToken)
        assertTrue(arbiter.abortJobAdmission(otherLease))
    }

    @Test
    fun poisonRetainsExactLeaseAndDeniesAllLaterAdmission() {
        val arbiter = R2GlobalAdmissionArbiter { "token" }
        val lease = requireNotNull(arbiter.admitJob(1, "poison", Any()).lease)

        assertTrue(arbiter.poison(lease))
        assertTrue(arbiter.requiresExternalSupervision())
        assertTrue(arbiter.isCurrent(lease))
        assertEquals(1, arbiter.activeAcceptedJobCount())

        val rejectedJob = arbiter.admitJob(2, "later", Any())
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            rejectedJob.errorCode,
        )
        val rejectedInternal = arbiter.admitInternal(Any())
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            rejectedInternal.errorCode,
        )
    }

    @Test
    fun poisonObserverRunsOnceOutsideLockAndMayReenterArbiter() {
        val observerCalls = AtomicInteger()
        val executor = Executors.newSingleThreadExecutor()
        lateinit var arbiter: R2GlobalAdmissionArbiter
        arbiter = R2GlobalAdmissionArbiter(
            poisonObserver = {
                assertTrue(arbiter.requiresExternalSupervision())
                assertTrue(
                    executor.submit<Boolean> {
                        arbiter.requiresExternalSupervision()
                    }.get(5, TimeUnit.SECONDS),
                )
                observerCalls.incrementAndGet()
            },
            jobTokenGenerator = { "token" },
        )
        try {
            val lease = requireNotNull(
                arbiter.admitJob(1, "poison", Any()).lease,
            )

            assertTrue(arbiter.poison(lease))
            assertTrue(arbiter.poison(lease))
            arbiter.failClosed()

            assertEquals(1, observerCalls.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun failClosedObserverFailureCannotUndoPoisonOrEscapeToCaller() {
        val observerCalls = AtomicInteger()
        val arbiter = R2GlobalAdmissionArbiter(
            poisonObserver = {
                observerCalls.incrementAndGet()
                error("supervisor unavailable")
            },
        )

        arbiter.failClosed()
        arbiter.failClosed()

        assertEquals(1, observerCalls.get())
        assertTrue(arbiter.requiresExternalSupervision())
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            arbiter.admitInternal(Any()).errorCode,
        )
    }

    @Test
    fun closeRejectsNewWorkWithoutForgingCleanupForActiveLease() {
        val sequence = AtomicInteger()
        val arbiter = R2GlobalAdmissionArbiter {
            "token-${sequence.incrementAndGet()}"
        }
        val lease = requireNotNull(arbiter.admitJob(1, "active", Any()).lease)

        arbiter.close()

        assertTrue(arbiter.isCurrent(lease))
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            arbiter.admitJob(2, "new", Any()).errorCode,
        )
        assertTrue(arbiter.completeJob(lease, R2AdmissionTerminal.SERVICE_DESTROYED))

        arbiter.openServiceEpoch()

        val reopened = requireNotNull(arbiter.admitJob(2, "new", Any()).lease)
        assertTrue(arbiter.abortJobAdmission(reopened))
        assertEquals(
            TypeScriptCompilerContract.ERROR_BUSY,
            arbiter.admitJob(1, "active", Any()).errorCode,
        )
    }

    @Test
    fun serviceEpochCannotClearProcessPoison() {
        val arbiter = R2GlobalAdmissionArbiter { "token" }
        val lease = requireNotNull(arbiter.admitJob(1, "active", Any()).lease)
        assertTrue(arbiter.poison(lease))
        arbiter.close()

        arbiter.openServiceEpoch()

        assertTrue(arbiter.requiresExternalSupervision())
        assertSame(lease, arbiter.authorizeActiveJob(1, "active", "token"))
        assertEquals(
            TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            arbiter.admitJob(2, "new", Any()).errorCode,
        )
    }

    @Test
    fun concurrentAdmissionHasExactlyOneWinner() {
        val sequence = AtomicInteger()
        val arbiter = R2GlobalAdmissionArbiter {
            "token-${sequence.incrementAndGet()}"
        }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val decisions = executor.invokeAll(
                (0 until 64).map { index ->
                    Callable {
                        arbiter.admitJob(
                            callingUid = index + 1,
                            requestId = "request-$index",
                            callbackIdentity = Any(),
                        )
                    }
                },
            ).map { future -> future.get(5, TimeUnit.SECONDS) }

            assertEquals(1, decisions.count(R2JobAdmissionDecision::accepted))
            assertEquals(
                63,
                decisions.count { decision ->
                    decision.errorCode == TypeScriptCompilerContract.ERROR_BUSY
                },
            )
        } finally {
            executor.shutdownNow()
        }
    }
}
