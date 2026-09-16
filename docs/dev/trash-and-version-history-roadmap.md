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
| D10 | 手动快照默认 pinned; pinned 只豁免单文件清理 | 用户主动创建的快照不应被 "最多 N 个版本" 挤掉, 因此 `recordManualSnapshot` 写入时 `pinned = 1`, 可在版本列表取消; 与最新 revision 内容相同时改为给该 revision 打标签并固定, 不重复存 blob. `cleanupPerFile` 先排除 pinned (不计入天数 / 条数 / 容量), `cleanupGlobal` 先删未固定的最旧 revision, 只有全部为 pinned 时才删 pinned, 保证全局上限仍是硬上限. |

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
- [x] P1.5 10 语言字符串与 changelog. 证据: 26 条字符串 x 11 个 `strings.xml` (`values` 与 `values-en` 均为英文), changelog feature 2 条 / improvement 2 条 x 10 语言并重新生成 markdown, 2026-09-16; 提交 `782e512f2`.

### P2 版本历史精进 (F12, F13, F15)

- [x] P2.1 重命名 / 移动迁移历史: `HistoryRepository.relocate(oldPath, newPath)` (文件精确匹配, 目录按前缀批量改写 `logicalPath`), 目标路径已有条目时合并 revisions 到目标 `fileId` (`HistoryDao.moveRevisionsToFile`, 目标条目沿用被移动文件的指纹); 接入 `ScriptOperations.rename` (IO 线程异步) / 单个与批量 `move` (移动成功后同步调用). 路径映射抽为纯函数 `HistoryRelocation`. 证据: `HistoryRelocationTest` (JVM, 5 例), 2026-09-16.
- [x] P2.2 覆盖类写入统一留快照 (D4): 资源管理器单个 (`confirmOverwriteIfNeeded`) 与批量 (`executeCopyMovePlans`) 复制 / 移动覆盖前调用 `snapshotBeforeOverwrite`: 文件走 `recordSnapshotBeforeOverwrite(OP_OVERWRITE_PRE)`, 目录走 `recordSnapshotsBeforeOverwriteUnderDirectory` (只为已纳管文件留快照, 代价受历史规模约束). 证据: 编译 + JVM 单测, 2026-09-16; 提交 `777398c1e`.
- [x] P2.3 按文件的版本列表页 `ui/storage/RevisionListActivity` (基于 `BaseSearchSortListActivity`, 替代单选对话框): 每行显示时间 / 大小 / `op` 标签 / 编码 (含 BOM) / "当前" 标记 (磁盘文件 sha256 与 revision 对比, 文件缺失或超过纳管上限则不标记); 点击弹出详情 (预览 / 恢复 / 删除), 长按进入多选, 菜单提供批量删除 / 全选 / 取消全选 / 排序 (时间, 大小) / 打开文件. 两种模式由 `VersionHistoryController.Mode` 区分: 资源管理器 `RESTORE_TO_DISK` (覆盖前留 `RESTORE_PRE` 快照), 编辑器 `RESTORE_TO_EDITOR` (回调经 `VersionHistoryEditorBridge` 以弱引用 token 跨 Activity 传递, 编辑器已销毁时提示 "编辑器已不可用"). 证据: 编译 + JVM 单测 (34 例), 2026-09-16; 提交 `f8bec51ed`.
- [x] P2.4 差异视图 `ui/storage/DiffActivity` (基于 `BaseSearchSortListActivity`): 版本列表的详情对话框提供 "与当前文件比较", 多选恰好两条时菜单提供 "比较选中项" (较旧者在左); 逐行显示左右行号 / 标记 / 文本, 新增与删除行着色 (`diff_line_inserted_bg` / `diff_line_deleted_bg`, 含夜间色), 未变更段落保留 3 行上下文后折叠, 点击或 "全部展开" 展开, 搜索框按文本过滤行; 任一侧超过 6000 行或 1 MiB 时退化为仅显示两侧行数与大小 (`LineDiff.exceedsLimits`). 算法按 Q4 自实现: `storage/history/LineDiff` 为线性空间 Myers (中间蛇 + 显式工作栈, 行先 intern 为整数, 每段变更内先删除后新增); `HistoryTextCodec` 统一 revision 与磁盘文件的解码. 磁盘文件按 revision 记录的编码解码, 编辑器入口比较的也是磁盘文件而非未保存缓冲. 证据: `LineDiffTest` (JVM, 8 例, 含 400 组随机输入的重建与 LCS 最小性校验), 2026-09-16; 提交 `c8ae91354`.
- [x] P2.5 手动快照与标签: 编辑器菜单 "创建快照" (`EditorView.showCreateSnapshotDialog`, 与版本历史相同的忙碌守卫; 输入可选标签后把当前编辑器文本按当前编码 / BOM 编码为字节, `HistoryRepository.recordManualSnapshot` 写入 op = `MANUAL`); schema v3 (`HistoryDatabase` version 3, `MIGRATION_2_3`: `ALTER TABLE revision ADD COLUMN label TEXT` / `pinned INTEGER NOT NULL DEFAULT 0`, 实体字段带默认值, D3). 规则 (D10): 手动快照默认 pinned; 与最新 revision 内容相同时不重复存 blob, 改为给该 revision 打标签并固定; `cleanupPerFile` 先排除 pinned 再套用天数 / 条数 / 容量; `cleanupGlobal` 先删未固定的最旧 revision (`listOldestUnpinnedRevisions`), 没有未固定的才动 pinned. 版本列表: 标题显示标签, 副标题显示 "已固定", 详情对话框提供 "固定 / 取消固定" 与 "编辑标签" (`HistorySnapshotLabel.normalizeOrNull`: 去首尾空白, 折叠内部空白, 上限 80 字符). Q1 的 `compression` 列未随 v3 加入 (无消费者, 留待需要时再加). 证据: `HistorySnapshotLabelTest` (JVM, 3 例) + 编译 + 全部 history 单测 (45 例), 2026-09-16; 迁移未在设备上验证, 列入 P4.5 冒烟; 提交 `10b929898`.
- [x] P2.6 revision 导出 (`VersionHistoryController.exportRevision`): 详情对话框 "导出" 选项, `FileChooserDialogBuilder.chooseDir()` 选目录 (根为内部存储), 输入对话框确认文件名 (建议名由纯函数 `HistoryExportNaming.suggestName` 生成: 原文件名的最后一个扩展名前插入 `yyyyMMdd-HHmmss` 时间戳; `isValidFileName` 拒绝空名 / `.` / `..` / 含路径分隔符), 同名时询问覆盖; 写出的是 blob 原始字节 (编码与 BOM 天然保留), 走 tmp + 校验 + rename; 覆盖内部存储中的已有文件前按 D4 记 `OVERWRITE_PRE`, 写完通知资源管理器刷新父目录. 证据: `HistoryExportNamingTest` (JVM, 3 例) + 编译 + 全部 history 单测 (48 例), 2026-09-16; 提交 `38b4e9919`.
- [x] P2.7 编辑器 "恢复并保存": 仅 `RESTORE_TO_EDITOR` 模式的详情对话框提供该选项 (positive 按钮仍是 "恢复", 只改编辑器内容并走 "不再提示" 确认); "恢复并保存" 走与写回磁盘相同的覆盖确认 (`text_version_history_restore_will_overwrite_file`), 然后经 `VersionHistoryEditorBridge.Callbacks.onRestoreToEditorAndSave` 载入编辑器并调用 `EditorView.saveFile()` (与手动保存同一路径, 因此仍会先记 `SAVE_PRE`). 证据: 编译 + 全部 history 单测 (48 例), 2026-09-16; 提交 `c87d33d8a`.

