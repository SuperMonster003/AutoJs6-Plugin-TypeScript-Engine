package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.Bundle
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptPluginActions
import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptPluginIds

object TypeScriptPluginMetadata {

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
        putInt(PluginCapabilityKeys.REQUIRES_HOST_VERSION, BuildConfig.REQUIRED_HOST_VERSION)
        putString(
            TypeScriptPluginCapabilityKeys.COMPILER_BACKEND,
            snapshot.backend,
        )
        putBoolean(TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE, false)
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
        putInt(
            TypeScriptPluginCapabilityKeys.INPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putBoolean(TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED, false)
        putInt(
            TypeScriptPluginCapabilityKeys.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
            TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION,
        )
        putBoolean(TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION, false)
        putInt(
            TypeScriptPluginCapabilityKeys.PROFILE_SCHEMA_REVISION,
            TypeScriptCompilerContract.PROFILE_SCHEMA_REVISION,
        )
        putInt(
            TypeScriptPluginCapabilityKeys.RUNTIME_CAPABILITY_SCHEMA_REVISION,
            TypeScriptCompilerContract.RUNTIME_CAPABILITY_SCHEMA_REVISION,
        )
        putBoolean(TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS, false)
        putString(
            TypeScriptPluginCapabilityKeys.SERVICE_STATUS,
            snapshot.status,
        )
        putBoolean(TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3, false)
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
                CompilerHostSnapshot(
                    backend = TypeScriptCompilerContract.BACKEND_WEBVIEW_WORKER,
                    status = TypeScriptCompilerContract.STATUS_BOOTSTRAP,
                    compilerVersion = null,
                    distributionFingerprint = metadata.distributionFingerprint,
                    declarationsFingerprint = metadata.declarationsFingerprint,
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
