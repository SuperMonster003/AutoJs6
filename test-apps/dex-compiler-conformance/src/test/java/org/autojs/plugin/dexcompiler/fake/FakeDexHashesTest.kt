package org.autojs.plugin.dexcompiler.fake

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class FakeDexHashesTest {
    @Test
    fun hashesKnownSha256Vector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FakeDexHashes.sha256("abc".toByteArray(Charsets.US_ASCII)).toHexString(),
        )
    }

    @Test
    fun streamAndByteArrayHashesMatch() {
        val bytes = ByteArray(32_769) { index -> (index * 31).toByte() }
        assertEquals(
            FakeDexHashes.sha256(bytes),
            ByteArrayInputStream(bytes).use(FakeDexHashes::sha256),
        )
    }

    @Test
    fun embeddedDexMatchesPinnedHash() {
        assertEquals(EmbeddedDexFixture.SIZE_BYTES, EmbeddedDexFixture.bytes().size)
        assertEquals(EmbeddedDexFixture.SHA256_HEX, FakeDexHashes.sha256(EmbeddedDexFixture.bytes()).toHexString())
        assertEquals(EmbeddedDexFixture.sha256, FakeDexHashes.sha256(EmbeddedDexFixture.bytes()))
    }
}
