package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CompilerBundleGoldenFixtureTest {

    @Test
    fun fixtureFreezesFiveScenariosAndRebuildsEveryOutputCollectionIdentity() {
        val root = loadFixture()

        assertEquals(ROOT_KEYS, root.keys.toList())
        assertEquals("autojs6.typescript.r2-m2-p3.compiler-bundle", root.string("schema"))
        assertEquals(1, root.int("revision"))
        assertEquals("6.0.3", root.string("typescriptVersion"))
        assertEquals("desktop-compiler-only/no-production-admission", root.string("scope"))

        val execution = root.objectValue("execution")
        assertEquals(EXECUTION_KEYS, execution.keys.toList())
        assertEquals(listOf("declared", "reversed"), execution.stringList("inputOrders"))
        assertEquals(2, execution.int("repetitionsPerOrder"))
        assertEquals(0, execution.int("expectedHostFsCalls"))
        assertEquals(0, execution.int("expectedTsSysCalls"))
        assertEquals(0, execution.int("expectedNetworkCalls"))
        assertEquals(0, execution.int("expectedEmittedJavaScriptExecutions"))
        assertEquals(OUTPUT_COLLECTION_CANONICAL, execution.string("outputCollectionCanonical"))

        val authorities = root.objectValue("authorities")
        assertEquals(listOf("rhino", "node", "mixed"), authorities.keys.toList())
        verifyFrozenAuthority(
            authority = authorities.objectValue("rhino"),
            expectedProfile = "rhino",
            expectedRevision = 1,
            expectedDeclarationLayer = "rhino",
            expectedCanonicalOptions =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1
                    .canonicalEffectiveOptionsJson,
            expectedFingerprint =
                R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1
                    .effectiveOptionsFingerprint,
        )
        verifyFrozenAuthority(
            authority = authorities.objectValue("node"),
            expectedProfile = "node",
            expectedRevision = 1,
            expectedDeclarationLayer = "node",
            expectedCanonicalOptions = R2CompilationProfileRegistry
                .PROVISIONAL_NODE_NEXT_REVISION_1.canonicalEffectiveOptionsJson,
            expectedFingerprint = R2CompilationProfileRegistry
                .PROVISIONAL_NODE_NEXT_REVISION_1.effectiveOptionsFingerprint,
        )
        verifyMixedAuthority(authorities.objectValue("mixed"))

        val cases = root.objectList("cases")
        assertEquals(EXPECTED_CASE_IDS, cases.map { fixtureCase -> fixtureCase.string("id") })
        assertEquals(EXPECTED_AUTHORITIES, cases.map { fixtureCase -> fixtureCase.string("authority") })
        assertEquals(listOf(4, 4, 4, 4, 5), cases.map(::verifyCaseAndReturnOutputCount))

        val mixed = cases.last()
        assertEquals(
            listOf("/sources/main.ts", "/sources/helper.js", "/sources/settings.json"),
            mixed.objectList("files").map { file -> file.string("path") },
        )
        val mixedOutputs = mixed.objectValue("expected").objectList("outputs")
        assertEquals(
            listOf(
                "/outputs/helper.js",
                "/outputs/helper.js.map",
                "/outputs/main.js",
                "/outputs/main.js.map",
                "/outputs/settings.json",
            ),
            mixedOutputs.map { output -> output.string("path") },
        )
        assertNull(mixedOutputs.last()["sourceMap"])
    }

    @Test
    fun mixedOracleStaysOutsideEveryRegisteredProductionSourceKind() {
        val root = loadFixture()
        val mixed = root.objectValue("authorities").objectValue("mixed")
        val options = R2StrictJson.parseObject(
            mixed.string("canonicalOptionsJson").toByteArray(StandardCharsets.UTF_8),
        )

        assertEquals("oracle-local", mixed.string("kind"))
        assertEquals("compiler-only-mixed", mixed.string("targetProfile"))
        assertNull(mixed["profileRevision"])
        assertEquals(false, mixed.boolean("productionAdmission"))
        assertEquals(true, options.boolean("allowJs"))
        assertEquals(false, options.boolean("checkJs"))
        assertEquals(true, options.boolean("resolveJsonModule"))
        assertTrue(
            R2CompilationProfileRegistry.registeredAuthorities.none { authority ->
                authority.canonicalEffectiveOptionsJson == mixed.string("canonicalOptionsJson")
            },
        )

        val productionAuthorities = listOf(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO_REVISION_1,
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2,
        )
        for (authority in productionAuthorities) {
            assertFalse(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    "/sources/helper.js",
                    authority,
                ),
            )
            assertFalse(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    "/sources/settings.json",
                    authority,
                ),
            )
            assertFalse(authority.effectiveOptions.allowJs)
        }
        assertFalse(
            R2ControlledTsConfigExplicitProfileDefaults.PROVISIONAL_RHINO.resolveJsonModule,
        )
    }

    private fun verifyCaseAndReturnOutputCount(fixtureCase: Map<String, Any?>): Int {
        assertEquals(CASE_KEYS, fixtureCase.keys.toList())
        val roots = fixtureCase.stringList("rootNames")
        assertTrue(roots.isNotEmpty())
        assertEquals(roots.sorted(), roots)
        assertEquals(roots.size, roots.distinct().size)
        assertTrue(roots.all { root -> root.startsWith("/sources/") && root.endsWith(".ts") })

        val files = fixtureCase.objectList("files")
        val filePaths = files.map { file ->
            assertEquals(FILE_KEYS, file.keys.toList())
            assertTrue(file.string("text").isNotEmpty())
            file.string("path")
        }
        assertEquals(filePaths.size, filePaths.distinct().size)
        assertTrue(filePaths.all { path -> path.startsWith("/sources/") })

        val expected = fixtureCase.objectValue("expected")
        assertEquals(EXPECTED_KEYS, expected.keys.toList())
        assertTrue(expected.listValue("diagnostics").isEmpty())
        assertFalse(expected.boolean("emitSkipped"))
        val outputs = expected.objectList("outputs")
        assertTrue(outputs.isNotEmpty())
        assertEquals(outputs.map { output -> output.string("path") }.sorted(), outputs.map { output -> output.string("path") })

        val collectionCanonical = buildString {
            outputs.forEach { output ->
                val path = output.string("path")
                val byteLength = output.int("byteLength")
                val sha256 = output.string("sha256")
                assertEquals(
                    if (path.endsWith(".map")) OUTPUT_WITH_SOURCE_MAP_KEYS else OUTPUT_KEYS,
                    output.keys.toList(),
                )
                assertTrue(path.startsWith("/outputs/"))
                assertTrue(byteLength > 0)
                R2Digests.requireLowerSha256(sha256, "P3 output SHA-256")
                val sourcePaths = output.stringList("sourcePaths")
                assertEquals(1, sourcePaths.size)
                assertTrue(sourcePaths.single().startsWith("/sources/"))
                if (path.endsWith(".map")) verifySourceMap(output.objectValue("sourceMap"), path)
                append(path)
                append('\u0000')
                append(byteLength)
                append('\u0000')
                append(sha256)
                append('\n')
            }
        }
        assertEquals(
            expected.string("outputCollectionSha256"),
            R2Digests.sha256(collectionCanonical.toByteArray(StandardCharsets.UTF_8)),
        )
        return outputs.size
    }

    private fun verifySourceMap(sourceMap: Map<String, Any?>, outputPath: String) {
        assertEquals(SOURCE_MAP_KEYS, sourceMap.keys.toList())
        assertEquals(3, sourceMap.int("version"))
        assertEquals(outputPath.substringAfterLast('/').removeSuffix(".map"), sourceMap.string("file"))
        assertEquals("", sourceMap.string("sourceRoot"))
        assertEquals(1, sourceMap.stringList("sources").size)
        assertTrue(sourceMap.stringList("names").isEmpty())
        assertTrue(sourceMap.int("mappingsByteLength") >= 0)
        R2Digests.requireLowerSha256(
            sourceMap.string("mappingsSha256"),
            "P3 Source Map mappings SHA-256",
        )
    }

    private fun verifyFrozenAuthority(
        authority: Map<String, Any?>,
        expectedProfile: String,
        expectedRevision: Int,
        expectedDeclarationLayer: String,
        expectedCanonicalOptions: String,
        expectedFingerprint: String,
    ) {
        assertEquals(AUTHORITY_KEYS, authority.keys.toList())
        assertEquals("frozen-profile", authority.string("kind"))
        assertEquals("createIncrementalProgram", authority.string("compilerApi"))
        assertEquals(expectedProfile, authority.string("targetProfile"))
        assertEquals(expectedRevision, authority.int("profileRevision"))
        assertEquals(expectedDeclarationLayer, authority.string("declarationLayer"))
        assertEquals("unchanged", authority.string("productionAdmission"))
        assertEquals(expectedCanonicalOptions, authority.string("canonicalOptionsJson"))
        assertEquals(expectedFingerprint, authority.string("optionsSha256"))
        assertEquals(
            expectedFingerprint,
            R2Digests.sha256(
                authority.string("canonicalOptionsJson").toByteArray(StandardCharsets.UTF_8),
            ),
        )
    }

    private fun verifyMixedAuthority(authority: Map<String, Any?>) {
        assertEquals(AUTHORITY_KEYS, authority.keys.toList())
        assertEquals("oracle-local", authority.string("kind"))
        assertEquals("createIncrementalProgram", authority.string("compilerApi"))
        assertEquals("compiler-only-mixed", authority.string("targetProfile"))
        assertNull(authority["profileRevision"])
        assertEquals("rhino", authority.string("declarationLayer"))
        assertFalse(authority.boolean("productionAdmission"))
        assertEquals(
            authority.string("optionsSha256"),
            R2Digests.sha256(
                authority.string("canonicalOptionsJson").toByteArray(StandardCharsets.UTF_8),
            ),
        )
    }

    private fun loadFixture(): Map<String, Any?> = R2StrictJson.parseObject(
        requireNotNull(
            javaClass.getResourceAsStream("/r2-compiler-bundle/p3-v1.json"),
        ).use { input -> input.readBytes() },
    )

    private fun Map<String, Any?>.objectValue(name: String): Map<String, Any?> =
        (getValue(name) as? Map<*, *>)
            ?.entries
            ?.associate { (key, value) -> (key as String) to value }
            ?: error("$name must be an object.")

    private fun Map<String, Any?>.objectList(name: String): List<Map<String, Any?>> =
        listValue(name).mapIndexed { index, value ->
            (value as? Map<*, *>)
                ?.entries
                ?.associate { (key, item) -> (key as String) to item }
                ?: error("$name[$index] must be an object.")
        }

    private fun Map<String, Any?>.listValue(name: String): List<Any?> =
        getValue(name) as? List<Any?> ?: error("$name must be an array.")

    private fun Map<String, Any?>.stringList(name: String): List<String> =
        listValue(name).mapIndexed { index, value ->
            value as? String ?: error("$name[$index] must be a string.")
        }

    private fun Map<String, Any?>.string(name: String): String =
        getValue(name) as? String ?: error("$name must be a string.")

    private fun Map<String, Any?>.boolean(name: String): Boolean =
        getValue(name) as? Boolean ?: error("$name must be a boolean.")

    private fun Map<String, Any?>.int(name: String): Int {
        val token = getValue(name) as? R2StrictJson.NumberToken
            ?: error("$name must be a JSON number.")
        return token.source.toInt()
    }

    private companion object {
        val ROOT_KEYS = listOf("schema", "revision", "typescriptVersion", "scope", "execution", "authorities", "cases")
        val EXECUTION_KEYS = listOf(
            "inputOrders",
            "repetitionsPerOrder",
            "expectedHostFsCalls",
            "expectedTsSysCalls",
            "expectedNetworkCalls",
            "expectedEmittedJavaScriptExecutions",
            "outputCollectionCanonical",
        )
        val AUTHORITY_KEYS = listOf(
            "kind",
            "compilerApi",
            "targetProfile",
            "profileRevision",
            "declarationLayer",
            "productionAdmission",
            "canonicalOptionsJson",
            "optionsSha256",
        )
        val CASE_KEYS = listOf("id", "authority", "rootNames", "files", "expected")
        val FILE_KEYS = listOf("path", "text")
        val EXPECTED_KEYS = listOf("diagnostics", "emitSkipped", "outputCollectionSha256", "outputs")
        val OUTPUT_KEYS = listOf("path", "byteLength", "sha256", "sourcePaths")
        val OUTPUT_WITH_SOURCE_MAP_KEYS = OUTPUT_KEYS + "sourceMap"
        val SOURCE_MAP_KEYS = listOf(
            "version",
            "file",
            "sourceRoot",
            "sources",
            "names",
            "mappingsByteLength",
            "mappingsSha256",
        )
        val EXPECTED_CASE_IDS = listOf(
            "rhino-two-file-import",
            "node-two-file-import",
            "rhino-source-cycle",
            "node-source-cycle",
            "mixed-ts-js-json",
        )
        val EXPECTED_AUTHORITIES = listOf("rhino", "node", "rhino", "node", "mixed")
        const val OUTPUT_COLLECTION_CANONICAL =
            "sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)"
    }
}
