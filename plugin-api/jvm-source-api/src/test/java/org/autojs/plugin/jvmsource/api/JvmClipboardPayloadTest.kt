package org.autojs.plugin.jvmsource.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JvmClipboardPayloadTest {
    @Test
    fun canonicalTextRoundTripsEmptyUnicodeQuotesAndControls() {
        assertEquals("{\"text\":\"\"}", JvmClipboardPayload.encodeText(""))
        val text = "M9 你好 \"quoted\" \\ line\nnext\tend"
        val encoded = JvmClipboardPayload.encodeText(text)

        assertEquals(text, JvmClipboardPayload.decodeText(encoded))
        assertEquals("{\"text\":\"M9 你好 \\\"quoted\\\" \\\\ line\\nnext\\tend\"}", encoded)
    }

    @Test
    fun exactTextLimitIsAcceptedAndNextByteIsRejected() {
        val exact = "x".repeat(JvmSourceContract.MAX_CLIPBOARD_TEXT_BYTES)

        assertEquals(exact, JvmClipboardPayload.decodeText(JvmClipboardPayload.encodeText(exact)))
        assertThrows(IllegalArgumentException::class.java) {
            JvmClipboardPayload.encodeText("x".repeat(JvmSourceContract.MAX_CLIPBOARD_TEXT_BYTES + 1))
        }
    }

    @Test
    fun rejectsMalformedUnicodeAndNonCanonicalAuthority() {
        assertThrows(IllegalArgumentException::class.java) { JvmClipboardPayload.encodeText("\ud800") }
        listOf(
            "{\"text\":\"\\u0041\"}",
            "{\"text\":\"safe\",\"text\":\"other\"}",
            "{\"text\":\"safe\",\"extra\":true}",
            "{ \"text\":\"safe\" }",
        ).forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) {
                JvmClipboardPayload.decodeText(payload)
            }
        }
    }

    @Test
    fun getRequestAcceptsOnlyCanonicalEmptyObject() {
        JvmClipboardPayload.validateGetRequest(JvmClipboardPayload.GET_REQUEST_JSON)
        listOf("", "{ }", "[]", "{\"extra\":true}").forEach { payload ->
            assertThrows(IllegalArgumentException::class.java) {
                JvmClipboardPayload.validateGetRequest(payload)
            }
        }
    }
}
