package org.autojs.autojs.core.console.log

import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer

/** A log sink. zh-CN: 日志输出目标. */
interface Appender {
    fun append(event: LogEvent)
    fun close()
}

/**
 * Size-based rolling file sink with log4j 1.2 `RollingFileAppender` semantics: the file is opened in append mode,
 * parent directories are created, and once the byte count reaches [maximumFileSize] after a write the file is
 * rolled: `file.<maxBackupIndex>` is deleted, `file.<i>` becomes `file.<i+1>`, the file becomes `file.1` and a new
 * file starts; with [maxBackupIndex] 0 the file is truncated instead. [immediateFlush] false buffers writes
 * (8 KiB) until flush, roll-over or close.
 *
 * zh-CN: 按大小分卷的文件输出, 语义同 log4j 1.2 `RollingFileAppender`: 追加模式打开, 自动创建父目录, 某次写入后字节数达到
 * [maximumFileSize] 即分卷: 删除 `file.<maxBackupIndex>`, `file.<i>` 改名为 `file.<i+1>`, 当前文件改名为 `file.1` 并新建文件;
 * [maxBackupIndex] 为 0 时改为清空文件. [immediateFlush] 为 false 时缓冲写入 (8 KiB), 直到刷盘, 分卷或关闭.
 */
class RollingFileAppender @Throws(IOException::class) constructor(
    val file: File,
    private val layout: PatternLayout,
    val immediateFlush: Boolean = true,
) : Appender {

    var maximumFileSize: Long = 10L * 1024 * 1024
    var maxBackupIndex: Int = 1

    private var writer: Writer? = null
    private var count: Long = 0

    init {
        open(append = true)
    }

    @Synchronized
    override fun append(event: LogEvent) {
        val out = writer ?: return
        val text = layout.format(event)
        try {
            out.write(text)
            if (immediateFlush) out.flush()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write ${file.path}", e)
            return
        }
        count += text.toByteArray(Charsets.UTF_8).size
        if (maximumFileSize > 0 && count >= maximumFileSize) rollOver()
    }

    @Synchronized
    fun flush() {
        runCatching { writer?.flush() }
    }

    @Synchronized
    fun rollOver() {
        closeWriter()
        if (maxBackupIndex > 0) {
            val path = file.path
            File("$path.$maxBackupIndex").delete()
            for (i in maxBackupIndex - 1 downTo 1) {
                val backup = File("$path.$i")
                if (backup.exists()) backup.renameTo(File("$path.${i + 1}"))
            }
            file.renameTo(File("$path.1"))
        }
        try {
            open(append = false)
        } catch (e: IOException) {
            Log.w(TAG, "Failed to reopen ${file.path} after roll-over", e)
        }
    }

    @Synchronized
    override fun close() = closeWriter()

    private fun open(append: Boolean) {
        file.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
        val stream = FileOutputStream(file, append)
        val raw = OutputStreamWriter(stream, Charsets.UTF_8)
        writer = if (immediateFlush) raw else BufferedWriter(raw, BUFFER_SIZE)
        count = if (append) file.length() else 0
    }

    private fun closeWriter() {
        val out = writer ?: return
        writer = null
        runCatching { out.flush() }
        runCatching { out.close() }
    }

    private companion object {
        const val TAG = "RollingFileAppender"
        const val BUFFER_SIZE = 8192
    }

}

/**
 * logcat sink like android-logging-log4j's `LogCatAppender`: the tag is the `%c` layout of the logger name, the
 * message the [messageLayout]; TRACE / DEBUG / INFO / WARN / ERROR map to `Log.v` / `d` / `i` / `w` / `e`, FATAL to
 * `Log.e` (not `wtf`, which may terminate the process on some builds).
 *
 * zh-CN: 类似 android-logging-log4j `LogCatAppender` 的 logcat 输出: 标签为日志器名称的 `%c` 布局, 消息为 [messageLayout];
 * TRACE / DEBUG / INFO / WARN / ERROR 对应 `Log.v` / `d` / `i` / `w` / `e`, FATAL 对应 `Log.e` (不用 `wtf`, 它在部分系统上会终止进程).
 */
class LogCatAppender(
    private val messageLayout: PatternLayout,
    private val tagLayout: PatternLayout = PatternLayout("%c"),
) : Appender {

    override fun append(event: LogEvent) {
        val tag = tagLayout.format(event)
        val message = messageLayout.format(event).removeSuffix("\n")
        when {
            event.level.isGreaterOrEqual(Level.ERROR) -> Log.e(tag, message)
            event.level.isGreaterOrEqual(Level.WARN) -> Log.w(tag, message)
            event.level.isGreaterOrEqual(Level.INFO) -> Log.i(tag, message)
            event.level.isGreaterOrEqual(Level.DEBUG) -> Log.d(tag, message)
            else -> Log.v(tag, message)
        }
    }

    override fun close() = Unit

}
