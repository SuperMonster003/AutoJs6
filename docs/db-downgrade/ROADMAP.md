# 数据库降级崩溃循环路线图 (db-downgrade)

更新日期: 2026-10-23 (立项: 两轮 7 题拍板 D2-D8)

本文是主题 db-downgrade 的可执行状态表: 旧版本宿主或打包应用 (`org.autojs.autojs6.inrt`) 装在新版本数据上时, `SQLiteOpenHelper` 默认的 `onDowngrade` 抛 `SQLiteException: Can't downgrade database from version X to Y`, 异常发生在 `App.onCreate`, 进程启动即崩溃, 再被系统或其他组件反复拉起形成崩溃循环. 它记录立项时查明的事实 (第 1 节), 维护者 2026-10-23 经两轮选择题确认的决策 (第 2 节), 派生技术决策 (第 3 节), 红线 (第 4 节), 阶段任务 (第 5 节) 与附录 (决策记录, 证据, 待决事项, 文件地图). 来源: `docs/shizuku-oom/ROADMAP.md` 1.5 与附录 C Q4 / Q7 (D16 把本题转为独立主题).

核心目标一句话: 任何版本的数据装到含本修复的构建上都不再在启动时崩溃: 两张任务表经 "无操作降级 + 幂等升级" 保留数据, 三个 Room 库按导出的 schema 通用降级保留共有列, 任何打开失败都退化为 "改名备份 + 空库继续 + 条件满足时自动还原", 脚本 sqlite 模块提供 onDowngrade 回调; 以上由仓库内可复用脚本在临时 AVD 与一台真机证明, 宿主与 inrt 都覆盖.

使用方式:

1. 每次会话开始时, 从第 5 节选取未完成条目, 按阶段顺序推进; 条目完成后勾选 `[x]` 并追加证据 (提交 hash / 测试类名 / 设备或 AVD 与 API / 报告文件名 / 关键数值).
2. 第 2 节固定决策不再重新讨论; 第 3 节派生决策可在标注的推翻点之前推翻; 附录 C 待决事项由调度会话用选择题请维护者拍板, 结果回填第 2 节或第 3 节.
3. 工程约定以仓库 `AGENTS.md` 与 `../COMMON_AGENTS.md` 为准 (ASCII 标点, 构建号, 共享工作区, 宿主只本地提交不推送). 真机规则见 `../.claude/rules/device-testing.md`: 系统对话框与 Shizuku 授权由维护者操作; 多步验证写成仓库内可复用脚本, 输出只保留通过 / 失败与关键数值.
4. 报告存 `docs/db-downgrade/evidence/`, 命名 `roundtrip-<设备或AVD>-<构建号>-<日期>[-<标签>].md`; 每条结论同时写进附录 B.

---

## 0. 阶段总览

| 阶段 | 目标 | 完成条件 | 预计会话数 |
| --- | --- | --- | --- |
| P0 取证与修前复现 | XQ-AT72 上 inrt 崩溃循环的拉起方与次数有证据; 临时 AVD 上宿主与 inrt 的修前崩溃与重启次数有数字; 往返脚本可用 | P0.1-P0.3 勾选, 附录 B.1 / B.2 "修前" 列填满 | 1 (与 P1 同一会话) |
| P1 两张任务表 + 兜底 | `TimedTaskDatabase` / `IntentTaskDatabase` 无操作降级, 幂等升级; 打开失败改名备份后空库继续, 条件满足时自动还原 | P1.1-P1.4 勾选, AVD API 24 与 35 往返 PASS, androidTest 通过 | 1 (与 P0 同一会话) |
| P2 Room 三库通用降级 | `HistoryDatabase` / `ColorHistoryDatabase` / `KeyStoreDatabase` 对 "未来 schema" 的数据保留共有列打开, 失败兜底 | P2.1-P2.3 勾选, androidTest 通过, AVD 页面核对不崩 | 1 (可与 P1 并行) |
| P3 脚本 sqlite 模块 | `DatabaseCallback` 新增 onDowngrade, 默认无操作降级; 文档与 d.ts 同步 | P3.1-P3.2 勾选 | 0.5 |
| P4 真机复核, changelog 与发布门 | 一台真机往返 PASS; changelog zh-Hans 条目; 6.8.0 发布门一行; 本文回填 | P4.1-P4.4 勾选, 本地提交 | 0.5 (P1 / P2 / P3 之后) |

---

## 1. 现状与证据 (基线 HEAD `26fd95f77`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=4283` 即 versionCode 5935, 2026-10-23 立项时阅读)

### 1.1 两次观察 (来自 shizuku-oom P0, 2026-10-22)

- 宿主: GitHub Release v6.6.4 (versionCode 3274) 覆盖装在 6.8.0 数据上, 初始化时抛 `SQLiteException: Can't downgrade database from version 5 to 3`, 系统每轮重启宿主最多 3 次后不再重启 (直到探针强停). 每个宿主进程都拉起一个 Shizuku 用户服务, 所以崩溃次数直接变成了服务残留数 (shizuku-oom 附录 C Q4, D13 接受该结果).
- inrt: XQ-AT72 (QV710AF65F, Android 12) 上他人安装的 `org.autojs.autojs6.inrt` (versionCode 5280) 在 Shizuku 服务端重启后被唤醒, 初始化时数据库 4 -> 3 降级崩溃, 系统反复重启, P0 期间 123 次, `am force-stop` 止住. 该包与数据保留作复现样本 (D16).
- 共享笔记 `shizuku-oom-p0-xq-at72-findings.md` 立项时在 side-session-notes 的三个目录与 AppData / ~/.claude / .claude 下都找不到; 以本节与 shizuku-oom roadmap 1.5 为准, 细节由 P0.1 重新取证.

### 1.2 版本对应与两张任务表

- `storage/database/TimedTaskDatabase.java`: `VERSION = 5` (文件 `TimedTaskDatabase.db`); 6.6.4 / 6.7.0 为 3. `onUpgrade`: `oldVersion < 4` 加 repeat_mode / end_mode / end_value / finished_times / day_of_month / month_of_year, `< 5` 加 source_kind / project_root; `addColumn` 已包 try / catch. 列读取 0-7 按位置, 其余按列名并带默认值. 无 `onDowngrade`.
- `storage/database/IntentTaskDatabase.java`: `VERSION = 4` (文件 `IntentTaskDatabase.db`). `onUpgrade`: `< 2` ADD local, `< 3` ADD source_kind / project_root, `< 4` ADD extras; ALTER 没有 try / catch (无操作降级后再升级会因重复列抛异常, 所以升级必须改为幂等). 无 `onDowngrade`.
- inrt 5280 对应旧编号时期的 VERSION_BUILD 5280 (2026-08-11 构建 5274 之后, 2026-09-21 构建 5282 之前; versionCode 偏移 1652 自 2026-10-11 `03b37ef76a` 起才引入), 其 IntentTask 版本为 3, TimedTask 版本为 5; "4 -> 3" 即 XQ-AT72 上的数据曾被 5282 之后的 inrt 写成 4, 再装回 5280. 宿主 "5 -> 3" 是 TimedTaskDatabase.
- `SQLiteOpenHelper` 的事实: 默认 `onDowngrade` 抛异常; 升级或降级回调返回后总是 `db.setVersion(newVersion)`; 所以无操作降级后文件版本变为旧版本, 再装回新版本时 `onUpgrade` 会重跑, 迁移必须幂等 (列已存在不再加).
- 基类 `storage/database/Database.java` 构造函数里同步 `getWritableDatabase()` 两次 (可读库也取可写), 异常直接从构造函数抛出; `timing/TimedTaskManager.kt` 是 `object`, 两库 `by lazy`, 第一次访问即打开.

### 1.3 崩溃点与循环机制

