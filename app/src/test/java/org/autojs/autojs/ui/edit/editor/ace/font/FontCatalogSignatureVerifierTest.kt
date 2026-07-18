package org.autojs.autojs.ui.edit.editor.ace.font

import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class FontCatalogSignatureVerifierTest {
    @Test
    fun verifiesStrictJsonEnvelopeOverExactCatalogBytes() {
        val keys = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val catalog = FontTestFixtures.catalogJson().toByteArray()
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keys.private)
            update(catalog)
            sign()
        }
        val envelope = envelope(Base64.getEncoder().encodeToString(signature))
        val verifier = EcdsaP256CatalogSignatureVerifier(keys.public, expectedKeyId = KEY_ID)

        verifier.verify(catalog, envelope.toByteArray())
        assertThrows(FontCatalogSignatureException::class.java) {
            verifier.verify(catalog, signature)
        }
        assertThrows(FontCatalogSignatureException::class.java) {
            verifier.verify(catalog, Base64.getEncoder().encode(signature))
        }
        assertThrows(FontCatalogSignatureException::class.java) {
            verifier.verify(catalog + '\n'.code.toByte(), envelope.toByteArray())
        }
        assertThrows(FontCatalogSignatureException::class.java) {
            verifier.verify(catalog, envelope.replace(KEY_ID, "another-key").toByteArray())
        }
        assertThrows(FontCatalogSignatureException::class.java) {
            verifier.verify(catalog, envelope.dropLast(1).plus(",\"extra\":true}").toByteArray())
        }
    }

    @Test
    fun rejectsNonP256Key() {
        val p384 = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp384r1"))
            generateKeyPair()
        }
        assertThrows(FontCatalogSignatureException::class.java) {
            EcdsaP256CatalogSignatureVerifier(p384.public, expectedKeyId = KEY_ID)
        }
    }

    private fun envelope(signature: String): String =
        """{"algorithm":"ECDSA_P256_SHA256","catalog":"catalog-v1.json","keyId":"$KEY_ID","signature":"$signature"}"""

    companion object {
        private const val KEY_ID = "test-key-1"
    }
}
