# Shizuku 用户服务残留探针: XQ-AT72 / 6.8.0 (5935) / spawn-orphan

- 设备: XQ-AT72 (QV710AF65F), Android 12 / API 31
- 宿主: 6.8.0 versionCode 5935, debuggable, Shizuku 授权 True
- Shizuku: 13.5.4.r1049.0e53409, 服务端身份 shell
- 模式 spawn-orphan, 轮数 5, 结束宿主后等待 3.0 s, 开始前残留 0
- 时间 2026-10-22 23:12:06 - 2026-10-22 23:12:59; 宿主无障碍服务开启 False -> False
- 探针: `tools/shizuku-oom/zombie_probe.py` (ROADMAP P0.1); 残留 = 结束宿主后仍在运行的用户服务进程数, RSS 合计为这些进程的 RSS 之和; 备注里的 stray = 本轮新出现且被 init 收养的 sh / getevent / sleep 及其后代 (服务死亡时仍在运行的命令), 只报告, 不参与判定

| 轮次 | 残留 | RSS 合计 MB | 退出来源 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | 0 | 0.0 | destroy | orphans 3 getevent/sh/sleep  sleep600 1  stray 3 getevent/sh/sleep 7.9 MB killed 3 |
| 2 | 0 | 0.0 | destroy | orphans 3 getevent/sh/sleep  sleep600 1  stray 3 getevent/sh/sleep 8.0 MB killed 3 |
| 3 | 0 | 0.0 | destroy | orphans 3 getevent/sh/sleep  sleep600 1  stray 3 getevent/sh/sleep 7.9 MB killed 3 |
| 4 | 0 | 0.0 | destroy | orphans 3 getevent/sh/sleep  sleep600 1  stray 3 getevent/sh/sleep 8.0 MB killed 3 |
| 5 | 0 | 0.0 | destroy | orphans 3 getevent/sh/sleep  sleep600 1  stray 3 getevent/sh/sleep 8.0 MB killed 3 |

结论: **FAIL** (service residue in 0/5 rounds, orphans in 5/5 rounds; orphaned exec processes in 5/5 rounds)
