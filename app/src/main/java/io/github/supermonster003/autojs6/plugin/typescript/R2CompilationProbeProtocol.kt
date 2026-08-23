package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/** Private same-UID controls for fault injection and non-shipping R2 evidence workloads. */
internal object R2CompilationProbeProtocol {

    const val KEY_ENABLED = "__autojs6_r2_debug_compilation"
    const val KEY_INPUT_ARCHIVE_SHA256 =
        "__autojs6_r2_debug_input_archive_sha256"
    const val KEY_REQUEST_MANIFEST_SHA256 =
        "__autojs6_r2_debug_request_manifest_sha256"
    const val KEY_PROFILE_REVISION =
        "__autojs6_r2_debug_profile_revision"
    const val KEY_WORKER_SPIN_MILLISECONDS =
        "__autojs6_r2_debug_worker_spin_milliseconds"
    const val KEY_WORKER_SPIN_PHASE =
        "__autojs6_r2_debug_worker_spin_phase"
    const val KEY_EXPANDED_WORKER_CAPACITY =
        "__autojs6_r2_debug_expanded_worker_capacity"
    const val KEY_TERMINAL_CLEANUP_DELAY_MILLISECONDS =
        "__autojs6_r2_debug_terminal_cleanup_delay_milliseconds"
    const val KEY_STOP_CAUSE_SELECTED =
        "__autojs6_r2_debug_stop_cause_selected"

    /** Any one of these request keys irrevocably selects the private debug control plane. */
    val REQUEST_CONTROL_KEYS: Set<String> = setOf(
        KEY_ENABLED,
        KEY_INPUT_ARCHIVE_SHA256,
        KEY_REQUEST_MANIFEST_SHA256,
        KEY_PROFILE_REVISION,
        KEY_WORKER_SPIN_MILLISECONDS,
        KEY_WORKER_SPIN_PHASE,
        KEY_EXPANDED_WORKER_CAPACITY,
        KEY_TERMINAL_CLEANUP_DELAY_MILLISECONDS,
    )

    const val MAX_WORKER_SPIN_MILLISECONDS = 10_000
    const val MAX_TERMINAL_CLEANUP_DELAY_MILLISECONDS = 4_000

    fun requestsControlPlane(presentKeys: Set<String>): Boolean =
        REQUEST_CONTROL_KEYS.any(presentKeys::contains)

    fun requireEnabled(present: Boolean, value: Any?): Boolean {
        require(present && value is Boolean && value) {
            "The private R2 debug enable flag must be the exact Boolean true."
        }
        return true
    }

    fun requireProfileRevision(
        present: Boolean,
        value: Any?,
        compatibilityRevision: Int,
    ): Int {
        require(compatibilityRevision > 0)
        if (!present) return compatibilityRevision
        require(value is Int) {
            "The private R2 profile revision must be an exact Int."
        }
        return value
    }

    fun formalInputAuthorityError(
        inputArchiveSchemaRevision: Int,
        declarationProfileFingerprint: String,
        expectedDeclarationProfileFingerprint: String?,
    ): String? {
        val fingerprintIsWellFormed = runCatching {
            R2Digests.requireLowerSha256(
                declarationProfileFingerprint,
                "R2 declaration profile fingerprint",
            )
        }.isSuccess
        if (!fingerprintIsWellFormed) {
            return TypeScriptCompilerContract.ERROR_INVALID_REQUEST
        }
        return if (
            inputArchiveSchemaRevision !=
                TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION ||
            expectedDeclarationProfileFingerprint == null ||
            !R2Digests.equal(
                declarationProfileFingerprint,
                expectedDeclarationProfileFingerprint,
            )
        ) {
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH
        } else {
            null
        }
    }
}

internal enum class R2CompilationControlOrigin {
    PUBLIC_PREVIEW,
    DEBUG_PROBE,
    EXTERNAL_CAPACITY_EVIDENCE,
}

/**
 * Trusted control-plane metadata for one R2 admission.
 *
 * Public callers can bind only hashes and revisions. Fault-injection knobs remain available to the
 * same-UID debug probe, and the constructor makes that separation an invariant rather than relying
 * on the Binder parser alone.
 */
