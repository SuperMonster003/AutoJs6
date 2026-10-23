# Shizuku 功能回归 (T6, P2.1): 20261023 (p3-6)

脚本: `tools/shizuku-oom/regression.py`, 各项定义见 `docs/shizuku-oom/ROADMAP.md` T6 与脚本说明. 每台设备的明细在同名 JSON.

- XQ-AT72 (QV710AF65F): Android 12 / API 31, 宿主 6.8.0 (5935), Shizuku 13.5.4.r1049.0e53409 服务端身份 shell, 2026-10-23 13:30:33 - 2026-10-23 13:31:49, 宿主无障碍 False -> False

| 项 | XQ-AT72 (shell) |
| --- | --- |
| (1) | - |
| (2) | - |
| (3) | - |
| (4) | - |
| (5) | - |
| (6) | - |
| (7) | - |
| (8) | - |
| (9) | **PASS**: getevent under the service 1, stopped after 0.63 s (exit event), residue 0, exit host-died, stray 0, volumes put back 0 |
| (10) | **PASS**: getevent while off 0, after turning on 1 in 3.5 s (observer created then True), volume up stopped the script True (0.62 s, uhid), after turning off 0 in 0.1 s, settings [True, True] put back |
| (11) | **PASS**: baseline 1, recording 0: 2 (0.2 s), stopped 0: 1 (0.1 s), recording 1: 2 (0.2 s), stopped 1: 1 (0.2 s), main thread per step up to 110 ms |
| (12) | **PASS**: cold start, user service connected True at the start / True after the main thread steps, main thread per step [63, 32] ms (limit 1000), then off the main thread node event4 in 592 ms, cached 4, cache 4 put back |
| 结束后残留 | **PASS**: user service 0, stray 0 |

结论: **PASS** (XQ-AT72 (shell) PASS)
