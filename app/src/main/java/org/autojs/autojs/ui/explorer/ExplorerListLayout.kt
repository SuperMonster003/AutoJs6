package org.autojs.autojs.ui.explorer

/** A logical row in the Explorer's flattened directory/file list. */
internal sealed interface ExplorerListRow {

    data class Category(val directories: Boolean) : ExplorerListRow

    data class Entry(val directories: Boolean, val index: Int) : ExplorerListRow
}

/**
 * Pure layout model for the Explorer list.
 *
 * Keeping collapse and category-offset arithmetic here gives change notifications, binding and
 * view-type lookup one source of truth. It also makes the boundary cases testable without Android.
 */
internal data class ExplorerListLayout(
    val directoryCount: Int,
    val fileCount: Int,
    val directoriesCollapsed: Boolean,
    val filesCollapsed: Boolean,
) {

    init {
        require(directoryCount >= 0) { "Explorer directory count cannot be negative" }
        require(fileCount >= 0) { "Explorer file count cannot be negative" }
    }

    val directoryCategoryPosition: Int = 0

    val fileCategoryPosition: Int
        get() = if (directoriesCollapsed) 1 else directoryCount + 1

    val itemCount: Int
        get() = visibleDirectoryCount + visibleFileCount + CATEGORY_COUNT

    fun rowAt(position: Int): ExplorerListRow {
        require(position in 0 until itemCount) { "Explorer row position is out of bounds" }
        return when {
            position == directoryCategoryPosition -> ExplorerListRow.Category(directories = true)
            position == fileCategoryPosition -> ExplorerListRow.Category(directories = false)
            position < fileCategoryPosition -> ExplorerListRow.Entry(
                directories = true,
                index = position - directoryCategoryPosition - 1,
            )
            else -> ExplorerListRow.Entry(
                directories = false,
                index = position - fileCategoryPosition - 1,
            )
        }
    }

    fun positionOf(directories: Boolean, index: Int): Int {
        require(index >= 0) { "Explorer entry index cannot be negative" }
        return index + if (directories) directoryCategoryPosition + 1 else fileCategoryPosition + 1
    }

    private val visibleDirectoryCount: Int
        get() = if (directoriesCollapsed) 0 else directoryCount

    private val visibleFileCount: Int
        get() = if (filesCollapsed) 0 else fileCount

    private companion object {
        private const val CATEGORY_COUNT = 2
    }
}
