package org.autojs.autojs.core.plugin.center

import android.content.Context
import org.autojs.autojs.util.ViewUtils

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 28, 2026.
 * Modified by SuperMonster003 as of Mar 29, 2026.
 */
object PluginIconUrlResolver {

    fun resolveForCurrentMode(
        @Suppress("UNUSED_PARAMETER") context: Context,
        remoteIconUrl: String?,
        remoteNightIconUrl: String? = null,
    ): String? {
        val source = remoteIconUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (ViewUtils.isNightModeEnabled) {
            remoteNightIconUrl?.trim()?.takeIf { it.isNotBlank() } ?: source
        } else {
            toDayVariant(source)
        }
    }

    private fun toDayVariant(url: String): String = url
        .replace("/res/mipmap-night/", "/res/mipmap/")
        .replace("/res/drawable-night/", "/res/drawable/")
}
