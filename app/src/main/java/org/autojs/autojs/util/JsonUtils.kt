package org.autojs.autojs.util

import org.json.JSONArray
import org.json.JSONObject

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
            json = '{' + json
            openBraces++
        }
        return json
    }

    fun isValidJson(json: String): Boolean = runCatching { JSONObject(json) }.isSuccess || runCatching { JSONArray(json) }.isSuccess

}