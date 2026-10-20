# 设置层重构与分类导入导出路线图

更新日期: 2026-10-20 (第 2 版: 回填附录 C 拍板结果, 新增向导, 部署项, 数据分类与三类自动备份)

本文是 AutoJs6 宿主 "设置 / 配置 / 记忆型状态" 源码层深度重构, 以及建立在新层之上的 "分类导入导出设置" (对标 IntelliJ IDEA 的 Export Settings 对话框: 逐分类勾选, 全选 / 全不选 / 反选, 导出为 zip; 交互做成向导) 的可执行状态表. 它以 2026-10-20 维护者经四轮选择题确认的 18 项决策 (D1-D18) 与同日对附录 C 的拍板结果 (D19-D22, 第 2 节) 为依据, 记录现状证据 (第 1 节), 派生技术决策 (第 3 节), 红线 (第 4 节), 阶段任务 (第 5 节) 与附录 (分类清单, 归档格式, 待决事项与拍板结果, 冒烟清单, 文件地图, 基线数据).

使用方式:

1. 每次会话开始时, 从第 5 节选取未完成条目, 按阶段顺序推进; 条目完成后勾选 `[x]` 并追加证据 (提交 hash / 测试类名 / 设备型号与 API / 计数数值). 证据等级见附录 E.
2. 第 2 节固定决策不再重新讨论; 第 3 节派生决策可在标注的推翻点之前推翻, 之后视同固定; 附录 C 待决事项在进入对应阶段前由维护者拍板, 结果回填到第 2 节或第 3 节.
3. 工程约定以仓库 `AGENTS.md` 与 `../COMMON_AGENTS.md` 为准 (ASCII 标点, 字符串资源 10 种语言, 构建号, 共享工作区). 本文只记录 "改什么" 与证据.
4. 涉及 feature / improvement 的阶段 (P6 起) 须同步 `.changelog/lang_*.json` 并重新生成 markdown; 脚本 API (P12) 须同步文档仓库与 d.ts 仓库 (见 `AGENTS.md` > 关联项目).
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
| 结构化数据库 | 5 个 | `storage/database/TimedTaskDatabase` / `IntentTaskDatabase` (SQLite), `theme/app/ColorHistoryDatabase` (Room), `storage/history/HistoryDatabase` (Room), `apkbuilder/keystore/KeyStoreDatabase` (Room) | 定时任务 / Intent 任务 / 版本历史 / 签名密钥库不在本期范围 (D1); 颜色库经既有 JSON 交换作为数据分类纳入 (D21) |

### 1.2 零散的具体表现

- 序列化方式至少 4 种并存: `org.json.JSONObject` (11 个存储类), Gson (`Pref` 内的 `LinkedHashSet` / `LinkedList` 序列化), 逗号拼接字符串 (5 处), `putStringSet` (7 处).
- 名为 `*Store` / `*Prefs` / `*Preferences` / `*Settings` / `*Config*` 的类约 80 个, 散落在 40 个目录, 其中 `core/plugin/center` 13 个, 其余目录 1-5 个; 同名后缀既用于用户设置 (`SymbolsConfigStore`), 也用于缓存 (`InstalledPluginMetadataStore`) 与运行时日志 (`PrivilegedFileOperationJournal`).
- 用户意图的 "设置" 与应用自身的 "记忆" 共用一个默认 SharedPreferences 且没有标记: `NotAskAgainDialog` 以调用方传入的任意键名写 `Boolean`; `key_last_updates_checked` 等时间戳与 `key_night_mode` 并列.
- 没有任何地方能回答 "某个键属于哪个设置页的哪个分类, 默认值是什么, 改了之后要不要重启", 这正是导入导出需要的元数据.

### 1.3 可复用先例

| 关注点 | 先例 | 用法 |
| --- | --- | --- |
| zip + SAF 导出 / 导入 | `ui/shortcut/ShortcutBackupActivity.kt` (`ShortcutBackupFragment`: `ActivityResultContracts.CreateDocument("application/zip")` / `OpenDocument()`, 页面以 `PreferenceFragmentCompat` 动作条目组织, 入口是 `fragment_preferences.xml` "配置" 分类的 `ShortcutBackupPreference`; 恢复时逐条选择, 缺图标时引导补选) | 入口页面 (T10) 照此结构放在它旁边 (D4); 快捷方式数据分类 (D21) 复用其 zip 写读与逐条选择 |
| JSON 导入前预览与错误消息 | `theme/app/ColorLibraryExchange.kt` (`allChoices` 列出预设 / 智能 / 用户库, `showImportPreview`, `ColorLibraryJsonException.reason` 映射到资源), 编解码在 `theme/library/ColorLibraryJson.kt` (`encode` / `decode`, MIME `application/json`) | 颜色库数据分类 (D21) 复用其文档格式与逐库选择; 导入向导的预览与错误提示 |
| 设置向导串联 | `ui/wizard/SetupWizards.kt` (`Kind { PERMISSIONS, PLUGINS }`, `showChooser` 多选后 `runFrom` 逐个运行, 每个向导带 `isFollowed` 标志; 首次进主页 `onHomeCreated` 弹欢迎条), `WizardUi` (`showAndAwait`, `launchAndAwaitReturn`, 进度条着色, snackbar 锚点), `WizardProfileController` / `WizardProfileStore` (配置档) | "设置导入向导" 作为第三个 `Kind` 接入 (D19 / P11); 导入 / 导出向导的对话框与等待写法复用 `WizardUi` |
| 字体下载与安装 | `ui/edit/editor/ace/font/FontDownloader` (`FontDownloadEvent.Queued / Progress / Success / Failure / Cancelled`), `FontStore` (`installationState`, `installedFont`), `FontCatalogRepository` (`loadCached` / `bootstrap`) | 部署项 "下载编辑器字体" (D20 / T18) 直接驱动, 进度事件映射到合并进度对话框 |
| 纯对象可测策略 | `storage/history/HistoryRetentionPolicy`, `TrashRestoreNaming` (回收站 roadmap D5: JVM 单测只有 JUnit4, 无 Robolectric) | 导入计划器, 编解码, 注册表描述, 部署项计划全部做成只依赖原始类型的对象 (R3) |
| 周期任务 | `storage/history/HistoryCleanupScheduler.schedulePeriodicCleanup` (`PeriodicWorkRequestBuilder<HistoryCleanupWorker>(1, TimeUnit.DAYS)`) | 周期备份 (P9) |
| 重启 | `util/IntentUtils.restart(context, beforeExit, scriptsAfterRestart)` | 结果页 "立即重启" (D10) |
| 热生效入口 | `ThemeColorManager.setThemeColor`, `ViewUtils.setDefaultNightMode`, `LocaleUtils.setLocale` / `setFollowSystem`, `LauncherIconPreference` 的组件切换逻辑 | 生效表 (T12) 调用, 不复制逻辑 |
| 资源一致性测试读源码文件 | `runtime/api/modules/ScriptMessagesTest.developerOptionStringsExistInEveryLanguage` | 守卫测试 (T5) 解析 `strings_donottranslate.xml` 与源码 |

---

## 2. 固定决策

