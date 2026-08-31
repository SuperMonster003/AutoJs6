package org.autojs.autojs.core.plugin.text

import org.autojs.plugin.opencc.api.OpenccPluginContract
import java.util.Locale

internal object OpenccPluginCallRouter {

    fun supportsExtendedConversions(contractVersion: Int): Boolean {
        return contractVersion >= OpenccPluginContract.VERSION_EXTENDED_CONVERSIONS
    }

    fun normalizeSupportedConversionTypes(
        reported: List<String>?,
        fallback: List<String>,
    ): List<String> {
        return reported.orEmpty()
            .asSequence()
            .map { it.trim().uppercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
            .ifEmpty { fallback.toList() }
    }

    fun convertBatch(
        supportsExtendedConversions: Boolean,
        texts: List<String>,
        conversionType: String,
        extendedCall: (List<String>, String) -> List<String>,
        legacyCall: (String, String) -> String,
    ): List<String> {
        require(texts.size <= OpenccPluginContract.MAX_BATCH_SIZE) {
            "OpenCC batch size ${texts.size} exceeds ${OpenccPluginContract.MAX_BATCH_SIZE}"
        }
        if (texts.isEmpty()) return emptyList()
        val normalizedType = normalizeConversionType(conversionType)
        if (!supportsExtendedConversions) {
            return texts.map { text -> legacyCall(text, normalizedType) }
        }
        return extendedCall(texts, normalizedType).also { converted ->
            check(converted.size == texts.size) {
                "OpenCC plugin returned ${converted.size} batch results for ${texts.size} inputs"
            }
        }
    }

    fun convertChain(
        supportsExtendedConversions: Boolean,
        text: String,
        conversionTypes: List<String>,
        extendedCall: (String, List<String>) -> String,
        legacyCall: (String, String) -> String,
    ): String {
        require(conversionTypes.size <= OpenccPluginContract.MAX_CHAIN_LENGTH) {
            "OpenCC chain length ${conversionTypes.size} exceeds ${OpenccPluginContract.MAX_CHAIN_LENGTH}"
        }
        if (conversionTypes.isEmpty()) return text
        val normalizedTypes = conversionTypes.map(::normalizeConversionType)
        if (supportsExtendedConversions) {
            return extendedCall(text, normalizedTypes)
        }
        return normalizedTypes.fold(text) { converted, conversionType ->
            legacyCall(converted, conversionType)
        }
    }

    private fun normalizeConversionType(conversionType: String): String {
        return conversionType.trim().uppercase(Locale.US).also {
            require(it.isNotEmpty()) { "OpenCC conversion type must not be blank" }
        }
    }
}
