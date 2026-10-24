# Room 三库降级页面核对 (dbdowngrade_q89_api35, versionCode 5935)

由 `tools/db-downgrade-room/room_pages.py` 生成 (docs/db-downgrade/ROADMAP.md P2.3). 只含通过 / 失败与关键数值.

## 2026-10-24 10:49 emulator-5660, API 35, org.autojs.autojs6: FAIL

| 项 | 结果 | 数值 |
| --- | --- | --- |
| future files written | PASS | history 4 -> 3, color 3 -> 2, keystore 2 -> 1; 3 existing file(s) renamed aside |
| host start from launcher | PASS | alive after 10 s |
| instrumentation page1Drawer | PASS | PASS |
| instrumentation page2VersionHistory | PASS | PASS |
| instrumentation page3Trash | FAIL | FAIL (java.lang.AssertionError: TrashActivity is not resumed after 4000 ms: []) |
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
| databases sent to recovery | PASS | 0 database(s) |
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

## 2026-10-24 10:51 emulator-5660, API 35, org.autojs.autojs6: PASS

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
| databases sent to recovery | PASS | 0 database(s) |
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

## 说明 (P2.4, db-downgrade-q8-q9)

- 构建: HEAD `2f0663ba8` + P2.4 / P1.5 改动的隔离副本 (不含崩溃注入), 宿主 x86_64 debug 5935 与测试包 `app-app-debug-androidTest.apk`; 临时 AVD `dbdowngrade_q89_api35` (Android 15, google_apis x86_64).
- 10:49 那次的唯一失败 `page3Trash`: 回收站页 10:49:51.114 已 RESUMED, 0.47 s 后被 LeakCanary 的 `leakcanary.internal.RequestPermissionActivity` (debug 包申请通知权限) 盖住而 PAUSED, 4 s 判定时列表为空; 无崩溃, 与降级无关. `pm grant org.autojs.autojs6 android.permission.POST_NOTIFICATIONS` 后 10:51 复跑 28 / 28.
- 页面核对的未来文件都含必需列, 所以 `databases sent to recovery` 应为 0 (此项取代 P2.3 时的 `tables kept aside`, 因 D9 已删除改名保留).

## androidTest (同一 AVD, 10:48)

`am instrument -w -r -e class org.autojs.autojs.storage.database.RoomDowngradeTest,org.autojs.autojs.storage.database.TaskDatabaseRecoveryTest,org.autojs.autojs.storage.history.HistoryDatabaseMigrationTest,org.autojs.autojs.theme.app.ColorHistoryDatabaseMigrationTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner`: OK (20 tests).

| 类 | 用例数 | 结果 |
| --- | --- | --- |
| `RoomDowngradeTest` | 5 | PASS (含改写后的 `tableMissingRequiredColumnSendsWholeFileToRecovery`) |
| `TaskDatabaseRecoveryTest` | 11 | PASS |
| `HistoryDatabaseMigrationTest` | 2 | PASS |
| `ColorHistoryDatabaseMigrationTest` | 2 | PASS |

`tableMissingRequiredColumnSendsWholeFileToRecovery`: 迁移抛出 `IllegalStateException: KeyStore (file version 2) lacks required column(s) alias_password, its rows cannot be kept`, `DatabaseRecovery` 记 `room-downgrade-test-keystore: cannot open, kept as room-downgrade-test-keystore.bak-2-<时间>`; 备份为 v2 且原行, `future_table` 与 identity hash 原样; 新库 v1 可写, 不含 `_downgrade_*` 表; 再次打开不还原 (备份比代码新) 也不再备份.