D1-D18 来自 2026-10-20 四轮选择题, D19-D22 来自同日附录 C 的拍板 (原问题与结论见附录 C).

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 第一期范围 = 默认 SharedPreferences + 命名 SharedPreferences, 外加两个数据分类 (快捷方式, 颜色库) | 定时任务 / Intent 任务 / 版本历史 / 签名密钥库仍是第二期 (附录 C Q5 / Q6); 快捷方式与颜色库保留各自的独立导出, 同时作为 `data_*` 分类融入全局导入导出 (D21) |
| D2 | 敏感项单独分类 "凭据与授权", 默认不勾选 | 勾选时弹一次 "将以明文写入文件" 警告; 文件内明文; 成员见附录 A (`credentials`) |
| D3 | 官方插件自身设置第一期不含, 预留契约 | 归档结构预留 `plugins/<packageName>/` 目录与 manifest `plugins` 字段; 注册表预留 `PluginSettingsProvider` 扩展点; 第二期在 `common-plugin-api` 定义导出 / 导入契约 (ContentProvider `call`, 见附录 C Q7) |
| D4 | 入口 = 设置 > 配置 > "管理 AutoJs6 设置" 独立页面, 交互为向导 | 页面顶部两个主动作 "导入设置" / "导出设置" (与 IDEA 的 Import Settings / Export Settings 对应), 其下 "恢复默认设置...", "撤销上次导入" 与 "自动备份" 三个开关; 导入与导出各自是一个多步向导 (T10) |
| D5 | 文件格式 = zip, `manifest.json` + `categories/<id>.json` + `data/` | 默认文件名全小写连字符: `autojs6-settings-<yyyymmdd-hhmmss>.zip`; 格式见附录 B |
| D6 | 分类粒度 = 按设置页内分类, 约 31 项 | 每项显示条目数; 清单见附录 A, 由注册表派生而非手工维护; 数据分类在向导内有细化页 (D21) |
| D7 | 未登记的键归 "其他 (内部状态)", 默认不勾选 | 永久排除清单见附录 A.2; 守卫测试保证正常情况下 "其他" 只含登记过的记忆型状态; 不向用户展示键名列表 (附录 C Q8) |
| D8 | 导入 = 按分类整体覆盖 + 自动备份可撤销 | 勾选分类内文件没有的键恢复默认值, 未勾选分类不动; 导入前自动把全部设置备份到内部目录 (保留 5 份), 页面提供 "撤销上次导入" |
| D9 | 宽松导入 + 跳过计数 | manifest 带 `formatVersion` 与宿主 `versionCode`; 未知键 / 类型不符 / 枚举值无效 / 目标不存在 (如未安装的插件) 各自计数并在结果中展示; 文件 `formatVersion` 高于本机时警告后允许; 预留键改名迁移表 (第一期为空) |
| D10 | 生效 = 能热生效的立即应用, 其余提示重启 | 注册表按键标记 `NONE` / `APPLY` / `RESTART`; `APPLY` 走现有入口 (1.3); 结果页在有 `RESTART` 键变化时提供 "立即重启" |
| D11 | 附带: 按分类恢复默认设置 | 同一注册表, 勾选分类后清除对应键 (命名存储整份清空), 先自动备份 |
| D12 | 附带: 脚本 API 只读导出 | `settings` 模块增加导出函数 (T15), 不开放导入; 同步文档与 d.ts |
| D13 | 附带: 自动备份到工作目录, 三类机制 | 周期备份 (日 / 周 / 月, 默认关), 变更后备份 (设置变化后防抖, 默认关), 操作后备份 (导入 / 恢复默认 / 撤销完成后立即一份, 默认开); 均不含凭据分类; 细节见 D22 / T20 |
| D14 | 验收 = JVM 单测 + 真机冒烟脚本 | 编解码 / 注册表 / 守卫 / 导入计划 / 部署计划全部为纯对象 JUnit4; 真机用 `docs/dev/smoke/settings-transfer/` 脚本做 "改设置 -> 导出 -> 清除 -> 导入 -> 核对" 闭环, 至少 2 台真机 + 1 个 API 24 AVD |
| D15 | 重构为前置阶段, 导入导出建在新层上 | P1-P5 先于 P6; 分类注册表是声明层的副产品, 不单独维护键清单 |
| D16 | 目标形态 = 声明式 `SettingKey` + 统一 `SettingsStore` | 不引入 Jetpack DataStore; 键名与存储文件名不变, 已安装用户零迁移; 序列化收敛到一套 JSON 编解码 |
| D17 | 迁移方式 = 平行新建 + 逐领域迁移 + 旧入口转发 | 新层先与旧代码共存, 按领域一次迁一片, 旧 `Pref.getXxx` / 页面级封装标 `@Deprecated` 转发到新层, 每个阶段编译通过且可独立提交, 最后阶段 (P14) 删除旧入口 |
| D18 | 重构边界 = 默认偏好与页面级封装 + 命名存储 + 记忆型状态 + 文件型 JSON 存储 | 记忆型状态单独声明为 `StateKey`; 文件型 JSON 存储只做归属登记与编解码统一, 不改文件格式, 不改目录; `inrt/Pref` 不并入 (附录 C Q3) |
| D19 | 导入 / 导出交互为向导; "设置导入向导" 成为第三个设置向导 | `SetupWizards.Kind` 增加 `SETTINGS_IMPORT`, 排在权限申请向导与插件安装向导之后, 出现在 `showChooser` 与首次欢迎流程中, 同样可跳过; 它与 "管理 AutoJs6 设置" 页面的 "导入设置" 共用同一导入向导实现 (附录 C Q0) |
| D20 | 部署项机制: 导入中需要用户确认的落地动作统一为 "部署项", 合并在一个进度对话框中执行 | 第一期两种部署项: 创建工作目录 (附录 C Q1), 下载并应用编辑器字体 (附录 C Q2, 未安装 Ace 编辑器插件时先提示安装); 用户放弃某项则该条目不导入并保持当前值; 部署失败在全部完成后集中展示原因并按需引导权限; 机制可扩展, 未来的部署项进同一对话框 |
| D21 | 数据分类: 快捷方式与颜色库 | 归档 `data/shortcuts.zip` (现有快捷方式备份 zip 原样) 与 `data/color-library.json` (现有 `ColorLibraryJson` 文档); 向导内各有一个细化页, 可逐条 / 逐库选择; 各自原有的独立导出入口保留 (附录 C Q6) |
| D22 | 自动备份三类机制的命名与默认值 | "周期备份" (每日 / 每周 / 每月三选一, 默认关), "变更后备份" (设置变化后 5 分钟内无新变化即备份一次, 默认关), "操作后备份" (导入 / 恢复默认 / 撤销完成后立即一份, 默认开); 术语可在 P9 实现时微调, 默认值不变 (附录 C Q9) |

---

