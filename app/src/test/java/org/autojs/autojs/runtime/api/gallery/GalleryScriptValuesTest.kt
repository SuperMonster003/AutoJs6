package org.autojs.autojs.runtime.api.gallery

import org.autojs.autojs.core.plugin.gallery.GalleryError
import org.autojs.autojs.core.plugin.gallery.GalleryPickedImage
import org.autojs.autojs.core.plugin.gallery.GalleryWrittenImage
import org.autojs.plugin.gallery.api.GalleryContract
import org.autojs.plugin.gallery.api.GalleryErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The script values of picked and written images and the outcome of a session without a result (3-Maple Image roadmap M6, 10.1). */
class GalleryScriptValuesTest {

    @Test
    fun pickedImagesKeepTheContractFieldOrderAndScriptFriendlyNumbers() {
        val values = GalleryScriptValues.picked(listOf(GalleryPickedImage("/sdcard/DCIM/a.jpg", "a.jpg", "image/jpeg", 4000, 3000, 3_000_000_000L, 1_792_065_600_000L)))
        val first = values.single()
        assertEquals(listOf("path", "name", "mimeType", "width", "height", "size", "dateTaken"), first.keys.toList())
        assertEquals(4000, first["width"])
        assertEquals(3.0e9, first["size"])
        assertEquals(1.7920656e12, first["dateTaken"])
        assertEquals(512, GalleryScriptValues.number(512L))
    }

    @Test
    fun writtenImagesNameTheirFile() {
        val value = GalleryScriptValues.written(GalleryWrittenImage("/sdcard/Pictures/a_converted.webp", "image/webp", 800, 600, 51_200L))
        assertEquals(listOf("path", "name", "mimeType", "width", "height", "size"), value.keys.toList())
        assertEquals("a_converted.webp", value["name"])
        assertEquals(51_200, value["size"])
    }

    @Test
    fun aSessionWithoutResultIsACancelUnlessItFailed() {
        assertNull(GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_USER, null))
        assertNull(GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_DONE, null))
        assertNull(GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_HOST, null))
        assertEquals(GalleryErrorCodes.TIMEOUT, GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_TIMEOUT, null)?.code)
        assertEquals(GalleryErrorCodes.SESSION_REPLACED, GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_REPLACED, null)?.code)
        assertEquals(GalleryErrorCodes.SESSION_CLOSED, GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_ERROR, null)?.code)
        assertEquals(GalleryErrorCodes.SESSION_CLOSED, GalleryScriptValues.closedWithoutResult(null, null)?.code)
        val refused = GalleryError(GalleryErrorCodes.PERMISSION_REQUIRED, "no photo access")
        assertEquals(refused, GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_ERROR, refused))
        assertEquals(refused, GalleryScriptValues.closedWithoutResult(GalleryContract.REASON_USER, refused))
    }
}
