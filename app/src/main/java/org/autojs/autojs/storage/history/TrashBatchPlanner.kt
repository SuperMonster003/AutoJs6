package org.autojs.autojs.storage.history

/**
 * Destination planning for restoring several trash items at once. Pure: file system state comes
 * in through [exists] and [siblingNames] so the rules can be unit tested.
 *
 * zh-CN: 批量恢复回收站条目时的目标路径规划. 纯函数: 文件系统状态通过 [exists] 与 [siblingNames] 传入, 便于单测.
 *
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
object TrashBatchPlanner {

    enum class ConflictPolicy { OVERWRITE, AUTO_RENAME, SKIP }

    /** The subset of a trash item the planner needs. zh-CN: 规划所需的条目字段子集. */
    data class Entry(val id: String, val originalPath: String, val isDirectory: Boolean)

    /**
     * [dest] is the planned absolute path; [conflict] tells whether something already occupied the
     * original destination; [skipped] means the item is left in the trash because of the policy.
     * zh-CN: [dest] 为规划的绝对路径; [conflict] 表示原目标位置已被占用; [skipped] 表示因策略而留在回收站.
     */
    data class Step(val entry: Entry, val dest: String, val conflict: Boolean, val skipped: Boolean)

    /**
     * Plan destinations for [entries]. [targetDir] null means each item's original parent directory.
     * Two items of one batch never share a destination: the second one is auto-renamed even under
     * [ConflictPolicy.OVERWRITE], because overwriting a file restored a moment ago is never intended.
     *
     * zh-CN: 为 [entries] 规划目标. [targetDir] 为 null 表示各自的原父目录.
     * 同一批次内的两个条目不会共用目标: 即使策略为 [ConflictPolicy.OVERWRITE], 后一个也自动改名, 因为覆盖刚恢复的文件绝非本意.
     */
    fun plan(
        entries: List<Entry>,
        targetDir: String?,
        policy: ConflictPolicy,
        exists: (path: String) -> Boolean,
        siblingNames: (dir: String) -> Collection<String>,
    ): List<Step> {
        val reservedByDir = HashMap<String, MutableSet<String>>()
        val steps = ArrayList<Step>(entries.size)
        for (entry in entries) {
            val name = nameOf(entry.originalPath)
            val dir = (targetDir ?: parentOf(entry.originalPath)).trimEnd('/').ifEmpty { "/" }
            val reserved = reservedByDir.getOrPut(dir) { HashSet() }
            val plainDest = join(dir, name)
            val takenOnDisk = exists(plainDest)
            val takenInBatch = name in reserved
            val conflict = takenOnDisk || takenInBatch

            val step = when {
                !conflict -> Step(entry, plainDest, conflict = false, skipped = false)
                policy == ConflictPolicy.SKIP -> Step(entry, plainDest, conflict = true, skipped = true)
                policy == ConflictPolicy.OVERWRITE && !takenInBatch -> Step(entry, plainDest, conflict = true, skipped = false)
                else -> {
                    val taken = HashSet<String>(siblingNames(dir)).also { it.addAll(reserved) }
                    val renamed = TrashRestoreNaming.nextIndexedName(taken, name, entry.isDirectory)
                    Step(entry, join(dir, renamed), conflict = true, skipped = false)
                }
            }
            if (!step.skipped) reserved.add(nameOf(step.dest))
            steps.add(step)
        }
        return steps
    }

    private fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

    private fun parentOf(path: String): String {
        val trimmed = path.trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        return if (idx <= 0) "/" else trimmed.substring(0, idx)
    }

    private fun join(dir: String, name: String): String = if (dir == "/") "/$name" else "$dir/$name"
}
