# 崩溃移交: dbdowngrade_q89_api35 / 6.8.0 (5935)

- 设备: dbdowngrade_q89_api35 (emulator-5660), Android 15 / API 35
- 脚本: `tools/db-downgrade/crash_handover.py` (ROADMAP P1.5, D10); 构建带临时崩溃注入 (README, 不提交)
- 每节一次运行, 只记通过 / 失败与关键数值 (R4)

## 11:08:22 foreground org.autojs.autojs6 PASS

- 桌面入口启动 (org.autojs.autojs6/org.autojs.autojs.launcher.AdaptiveIconAlias), 首个 Activity 恢复后 3 s 主线程抛出 (注入), 观察 45 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| crash logged | PASS | 1 process(es); java.lang.IllegalStateException: db-downgrade P1.5 injected foreground crash |
| crash report page | PASS | CrashReportActivity resumed after 5 s, :crash_report process running at the end |
| am_crash | PASS | 1; java.lang.IllegalStateException: db-downgrade P1.5 injected foreground crash |
| dropbox data_app_crash | PASS | 1 new entry; java.lang.IllegalStateException: db-downgrade P1.5 injected foreground crash |
| crashed process | PASS | ended; main processes at the end 1 (started after the crash) |
| process starts | - | am_proc_start 2 (next-top-activity AdaptiveIconAlias 1, service SystemJobService 1) |

## 11:09:10 foreground org.autojs.autojs6.inrt PASS

- 桌面入口启动 (org.autojs.autojs6.inrt/org.autojs.autojs.inrt.SplashActivity), 首个 Activity 恢复后 3 s 主线程抛出 (注入), 观察 45 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| crash logged | PASS | 1 process(es); java.lang.RuntimeException: Unable to start activity ComponentInfo{org.autojs.autojs6.inrt/org.autojs.autojs.inrt.SplashActivity}: java.lang.NullPointerExceptio |
| crash report page | PASS | CrashReportActivity resumed after 2 s, :crash_report process running at the end |
| am_crash | PASS | 1; java.lang.NullPointerException: Attempt to invoke virtual method 'org.autojs.autojs.project.LaunchConfig org.autojs.autojs.project.ProjectConfig.getLaunchConfig |
| dropbox data_app_crash | PASS | 1 new entry; java.lang.NullPointerException: Attempt to invoke virtual method 'org.autojs.autojs.project.LaunchConfig org.autojs.autojs.project.ProjectConfig.getLaunchConfig |
| crashed process | PASS | ended; main processes at the end 1 (started after the crash) |
| process starts | - | am_proc_start 2 (next-top-activity SplashActivity 1, broadcast StaticBroadcastReceiver 1) |

## 11:14:29 foreground org.autojs.autojs6 PASS

- 桌面入口启动 (org.autojs.autojs6/org.autojs.autojs.launcher.AdaptiveIconAlias), 首个 Activity 恢复后 3 s 主线程抛出 (注入), 观察 45 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| crash logged | PASS | 1 process(es); java.lang.IllegalStateException: db-downgrade P1.5 injected foreground crash |
| crash report page | PASS | CrashReportActivity resumed after 6 s, :crash_report process running at the end |
| am_crash | PASS | 1; java.lang.IllegalStateException: db-downgrade P1.5 injected foreground crash |
| dropbox data_app_crash | PASS | 1 new entry; java.lang.IllegalStateException: db-downgrade P1.5 injected foreground crash |
| crashed process | PASS | ended (system crash dialog for a repeated crash, ended 264 s later when the dialog timed out); main processes at the end 1 (started after the crash) |
| process starts | - | am_proc_start 1 (next-top-activity AdaptiveIconAlias 1) |

## 11:25:14 background org.autojs.autojs6 PASS

- 每 5 s 发一次显式广播 (roundtrip.py --start broadcast 同款), 共 60 s; App.onCreate 抛出 (注入)

