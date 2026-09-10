package org.autojs.autojs.ui.explorer

import org.autojs.autojs.util.FileUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerPrimaryActionResolverTest {

    @Test
    fun previewableDocumentsResolveToDocumentPreviewer() {
        listOf(
            FileUtils.TYPE.MARKDOWN,
            FileUtils.TYPE.MARKDOWN_LONG,
            FileUtils.TYPE.HTML,
            FileUtils.TYPE.XHTML,
        ).forEach { type ->
            assertSame(
                type.name,
                ExplorerPrimaryAction.DOCUMENT_PREVIEWER,
                ExplorerPrimaryActionResolver.resolve(type),
            )
        }
    }

    @Test
    fun picturesResolveToImageViewer() {
        listOf(
            FileUtils.TYPE.JPG,
            FileUtils.TYPE.PNG,
            FileUtils.TYPE.GIF,
            FileUtils.TYPE.WEBP,
        ).forEach { type ->
            assertTrue(type.name, type.isImageViewable())
            assertFalse(type.name, type.isAudioPlayable())
            assertFalse(type.name, type.isVideoPlayable())
            assertSame(
                type.name,
                FileUtils.ViewerType.IMAGE,
                type.viewerType,
            )
            assertSame(
                type.name,
                ExplorerPrimaryAction.IMAGE_VIEW,
                ExplorerPrimaryActionResolver.resolve(type),
            )
        }
    }

    @Test
    fun supportedAudioFilesResolveToMediaPlayer() {
        listOf(
            FileUtils.TYPE.MP3,
            FileUtils.TYPE.AAC,
            FileUtils.TYPE.FLAC,
        ).forEach { type ->
            assertFalse(type.name, type.isImageViewable())
            assertTrue(type.name, type.isAudioPlayable())
            assertFalse(type.name, type.isVideoPlayable())
            assertTrue(type.name, type.isMediaPlayable())
            assertSame(
                type.name,
                FileUtils.ViewerType.AUDIO,
                type.viewerType,
            )
            assertSame(
                type.name,
                ExplorerPrimaryAction.MEDIA_PLAY,
                ExplorerPrimaryActionResolver.resolve(type),
            )
        }
    }

    @Test
    fun supportedVideoFilesResolveToMediaPlayer() {
        listOf(
            FileUtils.TYPE.MP4,
            FileUtils.TYPE.MKV,
            FileUtils.TYPE.WEBM,
        ).forEach { type ->
            assertFalse(type.name, type.isImageViewable())
            assertFalse(type.name, type.isAudioPlayable())
            assertTrue(type.name, type.isVideoPlayable())
            assertTrue(type.name, type.isMediaPlayable())
            assertSame(
                type.name,
                FileUtils.ViewerType.VIDEO,
                type.viewerType,
            )
            assertSame(
                type.name,
                ExplorerPrimaryAction.MEDIA_PLAY,
                ExplorerPrimaryActionResolver.resolve(type),
            )
        }
    }

    @Test
    fun everyExplorerVideoTypeResolvesToPrimaryMediaPlayer() {
        val videoTypes = FileUtils.TYPE.entries.filter { type -> type.isVideoFile() }
        assertTrue(
            videoTypes.containsAll(
                listOf(
                    FileUtils.TYPE.WMV,
                    FileUtils.TYPE.MXF,
                    FileUtils.TYPE.H264,
                    FileUtils.TYPE.M3U_VIDEO,
                    FileUtils.TYPE.M3U8_VIDEO,
                    FileUtils.TYPE.SWF,
                ),
            ),
        )
        videoTypes.forEach { type ->
            assertTrue(type.name, type.isVideoFile())
            assertTrue(type.name, type.isMediaPlayable())
            assertSame(
                type.name,
                ExplorerPrimaryAction.MEDIA_PLAY,
                ExplorerPrimaryActionResolver.resolve(type),
            )
        }
    }

    @Test
    fun m3uPlaylistsResolveToPrimaryMediaPlayerRegardlessOfContentClassification() {
        listOf(FileUtils.TYPE.M3U_AUDIO, FileUtils.TYPE.M3U_VIDEO).forEach { type ->
            assertSame(type.name, ExplorerPrimaryAction.MEDIA_PLAY, ExplorerPrimaryActionResolver.resolve(type))
        }
    }

    @Test
    fun unsupportedAudioFormatsRetainLegacyExternalPlayback() {
        listOf(
            FileUtils.TYPE.M3U8_AUDIO,
            FileUtils.TYPE.AUDIO_CUE_SHEET,
        ).forEach { type ->
            assertTrue(type.name, type.isMediaPlayable())
            assertNull(type.name, type.viewerType)
            assertNull(type.name, ExplorerPrimaryActionResolver.resolve(type))
        }
        assertTrue(FileUtils.TYPE.PSD.isExternalEditable())
        assertNull(FileUtils.TYPE.PSD.viewerType)
        assertSame(
            ExplorerPrimaryAction.IMAGE_VIEW_EXTERNAL,
            ExplorerPrimaryActionResolver.resolve(FileUtils.TYPE.PSD),
        )
    }

    @Test
    fun unrelatedFilesDoNotExposePrimaryViewerAction() {
        listOf(
            FileUtils.TYPE.JAVASCRIPT,
            FileUtils.TYPE.APK,
            FileUtils.TYPE.JSON,
            FileUtils.TYPE.UNKNOWN,
        ).forEach { type ->
            assertNull(type.name, type.viewerType)
            assertNull(type.name, ExplorerPrimaryActionResolver.resolve(type))
        }
    }
}
