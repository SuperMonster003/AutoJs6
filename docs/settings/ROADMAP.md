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
| T6 | 快照 `SettingsSnapshot` = 分类 -> 存储 -> 键 -> `TypedValue(type, value)`; 类型标签 `bool` / `int` / `long` / `float` / `string` / `stringSet` / `json`; 编解码 `SettingsSnapshotCodec` 基于 `org.json`, 不用 Gson (JVM 单测经 `testImplementation org.json:json` 取得真实实现, 附录 C Q14); "其他" 分类的未登记键按 `SharedPreferences.getAll()` 的运行时类型打标签; 数据分类 (T19) 不进快照, 由各自的数据编解码直接写归档 | P6.1 结束 |
| T7 | 归档 `SettingsArchiveWriter` / `SettingsArchiveReader` 用 `java.util.zip`, 读取时先只解 `manifest.json` 列出分类与条目数, 用户勾选后再解对应 `categories/<id>.json` 或 `data/*`; 单个 zip 上限 16 MB, 超出拒绝 | P6.1 结束 |
| T8 | 导入计划 `SettingsImportPlanner` 为纯函数: 输入注册表描述, 归档快照, 勾选分类, 已安装插件包名集合, 本机环境探测结果 (目录是否存在, 字体是否已安装, Ace 插件是否安装); 输出 `ImportPlan` (`puts`, `removes`, `skips` 按原因 `UNKNOWN_KEY` / `TYPE_MISMATCH` / `INVALID_VALUE` / `TARGET_MISSING` / `NEWER_FORMAT` / `DECLINED`, `deployments` 待确认部署项列表); 带 `deployment` 的键在环境不满足时不直接跳过, 而是进入 `deployments` 等用户确认 (T18); 应用 `SettingsImportApplier` 对每个存储一次 `commit`, 之后 `SettingsEffects.run(changedKeys)` 按 `effect` 分组执行 | P7.2 结束 |
| T9 | 内部备份库 `SettingsBackupVault` 位于 `filesDir/settings-backups/`: `auto-import-<ts>.zip` 与 `auto-reset-<ts>.zip` 共保留 5 份 (含凭据与其他分类, 私有目录, 只服务撤销); `last-operation.json` 记录触及分类与时间; "撤销上次导入" = 以最近备份对触及分类再做一次导入 (同样走 T8, 无部署项), 撤销本身不再写内部备份, 但触发 "操作后备份" (T20) | P7.3 结束 |
| T10 | 页面与向导 (D4 / D19): `ui/settings/transfer/SettingsTransferActivity` + `SettingsTransferFragment` (`PreferenceFragmentCompat`, 同 `ShortcutBackupFragment` 写法) 为 "管理 AutoJs6 设置" 页面. 导出向导 `SettingsExportWizard`: 第 1 步选择分类 (`SettingsCategoryPickerStep`: 复选列表, 每项副标题 "N 项", 底部 "全选" / "全不选" / "反选", 与 IDEA 一致; 勾选 `credentials` 时弹 D2 警告; 数据分类旁有 "细化..." 进入 T19 的细化页) -> 第 2 步选择保存位置 (SAF) -> 完成页 (文件名, 分类数). 导入向导 `SettingsImportWizard`: 第 1 步选择文件 (SAF) 并显示 manifest 摘要 (来源宿主版本, 设备, 导出时间, 更高 `formatVersion` 的警告) -> 第 2 步选择分类 (只列文件中存在的, 复用同一步骤) -> 第 3 步确认部署项 (T18, 没有则跳过此步) -> 第 4 步执行 (内部备份, 应用, 部署进度对话框) -> 结果页 (写入 / 恢复默认 / 跳过按原因 / 部署成功与失败计数, 失败原因与权限引导, "立即重启"). 每一步可 "上一步" / "跳过" / "取消"; 对话框与等待用 `WizardUi.showAndAwait` / `launchAndAwaitReturn`; 向导状态对象 `SettingsWizardState` 为纯数据, 旋转屏幕后可恢复 | P6.2 结束 |
| T11 | SAF: `CreateDocument("application/zip")` / `OpenDocument()` (MIME `application/zip`, `application/octet-stream`); 默认文件名 D5 (全小写连字符); 导出成功后完成页显示文件名 | 不可推翻 |
| T12 | 生效表 `SettingsEffects`: `APPLY` 键各自绑定现有入口 (主题色 -> `ThemeColorManager.setThemeColor`; 夜间模式 -> `ViewUtils.setDefaultNightMode`; 语言 -> `LocaleUtils.setLocale` / `setFollowSystem`; 启动图标 -> `LauncherIconPreference` 抽出的组件切换逻辑; 定时任务后端 -> 现有重排入口; 前台服务 -> 抽屉开关复用的启动 / 停止入口, 需要通知权限, 失败记入结果页, 附录 C Q13); 未绑定入口的 `APPLY` 键在守卫测试中报错 | P7.4 结束 |
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
- [x] P0.2 (工具) `tools/settings/inventory.py`: 扫描 `strings_donottranslate.xml` 的 `key_*`, 5 个设置页 XML 的出现位置, 源码引用位置 (文件与行), `getSharedPreferences(` 的文件名字面量与常量, `filesDir` 下的 JSON 文件名; 输出 `tools/settings/baseline/inventory-<date>.json` 与可读的 `docs/settings/inventory.md`. 证据: 提交 `d6b97fbf6` (子会话 roadmap-p0).
- [x] P0.3 (文档) 分类初稿: 对 265 个 `key_*` 与约 27 个命名存储逐一标注 kind (Setting / State / Cache / ValueConstant / Excluded) 与分类 id (含 `deployment` 种类), 回填附录 A 的条目数; 无法判断的进附录 C (Q12 起). 证据: 提交 `06a331f28`; `tools/settings/classification.json` 覆盖 267 个 `key_*` (Setting 123 / State 40 / ValueConstant 74 / Excluded 29 / Cache 1), 71 组源码字面量键, 29 份命名存储, 13 个 filesDir JSON 存储与 3 个页面字面量键, `py -3.12 -I tools/settings/inventory.py --check` 0 问题; `docs/settings/inventory.md` 的 kind 列无空白, 第 7 节为各分类条目数; 部署项种类 `CREATE_DIRECTORY` (`key_working_directory`) 与 `DOWNLOAD_EDITOR_FONT` (`key_ace_editor_font`); 附录 A 条目数已回填 (计数规则见 `inventory.md` 第 7 节), 新登记 Q12 / Q13 (子会话 roadmap-p0).
- [x] P0.4 (文档) 默认值表: 每个 Setting 的默认值来源 (`R.bool` / `R.integer` / `R.string` / 伴生常量 / 调用处字面量) 与取值, 同一键多处不一致的列入附录 C Q10. 证据: 提交 `06a331f28`; `docs/settings/defaults.md` 列出 135 个 Setting (123 个 `key_*` + 12 组默认偏好字面量键) 的 218 条来源, 全部有来源 (其中 46 条为 `classification.json` 的 `extraDefaults` 人工补充), 3 处不一致已登记在附录 C Q10 (子会话 roadmap-p0).
- [x] P0.5 (测试) JVM 夹具 `InMemorySettingsStore` 与 `SettingDescriptor` 构造辅助, 放在 `app/src/test/java/org/autojs/autojs/core/pref/`. 证据: 提交 `f48cb6681`; `InMemorySettingsStore` (T3 成员, SharedPreferences 同款类型与提交 / 监听语义, `commitCount`) 与 `SettingDescriptorFixtures` (`SettingDescriptorSpec` 含 T4 的 8 个字段, 校验 T6 类型 / D10 生效 / T2 kind / T18 部署项); `InMemorySettingsStoreTest` 11 例 + `SettingDescriptorFixturesTest` 8 例, 先在 scratchpad 用 kotlinc 1.7.10 + JUnit 4.13.2 跑通, 放入后 `:app:testAppDebugUnitTest --tests "org.autojs.autojs.core.pref.*"` 19 例 0 失败 (E1); 夹具不在运行时使用 `org.json` (测试类路径只有 android.jar 桩, 见 Q14) (子会话 roadmap-p0).

### P1 新基座 (`core/pref`)

- [x] P1.0 (构建 / 清单) `gradle/libs.versions.toml` 增加 `org.json:json` 20231013 并在 `app` 以 `testImplementation` 引入 (Q14); 按 Q10 / Q12 / Q13 更新 `tools/settings/classification.json` (`key_enable_observe_key`, `key_version_history_restore_does_not_auto_save_to_disk`, `key_color_select_activity_legacy_layout` 为 State; `key_foreground_service` 为 `general` 的 Setting, `effect = APPLY`; `PluginUpdateCheckStore` 为 `plugin_center` 的 Setting; 4 类新排除) 并重新生成 `inventory.md` / `defaults.md`, 核对附录 A 条目数 (`general` 6, `a11y` 5). 证据: 提交 `3a89eeb7f`; `org.json:json` 20231013 只在 `app` 的 `testImplementation`, `OrgJsonProbeTest` 2 例确认 JVM 测试加载的是 `json-20231013` 构件 (E1); `classification.json`: `key_enable_observe_key` 改为 State (Q12), `key_foreground_service` 改为 `general` 的 Setting 且 `effect = APPLY` (默认值 false 来自抽屉开关与 `settings.isEnabled`, 补进 `extraDefaults`), `key_version_history_restore_does_not_auto_save_to_disk` / `key_color_select_activity_legacy_layout` 已是 State, `PluginUpdateCheckStore` 已是 `plugin_center` 的 Setting, 4 类排除已是 Excluded (注释改为引用 Q13 拍板); `inventory.md` / `defaults.md` 重新生成, `--check` 0 问题, 第 7 节 `general` 6 / `a11y` 5 / `other` 51 与附录 A 一致 (子会话 roadmap-p1).
- [x] P1.1 (宿主) `SettingKey` / `StateKey` / `CacheKey` / `SettingCategory` / `SettingEffect` / `DeploymentKind` / `SettingsJson` (T1 / T2), 不接入任何调用方. 证据: 提交 `94c0b0d1e`; `SettingKey` 的 8 种值类型, `StateKey` / `CacheKey` (同模型, 工厂函数 `bool` / `int` / ...), `SettingType` (存储形式与 T6 归档形式), `SettingDefault` (常量 / 每次读取的计算值 / 无), `SettingKeyNames` (`StringUtils.key` 惰性解析), `SettingCategory` (附录 A.1 的 31 项), `SettingEffect`, `SettingKind`, `DeploymentKind`, `SettingsJson`; `StoreId` 因键声明需要提前在本条加入; `SettingKeyTest` 12 例 + `SettingsJsonTest` 11 例 (含 `JSONObject.NULL`, Int / Long / Double 收窄与 `-0`, 严格语法, 字符串列表与 Gson 逐字节一致) 通过 (E1); 尚无调用方 (子会话 roadmap-p1).
- [x] P1.2 (宿主) `SettingsStore` / `SharedPreferencesStore` / `JsonFileStore` / `StoreId` / `SettingsStores` (T3, 含变化事件供 T20 使用). 证据: 提交 `1f8ec3d1e`; `SettingsStore` (类型化键读写为扩展函数), `SharedPreferencesStore`, `JsonFileStore` (条目 `{"t", "v"}`, 临时文件改名替换), `SettingsStores` (`addChangeListener` 变化事件), `InMemorySettingsStore` 改为实现 `SettingsStore`; `JsonFileStoreTest` 10 例 + `SettingsStoresTest` 7 例通过 (E1); 真机 Sony G8441 (API 28) 上 `py -3.12 -I tools/settings/device_store_check.py -s <serial> interop` 14 项全部通过: 默认与命名存储, 六种值类型双向读写, 写入同一 XML 文件, 类型不符时与 `Pref` 同样抛异常, 变化事件 (E3) (子会话 roadmap-p1).
- [x] P1.3 (宿主) `SettingsRegistry` 骨架与 `describe()` (T4); 守卫测试 (T5 a / b / c / e) 以 P0.3 的分类初稿为初始允许清单, 未迁移的键先以 "待迁移" 登记, 测试只要求不出现未登记项. 证据: 提交 `675978b69`; `SettingDescriptor`, `SettingsRegistry` (`DOMAINS` 为空, P2.1 起填充; `DEPLOYMENT_IMPLEMENTATIONS` 待 P7.6), `RegisteredName`, `SettingsDomain`; `SettingsRegistryGuardTest` 5 例通过 (E1), 待迁移计数: `key_*` 267 (Setting 123 / State 40 / ValueConstant 74 / Excluded 29 / Cache 1), 命名存储 29 (34 个文件 60 处 `getSharedPreferences(`), 部署项种类 2 (待 P7.6 实现); 声明与 `classification.json` 的 kind / 分类 / 部署项 / 生效方式不一致时失败; `inventory.py` 豁免 `core/pref/store/` 存储层 (子会话 roadmap-p1).
- [x] P1.4 (宿主) `core/pref/Pref` 的通用读写 (`getString` / `putBoolean` / `getStringList` 等) 改为经 `SettingsStores.default` (行为不变, Gson 列表序列化改为 `SettingsJson` 并保证旧值可读). 证据: 提交 `22a4c0133`; `Pref` 的通用读写经 `SettingsStores.default` (同一个 `PreferenceManager` 的 SharedPreferences, `apply()` / `commit()` 时机不变), 列表改为 `SettingsJson.encodeStringList` / `decodeStringList`, 写出文本与 Gson 逐字节一致; 真机 Sony G8441 (API 28): 旧 `Pref` (Gson) 的包写入 `key_working_directory_history` / `key_pc_server_address_history` / `key_ignored_updates` 与探针列表 (`lists-before`), 覆盖安装本条的包后 `lists-after` 20 项全部通过 (原始文本未变, 解码结果, `WorkingDirectoryUtils.history` / `JsonSocketClient.serverAddressHistory` 与 Gson 一致, 新 `Pref` 重写后文本不变), 同包 `interop` 14 项通过 (E3); `core.pref` 单测 66 例 0 失败 (子会话 roadmap-p1).

