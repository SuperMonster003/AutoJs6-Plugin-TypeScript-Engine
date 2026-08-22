package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R2DiagnosticStopEmitFixtureTest {

    @Test
    fun fixtureFreezesFourErrorPhasesAndOneNineFieldDiagnosticModel() {
        val root = loadFixture()

        assertEquals(ROOT_KEYS, root.keys.toList())
        assertEquals("autojs6.typescript.r2-m2-x2.unified-no-emit-diagnostics", root.string("schema"))
        assertEquals(1, root.int("revision"))
        assertEquals("6.0.3", root.string("typescriptVersion"))
        assertEquals("desktop-worker-only/no-production-hook", root.string("scope"))
        assertEquals(DIAGNOSTIC_FIELDS, root.stringList("diagnosticFields"))
        assertEquals(PHASE_PRECEDENCE, root.stringList("phasePrecedence"))

        val execution = root.objectValue("execution")
        assertEquals(EXECUTION_KEYS, execution.keys.toList())
        assertEquals("createIncrementalProgram", execution.string("compilerApi"))
        assertEquals(
            "Proxy(createIncrementalProgram/getProgram); test-only; no Worker hook",
            execution.string("desktopInjection"),
        )
        assertEquals(1, execution.int("errorCategory"))
        assertEquals(0, execution.int("warningCategory"))
        assertEquals(0, execution.int("expectedErrorBuilderEmitCalls"))
        assertEquals(1, execution.int("expectedCleanBuilderEmitCalls"))
        assertEquals(0, execution.int("expectedEmittedJavaScriptExecutions"))

        val errorCases = root.objectList("errorCases")
        assertEquals(PHASE_PRECEDENCE.map { phase -> "$phase-error" }, errorCases.map { it.string("id") })
        assertEquals(PHASE_PRECEDENCE, errorCases.map { it.string("phase") })
        assertEquals(
            listOf(
                "desktop-only-proxy-injection",
                "real-typescript-source",
                "desktop-only-proxy-injection",
                "real-typescript-source",
            ),
            errorCases.map { it.string("origin") },
        )
        for (fixtureCase in errorCases) {
            assertEquals(ERROR_CASE_KEYS, fixtureCase.keys.toList())
            assertTrue(fixtureCase.string("mainSource").isNotEmpty())
            assertTrue(fixtureCase.string("cleanSuccessorRequestId").startsWith("desktop-x2-"))
            assertErrorResult(fixtureCase.objectValue("expected"))
            assertDiagnostic(
                diagnostic = fixtureCase.objectValue("diagnostic"),
                expectedPhase = fixtureCase.string("phase"),
                expectedCategory = execution.int("errorCategory"),
            )
        }

        for (phase in listOf("options", "global")) {
            val diagnostic = errorCases.single { it.string("phase") == phase }.objectValue("diagnostic")
            assertLocationIsExplicitlyNull(diagnostic)
        }
    }

    @Test
    fun fixtureFreezesFirstPhaseDeduplicationAndWarningPassThrough() {
        val root = loadFixture()

        val duplicate = root.objectValue("duplicatePrecedenceCase")
        assertEquals(DUPLICATE_KEYS, duplicate.keys.toList())
        assertEquals("identical-diagnostic-phase-precedence", duplicate.string("id"))
        assertEquals(PHASE_PRECEDENCE, duplicate.stringList("injectedPhases"))
        assertTrue(duplicate.string("mainSource").isNotEmpty())
        assertErrorResult(duplicate.objectValue("expected"))
        assertDiagnostic(duplicate.objectValue("diagnostic"), "options", 1)
        assertLocationIsExplicitlyNull(duplicate.objectValue("diagnostic"))

        val warning = root.objectValue("warningCase")
        assertEquals(WARNING_KEYS, warning.keys.toList())
        assertEquals("warning-does-not-block-emit", warning.string("id"))
        assertEquals("options", warning.string("phase"))
        assertEquals("desktop-only-typescript-category-probe", warning.string("origin"))
        assertTrue(warning.string("mainSource").isNotEmpty())
        assertDiagnostic(warning.objectValue("diagnostic"), "options", 0)
        assertLocationIsExplicitlyNull(warning.objectValue("diagnostic"))
        val expected = warning.objectValue("expected")
        assertEquals(WARNING_EXPECTED_KEYS, expected.keys.toList())
        assertEquals(1, expected.int("builderEmitCalls"))
        assertFalse(expected.boolean("emitSkipped"))
        assertEquals(4, expected.int("outputCount"))

        val successorIds = root.objectList("errorCases")
            .map { it.string("cleanSuccessorRequestId") } +
            duplicate.string("cleanSuccessorRequestId") +
            warning.string("cleanSuccessorRequestId")
        assertEquals(successorIds.size, successorIds.distinct().size)
    }

    private fun assertErrorResult(expected: Map<String, Any?>) {
        assertEquals(ERROR_EXPECTED_KEYS, expected.keys.toList())
        assertEquals(0, expected.int("builderEmitCalls"))
        assertTrue(expected.boolean("emitSkipped"))
        assertTrue(expected.listValue("outputs").isEmpty())
    }

    private fun assertDiagnostic(
        diagnostic: Map<String, Any?>,
        expectedPhase: String,
        expectedCategory: Int,
    ) {
        assertEquals(DIAGNOSTIC_FIELDS, diagnostic.keys.toList())
        assertEquals(expectedPhase, diagnostic.string("phase"))
        assertEquals(expectedCategory, diagnostic.int("category"))
        assertTrue(diagnostic.int("code") > 0)
        assertTrue(diagnostic.string("message").isNotEmpty())
        for (name in listOf("sourcePath", "start", "length", "line", "column")) {
            assertTrue(diagnostic.containsKey(name))
        }
    }

    private fun assertLocationIsExplicitlyNull(diagnostic: Map<String, Any?>) {
        assertNull(diagnostic["sourcePath"])
        assertNull(diagnostic["start"])
        assertNull(diagnostic["length"])
        assertNull(diagnostic["line"])
        assertNull(diagnostic["column"])
    }

    private fun loadFixture(): Map<String, Any?> = R2StrictJson.parseObject(
        requireNotNull(javaClass.getResourceAsStream("/r2-compiler-diagnostics/x2-v1.json"))
            .use { input -> input.readBytes() },
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
        val ROOT_KEYS = listOf(
            "schema",
            "revision",
            "typescriptVersion",
            "scope",
            "diagnosticFields",
            "phasePrecedence",
            "execution",
            "errorCases",
            "duplicatePrecedenceCase",
            "warningCase",
        )
        val DIAGNOSTIC_FIELDS = listOf(
            "phase",
            "category",
            "code",
            "message",
            "sourcePath",
            "start",
            "length",
            "line",
            "column",
        )
        val PHASE_PRECEDENCE = listOf("options", "syntactic", "global", "semantic")
        val EXECUTION_KEYS = listOf(
            "compilerApi",
            "desktopInjection",
            "errorCategory",
            "warningCategory",
            "expectedErrorBuilderEmitCalls",
            "expectedCleanBuilderEmitCalls",
            "expectedEmittedJavaScriptExecutions",
        )
        val ERROR_CASE_KEYS = listOf(
            "id",
            "phase",
            "origin",
            "mainSource",
            "diagnostic",
            "expected",
            "cleanSuccessorRequestId",
        )
        val DUPLICATE_KEYS = listOf(
            "id",
            "injectedPhases",
            "mainSource",
            "diagnostic",
            "expected",
            "cleanSuccessorRequestId",
        )
        val WARNING_KEYS = listOf(
            "id",
            "phase",
            "origin",
            "mainSource",
            "diagnostic",
            "expected",
            "cleanSuccessorRequestId",
        )
        val ERROR_EXPECTED_KEYS = listOf("builderEmitCalls", "emitSkipped", "outputs")
        val WARNING_EXPECTED_KEYS = listOf("builderEmitCalls", "emitSkipped", "outputCount")
    }
}
