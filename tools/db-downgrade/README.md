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
| `check` | 从桌面入口启动 (`cmd package query-activities` 取本应用的入口, 排除 debug 包里 LeakCanary 的 "Leaks" 入口; 取不到时用 `monkey`), 观察 `--observe` 秒 (默认 10) 后强停, 读 events, logcat 与两个库 | 观察期末进程存活; 无崩溃 (见下文 "崩溃计数"); 进程启动不超过 1 次; `user_version` 等于代码版本; 标记行齐全; 未来列与表仍在 (`--no-future` 可免检后两项) |
| `inject-fail` | 把库文件改名为 `.roundtrip-kept`, 在原位置放同名目录, 启动 | 不崩溃; 每个库新出现一份 `<库>.bak-<版本>-<yyyyMMddHHmmss>`; 新库为代码版本且为空; `DatabaseRecovery` W 日志不少于 2 行; 宿主另需两条 tag 为 `db-recovery:<库名>` 的通知 (API 33+ 先 `pm grant POST_NOTIFICATIONS`; 在强停前读取, API 35 的 `am force-stop` 会清掉本包通知). 结束后放回原库 |
| `restore-check` | 需要 `--apk` (当前安装的同一构建). (a) 把库改名为本代码版本的备份, 不重装直接启动; (b) 重装后启动; (c) 改名为代码版本 + 1 的备份, 重装后启动 | (a) 不还原; (b) 还原, 备份消失, 标记行齐全; (c) 不还原. 结束后手工放回 (某步失败或安装超时也放回; 安装卡住时重试一次) |
| `future-build` | 需要 `--future-apk` (各库版本 +1 的临时构建, 不提交, 做法见下文 "真机复核") 与 `--apk` (被测构建). 在代码版本下补标记行, 未来列与未来表; 装临时构建并启动, 由它自己的 helper 把 `user_version` 升到更高版本; 再装被测构建, 按 `check` 核对 (ROADMAP P4.1) | 临时构建启动无崩溃, 留下的两个库版本高于代码版本且标记行齐全; 之后 `check` 各项 PASS |
| `suite` | `make-future`, `check`, `inject-fail`, 有 `--apk` 时再跑 `restore-check` | 各项均 PASS |
| `wake` | 每轮强停 `--wake-package` (默认 `--package`), 重启 adb 方式启动的 Shizuku 服务端 (`--start-sh` 存在时 `sh` 执行它, 否则直接执行 Shizuku APK 里的 `lib/<abi>/libshizuku.so`, 13.6.0 不再生成 `start.sh`), 等新的 `shizuku_server` 进程, 观察 `--observe` 秒后统计并强停; 共 `--rounds` 轮 (默认 3) | 服务端已重启, 每轮无崩溃且进程启动不超过 1 次 |
| `forensics` | 只读: `dumpsys package` (版本, 安装 / 更新时间, provider authority), `dumpsys dropbox --print data_app_crash` (条目数, 首末时间, 根因异常首行, 栈顶帧, 每日条数), `dumpsys activity processes` (bad processes), `dumpsys jobscheduler` / `dumpsys alarm` 计数, `logcat -b events` 的启动类型与崩溃计数 | 只记录, 不判定 |

其他参数: `--start launcher|broadcast` (`check` / `inject-fail` / `restore-check` 的启动方式, 默认 `launcher` 即上表的桌面入口; `broadcast` 向导出接收器 `androidx.profileinstaller.ProfileInstallReceiver` 发一个它忽略的 action, 加 `-f 0x20` 包含已停止的包, 只拉起进程走完 `App.onCreate`, 与后台被 provider 或广播拉起时相同), `--code-version timed=N` / `intent=N` (覆盖从 `app/src/main/java/org/autojs/autojs/storage/database/*.java` 解析的 `VERSION`), `--report-dir` (默认 `docs/db-downgrade/evidence`, 相对路径按仓库根目录解析), `--start-sh` (默认 `/sdcard/Android/data/moe.shizuku.privileged.api/start.sh`).

## 真机复核 (P4.1)

在维护者继续使用的真机上, 测前测后用 `device_state.py` 保存并放回现装的包与 `databases/`:

```
py -3.12 -I tools/db-downgrade/device_state.py save|restore -s SERIAL --dir <PC 上的目录> --package org.autojs.autojs6 [--package org.autojs.autojs6.test] [--package org.autojs.autojs6.inrt]
```

