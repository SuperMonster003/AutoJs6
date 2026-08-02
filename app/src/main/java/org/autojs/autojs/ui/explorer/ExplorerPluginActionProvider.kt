package org.autojs.autojs.ui.explorer

import android.content.Context
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerItem

internal interface ExplorerPluginActionProvider {

    fun actionsFor(item: ExplorerItem): List<ResolvedExplorerAction>

    fun launch(
        context: Context,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ): Result<Unit>
}
