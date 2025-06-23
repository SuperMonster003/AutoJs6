package org.autojs.autojs.theme.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.autojs.autojs.theme.app.ColorEntities.ColorHistory

@Dao
interface ColorHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: ColorHistory): Long

    @Update
    suspend fun update(history: ColorHistory)

    @Transaction
    suspend fun upsert(history: ColorHistory): Long {
        val existing = getByHistory(history) ?: return insert(history)
        val updated = existing.copy().also {
            it.setLastUsedTimeCurrent()
            update(it)
        }
        return updated.id.toLong()
    }

    private suspend fun getByHistory(history: ColorHistory): ColorHistory? {
        return getByLibraryIdAndItemId(history.libraryId.toLong(), history.itemId.toLong())
    }

    @Query("SELECT * FROM color_history WHERE library_id = :libraryId AND item_id = :itemId LIMIT 1")
    suspend fun getByLibraryIdAndItemId(libraryId: Long, itemId: Long): ColorHistory?

    @Query("SELECT * FROM color_history")
    suspend fun getAll(): List<ColorHistory>

    @Query("SELECT * FROM color_history WHERE library_id = :libraryId")
    suspend fun getAllByLibraryId(libraryId: Long): List<ColorHistory>

    @Query("SELECT EXISTS(SELECT 1 FROM color_history LIMIT 1)")
    suspend fun hasData(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM color_history WHERE library_id = :libraryId LIMIT 1)")
    suspend fun hasDataByLibraryId(libraryId: Long): Boolean

    @Delete
    suspend fun delete(vararg histories: ColorHistory)

    @Query("DELETE FROM color_history")
    suspend fun deleteAll()

    @Query("DELETE FROM color_history WHERE library_id = :libraryId")
    suspend fun deleteAllByLibraryId(libraryId: Long)

}
