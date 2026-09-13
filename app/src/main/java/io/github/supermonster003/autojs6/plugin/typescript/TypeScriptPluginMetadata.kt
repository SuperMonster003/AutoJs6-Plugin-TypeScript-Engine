package io.github.supermonster003.autojs6.plugin.typescript

import android.content.Context
import android.os.Bundle
import java.util.zip.ZipFile
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
    internal const val ADVERTISED_CONFIG_SCHEMA_REVISION =
        TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION
    internal const val ADVERTISED_CONFIG_KIND = TypeScriptR2Contract.CONFIG_KIND_TSCONFIG
    internal const val ADVERTISED_CONFIG_POLICY_REVISION =
        TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION
    internal val ADVERTISED_CONFIG_POLICY_FINGERPRINT
        get() = R2ControlledTsConfigPolicy.revision4Fingerprint
    internal val ADVERTISED_RHINO_SUPPORTED_EXTENSIONS
        get() = arrayOf(".ts", ".tsx")
    internal val ADVERTISED_SUPPORTED_EXTENSIONS
        get() = arrayOf(".ts", ".tsx", ".mts", ".cts")
    internal val ADVERTISED_NODE_PROFILE_AUTHORITY: R2CompilationProfileAuthority
        get() = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2

    @Suppress("DEPRECATION")
    private fun installedPackage(context: Context) = context.packageManager.getPackageInfo(context.packageName, 0)

    @Suppress("DEPRECATION")
    private fun installedVersionCode(context: Context): Long {
        val info = installedPackage(context)
        return if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun packagedAbis(context: Context): Array<String> {
        val supported = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        val archives = listOf(context.applicationInfo.sourceDir) + context.applicationInfo.splitSourceDirs.orEmpty()
        val packaged = archives.flatMapTo(linkedSetOf()) { path ->
            ZipFile(path).use { archive ->
                supported.filter { abi -> archive.getEntry("lib/$abi/libautojs6_r2_fd_flags.so") != null }
            }
        }
        check(packaged.isNotEmpty()) { "Installed TypeScript APK has no supported native helper" }
        return supported.filter(packaged::contains).toTypedArray()
    }

    fun pluginInfo(context: Context): PluginInfo = PluginInfo(
        name = context.getString(R.string.app_name),
        description = context.getString(R.string.plugin_description),
        instruction = context.getString(R.string.plugin_instruction),
        author = context.getString(R.string.plugin_author),
        collaborators = null,
        versionName = requireNotNull(installedPackage(context).versionName) { "Installed plugin version is missing" },
        versionCode = installedVersionCode(context),
        versionDate = BuildConfig.VERSION_DATE,
        id = TypeScriptPluginIds.ID,
        engine = TypeScriptPluginIds.ENGINE,
        variant = TypeScriptPluginIds.variant(BuildConfig.TYPESCRIPT_VERSION),
        supportedAbis = packagedAbis(context),
        capabilities = capabilityBundle(bootstrapSnapshot(context)).apply {
            nodeDeclarationProfileFingerprint(context)?.let { fingerprint ->
                putString(
                    TypeScriptR2Contract.KEY_NODE_DECLARATION_PROFILE_FINGERPRINT,
                    fingerprint,
                )
            }
        },
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
            ADVERTISED_CONFIG_SCHEMA_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_KIND,
            ADVERTISED_CONFIG_KIND,
        )
        putInt(
            TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
            ADVERTISED_CONFIG_POLICY_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
            ADVERTISED_CONFIG_POLICY_FINGERPRINT,
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
            TypeScriptR2Contract.KEY_PROJECT_SESSION_REVISION,
            TypeScriptR2Contract.PROJECT_SESSION_REVISION,
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
            ADVERTISED_CONFIG_SCHEMA_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_KIND,
            ADVERTISED_CONFIG_KIND,
        )
        putInt(
            TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
            ADVERTISED_CONFIG_POLICY_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
            ADVERTISED_CONFIG_POLICY_FINGERPRINT,
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
            ADVERTISED_RHINO_SUPPORTED_EXTENSIONS,
        )
        putStringArray(
            TypeScriptPluginCapabilityKeys.SUPPORTED_EXTENSIONS,
            ADVERTISED_SUPPORTED_EXTENSIONS,
        )
        putStringArray(
            TypeScriptPluginCapabilityKeys.TARGET_PROFILES,
            TypeScriptCompilerContract.TARGET_PROFILES,
        )
        putNodeProfileAuthority()
    }

    private fun Bundle.putNodeProfileAuthority() {
        val authority = ADVERTISED_NODE_PROFILE_AUTHORITY
        putInt(TypeScriptR2Contract.KEY_NODE_PROFILE_REVISION, authority.profileRevision)
        putInt(
            TypeScriptR2Contract.KEY_NODE_CONFIG_SCHEMA_REVISION,
            authority.configSchemaRevision,
        )
        putString(TypeScriptR2Contract.KEY_NODE_CONFIG_KIND, authority.configKind)
        putString(
            TypeScriptR2Contract.KEY_NODE_RUNTIME_CAPABILITIES_FINGERPRINT,
            authority.runtimeCapabilitiesFingerprint,
        )
        putInt(
            TypeScriptR2Contract.KEY_PACKAGE_METADATA_SCHEMA_REVISION,
            R2PackageMetadataPolicy.SCHEMA_REVISION,
        )
        putInt(
            TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_REVISION,
            R2PackageMetadataPolicy.POLICY_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_PACKAGE_METADATA_POLICY_FINGERPRINT,
            R2PackageMetadataPolicy.fingerprint,
        )
        putInt(
            TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_SCHEMA_REVISION,
            R2DependencyLayerPolicy.SCHEMA_REVISION,
        )
        putInt(
            TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_REVISION,
            R2DependencyLayerPolicy.POLICY_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_DEPENDENCY_LAYER_POLICY_FINGERPRINT,
            R2DependencyLayerPolicy.fingerprint,
        )
        putInt(
            TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_REVISION,
            R2DependencyResolverPolicy.POLICY_REVISION,
        )
        putString(
            TypeScriptR2Contract.KEY_DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
            R2DependencyResolverPolicy.fingerprint,
        )
    }

    private fun nodeDeclarationProfileFingerprint(context: Context): String? = runCatching {
        R2TrustedDeclarations.loadForProfile(
            context,
            ADVERTISED_NODE_PROFILE_AUTHORITY.profile,
        ).profileFingerprint
    }.getOrNull()

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
