# 数据库降级往返: XQ-AT72 / 6.8.0 (5835)

- 设备: XQ-AT72 (QV710AF65F), Android 12 / API 31
- 脚本: `tools/db-downgrade/roundtrip.py` (ROADMAP P0.3, T6); 代码版本 TimedTask 5 / IntentTask 4
- 每节一次运行, 只记通过 / 失败与关键数值 (R4)

## 18:45:59 forensics org.autojs.autojs6 INFO

- 只读取证: dumpsys package / dropbox / activity processes / jobscheduler / alarm, logcat -b events; 未装包, 未改设置

| 项 | 结果 | 数值 |
| --- | --- | --- |
| package | - | versionCode 5835, versionName 6.8.0, installer -, flags [DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA LARGE_HEAP] |
| install times | - | first 2026-09-01 19:15:53, last update 2026-10-23 17:06:52 |
| provider authorities | - | org.autojs.autojs6.androidx-startup, org.autojs.autojs6.com.twofortyfouram.spackle.internal.default, org.autojs.autojs6.context-probe, org.autojs.autojs6.deviceid.provider, org.autojs.autojs6.explorer_action_output, org.autojs.autojs6.fileprovider, org.autojs.autojs6.installer-process-probe, org.autojs.autojs6.leakcanary-installer, org.autojs.autojs6.plugin.settings, org.autojs.autojs6.plumber-installer, org.autojs.autojs6.privileged-editor, org.autojs.autojs6.privileged-view, org.autojs.autojs6.shizuku |
| resolver entries | - | receiver 49, service 5 |
| dropbox data_app_crash | - | 30 entries, 2026-10-22 23:17:13 - 2026-10-22 23:29:16, versionCode 3274 |
| dropbox root cause | - | 30 x android.database.sqlite.SQLiteException: Can't downgrade database from version 5 to 3 |
| dropbox top frame of the root cause | - | 30 x android.database.sqlite.SQLiteOpenHelper.onDowngrade(SQLiteOpenHelper.java:540) |
| dropbox entries per day | - | 2026-10-22 30 |
| dropbox window | - | 75 entries of all tags, oldest 2026-10-20 15:34:59 |
| bad processes | - | none listed |
| crash records | - | none |
| process now | - | 3 ProcessRecord lines, pids [26671] |
| jobs | - | 3 registered: androidx.work.impl.background.systemjob.SystemJobService |
| alarms | - | 0 pending |
| event log | - | buffer from 10-23 16:11:13; am_proc_start 2 (broadcast:NA/android.intent.action.MY_PACKAGE_REPLACED 2), am_crash 0 (downgrade 0), crashed too much 0, died 0, 10-23 16:58:17 - 10-23 17:06:52 |
| running now | - | yes |

