package org.autojs.autojs.ui.explorer

import android.util.Log
import androidx.recyclerview.widget.DiffUtil
import org.autojs.autojs.ui.viewmodel.ExplorerItemManager

/**
 * A flattened, immutable projection of one Explorer list row used to diff two directory
 * snapshots. Rows mirror the adapter layout exactly: a directory category header, the
 * directory entries (when expanded), a file category header, then the file entries.
 *
 * zh-CN: 文件管理器列表行的扁平不可变投影, 用于对两次目录快照做差量比较.
 * 行序与适配器完全一致: 文件夹分类头, 文件夹条目 (展开时), 文件分类头, 文件条目.
 */
internal data class ExplorerRefreshRow(
    val isCategory: Boolean,
    val isDir: Boolean,
    /** Entry path for item rows; empty for category rows. */
    val identity: String,
    /** Category rows carry their item count; entry rows carry a metadata fingerprint. */
    val fingerprint: Long,
) {

    fun sameItemAs(other: ExplorerRefreshRow): Boolean {
        return isCategory == other.isCategory && isDir == other.isDir && identity == other.identity
    }

    fun sameContentAs(other: ExplorerRefreshRow): Boolean {
        return fingerprint == other.fingerprint
    }

    companion object {

        /** Marks an entry whose metadata is unknown so it never matches a real fingerprint. */
        const val FINGERPRINT_UNKNOWN = Long.MIN_VALUE

        fun category(isDir: Boolean, count: Int) = ExplorerRefreshRow(
            isCategory = true,
            isDir = isDir,
            identity = "",
            fingerprint = count.toLong(),
        )

        fun entry(isDir: Boolean, path: String, fingerprint: Long) = ExplorerRefreshRow(
            isCategory = false,
            isDir = isDir,
            identity = path,
            fingerprint = fingerprint,
        )

        fun fingerprintOf(lastModified: Long, size: Long): Long {
            val mixed = 31 * lastModified + size
            return if (mixed == FINGERPRINT_UNKNOWN) FINGERPRINT_UNKNOWN + 1 else mixed
        }

    }

}

/** Builds and dispatches refresh snapshots without exposing row arithmetic to [ExplorerView]. */
internal object ExplorerRefreshDiffer {

    fun buildMetadata(manager: ExplorerItemManager): Map<String, Long> {
        val metadata = HashMap<String, Long>(manager.count() * 2)
        repeat(manager.groupCount()) { index ->
            val directory = manager.getDirItem(index)
            metadata[directory.path] = ExplorerRefreshRow.fingerprintOf(directory.lastModified(), 0L)
        }
        repeat(manager.itemCount()) { index ->
            val file = manager.getFileItem(index)
            metadata[file.path] = ExplorerRefreshRow.fingerprintOf(file.lastModified(), file.size)
        }
        return metadata
    }

    fun dispatch(
        oldManager: ExplorerItemManager,
        oldMetadata: Map<String, Long>,
        newManager: ExplorerItemManager,
        newMetadata: Map<String, Long>,
        directoriesCollapsed: Boolean,
        filesCollapsed: Boolean,
        adapter: ExplorerAdapter,
    ): Boolean = runCatching {
        val oldRows = buildRows(oldManager, oldMetadata, directoriesCollapsed, filesCollapsed)
        val newRows = buildRows(newManager, newMetadata, directoriesCollapsed, filesCollapsed)
        if (oldRows.size + newRows.size > MAX_DIFF_ROWS) return@runCatching false
        DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldRows.size
            override fun getNewListSize() = newRows.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRows[oldItemPosition].sameItemAs(newRows[newItemPosition])
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldRows[oldItemPosition].sameContentAs(newRows[newItemPosition])
            }
        }, false).dispatchUpdatesTo(adapter)
        true
    }.getOrElse { error ->
        Log.w(LOG_TAG, "Failed to dispatch Explorer refresh diff", error)
        false
    }

    private fun buildRows(
        manager: ExplorerItemManager,
        metadata: Map<String, Long>,
        directoriesCollapsed: Boolean,
        filesCollapsed: Boolean,
    ): List<ExplorerRefreshRow> {
        val rows = ArrayList<ExplorerRefreshRow>(manager.count() + CATEGORY_COUNT)
        rows += ExplorerRefreshRow.category(isDir = true, count = manager.groupCount())
        if (!directoriesCollapsed) {
            repeat(manager.groupCount()) { index ->
                val directory = manager.getDirItem(index)
                rows += ExplorerRefreshRow.entry(
                    isDir = true,
                    path = directory.path,
                    fingerprint = metadata[directory.path] ?: ExplorerRefreshRow.FINGERPRINT_UNKNOWN,
                )
            }
        }
        rows += ExplorerRefreshRow.category(isDir = false, count = manager.itemCount())
        if (!filesCollapsed) {
            repeat(manager.itemCount()) { index ->
                val file = manager.getFileItem(index)
                rows += ExplorerRefreshRow.entry(
                    isDir = false,
                    path = file.path,
                    fingerprint = metadata[file.path] ?: ExplorerRefreshRow.FINGERPRINT_UNKNOWN,
                )
            }
        }
        return rows
    }

    private const val LOG_TAG = "ExplorerRefreshDiffer"
    private const val MAX_DIFF_ROWS = 6000
    private const val CATEGORY_COUNT = 2
}
