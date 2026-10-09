package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class R2DeclarationWarmUpProtocolTest {

    @Test
    fun parsesTheWorkerReportInLogOrder() {
        val parsed = CompilerHostProtocol.parseMessage(
            warmUpMessage(
                state = "completed",
                fields = "\"sinceReadyMs\":512,\"startedAtMs\":1,\"loadedAtMs\":298," +
                    "\"completedAtMs\":489,\"commonImportMs\":95,\"commonVerifyMs\":18," +
                    "\"rhinoImportMs\":18,\"rhinoVerifyMs\":4," +
                    "\"parsedFiles\":83,\"parsedBytes\":1536000,\"queuedFiles\":0," +
                    "\"parseMs\":160,\"steps\":14,\"deferredSteps\":0",
            ),
        ) as CompilerHostMessage.DeclarationWarmUp

        assertEquals(3, parsed.generation)
        assertEquals("completed", parsed.state)
        assertEquals(
            CompilerHostProtocol.DECLARATION_WARM_UP_FIELDS,
            parsed.fields.keys.toList(),
        )
        assertEquals(512L, parsed.fields["sinceReadyMs"])
        assertEquals(489L, parsed.fields["completedAtMs"])
        assertEquals(18L, parsed.fields["commonVerifyMs"])
        assertEquals(83L, parsed.fields["parsedFiles"])
        assertEquals(1_536_000L, parsed.fields["parsedBytes"])
        assertEquals(0L, parsed.fields["deferredSteps"])
    }

    @Test
    fun reportsUnknownStatesAndMalformedValuesAsUnobserved() {
        val parsed = CompilerHostProtocol.parseMessage(
            warmUpMessage(
                state = "mystery",
                fields = "\"sinceReadyMs\":-1,\"loadedAtMs\":\"soon\",\"parsedFiles\":12",
            ),
        ) as CompilerHostMessage.DeclarationWarmUp

        assertEquals("na", parsed.state)
        assertNull(parsed.fields["sinceReadyMs"])
        assertNull(parsed.fields["loadedAtMs"])
        assertNull(parsed.fields["completedAtMs"])
        assertEquals(12L, parsed.fields["parsedFiles"])
        assertEquals(
            CompilerHostProtocol.DECLARATION_WARM_UP_FIELDS.size,
            parsed.fields.size,
        )
    }

    @Test
    fun keepsWorkerIsolationAndGenerationChecks() {
        val valid = warmUpMessage(state = "parsing", fields = "\"parsedFiles\":4")
        assertFails("TypeScript page-scope escape was accepted.") {
            CompilerHostProtocol.parseMessage(
                valid.replace("\"pageHasTypeScript\":false", "\"pageHasTypeScript\":true"),
            )
        }
        assertFails("A missing Worker generation was accepted.") {
            CompilerHostProtocol.parseMessage(valid.replace(",\"generation\":3", ""))
        }
        assertFails("A zero Worker generation was accepted.") {
            CompilerHostProtocol.parseMessage(valid.replace("\"generation\":3", "\"generation\":0"))
        }
    }

    private fun warmUpMessage(state: String, fields: String): String =
        "{\"kind\":\"declarationWarmUp\",\"state\":\"$state\",$fields," +
            "\"generation\":3,\"pageHasTypeScript\":false}"

    private fun assertFails(message: String, block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            return
        }
        fail(message)
    }
}
