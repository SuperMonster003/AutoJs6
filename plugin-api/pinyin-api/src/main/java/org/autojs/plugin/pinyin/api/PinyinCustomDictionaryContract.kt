package org.autojs.plugin.pinyin.api

import java.text.Normalizer
import java.util.Locale

object PinyinCustomDictionaryContract {
    const val MAX_JSON_BYTES = 64 * 1024
    const val MAX_ENTRIES = 1024
    const val MAX_KEY_CODE_POINTS = 32
    const val MAX_CANDIDATES_PER_CODE_POINT = 8
    const val MAX_SYLLABLE_CODE_POINTS = 16
    const val MAX_COMBINATIONS_PER_ENTRY = 256

    fun isHanKey(value: String): Boolean {
        if (value.isEmpty()) return false
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) return false
            index += Character.charCount(codePoint)
        }
        return true
    }

    fun isCanonicalSyllable(value: String): Boolean {
        if (value.isEmpty() || value != value.trim()) return false
        if (value != value.lowercase(Locale.ROOT)) return false
        if (value != Normalizer.normalize(value, Normalizer.Form.NFC)) return false
        if (value.codePointCount(0, value.length) > MAX_SYLLABLE_CODE_POINTS) return false

        return value.all { it in ALLOWED_SYLLABLE_CHARACTERS }
    }

    private const val ALLOWED_SYLLABLE_CHARACTERS =
        "abcdefghijklmnopqrstuvwxyzüāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜńňǹḿ"
}
