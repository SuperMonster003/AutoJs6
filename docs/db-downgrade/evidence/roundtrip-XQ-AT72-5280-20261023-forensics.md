# 数据库降级往返: XQ-AT72 / 6.8.0 (5280)

- 设备: XQ-AT72 (QV710AF65F), Android 12 / API 31
- 脚本: `tools/db-downgrade/roundtrip.py` (ROADMAP P0.3, T6); 代码版本 TimedTask 5 / IntentTask 4
- 每节一次运行, 只记通过 / 失败与关键数值 (R4)

## 18:45:57 forensics org.autojs.autojs6.inrt INFO

- 只读取证: dumpsys package / dropbox / activity processes / jobscheduler / alarm, logcat -b events; 未装包, 未改设置

| 项 | 结果 | 数值 |
| --- | --- | --- |
| package | - | versionCode 5280, versionName 6.8.0, installer -, flags [DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA LARGE_HEAP] |
| install times | - | first 2026-09-14 18:07:47, last update 2026-10-22 17:33:09 |
| provider authorities | - | org.autojs.autojs6.inrt.androidx-startup, org.autojs.autojs6.inrt.com.twofortyfouram.spackle.internal.default, org.autojs.autojs6.inrt.deviceid.provider, org.autojs.autojs6.inrt.explorer_action_output, org.autojs.autojs6.inrt.fileprovider, org.autojs.autojs6.inrt.plugin.settings, org.autojs.autojs6.inrt.shizuku |
| resolver entries | - | receiver 47, service 5 |
| dropbox data_app_crash | - | no entry for the package (rotated or never written) |
| dropbox window | - | 75 entries of all tags, oldest 2026-10-20 15:34:59 |
| bad processes | - | none listed |
| crash records | - | none |
| process now | - | 0 ProcessRecord lines |
| jobs | - | 0 registered |
| alarms | - | 0 pending |
| event log | - | buffer from 10-23 16:11:13; am_proc_start 0 (-), am_crash 0 (downgrade 0), crashed too much 0, died 0 |
| running now | - | no |

