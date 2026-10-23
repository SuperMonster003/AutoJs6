# Room 三库降级页面核对 (dbdowngrade_api35, versionCode 5935)

由 `tools/db-downgrade-room/room_pages.py` 生成 (docs/db-downgrade/ROADMAP.md P2.3). 只含通过 / 失败与关键数值.

## 2026-10-23 19:01 emulator-5620, API 35, org.autojs.autojs6: PASS

| 项 | 结果 | 数值 |
| --- | --- | --- |
| future files written | PASS | history 4 -> 3, color 3 -> 2, keystore 2 -> 1; 9 existing file(s) renamed aside |
| host start from launcher | PASS | alive after 10 s |
| instrumentation page1Drawer | PASS | PASS |
| instrumentation page2VersionHistory | PASS | PASS |
| instrumentation page3Trash | PASS | PASS |
| instrumentation page4ColorLibraries | PASS | PASS |
| instrumentation page5ColorSelect | PASS | PASS |
| instrumentation page6KeyStore | PASS | PASS |
| instrumentation worker1HistoryCleanup | PASS | PASS |
| instrumentation process | PASS | no crash |
| crashes in logcat | PASS | FATAL EXCEPTION 0, CrashHandler uncaught 0, am_crash 0 |
| Room open errors | PASS | 0 line(s) |
| history rebuild | PASS | 1 rebuild(s) logged |
| color rebuild | PASS | 1 rebuild(s) logged |
| keystore rebuild | PASS | 1 rebuild(s) logged |
| tables kept aside | PASS | 0 table(s) |
| history version | PASS | user_version 3 (code 3), identity hash matches |
| history marker rows | PASS | file_entry 1/1, revision 2/2, trash_item 1/1 |
| history columns | PASS | revision: 12 column(s), future column dropped |
| history extra table | PASS | room_future_extra rows 1, rebuild leftovers 0 |
| color version | PASS | user_version 2 (code 2), identity hash matches |
| color marker rows | PASS | palette_history 1/1, color_library 1/1, color_library_color 1/1 |
| color columns | PASS | color_library: 9 column(s), future column dropped |
| color extra table | PASS | room_future_extra rows 1, rebuild leftovers 0 |
| keystore version | PASS | user_version 1 (code 1), identity hash matches |
| keystore marker rows | PASS | KeyStore 1/1 |
| keystore columns | PASS | KeyStore: 6 column(s), future column dropped |
| keystore extra table | PASS | room_future_extra rows 1, rebuild leftovers 0 |

