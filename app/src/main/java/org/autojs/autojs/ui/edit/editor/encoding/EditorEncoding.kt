package org.autojs.autojs.ui.edit.editor.encoding

import com.ibm.icu.text.CharsetDetector
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil

enum class EditorEncodingDetectionSource {
    BOM,
    UTF8,
    ASCII,
    ICU,
    FALLBACK,
}

data class EditorEncodingMatch(
    val charset: Charset,
    val confidence: Int,
)

data class EditorEncodingDetection(
    val charset: Charset,
    val confidence: Int,
    val hadBom: Boolean,
    val source: EditorEncodingDetectionSource,
    val matches: List<EditorEncodingMatch>,
)

enum class EditorEncodingRisk {
    SAFE,
    WARNING,
    ERROR,
}

data class EditorEncodingCandidate(
    val charset: Charset,
    val isCurrent: Boolean,
    val detectionConfidence: Int?,
    val reloadRisk: EditorEncodingRisk,
    val convertRisk: EditorEncodingRisk,
    val listRisk: EditorEncodingRisk,
)

data class EditorEncodingProbe(
    val bytes: ByteArray,
    val truncated: Boolean,
)

data class EditorEncodedDigest(
    val byteCount: Long,
    val sha256: String,
)

/** Strict charset primitives shared by detection, risk preview, and the write path. */
object EditorEncodingCodec {

    fun decodeStrictOrNull(
        bytes: ByteArray,
        charset: Charset,
        endOfInput: Boolean = true,
    ): String? = runCatching {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        var output = CharBuffer.allocate(
            ceil(bytes.size * decoder.maxCharsPerByte().toDouble()).toInt().coerceAtLeast(16) + 8,
        )
        val input = ByteBuffer.wrap(bytes)

        while (true) {
            val result = decoder.decode(input, output, endOfInput)
            when {
                result.isError -> result.throwException()
                result.isOverflow -> output = output.grow()
                else -> break
            }
        }

        if (endOfInput) {
            while (true) {
                val result = decoder.flush(output)
                when {
                    result.isError -> result.throwException()
                    result.isOverflow -> output = output.grow()
                    else -> break
                }
            }
        }

        output.flip()
        output.toString()
    }.getOrNull()

    @Throws(CharacterCodingException::class)
    fun encodeStrict(text: CharSequence, charset: Charset): ByteArray {
        val encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(text))
        return ByteArray(encoded.remaining()).also(encoded::get)
    }

    fun encodeStrictOrNull(text: CharSequence, charset: Charset): ByteArray? = runCatching {
        encodeStrict(text, charset)
    }.getOrNull()

    fun canEncode(text: CharSequence, charset: Charset): Boolean = runCatching {
        charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .canEncode(CharBuffer.wrap(text))
    }.getOrDefault(false)

    fun isRoundTripSafe(text: CharSequence, charset: Charset): Boolean {
        val encoded = encodeStrictOrNull(text, charset) ?: return false
        val decoded = decodeStrictOrNull(encoded, charset) ?: return false
        return decoded == text.toString()
    }

    @Throws(CharacterCodingException::class)
    fun encodedDigest(text: CharSequence, charset: Charset, includeBom: Boolean): EditorEncodedDigest {
        val encoder = charset.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val digest = MessageDigest.getInstance("SHA-256")
        var byteCount = 0L

        fun update(bytes: ByteArray) {
            digest.update(bytes)
            byteCount += bytes.size
        }

        fun drain(buffer: ByteBuffer) {
            buffer.flip()
            byteCount += buffer.remaining().toLong()
            digest.update(buffer)
            buffer.clear()
        }

        if (EditorEncodingDetector.normalizeBomPreference(charset, includeBom)) {
            update(EditorEncodingDetector.bomBytes(charset))
        }

        val input = CharBuffer.wrap(text)
        val output = ByteBuffer.allocate(32 * 1024)
        while (true) {
            val result = encoder.encode(input, output, true)
            drain(output)
            when {
                result.isError -> result.throwException()
                result.isUnderflow -> break
            }
        }
        while (true) {
            val result = encoder.flush(output)
            drain(output)
            when {
                result.isError -> result.throwException()
                result.isUnderflow -> break
            }
        }

        return EditorEncodedDigest(
            byteCount = byteCount,
            sha256 = digest.digest().joinToString("") { "%02x".format(Locale.US, it) },
        )
    }

    /** Returns a UTF-16 bounded prefix without splitting a surrogate pair at the boundary. */
    fun takeCodePointSafePrefix(text: String, maxChars: Int): String {
        require(maxChars >= 0)
        var end = text.length.coerceAtMost(maxChars)
        if (end in 1 until text.length &&
            text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()
        ) {
            end--
        }
        return text.substring(0, end)
    }

    fun decodeReplacing(bytes: ByteArray, charset: Charset): String {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun CharBuffer.grow(): CharBuffer {
        flip()
        return CharBuffer.allocate(capacity() * 2).also { it.put(this) }
    }
}

