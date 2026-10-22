# Shizuku 功能回归 (T6, P2.1): 20261023 (p1)

脚本: `tools/shizuku-oom/regression.py`, 各项定义见 `docs/shizuku-oom/ROADMAP.md` T6 与脚本说明. 每台设备的明细在同名 JSON.

- G8441 (BH900ASK9E): Android 9 / API 28, 宿主 6.8.0 (5935), Shizuku 13.5.4.r1049.0e53409 服务端身份 root, 2026-10-23 01:20:44 - 2026-10-23 01:22:03, 宿主无障碍 False -> False
- XQ-AT72 (QV710AF65F): Android 12 / API 31, 宿主 6.8.0 (5935), Shizuku 13.5.4.r1049.0e53409 服务端身份 shell, 2026-10-23 06:37:22 - 2026-10-23 06:38:49, 宿主无障碍 False -> False

| 项 | G8441 (root) | XQ-AT72 (shell) |
| --- | --- | --- |
| (1) | **PASS**: shizuku() code 0 uid 0, execCommand code 0 uid 0 | **PASS**: shizuku() code 0 uid 2000, execCommand code 0 uid 2000 |
| (2) | **PASS**: pid reported, sleep 600 running 1 while the script ran, 0 left 3 s after it ended | **PASS**: pid reported, sleep 600 running 1 while the script ran, 0 left 3 s after it ended |
| (3) | **PASS**: adb code 0 uid 0, adb + pty code 0 uid 0 | **PASS**: adb code 0 uid 2000, adb + pty code 0 uid 2000 |
| (4) | **PASS**: 3/4 tests pass, 1 skipped (Window inspection requires API 29) | **PASS**: 4/4 tests pass |
| (5) | **PASS**: enabled before False, connected after 211 ms, disconnected after the list was written back in 4 ms | **PASS**: enabled before False, connected after 209 ms, disconnected after the list was written back in 2 ms |
| (6) | **PASS**: currentPackage / currentActivity match dumpsys (com.sonymobile.home/com.sonymobile.home.HomeActivity); 3/3 tests pass | **PASS**: currentPackage / currentActivity match dumpsys (com.sonymobile.launcher/com.sonymobile.launcher.XperiaLauncher); 3/3 tests pass |
| (7) | **PASS**: VOLUME_DOWN: adjust requests 1, streams changed 0, put back 0 | **PASS**: VOLUME_DOWN: adjust requests 1, streams changed 0, put back 0 |
| (8) | - | **PASS**: RootAutomator n/a (adb shell may write no /dev/input node here; the expected error); pointer location ok (on 1, off 0); launcher icon command ok (code 0, empty state map); AndroidIdReader ok (equals the adb value); app Shizuku branches ok (startActivity top com.android.settings, kill True, process gone True) |
| (9) | **PASS**: getevent under the service 1, stopped after 0.34 s (exit event), residue 0, exit host-died, stray 0, volumes put back 0 | **PASS**: getevent under the service 1, stopped after 0.66 s (exit event), residue 0, exit host-died, stray 0, volumes put back 0 |
| 结束后残留 | **PASS**: user service 0, stray 0 | **PASS**: user service 0, stray 0 |

结论: **PASS** (G8441 (root) PASS, XQ-AT72 (shell) PASS)
