package org.autojs.autojs.core.terminal

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Streams the plugin's npm / corepack archive into a directory while verifying its SHA-256.
 *
 * Only entries below [Request.root] are written; each one is checked against path traversal and
 * counted against the declared entry / byte limits. The digest covers the whole asset, so the
 * caller must treat the destination as unusable until [extract] has returned normally.
 *
 * zh-CN: 边校验 SHA-256 边把插件的 npm / corepack 资产解压到目录. 只写入 [Request.root] 下的条目,
 * 每个条目都做路径穿越检查并计入声明的条目数 / 字节上限. 摘要覆盖整个资产, 因此在 [extract] 正常返回前,
 * 调用方不得使用目标目录.
 */
object NodeCliArchive {

    /** Upper bounds applied even when a plugin declares larger numbers. zh-CN: 即使插件声明更大数值也生效的硬上限. */
    const val ABSOLUTE_MAX_ENTRIES = 50_000
    const val ABSOLUTE_MAX_BYTES = 256L * 1024 * 1024
    const val DEFAULT_MAX_ENTRIES = 20_000
    const val DEFAULT_MAX_BYTES = 64L * 1024 * 1024

    class ArchiveException(message: String) : IOException(message)

    /**
     * @param root       directory inside the archive whose contents are extracted (no trailing slash)
     * @param maxEntries maximum number of file entries accepted below [root]
     * @param maxBytes   maximum total uncompressed bytes accepted below [root]
     */
    data class Request(
        val expectedSha256: String,
        val root: String,
        val destination: File,
        val maxEntries: Int = DEFAULT_MAX_ENTRIES,
        val maxBytes: Long = DEFAULT_MAX_BYTES,
    )

    data class Result(val entries: Int, val bytes: Long, val skipped: Int, val sha256: String)

    /**
     * Derives extraction limits from what the plugin declared, clamped to the absolute bounds.
     * zh-CN: 由插件声明推导解压上限, 并夹到硬上限内.
     */
    @JvmStatic
    fun limitsFor(declaredEntries: Int?, declaredBytes: Long?): Pair<Int, Long> {
        val entries = (declaredEntries ?: DEFAULT_MAX_ENTRIES).coerceIn(1, ABSOLUTE_MAX_ENTRIES)
        val bytes = (declaredBytes ?: DEFAULT_MAX_BYTES).coerceIn(1L, ABSOLUTE_MAX_BYTES)
        return entries to bytes
    }

    /**
     * @throws ArchiveException on digest mismatch, unsafe entries or exceeded limits; the
     *   destination may then contain partial files and must be discarded by the caller
     */
    @JvmStatic
    @Throws(IOException::class)
    fun extract(input: InputStream, request: Request): Result {
        val rootPrefix = request.root.trimEnd('/') + "/"
        val destination = request.destination
        if (!destination.isDirectory && !destination.mkdirs()) {
            throw ArchiveException("cannot create ${destination.path}")
        }
        val destinationPrefix = destination.canonicalPath + File.separator
        val digest = MessageDigest.getInstance("SHA-256")
        val digesting = DigestInputStream(input, digest)
        var entries = 0
        var skipped = 0
        var bytes = 0L
        val buffer = ByteArray(64 * 1024)
        ZipInputStream(digesting).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory) continue
                if (!name.startsWith(rootPrefix)) {
                    skipped++
                    continue
                }
                val relative = name.substring(rootPrefix.length)
                val segments = relative.split('/')
                if (relative.isEmpty() || '\\' in relative || segments.any { it.isEmpty() || it == "." || it == ".." }) {
                    throw ArchiveException("unsafe archive entry: $name")
                }
                if (++entries > request.maxEntries) {
                    throw ArchiveException("archive has more than ${request.maxEntries} entries")
                }
                val target = File(destination, relative)
                if (!target.canonicalPath.startsWith(destinationPrefix)) {
                    throw ArchiveException("archive entry escapes the destination: $name")
                }
                target.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw ArchiveException("cannot create ${it.path}") }
                target.outputStream().use { out ->
                    while (true) {
                        val read = zip.read(buffer)
                        if (read < 0) break
                        bytes += read
                        if (bytes > request.maxBytes) {
                            throw ArchiveException("archive exceeds ${request.maxBytes} bytes")
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            // ZipInputStream stops at the central directory; the digest must cover the whole asset.
            while (digesting.read(buffer) >= 0) {
                // drain
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (!actual.equals(request.expectedSha256, ignoreCase = true)) {
            throw ArchiveException("archive digest mismatch: expected ${request.expectedSha256}, actual $actual")
        }
        if (entries == 0) {
            throw ArchiveException("archive has no entries below $rootPrefix")
        }
        return Result(entries, bytes, skipped, actual)
    }

}
