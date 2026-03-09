package org.autojs.autojs.util

import android.content.Context
import android.content.res.Configuration
import android.text.TextUtils
import androidx.annotation.StringRes
import androidx.core.net.toUri
import com.ibm.icu.text.CharsetDetector
import com.ibm.icu.text.CharsetMatch
import org.autojs.autojs.annotation.LocaleNonRelated
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Language
import org.autojs.autojs.util.NumberUtils.roundToString
import org.opencv.core.Point
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.*
import kotlin.math.min
import kotlin.math.pow

/**
 * Created by Stardust on May 3, 2017.
 * Modified by SuperMonster003 as of Feb 8, 2026.
 */
object StringUtils {

    private val globalAppContext by lazy { GlobalAppContext.get() }

    private val localePref = mapOf(
        Language.ZH_HANS.languageTag to listOf("GB18030", "GBK", "Big5"),
        Language.ZH_HANT_HK.languageTag to listOf("Big5", "GB18030"),
        Language.ZH_HANT_TW.languageTag to listOf("Big5", "GB18030"),
        Language.JA.languageTag to listOf("Shift_JIS", "EUC-JP", "ISO-2022-JP"),
        Language.KO.languageTag to listOf("EUC-KR")
    )

    @JvmStatic
    fun str(@LocaleNonRelated resId: Int, vararg args: Any): String = globalAppContext.getString(resId, *args)

    @JvmStatic
    fun key(@LocaleNonRelated resId: Int): String = globalAppContext.getString(resId)

    @JvmStatic
    fun join(delimiter: CharSequence?, vararg tokens: Any?): String = TextUtils.join(delimiter!!, tokens)

    @JvmStatic
    fun lastIndexOf(text: CharSequence, ch: Char, fromIndex: Int): Int {
        if (text is String) {
            return text.lastIndexOf(ch, fromIndex)
        }
        var i = min(fromIndex, text.length - 1)
        while (i >= 0) {
            if (text[i] == ch) return i else i--
        }
        return -1
    }

    @JvmStatic
    fun indexOf(text: CharSequence, ch: Char, fromIndex: Int): Int {
        if (text is String) {
            return text.indexOf(ch, fromIndex)
        }
        var index = fromIndex
        val max = text.length
        if (index < 0) {
            index = 0
        } else if (index >= max) {
            // Note: fromIndex might be near -1>>>1.
            return -1
        }

        // handle most cases here (ch is a BMP code point or a
        // negative value (invalid code point))
        for (i in index until max) {
            if (text[i] == ch) {
                return i
            }
        }
        return -1
    }

    @JvmStatic
    fun indexOf(source: CharSequence, target: CharSequence, fromIndex: Int): Int {
        if (source is String && target is String) {
            return source.indexOf(target)
        }
        var index = fromIndex
        if (index >= source.length) {
            return if (target.isEmpty()) source.length else -1
        }
        if (index < 0) {
            index = 0
        }
        if (target.isEmpty()) {
            return index
        }
        val first = target[0]
        val max = source.length - target.length
        var i = index
        while (i <= max) {
            /* Look for first character. */
            while (source[i] != first) {
                if (++i > max) {
                    break
                }
            }
            /* Found first character, now look at the rest of v2 */
            if (i <= max) {
                var j = i + 1
                val end = j + target.length - 1
                var k = 1
                while (j < end && source[j] == target[k]) {
                    j++
                    k++
                }
                if (j == end) {
                    /* Found whole string. */
                    return i
                }
            }
            i++
        }
        return -1
    }

    @JvmStatic
    fun lastIndexOf(source: CharSequence, target: CharSequence, fromIndex: Int): Int {
        if (source is String && target is String) {
            return source.lastIndexOf(target)
        }
        var index = fromIndex
        /*
         * Check arguments; return immediately where possible. For
         * consistency, don't check for null str.
         */
        val rightIndex = source.length - target.length
        if (index < 0) {
            return -1
        }
        if (index > rightIndex) {
            index = rightIndex
        }
        /* Empty string always matches. */
        if (target.isEmpty()) {
            return index
        }
        val strLastIndex = target.length - 1
        val strLastChar = target[strLastIndex]
        val min = target.length - 1
        var i = min + index
        startSearchForLastChar@ while (true) {
            while (i >= min && source[i] != strLastChar) {
                i--
            }
            if (i < min) {
                return -1
            }
            var j = i - 1
            val start = j - (target.length - 1)
            var k = strLastIndex - 1
            while (j > start) {
                if (source[j--] != target[k--]) {
                    i--
                    continue@startSearchForLastChar
                }
            }
            return start + 1
        }
    }

