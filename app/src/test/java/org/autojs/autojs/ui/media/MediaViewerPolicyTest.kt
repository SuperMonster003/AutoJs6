package org.autojs.autojs.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaViewerPolicyTest {

    @Test
    fun normalizesMimeTypeBeforeRouting() {
        assertEquals(
            "audio/mpeg",
            MediaViewerPolicy.normalizeMimeType(" Audio/MPEG ; charset=binary "),
        )
        assertEquals(
            MediaViewerKind.AUDIO,
            MediaViewerPolicy.kindForMimeType(" Audio/MPEG ; charset=binary "),
        )
    }

    @Test
    fun routesOnlySupportedMediaFamilies() {
        assertEquals(MediaViewerKind.IMAGE, MediaViewerPolicy.kindForMimeType("image/png"))
        assertEquals(MediaViewerKind.AUDIO, MediaViewerPolicy.kindForMimeType("audio/*"))
        assertEquals(MediaViewerKind.VIDEO, MediaViewerPolicy.kindForMimeType("video/mp4"))
        assertNull(MediaViewerPolicy.kindForMimeType("application/octet-stream"))
        assertNull(MediaViewerPolicy.kindForMimeType("text/plain"))
    }

    @Test
    fun externalRoutingRequiresViewActionAndLocalDataScheme() {
        assertEquals(
            MediaViewerKind.IMAGE,
            MediaViewerPolicy.resolveExternal("android.intent.action.VIEW", "content", "image/png"),
        )
        assertNull(
            MediaViewerPolicy.resolveExternal("android.intent.action.VIEW", "file", "video/mp4"),
        )
        assertNull(
            MediaViewerPolicy.resolveExternal("android.intent.action.EDIT", "content", "image/png"),
        )
        assertNull(
            MediaViewerPolicy.resolveExternal("android.intent.action.VIEW", "https", "audio/mpeg"),
        )
    }

    @Test
    fun rejectsMalformedMimeTypes() {
        listOf(
            null,
            "",
            "image",
            "/png",
            "image/",
            "image/png/extra",
            "image/p ng",
            "image/\u0000png",
        ).forEach { mimeType ->
            assertNull(mimeType, MediaViewerPolicy.normalizeMimeType(mimeType))
        }
    }

    @Test
    fun sanitizesControlCharactersFromDisplayedFileNames() {
        assertEquals(
            "reportgpj.exe",
            MediaViewerLauncher.sanitizeDisplayName(" report\u202Egpj.exe\n"),
        )
    }
}
