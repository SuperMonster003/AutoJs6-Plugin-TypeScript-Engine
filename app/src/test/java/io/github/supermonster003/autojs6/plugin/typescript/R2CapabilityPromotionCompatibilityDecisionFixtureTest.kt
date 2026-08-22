package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptPluginCapabilityKeys
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CapabilityPromotionCompatibilityDecisionFixtureTest {

    @Test
    fun fixtureFreezesExactSixteenStateDependencyMatrix() {
        val fixture = fixture()
        assertEquals(ROOT_KEYS, fixture.keys.toList())
        assertEquals(
            "autojs6.typescript.r2-m3-d3.capability-promotion-compatibility-decision",
            fixture.string("schema"),
        )
        assertEquals(1, fixture.int("revision"))
        assertEquals("dependency-ordered-independent-promotion", fixture.string("decision"))

        val metadataRevision = fixture.objectValue("metadataRevision")
        assertEquals(METADATA_REVISION_KEYS, metadataRevision.keys.toList())
        assertEquals(
            TypeScriptR2Contract.KEY_CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
            metadataRevision.string("key"),
        )
        assertEquals(
            TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
            metadataRevision.int("value"),
        )
        assertEquals("Int", metadataRevision.string("storedType"))
        assertEquals("fail-closed", metadataRevision.string("unknownOrWrongTypedPolicy"))

        val capabilities = fixture.objectList("capabilities")
        assertEquals(listOf("C", "P", "D", "M"), capabilities.map { it.string("id") })
        assertEquals(
            listOf(
                TypeScriptPluginCapabilityKeys.COMPILER_AVAILABLE,
                TypeScriptPluginCapabilityKeys.PROJECT_COMPILATION,
                TypeScriptPluginCapabilityKeys.SEMANTIC_DIAGNOSTICS,
                TypeScriptPluginCapabilityKeys.SOURCE_MAP_V3,
            ),
            capabilities.map { it.string("key") },
        )
        assertEquals(listOf(1, 2, 4, 8), capabilities.map { it.int("bit") })
        assertEquals(
            listOf(emptyList(), listOf("C"), listOf("C", "P"), listOf("C", "P")),
            capabilities.map { it.stringList("requires") },
        )
        capabilities.forEach { capability ->
            assertEquals(CAPABILITY_KEYS, capability.keys.toList())
        }

        val matrix = fixture.objectList("combinationMatrix")
        assertEquals((0 until 16).toList(), matrix.map { it.int("mask") })
        assertEquals(16, matrix.size)
        val compatibleMasks = matrix.filter { it.boolean("compatible") }.map { it.int("mask") }
        assertEquals(COMPATIBLE_MASKS, compatibleMasks)
        matrix.forEach { state ->
            assertEquals(MATRIX_KEYS, state.keys.toList())
            val mask = state.int("mask")
            val compilerAvailable = state.boolean("C")
            val projectCompilation = state.boolean("P")
            val semanticDiagnostics = state.boolean("D")
            val sourceMapV3 = state.boolean("M")
            assertEquals(mask and 1 != 0, compilerAvailable)
            assertEquals(mask and 2 != 0, projectCompilation)
            assertEquals(mask and 4 != 0, semanticDiagnostics)
            assertEquals(mask and 8 != 0, sourceMapV3)
            assertEquals(
                mask,
                TypeScriptR2Contract.capabilityPromotionMask(
                    compilerAvailable = compilerAvailable,
                    projectCompilation = projectCompilation,
                    semanticDiagnostics = semanticDiagnostics,
                    sourceMapV3 = sourceMapV3,
                ),
            )
            assertEquals(
                state.boolean("compatible"),
                TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                    compatibilityRevision =
                        TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
                    compilerAvailable = compilerAvailable,
                    projectCompilation = projectCompilation,
                    semanticDiagnostics = semanticDiagnostics,
                    sourceMapV3 = sourceMapV3,
                    nodeRuntimeRequired = false,
                ),
            )
        }
    }

    @Test
    fun legacyAllFalseWithoutRevisionRemainsDormantAndCompatible() {
        val allFalseLegacy = compilerMetadata(
            revision = Absent,
            mask = 0,
            nodeRuntimeRequired = Absent,
        )
        assertEquals(Compatibility.LEGACY_DISABLED, evaluateMetadata(allFalseLegacy))
        assertEquals(
            Compatibility.LEGACY_DISABLED,
            evaluateMetadata(allFalseLegacy + (NODE_RUNTIME_KEY to false)),
        )
        assertEquals(
            Compatibility.LEGACY_DISABLED,
            evaluateMetadata(allFalseLegacy + ("boundedFuturePrimitive" to "ignored")),
        )
        assertFalse(routeAllowsLegacy(HostRoute.COMPILER_BIND_PREWARM))
        assertFalse(routeAllowsLegacy(HostRoute.ORDINARY_PROJECT_COMPILATION))
        assertTrue(routeAllowsLegacy(HostRoute.IDENTITY_DISPLAY_DISCOVERY))

        for (mask in 1 until 16) {
            assertEquals(
                Compatibility.INCOMPATIBLE,
                evaluateMetadata(
                    compilerMetadata(
                        revision = Absent,
                        mask = mask,
                        nodeRuntimeRequired = Absent,
                    ),
                ),
            )
        }
        assertEquals(
            Compatibility.INCOMPATIBLE,
            evaluateMetadata(allFalseLegacy + (NODE_RUNTIME_KEY to true)),
        )
        FOUR_COMPILER_KEYS.forEach { key ->
            listOf<Any?>(0, 1, "false", null).forEach { wrongTyped ->
                assertEquals(
                    Compatibility.INCOMPATIBLE,
                    evaluateMetadata(allFalseLegacy + (key to wrongTyped)),
                )
            }
        }
        listOf<Any?>(0, 1, "false", null, true).forEach { invalidNodeDependency ->
            assertEquals(
                Compatibility.INCOMPATIBLE,
                evaluateMetadata(allFalseLegacy + (NODE_RUNTIME_KEY to invalidNodeDependency)),
            )
        }
    }

    @Test
    fun missingWrongTypedOrUnknownRevisionFailsClosedWithoutCapabilityInference() {
        val revisionOneDisabled = compilerMetadata(revision = 1, mask = 0, nodeRuntimeRequired = false)
        assertEquals(Compatibility.REVISION_ONE, evaluateMetadata(revisionOneDisabled))
        assertEquals(
            Compatibility.REVISION_ONE,
            evaluateMetadata(revisionOneDisabled + ("boundedFuturePrimitive" to 17)),
        )

        val revisions = listOf<Any?>(0, -1, 2, 1L, "1", true, null)
        revisions.forEach { revision ->
            assertEquals(
                Compatibility.INCOMPATIBLE,
                evaluateMetadata(
                    compilerMetadata(
                        revision = revision,
                        mask = 0,
                        nodeRuntimeRequired = false,
                    ),
                ),
            )
        }

        KNOWN_BOOLEAN_KEYS.forEach { key ->
            assertEquals(
                Compatibility.INCOMPATIBLE,
                evaluateMetadata(revisionOneDisabled - key),
            )
            listOf(0, 1, "false", null).forEach { wrongTyped ->
                assertEquals(
                    Compatibility.INCOMPATIBLE,
                    evaluateMetadata(revisionOneDisabled + (key to wrongTyped)),
                )
            }
        }

        FOUR_COMPILER_KEYS.forEach { key ->
            assertEquals(
                Compatibility.INCOMPATIBLE,
                evaluateMetadata(compilerMetadata(Absent, 0, Absent) - key),
            )
        }
        assertEquals(
            Compatibility.INCOMPATIBLE,
            evaluateMetadata(compilerMetadata(Absent, 0, "false")),
        )

        val rules = fixture().objectValue("metadataTypeRules")
        assertEquals(METADATA_TYPE_RULE_KEYS, rules.keys.toList())
        assertEquals(KNOWN_BOOLEAN_KEYS, rules.stringList("revisionOneKnownBooleanKeys"))
        assertEquals("Boolean", rules.string("revisionOneKnownBooleanStoredType"))
        assertEquals("fail-closed", rules.string("missingKnownBooleanPolicy"))
        assertEquals("fail-closed", rules.string("wrongTypedKnownBooleanPolicy"))
        assertEquals(
            "ignore-without-authority",
            rules.string("boundedUnknownPrimitiveKeyPolicy"),
        )
        assertEquals(
            "four-compiler-capabilities-explicit-false-node-absent-or-explicit-false",
            rules.string("legacyRevisionAbsentException"),
        )
        assertFalse(rules.boolean("legacyOperationalRouteAllowed"))
    }

    @Test
    fun hostRoutesRequireExactCapabilityClosure() {
        val fixtureRoutes = fixture().objectList("hostRoutes")
        assertEquals(HostRoute.entries.map { it.fixtureId }, fixtureRoutes.map { it.string("id") })
        fixtureRoutes.zip(HostRoute.entries).forEach { (fixtureRoute, route) ->
            assertEquals(HOST_ROUTE_KEYS, fixtureRoute.keys.toList())
            assertEquals(route.requiredCapabilities, fixtureRoute.stringList("requiredCapabilities"))
            val expectedMasks = (0 until 16).filter { mask -> routeAllows(mask, route) }
            assertEquals(expectedMasks, fixtureRoute.intList("compatibleRevisionOneMasks"))
        }

        assertEquals(COMPATIBLE_MASKS, routeMasks(HostRoute.IDENTITY_DISPLAY_DISCOVERY))
        assertEquals(listOf(1, 3, 7, 11, 15), routeMasks(HostRoute.COMPILER_BIND_PREWARM))
        assertEquals(listOf(3, 7, 11, 15), routeMasks(HostRoute.ORDINARY_PROJECT_COMPILATION))
        assertEquals(listOf(7, 15), routeMasks(HostRoute.PROJECT_SEMANTIC_DIAGNOSTICS))
        assertEquals(listOf(11, 15), routeMasks(HostRoute.PROJECT_SOURCE_MAP_V3))
        assertEquals(listOf(15), routeMasks(HostRoute.PROJECT_DIAGNOSTICS_AND_SOURCE_MAP_V3))

        assertTrue(routeAllows(7, HostRoute.PROJECT_SEMANTIC_DIAGNOSTICS))
        assertFalse(routeAllows(7, HostRoute.PROJECT_SOURCE_MAP_V3))
        assertTrue(routeAllows(11, HostRoute.PROJECT_SOURCE_MAP_V3))
        assertFalse(routeAllows(11, HostRoute.PROJECT_SEMANTIC_DIAGNOSTICS))
        assertFalse(routeAllows(2, HostRoute.ORDINARY_PROJECT_COMPILATION))
        assertFalse(routeAllows(4, HostRoute.PROJECT_SEMANTIC_DIAGNOSTICS))
        assertFalse(routeAllows(8, HostRoute.PROJECT_SOURCE_MAP_V3))
    }

    @Test
    fun nodeRuntimeDependencyRemainsSeparateAndCannotPromoteCompilerCapabilities() {
        val node = fixture().objectValue("nodeRuntimeDependency")
        assertEquals(NODE_DEPENDENCY_KEYS, node.keys.toList())
        assertEquals(TypeScriptPluginCapabilityKeys.NODE_RUNTIME_REQUIRED, node.string("key"))
        assertFalse(node.boolean("partOfCompilerMask"))
        assertFalse(node.boolean("revisionOneRequiredValue"))
        assertFalse(node.boolean("legacyAbsentValue"))
        assertEquals(
            "fail-closed-new-compatibility-revision-required",
            node.string("truePolicy"),
        )
        assertFalse(node.boolean("canPromoteCompilerCapability"))

        for (mask in 0 until 16) {
            assertFalse(
                TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                    compatibilityRevision = 1,
                    compilerAvailable = mask and 1 != 0,
                    projectCompilation = mask and 2 != 0,
                    semanticDiagnostics = mask and 4 != 0,
                    sourceMapV3 = mask and 8 != 0,
                    nodeRuntimeRequired = true,
                ),
            )
            assertEquals(
                Compatibility.INCOMPATIBLE,
                evaluateMetadata(compilerMetadata(1, mask, true)),
            )
        }

        val current = fixture().objectValue("currentPromotionState")
        assertEquals(CURRENT_PROMOTION_KEYS, current.keys.toList())
        assertEquals(
            TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
            current.int("metadataRevision"),
        )
        assertEquals(0, current.int("mask"))
        KNOWN_BOOLEAN_KEYS.forEach { key -> assertFalse(current.boolean(key)) }
        listOf(
            "publicCapabilityChanged",
            "runtimeCapabilityChanged",
            "nodeRevisionTwoEndToEndChanged",
            "deviceEvidenceRefreshed",
        ).forEach { key -> assertFalse(current.boolean(key)) }
        assertEquals(
            Compatibility.REVISION_ONE,
            evaluateMetadata(compilerMetadata(1, 0, false)),
        )
    }

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-capability-promotion/d3-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun evaluateMetadata(metadata: Map<String, Any?>): Compatibility {
        if (!metadata.containsKey(REVISION_KEY)) {
            val compilerCapabilitiesAreExplicitlyFalse = FOUR_COMPILER_KEYS.all { key ->
                metadata.containsKey(key) && metadata[key] is Boolean && metadata[key] == false
            }
            val nodeRuntimeIsAbsentOrExplicitlyFalse =
                !metadata.containsKey(NODE_RUNTIME_KEY) ||
                    (metadata[NODE_RUNTIME_KEY] is Boolean && metadata[NODE_RUNTIME_KEY] == false)
            return if (compilerCapabilitiesAreExplicitlyFalse && nodeRuntimeIsAbsentOrExplicitlyFalse) {
                Compatibility.LEGACY_DISABLED
            } else {
                Compatibility.INCOMPATIBLE
            }
        }
        val revision = metadata[REVISION_KEY]
        if (revision !is Int || revision != 1) return Compatibility.INCOMPATIBLE
        if (KNOWN_BOOLEAN_KEYS.any { key -> metadata[key] !is Boolean }) {
            return Compatibility.INCOMPATIBLE
        }
        val nodeRuntimeRequired = metadata.getValue(NODE_RUNTIME_KEY) as Boolean
        val mask = FOUR_COMPILER_KEYS.foldIndexed(0) { index, accumulated, key ->
            accumulated or if (metadata.getValue(key) as Boolean) 1 shl index else 0
        }
        return if (
            TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                compatibilityRevision = revision,
                compilerAvailable = mask and 1 != 0,
                projectCompilation = mask and 2 != 0,
                semanticDiagnostics = mask and 4 != 0,
                sourceMapV3 = mask and 8 != 0,
                nodeRuntimeRequired = nodeRuntimeRequired,
            )
        ) {
            Compatibility.REVISION_ONE
        } else {
            Compatibility.INCOMPATIBLE
        }
    }

    private fun compilerMetadata(
        revision: Any?,
        mask: Int,
        nodeRuntimeRequired: Any?,
    ): Map<String, Any?> = buildMap {
        if (revision !== Absent) put(REVISION_KEY, revision)
        FOUR_COMPILER_KEYS.forEachIndexed { index, key ->
            put(key, mask and (1 shl index) != 0)
        }
        if (nodeRuntimeRequired !== Absent) put(NODE_RUNTIME_KEY, nodeRuntimeRequired)
    }

    private fun routeMasks(route: HostRoute): List<Int> =
        (0 until 16).filter { mask -> routeAllows(mask, route) }

    private fun routeAllows(mask: Int, route: HostRoute): Boolean {
        val compatible = TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
            compatibilityRevision = 1,
            compilerAvailable = mask and 1 != 0,
            projectCompilation = mask and 2 != 0,
            semanticDiagnostics = mask and 4 != 0,
            sourceMapV3 = mask and 8 != 0,
            nodeRuntimeRequired = false,
        )
        return compatible && route.requiredBits.all { requiredBit -> mask and requiredBit != 0 }
    }

    private fun routeAllowsLegacy(route: HostRoute): Boolean =
        route == HostRoute.IDENTITY_DISPLAY_DISCOVERY

    private fun Map<String, Any?>.string(key: String): String = getValue(key) as String
    private fun Map<String, Any?>.int(key: String): Int =
        (getValue(key) as R2StrictJson.NumberToken).source.toInt()
    private fun Map<String, Any?>.boolean(key: String): Boolean = getValue(key) as Boolean

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectList(key: String): List<Map<String, Any?>> =
        (getValue(key) as List<*>).map { value -> value as Map<String, Any?> }

    private fun Map<String, Any?>.stringList(key: String): List<String> =
        (getValue(key) as List<*>).map { value -> value as String }

    private fun Map<String, Any?>.intList(key: String): List<Int> =
        (getValue(key) as List<*>).map { value ->
            (value as R2StrictJson.NumberToken).source.toInt()
        }

    private enum class Compatibility {
        REVISION_ONE,
        LEGACY_DISABLED,
        INCOMPATIBLE,
    }

    private enum class HostRoute(
        val fixtureId: String,
        val requiredCapabilities: List<String>,
        val requiredBits: List<Int>,
    ) {
        IDENTITY_DISPLAY_DISCOVERY("identity-display-discovery", emptyList(), emptyList()),
        COMPILER_BIND_PREWARM("compiler-bind-prewarm", listOf("C"), listOf(1)),
        ORDINARY_PROJECT_COMPILATION(
            "ordinary-project-compilation",
            listOf("C", "P"),
            listOf(1, 2),
        ),
        PROJECT_SEMANTIC_DIAGNOSTICS(
            "project-semantic-diagnostics",
            listOf("C", "P", "D"),
            listOf(1, 2, 4),
        ),
        PROJECT_SOURCE_MAP_V3(
            "project-source-map-v3",
            listOf("C", "P", "M"),
            listOf(1, 2, 8),
        ),
        PROJECT_DIAGNOSTICS_AND_SOURCE_MAP_V3(
            "project-diagnostics-and-source-map-v3",
            listOf("C", "P", "D", "M"),
            listOf(1, 2, 4, 8),
        ),
    }

    private companion object {
        val Absent = Any()
        const val REVISION_KEY = "capabilityPromotionCompatibilityRevision"
        const val NODE_RUNTIME_KEY = "typescriptNodeRuntimeRequired"
        val FOUR_COMPILER_KEYS = listOf(
            "typescriptCompilerAvailable",
            "typescriptProjectCompilation",
            "typescriptSemanticDiagnostics",
            "typescriptSourceMapV3",
        )
        val KNOWN_BOOLEAN_KEYS = FOUR_COMPILER_KEYS + NODE_RUNTIME_KEY
        val COMPATIBLE_MASKS = listOf(0, 1, 3, 7, 11, 15)
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "decision",
            "metadataRevision",
            "capabilities",
            "combinationMatrix",
            "hostRoutes",
            "metadataTypeRules",
            "nodeRuntimeDependency",
            "currentPromotionState",
        )
        val METADATA_REVISION_KEYS = listOf(
            "key",
            "value",
            "storedType",
            "unknownOrWrongTypedPolicy",
        )
        val CAPABILITY_KEYS = listOf("id", "key", "bit", "requires")
        val MATRIX_KEYS = listOf("mask", "C", "P", "D", "M", "compatible")
        val HOST_ROUTE_KEYS = listOf(
            "id",
            "requiredCapabilities",
            "compatibleRevisionOneMasks",
        )
        val METADATA_TYPE_RULE_KEYS = listOf(
            "revisionOneKnownBooleanKeys",
            "revisionOneKnownBooleanStoredType",
            "missingKnownBooleanPolicy",
            "wrongTypedKnownBooleanPolicy",
            "boundedUnknownPrimitiveKeyPolicy",
            "legacyRevisionAbsentException",
            "legacyOperationalRouteAllowed",
        )
        val NODE_DEPENDENCY_KEYS = listOf(
            "key",
            "partOfCompilerMask",
            "revisionOneRequiredValue",
            "legacyAbsentValue",
            "truePolicy",
            "canPromoteCompilerCapability",
        )
        val CURRENT_PROMOTION_KEYS = listOf(
            "metadataRevision",
            "typescriptCompilerAvailable",
            "typescriptProjectCompilation",
            "typescriptSemanticDiagnostics",
            "typescriptSourceMapV3",
            "typescriptNodeRuntimeRequired",
            "mask",
            "publicCapabilityChanged",
            "runtimeCapabilityChanged",
            "nodeRevisionTwoEndToEndChanged",
            "deviceEvidenceRefreshed",
        )
    }
}
