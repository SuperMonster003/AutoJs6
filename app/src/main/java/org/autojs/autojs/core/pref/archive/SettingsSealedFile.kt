package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.archive.SettingsArchiveException.Reason
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/**
 * The sealed form of a sensitive category file (docs/settings/ROADMAP.md D23 / T21, appendix B): the category file
 * text encrypted with a random content key (AES-256-GCM, the category id as associated data), and the content key
 * wrapped once per key slot. The first phase writes one device slot ([SettingsDeviceKey]); the password slot of P7.7
 * adds a slot kind without changing the envelope, and readers skip slot kinds they do not know. Pure JVM (R3).
 *
 * zh-CN: 敏感分类文件的加密形式 (ROADMAP D23 / T21, 附录 B): 分类文件文本以随机内容密钥加密 (AES-256-GCM, 分类 id 作附加数据), 内容密钥
 * 按每个密钥槽各包裹一次. 第一期只写一个设备槽 ([SettingsDeviceKey]); P7.7 的口令槽只增加槽种类而不改信封, 读取方忽略不认识的槽种类. 纯 JVM (R3).
 */
object SettingsSealedFile {

    const val VERSION = 1

    const val CIPHER = "AES-256-GCM"

    /** The slot kind of [SettingsDeviceKey]. zh-CN: [SettingsDeviceKey] 的槽种类. */
    const val SLOT_DEVICE = "device"

    private const val KEY_BYTES = 32

    private const val NONCE_BYTES = 12

    private const val TAG_BITS = 128

    private val random by lazy { SecureRandom() }

    /** True when the parsed category file [json] is an envelope. zh-CN: 已解析的分类文件 [json] 为信封时为 true. */
    @JvmStatic
    fun isSealed(json: Any?): Boolean = (json as? Map<*, *>)?.get("sealed") != null

    /**
     * The envelope of [text], the category file of [id], with a device slot for [key].
     * zh-CN: [id] 的分类文件 [text] 的信封, 带 [key] 的设备槽.
     */
    @JvmStatic
    fun seal(id: String, text: String, key: SettingsDeviceKey): String {
        val content = bytes(KEY_BYTES)
        try {
            val nonce = bytes(NONCE_BYTES)
            val data = gcm(Cipher.ENCRYPT_MODE, SecretKeySpec(content, "AES"), nonce, dataAad(id), text.toByteArray(Charsets.UTF_8))
            val slotNonce = bytes(NONCE_BYTES)
            val wrapped = gcm(Cipher.ENCRYPT_MODE, key.secret(), slotNonce, slotAad(SLOT_DEVICE, id), content)
            val slot = linkedMapOf(
                "kind" to SLOT_DEVICE,
                "kdf" to SettingsDeviceKey.KDF,
                "fingerprint" to key.fingerprint,
                "nonce" to Base64.encode(slotNonce),
                "key" to Base64.encode(wrapped),
            )
            val sealed = linkedMapOf(
                "version" to VERSION,
                "cipher" to CIPHER,
                "nonce" to Base64.encode(nonce),
                "data" to Base64.encode(data),
                "slots" to listOf(slot),
            )
            return SettingsJson.stringify(linkedMapOf("id" to id, "sealed" to sealed), indent = 2)
        } finally {
            content.fill(0)
        }
    }

    /**
     * True when the envelope [json] has a device slot of [key], i.e. [open] does not fail with `LOCKED`.
     * zh-CN: 信封 [json] 含 [key] 的设备槽时为 true, 即 [open] 不会因 `LOCKED` 失败.
     */
    @JvmStatic
    fun canOpen(json: Any?, key: SettingsDeviceKey?): Boolean =
        key != null && slots(json).any { it["kind"] == SLOT_DEVICE && it["fingerprint"] == key.fingerprint }

