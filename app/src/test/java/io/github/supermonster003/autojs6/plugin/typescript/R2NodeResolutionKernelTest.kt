package io.github.supermonster003.autojs6.plugin.typescript

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2NodeResolutionKernelTest {

    @Test
    fun acceptedCatalogReusesC5InventoryAndFreezesParsedRecords() {
        val projectBytes = "{\"imports\":{\"#app\":\"./sources/app.ts\"}}"
            .toByteArray(StandardCharsets.UTF_8)
        val dependencyBytes = "{\"exports\":\"./index.d.ts\"}"
            .toByteArray(StandardCharsets.UTF_8)
        val inventory = inventory(
            dependencyRoots = listOf("packages/pkg"),
            files = listOf(
                "package.json" to projectBytes,
                "packages/pkg/package.json" to dependencyBytes,
            ),
        )
        val catalog = R2NodePackageMetadataCatalog.create(
            inventory = inventory,
            packageMetadataBytes = listOf(projectBytes, dependencyBytes),
        )

        projectBytes.fill('x'.code.toByte())
        dependencyBytes.fill('x'.code.toByte())

        assertEquals(inventory.fingerprint, catalog.inventoryFingerprint)
        assertEquals(listOf("packages/pkg"), catalog.dependencyRoots)
        assertEquals(listOf("/", "/packages/pkg"), catalog.records.map { it.boundaryPath })
        assertEquals("#app", catalog.records.first().root.requireObject("imports").keys.single())
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (catalog.records as MutableList<R2NodePackageMetadataRecord>).clear()
        }
        assertFails<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (catalog.records.first().root as MutableMap<String, Any?>).clear()
        }
    }

    @Test
    fun exportsSelectPackageOrderedConditionsForImportRequireAndTypes() {
        val catalog = catalog(
            listOf("packages/pkg"),
            "package.json" to "{}",
            "packages/pkg/package.json" to
                (
                    "{\"exports\":{\".\":{" +
                        "\"custom\":\"./custom.ts\",\"import\":\"./import.ts\"," +
                        "\"require\":\"./require.ts\",\"types\":\"./index.d.ts\"," +
                        "\"default\":\"./default.ts\"}}}"
                    ),
        )
        val targets = listOf(
            source("/packages/pkg/custom.ts"),
            declaration("/packages/pkg/index.d.ts"),
            source("/packages/pkg/import.ts"),
            source("/packages/pkg/require.ts"),
            source("/packages/pkg/default.ts"),
        )

        val custom = resolved(catalog, targets, "pkg", R2NodeResolutionMode.IMPORT, "custom")
        assertEquals("/packages/pkg/custom.ts", custom.target.path)
        assertEquals(listOf("custom"), custom.matchedConditions)

        val imported = resolved(catalog, targets, "pkg", R2NodeResolutionMode.IMPORT)
        assertEquals("/packages/pkg/import.ts", imported.target.path)
        assertEquals(listOf("import"), imported.matchedConditions)

        val required = resolved(catalog, targets, "pkg", R2NodeResolutionMode.REQUIRE)
        assertEquals("/packages/pkg/require.ts", required.target.path)
        assertEquals(listOf("require"), required.matchedConditions)

        val typed = resolved(catalog, targets, "pkg", R2NodeResolutionMode.TYPES)
        assertEquals("/packages/pkg/index.d.ts", typed.target.path)
        assertEquals(listOf("types"), typed.matchedConditions)
    }

    @Test
    fun exportsUseExactThenMostSpecificPatternAndSupportScopedPackages() {
        val catalog = catalog(
            listOf("packages/@scope/pkg"),
            "package.json" to "{}",
            "packages/@scope/pkg/package.json" to
                (
                    "{\"exports\":{" +
                        "\"./feature/exact\":\"./exact.d.ts\"," +
                        "\"./feature/*\":\"./types/*.d.ts\"," +
                        "\"./feature/special/*\":\"./special/*.d.ts\"}}"
                    ),
        )
        val targets = listOf(
            declaration("/packages/@scope/pkg/exact.d.ts"),
            declaration("/packages/@scope/pkg/types/other.d.ts"),
            declaration("/packages/@scope/pkg/special/value.d.ts"),
        )

        assertEquals(
            "/packages/@scope/pkg/exact.d.ts",
            resolved(catalog, targets, "@scope/pkg/feature/exact").target.path,
        )
        val general = resolved(catalog, targets, "@scope/pkg/feature/other")
        assertEquals("/packages/@scope/pkg/types/other.d.ts", general.target.path)
        assertEquals("./feature/*", general.matchedKey)

        val specific = resolved(catalog, targets, "@scope/pkg/feature/special/value")
        assertEquals("/packages/@scope/pkg/special/value.d.ts", specific.target.path)
        assertEquals("./feature/special/*", specific.matchedKey)
    }

    @Test
    fun importsResolvePatternsAndUseTheNearestNestedPackageBoundary() {
        val catalog = catalog(
            listOf("packages/pkg"),
            "package.json" to """{"imports":{"#app/*":"./sources/*.ts"}}""",
            "packages/pkg/package.json" to """{"imports":{"#self":"./root.d.ts"}}""",
            "packages/pkg/nested/package.json" to
                """{"imports":{"#self":"./nested.d.ts"}}""",
        )
        val targets = listOf(
            source("/sources/util.ts"),
            declaration("/packages/pkg/root.d.ts"),
            declaration("/packages/pkg/nested/nested.d.ts"),
        )

        val project = resolved(
            catalog = catalog,
            targets = targets,
            specifier = "#app/util",
            containingPath = "/sources/main.ts",
        )
        assertEquals("/sources/util.ts", project.target.path)
        assertEquals("/", project.packageBoundary)
        assertEquals("#app/*", project.matchedKey)

        val root = resolved(
            catalog = catalog,
            targets = targets,
            specifier = "#self",
            containingPath = "/packages/pkg/file.d.ts",
        )
        assertEquals("/packages/pkg/root.d.ts", root.target.path)
        assertEquals("/packages/pkg", root.packageBoundary)

        val nested = resolved(
            catalog = catalog,
            targets = targets,
            specifier = "#self",
            containingPath = "/packages/pkg/nested/file.d.ts",
        )
        assertEquals("/packages/pkg/nested/nested.d.ts", nested.target.path)
        assertEquals("/packages/pkg/nested", nested.packageBoundary)
        assertEquals("packages/pkg/nested/package.json", nested.packageMetadataPath)
    }

    @Test
    fun importsMayDelegateToAnAllowedPackageButCyclesFailClosed() {
        val catalog = catalog(
            listOf("packages/pkg"),
            "package.json" to """{"imports":{"#dep":"pkg","#one":"#two","#two":"#one"}}""",
            "packages/pkg/package.json" to """{"exports":"./index.d.ts"}""",
        )
        val targets = listOf(declaration("/packages/pkg/index.d.ts"))

        val delegated = resolved(catalog, targets, "#dep")
        assertEquals("/packages/pkg/index.d.ts", delegated.target.path)
        assertEquals("packages/pkg/package.json", delegated.packageMetadataPath)

        assertFailure(
            R2NodeResolutionFailureCode.CYCLE,
            resolve(catalog, targets, "#one"),
        )
    }

    @Test
    fun exactRelativeTargetsNeverProbeOutsideTheProvidedInventory() {
        val catalog = catalog(emptyList(), "package.json" to "{}")
        val targets = listOf(
            source("/sources/nested/main.ts"),
            source("/sources/util.ts"),
        )

        val relative = resolved(
            catalog = catalog,
            targets = targets,
            specifier = "../util.ts",
            containingPath = "/sources/nested/main.ts",
        )
        assertEquals("/sources/util.ts", relative.target.path)
        assertEquals(emptyList<String>(), relative.matchedConditions)

        assertFailure(
            R2NodeResolutionFailureCode.TARGET_NOT_IN_SNAPSHOT,
            resolve(
                catalog = catalog,
                targets = targets,
                specifier = "./missing.ts",
                containingPath = "/sources/nested/main.ts",
            ),
        )
    }

    @Test
    fun missingAuthorityConditionsTargetsAndCaseHaveStableFailures() {
        val catalog = catalog(
            listOf("packages/empty", "packages/pkg"),
            "package.json" to "{}",
            "packages/empty/package.json" to "{}",
            "packages/pkg/package.json" to
                """{"exports":{".":{"browser":"./Browser.d.ts"},"./feature":"./feature.d.ts"}}""",
        )
        val targets = listOf(
            declaration("/packages/pkg/browser.d.ts"),
            declaration("/packages/pkg/feature.d.ts"),
        )

        assertFailure(
            R2NodeResolutionFailureCode.PACKAGE_NOT_ALLOWED,
            resolve(catalog, targets, "other"),
        )
        assertFailure(
            R2NodeResolutionFailureCode.EXPORTS_NOT_DEFINED,
            resolve(catalog, targets, "empty"),
        )
        assertFailure(
            R2NodeResolutionFailureCode.EXPORTS_NO_MATCH,
            resolve(catalog, targets, "pkg/unknown"),
        )
        assertFailure(
            R2NodeResolutionFailureCode.CONDITION_NO_MATCH,
            resolve(catalog, targets, "pkg"),
        )
        val caseFailure = assertFailure(
            R2NodeResolutionFailureCode.AMBIGUOUS_CASE,
            resolve(catalog, targets, "pkg", conditions = listOf("browser")),
        )
        assertEquals(".", caseFailure.matchedKey)
        assertEquals(listOf("browser"), caseFailure.matchedConditions)
    }

    @Test
    fun malformedBlockedEscapingAndUnsupportedTargetsFailClosed() {
        val malformed = catalog(
            listOf("packages/pkg"),
            "package.json" to "{}",
            "packages/pkg/package.json" to """{"exports":42}""",
        )
        assertFailure(
            R2NodeResolutionFailureCode.PACKAGE_METADATA_INVALID,
            resolve(malformed, emptyList(), "pkg"),
        )

        val malformedKey = catalog(
            listOf("packages/pkg"),
            "package.json" to "{}",
            "packages/pkg/package.json" to """{"exports":{"../escape":"./index.d.ts"}}""",
        )
        assertFailure(
            R2NodeResolutionFailureCode.PACKAGE_METADATA_INVALID,
            resolve(malformedKey, emptyList(), "pkg"),
        )

        val fallback = catalog(
            listOf("packages/pkg"),
            "package.json" to "{}",
            "packages/pkg/package.json" to
                """{"exports":["./missing.d.ts","./fallback.d.ts"]}""",
        )
        assertEquals(
            "/packages/pkg/fallback.d.ts",
            resolved(
                fallback,
                listOf(declaration("/packages/pkg/fallback.d.ts")),
                "pkg",
            ).target.path,
        )

        val blocked = catalog(
            listOf("packages/pkg"),
            "package.json" to "{}",
            "packages/pkg/package.json" to """{"exports":null}""",
        )
        assertFailure(
            R2NodeResolutionFailureCode.TARGET_BLOCKED,
            resolve(blocked, emptyList(), "pkg"),
        )

        val escaping = catalog(
            listOf("packages/pkg"),
            "package.json" to "{}",
            "packages/pkg/package.json" to """{"exports":"./../escape.d.ts"}""",
        )
        assertFailure(
            R2NodeResolutionFailureCode.TARGET_ESCAPE,
            resolve(escaping, emptyList(), "pkg"),
        )

        val unsupportedTarget = listOf(
            R2NodeResolutionTarget(
                path = "/packages/pkg/index.mts",
                kind = R2NodeResolutionTargetKind.PROJECT_SOURCE,
            ),
        )
        assertFailure(
            R2NodeResolutionFailureCode.UNSUPPORTED_SOURCE_KIND,
            resolve(blocked, unsupportedTarget, "pkg"),
        )
    }

    @Test
    fun onlyCanonicalEcmaScriptIntegerIndexConditionKeysFailClosed() {
        val canonicalMaximum = catalog(
            listOf("packages/pkg"),
            "package.json" to "{}",
            "packages/pkg/package.json" to
                """{"exports":{".":{"4294967294":"./wrong.ts","import":"./index.ts"}}}""",
        )
        assertFailure(
            R2NodeResolutionFailureCode.PACKAGE_METADATA_INVALID,
            resolve(canonicalMaximum, listOf(source("/packages/pkg/index.ts")), "pkg"),
        )

        listOf("4294967295", "99999999999999999999", "01").forEach { condition ->
            val nonIndex = catalog(
                listOf("packages/pkg"),
                "package.json" to "{}",
                "packages/pkg/package.json" to
                    """{"exports":{".":{"$condition":"./wrong.ts","import":"./index.ts"}}}""",
            )
            assertEquals(
                "/packages/pkg/index.ts",
                resolved(
                    nonIndex,
                    listOf(source("/packages/pkg/index.ts")),
                    "pkg",
                ).target.path,
            )
        }
    }

    @Test
    fun invalidRequestsAndTheFailureCatalogAreStable() {
        val catalog = catalog(emptyList(), "package.json" to "{}")
        assertFailure(
            R2NodeResolutionFailureCode.INVALID_CONTAINING_PATH,
            resolve(catalog, emptyList(), "#app", containingPath = "sources/main.ts"),
        )
        assertFailure(
            R2NodeResolutionFailureCode.INVALID_SPECIFIER,
            resolve(catalog, emptyList(), "bad\\specifier"),
        )
        assertFailure(
            R2NodeResolutionFailureCode.INVALID_CONDITION,
            resolve(catalog, emptyList(), "#app", conditions = listOf("custom", "custom")),
        )

        val values = R2NodeResolutionFailureCode.entries.map { code -> code.contractValue }
        assertEquals(
            listOf(
                "NODE_RESOLUTION_INVALID_CONTAINING_PATH",
                "NODE_RESOLUTION_INVALID_SPECIFIER",
                "NODE_RESOLUTION_INVALID_CONDITION",
                "NODE_RESOLUTION_AMBIGUOUS_CASE",
                "NODE_RESOLUTION_PACKAGE_NOT_ALLOWED",
                "NODE_RESOLUTION_PACKAGE_METADATA_MISSING",
                "NODE_RESOLUTION_PACKAGE_METADATA_INVALID",
                "NODE_RESOLUTION_IMPORTS_NOT_DEFINED",
                "NODE_RESOLUTION_IMPORTS_NO_MATCH",
                "NODE_RESOLUTION_EXPORTS_NOT_DEFINED",
                "NODE_RESOLUTION_EXPORTS_NO_MATCH",
                "NODE_RESOLUTION_CONDITION_NO_MATCH",
                "NODE_RESOLUTION_INVALID_TARGET",
                "NODE_RESOLUTION_TARGET_BLOCKED",
                "NODE_RESOLUTION_TARGET_ESCAPE",
                "NODE_RESOLUTION_TARGET_NOT_IN_SNAPSHOT",
                "NODE_RESOLUTION_UNSUPPORTED_SOURCE_KIND",
                "NODE_RESOLUTION_CYCLE",
            ),
            values,
        )
    }

    @Test
    fun sharedTypeScript603ParityFixtureFreezesTheClosedWorldSubset() {
        val bytes = requireNotNull(
            javaClass.getResourceAsStream("/r2-node-resolution/parity-v1.json"),
        ).use { input -> input.readBytes() }
        val fixture = R2StrictJson.parseObject(bytes)
        assertEquals(
            "autojs6.typescript.node-resolution-parity",
            fixture.requireString("schema"),
        )
        assertEquals(1, fixture.requireNumber("revision"))
        assertEquals("6.0.3", fixture.requireString("typescriptVersion"))
        assertEquals(
            "R2-M2-C6-B closed-world resolver parity; not complete R2-M2-C6",
            fixture.requireString("scope"),
        )

        var supported = 0
        var failClosed = 0
        val ids = LinkedHashSet<String>()
        val cases = fixture.requireList("cases")
        cases.forEach { value ->
            val testCase = value.requireObject("case")
            val id = testCase.requireString("id")
            assertTrue("Duplicate parity case: $id", ids.add(id))
            when (testCase.requireString("classification")) {
                "supported" -> supported += 1
                "fail-closed" -> failClosed += 1
                else -> fail("$id has an invalid parity classification.")
            }
            val metadata = testCase.requireList("metadata").map { metadataValue ->
                val record = metadataValue.requireObject("$id metadata")
                record.requireString("path") to record.requireString("json")
            }
            val catalog = catalog(
                testCase.requireStringList("dependencyRoots"),
                *metadata.toTypedArray(),
            )
            val targets = testCase.requireList("targets").map { targetValue ->
                val target = targetValue.requireObject("$id target")
                R2NodeResolutionTarget(
                    path = target.requireString("path"),
                    kind = when (target.requireString("kind")) {
                        "source" -> R2NodeResolutionTargetKind.PROJECT_SOURCE
                        "declaration" -> R2NodeResolutionTargetKind.DECLARATION
                        else -> error("$id has an invalid target kind.")
                    },
                )
            }
            val result = resolve(
                catalog = catalog,
                targets = targets,
                specifier = testCase.requireString("specifier"),
                mode = when (testCase.requireString("mode")) {
                    "import" -> R2NodeResolutionMode.IMPORT
                    "require" -> R2NodeResolutionMode.REQUIRE
                    else -> error("$id has an invalid resolution mode.")
                },
                conditions = testCase.requireStringList("customConditions"),
                containingPath = testCase.requireString("containingPath"),
            )
            val expected = testCase.requireObject("expectedR2")
            when (expected.requireString("kind")) {
                "resolved" -> {
                    assertTrue("$id expected a resolved target, got $result.", result is R2NodeResolutionResult.Resolved)
                    result as R2NodeResolutionResult.Resolved
                    assertEquals(id, expected.requireString("target"), result.target.path)
                    assertEquals(
                        id,
                        expected.requireString("packageBoundary"),
                        result.packageBoundary,
                    )
                    assertEquals(
                        id,
                        expected.requireString("packageMetadataPath"),
                        result.packageMetadataPath,
                    )
                    assertEquals(id, expected.requireString("matchedKey"), result.matchedKey)
                    assertEquals(
                        id,
                        expected.requireStringList("matchedConditions"),
                        result.matchedConditions,
                    )
                }
                "failure" -> {
                    val expectedCode = R2NodeResolutionFailureCode.entries.single { code ->
                        code.contractValue == expected.requireString("code")
                    }
                    assertFailure(expectedCode, result)
                }
                else -> fail("$id has an invalid R2 parity expectation.")
            }
        }

        assertEquals(18, cases.size)
        assertEquals(12, supported)
        assertEquals(6, failClosed)
        assertEquals(cases.size, ids.size)
    }

    private fun resolved(
        catalog: R2NodePackageMetadataCatalog,
        targets: List<R2NodeResolutionTarget>,
        specifier: String,
        mode: R2NodeResolutionMode = R2NodeResolutionMode.IMPORT,
        vararg conditions: String,
        containingPath: String = "/sources/main.ts",
    ): R2NodeResolutionResult.Resolved {
        val result = resolve(
            catalog = catalog,
            targets = targets,
            specifier = specifier,
            mode = mode,
            conditions = conditions.toList(),
            containingPath = containingPath,
        )
        if (result is R2NodeResolutionResult.Resolved) return result
        throw AssertionError("Expected resolution success, got $result.")
    }

    private fun resolve(
        catalog: R2NodePackageMetadataCatalog,
        targets: List<R2NodeResolutionTarget>,
        specifier: String,
        mode: R2NodeResolutionMode = R2NodeResolutionMode.IMPORT,
        conditions: List<String> = emptyList(),
        containingPath: String = "/sources/main.ts",
    ): R2NodeResolutionResult = R2NodeResolutionKernel.resolve(
        request = R2NodeResolutionRequest(
            containingPath = containingPath,
            specifier = specifier,
            mode = mode,
            conditions = conditions,
        ),
        packageMetadata = catalog,
        targets = targets,
    )

    private fun assertFailure(
        expected: R2NodeResolutionFailureCode,
        actual: R2NodeResolutionResult,
    ): R2NodeResolutionResult.Failure {
        if (actual is R2NodeResolutionResult.Failure) {
            assertEquals(expected, actual.code)
            return actual
        }
        fail("Expected $expected, got $actual.")
        error("unreachable")
    }

    private fun catalog(
        dependencyRoots: List<String>,
        vararg files: Pair<String, String>,
    ): R2NodePackageMetadataCatalog {
        val sortedFiles = files
            .map { (path, json) -> path to json.toByteArray(StandardCharsets.UTF_8) }
            .sortedBy { (path, _) -> path }
        return R2NodePackageMetadataCatalog.create(
            inventory = inventory(dependencyRoots, sortedFiles),
            packageMetadataBytes = sortedFiles.map { (_, bytes) -> bytes },
        )
    }

    private fun inventory(
        dependencyRoots: List<String>,
        files: List<Pair<String, ByteArray>>,
    ): R2PackageMetadataInventory = R2PackageMetadataInventory.create(
        dependencyRoots = dependencyRoots,
        files = files.map { (path, bytes) ->
            R2PackageMetadataFileDescriptor(
                path = path,
                size = bytes.size.toLong(),
                sha256 = R2Digests.sha256(bytes),
            )
        },
    )

    private fun source(path: String) = R2NodeResolutionTarget(
        path = path,
        kind = R2NodeResolutionTargetKind.PROJECT_SOURCE,
    )

    private fun declaration(path: String) = R2NodeResolutionTarget(
        path = path,
        kind = R2NodeResolutionTargetKind.DECLARATION,
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
        getValue(key) as Map<String, Any?>

    private fun Any?.requireObject(label: String): Map<String, Any?> {
        require(this is Map<*, *> && keys.all { key -> key is String }) {
            "$label must be an object."
        }
        @Suppress("UNCHECKED_CAST")
        return this as Map<String, Any?>
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        getValue(key) as? String ?: error("$key must be a string.")

    private fun Map<String, Any?>.requireNumber(key: String): Int =
        (getValue(key) as? R2StrictJson.NumberToken)
            ?.source
            ?.toIntOrNull()
            ?: error("$key must be a canonical Int.")

    private fun Map<String, Any?>.requireList(key: String): List<*> =
        getValue(key) as? List<*> ?: error("$key must be a list.")

    private fun Map<String, Any?>.requireStringList(key: String): List<String> =
        requireList(key).mapIndexed { index, value ->
            value as? String ?: error("$key[$index] must be a string.")
        }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.name}.", error)
        }
        fail("Expected ${T::class.java.name}.")
        error("unreachable")
    }
}
