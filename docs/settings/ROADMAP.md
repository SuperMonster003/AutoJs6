# 设置层重构与分类导入导出路线图

更新日期: 2026-10-20

本文是 AutoJs6 宿主 "设置 / 配置 / 记忆型状态" 源码层深度重构, 以及建立在新层之上的 "分类导入导出设置" (对标 IntelliJ IDEA 的 Export Settings 对话框: 逐分类勾选, 全选 / 全不选 / 反选, 导出为 zip) 的可执行状态表. 它以 2026-10-20 维护者经四轮选择题确认的 18 项决策 (第 2 节) 为依据, 记录现状证据 (第 1 节), 派生技术决策 (第 3 节), 红线 (第 4 节), 阶段任务 (第 5 节) 与附录 (分类清单, 归档格式, 待决事项, 冒烟清单, 文件地图, 基线数据).

使用方式:

1. 每次会话开始时, 从第 5 节选取未完成条目, 按阶段顺序推进; 条目完成后勾选 `[x]` 并追加证据 (提交 hash / 测试类名 / 设备型号与 API / 计数数值). 证据等级见附录 E.
2. 第 2 节固定决策不再重新讨论; 第 3 节派生决策可在标注的推翻点之前推翻, 之后视同固定; 附录 C 待决事项在进入对应阶段前由维护者拍板, 结果回填到第 2 节或第 3 节.
3. 工程约定以仓库 `AGENTS.md` 与 `../COMMON_AGENTS.md` 为准 (ASCII 标点, 字符串资源 10 种语言, 构建号, 共享工作区). 本文只记录 "改什么" 与证据.
4. 涉及 feature / improvement 的阶段 (P6 起) 须同步 `.changelog/lang_*.json` 并重新生成 markdown; 脚本 API (P10) 须同步文档仓库与 d.ts 仓库 (见 `AGENTS.md` > 关联项目).
5. 宿主仓库只做本地提交, 推送等维护者逐次指示.
6. 本文由一个调度会话跟踪: 它按阶段把条目分配给子会话 (每个子会话只做分配到的条目), 子会话完成后在本文勾选并附证据, 调度会话汇总进度, 更新附录 C 的拍板结果, 再分配下一批; 调度会话在全部条目完成前不结束.

---

## 1. 现状与证据 (基线 `master@4a16be478`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=3940`)

### 1.1 存储形态清单

| 形态 | 数量 | 代表 | 备注 |
| --- | --- | --- | --- |
| 默认 SharedPreferences (经 `core/pref/Pref`) | `key_*` 字符串资源 265 个, 其中 105 个出现在 5 个设置页 XML, 162 个只在代码中使用 | `Pref.kt` (476 行), `fragment_preferences.xml` (39 键), `fragment_code_editor_settings.xml` (40), `fragment_developer_options.xml` (11), `fragment_trash_and_version_history_settings.xml` (9), `fragment_plugin_center_preferences.xml` (5) | 122 个文件经 `Pref` 读写; 162 个代码键混合了设置值常量 (`key_night_mode_follow_system` 这类枚举值), 记忆型状态 (`key_last_*`, `key_dialog_*`, `*_collapsed`, `*_level`), 历史列表 (`key_working_directory_history`), 缓存 (`key_root_manager_apps_cache`) 与 Bundle 参数名 (`key_pref_bundle_*`, 不是偏好键) |
| 绕过 `Pref` 直接读默认 SharedPreferences | 8 个文件 | `engine/RootAutomatorEngine.kt`, `runtime/api/Shell.java`, `ui/common/NotAskAgainDialog.java`, `ui/edit/FindOrReplaceDialogBuilder.kt`, `ui/edit/debug/CodeEvaluateDialogBuilder.java`, `ui/settings/codegen/CodegenModelStoreFactory.kt`, `engine/NodeJsRuntimePluginBridge.kt`, `inrt/Pref.kt` | `inrt/Pref` (97 行) 是打包应用运行时的另一套 `Pref`, 与 `core/pref/Pref` 无共享代码 |
| 页面级 Prefs 封装 (手写默认值) | 若干 | `storage/history/HistoryPrefs`, `ui/edit/editor/engine/EditorEnginePreferences`, `ui/settings/EditorThresholdSettings`, `ui/settings/RetentionLimitSettings` | 默认值分别写在 `R.integer` / `R.bool` / 伴生常量 / 调用处字面量, 同一键的默认值可能出现在多处 (`Pref.screenCaptureRequestDelay` 读 `R.integer`, `HistoryPrefs` 读伴生常量) |
| 命名 SharedPreferences | 约 27 份, 34 个文件调用 `getSharedPreferences(` | 插件中心 13 个 `*Store` (`plugin_center_sort_state` 被 3 个类共用), `editor_symbols_config`, `FunctionsKeyboardHelper`, `ace_editor_font_ui_preferences`, `ace_action_history`, `build_apk_config_profiles`, `permission_wizard` / `plugin_install_wizard` (`WizardProfileStore` 两实例), `jvm_source_provider_selection`, `github_auth`, `plugin_center_authorization`, `mcp_server_trust_confirmation`, `three_stove_agent_trust_confirmation`, `bun_host_capability_grants`, `explorer_playback_history`, `privileged_edit_backups`, `device_id_prefs`, `runtime_task_intents`, `ScriptWidgets`, `autojs.localstorage.*`, `lua-runtime-storage-v1-*`, `UpdateIgnoreStore` (文件名取自 `R.string.key_ignored_updates`) | 每个类各自封装 `sp(context)`, 没有共同基类或接口 |
| 文件型 JSON 存储 (`filesDir`) | 14 个 | `ui/edit/editor/ace/font/FontStore`, `FontCatalogRepository`, `core/accessibility/codegen/store/ModelStore`, `core/plugin/center/InstalledPluginMetadataStore`, `PluginIndexRepository`, `engine/NodeLifecycleCheckpointStore`, `NodeWorkManagerBridgeScheduler`, `RuntimeWebLocalDownloads`, `core/plugin/python/PythonPrivateExecutionSnapshot`, `model/explorer/PrivilegedEditSessions` / `PrivilegedFileOperationJournal` / `PrivilegedBackupManager`, `ui/project/BuildActivity`, `ui/settings/NodeDoctorReportGenerator` | 大多是缓存, 运行时快照或日志, 不是用户设置 |
| 结构化数据库 | 5 个 | `storage/database/TimedTaskDatabase` / `IntentTaskDatabase` (SQLite), `theme/app/ColorHistoryDatabase` (Room), `storage/history/HistoryDatabase` (Room), `apkbuilder/keystore/KeyStoreDatabase` (Room) | 不在本期范围 (D1), 见附录 C Q5 / Q6 |

### 1.2 零散的具体表现

