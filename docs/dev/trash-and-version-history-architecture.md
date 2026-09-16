# AutoJs6 回收站与版本历史架构 (Trash and Version History Architecture)

本文是回收站 (Trash) 与版本历史 (Version History) 子系统的架构说明, 与 [trash-and-version-history-roadmap.md](trash-and-version-history-roadmap.md) 配套:
Roadmap 负责 "做什么, 做到哪了" (含固定决策 D1-D10 与证据), 本文负责 "现在长什么样, 为什么这样, 改动时要守住什么".

维护约定:

1. 本文描述当前 `master` 的真实结构, 以符号名 (类 / 方法 / 常量) 为锚点, 不引用行号.
2. 与 Roadmap 决策表冲突时以 Roadmap 为准并回改本文.
3. 只用半角标点.

---

## 1. 总览

两套功能共用一个 Room 数据库 (`HistoryDatabase`, 文件名 `history-database.db`) 与 `filesDir` 下的 blob 目录, 共用一套保留策略 (`HistoryPrefs` + `HistoryRetentionPolicy`) 与一套清理过程 (`HistoryCleanup`).

| 关注点 | 版本历史 | 回收站 |
| --- | --- | --- |
| 记录什么 | 文件被覆盖前的内容快照 (`revision`), 以及用户主动创建的快照 | 被移入回收站的文件 / 目录的完整内容 (`trash_item`) |
| 触发点 | 编辑器保存前 (`SAVE_PRE`), 历史恢复到磁盘前 (`RESTORE_PRE`), 资源管理器复制 / 移动覆盖前 (`OVERWRITE_PRE`), 编辑器 "创建快照" (`MANUAL`) | 资源管理器 "移入回收站" (单个 / 批量), 插件宿主会话的批量移入 |
| 键 | `file_entry.logicalPath` (规范化路径), 由指纹策略处理重命名 / 复制 | `trash_item.trashId` (UUID) |
| 内容存放 | `filesDir/history/blob/<fileId>/<revId>.bin` (原始字节) | `filesDir/trash/blob/<trashId>.bin` (文件: 原始字节; 目录: zip) |
| 页面 | `VersionHistoryActivity` (文件列表) -> `RevisionListActivity` (版本列表) -> `DiffActivity` | `TrashActivity` |
| 核心类 | `HistoryRepository`, `HistoryBlobStore`, `VersionHistoryController` | `TrashRepository`, `TrashBlobStore`, `TrashRestoreController`, `TrashPreviewController` |

所有 DB 与 blob 访问都在 IO 线程 (`Schedulers.io()` / `Observable.fromCallable` / Worker), 主线程只做 UI. 只有内部存储 (`HistoryPaths.internalStorageRoot`, 取自 `EnvironmentUtils.externalStoragePath`, 通常为 `/storage/emulated/0`) 下的路径被纳管 (`HistoryPaths.isUnderInternalStorage`).

## 2. 数据模型 (Room, schema version 3)

实体定义在 `HistoryEntities.kt` / `TrashItemEntity.kt`, DAO 为 `HistoryDao`. schema 快照位于 `app/schemas/org.autojs.autojs.storage.history.HistoryDatabase/{1,2,3}.json` (3.json 由 KSP 生成, 1 / 2 按迁移 SQL 反推, 见 Roadmap P4.2).

| 表 | 主键 | 列 | 索引 |
| --- | --- | --- | --- |
| `file_entry` | `fileId` (UUID) | `logicalPath`, `createdAt`, `lastSeenAt`, `latestFingerprint` (最近一次写入完成后磁盘内容的 SHA-256) | `logicalPath` (唯一), `latestFingerprint`, `lastSeenAt` |
| `revision` | `revId` (UUID) | `fileId`, `op`, `createdAt`, `logicalPathAtThatTime`, `encoding`, `hadBom`, `sizeBytes`, `sha256`, `blobRelPath`, `label` (v3, 可空), `pinned` (v3, 默认 0) | `fileId`, `createdAt` |
| `trash_item` | `trashId` (UUID) | `originalPath`, `fileId` (移入时拥有该路径的 history 条目, 目录与未纳管文件为 null, 仅供参考), `trashedAt`, `isDirectory`, `sizeBytes`, `sha256`, `blobRelPath` | `originalPath`, `trashedAt` |

