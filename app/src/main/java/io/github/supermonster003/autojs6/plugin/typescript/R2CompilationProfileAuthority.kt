package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

/**
 * Single local authority for one provisional compiler profile and its configuration policy.
 *
 * P2 binds this identity across the public control plane, request manifest, VFS snapshot, framed
 * Worker, native result validation and private output archive.
 */
internal data class R2CompilationProfileAuthority(
    val profile: R2ProjectProfile,
    val profileSchemaRevision: Int,
    val profileRevision: Int,
    val configSchemaRevision: Int,
    val configKind: String,
    val effectiveOptions: R2ProjectEffectiveOptions,
    val canonicalEffectiveOptionsJson: String,
    val effectiveOptionsFingerprint: String,
    val runtimeCapabilitiesCanonicalInput: String,
    val runtimeCapabilitiesFingerprint: String,
) {

    init {
        require(profileSchemaRevision > 0)
        require(profileRevision > 0)
        require(configSchemaRevision > 0)
        require(configKind.isNotEmpty())
        require(canonicalEffectiveOptionsJson.isNotEmpty())
        R2Digests.requireLowerSha256(
            effectiveOptionsFingerprint,
            "Effective compiler options fingerprint",
        )
        require(runtimeCapabilitiesCanonicalInput.isNotEmpty())
        R2Digests.requireLowerSha256(
            runtimeCapabilitiesFingerprint,
            "Runtime capabilities fingerprint",
        )
        require(
            R2Digests.equal(
                effectiveOptionsFingerprint,
                R2Digests.sha256(
                    canonicalEffectiveOptionsJson.toByteArray(StandardCharsets.US_ASCII),
                ),
            ),
        ) {
            "Effective compiler options fingerprint does not match its canonical JSON."
        }
        require(
            R2Digests.equal(
                runtimeCapabilitiesFingerprint,
                R2Digests.sha256(
                    runtimeCapabilitiesCanonicalInput.toByteArray(StandardCharsets.US_ASCII),
                ),
            ),
        ) {
            "Runtime capabilities fingerprint does not match its canonical input."
        }
    }
}

internal object R2CompilationProfileRegistry {

    const val CONFIG_SCHEMA_REVISION = 1
    const val CONFIG_KIND_NONE = TypeScriptR2Contract.CONFIG_KIND_NONE

    private const val PROVISIONAL_RHINO_PROFILE_REVISION =
        R2NodeModuleOutputPolicy.RHINO_PROFILE_REVISION
    private const val PROVISIONAL_NODE_NEXT_REVISION_1_PROFILE_REVISION =
        R2NodeModuleOutputPolicy.NODE_LEGACY_PROFILE_REVISION
    private const val PROVISIONAL_NODE_NEXT_REVISION_2_PROFILE_REVISION =
        R2NodeModuleOutputPolicy.NODE_PROFILE_REVISION

    private const val PROVISIONAL_RHINO_FIXED_OPTIONS_JSON =
        "{\"allowJs\":false,\"checkJs\":false,\"ignoreDeprecations\":\"6.0\"," +
            "\"incremental\":false,\"inlineSourceMap\":false,\"inlineSources\":false," +
            "\"module\":1,\"moduleResolution\":2,\"newLine\":1," +
            "\"noEmitOnError\":true,\"noLib\":false,\"outDir\":\"/outputs\"," +
            "\"rootDir\":\"/sources\",\"skipLibCheck\":false,\"sourceMap\":true," +
            "\"strict\":true,\"target\":5,\"types\":[]}"

    private const val PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON =
        "{\"allowJs\":false,\"checkJs\":false,\"ignoreDeprecations\":\"6.0\"," +
            "\"incremental\":false,\"inlineSourceMap\":false,\"inlineSources\":false," +
            "\"module\":199,\"moduleResolution\":99,\"newLine\":1," +
            "\"noEmitOnError\":true,\"noLib\":false,\"outDir\":\"/outputs\"," +
            "\"rootDir\":\"/sources\",\"skipLibCheck\":false,\"sourceMap\":true," +
            "\"strict\":true,\"target\":5,\"types\":[]}"

