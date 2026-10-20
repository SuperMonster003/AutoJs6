# 设置清单 (inventory)

由 `tools/settings/inventory.py` 生成, 不要手工编辑; kind / 分类 id / 备注来自 `tools/settings/classification.json`. 路线图见 [ROADMAP.md](ROADMAP.md) (P0.2 / P0.3).

- 生成日期: 2026-10-20, 扫描基线: `master@665c35482` (扫描范围内未提交文件 0 个)
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

kind 统计 (`key_*` 资源): Setting 123, State 40, Cache 1, ValueConstant 74, Excluded 29

## 2. `key_*` 字符串资源

| 键 | 出现页面与分类 | 引用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `key_a11y_service` |  | ui/main/drawer/DrawerFragment.kt:230 | State | `other` | 抽屉 "无障碍服务" 开关的镜像, DrawerMenuToggleableItem.sync() 按服务实际状态回写 |
| `key_a11y_service_manager_control_level` |  | ui/accessibility/A11yServiceManagerDialog.kt:129, ui/accessibility/A11yServiceManagerDialog.kt:312 | State | `other` | 无障碍服务管理器对话框的展开级别 |
| `key_a11y_service_manager_settings_collapsed` |  | ui/accessibility/A11yServiceManagerDialog.kt:180 | State | `other` | 管理器设置区折叠状态 |
| `key_a11y_service_manager_status_level` |  | ui/accessibility/A11yServiceManagerDialog.kt:128, ui/accessibility/A11yServiceManagerDialog.kt:293 | State | `other` | 管理器状态区展开级别 |
| `key_about_app_and_developer` | 设置 > 关于 |  | Excluded |  | 动作条目 (打开关于页), 不持久化值 |
| `key_about_app_tips` | 设置 > 关于 |  | Excluded |  | 动作条目 (提示对话框), 不持久化值 |
| `key_ace_custom_scrollbar_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:252, ui/settings/EditorSettingActionRegistry.kt:33, ui/edit/editor/EditorDisplayPreferences.kt:22 | Setting | `editor_appearance` | 开关 |
| `key_ace_editor_font` | 代码编辑器设置 > 外观 | ui/edit/EditorView.kt:928, ui/settings/CodeEditorSettingsFragment.kt:245, ui/settings/CodeEditorSettingsFragment.kt:287, ui/settings/EditorSettingActionRegistry.kt:22, ui/edit/editor/ace/AceEditorFontPreferences.kt:7 | Setting | `editor_appearance`, deployment `DOWNLOAD_EDITOR_FONT` | 字体 id; 字体未安装时作为部署项下载 (D20 / Q2) |
| `key_ace_editor_font_cascadia_code` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_fira_code` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_hack` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_ibm_plex_mono` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_intel_one_mono` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_iosevka` | 默认值 `default_key_ace_editor_font` |  | ValueConstant |  | 字体 id 常量, `default_key_ace_editor_font` 的目标 |
| `key_ace_editor_font_iosevka_slab` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_jetbrains_mono` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_source_code_pro` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_system_monospace` |  |  | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_font_ligatures_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:246, ui/settings/EditorSettingActionRegistry.kt:25, ui/edit/editor/EditorDisplayPreferences.kt:15 | Setting | `editor_appearance` | 开关 |
| `key_ace_font_styles_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:247, ui/settings/EditorSettingActionRegistry.kt:26, ui/edit/editor/EditorDisplayPreferences.kt:16 | Setting | `editor_appearance` | 开关 |
| `key_ace_highlight_indent_guides_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:250, ui/settings/EditorSettingActionRegistry.kt:29, ui/edit/editor/EditorDisplayPreferences.kt:20 | Setting | `editor_appearance` | 开关 |
| `key_ace_indent_guides_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:249, ui/settings/EditorSettingActionRegistry.kt:28, ui/edit/editor/EditorDisplayPreferences.kt:11 | Setting | `editor_appearance` | 开关 |
| `key_ace_inline_completion_preview_enabled` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:268, ui/settings/EditorSettingActionRegistry.kt:49, ui/edit/editor/EditorDisplayPreferences.kt:34 | Setting | `editor_smart_hint` | 开关 |
| `key_ace_language_support_matrix` | 代码编辑器设置 > 辅助 | ui/settings/CodeEditorSettingsFragment.kt:293, ui/settings/EditorSettingActionRegistry.kt:38 | Excluded |  | 动作条目 (语言支持矩阵对话框), 不持久化值 |
| `key_ace_line_numbers_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/EditorSettingActionRegistry.kt:54, ui/edit/editor/EditorDisplayPreferences.kt:9 | Setting | `editor_gutter` | 开关 |
| `key_ace_lsp_declaration_groups` | 代码编辑器设置 > 辅助 | ui/edit/EditorView.kt:946, ui/settings/CodeEditorSettingsFragment.kt:295, ui/settings/EditorSettingActionRegistry.kt:40, ui/edit/editor/ace/AceEditorLspPreferences.kt:9 | Setting | `editor_assistance` | 声明组集合 |
| `key_ace_lsp_enabled` | 代码编辑器设置 > 辅助 | ui/edit/EditorView.kt:945, ui/settings/CodeEditorSettingsFragment.kt:93, ui/settings/CodeEditorSettingsFragment.kt:256, ui/settings/EditorSettingActionRegistry.kt:39, ui/edit/editor/ace/AceEditorLspPreferences.kt:6 | Setting | `editor_assistance` | 开关 |
| `key_ace_lsp_file_types` | 代码编辑器设置 > 辅助 | ui/edit/EditorView.kt:947, ui/settings/CodeEditorSettingsFragment.kt:296, ui/settings/EditorSettingActionRegistry.kt:41, ui/edit/editor/ace/AceEditorLspPreferences.kt:7 | Setting | `editor_assistance` | 文件类型列表; 迁移版本号见字面量键 `key_$_ace_lsp_file_types_revision` |
| `key_ace_print_margin_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:248, ui/settings/EditorSettingActionRegistry.kt:27, ui/edit/editor/EditorDisplayPreferences.kt:10 | Setting | `editor_appearance` | 开关 |
| `key_ace_scrollbar_auto_hide_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:253, ui/settings/EditorSettingActionRegistry.kt:34, ui/edit/editor/EditorDisplayPreferences.kt:23 | Setting | `editor_appearance` | 开关 |
| `key_ace_semantic_languages` | 代码编辑器设置 > 辅助 | ui/settings/CodeEditorSettingsFragment.kt:297, ui/settings/EditorSettingActionRegistry.kt:42 | Excluded |  | 动作条目, 值存于 5 个 `key_$_ace_semantic_*_enabled` 字面量键 |
| `key_ace_smart_hint_arrow_overload_cycling` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:266, ui/settings/EditorSettingActionRegistry.kt:47, ui/edit/editor/EditorDisplayPreferences.kt:32 | Setting | `editor_smart_hint` | 开关 |
| `key_ace_smart_hint_completion_delay` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:264, ui/settings/EditorSettingActionRegistry.kt:45, ui/edit/editor/EditorDisplayPreferences.kt:30 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_completion_delay_0` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`); 默认值 `default_key_ace_smart_hint_completion_delay` |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_delay_100` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`) |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_delay_250` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`) |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_min_chars` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:263, ui/settings/EditorSettingActionRegistry.kt:44, ui/edit/editor/EditorDisplayPreferences.kt:29 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_completion_min_chars_1` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars`; 默认值 `default_key_ace_smart_hint_completion_min_chars` |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_min_chars_2` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars` |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_min_chars_3` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars` |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_trigger` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:262, ui/settings/EditorSettingActionRegistry.kt:43, ui/edit/editor/EditorDisplayPreferences.kt:28 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_completion_trigger_dot` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`) |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_trigger_identifier` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`); 默认值 `default_key_ace_smart_hint_completion_trigger` |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_trigger_off` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`) |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_signature_trigger` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:265, ui/settings/EditorSettingActionRegistry.kt:46, ui/edit/editor/EditorDisplayPreferences.kt:31 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_signature_trigger_cursor` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`); 默认值 `default_key_ace_smart_hint_signature_trigger` |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_signature_trigger_off` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`) |  | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_signature_trigger_typing` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`) |  | ValueConstant |  | 候选值 |
| `key_ace_svg_gutter_icons_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/CodeEditorSettingsFragment.kt:254, ui/settings/EditorSettingActionRegistry.kt:56, ui/edit/editor/EditorDisplayPreferences.kt:21 | Setting | `editor_gutter` | 开关 |
| `key_ace_symbol_highlight_enabled` | 代码编辑器设置 > 智能提示 | ui/settings/CodeEditorSettingsFragment.kt:267, ui/settings/EditorSettingActionRegistry.kt:48, ui/edit/editor/EditorDisplayPreferences.kt:33 | Setting | `editor_smart_hint` | 开关 |
| `key_ace_touch_magnifier_enabled` | 代码编辑器设置 > 操作手势 | ui/settings/CodeEditorSettingsFragment.kt:255, ui/settings/EditorSettingActionRegistry.kt:51, ui/edit/editor/EditorDisplayPreferences.kt:25 | Setting | `editor_gestures` | 开关 |
| `key_ace_whitespaces_in_selection_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:251, ui/settings/EditorSettingActionRegistry.kt:32, ui/edit/editor/EditorDisplayPreferences.kt:24 | Setting | `editor_appearance` | 开关 |
| `key_ace_word_wrap_enabled` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:94, ui/settings/CodeEditorSettingsFragment.kt:258, ui/settings/EditorSettingActionRegistry.kt:30, ui/edit/editor/EditorDisplayPreferences.kt:7 | Setting | `editor_appearance` | 开关 |
| `key_ai_settings` | 开发者选项 > AI |  | Excluded |  | 动作条目, 打开 AI 插件设置 (插件自身设置第一期不含, D3) |
| `key_all_files_access` | 其他资源 res/xml/fragment_preferences_inrt.xml:42 | inrt/SettingsActivity.kt:56, inrt/SettingsActivity.kt:65 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 |
| `key_app_language` | 设置 > 外观 | core/pref/Language.kt:78, core/pref/Language.kt:97, core/pref/Language.kt:101, core/pref/Language.kt:105 | Setting | `appearance` | 列表项 (Language) |
| `key_app_language_ar` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:21 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_auto` | 候选值 `keys_app_language` (`key_app_language`); 默认值 `default_key_app_language` | core/pref/Language.kt:22 | ValueConstant |  | Language 枚举候选值, 默认值 |
| `key_app_language_en` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:15 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_es` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:17 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_fr` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:16 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_ja` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:18 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_ko` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:19 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_ru` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:20 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_zh_hans` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:12 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_zh_hant_hk` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:13 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_zh_hant_tw` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:14 | ValueConstant |  | Language 枚举候选值 |
| `key_auto_check_for_updates` | 设置 > 更新 | core/pref/Pref.kt:176 | Setting | `updates` | 开关 |
| `key_auto_night_mode_enabled` |  | core/plugin/center/PluginCenterFragment.kt:981, core/pref/Pref.kt:49, ui/main/drawer/DrawerFragment.kt:640, ui/main/plugin/PluginFragment.kt:66, ui/settings/NightModePreference.kt:33, ui/settings/NightModePreference.kt:38 等 9 处 | Setting | `appearance` | 与 `key_night_mode` 一起由 NightModePreference 与抽屉写入 |
| `key_batch_rename_history_retention` |  | core/plugin/batchrename/BatchRenameHistoryPrefs.kt:13, core/plugin/batchrename/BatchRenameHistoryPrefs.kt:18 | Setting | `explorer` | 批量重命名历史保留批数 (BatchRenameHistoryPrefs) |
| `key_change_working_dir` | 设置 > 文件管理器 |  | Excluded |  | 动作条目, 工作目录存于 `key_working_directory` |
| `key_check_for_updates` | 设置 > 更新 |  | Excluded |  | 动作条目 (立即检查更新) |
| `key_check_for_updates_while_ignoring_the_local_version` | 开发者选项 > 更新 |  | Excluded |  | 动作条目 (开发者选项, 忽略本机版本检查一次) |
| `key_clear_explorer_playback_history` | 设置 > 文件管理器 |  | Excluded |  | 动作条目 (清除回放历史) |
| `key_client_mode_auto_reconnect` |  | pluginclient/DevPluginPrefs.kt:14, ui/devplugin/ClientModeManagerDialog.kt:70 | Setting | `connection` | 客户端模式管理器开关 |
| `key_client_mode_manager_clients_level` |  | ui/devplugin/ClientModeManagerDialog.kt:34 | State | `other` | 管理器对话框展开级别 |
| `key_client_mode_manager_control_level` |  | ui/devplugin/ClientModeManagerDialog.kt:32 | State | `other` | 管理器对话框展开级别 |
| `key_client_mode_manager_settings_collapsed` |  | ui/devplugin/ClientModeManagerDialog.kt:33 | State | `other` | 管理器设置区折叠状态 |
| `key_client_mode_manager_status_level` |  | ui/devplugin/ClientModeManagerDialog.kt:31 | State | `other` | 管理器对话框展开级别 |
| `key_client_mode_reconnect_on_network_change` |  | pluginclient/DevPluginPrefs.kt:18, ui/devplugin/ClientModeManagerDialog.kt:76 | Setting | `connection` | 客户端模式管理器开关 |
| `key_client_mode_restore_on_launch` |  | pluginclient/DevPluginPrefs.kt:22, ui/devplugin/ClientModeManagerDialog.kt:82 | Setting | `connection` | 客户端模式管理器开关 |
| `key_client_socket_normally_closed` |  | pluginclient/JsonSocketClient.kt:476, pluginclient/JsonSocketClient.kt:477 | State | `other` | 上次连接是否正常关闭, 用于启动时恢复 |
| `key_codegen_model_current` |  | ui/settings/codegen/CodegenModelStoreFactory.kt:27 | Setting | `layout_inspector` | 代码生成当前模型 id (经 selectionPreferenceKey 派生实际键); 模型文件不导出, 本机缺该模型时的处理待 P7.2 计划器定 |
| `key_color_select_activity_legacy_layout` |  | theme/app/ColorSelectBaseActivity.kt:1045, theme/app/ColorSelectBaseActivity.kt:1046 | State | `other` | ColorLibrariesActivity / ColorSelectActivity 打开前写入的界面模式, 不是用户设置 (Q13) |
| `key_console_element_time_format` |  | core/console/ConsoleTimeSettings.kt:16 | Setting | `console` | ConsoleTimeSettings.CONSOLE_ELEMENT |
| `key_console_element_time_visible` |  | core/console/ConsoleTimeSettings.kt:16 | Setting | `console` | ConsoleTimeSettings.CONSOLE_ELEMENT |
| `key_delete_permanently_also_clear_history` | 设置 > 回收站与版本历史 | storage/history/HistoryPrefs.kt:64 | Setting | `history` | 开关 |
| `key_dialog_foreground_svc` |  |  | State | `other` | 不再提示类键名, 源码与资源均无引用 (Q12) |
| `key_dialog_layout_inspector_exit_tips` |  |  | State | `other` | 不再提示类键名, 只在注释掉的代码中出现 (Q12) |
| `key_dialog_log_entries_exported` |  |  | State | `other` | 不再提示类键名, 源码与资源均无引用 (Q12) |
| `key_dialog_num_of_log_entries_exceeds_limit_for_sending` |  | core/console/ConsoleImpl.kt:419 | State | `other` | NotAskAgainDialog 不再提示 |
| `key_dialog_selected_abi_is_unavailable` |  | ui/project/BuildActivity.java:1780 | State | `other` | NotAskAgainDialog 不再提示 (BuildActivity) |
| `key_dialog_transparent_background_launcher_icon` |  | ui/settings/LauncherIconPreference.kt:88, ui/settings/LauncherIconPreference.kt:110 | State | `other` | NotAskAgainDialog 不再提示 |
| `key_display_over_other_apps` | 其他资源 res/xml/fragment_preferences_inrt.xml:36 | inrt/SettingsActivity.kt:55 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 |
| `key_documentation_source` | 设置 > 配置 | ui/doc/DocumentationPageController.kt:273, util/DocsUtils.kt:23, util/DocsUtils.kt:57 | Setting | `general` | 列表项 |
| `key_documentation_source_local` | 候选值 `keys_documentation_source` (`key_documentation_source`); 默认值 `default_key_documentation_source` | util/DocsUtils.kt:24 | ValueConstant |  | 候选值, 默认值 |
| `key_documentation_source_online` | 候选值 `keys_documentation_source` (`key_documentation_source`) | ui/doc/DocumentationPageController.kt:274 | ValueConstant |  | 候选值 |
| `key_drafts_max_days` | 回收站与版本历史设置 > 紧急草稿 | storage/history/HistoryPrefs.kt:53, ui/settings/RetentionLimitSettings.kt:40 | Setting | `history` | 保留策略 |
| `key_drafts_max_total_bytes` | 回收站与版本历史设置 > 紧急草稿 | storage/history/HistoryPrefs.kt:58, ui/settings/RetentionLimitSettings.kt:41 | Setting | `history` | 保留策略 |
| `key_editor_auto_show_log_bottom_sheet_on_run` | 设置 > 代码编辑器; 代码编辑器设置 > 日志 | ui/edit/EditorView.kt:5378, ui/settings/EditorSettingActionRegistry.kt:36, ui/settings/PreferencesFragment.kt:65, ui/settings/PreferencesFragment.kt:67, ui/settings/PreferencesFragment.kt:76 | Setting | `editor_log` | 开关, 设置页与代码编辑器设置两处 |
| `key_editor_breakpoint_markers_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/EditorSettingActionRegistry.kt:53, ui/edit/editor/EditorDisplayPreferences.kt:12 | Setting | `editor_gutter` | 开关 |
| `key_editor_code_highlight_threshold_bytes` | 代码编辑器设置 > 性能 | core/pref/Pref.kt:83, core/pref/Pref.kt:350, ui/settings/CodeEditorSettingsFragment.kt:269, ui/settings/EditorSettingActionRegistry.kt:57 | Setting | `editor_performance` | 阈值 |
| `key_editor_engine` | 设置 > 代码编辑器; 代码编辑器设置 > 常规 | ui/edit/EditorView.kt:934, ui/settings/CodeEditorSettingsFragment.kt:92, ui/settings/CodeEditorSettingsFragment.kt:99, ui/settings/EditorSettingActionRegistry.kt:19, ui/settings/PreferencesFragment.kt:63, ui/settings/PreferencesFragment.kt:75 等 7 处 | Setting | `editor_general` | 列表项, 与字面量键 `key_$_editor_engine_selection_mode` 一起写入 |
| `key_editor_engine_ace` | 候选值 `keys_editor_engine` (`key_editor_engine`) |  | ValueConstant |  | 候选值 |
| `key_editor_engine_native` | 候选值 `keys_editor_engine` (`key_editor_engine`); 默认值 `default_key_editor_engine` |  | ValueConstant |  | 候选值, 默认值 |
| `key_editor_fold_markers_enabled` | 代码编辑器设置 > 边栏标记 | ui/settings/CodeEditorSettingsFragment.kt:261, ui/settings/EditorSettingActionRegistry.kt:55, ui/edit/editor/EditorDisplayPreferences.kt:13 | Setting | `editor_gutter` | 开关 |
| `key_editor_fullscreen_mode` | 代码编辑器设置 > 外观 | ui/edit/EditorView.kt:748, ui/edit/EditorView.kt:935, ui/edit/EditorView.kt:938, ui/edit/EditorView.kt:1362, ui/edit/EditorView.kt:1366, ui/settings/EditorSettingActionRegistry.kt:24 | Setting | `editor_appearance` | 开关, 编辑器菜单也会写入 |
| `key_editor_gutter_width_mode` | 代码编辑器设置 > 边栏标记 | ui/settings/CodeEditorSettingsFragment.kt:260, ui/settings/EditorSettingActionRegistry.kt:52, ui/edit/editor/EditorDisplayPreferences.kt:14 | Setting | `editor_gutter` | 列表项 |
| `key_editor_gutter_width_mode_dynamic` | 候选值 `keys_editor_gutter_width_mode` (`key_editor_gutter_width_mode`); 默认值 `default_key_editor_gutter_width_mode` |  | ValueConstant |  | 候选值, 默认值 |
| `key_editor_gutter_width_mode_fixed` | 候选值 `keys_editor_gutter_width_mode` (`key_editor_gutter_width_mode`) |  | ValueConstant |  | 候选值 |
| `key_editor_large_file_mode_threshold_bytes` | 代码编辑器设置 > 性能 | core/pref/Pref.kt:96, core/pref/Pref.kt:353, ui/settings/CodeEditorSettingsFragment.kt:270, ui/settings/EditorSettingActionRegistry.kt:58 | Setting | `editor_performance` | 阈值 |
| `key_editor_log_button_behavior` | 设置 > 代码编辑器; 代码编辑器设置 > 日志 | ui/edit/EditorView.kt:274, ui/settings/EditorSettingActionRegistry.kt:35, ui/settings/PreferencesFragment.kt:64, ui/settings/PreferencesFragment.kt:77 | Setting | `editor_log` | 列表项, 设置页与代码编辑器设置两处 |
| `key_editor_log_button_behavior_bottom_sheet` | 候选值 `keys_editor_log_button_behavior` (`key_editor_log_button_behavior`); 默认值 `default_key_editor_log_button_behavior` |  | ValueConstant |  | 候选值, 默认值 |
| `key_editor_log_button_behavior_fullscreen_page` | 候选值 `keys_editor_log_button_behavior` (`key_editor_log_button_behavior`) | ui/edit/EditorView.kt:277 | ValueConstant |  | 候选值 |
| `key_editor_log_panel_input_bar_visible` |  | ui/log/LogBottomSheet.kt:237 | Setting | `editor_log` | 日志面板输入栏 (LogPageConsoleSettings) |
| `key_editor_log_panel_time_format` |  | core/console/ConsoleTimeSettings.kt:13 | Setting | `editor_log` | ConsoleTimeSettings.EDITOR_PANEL |
| `key_editor_log_panel_time_visible` |  | core/console/ConsoleTimeSettings.kt:13 | Setting | `editor_log` | ConsoleTimeSettings.EDITOR_PANEL |
| `key_editor_log_panel_typescript_console_visible` |  | ui/log/LogBottomSheet.kt:361, ui/log/LogBottomSheet.kt:374 | Setting | `editor_log` | 日志面板开关 |
| `key_editor_pinch_to_zoom_change_text_size` | 候选值 `keys_editor_pinch_to_zoom_strategy`; 默认值 `default_key_editor_pinch_to_zoom_strategy` | ui/settings/EditorPinchToZoomPreference.kt:84 | ValueConstant |  | 旧策略键 `key_editor_pinch_to_zoom_strategy` 的候选值, `default_key_editor_pinch_to_zoom_strategy` 的目标 |
| `key_editor_pinch_to_zoom_change_text_size_enabled` | 代码编辑器设置 > 操作手势 | ui/edit/EditorView.kt:944, ui/edit/editor/EditorPinchToZoomPreferences.kt:10, ui/edit/editor/EditorPinchToZoomPreferences.kt:19, ui/settings/EditorSettingActionRegistry.kt:50, ui/settings/EditorSettingActionRegistry.kt:11 | Setting | `editor_gestures` | 开关, 取代旧策略键 |
| `key_editor_pinch_to_zoom_disable` | 候选值 `keys_editor_pinch_to_zoom_strategy` | ui/edit/editor/EditorPinchToZoomPreferences.kt:15, ui/edit/editor/EditorPinchToZoomPreferences.kt:23 | ValueConstant |  | 旧策略键的候选值, 迁移时比较 |
| `key_editor_pinch_to_zoom_scale_view` |  | ui/settings/EditorPinchToZoomPreference.kt:83 | ValueConstant |  | 旧策略键的候选值, 只在未被设置页引用的 EditorPinchToZoomPreference 中使用 |
| `key_editor_pinch_to_zoom_strategy` |  | ui/edit/editor/EditorPinchToZoomPreferences.kt:28, ui/settings/EditorPinchToZoomPreference.kt:48, ui/settings/EditorPinchToZoomPreference.kt:80, ui/settings/EditorSettingActionRegistry.kt:10 | State | `other` | 旧版策略设置, 只作 migrateLegacyPreferenceIfNeeded 的迁移来源; 写入方 EditorPinchToZoomPreference 已不在任何设置页 |
| `key_editor_shortcut_input_bar` | 代码编辑器设置 > 辅助 | ui/settings/EditorSettingActionRegistry.kt:37 | Excluded |  | 动作条目, 打开 SymbolsSettingsActivity (数据在命名存储 `editor_symbols_config`) |
| `key_editor_text_size` | 代码编辑器设置 > 外观 | core/pref/Pref.kt:344, core/pref/Pref.kt:347, ui/edit/EditorView.kt:927, ui/settings/EditorSettingActionRegistry.kt:23 | Setting | `editor_appearance` | 字号 |
| `key_editor_theme` | 代码编辑器设置 > 外观 | core/pref/Pref.kt:273, core/pref/Pref.kt:275, ui/edit/EditorView.kt:929, ui/settings/CodeEditorSettingsFragment.kt:101, ui/settings/CodeEditorSettingsFragment.kt:105, ui/settings/CodeEditorSettingsFragment.kt:278 等 7 处 | Setting | `editor_appearance` | 统一模式下的主题名 |
| `key_editor_theme_dark` |  | ui/edit/EditorView.kt:931, ui/edit/theme/Themes.java:168, ui/edit/theme/Themes.java:208, ui/edit/theme/Themes.java:214, ui/edit/theme/Themes.java:219, ui/edit/theme/Themes.java:233 等 7 处 | Setting | `editor_appearance` | 分离模式下的深色主题名 (Themes.java) |
| `key_editor_theme_light` |  | ui/edit/EditorView.kt:930, ui/edit/theme/Themes.java:170, ui/edit/theme/Themes.java:209, ui/edit/theme/Themes.java:213, ui/edit/theme/Themes.java:222, ui/edit/theme/Themes.java:235 等 7 处 | Setting | `editor_appearance` | 分离模式下的浅色主题名 (Themes.java) |
| `key_editor_theme_mode` | 代码编辑器设置 > 外观 | ui/edit/EditorView.kt:932, ui/edit/theme/Themes.java:252, ui/settings/CodeEditorSettingsFragment.kt:100, ui/settings/EditorSettingActionRegistry.kt:20 | Setting | `editor_appearance` | 列表项 |
| `key_editor_theme_mode_separate` | 候选值 `keys_editor_theme_mode` (`key_editor_theme_mode`); 默认值 `default_key_editor_theme_mode` |  | ValueConstant |  | 候选值, 默认值 |
| `key_editor_theme_mode_unified` | 候选值 `keys_editor_theme_mode` (`key_editor_theme_mode`) | ui/edit/theme/Themes.java:255 | ValueConstant |  | 候选值 |
| `key_editor_word_wrap_indent_style` | 代码编辑器设置 > 外观 | ui/settings/CodeEditorSettingsFragment.kt:307, ui/settings/CodeEditorSettingsFragment.kt:312, ui/settings/EditorSettingActionRegistry.kt:31, ui/edit/editor/EditorDisplayPreferences.kt:8 | Setting | `editor_appearance` | 列表项 |
| `key_editor_word_wrap_indent_style_classic` | 候选值 `keys_editor_word_wrap_indent_style` (`key_editor_word_wrap_indent_style`) |  | ValueConstant |  | 候选值 |
| `key_editor_word_wrap_indent_style_continuation` | 候选值 `keys_editor_word_wrap_indent_style` (`key_editor_word_wrap_indent_style`); 默认值 `default_key_editor_word_wrap_indent_style` |  | ValueConstant |  | 候选值, 默认值 |
| `key_enable_a11y_service_with_root_access` | 设置 > 无障碍服务; 其他资源 res/xml/fragment_preferences_inrt.xml:85 | core/pref/Pref.kt:316, inrt/Pref.kt:45, runtime/api/augment/settings/Settings.kt:26, ui/accessibility/A11yServiceManagerDialog.kt:187 | Setting | `a11y` | 开关, 也是脚本 API settings 的 ROOT_ACCESSIBILITY_SERVICE |
| `key_enable_a11y_service_with_secure_settings` | 设置 > 无障碍服务 | core/pref/Pref.kt:322, ui/accessibility/A11yServiceManagerDialog.kt:193 | Setting | `a11y` | 开关 |
| `key_enable_a11y_service_with_shizuku` | 设置 > 无障碍服务 | core/pref/Pref.kt:328, ui/accessibility/A11yServiceManagerDialog.kt:199 | Setting | `a11y` | 开关 |
| `key_enable_observe_key` |  |  | Setting | `a11y` | 按 ROADMAP 附录 A 归 a11y (按键观察), 但源码与资源均无读写方 (Q12) |
| `key_explorer_file_default_prefix` |  | ui/common/ScriptOperations.java:166, ui/common/ScriptOperations.java:205, ui/common/ScriptOperations.java:206 | Setting | `explorer` | 新文件默认前缀序号 (ScriptOperations) |
| `key_explorer_playback_history` | 设置 > 文件管理器 | core/pref/Pref.kt:248 | Setting | `explorer` | 开关; 历史数据在命名存储 `explorer_playback_history` (永久排除) |
| `key_extending_js_build_in_objects` | 设置 > 扩展性 | core/pref/Pref.kt:124 | Setting | `extensibility` | 开关 |
| `key_file_extensions` | 设置 > 文件管理器 | core/pref/Pref.kt:254, core/pref/Pref.kt:258, core/pref/Pref.kt:262, ui/explorer/ExplorerPathMenu.kt:311, ui/explorer/ExplorerPathMenu.kt:327 | Setting | `explorer` | 列表项, 文件管理器菜单也会写入 |
| `key_file_extensions_not_show` | 候选值 `keys_file_extensions` (`key_file_extensions`) | core/pref/Pref.kt:262 | ValueConstant |  | 候选值 |
| `key_file_extensions_show_all` | 候选值 `keys_file_extensions` (`key_file_extensions`); 默认值 `default_key_file_extensions` | core/pref/Pref.kt:254 | ValueConstant |  | 候选值, 默认值 |
| `key_file_extensions_show_all_but_executable` | 候选值 `keys_file_extensions` (`key_file_extensions`) | core/pref/Pref.kt:258 | ValueConstant |  | 候选值 |
| `key_floating_console_time_format` |  | core/console/ConsoleTimeSettings.kt:17 | Setting | `console` | ConsoleTimeSettings.FLOATING_WINDOW |
| `key_floating_console_time_visible` |  | core/console/ConsoleTimeSettings.kt:17 | Setting | `console` | ConsoleTimeSettings.FLOATING_WINDOW |
| `key_floating_menu_shown` |  | ui/floating/CircularMenu.java:495, ui/main/drawer/DrawerFragment.kt:279 | State | `other` | 抽屉 "悬浮按钮" 开关的镜像, 按悬浮窗实际状态回写 |
| `key_foreground_service` | 其他资源 res/xml/fragment_preferences_inrt.xml:12 | inrt/SettingsActivity.kt:52, runtime/api/augment/settings/Settings.kt:29, ui/main/drawer/DrawerFragment.kt:249 | State | `other` | 主应用中是抽屉 "前台服务" 开关的镜像; 脚本 API settings 与 inrt 把它当设置读写 (Q13) |
| `key_gesture_observing` |  | core/pref/Pref.kt:229 | Setting | `a11y` | 开关 (手势观察) |
| `key_github_token` | 开发者选项 > GitHub |  | Excluded |  | 动作条目, 令牌存于命名存储 `github_auth` (credentials) |
| `key_global_console_element_time_format` |  | core/console/ConsoleTimeSettings.kt:15 | Setting | `console` | ConsoleTimeSettings.GLOBAL_CONSOLE_ELEMENT |
| `key_global_console_element_time_visible` |  | core/console/ConsoleTimeSettings.kt:15 | Setting | `console` | ConsoleTimeSettings.GLOBAL_CONSOLE_ELEMENT |
| `key_guard_mode` | 设置 > 脚本运行 | core/pref/Pref.kt:155 | Setting | `running` | 开关 |
| `key_hidden_files` | 设置 > 文件管理器 | core/pref/Pref.kt:243, ui/explorer/ExplorerPathMenu.kt:174 | Setting | `explorer` | 列表项, 文件管理器菜单也会写入 |
| `key_hidden_files_not_show` | 候选值 `keys_hidden_files` (`key_hidden_files`); 默认值 `default_key_hidden_files` | ui/explorer/ExplorerPathMenu.kt:174 | ValueConstant |  | 候选值, 默认值 |
| `key_hidden_files_show` | 候选值 `keys_hidden_files` (`key_hidden_files`) | core/pref/Pref.kt:243, ui/explorer/ExplorerPathMenu.kt:174 | ValueConstant |  | 候选值 |
| `key_history_max_days` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:23, ui/settings/RetentionLimitSettings.kt:33 | Setting | `history` | 保留策略 |
| `key_history_max_file_size_to_track_bytes` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:18, ui/settings/RetentionLimitSettings.kt:37 | Setting | `history` | 保留策略 |
| `key_history_max_total_bytes` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:38, ui/main/drawer/DrawerFragment.kt:858, ui/settings/RetentionLimitSettings.kt:36, ui/storage/VersionHistoryActivity.kt:109 | Setting | `history` | 保留策略 |
| `key_history_max_total_bytes_per_file` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:33, ui/settings/RetentionLimitSettings.kt:35 | Setting | `history` | 保留策略 |
| `key_history_max_versions` | 回收站与版本历史设置 > 版本历史 | storage/history/HistoryPrefs.kt:28, ui/settings/RetentionLimitSettings.kt:34 | Setting | `history` | 保留策略 |
| `key_ignored_updates` |  | network/UpdateIgnoreStore.kt:13, ui/settings/ManageIgnoredUpdatesPreference.kt:28, util/UpdateUtils.kt:27, util/UpdateUtils.kt:130, util/UpdateUtils.kt:137, util/UpdateUtils.kt:142 | Setting | `updates` | 默认偏好中的忽略版本列表 (Gson LinkedHashSet); 同一字符串也是 UpdateIgnoreStore 命名存储的文件名 |
| `key_keep_screen_on_when_in_foreground` | 设置 > 外观 | AbstractAutoJs.kt:172, core/pref/Pref.kt:209, ui/main/MainActivity.kt:184, util/ViewUtils.kt:1292, util/ViewUtils.kt:1297, util/ViewUtils.kt:1303 等 7 处 | Setting | `appearance` | 列表项 |
| `key_keep_screen_on_when_in_foreground_all_pages` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`) | util/ViewUtils.kt:122, util/ViewUtils.kt:1297, util/ViewUtils.kt:1298 | ValueConstant |  | 候选值 |
| `key_keep_screen_on_when_in_foreground_disabled` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`); 默认值 `default_key_keep_screen_on_when_in_foreground` | util/ViewUtils.kt:119, util/ViewUtils.kt:1292 | ValueConstant |  | 候选值, 默认值 |
| `key_keep_screen_on_when_in_foreground_enabled` |  | util/ViewUtils.kt:115, util/ViewUtils.kt:116 | Setting | `appearance` | 与主键一起由 ViewUtils 写入的布尔镜像, 须与主键同进同出 |
| `key_keep_screen_on_when_in_foreground_homepage_only` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`) | util/ViewUtils.kt:125, util/ViewUtils.kt:1303, util/ViewUtils.kt:1304 | ValueConstant |  | 候选值 |
| `key_keep_screen_on_when_in_foreground_last_enabled` |  | util/ViewUtils.kt:125, util/ViewUtils.kt:126 | State | `other` | 抽屉开关恢复用的上次启用模式 |
| `key_last_no_newer_updates` |  | core/pref/Pref.kt:197, core/pref/Pref.kt:298, core/pref/Pref.kt:308 | State | `other` | 时间戳 |
| `key_last_updates_auto_checked` |  | core/pref/Pref.kt:205, core/pref/Pref.kt:304, core/pref/Pref.kt:311 | State | `other` | 时间戳 |
| `key_last_updates_checked` |  | core/pref/Pref.kt:181, core/pref/Pref.kt:295, core/pref/Pref.kt:310 | State | `other` | 时间戳 |
| `key_last_updates_postponed` |  | core/pref/Pref.kt:201, core/pref/Pref.kt:301, core/pref/Pref.kt:309 | State | `other` | 时间戳 |
| `key_launcher_icon` | 设置 > 配置 |  | Setting | `general` | 列表项, LauncherIconPreference.persistString; 生效需切换组件 (APPLY) |
| `key_launcher_icon_adaptive` | 候选值 `keys_launcher_icon` (`key_launcher_icon`); 默认值 `default_key_launcher_icon` | ui/settings/LauncherIconPreference.kt:55, ui/settings/LauncherIconPreference.kt:94, ui/settings/LauncherIconPreference.kt:216 | ValueConstant |  | 候选值, 默认值 |
| `key_launcher_icon_transparent_background` | 候选值 `keys_launcher_icon` (`key_launcher_icon`) | ui/settings/LauncherIconPreference.kt:54, ui/settings/LauncherIconPreference.kt:215 | ValueConstant |  | 候选值 |
| `key_launcher_shortcuts` | 设置 > 配置 |  | Excluded |  | 动作条目 (启动器快捷方式对话框), 不持久化值 |
| `key_layout_inspector_field_mode_introduced` |  | ui/floating/layoutinspector/search/QueryModeStore.kt:27, ui/floating/layoutinspector/search/QueryModeStore.kt:29 | State | `other` | 功能介绍已展示 |
| `key_layout_inspector_filter_profile_selected` |  | ui/floating/layoutinspector/search/FilterProfileStore.kt:20, ui/floating/layoutinspector/search/FilterProfileStore.kt:29 | Setting | `layout_inspector` | 选中的筛选配置档 id |
| `key_layout_inspector_filter_profiles` |  | ui/floating/layoutinspector/search/FilterProfileStore.kt:19, ui/floating/layoutinspector/search/FilterProfileStore.kt:24 | Setting | `layout_inspector` | 筛选配置档 JSON |
| `key_layout_inspector_node_copy_mode` |  | ui/floating/layoutinspector/NodeInfoDialog.kt:23, ui/floating/layoutinspector/NodeInfoDialog.kt:27 | Setting | `layout_inspector` | 节点复制模式 |
| `key_layout_inspector_query_history` |  | ui/floating/layoutinspector/search/QueryHistoryStore.kt:17, ui/floating/layoutinspector/search/QueryHistoryStore.kt:20 | State | `other` | 查询历史 JSON |
| `key_layout_inspector_query_mode` |  | ui/floating/layoutinspector/search/QueryModeStore.kt:17, ui/floating/layoutinspector/search/QueryModeStore.kt:22 | Setting | `layout_inspector` | 查询模式 |
| `key_layout_inspector_selector_mode_introduced` |  | ui/floating/layoutinspector/search/QueryModeStore.kt:33, ui/floating/layoutinspector/search/QueryModeStore.kt:35 | State | `other` | 功能介绍已展示; 资源值缺少 `$_` 中缀 (键名不得改, R1) |
| `key_local_network_permission` | 其他资源 res/xml/fragment_preferences_inrt.xml:24 | inrt/SettingsActivity.kt:54 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 |
| `key_log_activity_input_bar_visible` |  | ui/log/LogActivity.kt:77 | Setting | `console` | 日志页 (LogPageConsoleSettings) |
| `key_log_activity_log_coloring` |  | ui/log/LogPageConsoleSettings.kt:39, ui/log/LogPageConsoleSettings.kt:41 | Setting | `console` | 日志页 |
| `key_log_activity_text_size` |  | ui/log/LogPageConsoleSettings.kt:48, ui/log/LogPageConsoleSettings.kt:68 | Setting | `console` | 日志页 |
| `key_log_activity_time_format` |  | core/console/ConsoleTimeSettings.kt:14 | Setting | `console` | ConsoleTimeSettings.LOG_ACTIVITY |
| `key_log_activity_time_visible` |  | core/console/ConsoleTimeSettings.kt:14 | Setting | `console` | ConsoleTimeSettings.LOG_ACTIVITY |
| `key_log_activity_typescript_console_visible` |  | ui/log/LogActivity.kt:184, ui/log/LogActivity.kt:202 | Setting | `console` | 日志页 |
| `key_mail_settings` | 开发者选项 > 邮件 |  | Excluded |  | 动作条目, 打开邮件插件设置 (插件自身设置第一期不含, D3) |
| `key_manage_ignored_updates` | 设置 > 更新 |  | Excluded |  | 动作条目 (管理忽略的更新) |
| `key_mcp_server_normally_closed` |  | app/tool/McpServerTool.kt:168, app/tool/McpServerTool.kt:169 | State | `other` | 上次是否正常关闭, 用于启动时恢复 |
| `key_new_script_module_layer` | 开发者选项 > 脚本模块 | core/pref/Pref.kt:135 | Setting | `developer` | 开关 |
| `key_night_mode` | 设置 > 外观 | core/plugin/center/PluginCenterFragment.kt:979, ui/main/drawer/DrawerFragment.kt:623, ui/main/drawer/DrawerFragment.kt:628, ui/main/drawer/DrawerFragment.kt:659, ui/main/drawer/DrawerFragment.kt:664, ui/main/plugin/PluginFragment.kt:64 | Setting | `appearance` | 列表项 (ViewUtils.MODE) |
| `key_night_mode_always_off` | 候选值 `keys_night_mode` (`key_night_mode`) | ui/settings/NightModePreference.kt:54, util/ViewUtils.kt:1674 | ValueConstant |  | 候选值 (MODE.DAY) |
| `key_night_mode_always_on` | 候选值 `keys_night_mode` (`key_night_mode`) | util/ViewUtils.kt:1675 | ValueConstant |  | 候选值 (MODE.NIGHT) |
| `key_night_mode_enabled` |  | App.kt:188, core/plugin/center/PluginCenterFragment.kt:980, core/pref/Pref.kt:59, ui/main/drawer/DrawerFragment.kt:677, ui/main/plugin/PluginFragment.kt:65, ui/settings/NightModePreference.kt:34 等 10 处 | Setting | `appearance` | 与 `key_night_mode` 一起由 NightModePreference 与抽屉写入 |
| `key_night_mode_follow_system` | 候选值 `keys_night_mode` (`key_night_mode`); 默认值 `default_key_night_mode` | ui/settings/NightModePreference.kt:55, util/ViewUtils.kt:1676 | ValueConstant |  | 候选值 (MODE.FOLLOW), 默认值 |
| `key_night_mode_null` |  | util/ViewUtils.kt:1677 | ValueConstant |  | ViewUtils.MODE.NULL, 不在列表候选中 |
| `key_nodejs_doctor_report` | 开发者选项 > Node.js |  | Excluded |  | 动作条目 (生成 Node.js 诊断报告) |
| `key_nodejs_inspector_enabled` | 开发者选项 > Node.js | engine/NodeJsRuntimePluginBridge.kt:78 | Setting | `developer` | 开关 |
| `key_not_showing_main_activity` | 其他资源 res/xml/fragment_preferences_inrt.xml:72 | inrt/Pref.kt:49, inrt/Pref.kt:75, runtime/api/augment/settings/Settings.kt:28 | Excluded |  | inrt 运行配置 (打包时由 launchConfig 写入); 主应用只经脚本 API settings 读写, 无消费方 |
| `key_pc_server_address_history` |  | pluginclient/JsonSocketClient.kt:447, pluginclient/JsonSocketClient.kt:448 | State | `other` | 地址历史 (Gson LinkedHashSet) |
| `key_plugin_center_auto_check_updates` | 插件中心设置 > 更新 | core/plugin/center/PluginCenterFragment.kt:139, core/plugin/center/PluginOnlineSettingsStore.kt:25 | Setting | `plugin_center` | 开关 |
| `key_plugin_center_last_online_check_ts` |  | core/plugin/center/PluginOnlineSettingsStore.kt:44, core/plugin/center/PluginOnlineSettingsStore.kt:49 | State | `other` | 时间戳 |
| `key_plugin_center_show_official_plugins` | 插件中心设置 > 在线插件来源 | core/plugin/center/PluginCenterFragment.kt:137, core/plugin/center/PluginOnlineSettingsStore.kt:13 | Setting | `plugin_center` | 开关 |
| `key_plugin_center_show_third_party_plugins` | 插件中心设置 > 在线插件来源 | core/plugin/center/PluginCenterFragment.kt:138, core/plugin/center/PluginOnlineSettingsStore.kt:19 | Setting | `plugin_center` | 开关 |
| `key_post_notifications_permission` | 其他资源 res/xml/fragment_preferences_inrt.xml:30 | inrt/SettingsActivity.kt:53 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 |
| `key_post_notifications_permission_request_attempted` |  | permission/PostNotificationsPermission.kt:45, permission/PostNotificationsPermission.kt:61, ui/wizard/permission/PermissionWizardRunner.kt:122, ui/wizard/permission/PermissionWizardRunner.kt:128 | State | `other` | 是否已请求过通知权限 |
| `key_pref_bundle_default_item` |  | theme/preference/MaterialListPreference.kt:74, ui/settings/LauncherIconPreference.kt:215, ui/settings/NightModePreference.kt:54 | Excluded |  | MaterialListPreference 的 Bundle 参数名, 不是偏好键 (附录 A.2) |
| `key_pref_bundle_disabled_items` |  | theme/preference/MaterialListPreference.kt:79, ui/settings/LauncherIconPreference.kt:216, ui/settings/NightModePreference.kt:55 | Excluded |  | MaterialListPreference 的 Bundle 参数名, 不是偏好键 (附录 A.2) |
| `key_record_toast` | 设置 > 录制脚本 | core/pref/Pref.kt:267 | Setting | `record` | 开关 |
| `key_release_history` | 设置 > 更新 |  | Excluded |  | 动作条目 (发行历史) |
| `key_restart_strategy` | 设置 > 配置 | core/pref/Pref.kt:235 | Setting | `general` | 列表项 |
| `key_restart_strategy_quick` | 候选值 `keys_restart_strategy` (`key_restart_strategy`); 默认值 `default_key_restart_strategy` | core/pref/Pref.kt:235 | ValueConstant |  | 候选值, 默认值 |
| `key_restart_strategy_scheduled` | 候选值 `keys_restart_strategy` (`key_restart_strategy`) |  | ValueConstant |  | 候选值 |
| `key_rhino_java_primitive_wrap` | 设置 > 扩展性 | rhino/AndroidContextFactory.kt:77 | Setting | `extensibility` | 开关 |
| `key_root_manager_apps_cache` |  | ui/accessibility/RootManagerAppsDialog.kt:117, ui/accessibility/RootManagerAppsDialog.kt:216 | Cache |  | Root 管理器应用列表缓存 (附录 A.2) |
| `key_root_manager_apps_custom` |  | ui/accessibility/RootManagerAppsDialog.kt:115, ui/accessibility/RootManagerAppsDialog.kt:171, ui/accessibility/RootManagerAppsDialog.kt:173 | Setting | `running` | 用户追加的 Root 管理器包名 (StringSet) |
| `key_root_mode` | 设置 > 脚本运行 | core/pref/Pref.kt:214, core/pref/Pref.kt:218 | Setting | `running` | 列表项 (RootUtils.RootMode) |
| `key_root_mode_auto_detect` | 候选值 `keys_root_mode` (`key_root_mode`); 默认值 `default_key_root_mode` | util/RootUtils.java:133 | ValueConstant |  | 候选值, 默认值 |
| `key_root_mode_force_non_root` | 候选值 `keys_root_mode` (`key_root_mode`) | util/RootUtils.java:132 | ValueConstant |  | 候选值 |
| `key_root_mode_force_root` | 候选值 `keys_root_mode` (`key_root_mode`) | util/RootUtils.java:131 | ValueConstant |  | 候选值 |
| `key_root_record_out_file_type` | 设置 > 录制脚本 | core/pref/Pref.kt:341 | Setting | `record` | 列表项 |
| `key_root_record_out_file_type_binary` | 候选值 `keys_root_record_out_file_type` (`key_root_record_out_file_type`); 默认值 `default_key_root_record_out_file_type` | core/pref/Pref.kt:341 | ValueConstant |  | 候选值, 默认值 |
| `key_root_record_out_file_type_js` | 候选值 `keys_root_record_out_file_type` (`key_root_record_out_file_type`) |  | ValueConstant |  | 候选值 |
| `key_run_on_boot_inrt` | 其他资源 res/xml/fragment_preferences_inrt.xml:66 | inrt/Pref.kt:57, inrt/Pref.kt:76 | Excluded |  | inrt 运行配置 (打包时由 launchConfig 写入) |
| `key_scheduled_restart_backend` |  | core/pref/Pref.kt:239, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:40, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:90, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:91 | Setting | `general` | 计划重启后端 (ScheduledRestartSettingsDialogBuilder) |
| `key_scheduled_restart_backend_alarm_manager` |  | ui/settings/ScheduledRestartSettingsDialogBuilder.kt:41, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:72, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:91 | ValueConstant |  | 候选值 |
| `key_scheduled_restart_backend_work_manager` | 默认值 `default_key_scheduled_restart_backend` | core/pref/Pref.kt:239, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:90 | ValueConstant |  | 候选值, 默认值 |
| `key_scheduled_restart_delay` |  | ui/settings/ScheduledRestartSettingsDialogBuilder.kt:34, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:93, util/IntentUtils.kt:373 | Setting | `general` | 计划重启延迟 |
| `key_screen_capture_request_delay` | 设置 > 脚本运行 | core/pref/Pref.kt:75, ui/settings/ScreenCaptureRequestDelayDialogBuilder.kt:29, ui/settings/ScreenCaptureRequestDelayDialogBuilder.kt:72 | Setting | `running` | 截图授权延迟 |
| `key_script_module_debug_print` | 开发者选项 > 脚本模块 | core/pref/Pref.kt:145 | Setting | `developer` | 开关 |
| `key_script_module_debug_print_disabled_modules` | 开发者选项 > 脚本模块 | core/pref/Pref.kt:149, core/pref/Pref.kt:150 | Setting | `developer` | StringSet |
| `key_scripts_after_app_restart` |  | util/IntentUtils.kt:439, util/IntentUtils.kt:477 | Excluded |  | 重启前写入, 重启后由 runAfterRestartIfNeeded 消费的一次性交接值 (同附录 A.2 的运行时状态) |
| `key_server_address` |  | core/pref/Pref.kt:335, core/pref/Pref.kt:338 | Setting | `connection` | 服务端地址 |
| `key_server_mode_lan_discovery` |  | pluginclient/DevPluginPrefs.kt:34, ui/devplugin/ServerModeManagerDialog.kt:89 | Setting | `connection` | 服务端模式管理器开关 |
| `key_server_mode_manager_clients_level` |  | ui/devplugin/ServerModeManagerDialog.kt:39 | State | `other` | 管理器对话框展开级别 |
| `key_server_mode_manager_control_level` |  | ui/devplugin/ServerModeManagerDialog.kt:37 | State | `other` | 管理器对话框展开级别 |
| `key_server_mode_manager_settings_collapsed` |  | ui/devplugin/ServerModeManagerDialog.kt:38 | State | `other` | 管理器设置区折叠状态 |
| `key_server_mode_manager_status_level` |  | ui/devplugin/ServerModeManagerDialog.kt:36 | State | `other` | 管理器对话框展开级别 |
| `key_server_mode_notify_connection_changes` |  | pluginclient/DevPluginPrefs.kt:30, ui/devplugin/ServerModeManagerDialog.kt:83 | Setting | `connection` | 服务端模式管理器开关 |
| `key_server_mode_restore_on_launch` |  | pluginclient/DevPluginPrefs.kt:26, ui/devplugin/ServerModeManagerDialog.kt:77 | Setting | `connection` | 服务端模式管理器开关 |
| `key_server_socket_normally_closed` |  | pluginclient/JsonSocketServer.kt:167, pluginclient/JsonSocketServer.kt:169 | State | `other` | 上次是否正常关闭, 用于启动时恢复 |
| `key_setup_wizard_welcome_done` |  | ui/settings/ResetSetupWizardWelcomePreference.kt:28, ui/wizard/SetupWizards.kt:54, ui/wizard/SetupWizards.kt:84 | State | `other` | 欢迎向导已完成 |
| `key_setup_wizards` | 设置 > 配置 | ui/settings/PreferencesFragment.kt:24 | Excluded |  | 动作条目 (打开设置向导) |
| `key_stable_mode` | 设置 > 无障碍服务; 其他资源 res/xml/fragment_preferences_inrt.xml:92 | core/pref/Pref.kt:223, runtime/api/augment/settings/Settings.kt:25, ui/accessibility/A11yServiceManagerDialog.kt:202, ui/accessibility/A11yServiceManagerDialog.kt:403, ui/accessibility/A11yServiceManagerDialog.kt:428 | Setting | `a11y` | 开关, 也出现在无障碍服务管理器与脚本 API settings |
| `key_theme_color` | 设置 > 外观 |  | Excluded |  | 动作条目 (打开主题色选择), 值存于 `key_theme_color_primary` / `_primary_dark` / `_accent` |
| `key_theme_color_accent` |  | theme/ThemeColor.kt:45, theme/ThemeColor.kt:60, theme/ThemeColor.kt:74 | Setting | `appearance` | 主题色三项之一 |
| `key_theme_color_primary` |  | theme/ThemeColor.kt:43, theme/ThemeColor.kt:59, theme/ThemeColor.kt:72 | Setting | `appearance` | 主题色三项之一 |
| `key_theme_color_primary_dark` |  | theme/ThemeColor.kt:44, theme/ThemeColor.kt:61, theme/ThemeColor.kt:73 | Setting | `appearance` | 主题色三项之一 |
| `key_timed_task_backend` | 设置 > 定时任务 | timing/TimedTaskScheduler.kt:32 | Setting | `timed_task` | 列表项 |
| `key_timed_task_backend_alarm` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`); 默认值 `default_key_timed_task_backend` | timing/ExactAlarmPermissionHelper.kt:39, timing/TimedTaskScheduler.kt:35 | ValueConstant |  | 候选值, 默认值; 同一字符串还被 ExactAlarmPermissionHelper 用作 NotAskAgainDialog 的键 (默认偏好中的 Boolean 状态, P4.1 另行声明 StateKey) |
| `key_timed_task_backend_job` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`) | timing/TimedTaskScheduler.kt:37 | ValueConstant |  | 候选值 |
| `key_timed_task_backend_work` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`) | timing/TimedTaskScheduler.kt:36 | ValueConstant |  | 候选值 |
| `key_trash_and_version_history_settings` | 设置 > 回收站与版本历史 |  | Excluded |  | 动作条目 (打开回收站与版本历史子页) |
| `key_trash_max_days` | 回收站与版本历史设置 > 回收站 | storage/history/HistoryPrefs.kt:43, ui/settings/RetentionLimitSettings.kt:38 | Setting | `history` | 保留策略 |
| `key_trash_max_total_bytes` | 回收站与版本历史设置 > 回收站 | storage/history/HistoryPrefs.kt:48, ui/main/drawer/DrawerFragment.kt:825, ui/settings/RetentionLimitSettings.kt:39, ui/storage/TrashActivity.kt:115 | Setting | `history` | 保留策略 |
| `key_updates_checked_states_cleared` | 开发者选项 > 更新 |  | Excluded |  | 动作条目 (开发者选项, 清除检查更新状态) |
| `key_use_volume_control_record` | 设置 > 录制脚本 | core/pref/Pref.kt:169 | Setting | `record` | 开关 |
| `key_use_volume_control_running` | 设置 > 脚本运行; 其他资源 res/xml/fragment_preferences_inrt.xml:60 | core/pref/Pref.kt:162, inrt/Pref.kt:53, runtime/api/augment/settings/Settings.kt:27 | Setting | `running` | 开关, 也是脚本 API settings 的 STOP_ALL_ON_VOLUME_UP |
| `key_version_history_restore_does_not_auto_save_to_disk` |  | storage/history/VersionHistoryController.kt:443 | State | `other` | VersionHistoryController 中 NotAskAgainDialog 的不再提示键, 不是设置 (Q13) |
| `key_working_directory` |  | util/WorkingDirectoryUtils.kt:21, util/WorkingDirectoryUtils.kt:28, util/WorkingDirectoryUtils.kt:36, util/WorkingDirectoryUtils.kt:63 | Setting | `explorer`, deployment `CREATE_DIRECTORY` | 工作目录; 目标不存在时作为部署项创建 (D20 / Q1) |
| `key_working_directory_history` |  | util/WorkingDirectoryUtils.kt:16, util/WorkingDirectoryUtils.kt:17 | State | `other` | 工作目录历史 (Gson LinkedHashSet) |
| `key_working_directory_initialized` |  | util/WorkingDirectoryUtils.kt:58 | State | `other` | 首次确定工作目录的标记 |