- 序列化方式至少 4 种并存: `org.json.JSONObject` (11 个存储类), Gson (`Pref` 内的 `LinkedHashSet` / `LinkedList` 序列化), 逗号拼接字符串 (5 处), `putStringSet` (7 处).
- 名为 `*Store` / `*Prefs` / `*Preferences` / `*Settings` / `*Config*` 的类约 80 个, 散落在 40 个目录, 其中 `core/plugin/center` 13 个, 其余目录 1-5 个; 同名后缀既用于用户设置 (`SymbolsConfigStore`), 也用于缓存 (`InstalledPluginMetadataStore`) 与运行时日志 (`PrivilegedFileOperationJournal`).
- 用户意图的 "设置" 与应用自身的 "记忆" 共用一个默认 SharedPreferences 且没有标记: `NotAskAgainDialog` 以调用方传入的任意键名写 `Boolean`; `key_last_updates_checked` 等时间戳与 `key_night_mode` 并列.
- 没有任何地方能回答 "某个键属于哪个设置页的哪个分类, 默认值是什么, 改了之后要不要重启", 这正是导入导出需要的元数据.

### 1.3 可复用先例

| 关注点 | 先例 | 用法 |
| --- | --- | --- |
| zip + SAF 导出 / 导入 | `ui/shortcut/ShortcutBackupActivity.kt` (`ShortcutBackupFragment`: `ActivityResultContracts.CreateDocument("application/zip")` / `OpenDocument()`, 页面以 `PreferenceFragmentCompat` 动作条目组织, 入口是 `fragment_preferences.xml` "配置" 分类的 `ShortcutBackupPreference`) | 导入导出页面 (T10) 照此结构, 入口放在它旁边 (D4) |
| JSON 导入前预览与错误消息 | `theme/app/ColorLibraryExchange.kt` (`showImportPreview`, `ColorLibraryJsonException.reason` 映射到资源) | 导入流程 (P7.5) 的预览与错误提示 |
| 纯对象可测策略 | `storage/history/HistoryRetentionPolicy`, `TrashRestoreNaming` (回收站 roadmap D5: JVM 单测只有 JUnit4, 无 Robolectric) | 导入计划器, 编解码, 注册表描述全部做成只依赖原始类型的对象 (R3) |
| 周期任务 | `storage/history/HistoryCleanupScheduler.schedulePeriodicCleanup` (`PeriodicWorkRequestBuilder<HistoryCleanupWorker>(1, TimeUnit.DAYS)`) | 自动备份 (P9) |
| 重启 | `util/IntentUtils.restart(context, beforeExit, scriptsAfterRestart)` | 结果对话框 "立即重启" (D10) |
| 热生效入口 | `ThemeColorManager.setThemeColor`, `ViewUtils.setDefaultNightMode`, `LocaleUtils.setLocale` / `setFollowSystem`, `LauncherIconPreference` 的组件切换逻辑 | 生效表 (T12) 调用, 不复制逻辑 |
| 资源一致性测试读源码文件 | `runtime/api/modules/ScriptMessagesTest.developerOptionStringsExistInEveryLanguage` | 守卫测试 (T5) 解析 `strings_donottranslate.xml` 与源码 |

---

## 2. 固定决策 (2026-10-20 四轮选择题)

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 第一期范围 = 默认 SharedPreferences + 命名 SharedPreferences | 不含定时任务 / Intent 任务 / 颜色库 / 快捷方式 / 版本历史 / 签名密钥库等结构化数据 (第二期候选见附录 C Q5 / Q6) |
| D2 | 敏感项单独分类 "凭据与授权", 默认不勾选 | 勾选时弹一次 "将以明文写入文件" 警告; 文件内明文; 成员见附录 A (`credentials`) |
| D3 | 官方插件自身设置第一期不含, 预留契约 | 归档结构预留 `plugins/<packageName>/` 目录与 manifest `plugins` 字段; 注册表预留 `PluginSettingsProvider` 扩展点; 第二期在 `common-plugin-api` 定义导出 / 导入契约 (形状见附录 C Q7) |
| D4 | 入口 = 设置 > 配置 > "导入与导出设置" 独立页面 | 页面内 "导出...", "导入...", "恢复默认...", "撤销上次导入" 动作与 "自动备份到工作目录" 开关; 各动作弹 IDEA 风格分类勾选对话框 |
| D5 | 文件格式 = zip, `manifest.json` + `categories/<id>.json` | 默认文件名 `AutoJs6-settings-<yyyyMMdd-HHmmss>.zip`; 格式见附录 B |
| D6 | 分类粒度 = 按设置页内分类, 约 28 项 | 每项显示条目数; 清单见附录 A, 由注册表派生而非手工维护 |
| D7 | 未登记的键归 "其他 (内部状态)", 默认不勾选 | 永久排除清单见附录 A.2; 守卫测试保证正常情况下 "其他" 只含登记过的记忆型状态 |
| D8 | 导入 = 按分类整体覆盖 + 自动备份可撤销 | 勾选分类内文件没有的键恢复默认值, 未勾选分类不动; 导入前自动把全部设置备份到内部目录 (保留 5 份), 页面提供 "撤销上次导入" |
| D9 | 宽松导入 + 跳过计数 | manifest 带 `formatVersion` 与宿主 `versionCode`; 未知键 / 类型不符 / 枚举值无效 / 目标不存在 (如未安装的插件) 各自计数并在结果中展示; 文件 `formatVersion` 高于本机时警告后允许; 预留键改名迁移表 (第一期为空) |
| D10 | 生效 = 能热生效的立即应用, 其余提示重启 | 注册表按键标记 `NONE` / `APPLY` / `RESTART`; `APPLY` 走现有入口 (1.3); 结果对话框在有 `RESTART` 键变化时提供 "立即重启" |
| D11 | 附带: 按分类恢复默认设置 | 同一注册表, 勾选分类后清除对应键 (命名存储整份清空), 先自动备份 |
| D12 | 附带: 脚本 API 只读导出 | `settings` 模块增加导出函数 (T15), 不开放导入; 同步文档与 d.ts |
| D13 | 附带: 定期自动备份到工作目录 | 页面开关, 每日一份, 保留 7 份, 不含凭据分类; 复用 WorkManager 周期任务做法 |
| D14 | 验收 = JVM 单测 + 真机冒烟脚本 | 编解码 / 注册表 / 守卫 / 导入计划全部为纯对象 JUnit4; 真机用 `docs/dev/smoke/settings-transfer/` 脚本做 "改设置 -> 导出 -> 清除 -> 导入 -> 核对" 闭环, 至少 2 台真机 + 1 个 API 24 AVD |
| D15 | 重构为前置阶段, 导入导出建在新层上 | P1-P5 先于 P6; 分类注册表是声明层的副产品, 不单独维护键清单 |
| D16 | 目标形态 = 声明式 `SettingKey` + 统一 `SettingsStore` | 不引入 Jetpack DataStore; 键名与存储文件名不变, 已安装用户零迁移; 序列化收敛到一套 JSON 编解码 |
| D17 | 迁移方式 = 平行新建 + 逐领域迁移 + 旧入口转发 | 新层先与旧代码共存, 按领域一次迁一片, 旧 `Pref.getXxx` / 页面级封装标 `@Deprecated` 转发到新层, 每个阶段编译通过且可独立提交, 最后阶段 (P12) 删除旧入口 |
| D18 | 重构边界 = 默认偏好与页面级封装 + 命名存储 + 记忆型状态 + 文件型 JSON 存储 | 记忆型状态单独声明为 `StateKey`; 文件型 JSON 存储只做归属登记与编解码统一, 不改文件格式, 不改目录 |

