package org.autojs.autojs.core.plugin.typescript

import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException
import org.autojs.plugin.apkbuilder.template.TypeScriptBuildStagingCipher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptBuildStagingCipherTest {

    @Test
    fun authenticatesRoundTripWithoutRetainingCleartext() {
        val key = TypeScriptBuildStagingCipher.generateKey()
        val cleartext = "const secret = 42;".toByteArray(StandardCharsets.UTF_8)
        val envelope = TypeScriptBuildStagingCipher.encrypt(cleartext, key)
        try {
            assertTrue(TypeScriptBuildStagingCipher.isEncrypted(envelope))
            assertFalse(envelope.toString(StandardCharsets.UTF_8).contains("secret"))
            val restored = TypeScriptBuildStagingCipher.decrypt(envelope, key)
            try {
                assertArrayEquals(cleartext, restored)
            } finally {
                restored.fill(0)
            }
        } finally {
            key.fill(0)
            cleartext.fill(0)
            envelope.fill(0)
        }
    }

    @Test
    fun rejectsTamperedEnvelope() {
        val key = TypeScriptBuildStagingCipher.generateKey()
        val cleartext = byteArrayOf(1, 2, 3, 4)
        val envelope = TypeScriptBuildStagingCipher.encrypt(cleartext, key)
        try {
            envelope[envelope.lastIndex] = (envelope.last() + 1).toByte()
            var rejected = false
            try {
                TypeScriptBuildStagingCipher.decrypt(envelope, key).fill(0)
            } catch (_: AEADBadTagException) {
                rejected = true
            }
            assertTrue("Tampered staging data was accepted", rejected)
        } finally {
            key.fill(0)
            cleartext.fill(0)
            envelope.fill(0)
        }
    }
}
