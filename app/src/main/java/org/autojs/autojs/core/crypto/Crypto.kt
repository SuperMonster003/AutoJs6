@file:Suppress("unused")

package org.autojs.autojs.core.crypto

import android.util.Base64
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.util.ArrayUtils
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by SuperMonster003 on Jun 15, 2023.
 */
// @Reference to com.stardust.autojs.core.cypto.Crypto.class from Auto.js Pro 9.3.11 on Jun 15, 2023.
//  ! There is a strong possibility that "cypto" is a typo.
// @Reference to module __$crypto__.js from Auto.js Pro 9.3.11  on Jun 15, 2023.
object Crypto {

    private val scriptRuntime by lazy { AutoJs.instance.runtime }

    private const val A = 97
    private const val F = 102
    private const val NINE = 57
    private const val ZERO = 48

    private const val DEFAULT_DIGEST_ALGORITHM = "MD5"

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    private fun singleHexToNumber(paramChar: Char): Byte {
        val byte = paramChar.lowercaseChar().code.toByte()
        if (byte in ZERO..NINE) {
            return (byte - ZERO).toByte()
        }
        if (byte in A..F) {
            return (byte - A + 10).toByte()
        }
        throw IllegalArgumentException("char: $paramChar")
    }

    @JvmStatic
    fun fromHex(hex: String): ByteArray {
        if (hex.length % 2 != 0) {
            throw IllegalArgumentException("The length of hex string is required to be even.")
        }
        val max = hex.length / 2
        val arrayOfByte = ByteArray(max)
        for (i in 0 until max) {
            val j = i * 2
            arrayOfByte[i] = (singleHexToNumber(hex[j]) * 16 + singleHexToNumber(hex[j + 1])).toByte()
        }
        return arrayOfByte
    }

    @JvmStatic
    fun toHex(bytes: ByteArray): String {
        val stringBuilder = StringBuilder(bytes.size * 2)
        bytes.indices.forEach { i ->
            stringBuilder.append(HEX_DIGITS[bytes[i].toInt() and 0xF0 ushr 4])
            stringBuilder.append(HEX_DIGITS[bytes[i].toInt() and 0xF])
        }
        return stringBuilder.toString()
    }

    @JvmStatic
    @JvmOverloads
    fun digest(message: String, algorithm: String = DEFAULT_DIGEST_ALGORITHM, options: NativeObject = NativeObject()): Serializable {
        val messageDigest = MessageDigest.getInstance(algorithm)
        input(message, options) { bytes: ByteArray, start: Int, length: Int ->
            messageDigest.update(/* input = */ bytes, /* offset = */ start, /* len = */ length)
        }
        return output(messageDigest.digest(), options, "hex")
    }

    @JvmStatic
    fun digest(message: String, options: NativeObject) = digest(message, DEFAULT_DIGEST_ALGORITHM, options)

    @JvmStatic
    @JvmOverloads
    fun encrypt(data: Any, key: Any, transformation: String, options: NativeObject = NativeObject()): Serializable {
        return cipher(data, Cipher.ENCRYPT_MODE, key, transformation, options)
    }

    @JvmStatic
    @JvmOverloads
    fun decrypt(data: Any, key: Any, transformation: String, options: NativeObject = NativeObject()): Serializable {
        return cipher(data, Cipher.DECRYPT_MODE, key, transformation, options)
    }

    private fun cipher(data: Any, mode: Int, key: Any, transformation: String, options: NativeObject): Serializable {
        val niceKey = when (key) {
            is Key -> key
            is java.security.Key -> Key(key.encoded)
            else -> throw Exception("Unknown type of key: ${key::class.java}")
        }
        val cipher = Cipher.getInstance(transformation)
        when (val iv = options["iv"]) {
            is String -> cipher.init(mode, niceKey.toKeySpec(transformation), IvParameterSpec(iv.toByteArray()))
            is ByteArray -> cipher.init(mode, niceKey.toKeySpec(transformation), IvParameterSpec(iv))
            is NativeArray -> cipher.init(mode, niceKey.toKeySpec(transformation), IvParameterSpec(ArrayUtils.jsBytesToByteArray(iv)))
            is AlgorithmParameterSpec -> cipher.init(mode, niceKey.toKeySpec(transformation), iv)
            else -> cipher.init(mode, niceKey.toKeySpec(transformation))
        }
        if (options["output"] == "file") {
            val dest = options["dest"] as? String ?: throw IllegalArgumentException(
                "Property \"dest\" is required when writing output to a file"
            )
            val fos = FileOutputStream(scriptRuntime.files.path(dest))
            writeInputData(fos, cipher, data, options)
        }
        val bos = ByteArrayOutputStream()
        writeInputData(bos, cipher, data, options)
        return output(bos.toByteArray(), options, "bytes").also { bos.close() }
    }

    private fun writeInputData(os: OutputStream, cipher: Cipher, data: Any, options: NativeObject) {
        val cos = CipherOutputStream(os, cipher)
        input(data, options) { bytes, start, length ->
            cos.write(bytes, start, length)
        }
        cos.close()
    }

    @JvmStatic
    @JvmOverloads
    fun generateKeyPair(algorithm: String, length: Int = 256): KeyPair {
        return KeyPairGenerator.getInstance(algorithm).apply {
            initialize(length)
        }.generateKeyPair().let {
            KeyPair(it.public.encoded, it.private.encoded).apply {
                keyPairGeneratorAlgorithm = algorithm
            }
        }
    }

