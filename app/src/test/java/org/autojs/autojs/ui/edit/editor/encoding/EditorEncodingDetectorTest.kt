package org.autojs.autojs.ui.edit.editor.encoding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class EditorEncodingDetectorTest {

    @Test
    fun asciiIsRecognizedWithoutConsultingLocaleHints() {
        val bytes = "plain ASCII\n".toByteArray(StandardCharsets.US_ASCII)

        val detection = EditorEncodingDetector.detect(
            bytes = bytes,
            preferredCharsetNames = listOf("GB18030", "Big5"),
            localeTag = "zh-CN",
        )

        assertEquals(StandardCharsets.UTF_8, detection.charset)
        assertEquals(EditorEncodingDetectionSource.ASCII, detection.source)
        assertFalse(detection.hadBom)
        assertEquals(50, detection.confidence)
    }

    @Test
    fun emptyFileUsesUtf8WithoutPretendingThereIsEncodingEvidence() {
        val detection = EditorEncodingDetector.detect(ByteArray(0))

        assertEquals(StandardCharsets.UTF_8, detection.charset)
        assertEquals(EditorEncodingDetectionSource.ASCII, detection.source)
        assertEquals(0, detection.confidence)
    }

    @Test
    fun multilingualUtf8IsRecognizedStrictly() {
        val text = "AutoJs6: 编码, café, 🙂"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val detection = EditorEncodingDetector.detect(
            bytes = bytes,
            preferredCharsetNames = listOf("GB18030"),
            localeTag = "zh-CN",
        )

        assertEquals(StandardCharsets.UTF_8, detection.charset)
        assertEquals(EditorEncodingDetectionSource.UTF8, detection.source)
        assertFalse(detection.hadBom)
        assertEquals(text, EditorEncodingCodec.decodeStrictOrNull(bytes, detection.charset))
        assertTrue(detection.confidence in 0..100)
    }

    @Test
    fun localeEvidenceBeatsWeakSingleByteGuessForShortGb18030Text() {
        assumeTrue("Host JVM does not support GB18030", Charset.isSupported("GB18030"))
        val charset = Charset.forName("GB18030")
        val text = "简体中文编码测试"
        val bytes = text.toByteArray(charset)

        val detection = EditorEncodingDetector.detect(
            bytes = bytes,
            preferredCharsetNames = listOf("GB18030", "GBK", "Big5"),
            localeTag = "zh-CN",
        )

        assertEquals(text, EditorEncodingCodec.decodeStrictOrNull(bytes, detection.charset))
        assertEquals(EditorEncodingDetectionSource.ICU, detection.source)
    }

    @Test
    fun localeEvidenceBeatsLatinGuessForShortBig5Text() {
        assumeTrue("Host JVM does not support Big5", Charset.isSupported("Big5"))
        val charset = Charset.forName("Big5")
        val text = "繁體中文編碼測試"
        val bytes = text.toByteArray(charset)

        val detection = EditorEncodingDetector.detect(
            bytes = bytes,
            preferredCharsetNames = listOf("Big5", "GB18030"),
            localeTag = "zh-TW",
        )

        assertEquals(text, EditorEncodingCodec.decodeStrictOrNull(bytes, detection.charset))
        assertEquals(EditorEncodingDetectionSource.ICU, detection.source)
    }

    @Test
    fun strongIcuEvidenceIsNotOverriddenByAConflictingLocaleHint() {
        assumeTrue("Host JVM does not support ISO-2022-JP", Charset.isSupported("ISO-2022-JP"))
        val charset = Charset.forName("ISO-2022-JP")
        val text = "日本語の文字コード判定テストです"
        val bytes = text.toByteArray(charset)

        val detection = EditorEncodingDetector.detect(
            bytes = bytes,
            preferredCharsetNames = listOf("GB18030", "GBK", "Big5"),
            localeTag = "zh-CN",
        )

        assertEquals(charset, detection.charset)
        assertEquals(EditorEncodingDetectionSource.ICU, detection.source)
        assertTrue(detection.confidence >= 50)
        assertEquals(text, EditorEncodingCodec.decodeStrictOrNull(bytes, detection.charset))
    }

    @Test
    fun truncatedUtf8ProbeDoesNotRejectAnIncompleteTrailingCharacter() {
        val complete = "prefix中".toByteArray(StandardCharsets.UTF_8)
        val truncated = complete.copyOf(complete.size - 1)

        val detection = EditorEncodingDetector.detect(
            bytes = truncated,
            endOfInput = false,
        )

        assertEquals(StandardCharsets.UTF_8, detection.charset)
        assertEquals(EditorEncodingDetectionSource.UTF8, detection.source)
    }

    @Test
    fun bomWinsEvenWhenItsPayloadIsMalformed() {
        val bom = EditorEncodingDetector.bomBytes(StandardCharsets.UTF_8)
        val malformedUtf8 = byteArrayOf(0xC3.toByte(), 0x28)
        val bytes = bom + malformedUtf8

        val detection = EditorEncodingDetector.detect(
            bytes = bytes,
            preferredCharsetNames = listOf("windows-1252"),
            localeTag = "en-US",
        )

        assertEquals(StandardCharsets.UTF_8, detection.charset)
        assertEquals(EditorEncodingDetectionSource.BOM, detection.source)
        assertTrue(detection.hadBom)
        assertArrayEquals(
            malformedUtf8,
            EditorEncodingDetector.payloadWithoutMatchingBom(bytes, detection.charset),
        )
        assertNull(EditorEncodingCodec.decodeStrictOrNull(malformedUtf8, detection.charset))
    }

    @Test
    fun utf32LeBomWinsOverItsUtf16LePrefixWhenSupported() {
        assumeTrue("Host JVM does not support UTF-32LE", Charset.isSupported("UTF-32LE"))
        val utf32Le = Charset.forName("UTF-32LE")
        val bom = EditorEncodingDetector.bomBytes(utf32Le)
        val body = EditorEncodingCodec.encodeStrict("A🙂", utf32Le)
        val bytes = bom + body

        assertEquals(4, bom.size)
        assertEquals(utf32Le, EditorEncodingDetector.bomCharset(bytes))

        val detection = EditorEncodingDetector.detect(bytes)

        assertEquals(utf32Le, detection.charset)
        assertEquals(EditorEncodingDetectionSource.BOM, detection.source)
        assertTrue(detection.hadBom)
        assertArrayEquals(body, EditorEncodingDetector.payloadWithoutMatchingBom(bytes, utf32Le))
        assertEquals("A🙂", EditorEncodingCodec.decodeStrictOrNull(body, utf32Le))
    }

    @Test
    fun convertedUtf16CjkUsesBomAndCanBeDetectedReliably() {
        val utf16Le = StandardCharsets.UTF_16LE
        val text = "简体中文编码测试"

        assertTrue(
            EditorEncodingDetector.shouldWriteBomOnConvert(
                targetCharset = utf16Le,
                currentCharset = StandardCharsets.UTF_8,
                currentHadBom = false,
            ),
        )
        val bytes = EditorEncodingDetector.bomBytes(utf16Le) +
            EditorEncodingCodec.encodeStrict(text, utf16Le)
        val detection = EditorEncodingDetector.detect(bytes)
        val payload = EditorEncodingDetector.payloadWithoutMatchingBom(bytes, detection.charset)

        assertEquals(utf16Le, detection.charset)
        assertTrue(detection.hadBom)
        assertEquals(text, EditorEncodingCodec.decodeStrictOrNull(payload, detection.charset))
    }

    @Test
    fun bomSupportIsLimitedToExplicitUnicodeEncodings() {
        val supported = buildList {
            add(StandardCharsets.UTF_8)
            add(StandardCharsets.UTF_16BE)
            add(StandardCharsets.UTF_16LE)
            if (Charset.isSupported("UTF-32BE")) add(Charset.forName("UTF-32BE"))
            if (Charset.isSupported("UTF-32LE")) add(Charset.forName("UTF-32LE"))
        }

        supported.forEach { charset ->
            assertTrue(charset.name(), EditorEncodingDetector.supportsBom(charset))
            assertTrue(
                charset.name(),
                EditorEncodingDetector.normalizeBomPreference(charset, requested = true),
            )
            assertFalse(
                charset.name(),
                EditorEncodingDetector.normalizeBomPreference(charset, requested = false),
            )
        }

        listOf(StandardCharsets.US_ASCII, StandardCharsets.ISO_8859_1, Charset.forName("UTF-16"))
            .forEach { charset ->
                assertFalse(charset.name(), EditorEncodingDetector.supportsBom(charset))
                assertFalse(
                    charset.name(),
                    EditorEncodingDetector.normalizeBomPreference(charset, requested = true),
                )
            }
    }

    @Test
    fun bomSignaturesMatchTheirCanonicalByteOrder() {
        val expected = linkedMapOf(
            "UTF-8" to byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            "UTF-16BE" to byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
            "UTF-16LE" to byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            "UTF-32BE" to byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte()),
            "UTF-32LE" to byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00),
        )

        expected.forEach { (name, signature) ->
            assumeTrue("Host JVM does not support $name", Charset.isSupported(name))
            val charset = Charset.forName(name)
            assertArrayEquals(name, signature, EditorEncodingDetector.bomBytes(charset))
            assertEquals(name, charset, EditorEncodingDetector.bomCharset(signature))
        }
    }

    @Test
    fun convertDefaultNeverCarriesBomForUnsupportedCharset() {
        assertFalse(
            EditorEncodingDetector.shouldWriteBomOnConvert(
                targetCharset = Charset.forName("UTF-16"),
                currentCharset = Charset.forName("UTF-16"),
                currentHadBom = true,
            ),
        )
        assertFalse(
            EditorEncodingDetector.shouldWriteBomOnConvert(
                targetCharset = StandardCharsets.ISO_8859_1,
                currentCharset = StandardCharsets.ISO_8859_1,
                currentHadBom = true,
            ),
        )
    }
}
