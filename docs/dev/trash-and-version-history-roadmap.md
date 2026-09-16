# AutoJs6 回收站 (Trash) 与版本历史 (Version History) Roadmap

本文是回收站与版本历史两个功能 "缺陷修复 + 能力精进" 的可执行状态表.
以 2026-09-16 的本地代码快照 (`master@03aa90f72`, `VERSION_NAME=6.8.0`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号), 证据等级见附录 C.
3. 涉及字符串资源的条目必须同步 10 个语言的 `strings.xml`; 涉及 feature / fix / improvement 的提交必须同步 `.changelog/lang_*.json` 并重新生成 markdown.
4. 附录 A 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".

---

## 1. 固定决策与硬约束

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 保留策略中 `0` 或负数统一表示 "不限制" | 适用于天数 / 版本数 / 单文件容量 / 总容量 / 纳管文件大小上限. 现状是 `historyMaxDays = 0` 会立刻删光而 `historyMaxTotalBytes = 0` 表示不限 (见 F7), P0.7 统一为本语义并由 `HistoryRetentionPolicy` 单点实现. |
| D2 | 插件宿主会话依赖的 `TrashRepository` 表面冻结 | `moveToTrashWithProgress(path, progress, file, controller, beforeSourceDelete, trashId)`, `newTrashId()`, `findTrashItem()`, `restoreTrashItemToPathWithoutOverwrite()`, `deleteTrashItem()` 被 `core/plugin/explorer/session/ExplorerActionTargetTrashBatchStore.kt` 的崩溃恢复状态机依赖. 签名与不变量 (成功后目标存在且条目消失; 异常时 "条目仍在且目标不存在" 或 "条目消失且目标存在" 或需要恢复) 不变, 只允许新增重载. |
| D3 | Room schema 只增不减 | `HistoryDatabase` 从 version 2 升级只允许 `ALTER TABLE ... ADD COLUMN` (带默认值) 或新表, 必须提供显式 `Migration`, 不使用 destructive fallback. 旧 blob 目录布局 (`history/blob/<fileId>/<revId>.bin`, `trash/blob/<trashId>.bin`) 不变. |
| D4 | 覆盖类写入前留快照 | 凡是应用自身以 "覆盖已有文件" 方式写入内部存储的路径 (历史恢复到磁盘, 回收站覆盖恢复, 资源管理器复制 / 移动覆盖), 都先为目标文件记录一条 revision (受纳管文件大小上限约束), `op` 使用 `RESTORE_PRE` / `OVERWRITE_PRE`. 脚本 API (`files.write` 等) 不纳管 (见 Q2). |
| D5 | 可测逻辑必须与 Android 类型解耦 | JVM 单测环境只有 JUnit4 (无 Robolectric, `android.net.Uri` / `Pref` / Room 不可用), 策略与路径逻辑抽成基于原始类型参数的纯对象 (`HistoryRetentionPolicy`, `HistoryPaths`, `TrashRestoreNaming` 等), 测试只触碰这些对象. |
| D6 | Roadmap 位于 `docs/dev/` | 与仓库内既有 roadmap 的位置与风格一致. |
| D7 | 回收站容量上限始终保留最新条目 | `TrashRepository.enforceTotalBytesLimit` 经 `HistoryRetentionPolicy.selectOverBytesProtecting` 从最旧开始清理, 最新条目与刚移入的批次不参与清理; 单个超大条目保留到按天数过期或有更新条目移入为止. 版本历史的容量上限不采用此保护 (纳管文件大小上限已在入口处约束). |
| D8 | 待移入条目超过回收站上限时默认 "继续移入" (Q3 拍板) | 对话框三选一: 移入回收站 (默认, positive) / 彻底删除 (neutral, caution 色) / 取消; 批量移入以整批总大小与上限比较. |
| D9 | 保留策略数值项放在设置子页而非主页平铺 | 9 个数值项 + 1 个开关过多; 主设置页 "回收站与版本历史" 分类只含 "保留策略" 子页入口与 "彻底删除时同时清除版本历史" 开关. 数值项使用数字输入对话框 (`RetentionLimitDialogBuilder`, 字节以 MB 输入) 而非滑杆, 附 "使用默认值" / "不限制" 选项. |