### P2 默认偏好迁移 (每条: 声明键 -> 替换读写 -> 旧 getter `@Deprecated` 转发 -> 单测 -> 守卫计数下降)

- [x] P2.1 外观与配置: `AppearanceKeys` (语言, 夜间模式与自动夜间, 主题色三项, 前台常亮), `GeneralKeys` (文档来源, 重启策略与计划重启后端 / 延迟, 启动图标, 启动器快捷方式, 前台服务 [`key_foreground_service`, `APPLY`, Q13], 自动备份三个开关与周期选项占位); 迁移 `Language`, `ThemeColorManager`, `ViewUtils` 夜间模式, `LauncherIconPreference`, 抽屉前台服务开关的读写. 证据 (单测 + E3): 提交 `bf31a586b`; `AppearanceKeys` 9 键, `GeneralKeys` 6 键 (启动器快捷方式是动作条目, 以 Excluded 名称登记; 自动备份三开关与周期选项的键随 P9 加入), 二者为 `SettingsRegistry.DOMAINS` 的前两个域; 文档来源, 计划重启对话框, `IntentUtils`, 抽屉开关等读写方也改走键, 对应的 `Pref` getter 标 `@Deprecated` 转发; `PrefKey` 增加 `get` / `set` / `storedOrNull` / `isStored` / `remove`, `Enum` 键增加 `storedKey` / `storedKeyOrDefault` / `setStoredKey` (保留原先按原始字符串比较的读法, R2); `SettingResources` 让 JVM 单测从 `res/values` 读计算默认值; 单测 `core.pref` 80 例 0 失败 (`AppearanceKeysTest`, `GeneralKeysTest`, `SettingsDomainsTest` 按 `tools/settings/baseline/defaults-p0.json` 核对声明的类型与默认值); 守卫 T5 a: 已登记 45, 待迁移 222 (Setting 108); E3: G8441 (API 28) `device_store_check.py domains` 39 项 PASS (子会话 roadmap-p2).
- [x] P2.2 文件管理器与回收站: `ExplorerKeys` (工作目录 [`deployment = CREATE_DIRECTORY`], 隐藏文件, 扩展名, 回放历史开关, 新文件默认前缀, 批量重命名历史保留), `HistoryKeys` (9 个保留策略键, 永久删除同时清历史; "恢复不自动落盘" 是不再提示键, 归 P4.1); `HistoryPrefs` / `RetentionLimitSettings` 并入. 证据 (`storage/history` 既有单测仍过 + E3): 提交 `438c9fe12`; `ExplorerKeys` 6 键, `HistoryKeys` 10 键; `HistoryPrefs` 与 `BatchRenameHistoryPrefs` 的成员移入域对象, 旧对象转发; `RetentionLimitSettings`, 文件管理器视图与菜单, `ScriptOperations`, `WorkingDirectoryUtils`, 回收站 / 版本历史页面与抽屉副标题改走键 (`PrefRx.observeLong` 接收声明的键); `inventory.py` 读取声明的默认值参数; 单测 `core.pref` 88 例, `storage.history` 73 例, `batchrename` 46 例, 均 0 失败 (新增 `ExplorerKeysTest`, `HistoryKeysTest`); 守卫 T5 a: 已登记 69, 待迁移 199 (Setting 93, 含 Q16 的 `key_leakcanary_enabled`); `inventory.py --check` 0 问题; E3: G8441 (API 28) `domains` 77 项 PASS, "回收站与版本历史设置" 页截图正常 (`page` 模式) (子会话 roadmap-p2).
- [x] P2.3 代码编辑器 8 个分类: `EditorKeys` (常规, 外观 [`key_ace_editor_font` 的 `deployment = DOWNLOAD_EDITOR_FONT`], 日志, 辅助, 智能提示, 操作手势, 边栏标记, 性能, 含 `key_editor_log_panel_*` 等代码键); `EditorEnginePreferences` / `EditorThresholdSettings` / `AceEditorFontUiPreferences` (默认偏好部分) 并入; `CodeEditor` 的字号初值改读 `R.integer.editor_text_size_default_value` (Q10, fix changelog). 证据 (单测 + E3: 两种编辑器引擎各开一次, 原生编辑器未设置字号时显示 14): 提交 `559212489`; `EditorKeys` 49 键 (8 个分类, `key_ace_editor_font` 带 `DOWNLOAD_EDITOR_FONT`); `EditorEnginePreferences` / `EditorThresholdSettings` 的默认偏好部分与 `Pref` 的阈值读取移入 (`AceEditorFontUiPreferences` 只用命名存储, 没有默认偏好部分, 留给 P3.2; 默认偏好中的字体读写在 `AceEditorFontPreferences`, 已改走键); `SettingsStore.Editor` 支持按键写入, 引擎与选区模式仍是一次写入; Q10 (1) 落地并记 fix changelog (10 种语言); 单测 `core.pref` + `ui.edit` 255 例 0 失败 (新增 `EditorKeysTest`); 守卫 T5 a: 已登记 150, 待迁移 118 (Setting 50); `inventory.py --check` 0 问题, 默认值冲突只剩 Q10 (2) / (3) 两条 (P2.4 落地); E3: OnePlus OPD2413 (API 35, ColorOS) `domains` 164 项 PASS (`EditorKeys` 49 / 49 与旧读法一致), `editor` 模式: 未存字号时新建原生编辑器为 14 sp, 原生与 Ace 两种引擎各打开一次示例脚本并截图, 之后恢复引擎, 选区模式与字号 (子会话 roadmap-p2).
- [x] P2.4 定时任务, 无障碍, 脚本运行, 录制, 扩展性, 更新: `TimedTaskKeys`, `A11yKeys` (`key_enable_observe_key` 归 P4.2, Q12), `RunningKeys` (含 `key_root_manager_apps_custom`), `RecordKeys`, `ExtensibilityKeys`, `UpdatesKeys` (自动检查, 忽略本机版本检查; 时间戳归 P4); 脚本 API `runtime/api/augment/settings/Settings.kt` 的 `isEnabled` 缺键时改读注册表默认值 (Q10, fix changelog). 证据 (单测: `Settings.isEnabled` 对 Root 启动无障碍与音量键停止脚本缺键时返回 true; E3): 提交 `ed4674afe`; `TimedTaskKeys` 1 键 (`APPLY`), `A11yKeys` 5 键, `RunningKeys` 6 键 (含 `key_root_manager_apps_custom` 与缓存 `key_root_manager_apps_cache`), `RecordKeys` 3 键, `ExtensibilityKeys` 2 键, `UpdatesKeys` 2 键 (忽略的版本为兼容 Gson 的 JSON 列表); 对应 `Pref` 成员 `@Deprecated` 转发 (`ScriptRuntime` 与新脚本模块层 `runtime/api/modules/ScriptModuleLayer.kt` 属 Augment 重构正在改的文件, 暂留旧读法); Q10 (2) / (3) 落地: `settings.isEnabled` 缺键时按 `SettingsRegistry.find` 取声明的默认值, 没有 Boolean 声明的名称仍为 false, 记 fix changelog (10 种语言); 单测 `core.pref` 100 例, `runtime.api.augment.settings` 4 例 (`SettingsTest` 含缺键返回声明默认值的用例), `ui.edit` 161 例, 均 0 失败 (新增 `RuntimeDomainsKeysTest`); 守卫 T5 a: 已登记 182, 待迁移 86 (Setting 32); `inventory.py --check` 0 问题 0 冲突; E3: OnePlus OPD2413 (API 35) `domains` 225 项 PASS, 含 `settings-isEnabled-missing-keys` (子会话 roadmap-p2).
- [x] P2.5 日志与控制台, 连接, 布局分析器, 开发者选项: `ConsoleKeys` (`key_log_activity_*`, `key_console_element_*`, `key_global_console_element_*`, `key_floating_console_*`), `ConnectionKeys` (服务端地址, 客户端 / 服务端模式各开关), `LayoutInspectorKeys` (筛选配置档与选中项, 节点复制模式, 查询模式, 代码生成当前模型; "颜色选择旧布局" 是界面模式镜像, 归 P4.2), `DeveloperKeys` (新脚本模块层, 调试打印与模块列表, Node.js inspector). 证据 (单测 + E3): 提交 `0357eed9a`; `ConsoleKeys` 12 键, `ConnectionKeys` 7 键 (`DevPluginPrefs` 标 `@Deprecated` 转发), `LayoutInspectorKeys` 5 键, `DeveloperKeys` 5 键 (含 Q16 的 `key_leakcanary_enabled`); `ConsoleTimeSettings` 按控制台类型绑定声明的键, `LogPageConsoleSettings` 接收输入栏键, 连接管理器开关与布局分析器的存储改走键; 单测 `core.pref` 104 例, `runtime.api.augment.settings` 4 例, `core.console` 46 例, `ui.log` 9 例, `ui.floating` 28 例, 均 0 失败 (新增 `ToolingDomainsKeysTest`); 守卫 T5 a: 已登记 212, 待迁移 56 (Setting 3, 即 P2.6 的插件中心三键); `inventory.py --check` 0 问题 0 冲突; E3: OnePlus OPD2413 (API 35) `domains` 287 项 PASS (15 个域 128 键), "开发者选项" 页截图正常; 设备脚本另把结果写入设备上的结果文件 (ColorOS 会丢弃繁忙进程的日志行) (子会话 roadmap-p2).
- [x] P2.6 收口: 8 个绕过文件改为 `SettingKey` (其中 `NotAskAgainDialog` 归 P4.1); `inrt/Pref` 底层换 `SharedPreferencesStore` (T13); 守卫测试 "待迁移" 计数为 0 (默认偏好部分). 证据 (`SettingsRegistryGuardTest` 输出): 提交 `bae7b1c39`; `PluginCenterKeys` 3 键 (`PluginOnlineSettingsStore` 改读); 7 个绕过文件改走声明的键或 `SettingsStores.default`: `RootAutomatorEngine`, `FindOrReplaceDialogBuilder`, `CodeEvaluateDialogBuilder`, `NodeJsRuntimePluginBridge` 用键, `Shell` (`TermSettings` 需要 `SharedPreferences`) 与 `CodegenModelStoreFactory` (模型选择须 commit 落盘) 取默认存储的 `preferences`, `inrt/Pref` 底层换为 `SettingsStores.default` (T13, 键, 默认值与写入时机不变); 绕过文件 8 -> 1 (`NotAskAgainDialog`, P4.1); 这些读取方的 4 个字面量键在 `MiscStateKeys` 声明, 名称不变 (R1, Q17); 单测 `core.pref` 107 例, `core.plugin.center` 182 例, `runtime.api.augment.settings` 4 例, `ui.edit` 161 例, 均 0 失败 (新增 `MiscStateKeysTest`); `SettingsRegistryGuardTest` T5 a: `key_*` 资源 268, 已登记 215, 待迁移 53 (Excluded 13, State 40; Setting 0), T5 b 命名存储待迁移 29 (P3); `inventory.py --check` 0 问题 0 冲突, 源码字面量键 75 组 (基线 `tools/settings/baseline/inventory-2026-10-21.json`); E3: OnePlus OPD2413 (API 35, 宿主 versionCode 5679) `domains` 303 项 PASS (17 个域 135 键, 含 `plugin-center-online-settings` 与 `inrt-pref-through-the-store`) (子会话 roadmap-p2).