- `App.onCreate` (主进程分支) 依次 `setUpDebugEnvironment` (装 `CrashHandler`), `AutoJs.initInstance`, `GlobalKeyObserver.initIfNeeded`, `TimedTaskScheduler.init(this)`, `initDynamicBroadcastReceivers()` (`TimedTaskManager.allIntentTasks` 订阅); 后两项同步打开两库, 异常从 `onCreate` 抛出, 进程启动失败. 宿主与 inrt 共用 `App` (`BuildConfig.isInrt` 分支只影响其他初始化).
- `tool/CrashHandler.kt`: 相同消息缓存超过 4 条才启动 `CrashReportActivity` 并 `exitProcess(1)`, 否则主线程异常交系统默认处理器; 进程重启后缓存为空, 没有跨进程的循环防护.
- 谁拉起进程谁形成循环: Shizuku 服务端重启后经 content provider `rikka.shizuku.ShizukuProvider` 拉起 inrt (123 次的来源, P0.1 核实); WorkManager 周期任务 `CheckTasksWorker` (`WorkTimedTaskScheduler`), 闹钟 / Job 调度器, 静态广播 (`BootCompletedReceiver`, `StaticBroadcastReceiver` 等), 各服务都能拉起主进程. 宿主 "每轮最多 3 次" 的停止条件 (系统 bad-process 限制或拉起方放弃) 由 P0.1 查明.
- Room 三库 (预防性, 今日版本尚未降过): `storage/history/HistoryDatabase.kt` (version 3, exportSchema true, 文件 `history-database.db`), `theme/app/ColorHistoryDatabase.kt` (version 2, exportSchema true, 文件 `color-history-database.db`), `apkbuilder/keystore/KeyStoreDatabase.kt` (version 1, exportSchema false, "keystore-database"). Room 降级同样抛 `IllegalStateException` ("A migration from X to Y was required but not found"); 调用点 `DrawerFragment.kt` (约 822 行), `HistoryCleanup.kt` (`HistoryCleanupWorker` 周期 worker, `HistoryCleanupUi` 有 runCatching), `VersionHistoryActivity`, `TrashActivity`, `ColorSelectBaseActivity`, `ColorLibraryRepository`, `KeyStoreRepository`. schema json 在 `app/schemas/<类全名>/<版本>.json` (`build.gradle.kts` 约 489 行 `room.schemaLocation`). 已有 `HistoryDatabaseMigrationTest` / `ColorHistoryDatabaseMigrationTest` (androidTest, `createBuilder` 供测试共用); 仓库没有 Robolectric.
- 脚本 sqlite 模块: `core/database/Database.java` (extends SQLiteOpenHelper) 的 `DatabaseCallback` 只有 onCorruption / onCreate / onOpen / onUpgrade, 经 `runtime/api/augment/sqlite/SQLite.kt` 的 `open(scriptRuntime, args)` 暴露 (version 选项). 脚本传的 version 低于文件版本时同样抛异常 (脚本自身的异常, 不进崩溃循环, 但脚本无法处理). 文档 `AutoJs6-Documentation/api/sqlite.md` 第 60 行写明 "当前实现没有自定义 onDowngrade"; d.ts 在 `AutoJs6-TypeScript-Declarations/declarations/autojs6/aj6-int-sqlite.d.ts` 与 `declarations/autojs6-node/sqlite.d.ts`.

### 1.4 修复的作用范围 (事实, 决定验收方式)

迁移代码运行在打开库的那个构建里, 所以修复只保护 "含修复的构建" 被装在更新数据上的情形: 6.8.0 含修复后, 6.9.x 的数据装回 6.8.0 不再崩; 已发布的 6.6.4 / 6.7.0 装在 6.8.0 数据上仍会崩, 无法补救. inrt 同理, 用含修复的宿主打包的 inrt 起才受保护. 因此验收不能用 "旧发布包装在新数据上", 只能构造 "未来数据" (文件版本 +1 并加列 / 加表) 装到当前构建上 (T6).

### 1.5 设备与锁表

- XQ-AT72 (QV710AF65F, Android 12 / API 31): 只做只读取证, inrt 5280 样本不卸载不覆盖不清数据 (R2).
- 真机复核候选 (P4.1): G8441 (BH900ASK9E, Android 9 / API 28) 或 OPD2413 (63751f63, Android 15 / API 35); 装机前备份原包 (md5), 测后装回. 本地设备可能不在线, 需要时请维护者连接.
- 主测: 临时 AVD, API 24 (最低支持) 与 API 35 各一台, 用后删除 (R7); 不碰维护者预置的 `AVD_API_*`.
- 锁表 `C:/Users/wu2du/AppData/Roaming/Claude/side-session-notes/local_8ab23cdc-d795-4973-9379-2554a76c2967/locks.md`, 行格式 `- 设备 (serial) | 会话名 (调度 id) | MM-DD HH:MM | 用途 | 预计时长`, 用前登记用后释放.

---

## 2. 固定决策 (2026-10-23 两轮选择题, 维护者确认; D1 为立项惯例)

| 编号 | 决策 | 来源 |
| --- | --- | --- |
| D1 | 路线图位于 `docs/db-downgrade/ROADMAP.md`; 证据 `docs/db-downgrade/evidence/`; 脚本 `tools/db-downgrade/` | 立项惯例 |
| D2 | 两张 `SQLiteOpenHelper` 任务表: `onDowngrade` 无操作 (不删表, 不删列, 保留未知列, 只记一行日志), `onUpgrade` 改为幂等 (列存在才不加), 两表共用工具 | Q1 (维护者选 A, 推荐项) |
| D3 | 兜底: `TimedTaskManager` 打开任一库失败 (任何异常) 时捕获, 把库文件改名为 `<name>.db.bak-<文件版本>-<时间戳>`, 以空库继续启动; 宿主发一条通知并记日志, inrt 只记日志; 之后安装的构建的代码版本 >= 备份时的文件版本, 且当前库不存在或为空时, 自动还原备份 | Q2 (维护者选 A, 推荐项) |
| D4 | Room 三库通用保留数据降级: 构建 Room 前读文件 `PRAGMA user_version`, 高于代码版本时动态注册 `Migration(文件版本, 代码版本)`, 迁移体按随 APK 打包的导出 schema json 重建各表, 只保留共有列; `KeyStoreDatabase` 的 exportSchema 改为 true; 迁移失败时按 D3 备份 + 空库 | Q3 第二轮 (维护者选 B1, 推荐项; 第一轮选 B "写死反向迁移" 因 1.4 的事实改题) |
| D5 | 验证: 临时 AVD (API 24 与 35) 主测, 一台真机复核 (G8441 或 OPD2413); XQ-AT72 只做只读取证 | Q4 (维护者选 A, 推荐项) |
| D6 | 脚本 sqlite 模块: `DatabaseCallback` 新增 onDowngrade 回调, 脚本未提供时默认无操作降级 (不抛); 同步文档与 d.ts | Q5 (维护者选 B, 非推荐项; 推荐项为只改文档) |
| D7 | 修复进 6.8.0: changelog zh-Hans 6.8.0 段加条目, `docs/dev/6.8.0-release-gate.md` 加一行 | Q6 (维护者选 A, 推荐项) |
| D8 | 复现样本处置: XQ-AT72 只读取证 (dropbox, dumpsys, events), 并在临时 AVD 上装 Shizuku 复现 "服务端重启唤醒 inrt 崩溃循环", 修后在同一场景证明不循环 | Q7 (维护者选 B, 非推荐项; 推荐项为只取证不复现唤醒) |

---

## 3. 派生技术决策 (可在推翻点之前推翻)

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 幂等升级工具 `storage/database/SQLiteSchemaUtils` (或等价命名): `hasColumn(db, table, column)` 用 `PRAGMA table_info`, `addColumnIfMissing(db, table, column, definition)`; 两张表的 `onUpgrade` 全部改用它, 现有 `addColumn` 的 try / catch 不再作为幂等手段; `onDowngrade` 覆盖为记日志 `<name>: downgrade <old> -> <new>, keeping data` 后返回 (helper 随后 `setVersion`) | P1.1 实现时 |
| T2 | 兜底入口 `storage/database/DatabaseRecovery` (或等价命名): `openOrRecover(context, name, open: () -> T): T`; 捕获打开异常后关闭 helper, 把 `<name>.db` 及 `-journal` / `-wal` / `-shm` 一并改名 (`.bak-<文件版本>-<yyyyMMddHHmmss>`, 文件版本读不到时记 0), 在 SharedPreferences 记录 (库名, 备份文件名, 文件版本), 重新打开; 打开前先检查还原条件 (D3) 并把备份改回原名. `TimedTaskManager` 的两个 lazy 改为经它打开. 不改 `Database` 基类的构造语义 | P1.2 实现时 |
| T3 | Room 工具 `storage/database/RoomDowngrade` (或等价命名): 用 `SQLiteDatabase.openDatabase(path, null, OPEN_READONLY)` 读 `user_version` (文件不存在即跳过); 高于代码版本时 `addMigrations(Migration(文件版本, 代码版本))`, 迁移体对 schema json 里的每张表: 按 `createSql` 建临时表, `INSERT INTO 新表 SELECT 共有列 FROM 旧表` (共有列 = schema 列与 `PRAGMA table_info` 交集, 新表非空且无默认值的列不在交集时该表只能清空并记日志), 删旧表改名, 重建索引; schema 里没有的表保留不动 (Room 只校验声明的实体); Room 迁移后自行更新 identity hash. schema json 由 Gradle 任务从 `app/schemas` 复制到 assets `db-schemas/<类全名>/<版本>.json` (只复制, 不改 `room.schemaLocation`) | P2.1 实现时 |
| T4 | Room 接入: 三个 `createBuilder` / `getInstance` 经 `RoomDowngrade.builder(...)` 取 builder; 兜底在 `getInstance` 里主动触发一次 `openHelper.writableDatabase`, 失败即按 T2 备份后重建实例 (调用线程由子会话核对, 不在主线程做额外 IO 的改为后台首次打开). 既有 `MIGRATION_*` 与 androidTest 不改 | P2.1 实现时 |
| T5 | 脚本 sqlite: `DatabaseCallback` 加 `onDowngrade(db, oldVersion, newVersion)`; `core/database/Database.java` 覆盖 `onDowngrade`: 回调存在即调用, 否则无操作返回; `SQLite.kt` 的 open 选项解析该回调, 位置与 onUpgrade 并列; 既有回调的顺序与行为不变 (R6) | P3.1 实现时 |
| T6 | 往返脚本 `tools/db-downgrade/roundtrip.py` (py -3.12 -I), 参数 `--serial`, `--package` (宿主 / inrt 包名), `--report-dir`, 子命令: `make-future` (对 debug 包用 `run-as` 拉出库文件, 在 PC 端用 Python sqlite3 把 `user_version` +1 并加一列与一张表, 删 `-journal` / `-wal` 后推回; 不依赖设备上的 sqlite3), `check` (启动应用, 等 10 秒, 核对进程存活, logcat `am_crash` / `FATAL EXCEPTION` 计数 0, 任务表行数与 `user_version` 等于代码版本), `inject-fail` (把库路径换成同名目录, 预期走兜底: 启动不崩, 出现 `.bak` 文件), `restore-check` (装回版本满足的构建后备份被还原且行数一致), `wake` (重启 Shizuku 服务端 N 次, 统计 `am_proc_start` 与崩溃次数). 正式签名包不能 `run-as`, 真机复核 (P4.1) 改用 "各库版本 +1 的临时测试构建" 先写数据再装当前构建. 输出 md 只含每项通过 / 失败与关键数值 | P0.3 完成 |
| T7 | 备份保留: 每个库最多保留最近 3 份 `.bak`, 超出删最旧; 还原成功后删除该份; 不另做界面 | P1.2 实现时 |
| T8 | 约定: 两张任务表今后的 schema 变更只做带 DEFAULT 的 ADD COLUMN, 不改列类型, 不删列, 不重建表 (这是无操作降级能保留数据的前提); 写进两个类的注释与本表; 需要破坏性迁移时先登记待决. 源码守卫 (JVM 单测扫描 `onUpgrade` 只含 `addColumnIfMissing`) 由 P1.1 视成本决定 | 需要破坏性迁移时 |
| T9 | 兜底通知: 宿主走现有通知渠道 (子会话核对渠道 id), 文案为字符串资源 (按 `AGENTS.md` 字符串资源规则, 默认与 values-en 一致, `...` 省略号), 内容说明库名, 备份文件名, 已以空库继续, 装回 >= 该版本后自动还原; 不加点击入口; inrt 只 `Log.w` | P1.2 实现时 |