internal data class R2CompilationControls(
    val inputArchiveSchemaRevision: Int,
    val inputArchiveSha256: String,
    val requestManifestSha256: String,
    val compilerDistributionFingerprint: String,
    val declarationsFingerprint: String,
    val declarationProfileFingerprint: String,
    val runtimeCapabilitiesFingerprint: String,
    val origin: R2CompilationControlOrigin,
    val profileAuthority: R2CompilationProfileAuthority =
        R2CompilationProfileRegistry.PROVISIONAL_RHINO,
    val rawConfigAuthority: R2RawProjectConfigAuthority? = null,
    val workerSpinMilliseconds: Int = 0,
    val workerSpinPhase: R2ProjectExecutionPhase? = null,
    val expandedWorkerCapacity: Boolean = false,
    val terminalCleanupDelayMilliseconds: Int = 0,
    /**
     * Read-only observation derived by the service from the exact non-shipping fixture identity.
     *
     * This bit never changes archive or Worker authority and is intentionally unavailable to the
     * same-UID debug control plane.
     */
    val externalCleanupEvidenceEnabled: Boolean = false,
) {

    init {
        require(inputArchiveSchemaRevision == TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION) {
            "R2 controls do not carry the supported input archive schema revision."
        }
        R2Digests.requireLowerSha256(
            declarationProfileFingerprint,
            "R2 declaration profile fingerprint",
        )
        require(R2CompilationProfileRegistry.containsExact(profileAuthority)) {
            "R2 controls do not carry an exact registered profile authority."
        }
        require(
            rawConfigAuthority == null ||
                profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        ) {
            "Controlled tsconfig is admitted only for the exact Rhino profile authority."
        }
        require(
            rawConfigAuthority == null || origin != R2CompilationControlOrigin.DEBUG_PROBE,
        ) {
            "The private debug control plane cannot synthesize public raw-config authority."
        }
        require(
            when (origin) {
                R2CompilationControlOrigin.DEBUG_PROBE ->
                    !externalCleanupEvidenceEnabled
                R2CompilationControlOrigin.PUBLIC_PREVIEW ->
                    workerSpinMilliseconds == 0 &&
                        workerSpinPhase == null &&
                        !expandedWorkerCapacity &&
                        terminalCleanupDelayMilliseconds == 0
                R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE ->
                    workerSpinMilliseconds == 0 &&
                        workerSpinPhase == null &&
                        expandedWorkerCapacity &&
                        terminalCleanupDelayMilliseconds == 0 &&
                        externalCleanupEvidenceEnabled
            },
        ) {
            "R2 control origin cannot enable an unauthorized capacity or fault-injection hook."
        }
        require(
            (workerSpinMilliseconds == 0) == (workerSpinPhase == null),
        ) {
            "Worker spin milliseconds and execution phase must be enabled together."
        }
    }

    fun authority(request: R0CompilationEnvelope): R2ProjectArchiveAuthority {
        require(
            origin == R2CompilationControlOrigin.DEBUG_PROBE ||
                request.timeoutMs >=
                TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS
        ) {
            "External R2 compilation timeout must leave the server acknowledgement budget intact."
        }
        require(request.targetProfile == profileAuthority.profile.contractValue) {
            "R2 request target does not match its selected compiler profile authority."
        }
        R2CompilationProfileRegistry.requireEndToEndAdmitted(profileAuthority)
        require(
            profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO ||
                profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2 ||
                origin == R2CompilationControlOrigin.DEBUG_PROBE,
        ) {
            "Public compilation requires the exact Rhino or NodeNext revision-2 authority."
        }
        require(
            R2Digests.equal(
                runtimeCapabilitiesFingerprint,
                profileAuthority.runtimeCapabilitiesFingerprint,
            ),
        ) {
            "R2 runtime capabilities do not match the selected compiler profile."
        }
        return R2ProjectArchiveAuthority(
            requestId = request.requestId,
            projectId = request.projectId,
            entrySourceUri = request.entrySourceUri,
            profile = profileAuthority.profile,
            profileAuthority = profileAuthority,
            compilationMode = request.compilationMode,
            compilerVersion = BuildConfig.TYPESCRIPT_VERSION,
            compilerDistributionFingerprint = compilerDistributionFingerprint,
            declarationsFingerprint = declarationsFingerprint,
            inputArchiveSchemaRevision = inputArchiveSchemaRevision,
            declarationProfileFingerprint = declarationProfileFingerprint,
            runtimeCapabilitiesFingerprint = runtimeCapabilitiesFingerprint,
            archiveSha256 = inputArchiveSha256,
            requestManifestSha256 = requestManifestSha256,
            compilerOptionsJson = request.compilerOptionsJson,
            rawConfigAuthority = rawConfigAuthority,
        )
    }
}