    @JvmStatic
    fun getStringByLanguageTag(id: Int, locale: String): String {
        val configuration = Configuration(globalAppContext.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(locale))
        return globalAppContext.createConfigurationContext(configuration).resources.getString(id)
    }

    @JvmStatic
    @JvmOverloads
    fun formatDouble(d: Double, fraction: Int = 3): String {
        var frac = 0
        while (frac < fraction) {
            if (d * 10.0.pow(frac) % 1.0 == 0.0) {
                break
            }
            frac += 1
        }
        return String.format(Language.getPrefLanguage().locale, "%.${frac}f", d).let { s ->
            when {
                s.contains('.') -> s.replace("\\.?0+$".toRegex(), "")
                else -> s
            }
        }
    }

    @JvmStatic
    @Deprecated("Deprecated since v6.6.0", ReplaceWith("uppercaseFirstChar(s)"))
    fun toUpperCaseFirst(s: String) = s.uppercaseFirstChar()

    @JvmStatic
    fun toFormattedSummary(dataList: List<Pair<String, () -> Any?>>): String {
        val separatorLv0 = "\n"
        val separatorLv1 = "$separatorLv0  "
        val separatorLv2 = "$separatorLv1  "

        return dataList.joinToString(prefix = "{$separatorLv1", separator = separatorLv1, postfix = "$separatorLv0}") { (name, action) ->
            val value = when (val actionResult = action()) {
                is CharSequence -> "\"$actionResult\""
                is Iterable<*> -> actionResult.joinToString(prefix = "[$separatorLv2", separator = separatorLv2, postfix = "$separatorLv1]")
                is Array<*> -> actionResult.joinToString(prefix = "[$separatorLv2", separator = separatorLv2, postfix = "$separatorLv1]")
                is Point -> actionResult.toFormattedPointString(0)
                else -> actionResult
            }
            "$name=$value"
        }
    }

    @JvmStatic
    @JvmOverloads
    fun Point.toFormattedPointString(scale: Int = 0): String {
        return "{${x.roundToString(scale)}, ${y.roundToString(scale)}}"
    }

    @JvmStatic
    fun detectCharset(bytes: ByteArray): CharsetMatchWrapper {
        val matches = kotlin.runCatching {
            CharsetDetector().apply { setText(bytes).setDeclaredEncoding("UTF-8") }.detectAll()
        }.getOrNull() ?: return CharsetMatchWrapper(null)

        matches.firstOrNull {
            it.name.startsWith("UTF-", true) && it.confidence >= 30
        }?.let {
            return CharsetMatchWrapper(it)
        }

        val topScore = matches.maxOfOrNull { it.confidence } ?: -1
        val candidates = matches.filter { it.confidence == topScore }
        val localeTag = Language.getPrefLanguage().getLocalCompatibleLanguageTag()
        val prefList = localePref[localeTag] ?: emptyList()
        val best = candidates.minByOrNull {
            prefList.indexOf(it.name).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE
        } ?: candidates.first()

        return CharsetMatchWrapper(best)
    }

    @JvmStatic
    fun hasBom(bytes: ByteArray, charset: Charset): Boolean {
        val bom = bomBytes(charset)
        return bom.isNotEmpty() && bytes.take(bom.size).toByteArray().contentEquals(bom)
    }

    @JvmStatic
    fun bomBytes(charset: Charset): ByteArray = when (charset) {
        StandardCharsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        StandardCharsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        StandardCharsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> ByteArray(0)
    }

    @JvmStatic
    fun dropBom(bytes: ByteArray, charset: Charset): ByteArray {
        val bom = bomBytes(charset)
        if (bom.isEmpty() || bytes.size < bom.size || !bytes.take(bom.size).toByteArray().contentEquals(bom)) {
            return bytes
        }
        return bytes.copyOfRange(bom.size, bytes.size)
    }

    val String.estimateVisualWidth: Int
        get() {
            var width = 0
            var i = 0
            while (i < length) {
                // 取 code point (可能是两位 surrogates 拼成一个 code point)
                val cp = codePointAt(i)
                // 移动下标, 跳过组合过的 surrogate
                i += Character.charCount(cp)

                // 简易判断: 东亚全宽/Emoji 等
                width += if (isLikelyFullwidth(cp)) 2 else 1
            }
            return width
        }

