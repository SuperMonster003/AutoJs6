package org.autojs.autojs.ui.explorer

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.plugin.explorer.ExplorerActionEntryPoint
import org.autojs.autojs.core.plugin.explorer.ExplorerSurface
import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.plugin.explorer.api.ExplorerActionValues

/**
 * Resolves and dispatches file-manager plugin actions.
 *
 * The controller keeps provider/surface selection, official video routing, and host-presented
 * archive actions in one place. Explorer rows therefore consume the same policy at every entry
 * point instead of reproducing fallback decisions.
 */
internal class ExplorerPluginActionController(
    private val openHostAction: (
        provider: ExplorerPluginActionProvider,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        action: ResolvedExplorerAction,
    ) -> Result<Unit>,
) {

    var provider: ExplorerPluginActionProvider? = null
        private set

    var surface: ExplorerSurface = ExplorerSurface.UNSPECIFIED
        private set

    fun setProvider(surface: ExplorerSurface, provider: ExplorerPluginActionProvider?) {
        this.surface = if (provider == null) ExplorerSurface.UNSPECIFIED else surface
        this.provider = provider
    }

    fun select(item: ExplorerItem, actions: List<ResolvedExplorerAction>): ResolvedExplorerAction? {
        return if (requiresOfficialVideoPlayer(item)) {
            actions.firstOrNull(ExplorerVideoPlayerPluginUi::isOfficialAction)
        } else {
            actions.firstOrNull()
        }
    }

    fun resolve(
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
    ): ResolvedExplorerAction? {
        val currentProvider = provider ?: return null
        return runCatching {
            select(item, currentProvider.actionsFor(surface, entryPoint, item))
        }.onFailure { error ->
            Log.w(LOG_TAG, "Failed to resolve Explorer plugin action from $entryPoint", error)
        }.getOrNull()
    }

    /** Returns true when the item is a video whose implicit viewer fallback must be suppressed. */
    fun launchOfficialVideoOrShowGuidance(
        context: Context,
        entryPoint: ExplorerActionEntryPoint,
        item: ExplorerItem,
        preResolvedAction: ResolvedExplorerAction? = null,
    ): Boolean {
        if (!requiresOfficialVideoPlayer(item)) return false
        val action = preResolvedAction
            ?.takeIf(ExplorerVideoPlayerPluginUi::isOfficialAction)
            ?: resolve(entryPoint, item)
        val launched = action?.let { launch(context, entryPoint, it, item) } == true
        if (!launched) ExplorerVideoPlayerPluginUi.showUnavailableDialog(context, item)
        return true
    }

    fun launch(
        context: Context,
        entryPoint: ExplorerActionEntryPoint,
        action: ResolvedExplorerAction,
        item: ExplorerItem,
    ): Boolean {
        if (provider == null) return false
        return runCatching {
            dispatch(context, surface, entryPoint, listOf(item), action).getOrThrow()
        }.onFailure { error ->
            Log.w(LOG_TAG, "Failed to launch Explorer plugin action from $entryPoint", error)
        }.isSuccess
    }

    fun dispatch(
        context: Context,
        surface: ExplorerSurface,
        entryPoint: ExplorerActionEntryPoint,
        items: List<ExplorerItem>,
        action: ResolvedExplorerAction,
    ): Result<Unit> {
        val currentProvider = provider
            ?: return Result.failure(IllegalStateException("Explorer Action provider is unavailable"))
        if (action.descriptor.presentation != ExplorerActionValues.PRESENTATION_HOST_EXPLORER) {
            return currentProvider.launch(context, surface, entryPoint, items, action)
        }
        if (items.size != 1) {
            return Result.failure(IllegalArgumentException("Host archive sessions require one file"))
        }
        return openHostAction(currentProvider, surface, entryPoint, items.single(), action)
    }

    private fun requiresOfficialVideoPlayer(item: ExplorerItem): Boolean {
        return surface == ExplorerSurface.MAIN_FILES && item.type.isVideoFile()
    }

    private companion object {
        private const val LOG_TAG = "ExplorerPluginActions"
    }
}
