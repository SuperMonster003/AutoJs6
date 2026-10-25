package org.autojs.autojs.runtime.api.modules.base64

import org.autojs.autojs.runtime.api.augment.base64.Base64
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mozilla.javascript.NativeArray
import java.nio.charset.StandardCharsets

class Base64Test {

    @Test
    fun `decode input bytes are independent from output charset`() {
        val base64Text = "5L2g5aW9"

        assertArrayEquals(
            base64Text.toByteArray(StandardCharsets.US_ASCII),
            Base64.toBase64InputBytes(base64Text),
        )
        assertEquals(StandardCharsets.UTF_16, Base64.parseEncoding("UTF-16"))
    }

    @Test
    fun `decode preserves byte and native array inputs`() {
        val bytes = byteArrayOf(81, 85, 74, 68)
        val nativeArray = NativeArray(arrayOf<Any?>(81, 85, 74, 68))

        assertSame(bytes, Base64.toBase64InputBytes(bytes))
        assertArrayEquals(bytes, Base64.toBase64InputBytes(nativeArray))
    }

    @Test
    fun `charset lookup remains punctuation tolerant but rejects unknown names`() {
        assertEquals(StandardCharsets.UTF_8, Base64.parseEncoding(" UTF_8 "))
        assertThrows(WrappedIllegalArgumentException::class.java) {
            Base64.parseEncoding("definitely-not-a-charset")
        }
    }
}
