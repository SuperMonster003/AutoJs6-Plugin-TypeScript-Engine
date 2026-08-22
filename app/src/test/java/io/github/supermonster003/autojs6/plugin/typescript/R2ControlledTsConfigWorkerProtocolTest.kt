package io.github.supermonster003.autojs6.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class R2ControlledTsConfigWorkerProtocolTest {

    @Test
    fun typedConfigErrorPreservesBoundedPolicyIdentity() {
        R2ControlledTsConfigPolicy.policyCodes.forEach { policyCode ->
            val parsed = CompilerHostProtocol.parseMessage(
                configErrorMessage(policyCode = policyCode),
            ) as CompilerHostMessage.ProjectConfigError

            assertEquals("controlled-config", parsed.requestId)
            assertEquals(7, parsed.generation)
            assertEquals(policyCode, parsed.policyCode)
            assertEquals("Controlled tsconfig was rejected.", parsed.message)
        }
    }

    @Test
    fun typedConfigErrorRejectsShapeAndInvariantDrift() {
        val valid = configErrorMessage()
        val invalidMessages = listOf(
            valid.replace(
                "\"stage\":\"config\"",
                "\"stage\":\"type_check\"",
            ),
            valid.replace(
                "\"outcome\":\"diagnostics\"",
                "\"outcome\":\"failed\"",
            ),
            valid.replace(
                TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS,
                TypeScriptCompilerContract.ERROR_COMPILER_UNAVAILABLE,
            ),
            valid.replace("\"emittedModuleCount\":0", "\"emittedModuleCount\":1"),
            valid.replace(
                "CONFIG_SYNTAX_INVALID",
                "CONFIG_NOT_A_FROZEN_POLICY_CODE",
            ),
            valid.removeSuffix("}") + ",\"unknown\":true}",
            valid.replace(
                "\"policyCode\":\"CONFIG_SYNTAX_INVALID\"",
                "\"policyCode\":\"CONFIG_SYNTAX_INVALID\"," +
                    "\"policyCode\":\"CONFIG_PATH_INVALID\"",
            ),
            configErrorMessage(message = "x".repeat(4_097)),
        )

        invalidMessages.forEachIndexed { index, message ->
            assertFails("Invalid typed config error $index was accepted.") {
                CompilerHostProtocol.parseMessage(message)
            }
        }
    }

    @Test
    fun typedConfigErrorRequiresStrictJsonIntegerAndPageIsolation() {
        assertFails("Fractional module count was accepted.") {
            CompilerHostProtocol.parseMessage(
                configErrorMessage().replace(
                    "\"emittedModuleCount\":0",
                    "\"emittedModuleCount\":0.0",
                ),
            )
        }
        assertFails("TypeScript page-scope escape was accepted.") {
            CompilerHostProtocol.parseMessage(
                configErrorMessage().replace(
                    "\"pageHasTypeScript\":false",
                    "\"pageHasTypeScript\":true",
                ),
            )
        }
    }

    private fun configErrorMessage(
        policyCode: String = "CONFIG_SYNTAX_INVALID",
        message: String = "Controlled tsconfig was rejected.",
    ): String =
        """
        {
          "kind":"projectConfigError",
          "requestId":"controlled-config",
          "stage":"config",
          "outcome":"diagnostics",
          "errorCode":"${TypeScriptCompilerContract.ERROR_COMPILATION_DIAGNOSTICS}",
          "policyCode":"$policyCode",
          "emittedModuleCount":0,
          "message":"$message",
          "generation":7,
          "pageHasTypeScript":false
        }
        """.trimIndent()

    private fun assertFails(
        message: String,
        operation: () -> Unit,
    ) {
        var failed = false
        try {
            operation()
        } catch (_: Throwable) {
            failed = true
        }
        if (!failed) fail(message)
        assertTrue(failed)
    }
}
