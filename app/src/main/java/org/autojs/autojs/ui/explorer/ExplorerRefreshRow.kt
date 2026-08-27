package org.autojs.autojs.ui.explorer

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
