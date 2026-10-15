package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionIntentValues
import org.autojs.plugin.explorer.api.ExplorerActionValues

internal enum class ExplorerSurface {
    MAIN_FILES,
    UNSPECIFIED,
}

internal enum class ExplorerActionEntryPoint(
    val placement: Int,
) {
    DEFAULT_CLICK(ExplorerActionValues.PLACEMENT_PRIMARY),
    PRIMARY_BUTTON(ExplorerActionValues.PLACEMENT_PRIMARY),
    MEDIA_INFO_DIALOG(ExplorerActionValues.PLACEMENT_PRIMARY),
    OVERFLOW_MENU(ExplorerActionValues.PLACEMENT_OVERFLOW),
    SELECTION_TOOLBAR(ExplorerActionValues.PLACEMENT_SELECTION_TOOLBAR),
    /** v25: the "More" menu of the single-item rename dialog. zh-CN: 单项重命名对话框的 "更多" 菜单. */
    RENAME_DIALOG(ExplorerActionValues.PLACEMENT_RENAME_DIALOG),
}

internal object ExplorerActionPolicy {

    fun allows(
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        descriptor: ExplorerActionDescriptor,
    ): Boolean {
        val archiveMenu = entryPoint == ExplorerActionEntryPoint.OVERFLOW_MENU &&
            descriptor.presentation == ExplorerActionValues.PRESENTATION_HOST_EXPLORER &&
            descriptor.placement == ExplorerActionValues.PLACEMENT_PRIMARY
        return allowsThirdPartyActions(surface) &&
            (descriptor.placement == entryPoint.placement || archiveMenu)
    }

    fun sourceSurfaceValue(surface: ExplorerSurface): String? = when (surface) {
        ExplorerSurface.MAIN_FILES -> ExplorerActionIntentValues.SOURCE_SURFACE_MAIN
        ExplorerSurface.UNSPECIFIED -> null
    }

    private fun allowsThirdPartyActions(surface: ExplorerSurface): Boolean {
        return surface == ExplorerSurface.MAIN_FILES
    }
}