## 3. 派生技术决策

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 键声明模型 `core/pref/SettingKey<T>`: 密封类型 `Bool` / `Int` / `Long` / `Float` / `Str` / `StrSet` / `Enum<E>` (值为资源键字符串, 携带候选项) / `Json<T>` (经 `SettingsJson` 编解码); 字段: `keyRes` (由 `StringUtils.key` 惰性解析为键名), `store` (`StoreId`, 默认为 `Default`), `category`, `default` (常量或读资源的 lambda), `effect` (`NONE` / `APPLY` / `RESTART`), `sensitive`, `deployment` (可空, 指向部署项种类, T18). 声明按领域放在 `core/pref/keys/<Domain>Keys.kt` 的 `object` 中 | P1.1 结束 |
| T2 | `StateKey<T>` (记忆型状态) 与 `CacheKey<T>` (缓存) 同模型: `StateKey` 固定归 `other` 分类, 默认不勾选, 不参与恢复默认; `CacheKey` 不可导出, 登记只为守卫测试; 设置值常量 (`key_night_mode_follow_system` 等) 登记为 `ValueConstant`, Bundle 参数名登记为 `Excluded` | P1.1 结束 |
| T3 | `SettingsStore` 接口 (`get` / `put` / `remove` / `contains` / `all` / `edit {}` / `listen`), 实现 `SharedPreferencesStore(name?)` 与 `JsonFileStore(file)`; 命名存储以 `StoreId(name, backend)` 在 `SettingsStores` 登记; 测试用 `InMemorySettingsStore` | P1.2 结束 |
| T4 | `SettingsRegistry` 聚合全部领域 `object`, 提供 `describe()` 输出纯数据 `SettingDescriptor(name, type, store, category, defaultText, effect, kind, deployment)`, 分类列表 `SettingCategory(id, titleRes, order, defaultSelected, sensitive, kind = SETTINGS / DATA)`; 键在声明处写分类, 注册表反向索引; 导入导出, 恢复默认, 守卫测试, 文档生成全部只消费 `describe()` | P1.3 结束 |
| T5 | 守卫测试 (JVM): (a) `strings_donottranslate.xml` 中每个 `key_*` 都被登记为 Setting / State / Cache / ValueConstant / Excluded 之一; (b) 源码中 `getSharedPreferences(` 的字面量与常量文件名都在 `SettingsStores` 或排除清单; (c) 每个键恰好一个分类, 每个分类至少一个键, `sensitive` 键只在 `credentials`; (d) 旧入口删除后 (P14) 源码中不再出现 `PreferenceManager.getDefaultSharedPreferences` (白名单: `SharedPreferencesStore`, `PreferenceFragmentCompat` 子类); (e) 每个 `deployment` 种类都有已注册的部署项实现 | 不可推翻 |
| T6 | 快照 `SettingsSnapshot` = 分类 -> 存储 -> 键 -> `TypedValue(type, value)`; 类型标签 `bool` / `int` / `long` / `float` / `string` / `stringSet` / `json`; 编解码 `SettingsSnapshotCodec` 基于 `org.json`, 不用 Gson; "其他" 分类的未登记键按 `SharedPreferences.getAll()` 的运行时类型打标签; 数据分类 (T19) 不进快照, 由各自的数据编解码直接写归档 | P6.1 结束 |
| T7 | 归档 `SettingsArchiveWriter` / `SettingsArchiveReader` 用 `java.util.zip`, 读取时先只解 `manifest.json` 列出分类与条目数, 用户勾选后再解对应 `categories/<id>.json` 或 `data/*`; 单个 zip 上限 16 MB, 超出拒绝 | P6.1 结束 |
| T8 | 导入计划 `SettingsImportPlanner` 为纯函数: 输入注册表描述, 归档快照, 勾选分类, 已安装插件包名集合, 本机环境探测结果 (目录是否存在, 字体是否已安装, Ace 插件是否安装); 输出 `ImportPlan` (`puts`, `removes`, `skips` 按原因 `UNKNOWN_KEY` / `TYPE_MISMATCH` / `INVALID_VALUE` / `TARGET_MISSING` / `NEWER_FORMAT` / `DECLINED`, `deployments` 待确认部署项列表); 带 `deployment` 的键在环境不满足时不直接跳过, 而是进入 `deployments` 等用户确认 (T18); 应用 `SettingsImportApplier` 对每个存储一次 `commit`, 之后 `SettingsEffects.run(changedKeys)` 按 `effect` 分组执行 | P7.2 结束 |
| T9 | 内部备份库 `SettingsBackupVault` 位于 `filesDir/settings-backups/`: `auto-import-<ts>.zip` 与 `auto-reset-<ts>.zip` 共保留 5 份 (含凭据与其他分类, 私有目录, 只服务撤销); `last-operation.json` 记录触及分类与时间; "撤销上次导入" = 以最近备份对触及分类再做一次导入 (同样走 T8, 无部署项), 撤销本身不再写内部备份, 但触发 "操作后备份" (T20) | P7.3 结束 |
| T10 | 页面与向导 (D4 / D19): `ui/settings/transfer/SettingsTransferActivity` + `SettingsTransferFragment` (`PreferenceFragmentCompat`, 同 `ShortcutBackupFragment` 写法) 为 "管理 AutoJs6 设置" 页面. 导出向导 `SettingsExportWizard`: 第 1 步选择分类 (`SettingsCategoryPickerStep`: 复选列表, 每项副标题 "N 项", 底部 "全选" / "全不选" / "反选", 与 IDEA 一致; 勾选 `credentials` 时弹 D2 警告; 数据分类旁有 "细化..." 进入 T19 的细化页) -> 第 2 步选择保存位置 (SAF) -> 完成页 (文件名, 分类数). 导入向导 `SettingsImportWizard`: 第 1 步选择文件 (SAF) 并显示 manifest 摘要 (来源宿主版本, 设备, 导出时间, 更高 `formatVersion` 的警告) -> 第 2 步选择分类 (只列文件中存在的, 复用同一步骤) -> 第 3 步确认部署项 (T18, 没有则跳过此步) -> 第 4 步执行 (内部备份, 应用, 部署进度对话框) -> 结果页 (写入 / 恢复默认 / 跳过按原因 / 部署成功与失败计数, 失败原因与权限引导, "立即重启"). 每一步可 "上一步" / "跳过" / "取消"; 对话框与等待用 `WizardUi.showAndAwait` / `launchAndAwaitReturn`; 向导状态对象 `SettingsWizardState` 为纯数据, 旋转屏幕后可恢复 | P6.2 结束 |
| T11 | SAF: `CreateDocument("application/zip")` / `OpenDocument()` (MIME `application/zip`, `application/octet-stream`); 默认文件名 D5 (全小写连字符); 导出成功后完成页显示文件名 | 不可推翻 |
| T12 | 生效表 `SettingsEffects`: `APPLY` 键各自绑定现有入口 (主题色 -> `ThemeColorManager.setThemeColor`; 夜间模式 -> `ViewUtils.setDefaultNightMode`; 语言 -> `LocaleUtils.setLocale` / `setFollowSystem`; 启动图标 -> `LauncherIconPreference` 抽出的组件切换逻辑; 定时任务后端 -> 现有重排入口); 未绑定入口的 `APPLY` 键在守卫测试中报错 | P7.4 结束 |
| T13 | `inrt/Pref` 不并入 `core/pref/Pref`, 只在 P2.6 把底层换成 `SharedPreferencesStore`; 打包应用无导入导出界面 | 已拍板 (附录 C Q3), 不可推翻 |
| T14 | 旧入口转发期间 (P2-P13) 不得新增旧式访问 (`Pref.getXxx` 新成员, 新的 `getSharedPreferences` 调用); 新代码直接用 `SettingKey` | 不可推翻 |
| T15 | 脚本 API `settings.export(path?, categories?)`: 返回写入的绝对路径; `path` 省略时写到工作目录 `backups/settings/`; `categories` 省略时为默认勾选集合 (含数据分类); 传入 `credentials` 时忽略并在返回对象的 `skipped` 中标注 (脚本不得导出凭据); 放在现有 `runtime/api/augment/settings/Settings.kt` | P12.1 结束 |
| T16 | (并入 T20) | - |
| T17 | 不移动既有文件的包位置 (与并行会话冲突最小); 重构只改类的内部实现与基类, 命名规则: 命名存储类保留 `*Store` 后缀, 页面级 `*Prefs` / `*Preferences` / `*Settings` 封装在迁移后删除, 其成员并入对应 `<Domain>Keys` | 不可推翻 |
| T18 | 部署项 (D20): `core/pref/deploy/DeploymentItem` 接口 (`kind`, `describe()` 给确认页的标题 / 说明 / 需要的权限, `check()` 判断是否已满足, `run(progress)` 挂起函数, `result`); 实现 `CreateWorkingDirectoryItem` (`key_working_directory`: 目标目录不存在时提出; 运行时创建目录, 失败原因映射到资源并给出 "所有文件访问" 等权限引导) 与 `DownloadEditorFontItem` (`key_ace_editor_font`: 字体未安装时提出; Ace 编辑器插件未安装时先列为 "需要安装 Ace 编辑器插件" 的前置项并链接插件中心; 运行时经 `FontCatalogRepository` + `FontDownloader` 下载, `FontDownloadEvent.Progress` 映射为进度, 成功后写入字体键); 确认页 `DeploymentConfirmStep` 逐项勾选 "部署" 或 "放弃" (放弃 = 该键 `DECLINED`, 保持当前值); 进度对话框 `DeploymentProgressDialog` 合并显示全部部署项 (每项状态 + 总进度), 可取消未开始的项; 失败不中断其他项, 完成后在结果页集中列出原因与引导按钮; 纯逻辑部分 `DeploymentPlanner` (环境探测结果 -> 待确认列表) 可 JVM 测试 | P7.6 结束 |
| T19 | 数据分类 (D21): 分类 `data_shortcuts` 与 `data_color_library` 的 `kind = DATA`, 不经 `SettingsSnapshot`, 由 `ShortcutDataCodec` (调用 `ShortcutBackupFragment` 抽出的 zip 写读对象, 归档内 `data/shortcuts.zip` 与独立导出字节一致) 与 `ColorLibraryDataCodec` (`ColorLibraryJson.encode` / `decode`, 归档内 `data/color-library.json`) 读写; 导出细化页列出全部快捷方式 / 全部用户与智能颜色库供勾选 (默认全选), 导入细化页列出文件内条目供勾选并按既有逻辑逐条恢复 (快捷方式沿用 `ShortcutBackupReconciler` 的缺图标补选, 颜色库沿用 `ColorLibraryExchange.importLibraries` 的分组 id 重映射); manifest `data` 字段记录每个数据分类的条目数 | P10.1 结束 |
| T20 | 自动备份 (D13 / D22): `SettingsAutoBackup` 统一写到 `<工作目录>/backups/settings/`, 文件名 `autojs6-settings-auto-<mechanism>-<yyyymmdd-hhmmss>.zip` (`mechanism` = `periodic` / `change` / `operation`), 每种机制各保留最近 7 份, 分类 = 默认勾选集合 (含数据分类, 不含 `credentials` 与 `other`); 周期备份用 `PeriodicWorkRequest` (每日 1 天, 每周 7 天, 每月 30 天); 变更后备份监听 `SettingsStores` 的变化事件, 5 分钟防抖后由一次性 `WorkRequest` 执行, 自动备份自身写入与 `other` 分类的变化不触发; 操作后备份在导入 / 恢复默认 / 撤销的结果页之前同步执行, 失败只在结果页提示不阻断; 三个开关与周期选项都是 `general` 分类的普通设置键; 工作目录不可写时发通知并暂停 | P9.1 结束 |

---

## 4. 红线 (所有阶段必须遵守)

- R1 零迁移 (D16): 任何键名, 命名存储文件名, 文件型存储的路径与格式都不得改变; 需要改名时只能走 D9 的迁移表并保留旧键读取.
- R2 行为保持: P1-P5 不改变任何设置的默认值, 类型与读写时机; P0.4 记录的默认值表是核对依据, 发现同一键多处默认值不一致时在附录 C Q10 下登记, 由维护者拍板后才统一.
- R3 可测逻辑与 Android 解耦 (回收站 roadmap D5 同款): 编解码, 注册表描述, 导入计划, 部署计划, 备份保留策略, 守卫逻辑只依赖原始类型与 `org.json`; JVM 单测只有 JUnit4.
- R4 共享工作区: 新建 Kotlin 文件在编译通过前不放入 `app/src`; 放回前通知并立即编译; 每个阶段结束时 `:app:assembleAppDebug` 与相关单测通过.
- R5 文案: 代码注释, 文档, 字符串资源只用 ASCII 标点; 新字符串资源 10 种语言同步, 按 `name` 升序, 默认与 `values-en` 一致.
- R6 敏感项: `sensitive` 键的值不得出现在日志, toast, 崩溃信息与自动备份 (T20) 中; 内部备份 (T9) 允许包含.
- R7 不引入 DataStore, 不改 `inrt` 行为, 不把 Room / SQLite 数据纳入第一期 (颜色库只经既有 JSON 交换, 不直接读 Room).
- R8 部署项只做用户在确认页勾选过的动作, 不在后台静默创建目录或下载; 放弃的部署项对应的键保持当前值.

---

## 5. 阶段任务

### P0 安全网与清单

