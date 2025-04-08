package org.autojs.autojs.runtime.api.augment.pinyin

import com.huaban.analysis.jieba.CharsDictionaryDatabase
import com.huaban.analysis.jieba.JiebaSegmenter
import com.huaban.analysis.jieba.PhrasesDictionaryDatabase
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.DefaultOptions
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.PinyinMode
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.PinyinStyle
import org.autojs.autojs.runtime.api.augment.pinyin.PinyinCore.parseStyle
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY

object Pinyin : Augmentable(), Invokable {

    private val charsDictDatabase by lazy { CharsDictionaryDatabase.getInstance(globalContext.applicationContext).database }
    private val phrasesDictDatabase by lazy { PhrasesDictionaryDatabase.getInstance(globalContext.applicationContext).database }

    @Suppress("SpellCheckingInspection")
    override val selfAssignmentProperties = listOf(
        "STYLE_NORMAL" to PinyinStyle.NORMAL,
        "STYLE_TONE" to PinyinStyle.TONE,
        "STYLE_TONE2" to PinyinStyle.TONE2,
        "STYLE_TO3NE" to PinyinStyle.TO3NE,
        "STYLE_INITIALS" to PinyinStyle.INITIALS,
        "STYLE_FIRST_LETTER" to PinyinStyle.FIRST_LETTER,
        "MODE_NORMAL" to PinyinMode.NORMAL,
        "MODE_SURNAME" to PinyinMode.SURNAME,
        "MODE_PLACENAME" to PinyinMode.PLACE_NAME,
        "MODE_PLACE_NAME" to PinyinMode.PLACE_NAME,
    )

    override val selfAssignmentFunctions = listOf(
        ::convert.name,
        ::simple.name,
        ::compare.name,
        ::compact.name,
        ::fromCodePoint.name,
        ::fromPhrase.name,
    )

    @RhinoSingletonFunctionInterface
    override fun invoke(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        convert(argList)
    }

    @RhinoSingletonFunctionInterface
    fun convert(args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        val (hansArg, options) = argList
        val hans = coerceString(hansArg, "")
        val opt = options as? NativeObject ?: newNativeObject()
        convertRhino(hans, opt).map { it.toNativeArray() }.toNativeArray().also { result ->
            val funcName = "compact"
            withRhinoContext { cx ->
                val thisObj = Context.javaToJS(Util, result) as Scriptable
                val boundFunction = BoundFunction(
                    /* cx = */ cx,
                    /* scope = */ result,
                    /* targetFunction = */ NativeJavaMethod(Util::class.java.methods.find { it.name == funcName }, funcName),
                    /* boundThis = */ thisObj,
                    /* boundArgs = */ arrayOf(result),
                )
                result.defineProp(funcName, boundFunction, READONLY or DONTENUM or PERMANENT)
            }
        }
    }

    fun convertRhino(hans: String, opt: NativeObject): List<List<String>> = when {
        hans.isEmpty() -> listOf()
        else -> {
            val phrases: List<String> = when {
                opt.inquire("segment", ::coerceBoolean, DefaultOptions.SEGMENT) -> {
                    segment(hans)
                }
                else -> groupChineseAndNonChinese(hans)
            }
            var nonHans = ""

            when (PinyinMode.SURNAME.value) {
                opt.inquire("mode", { o, def -> PinyinCore.parseMode(o) ?: def }, DefaultOptions.MODE.value) -> {
                    PinyinCore.surnamePinyin(hans, opt)
                }
                else -> {
                    val pinyinList = mutableListOf<List<String>>()
                    for (words in phrases) {
                        val firstCharCode = words.codePointAt(0)
                        if (fromCodePointInternal(firstCharCode) != null) {
                            if (nonHans.isNotEmpty()) {
                                pinyinList.add(listOf(nonHans))
                                nonHans = ""
                            }

                            val newPinyinList = when (words.length) {
                                1 -> PinyinCore.convert(words, opt)
                                else -> phrasePinyin(words, opt)
                            }

                            if (opt.inquire("group", ::coerceBoolean, false)) {
                                pinyinList.add(groupPhrases(newPinyinList))
                            } else {
                                pinyinList.addAll(newPinyinList)
                            }
                        } else {
                            nonHans += words
                        }
                    }
                    if (nonHans.isNotEmpty()) {
                        pinyinList.add(listOf(nonHans))
                    }
                    pinyinList
                }
            }
        }
    }

    @RhinoSingletonFunctionInterface
    fun compare(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        ""
    }

