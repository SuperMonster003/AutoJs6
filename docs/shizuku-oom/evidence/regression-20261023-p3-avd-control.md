# Shizuku 功能回归 (T6, P2.1): 20261023 (p3-avd-control)

脚本: `tools/shizuku-oom/regression.py`, 各项定义见 `docs/shizuku-oom/ROADMAP.md` T6 与脚本说明. 每台设备的明细在同名 JSON.

- Android_SDK_built_for_x86_64 (emulator-5656): Android 9 / API 28, 宿主 6.8.0 (5935), Shizuku 13.5.4.r1049.0e53409 服务端身份 shell, 2026-10-23 09:51:21 - 2026-10-23 09:51:32, 宿主无障碍 False -> False
- 对照预置 (手工, 不由脚本完成): 运行前经宿主脚本把 `MiscStateKeys.ROOT_AUTOMATOR_TOUCH_DEVICE` 由 -1 设为 1 (`/dev/input/event1`, qwerty2 多点触控), 运行后还原为 -1. 不预置的同机运行见 `regression-20261023-p3-avd.md`, 根因见 `ROADMAP.md` 附录 B.4.

| 项 | Android_SDK_built_for_x86_64 (shell) |
| --- | --- |
| (1) | - |
| (2) | - |
| (3) | - |
| (4) | - |
| (5) | - |
| (6) | - |
| (7) | - |
| (8) | **PASS**: RootAutomator ok (Shizuku backend, sendSync + exit); pointer location ok (on 1, off 0); launcher icon command ok (code 0, empty state map); AndroidIdReader ok (equals the adb value); app Shizuku branches ok (startActivity top com.android.settings, kill True, process gone True) |
| (9) | - |
| (10) | - |
| (11) | - |
| 结束后残留 | **PASS**: user service 0, stray 0 |

结论: **PASS** (Android_SDK_built_for_x86_64 (shell) PASS)
