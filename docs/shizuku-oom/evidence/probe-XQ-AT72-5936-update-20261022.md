# Shizuku 用户服务残留探针: XQ-AT72 / 6.8.0 (5936) / update

- 设备: XQ-AT72 (QV710AF65F), Android 12 / API 31
- 宿主: 6.8.0 versionCode 5936, debuggable, Shizuku 授权 True
- Shizuku: 13.5.4.r1049.0e53409, 服务端身份 shell
- 模式 update, 轮数 3, 结束宿主后等待 3.0 s, 开始前残留 0
- 时间 2026-10-22 23:13:17 - 2026-10-22 23:14:26; 宿主无障碍服务开启 False -> False
- 探针: `tools/shizuku-oom/zombie_probe.py` (ROADMAP P0.1); 残留 = 结束宿主后仍在运行的用户服务进程数, RSS 合计为这些进程的 RSS 之和; 备注里的 stray = 本轮新出现且被 init 收养的 sh / getevent / sleep 及其后代 (服务死亡时仍在运行的命令), 只报告, 不参与判定

| 轮次 | 残留 | RSS 合计 MB | 退出来源 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | 0 | 0.0 | destroy | after-update 0 after-stop 0  stray 4 getevent/sh 9.8 MB killed 4 |
| 2 | 0 | 0.0 | destroy | after-update 0 after-stop 0  stray 4 getevent/sh 10.0 MB killed 4 |
| 3 | 0 | 0.0 | destroy | after-update 0 after-stop 0  stray 4 getevent/sh 9.9 MB killed 4 |

结论: **PASS** (residue in 0/3 rounds; orphaned exec processes in 3/3 rounds)
