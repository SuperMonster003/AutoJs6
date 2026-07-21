package org.autojs.autojs.ui.edit.editor.encoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class EditorEncodingRiskEvaluatorTest {

    @Test
    fun currentUtf8WithBomIsSafeAndRetainsBomInByteComparison() {
        val text = "const label = \"编码\";\r\n"
        val bom = EditorEncodingDetector.bomBytes(StandardCharsets.UTF_8)
        val bytes = bom + EditorEncodingCodec.encodeStrict(text, StandardCharsets.UTF_8)

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = true,
            candidateCharset = StandardCharsets.UTF_8,
        )

        assertTrue(candidate.isCurrent)
        assertEquals(EditorEncodingRisk.SAFE, candidate.reloadRisk)
        assertEquals(EditorEncodingRisk.SAFE, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.SAFE, candidate.listRisk)
    }

    @Test
    fun reloadCanWarnWhileConvertRemainsSafe() {
        val text = "café"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            candidateCharset = StandardCharsets.ISO_8859_1,
        )

        assertEquals(EditorEncodingRisk.WARNING, candidate.reloadRisk)
        assertEquals(EditorEncodingRisk.SAFE, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.WARNING, candidate.listRisk)
    }

    @Test
    fun malformedReloadCanBeRedWhileConvertRemainsSafe() {
        val text = "abc"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            candidateCharset = StandardCharsets.UTF_16LE,
        )

        assertEquals(EditorEncodingRisk.ERROR, candidate.reloadRisk)
        assertEquals(EditorEncodingRisk.SAFE, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.ERROR, candidate.listRisk)
    }

    @Test
    fun candidateIsRedWhenNeitherReloadNorConvertIsLossless() {
        val text = "你好🙂"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            candidateCharset = StandardCharsets.US_ASCII,
        )

        assertEquals(EditorEncodingRisk.ERROR, candidate.reloadRisk)
        assertEquals(EditorEncodingRisk.ERROR, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.ERROR, candidate.listRisk)
    }

    @Test
    fun conflictingBomMakesReloadRedWithoutMakingRepresentableConvertRed() {
        val text = "AB"
        val bytes = EditorEncodingDetector.bomBytes(StandardCharsets.UTF_8) +
            text.toByteArray(StandardCharsets.UTF_8)

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = true,
            candidateCharset = StandardCharsets.UTF_16LE,
        )

        assertEquals(EditorEncodingRisk.ERROR, candidate.reloadRisk)
        assertEquals(EditorEncodingRisk.SAFE, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.ERROR, candidate.listRisk)
    }

    @Test
    fun truncatedProbeKeepsAByteIdenticalPrefixConservativelyYellow() {
        val text = "prefix"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val candidate = EditorEncodingRiskEvaluator.evaluate(
            probe = EditorEncodingProbe(bytes = bytes, truncated = true),
            currentTextSample = text,
            currentTextTruncated = true,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            detection = EditorEncodingDetector.detect(bytes, endOfInput = false),
            charsets = listOf(StandardCharsets.UTF_8),
        ).single()

        assertEquals(EditorEncodingRisk.WARNING, candidate.reloadRisk)
        assertEquals(EditorEncodingRisk.WARNING, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.WARNING, candidate.listRisk)
    }

    @Test
    fun unacceptedDecodingReplacementsMakeRepresentableConvertYellow() {
        val text = "bad\uFFFDbytes"
        val bytes = byteArrayOf('b'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte(), 0xFF.toByte())

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            candidateCharset = StandardCharsets.UTF_8,
            currentTextHadDecodingErrors = true,
        )

        assertEquals(EditorEncodingRisk.ERROR, candidate.reloadRisk)
        assertEquals(EditorEncodingRisk.WARNING, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.ERROR, candidate.listRisk)
    }

    @Test
    fun unacceptedDecodingReplacementsDoNotDowngradeImpossibleConvertFromRed() {
        val text = "bad\uFFFDbytes"
        val bytes = byteArrayOf('b'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte(), 0xFF.toByte())

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            candidateCharset = StandardCharsets.US_ASCII,
            currentTextHadDecodingErrors = true,
        )

        assertEquals(EditorEncodingRisk.ERROR, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.ERROR, candidate.listRisk)
    }

    @Test
    fun representableButNonRoundTrippingConvertIsYellow() {
        val shiftJis = Charset.forName("Shift_JIS")
        val text = "price ¥100"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)

        val candidate = evaluateSingle(
            bytes = bytes,
            currentText = text,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            candidateCharset = shiftJis,
        )

        assertEquals(EditorEncodingRisk.WARNING, candidate.convertRisk)
        assertEquals(EditorEncodingRisk.WARNING, candidate.listRisk)
    }

    @Test
    fun codePointSafeTruncatedSampleDoesNotMakeUtf8ConvertRed() {
        val fullText = "a".repeat(7) + "🙂tail"
        val sample = EditorEncodingCodec.takeCodePointSafePrefix(fullText, 8)
        val bytes = fullText.toByteArray(StandardCharsets.UTF_8)

        val candidate = EditorEncodingRiskEvaluator.evaluate(
            probe = EditorEncodingProbe(bytes = bytes, truncated = false),
            currentTextSample = sample,
            currentTextTruncated = true,
            currentCharset = StandardCharsets.UTF_8,
            currentHadBom = false,
            detection = EditorEncodingDetector.detect(bytes),
            charsets = listOf(StandardCharsets.UTF_8),
        ).single()

        assertEquals(EditorEncodingRisk.WARNING, candidate.convertRisk)
    }

    @Test
    fun candidatesExcludeCharsetsThatEmitAnImplicitBom() {
        val names = EditorEncodingRiskEvaluator.candidateCharsets().map { it.name() }

        assertTrue(names.none { it.equals("UTF-16", true) || it.equals("UTF-32", true) })
        assertTrue(names.none { it.contains("BOM", true) })
    }

    private fun evaluateSingle(
        bytes: ByteArray,
        currentText: String,
        currentCharset: Charset,
        currentHadBom: Boolean,
        candidateCharset: Charset,
        currentTextHadDecodingErrors: Boolean = false,
    ): EditorEncodingCandidate {
        return EditorEncodingRiskEvaluator.evaluate(
            probe = EditorEncodingProbe(bytes = bytes, truncated = false),
            currentTextSample = currentText,
            currentTextTruncated = false,
            currentCharset = currentCharset,
            currentHadBom = currentHadBom,
            detection = EditorEncodingDetector.detect(bytes),
            currentTextHadDecodingErrors = currentTextHadDecodingErrors,
            charsets = listOf(candidateCharset),
        ).single()
    }
}
