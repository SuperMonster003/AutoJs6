package org.autojs.autojs.core.plugin.center

enum class PluginVariantDisplayMode {
    STANDARD,
    ADDITIONAL_ONLY,
}

object PluginVariantDisplayPolicy {

    fun apply(
        items: List<PluginCenterItem>,
        mode: PluginVariantDisplayMode,
    ): List<PluginCenterItem> = items.filter { item ->
        shouldDisplay(
            isFeatured = item.isFeatured,
            isInstalled = item.isInstalled,
            mode = mode,
        )
    }

    fun shouldDisplay(
        isFeatured: Boolean,
        isInstalled: Boolean,
        mode: PluginVariantDisplayMode,
    ): Boolean = when (mode) {
        PluginVariantDisplayMode.STANDARD -> isFeatured || isInstalled
        PluginVariantDisplayMode.ADDITIONAL_ONLY -> !isFeatured
    }
}