---

## 3. 派生技术决策

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 键声明模型 `core/pref/SettingKey<T>`: 密封类型 `Bool` / `Int` / `Long` / `Float` / `Str` / `StrSet` / `Enum<E>` (值为资源键字符串, 携带候选项) / `Json<T>` (经 `SettingsJson` 编解码); 字段: `keyRes` (由 `StringUtils.key` 惰性解析为键名), `store` (`StoreId`, 默认为 `Default`), `category`, `default` (常量或读资源的 lambda), `effect` (`NONE` / `APPLY` / `RESTART`), `sensitive`. 声明按领域放在 `core/pref/keys/<Domain>Keys.kt` 的 `object` 中 | P1.1 结束 |
| T2 | `StateKey<T>` (记忆型状态) 与 `CacheKey<T>` (缓存) 同模型: `StateKey` 固定归 `other` 分类, 默认不勾选, 不参与恢复默认; `CacheKey` 不可导出, 登记只为守卫测试; 设置值常量 (`key_night_mode_follow_system` 等) 登记为 `ValueConstant`, Bundle 参数名登记为 `Excluded` | P1.1 结束 |
| T3 | `SettingsStore` 接口 (`get` / `put` / `remove` / `contains` / `all` / `edit {}` / `listen`), 实现 `SharedPreferencesStore(name?)` 与 `JsonFileStore(file)`; 命名存储以 `StoreId(name, backend)` 在 `SettingsStores` 登记; 测试用 `InMemorySettingsStore` | P1.2 结束 |
| T4 | `SettingsRegistry` 聚合全部领域 `object`, 提供 `describe()` 输出纯数据 `SettingDescriptor(name, type, store, category, defaultText, effect, kind)`, 分类列表 `SettingCategory(id, titleRes, order, defaultSelected, sensitive)`; 键在声明处写分类, 注册表反向索引; 导入导出, 恢复默认, 守卫测试, 文档生成全部只消费 `describe()` | P1.3 结束 |
| T5 | 守卫测试 (JVM): (a) `strings_donottranslate.xml` 中每个 `key_*` 都被登记为 Setting / State / Cache / ValueConstant / Excluded 之一; (b) 源码中 `getSharedPreferences(` 的字面量与常量文件名都在 `SettingsStores` 或排除清单; (c) 每个键恰好一个分类, 每个分类至少一个键, `sensitive` 键只在 `credentials`; (d) 旧入口删除后 (P12) 源码中不再出现 `PreferenceManager.getDefaultSharedPreferences` (白名单: `SharedPreferencesStore`, `PreferenceFragmentCompat` 子类) | 不可推翻 |
| T6 | 快照 `SettingsSnapshot` = 分类 -> 存储 -> 键 -> `TypedValue(type, value)`; 类型标签 `bool` / `int` / `long` / `float` / `string` / `stringSet` / `json`; 编解码 `SettingsSnapshotCodec` 基于 `org.json`, 不用 Gson; "其他" 分类的未登记键按 `SharedPreferences.getAll()` 的运行时类型打标签 | P6.1 结束 |
| T7 | 归档 `SettingsArchiveWriter` / `SettingsArchiveReader` 用 `java.util.zip`, 读取时先只解 `manifest.json` 列出分类与条目数, 用户勾选后再解对应 `categories/<id>.json`; 单个 zip 上限 16 MB, 超出拒绝 | P6.1 结束 |
| T8 | 导入计划 `SettingsImportPlanner` 为纯函数: 输入注册表描述, 归档快照, 勾选分类, 已安装插件包名集合; 输出 `ImportPlan` (`puts`, `removes`, `skips` 按原因 `UNKNOWN_KEY` / `TYPE_MISMATCH` / `INVALID_VALUE` / `TARGET_MISSING` / `NEWER_FORMAT`); 应用 `SettingsImportApplier` 对每个存储一次 `commit`, 之后 `SettingsEffects.run(changedKeys)` 按 `effect` 分组执行 | P7.2 结束 |
| T9 | 备份库 `SettingsBackupVault` 位于 `filesDir/settings-backups/`: `auto-import-<ts>.zip` 与 `auto-reset-<ts>.zip` 共保留 5 份 (含凭据与其他分类, 私有目录); `last-operation.json` 记录触及分类与时间; "撤销上次导入" = 以最近备份对触及分类再做一次导入 (同样走 T8), 撤销本身不再备份 | P7.3 结束 |
| T10 | 页面 `ui/settings/transfer/SettingsTransferActivity` + `SettingsTransferFragment` (`PreferenceFragmentCompat`, 同 `ShortcutBackupFragment` 写法); 对话框 `SettingsCategoryPickerDialog` (MaterialDialog 自定义视图: 复选列表, 每项副标题 "N 项", 底部 "全选" / "全不选" / "反选", 与 IDEA 一致; 勾选 `credentials` 时弹 D2 警告; 导出 / 导入 / 恢复默认共用, 标题与确认按钮不同); 结果对话框 `SettingsTransferResultDialog` 列出写入 / 恢复默认 / 跳过 (按原因) 计数与 "立即重启" | P6.2 结束 |
| T11 | SAF: `CreateDocument("application/zip")` / `OpenDocument()` (MIME `application/zip`, `application/octet-stream`); 默认文件名 D5; 导出成功后 toast 显示文件名 | 不可推翻 |
| T12 | 生效表 `SettingsEffects`: `APPLY` 键各自绑定现有入口 (主题色 -> `ThemeColorManager.setThemeColor`; 夜间模式 -> `ViewUtils.setDefaultNightMode`; 语言 -> `LocaleUtils.setLocale` / `setFollowSystem`; 启动图标 -> `LauncherIconPreference` 抽出的组件切换逻辑; 定时任务后端 -> 现有重排入口); 未绑定入口的 `APPLY` 键在守卫测试中报错 | P7.4 结束 |
| T13 | `inrt/Pref` 不并入 `core/pref/Pref`, 只在 P2.6 把底层换成 `SharedPreferencesStore`; 打包应用无导入导出界面 | 附录 C Q3 |
| T14 | 旧入口转发期间 (P2-P11) 不得新增旧式访问 (`Pref.getXxx` 新成员, 新的 `getSharedPreferences` 调用); 新代码直接用 `SettingKey` | 不可推翻 |
| T15 | 脚本 API `settings.export(path?, categories?)`: 返回写入的绝对路径; `path` 省略时写到工作目录 `backups/settings/`; `categories` 省略时为默认勾选集合; 传入 `credentials` 时忽略并在返回对象的 `skipped` 中标注 (脚本不得导出凭据); 放在现有 `runtime/api/augment/settings/Settings.kt` | P10.1 结束 |
| T16 | 自动备份 `SettingsAutoBackupWorker`: `PeriodicWorkRequest` 1 天, 目录 `<工作目录>/backups/settings/`, 文件名 `AutoJs6-settings-auto-<ts>.zip`, 保留 7 份, 分类 = 默认勾选集合 (不含 `credentials` 与 `other`); 开关键本身属于 `general` 分类 | 附录 C Q9 |
| T17 | 不移动既有文件的包位置 (与并行会话冲突最小); 重构只改类的内部实现与基类, 命名规则: 命名存储类保留 `*Store` 后缀, 页面级 `*Prefs` / `*Preferences` / `*Settings` 封装在迁移后删除, 其成员并入对应 `<Domain>Keys` | 不可推翻 |

