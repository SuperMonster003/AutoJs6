# Shizuku 用户服务残留探针

配合 `docs/shizuku-oom/ROADMAP.md` 使用 (issue #474). `zombie_probe.py` 在真机上反复启动并结束宿主, 统计结束后仍在运行的 Shizuku 用户服务进程 (`org.autojs.autojs6:shizuku-service-for-autojs6`). 脚本不点屏幕, 不改宿主设置; 只在 `update` 模式安装 APK. Shizuku 授权等对话框由维护者在手机上操作. `regression.py` 做 T6 功能回归, 见 "功能回归" 一节.

## 用法

```
py -3.12 -I tools/shizuku-oom/zombie_probe.py -s SERIAL --mode force-stop --rounds 20
```

前提: 宿主已安装并已获 Shizuku 授权 (`moe.shizuku.manager.permission.API_V23`), Shizuku 服务端在运行, 屏幕已解锁 (脚本开始前发送 `KEYCODE_WAKEUP`, 只点亮不点击). 设备临时文件放在 `/sdcard/Download/aj6-shizuku-oom/`, 结束时删除.

每轮:

1. 写入一个最小脚本 (`shizuku('id')`, 把 `code` 与 uid 写进标记文件, `exit()`), 用 `am start -n org.autojs.autojs6/org.autojs.autojs.external.open.RunIntentActivity -e path <脚本>` 启动. 这是 `tools/memory` 设备脚本 (`device_diagnostics_page.py` 等) 已验证的途径; `run_gate.py` 本身走 `am instrument`, 测试进程随宿主一起被强停, 不适合这里. v6.6.4 的 `ScriptIntents` 也读 `path` 参数.
2. 等新的用户服务进程出现与标记文件写入 (`--wait`, 默认 45 s).
3. 按 `--mode` 结束宿主, 等 `--settle` 秒 (默认 3, ROADMAP T2), 用 `ps -A -o PID,PPID,USER,RSS,NAME` 统计用户服务进程数与 RSS 合计.
4. 读本轮开始以来的 logcat, 只判定每个服务进程的退出来源: 优先取 P1 后服务自己打出的 `UserService: exit: <原因>` (`destroy` / `exit` / `host-died` / `no-host-attached`, ROADMAP D5); 没有这一行时为 `destroy` (打出了 `UserService: destroy`, 即宿主连接死亡后 Shizuku 发来的销毁事务), `exited` (没有这一行就消失了, 例如服务端重启后启动器的 `System.exit`), `alive`.
5. 记录本轮新出现, 已被 init 收养的 `sh` / `getevent` / `sleep` 及其后代 (报告备注里的 `stray`): 服务死亡时仍在运行的命令. 例如 P1 前开启 "音量键控制" 且 Shizuku 可用时, 宿主启动后由 `InputEventObserver` 经用户服务循环执行 `getevent -t -c 1`, 每次结束宿主都会留下一对 `sh` + `getevent`, 直到下一次实体输入事件才退出 (`input` 注入的事件不经 evdev, 不能让它们退出). 与 adb shell 同 uid 的会被结束; root 身份的 (Shizuku 以 root 运行) 无法在不经 `su` 的情况下结束, 报告里记 `killed 0`. 默认 stray 只报告, 不参与判定; 加 `--stray-fails` 或在 `volume-key` 模式下, 有 stray 的轮次判 FAIL (P1.5 验收).

模式:

| 模式 | 结束宿主的方式 | 用途 |
| --- | --- | --- |
| `force-stop` | `am force-stop` | issue #474 场景 (绿色守护休眠相当于强停); P0.1 / P0.2 |
| `kill9` | `run-as <包名> kill -9 <宿主 pid>` | 只适用于 debuggable 宿主 |
| `bind-kill:<秒>` | 设备端一条 shell: 启动脚本, 每 50 ms 查一次新服务进程, 出现后等 `<秒>` 再强停 | 宿主在服务启动完成前后被杀 (P0.3(a)); 备注 `connected` 表示宿主在被杀前已收到 `onServiceConnected` |
| `update` | 每轮先装 `--apk-a`, 跑脚本, 再覆盖安装 `--apk-b` (versionCode 不同, 绑定的 version 也不同), 统计旧服务; 再跑一次脚本, 最后强停 | P0.3(b) |
| `server-restart` | 跑脚本后 `sh <--start-sh>` 重启 Shizuku 服务端 (只适用于 adb 方式启动的 Shizuku), 统计旧服务; 再强停 | P0.3(c) |
| `spawn-orphan` | 脚本 `shizuku.spawn('sleep 600')` 后常驻, 强停后另统计服务的子进程与本轮的 `sleep 600` | P0.3(e), P1.1 的修前基线; 能结束的子进程会被结束 |
| `volume-key` | 脚本每 500 ms 写一次心跳; 服务下出现常驻 `getevent` 后, 用 `sendevent` 向 `getevent -pl` 里带 `KEY_VOLUMEUP` 的节点写一次音量加键 (按下, SYN, 抬起, SYN); adb shell 不能写该节点时 (例如 Android 12 的 SELinux 拒绝 shell 写 `/dev/input`), 改用系统 `hid` 工具经 `/dev/uhid` 注册一个虚拟消费类控制设备并发送音量加 (报告里 `keyInjection` 为 `uhid`). 然后等脚本停止 (标记为 `stopped` 或心跳停止 2.5 s, 最多 8 s), 把变化了的铃声 / 媒体 / 通知音量 (stream 2 / 3 / 5) 改回原值, 再强停 | T6 (9) / P1.5: 需开启 "运行时音量键控制"; 备注 `getevent` 为服务下的 getevent 数, `stopped` 为是否停止及用时 (信号: 标记或心跳); stray 参与判定 |

其他参数: `--stray-fails` (有 stray 的轮次判 FAIL), `--report-dir` (默认 `docs/shizuku-oom/evidence`, 相对路径按仓库根目录解析), `--label` (报告名后缀, 例如 `v664`), `--expect accumulate` (负对照: 至少 80% 的轮次残留数比上一轮多 1 才算 PASS), `--kill-residue` (结束时结束运行期间新出现的用户服务进程, 负对照用; root 身份的服务进程结束不了), `--apk-a` / `--apk-b` (`update` 模式), `--start-sh` (默认 `/sdcard/Android/data/moe.shizuku.privileged.api/start.sh`), `--package`, `--no-wake`, `--profile` (见下).

`--profile` (P0.4 / P1.4 基线): 第 1 轮在结束宿主前给新服务进程做一次内存画像, 写进报告头部与 JSON 的 `profile`: `dumpsys meminfo <pid>` 的 PSS / RSS / Java 堆 / native / code 与 `.dex mmap` / `.art mmap` 两行, 线程数 (`/proc/<pid>/task`), 运行时解压到匿名内存的 dex (smaps 里 `extracted in memory` 的映射; 只有服务以 shell 身份运行时可读, root 身份记 `-`), 以及该进程的 `App attachBaseContext |` / `App onCreate |` 日志行数 (为 0 且启动器日志在缓冲区内, 说明宿主 `App` 没有在服务进程里运行).

## 输出

`<report-dir>/probe-<型号>-<versionCode>-<模式>-<yyyyMMdd>[-<label>].json` 与同名 `.md`. 终端与 md 每轮一行 (轮次, 残留, RSS 合计, 退出来源, 备注), 末尾 PASS / FAIL. 判定: 每轮残留 0 且都等到了新的服务进程才 PASS; `spawn-orphan` 还要求子进程与 `sleep 600` 为 0; `volume-key` 还要求服务下有 `getevent`, 脚本被音量键停止, 且 stray 为 0. 报告不含完整进程清单, logcat 原文与脚本输出 (ROADMAP R3).

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

## 功能回归 (T6, P2.1)

```
py -3.12 -I tools/shizuku-oom/regression.py -s SERIAL --label p1
py -3.12 -I tools/shizuku-oom/regression.py -s SERIAL --items 1,2,3
```

`regression.py` 按 ROADMAP T6 逐项检查 Shizuku 功能, 每项一行通过 / 失败与关键数值: (1) `shizuku('id')` / `shizuku.execCommand`, (2) `shizuku.spawn` 随脚本结束, (3) `new Shell({ adb: true })` 与 `pty: true`, (4) 文件管理器经 Shizuku 浏览与保存 (仪器测试), (5) 经 Shizuku 写入无障碍启用列表并等服务连上, (6) currentPackage / currentActivity 与 dumpsys 一致, 加浮动按钮 "更多" 的仪器测试, (7) KeyCode 经 Shizuku 发送音量减键 (`dumpsys audio` 音量日志里出现 `ADJUST_LOWER` 请求或音量变化即算送达; 静音或勿扰时音量不变), (8) 其余调用点冒烟 (RootAutomator 的 Shizuku 后端; adb shell 不能写任何 `/dev/input` 节点且它如实报告时记为不适用, 指针位置工具, 插件中心启动器图标命令, AndroidIdReader, app 模块的 Shizuku 分支; 默认只在 Shizuku 以 shell 身份运行时跑), (9) 一轮 `volume-key` 探针, (10) 运行中打开音量键控制 (P3.1: 两项设置都关闭时冷启动宿主, 服务下不应有 getevent; 打开 "运行时音量键控制" 后 15 s 内出现 getevent, 音量加键能停止心跳脚本; 再关闭后 10 s 内 getevent 结束), (11) 录制后回收 getevent (P3.2: 经 GlobalActionRecorder 录制两轮并以 discard 结束, 录制中服务下的 getevent 比基线多 1, 结束后回到基线; 基线在脚本等到用户服务后取计数稳定的值, 已开启音量键控制时为 1. 开始录制会在主线程上经 Shizuku 解析触摸屏设备, P3.6 之前宿主刚启动时可能等待用户服务 10 s 以上, 所以每步最多等 30 s 并记录主线程耗时), (12) 冷启动后立即录制 (P3.6, D17 / D18: 先以 commit 清掉触摸屏节点缓存, 强停后冷启动脚本, 立即在主线程开始并丢弃一次录制 (用户服务可能尚未连上), 每步不超过 1 s (主线程上既不等待用户服务, 也不解析节点); 再在非主线程等到用户服务后解析节点, `/proc/bus/input/devices` 被拒时经 `getevent -pl` 解析成功并写入缓存; 最后写回原缓存值). 最后再强停一次宿主, 用户服务与 stray 都须为 0.

- 前提: 宿主已获 Shizuku 授权; (4) / (6) 需要同一构建的仪器测试包 `org.autojs.autojs6.test` (`:app:assembleAppDebugAndroidTest`), 先按上一节备份设备上原有的测试包, 测后装回. 脚本直接跑 `am instrument`, 不用 `connected*` 任务 (会卸载应用).
- 会临时改动并在该项结束时还原: (5) 的无障碍启用列表与 `accessibility_enabled`, (7) 的音量与铃声模式 (最低音量减小切到振动时用音量加键改回), (8) 的 `pointer_location`, (10) 的两项音量键控制设置 (经脚本读写宿主设置) 与音量加键改变的音量. 仪器测试里被 assumption 跳过的用例 (例如只适用于 API 29+) 不算失败, 备注写明跳过原因. (8) 还会经 Shizuku 打开系统设置再强停它. `am instrument` 与强停使宿主被移出无障碍启用列表, 结束时补回宿主原有的条目.
- 输出: 每台设备 `regression-<型号>-<versionCode>-<yyyyMMdd>[-<label>].json`, 以及由同日期 (同 label) 的全部 JSON 重建的 `regression-<yyyyMMdd>[-<label>].md` (每台设备一列). Android ID 只比较, 不写入报告.
