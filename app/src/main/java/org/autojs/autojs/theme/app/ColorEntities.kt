package org.autojs.autojs.theme.app

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
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

    /**
     * Where a history entry came from (schema v2). Rows written before v2 default to [USER].
     * zh-CN: 历史条目的来源 (schema v2). v2 之前写入的行默认为 [USER].
     */
    object HistorySource {
        const val USER = "USER"
        const val SCRIPT = "SCRIPT"
    }

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
        @ColumnInfo(name = "use_count", defaultValue = "1")
        val useCount: Int = 1,
        @ColumnInfo(name = "source", defaultValue = HistorySource.USER)
        val source: String = HistorySource.USER,
        @ColumnInfo(name = "source_detail")
        val sourceDetail: String? = null,
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
        @ColumnInfo(name = "use_count", defaultValue = "1")
        val useCount: Int = 1,
        @ColumnInfo(name = "source", defaultValue = HistorySource.USER)
        val source: String = HistorySource.USER,
        @ColumnInfo(name = "source_detail")
        val sourceDetail: String? = null,
    ) : UsedRecord(lastUsedTime)

    /**
     * A user library (schema v2): a plain library holds [LibraryColor] rows, an intelligent one holds
     * [ColorGroup] rows and keeps its configuration in [configJson] (see `IntelligentConfigJson`).
     * zh-CN: 用户颜色库 (schema v2): 普通库包含 [LibraryColor] 行, 智能库包含 [ColorGroup] 行, 其配置保存在 [configJson] (见 `IntelligentConfigJson`).
     */
    @Entity(
        tableName = "color_library",
        indices = [Index(value = ["position"])]
    )
    data class ColorLibrary(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,
        @ColumnInfo(name = "kind")
        val kind: String,
        @ColumnInfo(name = "is_intelligent")
        val isIntelligent: Boolean,
        @ColumnInfo(name = "name")
        val name: String,
        /**
         * Display-only provenance, such as the identifier of a cloned library or an imported file name.
         * zh-CN: 仅用于显示的来源信息, 例如被克隆库的标识或导入的文件名.
         */
        @ColumnInfo(name = "origin")
        val origin: String? = null,
        @ColumnInfo(name = "config_json")
        val configJson: String? = null,
        @ColumnInfo(name = "position")
        val position: Int,
        @ColumnInfo(name = "created_time")
        val createdTime: Long,
        @ColumnInfo(name = "modified_time")
        val modifiedTime: Long,
    ) {
        object Kind {
            const val CREATED = "CREATED"
            const val IMPORTED = "IMPORTED"
            const val CLONED = "CLONED"
        }
    }

    @Entity(
        tableName = "color_library_color",
        foreignKeys = [
            ForeignKey(
                entity = ColorLibrary::class,
                parentColumns = ["id"],
                childColumns = ["library_id"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
        indices = [Index(value = ["library_id"])]
    )
    data class LibraryColor(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,
        @ColumnInfo(name = "library_id")
        val libraryId: Long,
        @ColumnInfo(name = "name")
        val name: String? = null,
        @ColumnInfo(name = "color")
        val color: Int,
        @ColumnInfo(name = "position")
        val position: Int,
        @ColumnInfo(name = "created_time")
        val createdTime: Long,
        @ColumnInfo(name = "modified_time")
        val modifiedTime: Long,
    )

    @Entity(
        tableName = "color_group",
        foreignKeys = [
            ForeignKey(
                entity = ColorLibrary::class,
                parentColumns = ["id"],
                childColumns = ["library_id"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
        indices = [Index(value = ["library_id"])]
    )
    data class ColorGroup(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,
        @ColumnInfo(name = "library_id")
        val libraryId: Long,
        @ColumnInfo(name = "name")
        val name: String? = null,
        @ColumnInfo(name = "enabled")
        val enabled: Boolean = true,
        @ColumnInfo(name = "position")
        val position: Int,
        @ColumnInfo(name = "created_time")
        val createdTime: Long,
        @ColumnInfo(name = "modified_time")
        val modifiedTime: Long,
    )

    /**
     * Colors of one state of a group. A null [primaryColor] means "same as the first state that has one".
     * zh-CN: 组内一个状态的颜色. [primaryColor] 为空表示 "与第一个有颜色的状态相同".
     */
    @Entity(
        tableName = "color_group_state",
        primaryKeys = ["group_id", "state_key"],
        foreignKeys = [
            ForeignKey(
                entity = ColorGroup::class,
                parentColumns = ["id"],
                childColumns = ["group_id"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    )
    data class ColorGroupState(
        @ColumnInfo(name = "group_id")
        val groupId: Long,
        @ColumnInfo(name = "state_key")
        val stateKey: String,
        @ColumnInfo(name = "primary_color")
        val primaryColor: Int? = null,
        @ColumnInfo(name = "accent_color")
        val accentColor: Int? = null,
        @ColumnInfo(name = "dark_color")
        val darkColor: Int? = null,
    )

    @Entity(
        tableName = "color_favorite",
        indices = [Index(value = ["color"], unique = true)]
    )
    data class FavoriteColor(
        @PrimaryKey(autoGenerate = true)
        val id: Long = 0L,
        @ColumnInfo(name = "color")
        val color: Int,
        @ColumnInfo(name = "pinned")
        val pinned: Boolean = false,
        @ColumnInfo(name = "position")
        val position: Int,
        @ColumnInfo(name = "created_time")
        val createdTime: Long,
    )

}