迁移: `MIGRATION_1_2` 新建 `trash_item`; `MIGRATION_2_3` 为 `revision` 增加 `label` / `pinned`. 规则 (D3): 只增不减, 只允许带默认值的 `ADD COLUMN` 或新表, 必须写显式 `Migration`, 不用 destructive fallback. 回归载体是 `androidTest` 的 `HistoryDatabaseMigrationTest` (按 1.json / 2.json 的 CREATE 语句建旧库, 经 `HistoryDatabase.createBuilder` 打开触发迁移并由 Room 校验).

`revision.op` 的取值: `SAVE_PRE` / `RESTORE_PRE` / `OVERWRITE_PRE` / `MANUAL` (`HistoryRepository` 的 `OP_*` 常量), UI 文案由 `VersionHistoryController.opLabel` 映射.

## 3. blob 布局

```
<filesDir>/
  history/blob/<fileId>/<revId>.bin        revision 内容 (原始字节, 未压缩)
  trash/blob/<trashId>.bin                 回收站条目内容 (文件原样; 目录为 zip, 相对路径以 '/' 分隔, 空目录有显式 "<dir>/" 条目)
  trash/blob/<trashId>.partial.<uuid>.bin  正在写入的回收站 blob (提交前的临时文件)
  drafts/                                  编辑器紧急草稿 (由 EmergencyDraftDirectoryGuard 加锁, 清理时一并处理)
```

- `HistoryBlobStore.writeRevisionBlob` 直接写最终文件; revision 先写 blob 再插 DB 行, 因此 "有 blob 无行" 是允许的中间态, 由孤儿清理兜底 (见第 5 节).
- `TrashBlobStore` 先写 `.partial` 临时文件, `commitTrashBlobTemp` 对文件 `fsync` 后同目录 `rename` 到最终名, 再对目录 `fsync`. 最终名已存在时拒绝提交 (`check`).
- `TrashRepository.isBlobMissing` 判定 "失效条目" (DB 行在而 blob 不在); 这类条目由页面标记, 只能查看详情或移除, 周期清理只记录日志不删除 (P3.6).

## 4. 写入路径

### 4.1 记录快照 (`HistoryRepository.recordSnapshot`)

1. 入口: `recordSnapshotBeforeOverwrite(path, op, newContentSha256)` 读取磁盘当前内容 (受 `maxFileSizeToTrackBytes` 约束), 编辑器保存路径直接传入编辑器字节与编码; `recordManualSnapshot(path, bytes, encoding, hadBom, label)` 用于手动快照; `recordSnapshotsBeforeOverwriteUnderDirectory` 用于目录级覆盖.
2. 找到或创建 `file_entry` (`getOrCreateFileEntry`), 决策由纯对象 `HistoryFingerprintPolicy.decideAttach` 给出:
   - 路径已有条目 -> 沿用 (`EXISTING_BY_PATH`);
   - 路径无条目但某条目的 `latestFingerprint` 等于当前内容且该条目的旧路径已不存在 -> 视为钩子之外的重命名 / 移动, 把条目改到新路径 (`REATTACH_BY_FINGERPRINT`);
   - 否则新建. 旧路径仍存在时绝不按指纹重绑, 以免复制出的副本 "偷走" 原文件的历史 (P0.1).
3. 与该文件最新 revision 内容相同 (`isDuplicateSnapshot`: sha256 + 大小) 时不重复写; 手动快照在此情况下改为给最新 revision 打标签并固定 (D10).
4. 写 blob -> 插 `revision` -> `cleanupPerFile` (天数 / 条数 / 单文件容量, 固定的 revision 不计入也不删除) -> `cleanupGlobal` (总容量, 先删未固定的最旧项, 都固定时才动固定的).

### 4.2 路径跟随 (`HistoryRepository.relocate`)

