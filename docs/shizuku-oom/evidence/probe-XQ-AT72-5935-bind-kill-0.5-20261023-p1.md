# Shizuku 用户服务残留探针: XQ-AT72 / 6.8.0 (5935) / bind-kill:0.5

- 设备: XQ-AT72 (QV710AF65F), Android 12 / API 31
- 宿主: 6.8.0 versionCode 5935, debuggable, Shizuku 授权 True
- Shizuku: 13.5.4.r1049.0e53409, 服务端身份 shell
- 模式 bind-kill:0.5, 轮数 5, 结束宿主后等待 3.0 s, 开始前残留 0
- 时间 2026-10-23 06:25:11 - 2026-10-23 06:25:45; 宿主无障碍服务开启 False -> False
- 探针: `tools/shizuku-oom/zombie_probe.py` (ROADMAP P0.1); 残留 = 结束宿主后仍在运行的用户服务进程数, RSS 合计为这些进程的 RSS 之和; 备注里的 stray = 本轮新出现且被 init 收养的 sh / getevent / sleep 及其后代 (服务死亡时仍在运行的命令), 参与判定 (--stray-fails 或 volume-key 模式)

| 轮次 | 残留 | RSS 合计 MB | 退出来源 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | 0 | 0.0 | exited | connected no |
| 2 | 0 | 0.0 | exited | connected no |
| 3 | 0 | 0.0 | exited | connected no |
| 4 | 0 | 0.0 | exited | connected no |
| 5 | 0 | 0.0 | exited | connected no |

结论: **PASS** (residue in 0/5 rounds)
