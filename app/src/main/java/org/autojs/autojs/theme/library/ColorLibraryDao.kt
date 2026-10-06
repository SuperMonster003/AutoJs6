package org.autojs.autojs.theme.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import org.autojs.autojs.theme.app.ColorEntities.ColorGroup
import org.autojs.autojs.theme.app.ColorEntities.ColorGroupState
import org.autojs.autojs.theme.app.ColorEntities.ColorLibrary
import org.autojs.autojs.theme.app.ColorEntities.FavoriteColor
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor

/**
 * Access to user color libraries, intelligent library groups and favorites (schema v2).
 * Child rows are removed by foreign key cascades when their library or group is deleted.
 * zh-CN: 用户颜色库, 智能库的组以及收藏的数据访问 (schema v2). 删除库或组时, 子行由外键级联删除.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
@Dao
interface ColorLibraryDao {

    @Insert
    suspend fun insertLibrary(library: ColorLibrary): Long

    @Update
    suspend fun updateLibrary(library: ColorLibrary)

    @Query("DELETE FROM color_library WHERE id = :id")
    suspend fun deleteLibrary(id: Long)

    @Query("SELECT * FROM color_library WHERE id = :id")
    suspend fun findLibrary(id: Long): ColorLibrary?

    @Query("SELECT * FROM color_library ORDER BY position, id")
    suspend fun listLibraries(): List<ColorLibrary>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM color_library")
    suspend fun nextLibraryPosition(): Int

    @Query("SELECT name FROM color_library")
    suspend fun listLibraryNames(): List<String>

    @Query("UPDATE color_library SET modified_time = :time WHERE id = :id")
    suspend fun touchLibrary(id: Long, time: Long)

    @Transaction
    suspend fun insertLibraryWithColors(library: ColorLibrary, colors: List<LibraryColor>): Long {
        val libraryId = insertLibrary(library)
        if (colors.isNotEmpty()) insertColors(colors.map { it.copy(libraryId = libraryId) })
        return libraryId
    }

    @Insert
    suspend fun insertColors(colors: List<LibraryColor>): List<Long>

    @Update
    suspend fun updateColor(color: LibraryColor)

    @Update
    suspend fun updateColors(colors: List<LibraryColor>)

    @Query("DELETE FROM color_library_color WHERE id IN (:ids)")
    suspend fun deleteColors(ids: List<Long>)

    @Query("SELECT * FROM color_library_color WHERE id = :id")
    suspend fun findColor(id: Long): LibraryColor?

    @Query("SELECT * FROM color_library_color WHERE library_id = :libraryId ORDER BY position, id")
    suspend fun listColors(libraryId: Long): List<LibraryColor>

    @Query("SELECT * FROM color_library_color ORDER BY library_id, position, id")
    suspend fun listAllColors(): List<LibraryColor>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM color_library_color WHERE library_id = :libraryId")
    suspend fun nextColorPosition(libraryId: Long): Int

    @Insert
    suspend fun insertGroup(group: ColorGroup): Long

    @Update
    suspend fun updateGroup(group: ColorGroup)

    @Query("DELETE FROM color_group WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("SELECT * FROM color_group WHERE library_id = :libraryId ORDER BY position, id")
    suspend fun listGroups(libraryId: Long): List<ColorGroup>

    @Upsert
    suspend fun upsertStates(states: List<ColorGroupState>)

    @Query("DELETE FROM color_group_state WHERE group_id = :groupId")
    suspend fun deleteStates(groupId: Long)

    @Query("SELECT * FROM color_group_state WHERE group_id IN (SELECT id FROM color_group WHERE library_id = :libraryId)")
    suspend fun listStates(libraryId: Long): List<ColorGroupState>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFavorite(favorite: FavoriteColor): Long

    @Query("DELETE FROM color_favorite WHERE color = :color")
    suspend fun deleteFavorite(color: Int)

    @Query("SELECT * FROM color_favorite ORDER BY pinned DESC, position, id")
    suspend fun listFavorites(): List<FavoriteColor>

}