资源管理器的重命名 / 移动 / 覆盖, 以及回收站恢复到非原路径 (P3.7), 都调用 `relocate(oldPath, newPath)`: 文件精确匹配, 目录按 `old/` 前缀 (`HistoryRelocation.mapPath`), 目标路径已有条目时把 revision 合并过去并沿用被移动文件的指纹. 该方法不抛异常, 失败只打印.

### 4.3 移入回收站 (`TrashRepository.moveToTrashWithProgress`)

顺序固定, 是崩溃安全的核心:

1. 校验 `trashId` 为 UUID, 源存在; 目录大小在后台线程并行统计以驱动进度.
2. 打开 `.partial` 临时 blob, 文件流式拷贝 / 目录流式打包为 zip, 同时计算 SHA-256 与字节数 (可取消: `OperationController.throwIfCancelled`).
3. `commitTrashBlobTemp` (fsync + rename + 目录 fsync).
4. 构造 `TrashItem` (含 `fileId`), 调用可选的 `beforeSourceDelete` 让能力受限的调用方 (插件宿主) 拒绝 "打包期间源已变化" 的情况.
5. `dao.upsertTrashItem` 落库 (`recoveryPersisted = true`).
6. 删除源. 删除失败且源仍存在时抛异常但保留记录 (目录可能已删掉一部分, 记录是唯一副本).

失败处理: 第 5 步之前失败 -> 删掉 DB 行 (若有) 与 blob (已提交则删最终文件, 否则删临时文件) 后原样抛出; 第 5 步之后失败 -> 抛 `TrashMoveRecoveryRequiredException(trashItem, cause)`, 调用方据此提示用户从回收站恢复. 调用方 (`ScriptOperations`) 在成功后执行 `enforceTrashQuotaAfterMove` (D7, 刚移入的批次受保护) 并显示带 "撤销" 的 Snackbar (P3.4).

### 4.4 从回收站恢复 (`TrashRepository.restoreTrashItemToPath`)

- blob 缺失 -> `TrashBlobMissingException`.
- 文件: 写到同目录隐藏临时文件 `.<name>.restore.<8位>.tmp`, `fsync` 后 `rename` 覆盖目标 (rename 失败时先删目标再 rename); 中止或崩溃不会在目标位置留下半成品.
- 目录: `TrashArchiveExtractor.extract` 解压. 每个条目名按目标目录的 canonical path 解析 (`resolveEntry`), 绝对路径 / 含 `..` / 越界的条目跳过并计入 `skippedUnsafe`; 单个条目读取失败 (截断 / CRC) 记录到 `failed`, 删除半成品后继续; 本地头不可读时记录 `(unreadable remainder)` 并停止. 有失败条目时抛 `TrashArchiveExtractor.PartialFailure(report)` 且保留回收站记录与 blob (P4.3).
- 成功后删除 DB 行与 blob, 再按 4.2 迁移版本历史 (目标路径与原路径不同时).
- `restoreTrashItemToPathWithoutOverwrite` 先用 `mkdir` / `createNewFile` 占位, 确保绝不覆盖已有数据, 供插件宿主与 "撤销" 使用. 覆盖恢复前由 `TrashRestoreController` 先为目标记录 `RESTORE_PRE` 快照 (D4).

## 5. 清理时序

调度 (`HistoryCleanupScheduler`, 在 `App` 启动时注册):

| 触发 | Worker / 入口 | 时机 | 内容 |
| --- | --- | --- | --- |
| 启动一次性 | `TrashPartialCleanupWorker` (`scheduleStartupCleanup`, `ExistingWorkPolicy.KEEP`) | 启动后 10 s | 删除 30 分钟以上的 `.partial` 回收站 blob |
| 周期 | `HistoryCleanupWorker` (`schedulePeriodicCleanup`, 唯一名 `history_cleanup_periodic`, `KEEP`) | 每 1 天 | `HistoryCleanup.runAll` |
| 设置变更 | `HistoryCleanupWorker` (`scheduleOnDemandCleanup`, 唯一名 `history_cleanup_on_demand`, `REPLACE`) | 保留策略数值改动后 2 s | `HistoryCleanup.runAll` |
| 菜单 "立即清理" | `HistoryCleanupUi.runNow` | 立即, IO 线程 | `HistoryCleanup.runAll`, 完成后刷新页面 |

