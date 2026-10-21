package org.autojs.autojs.core.plugin.mail

import com.google.gson.JsonParser
import org.autojs.plugin.mail.api.MailContract
import org.autojs.plugin.mail.api.MailErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MailJsonTest {

    @Test
    fun requestRoundTripKeepsSupplementaryPlaneTextAndEmptyContainers() {
        val args = """{"subject":"😀 汉字 <b>&amp;</b>","tags":[],"nested":{"empty":{}},"n":null}"""
        val request = MailJson.encodeRequest("req-1", MailContract.OP_MESSAGES_LIST, args)
        assertEquals("req-1", request.id)
        assertEquals(MailContract.OP_MESSAGES_LIST, request.op)
        val decoded = JsonParser.parseString(request.json).asJsonObject
        assertEquals("req-1", decoded.get("id").asString)
        assertEquals("messages.list", decoded.get("op").asString)
        assertEquals(JsonParser.parseString(args), decoded.get("args"))
        assertEquals("😀 汉字 <b>&amp;</b>", decoded.getAsJsonObject("args").get("subject").asString)
        assertTrue(request.json.contains("😀"))
        assertFalse(request.json.contains("\\u003c"))
    }

    @Test
    fun requestWithoutArgsUsesAnEmptyObject() {
        listOf(null, "", "   ").forEach { args ->
            val request = MailJson.encodeRequest("id", MailContract.OP_SESSION_TEST, args)
            assertEquals("""{"id":"id","op":"session.test","args":{}}""", request.json)
        }
    }

    @Test
    fun requestArgumentsMustBeAnObjectOfAKnownOperation() {
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailJson.encodeRequest("id", MailContract.OP_SESSION_TEST, "[]") })
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailJson.encodeRequest("id", MailContract.OP_SESSION_TEST, "not json") })
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailJson.encodeRequest("id", MailContract.OP_SESSION_TEST, "42") })
        try {
            MailJson.encodeRequest("id", "messages.purge", null)
            fail("unknown op accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("messages.purge"))
        }
        try {
            MailJson.encodeRequest(" ", MailContract.OP_SESSION_TEST, null)
            fail("blank id accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun oversizedRequestIsRejectedBeforeItReachesTheBinder() {
        val huge = "x".repeat(MailContract.MAX_ENVELOPE_BYTES + 1)
        val error = errorOf { MailJson.encodeRequest("id", MailContract.OP_MAIL_SEND, """{"text":"$huge"}""") }
        assertEquals(MailErrorCodes.LIMIT_EXCEEDED, error.code)
        assertFalse(error.retryable)
        assertTrue(error.message.contains("request envelope"))
    }

    @Test
    fun okResponseCarriesTheResultText() {
        val response = MailJson.decodeResponse("""{"id":"7","ok":true,"result":{"a":[1,2],"s":"汉"}}""")
        assertEquals("7", response.id)
        assertTrue(response.ok)
        assertNull(response.error)
        assertEquals(JsonParser.parseString("""{"a":[1,2],"s":"汉"}"""), JsonParser.parseString(response.result!!))
        assertNull(MailJson.decodeResponse("""{"ok":true}""").result)
        assertNull(MailJson.decodeResponse("""{"ok":true,"result":null}""").result)
        assertEquals("[]", MailJson.decodeResponse("""{"ok":true,"result":[]}""").result)
        assertEquals("3", MailJson.decodeResponse("""{"ok":true,"result":3}""").result)
    }

    @Test
    fun errorResponseMapsCodeMessageDetailsAndRetryDefault() {
        val response = MailJson.decodeResponse("""{"id":"7","ok":false,"error":{"code":"CONNECT_FAILED","message":"refused","details":{"host":"imap.example.org"}}}""")
        assertFalse(response.ok)
        val error = response.error!!
        assertEquals(MailErrorCodes.CONNECT_FAILED, error.code)
        assertEquals("refused", error.message)
        assertTrue(error.retryable)
        assertEquals("""{"host":"imap.example.org"}""", error.details)

        val explicit = MailJson.decodeResponse("""{"ok":false,"error":{"code":"CONNECT_FAILED","message":"m","retryable":false}}""").error!!
        assertFalse(explicit.retryable)

        val unknown = MailJson.decodeResponse("""{"ok":false,"error":{"code":"WEIRD","message":"m"}}""").error!!
        assertEquals(MailErrorCodes.INTERNAL, unknown.code)
        assertEquals("unknown error code WEIRD: m", unknown.message)
        assertFalse(unknown.retryable)

        val missing = MailJson.decodeResponse("""{"ok":false}""").error!!
        assertEquals(MailErrorCodes.INTERNAL, missing.code)
        assertTrue(missing.message.contains("no error"))
    }

    @Test
    fun malformedResponsesAreInternalErrors() {
        listOf(null, "", "  ", "[]", "42", "{\"id\":\"1\"}", "{\"ok\":\"yes\"}", "{not json").forEach { json ->
            val error = errorOf { MailJson.decodeResponse(json) }
            assertEquals(json.toString(), MailErrorCodes.INTERNAL, error.code)
            assertFalse(error.retryable)
        }
    }

    @Test
    fun progressDefaultsMissingAndNegativeFields() {
        val progress = MailJson.decodeProgress("""{"id":"1","transferred":10}""")
        assertEquals("1", progress.id)
        assertEquals(10L, progress.transferred)
        assertNull(progress.total)
        assertEquals(0L, MailJson.decodeProgress("{}").transferred)
        assertNull(MailJson.decodeProgress("""{"transferred":1,"total":-1}""").total)
        assertEquals(20L, MailJson.decodeProgress("""{"transferred":1,"total":20}""").total)
        assertEquals(0L, MailJson.decodeProgress("""{"transferred":-5}""").transferred)
        assertEquals(0L, MailJson.decodeProgress("""{"transferred":"ten"}""").transferred)
        assertNull(progress.debug)
    }

    @Test
    fun progressCarriesTheDebugTraceLinesWhenPresent() {
        val traced = MailJson.decodeProgress("""{"transferred":0,"debug":["C: LOGIN alice ****",null,"S: A1 OK"]}""")
        assertEquals(listOf("C: LOGIN alice ****", "S: A1 OK"), traced.debug)
        assertNull(MailJson.decodeProgress("""{"transferred":0,"debug":[]}""").debug)
        assertNull(MailJson.decodeProgress("""{"transferred":0,"debug":"C: NOOP"}""").debug)
    }

    @Test
    fun debugOnlyPacketsDoNotResetTheTransferCounterButExplicitZeroStillCounts() {
        val debug = MailJson.decodeProgress("""{"id":"download","debug":["imap download complete"]}""")
        assertFalse(debug.hasTransfer)
        assertEquals(listOf("imap download complete"), debug.debug)
        assertFalse(MailJson.decodeProgress("{}").hasTransfer)
        assertTrue(MailJson.decodeProgress("""{"transferred":0}""").hasTransfer)
        assertTrue(MailJson.decodeProgress("""{"transferred":98304,"debug":["done"]}""").hasTransfer)
    }

    @Test
    fun eventDecodesOptionalFieldsAndKeepsTheDocument() {
        val json = """{"type":"message","seq":3,"generation":9,"folder":"INBOX","message":{"uid":5},"mode":"idle","reason":null,"error":{"code":"TIMEOUT"}}"""
        val event = MailJson.decodeEvent(json)
        assertEquals(MailContract.EVENT_MESSAGE, event.type)
        assertEquals(3L, event.seq)
        assertEquals(9L, event.generation)
        assertEquals("INBOX", event.folder)
        assertEquals("""{"uid":5}""", event.message)
        assertEquals(MailContract.WATCH_MODE_IDLE, event.mode)
        assertNull(event.reason)
        assertEquals(MailErrorCodes.TIMEOUT, event.error!!.code)
        assertTrue(event.error.retryable)
        assertEquals(JsonParser.parseString(json), JsonParser.parseString(event.json))

        val minimal = MailJson.decodeEvent("""{"type":"resync"}""")
        assertEquals(MailContract.EVENT_RESYNC, minimal.type)
        assertEquals(0L, minimal.seq)
        assertNull(minimal.folder)
        assertNull(minimal.message)
        assertNull(minimal.error)
        assertEquals(MailErrorCodes.INTERNAL, codeOf { MailJson.decodeEvent("""{"seq":1}""") })
        assertEquals(MailErrorCodes.INTERNAL, codeOf { MailJson.decodeEvent("""{"type":""}""") })
    }

    @Test
    fun statusDecodesStateReasonAndLastError() {
        val open = MailJson.decodeStatus("""{"state":"open"}""")
        assertEquals(MailContract.STATE_OPEN, open.state)
        assertNull(open.reason)
        assertNull(open.lastError)

        val closed = MailJson.decodeStatus("""{"state":"closed","reason":"idle-timeout","lastError":{"code":"AUTH_FAILED","message":"bad"}}""")
        assertEquals(MailContract.STATE_CLOSED, closed.state)
        assertEquals("idle-timeout", closed.reason)
        assertEquals(MailErrorCodes.AUTH_FAILED, closed.lastError!!.code)
        assertFalse(closed.lastError.retryable)
        assertEquals(MailErrorCodes.INTERNAL, codeOf { MailJson.decodeStatus("""{"state":"paused"}""") })
        assertEquals(MailErrorCodes.INTERNAL, codeOf { MailJson.decodeStatus("{}") })
    }

    @Test
    fun watchOptionsEncodeFolderModeIntervalAndGeneration() {
        val json = MailJson.encodeWatchOptions(MailJson.WatchOptions("INBOX", MailContract.WATCH_MODE_POLL, 30_000L), 42L)
        assertEquals(JsonParser.parseString("""{"folder":"INBOX","mode":"poll","pollIntervalMs":30000,"generation":42}"""), JsonParser.parseString(json))
        assertEquals(JsonParser.parseString("""{"folder":"INBOX","generation":1}"""), JsonParser.parseString(MailJson.encodeWatchOptions(MailJson.WatchOptions("INBOX", null, null), 1L)))
        assertEquals(
            JsonParser.parseString("""{"folder":"Archive","fetchBody":true,"generation":2}"""),
            JsonParser.parseString(MailJson.encodeWatchOptions(MailJson.WatchOptions("Archive", null, null, fetchBody = true), 2L)),
        )
        listOf(
            MailJson.WatchOptions("", null, null),
            MailJson.WatchOptions("INBOX", "push", null),
            MailJson.WatchOptions("INBOX", null, MailContract.MIN_POLL_INTERVAL_MS - 1),
        ).forEach { options ->
            try {
                MailJson.encodeWatchOptions(options, 1L)
                fail("$options accepted")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun requireObjectAcceptsObjectsOnly() {
        assertEquals("""{"a":1}""", MailJson.requireObject("""{"a":1}""", "account"))
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailJson.requireObject("[]", "account") })
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailJson.requireObject(null, "account") })
        assertEquals(MailErrorCodes.INVALID_ARGUMENT, codeOf { MailJson.requireObject("{", "account") })
    }

    private fun codeOf(block: () -> Unit): String = errorOf(block).code

    private fun errorOf(block: () -> Unit): MailError {
        try {
            block()
        } catch (e: MailPluginException) {
            return e.error
        }
        fail("expected MailPluginException")
        throw AssertionError()
    }
}
