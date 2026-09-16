package org.autojs.autojs.storage.history

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Extracts a trashed directory archive.
 *
 * - Every entry name is resolved against the destination's canonical path, so a crafted name
 *   (absolute, `..` segments, symlink tricks) can never write outside the destination; such entries
 *   are skipped and reported.
 * - A damaged entry (truncated deflate stream, bad CRC) is recorded and the extraction continues with
 *   the next entry instead of aborting the whole restore; half-written files are removed.
 * - Pure `java.util.zip`: unit-tested on the JVM. Cancellation is a caller-supplied [checkpoint] whose
 *   exception (a RuntimeException) propagates untouched.
 *
 * zh-CN: 解压回收站中的目录归档. 每个条目名都按目标目录的 canonical path 解析, 构造的条目 (绝对路径, `..`, 符号链接技巧)
 * 不可能写到目标目录之外, 这类条目跳过并记录; 损坏的条目 (deflate 流截断, CRC 错误) 记录后继续下一个而不是中止整个恢复,
 * 写了一半的文件会被删除. 纯 `java.util.zip`, 可在 JVM 单测. 取消由调用方传入的 [checkpoint] 负责, 其抛出的 RuntimeException 原样传播.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object TrashArchiveExtractor {

    /** Marker used in [Report.failed] when the remainder of the archive cannot be read at all. */
    const val UNREADABLE_REMAINDER: String = "(unreadable remainder)"

    data class Report(
        val restoredEntries: Int,
        val skippedUnsafe: List<String>,
        val failed: List<String>,
    ) {
        val hasFailures: Boolean get() = failed.isNotEmpty()
    }

    /**
     * Thrown by callers once extraction finished with [Report.hasFailures]; carries the report so the
     * UI can show counts.
     * zh-CN: 解压结束且 [Report.hasFailures] 时由调用方抛出; 携带报告以便 UI 显示数量.
     */
    class PartialFailure(val report: Report) : IOException(
        "Archive restored partially: ${report.failed.size} entries failed, ${report.skippedUnsafe.size} unsafe entries skipped"
    )

    /**
     * The file an archive entry maps to under [destDir], or null when the entry is blank, absolute,
     * contains a `..` segment, or does not resolve strictly inside the destination.
     * zh-CN: 条目在 [destDir] 下对应的文件; 条目为空, 绝对路径, 含 `..` 段, 或未严格落在目标目录内部时返回 null.
     */
    fun resolveEntry(destDir: File, entryName: String): File? {
        if (entryName.isBlank()) return null
        val normalized = entryName.replace('\\', '/')
        if (normalized.startsWith("/")) return null
        if (normalized.split('/').any { it == ".." }) return null
        val destCanonical = destDir.canonicalFile
        val target = File(destDir, normalized)
        val prefix = destCanonical.path.trimEnd(File.separatorChar) + File.separator
        if (!target.canonicalFile.path.startsWith(prefix)) return null
        return target
    }

    fun extract(zip: File, destDir: File, checkpoint: () -> Unit = {}): Report {
        destDir.mkdirs()
        var restored = 0
        val skipped = ArrayList<String>()
        val failed = ArrayList<String>()
        val buffer = ByteArray(256 * 1024)

        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                checkpoint()
                val entry = try {
                    zis.nextEntry ?: break
                } catch (e: IOException) {
                    // The local headers are unreadable from here on: nothing more can be salvaged.
                    // zh-CN: 从这里起本地头已不可读: 后续无法再挽回.
                    failed.add(UNREADABLE_REMAINDER)
                    break
                }
                val name = entry.name
                val target = resolveEntry(destDir, name)
                if (target == null) {
                    skipped.add(name)
                    if (!closeEntryOrFalse(zis)) {
                        failed.add(UNREADABLE_REMAINDER)
                        break
                    }
                    continue
                }
                try {
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        writeEntry(zis, target, buffer, checkpoint)
                    }
                    restored++
                } catch (e: IOException) {
                    failed.add(name)
                    if (!entry.isDirectory) {
                        // noinspection ResultOfMethodCallIgnored
                        target.delete()
                    }
                }
                if (!closeEntryOrFalse(zis)) {
                    // A truncated stream cannot be positioned on the next entry.
                    // zh-CN: 截断的流无法定位到下一个条目.
                    if (failed.lastOrNull() != name) failed.add(UNREADABLE_REMAINDER)
                    break
                }
            }
        }
        return Report(restored, skipped, failed)
    }

    private fun closeEntryOrFalse(zis: ZipInputStream): Boolean = try {
        zis.closeEntry()
        true
    } catch (e: IOException) {
        false
    }

    private fun writeEntry(input: InputStream, target: File, buffer: ByteArray, checkpoint: () -> Unit) {
        target.outputStream().use { os ->
            while (true) {
                checkpoint()
                val n = input.read(buffer)
                if (n < 0) break
                os.write(buffer, 0, n)
            }
            os.flush()
        }
    }
}
