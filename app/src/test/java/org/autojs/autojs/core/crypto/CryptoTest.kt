package org.autojs.autojs.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import java.nio.file.Files
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

class CryptoTest {

    @Test
    fun `digest accepts bytes native arrays strings and files`() {
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val bytes = byteArrayOf(97, 98, 99)
        val nativeArray = NativeArray(arrayOf<Any?>(97, 98, 99))
        val file = Files.createTempFile("autojs6-crypto-digest-", ".txt")

        try {
            Files.write(file, bytes)
            assertEquals(expected, Crypto.digest(bytes, "SHA-256"))
            assertEquals(expected, Crypto.digest(nativeArray, "SHA-256"))
            assertEquals(expected, Crypto.digest("abc", "SHA-256"))
            assertEquals(
                expected,
                Crypto.digest(
                    file.toString(),
                    "SHA-256",
                    options("input" to "file"),
                ),
            )
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `cipher file output returns undefined and writes only to destination`() {
        val destination = Files.createTempFile("autojs6-crypto-output-", ".bin")

        try {
            val result = Crypto.encrypt(
                "payload",
                Crypto.Key("0123456789abcdef"),
                "AES/ECB/PKCS5Padding",
                options(
                    "output" to "file",
                    "dest" to destination.toString(),
                ),
            )

            assertSame(Undefined.instance, result)
            assertTrue(Files.size(destination) > 0)
        } finally {
            Files.deleteIfExists(destination)
        }
    }

    @Test
    fun `provider default key pair sizes are usable for RSA and EC`() {
        val rsaPair = Crypto.generateKeyPair("RSA")
        val rsaPublicKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(rsaPair.publicKey.data)) as RSAPublicKey
        assertTrue(rsaPublicKey.modulus.bitLength() >= 2048)

        val ecPair = Crypto.generateKeyPair("EC")
        val ecPublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(ecPair.publicKey.data)) as ECPublicKey
        assertTrue(ecPublicKey.params.curve.field.fieldSize >= 256)
    }

    @Test
    fun `default key pair generation lets each provider choose valid parameters`() {
        val dsaPair = Crypto.generateKeyPair("DSA")

        assertTrue(dsaPair.publicKey.data.isNotEmpty())
        assertTrue(dsaPair.privateKey.data.isNotEmpty())
    }

    private fun options(vararg entries: Pair<String, Any?>) = NativeObject().apply {
        entries.forEach { (key, value) -> put(key, this, value) }
    }
}