`save` 拉取每个包的全部 APK (记 md5), 强停后经 `run-as` 把 `databases/` 存成 tar (测试 APK 没有 `databases/`, 只存 APK; 未安装的包记下, `restore` 时卸载). `restore` 先在被测构建仍在且已停止时放回 `databases/`, 再 `install -r -d` 装回原包并核对 md5 与文件列表. 顺序不能反: 不含修复的旧构建遇到被新构建升级过的库会在启动时崩溃 (ROADMAP 1.4), 而包被替换后它可能随即被拉起. `room_pages.py` 另在 `files/` 写了 `aj6-db-downgrade-room.bks`, 测后手工删除.

版本 +1 的临时构建只在隔离克隆里改, 不提交: `TimedTaskDatabase.VERSION` 5 -> 6, `IntentTaskDatabase.VERSION` 4 -> 5; Room 三库 `version` 各 +1 (History 4, ColorHistory 3, KeyStore 2), builder 各加一个空的 `Migration(旧, 新)`; 实体不变, KSP 在克隆的 `app/schemas` 生成新版本的快照. 用法:

```
py -3.12 -I tools/db-downgrade/roundtrip.py future-build -s SERIAL --future-apk <临时宿主> --apk <被测宿主> --observe 30 --label real-<型号>
py -3.12 -I tools/db-downgrade-room/room_pages.py -s SERIAL --future-apk <临时宿主> --apk <被测宿主> --observe 15
```

`room_pages.py --future-apk` 写的是代码版本的文件 (标记行, 多出的表, 正确的 identity hash, 没有未来列), 由临时构建在各页面打开并经空迁移升级, 被测构建再按快照降级. ColorOS 新装包 (例如设备上原本没有的 inrt) 会弹 "来自电脑" 安装确认页, `adb install` 阻塞等待, 由维护者在手机上点按.

## 崩溃计数

6.7.0 Alpha11 到 P1.5 之前的 `CrashHandler` 不把异常交给系统, 主线程崩溃后自行 `exitProcess(1)`, 系统不写 `am_crash` 与 dropbox (ROADMAP 附录 B.1, Q9; P1.5 / D10 已修). 所以 "无崩溃" 同时要求: events 里本包 `am_crash` 为 0; 本次观察期间 `CrashHandler` / `AndroidRuntime` 的 E 日志里没有本包进程打出 "Uncaught Exception" 或 "FATAL EXCEPTION". 报告分别列出两者 (`am_crash N`, `logged N`) 及其中的降级崩溃数. 每次启动前开始持续抓取 CrashHandler / AndroidRuntime 的 E 日志与 `DatabaseRecovery` 的 W 日志, 观察结束后统计: 手机的 main 缓冲可能只有 256 KiB (G8441, OPD2413), 启动时写的行到观察期末已被轮转, `logcat -d` 会少计 (P4.1). `room_pages.py` 同样从启动起持续抓取 logcat.

## 崩溃移交 (`crash_handover.py`, P1.5)

```
py -3.12 -I tools/db-downgrade/crash_handover.py foreground|background -s SERIAL [--package org.autojs.autojs6.inrt] [--label before-fix]
```

核对 `CrashHandler` 把会结束进程的崩溃交给系统处理器 (D10): 系统记 `am_crash` 与 dropbox `data_app_crash`, 一分钟内第二次崩溃的进程被标为 bad, 之后不再为后台广播拉起. 需要带下面临时崩溃注入的 debuggable 构建 (只在隔离副本里改, 不提交), 放在 `App.onCreate` 主进程分支的 `setUpDebugEnvironment()` 之后:

```kotlin
if (getProcessNameCompat() == applicationInfo.processName) {
    if (java.io.File(filesDir, "db-downgrade-crash-startup").exists()) {
        throw IllegalStateException("db-downgrade P1.5 injected startup crash")
    }
    if (java.io.File(filesDir, "db-downgrade-crash-foreground").exists()) {
        registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            private var armed = true
            override fun onActivityResumed(activity: android.app.Activity) {
                if (!armed) return
                armed = false
                android.os.Handler(mainLooper).postDelayed({ throw IllegalStateException("db-downgrade P1.5 injected foreground crash") }, 3000)
            }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }
}
```