---

## 4. 红线

- R1 宿主仓库只本地提交, 不推送; 共享工作区: 只提交自己的文件, 不切分支, 不碰其他会话在改的文件 (立项时在途: `.changelog/lang_zh-Hans.json`, `README.md`, `.readme/README-zh-Hans.md`, `CHANGELOG*.md`, `AutoModule.kt`, `FlowArgs.kt`, `FlowOptions.kt`, `ToolkitArgs.kt`, `AutomatorModulesTest.kt`, `docs/augment/ROADMAP.md`, `docs/memory-phase2/ROADMAP.md`, `tools/augment/bench/calls/automator.js`); changelog 与发布门只追加并只暂存自己的 hunk.
- R2 XQ-AT72 上的 `org.autojs.autojs6.inrt` 5280 与其数据是复现样本: 不卸载, 不覆盖安装, 不清数据, 不改设置; 只读取证 (`dumpsys`, `logcat`, dropbox, `pm dump`); 取证中它再进崩溃循环时用 `am force-stop` 止住并记录次数. 真机按锁表登记与释放; 装机前备份原包 (md5), 测后装回; Shizuku 授权与系统对话框由维护者操作 (AVD 上也一样, 停下来说明要点什么).
- R3 任何降级或兜底路径都不得删除用户数据文件 (只改名备份, 按 T7 轮换); 升级路径保持幂等; 两张任务表的 schema 变更只 ADD COLUMN (T8); Room 通用降级只在保留共有列后才删旧表.
- R4 脚本与报告只保留通过 / 失败与关键数值 (次数, 行数, 版本号, 文件名), 不打印完整 logcat, 库内容或任务脚本路径.
- R5 外部 APK 只从官方来源: Shizuku 从 RikkaApps/Shizuku 的 GitHub Release, 宿主旧版本从 SuperMonster003/AutoJs6 的 GitHub Release; 放独立目录, 校验签名后再装.
- R6 不改脚本 sqlite 模块既有回调的行为与顺序; 文档与 d.ts 按 `AGENTS.md` 关联项目流程更新, 两个关联仓库的提交与推送按各自惯例, 不确定时只提交并在汇报里列出.
- R7 临时 AVD 用后关闭并删除 (`.avd` 目录与同名 `.ini`), 不碰维护者预置的 AVD; 会话结束按 `.claude/rules/session-cleanup.md` 清理.

---

## 5. 阶段任务

### P0 取证与修前复现 (D5 / D8; 设备 XQ-AT72 只读, 临时 AVD API 35)

- [x] P0.1 XQ-AT72 只读取证 (不装包, 不卸载, 不清数据): `dumpsys dropbox --print` 里 `data_app_crash` 的 inrt 条目 (异常类, 消息, 栈顶, 时间跨度), `dumpsys package org.autojs.autojs6.inrt` (versionCode, 安装与更新时间, provider / receiver / service 清单), `dumpsys activity processes` / `dumpsys jobscheduler` / `dumpsys alarm` 里 inrt 的拉起来源, `logcat -b events` 的 `am_proc_start` caller (若未轮转); 宿主 "每轮最多 3 次" 的停止条件 (系统 bad-process 限制还是拉起方放弃); inrt 数据库版本 4 的来源 (哪个构建写的). 验收: 附录 B.1 有异常与消息, 拉起方 (预期 `ShizukuProvider` 的 provider 调用), 次数与时间跨度; dropbox 已轮转的项记 "已轮转", 以 P0.2 的 AVD 复现为准.
- [x] P0.2 临时 AVD (API 35) 修前基线: 当前 HEAD 的宿主 debug 包与 inrt debug 包 (子会话核对 Gradle 任务名, inrt flavor 见 `app/build.gradle.kts`) 各装一次, 用 T6 `make-future` 构造未来数据后启动, 记崩溃异常, 系统重启次数 (`am_proc_start` / `am_crash`) 与停止条件; 装 Shizuku (R5) 并经 adb 启动服务端, 授权对话框由维护者点按, 重启服务端 3 次, 记每次拉起 inrt 的次数与崩溃次数. 验收: 附录 B.2 "修前" 列有宿主与 inrt 的异常, 重启次数, 唤醒循环次数 (预期崩溃, 即 `check` 为 FAIL).
- [x] P0.3 往返脚本 `tools/db-downgrade/roundtrip.py` + `tools/db-downgrade/README.md` (T6, R4). 验收: P0.2 的数字由脚本产生, 报告入 `docs/db-downgrade/evidence/` (修前 FAIL 是预期结果).

### P1 两张任务表 + 兜底 (D2 / D3, T1 / T2 / T7 / T8 / T9; 代码; 验证临时 AVD API 24 与 35)

- [x] P1.1 `TimedTaskDatabase` / `IntentTaskDatabase`: `onDowngrade` 无操作 (日志), `onUpgrade` 改用 `addColumnIfMissing` (T1), 类注释写明 T8 约定. 验收: 编译通过; P1.3 的往返与幂等用例通过.
  - 2026-10-23 完成 (db-downgrade-p0-p1, 提交 `64a62c53e`): `storage/database/SQLiteSchemaUtils.java` (`hasColumn` 用 `PRAGMA table_info`, `addColumnIfMissing`), 两表 `onUpgrade` 改用它, `onDowngrade` 记日志 `<库>: downgrade <old> -> <new>, keeping data` 后返回; `VERSION` / `FILE_NAME` 改为 public 供兜底使用; 类注释写 T8. T8 源码守卫不做, 理由见附录 A 之后的 "P0 / P1 实施处置" 第 9 条. `:app:assembleAppDebug` 通过 (构建号 5935).
- [x] P1.2 兜底 (T2 / T7 / T9): `DatabaseRecovery` 备份 / 还原 / 轮换, `TimedTaskManager` 两个 lazy 经它打开, 宿主通知 (字符串资源按 `AGENTS.md`), inrt 只日志. 验收: `inject-fail` 时宿主与 inrt 启动不崩, 出现 `.bak`, 宿主有通知; `restore-check` 还原且行数一致.
  - 2026-10-23 完成 (db-downgrade-p0-p1, 提交 `b7206e176` `DatabaseRecovery` 与 11 种语言的 3 条字符串, `64a62c53e` `TimedTaskManager` 接入, `e87b22a80` 按 P2 交接补齐 Room 库名与 `room_master_table`): 与 T2 原文的差异 (不用 SharedPreferences, 还原只看最新一份, 安装时间条件, `onFailure` 参数) 见 "P0 / P1 实施处置" 第 5-8, 10 条. `inject-fail` / `restore-check` 在 AVD API 24 与 35 上宿主与 inrt 均 PASS (附录 B.2).