    val PROVISIONAL_RHINO: R2CompilationProfileAuthority = run {
        val effectiveOptionsFingerprint = R2Digests.sha256(
            PROVISIONAL_RHINO_FIXED_OPTIONS_JSON.toByteArray(StandardCharsets.US_ASCII),
        )
        val runtimeCapabilitiesCanonicalInput = buildString {
            append("autojs6.typescript.runtime-capabilities.provisional")
            append("|runtimeCapabilitySchemaRevision=")
            append(TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION)
            append("|profileSchemaRevision=")
            append(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
            append("|profileRevision=")
            append(PROVISIONAL_RHINO_PROFILE_REVISION)
            append("|configSchemaRevision=")
            append(CONFIG_SCHEMA_REVISION)
            append("|configKind=")
            append(CONFIG_KIND_NONE)
            append("|profile=")
            append(TypeScriptCompilerContract.PROFILE_RHINO)
            append("|extensions=.ts")
            append("|defaultLibrary=/declarations/lib.es2018.d.ts")
            append("|compilerApi=createIncrementalProgram")
            append("|allowJs=false")
            append("|checkJs=false")
            append("|ignoreDeprecations=6.0")
            append("|incremental=false")
            append("|inlineSourceMap=false")
            append("|inlineSources=false")
            append("|module=CommonJS")
            append("|moduleResolution=Node10")
            append("|newLine=LF")
            append("|noEmitOnError=true")
            append("|noLib=false")
            append("|outDir=/outputs")
            append("|rootDir=/sources")
            append("|skipLibCheck=false")
            append("|sourceMap=true")
            append("|strict=true")
            append("|target=ES2018")
            append("|types=[]")
        }
        R2CompilationProfileAuthority(
            profile = R2ProjectProfile.PROVISIONAL_RHINO,
            profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            profileRevision = PROVISIONAL_RHINO_PROFILE_REVISION,
            configSchemaRevision = CONFIG_SCHEMA_REVISION,
            configKind = CONFIG_KIND_NONE,
            effectiveOptions = R2ProjectEffectiveOptions.PROVISIONAL_RHINO,
            canonicalEffectiveOptionsJson = PROVISIONAL_RHINO_FIXED_OPTIONS_JSON,
            effectiveOptionsFingerprint = effectiveOptionsFingerprint,
            runtimeCapabilitiesCanonicalInput = runtimeCapabilitiesCanonicalInput,
            runtimeCapabilitiesFingerprint = R2Digests.sha256(
                runtimeCapabilitiesCanonicalInput.toByteArray(StandardCharsets.US_ASCII),
            ),
        )
    }

    /**
     * P1 config-none NodeNext authority. P2 deliberately owns .mts/.cts admission and generated
     * URI rules, so revision 1 advertises only the already frozen .ts -> .js source kind.
     */
    val PROVISIONAL_NODE_NEXT_REVISION_1: R2CompilationProfileAuthority = run {
        val effectiveOptionsFingerprint = R2Digests.sha256(
            PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON.toByteArray(StandardCharsets.US_ASCII),
        )
        val runtimeCapabilitiesCanonicalInput = buildString {
            append("autojs6.typescript.runtime-capabilities.provisional")
            append("|runtimeCapabilitySchemaRevision=")
            append(TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION)
            append("|profileSchemaRevision=")
            append(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
            append("|profileRevision=")
            append(PROVISIONAL_NODE_NEXT_REVISION_1_PROFILE_REVISION)
            append("|configSchemaRevision=")
            append(CONFIG_SCHEMA_REVISION)
            append("|configKind=")
            append(CONFIG_KIND_NONE)
            append("|profile=")
            append(TypeScriptCompilerContract.PROFILE_NODE)
            append("|extensions=.ts")
            append("|compilerApi=createIncrementalProgram")
            append("|allowJs=false")
            append("|checkJs=false")
            append("|ignoreDeprecations=6.0")
            append("|incremental=false")
            append("|inlineSourceMap=false")
            append("|inlineSources=false")
            append("|module=NodeNext")
            append("|moduleResolution=NodeNext")
            append("|newLine=LF")
            append("|noEmitOnError=true")
            append("|noLib=false")
            append("|outDir=/outputs")
            append("|rootDir=/sources")
            append("|skipLibCheck=false")
            append("|sourceMap=true")
            append("|strict=true")
            append("|target=ES2018")
            append("|types=[]")
        }
        R2CompilationProfileAuthority(
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            profileRevision = PROVISIONAL_NODE_NEXT_REVISION_1_PROFILE_REVISION,
            configSchemaRevision = CONFIG_SCHEMA_REVISION,
            configKind = CONFIG_KIND_NONE,
            effectiveOptions = R2ProjectEffectiveOptions.PROVISIONAL_NODE_NEXT,
            canonicalEffectiveOptionsJson = PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON,
            effectiveOptionsFingerprint = effectiveOptionsFingerprint,
            runtimeCapabilitiesCanonicalInput = runtimeCapabilitiesCanonicalInput,
            runtimeCapabilitiesFingerprint = R2Digests.sha256(
                runtimeCapabilitiesCanonicalInput.toByteArray(StandardCharsets.US_ASCII),
            ),
        )
    }

    /**
     * Exact NodeNext authority for `.ts/.mts/.cts` ingress, Worker Program/result, and formal
     * private output. S1-5 admits this revision end to end; revision 1 remains the compatibility
     * default for private callers that omit an explicit revision.
     */
    val PROVISIONAL_NODE_NEXT_REVISION_2: R2CompilationProfileAuthority = run {
        val effectiveOptionsFingerprint = R2Digests.sha256(
            PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON.toByteArray(StandardCharsets.US_ASCII),
        )
        val runtimeCapabilitiesCanonicalInput = buildString {
            append("autojs6.typescript.runtime-capabilities.provisional")
            append("|runtimeCapabilitySchemaRevision=")
            append(TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION)
            append("|profileSchemaRevision=")
            append(TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION)
            append("|profileRevision=")
            append(PROVISIONAL_NODE_NEXT_REVISION_2_PROFILE_REVISION)
            append("|configSchemaRevision=")
            append(CONFIG_SCHEMA_REVISION)
            append("|configKind=")
            append(CONFIG_KIND_NONE)
            append("|profile=")
            append(TypeScriptCompilerContract.PROFILE_NODE)
            append("|extensions=")
            append(R2NodeModuleOutputPolicy.sourceExtensions.joinToString(","))
            append("|moduleFormats=")
            append(R2NodeModuleOutputPolicy.moduleFormats.joinToString(","))
            append("|nodeModuleOutputPolicyRevision=")
            append(R2NodeModuleOutputPolicy.POLICY_REVISION)
            append("|nodeModuleOutputPolicyFingerprint=")
            append(R2NodeModuleOutputPolicy.fingerprint)
            append("|resolverPolicyRevision=")
            append(R2NodeModuleOutputPolicy.RESOLVER_POLICY_REVISION)
            append("|compilerApi=createIncrementalProgram")
            append("|allowJs=false")
            append("|checkJs=false")
            append("|ignoreDeprecations=6.0")
            append("|incremental=false")
            append("|inlineSourceMap=false")
            append("|inlineSources=false")
            append("|module=NodeNext")
            append("|moduleResolution=NodeNext")
            append("|newLine=LF")
            append("|noEmitOnError=true")
            append("|noLib=false")
            append("|outDir=/outputs")
            append("|rootDir=/sources")
            append("|skipLibCheck=false")
            append("|sourceMap=true")
            append("|strict=true")
            append("|target=ES2018")
            append("|types=[]")
        }
        R2CompilationProfileAuthority(
            profile = R2ProjectProfile.PROVISIONAL_NODE_NEXT,
            profileSchemaRevision = TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
            profileRevision = PROVISIONAL_NODE_NEXT_REVISION_2_PROFILE_REVISION,
            configSchemaRevision = CONFIG_SCHEMA_REVISION,
            configKind = CONFIG_KIND_NONE,
            effectiveOptions = R2ProjectEffectiveOptions.PROVISIONAL_NODE_NEXT,
            canonicalEffectiveOptionsJson = PROVISIONAL_NODE_NEXT_FIXED_OPTIONS_JSON,
            effectiveOptionsFingerprint = effectiveOptionsFingerprint,
            runtimeCapabilitiesCanonicalInput = runtimeCapabilitiesCanonicalInput,
            runtimeCapabilitiesFingerprint = R2Digests.sha256(
                runtimeCapabilitiesCanonicalInput.toByteArray(StandardCharsets.US_ASCII),
            ),
        )
    }

    /** P1 compatibility alias. It is intentionally frozen to revision 1, never "latest". */
    val PROVISIONAL_NODE_NEXT: R2CompilationProfileAuthority
        get() = PROVISIONAL_NODE_NEXT_REVISION_1

    private data class ExactKey(
        val profile: R2ProjectProfile,
        val profileSchemaRevision: Int,
        val profileRevision: Int,
    )

    val registeredAuthorities: List<R2CompilationProfileAuthority> = listOf(
        PROVISIONAL_RHINO,
        PROVISIONAL_NODE_NEXT_REVISION_1,
        PROVISIONAL_NODE_NEXT_REVISION_2,
    )

    init {
        check(PROVISIONAL_RHINO.profileRevision == R2NodeModuleOutputPolicy.RHINO_PROFILE_REVISION)
        check(
            PROVISIONAL_NODE_NEXT_REVISION_1.profileRevision ==
                R2NodeModuleOutputPolicy.NODE_LEGACY_PROFILE_REVISION,
        )
        check(
            PROVISIONAL_NODE_NEXT_REVISION_2.profileRevision ==
                R2NodeModuleOutputPolicy.NODE_PROFILE_REVISION,
        )
        check(
            "|extensions=${R2NodeModuleOutputPolicy.sourceExtensions.joinToString(",")}" in
                PROVISIONAL_NODE_NEXT_REVISION_2.runtimeCapabilitiesCanonicalInput,
        )
        check(
            "|moduleFormats=${R2NodeModuleOutputPolicy.moduleFormats.joinToString(",")}" in
                PROVISIONAL_NODE_NEXT_REVISION_2.runtimeCapabilitiesCanonicalInput,
        )
    }

    private val exactAuthorities: Map<ExactKey, R2CompilationProfileAuthority> =
        registeredAuthorities.associateBy { authority -> authority.exactKey() }.also { authorities ->
            check(authorities.size == registeredAuthorities.size) {
                "Compilation profile registry contains a duplicate key."
            }
        }

    private val endToEndAdmittedAuthorities: Set<R2CompilationProfileAuthority> = setOf(
        PROVISIONAL_RHINO,
        PROVISIONAL_NODE_NEXT_REVISION_1,
        PROVISIONAL_NODE_NEXT_REVISION_2,
    )

    private val workerResultAdmittedAuthorities: Set<R2CompilationProfileAuthority> =
        endToEndAdmittedAuthorities

    fun require(profile: R2ProjectProfile): R2CompilationProfileAuthority = when (profile) {
        R2ProjectProfile.PROVISIONAL_RHINO -> PROVISIONAL_RHINO
        R2ProjectProfile.PROVISIONAL_NODE_NEXT -> PROVISIONAL_NODE_NEXT_REVISION_1
    }

    fun requireContractValue(contractValue: String): R2CompilationProfileAuthority =
        require(
            requireProfile(contractValue),
        )

    fun requireExact(
        profile: R2ProjectProfile,
        profileSchemaRevision: Int,
        profileRevision: Int,
    ): R2CompilationProfileAuthority =
        exactAuthorities[ExactKey(profile, profileSchemaRevision, profileRevision)]
            ?: throw IllegalArgumentException(
                "Project profile authority is not registered: " +
                    "profile=${profile.contractValue}, schema=$profileSchemaRevision, " +
                    "revision=$profileRevision.",
            )

    fun requireExactContractValue(
        contractValue: String,
        profileSchemaRevision: Int,
        profileRevision: Int,
    ): R2CompilationProfileAuthority = requireExact(
        profile = requireProfile(contractValue),
        profileSchemaRevision = profileSchemaRevision,
        profileRevision = profileRevision,
    )

    fun requireExactRuntimeAuthority(
        contractValue: String,
        profileSchemaRevision: Int,
        profileRevision: Int,
        runtimeCapabilitiesFingerprint: String,
    ): R2CompilationProfileAuthority = requireExactContractValue(
        contractValue = contractValue,
        profileSchemaRevision = profileSchemaRevision,
        profileRevision = profileRevision,
    ).also { authority ->
        require(
            R2Digests.equal(
                runtimeCapabilitiesFingerprint,
                authority.runtimeCapabilitiesFingerprint,
            ),
        ) {
            "Runtime capabilities do not match the exact compiler profile authority."
        }
    }

    fun containsExact(authority: R2CompilationProfileAuthority): Boolean =
        exactAuthorities[authority.exactKey()] == authority

    fun requireEndToEndAdmitted(
        authority: R2CompilationProfileAuthority,
    ): R2CompilationProfileAuthority = authority.also {
        require(containsExact(authority) && authority in endToEndAdmittedAuthorities) {
            "Project profile authority is registered but not admitted end to end."
        }
    }

    fun requireEndToEndAdmittedExactContractValue(
        contractValue: String,
        profileSchemaRevision: Int,
        profileRevision: Int,
    ): R2CompilationProfileAuthority = requireEndToEndAdmitted(
        requireExactContractValue(contractValue, profileSchemaRevision, profileRevision),
    )

    fun requireWorkerResultAdmittedExactContractValue(
        contractValue: String,
        profileSchemaRevision: Int,
        profileRevision: Int,
    ): R2CompilationProfileAuthority = requireExactContractValue(
        contractValue,
        profileSchemaRevision,
        profileRevision,
    ).also { authority ->
        require(authority in workerResultAdmittedAuthorities) {
            "Project profile authority is registered but not admitted for Worker results."
        }
    }

    private fun requireProfile(contractValue: String): R2ProjectProfile =
        R2ProjectProfile.entries.singleOrNull { profile ->
            profile.contractValue == contractValue
        } ?: throw IllegalArgumentException(
            "Project profile is not supported: $contractValue.",
        )

    private fun R2CompilationProfileAuthority.exactKey(): ExactKey = ExactKey(
        profile = profile,
        profileSchemaRevision = profileSchemaRevision,
        profileRevision = profileRevision,
    )
}
