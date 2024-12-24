package com.huaban.analysis.jieba

import java.util.regex.Pattern

object CharacterUtil {

    var reSkip: Pattern = Pattern.compile("(\\d+\\.\\d+|[a-zA-Z0-9]+)")

    private val connectors = charArrayOf('+', '#', '&', '.', '_', '-')

    internal fun isChineseLetter(ch: Char) = ch.code in 0x4E00..0x9FA5

    internal fun isEnglishLetter(ch: Char) = ch.code in 0x0041..0x005A || ch.code in 0x0061..0x007A

    internal fun isDigit(ch: Char) = ch.code in 0x0030..0x0039

    private fun isConnector(ch: Char) = connectors.contains(ch)

    fun ccFind(ch: Char) = isChineseLetter(ch) || isEnglishLetter(ch) || isDigit(ch) || isConnector(ch)

    /**
     * 全角 to 半角,大写 to 小写
     *
     * @param input
     * 输入字符
     * @return 转换后的字符
     */
    fun regularize(input: Char) = when (input.code) {
        12288 -> 32.toChar()
        in 65281..65374 -> (input.code - 65248).toChar()
        in 'A'.code..'Z'.code -> input + 32
        else -> input
    }

}