- [x] P1.3 androidTest (`app/src/androidTest`, 与既有迁移测试同目录惯例): 降级往返 (写版本 +1 且多一列的库文件, 用当前 helper 打开, 行与列值保留, 文件版本变为代码版本), 幂等升级 (3 -> 5 -> 3 -> 5 不抛, 列不重复), 兜底 (目录占位 -> 备份 + 空库), 还原 (备份存在且版本满足 -> 还原; 版本不满足 -> 不还原). 验收: AVD 上只跑本主题测试类通过 (子会话核对 Gradle 任务与过滤参数).
  - 2026-10-23 完成 (db-downgrade-p0-p1, 提交 `d48f482c2`): `app/src/androidTest/.../storage/database/TaskDatabaseRecoveryTest.kt` 11 例在临时 AVD `dbdowngrade_p01_api24` (API 24) 与 `dbdowngrade_p01_api35` (API 35) 上均通过: 两表各一例降级往返 (版本 +1, 多一列一表, 行与值保留, 版本变为代码版本), 两表各一例幂等升级 (TimedTask 3 -> 5 -> 3 -> 5, IntentTask 2 -> 4 -> 2 -> 4), 目录占位兜底, 轮换 3 份, 还原条件 (安装时间, 版本), 只替换空库, 只看最新可读备份, Room 库名不带 `.db`, `openOrRecover` 装后还原. 库文件经 `ContextWrapper` 放在 `cacheDir/db-downgrade-test`, 不碰被测应用自己的任务库. 构建: `:app:assembleAppDebug :app:assembleAppDebugAndroidTest`, 运行: `adb shell am instrument -w -r -e class org.autojs.autojs.storage.database.TaskDatabaseRecoveryTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner`.
- [x] P1.4 AVD 验收 (API 24 与 35): `check` 宿主与 inrt 均 PASS (崩溃 0, 重启 0, 行数保留), API 35 上 Shizuku 重启服务端 3 次 inrt 均正常启动 (对照 P0.2); 报告入 evidence, 附录 B.2 "修后" 列.
  - 2026-10-23 完成 (db-downgrade-p0-p1): 临时 AVD `dbdowngrade_p01_api24` (Android 7.0) 与 `dbdowngrade_p01_api35` (Android 15) 上, 宿主 5935 与 inrt 5835 的 `suite` (`make-future`, `check`, `inject-fail`, `restore-check`, 每步观察 30 s) 全部 PASS: 启动 1 次, 重启 0, 崩溃 0, 两表降回代码版本 (6 -> 5, 5 -> 4) 且标记行与未来列 / 表保留; inrt 用 `--start broadcast` (理由见 "P0 / P1 实施处置" 第 11 条 (e)). API 35 上 Shizuku 服务端重启 3 轮 (连续 3 轮, 只有第 1 轮是版本 +1 数据, 之后库已是代码版本) 加 2 轮 (每轮之前重新 `make-future`), 共 5 轮: inrt 与宿主每轮都只由 `ShizukuProvider` 拉起 1 次, 崩溃 0, 90 s 末存活 (修前每轮 9 / 1 / 8 次与 7 / 8 / 8 次). 数字见附录 B.2, 报告 `evidence/roundtrip-dbdowngrade_p01_api24-5935-20261023-after-fix.md` 与 `evidence/roundtrip-dbdowngrade_p01_api35-5935-20261023-after-fix.md`. 构建: 修后 APK 含 `b7206e176` 与 `64a62c53e` (suite 用); 唤醒时宿主为含 `e87b22a80` 的构建 (只改 Room 库名与 `room_master_table` 判定, 不影响两张任务表).

### P2 Room 三库通用降级 (D4, T3 / T4; 代码; 可与 P1 并行, 文件不重叠; 兜底复用 P1.2 的 `DatabaseRecovery`, 它尚未提交时先只做迁移, 兜底在它提交后接入并在结果区写明)

- [x] P2.1 schema 打包与工具类: `KeyStoreDatabase` exportSchema = true (生成 `1.json`), Gradle 复制 `app/schemas` 到 assets (T3), `RoomDowngrade` 读 `user_version` / 动态 Migration / 按 schema 重建保留共有列, 三个 builder 接入 (T4). 验收: 编译通过, 既有 `MIGRATION_*` 与迁移测试不变.
  - 2026-10-23 完成 (db-downgrade-p2, 提交 `01eb74e66`): `storage/database/RoomDowngrade.kt`, 三个 `createBuilder` 改经 `RoomDowngrade.databaseBuilder`, `MIGRATION_*` 未改; Gradle 任务 `package<Variant>RoomSchemaAssets` 把 `app/schemas` 打包为 assets `db-schemas/` (APK 内 6 个 json), 排在 KSP 之后; `:app:assembleAppDebug` 通过 (构建号 5935), 既有 `HistoryDatabaseMigrationTest` (3) 与 `ColorHistoryDatabaseMigrationTest` (1) 在 AVD API 35 上通过. 与 T3 / T4 原文的差异及理由见附录 A 之后的 "P2 实施处置". 兜底已在 `DatabaseRecovery` (P1.2, `b7206e176`) 提交后接入, 见 P2.2 进度与 "P2 实施处置" 第 5 条.
- [x] P2.2 androidTest: 用 SQLite 直接建 "未来 schema" (版本 +1, 多一列 + 多一表) 后用当前 builder 打开: 不抛, 行保留, 共有列值一致, 多余表按 T3 处置, 再次打开不再迁移 (identity hash 已更新); 三库各一例 + 迁移失败兜底一例 (需 P1.2). 验收: AVD 通过.
  - 2026-10-23 进度 (db-downgrade-p2, 提交 `5788e957e`): `app/src/androidTest/.../storage/database/RoomDowngradeTest.kt` 4 例在临时 AVD `dbdowngrade_api35` (API 35, x86_64) 上通过: History v4 -> 3 (revision 多一列 + 一个索引, 13 列保留 12), ColorHistory v3 -> 2 (color_library 多一列; 父表重建不级联删子表, AUTOINCREMENT 序号 100 保持, 新行 id 101), KeyStore v2 -> 1 (7 列保留 6), KeyStore 缺必需列 (旧表改名为 `_downgrade_kept_KeyStore_v2` 保留 1 行, 新表为空可写); 每例核对 `user_version` = 代码版本, identity hash = 快照值, 多出的表 `future_table` 与其索引保留, 无 `_downgrade_new_` 残留, 再次打开不迁移. 剩迁移失败兜底一例, 待 P1.2 `DatabaseRecovery` 提交后接入.
  - 2026-10-23 完成 (db-downgrade-p2): 兜底接入 `RoomDowngrade` 的 open helper (首次打开经 `DatabaseRecovery.openOrRecover`), 补第 5 例 `fileThatStillCannotOpenIsBackedUpAndReplacedByEmptyDatabase`: History v4 的 `revision.sha256` 可空且有 NULL 行, 重建违反 NOT NULL 而回滚 (framework helper 重试一次, 共两次), 文件改名为 `<库名>.bak-4-<时间>` (备份仍为 v4, 原行保留), 新空库 v3 可写; 再次打开既不还原较新的备份, 也不再次备份. 临时 AVD `dbdowngrade_api35` 上 5 例与既有迁移测试 4 例共 9 例通过, P2.3 页面核对复跑仍为 28 / 28.
- [x] P2.3 AVD 核对: 三库构造未来 schema 后启动宿主, 打开抽屉, 版本历史页, 回收站, 颜色选择页, 密钥库页不崩; 手动触发 `HistoryCleanupWorker` 一次不崩 (子会话核对触发方法). 报告入 evidence, 附录 B.3.
  - 2026-10-23 完成 (db-downgrade-p2): `tools/db-downgrade-room/room_pages.py` 在宿主停止时写入三库未来文件, 从桌面入口启动后由 `RoomDowngradePagesDeviceTest` (instrumentation 参数 `autojs.dbDowngrade.pages=true`) 打开 6 个页面并以 `OneTimeWorkRequest` 触发 `HistoryCleanupWorker` 一次 (worker 出错时返回 retry, 因此断言状态为 SUCCEEDED). 临时 AVD `dbdowngrade_api35` 上 28 / 28 项通过, 结果见附录 B.3, 报告 `evidence/roundtrip-dbdowngrade_api35-5935-20261023-room.md`.

### P3 脚本 sqlite 模块 onDowngrade (D6, T5, R6; 代码 + 文档 + d.ts; 与 P1 / P2 文件不重叠, 可并行或之后)

- [x] P3.1 `core/database/Database.java` + `DatabaseCallback` 加 onDowngrade, `SQLite.kt` 解析回调, 默认无操作降级. 验收: 脚本用 version 2 建库再用 version 1 打开不抛且可查询; 提供 onDowngrade 时被调用且参数正确; 用例脚本与结果入 evidence (或 androidTest).
  - 2026-10-24 db-downgrade-p3, 提交 `e03d82e0f`: `Database.onDowngrade` 有回调即调用, 否则无操作 (helper 随后设为请求的版本); `SQLite.kt` 不需要改 (附录 A "P3 实施处置" 第 1 条). 临时 AVD `dbdowngrade_p3_api24` (Android 7.0) 与 `dbdowngrade_p3_api35` (Android 15), 隔离克隆 debug 5935: `tools/db-downgrade/sqlite_ondowngrade.py` 5 / 5 PASS (无回调 2 -> 1 版本 1 行 2; 回调无 onDowngrade 同左; onDowngrade 收到 (Database, 3, 2), 在事务内且先于 onOpen, 写入的行保留; onDowngrade 抛出时 sqlite.open 抛出, 版本与行回滚; readOnly 同无回调), `SqliteModuleTest` JVM 5 例 0 失败, 其设备用例 `device_eval.py --new-modules on` 两台各 25 / 25 一致. 证据见附录 B.4.
