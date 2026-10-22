package org.autojs.autojs.core.pref

import org.autojs.autojs6.R

/**
 * One category of the export / import wizards (docs/settings/ROADMAP.md D6, T4, appendix A.1).
 *
 * Keys name their category where they are declared and the registry builds the reverse index (T4); [order] is the
 * position in appendix A.1, [defaultSelected] the default check state in the wizards, [sensitive] marks `credentials`
 * (D2), and a [Kind.DATA] category holds data items instead of keys (D21 / T19). [titleRes] is the title the wizards
 * show (`settings_transfer_category_<id>`, P6.2); a category built elsewhere (tests) may leave it [NO_TITLE].
 *
 * zh-CN: 导出 / 导入向导的一个分类 (ROADMAP D6, T4, 附录 A.1). 键在声明处写分类, 注册表反向索引 (T4); [order] 为附录 A.1
 * 中的位置, [defaultSelected] 为向导中的默认勾选, [sensitive] 标记 `credentials` (D2), [Kind.DATA] 分类存放数据条目而非键
 * (D21 / T19). [titleRes] 为向导显示的标题 (`settings_transfer_category_<id>`, P6.2); 在其他地方 (测试) 创建的分类可为 [NO_TITLE].
 */
data class SettingCategory(
    val id: String,
    val order: Int,
    val defaultSelected: Boolean = true,
    val sensitive: Boolean = false,
    val kind: Kind = Kind.SETTINGS,
    val titleRes: Int = NO_TITLE,
) {

    init {
        require(ID_PATTERN.matches(id)) { "Invalid category id \"$id\"" }
        require(!sensitive || !defaultSelected) { "$id: a sensitive category is never selected by default (D2)" }
    }

    enum class Kind {
        SETTINGS,
        DATA,
    }

    val isData: Boolean
        get() = kind == Kind.DATA

    override fun toString(): String = id

    companion object {

        const val NO_TITLE = 0

        private val ID_PATTERN = Regex("[a-z][a-z0-9_]*")

        @JvmField val APPEARANCE = SettingCategory("appearance", 1, titleRes = R.string.settings_transfer_category_appearance)
        @JvmField val GENERAL = SettingCategory("general", 2, titleRes = R.string.settings_transfer_category_general)
        @JvmField val EXPLORER = SettingCategory("explorer", 3, titleRes = R.string.settings_transfer_category_explorer)
        @JvmField val HISTORY = SettingCategory("history", 4, titleRes = R.string.settings_transfer_category_history)
        @JvmField val EDITOR_GENERAL = SettingCategory("editor_general", 5, titleRes = R.string.settings_transfer_category_editor_general)
        @JvmField val EDITOR_APPEARANCE = SettingCategory("editor_appearance", 6, titleRes = R.string.settings_transfer_category_editor_appearance)
        @JvmField val EDITOR_LOG = SettingCategory("editor_log", 7, titleRes = R.string.settings_transfer_category_editor_log)
        @JvmField val EDITOR_ASSISTANCE = SettingCategory("editor_assistance", 8, titleRes = R.string.settings_transfer_category_editor_assistance)
        @JvmField val EDITOR_SMART_HINT = SettingCategory("editor_smart_hint", 9, titleRes = R.string.settings_transfer_category_editor_smart_hint)
        @JvmField val EDITOR_GESTURES = SettingCategory("editor_gestures", 10, titleRes = R.string.settings_transfer_category_editor_gestures)
        @JvmField val EDITOR_GUTTER = SettingCategory("editor_gutter", 11, titleRes = R.string.settings_transfer_category_editor_gutter)
        @JvmField val EDITOR_PERFORMANCE = SettingCategory("editor_performance", 12, titleRes = R.string.settings_transfer_category_editor_performance)
        @JvmField val EDITOR_KEYBOARD = SettingCategory("editor_keyboard", 13, titleRes = R.string.settings_transfer_category_editor_keyboard)
        @JvmField val TIMED_TASK = SettingCategory("timed_task", 14, titleRes = R.string.settings_transfer_category_timed_task)
        @JvmField val A11Y = SettingCategory("a11y", 15, titleRes = R.string.settings_transfer_category_a11y)
        @JvmField val RUNNING = SettingCategory("running", 16, titleRes = R.string.settings_transfer_category_running)
        @JvmField val RECORD = SettingCategory("record", 17, titleRes = R.string.settings_transfer_category_record)
        @JvmField val EXTENSIBILITY = SettingCategory("extensibility", 18, titleRes = R.string.settings_transfer_category_extensibility)
        @JvmField val UPDATES = SettingCategory("updates", 19, titleRes = R.string.settings_transfer_category_updates)
        @JvmField val CONSOLE = SettingCategory("console", 20, titleRes = R.string.settings_transfer_category_console)
        @JvmField val CONNECTION = SettingCategory("connection", 21, titleRes = R.string.settings_transfer_category_connection)
        @JvmField val LAYOUT_INSPECTOR = SettingCategory("layout_inspector", 22, titleRes = R.string.settings_transfer_category_layout_inspector)

        /** The remembered positions of the floating windows (appendix C Q18). */
        @JvmField val FLOATING = SettingCategory("floating", 23, titleRes = R.string.settings_transfer_category_floating)

        /** Not selected by default: debugging switches should not travel to another device (appendix A.1). */
        @JvmField val DEVELOPER = SettingCategory("developer", 24, defaultSelected = false, titleRes = R.string.settings_transfer_category_developer)
        @JvmField val PLUGIN_CENTER = SettingCategory("plugin_center", 25, titleRes = R.string.settings_transfer_category_plugin_center)
        @JvmField val PLUGIN_ENABLE_STATE = SettingCategory("plugin_enable_state", 26, titleRes = R.string.settings_transfer_category_plugin_enable_state)
        @JvmField val APK_BUILD = SettingCategory("apk_build", 27, titleRes = R.string.settings_transfer_category_apk_build)
        @JvmField val WIZARDS = SettingCategory("wizards", 28, titleRes = R.string.settings_transfer_category_wizards)
        @JvmField val DATA_SHORTCUTS = SettingCategory("data_shortcuts", 29, kind = Kind.DATA, titleRes = R.string.settings_transfer_category_data_shortcuts)
        @JvmField val DATA_COLOR_LIBRARY = SettingCategory("data_color_library", 30, kind = Kind.DATA, titleRes = R.string.settings_transfer_category_data_color_library)

        /** "Credentials and authorizations" (D2): sensitive, never selected by default, left out of scripts and auto backups. */
        @JvmField val CREDENTIALS = SettingCategory("credentials", 31, defaultSelected = false, sensitive = true, titleRes = R.string.settings_transfer_category_credentials)

        /** "Other (internal state)" (D7, T2): every `StateKey` and the unregistered keys found at run time. */
        @JvmField val OTHER = SettingCategory("other", 32, defaultSelected = false, titleRes = R.string.settings_transfer_category_other)

        /** The 32 categories of appendix A.1 in page order. */
        @JvmField
        val ALL: List<SettingCategory> = listOf(
            APPEARANCE, GENERAL, EXPLORER, HISTORY, EDITOR_GENERAL, EDITOR_APPEARANCE, EDITOR_LOG, EDITOR_ASSISTANCE,
            EDITOR_SMART_HINT, EDITOR_GESTURES, EDITOR_GUTTER, EDITOR_PERFORMANCE, EDITOR_KEYBOARD, TIMED_TASK, A11Y,
            RUNNING, RECORD, EXTENSIBILITY, UPDATES, CONSOLE, CONNECTION, LAYOUT_INSPECTOR, FLOATING, DEVELOPER,
            PLUGIN_CENTER, PLUGIN_ENABLE_STATE, APK_BUILD, WIZARDS, DATA_SHORTCUTS, DATA_COLOR_LIBRARY, CREDENTIALS, OTHER,
        )

        private val byId: Map<String, SettingCategory> = ALL.associateBy { it.id }

        @JvmStatic
        fun ofId(id: String): SettingCategory = byId[id] ?: throw IllegalArgumentException("Unknown category id \"$id\"")

        @JvmStatic
        fun ofIdOrNull(id: String): SettingCategory? = byId[id]
    }
}