## 3. 源码字面量键

字符串字面量形如 `"key_$_..."` 且不等于任何 `key_*` 资源值的键 (`*` 为模板变量). 存储列是按调用文件推测的结果: 文件内调用了 `getSharedPreferences(` 时取其文件名, 否则记为 `default`.

| 键 | 存储 (推测) | 引用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `key_$_ace_editor_font_name_sort_type_locked` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:91 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_preview_excluded_features` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:93 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_preview_excluded_types` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:94 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_selected_variant_` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:96 | Setting | `editor_keyboard` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_size_sort_type_locked` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:92 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_sort_mode` | ace_editor_font_ui_preferences | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:90 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_lsp_file_types_revision` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:8 | State | `other` | 默认文件类型列表的迁移版本号 (AceEditorLspPreferences) |
| `key_$_ace_semantic_java_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:13 | Setting | `editor_assistance` | 语义语言开关 (AceSemanticLanguagesPreference) |
| `key_$_ace_semantic_kotlin_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:14 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_ace_semantic_lua_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:12 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_ace_semantic_python_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:11 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_ace_semantic_typescript_enabled` | default | ui/edit/editor/ace/AceEditorLspPreferences.kt:10 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_dialog_terminal_tips` | default | core/plugin/terminal/TerminalLegacyCleanup.kt:29 | Excluded |  | 已移除的宿主终端的旧键, TerminalLegacyCleanup 每次启动删除 |
| `key_$_editor_engine_selection_mode` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:12 | Setting | `editor_general` | 与 `key_editor_engine` 一起由 EditorEnginePreferences.persist 写入 |
| `key_$_enabled_plugin_$_*` | plugin_center_enable_state | core/plugin/center/PluginEnableStore.kt:68 | Setting | `plugin_enable_state` | 属于命名存储, 随整份计数 |
| `key_$_ignored_plugin_$_*` | key_$_ignored_updates | network/UpdateIgnoreStore.kt:64 | Setting | `updates` | UpdateIgnoreStore 中忽略的插件版本, 随整份计数 |
| `key_$_last_install_time_$_*` | plugin_center_recent | core/plugin/center/PluginRecentStore.kt:33 | State | `other` | 属于命名存储, 随整份计数 |
| `key_$_last_orientation` | default | ui/floating/CircularMenuWindow.java:22 | State | `other` | CircularMenuWindow 悬浮按钮上次的屏幕方向 |
| `key_$_last_uninstall_time_$_*` | plugin_center_recent | core/plugin/center/PluginRecentStore.kt:35 | State | `other` | 属于命名存储, 随整份计数 |
| `key_$_permission_wizard` | permission_wizard | ui/wizard/permission/PermissionWizardDialog.kt:33 | Setting | `wizards` | WizardProfileStore 的 keyPrefix, 键在命名存储中, 随整份计数 |
| `key_$_plugin_abi_mismatch_download_prompt` | default | core/plugin/center/PluginAbiCompatibilityPrompt.kt:14 | State | `other` | 不再提示 |
| `key_$_plugin_abi_mismatch_enable_prompt` | default | core/plugin/center/PluginAbiCompatibilityPrompt.kt:15 | State | `other` | 不再提示 |
| `key_$_plugin_auth_$_*$_*` | plugin_center_authorization | core/plugin/center/PluginAuthorizationStore.kt:32 | Setting | `credentials` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_enabled_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:13 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_install_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:12 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_mechanism_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:14 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_source_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:15 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_state` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:11 | Setting | `plugin_center` | 旧版序号键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_updatable_only` | plugin_center_filter_state | core/plugin/center/PluginFilterStore.kt:16 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_hidden_packages` | plugin_center_visibility_state | core/plugin/center/PluginVisibilityStore.kt:13 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_hidden_source_` | plugin_center_visibility_state | core/plugin/center/PluginVisibilityStore.kt:15 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_hidden_title_` | plugin_center_visibility_state | core/plugin/center/PluginVisibilityStore.kt:14 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:11 | Setting | `plugin_center` | PluginSortStore, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_ascending_` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:12 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_mechanism_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:14 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:14 | Setting | `plugin_center` | PluginSortStoreOfficial, 与另两个类共用同一份存储 |
| `key_$_plugin_center_sort_state_official_ascending_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:15 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official_mechanism_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:17 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official_secondary_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:16 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official_source_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreOfficial.kt:18 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_secondary_` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:13 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_source_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStore.kt:15 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:14 | Setting | `plugin_center` | PluginSortStoreThirdParty, 与另两个类共用同一份存储 |
| `key_$_plugin_center_sort_state_third_party_ascending_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:15 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party_mechanism_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:17 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party_secondary_` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:16 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party_source_secondary` | plugin_center_sort_state | core/plugin/center/PluginSortStoreThirdParty.kt:18 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_hide_confirm_prompt` | default | core/plugin/center/PluginSettingsFragment.kt:1165 | State | `other` | 隐藏插件确认的不再提示 |
| `key_$_plugin_install_wizard` | plugin_install_wizard | core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:28 | Setting | `wizards` | WizardProfileStore 的 keyPrefix, 键在命名存储中, 随整份计数 |
| `key_$_plugin_mount_enabled_$_` | default | core/plugin/center/PluginMountStore.kt:14 | Setting | `plugin_center` | PluginMountStore 前缀键 (挂载开关), P3.1 登记为 Json 键集合 |
| `key_$_plugin_mount_object_name_$_` | default | core/plugin/center/PluginMountStore.kt:15 | Setting | `plugin_center` | PluginMountStore 前缀键 (挂载对象名) |
| `key_$_plugin_mount_packages` | default | core/plugin/center/PluginMountStore.kt:13 | Setting | `plugin_center` | PluginMountStore 已配置包名列表 |
| `key_$_plugin_mount_point_$_` | default | core/plugin/center/PluginMountStore.kt:16 | Setting | `plugin_center` | PluginMountStore 前缀键 (挂载点) |
| `key_$_plugin_update_check_$_` | default | core/plugin/center/PluginUpdateCheckStore.kt:10 | Setting | `plugin_center` | PluginUpdateCheckStore 每个插件的 "检查更新" 开关, 是用户设置 (ROADMAP P4.2 列为状态, 见 Q13) |
| `key_$_plugin_variant_priority_$_` | default | core/plugin/center/PluginVariantPriorityStore.kt:11 | Setting | `plugin_center` | PluginVariantPriorityStore 每个引擎的变体优先级 (JSON 数组) |
| `key_$_seen_ace_fallback_notice` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:14 | State | `other` | 一次性提示已展示 (EditorEnginePreferences) |
| `key_$_seen_ace_large_file_notice` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:15 | State | `other` | 一次性提示已展示 |
| `key_$_seen_editor_switch_undo_notice` | default | ui/edit/editor/engine/EditorEnginePreferences.kt:13 | State | `other` | 一次性提示已展示 |
| `key_$_suspended_plugin_$_*` | plugin_center_enable_state | core/plugin/center/PluginEnableStore.kt:70 | Setting | `plugin_enable_state` | 挂起标记, 属于命名存储, 随整份计数 |
| `key_$_terminal_` | default | core/plugin/terminal/TerminalLegacyCleanup.kt:26 | Excluded |  | 已移除的宿主终端的旧键前缀, TerminalLegacyCleanup 每次启动删除 |
| `key_dont_show_main_activity` | default | runtime/api/augment/settings/Settings.kt:41 | Excluded |  | 脚本 API settings 模块的别名字符串, 不是偏好键 |
| `key_down` | default | core/ui/nativeview/ViewPrototype.kt:111, core/ui/nativeview/ViewPrototype.kt:115, execution/ScriptExecuteActivity.kt:338, runtime/api/Events.java:362 | Excluded |  | 按键事件名, 不是偏好键 |
| `key_enable_a11y_service_with_root_access` | default | runtime/api/augment/settings/Settings.kt:37 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_enable_accessibility_service_by_root` | default | runtime/api/augment/settings/Settings.kt:36 | Excluded |  | 脚本 API settings 模块的别名字符串, 不是偏好键 |
| `key_first_using` | default | inrt/Pref.kt:18 | Excluded |  | inrt 打包应用运行时状态 (附录 A.2) |
| `key_foreground_service` | default | runtime/api/augment/settings/Settings.kt:44 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_inrt_applied_build_id` | default | inrt/Pref.kt:19 | Excluded |  | inrt 打包应用运行时状态 (附录 A.2) |
| `key_not_showing_main_activity` | default | runtime/api/augment/settings/Settings.kt:42 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_stable_mode` | default | runtime/api/augment/settings/Settings.kt:34 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_up` | default | core/ui/nativeview/ViewPrototype.kt:111, core/ui/nativeview/ViewPrototype.kt:117, runtime/api/Events.java:365 | Excluded |  | 按键事件名, 不是偏好键 |
| `key_use_volume_control_running` | default | runtime/api/augment/settings/Settings.kt:39 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |

