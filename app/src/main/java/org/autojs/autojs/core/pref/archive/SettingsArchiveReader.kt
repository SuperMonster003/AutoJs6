package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingsJson
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Reads a settings archive (docs/settings/ROADMAP.md T7, appendix B) with `java.util.zip`, one file at a time: [open]
 * gives a new stream of the archive for every read (a document `Uri` in the app), so that [manifest] inflates only
 * `manifest.json` and the wizard opens a category file ([category]), a data file ([data]) or the settings of a plugin
 * ([plugin], D3) only once it is selected.
 * An archive of more than [maxBytes], or a file in it that inflates to more, is refused
 * ([SettingsArchiveException.Reason.TOO_LARGE]). An import (P7.1) checks the manifest ([SettingsArchiveManifest.fromJson],
 * [SettingsArchiveManifest.warnings]) and reads the selected categories through [categories], which applies the key
 * renames of [SettingsKeyMigrations]. A sealed category file (D23) is opened with [key], or else with [password]
 * through its password slot (P7.7); [access] and [isLocked] tell beforehand whether it can be, and [checkPassword]
 * tells a wrong password apart, so that the wizard asks again or skips the credentials. Pure JVM (R3).
 *
 * zh-CN: 以 `java.util.zip` 逐个文件读取设置归档 (ROADMAP T7, 附录 B): 每次读取都由 [open] 重新打开归档流 (应用中为文档 `Uri`),
 * 因此 [manifest] 只解压 `manifest.json`, 向导在勾选后才打开分类文件 ([category]), 数据文件 ([data]) 或插件的设置 ([plugin], D3).
 * 归档超过 [maxBytes]
 * 或其中文件解压后超过该值时拒绝 (`TOO_LARGE`). 导入 (P7.1) 校验 manifest ([SettingsArchiveManifest.fromJson],
 * [SettingsArchiveManifest.warnings]), 并经 [categories] 读取勾选的分类, 其中应用 [SettingsKeyMigrations] 的键改名. 加密的分类文件
 * (D23) 以 [key] 打开, 否则以 [password] 经其口令槽打开 (P7.7); [access] 与 [isLocked] 可事先判断能否打开, [checkPassword] 区分口令
 * 错误, 供向导重新询问或跳过凭据. 纯 JVM (R3).
 */
