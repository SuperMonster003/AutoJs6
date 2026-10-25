package org.autojs.autojs.runtime.api.modules.opencc

import org.autojs.plugin.opencc.api.OpenccConversionTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OpenccConversionPlanTest {

    @Test
    fun everyCompositeConversionHasTheExpectedOrderedCoreChain() {
        val expected = mapOf(
            "s2jp" to listOf("S2T", "T2JP"),
            "t2twi" to listOf("T2S", "S2TWP"),
            "hk2tw" to listOf("HK2T", "T2TW"),
            "hk2twi" to listOf("HK2S", "S2TWP"),
            "hk2jp" to listOf("HK2T", "T2JP"),
            "tw2hk" to listOf("TW2T", "T2HK"),
            "tw2twi" to listOf("TW2S", "S2TWP"),
            "tw2jp" to listOf("TW2T", "T2JP"),
            "twi2t" to listOf("TW2SP", "S2T"),
            "twi2hk" to listOf("TW2SP", "S2HK"),
            "twi2tw" to listOf("TW2SP", "S2TW"),
            "twi2jp" to listOf("TW2SP", "S2T", "T2JP"),
            "jp2s" to listOf("JP2T", "T2S"),
            "jp2hk" to listOf("JP2T", "T2HK"),
            "jp2tw" to listOf("JP2T", "T2TW"),
            "jp2twi" to listOf("JP2T", "T2S", "S2TWP"),
        )
        assertEquals(expected.keys, OpenccConversionPlan.compositeNames)
        expected.forEach { (name, chain) ->
            assertEquals(chain, OpenccConversionPlan.chainFor(name))
            assertTrue(chain.all { it in OpenccConversionTypes.ALL })
        }
    }

    @Test
    fun lookupIsCaseInsensitiveAndRejectsUnknownNames() {
        assertEquals(listOf("TW2SP", "S2T", "T2JP"), OpenccConversionPlan.chainFor("TWI2JP"))
        assertNull(OpenccConversionPlan.chainFor("unknown"))
    }

    @Test
    fun documentedTaiwanPhraseAliasesSurviveChainRoutingInEveryLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(listOf("S2TWP"), OpenccConversionPlan.chainFor("S2TWI"))
            assertEquals(listOf("TW2SP"), OpenccConversionPlan.chainFor("TWI2S"))
            assertEquals(listOf("S2TWP"), OpenccConversionPlan.chainFor("s2twi"))
            assertEquals(listOf("TW2SP"), OpenccConversionPlan.chainFor("twi2s"))
            assertEquals(listOf("TW2SP", "S2T", "T2JP"), OpenccConversionPlan.chainFor("TWI2JP"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