    @RhinoSingletonFunctionInterface
    fun compact(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) { argList ->
        ""
    }

    @RhinoSingletonFunctionInterface
    fun simple(args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) { argList ->
        val (str, enableNumericTone, enableSegment) = argList
        simpleRhino(coerceString(str, ""), coerceBoolean(enableNumericTone, false), coerceBoolean(enableSegment, false))
    }

    @JvmOverloads
    fun simpleRhino(s: String, enableNumericTone: Boolean = false, enableSegment: Boolean = false): String {
        return convertRhino(s, newNativeObject().also {
            it.defineProp("style", if (enableNumericTone) PinyinStyle.TONE2 else PinyinStyle.NORMAL)
            it.defineProp("segment", enableSegment)
        }).map { it.firstOrNull() ?: emptyList<String>() }.joinToString("")
    }

    @RhinoSingletonFunctionInterface
    fun fromCodePoint(args: Array<out Any?>): String? = ensureArgumentsOnlyOne(args) { o ->
        fromCodePointInternal(coerceIntNumber(o))
    }

    @RhinoSingletonFunctionInterface
    fun fromPhrase(args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) { o ->
        fromPhraseInternal(coerceString(o)).map { it.toNativeArray() }.toNativeArray()
    }

    internal fun fromCodePointInternal(codePoint: Int): String? = charsDictDatabase.let { db ->
        db.query(
            "Dict",
            arrayOf("Pinyin"),
            "CodePoint = ?",
            arrayOf(codePoint.toString()),
            null,
            null,
            null
        ).use { cursor ->
            when {
                cursor.moveToFirst() -> cursor.getString(cursor.getColumnIndexOrThrow("Pinyin"))
                else -> null
            }
        }
    }

    private fun fromPhraseInternal(phrase: String): List<List<String>> = phrasesDictDatabase.let { db ->
        val cursor = db.query(
            "PhrasesDict",
            arrayOf("Pinyin", "OrderIndex"),
            "Word = ?",
            arrayOf(phrase),
            null,
            null,
            "OrderIndex ASC",
        )

        val result = mutableListOf<MutableList<String>>()
        cursor.use {
            while (it.moveToNext()) {
                val pinyin = it.getString(it.getColumnIndexOrThrow("Pinyin"))
                val orderIndex = it.getInt(it.getColumnIndexOrThrow("OrderIndex"))
                while (result.size < orderIndex) {
                    result.add(mutableListOf())
                }
                result[orderIndex - 1].add(pinyin)
            }
        }
        result
    }

    private fun segment(hans: String): List<String> {
        return JiebaSegmenter(globalContext).cutSmall(hans, 4)
    }

    private fun phrasePinyin(phrase: String, options: NativeObject): List<List<String>> {
        val phrasePinyinList = fromPhraseInternal(phrase)
        return when {
            phrasePinyinList.isNotEmpty() -> {
                val style = options.inquire("style", { o, def -> parseStyle(o) ?: def }, DefaultOptions.STYLE.value)
                phrasePinyinList.map { item ->
                    if (options.inquire("heteronym", ::coerceBoolean, DefaultOptions.HETERONYM)) {
                        item.map { pyItem -> PinyinCore.toFixed(pyItem, style) }.toMutableList()
                    } else {
                        mutableListOf(PinyinCore.toFixed(item[0], style))
                    }
                }.toMutableList()
            }
            else -> PinyinCore.convert(phrase, options)
        }
    }

    private fun groupPhrases(phrases: List<List<String>>): List<String> {
        return when (phrases.size) {
            1 -> phrases[0]
            else -> Util.combo(phrases)
        }
    }

    private fun groupChineseAndNonChinese(input: String): List<String> {
        if (input.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        val chineseRegex = "\\p{IsHan}".toRegex() // 匹配中文字符的正则
        var tempGroup = StringBuilder()
        var isLastChinese = chineseRegex.matches(input[0].toString()) // 判断第一个字符是否中文

        for (char in input) {
            val isChinese = chineseRegex.matches(char.toString())
            // 如果字符类型（中文或非中文）发生切换，保存当前分组
            if (isChinese != isLastChinese) {
                result.add(tempGroup.toString()) // 添加当前分组
                tempGroup = StringBuilder() // 重置分组
            }
            tempGroup.append(char) // 将字符添加到当前分组
            isLastChinese = isChinese // 更新当前字符类型
        }
        // 添加最后一个分组
        if (tempGroup.isNotEmpty()) {
            result.add(tempGroup.toString())
        }

        return result
    }

}