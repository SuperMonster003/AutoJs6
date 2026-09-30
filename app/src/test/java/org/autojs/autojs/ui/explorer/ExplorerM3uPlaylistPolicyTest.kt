package org.autojs.autojs.ui.explorer

import org.autojs.autojs.core.plugin.explorer.ExplorerM3uPlaylistPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExplorerM3uPlaylistPolicyTest {

    @Test
    fun amberTakesPriorityRegardlessOfDiscoveryOrder() {
        val video = ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE
        val audio = ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE

        assertEquals(video, ExplorerM3uPlaylistPolicy.preferredPackage(linkedSetOf(audio, video)))
        assertEquals(video, ExplorerM3uPlaylistPolicy.preferredPackage(linkedSetOf(video, audio)))
    }

    @Test
    fun eitherOfficialPlayerCanBeUsedAlone() {
        listOf(ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE, ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE).forEach { player ->
            assertEquals(player, ExplorerM3uPlaylistPolicy.preferredPackage(setOf(player)))
        }
    }

    @Test
    fun eitherPlayerCanMatchM3uDespiteAudioVideoClassificationOrPlatformMime() {
        listOf("audio/x-mpegurl", "video/*", "application/octet-stream").forEach { mime ->
            assertEquals("video/*", ExplorerM3uPlaylistPolicy.resolveMimeType(
                ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE, "M3U", mime,
            ))
            assertEquals("audio/*", ExplorerM3uPlaylistPolicy.resolveMimeType(
                ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE, "m3u", mime,
            ))
            assertEquals(mime, ExplorerM3uPlaylistPolicy.resolveMimeType("example.other.player", "m3u", mime))
        }
    }

    @Test
    fun otherFileTypesKeepTheirExistingMimeRouting() {
        listOf("m3u8", "pls", "mp3", "mp4", "ts").forEach { extension ->
            assertEquals("original/mime", ExplorerM3uPlaylistPolicy.resolveMimeType(
                ExplorerM3uPlaylistPolicy.VIDEO_PACKAGE, extension, "original/mime",
            ))
            assertEquals("original/mime", ExplorerM3uPlaylistPolicy.resolveMimeType(
                ExplorerM3uPlaylistPolicy.AUDIO_PACKAGE, extension, "original/mime",
            ))
        }
    }

    @Test
    fun missingOfficialPlayersRequireGuidanceEvenIfAnotherPluginIsAvailable() {
        assertNull(ExplorerM3uPlaylistPolicy.preferredPackage(emptySet()))
        assertNull(ExplorerM3uPlaylistPolicy.preferredPackage(setOf("example.other.player")))
        assertNull(ExplorerM3uPlaylistPolicy.preferredPackage(setOf("io.github.supermonster003.autojs6.plugin.videoplayer")))
    }
}