`HistoryCleanup.runAll` 的固定顺序:

1. `runRetention`: `HistoryRepository.enforceRetentionPolicy` (每个文件的天数 / 条数 / 容量, 然后全局容量, 最后删除不再拥有 revision 的 `file_entry`), `TrashRepository.enforceRetentionPolicy` (天数过期 -> 总容量, D7 始终保留最新条目), 紧急草稿 (天数 / 容量, 受 `EmergencyDraftDirectoryGuard` 保护的文件除外).
2. 孤儿 history blob: 不在 `revision.blobRelPath` 集合中且 30 分钟以上 (`ORPHAN_BLOB_MIN_AGE_MS`) 的文件删除, 空目录清理.
3. 孤儿 trash blob: 同上, 集合来自 `trash_item.blobRelPath`.
4. 紧急草稿再清理一遍 (与步骤 1 同一函数, 幂等).
5. `reportTrashItemsWithMissingBlobs`: 统计 blob 缺失的回收站条目, 只 `Log.w`, 不删除.

保留策略语义 (D1): 天数 / 条数 / 容量为 0 或负数一律表示 "不限制", 单点实现于 `HistoryRetentionPolicy`; 到期边界与页面倒计时 (`TrashExpiry`) 对齐 (`trashedAt + days * DAY_MS` 起视为过期).

## 6. 崩溃恢复不变量

改动写入路径时必须保持以下不变量 (对应第 4 / 5 节):

1. 回收站: 源文件被删除之前, 其内容 blob 已 `fsync` 并以最终名存在, 且 DB 行已写入. 因此任何时刻用户可见的回收站条目都可恢复, 除非外部删除了 blob (此时被标记为失效, 不自动清除).
2. 回收站: `.partial` 文件永远不被 DB 引用; 它要么被 `commitTrashBlobTemp` rename 掉, 要么被失败清理 / 启动清理 (30 分钟) 删除.
3. 历史: blob 先于 DB 行写入, 所以孤儿清理必须保留 30 分钟内的 blob, 否则会与尚未提交的写入竞争.
4. 恢复: 目标文件的覆盖是 "临时文件 + rename", 中止 (`OperationAbortedException`) 后调用方删除新建的目标 (仅当恢复前不存在), 从不删除原有文件.
5. 目录归档: 条目名永远不能解析到目标目录之外; 部分失败不删除回收站记录.
6. 数据库: schema 只增不减, 迁移显式, 打开数据库不使用 destructive fallback.
7. 线程: 主线程不访问 Room / blob; `HistoryRepository.relocate` 与 `recordSnapshotBeforeOverwrite` 吞掉异常 (历史是辅助功能, 不能阻断用户的主操作).

## 7. 插件宿主依赖面 (D2)

`core/plugin/explorer/session/ExplorerActionTargetTrashBatchStore.kt` 让资源管理器动作插件以受限能力批量移入 / 还原文件, 它使用且只使用以下 `TrashRepository` 方法, 签名冻结 (只允许新增重载):

- `moveToTrashWithProgress(path, progress, file, controller, beforeSourceDelete, trashId)`: 宿主预生成 `trashId` 并用 `beforeSourceDelete` 在删源前复核文件身份 (`ExplorerActionFileIdentity`);
- `newTrashId()`, `findTrashItem(trashId)`, `restoreTrashItemToPathWithoutOverwrite(item, dest, controller)`, `deleteTrashItem(item)`.

宿主侧的条目状态机 `ExplorerActionTargetTrashItemState`: `PENDING -> MOVING -> MOVED | NOT_MOVED`, `MOVED -> RESTORING -> RESTORED | STALE`, 以及 `RECOVERY_REQUIRED` (对应 `TrashMoveRecoveryRequiredException`). 宿主不依赖 UI 层 (`ScriptOperations` / `TrashRestoreController`), 因此 UI 侧的撤销 / 批量恢复 / 失效条目逻辑对它无影响; 反之修改上述方法的行为 (例如覆盖语义) 会直接影响插件协议.

