# 设置清单 (inventory)

由 `tools/settings/inventory.py` 生成, 不要手工编辑; kind / 分类 id / 备注来自 `tools/settings/classification.json`. 路线图见 [ROADMAP.md](ROADMAP.md) (P0.2 / P0.3).

- 生成日期: 2026-10-20, 扫描基线: `master@218ea2f45` (扫描范围内未提交文件 0 个)
- 源码路径相对 `app/src/main/java/org/autojs/autojs/` (其他包保留 `com/...` 等前缀); 资源路径相对 `app/src/main/`.
- 引用处只计非注释行; 超过 6 处时只列前 6 处, 完整列表见 JSON 基线.
- kind: Setting (用户设置), State (记忆型状态, 归 `other`), Cache (缓存, 不可导出), ValueConstant (设置值常量, 列表项候选值或默认值别名), Excluded (不是偏好键或永久排除, 见 ROADMAP 附录 A.2).

## 1. 概况

| 指标 | 数值 |
| --- | --- |
| `key_*` 字符串资源 | 267 (`strings_donottranslate.xml` 265, 其他文件 2: `key_ace_language_support_matrix`, `key_ace_semantic_languages`) |
| 出现在设置页 XML 的 `key_*` 键 (各页之和去重) | 98 (preferences 39, code_editor_settings 40, developer_options 10, trash_and_version_history_settings 9, plugin_center_preferences 3) |
| 设置页中不经字符串资源的字面量键 | 3 (developer_options 1, plugin_center_preferences 2) |
| 不在设置页的 `key_*` 键 | 169 |
| 源码 (`app/src/main/java`) 没有引用的键 | 53 (其中设置页, 数组与其他资源也没有引用的 13 个: `key_ace_editor_font_cascadia_code`, `key_ace_editor_font_fira_code`, `key_ace_editor_font_hack`, `key_ace_editor_font_ibm_plex_mono`, `key_ace_editor_font_intel_one_mono`, `key_ace_editor_font_iosevka_slab`, `key_ace_editor_font_jetbrains_mono`, `key_ace_editor_font_source_code_pro`, `key_ace_editor_font_system_monospace`, `key_dialog_foreground_svc`, `key_dialog_layout_inspector_exit_tips`, `key_dialog_log_entries_exported`, `key_enable_observe_key`) |
| 源码字面量键 (不对应 `key_*` 资源) | 71 |
| 命名 SharedPreferences / 调用文件 | 29 / 34 |
| 绕过 `Pref` 读默认偏好的文件 | 8 |
| 经 `Pref.` 读写的文件 (不含两个 `Pref.kt`) | 119 |
| filesDir / noBackupFilesDir 旁出现 JSON 文件名的源文件 | 13 |

## 2. `key_*` 字符串资源