设置页中不经字符串资源的键: `process_exit_history` (fragment_developer_options.xml:148, kind Excluded), `plugin_center_hidden_plugin_management` (fragment_plugin_center_preferences.xml:42, kind Excluded), `plugin_center_auto_mount_management` (fragment_plugin_center_preferences.xml:54, kind Excluded)

## 4. 命名 SharedPreferences

| 文件名 | 解析方式 | 调用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `FunctionsKeyboardHelper` | 常量 SHARE_PREFERENCE_NAME (ui/edit/keyboard/FunctionsKeyboardHelper.java:26) | ui/edit/keyboard/FunctionsKeyboardHelper.java:44 | Setting | `editor_keyboard` | 编辑器功能键盘配置 (附录 A) |
| `ScriptWidgets` | 字面量 | external/widget/ScriptWidgets.java:22 | Excluded |  | 桌面小部件 id 由启动器分配, 仅本机有效 (附录 A.2) |
| `ace_action_history` | 常量 PREF_NAME (ui/edit/keyboard/AceActionHistoryStore.kt:10) | ui/edit/keyboard/AceActionHistoryStore.kt:40 | State | `other` | Ace 动作历史 (P3.2 为 State) |
| `ace_editor_font_ui_preferences` | 常量 SHARED_PREFERENCES_NAME (ui/edit/editor/ace/AceEditorFontUiPreferences.kt:89) | ui/edit/editor/ace/AceEditorFontUiPreferences.kt:243 | Setting | `editor_keyboard` | 字体选择界面的排序 / 预览 / 变体偏好 (附录 A) |
| `autojs.localstorage.*` | 人工: `.let` 的接收者是 `LocalStorage.NAME_PREFIX + name`; 拼接 | core/storage/LocalStorage.java:28, runtime/api/augment/storages/Storages.kt:48, runtime/api/augment/storages/Storages.kt:61 | Excluded |  | 脚本自己的数据 (附录 A.2) |
| `autojs.node.storage.*.*.*` | 函数 storagePreferencesName (engine/NodeBridgeProtocol.kt:3469) | engine/NodeBridgeProtocol.kt:3354 | Excluded |  | Node.js 运行时 storage 桥的脚本数据, 理由同 `autojs.localstorage.*` (附录 A.2 补登, Q13) |
| `build_apk_config_profiles` | 常量 PREF_NAME (ui/project/BuildConfigStore.java:27) | ui/project/BuildConfigStore.java:294 | Setting | `apk_build` | APK 构建配置档 |
| `bun_host_capability_grants` | 常量 SP_NAME (core/plugin/bun/BunHostCapabilityGrants.kt:62) | core/plugin/bun/BunHostCapabilityGrants.kt:68, core/plugin/bun/BunHostCapabilityGrants.kt:71, core/plugin/bun/BunHostCapabilityGrants.kt:81, core/plugin/bun/BunHostCapabilityGrants.kt:84 等 8 处 | Setting | `credentials` | Bun 宿主能力授权, sensitive |
| `device_id_prefs` | 常量 SP_NAME (deviceid/DeviceIdProvider.kt:20) | deviceid/DeviceIdProvider.kt:33 | Excluded |  | 设备标识 (附录 A.2) |
| `editor_symbols_config` | 常量 PREF_NAME (ui/edit/keyboard/SymbolsConfigStore.kt:26) | ui/edit/keyboard/SymbolsConfigStore.kt:45 | Setting | `editor_keyboard` | 符号键盘配置 (附录 A) |
| `explorer_playback_history` | 常量 PREFERENCES_NAME (core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:79) | core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:13, core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:84, core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:96 | Excluded |  | 媒体回放进度, 指向本机文件 (P3.5 永久排除) |
| `github_auth` | 常量 PREF_NAME (network/GitHubTokenStore.kt:7) | network/GitHubTokenStore.kt:44 | Setting | `credentials` | GitHub 令牌, sensitive |
| `jvm_source_provider_selection` | 常量 PREFERENCES (core/plugin/jvm/JvmSourceProviderSelection.kt:8) | core/plugin/jvm/JvmSourceProviderSelection.kt:19, core/plugin/jvm/JvmSourceProviderSelection.kt:31 | Setting | `plugin_center` | JVM 源码提供方选择 (附录 A) |
| `key_$_ignored_updates` | 字符串资源 key_ignored_updates | network/UpdateIgnoreStore.kt:13 | Setting | `updates` | UpdateIgnoreStore (文件名取自 `R.string.key_ignored_updates`), 忽略的插件更新 |
| `lua-runtime-storage-v1-*` | 拼接 | engine/LuaHostStorage.kt:166 | Excluded |  | 脚本自己的数据 (附录 A.2) |
| `mcp_server_trust_confirmation` | 常量 CONFIRMATION_STORE (core/plugin/mcp/McpServerPluginInspector.kt:23) | app/tool/McpServerTool.kt:116, core/plugin/mcp/McpServerPluginInspector.kt:51 | Setting | `credentials` | MCP Server 签名信任确认, sensitive |
| `permission_wizard` | 构造参数 core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:26, ui/wizard/permission/PermissionWizardDialog.kt:31 | ui/wizard/WizardProfileStore.kt:27 | Setting | `wizards` | 权限向导自定义配置档; 其中 `_last_profile_id` 是状态 (附录 A), 整份随 wizards |
| `plugin_center_attention` | 常量 SP (core/plugin/center/PluginCenterAttentionStore.kt:17) | core/plugin/center/PluginCenterAttentionStore.kt:76, core/plugin/center/PluginCenterAttentionStore.kt:113 | State | `other` | 插件中心临时置顶 (P4.2) |
| `plugin_center_authorization` | 常量 SP_NAME (core/plugin/center/PluginAuthorizationStore.kt:8) | core/plugin/center/PluginAuthorizationStore.kt:12, core/plugin/center/PluginAuthorizationStore.kt:18, core/plugin/center/PluginAuthorizationStore.kt:26 | Setting | `credentials` | 插件授权记录, sensitive |
| `plugin_center_enable_state` | 常量 SP_NAME (core/plugin/center/PluginEnableStore.kt:9) | core/plugin/center/PluginEnableStore.kt:12, core/plugin/center/PluginEnableStore.kt:23, core/plugin/center/PluginEnableStore.kt:41, core/plugin/center/PluginEnableStore.kt:51 等 5 处 | Setting | `plugin_enable_state` | 插件启用与挂起状态 |
| `plugin_center_filter_state` | 常量 SP_NAME (core/plugin/center/PluginFilterStore.kt:10) | core/plugin/center/PluginFilterStore.kt:72 | Setting | `plugin_center` | 筛选 |
| `plugin_center_recent` | 常量 SP (core/plugin/center/PluginRecentStore.kt:9) | core/plugin/center/PluginRecentStore.kt:14, core/plugin/center/PluginRecentStore.kt:20, core/plugin/center/PluginRecentStore.kt:26, core/plugin/center/PluginRecentStore.kt:30 | State | `other` | 最近安装 / 卸载时间 (P4.2) |
| `plugin_center_sort_state` | 常量 SP_NAME (core/plugin/center/PluginSortStore.kt:10); 常量 SP_NAME (core/plugin/center/PluginSortStoreOfficial.kt:13); 常量 SP_NAME (core/plugin/center/PluginSortStoreThirdParty.kt:13) | core/plugin/center/PluginSortStore.kt:17, core/plugin/center/PluginSortStoreOfficial.kt:20, core/plugin/center/PluginSortStoreThirdParty.kt:20 | Setting | `plugin_center` | 排序, PluginSortStore / Official / ThirdParty 三个类共用一份 |
| `plugin_center_visibility_state` | 常量 SP_NAME (core/plugin/center/PluginVisibilityStore.kt:12) | core/plugin/center/PluginVisibilityStore.kt:18 | Setting | `plugin_center` | 隐藏的插件 |
| `plugin_install_wizard` | 字面量; 构造参数 core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:26, ui/wizard/permission/PermissionWizardDialog.kt:31 | core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:18, core/plugin/center/wizard/PluginInstallWizardProfileStore.kt:22, ui/wizard/WizardProfileStore.kt:27 | Setting | `wizards` | 插件安装向导自定义配置档; 其中 `_last_profile_id` 是状态, 整份随 wizards |
| `plugin_launcher_icons` | 常量 PREFS (core/plugin/center/PluginLauncherIconController.kt:38) | core/plugin/center/PluginLauncherIconController.kt:49 | Setting | `plugin_center` | 插件启动图标显示状态 (PluginLauncherIconController) |
| `privileged_edit_backups` | 常量 PREFERENCES (model/explorer/PrivilegedBackupManager.kt:30) | model/explorer/PrivilegedBackupManager.kt:38, model/explorer/PrivilegedBackupManager.kt:44, model/explorer/PrivilegedBackupManager.kt:87, model/explorer/PrivilegedBackupManager.kt:90 等 5 处 | Excluded |  | 指向本机文件的操作日志 (附录 A.2) |
| `runtime_task_intents` | 字面量 | engine/RuntimeTasksBridge.kt:155 | Excluded |  | 运行时状态 (附录 A.2) |
| `three_stove_agent_trust_confirmation` | 常量 CONFIRMATION_STORE (core/plugin/agent/ThreeStoveAgentPluginInspector.kt:23) | core/plugin/agent/ThreeStoveAgentPluginInspector.kt:52, ui/settings/ThreeStoveAgentGuide.kt:68 | Setting | `credentials` | 3-Stove Agent 签名信任确认, sensitive |

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
| core/plugin/center/InstalledPluginMetadataStore.kt | `autojs6_installed_plugin_metadata_v1.json` | 163 | Cache |  | 已安装插件元数据缓存 (附录 A.2) |
| core/plugin/center/PluginIndexRepository.kt | `autojs6_plugin_index_v11.json`, `compat-matrix.json`, `plugins.generated.json`, `plugins.official.generated.json` | 470 | Cache |  | 插件索引缓存; 另外三个 .json 是索引仓库内的路径 |
| core/plugin/python/PythonPrivateExecutionSnapshot.kt | `.json` | 501 | Excluded |  | 运行时快照, 进程结束即失效 (附录 A.2) |
| engine/NodeLifecycleCheckpointStore.kt | `*.json` | 228,236 | Excluded |  | 运行时检查点 (附录 A.2) |
| engine/NodeWorkManagerBridgeScheduler.kt | `*.json`, `.json` | 458 | Excluded |  | 运行时调度记录 (附录 A.2) |
| engine/RuntimeWebLocalDownloads.kt | `runtime-web-downloads/*.json` | 270 | Cache |  | 可重新下载的资源 (附录 A.2) |
| model/explorer/PrivilegedBackupManager.kt | `*.json` | 75,283,311 | Excluded |  | 指向本机文件的备份记录 (附录 A.2) |
| model/explorer/PrivilegedEditSessions.kt | `session.json` | 298,310,316,336 | Excluded |  | 指向本机文件的编辑会话 (附录 A.2) |
| model/explorer/PrivilegedFileOperationJournal.kt | `*.json` | 15,56 | Excluded |  | 指向本机文件的操作日志 (附录 A.2) |
| ui/edit/editor/ace/font/FontCatalogRepository.kt | `catalog-v1.json`, `catalog-v1.sig.json` | 93 | Cache |  | 字体目录缓存与签名 (附录 A.2) |
| ui/edit/editor/ace/font/FontStore.kt | `installed.json` | 26 | Cache |  | 已安装字体清单与字体文件 (附录 A.2) |
| ui/project/BuildActivity.java | `.json` | 2036 | Excluded |  | `.json` 是 SAF 导出配置档的建议文件名, 不是内部存储 |
| ui/settings/NodeDoctorReportGenerator.kt | `node-doctor-report.json` | 43 | Excluded |  | 诊断报告输出文件, 不是存储 |