- [x] P0.1 (文档) 本文: 现状证据, 决策, 派生决策, 红线, 阶段任务, 附录. 证据: 本文件 (c35f603be, 551b8be14, 第 2 版见本次提交).
- [x] P0.2 (工具) `tools/settings/inventory.py`: 扫描 `strings_donottranslate.xml` 的 `key_*`, 5 个设置页 XML 的出现位置, 源码引用位置 (文件与行), `getSharedPreferences(` 的文件名字面量与常量, `filesDir` 下的 JSON 文件名; 输出 `tools/settings/baseline/inventory-<date>.json` 与可读的 `docs/settings/inventory.md`. 证据: 提交 `d6b97fbf6` (子会话 roadmap-p0); 分类合并, 默认值提取与 `--check` 随 `06a331f28` 补入.
- [x] P0.3 (文档) 分类初稿: 对 265 个 `key_*` 与约 27 个命名存储逐一标注 kind (Setting / State / Cache / ValueConstant / Excluded) 与分类 id (含 `deployment` 种类), 回填附录 A 的条目数; 无法判断的进附录 C (Q12 起). 证据: 提交 `06a331f28`; `tools/settings/classification.json` 覆盖 267 个 `key_*` (Setting 123 / State 40 / ValueConstant 74 / Excluded 29 / Cache 1), 71 组源码字面量键, 29 份命名存储, 13 个 filesDir JSON 存储与 3 个页面字面量键, `py -3.12 -I tools/settings/inventory.py --check` 0 问题; `docs/settings/inventory.md` 的 kind 列无空白, 第 7 节为各分类条目数; 部署项种类 `CREATE_DIRECTORY` (`key_working_directory`) 与 `DOWNLOAD_EDITOR_FONT` (`key_ace_editor_font`); 附录 A 已回填, 附录 A.2 补登 4 行, 新登记 Q12 / Q13 (子会话 roadmap-p0).
- [x] P0.4 (文档) 默认值表: 每个 Setting 的默认值来源 (`R.bool` / `R.integer` / `R.string` / 伴生常量 / 调用处字面量) 与取值, 同一键多处不一致的列入附录 C Q10. 证据: 提交 `06a331f28`; `docs/settings/defaults.md` 列出 135 个 Setting (123 个 `key_*` + 12 组默认偏好字面量键) 的 218 条来源, 全部有来源 (其中 46 条为 `classification.json` 的 `extraDefaults` 人工补充), 3 处不一致已登记在附录 C Q10 (子会话 roadmap-p0).
- [x] P0.5 (测试) JVM 夹具 `InMemorySettingsStore` 与 `SettingDescriptor` 构造辅助, 放在 `app/src/test/java/org/autojs/autojs/core/pref/`. 证据: 提交 `f48cb6681`; `InMemorySettingsStore` (T3 成员, SharedPreferences 同款类型与提交 / 监听语义, `commitCount`) 与 `SettingDescriptorFixtures` (`SettingDescriptorSpec` 含 T4 的 8 个字段, 校验 T6 类型 / D10 生效 / T2 kind / T18 部署项); `InMemorySettingsStoreTest` 11 例 + `SettingDescriptorFixturesTest` 8 例, 先在 scratchpad 用 kotlinc 1.7.10 + JUnit 4.13.2 跑通, 放入后 `:app:testAppDebugUnitTest --tests "org.autojs.autojs.core.pref.*"` 19 例 0 失败 (E1); 夹具不在运行时使用 `org.json` (测试类路径只有 android.jar 桩, 见 Q14) (子会话 roadmap-p0).

### P1 新基座 (`core/pref`)

- [ ] P1.1 (宿主) `SettingKey` / `StateKey` / `CacheKey` / `SettingCategory` / `SettingEffect` / `DeploymentKind` / `SettingsJson` (T1 / T2), 不接入任何调用方. 证据: `SettingKeyTest` (类型, 默认值解析, Enum 候选校验, Json 往返).
- [ ] P1.2 (宿主) `SettingsStore` / `SharedPreferencesStore` / `JsonFileStore` / `StoreId` / `SettingsStores` (T3, 含变化事件供 T20 使用). 证据: `SharedPreferencesStore` 在真机上对同一文件与旧 `Pref` 读写互通 (E3, 记录机型); `JsonFileStoreTest` (JVM, 临时目录).
- [ ] P1.3 (宿主) `SettingsRegistry` 骨架与 `describe()` (T4); 守卫测试 (T5 a / b / c / e) 以 P0.3 的分类初稿为初始允许清单, 未迁移的键先以 "待迁移" 登记, 测试只要求不出现未登记项. 证据: `SettingsRegistryGuardTest` 通过且列出待迁移计数.
- [ ] P1.4 (宿主) `core/pref/Pref` 的通用读写 (`getString` / `putBoolean` / `getStringList` 等) 改为经 `SettingsStores.default` (行为不变, Gson 列表序列化改为 `SettingsJson` 并保证旧值可读). 证据: 真机旧版本升级后 `key_working_directory_history` 等列表值仍可读 (E3).

### P2 默认偏好迁移 (每条: 声明键 -> 替换读写 -> 旧 getter `@Deprecated` 转发 -> 单测 -> 守卫计数下降)

- [ ] P2.1 外观与配置: `AppearanceKeys` (语言, 夜间模式与自动夜间, 主题色三项, 前台常亮), `GeneralKeys` (文档来源, 重启策略与计划重启后端 / 延迟, 启动图标, 启动器快捷方式, 自动备份三个开关与周期选项占位); 迁移 `Language`, `ThemeColorManager`, `ViewUtils` 夜间模式, `LauncherIconPreference`. 证据: 单测 + E3.
- [ ] P2.2 文件管理器与回收站: `ExplorerKeys` (工作目录 [`deployment = CREATE_DIRECTORY`], 隐藏文件, 扩展名, 回放历史开关, 新文件默认前缀, 批量重命名历史保留), `HistoryKeys` (9 个保留策略键, 永久删除同时清历史, 恢复不自动落盘); `HistoryPrefs` / `RetentionLimitSettings` 并入. 证据: `storage/history` 既有单测仍过 + E3.
- [ ] P2.3 代码编辑器 8 个分类: `EditorKeys` (常规, 外观 [`key_ace_editor_font` 的 `deployment = DOWNLOAD_EDITOR_FONT`], 日志, 辅助, 智能提示, 操作手势, 边栏标记, 性能, 含 `key_editor_log_panel_*` 等代码键); `EditorEnginePreferences` / `EditorThresholdSettings` / `AceEditorFontUiPreferences` (默认偏好部分) 并入. 证据: 单测 + E3 (两种编辑器引擎各开一次).
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
- [ ] P4.2 其余状态: `key_last_*` 四个时间戳, `key_setup_wizard_welcome_done`, `key_post_notifications_permission_request_attempted`, `key_*_socket_normally_closed` / `key_mcp_server_normally_closed`, `key_floating_menu_shown`, `*_manager_*_level` / `*_collapsed`, `*_introduced`, `key_working_directory_initialized` / `_history`, `key_pc_server_address_history`, `key_layout_inspector_query_history`, `plugin_center_recent`, `plugin_center_attention`, `PluginUpdateCheckStore`, `key_plugin_center_last_online_check_ts`, `key_keep_screen_on_when_in_foreground_last_enabled`, 向导的 "上次勾选" 记忆. 证据: 守卫 "待迁移" 计数为 0.
- [ ] P4.3 "其他" 分类的运行时兜底: 导出时对默认存储 `getAll()` 与注册表求差, 未登记键仍进 `other` 并以运行时类型打标签 (T6), 单测覆盖. 证据: `SettingsSnapshotTest`.

### P5 文件型 JSON 存储归属整理 (D18 / T17)

- [ ] P5.1 14 个文件型存储逐一登记为 `JsonFileStore` (用户设置: 无, 预期全部为 Cache 或运行时快照) 或 `CacheKey`; 内部的 `JSONObject` 手写编解码改用 `SettingsJson` 的辅助函数 (不改文件格式). 证据: 各自既有单测仍过; 守卫 (b) 对 `filesDir` JSON 文件名的检查通过.
- [ ] P5.2 收口: 删除已空的 `*Prefs` / `*Preferences` / `*Settings` 封装 (T17), 全量 `:app:testAppDebugUnitTest` 通过. 证据: 用例数与失败数.

### P6 导出向导 (D4 / D5 / D6 / D2 / D19)