## 8. UI 层

| 页面 / 组件 | 说明 |
| --- | --- |
| `BaseSearchSortListActivity<VB, T>` | 搜索 / 排序 / 下拉刷新的列表页基类; `loadAllItemsInBackground` 在 IO 线程, `onItemsLoaded` -> `applyFilterAndSort` -> `submitToAdapter` 在主线程 |
| `VersionHistoryActivity` + `VersionHistoryFileAdapter` (`ListAdapter<VersionHistoryFileRow>`) | 已纳管文件列表, 行携带聚合统计 (大小 / 版本数, 异步计算后重新提交) 与文件类型图标 (`FileUtils.TYPE.determineBy`, 加载时在 IO 线程解析) |
| `RevisionListActivity` + `RevisionAdapter` | 单文件的版本列表: 预览 / 与当前文件比较 / 两版比较 / 恢复到编辑器 (可 "恢复并保存") / 恢复到磁盘 / 固定 / 标签 / 导出 / 删除; 编辑器回调经 `VersionHistoryEditorBridge` 的弱引用令牌传递 |
| `DiffActivity` + `LineDiff` | 行级差异 (线性空间 Myers, 纯函数), 超过 `DEFAULT_MAX_LINES_PER_SIDE` / `DEFAULT_MAX_BYTES_PER_SIDE` 时退化为统计 |
| `TrashActivity` + `TrashAdapter` (`ListAdapter<TrashRow>`) | 回收站列表: 行携带失效标记与到期倒计时; 长按进入多选 (批量恢复 `TrashBatchPlanner` / 批量移除); 失效条目只允许详情 / 移除, 菜单 "移除失效条目" |
| `TrashRestoreController` / `TrashPreviewController` / `TrashFailureText` | 单个与批量恢复流程 (目标目录选择, 冲突策略, 可中止进度), 预览 (文本到只读编辑器, 目录到条目列表 `TrashArchiveListing`), 失败文案本地化 |
| `EditorVersionHistoryService` / `EditorView` | 编辑器菜单 "版本历史" 与 "创建快照", 保存前记录 `SAVE_PRE` |
| `ScriptOperations` | 资源管理器的移入回收站 (含配额提示 `TrashQuotaHint`, D8) / 撤销 / 重命名 / 移动 / 覆盖钩子 / 彻底删除时可选清除历史 |
| `TrashAndVersionHistorySettingsActivity` / `RetentionLimitPreference` | 保留策略子页 (D9): 9 个数值项 (`RetentionLimitUnits` 换算) + 1 个开关 |

## 9. 可测性与证据

- D5: 策略 / 路径 / 差异 / 规划 / 归档逻辑都是不依赖 Android 类型的纯对象, JVM 单测位于 `app/src/test/java/org/autojs/autojs/storage/history/` (`HistoryPaths`, `HistoryRetentionPolicy`, `HistoryFingerprintPolicy`, `HistoryRelocation`, `HistorySnapshotLabel`, `HistoryExportNaming`, `RetentionLimitUnits`, `LineDiff`, `TrashRestoreNaming`, `TrashBatchPlanner`, `TrashArchiveListing`, `TrashArchiveExtractor`, `TrashExpiry`, `TrashQuotaHint`), 运行命令: `./gradlew.bat :app:testAppDebugUnitTest --tests "org.autojs.autojs.storage.history.*" --offline`.
- 数据库迁移: `app/src/androidTest/.../HistoryDatabaseMigrationTest` (需要设备).
- 设备冒烟清单与证据等级见 Roadmap 附录 B / C.

## 10. 用户可见说明

用户文档项目 (AutoJs6-Documentation) 目前没有回收站 / 版本历史的专页; 应用内的说明只有设置子页各项的摘要文案与对话框提示. 若日后补充用户文档, 建议以第 1 节的表格与第 5 节的清理时序为蓝本, 并说明 "固定的快照不受单文件版本数限制" (D10) 与 "回收站上限始终保留最新条目" (D7) 这两条用户容易困惑的规则.
