package org.autojs.autojs.runtime.api.augment.opencc

import org.autojs.plugin.opencc.api.OpenccConversionTypes
import java.util.Locale

internal object OpenccConversionPlan {

    private val aliases = mapOf(
        "s2twi" to listOf(OpenccConversionTypes.S2TWP),
        "twi2s" to listOf(OpenccConversionTypes.TW2SP),
    )

    private val compositeChains = mapOf(
        "s2jp" to listOf(OpenccConversionTypes.S2T, OpenccConversionTypes.T2JP),
        "t2twi" to listOf(OpenccConversionTypes.T2S, OpenccConversionTypes.S2TWP),
        "hk2tw" to listOf(OpenccConversionTypes.HK2T, OpenccConversionTypes.T2TW),
        "hk2twi" to listOf(OpenccConversionTypes.HK2S, OpenccConversionTypes.S2TWP),
        "hk2jp" to listOf(OpenccConversionTypes.HK2T, OpenccConversionTypes.T2JP),
        "tw2hk" to listOf(OpenccConversionTypes.TW2T, OpenccConversionTypes.T2HK),
        "tw2twi" to listOf(OpenccConversionTypes.TW2S, OpenccConversionTypes.S2TWP),
        "tw2jp" to listOf(OpenccConversionTypes.TW2T, OpenccConversionTypes.T2JP),
        "twi2t" to listOf(OpenccConversionTypes.TW2SP, OpenccConversionTypes.S2T),
        "twi2hk" to listOf(OpenccConversionTypes.TW2SP, OpenccConversionTypes.S2HK),
        "twi2tw" to listOf(OpenccConversionTypes.TW2SP, OpenccConversionTypes.S2TW),
        "twi2jp" to listOf(OpenccConversionTypes.TW2SP, OpenccConversionTypes.S2T, OpenccConversionTypes.T2JP),
        "jp2s" to listOf(OpenccConversionTypes.JP2T, OpenccConversionTypes.T2S),
        "jp2hk" to listOf(OpenccConversionTypes.JP2T, OpenccConversionTypes.T2HK),
        "jp2tw" to listOf(OpenccConversionTypes.JP2T, OpenccConversionTypes.T2TW),
        "jp2twi" to listOf(OpenccConversionTypes.JP2T, OpenccConversionTypes.T2S, OpenccConversionTypes.S2TWP),
    )

    val compositeNames: Set<String> = compositeChains.keys

    val supportedNames: Set<String> = (OpenccConversionTypes.ALL.map { it.lowercase(Locale.ROOT) } + aliases.keys + compositeChains.keys).toSet()

    fun chainFor(name: String): List<String>? {
        val normalizedName = name.lowercase(Locale.ROOT)
        return aliases[normalizedName] ?: compositeChains[normalizedName]
    }
}