### P3 命名存储迁移 (每条: 登记 `StoreId` -> 类内部改为 `SettingsStore` + `Json` 键 -> 单测)

- [x] P3.1 插件中心 13 个 `*Store`: `plugin_center_sort_state` 三个类共用一份存储的情况在登记时写明; `PluginMountStore` 的默认偏好前缀键登记为 `Json` 键集合; `PluginUpdateCheckStore` (每个插件的 "检查更新" 开关, 前缀键) 登记为 `plugin_center` 的 Setting (Q13); 启用状态 (`plugin_center_enable_state`) 单独分类 `plugin_enable_state`. 证据: 插件中心既有单测仍过 + E3 (排序 / 隐藏 / 挂载各改一项后重启核对). 实际: 提交 `3353c3097`; `NamedStores` 登记插件中心 7 份命名存储 (`plugin_center_filter_state`, `plugin_center_sort_state`, `plugin_center_visibility_state`, `plugin_launcher_icons`, `plugin_center_enable_state`, `plugin_center_recent`, `plugin_center_attention`; 名称不变, R1), `plugin_center_sort_state` 由 `PluginSortStore` / `PluginSortStoreOfficial` / `PluginSortStoreThirdParty` 共用一份, 在 `NamedStores` 与 `PluginCenterKeys.PluginSortKeys` 的说明中写明; `PluginCenterKeys` 3 -> 23 个键 + 18 个键族: 筛选 6 键, 三个列表各 3 键 + 2 个键族 (方向, 次排序), 隐藏的包名 + 标题 / 来源键族, 启动图标 (全名称键族, JSON 对象), 启用状态与挂起两个键族 (分类 `plugin_enable_state`), 最近安装 / 卸载时间两个键族与临时置顶 3 键 (State, `other`); 默认偏好中的前缀键: `PluginMountStore` 登记为包名列表 (StrSet) + 开关 / 对象名 / 挂载点三个键族 (值类型与存储形式保持 Bool / Str 不变, 不改为 Json, R1), `PluginUpdateCheckStore` 的每插件 "检查更新" 开关为 `plugin_center` 的 Setting 键族 (Q13 c), `PluginVariantPriorityStore` 为 Json 键族; 13 个类 (`PluginOnlineSettingsStore` 已在 P2.6 完成) 改走键: 原先自带回退值的读取方保留回退值, 一次 edit 的写入仍是一次写入, org.json 写出的文本仍由原代码读写 (`PrefKey.storedText` / `setStoredText`), 监听器仍注册在同一个 SharedPreferences 上 (R2); `classification.json` 的 4 个通配名称改为键族前缀, 新增临时置顶的 3 个 State; 单测 `core.pref` 125 例 (新增 `PluginCenterStoresTest` 6 例), `core.plugin.center` 182 例 (`PluginSortStoreTest` 改用内存存储, 断言不变), `core.plugin.agent` 44 例, `core.plugin.mcp` 24 例, 均 0 失败; 守卫 T5 b: 已登记 20 (Excluded 8, Setting 10, State 2), 待迁移 16 -> 9 (Setting 8, State 1, 即 P3.2 / P3.3); T5 a 不变 (Setting 待迁移 0), T5 c 通过; `inventory.py --check` 0 问题 0 冲突, 附录 A 计数不变 (`plugin_center` 14, `plugin_enable_state` 1, `other` 60); E3: OnePlus OPD2413 (API 35, 宿主 versionCode 5679) `device_store_check.py stores --groups plugin_center,credentials` 23 项 PASS: 7 份插件中心存储与 5 份凭据存储中已有的 33 个条目逐条经声明的键读到与原始 SharedPreferences 相同的值 (只输出计数), 全部读写方与原始值一致; 排序 (全部插件列表主排序), 隐藏 (探针包名与标题), 挂载 (探针开关) 各改一项 -> 强制停止 -> 重启后三项保持 -> 7 个原始值恢复 (含自动挂载可能写入的探针启用键) (子会话 settings-p3).
- [x] P3.2 编辑器键盘与字体: `editor_symbols_config`, `FunctionsKeyboardHelper`, `ace_editor_font_ui_preferences`, `ace_action_history` (State). 证据: 单测 + E3. 实际: 提交 `6a907be21` (E3 用的设备组随 P3.3 的 `3afa72055` 补齐); `NamedStores` 登记 4 份存储 (`editor_symbols_config`, `FunctionsKeyboardHelper`, `ace_editor_font_ui_preferences`, `ace_action_history`; 名称不变, R1), 新增 `EditorKeyboardKeys` (`SettingsRegistry.DOMAINS` 中排在 `EditorKeys` 之后): 当前符号配置档 (Str, 默认 `__default__`) 与配置档 (Json 对象, org.json 文本), 字体对话框的排序方式, 名称 / 大小两个类型锁定, 预览排除的特性 / 类型 (StrSet), 每个字体组选中的变体 (键族), Ace 动作历史 (State, `other`), `FunctionsKeyboardHelper` 的 `soft_input_height` 按附录 C Q19 的建议登记为 Cache (本机测得的软键盘高度, 不导出; 已先行实施, 待维护者确认), 附录 A `editor_keyboard` 3 -> 2 份; `SymbolsConfigStore`, `AceEditorFontUiPreferences` (P2.3 留下的命名存储部分), `AceActionHistoryStore`, `FunctionsKeyboardHelper` 改走键 (回退值, apply 时机, org.json 文本的读写方式不变, R2); 单测 `core.pref` 129 例 (新增 `EditorKeyboardKeysTest` 4 例), `ui.edit` 161 例 (`AceEditorFontUiPreferencesTest` 等), 均 0 失败; 守卫 T5 b: 已登记 24 (Cache 1, Excluded 8, Setting 12, State 3), 待迁移 9 -> 5; T5 a 不变 (Setting 待迁移 0); `inventory.py --check` 0 问题 0 冲突; E3 (与 P3.3 同一个包, 宿主 versionCode 5679): `device_store_check.py stores --groups ...,editor_keyboard,...` 在 OnePlus OPD2413 (API 35) 与 Sony G8441 (API 28) 上: 4 份存储中已有的条目 (OnePlus 3 个, G8441 2 个) 经声明的键读到与原始值相同的值, 读写方一致; 字体排序方式与一个探针字体组的选中变体各改一项 -> 强制停止 -> 重启后保持 -> 原始值恢复, 两台均 PASS (子会话 settings-p3).
- [x] P3.3 配置档类: `build_apk_config_profiles` (`BuildConfigStore`), `permission_wizard` / `plugin_install_wizard` (`WizardProfileStore`, 自定义配置档为 Setting, 最近使用的配置档 id 为 State), `jvm_source_provider_selection`, `UpdateIgnoreStore`. 证据: 单测 + E3. 实际: 提交 `3afa72055`; `NamedStores` 登记 5 份存储 (`build_apk_config_profiles`, `permission_wizard`, `plugin_install_wizard`, `jvm_source_provider_selection`, 以及 `UpdateIgnoreStore` 的存储, 文件名为 `R.string.key_ignored_updates` 的值 `key_$_ignored_updates`, `NamedStoresTest` 核对; 名称不变, R1), 至此宿主全部 29 份命名存储都已登记; 新增 `ApkBuildKeys` (当前配置档 Str 默认 `__default__`, 配置档 Json 对象) 与 `WizardKeys` (两个向导各: 自定义配置档 Json 数组 = Setting, "显示已完成条目" = Setting, 最近使用的配置档 id = State; 插件安装向导另有 `show_launcher_icons_for_new_plugins`), JVM 源码提供方选择 (Java / Kotlin 两键) 并入 `PluginCenterKeys`, 插件被忽略的版本 (键族 `key_$_ignored_plugin_$_`) 并入 `UpdatesKeys`; `BuildConfigStore`, `WizardProfileStore` (构造参数改为 `WizardKeys.WizardProfileKeys`), `PermissionWizardDialog`, `PluginInstallWizardProfileStore`, `JvmSourceProviderSelection` (仍在该存储的 SharedPreferences 上 commit 并检查结果), `UpdateIgnoreStore` 改走键 (R2); 真机核对发现 `plugin_install_wizard` 中残留已不再读写的 `key_$_plugin_install_wizard_first_run_shown`, 按 Q13 先例登记为 Excluded (附录 C Q20); 另按 Q16 先例登记内存路线图新增的 `key_memory_unreclaimed_warning` (`DeveloperKeys`, 附录 C Q21, 附录 A `developer` 9 -> 10); 单测 `core.pref` 135 例 (新增 `ProfileStoresTest` 5 例, `NamedStoresTest` 3 例), `ui.wizard` 19 例, `core.plugin.jvm` 110 例, `core.plugin.center` 182 例, `network` 14 例, 连同 P3.1 / P3.2 / P3.4 / P3.6 涉及的 `ui.edit`, `core.plugin.dex` / `agent` / `mcp`, `engine` 合计 885 例 0 失败; 守卫 T5 a: `key_*` 资源 269, 已登记 216, 待迁移 53 (Excluded 13, State 40; Setting 0), T5 b: `getSharedPreferences(` 调用 16 处 / 9 个文件 (都是不改代码的排除项), 已登记 29 (Cache 1, Excluded 8, Setting 17, State 3), 待迁移 5 -> 0, T5 c 通过; `inventory.py --check` 0 问题 0 冲突, 附录 A `apk_build` 1, `wizards` 2, `updates` 3, `plugin_center` 14 不变; E3: OnePlus OPD2413 (API 35) 与 Sony G8441 (API 28), 宿主 versionCode 5679, `device_store_check.py stores --groups q15,plugin_center,credentials,editor_keyboard,profiles` 各 47 项 PASS: 全部已登记存储中的已有条目经声明的键读到与原始值相同的值 (排除项单独计数), 当前 APK 构建配置档, 插件安装向导的启动图标选项, 一个探针插件的忽略版本各改一项 -> 强制停止 -> 重启后保持 -> 15 个原始值恢复; 首轮 G8441 的 verify 阶段未跑完, 原值记录只在内存中, 按推断原值 (探针键原先都不存在) 重建记录后单独重跑 verify 恢复; 其中 G8441 上 dex 编译器选择 (开关与组件, P3.6 的 q15 组改动项) 的原值无法确认, 已恢复为未选择 (默认值), 该机装有 dex 编译器插件, 若原先在开发者选项中选择过需要重新选择, 因此 `device_store_check.py` 改为把记录写入本地状态文件并支持 `--phase verify` 重跑 (子会话 settings-p3).
- [x] P3.4 凭据与授权: `github_auth`, `plugin_center_authorization`, `mcp_server_trust_confirmation`, `three_stove_agent_trust_confirmation`, `bun_host_capability_grants`, 全部 `sensitive = true`, 分类 `credentials`. 证据: 守卫 (c) 通过, R6 检查 (grep 日志语句). 实际: 提交 `2abf79461`; `NamedStores` 登记 5 份凭据存储 (名称不变, R1), 新增 `CredentialKeys` (附录 F 计划的 `CredentialKeys.kt`): `github_token` (Str, 无默认值), `key_$_plugin_auth_$_` 键族 (后接 `<包名>$_<签名指纹>`, 默认 false), MCP Server 与 3-Stove Agent 的签名确认 (名称即签名指纹, 为 `KeyFamily.everyName` 全名称键族, 默认 false), Bun 宿主能力授权 (全名称键族, 名称即能力 id, 各能力的默认值仍在 `BunHostCapabilityGrants`), 全部 `sensitive = true` 且归 `credentials` (D2, 本条只登记, 导出警告在 P6.3); `GitHubTokenStore`, `PluginAuthorizationStore`, `McpServerPluginInspector` / `McpServerTool`, `ThreeStoveAgentPluginInspector` / `ThreeStoveAgentGuide`, `BunHostCapabilityGrants` 改走键 (默认值, apply 时机, 类型不符时抛 ClassCastException 均不变, R2; 保留 `Context` 参数以免改调用方; `McpServerTool` 不再写入空签名, 空签名本就不会被判为已确认); `classification.json` 的 `key_$_plugin_auth_$_*$_*` 改为键族前缀 `key_$_plugin_auth_$_`, 新增 `github_token`; 单测 `core.pref` 119 例 (新增 `CredentialKeysTest` 5 例, 只比较不输出值), `core.plugin.center` 182 例, `core.plugin.agent` 44 例, `core.plugin.mcp` 24 例, `engine` 的 Bun / 授权用例 12 例, 均 0 失败; 守卫 T5 c: 31 个分类, 条目 407 (已登记 146), 敏感声明 5 个 (5 份存储) 全部在 `credentials`, 通过; T5 b: 已登记 13 (Excluded 8, Setting 5), 待迁移 21 -> 16 (Setting 13, State 3); T5 a 不变 (Setting 待迁移 0); `inventory.py --check` 0 问题 0 冲突; R6: 引用这些存储的 24 个源文件中, 日志 (`Log.*` / `println` / `Timber`), toast 与异常信息均不含令牌, 签名指纹或授权值 (grep 结果只有插件索引与会话状态等无关日志) (子会话 settings-p3).
- [x] P3.5 永久排除存储登记 (附录 A.2): `device_id_prefs`, `autojs.localstorage.*`, `lua-runtime-storage-v1-*`, `privileged_edit_backups`, `runtime_task_intents`, `ScriptWidgets`, `explorer_playback_history` 只登记 `StoreId(excluded = true)`, 不改代码. 证据: 守卫 (b) 通过. 实际: 提交 `b0fee2ba1`; 新增 `core/pref/keys/NamedStores.kt` (命名存储的统一登记表, 文件名不变, R1, 声明时登记到 `SettingsStores`; `SettingsRegistry.stores` 列出它们), 附录 A.2 的 8 份存储登记为 `StoreId(excluded = true)`: 条目列出的 7 份加 `autojs.node.storage.*.*.*` (Q13 (e) 的 Node.js storage 桥, 同为脚本数据; 不登记则 P3 结束时守卫 (b) 无法归零), 读写方代码未改; 守卫 (b) 增加核对: 已登记存储与 `classification.json` 一致 (排除项为 Excluded 且不含键声明, 其余存储含所归分类与 kind 的键声明), 已登记的存储不再因没有 `getSharedPreferences(` 调用而被判为失效条目, 命名存储中的键必须属于 `NamedStores` 声明的存储; 单测 `core.pref` 114 例 0 失败 (新增 `NamedStoresTest` 2 例); 守卫 T5 b: `getSharedPreferences(` 调用 60 处 / 34 个文件, 存储名 29, 已登记 8 (Excluded 8), 待迁移 29 -> 21 (Setting 18, State 3); T5 a 不变 (Setting 待迁移 0); `inventory.py --check` 0 问题 0 冲突, `inventory.md` 第 4 节为已登记存储标出 `NamedStores` 常量与引用处, 概况表 "已登记 `StoreId`" 8 (子会话 settings-p3).
- [x] P3.6 Q15 字面量键登记 (P2 遗留, 2026-10-21 拍板): `THEME_COLOR_DERIVED_ACCENT_AND_DARK` 与 `COLOR_HISTORY_MERGE_SIMILAR` 在 `AppearanceKeys` 声明为 Setting (名称不变, R1); 其余 6 个颜色选择键先在 `classification.json` 的 `literalKeys` 登记为 State (声明随 P4.2); `three_stove_agent_script_root_configuration` 在 `ExtensibilityKeys` 声明为 `Json` 键 (默认 `{}`); dex / r8 的开关与组件在 `DeveloperKeys` 声明 (Bool + Str, 读写方 `DexCompilerOptInStore` / `R8CompilerOptInStore` 改走键, 组件存在性校验留给 P7.3); debug 派生键 `key_$_codegen_model_current_debug_<profile>` 登记为 `layout_inspector` 的前缀键; `inventory.py` 识别这些常量 (与 Q17 同一机制); 附录 A 的 `appearance` / `extensibility` / `developer` / `other` 条目数更新. 证据: 单测 + `inventory.py --check` 0 问题 + 守卫 T5 a 的 Setting 待迁移仍为 0 + E3 (主题色派生开关与一个编译器选择各改一项后重启核对). 实际: 提交 `4764f1e0f`; `AppearanceKeys` 9 -> 11 键 (`THEME_COLOR_DERIVED_ACCENT_AND_DARK`, `COLOR_HISTORY_MERGE_SIMILAR`), `ExtensibilityKeys` 2 -> 3 键 (`three_stove_agent_script_root_configuration`, `Json` 对象, 默认 `{}`; 读写方取原文本, 规范化与失败回退仍在 `ThreeStoveAgentConnectionOwner`), `DeveloperKeys` 5 -> 9 键 (dex / R8 的开关 Bool 与组件 Str), 名称不变 (R1); `ThemeColorManager`, `ColorSelectBaseActivity`, `ThreeStoveAgentConnectionOwner`, `DexCompilerOptInStore` / `R8CompilerOptInStore` 改走键 (默认值, apply 时机, 类型不符时的回退不变, R2; 两个编译器选择仍是一次写入); 新增 `KeyFamily` (前缀加运行时部分命名的键族, `SettingDescriptor.family`), `LayoutInspectorKeys.CODEGEN_MODEL_CURRENT_DEBUG` 登记 `key_$_codegen_model_current_debug_<profile>`; 其余 6 个颜色选择键在 `classification.json` 登记为 State, `inventory.py` 经 `LITERAL_KEY_CONSTANTS` 识别其常量, 并识别键族工厂的默认值; 单测 `core.pref` 112 例 (新增 `LiteralKeysQ15Test` 5 例), `core.plugin.dex` 184 例, `core.plugin.agent` 44 例, 均 0 失败; 守卫 T5 a: `key_*` 资源 268, 已登记 215, 待迁移 53 (Excluded 13, State 40; Setting 0), T5 b 命名存储待迁移 29 (本条不涉及); `inventory.py --check` 0 问题 0 冲突, 源码字面量键 75 -> 89 组; 附录 A: `appearance` 11, `extensibility` 3, `developer` 9, `other` 60, 另 `layout_inspector` 5 -> 6 (前缀键按 `inventory.md` 第 7 节计数规则算 1 条, Q15 结论未列出); 核对 Q15 时另发现 13 个未登记的字面量键, 登记为附录 C Q18 (待决, 不阻塞); E3: OnePlus OPD2413 (API 35, 宿主 versionCode 5679) `device_store_check.py stores --groups q15` 13 项 PASS (派生开关取反, dex 选择改为探针组件并关闭开关 -> 强制停止 -> 重启后两项保持 -> 3 个原始值恢复), `domains` (Appearance / Extensibility / LayoutInspector / Developer, 28 键) 64 项 PASS (子会话 settings-p3).

