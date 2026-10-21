package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.CacheKey
import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_KEYBOARD
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingType
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.StateKey
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFontSortField
import org.autojs.autojs.ui.edit.keyboard.SymbolsConfigStore

/**
 * Code editor > symbol keyboard (docs/settings/ROADMAP.md P3.2, appendix A.1 `editor_keyboard`): the named stores of the
 * editor keyboards and of the font dialog, each exported as a whole ([NamedStores]).
 *
 * - `editor_symbols_config` (`SymbolsConfigStore`): the active symbol profile and the profiles as org.json text.
 * - `ace_editor_font_ui_preferences` (`AceEditorFontUiPreferences`): sort mode, type locks, preview filters and the
 *   selected variant per font group (P2.3 left this store here).
 * - `FunctionsKeyboardHelper`: only the measured height of the soft keyboard of this device, a cache (appendix C Q19).
 * - `ace_action_history` (`AceActionHistoryStore`): usage history of the Ace actions, a state.
 *
 * Names are unchanged (R1); readers keep the fallbacks they passed and org.json texts are still written by the same
 * code ([PrefKey.storedText], R2).
 *
 * zh-CN: 代码编辑器 > 符号键盘 (ROADMAP P3.2, 附录 A.1 `editor_keyboard`): 编辑器键盘与字体对话框的命名存储, 各自整份导出. 符号配置
 * (当前配置档与 org.json 文本的配置档), 字体界面偏好 (排序, 类型锁定, 预览筛选, 每个字体组选中的变体; P2.3 留到此处),
 * `FunctionsKeyboardHelper` (只有本机测得的软键盘高度, 为缓存, 附录 C Q19), Ace 动作历史 (状态). 名称不变 (R1); 读取方保留原先
 * 传入的回退值, org.json 文本仍由原代码读写 (R2).
 */
object EditorKeyboardKeys : SettingsDomain {

    // ------------------------------------------------------------------------------------ editor_symbols_config

    /** The active symbol profile (`SymbolsConfigStore.getActiveProfileName`); missing means the default profile. */
    @JvmField
    val SYMBOLS_ACTIVE_PROFILE = SettingKey.Str(
        name = "active_profile", category = EDITOR_KEYBOARD, default = SettingDefault.of(SymbolsConfigStore.PROFILE_DEFAULT_ID),
        store = NamedStores.EDITOR_SYMBOLS_CONFIG,
    )

    /** The symbol profiles as an org.json object text ({"profiles": {...}}). */
    @JvmField
    val SYMBOLS_PROFILES = SettingKey.Json(
        name = "profiles_json", category = EDITOR_KEYBOARD, jsonType = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None,
        store = NamedStores.EDITOR_SYMBOLS_CONFIG,
    )

    // ---------------------------------------------------------------------------- ace_editor_font_ui_preferences

    /** Sort mode of the font list (`AceEditorFontSortMode.name`); missing means the default mode. */
    @JvmField
    val FONT_SORT_MODE = SettingKey.Str(
        name = "key_\$_ace_editor_font_sort_mode", category = EDITOR_KEYBOARD, default = SettingDefault.None,
        store = NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES,
    )

    /** Fonts sorted by name keep their types together; removed while it equals the default of the field. */
    @JvmField
    val FONT_NAME_SORT_TYPE_LOCKED = SettingKey.Bool(
        name = "key_\$_ace_editor_font_name_sort_type_locked", category = EDITOR_KEYBOARD, store = NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES,
        default = SettingDefault.computed("AceEditorFontSortField.NAME.typeLockedByDefault") { AceEditorFontSortField.NAME.typeLockedByDefault },
    )

    /** Fonts sorted by size keep their types together; removed while it equals the default of the field. */
    @JvmField
    val FONT_SIZE_SORT_TYPE_LOCKED = SettingKey.Bool(
        name = "key_\$_ace_editor_font_size_sort_type_locked", category = EDITOR_KEYBOARD, store = NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES,
        default = SettingDefault.computed("AceEditorFontSortField.SIZE.typeLockedByDefault") { AceEditorFontSortField.SIZE.typeLockedByDefault },
    )

    /** Font features left out of the preview (`FontFeature.catalogValue`, "other" for fonts without a named feature). */
    @JvmField
    val FONT_PREVIEW_EXCLUDED_FEATURES = SettingKey.StrSet(
        name = "key_\$_ace_editor_font_preview_excluded_features", category = EDITOR_KEYBOARD, default = SettingDefault.of(emptySet()),
        store = NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES,
    )

    /** Font types left out of the preview (`AceEditorFontType.storageValue`). */
    @JvmField
    val FONT_PREVIEW_EXCLUDED_TYPES = SettingKey.StrSet(
        name = "key_\$_ace_editor_font_preview_excluded_types", category = EDITOR_KEYBOARD, default = SettingDefault.of(emptySet()),
        store = NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES,
    )

    /** The selected variant (font id) per font group id. */
    @JvmField
    val FONT_SELECTED_VARIANT = KeyFamily("key_\$_ace_editor_font_selected_variant_") { name ->
        SettingKey.Str(name = name, category = EDITOR_KEYBOARD, default = SettingDefault.None, store = NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES)
    }

    // --------------------------------------------------------------------------------- FunctionsKeyboardHelper

    /**
     * Height in pixels of the soft keyboard last measured on this device (`FunctionsKeyboardHelper`), 400 until
     * measured; a cache, never exported (appendix C Q19).
     * zh-CN: 本机最近测得的软键盘高度 (像素), 测得之前为 400; 为缓存, 不导出 (附录 C Q19).
     */
    @JvmField
    val SOFT_INPUT_HEIGHT = CacheKey.int(name = "soft_input_height", default = SettingDefault.of(400), store = NamedStores.FUNCTIONS_KEYBOARD_HELPER)

    // -------------------------------------------------------------------------------------- ace_action_history

    /** Usage history of the Ace editor actions as an org.json object text (`AceActionHistoryStore`). */
    @JvmField
    val ACE_ACTION_HISTORY = StateKey.json(
        name = "state_json", type = SettingType.Json(SettingsJson.OBJECT), default = SettingDefault.None, store = NamedStores.ACE_ACTION_HISTORY,
    )

    override val keys: List<PrefKey<*>> = listOf(
        SYMBOLS_ACTIVE_PROFILE, SYMBOLS_PROFILES,
        FONT_SORT_MODE, FONT_NAME_SORT_TYPE_LOCKED, FONT_SIZE_SORT_TYPE_LOCKED, FONT_PREVIEW_EXCLUDED_FEATURES, FONT_PREVIEW_EXCLUDED_TYPES,
        SOFT_INPUT_HEIGHT, ACE_ACTION_HISTORY,
    )

    override val families: List<KeyFamily<*, *>> = listOf(FONT_SELECTED_VARIANT)
}
