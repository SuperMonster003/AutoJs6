package org.autojs.autojs.core.plugin.explorer

import org.autojs.autojs.util.FileUtils

internal object ExplorerM3uPlaylistPolicy {

    const val VIDEO_PACKAGE = "io.github.supermonster003.autojs6.plugin.videoplayer"
    const val AUDIO_PACKAGE = "io.github.supermonster003.autojs6.plugin.audioplayer"

    fun supports(type: FileUtils.TYPE): Boolean =
        type == FileUtils.TYPE.M3U_AUDIO || type == FileUtils.TYPE.M3U_VIDEO

    /** A shared audio/video playlist can be handed to either official player's media contract. */
    fun resolveMimeType(packageName: String, extension: String, fallbackMimeType: String): String {
        if (!extension.equals("m3u", ignoreCase = true)) return fallbackMimeType
        return when (packageName) {
            VIDEO_PACKAGE -> "video/*"
            AUDIO_PACKAGE -> "audio/*"
            else -> fallbackMimeType
        }
    }

    fun preferredPackage(availablePackages: Set<String>): String? = when {
        VIDEO_PACKAGE in availablePackages -> VIDEO_PACKAGE
        AUDIO_PACKAGE in availablePackages -> AUDIO_PACKAGE
        else -> null
    }

    fun select(actions: List<ResolvedExplorerAction>): ResolvedExplorerAction? {
        val players = actions.filter { action ->
            when (action.packageName) {
                VIDEO_PACKAGE -> action.descriptor.id == "play-video"
                AUDIO_PACKAGE -> action.descriptor.id == "play-audio"
                else -> false
            }
        }
        val packageName = preferredPackage(players.mapTo(mutableSetOf()) { it.packageName })
        return players.firstOrNull { it.packageName == packageName }
    }
}
