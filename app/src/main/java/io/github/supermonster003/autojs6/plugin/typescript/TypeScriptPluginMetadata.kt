package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.Bundle
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds
import org.autojs.plugin.typescript.api.TypeScriptR2Contract

object TypeScriptPluginMetadata {

    internal const val ADVERTISED_COMPILER_AVAILABLE = true
    internal const val ADVERTISED_PROJECT_COMPILATION = true
    internal const val ADVERTISED_SEMANTIC_DIAGNOSTICS = true
    internal const val ADVERTISED_SOURCE_MAP_V3 = true
    internal const val ADVERTISED_NODE_RUNTIME_REQUIRED = false

    fun pluginInfo(context: Context): PluginInfo = PluginInfo(
        name = context.getString(R.string.app_name),
        description = context.getString(R.string.plugin_description),
        instruction = context.getString(R.string.plugin_instruction),
        author = context.getString(R.string.plugin_author),
        collaborators = null,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        versionDate = BuildConfig.VERSION_DATE,
        id = TypeScriptPluginIds.ID,
        engine = TypeScriptPluginIds.ENGINE,
        variant = TypeScriptPluginIds.variant(BuildConfig.TYPESCRIPT_VERSION),
        supportedAbis = null,
        capabilities = capabilityBundle(bootstrapSnapshot(context)),
    )

    fun compilerInfo(snapshot: CompilerHostSnapshot): Bundle =
        capabilityBundle(snapshot).apply {
        putInt(TypeScriptCompilerContract.KEY_CONTRACT_VERSION, TypeScriptCompilerContract.VERSION)
        putString(TypeScriptCompilerContract.KEY_CONTRACT_HASH, TypeScriptCompilerContract.HASH)
        putString(TypeScriptCompilerContract.KEY_BACKEND, snapshot.backend)
        putInt(
            TypeScriptR2Contract.KEY_PROFILE_REVISION,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        )
        putInt(
            TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configSchemaRevision,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_KIND,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
        )
        putString(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
            R2RuntimeCapabilityMetadata.fingerprint,
        )
        snapshot.distributionFingerprint?.let { fingerprint ->
            putString(
                TypeScriptCompilerContract.KEY_COMPILER_DISTRIBUTION_FINGERPRINT,
                fingerprint,
            )
        }
        snapshot.declarationsFingerprint?.let { fingerprint ->
            putString(TypeScriptCompilerContract.KEY_DECLARATIONS_FINGERPRINT, fingerprint)
        }
    }

    private fun capabilityBundle(snapshot: CompilerHostSnapshot) = Bundle().apply {
        val compatibilityRevision =
            TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION
        val compilerAvailable = ADVERTISED_COMPILER_AVAILABLE
        val projectCompilation = ADVERTISED_PROJECT_COMPILATION
        val semanticDiagnostics = ADVERTISED_SEMANTIC_DIAGNOSTICS
        val sourceMapV3 = ADVERTISED_SOURCE_MAP_V3
        val nodeRuntimeRequired = ADVERTISED_NODE_RUNTIME_REQUIRED
        check(
            TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                compatibilityRevision = compatibilityRevision,
                compilerAvailable = compilerAvailable,
                projectCompilation = projectCompilation,
                semanticDiagnostics = semanticDiagnostics,
                sourceMapV3 = sourceMapV3,
                nodeRuntimeRequired = nodeRuntimeRequired,
            ),
        ) {
            "Current TypeScript compiler capability advertisement is structurally incompatible."
        }

        putInt(
            TypeScriptR2Contract.KEY_CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
            compatibilityRevision,
        )
        putBoolean(TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE, compilerAvailable)
        putBoolean(TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION, projectCompilation)
        putBoolean(TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS, semanticDiagnostics)
        putBoolean(TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3, sourceMapV3)
        putBoolean(TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED, nodeRuntimeRequired)

