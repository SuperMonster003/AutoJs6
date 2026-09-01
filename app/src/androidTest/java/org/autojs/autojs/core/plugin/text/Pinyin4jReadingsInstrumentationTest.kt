package org.autojs.autojs.core.plugin.text

import android.os.Bundle
import org.autojs.plugin.pinyin4j.api.Pinyin4jOptionKeys
import org.autojs.plugin.pinyin4j.api.Pinyin4jPluginCapabilityKeys
import org.autojs.plugin.pinyin4j.api.Pinyin4jRomanizationSystems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Pinyin4jReadingsInstrumentationTest {

    @Test
    fun readingsV1UsesNativeTransactionAndValidatesTheMatrix() {
        val capabilities = Bundle().apply {
            putInt(
                Pinyin4jPluginCapabilityKeys.READINGS_VERSION,
                Pinyin4jPluginCapabilityKeys.READINGS_VERSION_1,
            )
        }
        var nativeCalled = false
        var legacyCalled = false

        val rows = Pinyin4jReadingsNegotiator.read(
            text = "重A𠀀",
            options = hanyuOptions(),
            capabilities = capabilities,
            nativeRead = {
                nativeCalled = true
                """[["zhong4","chong2"],["A"],["𠀀"]]"""
            },
            legacyOf = { _, _ ->
                legacyCalled = true
                error("legacy fallback must not run")
            },
        )

        assertEquals(listOf(listOf("zhong4", "chong2"), listOf("A"), listOf("𠀀")), rows)
        assertTrue(nativeCalled)
        assertFalse(legacyCalled)
    }

    @Test
    fun oldPluginFallsBackPerCodePointForHanyuOnly() {
        val sources = mutableListOf<String>()
        val rows = Pinyin4jReadingsNegotiator.read(
            text = "重A𠀀",
            options = hanyuOptions(),
            capabilities = null,
            nativeRead = { error("native readings transaction must not run") },
            legacyOf = { source, options ->
                assertFalse(options.containsKey(Pinyin4jOptionKeys.ROMANIZATION))
                sources += source
                when (source) {
                    "重" -> "zhong4"
                    "A" -> "A"
                    else -> ""
                }
            },
        )

        assertEquals(listOf("重", "A", "𠀀"), sources)
        assertEquals(listOf(listOf("zhong4"), listOf("A"), listOf("𠀀")), rows)
    }

    @Test
    fun oldPluginRejectsNonHanyuWithoutCallingEitherTransaction() {
        val options = Bundle().apply {
            putString(Pinyin4jOptionKeys.ROMANIZATION, Pinyin4jRomanizationSystems.WADE_GILES)
        }
        val error = expectThrows<IllegalStateException> {
            Pinyin4jReadingsNegotiator.read(
                text = "间",
                options = options,
                capabilities = Bundle(),
                nativeRead = { error("native readings transaction must not run") },
                legacyOf = { _, _ -> error("legacy Hanyu fallback must not run") },
            )
        }
        assertTrue(error.message.orEmpty().contains("does not support readings v1"))
    }

    @Test
    fun malformedNativeMatricesFailClosed() {
        listOf(
            "{}",
            "[]",
            """[["zhong4"],"A"]""",
            """[["zhong4"],[null]]""",
        ).forEach { json ->
            expectThrows<IllegalArgumentException> {
                Pinyin4jReadingMatrixCodec.decode("重A", json)
            }
        }
    }

    private fun hanyuOptions() = Bundle().apply {
        putString(Pinyin4jOptionKeys.ROMANIZATION, Pinyin4jRomanizationSystems.HANYU)
        putString(Pinyin4jOptionKeys.CASE_TYPE, "LOWERCASE")
        putString(Pinyin4jOptionKeys.TONE_TYPE, "WITH_TONE_NUMBER")
        putString(Pinyin4jOptionKeys.V_CHAR_TYPE, "WITH_V")
    }

    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        fail("Expected ${T::class.java.name}")
        error("unreachable")
    }
}