### P3 回收站精进 (F14, F15)

- [x] P3.1 多选模式: `TrashAdapter` 长按进入多选 (与 P2.3 的 revision 列表同一套模式: 全选 / 取消全选 / 返回键退出, 工具栏显示已选数量); 菜单 "恢复选中项" 走 `TrashRestoreController.startBatchRestoreFlow`: 选目标 (各自原父目录 / 统一目录), 在 IO 线程用纯函数 `TrashBatchPlanner.plan` 预判冲突, 有冲突时整批三选一 (跳过 / 覆盖 / 自动改名; 批次内同名条目即使选 "覆盖" 也自动改名, 避免覆盖刚恢复的文件), 执行时使用可中止的确定进度对话框 (逐项更新 "i / n" 与文件名, 覆盖文件前记 `RESTORE_PRE`, 中止时清理未完成的目录), 结束 toast 汇总恢复 / 跳过 / 失败; 菜单 "从回收站移除" 批量删除 (确认后逐项 `deleteTrashItem`). 顺带修复: `withControllableProgressDialog` 吞掉非中止异常导致单个恢复失败仍提示 "完成", 现在向外抛出. 证据: `TrashBatchPlannerTest` (JVM, 6 例) + 编译 + 全部 history 单测 (54 例), 2026-09-16; 提交 `e4eda89cf`.
- [x] P3.2 内容预览 (`TrashPreviewController`): 条目对话框 "预览" 选项. 文件: 先嗅探前 8 KiB, 含 NUL 视为二进制并提示 (`text_binary_content_cannot_be_previewed`); 文本则复制 blob 到 `TmpScriptFiles` 临时文件 (沿用原扩展名以便高亮) 后 `EditActivity.viewPath` 只读打开 (不再区分大小走 Intent extras, 一律临时文件). 目录: `TrashArchiveListing.read` 仅读 zip 中央目录 (不解压即得大小), 对话框显示 "N 个文件, M 个文件夹 | 总大小" 与最多 500 条 "相对路径 (大小)", 超出显示 "及其他 N 项", 文本可选中. 证据: `TrashArchiveListingTest` (JVM, 3 例, 用临时 zip) + 编译 + 全部 history 单测 (57 例), 2026-09-16; 提交 `b05a50dd9`.
- [x] P3.3 条目详情对话框 (`TrashPreviewController.showDetails`): 名称 / 类型 / 原路径 / 移入时间 / 大小 / SHA-256 (仅文件) / 到期时间 (保留天数不限时显示 "不限制", 否则为时刻加 "剩余 N 天 M 小时" / "剩余 M 小时" / "已到期"); 内容 `setTextIsSelectable` 可逐行选中复制, neutral 按钮一键复制全文. 到期计算抽为纯函数 `TrashExpiry` (`expiresAtOrNull` = 移入时刻 + 天数, 与 `HistoryRetentionPolicy.selectExpired` 的边界一致; `remaining` 给出天 / 小时 / 已过期 / 不足一天), P3.5 复用. 证据: `TrashExpiryTest` (JVM, 3 例, 含与 selectExpired 的边界对照) + 编译 + 全部 history 单测 (60 例), 2026-09-16; 提交 hash 由 P3.5 提交回填.
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
| Q1 | 历史快照 blob 是否 gzip 压缩 (schema 加 `compression TEXT` 列, 读取时按列解码)? | 建议在 P2.5 的 schema v3 中一并加列但默认不压缩, 压缩作为设置项后续开放. 2026-09-16: v3 未加该列 (无消费者的列徒增迁移面), 需要时按 D3 走 v4. |
| Q2 | 脚本 API (`files.write` / `files.append` 等) 的写入是否纳入版本历史? | 建议不纳管 (热路径, 且脚本写入多为数据文件), 维持 D4 的 "应用自身覆盖写入" 范围. |
| Q3 | P1.3 单个条目超过回收站上限时的默认动作? | 已拍板, 见 D8. |
| Q4 | P2.4 diff 的实现选型: 引入 `java-diff-utils` 依赖, 还是自实现 Myers 行 diff? | 已拍板: 自实现线性空间 Myers (`LineDiff`, 无新依赖, 随机输入对照 LCS 单测), 大文件用阈值退化. |
| Q5 | 版本历史是否记录 `SAVE_PRE` 之外的 "保存后" 内容 (即每次保存两条)? | 建议不记录, 当前 "保存前 + 磁盘现状" 已覆盖全部状态; 改为在列表中标记 "与当前文件一致". |

