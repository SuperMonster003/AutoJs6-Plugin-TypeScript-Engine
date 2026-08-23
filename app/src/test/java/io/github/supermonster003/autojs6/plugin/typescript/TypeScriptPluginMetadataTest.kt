package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptR2Contract
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