---

## 2. 范围与非目标

范围内:

- `app/src/main/java/org/autojs/autojs/storage/history/**` (15 个文件, 2,513 行): 实体 / DAO / 数据库 / 偏好 / 仓库 / blob 存储 / 清理任务 / 恢复与历史对话框控制器
- `app/src/main/java/org/autojs/autojs/ui/storage/**` (9 个文件): 回收站页, 版本历史页, 通用搜索排序列表基类
- 接入点: `ui/edit/editor/service/EditorVersionHistoryService.kt` (保存前快照, 编辑器菜单), `ui/edit/editor/service/EditorFileService.kt:197` (调用处), `ui/common/ScriptOperations.java` (删除策略对话框 / 移入回收站 / 彻底删除后清历史), `ui/explorer/ExplorerBuiltInActionHandler.kt:62` (资源管理器 "版本历史"), `ui/main/drawer/DrawerFragment.kt:720-830` (抽屉入口与统计), `App.kt:370-372` (清理任务调度)
- 偏好键: `res/values/strings_donottranslate.xml` 中的 `key_history_*`, `key_trash_*`, `key_drafts_*`, `key_delete_permanently_also_clear_history`, `key_version_history_restore_does_not_auto_save_to_disk`

非目标 (本 Roadmap 不处理):

- 插件宿主会话 `core/plugin/explorer/session/**` 的批量回收状态机本身 (只作为消费方, 见 D2).
- 编辑器 "紧急草稿" (`storage/file/EmergencyDraftDirectoryGuard`, `drafts/` 目录) 的内容与恢复流程, 只在 P1 中把其保留策略与其他策略一起暴露到设置页.
- `com.stardust.**` 兼容包.

---

## 3. 现状诊断 (2026-09-16)

行号以快照 `03aa90f72` 为准.

