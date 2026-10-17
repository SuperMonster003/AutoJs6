package org.autojs.autojs.ui.explorer

import org.autojs.autojs.core.plugin.explorer.ResolvedExplorerAction
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerFileItem
import org.autojs.autojs.core.plugin.explorer.archive.ArchiveExplorerItem
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.PrivilegedExplorerItem
import org.autojs.plugin.explorer.api.ExplorerActionValues

/** Immutable presentation state for the action strip of one Explorer file row. */
internal data class ExplorerItemActionState(
    val primaryAction: ExplorerPrimaryAction?,
    val pluginPrimaryAction: ResolvedExplorerAction?,
    val editVisible: Boolean,
    val runVisible: Boolean,
    val runAsMedia: Boolean,
    val installVisible: Boolean,
    val infoVisible: Boolean,
)

/**
 * Resolves file capabilities into a row presentation without touching Android views.
 *
 * Plugin discovery stays outside this policy because it can perform IPC. Once discovery has
 * completed, all precedence and icon-density rules are decided in one deterministic place.
 */
internal object ExplorerItemActionPolicy {

    fun resolve(
        item: ExplorerItem,
        pluginPrimaryAction: ResolvedExplorerAction?,
    ): ExplorerItemActionState {
        if (item is PrivilegedExplorerItem) {
            return ExplorerItemActionState(null, null, false, false, false, false, false)
        }
        val primaryAction = when (item) {
            is ArchiveExplorerFileItem -> ExplorerPrimaryActionResolver
                .resolve(item.type)
                ?.takeIf { item.canOpenEntry }
            is ArchiveExplorerItem -> null
            else -> ExplorerPrimaryActionResolver.resolve(item.type)
        }
        val effectivePluginAction = pluginPrimaryAction.takeUnless {
            item is ArchiveExplorerItem || ((item.isInstallable || ExplorerPrimaryActionResolver.usesPlaylistPlayButton(item.type)) &&
                it?.descriptor?.presentation == ExplorerActionValues.PRESENTATION_HOST_EXPLORER)
        }
        val hasPrimaryAction = primaryAction != null || effectivePluginAction != null
        val editVisible = item.isTextEditable || (item.isExternalEditable && !item.type.isImageFile())
        val runAsMedia = !item.isExecutable && item.isMediaPlayable && primaryAction == null
        val runVisible = item.isExecutable || runAsMedia
        val installVisible = item.isInstallable
        val alreadyHasTwoImportantIcons = listOf(
            hasPrimaryAction,
            editVisible,
            runVisible,
            installVisible,
        ).count { it } >= 2
        val infoVisible = effectivePluginAction?.descriptor?.presentation ==
            ExplorerActionValues.PRESENTATION_HOST_EXPLORER || item.type.isImageFile() ||
            (!alreadyHasTwoImportantIcons && (item.isInstallable || item.isMediaMenu || item.isMediaPlayable))

        return ExplorerItemActionState(
            primaryAction = primaryAction,
            pluginPrimaryAction = effectivePluginAction,
            editVisible = editVisible,
            runVisible = runVisible,
            runAsMedia = runAsMedia,
            installVisible = installVisible,
            infoVisible = infoVisible,
        )
    }
}