/**
 * BOM-first detector with strict UTF-8 recognition and ICU as a legacy-charset fallback.
 * A locale hint only breaks weak legacy matches; it never overrides a BOM or valid UTF-8.
 */
object EditorEncodingDetector {

    fun detect(
        bytes: ByteArray,
        preferredCharsetNames: List<String> = emptyList(),
        localeTag: String? = null,
        endOfInput: Boolean = true,
    ): EditorEncodingDetection {
        bomCharset(bytes)?.let { charset ->
            return detection(
                charset = charset,
                confidence = 100,
                hadBom = true,
                source = EditorEncodingDetectionSource.BOM,
            )
        }

        if (bytes.isEmpty()) {
            return detection(StandardCharsets.UTF_8, 0, false, EditorEncodingDetectionSource.ASCII)
        }

        if (bytes.all(::isTextAsciiByte)) {
            // ASCII bytes are valid in many encodings. UTF-8 is the safest default, but the
            // evidence cannot honestly identify it with certainty.
            return detection(StandardCharsets.UTF_8, 50, false, EditorEncodingDetectionSource.ASCII)
        }

        if (!hasUnicodeNullPattern(bytes) && !hasIso2022EscapeSequence(bytes) &&
            EditorEncodingCodec.decodeStrictOrNull(bytes, StandardCharsets.UTF_8, endOfInput) != null
        ) {
            return detection(StandardCharsets.UTF_8, 100, false, EditorEncodingDetectionSource.UTF8)
        }

        val rawMatches = runCatching {
            CharsetDetector().apply { setText(bytes) }.detectAll().mapNotNull { match ->
                val charset = charsetOrNull(match.name) ?: return@mapNotNull null
                if (!isPlausibleUnicodeWithoutBom(bytes, charset)) return@mapNotNull null
                if (EditorEncodingCodec.decodeStrictOrNull(bytes, charset, endOfInput) == null) return@mapNotNull null
                RawMatch(
                    charset = charset,
                    confidence = match.confidence.coerceIn(0, 100),
                )
            }
        }.getOrDefault(emptyList())
            .distinctBy { it.charset.name() }

        // Language/region hints may reorder only an entirely weak ICU result set. Once ICU has a
        // medium-or-strong match, its byte evidence remains authoritative.
        // zh-CN: 语言和地区提示仅可重排全部为弱匹配的 ICU 结果. ICU 出现中高置信度匹配后,
        // 仍以字节证据为准.
        val mayApplyLocaleHints = rawMatches.maxOfOrNull { it.confidence }?.let {
            it < MIN_AUTHORITATIVE_ICU_CONFIDENCE
        } == true
        val ranked = rawMatches.map { raw ->
            val localeEvidence = if (mayApplyLocaleHints) {
                localeEvidence(bytes, raw.charset, localeTag, endOfInput)
            } else {
                0
            }
            val preferredIndex = if (mayApplyLocaleHints) {
                preferredCharsetNames.indexOfFirst { preferred -> charsetOrNull(preferred) == raw.charset }
            } else {
                -1
            }
            RankedMatch(
                charset = raw.charset,
                // A locale-assisted result remains explicitly below the authoritative threshold.
                confidence = if (mayApplyLocaleHints) {
                    (raw.confidence + localeEvidence).coerceAtMost(MIN_AUTHORITATIVE_ICU_CONFIDENCE - 1)
                } else {
                    raw.confidence
                },
                score = raw.confidence + localeEvidence +
                    if (preferredIndex >= 0) (preferredCharsetNames.size - preferredIndex) * 3 else 0,
            )
        }
            .sortedWith(compareByDescending<RankedMatch> { it.score }.thenByDescending { it.confidence })

        val best = ranked.firstOrNull()
        if (best != null) {
            return EditorEncodingDetection(
                charset = best.charset,
                confidence = best.confidence,
                hadBom = false,
                source = EditorEncodingDetectionSource.ICU,
                matches = ranked.map { EditorEncodingMatch(it.charset, it.confidence) },
            )
        }

        return detection(StandardCharsets.UTF_8, 0, false, EditorEncodingDetectionSource.FALLBACK)
    }

    fun bomCharset(bytes: ByteArray): Charset? {
        BOM_SIGNATURES.firstOrNull { (_, signature) -> bytes.startsWith(signature) }
            ?.first
            ?.let(::charsetOrNull)
            ?.let { return it }
        return null
    }

    fun bomBytes(charset: Charset): ByteArray {
        val canonical = charset.name()
        return BOM_SIGNATURES.firstOrNull { (name, _) ->
            charsetOrNull(name) == charset || name.equals(canonical, ignoreCase = true)
        }?.second?.copyOf() ?: ByteArray(0)
    }

