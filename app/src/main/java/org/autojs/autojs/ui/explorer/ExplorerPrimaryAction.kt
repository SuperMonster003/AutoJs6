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
        type.isAudioPlayable() || type.isVideoFile() || usesPlaylistPlayButton(type) ->
            ExplorerPrimaryAction.MEDIA_PLAY
        type.isImageFile() -> ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL
        else -> null
    }

    fun usesPlaylistPlayButton(type: FileUtils.TYPE): Boolean = when (type) {
        FileUtils.TYPE.M3U8_AUDIO, FileUtils.TYPE.M3U8_VIDEO,
        FileUtils.TYPE.PLS, FileUtils.TYPE.WAX, FileUtils.TYPE.WVX, FileUtils.TYPE.ASX -> true
        else -> ExplorerM3uPlaylistPolicy.supports(type)
    }
}