        putInt(PluginCapabilityKeys.REQUIRES_HOST_VERSION, BuildConfig.REQUIRED_HOST_VERSION)
        putString(
            TypeScriptPluginCapabilityKeys.COMPILER_BACKEND,
            snapshot.backend,
        )
        putInt(
            TypeScriptPluginCapabilityKeys.COMPILER_CONTRACT_VERSION,
            TypeScriptCompilerContract.VERSION,
        )
        putString(
            TypeScriptPluginCapabilityKeys.COMPILER_CONTRACT_HASH,
            TypeScriptCompilerContract.HASH,
        )
        putString(
            TypeScriptPluginCapabilityKeys.COMPILER_SERVICE_ACTION,
            TypeScriptPluginActions.COMPILER,
        )
        putString(
            TypeScriptPluginCapabilityKeys.COMPILER_VERSION,
            BuildConfig.TYPESCRIPT_VERSION,
        )
        snapshot.distributionFingerprint?.let { fingerprint ->
            putString(
                TypeScriptPluginCapabilityKeys.COMPILER_DISTRIBUTION_FINGERPRINT,
                fingerprint,
            )
        }
        snapshot.declarationsFingerprint?.let { fingerprint ->
            putString(
                TypeScriptPluginCapabilityKeys.DECLARATIONS_FINGERPRINT,
                fingerprint,
            )
        }
        snapshot.declarationProfileFingerprint?.let { fingerprint ->
            putString(
                TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
                fingerprint,
            )
        }
        putInt(
            TypeScriptPluginCapabilityKeys.INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putInt(
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptPluginCapabilityKeys.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putInt(
            TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION,
            TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptPluginCapabilityKeys.PROFILE_SCHEMA_REVISION,
            TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptR2Contract.KEY_PROFILE_REVISION,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.profileRevision,
        )
        putInt(
            TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configSchemaRevision,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_KIND,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO.configKind,
        )
        putInt(
            TypeScriptPluginCapabilityKeys.RUNTIME_CAPABILITY_SCHEMA_REVISION,
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        )
        putString(
            TypeScriptCompilerContract.KEY_RUNTIME_CAPABILITIES_FINGERPRINT,
            R2RuntimeCapabilityMetadata.fingerprint,
        )
        putString(
            TypeScriptPluginCapabilityKeys.SERVICE_STATUS,
            snapshot.status,
        )
        putStringArray(
            TypeScriptPluginCapabilityKeys.NODE_SUPPORTED_EXTENSIONS,
            TypeScriptCompilerContract.NODE_SUPPORTED_EXTENSIONS,
        )
        putStringArray(
            TypeScriptPluginCapabilityKeys.RHINO_SUPPORTED_EXTENSIONS,
            TypeScriptCompilerContract.RHINO_SUPPORTED_EXTENSIONS,
        )
        putStringArray(
            TypeScriptPluginCapabilityKeys.SUPPORTED_EXTENSIONS,
            TypeScriptCompilerContract.SUPPORTED_EXTENSIONS,
        )
        putStringArray(
            TypeScriptPluginCapabilityKeys.TARGET_PROFILES,
            TypeScriptCompilerContract.TARGET_PROFILES,
        )
    }

    private fun bootstrapSnapshot(context: Context): CompilerHostSnapshot =
        runCatching {
            CompilerDistributionMetadata.load(context)
        }.fold(
            onSuccess = { metadata ->
                val declarationProfileFingerprint = runCatching {
                    R2TrustedDeclarations.loadForProfile(
                        context,
                        R2CompilationProfileRegistry.PROVISIONAL_RHINO.profile,
                    ).profileFingerprint
                }.getOrNull()
                CompilerHostSnapshot(
                    backend = TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                    status = TypeScriptCompilerContract.STATUS_BOOTSTRAP,
                    compilerVersion = null,
                    distributionFingerprint = metadata.distributionFingerprint,
                    declarationsFingerprint = metadata.declarationsFingerprint,
                    declarationProfileFingerprint = declarationProfileFingerprint,
                    generatorFingerprint = metadata.generatorFingerprint,
                    lastError = null,
                )
            },
            onFailure = { error ->
                CompilerHostSnapshot.failed(
                    "Compiler distribution metadata is unavailable: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            },
        )
}
