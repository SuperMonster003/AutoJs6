package org.autojs.autojs.core.plugin.center

/**
 * Tracks the mode used by the currently rendered plugin items so newly resolved package icons
 * are not discarded by semantic list deduplication after a night mode configuration change.
 */
internal class PluginIconRenderModeTracker {

    private var renderedNightModeEnabled: Boolean? = null

    fun shouldForceRender(nightModeEnabled: Boolean): Boolean =
        renderedNightModeEnabled != nightModeEnabled

    fun markRendered(nightModeEnabled: Boolean) {
        renderedNightModeEnabled = nightModeEnabled
    }
}