---

## 4. 红线 (所有阶段必须遵守)

- R1 零迁移 (D16): 任何键名, 命名存储文件名, 文件型存储的路径与格式都不得改变; 需要改名时只能走 D9 的迁移表并保留旧键读取.
- R2 行为保持: P1-P5 不改变任何设置的默认值, 类型与读写时机; P0.4 记录的默认值表是核对依据, 发现同一键多处默认值不一致时在附录 C 登记, 由维护者拍板后才统一.
- R3 可测逻辑与 Android 解耦 (回收站 roadmap D5 同款): 编解码, 注册表描述, 导入计划, 备份保留策略, 守卫逻辑只依赖原始类型与 `org.json`; JVM 单测只有 JUnit4.
- R4 共享工作区: 新建 Kotlin 文件在编译通过前不放入 `app/src`; 放回前通知并立即编译; 每个阶段结束时 `:app:assembleAppDebug` 与相关单测通过.
- R5 文案: 代码注释, 文档, 字符串资源只用 ASCII 标点; 新字符串资源 10 种语言同步, 按 `name` 升序, 默认与 `values-en` 一致.
- R6 敏感项: `sensitive` 键的值不得出现在日志, toast, 崩溃信息与自动备份 (D13) 中; 内部备份 (T9) 允许包含.
- R7 不引入 DataStore, 不改 `inrt` 行为, 不把 Room / SQLite 数据纳入第一期.

---

## 5. 阶段任务

### P0 安全网与清单

- [x] P0.1 (文档) 本文: 现状证据, 18 项决策, 派生决策, 红线, 阶段任务, 附录. 证据: 本文件.
- [ ] P0.2 (工具) `tools/settings/inventory.py`: 扫描 `strings_donottranslate.xml` 的 `key_*`, 5 个设置页 XML 的出现位置, 源码引用位置 (文件与行), `getSharedPreferences(` 的文件名字面量与常量, `filesDir` 下的 JSON 文件名; 输出 `tools/settings/baseline/inventory-<date>.json` 与可读的 `docs/settings/inventory.md`. 证据: 生成文件与本条提交.
- [ ] P0.3 (文档) 分类初稿: 对 265 个 `key_*` 与约 27 个命名存储逐一标注 kind (Setting / State / Cache / ValueConstant / Excluded) 与分类 id, 回填附录 A 的条目数; 无法判断的进附录 C. 证据: 附录 A 更新, `inventory.md` 的 kind 列无空白.
- [ ] P0.4 (文档) 默认值表: 每个 Setting 的默认值来源 (`R.bool` / `R.integer` / `R.string` / 伴生常量 / 调用处字面量) 与取值, 同一键多处不一致的列入附录 C. 证据: `docs/settings/defaults.md`.
- [ ] P0.5 (测试) JVM 夹具 `InMemorySettingsStore` 与 `SettingDescriptor` 构造辅助, 放在 `app/src/test/java/org/autojs/autojs/core/pref/`. 证据: 夹具自测通过.

### P1 新基座 (`core/pref`)

- [ ] P1.1 (宿主) `SettingKey` / `StateKey` / `CacheKey` / `SettingCategory` / `SettingEffect` / `SettingsJson` (T1 / T2), 不接入任何调用方. 证据: `SettingKeyTest` (类型, 默认值解析, Enum 候选校验, Json 往返).
- [ ] P1.2 (宿主) `SettingsStore` / `SharedPreferencesStore` / `JsonFileStore` / `StoreId` / `SettingsStores` (T3). 证据: `SharedPreferencesStore` 在真机上对同一文件与旧 `Pref` 读写互通 (E3, 记录机型); `JsonFileStoreTest` (JVM, 临时目录).
- [ ] P1.3 (宿主) `SettingsRegistry` 骨架与 `describe()` (T4); 守卫测试 (T5 a / b / c) 以 P0.3 的分类初稿为初始允许清单, 未迁移的键先以 "待迁移" 登记, 测试只要求不出现未登记项. 证据: `SettingsRegistryGuardTest` 通过且列出待迁移计数.
- [ ] P1.4 (宿主) `core/pref/Pref` 的通用读写 (`getString` / `putBoolean` / `getStringList` 等) 改为经 `SettingsStores.default` (行为不变, Gson 列表序列化改为 `SettingsJson` 并保证旧值可读). 证据: 真机旧版本升级后 `key_working_directory_history` 等列表值仍可读 (E3).

### P2 默认偏好迁移 (每条: 声明键 -> 替换读写 -> 旧 getter `@Deprecated` 转发 -> 单测 -> 守卫计数下降)

- [ ] P2.1 外观与配置: `AppearanceKeys` (语言, 夜间模式与自动夜间, 主题色三项, 前台常亮), `GeneralKeys` (文档来源, 重启策略与计划重启后端 / 延迟, 启动图标, 启动器快捷方式, 自动备份开关占位); 迁移 `Language`, `ThemeColorManager`, `ViewUtils` 夜间模式, `LauncherIconPreference`. 证据: 单测 + E3.
- [ ] P2.2 文件管理器与回收站: `ExplorerKeys` (工作目录, 隐藏文件, 扩展名, 回放历史开关, 新文件默认前缀, 批量重命名历史保留), `HistoryKeys` (9 个保留策略键, 永久删除同时清历史, 恢复不自动落盘); `HistoryPrefs` / `RetentionLimitSettings` 并入. 证据: `storage/history` 既有单测仍过 + E3.
- [ ] P2.3 代码编辑器 8 个分类: `EditorKeys` (常规, 外观, 日志, 辅助, 智能提示, 操作手势, 边栏标记, 性能, 含 `key_editor_log_panel_*` 等代码键); `EditorEnginePreferences` / `EditorThresholdSettings` / `AceEditorFontUiPreferences` (默认偏好部分) 并入. 证据: 单测 + E3 (两种编辑器引擎各开一次).
- [ ] P2.4 定时任务, 无障碍, 脚本运行, 录制, 扩展性, 更新: `TimedTaskKeys`, `A11yKeys`, `RunningKeys` (含 `key_root_manager_apps_custom`), `RecordKeys`, `ExtensibilityKeys`, `UpdatesKeys` (自动检查, 忽略本机版本检查; 时间戳归 P4). 证据: 单测 + E3.
- [ ] P2.5 日志与控制台, 连接, 布局分析器, 开发者选项: `ConsoleKeys` (`key_log_activity_*`, `key_console_element_*`, `key_global_console_element_*`, `key_floating_console_*`), `ConnectionKeys` (服务端地址, 客户端 / 服务端模式各开关), `LayoutInspectorKeys` (筛选配置档与选中项, 节点复制模式, 查询模式, 颜色选择旧布局, 代码生成当前模型), `DeveloperKeys` (新脚本模块层, 调试打印与模块列表, Node.js inspector). 证据: 单测 + E3.
- [ ] P2.6 收口: 8 个绕过文件改为 `SettingKey` (其中 `NotAskAgainDialog` 归 P4.1); `inrt/Pref` 底层换 `SharedPreferencesStore` (T13); 守卫测试 "待迁移" 计数为 0 (默认偏好部分). 证据: `SettingsRegistryGuardTest` 输出.

