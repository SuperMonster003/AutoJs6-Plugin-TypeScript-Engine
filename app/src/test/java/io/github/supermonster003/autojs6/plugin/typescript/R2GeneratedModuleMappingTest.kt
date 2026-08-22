package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2GeneratedModuleMappingTest {

    @Test
    fun sharedFixtureProvesKotlinMappingAndJavaScriptGoldenParity() {
        val fixture = fixture()
        assertEquals(EXACT_FIXTURE_KEYS, fixture.keys)
        assertEquals(FIXTURE_SCHEMA, fixture.string("schema"))
        assertEquals(1, fixture.integer("revision"))
        assertEquals(
            R2NodeModuleOutputPolicy.fingerprint,
            fixture.string("moduleOutputPolicyFingerprint"),
        )
        val packageExamples = fixture.objectValue("packageMetadataInventoryFingerprints")
        assertEquals(EXACT_PACKAGE_EXAMPLE_KEYS, packageExamples.keys)
        mapOf(
            "typeAbsent" to "{}",
            "commonjs" to "{\"type\":\"commonjs\"}",
            "module" to "{\"type\":\"module\"}",
        ).forEach { (key, rawJson) ->
            val bytes = rawJson.toByteArray()
            val inventory = R2PackageMetadataInventory.create(
                dependencyRoots = emptyList(),
                files = listOf(
                    R2PackageMetadataFileDescriptor(
                        path = R2PackageMetadataPolicy.PROJECT_PACKAGE_PATH,
                        size = bytes.size.toLong(),
                        sha256 = R2Digests.sha256(bytes),
                    ),
                ),
            )
            assertEquals(inventory.fingerprint, packageExamples.string(key))
        }

        val requests = LinkedHashMap<String, R2GeneratedModuleMappingRequest>()
        val mappings = LinkedHashMap<String, R2GeneratedModuleMapping>()
        fixture.objects("validCases").forEach { testCase ->
            assertEquals(EXACT_VALID_CASE_KEYS, testCase.keys)
            val id = testCase.string("id")
            assertFalse(requests.containsKey(id))
            val request = request(testCase.objectValue("request"))
            val mapping = R2GeneratedModuleMappingPolicy.map(request)
            val expected = testCase.objectValue("expected")
            assertEquals(EXACT_EXPECTED_KEYS, expected.keys)
            assertEquals(expected.string("projectNamespace"), mapping.projectNamespace)
            assertEquals(expected.string("originalSourceUri"), mapping.originalSourceUri)
            assertEquals(
                mapping.originalSourceUri,
                R2GeneratedModuleMappingPolicy.originalSourceUri(
                    projectId = request.projectId,
                    sourcePath = request.sourcePath,
                ),
            )
            assertEquals(expected.string("outputExtension"), mapping.outputExtension)
            assertEquals(expected.string("generatedPath"), mapping.generatedPath)
            assertEquals(expected.string("sourceMapPath"), mapping.sourceMapPath)
            assertEquals(expected.string("generatedUri"), mapping.generatedUri)
            assertEquals(expected.string("fingerprint"), mapping.fingerprint)
            assertEquals(request.sourcePath, mapping.sourcePath)
            assertEquals(request.impliedModuleFormat, mapping.moduleFormat)
            assertTrue(mapping.originalSourceUri.length <= 4_096)
            assertTrue(mapping.generatedUri.length <= 4_096)
            assertTrue(
                mapping.canonicalEvidence.split('|').drop(1).all { token ->
                    token.indexOf('=') > 0 && token.indexOf('=') == token.lastIndexOf('=')
                },
            )
            requests[id] = request
            mappings[id] = mapping
        }

        fixture.objects("invalidCases").forEach { testCase ->
            val error = assertFails<R2GeneratedModuleMappingException> {
                when (testCase.string("operation")) {
                    "map" -> R2GeneratedModuleMappingPolicy.map(
                        request(testCase.objectValue("request")),
                    )
                    "inventory" -> R2GeneratedModuleMappingPolicy.mapInventory(
                        testCase.objects("requests").map(::request),
                    )
                    "claim" -> {
                        val validCaseId = testCase.string("validCaseId")
                        val canonical = requireNotNull(mappings[validCaseId])
                        val claim = mutateClaim(
                            canonical = canonical,
                            field = testCase.string("field"),
                            replacement = testCase.string("replacement"),
                        )
                        R2GeneratedModuleMappingPolicy.requireExact(
                            request = requireNotNull(requests[validCaseId]),
                            claimed = claim,
                        )
                    }
                    else -> fail("Unknown invalid fixture operation.")
                }
            }
            assertEquals(testCase.string("expectedCode"), error.policyCode.contractValue)
        }

        val singleProjectRequests = requests.values.filter { request ->
            request.projectId == "demo"
        }
        val inventory = R2GeneratedModuleMappingPolicy.mapInventory(singleProjectRequests)
        assertEquals(singleProjectRequests.size, inventory.size)
        assertEquals(inventory.map { mapping -> mapping.sourcePath }.sorted(), inventory.map {
            mapping -> mapping.sourcePath
        })
        assertEquals(
            inventory,
            R2GeneratedModuleMappingPolicy.mapInventory(singleProjectRequests.reversed()),
        )
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (inventory as MutableList<R2GeneratedModuleMapping>).clear()
        }

        val exactRequest = requireNotNull(requests["node-mts-esm"])
        val exactMapping = requireNotNull(mappings["node-mts-esm"])
        listOf(
            exactMapping.copy(sourcePath = "/sources/other.mts"),
            exactMapping.copy(projectNamespace = "p-${"0".repeat(64)}"),
            exactMapping.copy(originalSourceUri = "autojs://source/wrong/sources/entry.mts"),
            exactMapping.copy(moduleFormat = R2ModuleFormat.COMMONJS),
            exactMapping.copy(outputExtension = ".js"),
            exactMapping.copy(generatedPath = "/outputs/entry.js"),
            exactMapping.copy(sourceMapPath = "/outputs/entry.js.map"),
            exactMapping.copy(generatedUri = "autojs://generated/wrong/outputs/entry.mjs"),
            exactMapping.copy(canonicalEvidence = "forged"),
            exactMapping.copy(fingerprint = "0".repeat(64)),
        ).forEach { claim ->
            assertEquals(
                R2GeneratedModuleMappingFailureCode.CLAIM_MISMATCH,
                assertFails<R2GeneratedModuleMappingException> {
                    R2GeneratedModuleMappingPolicy.requireExact(exactRequest, claim)
                }.policyCode,
            )
        }

        val rawDelimiter = requireNotNull(mappings[RAW_DELIMITER_CASE])
        assertTrue(
            rawDelimiter.canonicalEvidence.contains(
                "sourcePath=%2Fsources%2Fraw%7Cpipe.mts",
            ),
        )
        assertFalse(rawDelimiter.canonicalEvidence.contains("sourcePath=/sources/raw|pipe.mts"))
        val literalPercent = requireNotNull(mappings[LITERAL_PERCENT_CASE])
        assertTrue(literalPercent.generatedUri.contains("/%252e%252e/"))
        assertFalse(literalPercent.generatedUri.contains("/../"))
    }

    @Test
    fun strictUnicodeAndTransportRelativeBudgetsFailClosed() {
        val invalidProjectId = "bad\ud800id"
        assertEquals(
            R2GeneratedModuleMappingFailureCode.INVALID_PROJECT_ID,
            assertFails<R2GeneratedModuleMappingException> {
                R2GeneratedModuleMappingPolicy.map(
                    rhinoRequest(projectId = invalidProjectId),
                )
            }.policyCode,
        )
        assertEquals(
            R2GeneratedModuleMappingFailureCode.INVALID_PROJECT_ID,
            assertFails<R2GeneratedModuleMappingException> {
                R2GeneratedModuleMappingPolicy.map(rhinoRequest(projectId = "x".repeat(257)))
            }.policyCode,
        )
        val maximumFileName =
            "a".repeat(1_024 - "outputs/".length - ".js.map".length) + ".ts"
        assertEquals(
            "/outputs/${maximumFileName.removeSuffix(".ts")}.js",
            R2GeneratedModuleMappingPolicy.map(
                rhinoRequest(sourcePath = "/sources/$maximumFileName"),
            ).generatedPath,
        )
        val oversizedOutputFileName = "a$maximumFileName"
        assertEquals(
            R2GeneratedModuleMappingFailureCode.OUTPUT_PATH_LIMIT_EXCEEDED,
            assertFails<R2GeneratedModuleMappingException> {
                R2GeneratedModuleMappingPolicy.map(
                    rhinoRequest(sourcePath = "/sources/$oversizedOutputFileName"),
                )
            }.policyCode,
        )
        val oversizedSourceFileName =
            "a".repeat(1_025 - "sources/".length - ".ts".length) + ".ts"
        assertEquals(
            R2GeneratedModuleMappingFailureCode.INVALID_SOURCE_PATH,
            assertFails<R2GeneratedModuleMappingException> {
                R2GeneratedModuleMappingPolicy.map(
                    rhinoRequest(sourcePath = "/sources/$oversizedSourceFileName"),
                )
            }.policyCode,
        )
        val maximumDepthPath = "/sources/" +
            List(62) { index -> "d$index" }.joinToString("/") +
            "/main.ts"
        R2GeneratedModuleMappingPolicy.map(rhinoRequest(sourcePath = maximumDepthPath))
        val excessiveDepthPath = "/sources/x/$maximumDepthPath".replace("/sources/x//", "/sources/x/")
        assertEquals(
            R2GeneratedModuleMappingFailureCode.INVALID_SOURCE_PATH,
            assertFails<R2GeneratedModuleMappingException> {
                R2GeneratedModuleMappingPolicy.map(
                    rhinoRequest(sourcePath = excessiveDepthPath),
                )
            }.policyCode,
        )
    }

    private fun request(value: Map<String, Any?>): R2GeneratedModuleMappingRequest {
        assertEquals(EXACT_REQUEST_KEYS, value.keys)
        val profileValue = value.string("profile")
        val profileRevision = value.integer("profileRevision")
        val profileAuthority = when (profileValue to profileRevision) {
            "rhino" to 1 -> R2CompilationProfileRegistry.PROVISIONAL_RHINO
            "node" to 1 -> R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_1
            "node" to 2 -> R2CompilationProfileRegistry.PROVISIONAL_NODE_NEXT_REVISION_2
            else -> throw R2GeneratedModuleMappingException(
                R2GeneratedModuleMappingFailureCode.INVALID_PROFILE_AUTHORITY,
                "Fixture profile authority is not registered.",
            )
        }
        val moduleFormat = R2ModuleFormat.entries.singleOrNull { format ->
            format.contractValue == value.string("impliedModuleFormat")
        } ?: error("Unknown fixture module format.")
        val packageValue = value["packageFormatAuthority"]
        val packageAuthority = if (packageValue == null) {
            null
        } else {
            val packageObject = packageValue as? Map<*, *>
                ?: error("Package authority must be an object.")
            @Suppress("UNCHECKED_CAST")
            packageAuthority(packageObject as Map<String, Any?>)
        }
        return R2GeneratedModuleMappingRequest(
            projectId = value.string("projectId"),
            profileAuthority = profileAuthority,
            sourcePath = value.string("sourcePath"),
            impliedModuleFormat = moduleFormat,
            packageFormatAuthority = packageAuthority,
        )
    }

    private fun packageAuthority(
        value: Map<String, Any?>,
    ): R2GeneratedPackageFormatAuthority {
        assertEquals(EXACT_PACKAGE_AUTHORITY_KEYS, value.keys)
        val typeState = R2PackageTypeState.entries.singleOrNull { state ->
            state.contractValue == value.string("typeState")
        } ?: error("Unknown fixture package state.")
        return R2GeneratedPackageFormatAuthority(
            typeState = typeState,
            packageMetadataInventoryFingerprint =
                value["packageMetadataInventoryFingerprint"] as String?,
            boundaryPath = value["boundaryPath"] as String?,
        )
    }

    private fun mutateClaim(
        canonical: R2GeneratedModuleMapping,
        field: String,
        replacement: String,
    ): R2GeneratedModuleMapping = when (field) {
        "originalSourceUri" -> canonical.copy(originalSourceUri = replacement)
        "generatedPath" -> canonical.copy(generatedPath = replacement)
        "sourceMapPath" -> canonical.copy(sourceMapPath = replacement)
        "generatedUri" -> canonical.copy(generatedUri = replacement)
        else -> error("Unknown claim field: $field")
    }

    private fun rhinoRequest(
        projectId: String = "budget",
        sourcePath: String = "/sources/main.ts",
    ): R2GeneratedModuleMappingRequest = R2GeneratedModuleMappingRequest(
        projectId = projectId,
        profileAuthority = R2CompilationProfileRegistry.PROVISIONAL_RHINO,
        sourcePath = sourcePath,
        impliedModuleFormat = R2ModuleFormat.COMMONJS,
    )

    private fun fixture(): Map<String, Any?> = requireNotNull(
        javaClass.getResourceAsStream("/r2-generated-module-mapping/parity-v1.json"),
    ).use { input -> R2StrictJson.parseObject(input.readBytes()) }

    private fun Map<String, Any?>.string(key: String): String =
        getValue(key) as? String ?: error("$key must be a string.")

    private fun Map<String, Any?>.integer(key: String): Int =
        ((getValue(key) as? R2StrictJson.NumberToken)?.source?.toIntOrNull())
            ?: error("$key must be an integer.")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objectValue(key: String): Map<String, Any?> =
        getValue(key) as? Map<String, Any?> ?: error("$key must be an object.")

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> =
        (getValue(key) as? List<*>)?.map { value ->
            value as? Map<String, Any?> ?: error("$key must contain objects.")
        } ?: error("$key must be an array.")

    private inline fun <reified T : Throwable> assertFails(operation: () -> Unit): T {
        try {
            operation()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        fail("Expected ${T::class.java.simpleName}.")
        error("unreachable")
    }

    private companion object {
        const val FIXTURE_SCHEMA = "autojs6.typescript.generated-module-mapping-parity"
        const val RAW_DELIMITER_CASE = "raw-evidence-delimiter-is-single-token-encoded"
        const val LITERAL_PERCENT_CASE = "literal-percent-is-not-decoded-as-traversal"
        val EXACT_FIXTURE_KEYS = setOf(
            "schema",
            "revision",
            "moduleOutputPolicyFingerprint",
            "packageMetadataInventoryFingerprints",
            "validCases",
            "invalidCases",
        )
        val EXACT_VALID_CASE_KEYS = setOf("id", "request", "expected")
        val EXACT_REQUEST_KEYS = setOf(
            "projectId",
            "profile",
            "profileRevision",
            "sourcePath",
            "impliedModuleFormat",
            "packageFormatAuthority",
        )
        val EXACT_PACKAGE_AUTHORITY_KEYS = setOf(
            "typeState",
            "packageMetadataInventoryFingerprint",
            "boundaryPath",
        )
        val EXACT_PACKAGE_EXAMPLE_KEYS = setOf("typeAbsent", "commonjs", "module")
        val EXACT_EXPECTED_KEYS = setOf(
            "projectNamespace",
            "originalSourceUri",
            "outputExtension",
            "generatedPath",
            "sourceMapPath",
            "generatedUri",
            "fingerprint",
        )
    }
}
