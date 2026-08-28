package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2ExternalCapacityEvidenceProtocolTest {

    @Test
    fun exactEvidenceAttestationDerivesOnlyTheBoundedCapacityOrigin() {
        val controls = R2ExternalCapacityEvidenceProtocol.controlsOrNull(
            baseControls = publicControls(),
            scope = STORED_SCOPE,
            storedContainerScope = STORED_SCOPE,
            deflatedExpandedScope = DEFLATED_SCOPE,
            projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
            entrySourceUri = R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
            attestation = validAttestation(),
        )

        requireNotNull(controls)
        assertEquals(
            R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE,
            controls.origin,
        )
        assertTrue(controls.expandedWorkerCapacity)
        assertEquals(0, controls.workerSpinMilliseconds)
        assertNull(controls.workerSpinPhase)
        assertEquals(0, controls.terminalCleanupDelayMilliseconds)
        assertTrue(controls.externalCleanupEvidenceEnabled)

        val deflated = R2ExternalCapacityEvidenceProtocol.controlsOrNull(
            baseControls = publicControls(),
            scope = DEFLATED_SCOPE,
            storedContainerScope = STORED_SCOPE,
            deflatedExpandedScope = DEFLATED_SCOPE,
            projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
            entrySourceUri = R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
            attestation = validAttestation().copy(inputArchiveBytes = 8L * 1024L),
        )
        assertEquals(
            R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE,
            requireNotNull(deflated).origin,
        )
    }

    @Test
    fun evidenceAttestationFailsClosedForEveryIdentityAndBuildBoundary() {
        val base = publicControls()
        val valid = validAttestation()

        listOf(
            valid.copy(evidenceBuildEnabled = false),
            valid.copy(applicationUid = 0),
            valid.copy(callingUid = 0, fixtureUid = 0),
            valid.copy(applicationUid = valid.callingUid),
            valid.copy(fixtureUid = valid.callingUid + 1),
            valid.copy(callingPackages = setOf("unexpected.fixture")),
            valid.copy(
                callingPackages = setOf(
                    R2ExternalCapacityEvidenceProtocol.FIXTURE_PACKAGE,
                    "shared.uid.peer",
                ),
            ),
            valid.copy(applicationTestOnly = false),
            valid.copy(fixtureTestOnly = false),
            valid.copy(signaturesMatch = false),
            valid.copy(
                inputArchiveBytes =
                    R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES - 1L,
            ),
        ).forEach { attestation ->
            assertNull(
                R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                    baseControls = base,
                    scope = STORED_SCOPE,
                    storedContainerScope = STORED_SCOPE,
                    deflatedExpandedScope = DEFLATED_SCOPE,
                    projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
                    entrySourceUri =
                        R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
                    attestation = attestation,
                ),
            )
        }

        assertNull(
            R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                baseControls = base,
                scope = "wrong-scope",
                storedContainerScope = STORED_SCOPE,
                deflatedExpandedScope = DEFLATED_SCOPE,
                projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
                entrySourceUri =
                    R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
                attestation = valid,
            ),
        )
        assertNull(
            R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                baseControls = base,
                scope = STORED_SCOPE,
                storedContainerScope = STORED_SCOPE,
                deflatedExpandedScope = DEFLATED_SCOPE,
                projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
                entrySourceUri = "autojs://source/project/wrong.ts",
                attestation = valid,
            ),
        )
        assertNull(
            R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                baseControls = null,
                scope = STORED_SCOPE,
                storedContainerScope = STORED_SCOPE,
                deflatedExpandedScope = DEFLATED_SCOPE,
                projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
                entrySourceUri =
                    R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
                attestation = valid,
            ),
        )
        assertNull(
            R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                baseControls = base.copy(
                    origin = R2CompilationControlOrigin.DEBUG_PROBE,
                ),
                scope = STORED_SCOPE,
                storedContainerScope = STORED_SCOPE,
                deflatedExpandedScope = DEFLATED_SCOPE,
                projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
                entrySourceUri =
                    R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
                attestation = valid,
            ),
        )
        listOf(
            Triple("", DEFLATED_SCOPE, STORED_SCOPE),
            Triple(STORED_SCOPE, "", DEFLATED_SCOPE),
            Triple(STORED_SCOPE, STORED_SCOPE, STORED_SCOPE),
        ).forEach { (storedScope, deflatedScope, requestedScope) ->
            assertNull(
                R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                    baseControls = base,
                    scope = requestedScope,
                    storedContainerScope = storedScope,
                    deflatedExpandedScope = deflatedScope,
                    projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
                    entrySourceUri =
                        R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
                    attestation = valid,
                ),
            )
        }
        listOf(
            0L,
            R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES + 1L,
        ).forEach { inputArchiveBytes ->
            assertNull(
                R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                    baseControls = base,
                    scope = DEFLATED_SCOPE,
                    storedContainerScope = STORED_SCOPE,
                    deflatedExpandedScope = DEFLATED_SCOPE,
                    projectId = R2ExternalCapacityEvidenceProtocol.PROJECT_ID,
                    entrySourceUri =
                        R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
                    attestation = valid.copy(
                        inputArchiveBytes = inputArchiveBytes,
                    ),
                ),
            )
        }
        assertNull(
            R2ExternalCapacityEvidenceProtocol.controlsOrNull(
                baseControls = base,
                scope = STORED_SCOPE,
                storedContainerScope = STORED_SCOPE,
                deflatedExpandedScope = DEFLATED_SCOPE,
                projectId = "wrong-project",
                entrySourceUri =
                    R2ExternalCapacityEvidenceProtocol.ENTRY_SOURCE_URI,
                attestation = valid,
            ),
        )
    }

    @Test
    fun exactTestOnlyPairCanDeriveObservationWithoutExpandedAuthority() {
        val controls =
            R2ExternalCapacityEvidenceProtocol.cleanupEvidenceControlsOrNull(
                baseControls = publicControls(),
                attestation = validAttestation().copy(inputArchiveBytes = null),
            )

        requireNotNull(controls)
        assertEquals(
            R2CompilationControlOrigin.PUBLIC_PREVIEW,
            controls.origin,
        )
        assertTrue(controls.externalCleanupEvidenceEnabled)
        assertTrue(!controls.expandedWorkerCapacity)

        assertNull(
            R2ExternalCapacityEvidenceProtocol
                .cleanupEvidenceControlsOrNull(
                    baseControls = publicControls(),
                    attestation =
                        validAttestation().copy(applicationTestOnly = false),
                ),
        )
    }

    @Test
    fun publicAndEvidenceOriginsCannotGainTheOtherFaultInjectionControls() {
        assertRejected {
            publicControls().copy(expandedWorkerCapacity = true)
        }
        assertRejected {
            publicControls().copy(
                origin =
                    R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE,
                expandedWorkerCapacity = false,
            )
        }
        assertRejected {
            publicControls().copy(
                origin =
                    R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE,
                expandedWorkerCapacity = true,
                terminalCleanupDelayMilliseconds = 1,
            )
        }
    }

    private fun publicControls() = R2CompilationControls(
        inputArchiveSchemaRevision = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        inputArchiveSha256 = SHA,
        requestManifestSha256 = SHA,
        compilerDistributionFingerprint = SHA,
        declarationsFingerprint = SHA,
        declarationProfileFingerprint = SHA,
        runtimeCapabilitiesFingerprint = SHA,
        origin = R2CompilationControlOrigin.PUBLIC_PREVIEW,
    )

    private fun validAttestation() = R2ExternalCapacityEvidenceAttestation(
        evidenceBuildEnabled = true,
        applicationUid = 10_001,
        callingUid = 10_002,
        fixtureUid = 10_002,
        callingPackages =
            setOf(R2ExternalCapacityEvidenceProtocol.FIXTURE_PACKAGE),
        applicationTestOnly = true,
        fixtureTestOnly = true,
        signaturesMatch = true,
        inputArchiveBytes =
            R2_INPUT_ARCHIVE_MAXIMUM_CONTAINER_BYTES,
    )

    private fun assertRejected(block: () -> Unit) {
        var rejected = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected an invalid R2 control origin to fail closed.", rejected)
    }

    private companion object {
        const val SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val STORED_SCOPE = "stored-container-exact-64-v1"
        const val DEFLATED_SCOPE = "deflated-expanded-exact-64-v1"
    }
}
