# 设置清单 (inventory)

由 `tools/settings/inventory.py` 生成, 不要手工编辑; kind / 分类 id / 备注来自 `tools/settings/classification.json`. 路线图见 [ROADMAP.md](ROADMAP.md) (P0.2 / P0.3).

- 生成日期: 2026-10-21, 扫描基线: `master@b1114075b` (扫描范围内未提交文件 12 个)
- 源码路径相对 `app/src/main/java/org/autojs/autojs/` (其他包保留 `com/...` 等前缀); 资源路径相对 `app/src/main/`.
- 引用处只计非注释行; 超过 6 处时只列前 6 处, 完整列表见 JSON 基线.
- kind: Setting (用户设置), State (记忆型状态, 归 `other`), Cache (缓存, 不可导出), ValueConstant (设置值常量, 列表项候选值或默认值别名), Excluded (不是偏好键或永久排除, 见 ROADMAP 附录 A.2).

## 1. 概况

| 指标 | 数值 |
| --- | --- |
| `key_*` 字符串资源 | 270 (`strings_donottranslate.xml` 268, 其他文件 2: `key_ace_language_support_matrix`, `key_ace_semantic_languages`) |
| 出现在设置页 XML 的 `key_*` 键 (各页之和去重) | 101 (preferences 39, code_editor_settings 40, developer_options 13, trash_and_version_history_settings 9, plugin_center_preferences 3) |
| 设置页中不经字符串资源的字面量键 | 3 (developer_options 1, plugin_center_preferences 2) |
| 不在设置页的 `key_*` 键 | 169 |
| 源码 (`app/src/main/java`) 没有引用的键 | 5 (其中设置页, 数组与其他资源也没有引用的 0 个: 无) |
| 源码字面量键 (不对应 `key_*` 资源) | 126 |
| 命名 SharedPreferences / 调用文件 / 已登记 `StoreId` | 29 / 9 / 29 |
| 绕过 `Pref` 读默认偏好的文件 | 0 |
| 经 `Pref.` 读写的文件 (不含两个 `Pref.kt`) | 41 |
| filesDir / noBackupFilesDir 旁出现 JSON 文件名的源文件 | 14 |
| `JsonFileStores` 声明的 JSON 文件存储 (P5.1) | 14 |

kind 统计 (`key_*` 资源): Setting 126, State 40, Cache 1, ValueConstant 74, Excluded 29

## 2. `key_*` 字符串资源

