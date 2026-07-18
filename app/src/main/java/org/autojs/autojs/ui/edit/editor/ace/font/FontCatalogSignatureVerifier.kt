package org.autojs.autojs.ui.edit.editor.ace.font

import com.google.gson.JsonParser
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.X509EncodedKeySpec

fun interface FontCatalogSignatureVerifier {
    @Throws(FontCatalogSignatureException::class)
    fun verify(catalogBytes: ByteArray, signatureBytes: ByteArray)
}

/** Verifies a signature over the exact catalog bytes, without JSON normalization. */
class EcdsaP256CatalogSignatureVerifier(
    private val publicKey: PublicKey,
    private val signatureEncoding: SignatureEncoding = SignatureEncoding.JSON_ENVELOPE,
    private val expectedKeyId: String? = null,
    private val expectedCatalogFileName: String = FontCatalogRepository.DEFAULT_CATALOG_FILE_NAME,
) : FontCatalogSignatureVerifier {

    init {
        validateP256Key(publicKey)
    }

    override fun verify(catalogBytes: ByteArray, signatureBytes: ByteArray) {
        val derSignature = try {
            when (signatureEncoding) {
                SignatureEncoding.DER -> signatureBytes
                SignatureEncoding.BASE64 -> Base64Codec.decode(signatureBytes.toString(Charsets.US_ASCII))
                SignatureEncoding.JSON_ENVELOPE -> decodeEnvelope(signatureBytes)
                SignatureEncoding.AUTO -> decodeAutomatic(signatureBytes)
            }
        } catch (e: Exception) {
            throw FontCatalogSignatureException("Invalid font catalog signature encoding", e)
        }
        if (derSignature.isEmpty()) throw FontCatalogSignatureException("Font catalog signature is empty")

        val valid = try {
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(catalogBytes)
                verify(derSignature)
            }
        } catch (e: Exception) {
            throw FontCatalogSignatureException("Unable to verify font catalog signature", e)
        }
        if (!valid) throw FontCatalogSignatureException("Font catalog signature verification failed")
    }

    enum class SignatureEncoding {
        AUTO,
        DER,
        BASE64,
        JSON_ENVELOPE,
    }

    private fun decodeAutomatic(bytes: ByteArray): ByteArray {
        val first = bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }
        return when (first?.toInt()?.and(0xff)) {
            0x30 -> bytes
            '{'.code -> decodeEnvelope(bytes)
            else -> Base64Codec.decode(bytes.toString(Charsets.US_ASCII))
        }
    }

    private fun decodeEnvelope(bytes: ByteArray): ByteArray {
        val root = try {
            JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed signature envelope", e)
        }
        val expectedFields = setOf("algorithm", "catalog", "keyId", "signature")
        if (root.keySet() != expectedFields) {
            throw IllegalArgumentException("Signature envelope must contain exactly $expectedFields")
        }
        fun requiredString(name: String): String {
            val value = root.get(name)
            if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString ||
                value.asString.isBlank()
            ) {
                throw IllegalArgumentException("Signature envelope field '$name' must be a non-blank string")
            }
            return value.asString
        }

        val algorithm = requiredString("algorithm")
        if (algorithm != ENVELOPE_ALGORITHM) {
            throw IllegalArgumentException("Unexpected signature algorithm '$algorithm'")
        }
        val catalog = requiredString("catalog")
        if (catalog != expectedCatalogFileName) {
            throw IllegalArgumentException("Signature envelope names unexpected catalog '$catalog'")
        }
        val keyId = requiredString("keyId")
        if (!KEY_ID.matches(keyId)) throw IllegalArgumentException("Invalid signature key id")
        val requiredKeyId = expectedKeyId
            ?: throw IllegalArgumentException("An expected key id is required for a signature envelope")
        if (keyId != requiredKeyId) {
            throw IllegalArgumentException("Unexpected signature key id '$keyId'")
        }
        return Base64Codec.decode(requiredString("signature"), allowWhitespace = false).also {
            if (it.isEmpty()) throw IllegalArgumentException("Signature envelope contains an empty signature")
        }
    }

    companion object {
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        private const val ENVELOPE_ALGORITHM = "ECDSA_P256_SHA256"
        private val KEY_ID = Regex("[A-Za-z0-9._-]{1,64}")

        @JvmStatic
        @Throws(FontCatalogSignatureException::class)
        fun publicKeyFromPem(pem: String): PublicKey {
            val payload = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
            return publicKeyFromDer(Base64Codec.decode(payload))
        }

        @JvmStatic
        @Throws(FontCatalogSignatureException::class)
        fun publicKeyFromDer(der: ByteArray): PublicKey {
            val key = try {
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
            } catch (e: Exception) {
                throw FontCatalogSignatureException("Invalid ECDSA public key", e)
            }
            validateP256Key(key)
            return key
        }

        private fun validateP256Key(key: PublicKey) {
            val ecKey = key as? ECPublicKey
                ?: throw FontCatalogSignatureException("Font catalog key must be an EC public key")
            val params = ecKey.params
                ?: throw FontCatalogSignatureException("Font catalog EC key has no curve parameters")
            val field = params.curve.field as? ECFieldFp
            if (field?.p != P256_P || params.curve.a != P256_A || params.curve.b != P256_B ||
                params.generator.affineX != P256_GX || params.generator.affineY != P256_GY ||
                params.order != P256_N || params.cofactor != 1
            ) {
                throw FontCatalogSignatureException("Font catalog key must use the P-256 curve")
            }
        }

        private val P256_P = hex("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF")
        private val P256_A = P256_P.subtract(BigInteger.valueOf(3))
        private val P256_B = hex("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B")
        private val P256_GX = hex("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296")
        private val P256_GY = hex("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5")
        private val P256_N = hex("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551")

        private fun hex(value: String): BigInteger = BigInteger(value, 16)
    }
}

