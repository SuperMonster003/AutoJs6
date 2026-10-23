# Shizuku 功能回归 (T6, P2.1): 20261023 (p3)

脚本: `tools/shizuku-oom/regression.py`, 各项定义见 `docs/shizuku-oom/ROADMAP.md` T6 与脚本说明. 每台设备的明细在同名 JSON.

- Android_SDK_built_for_x86_64 (emulator-5656): Android 9 / API 28, 宿主 6.8.0 (5935), Shizuku 13.5.4.r1049.0e53409 服务端身份 shell, 2026-10-23 10:13:21 - 2026-10-23 10:14:02, 宿主无障碍 False -> False
- XQ-AT72 (QV710AF65F): Android 12 / API 31, 宿主 6.8.0 (5935), Shizuku 13.5.4.r1049.0e53409 服务端身份 shell, 2026-10-23 10:30:16 - 2026-10-23 10:31:18, 宿主无障碍 False -> False

| 项 | Android_SDK_built_for_x86_64 (shell) | XQ-AT72 (shell) |
| --- | --- | --- |
| (1) | - | - |
| (2) | - | - |
| (3) | - | - |
| (4) | - | - |
| (5) | - | - |
| (6) | - | - |
| (7) | - | - |
| (8) | - | - |
| (9) | **PASS**: getevent under the service 1, stopped after 0.27 s (exit event), residue 0, exit host-died, stray 0, volumes put back 0 | **PASS**: getevent under the service 1, stopped after 0.63 s (exit event), residue 0, exit host-died, stray 0, volumes put back 0 |
| (10) | **PASS**: getevent while off 0, after turning on 1 in 0.1 s (observer created then True), volume up stopped the script True (0.37 s, sendevent), after turning off 0 in 0.1 s, settings [True, True] put back | **PASS**: getevent while off 0, after turning on 1 in 2.0 s (observer created then True), volume up stopped the script True (0.62 s, uhid), after turning off 0 in 0.1 s, settings [True, True] put back |
| (11) | **PASS**: baseline 1, recording 0: 2 (0.1 s), stopped 0: 1 (0.1 s), recording 1: 2 (0.1 s), stopped 1: 1 (0.1 s), main thread per step up to 267 ms | **PASS**: baseline 1, recording 0: 2 (0.2 s), stopped 0: 1 (0.1 s), recording 1: 2 (0.2 s), stopped 1: 1 (0.2 s), main thread per step up to 529 ms |
| 结束后残留 | **PASS**: user service 0, stray 0 | **PASS**: user service 0, stray 0 |

结论: **PASS** (Android_SDK_built_for_x86_64 (shell) PASS, XQ-AT72 (shell) PASS)
