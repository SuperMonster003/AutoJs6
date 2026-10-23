# 数据库降级往返: dbdowngrade_p01_api24 / 6.8.0 (5935)

- 设备: dbdowngrade_p01_api24 (emulator-5644), Android 7.0 / API 24
- 脚本: `tools/db-downgrade/roundtrip.py` (ROADMAP P0.3, T6); 代码版本 TimedTask 5 / IntentTask 4
- 每节一次运行, 只记通过 / 失败与关键数值 (R4)

## 19:50:28 make-future org.autojs.autojs6 PASS

| 项 | 结果 | 数值 |
| --- | --- | --- |
| databases | - | present |
| TimedTaskDatabase.db | PASS | user_version 5 -> 6, rows 3 (markers 3), future column True, future table True |
| IntentTaskDatabase.db | PASS | user_version 4 -> 5, rows 2 (markers 2), future column True, future table True |

## 19:51:01 check org.autojs.autojs6 PASS

- 启动方式: 桌面入口 (org.autojs.autojs6/org.autojs.autojs.launcher.AdaptiveIconAlias), 观察 30.0 s 后强停

| 项 | 结果 | 数值 |
| --- | --- | --- |
| process | PASS | alive |
| crashes | PASS | am_crash 0 (downgrade 0), logged 0 (downgrade 0), crashed too much 0 |
| process starts | PASS | am_proc_start 1 (restarts 0; activity AdaptiveIconAlias 1), 10-23 19:50:29 - 10-23 19:50:29 |
| TimedTaskDatabase.db | PASS | user_version 5 (code 5), rows 3 (markers 3/3), future column True, future table True |
| IntentTaskDatabase.db | PASS | user_version 4 (code 4), rows 2 (markers 2/2), future column True, future table True |

## 19:51:38 inject-fail org.autojs.autojs6 PASS

- 注入: databases/<库文件> 换成同名目录, 观察 30.0 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| TimedTaskDatabase.db injected | PASS | databases/TimedTaskDatabase.db is a dir |
| IntentTaskDatabase.db injected | PASS | databases/IntentTaskDatabase.db is a dir |
| process | PASS | alive |
| crashes | PASS | am_crash 0 (downgrade 0), logged 0 (downgrade 0), crashed too much 0, process starts 1 |
| recovery log | PASS | DatabaseRecovery W lines 79 |
| TimedTaskDatabase.db recovered | PASS | new backups TimedTaskDatabase.db.bak-0-20261023195104; new database user_version 5, rows 0 |
| IntentTaskDatabase.db recovered | PASS | new backups IntentTaskDatabase.db.bak-0-20261023195104; new database user_version 4, rows 0 |
| notification | PASS | tags db-recovery:IntentTaskDatabase, db-recovery:TimedTaskDatabase |
| kept files back | PASS | TimedTaskDatabase.db markers 3, IntentTaskDatabase.db markers 2 |

## 19:53:53 restore-check org.autojs.autojs6 PASS

- APK: host-x86_64.apk; 每步观察 30.0 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| (a) no reinstall | PASS | alive True, am_crash 0; TimedTaskDatabase.db backup kept True, markers 0, IntentTaskDatabase.db backup kept True, markers 0 |
| (b) reinstalled, version satisfied | PASS | alive True, am_crash 0; TimedTaskDatabase.db backups left 0, markers 3/3, user_version 5, IntentTaskDatabase.db backups left 0, markers 2/2, user_version 4 |
| (c) reinstalled, backup newer than code | PASS | alive True, am_crash 0; TimedTaskDatabase.db backup kept True, markers 0, IntentTaskDatabase.db backup kept True, markers 0 |
| data back | PASS | TimedTaskDatabase.db markers 3, IntentTaskDatabase.db markers 2 |
## 20:06:59 make-future org.autojs.autojs6.inrt PASS

| 项 | 结果 | 数值 |
| --- | --- | --- |
| databases | - | present |
| TimedTaskDatabase.db | PASS | user_version 5 -> 6, rows 3 (markers 3), future column True, future table True |
| IntentTaskDatabase.db | PASS | user_version 4 -> 5, rows 2 (markers 2), future column True, future table True |

## 20:07:33 check org.autojs.autojs6.inrt PASS

- 启动方式: 显式广播 (ProfileInstallReceiver, 只拉起进程), 观察 30.0 s 后强停

| 项 | 结果 | 数值 |
| --- | --- | --- |
| process | PASS | alive |
| crashes | PASS | am_crash 0 (downgrade 0), logged 0 (downgrade 0), crashed too much 0 |
| process starts | PASS | am_proc_start 1 (restarts 0; broadcast ProfileInstallReceiver 1), 10-23 20:07:00 - 10-23 20:07:00 |
| TimedTaskDatabase.db | PASS | user_version 5 (code 5), rows 3 (markers 3/3), future column True, future table True |
| IntentTaskDatabase.db | PASS | user_version 4 (code 4), rows 2 (markers 2/2), future column True, future table True |

## 20:08:10 inject-fail org.autojs.autojs6.inrt PASS

- 注入: databases/<库文件> 换成同名目录; 启动方式: 显式广播 (ProfileInstallReceiver, 只拉起进程), 观察 30.0 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| TimedTaskDatabase.db injected | PASS | databases/TimedTaskDatabase.db is a dir |
| IntentTaskDatabase.db injected | PASS | databases/IntentTaskDatabase.db is a dir |
| process | PASS | alive |
| crashes | PASS | am_crash 0 (downgrade 0), logged 0 (downgrade 0), crashed too much 0, process starts 1 |
| recovery log | PASS | DatabaseRecovery W lines 79 |
| TimedTaskDatabase.db recovered | PASS | new backups TimedTaskDatabase.db.bak-0-20261023200735; new database user_version 5, rows 0 |
| IntentTaskDatabase.db recovered | PASS | new backups IntentTaskDatabase.db.bak-0-20261023200735; new database user_version 4, rows 0 |
| kept files back | PASS | TimedTaskDatabase.db markers 3, IntentTaskDatabase.db markers 2 |

## 20:10:21 restore-check org.autojs.autojs6.inrt PASS

- APK: inrt-universal.apk; 启动方式: 显式广播 (ProfileInstallReceiver, 只拉起进程), 每步观察 30.0 s

| 项 | 结果 | 数值 |
| --- | --- | --- |
| (a) no reinstall | PASS | alive True, am_crash 0; TimedTaskDatabase.db backup kept True, markers 0, IntentTaskDatabase.db backup kept True, markers 0 |
| (b) reinstalled, version satisfied | PASS | alive True, am_crash 0; TimedTaskDatabase.db backups left 0, markers 3/3, user_version 5, IntentTaskDatabase.db backups left 0, markers 2/2, user_version 4 |
| (c) reinstalled, backup newer than code | PASS | alive True, am_crash 0; TimedTaskDatabase.db backup kept True, markers 0, IntentTaskDatabase.db backup kept True, markers 0 |
| data back | PASS | TimedTaskDatabase.db markers 3, IntentTaskDatabase.db markers 2 |

