package org.autojs.autojs.core.plugin.text

import org.autojs.plugin.opencc.api.OpenccConversionTypes
import org.autojs.plugin.opencc.api.OpenccPluginContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenccPluginCallRouterTest {

    @Test
    fun supportedTypesAreNormalizedDeduplicatedAndSafelyDefaulted() {
        assertEquals(
            listOf("S2T", "FUTURE_TYPE"),
            OpenccPluginCallRouter.normalizeSupportedConversionTypes(
                listOf(" s2t ", "S2T", "future_type", " "),
                OpenccConversionTypes.ALL,
            ),
        )
        assertEquals(
            OpenccConversionTypes.ALL,
            OpenccPluginCallRouter.normalizeSupportedConversionTypes(emptyList(), OpenccConversionTypes.ALL),
        )
    }

    @Test
    fun contractVersionSelectsExtendedCallsOnlyFromVersionTwo() {
        assertFalse(OpenccPluginCallRouter.supportsExtendedConversions(OpenccPluginContract.VERSION_LEGACY))
        assertTrue(OpenccPluginCallRouter.supportsExtendedConversions(OpenccPluginContract.VERSION_EXTENDED_CONVERSIONS))
    }

    @Test
    fun batchUsesOneExtendedCallOrOrderedLegacyFallback() {
        var extendedCalls = 0
        var legacyCalls = 0
        val texts = listOf("one", "two", "three")
        val extended = OpenccPluginCallRouter.convertBatch(
            supportsExtendedConversions = true,
            texts = texts,
            conversionType = " s2t ",
            extendedCall = { values, type ->
                extendedCalls++
                values.map { "$type:$it" }
            },
            legacyCall = { _, _ ->
                legacyCalls++
                error("Legacy call must not run")
            },
        )
        assertEquals(listOf("S2T:one", "S2T:two", "S2T:three"), extended)
        assertEquals(1, extendedCalls)
        assertEquals(0, legacyCalls)

        val legacy = OpenccPluginCallRouter.convertBatch(
            supportsExtendedConversions = false,
            texts = texts,
            conversionType = "s2t",
            extendedCall = { _, _ -> error("Extended call must not run") },
            legacyCall = { value, type ->
                legacyCalls++
                "$type:$value"
            },
        )
        assertEquals(extended, legacy)
        assertEquals(texts.size, legacyCalls)
    }

    @Test
    fun batchRejectsWrongResultCountAndOversizedRequests() {
        assertThrows(IllegalStateException::class.java) {
            OpenccPluginCallRouter.convertBatch(
                supportsExtendedConversions = true,
                texts = listOf("one", "two"),
                conversionType = "S2T",
                extendedCall = { _, _ -> listOf("one") },
                legacyCall = { value, _ -> value },
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpenccPluginCallRouter.convertBatch(
                supportsExtendedConversions = false,
                texts = List(OpenccPluginContract.MAX_BATCH_SIZE + 1) { it.toString() },
                conversionType = "S2T",
                extendedCall = { values, _ -> values },
                legacyCall = { value, _ -> value },
            )
        }
    }

    @Test
    fun chainUsesOneExtendedCallOrSequentialLegacyFallback() {
        val chain = listOf(" tw2sp ", "s2t", "t2jp")
        var extendedCalls = 0
        val extended = OpenccPluginCallRouter.convertChain(
            supportsExtendedConversions = true,
            text = "source",
            conversionTypes = chain,
            extendedCall = { value, types ->
                extendedCalls++
                types.fold(value) { current, type -> "$current>$type" }
            },
            legacyCall = { _, _ -> error("Legacy call must not run") },
        )
        assertEquals("source>TW2SP>S2T>T2JP", extended)
        assertEquals(1, extendedCalls)

        var legacyCalls = 0
        val legacy = OpenccPluginCallRouter.convertChain(
            supportsExtendedConversions = false,
            text = "source",
            conversionTypes = chain,
            extendedCall = { _, _ -> error("Extended call must not run") },
            legacyCall = { value, type ->
                legacyCalls++
                "$value>$type"
            },
        )
        assertEquals(extended, legacy)
        assertEquals(chain.size, legacyCalls)
    }

    @Test
    fun emptyCollectionsAvoidAllRemoteCalls() {
        assertEquals(
            emptyList<String>(),
            OpenccPluginCallRouter.convertBatch(
                supportsExtendedConversions = true,
                texts = emptyList(),
                conversionType = "S2T",
                extendedCall = { _, _ -> error("No batch call expected") },
                legacyCall = { _, _ -> error("No legacy call expected") },
            ),
        )
        assertEquals(
            "source",
            OpenccPluginCallRouter.convertChain(
                supportsExtendedConversions = true,
                text = "source",
                conversionTypes = emptyList(),
                extendedCall = { _, _ -> error("No chain call expected") },
                legacyCall = { _, _ -> error("No legacy call expected") },
            ),
        )
    }
}
