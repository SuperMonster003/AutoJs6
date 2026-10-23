# 数据库降级往返: dbdowngrade_p01_api35 / 6.8.0 (5835)

- 设备: dbdowngrade_p01_api35 (emulator-5640), Android 15 / API 35
- 脚本: `tools/db-downgrade/roundtrip.py` (ROADMAP P0.3, T6); 代码版本 TimedTask 5 / IntentTask 4
- 每节一次运行, 只记通过 / 失败与关键数值 (R4)

## 18:51:06 make-future org.autojs.autojs6.inrt PASS

| 项 | 结果 | 数值 |
| --- | --- | --- |
| databases | - | created by a first start |
| TimedTaskDatabase.db | PASS | user_version 5 -> 6, rows 3 (markers 3), future column True, future table True |
| IntentTaskDatabase.db | PASS | user_version 4 -> 5, rows 2 (markers 2), future column True, future table True |

## 18:52:10 check org.autojs.autojs6.inrt FAIL

- 启动方式: launcher (monkey), 观察 60.0 s 后强停

| 项 | 结果 | 数值 |
| --- | --- | --- |
| process | FAIL | not running at the end |
| crashes | FAIL | am_crash 0 (downgrade 0), logged 8 (downgrade 8), crashed too much 0 |
| process starts | FAIL | am_proc_start 8 (restarts 7; broadcast InrtBootCompletedReceiver 2, next-top-activity SplashActivity 1, broadcast AudioCompatibilityProbeReceiver 1, broadcast StaticBroadcastReceiver 1, broadcast BootCompletedReceiver 1, broadcast RescheduleReceiver 1, broadcast JobBootReceiver 1), 10-23 18:51:07 - 10-23 18:51:22 |
| TimedTaskDatabase.db | FAIL | user_version 6 (code 5), rows 3 (markers 3/3), future column True, future table True |
| IntentTaskDatabase.db | FAIL | user_version 5 (code 4), rows 2 (markers 2/2), future column True, future table True |

## 19:04:09 wake org.autojs.autojs6.inrt FAIL

- Shizuku 13.6.0.r1086.2650830c, 服务端经 `libshizuku.so` 重启, 每轮观察 90.0 s 后强停 (每次启动到退出 5-15 s, 一轮约 60 s 才平息, 先前一次 30 s 窗口的运行不够覆盖, 已弃用)
- 合计: org.autojs.autojs6.inrt am_proc_start 18, 崩溃 (am_crash + logged) 18 (3 轮); org.autojs.autojs6 am_proc_start 23, 崩溃 (am_crash + logged) 22 (3 轮)

| 项 | 结果 | 数值 |
| --- | --- | --- |
| round 1 org.autojs.autojs6.inrt | FAIL | server restarted True, am_proc_start 9 (broadcast InrtBootCompletedReceiver 2, content provider ShizukuProvider 1, broadcast AudioCompatibilityProbeReceiver 1, broadcast StaticBroadcastReceiver 1, broadcast BootCompletedReceiver 1, broadcast RescheduleReceiver 1, broadcast JobBootReceiver 1, service SystemJobService 1), am_crash 0 (downgrade 0), logged 9 (downgrade 9), crashed too much 0, alive at 90.0 s False |
| round 1 org.autojs.autojs6 | FAIL | server restarted True, am_proc_start 7 (broadcast InrtBootCompletedReceiver 2, content provider ShizukuProvider 1, broadcast AudioCompatibilityProbeReceiver 1, broadcast StaticBroadcastReceiver 1, broadcast BootCompletedReceiver 1, broadcast RescheduleReceiver 1), am_crash 0 (downgrade 0), logged 6 (downgrade 6), crashed too much 0, alive at 90.0 s True |
| round 2 org.autojs.autojs6.inrt | FAIL | server restarted True, am_proc_start 1 (content provider ShizukuProvider 1), am_crash 0 (downgrade 0), logged 1 (downgrade 1), crashed too much 0, alive at 90.0 s False |
| round 2 org.autojs.autojs6 | FAIL | server restarted True, am_proc_start 8 (broadcast InrtBootCompletedReceiver 2, content provider ShizukuProvider 1, broadcast AudioCompatibilityProbeReceiver 1, broadcast StaticBroadcastReceiver 1, broadcast BootCompletedReceiver 1, broadcast RescheduleReceiver 1, broadcast JobBootReceiver 1), am_crash 0 (downgrade 0), logged 8 (downgrade 8), crashed too much 0, alive at 90.0 s False |
| round 3 org.autojs.autojs6.inrt | FAIL | server restarted True, am_proc_start 8 (broadcast InrtBootCompletedReceiver 2, content provider ShizukuProvider 1, broadcast AudioCompatibilityProbeReceiver 1, broadcast StaticBroadcastReceiver 1, broadcast BootCompletedReceiver 1, broadcast RescheduleReceiver 1, broadcast JobBootReceiver 1), am_crash 0 (downgrade 0), logged 8 (downgrade 8), crashed too much 0, alive at 90.0 s False |
| round 3 org.autojs.autojs6 | FAIL | server restarted True, am_proc_start 8 (broadcast InrtBootCompletedReceiver 2, content provider ShizukuProvider 1, broadcast AudioCompatibilityProbeReceiver 1, broadcast StaticBroadcastReceiver 1, broadcast BootCompletedReceiver 1, broadcast RescheduleReceiver 1, broadcast JobBootReceiver 1), am_crash 0 (downgrade 0), logged 8 (downgrade 8), crashed too much 0, alive at 90.0 s False |

