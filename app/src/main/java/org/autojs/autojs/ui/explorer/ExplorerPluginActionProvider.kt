package org.autojs.autojs.ui.explorer

import android.content.Context
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerItem

internal interface ExplorerPluginActionProvider {

    fun actionsFor(
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
    ): List<ResolvedExplorerAction>

    fun launch(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ): Result<Unit>
}