    /** Whether [charset] has a well-defined BOM that AutoJs6 can emit. */
    fun supportsBom(charset: Charset): Boolean = bomBytes(charset).isNotEmpty()

    /** Drops an impossible BOM request instead of carrying an invalid editor/save state. */
    fun normalizeBomPreference(charset: Charset, requested: Boolean): Boolean =
        requested && supportsBom(charset)

    fun hasBom(bytes: ByteArray, charset: Charset): Boolean {
        val bom = bomBytes(charset)
        return bom.isNotEmpty() && bytes.startsWith(bom)
    }

    fun shouldWriteBomOnConvert(
        targetCharset: Charset,
        currentCharset: Charset,
        currentHadBom: Boolean,
    ): Boolean {
        if (!supportsBom(targetCharset)) return false
        if (targetCharset == currentCharset && currentHadBom) return true
        val name = targetCharset.name().uppercase()
        // Explicit-endian UTF-16/32 without a BOM is frequently misdetected for CJK-only text.
        // UTF-8 remains BOM-less unless the current UTF-8 document already has one.
        return name.startsWith("UTF-16") || name.startsWith("UTF-32")
    }

    fun payloadWithoutMatchingBom(bytes: ByteArray, charset: Charset): ByteArray {
        val bom = bomBytes(charset)
        return if (bom.isNotEmpty() && bytes.startsWith(bom)) bytes.copyOfRange(bom.size, bytes.size) else bytes
    }

    private fun detection(
        charset: Charset,
        confidence: Int,
        hadBom: Boolean,
        source: EditorEncodingDetectionSource,
    ) = EditorEncodingDetection(
        charset = charset,
        confidence = confidence,
        hadBom = hadBom,
        source = source,
        matches = listOf(EditorEncodingMatch(charset, confidence)),
    )

    private fun localeEvidence(
        bytes: ByteArray,
        charset: Charset,
        localeTag: String?,
        endOfInput: Boolean,
    ): Int {
        val normalizedLocale = localeTag?.lowercase().orEmpty()
        if (normalizedLocale.isEmpty()) return 0
        val text = EditorEncodingCodec.decodeStrictOrNull(bytes, charset, endOfInput) ?: return 0
        if (text.isEmpty()) return 0

        val relevant = when {
            normalizedLocale.startsWith("zh") -> text.count { it.code in CJK_RANGES }
            normalizedLocale.startsWith("ja") -> text.count {
                it.code in CJK_RANGES || it.code in HIRAGANA_RANGE || it.code in KATAKANA_RANGE
            }
            normalizedLocale.startsWith("ko") -> text.count { it.code in HANGUL_RANGES }
            else -> 0
        }
        if (relevant == 0) return 0
        val ratio = relevant.toDouble() / text.count { !it.isWhitespace() }.coerceAtLeast(1)
        return when {
            relevant >= 4 && ratio >= 0.5 -> 45
            relevant >= 2 && ratio >= 0.25 -> 35
            else -> 20
        }
    }

    private fun isPlausibleUnicodeWithoutBom(bytes: ByteArray, charset: Charset): Boolean {
        val name = charset.name().uppercase()
        if (!name.startsWith("UTF-16") && !name.startsWith("UTF-32")) return true
        if (name.startsWith("UTF-16") && bytes.size % 2 != 0) return false
        if (name.startsWith("UTF-32") && bytes.size % 4 != 0) return false
        val zeroRatio = bytes.count { it == 0.toByte() }.toDouble() / bytes.size.coerceAtLeast(1)
        return zeroRatio >= if (name.startsWith("UTF-32")) 0.45 else 0.15
    }

    private fun isTextAsciiByte(byte: Byte): Boolean {
        val value = byte.toInt()
        return value == 0x09 || value == 0x0A || value == 0x0D || value in 0x20..0x7E
    }

    private fun hasUnicodeNullPattern(bytes: ByteArray): Boolean {
        if (bytes.size < 4 || bytes.size % 2 != 0) return false
        return bytes.count { it == 0.toByte() }.toDouble() / bytes.size >= 0.15
    }

    private fun hasIso2022EscapeSequence(bytes: ByteArray): Boolean {
        for (index in 0 until bytes.lastIndex) {
            if (bytes[index] == 0x1B.toByte() &&
                (bytes[index + 1] == '$'.code.toByte() || bytes[index + 1] == '('.code.toByte())
            ) {
                return true
            }
        }
        return false
    }

    private fun charsetOrNull(name: String): Charset? = runCatching { Charset.forName(name) }.getOrNull()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private data class RankedMatch(
        val charset: Charset,
        val confidence: Int,
        val score: Int,
    )