| 项 | 结果 | 数值 |
| --- | --- | --- |
| bad mark before | PASS | none |
| crashes | PASS | 2 of 2 start(s) crashed; java.lang.RuntimeException: Unable to create application org.autojs.autojs.App: java.lang.IllegalStateException: db-downgrade P1.5 injected startup crash |
| am_crash | PASS | 2 |
| crashed too many times | PASS | am_process_crashed_too_much 1, log 1; Process org.autojs.autojs6 has crashed too many times, killing! Reason: crashed quickly |
| bad process | PASS | am_proc_bad 1 (at 11:24:09.880), listed at the end yes |
| starts stopped | PASS | am_proc_start 2 for 12 broadcasts (broadcast ProfileInstallReceiver 1, broadcast InrtBootCompletedReceiver 1), 0 after the bad mark, last at 11:24:09.136; broadcasts unfinished after 5 s 0; refusal lines 0: - |

## 11:26:29 background org.autojs.autojs6.inrt PASS

- 每 5 s 发一次显式广播 (roundtrip.py --start broadcast 同款), 共 60 s; App.onCreate 抛出 (注入)

| 项 | 结果 | 数值 |
| --- | --- | --- |
| bad mark before | PASS | none; cleared by a provider start |
| crashes | PASS | 2 of 2 start(s) crashed; java.lang.RuntimeException: Unable to create application org.autojs.autojs.App: java.lang.IllegalStateException: db-downgrade P1.5 injected startup crash |
| am_crash | PASS | 2 |
| crashed too many times | PASS | am_process_crashed_too_much 1, log 1; Process org.autojs.autojs6.inrt has crashed too many times, killing! Reason: crashed quickly |
| bad process | PASS | am_proc_bad 1 (at 11:25:25.058), listed at the end yes |
| starts stopped | PASS | am_proc_start 2 for 12 broadcasts (broadcast ProfileInstallReceiver 1, broadcast InrtBootCompletedReceiver 1), 0 after the bad mark, last at 11:25:24.164; broadcasts unfinished after 5 s 0; refusal lines 0: - |

## 说明 (P1.5, db-downgrade-q8-q9)

- 构建: HEAD `2f0663ba8` + P2.4 / P1.5 改动 + 临时崩溃注入 (`tools/db-downgrade/README.md` "崩溃移交" 一节, 只在隔离副本里, 不提交) 的 universal debug 包: 宿主 5935 (md5 `5eb2d90d`), inrt 5935 (md5 `3aef9e11`, 含 `CrashReportActivity`, 进程 `:crash_report`). 临时 AVD `dbdowngrade_q89_api35` (Android 15, google_apis x86_64), 已授予 `POST_NOTIFICATIONS`.
- 前台: 宿主由注入在 `MainActivity` 恢复 3 s 后抛出; inrt 是没有项目的模板, 在 `SplashActivity.onCreate` 先因 `ProjectConfig` 为 null 抛 NPE (这本身就是前台主线程崩溃), 注入来不及触发. 两者都是: `CrashHandler` 记 1 次, 报告页 2-6 s 内 resumed, `am_crash` 1, dropbox `data_app_crash` 新增 1 条, 崩溃的进程结束; 之后 WorkManager (`SystemJobService`) 或静态广播另拉起新进程 (没有再崩溃), 不计.
- 11:14:29 那节是距上次崩溃不到 2 分钟的重复崩溃: 系统记 "Showing crash dialog", 显示 "Application Error: org.autojs.autojs6" 对话框, 系统处理器在 `handleApplicationCrash` 里等对话框关闭; 脚本不点按, 对话框约 5 分钟后自行关闭, 进程随即结束 (264 s). 这是系统对重复前台崩溃的标准处理, 崩溃报告页同时在前台.
- 后台: 12 条广播 (每 5 s) 中, 第 1 条拉起后 `App.onCreate` 崩溃, 随即由一条待投递的启动类广播 (`InrtBootCompletedReceiver`) 再拉起并崩溃; 第 2 次崩溃时系统记 "has crashed too many times, killing! Reason: crashed quickly" 与 `am_proc_bad`, 之后 10 条广播不再拉起进程 (API 35 静默跳过, 没有日志行). 宿主与 inrt 相同. bad 标记会被前台启动或 provider 调用清除 (inrt 那节开头即经 provider 清除了上一轮留下的标记), 所以 Shizuku 服务端重启时 provider 仍会拉起 1 次.
- 对照 (修前 `CrashHandler`, 同样的注入): `roundtrip-dbdowngrade_q89_api35-5935-20261024-crash-handover-before-fix.md`.
