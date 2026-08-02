package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import org.autojs.plugin.explorer.api.ExplorerActionValues

internal data class ExplorerActionOutputSpec(
    val mode: Int,
    val mimeTypes: Set<String>,
    val nameSuffix: String?,
    val maxBytes: Long?,
)

internal object ExplorerActionOutputPolicy {

    fun normalize(
        protocolVersion: Int,
        outputMode: Int?,
        outputMimeTypes: Collection<String>?,
        outputNameSuffix: String?,
        maxOutputBytes: Long?,
    ): ExplorerActionOutputSpec? {
        if (
            protocolVersion !in
            ExplorerActionProtocol.MIN_SUPPORTED_VERSION..ExplorerActionProtocol.MAX_SUPPORTED_VERSION
        ) {
            return null
        }
        if (protocolVersion < OUTPUT_PROTOCOL_VERSION) {
            if (
                outputMode != null ||
                outputMimeTypes != null ||
                outputNameSuffix != null ||
                maxOutputBytes != null
            ) {
                return null
            }
            return noOutput()
        }

        return when (outputMode ?: ExplorerActionValues.OUTPUT_NONE) {
            ExplorerActionValues.OUTPUT_NONE -> {
                if (outputMimeTypes != null || outputNameSuffix != null || maxOutputBytes != null) null else noOutput()
            }
            ExplorerActionValues.OUTPUT_CREATE_SIBLING -> normalizeCreateSibling(
                outputMimeTypes = outputMimeTypes,
                outputNameSuffix = outputNameSuffix,
                maxOutputBytes = maxOutputBytes,
            )
            else -> null
        }
    }

    private fun normalizeCreateSibling(
        outputMimeTypes: Collection<String>?,
        outputNameSuffix: String?,
        maxOutputBytes: Long?,
    ): ExplorerActionOutputSpec? {
        val normalizedMimeTypes = outputMimeTypes
            ?.takeIf { it.isNotEmpty() && it.size <= ExplorerActionProtocol.MAX_MATCHERS_PER_KIND }
            ?.mapTo(LinkedHashSet()) { value ->
                ExplorerActionCatalogPolicy.normalizeMimeType(value)
                    ?.takeIf(SUPPORTED_IMAGE_MIME_TYPES::contains)
                    ?: return null
            }
            ?: return null
        val normalizedNameSuffix = outputNameSuffix
            ?.trim()
            ?.takeIf {
                it.length <= ExplorerActionProtocol.MAX_OUTPUT_NAME_SUFFIX_LENGTH &&
                    it.any(Char::isLetterOrDigit) &&
                    outputNameSuffixPattern.matches(it)
            }
            ?: return null
        val normalizedMaxBytes = maxOutputBytes?.takeIf {
            it in 1L..MAX_HOST_OUTPUT_BYTES
        } ?: return null
        return ExplorerActionOutputSpec(
            mode = ExplorerActionValues.OUTPUT_CREATE_SIBLING,
            mimeTypes = normalizedMimeTypes,
            nameSuffix = normalizedNameSuffix,
            maxBytes = normalizedMaxBytes,
        )
    }

    private fun noOutput() = ExplorerActionOutputSpec(
        mode = ExplorerActionValues.OUTPUT_NONE,
        mimeTypes = emptySet(),
        nameSuffix = null,
        maxBytes = null,
    )

    private const val OUTPUT_PROTOCOL_VERSION = 3
    const val MAX_HOST_OUTPUT_BYTES = 256L * 1024L * 1024L

    val SUPPORTED_IMAGE_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
    )

    private val outputNameSuffixPattern = Regex("[A-Za-z0-9._+-]+")
}