    private data class RawMatch(
        val charset: Charset,
        val confidence: Int,
    )

    private val BOM_SIGNATURES = listOf(
        "UTF-32BE" to byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte()),
        "UTF-32LE" to byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00),
        "UTF-8" to byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
        "UTF-16BE" to byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
        "UTF-16LE" to byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
    )

    private val CJK_RANGES = 0x3400..0x9FFF
    private val HIRAGANA_RANGE = 0x3040..0x309F
    private val KATAKANA_RANGE = 0x30A0..0x30FF
    private val HANGUL_RANGES = 0xAC00..0xD7AF
    private const val MIN_AUTHORITATIVE_ICU_CONFIDENCE = 50
}

/** Computes the yellow/red candidate badges and the two action-specific risks. */
object EditorEncodingRiskEvaluator {

    fun evaluate(
        probe: EditorEncodingProbe,
        currentTextSample: String,
        currentTextTruncated: Boolean,
        currentCharset: Charset,
        currentHadBom: Boolean,
        detection: EditorEncodingDetection,
        currentTextHadDecodingErrors: Boolean = false,
        charsets: Collection<Charset> = candidateCharsets(),
    ): List<EditorEncodingCandidate> {
        val bomCharset = EditorEncodingDetector.bomCharset(probe.bytes)
        return charsets
            .asSequence()
            .distinctBy { it.name() }
            .map { charset ->
                val payload = EditorEncodingDetector.payloadWithoutMatchingBom(probe.bytes, charset)
                val decoded = if (bomCharset != null && bomCharset != charset) {
                    null
                } else {
                    EditorEncodingCodec.decodeStrictOrNull(
                        payload,
                        charset,
                        endOfInput = !probe.truncated,
                    )
                }
                val reloadRisk = when {
                    decoded == null -> EditorEncodingRisk.ERROR
                    probe.truncated || currentTextTruncated -> EditorEncodingRisk.WARNING
                    decoded != currentTextSample -> EditorEncodingRisk.WARNING
                    else -> EditorEncodingRisk.SAFE
                }

                val encodedBody = EditorEncodingCodec.encodeStrictOrNull(currentTextSample, charset)
                val convertsRoundTripSafely = encodedBody?.let { encoded ->
                    EditorEncodingCodec.decodeStrictOrNull(encoded, charset) == currentTextSample
                } == true
                val convertRisk = when {
                    encodedBody == null -> EditorEncodingRisk.ERROR
                    currentTextHadDecodingErrors -> EditorEncodingRisk.WARNING
                    currentTextTruncated -> EditorEncodingRisk.WARNING
                    !convertsRoundTripSafely -> EditorEncodingRisk.WARNING
                    else -> EditorEncodingRisk.SAFE
                }
                val shouldKeepBom = EditorEncodingDetector.shouldWriteBomOnConvert(
                    targetCharset = charset,
                    currentCharset = currentCharset,
                    currentHadBom = currentHadBom,
                )
                val encoded = encodedBody?.let { body ->
                    if (shouldKeepBom) EditorEncodingDetector.bomBytes(charset) + body else body
                }
                val listRisk = when {
                    encoded == null || reloadRisk == EditorEncodingRisk.ERROR ||
                        convertRisk == EditorEncodingRisk.ERROR -> EditorEncodingRisk.ERROR
                    probe.truncated || currentTextTruncated -> EditorEncodingRisk.WARNING
                    !encoded.contentEquals(probe.bytes) -> EditorEncodingRisk.WARNING
                    convertRisk != EditorEncodingRisk.SAFE -> EditorEncodingRisk.WARNING
                    reloadRisk != EditorEncodingRisk.SAFE -> EditorEncodingRisk.WARNING
                    else -> EditorEncodingRisk.SAFE
                }

                EditorEncodingCandidate(
                    charset = charset,
                    isCurrent = charset == currentCharset,
                    detectionConfidence = detection.matches.firstOrNull { it.charset == charset }?.confidence,
                    reloadRisk = reloadRisk,
                    convertRisk = convertRisk,
                    listRisk = listRisk,
                )
            }
            .sortedWith(
                compareByDescending<EditorEncodingCandidate> { it.isCurrent }
                    .thenByDescending { it.detectionConfidence ?: -1 }
                    .thenBy { charsetGroup(it.charset) }
                    .thenBy { it.charset.name() },
            )
            .toList()
    }

    fun candidateCharsets(): List<Charset> = Charset.availableCharsets().values
        .filterNot { charset ->
            val name = charset.name()
            name.equals("UTF-16", true) || name.equals("UTF-32", true) ||
                name.contains("BOM", true)
        }

    private fun charsetGroup(charset: Charset): Int = when {
        charset == StandardCharsets.UTF_8 -> 0
        charset.name().startsWith("UTF-", true) -> 1
        else -> 2
    }
}
