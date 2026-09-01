package org.autojs.plugin.pinyin.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinCustomDictionaryContractTest {

    @Test
    fun hanKeysAcceptBmpAndSupplementaryHanOnly() {
        assertTrue(PinyinCustomDictionaryContract.isHanKey("重庆𠀀"))
        assertFalse(PinyinCustomDictionaryContract.isHanKey(""))
        assertFalse(PinyinCustomDictionaryContract.isHanKey("重庆A"))
        assertFalse(PinyinCustomDictionaryContract.isHanKey("重庆1"))
    }

    @Test
    fun canonicalSyllablesAreLowercaseNfcAndToneMarkedWithoutNumbers() {
        listOf("lǜ", "lüe", "ḿ", "ǹ", "ma").forEach { syllable ->
            assertTrue("Expected canonical syllable: $syllable", PinyinCustomDictionaryContract.isCanonicalSyllable(syllable))
        }
        listOf("", " Lǜ", "Lǜ", "lu\u0308\u0300", "lv4", "ma1", "ma-ma").forEach { syllable ->
            assertFalse("Expected non-canonical syllable: $syllable", PinyinCustomDictionaryContract.isCanonicalSyllable(syllable))
        }
    }

    @Test
    fun canonicalSyllableLengthIsBoundedByCodePoint() {
        assertTrue(
            PinyinCustomDictionaryContract.isCanonicalSyllable(
                "a".repeat(PinyinCustomDictionaryContract.MAX_SYLLABLE_CODE_POINTS),
            ),
        )
        assertFalse(
            PinyinCustomDictionaryContract.isCanonicalSyllable(
                "a".repeat(PinyinCustomDictionaryContract.MAX_SYLLABLE_CODE_POINTS + 1),
            ),
        )
    }
}
