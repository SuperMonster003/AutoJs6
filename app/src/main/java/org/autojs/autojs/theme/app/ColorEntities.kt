package org.autojs.autojs.theme.app

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.autojs.autojs.util.ColorUtils

object ColorEntities {

    data class ColorInfo(val hex: String) {
        val colorInt: Int by lazy { ColorUtils.toInt(hex) }
    }

    open class UsedRecord(open var lastUsedTime: Long) {
        fun setLastUsedTimeCurrent(): UsedRecord {
            return also { lastUsedTime = System.currentTimeMillis() }
        }
    }

    @Entity(tableName = "created_library")
    data class ColorLibrary(
        @PrimaryKey(autoGenerate = true)
        val id: Int = 0,
    )

    @Entity(tableName = "created_color")
    data class CreatedColor(
        @PrimaryKey(autoGenerate = true)
        val id: Int = 0,
        @Embedded
        val colorInfo: ColorInfo,
        @ColumnInfo(name = "created_time")
        val createdTime: Long,
        @ColumnInfo(name = "last_modified_time")
        val lastModifiedTime: Long,
    )

    @Entity(tableName = "preset_color")
    data class PresetColor(
        @PrimaryKey(autoGenerate = true)
        val id: Int = 0,
        @Embedded
        val colorInfo: ColorInfo,
    )

    @Entity(
        tableName = "color_history",
        indices = [Index(value = ["library_id", "item_id"], unique = true)]
    )
    data class ColorHistory(
        @PrimaryKey(autoGenerate = true)
        val id: Int = 0,
        @ColumnInfo(name = "library_id")
        val libraryId: Int,
        @ColumnInfo(name = "item_id")
        val itemId: Int,
        @ColumnInfo(name = "last_used_time")
        override var lastUsedTime: Long = 0L,
    ) : UsedRecord(lastUsedTime)

    @Entity(
        tableName = "palette_history",
        indices = [Index(value = ["hex"], unique = true)]
    )
    data class PaletteHistory(
        @PrimaryKey(autoGenerate = true)
        var id: Int = 0,
        @Embedded
        val colorInfo: ColorInfo,
        @ColumnInfo(name = "last_used_time")
        override var lastUsedTime: Long = 0L,
    ) : UsedRecord(lastUsedTime)

}