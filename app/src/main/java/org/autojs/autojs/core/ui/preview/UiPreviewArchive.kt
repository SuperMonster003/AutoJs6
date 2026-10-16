package org.autojs.autojs.core.ui.preview

import android.os.ParcelFileDescriptor
import org.autojs.plugin.ui.preview.UiPreviewContract
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * The project and resources zips of preview contract v3 (host roadmap `docs/dev/android-xml-attributes-roadmap.md`
 * R2, 3-Block Compose P11.H1): read from the client's read-only descriptor within [UiPreviewContract.MAX_ARCHIVE_BYTES]
 * and checked against their digest, then unpacked entry by entry into a directory of the session. Only regular files
 * are written. A name that [UiPreviewRequests.archivePath] rejects (absolute, a `..` or empty segment, a backslash, a
 * drive letter, too long), a repeated name, more entries or more unpacked bytes than the contract allows (counted
 * while writing, never taken from the headers) reject the whole zip with [UiPreviewContract.ERROR_ARCHIVE_INVALID]
 * and leave nothing behind.
 *
 * zh-CN: 预览契约 v3 的工程 / 资源 zip (宿主路线图 R2): 在上限内从客户端的只读描述符读取并核对摘要, 再逐条解压到会话目录.
 * 只写普通文件; 名称不安全, 名称重复, 条目数或解压字节数超限 (边写边计, 不信任头部声明) 时整个 zip 以 ARCHIVE_INVALID 拒绝且不留残余.
 */
internal object UiPreviewArchive {

    /**
     * Copies the zip behind [fd] (closed afterwards) into [scratch], checks its lowercase SHA-256 against
     * [expectedSha256] and unpacks it into [target], which must not exist yet. [scratch] is removed in any case.
     */
    @Throws(UiPreviewRequestException::class)
    fun receive(fd: ParcelFileDescriptor, expectedSha256: String, scratch: File, target: File) {
        try {
            scratch.parentFile?.mkdirs()
            val digest = ParcelFileDescriptor.AutoCloseInputStream(fd).use { input ->
                scratch.outputStream().use { output -> copyLimited(input, output, UiPreviewContract.MAX_ARCHIVE_BYTES) }
            }
            if (digest != expectedSha256) throw invalid("the zip does not match its SHA-256 ($digest)")
            scratch.inputStream().buffered().use { unpack(it, target) }
        } catch (e: IOException) {
            throw invalid("the zip cannot be read: ${e.message}")
        } finally {
            scratch.delete()
        }
    }

    /**
     * Unpacks [input] into [target] (created here; it must not exist). On any rejection the partly written
     * directory is removed before the exception leaves.
     */
    @Throws(UiPreviewRequestException::class, IOException::class)
    fun unpack(
        input: InputStream,
        target: File,
        maxEntries: Int = UiPreviewContract.MAX_ARCHIVE_ENTRIES,
        maxUnpackedBytes: Long = UiPreviewContract.MAX_ARCHIVE_UNPACKED_BYTES,
    ) {
        if (target.exists()) throw IOException("$target already exists")
        val staging = File(target.parentFile, target.name + ".partial")
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("cannot create $staging")
        try {
            var entries = 0
            var unpacked = 0L
            val names = HashSet<String>()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = try {
                        zip.nextEntry
                    } catch (e: ZipException) {
                        throw invalid("the zip is malformed: ${e.message}")
                    } ?: break
                    if (++entries > maxEntries) throw invalid("the zip has more than $maxEntries entries")
                    val path = UiPreviewRequests.archivePath(entry.name.removeSuffix("/"), "zip entry", UiPreviewContract.ERROR_ARCHIVE_INVALID)
                    if (!names.add(path)) throw invalid("the zip names $path twice")
                    val file = File(staging, path)
                    if (entry.isDirectory) {
                        file.mkdirs()
                        continue
                    }
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            unpacked += read
                            if (unpacked > maxUnpackedBytes) throw invalid("the zip unpacks to more than $maxUnpackedBytes bytes")
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (!staging.renameTo(target)) throw IOException("cannot move $staging to $target")
        } catch (e: Throwable) {
            staging.deleteRecursively()
            throw e
        }
    }

    /** Copies at most [limit] bytes and returns their lowercase SHA-256; one byte more rejects the zip. */
    private fun copyLimited(input: InputStream, output: java.io.OutputStream, limit: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw invalid("the zip is larger than $limit bytes")
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun invalid(message: String) = UiPreviewRequestException(UiPreviewContract.ERROR_ARCHIVE_INVALID, message)

    private const val BUFFER_BYTES = 64 * 1024
}