- [x] P3.2 文档 `AutoJs6-Documentation/api/sqlite.md` (第 60 行附近说明与回调表) 与两处 d.ts 同步, 按 `AGENTS.md` 关联项目流程 (生成脚本, 版本号). 验收: 关联仓库各有提交; 推送按各自惯例 (R6).
  - 2026-10-24 db-downgrade-p3, 均只本地提交 (附录 A "P3 实施处置" 第 8 条): 文档 `e083ccb` (sqlite.md 第 60 行说明, DatabaseCallback 说明与示例, 新增 `DatabaseCallback#onDowngrade` 一节, progress.md, 全量生成) 与 `16134b9` (versionCode 168); 离线文档插件 `e5ef6df` (`auto-generate-for-autojs6.bat --verify-offline` 同步, 插件单测与 debug / release APK 检查通过, VERSION_BUILD 206, provenance 指向 `e083ccb`); d.ts `44f6825` (4.83.0, `aj6-int-sqlite.d.ts` 加 `onDowngrade?`, `aj6dts.bat -Publish` 重新生成 aj6-main-app / aj6-resources, 10 种语言 changelog, `npm test` 通过); Ace `cc70b55` (2.45.0, VERSION_BUILD 331, 同步 aj6-int 与生成的声明, `:app:generateAutoJs6LspDeclarations`).

### P4 真机复核, changelog 与发布门 (D5 / D7; 需 P1 与 P2 完成; 真机 G8441 或 OPD2413, 按锁表)

- [ ] P4.1 真机复核: 用各库版本 +1 的临时测试构建 (不提交) 先装并写数据, 再装当前构建, `check` PASS; 备份原包, 测后装回. 验收: 报告入 evidence, 附录 B.2 真机行.
- [ ] P4.2 changelog `.changelog/lang_zh-Hans.json` 6.8.0 段: fix (宿主与打包应用在旧版本装到新版本数据上时启动崩溃并反复重启的问题; 任务数据库无法打开时改为备份后继续启动) 与 feature (sqlite 模块 onDowngrade 回调), 按 `AGENTS.md` 更新日志规则, 只追加自己的 hunk, 只生成 zh-Hans; 其他语言由维护者同步.
- [ ] P4.3 发布门 `docs/dev/6.8.0-release-gate.md` 加一行 (条目: 数据库降级不崩溃不循环; 来源: db-downgrade; 复核: AVD 往返 + 真机; 证据: evidence 文件名; 状态), 只追加自己的 hunk.
- [ ] P4.4 本文回填: 附录 B 数字, 附录 A 补充, 第 5 节勾选与证据, 附录 D 文件地图.

---

## 附录 A. 决策记录 (2026-10-23 立项, 两轮选择题)

| 题 | 问题 | 选项 | 维护者选择 | 落为 |
| --- | --- | --- | --- | --- |
| Q1 | 两张任务表的降级策略 | A 无操作降级 + 幂等升级 (推荐) / B 反向迁移 (删新列, 重建表) / C 降级即清空重建 | A | D2 |
| Q2 | 打开失败的兜底 | A 捕获 + 改名备份 + 空库继续 + 条件满足时自动还原 (推荐) / B 捕获 + 清空重建, 不备份 / C 不兜底, 只修降级 | A | D3 |
| Q3 (第一轮) | Room 三库 | A 不处理 (预防性) / B 写反向迁移 / C 通用降级 | B; 调度会话指出写死的 `Migration(3, 2)` 在 HEAD 里永远不会执行 (1.4), 改题 | 第二轮 |
| Q3 (第二轮) | Room 三库 | B1 通用保留数据降级: 读 user_version, 动态 Migration, 按导出 schema 重建保留共有列 (推荐) / B2 通用清空重建 / A 不处理 | B1 | D4 |
| Q4 | 验证方式 | A AVD 主测 + 一台真机复核 (推荐) / B 只 AVD / C 多台真机 | A | D5 |
| Q5 | 脚本 sqlite 模块 | A 只改文档说明 (推荐) / B 加 onDowngrade 回调, 默认无操作降级, 同步文档与 d.ts | B (非推荐) | D6 |
| Q6 | 发布 | A 进 6.8.0 并加发布门行 (推荐) / B 下个版本 | A | D7 |
| Q7 | XQ-AT72 样本 | A 只读取证, 不复现唤醒 (推荐) / B 取证 + AVD 装 Shizuku 复现唤醒循环并在修后证明不循环 / C 不取证 | B (非推荐) | D8 |

条目实施中的处置理由由子会话追加到本表之后.

### P2 实施处置 (2026-10-23, db-downgrade-p2, 推翻点 P2.1 之前)

1. T3 读文件版本: 不在 build 前用只读连接读 `user_version`, 改由包装的 open helper 在 `onDowngrade` 回调里得到文件版本 (Room 在后台线程首次打开库时触发). 理由: `DrawerFragment`, `TrashActivity`, `VersionHistoryActivity` 与 `KeyStoreRepository` 在主线程取实例, 预读会在主线程做磁盘 IO; 也省去额外连接与 WAL 文件的处理. 三个库都不允许主线程查询, DAO 调用本来就在后台线程.
2. T3 注册时机: `Migration(文件版本, 代码版本)` 在回调中经 `builder.addMigrations` 注册, 依赖 Room 2.8.1 的 builder 与已构建的库共用迁移容器. framework helper 打开失败会重试一次, 注册按 (文件版本, 代码版本) 去重. 将来 Room 若改为复制容器, 打开会抛 "migration required" 而走兜底, `RoomDowngradeTest` 会失败提示.
3. T3 缺必需列: 原文 "该表只能清空并记日志" 改为旧表改名为 `_downgrade_kept_<表名>_v<文件版本>` 保留 (名字被占时加 `_2`, `_3` ...), 新表为空, 以满足 R3 (不删用户数据); 与快照没有共有列的表同样处理. 登记为附录 C Q8.
4. T3 补充: 重建期间临时打开 `legacy_alter_table` (改名时不重写其他表的外键引用, 也不重新检查视图), 外键约束此时未开启 (Room 在迁移之后才执行 `foreign_keys = ON`), 父表重建不级联删除子表; AUTOINCREMENT 表的 `sqlite_sequence` 不回退; NOT NULL 且有默认值的列用 `COALESCE` 补默认值; 插入违反约束时整个迁移回滚, 文件不变, 交给兜底. 快照含 FTS 表时报错 (三库目前都没有).
5. T4 兜底: 不在 `getInstance` 主动触发 `writableDatabase` (同样是主线程 IO), 改为在 `RoomDowngrade` 包装的 open helper 的 `writableDatabase` / `readableDatabase` 处 (后台线程) 经 `DatabaseRecovery` 处理. 已接入: 只有首次打开 (以及 `close` 之后的再次打开) 经过 `openOrRecover`, 之后直接交给 framework helper, 因为 Room 每次查询都会取 `writableDatabase`; 打开失败时先关闭 helper 再移走文件; 内存库不经过兜底. 接入时发现 `DatabaseRecovery` 两处对 Room 库不生效, 已告知 db-downgrade-p0-p1 (P1.2 文件, P2 不改): (a) 备份名的正则要求库名以 `.db` 结尾, `KeyStoreDatabase` 的库名 `keystore-database` 的备份因此既不清理也不还原; (b) "当前库没有行" 未排除 `room_master_table`, Room 库兜底后的新空库总有这一行, 备份因此不会自动还原.
6. T3 打包: Gradle 任务 `package<Variant>RoomSchemaAssets` 经 variant API 加为生成的 assets 目录, 只复制不改 `room.schemaLocation`; `mustRunAfter` 对应的 KSP 任务, 新版本的快照在同一次构建内打包.

### P0 / P1 实施处置 (2026-10-23, db-downgrade-p0-p1)