### P3 命名存储迁移 (每条: 登记 `StoreId` -> 类内部改为 `SettingsStore` + `Json` 键 -> 单测)

- [ ] P3.1 插件中心 13 个 `*Store`: `plugin_center_sort_state` 三个类共用一份存储的情况在登记时写明; `PluginMountStore` 的默认偏好前缀键登记为 `Json` 键集合; 启用状态 (`plugin_center_enable_state`) 单独分类 `plugin_enable_state`. 证据: 插件中心既有单测仍过 + E3 (排序 / 隐藏 / 挂载各改一项后重启核对).
- [ ] P3.2 编辑器键盘与字体: `editor_symbols_config`, `FunctionsKeyboardHelper`, `ace_editor_font_ui_preferences`, `ace_action_history` (State). 证据: 单测 + E3.
- [ ] P3.3 配置档类: `build_apk_config_profiles` (`BuildConfigStore`), `permission_wizard` / `plugin_install_wizard` (`WizardProfileStore`, 自定义配置档为 Setting, 最近使用的配置档 id 为 State), `jvm_source_provider_selection`, `UpdateIgnoreStore`. 证据: 单测 + E3.
- [ ] P3.4 凭据与授权: `github_auth`, `plugin_center_authorization`, `mcp_server_trust_confirmation`, `three_stove_agent_trust_confirmation`, `bun_host_capability_grants`, 全部 `sensitive = true`, 分类 `credentials`. 证据: 守卫 (c) 通过, R6 检查 (grep 日志语句).
- [ ] P3.5 永久排除存储登记 (附录 A.2): `device_id_prefs`, `autojs.localstorage.*`, `lua-runtime-storage-v1-*`, `privileged_edit_backups`, `runtime_task_intents`, `ScriptWidgets`, `explorer_playback_history` 只登记 `StoreId(excluded = true)`, 不改代码. 证据: 守卫 (b) 通过.

### P4 记忆型状态 (`StateKey`)

- [ ] P4.1 `DialogStateKeys`: `NotAskAgainDialog` 改为接受 `StateKey<Boolean>` (保留接受字符串键的 `@Deprecated` 构造直到调用方全部迁移), 现有 `key_dialog_*` 逐个声明. 证据: 单测 + 守卫.
- [ ] P4.2 其余状态: `key_last_*` 四个时间戳, `key_setup_wizard_welcome_done`, `key_post_notifications_permission_request_attempted`, `key_*_socket_normally_closed` / `key_mcp_server_normally_closed`, `key_floating_menu_shown`, `*_manager_*_level` / `*_collapsed`, `*_introduced`, `key_working_directory_initialized` / `_history`, `key_pc_server_address_history`, `key_layout_inspector_query_history`, `plugin_center_recent`, `plugin_center_attention`, `PluginUpdateCheckStore`, `key_plugin_center_last_online_check_ts`, `key_keep_screen_on_when_in_foreground_last_enabled`. 证据: 守卫 "待迁移" 计数为 0.
- [ ] P4.3 "其他" 分类的运行时兜底: 导出时对默认存储 `getAll()` 与注册表求差, 未登记键仍进 `other` 并以运行时类型打标签 (T6), 单测覆盖. 证据: `SettingsSnapshotTest`.

### P5 文件型 JSON 存储归属整理 (D18 / T17)

- [ ] P5.1 14 个文件型存储逐一登记为 `JsonFileStore` (用户设置: 无, 预期全部为 Cache 或运行时快照) 或 `CacheKey`; 内部的 `JSONObject` 手写编解码改用 `SettingsJson` 的辅助函数 (不改文件格式). 证据: 各自既有单测仍过; 守卫 (b) 对 `filesDir` JSON 文件名的检查通过.
- [ ] P5.2 收口: 删除已空的 `*Prefs` / `*Preferences` / `*Settings` 封装 (T17), 全量 `:app:testAppDebugUnitTest` 通过. 证据: 用例数与失败数.

### P6 导出 (D4 / D5 / D6 / D2)

- [ ] P6.1 (宿主) `SettingsSnapshot` / `SettingsSnapshotCodec` / `SettingsArchiveWriter` (T6 / T7) 与 manifest (附录 B). 证据: `SettingsSnapshotCodecTest` (七种类型往返, 其他分类标签), `SettingsArchiveTest` (JVM 临时目录写读往返).
- [ ] P6.2 (宿主) 页面与入口: `SettingsTransferActivity` / `Fragment` (T10), `fragment_preferences.xml` "配置" 分类新增 `SettingsTransferPreference` (放在 `ShortcutBackupPreference` 之后), Manifest 注册, 字符串资源 10 种语言 (标题 "导入与导出设置", 动作, 结果, 警告). 证据: E3 截图核对 (英文与简体中文各一次).
- [ ] P6.3 (宿主) `SettingsCategoryPickerDialog` (T10): 列表项 = 附录 A 的分类 (按 order), 副标题条目数来自当前快照, 全选 / 全不选 / 反选, `credentials` 勾选警告 (D2), 记住上次勾选 (本身是 `StateKey`). 证据: E3.
- [ ] P6.4 (宿主) 导出流程: 勾选 -> `CreateDocument` -> 写 zip -> toast; 取消与写入失败的提示; 大文件上限 (T7). 证据: E3 (两台), 导出文件用 `tools/settings/archive_inspect.py` 校验 manifest 与分类数.

### P7 导入 (D8 / D9 / D10)

- [ ] P7.1 (宿主) `SettingsArchiveReader` + manifest 校验 (格式名, `formatVersion`, 宿主包名) + 迁移表骨架 `SettingsKeyMigrations` (空). 证据: `SettingsArchiveReaderTest` (损坏 zip, 缺 manifest, 更高 formatVersion 的警告标记).
- [ ] P7.2 (宿主) `SettingsImportPlanner` (T8). 证据: `SettingsImportPlannerTest` (覆盖语义: 文件缺键 -> remove; 未勾选分类不动; 四种跳过原因; `plugin_enable_state` 对未安装包名 -> `TARGET_MISSING`; `other` 分类只写登记过的 State 键).
- [ ] P7.3 (宿主) `SettingsBackupVault` (T9): 自动备份, 保留 5 份, `last-operation.json`, 撤销计划. 证据: `SettingsBackupVaultTest` (保留策略, 撤销只触及记录过的分类).
- [ ] P7.4 (宿主) `SettingsImportApplier` + `SettingsEffects` (T8 / T12): 每存储一次 commit, 生效表绑定, 守卫 "APPLY 键必须有入口". 证据: 单测 + E3 (改主题色 / 夜间模式 / 语言后导入, 不重启即生效; 改启动图标后导入, 图标切换).
- [ ] P7.5 (宿主) 导入 UI 流程: `OpenDocument` -> 读 manifest -> 勾选对话框 (只列文件中存在的分类, 显示文件来源宿主版本与设备) -> 确认 -> 自动备份 -> 应用 -> 结果对话框 (T10, "立即重启"); "撤销上次导入" 动作. 证据: E3 (两台 + API 24 AVD), 冒烟清单附录 D 第 1-5 项.

