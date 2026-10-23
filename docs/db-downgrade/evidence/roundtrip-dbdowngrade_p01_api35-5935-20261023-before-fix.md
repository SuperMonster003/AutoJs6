# 数据库降级往返: dbdowngrade_p01_api35 / 6.8.0 (5935)

- 设备: dbdowngrade_p01_api35 (emulator-5640), Android 15 / API 35
- 脚本: `tools/db-downgrade/roundtrip.py` (ROADMAP P0.3, T6); 代码版本 TimedTask 5 / IntentTask 4
- 每节一次运行, 只记通过 / 失败与关键数值 (R4)

## 18:49:31 make-future org.autojs.autojs6 PASS

| 项 | 结果 | 数值 |
| --- | --- | --- |
| databases | - | created by a first start |
| TimedTaskDatabase.db | PASS | user_version 5 -> 6, rows 3 (markers 3), future column True, future table True |
| IntentTaskDatabase.db | PASS | user_version 4 -> 5, rows 2 (markers 2), future column True, future table True |

## 18:50:41 check org.autojs.autojs6 FAIL

- 启动方式: launcher (monkey), 观察 60.0 s 后强停

| 项 | 结果 | 数值 |
| --- | --- | --- |
| process | FAIL | not running at the end |
| crashes | FAIL | am_crash 0 (downgrade 0), logged 8 (downgrade 8), crashed too much 0 |
| process starts | FAIL | am_proc_start 8 (restarts 7; broadcast 7, next-top-activity 1), 10-23 18:49:38 - 10-23 18:50:09 |
| TimedTaskDatabase.db | FAIL | user_version 6 (code 5), rows 3 (markers 3/3), future column True, future table True |
| IntentTaskDatabase.db | FAIL | user_version 5 (code 4), rows 2 (markers 2/2), future column True, future table True |