| 编号 | 问题 | 锚点 |
| --- | --- | --- |
| F1 | 指纹回退错位: `latestFingerprint` 记录的是保存前内容 (即快照本身) 的 sha; 保存后磁盘内容已变, 文件重命名后下一次保存的 old bytes 等于上次的 new bytes, 指纹永不匹配, 历史与文件脱钩. 且匹配时不检查旧路径是否仍存在, 复制一个副本并编辑副本会把原文件的历史条目 "搬走" | `HistoryRepository.kt:53-58, 118-131` |
| F2 | 内部存储根目录在 5 处硬编码 `/storage/emulated/0`; 多用户 / 工作资料 (`/storage/emulated/10`) 下历史静默失效 (`toLogicalPathOrNull` 恒为 null), 回收站路径选择器根目录错误. `util/EnvironmentUtils.externalStoragePath` 已存在但未被使用 | `HistoryUriUtils.kt:10`, `TrashRestoreController.kt:301`, `VersionHistoryController.kt:476`, `ScriptOperations.java:95`, `EditorFileService.kt:983` |
| F3 | 版本历史 "恢复到磁盘" 直接覆盖当前文件, 当前内容无快照; 回收站 "覆盖" 恢复同样. `Revision.op` 注释预留的 `RESTORE` 从未使用 | `VersionHistoryController.kt:291-312`, `TrashRestoreController.kt:170-174` |
| F4 | 回收站文件覆盖恢复非原子: 直接 `dest.outputStream()` 写入, 中止或崩溃后原文件损坏且无法回退 (`destExistsBeforeRestore` 只清理新建的 dest) | `TrashRepository.kt:351-356`, `TrashRestoreController.kt:206-208` |
| F5 | 自动改名启发式错误: 以 "名称是否含 `.`" 判断目录, 目录 `my.project` 会得到 `my-1.project`; 应使用 `item.isDirectory` | `TrashRestoreController.kt:265-298` |
| F6 | 历史对话框最多列出 20 条 (`HISTORY_DIALOG_MAX_ITEMS`) 而保留策略默认 50 条, 更早的版本不可达; 编辑器模式无 "预览", 删除一条后重载时 `showPreview` 被写死为 true | `VersionHistoryController.kt:118, 165, 455, 475` |
| F7 | 保留策略 0 值语义不一致: `historyMaxDays = 0` / `historyMaxVersions = 0` / `historyMaxTotalBytesPerFile = 0` 会立刻删光, 而 `historyMaxTotalBytes = 0` / `trashMaxTotalBytes = 0` 表示不限; 策略实现散落在仓库与 Worker 三处, 无单测 | `HistoryRepository.kt:150-181, 224-227`, `HistoryCleanupWorker.kt:47-55, 76-81` |
| F8 | 10 个策略偏好键没有任何设置界面 (`res/xml/fragment_preferences.xml` 未引用), 用户只能使用默认值 (历史 30 天 / 50 版 / 200 MB 每文件 / 2 GB 总量 / 20 MB 纳管上限; 回收站 30 天 / 500 MB; 草稿 7 天 / 200 MB) | `HistoryPrefs.kt`, `fragment_preferences.xml` |
| F9 | 历史 orphan blob 清理无最小年龄保护, 与回收站的 30 分钟保护不一致; blob 已写而 DB 插入尚未提交时, 周期任务恰好运行会删掉 blob, 留下指向缺失文件的 revision | `HistoryCleanupWorker.kt:105-133` 对比 `145-160` |
| F10 | 连续相同内容的快照不去重 (无 sha 比较): 空操作保存或 "恢复后再保存" 会产生内容完全相同的 blob | `HistoryRepository.kt:44-79` |
| F11 | 回收站容量上限只在每日 Worker 执行, 移入时不检查; 一个超过上限的条目会让更旧的条目被清空而自身保留 | `HistoryCleanupWorker.kt:76-99`, `TrashRepository.kt:96-283` |
| F12 | 重命名 / 移动不迁移历史: `rename` / `move` 均不调用 `HistoryRepository`, 历史停留在旧路径, 版本历史页会显示已不存在的路径 | `ScriptOperations.java:374-402` |
| F13 | 快照来源只有编辑器保存; 资源管理器复制 / 移动覆盖, 回收站覆盖恢复, 历史覆盖恢复等覆盖路径均不留快照 | `EditorFileService.kt:197`, `ScriptOperations.java:918-1010` |
| F14 | blob 缺失时的用户体验: 读取 revision 抛 `FileNotFoundException` 并以原文 toast; 回收站恢复 `require(blobFile.exists())` 抛 `IllegalArgumentException`; 均无 "移除失效条目" 引导 | `HistoryRepository.kt:22-25`, `TrashRepository.kt:299-301` |
| F15 | 能力缺口: 回收站无批量操作 / 内容预览 / 条目详情 / 移入后撤销 / 到期提示; 版本历史无差异视图 / 按文件的独立列表页 / 手动与命名快照 / pin 保护 / 导出 | `TrashActivity.kt`, `VersionHistoryController.kt` |
| F16 | `storage/history` 与 `ui/storage` 没有任何 JVM 单测; 策略与路径逻辑与 `Uri` / `Pref` / Room 耦合不可测 | `app/src/test/java/org/autojs/autojs/` 无 `storage/history` 目录 |
| F17 | `VersionHistoryFileViewHolder` 以代码构建视图 (无布局文件, 无图标), 与 `item_trash.xml` 不一致; 两个列表页使用 `notifyDataSetChanged` 全量刷新 | `VersionHistoryFileViewHolder.kt:82-135`, `TrashAdapter.kt:22` |
| F18 | `HistoryDatabase` `exportSchema = false`, 无 schema 快照, 迁移不可回归验证 | `HistoryDatabase.kt:19` |