/** Small Android-24-safe RFC 4648 decoder used for public keys and detached signatures. */
private object Base64Codec {
    private val reverse = IntArray(256) { -1 }.also { table ->
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { index, char ->
            table[char.code] = index
        }
    }

    fun decode(value: String, allowWhitespace: Boolean = true): ByteArray {
        if (!allowWhitespace && value.any(Char::isWhitespace)) {
            throw IllegalArgumentException("Whitespace is not allowed in Base64")
        }
        val input = if (allowWhitespace) value.filterNot(Char::isWhitespace) else value
        if (input.isEmpty()) return ByteArray(0)
        if (input.length % 4 != 0) throw IllegalArgumentException("Invalid Base64 length")
        val padding = when {
            input.endsWith("==") -> 2
            input.endsWith('=') -> 1
            else -> 0
        }
        val firstPadding = input.indexOf('=')
        if (firstPadding >= 0 && firstPadding < input.length - padding) {
            throw IllegalArgumentException("Invalid Base64 padding")
        }
        val output = ByteArray(input.length / 4 * 3 - padding)
        var inputIndex = 0
        var outputIndex = 0
        while (inputIndex < input.length) {
            val a = sextet(input[inputIndex++], false)
            val b = sextet(input[inputIndex++], false)
            val c = sextet(input[inputIndex++], true)
            val d = sextet(input[inputIndex++], true)
            val finalBlock = inputIndex == input.length
            if ((!finalBlock && (c == PADDING || d == PADDING)) || (c == PADDING && d != PADDING)) {
                throw IllegalArgumentException("Invalid Base64 padding")
            }
            if (finalBlock && padding == 2 && (b and 0x0f) != 0 ||
                finalBlock && padding == 1 && (c and 0x03) != 0
            ) {
                throw IllegalArgumentException("Non-canonical Base64 padding")
            }
            val bits = (a shl 18) or (b shl 12) or ((c and 63) shl 6) or (d and 63)
            if (outputIndex < output.size) output[outputIndex++] = (bits ushr 16).toByte()
            if (outputIndex < output.size) output[outputIndex++] = (bits ushr 8).toByte()
            if (outputIndex < output.size) output[outputIndex++] = bits.toByte()
        }
        return output
    }

    private fun sextet(char: Char, allowPadding: Boolean): Int {
        if (char == '=' && allowPadding) return PADDING
        return reverse.getOrNull(char.code)?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid Base64 character")
    }

    private const val PADDING = 64
}