### P4 记忆型状态 (`StateKey`)

- [x] P4.1 `DialogStateKeys`: `NotAskAgainDialog` 改为接受 `StateKey<Boolean>` (保留接受字符串键的 `@Deprecated` 构造直到调用方全部迁移), 现有 `key_dialog_*` 与 `key_version_history_restore_does_not_auto_save_to_disk` 逐个声明 (含 Q12 的 3 个无引用不再提示键, 只登记不使用). 证据: 单测 + 守卫. 实际: 提交 `6da9703f1`; 新增 `DialogStateKeys` (`SettingsRegistry.DOMAINS` 中排在 `CredentialKeys` 之后) 13 键 + 2 个键族, 全部为 `StateKey<Boolean>` (默认 true 即仍显示, 勾选 "不再提示" 后为 false), 名称不变 (R1): 6 个 `key_dialog_*` (其中 Q12 的 3 个只登记不使用) 与 `key_version_history_restore_does_not_auto_save_to_disk` (Q13 (a)), 插件中心的 3 个字面量键 (ABI 不匹配的下载 / 启用提示, 隐藏确认), 以及 P0 清单未识别的编辑器 "启动调试器" 提示 `editor.debug.long_click_hint`, 关于页反馈提示 `AboutActivity.start_feedback_activity`, 列表偏好确认键族 `prompt_$_` 与抽屉开关提示键族 `DrawerMenuToggleableItem$` (附录 C Q22); `ExactAlarmPermissionHelper` 以候选值 `key_timed_task_backend_alarm` 的字符串为键名, 声明为 `TIMED_TASK_EXACT_ALARM_PROMPT`, 守卫 (a) / (c) 允许一个设置值常量与一个同名存储键并存 (`classification.json` 以 `storedAs` 标明, `inventory.py` 的分类计数随之支持); `NotAskAgainDialog` 改为接受 `StateKey<Boolean>`, 经存储层读写 (默认值, apply 写入, 类型不符时抛 ClassCastException 均不变, R2), 字符串构造标 `@Deprecated` 并经 `DialogStateKeys.forName` 转发, 11 个调用方全部改走声明的键; 未指定键的对话框 (只有 `JsonSocketClientTool` 的地址可能无效提示) 仍以调用栈的 MD5 为键名, 无法声明, 由 P4.3 的运行时兜底归入 `other` (附录 C Q23); 默认偏好的绕过文件 1 -> 0; 单测 `core.pref` 139 例 (新增 `DialogStateKeysTest` 4 例) 中只有 `LiteralKeysQ15Test` 1 例失败, 原因是内存路线图会话未提交的 `key_memory_script_heap_dump` 使 `DeveloperKeys` 由 10 键变为 11 键 (与本条无关), `core.plugin.center` 182 例, `storage.history` 73 例, 均 0 失败; 守卫 T5 a: `key_*` 资源 270 (含上述未提交的 1 个), 已登记 224, 待迁移 53 -> 46 (Excluded 13, State 40 -> 33), T5 c: 31 个分类, 条目 502 (已登记 222); `inventory.py --check` 0 问题 0 冲突, 源码字面量键 105 -> 109 组; 附录 A `other` 60 -> 65 (子会话 settings-p4).
- [x] P4.2 其余状态: `key_last_*` 四个时间戳, `key_setup_wizard_welcome_done`, `key_post_notifications_permission_request_attempted`, `key_*_socket_normally_closed` / `key_mcp_server_normally_closed`, `key_floating_menu_shown`, `*_manager_*_level` / `*_collapsed`, `*_introduced`, `key_working_directory_initialized` / `_history`, `key_pc_server_address_history`, `key_layout_inspector_query_history`, `key_color_select_activity_legacy_layout`, `key_enable_observe_key`, `plugin_center_recent`, `plugin_center_attention`, `key_plugin_center_last_online_check_ts`, `key_keep_screen_on_when_in_foreground_last_enabled`, 向导的 "上次勾选" 记忆. 证据: 守卫 "待迁移" 计数为 0. 实际: 提交 `da4ecbadf`; `MiscStateKeys` 由 4 键增至 60 键 (除 `ROOT_AUTOMATOR_TOUCH_DEVICE` 为缓存外全部为 `other` 的 `StateKey`), 新增 56 个状态, 名称不变 (R1): 待迁移的 33 个 `key_*` 状态 (更新检查的 4 个时间戳与插件中心在线检查时间, 向导与通知权限标记, 3 个 `*_normally_closed`, 抽屉开关镜像 `key_a11y_service` / `key_floating_menu_shown`, 三个管理器对话框的等级与折叠状态, 布局分析的两个 `*_introduced` 与查询历史, 工作目录初始化标记与历史, PC 服务端地址历史, 取色器布局, "屏幕常亮" 的上次选择, 只登记不使用的 `key_enable_observe_key`), 5 组 `key_$_` 字面量状态 (Ace LSP 文件类型修订号, 3 个一次性提示, 悬浮按钮的上次屏幕方向), Q15 的取色器选择 6 键, Q18 的 8 个状态 (脚本主题色撤销快照, 智能配色 3 键, 悬浮按钮与悬浮控制器位置 4 键) 与 P0 清单未识别的文件管理器视图状态 4 键 (附录 C Q24); Q18 的 5 个设置在 `AppearanceKeys` (`COLOR_FAVORITE_SORT`) 与 `ExplorerKeys` (`ExplorerItemManager.SortConfig` 的排序类型与升降序 4 键) 声明; `plugin_center_recent` / `plugin_center_attention` 已在 P3.1 声明, 向导的 "上次勾选" 记忆已在 P3.3 声明, 本条不重复. 约 40 个读写方全部改走声明的键 (R2): 默认值与原读取方一致, 自带回退值的读取方保留回退值 (`storedOrNull() ?: 回退值`, 如编辑器缩放策略的迁移, 取色器的自定义颜色与旧版选中位置), 原先 `putXxxSync` 的写入仍为 commit (`CircularMenuWindow`), 原先一次写入的仍为一次 (`clearUpdatesCheckedStates`, `ExplorerItemManager.saveSortConfig`), `ExplorerViewStateStore` 的保存与清除由多次 apply 合并为一次写入 (结果相同); 字符串列表沿用 Gson 文本 (`htmlSafe`), 读法同 `Pref.getLinkedHashSet` / `getLinkedList` (键不存在时为空, 格式错误时抛出), 由读取方自行解析的 JSON 文本 (查询历史, 主题色撤销快照, 智能配色快照与运行状态) 按存储的文本读写; `Pref` 的时间戳读写与 `clearUpdatesCheckedStates` 标 `@Deprecated` 并转发, 私有的 `getTimestamp` / `updateTimestamp` 删除; `ColorSelectBaseActivity` 等读写方的键名常量删除 (`AceEditorLspPreferences.KEY_ACE_LSP_FILE_TYPES_REVISION` 因设备测试引用而保留), `inventory.py` 的 `LITERAL_KEY_CONSTANTS` 随之清空 (声明中的名称由声明扫描识别); 单测 `MiscStateKeysTest` 新增 7 例 (名称与类名拼接一致, 全部为 `other` 的已登记 State, 默认值, 时间戳一次写入, 字符串列表的 Gson 文本与严格解码, JSON 文本原样保留, 文件管理器状态与排序配置), `SettingsDomainsTest` 的 `KEYS_AFTER_P0` 增加 Q18 的 5 个设置, `AppearanceKeysTest` / `ExplorerKeysTest` / `LiteralKeysQ15Test` 的键数随之更新 (外观 11 -> 12, 文件管理器 6 -> 10; `LiteralKeysQ15Test` 的开发者选项 10 -> 11 是内存路线图 P5.1 的 `key_memory_script_heap_dump` 所致, P4.1 记录的失败随之消除); `core.pref` 146 例, 连同 `core.accessibility.inspector`, `network.github`, `runtime.api.modules.colors`, `theme`, `ui.edit.editor.ace`, `ui.explorer`, `ui.wizard`, `core.plugin.center`, `storage.history` 共 840 例, 0 失败; 守卫 T5 a: `key_*` 资源 270, 已登记 224 -> 257, 待迁移 46 -> 13 (只剩 Excluded 13, State 33 -> 0), T5 b 待迁移仍为 0, T5 c: 条目 502 -> 580 (已登记 222 -> 283); `inventory.py --check` 0 问题 0 冲突, 源码字面量键 109 -> 126 组, 默认偏好的绕过文件 0; 读写均经存储层, 由 JVM 单测覆盖, 未做真机测试; 附录 A `other` 65 -> 77, `appearance` 11 -> 12, `explorer` 6 -> 10 (子会话 settings-p4).
- [x] P4.3 "其他" 分类的运行时兜底: 导出时对默认存储 `getAll()` 与注册表求差, 未登记键仍进 `other` 并以运行时类型打标签 (T6), 单测覆盖. 证据: `SettingsSnapshotTest`. 实际: 提交 `0bad358b4`; 新增 `core/pref/OtherCategoryFallback.kt` (注册表旁的纯逻辑, 只依赖 Kotlin 基本类型与 `SettingDescriptor` 行, 不依赖 Android 与 `org.json`, R3): `TypedValue(type, value)` 为 T6 的带类型值 (构造时校验标签与值的类型, P6.1 的 `SettingsSnapshot` 沿用), `TypedValue.ofRuntimeType` 按 `getAll()` 的运行时类型打标签 (Boolean / Int / Long / Float / String / 字符串集合 -> `bool` / `int` / `long` / `float` / `string` / `stringSet`; 文本一律为 `string`, 是否为 JSON 只有键声明知道; 集合按名称排序复制, 快照与存储的遍历顺序无关; SharedPreferences 不能保存的类型返回 null); `OtherCategoryFallback.collect(getAll(), 描述行, 存储)` 求差: 同一存储中声明的键 (设置, 状态, 缓存), 键族成员与排除项算已登记, 设置值常量不持有值, 不算已登记 (与候选值同名的状态 `key_timed_task_backend_alarm` 经其状态声明算已登记), 其余条目按名称排序, 以运行时类型打标签归入 `other`, 无法打标签的只报告 (`untyped`) 不导出; 覆盖 `NotAskAgainDialog` 的调用栈键 (Q23) 与更新版本写入的键; 尚无调用方, 由 P6.1 的导出接入; 单测 `SettingsSnapshotTest` 5 例 (运行时类型标签, `TypedValue` 校验, 以真实注册表对默认存储求差且每个条目非已登记即兜底, 无法打标签的值, 描述行只对所属存储生效), `core.pref` 151 例 0 失败; 全量 `:app:testAppDebugUnitTest` 835 个测试类 5107 例, 跳过 26, 0 失败 (工作区含其他会话未提交的改动); 守卫计数不变, `inventory.py --check` 0 问题 (子会话 settings-p4).

