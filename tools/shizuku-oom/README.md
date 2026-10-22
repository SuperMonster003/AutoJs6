# Shizuku 用户服务残留探针

配合 `docs/shizuku-oom/ROADMAP.md` 使用 (issue #474). `zombie_probe.py` 在真机上反复启动并结束宿主, 统计结束后仍在运行的 Shizuku 用户服务进程 (`org.autojs.autojs6:shizuku-service-for-autojs6`). 脚本不点屏幕, 不改宿主设置; 只在 `update` 模式安装 APK. Shizuku 授权等对话框由维护者在手机上操作.

## 用法

```
py -3.12 -I tools/shizuku-oom/zombie_probe.py -s SERIAL --mode force-stop --rounds 20
```

前提: 宿主已安装并已获 Shizuku 授权 (`moe.shizuku.manager.permission.API_V23`), Shizuku 服务端在运行, 屏幕已解锁 (脚本开始前发送 `KEYCODE_WAKEUP`, 只点亮不点击). 设备临时文件放在 `/sdcard/Download/aj6-shizuku-oom/`, 结束时删除.

每轮:

1. 写入一个最小脚本 (`shizuku('id')`, 把 `code` 与 uid 写进标记文件, `exit()`), 用 `am start -n org.autojs.autojs6/org.autojs.autojs.external.open.RunIntentActivity -e path <脚本>` 启动. 这是 `tools/memory` 设备脚本 (`device_diagnostics_page.py` 等) 已验证的途径; `run_gate.py` 本身走 `am instrument`, 测试进程随宿主一起被强停, 不适合这里. v6.6.4 的 `ScriptIntents` 也读 `path` 参数.
2. 等新的用户服务进程出现与标记文件写入 (`--wait`, 默认 45 s).
3. 按 `--mode` 结束宿主, 等 `--settle` 秒 (默认 3, ROADMAP T2), 用 `ps -A -o PID,PPID,USER,RSS,NAME` 统计用户服务进程数与 RSS 合计.
4. 读本轮开始以来的 logcat, 只判定每个服务进程的退出来源: `destroy` (打出了 `UserService: destroy`, 即宿主连接死亡后 Shizuku 发来的销毁事务), `exited` (没有这一行就消失了, 例如服务端重启后启动器的 `System.exit`), `alive`.
5. 记录本轮新出现, 已被 init 收养的 `sh` / `getevent` / `sleep` 及其后代 (报告备注里的 `stray`): 服务死亡时仍在运行的命令. 例如开启 "音量键控制" 且 Shizuku 可用时, 宿主启动后由 `InputEventObserver` 经用户服务循环执行 `getevent -t -c 1`, 每次结束宿主都会留下一对 `sh` + `getevent`, 直到下一次实体输入事件才退出 (`input` 注入的事件不经 evdev, 不能让它们退出). 与 adb shell 同 uid 的会被结束; root 身份的 (Shizuku 以 root 运行) 无法在不经 `su` 的情况下结束, 报告里记 `killed 0`. stray 只报告, 不参与判定.

模式:

| 模式 | 结束宿主的方式 | 用途 |
| --- | --- | --- |
| `force-stop` | `am force-stop` | issue #474 场景 (绿色守护休眠相当于强停); P0.1 / P0.2 |
| `kill9` | `run-as <包名> kill -9 <宿主 pid>` | 只适用于 debuggable 宿主 |
| `bind-kill:<秒>` | 设备端一条 shell: 启动脚本, 每 50 ms 查一次新服务进程, 出现后等 `<秒>` 再强停 | 宿主在服务启动完成前后被杀 (P0.3(a)); 备注 `connected` 表示宿主在被杀前已收到 `onServiceConnected` |
| `update` | 每轮先装 `--apk-a`, 跑脚本, 再覆盖安装 `--apk-b` (versionCode 不同, 绑定的 version 也不同), 统计旧服务; 再跑一次脚本, 最后强停 | P0.3(b) |
| `server-restart` | 跑脚本后 `sh <--start-sh>` 重启 Shizuku 服务端 (只适用于 adb 方式启动的 Shizuku), 统计旧服务; 再强停 | P0.3(c) |
| `spawn-orphan` | 脚本 `shizuku.spawn('sleep 600')` 后常驻, 强停后另统计服务的子进程与本轮的 `sleep 600` | P0.3(e), P1.1 的修前基线; 能结束的子进程会被结束 |

其他参数: `--report-dir` (默认 `docs/shizuku-oom/evidence`, 相对路径按仓库根目录解析), `--label` (报告名后缀, 例如 `v664`), `--expect accumulate` (负对照: 至少 80% 的轮次残留数比上一轮多 1 才算 PASS), `--kill-residue` (结束时结束运行期间新出现的用户服务进程, 负对照用; root 身份的服务进程结束不了), `--apk-a` / `--apk-b` (`update` 模式), `--start-sh` (默认 `/sdcard/Android/data/moe.shizuku.privileged.api/start.sh`), `--package`, `--no-wake`, `--profile` (见下).

`--profile` (P0.4 / P1.4 基线): 第 1 轮在结束宿主前给新服务进程做一次内存画像, 写进报告头部与 JSON 的 `profile`: `dumpsys meminfo <pid>` 的 PSS / RSS / Java 堆 / native / code 与 `.dex mmap` / `.art mmap` 两行, 线程数 (`/proc/<pid>/task`), 运行时解压到匿名内存的 dex (smaps 里 `extracted in memory` 的映射; 只有服务以 shell 身份运行时可读, root 身份记 `-`), 以及该进程的 `App attachBaseContext |` / `App onCreate |` 日志行数 (为 0 且启动器日志在缓冲区内, 说明宿主 `App` 没有在服务进程里运行).

## 输出

`<report-dir>/probe-<型号>-<versionCode>-<模式>-<yyyyMMdd>[-<label>].json` 与同名 `.md`. 终端与 md 每轮一行 (轮次, 残留, RSS 合计, 退出来源, 备注), 末尾 PASS / FAIL. 判定: 每轮残留 0 且都等到了新的服务进程才 PASS; `spawn-orphan` 还要求子进程与 `sleep 600` 为 0. 报告不含完整进程清单, logcat 原文与脚本输出 (ROADMAP R3).

## 宿主无障碍服务与起始状态

- Android 会把被强停的应用移出 `enabled_accessibility_services`. 宿主无障碍服务在运行前已开启时, 探针结束时只把宿主自己的条目补回 (报告头部 `a11yRestored`), 其他条目不动. 被直接结束 Python 进程时来不及补回, 需在系统无障碍设置里重新开启.
- 宿主无障碍服务开启时, Android 9 (G8441) 在第一次强停后会立即重新绑定该服务, 宿主随之复活并在 `App.onCreate` 里绑定新的用户服务; 再强停一次才会被移出启用列表. 所以探针开始前最多强停 3 次, 直到宿主不再运行 (报告 `startForceStops`), 计数时宿主仍在运行会在备注里写 `host alive at count`.

## 装包与还原 (P0.2 / P0.3(b))

探针本身不备份也不还原宿主. 换包前:

```
adb -s SERIAL shell pm path org.autojs.autojs6
adb -s SERIAL pull <base.apk 路径> <本地备份目录>/<型号>-<versionCode>-base.apk
```

记下 md5 (设备端 `md5sum <路径>` 与本地一致), 只有一个 `base.apk` 时直接 `adb install -r -d -t <备份>` 装回; 有 split 时用 `install-multiple`. debug 宿主与正式版用同一签名 (`sign.properties` 的 `sm003.jks`), 可以互相覆盖; 从高版本装回低版本需要 `-d` (旧包须为 debuggable, 否则先卸载会丢数据, 不要这样做). 负对照 APK 只从 `SuperMonster003/AutoJs6` 的 GitHub Release 取, 放独立空目录, 用 `apksigner verify --print-certs` 核对证书 SHA-256 与设备上的宿主一致后再装 (ROADMAP R6).