    fun String.toDoubleOrNaN() = this.toDoubleOrNull() ?: Double.NaN

    fun String.padStart(length: Int, padStr: String) = when {
        this.length >= length || padStr.isEmpty() -> this
        else -> getPadding(length, padStr) + this
    }

    fun String.padEnd(length: Int, padStr: String) = when {
        this.length >= length || padStr.isEmpty() -> this
        else -> this + getPadding(length, padStr)
    }

    private fun String.getPadding(length: Int, padStr: String): String {
        val repeatTimes = (length - this.length) / padStr.length
        val remainderStr = padStr.take((length - this.length) % padStr.length)
        return padStr.repeat(repeatTimes) + remainderStr
    }

    fun CharSequence.looseMatches(reference: CharSequence): Boolean {
        return this.toString().normalizeForMatch() == reference.toString().normalizeForMatch()
    }

    private fun String.normalizeForMatch(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .trim()
            .replace(Regex("\\p{M}"), "") // 移除组合符号, 如上标符号
            .replace(Regex("[^\\p{L}\\p{N}@.|]+"), "") // 只保留 [字母/数字/特定符号]
            .lowercase()
    }

    private fun isLikelyFullwidth(codePoint: Int) = when (codePoint) {
        // CJK 中日韩统一表意文字
        in 0x4E00..0x9FFF -> true
        // 常见的 Emoji 起始, 又或者判断 Character.getType(cp)
        in 0x1F300..0x1FAFF -> true
        // East Asian Fullwidth, Wide 等范围, 可参考 EastAsianWidth.txt
        else -> false
    }

    @JvmStatic
    fun String.isUri(): Boolean {
        if (isBlank()) return false
        val uri = runCatching { this.toUri() }.getOrNull() ?: return false
        uri.scheme?.lowercase() ?: return false
        return true
    }

    @JvmStatic
    fun String.toFile(): File {
        val path = this
        return File(path)
    }

    @JvmStatic
    fun String.uppercaseFirstChar(): String {
        val s = this
        return if (s.isEmpty()) s else s.replaceFirstChar { s[0].uppercaseChar() }
    }

    @JvmStatic
    fun String.lowercaseFirstChar(): String {
        val s = this
        return if (s.isEmpty()) s else s.replaceFirstChar { s[0].lowercaseChar() }
    }

    @JvmStatic
    fun String.looseKey(): String =
        buildString(length) {
            for (c in this@looseKey) {
                if (c.isLetterOrDigit()) append(c.lowercaseChar())
            }
        }

    @JvmStatic
    fun String.equalsLoosely(other: String): Boolean =
        this.looseKey() == other.looseKey()

    @JvmStatic
    fun String.isLooselyIn(list: Iterable<String>): Boolean =
        list.any { this.equalsLoosely(it) }

    @JvmStatic
    fun String.normalizeTrailingSlash(isDir: Boolean): String {
        if (this == "/") return "/"

        val noTail = this.trimEnd('/')
        return if (isDir) "$noTail/" else noTail
    }

    @JvmStatic
    fun getStringForLocale(context: Context, locale: Locale, @StringRes resId: Int): String {
        val cfg = Configuration(context.resources.configuration)
        cfg.setLocale(locale)
        val localized = context.createConfigurationContext(cfg)
        return localized.resources.getString(resId)
    }

    class CharsetMatchWrapper(private val charsetMatch: CharsetMatch?) {

        val name: String? by lazy { charsetMatch?.name }
        val confidence: Int? by lazy { charsetMatch?.confidence }

        fun charsetOrNull(): Charset? = runCatching {
            name?.let { Charset.forName(it) }
        }.getOrNull()

        @JvmOverloads
        fun charsetOrDefault(defaultValue: Charset = StandardCharsets.UTF_8): Charset = charsetOrNull() ?: defaultValue

        fun nameOrDefault(defaultValue: String): String = name ?: defaultValue

    }

    class LooseMatcher(options: Iterable<String>) {

        private val keySet: Set<String>

        init {
            val map = HashMap<String, String>(16)
            for (opt in options) {
                val key = opt.looseKey()
                val prev = map.putIfAbsent(key, opt)
                require(prev == null) {
                    "Loose key collision: \"$prev\" and \"$opt\" both normalize to \"$key\""
                }
            }
            keySet = map.keys
        }

        fun contains(value: String): Boolean = value.looseKey() in keySet
    }

}
