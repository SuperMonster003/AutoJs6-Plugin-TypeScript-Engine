package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Build-time-scoped authority for the non-shipping external exact-capacity evidence variants.
 *
 * The caller never supplies a generic "expanded capacity" bit. The service derives that authority
 * only when the evidence build, exact fixture identity, signer, project identity, scope, and frozen
 * container-size envelope all agree. The fixture's two-pass ZIP verification and runtime assertions
 * separately bind the requested STORED/DEFLATED exact-64-MiB boundary. Normal debug/release builds
 * compile this policy with the build flag disabled and therefore fail closed.
 */
internal object R2ExternalCapacityEvidenceProtocol {

    const val FIXTURE_PACKAGE =
        "io.github.supermonster003.autojs6.plugin.typescript.publicpreview"
    const val PROJECT_ID = "capacity-project"
    const val ENTRY_SOURCE_URI = "autojs://source/project/main.ts"

    /**
     * Adds no compilation authority. It only lets the coordinator retain bounded cleanup
     * observations for the exact TEST_ONLY evidence pair.
     */
    fun cleanupEvidenceControlsOrNull(
        baseControls: R2CompilationControls?,
        attestation: R2ExternalCapacityEvidenceAttestation,
    ): R2CompilationControls? {
        val controls = baseControls ?: return null
        if (
            controls.origin != R2CompilationControlOrigin.PUBLIC_PREVIEW ||
            !isExactEvidenceCaller(attestation)
        ) {
            return null
        }
        return controls.copy(externalCleanupEvidenceEnabled = true)
    }

    fun isExactEvidenceCaller(
        attestation: R2ExternalCapacityEvidenceAttestation,
    ): Boolean =
        attestation.evidenceBuildEnabled &&
            attestation.applicationUid > 0 &&
            attestation.callingUid > 0 &&
            attestation.callingUid != attestation.applicationUid &&
            attestation.fixtureUid == attestation.callingUid &&
            attestation.callingPackages == setOf(FIXTURE_PACKAGE) &&
            attestation.applicationTestOnly &&
            attestation.fixtureTestOnly &&
            attestation.signaturesMatch

    fun controlsOrNull(
        baseControls: R2CompilationControls?,
        scope: String?,
        storedContainerScope: String,
        deflatedExpandedScope: String,
        projectId: String?,
        entrySourceUri: String?,
        attestation: R2ExternalCapacityEvidenceAttestation,
    ): R2CompilationControls? {
        val controls = cleanupEvidenceControlsOrNull(
            baseControls = baseControls,
            attestation = attestation,
        ) ?: return null
        if (
            projectId != PROJECT_ID ||
            entrySourceUri != ENTRY_SOURCE_URI ||
            !scopeAcceptsInputSize(
                requestedScope = scope,
                storedContainerScope = storedContainerScope,
                deflatedExpandedScope = deflatedExpandedScope,
                inputArchiveBytes = attestation.inputArchiveBytes,
            )
        ) {
            return null
        }
        return controls.copy(
            origin = R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE,
            expandedWorkerCapacity = true,
        )
    }

    private fun scopeAcceptsInputSize(
        requestedScope: String?,
        storedContainerScope: String,
        deflatedExpandedScope: String,
        inputArchiveBytes: Long?,
    ): Boolean {
        if (
            storedContainerScope.isEmpty() ||
            deflatedExpandedScope.isEmpty() ||
            storedContainerScope == deflatedExpandedScope
        ) {
            return false
        }
        return when {
            requestedScope == storedContainerScope ->
                inputArchiveBytes ==
                    TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES
            requestedScope == deflatedExpandedScope ->
                inputArchiveBytes != null &&
                    inputArchiveBytes in
                    1L..TypeScriptCompilerContract.MAX_INPUT_ARCHIVE_BYTES
            else -> false
        }
    }
}

internal data class R2ExternalCapacityEvidenceAttestation(
    val evidenceBuildEnabled: Boolean,
    val applicationUid: Int,
    val callingUid: Int,
    val fixtureUid: Int?,
    val callingPackages: Set<String>,
    val applicationTestOnly: Boolean,
    val fixtureTestOnly: Boolean,
    val signaturesMatch: Boolean,
    val inputArchiveBytes: Long?,
)