1. T6 崩溃计数: 6.7.0 起的 `CrashHandler` 自行 `exitProcess(1)`, 系统不写 am_crash (附录 B.1), 所以 `check` / `wake` 除 `am_crash` 外, 还按 `CrashHandler` / `AndroidRuntime` 日志里 "Uncaught Exception" / "FATAL EXCEPTION" 的进程数计崩溃 (只计本包本次启动的 pid), 并要求进程启动不超过 1 次, 观察期末进程存活.
2. T6 唤醒: Shizuku 13.6.0 在 AVD 上没有生成 `start.sh`, `wake` 在它不存在时直接执行 APK 里的 `lib/<abi>/libshizuku.so` (`start.sh` 执行的也是它); 不需要授权, 服务端重启时会调用所有声明了 Shizuku 权限的包的 `ShizukuProvider`. 修前每次启动到退出 5-15 s, 一轮约 60 s 才平息, 修前基线的观察窗口取 90 s.
3. T6 备份命名: 伴随文件跟在备份名之后 (`<库>.bak-<版本>-<时间>-wal`), 与 SQLite 的命名方式一致, 备份可直接打开.
4. P0.2 的 AVD 用 `dbdowngrade_p01_api35` (P2 子会话同时在用 `dbdowngrade_api35`, 加 `_p01_` 区分).
5. T2 记录方式: 不用 SharedPreferences, 改为扫描文件名 (`<库>.bak-<文件版本>-<yyyyMMddHHmmss>`). 理由: 兜底发生在启动期, 之后若有其他崩溃以 `exitProcess(1)` 结束 (Q9), `apply` 的写入可能丢失; 记录也可能与磁盘上的文件不一致 (用户手动删改). 文件名本身就带库名, 版本与时间.
6. T2 / D3 还原条件: 只看最新一份可读的备份 (文件版本 > 0, SQLite 文件头有效), 不会用较旧的备份覆盖较新的备份; 安装时间取 `lastUpdateTime`, 须不早于备份秒数 + 1 s (备份名只精确到秒, 防止同一构建在同一秒内备份后立即还原, 形成循环); 当前库打不开时算作有行, 不替换; 还原后备份即改回原名, 不再计入轮换.
7. T9 通知: 用前台服务的通知渠道 (`AppForegroundService`, 应用自身提示所用的唯一渠道, 与 `AndroidSettingsAutoBackup` 相同), tag `db-recovery:<库名>`, 无点按动作; 文件版本读不到 (0) 时用不提还原条件的文案 (`database_recovery_notification_content_unreadable`); API 33+ 没有 `POST_NOTIFICATIONS` 时跳过. inrt 只记日志.
8. T2 接口: `openOrRecover` 增加 `onFailure` 参数, 在移动文件之前执行, 供 Room (P2) 关闭 helper; 第二次打开仍失败时直接抛出.
9. T8 源码守卫: 不做. 理由: 只涉及两个类, 约定已写进两个类的注释与 T8; JVM 单测要编译整个 app 单测源集, 成本高, 且共享工作区里他人在改的单测可能编译不过, 守卫会被无关失败拖累.
10. P2 接入后的两处缺口 (db-downgrade-p2 交接笔记): 备份名正则不再要求 `.db` 结尾 (`keystore-database` 的备份可轮换与还原), "当前库没有行" 不计 `room_master_table` (Room 新建空库就有 identity 行). 用例 `restoreHandlesRoomDatabaseWithoutDbSuffix`.
11. T6 脚本修正: (a) debug 包有两个 LAUNCHER 入口, `monkey` 可能选中 LeakCanary 的 "Leaks" 入口, 各次运行的启动路径因此不一致; 改为 `cmd package query-activities` 取本应用入口并排除它, 取不到时才用 `monkey`; (b) 通知在强停前读取 (API 35 的 `am force-stop` 会清掉本包通知); (c) 新装 debug 包在 API 35 首启做 ART 校验, `App.onCreate` 超过 10 s, 修后验收用 `--observe 30`; (d) `restore-check` 的重装偶尔卡住 (API 24 上一次 300 s 超时), 改为 180 s 超时重试一次, 且任何一步失败都先放回数据; (e) 直接构建的 inrt debug 包是没有项目的模板, 从桌面入口启动时 `SplashActivity` 因 `ProjectConfig.fromAssets` 返回 null 抛 NPE (`getLaunchConfig`), 与降级无关, 修前被 `App.onCreate` 的降级崩溃遮住 (修后首轮: API 35 / 24 各记到 1 次, 并被广播再拉起 1 / 2 次); 打包出的应用有 `project.json`, 不受影响. inrt 修后验收改用 `--start broadcast` (向 `ProfileInstallReceiver` 发它忽略的 action, 只拉起进程走完 `App.onCreate`, 与真实循环里后台组件拉起相同), 唤醒本来就不经界面.
12. 修后包与修前包 versionCode 相同 (5935 / inrt 5835): VERSION_BUILD 未变, 两者只差 P1 的提交. API 24 用临时 AVD `dbdowngrade_p01_api24`.

### P3 实施处置 (2026-10-24, db-downgrade-p3)

1. T5 "`SQLite.kt` 解析该回调": `SQLite.kt` 不需要改. 回调对象经 `jsTryToJava<DatabaseCallback>()` 整体适配为接口 (Rhino `InterfaceAdapter`), 接口新增的方法自动对应对象上的同名函数; 对象没有该方法时, 适配器只报一条 Rhino 警告 (宿主未设 ErrorReporter, 不输出) 并返回 null. 所以 "未提供回调" 与 "回调对象没有 onDowngrade" 都是无操作降级 (附录 B.4 前两例). 新脚本模块层的 `SqliteModule` 交给同一个 `SQLite.open`, 新层开关两态行为相同.
2. `DatabaseCallback.onDowngrade` 与其他 4 个方法一样是抽象方法, 不用 Java 默认方法: Rhino 的接口代理不执行默认方法体 (缺方法时直接返回 null), 默认方法没有额外作用. 仓库内没有其他实现类.
3. 降级在 Android 管理的事务内: 回调抛出时回滚, 版本与数据不变, `sqlite.open` 抛出 (消息前缀 `Failed to call method "sqlite.open".`); 文档写明需要旧行为 (拒绝降级) 时在回调中抛出. readOnly 不影响 (Android 先取可写连接).
4. 验收用设备脚本 (`tools/db-downgrade/sqlite_ondowngrade.py` + `sqlite_ondowngrade_probe.js`), 不另写 androidTest: `Database` 依赖 `ScriptRuntime` (路径解析, closeableManager), 回调须经 Rhino 接口适配, 脚本层才覆盖真实路径. 探针放在宿主私有目录 (经 run-as), 但 `RunIntentActivity` 的 `PathChecker` 运行任何路径前都要求存储权限, 临时 AVD 上经 adb 授予 (API 35 `appops`, API 24 `pm grant`), 脚本先检查. 另改 `SqliteModuleTest` 的设备用例: 原 "version 1 打开 v2 库抛 SQLiteException" 改为期望 `[1,1]`, 加 onDowngrade 参数顺序与抛出回滚两条; augment 的 `device_eval.py` 用例文件随之更新 (旧构建配新用例文件会在这条上不一致).
5. `tools/db-downgrade/` 被 `.gitignore` 的 `/tools/*` 忽略且没有放行行 (P0.3 的文件是强制添加的), 本条同样 `git add -f`, 不改共享的 `.gitignore`; `tools/db-downgrade/README.md` 当时由 db-downgrade-q8-q9 在改, 本条未改它, 用法写在脚本 docstring.
6. 文档: 共享文档工作区有其他会话未提交的 memory 文档, 全量生成与离线同步改在文档仓库的隔离克隆中进行, 生成物拷回后只提交本条路径. 全量生成同时刷新了 `63e04f9` (speech punctuation, 改了 Markdown 未重新生成) 的 speech / changelog 页, 一并提交并同步进离线插件 (提交说明已写明). DatabaseCallback 的说明由 "对象应实现 4 个方法" 改为 "可只实现需要的方法, 未实现的在触发时被忽略" (依据同第 1 条).
7. d.ts: 两个仓库的 `aj6-int-sqlite.d.ts` 相同, `JavaScriptDatabaseCallback` 加可选 `onDowngrade?`. `aj6dts.bat -Publish` 以宿主隔离克隆 (HEAD `e03d82e0f`, 不含共享工作区他人未提交的文件) 为输入重新生成 `aj6-main-app.d.ts` 等; `aj6dts.properties` 里的声明仓库路径 `D:/webstorm-projects/...` 不存在, 用 `-DeclarationsOutput` / `-ApiRoots` 覆盖. 版本: d.ts 4.83.0, Ace 2.45.0 (VERSION_BUILD 331), 10 种语言 changelog.
8. 推送: 宿主不推送 (R1). 文档仓库惯例推送, 但 origin 之前还有他人未推送的 `63e04f9`; 离线文档插件惯例推送; d.ts 与 Ace 惯例只本地 (各积压 50 余笔). 按 R6 "不确定时只提交", 四个关联仓库都只本地提交, 推送由维护者决定.

## 附录 B. 证据

### B.1 XQ-AT72 取证 (P0.1)

