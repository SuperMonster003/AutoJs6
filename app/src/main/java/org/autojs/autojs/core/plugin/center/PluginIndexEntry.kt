package org.autojs.autojs.core.plugin.center

import android.net.Uri

data class PluginIndexEntry(
    val packageName: String,
    val iconUrl: Uri? = null,

    val title: String,
    val description: String,

    val author: String? = null,
    val collaborators: List<String> = emptyList(),

    /** @sample "paddle-ocr" */
    val engine: String? = null,
    /** @sample "v5" */
    val variant: String? = null,
    /** @sample "paddle-ocr-v5" */
    val engineId: String? = null,

    val versionName: String,
    val versionCode: Long? = null,
    val versionDate: String? = null,

    val tags: List<String> = emptyList(),
)
