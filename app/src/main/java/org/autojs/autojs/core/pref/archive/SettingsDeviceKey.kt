package org.autojs.autojs.core.pref.archive

import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The key of this device that seals the sensitive categories (docs/settings/ROADMAP.md D23 / T21): 32 bytes derived
 * from `ANDROID_ID` with HKDF-SHA256 and never stored. AutoJs6 on the same device, of the same signature and for the
 * same user, therefore opens its archives and backups again after a reinstall, while another device, another user, a
 * factory reset or a build of another signature (debug and release) cannot. [fingerprint] names the key in a sealed
 * file ([SettingsSealedFile]) without revealing it. Pure JVM (R3); the app gets its key from `AndroidSettingsKeys`.
 *
 * zh-CN: 加密敏感分类的本机密钥 (ROADMAP D23 / T21): 由 `ANDROID_ID` 经 HKDF-SHA256 派生的 32 字节, 不落盘. 因此同一设备上同一签名,
 * 同一用户的 AutoJs6 重装后仍能打开自己的归档与备份, 其他设备, 其他用户, 恢复出厂或其他签名的构建 (debug 与 release) 则不能.
 * [fingerprint] 在加密文件 ([SettingsSealedFile]) 中标明所用密钥而不泄露密钥. 纯 JVM (R3); 应用经 `AndroidSettingsKeys` 取得密钥.
 */
class SettingsDeviceKey private constructor(private val bytes: ByteArray) {

    /**
     * HMAC-SHA256 of "fingerprint" under the key, its first 8 bytes in lowercase hex.
     * zh-CN: 以密钥计算 "fingerprint" 的 HMAC-SHA256, 取前 8 字节的小写十六进制.
     */
    val fingerprint: String = hmac(bytes, FINGERPRINT_LABEL).copyOf(8).joinToString("") { "%02x".format(it) }

    internal fun secret() = SecretKeySpec(bytes, "AES")

    /** Never the key itself. zh-CN: 不输出密钥本身. */
    override fun toString() = "SettingsDeviceKey($fingerprint)"

    companion object {

        /** The `kdf` of a device slot (appendix B). zh-CN: 设备槽的 `kdf` (附录 B). */
        const val KDF = "HKDF-SHA256"

        private const val LENGTH = 32

        private val SALT = "org.autojs.autojs6/settings-archive/device-key/v1".toByteArray(Charsets.UTF_8)

        private val INFO = "credentials".toByteArray(Charsets.UTF_8)

        private val FINGERPRINT_LABEL = "fingerprint".toByteArray(Charsets.UTF_8)

        /**
         * The key of the device whose `ANDROID_ID` (16 hex digits, case ignored) is [androidId].
         * zh-CN: `ANDROID_ID` (16 位十六进制, 不区分大小写) 为 [androidId] 的设备的密钥.
         */
        @JvmStatic
        fun fromAndroidId(androidId: String): SettingsDeviceKey {
            val id = androidId.trim().lowercase(Locale.ROOT)
            require(id.isNotEmpty()) { "ANDROID_ID is empty" }
            return SettingsDeviceKey(hkdf(id.toByteArray(Charsets.UTF_8), SALT, INFO, LENGTH))
        }

        /** A key of the 32 bytes [bytes], for tests. zh-CN: 以 32 字节 [bytes] 为密钥, 供测试使用. */
        @JvmStatic
        fun ofBytes(bytes: ByteArray): SettingsDeviceKey {
            require(bytes.size == LENGTH) { "A device key has $LENGTH bytes" }
            return SettingsDeviceKey(bytes.copyOf())
        }

        /** HKDF (RFC 5869) with HMAC-SHA256. zh-CN: 以 HMAC-SHA256 实现的 HKDF (RFC 5869). */
        @JvmStatic
        fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            require(length in 1..255 * 32) { "Invalid HKDF length $length" }
            val prk = hmac(salt.takeIf { it.isNotEmpty() } ?: ByteArray(32), ikm)
            val out = ByteArrayOutputStream()
            var block = ByteArray(0)
            var counter = 1
            while (out.size() < length) {
                block = hmac(prk, block + info + byteArrayOf(counter.toByte()))
                out.write(block)
                counter++
            }
            return out.toByteArray().copyOf(length)
        }

        internal fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }
    }
}
