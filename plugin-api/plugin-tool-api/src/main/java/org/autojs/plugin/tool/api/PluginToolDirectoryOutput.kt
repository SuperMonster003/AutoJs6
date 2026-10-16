package org.autojs.plugin.tool.api

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * A directory output of a call (MCP roadmap D53 / D59) over the IPluginToolOutputTree the host opened: the host stages what
 * the tool writes and moves it into the destination once the call reports its result. Paths follow PluginToolOutputTreePaths;
 * tools that write names they did not choose, such as archive entries, check them with PluginToolOutputTreePaths.problemOf
 * first. Streams still open when `onCall` returns are closed by the base class.
 *
 * zh-CN: 一次调用的目录输出, 基于宿主打开的 IPluginToolOutputTree: 宿主暂存工具写入的内容, 调用报告结果后移入目标目录. 路径遵循
 * PluginToolOutputTreePaths, 写入非自己决定的名字 (如压缩包条目) 前先用 PluginToolOutputTreePaths.problemOf 检查. `onCall` 返回时仍未关闭的流由基类关闭.
 */
class PluginToolDirectoryOutput internal constructor(
    /** The output name as the catalog declares it. */
    val name: String,
    private val tree: IPluginToolOutputTree,
    private val call: PluginToolCall,
) {

    private val open = ConcurrentHashMap.newKeySet<OutputStream>()
    private val skipped = Collections.synchronizedList(ArrayList<String>())

    /** The paths [openFile] skipped because they already exist in the destination and the caller did not allow overwriting. */
    val existing: List<String>
        get() = synchronized(skipped) { skipped.toList() }

    /** Creates the directory [relativePath] and its parents, so that empty directories appear in the destination too. */
    fun createDirectory(relativePath: String) {
        val answer = transact(relativePath) { tree.createDirectory(relativePath) }
        if (answer?.getBoolean(PluginToolOutputTreeKeys.OK) != true) throw failure(relativePath, answer ?: Bundle())
    }

    /**
     * Opens the new file [relativePath] (its parents are created) for writing; [expectedBytes] is a hint, -1 when unknown.
     * Returns null when the file already exists in the destination and the caller did not allow overwriting: the path is
     * added to [existing] and the tool may go on. Other refusals throw a PluginToolException.
     */
    @JvmOverloads
    fun openFile(relativePath: String, expectedBytes: Long = -1L): OutputStream? {
        val answer = Bundle()
        val descriptor = transact(relativePath) { tree.openFile(relativePath, expectedBytes, answer) }
        if (descriptor == null) {
            if (answer.getString(PluginToolOutputTreeKeys.ERROR_CODE) == PluginToolOutputTreeErrors.EXISTS) {
                skipped += relativePath
                return null
            }
            throw failure(relativePath, answer)
        }
        return TreeFileStream(descriptor).also { open += it }
    }

    /** Writes [bytes] to the new file [relativePath]; false when [openFile] skipped it because it exists. */
    fun writeFile(relativePath: String, bytes: ByteArray): Boolean {
        val stream = openFile(relativePath, bytes.size.toLong()) ?: return false
        stream.use { it.write(bytes) }
        return true
    }

    internal fun closeStreams() {
        open.toList().forEach { runCatching { it.close() } }
    }

    private fun <T> transact(relativePath: String, block: () -> T): T = try {
        block()
    } catch (e: RemoteException) {
        val code = if (call.isCancelled) PluginToolErrorCodes.CANCELLED else PluginToolErrorCodes.FAILED
        throw PluginToolException(code, "the directory output $name could not take $relativePath: the host is gone", e)
    }

    private fun failure(relativePath: String, answer: Bundle): PluginToolException {
        val reason = answer.getString(PluginToolOutputTreeKeys.ERROR_CODE)
        val code = when (reason) {
            PluginToolOutputTreeErrors.CLOSED -> PluginToolErrorCodes.CANCELLED
            PluginToolOutputTreeErrors.LIMIT_EXCEEDED -> PluginToolErrorCodes.LIMIT_EXCEEDED
            PluginToolOutputTreeErrors.INVALID_PATH -> PluginToolErrorCodes.INVALID_ARGUMENTS
            else -> PluginToolErrorCodes.FAILED
        }
        val detail = answer.getString(PluginToolOutputTreeKeys.ERROR_MESSAGE)?.let { ": $it" }.orEmpty()
        return PluginToolException(code, "the directory output $name refused $relativePath (${reason ?: "no answer"})$detail")
    }

    private inner class TreeFileStream(descriptor: ParcelFileDescriptor) : ParcelFileDescriptor.AutoCloseOutputStream(descriptor) {
        override fun close() {
            try {
                super.close()
            } finally {
                open.remove(this)
            }
        }
    }
}
