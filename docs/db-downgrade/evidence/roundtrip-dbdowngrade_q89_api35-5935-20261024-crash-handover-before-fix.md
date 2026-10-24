# 崩溃移交: dbdowngrade_q89_api35 / 6.8.0 (5935)

- 设备: dbdowngrade_q89_api35 (emulator-5660), Android 15 / API 35
- 脚本: `tools/db-downgrade/crash_handover.py` (ROADMAP P1.5, D10); 构建带临时崩溃注入 (README, 不提交)
- 每节一次运行, 只记通过 / 失败与关键数值 (R4)

## 11:18:06 foreground org.autojs.autojs6 FAIL

- 桌面入口启动 (org.autojs.autojs6/org.autojs.autojs.launcher.AdaptiveIconAlias), 首个 Activity 恢复后 3 s 主线程抛出 (注入), 观察 45 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| crash logged | PASS | 1 process(es); java.lang.IllegalStateException: db-downgrade P1.5 injected foreground crash |
| crash report page | PASS | CrashReportActivity resumed after 6 s, :crash_report process running at the end |
| am_crash | FAIL | 0 |
| dropbox data_app_crash | FAIL | 0 new entries |
| crashed process | PASS | ended; main processes at the end 1 (started after the crash) |
| process starts | - | am_proc_start 2 (next-top-activity AdaptiveIconAlias 1, service SystemJobService 1) |

## 11:23:58 background org.autojs.autojs6 FAIL

- 每 5 s 发一次显式广播 (roundtrip.py --start broadcast 同款), 共 60 s; App.onCreate 抛出 (注入)

| 项 | 结果 | 数值 |
| --- | --- | --- |
| bad mark before | PASS | none |
| crashes | PASS | 5 of 5 start(s) crashed; java.lang.RuntimeException: Unable to create application org.autojs.autojs.App: java.lang.IllegalStateException: db-downgrade P1.5 injected startup crash |
| am_crash | FAIL | 0 |
| crashed too many times | FAIL | am_process_crashed_too_much 0, log 0; - |
| bad process | FAIL | am_proc_bad 0, listed at the end no |
| starts stopped | FAIL | am_proc_start 5 for 12 broadcasts (broadcast ProfileInstallReceiver 1, broadcast BootCompletedReceiver 1, broadcast InrtBootCompletedReceiver 1, broadcast AudioCompatibilityProbeReceiver 1, broadcast StaticBroadcastReceiver 1), 0 after the bad mark, last at 11:23:52.375; broadcasts unfinished after 5 s 12; refusal lines 0: - |

## 说明 (P1.5 对照, db-downgrade-q8-q9)

- 构建: HEAD `2f0663ba8` 的 `CrashHandler.kt` / `App.kt` (即 P1.5 之前) + 同一临时崩溃注入, 宿主 universal debug 5935 (md5 `4955419b`); 同一 AVD. 预期 FAIL, 用来对照修后报告 `roundtrip-dbdowngrade_q89_api35-5935-20261024-crash-handover.md`.
- 前台: 报告页照样弹出 (`CrashHandler` 递归 5 次后 `exitProcess(1)`), 但 `am_crash` 0, dropbox 0.
- 后台: 60 s 内 5 次启动全部崩溃, `am_crash` 0, 没有 "crashed too many times" 与 `am_proc_bad`; 12 条 `am broadcast` 都没在 5 s 内返回 (接收进程自行退出, 系统一再重投), 窗口末尾 (11:23:52) 仍在被拉起, 拉起方依次为 `ProfileInstallReceiver`, `BootCompletedReceiver`, `InrtBootCompletedReceiver`, `AudioCompatibilityProbeReceiver`, `StaticBroadcastReceiver`. 第一次尝试 (11:19) 因 `am broadcast` 不返回超时中止 (约 70 s 内 6 次启动), 脚本随后改为每条广播最多等一个间隔.
