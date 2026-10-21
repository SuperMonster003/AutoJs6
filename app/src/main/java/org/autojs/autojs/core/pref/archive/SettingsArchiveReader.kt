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
 * `manifest.json` and the wizard opens a category file ([category]) or a data file ([data]) only once it is selected.
 * An archive of more than [maxBytes], or a file in it that inflates to more, is refused
 * ([SettingsArchiveException.Reason.TOO_LARGE]). Pure JVM (R3).
 *
 * zh-CN: 以 `java.util.zip` 逐个文件读取设置归档 (ROADMAP T7, 附录 B): 每次读取都由 [open] 重新打开归档流 (应用中为文档 `Uri`),
 * 因此 [manifest] 只解压 `manifest.json`, 向导在勾选后才打开分类文件 ([category]) 或数据文件 ([data]). 归档超过 [maxBytes]
 * 或其中文件解压后超过该值时拒绝 (`TOO_LARGE`). 纯 JVM (R3).
 */
class SettingsArchiveReader @JvmOverloads constructor(
    private val open: () -> InputStream,
    private val maxBytes: Long = SettingsArchive.MAX_BYTES,
) {

    /** `manifest.json`; throws [SettingsArchiveException]. zh-CN: 读取 `manifest.json`; 失败时抛 [SettingsArchiveException]. */
    fun manifest(): SettingsArchiveManifest = SettingsArchiveManifest.fromText(text(SettingsArchive.MANIFEST))

    /**
     * The category file of [id]: its values, and the entries left out (D9); throws [SettingsArchiveException] when the
     * file is missing, malformed or names another category.
     * zh-CN: [id] 的分类文件: 其中的值与被略去的条目 (D9); 文件缺失, 格式错误或属于其他分类时抛 [SettingsArchiveException].
     */
    fun category(id: String): SettingsSnapshotCodec.Decoded {
        val file = SettingsArchive.categoryFile(id)
        val decoded = try {
            SettingsSnapshotCodec.decodeCategoryText(text(file))
        } catch (e: SettingsJson.SettingsJsonException) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_FILE, "$file: ${e.message}", e)
        }
        if (decoded.category.id != id) {
            throw SettingsArchiveException(SettingsArchiveException.Reason.INVALID_FILE, "$file holds the category ${decoded.category.id}")
        }
        return decoded
    }

    /** The bytes of the data file [file] (a `file` of the manifest's `data`). zh-CN: 数据文件 [file] 的内容. */
    fun data(file: String): ByteArray {
        require(file.startsWith(SettingsArchive.DATA_DIR)) { "Not a data file: $file" }
        return bytes(file)
    }

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
