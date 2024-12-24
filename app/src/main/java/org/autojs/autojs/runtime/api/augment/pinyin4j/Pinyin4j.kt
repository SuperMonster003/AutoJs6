package org.autojs.autojs.runtime.api.augment.pinyin4j

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.LOWERCASE
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.UPPERCASE
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITHOUT_TONE
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITH_TONE_MARK
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITH_TONE_NUMBER
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_U_AND_COLON
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_U_UNICODE
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_V
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.coerceStringUppercase
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject

object Pinyin4j : Augmentable(), Invokable {

    private const val DEFAULT_SEPARATOR = ""

    private const val DEFAULT_CASE_TYPE_STRING = "LOWERCASE"
    private val DEFAULT_CASE_TYPE = LOWERCASE

    private const val DEFAULT_TONE_TYPE_STRING = "WITHOUT_TONE"
    private val DEFAULT_TONE_TYPE = WITHOUT_TONE

    private const val DEFAULT_V_CHAR_TYPE_STRING = "WITH_V"
    private val DEFAULT_V_CHAR_TYPE = WITH_V

    private val pinyinToneMap = mapOf(
        // 单韵母
        "a1" to "ā", "a2" to "á", "a3" to "ǎ", "a4" to "à", "a0" to "a",
        "o1" to "ō", "o2" to "ó", "o3" to "ǒ", "o4" to "ò", "o0" to "o",
        "e1" to "ē", "e2" to "é", "e3" to "ě", "e4" to "è", "e0" to "e",
        "i1" to "ī", "i2" to "í", "i3" to "ǐ", "i4" to "ì", "i0" to "i",
        "u1" to "ū", "u2" to "ú", "u3" to "ǔ", "u4" to "ù", "u0" to "u",
        "ü1" to "ǖ", "ü2" to "ǘ", "ü3" to "ǚ", "ü4" to "ǜ", "ü0" to "ü",
        "v1" to "ǖ", "v2" to "ǘ", "v3" to "ǚ", "v4" to "ǜ", "v0" to "ü",
        "u:1" to "ǖ", "u:2" to "ǘ", "u:3" to "ǚ", "u:4" to "ǜ", "u:0" to "ü",

        // 复韵母 - 普通
        "ai1" to "āi", "ai2" to "ái", "ai3" to "ǎi", "ai4" to "ài", "ai0" to "ai",
        "ei1" to "ēi", "ei2" to "éi", "ei3" to "ěi", "ei4" to "èi", "ei0" to "ei",
        "ui1" to "uī", "ui2" to "uí", "ui3" to "uǐ", "ui4" to "uì", "ui0" to "ui",
        "ao1" to "āo", "ao2" to "áo", "ao3" to "ǎo", "ao4" to "ào", "ao0" to "ao",
        "ou1" to "ōu", "ou2" to "óu", "ou3" to "ǒu", "ou4" to "òu", "ou0" to "ou",
        "iu1" to "iū", "iu2" to "iú", "iu3" to "iǔ", "iu4" to "iù", "iu0" to "iu",
        "ie1" to "iē", "ie2" to "ié", "ie3" to "iě", "ie4" to "iè", "ie0" to "ie",
        "üe1" to "üē", "üe2" to "üé", "üe3" to "üě", "üe4" to "üè", "üe0" to "üe",
        "u:e1" to "üē", "u:e2" to "üé", "u:e3" to "üě", "u:e4" to "üè", "u:e0" to "üe",
        "ve1" to "üē", "ve2" to "üé", "ve3" to "üě", "ve4" to "üè", "ve0" to "üe",

        // 复韵母 - 特殊
        "er1" to "ēr", "er2" to "ér", "er3" to "ěr", "er4" to "èr", "er0" to "er",

        // 复韵母 - 前鼻韵母
        "an1" to "ān", "an2" to "án", "an3" to "ǎn", "an4" to "àn", "an0" to "an",
        "en1" to "ēn", "en2" to "én", "en3" to "ěn", "en4" to "èn", "en0" to "en",
        "in1" to "īn", "in2" to "ín", "in3" to "ǐn", "in4" to "ìn", "in0" to "in",
        "un1" to "ūn", "un2" to "ún", "un3" to "ǔn", "un4" to "ùn", "un0" to "un",
        "ün1" to "ǖn", "ün2" to "ǘn", "ün3" to "ǚn", "ün4" to "ǜn", "ün0" to "ün",
        "u:n1" to "ǖn", "u:n2" to "ǘn", "u:n3" to "ǚn", "u:n4" to "ǜn", "u:n0" to "ün",
        "vn1" to "ǖn", "vn2" to "ǘn", "vn3" to "ǚn", "vn4" to "ǜn", "vn0" to "ün",

        // 复韵母 - 后鼻韵母
        "ang1" to "āng", "ang2" to "áng", "ang3" to "ǎng", "ang4" to "àng", "ang0" to "ang",
        "eng1" to "ēng", "eng2" to "éng", "eng3" to "ěng", "eng4" to "èng", "eng0" to "eng",
        "ing1" to "īng", "ing2" to "íng", "ing3" to "ǐng", "ing4" to "ìng", "ing0" to "ing",
        "ong1" to "ōng", "ong2" to "óng", "ong3" to "ǒng", "ong4" to "òng", "ong0" to "ong"
    )