---

## 4. 阶段总览

### P0 缺陷修复与可测基座

- [x] P0.1 指纹语义修正与回退保护 (F1): `recordSavePre` 接收保存后内容的指纹并写入 `latestFingerprint`; 指纹回退只在旧路径已不存在时才重新绑定; 决策逻辑抽为 `HistoryFingerprintPolicy` 并单测. 证据: `HistoryFingerprintPolicyTest` (JVM), 2026-09-16.
- [x] P0.2 存储根目录统一 (F2): 新增 `HistoryPaths` (根目录来自 `EnvironmentUtils.externalStoragePath`, 失败时回退到 `/storage/emulated/0`), `HistoryUriUtils` / 两个控制器 / `ScriptOperations` / `EditorFileService` 改为共用; 路径解析为纯函数并单测. 证据: `HistoryPathsTest` (JVM), 2026-09-16.
- [x] P0.3 覆盖恢复前留快照 (F3, D4): 历史恢复到磁盘与回收站覆盖恢复文件前记录 `RESTORE_PRE` revision (受纳管大小上限约束, 失败不阻断恢复). 证据: `HistoryRepository.recordSnapshotBeforeOverwrite`, 2026-09-16.
- [x] P0.4 回收站文件恢复原子化 (F4): 先写同目录临时文件再 rename 替换, 失败或中止时清理临时文件; D2 不变量保持. 证据: `TrashRepository.restoreTrashItemToPath`, 2026-09-16.
- [x] P0.5 自动改名使用 `isDirectory` (F5): 逻辑抽为 `TrashRestoreNaming.nextIndexedName(existingNames, originalName, isDirectory)` 并单测. 证据: `TrashRestoreNamingTest` (JVM), 2026-09-16.
- [x] P0.6 历史对话框列出全部保留版本, 编辑器模式也提供预览, 删除后重载保持原模式 (F6). 证据: `VersionHistoryController`, 2026-09-16.
- [x] P0.7 保留策略单点化 (F7, D1): 新增纯对象 `HistoryRetentionPolicy` (`selectExpired` / `selectOverCount` / `selectOverBytes`), 仓库与 Worker 共用, `0` 或负数表示不限; 单测覆盖天数 / 条数 / 单文件容量 / 全局容量 / 0 值边界. 证据: `HistoryRetentionPolicyTest` (JVM), 2026-09-16.
- [x] P0.8 历史 orphan blob 清理增加 30 分钟最小年龄保护 (F9). 证据: `HistoryCleanupWorker.cleanupOrphanHistoryBlobs`, 2026-09-16.
- [x] P0.9 连续相同快照去重 (F10): 最新 revision 的 sha256 与大小均相同时不再写入新 blob, 只刷新 `lastSeenAt` 与指纹; 决策纳入 `HistoryFingerprintPolicy` 单测. 证据: `HistoryFingerprintPolicyTest` (JVM), 2026-09-16.
- [x] P0.10 编译 (`:app:compileAppDebugKotlin`) 与单测 (`:app:testAppDebugUnitTest --tests "org.autojs.autojs.storage.history.*"`) 通过, changelog (fix / improvement) 落盘, 本表证据回填. 证据: E1, JVM 单测 4 类 24/24 通过, changelog 10 语言 (fix 6 条 / improvement 2 条) 已落盘, 2026-09-16; 提交 `765331c77`.

### P1 策略设置与配额 (F7, F8, F11)