| 键 | 出现页面与分类 | 引用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `key_a11y_service` |  | core/pref/keys/MiscStateKeys.kt:71 | State | `other` | 抽屉 "无障碍服务" 开关的镜像, DrawerMenuToggleableItem.sync() 按服务实际状态回写; P4.2 声明为 MiscStateKeys |
| `key_a11y_service_manager_control_level` |  | core/pref/keys/MiscStateKeys.kt:86 | State | `other` | 无障碍服务管理器对话框的展开级别; P4.2 声明为 MiscStateKeys |
| `key_a11y_service_manager_settings_collapsed` |  | core/pref/keys/MiscStateKeys.kt:89 | State | `other` | 管理器设置区折叠状态; P4.2 声明为 MiscStateKeys |
| `key_a11y_service_manager_status_level` |  | core/pref/keys/MiscStateKeys.kt:83 | State | `other` | 管理器状态区展开级别; P4.2 声明为 MiscStateKeys |
| `key_about_app_and_developer` | 设置 > 关于 |  | Excluded |  | 动作条目 (打开关于页), 不持久化值 |
| `key_about_app_tips` | 设置 > 关于 |  | Excluded |  | 动作条目 (提示对话框), 不持久化值 |
| `key_ace_custom_scrollbar_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:143, ui/settings/CodeEditorSettingsFragment.kt:252, ui/settings/EditorSettingActionRegistry.kt:33, ui/edit/editor/EditorDisplayPreferences.kt:22 | Setting | `editor_appearance` | 开关 |
| `key_ace_editor_font` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:110, ui/edit/EditorView.kt:922, ui/settings/CodeEditorSettingsFragment.kt:245, ui/settings/CodeEditorSettingsFragment.kt:287, ui/settings/EditorSettingActionRegistry.kt:22, ui/edit/editor/ace/AceEditorFontPreferences.kt:7 | Setting | `editor_appearance`, deployment `DOWNLOAD_EDITOR_FONT` | 字体 id; 字体未安装时作为部署项下载 (D20 / Q2) |
| `key_ace_editor_font_cascadia_code` |  | core/pref/keys/EditorKeys.kt:73 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_fira_code` |  | core/pref/keys/EditorKeys.kt:74 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_hack` |  | core/pref/keys/EditorKeys.kt:75 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_ibm_plex_mono` |  | core/pref/keys/EditorKeys.kt:76 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_intel_one_mono` |  | core/pref/keys/EditorKeys.kt:77 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_iosevka` | 默认值 `default_key_ace_editor_font` | core/pref/keys/EditorKeys.kt:78 | ValueConstant |  | 字体 id 常量, `default_key_ace_editor_font` 的目标 |
| `key_ace_editor_font_iosevka_slab` |  | core/pref/keys/EditorKeys.kt:79 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_jetbrains_mono` |  | core/pref/keys/EditorKeys.kt:80 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_source_code_pro` |  | core/pref/keys/EditorKeys.kt:81 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_editor_font_system_monospace` |  | core/pref/keys/EditorKeys.kt:82 | ValueConstant |  | 字体 id 常量, 源码与资源均无引用 (Q12) |
| `key_ace_font_ligatures_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:116, ui/settings/CodeEditorSettingsFragment.kt:246, ui/settings/EditorSettingActionRegistry.kt:25, ui/edit/editor/EditorDisplayPreferences.kt:15 | Setting | `editor_appearance` | 开关 |
| `key_ace_font_styles_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:119, ui/settings/CodeEditorSettingsFragment.kt:247, ui/settings/EditorSettingActionRegistry.kt:26, ui/edit/editor/EditorDisplayPreferences.kt:16 | Setting | `editor_appearance` | 开关 |
| `key_ace_highlight_indent_guides_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:128, ui/settings/CodeEditorSettingsFragment.kt:250, ui/settings/EditorSettingActionRegistry.kt:29, ui/edit/editor/EditorDisplayPreferences.kt:20 | Setting | `editor_appearance` | 开关 |
| `key_ace_indent_guides_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:125, ui/settings/CodeEditorSettingsFragment.kt:249, ui/settings/EditorSettingActionRegistry.kt:28, ui/edit/editor/EditorDisplayPreferences.kt:11 | Setting | `editor_appearance` | 开关 |
| `key_ace_inline_completion_preview_enabled` | 代码编辑器设置 > 智能提示 | core/pref/keys/EditorKeys.kt:285, ui/settings/CodeEditorSettingsFragment.kt:268, ui/settings/EditorSettingActionRegistry.kt:49, ui/edit/editor/EditorDisplayPreferences.kt:34 | Setting | `editor_smart_hint` | 开关 |
| `key_ace_language_support_matrix` | 代码编辑器设置 > 辅助 | core/pref/keys/EditorKeys.kt:359, ui/settings/CodeEditorSettingsFragment.kt:293, ui/settings/EditorSettingActionRegistry.kt:38 | Excluded |  | 动作条目 (语言支持矩阵对话框), 不持久化值 |
| `key_ace_line_numbers_enabled` | 代码编辑器设置 > 边栏标记 | core/pref/keys/EditorKeys.kt:299, ui/settings/EditorSettingActionRegistry.kt:54, ui/edit/editor/EditorDisplayPreferences.kt:9 | Setting | `editor_gutter` | 开关 |
| `key_ace_lsp_declaration_groups` | 代码编辑器设置 > 辅助 | core/pref/keys/EditorKeys.kt:218, ui/edit/EditorView.kt:937, ui/settings/CodeEditorSettingsFragment.kt:295, ui/settings/EditorSettingActionRegistry.kt:40, ui/edit/editor/ace/AceEditorLspPreferences.kt:11 | Setting | `editor_assistance` | 声明组集合 |
| `key_ace_lsp_enabled` | 代码编辑器设置 > 辅助 | core/pref/keys/EditorKeys.kt:201, ui/edit/EditorView.kt:936, ui/settings/CodeEditorSettingsFragment.kt:93, ui/settings/CodeEditorSettingsFragment.kt:256, ui/settings/EditorSettingActionRegistry.kt:39, ui/edit/editor/ace/AceEditorLspPreferences.kt:8 | Setting | `editor_assistance` | 开关 |
| `key_ace_lsp_file_types` | 代码编辑器设置 > 辅助 | core/pref/keys/EditorKeys.kt:211, ui/edit/EditorView.kt:938, ui/settings/CodeEditorSettingsFragment.kt:296, ui/settings/EditorSettingActionRegistry.kt:41, ui/edit/editor/ace/AceEditorLspPreferences.kt:9 | Setting | `editor_assistance` | 文件类型列表; 迁移版本号见字面量键 `key_$_ace_lsp_file_types_revision` |
| `key_ace_print_margin_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:122, ui/settings/CodeEditorSettingsFragment.kt:248, ui/settings/EditorSettingActionRegistry.kt:27, ui/edit/editor/EditorDisplayPreferences.kt:10 | Setting | `editor_appearance` | 开关 |
| `key_ace_scrollbar_auto_hide_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:146, ui/settings/CodeEditorSettingsFragment.kt:253, ui/settings/EditorSettingActionRegistry.kt:34, ui/edit/editor/EditorDisplayPreferences.kt:23 | Setting | `editor_appearance` | 开关 |
| `key_ace_semantic_languages` | 代码编辑器设置 > 辅助 | core/pref/keys/EditorKeys.kt:360, ui/settings/CodeEditorSettingsFragment.kt:297, ui/settings/EditorSettingActionRegistry.kt:42 | Excluded |  | 动作条目, 值存于 5 个 `key_$_ace_semantic_*_enabled` 字面量键 |
| `key_ace_smart_hint_arrow_overload_cycling` | 代码编辑器设置 > 智能提示 | core/pref/keys/EditorKeys.kt:279, ui/settings/CodeEditorSettingsFragment.kt:266, ui/settings/EditorSettingActionRegistry.kt:47, ui/edit/editor/EditorDisplayPreferences.kt:32 | Setting | `editor_smart_hint` | 开关 |
| `key_ace_smart_hint_completion_delay` | 代码编辑器设置 > 智能提示 | core/pref/keys/EditorKeys.kt:268, ui/settings/CodeEditorSettingsFragment.kt:264, ui/settings/EditorSettingActionRegistry.kt:45, ui/edit/editor/EditorDisplayPreferences.kt:30 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_completion_delay_0` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`); 默认值 `default_key_ace_smart_hint_completion_delay` | core/pref/keys/EditorKeys.kt:61 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_delay_100` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`) | core/pref/keys/EditorKeys.kt:62 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_delay_250` | 候选值 `keys_ace_smart_hint_completion_delay` (`key_ace_smart_hint_completion_delay`) | core/pref/keys/EditorKeys.kt:63 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_min_chars` | 代码编辑器设置 > 智能提示 | core/pref/keys/EditorKeys.kt:262, ui/settings/CodeEditorSettingsFragment.kt:263, ui/settings/EditorSettingActionRegistry.kt:44, ui/edit/editor/EditorDisplayPreferences.kt:29 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_completion_min_chars_1` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars`; 默认值 `default_key_ace_smart_hint_completion_min_chars` | core/pref/keys/EditorKeys.kt:56 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_min_chars_2` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars` | core/pref/keys/EditorKeys.kt:57 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_min_chars_3` | 候选值 `keys_ace_smart_hint_completion_min_chars` (`key_ace_smart_hint_completion_min_chars`); 候选值 `values_ace_smart_hint_completion_min_chars` | core/pref/keys/EditorKeys.kt:58 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_trigger` | 代码编辑器设置 > 智能提示 | core/pref/keys/EditorKeys.kt:256, ui/settings/CodeEditorSettingsFragment.kt:262, ui/settings/EditorSettingActionRegistry.kt:43, ui/edit/editor/EditorDisplayPreferences.kt:28 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_completion_trigger_dot` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`) | core/pref/keys/EditorKeys.kt:51 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_trigger_identifier` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`); 默认值 `default_key_ace_smart_hint_completion_trigger` | core/pref/keys/EditorKeys.kt:52 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_completion_trigger_off` | 候选值 `keys_ace_smart_hint_completion_trigger` (`key_ace_smart_hint_completion_trigger`) | core/pref/keys/EditorKeys.kt:53 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_signature_trigger` | 代码编辑器设置 > 智能提示 | core/pref/keys/EditorKeys.kt:274, ui/settings/CodeEditorSettingsFragment.kt:265, ui/settings/EditorSettingActionRegistry.kt:46, ui/edit/editor/EditorDisplayPreferences.kt:31 | Setting | `editor_smart_hint` | 列表项 |
| `key_ace_smart_hint_signature_trigger_cursor` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`); 默认值 `default_key_ace_smart_hint_signature_trigger` | core/pref/keys/EditorKeys.kt:66, core/pref/keys/EditorKeys.kt:355 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_signature_trigger_off` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`) | core/pref/keys/EditorKeys.kt:67 | ValueConstant |  | 候选值 |
| `key_ace_smart_hint_signature_trigger_typing` | 候选值 `keys_ace_smart_hint_signature_trigger` (`key_ace_smart_hint_signature_trigger`) | core/pref/keys/EditorKeys.kt:68, core/pref/keys/EditorKeys.kt:355 | ValueConstant |  | 候选值 |
| `key_ace_svg_gutter_icons_enabled` | 代码编辑器设置 > 边栏标记 | core/pref/keys/EditorKeys.kt:302, ui/settings/CodeEditorSettingsFragment.kt:254, ui/settings/EditorSettingActionRegistry.kt:56, ui/edit/editor/EditorDisplayPreferences.kt:21 | Setting | `editor_gutter` | 开关 |
| `key_ace_symbol_highlight_enabled` | 代码编辑器设置 > 智能提示 | core/pref/keys/EditorKeys.kt:282, ui/settings/CodeEditorSettingsFragment.kt:267, ui/settings/EditorSettingActionRegistry.kt:48, ui/edit/editor/EditorDisplayPreferences.kt:33 | Setting | `editor_smart_hint` | 开关 |
| `key_ace_touch_magnifier_enabled` | 代码编辑器设置 > 操作手势 | core/pref/keys/EditorKeys.kt:290, ui/settings/CodeEditorSettingsFragment.kt:255, ui/settings/EditorSettingActionRegistry.kt:51, ui/edit/editor/EditorDisplayPreferences.kt:25 | Setting | `editor_gestures` | 开关 |
| `key_ace_whitespaces_in_selection_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:140, ui/settings/CodeEditorSettingsFragment.kt:251, ui/settings/EditorSettingActionRegistry.kt:32, ui/edit/editor/EditorDisplayPreferences.kt:24 | Setting | `editor_appearance` | 开关 |
| `key_ace_word_wrap_enabled` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:131, ui/settings/CodeEditorSettingsFragment.kt:94, ui/settings/CodeEditorSettingsFragment.kt:258, ui/settings/EditorSettingActionRegistry.kt:30, ui/edit/editor/EditorDisplayPreferences.kt:7 | Setting | `editor_appearance` | 开关 |
| `key_ai_settings` | 开发者选项 > AI |  | Excluded |  | 动作条目, 打开 AI 插件设置 (插件自身设置第一期不含, D3) |
| `key_all_files_access` | 其他资源 res/xml/fragment_preferences_inrt.xml:42 | inrt/SettingsActivity.kt:56, inrt/SettingsActivity.kt:65 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 (附录 A.2, Q13 已拍板) |
| `key_app_language` | 设置 > 外观 | core/pref/keys/AppearanceKeys.kt:57 | Setting | `appearance` | 列表项 (Language) |
| `key_app_language_ar` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:22, core/pref/keys/AppearanceKeys.kt:38 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_auto` | 候选值 `keys_app_language` (`key_app_language`); 默认值 `default_key_app_language` | core/pref/Language.kt:23, core/pref/keys/AppearanceKeys.kt:28 | ValueConstant |  | Language 枚举候选值, 默认值 |
| `key_app_language_en` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:16, core/pref/keys/AppearanceKeys.kt:32 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_es` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:18, core/pref/keys/AppearanceKeys.kt:34 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_fr` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:17, core/pref/keys/AppearanceKeys.kt:33 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_ja` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:19, core/pref/keys/AppearanceKeys.kt:35 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_ko` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:20, core/pref/keys/AppearanceKeys.kt:36 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_ru` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:21, core/pref/keys/AppearanceKeys.kt:37 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_zh_hans` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:13, core/pref/keys/AppearanceKeys.kt:29 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_zh_hant_hk` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:14, core/pref/keys/AppearanceKeys.kt:30 | ValueConstant |  | Language 枚举候选值 |
| `key_app_language_zh_hant_tw` | 候选值 `keys_app_language` (`key_app_language`) | core/pref/Language.kt:15, core/pref/keys/AppearanceKeys.kt:31 | ValueConstant |  | Language 枚举候选值 |
| `key_auto_check_for_updates` | 设置 > 更新 | core/pref/keys/UpdatesKeys.kt:26 | Setting | `updates` | 开关 |
| `key_auto_night_mode_enabled` |  | core/plugin/center/PluginCenterFragment.kt:981, core/pref/keys/AppearanceKeys.kt:75, ui/main/plugin/PluginFragment.kt:66 | Setting | `appearance` | 与 `key_night_mode` 一起由 NightModePreference 与抽屉写入 |
| `key_batch_rename_history_retention` |  | core/pref/keys/ExplorerKeys.kt:73 | Setting | `explorer` | 批量重命名历史保留批数 (BatchRenameHistoryPrefs) |
| `key_change_working_dir` | 设置 > 文件管理器 | core/pref/keys/ExplorerKeys.kt:98 | Excluded |  | 动作条目, 工作目录存于 `key_working_directory` |
| `key_check_for_updates` | 设置 > 更新 | core/pref/keys/UpdatesKeys.kt:52 | Excluded |  | 动作条目 (立即检查更新) |
| `key_check_for_updates_while_ignoring_the_local_version` | 开发者选项 > 更新 | core/pref/keys/UpdatesKeys.kt:53 | Excluded |  | 动作条目 (开发者选项, 忽略本机版本检查一次) |
| `key_clear_explorer_playback_history` | 设置 > 文件管理器 | core/pref/keys/ExplorerKeys.kt:98 | Excluded |  | 动作条目 (清除回放历史) |
| `key_client_mode_auto_reconnect` |  | core/pref/keys/ConnectionKeys.kt:34 | Setting | `connection` | 客户端模式管理器开关 |
| `key_client_mode_manager_clients_level` |  | core/pref/keys/MiscStateKeys.kt:101 | State | `other` | 管理器对话框展开级别; P4.2 声明为 MiscStateKeys |
| `key_client_mode_manager_control_level` |  | core/pref/keys/MiscStateKeys.kt:95 | State | `other` | 管理器对话框展开级别; P4.2 声明为 MiscStateKeys |
| `key_client_mode_manager_settings_collapsed` |  | core/pref/keys/MiscStateKeys.kt:98 | State | `other` | 管理器设置区折叠状态; P4.2 声明为 MiscStateKeys |
| `key_client_mode_manager_status_level` |  | core/pref/keys/MiscStateKeys.kt:92 | State | `other` | 管理器对话框展开级别; P4.2 声明为 MiscStateKeys |
| `key_client_mode_reconnect_on_network_change` |  | core/pref/keys/ConnectionKeys.kt:41 | Setting | `connection` | 客户端模式管理器开关 |
| `key_client_mode_restore_on_launch` |  | core/pref/keys/ConnectionKeys.kt:48 | Setting | `connection` | 客户端模式管理器开关 |
| `key_client_socket_normally_closed` |  | core/pref/keys/MiscStateKeys.kt:119 | State | `other` | 上次连接是否正常关闭, 用于启动时恢复; P4.2 声明为 MiscStateKeys |
| `key_codegen_model_current` |  | core/pref/keys/LayoutInspectorKeys.kt:47 | Setting | `layout_inspector` | 代码生成当前模型 id (经 selectionPreferenceKey 派生实际键); 模型文件不导出, 本机缺该模型时的处理待 P7.2 计划器定 |
| `key_color_select_activity_legacy_layout` |  | core/pref/keys/MiscStateKeys.kt:190 | State | `other` | ColorLibrariesActivity / ColorSelectActivity 打开前写入的界面模式, 不是用户设置 (Q13 (b) 已拍板, P4.2); P4.2 声明为 MiscStateKeys |
| `key_console_element_time_format` |  | core/pref/keys/ConsoleKeys.kt:52 | Setting | `console` | ConsoleTimeSettings.CONSOLE_ELEMENT |
| `key_console_element_time_visible` |  | core/pref/keys/ConsoleKeys.kt:49 | Setting | `console` | ConsoleTimeSettings.CONSOLE_ELEMENT |
| `key_delete_permanently_also_clear_history` | 设置 > 回收站与版本历史 | core/pref/keys/HistoryKeys.kt:67 | Setting | `history` | 开关 |
| `key_dialog_foreground_svc` |  | core/pref/keys/DialogStateKeys.kt:59 | State | `other` | 不再提示类键名, 源码与资源均无引用 (Q12); P4.1 声明为 DialogStateKeys, 只登记不使用 |
| `key_dialog_layout_inspector_exit_tips` |  | core/pref/keys/DialogStateKeys.kt:67 | State | `other` | 不再提示类键名, 只在注释掉的代码中出现 (Q12); P4.1 声明为 DialogStateKeys, 只登记不使用 |
| `key_dialog_log_entries_exported` |  | core/pref/keys/DialogStateKeys.kt:63 | State | `other` | 不再提示类键名, 源码与资源均无引用 (Q12); P4.1 声明为 DialogStateKeys, 只登记不使用 |
| `key_dialog_num_of_log_entries_exceeds_limit_for_sending` |  | core/pref/keys/DialogStateKeys.kt:39 | State | `other` | NotAskAgainDialog 不再提示 (ConsoleImpl); P4.1 声明为 DialogStateKeys |
| `key_dialog_selected_abi_is_unavailable` |  | core/pref/keys/DialogStateKeys.kt:43 | State | `other` | NotAskAgainDialog 不再提示 (BuildActivity); P4.1 声明为 DialogStateKeys |
| `key_dialog_transparent_background_launcher_icon` |  | core/pref/keys/DialogStateKeys.kt:47 | State | `other` | NotAskAgainDialog 不再提示 (LauncherIconPreference); P4.1 声明为 DialogStateKeys |
| `key_display_over_other_apps` | 其他资源 res/xml/fragment_preferences_inrt.xml:36 | inrt/SettingsActivity.kt:55 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 (附录 A.2, Q13 已拍板) |
| `key_documentation_source` | 设置 > 配置 | core/pref/keys/GeneralKeys.kt:48 | Setting | `general` | 列表项 |
| `key_documentation_source_local` | 候选值 `keys_documentation_source` (`key_documentation_source`); 默认值 `default_key_documentation_source` | core/pref/keys/GeneralKeys.kt:26, util/DocsUtils.kt:24 | ValueConstant |  | 候选值, 默认值 |
| `key_documentation_source_online` | 候选值 `keys_documentation_source` (`key_documentation_source`) | core/pref/keys/GeneralKeys.kt:27, ui/doc/DocumentationPageController.kt:273 | ValueConstant |  | 候选值 |
| `key_drafts_max_days` | 回收站与版本历史设置 > 紧急草稿 | core/pref/keys/HistoryKeys.kt:60 | Setting | `history` | 保留策略 |
| `key_drafts_max_total_bytes` | 回收站与版本历史设置 > 紧急草稿 | core/pref/keys/HistoryKeys.kt:63 | Setting | `history` | 保留策略 |
| `key_editor_auto_show_log_bottom_sheet_on_run` | 设置 > 代码编辑器; 代码编辑器设置 > 日志 | core/pref/keys/EditorKeys.kt:177, ui/settings/EditorSettingActionRegistry.kt:36, ui/settings/PreferencesFragment.kt:65, ui/settings/PreferencesFragment.kt:73 | Setting | `editor_log` | 开关, 设置页与代码编辑器设置两处 |
| `key_editor_breakpoint_markers_enabled` | 代码编辑器设置 > 边栏标记 | core/pref/keys/EditorKeys.kt:305, ui/settings/EditorSettingActionRegistry.kt:53, ui/edit/editor/EditorDisplayPreferences.kt:12 | Setting | `editor_gutter` | 开关 |
| `key_editor_code_highlight_threshold_bytes` | 代码编辑器设置 > 性能 | core/pref/keys/EditorKeys.kt:321, ui/settings/CodeEditorSettingsFragment.kt:269, ui/settings/EditorSettingActionRegistry.kt:57 | Setting | `editor_performance` | 阈值 |
| `key_editor_engine` | 设置 > 代码编辑器; 代码编辑器设置 > 常规 | core/pref/keys/EditorKeys.kt:97, ui/edit/EditorView.kt:928, ui/settings/CodeEditorSettingsFragment.kt:92, ui/settings/CodeEditorSettingsFragment.kt:99, ui/settings/EditorSettingActionRegistry.kt:19, ui/settings/PreferencesFragment.kt:63 等 8 处 | Setting | `editor_general` | 列表项, 与字面量键 `key_$_editor_engine_selection_mode` 一起写入 |
| `key_editor_engine_ace` | 候选值 `keys_editor_engine` (`key_editor_engine`) | core/pref/keys/EditorKeys.kt:45 | ValueConstant |  | 候选值 |
| `key_editor_engine_native` | 候选值 `keys_editor_engine` (`key_editor_engine`); 默认值 `default_key_editor_engine` | core/pref/keys/EditorKeys.kt:45 | ValueConstant |  | 候选值, 默认值 |
| `key_editor_fold_markers_enabled` | 代码编辑器设置 > 边栏标记 | core/pref/keys/EditorKeys.kt:308, ui/settings/CodeEditorSettingsFragment.kt:261, ui/settings/EditorSettingActionRegistry.kt:55, ui/edit/editor/EditorDisplayPreferences.kt:13 | Setting | `editor_gutter` | 开关 |
| `key_editor_fullscreen_mode` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:149, ui/edit/EditorView.kt:929, ui/settings/EditorSettingActionRegistry.kt:24 | Setting | `editor_appearance` | 开关, 编辑器菜单也会写入 |
| `key_editor_gutter_width_mode` | 代码编辑器设置 > 边栏标记 | core/pref/keys/EditorKeys.kt:312, ui/settings/CodeEditorSettingsFragment.kt:260, ui/settings/EditorSettingActionRegistry.kt:52, ui/edit/editor/EditorDisplayPreferences.kt:14 | Setting | `editor_gutter` | 列表项 |
| `key_editor_gutter_width_mode_dynamic` | 候选值 `keys_editor_gutter_width_mode` (`key_editor_gutter_width_mode`); 默认值 `default_key_editor_gutter_width_mode` | core/pref/keys/EditorKeys.kt:49 | ValueConstant |  | 候选值, 默认值 |
| `key_editor_gutter_width_mode_fixed` | 候选值 `keys_editor_gutter_width_mode` (`key_editor_gutter_width_mode`) | core/pref/keys/EditorKeys.kt:49 | ValueConstant |  | 候选值 |
| `key_editor_large_file_mode_threshold_bytes` | 代码编辑器设置 > 性能 | core/pref/keys/EditorKeys.kt:328, ui/settings/CodeEditorSettingsFragment.kt:270, ui/settings/EditorSettingActionRegistry.kt:58 | Setting | `editor_performance` | 阈值 |
| `key_editor_log_button_behavior` | 设置 > 代码编辑器; 代码编辑器设置 > 日志 | core/pref/keys/EditorKeys.kt:181, ui/settings/EditorSettingActionRegistry.kt:35, ui/settings/PreferencesFragment.kt:64, ui/settings/PreferencesFragment.kt:74 | Setting | `editor_log` | 列表项, 设置页与代码编辑器设置两处 |
| `key_editor_log_button_behavior_bottom_sheet` | 候选值 `keys_editor_log_button_behavior` (`key_editor_log_button_behavior`); 默认值 `default_key_editor_log_button_behavior` | core/pref/keys/EditorKeys.kt:48 | ValueConstant |  | 候选值, 默认值 |
| `key_editor_log_button_behavior_fullscreen_page` | 候选值 `keys_editor_log_button_behavior` (`key_editor_log_button_behavior`) | core/pref/keys/EditorKeys.kt:48, ui/edit/EditorView.kt:275 | ValueConstant |  | 候选值 |
| `key_editor_log_panel_input_bar_visible` |  | core/pref/keys/EditorKeys.kt:187 | Setting | `editor_log` | 日志面板输入栏 (LogPageConsoleSettings) |
| `key_editor_log_panel_time_format` |  | core/pref/keys/EditorKeys.kt:193 | Setting | `editor_log` | ConsoleTimeSettings.EDITOR_PANEL |
| `key_editor_log_panel_time_visible` |  | core/pref/keys/EditorKeys.kt:190 | Setting | `editor_log` | ConsoleTimeSettings.EDITOR_PANEL |
| `key_editor_log_panel_typescript_console_visible` |  | core/pref/keys/EditorKeys.kt:196 | Setting | `editor_log` | 日志面板开关 |
| `key_editor_pinch_to_zoom_change_text_size` | 候选值 `keys_editor_pinch_to_zoom_strategy`; 默认值 `default_key_editor_pinch_to_zoom_strategy` | core/pref/keys/EditorKeys.kt:87, ui/settings/EditorPinchToZoomPreference.kt:82 | ValueConstant |  | 旧策略键 `key_editor_pinch_to_zoom_strategy` 的候选值, `default_key_editor_pinch_to_zoom_strategy` 的目标 |
| `key_editor_pinch_to_zoom_change_text_size_enabled` | 代码编辑器设置 > 操作手势 | core/pref/keys/EditorKeys.kt:294, ui/edit/EditorView.kt:935, ui/settings/EditorSettingActionRegistry.kt:50, ui/settings/EditorSettingActionRegistry.kt:11 | Setting | `editor_gestures` | 开关, 取代旧策略键 |
| `key_editor_pinch_to_zoom_disable` | 候选值 `keys_editor_pinch_to_zoom_strategy` | core/pref/keys/EditorKeys.kt:88, ui/edit/editor/EditorPinchToZoomPreferences.kt:15, ui/edit/editor/EditorPinchToZoomPreferences.kt:22 | ValueConstant |  | 旧策略键的候选值, 迁移时比较 |
| `key_editor_pinch_to_zoom_scale_view` |  | core/pref/keys/EditorKeys.kt:89, ui/settings/EditorPinchToZoomPreference.kt:81 | ValueConstant |  | 旧策略键的候选值, 只在未被设置页引用的 EditorPinchToZoomPreference 中使用 |
| `key_editor_pinch_to_zoom_strategy` |  | core/pref/keys/MiscStateKeys.kt:51, ui/settings/EditorSettingActionRegistry.kt:10 | State | `other` | 旧版策略设置, 只作 migrateLegacyPreferenceIfNeeded 的迁移来源; 写入方 EditorPinchToZoomPreference 已不在任何设置页; P4.2 声明为 MiscStateKeys |
| `key_editor_shortcut_input_bar` | 代码编辑器设置 > 辅助 | core/pref/keys/EditorKeys.kt:361, ui/settings/EditorSettingActionRegistry.kt:37 | Excluded |  | 动作条目, 打开 SymbolsSettingsActivity (数据在命名存储 `editor_symbols_config`) |
| `key_editor_text_size` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:154, ui/edit/EditorView.kt:921, ui/settings/EditorSettingActionRegistry.kt:23 | Setting | `editor_appearance` | 字号 |
| `key_editor_theme` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:160, ui/edit/EditorView.kt:923, ui/settings/CodeEditorSettingsFragment.kt:101, ui/settings/CodeEditorSettingsFragment.kt:105, ui/settings/CodeEditorSettingsFragment.kt:278, ui/settings/EditorSettingActionRegistry.kt:21 | Setting | `editor_appearance` | 统一模式下的主题名 |
| `key_editor_theme_dark` |  | core/pref/keys/EditorKeys.kt:166, ui/edit/EditorView.kt:925, ui/settings/CodeEditorSettingsFragment.kt:103 | Setting | `editor_appearance` | 分离模式下的深色主题名 (Themes.java) |
| `key_editor_theme_light` |  | core/pref/keys/EditorKeys.kt:163, ui/edit/EditorView.kt:924, ui/settings/CodeEditorSettingsFragment.kt:102 | Setting | `editor_appearance` | 分离模式下的浅色主题名 (Themes.java) |
| `key_editor_theme_mode` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:170, ui/edit/EditorView.kt:926, ui/settings/CodeEditorSettingsFragment.kt:100, ui/settings/EditorSettingActionRegistry.kt:20 | Setting | `editor_appearance` | 列表项 |
| `key_editor_theme_mode_separate` | 候选值 `keys_editor_theme_mode` (`key_editor_theme_mode`); 默认值 `default_key_editor_theme_mode` | core/pref/keys/EditorKeys.kt:46 | ValueConstant |  | 候选值, 默认值 |
| `key_editor_theme_mode_unified` | 候选值 `keys_editor_theme_mode` (`key_editor_theme_mode`) | core/pref/keys/EditorKeys.kt:46, ui/edit/theme/Themes.java:252 | ValueConstant |  | 候选值 |
| `key_editor_word_wrap_indent_style` | 代码编辑器设置 > 外观 | core/pref/keys/EditorKeys.kt:135, ui/settings/CodeEditorSettingsFragment.kt:307, ui/settings/CodeEditorSettingsFragment.kt:312, ui/settings/EditorSettingActionRegistry.kt:31, ui/edit/editor/EditorDisplayPreferences.kt:8 | Setting | `editor_appearance` | 列表项 |
| `key_editor_word_wrap_indent_style_classic` | 候选值 `keys_editor_word_wrap_indent_style` (`key_editor_word_wrap_indent_style`) | core/pref/keys/EditorKeys.kt:47 | ValueConstant |  | 候选值 |
| `key_editor_word_wrap_indent_style_continuation` | 候选值 `keys_editor_word_wrap_indent_style` (`key_editor_word_wrap_indent_style`); 默认值 `default_key_editor_word_wrap_indent_style` | core/pref/keys/EditorKeys.kt:47 | ValueConstant |  | 候选值, 默认值 |
| `key_enable_a11y_service_with_root_access` | 设置 > 无障碍服务; 其他资源 res/xml/fragment_preferences_inrt.xml:85 | core/pref/keys/A11yKeys.kt:21, inrt/Pref.kt:40, runtime/api/augment/settings/Settings.kt:28 | Setting | `a11y` | 开关, 也是脚本 API settings 的 ROOT_ACCESSIBILITY_SERVICE |
| `key_enable_a11y_service_with_secure_settings` | 设置 > 无障碍服务 | core/pref/keys/A11yKeys.kt:27 | Setting | `a11y` | 开关 |
| `key_enable_a11y_service_with_shizuku` | 设置 > 无障碍服务 | core/pref/keys/A11yKeys.kt:33 | Setting | `a11y` | 开关 |
| `key_enable_observe_key` |  | core/pref/keys/MiscStateKeys.kt:284 | State | `other` | 按键观察, 源码与资源均无读写方, 不作为可导入设置 (Q12 已拍板); P4.2 声明为 MiscStateKeys, 只登记不使用 |
| `key_explorer_file_default_prefix` |  | core/pref/keys/ExplorerKeys.kt:69 | Setting | `explorer` | 新文件默认前缀序号 (ScriptOperations) |
| `key_explorer_playback_history` | 设置 > 文件管理器 | core/pref/keys/ExplorerKeys.kt:63 | Setting | `explorer` | 开关; 历史数据在命名存储 `explorer_playback_history` (永久排除) |
| `key_extending_js_build_in_objects` | 设置 > 扩展性 | core/pref/keys/ExtensibilityKeys.kt:24 | Setting | `extensibility` | 开关 |
| `key_file_extensions` | 设置 > 文件管理器 | core/pref/keys/ExplorerKeys.kt:57 | Setting | `explorer` | 列表项, 文件管理器菜单也会写入 |
| `key_file_extensions_not_show` | 候选值 `keys_file_extensions` (`key_file_extensions`) | core/pref/keys/ExplorerKeys.kt:30, core/pref/keys/ExplorerKeys.kt:115 | ValueConstant |  | 候选值 |
| `key_file_extensions_show_all` | 候选值 `keys_file_extensions` (`key_file_extensions`); 默认值 `default_key_file_extensions` | core/pref/keys/ExplorerKeys.kt:31, core/pref/keys/ExplorerKeys.kt:107 | ValueConstant |  | 候选值, 默认值 |
| `key_file_extensions_show_all_but_executable` | 候选值 `keys_file_extensions` (`key_file_extensions`) | core/pref/keys/ExplorerKeys.kt:32, core/pref/keys/ExplorerKeys.kt:111 | ValueConstant |  | 候选值 |
| `key_floating_console_time_format` |  | core/pref/keys/ConsoleKeys.kt:58 | Setting | `console` | ConsoleTimeSettings.FLOATING_WINDOW |
| `key_floating_console_time_visible` |  | core/pref/keys/ConsoleKeys.kt:55 | Setting | `console` | ConsoleTimeSettings.FLOATING_WINDOW |
| `key_floating_menu_shown` |  | core/pref/keys/MiscStateKeys.kt:75 | State | `other` | 抽屉 "悬浮按钮" 开关的镜像, 按悬浮窗实际状态回写; P4.2 声明为 MiscStateKeys |
| `key_foreground_service` | 其他资源 res/xml/fragment_preferences_inrt.xml:12 | core/pref/keys/GeneralKeys.kt:80, inrt/SettingsActivity.kt:52, runtime/api/augment/settings/Settings.kt:31 | Setting | `general` | 抽屉 "前台服务" 开关, 脚本 API settings 与 inrt 也读写; 导入后按值启动或停止前台服务 (Q13 (d) 已拍板, T12) |
| `key_gesture_observing` |  | core/pref/keys/A11yKeys.kt:41 | Setting | `a11y` | 开关 (手势观察) |
| `key_github_token` | 开发者选项 > GitHub |  | Excluded |  | 动作条目, 令牌存于命名存储 `github_auth` (credentials) |
| `key_global_console_element_time_format` |  | core/pref/keys/ConsoleKeys.kt:46 | Setting | `console` | ConsoleTimeSettings.GLOBAL_CONSOLE_ELEMENT |
| `key_global_console_element_time_visible` |  | core/pref/keys/ConsoleKeys.kt:43 | Setting | `console` | ConsoleTimeSettings.GLOBAL_CONSOLE_ELEMENT |
| `key_guard_mode` | 设置 > 脚本运行 | core/pref/keys/RunningKeys.kt:37 | Setting | `running` | 开关 |
| `key_hidden_files` | 设置 > 文件管理器 | core/pref/keys/ExplorerKeys.kt:51 | Setting | `explorer` | 列表项, 文件管理器菜单也会写入 |
| `key_hidden_files_not_show` | 候选值 `keys_hidden_files` (`key_hidden_files`); 默认值 `default_key_hidden_files` | core/pref/keys/ExplorerKeys.kt:25, ui/explorer/ExplorerPathMenu.kt:174 | ValueConstant |  | 候选值, 默认值 |
| `key_hidden_files_show` | 候选值 `keys_hidden_files` (`key_hidden_files`) | core/pref/keys/ExplorerKeys.kt:26, core/pref/keys/ExplorerKeys.kt:103, ui/explorer/ExplorerPathMenu.kt:174 | ValueConstant |  | 候选值 |
| `key_history_max_days` | 回收站与版本历史设置 > 版本历史 | core/pref/keys/HistoryKeys.kt:42 | Setting | `history` | 保留策略 |
| `key_history_max_file_size_to_track_bytes` | 回收站与版本历史设置 > 版本历史 | core/pref/keys/HistoryKeys.kt:39 | Setting | `history` | 保留策略 |
| `key_history_max_total_bytes` | 回收站与版本历史设置 > 版本历史 | core/pref/keys/HistoryKeys.kt:51 | Setting | `history` | 保留策略 |
| `key_history_max_total_bytes_per_file` | 回收站与版本历史设置 > 版本历史 | core/pref/keys/HistoryKeys.kt:48 | Setting | `history` | 保留策略 |
| `key_history_max_versions` | 回收站与版本历史设置 > 版本历史 | core/pref/keys/HistoryKeys.kt:45 | Setting | `history` | 保留策略 |
| `key_ignored_updates` |  | core/pref/keys/UpdatesKeys.kt:33, core/pref/keys/NamedStores.kt:67 | Setting | `updates` | 默认偏好中的忽略版本列表 (Gson LinkedHashSet); 同一字符串也是 UpdateIgnoreStore 命名存储的文件名 |
| `key_keep_screen_on_when_in_foreground` | 设置 > 外观 | AbstractAutoJs.kt:172, core/pref/keys/AppearanceKeys.kt:99, ui/main/MainActivity.kt:184 | Setting | `appearance` | 列表项 |
| `key_keep_screen_on_when_in_foreground_all_pages` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`) | core/pref/keys/AppearanceKeys.kt:50, util/ViewUtils.kt:123, util/ViewUtils.kt:1298, util/ViewUtils.kt:1299 | ValueConstant |  | 候选值 |
| `key_keep_screen_on_when_in_foreground_disabled` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`); 默认值 `default_key_keep_screen_on_when_in_foreground` | core/pref/keys/AppearanceKeys.kt:49, util/ViewUtils.kt:120, util/ViewUtils.kt:1293 | ValueConstant |  | 候选值, 默认值 |
| `key_keep_screen_on_when_in_foreground_enabled` |  | core/pref/keys/AppearanceKeys.kt:105 | Setting | `appearance` | 与主键一起由 ViewUtils 写入的布尔镜像, 须与主键同进同出 |
| `key_keep_screen_on_when_in_foreground_homepage_only` | 候选值 `keys_keep_screen_on_when_in_foreground` (`key_keep_screen_on_when_in_foreground`) | core/pref/keys/AppearanceKeys.kt:51, core/pref/keys/MiscStateKeys.kt:183, core/pref/keys/MiscStateKeys.kt:184, util/ViewUtils.kt:1304, util/ViewUtils.kt:1305 | ValueConstant |  | 候选值 |
| `key_keep_screen_on_when_in_foreground_last_enabled` |  | core/pref/keys/MiscStateKeys.kt:180 | State | `other` | 抽屉开关恢复用的上次启用模式; P4.2 声明为 MiscStateKeys |
| `key_last_no_newer_updates` |  | core/pref/keys/MiscStateKeys.kt:143 | State | `other` | 时间戳; P4.2 声明为 MiscStateKeys |
| `key_last_updates_auto_checked` |  | core/pref/keys/MiscStateKeys.kt:149 | State | `other` | 时间戳; P4.2 声明为 MiscStateKeys |
| `key_last_updates_checked` |  | core/pref/keys/MiscStateKeys.kt:140 | State | `other` | 时间戳; P4.2 声明为 MiscStateKeys |
| `key_last_updates_postponed` |  | core/pref/keys/MiscStateKeys.kt:146 | State | `other` | 时间戳; P4.2 声明为 MiscStateKeys |
| `key_launcher_icon` | 设置 > 配置 | core/pref/keys/GeneralKeys.kt:74 | Setting | `general` | 列表项, LauncherIconPreference.persistString; 生效需切换组件 (APPLY) |
| `key_launcher_icon_adaptive` | 候选值 `keys_launcher_icon` (`key_launcher_icon`); 默认值 `default_key_launcher_icon` | core/pref/keys/GeneralKeys.kt:41, ui/settings/LauncherIconPreference.kt:57, ui/settings/LauncherIconPreference.kt:96, ui/settings/LauncherIconPreference.kt:218 | ValueConstant |  | 候选值, 默认值 |
| `key_launcher_icon_transparent_background` | 候选值 `keys_launcher_icon` (`key_launcher_icon`) | core/pref/keys/GeneralKeys.kt:42, ui/settings/LauncherIconPreference.kt:56, ui/settings/LauncherIconPreference.kt:217 | ValueConstant |  | 候选值 |
| `key_launcher_shortcuts` | 设置 > 配置 | core/pref/keys/GeneralKeys.kt:90 | Excluded |  | 动作条目 (启动器快捷方式对话框), 不持久化值 |
| `key_layout_inspector_field_mode_introduced` |  | core/pref/keys/MiscStateKeys.kt:271 | State | `other` | 功能介绍已展示; P4.2 声明为 MiscStateKeys |
| `key_layout_inspector_filter_profile_selected` |  | core/pref/keys/LayoutInspectorKeys.kt:31 | Setting | `layout_inspector` | 选中的筛选配置档 id |
| `key_layout_inspector_filter_profiles` |  | core/pref/keys/LayoutInspectorKeys.kt:27 | Setting | `layout_inspector` | 筛选配置档 JSON |
| `key_layout_inspector_node_copy_mode` |  | core/pref/keys/LayoutInspectorKeys.kt:35 | Setting | `layout_inspector` | 节点复制模式 |
| `key_layout_inspector_query_history` |  | core/pref/keys/MiscStateKeys.kt:279 | State | `other` | 查询历史 JSON; P4.2 声明为 MiscStateKeys |
| `key_layout_inspector_query_mode` |  | core/pref/keys/LayoutInspectorKeys.kt:39 | Setting | `layout_inspector` | 查询模式 |
| `key_layout_inspector_selector_mode_introduced` |  | core/pref/keys/MiscStateKeys.kt:274 | State | `other` | 功能介绍已展示; 资源值缺少 `$_` 中缀 (键名不得改, R1); P4.2 声明为 MiscStateKeys |
| `key_leakcanary_enabled` | 开发者选项 > 内存诊断 | core/pref/keys/DeveloperKeys.kt:46, ui/settings/DeveloperOptionsFragment.kt:24 | Setting | `developer` | LeakCanary 开关 (开发者选项, 页面不可见, XML 默认 true); 来自内存路线图 P1 的新资源, 由调度会话决定登记 (附录 C Q15) |
| `key_local_network_permission` | 其他资源 res/xml/fragment_preferences_inrt.xml:24 | inrt/SettingsActivity.kt:54 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 (附录 A.2, Q13 已拍板) |
| `key_log_activity_input_bar_visible` |  | core/pref/keys/ConsoleKeys.kt:30 | Setting | `console` | 日志页 (LogPageConsoleSettings) |
| `key_log_activity_log_coloring` |  | core/pref/keys/ConsoleKeys.kt:33 | Setting | `console` | 日志页 |
| `key_log_activity_text_size` |  | core/pref/keys/ConsoleKeys.kt:37 | Setting | `console` | 日志页 |
| `key_log_activity_time_format` |  | core/pref/keys/ConsoleKeys.kt:27 | Setting | `console` | ConsoleTimeSettings.LOG_ACTIVITY |
| `key_log_activity_time_visible` |  | core/pref/keys/ConsoleKeys.kt:24 | Setting | `console` | ConsoleTimeSettings.LOG_ACTIVITY |
| `key_log_activity_typescript_console_visible` |  | core/pref/keys/ConsoleKeys.kt:40 | Setting | `console` | 日志页 |
| `key_mail_settings` | 开发者选项 > 邮件 |  | Excluded |  | 动作条目, 打开邮件插件设置 (插件自身设置第一期不含, D3) |
| `key_manage_ignored_updates` | 设置 > 更新 | core/pref/keys/UpdatesKeys.kt:54 | Excluded |  | 动作条目 (管理忽略的更新) |
| `key_mcp_server_normally_closed` |  | core/pref/keys/MiscStateKeys.kt:127 | State | `other` | 上次是否正常关闭, 用于启动时恢复; P4.2 声明为 MiscStateKeys |
| `key_memory_script_heap_dump` | 开发者选项 > 内存诊断 | core/pref/keys/DeveloperKeys.kt:63 | Setting | `developer` | 开发者选项 > 内存诊断: 允许脚本导出 hprof (release 构建的 memory.dumpHeap, debug 构建始终允许), 默认 false (内存路线图 P5.1, 附录 C Q8); 按 key_memory_unreclaimed_warning 的先例登记, DeveloperKeys 声明 |
| `key_memory_unreclaimed_warning` | 开发者选项 > 内存诊断 | core/pref/keys/DeveloperKeys.kt:54, runtime/memory/MemoryDiagnostics.kt:112 | Setting | `developer` | 开发者选项 > 内存诊断: 已结束的脚本未被回收时在控制台警告, 默认 true (内存路线图 P2.3, 6b09b7d8e); 按 Q16 先例登记 (附录 C Q21), DeveloperKeys 声明 |
| `key_new_script_module_layer` | 开发者选项 > 脚本模块 | core/pref/keys/DeveloperKeys.kt:26 | Setting | `developer` | 开关 |
| `key_night_mode` | 设置 > 外观 | core/plugin/center/PluginCenterFragment.kt:979, core/pref/keys/AppearanceKeys.kt:64, ui/main/plugin/PluginFragment.kt:64 | Setting | `appearance` | 列表项 (ViewUtils.MODE) |
| `key_night_mode_always_off` | 候选值 `keys_night_mode` (`key_night_mode`) | core/pref/keys/AppearanceKeys.kt:44, ui/settings/NightModePreference.kt:54, util/ViewUtils.kt:1675 | ValueConstant |  | 候选值 (MODE.DAY) |
| `key_night_mode_always_on` | 候选值 `keys_night_mode` (`key_night_mode`) | core/pref/keys/AppearanceKeys.kt:43, util/ViewUtils.kt:1676 | ValueConstant |  | 候选值 (MODE.NIGHT) |
| `key_night_mode_enabled` |  | core/plugin/center/PluginCenterFragment.kt:980, core/pref/keys/AppearanceKeys.kt:70, ui/main/plugin/PluginFragment.kt:65 | Setting | `appearance` | 与 `key_night_mode` 一起由 NightModePreference 与抽屉写入 |
| `key_night_mode_follow_system` | 候选值 `keys_night_mode` (`key_night_mode`); 默认值 `default_key_night_mode` | core/pref/keys/AppearanceKeys.kt:42, ui/settings/NightModePreference.kt:55, util/ViewUtils.kt:1677 | ValueConstant |  | 候选值 (MODE.FOLLOW), 默认值 |
| `key_night_mode_null` |  | core/pref/keys/AppearanceKeys.kt:45, util/ViewUtils.kt:1678 | ValueConstant |  | ViewUtils.MODE.NULL, 不在列表候选中 |
| `key_nodejs_doctor_report` | 开发者选项 > Node.js | core/pref/keys/DeveloperKeys.kt:88 | Excluded |  | 动作条目 (生成 Node.js 诊断报告) |
| `key_nodejs_inspector_enabled` | 开发者选项 > Node.js | core/pref/keys/DeveloperKeys.kt:38 | Setting | `developer` | 开关 |
| `key_not_showing_main_activity` | 其他资源 res/xml/fragment_preferences_inrt.xml:72 | inrt/Pref.kt:44, inrt/Pref.kt:70, runtime/api/augment/settings/Settings.kt:30 | Excluded |  | inrt 运行配置 (打包时由 launchConfig 写入); 主应用只经脚本 API settings 读写, 无消费方 (附录 A.2, Q13 已拍板) |
| `key_pc_server_address_history` |  | core/pref/keys/MiscStateKeys.kt:132 | State | `other` | 地址历史 (Gson LinkedHashSet); P4.2 声明为 MiscStateKeys |
| `key_plugin_center_auto_check_updates` | 插件中心设置 > 更新 | core/plugin/center/PluginCenterFragment.kt:139, core/pref/keys/PluginCenterKeys.kt:57 | Setting | `plugin_center` | 开关 |
| `key_plugin_center_last_online_check_ts` |  | core/pref/keys/MiscStateKeys.kt:153 | State | `other` | 时间戳; P4.2 声明为 MiscStateKeys |
| `key_plugin_center_show_official_plugins` | 插件中心设置 > 在线插件来源 | core/plugin/center/PluginCenterFragment.kt:137, core/pref/keys/PluginCenterKeys.kt:45 | Setting | `plugin_center` | 开关 |
| `key_plugin_center_show_third_party_plugins` | 插件中心设置 > 在线插件来源 | core/plugin/center/PluginCenterFragment.kt:138, core/pref/keys/PluginCenterKeys.kt:51 | Setting | `plugin_center` | 开关 |
| `key_post_notifications_permission` | 其他资源 res/xml/fragment_preferences_inrt.xml:30 | inrt/SettingsActivity.kt:53 | Excluded |  | inrt 设置页的权限开关, 反映系统授权状态 (附录 A.2, Q13 已拍板) |
| `key_post_notifications_permission_request_attempted` |  | core/pref/keys/MiscStateKeys.kt:159 | State | `other` | 是否已请求过通知权限; P4.2 声明为 MiscStateKeys |
| `key_pref_bundle_default_item` |  | theme/preference/MaterialListPreference.kt:75, ui/settings/LauncherIconPreference.kt:217, ui/settings/NightModePreference.kt:54 | Excluded |  | MaterialListPreference 的 Bundle 参数名, 不是偏好键 (附录 A.2) |
| `key_pref_bundle_disabled_items` |  | theme/preference/MaterialListPreference.kt:80, ui/settings/LauncherIconPreference.kt:218, ui/settings/NightModePreference.kt:55 | Excluded |  | MaterialListPreference 的 Bundle 参数名, 不是偏好键 (附录 A.2) |
| `key_record_toast` | 设置 > 录制脚本 | core/pref/keys/RecordKeys.kt:30 | Setting | `record` | 开关 |
| `key_release_history` | 设置 > 更新 | core/pref/keys/UpdatesKeys.kt:55 | Excluded |  | 动作条目 (发行历史) |
| `key_restart_strategy` | 设置 > 配置 | core/pref/keys/GeneralKeys.kt:54 | Setting | `general` | 列表项 |
| `key_restart_strategy_quick` | 候选值 `keys_restart_strategy` (`key_restart_strategy`); 默认值 `default_key_restart_strategy` | core/pref/keys/GeneralKeys.kt:31, core/pref/keys/GeneralKeys.kt:102 | ValueConstant |  | 候选值, 默认值 |
| `key_restart_strategy_scheduled` | 候选值 `keys_restart_strategy` (`key_restart_strategy`) | core/pref/keys/GeneralKeys.kt:32 | ValueConstant |  | 候选值 |
| `key_rhino_java_primitive_wrap` | 设置 > 扩展性 | core/pref/keys/ExtensibilityKeys.kt:30 | Setting | `extensibility` | 开关 |
| `key_root_manager_apps_cache` |  | core/pref/keys/RunningKeys.kt:59 | Cache |  | Root 管理器应用列表缓存 (附录 A.2) |
| `key_root_manager_apps_custom` |  | core/pref/keys/RunningKeys.kt:55 | Setting | `running` | 用户追加的 Root 管理器包名 (StringSet) |
| `key_root_mode` | 设置 > 脚本运行 | core/pref/keys/RunningKeys.kt:42 | Setting | `running` | 列表项 (RootUtils.RootMode) |
| `key_root_mode_auto_detect` | 候选值 `keys_root_mode` (`key_root_mode`); 默认值 `default_key_root_mode` | core/pref/keys/RunningKeys.kt:25, util/RootUtils.java:133 | ValueConstant |  | 候选值, 默认值 |
| `key_root_mode_force_non_root` | 候选值 `keys_root_mode` (`key_root_mode`) | core/pref/keys/RunningKeys.kt:27, util/RootUtils.java:132 | ValueConstant |  | 候选值 |
| `key_root_mode_force_root` | 候选值 `keys_root_mode` (`key_root_mode`) | core/pref/keys/RunningKeys.kt:26, util/RootUtils.java:131 | ValueConstant |  | 候选值 |
| `key_root_record_out_file_type` | 设置 > 录制脚本 | core/pref/keys/RecordKeys.kt:34 | Setting | `record` | 列表项 |
| `key_root_record_out_file_type_binary` | 候选值 `keys_root_record_out_file_type` (`key_root_record_out_file_type`); 默认值 `default_key_root_record_out_file_type` | core/pref/keys/RecordKeys.kt:19, core/pref/keys/RecordKeys.kt:44 | ValueConstant |  | 候选值, 默认值 |
| `key_root_record_out_file_type_js` | 候选值 `keys_root_record_out_file_type` (`key_root_record_out_file_type`) | core/pref/keys/RecordKeys.kt:20 | ValueConstant |  | 候选值 |
| `key_run_on_boot_inrt` | 其他资源 res/xml/fragment_preferences_inrt.xml:66 | inrt/Pref.kt:52, inrt/Pref.kt:71 | Excluded |  | inrt 运行配置 (打包时由 launchConfig 写入; 附录 A.2, Q13 已拍板) |
| `key_scheduled_restart_backend` |  | core/pref/keys/GeneralKeys.kt:60 | Setting | `general` | 计划重启后端 (ScheduledRestartSettingsDialogBuilder) |
| `key_scheduled_restart_backend_alarm_manager` |  | core/pref/keys/GeneralKeys.kt:36, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:41, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:72, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:91 | ValueConstant |  | 候选值 |
| `key_scheduled_restart_backend_work_manager` | 默认值 `default_key_scheduled_restart_backend` | core/pref/keys/GeneralKeys.kt:37, core/pref/keys/GeneralKeys.kt:107, ui/settings/ScheduledRestartSettingsDialogBuilder.kt:90 | ValueConstant |  | 候选值, 默认值 |
| `key_scheduled_restart_delay` |  | core/pref/keys/GeneralKeys.kt:67 | Setting | `general` | 计划重启延迟 |
| `key_screen_capture_request_delay` | 设置 > 脚本运行 | core/pref/keys/RunningKeys.kt:49 | Setting | `running` | 截图授权延迟 |
| `key_script_module_debug_print` | 开发者选项 > 脚本模块 | core/pref/keys/DeveloperKeys.kt:30 | Setting | `developer` | 开关 |
| `key_script_module_debug_print_disabled_modules` | 开发者选项 > 脚本模块 | core/pref/keys/DeveloperKeys.kt:34 | Setting | `developer` | StringSet |
| `key_scripts_after_app_restart` |  | core/pref/keys/GeneralKeys.kt:92, util/IntentUtils.kt:440, util/IntentUtils.kt:478 | Excluded |  | 重启前写入, 重启后由 runAfterRestartIfNeeded 消费的一次性交接值 (附录 A.2, Q13 已拍板) |
| `key_server_address` |  | core/pref/keys/ConnectionKeys.kt:25 | Setting | `connection` | 服务端地址 |
| `key_server_mode_lan_discovery` |  | core/pref/keys/ConnectionKeys.kt:69 | Setting | `connection` | 服务端模式管理器开关 |
| `key_server_mode_manager_clients_level` |  | core/pref/keys/MiscStateKeys.kt:113 | State | `other` | 管理器对话框展开级别; P4.2 声明为 MiscStateKeys |
| `key_server_mode_manager_control_level` |  | core/pref/keys/MiscStateKeys.kt:107 | State | `other` | 管理器对话框展开级别; P4.2 声明为 MiscStateKeys |
| `key_server_mode_manager_settings_collapsed` |  | core/pref/keys/MiscStateKeys.kt:110 | State | `other` | 管理器设置区折叠状态; P4.2 声明为 MiscStateKeys |
| `key_server_mode_manager_status_level` |  | core/pref/keys/MiscStateKeys.kt:104 | State | `other` | 管理器对话框展开级别; P4.2 声明为 MiscStateKeys |
| `key_server_mode_notify_connection_changes` |  | core/pref/keys/ConnectionKeys.kt:62 | Setting | `connection` | 服务端模式管理器开关 |
| `key_server_mode_restore_on_launch` |  | core/pref/keys/ConnectionKeys.kt:55 | Setting | `connection` | 服务端模式管理器开关 |
| `key_server_socket_normally_closed` |  | core/pref/keys/MiscStateKeys.kt:123 | State | `other` | 上次是否正常关闭, 用于启动时恢复; P4.2 声明为 MiscStateKeys |
| `key_setup_wizard_welcome_done` |  | core/pref/keys/MiscStateKeys.kt:163 | State | `other` | 欢迎向导已完成; P4.2 声明为 MiscStateKeys |
| `key_setup_wizards` | 设置 > 配置 | core/pref/keys/GeneralKeys.kt:91, ui/settings/PreferencesFragment.kt:24 | Excluded |  | 动作条目 (打开设置向导) |
| `key_stable_mode` | 设置 > 无障碍服务; 其他资源 res/xml/fragment_preferences_inrt.xml:92 | core/pref/keys/A11yKeys.kt:38, runtime/api/augment/settings/Settings.kt:27 | Setting | `a11y` | 开关, 也出现在无障碍服务管理器与脚本 API settings |
| `key_theme_color` | 设置 > 外观 | core/pref/keys/AppearanceKeys.kt:133 | Excluded |  | 动作条目 (打开主题色选择), 值存于 `key_theme_color_primary` / `_primary_dark` / `_accent` |
| `key_theme_color_accent` |  | core/pref/keys/AppearanceKeys.kt:93 | Setting | `appearance` | 主题色三项之一 |
| `key_theme_color_primary` |  | core/pref/keys/AppearanceKeys.kt:81 | Setting | `appearance` | 主题色三项之一 |
| `key_theme_color_primary_dark` |  | core/pref/keys/AppearanceKeys.kt:87 | Setting | `appearance` | 主题色三项之一 |
| `key_timed_task_backend` | 设置 > 定时任务 | core/pref/keys/TimedTaskKeys.kt:28 | Setting | `timed_task` | 列表项 |
| `key_timed_task_backend_alarm` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`); 默认值 `default_key_timed_task_backend` | core/pref/keys/DialogStateKeys.kt:55, core/pref/keys/TimedTaskKeys.kt:20, timing/TimedTaskScheduler.kt:33 | ValueConstant |  | 候选值, 默认值; 同一字符串还被 ExactAlarmPermissionHelper 用作 NotAskAgainDialog 的键 (默认偏好中的 Boolean 状态), P4.1 声明为 DialogStateKeys.TIMED_TASK_EXACT_ALARM_PROMPT (storedAs) |
| `key_timed_task_backend_job` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`) | core/pref/keys/TimedTaskKeys.kt:22, timing/TimedTaskScheduler.kt:35 | ValueConstant |  | 候选值 |
| `key_timed_task_backend_work` | 候选值 `keys_timed_task_backend` (`key_timed_task_backend`) | core/pref/keys/TimedTaskKeys.kt:21, timing/TimedTaskScheduler.kt:34 | ValueConstant |  | 候选值 |
| `key_trash_and_version_history_settings` | 设置 > 回收站与版本历史 | core/pref/keys/HistoryKeys.kt:75 | Excluded |  | 动作条目 (打开回收站与版本历史子页) |
| `key_trash_max_days` | 回收站与版本历史设置 > 回收站 | core/pref/keys/HistoryKeys.kt:54 | Setting | `history` | 保留策略 |
| `key_trash_max_total_bytes` | 回收站与版本历史设置 > 回收站 | core/pref/keys/HistoryKeys.kt:57 | Setting | `history` | 保留策略 |
| `key_updates_checked_states_cleared` | 开发者选项 > 更新 | core/pref/keys/UpdatesKeys.kt:56 | Excluded |  | 动作条目 (开发者选项, 清除检查更新状态) |
| `key_use_volume_control_record` | 设置 > 录制脚本 | core/pref/keys/RecordKeys.kt:25 | Setting | `record` | 开关 |
| `key_use_volume_control_running` | 设置 > 脚本运行; 其他资源 res/xml/fragment_preferences_inrt.xml:60 | core/pref/keys/RunningKeys.kt:32, inrt/Pref.kt:48, runtime/api/augment/settings/Settings.kt:29 | Setting | `running` | 开关, 也是脚本 API settings 的 STOP_ALL_ON_VOLUME_UP |
| `key_version_history_restore_does_not_auto_save_to_disk` |  | core/pref/keys/DialogStateKeys.kt:51 | State | `other` | VersionHistoryController 中 NotAskAgainDialog 的不再提示键, 不是设置 (Q13 (a) 已拍板, P4.1); P4.1 声明为 DialogStateKeys |
| `key_working_directory` |  | core/pref/keys/ExplorerKeys.kt:44 | Setting | `explorer`, deployment `CREATE_DIRECTORY` | 工作目录; 目标不存在时作为部署项创建 (D20 / Q1) |
| `key_working_directory_history` |  | core/pref/keys/MiscStateKeys.kt:172 | State | `other` | 工作目录历史 (Gson LinkedHashSet); P4.2 声明为 MiscStateKeys |
| `key_working_directory_initialized` |  | core/pref/keys/MiscStateKeys.kt:167 | State | `other` | 首次确定工作目录的标记; P4.2 声明为 MiscStateKeys |

## 3. 源码字面量键

字符串字面量形如 `"key_$_..."` 且不等于任何 `key_*` 资源值的键 (`*` 为模板变量). 存储列是按调用文件推测的结果: 文件内调用了 `getSharedPreferences(` 时取其文件名, 否则记为 `default`.

| 键 | 存储 (推测) | 引用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `...` | default | core/pref/keys/MiscStateKeys.kt:36 | State | `other` | 代码编辑器查找 / 替换对话框的查找文本, 键名就是三个点 (FindOrReplaceDialogBuilder); P0 清单未识别, P2.6 声明为 MiscStateKeys (附录 C Q15) |
| `AboutActivity.start_feedback_activity` | default | core/pref/keys/DialogStateKeys.kt:87 | State | `other` | 关于页 "反馈" 打开 GitHub issue 页前的不再提示, 名称为 AboutActivity 的简单类名加后缀; P0 清单未识别, P4.1 声明为 DialogStateKeys (附录 C Q22) |
| `COLOR_FAVORITE_SORT` | default | core/pref/keys/AppearanceKeys.kt:122 | Setting | `appearance` | 收藏颜色的排序方式, FavoriteSort 的键 (VirtualColorLibraryActivity); P0 清单未识别 (附录 C Q18), P4.2 声明为 AppearanceKeys |
| `COLOR_HISTORY_MERGE_SIMILAR` | default | core/pref/keys/AppearanceKeys.kt:118 | Setting | `appearance` | 颜色历史列表的 "合并相似颜色" (ColorSelectBaseActivity); P3.6 声明为 AppearanceKeys (附录 C Q15) |
| `DrawerMenuToggleableItem$` | default | core/pref/keys/DialogStateKeys.kt:97 | State | `other` | 前缀键族 (后接抽屉开关在当前语言下的标题): 抽屉开关提示的不再提示 (DrawerMenuToggleableItem.getPrompt), 切换语言后按新标题另记; P0 清单未识别, P4.1 声明为 DialogStateKeys.DRAWER_TOGGLE_PROMPT (附录 C Q22) |
| `ExplorerView.explorer_current` | default | core/pref/keys/MiscStateKeys.kt:256 | State | `other` | 文件管理器的当前目录 (ExplorerViewStateStore); P0 清单未识别 (附录 C Q24), P4.2 声明为 MiscStateKeys |
| `ExplorerView.explorer_history` | default | core/pref/keys/MiscStateKeys.kt:264 | State | `other` | 文件管理器的页面历史, JSON 字符串列表 (ExplorerViewStateStore); P0 清单未识别 (附录 C Q24), P4.2 声明为 MiscStateKeys |
| `ExplorerView.explorer_root` | default | core/pref/keys/MiscStateKeys.kt:259 | State | `other` | 文件管理器的根目录 (ExplorerViewStateStore); P0 清单未识别 (附录 C Q24), P4.2 声明为 MiscStateKeys |
| `ExplorerView.page_state` | default | core/pref/keys/MiscStateKeys.kt:252 | State | `other` | 文件管理器页面的滚动位置 (ExplorerViewStateStore); P0 清单未识别 (附录 C Q24), P4.2 声明为 MiscStateKeys |
| `INTELLIGENT_COLOR_ACTIVE_SNAPSHOT` | default | core/pref/keys/MiscStateKeys.kt:220 | State | `other` | 智能配色的当前快照, JSON 对象文本 (IntelligentColorEngine); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `INTELLIGENT_COLOR_RUNTIME_STATE` | default | core/pref/keys/MiscStateKeys.kt:223 | State | `other` | 智能配色的运行状态, JSON 对象文本 (IntelligentColorEngine); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `INTELLIGENT_COLOR_SCRIPT_STATE` | default | core/pref/keys/MiscStateKeys.kt:226 | State | `other` | 脚本设置的智能配色状态名 (IntelligentColorEngine); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `SELECTED_COLOR_LIBRARY_ID` | default | core/pref/keys/MiscStateKeys.kt:202 | State | `other` | 颜色选择页选中的颜色库 id (ColorSelectBaseActivity); 声明随 P4.2 (附录 C Q15); P4.2 声明为 MiscStateKeys |
| `SELECTED_COLOR_LIBRARY_ITEM_ID` | default | core/pref/keys/MiscStateKeys.kt:205 | State | `other` | 颜色选择页选中的颜色条目 id (ColorSelectBaseActivity.KEY_SELECTED_COLOR_ITEM_ID); 声明随 P4.2 (附录 C Q15); P4.2 声明为 MiscStateKeys |
| `SELECTED_COLOR_LIBRARY_ITEM_NAME` | default | core/pref/keys/MiscStateKeys.kt:212 | State | `other` | 选中的颜色条目名称缓存 (ColorSelectBaseActivity.KEY_SELECTED_COLOR_ITEM_NAME); 声明随 P4.2 (附录 C Q15); P4.2 声明为 MiscStateKeys |
| `SELECTED_COLOR_LIBRARY_NAME` | default | core/pref/keys/MiscStateKeys.kt:209 | State | `other` | 选中的用户库或智能库名称缓存, 供摘要显示 (ColorSelectBaseActivity); 声明随 P4.2 (附录 C Q15); P4.2 声明为 MiscStateKeys |
| `THEME_COLOR_DERIVED_ACCENT_AND_DARK` | default | core/pref/keys/AppearanceKeys.kt:114 | Setting | `appearance` | 强调色与深色是否由主题色派生 (ThemeColorManager); P3.6 声明为 AppearanceKeys (附录 C Q15) |
| `THEME_COLOR_SCRIPT_UNDO` | default | core/pref/keys/MiscStateKeys.kt:216 | State | `other` | 脚本改主题色前的撤销快照, JSON 对象文本 (ScriptThemeChanges); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `active_profile` | editor_symbols_config, build_apk_config_profiles | core/pref/keys/ApkBuildKeys.kt:25, core/pref/keys/EditorKeyboardKeys.kt:41 | Setting | `editor_keyboard` | 两份命名存储共用的键名: 当前符号配置档 (SymbolsConfigStore, editor_keyboard) 与当前 APK 构建配置档 (BuildConfigStore, apk_build); P3.2 / P3.3 声明为 EditorKeyboardKeys / ApkBuildKeys; 各随整份计数 |
| `dex_compiler_experimental_component` | default | core/pref/keys/DeveloperKeys.kt:71 | Setting | `developer` | 选择的 dex 编译器组件; 导入时插件未安装则与开关一起跳过 (P7.3); P3.6 声明为 DeveloperKeys (附录 C Q15) |
| `dex_compiler_experimental_enabled` | default | core/pref/keys/DeveloperKeys.kt:67 | Setting | `developer` | 开发者选项选择的实验性 dex 编译器插件开关 (DexCompilerOptInStore); P3.6 声明为 DeveloperKeys (附录 C Q15) |
| `editor.debug.long_click_hint` | default | core/pref/keys/DialogStateKeys.kt:83 | State | `other` | 代码编辑器 "启动调试器" 菜单的不再提示 (EditorMenu); P0 清单未识别, P4.1 声明为 DialogStateKeys (附录 C Q22) |
| `exact_component` | jvm_source_provider_selection | core/pref/keys/PluginCenterKeys.kt:224 | Setting | `plugin_center` | Java 的 JVM 源码提供方组件 (JvmSourceProviderSelection, 原键只用于 Java); P3.3 声明为 PluginCenterKeys; 属于命名存储, 随整份计数 |
| `exact_component_kotlin` | jvm_source_provider_selection | core/pref/keys/PluginCenterKeys.kt:230 | Setting | `plugin_center` | Kotlin 的 JVM 源码提供方组件; P3.3 声明为 PluginCenterKeys; 属于命名存储, 随整份计数 |
| `find_or_replace_using_regex` | default | core/pref/keys/MiscStateKeys.kt:39 | State | `other` | 查找 / 替换对话框的正则开关 (FindOrReplaceDialogBuilder); P2.6 声明为 MiscStateKeys (附录 C Q15) |
| `github_token` | github_auth | core/pref/keys/CredentialKeys.kt:31 | Setting | `credentials` | GitHub 令牌 (GitHubTokenStore); P3.4 声明为 CredentialKeys.GITHUB_TOKEN, sensitive, 属于命名存储, 随整份计数 |
| `initialized_sources` | plugin_center_attention | core/pref/keys/PluginCenterKeys.kt:310 | State | `other` | 已比对过一次的索引来源 (PluginCenterAttentionStore); P3.1 声明为 PluginCenterKeys 的 State; 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_name_sort_type_locked` | ace_editor_font_ui_preferences | core/pref/keys/EditorKeyboardKeys.kt:64 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_preview_excluded_features` | ace_editor_font_ui_preferences | core/pref/keys/EditorKeyboardKeys.kt:78 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_preview_excluded_types` | ace_editor_font_ui_preferences | core/pref/keys/EditorKeyboardKeys.kt:85 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_selected_variant_` | ace_editor_font_ui_preferences | core/pref/keys/EditorKeyboardKeys.kt:91 | Setting | `editor_keyboard` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_size_sort_type_locked` | ace_editor_font_ui_preferences | core/pref/keys/EditorKeyboardKeys.kt:71 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_editor_font_sort_mode` | ace_editor_font_ui_preferences | core/pref/keys/EditorKeyboardKeys.kt:57 | Setting | `editor_keyboard` | 属于命名存储, 随整份计数 |
| `key_$_ace_lsp_file_types_revision` | default | core/pref/keys/MiscStateKeys.kt:55, ui/edit/editor/ace/AceEditorLspPreferences.kt:10 | State | `other` | 默认文件类型列表的迁移版本号 (AceEditorLspPreferences); P4.2 声明为 MiscStateKeys |
| `key_$_ace_semantic_java_enabled` | default | core/pref/keys/EditorKeys.kt:242, ui/edit/editor/ace/AceEditorLspPreferences.kt:15 | Setting | `editor_assistance` | 语义语言开关 (AceSemanticLanguagesPreference) |
| `key_$_ace_semantic_kotlin_enabled` | default | core/pref/keys/EditorKeys.kt:248, ui/edit/editor/ace/AceEditorLspPreferences.kt:16 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_ace_semantic_lua_enabled` | default | core/pref/keys/EditorKeys.kt:236, ui/edit/editor/ace/AceEditorLspPreferences.kt:14 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_ace_semantic_python_enabled` | default | core/pref/keys/EditorKeys.kt:230, ui/edit/editor/ace/AceEditorLspPreferences.kt:13 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_ace_semantic_typescript_enabled` | default | core/pref/keys/EditorKeys.kt:224, ui/edit/editor/ace/AceEditorLspPreferences.kt:12 | Setting | `editor_assistance` | 语义语言开关 |
| `key_$_codegen_model_current_debug_` | default | core/pref/keys/LayoutInspectorKeys.kt:56 | Setting | `layout_inspector` | 前缀键: debug 包代码生成模型的当前选择, 按目录配置名派生 (ModelCatalogConfiguration.selectionPreferenceKey); P3.6 声明为 LayoutInspectorKeys 的键族 (附录 C Q15) |
| `key_$_dialog_terminal_tips` | default | core/plugin/terminal/TerminalLegacyCleanup.kt:29 | Excluded |  | 已移除的宿主终端的旧键, TerminalLegacyCleanup 每次启动删除 (附录 A.2, Q13 已拍板) |
| `key_$_editor_engine_selection_mode` | default | core/pref/keys/EditorKeys.kt:103, ui/edit/editor/engine/EditorEnginePreferences.kt:15 | Setting | `editor_general` | 与 `key_editor_engine` 一起由 EditorEnginePreferences.persist 写入 |
| `key_$_enabled_plugin_$_` | plugin_center_enable_state | core/pref/keys/PluginCenterKeys.kt:277 | Setting | `plugin_enable_state` | 前缀键族 (后接包名), P3.1 声明为 PluginCenterKeys.PLUGIN_ENABLED; 属于命名存储, 随整份计数 |
| `key_$_ignored_plugin_$_` | key_$_ignored_updates | core/pref/keys/UpdatesKeys.kt:42 | Setting | `updates` | UpdateIgnoreStore 中每个插件忽略的版本, 前缀键族 (后接包名); P3.3 声明为 UpdatesKeys.IGNORED_PLUGIN_VERSIONS; 属于命名存储, 随整份计数 |
| `key_$_last_install_time_$_` | plugin_center_recent | core/pref/keys/PluginCenterKeys.kt:291 | State | `other` | 前缀键族 (后接包名), P3.1 声明为 PluginCenterKeys.LAST_INSTALL_TIME; 属于命名存储, 随整份计数 |
| `key_$_last_orientation` | default | core/pref/keys/MiscStateKeys.kt:237 | State | `other` | CircularMenuWindow 悬浮按钮上次的屏幕方向; P4.2 声明为 MiscStateKeys |
| `key_$_last_uninstall_time_$_` | plugin_center_recent | core/pref/keys/PluginCenterKeys.kt:297 | State | `other` | 前缀键族 (后接包名), P3.1 声明为 PluginCenterKeys.LAST_UNINSTALL_TIME; 属于命名存储, 随整份计数 |
| `key_$_permission_wizard_custom_profiles` | permission_wizard | core/pref/keys/WizardKeys.kt:58 | Setting | `wizards` | 向导的自定义配置档, JSON 数组文本 (WizardProfileCodec); P3.3 声明为 WizardKeys; 属于命名存储, 随整份计数 |
| `key_$_permission_wizard_last_profile_id` | permission_wizard | core/pref/keys/WizardKeys.kt:59 | State | `other` | 上次选择的配置档 id, 状态 (附录 A wizards 备注); P3.3 声明为 WizardKeys; 属于命名存储, 随整份计数 |
| `key_$_permission_wizard_show_done` | permission_wizard | core/pref/keys/WizardKeys.kt:60 | Setting | `wizards` | "显示已完成条目" 选项; P3.3 声明为 WizardKeys; 属于命名存储, 随整份计数 |
| `key_$_plugin_abi_mismatch_download_prompt` | default | core/pref/keys/DialogStateKeys.kt:71 | State | `other` | 不再提示 (PluginAbiCompatibilityPrompt); P4.1 声明为 DialogStateKeys |
| `key_$_plugin_abi_mismatch_enable_prompt` | default | core/pref/keys/DialogStateKeys.kt:75 | State | `other` | 不再提示 (PluginAbiCompatibilityPrompt); P4.1 声明为 DialogStateKeys |
| `key_$_plugin_auth_$_` | plugin_center_authorization | core/pref/keys/CredentialKeys.kt:36 | Setting | `credentials` | 前缀键族: 后接 <包名>$_<签名指纹>; P3.4 声明为 CredentialKeys.PLUGIN_AUTHORIZATION, sensitive, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_enabled_state` | plugin_center_filter_state | core/pref/keys/PluginCenterKeys.kt:75 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_install_state` | plugin_center_filter_state | core/pref/keys/PluginCenterKeys.kt:69 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_mechanism_state` | plugin_center_filter_state | core/pref/keys/PluginCenterKeys.kt:81 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_source_state` | plugin_center_filter_state | core/pref/keys/PluginCenterKeys.kt:87 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_state` | plugin_center_filter_state | core/pref/keys/PluginCenterKeys.kt:65 | Setting | `plugin_center` | 旧版序号键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_filter_updatable_only` | plugin_center_filter_state | core/pref/keys/PluginCenterKeys.kt:93 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_hidden_packages` | plugin_center_visibility_state | core/pref/keys/PluginCenterKeys.kt:187 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_hidden_source_` | plugin_center_visibility_state | core/pref/keys/PluginCenterKeys.kt:199 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_hidden_title_` | plugin_center_visibility_state | core/pref/keys/PluginCenterKeys.kt:193 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:155 | Setting | `plugin_center` | PluginSortStore, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_ascending_` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:156 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_mechanism_secondary` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:158 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:165 | Setting | `plugin_center` | PluginSortStoreOfficial, 与另两个类共用同一份存储 |
| `key_$_plugin_center_sort_state_official_ascending_` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:166 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official_mechanism_secondary` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:168 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official_secondary_` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:167 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_official_source_secondary` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:169 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_secondary_` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:157 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_source_secondary` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:159 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:175 | Setting | `plugin_center` | PluginSortStoreThirdParty, 与另两个类共用同一份存储 |
| `key_$_plugin_center_sort_state_third_party_ascending_` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:176 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party_mechanism_secondary` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:178 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party_secondary_` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:177 | Setting | `plugin_center` | 前缀键, 属于命名存储, 随整份计数 |
| `key_$_plugin_center_sort_state_third_party_source_secondary` | plugin_center_sort_state | core/pref/keys/PluginCenterKeys.kt:179 | Setting | `plugin_center` | 属于命名存储, 随整份计数 |
| `key_$_plugin_hide_confirm_prompt` | default | core/pref/keys/DialogStateKeys.kt:79 | State | `other` | 隐藏插件确认的不再提示 (PluginSettingsFragment); P4.1 声明为 DialogStateKeys |
| `key_$_plugin_install_wizard_custom_profiles` | plugin_install_wizard | core/pref/keys/WizardKeys.kt:67 | Setting | `wizards` | 向导的自定义配置档, JSON 数组文本 (WizardProfileCodec); P3.3 声明为 WizardKeys; 属于命名存储, 随整份计数 |
| `key_$_plugin_install_wizard_first_run_shown` | plugin_install_wizard | core/pref/keys/WizardKeys.kt:84 | Excluded |  | 插件安装向导的首次运行提示, 2d7c72b33 起不再读写, 旧安装中残留; 按已移除终端遗留键的先例排除 (附录 C Q13 / Q20), P3.3 在 WizardKeys 登记 |
| `key_$_plugin_install_wizard_last_profile_id` | plugin_install_wizard | core/pref/keys/WizardKeys.kt:68 | State | `other` | 上次选择的配置档 id, 状态 (附录 A wizards 备注); P3.3 声明为 WizardKeys; 属于命名存储, 随整份计数 |
| `key_$_plugin_install_wizard_show_done` | plugin_install_wizard | core/pref/keys/WizardKeys.kt:69 | Setting | `wizards` | "显示已完成条目" 选项; P3.3 声明为 WizardKeys; 属于命名存储, 随整份计数 |
| `key_$_plugin_mount_enabled_$_` | default | core/pref/keys/PluginCenterKeys.kt:241 | Setting | `plugin_center` | PluginMountStore 前缀键 (挂载开关), P3.1 登记为 Json 键集合 |
| `key_$_plugin_mount_object_name_$_` | default | core/pref/keys/PluginCenterKeys.kt:247 | Setting | `plugin_center` | PluginMountStore 前缀键 (挂载对象名) |
| `key_$_plugin_mount_packages` | default | core/pref/keys/PluginCenterKeys.kt:237 | Setting | `plugin_center` | PluginMountStore 已配置包名列表 |
| `key_$_plugin_mount_point_$_` | default | core/pref/keys/PluginCenterKeys.kt:253 | Setting | `plugin_center` | PluginMountStore 前缀键 (挂载点) |
| `key_$_plugin_update_check_$_` | default | core/pref/keys/PluginCenterKeys.kt:259 | Setting | `plugin_center` | PluginUpdateCheckStore 每个插件的 "检查更新" 开关, 是用户设置 (Q13 (c) 已拍板, P3.1) |
| `key_$_plugin_variant_priority_$_` | default | core/pref/keys/PluginCenterKeys.kt:269 | Setting | `plugin_center` | PluginVariantPriorityStore 每个引擎的变体优先级 (JSON 数组) |
| `key_$_seen_ace_fallback_notice` | default | core/pref/keys/MiscStateKeys.kt:62 | State | `other` | 一次性提示已展示 (EditorEnginePreferences); P4.2 声明为 MiscStateKeys |
| `key_$_seen_ace_large_file_notice` | default | core/pref/keys/MiscStateKeys.kt:65 | State | `other` | 一次性提示已展示; P4.2 声明为 MiscStateKeys |
| `key_$_seen_editor_switch_undo_notice` | default | core/pref/keys/MiscStateKeys.kt:59 | State | `other` | 一次性提示已展示; P4.2 声明为 MiscStateKeys |
| `key_$_suspended_plugin_$_` | plugin_center_enable_state | core/pref/keys/PluginCenterKeys.kt:283 | Setting | `plugin_enable_state` | 挂起标记, 前缀键族 (后接包名), P3.1 声明为 PluginCenterKeys.PLUGIN_SUSPENDED; 属于命名存储, 随整份计数 |
| `key_$_terminal_` | default | core/plugin/terminal/TerminalLegacyCleanup.kt:26 | Excluded |  | 已移除的宿主终端的旧键前缀, TerminalLegacyCleanup 每次启动删除 (附录 A.2, Q13 已拍板) |
| `key_dont_show_main_activity` | default | runtime/api/augment/settings/Settings.kt:43 | Excluded |  | 脚本 API settings 模块的别名字符串, 不是偏好键 |
| `key_down` | default | core/ui/nativeview/ViewPrototype.kt:111, core/ui/nativeview/ViewPrototype.kt:115, execution/ScriptExecuteActivity.kt:338, runtime/api/Events.java:362 | Excluded |  | 按键事件名, 不是偏好键 |
| `key_enable_a11y_service_with_root_access` | default | runtime/api/augment/settings/Settings.kt:39 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_enable_accessibility_service_by_root` | default | runtime/api/augment/settings/Settings.kt:38 | Excluded |  | 脚本 API settings 模块的别名字符串, 不是偏好键 |
| `key_first_using` | default | inrt/Pref.kt:18 | Excluded |  | inrt 打包应用运行时状态 (附录 A.2) |
| `key_foreground_service` | default | runtime/api/augment/settings/Settings.kt:46 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_inrt_applied_build_id` | default | inrt/Pref.kt:19 | Excluded |  | inrt 打包应用运行时状态 (附录 A.2) |
| `key_not_showing_main_activity` | default | runtime/api/augment/settings/Settings.kt:44 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_stable_mode` | default | runtime/api/augment/settings/Settings.kt:36 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `key_up` | default | core/ui/nativeview/ViewPrototype.kt:111, core/ui/nativeview/ViewPrototype.kt:117, runtime/api/Events.java:365 | Excluded |  | 按键事件名, 不是偏好键 |
| `key_use_volume_control_running` | default | runtime/api/augment/settings/Settings.kt:41 | Excluded |  | 脚本 API settings 模块的别名字符串 (资源名形式), 不是偏好键 |
| `known_first_install_times` | plugin_center_attention | core/pref/keys/PluginCenterKeys.kt:304 | State | `other` | 已见过的包的首次安装时间, org.json 对象文本 (PluginCenterAttentionStore); P3.1 声明为 PluginCenterKeys 的 State; 属于命名存储, 随整份计数 |
| `org.autojs.autojs.engine.RootAutomatorEngine.touch_device` | default | core/pref/keys/MiscStateKeys.kt:47 | Cache |  | Root 自动化探测到的触摸设备 id, 本机才有效 (RootAutomatorEngine); P2.6 声明为 MiscStateKeys (附录 C Q15) |
| `org.autojs.autojs.theme.app.ColorSettingRecyclerView.COLOR_SETTING_CUSTOM_COLOR` | default | core/pref/keys/MiscStateKeys.kt:194 | State | `other` | 颜色选择页的自定义颜色 (ColorSelectBaseActivity.KEY_CUSTOM_COLOR); 声明随 P4.2 (附录 C Q15); P4.2 声明为 MiscStateKeys |
| `org.autojs.autojs.theme.app.ColorSettingRecyclerView.SELECTED_COLOR_INDEX` | default | core/pref/keys/MiscStateKeys.kt:198 | State | `other` | 旧布局选中的颜色序号 (ColorSelectBaseActivity.KEY_LEGACY_SELECTED_COLOR_INDEX); 声明随 P4.2 (附录 C Q15); P4.2 声明为 MiscStateKeys |
| `org.autojs.autojs.ui.edit.debug.CodeEvaluateDialogBuilder.code` | default | core/pref/keys/MiscStateKeys.kt:43 | State | `other` | 代码求值对话框的上次输入 (CodeEvaluateDialogBuilder); P2.6 声明为 MiscStateKeys (附录 C Q15) |
| `org.autojs.autojs.ui.floating.CircularMenuWindow.position.x_percent` | default | core/pref/keys/MiscStateKeys.kt:230 | State | `other` | 悬浮按钮位置, 屏幕宽度的比例 (CircularMenuWindow); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `org.autojs.autojs.ui.floating.CircularMenuWindow.position.y_percent` | default | core/pref/keys/MiscStateKeys.kt:233 | State | `other` | 悬浮按钮位置, 屏幕高度的比例 (CircularMenuWindow); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `org.autojs.autojs.ui.floating.controller.FloatingControllerWindow.position.x_percent` | default | core/pref/keys/MiscStateKeys.kt:241 | State | `other` | 悬浮控制器位置, 屏幕宽度的比例, -1 为尚未放置 (FloatingControllerWindow); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `org.autojs.autojs.ui.floating.controller.FloatingControllerWindow.position.y_percent` | default | core/pref/keys/MiscStateKeys.kt:244 | State | `other` | 悬浮控制器位置, 屏幕高度的比例, -1 为尚未放置 (FloatingControllerWindow); P0 清单未识别 (附录 C Q18), P4.2 声明为 MiscStateKeys |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.dir_ascending` | default | core/pref/keys/ExplorerKeys.kt:89 | Setting | `explorer` | 文件管理器中目录是否升序 (ExplorerItemManager.SortConfig); P0 清单未识别 (附录 C Q18), P4.2 声明为 ExplorerKeys |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.dir_sort_type` | default | core/pref/keys/ExplorerKeys.kt:83 | Setting | `explorer` | 文件管理器中目录的排序类型 (ExplorerItemManager.SortConfig, 0x10 为按名称); P0 清单未识别 (附录 C Q18), P4.2 声明为 ExplorerKeys |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.file_ascending` | default | core/pref/keys/ExplorerKeys.kt:86 | Setting | `explorer` | 文件管理器中文件是否升序 (ExplorerItemManager.SortConfig); P0 清单未识别 (附录 C Q18), P4.2 声明为 ExplorerKeys |
| `org.autojs.autojs.ui.viewmodel.ExplorerItemManager$SortConfig.file_sort_type` | default | core/pref/keys/ExplorerKeys.kt:80 | Setting | `explorer` | 文件管理器中文件的排序类型 (ExplorerItemManager.SortConfig, 0x10 为按名称); P0 清单未识别 (附录 C Q18), P4.2 声明为 ExplorerKeys |
| `pins` | plugin_center_attention | core/pref/keys/PluginCenterKeys.kt:315 | State | `other` | 当前的临时置顶, org.json 数组文本 (PluginCenterAttentionStore); P3.1 声明为 PluginCenterKeys 的 State; 属于命名存储, 随整份计数 |
| `profiles_json` | editor_symbols_config, build_apk_config_profiles | core/pref/keys/ApkBuildKeys.kt:32, core/pref/keys/EditorKeyboardKeys.kt:48 | Setting | `editor_keyboard` | 两份命名存储共用的键名: 符号配置档 (editor_keyboard) 与 APK 构建配置档 (apk_build), org.json 对象文本; P3.2 / P3.3 声明为 EditorKeyboardKeys / ApkBuildKeys; 各随整份计数 |
| `prompt_$_` | default | core/pref/keys/DialogStateKeys.kt:91 | State | `other` | 前缀键族 (后接列表偏好的键): 列表偏好确认提示的不再提示 (MaterialListPreference.showPrompt); P0 清单未识别, P4.1 声明为 DialogStateKeys.LIST_PREFERENCE_PROMPT (附录 C Q22) |
| `r8_compiler_explicit_component` | default | core/pref/keys/DeveloperKeys.kt:79 | Setting | `developer` | 选择的 R8 编译器组件; 导入时插件未安装则与开关一起跳过 (P7.3); P3.6 声明为 DeveloperKeys (附录 C Q15) |
| `r8_compiler_explicit_enabled` | default | core/pref/keys/DeveloperKeys.kt:75 | Setting | `developer` | 开发者选项显式选择的 R8 编译器插件开关 (R8CompilerOptInStore); P3.6 声明为 DeveloperKeys (附录 C Q15) |
| `show_launcher_icons_for_new_plugins` | plugin_install_wizard | core/pref/keys/WizardKeys.kt:75 | Setting | `wizards` | 插件安装向导: 新安装的插件显示启动图标 (PluginInstallWizardProfileStore); P3.3 声明为 WizardKeys; 属于命名存储, 随整份计数 |
| `soft_input_height` | FunctionsKeyboardHelper | core/pref/keys/EditorKeyboardKeys.kt:103 | Cache |  | 本机测得的软键盘高度 (像素, FunctionsKeyboardHelper), 不导出 (附录 C Q19); P3.2 声明为 EditorKeyboardKeys 的 CacheKey |
| `state_json` | ace_action_history | core/pref/keys/EditorKeyboardKeys.kt:110 | State | `other` | Ace 动作使用历史, org.json 对象文本 (AceActionHistoryStore); P3.2 声明为 EditorKeyboardKeys 的 State; 属于命名存储, 随整份计数 |
| `three_stove_agent_script_root_configuration` | default | core/pref/keys/ExtensibilityKeys.kt:42 | Setting | `extensibility` | 3-Stove Agent 的脚本根目录配置, JSON 对象 (ThreeStoveAgentConnectionOwner); P3.6 声明为 ExtensibilityKeys (附录 C Q15) |

设置页中不经字符串资源的键: `process_exit_history` (fragment_developer_options.xml:181, kind Excluded), `plugin_center_hidden_plugin_management` (fragment_plugin_center_preferences.xml:42, kind Excluded), `plugin_center_auto_mount_management` (fragment_plugin_center_preferences.xml:54, kind Excluded)

## 4. 命名 SharedPreferences

| 文件名 | 解析方式 | 调用处 | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- |
| `FunctionsKeyboardHelper` | 登记 `NamedStores.FUNCTIONS_KEYBOARD_HELPER` (core/pref/keys/NamedStores.kt:33) | core/pref/keys/EditorKeyboardKeys.kt:103 | Cache |  | 只有本机测得的软键盘高度 (soft_input_height), 为缓存, 不导出 (附录 C Q19, 原归 editor_keyboard); P3.2 登记为 NamedStores |
| `ScriptWidgets` | 排除登记 `NamedStores.SCRIPT_WIDGETS` (core/pref/keys/NamedStores.kt:152); 字面量 | external/widget/ScriptWidgets.java:22 | Excluded |  | 桌面小部件 id 由启动器分配, 仅本机有效 (附录 A.2) |
| `ace_action_history` | 登记 `NamedStores.ACE_ACTION_HISTORY` (core/pref/keys/NamedStores.kt:41) | core/pref/keys/EditorKeyboardKeys.kt:110 | State | `other` | Ace 动作历史 (P3.2 为 State); P3.2 登记为 NamedStores, 键声明在 EditorKeyboardKeys |
| `ace_editor_font_ui_preferences` | 登记 `NamedStores.ACE_EDITOR_FONT_UI_PREFERENCES` (core/pref/keys/NamedStores.kt:37) | core/pref/keys/EditorKeyboardKeys.kt:58, core/pref/keys/EditorKeyboardKeys.kt:64, core/pref/keys/EditorKeyboardKeys.kt:71, core/pref/keys/EditorKeyboardKeys.kt:79 等 7 处 | Setting | `editor_keyboard` | 字体选择界面的排序 / 预览 / 变体偏好 (附录 A); P3.2 登记为 NamedStores, 键声明在 EditorKeyboardKeys |
| `autojs.localstorage.*` | 排除登记 `NamedStores.SCRIPT_LOCAL_STORAGES` (core/pref/keys/NamedStores.kt:132); 人工: `.let` 的接收者是 `LocalStorage.NAME_PREFIX + name`; 拼接 | core/storage/LocalStorage.java:28, runtime/api/augment/storages/Storages.kt:48, runtime/api/augment/storages/Storages.kt:61 | Excluded |  | 脚本自己的数据 (附录 A.2) |
| `autojs.node.storage.*.*.*` | 排除登记 `NamedStores.NODE_SCRIPT_STORAGES` (core/pref/keys/NamedStores.kt:136); 函数 storagePreferencesName (engine/NodeBridgeProtocol.kt:3526) | engine/NodeBridgeProtocol.kt:3411 | Excluded |  | Node.js 运行时 storage 桥的脚本数据, 理由同 `autojs.localstorage.*` (附录 A.2, Q13 (e) 已拍板) |
| `build_apk_config_profiles` | 登记 `NamedStores.BUILD_APK_CONFIG_PROFILES` (core/pref/keys/NamedStores.kt:48) | core/pref/keys/ApkBuildKeys.kt:26, core/pref/keys/ApkBuildKeys.kt:33 | Setting | `apk_build` | APK 构建配置档; P3.3 登记为 NamedStores, 键声明在 ApkBuildKeys |
| `bun_host_capability_grants` | 登记 `NamedStores.BUN_HOST_CAPABILITY_GRANTS` (core/pref/keys/NamedStores.kt:121) | core/pref/keys/CredentialKeys.kt:55 | Setting | `credentials` | Bun 宿主能力授权, sensitive; P3.4 登记为 NamedStores, 键声明在 CredentialKeys |
| `device_id_prefs` | 排除登记 `NamedStores.DEVICE_ID_PREFS` (core/pref/keys/NamedStores.kt:128); 常量 SP_NAME (deviceid/DeviceIdProvider.kt:20) | deviceid/DeviceIdProvider.kt:33 | Excluded |  | 设备标识 (附录 A.2) |
| `editor_symbols_config` | 登记 `NamedStores.EDITOR_SYMBOLS_CONFIG` (core/pref/keys/NamedStores.kt:29) | core/pref/keys/EditorKeyboardKeys.kt:42, core/pref/keys/EditorKeyboardKeys.kt:49 | Setting | `editor_keyboard` | 符号键盘配置 (附录 A); P3.2 登记为 NamedStores, 键声明在 EditorKeyboardKeys |
| `explorer_playback_history` | 排除登记 `NamedStores.EXPLORER_PLAYBACK_HISTORY` (core/pref/keys/NamedStores.kt:156); 常量 PREFERENCES_NAME (core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:79) | core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:13, core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:84, core/plugin/explorer/session/ExplorerPlaybackHistoryStore.kt:96 | Excluded |  | 媒体回放进度, 指向本机文件 (P3.5 永久排除) |
| `github_auth` | 登记 `NamedStores.GITHUB_AUTH` (core/pref/keys/NamedStores.kt:105) | core/pref/keys/CredentialKeys.kt:31 | Setting | `credentials` | GitHub 令牌, sensitive; P3.4 登记为 NamedStores, 键声明在 CredentialKeys |
| `jvm_source_provider_selection` | 登记 `NamedStores.JVM_SOURCE_PROVIDER_SELECTION` (core/pref/keys/NamedStores.kt:60) | core/plugin/jvm/JvmSourceProviderSelection.kt:39, core/pref/keys/PluginCenterKeys.kt:224, core/pref/keys/PluginCenterKeys.kt:230 | Setting | `plugin_center` | JVM 源码提供方选择 (附录 A); P3.3 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `key_$_ignored_updates` | 登记 `NamedStores.IGNORED_UPDATES` (core/pref/keys/NamedStores.kt:67) | core/pref/keys/UpdatesKeys.kt:43 | Setting | `updates` | UpdateIgnoreStore (文件名取自 `R.string.key_ignored_updates`), 忽略的插件更新; P3.3 登记为 NamedStores, 键声明在 UpdatesKeys |
| `lua-runtime-storage-v1-*` | 排除登记 `NamedStores.LUA_SCRIPT_STORAGES` (core/pref/keys/NamedStores.kt:140); 拼接 | engine/LuaHostStorage.kt:166 | Excluded |  | 脚本自己的数据 (附录 A.2) |
| `mcp_server_trust_confirmation` | 登记 `NamedStores.MCP_SERVER_TRUST_CONFIRMATION` (core/pref/keys/NamedStores.kt:113) | core/pref/keys/CredentialKeys.kt:43 | Setting | `credentials` | MCP Server 签名信任确认, sensitive; P3.4 登记为 NamedStores, 键声明在 CredentialKeys |
| `permission_wizard` | 登记 `NamedStores.PERMISSION_WIZARD` (core/pref/keys/NamedStores.kt:52) | core/pref/keys/WizardKeys.kt:57 | Setting | `wizards` | 权限向导自定义配置档; 其中 `_last_profile_id` 是状态 (附录 A), 整份随 wizards; P3.3 登记为 NamedStores, 键声明在 WizardKeys |
| `plugin_center_attention` | 登记 `NamedStores.PLUGIN_CENTER_ATTENTION` (core/pref/keys/NamedStores.kt:98) | core/plugin/center/PluginCenterAttentionStore.kt:118, core/pref/keys/PluginCenterKeys.kt:305, core/pref/keys/PluginCenterKeys.kt:310, core/pref/keys/PluginCenterKeys.kt:315 | State | `other` | 插件中心临时置顶 (P4.2); P3.1 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `plugin_center_authorization` | 登记 `NamedStores.PLUGIN_CENTER_AUTHORIZATION` (core/pref/keys/NamedStores.kt:109) | core/pref/keys/CredentialKeys.kt:37 | Setting | `credentials` | 插件授权记录, sensitive; P3.4 登记为 NamedStores, 键声明在 CredentialKeys |
| `plugin_center_enable_state` | 登记 `NamedStores.PLUGIN_CENTER_ENABLE_STATE` (core/pref/keys/NamedStores.kt:90) | core/plugin/center/PluginEnableStore.kt:67, core/pref/keys/PluginCenterKeys.kt:278, core/pref/keys/PluginCenterKeys.kt:284 | Setting | `plugin_enable_state` | 插件启用与挂起状态; P3.1 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `plugin_center_filter_state` | 登记 `NamedStores.PLUGIN_CENTER_FILTER_STATE` (core/pref/keys/NamedStores.kt:74) | core/plugin/center/PluginFilterStore.kt:79, core/pref/keys/PluginCenterKeys.kt:94, core/pref/keys/PluginCenterKeys.kt:334 | Setting | `plugin_center` | 筛选; P3.1 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `plugin_center_recent` | 登记 `NamedStores.PLUGIN_CENTER_RECENT` (core/pref/keys/NamedStores.kt:94) | core/pref/keys/PluginCenterKeys.kt:292, core/pref/keys/PluginCenterKeys.kt:298 | State | `other` | 最近安装 / 卸载时间 (P4.2); P3.1 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `plugin_center_sort_state` | 登记 `NamedStores.PLUGIN_CENTER_SORT_STATE` (core/pref/keys/NamedStores.kt:78) | core/plugin/center/PluginSortStore.kt:21, core/plugin/center/PluginSortStoreOfficial.kt:23, core/plugin/center/PluginSortStoreThirdParty.kt:23, core/pref/keys/PluginCenterKeys.kt:117 等 8 处 | Setting | `plugin_center` | 排序, PluginSortStore / Official / ThirdParty 三个类共用一份; P3.1 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `plugin_center_visibility_state` | 登记 `NamedStores.PLUGIN_CENTER_VISIBILITY_STATE` (core/pref/keys/NamedStores.kt:82) | core/plugin/center/PluginVisibilityStore.kt:23, core/pref/keys/PluginCenterKeys.kt:188, core/pref/keys/PluginCenterKeys.kt:194, core/pref/keys/PluginCenterKeys.kt:200 | Setting | `plugin_center` | 隐藏的插件; P3.1 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `plugin_install_wizard` | 登记 `NamedStores.PLUGIN_INSTALL_WIZARD` (core/pref/keys/NamedStores.kt:56) | core/pref/keys/WizardKeys.kt:66, core/pref/keys/WizardKeys.kt:75, core/pref/keys/WizardKeys.kt:84 | Setting | `wizards` | 插件安装向导自定义配置档; 其中 `_last_profile_id` 是状态, 整份随 wizards; P3.3 登记为 NamedStores, 键声明在 WizardKeys |
| `plugin_launcher_icons` | 登记 `NamedStores.PLUGIN_LAUNCHER_ICONS` (core/pref/keys/NamedStores.kt:86) | core/pref/keys/PluginCenterKeys.kt:215 | Setting | `plugin_center` | 插件启动图标显示状态 (PluginLauncherIconController); P3.1 登记为 NamedStores, 键声明在 PluginCenterKeys |
| `privileged_edit_backups` | 排除登记 `NamedStores.PRIVILEGED_EDIT_BACKUPS` (core/pref/keys/NamedStores.kt:144); 常量 PREFERENCES (model/explorer/PrivilegedBackupManager.kt:34) | model/explorer/PrivilegedBackupManager.kt:42, model/explorer/PrivilegedBackupManager.kt:48, model/explorer/PrivilegedBackupManager.kt:91, model/explorer/PrivilegedBackupManager.kt:94 等 5 处 | Excluded |  | 指向本机文件的操作日志 (附录 A.2) |
| `runtime_task_intents` | 排除登记 `NamedStores.RUNTIME_TASK_INTENTS` (core/pref/keys/NamedStores.kt:148); 字面量 | engine/RuntimeTasksBridge.kt:155 | Excluded |  | 运行时状态 (附录 A.2) |
| `three_stove_agent_trust_confirmation` | 登记 `NamedStores.THREE_STOVE_AGENT_TRUST_CONFIRMATION` (core/pref/keys/NamedStores.kt:117) | core/pref/keys/CredentialKeys.kt:49 | Setting | `credentials` | 3-Stove Agent 签名信任确认, sensitive; P3.4 登记为 NamedStores, 键声明在 CredentialKeys |

## 5. 绕过 `Pref` 读默认偏好的文件

- core/pref/Pref.kt:55

## 6. filesDir / noBackupFilesDir 下的 JSON 文件

启发式: 源文件在非注释行引用 `filesDir` / `noBackupFilesDir` 且含 `.json` 字面量; 目录由其他源文件传入的见 `MANUAL_JSON_FILE_SOURCES`. 与 ROADMAP 1.1 的 14 个对照见表后. "登记" 列为源文件认领的 `JsonFileStores` 排除存储 (P5.1; 名称为相对 `filesDir` 的路径, `../no_backup/` 即 `noBackupFilesDir`, `*` 为运行时拼出的部分).

| 源文件 | JSON 文件名 | 目录引用行 | 登记 (`JsonFileStores`) | kind | 分类 id | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| core/accessibility/codegen/store/ModelStore.kt | `registry.json` | 目录来自 ui/settings/codegen/CodegenModelStoreFactory.kt | `../no_backup/codegen_models*/registry.json` `CODEGEN_MODEL_REGISTRY` | Cache |  | 布局代码生成模型的登记表 (及 .bak), 目录由 CodegenModelStoreFactory 传入, 启发式不命中 (MANUAL_JSON_FILE_SOURCES); 模型文件可重新下载 (附录 A.2) |
| core/plugin/center/InstalledPluginMetadataStore.kt | `autojs6_installed_plugin_metadata_v1.json` | 163 | `plugin_center/autojs6_installed_plugin_metadata_v1.json` `INSTALLED_PLUGIN_METADATA` | Cache |  | 已安装插件元数据缓存 (附录 A.2) |
| core/plugin/center/PluginIndexRepository.kt | `autojs6_plugin_index_v11.json`, `compat-matrix.json`, `plugins.generated.json`, `plugins.official.generated.json` | 472 | `plugin_center/autojs6_plugin_index_v11.json` `PLUGIN_INDEX` | Cache |  | 插件索引缓存; 另外三个 .json 是索引仓库内的路径 |
| core/plugin/python/PythonPrivateExecutionSnapshot.kt | `.json` | 501 | `../no_backup/python-runtime-preview/host-capabilities-*.json` `PYTHON_EXECUTION_SNAPSHOTS` | Excluded |  | 运行时快照, 进程结束即失效 (附录 A.2) |
| engine/NodeLifecycleCheckpointStore.kt | `*.json` | 233,241 | `nodejs/checkpoints/*.json` `NODE_LIFECYCLE_CHECKPOINTS` | Excluded |  | 运行时检查点 (附录 A.2) |
| engine/NodeWorkManagerBridgeScheduler.kt | `*.json`, `.json` | 463 | `nodejs-work-manager/tasks/*.json` `NODE_WORK_MANAGER_TASKS` | Excluded |  | 运行时调度记录 (附录 A.2) |
| engine/RuntimeWebLocalDownloads.kt | `runtime-web-downloads/*.json` | 274 | `runtime-web-downloads/*.json` `RUNTIME_WEB_DOWNLOADS` | Cache |  | 可重新下载的资源 (附录 A.2) |
| model/explorer/PrivilegedBackupManager.kt | `*.json` | 79,287,315 | `privileged-edit-backups/*.json` `PRIVILEGED_EDIT_BACKUP_RECORDS` | Excluded |  | 指向本机文件的备份记录 (附录 A.2) |
| model/explorer/PrivilegedEditSessions.kt | `session.json` | 301,313,319,339 | `privileged-edit-sessions/*/session.json` `PRIVILEGED_EDIT_SESSIONS` | Excluded |  | 指向本机文件的编辑会话 (附录 A.2) |
| model/explorer/PrivilegedFileOperationJournal.kt | `*.json` | 19,60 | `privileged-file-operations/*.json` `PRIVILEGED_FILE_OPERATIONS` | Excluded |  | 指向本机文件的操作日志 (附录 A.2) |
| ui/edit/editor/ace/font/FontCatalogRepository.kt | `catalog-v1.json`, `catalog-v1.sig.json` | 93 | `../no_backup/ace_fonts/catalog-v1.json` `FONT_CATALOG`, `../no_backup/ace_fonts/catalog-v1.sig.json` `FONT_CATALOG_SIGNATURE` | Cache |  | 字体目录缓存与签名 (附录 A.2) |
| ui/edit/editor/ace/font/FontStore.kt | `installed.json` | 26 | `../no_backup/ace_fonts/fonts/*/installed.json` `INSTALLED_FONTS` | Cache |  | 已安装字体清单与字体文件 (附录 A.2) |
| ui/project/BuildActivity.java | `.json` | 2035 | 无 (不是存储) | Excluded |  | `.json` 是 SAF 导出配置档的建议文件名, 不是内部存储 |
| ui/settings/NodeDoctorReportGenerator.kt | `node-doctor-report.json` | 43 | `node-doctor/node-doctor-report.json` `NODE_DOCTOR_REPORT` | Excluded |  | 诊断报告输出文件 (供分享, 不读回), 不是存储 |

- ROADMAP 列出但启发式未命中: 无 (目录或 JSON 文件名在其他类中拼接, 归属以 ROADMAP 为准)
- 启发式命中但 ROADMAP 未列出: 无

## 7. 分类条目数 (回填 ROADMAP 附录 A)

Setting 与 State 计入条目数; 默认偏好中的字面量键 (含前缀键, 每组算 1 条) 单独计数, 命名存储每份算 1 条, 存储内部的键随整份计数. 数据分类 (D21) 不含键, 条目数在运行时按条 / 按库计算.

| 分类 id | `key_*` 键 | 字面量键 (默认偏好) | 命名存储 | 合计 | 部署项 |
| --- | --- | --- | --- | --- | --- |
| `appearance` | 9 | 3 | 0 | 12 |  |
| `general` | 6 | 0 | 0 | 6 |  |
| `explorer` | 6 | 4 | 0 | 10 | `CREATE_DIRECTORY` (`key_working_directory`) |
| `history` | 10 | 0 | 0 | 10 |  |
| `editor_general` | 1 | 1 | 0 | 2 |  |
| `editor_appearance` | 17 | 0 | 0 | 17 | `DOWNLOAD_EDITOR_FONT` (`key_ace_editor_font`) |
| `editor_log` | 6 | 0 | 0 | 6 |  |
| `editor_assistance` | 3 | 5 | 0 | 8 |  |
| `editor_smart_hint` | 7 | 0 | 0 | 7 |  |
| `editor_gestures` | 2 | 0 | 0 | 2 |  |
| `editor_gutter` | 5 | 0 | 0 | 5 |  |
| `editor_performance` | 2 | 0 | 0 | 2 |  |
| `editor_keyboard` | 0 | 0 | 2 | 2 |  |
| `timed_task` | 1 | 0 | 0 | 1 |  |
| `a11y` | 5 | 0 | 0 | 5 |  |
| `running` | 5 | 0 | 0 | 5 |  |
| `record` | 3 | 0 | 0 | 3 |  |
| `extensibility` | 2 | 1 | 0 | 3 |  |
| `updates` | 2 | 0 | 1 | 3 |  |
| `console` | 12 | 0 | 0 | 12 |  |
| `connection` | 7 | 0 | 0 | 7 |  |
| `layout_inspector` | 5 | 1 | 0 | 6 |  |
| `developer` | 7 | 4 | 0 | 11 |  |
| `plugin_center` | 3 | 6 | 5 | 14 |  |
| `plugin_enable_state` | 0 | 0 | 1 | 1 |  |
| `apk_build` | 0 | 0 | 1 | 1 |  |
| `wizards` | 0 | 0 | 2 | 2 |  |
| `data_shortcuts` | 0 | 0 | 0 | 数据 |  |
| `data_color_library` | 0 | 0 | 0 | 数据 |  |
| `credentials` | 0 | 0 | 5 | 5 |  |
| `other` | 41 | 33 | 3 | 77 |  |
