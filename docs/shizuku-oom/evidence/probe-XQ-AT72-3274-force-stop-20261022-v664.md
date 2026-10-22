# Shizuku 用户服务残留探针: XQ-AT72 / 6.6.4 (3274) / force-stop

- 设备: XQ-AT72 (QV710AF65F), Android 12 / API 31
- 宿主: 6.6.4 versionCode 3274, release, Shizuku 授权 True
- Shizuku: 13.5.4.r1049.0e53409, 服务端身份 shell
- 模式 force-stop, 轮数 10, 结束宿主后等待 3.0 s, 开始前残留 0, 预期 accumulate
- 时间 2026-10-22 23:17:02 - 2026-10-22 23:25:31; 宿主无障碍服务开启 False -> False
- 探针: `tools/shizuku-oom/zombie_probe.py` (ROADMAP P0.1); 残留 = 结束宿主后仍在运行的用户服务进程数, RSS 合计为这些进程的 RSS 之和; 备注里的 stray = 本轮新出现且被 init 收养的 sh / getevent / sleep 及其后代 (服务死亡时仍在运行的命令), 只报告, 不参与判定
- 画像 (第 1 轮服务进程, 结束宿主前, 身份 shell, 单位 MB): RSS 130.1, PSS 39.5, Java 堆 20.1, native 3.5, code 4.9 (RSS 102.9), .dex mmap PSS 2.1 / 私有脏页 0.4, 线程 16, 解压到内存的 dex 0 个映射 0.0, 该进程的 App attachBaseContext / onCreate 日志 0 行 (启动器日志在缓冲区内)

| 轮次 | 残留 | RSS 合计 MB | 退出来源 | 备注 |
| --- | --- | --- | --- | --- |
| 1 | 2 | 251.4 | alive |  |
| 2 | 5 | 629.0 | alive |  |
| 3 | 8 | 1006.3 | alive |  |
| 4 | 9 | 1132.0 | alive |  |
| 5 | 12 | 1507.8 | alive |  |
| 6 | 15 | 1885.5 | alive |  |
| 7 | 18 | 2263.6 | alive |  |
| 8 | 21 | 2640.5 | alive |  |
| 9 | 22 | 2766.4 | alive |  |
| 10 | 25 | 3144.3 | alive |  |

结论: **PASS** (residue grew in 10/10 rounds (expect accumulate))
