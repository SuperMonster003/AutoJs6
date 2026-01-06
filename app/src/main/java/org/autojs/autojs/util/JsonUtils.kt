package org.autojs.autojs.util

import org.json.JSONTokener

/**
 * Created by SuperMonster003 on Nov 20, 2024.
 */
object JsonUtils {

    @JvmStatic
    fun repairJson(input: String): String {
        // 将输入字符串进行初步处理
        var json = input.replace("\n", " ").trim()

        // 修复缺少的逗号
        json = fixMissingCommas(json)

        // 修复不匹配的引号
        json = fixQuotes(json)

        // 修复不匹配的括号
        json = fixBraces(json)

        return json
    }

    private fun fixMissingCommas(input: String): String {
        val pattern = "\"\\s*\"".toRegex()
        return pattern.replace(input) { result ->
            val match = result.value
            match[0] + "," + match.substring(1)
        }
    }

    private fun fixQuotes(input: String): String {
        val chars = input.toCharArray()
        val corrected = StringBuilder()
        var inQuote = false
        var quoteCount = 0
        for (i in chars.indices) {
            if (chars[i] == '\"') {
                inQuote = !inQuote
                quoteCount++
            }
            if (chars[i] == ':' && !inQuote && i > 0 && chars[i - 1] != '\"') {
                corrected.append('\"')
            }
            corrected.append(chars[i])
            if (chars[i] == ':' && i + 1 < chars.size && chars[i + 1] != '\"') {
                corrected.append('\"')
            }
        }
        if (quoteCount % 2 != 0) {
            corrected.append('\"')
        }
        return corrected.toString()
    }

    private fun fixBraces(input: String): String {
        var openBraces = 0
        var closeBraces = 0
        var json = input
        for (char in input) {
            if (char == '{') openBraces++
            if (char == '}') closeBraces++
        }
        while (openBraces > closeBraces) {
            json += '}'
            closeBraces++
        }
        while (closeBraces > openBraces) {
            json = "{$json"
            openBraces++
        }
        return json
    }

    /**
     * This method checks whether the given string is a valid JSON format.
     *
     * This method supports the following common JSON formats:
     * - Objects (e.g., `{"key": "value"}`)
     * - Arrays (e.g., `[1, 2, 3]`)
     * - Single values (e.g., `"string"`, `123`, `true`, `null`)
     *
     * Note:
     * 1. Empty strings or strings containing only whitespace are considered invalid JSON.
     * 2. If the JSON contains comments (e.g., `// comment` or `/* multi-line comment */`),
     *    this method will consider it invalid. Because comments are not allowed in strict JSON standards.
     *
     * zh-CN:
     *
     * 判断给定的字符串是否是有效的 JSON 格式.
     *
     * 支持以下几种常见 JSON 格式:
     * - 对象 (例如: `{"key": "value"}`)
     * - 数组 (例如: `[1, 2, 3]`)
     * - 单一值 (例如: `"string"`, `123`, `true`, `null`)
     *
     * 注意:
     * 1. 空字符串或仅包含空白字符的字符串被视为无效的 JSON.
     * 2. 如果 JSON 中包含注释 (例如: `// 注释` 或 `/* 多行注释 */`), 此方法会判断为无效. 因为注释不符合严格的 JSON 规范.
     */
    @JvmStatic
    fun isValidJson(json: String) = json.isNotBlank() && runCatching { JSONTokener(json).nextValue() }.isSuccess

}