| 键 | 出现页面与分类 | 引用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `key_a11y_service` |  | ui/main/drawer/DrawerFragment.kt:230 |  |  |  |
| `key_a11y_service_manager_control_level` |  | ui/accessibility/A11yServiceManagerDialog.kt:129, ui/accessibility/A11yServiceManagerDialog.kt:312 |  |  |  |
| `key_a11y_service_manager_settings_collapsed` |  | ui/accessibility/A11yServiceManagerDialog.kt:180 |  |  |  |
| `key_a11y_service_manager_status_level` |  | ui/accessibility/A11yServiceManagerDialog.kt:128, ui/accessibility/A11yServiceManagerDialog.kt:293 |  |  |  |
| `key_about_app_and_developer` | 设置 > 关于 |  |  |  |  |
| `key_about_app_tips` | 设置 > 关于 |  |  |  |  |
| `key_ace_custom_scrollbar_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:252, ui/settings/EditorSettingActionRegistry.kt:33, ui/edit/editor/EditorDisplayPreferences.kt:22 |  |  |  |
| `key_ace_editor_font` | 代码编辑器设置 > 外观 | ui/edit/EditorView.kt:928, ui/settings/CodeEditorSettingsFragment.kt:245, ui/settings/CodeEditorSettingsFragment.kt:287, ui/settings/EditorSettingActionRegistry.kt:22, ui/edit/editor/ace/AceEditorFontPreferences.kt:7 |  |  |  |
| `key_ace_editor_font_cascadia_code` |  |  |  |  |  |
| `key_ace_editor_font_fira_code` |  |  |  |  |  |
| `key_ace_editor_font_hack` |  |  |  |  |  |
| `key_ace_editor_font_ibm_plex_mono` |  |  |  |  |  |
| `key_ace_editor_font_intel_one_mono` |  |  |  |  |  |
| `key_ace_editor_font_iosevka` | 默认值 `default_key_ace_editor_font` |  |  |  |  |
| `key_ace_editor_font_iosevka_slab` |  |  |  |  |  |
| `key_ace_editor_font_jetbrains_mono` |  |  |  |  |  |
| `key_ace_editor_font_source_code_pro` |  |  |  |  |  |
| `key_ace_editor_font_system_monospace` |  |  |  |  |  |
| `key_ace_font_ligatures_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:246, ui/settings/EditorSettingActionRegistry.kt:25, ui/edit/editor/EditorDisplayPreferences.kt:15 |  |  |  |
| `key_ace_font_styles_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:247, ui/settings/EditorSettingActionRegistry.kt:26, ui/edit/editor/EditorDisplayPreferences.kt:16 |  |  |  |
| `key_ace_highlight_indent_guides_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:250, ui/settings/EditorSettingActionRegistry.kt:29, ui/edit/editor/EditorDisplayPreferences.kt:20 |  |  |  |
| `key_ace_indent_guides_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:249, ui/settings/EditorSettingActionRegistry.kt:28, ui/edit/editor/EditorDisplayPreferences.kt:11 |  |  |  |
| `key_ace_inline_completion_preview_enabled` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:268, ui/settings/EditorSettingActionRegistry.kt:49, ui/edit/editor/EditorDisplayPreferences.kt:34 |  |  |  |
| `key_ace_language_support_matrix` | 代码编辑器设置 > 辅助 | ui/settings/CodeEditorSettingsFragment.kt:293, ui/settings/EditorSettingActionRegistry.kt:38 |  |  |  |
| `key_ace_line_numbers_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/EditorSettingActionRegistry.kt:54, ui/edit/editor/EditorDisplayPreferences.kt:9 |  |  |  |
| `key_ace_lsp_declaration_groups` | 代码编辑器设置 > 辅助 | ui/edit/EditorView.kt:946, ui/settings/CodeEditorSettingsFragment.kt:295, ui/settings/EditorSettingActionRegistry.kt:40, ui/edit/editor/ace/AceEditorLspPreferences.kt:9 |  |  |  |
| `key_ace_lsp_enabled` | 代码编辑器设置 > 辅助 | ui/edit/EditorView.kt:945, ui/settings/CodeEditorSettingsFragment.kt:93, ui/settings/CodeEditorSettingsFragment.kt:256, ui/settings/EditorSettingActionRegistry.kt:39, ui/edit/editor/ace/AceEditorLspPreferences.kt:6 |  |  |  |
| `key_ace_lsp_file_types` | 代码编辑器设置 > 辅助 | ui/edit/EditorView.kt:947, ui/settings/CodeEditorSettingsFragment.kt:296, ui/settings/EditorSettingActionRegistry.kt:41, ui/edit/editor/ace/AceEditorLspPreferences.kt:7 |  |  |  |
| `key_ace_print_margin_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:248, ui/settings/EditorSettingActionRegistry.kt:27, ui/edit/editor/EditorDisplayPreferences.kt:10 |  |  |  |
| `key_ace_scrollbar_auto_hide_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:253, ui/settings/EditorSettingActionRegistry.kt:34, ui/edit/editor/EditorDisplayPreferences.kt:23 |  |  |  |
| `key_ace_semantic_languages` | 代码编辑器设置 > 辅助 | ui/settings/CodeEditorSettingsFragment.kt:297, ui/settings/EditorSettingActionRegistry.kt:42 |  |  |  |
| `key_ace_smart_hint_arrow_overload_cycling` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:266, ui/settings/EditorSettingActionRegistry.kt:47, ui/edit/editor/EditorDisplayPreferences.kt:32 |  |  |  |
| `key_ace_smart_hint_completion_delay` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:264, ui/settings/EditorSettingActionRegistry.kt:45, ui/edit/editor/EditorDisplayPreferences.kt:30 |  |  |  |
| `key_ace_smart_hint_completion_delay_0` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`); 默认值 `default_key_ace_smart_hint_completion_delay` |  |  |  |  |
| `key_ace_smart_hint_completion_delay_100` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`) |  |  |  |  |
| `key_ace_smart_hint_completion_delay_250` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`) |  |  |  |  |
| `key_ace_smart_hint_completion_min_chars` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:263, ui/settings/EditorSettingActionRegistry.kt:44, ui/edit/editor/EditorDisplayPreferences.kt:29 |  |  |  |
| `key_ace_smart_hint_completion_min_chars_1` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars`; 默认值 `default_key_ace_smart_hint_completion_min_chars` |  |  |  |  |
| `key_ace_smart_hint_completion_min_chars_2` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars` |  |  |  |  |
| `key_ace_smart_hint_completion_min_chars_3` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars` |  |  |  |  |
| `key_ace_smart_hint_completion_trigger` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:262, ui/settings/EditorSettingActionRegistry.kt:43, ui/edit/editor/EditorDisplayPreferences.kt:28 |  |  |  |
| `key_ace_smart_hint_completion_trigger_dot` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`) |  |  |  |  |
| `key_ace_smart_hint_completion_trigger_identifier` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`); 默认值 `default_key_ace_smart_hint_completion_trigger` |  |  |  |  |
| `key_ace_smart_hint_completion_trigger_off` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`) |  |  |  |  |
| `key_ace_smart_hint_signature_trigger` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:265, ui/settings/EditorSettingActionRegistry.kt:46, ui/edit/editor/EditorDisplayPreferences.kt:31 |  |  |  |
| `key_ace_smart_hint_signature_trigger_cursor` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`); 默认值 `default_key_ace_smart_hint_signature_trigger` |  |  |  |  |
| `key_ace_smart_hint_signature_trigger_off` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`) |  |  |  |  |
| `key_ace_smart_hint_signature_trigger_typing` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`) |  |  |  |  |
| `key_ace_svg_gutter_icons_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/CodeEditorSettingsFragment.kt:254, ui/settings/EditorSettingActionRegistry.kt:56, ui/edit/editor/EditorDisplayPreferences.kt:21 |  |  |  |
| `key_ace_symbol_highlight_enabled` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:267, ui/settings/EditorSettingActionRegistry.kt:48, ui/edit/editor/EditorDisplayPreferences.kt:33 |  |  |  |
| `key_ace_touch_magnifier_enabled` | 代码编辑器设置 > 操作手势 | ui/settings/CodeEditorSettingsFragment.kt:255, ui/settings/EditorSettingActionRegistry.kt:51, ui/edit/editor/EditorDisplayPreferences.kt:25 |  |  |  |
| `key_ace_whitespaces_in_selection_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:251, ui/settings/EditorSettingActionRegistry.kt:32, ui/edit/editor/EditorDisplayPreferences.kt:24 |  |  |  |
| `key_ace_word_wrap_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:94, ui/settings/CodeEditorSettingsFragment.kt:258, ui/settings/EditorSettingActionRegistry.kt:30, ui/edit/editor/EditorDisplayPreferences.kt:7 |  |  |  |
| `key_ai_settings` | 开发者选项 > AI |  |  |  |  |
| `key_all_files_access` | 其他资源 res/xml/fragment_preferences_inrt.xml:42 | inrt/SettingsActivity.kt:56, inrt/SettingsActivity.kt:65 |  |  |  |
| `key_app_language` | 设置 > 外观 | core/pref/Language.kt:78, core/pref/Language.kt:97, core/pref/Language.kt:101, core/pref/Language.kt:105 |  |  |  |
| `key_app_language_ar` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:21 |  |  |  |
| `key_app_language_auto` | 候选值 `keys_app_language` (`key_app_language`); 默认值 `default_key_app_language` | core/pref/Language.kt:22 |  |  |  |
| `key_app_language_en` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:15 |  |  |  |
| `key_app_language_es` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:17 |  |  |  |
| `key_app_language_fr` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:16 |  |  |  |
| `key_app_language_ja` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:18 |  |  |  |
| `key_app_language_ko` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:19 |  |  |  |
| `key_app_language_ru` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:20 |  |  |  |
| `key_app_language_zh_hans` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:12 |  |  |  |
| `key_app_language_zh_hant_hk` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:13 |  |  |  |
| `key_app_language_zh_hant_tw` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:14 |  |  |  |
| `key_auto_check_for_updates` | 设置 > 更新 | core/pref/Pref.kt:176 |  |  |  |
| `key_auto_night_mode_enabled` |  | core/plugin/center/PluginCenterFragment.kt:981, core/pref/Pref.kt:49, ui/main/drawer/DrawerFragment.kt:640, ui/main/plugin/PluginFragment.kt:66, ui/settings/NightModePreference.kt:33, ui/settings/NightModePreference.kt:38 等 9 处 |  |  |  |
| `key_batch_rename_history_retention` |  | core/plugin/batchrename/BatchRenameHistoryPrefs.kt:13, core/plugin/batchrename/BatchRenameHistoryPrefs.kt:18 |  |  |  |
| `key_change_working_dir` | 设置 > 文件管理器 |  |  |  |  |
| `key_check_for_updates` | 设置 > 更新 |  |  |  |  |
| `key_check_for_updates_while_ignoring_the_local_version` | 开发者选项 > 更新 |  |  |  |  |
| `key_clear_explorer_playback_history` | 设置 > 文件管理器 |  |  |  |  |
| `key_client_mode_auto_reconnect` |  | pluginclient/DevPluginPrefs.kt:14, ui/devplugin/ClientModeManagerDialog.kt:70 |  |  |  |
| `key_client_mode_manager_clients_level` |  | ui/devplugin/ClientModeManagerDialog.kt:34 |  |  |  |
| `key_client_mode_manager_control_level` |  | ui/devplugin/ClientModeManagerDialog.kt:32 |  |  |  |
| `key_client_mode_manager_settings_collapsed` |  | ui/devplugin/ClientModeManagerDialog.kt:33 |  |  |  |
| `key_client_mode_manager_status_level` |  | ui/devplugin/ClientModeManagerDialog.kt:31 |  |  |  |
| `key_client_mode_reconnect_on_network_change` |  | pluginclient/DevPluginPrefs.kt:18, ui/devplugin/ClientModeManagerDialog.kt:76 |  |  |  |
| `key_client_mode_restore_on_launch` |  | pluginclient/DevPluginPrefs.kt:22, ui/devplugin/ClientModeManagerDialog.kt:82 |  |  |  |
| `key_client_socket_normally_closed` |  | pluginclient/JsonSocketClient.kt:476, pluginclient/JsonSocketClient.kt:477 |  |  |  |
| `key_codegen_model_current` |  | ui/settings/codegen/CodegenModelStoreFactory.kt:27 |  |  |  |
| `key_color_select_activity_legacy_layout` |  | theme/app/ColorSelectBaseActivity.kt:1045, theme/app/ColorSelectBaseActivity.kt:1046 |  |  |  |
| `key_console_element_time_format` |  | core/console/ConsoleTimeSettings.kt:16 |  |  |  |
| `key_console_element_time_visible` |  | core/console/ConsoleTimeSettings.kt:16 |  |  |  |
| `key_delete_permanently_also_clear_history` | 设置 > 回收站与版本历史 | storage/history/HistoryPrefs.kt:64 |  |  |  |
| `key_dialog_foreground_svc` |  |  |  |  |  |
| `key_dialog_layout_inspector_exit_tips` |  |  |  |  |  |
| `key_dialog_log_entries_exported` |  |  |  |  |  |
| `key_dialog_num_of_log_entries_exceeds_limit_for_sending` |  | core/console/ConsoleImpl.kt:419 |  |  |  |
| `key_dialog_selected_abi_is_unavailable` |  | ui/project/BuildActivity.java:1780 |  |  |  |
| `key_dialog_transparent_background_launcher_icon` |  | ui/settings/LauncherIconPreference.kt:88, ui/settings/LauncherIconPreference.kt:110 |  |  |  |
| `key_display_over_other_apps` | 其他资源 res/xml/fragment_preferences_inrt.xml:36 | inrt/SettingsActivity.kt:55 |  |  |  |
| `key_documentation_source` | 设置 > 配置 | ui/doc/DocumentationPageController.kt:273, util/DocsUtils.kt:23, util/DocsUtils.kt:57 |  |  |  |
| `key_documentation_source_local` | 候选值 `keys_documentation_source` (`key_documentation_source`); 默认值 `default_key_documentation_source` | util/DocsUtils.kt:24 |  |  |  |
| `key_documentation_source_online` | 候选值 `keys_documentation_source` (`key_documentation_source`) | ui/doc/DocumentationPageController.kt:274 |  |  |  |
| `key_drafts_max_days` | 回收站与版本历史设置 > 紧急草稿 | storage/history/HistoryPrefs.kt:53, ui/settings/RetentionLimitSettings.kt:40 |  |  |  |
| `key_drafts_max_total_bytes` | 回收站与版本历史设置 > 紧急草稿 | storage/history/HistoryPrefs.kt:58, ui/settings/RetentionLimitSettings.kt:41 |  |  |  |
| `key_editor_auto_show_log_bottom_sheet_on_run` | 设置 > 代码编辑器; 代码编辑器设置 > 日志 | ui/edit/EditorView.kt:5378, ui/settings/EditorSettingActionRegistry.kt:36, ui/settings/PreferencesFragment.kt:65, ui/settings/PreferencesFragment.kt:67, ui/settings/PreferencesFragment.kt:76 |  |  |  |
| `key_editor_breakpoint_markers_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/EditorSettingActionRegistry.kt:53, ui/edit/editor/EditorDisplayPreferences.kt:12 |  |  |  |
| `key_editor_code_highlight_threshold_bytes` | 代码编辑器设置 > 性能 | core/pref/Pref.kt:83, core/pref/Pref.kt:350, ui/settings/CodeEditorSettingsFragment.kt:269, ui/settings/EditorSettingActionRegistry.kt:57 |  |  |  |
| `key_editor_engine` | 设置 > 代码编辑器; 代码编辑器设置 > 常规 | ui/edit/EditorView.kt:934, ui/settings/CodeEditorSettingsFragment.kt:92, ui/settings/CodeEditorSettingsFragment.kt:99, ui/settings/EditorSettingActionRegistry.kt:19, ui/settings/PreferencesFragment.kt:63, ui/settings/PreferencesFragment.kt:75 等 7 处 |  |  |  |
| `key_editor_engine_ace` | 候选值 `keys_editor_engine` (`key_editor_engine`) |  |  |  |  |
| `key_editor_engine_native` | 候选值 `keys_editor_engine` (`key_editor_engine`); 默认值 `default_key_editor_engine` |  |  |  |  |
| `key_editor_fold_markers_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/CodeEditorSettingsFragment.kt:261, ui/settings/EditorSettingActionRegistry.kt:55, ui/edit/editor/EditorDisplayPreferences.kt:13 |  |  |  |
| `key_editor_fullscreen_mode` | 代码编辑器设置 > 外观 | ui/edit/EditorView.kt:748, ui/edit/EditorView.kt:935, ui/edit/EditorView.kt:938, ui/edit/EditorView.kt:1362, ui/edit/EditorView.kt:1366, ui/settings/EditorSettingActionRegistry.kt:24 |  |  |  |
| `key_editor_gutter_width_mode` | 代码编辑器设置 > 边栏标记 | ui/settings/CodeEditorSettingsFragment.kt:260, ui/settings/EditorSettingActionRegistry.kt:52, ui/edit/editor/EditorDisplayPreferences.kt:14 |  |  |  |
| `key_editor_gutter_width_mode_dynamic` | 候选值 `keys_editor_gutter_width_mode` (`key_editor_gutter_width_mode`); 默认值 `default_key_editor_gutter_width_mode` |  |  |  |  |
| `key_editor_gutter_width_mode_fixed` | 候选值 `keys_editor_gutter_width_mode` (`key_editor_gutter_width_mode`) |  |  |  |  |
| `key_editor_large_file_mode_threshold_bytes` | 代码编辑器设置 > 性能 | core/pref/Pref.kt:96, core/pref/Pref.kt:353, ui/settings/CodeEditorSettingsFragment.kt:270, ui/settings/EditorSettingActionRegistry.kt:58 |  |  |  |
| `key_editor_log_button_behavior` | 设置 > 代码编辑器; 代码编辑器设置 > 日志 | ui/edit/EditorView.kt:274, ui/settings/EditorSettingActionRegistry.kt:35, ui/settings/PreferencesFragment.kt:64, ui/settings/PreferencesFragment.kt:77 |  |  |  |
| `key_editor_log_button_behavior_bottom_sheet` | 候选值 `keys_editor_log_button_behavior` (`key_editor_log_button_behavior`); 默认值 `default_key_editor_log_button_behavior` |  |  |  |  |
| `key_editor_log_button_behavior_fullscreen_page` | 候选值 `keys_editor_log_button_behavior` (`key_editor_log_button_behavior`) | ui/edit/EditorView.kt:277 |  |  |  |
| `key_editor_log_panel_input_bar_visible` |  | ui/log/LogBottomSheet.kt:237 |  |  |  |
| `key_editor_log_panel_time_format` |  | core/console/ConsoleTimeSettings.kt:13 |  |  |  |
| `key_editor_log_panel_time_visible` |  | core/console/ConsoleTimeSettings.kt:13 |  |  |  |
| `key_editor_log_panel_typescript_console_visible` |  | ui/log/LogBottomSheet.kt:361, ui/log/LogBottomSheet.kt:374 |  |  |  |
| `key_editor_pinch_to_zoom_change_text_size` | 候选值 `keys_editor_pinch_to_zoom_strategy`; 默认值 `default_key_editor_pinch_to_zoom_strategy` | ui/settings/EditorPinchToZoomPreference.kt:84 |  |  |  |
| `key_editor_pinch_to_zoom_change_text_size_enabled` | 代码编辑器设置 > 操作手势 | ui/edit/EditorView.kt:944, ui/edit/editor/EditorPinchToZoomPreferences.kt:10, ui/edit/editor/EditorPinchToZoomPreferences.kt:19, ui/settings/EditorSettingActionRegistry.kt:50, ui/settings/EditorSettingActionRegistry.kt:11 |  |  |  |
| `key_editor_pinch_to_zoom_disable` | 候选值 `keys_editor_pinch_to_zoom_strategy` | ui/edit/editor/EditorPinchToZoomPreferences.kt:15, ui/edit/editor/EditorPinchToZoomPreferences.kt:23 |  |  |  |
| `key_editor_pinch_to_zoom_scale_view` |  | ui/settings/EditorPinchToZoomPreference.kt:83 |  |  |  |
| `key_editor_pinch_to_zoom_strategy` |  | ui/edit/editor/EditorPinchToZoomPreferences.kt:28, ui/settings/EditorPinchToZoomPreference.kt:48, ui/settings/EditorPinchToZoomPreference.kt:80, ui/settings/EditorSettingActionRegistry.kt:10 |  |  |  |
| `key_editor_shortcut_input_bar` | 代码编辑器设置 > 辅助 | ui/settings/EditorSettingActionRegistry.kt:37 |  |  |  |
| `key_editor_text_size` | 代码编辑器设置 > 外观 | core/pref/Pref.kt:344, core/pref/Pref.kt:347, ui/edit/EditorView.kt:927, ui/settings/EditorSettingActionRegistry.kt:23 |  |  |  |
| `key_editor_theme` | 代码编辑器设置 > 外观 | core/pref/Pref.kt:273, core/pref/Pref.kt:275, ui/edit/EditorView.kt:929, ui/settings/CodeEditorSettingsFragment.kt:101, ui/settings/CodeEditorSettingsFragment.kt:105, ui/settings/CodeEditorSettingsFragment.kt:278 等 7 处 |  |  |  |
| `key_editor_theme_dark` |  | ui/edit/EditorView.kt:931, ui/edit/theme/Themes.java:168, ui/edit/theme/Themes.java:208, ui/edit/theme/Themes.java:214, ui/edit/theme/Themes.java:219, ui/edit/theme/Themes.java:233 等 7 处 |  |  |  |
| `key_editor_theme_light` |  | ui/edit/EditorView.kt:930, ui/edit/theme/Themes.java:170, ui/edit/theme/Themes.java:209, ui/edit/theme/Themes.java:213, ui/edit/theme/Themes.java:222, ui/edit/theme/Themes.java:235 等 7 处 |  |  |  |
| `key_editor_theme_mode` | 代码编辑器设置 > 外观 | ui/edit/EditorView.kt:932, ui/edit/theme/Themes.java:252, ui/settings/CodeEditorSettingsFragment.kt:100, ui/settings/EditorSettingActionRegistry.kt:20 |  |  |  |
| `key_editor_theme_mode_separate` | 候选值 `keys_editor_theme_mode` (`key_editor_theme_mode`); 默认值 `default_key_editor_theme_mode` |  |  |  |  |
| `key_editor_theme_mode_unified` | 候选值 `keys_editor_theme_mode` (`key_editor_theme_mode`) | ui/edit/theme/Themes.java:255 |  |  |  |
| `key_editor_word_wrap_indent_style` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:307, ui/settings/CodeEditorSettingsFragment.kt:312, ui/settings/EditorSettingActionRegistry.kt:31, ui/edit/editor/EditorDisplayPreferences.kt:8 |  |  |  |
| `key_editor_word_wrap_indent_style_classic` | 候选值 `keys_editor_word_wrap_indent_style` (`key_editor_word_wrap_indent_style`) |  |  |  |  |
| `key_editor_word_wrap_indent_style_continuation` | 候选值 `keys_editor_word_wrap_indent_style` (`key_editor_word_wrap_indent_style`); 默认值 `default_key_editor_word_wrap_indent_style` |  |  |  |  |
| `key_enable_a11y_service_with_root_access` | 设置 > 无障碍服务; 其他资源 res/xml/fragment_preferences_inrt.xml:85 | core/pref/Pref.kt:316, inrt/Pref.kt:45, runtime/api/augment/settings/Settings.kt:26, ui/accessibility/A11yServiceManagerDialog.kt:187 |  |  |  |
| `key_enable_a11y_service_with_secure_settings` | 设置 > 无障碍服务 | core/pref/Pref.kt:322, ui/accessibility/A11yServiceManagerDialog.kt:193 |  |  |  |
| `key_enable_a11y_service_with_shizuku` | 设置 > 无障碍服务 | core/pref/Pref.kt:328, ui/accessibility/A11yServiceManagerDialog.kt:199 |  |  |  |
| `key_enable_observe_key` |  |  |  |  |  |
| `key_explorer_file_default_prefix` |  | ui/common/ScriptOperations.java:166, ui/common/ScriptOperations.java:205, ui/common/ScriptOperations.java:206 |  |  |  |
| `key_explorer_playback_history` | 设置 > 文件管理器 | core/pref/Pref.kt:248 |  |  |  |
| `key_extending_js_build_in_objects` | 设置 > 扩展性 | core/pref/Pref.kt:124 |  |  |  |
| `key_file_extensions` | 设置 > 文件管理器 | core/pref/Pref.kt:254, core/pref/Pref.kt:258, core/pref/Pref.kt:262, ui/explorer/ExplorerPathMenu.kt:311, ui/explorer/ExplorerPathMenu.kt:327 |  |  |  |
| `key_file_extensions_not_show` | 候选值 `keys_file_extensions` (`key_file_extensions`) | core/pref/Pref.kt:262 |  |  |  |
| `key_file_extensions_show_all` | 候选值 `keys_file_extensions` (`key_file_extensions`); 默认值 `default_key_file_extensions` | core/pref/Pref.kt:254 |  |  |  |
| `key_file_extensions_show_all_but_executable` | 候选值 `keys_file_extensions` (`key_file_extensions`) | core/pref/Pref.kt:258 |  |  |  |
| `key_floating_console_time_format` |  | core/console/ConsoleTimeSettings.kt:17 |  |  |  |
| `key_floating_console_time_visible` |  | core/console/ConsoleTimeSettings.kt:17 |  |  |  |
| `key_floating_menu_shown` |  | ui/floating/CircularMenu.java:495, ui/main/drawer/DrawerFragment.kt:279 |  |  |  |
| `key_foreground_service` | 其他资源 res/xml/fragment_preferences_inrt.xml:12 | inrt/SettingsActivity.kt:52, runtime/api/augment/settings/Settings.kt:29, ui/main/drawer/DrawerFragment.kt:249 |  |  |  |
| `key_gesture_observing` |  | core/pref/Pref.kt:229 |  |  |  |
| `key_github_token` | 开发者选项 > GitHub |  |  |  |  |
| `key_global_console_element_time_format` |  | core/console/ConsoleTimeSettings.kt:15 |  |  |  |
| `key_global_console_element_time_visible` |  | core/console/ConsoleTimeSettings.kt:15 |  |  |  |
| `key_guard_mode` | 设置 > 脚本运行 | core/pref/Pref.kt:155 |  |  |  |
| `key_hidden_files` | 设置 > 文件管理器 | core/pref/Pref.kt:243, ui/explorer/ExplorerPathMenu.kt:174 |  |  |  |
| `key_hidden_files_not_show` | 候选值 `keys_hidden_files` (`key_hidden_files`); 默认值 `default_key_hidden_files` | ui/explorer/ExplorerPathMenu.kt:174 |  |  |  |
| `key_hidden_files_show` | 候选值 `keys_hidden_files` (`key_hidden_files`) | core/pref/Pref.kt:243, ui/explorer/ExplorerPathMenu.kt:174 |  |  |  |
| `key_history_max_days` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:23, ui/settings/RetentionLimitSettings.kt:33 |  |  |  |
| `key_history_max_file_size_to_track_bytes` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:18, ui/settings/RetentionLimitSettings.kt:37 |  |  |  |
| `key_history_max_total_bytes` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:38, ui/main/drawer/DrawerFragment.kt:858, ui/settings/RetentionLimitSettings.kt:36, ui/storage/VersionHistoryActivity.kt:109 |  |  |  |
| `key_history_max_total_bytes_per_file` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:33, ui/settings/RetentionLimitSettings.kt:35 |  |  |  |
| `key_history_max_versions` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:28, ui/settings/RetentionLimitSettings.kt:34 |  |  |  |
| `key_ignored_updates` |  | network/UpdateIgnoreStore.kt:13, ui/settings/ManageIgnoredUpdatesPreference.kt:28, util/UpdateUtils.kt:27, util/UpdateUtils.kt:130, util/UpdateUtils.kt:137, util/UpdateUtils.kt:142 |  |  |  |
| `key_keep_screen_on_when_in_foreground` | 设置 > 外观 | AbstractAutoJs.kt:172, core/pref/Pref.kt:209, ui/main/MainActivity.kt:184, util/ViewUtils.kt:1292, util/ViewUtils.kt:1297, util/ViewUtils.kt:1303 等 7 处 |  |  |  |
| `key_keep_screen_on_when_in_foreground_all_pages` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`) | util/ViewUtils.kt:122, util/ViewUtils.kt:1297, util/ViewUtils.kt:1298 |  |  |  |
| `key_keep_screen_on_when_in_foreground_disabled` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`); 默认值 `default_key_keep_screen_on_when_in_foreground` | util/ViewUtils.kt:119, util/ViewUtils.kt:1292 |  |  |  |
| `key_keep_screen_on_when_in_foreground_enabled` |  | util/ViewUtils.kt:115, util/ViewUtils.kt:116 |  |  |  |
| `key_keep_screen_on_when_in_foreground_homepage_only` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`) | util/ViewUtils.kt:125, util/ViewUtils.kt:1303, util/ViewUtils.kt:1304 |  |  |  |
| `key_keep_screen_on_when_in_foreground_last_enabled` |  | util/ViewUtils.kt:125, util/ViewUtils.kt:126 |  |  |  |
| `key_last_no_newer_updates` |  | core/pref/Pref.kt:197, core/pref/Pref.kt:298, core/pref/Pref.kt:308 |  |  |  |
| `key_last_updates_auto_checked` |  | core/pref/Pref.kt:205, core/pref/Pref.kt:304, core/pref/Pref.kt:311 |  |  |  |
| `key_last_updates_checked` |  | core/pref/Pref.kt:181, core/pref/Pref.kt:295, core/pref/Pref.kt:310 |  |  |  |
| `key_last_updates_postponed` |  | core/pref/Pref.kt:201, core/pref/Pref.kt:301, core/pref/Pref.kt:309 |  |  |  |
| `key_launcher_icon` | 设置 > 配置 |  |  |  |  |
| `key_launcher_icon_adaptive` | 候选值 `keys_launcher_icon` (`key_launcher_icon`); 默认值 `default_key_launcher_icon` | ui/settings/LauncherIconPreference.kt:55, ui/settings/LauncherIconPreference.kt:94, ui/settings/LauncherIconPreference.kt:216 |  |  |  |
| `key_launcher_icon_transparent_background` | 候选值 `keys_launcher_icon` (`key_launcher_icon`) | ui/settings/LauncherIconPreference.kt:54, ui/settings/LauncherIconPreference.kt:215 |  |  |  |
| `key_launcher_shortcuts` | 设置 > 配置 |  |  |  |  |
| `key_layout_inspector_field_mode_introduced` |  | ui/floating/layoutinspector/search/QueryModeStore.kt:27, ui/floating/layoutinspector/search/QueryModeStore.kt:29 |  |  |  |
| `key_layout_inspector_filter_profile_selected` |  | ui/floating/layoutinspector/search/FilterProfileStore.kt:20, ui/floating/layoutinspector/search/FilterProfileStore.kt:29 |  |  |  |
| `key_layout_inspector_filter_profiles` |  | ui/floating/layoutinspector/search/FilterProfileStore.kt:19, ui/floating/layoutinspector/search/FilterProfileStore.kt:24 |  |  |  |
| `key_layout_inspector_node_copy_mode` |  | ui/floating/layoutinspector/NodeInfoDialog.kt:23, ui/floating/layoutinspector/NodeInfoDialog.kt:27 |  |  |  |
| `key_layout_inspector_query_history` |  | ui/floating/layoutinspector/search/QueryHistoryStore.kt:17, ui/floating/layoutinspector/search/QueryHistoryStore.kt:20 |  |  |  |
| `key_layout_inspector_query_mode` |  | ui/floating/layoutinspector/search/QueryModeStore.kt:17, ui/floating/layoutinspector/search/QueryModeStore.kt:22 |  |  |  |
| `key_layout_inspector_selector_mode_introduced` |  | ui/floating/layoutinspector/search/QueryModeStore.kt:33, ui/floating/layoutinspector/search/QueryModeStore.kt:35 |  |  |  |
| `key_local_network_permission` | 其他资源 res/xml/fragment_preferences_inrt.xml:24 | inrt/SettingsActivity.kt:54 |  |  |  |
| `key_log_activity_input_bar_visible` |  | ui/log/LogActivity.kt:77 |  |  |  |
| `key_log_activity_log_coloring` |  | ui/log/LogPageConsoleSettings.kt:39, ui/log/LogPageConsoleSettings.kt:41 |  |  |  |
| `key_log_activity_text_size` |  | ui/log/LogPageConsoleSettings.kt:48, ui/log/LogPageConsoleSettings.kt:68 |  |  |  |
| `key_log_activity_time_format` |  | core/console/ConsoleTimeSettings.kt:14 |  |  |  |
| `key_log_activity_time_visible` |  | core/console/ConsoleTimeSettings.kt:14 |  |  |  |
| `key_log_activity_typescript_console_visible` |  | ui/log/LogActivity.kt:184, ui/log/LogActivity.kt:202 |  |  |  |
| `key_mail_settings` | 开发者选项 > 邮件 |  |  |  |  |
| `key_manage_ignored_updates` | 设置 > 更新 |  |  |  |  |
| `key_mcp_server_normally_closed` |  | app/tool/McpServerTool.kt:168, app/tool/McpServerTool.kt:169 |  |  |  |
| `key_new_script_module_layer` | 开发者选项 > 脚本模块 | core/pref/Pref.kt:135 |  |  |  |
| `key_night_mode` | 设置 > 外观 | core/plugin/center/PluginCenterFragment.kt:979, ui/main/drawer/DrawerFragment.kt:623, ui/main/drawer/DrawerFragment.kt:628, ui/main/drawer/DrawerFragment.kt:659, ui/main/drawer/DrawerFragment.kt:664, ui/main/plugin/PluginFragment.kt:64 |  |  |  |
| `key_night_mode_always_off` | 候选值 `keys_night_mode` (`key_night_mode`) | ui/settings/NightModePreference.kt:54, util/ViewUtils.kt:1674 |  |  |  |
| `key_night_mode_always_on` | 候选值 `keys_night_mode` (`key_night_mode`) | util/ViewUtils.kt:1675 |  |  |  |
| `key_night_mode_enabled` |  | App.kt:188, core/plugin/center/PluginCenterFragment.kt:980, core/pref/Pref.kt:59, ui/main/drawer/DrawerFragment.kt:677, ui/main/plugin/PluginFragment.kt:65, ui/settings/NightModePreference.kt:34 等 10 处 |  |  |  |
| `key_night_mode_follow_system` | 候选值 `keys_night_mode` (`key_night_mode`); 默认值 `default_key_night_mode` | ui/settings/NightModePreference.kt:55, util/ViewUtils.kt:1676 |  |  |  |
| `key_night_mode_null` |  | util/ViewUtils.kt:1677 |  |  |  |
| `key_nodejs_doctor_report` | 开发者选项 > Node.js |  |  |  |  |
| `key_nodejs_inspector_enabled` | 开发者选项 > Node.js | engine/NodeJsRuntimePluginBridge.kt:78 |  |  |  |
| `key_not_showing_main_activity` | 其他资源 res/xml/fragment_preferences_inrt.xml:72 | inrt/Pref.kt:49, inrt/Pref.kt:75, runtime/api/augment/settings/Settings.kt:28 |  |  |  |
| `key_pc_server_address_history` |  | pluginclient/JsonSocketClient.kt:447, pluginclient/JsonSocketClient.kt:448 |  |  |  |
| `key_plugin_center_auto_check_updates` | 插件中心设置 > 更新 | core/plugin/center/PluginCenterFragment.kt:139, core/plugin/center/PluginOnlineSettingsStore.kt:25 |  |  |  |
| `key_plugin_center_last_online_check_ts` |  | core/plugin/center/PluginOnlineSettingsStore.kt:44, core/plugin/center/PluginOnlineSettingsStore.kt:49 |  |  |  |
| `key_plugin_center_show_official_plugins` | 插件中心设置 > 在线插件来源 | core/plugin/center/PluginCenterFragment.kt:137, core/plugin/center/PluginOnlineSettingsStore.kt:13 |  |  |  |
| `key_plugin_center_show_third_party_plugins` | 插件中心设置 > 在线插件来源 | core/plugin/center/PluginCenterFragment.kt:138, core/plugin/center/PluginOnlineSettingsStore.kt:19 |  |  |  |
| `key_post_notifications_permission` | 其他资源 res/xml/fragment_preferences_inrt.xml:30 | inrt/SettingsActivity.kt:53 |  |  |  |
| `key_post_notifications_permission_request_attempted` |  | permission/PostNotificationsPermission.kt:45, permission/PostNotificationsPermission.kt:61, ui/wizard/permission/PermissionWizardRunner.kt:122, ui/wizard/permission/PermissionWizardRunner.kt:128 |  |  |  |
| `key_pref_bundle_default_item` |  | theme/preference/MaterialListPreference.kt:74, ui/settings/LauncherIconPreference.kt:215, ui/settings/NightModePreference.kt:54 |  |  |  |
| `key_pref_bundle_disabled_items` |  | theme/preference/MaterialListPreference.kt:79, ui/settings/LauncherIconPreference.kt:216, ui/settings/NightModePreference.kt:55 |  |  |  |
| `key_record_toast` | 设置 > 录制脚本 | core/pref/Pref.kt:267 |  |  |  |
| `key_release_history` | 设置 > 更新 |  |  |  |  |
| `key_restart_strategy` | 设置 > 配置 | core/pref/Pref.kt:235 |  |  |  |
| `key_restart_strategy_quick` | 候选值 `keys_restart_strategy` (`key_restart_strategy`); 默认值 `default_key_restart_strategy` | core/pref/Pref.kt:235 |  |  |  |
| `key_restart_strategy_scheduled` | 候选值 `keys_restart_strategy` (`key_restart_strategy`) |  |  |  |  |
| `key_rhino_java_primitive_wrap` | 设置 > 扩展性 | rhino/AndroidContextFactory.kt:77 |  |  |  |
| `key_root_manager_apps_cache` |  | ui/accessibility/RootManagerAppsDialog.kt:117, ui/accessibility/RootManagerAppsDialog.kt:216 |  |  |  |
| `key_root_manager_apps_custom` |  | ui/accessibility/RootManagerAppsDialog.kt:115, ui/accessibility/RootManagerAppsDialog.kt:171, ui/accessibility/RootManagerAppsDialog.kt:173 |  |  |  |
| `key_root_mode` | 设置 > 脚本运行 | core/pref/Pref.kt:214, core/pref/Pref.kt:218 |  |  |  |
| `key_root_mode_auto_detect` | 候选值 `keys_root_mode` (`key_root_mode`); 默认值 `default_key_root_mode` | util/RootUtils.java:133 |  |  |  |
| `key_root_mode_force_non_root` | 候选值 `keys_root_mode` (`key_root_mode`) | util/RootUtils.java:132 |  |  |  |
| `key_root_mode_force_root` | 候选值 `keys_root_mode` (`key_root_mode`) | util/RootUtils.java:131 |  |  |  |
| `key_root_record_out_file_type` | 设置 > 录制脚本 | core/pref/Pref.kt:341 |  |  |  |
| `key_root_record_out_file_type_binary` | 候选值 `keys_root_record_out_file_type` (`key_root_record_out_file_type`); 默认值 `default_key_root_record_out_file_type` | core/pref/Pref.kt:341 |  |  |  |
| `key_root_record_out_file_type_js` | 候选值 `keys_root_record_out_file_type` (`key_root_record_out_file_type`) |  |  |  |  |
| `key_run_on_boot_inrt` | 其他资源 res/xml/fragment_preferences_inrt.xml:66 | inrt/Pref.kt:57, inrt/Pref.kt:76 |  |  |  |
| `key_scheduled_restart_backend` |  | core/pref/Pref.kt:239, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:40, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:90, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:91 |  |  |  |
| `key_scheduled_restart_backend_alarm_manager` |  | ui/settings/ScheduledRestartSettingsDialogBuilder.kt:41, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:72, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:91 |  |  |  |
| `key_scheduled_restart_backend_work_manager` | 默认值 `default_key_scheduled_restart_backend` | core/pref/Pref.kt:239, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:90 |  |  |  |
| `key_scheduled_restart_delay` |  | ui/settings/ScheduledRestartSettingsDialogBuilder.kt:34, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:93, util/IntentUtils.kt:373 |  |  |  |
| `key_screen_capture_request_delay` | 设置 > 脚本运行 | core/pref/Pref.kt:75, ui/settings/ScreenCaptureRequestDelayDialogBuilder.kt:29, ui/settings/ScreenCaptureRequestDelayDialogBuilder.kt:72 |  |  |  |
| `key_script_module_debug_print` | 开发者选项 > 脚本模块 | core/pref/Pref.kt:145 |  |  |  |
| `key_script_module_debug_print_disabled_modules` | 开发者选项 > 脚本模块 | core/pref/Pref.kt:149, core/pref/Pref.kt:150 |  |  |  |
| `key_scripts_after_app_restart` |  | util/IntentUtils.kt:439, util/IntentUtils.kt:477 |  |  |  |
| `key_server_address` |  | core/pref/Pref.kt:335, core/pref/Pref.kt:338 |  |  |  |
| `key_server_mode_lan_discovery` |  | pluginclient/DevPluginPrefs.kt:34, ui/devplugin/ServerModeManagerDialog.kt:89 |  |  |  |
| `key_server_mode_manager_clients_level` |  | ui/devplugin/ServerModeManagerDialog.kt:39 |  |  |  |
| `key_server_mode_manager_control_level` |  | ui/devplugin/ServerModeManagerDialog.kt:37 |  |  |  |
| `key_server_mode_manager_settings_collapsed` |  | ui/devplugin/ServerModeManagerDialog.kt:38 |  |  |  |
| `key_server_mode_manager_status_level` |  | ui/devplugin/ServerModeManagerDialog.kt:36 |  |  |  |
| `key_server_mode_notify_connection_changes` |  | pluginclient/DevPluginPrefs.kt:30, ui/devplugin/ServerModeManagerDialog.kt:83 |  |  |  |
| `key_server_mode_restore_on_launch` |  | pluginclient/DevPluginPrefs.kt:26, ui/devplugin/ServerModeManagerDialog.kt:77 |  |  |  |
| `key_server_socket_normally_closed` |  | pluginclient/JsonSocketServer.kt:167, pluginclient/JsonSocketServer.kt:169 |  |  |  |
| `key_setup_wizard_welcome_done` |  | ui/settings/ResetSetupWizardWelcomePreference.kt:28, ui/wizard/SetupWizards.kt:54, ui/wizard/SetupWizards.kt:84 |  |  |  |
| `key_setup_wizards` | 设置 > 配置 | ui/settings/PreferencesFragment.kt:24 |  |  |  |
| `key_stable_mode` | 设置 > 无障碍服务; 其他资源 res/xml/fragment_preferences_inrt.xml:92 | core/pref/Pref.kt:223, runtime/api/augment/settings/Settings.kt:25, ui/accessibility/A11yServiceManagerDialog.kt:202, ui/accessibility/A11yServiceManagerDialog.kt:403, ui/accessibility/A11yServiceManagerDialog.kt:428 |  |  |  |
| `key_theme_color` | 设置 > 外观 |  |  |  |  |
| `key_theme_color_accent` |  | theme/ThemeColor.kt:45, theme/ThemeColor.kt:60, theme/ThemeColor.kt:74 |  |  |  |
| `key_theme_color_primary` |  | theme/ThemeColor.kt:43, theme/ThemeColor.kt:59, theme/ThemeColor.kt:72 |  |  |  |
| `key_theme_color_primary_dark` |  | theme/ThemeColor.kt:44, theme/ThemeColor.kt:61, theme/ThemeColor.kt:73 |  |  |  |
| `key_timed_task_backend` | 设置 > 定时任务 | timing/TimedTaskScheduler.kt:32 |  |  |  |
| `key_timed_task_backend_alarm` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`); 默认值 `default_key_timed_task_backend` | timing/ExactAlarmPermissionHelper.kt:39, timing/TimedTaskScheduler.kt:35 |  |  |  |
| `key_timed_task_backend_job` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`) | timing/TimedTaskScheduler.kt:37 |  |  |  |
| `key_timed_task_backend_work` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`) | timing/TimedTaskScheduler.kt:36 |  |  |  |
| `key_trash_and_version_history_settings` | 设置 > 回收站与版本历史 |  |  |  |  |
| `key_trash_max_days` | 回收站与版本历史设置 > 回收站 | storage/history/HistoryPrefs.kt:43, ui/settings/RetentionLimitSettings.kt:38 |  |  |  |
| `key_trash_max_total_bytes` | 回收站与版本历史设置 > 回收站 | storage/history/HistoryPrefs.kt:48, ui/main/drawer/DrawerFragment.kt:825, ui/settings/RetentionLimitSettings.kt:39, ui/storage/TrashActivity.kt:115 |  |  |  |
| `key_updates_checked_states_cleared` | 开发者选项 > 更新 |  |  |  |  |
| `key_use_volume_control_record` | 设置 > 录制脚本 | core/pref/Pref.kt:169 |  |  |  |
| `key_use_volume_control_running` | 设置 > 脚本运行; 其他资源 res/xml/fragment_preferences_inrt.xml:60 | core/pref/Pref.kt:162, inrt/Pref.kt:53, runtime/api/augment/settings/Settings.kt:27 |  |  |  |
| `key_version_history_restore_does_not_auto_save_to_disk` |  | storage/history/VersionHistoryController.kt:443 |  |  |  |
| `key_working_directory` |  | util/WorkingDirectoryUtils.kt:21, util/WorkingDirectoryUtils.kt:28, util/WorkingDirectoryUtils.kt:36, util/WorkingDirectoryUtils.kt:63 |  |  |  |
| `key_working_directory_history` |  | util/WorkingDirectoryUtils.kt:16, util/WorkingDirectoryUtils.kt:17 |  |  |  |
| `key_working_directory_initialized` |  | util/WorkingDirectoryUtils.kt:58 |  |  |  |

## 3. 源码字面量键

字符串字面量形如 `"key_$_..."` 且不等于任何 `key_*` 资源值的键 (`*` 为模板变量). 存储列是按调用文件推测的结果: 文件内调用了 `getSharedPreferences(` 时取其文件名, 否则记为 `default`.

| 键 | 存储 (推测) | 引用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `key_$_ace_editor_font_name_sort_type_locked` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:91 |  |  |  |
| `key_$_ace_editor_font_preview_excluded_features` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:93 |  |  |  |
| `key_$_ace_editor_font_preview_excluded_types` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:94 |  |  |  |
| `key_$_ace_editor_font_selected_variant_` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:96 |  |  |  |
| `key_$_ace_editor_font_size_sort_type_locked` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:92 |  |  |  |
| `key_$_ace_editor_font_sort_mode` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:90 |  |  |  |
| `key_$_ace_lsp_file_types_revision` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:8 |  |  |  |
| `key_$_ace_semantic_java_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:13 |  |  |  |
| `key_$_ace_semantic_kotlin_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:14 |  |  |  |
| `key_$_ace_semantic_lua_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:12 |  |  |  |
| `key_$_ace_semantic_python_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:11 |  |  |  |
| `key_$_ace_semantic_typescript_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:10 |  |  |  |
| `key_$_dialog_terminal_tips` | default | core/plugin/terminal/TerminalLegacyCleanup.kt:29 |  |  |  |
| `key_$_editor_engine_selection_mode` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:12 |  |  |  |
| `key_$_enabled_plugin_$_*` | plugin_center_enable_state | core/plugin/center/PluginEnableStore.kt:68 |  |  |  |
| `key_$_ignored_plugin_$_*` | key_$_ignored_updates | network/UpdateIgnoreStore.kt:64 |  |  |  |
| `key_$_last_install_time_$_*` | plugin_center_recent | core/plugin/center/PluginRecentStore.kt:33 |  |  |  |
| `key_$_last_uninstall_time_$_*` | plugin_center_recent | core/plugin/center/PluginRecentStore.kt:35 |  |  |  |
| `key_$_permission_wizard` | default | ui/wizard/permission/PermissionWizardDialog.kt:33 |  |  |  |
| `key_$_plugin_abi_mismatch_download_prompt` | default | core/plugin/center/PluginAbiCompatibilityPrompt.kt:14 |  |  |  |
| `key_$_plugin_abi_mismatch_enable_prompt` | default | core/plugin/center/PluginAbiCompatibilityPrompt.kt:15 |  |  |  |
| `key_$_plugin_auth_$_*$_*` | plugin_center_authorization | core/plugin/center/PluginAuthorizationStore.kt:32 |  |  |  |
| `key_$_plugin_center_filter_enabled_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:13 |  |  |  |
| `key_$_plugin_center_filter_install_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:12 |  |  |  |
| `key_$_plugin_center_filter_mechanism_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:14 |  |  |  |
| `key_$_plugin_center_filter_source_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:15 |  |  |  |
| `key_$_plugin_center_filter_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:11 |  |  |  |
| `key_$_plugin_center_filter_updatable_only` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:16 |  |  |  |
| `key_$_plugin_center_hidden_packages` | plugin_center_visibility_state | core/plugin/center/PluginVisibilityStore.kt:13 |  |  |  |
| `key_$_plugin_center_hidden_source_` | plugin_center_visibility_state | core/plugin/center/PluginVisibilityStore.kt:15 |  |  |  |
| `key_$_plugin_center_hidden_title_` | plugin_center_visibility_state | core/plugin/center/PluginVisibilityStore.kt:14 |  |  |  |
| `key_$_plugin_center_sort_state` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:11 |  |  |  |
| `key_$_plugin_center_sort_state_ascending_` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:12 |  |  |  |
| `key_$_plugin_center_sort_state_mechanism_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:14 |  |  |  |
| `key_$_plugin_center_sort_state_official` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:14 |  |  |  |
| `key_$_plugin_center_sort_state_official_ascending_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:15 |  |  |  |
| `key_$_plugin_center_sort_state_official_mechanism_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:17 |  |  |  |
| `key_$_plugin_center_sort_state_official_secondary_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:16 |  |  |  |
| `key_$_plugin_center_sort_state_official_source_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:18 |  |  |  |
| `key_$_plugin_center_sort_state_secondary_` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:13 |  |  |  |
| `key_$_plugin_center_sort_state_source_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:15 |  |  |  |
| `key_$_plugin_center_sort_state_third_party` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:14 |  |  |  |
| `key_$_plugin_center_sort_state_third_party_ascending_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:15 |  |  |  |
| `key_$_plugin_center_sort_state_third_party_mechanism_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:17 |  |  |  |
| `key_$_plugin_center_sort_state_third_party_secondary_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:16 |  |  |  |
| `key_$_plugin_center_sort_state_third_party_source_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:18 |  |  |  |
| `key_$_plugin_hide_confirm_prompt` | default | core/plugin/center/PluginSettingsFragment.kt:1158 |  |  |  |
| `key_$_plugin_install_wizard` | plugin_install_wizard | core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:28 |  |  |  |
| `key_$_plugin_mount_enabled_$_` | default | core/plugin/center/PluginMountStore.kt:14 |  |  |  |
| `key_$_plugin_mount_object_name_$_` | default | core/plugin/center/PluginMountStore.kt:15 |  |  |  |
| `key_$_plugin_mount_packages` | default | core/plugin/center/PluginMountStore.kt:13 |  |  |  |
| `key_$_plugin_mount_point_$_` | default | core/plugin/center/PluginMountStore.kt:16 |  |  |  |
| `key_$_plugin_update_check_$_` | default | core/plugin/center/PluginUpdateCheckStore.kt:10 |  |  |  |
| `key_$_plugin_variant_priority_$_` | default | core/plugin/center/PluginVariantPriorityStore.kt:11 |  |  |  |
| `key_$_seen_ace_fallback_notice` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:14 |  |  |  |
| `key_$_seen_ace_large_file_notice` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:15 |  |  |  |
| `key_$_seen_editor_switch_undo_notice` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:13 |  |  |  |
| `key_$_suspended_plugin_$_*` | plugin_center_enable_state | core/plugin/center/PluginEnableStore.kt:70 |  |  |  |
| `key_$_terminal_` | default | core/plugin/terminal/TerminalLegacyCleanup.kt:26 |  |  |  |
| `key_*` | default | ui/floating/CircularMenuWindow.java:22 |  |  |  |
| `key_dont_show_main_activity` | default | runtime/api/augment/settings/Settings.kt:41 |  |  |  |
| `key_down` | default | core/ui/nativeview/ViewPrototype.kt:111, core/ui/nativeview/ViewPrototype.kt:115, execution/ScriptExecuteActivity.kt:338, runtime/api/Events.java:362 |  |  |  |
| `key_enable_a11y_service_with_root_access` | default | runtime/api/augment/settings/Settings.kt:37 |  |  |  |
| `key_enable_accessibility_service_by_root` | default | runtime/api/augment/settings/Settings.kt:36 |  |  |  |
| `key_first_using` | default | inrt/Pref.kt:18 |  |  |  |
| `key_foreground_service` | default | runtime/api/augment/settings/Settings.kt:44 |  |  |  |
| `key_inrt_applied_build_id` | default | inrt/Pref.kt:19 |  |  |  |
| `key_not_showing_main_activity` | default | runtime/api/augment/settings/Settings.kt:42 |  |  |  |
| `key_stable_mode` | default | runtime/api/augment/settings/Settings.kt:34 |  |  |  |
| `key_up` | default | core/ui/nativeview/ViewPrototype.kt:111, core/ui/nativeview/ViewPrototype.kt:117, runtime/api/Events.java:365 |  |  |  |
| `key_use_volume_control_running` | default | runtime/api/augment/settings/Settings.kt:39 |  |  |  |

设置页中不经字符串资源的键: `process_exit_history` (fragment_developer_options.xml:148, kind 未分类), `plugin_center_hidden_plugin_management` (fragment_plugin_center_preferences.xml:42, kind 未分类), `plugin_center_auto_mount_management` (fragment_plugin_center_preferences.xml:54, kind 未分类)

## 4. 命名 SharedPreferences

| 文件名 | 解析方式 | 调用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `FunctionsKeyboardHelper` | 常量 SHARE_PREFERENCE_NAME (ui/edit/keyboard/FunctionsKeyboardHelper.java:26) | ui/edit/keyboard/FunctionsKeyboardHelper.java:44 |  |  |  |
| `ScriptWidgets` | 字面量 | external/widget/ScriptWidgets.java:22 |  |  |  |
| `ace_action_history` | 常量 PREF_NAME (ui/edit/keyboard/AceActionHistoryStore.kt:10) | ui/edit/keyboard/AceActionHistoryStore.kt:40 |  |  |  |
| `ace_editor_font_ui_preferences` | 常量 SHARED_PREFERENCES_NAME (ui/edit/editor/ace/AceEditorFontUiPreferences.kt:89) | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:243 |  |  |  |
| `autojs.localstorage.*` | 人工: `.let` 的接收者是 `LocalStorage.NAME_PREFIX + name`; 拼接 | core/storage/LocalStorage.java:28, runtime/api/augment/storages/Storages.kt:48, runtime/api/augment/storages/Storages.kt:61 |  |  |  |
| `autojs.node.storage.*.*.*` | 函数 storagePreferencesName (engine/NodeBridgeProtocol.kt:3457) | engine/NodeBridgeProtocol.kt:3342 |  |  |  |
| `build_apk_config_profiles` | 常量 PREF_NAME (ui/project/BuildConfigStore.java:27) | ui/project/BuildConfigStore.java:294 |  |  |  |
| `bun_host_capability_grants` | 常量 SP_NAME (core/plugin/bun/BunHostCapabilityGrants.kt:61) | core/plugin/bun/BunHostCapabilityGrants.kt:67, core/plugin/bun/BunHostCapabilityGrants.kt:70, core/plugin/bun/BunHostCapabilityGrants.kt:80, core/plugin/bun/BunHostCapabilityGrants.kt:83 等 8 处 |  |  |  |
| `device_id_prefs` | 常量 SP_NAME (deviceid/DeviceIdProvider.kt:20) | deviceid/DeviceIdProvider.kt:33 |  |  |  |
| `editor_symbols_config` | 常量 PREF_NAME (ui/edit/keyboard/SymbolsConfigStore.kt:26) | ui/edit/keyboard/SymbolsConfigStore.kt:45 |  |  |  |
| `explorer_playback_history` | 常量 PREFERENCES_NAME (core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:79) | core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:13, core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:84, core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:96 |  |  |  |
| `github_auth` | 常量 PREF_NAME (network/GitHubTokenStore.kt:7) | network/GitHubTokenStore.kt:44 |  |  |  |
| `jvm_source_provider_selection` | 常量 PREFERENCES (core/plugin/jvm/JvmSourceProviderSelection.kt:8) | core/plugin/jvm/JvmSourceProviderSelection.kt:19, core/plugin/jvm/JvmSourceProviderSelection.kt:31 |  |  |  |
| `key_$_ignored_updates` | 字符串资源 key_ignored_updates | network/UpdateIgnoreStore.kt:13 |  |  |  |
| `lua-runtime-storage-v1-*` | 拼接 | engine/LuaHostStorage.kt:166 |  |  |  |
| `mcp_server_trust_confirmation` | 常量 CONFIRMATION_STORE (core/plugin/mcp/McpServerPluginInspector.kt:23) | app/tool/McpServerTool.kt:116, core/plugin/mcp/McpServerPluginInspector.kt:51 |  |  |  |
| `permission_wizard` | 构造参数 core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:26, ui/wizard/permission/PermissionWizardDialog.kt:31 | ui/wizard/WizardProfileStore.kt:27 |  |  |  |
| `plugin_center_attention` | 常量 SP (core/plugin/center/PluginCenterAttentionStore.kt:17) | core/plugin/center/PluginCenterAttentionStore.kt:76, core/plugin/center/PluginCenterAttentionStore.kt:113 |  |  |  |
| `plugin_center_authorization` | 常量 SP_NAME (core/plugin/center/PluginAuthorizationStore.kt:8) | core/plugin/center/PluginAuthorizationStore.kt:12, core/plugin/center/PluginAuthorizationStore.kt:18, core/plugin/center/PluginAuthorizationStore.kt:26 |  |  |  |
| `plugin_center_enable_state` | 常量 SP_NAME (core/plugin/center/PluginEnableStore.kt:9) | core/plugin/center/PluginEnableStore.kt:12, core/plugin/center/PluginEnableStore.kt:23, core/plugin/center/PluginEnableStore.kt:41, core/plugin/center/PluginEnableStore.kt:51 等 5 处 |  |  |  |
| `plugin_center_filter_state` | 常量 SP_NAME (core/plugin/center/PluginFilterStore.kt:10) | core/plugin/center/PluginFilterStore.kt:72 |  |  |  |
| `plugin_center_recent` | 常量 SP (core/plugin/center/PluginRecentStore.kt:9) | core/plugin/center/PluginRecentStore.kt:14, core/plugin/center/PluginRecentStore.kt:20, core/plugin/center/PluginRecentStore.kt:26, core/plugin/center/PluginRecentStore.kt:30 |  |  |  |
| `plugin_center_sort_state` | 常量 SP_NAME (core/plugin/center/PluginSortStore.kt:10); 常量 SP_NAME (core/plugin/center/PluginSortStoreOfficial.kt:13); 常量 SP_NAME (core/plugin/center/PluginSortStoreThirdParty.kt:13) | core/plugin/center/PluginSortStore.kt:17, core/plugin/center/PluginSortStoreOfficial.kt:20, core/plugin/center/PluginSortStoreThirdParty.kt:20 |  |  |  |
| `plugin_center_visibility_state` | 常量 SP_NAME (core/plugin/center/PluginVisibilityStore.kt:12) | core/plugin/center/PluginVisibilityStore.kt:18 |  |  |  |
| `plugin_install_wizard` | 字面量; 构造参数 core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:26, ui/wizard/permission/PermissionWizardDialog.kt:31 | core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:18, core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:22, ui/wizard/WizardProfileStore.kt:27 |  |  |  |
| `plugin_launcher_icons` | 常量 PREFS (core/plugin/center/PluginLauncherIconController.kt:38) | core/plugin/center/PluginLauncherIconController.kt:49 |  |  |  |
| `privileged_edit_backups` | 常量 PREFERENCES (model/explorer/PrivilegedBackupManager.kt:30) | model/explorer/PrivilegedBackupManager.kt:38, model/explorer/PrivilegedBackupManager.kt:44, model/explorer/PrivilegedBackupManager.kt:87, model/explorer/PrivilegedBackupManager.kt:90 等 5 处 |  |  |  |
| `runtime_task_intents` | 字面量 | engine/RuntimeTasksBridge.kt:155 |  |  |  |
| `three_stove_agent_trust_confirmation` | 常量 CONFIRMATION_STORE (core/plugin/agent/ThreeStoveAgentPluginInspector.kt:23) | core/plugin/agent/ThreeStoveAgentPluginInspector.kt:52, ui/settings/ThreeStoveAgentGuide.kt:68 |  |  |  |

## 5. 绕过 `Pref` 读默认偏好的文件

- core/pref/Pref.kt:38
- engine/NodeJsRuntimePluginBridge.kt:77
- engine/RootAutomatorEngine.kt:129,159
- inrt/Pref.kt:25
- runtime/api/Shell.java:144
- ui/common/NotAskAgainDialog.java:52,58
- ui/edit/FindOrReplaceDialogBuilder.kt:74,81,85
- ui/edit/debug/CodeEvaluateDialogBuilder.java:29
- ui/settings/codegen/CodegenModelStoreFactory.kt:26

## 6. filesDir / noBackupFilesDir 下的 JSON 文件

启发式: 源文件在非注释行引用 `filesDir` / `noBackupFilesDir` 且含 `.json` 字面量. 与 ROADMAP 1.1 的 14 个对照见表后.

| 源文件 | JSON 文件名 | 目录引用行 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| core/plugin/center/InstalledPluginMetadataStore.kt | `autojs6_installed_plugin_metadata_v1.json` | 163 |  |  |  |
| core/plugin/center/PluginIndexRepository.kt | `autojs6_plugin_index_v11.json`, `compat-matrix.json`, `plugins.generated.json`, `plugins.official.generated.json` | 470 |  |  |  |
| core/plugin/python/PythonPrivateExecutionSnapshot.kt | `.json` | 501 |  |  |  |
| engine/NodeLifecycleCheckpointStore.kt | `*.json` | 228,236 |  |  |  |
| engine/NodeWorkManagerBridgeScheduler.kt | `*.json`, `.json` | 458 |  |  |  |
| engine/RuntimeWebLocalDownloads.kt | `runtime-web-downloads/*.json` | 270 |  |  |  |
| model/explorer/PrivilegedBackupManager.kt | `*.json` | 75,283,311 |  |  |  |
| model/explorer/PrivilegedEditSessions.kt | `session.json` | 298,310,316,336 |  |  |  |
| model/explorer/PrivilegedFileOperationJournal.kt | `*.json` | 15,56 |  |  |  |
| ui/edit/editor/ace/font/FontCatalogRepository.kt | `catalog-v1.json`, `catalog-v1.sig.json` | 93 |  |  |  |
| ui/edit/editor/ace/font/FontStore.kt | `installed.json` | 26 |  |  |  |
| ui/project/BuildActivity.java | `.json` | 2036 |  |  |  |
| ui/settings/NodeDoctorReportGenerator.kt | `node-doctor-report.json` | 43 |  |  |  |

- ROADMAP 列出但启发式未命中: `ModelStore` (core/accessibility/codegen/store/ModelStore.kt) (目录或 JSON 文件名在其他类中拼接, 归属以 ROADMAP 为准)
- 启发式命中但 ROADMAP 未列出: 无