### P5 文件型 JSON 存储归属整理 (D18 / T17)

- [x] P5.1 14 个文件型存储逐一登记为 `JsonFileStore` (用户设置: 无, 预期全部为 Cache 或运行时快照) 或 `CacheKey`; 内部的 `JSONObject` 手写编解码改用 `SettingsJson` 的辅助函数 (不改文件格式). 证据: 各自既有单测仍过; 守卫 (b) 对 `filesDir` JSON 文件名的检查通过. 实际: 提交 `589746038`; 新增 `core/pref/keys/JsonFileStores.kt`, `filesDir` / `noBackupFilesDir` 下的 14 个 JSON 文件逐一登记为排除的 `JSON_FILE` 存储 (路径不变, R1; 名称为相对 `filesDir` 的路径, `../no_backup/` 即 `noBackupFilesDir`, `*` 为运行时拼出的部分): 缓存 7 个 (插件索引, 已安装插件元数据, 字体目录与签名, 已安装字体清单, 布局代码生成模型登记表, 网页运行时下载状态), 本机记录 7 个 (Node.js 检查点与 WorkManager 任务, Python 执行快照, 特权编辑会话 / 操作日志 / 备份记录, Node.js 诊断报告); 都不含用户设置, 也都不在 SharedPreferences 中, 因此没有需要 `CacheKey` 的条目 (`classification.json` 仍按源文件区分 Cache 6 个 / Excluded 8 个); `BuildActivity` 的 `.json` 是 SAF 建议文件名, 不登记. `SettingsRegistry.fileStores` 列出它们, `SettingsStores.AndroidProvider` 拒绝打开排除的 JSON 文件 (`JsonFileStore` 会替换读写方自己的格式). 编解码: `SettingsJson` 增加 `parseObject` / `toOrgJson` 与 `stringify` 的 `indent` 参数 (Android `JSONObject.toString(indent)` 的版式); 以 `JSONObject` 为模型的 7 个存储 (`PluginIndexRepository` 的缓存, `NodeLifecycleCheckpointStore`, `NodeWorkManagerBridgeScheduler`, `RuntimeWebLocalDownloads`, `PrivilegedEditSessions`, `PrivilegedFileOperationJournal`, `PrivilegedBackupManager`) 的文件读写改经 `SettingsJson` (严格解析, 数字收窄与 Android 自带解析一致, 检查点仍缩进 2; 写出的文本不再转义 `/`, 字段与取值不变, 旧文件照常读取), 内存中的 `JSONObject` 与跨进程 / 脚本桥载荷不变; Gson 读写的 `FontStore` / `ModelStore` / `InstalledPluginMetadataStore`, 按原始字节保存的字体目录签名与 Python 快照, 以及只写不读的诊断报告不改 (附录 C Q25). 守卫 (b) 新增 `filesDir` JSON 文件检查 (`b_everyFilesDirJsonFileIsRegistered`): 源码启发式 (同 `inventory.py`; 目录由其他类传入的 `ModelStore` 列入 `MANUAL_JSON_FILE_SOURCES`) 找到的 14 个源文件都在 `classification.json` 的 `jsonFileStores`, 其中的 JSON 文件名都对应该条目认领的 `stores` 或列入 `otherJsonNames`, 每个声明恰被认领一次且为不含键的排除存储; `inventory.py` 同样核对并在第 6 节列出登记. 单测: `core.pref` 158 例 (新增 `JsonFileStoresTest` 3 例; `SettingsJsonTest` 3 例: 读 Android 写出的文本, 与 `normalize` 互逆, 缩进版式; 守卫 1 例), 连同 `ui.edit.editor.ace.font` 41 例, `core.accessibility.codegen.store` 132 例, `InstalledPluginMetadataStoreTest` 4 例共 335 例, 0 失败; 守卫 T5 b (filesDir JSON): 源文件 14 (人工 1), JSON 文件存储 14, 分类 {Cache=6, Excluded=8}, 其余守卫计数不变; `inventory.py --check` 0 问题, JSON 文件源文件 13 -> 14, 与 ROADMAP 1.1 对照无遗漏; 读写方均为 Android 代码, 未做真机测试 (子会话 settings-p5).
- [x] P5.2 收口: 删除已空的 `*Prefs` / `*Preferences` / `*Settings` 封装 (T17), 全量 `:app:testAppDebugUnitTest` 通过. 证据: 用例数与失败数. 实际: 提交 `36d58a047`; 删除只剩转发且已无调用方的 `storage/history/HistoryPrefs` (-> `HistoryKeys`), `core/plugin/batchrename/BatchRenameHistoryPrefs` (-> `ExplorerKeys`), `pluginclient/DevPluginPrefs` (-> `ConnectionKeys`), 以及 `EditorThresholdSettings` 中两个已弃用的默认值读取 (`defaultCodeHighlightThreshold` / `defaultLargeFileModeThreshold`, 即 `EditorKeys` 声明的默认值); 仪器测试 `BatchRenameHistoryActivityTest` 与 `ToolingDomainsKeysTest` / `ExplorerKeysTest` 改为直接用键. 其余 15 个同名后缀的类不是空封装, 保留: `EditorThresholdSettings` (阈值档位与摘要) 与 `RetentionLimitSettings` (保留上限页面的规格), `EditorEnginePreferences` (引擎选择与插件可用性), `EditorDisplayPreferences` (键名常量供 Ace 插件转发变更, 取值规范化), `EditorPinchToZoomPreferences` (旧策略键迁移), `AceEditorFontPreferences` (字体 id 校验), `AceEditorFontUiPreferences` / `AceEditorLspPreferences` (命名存储与 LSP 配置), `ConsoleTimeSettings` / `LogPageConsoleSettings` (按页面绑定键), 以及与设置存储无关的 `TokenColorSettings`, `ScriptModuleDebugSettings`, `PluginCenterItemSettings` 与脚本 API 的两个 `Settings`; `Pref` 中标 `@Deprecated` 的转发 getter 属 P14.1. 全量 `:app:testAppDebugUnitTest`: 838 个测试类 5127 例, 跳过 26, 失败 3, 均在 `runtime.api.modules` (`ScriptMessagesTest`, `ScriptModuleBindingPrototypeTest`, `ScriptModuleMounterTest`), 由 Augment 重构会话尚未提交的 `ScriptMessages` / `ScriptModuleBinding` / `ScriptModuleMounter` 及其测试改动引起, 与本条无关, 其余 0 失败 (P4 结束时 835 类 5107 例); `:app:assembleAppDebug` 与 `:app:compileAppDebugAndroidTestKotlin` 通过 (R4); 守卫计数不变, `inventory.py --check` 0 问题 (子会话 settings-p5).

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
| `appearance` | 外观 | 设置 > 外观 | 12 (P3.6 起含派生开关与合并相似颜色两组字面量键, Q15; P4.2 起含收藏颜色排序 `COLOR_FAVORITE_SORT`, Q18) | 是 | APPLY (语言, 夜间模式, 主题色) | `key_theme_color_*` 三项随 `key_theme_color` 一起; 派生开关 `THEME_COLOR_DERIVED_ACCENT_AND_DARK` 只影响之后选择的颜色 (NONE) |
| `general` | 配置 | 设置 > 配置 | 6 (含前台服务, Q13; P9 另加自动备份三开关与周期选项) | 是 | APPLY (启动图标, 前台服务) | 含重启策略与计划重启后端 / 延迟, 自动备份三开关与周期选项 (P9) |
| `explorer` | 文件管理器 | 设置 > 文件管理器 | 10 (P4.2 起含 `ExplorerItemManager.SortConfig` 的排序类型与升降序 4 组字面量键, Q18) | 是 | NONE | 工作目录不存在时作为部署项 "创建工作目录" (D20 / Q1) |
| `history` | 回收站与版本历史 | 设置 > 回收站与版本历史 及其子页 | 10 | 是 | NONE | 保留策略改变后由既有清理任务按新值执行 |
| `editor_general` | 代码编辑器 > 常规 | 代码编辑器设置 | 2 (1 键 + 1 组字面量键) | 是 | NONE | 编辑器引擎, 下次打开编辑器生效 |
| `editor_appearance` | 代码编辑器 > 外观 | 代码编辑器设置 | 17 | 是 | NONE | 字体未安装时作为部署项 "下载编辑器字体" (D20 / Q2), Ace 插件未安装时先提示安装 |
| `editor_log` | 代码编辑器 > 日志 | 代码编辑器设置 + 代码键 | 6 | 是 | NONE | 含 `key_editor_log_panel_*` |
| `editor_assistance` | 代码编辑器 > 辅助 | 代码编辑器设置 | 8 (3 键 + 5 组字面量键) | 是 | NONE | LSP 开关, 语言矩阵, 文件类型, 声明组 |
| `editor_smart_hint` | 代码编辑器 > 智能提示 | 代码编辑器设置 | 7 | 是 | NONE | |
| `editor_gestures` | 代码编辑器 > 操作手势 | 代码编辑器设置 + 代码键 | 2 | 是 | NONE | |
| `editor_gutter` | 代码编辑器 > 边栏标记 | 代码编辑器设置 | 5 | 是 | NONE | |
| `editor_performance` | 代码编辑器 > 性能 | 代码编辑器设置 | 2 | 是 | NONE | |
| `editor_keyboard` | 代码编辑器 > 符号键盘 | 命名存储 `editor_symbols_config`, `ace_editor_font_ui_preferences` | 2 份 (P3.2 起 `FunctionsKeyboardHelper` 只存本机测得的软键盘高度, 为缓存, Q19) | 是 | NONE | 整份 JSON 作为一个条目 |
| `timed_task` | 定时任务 | 设置 > 定时任务 | 1 | 是 | APPLY (重排任务) | |
| `a11y` | 无障碍服务 | 设置 > 无障碍服务 + 代码键 | 5 | 是 | NONE | 含手势观察; "按键观察" 键无读写方, 归 `other` (Q12) |
| `running` | 脚本运行 | 设置 > 脚本运行 + 代码键 | 5 | 是 | NONE | 含 `key_root_manager_apps_custom` |
| `record` | 录制脚本 | 设置 > 录制脚本 | 3 | 是 | NONE | |
| `extensibility` | 扩展性 | 设置 > 扩展性 | 3 (P3.6 起含 3-Stove Agent 脚本根目录配置, Q15) | 是 | NONE | 对之后启动的脚本生效 |
| `updates` | 更新 | 设置 > 更新 + 开发者选项 > 更新 + `UpdateIgnoreStore` | 3 (2 键 + 1 份) | 是 | NONE | 时间戳归 `other` |
| `console` | 日志与控制台 | 日志页面与控制台设置对话框的代码键 | 12 | 是 | NONE | |
| `connection` | 客户端与服务端模式 | 抽屉连接项的代码键 | 7 | 是 | NONE | 地址历史与管理器折叠状态归 `other` |
| `layout_inspector` | 布局分析器 | 悬浮窗与代码生成的代码键 | 6 (P3.6 起含 debug 包代码生成选择的前缀键, Q15) | 是 | NONE | 查询历史归 `other`; 模型文件本身不导出 |
| `developer` | 开发者选项 | 开发者选项页 | 11 (P2.5 起含 `key_leakcanary_enabled`, Q16; P3.6 起含 dex / R8 编译器选择的开关与组件 4 组字面量键, Q15; P3.3 起含 `key_memory_unreclaimed_warning`, Q21; 内存路线图 P5.1 起含 `key_memory_script_heap_dump`, P4.2 记录) | 否 | NONE | 默认不勾选: 调试类开关不宜跨设备带入; 编译器组件所属插件未安装时整组跳过 (P7.3) |
| `plugin_center` | 插件中心 | 插件中心设置页 + 命名存储 (筛选, 排序, 可见性, 启动图标, 挂载, 变体优先级, 在线来源) + `jvm_source_provider_selection` | 14 (3 键 + 6 组字面量键 + 5 份) | 是 | NONE | 列表页面下次打开生效 |
| `plugin_enable_state` | 插件启用状态 | `plugin_center_enable_state` | 1 份 | 是 | NONE | 未安装的插件条目 -> `TARGET_MISSING`; 只写偏好, 唤醒 / 重绑由插件中心下次刷新处理 (Q11) |
| `apk_build` | APK 构建配置档 | `build_apk_config_profiles` | 1 份 | 是 | NONE | 签名密钥库不含 (附录 C Q5) |
| `wizards` | 设置向导配置档 | `permission_wizard`, `plugin_install_wizard` 的自定义配置档 | 2 份 | 是 | NONE | 最近使用的配置档 id 归 `other` |
| `data_shortcuts` | 快捷方式 (数据) | 现有快捷方式备份 zip (`data/shortcuts.zip`) | 按条 | 是 | NONE | 细化页逐条勾选; 恢复沿用 `ShortcutBackupReconciler` (D21) |
| `data_color_library` | 颜色库 (数据) | 现有 `ColorLibraryJson` 文档 (`data/color-library.json`) | 按库 | 是 | NONE | 细化页逐库勾选 (用户库与智能库); 预设库不导出 (D21) |
| `credentials` | 凭据与授权 | `github_auth`, `plugin_center_authorization`, `mcp_server_trust_confirmation`, `three_stove_agent_trust_confirmation`, `bun_host_capability_grants` | 5 份 | 否 | NONE | D2 警告; 脚本 API 不导出 (T15); 自动备份不含 (T20) |
| `other` | 其他 (内部状态) | 全部 `StateKey` + 运行时发现的未登记键 | 77 (41 键 + 33 组字面量键 + 3 份; P2.6 起含 `MiscStateKeys` 的 3 个状态, Q17; P3.6 起含颜色选择 6 键, Q15; P4.1 起含 4 组不再提示的字面量键, Q22, 与候选值 `key_timed_task_backend_alarm` 同名的状态; P4.2 起含 Q18 的 8 个状态与文件管理器视图状态 4 键, Q24) | 否 | NONE | 不参与恢复默认 (T2); 不展示键名 (Q8) |

