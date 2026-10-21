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
 * wrapped once per key slot: a device slot ([SettingsDeviceKey]) and, when the export is given a password, a password
 * slot ([SettingsPassword], P7.7) that another device opens with the password. Both slot kinds share the envelope;
 * readers try the device slot first and skip slot kinds they do not know. Pure JVM (R3).
 *
 * zh-CN: 敏感分类文件的加密形式 (ROADMAP D23 / T21, 附录 B): 分类文件文本以随机内容密钥加密 (AES-256-GCM, 分类 id 作附加数据), 内容密钥
 * 按每个密钥槽各包裹一次: 设备槽 ([SettingsDeviceKey]), 以及导出时设了口令才有的口令槽 ([SettingsPassword], P7.7), 其他设备输入口令即可
 * 打开. 两种槽共用同一信封; 读取方先试设备槽, 忽略不认识的槽种类. 纯 JVM (R3).
 */
object SettingsSealedFile {

    const val VERSION = 1

    const val CIPHER = "AES-256-GCM"

    /** The slot kind of [SettingsDeviceKey]. zh-CN: [SettingsDeviceKey] 的槽种类. */
    const val SLOT_DEVICE = "device"

    /** The slot kind of [SettingsPassword] (P7.7). zh-CN: [SettingsPassword] 的槽种类 (P7.7). */
    const val SLOT_PASSWORD = "password"

    /**
     * How a category file opens with a device key ([access]): the import wizard reads [PLAIN] and [DEVICE] files, asks
     * for the password of a [PASSWORD] one (P7.7) and disables a [LOCKED] one.
     * zh-CN: 分类文件能否以设备密钥打开 ([access]): 导入向导直接读取 [PLAIN] 与 [DEVICE], 对 [PASSWORD] 询问口令 (P7.7), 禁用 [LOCKED].
     */
    enum class Access {
        /** Not sealed. zh-CN: 未加密. */
        PLAIN,

        /** Sealed with a slot of the device key. zh-CN: 已加密且有该设备密钥的槽. */
        DEVICE,

        /** Sealed without a slot of the device key, but with a password slot. zh-CN: 已加密, 没有该设备密钥的槽, 但有口令槽. */
        PASSWORD,

        /** Sealed without a slot to open it with. zh-CN: 已加密且没有可用的槽. */
        LOCKED,
    }

    private const val KEY_BYTES = 32

    private const val NONCE_BYTES = 12

    private const val TAG_BITS = 128

    private val random by lazy { SecureRandom() }

    /** True when the parsed category file [json] is an envelope. zh-CN: 已解析的分类文件 [json] 为信封时为 true. */
    @JvmStatic
    fun isSealed(json: Any?): Boolean = (json as? Map<*, *>)?.get("sealed") != null

    /**
     * The envelope of [text], the category file of [id], with a device slot for [key] and a password slot for
     * [password]; at least one of them is given.
     * zh-CN: [id] 的分类文件 [text] 的信封, 带 [key] 的设备槽与 [password] 的口令槽; 两者至少给出一个.
     */
    @JvmStatic
    @JvmOverloads
    fun seal(id: String, text: String, key: SettingsDeviceKey?, password: SettingsPassword? = null): String {
        require(key != null || password != null) { "A sealed file needs a device key or a password" }
        val content = bytes(KEY_BYTES)
        try {
            val nonce = bytes(NONCE_BYTES)
            val data = gcm(Cipher.ENCRYPT_MODE, SecretKeySpec(content, "AES"), nonce, dataAad(id), text.toByteArray(Charsets.UTF_8))
            val slots = ArrayList<Map<String, Any>>()
            if (key != null) {
                val slotNonce = bytes(NONCE_BYTES)
                val wrapped = gcm(Cipher.ENCRYPT_MODE, key.secret(), slotNonce, slotAad(SLOT_DEVICE, id), content)
                slots += linkedMapOf(
                    "kind" to SLOT_DEVICE,
                    "kdf" to SettingsDeviceKey.KDF,
                    "fingerprint" to key.fingerprint,
                    "nonce" to Base64.encode(slotNonce),
                    "key" to Base64.encode(wrapped),
                )
            }
            if (password != null) {
                val salt = bytes(SettingsPassword.SALT_BYTES)
                val slotNonce = bytes(NONCE_BYTES)
                val wrapped = gcm(Cipher.ENCRYPT_MODE, password.secret(salt, password.iterations), slotNonce, slotAad(SLOT_PASSWORD, id), content)
                slots += linkedMapOf(
                    "kind" to SLOT_PASSWORD,
                    "kdf" to SettingsPassword.KDF,
                    "iterations" to password.iterations,
                    "salt" to Base64.encode(salt),
                    "nonce" to Base64.encode(slotNonce),
                    "key" to Base64.encode(wrapped),
                )
            }
            val sealed = linkedMapOf(
                "version" to VERSION,
                "cipher" to CIPHER,
                "nonce" to Base64.encode(nonce),
                "data" to Base64.encode(data),
                "slots" to slots,
            )
            return SettingsJson.stringify(linkedMapOf("id" to id, "sealed" to sealed), indent = 2)
        } finally {
            content.fill(0)
        }
    }

    /**
     * True when the envelope [json] has a device slot of [key], i.e. [open] with [key] does not fail with `LOCKED`.
     * zh-CN: 信封 [json] 含 [key] 的设备槽时为 true, 即以 [key] 调用 [open] 不会因 `LOCKED` 失败.
     */
    @JvmStatic
    fun canOpen(json: Any?, key: SettingsDeviceKey?): Boolean =
        key != null && slots(json).any { it["kind"] == SLOT_DEVICE && it["fingerprint"] == key.fingerprint }

