package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingCategory
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a settings archive (docs/settings/ROADMAP.md T7, appendix B) with `java.util.zip`: `manifest.json` first, so
 * that a reader lists the categories without inflating anything else, then `categories/<id>.json` for every category
 * of the snapshot, `data/<file>` for every data file and `plugins/<packageName>/settings.json` for every plugin file
 * (D3, P13.1; none in the first phase, which has no plugin provider). The file of a sensitive category is sealed for the device key ([SettingsSealedFile], D23): there is no plain form any more, so a
 * snapshot with a sensitive category and no key is refused before anything is written. An archive larger than
 * [SettingsArchive.MAX_BYTES] is refused ([SettingsArchiveException.Reason.TOO_LARGE]); the caller then drops what it
 * has written. Pure JVM (R3); the stream is finished but not closed.
 *
 * zh-CN: 以 `java.util.zip` 写设置归档 (ROADMAP T7, 附录 B): 先写 `manifest.json`, 读取方无需解压其他文件即可列出分类; 然后为
 * 快照中每个分类写 `categories/<id>.json`, 为每个数据文件写 `data/<文件>`, 为每个插件文件写 `plugins/<包名>/settings.json` (D3,
 * P13.1; 第一期没有插件提供方, 因此没有). 敏感分类的文件以设备密钥加密
 * ([SettingsSealedFile], D23): 不再有明文形式, 因此含敏感分类而未给密钥的快照在写入任何内容之前即被拒绝. 超过
 * [SettingsArchive.MAX_BYTES] 的归档被拒绝 (`TOO_LARGE`), 调用方随后丢弃已写内容. 纯 JVM (R3); 只结束 zip, 不关闭流.
 */
object SettingsArchiveWriter {

    /**
     * The file of a data category (T19): [bytes] written as `data/<name>` (`shortcuts.zip`, `color-library.json`), with
     * the number of [entries] it holds.
     * zh-CN: 数据分类的文件 (T19): [bytes] 写为 `data/<name>`, [entries] 为其中的条目数.
     */
    class DataFile(val id: String, val entries: Int, val name: String, val bytes: ByteArray) {
        init {
            require(SettingsArchive.isCategoryId(id)) { "Invalid data category id \"$id\"" }
        }
    }

    /**
     * The settings of an official plugin (D3, P13.1): [text], the JSON document its provider exported (P13.2), written
     * as `plugins/<packageName>/settings.json`, with the number of [entries] it holds.
     * zh-CN: 一个官方插件的设置 (D3, P13.1): [text] 为其提供方导出的 JSON 文档 (P13.2), 写为 `plugins/<包名>/settings.json`,
     * [entries] 为其中的条目数.
     */
    class PluginFile(val packageName: String, val entries: Int, val text: String) {
        init {
            require(SettingsArchive.isPackageName(packageName)) { "Invalid plugin package name \"$packageName\"" }
            require(entries >= 0) { "A negative entry count of $packageName" }
        }
    }

    /**
     * Writes [snapshot], [data] and [plugins] to [out] and returns the manifest written, whose categories are those of
     * the snapshot in its order (sensitive as their [SettingCategory] declares, and sealed for [key]).
     * zh-CN: 把 [snapshot], [data] 与 [plugins] 写入 [out], 返回写入的 manifest; 其中的分类即快照中的分类, 顺序不变 (敏感标记取自
     * [SettingCategory] 的声明, 敏感分类以 [key] 加密).
     */
    @JvmStatic
    @JvmOverloads
    fun write(
        out: OutputStream,
        snapshot: SettingsSnapshot,
        exportedAt: String,
        host: SettingsArchiveManifest.Host,
        device: SettingsArchiveManifest.Device,
        data: List<DataFile> = emptyList(),
        maxBytes: Long = SettingsArchive.MAX_BYTES,
        key: SettingsDeviceKey? = null,
        plugins: List<PluginFile> = emptyList(),
    ): SettingsArchiveManifest {
        val sensitive = snapshot.categories.filter { isSensitive(it.id) }.map { it.id }
        require(sensitive.isEmpty() || key != null) { "The sensitive categories $sensitive need the device key (D23)" }
        require(plugins.distinctBy { it.packageName }.size == plugins.size) { "A plugin is given more than once" }
        val manifest = SettingsArchiveManifest(
            exportedAt = exportedAt,
            host = host,
            device = device,
            categories = snapshot.categories.map { SettingsArchiveManifest.CategoryEntry(it.id, it.entries, isSensitive(it.id)) },
            data = data.map { SettingsArchiveManifest.DataEntry(it.id, it.entries, SettingsArchive.dataFile(it.name)) },
            plugins = plugins.map { SettingsArchiveManifest.PluginEntry(it.packageName, it.entries) },
        )
        val zip = ZipOutputStream(LimitedOutputStream(out, maxBytes))
        zip.put(SettingsArchive.MANIFEST, manifest.toText().toByteArray(Charsets.UTF_8))
        snapshot.categories.forEach { category ->
            val text = SettingsSnapshotCodec.categoryText(category)
            val file = if (category.id in sensitive) SettingsSealedFile.seal(category.id, text, key!!) else text
            zip.put(SettingsArchive.categoryFile(category.id), file.toByteArray(Charsets.UTF_8))
        }
        data.forEach { zip.put(SettingsArchive.dataFile(it.name), it.bytes) }
        plugins.forEach { zip.put(SettingsArchive.pluginFile(it.packageName), it.text.toByteArray(Charsets.UTF_8)) }
        zip.finish()
        zip.flush()
        return manifest
    }

    private fun isSensitive(id: String) = SettingCategory.ofIdOrNull(id)?.sensitive == true

    private fun ZipOutputStream.put(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    /** Counts the bytes of the archive and refuses the one beyond [maxBytes]. */
    private class LimitedOutputStream(out: OutputStream, private val maxBytes: Long) : FilterOutputStream(out) {

        private var written = 0L

        override fun write(b: Int) {
            count(1)
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            count(len)
            out.write(b, off, len)
        }

        private fun count(len: Int) {
            written += len
            if (written > maxBytes) {
                throw SettingsArchiveException(SettingsArchiveException.Reason.TOO_LARGE, "The settings archive exceeds $maxBytes bytes")
            }
        }
    }
}
