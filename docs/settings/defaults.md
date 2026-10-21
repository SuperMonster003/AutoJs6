# 设置默认值表 (defaults)

由 `tools/settings/inventory.py` 生成, 不要手工编辑; 人工补充的来源写在 `tools/settings/classification.json` 的 `extraDefaults`. 路线图见 [ROADMAP.md](ROADMAP.md) (P0.4, 红线 R2).

- 生成日期: 2026-10-21, 扫描基线: `master@c30622af6`
- 来源形式: `R.bool` / `R.integer` / `R.string` (含 `default_key_*` 别名) / 伴生常量 / 调用处字面量 / XML 字面量 (设置页 `app:defaultValue`); "表达式" 表示运行时计算, 取值列写明依据.
- 自动提取规则: `getBoolean` / `getInt` / `getLong` / `getFloat` / `getString` / `getStringSet` 等调用的第一个参数解析为键 (`R.string.key_*`, 常量或局部变量), 第二个参数经资源, 常量与局部变量求值; 设置页的 `app:defaultValue` 与 `app:itemDefaultKey` 单独成行. 人工补充行标 "(人工)".
- 范围: kind 为 Setting 的 `key_*` 键与默认偏好中的字面量键; 命名存储整份作为条目, 其内部默认值不在本表.

## 1. 默认值来源