- [ ] P6.1 (宿主) `SettingsSnapshot` / `SettingsSnapshotCodec` / `SettingsArchiveWriter` (T6 / T7) 与 manifest (附录 B, 含 `data` 与 `plugins` 字段). 证据: `SettingsSnapshotCodecTest` (七种类型往返, 其他分类标签), `SettingsArchiveTest` (JVM 临时目录写读往返).
- [ ] P6.2 (宿主) 页面与向导骨架: `SettingsTransferActivity` / `Fragment` ("管理 AutoJs6 设置": "导入设置", "导出设置", "恢复默认设置...", "撤销上次导入", 自动备份三开关), `SettingsWizardState`, 向导步骤基类与 `WizardUi` 接法 (T10); `fragment_preferences.xml` "配置" 分类新增 `SettingsTransferPreference` (放在 `ShortcutBackupPreference` 之后), Manifest 注册, 字符串资源 10 种语言. 证据: E3 截图核对 (英文与简体中文各一次, 旋转屏幕后向导状态保持).
- [ ] P6.3 (宿主) `SettingsCategoryPickerStep` (T10): 列表项 = 附录 A 的分类 (按 order), 副标题条目数来自当前快照, 全选 / 全不选 / 反选, `credentials` 勾选警告 (D2), 数据分类的 "细化..." 入口占位 (P10 填充), 记住上次勾选 (`StateKey`). 证据: E3.
- [ ] P6.4 (宿主) 导出向导流程: 选择分类 -> `CreateDocument` -> 写 zip -> 完成页; 取消与写入失败的提示; 大文件上限 (T7). 证据: E3 (两台), 导出文件用 `tools/settings/archive_inspect.py` 校验 manifest 与分类数.

### P7 导入向导 (D8 / D9 / D10 / D19 / D20)

- [ ] P7.1 (宿主) `SettingsArchiveReader` + manifest 校验 (格式名, `formatVersion`, 宿主包名) + 迁移表骨架 `SettingsKeyMigrations` (空). 证据: `SettingsArchiveReaderTest` (损坏 zip, 缺 manifest, 更高 formatVersion 的警告标记).
- [ ] P7.2 (宿主) `SettingsImportPlanner` (T8, 含 `deployments` 输出). 证据: `SettingsImportPlannerTest` (覆盖语义: 文件缺键 -> remove; 未勾选分类不动; 五种跳过原因; `plugin_enable_state` 对未安装包名 -> `TARGET_MISSING`; `other` 分类只写登记过的 State 键; 工作目录不存在 -> 进入 `deployments` 而非跳过; 字体未安装 -> 进入 `deployments`, 放弃后 -> `DECLINED`).
- [ ] P7.3 (宿主) `SettingsBackupVault` (T9): 内部备份, 保留 5 份, `last-operation.json`, 撤销计划. 证据: `SettingsBackupVaultTest` (保留策略, 撤销只触及记录过的分类).
- [ ] P7.4 (宿主) `SettingsImportApplier` + `SettingsEffects` (T8 / T12): 每存储一次 commit, 生效表绑定, 守卫 "APPLY 键必须有入口". 证据: 单测 + E3 (改主题色 / 夜间模式 / 语言后导入, 不重启即生效; 改启动图标后导入, 图标切换).
- [ ] P7.5 (宿主) 导入向导步骤 1 / 2 / 4 / 结果页 (T10): 选文件 -> manifest 摘要 -> 勾选 (只列文件中存在的分类) -> 执行 -> 结果页 ("立即重启"); 页面 "撤销上次导入" 动作. 证据: E3 (两台 + API 24 AVD), 冒烟清单附录 D 第 1-5 项.
- [ ] P7.6 (宿主) 部署项 (T18): `DeploymentItem` / `DeploymentPlanner` / `DeploymentConfirmStep` / `DeploymentProgressDialog`, 实现 `CreateWorkingDirectoryItem` 与 `DownloadEditorFontItem` (含 Ace 插件前置项); 守卫 (e). 证据: `DeploymentPlannerTest` (环境探测 -> 待确认列表, 放弃 -> `DECLINED`), E3 (附录 D 第 10-12 项: 目录创建成功 / 无权限失败并引导, 字体下载进度与应用, 两项同时出现在一个进度对话框).

### P8 恢复默认与撤销 (D11)

- [ ] P8.1 (宿主) "恢复默认设置..." 动作: 同一勾选步骤 (列出全部分类, `other` 不可选, 数据分类不可选), 先 `auto-reset` 内部备份, 清除勾选分类的键 (命名存储整份 `clear`), 跑生效表, 触发操作后备份 (T20). 证据: 单测 (计划器复用, `removes` 全集) + E3 (附录 D 第 6 项).

### P9 自动备份三类 (D13 / D22 / T20)

- [ ] P9.1 (宿主) `SettingsAutoBackup` 写入与保留策略 (每种机制 7 份), 操作后备份接入导入 / 恢复默认 / 撤销的结果页之前. 证据: `SettingsBackupRetentionTest` + E3 (导入后工作目录出现 `operation` 备份且不含 `credentials`).
- [ ] P9.2 (宿主) 周期备份: `general` 分类的开关与周期选项 (每日 / 每周 / 每月), `SettingsPeriodicBackupWorker` (`PeriodicWorkRequest`), 工作目录不可写时的通知. 证据: E3 (手动触发一次任务, 核对文件与份数).
- [ ] P9.3 (宿主) 变更后备份: 开关, `SettingsStores` 变化事件 -> 5 分钟防抖 -> 一次性 `WorkRequest`; 自动备份自身与 `other` 分类变化不触发. 证据: `SettingsChangeDebounceTest` (纯逻辑) + E3 (改一项设置后 5 分钟内出现 `change` 备份, 连续改动只产生一份).

### P10 数据分类: 快捷方式与颜色库 (D21 / T19)

- [ ] P10.1 (宿主) `ShortcutDataCodec` (从 `ShortcutBackupFragment` 抽出 zip 写读对象, 原页面改为调用它) 与 `ColorLibraryDataCodec` (`ColorLibraryJson`); 归档 `data/shortcuts.zip` / `data/color-library.json`, manifest `data` 字段. 证据: 单测 (与独立导出字节一致) + `archive_inspect.py`.
- [ ] P10.2 (宿主) 导出细化页 (全部快捷方式 / 用户与智能颜色库, 默认全选) 与导入细化页 (文件内条目, 逐条恢复沿用 `ShortcutBackupReconciler` 与 `ColorLibraryExchange.importLibraries`); 分类勾选步骤的 "细化..." 入口启用. 证据: E3 (附录 D 第 13 项).

### P11 设置导入向导接入 (D19)

- [ ] P11.1 (宿主) `SetupWizards.Kind.SETTINGS_IMPORT`: `showChooser` 第三项 "设置导入向导", `runFrom` 分支调用 `SettingsImportWizard.show(activity, isFollowed)`, 首次欢迎流程 (`showWelcome`) 的默认勾选含它; 向导首页说明用途并提供 "跳过"; 字符串资源 10 种语言. 证据: E3 (附录 D 第 14 项: 清除 `key_setup_wizard_welcome_done` 后进主页, 三个向导依次出现, 跳过设置导入向导后进入主页).

### P12 脚本 API 只读导出 (D12 / T15)

- [ ] P12.1 (宿主) `settings.export(path?, categories?)` 与 `settings.categories()` (返回分类 id, 种类与条目数). 证据: 单测 (参数解析) + E3 (脚本导出一次, `archive_inspect.py` 校验).
- [ ] P12.2 (关联仓库) 文档仓库 `settings` 页面增补, d.ts 两个仓库同步 (`AGENTS.md` > 关联项目), `.changelog` feature 条目. 证据: 三个仓库的提交 hash.

### P13 插件契约预留 (D3)

- [ ] P13.1 (宿主) manifest `plugins` 字段与 `plugins/<packageName>/` 目录读写能力 (空集合), `PluginSettingsProvider` 接口与注册表空实现, 导入时遇到未知插件目录计入 `TARGET_MISSING`. 证据: 单测.
- [ ] P13.2 (文档) 第二期契约草案 `docs/dev/plugin-settings-transfer-protocol-v1.md` (ContentProvider `call`, `exportSettings` / `importSettings`, JSON 载荷与 `formatVersion`; 不实现). 证据: 文档提交.

### P14 清理与门禁

- [ ] P14.1 (宿主) 删除全部 `@Deprecated` 旧入口 (T14), 守卫 (d) 启用. 证据: 守卫测试通过, `:app:assembleAppDebug` 通过.
- [ ] P14.2 (文档) `docs/settings/archive-format-v1.md` (附录 B 的正式版), `inventory.md` / `defaults.md` 以 P0.2 工具重新生成, `.changelog` 10 种语言 (feature: 管理 AutoJs6 设置页面与导入 / 导出向导, 设置导入向导, 部署项, 恢复默认, 三类自动备份, 数据分类, 脚本 API; improvement: 设置存储层统一). 证据: 提交 hash.
- [ ] P14.3 (验收) 全量 `:app:testAppDebugUnitTest` 0 失败; 附录 D 冒烟清单全部通过并记录机型; 附录 G 基线复测 (键数, 存储数, 守卫计数). 证据: 附录 D / G 更新.

---

## 附录 A 分类清单 (D6, 条目数为 P0.3 精确值, 来源 `docs/settings/inventory.md` 第 7 节, 基线 `master@665c35482`)

### A.1 可勾选分类 (按页面顺序; "默认" 指导出 / 导入向导的默认勾选)