### P8 恢复默认 (D11)

- [ ] P8.1 (宿主) "恢复默认..." 动作: 同一勾选对话框 (不列 `other` 与 `credentials` 之外的敏感项? 否, 列出全部但 `other` 不可选), 先 `auto-reset` 备份, 清除勾选分类的键 (命名存储整份 `clear`), 跑生效表. 证据: 单测 (计划器复用, `removes` 全集) + E3 (附录 D 第 6 项).

### P9 自动备份 (D13 / T16)

- [ ] P9.1 (宿主) `SettingsAutoBackupWorker` + 开关 (`GeneralKeys.autoBackupEnabled`) + 保留 7 份 + 工作目录不可写时的通知. 证据: 单测 (保留策略纯函数) + E3 (手动触发一次 `WorkManager` 任务, 核对文件与份数).

### P10 脚本 API 只读导出 (D12 / T15)

- [ ] P10.1 (宿主) `settings.export(path?, categories?)` 与 `settings.categories()` (返回分类 id 与条目数). 证据: 单测 (参数解析) + E3 (脚本导出一次, `archive_inspect.py` 校验).
- [ ] P10.2 (关联仓库) 文档仓库 `settings` 页面增补, d.ts 两个仓库同步 (`AGENTS.md` > 关联项目), `.changelog` feature 条目. 证据: 三个仓库的提交 hash.

### P11 插件契约预留 (D3)

- [ ] P11.1 (宿主) manifest `plugins` 字段与 `plugins/<packageName>/` 目录读写能力 (空集合), `PluginSettingsProvider` 接口与注册表空实现, 导入时遇到未知插件目录计入 `TARGET_MISSING`. 证据: 单测.
- [ ] P11.2 (文档) 第二期契约形状写入附录 C Q7 的结论并新建 `docs/dev/plugin-settings-transfer-protocol-v1.md` 草案 (不实现). 证据: 文档提交.

### P12 清理与门禁

- [ ] P12.1 (宿主) 删除全部 `@Deprecated` 旧入口 (T14), 守卫 (d) 启用. 证据: 守卫测试通过, `:app:assembleAppDebug` 通过.
- [ ] P12.2 (文档) `docs/settings/archive-format-v1.md` (附录 B 的正式版), `inventory.md` / `defaults.md` 以 P0.2 工具重新生成, `.changelog` 10 种语言 (feature: 导入与导出设置页面, 恢复默认, 自动备份, 脚本 API; improvement: 设置存储层统一). 证据: 提交 hash.
- [ ] P12.3 (验收) 全量 `:app:testAppDebugUnitTest` 0 失败; 附录 D 冒烟清单全部通过并记录机型; 附录 G 基线复测 (键数, 存储数, 守卫计数). 证据: 附录 D / G 更新.

---

## 附录 A 分类清单 (D6, 条目数为 2026-10-20 估计, P0.3 回填精确值)

### A.1 可勾选分类 (按页面顺序; "默认" 指导出 / 导入对话框的默认勾选)

| id | 标题 (简体中文) | 来源 | 条目估计 | 默认 | 生效 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `appearance` | 外观 | 设置 > 外观 | 8 | 是 | APPLY (语言, 夜间模式, 主题色) | `key_theme_color_*` 三项随 `key_theme_color` 一起 |
| `general` | 配置 | 设置 > 配置 | 8 | 是 | APPLY (启动图标) | 含重启策略与计划重启后端 / 延迟, 自动备份开关 (P9) |
| `explorer` | 文件管理器 | 设置 > 文件管理器 | 6 | 是 | NONE | 工作目录跨设备见附录 C Q1 |
| `history` | 回收站与版本历史 | 设置 > 回收站与版本历史 及其子页 | 11 | 是 | NONE | 保留策略改变后由既有清理任务按新值执行 |
| `editor_general` | 代码编辑器 > 常规 | 代码编辑器设置 | 1 | 是 | NONE | 编辑器引擎, 下次打开编辑器生效 |
| `editor_appearance` | 代码编辑器 > 外观 | 代码编辑器设置 | 14 | 是 | NONE | 字体键指向未下载字体见附录 C Q2 |
| `editor_log` | 代码编辑器 > 日志 | 代码编辑器设置 + 代码键 | 6 | 是 | NONE | 含 `key_editor_log_panel_*` |
| `editor_assistance` | 代码编辑器 > 辅助 | 代码编辑器设置 | 6 | 是 | NONE | LSP 开关, 语言矩阵, 文件类型, 声明组 |
| `editor_smart_hint` | 代码编辑器 > 智能提示 | 代码编辑器设置 | 7 | 是 | NONE | |
| `editor_gestures` | 代码编辑器 > 操作手势 | 代码编辑器设置 + 代码键 | 3 | 是 | NONE | |
| `editor_gutter` | 代码编辑器 > 边栏标记 | 代码编辑器设置 | 5 | 是 | NONE | |
| `editor_performance` | 代码编辑器 > 性能 | 代码编辑器设置 | 2 | 是 | NONE | |
| `editor_keyboard` | 代码编辑器 > 符号键盘 | 命名存储 `editor_symbols_config`, `FunctionsKeyboardHelper`, `ace_editor_font_ui_preferences` | 3 份 | 是 | NONE | 整份 JSON 作为一个条目 |
| `timed_task` | 定时任务 | 设置 > 定时任务 | 1 | 是 | APPLY (重排任务) | |
| `a11y` | 无障碍服务 | 设置 > 无障碍服务 + 代码键 | 6 | 是 | NONE | 含手势观察, 按键观察 |
| `running` | 脚本运行 | 设置 > 脚本运行 + 代码键 | 5 | 是 | NONE | 含 `key_root_manager_apps_custom` |
| `record` | 录制脚本 | 设置 > 录制脚本 | 3 | 是 | NONE | |
| `extensibility` | 扩展性 | 设置 > 扩展性 | 2 | 是 | NONE | 对之后启动的脚本生效 |
| `updates` | 更新 | 设置 > 更新 + 开发者选项 > 更新 + `UpdateIgnoreStore` | 3 | 是 | NONE | 时间戳归 `other` |
| `console` | 日志与控制台 | 日志页面与控制台设置对话框的代码键 | 13 | 是 | NONE | |
| `connection` | 客户端与服务端模式 | 抽屉连接项的代码键 | 7 | 是 | NONE | 地址历史与管理器折叠状态归 `other` |
| `layout_inspector` | 布局分析器 | 悬浮窗与代码生成的代码键 | 6 | 是 | NONE | 查询历史归 `other`; 模型文件本身不导出 |
| `developer` | 开发者选项 | 开发者选项页 | 4 | 否 | NONE | 默认不勾选: 调试类开关不宜跨设备带入 |
| `plugin_center` | 插件中心 | 插件中心设置页 + 命名存储 (筛选, 排序, 可见性, 启动图标, 挂载, 变体优先级, 在线来源) + `jvm_source_provider_selection` | 5 + 8 份 | 是 | NONE | 列表页面下次打开生效 |
| `plugin_enable_state` | 插件启用状态 | `plugin_center_enable_state` | 1 份 | 是 | NONE | 未安装的插件条目 -> `TARGET_MISSING` |
| `apk_build` | APK 构建配置档 | `build_apk_config_profiles` | 1 份 | 是 | NONE | 签名密钥库不含 (附录 C Q5) |
| `wizards` | 设置向导配置档 | `permission_wizard`, `plugin_install_wizard` 的自定义配置档 | 2 份 | 是 | NONE | 最近使用的配置档 id 归 `other` |
| `credentials` | 凭据与授权 | `github_auth`, `plugin_center_authorization`, `mcp_server_trust_confirmation`, `three_stove_agent_trust_confirmation`, `bun_host_capability_grants` | 5 份 | 否 | NONE | D2 警告; 脚本 API 不导出 (T15); 自动备份不含 (T16) |
| `other` | 其他 (内部状态) | 全部 `StateKey` + 运行时发现的未登记键 | 约 40 | 否 | NONE | 不参与恢复默认 (T2) |

