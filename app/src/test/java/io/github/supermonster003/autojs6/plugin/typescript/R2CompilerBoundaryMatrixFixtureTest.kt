package io.github.supermonster003.autojs6.plugin.typescript

import java.text.Normalizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class R2CompilerBoundaryMatrixFixtureTest {

    @Test
    fun fixtureFreezesSevenBoundaryKindsAcrossTwoProfilesAndRebuildsDeterministicIdentities() {
        val root = loadFixture()
        requireValidScalarTree(root, "P5 fixture")

        assertEquals(ROOT_KEYS, root.keys.toList())
        assertEquals("autojs6.typescript.r2-m2-p5.compiler-boundaries", root.string("schema"))
        assertEquals(1, root.int("revision"))
        assertEquals("6.0.3", root.string("typescriptVersion"))
        assertEquals("desktop-compiler-only/no-production-admission", root.string("scope"))

        val execution = root.objectValue("execution")
        verifyExecution(execution)

        val authorities = root.objectValue("authorities")
        assertEquals(listOf("rhino", "node"), authorities.keys.toList())
        verifyFrozenAuthority(
            authority = authorities.objectValue("rhino"),
            expectedProfile = "rhino",
            productionAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        )
        verifyFrozenAuthority(
            authority = authorities.objectValue("node"),
            expectedProfile = "node",
            productionAuthority =
                R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
        )

        val generator = verifyGeneratorAuthority(root.objectValue("generatorAuthority"))
        verifyBoundaryMatrix(root.objectList("boundaryMatrix"))

        val cases = root.objectList("cases")
        assertEquals(EXPECTED_CASE_IDS, cases.map { fixtureCase -> fixtureCase.string("id") })
        assertEquals(14, cases.size)
        val shapes = cases.map { fixtureCase -> verifyCase(fixtureCase, generator) }
        assertEquals(12, shapes.count { shape -> shape.compiled })
        assertEquals(2, shapes.count { shape -> !shape.compiled })
        assertEquals(48, shapes.sumOf { shape -> shape.outputCount })
        assertEquals(
            execution.int("expectedCompiles"),
            shapes.count { shape -> shape.compiled } *
                execution.stringList("inputOrders").size *
                execution.int("repetitionsPerOrder"),
        )
        assertEquals(
            execution.int("expectedCollisionRejections"),
            shapes.count { shape -> !shape.compiled } *
                execution.stringList("inputOrders").size *
                execution.int("repetitionsPerOrder"),
        )
        assertEquals(
            execution.int("expectedTokenOverflowRejections"),
            cases.count { fixtureCase -> fixtureCase.string("boundary") == "high-token" } *
                execution.stringList("inputOrders").size *
                execution.int("repetitionsPerOrder"),
        )

        EXPECTED_BOUNDARIES.forEach { definition ->
            val boundaryCases = cases.filter { fixtureCase ->
                fixtureCase.string("boundary") == definition.boundary
            }
            assertEquals(
                listOf("rhino", "node"),
                boundaryCases.map { fixtureCase -> fixtureCase.string("authority") },
            )
            assertEquals(
                1,
                boundaryCases.map { fixtureCase ->
                    fixtureCase.string("sourceCollectionSha256")
                }.distinct().size,
            )
        }
    }

    @Test
    fun productionPathPolicyKeepsUnicodeNegativeSpacePositiveAndCollisionFailClosed() {
        val root = loadFixture()
        verifyGeneratorAuthority(root.objectValue("generatorAuthority"))

        val exactText = "\ufeffexport const \u4e2d\u6587 = \"\ud83d\ude80\";\r\n"
        requireValidUnicodeScalars(exactText, "P5 production round-trip source")
        val exactBytes = R2Utf8.encode(exactText, "P5 production round-trip source")
        assertArrayEquals(
            byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()),
            exactBytes.copyOfRange(0, 3),
        )
        assertTrue(exactText.endsWith("\r\n"))

        val authorities = listOf(
            R2CompilationProfileRegistry.PROVISIONAL_RHINO,
            R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1,
        )
        authorities.forEach { authority ->
            assertTrue(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    SPACE_PATH,
                    authority,
                ),
            )
            R2ProjectSnapshot.validateProjectPath(SPACE_PATH, authority)
            R2ProjectSnapshot.create(
                files = listOf(
                    R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                    R2ProjectTextFile(SPACE_PATH, exactText),
                ),
                rootNames = listOf(SPACE_PATH),
                profileAuthority = authority,
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                entryPath = SPACE_PATH,
            ).use { snapshot ->
                assertEquals(listOf(SPACE_PATH), snapshot.rootNames)
                assertEquals(SPACE_PATH, snapshot.entryPath)
                assertEquals(exactText, snapshot.readText(SPACE_PATH))
                val metadata = snapshot.fileInventory.single { file -> file.path == SPACE_PATH }
                assertEquals(exactBytes.size.toLong(), metadata.utf8Bytes)
                assertEquals(exactText.length, metadata.utf16CodeUnits)
                assertEquals(R2Digests.sha256(exactBytes), metadata.sha256)
            }

            assertTrue(
                R2ProjectSourceKindAdmission.isExecutableSourcePath(
                    CHINESE_PATH,
                    authority,
                ),
            )
            val unicodePathFailure = assertThrows(IllegalArgumentException::class.java) {
                R2ProjectSnapshot.validateProjectPath(CHINESE_PATH, authority)
            }
            assertTrue(unicodePathFailure.message.orEmpty().contains("printable ASCII"))

            val collisionFailure = assertThrows(IllegalArgumentException::class.java) {
                R2ProjectSnapshot.create(
                    files = listOf(
                        R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                        R2ProjectTextFile(COLLISION_UPPER_PATH, "export const upper = 1;\n"),
                        R2ProjectTextFile(COLLISION_LOWER_PATH, "export const lower = 2;\n"),
                    ),
                    rootNames = listOf(COLLISION_UPPER_PATH, COLLISION_LOWER_PATH),
                    profileAuthority = authority,
                    limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
                    entryPath = COLLISION_UPPER_PATH,
                )
            }
            assertTrue(collisionFailure.message.orEmpty().contains("ASCII case-fold collision"))
        }

        assertThrows(IllegalArgumentException::class.java) {
            R2ProjectSnapshot.create(
                files = listOf(
                    R2ProjectTextFile(DEFAULT_LIBRARY, ""),
                    R2ProjectTextFile("/sources/lone-surrogate.ts", "\ud800"),
                ),
                rootNames = listOf("/sources/lone-surrogate.ts"),
                limits = R2ProjectSnapshotLimits.PROVISIONAL_WORKER,
            )
        }

        val rhino = R2CompilationProfileRegistry.PROVISIONAL_RHINO
        val nodeOne = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
        val nodeTwo = R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
        assertEquals(
            listOf(rhino, nodeOne, nodeTwo),
            R2CompilationProfileRegistry.registeredAuthorities,
        )
        assertEquals(nodeOne.canonicalEffectiveOptionsJson, nodeTwo.canonicalEffectiveOptionsJson)
        assertEquals(nodeOne.effectiveOptionsFingerprint, nodeTwo.effectiveOptionsFingerprint)
        assertNotEquals(nodeOne.runtimeCapabilitiesFingerprint, nodeTwo.runtimeCapabilitiesFingerprint)
        assertSame(rhino, R2CompilationProfileRegistry.requireEndToEndAdmitted(rhino))
        assertSame(nodeOne, R2CompilationProfileRegistry.requireEndToEndAdmitted(nodeOne))
        assertThrows(IllegalArgumentException::class.java) {
            R2CompilationProfileRegistry.requireEndToEndAdmitted(nodeTwo)
        }
        assertSame(
            nodeTwo,
            R2CompilationProfileRegistry.requireWorkerResultAdmittedExactContractValue(
                nodeTwo.profile.contractValue,
                nodeTwo.profileSchemaRevision,
                nodeTwo.profileRevision,
            ),
        )
    }

    private fun verifyExecution(execution: Map<String, Any?>) {
        assertEquals(EXECUTION_KEYS, execution.keys.toList())
        assertEquals(listOf("declared", "reversed"), execution.stringList("inputOrders"))
        assertEquals(2, execution.int("repetitionsPerOrder"))
        assertEquals(0, execution.int("expectedHostFsCalls"))
        assertEquals(0, execution.int("expectedTsSysCalls"))
        assertEquals(0, execution.int("expectedNetworkCalls"))
        assertEquals(0, execution.int("expectedEmittedJavaScriptExecutions"))
        assertEquals(48, execution.int("expectedCompiles"))
        assertEquals(8, execution.int("expectedCollisionRejections"))
        assertEquals(8, execution.int("expectedTokenOverflowRejections"))
        assertEquals(SOURCE_COLLECTION_CANONICAL, execution.string("sourceCollectionCanonical"))
        assertEquals(OUTPUT_COLLECTION_CANONICAL, execution.string("outputCollectionCanonical"))
    }

    private fun verifyFrozenAuthority(
        authority: Map<String, Any?>,
        expectedProfile: String,
        productionAuthority: R2CompilationProfileAuthority,
    ) {
        assertEquals(AUTHORITY_KEYS, authority.keys.toList())
        assertEquals("frozen-profile", authority.string("kind"))
        assertEquals("createIncrementalProgram", authority.string("compilerApi"))
        assertEquals(expectedProfile, authority.string("targetProfile"))
        assertEquals(1, authority.int("profileRevision"))
        assertEquals(expectedProfile, authority.string("declarationLayer"))
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
                R2Utf8.encode(authority.string("canonicalOptionsJson"), "P5 compiler options"),
            ),
        )
        val options = R2StrictJson.parseObject(
            R2Utf8.encode(authority.string("canonicalOptionsJson"), "P5 compiler options"),
        )
        assertFalse("experimentalDecorators" in options)
        assertFalse("emitDecoratorMetadata" in options)
    }

    private fun verifyGeneratorAuthority(generator: Map<String, Any?>): GeneratorAuthority {
        assertEquals(GENERATOR_KEYS, generator.keys.toList())
        assertEquals("repeated-interface-v1", generator.string("kind"))
        assertEquals("interface Boundary{}\r\n", generator.string("line"))
        assertEquals(16_384, generator.int("repetitions"))
        assertEquals(360_448, generator.int("renderedUtf8ByteLength"))
        assertEquals(RENDERED_GENERATOR_SHA256, generator.string("renderedSha256"))
        assertEquals(4, generator.int("tokensPerRepetition"))
        assertEquals(65_536, generator.int("acceptedTokenCount"))
        assertEquals(";\r\n", generator.string("overflowSuffix"))
        assertEquals(65_537, generator.int("overflowTokenCount"))
        assertEquals(262_144, generator.int("maximumProjectTokens"))

        val profileTotals = generator.objectValue("profileAggregateTokenCounts")
        assertEquals(listOf("rhino", "node"), profileTotals.keys.toList())
        assertEquals(152_036, profileTotals.int("rhino"))
        assertEquals(191_040, profileTotals.int("node"))
        profileTotals.values.forEach { rawValue ->
            val token = rawValue as R2StrictJson.NumberToken
            assertTrue(token.source.toInt() <= generator.int("maximumProjectTokens"))
        }
        verifyPreProgramRejection(
            expected = generator.objectValue("overflowExpected"),
            code = "ORACLE_FILE_TOKEN_LIMIT_EXCEEDED",
        )

        val renderedText = generator.string("line").repeat(generator.int("repetitions"))
        requireValidUnicodeScalars(renderedText, "P5 generated high-token source")
        val renderedBytes = R2Utf8.encode(renderedText, "P5 generated high-token source")
        assertEquals(generator.int("renderedUtf8ByteLength"), renderedBytes.size)
        assertEquals(generator.string("renderedSha256"), R2Digests.sha256(renderedBytes))
        assertEquals(
            generator.int("acceptedTokenCount"),
            generator.int("repetitions") * generator.int("tokensPerRepetition"),
        )
        assertEquals(
            generator.int("acceptedTokenCount") + 1,
            generator.int("overflowTokenCount"),
        )
        val overflowText = renderedText + generator.string("overflowSuffix")
        assertEquals(360_451, R2Utf8.encode(overflowText, "P5 overflow source").size)
        assertEquals(OVERFLOW_GENERATOR_SHA256, R2Digests.sha256(R2Utf8.encode(
            overflowText,
            "P5 overflow source",
        )))
        return GeneratorAuthority(
            kind = generator.string("kind"),
            renderedText = renderedText,
            acceptedTokenCount = generator.int("acceptedTokenCount"),
            overflowTokenCount = generator.int("overflowTokenCount"),
        )
    }

    private fun verifyBoundaryMatrix(matrix: List<Map<String, Any?>>) {
        assertEquals(EXPECTED_BOUNDARIES.size, matrix.size)
        matrix.zip(EXPECTED_BOUNDARIES).forEach { (entry, expected) ->
            assertEquals(BOUNDARY_MATRIX_KEYS, entry.keys.toList())
            assertEquals(expected.boundary, entry.string("boundary"))
            assertEquals(expected.caseSuffix, entry.string("caseSuffix"))
            assertEquals(listOf("rhino", "node"), entry.stringList("profiles"))
            assertEquals(expected.oracleContract, entry.string("oracleContract"))
        }
    }

    private fun verifyCase(
        fixtureCase: Map<String, Any?>,
        generator: GeneratorAuthority,
    ): CaseShape {
        assertEquals(CASE_KEYS, fixtureCase.keys.toList())
        val boundary = fixtureCase.string("boundary")
        val definition = EXPECTED_BOUNDARIES.single { expected ->
            expected.boundary == boundary
        }
        val authority = fixtureCase.string("authority")
        assertTrue(authority == "rhino" || authority == "node")
        assertEquals("$authority-${definition.caseSuffix}", fixtureCase.string("id"))
        assertEquals(definition.productionRelationship, fixtureCase.string("productionRelationship"))

        val roots = fixtureCase.stringList("rootNames")
        val files = fixtureCase.objectList("files")
        assertEquals(2, roots.size)
        assertEquals(2, roots.distinct().size)
        assertEquals(2, files.size)
        val materialized = files.map { file -> materializeFile(file, generator) }
        val filePaths = materialized.map(MaterializedFile::path)
        assertEquals(filePaths, roots)
        assertEquals(2, filePaths.distinct().size)
        filePaths.forEach { path ->
            assertTrue(path.startsWith("/sources/"))
            assertTrue(path.endsWith(".ts"))
            assertFalse(path.endsWith(".d.ts"))
        }

        val sourceCanonical = buildCollectionCanonical(
            materialized.map { file ->
                val bytes = R2Utf8.encode(file.text, "${fixtureCase.string("id")} source")
                CollectionRecord(file.path, bytes.size, R2Digests.sha256(bytes))
            },
        )
        val sourceIdentity = fixtureCase.string("sourceCollectionSha256")
        R2Digests.requireLowerSha256(sourceIdentity, "P5 source collection SHA-256")
        assertEquals(EXPECTED_SOURCE_IDENTITIES.getValue(boundary), sourceIdentity)
        assertEquals(
            sourceIdentity,
            R2Digests.sha256(R2Utf8.encode(sourceCanonical, "P5 source collection")),
        )

        val assertions = fixtureCase.objectValue("assertions")
        verifySourceAssertions(boundary, assertions, materialized, generator)
        val expected = fixtureCase.objectValue("expected")
        if (boundary == "ascii-case-fold-collision") {
            verifyPreProgramRejection(expected, "ORACLE_ASCII_CASE_FOLD_COLLISION")
            assertEquals(listOf("collisionPolicy", "compilerInvocationExpected"), assertions.keys.toList())
            assertEquals("ascii-a-z-case-fold", assertions.string("collisionPolicy"))
            assertFalse(assertions.boolean("compilerInvocationExpected"))
            assertNotEquals(filePaths[0], filePaths[1])
            assertEquals(filePaths[0].asciiCaseFold(), filePaths[1].asciiCaseFold())
            assertTrue(filePaths.all { path -> path.all { character -> character.code in 0x20..0x7e } })
            return CaseShape(compiled = false, outputCount = 0)
        }

        assertEquals(COMPILED_EXPECTED_KEYS, expected.keys.toList())
        assertEquals("compiled", expected.string("outcome"))
        assertTrue(expected.listValue("diagnostics").isEmpty())
        assertFalse(expected.boolean("emitSkipped"))
        val outputs = expected.objectList("outputs")
        assertEquals(4, outputs.size)
        assertEquals(2, outputs.count { output -> output.string("path").endsWith(".js") })
        assertEquals(2, outputs.count { output -> output.string("path").endsWith(".js.map") })
        val outputPaths = outputs.map { output -> output.string("path") }
        assertEquals(outputPaths.sorted(), outputPaths)
        val exactOutputPaths = filePaths.flatMap { sourcePath ->
            val stem = sourcePath.removePrefix("/sources/").removeSuffix(".ts")
            listOf("/outputs/$stem.js", "/outputs/$stem.js.map")
        }.sorted()
        assertEquals(exactOutputPaths, outputPaths)
        outputs.forEach { output -> verifyOutput(output, outputPaths) }

        val outputCanonical = buildCollectionCanonical(outputs.map { output ->
            CollectionRecord(
                path = output.string("path"),
                byteLength = output.int("byteLength"),
                sha256 = output.string("sha256"),
            )
        })
        val outputIdentity = expected.string("outputCollectionSha256")
        R2Digests.requireLowerSha256(outputIdentity, "P5 output collection SHA-256")
        assertEquals(
            EXPECTED_OUTPUT_IDENTITIES.getValue(fixtureCase.string("id")),
            outputIdentity,
        )
        assertEquals(
            outputIdentity,
            R2Digests.sha256(R2Utf8.encode(outputCanonical, "P5 output collection")),
        )
        verifyOutputAssertions(boundary, materialized, outputPaths)
        return CaseShape(compiled = true, outputCount = outputs.size)
    }

    private fun materializeFile(
        file: Map<String, Any?>,
        generator: GeneratorAuthority,
    ): MaterializedFile {
        val path = file.string("path")
        return if ("text" in file) {
            assertEquals(TEXT_FILE_KEYS, file.keys.toList())
            MaterializedFile(path, file.string("text"), generated = false)
        } else {
            assertEquals(GENERATED_FILE_KEYS, file.keys.toList())
            val descriptor = file.objectValue("generator")
            assertEquals(listOf("kind"), descriptor.keys.toList())
            assertEquals(generator.kind, descriptor.string("kind"))
            MaterializedFile(path, generator.renderedText, generated = true)
        }.also { materialized ->
            requireValidUnicodeScalars(materialized.path, "P5 source path")
            requireValidUnicodeScalars(materialized.text, "P5 source text")
        }
    }

    private fun verifySourceAssertions(
        boundary: String,
        assertions: Map<String, Any?>,
        files: List<MaterializedFile>,
        generator: GeneratorAuthority,
    ) {
        when (boundary) {
            "chinese-path" -> {
                assertEquals(CHINESE_ASSERTION_KEYS, assertions.keys.toList())
                assertTrue(assertions.boolean("directCompilerUnicodePath"))
                assertFalse(assertions.boolean("productionPrintableAsciiAdmission"))
                assertTrue(files.any { file -> file.path.any { it.code !in 0x20..0x7e } })
            }
            "space-path" -> {
                assertEquals(listOf("productionOrdinarySourceCompatible"), assertions.keys.toList())
                assertTrue(assertions.boolean("productionOrdinarySourceCompatible"))
                assertTrue(files.all { file -> ' ' in file.path })
            }
            "crlf" -> {
                assertEquals(listOf("rawInputLineEnding", "emittedLineEnding"), assertions.keys.toList())
                assertEquals("CRLF", assertions.string("rawInputLineEnding"))
                assertEquals("LF", assertions.string("emittedLineEnding"))
                assertTrue(files.all { file -> "\r\n" in file.text })
                assertTrue(files.all { file -> '\r' !in file.text.replace("\r\n", "") })
            }
            "leading-bom" -> {
                assertEquals(listOf("leadingBomCodePoint", "emittedBom"), assertions.keys.toList())
                assertEquals(0xfeff, assertions.int("leadingBomCodePoint"))
                assertFalse(assertions.boolean("emittedBom"))
                files.forEach { file ->
                    assertTrue(file.text.startsWith('\ufeff'))
                    assertFalse(file.text.drop(1).startsWith('\ufeff'))
                    assertArrayEquals(
                        byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()),
                        R2Utf8.encode(file.text, "P5 BOM source").copyOfRange(0, 3),
                    )
                }
            }
            "unicode-content" -> {
                assertEquals(
                    listOf("contentNormalization", "requiredSourceSubstrings"),
                    assertions.keys.toList(),
                )
                assertEquals("opaque", assertions.string("contentNormalization"))
                val markers = assertions.stringList("requiredSourceSubstrings")
                assertEquals(listOf("\u4e2d\u6587", "\u00e9", "e\u0301", "\ud83d\ude80"), markers)
                assertTrue(markers.all { marker -> files.any { file -> marker in file.text } })
                assertTrue(Normalizer.isNormalized("\u00e9", Normalizer.Form.NFC))
                assertTrue(Normalizer.isNormalized("e\u0301", Normalizer.Form.NFD))
                assertNotEquals("\u00e9", "e\u0301")
            }
            "high-token" -> {
                assertEquals(listOf("acceptedTokenCount", "overflowTokenCount"), assertions.keys.toList())
                assertEquals(generator.acceptedTokenCount, assertions.int("acceptedTokenCount"))
                assertEquals(generator.overflowTokenCount, assertions.int("overflowTokenCount"))
                assertEquals(1, files.count { file -> file.generated })
                assertEquals(generator.renderedText, files.single { file -> file.generated }.text)
            }
            "ascii-case-fold-collision" -> assertTrue(files.none { file -> file.generated })
            else -> error("Unknown P5 boundary: $boundary")
        }
        if (boundary != "high-token") {
            assertTrue(files.none { file -> file.generated })
        }
    }

    private fun verifyOutputAssertions(
        boundary: String,
        files: List<MaterializedFile>,
        outputPaths: List<String>,
    ) {
        when (boundary) {
            "chinese-path" -> {
                assertTrue(files.any { file -> file.path.any { it.code !in 0x20..0x7e } })
                assertTrue(outputPaths.any { path -> path.any { it.code !in 0x20..0x7e } })
            }
            "space-path" -> assertTrue(outputPaths.all { path -> ' ' in path })
            else -> Unit
        }
    }

    private fun verifyOutput(output: Map<String, Any?>, allOutputPaths: List<String>) {
        val outputPath = output.string("path")
        val isMap = outputPath.endsWith(".js.map")
        assertEquals(if (isMap) SOURCE_MAP_OUTPUT_KEYS else OUTPUT_KEYS, output.keys.toList())
        assertTrue(outputPath.startsWith("/outputs/"))
        assertTrue(output.int("byteLength") > 0)
        R2Digests.requireLowerSha256(output.string("sha256"), "P5 output SHA-256")
        val sourcePath = "/sources/" + outputPath
            .removePrefix("/outputs/")
            .removeSuffix(".map")
            .removeSuffix(".js") + ".ts"
        assertEquals(listOf(sourcePath), output.stringList("sourcePaths"))
        if (isMap) {
            val sourceMap = output.objectValue("sourceMap")
            assertEquals(SOURCE_MAP_KEYS, sourceMap.keys.toList())
            assertEquals(3, sourceMap.int("version"))
            assertEquals(outputPath.substringAfterLast('/').removeSuffix(".map"), sourceMap.string("file"))
            assertEquals("", sourceMap.string("sourceRoot"))
            assertEquals(
                listOf(relativeVfsPath(outputPath.substringBeforeLast('/'), sourcePath)),
                sourceMap.stringList("sources"),
            )
            assertTrue(sourceMap.stringList("names").isEmpty())
            assertTrue(sourceMap.int("mappingsByteLength") >= 0)
            R2Digests.requireLowerSha256(
                sourceMap.string("mappingsSha256"),
                "P5 Source Map mappings SHA-256",
            )
        } else {
            assertTrue("$outputPath.map" in allOutputPaths)
        }
    }

    private fun verifyPreProgramRejection(expected: Map<String, Any?>, code: String) {
        assertEquals(REJECTION_EXPECTED_KEYS, expected.keys.toList())
        assertEquals("rejected", expected.string("outcome"))
        assertEquals("pre-program", expected.string("stage"))
        assertEquals(code, expected.string("code"))
        assertFalse(expected.boolean("programCreated"))
        assertTrue(expected.listValue("diagnostics").isEmpty())
        assertTrue(expected.listValue("outputs").isEmpty())
        assertEquals(0, expected.int("compilerApiCalls"))
        assertEquals(0, expected.int("outputWrites"))
    }

    private fun buildCollectionCanonical(records: List<CollectionRecord>): String = buildString {
        records.sortedBy(CollectionRecord::path).forEach { record ->
            R2Digests.requireLowerSha256(record.sha256, "P5 collection record SHA-256")
            append(record.path)
            append('\u0000')
            append(record.byteLength)
            append('\u0000')
            append(record.sha256)
            append('\n')
        }
    }

    private fun relativeVfsPath(fromDirectory: String, targetPath: String): String {
        val from = fromDirectory.removePrefix("/").split('/').filter(String::isNotEmpty)
        val target = targetPath.removePrefix("/").split('/').filter(String::isNotEmpty)
        var common = 0
        while (common < from.size && common < target.size && from[common] == target[common]) {
            common += 1
        }
        return (List(from.size - common) { ".." } + target.drop(common)).joinToString("/")
    }

    private fun String.asciiCaseFold(): String = buildString(length) {
        for (character in this@asciiCaseFold) {
            append(if (character in 'A'..'Z') (character.code + 0x20).toChar() else character)
        }
    }

    private fun requireValidScalarTree(value: Any?, label: String) {
        when (value) {
            is String -> requireValidUnicodeScalars(value, label)
            is List<*> -> value.forEachIndexed { index, item ->
                requireValidScalarTree(item, "$label[$index]")
            }
            is Map<*, *> -> value.forEach { (rawKey, item) ->
                val key = rawKey as? String ?: error("$label contains a non-string key.")
                requireValidUnicodeScalars(key, "$label key")
                requireValidScalarTree(item, "$label.$key")
            }
        }
    }

    private fun requireValidUnicodeScalars(value: String, label: String) {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when {
                Character.isHighSurrogate(character) -> {
                    require(index + 1 < value.length) { "$label ends with a lone high surrogate." }
                    require(Character.isLowSurrogate(value[index + 1])) {
                        "$label contains a lone high surrogate."
                    }
                    index += 2
                }
                Character.isLowSurrogate(character) ->
                    error("$label contains a lone low surrogate.")
                else -> index += 1
            }
        }
    }

    private fun loadFixture(): Map<String, Any?> = R2StrictJson.parseObject(
        requireNotNull(
            javaClass.getResourceAsStream("/r2-compiler-boundaries/p5-v1.json"),
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

    private data class BoundaryDefinition(
        val boundary: String,
        val caseSuffix: String,
        val oracleContract: String,
        val productionRelationship: String,
    )

    private data class GeneratorAuthority(
        val kind: String,
        val renderedText: String,
        val acceptedTokenCount: Int,
        val overflowTokenCount: Int,
    )

    private data class MaterializedFile(
        val path: String,
        val text: String,
        val generated: Boolean,
    )
    private data class CollectionRecord(val path: String, val byteLength: Int, val sha256: String)
    private data class CaseShape(val compiled: Boolean, val outputCount: Int)

    private companion object {
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "typescriptVersion",
            "scope",
            "execution",
            "authorities",
            "generatorAuthority",
            "boundaryMatrix",
            "cases",
        )
        val EXECUTION_KEYS = listOf(
            "inputOrders",
            "repetitionsPerOrder",
            "expectedHostFsCalls",
            "expectedTsSysCalls",
            "expectedNetworkCalls",
            "expectedEmittedJavaScriptExecutions",
            "expectedCompiles",
            "expectedCollisionRejections",
            "expectedTokenOverflowRejections",
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
        val GENERATOR_KEYS = listOf(
            "kind",
            "line",
            "repetitions",
            "renderedUtf8ByteLength",
            "renderedSha256",
            "tokensPerRepetition",
            "acceptedTokenCount",
            "overflowSuffix",
            "overflowTokenCount",
            "maximumProjectTokens",
            "profileAggregateTokenCounts",
            "overflowExpected",
        )
        val BOUNDARY_MATRIX_KEYS = listOf("boundary", "caseSuffix", "profiles", "oracleContract")
        val CASE_KEYS = listOf(
            "id",
            "boundary",
            "authority",
            "productionRelationship",
            "sourceCollectionSha256",
            "rootNames",
            "files",
            "assertions",
            "expected",
        )
        val TEXT_FILE_KEYS = listOf("path", "text")
        val GENERATED_FILE_KEYS = listOf("path", "generator")
        val COMPILED_EXPECTED_KEYS = listOf(
            "outcome",
            "diagnostics",
            "emitSkipped",
            "outputCollectionSha256",
            "outputs",
        )
        val REJECTION_EXPECTED_KEYS = listOf(
            "outcome",
            "stage",
            "code",
            "programCreated",
            "diagnostics",
            "outputs",
            "compilerApiCalls",
            "outputWrites",
        )
        val OUTPUT_KEYS = listOf("path", "byteLength", "sha256", "sourcePaths")
        val SOURCE_MAP_OUTPUT_KEYS = OUTPUT_KEYS + "sourceMap"
        val SOURCE_MAP_KEYS = listOf(
            "version",
            "file",
            "sourceRoot",
            "sources",
            "names",
            "mappingsByteLength",
            "mappingsSha256",
        )
        val CHINESE_ASSERTION_KEYS = listOf(
            "directCompilerUnicodePath",
            "productionPrintableAsciiAdmission",
        )
        val EXPECTED_BOUNDARIES = listOf(
            BoundaryDefinition(
                "chinese-path",
                "chinese-path",
                "direct-compiler-positive/production-printable-ascii-negative",
                "direct-compiler-only/production-printable-ascii-negative",
            ),
            BoundaryDefinition(
                "space-path",
                "space-path",
                "direct-compiler-positive/production-ordinary-source-compatible",
                "production-compatible-ordinary-source",
            ),
            BoundaryDefinition(
                "crlf",
                "crlf",
                "raw-crlf-identity/emitted-lf",
                "production-compatible-opaque-source-bytes",
            ),
            BoundaryDefinition(
                "leading-bom",
                "bom",
                "raw-leading-bom-identity/emitted-no-bom",
                "production-compatible-opaque-source-bytes",
            ),
            BoundaryDefinition(
                "unicode-content",
                "unicode",
                "valid-scalars-opaque/no-normalization",
                "production-compatible-valid-unicode-content",
            ),
            BoundaryDefinition(
                "high-token",
                "high-token",
                "65536-inclusive/65537-pre-program-negative",
                "production-compatible-inclusive-limit",
            ),
            BoundaryDefinition(
                "ascii-case-fold-collision",
                "canonical-collision",
                "ascii-a-z-fold/pre-program-negative",
                "production-compatible-pre-program-negative",
            ),
        )
        val EXPECTED_CASE_IDS = EXPECTED_BOUNDARIES.flatMap { definition ->
            listOf("rhino-${definition.caseSuffix}", "node-${definition.caseSuffix}")
        }
        val EXPECTED_SOURCE_IDENTITIES = mapOf(
            "chinese-path" to "65a117549f2b2fce5de62c2569001cd03d58191d7cc2938b6cb7079f2bd2f264",
            "space-path" to "d94a3907a35fb6743f512e76394439fe986c13c4a062ad5b5517e923d56cc1b7",
            "crlf" to "f3488915bef1d9caad80d56e62e6c2bf882d4a3d9d3c27744ab6d38f5bcea026",
            "leading-bom" to "8befc549550472efc6cb80aa438f39bc7588f5acd5b19f2ea781c13c8e4cf83f",
            "unicode-content" to "afc1aa7adf0492950d48881753870342701cb12d86107c6168c0dfd97a020899",
            "high-token" to "4b151c08b296d35061b9d6a1929aabe34d226e6b3c72a3db255cf2718a9b3a06",
            "ascii-case-fold-collision" to
                "46fbf14d996ca8b63d48bf69b93bc782a25d763d4e025ef458a64844a746336b",
        )
        val EXPECTED_OUTPUT_IDENTITIES = mapOf(
            "rhino-chinese-path" to
                "5cba449b921d9fd461b21796700e94f2d541d6ddd788c62509db60a78484c5d4",
            "node-chinese-path" to
                "5cba449b921d9fd461b21796700e94f2d541d6ddd788c62509db60a78484c5d4",
            "rhino-space-path" to
                "7be73c7fcf4cb2eb200892046ee67264ac21f9b04824384bd3bb97211d666d85",
            "node-space-path" to
                "7be73c7fcf4cb2eb200892046ee67264ac21f9b04824384bd3bb97211d666d85",
            "rhino-crlf" to
                "2bdb28ff8b3655d808ce534a49a99120b3f7b2e871dadbebe148575a1db316be",
            "node-crlf" to
                "2bdb28ff8b3655d808ce534a49a99120b3f7b2e871dadbebe148575a1db316be",
            "rhino-bom" to
                "c7aef340adbfd56eedd44b4481f74015169668088c181c65e2e91ce3450b0eec",
            "node-bom" to
                "c7aef340adbfd56eedd44b4481f74015169668088c181c65e2e91ce3450b0eec",
            "rhino-unicode" to
                "0a0848cbc71e8d8f50fdfe24935df888b48e8799e7ead03cfb3b77fcb62ed9da",
            "node-unicode" to
                "0a0848cbc71e8d8f50fdfe24935df888b48e8799e7ead03cfb3b77fcb62ed9da",
            "rhino-high-token" to
                "bf7ea695871700ca5fbee7f54af5052c64e94483f0ab34a7f428ccee1a0758d8",
            "node-high-token" to
                "f2422646a3def4972a6c540e11ed915c062e4d665210d79b7d24ae7bd8f152a3",
        )
        const val STANDARD_DECORATOR_SEMANTICS =
            "standard-stage-3/experimentalDecorators-absent"
        const val SOURCE_COLLECTION_CANONICAL =
            "sorted files; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)"
        const val OUTPUT_COLLECTION_CANONICAL =
            "sorted outputs; UTF-8(path + NUL + decimal byteLength + NUL + lowercase sha256 + LF)"
        const val RENDERED_GENERATOR_SHA256 =
            "f55b81564428621c455a2860f5ab99a30827ace8089e5a3118b1af35ecbae26b"
        const val OVERFLOW_GENERATOR_SHA256 =
            "48248de912ee4000546cf38ddc013a066362717f965aec217e3aae9ae6184471"
        const val DEFAULT_LIBRARY = "/declarations/lib.d.ts"
        const val SPACE_PATH = "/sources/space directory/main file.ts"
        const val CHINESE_PATH = "/sources/\u4e2d\u6587\u76ee\u5f55/\u5165\u53e3.ts"
        const val COLLISION_UPPER_PATH = "/sources/Collision/main.ts"
        const val COLLISION_LOWER_PATH = "/sources/collision/main.ts"
    }
}
