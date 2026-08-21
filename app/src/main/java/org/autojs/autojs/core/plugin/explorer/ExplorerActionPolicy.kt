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
}

internal object ExplorerActionPolicy {

    fun allows(
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        descriptor: ExplorerActionDescriptor,
    ): Boolean {
        return allowsThirdPartyActions(surface) && descriptor.placement == entryPoint.placement
    }

    fun sourceSurfaceValue(surface: ExplorerSurface): String? = when (surface) {
        ExplorerSurface.MAIN_FILES -> ExplorerActionIntentValues.SOURCE_SURFACE_MAIN
        ExplorerSurface.UNSPECIFIED -> null
    }

    private fun allowsThirdPartyActions(surface: ExplorerSurface): Boolean {
        return surface == ExplorerSurface.MAIN_FILES
    }
}
