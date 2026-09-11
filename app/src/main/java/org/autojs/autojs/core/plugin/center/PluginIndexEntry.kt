package org.autojs.autojs.core.plugin.center

import android.net.Uri

enum class PluginIndexSource {
    OFFICIAL,
    THIRD_PARTY,
}

data class PluginIndexEntry(
    val packageName: String,
    val iconUrl: Uri? = null,
    val nightIconUrl: Uri? = null,
    val forceIgnoreLocalIcon: Boolean = false,

    val title: String,
    val description: String? = null,
    val instructionHardCoded: String? = null,
    val instructionMarkdownUrl: String? = null,

    val author: String? = null,
    val collaborators: List<String> = emptyList(),

    /** @sample "paddle-ocr" */
    val engine: String? = null,
    /** @sample "v5" */
    val variant: String? = null,
    /** @sample "paddle-ocr-pp-ocrv5" */
    val engineId: String? = null,

    /** Whether this distribution variant belongs in the default Plugin Center list. */
    val isFeatured: Boolean = true,

    val releases: List<PluginIndexRelease> = emptyList(),
    val supportedAbis: List<String>? = null,

    val tags: List<String> = emptyList(),

    val requiresHostVersion: Long? = null,
    val nativePageAlignment: Long? = null,
    val nativePageAlignmentSource: String? = null,

    val source: PluginIndexSource = PluginIndexSource.OFFICIAL,
)