合计 31 项 (含 `other` 与两个数据分类). 条目数由 `SettingsRegistry.describe()` 与数据编解码在运行时计算并显示, 本表只作校对.

### A.2 永久排除 (不出现在任何分类)

| 对象 | 原因 |
| --- | --- |
| `device_id_prefs` | 设备标识, 跨设备导入会破坏唯一性 |
| `autojs.localstorage.*` (`core/storage/LocalStorage`, `runtime/api/augment/storages`), `lua-runtime-storage-v1-*` | 脚本自己的数据, 不是宿主设置 |
| `privileged_edit_backups`, `PrivilegedEditSessions`, `PrivilegedFileOperationJournal` | 指向本机文件的操作日志 |
| `runtime_task_intents`, `NodeLifecycleCheckpointStore`, `NodeWorkManagerBridgeScheduler`, `PythonPrivateExecutionSnapshot` | 运行时状态, 进程重启即失效 |
| `ScriptWidgets` | 桌面小部件 id 由启动器分配, 仅本机有效 |
| `key_root_manager_apps_cache`, `InstalledPluginMetadataStore`, `PluginIndexRepository`, `FontCatalogRepository`, `FontStore` (字体文件), `ModelStore` (模型文件), `RuntimeWebLocalDownloads` | 缓存或可重新下载的资源 (字体经部署项按需重新下载) |
| `key_pref_bundle_default_item` / `key_pref_bundle_disabled_items` | `MaterialListPreference` 的 Bundle 参数名, 不是偏好键 |
| `KeyStoreDatabase`, `TimedTaskDatabase`, `IntentTaskDatabase`, `HistoryDatabase` | D1, 第二期候选 (Q5 / Q6) |
| `ColorHistoryDatabase` 的直接读取 | 颜色库只经 `ColorLibraryJson` 交换 (D21 / R7); 颜色历史记录本身不导出 |
| `inrt` 的 `key_first_using` / `key_inrt_applied_build_id`, `key_run_on_boot_inrt`, `key_not_showing_main_activity` 与 4 个 inrt 权限开关键 | 打包应用运行时状态与专用配置 (Q13) |
| `autojs.node.storage.<包名>.<cwd 摘要>.<存储名>` (`NodeBridgeProtocol`) | Node.js 脚本自己的数据 (Q13) |
| `key_scripts_after_app_restart` | 重启前写入, 重启后消费的一次性交接值 (Q13) |
| `key_$_terminal_*`, `key_$_dialog_terminal_tips` | 已移除的宿主内置终端遗留键, 启动时清理 (Q13) |

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

