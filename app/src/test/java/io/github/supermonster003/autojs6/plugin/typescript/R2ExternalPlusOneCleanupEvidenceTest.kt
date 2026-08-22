package io.github.supermonster003.autojs6.plugin.typescript

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ExternalPlusOneCleanupEvidenceTest {

    @Test
    fun preAcknowledgementPublishesOnlyStableExactPostTransactionOwnership() {
        val exact = preAcknowledgement(
            descriptorSnapshotAvailable = true,
            inputExactTargetFdCount = 1,
            outputExactTargetFdCount = 1,
            activeLease = true,
        )

        assertTrue(exact.exactPostTransactionOwnershipProven)
        assertFalse(
            exact.copy(inputExactTargetFdCount = 2)
                .exactPostTransactionOwnershipProven,
        )
        assertFalse(
            exact.copy(inputExactTargetFdCount = 0)
                .exactPostTransactionOwnershipProven,
        )
        assertFalse(
            exact.copy(inputExactTargetFdCount = -1)
                .exactPostTransactionOwnershipProven,
        )
        assertFalse(
            exact.copy(outputExactTargetFdCount = 2)
                .exactPostTransactionOwnershipProven,
        )
        assertFalse(
            exact.copy(descriptorSnapshotAvailable = false)
                .exactPostTransactionOwnershipProven,
        )
        assertFalse(
            exact.copy(activeLease = false)
                .exactPostTransactionOwnershipProven,
        )
    }

    @Test
    fun unclaimedHostHandleProvesCursorWasNeverCreated() {
        val evidence =
            R2HostProjectOperationHandle.create().cursorCleanupEvidence()

        assertEquals(
            R2ExternalPlusOneCleanupEvidenceProtocol.CURSOR_STATE_NOT_CREATED,
            evidence.state,
        )
        assertEquals(0, evidence.openedCursorCount)
        assertEquals(0, evidence.closedCursorCount)
        assertTrue(evidence.released)
        assertTrue(evidence.cleanupProven)
    }

    @Test
    fun hostHandleRequiresReleasedBalancedCursorTelemetry() {
        val handle = R2HostProjectOperationHandle.create()
        assertTrue(handle.claim())
        handle.markProjectInputTransportCreated()
        handle.recordProjectInputTransportCleanup(
            transportTelemetry(
                requestId = handle.requestId,
                opened = 2,
                closed = 2,
                released = true,
                active = false,
            ),
        )

        val released = handle.cursorCleanupEvidence()
        assertEquals(
            R2ExternalPlusOneCleanupEvidenceProtocol.CURSOR_STATE_RELEASED,
            released.state,
        )
        assertTrue(released.cleanupProven)

        val unprovenHandle = R2HostProjectOperationHandle.create()
        assertTrue(unprovenHandle.claim())
        unprovenHandle.markProjectInputTransportCreated()
        unprovenHandle.recordProjectInputTransportCleanup(
            transportTelemetry(
                requestId = unprovenHandle.requestId,
                opened = 2,
                closed = 1,
                released = true,
                active = false,
            ),
        )
        assertFalse(unprovenHandle.cursorCleanupEvidence().cleanupProven)
    }

    @Test
    fun resourceSnapshotUsesTheScavengerNamespacesForNamedAndOpenFiles() {
        val inputDirectory = File(
            "sandbox/${R2PrivateTransientOrphanScavenger.INPUT_STAGING_DIRECTORY_NAME}",
        )
        val outputDirectory = File(
            "sandbox/${R2PrivateTransientOrphanScavenger.OUTPUT_STAGING_DIRECTORY_NAME}",
        )
        val operations = FakeOperations(
            links = mapOf(
                "3" to "/caller/input.zip",
                "4" to "/caller/output.zip",
                "5" to
                    "${inputDirectory.path}/r2-private-input-one.staging",
                "6" to
                    "${inputDirectory.path}/r2-project-vfs-one.spool (deleted)",
                "7" to
                    "${outputDirectory.path}/r2-private-output-one.zip.staging",
            ),
            children = mapOf(
                inputDirectory.name to arrayOf(
                    File(inputDirectory, "r2-private-input-one.staging"),
                    File(inputDirectory, "r2-project-vfs-one.spool"),
                    File(inputDirectory, "near-r2-project-vfs-one.spool"),
                ),
                outputDirectory.name to arrayOf(
                    File(
                        outputDirectory,
                        "r2-private-output-one.zip.staging",
                    ),
                ),
            ),
        )

        val snapshot = R2ExternalPlusOneCleanupEvidence.captureResourceSnapshot(
            descriptorTargets =
                R2ExternalPlusOneCleanupDescriptorTargets(
                    inputTarget = "/caller/input.zip",
                    outputTarget = "/caller/output.zip",
                ),
            inputStagingDirectory = inputDirectory,
            outputStagingDirectory = outputDirectory,
            operations = operations,
        )

        assertTrue(snapshot.available)
        assertEquals(1, snapshot.inputExactTargetFdCount)
        assertEquals(1, snapshot.outputExactTargetFdCount)
        assertEquals(1, snapshot.privateInputNamedFileCount)
        assertEquals(1, snapshot.privateInputFdCount)
        assertEquals(1, snapshot.projectSpoolNamedFileCount)
        assertEquals(1, snapshot.projectSpoolFdCount)
        assertEquals(1, snapshot.privateOutputNamedFileCount)
        assertEquals(1, snapshot.privateOutputFdCount)
    }

    @Test
    fun unreadableFdOtherThanAnEnoentRaceFailsTheSnapshotClosed() {
        val inputDirectory = File(
            "sandbox/${R2PrivateTransientOrphanScavenger.INPUT_STAGING_DIRECTORY_NAME}",
        )
        val outputDirectory = File(
            "sandbox/${R2PrivateTransientOrphanScavenger.OUTPUT_STAGING_DIRECTORY_NAME}",
        )
        val operations = object : R2ExternalPlusOneCleanupEvidenceOperations {
            override fun listFdNames() = arrayOf("3")

            override fun readLink(path: String): String =
                throw IOException("denied")

            override fun listChildren(directory: File) = emptyArray<File>()
        }

        val snapshot = R2ExternalPlusOneCleanupEvidence.captureResourceSnapshot(
            descriptorTargets =
                R2ExternalPlusOneCleanupDescriptorTargets(
                    inputTarget = "/caller/input.zip",
                    outputTarget = "/caller/output.zip",
                ),
            inputStagingDirectory = inputDirectory,
            outputStagingDirectory = outputDirectory,
            operations = operations,
        )

        assertFalse(snapshot.available)
        assertEquals(-1, snapshot.inputExactTargetFdCount)
        assertEquals(-1, snapshot.privateInputFdCount)
    }

    private fun transportTelemetry(
        requestId: String,
        opened: Int,
        closed: Int,
        released: Boolean,
        active: Boolean,
    ) = R2ProjectInputTransportTelemetry(
        requestId = requestId,
        sentFrameCount = 0,
        maximumEncodedFrameBytes = 0,
        acceptedPayloadBytes = 0,
        totalPayloadBytes = 0,
        fileIndex = 0,
        fileOffset = 0,
        stage = "PROJECT_START",
        complete = false,
        openedCursorCount = opened,
        closedCursorCount = closed,
        activeCursor = active,
        released = released,
    )

    private fun preAcknowledgement(
        descriptorSnapshotAvailable: Boolean,
        inputExactTargetFdCount: Int,
        outputExactTargetFdCount: Int,
        activeLease: Boolean,
    ) = R2ExternalPlusOneCleanupPreAcknowledgement(
        requestId = "request",
        jobTokenSha256 = "0".repeat(64),
        descriptorTargets = R2ExternalPlusOneCleanupDescriptorTargets(
            inputTarget = "/caller/input.zip",
            outputTarget = "/caller/output.zip",
        ),
        descriptorSnapshotAvailable = descriptorSnapshotAvailable,
        inputExactTargetFdCount = inputExactTargetFdCount,
        outputExactTargetFdCount = outputExactTargetFdCount,
        activeLease = activeLease,
        processIdentity = R2ExternalPlusOneCleanupSafeProcessIdentity(
            pid = 1,
            startTimeTicks = 2L,
            processName = "compiler",
            epochSha256 = "1".repeat(64),
        ),
    )

    private class FakeOperations(
        private val links: Map<String, String>,
        private val children: Map<String, Array<File>>,
    ) : R2ExternalPlusOneCleanupEvidenceOperations {

        override fun listFdNames(): Array<String> =
            links.keys.toTypedArray()

        override fun readLink(path: String): String =
            links.getValue(path.substringAfterLast('/'))

        override fun listChildren(directory: File): Array<File> =
            children[directory.name] ?: emptyArray()

        override fun isRegularFile(file: File) = true
    }
}