internal data class R2PublicCompilationMetadata(
    val inputArchiveSchemaRevision: Int?,
    val targetProfile: String?,
    val profileSchemaRevision: Int?,
    val profileRevision: Int?,
    val configSchemaRevision: Int?,
    val configKind: String?,
    val runtimeCapabilitySchemaRevision: Int?,
    val inputArchiveSha256: String?,
    val requestManifestSha256: String?,
    val compilerDistributionFingerprint: String?,
    val declarationsFingerprint: String?,
    val declarationProfileFingerprint: String?,
    val runtimeCapabilitiesFingerprint: String?,
    val configByteLength: Long? = null,
    val configSha256: String? = null,
    val configPolicyRevision: Int? = null,
    val configPolicyFingerprint: String? = null,
)

internal enum class R2PublicCompilationDisposition {
    ACCEPTED_CONFIG_NONE,
    ACCEPTED_CONTROLLED_TSCONFIG,
    INCOMPLETE_OR_MALFORMED,
    UNSUPPORTED_AUTHORITY,
}

internal data class R2PublicCompilationClassification(
    val disposition: R2PublicCompilationDisposition,
    val controls: R2CompilationControls? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

internal enum class R2CompilationControlPlaneRoute {
    NONE,
    DEBUG,
    PUBLIC,
    CONFLICT,
}

/**
 * Classifies the additive, production-facing R2 request fields without depending on Android
 * Bundle. [controlsOrNull] preserves the nullable API used by the config-none preview, while
 * [classify] gives the Binder service a stable error category for malformed metadata and
 * unsupported authority. Config-none supports the exact public Rhino and Node revision-2
 * authorities; the revision-2 controlled tsconfig identity remains Rhino-only.
 */
internal object R2PublicCompilationProtocol {

    val PUBLIC_METADATA_KEYS: Set<String> = setOf(
        TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
        TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SHA256,
        TypeScriptR2Contract.KEY_REQUEST_MANIFEST_SHA256,
        TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
        TypeScriptR2Contract.KEY_PROFILE_REVISION,
        TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
        TypeScriptR2Contract.KEY_CONFIG_KIND,
        TypeScriptR2Contract.KEY_CONFIG_BYTE_LENGTH,
        TypeScriptR2Contract.KEY_CONFIG_SHA256,
        TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
        TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
    )

    /** Formal authority shared by the public and same-UID debug control planes. */
    val SHARED_FORMAL_AUTHORITY_KEYS: Set<String> = setOf(
        TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
        TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
    )

    /** Metadata that selects only the public control plane and therefore conflicts with debug. */
    val PUBLIC_ONLY_METADATA_KEYS: Set<String> =
        PUBLIC_METADATA_KEYS - SHARED_FORMAL_AUTHORITY_KEYS

    fun hasAnyMetadata(presentKeys: Set<String>): Boolean =
        PUBLIC_METADATA_KEYS.any(presentKeys::contains)

    fun hasPublicOnlyMetadata(presentKeys: Set<String>): Boolean =
        PUBLIC_ONLY_METADATA_KEYS.any(presentKeys::contains)

    fun conflictsWithDebugControlPlane(
        debugControlPlaneAccepted: Boolean,
        presentKeys: Set<String>,
    ): Boolean = debugControlPlaneAccepted && hasPublicOnlyMetadata(presentKeys)

    /**
     * Selects one control plane before request parsing or descriptor ownership transfer.
     * A malformed private request remains DEBUG so its private validation error wins; it must not
     * fall through to the public classifier merely because the private parser rejected it.
     */
    fun controlPlaneRoute(
        debugControlPlaneRequested: Boolean,
        debugControlPlaneAccepted: Boolean,
        presentKeys: Set<String>,
    ): R2CompilationControlPlaneRoute = when {
        debugControlPlaneRequested && conflictsWithDebugControlPlane(
            debugControlPlaneAccepted = debugControlPlaneAccepted,
            presentKeys = presentKeys,
        ) -> R2CompilationControlPlaneRoute.CONFLICT
        debugControlPlaneRequested -> R2CompilationControlPlaneRoute.DEBUG
        hasAnyMetadata(presentKeys) -> R2CompilationControlPlaneRoute.PUBLIC
        else -> R2CompilationControlPlaneRoute.NONE
    }

    fun controlsOrNull(
        metadata: R2PublicCompilationMetadata?,
        expectedDeclarationProfileFingerprint: String?,
    ): R2CompilationControls? =
        classify(metadata, expectedDeclarationProfileFingerprint).controls

    fun classify(
        metadata: R2PublicCompilationMetadata?,
        expectedDeclarationProfileFingerprint: String?,
    ): R2PublicCompilationClassification {
        metadata ?: return malformed()

        val inputArchiveSchemaRevision =
            metadata.inputArchiveSchemaRevision ?: return malformed()
        val targetProfile = metadata.targetProfile ?: return malformed()
        val profileSchemaRevision = metadata.profileSchemaRevision ?: return malformed()
        val profileRevision = metadata.profileRevision ?: return malformed()
        val configSchemaRevision = metadata.configSchemaRevision ?: return malformed()
        val configKind = metadata.configKind ?: return malformed()
        val runtimeCapabilitySchemaRevision =
            metadata.runtimeCapabilitySchemaRevision ?: return malformed()
        val inputArchiveSha256 = metadata.inputArchiveSha256 ?: return malformed()
        val requestManifestSha256 = metadata.requestManifestSha256 ?: return malformed()
        val compilerDistributionFingerprint =
            metadata.compilerDistributionFingerprint ?: return malformed()
        val declarationsFingerprint = metadata.declarationsFingerprint ?: return malformed()
        val declarationProfileFingerprint =
            metadata.declarationProfileFingerprint ?: return malformed()
        val runtimeCapabilitiesFingerprint =
            metadata.runtimeCapabilitiesFingerprint ?: return malformed()

        if (targetProfile.isEmpty() || configKind.isEmpty()) return malformed()
        val configFieldPresence = listOf(
            metadata.configByteLength != null,
            metadata.configSha256 != null,
            metadata.configPolicyRevision != null,
            metadata.configPolicyFingerprint != null,
        )
        val hasAnyConfigIdentity = configFieldPresence.any { present -> present }
        val hasCompleteConfigIdentity = configFieldPresence.all { present -> present }
        if (hasAnyConfigIdentity && !hasCompleteConfigIdentity) return malformed()

        val baseDigestsValid = runCatching {
            R2Digests.requireLowerSha256(
                inputArchiveSha256,
                "R2 input archive SHA-256",
            )
            R2Digests.requireLowerSha256(
                requestManifestSha256,
                "R2 request manifest SHA-256",
            )
            R2Digests.requireLowerSha256(
                compilerDistributionFingerprint,
                "R2 compiler distribution fingerprint",
            )
            R2Digests.requireLowerSha256(
                declarationsFingerprint,
                "R2 declarations fingerprint",
            )
            R2Digests.requireLowerSha256(
                declarationProfileFingerprint,
                "R2 declaration profile fingerprint",
            )
            R2Digests.requireLowerSha256(
                runtimeCapabilitiesFingerprint,
                "R2 runtime capabilities fingerprint",
            )
        }.isSuccess
        if (!baseDigestsValid) return malformed()

        if (hasCompleteConfigIdentity) {
            val configByteLength = requireNotNull(metadata.configByteLength)
            if (
                configByteLength !in
                0L..TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong()
            ) {
                return malformed()
            }
            val configIdentityValid = runCatching {
                R2Digests.requireLowerSha256(
                    requireNotNull(metadata.configSha256),
                    "R2 config SHA-256",
                )
                R2Digests.requireLowerSha256(
                    requireNotNull(metadata.configPolicyFingerprint),
                    "R2 config policy fingerprint",
                )
            }.isSuccess
            if (!configIdentityValid) return malformed()
        }

        val profileAuthority = runCatching {
            R2CompilationProfileRegistry.requireExactContractValue(
                contractValue = targetProfile,
                profileSchemaRevision = profileSchemaRevision,
                profileRevision = profileRevision,
            )
        }.getOrElse {
            return unsupportedAuthority()
        }
        val publicProfileAdmitted =
            profileAuthority == R2CompilationProfileRegistry.PROVISIONAL_RHINO ||
                profileAuthority ==
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        if (
            inputArchiveSchemaRevision !=
                TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION ||
            !publicProfileAdmitted ||
            runtimeCapabilitySchemaRevision !=
                TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION ||
            !R2Digests.equal(
                runtimeCapabilitiesFingerprint,
                profileAuthority.runtimeCapabilitiesFingerprint,
            ) ||
            expectedDeclarationProfileFingerprint == null ||
            !R2Digests.equal(
                declarationProfileFingerprint,
                expectedDeclarationProfileFingerprint,
            )
        ) {
            return unsupportedAuthority()
        }

        return when (configKind) {
            TypeScriptR2Contract.CONFIG_KIND_NONE -> {
                if (hasAnyConfigIdentity) return malformed()
                if (configSchemaRevision != profileAuthority.configSchemaRevision) {
                    return unsupportedAuthority()
                }
                R2PublicCompilationClassification(
                    disposition = R2PublicCompilationDisposition.ACCEPTED_CONFIG_NONE,
                    controls = R2CompilationControls(
                        inputArchiveSchemaRevision = inputArchiveSchemaRevision,
                        inputArchiveSha256 = inputArchiveSha256,
                        requestManifestSha256 = requestManifestSha256,
                        compilerDistributionFingerprint =
                            compilerDistributionFingerprint,
                        declarationsFingerprint = declarationsFingerprint,
                        declarationProfileFingerprint =
                            declarationProfileFingerprint,
                        runtimeCapabilitiesFingerprint =
                            runtimeCapabilitiesFingerprint,
                        origin = R2CompilationControlOrigin.PUBLIC_PREVIEW,
                        profileAuthority = profileAuthority,
                    ),
                )
            }

            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG -> {
                if (!hasCompleteConfigIdentity) return malformed()
                if (
                    profileAuthority != R2CompilationProfileRegistry.PROVISIONAL_RHINO ||
                    configSchemaRevision !=
                    TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION ||
                    metadata.configPolicyRevision !=
                    TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION ||
                    !R2Digests.equal(
                        requireNotNull(metadata.configPolicyFingerprint),
                        R2ControlledTsConfigPolicy.fingerprint,
                    )
                ) {
                    return unsupportedAuthority()
                }
                R2PublicCompilationClassification(
                    disposition =
                        R2PublicCompilationDisposition.ACCEPTED_CONTROLLED_TSCONFIG,
                    controls = R2CompilationControls(
                        inputArchiveSchemaRevision = inputArchiveSchemaRevision,
                        inputArchiveSha256 = inputArchiveSha256,
                        requestManifestSha256 = requestManifestSha256,
                        compilerDistributionFingerprint =
                            compilerDistributionFingerprint,
                        declarationsFingerprint = declarationsFingerprint,
                        declarationProfileFingerprint =
                            declarationProfileFingerprint,
                        runtimeCapabilitiesFingerprint =
                            runtimeCapabilitiesFingerprint,
                        origin = R2CompilationControlOrigin.PUBLIC_PREVIEW,
                        profileAuthority = profileAuthority,
                        rawConfigAuthority = R2RawProjectConfigAuthority(
                            size = requireNotNull(metadata.configByteLength),
                            sha256 = requireNotNull(metadata.configSha256),
                            policyRevision =
                                requireNotNull(metadata.configPolicyRevision),
                            policyFingerprint =
                                requireNotNull(metadata.configPolicyFingerprint),
                        ),
                    ),
                )
            }

            else -> unsupportedAuthority()
        }
    }

    private fun malformed() = R2PublicCompilationClassification(
        disposition = R2PublicCompilationDisposition.INCOMPLETE_OR_MALFORMED,
        errorCode = TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
        errorMessage = "Public R2 metadata is incomplete or malformed.",
    )

    private fun unsupportedAuthority() = R2PublicCompilationClassification(
        disposition = R2PublicCompilationDisposition.UNSUPPORTED_AUTHORITY,
        errorCode = TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
        errorMessage =
            "Public R2 revision, config kind, or policy authority is not supported.",
    )
}

/**
 * Local authority for the still-provisional Rhino runtime/profile surface.
 *
 * The canonical input is deliberately explicit so the same fingerprint can be written into both
 * request and output manifests without trusting caller-provided capability bytes.
 */
internal object R2RuntimeCapabilityMetadata {

    internal val canonicalInput: String
        get() =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO
                .runtimeCapabilitiesCanonicalInput

    val fingerprint: String
        get() =
            R2CompilationProfileRegistry.PROVISIONAL_RHINO
                .runtimeCapabilitiesFingerprint
}