| 项 | 结果 |
| --- | --- |
| inrt 崩溃异常与消息 | XQ-AT72 的 dropbox 没有 inrt 条目: 全部 75 条记录最早为 2026-10-20 15:34:59, 不是已轮转, 而是从未写入 (原因见 "停止条件" 一行). 异常以 AVD 复现为准: `RuntimeException: Unable to create application org.autojs.autojs.App`, Caused by `SQLiteException: Can't downgrade database from version 6 to 5`, 栈顶 `SQLiteOpenHelper.onDowngrade` (XQ-AT72 上是 IntentTask 4 -> 3). 宿主 6.6.4 的同类崩溃在 XQ-AT72 dropbox 里有 30 条: `ExceptionInInitializerError`, Caused by `Can't downgrade database from version 5 to 3`, 经 `Database.<init>` <- `TimedTaskManager.<clinit>` <- `TimedTaskScheduler.init` <- `App.onCreate` |
| 拉起方 (provider / receiver / service / job) | XQ-AT72: events 缓冲区从 10-23 16:11 起, 已没有 inrt 的 `am_proc_start` (已轮转); jobscheduler 0 条, alarm 0 条, 无 bad process. 清单: provider authority 7 个 (含 `org.autojs.autojs6.inrt.shizuku`), receiver 47 条, service 5 条. AVD API 35 复现 (Shizuku 13.6.0): 服务端每次重启先经 `ShizukuProvider` (content provider) 拉起 1 次; 进程死后, 待投递的启动类广播逐个再拉起 (`InrtBootCompletedReceiver` x2, `AudioCompatibilityProbeReceiver`, `StaticBroadcastReceiver`, `BootCompletedReceiver`, `RescheduleReceiver`, `JobBootReceiver`); 约 50 s 后 WorkManager `SystemJobService` 再拉起 1 次 |
| 崩溃次数与时间跨度 | XQ-AT72: 无记录 (dropbox 未写入, events 已轮转), 以 shizuku-oom P0 记的 123 次为准. AVD API 35: 服务端每次重启 inrt 崩溃 9 / 1 / 8 次 (3 轮共 18 次, 均为降级), 每次从启动到退出 5-15 s, 一轮约 60 s 后平息; 宿主同轮 7 / 8 / 8 次 |
| 宿主每轮 3 次的停止条件 | 系统的 bad-process 限制 (推断). 6.6.4 的 `CrashHandler` 把异常交给 Bugly 处理器再交给系统, XQ-AT72 dropbox 有 30 条 data_app_crash (v3274, 2026-10-22 23:17:13 - 23:29:16, 按每轮 3 次即 10 轮); 系统在 60 s 内第二次崩溃时把进程标为 bad process, 之后不再为后台请求拉起. 6.7.0 Alpha11 (`e61289c86a`, 移除 Bugly) 起, `CrashHandler.mSystemHandler` 改为 lazy, 第一次崩溃时才取默认处理器, 而这时默认处理器已是 `CrashHandler` 自身. 结果: 主线程崩溃递归 5 次后 `exitProcess(1)`, 系统没有 am_crash 与 dropbox 记录, 也不计崩溃次数, 每个待投递的广播, provider 调用与 job 都会再拉起一次 (inrt 5280 的 123 次; AVD 上 am_crash 恒为 0, 日志里只有 FATAL EXCEPTION). 已登记附录 C Q9 |
| inrt 数据库版本 4 的来源 | IntentTask 版本 4 从构建 5282 (`6ad80448f6`, 2026-09-21, "收到邮件" 意图任务) 开始. inrt 5280 首装 2026-09-14 18:07:47, 最后更新 2026-10-22 17:33:09 (即装回 5280 的时间); 两者之间装过 >= 5282 的 inrt 构建, 库被写成 4. 具体是哪个构建, 只读取证无法确定 (不能 run-as 读库) |

报告: `evidence/roundtrip-XQ-AT72-5280-20261023-forensics.md` (inrt), `evidence/roundtrip-XQ-AT72-5835-20261023-forensics.md` (宿主).

### B.2 往返结果 (修前 / 修后)

| 场景 | 修前 (P0.2) | 修后 AVD API 24 (P1.4) | 修后 AVD API 35 (P1.4) | 真机 (P4.1) |
| --- | --- | --- | --- | --- |
| 宿主 TimedTask / IntentTask 版本 +1 数据启动 | FAIL (5935): 桌面启动后 60 s 内启动 8 次 (重启 7 次, 均为广播拉起), 崩溃 8 次全是降级 (6 -> 5), am_crash 0; 31 s 后不再拉起; 库文件版本不变 (6 / 5), 标记行保留 | PASS (5935): 桌面入口启动 1 次, 重启 0, 崩溃 0 (am_crash 0, 日志 0), 30 s 末存活; 版本 6 -> 5 / 5 -> 4, 标记行 3/3 与 2/2, 未来列与表保留 | PASS (5935): 同左 | 待填 |
| inrt 同上 | FAIL (5835): 60 s 内启动 8 次 (重启 7 次, 启动类广播), 崩溃 8 次全是降级, am_crash 0; 15 s 后不再拉起 | PASS (5835): 广播拉起 1 次 (`--start broadcast`, 见 "P0 / P1 实施处置" 第 11 条 (e)), 重启 0, 崩溃 0, 30 s 末存活; 版本与行同上 | PASS (5835): 同左 | 待填 |
| Shizuku 服务端重启 3 次唤醒 inrt | FAIL: 每轮 (90 s) inrt 拉起并崩溃 9 / 1 / 8 次 (provider 1 次 + 启动类广播 + job), 宿主同轮 7 / 8 / 8 次; am_crash 均为 0 | - | PASS: 共 5 轮 (连续 3 轮, 再 2 轮每轮先 `make-future`, 即 3 轮带版本 +1 数据), inrt 与宿主每轮各由 `ShizukuProvider` 拉起 1 次, 崩溃 0 (am_crash 0, 日志 0), 90 s 末存活; 下一轮 `make-future` 读到 5 / 4, 即唤醒时已降回代码版本 | - |
| 打开失败兜底 (`inject-fail`) | - | PASS (宿主与 inrt): 不崩溃, 启动 1 次; 两库各新增一份 `<库>.bak-0-<时间>` (目录占位读不到版本, 记 0), 新库为代码版本 (5 / 4) 且为空; `DatabaseRecovery` W 日志 79 行 (含栈帧); 宿主两条 `db-recovery:<库名>` 通知, inrt 只记日志 | PASS (宿主与 inrt): 同左, W 日志 85 行 | 待填 |
| 自动还原 (`restore-check`) | - | PASS (宿主与 inrt): (a) 不重装不还原; (b) 重装后还原, 备份 0 份, 标记行 3/3 与 2/2, 版本 5 / 4; (c) 备份版本高于代码版本, 重装后不还原; 结束后数据放回 | PASS (宿主与 inrt): 同左 | 待填 |

修前报告 (AVD `dbdowngrade_p01_api35`, Android 15): `evidence/roundtrip-dbdowngrade_p01_api35-5935-20261023-before-fix.md` (宿主), `evidence/roundtrip-dbdowngrade_p01_api35-5835-20261023-before-fix.md` (inrt 与唤醒). 修前构建是当前 HEAD 的 debug 包 (宿主 x86_64 5935, inrt universal 5835), 两张任务表的代码版本与 HEAD 相同 (TimedTask 5, IntentTask 4).

修后报告: `evidence/roundtrip-dbdowngrade_p01_api24-5935-20261023-after-fix.md` (AVD `dbdowngrade_p01_api24`, Android 7.0, 宿主与 inrt), `evidence/roundtrip-dbdowngrade_p01_api35-5935-20261023-after-fix.md` (AVD `dbdowngrade_p01_api35`, Android 15, 宿主, inrt 与唤醒). 修后包与修前包 versionCode 相同, 只差 P1 的提交 ("P0 / P1 实施处置" 第 12 条).

### B.3 Room 三库 (P2.3)

2026-10-23, 临时 AVD `dbdowngrade_api35` (API 35, x86_64), versionCode 5935, `room_pages.py` 28 / 28 项通过 (报告 `evidence/roundtrip-dbdowngrade_api35-5935-20261023-room.md`).

| 库 | 未来文件 | 打开后 | 标记行 | 多出的表 |
| --- | --- | --- | --- | --- |
| History | v4, `revision` 多一列 | v3, identity hash = 快照, 重建 1 次, `revision` 12 列 | `file_entry` 1/1, `revision` 2/2 (已固定), `trash_item` 1/1 | `room_future_extra` 保留 1 行 |
| ColorHistory | v3, `color_library` 多一列 | v2, 同上, `color_library` 9 列 | `palette_history` 1/1, `color_library` 1/1, `color_library_color` 1/1 | 同上 |
| KeyStore | v2, `KeyStore` 多一列 | v1, 同上, `KeyStore` 6 列 | `KeyStore` 1/1 (指向存在的文件) | 同上 |

页面与任务: 从桌面入口启动后存活; 抽屉, 版本历史页, 回收站, 颜色库页, 颜色选择页 (旧版布局), 密钥库页均进入 RESUMED; `HistoryCleanupWorker` 单次运行 SUCCEEDED (首次运行即成功, 未重试). logcat: FATAL EXCEPTION 0, CrashHandler 未捕获异常 0, am_crash 0, Room 打开错误 0, 移到一旁的表 0, `_downgrade_` 残留表 0.

### B.4 脚本 sqlite 模块 (P3.1)

2026-10-24, 隔离克隆 (HEAD `2f0663ba8` + P3.1 的两个文件, 即提交 `e03d82e0f` 的代码) 的 debug x86_64 5935 (md5 e0acdf85), 临时 AVD `dbdowngrade_p3_api24` (Android 7.0) 与 `dbdowngrade_p3_api35` (Android 15). `sqlite_ondowngrade.py` 经 `RunIntentActivity` 运行探针 (宿主存储权限经 adb 授予, 无点按), 两台结果相同:

