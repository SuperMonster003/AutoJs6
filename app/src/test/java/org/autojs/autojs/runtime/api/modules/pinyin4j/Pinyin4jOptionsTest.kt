package org.autojs.autojs.runtime.api.modules.pinyin4j

import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Pinyin4jOptionsTest {

    @Test
    fun romanizationAliasesNormalizeToCanonicalWireValues() {
        val cases = mapOf(
            null to Pinyin4jRomanizationSystems.HANYU,
            "pinyin" to Pinyin4jRomanizationSystems.HANYU,
            "tongyong-pinyin" to Pinyin4jRomanizationSystems.TONGYONG,
            "Wade Giles" to Pinyin4jRomanizationSystems.WADE_GILES,
            "mandarin phonetic symbols 2" to Pinyin4jRomanizationSystems.MPS2,
            "yale-pinyin" to Pinyin4jRomanizationSystems.YALE,
            "gr" to Pinyin4jRomanizationSystems.GWOYEU_ROMATZYH,
        )
        cases.forEach { (raw, expected) ->
            assertEquals(expected, Pinyin4jOptions.normalizeRomanization(raw))
        }
    }

    @Test
    fun HanyuFormattingDefaultsAndAliasesRemainStable() {
        assertEquals("LOWERCASE", Pinyin4jOptions.normalizeCaseType(null))
        assertEquals(
            "WITHOUT_TONE",
            Pinyin4jOptions.normalizeToneType(null, Pinyin4jOptions.DEFAULT_OF_TONE_TYPE),
        )
        assertEquals(
            "WITH_TONE_NUMBER",
            Pinyin4jOptions.normalizeToneType(null, Pinyin4jOptions.DEFAULT_READINGS_TONE_TYPE),
        )
        assertEquals("WITH_TONE_MARK", Pinyin4jOptions.normalizeToneType("true", "WITHOUT_TONE"))
        assertEquals("WITH_U_UNICODE", Pinyin4jOptions.normalizeVCharType(null, "WITH_TONE_MARK"))
        assertEquals("WITH_V", Pinyin4jOptions.normalizeVCharType(null, "WITH_TONE_NUMBER"))
    }

    @Test
    fun invalidNamesFailExplicitly() {
        assertThrows(IllegalArgumentException::class.java) {
            Pinyin4jOptions.normalizeRomanization("unknown")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pinyin4jOptions.normalizeCaseType("mixed")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pinyin4jOptions.normalizeToneType("five", "WITHOUT_TONE")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Pinyin4jOptions.normalizeVCharType("ascii-u", "WITH_TONE_NUMBER")
        }
    }
}
