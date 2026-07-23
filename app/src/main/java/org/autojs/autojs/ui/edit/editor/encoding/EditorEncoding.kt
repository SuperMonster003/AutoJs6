package org.autojs.autojs.ui.edit.editor.encoding

import org.mozilla.universalchardet.UniversalDetector
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
    LEGACY,
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
 * BOM-first detector with strict UTF-8 recognition and a compact statistical legacy fallback.
 * Statistical output is deliberately treated as one signal rather than absolute truth because
 * short East Asian and Arabic samples can otherwise produce confident cross-script false matches.
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

        val effectivePreferredCharsetNames = (
            preferredCharsetNames + localeLegacyCharsetNames(localeTag)
        ).distinctBy { name -> charsetOrNull(name)?.name() ?: name }
        val rawMatches = legacyMatches(
            bytes = bytes,
            preferredCharsetNames = effectivePreferredCharsetNames,
            localeTag = localeTag,
            endOfInput = endOfInput,
        )

        // Language/region hints may reorder only an entirely weak legacy result set. Once a
        // medium-or-strong match exists, its byte evidence remains authoritative.
        // zh-CN: 语言和地区提示仅可重排全部为弱匹配的旧式编码结果. 出现中高置信度匹配后,
        // 仍以字节证据为准.
        val mayApplyLocaleHints = rawMatches.maxOfOrNull { it.confidence }?.let {
            it < MIN_AUTHORITATIVE_LEGACY_CONFIDENCE
        } == true
        val ranked = rawMatches.map { raw ->
            val localeEvidence = if (mayApplyLocaleHints) {
                localeEvidence(bytes, raw.charset, localeTag, endOfInput)
            } else {
                0
            }
            val preferredIndex = if (mayApplyLocaleHints) {
                effectivePreferredCharsetNames.indexOfFirst { preferred ->
                    charsetOrNull(preferred) == raw.charset
                }
            } else {
                -1
            }
            RankedMatch(
                charset = raw.charset,
                // A locale-assisted result remains explicitly below the authoritative threshold.
                confidence = if (mayApplyLocaleHints) {
                    (raw.confidence + localeEvidence).coerceAtMost(MIN_AUTHORITATIVE_LEGACY_CONFIDENCE - 1)
                } else {
                    raw.confidence
                },
                score = raw.confidence + raw.quality + localeEvidence +
                    if (preferredIndex >= 0) {
                        (effectivePreferredCharsetNames.size - preferredIndex) * 3
                    } else {
                        0
                    },
            )
        }
            .sortedWith(compareByDescending<RankedMatch> { it.score }.thenByDescending { it.confidence })

        val best = ranked.firstOrNull()
        if (best != null) {
            return EditorEncodingDetection(
                charset = best.charset,
                confidence = best.confidence,
                hadBom = false,
                source = EditorEncodingDetectionSource.LEGACY,
                matches = ranked.map { EditorEncodingMatch(it.charset, it.confidence) },
            )
        }

        return detection(StandardCharsets.UTF_8, 0, false, EditorEncodingDetectionSource.FALLBACK)
    }

    private fun legacyMatches(
        bytes: ByteArray,
        preferredCharsetNames: List<String>,
        localeTag: String?,
        endOfInput: Boolean,
    ): List<RawMatch> {
        val universalCharset = detectUniversalCharset(bytes)
        val candidateNames = linkedSetOf<String>()
        universalCharset?.name()?.let(candidateNames::add)
        if (hasUnicodeNullPattern(bytes)) {
            candidateNames += UNICODE_WITHOUT_BOM_CHARSET_NAMES
        }
        if (hasIso2022EscapeSequence(bytes)) {
            candidateNames += ISO_2022_CHARSET_NAMES
        }
        candidateNames += preferredCharsetNames
        candidateNames += localeLegacyCharsetNames(localeTag)
        candidateNames += COMMON_LEGACY_CHARSET_NAMES

        return candidateNames
            .mapNotNull(::charsetOrNull)
            .distinctBy { it.name() }
            .mapNotNull { charset ->
                if (!isPlausibleUnicodeWithoutBom(bytes, charset)) return@mapNotNull null
                val text = EditorEncodingCodec.decodeStrictOrNull(bytes, charset, endOfInput)
                    ?: return@mapNotNull null
                val textQuality = textQuality(text) ?: return@mapNotNull null
                val evidence = legacyEvidence(bytes, charset, text) ?: return@mapNotNull null
                val detectedByUniversal = charset == universalCharset
                RawMatch(
                    charset = charset,
                    confidence = if (detectedByUniversal) {
                        evidence.confidence.coerceAtLeast(UNIVERSAL_DETECTOR_CONFIDENCE)
                    } else {
                        evidence.confidence
                    },
                    quality = textQuality + evidence.quality +
                            if (detectedByUniversal) UNIVERSAL_DETECTOR_SCORE_BONUS else 0,
                )
            }
    }

    private fun detectUniversalCharset(bytes: ByteArray): Charset? = runCatching {
        UniversalDetector().run {
            handleData(bytes, 0, bytes.size)
            dataEnd()
            detectedCharset?.let(::charsetOrNull)
        }
    }.getOrNull()

    private fun localeLegacyCharsetNames(localeTag: String?): List<String> {
        val locale = localeTag?.lowercase(Locale.ROOT).orEmpty()
        return when {
            locale.startsWith("zh") && (
                locale.contains("hant") || locale.endsWith("-hk") || locale.endsWith("-tw")
                ) -> listOf("Big5", "GB18030", "GBK")
            locale.startsWith("zh") -> listOf("GB18030", "GBK", "Big5")
            locale.startsWith("ja") -> listOf("Shift_JIS", "EUC-JP", "ISO-2022-JP")
            locale.startsWith("ko") -> listOf("EUC-KR")
            locale.startsWith("ru") -> listOf("windows-1251", "KOI8-R", "ISO-8859-5")
            locale.startsWith("ar") -> listOf("windows-1256", "ISO-8859-6")
            locale.startsWith("en") || locale.startsWith("es") || locale.startsWith("fr") ->
                listOf("windows-1252", "ISO-8859-1", "ISO-8859-15")
            else -> emptyList()
        }
    }

    /**
     * Rejects binary-looking decodes and returns a small, charset-independent quality score.
     * Strict decoders already reject malformed byte sequences; this filters single-byte
     * charsets that can otherwise turn arbitrary binary data into control-heavy strings.
     */
    private fun textQuality(text: String): Int? {
        if (text.isEmpty()) return 0
        var controls = 0
        var suspicious = 0
        var visible = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            when {
                codePoint == '\t'.code || codePoint == '\n'.code || codePoint == '\r'.code -> Unit
                Character.isISOControl(codePoint) -> controls++
                Character.getType(codePoint) == Character.UNASSIGNED.toInt() ||
                    Character.getType(codePoint) == Character.PRIVATE_USE.toInt() ||
                    Character.getType(codePoint) == Character.SURROGATE.toInt() -> suspicious++
                !Character.isWhitespace(codePoint) -> visible++
            }
        }
        val codePointCount = text.codePointCount(0, text.length).coerceAtLeast(1)
        if (controls * 20 > codePointCount || suspicious * 20 > codePointCount) return null
        val visibleRatio = visible * 100 / codePointCount
        return (visibleRatio / 5 - controls * 4 - suspicious * 4).coerceIn(0, 20)
    }

    private fun legacyEvidence(bytes: ByteArray, charset: Charset, text: String): LegacyEvidence? {
        val name = charset.name().uppercase(Locale.ROOT)
        val nonWhitespaceCount = text.count { !it.isWhitespace() }.coerceAtLeast(1)
        val cjkCount = text.count { it.code in CJK_RANGES }
        val kanaCount = text.count { it.code in HIRAGANA_RANGE || it.code in KATAKANA_RANGE }
        val hangulCount = text.count { it.code in HANGUL_RANGES }
        val cyrillicCount = text.count { it.code in CYRILLIC_RANGES }
        val arabicCount = text.count { it.code in ARABIC_RANGES }
        val latinExtendedCount = text.count { it.code in LATIN_EXTENDED_RANGES }

        fun scriptEvidence(count: Int): Int =
            (count * 60 / nonWhitespaceCount).coerceIn(0, 60)

        return when {
            name.startsWith("UTF-16") || name.startsWith("UTF-32") -> {
                val laneQuality = unicodeLaneQuality(bytes, name)
                if (laneQuality <= 0) null else LegacyEvidence(
                    confidence = (70 + laneQuality / 3).coerceAtMost(95),
                    quality = laneQuality,
                )
            }
            name == "ISO-2022-JP" -> {
                if (!hasIso2022JpEscapeSequence(bytes)) return null
                LegacyEvidence(
                    confidence = 70,
                    quality = 40 + scriptEvidence(cjkCount + kanaCount),
                )
            }
            name == "ISO-2022-KR" -> {
                if (!hasIso2022KrEscapeSequence(bytes)) return null
                LegacyEvidence(
                    confidence = 70,
                    quality = 40 + scriptEvidence(hangulCount),
                )
            }
            name in CHINESE_CHARSET_NAMES -> {
                val script = scriptEvidence(cjkCount)
                LegacyEvidence(
                    confidence = (10 + script / 2).coerceAtMost(49),
                    quality = script,
                )
            }
            name in JAPANESE_CHARSET_NAMES -> {
                val script = scriptEvidence(cjkCount + kanaCount)
                LegacyEvidence(
                    // Other East Asian byte streams can decode as plausible Shift_JIS/EUC-JP.
                    // Keep statistical matches weak so locale and explicit preferences may break ties.
                    confidence = (10 + script / 2).coerceAtMost(49),
                    quality = script + if (kanaCount > 0) 15 else 0,
                )
            }
            name in KOREAN_CHARSET_NAMES -> {
                val script = scriptEvidence(hangulCount)
                LegacyEvidence(
                    confidence = (10 + script / 2).coerceAtMost(49),
                    quality = script,
                )
            }
            name in CYRILLIC_CHARSET_NAMES -> {
                val script = scriptEvidence(cyrillicCount)
                LegacyEvidence(
                    confidence = (15 + script / 2).coerceAtMost(49),
                    quality = script,
                )
            }
            name in ARABIC_CHARSET_NAMES -> {
                val script = scriptEvidence(arabicCount)
                LegacyEvidence(
                    confidence = (15 + script / 2).coerceAtMost(49),
                    quality = script,
                )
            }
            name in WESTERN_CHARSET_NAMES -> {
                val script = scriptEvidence(latinExtendedCount)
                LegacyEvidence(
                    confidence = (10 + script / 3).coerceAtMost(35),
                    quality = script,
                )
            }
            else -> LegacyEvidence(confidence = 10, quality = 0)
        }
    }

    private fun unicodeLaneQuality(bytes: ByteArray, charsetName: String): Int {
        if (bytes.isEmpty()) return 0
        val unitSize = if (charsetName.startsWith("UTF-32")) 4 else 2
        if (bytes.size % unitSize != 0) return 0
        val expectedZeroLanes = when (charsetName) {
            "UTF-16BE" -> setOf(0)
            "UTF-16LE" -> setOf(1)
            "UTF-32BE" -> setOf(0, 1, 2)
            "UTF-32LE" -> setOf(1, 2, 3)
            else -> return 0
        }
        var expectedZeros = 0
        var unexpectedZeros = 0
        bytes.forEachIndexed { index, byte ->
            if (byte != 0.toByte()) return@forEachIndexed
            if (index % unitSize in expectedZeroLanes) expectedZeros++ else unexpectedZeros++
        }
        val units = bytes.size / unitSize
        if (expectedZeros == 0 || expectedZeros <= unexpectedZeros) return 0
        return (expectedZeros * 60 / (units * expectedZeroLanes.size).coerceAtLeast(1) -
            unexpectedZeros * 10 / units.coerceAtLeast(1)).coerceIn(0, 60)
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
        val name = targetCharset.name().uppercase(Locale.ROOT)
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
        val normalizedLocale = localeTag?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedLocale.isEmpty()) return 0
        val text = EditorEncodingCodec.decodeStrictOrNull(bytes, charset, endOfInput) ?: return 0
        if (text.isEmpty()) return 0

        val relevant = when {
            normalizedLocale.startsWith("zh") -> text.count { it.code in CJK_RANGES }
            normalizedLocale.startsWith("ja") -> text.count {
                it.code in CJK_RANGES || it.code in HIRAGANA_RANGE || it.code in KATAKANA_RANGE
            }
            normalizedLocale.startsWith("ko") -> text.count { it.code in HANGUL_RANGES }
            normalizedLocale.startsWith("ru") -> text.count { it.code in CYRILLIC_RANGES }
            normalizedLocale.startsWith("ar") -> text.count { it.code in ARABIC_RANGES }
            normalizedLocale.startsWith("en") ||
                normalizedLocale.startsWith("es") ||
                normalizedLocale.startsWith("fr") -> text.count { it.code in LATIN_EXTENDED_RANGES }
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
        val name = charset.name().uppercase(Locale.ROOT)
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

    private fun hasIso2022JpEscapeSequence(bytes: ByteArray): Boolean =
        bytes.containsSequence(0x1B, 0x24, 0x40) ||
            bytes.containsSequence(0x1B, 0x24, 0x42) ||
            bytes.containsSequence(0x1B, 0x28, 0x42) ||
            bytes.containsSequence(0x1B, 0x28, 0x4A) ||
            bytes.containsSequence(0x1B, 0x28, 0x49) ||
            bytes.containsSequence(0x1B, 0x24, 0x28, 0x44)

    private fun hasIso2022KrEscapeSequence(bytes: ByteArray): Boolean =
        bytes.containsSequence(0x1B, 0x24, 0x29, 0x43)

    private fun ByteArray.containsSequence(vararg sequence: Int): Boolean {
        if (sequence.isEmpty() || size < sequence.size) return false
        return (0..size - sequence.size).any { offset ->
            sequence.indices.all { index -> this[offset + index].toInt() and 0xff == sequence[index] }
        }
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
        val quality: Int,
    )

    private data class LegacyEvidence(
        val confidence: Int,
        val quality: Int,
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
    private val CYRILLIC_RANGES = 0x0400..0x052F
    private val ARABIC_RANGES = 0x0600..0x08FF
    private val LATIN_EXTENDED_RANGES = 0x00C0..0x024F

    private val UNICODE_WITHOUT_BOM_CHARSET_NAMES = listOf(
        "UTF-32BE",
        "UTF-32LE",
        "UTF-16BE",
        "UTF-16LE",
    )

    private val ISO_2022_CHARSET_NAMES = listOf(
        "ISO-2022-JP",
        "ISO-2022-KR",
    )

    private val COMMON_LEGACY_CHARSET_NAMES = listOf(
        "GB18030",
        "GBK",
        "Big5",
        "Shift_JIS",
        "EUC-JP",
        "ISO-2022-JP",
        "EUC-KR",
        "ISO-2022-KR",
        "windows-1251",
        "KOI8-R",
        "ISO-8859-5",
        "windows-1256",
        "ISO-8859-6",
        "windows-1252",
        "ISO-8859-1",
        "ISO-8859-15",
    )

    private val CHINESE_CHARSET_NAMES = setOf("GB18030", "GBK", "BIG5")
    private val JAPANESE_CHARSET_NAMES = setOf("SHIFT_JIS", "EUC-JP")
    private val KOREAN_CHARSET_NAMES = setOf("EUC-KR")
    private val CYRILLIC_CHARSET_NAMES = setOf("WINDOWS-1251", "KOI8-R", "ISO-8859-5")
    private val ARABIC_CHARSET_NAMES = setOf("WINDOWS-1256", "ISO-8859-6")
    private val WESTERN_CHARSET_NAMES = setOf("WINDOWS-1252", "ISO-8859-1", "ISO-8859-15")

    private const val MIN_AUTHORITATIVE_LEGACY_CONFIDENCE = 50
    // juniversalchardet has no public probability and can be wrong for short cross-script input.
    // Keep it below the authoritative threshold so strict byte structure and locale/script
    // consistency can still overrule it.
    private const val UNIVERSAL_DETECTOR_CONFIDENCE = 45
    private const val UNIVERSAL_DETECTOR_SCORE_BONUS = 30
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
