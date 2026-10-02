package org.autojs.autojs.runtime.api.terminal

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The worker thread that drains one plugin output pipe into a [TerminalOutputReader] (plugin
 * roadmap P4.2). It ends at end of stream, which the plugin produces when the session exits
 * (after draining its buffer) or when the subscription is cancelled, or when [close] closes the
 * read end. The reader is finished exactly once, then [onFinished] runs on the worker thread.
 * zh-CN: 把一个插件输出管道排入 [TerminalOutputReader] 的工作线程 (插件路线图 P4.2). 在流结束 (会话退出后插件刷完缓冲, 或订阅被取消时由插件产生) 或
 * [close] 关闭读端时结束. 读取器恰好结束一次, 随后 [onFinished] 在工作线程上运行.
 */
internal class TerminalOutputPump(
    private val readEnd: ParcelFileDescriptor,
    private val reader: TerminalOutputReader,
    private val onFinished: (TerminalOutputPump) -> Unit = {},
) {

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val worker = thread(name = "TerminalOutputPump", start = false, isDaemon = true) { run() }

    @Volatile
    var isFinished: Boolean = false
        private set

    fun start() {
        if (started.compareAndSet(false, true)) worker.start()
    }

    /** Closes the read end; the plugin side is told separately (`unsubscribeOutput`) so the write end closes too. zh-CN: 关闭读端; 插件侧另行通知 (`unsubscribeOutput`) 以便写端也关闭. */
    fun close() {
        closed.set(true)
    }

    private fun run() {
        val input = ParcelFileDescriptor.AutoCloseInputStream(readEnd)
        val buffer = ByteArray(BUFFER_BYTES)
        try {
            while (!closed.get()) {
                val poll = StructPollfd().apply {
                    fd = readEnd.fileDescriptor
                    events = OsConstants.POLLIN.toShort()
                }
                if (Os.poll(arrayOf(poll), 100) == 0) {
                    reader.flushPartial()
                    continue
                }
                if (closed.get()) break
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) reader.feed(buffer, 0, read)
            }
        } catch (_: IOException) {
            // The read end was closed or the plugin is gone: the stream is over.
            // zh-CN: 读端已关闭或插件已消失: 流结束.
        } catch (_: ErrnoException) {
            // The bounded poll also makes cancellation work on Android 7.
        } finally {
            runCatching { input.close() }
            runCatching { reader.finish() }
            isFinished = true
            runCatching { onFinished(this) }
        }
    }

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
    }
}