合计 29 项 (含 `other`). 条目数由 `SettingsRegistry.describe()` 在运行时计算并显示, 本表只作校对.

### A.2 永久排除 (不出现在任何分类)

| 对象 | 原因 |
| --- | --- |
| `device_id_prefs` | 设备标识, 跨设备导入会破坏唯一性 |
| `autojs.localstorage.*` (`core/storage/LocalStorage`, `runtime/api/augment/storages`), `lua-runtime-storage-v1-*` | 脚本自己的数据, 不是宿主设置 |
| `privileged_edit_backups`, `PrivilegedEditSessions`, `PrivilegedFileOperationJournal` | 指向本机文件的操作日志 |
| `runtime_task_intents`, `NodeLifecycleCheckpointStore`, `NodeWorkManagerBridgeScheduler`, `PythonPrivateExecutionSnapshot` | 运行时状态, 进程重启即失效 |
| `ScriptWidgets` | 桌面小部件 id 由启动器分配, 仅本机有效 |
| `key_root_manager_apps_cache`, `InstalledPluginMetadataStore`, `PluginIndexRepository`, `FontCatalogRepository`, `FontStore` (字体文件), `ModelStore` (模型文件), `RuntimeWebLocalDownloads` | 缓存或可重新下载的资源 |
| `key_pref_bundle_default_item` / `key_pref_bundle_disabled_items` | `MaterialListPreference` 的 Bundle 参数名, 不是偏好键 |
| `KeyStoreDatabase`, `TimedTaskDatabase`, `IntentTaskDatabase`, `ColorHistoryDatabase`, `HistoryDatabase` | D1, 第二期候选 |
| `inrt` 的 `key_first_using` / `key_inrt_applied_build_id` | 打包应用运行时状态 |

---

## 附录 B 归档格式 v1 (D5 / T6 / T7, 正式版在 P12.2 落到 `archive-format-v1.md`)

```
AutoJs6-settings-20261020-153000.zip
  manifest.json
  categories/appearance.json
  categories/editor_appearance.json
  ...
  plugins/                      (第一期为空目录或不存在, D3)
```

`manifest.json`:

```json
{
  "format": "autojs6-settings",
  "formatVersion": 1,
  "exportedAt": "2026-10-20T15:30:00+08:00",
  "host": { "packageName": "org.autojs.autojs6", "versionCode": 5592, "versionName": "6.8.0" },
  "device": { "model": "XQ-AT72", "sdk": 31 },
  "categories": [
    { "id": "appearance", "entries": 8, "sensitive": false },
    { "id": "credentials", "entries": 2, "sensitive": true }
  ],
  "plugins": []
}
```

`categories/<id>.json` (`stores` 的键为 `StoreId` 名称, 默认 SharedPreferences 为 `default`; 每个值带类型标签):

```json
{
  "id": "appearance",
  "stores": {
    "default": {
      "key_night_mode": { "t": "string", "v": "key_night_mode_follow_system" },
      "key_theme_color_primary": { "t": "int", "v": -16738680 },
      "key_keep_screen_on_when_in_foreground": { "t": "string", "v": "key_keep_screen_on_when_in_foreground_disabled" }
    }
  }
}
```

命名存储整份导出时 `stores` 下以文件名为键, 值为该文件全部条目; `Json` 键的 `v` 是解码后的 JSON 对象 (`"t": "json"`), 不是再次字符串化的文本.

导入规则 (D8 / D9): 对每个勾选分类, 注册表中属于该分类的键若在文件中存在且类型匹配则写入, 不存在则移除 (恢复默认); 文件中有而注册表没有的键计 `UNKNOWN_KEY`; 类型标签与注册表不符计 `TYPE_MISMATCH`; `Enum` 键的值不在候选项计 `INVALID_VALUE`; `other` 分类只写入注册表登记过的 `StateKey`.

---

## 附录 C 待决事项

| 编号 | 问题 | 建议 | 拍板前置 |
| --- | --- | --- | --- |
| Q1 | `key_working_directory` 跨设备导入时目标目录可能不存在 | 导入但在计划器里标记 `TARGET_MISSING` 并跳过, 结果对话框提示 | P7.2 |
| Q2 | `key_ace_editor_font` 指向本机未下载的字体 | 导入该键, 依赖编辑器现有的字体回退; P2.3 核实回退逻辑存在, 不存在则改为跳过 | P2.3 |
| Q3 | `inrt/Pref` 是否并入 `core/pref/Pref` | 不并入 (T13), 只换底层 | P2.6 |
| Q4 | 页面与文件命名: "导入与导出设置" / `AutoJs6-settings-` 前缀 | 采用; 若维护者偏好 "设置备份" 则在 P6.2 前改 | P6.2 |
| Q5 | 签名密钥库 (`KeyStoreDatabase`) 与密钥文件是否作为第二期 "数据" 分类 | 第二期, 需单独加密方案 | 第二期 |
| Q6 | 定时任务 / Intent 任务 / 颜色库 / 快捷方式 / 版本历史是否作为第二期 "数据" 分类 | 第二期; 快捷方式与颜色库已有独立导出, 可只在 manifest 里引用 | 第二期 |
| Q7 | 插件契约形状: ContentProvider `call` (同 `AutoJs6HostSettingsContract`) 还是 Binder 服务 | ContentProvider `call`, 两个方法 `exportSettings` / `importSettings`, 载荷为 JSON 字符串与 `formatVersion` | P11.2 |
| Q8 | `other` 分类是否向用户展示键名列表 | 不展示, 只显示条目数; 开发者选项可加 "查看内部状态键" 入口 (不在本期) | P6.3 |
| Q9 | 自动备份触发: 每日周期 (T16) 还是设置变化后防抖 | 每日周期, 另在导入 / 恢复默认完成后立即补一份 | P9.1 |
| Q10 | P0.4 发现的同一键多处默认值不一致 | 逐条列出后由维护者定统一值 | P2 对应领域 |
| Q11 | 导入 `plugin_enable_state` 时, 启用状态变化是否触发插件中心的唤醒 / 重绑逻辑 (ColorOS 相关) | 只写偏好, 由插件中心下次刷新按现有逻辑处理; P7.5 在 OnePlus (ColorOS) 上验证 | P7.5 |

