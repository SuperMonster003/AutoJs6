package org.autojs.plugin.jvmsource.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JvmToastPayloadTest {
    @Test
    fun canonicalPayloadRoundTripsUnicodeQuotesAndControls() {
        val message = "M5 你好 \"quoted\" \\ line\nnext\tend"
        val encoded = JvmToastPayload.encode(message)

        assertEquals(message, JvmToastPayload.decode(encoded))
        assertEquals("{\"message\":\"M5 你好 \\\"quoted\\\" \\\\ line\\nnext\\tend\"}", encoded)
    }

    @Test
    fun rejectsEmptyOversizedMalformedAndNonCanonicalPayloads() {
        assertThrows(IllegalArgumentException::class.java) { JvmToastPayload.encode("") }
        assertThrows(IllegalArgumentException::class.java) {
            JvmToastPayload.encode("x".repeat(JvmSourceContract.MAX_TOAST_MESSAGE_BYTES + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JvmToastPayload.encode("\ud800")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JvmToastPayload.decode("{\"message\":\"\\u0041\"}")
        }
        assertThrows(IllegalArgumentException::class.java) {
            JvmToastPayload.decode("{\"message\":\"safe\",\"extra\":true}")
        }
    }
}