| 子命令 | 做什么 | PASS 条件 |
| --- | --- | --- |
| `foreground` | 经 `run-as` 放 `files/db-downgrade-crash-foreground`, 从桌面入口启动, 观察 `--observe` 秒 (默认 30), 每秒查一次 `CrashReportActivity` 是否处于 resumed; 直接构建的 inrt 包在 `SplashActivity` 先因 NPE 崩溃 (见下文 "注意"), 同样是前台主线程崩溃 | 本包进程打出 1 次未捕获异常; 崩溃报告页 resumed (`:crash_report` 进程); `am_crash` 与日志数一致且不少于 1; dropbox 新增本包 `data_app_crash` 条目; 崩溃的进程已结束 (之后 WorkManager 的 job 等可能拉起新的主进程, 不计; 短时间内重复崩溃时系统弹出 "Application Error" 对话框, 进程等它关闭才结束, 脚本等它自行超时关闭, `--dialog-wait` 默认 330 s, 不点按) |
| `background` | 若 `dumpsys activity processes` 里本包是 bad process, 先经 provider (`content://<包名>.shizuku`, 系统不算后台启动) 拉起一次清除标记并强停; 再放 `files/db-downgrade-crash-startup`, 每 `--interval` 秒 (默认 5) 发一次 `roundtrip.py --start broadcast` 同款显式广播, 共 `--observe` 秒 (默认 60) | 每次启动都崩溃且都有 `am_crash`; 系统日志有 "crashed too many times" (或 `am_process_crashed_too_much`); 有 `am_proc_bad` 或结束时仍列为 bad; bad 标记之后没有再启动 (系统不再为后台广播拉起 bad 进程, API 35 跳过时不写日志). 报告另列每条广播是否在 `--interval` 秒内完成: 修前接收进程自行退出, 系统一再重投同一条广播, `am broadcast` 不返回 |

两个子命令结束时都删掉注入标记并强停. 未含 P1.5 的构建两项都 FAIL (没有 `am_crash` 与 dropbox 条目, 每个广播都拉起一次), 用 `--label before-fix` 记作对照. 报告 `<report-dir>/roundtrip-<AVD 名或型号>-<versionCode>-<yyyyMMdd>-<label>.md` (label 默认 `crash-handover`), 只含次数与第一个异常的首行 (R4).

## 标记行

标记行指向不存在的脚本 `/sdcard/aj6-db-downgrade/marker-*.js`, 只在 2099 年运行一次; IntentTask 标记行的 action 为 `org.autojs.autojs6.dbdowngrade.NOOP`, 不会被触发.

## 备份命名

须与 `DatabaseRecovery` 一致: 主文件 `<库>.bak-<文件版本>-<yyyyMMddHHmmss>`, 伴随文件在其后加 `-wal` / `-shm` / `-journal` (与 SQLite 的命名方式相同, 备份可以直接打开). 文件版本读不到时记 0.

## 输出

`<report-dir>/roundtrip-<AVD 名或型号>-<versionCode>-<yyyyMMdd>[-<label>].md`, 每次运行追加一节 (时间, 子命令, 包名, PASS / FAIL), 表格列为 项 / 结果 / 数值. 报告只含通过 / 失败与关键数值 (次数, 行数, 版本号, 文件名, 第一个崩溃的异常类与消息首行), 不含完整 logcat 与库内容 (ROADMAP R4).

## 注意

- XQ-AT72 上的 inrt 5280 是复现样本, 只能跑 `forensics` (ROADMAP R1 / D8).
- 直接构建的 inrt 包是没有项目的模板 (`assets/project/project.json` 不存在), 从桌面入口启动会在 `SplashActivity` 因 `ProjectConfig` 为 null 抛 NPE, 与降级无关 (修前被 `App.onCreate` 的降级崩溃遮住). 修后验收 inrt 用 `--start broadcast`.
- 修复前的构建在 `check` 里会进崩溃循环, 脚本观察期末强停; `wake` 每轮结束也强停, 因为修复前循环不会自行结束.
- 修复前一次服务端重启引发的循环约 60 s 才平息 (provider, 启动类广播, 约 50 s 后 WorkManager 的 job), `wake` 建议 `--observe 90`.
- Shizuku 授权等对话框由维护者操作, 脚本不点屏幕.
- 刚装好的 debug 包在 API 35 模拟器上首次启动要做 ART 校验 (没有 base.dm), `App.onCreate` 超过 10 s, 修后验收用 `--observe 30`.
- `DatabaseRecovery` 的 W 日志行数含异常栈帧 (每次打开失败数十行), 只作 "不少于 2 行" 判定.
