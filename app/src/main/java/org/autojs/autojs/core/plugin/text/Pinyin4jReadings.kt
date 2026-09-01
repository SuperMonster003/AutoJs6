package org.autojs.autojs.core.plugin.text

import android.os.Bundle
import org.autojs.plugin.pinyin4j.api.Pinyin4jOptionKeys
import org.autojs.plugin.pinyin4j.api.Pinyin4jPluginCapabilityKeys
import org.autojs.plugin.pinyin4j.api.Pinyin4jReadingsContract
import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
import org.json.JSONArray
import org.json.JSONException
import java.nio.charset.StandardCharsets

internal object Pinyin4jReadingsNegotiator {

    fun supportsNativeReadings(capabilities: Bundle?): Boolean {
        return runCatching {
            capabilities?.getInt(Pinyin4jPluginCapabilityKeys.READINGS_VERSION, 0) ?: 0
        }.getOrDefault(0) >= Pinyin4jPluginCapabilityKeys.READINGS_VERSION_1
    }

    fun read(
        text: String,
        options: Bundle,
        capabilities: Bundle?,
        nativeRead: () -> String,
        legacyOf: (String, Bundle) -> String,
    ): List<List<String>> {
        Pinyin4jReadingMatrixCodec.requireValidSource(text)
        if (supportsNativeReadings(capabilities)) {
            return Pinyin4jReadingMatrixCodec.decode(text, nativeRead())
        }

        val romanization = options.getString(Pinyin4jOptionKeys.ROMANIZATION)
            ?: options.getString(Pinyin4jOptionKeys.SYSTEM)
            ?: Pinyin4jRomanizationSystems.HANYU
        check(romanization == Pinyin4jRomanizationSystems.HANYU) {
            "Selected Pinyin4j plugin does not support readings v1 or $romanization romanization; update the plugin"
        }

        val legacyOptions = Bundle(options).apply {
            remove(Pinyin4jOptionKeys.ROMANIZATION)
            remove(Pinyin4jOptionKeys.SYSTEM)
        }
        val rows = buildList {
            var offset = 0
            while (offset < text.length) {
                val codePoint = text.codePointAt(offset)
                val source = String(Character.toChars(codePoint))
                add(listOf(legacyOf(source, legacyOptions).ifEmpty { source }))
                offset += Character.charCount(codePoint)
            }
        }
        Pinyin4jReadingMatrixCodec.requireValidRows(text, rows)
        return rows
    }
}

internal object Pinyin4jReadingMatrixCodec {

    fun decode(text: String, json: String): List<List<String>> {
        requireValidSource(text)
        require(json.toByteArray(StandardCharsets.UTF_8).size <= Pinyin4jReadingsContract.MAX_RESULT_JSON_BYTES) {
            "Pinyin4j readings result exceeds the JSON byte limit"
        }
        val array = try {
            JSONArray(json)
        } catch (error: JSONException) {
            throw IllegalArgumentException("Pinyin4j readings result is not a JSON array", error)
        }
        val rows = (0 until array.length()).map { rowIndex ->
            val row = array.optJSONArray(rowIndex)
                ?: throw IllegalArgumentException("Pinyin4j readings row $rowIndex must be an array")
            require(row.length() in 1..Pinyin4jReadingsContract.MAX_CANDIDATES_PER_CODE_POINT) {
                "Pinyin4j readings row $rowIndex has an invalid candidate count"
            }
            (0 until row.length()).map { itemIndex ->
                val value = row.opt(itemIndex) as? String
                    ?: throw IllegalArgumentException("Pinyin4j readings item [$rowIndex][$itemIndex] must be a string")
                require(
                    value.isNotEmpty() &&
                        value.codePointCount(0, value.length) <= Pinyin4jReadingsContract.MAX_READING_CODE_POINTS,
                ) {
                    "Pinyin4j readings item [$rowIndex][$itemIndex] has an invalid length"
                }
                value
            }
        }
        requireValidRows(text, rows)
        return rows
    }

    fun encode(rows: List<List<String>>): String {
        return JSONArray().apply {
            rows.forEach { row -> put(JSONArray(row)) }
        }.toString().also { json ->
            require(json.toByteArray(StandardCharsets.UTF_8).size <= Pinyin4jReadingsContract.MAX_RESULT_JSON_BYTES) {
                "Pinyin4j readings result exceeds the JSON byte limit"
            }
        }
    }

    fun requireValidSource(text: String) {
        require(text.codePointCount(0, text.length) <= Pinyin4jReadingsContract.MAX_SOURCE_CODE_POINTS) {
            "Pinyin4j readings source exceeds the code point limit"
        }
    }

    fun requireValidRows(text: String, rows: List<List<String>>) {
        val expectedRows = text.codePointCount(0, text.length)
        require(rows.size == expectedRows) {
            "Pinyin4j readings result row count ${rows.size} does not match source code point count $expectedRows"
        }
        rows.forEachIndexed { rowIndex, row ->
            require(row.size in 1..Pinyin4jReadingsContract.MAX_CANDIDATES_PER_CODE_POINT) {
                "Pinyin4j readings row $rowIndex has an invalid candidate count"
            }
            row.forEachIndexed { itemIndex, value ->
                require(
                    value.isNotEmpty() &&
                        value.codePointCount(0, value.length) <= Pinyin4jReadingsContract.MAX_READING_CODE_POINTS,
                ) {
                    "Pinyin4j readings item [$rowIndex][$itemIndex] has an invalid length"
                }
            }
        }
    }
}
