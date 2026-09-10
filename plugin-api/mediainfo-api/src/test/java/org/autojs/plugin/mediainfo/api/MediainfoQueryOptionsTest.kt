package org.autojs.plugin.mediainfo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediainfoQueryOptionsTest {
    @Test fun validatesIndexesWithoutTruncationOrCoercion() {
        assertEquals(MediainfoQueryOptions(), MediainfoQueryOptions.parse())
        assertEquals(MediainfoQueryOptions(1, "MEASURE"), MediainfoQueryOptions.parse(1.0, "measure"))
        for (value in listOf(-1, 0.5, Double.NaN, Double.POSITIVE_INFINITY, 2147483648L, "1", true)) {
            assertThrows("Accepted streamNumber=$value", IllegalArgumentException::class.java) {
                MediainfoQueryOptions.parse(value)
            }
        }
        for (kind in listOf("MAX", "COUNT", "unknown", 1)) {
            assertThrows(IllegalArgumentException::class.java) { MediainfoQueryOptions.parse(infoKind = kind) }
        }
    }

    @Test fun replacesIdentityWithoutChangingMetadataOrInterpretingReplacementTokens() {
        val original = "File\r\nComplete name : /proc/self/fd/82\r\n\r\nGeneral\nTitle : /proc/self/fd/82\n"
        val name = "/storage/媒体/$1\\take:01\n.wav"
        val report = MediainfoReportIdentity.inform(original, name)
        assertEquals("File\r\nComplete name : /storage/媒体/$1\\take:01\\n.wav\r\n\r\nGeneral\nTitle : /proc/self/fd/82\n", report)
    }
}
