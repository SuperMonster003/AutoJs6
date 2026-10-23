# 数据库降级往返

配合 `docs/db-downgrade/ROADMAP.md` 使用 (P0.3, T6). 宿主与打包应用 (inrt) 在 `App.onCreate` 里同步打开 `TimedTaskDatabase.db` (表 `TimedTask`) 与 `IntentTaskDatabase.db` (表 `IntentTask`). 文件版本高于代码版本时, 修复前的构建抛出 "Can't downgrade database from version X to Y", 进程启动即崩溃. 修复只保护含修复的构建, 所以脚本不在新数据上装旧版本, 而是为当前构建造 "未来数据" (ROADMAP 1.4).

## 用法

```
py -3.12 -I tools/db-downgrade/roundtrip.py <子命令> -s SERIAL [--package org.autojs.autojs6.inrt] [--label before-fix]
```

除 `forensics` 外都需要 debuggable 构建 (经 `run-as` 读写 `databases/`). 设备不需要 sqlite3: 库文件拉到本机用 Python sqlite3 改写, 推回时先 `adb push` 到 `/data/local/tmp/aj6-db-downgrade/`, 再由 `run-as` 写入, 重新拉取核对 sha256 后删除临时文件.

| 子命令 | 做什么 | PASS 条件 |
| --- | --- | --- |
| `make-future` | 两个库缺失时先启动一次让构建建库; 拉取库文件 (连同 `-wal` / `-shm` / `-journal`, 本机 checkpoint 并改为 `journal_mode=DELETE`), 补足标记行 (TimedTask 3 行, IntentTask 2 行), 加列 `roundtrip_future_note TEXT DEFAULT 'future'` 与表 `roundtrip_future_extra`, `user_version` 设为代码版本 + `--bump` (默认 1); 推回单个文件, 删掉设备上的伴随文件 | 改写后版本, 标记行, 未来列与表都在 |
| `check` | 从桌面入口 (`monkey`) 启动, 观察 `--observe` 秒 (默认 10) 后强停, 读 events, logcat 与两个库 | 观察期末进程存活; 无崩溃 (见下文 "崩溃计数"); 进程启动不超过 1 次; `user_version` 等于代码版本; 标记行齐全; 未来列与表仍在 (`--no-future` 可免检后两项) |
| `inject-fail` | 把库文件改名为 `.roundtrip-kept`, 在原位置放同名目录, 启动 | 不崩溃; 每个库新出现一份 `<库>.bak-<版本>-<yyyyMMddHHmmss>`; 新库为代码版本且为空; `DatabaseRecovery` W 日志不少于 2 行; 宿主另需两条 tag 为 `db-recovery:<库名>` 的通知 (API 33+ 先 `pm grant POST_NOTIFICATIONS`). 结束后放回原库 |
| `restore-check` | 需要 `--apk` (当前安装的同一构建). (a) 把库改名为本代码版本的备份, 不重装直接启动; (b) 重装后启动; (c) 改名为代码版本 + 1 的备份, 重装后启动 | (a) 不还原; (b) 还原, 备份消失, 标记行齐全; (c) 不还原. 结束后手工放回 |
| `suite` | `make-future`, `check`, `inject-fail`, 有 `--apk` 时再跑 `restore-check` | 各项均 PASS |
| `wake` | 每轮强停 `--wake-package` (默认 `--package`), 重启 adb 方式启动的 Shizuku 服务端 (`--start-sh` 存在时 `sh` 执行它, 否则直接执行 Shizuku APK 里的 `lib/<abi>/libshizuku.so`, 13.6.0 不再生成 `start.sh`), 等新的 `shizuku_server` 进程, 观察 `--observe` 秒后统计并强停; 共 `--rounds` 轮 (默认 3) | 服务端已重启, 每轮无崩溃且进程启动不超过 1 次 |
| `forensics` | 只读: `dumpsys package` (版本, 安装 / 更新时间, provider authority), `dumpsys dropbox --print data_app_crash` (条目数, 首末时间, 根因异常首行, 栈顶帧, 每日条数), `dumpsys activity processes` (bad processes), `dumpsys jobscheduler` / `dumpsys alarm` 计数, `logcat -b events` 的启动类型与崩溃计数 | 只记录, 不判定 |

其他参数: `--code-version timed=N` / `intent=N` (覆盖从 `app/src/main/java/org/autojs/autojs/storage/database/*.java` 解析的 `VERSION`), `--report-dir` (默认 `docs/db-downgrade/evidence`, 相对路径按仓库根目录解析), `--start-sh` (默认 `/sdcard/Android/data/moe.shizuku.privileged.api/start.sh`).

## 崩溃计数

6.7.0 起的 `CrashHandler` 不把异常交给系统, 主线程崩溃后自行 `exitProcess(1)`, 系统不写 `am_crash` 与 dropbox (ROADMAP 附录 B.1, Q9). 所以 "无崩溃" 同时要求: events 里本包 `am_crash` 为 0; 本次观察期间 `CrashHandler` / `AndroidRuntime` 的 E 日志里没有本包进程打出 "Uncaught Exception" 或 "FATAL EXCEPTION". 报告分别列出两者 (`am_crash N`, `logged N`) 及其中的降级崩溃数.

## 标记行

标记行指向不存在的脚本 `/sdcard/aj6-db-downgrade/marker-*.js`, 只在 2099 年运行一次; IntentTask 标记行的 action 为 `org.autojs.autojs6.dbdowngrade.NOOP`, 不会被触发.

## 备份命名

须与 `DatabaseRecovery` 一致: 主文件 `<库>.bak-<文件版本>-<yyyyMMddHHmmss>`, 伴随文件在其后加 `-wal` / `-shm` / `-journal` (与 SQLite 的命名方式相同, 备份可以直接打开). 文件版本读不到时记 0.

## 输出

`<report-dir>/roundtrip-<AVD 名或型号>-<versionCode>-<yyyyMMdd>[-<label>].md`, 每次运行追加一节 (时间, 子命令, 包名, PASS / FAIL), 表格列为 项 / 结果 / 数值. 报告只含通过 / 失败与关键数值 (次数, 行数, 版本号, 文件名, 第一个崩溃的异常类与消息首行), 不含完整 logcat 与库内容 (ROADMAP R4).

## 注意

- XQ-AT72 上的 inrt 5280 是复现样本, 只能跑 `forensics` (ROADMAP R1 / D8).
- 修复前的构建在 `check` 里会进崩溃循环, 脚本观察期末强停; `wake` 每轮结束也强停, 因为修复前循环不会自行结束.
- 修复前一次服务端重启引发的循环约 60 s 才平息 (provider, 启动类广播, 约 50 s 后 WorkManager 的 job), `wake` 建议 `--observe 90`.
- Shizuku 授权等对话框由维护者操作, 脚本不点屏幕.
