package org.autojs.autojs.runtime.api.augment.pinyin

import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject
import java.util.regex.Pattern

object PinyinCore {

    private val SURNAME = Dict.SURNAME
    private val COMPOUND_SURNAME = Dict.COMPOUND_SURNAME

    // 声母表
    private val INITIALS = arrayOf(
        "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x",
        "zh", "ch", "sh", "r", "z", "c", "s",
    )

    // 韵母表
    private val FINALS = arrayOf(
        "a", "o", "e", "i", "u", "v",
        "ai", "ei", "ui", "ao", "ou", "iu", "ie", "ve", "er",
        "an", "en", "in", "un", "vn",
        "ang", "eng", "ing", "ong",
    )

    // 带声调字符
    private val PHONETIC_SYMBOL = Dict.PHONETIC_SYMBOL
    private val RE_PHONETIC_SYMBOL = Pattern.compile("([${PHONETIC_SYMBOL.keys.joinToString("")}])").toRegex()
    private val RE_TONE2 = Regex("([aeoiuvnm])([0-4])$")

    /**
     * @param hansArg 要转为拼音的目标字符串 (汉字).
     * @param options 可选, 用于指定拼音风格, 是否启用多音字.
     * @return 返回的拼音列表.
     */
    fun convert(hansArg: String, options: NativeObject = newNativeObject()): MutableList<List<String>> {
        val pinyinList = mutableListOf<List<String>>()
        val hans = coerceString(hansArg, "").trim()

        if (hans.isNotEmpty()) {
            var nonHans = ""

            when (PinyinMode.SURNAME.value) {
                options.inquire("mode", { o, def -> parseMode(o) ?: def }, DefaultOptions.MODE.value) -> {
                    pinyinList.addAll(surnamePinyin(hans, options))
                }
                else -> hans.forEach { char ->
                    when {
                        Pinyin.fromCodePointInternal(char.code) != null -> {
                            // 拼音转换
                            if (nonHans.isNotEmpty()) {
                                pinyinList.add(listOf(nonHans))
                                nonHans = ""
                            }
                            pinyinList.add(singlePinyin(char.toString(), options))
                        }
                        else -> nonHans += char
                    }
                }
            }

            if (nonHans.isNotEmpty()) pinyinList.add(listOf(nonHans))
        }

        return pinyinList
    }

    /**
     * 比较两个汉字转成拼音后的排序顺序, 可以用作默认的拼音排序算法.
     *
     * @param hanA 汉字字符串 A.
     * @param hanB 汉字字符串 B.
     * @return -1, 0 或 1.
     */
    fun compare(hanA: String, hanB: String): Int {
        return coerceString(convert(hanA)).compareTo(coerceString(convert(hanB)))
    }

    fun compact(arr: List<List<String>>) = Util.compact(arr)

    /**
     * 单字拼音转换.
     *
     * @param han 单个汉字.
     * @param options 选项.
     * @return 返回拼音列表, 多音字会有多个拼音项.
     */
    private fun singlePinyin(han: String, options: NativeObject): List<String> {
        require(han.isNotEmpty()) { "Argument han must not be empty" }
        val hanCode = han.first().code
        val consulted = Pinyin.fromCodePointInternal(hanCode)
        val pinyinList = consulted?.split(",") ?: return listOf(han.first().toString())
        val style = options.inquire("style", { o, def -> parseStyle(o) ?: def }, DefaultOptions.STYLE.value)
        return when {
            options.inquire("heteronym", ::coerceBoolean, DefaultOptions.HETERONYM) -> {
                pinyinList.map { toFixed(it, style) }.distinct()
            }
            else -> listOf(toFixed(pinyinList.first(), style))
        }
    }

    /**
     * 单姓处理, 将单个姓氏转换为拼音.
     *
     * @param hans 姓氏字符串.
     * @param options 转换选项 (包括风格、多音字选项等).
     * @return 转换后的拼音列表.
     */
    private fun singleSurname(hans: String, options: NativeObject): List<List<String>> {
        val result = mutableListOf<List<String>>()

        // 遍历所有汉字
        hans.forEach { char ->
            val word = char.toString()
            when {
                // 如果是已知的姓氏, 从预定义数据中获取拼音, 并格式化
                SURNAME.containsKey(word) -> SURNAME[word]?.map { toFixedList(it, options) }?.let { surnamePinyin ->
                    result.addAll(surnamePinyin)
                }
                // 如果是未知姓氏, 则按一般规则转换为拼音
                else -> result.add(singlePinyin(word, options))
            }
        }

        return result
    }

    /**
     * 姓名转换为拼音.
     */
    fun surnamePinyin(hans: String, options: NativeObject): List<List<String>> {
        return compoundSurname(hans, options)
    }

