package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptPluginMetadataTest {

    @Test
    fun publicCompilerAdvertisementUsesCompatibleMaskFifteen() {
        assertTrue(TypeScriptPluginMetadata.ADVERTISED_COMPILER_AVAILABLE)
        assertTrue(TypeScriptPluginMetadata.ADVERTISED_PROJECT_COMPILATION)
        assertTrue(TypeScriptPluginMetadata.ADVERTISED_SEMANTIC_DIAGNOSTICS)
        assertTrue(TypeScriptPluginMetadata.ADVERTISED_SOURCE_MAP_V3)
        assertFalse(TypeScriptPluginMetadata.ADVERTISED_NODE_RUNTIME_REQUIRED)
        assertEquals(
            TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION,
            TypeScriptPluginMetadata.ADVERTISED_CONFIG_SCHEMA_REVISION,
        )
        assertEquals(
            TypeScriptR2Contract.CONFIG_KIND_TSCONFIG,
            TypeScriptPluginMetadata.ADVERTISED_CONFIG_KIND,
        )
        assertEquals(
            TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION,
            TypeScriptPluginMetadata.ADVERTISED_CONFIG_POLICY_REVISION,
        )
        assertEquals(
            R2ControlledTsConfigPolicy.revision4Fingerprint,
            TypeScriptPluginMetadata.ADVERTISED_CONFIG_POLICY_FINGERPRINT,
        )
        assertEquals(
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
            TypeScriptPluginMetadata.ADVERTISED_NODE_PROFILE_AUTHORITY,
        )
        assertEquals(
            TypeScriptR2Contract.DEPENDENCY_LAYER_SCHEMA_REVISION,
            R2DependencyLayerPolicy.SCHEMA_REVISION,
        )
        assertEquals(
            TypeScriptR2Contract.DEPENDENCY_LAYER_POLICY_REVISION,
            R2DependencyLayerPolicy.POLICY_REVISION,
        )
        assertEquals(
            "c159ae179de24307c456c69ae4e456ed9d7dba71dc965c22e6114f632af3834a",
            R2DependencyLayerPolicy.fingerprint,
        )
        assertEquals(
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_REVISION,
            R2DependencyResolverPolicy.POLICY_REVISION,
        )
        assertEquals(
            TypeScriptR2Contract.DEPENDENCY_RESOLVER_POLICY_FINGERPRINT,
            R2DependencyResolverPolicy.fingerprint,
        )
        assertEquals(1, TypeScriptR2Contract.PROJECT_SESSION_REVISION)
        assertArrayEquals(
            arrayOf(".ts"),
            TypeScriptCompilerContract.RHINO_SUPPORTED_EXTENSIONS,
        )
        assertArrayEquals(
            arrayOf(".ts", ".tsx"),
            TypeScriptPluginMetadata.ADVERTISED_RHINO_SUPPORTED_EXTENSIONS,
        )
        assertArrayEquals(
            arrayOf(".ts", ".tsx", ".mts", ".cts"),
            TypeScriptPluginMetadata.ADVERTISED_SUPPORTED_EXTENSIONS,
        )

        assertEquals(
            15,
            TypeScriptR2Contract.capabilityPromotionMask(
                compilerAvailable = TypeScriptPluginMetadata.ADVERTISED_COMPILER_AVAILABLE,
                projectCompilation = TypeScriptPluginMetadata.ADVERTISED_PROJECT_COMPILATION,
                semanticDiagnostics = TypeScriptPluginMetadata.ADVERTISED_SEMANTIC_DIAGNOSTICS,
                sourceMapV3 = TypeScriptPluginMetadata.ADVERTISED_SOURCE_MAP_V3,
            ),
        )
        assertTrue(
            TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                compatibilityRevision =
                    TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
                compilerAvailable = TypeScriptPluginMetadata.ADVERTISED_COMPILER_AVAILABLE,
                projectCompilation = TypeScriptPluginMetadata.ADVERTISED_PROJECT_COMPILATION,
                semanticDiagnostics = TypeScriptPluginMetadata.ADVERTISED_SEMANTIC_DIAGNOSTICS,
                sourceMapV3 = TypeScriptPluginMetadata.ADVERTISED_SOURCE_MAP_V3,
                nodeRuntimeRequired = TypeScriptPluginMetadata.ADVERTISED_NODE_RUNTIME_REQUIRED,
            ),
        )
    }
}