- [x] P1.1 设置页新增 "回收站与版本历史" 分类: 版本历史 (保留天数 / 每文件版本数 / 每文件容量 / 总容量 / 纳管文件大小上限), 回收站 (保留天数 / 总容量), 紧急草稿 (保留天数 / 总容量), "彻底删除时同时清除版本历史" 开关. 数值项摘要显示当前值, `0` 显示为 "不限制". 证据: 按 D9 落地为子页 `TrashAndVersionHistorySettingsActivity` (`res/xml/fragment_trash_and_version_history_settings.xml`, `RetentionLimitPreference` + `RetentionLimitDialogBuilder`, 规格表 `RetentionLimitSettings`), 主页分类含子页入口 + 开关; 单位换算 `RetentionLimitUnits` 有 `RetentionLimitUnitsTest` (JVM), 2026-09-16.
- [x] P1.2 策略变更即时生效: 确认对话框后 `HistoryCleanupScheduler.scheduleOnDemandCleanup` 排队唯一的一次性 Worker (REPLACE, 2 秒去抖); 清理过程抽为 `HistoryCleanup.runAll` (Worker 只做薄封装), 其中 `HistoryRepository.enforceRetentionPolicy` 应用天数 + 版本数 + 单文件容量 + 全局容量并移除不再拥有 revision 的 file entry, `TrashRepository.enforceRetentionPolicy` 应用天数 + 容量, 紧急草稿改为 D1 语义. 证据: 编译 + JVM 单测, 2026-09-16.
- [x] P1.3 回收站移入后执行容量策略; 待移入条目超过上限时移入前提示 (继续移入 / 彻底删除 / 取消, D8). 证据: `ScriptOperations.checkTrashQuotaThenRun` (单个与批量共用, IO 线程统计大小), 移入成功后 `enforceTrashQuotaAfterMove` 调用 `TrashRepository.enforceTotalBytesLimit(movedTrashIds)`; D7 保护逻辑见 `HistoryRetentionPolicyTest.protectedItemsSurviveBytesLimit` (JVM), 2026-09-16.
- [x] P1.4 回收站页与版本历史页菜单增加 "立即清理" 与 "设置" 入口; 抽屉条目长按打开对应设置. 证据: `menu_trash.xml` / `menu_version_history.xml` 的 `action_clean_up_now` / `action_trash_and_version_history_settings`, `HistoryCleanupUi.runNow` (IO 线程执行 + 完成 toast + 重新加载, 并发调用去重), `DrawerMenuItem.setLongClickAction` 与 `DrawerMenuItemViewHolder` 长按分发, 抽屉副标题在容量不限制时不再显示百分比, 2026-09-16.
- [x] P1.5 10 语言字符串与 changelog. 证据: 26 条字符串 x 11 个 `strings.xml` (`values` 与 `values-en` 均为英文), changelog feature 2 条 / improvement 2 条 x 10 语言并重新生成 markdown, 2026-09-16; 提交 hash 由 P2 首次提交回填.

### P2 版本历史精进 (F12, F13, F15)

- [ ] P2.1 重命名 / 移动迁移历史: `HistoryRepository.relocate(oldPath, newPath)` (文件精确匹配, 目录按前缀批量改写 `logicalPath`), 目标路径已有条目时合并 revisions 到目标 `fileId`; 接入 `ScriptOperations.rename` / `move` (含批量移动). 路径映射抽为纯函数并单测.
- [ ] P2.2 覆盖类写入统一留快照 (D4): 资源管理器复制 / 移动覆盖已有文件前记录 `OVERWRITE_PRE`; 复用 P0.3 的 `recordSnapshotBeforeOverwrite`.
- [ ] P2.3 按文件的版本列表页 (RecyclerView, 替代单选对话框): 显示时间 / 大小 / `op` / 编码 / "与当前文件一致" 标记 (sha 对比), 支持多选删除; 资源管理器与编辑器入口均切换到该页, 保留 "恢复到编辑器" 与 "恢复到磁盘" 两种模式.
- [ ] P2.4 差异视图: 所选 revision 与当前文件, 或两条 revision 之间的行级 diff (只读页面, 新增 / 删除行着色); 超过阈值的大文件退化为 "仅显示行数与大小统计". 实现选型见 Q4.
- [ ] P2.5 手动快照与标签: 编辑器菜单 "创建快照" (op = `MANUAL`, 可选标签); schema v3 增加 `label TEXT` 与 `pinned INTEGER NOT NULL DEFAULT 0` 列 (D3); pinned 的 revision 不参与天数 / 版本数 / 单文件容量清理, 但仍受全局总容量的最终保护.
- [ ] P2.6 revision 导出: "另存为" 到任意目录 (复用 `FileChooserDialogBuilder`), 保留原编码与 BOM.
- [ ] P2.7 编辑器 "恢复并保存" 选项 (现有 "仅恢复到编辑器" 保持默认).

