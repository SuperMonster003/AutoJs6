package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.console.ConsoleLogFormat
import org.autojs.autojs.core.pref.DeploymentKind
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.RegisteredName
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_APPEARANCE
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_ASSISTANCE
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_GENERAL
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_GESTURES
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_GUTTER
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_LOG
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_PERFORMANCE
import org.autojs.autojs.core.pref.SettingCategory.Companion.EDITOR_SMART_HINT
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.ui.edit.editor.ace.AceEditorFont
import org.autojs.autojs.ui.edit.editor.ace.AceEditorLspPreferences
import org.autojs.autojs6.R

/**
 * Code editor settings (docs/settings/ROADMAP.md P2.3, appendix A.1 `editor_*`), the eight categories of the code
 * editor settings page: general (engine), appearance (font, theme, text size and the Ace display switches; a font that
 * is not installed is offered as the "download editor font" deployment item, D20 / appendix C Q2), log, assistance
 * (LSP), smart hints, gestures, gutter and performance thresholds. Many Ace switches are read by the Ace editor plugin
 * itself; the host only relays their changes. The keyboard category holds named stores (P3.2).
 *
 * The default preference parts of `EditorEnginePreferences` (engine and selection mode) and `EditorThresholdSettings`
 * (threshold defaults) and the threshold readers of `Pref` live here (T17); `EditorDisplayPreferences`,
 * `AceEditorFontPreferences`, `AceEditorLspPreferences` and `EditorPinchToZoomPreferences` read and write through
 * these keys.
 *
 * zh-CN: 代码编辑器设置 (ROADMAP P2.3, 附录 A.1 `editor_*`), 即代码编辑器设置页的八个分类: 常规 (引擎), 外观 (字体, 主题, 字号与
 * Ace 显示开关; 字体未安装时提出部署项 "下载编辑器字体", D20 / 附录 C Q2), 日志, 辅助 (LSP), 智能提示, 操作手势, 边栏标记,
 * 性能阈值. 许多 Ace 开关由 Ace 编辑器插件自行读取, 宿主只转发变更. 符号键盘分类为命名存储 (P3.2). `EditorEnginePreferences`
 * (引擎与选择模式) 与 `EditorThresholdSettings` (阈值默认值) 的默认偏好部分以及 `Pref` 的阈值读取并入本对象 (T17);
 * `EditorDisplayPreferences`, `AceEditorFontPreferences`, `AceEditorLspPreferences`, `EditorPinchToZoomPreferences` 经这些键读写.
 */
object EditorKeys : SettingsDomain {

    /** "No threshold" of the performance thresholds: always on (code highlight) or always off (large file mode). */
    const val NO_THRESHOLD = -1

    private val EDITOR_ENGINE_VALUES = intArrayOf(R.string.key_editor_engine_ace, R.string.key_editor_engine_native)
    private val THEME_MODE_VALUES = intArrayOf(R.string.key_editor_theme_mode_separate, R.string.key_editor_theme_mode_unified)
    private val WORD_WRAP_INDENT_STYLE_VALUES = intArrayOf(R.string.key_editor_word_wrap_indent_style_classic, R.string.key_editor_word_wrap_indent_style_continuation)
    private val LOG_BUTTON_BEHAVIOR_VALUES = intArrayOf(R.string.key_editor_log_button_behavior_bottom_sheet, R.string.key_editor_log_button_behavior_fullscreen_page)
    private val GUTTER_WIDTH_MODE_VALUES = intArrayOf(R.string.key_editor_gutter_width_mode_dynamic, R.string.key_editor_gutter_width_mode_fixed)
    private val COMPLETION_TRIGGER_VALUES = intArrayOf(
        R.string.key_ace_smart_hint_completion_trigger_dot,
        R.string.key_ace_smart_hint_completion_trigger_identifier,
        R.string.key_ace_smart_hint_completion_trigger_off,
    )
    private val COMPLETION_MIN_CHARS_VALUES = intArrayOf(
        R.string.key_ace_smart_hint_completion_min_chars_1,
        R.string.key_ace_smart_hint_completion_min_chars_2,
        R.string.key_ace_smart_hint_completion_min_chars_3,
    )
    private val COMPLETION_DELAY_VALUES = intArrayOf(
        R.string.key_ace_smart_hint_completion_delay_0,
        R.string.key_ace_smart_hint_completion_delay_100,
        R.string.key_ace_smart_hint_completion_delay_250,
    )
    private val SIGNATURE_TRIGGER_VALUES = intArrayOf(
        R.string.key_ace_smart_hint_signature_trigger_cursor,
        R.string.key_ace_smart_hint_signature_trigger_off,
        R.string.key_ace_smart_hint_signature_trigger_typing,
    )