    /**
     * The category file text of the envelope [json] of [id]. Throws [SettingsArchiveException]: `LOCKED` without a slot
     * of [key] (another device, another signature, no key), `INVALID_FILE` for a malformed or altered envelope.
     * zh-CN: [id] 的信封 [json] 中的分类文件文本. 抛 [SettingsArchiveException]: 没有 [key] 的槽 (其他设备, 其他签名, 无密钥) 为
     * `LOCKED`, 信封格式错误或被改动为 `INVALID_FILE`.
     */
    @JvmStatic
    fun open(id: String, json: Any?, key: SettingsDeviceKey?): String {
        val root = json as? Map<*, *> ?: throw invalid(id, "not an envelope")
        if (root["id"] != id) throw invalid(id, "the envelope names the category ${root["id"]}")
        val sealed = root["sealed"] as? Map<*, *> ?: throw invalid(id, "no sealed object")
        if (SettingsJson.toInt(sealed["version"]) != VERSION) throw invalid(id, "sealing version ${sealed["version"]} is not $VERSION")
        if (sealed["cipher"] != CIPHER) throw invalid(id, "cipher ${sealed["cipher"]} is not $CIPHER")
        val nonce = decode(id, sealed["nonce"], "nonce").also { if (it.size != NONCE_BYTES) throw invalid(id, "nonce of ${it.size} bytes") }
        val data = decode(id, sealed["data"], "data")
        if (sealed["slots"] !is List<*>) throw invalid(id, "no slots")
        if (key == null) throw SettingsArchiveException(Reason.LOCKED, "${file(id)} is sealed and no device key is given")
        val slot = slots(json).firstOrNull { it["kind"] == SLOT_DEVICE && it["fingerprint"] == key.fingerprint }
            ?: throw SettingsArchiveException(Reason.LOCKED, "${file(id)} is sealed for another device")
        val slotNonce = decode(id, slot["nonce"], "slot nonce")
        val content = try {
            gcm(Cipher.DECRYPT_MODE, key.secret(), slotNonce, slotAad(SLOT_DEVICE, id), decode(id, slot["key"], "slot key"))
        } catch (e: GeneralSecurityException) {
            throw invalid(id, "the device slot does not open", e)
        }
        try {
            if (content.size != KEY_BYTES) throw invalid(id, "content key of ${content.size} bytes")
            val plain = try {
                gcm(Cipher.DECRYPT_MODE, SecretKeySpec(content, "AES"), nonce, dataAad(id), data)
            } catch (e: GeneralSecurityException) {
                throw invalid(id, "the data does not decrypt", e)
            }
            return plain.toString(Charsets.UTF_8)
        } finally {
            content.fill(0)
        }
    }

    private fun slots(json: Any?): List<Map<*, *>> =
        (((json as? Map<*, *>)?.get("sealed") as? Map<*, *>)?.get("slots") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

    private fun gcm(mode: Int, key: SecretKeySpec, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        if (mode == Cipher.DECRYPT_MODE && (nonce.size != NONCE_BYTES || input.size < TAG_BITS / 8)) throw AEADBadTagException("Truncated input")
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad)
            doFinal(input)
        }
    }

    private fun dataAad(id: String) = "autojs6-settings:$id".toByteArray(Charsets.UTF_8)

    private fun slotAad(kind: String, id: String) = "autojs6-settings-slot:$kind:$id".toByteArray(Charsets.UTF_8)

    private fun bytes(size: Int) = ByteArray(size).also(random::nextBytes)

    private fun decode(id: String, value: Any?, name: String): ByteArray {
        val text = value as? String ?: throw invalid(id, "no $name")
        return try {
            Base64.decode(text)
        } catch (e: IllegalArgumentException) {
            throw invalid(id, "$name is not Base64", e)
        }
    }

    private fun file(id: String) = SettingsArchive.categoryFile(id)

    private fun invalid(id: String, message: String, cause: Throwable? = null) =
        SettingsArchiveException(Reason.INVALID_FILE, "${file(id)}: $message", cause)
}
