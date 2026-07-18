package org.autojs.autojs.engine.encryption

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ScriptEncryptionTest {

    @Test
    fun decryptorSnapshotIsIsolatedFromLaterKeyConfiguration() {
        val firstKey = "0123456789abcdef0123456789abcdef"
        val firstVector = "0123456789abcdef"
        val secondKey = "fedcba9876543210fedcba9876543210"
        val secondVector = "fedcba9876543210"
        val cleartext = "request-scoped encrypted module source".toByteArray()
        val firstCiphertext = AdvancedEncryptionStandard(
            firstKey.toByteArray(),
            firstVector,
        ).encrypt(cleartext)
        val secondCiphertext = AdvancedEncryptionStandard(
            secondKey.toByteArray(),
            secondVector,
        ).encrypt(cleartext)

        ScriptEncryption.configure(firstKey, firstVector)
        val firstSnapshot = ScriptEncryption.snapshotDecryptor()
        ScriptEncryption.configure(secondKey, secondVector)

        assertArrayEquals(cleartext, firstSnapshot.decrypt(firstCiphertext))
        assertArrayEquals(cleartext, ScriptEncryption.decrypt(secondCiphertext))
    }
}