---

## 附录 D 冒烟清单与设备矩阵 (P12.3)

脚本位置: `docs/dev/smoke/settings-transfer/` (`run-settings-smoke.sh <serial>`, 经 adb 运行脚本改设置并调用 P10 的脚本 API 导出, 再用 `tools/settings/archive_inspect.py` 核对; UI 流程由维护者手工点击, 脚本只做前后快照比对). 输出只保留每项通过 / 失败与关键计数, 不打印凭据值.

1. 改主题色, 夜间模式, 编辑器字号, 插件中心排序各一项 -> 导出 (默认勾选) -> 恢复默认 (同一组分类) -> 导入 -> 四项恢复, 主题色与夜间模式未重启即生效.
2. 导出时勾选 `credentials` 出现警告; 导入同一文件时不勾选 `credentials`, GitHub token 保持本机值.
3. 用 `archive_inspect.py` 把 manifest 的 `formatVersion` 改为 2 后导入: 出现警告, 允许继续, 结果计数正确.
4. 导入含未安装插件的 `plugin_enable_state`: 结果显示 `TARGET_MISSING` 计数, 已安装插件的状态被写入.
5. 导入后 "撤销上次导入": 第 1 项的四项回到导入前的值; 内部备份目录只保留 5 份.
6. 恢复默认 `editor_appearance`: 字号回到默认, 其他分类不变.
7. 自动备份开关打开后手动触发 `WorkManager` 任务: 工作目录出现备份文件且不含 `credentials`.
8. 旧版本 (本文基线之前的安装包) 升级到本版: 全部设置保持, 守卫测试在本机通过 (R1).
9. API 24 AVD 上完成第 1 与第 5 项 (SAF 与 `java.time` 用法在低版本可用).

设备矩阵 (见 `../COMMON_AGENTS.md` > 可用测试设备; 与其他会话协调占用):

| 设备 | 用途 |
| --- | --- |
| Sony XQ-AT72 (API 31) | 主力 E3 设备 |
| Sony XQ-DQ72 (API 33) 或 Redmi (API 33) | 第二台真机 |
| OnePlus OPD2413 (ColorOS, Android 15) | Q11 与权限弹窗差异 |
| API 24 AVD (CI 同款配方) | 第 9 项 |

---

## 附录 E 证据等级

| 等级 | 含义 |
| --- | --- |
| E0 | 代码已提交, 编译通过 |
| E1 | E0 + JVM 单测通过 |
| E2 | E1 + AVD 冒烟通过 (记录 API level) |
| E3 | E2 + 真机冒烟通过 (记录机型) |

---

## 附录 F 文件地图 (计划, 阶段完成后回填实际路径)

| 关注点 | 文件 |
| --- | --- |
| 键声明 | `core/pref/SettingKey.kt`, `StateKey.kt`, `CacheKey.kt`, `SettingCategory.kt`, `SettingEffect.kt`, `SettingsJson.kt`, `keys/AppearanceKeys.kt`, `GeneralKeys.kt`, `ExplorerKeys.kt`, `HistoryKeys.kt`, `EditorKeys.kt`, `TimedTaskKeys.kt`, `A11yKeys.kt`, `RunningKeys.kt`, `RecordKeys.kt`, `ExtensibilityKeys.kt`, `UpdatesKeys.kt`, `ConsoleKeys.kt`, `ConnectionKeys.kt`, `LayoutInspectorKeys.kt`, `DeveloperKeys.kt`, `PluginCenterKeys.kt`, `CredentialKeys.kt`, `DialogStateKeys.kt`, `MiscStateKeys.kt` |
| 存储 | `core/pref/store/SettingsStore.kt`, `SharedPreferencesStore.kt`, `JsonFileStore.kt`, `StoreId.kt`, `SettingsStores.kt` |
| 注册表 | `core/pref/SettingsRegistry.kt`, `SettingDescriptor.kt` |
| 快照与归档 | `core/pref/archive/SettingsSnapshot.kt`, `SettingsSnapshotCodec.kt`, `SettingsArchiveManifest.kt`, `SettingsArchiveWriter.kt`, `SettingsArchiveReader.kt`, `SettingsKeyMigrations.kt` |
| 导入与备份 | `core/pref/archive/SettingsImportPlanner.kt`, `ImportPlan.kt`, `SettingsImportApplier.kt`, `SettingsEffects.kt`, `SettingsBackupVault.kt`, `SettingsAutoBackupWorker.kt`, `SettingsBackupRetention.kt` |
| 页面 | `ui/settings/transfer/SettingsTransferActivity.kt`, `SettingsTransferFragment.kt`, `SettingsTransferPreference.kt`, `SettingsCategoryPickerDialog.kt`, `SettingsTransferResultDialog.kt`, `res/layout/dialog_settings_category_picker.xml`, `res/xml/fragment_preferences.xml` (入口) |
| 脚本 API | `runtime/api/augment/settings/Settings.kt` |
| 工具 | `tools/settings/inventory.py`, `archive_inspect.py`, `baseline/` |
| 单测 | `app/src/test/java/org/autojs/autojs/core/pref/` (`SettingKeyTest`, `SettingsRegistryGuardTest`, `SettingsSnapshotCodecTest`, `SettingsArchiveTest`, `SettingsArchiveReaderTest`, `SettingsImportPlannerTest`, `SettingsBackupVaultTest`, `InMemorySettingsStore`) |
| 冒烟 | `docs/dev/smoke/settings-transfer/` |
| 文档 | `docs/settings/ROADMAP.md` (本文), `inventory.md`, `defaults.md`, `archive-format-v1.md` |

---

## 附录 G 基线数据 (2026-10-20, `master@4a16be478`)

| 指标 | 数值 | 来源 |
| --- | --- | --- |
| `key_*` 字符串资源 | 265 | `strings_donottranslate.xml` |
| 出现在设置页 XML 的键 | 105 (39 + 40 + 11 + 9 + 5, 去重后) | 5 个 `fragment_*.xml` |
| 只在代码中使用的键 | 162 | 上两项之差 |
| 经 `Pref` 读写的源文件 | 122 | grep `Pref.` |
| 绕过 `Pref` 读默认偏好的源文件 | 8 | grep `PreferenceManager.getDefaultSharedPreferences` |
| 调用 `getSharedPreferences(` 的源文件 / 命名存储数 | 34 / 约 27 | grep |
| `*Store` / `*Prefs` / `*Preferences` / `*Settings` / `*Config*` 类 | 约 80, 分布 40 个目录 | find |
| 序列化方式 | `JSONObject` 11, Gson 1, 逗号拼接 5, `putStringSet` 7 | grep |
| 文件型 JSON 存储 | 14 | grep `filesDir` + `.json` |
| 单测环境 | JUnit4 (`libs.junit`), 无 Robolectric | `app/build.gradle.kts` |

P12.3 复测时在本表追加一列, 预期: 绕过文件 0, `getSharedPreferences(` 调用只剩 `SharedPreferencesStore`, 序列化方式只剩 `SettingsJson`, 守卫 "待迁移" 0.
