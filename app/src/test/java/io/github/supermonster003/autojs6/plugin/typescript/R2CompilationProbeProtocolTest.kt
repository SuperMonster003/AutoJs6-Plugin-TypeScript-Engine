package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CompilationProbeProtocolTest {

    @Test
    fun everyPrivateRequestKeyLocksTheDebugControlPlane() {
        assertFalse(R2CompilationProbeProtocol.requestsControlPlane(emptySet()))
        R2CompilationProbeProtocol.REQUEST_CONTROL_KEYS.forEach { key ->
            assertTrue(
                "Private request key did not select the debug control plane: $key",
                R2CompilationProbeProtocol.requestsControlPlane(setOf(key)),
            )
        }
        assertFalse(
            R2CompilationProbeProtocol.REQUEST_CONTROL_KEYS.contains(
                R2CompilationProbeProtocol.KEY_STOP_CAUSE_SELECTED,
            ),
        )
    }

    @Test
    fun debugEnableAndRevisionDistinguishAbsenceFromMalformedValues() {
        assertTrue(R2CompilationProbeProtocol.requireEnabled(true, true))
        assertEquals(
            1,
            R2CompilationProbeProtocol.requireProfileRevision(
                present = false,
                value = null,
                compatibilityRevision = 1,
            ),
        )
        assertEquals(
            2,
            R2CompilationProbeProtocol.requireProfileRevision(
                present = true,
                value = 2,
                compatibilityRevision = 1,
            ),
        )

        listOf(false, "true", 1, null).forEach { value ->
            assertTrue(
                runCatching {
                    R2CompilationProbeProtocol.requireEnabled(true, value)
                }.exceptionOrNull() is IllegalArgumentException,
            )
        }
        assertTrue(
            runCatching {
                R2CompilationProbeProtocol.requireEnabled(false, null)
            }.exceptionOrNull() is IllegalArgumentException,
        )
        listOf(2L, "2", false, null).forEach { value ->
            assertTrue(
                runCatching {
                    R2CompilationProbeProtocol.requireProfileRevision(
                        present = true,
                        value = value,
                        compatibilityRevision = 1,
                    )
                }.exceptionOrNull() is IllegalArgumentException,
            )
        }
    }

    @Test
    fun debugNodeProfileBindsItsOwnExactDeclarationAuthority() {
        assertNull(
            R2CompilationProbeProtocol.formalInputAuthorityError(
                inputArchiveSchemaRevision =
                    TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
                declarationProfileFingerprint = SHA_F,
                expectedDeclarationProfileFingerprint = SHA_F,
            ),
        )
    }

    @Test
    fun debugNodeProfileRejectsCrossProfileDeclarationAuthority() {
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            R2CompilationProbeProtocol.formalInputAuthorityError(
                inputArchiveSchemaRevision =
                    TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
                declarationProfileFingerprint = SHA_E,
                expectedDeclarationProfileFingerprint = SHA_F,
            ),
        )
    }

    @Test
    fun publicMetadataDetectionCoversEveryAdditiveAuthorityKey() {
        assertEquals(
            setOf(
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
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_SCHEMA_REVISION,
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_REVISION,
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_FINGERPRINT,
                TypeScriptR2Contract.KEY_PACKAGE_METADATA_INVENTORY_FINGERPRINT,
            ),
            R2PublicCompilationProtocol.PUBLIC_METADATA_KEYS,
        )
    }

    @Test
    fun sharedFormalAuthorityDoesNotConflictWithThePrivateDebugControlPlane() {
        val sharedFormalAuthority = setOf(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
        )
        assertEquals(
            sharedFormalAuthority,
            R2PublicCompilationProtocol.SHARED_FORMAL_AUTHORITY_KEYS,
        )
        assertTrue(R2PublicCompilationProtocol.hasAnyMetadata(sharedFormalAuthority))
        assertFalse(R2PublicCompilationProtocol.hasPublicOnlyMetadata(sharedFormalAuthority))
        assertTrue(
            R2CompilationProbeProtocol.requestsControlPlane(
                sharedFormalAuthority + R2CompilationProbeProtocol.KEY_ENABLED,
            ),
        )
        assertFalse(
            R2PublicCompilationProtocol.conflictsWithDebugControlPlane(
                debugControlPlaneAccepted = true,
                presentKeys = sharedFormalAuthority + R2CompilationProbeProtocol.KEY_ENABLED,
            ),
        )

        assertEquals(
            R2PublicCompilationProtocol.PUBLIC_METADATA_KEYS - sharedFormalAuthority,
            R2PublicCompilationProtocol.PUBLIC_ONLY_METADATA_KEYS,
        )
        R2PublicCompilationProtocol.PUBLIC_ONLY_METADATA_KEYS.forEach { key ->
            assertTrue(R2PublicCompilationProtocol.hasPublicOnlyMetadata(setOf(key)))
            assertTrue(
                R2PublicCompilationProtocol.conflictsWithDebugControlPlane(
                    debugControlPlaneAccepted = true,
                    presentKeys = sharedFormalAuthority + key,
                ),
            )
            assertFalse(
                R2PublicCompilationProtocol.conflictsWithDebugControlPlane(
                    debugControlPlaneAccepted = false,
                    presentKeys = setOf(key),
                ),
            )
        }
    }

    @Test
    fun controlPlaneRouteSeparatesSharedFormalPublicAndMalformedDebugRequests() {
        val sharedFormalAuthority =
            R2PublicCompilationProtocol.SHARED_FORMAL_AUTHORITY_KEYS
        val debugKeys =
            sharedFormalAuthority + R2CompilationProbeProtocol.KEY_ENABLED

        assertEquals(
            R2CompilationControlPlaneRoute.NONE,
            R2PublicCompilationProtocol.controlPlaneRoute(
                debugControlPlaneRequested = false,
                debugControlPlaneAccepted = false,
                presentKeys = emptySet(),
            ),
        )
        assertEquals(
            R2CompilationControlPlaneRoute.PUBLIC,
            R2PublicCompilationProtocol.controlPlaneRoute(
                debugControlPlaneRequested = false,
                debugControlPlaneAccepted = false,
                presentKeys = sharedFormalAuthority,
            ),
        )
        assertEquals(
            R2CompilationControlPlaneRoute.DEBUG,
            R2PublicCompilationProtocol.controlPlaneRoute(
                debugControlPlaneRequested = true,
                debugControlPlaneAccepted = true,
                presentKeys = debugKeys,
            ),
        )
        R2PublicCompilationProtocol.PUBLIC_ONLY_METADATA_KEYS.forEach { publicOnlyKey ->
            val mixedKeys = debugKeys + publicOnlyKey
            assertEquals(
                R2CompilationControlPlaneRoute.CONFLICT,
                R2PublicCompilationProtocol.controlPlaneRoute(
                    debugControlPlaneRequested = true,
                    debugControlPlaneAccepted = true,
                    presentKeys = mixedKeys,
                ),
            )
            assertEquals(
                R2CompilationControlPlaneRoute.DEBUG,
                R2PublicCompilationProtocol.controlPlaneRoute(
                    debugControlPlaneRequested = true,
                    debugControlPlaneAccepted = false,
                    presentKeys = mixedKeys,
                ),
            )
            assertEquals(
                R2CompilationControlPlaneRoute.PUBLIC,
                R2PublicCompilationProtocol.controlPlaneRoute(
                    debugControlPlaneRequested = false,
                    debugControlPlaneAccepted = false,
                    presentKeys = setOf(publicOnlyKey),
                ),
            )
        }
    }

    @Test
    fun authorityBindsTheOuterRhinoEnvelope() {
        val request = validRequest()

        val authority = validControls().authority(request)

        assertEquals(request.requestId, authority.requestId)
        assertEquals(request.projectId, authority.projectId)
        assertEquals(request.entrySourceUri, authority.entrySourceUri)
        assertEquals(R2ProjectProfile.PROVISIONAL_RHINO, authority.profile)
        assertEquals(request.compilationMode, authority.compilationMode)
        assertEquals(request.compilerOptionsJson, authority.compilerOptionsJson)
        assertEquals(
            R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON,
            authority.compilerOptionsJson,
        )
    }

    @Test
    fun debugNodeProfileBindsItsOwnExactRuntimeAuthority() {
        val nodeAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT
        val authority = validControls().copy(
            runtimeCapabilitiesFingerprint = nodeAuthority.runtimeCapabilitiesFingerprint,
            profileAuthority = nodeAuthority,
        ).authority(
            validRequest().copy(targetProfile = TypeScriptCompilerContract.PROFILE_NODE),
        )

        assertEquals(R2ProjectProfile.PROVISIONAL_NODE_NEXT, authority.profile)
        assertEquals(nodeAuthority, authority.profileAuthority)
    }

    @Test(expected = IllegalArgumentException::class)
    fun debugNodeProfileRejectsRhinoRuntimeFingerprint() {
        validControls().authority(
            validRequest().copy(targetProfile = TypeScriptCompilerContract.PROFILE_NODE),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun publicPreviewRejectsLegacyNodeRevisionOne() {
        validControls().copy(
            runtimeCapabilitiesFingerprint =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT
                    .runtimeCapabilitiesFingerprint,
            origin = R2CompilationControlOrigin.PUBLIC_PREVIEW,
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT,
        ).authority(
            validRequest().copy(targetProfile = TypeScriptCompilerContract.PROFILE_NODE),
        )
    }

    @Test
    fun publicNodeRevisionTwoCanEnterTheEndToEndPipeline() {
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        val authority = validControls().copy(
            runtimeCapabilitiesFingerprint = revisionTwo.runtimeCapabilitiesFingerprint,
            origin = R2CompilationControlOrigin.PUBLIC_PREVIEW,
            profileAuthority = revisionTwo,
        ).authority(
            validRequest().copy(targetProfile = TypeScriptCompilerContract.PROFILE_NODE),
        )

        assertEquals(R2ProjectProfile.PROVISIONAL_NODE_NEXT, authority.profile)
        assertEquals(revisionTwo, authority.profileAuthority)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nodeRevisionOneRejectsRevisionTwoRuntimeFingerprint() {
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        validControls().copy(
            runtimeCapabilitiesFingerprint = revisionTwo.runtimeCapabilitiesFingerprint,
            profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
        ).authority(
            validRequest().copy(targetProfile = TypeScriptCompilerContract.PROFILE_NODE),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun callerCompilerOptionsCannotOverrideTheProvisionalProfile() {
        validControls().authority(
            validRequest().copy(compilerOptionsJson = """{"strict":false}"""),
        )
    }

    @Test
    fun runtimeFingerprintCoversTheCompleteFixedWorkerPolicy() {
        val expected =
                "autojs6.typescript.runtime-capabilities.provisional|" +
                "runtimeCapabilitySchemaRevision=1|profileSchemaRevision=1|" +
                "profileRevision=2|configSchemaRevision=1|configKind=none|" +
                "profile=rhino|extensions=.ts,.tsx|" +
                "defaultLibrary=/declarations/lib.es2018.d.ts|" +
                "compilerApi=createIncrementalProgram|allowJs=false|checkJs=false|" +
                "ignoreDeprecations=6.0|incremental=false|inlineSourceMap=false|" +
                "inlineSources=false|jsx=React|jsxFactory=__autojs6Tsx|" +
                "jsxFragmentFactory=__autojs6TsxFragment|module=CommonJS|" +
                "moduleResolution=Node10|" +
                "newLine=LF|noEmitOnError=true|noLib=false|outDir=/outputs|" +
                "rootDir=/sources|skipLibCheck=false|sourceMap=true|strict=true|" +
                "target=ES2018|types=[]"

        assertEquals(expected, R2RuntimeCapabilityMetadata.canonicalInput)
        assertEquals(
            R2Digests.sha256(expected.toByteArray(StandardCharsets.US_ASCII)),
            R2RuntimeCapabilityMetadata.fingerprint,
        )
    }

    @Test
    fun completePublicMetadataCreatesControlsWithoutDebugHooks() {
        val metadata = validPublicMetadata()
        val classification = classifyPublic(metadata)
        val controls = publicControlsOrNull(metadata)

        requireNotNull(controls)
        assertEquals(
            R2PublicCompilationDisposition.ACCEPTED_CONFIG_NONE,
            classification.disposition,
        )
        assertEquals(controls, classification.controls)
        assertNull(classification.errorCode)
        assertEquals(R2CompilationControlOrigin.PUBLIC_PREVIEW, controls.origin)
        assertEquals(0, controls.workerSpinMilliseconds)
        assertNull(controls.workerSpinPhase)
        assertEquals(false, controls.expandedWorkerCapacity)
        assertEquals(0, controls.terminalCleanupDelayMilliseconds)
        assertNull(controls.rawConfigAuthority)
        assertEquals(SHA_A, controls.inputArchiveSha256)
        assertEquals(SHA_B, controls.requestManifestSha256)
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.runtimeCapabilitiesFingerprint,
            controls.runtimeCapabilitiesFingerprint,
        )
    }

    @Test
    fun completeNodeRevisionTwoMetadataCreatesPublicConfigNoneControls() {
        val metadata = validNodePublicMetadata()
        val classification = classifyPublic(metadata)
        val controls = requireNotNull(publicControlsOrNull(metadata))

        assertEquals(
            R2PublicCompilationDisposition.ACCEPTED_CONFIG_NONE,
            classification.disposition,
        )
        assertEquals(controls, classification.controls)
        assertNull(classification.errorCode)
        assertEquals(R2CompilationControlOrigin.PUBLIC_PREVIEW, controls.origin)
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            controls.profileAuthority,
        )
        assertNull(controls.rawConfigAuthority)
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
                .runtimeCapabilitiesFingerprint,
            controls.runtimeCapabilitiesFingerprint,
        )
    }

    @Test
    fun completeNodePackageMetadataCreatesBoundPublicAuthority() {
        val metadata = validNodePackageMetadata()
        val classification = classifyPublic(metadata)
        val controls = requireNotNull(classification.controls)
        val packageAuthority = requireNotNull(controls.packageMetadataAuthority)

        assertEquals(
            R2PublicCompilationDisposition.ACCEPTED_CONFIG_NONE,
            classification.disposition,
        )
        assertEquals(SHA_F, packageAuthority.fingerprint)
        assertEquals(R2PackageMetadataPolicy.POLICY_REVISION, packageAuthority.policyRevision)
        assertEquals(R2PackageMetadataPolicy.fingerprint, packageAuthority.policyFingerprint)
    }

    @Test
    fun packageMetadataAuthorityIsAllOrNothingAndNodeRevisionTwoOnly() {
        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            classifyPublic(
                validNodePublicMetadata().copy(
                    packageMetadataSchemaRevision = R2PackageMetadataPolicy.SCHEMA_REVISION,
                ),
            ).errorCode,
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            classifyPublic(
                validPublicMetadata().copy(
                    packageMetadataSchemaRevision = R2PackageMetadataPolicy.SCHEMA_REVISION,
                    packageMetadataPolicyRevision = R2PackageMetadataPolicy.POLICY_REVISION,
                    packageMetadataPolicyFingerprint = R2PackageMetadataPolicy.fingerprint,
                    packageMetadataInventoryFingerprint = SHA_F,
                ),
            ).errorCode,
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            classifyPublic(
                validNodePackageMetadata().copy(
                    packageMetadataPolicyRevision = R2PackageMetadataPolicy.POLICY_REVISION + 1,
                ),
            ).errorCode,
        )
    }

    @Test
    fun missingOrMismatchedPublicRevisionDoesNotClassifyAsR2() {
        assertNull(publicControlsOrNull(null))
        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            classifyPublic(null).errorCode,
        )
        assertNull(
            publicControlsOrNull(
                validPublicMetadata().copy(inputArchiveSchemaRevision = -1),
            ),
        )
        assertNull(
            publicControlsOrNull(
                validPublicMetadata().copy(declarationProfileFingerprint = SHA_F),
            ),
        )
        assertNull(
            publicControlsOrNull(
                validPublicMetadata().copy(profileRevision = -1),
            ),
        )
        assertNull(
            publicControlsOrNull(
                validPublicMetadata().copy(
                    runtimeCapabilitiesFingerprint = SHA_A,
                ),
            ),
        )
        assertNull(
            publicControlsOrNull(
                validPublicMetadata().copy(configSchemaRevision = -1),
            ),
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            classifyPublic(
                validPublicMetadata().copy(inputArchiveSchemaRevision = -1),
            ).errorCode,
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            classifyPublic(
                validPublicMetadata().copy(declarationProfileFingerprint = SHA_F),
            ).errorCode,
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            classifyPublic(
                validPublicMetadata().copy(profileRevision = -1),
            ).errorCode,
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
            classifyPublic(
                validPublicMetadata().copy(
                    runtimeCapabilitiesFingerprint = SHA_A,
                ),
            ).errorCode,
        )
    }

    @Test
    fun completeControlledTsConfigMetadataCreatesExactPublicRawConfigControls() {
        requireNotNull(publicControlsOrNull(validPublicMetadata()))
        val metadata = validControlledTsConfigMetadata()
        val classification = classifyPublic(metadata)
        val controls = requireNotNull(publicControlsOrNull(metadata))

        assertEquals(
            R2PublicCompilationDisposition.ACCEPTED_CONTROLLED_TSCONFIG,
            classification.disposition,
        )
        assertEquals(controls, classification.controls)
        assertNull(classification.errorCode)
        assertEquals(R2CompilationControlOrigin.PUBLIC_PREVIEW, controls.origin)
        assertEquals(
            R2RawProjectConfigAuthority(
                size = requireNotNull(metadata.configByteLength),
                sha256 = requireNotNull(metadata.configSha256),
            ),
            controls.rawConfigAuthority,
        )
        assertEquals(
            controls.rawConfigAuthority,
            controls.authority(validRequest()).rawConfigAuthority,
        )
    }

    @Test
    fun incompleteOrMalformedConfigIdentityIsAnInvalidRequest() {
        val incomplete = validControlledTsConfigMetadata().copy(configSha256 = null)
        val oversized = validControlledTsConfigMetadata().copy(
            configByteLength =
                TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES.toLong() + 1L,
        )
        val malformedDigest = validControlledTsConfigMetadata().copy(
            configSha256 = "A".repeat(64),
        )
        val malformedDeclarationProfile = validPublicMetadata().copy(
            declarationProfileFingerprint = "A".repeat(64),
        )

        listOf(
            incomplete,
            oversized,
            malformedDigest,
            malformedDeclarationProfile,
        ).forEach { metadata ->
            val classification = classifyPublic(metadata)
            assertEquals(
                R2PublicCompilationDisposition.INCOMPLETE_OR_MALFORMED,
                classification.disposition,
            )
            assertEquals(
                TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
                classification.errorCode,
            )
            assertNull(publicControlsOrNull(metadata))
        }
    }

    @Test
    fun unsupportedConfigRevisionKindAndPolicyAreContractMismatches() {
        val unsupported = listOf(
            validControlledTsConfigMetadata().copy(configSchemaRevision = 3),
            validPublicMetadata().copy(configKind = "future-config"),
            validNodePublicMetadata().copy(
                configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
                configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
                configByteLength = 48L,
                configSha256 = SHA_A,
                configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
                configPolicyFingerprint = R2ControlledTsConfigPolicy.fingerprint,
            ),
            validControlledTsConfigMetadata().copy(
                configPolicyRevision =
                    TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION + 1,
            ),
            validControlledTsConfigMetadata().copy(configPolicyFingerprint = SHA_A),
        )

        unsupported.forEach { metadata ->
            val classification = classifyPublic(metadata)
            assertEquals(
                R2PublicCompilationDisposition.UNSUPPORTED_AUTHORITY,
                classification.disposition,
            )
            assertEquals(
                TypeScriptCompilerContract.ERROR_CONTRACT_MISMATCH,
                classification.errorCode,
            )
            assertNull(publicControlsOrNull(metadata))
        }
    }

    @Test
    fun configNoneRejectsStrayRawConfigIdentityAsMalformed() {
        val metadata = validPublicMetadata().copy(
            configByteLength = 0L,
            configSha256 = SHA_A,
            configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
            configPolicyFingerprint = R2ControlledTsConfigPolicy.fingerprint,
        )

        val classification = classifyPublic(metadata)

        assertEquals(
            R2PublicCompilationDisposition.INCOMPLETE_OR_MALFORMED,
            classification.disposition,
        )
        assertEquals(
            TypeScriptCompilerContract.ERROR_INVALID_REQUEST,
            classification.errorCode,
        )
        assertNull(publicControlsOrNull(metadata))
    }

    @Test(expected = IllegalArgumentException::class)
    fun publicControlsCannotCarryDebugFaultHooks() {
        R2CompilationControls(
            inputArchiveSchemaRevision = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
            inputArchiveSha256 = SHA_A,
            requestManifestSha256 = SHA_B,
            compilerDistributionFingerprint = SHA_C,
            declarationsFingerprint = SHA_D,
            declarationProfileFingerprint = SHA_E,
            runtimeCapabilitiesFingerprint = R2RuntimeCapabilityMetadata.fingerprint,
            origin = R2CompilationControlOrigin.PUBLIC_PREVIEW,
            workerSpinMilliseconds = 1,
            workerSpinPhase = R2ProjectExecutionPhase.PARSE,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun publicControlsCannotEnableExpandedWorkerCapacity() {
        R2CompilationControls(
            inputArchiveSchemaRevision = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
            inputArchiveSha256 = SHA_A,
            requestManifestSha256 = SHA_B,
            compilerDistributionFingerprint = SHA_C,
            declarationsFingerprint = SHA_D,
            declarationProfileFingerprint = SHA_E,
            runtimeCapabilitiesFingerprint = R2RuntimeCapabilityMetadata.fingerprint,
            origin = R2CompilationControlOrigin.PUBLIC_PREVIEW,
            expandedWorkerCapacity = true,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun workerSpinRequiresAnExactExecutionPhase() {
        R2CompilationControls(
            inputArchiveSchemaRevision = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
            inputArchiveSha256 = SHA_A,
            requestManifestSha256 = SHA_B,
            compilerDistributionFingerprint = SHA_C,
            declarationsFingerprint = SHA_D,
            declarationProfileFingerprint = SHA_E,
            runtimeCapabilitiesFingerprint = R2RuntimeCapabilityMetadata.fingerprint,
            origin = R2CompilationControlOrigin.DEBUG_PROBE,
            workerSpinMilliseconds = 1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun publicTimeoutCannotConsumeThePostReturnAcknowledgementBudget() {
        val controls = requireNotNull(
            publicControlsOrNull(validPublicMetadata()),
        )

        controls.authority(
            validRequest().copy(
                timeoutMs =
                    TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS - 1L,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun externalEvidenceTimeoutCannotConsumeThePostReturnAcknowledgementBudget() {
        val controls = requireNotNull(
            publicControlsOrNull(validPublicMetadata()),
        ).copy(
            origin = R2CompilationControlOrigin.EXTERNAL_CAPACITY_EVIDENCE,
            expandedWorkerCapacity = true,
            externalCleanupEvidenceEnabled = true,
        )

        controls.authority(
            validRequest().copy(
                timeoutMs =
                    TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS - 1L,
            ),
        )
    }

    @Test
    fun debugProbeKeepsSupportingShortDeadlineLifecycleTests() {
        validControls().authority(validRequest().copy(timeoutMs = 1L))
    }

    private fun validControls() = R2CompilationControls(
        inputArchiveSchemaRevision = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        inputArchiveSha256 = SHA_A,
        requestManifestSha256 = SHA_B,
        compilerDistributionFingerprint = SHA_C,
        declarationsFingerprint = SHA_D,
        declarationProfileFingerprint = SHA_E,
        runtimeCapabilitiesFingerprint = R2RuntimeCapabilityMetadata.fingerprint,
        origin = R2CompilationControlOrigin.DEBUG_PROBE,
        workerSpinMilliseconds = 0,
        terminalCleanupDelayMilliseconds = 0,
    )

    private fun classifyPublic(
        metadata: R2PublicCompilationMetadata?,
    ): R2PublicCompilationClassification = R2PublicCompilationProtocol.classify(
        metadata = metadata,
        expectedDeclarationProfileFingerprint = SHA_E,
    )

    private fun publicControlsOrNull(
        metadata: R2PublicCompilationMetadata?,
    ): R2CompilationControls? = R2PublicCompilationProtocol.controlsOrNull(
        metadata = metadata,
        expectedDeclarationProfileFingerprint = SHA_E,
    )

    private fun validPublicMetadata() = R2PublicCompilationMetadata(
        inputArchiveSchemaRevision = TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
        profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        profileRevision = R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        configSchemaRevision = R2CompilationProfileRegistry.CONFIG_SCHEMA_REVISION,
        configKind = R2CompilationProfileRegistry.CONFIG_KIND_NONE,
        runtimeCapabilitySchemaRevision =
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        inputArchiveSha256 = SHA_A,
        requestManifestSha256 = SHA_B,
        compilerDistributionFingerprint = SHA_C,
        declarationsFingerprint = SHA_D,
        declarationProfileFingerprint = SHA_E,
        runtimeCapabilitiesFingerprint = R2RuntimeCapabilityMetadata.fingerprint,
    )

    private fun validNodePublicMetadata(): R2PublicCompilationMetadata {
        val authority = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        return validPublicMetadata().copy(
            targetProfile = TypeScriptCompilerContract.PROFILE_NODE,
            profileRevision = authority.profileRevision,
            configSchemaRevision = authority.configSchemaRevision,
            configKind = authority.configKind,
            runtimeCapabilitiesFingerprint = authority.runtimeCapabilitiesFingerprint,
        )
    }

    private fun validNodePackageMetadata() = validNodePublicMetadata().copy(
        packageMetadataSchemaRevision = R2PackageMetadataPolicy.SCHEMA_REVISION,
        packageMetadataPolicyRevision = R2PackageMetadataPolicy.POLICY_REVISION,
        packageMetadataPolicyFingerprint = R2PackageMetadataPolicy.fingerprint,
        packageMetadataInventoryFingerprint = SHA_F,
    )

    private fun validControlledTsConfigMetadata() = validPublicMetadata().copy(
        configSchemaRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
        configKind = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
        configByteLength = 48L,
        configSha256 = SHA_A,
        configPolicyRevision = TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
        configPolicyFingerprint = R2ControlledTsConfigPolicy.fingerprint,
    )

    private fun validRequest() = R0CompilationEnvelope(
        contractVersion = TypeScriptCompilerContract.VERSION,
        contractHash = TypeScriptCompilerContract.HASH,
        requestId = "request",
        projectId = "project",
        entrySourceUri = "autojs://source/project/main.ts",
        targetProfile = TypeScriptCompilerContract.PROFILE_RHINO,
        compilationMode = TypeScriptCompilerContract.MODE_DEVELOPMENT,
        compilerOptionsJson = R2CompilerEscapePolicy.INLINE_COMPILER_OPTIONS_JSON,
        inputArchiveTransportVersion =
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        outputArchiveTransportVersion =
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        timeoutMs = TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
    )

    private companion object {
        val SHA_A = "a".repeat(64)
        val SHA_B = "b".repeat(64)
        val SHA_C = "c".repeat(64)
        val SHA_D = "d".repeat(64)
        val SHA_E = "e".repeat(64)
        val SHA_F = "f".repeat(64)
    }
}