Q0-Q14 均已于 2026-10-20 由维护者拍板 (Q10 / Q12 / Q13 / Q14 在 P0 完成后拍板, 结论已并入第 3 节与第 5 节); 新待决事项从 Q15 起登记.

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
| Q10 | P0.4 发现的同一键多处默认值不一致 (`docs/settings/defaults.md` 第 2 节, 共 3 条): (1) `key_editor_text_size`: 设置页 `EditorTextSizePreference` 未设置时显示 `R.integer.editor_text_size_default_value` = 14, 原生编辑器 `CodeEditor` 未设置时取布局 `code_editor.xml` 的 `textSize` = 15sp; (2) `key_enable_a11y_service_with_root_access`: `Pref` / 设置页 / inrt 缺键时为 true (`R.bool.pref_enable_a11y_service_with_root_access`), 脚本 API `settings.isEnabled` 缺键时为 false (`Settings.kt:50`, 对所有别名统一用 false); (3) `key_use_volume_control_running`: 同 (2), true 对 false | (1) 统一为 14, `CodeEditor` 初值改读 `R.integer.editor_text_size_default_value` (P2.3); (2) / (3) `settings.isEnabled` 缺键时改用注册表中各键自身的默认值, 与设置页一致 (P2.4); 各记一条 fix changelog; 落地前保持现状 (R2) | 已拍板 |
| Q11 | 导入 `plugin_enable_state` 时是否触发插件中心的唤醒 / 重绑 | 只写偏好, 由插件中心下次刷新按现有逻辑处理; P7.5 在 OnePlus (ColorOS) 上验证 | 已拍板 |
| Q12 | 源码与资源均无引用的 13 个 `key_*` 资源如何登记: 9 个字体 id 常量 (`key_ace_editor_font_cascadia_code` 等, 只有 `iosevka` 是 `default_key_ace_editor_font` 的目标), 3 个不再提示键 (`key_dialog_foreground_svc`, `key_dialog_log_entries_exported`, 只在注释代码中出现的 `key_dialog_layout_inspector_exit_tips`), `key_enable_observe_key` (附录 A 的 "按键观察", 无任何读写方) | 资源保留 (R1); 9 个字体 id 为 ValueConstant, 3 个不再提示键为 State, `key_enable_observe_key` 改登记为 State (无读写方的键不作为可导入设置), 附录 A `a11y` 条目数 6 -> 5; `classification.json` 在 P1.0 更新 | 已拍板 |
| Q13 | P0.3 初稿与第 5 节 / 附录 A 的描述不一致或需确认的归属: (a) `key_version_history_restore_does_not_auto_save_to_disk`: P2.2 列为 `HistoryKeys` 的设置, 代码中是 `VersionHistoryController` 里 `NotAskAgainDialog` 的不再提示键; (b) `key_color_select_activity_legacy_layout`: P2.5 列在 `LayoutInspectorKeys`, 代码中是 `ColorLibrariesActivity` / `ColorSelectActivity` 打开前写入的界面模式, 用户不直接设置; (c) `PluginUpdateCheckStore` (`key_$_plugin_update_check_$_<包名>`): P4.2 列为状态, 代码中是每个插件的 "检查更新" 开关; (d) `key_foreground_service`: 主应用里是抽屉开关对前台服务运行状态的镜像, 脚本 API `settings` 与 inrt 把它当设置读写; (e) `autojs.node.storage.<包名>.<cwd 摘要>.<存储名>` (`NodeBridgeProtocol` 的 Node.js storage 桥): 不在 1.1 的命名存储清单中 (实测 29 份) | (a) State (P4.1), (b) State (P4.2), (c) `plugin_center` 的 Setting (P3.1), (e) Excluded; (d) `key_foreground_service` 改为 `general` 的 Setting (`effect = APPLY`: 导入后按值启动或停止前台服务, 需要通知权限, 失败记入结果页, P2.1 / T12), 附录 A `general` 条目数 5 -> 6. 附录 A.2 补登 4 类排除: `autojs.node.storage.*`, `key_scripts_after_app_restart`, inrt 专用键 (`key_run_on_boot_inrt`, `key_not_showing_main_activity` 与 4 个权限开关键), 已移除宿主终端的 `key_$_terminal_*` / `key_$_dialog_terminal_tips`; `classification.json` 在 P1.0 更新 | 已拍板 |
| Q14 | JVM 单测类路径上没有真实的 `org.json`: `app` 的测试依赖只有 JUnit4 与 bcprov, 单测里的 `org.json` 来自 android.jar (P0.5 探针: `new JSONObject().put(...)` 抛 `RuntimeException: Stub!`, AGP 的 mockable jar 同样抛异常), 而 R3 / T6 要求编解码只依赖 `org.json`, P1.1 的 `SettingKeyTest` (Json 往返) 与 P6.1 的编解码测试都需要真实实现 | P1.0 在 `gradle/libs.versions.toml` 增加 `org.json:json` 20231013, 仅 `testImplementation` (运行时仍用 Android 自带的 `org.json`, APK 不变); 与 Android 运行时的行为差异 (`JSONObject.NULL`, 数字类型收窄) 在 `SettingsJsonTest` 显式覆盖; T6 / R3 的 "只依赖 `org.json`" 含义不变 | 已拍板 |
| Q15 | P2 迁移中发现 P0 清单没有识别的默认偏好字面量键 (名称不以 `key_` 开头, 经 `Pref` 读写, 不在 `classification.json`, 守卫测试不覆盖): `ThemeColorManager` 的 `THEME_COLOR_DERIVED_ACCENT_AND_DARK` (Boolean, 强调色与深色是否由主题色派生); `ColorSelectBaseActivity` 的 7 个颜色选择键 (带 `org.autojs.autojs.theme.app.ColorSettingRecyclerView.` 前缀的 `COLOR_SETTING_CUSTOM_COLOR` 与 `SELECTED_COLOR_INDEX`, 以及 `SELECTED_COLOR_LIBRARY_ID`, `SELECTED_COLOR_LIBRARY_ITEM_ID`, `SELECTED_COLOR_LIBRARY_NAME`, `SELECTED_COLOR_LIBRARY_ITEM_NAME`, 颜色历史的 `COLOR_HISTORY_MERGE_SIMILAR` 开关); `ThreeStoveAgentConnectionOwner` 的 `three_stove_agent_script_root_configuration`; `DexCompilerOptInStore` 的 `dex_compiler_experimental_enabled` / `_component` 与 `R8CompilerOptInStore` 的 `r8_compiler_explicit_enabled` / `_component` (开发者选项中选择的编译器插件组件); debug 包代码生成模型选择的派生键 `key_$_codegen_model_current_debug_<profile>` | (1) 派生开关 `THEME_COLOR_DERIVED_ACCENT_AND_DARK` 为 `appearance` 的 Setting (随 `key_theme_color` 三项一起), debug 派生键 `key_$_codegen_model_current_debug_<profile>` 随 `key_codegen_model_current` 归 `layout_inspector` (前缀键, 仅 debug 包); (2) `COLOR_HISTORY_MERGE_SIMILAR` 为 `appearance` 的 Setting, 其余 6 个颜色选择键为 State (P4.2, 与 `key_color_select_activity_legacy_layout` 同组); (3) Stove 根配置为 `extensibility` 的 Setting (`Json` 键), dex / r8 两组 (开关 Bool + 组件 Str) 为 `developer` 的 Setting, 导入时组件对应的插件未安装则整组跳过并计 `TARGET_MISSING` (D9, 校验在 P7.3); 登记, 声明与 `inventory.py` 识别作为 P3.6 落地, 附录 A 条目数随之更新 (`appearance` 9 -> 11, `extensibility` 2 -> 3, `developer` 5 -> 9, `other` 54 -> 60) | 已拍板 (2026-10-21, 三项均按建议) |
| Q16 | `key_leakcanary_enabled` (内存泄漏门禁路线图 P1 新增, 资源提交 `8e52a7bcb`) 不在 P0.4 冻结的默认值基线中, P2.1 守卫测试报未登记项 | 登记为 `developer` 的 Setting, 默认 true, 在 `DeveloperKeys` 声明 (P2.5); `SettingsDomainsTest` 以 `KEYS_AFTER_P0` 补其默认值; 附录 A `developer` 条目数 4 -> 5 | 已定 (调度会话, 2026-10-20) |
| Q17 | P2.6 收口时 4 个绕过文件读写的字面量键不在 P0 清单中: 查找 / 替换对话框的 `...` (查找文本, 键名就是三个点) 与 `find_or_replace_using_regex`, 代码求值对话框的 `org.autojs.autojs.ui.edit.debug.CodeEvaluateDialogBuilder.code`, Root 自动化的 `org.autojs.autojs.engine.RootAutomatorEngine.touch_device` | 名称不变 (R1), 在 `MiscStateKeys` 声明: 前 3 个为 `other` 的 State (附录 A `other` 条目数 51 -> 54), `touch_device` 为 Cache (本机探测的触摸设备 id, 不导出); `classification.json` 的 `literalKeys` 已登记, `inventory.py` 识别声明中的字面量名称 | 已登记 (P2.6), 维护者 2026-10-21 复核确认 |
| Q18 | P3.6 核对 Q15 时发现另有 13 个默认偏好字面量键不在 P0 清单与 `classification.json` 中 (名称不以 `key_` 开头, 经 `Pref` 读写, 守卫测试不覆盖): (1) `ScriptThemeChanges` 的 `THEME_COLOR_SCRIPT_UNDO` (脚本改主题色前的撤销快照); (2) `VirtualColorLibraryActivity` 的 `COLOR_FAVORITE_SORT` (收藏颜色的排序方式); (3) `IntelligentColorEngine` 的 `INTELLIGENT_COLOR_ACTIVE_SNAPSHOT` / `INTELLIGENT_COLOR_RUNTIME_STATE` / `INTELLIGENT_COLOR_SCRIPT_STATE` (智能配色的当前快照与运行状态); (4) 悬浮窗位置 `<CircularMenuWindow 类名>.position.x_percent` / `.y_percent` 与 `<FloatingControllerWindow 类名>.position.x_percent` / `.y_percent`; (5) 文件管理器排序 `<ExplorerItemManager.SortConfig 类名>.file_sort_type` / `.dir_sort_type` / `.file_ascending` / `.dir_ascending` (`ExplorerItemManager.saveSortConfig`) | 建议: 名称不变 (R1); (2) 为 `appearance` 的 Setting, (5) 为 `explorer` 的 Setting (与插件中心排序同为用户选择的排序方式), 其余为 `other` 的 State; 随 P4.2 一并登记与声明 (常量名可经 `inventory.py` 的 `LITERAL_KEY_CONSTANTS` 识别), 附录 A 条目数届时更新; 不阻塞 P3, 本阶段不改动 | 待决 (P3.6 子会话 settings-p3 登记, 2026-10-21; 已按建议随 P4.2 实施, 键名由声明扫描识别而非 `LITERAL_KEY_CONSTANTS`, `da4ecbadf`, settings-p4) |
| Q19 | P3.2: 命名存储 `FunctionsKeyboardHelper` 只有 `soft_input_height` 一个键, 是 `FunctionsKeyboardHelper` 在本机测得的软键盘高度 (像素, 测得前为 400, 每次弹出软键盘时重测), P0.3 把整份存储归为 `editor_keyboard` 的 Setting; 跨设备导入会给出另一台设备的高度 | 建议: 改为 Cache (不导出, 名称不变, R1), 附录 A `editor_keyboard` 3 -> 2 份; 已按建议先行实施 (`EditorKeyboardKeys.SOFT_INPUT_HEIGHT` 为 `CacheKey`, `classification.json` 的存储 kind 为 Cache), 维护者不同意时改回 Setting 即可 | 待决 (已按建议先行, P3.2 子会话 settings-p3 登记, 2026-10-21) |
| Q20 | P3.3 真机核对发现命名存储 `plugin_install_wizard` 中残留 `key_$_plugin_install_wizard_first_run_shown` (Boolean): 插件安装向导的首次运行提示, 自 `2d7c72b33` (2026-10-06) 起代码不再读写, 但旧安装 (OnePlus OPD2413, Sony G8441) 中仍有该值 | 建议: 按 Q13 对已移除终端遗留键的先例登记为 Excluded (不导出, 不恢复默认); 是否像 `TerminalLegacyCleanup` 那样在启动时删除留给维护者决定; 已按建议登记 (`WizardKeys.names`, `classification.json` 的 `literalKeys`) | 待决 (已按建议登记, P3.3 子会话 settings-p3, 2026-10-21) |
| Q21 | 内存泄漏门禁路线图 P2.3 (`6b09b7d8e`, 2026-10-21) 新增 `key_memory_unreclaimed_warning` (开发者选项 > 内存诊断: 已结束的脚本未被回收时在控制台警告, XML 默认 true), 守卫 (a) 报未登记项 | 按 Q16 先例: `developer` 的 Setting, 默认 true, 在 `DeveloperKeys` 声明 (读取方 `MemoryDiagnostics` 属内存路线图会话, 未改), `SettingsDomainsTest` 以 `KEYS_AFTER_P0` 补其默认值, 附录 A `developer` 9 -> 10 | 已登记 (P3.3 子会话 settings-p3, 按 Q16 先例, 2026-10-21) |
| Q22 | P4.1 迁移 `NotAskAgainDialog` 的调用方时发现 4 组不以 `key_` 开头的不再提示键不在 P0 清单与 `classification.json` 中 (默认偏好, Boolean): `EditorMenu` 的 `editor.debug.long_click_hint` ("启动调试器" 菜单), `AboutActivity` 的 `AboutActivity.start_feedback_activity` (简单类名加后缀, 反馈前的提示), `MaterialListPreference.showPrompt` 的 `prompt_$_<列表偏好的键>`, `DrawerMenuToggleableItem.getPrompt` 的 `DrawerMenuToggleableItem$<抽屉开关在当前语言下的标题>` (切换语言后按新标题另记) | 按 Q17 先例: 名称不变 (R1), 在 `DialogStateKeys` 声明为 `other` 的 State (后两组为键族), `classification.json` 的 `literalKeys` 登记, `inventory.py` 识别声明中的键族前缀; 附录 A `other` 条目数随之更新 | 已登记 (P4.1 子会话 settings-p4, 按 Q17 先例, 2026-10-21) |
| Q23 | `NotAskAgainDialog` 未指定键时以调用栈 (含行号) 的 MD5 为键名, 现只有 `JsonSocketClientTool` 的 "IP 地址可能无效" 提示这样用: 键名随相关代码的行号变化, 改动后 "不再提示" 即失效, 旧值作为无法声明的键留在默认偏好中 | 建议: P4 保持原样 (R1 / R2), 这类键由 P4.3 的运行时兜底按运行时类型归入 `other` 导出; 是否改为固定的声明键 (属于新键名, 旧值一次性失效) 并清理残留的 MD5 键, 留给维护者决定 | 待决 (已按建议先行, P4.1 子会话 settings-p4 登记, 2026-10-21; P4.3 的运行时兜底已实现, `0bad358b4`) |
| Q24 | P4.2 核对 `Pref` 的字符串键调用方时发现 `ExplorerViewStateStore` 以 `ExplorerView` 的简单类名加后缀在运行时拼出 4 个默认偏好键, P0 清单与 `classification.json` 均未收录: `ExplorerView.page_state` (文件管理器页面的滚动位置), `ExplorerView.explorer_current` / `ExplorerView.explorer_root` (当前目录与根目录), `ExplorerView.explorer_history` (页面历史, 每项为 "路径,滚动位置,目录折叠,文件折叠", JSON 字符串列表); 后三者是本机路径 | 建议: 按 Q17 / Q22 先例, 名称不变 (R1), 在 `MiscStateKeys` 声明为 `other` 的 State (导出时默认不勾选), `classification.json` 的 `literalKeys` 登记; 是否像 `explorer_playback_history` (P3.5) 那样因指向本机路径改为 Excluded, 留给维护者决定 | 待决 (已按建议先行, P4.2 子会话 settings-p4 登记, 2026-10-21) |
| Q25 | P5.1 "内部的 `JSONObject` 手写编解码改用 `SettingsJson`" 的范围: 14 个文件中 7 个以 `JSONObject` 为内存模型, 同一对象还用作跨进程请求 (特权文件操作), 脚本桥返回值 (Node.js, 网页运行时) 或远程索引的解析输入 (`PluginIndexRepository.parseIndexEntries`); 3 个经 Gson 读写数据类 (`FontStore`, `ModelStore`, `InstalledPluginMetadataStore`); 字体目录与签名按下载的原始字节保存 (签名校验要求逐字节不变), Python 快照写入调用方给出的字节, 诊断报告只写不读 | 建议: 只把文件读写边界统一为 `SettingsJson.parseObject` / `stringify` (读法严格, 数字收窄与 Android 一致; 写出的文本不再转义 `/`, 整数值的 Double 写为 `1.0`, 字段与取值不变, 旧文件照常读取, 新文件旧版本也能读), 内存模型与载荷不改为普通值, Gson / 原始字节 / 诊断报告不改; 这些文件都是排除项, 不进归档, 更深的改写只影响 其他子系统 (特权文件, Node.js, 插件中心) 而与导入导出无关; 维护者要求更彻底的统一 (例如 Gson 存储也改用 `SettingsJson`) 时另立条目 | 待决 (已按建议先行, P5.1 子会话 settings-p5 登记, 2026-10-21, `589746038`) |

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
| 键声明 | `core/pref/SettingKey.kt`, `StateKey.kt`, `CacheKey.kt`, `KeyFamily.kt` (P3, 前缀键族), `SettingCategory.kt`, `SettingEffect.kt`, `DeploymentKind.kt`, `SettingsJson.kt`, `keys/NamedStores.kt` (P3, 命名存储登记), `keys/EditorKeyboardKeys.kt` (P3.2), `keys/ApkBuildKeys.kt` / `keys/WizardKeys.kt` (P3.3), `keys/AppearanceKeys.kt`, `GeneralKeys.kt`, `ExplorerKeys.kt`, `HistoryKeys.kt`, `EditorKeys.kt`, `TimedTaskKeys.kt`, `A11yKeys.kt`, `RunningKeys.kt`, `RecordKeys.kt`, `ExtensibilityKeys.kt`, `UpdatesKeys.kt`, `ConsoleKeys.kt`, `ConnectionKeys.kt`, `LayoutInspectorKeys.kt`, `DeveloperKeys.kt`, `PluginCenterKeys.kt`, `CredentialKeys.kt`, `DialogStateKeys.kt`, `MiscStateKeys.kt` |
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

P14.3 复测时在本表追加一列, 预期: 绕过文件 0, `getSharedPreferences(` 调用只剩 `SharedPreferencesStore`, 序列化方式只剩 `SettingsJson`, 守卫 "待迁移" 0.