- ROADMAP 列出但启发式未命中: `ModelStore` (core/accessibility/codegen/store/ModelStore.kt) (目录或 JSON 文件名在其他类中拼接, 归属以 ROADMAP 为准)
- 启发式命中但 ROADMAP 未列出: 无

## 7. 分类条目数 (回填 ROADMAP 附录 A)

Setting 与 State 计入条目数; 默认偏好中的字面量键 (含前缀键, 每组算 1 条) 单独计数, 命名存储每份算 1 条, 存储内部的键随整份计数. 数据分类 (D21) 不含键, 条目数在运行时按条 / 按库计算.

| 分类 id | `key_*` 键 | 字面量键 (默认偏好) | 命名存储 | 合计 | 部署项 |
| --- | --- | --- | --- | --- | --- |
| `appearance` | 9 | 0 | 0 | 9 |  |
| `general` | 5 | 0 | 0 | 5 |  |
| `explorer` | 6 | 0 | 0 | 6 | `CREATE_DIRECTORY` (`key_working_directory`) |
| `history` | 10 | 0 | 0 | 10 |  |
| `editor_general` | 1 | 1 | 0 | 2 |  |
| `editor_appearance` | 17 | 0 | 0 | 17 | `DOWNLOAD_EDITOR_FONT` (`key_ace_editor_font`) |
| `editor_log` | 6 | 0 | 0 | 6 |  |
| `editor_assistance` | 3 | 5 | 0 | 8 |  |
| `editor_smart_hint` | 7 | 0 | 0 | 7 |  |
| `editor_gestures` | 2 | 0 | 0 | 2 |  |
| `editor_gutter` | 5 | 0 | 0 | 5 |  |
| `editor_performance` | 2 | 0 | 0 | 2 |  |
| `editor_keyboard` | 0 | 0 | 3 | 3 |  |
| `timed_task` | 1 | 0 | 0 | 1 |  |
| `a11y` | 6 | 0 | 0 | 6 |  |
| `running` | 5 | 0 | 0 | 5 |  |
| `record` | 3 | 0 | 0 | 3 |  |
| `extensibility` | 2 | 0 | 0 | 2 |  |
| `updates` | 2 | 0 | 1 | 3 |  |
| `console` | 12 | 0 | 0 | 12 |  |
| `connection` | 7 | 0 | 0 | 7 |  |
| `layout_inspector` | 5 | 0 | 0 | 5 |  |
| `developer` | 4 | 0 | 0 | 4 |  |
| `plugin_center` | 3 | 6 | 5 | 14 |  |
| `plugin_enable_state` | 0 | 0 | 1 | 1 |  |
| `apk_build` | 0 | 0 | 1 | 1 |  |
| `wizards` | 0 | 0 | 2 | 2 |  |
| `data_shortcuts` | 0 | 0 | 0 | 数据 |  |
| `data_color_library` | 0 | 0 | 0 | 数据 |  |
| `credentials` | 0 | 0 | 5 | 5 |  |
| `other` | 40 | 8 | 3 | 51 |  |
