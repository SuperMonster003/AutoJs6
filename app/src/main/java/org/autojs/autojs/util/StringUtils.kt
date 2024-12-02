package org.autojs.autojs.util

import android.content.res.Configuration
import android.text.TextUtils
import org.autojs.autojs.annotation.LocaleNonRelated
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.core.pref.Language
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow

/**
 * Created by Stardust on May 3, 2017.
 * Modified by SuperMonster003 as of Jul 6, 2022.
 */
object StringUtils {

    private val regexPattern = "^/(.+)/$".toRegex()

    private val globalAppContext by lazy { GlobalAppContext.get() }

    @JvmStatic
    fun str(@LocaleNonRelated resId: Int, vararg args: Any): String = globalAppContext.getString(resId, *args)

    @JvmStatic
    fun key(@LocaleNonRelated resId: Int): String = globalAppContext.getString(resId)

    // @Overwrite by SuperMonster003 on May 5, 2022.
    //  ! Dunno what "better implementation" really means.
    //  ! The only thing I did was make code more "lightweight". ;)
    //  ! zh-CN:
    //  ! 不清楚 "更好的实现方式" 的真正含义.
    //  ! 唯一做的只是使代码更 "轻量级". [眨眼符号]
    // @TodoDiary by Stardust on Jan 30, 2018.
    //  ! 更好的实现方式.
    //  ! en-US (translated by SuperMonster003 on Jul 29, 2024):
    //  ! Better implementation.
    @JvmStatic
    fun convertRegex(regex: String) = regex.apply { if (shouldTakenAsRegex(this)) return replace(regexPattern, "$1") }

    @JvmStatic
    fun shouldTakenAsRegex(s: String) = regexPattern.containsMatchIn(s)

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
    fun getStringByLocal(id: Int, locale: String): String {
        val configuration = Configuration(globalAppContext.resources.configuration)
        configuration.setLocale(Locale(locale))
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
    fun uppercaseFirstChar(s: String) = if (s.isEmpty()) s else s.replaceFirstChar { s[0].uppercaseChar() }

    @JvmStatic
    fun lowercaseFirstChar(s: String) = if (s.isEmpty()) s else s.replaceFirstChar { s[0].lowercaseChar() }

    @JvmStatic
    @Deprecated("Deprecated since v6.6.0", ReplaceWith("uppercaseFirstChar(s)"))
    fun toUpperCaseFirst(s: String) = uppercaseFirstChar(s)

}