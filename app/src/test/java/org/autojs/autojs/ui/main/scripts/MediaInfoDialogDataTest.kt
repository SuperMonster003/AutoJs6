package org.autojs.autojs.ui.main.scripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaInfoDialogDataTest {

    @Test
    fun informIsParsedIntoOneDialogSnapshot() {
        val data = MediaInfoInformParser.parse(
            """
            General
            Format                                   : MPEG-4
            File size                                : 12.3 MiB
            Duration                                 : 1 min 23 s
            Track name                               : Sample
            Performer                                : Artist
            Album                                    : Album

            Video
            Format                                   : AVC
            Bit rate                                 : 1 500 kb/s
            Width                                    : 1 920 pixels
            Height                                   : 1 080 pixels
            Display aspect ratio                     : 16:9
            Frame rate                               : 29.970 FPS

            Audio
            Format                                   : AAC LC
            Bit rate                                 : 128 kb/s
            """.trimIndent()
        )

        assertEquals("MPEG-4", data.containerFormat)
        assertEquals("AVC", data.videoFormat)
        assertEquals("AAC LC", data.audioFormat)
        assertEquals("12.3 MiB", data.fileSize)
        assertEquals("1 min 23 s", data.duration)
        assertEquals("1 500 kb/s", data.videoBitRate)
        assertEquals("128 kb/s", data.audioBitRate)
        assertEquals("Sample", data.trackName)
        assertEquals("Artist", data.performer)
        assertEquals("Album", data.album)
        assertEquals("1 920 pixels", data.width)
        assertEquals("1 080 pixels", data.height)
        assertEquals("16:9", data.aspectRatio)
        assertEquals("29.970 FPS", data.frameRate)
        assertTrue(data.hasVideo)
        assertTrue(data.hasAudio)
        assertEquals(MediaInfoDialogKind.VIDEO, data.resolveDialogKind(isMediaMenu = false))
    }

    @Test
    fun firstStreamWinsWhenInformContainsMultipleStreams() {
        val data = MediaInfoInformParser.parse(
            """
            General
            Format : Matroska
            Video
            Format : AVC
            Video #2
            Format : HEVC
            """.trimIndent()
        )

        assertEquals("AVC", data.videoFormat)
        assertTrue(data.hasVideo)
        assertFalse(data.hasAudio)
    }

    @Test
    fun videoWithEmbeddedAudioKeepsVideoDialogKind() {
        val data = MediaInfoDialogData(
            videoFormat = "AVC",
            audioFormat = "AAC",
            audioBitRate = "128 kb/s",
            trackName = "Sample",
            performer = "Artist",
            album = "Album",
            hasVideo = true,
            hasAudio = true,
        )

        val dialogKind = data.resolveDialogKind(isMediaMenu = false)

        assertEquals(MediaInfoDialogKind.VIDEO, dialogKind)
        assertTrue(dialogKind.showsVideoDetails)
        assertFalse(dialogKind.showsAudioDetails)
        assertTrue(dialogKind.showsSplitLine)
    }

    @Test
    fun audioOnlyMediaUsesAudioDialogKind() {
        val data = MediaInfoDialogData(
            audioFormat = "FLAC",
            hasAudio = true,
        )

        val dialogKind = data.resolveDialogKind(isMediaMenu = false)

        assertEquals(MediaInfoDialogKind.AUDIO, dialogKind)
        assertFalse(dialogKind.showsVideoDetails)
        assertTrue(dialogKind.showsAudioDetails)
        assertTrue(dialogKind.showsSplitLine)
    }

    @Test
    fun streamFlagsAndFormatFallbacksRetainVideoPriority() {
        assertEquals(
            MediaInfoDialogKind.VIDEO,
            MediaInfoDialogData(hasVideo = true, hasAudio = true)
                .resolveDialogKind(isMediaMenu = true),
        )
        assertEquals(
            MediaInfoDialogKind.VIDEO,
            MediaInfoDialogData(videoFormat = "AVC", audioFormat = "AAC")
                .resolveDialogKind(isMediaMenu = false),
        )
        assertEquals(
            MediaInfoDialogKind.AUDIO,
            MediaInfoDialogData(hasAudio = true)
                .resolveDialogKind(isMediaMenu = false),
        )
        assertEquals(
            MediaInfoDialogKind.AUDIO,
            MediaInfoDialogData(audioFormat = "AAC")
                .resolveDialogKind(isMediaMenu = false),
        )
    }

    @Test
    fun filesWithoutStreamsRetainMediaMenuAndOtherKinds() {
        val data = MediaInfoDialogData()

        val mediaMenuKind = data.resolveDialogKind(isMediaMenu = true)
        val otherKind = data.resolveDialogKind(isMediaMenu = false)

        assertEquals(MediaInfoDialogKind.MEDIA_MENU, mediaMenuKind)
        assertFalse(mediaMenuKind.showsVideoDetails)
        assertFalse(mediaMenuKind.showsAudioDetails)
        assertFalse(mediaMenuKind.showsSplitLine)
        assertEquals(MediaInfoDialogKind.OTHER, otherKind)
        assertFalse(otherKind.showsVideoDetails)
        assertFalse(otherKind.showsAudioDetails)
        assertFalse(otherKind.showsSplitLine)
    }

    @Test
    fun fallbackReportExplainsFailureAndRetainsPlatformMetadata() {
        val data = MediaInfoDialogData(
            containerFormat = "video/mp4",
            videoFormat = "video/avc",
            fileSize = "8.0 MB",
            duration = "1:05",
            hasVideo = true,
        )

        val report = data.asFallbackReport("MediaInfo library is not available")

        assertTrue(report.contains("Status : Unavailable"))
        assertTrue(report.contains("Reason : MediaInfo library is not available"))
        assertTrue(report.contains("Container format : video/mp4"))
        assertTrue(report.contains("Video format : video/avc"))
        assertTrue(report.contains("Duration : 1:05"))
    }

    @Test
    fun basicFilePropertiesRemainWhenBothMetadataReadersFail() {
        val data = MediaInfoDialogData().withEssentialFallbacks(
            fileSizeFallback = "8.0 MB",
            containerFormatFallback = "MP4",
        )

        assertEquals("MP4", data.containerFormat)
        assertEquals("8.0 MB", data.fileSize)
        assertFalse(data.hasVideo)
        assertFalse(data.hasAudio)
    }

    @Test
    fun libraryUnavailableCauseIsPreferredAndCollapsedToOneLine() {
        val error = IllegalStateException(
            "Binder call failed",
            IllegalArgumentException("MediaInfo library is not available\nremote stack"),
        )

        assertEquals(
            "MediaInfo library is not available",
            MediaInfoFailureMessage.from(error),
        )
    }
}