## 附录 B. 冒烟清单 (P4.5)

1. 编辑器保存三次后打开版本历史, 列表条数与内容正确, 预览与 "恢复到编辑器" / "恢复到磁盘" 各一次, 恢复到磁盘后历史中多出一条 `RESTORE_PRE`.
2. 资源管理器重命名文件后再保存, 版本历史仍关联到新路径 (P0.1 / P2.1).
3. 复制文件 `a.js` 为 `b.js`, 编辑并保存 `b.js`, `a.js` 的历史不受影响 (P0.1).
4. 目录 `my.project` 移入回收站, 在原路径新建同名目录后恢复并选 "自动改名", 得到 `my.project-1` (P0.5).
5. 大文件 (>50 MB) 覆盖恢复时中止, 原文件内容完整 (P0.4).
6. 修改设置中的保留天数为 1 并等待清理任务, 历史条目按预期消失 (P1.2).
7. 多用户 / 工作资料环境 (可选): 历史与回收站可用 (P0.2).
8. 已安装 schema v2 的版本 (含至少一条 revision) 升级到本版后打开版本列表, 旧 revision 正常显示且未固定 / 无标签 (P2.5 `MIGRATION_2_3`); 编辑器 "创建快照" 一次后列表出现 `手动快照` 且 "已固定", 连续创建两次相同内容不新增条目.
9. 把 "每个文件最多版本数" 设为 1 后再保存两次, pinned 的快照仍在, 未固定的只剩最新一条 (P2.5 / D10).

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
| 策略 | `storage/history/HistoryPrefs.kt`, `HistoryRetentionPolicy.kt` (P0.7), `HistoryFingerprintPolicy.kt` (P0.1), `HistorySnapshotLabel.kt` (P2.5), `HistoryExportNaming.kt` (P2.6), `TrashExpiry.kt` (P3.3 / P3.5), `RetentionLimitUnits.kt` (P1.1) |
| 路径 | `storage/history/HistoryPaths.kt` (P0.2), `HistoryRelocation.kt` (P2.1), `HistoryUriUtils.kt` |
| 仓库与 blob | `storage/history/HistoryRepository.kt`, `HistoryBlobStore.kt`, `TrashRepository.kt`, `TrashBlobStore.kt` |
| 清理 | `storage/history/HistoryCleanup.kt` (P1.2, 全部清理过程), `HistoryCleanupUi.kt` (P1.4), `HistoryCleanupScheduler.kt`, `HistoryCleanupWorker.kt`, `TrashPartialCleanupWorker.kt` |
| 交互 | `storage/history/VersionHistoryController.kt`, `VersionHistoryEditorBridge.kt` (P2.3), `HistoryTextCodec.kt` (P2.4), `TrashRestoreController.kt`, `TrashRestoreNaming.kt` (P0.5), `TrashBatchPlanner.kt` (P3.1, 纯函数), `TrashPreviewController.kt` / `TrashArchiveListing.kt` (P3.2) |
| 差异 | `storage/history/LineDiff.kt` (P2.4, 纯函数), `ui/storage/DiffActivity.kt` / `DiffLineAdapter.kt` (P2.4) |
| 页面 | `ui/storage/TrashActivity.kt`, `VersionHistoryActivity.kt`, `RevisionListActivity.kt` / `RevisionAdapter.kt` / `RevisionViewHolder.kt` (P2.3), `BaseSearchSortListActivity.kt` |
| 设置 | `ui/settings/TrashAndVersionHistorySettingsActivity.kt` / `...Fragment.kt` / `...Preference.kt`, `RetentionLimitSettings.kt`, `RetentionLimitPreference.kt`, `RetentionLimitDialogBuilder.kt`, `res/xml/fragment_trash_and_version_history_settings.xml`, `res/xml/fragment_preferences.xml` (分类) |
| 接入 | `ui/edit/editor/service/EditorVersionHistoryService.kt`, `ui/common/ScriptOperations.java`, `ui/explorer/ExplorerBuiltInActionHandler.kt`, `ui/main/drawer/DrawerFragment.kt`, `App.kt` |
| 单测 | `app/src/test/java/org/autojs/autojs/storage/history/*Test.kt` |
