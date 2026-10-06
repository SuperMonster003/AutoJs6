package org.autojs.autojs.theme.library

import android.content.Context
import androidx.room.withTransaction
import org.autojs.autojs.theme.app.ColorEntities.ColorGroup
import org.autojs.autojs.theme.app.ColorEntities.ColorGroupState
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor
import org.autojs.autojs.theme.app.ColorHistoryDatabase
import org.autojs.autojs.theme.intelligent.IntelligentConfig
import org.autojs.autojs.theme.intelligent.IntelligentConfigJson
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs.theme.intelligent.StateColors

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

    /** All user libraries, plain and intelligent, in list order. zh-CN: 按列表顺序排列的全部用户库 (含普通库与智能库). */
    suspend fun listLibraries(): List<ColorLibrary> = dao.listLibraries()

    suspend fun findLibrary(id: Long): ColorLibrary? = dao.findLibrary(id)

    suspend fun findColor(id: Long): LibraryColor? = dao.findColor(id)

    /**
     * Every color of the plain user libraries with its library, in list order (roadmap P2.4).
     * zh-CN: 全部普通用户库的颜色及其所属库, 按列表顺序排列 (Roadmap P2.4).
     */
    suspend fun listAllPlainColors(): List<Pair<ColorLibrary, LibraryColor>> {
        val colorsByLibrary = dao.listAllColors().groupBy { it.libraryId }
        return dao.listLibraries().filterNot { it.isIntelligent }.flatMap { library ->
            colorsByLibrary[library.id].orEmpty().sortedWith(compareBy({ it.position }, { it.id })).map { library to it }
        }
    }

    suspend fun listColors(libraryId: Long): List<LibraryColor> = dao.listColors(libraryId)

    /**
     * Creates a plain library; [name] is made unique among existing libraries and [reservedNames] (such as preset names).
     * zh-CN: 新建普通库; [name] 会在现有库名与 [reservedNames] (例如预设库名) 中去重.
     */
    suspend fun createPlainLibrary(
        name: String,
        kind: String,
        origin: String? = null,
        colors: List<NewColor> = emptyList(),
        reservedNames: Collection<String> = emptyList(),
    ): ColorLibrary {
        val time = now()
        val library = ColorLibrary(
            kind = kind,
            isIntelligent = false,
            name = LibraryNaming.unique(name, dao.listLibraryNames() + reservedNames),
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

    /**
     * Stores the order of [orderedIds] as positions `0..n-1`; libraries not listed (such as intelligent libraries when
     * reordering plain ones) keep their relative order after them (roadmap P2.5).
     * zh-CN: 按 [orderedIds] 的顺序保存为位置 `0..n-1`; 未列出的库 (例如调整普通库顺序时的智能库) 保持原相对顺序排在其后 (Roadmap P2.5).
     */
    suspend fun reorderLibraries(orderedIds: List<Long>) {
        val rank = orderedIds.withIndex().associate { (index, id) -> id to index }
        val sorted = dao.listLibraries().sortedWith(compareBy<ColorLibrary> { rank[it.id] ?: Int.MAX_VALUE }.thenBy { it.position })
        val changed = sorted.mapIndexedNotNull { index, library -> library.takeIf { it.position != index }?.copy(position = index) }
        if (changed.isNotEmpty()) dao.updateLibraries(changed)
    }

    suspend fun listIntelligentLibraries(): List<IntelligentSnapshot> {
        return dao.listLibraries().filter { it.isIntelligent }.map { snapshotOf(it) }
    }

    suspend fun loadIntelligent(rowId: Long): IntelligentSnapshot? {
        return dao.findLibrary(rowId)?.takeIf { it.isIntelligent }?.let { snapshotOf(it) }
    }

    /**
     * Creates ([rowId] null) or updates an intelligent library. Groups with an id that already belongs to the library
     * keep it; any other id (such as a negative editor id) creates a new group. The overrides of [config] refer to the
     * ids in [groups] and are rewritten to the stored ids. Returns the stored library.
     * zh-CN: 新建 ([rowId] 为 null) 或更新智能库. id 已属于该库的组保留其 id; 其他 id (例如编辑器中的负数 id) 新建组.
     * [config] 的覆盖层引用 [groups] 中的 id, 保存时改写为实际的 id. 返回保存后的库.
     */
    suspend fun saveIntelligent(
        rowId: Long?,
        name: String,
        config: IntelligentConfig,
        groups: List<IntelligentGroup>,
        kind: String = ColorLibrary.Kind.CREATED,
        origin: String? = null,
        reservedNames: Collection<String> = emptyList(),
    ): IntelligentSnapshot {
        val time = now()
        val configJson = IntelligentConfigJson.encode(config)
        val groupRows = groups.mapIndexed { index, group ->
            ColorGroup(
                id = group.id,
                libraryId = rowId ?: 0L,
                name = group.name,
                enabled = group.enabled,
                position = index,
                createdTime = group.createdTime.takeIf { it > 0L } ?: time,
                modifiedTime = group.modifiedTime.takeIf { it > 0L } ?: time,
            )
        }
        val stateRows = groups.map { group ->
            group.states.map { (key, colors) -> ColorGroupState(0L, key, colors.primary, colors.accent, colors.dark) }
        }
        val libraryId = database.withTransaction {
            val saved = when (val existing = rowId?.let { dao.findLibrary(it) }) {
                null -> dao.insertIntelligentLibrary(
                    ColorLibrary(
                        kind = kind,
                        isIntelligent = true,
                        name = LibraryNaming.unique(name, dao.listLibraryNames() + reservedNames),
                        origin = origin,
                        configJson = configJson,
                        position = dao.nextLibraryPosition(),
                        createdTime = time,
                        modifiedTime = time,
                    ),
                    groupRows,
                    stateRows,
                )
                else -> {
                    val others = dao.listLibraries().filter { it.id != existing.id }.map { it.name }
                    dao.updateIntelligentLibrary(
                        existing.copy(name = LibraryNaming.unique(name, others), configJson = configJson, modifiedTime = time),
                        groupRows,
                        stateRows,
                    )
                }
            }
            // Overrides refer to the ids of [groups]; new groups have just received their stored ids.
            // zh-CN: 覆盖层引用的是 [groups] 中的 id; 新组刚刚获得保存后的 id.
            val remapped = config.remapGroupIds(groups.map { it.id }.zip(saved.groupIds).toMap())
            if (remapped != config) dao.updateLibraryConfig(saved.libraryId, IntelligentConfigJson.encode(remapped))
            saved.libraryId
        }
        return loadIntelligent(libraryId)!!
    }

    private suspend fun snapshotOf(library: ColorLibrary): IntelligentSnapshot {
        val states = dao.listStates(library.id).groupBy { it.groupId }
        return IntelligentSnapshot(
            libraryId = ColorLibraryIds.ofUserLibrary(library.id, intelligent = true),
            name = library.name,
            config = IntelligentConfigJson.decode(library.configJson),
            groups = dao.listGroups(library.id).map { group ->
                IntelligentGroup(
                    id = group.id,
                    name = group.name,
                    enabled = group.enabled,
                    position = group.position,
                    createdTime = group.createdTime,
                    modifiedTime = group.modifiedTime,
                    states = states[group.id].orEmpty().associate { it.stateKey to StateColors(it.primaryColor, it.accentColor, it.darkColor) },
                )
            },
        )
    }

    companion object {

        private const val PREVIEW_SIZE = 5

        private const val OPAQUE = 0xFF shl 24

        fun get(context: Context) = ColorLibraryRepository(ColorHistoryDatabase.getInstance(context.applicationContext))

    }

}
