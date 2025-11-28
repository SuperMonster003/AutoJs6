package org.autojs.autojs.core.plugin.center

import android.graphics.drawable.Drawable

data class PluginCenterItem(
    val packageName: String,
    val title: String,
    val description: String,
    val author: String? = null,
    val collaborators: List<String> = emptyList(),
    val versionName: String,
    val versionCode: Long? = null,
    val versionDate: String? = null,
    val isEnabled: Boolean = true,
    val isUpdatable: Boolean = false,
    val icon: Drawable? = null,
    val settings: PluginCenterItemSettings? = null,
)