| id | 标题 (简体中文) | 来源 | 条目数 | 默认 | 生效 | 备注 |
| --- | --- | --- | --- | --- | --- | --- |
| `appearance` | 外观 | 设置 > 外观 | 9 | 是 | APPLY (语言, 夜间模式, 主题色) | `key_theme_color_*` 三项随 `key_theme_color` 一起 |
| `general` | 配置 | 设置 > 配置 | 5 (P9 另加自动备份三开关与周期选项) | 是 | APPLY (启动图标) | 含重启策略与计划重启后端 / 延迟, 自动备份三开关与周期选项 (P9) |
| `explorer` | 文件管理器 | 设置 > 文件管理器 | 6 | 是 | NONE | 工作目录不存在时作为部署项 "创建工作目录" (D20 / Q1) |
| `history` | 回收站与版本历史 | 设置 > 回收站与版本历史 及其子页 | 10 | 是 | NONE | 保留策略改变后由既有清理任务按新值执行 |
| `editor_general` | 代码编辑器 > 常规 | 代码编辑器设置 | 2 (1 键 + 1 组字面量键) | 是 | NONE | 编辑器引擎, 下次打开编辑器生效 |
| `editor_appearance` | 代码编辑器 > 外观 | 代码编辑器设置 | 17 | 是 | NONE | 字体未安装时作为部署项 "下载编辑器字体" (D20 / Q2), Ace 插件未安装时先提示安装 |
| `editor_log` | 代码编辑器 > 日志 | 代码编辑器设置 + 代码键 | 6 | 是 | NONE | 含 `key_editor_log_panel_*` |
| `editor_assistance` | 代码编辑器 > 辅助 | 代码编辑器设置 | 8 (3 键 + 5 组字面量键) | 是 | NONE | LSP 开关, 语言矩阵, 文件类型, 声明组 |
| `editor_smart_hint` | 代码编辑器 > 智能提示 | 代码编辑器设置 | 7 | 是 | NONE | |
| `editor_gestures` | 代码编辑器 > 操作手势 | 代码编辑器设置 + 代码键 | 2 | 是 | NONE | |
| `editor_gutter` | 代码编辑器 > 边栏标记 | 代码编辑器设置 | 5 | 是 | NONE | |
| `editor_performance` | 代码编辑器 > 性能 | 代码编辑器设置 | 2 | 是 | NONE | |
| `editor_keyboard` | 代码编辑器 > 符号键盘 | 命名存储 `editor_symbols_config`, `FunctionsKeyboardHelper`, `ace_editor_font_ui_preferences` | 3 份 | 是 | NONE | 整份 JSON 作为一个条目 |
| `timed_task` | 定时任务 | 设置 > 定时任务 | 1 | 是 | APPLY (重排任务) | |
| `a11y` | 无障碍服务 | 设置 > 无障碍服务 + 代码键 | 6 | 是 | NONE | 含手势观察, 按键观察 (`key_enable_observe_key` 无读写方, 见 Q12) |
| `running` | 脚本运行 | 设置 > 脚本运行 + 代码键 | 5 | 是 | NONE | 含 `key_root_manager_apps_custom` |
| `record` | 录制脚本 | 设置 > 录制脚本 | 3 | 是 | NONE | |
| `extensibility` | 扩展性 | 设置 > 扩展性 | 2 | 是 | NONE | 对之后启动的脚本生效 |
| `updates` | 更新 | 设置 > 更新 + 开发者选项 > 更新 + `UpdateIgnoreStore` | 3 (2 键 + 1 份) | 是 | NONE | 时间戳归 `other` |
| `console` | 日志与控制台 | 日志页面与控制台设置对话框的代码键 | 12 | 是 | NONE | |
| `connection` | 客户端与服务端模式 | 抽屉连接项的代码键 | 7 | 是 | NONE | 地址历史与管理器折叠状态归 `other` |
| `layout_inspector` | 布局分析器 | 悬浮窗与代码生成的代码键 | 5 | 是 | NONE | 查询历史归 `other`; 模型文件本身不导出; 颜色选择旧布局键按代码事实归 `other` (Q13) |
| `developer` | 开发者选项 | 开发者选项页 | 4 | 否 | NONE | 默认不勾选: 调试类开关不宜跨设备带入 |
| `plugin_center` | 插件中心 | 插件中心设置页 + 命名存储 (筛选, 排序, 可见性, 启动图标, 挂载, 变体优先级, 在线来源) + `jvm_source_provider_selection` | 14 (3 键 + 6 组字面量键 + 5 份) | 是 | NONE | 列表页面下次打开生效; 挂载 / 每插件检查更新 / 变体优先级是默认偏好中的字面量前缀键 |
| `plugin_enable_state` | 插件启用状态 | `plugin_center_enable_state` | 1 份 | 是 | NONE | 未安装的插件条目 -> `TARGET_MISSING`; 只写偏好, 唤醒 / 重绑由插件中心下次刷新处理 (Q11) |
| `apk_build` | APK 构建配置档 | `build_apk_config_profiles` | 1 份 | 是 | NONE | 签名密钥库不含 (附录 C Q5) |
| `wizards` | 设置向导配置档 | `permission_wizard`, `plugin_install_wizard` 的自定义配置档 | 2 份 | 是 | NONE | 最近使用的配置档 id 归 `other` |
| `data_shortcuts` | 快捷方式 (数据) | 现有快捷方式备份 zip (`data/shortcuts.zip`) | 按条 | 是 | NONE | 细化页逐条勾选; 恢复沿用 `ShortcutBackupReconciler` (D21) |
| `data_color_library` | 颜色库 (数据) | 现有 `ColorLibraryJson` 文档 (`data/color-library.json`) | 按库 | 是 | NONE | 细化页逐库勾选 (用户库与智能库); 预设库不导出 (D21) |
| `credentials` | 凭据与授权 | `github_auth`, `plugin_center_authorization`, `mcp_server_trust_confirmation`, `three_stove_agent_trust_confirmation`, `bun_host_capability_grants` | 5 份 | 否 | NONE | D2 警告; 脚本 API 不导出 (T15); 自动备份不含 (T20) |
| `other` | 其他 (内部状态) | 全部 `StateKey` + 运行时发现的未登记键 | 51 (40 键 + 8 组字面量键 + 3 份) | 否 | NONE | 不参与恢复默认 (T2); 不展示键名 (Q8) |

合计 31 项 (含 `other` 与两个数据分类). 条目数由 `SettingsRegistry.describe()` 与数据编解码在运行时计算并显示, 本表只作校对. P0.3 计数规则: Setting 与 State 计入; "N 键" 为 `key_*` 资源, "字面量键" 为默认偏好中不经资源的键 (含前缀键, 每组 1 条), "份" 为命名存储 (整份 1 条, 内部键不另计); 未写明来源的数字全部是 `key_*` 键. 待决的归属见附录 C Q12 / Q13.

### A.2 永久排除 (不出现在任何分类)

| 对象 | 原因 |
| --- | --- |
| `device_id_prefs` | 设备标识, 跨设备导入会破坏唯一性 |
| `autojs.localstorage.*` (`core/storage/LocalStorage`, `runtime/api/augment/storages`), `lua-runtime-storage-v1-*`, `autojs.node.storage.*` (`engine/NodeBridgeProtocol` 的 Node.js storage 桥, P0.3 补登, Q13) | 脚本自己的数据, 不是宿主设置 |
| `privileged_edit_backups`, `PrivilegedEditSessions`, `PrivilegedFileOperationJournal` | 指向本机文件的操作日志 |
| `runtime_task_intents`, `NodeLifecycleCheckpointStore`, `NodeWorkManagerBridgeScheduler`, `PythonPrivateExecutionSnapshot`, `key_scripts_after_app_restart` (P0.3 补登) | 运行时状态, 进程重启即失效 (后者是重启前写入, 重启后消费的一次性交接值) |
| `ScriptWidgets` | 桌面小部件 id 由启动器分配, 仅本机有效 |
| `key_root_manager_apps_cache`, `InstalledPluginMetadataStore`, `PluginIndexRepository`, `FontCatalogRepository`, `FontStore` (字体文件), `ModelStore` (模型文件), `RuntimeWebLocalDownloads` | 缓存或可重新下载的资源 (字体经部署项按需重新下载) |
| `key_pref_bundle_default_item` / `key_pref_bundle_disabled_items` | `MaterialListPreference` 的 Bundle 参数名, 不是偏好键 |
| `KeyStoreDatabase`, `TimedTaskDatabase`, `IntentTaskDatabase`, `HistoryDatabase` | D1, 第二期候选 (Q5 / Q6) |
| `ColorHistoryDatabase` 的直接读取 | 颜色库只经 `ColorLibraryJson` 交换 (D21 / R7); 颜色历史记录本身不导出 |
| `inrt` 的 `key_first_using` / `key_inrt_applied_build_id`, 以及 inrt 专用的 `key_run_on_boot_inrt`, `key_not_showing_main_activity` 与 4 个权限开关键 (P0.3 补登) | 打包应用运行时状态或打包配置 |
| 已移除的宿主终端的 `key_$_terminal_*` / `key_$_dialog_terminal_tips` (P0.3 补登) | `TerminalLegacyCleanup` 每次启动删除 |

---

## 附录 B 归档格式 v1 (D5 / T6 / T7 / T19, 正式版在 P14.2 落到 `archive-format-v1.md`)