| 键 | 类型 | 来源形式 | 表达式 | 取值 | 位置 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `key_ace_custom_scrollbar_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_custom_scrollbar_enabled, "pref_ace_custom_scrollbar_enabled")` | `true` | core/pref/keys/EditorKeys.kt:143 |  |
| `key_ace_custom_scrollbar_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_custom_scrollbar_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:121 |  |
| `key_ace_editor_font` | String | 伴生常量 (人工) | `AceEditorFont.IOSEVKA.preferenceValue` | `iosevka` | core/pref/keys/EditorKeys.kt:109 | 枚举 AceEditorFont.IOSEVKA, 与 `default_key_ace_editor_font` 一致 |
| `key_ace_font_ligatures_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_font_ligatures_enabled, "pref_ace_font_ligatures_enabled")` | `true` | core/pref/keys/EditorKeys.kt:116 |  |
| `key_ace_font_ligatures_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_font_ligatures_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:56 |  |
| `key_ace_font_styles_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_font_styles_enabled, "pref_ace_font_styles_enabled")` | `true` | core/pref/keys/EditorKeys.kt:119 |  |
| `key_ace_font_styles_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_font_styles_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:64 |  |
| `key_ace_highlight_indent_guides_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_highlight_indent_guides_enabled, "pref_ace_highlight_indent_guides_enabled")` | `true` | core/pref/keys/EditorKeys.kt:128 |  |
| `key_ace_highlight_indent_guides_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_highlight_indent_guides_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:88 |  |
| `key_ace_indent_guides_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_indent_guides_enabled, "pref_ace_indent_guides_enabled")` | `true` | core/pref/keys/EditorKeys.kt:125 |  |
| `key_ace_indent_guides_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_indent_guides_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:80 |  |
| `key_ace_inline_completion_preview_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_inline_completion_preview_enabled, "pref_ace_inline_completion_preview_enabled")` | `false` | core/pref/keys/EditorKeys.kt:285 |  |
| `key_ace_inline_completion_preview_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_inline_completion_preview_enabled"` | `false` | res/xml/fragment_code_editor_settings.xml:265 |  |
| `key_ace_line_numbers_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_line_numbers_enabled, "pref_ace_line_numbers_enabled")` | `true` | core/pref/keys/EditorKeys.kt:299 |  |
| `key_ace_line_numbers_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_line_numbers_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:317 |  |
| `key_ace_lsp_declaration_groups` | String | 伴生常量 (人工) | `DEFAULT_DECLARATION_GROUPS` | `[] (缺键时 DEFAULT_DECLARATION_GROUPS)` | core/pref/keys/EditorKeys.kt:217 | 读取方先判断 isStored, 缺键返回 AceEditorLspPreferences.DEFAULT_DECLARATION_GROUPS; 声明的默认值是其逗号拼接 (P2.3) |
| `key_ace_lsp_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_lsp_enabled, "pref_ace_lsp_enabled")` | `true` | core/pref/keys/EditorKeys.kt:201 |  |
| `key_ace_lsp_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_lsp_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:171 |  |
| `key_ace_lsp_file_types` | String | 伴生常量 (人工) | `DEFAULT_FILE_TYPES` | `DEFAULT_FILE_TYPES (19 个扩展名, 缺键时)` | core/pref/keys/EditorKeys.kt:210 | 读取方先判断 isStored, 缺键返回 AceEditorLspPreferences.DEFAULT_FILE_TYPES; 声明的默认值是其逗号拼接 (P2.3) |
| `key_ace_print_margin_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_print_margin_enabled, "pref_ace_print_margin_enabled")` | `false` | core/pref/keys/EditorKeys.kt:122 |  |
| `key_ace_print_margin_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_print_margin_enabled"` | `false` | res/xml/fragment_code_editor_settings.xml:72 |  |
| `key_ace_scrollbar_auto_hide_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_scrollbar_auto_hide_enabled, "pref_ace_scrollbar_auto_hide_enabled")` | `true` | core/pref/keys/EditorKeys.kt:146 |  |
| `key_ace_scrollbar_auto_hide_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_scrollbar_auto_hide_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:129 |  |
| `key_ace_smart_hint_arrow_overload_cycling` | Boolean | R.bool | `boolResource(R.bool.pref_ace_smart_hint_arrow_overload_cycling, "pref_ace_smart_hint_arrow_overload_cycling")` | `true` | core/pref/keys/EditorKeys.kt:279 |  |
| `key_ace_smart_hint_arrow_overload_cycling` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_smart_hint_arrow_overload_cycling"` | `true` | res/xml/fragment_code_editor_settings.xml:249 |  |
| `key_ace_smart_hint_completion_delay` | String (Enum) | R.string | `keyResource(R.string.default_key_ace_smart_hint_completion_delay, "default_key_ace_smart_hint_completion_delay")` | `key_ace_smart_hint_completion_delay_0 = 0` | core/pref/keys/EditorKeys.kt:267 |  |
| `key_ace_smart_hint_completion_delay` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_ace_smart_hint_completion_delay"` | `key_ace_smart_hint_completion_delay_0 = 0` | res/xml/fragment_code_editor_settings.xml:231 |  |
| `key_ace_smart_hint_completion_min_chars` | String (Enum) | R.string | `keyResource(R.string.default_key_ace_smart_hint_completion_min_chars, "default_key_ace_smart_hint_completion_min_chars")` | `key_ace_smart_hint_completion_min_chars_1 = 1` | core/pref/keys/EditorKeys.kt:261 |  |
| `key_ace_smart_hint_completion_min_chars` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_ace_smart_hint_completion_min_chars"` | `key_ace_smart_hint_completion_min_chars_1 = 1` | res/xml/fragment_code_editor_settings.xml:222 |  |
| `key_ace_smart_hint_completion_trigger` | String (Enum) | R.string | `keyResource(R.string.default_key_ace_smart_hint_completion_trigger, "default_key_ace_smart_hint_completion_trigger")` | `key_ace_smart_hint_completion_trigger_identifier = identifier` | core/pref/keys/EditorKeys.kt:255 |  |
| `key_ace_smart_hint_completion_trigger` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_ace_smart_hint_completion_trigger"` | `key_ace_smart_hint_completion_trigger_identifier = identifier` | res/xml/fragment_code_editor_settings.xml:213 |  |
| `key_ace_smart_hint_signature_trigger` | String (Enum) | R.string | `keyResource(R.string.default_key_ace_smart_hint_signature_trigger, "default_key_ace_smart_hint_signature_trigger")` | `key_ace_smart_hint_signature_trigger_cursor = cursor` | core/pref/keys/EditorKeys.kt:273 |  |
| `key_ace_smart_hint_signature_trigger` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_ace_smart_hint_signature_trigger"` | `key_ace_smart_hint_signature_trigger_cursor = cursor` | res/xml/fragment_code_editor_settings.xml:240 |  |
| `key_ace_svg_gutter_icons_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_svg_gutter_icons_enabled, "pref_ace_svg_gutter_icons_enabled")` | `true` | core/pref/keys/EditorKeys.kt:302 |  |
| `key_ace_svg_gutter_icons_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_svg_gutter_icons_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:333 |  |
| `key_ace_symbol_highlight_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_symbol_highlight_enabled, "pref_ace_symbol_highlight_enabled")` | `true` | core/pref/keys/EditorKeys.kt:282 |  |
| `key_ace_symbol_highlight_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_symbol_highlight_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:257 |  |
| `key_ace_touch_magnifier_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_touch_magnifier_enabled, "pref_ace_touch_magnifier_enabled")` | `false` | core/pref/keys/EditorKeys.kt:290 |  |
| `key_ace_touch_magnifier_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_touch_magnifier_enabled"` | `false` | res/xml/fragment_code_editor_settings.xml:286 |  |
| `key_ace_whitespaces_in_selection_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_whitespaces_in_selection_enabled, "pref_ace_whitespaces_in_selection_enabled")` | `true` | core/pref/keys/EditorKeys.kt:140 |  |
| `key_ace_whitespaces_in_selection_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_whitespaces_in_selection_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:113 |  |
| `key_ace_word_wrap_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_ace_word_wrap_enabled, "pref_ace_word_wrap_enabled")` | `false` | core/pref/keys/EditorKeys.kt:131 |  |
| `key_ace_word_wrap_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_ace_word_wrap_enabled"` | `false` | res/xml/fragment_code_editor_settings.xml:96 |  |
| `key_app_language` | String (Enum) | R.string | `keyResource(R.string.default_key_app_language, "default_key_app_language")` | `key_app_language_auto = key_$_app_language_auto` | core/pref/keys/AppearanceKeys.kt:56 |  |
| `key_app_language` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_app_language"` | `key_app_language_auto = key_$_app_language_auto` | res/xml/fragment_preferences.xml:9 |  |
| `key_auto_check_for_updates` | Boolean | R.bool | `boolResource(R.bool.pref_auto_check_for_updates, "pref_auto_check_for_updates")` | `true` | core/pref/keys/UpdatesKeys.kt:25 |  |
| `key_auto_check_for_updates` | Boolean | R.bool | `app:defaultValue="@bool/pref_auto_check_for_updates"` | `true` | res/xml/fragment_preferences.xml:348 |  |
| `key_auto_night_mode_enabled` | Boolean | 表达式 (人工) | `AutoNightMode.isFunctional()` | `运行时: AutoNightMode.isFunctional()` | core/pref/keys/AppearanceKeys.kt:74 |  |
| `key_auto_night_mode_enabled` | Boolean | 表达式 (人工) | `ViewUtils.isAutoNightModeEnabled = isFunctional()` | `运行时: 缺键时 Pref 初始化写入 isFunctional()` | core/pref/Pref.kt:49 |  |
| `key_batch_rename_history_retention` | Int | 伴生常量 | `SettingDefault.of(BatchRenameJournalStore.MAX_BATCHES) (经 常量 MAX_BATCHES (core/plugin/batchrename/BatchRenameJournalStore.kt:231))` | `20` | core/pref/keys/ExplorerKeys.kt:73 |  |
| `key_client_mode_auto_reconnect` | Boolean | R.bool | `boolResource(R.bool.pref_client_mode_auto_reconnect, "pref_client_mode_auto_reconnect")` | `true` | core/pref/keys/ConnectionKeys.kt:33 |  |
| `key_client_mode_reconnect_on_network_change` | Boolean | R.bool | `boolResource(R.bool.pref_client_mode_reconnect_on_network_change, "pref_client_mode_reconnect_on_network_change")` | `true` | core/pref/keys/ConnectionKeys.kt:40 |  |
| `key_client_mode_restore_on_launch` | Boolean | R.bool | `boolResource(R.bool.pref_client_mode_restore_on_launch, "pref_client_mode_restore_on_launch")` | `true` | core/pref/keys/ConnectionKeys.kt:47 |  |
| `key_codegen_model_current` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/LayoutInspectorKeys.kt:47 |  |
| `key_codegen_model_current` | String | 调用处字面量 (人工) | `preferences.getString(preferenceKey, null)` | `null` | ui/settings/codegen/CodegenModelStoreFactory.kt:40 | 未选择模型 |
| `key_console_element_time_format` | String | 伴生常量 | `SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN) (经 常量 DEFAULT_TIME_PATTERN (core/console/ConsoleLogFormat.kt:23))` | `HH:mm:ss.SSS` | core/pref/keys/ConsoleKeys.kt:52 |  |
| `key_console_element_time_format` | String | 伴生常量 (人工) | `ConsoleLogFormat.DEFAULT_TIME_PATTERN` | `HH:mm:ss.SSS` | core/console/ConsoleTimeSettings.kt:24 |  |
| `key_console_element_time_visible` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/ConsoleKeys.kt:49 |  |
| `key_console_element_time_visible` | Boolean | 伴生常量 (人工) | `CONSOLE_ELEMENT(..., false)` | `false` | core/console/ConsoleTimeSettings.kt:16 |  |
| `key_delete_permanently_also_clear_history` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/HistoryKeys.kt:67 |  |
| `key_delete_permanently_also_clear_history` | Boolean | XML 字面量 | `app:defaultValue="false"` | `false` | res/xml/fragment_preferences.xml:152 |  |
| `key_documentation_source` | String (Enum) | R.string | `keyResource(R.string.default_key_documentation_source, "default_key_documentation_source")` | `key_documentation_source_local = key_$_documentation_source_local` | core/pref/keys/GeneralKeys.kt:47 |  |
| `key_documentation_source` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_documentation_source"` | `key_documentation_source_local = key_$_documentation_source_local` | res/xml/fragment_preferences.xml:51 |  |
| `key_drafts_max_days` | Int | 伴生常量 | `SettingDefault.of(DEFAULT_DRAFTS_MAX_DAYS) (经 常量 DEFAULT_DRAFTS_MAX_DAYS (core/pref/keys/HistoryKeys.kt:34))` | `7` | core/pref/keys/HistoryKeys.kt:60 |  |
| `key_drafts_max_total_bytes` | Long | 伴生常量 | `SettingDefault.of(DEFAULT_DRAFTS_MAX_TOTAL_BYTES) (经 常量 DEFAULT_DRAFTS_MAX_TOTAL_BYTES (core/pref/keys/HistoryKeys.kt:35))` | `209715200` | core/pref/keys/HistoryKeys.kt:63 |  |
| `key_editor_auto_show_log_bottom_sheet_on_run` | Boolean | R.bool | `boolResource(R.bool.pref_editor_auto_show_log_bottom_sheet_on_run, "pref_editor_auto_show_log_bottom_sheet_on_run")` | `true` | core/pref/keys/EditorKeys.kt:177 |  |
| `key_editor_auto_show_log_bottom_sheet_on_run` | Boolean | R.bool | `app:defaultValue="@bool/pref_editor_auto_show_log_bottom_sheet_on_run"` | `true` | res/xml/fragment_preferences.xml:174 |  |
| `key_editor_auto_show_log_bottom_sheet_on_run` | Boolean | R.bool | `app:defaultValue="@bool/pref_editor_auto_show_log_bottom_sheet_on_run"` | `true` | res/xml/fragment_code_editor_settings.xml:152 |  |
| `key_editor_breakpoint_markers_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/EditorKeys.kt:305 |  |
| `key_editor_breakpoint_markers_enabled` | Boolean | XML 字面量 | `app:defaultValue="true"` | `true` | res/xml/fragment_code_editor_settings.xml:309 |  |
| `key_editor_code_highlight_threshold_bytes` | Int | R.integer | `intResource(R.integer.editor_code_highlight_threshold_default_value, "editor_code_highlight_threshold_default_value")` | `524288` | core/pref/keys/EditorKeys.kt:320 |  |
| `key_editor_engine` | String (Enum) | R.string | `keyResource(R.string.default_key_editor_engine, "default_key_editor_engine")` | `key_editor_engine_native = native` | core/pref/keys/EditorKeys.kt:96 |  |
| `key_editor_engine` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_editor_engine"` | `key_editor_engine_native = native` | res/xml/fragment_preferences.xml:165 |  |
| `key_editor_engine` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_editor_engine"` | `key_editor_engine_native = native` | res/xml/fragment_code_editor_settings.xml:9 |  |
| `key_editor_fold_markers_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/EditorKeys.kt:308 |  |
| `key_editor_fold_markers_enabled` | Boolean | XML 字面量 | `app:defaultValue="true"` | `true` | res/xml/fragment_code_editor_settings.xml:325 |  |
| `key_editor_fullscreen_mode` | Boolean | R.bool | `boolResource(R.bool.pref_editor_fullscreen_mode, "pref_editor_fullscreen_mode")` | `false` | core/pref/keys/EditorKeys.kt:149 |  |
| `key_editor_fullscreen_mode` | Boolean | R.bool | `app:defaultValue="@bool/pref_editor_fullscreen_mode"` | `false` | res/xml/fragment_code_editor_settings.xml:49 |  |
| `key_editor_gutter_width_mode` | String (Enum) | R.string | `keyResource(R.string.default_key_editor_gutter_width_mode, "default_key_editor_gutter_width_mode")` | `key_editor_gutter_width_mode_dynamic = dynamic` | core/pref/keys/EditorKeys.kt:311 |  |
| `key_editor_gutter_width_mode` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_editor_gutter_width_mode"` | `key_editor_gutter_width_mode_dynamic = dynamic` | res/xml/fragment_code_editor_settings.xml:300 |  |
| `key_editor_large_file_mode_threshold_bytes` | Int | R.integer | `intResource(R.integer.editor_large_file_mode_threshold_default_value, "editor_large_file_mode_threshold_default_value")` | `524288` | core/pref/keys/EditorKeys.kt:327 |  |
| `key_editor_log_button_behavior` | String (Enum) | R.string | `keyResource(R.string.default_key_editor_log_button_behavior, "default_key_editor_log_button_behavior")` | `key_editor_log_button_behavior_bottom_sheet = key_$_editor_log_button_behavior_bottom_sheet` | core/pref/keys/EditorKeys.kt:180 |  |
| `key_editor_log_button_behavior` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_editor_log_button_behavior"` | `key_editor_log_button_behavior_bottom_sheet = key_$_editor_log_button_behavior_bottom_sheet` | res/xml/fragment_preferences.xml:181 |  |
| `key_editor_log_button_behavior` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_editor_log_button_behavior"` | `key_editor_log_button_behavior_bottom_sheet = key_$_editor_log_button_behavior_bottom_sheet` | res/xml/fragment_code_editor_settings.xml:143 |  |
| `key_editor_log_panel_input_bar_visible` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/EditorKeys.kt:187 |  |
| `key_editor_log_panel_input_bar_visible` | Boolean | 伴生常量 (人工) | `DEFAULT_INPUT_BAR_VISIBLE` | `false` | ui/log/LogPageConsoleSettings.kt:61 |  |
| `key_editor_log_panel_time_format` | String | 伴生常量 | `SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN) (经 常量 DEFAULT_TIME_PATTERN (core/console/ConsoleLogFormat.kt:23))` | `HH:mm:ss.SSS` | core/pref/keys/EditorKeys.kt:193 |  |
| `key_editor_log_panel_time_format` | String | 伴生常量 (人工) | `ConsoleLogFormat.DEFAULT_TIME_PATTERN` | `HH:mm:ss.SSS` | core/console/ConsoleTimeSettings.kt:24 |  |
| `key_editor_log_panel_time_visible` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/EditorKeys.kt:190 |  |
| `key_editor_log_panel_time_visible` | Boolean | 伴生常量 (人工) | `EDITOR_PANEL(..., true)` | `true` | core/console/ConsoleTimeSettings.kt:13 |  |
| `key_editor_log_panel_typescript_console_visible` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/EditorKeys.kt:196 |  |
| `key_editor_pinch_to_zoom_change_text_size_enabled` | Boolean | R.bool | `boolResource(R.bool.pref_editor_pinch_to_zoom_change_text_size_enabled, "pref_editor_pinch_to_zoom_change_text_size_enabled")` | `true` | core/pref/keys/EditorKeys.kt:294 |  |
| `key_editor_pinch_to_zoom_change_text_size_enabled` | Boolean | R.bool | `app:defaultValue="@bool/pref_editor_pinch_to_zoom_change_text_size_enabled"` | `true` | res/xml/fragment_code_editor_settings.xml:279 |  |
| `key_editor_text_size` | Int | R.integer | `intResource(R.integer.editor_text_size_default_value, "editor_text_size_default_value")` | `14` | core/pref/keys/EditorKeys.kt:153 |  |
| `key_editor_text_size` | Int | 表达式 (人工) | `EditorKeys.EDITOR_TEXT_SIZE.storedOrNull() ?: editor.getTextSizeSp().toInt()` | `运行时: 当前编辑器字号` | ui/edit/editor/service/EditorThemeFontService.kt:68 | 缺键时保持编辑器当前字号; 原生编辑器的初值已改读声明的默认值 14 (附录 C Q10 (1), P2.3) |
| `key_editor_theme` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/EditorKeys.kt:160 |  |
| `key_editor_theme` | String | 表达式 (人工) | `Themes.getCurrentThemeName(...)` | `运行时: 原生编辑器 "Quiet Light" (分离模式夜间 "Dark (Visual Studio)"), Ace 为 AceEditorThemes.getDefault(dark)` | ui/edit/theme/Themes.java:181 |  |
| `key_editor_theme_dark` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/EditorKeys.kt:166 |  |
| `key_editor_theme_dark` | String | 伴生常量 (人工) | `firstNonBlank(..., DARK_THEME)` | `Dark (Visual Studio)` | ui/edit/theme/Themes.java:219 | 原生编辑器分离模式夜间的回退 |
| `key_editor_theme_light` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/EditorKeys.kt:163 |  |
| `key_editor_theme_light` | String | 伴生常量 (人工) | `firstNonBlank(..., DEFAULT_THEME)` | `Quiet Light` | ui/edit/theme/Themes.java:222 | 原生编辑器分离模式日间的回退 (先回退到 `key_editor_theme`) |
| `key_editor_theme_mode` | String (Enum) | R.string | `keyResource(R.string.default_key_editor_theme_mode, "default_key_editor_theme_mode")` | `key_editor_theme_mode_separate = separate_light_dark` | core/pref/keys/EditorKeys.kt:169 |  |
| `key_editor_theme_mode` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_editor_theme_mode"` | `key_editor_theme_mode_separate = separate_light_dark` | res/xml/fragment_code_editor_settings.xml:24 |  |
| `key_editor_word_wrap_indent_style` | String (Enum) | R.string | `keyResource(R.string.default_key_editor_word_wrap_indent_style, "default_key_editor_word_wrap_indent_style")` | `key_editor_word_wrap_indent_style_continuation = continuation` | core/pref/keys/EditorKeys.kt:134 |  |
| `key_editor_word_wrap_indent_style` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_editor_word_wrap_indent_style"` | `key_editor_word_wrap_indent_style_continuation = continuation` | res/xml/fragment_code_editor_settings.xml:104 |  |
| `key_enable_a11y_service_with_root_access` | Boolean | 调用处字面量 | `true` | `true` | inrt/Pref.kt:40 |  |
| `key_enable_a11y_service_with_root_access` | Boolean | R.bool | `boolResource(R.bool.pref_enable_a11y_service_with_root_access, "pref_enable_a11y_service_with_root_access")` | `true` | core/pref/keys/A11yKeys.kt:20 |  |
| `key_enable_a11y_service_with_root_access` | Boolean | R.bool | `app:defaultValue="@bool/pref_enable_a11y_service_with_root_access"` | `true` | res/xml/fragment_preferences.xml:217 |  |
| `key_enable_a11y_service_with_secure_settings` | Boolean | R.bool | `boolResource(R.bool.pref_enable_a11y_service_with_secure_settings, "pref_enable_a11y_service_with_secure_settings")` | `true` | core/pref/keys/A11yKeys.kt:26 |  |
| `key_enable_a11y_service_with_secure_settings` | Boolean | R.bool | `app:defaultValue="@bool/pref_enable_a11y_service_with_secure_settings"` | `true` | res/xml/fragment_preferences.xml:224 |  |
| `key_enable_a11y_service_with_shizuku` | Boolean | R.bool | `boolResource(R.bool.pref_enable_a11y_service_with_shizuku, "pref_enable_a11y_service_with_shizuku")` | `true` | core/pref/keys/A11yKeys.kt:32 |  |
| `key_enable_a11y_service_with_shizuku` | Boolean | R.bool | `app:defaultValue="@bool/pref_enable_a11y_service_with_shizuku"` | `true` | res/xml/fragment_preferences.xml:231 |  |
| `key_explorer_file_default_prefix` | Int | 调用处字面量 | `SettingDefault.of(0)` | `0` | core/pref/keys/ExplorerKeys.kt:69 |  |
| `key_explorer_playback_history` | Boolean | R.bool | `boolResource(R.bool.pref_explorer_playback_history, "pref_explorer_playback_history")` | `false` | core/pref/keys/ExplorerKeys.kt:62 |  |
| `key_explorer_playback_history` | Boolean | R.bool | `app:defaultValue="@bool/pref_explorer_playback_history"` | `false` | res/xml/fragment_preferences.xml:127 |  |
| `key_extending_js_build_in_objects` | Boolean | R.bool | `boolResource(R.bool.pref_extending_js_build_in_objects, "pref_extending_js_build_in_objects")` | `false` | core/pref/keys/ExtensibilityKeys.kt:23 |  |
| `key_extending_js_build_in_objects` | Boolean | R.bool | `app:defaultValue="@bool/pref_extending_js_build_in_objects"` | `false` | res/xml/fragment_preferences.xml:320 |  |
| `key_file_extensions` | String (Enum) | R.string | `keyResource(R.string.default_key_file_extensions, "default_key_file_extensions")` | `key_file_extensions_show_all = key_$_file_extensions_show_all` | core/pref/keys/ExplorerKeys.kt:56 |  |
| `key_file_extensions` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_file_extensions"` | `key_file_extensions_show_all = key_$_file_extensions_show_all` | res/xml/fragment_preferences.xml:118 |  |
| `key_floating_console_time_format` | String | 伴生常量 | `SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN) (经 常量 DEFAULT_TIME_PATTERN (core/console/ConsoleLogFormat.kt:23))` | `HH:mm:ss.SSS` | core/pref/keys/ConsoleKeys.kt:58 |  |
| `key_floating_console_time_format` | String | 伴生常量 (人工) | `ConsoleLogFormat.DEFAULT_TIME_PATTERN` | `HH:mm:ss.SSS` | core/console/ConsoleTimeSettings.kt:24 |  |
| `key_floating_console_time_visible` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/ConsoleKeys.kt:55 |  |
| `key_floating_console_time_visible` | Boolean | 伴生常量 (人工) | `FLOATING_WINDOW(..., false)` | `false` | core/console/ConsoleTimeSettings.kt:17 |  |
| `key_foreground_service` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/GeneralKeys.kt:80 |  |
| `key_foreground_service` | Boolean | 调用处字面量 (人工) | `setting.storedOrNull() ?: false` | `false` | ui/main/drawer/DrawerMenuToggleableItem.kt:285 | 抽屉 "前台服务" 开关 (prefSetting = GeneralKeys.FOREGROUND_SERVICE); 脚本 API settings.isEnabled 缺键时读声明的默认值 (附录 C Q10, P2.4) |
| `key_gesture_observing` | Boolean | R.bool | `boolResource(R.bool.pref_gesture_observing, "pref_gesture_observing")` | `false` | core/pref/keys/A11yKeys.kt:41 |  |
| `key_global_console_element_time_format` | String | 伴生常量 | `SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN) (经 常量 DEFAULT_TIME_PATTERN (core/console/ConsoleLogFormat.kt:23))` | `HH:mm:ss.SSS` | core/pref/keys/ConsoleKeys.kt:46 |  |
| `key_global_console_element_time_format` | String | 伴生常量 (人工) | `ConsoleLogFormat.DEFAULT_TIME_PATTERN` | `HH:mm:ss.SSS` | core/console/ConsoleTimeSettings.kt:24 |  |
| `key_global_console_element_time_visible` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/ConsoleKeys.kt:43 |  |
| `key_global_console_element_time_visible` | Boolean | 伴生常量 (人工) | `GLOBAL_CONSOLE_ELEMENT(..., false)` | `false` | core/console/ConsoleTimeSettings.kt:15 |  |
| `key_guard_mode` | Boolean | R.bool | `boolResource(R.bool.pref_guard_mode, "pref_guard_mode")` | `false` | core/pref/keys/RunningKeys.kt:37 |  |
| `key_guard_mode` | Boolean | R.bool | `app:defaultValue="@bool/pref_guard_mode"` | `false` | res/xml/fragment_preferences.xml:263 |  |
| `key_hidden_files` | String (Enum) | R.string | `keyResource(R.string.default_key_hidden_files, "default_key_hidden_files")` | `key_hidden_files_not_show = key_$_hidden_files_not_show` | core/pref/keys/ExplorerKeys.kt:50 |  |
| `key_hidden_files` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_hidden_files"` | `key_hidden_files_not_show = key_$_hidden_files_not_show` | res/xml/fragment_preferences.xml:109 |  |
| `key_history_max_days` | Int | 伴生常量 | `SettingDefault.of(DEFAULT_HISTORY_MAX_DAYS) (经 常量 DEFAULT_HISTORY_MAX_DAYS (core/pref/keys/HistoryKeys.kt:26))` | `30` | core/pref/keys/HistoryKeys.kt:42 |  |
| `key_history_max_file_size_to_track_bytes` | Long | 伴生常量 | `SettingDefault.of(DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES) (经 常量 DEFAULT_MAX_FILE_SIZE_TO_TRACK_BYTES (core/pref/keys/HistoryKeys.kt:24))` | `20971520` | core/pref/keys/HistoryKeys.kt:39 |  |
| `key_history_max_total_bytes` | Long | 伴生常量 | `SettingDefault.of(DEFAULT_HISTORY_MAX_TOTAL_BYTES) (经 常量 DEFAULT_HISTORY_MAX_TOTAL_BYTES (core/pref/keys/HistoryKeys.kt:29))` | `2147483648` | core/pref/keys/HistoryKeys.kt:51 |  |
| `key_history_max_total_bytes_per_file` | Long | 伴生常量 | `SettingDefault.of(DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE) (经 常量 DEFAULT_HISTORY_MAX_TOTAL_BYTES_PER_FILE (core/pref/keys/HistoryKeys.kt:28))` | `209715200` | core/pref/keys/HistoryKeys.kt:48 |  |
| `key_history_max_versions` | Int | 伴生常量 | `SettingDefault.of(DEFAULT_HISTORY_MAX_VERSIONS) (经 常量 DEFAULT_HISTORY_MAX_VERSIONS (core/pref/keys/HistoryKeys.kt:27))` | `50` | core/pref/keys/HistoryKeys.kt:45 |  |
| `key_ignored_updates` | Json | 调用处字面量 | `SettingDefault.of(emptyList())` | `[]` | core/pref/keys/UpdatesKeys.kt:32 |  |
| `key_keep_screen_on_when_in_foreground` | String (Enum) | R.string | `keyResource(R.string.default_key_keep_screen_on_when_in_foreground, "default_key_keep_screen_on_when_in_foreground")` | `key_keep_screen_on_when_in_foreground_disabled = key_$_keep_screen_on_when_in_foreground_disabled` | core/pref/keys/AppearanceKeys.kt:98 |  |
| `key_keep_screen_on_when_in_foreground` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_keep_screen_on_when_in_foreground"` | `key_keep_screen_on_when_in_foreground_disabled = key_$_keep_screen_on_when_in_foreground_disabled` | res/xml/fragment_preferences.xml:36 |  |
| `key_keep_screen_on_when_in_foreground_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/AppearanceKeys.kt:105 |  |
| `key_launcher_icon` | String (Enum) | R.string | `keyResource(R.string.default_key_launcher_icon, "default_key_launcher_icon")` | `key_launcher_icon_adaptive = key_$_launcher_icon_adaptive` | core/pref/keys/GeneralKeys.kt:73 |  |
| `key_launcher_icon` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_launcher_icon"` | `key_launcher_icon_adaptive = key_$_launcher_icon_adaptive` | res/xml/fragment_preferences.xml:70 |  |
| `key_layout_inspector_filter_profile_selected` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/LayoutInspectorKeys.kt:31 |  |
| `key_layout_inspector_filter_profiles` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/LayoutInspectorKeys.kt:27 |  |
| `key_layout_inspector_node_copy_mode` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/LayoutInspectorKeys.kt:35 |  |
| `key_layout_inspector_query_mode` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/LayoutInspectorKeys.kt:39 |  |
| `key_leakcanary_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/DeveloperKeys.kt:46 |  |
| `key_leakcanary_enabled` | Boolean | XML 字面量 | `app:defaultValue="true"` | `true` | res/xml/fragment_developer_options.xml:167 |  |
| `key_log_activity_input_bar_visible` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/ConsoleKeys.kt:30 |  |
| `key_log_activity_input_bar_visible` | Boolean | 伴生常量 (人工) | `DEFAULT_INPUT_BAR_VISIBLE` | `false` | ui/log/LogPageConsoleSettings.kt:61 |  |
| `key_log_activity_log_coloring` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/ConsoleKeys.kt:33 |  |
| `key_log_activity_text_size` | Int | 调用处字面量 | `SettingDefault.of(0)` | `0` | core/pref/keys/ConsoleKeys.kt:37 |  |
| `key_log_activity_time_format` | String | 伴生常量 | `SettingDefault.of(ConsoleLogFormat.DEFAULT_TIME_PATTERN) (经 常量 DEFAULT_TIME_PATTERN (core/console/ConsoleLogFormat.kt:23))` | `HH:mm:ss.SSS` | core/pref/keys/ConsoleKeys.kt:27 |  |
| `key_log_activity_time_format` | String | 伴生常量 (人工) | `ConsoleLogFormat.DEFAULT_TIME_PATTERN` | `HH:mm:ss.SSS` | core/console/ConsoleTimeSettings.kt:24 |  |
| `key_log_activity_time_visible` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/ConsoleKeys.kt:24 |  |
| `key_log_activity_time_visible` | Boolean | 伴生常量 (人工) | `LOG_ACTIVITY(..., true)` | `true` | core/console/ConsoleTimeSettings.kt:14 |  |
| `key_log_activity_typescript_console_visible` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/ConsoleKeys.kt:40 |  |
| `key_memory_script_heap_dump` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/DeveloperKeys.kt:63 |  |
| `key_memory_script_heap_dump` | Boolean | XML 字面量 | `app:defaultValue="false"` | `false` | res/xml/fragment_developer_options.xml:160 |  |
| `key_memory_unreclaimed_warning` | Boolean | 调用处字面量 | `true` | `true` | runtime/memory/MemoryDiagnostics.kt:112 |  |
| `key_memory_unreclaimed_warning` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/DeveloperKeys.kt:54 |  |
| `key_memory_unreclaimed_warning` | Boolean | XML 字面量 | `app:defaultValue="true"` | `true` | res/xml/fragment_developer_options.xml:153 |  |
| `key_new_script_module_layer` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/DeveloperKeys.kt:26 |  |
| `key_new_script_module_layer` | Boolean | XML 字面量 | `app:defaultValue="false"` | `false` | res/xml/fragment_developer_options.xml:9 |  |
| `key_night_mode` | String (Enum) | R.string | `keyResource(R.string.default_key_night_mode, "default_key_night_mode")` | `key_night_mode_follow_system = key_$_night_mode_follow_system` | core/pref/keys/AppearanceKeys.kt:63 |  |
| `key_night_mode` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_night_mode"` | `key_night_mode_follow_system = key_$_night_mode_follow_system` | res/xml/fragment_preferences.xml:20 |  |
| `key_night_mode_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/AppearanceKeys.kt:70 |  |
| `key_night_mode_enabled` | Boolean | 表达式 (人工) | `ViewUtils.isNightModeEnabled = isNightModeYes(app)` | `运行时: 缺键时 Pref 初始化写入系统当前是否夜间` | core/pref/Pref.kt:59 |  |
| `key_nodejs_inspector_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/DeveloperKeys.kt:38 |  |
| `key_nodejs_inspector_enabled` | Boolean | XML 字面量 | `app:defaultValue="false"` | `false` | res/xml/fragment_developer_options.xml:117 |  |
| `key_plugin_center_auto_check_updates` | Boolean | R.bool | `boolResource(R.bool.pref_plugin_center_auto_check_updates, "pref_plugin_center_auto_check_updates")` | `true` | core/pref/keys/PluginCenterKeys.kt:56 |  |
| `key_plugin_center_auto_check_updates` | Boolean | R.bool | `app:defaultValue="@bool/pref_plugin_center_auto_check_updates"` | `true` | res/xml/fragment_plugin_center_preferences.xml:29 |  |
| `key_plugin_center_show_official_plugins` | Boolean | R.bool | `boolResource(R.bool.pref_plugin_center_show_official_plugins, "pref_plugin_center_show_official_plugins")` | `true` | core/pref/keys/PluginCenterKeys.kt:44 |  |
| `key_plugin_center_show_official_plugins` | Boolean | R.bool | `app:defaultValue="@bool/pref_plugin_center_show_official_plugins"` | `true` | res/xml/fragment_plugin_center_preferences.xml:9 |  |
| `key_plugin_center_show_third_party_plugins` | Boolean | R.bool | `boolResource(R.bool.pref_plugin_center_show_third_party_plugins, "pref_plugin_center_show_third_party_plugins")` | `true` | core/pref/keys/PluginCenterKeys.kt:50 |  |
| `key_plugin_center_show_third_party_plugins` | Boolean | R.bool | `app:defaultValue="@bool/pref_plugin_center_show_third_party_plugins"` | `true` | res/xml/fragment_plugin_center_preferences.xml:16 |  |
| `key_record_toast` | Boolean | R.bool | `boolResource(R.bool.pref_record_toast, "pref_record_toast")` | `true` | core/pref/keys/RecordKeys.kt:30 |  |
| `key_record_toast` | Boolean | R.bool | `app:defaultValue="@bool/pref_record_toast"` | `true` | res/xml/fragment_preferences.xml:298 |  |
| `key_restart_strategy` | String (Enum) | R.string | `keyResource(R.string.default_key_restart_strategy, "default_key_restart_strategy")` | `key_restart_strategy_quick = key_$_restart_strategy_quick` | core/pref/keys/GeneralKeys.kt:53 |  |
| `key_restart_strategy` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_restart_strategy"` | `key_restart_strategy_quick = key_$_restart_strategy_quick` | res/xml/fragment_preferences.xml:61 |  |
| `key_rhino_java_primitive_wrap` | Boolean | R.bool | `boolResource(R.bool.pref_rhino_java_primitive_wrap, "pref_rhino_java_primitive_wrap")` | `true` | core/pref/keys/ExtensibilityKeys.kt:29 |  |
| `key_rhino_java_primitive_wrap` | Boolean | R.bool | `app:defaultValue="@bool/pref_rhino_java_primitive_wrap"` | `true` | res/xml/fragment_preferences.xml:328 |  |
| `key_root_manager_apps_custom` | StringSet | 调用处字面量 | `SettingDefault.of(emptySet())` | `[]` | core/pref/keys/RunningKeys.kt:55 |  |
| `key_root_mode` | String (Enum) | R.string | `keyResource(R.string.default_key_root_mode, "default_key_root_mode")` | `key_root_mode_auto_detect = key_$_root_mode_auto_detect` | core/pref/keys/RunningKeys.kt:41 |  |
| `key_root_mode` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_root_mode"` | `key_root_mode_auto_detect = key_$_root_mode_auto_detect` | res/xml/fragment_preferences.xml:270 |  |
| `key_root_record_out_file_type` | String (Enum) | R.string | `keyResource(R.string.default_key_root_record_out_file_type, "default_key_root_record_out_file_type")` | `key_root_record_out_file_type_binary = key_$_root_record_out_file_type_binary` | core/pref/keys/RecordKeys.kt:33 |  |
| `key_root_record_out_file_type` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_root_record_out_file_type"` | `key_root_record_out_file_type_binary = key_$_root_record_out_file_type_binary` | res/xml/fragment_preferences.xml:305 |  |
| `key_scheduled_restart_backend` | String (Enum) | R.string | `keyResource(R.string.default_key_scheduled_restart_backend, "default_key_scheduled_restart_backend")` | `key_scheduled_restart_backend_work_manager = key_$_scheduled_restart_backend_work_manager` | core/pref/keys/GeneralKeys.kt:59 |  |
| `key_scheduled_restart_delay` | Int | R.integer | `intResource(R.integer.scheduled_restart_start_delay_default_value, "scheduled_restart_start_delay_default_value")` | `300` | core/pref/keys/GeneralKeys.kt:66 |  |
| `key_screen_capture_request_delay` | Int | R.integer | `intResource(R.integer.screen_capture_request_delay_default_value, "screen_capture_request_delay_default_value")` | `350` | core/pref/keys/RunningKeys.kt:48 |  |
| `key_script_module_debug_print` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/DeveloperKeys.kt:30 |  |
| `key_script_module_debug_print` | Boolean | XML 字面量 | `app:defaultValue="false"` | `false` | res/xml/fragment_developer_options.xml:16 |  |
| `key_script_module_debug_print_disabled_modules` | StringSet | 声明 | `SettingDefault.None` | `null` | core/pref/keys/DeveloperKeys.kt:34 |  |
| `key_server_address` | String | 表达式 (人工) | `if (!isInrt) NetworkUtils.getGatewayAddress() else NetworkUtils.DEFAULT_IP_ADDRESS` | `运行时: 网关地址 (inrt 为 NetworkUtils.DEFAULT_IP_ADDRESS)` | core/pref/keys/ConnectionKeys.kt:24 |  |
| `key_server_mode_lan_discovery` | Boolean | R.bool | `boolResource(R.bool.pref_server_mode_lan_discovery, "pref_server_mode_lan_discovery")` | `true` | core/pref/keys/ConnectionKeys.kt:68 |  |
| `key_server_mode_notify_connection_changes` | Boolean | R.bool | `boolResource(R.bool.pref_server_mode_notify_connection_changes, "pref_server_mode_notify_connection_changes")` | `true` | core/pref/keys/ConnectionKeys.kt:61 |  |
| `key_server_mode_restore_on_launch` | Boolean | R.bool | `boolResource(R.bool.pref_server_mode_restore_on_launch, "pref_server_mode_restore_on_launch")` | `true` | core/pref/keys/ConnectionKeys.kt:54 |  |
| `key_stable_mode` | Boolean | R.bool | `boolResource(R.bool.pref_stable_mode, "pref_stable_mode")` | `false` | core/pref/keys/A11yKeys.kt:38 |  |
| `key_stable_mode` | Boolean | R.bool | `app:defaultValue="@bool/pref_stable_mode"` | `false` | res/xml/fragment_preferences.xml:238 |  |
| `key_theme_color_accent` | Int | 伴生常量 (人工) | `ThemeColorManager.defaultThemeColor.colorAccent` | `由 #FFDEAD 派生 (inrt 为 #263238)` | core/pref/keys/AppearanceKeys.kt:92 | ThemeColorManager.kt:20 defaultThemeColor |
| `key_theme_color_primary` | Int | 伴生常量 (人工) | `ThemeColorManager.defaultThemeColor.colorPrimary` | `由 #FFDEAD 派生 (inrt 为 #263238)` | core/pref/keys/AppearanceKeys.kt:80 | ThemeColorManager.kt:20 defaultThemeColor |
| `key_theme_color_primary_dark` | Int | 伴生常量 (人工) | `ThemeColorManager.defaultThemeColor.colorPrimaryDark` | `由 #FFDEAD 派生 (inrt 为 #263238)` | core/pref/keys/AppearanceKeys.kt:86 | ThemeColorManager.kt:20 defaultThemeColor |
| `key_timed_task_backend` | String (Enum) | R.string | `keyResource(R.string.default_key_timed_task_backend, "default_key_timed_task_backend")` | `key_timed_task_backend_alarm = key_$_timed_task_backend_alarm` | core/pref/keys/TimedTaskKeys.kt:27 |  |
| `key_timed_task_backend` | String (Enum) | R.string | `app:itemDefaultKey="@string/default_key_timed_task_backend"` | `key_timed_task_backend_alarm = key_$_timed_task_backend_alarm` | res/xml/fragment_preferences.xml:201 |  |
| `key_trash_max_days` | Int | 伴生常量 | `SettingDefault.of(DEFAULT_TRASH_MAX_DAYS) (经 常量 DEFAULT_TRASH_MAX_DAYS (core/pref/keys/HistoryKeys.kt:31))` | `30` | core/pref/keys/HistoryKeys.kt:54 |  |
| `key_trash_max_total_bytes` | Long | 伴生常量 | `SettingDefault.of(DEFAULT_TRASH_MAX_TOTAL_BYTES) (经 常量 DEFAULT_TRASH_MAX_TOTAL_BYTES (core/pref/keys/HistoryKeys.kt:32))` | `524288000` | core/pref/keys/HistoryKeys.kt:57 |  |
| `key_use_volume_control_record` | Boolean | R.bool | `boolResource(R.bool.pref_use_volume_control_record, "pref_use_volume_control_record")` | `true` | core/pref/keys/RecordKeys.kt:24 |  |
| `key_use_volume_control_record` | Boolean | R.bool | `app:defaultValue="@bool/pref_use_volume_control_record"` | `true` | res/xml/fragment_preferences.xml:291 |  |
| `key_use_volume_control_running` | Boolean | 调用处字面量 | `true` | `true` | inrt/Pref.kt:48 |  |
| `key_use_volume_control_running` | Boolean | R.bool | `boolResource(R.bool.pref_use_volume_control_running, "pref_use_volume_control_running")` | `true` | core/pref/keys/RunningKeys.kt:31 |  |
| `key_use_volume_control_running` | Boolean | R.bool | `app:defaultValue="@bool/pref_use_volume_control_running"` | `true` | res/xml/fragment_preferences.xml:256 |  |
| `key_working_directory` | String | R.string | `stringResource(R.string.default_value_working_directory, "default_value_working_directory")` | `/Scripts` | core/pref/keys/ExplorerKeys.kt:43 |  |
| `key_working_directory` | String | 表达式 (人工) | `WorkingDirectoryUtils.determineIfNeeded()` | `运行时: 首次启动在各语言的默认目录中选最近修改且非空者` | util/WorkingDirectoryUtils.kt:57 | `default_value_working_directory` 按语言不同 (/Scripts, /脚本 等 10 种) |
| `COLOR_FAVORITE_SORT` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/AppearanceKeys.kt:122 |  |
| `COLOR_HISTORY_MERGE_SIMILAR` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/AppearanceKeys.kt:118 |  |
| `THEME_COLOR_DERIVED_ACCENT_AND_DARK` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/AppearanceKeys.kt:114 |  |
| `dex_compiler_experimental_component` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/DeveloperKeys.kt:71 |  |
| `dex_compiler_experimental_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/DeveloperKeys.kt:67 |  |
| `key_$_ace_semantic_java_enabled` | Boolean | 表达式 | `DEFAULT_SEMANTIC_LANGUAGES[java]` | `运行时: DEFAULT_SEMANTIC_LANGUAGES[java]` | core/pref/keys/EditorKeys.kt:241 |  |
| `key_$_ace_semantic_java_enabled` | Boolean | 伴生常量 (人工) | `DEFAULT_SEMANTIC_LANGUAGES[java]` | `true` | ui/edit/editor/ace/AceEditorLspPreferences.kt:123 |  |
| `key_$_ace_semantic_kotlin_enabled` | Boolean | 表达式 | `DEFAULT_SEMANTIC_LANGUAGES[kotlin]` | `运行时: DEFAULT_SEMANTIC_LANGUAGES[kotlin]` | core/pref/keys/EditorKeys.kt:247 |  |
| `key_$_ace_semantic_kotlin_enabled` | Boolean | 伴生常量 (人工) | `DEFAULT_SEMANTIC_LANGUAGES[kotlin]` | `false` | ui/edit/editor/ace/AceEditorLspPreferences.kt:123 |  |
| `key_$_ace_semantic_lua_enabled` | Boolean | 表达式 | `DEFAULT_SEMANTIC_LANGUAGES[lua]` | `运行时: DEFAULT_SEMANTIC_LANGUAGES[lua]` | core/pref/keys/EditorKeys.kt:235 |  |
| `key_$_ace_semantic_lua_enabled` | Boolean | 伴生常量 (人工) | `DEFAULT_SEMANTIC_LANGUAGES[lua]` | `true` | ui/edit/editor/ace/AceEditorLspPreferences.kt:123 |  |
| `key_$_ace_semantic_python_enabled` | Boolean | 表达式 | `DEFAULT_SEMANTIC_LANGUAGES[python]` | `运行时: DEFAULT_SEMANTIC_LANGUAGES[python]` | core/pref/keys/EditorKeys.kt:229 |  |
| `key_$_ace_semantic_python_enabled` | Boolean | 伴生常量 (人工) | `DEFAULT_SEMANTIC_LANGUAGES[python]` | `true` | ui/edit/editor/ace/AceEditorLspPreferences.kt:123 |  |
| `key_$_ace_semantic_typescript_enabled` | Boolean | 表达式 | `DEFAULT_SEMANTIC_LANGUAGES[typescript]` | `运行时: DEFAULT_SEMANTIC_LANGUAGES[typescript]` | core/pref/keys/EditorKeys.kt:223 |  |
| `key_$_ace_semantic_typescript_enabled` | Boolean | 伴生常量 (人工) | `DEFAULT_SEMANTIC_LANGUAGES[typescript]` | `true` | ui/edit/editor/ace/AceEditorLspPreferences.kt:123 |  |
| `key_$_codegen_model_current_debug_` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/LayoutInspectorKeys.kt:57 |  |
| `key_$_editor_engine_selection_mode` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/EditorKeys.kt:103 |  |
| `key_$_plugin_mount_enabled_$_` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/PluginCenterKeys.kt:242 |  |
| `key_$_plugin_mount_enabled_$_` | Boolean | 调用处字面量 (人工) | `Pref.getBoolean(enabledKey(packageName), false)` | `false` | core/plugin/center/PluginMountStore.kt:32 |  |
| `key_$_plugin_mount_object_name_$_` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/PluginCenterKeys.kt:248 |  |
| `key_$_plugin_mount_object_name_$_` | String | 调用处字面量 (人工) | `Pref.getStringOrNull(objectNameKey(packageName))` | `null` | core/plugin/center/PluginMountStore.kt:43 | 缺省时用 defaultObjectName(packageName) |
| `key_$_plugin_mount_packages` | StringSet | 调用处字面量 | `SettingDefault.of(emptySet())` | `[]` | core/pref/keys/PluginCenterKeys.kt:237 |  |
| `key_$_plugin_mount_point_$_` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/PluginCenterKeys.kt:254 |  |
| `key_$_plugin_mount_point_$_` | String | 调用处字面量 (人工) | `Pref.getStringOrNull(mountPointKey(packageName))` | `null` | core/plugin/center/PluginMountStore.kt:61 | null 与 "global" 都表示全局挂载点 |
| `key_$_plugin_update_check_$_` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/PluginCenterKeys.kt:260 |  |
| `key_$_plugin_update_check_$_` | Boolean | 调用处字面量 (人工) | `isEnabled(packageName, defaultEnabled = true)` | `true` | core/plugin/center/PluginUpdateCheckStore.kt:12 |  |
| `key_$_plugin_variant_priority_$_` | Json | 调用处字面量 | `SettingDefault.of(emptyList())` | `[]` | core/pref/keys/PluginCenterKeys.kt:270 |  |
| `key_$_plugin_variant_priority_$_` | Json (JSON 数组) | 调用处字面量 (人工) | `Pref.getStringOrNull(key).orEmpty()` | `[]` | core/plugin/center/PluginVariantPriorityStore.kt:15 |  |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.dir_ascending` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/ExplorerKeys.kt:89 |  |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.dir_sort_type` | Int | 表达式 | `SettingDefault.of(0x10)` | `(需人工核对)` | core/pref/keys/ExplorerKeys.kt:83 |  |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.file_ascending` | Boolean | 调用处字面量 | `SettingDefault.of(true)` | `true` | core/pref/keys/ExplorerKeys.kt:86 |  |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.file_sort_type` | Int | 表达式 | `SettingDefault.of(0x10)` | `(需人工核对)` | core/pref/keys/ExplorerKeys.kt:80 |  |
| `r8_compiler_explicit_component` | String | 声明 | `SettingDefault.None` | `null` | core/pref/keys/DeveloperKeys.kt:79 |  |
| `r8_compiler_explicit_enabled` | Boolean | 调用处字面量 | `SettingDefault.of(false)` | `false` | core/pref/keys/DeveloperKeys.kt:75 |  |
| `three_stove_agent_script_root_configuration` | Json | 调用处字面量 | `SettingDefault.of(emptyMap())` | `{}` | core/pref/keys/ExtensibilityKeys.kt:41 |  |

## 2. 同一键多处默认值不一致

只比较能求出取值的来源; 取值为 `null` 的行 (`getStringOrNull` 或变更检测, 调用处自行回退) 不参与比较. 逐条登记在 ROADMAP 附录 C Q10, 由维护者定统一值; P1-P5 期间保持现状 (红线 R2).

无.