### P3 回收站精进 (F14, F15)

- [ ] P3.1 多选模式: 批量恢复 (原路径 / 指定目录, 冲突逐项询问或统一策略) 与批量彻底删除, 进度对话框可中止.
- [ ] P3.2 内容预览: 文本文件以只读编辑器打开 (复用 `PREVIEW_INTENT_MAX_BYTES` 与临时文件策略), 目录显示 zip 条目列表 (相对路径 / 大小 / 数量).
- [ ] P3.3 条目详情对话框: 原路径 / 移入时间 / 大小 / sha256 / 预计到期时间, 逐行可复制.
- [ ] P3.4 移入回收站后 Snackbar "撤销" (仅在原路径仍空闲时可用, 复用 `restoreTrashItemToPathWithoutOverwrite`).
- [ ] P3.5 列表显示到期倒计时 (基于回收站保留天数), 24 小时内到期的条目高亮; 到期计算抽为纯函数并单测.
- [ ] P3.6 失效条目处理: blob 缺失 / 损坏时给出本地化提示并提供 "移除失效条目"; 周期任务把 blob 缺失的条目标记出来 (不自动删除).
- [ ] P3.7 回收站条目关联 history: 移入时按路径查 `FileEntry` 写入 `TrashItem.fileId` (现恒为 null), 恢复到新路径时调用 P2.1 的 `relocate`.

### P4 质量, 验证与文档

- [ ] P4.1 单测扩展: relocate 路径映射, 配额提示决策, 到期计算, zip 条目列表解析.
- [ ] P4.2 Room `exportSchema = true` 并提交 `app/schemas/` 快照, 2 -> 3 迁移有可回归的 schema 文件 (F18).
- [ ] P4.3 zip 解压的 canonical path 检查与损坏 zip 的容错 (单个条目失败时记录并继续, 最终提示).
- [ ] P4.4 两个列表页改用 `ListAdapter` + `DiffUtil`; `VersionHistoryFileViewHolder` 改用布局文件并加文件图标 (F17).
- [ ] P4.5 真机 / AVD 冒烟 (附录 B) 并回填证据.
- [ ] P4.6 `docs/dev/trash-and-version-history-architecture.md` (数据模型 / blob 布局 / 清理时序 / 崩溃恢复不变量 / 插件宿主依赖面), 以及文档项目中用户可见说明 (若有对应页面).

---

## 附录 A. 待决事项

| 编号 | 问题 | 建议 |
| --- | --- | --- |
| Q1 | 历史快照 blob 是否 gzip 压缩 (schema 加 `compression TEXT` 列, 读取时按列解码)? | 建议在 P2.5 的 schema v3 中一并加列但默认不压缩, 压缩作为设置项后续开放. |
| Q2 | 脚本 API (`files.write` / `files.append` 等) 的写入是否纳入版本历史? | 建议不纳管 (热路径, 且脚本写入多为数据文件), 维持 D4 的 "应用自身覆盖写入" 范围. |
| Q3 | P1.3 单个条目超过回收站上限时的默认动作? | 已拍板, 见 D8. |
| Q4 | P2.4 diff 的实现选型: 引入 `java-diff-utils` 依赖, 还是自实现 Myers 行 diff? | 建议自实现最小 Myers (约 150 行, 无新依赖, 可单测), 大文件用阈值退化. |
| Q5 | 版本历史是否记录 `SAVE_PRE` 之外的 "保存后" 内容 (即每次保存两条)? | 建议不记录, 当前 "保存前 + 磁盘现状" 已覆盖全部状态; 改为在列表中标记 "与当前文件一致". |

