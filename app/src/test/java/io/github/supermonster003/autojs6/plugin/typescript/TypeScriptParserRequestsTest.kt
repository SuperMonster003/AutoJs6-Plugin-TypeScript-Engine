package io.github.supermonster003.autojs6.plugin.typescript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.autojs.plugin.typescript.parser.TypeScriptParserContract as Contract

class TypeScriptParserRequestsTest {

    private fun request(vararg overrides: Pair<String, Any?>): Map<String, Any?> = mapOf(
        Contract.KEY_CONTRACT_VERSION to Contract.VERSION,
        Contract.KEY_REQUEST_ID to "request-1",
        Contract.KEY_FILE_NAME to "counter.tsx",
        Contract.KEY_SOURCE_TEXT to "compose.mount(<compose.Text text='x' />);",
    ) + overrides

    private fun check(values: Map<String, Any?>) = TypeScriptParserRequests.check { key -> values[key] }

    private fun rejectedCode(values: Map<String, Any?>): String =
        (check(values) as ParserRequestCheck.Rejected).code

    @Test
    fun aCompleteRequestIsAcceptedWithTheDefaultTimeout() {
        val valid = (check(request()) as ParserRequestCheck.Valid).request
        assertEquals("request-1", valid.requestId)
        assertEquals("counter.tsx", valid.fileName)
        assertEquals(Contract.DEFAULT_TIMEOUT_MS, valid.timeoutMs)
        assertEquals(5_000L, (check(request(Contract.KEY_TIMEOUT_MS to 5_000)) as ParserRequestCheck.Valid).request.timeoutMs)
        // Unknown keys are left to later revisions.
        assertTrue(check(request("futureOption" to true)) is ParserRequestCheck.Valid)
    }

    @Test
    fun theContractVersionIsCheckedFirst() {
        assertEquals(Contract.ERROR_CONTRACT_MISMATCH, rejectedCode(request(Contract.KEY_CONTRACT_VERSION to 2)))
        assertEquals(Contract.ERROR_CONTRACT_MISMATCH, rejectedCode(request(Contract.KEY_CONTRACT_VERSION to null, Contract.KEY_REQUEST_ID to null)))
    }

    @Test
    fun malformedRequestsAreInvalid() {
        val invalid = listOf(
            request(Contract.KEY_REQUEST_ID to ""),
            request(Contract.KEY_REQUEST_ID to "x".repeat(Contract.MAX_REQUEST_ID_CHARS + 1)),
            request(Contract.KEY_FILE_NAME to "src/counter.tsx"),
            request(Contract.KEY_FILE_NAME to "counter.vue"),
            request(Contract.KEY_FILE_NAME to "a\u0001.tsx"),
            request(Contract.KEY_SOURCE_TEXT to null),
            request(Contract.KEY_SOURCE_TEXT to "x".repeat(Contract.MAX_SOURCE_CHARS + 1)),
            request(Contract.KEY_TIMEOUT_MS to Contract.MIN_TIMEOUT_MS - 1),
            request(Contract.KEY_TIMEOUT_MS to Contract.MAX_TIMEOUT_MS + 1),
            request(Contract.KEY_TIMEOUT_MS to "15000"),
        )
        invalid.forEach { values -> assertEquals(values.toString().take(80), Contract.ERROR_INVALID_REQUEST, rejectedCode(values)) }
        assertTrue(check(request(Contract.KEY_FILE_NAME to "Counter.TSX")) is ParserRequestCheck.Valid)
        assertTrue(check(request(Contract.KEY_SOURCE_TEXT to "")) is ParserRequestCheck.Valid)
    }

    @Test
    fun framesCoverTheTextAndNeverSplitASurrogatePair() {
        assertEquals(listOf(""), TypeScriptParserRequests.frames("", 4))
        assertEquals(listOf("abcd", "ef"), TypeScriptParserRequests.frames("abcdef", 4))
        val emoji = "😀"
        val text = "abc" + emoji + "def"
        val frames = TypeScriptParserRequests.frames(text, 4)
        assertEquals(text, frames.joinToString(""))
        frames.dropLast(1).forEach { frame -> assertFalse(frame, Character.isHighSurrogate(frame.last())) }
        assertTrue(frames.all { it.length <= 4 })
    }
}
