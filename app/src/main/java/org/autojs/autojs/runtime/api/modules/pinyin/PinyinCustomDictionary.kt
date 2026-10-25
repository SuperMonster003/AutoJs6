package org.autojs.autojs.runtime.api.modules.pinyin

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.plugin.pinyin.api.PinyinCustomDictionaryContract
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.nio.charset.StandardCharsets

internal object PinyinCustomDictionary {

    fun encodeRhino(value: Any?): String? {
        if (value.isJsNullish()) return null
        val dictionary = value as? NativeObject
            ?: throw IllegalArgumentException("pinyin customDictionary must be a plain object")
        require(dictionary.ids.size <= PinyinCustomDictionaryContract.MAX_ENTRIES) {
            "pinyin customDictionary exceeds ${PinyinCustomDictionaryContract.MAX_ENTRIES} entries"
        }

        val entries = linkedMapOf<String, List<List<String>>>()
        dictionary.ids.forEach { id ->
            val key = id as? String
                ?: throw IllegalArgumentException("pinyin customDictionary keys must be strings")
            val rawRows = ScriptableObject.getProperty(dictionary, key)
            entries[key] = parseRows(key, rawRows)
        }
        return encodeEntries(entries)
    }

    fun encodeJson(value: JSONObject?): String? {
        if (value == null) return null
        require(value.length() <= PinyinCustomDictionaryContract.MAX_ENTRIES) {
            "pinyin customDictionary exceeds ${PinyinCustomDictionaryContract.MAX_ENTRIES} entries"
        }
        val entries = linkedMapOf<String, List<List<String>>>()
        value.keys().forEach { key ->
            val rows = value.optJSONArray(key)
                ?: throw IllegalArgumentException("pinyin customDictionary[$key] must be an array of candidate arrays")
            entries[key] = (0 until rows.length()).map { rowIndex ->
                val candidates = rows.optJSONArray(rowIndex)
                    ?: throw IllegalArgumentException("pinyin customDictionary[$key][$rowIndex] must be an array")
                (0 until candidates.length()).map { candidateIndex ->
                    candidates.opt(candidateIndex) as? String
                        ?: throw IllegalArgumentException(
                            "pinyin customDictionary[$key][$rowIndex][$candidateIndex] must be a string",
                        )
                }
            }
        }
        return encodeEntries(entries)
    }

    internal fun encodeEntries(entries: Map<String, List<List<String>>>): String? {
        if (entries.isEmpty()) return null
        require(entries.size <= PinyinCustomDictionaryContract.MAX_ENTRIES) {
            "pinyin customDictionary exceeds ${PinyinCustomDictionaryContract.MAX_ENTRIES} entries"
        }

        val result = JSONObject()
        entries.keys.sorted().forEach { key ->
            val rows = entries.getValue(key)
            validateEntry(key, rows)
            result.put(key, JSONArray().apply {
                rows.forEach { candidates ->
                    put(JSONArray().apply { candidates.forEach(::put) })
                }
            })
        }
        return result.toString().also { json ->
            require(json.toByteArray(StandardCharsets.UTF_8).size <= PinyinCustomDictionaryContract.MAX_JSON_BYTES) {
                "pinyin customDictionary exceeds ${PinyinCustomDictionaryContract.MAX_JSON_BYTES} UTF-8 bytes"
            }
        }
    }

    private fun parseRows(key: String, value: Any?): List<List<String>> {
        val rows = value.jsUnwrapped() as? NativeArray
            ?: throw IllegalArgumentException("pinyin customDictionary[$key] must be an array of candidate arrays")
        require(rows.length <= PinyinCustomDictionaryContract.MAX_KEY_CODE_POINTS.toLong()) {
            "pinyin customDictionary[$key] has too many rows"
        }
        return (0 until rows.length.toInt()).map { rowIndex ->
            val rawCandidates = ScriptableObject.getProperty(rows, rowIndex).jsUnwrapped()
            val candidates = rawCandidates as? NativeArray
                ?: throw IllegalArgumentException("pinyin customDictionary[$key][$rowIndex] must be an array")
            require(candidates.length <= PinyinCustomDictionaryContract.MAX_CANDIDATES_PER_CODE_POINT.toLong()) {
                "pinyin customDictionary[$key][$rowIndex] has too many candidates"
            }
            (0 until candidates.length.toInt()).map { candidateIndex ->
                ScriptableObject.getProperty(candidates, candidateIndex).jsUnwrapped() as? String
                    ?: throw IllegalArgumentException(
                        "pinyin customDictionary[$key][$rowIndex][$candidateIndex] must be a string",
                    )
            }
        }
    }

    private fun validateEntry(key: String, rows: List<List<String>>) {
        val codePointCount = key.codePointCount(0, key.length)
        require(codePointCount in 1..PinyinCustomDictionaryContract.MAX_KEY_CODE_POINTS) {
            "pinyin customDictionary key length must be between 1 and ${PinyinCustomDictionaryContract.MAX_KEY_CODE_POINTS} code points"
        }
        require(PinyinCustomDictionaryContract.isHanKey(key)) {
            "pinyin customDictionary key must contain Han characters only: $key"
        }
        require(rows.size == codePointCount) {
            "pinyin customDictionary[$key] must contain exactly $codePointCount rows"
        }

        var combinations = 1
        rows.forEachIndexed { rowIndex, candidates ->
            require(candidates.isNotEmpty()) {
                "pinyin customDictionary[$key][$rowIndex] must not be empty"
            }
            require(candidates.size <= PinyinCustomDictionaryContract.MAX_CANDIDATES_PER_CODE_POINT) {
                "pinyin customDictionary[$key][$rowIndex] has too many candidates"
            }
            require(candidates.distinct().size == candidates.size) {
                "pinyin customDictionary[$key][$rowIndex] contains duplicate candidates"
            }
            candidates.forEachIndexed { candidateIndex, candidate ->
                require(PinyinCustomDictionaryContract.isCanonicalSyllable(candidate)) {
                    "pinyin customDictionary[$key][$rowIndex][$candidateIndex] is not a canonical lowercase pinyin syllable"
                }
            }
            combinations *= candidates.size
            require(combinations <= PinyinCustomDictionaryContract.MAX_COMBINATIONS_PER_ENTRY) {
                "pinyin customDictionary[$key] exceeds ${PinyinCustomDictionaryContract.MAX_COMBINATIONS_PER_ENTRY} combinations"
            }
        }
    }
}