    // Font ids of the bundled catalog; the font key itself takes any valid id (fonts are downloaded).
    private val FONT_ID_VALUES = intArrayOf(
        R.string.key_ace_editor_font_cascadia_code,
        R.string.key_ace_editor_font_fira_code,
        R.string.key_ace_editor_font_hack,
        R.string.key_ace_editor_font_ibm_plex_mono,
        R.string.key_ace_editor_font_intel_one_mono,
        R.string.key_ace_editor_font_iosevka,
        R.string.key_ace_editor_font_iosevka_slab,
        R.string.key_ace_editor_font_jetbrains_mono,
        R.string.key_ace_editor_font_source_code_pro,
        R.string.key_ace_editor_font_system_monospace,
    )

    // Values of key_editor_pinch_to_zoom_strategy, the legacy pinch strategy kept as a state (P4.2).
    private val PINCH_TO_ZOOM_STRATEGY_VALUES = intArrayOf(
        R.string.key_editor_pinch_to_zoom_change_text_size,
        R.string.key_editor_pinch_to_zoom_disable,
        R.string.key_editor_pinch_to_zoom_scale_view,
    )

    // ------------------------------------------------------------------------------------------------- general

    /** The effective editor engine, kept in step with the selection mode by `EditorEnginePreferences`. */
    @JvmField
    val EDITOR_ENGINE = SettingKey.Enum(
        R.string.key_editor_engine, EDITOR_GENERAL, keyChoices(*EDITOR_ENGINE_VALUES),
        keyResource(R.string.default_key_editor_engine, "default_key_editor_engine"),
    )

    /** How the engine was chosen (`EditorEngineSelectionPolicy.Mode`); missing for installs before the mode existed. */
    @JvmField
    val EDITOR_ENGINE_SELECTION_MODE = SettingKey.Str(name = "key_\$_editor_engine_selection_mode", category = EDITOR_GENERAL, default = SettingDefault.None)

    // ------------------------------------------------------------------------------------------------- appearance

    /** Font id of the Ace editor; a font not installed on import is a deployment item (D20 / Q2). */
    @JvmField
    val ACE_EDITOR_FONT = SettingKey.Str(
        R.string.key_ace_editor_font, EDITOR_APPEARANCE,
        SettingDefault.computed("AceEditorFont.IOSEVKA.preferenceValue") { AceEditorFont.IOSEVKA.preferenceValue },
        deployment = DeploymentKind.DOWNLOAD_EDITOR_FONT,
    )

