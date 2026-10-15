package org.autojs.autojs.core.plugin.batchrename

import org.autojs.plugin.batchrename.api.BatchRenameLimits
import java.io.File
import java.io.IOException
import java.util.Locale

/** One rename candidate of a script plan. zh-CN: 脚本方案中的一个改名候选项. */
data class BatchRenameTarget(
    val file: File,
    val parentId: String,
    /** 0 for a path the script named, then one more per directory level below it. zh-CN: 脚本给出的路径为 0, 其下每层加一. */
    val depth: Int,
    val directory: Boolean,
    val lastModified: Long,
    /** Size of a file; -1 for a directory. zh-CN: 文件大小; 目录为 -1. */
    val length: Long,
)

/** A directory that holds candidates, with every name it holds and its file-system traits. zh-CN: 含候选项的目录. */
data class BatchRenameParent(
    val id: String,
    val directory: File,
    val existingNames: List<String>,
    val traits: BatchRenameVolumeTraits,
)

data class BatchRenameTargets(val items: List<BatchRenameTarget>, val parents: List<BatchRenameParent>)

/**
 * Enumerates the candidates of a script plan (plugin roadmap D30): every path the script named, in its order, and with
 * `includeSubfolders` everything below a named directory, depth first and sorted by name like the file manager.
 * Symbolic links are never followed: a named path must not be one, and links found below a directory are skipped (they
 * still occupy their names). Duplicates are dropped. Exceeding the contract limits or depth 32 is `LIMIT_EXCEEDED`.
 *
 * zh-CN: 枚举脚本方案的候选项 (插件路线图 D30): 脚本给出的每个路径 (保持顺序), 以及 `includeSubfolders` 时给出目录之下的全部条目
 * (深度优先, 与文件管理器一样按名称排序). 从不跟随符号链接: 给出的路径不能是符号链接, 目录下遇到的链接被跳过 (仍占用其名称).
 * 重复项被去除. 超出契约上限或深度 32 时为 `LIMIT_EXCEEDED`.
 */
class BatchRenameTargetCollector(
    private val maxItems: Int = BatchRenameLimits.MAX_ITEMS_PER_PLAN,
    private val maxParents: Int = BatchRenameLimits.MAX_PARENTS_PER_PLAN,
    private val maxExistingNames: Int = BatchRenameLimits.MAX_EXISTING_NAMES_PER_PLAN,
    private val maxDepth: Int = MAX_DEPTH,
    private val traitsOf: (File, List<String>) -> BatchRenameVolumeTraits = BatchRenameVolumeTraits::of,
) {

    fun collect(paths: List<File>, includeSubfolders: Boolean): BatchRenameTargets = Collection().apply {
        if (paths.isEmpty()) batchRenameFailure(BatchRenameScriptCodes.INVALID_PATHS, "no path to rename")
        paths.forEach { path ->
            val file = path.absoluteFile.normalize()
            if (file.parentFile == null) batchRenameFailure(BatchRenameScriptCodes.INVALID_PATHS, "cannot rename the root directory: ${file.path}")
            if (isSymbolicLink(file)) batchRenameFailure(BatchRenameScriptCodes.INVALID_PATHS, "symbolic links are not renamed: ${file.path}")
            if (!file.exists()) batchRenameFailure(BatchRenameScriptCodes.INVALID_PATHS, "path does not exist: ${file.path}")
            if (add(file, 0) && file.isDirectory && includeSubfolders) walk(file, 1)
        }
    }.result()

    private inner class Collection {
        private val items = ArrayList<BatchRenameTarget>()
        private val seen = HashSet<String>()
        private val parents = LinkedHashMap<String, BatchRenameParent>()
        private var existingNames = 0

        fun add(file: File, depth: Int): Boolean {
            if (!seen.add(file.path)) return false
            if (items.size >= maxItems) {
                batchRenameFailure(BatchRenameScriptCodes.LIMIT_EXCEEDED, "a plan holds at most $maxItems items")
            }
            val directory = file.isDirectory
            items += BatchRenameTarget(
                file = file,
                parentId = parentOf(file.parentFile!!).id,
                depth = depth,
                directory = directory,
                lastModified = file.lastModified(),
                length = if (directory) -1L else file.length(),
            )
            return true
        }

        fun walk(directory: File, depth: Int) {
            val children = directory.listFiles()
                ?.filterNot(::isSymbolicLink)
                ?: batchRenameFailure(BatchRenameScriptCodes.IO_FAILED, "cannot list the folder ${directory.path}")
            if (children.isEmpty()) return
            if (depth > maxDepth) {
                batchRenameFailure(BatchRenameScriptCodes.LIMIT_EXCEEDED, "folders nest deeper than $maxDepth levels below the named paths")
            }
            children.sortedWith(NAME_ORDER).forEach { child ->
                if (add(child, depth) && child.isDirectory) walk(child, depth + 1)
            }
        }

        private fun parentOf(directory: File): BatchRenameParent = parents.getOrPut(directory.path) {
            if (parents.size >= maxParents) {
                batchRenameFailure(BatchRenameScriptCodes.LIMIT_EXCEEDED, "a plan spans at most $maxParents folders")
            }
            val names = directory.list()?.sortedWith(NAME_STRING_ORDER)
                ?: batchRenameFailure(BatchRenameScriptCodes.IO_FAILED, "cannot list the folder ${directory.path}")
            existingNames += names.size
            if (existingNames > maxExistingNames) {
                batchRenameFailure(BatchRenameScriptCodes.LIMIT_EXCEEDED, "the folders of a plan hold at most $maxExistingNames names")
            }
            BatchRenameParent("p${parents.size}", directory, names, traitsOf(directory, names))
        }

        fun result() = BatchRenameTargets(items.toList(), parents.values.toList())
    }

    companion object {
        /** Folder levels below a named directory (plugin roadmap D30). zh-CN: 给出目录之下的层数上限. */
        const val MAX_DEPTH = 32

        private val NAME_STRING_ORDER = compareBy<String>({ it.lowercase(Locale.ROOT) }, { it })
        private val NAME_ORDER = compareBy<File>({ it.name.lowercase(Locale.ROOT) }, File::getName)

        /** True when the last segment of [file] is a symbolic link (or cannot be resolved). zh-CN: [file] 最后一段为符号链接时为真. */
        fun isSymbolicLink(file: File): Boolean = try {
            val parent = file.absoluteFile.parentFile?.canonicalFile
            if (parent == null) {
                false
            } else {
                val unresolved = File(parent, file.name)
                unresolved.canonicalFile != unresolved
            }
        } catch (_: IOException) {
            true
        }
    }
}