| 用例 | 结果 | 数值 |
| --- | --- | --- |
| 无回调, v2 库以 version 1 打开 | PASS | 不抛, 版本 1, 行 2; 再以 version 2 打开时 onUpgrade 1 -> 2 |
| 回调对象没有 onDowngrade (只有 onCreate / onOpen) | PASS | 不抛, 版本 1, 行 2, 事件只有 open |
| onDowngrade 回调 (v3 库以 version 2 打开) | PASS | 事件 downgrade 3->2 然后 open (无 onCreate / onUpgrade); 3 个参数, 类型 number / number, database 为 `core.database.Database`; 回调内版本仍为 3 且 `inTransaction()` 为 true, onOpen 内为 2; 回调插入的行保留 (行 3) |
| onDowngrade 抛出 | PASS | sqlite.open 抛出 (消息 `Failed to call method "sqlite.open". <回调的消息>`), 之后版本仍为 2, 行 2 (回调抛出前插入的行已回滚) |
| readOnly: true, v2 库以 version 1 打开 | PASS | 版本 1, 行 2 |
| `SqliteModuleTest` 设备用例 (`device_eval.py --strict-errors`) | PASS | 新层开: 两台各 25 / 25 一致; 新层关 (API 35): 9 条开库用例一致, 16 条参数错误不同, 均为 augment 附录 E P3.5 已记录的差异 |

修前的行为 (Android 默认 `onDowngrade` 抛 `SQLiteException: Can't downgrade database from version 2 to 1`) 由改动前的 `SqliteModuleTest` 设备用例 "`sqlite('aj6-p35-sqlite.db', { version: 1 })` 抛 `JavaException SQLiteException`" 记录 (augment P3.5 真机验收, XQ-AT72 一致), 本条把它改为期望 `[1,1]`. 报告: `evidence/roundtrip-dbdowngrade_p3_api24-5935-20261024-sqlite.md`, `evidence/roundtrip-dbdowngrade_p3_api35-5935-20261024-sqlite.md`.

## 附录 C. 待决事项

格式: `- [状态] 编号 问题 | 选项: A (推荐) / B | 影响: 条目 | 改选返工: ... | 来源`. 状态: 需拍板 / 已按推荐先行待确认 / 已决 (日期, 维护者选: ...) / 暂缓.

- (立项时无)
- [已按推荐先行待确认] Q8 Room 降级时某张表缺少本版本必需的列 (NOT NULL 且无默认值), 或与快照没有共有列, 其行无法搬进新表, 如何处置 | 选项: A 旧表改名为 `_downgrade_kept_<表名>_v<文件版本>` 留在库文件里, 新表为空 (推荐, 已先行; 满足 R3, 代价是库文件保留旧数据的体积, 之后装回较新版本也不会自动搬回) / B 按 T3 原文清空该表只记日志 (数据丢失, 与 R3 冲突) / C 该表重建失败即整库走兜底备份 (其他表也变空) | 影响: P2.1, P2.2 | 改选返工: B 约 10 行 + 改 1 个用例; C 约 20 行 + 改 1 个用例, 依赖 P1.2 | 来源: db-downgrade-p2 (附录 A "P2 实施处置" 第 3 条)
- [需拍板] Q9 `CrashHandler` 从不把异常交给系统 (6.7.0 Alpha11 移除 Bugly 后 `mSystemHandler` 取到的是自身, 见附录 B.1 "停止条件"), 主线程崩溃以 `exitProcess(1)` 结束, 系统不记崩溃也不限制重启; P1 / P2 只消除降级这一种启动期崩溃, 其他启动期崩溃仍会被每个待投递的广播, provider 调用与 job 各拉起一次. 是否修 | 选项: A 另立条目修: 在 `setDefaultUncaughtExceptionHandler` 之前取得系统处理器并传给 `CrashHandler`, 主线程崩溃时照旧打开崩溃报告页 (独立进程 `:crash_report`) 后交给系统, 恢复 am_crash / dropbox 记录与系统的 bad-process 限制 (后台拉起在 60 s 内第二次崩溃后停止) (推荐, 未先行: 改的是全局崩溃路径, 前台崩溃会多出系统的 "应用已停止" 处理, 且不在本批文件范围内) / B 不修, 只靠 P1 / P2 消除降级崩溃 / C 修 A 之外再加跨进程的启动崩溃计数 (例如连续 3 次启动期崩溃后跳过非必要初始化) | 影响: 新条目 (`tool/CrashHandler.kt`, `App.kt`), 不影响 P1 / P2 | 改选返工: A 约 20 行 + 手测前台 / 后台崩溃各一次; C 另加约 60 行与用例 | 来源: db-downgrade-p0-p1 (P0.1 / P0.2)

## 附录 D. 文件地图

| 文件 | 作用 | 阶段 |
| --- | --- | --- |
| `app/src/main/java/org/autojs/autojs/storage/database/TimedTaskDatabase.java` | 定时任务表, VERSION 5 | P1.1 |
| `app/src/main/java/org/autojs/autojs/storage/database/IntentTaskDatabase.java` | 意图任务表, VERSION 4 | P1.1 |
| `app/src/main/java/org/autojs/autojs/storage/database/Database.java` | 两表基类, 构造即打开 | P1 (只读) |
| `app/src/main/java/org/autojs/autojs/timing/TimedTaskManager.kt` | 两库的 lazy 打开点, 兜底接入处 | P1.2 |
| `app/src/main/java/org/autojs/autojs/storage/database/SQLiteSchemaUtils.java` | `hasColumn` / `addColumnIfMissing` (T1) | P1.1 |
| `app/src/main/java/org/autojs/autojs/storage/database/DatabaseRecovery.kt` | 打开失败兜底: 备份, 空库, 轮换, 还原, 宿主通知 (T2 / T7 / T9); Room 经 `RoomDowngrade` 接入 | P1.2 |
| `app/src/androidTest/java/org/autojs/autojs/storage/database/TaskDatabaseRecoveryTest.kt` | 两表降级 / 幂等升级与兜底 / 还原用例 | P1.3 |
| `app/src/main/java/org/autojs/autojs/App.kt` | 崩溃发生处 (`TimedTaskScheduler.init`, `initDynamicBroadcastReceivers`) | P0 / P1 (只读) |
| `app/src/main/java/org/autojs/autojs/tool/CrashHandler.kt` | 主线程异常处理, 无循环防护 | P0 (只读) |
| `app/src/main/java/org/autojs/autojs/storage/history/HistoryDatabase.kt` | Room, version 3 | P2 |
| `app/src/main/java/org/autojs/autojs/theme/app/ColorHistoryDatabase.kt` | Room, version 2 | P2 |
| `app/src/main/java/org/autojs/autojs/apkbuilder/keystore/KeyStoreDatabase.kt` | Room, version 1, exportSchema 改 true | P2 |
| `app/schemas/` | Room 导出 schema, 复制进 assets | P2.1 |
| `app/src/main/java/org/autojs/autojs/storage/database/RoomDowngrade.kt` | Room 通用降级 (包装 open helper, 动态 Migration, 按快照重建) | P2.1 |
| `app/src/androidTest/java/org/autojs/autojs/storage/database/RoomDowngradeTest.kt` | Room 三库降级用例 | P2.2 |
| `app/src/androidTest/java/org/autojs/autojs/storage/database/RoomDowngradePagesDeviceTest.kt`, `tools/db-downgrade-room/room_pages.py` | Room 三库未来文件的页面与 `HistoryCleanupWorker` 核对 (instrumentation 参数 `autojs.dbDowngrade.pages=true`) | P2.3 |
| `app/src/main/java/org/autojs/autojs/core/database/Database.java` | 脚本 sqlite 模块底层 helper 与 `DatabaseCallback` | P3.1 |
| `app/src/main/java/org/autojs/autojs/runtime/api/augment/sqlite/SQLite.kt` | 脚本 `sqlite.open` 选项解析 (P3.1 未改, 见 "P3 实施处置" 第 1 条) | P3.1 |
| `app/src/test/java/org/autojs/autojs/runtime/api/modules/sqlite/SqliteModuleTest.kt` | 新层 sqlite 的 JVM 用例与导出的设备用例 (降级一条改为期望保留数据, 加 onDowngrade 两条) | P3.1 |
| `tools/db-downgrade/sqlite_ondowngrade.py`, `sqlite_ondowngrade_probe.js` | 脚本 sqlite onDowngrade 设备用例 (RunIntentActivity, 5 例) | P3.1 |
| `tools/db-downgrade/roundtrip.py`, `README.md` | 往返脚本 | P0.3 |
| `docs/db-downgrade/evidence/` | 报告 | 各阶段 |
| `.changelog/lang_zh-Hans.json`, `docs/dev/6.8.0-release-gate.md` | changelog 与发布门 (只追加自己的 hunk) | P4 |
| `D:/idea-projects/AutoJs6-Documentation/api/sqlite.md` (与 `api/progress.md`, 生成物), `AutoJs6-Plugin-Offline-Docs` (同步), `AutoJs6-TypeScript-Declarations/declarations/autojs6/aj6-int-sqlite.d.ts`, `AutoJs6-Plugin-Ace-Editor/app/src/main/assets/editor/ace-builds-1.44.0/autojs6/types/autojs6/aj6-int-sqlite.d.ts` | 文档与声明; `declarations/autojs6-node/sqlite.d.ts` 是 Node.js 的异步数据库接口, 没有 version 与回调, P3.2 不涉及 | P3.2 |