    override val selfAssignmentFunctions = listOf(
        ::`as`.name,
        ::of.name,
    )

    @RhinoSingletonFunctionInterface
    override fun invoke(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        of(argList)
    }

    @RhinoSingletonFunctionInterface
    fun of(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        val (string, options) = argList

        val str = coerceString(string, "").takeIf { it.isNotEmpty() } ?: return@ensureArgumentsLengthInRange ""
        var separator = DEFAULT_SEPARATOR
        val opt = when (options) {
            is String -> newNativeObject().also { separator = options }
            is NativeObject -> options
            else -> newNativeObject()
        }

        separator = opt.inquire(listOf("separator", "sep"), ::coerceString, separator)

        val caseType = when (val case = opt.inquire(listOf("case", "caseType"))) {
            is HanyuPinyinCaseType -> case
            else -> when (coerceStringUppercase(case, DEFAULT_CASE_TYPE_STRING)) {
                "LOWERCASE", "LOW", "L", "0" -> LOWERCASE
                "UPPERCASE", "UP", "U", "1" -> UPPERCASE
                else -> throw IllegalArgumentException("Invalid case type: ${Context.toString(case)}")
            }
        }
        val toneType = when (val tone = opt.inquire(listOf("tone", "toneType"))) {
            is HanyuPinyinToneType -> tone
            else -> when (coerceStringUppercase(tone, DEFAULT_TONE_TYPE_STRING)) {
                "WITH_TONE_NUMBER", "WITH_NUMBER", "NUMBER", "NUM" -> WITH_TONE_NUMBER
                "WITHOUT_TONE", "NO_TONE", "NO", "FALSE", "0" -> WITHOUT_TONE
                "WITH_TONE_MARK", "WITH_MARK", "MARK", "TRUE", "1" -> WITH_TONE_MARK
                else -> throw IllegalArgumentException("Invalid tone type: ${Context.toString(tone)}")
            }
        }
        val vCharType = when (val vChar = opt.inquire(listOf("v", "vChar", "vCharType"))) {
            is HanyuPinyinVCharType -> vChar
            else -> when (coerceStringUppercase(vChar, if (toneType == WITH_TONE_MARK) "WITH_U_UNICODE" else DEFAULT_V_CHAR_TYPE_STRING)) {
                "WITH_U_AND_COLON", "U_AND_COLON", "U_COLON", "U:" -> WITH_U_AND_COLON
                "WITH_V", "V" -> WITH_V
                "WITH_U_UNICODE", "U_UNICODE", "UNICODE", "Ü" -> WITH_U_UNICODE
                else -> throw IllegalArgumentException("Invalid v char type: ${Context.toString(vChar)}")
            }
        }
        ofRhino(str, separator, caseType, toneType, vCharType)
    }

    @JvmStatic
    @JvmOverloads
    fun ofRhino(
        str: String,
        separator: String = DEFAULT_SEPARATOR,
        caseType: HanyuPinyinCaseType = DEFAULT_CASE_TYPE,
        toneType: HanyuPinyinToneType = DEFAULT_TONE_TYPE,
        vCharType: HanyuPinyinVCharType = DEFAULT_V_CHAR_TYPE,
    ): String {
        val format = HanyuPinyinOutputFormat().also {
            it.caseType = caseType
            it.toneType = toneType
            it.vCharType = vCharType
        }

        val pinyinResult = StringBuilder()

        str.forEach { ch ->
            val pinyin4j = when (ch) {
                in '\u4E00'..'\u9FA5' -> {
                    PinyinHelper.toHanyuPinyinStringArray(ch, format)?.firstOrNull() ?: ""
                }
                else -> ch.toString()
            }
            pinyinResult.append(pinyin4j).append(separator)
        }

        return pinyinResult.toString().trim()
    }

    @RhinoSingletonFunctionInterface
    fun `as`(args: Array<out Any?>): String = ensureArgumentsOnlyOne(args) { o ->
        convertToTonePinyin(coerceString(o, ""))
    }

    private fun convertToTonePinyin(input: String): String {
        val regex = Regex("(üe|u:e|ve|ue|ang|eng|ing|ong|ai|ei|ui|ao|ou|iu|ie|er|an|en|in|un|ün|u:n|vn|[aoeiuüv])([0-4])")
        return regex.replace(input) { matchResult ->
            val pinyinBase = matchResult.groupValues[1]
            val toneNumber = matchResult.groupValues[2]
            pinyinToneMap[pinyinBase + toneNumber] ?: matchResult.value
        }
    }

}