    /**
     * True when the envelope [json] has a password slot of a known kdf (P7.7).
     * zh-CN: 信封 [json] 含已知 kdf 的口令槽时为 true (P7.7).
     */
    @JvmStatic
    fun hasPasswordSlot(json: Any?): Boolean = passwordSlots(json).isNotEmpty()

    /** How the category file [json] opens with [key]. zh-CN: 分类文件 [json] 能否以 [key] 打开. */
    @JvmStatic
    fun access(json: Any?, key: SettingsDeviceKey?): Access = when {
        !isSealed(json) -> Access.PLAIN
        canOpen(json, key) -> Access.DEVICE
        hasPasswordSlot(json) -> Access.PASSWORD
        else -> Access.LOCKED
    }

    /**
     * The category file text of the envelope [json] of [id], through the device slot of [key] or else a password slot
     * opened by [password]. Throws [SettingsArchiveException]: `LOCKED` without a slot of [key] and without a password
     * slot that [password] opens (another device, another signature, no key, no or a wrong password), `INVALID_FILE` for
     * a malformed or altered envelope. A password slot cannot tell a wrong password from an altered slot; both are
     * `LOCKED`, and the import wizard checks a password first ([SettingsArchiveReader.checkPassword]).
     * zh-CN: [id] 的信封 [json] 中的分类文件文本, 经 [key] 的设备槽, 否则经 [password] 能打开的口令槽. 抛 [SettingsArchiveException]:
     * 没有 [key] 的槽且没有 [password] 能打开的口令槽 (其他设备, 其他签名, 无密钥, 无口令或口令错误) 为 `LOCKED`, 信封格式错误或被改动为
     * `INVALID_FILE`. 口令槽无法区分口令错误与槽被改动, 两者都是 `LOCKED`; 导入向导先校验口令 ([SettingsArchiveReader.checkPassword]).
     */
    @JvmStatic
    @JvmOverloads
    fun open(id: String, json: Any?, key: SettingsDeviceKey?, password: SettingsPassword? = null): String {
        val root = json as? Map<*, *> ?: throw invalid(id, "not an envelope")
        if (root["id"] != id) throw invalid(id, "the envelope names the category ${root["id"]}")
        val sealed = root["sealed"] as? Map<*, *> ?: throw invalid(id, "no sealed object")
        if (SettingsJson.toInt(sealed["version"]) != VERSION) throw invalid(id, "sealing version ${sealed["version"]} is not $VERSION")
        if (sealed["cipher"] != CIPHER) throw invalid(id, "cipher ${sealed["cipher"]} is not $CIPHER")
        val nonce = decode(id, sealed["nonce"], "nonce").also { if (it.size != NONCE_BYTES) throw invalid(id, "nonce of ${it.size} bytes") }
        val data = decode(id, sealed["data"], "data")
        if (sealed["slots"] !is List<*>) throw invalid(id, "no slots")
        val content = contentKey(id, json, key, password)
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

    /** The content key, unwrapped by the device slot of [key] or else by a password slot. */
    private fun contentKey(id: String, json: Any?, key: SettingsDeviceKey?, password: SettingsPassword?): ByteArray {
        val device = key?.let { slots(json).firstOrNull { slot -> slot["kind"] == SLOT_DEVICE && slot["fingerprint"] == it.fingerprint } }
        if (key != null && device != null) {
            val slotNonce = decode(id, device["nonce"], "slot nonce")
            return try {
                gcm(Cipher.DECRYPT_MODE, key.secret(), slotNonce, slotAad(SLOT_DEVICE, id), decode(id, device["key"], "slot key"))
            } catch (e: GeneralSecurityException) {
                throw invalid(id, "the device slot does not open", e)
            }
        }
        val passwordSlots = passwordSlots(json)
        if (password != null && passwordSlots.isNotEmpty()) {
            passwordSlots.forEach { slot ->
                val iterations = SettingsJson.toInt(slot["iterations"])
                    ?.takeIf { it in SettingsPassword.MIN_ITERATIONS..SettingsPassword.MAX_ITERATIONS }
                    ?: throw invalid(id, "password slot iterations ${slot["iterations"]} out of range")
                val salt = decode(id, slot["salt"], "slot salt")
                    .also { if (it.size !in SettingsPassword.SALT_BYTES..SettingsPassword.MAX_SALT_BYTES) throw invalid(id, "salt of ${it.size} bytes") }
                val slotNonce = decode(id, slot["nonce"], "slot nonce")
                val wrapped = decode(id, slot["key"], "slot key")
                try {
                    return gcm(Cipher.DECRYPT_MODE, password.secret(salt, iterations), slotNonce, slotAad(SLOT_PASSWORD, id), wrapped)
                } catch (_: GeneralSecurityException) {
                    // A wrong password, or another password slot: try the next one.
                }
            }
            throw SettingsArchiveException(Reason.LOCKED, "${file(id)} does not open with the password given")
        }
        val message = when {
            passwordSlots.isNotEmpty() -> "is sealed for another device and needs its password"
            key == null -> "is sealed and no device key is given"
            else -> "is sealed for another device"
        }
        throw SettingsArchiveException(Reason.LOCKED, "${file(id)} $message")
    }

    private fun slots(json: Any?): List<Map<*, *>> =
        (((json as? Map<*, *>)?.get("sealed") as? Map<*, *>)?.get("slots") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

    private fun passwordSlots(json: Any?): List<Map<*, *>> = slots(json).filter { it["kind"] == SLOT_PASSWORD && it["kdf"] == SettingsPassword.KDF }

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
