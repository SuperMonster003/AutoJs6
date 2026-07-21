package org.autojs.autojs.ui.edit.editor.encoding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class EditorEncodingCodecTest {

    @Test
    fun strictUtf8DecoderRejectsMalformedAndTruncatedSequences() {
        val invalidSequences = listOf(
            byteArrayOf(0xC3.toByte(), 0x28),
            byteArrayOf(0xE2.toByte(), 0x82.toByte()),
            byteArrayOf(0xF0.toByte(), 0x28, 0x8C.toByte(), 0xBC.toByte()),
        )

        invalidSequences.forEachIndexed { index, bytes ->
            assertNull(
                "Invalid UTF-8 case $index must not be decoded with replacement characters",
                EditorEncodingCodec.decodeStrictOrNull(bytes, StandardCharsets.UTF_8),
            )
        }
    }

    @Test
    fun strictEncoderRejectsCharactersThatTargetCharsetCannotRepresent() {
        val text = "ASCII plus 汉字 and 🙂"

        assertNull(EditorEncodingCodec.encodeStrictOrNull(text, StandardCharsets.US_ASCII))
        assertThrows(CharacterCodingException::class.java) {
            EditorEncodingCodec.encodeStrict(text, StandardCharsets.US_ASCII)
        }
    }

    @Test
    fun strictLegacyRoundTripPreservesRepresentableText() {
        val text = "café déjà vu"

        val encoded = EditorEncodingCodec.encodeStrict(text, StandardCharsets.ISO_8859_1)
        val decoded = EditorEncodingCodec.decodeStrictOrNull(encoded, StandardCharsets.ISO_8859_1)

        assertEquals(text, decoded)
        assertArrayEquals(encoded, EditorEncodingCodec.encodeStrict(decoded!!, StandardCharsets.ISO_8859_1))
    }

    @Test
    fun representableLegacyCharacterCanStillFailRoundTrip() {
        val shiftJis = Charset.forName("Shift_JIS")

        assertTrue(EditorEncodingCodec.canEncode("¥", shiftJis))
        assertFalse(EditorEncodingCodec.isRoundTripSafe("¥", shiftJis))
    }

    @Test
    fun boundedTextSampleNeverSplitsSurrogatePair() {
        val text = "a".repeat(7) + "🙂tail"

        val sample = EditorEncodingCodec.takeCodePointSafePrefix(text, 8)

        assertEquals("a".repeat(7), sample)
        assertTrue(EditorEncodingCodec.canEncode(sample, StandardCharsets.UTF_8))
    }

    @Test
    fun encodedDigestAddsBomOnlyForSupportedCharsets() {
        val text = "AutoJs6"
        val utf8WithoutBom = EditorEncodingCodec.encodedDigest(
            text = text,
            charset = StandardCharsets.UTF_8,
            includeBom = false,
        )
        val utf8WithBom = EditorEncodingCodec.encodedDigest(
            text = text,
            charset = StandardCharsets.UTF_8,
            includeBom = true,
        )

        assertEquals(utf8WithoutBom.byteCount + 3, utf8WithBom.byteCount)
        assertFalse(utf8WithoutBom.sha256 == utf8WithBom.sha256)

        val latin1WithoutBom = EditorEncodingCodec.encodedDigest(
            text = text,
            charset = StandardCharsets.ISO_8859_1,
            includeBom = false,
        )
        val latin1WithBomRequested = EditorEncodingCodec.encodedDigest(
            text = text,
            charset = StandardCharsets.ISO_8859_1,
            includeBom = true,
        )

        assertEquals(latin1WithoutBom, latin1WithBomRequested)
    }
}