    /**
     * 复姓处理, 将复姓转换为拼音.
     *
     * @param hans 包含复姓的字符串.
     * @param options 转换选项 (包含拼音风格等).
     * @return 转换后的拼音列表.
     */
    private fun compoundSurname(hans: String, options: NativeObject): List<List<String>> {
        val len = hans.length
        var prefixIndex = 0
        val result = mutableListOf<List<String>>()

        var i = 0
        while (i < len) {
            // 获取两个连续字符, 检测是否是复姓
            val twoWords = hans.substring(i, (i + 2).coerceAtMost(len))

            if (!COMPOUND_SURNAME.containsKey(twoWords)) {
                i++
                continue
            }

            // 如果前缀内容存在 (非复姓部分), 处理非复姓部分
            if (prefixIndex < i) {
                val nonCompoundPart = hans.substring(prefixIndex, i)
                result.addAll(singleSurname(nonCompoundPart, options)) // 调用单姓处理函数
            }

            // 处理复姓部分, 并格式化为指定风格
            COMPOUND_SURNAME[twoWords]?.map { toFixedList(it, options) }?.let { compoundPart ->
                result.addAll(compoundPart)
            }

            // 跳过复姓的两个字
            i += 2
            prefixIndex = i
        }

        // 处理复姓最后剩下的部分 (即非复姓部分)
        if (prefixIndex < len) {
            val remaining = hans.substring(prefixIndex, len)
            result.addAll(singleSurname(remaining, options))
        }

        return result
    }

    /**
     * 格式化拼音风格.
     *
     * @param pinyin TONE 风格的拼音.
     * @param style 目标转换的拼音风格.
     * @return 转换后的拼音.
     */
    internal fun toFixed(pinyin: String, style: Int): String = when (style) {
        PinyinStyle.NORMAL.value -> pinyin.replace(RE_PHONETIC_SYMBOL) { matchResult ->
            PHONETIC_SYMBOL[matchResult.groupValues[1]]?.replace(RE_TONE2, "$1") ?: matchResult.value
        }
        PinyinStyle.INITIALS.value -> initials(pinyin)
        PinyinStyle.FIRST_LETTER.value -> pinyin.first().toString()
        PinyinStyle.TONE.value -> pinyin
        PinyinStyle.TONE2.value -> {
            var tone = ""
            val py = pinyin.replace(RE_PHONETIC_SYMBOL) { matchResult ->
                PHONETIC_SYMBOL[matchResult.groupValues[1]]?.let { s ->
                    s.replace(RE_TONE2, "$2").also { tone = it }
                    s.replace(RE_TONE2, "$1")
                } ?: matchResult.value
            }
            py + tone
        }
        PinyinStyle.TO3NE.value -> pinyin.replace(RE_PHONETIC_SYMBOL) { matchResult ->
            PHONETIC_SYMBOL[matchResult.groupValues[1]] ?: matchResult.value
        }
        else -> pinyin
    }

    private fun toFixedList(items: List<String>, options: NativeObject): List<String> {
        val style = options.inquire("style", { o, def -> parseStyle(o) ?: def }, DefaultOptions.STYLE.value)
        return items.map { toFixed(it, style) }
    }

    /**
     * 格式化拼音为声母 (Initials) 形式.
     */
    private fun initials(pinyin: String) = INITIALS.find { pinyin.startsWith(it) } ?: ""

    internal fun parseMode(o: Any): Int? {
        val obj = o.jsUnwrapped()
        return when {
            obj.isJsNullish() -> null
            obj is PinyinMode -> obj.value
            obj is Int -> {
                require(PinyinMode.entries.any { it.value == obj }) { "Invalid mode constant: $obj" }
                obj
            }
            else -> coerceString(obj, "").uppercase().let { name ->
                PinyinMode.entries.find { it.name == name }?.value ?: throw IllegalArgumentException("Invalid mode name: $obj")
            }
        }
    }

    internal fun parseStyle(o: Any): Int? {
        val obj = o.jsUnwrapped()
        return when {
            obj.isJsNullish() -> null
            obj is PinyinStyle -> obj.value
            obj is Int -> {
                require(PinyinStyle.entries.any { it.value == obj }) { "Invalid style constant: $obj" }
                obj
            }
            else -> coerceString(obj, "").uppercase().let { name ->
                PinyinStyle.entries.find { it.name == name }?.value ?: throw IllegalArgumentException("Invalid style name: $obj")
            }
        }
    }

    enum class PinyinStyle(val value: Int) {
        NORMAL(0), // 普通风格, 不带声调
        TONE(1), // 标准风格, 声调在韵母上
        TONE2(2), // 数字形式标记声调
        TO3NE(5), // 数字方式, 声母后加声调
        INITIALS(3), // 仅保留声母
        FIRST_LETTER(4), // 仅保留首字母
    }

    @Suppress("SpellCheckingInspection")
    enum class PinyinMode(val value: Int) {
        NORMAL(0), // 普通模式
        SURNAME(1), // 姓氏模式
        PLACE_NAME(2), // 地名模式
        PLACENAME(2), // 地名模式 (兼容)
    }

    object DefaultOptions {
        val MODE = PinyinMode.NORMAL // 默认模式
        val STYLE = PinyinStyle.TONE // 默认风格
        const val SEGMENT: Boolean = false // 是否分词
        const val HETERONYM: Boolean = false // 是否允许多音字
    }

}