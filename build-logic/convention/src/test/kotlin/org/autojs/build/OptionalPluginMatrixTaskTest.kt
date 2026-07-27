package org.autojs.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OptionalPluginMatrixTaskTest {

    @Test
    fun `matrix uses four-state Gray code and finishes with Ace only`() {
        assertEquals(
            listOf(
                OptionalPluginMatrixState("none", false, false),
                OptionalPluginMatrixState("node-only", true, false),
                OptionalPluginMatrixState("node+ace", true, true),
                OptionalPluginMatrixState("ace-only", false, true),
            ),
            optionalPluginMatrixStates,
        )
        optionalPluginMatrixStates.zipWithNext().forEach { (first, second) ->
            val changes =
                (if (first.nodeInstalled != second.nodeInstalled) 1 else 0) +
                    (if (first.aceInstalled != second.aceInstalled) 1 else 0)
            assertEquals(1, changes)
        }
    }

    @Test
    fun `aapt2 package parser accepts one canonical package`() {
        assertEquals(
            "io.github.supermonster003.autojs6.plugin.nodejs",
            parseAapt2PackageName(
                "io.github.supermonster003.autojs6.plugin.nodejs\r\n",
            ),
        )
    }

    @Test
    fun `aapt2 package parser rejects extra output`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseAapt2PackageName("warning\nio.github.example.plugin\n")
        }
    }

    @Test
    fun `instrumentation parser requires one successful test and terminal code`() {
        requireSuccessfulOptionalPluginInstrumentation(
            """
            Time: 1.234

            OK (1 test)

            INSTRUMENTATION_CODE: -1
            """.trimIndent(),
        )
    }

    @Test
    fun `instrumentation parser rejects shell-success test failure`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSuccessfulOptionalPluginInstrumentation(
                """
                FAILURES!!!
                Tests run: 1,  Failures: 1
                INSTRUMENTATION_CODE: -1
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `instrumentation parser rejects zero tests`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSuccessfulOptionalPluginInstrumentation(
                """
                OK (0 tests)
                INSTRUMENTATION_CODE: -1
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `instrumentation parser rejects a later non-success terminal code`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSuccessfulOptionalPluginInstrumentation(
                """
                OK (1 test)
                INSTRUMENTATION_CODE: -1
                INSTRUMENTATION_CODE: 0
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `instrumentation parser rejects duplicate success lines`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireSuccessfulOptionalPluginInstrumentation(
                """
                OK (1 test)
                OK (1 test)
                INSTRUMENTATION_CODE: -1
                """.trimIndent(),
            )
        }
    }
}
