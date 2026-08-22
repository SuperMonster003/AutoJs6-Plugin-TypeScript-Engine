package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.autojs.plugin.typescript.api.TypeScriptR2Contract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CompilerSyntaxMatrixFixtureTest {

    @Test
    fun fixtureFreezesSevenSyntaxKindsAcrossTwoProfilesAndRebuildsSourceAndOutputIdentities() {
        val root = loadFixture()

        assertEquals(ROOT_KEYS, root.keys.toList())
        assertEquals("autojs6.typescript.r2-m2-p4.compiler-syntax", root.string("schema"))
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
        assertEquals(SOURCE_COLLECTION_CANONICAL, execution.string("sourceCollectionCanonical"))
        assertEquals(OUTPUT_COLLECTION_CANONICAL, execution.string("outputCollectionCanonical"))

        val authorities = root.objectValue("authorities")
        assertEquals(listOf("rhino", "node"), authorities.keys.toList())
        verifyFrozenAuthority(
            authority = authorities.objectValue("rhino"),
            expectedProfile = "rhino",
            expectedDeclarationLayer = "rhino",
            productionAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        )
        verifyFrozenAuthority(
            authority = authorities.objectValue("node"),
            expectedProfile = "node",
            expectedDeclarationLayer = "node",
            productionAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
        )

        val syntaxMatrix = root.objectList("syntaxMatrix")
        assertEquals(EXPECTED_SYNTAX_KINDS, syntaxMatrix.map { entry -> entry.string("syntax") })
        syntaxMatrix.forEach { entry ->
            assertEquals(SYNTAX_MATRIX_KEYS, entry.keys.toList())
            val syntax = entry.string("syntax")
            assertEquals(listOf("rhino", "node"), entry.stringList("profiles"))
            assertEquals(
                EXPECTED_SEMANTICS.getValue(syntax).semanticContract,
                entry.string("semanticContract"),
            )
        }

        val cases = root.objectList("cases")
        assertEquals(EXPECTED_CASE_IDS, cases.map { fixtureCase -> fixtureCase.string("id") })
        assertEquals(14, cases.size)
        assertEquals(List(14) { 4 }, cases.map(::verifyCaseAndReturnOutputCount))

        EXPECTED_SYNTAX_KINDS.forEach { syntax ->
            val profileCases = cases.filter { fixtureCase ->
                fixtureCase.string("syntax") == syntax
            }
            assertEquals(listOf("rhino", "node"), profileCases.map { it.string("authority") })
            assertEquals(
                1,
                profileCases.map { it.string("sourceCollectionSha256") }.distinct().size,
            )
            assertEquals(
                1,
                profileCases.map {
                    it.objectValue("expected").string("outputCollectionSha256")
                }.distinct().size,
            )
        }
    }

    @Test
    fun standardDecoratorsKeepExperimentalOptionsClosedAndNodeRevisionTwoWorkerResultOnly() {
        val root = loadFixture()
        val authorities = root.objectValue("authorities")
        val decoratorCases = root.objectList("cases").filter { fixtureCase ->
            fixtureCase.string("syntax") == "decorators"
        }

        assertEquals(
            listOf("rhino-decorators", "node-decorators"),
            decoratorCases.map { fixtureCase -> fixtureCase.string("id") },
        )
        decoratorCases.forEach { fixtureCase ->
            val markers = fixtureCase.objectValue("semanticMarkers")
            assertEquals(
                EXPECTED_SEMANTICS.getValue("decorators").requiredJavaScriptSubstrings,
                markers.stringList("requiredJavaScriptSubstrings"),
            )
            assertEquals(
                EXPECTED_SEMANTICS.getValue("decorators").forbiddenJavaScriptSubstrings,
                markers.stringList("forbiddenJavaScriptSubstrings"),
            )
        }

        listOf("rhino", "node").forEach { profile ->
            val authority = authorities.objectValue(profile)
            assertEquals(STANDARD_DECORATOR_SEMANTICS, authority.string("decoratorSemantics"))
            val options = R2StrictJson.parseObject(
                authority.string("canonicalOptionsJson").toByteArray(StandardCharsets.UTF_8),
            )
            assertFalse("experimentalDecorators" in options)
            assertFalse("emitDecoratorMetadata" in options)
        }

        listOf("experimentalDecorators", "emitDecoratorMetadata").forEach { option ->
            assertFalse(option in R2ControlledTsConfigPolicy.allowedBooleanCompilerOptions)
            assertEquals(
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE,
                R2ControlledTsConfigPolicy.compilerOptionRejectionCode(
                    option,
                    isKnownTypeScriptOption = true,
                ),
            )
        }
        assertFalse(
            R2ControlledTsConfigExplicitProfileDefaults.PROVISIONAL_RHINO
                .useDefineForClassFields,
        )

        val rhino = R2CompilationProfileRegistry.PROVISIONAL_RHINO
        val revisionOne = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val revisionTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        assertEquals(
            listOf(rhino, revisionOne, revisionTwo),
            R2CompilationProfileRegistry.registeredAuthorities,
        )
        assertEquals(
            revisionOne.canonicalEffectiveOptionsJson,
            revisionTwo.canonicalEffectiveOptionsJson,
        )
        assertEquals(
            revisionOne.effectiveOptionsFingerprint,
            revisionTwo.effectiveOptionsFingerprint,
        )
        assertNotEquals(
            revisionOne.runtimeCapabilitiesFingerprint,
            revisionTwo.runtimeCapabilitiesFingerprint,
        )
        assertSame(
            rhino,
            R2CompilationProfileRegistry.requireEndToEndAdmitted(rhino),
        )
        assertSame(
            revisionOne,
            R2CompilationProfileRegistry.requireEndToEndAdmitted(revisionOne),
        )
        assertThrows(IllegalArgumentException::class.java) {
            R2CompilationProfileRegistry.requireEndToEndAdmitted(revisionTwo)
        }
        assertSame(
            revisionTwo,
            R2CompilationProfileRegistry.requireWorkerResultAdmittedExactContractValue(
                revisionTwo.profile.contractValue,
                revisionTwo.profileSchemaRevision,
                revisionTwo.profileRevision,
            ),
        )
    }

    private fun verifyCaseAndReturnOutputCount(fixtureCase: Map<String, Any?>): Int {
        assertEquals(CASE_KEYS, fixtureCase.keys.toList())
        val syntax = fixtureCase.string("syntax")
        val authorityName = fixtureCase.string("authority")
        assertEquals("$authorityName-$syntax", fixtureCase.string("id"))

        val roots = fixtureCase.stringList("rootNames")
        val files = fixtureCase.objectList("files")
        assertEquals(2, roots.size)
        assertEquals(roots.sorted(), roots)
        assertEquals(roots.size, roots.distinct().size)
        assertEquals(2, files.size)
        val filePaths = files.map { file ->
            assertEquals(FILE_KEYS, file.keys.toList())
            assertTrue(file.string("text").isNotEmpty())
            file.string("path")
        }
        assertEquals(filePaths.sorted(), roots)
        assertEquals(filePaths.size, filePaths.distinct().size)

        val productionAuthority = when (authorityName) {
            "rhino" -> R2CompilationProfileRegistry.PROVISIONAL_RHINO
            "node" -> R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
            else -> error("Unexpected P4 authority: $authorityName")
        }
        filePaths.forEach { path ->
            assertTrue(path.startsWith("/sources/"))
            assertTrue(path.endsWith(".ts"))
            assertFalse(path.endsWith(".d.ts"))
            assertFalse(path.endsWith(".mts") || path.endsWith(".cts"))
            assertTrue(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(path, productionAuthority),
            )
        }

        val sourceCanonical = buildString {
            files.sortedBy { file -> file.string("path") }.forEach { file ->
                val path = file.string("path")
                val bytes = file.string("text").toByteArray(StandardCharsets.UTF_8)
                append(path)
                append('\u0000')
                append(bytes.size)
                append('\u0000')
                append(R2Digests.sha256(bytes))
                append('\n')
            }
        }
        val sourceCollectionSha256 = fixtureCase.string("sourceCollectionSha256")
        assertEquals(
            EXPECTED_SOURCE_IDENTITIES.getValue(syntax),
            sourceCollectionSha256,
        )
        assertEquals(
            sourceCollectionSha256,
            R2Digests.sha256(sourceCanonical.toByteArray(StandardCharsets.UTF_8)),
        )

        verifySemanticMarkers(fixtureCase.objectValue("semanticMarkers"), syntax)

        val expected = fixtureCase.objectValue("expected")
        assertEquals(EXPECTED_KEYS, expected.keys.toList())
        assertTrue(expected.listValue("diagnostics").isEmpty())
        assertFalse(expected.boolean("emitSkipped"))
        val outputs = expected.objectList("outputs")
        val outputPaths = outputs.map { output -> output.string("path") }
        val exactOutputPaths = roots.flatMap { sourcePath ->
            val relativeStem = sourcePath.removePrefix("/sources/").removeSuffix(".ts")
            listOf("/outputs/$relativeStem.js", "/outputs/$relativeStem.js.map")
        }.sorted()
        assertEquals(4, outputs.size)
        assertEquals(exactOutputPaths, outputPaths)
        assertEquals(outputPaths.sorted(), outputPaths)

        val outputCanonical = buildString {
            outputs.forEach { output ->
                val path = output.string("path")
                val byteLength = output.int("byteLength")
                val sha256 = output.string("sha256")
                assertEquals(
                    if (path.endsWith(".js.map")) OUTPUT_WITH_SOURCE_MAP_KEYS else OUTPUT_KEYS,
                    output.keys.toList(),
                )
                assertTrue(byteLength > 0)
                R2Digests.requireLowerSha256(sha256, "P4 output SHA-256")
                val relativeStem = path
                    .removePrefix("/outputs/")
                    .removeSuffix(".map")
                    .removeSuffix(".js")
                val expectedSourcePath = "/sources/$relativeStem.ts"
                assertEquals(listOf(expectedSourcePath), output.stringList("sourcePaths"))
                if (path.endsWith(".js.map")) {
                    verifySourceMap(
                        sourceMap = output.objectValue("sourceMap"),
                        outputPath = path,
                        expectedSourcePath = expectedSourcePath,
                    )
                } else {
                    assertTrue(outputs.any { candidate ->
                        candidate.string("path") == "$path.map"
                    })
                }
                append(path)
                append('\u0000')
                append(byteLength)
                append('\u0000')
                append(sha256)
                append('\n')
            }
        }
        val outputCollectionSha256 = expected.string("outputCollectionSha256")
        assertEquals(EXPECTED_OUTPUT_IDENTITIES.getValue(syntax), outputCollectionSha256)
        assertEquals(
            outputCollectionSha256,
            R2Digests.sha256(outputCanonical.toByteArray(StandardCharsets.UTF_8)),
        )
        return outputs.size
    }

    private fun verifySemanticMarkers(markers: Map<String, Any?>, syntax: String) {
        assertEquals(SEMANTIC_MARKER_KEYS, markers.keys.toList())
        val semantics = EXPECTED_SEMANTICS.getValue(syntax)
        assertEquals("/outputs/$syntax.js", markers.string("outputPath"))
        assertEquals(
            semantics.requiredJavaScriptSubstrings,
            markers.stringList("requiredJavaScriptSubstrings"),
        )
        assertEquals(
            semantics.forbiddenJavaScriptSubstrings,
            markers.stringList("forbiddenJavaScriptSubstrings"),
        )
        assertTrue(markers.stringList("requiredJavaScriptSubstrings").all { it.isNotEmpty() })
        assertTrue(markers.stringList("forbiddenJavaScriptSubstrings").all { it.isNotEmpty() })
        assertEquals("/outputs/$syntax.js.map", markers.string("sourceMapPath"))
        assertEquals(listOf("../sources/$syntax.ts"), markers.stringList("sourceMapSources"))
    }

    private fun verifySourceMap(
        sourceMap: Map<String, Any?>,
        outputPath: String,
        expectedSourcePath: String,
    ) {
        assertEquals(SOURCE_MAP_KEYS, sourceMap.keys.toList())
        assertEquals(3, sourceMap.int("version"))
        assertEquals(
            outputPath.substringAfterLast('/').removeSuffix(".map"),
            sourceMap.string("file"),
        )
        assertEquals("", sourceMap.string("sourceRoot"))
        assertEquals(
            listOf("../${expectedSourcePath.removePrefix("/")}"),
            sourceMap.stringList("sources"),
        )
        assertTrue(sourceMap.stringList("names").isEmpty())
        assertTrue(sourceMap.int("mappingsByteLength") > 0)
        R2Digests.requireLowerSha256(
            sourceMap.string("mappingsSha256"),
            "P4 Source Map mappings SHA-256",
        )
    }

    private fun verifyFrozenAuthority(
        authority: Map<String, Any?>,
        expectedProfile: String,
        expectedDeclarationLayer: String,
        productionAuthority: R2CompilationProfileAuthority,
    ) {
        assertEquals(AUTHORITY_KEYS, authority.keys.toList())
        assertEquals("frozen-profile", authority.string("kind"))
        assertEquals("createIncrementalProgram", authority.string("compilerApi"))
        assertEquals(expectedProfile, authority.string("targetProfile"))
        assertEquals(1, authority.int("profileRevision"))
        assertEquals(expectedDeclarationLayer, authority.string("declarationLayer"))
        assertEquals("unchanged", authority.string("productionAdmission"))
        assertEquals(STANDARD_DECORATOR_SEMANTICS, authority.string("decoratorSemantics"))
        assertEquals(
            productionAuthority.canonicalEffectiveOptionsJson,
            authority.string("canonicalOptionsJson"),
        )
        assertEquals(
            productionAuthority.effectiveOptionsFingerprint,
            authority.string("optionsSha256"),
        )
        assertEquals(
            authority.string("optionsSha256"),
            R2Digests.sha256(
                authority.string("canonicalOptionsJson").toByteArray(StandardCharsets.UTF_8),
            ),
        )
    }

    private fun loadFixture(): Map<String, Any?> = R2StrictJson.parseObject(
        requireNotNull(
            javaClass.getResourceAsStream("/r2-compiler-syntax/p4-v1.json"),
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

    private data class SyntaxSemantics(
        val semanticContract: String,
        val requiredJavaScriptSubstrings: List<String>,
        val forbiddenJavaScriptSubstrings: List<String>,
    )

    private companion object {
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "typescriptVersion",
            "scope",
            "execution",
            "authorities",
            "syntaxMatrix",
            "cases",
        )
        val EXECUTION_KEYS = listOf(
            "inputOrders",
            "repetitionsPerOrder",
            "expectedHostFsCalls",
            "expectedTsSysCalls",
            "expectedNetworkCalls",
            "expectedEmittedJavaScriptExecutions",
            "sourceCollectionCanonical",
            "outputCollectionCanonical",
        )
        val AUTHORITY_KEYS = listOf(
            "kind",
            "compilerApi",
            "targetProfile",
            "profileRevision",
            "declarationLayer",
            "productionAdmission",
            "decoratorSemantics",
            "canonicalOptionsJson",
            "optionsSha256",
        )
        val SYNTAX_MATRIX_KEYS = listOf("syntax", "profiles", "semanticContract")
        val CASE_KEYS = listOf(
            "id",
            "syntax",
            "authority",
            "sourceCollectionSha256",
            "rootNames",
            "files",
            "semanticMarkers",
            "expected",
        )
        val FILE_KEYS = listOf("path", "text")
        val SEMANTIC_MARKER_KEYS = listOf(
            "outputPath",
            "requiredJavaScriptSubstrings",
            "forbiddenJavaScriptSubstrings",
            "sourceMapPath",
            "sourceMapSources",
        )
        val EXPECTED_KEYS = listOf(
            "diagnostics",
            "emitSkipped",
            "outputCollectionSha256",
            "outputs",
        )
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
        val EXPECTED_SYNTAX_KINDS = listOf(
            "enum",
            "namespace",
            "class-fields",
            "generics",
            "optional-chaining",
            "async",
            "decorators",
        )
        val EXPECTED_CASE_IDS = EXPECTED_SYNTAX_KINDS.flatMap { syntax ->
            listOf("rhino-$syntax", "node-$syntax")
        }
        val EXPECTED_SOURCE_IDENTITIES = mapOf(
            "enum" to "834ef9ecb9406c5dd5531baa42e0bd727cc7af505ffa0129e98eae4e08b50fb6",
            "namespace" to "f14e0e65a73cb1ef335491cc36e820c0ef9a098778a2c75b81a4f0ebb213d4db",
            "class-fields" to "97cd4af076cce59eff917f56ee39c49b452a8646cb08b9544ae208754fbf0d7c",
            "generics" to "773e9e058751574f76d451199f2db1651870cec61d68ca4305f9c0f27b4789c6",
            "optional-chaining" to
                "37f65f03acb3070449d5370941ef448332e9bdce62d85cdfe478b8030dd03aee",
            "async" to "4f3ce56531f4beae7c381ef5ad3cb7941ca3bc331e6f13f353d9c4f9b48aba0a",
            "decorators" to "5c5000bc2c031a1f1100e219cad2ec9770a1ca88672bf5b2bf9b3103c9a28c60",
        )
        val EXPECTED_OUTPUT_IDENTITIES = mapOf(
            "enum" to "cd7d7d4de87050fac38ce4d29f9140f8f576c0e264d30bfdc583206ea6b7d333",
            "namespace" to "7d07f3d56ee422c267e877ac56023b5f8c08b73a067ced32a4704ea4ef23d272",
            "class-fields" to "22189fad0dfd2774407d1f0a9b41cd171536f92572a49a8488cd236473ac19ee",
            "generics" to "7e9bd699193f58888a3d8f6349d429daf3564d16f976d1fe90bf0fed914c4dbd",
            "optional-chaining" to
                "6e0cb9c70f408fe6d6423f48df11039aa18caebe0ffca9072406566118da413b",
            "async" to "9ebb228a66b627de15f33ea370eeaaf9062bdfbf5f76a213bcd5f6cace70eae2",
            "decorators" to "95970045b84d488a19a5e6421bd43d43600168e29e0e793a485989a113d63711",
        )
        val EXPECTED_SEMANTICS = mapOf(
            "enum" to SyntaxSemantics(
                semanticContract = "numeric-enum-runtime-object",
                requiredJavaScriptSubstrings = listOf(
                    "Direction[Direction[\"Up\"] = 0] = \"Up\";",
                    "Direction[Direction[\"Down\"] = 3] = \"Down\";",
                ),
                forbiddenJavaScriptSubstrings = listOf("enum Direction"),
            ),
            "namespace" to SyntaxSemantics(
                semanticContract = "namespace-runtime-object",
                requiredJavaScriptSubstrings = listOf(
                    "MathBox.base = 40;",
                    "MathBox.add = add;",
                ),
                forbiddenJavaScriptSubstrings = listOf("namespace MathBox"),
            ),
            "class-fields" to SyntaxSemantics(
                semanticContract = "es2018-constructor-field-initialization",
                requiredJavaScriptSubstrings = listOf(
                    "this.count = 1;",
                    "this.label = \"counter\";",
                ),
                forbiddenJavaScriptSubstrings = listOf(
                    "count: number",
                    "readonly label",
                    "Object.defineProperty(this",
                ),
            ),
            "generics" to SyntaxSemantics(
                semanticContract = "type-parameter-erasure",
                requiredJavaScriptSubstrings = listOf(
                    "function identity(value) { return value; }",
                    "exports.result = identity(42);",
                ),
                forbiddenJavaScriptSubstrings = listOf("identity<T>", "identity<number>"),
            ),
            "optional-chaining" to SyntaxSemantics(
                semanticContract = "es2018-nullish-short-circuit-lowering",
                requiredJavaScriptSubstrings = listOf("profile.user", "\"anonymous\""),
                forbiddenJavaScriptSubstrings = listOf("?.", "??"),
            ),
            "async" to SyntaxSemantics(
                semanticContract = "es2018-native-async-await",
                requiredJavaScriptSubstrings = listOf(
                    "async function resolveAnswer(value)",
                    "await Promise.resolve(value * 2)",
                ),
                forbiddenJavaScriptSubstrings = listOf(": Promise<number>", "__awaiter"),
            ),
            "decorators" to SyntaxSemantics(
                semanticContract =
                    "standard-stage-3-transform/no-legacy-experimental-decorators",
                requiredJavaScriptSubstrings = listOf(
                    "var __esDecorate",
                    "var __runInitializers",
                    "kind: \"class\"",
                ),
                forbiddenJavaScriptSubstrings = listOf(
                    "var __decorate",
                    "experimentalDecorators",
                    "__metadata",
                    "Reflect.decorate",
                ),
            ),
        )
        const val STANDARD_DECORATOR_SEMANTICS =
            "standard-stage-3/experimentalDecorators-absent"
        const val SOURCE_COLLECTION_CANONICAL =
            "sorted files; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)"
        const val OUTPUT_COLLECTION_CANONICAL =
            "sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)"
    }
}
