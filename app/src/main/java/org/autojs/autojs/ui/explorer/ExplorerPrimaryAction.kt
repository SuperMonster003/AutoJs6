package org.autojs.autojs.ui.explorer

import org.autojs.autojs.util.FileUtils

internal enum class ExplorerPrimaryAction {
    DOCUMENT_PREVIEW,
    IMAGE_VIEW,
    IMAGE_VIEW_EXTERNAL,
    MEDIA_PLAY,
}

internal object ExplorerPrimaryActionResolver {

    fun resolve(type: FileUtils.TYPE): ExplorerPrimaryAction? = when {
        type.isPreviewable() -> ExplorerPrimaryAction.DOCUMENT_PREVIEW
        type.isImageViewable() -> ExplorerPrimaryAction.IMAGE_VIEW
        type.isAudioPlayable() || type.isVideoPlayable() -> ExplorerPrimaryAction.MEDIA_PLAY
        type.isImageFile() -> ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL
        else -> null
    }
}
