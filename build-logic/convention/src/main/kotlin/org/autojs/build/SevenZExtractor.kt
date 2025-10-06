package org.autojs.build

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object SevenZExtractor {

    @JvmStatic
    fun extractDirectoryFrom7z(
        archive: File,
        sourceDir: String,
        outDir: File,
        shouldPrintProgress: Boolean = true
    ): Long {
        require(archive.isFile) { "7z archive not found: ${archive.absolutePath}" }
        if (!outDir.exists()) outDir.mkdirs()

        val sourceDirPath = normalizePrefix(sourceDir)
        var sevenZFile: SevenZFile? = null

        var totalBytes: Long
        var writtenBytes = 0L
        val buffer = ByteArray(64 * 1024)

        try {
            sevenZFile = SevenZFile.Builder().setFile(archive).get()
            val allEntries: Iterable<SevenZArchiveEntry> = sevenZFile.entries

            val targetEntries = allEntries.filter { e ->
                val entryPath = e.name.replace('\\', '/')
                entryPath.startsWith(sourceDirPath) ||
                        entryPath.startsWith(trimLeadingSlash(sourceDirPath))
            }

            totalBytes = targetEntries
                .filter { !it.isDirectory && it.size >= 0 && it.hasStream() }
                .sumOf { it.size }

            val entriesCount = targetEntries.size
            var processed = 0

            for (entry in targetEntries) {
                val entryPath = entry.name.replace('\\', '/')
                var relative = when {
                    entryPath.startsWith(sourceDirPath) ->
                        entryPath.substring(sourceDirPath.length)
                    entryPath.startsWith(trimLeadingSlash(sourceDirPath)) ->
                        entryPath.substring(trimLeadingSlash(sourceDirPath).length)
                    else -> {
                        processed++
                        continue
                    }
                }

                // Remove leading separator to avoid being treated as absolute path.
                // zh-CN: 去掉前导分隔符, 避免被当作绝对路径.
                while (relative.startsWith("/") || relative.startsWith("\\")) {
                    relative = relative.substring(1)
                }
                if (relative.isEmpty()) {
                    processed++
                    continue
                }

                val outFile = File(outDir, relative)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    if (!entry.hasStream()) {
                        processed++
                        continue
                    }
                    outFile.parentFile?.mkdirs()
                    var ins: InputStream? = null
                    var bos: BufferedOutputStream? = null
                    try {
                        ins = sevenZFile.getInputStream(entry)
                        bos = BufferedOutputStream(FileOutputStream(outFile))
                        while (true) {
                            val read = ins.read(buffer)
                            if (read == -1) break
                            bos.write(buffer, 0, read)
                            if (shouldPrintProgress && totalBytes > 0) {
                                writtenBytes += read
                                printProgress(writtenBytes, totalBytes)
                            }
                        }
                        bos.flush()
                        if (entry.size >= 0 && outFile.length() != entry.size) {
                            throw IllegalStateException(
                                "Extracted file size mismatch for: ${entry.name}, expected=${entry.size}, actual=${outFile.length()}"
                            )
                        }
                    } finally {
                        try { bos?.close() } catch (_: Throwable) {}
                        try { ins?.close() } catch (_: Throwable) {}
                    }
                }

                if (shouldPrintProgress && totalBytes == 0L) {
                    val pct = processed * 100.0 / entriesCount
                    printProgress(pct)
                }
                processed++
            }
            return totalBytes
        } finally {
            try { sevenZFile?.close() } catch (_: Throwable) {}
        }
    }

    private fun normalizePrefix(path: String): String {
        var p = File(path).path.replace('\\', '/')
        if (p.startsWith("/")) p = p.substring(1)
        if (!p.endsWith("/")) p += "/"
        return p
    }

    private fun trimLeadingSlash(s: String): String =
        if (s.startsWith("/")) s.substring(1) else s

    private fun printProgress(written: Long, total: Long) {
        val pct = if (total > 0) written * 100.0 / total else 0.0
        printProgress(pct)
    }

    private fun printProgress(percent: Double) {
        val width = 30
        val filled = ((percent / 100.0) * width).toInt().coerceIn(0, width)
        val bar = buildString {
            append("[").append("#".repeat(filled)).append("-".repeat(width - filled)).append("]")
        }
        print("\rExtracting... $bar ${"%.2f".format(percent)}%")
        System.out.flush()
    }

}
