package org.autojs.autojs.core.pref.archive

import java.text.Normalizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The optional password of a sealed category file (docs/settings/ROADMAP.md D23 / T21, P7.7): its password slot wraps
 * the content key with a key derived by PBKDF2-HMAC-SHA256 from the password (UTF-8 of its NFC form), a random salt of
 * [SALT_BYTES] and [iterations], so that another device opens the credentials when the user types the password the
 * export was given. [of] gives null for an empty password (the export wizard's "leave it empty": no password slot).
 * The keys derived are kept per salt and iteration count, so checking a password and then reading with it derives once.
 * PBKDF2 is written here over `HmacSHA256` because the `SecretKeyFactory` "PBKDF2WithHmacSHA256" needs Android 8.
 * Pure JVM (R3); like the device key, it never prints itself.
 *
 * zh-CN: 加密分类文件的可选口令 (ROADMAP D23 / T21, P7.7): 口令槽以 PBKDF2-HMAC-SHA256 由口令 (NFC 形式的 UTF-8), [SALT_BYTES]
 * 字节随机盐与 [iterations] 派生的密钥包裹内容密钥, 因此用户在其他设备上输入导出时设定的口令即可打开凭据. 口令为空时 [of] 返回
 * null (导出向导中 "留空": 不写口令槽). 派生出的密钥按盐与迭代次数保留, 因此先校验口令再以它读取只派生一次. PBKDF2 在此以
 * `HmacSHA256` 实现, 因为 `SecretKeyFactory` 的 "PBKDF2WithHmacSHA256" 需要 Android 8. 纯 JVM (R3); 与设备密钥一样不输出自身.
 */
class SettingsPassword private constructor(private val bytes: ByteArray, val iterations: Int) {

    private val derived = HashMap<String, ByteArray>()

    /** The key of a password slot with [salt] and [iterations]. zh-CN: 盐为 [salt], 迭代次数为 [iterations] 的口令槽密钥. */
    internal fun secret(salt: ByteArray, iterations: Int): SecretKeySpec = synchronized(derived) {
        val cacheKey = "$iterations:" + salt.joinToString("") { "%02x".format(it) }
        SecretKeySpec(derived.getOrPut(cacheKey) { pbkdf2(bytes, salt, iterations, KEY_BYTES) }, "AES")
    }

    /** Never the password itself. zh-CN: 不输出口令本身. */
    override fun toString() = "SettingsPassword"

    companion object {

        /** The `kdf` of a password slot (appendix B). zh-CN: 口令槽的 `kdf` (附录 B). */
        const val KDF = "PBKDF2-HMAC-SHA256"

        /**
         * The iterations a password slot is written with (OWASP 2023 for PBKDF2-HMAC-SHA256; appendix C Q30).
         * zh-CN: 写入口令槽时的迭代次数 (OWASP 2023 对 PBKDF2-HMAC-SHA256 的建议值; 附录 C Q30).
         */
        const val ITERATIONS = 600_000

        /**
         * The iteration counts a reader accepts: the upper bound keeps an altered file from hanging the import.
         * zh-CN: 读取方接受的迭代次数: 上限避免被改动的文件让导入长时间卡住.
         */
        const val MIN_ITERATIONS = 1_000
        const val MAX_ITERATIONS = 10_000_000

        const val SALT_BYTES = 16

        /** The salt sizes a reader accepts. zh-CN: 读取方接受的盐长度. */
        const val MAX_SALT_BYTES = 64

        private const val KEY_BYTES = 32

        private const val HASH_BYTES = 32

        /**
         * The password [text], or null when it is empty (no password slot); [iterations] is what sealing uses.
         * zh-CN: 口令 [text], 为空时返回 null (不写口令槽); [iterations] 为加密时使用的迭代次数.
         */
        @JvmStatic
        @JvmOverloads
        fun of(text: CharSequence, iterations: Int = ITERATIONS): SettingsPassword? {
            require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { "Iterations $iterations are out of $MIN_ITERATIONS..$MAX_ITERATIONS" }
            if (text.isEmpty()) return null
            return SettingsPassword(Normalizer.normalize(text, Normalizer.Form.NFC).toByteArray(Charsets.UTF_8), iterations)
        }

        /** PBKDF2 (RFC 8018) with HMAC-SHA256. zh-CN: 以 HMAC-SHA256 实现的 PBKDF2 (RFC 8018). */
        @JvmStatic
        fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
            require(password.isNotEmpty()) { "The password is empty" }
            require(iterations >= 1) { "Invalid PBKDF2 iterations $iterations" }
            require(length >= 1) { "Invalid PBKDF2 length $length" }
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(password, "HmacSHA256")) }
            val blocks = (length + HASH_BYTES - 1) / HASH_BYTES
            val out = ByteArray(blocks * HASH_BYTES)
            val u = ByteArray(HASH_BYTES)
            for (block in 1..blocks) {
                mac.update(salt)
                mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
                mac.doFinal(u, 0)
                val offset = (block - 1) * HASH_BYTES
                u.copyInto(out, offset)
                repeat(iterations - 1) {
                    mac.update(u)
                    mac.doFinal(u, 0)
                    for (i in 0 until HASH_BYTES) out[offset + i] = (out[offset + i].toInt() xor u[i].toInt()).toByte()
                }
            }
            u.fill(0)
            return out.copyOf(length).also { out.fill(0) }
        }
    }
}