```
autojs6-settings-20261020-153000.zip
  manifest.json
  categories/appearance.json
  categories/editor_appearance.json
  ...
  data/shortcuts.zip            (data_shortcuts 勾选时; 与独立导出字节一致)
  data/color-library.json       (data_color_library 勾选时; ColorLibraryJson 文档)
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
  "data": [
    { "id": "data_shortcuts", "entries": 12, "file": "data/shortcuts.zip" },
    { "id": "data_color_library", "entries": 3, "file": "data/color-library.json" }
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

导入规则 (D8 / D9 / D20): 对每个勾选分类, 注册表中属于该分类的键若在文件中存在且类型匹配则写入, 不存在则移除 (恢复默认); 文件中有而注册表没有的键计 `UNKNOWN_KEY`; 类型标签与注册表不符计 `TYPE_MISMATCH`; `Enum` 键的值不在候选项计 `INVALID_VALUE`; 带 `deployment` 的键在本机环境不满足时进入部署项确认, 用户放弃计 `DECLINED` 并保持当前值; `other` 分类只写入注册表登记过的 `StateKey`; 数据分类按各自编解码逐条恢复, 不走键级规则.

---

## 附录 C 待决事项与拍板结果

Q1-Q11 于 2026-10-20 由维护者拍板 (结论已并入第 2 节 / 第 3 节); Q0 为同日新增并同时拍板; Q10 与 Q12 起待定 (Q10 的 3 条与 Q12-Q14 由 P0.3 / P0.4 / P0.5 子会话登记).

| 编号 | 问题 | 结论 (2026-10-20) | 状态 |
| --- | --- | --- | --- |
| Q0 | 导入 / 导出是否做成向导, 并作为第三个设置向导 | 是: 导入与导出都是向导; "设置导入向导" 排在权限申请向导与插件安装向导之后, 可跳过 (D19, P11) | 已拍板 |
| Q1 | `key_working_directory` 跨设备导入时目标目录可能不存在 | 提示需要创建该目录, 同意后在部署步骤自动创建, 失败时显示原因并按需引导权限; 放弃则不导入此条目并保持当前值 (D20 / T18) | 已拍板 |
| Q2 | `key_ace_editor_font` 指向本机未下载的字体 | 提示下载字体 (Ace 插件未安装时同时提示安装插件), 同意后自动下载并应用, 显示进度; 与 Q1 共用一个部署进度对话框, 未来更多部署项也进同一对话框; 放弃则保持现状, 失败在完成后集中展示 (D20 / T18) | 已拍板 |
| Q3 | `inrt/Pref` 是否并入 `core/pref/Pref` | 不并入, 只换底层 (T13) | 已拍板 |
| Q4 | 页面与文件命名 | 页面仿 IDEA: "管理 AutoJs6 设置" 下 "导入设置" / "导出设置"; 文件名全小写连字符 `autojs6-settings-<yyyymmdd-hhmmss>.zip` (D4 / D5 / T11) | 已拍板 |
| Q5 | 签名密钥库 (`KeyStoreDatabase`) 与密钥文件是否作为第二期 "数据" 分类 | 第二期, 需单独加密方案 | 已拍板 |
| Q6 | 定时任务 / Intent 任务 / 颜色库 / 快捷方式 / 版本历史是否作为第二期 "数据" 分类 | 定时任务 / Intent 任务 / 版本历史第二期; 快捷方式与颜色库保留独立导出并在第一期融入全局导入导出, 向导内有细化页 (D21 / P10) | 已拍板 |
| Q7 | 插件契约形状 | ContentProvider `call` (同 `AutoJs6HostSettingsContract`), `exportSettings` / `importSettings`, JSON 载荷与 `formatVersion` (P13.2 写草案) | 已拍板 |
| Q8 | `other` 分类是否向用户展示键名列表 | 不展示, 只显示条目数 (D7) | 已拍板 |
| Q9 | 自动备份触发方式 | 三类机制: 周期 (日 / 周 / 月, 默认关), 变更后防抖 (默认关), 操作后立即 (默认开); 术语可微调 (D22 / T20) | 已拍板 |
| Q10 | P0.4 发现的同一键多处默认值不一致 (`docs/settings/defaults.md` 第 2 节, 共 3 条): (1) `key_editor_text_size`: 设置页 `EditorTextSizePreference` 未设置时显示 `R.integer.editor_text_size_default_value` = 14, 原生编辑器 `CodeEditor` 未设置时取布局 `code_editor.xml` 的 `textSize` = 15sp; (2) `key_enable_a11y_service_with_root_access`: `Pref` / 设置页 / inrt 缺键时为 true (`R.bool.pref_enable_a11y_service_with_root_access`), 脚本 API `settings.isEnabled` 缺键时为 false (`Settings.kt:50`, 对所有别名统一用 false); (3) `key_use_volume_control_running`: 同 (2), true 对 false | 建议: (1) 统一为 14, 改原生编辑器的初值读同一资源; (2) / (3) `settings.isEnabled` 缺键时改用各键自身的默认值 (与设置页一致); 拍板前 P1-P5 保持现状 (R2) | 待拍板 (P0.4 已登记) |
| Q11 | 导入 `plugin_enable_state` 时是否触发插件中心的唤醒 / 重绑 | 只写偏好, 由插件中心下次刷新按现有逻辑处理; P7.5 在 OnePlus (ColorOS) 上验证 | 已拍板 |
| Q12 | 源码与资源均无引用的 13 个 `key_*` 资源如何登记: 9 个字体 id 常量 (`key_ace_editor_font_cascadia_code` 等, 只有 `iosevka` 是 `default_key_ace_editor_font` 的目标), 3 个不再提示键 (`key_dialog_foreground_svc`, `key_dialog_log_entries_exported`, 只在注释代码中出现的 `key_dialog_layout_inspector_exit_tips`), `key_enable_observe_key` (附录 A 的 "按键观察", 无任何读写方) | 初稿: 字体 id 为 ValueConstant, 不再提示键为 State, `key_enable_observe_key` 暂按附录 A 记为 `a11y` 的 Setting. 建议: 资源保留并按初稿登记 (老用户偏好文件里可能仍有这些键, R1), `key_enable_observe_key` 改登记为 State (无读写方的键不宜作为可导入的设置); 是否删除资源由维护者定 | 待拍板 (P1.3 前) |
| Q13 | P0.3 初稿与第 5 节 / 附录 A 的描述不一致或需确认的归属: (a) `key_version_history_restore_does_not_auto_save_to_disk`: P2.2 列为 `HistoryKeys` 的设置, 代码中是 `VersionHistoryController` 里 `NotAskAgainDialog` 的不再提示键; (b) `key_color_select_activity_legacy_layout`: P2.5 列在 `LayoutInspectorKeys`, 代码中是 `ColorLibrariesActivity` / `ColorSelectActivity` 打开前写入的界面模式, 用户不直接设置; (c) `PluginUpdateCheckStore` (`key_$_plugin_update_check_$_<包名>`): P4.2 列为状态, 代码中是每个插件的 "检查更新" 开关; (d) `key_foreground_service`: 主应用里是抽屉开关对前台服务运行状态的镜像, 脚本 API `settings` 与 inrt 把它当设置读写; (e) `autojs.node.storage.<包名>.<cwd 摘要>.<存储名>` (`NodeBridgeProtocol` 的 Node.js storage 桥): 不在 1.1 的命名存储清单中 (实测 29 份) | 初稿: (a) State, (b) State, (c) `plugin_center` 的 Setting, (d) State, (e) Excluded 并已补登附录 A.2. 建议按初稿; 若希望跨设备带入 "前台服务" 偏好, (d) 改为 `general` 的 Setting | 待拍板 (P1.3 前) |
| Q14 | JVM 单测类路径上没有真实的 `org.json`: `app` 的测试依赖只有 JUnit4 与 bcprov, 单测里的 `org.json` 来自 android.jar (P0.5 探针: `new JSONObject().put(...)` 抛 `RuntimeException: Stub!`, AGP 的 mockable jar 同样抛异常), 而 R3 / T6 要求编解码只依赖 `org.json`, P1.1 的 `SettingKeyTest` (Json 往返) 与 P6.1 的编解码测试都需要真实实现 | 建议: P1.1 前在 `gradle/libs.versions.toml` 增加 `org.json:json` (本机 Gradle 缓存已有 20231013) 并 `testImplementation`, 与 Android 运行时的 `org.json` 行为差异 (如 `JSONObject.NULL`, 数字类型) 在测试里显式覆盖; P0.5 夹具因此不在运行时使用 `org.json` | 待拍板 (P1.1 前) |

---

## 附录 D 冒烟清单与设备矩阵 (P14.3)

脚本位置: `docs/dev/smoke/settings-transfer/` (`run-settings-smoke.sh <serial>`, 经 adb 运行脚本改设置并调用 P12 的脚本 API 导出, 再用 `tools/settings/archive_inspect.py` 核对; 向导流程由维护者手工点击, 脚本只做前后快照比对). 输出只保留每项通过 / 失败与关键计数, 不打印凭据值.

1. 改主题色, 夜间模式, 编辑器字号, 插件中心排序各一项 -> 导出向导 (默认勾选) -> 恢复默认 (同一组分类) -> 导入向导 -> 四项恢复, 主题色与夜间模式未重启即生效.
2. 导出时勾选 `credentials` 出现警告; 导入同一文件时不勾选 `credentials`, GitHub token 保持本机值.
3. 用 `archive_inspect.py` 把 manifest 的 `formatVersion` 改为 2 后导入: 步骤 1 出现警告, 允许继续, 结果计数正确.
4. 导入含未安装插件的 `plugin_enable_state`: 结果显示 `TARGET_MISSING` 计数, 已安装插件的状态被写入.
5. 导入后 "撤销上次导入": 第 1 项的四项回到导入前的值; 内部备份目录只保留 5 份; 工作目录出现一份 `operation` 备份.
6. 恢复默认 `editor_appearance`: 字号回到默认, 其他分类不变; 工作目录出现一份 `operation` 备份.
7. 打开周期备份 (每日) 后手动触发一次 `WorkManager` 任务: 工作目录出现 `periodic` 备份且不含 `credentials`.
8. 旧版本 (本文基线之前的安装包) 升级到本版: 全部设置保持, 守卫测试在本机通过 (R1).
9. API 24 AVD 上完成第 1 与第 5 项 (SAF 与 `java.time` 用法在低版本可用).
10. 导入一份工作目录指向不存在路径的归档: 部署项确认页出现 "创建工作目录", 同意后目录被创建且工作目录键写入; 放弃后键保持原值.
11. 在未授予 "所有文件访问" 的设备上重复第 10 项: 创建失败, 结果页显示原因与权限引导按钮, 键保持原值.
12. 导入一份字体键指向未安装字体的归档 (Ace 插件已装): 部署项确认页出现 "下载编辑器字体", 同意后进度对话框显示下载进度并应用; 与第 10 项同时存在时两项在同一进度对话框中; Ace 插件未装时确认页先列出 "需要安装 Ace 编辑器插件".
13. 导出时在快捷方式细化页只选 2 条, 颜色库细化页只选 1 个用户库: manifest `data` 条目数对应; 删除本机该快捷方式与颜色库后导入, 两者恢复 (快捷方式缺图标时出现补选).
14. 清除 `key_setup_wizard_welcome_done` 后进入主页: 欢迎条 -> 三个向导依次出现, 设置导入向导可跳过; 设置 > 配置 > 设置向导 的选择器含第三项.
15. 打开变更后备份, 连续改 3 项设置: 5 分钟后工作目录只出现一份 `change` 备份.

设备矩阵 (见 `../COMMON_AGENTS.md` > 可用测试设备; 与其他会话协调占用):

| 设备 | 用途 |
| --- | --- |
| Sony XQ-AT72 (API 31) | 主力 E3 设备 |
| Sony XQ-DQ72 (API 33) 或 Redmi (API 33) | 第二台真机 |
| OnePlus OPD2413 (ColorOS, Android 15) | Q11 与权限弹窗差异, 第 11 项 |
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
| 键声明 | `core/pref/SettingKey.kt`, `StateKey.kt`, `CacheKey.kt`, `SettingCategory.kt`, `SettingEffect.kt`, `DeploymentKind.kt`, `SettingsJson.kt`, `keys/AppearanceKeys.kt`, `GeneralKeys.kt`, `ExplorerKeys.kt`, `HistoryKeys.kt`, `EditorKeys.kt`, `TimedTaskKeys.kt`, `A11yKeys.kt`, `RunningKeys.kt`, `RecordKeys.kt`, `ExtensibilityKeys.kt`, `UpdatesKeys.kt`, `ConsoleKeys.kt`, `ConnectionKeys.kt`, `LayoutInspectorKeys.kt`, `DeveloperKeys.kt`, `PluginCenterKeys.kt`, `CredentialKeys.kt`, `DialogStateKeys.kt`, `MiscStateKeys.kt` |
| 存储 | `core/pref/store/SettingsStore.kt`, `SharedPreferencesStore.kt`, `JsonFileStore.kt`, `StoreId.kt`, `SettingsStores.kt` |
| 注册表 | `core/pref/SettingsRegistry.kt`, `SettingDescriptor.kt` |
| 快照与归档 | `core/pref/archive/SettingsSnapshot.kt`, `SettingsSnapshotCodec.kt`, `SettingsArchiveManifest.kt`, `SettingsArchiveWriter.kt`, `SettingsArchiveReader.kt`, `SettingsKeyMigrations.kt` |
| 导入与备份 | `core/pref/archive/SettingsImportPlanner.kt`, `ImportPlan.kt`, `SettingsImportApplier.kt`, `SettingsEffects.kt`, `SettingsBackupVault.kt`, `SettingsAutoBackup.kt`, `SettingsPeriodicBackupWorker.kt`, `SettingsChangeBackupScheduler.kt`, `SettingsBackupRetention.kt` |
| 部署项 | `core/pref/deploy/DeploymentItem.kt`, `DeploymentKind.kt` (同上), `DeploymentPlanner.kt`, `CreateWorkingDirectoryItem.kt`, `DownloadEditorFontItem.kt` |
| 数据分类 | `core/pref/data/ShortcutDataCodec.kt`, `ColorLibraryDataCodec.kt` (调用 `ui/shortcut/` 与 `theme/library/ColorLibraryJson.kt`) |
| 页面与向导 | `ui/settings/transfer/SettingsTransferActivity.kt`, `SettingsTransferFragment.kt`, `SettingsTransferPreference.kt`, `SettingsWizardState.kt`, `SettingsExportWizard.kt`, `SettingsImportWizard.kt`, `SettingsCategoryPickerStep.kt`, `DataDetailStep.kt`, `DeploymentConfirmStep.kt`, `DeploymentProgressDialog.kt`, `SettingsTransferResultStep.kt`, `res/layout/dialog_settings_category_picker.xml`, `res/layout/dialog_deployment_progress.xml`, `res/xml/fragment_preferences.xml` (入口), `ui/wizard/SetupWizards.kt` (第三个 Kind) |
| 脚本 API | `runtime/api/augment/settings/Settings.kt` |
| 工具 | `tools/settings/inventory.py`, `archive_inspect.py`, `baseline/`, `classification.json` |
| 单测 | `app/src/test/java/org/autojs/autojs/core/pref/` (`SettingKeyTest`, `SettingsRegistryGuardTest`, `SettingsSnapshotCodecTest`, `SettingsArchiveTest`, `SettingsArchiveReaderTest`, `SettingsImportPlannerTest`, `SettingsBackupVaultTest`, `SettingsBackupRetentionTest`, `SettingsChangeDebounceTest`, `DeploymentPlannerTest`, `InMemorySettingsStore`) |
| 冒烟 | `docs/dev/smoke/settings-transfer/` |
| 文档 | `docs/settings/ROADMAP.md` (本文), `inventory.md`, `defaults.md`, `archive-format-v1.md`, `docs/dev/plugin-settings-transfer-protocol-v1.md` (草案) |

---

## 附录 G 基线数据 (2026-10-20, `master@4a16be478`)

| 指标 | 数值 | 来源 | P0.2 实测 (`inventory.py`, `master@665c35482`) |
| --- | --- | --- | --- |
| `key_*` 字符串资源 | 265 | `strings_donottranslate.xml` | 267 (另 2 个在 `ace_editor_language_keys.xml`) |
| 出现在设置页 XML 的键 | 105 (39 + 40 + 11 + 9 + 5, 去重后) | 5 个 `fragment_*.xml` | `key_*` 98 (39 + 40 + 10 + 9 + 3, 去重后) + 不经资源的字面量键 3 |
| 只在代码中使用的键 | 162 | 上两项之差 | 169 (其中源码与资源均无引用 13, 见 Q12) |
| 经 `Pref` 读写的源文件 | 122 | grep `Pref.` | 119 (不含两个 `Pref.kt`) |
| 绕过 `Pref` 读默认偏好的源文件 | 8 | grep `PreferenceManager.getDefaultSharedPreferences` | 8 |
| 调用 `getSharedPreferences(` 的源文件 / 命名存储数 | 34 / 约 27 | grep | 34 / 29 (全部解析出文件名或模式) |
| `*Store` / `*Prefs` / `*Preferences` / `*Settings` / `*Config*` 类 | 约 80, 分布 40 个目录 | find | (未测) |
| 序列化方式 | `JSONObject` 11, Gson 1, 逗号拼接 5, `putStringSet` 7 | grep | (未测) |
| 文件型 JSON 存储 | 14 | grep `filesDir` + `.json` | 启发式命中 13, 加 `ModelStore` (目录在工厂类中拼接) 共 14 |
| 单测环境 | JUnit4 (`libs.junit`), 无 Robolectric | `app/build.gradle.kts` | 同左; 测试类路径上没有真实的 `org.json` 实现 (android.jar 中为桩, 见 Q14) |

P14.3 复测时在本表追加一列, 预期: 绕过文件 0, `getSharedPreferences(` 调用只剩 `SharedPreferencesStore`, 序列化方式只剩 `SettingsJson`, 守卫 "待迁移" 0.