    @JvmField
    val ACE_FONT_LIGATURES_ENABLED = SettingKey.Bool(R.string.key_ace_font_ligatures_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_font_ligatures_enabled, "pref_ace_font_ligatures_enabled"))

    @JvmField
    val ACE_FONT_STYLES_ENABLED = SettingKey.Bool(R.string.key_ace_font_styles_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_font_styles_enabled, "pref_ace_font_styles_enabled"))

    @JvmField
    val ACE_PRINT_MARGIN_ENABLED = SettingKey.Bool(R.string.key_ace_print_margin_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_print_margin_enabled, "pref_ace_print_margin_enabled"))

    @JvmField
    val ACE_INDENT_GUIDES_ENABLED = SettingKey.Bool(R.string.key_ace_indent_guides_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_indent_guides_enabled, "pref_ace_indent_guides_enabled"))

    @JvmField
    val ACE_HIGHLIGHT_INDENT_GUIDES_ENABLED = SettingKey.Bool(R.string.key_ace_highlight_indent_guides_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_highlight_indent_guides_enabled, "pref_ace_highlight_indent_guides_enabled"))

    @JvmField
    val ACE_WORD_WRAP_ENABLED = SettingKey.Bool(R.string.key_ace_word_wrap_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_word_wrap_enabled, "pref_ace_word_wrap_enabled"))

    @JvmField
    val EDITOR_WORD_WRAP_INDENT_STYLE = SettingKey.Enum(
        R.string.key_editor_word_wrap_indent_style, EDITOR_APPEARANCE, keyChoices(*WORD_WRAP_INDENT_STYLE_VALUES),
        keyResource(R.string.default_key_editor_word_wrap_indent_style, "default_key_editor_word_wrap_indent_style"),
    )

    @JvmField
    val ACE_WHITESPACES_IN_SELECTION_ENABLED = SettingKey.Bool(R.string.key_ace_whitespaces_in_selection_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_whitespaces_in_selection_enabled, "pref_ace_whitespaces_in_selection_enabled"))

    @JvmField
    val ACE_CUSTOM_SCROLLBAR_ENABLED = SettingKey.Bool(R.string.key_ace_custom_scrollbar_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_custom_scrollbar_enabled, "pref_ace_custom_scrollbar_enabled"))

    @JvmField
    val ACE_SCROLLBAR_AUTO_HIDE_ENABLED = SettingKey.Bool(R.string.key_ace_scrollbar_auto_hide_enabled, EDITOR_APPEARANCE, boolResource(R.bool.pref_ace_scrollbar_auto_hide_enabled, "pref_ace_scrollbar_auto_hide_enabled"))

    @JvmField
    val EDITOR_FULLSCREEN_MODE = SettingKey.Bool(R.string.key_editor_fullscreen_mode, EDITOR_APPEARANCE, boolResource(R.bool.pref_editor_fullscreen_mode, "pref_editor_fullscreen_mode"))

    /** Text size in sp of both editors; 14 when not set (appendix C Q10). */
    @JvmField
    val EDITOR_TEXT_SIZE = SettingKey.Int(
        R.string.key_editor_text_size, EDITOR_APPEARANCE,
        intResource(R.integer.editor_text_size_default_value, "editor_text_size_default_value"),
    )

    /** The theme of the unified mode; missing means the engine default (`Themes.getCurrentThemeName`). */
    @JvmField
    val EDITOR_THEME = SettingKey.Str(R.string.key_editor_theme, EDITOR_APPEARANCE, SettingDefault.None)

    @JvmField
    val EDITOR_THEME_LIGHT = SettingKey.Str(R.string.key_editor_theme_light, EDITOR_APPEARANCE, SettingDefault.None)

    @JvmField
    val EDITOR_THEME_DARK = SettingKey.Str(R.string.key_editor_theme_dark, EDITOR_APPEARANCE, SettingDefault.None)

    @JvmField
    val EDITOR_THEME_MODE = SettingKey.Enum(
        R.string.key_editor_theme_mode, EDITOR_APPEARANCE, keyChoices(*THEME_MODE_VALUES),
        keyResource(R.string.default_key_editor_theme_mode, "default_key_editor_theme_mode"),
    )

    // ------------------------------------------------------------------------------------------------- log

    @JvmField
    val EDITOR_AUTO_SHOW_LOG_BOTTOM_SHEET_ON_RUN = SettingKey.Bool(R.string.key_editor_auto_show_log_bottom_sheet_on_run, EDITOR_LOG, boolResource(R.bool.pref_editor_auto_show_log_bottom_sheet_on_run, "pref_editor_auto_show_log_bottom_sheet_on_run"))

    @JvmField
    val EDITOR_LOG_BUTTON_BEHAVIOR = SettingKey.Enum(
        R.string.key_editor_log_button_behavior, EDITOR_LOG, keyChoices(*LOG_BUTTON_BEHAVIOR_VALUES),
        keyResource(R.string.default_key_editor_log_button_behavior, "default_key_editor_log_button_behavior"),
    )

    /** Settings of the log panel of the editor (`LogBottomSheet`; time display through `ConsoleTimeSettings.EDITOR_PANEL`). */
    @JvmField
    val EDITOR_LOG_PANEL_INPUT_BAR_VISIBLE = SettingKey.Bool(R.string.key_editor_log_panel_input_bar_visible, EDITOR_LOG, SettingDefault.of(false))

    @JvmField
    val EDITOR_LOG_PANEL_TIME_VISIBLE = SettingKey.Bool(R.string.key_editor_log_panel_time_visible, EDITOR_LOG, SettingDefault.of(true))

    @JvmField
    val EDITOR_LOG_PANEL_TIME_FORMAT = SettingKey.Str(R.string.key_editor_log_panel_time_format, EDITOR_LOG, SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN))

    @JvmField
    val EDITOR_LOG_PANEL_TYPESCRIPT_CONSOLE_VISIBLE = SettingKey.Bool(R.string.key_editor_log_panel_typescript_console_visible, EDITOR_LOG, SettingDefault.of(false))

    // ------------------------------------------------------------------------------------------------- assistance

    @JvmField
    val ACE_LSP_ENABLED = SettingKey.Bool(R.string.key_ace_lsp_enabled, EDITOR_ASSISTANCE, boolResource(R.bool.pref_ace_lsp_enabled, "pref_ace_lsp_enabled"))

    /**
     * File types (comma separated extensions) the LSP serves; read only when present, otherwise `DEFAULT_FILE_TYPES`
     * of `AceEditorLspPreferences`; the format revision is a state (`key_$_ace_lsp_file_types_revision`, P4.2).
     * zh-CN: LSP 服务的文件类型 (逗号分隔的扩展名); 存在时才读取, 否则为 `AceEditorLspPreferences` 的 `DEFAULT_FILE_TYPES`;
     * 格式版本号为状态 (P4.2).
     */
    @JvmField
    val ACE_LSP_FILE_TYPES = SettingKey.Str(
        R.string.key_ace_lsp_file_types, EDITOR_ASSISTANCE,
        SettingDefault.computed("DEFAULT_FILE_TYPES") { AceEditorLspPreferences.DEFAULT_FILE_TYPES.joinToString(",") },
    )

    /** Declaration groups (comma separated); read only when present, otherwise `DEFAULT_DECLARATION_GROUPS`. */
    @JvmField
    val ACE_LSP_DECLARATION_GROUPS = SettingKey.Str(
        R.string.key_ace_lsp_declaration_groups, EDITOR_ASSISTANCE,
        SettingDefault.computed("DEFAULT_DECLARATION_GROUPS") { AceEditorLspPreferences.DEFAULT_DECLARATION_GROUPS.joinToString(",") },
    )

    @JvmField
    val ACE_SEMANTIC_TYPESCRIPT_ENABLED = SettingKey.Bool(
        name = "key_\$_ace_semantic_typescript_enabled", category = EDITOR_ASSISTANCE,
        default = SettingDefault.computed("DEFAULT_SEMANTIC_LANGUAGES[typescript]") { AceEditorLspPreferences.DEFAULT_SEMANTIC_LANGUAGES.getValue("typescript") },
    )

    @JvmField
    val ACE_SEMANTIC_PYTHON_ENABLED = SettingKey.Bool(
        name = "key_\$_ace_semantic_python_enabled", category = EDITOR_ASSISTANCE,
        default = SettingDefault.computed("DEFAULT_SEMANTIC_LANGUAGES[python]") { AceEditorLspPreferences.DEFAULT_SEMANTIC_LANGUAGES.getValue("python") },
    )

    @JvmField
    val ACE_SEMANTIC_LUA_ENABLED = SettingKey.Bool(
        name = "key_\$_ace_semantic_lua_enabled", category = EDITOR_ASSISTANCE,
        default = SettingDefault.computed("DEFAULT_SEMANTIC_LANGUAGES[lua]") { AceEditorLspPreferences.DEFAULT_SEMANTIC_LANGUAGES.getValue("lua") },
    )

    @JvmField
    val ACE_SEMANTIC_JAVA_ENABLED = SettingKey.Bool(
        name = "key_\$_ace_semantic_java_enabled", category = EDITOR_ASSISTANCE,
        default = SettingDefault.computed("DEFAULT_SEMANTIC_LANGUAGES[java]") { AceEditorLspPreferences.DEFAULT_SEMANTIC_LANGUAGES.getValue("java") },
    )

    @JvmField
    val ACE_SEMANTIC_KOTLIN_ENABLED = SettingKey.Bool(
        name = "key_\$_ace_semantic_kotlin_enabled", category = EDITOR_ASSISTANCE,
        default = SettingDefault.computed("DEFAULT_SEMANTIC_LANGUAGES[kotlin]") { AceEditorLspPreferences.DEFAULT_SEMANTIC_LANGUAGES.getValue("kotlin") },
    )

    // ------------------------------------------------------------------------------------------------- smart hints

    @JvmField
    val ACE_SMART_HINT_COMPLETION_TRIGGER = SettingKey.Enum(
        R.string.key_ace_smart_hint_completion_trigger, EDITOR_SMART_HINT, keyChoices(*COMPLETION_TRIGGER_VALUES),
        keyResource(R.string.default_key_ace_smart_hint_completion_trigger, "default_key_ace_smart_hint_completion_trigger"),
    )

    @JvmField
    val ACE_SMART_HINT_COMPLETION_MIN_CHARS = SettingKey.Enum(
        R.string.key_ace_smart_hint_completion_min_chars, EDITOR_SMART_HINT, keyChoices(*COMPLETION_MIN_CHARS_VALUES),
        keyResource(R.string.default_key_ace_smart_hint_completion_min_chars, "default_key_ace_smart_hint_completion_min_chars"),
    )

    @JvmField
    val ACE_SMART_HINT_COMPLETION_DELAY = SettingKey.Enum(
        R.string.key_ace_smart_hint_completion_delay, EDITOR_SMART_HINT, keyChoices(*COMPLETION_DELAY_VALUES),
        keyResource(R.string.default_key_ace_smart_hint_completion_delay, "default_key_ace_smart_hint_completion_delay"),
    )

    @JvmField
    val ACE_SMART_HINT_SIGNATURE_TRIGGER = SettingKey.Enum(
        R.string.key_ace_smart_hint_signature_trigger, EDITOR_SMART_HINT, keyChoices(*SIGNATURE_TRIGGER_VALUES),
        keyResource(R.string.default_key_ace_smart_hint_signature_trigger, "default_key_ace_smart_hint_signature_trigger"),
    )

    @JvmField
    val ACE_SMART_HINT_ARROW_OVERLOAD_CYCLING = SettingKey.Bool(R.string.key_ace_smart_hint_arrow_overload_cycling, EDITOR_SMART_HINT, boolResource(R.bool.pref_ace_smart_hint_arrow_overload_cycling, "pref_ace_smart_hint_arrow_overload_cycling"))

    @JvmField
    val ACE_SYMBOL_HIGHLIGHT_ENABLED = SettingKey.Bool(R.string.key_ace_symbol_highlight_enabled, EDITOR_SMART_HINT, boolResource(R.bool.pref_ace_symbol_highlight_enabled, "pref_ace_symbol_highlight_enabled"))

    @JvmField
    val ACE_INLINE_COMPLETION_PREVIEW_ENABLED = SettingKey.Bool(R.string.key_ace_inline_completion_preview_enabled, EDITOR_SMART_HINT, boolResource(R.bool.pref_ace_inline_completion_preview_enabled, "pref_ace_inline_completion_preview_enabled"))

    // ------------------------------------------------------------------------------------------------- gestures

    @JvmField
    val ACE_TOUCH_MAGNIFIER_ENABLED = SettingKey.Bool(R.string.key_ace_touch_magnifier_enabled, EDITOR_GESTURES, boolResource(R.bool.pref_ace_touch_magnifier_enabled, "pref_ace_touch_magnifier_enabled"))

    /** Pinch to change the text size; missing on old installs, which then follow the legacy strategy state (`EditorPinchToZoomPreferences`). */
    @JvmField
    val EDITOR_PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED = SettingKey.Bool(R.string.key_editor_pinch_to_zoom_change_text_size_enabled, EDITOR_GESTURES, boolResource(R.bool.pref_editor_pinch_to_zoom_change_text_size_enabled, "pref_editor_pinch_to_zoom_change_text_size_enabled"))

    // ------------------------------------------------------------------------------------------------- gutter

    @JvmField
    val ACE_LINE_NUMBERS_ENABLED = SettingKey.Bool(R.string.key_ace_line_numbers_enabled, EDITOR_GUTTER, boolResource(R.bool.pref_ace_line_numbers_enabled, "pref_ace_line_numbers_enabled"))

    @JvmField
    val ACE_SVG_GUTTER_ICONS_ENABLED = SettingKey.Bool(R.string.key_ace_svg_gutter_icons_enabled, EDITOR_GUTTER, boolResource(R.bool.pref_ace_svg_gutter_icons_enabled, "pref_ace_svg_gutter_icons_enabled"))

    @JvmField
    val EDITOR_BREAKPOINT_MARKERS_ENABLED = SettingKey.Bool(R.string.key_editor_breakpoint_markers_enabled, EDITOR_GUTTER, SettingDefault.of(true))

    @JvmField
    val EDITOR_FOLD_MARKERS_ENABLED = SettingKey.Bool(R.string.key_editor_fold_markers_enabled, EDITOR_GUTTER, SettingDefault.of(true))

    @JvmField
    val EDITOR_GUTTER_WIDTH_MODE = SettingKey.Enum(
        R.string.key_editor_gutter_width_mode, EDITOR_GUTTER, keyChoices(*GUTTER_WIDTH_MODE_VALUES),
        keyResource(R.string.default_key_editor_gutter_width_mode, "default_key_editor_gutter_width_mode"),
    )

    // ------------------------------------------------------------------------------------------------- performance

    /** Code highlight up to this many bytes (native editor); 0 never, [NO_THRESHOLD] always. */
    @JvmField
    val EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES = SettingKey.Int(
        R.string.key_editor_code_highlight_threshold_bytes, EDITOR_PERFORMANCE,
        intResource(R.integer.editor_code_highlight_threshold_default_value, "editor_code_highlight_threshold_default_value"),
    )

    /** Large file mode above this many bytes (native editor); 0 always, [NO_THRESHOLD] never. */
    @JvmField
    val EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES = SettingKey.Int(
        R.string.key_editor_large_file_mode_threshold_bytes, EDITOR_PERFORMANCE,
        intResource(R.integer.editor_large_file_mode_threshold_default_value, "editor_large_file_mode_threshold_default_value"),
    )

    override val keys: List<PrefKey<*>> = listOf(
        EDITOR_ENGINE, EDITOR_ENGINE_SELECTION_MODE,
        ACE_EDITOR_FONT, ACE_FONT_LIGATURES_ENABLED, ACE_FONT_STYLES_ENABLED, ACE_PRINT_MARGIN_ENABLED, ACE_INDENT_GUIDES_ENABLED,
        ACE_HIGHLIGHT_INDENT_GUIDES_ENABLED, ACE_WORD_WRAP_ENABLED, EDITOR_WORD_WRAP_INDENT_STYLE, ACE_WHITESPACES_IN_SELECTION_ENABLED,
        ACE_CUSTOM_SCROLLBAR_ENABLED, ACE_SCROLLBAR_AUTO_HIDE_ENABLED, EDITOR_FULLSCREEN_MODE, EDITOR_TEXT_SIZE,
        EDITOR_THEME, EDITOR_THEME_LIGHT, EDITOR_THEME_DARK, EDITOR_THEME_MODE,
        EDITOR_AUTO_SHOW_LOG_BOTTOM_SHEET_ON_RUN, EDITOR_LOG_BUTTON_BEHAVIOR, EDITOR_LOG_PANEL_INPUT_BAR_VISIBLE,
        EDITOR_LOG_PANEL_TIME_VISIBLE, EDITOR_LOG_PANEL_TIME_FORMAT, EDITOR_LOG_PANEL_TYPESCRIPT_CONSOLE_VISIBLE,
        ACE_LSP_ENABLED, ACE_LSP_FILE_TYPES, ACE_LSP_DECLARATION_GROUPS,
        ACE_SEMANTIC_TYPESCRIPT_ENABLED, ACE_SEMANTIC_PYTHON_ENABLED, ACE_SEMANTIC_LUA_ENABLED, ACE_SEMANTIC_JAVA_ENABLED, ACE_SEMANTIC_KOTLIN_ENABLED,
        ACE_SMART_HINT_COMPLETION_TRIGGER, ACE_SMART_HINT_COMPLETION_MIN_CHARS, ACE_SMART_HINT_COMPLETION_DELAY, ACE_SMART_HINT_SIGNATURE_TRIGGER,
        ACE_SMART_HINT_ARROW_OVERLOAD_CYCLING, ACE_SYMBOL_HIGHLIGHT_ENABLED, ACE_INLINE_COMPLETION_PREVIEW_ENABLED,
        ACE_TOUCH_MAGNIFIER_ENABLED, EDITOR_PINCH_TO_ZOOM_CHANGE_TEXT_SIZE_ENABLED,
        ACE_LINE_NUMBERS_ENABLED, ACE_SVG_GUTTER_ICONS_ENABLED, EDITOR_BREAKPOINT_MARKERS_ENABLED, EDITOR_FOLD_MARKERS_ENABLED, EDITOR_GUTTER_WIDTH_MODE,
        EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES, EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES,
    )

    // key_ace_smart_hint_signature_trigger_off has the value "off" of key_ace_smart_hint_completion_trigger_off, so the
    // name is registered once.
    // zh-CN: key_ace_smart_hint_signature_trigger_off 与 key_ace_smart_hint_completion_trigger_off 的值都是 "off", 只登记一次.
    override val names: List<RegisteredName> = valueConstants(
        *EDITOR_ENGINE_VALUES, *THEME_MODE_VALUES, *WORD_WRAP_INDENT_STYLE_VALUES, *LOG_BUTTON_BEHAVIOR_VALUES, *GUTTER_WIDTH_MODE_VALUES,
        *COMPLETION_TRIGGER_VALUES, *COMPLETION_MIN_CHARS_VALUES, *COMPLETION_DELAY_VALUES,
        R.string.key_ace_smart_hint_signature_trigger_cursor, R.string.key_ace_smart_hint_signature_trigger_typing,
        *FONT_ID_VALUES, *PINCH_TO_ZOOM_STRATEGY_VALUES,
    ) + excludedNames(
        // Entries of the settings page that hold no value (the semantic languages are the five keys above).
        R.string.key_ace_language_support_matrix,
        R.string.key_ace_semantic_languages,
        R.string.key_editor_shortcut_input_bar,
    )

    // Readers of the former Pref thresholds and of EditorThresholdSettings (T17).
    // zh-CN: 原 Pref 阈值读取与 EditorThresholdSettings 的读取函数 (T17).

    /**
     * The code highlight threshold as the former `Pref.codeHighlightThresholdChars` read it: values below [NO_THRESHOLD] fall
     * back to the default, other negative values are not possible.
     * zh-CN: 代码高亮阈值, 读法同原 `Pref.codeHighlightThresholdChars`: 小于 [NO_THRESHOLD] 的值回退为默认值.
     */
    @JvmStatic
    val codeHighlightThresholdChars: Int
        get() = normalizedThreshold(EDITOR_CODE_HIGHLIGHT_THRESHOLD_BYTES)

    @JvmStatic
    val largeFileModeThresholdBytes: Int
        get() = normalizedThreshold(EDITOR_LARGE_FILE_MODE_THRESHOLD_BYTES)

    @JvmStatic
    fun isCodeHighlightEnabledForLength(length: Int): Boolean = when (val threshold = codeHighlightThresholdChars) {
        NO_THRESHOLD -> true
        0 -> false
        else -> length <= threshold
    }

    @JvmStatic
    fun shouldEnableLargeFileModeForSize(size: Long): Boolean = when (val threshold = largeFileModeThresholdBytes) {
        NO_THRESHOLD -> false
        0 -> true
        else -> size > threshold.toLong()
    }

    private fun normalizedThreshold(key: SettingKey.Int): Int {
        val value = key.get()
        return when {
            value < NO_THRESHOLD -> key.defaultOrNull()!!
            value == NO_THRESHOLD -> NO_THRESHOLD
            value <= 0 -> 0
            else -> value
        }
    }
}
