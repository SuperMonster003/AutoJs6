package org.autojs.autojs.theme.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import org.autojs.autojs.theme.app.ColorEntities.PaletteHistory

@Dao
interface PaletteHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(history: PaletteHistory): Long

    @Update
    suspend fun update(history: PaletteHistory)

    @Query("SELECT * FROM palette_history WHERE hex = :hex LIMIT 1")
    suspend fun getByHex(hex: String): PaletteHistory?

    @Transaction
    suspend fun upsert(history: PaletteHistory): Long {
        val existing = getByHex(history.colorInfo.hex) ?: return insert(history)
        val updated = existing.copy().also {
            it.setLastUsedTimeCurrent()
            update(it)
        }
        return updated.id.toLong()
    }

    @Query("SELECT * FROM palette_history")
    suspend fun getAll(): List<PaletteHistory>

    @Query("SELECT EXISTS(SELECT 1 FROM palette_history LIMIT 1)")
    suspend fun hasData(): Boolean

    @Delete
    suspend fun delete(vararg histories: PaletteHistory): Int

    @Query("DELETE FROM palette_history")
    suspend fun deleteAll()

}
