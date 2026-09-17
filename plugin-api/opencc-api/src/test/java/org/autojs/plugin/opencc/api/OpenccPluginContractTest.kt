package org.autojs.plugin.opencc.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenccPluginContractTest {

    @Test
    fun conversionTypesAreOrderedUniqueAndStable() {
        assertEquals(
            listOf(
                "HK2S",
                "HK2T",
                "JP2T",
                "S2HK",
                "S2T",
                "S2TW",
                "S2TWP",
                "T2HK",
                "T2S",
                "T2TW",
                "T2JP",
                "TW2S",
                "TW2T",
                "TW2SP",
            ),
            OpenccConversionTypes.ALL,
        )
        assertEquals(OpenccConversionTypes.ALL.size, OpenccConversionTypes.ALL.toSet().size)
    }

    @Test
    fun extendedContractKeepsLegacyVersionAndBoundedCollections() {
        assertTrue(OpenccPluginContract.VERSION_EXTENDED_CONVERSIONS > OpenccPluginContract.VERSION_LEGACY)
        assertEquals(OpenccPluginContract.VERSION_EXTENDED_CONVERSIONS, OpenccPluginContract.VERSION_CURRENT)
        assertTrue(OpenccPluginContract.MAX_BATCH_SIZE > 0)
        assertTrue(OpenccPluginContract.MAX_CHAIN_LENGTH > 0)
        assertTrue(OpenccPluginCapabilityKeys.CONTRACT_VERSION.isNotBlank())
        assertTrue(OpenccPluginCapabilityKeys.SUPPORTED_CONVERSION_TYPES.isNotBlank())
    }
}