## 附录 B. 冒烟清单 (P4.5)

1. 编辑器保存三次后打开版本历史, 列表条数与内容正确, 预览与 "恢复到编辑器" / "恢复到磁盘" 各一次, 恢复到磁盘后历史中多出一条 `RESTORE_PRE`.
2. 资源管理器重命名文件后再保存, 版本历史仍关联到新路径 (P0.1 / P2.1).
3. 复制文件 `a.js` 为 `b.js`, 编辑并保存 `b.js`, `a.js` 的历史不受影响 (P0.1).
4. 目录 `my.project` 移入回收站, 在原路径新建同名目录后恢复并选 "自动改名", 得到 `my.project-1` (P0.5).
5. 大文件 (>50 MB) 覆盖恢复时中止, 原文件内容完整 (P0.4).
6. 修改设置中的保留天数为 1 并等待清理任务, 历史条目按预期消失 (P1.2).
7. 多用户 / 工作资料环境 (可选): 历史与回收站可用 (P0.2).

## 附录 C. 证据等级

| 等级 | 含义 |
| --- | --- |
| E0 | 代码已提交, 编译通过 |
| E1 | E0 + JVM 单测通过 |
| E2 | E1 + AVD 冒烟通过 (记录 API level) |
| E3 | E2 + 真机冒烟通过 (记录机型) |

## 附录 D. 文件地图

| 关注点 | 文件 |
| --- | --- |
| 数据模型 | `storage/history/HistoryEntities.kt`, `TrashItemEntity.kt`, `HistoryDao.kt`, `HistoryDatabase.kt` |
| 策略 | `storage/history/HistoryPrefs.kt`, `HistoryRetentionPolicy.kt` (P0.7), `HistoryFingerprintPolicy.kt` (P0.1), `RetentionLimitUnits.kt` (P1.1) |
| 路径 | `storage/history/HistoryPaths.kt` (P0.2), `HistoryUriUtils.kt` |
| 仓库与 blob | `storage/history/HistoryRepository.kt`, `HistoryBlobStore.kt`, `TrashRepository.kt`, `TrashBlobStore.kt` |
| 清理 | `storage/history/HistoryCleanup.kt` (P1.2, 全部清理过程), `HistoryCleanupUi.kt` (P1.4), `HistoryCleanupScheduler.kt`, `HistoryCleanupWorker.kt`, `TrashPartialCleanupWorker.kt` |
| 交互 | `storage/history/VersionHistoryController.kt`, `TrashRestoreController.kt`, `TrashRestoreNaming.kt` (P0.5) |
| 页面 | `ui/storage/TrashActivity.kt`, `VersionHistoryActivity.kt`, `BaseSearchSortListActivity.kt` |
| 设置 | `ui/settings/TrashAndVersionHistorySettingsActivity.kt` / `...Fragment.kt` / `...Preference.kt`, `RetentionLimitSettings.kt`, `RetentionLimitPreference.kt`, `RetentionLimitDialogBuilder.kt`, `res/xml/fragment_trash_and_version_history_settings.xml`, `res/xml/fragment_preferences.xml` (分类) |
| 接入 | `ui/edit/editor/service/EditorVersionHistoryService.kt`, `ui/common/ScriptOperations.java`, `ui/explorer/ExplorerBuiltInActionHandler.kt`, `ui/main/drawer/DrawerFragment.kt`, `App.kt` |
| 单测 | `app/src/test/java/org/autojs/autojs/storage/history/*Test.kt` |