class SettingsArchiveReader @JvmOverloads constructor(
    private val open: () -> InputStream,
    private val maxBytes: Long = SettingsArchive.MAX_BYTES,
    private val key: SettingsDeviceKey? = null,
    private val password: SettingsPassword? = null,
) {

    /**
     * This reader with [password] for the password slots (P7.7), once [checkPassword] accepted it.
     * zh-CN: 带口令槽口令 [password] 的同一读取方 (P7.7), 在 [checkPassword] 通过后使用.
     */
    fun withPassword(password: SettingsPassword?) = SettingsArchiveReader(open, maxBytes, key, password)

    /** `manifest.json`; throws [SettingsArchiveException]. zh-CN: 读取 `manifest.json`; 失败时抛 [SettingsArchiveException]. */
    fun manifest(): SettingsArchiveManifest = SettingsArchiveManifest.fromText(text(SettingsArchive.MANIFEST))

    /**
     * The category file of [id]: its values, and the entries left out (D9); throws [SettingsArchiveException] when the
     * file is missing, malformed or names another category, and `LOCKED` when it is sealed for another key (D23).
     * zh-CN: [id] 的分类文件: 其中的值与被略去的条目 (D9); 文件缺失, 格式错误或属于其他分类时抛 [SettingsArchiveException], 以其他密钥
     * 加密时为 `LOCKED` (D23).
     */
    fun category(id: String): SettingsSnapshotCodec.Decoded {
        val file = SettingsArchive.categoryFile(id)
        val decoded = try {
            val json = SettingsJson.parse(text(file))
            SettingsSnapshotCodec.decodeCategory(if (SettingsSealedFile.isSealed(json)) SettingsJson.parse(SettingsSealedFile.open(id, json, key, password)) else json)
        } catch (e: SettingsJson.SettingsJsonException) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_FILE, "$file: ${e.message}", e)
        }
        if (decoded.category.id != id) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_FILE, "$file holds the category ${decoded.category.id}")
        }
        return decoded
    }

    /**
     * True when the category file of [id] is sealed without a slot of [key], and without a password slot or without a
     * [password] for it, so that [category] fails with `LOCKED` (the import wizard disables such a category, P7.5);
     * with a password, [category] still fails with `LOCKED` when it is wrong ([checkPassword]). Throws
     * [SettingsArchiveException] when the file is missing or malformed.
     * zh-CN: [id] 的分类文件已加密且没有 [key] 的槽, 也没有口令槽或未给 [password], 即 [category] 会因 `LOCKED` 失败时为 true (导入向导
     * 禁用该分类, P7.5); 给了口令而口令错误时 [category] 同样为 `LOCKED` ([checkPassword]). 文件缺失或格式错误时抛
     * [SettingsArchiveException].
     */
    fun isLocked(id: String): Boolean = when (access(id)) {
        SettingsSealedFile.Access.LOCKED -> true
        SettingsSealedFile.Access.PASSWORD -> password == null
        else -> false
    }

    /**
     * How the category file of [id] opens with [key] (P7.7): the import wizard asks for the password of a
     * [SettingsSealedFile.Access.PASSWORD] file; throws [SettingsArchiveException] when the file is missing or malformed.
     * zh-CN: [id] 的分类文件能否以 [key] 打开 (P7.7): 对 [SettingsSealedFile.Access.PASSWORD] 的文件导入向导询问口令; 文件缺失或格式错误时
     * 抛 [SettingsArchiveException].
     */
    fun access(id: String): SettingsSealedFile.Access = SettingsSealedFile.access(json(id), key)

    /**
     * True when [password] opens a password slot of the sealed category file of [id], false when it does not (a wrong
     * password: ask again or skip the category) or the file has no password slot (P7.7). Deriving the slot key takes a
     * moment (PBKDF2), so call it off the main thread; a reader [withPassword] of the same [password] does not derive
     * it again. Throws [SettingsArchiveException] when the file is missing, malformed or altered.
     * zh-CN: [password] 能打开 [id] 的加密分类文件的口令槽时为 true, 打不开 (口令错误: 重新询问或跳过该分类) 或文件没有口令槽时为 false
     * (P7.7). 派生槽密钥 (PBKDF2) 需要一些时间, 因此在后台线程调用; 以同一 [password] 经 [withPassword] 得到的读取方不再重复派生. 文件
     * 缺失, 格式错误或被改动时抛 [SettingsArchiveException].
     */
    fun checkPassword(id: String, password: SettingsPassword): Boolean {
        val json = json(id)
        if (!SettingsSealedFile.hasPasswordSlot(json)) return false
        return try {
            SettingsSealedFile.open(id, json, null, password)
            true
        } catch (e: SettingsArchiveException) {
            if (e.reason != SettingsArchiveException.Reason.LOCKED) throw e
            false
        }
    }

    private fun json(id: String): Any? {
        val file = SettingsArchive.categoryFile(id)
        return try {
            SettingsJson.parse(text(file))
        } catch (e: SettingsJson.SettingsJsonException) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_FILE, "$file: ${e.message}", e)
        }
    }

    /**
     * The category files of [ids] as an import reads them (P7.1): each decoded by [category] and renamed by
     * [SettingsKeyMigrations] for the host version of [manifest]; throws [SettingsArchiveException] like [category].
     * zh-CN: 导入时读取的 [ids] 的分类文件 (P7.1): 逐个经 [category] 解码, 再按 [manifest] 的宿主版本经 [SettingsKeyMigrations]
     * 改名; 失败时与 [category] 一样抛 [SettingsArchiveException].
     */
    @JvmOverloads
    fun categories(
        ids: Collection<String>,
        manifest: SettingsArchiveManifest = manifest(),
        renames: List<SettingsKeyMigrations.Rename> = SettingsKeyMigrations.RENAMES,
    ): List<SettingsSnapshotCodec.Decoded> = ids.distinct().map { SettingsKeyMigrations.migrate(category(it), manifest.host.versionCode, renames) }

    /** The bytes of the data file [file] (a `file` of the manifest's `data`). zh-CN: 数据文件 [file] 的内容. */
    fun data(file: String): ByteArray {
        require(file.startsWith(SettingsArchive.DATA_DIR)) { "Not a data file: $file" }
        return bytes(file)
    }

    /**
     * The settings of the plugin [packageName] (an `id` of the manifest's `plugins`, D3, P13.1): the text of
     * `plugins/<packageName>/settings.json`, which its provider imports (P13.2); throws [SettingsArchiveException] when
     * the file is missing or too large.
     * zh-CN: 插件 [packageName] 的设置 (manifest `plugins` 中的 `id`, D3, P13.1): `plugins/<包名>/settings.json` 的文本, 由其提供方导入
     * (P13.2); 文件缺失或过大时抛 [SettingsArchiveException].
     */
    fun plugin(packageName: String): String = text(SettingsArchive.pluginFile(packageName))

    private fun text(name: String): String = bytes(name).toString(Charsets.UTF_8)

    private fun bytes(name: String): ByteArray {
        try {
            open().use { raw ->
                ZipInputStream(LimitedInputStream(raw, maxBytes)).use { zip ->
                    var files = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        files++
                        if (!entry.isDirectory && entry.name == name) return readLimited(zip, name)
                    }
                    if (files == 0) throw SettingsArchiveException(SettingsArchiveException.Reason.NOT_AN_ARCHIVE, "Not a settings archive")
                    throw SettingsArchiveException(SettingsArchiveException.Reason.MISSING_FILE, "The settings archive has no $name")
                }
            }
        } catch (e: ZipException) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.NOT_AN_ARCHIVE, "Not a settings archive: ${e.message}", e)
        } catch (e: EOFException) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.NOT_AN_ARCHIVE, "The settings archive is truncated", e)
        }
    }

    private fun readLimited(zip: ZipInputStream, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > maxBytes) throw SettingsArchiveException(SettingsArchiveException.Reason.TOO_LARGE, "$name inflates to more than $maxBytes bytes")
            out.write(buffer, 0, n)
        }
    }

    /** Refuses an archive of more than [maxBytes]. */
    private class LimitedInputStream(input: InputStream, private val maxBytes: Long) : FilterInputStream(input) {

        private var read = 0L

        override fun read(): Int = super.read().also { if (it >= 0) count(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count(it) }

        override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) count(it) }

        private fun count(n: Number) {
            read += n.toLong()
            if (read > maxBytes) throw SettingsArchiveException(SettingsArchiveException.Reason.TOO_LARGE, "The settings archive exceeds $maxBytes bytes")
        }
    }
}
