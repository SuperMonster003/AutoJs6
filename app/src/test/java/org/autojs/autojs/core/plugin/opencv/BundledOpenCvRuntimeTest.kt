package org.autojs.autojs.core.plugin.opencv

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledOpenCvRuntimeTest {

    private val arm64 = "988f653ee309848dae1a57b5bedb32d85c17c6f2ad9b8c7ec1a1bb4c3095d506"
    private val x8664 = "f9a66c2fec379e14757b11aa4220e67205c2e4d917b4dfffe7c6f99ba2623c6e"
    private val record = BundledOpenCvRuntime.Record(
        library = "libopencv_java4.so",
        packageName = "io.github.supermonster003.autojs6.plugin.opencv",
        versionName = "1.1.4",
        versionCode = 23L,
        sha256ByAbi = linkedMapOf("x86_64" to x8664, "arm64-v8a" to arm64),
    )

    @Test
    fun recordRoundTripsDeterministically() {
        val encoded = BundledOpenCvRuntime.encode(record)

        assertEquals(record, BundledOpenCvRuntime.decode(encoded))
        assertArrayEquals(encoded, BundledOpenCvRuntime.encode(record.copy(sha256ByAbi = record.sha256ByAbi.toSortedMap())))
        assertTrue(String(encoded, Charsets.UTF_8).startsWith("schema=1\n"))
    }

    @Test
    fun matchingAcceptsAnyRecordedAbiAndIgnoresCase() {
        assertTrue(record.matches(arm64.uppercase()))
        assertTrue(record.matches(x8664))
        assertFalse(record.matches("0".repeat(64)))
    }

    @Test
    fun incompleteOrUnknownRecordsAreRejected() {
        val text = String(BundledOpenCvRuntime.encode(record), Charsets.UTF_8)
        assertNull(BundledOpenCvRuntime.decode(text.replace("schema=1", "schema=2").toByteArray()))
        assertNull(BundledOpenCvRuntime.decode(text.replace(Regex("packageName=.*\n"), "").toByteArray()))
        assertNull(BundledOpenCvRuntime.decode(text.replace(arm64, "zz").replace(x8664, "short").toByteArray()))
        assertNull(BundledOpenCvRuntime.decode(ByteArray(0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHashesAreNotEncoded() {
        BundledOpenCvRuntime.encode(record.copy(sha256ByAbi = mapOf("arm64-v8a" to "not-a-hash")))
    }

    @Test
    fun hashesStreams() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            BundledOpenCvRuntime.sha256(ByteArray(0).inputStream()),
        )
    }
}
