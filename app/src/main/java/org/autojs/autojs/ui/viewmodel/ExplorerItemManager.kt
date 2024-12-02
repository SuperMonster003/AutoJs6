package org.autojs.autojs.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.explorer.ExplorerSorter
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/**
 * Created by Stardust on Sep 30, 2017.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on May 14, 2023.
 */
class ExplorerItemManager(private val mContext: Context) {

    private var mSortConfig = SortConfig()
    private val mFileItems = ArrayList<ExplorerItem>()
    private val mDirItems = ArrayList<ExplorerPage>()

    var isDirSortedAscending
        get() = mSortConfig.isDirSortedAscending
        set(dirSortedAscending) {
            mSortConfig.isDirSortedAscending = dirSortedAscending
        }

    var isFileSortedAscending
        get() = mSortConfig.isFileSortedAscending
        set(fileSortedAscending) {
            mSortConfig.isFileSortedAscending = fileSortedAscending
        }

    var dirSortType
        get() = mSortConfig.dirSortType
        set(sortType) {
            mSortConfig.dirSortType = sortType
        }

    var fileSortType
        get() = mSortConfig.fileSortType
        set(sortType) {
            mSortConfig.fileSortType = sortType
        }

    private fun getComparator(sortType: Int) = when (sortType) {
        SORT_TYPE_NAME -> ExplorerSorter.NAME
        SORT_TYPE_DATE -> ExplorerSorter.DATE
        SORT_TYPE_SIZE -> ExplorerSorter.SIZE
        SORT_TYPE_TYPE -> ExplorerSorter.TYPE
        else -> throw IllegalArgumentException(mContext.getString(R.string.error_illegal_argument, "sortType", "$sortType"))
    }

    private fun setSortConfig(sortConfig: SortConfig) {
        mSortConfig = sortConfig
    }

    fun groupCount() = mDirItems.size

    fun itemCount() = mFileItems.size

    fun clear() {
        mFileItems.clear()
        mDirItems.clear()
    }

    fun add(item: ExplorerItem) {
        if (item is ExplorerPage) {
            mDirItems.add(item)
        } else {
            mFileItems.add(item)
        }
    }

    fun insertAtFront(item: ExplorerItem) {
        if (item is ExplorerPage) {
            mDirItems.add(0, item)
        } else {
            mFileItems.add(0, item)
        }
    }

    fun remove(item: ExplorerItem?): Int {
        item ?: return -1
        return if (item is ExplorerPage) {
            remove(mDirItems, item)
        } else {
            remove(mFileItems, item)
        }
    }

    fun update(oldItem: ExplorerItem?, newItem: ExplorerItem): Int {
        oldItem ?: return -1
        return if (oldItem is ExplorerPage) {
            update(mDirItems, oldItem, newItem as ExplorerPage)
        } else {
            update(mFileItems, oldItem, newItem)
        }
    }

    private fun <T> update(list: ArrayList<T>, oldItem: T, newItem: T): Int {
        return list.indexOf(oldItem).also { i ->
            newItem.takeUnless { i < 0 }?.let { list[i] = it }
        }
    }

    private fun <T> remove(list: ArrayList<*>, o: T): Int {
        return list.indexOf(o).also { i ->
            list.takeUnless { i < 0 }?.removeAt(i)
        }
    }

    fun getDirItem(i: Int) = mDirItems[i]

    fun getFileItem(i: Int) = mFileItems[i]

    fun count() = mFileItems.size + mDirItems.size

    fun sortDirs(sortType: Int, isSortedAscending: Boolean) {
        dirSortType = sortType
        isDirSortedAscending = isSortedAscending
        ExplorerSorter.sort(mDirItems, getComparator(sortType), isSortedAscending)
    }

    fun sortFiles(sortType: Int, isSortedAscending: Boolean) {
        fileSortType = sortType
        isFileSortedAscending = isSortedAscending
        ExplorerSorter.sort(mFileItems, getComparator(sortType), isSortedAscending)
    }

    fun sort() {
        ExplorerSorter.sort(mDirItems, getComparator(mSortConfig.dirSortType), mSortConfig.isDirSortedAscending)
        ExplorerSorter.sort(mFileItems, getComparator(mSortConfig.fileSortType), mSortConfig.isFileSortedAscending)
    }

    fun cloneConfig() = ExplorerItemManager(mContext).also { it.setSortConfig(mSortConfig) }

    fun saveSortConfig() = mSortConfig.saveInto(Pref.get())

    fun restoreSortConfig() {
        setSortConfig(SortConfig.from(Pref.get()))
    }

    internal class SortConfig {

        var dirSortType = SORT_TYPE_NAME
        var fileSortType = SORT_TYPE_NAME

        var isDirSortedAscending = true
        var isFileSortedAscending = true

        fun saveInto(preferences: SharedPreferences) {
            preferences.edit()
                .putInt("$CLASS_NAME.file_sort_type", fileSortType)
                .putInt("$CLASS_NAME.dir_sort_type", dirSortType)
                .putBoolean("$CLASS_NAME.file_ascending", isFileSortedAscending)
                .putBoolean("$CLASS_NAME.dir_ascending", isDirSortedAscending)
                .apply()
        }

        companion object {

            private val CLASS_NAME = SortConfig::class.java.name

            fun from(preferences: SharedPreferences) = SortConfig().apply {
                isDirSortedAscending = preferences.getBoolean("$CLASS_NAME.dir_ascending", true)
                isFileSortedAscending = preferences.getBoolean("$CLASS_NAME.file_ascending", true)
                dirSortType = preferences.getInt("$CLASS_NAME.dir_sort_type", SORT_TYPE_NAME)
                fileSortType = preferences.getInt("$CLASS_NAME.file_sort_type", SORT_TYPE_NAME)
            }

        }

    }

    companion object {

        const val SORT_TYPE_NAME = 0x10
        const val SORT_TYPE_TYPE = 0x20
        const val SORT_TYPE_SIZE = 0x30
        const val SORT_TYPE_DATE = 0x40

    }

}