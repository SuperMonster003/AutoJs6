package org.autojs.autojs.ui.explorer

import org.autojs.autojs.core.plugin.explorer.ExplorerM3uPlaylistPolicy
import org.autojs.autojs.util.FileUtils

internal enum class ExplorerPrimaryAction {
    DOCUMENT_PREVIEWER,
    IMAGE_VIEW,
    IMAGE_VIEW_EXTERNAL,
    MEDIA_PLAY,
}

internal object ExplorerPrimaryActionResolver {

    fun resolve(type: FileUtils.TYPE): ExplorerPrimaryAction? = when {
        type.isPreviewable() -> ExplorerPrimaryAction.DOCUMENT_PREVIEWER
        type.isImageViewable() -> ExplorerPrimaryAction.IMAGE_VIEW
        type.isAudioPlayable() || type.isVideoFile() || ExplorerM3uPlaylistPolicy.supports(type) ->
            ExplorerPrimaryAction.MEDIA_PLAY
        type.isImageFile() -> ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL
        else -> null
    }
}
