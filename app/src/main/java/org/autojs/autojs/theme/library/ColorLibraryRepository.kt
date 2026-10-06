package org.autojs.autojs.theme.library

import android.content.Context
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor
import org.autojs.autojs.theme.app.ColorHistoryDatabase

/**
 * Operations on user color libraries (roadmap P1.1). All functions must run off the main thread.
 * zh-CN: 用户颜色库的操作 (Roadmap P1.1). 所有函数都须在主线程之外调用.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
class ColorLibraryRepository(
    private val database: ColorHistoryDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {

    data class NewColor(val color: Int, val name: String? = null)

    data class PlainLibrarySummary(val library: ColorLibrary, val colorCount: Int, val previewColors: List<Int>)

    private val dao get() = database.colorLibraryDao()

    suspend fun listPlainLibraries(): List<PlainLibrarySummary> {
        val colorsByLibrary = dao.listAllColors().groupBy { it.libraryId }
        return dao.listLibraries().filterNot { it.isIntelligent }.map { library ->
            val colors = colorsByLibrary[library.id].orEmpty()
            PlainLibrarySummary(library, colors.size, colors.take(PREVIEW_SIZE).map { it.color })
        }
    }

    suspend fun findLibrary(id: Long): ColorLibrary? = dao.findLibrary(id)

    suspend fun findColor(id: Long): LibraryColor? = dao.findColor(id)

    suspend fun listColors(libraryId: Long): List<LibraryColor> = dao.listColors(libraryId)

    /**
     * Creates a plain library; [name] is made unique among existing libraries.
     * zh-CN: 新建普通库; [name] 会在现有库名中去重.
     */
    suspend fun createPlainLibrary(name: String, kind: String, origin: String? = null, colors: List<NewColor> = emptyList()): ColorLibrary {
        val time = now()
        val library = ColorLibrary(
            kind = kind,
            isIntelligent = false,
            name = LibraryNaming.unique(name, dao.listLibraryNames()),
            origin = origin,
            position = dao.nextLibraryPosition(),
            createdTime = time,
            modifiedTime = time,
        )
        val rows = colors.mapIndexed { index, color ->
            LibraryColor(libraryId = 0L, name = color.name, color = color.color or OPAQUE, position = index, createdTime = time, modifiedTime = time)
        }
        val id = dao.insertLibraryWithColors(library, rows)
        return library.copy(id = id)
    }

    /**
     * Renames a library, keeping the name unique among the other libraries; returns the stored name.
     * zh-CN: 重命名库并在其他库名中去重; 返回实际保存的名称.
     */
    suspend fun renameLibrary(id: Long, name: String): String? {
        val library = dao.findLibrary(id) ?: return null
        val others = dao.listLibraries().filter { it.id != id }.map { it.name }
        val unique = LibraryNaming.unique(name, others)
        dao.updateLibrary(library.copy(name = unique, modifiedTime = now()))
        return unique
    }

    /**
     * Deletes a library with its colors (cascade) and its color history rows.
     * zh-CN: 删除库及其颜色 (级联), 并清除该库的颜色历史.
     */
    suspend fun deleteLibrary(library: ColorLibrary) {
        dao.deleteLibrary(library.id)
        database.colorHistoryDao().deleteAllByLibraryId(ColorLibraryIds.ofUserLibrary(library.id, library.isIntelligent).toLong())
    }

    suspend fun addColors(libraryId: Long, colors: List<NewColor>): List<Long> {
        if (colors.isEmpty()) return emptyList()
        val time = now()
        val start = dao.nextColorPosition(libraryId)
        val rows = colors.mapIndexed { index, color ->
            LibraryColor(libraryId = libraryId, name = color.name, color = color.color or OPAQUE, position = start + index, createdTime = time, modifiedTime = time)
        }
        return dao.insertColors(rows).also { dao.touchLibrary(libraryId, time) }
    }

    suspend fun updateColor(color: LibraryColor, name: String?, value: Int) {
        val time = now()
        dao.updateColor(color.copy(name = name, color = value or OPAQUE, modifiedTime = time))
        dao.touchLibrary(color.libraryId, time)
    }

    suspend fun deleteColors(libraryId: Long, ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteColors(ids)
        database.colorHistoryDao().deleteByLibraryIdAndItemIds(ColorLibraryIds.ofUserLibrary(libraryId, intelligent = false).toLong(), ids)
        dao.touchLibrary(libraryId, now())
    }

    /**
     * Stores the order of [orderedIds] as positions `0..n-1`; colors not listed keep their relative order after them.
     * zh-CN: 按 [orderedIds] 的顺序保存为位置 `0..n-1`; 未列出的颜色保持原相对顺序排在其后.
     */
    suspend fun reorderColors(libraryId: Long, orderedIds: List<Long>) {
        val colors = dao.listColors(libraryId)
        val rank = orderedIds.withIndex().associate { (index, id) -> id to index }
        val sorted = colors.sortedWith(compareBy<LibraryColor> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.position })
        val changed = sorted.mapIndexedNotNull { index, color -> color.takeIf { it.position != index }?.copy(position = index) }
        if (changed.isNotEmpty()) {
            dao.updateColors(changed)
            dao.touchLibrary(libraryId, now())
        }
    }

    companion object {

        private const val PREVIEW_SIZE = 5

        private const val OPAQUE = 0xFF shl 24

        fun get(context: Context) = ColorLibraryRepository(ColorHistoryDatabase.getInstance(context.applicationContext))

    }

}
