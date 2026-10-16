package org.autojs.autojs.engine

import android.os.Bundle
import android.os.ParcelFileDescriptor
import org.autojs.plugin.tool.api.IPluginToolOutputTree
import org.autojs.plugin.tool.api.PluginToolLimits
import org.autojs.plugin.tool.api.PluginToolOutputTreeErrors
import org.autojs.plugin.tool.api.PluginToolOutputTreeKeys
import org.autojs.plugin.tool.api.PluginToolOutputTreePaths
import java.io.File

/**
 * One directory output of a plugin tool call (MCP Server roadmap D53, docs/dev/plugin-tool-protocol-v1.md). The plugin
 * writes through it into a hidden staging directory next to [destination]; [commit] moves the files into the
 * destination when the call succeeds and [discard] deletes the staging directory otherwise. The destination may exist
 * (D56): a file already there is a conflict unless [overwrite], and so is a path that would leave the destination
 * through a link. Calls arrive on Binder threads, so the state is guarded by one lock.
 *
 * zh-CN: 插件工具调用的一个目录输出: 插件经它写入目标目录旁的隐藏暂存目录, 调用成功时 [commit] 把文件移入目标目录, 否则 [discard]
 * 删除暂存目录. 目标目录可以已存在: 已有的同名文件除非 [overwrite] 否则记为冲突, 经链接跑出目标目录的路径也记为冲突.
 */
internal class PluginToolOutputTree(
    private val destination: File,
    private val overwrite: Boolean,
    stagingName: String,
) : IPluginToolOutputTree.Stub() {

    /** What [commit] did: files moved into place, their bytes, and the paths it left alone. */
    data class Committed(val files: Int, val bytes: Long, val conflicts: List<String>)

    private val staging = File(destination.absoluteFile.parentFile, stagingName)
    private val lock = Any()
    private val directories = LinkedHashSet<String>()
    private val files = LinkedHashSet<String>()
    private var closed = false

    override fun createDirectory(relativePath: String?): Bundle = synchronized(lock) {
        val path = relativePath.orEmpty().removeSuffix("/")
        if (closed) return refuse(PluginToolOutputTreeErrors.CLOSED, "the call has ended")
        PluginToolOutputTreePaths.problemOf(path)?.let { return refuse(PluginToolOutputTreeErrors.INVALID_PATH, it) }
        if (path in files) return refuse(PluginToolOutputTreeErrors.INVALID_PATH, "$path is a file of this output")
        val directory = File(staging, path)
        if (!directory.isDirectory && !directory.mkdirs()) return refuse(PluginToolOutputTreeErrors.IO_ERROR, "cannot create $path")
        directories += path
        Bundle().apply { putBoolean(PluginToolOutputTreeKeys.OK, true) }
    }

    override fun openFile(relativePath: String?, expectedBytes: Long, answer: Bundle?): ParcelFileDescriptor? = synchronized(lock) {
        val path = relativePath.orEmpty()
        fun refuse(code: String, message: String): ParcelFileDescriptor? {
            answer?.apply { putBoolean(PluginToolOutputTreeKeys.OK, false); putString(PluginToolOutputTreeKeys.ERROR_CODE, code); putString(PluginToolOutputTreeKeys.ERROR_MESSAGE, message) }
            return null
        }
        if (closed) return refuse(PluginToolOutputTreeErrors.CLOSED, "the call has ended")
        PluginToolOutputTreePaths.problemOf(path)?.let { return refuse(PluginToolOutputTreeErrors.INVALID_PATH, it) }
        if (path.endsWith("/")) return refuse(PluginToolOutputTreeErrors.INVALID_PATH, "$path names a directory")
        if (path in files) return refuse(PluginToolOutputTreeErrors.INVALID_PATH, "$path was opened before")
        if (path in directories || directories.any { it.startsWith("$path/") }) return refuse(PluginToolOutputTreeErrors.INVALID_PATH, "$path is a directory of this output")
        if (files.size >= PluginToolLimits.MAX_OUTPUT_TREE_FILES) {
            return refuse(PluginToolOutputTreeErrors.LIMIT_EXCEEDED, "the output already holds ${PluginToolLimits.MAX_OUTPUT_TREE_FILES} files")
        }
        val target = File(destination, path)
        if (!inside(target)) return refuse(PluginToolOutputTreeErrors.INVALID_PATH, "$path leaves the output directory")
        if (target.isDirectory) return refuse(PluginToolOutputTreeErrors.EXISTS, "$path is a directory in the output directory")
        if (target.exists() && !overwrite) return refuse(PluginToolOutputTreeErrors.EXISTS, "$path exists in the output directory")
        val staged = File(staging, path)
        val parent = staged.parentFile
        if (parent == null || (!parent.isDirectory && !parent.mkdirs())) return refuse(PluginToolOutputTreeErrors.IO_ERROR, "cannot create the folder of $path")
        val descriptor = runCatching {
            ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
        }.getOrElse { return refuse(PluginToolOutputTreeErrors.IO_ERROR, "cannot create $path: ${it.message}") }
        files += path
        answer?.putBoolean(PluginToolOutputTreeKeys.OK, true)
        // AIDL writes a returned descriptor with PARCELABLE_WRITE_RETURN_VALUE, which closes the host's copy.
        descriptor
    }

    /** Moves the staged files into [destination] once; later calls of the plugin answer CLOSED. */
    fun commit(): Committed = synchronized(lock) {
        if (closed) return Committed(0, 0L, emptyList())
        closed = true
        try {
            if (!destination.isDirectory && !destination.mkdirs()) throw IllegalStateException("cannot create ${destination.name}")
            directories.forEach { path -> File(destination, path).takeIf { inside(it) }?.mkdirs() }
            var moved = 0
            var bytes = 0L
            val conflicts = mutableListOf<String>()
            for (path in files) {
                val staged = File(staging, path)
                val target = File(destination, path)
                val parent = target.parentFile
                when {
                    !staged.isFile -> continue
                    !inside(target) || target.isDirectory || (target.exists() && !overwrite) -> conflicts += path
                    parent == null || (!parent.isDirectory && !parent.mkdirs()) -> conflicts += path
                    else -> {
                        val length = staged.length()
                        if (target.exists()) target.delete()
                        if (staged.renameTo(target)) {
                            moved += 1
                            bytes += length
                        } else {
                            conflicts += path
                        }
                    }
                }
            }
            Committed(moved, bytes, conflicts)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Drops whatever the plugin wrote; nothing reaches [destination]. */
    fun discard() = synchronized(lock) {
        val wasOpen = !closed
        closed = true
        if (wasOpen) staging.deleteRecursively()
    }

    /** False when [file] resolves outside [destination], for example through a link inside it. */
    private fun inside(file: File): Boolean {
        val root = destination.canonicalFile.path
        val path = file.canonicalFile.path
        return path == root || path.startsWith(root + File.separator)
    }

    private fun refuse(code: String, message: String) = Bundle().apply {
        putBoolean(PluginToolOutputTreeKeys.OK, false)
        putString(PluginToolOutputTreeKeys.ERROR_CODE, code)
        putString(PluginToolOutputTreeKeys.ERROR_MESSAGE, message)
    }
}
