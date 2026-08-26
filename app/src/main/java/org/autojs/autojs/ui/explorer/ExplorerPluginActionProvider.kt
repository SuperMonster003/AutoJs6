package org.autojs.autojs.ui.explorer

import android.content.Context
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveOpenRequestOptions
import org.autojs.autojs.core.plugin.explorer.archive.ExplorerArchiveSessionConnection

internal interface ExplorerPluginActionProvider {

    fun actionsFor(
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
    ): List<ResolvedExplorerAction>

    fun actionsFor(
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
    ): List<ResolvedExplorerAction> = actionsFor(surface, entryPoint, listOf(item))

    fun launch(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
        action: ResolvedExplorerAction,
    ): Result<Unit>

    fun launch(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ): Result<Unit> = launch(context, surface, entryPoint, listOf(item), action)

    suspend fun openArchive(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
        options: ExplorerArchiveOpenRequestOptions,
    ): ExplorerArchiveSessionConnection = error("Explorer Action provider does not support host archive sessions")
}
