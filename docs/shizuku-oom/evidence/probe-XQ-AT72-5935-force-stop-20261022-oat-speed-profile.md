# Shizuku 用户服务残留探针: XQ-AT72 / 6.8.0 (5935) / force-stop

- 设备: XQ-AT72 (QV710AF65F), Android 12 / API 31
- 宿主: 6.8.0 versionCode 5935, debuggable, Shizuku 授权 True
- Shizuku: 13.5.4.r1049.0e53409, 服务端身份 shell
- 模式 force-stop, 轮数 3, 结束宿主后等待 3.0 s, 开始前残留 0
- 时间 2026-10-22 23:16:01 - 2026-10-22 23:16:25; 宿主无障碍服务开启 False -> False
- 探针: `tools/shizuku-oom/zombie_probe.py` (ROADMAP P0.1); 残留 = 结束宿主后仍在运行的用户服务进程数, RSS 合计为这些进程的 RSS 之和; 备注里的 stray = 本轮新出现且被 init 收养的 sh / getevent / sleep 及其后代 (服务死亡时仍在运行的命令), 只报告, 不参与判定
- 画像 (第 1 轮服务进程, 结束宿主前, 身份 shell, 单位 MB): RSS 143.6, PSS 49.6, Java 堆 20.3, native 3.6, code 5.8 (RSS 115.9), .dex mmap PSS 10.2 / 私有脏页 0.4, 线程 18, 解压到内存的 dex 0 个映射 0.0, 该进程的 App attachBaseContext / onCreate 日志 0 行 (启动器日志在缓冲区内)

| 轮次 | 残留 | RSS 合计 MB | 退出来源 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | 0 | 0.0 | destroy | stray 2 getevent/sh 5.0 MB killed 2 |
| 2 | 0 | 0.0 | destroy | stray 2 getevent/sh 4.9 MB killed 2 |
| 3 | 0 | 0.0 | destroy | stray 2 getevent/sh 4.9 MB killed 2 |

结论: **PASS** (residue in 0/3 rounds; orphaned exec processes in 3/3 rounds)