    /**
     * @param input { ByteArray | NativeArray | String }
     */
    private fun input(input: Any, options: NativeObject, callback: (bytes: ByteArray, start: Int, length: Int) -> Unit) {
        when (input) {
            is ByteArray -> callback(input, 0, input.size)
            is NativeArray -> callback(ArrayUtils.jsBytesToByteArray(input), 0, input.size)
            is String -> when (options["input"]) {
                "file" -> {
                    val fis = FileInputStream(scriptRuntime.files.path(input))
                    val buffer = ByteArray(4096)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        callback(buffer, 0, read)
                    }
                }
                "base64" -> Base64.decode(input, Base64.NO_WRAP).also {
                    callback(it, 0, it.size)
                }
                "hex" -> fromHex(input).also {
                    callback(it, 0, it.size)
                }
                else -> {
                    val encoding = when (val optEncoding = options["encoding"]) {
                        is String -> Charset.forName(optEncoding)
                        else -> Charsets.UTF_8
                    }
                    input.toByteArray(encoding).also {
                        callback(it, 0, it.size)
                    }
                }
            }
            else -> throw Exception("Unknown type of input (${input.javaClass})")
        }
    }

    private fun output(bytes: ByteArray, options: NativeObject, defaultFormat: String): Serializable {
        return when (options["output"]?.takeUnless { it is Undefined } ?: defaultFormat) {
            "bytes" -> bytes
            "base64" -> Base64.encodeToString(bytes, Base64.NO_WRAP)
            "string" -> {
                val encoding = when (val optEncoding = options["encoding"]) {
                    is String -> Charset.forName(optEncoding)
                    else -> Charsets.UTF_8
                }
                String(bytes, encoding)
            }
            else -> toHex(bytes)
        }
    }

    /**
     * @param data { ByteArray | NativeArray | String }
     */
    class Key internal constructor(data: Any, options: NativeObject, isPublic: Boolean?) {

        val data: ByteArray

        val keyPair: String?

        init {
            this.keyPair = isPublic?.let {
                if (it) KEY_PAIR_PUBLIC else KEY_PAIR_PRIVATE
            } ?: (options["keyPair"] as? String) ?.also {
                if (it != KEY_PAIR_PUBLIC && it != KEY_PAIR_PRIVATE) {
                    throw Exception("Unknown keyPair ($it)")
                }
            }
            val bos = ByteArrayOutputStream()
            input(data, options) { bytes, start, length ->
                bos.write(bytes, start, length)
            }
            this.data = bos.toByteArray()
        }

        constructor(data: Any) : this(data, NativeObject())

        constructor(data: Any, options: NativeObject) : this(data, options, null)

        fun toKeySpec(transformation: String): java.security.Key {
            val i = transformation.indexOf('/')
            val algorithm = if (i >= 0) transformation.substring(0, i) else transformation
            if (algorithm == "RSA") {
                if (keyPair == KEY_PAIR_PUBLIC) {
                    return KeyFactory.getInstance(algorithm).generatePublic(X509EncodedKeySpec(this.data))
                }
                if (keyPair == KEY_PAIR_PRIVATE) {
                    return KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(this.data))
                }
                throw Exception("Unknown keyPair (${keyPair})")
            }
            return SecretKeySpec(this.data, algorithm)
        }

        override fun toString(): String {
            val data = Base64.encodeToString(this.data, Base64.NO_WRAP)
            return this.keyPair?.let { "Key[${this.keyPair}]{data=\'$data\'}" } ?: "Key{data=\'$data\'}"
        }

        companion object {

            private const val KEY_PAIR_PUBLIC = "public"
            private const val KEY_PAIR_PRIVATE = "private"

        }

    }

    /**
     * @param publicKeyData { String | ByteArray | NativeArray }
     * @param privateKeyData { String | ByteArray | NativeArray }
     */
    class KeyPair(publicKeyData: Any, privateKeyData: Any, options: NativeObject) {

        @get:ScriptInterface
        val publicKey: Key

        @get:ScriptInterface
        val privateKey: Key

        internal var keyPairGeneratorAlgorithm: String? = null

        /**
         * @param publicKeyData { String | ByteArray | NativeArray }
         * @param privateKeyData { String | ByteArray | NativeArray }
         */
        constructor(publicKeyData: Any, privateKeyData: Any) : this(publicKeyData, privateKeyData, NativeObject())

        init {
            this.publicKey = Key(publicKeyData, options, true)
            this.privateKey = Key(privateKeyData, options, false)
        }

        fun toKeySpec(transformation: String): java.security.Key {
            val keyFactory = KeyFactory.getInstance(
                keyPairGeneratorAlgorithm
                ?: throw Exception("keyPairGeneratorAlgorithm must be defined first")
            )

            val x509KeySpec = X509EncodedKeySpec(publicKey.data)
            val pubKey = keyFactory.generatePublic(x509KeySpec)

            val pkcs8KeySpec = PKCS8EncodedKeySpec(privateKey.data)
            val priKey = keyFactory.generatePrivate(pkcs8KeySpec)

            val keyAgreement = KeyAgreement.getInstance(keyFactory.algorithm).apply {
                init(priKey)
                doPhase(pubKey, true)
            }

            val i = transformation.indexOf('/')
            val cipherAlgorithm = if (i >= 0) transformation.substring(0, i) else transformation
            return keyAgreement.generateSecret(cipherAlgorithm)
        }

        override fun toString() = """{
            |   publicKey: $publicKey,
            |   privateKey: $privateKey,
            |}""".trimMargin()

    }

}