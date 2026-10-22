# Shizuku 用户服务进程残留与 OOM 加固路线图 (issue #474)

更新日期: 2026-10-22 (立项)

本文是 GitHub issue #474 "shizuku-service-autojs6 僵尸进程导致 oom" 的可执行状态表. 它记录立项时查明的事实 (第 1 节), 维护者 2026-10-22 经三轮选择题确认的 9 项决策 (第 2 节), 派生技术决策 (第 3 节), 红线 (第 4 节), 阶段任务 (第 5 节) 与附录 (决策记录, 证据数字, 待决事项, 文件地图). 内存路线图 `docs/memory/ROADMAP.md` 附录 C Q4 决定该 issue 不纳入内存路线图, Q52 决定单独立项, 位置即本文.

核心目标一句话: 宿主经 Shizuku 启动的用户服务进程 (`org.autojs.autojs6:shizuku-service-for-autojs6`) 在宿主退出, 被杀, 更新, Shizuku 服务端重启等任何情形下都随之退出并带走它启动的子进程, 不再堆积; 存活期间占用的内存降到与它的职责相称的水平; 以上由仓库内可复用的真机探针证明.

使用方式:

1. 每次会话开始时, 从第 5 节选取未完成条目, 按阶段顺序推进; 条目完成后勾选 `[x]` 并追加证据 (提交 hash / 测试类名 / 设备型号与 API / 探针报告文件名 / 关键数值).
2. 第 2 节固定决策不再重新讨论; 第 3 节派生决策可在标注的推翻点之前推翻; 附录 C 待决事项由调度会话用选择题请维护者拍板, 结果回填第 2 节或第 3 节.
3. 工程约定以仓库 `AGENTS.md` 与 `../COMMON_AGENTS.md` 为准 (ASCII 标点, 构建号, 共享工作区, 宿主只本地提交不推送). 真机规则见 `../.claude/rules/device-testing.md`: Shizuku 授权与系统对话框由维护者在手机上操作; 多步验证写成仓库内可复用脚本, 输出只保留通过 / 失败与关键数值, 不打印完整进程清单.
4. 探针报告存 `docs/shizuku-oom/evidence/`, 命名 `probe-<型号>-<构建号>-<模式>-<日期>.{json,md}`; 每条探针结论同时写进附录 B.

---

## 0. 阶段总览

| 阶段 | 目标 | 完成条件 | 预计会话数 |
| --- | --- | --- | --- |
| P0 复现, 负对照与边界探针 | 探针能检出堆积, 报告者场景在当前构建上残留为 0, 5 条边界路径与内存画像有数字 | P0.1-P0.4 全部有报告, 附录 B 填满 | 1 |
| P1 修复与加固 | 子进程清理, 用户服务自守护, (可选) 进程瘦身 | P1.1 / P1.2 真机探针残留 0 并有 JVM 单测; P1.4 RSS 达标 | 1-2 |
| P2 回归, changelog 与发布门 | 7 项 Shizuku 功能回归通过, changelog 条目, 6.8.0 发布门一行 | P2.1-P2.4 勾选, 本地提交 | 1 (可与 P1 同一会话) |

---

## 1. 现状与证据 (基线 HEAD `61c0e6736`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=4283` 即 versionCode 5935, 2026-10-22 立项时阅读)

### 1.1 issue #474

- 报告者 (otokaze, 2025-12-23): 用绿色守护把 AutoJs6 进程自动休眠 (相当于 force-stop), 再用 Tasker 定时唤醒 AutoJs6 执行目标脚本. 截图 (`top`): 22 个 `org.autojs.autojs6:shizuku-service-for-autojs6` 进程, 用户 `shell` (Shizuku 以 adb 身份运行), 每个 RES 114-115 MB, 各占 1.0% 内存; 设备 11357 MB 总内存只剩 147 MB, 4095 MB swap 用满.
- 维护者回复 (2025-12-25): 根因是 `UserService.destroy()` 只打日志不结束进程; 修复为 `Process.killProcess(Process.myPid())` + `exitProcess(0)`.
- 修复提交 `489a8d3a70` (2025-12-25, "6.7.0 - Alpha12 - 修复 Shizuku 用户服务进程未能正常结束导致进程堆积的问题"), 随 v6.7.0 (2026-03-14) 发布; `app/src/main/assets-app/doc/CHANGELOG-zh-Hans.md` 6.7.0 段已有条目. issue 仍为 open, 标签 Accepted / Bug / Deferred (2026-10-01 标签迁移), 报告者未确认.

### 1.2 Shizuku 侧机制 (Shizuku-API 13.1.5 `server-shared`, Shizuku `starter`, 2026-10-22 读 GitHub 源码)

- 宿主以 `daemon(false)`, `version(BuildConfig.VERSION_CODE + 5)`, 进程名后缀 `shizuku-service-for-<包名末段>` 绑定 (`WrappedShizuku.mUserServiceArgs`). 服务端按 `包名:类名` 保存一条 `UserServiceRecord`.
- 服务端在以下情形移除记录并调用 `record.destroy()`: 宿主所有连接死亡且非 daemon (`ConnectionList.onCallbackDied`); 宿主 `unbindUserService(..., remove = true)`; 新绑定的 version 与记录不同 (替换); 包被移除 (`removeUserServicesForPackage`); 启动 30 秒内未收到 binder (`setStartingTimeout`).
- `record.destroy()` 只在 `service != null && service.pingBinder()` 时向用户服务发 oneway `destroy` 事务 (code 16777114); 服务端自己不杀进程. 所以用户服务进程能否退出, 完全取决于宿主 `UserService.destroy()` 的实现 (1.1 的根因).
- 启动器 `moe.shizuku.starter.ServiceStarter.main`: 创建服务实例 (`UserService.create` 会 `makeApplication`, 即运行宿主 `App.onCreate`; P0.4 更正: 参数为 `(true, null)`, 只建默认 `android.app.Application` 且不调用 onCreate, 宿主 `App` 不在服务进程里运行, 见 1.5), 经 provider 把 binder 交给服务端; 交付失败 (含记录已被移除, 服务端抛 "unable to find token") 时 `System.exit(1)`; 交付成功后对服务端 binder `linkToDeath` -> `System.exit(0)`; 然后 `Looper.loop()`. 因此 "宿主在服务启动完成前被杀" 与 "Shizuku 服务端重启" 两条路径由 Shizuku 自身覆盖, 其余全靠 `destroy()`.

### 1.3 宿主侧现状

- `core/shizuku/UserService.kt`: `destroy()` / `exit()` 已含退出代码 (1.1); `execCommand` 每次 `Runtime.exec("sh")` 跑完即 `destroy()`; `newShellProcess` 返回 `RemoteShellProcessHolder`, 它对宿主传来的 client token `linkToDeath` -> 结束子进程. 但 `destroy()` 退出前不清理仍存活的 holder 子进程与 pty 进程, 进程被 SIGKILL 后这些子进程成为孤儿 (待 P0.3(e) 实测).
- `runtime/api/WrappedShizuku.kt`: 绑定是懒触发的 (`connectedService` / `getServiceOrNull` / 授权结果监听; P0 更正: 开启 "音量键控制" 时宿主启动约 1.2 秒后即经 `InputEventObserver` 绑定, 见 1.5); `connectedService` 等 10 秒无服务时 `unbindUserService(remove = true)` 再重绑 (针对更新后旧记录); `onDestroy` 只在应用主动退出 (`IntentUtils.exit`) 时调用. 宿主侧没有向服务传递自身存活令牌的通道 (`newShellProcess` 的 client 只对该子进程有效).
- (P0.4 实测不成立, 见 1.5: 宿主 `App` 不在用户服务进程里运行, 本条只是代码静态推断) `App.onCreate` 在用户服务进程里走 "主进程" 分支的大部分: `setUpDebugEnvironment`, `setUpLeakCanary`, `AutoJs.initInstance`, `AccessibilityCompanionSync.install`, `GlobalKeyObserver.initIfNeeded`, `setupDrawableImageLoader`, `TimedTaskScheduler.init`, `initDynamicBroadcastReceivers`, `Toaster.init`, `ThemeColorManager.init`, `setUpDefaultNightMode`, `scheduleDeferredMaintenance`; 只有 `getProcessNameCompat() == applicationInfo.processName` 守卫的几项被跳过. 这是单个用户服务进程 RES 约 115 MB 的来源 (P0.4 画像后确认). 已有 `:crash_report` 进程后缀分支可作瘦身分支的样板.

### 1.4 测试设备快照 (2026-10-22 20:15, 只读 `pm list packages` / `ps -A`)

| 设备 | Android / API | Shizuku 版本 | 运行身份 | 用户服务进程数 |
| --- | --- | --- | --- | --- |
| XQ-AT72 (QV710AF65F) | 12 / 31 | 13.5.4 | shell (与报告者一致) | 1 |
| G8441 (BH900ASK9E) | 9 / 28 | 13.5.4 | root | 1 |
| XQ-DQ72 (QV770340J7) | 13 / 33 | 13.6.0 | root | 1 |
| OPD2413 (63751f63) | 15 / 35 | 13.6.0 | 未运行 | - |
| 23046RP50C (968e9f18) | 15 / 35 | 13.5.4 | 未运行 (仅应用进程) | - |
| 22120RN86C (bek749scrwv4wo8h) | 13 / 33 | 13.5.4 | 未运行 | - |

当前构建 (含 6.7.0 修复) 在三台运行 Shizuku 的设备上各只有 1 个用户服务进程, 与 1.1 的修复一致.

### 1.5 P0 实测更正与新发现 (2026-10-22, 子会话 shizuku-oom-p0)

- 宿主 `App` 不在用户服务进程里运行 (更正 1.2 第 4 条, 1.3 第 3 条, T5): Shizuku-API `server-shared` 的 `UserService.create` 调用 `LoadedApk.makeApplication(true, null)`, `forceDefaultAppClass = true` 只建默认 `android.app.Application`, instrumentation 为 null 也不调用其 onCreate. 实测 G8441 服务进程的日志从启动器第一行 (`ShizukuServiceStarter: starting service`) 起没有 `App attachBaseContext |` / `App onCreate |`, 构造函数收到的是 `ContextImpl`; XQ-AT72 同样为 0 行 (附录 B.3). 因此 D6 / P1.4 "在 `App.onCreate` 加用户服务进程分支" 没有可跳过的初始化, 见附录 C Q2.
- 用户服务进程的内存构成 (P0.4, 附录 B.3): XQ-AT72 (shell 身份) 上 HEAD debug 包的服务进程 RSS 约 210 MB, 其中约 83 MB 是宿主 APK 的 38 个 `classesN.dex` 被运行时 "extracted in memory" 到匿名内存 (adb 安装的 debug 包没有 oat 目录), 约 20 MB 是 boot image 的私有脏页 (app_process 不经 zygote 启动, 不能共享); G8441 (root 身份, Android 9) 上原有 debug 5835 的服务进程 RSS 约 65 MB, PSS 约 22 MB, `.dex mmap` 约 5 MB 且几乎全为干净页 (root 可读 oat).
- 宿主启动即绑定用户服务 (更正 1.3 第 2 条): 开启 "音量键控制" 时 `App.onCreate` 的 `GlobalKeyObserver.initIfNeeded` 在 1200 ms 后调用 `InputEventObserver.ensureObservedAsync`, Shizuku 可用时进 `observeByShizuku`, 线程 "InputEventObserver-Shizuku" 循环执行 `WrappedShizuku.execCommand("getevent -t -c 1")`. G8441 日志: 宿主 `App onCreate` 后 1.26 秒即新建服务记录. 所以报告者场景 (Tasker 定时唤醒宿主) 即使脚本不用 Shizuku, 每次唤醒也会拉起用户服务.
- 第二类残留 (P1.1 范围): 宿主被强停时, 用户服务正在执行的 `getevent -t -c 1` (`ProcessShell.execCommand` 的 `Runtime.exec("sh")` 及其子进程) 随服务死亡被 init 收养, 直到下一个实体输入事件才退出 (`input` 注入的事件不经 evdev, 不能让它退出). G8441 (root) 静置一晚累积 32 对 `sh` + `getevent`, RSS 合计约 115 MB (多为共享页). 探针把它记为 stray (不参与判定), shell 身份的会被探针结束.
- 10 秒超时重绑 (P0.3(d)) 在 G8441 装包后首次启动时自然发生: 新装 APK 冷加载 dex, 第一个服务进程从启动到构造约 19 秒, 宿主 10 秒后 `unbindUserService(remove = true)` 再绑, 第二个进程启动; 两者同时构造完成后, 记录已被移除的旧进程交付 binder 失败, 启动器 `System.exit(1)` 退出, 新进程正常服务; Shizuku 30 秒后报旧记录 "is not started in 30000 ms". 结果: 无永久残留, 约 19 秒内两个服务进程 (各约 65 MB) 并存. 日志摘录在共享笔记 `shizuku-oom-p0-g8441-rebind.md`.

---

## 2. 固定决策 (2026-10-22 三轮选择题, 维护者确认)

| 编号 | 决策 | 来源 |
| --- | --- | --- |
| D1 | 路线图位于 `docs/shizuku-oom/ROADMAP.md`; 证据 `docs/shizuku-oom/evidence/`; 探针脚本 `tools/shizuku-oom/` | Q1 |
| D2 | 范围: 验证已发布修复 + 边界路径探针 + destroy 前清理子进程 + 用户服务自守护 + 用户服务进程瘦身 | Q2 |
| D3 | 负对照用 GitHub Release v6.6.4 APK (维护者签名, 修复前最后一个正式版) 跑同一探针, 证明探针能检出堆积; 测后装回原包 | Q3 |
| D4 | 设备: XQ-AT72 (shell 身份, Android 12) 主测跑全部探针; G8441 (root 身份, Android 9) 复核 P0.1 与 P2.1; 按锁表排队 | Q4 |
| D5 | 自守护: `IUserService` 新增 `attachHost(IBinder)`, 宿主连上后立即附着, 服务对其 `linkToDeath`, 宿主死亡即清理子进程并退出; 启动后 30 秒无宿主附着也退出; `version(VERSION_CODE + 5)` 改为 `+ 6` 让旧服务被替换 | Q5 |
| D6 | 瘦身: `App.onCreate` 仿照 `:crash_report` 增加用户服务进程分支, 只做 `UserService` 需要的初始化; 验收 RSS 从约 115 MB 降到 40 MB 以下且 7 项回归通过; 降不到就登记实测值请维护者再定 | Q6 |
| D7 | issue #474 的回复与关闭由维护者自行处理, 子会话不写回复草稿; 本文与证据是回复素材 | Q7 |
| D8 | 第一批只发一个子会话做完 P0; P1 按 P0 结果发 | Q8 |
| D9 | 列入 6.8.0 发布门 (`docs/dev/6.8.0-release-gate.md`): P0 + P1.1 / P1.2 完成并 P2.1 回归通过前不发 6.8.0; P1.4 瘦身不在门内 | Q9 |

---

## 3. 派生技术决策 (可在推翻点之前推翻)

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 探针 `tools/shizuku-oom/zombie_probe.py` (py -3.12 -I): 参数 `--serial`, `--rounds N`, `--mode {force-stop, kill9, bind-kill:<秒>, update, server-restart, spawn-orphan}`, `--report-dir`; 每轮: 启动宿主并运行一个只调用 `shizuku('id')` 后 `exit()` 的最小脚本, 等服务进程出现, 按模式结束宿主, 等 3 秒, 统计 `ps -A -o PID,USER,RSS,NAME` 里 `shizuku-service-for-autojs6` 的进程数与 RSS 合计, 以及 `spawn-orphan` 模式下的 `sleep` 子进程; 输出 JSON + md, 每轮一行 (轮次, 残留数, RSS 合计), 末尾 PASS / FAIL | P0.1 完成 |
| T2 | 残留定义: 宿主被结束后 3 秒, 用户服务进程数应为 0 (daemon(false) 的服务随宿主退出); 负对照预期每轮 +1. 启动脚本的方式复用 `tools/memory/run_gate.py` 已验证的宿主脚本启动途径 (子会话核对), 不自造 | P0.1 完成 |
| T3 | 退出原因统一出口: `UserService.shutdown(reason)` 先结束登记的 `RemoteShellProcessHolder` 与在途 `execCommand` 进程 (`ProcessTree.terminate`), 再 `killProcess` + `exitProcess`; 打一行 `UserService exit: <reason>` (destroy / exit / host-died / no-host-attached), 探针用 logcat 核对原因 | P1.1 实现时 |
| T4 | `attachHost` 的宿主令牌由 `WrappedShizuku` 持有一个进程级 `Binder` (静态, 与宿主进程同生命周期); `onServiceConnected` 后立即调用; 旧宿主连新服务与新宿主连旧服务都被 version 替换排除, 不需要兼容分支. 30 秒看门狗用服务进程主线程 Handler 实现, 附着后取消 | P1.2 实现时 |
| T5 | (P0.4 实测前提不成立, 待附录 C Q2 拍板) 瘦身分支按进程名后缀前缀 `shizuku-service-for-` 判断 (常量从 `WrappedShizuku` 抽出, 打包应用 inrt 的后缀也以它开头); 分支里保留的初始化由子会话按 `UserService` 调用图确定 (至少: 字符串资源可用, `PrivilegedFileNative` 按请求加载, termexec 可加载), 其余全部跳过; 不改 `Application` 类 (Shizuku 固定用宿主的 `Application`) | P1.4 实现时 |
| T6 | 7 项回归清单 (P2.1): (1) `shizuku('id')` / `shizuku.execCommand`; (2) `shizuku.spawn` 常驻进程 + 脚本结束自动结束; (3) `new Shell({ adb: true })` 与 `pty: true`; (4) 文件管理器经 Shizuku 浏览受限目录并保存文本文件; (5) 无障碍服务用 Shizuku 自动启用; (6) 浮动按钮 "更多" 经 Shizuku 取 currentPackage / currentActivity; (7) KeyCode 经 Shizuku 发送键码 (issue #492); (8) 其余调用点各跑一次冒烟: RootAutomator 的 adb 模式, 指针位置工具, 插件中心启动器图标, AndroidIdReader, augment app 的 Shizuku 分支 (立项时 grep `WrappedShizuku.` 共 16 个文件, 子会话核对清单). (1)-(7) 在 XQ-AT72 (shell) 与 G8441 (root) 各跑一次, (8) 只在 XQ-AT72 | P2.1 开始前 |
| T7 | JVM 单测: `UserService` 的退出逻辑抽成可注入 exit 钩子的 `UserServiceLifecycle` (或等价命名), 测 (a) 宿主令牌死亡 -> 先清理子进程再退出, (b) 30 秒无附着 -> 退出, (c) 附着后看门狗取消, (d) destroy 重入只退出一次 | P1.2 实现时 |

---

## 4. 红线

- R1 宿主仓库只本地提交, 不推送; 共享工作区: 只提交自己的文件, 不切分支; `.changelog/lang_zh-Hans.json`, `docs/dev/6.8.0-release-gate.md`, `docs/memory/ROADMAP.md` 有 memory 子会话在改, 只追加并只暂存自己的 hunk.
- R2 真机按锁表 `C:/Users/wu2du/AppData/Roaming/Claude/side-session-notes/local_8ab23cdc-d795-4973-9379-2554a76c2967/locks.md` 登记与释放; 装机前备份原包 (md5), 测后装回; 不改宿主设置; Shizuku 授权对话框由维护者操作. XQ-AT72 的 Shizuku 以 adb 身份运行, 若设备重启可用 `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh` 重启服务端 (授权保留).
- R3 探针与报告不打印完整进程清单与令牌, 只保留每轮残留数, RSS 合计与 PASS / FAIL.
- R4 AIDL 变更 (D5) 必须同步 `version(+ 6)`; 不改既有方法的事务号.
- R5 瘦身 (P1.4) 不得改变 Shizuku 功能行为; 7 项回归 (T6) 任一失败即回退该项初始化.
- R6 不下载来历不明的文件: 负对照 APK 只从 `SuperMonster003/AutoJs6` 的 GitHub Release v6.6.4 取, 放独立目录, 校验签名与宿主正式签名一致后再装.

---

## 5. 阶段任务

### P0 复现, 负对照与边界探针 (D3 / D4, 设备 XQ-AT72; P0.1 另在 G8441 复核)

- [ ] P0.1 探针脚本 `tools/shizuku-oom/zombie_probe.py` + `tools/shizuku-oom/README.md` (T1 / T2). 验收: 当前 HEAD 的 debug 构建在 XQ-AT72 跑 `--mode force-stop --rounds 20` 残留 0/20, G8441 跑 10 轮残留 0/10; 报告入 `docs/shizuku-oom/evidence/`, 数字入附录 B.
- [ ] P0.2 负对照 (D3): v6.6.4 发布包在 XQ-AT72 跑 `--mode force-stop --rounds 10`, 预期残留逐轮累加到约 10, 记录 RSS 合计; 测后装回原包. 验收: 报告显示堆积 (证明探针有效), 附录 B 有 "修复前" 列.
- [ ] P0.3 边界路径探针 (HEAD 构建, XQ-AT72), 每条路径写残留数与 logcat 里的退出来源: (a) `bind-kill:0.2 / 0.5 / 1 / 2` 各 5 轮 (宿主在服务启动完成前后被杀); (b) `update`: 两个构建号不同的 debug 包先后覆盖安装, 旧服务应因 version 不同被替换并退出; (c) `server-restart`: 用 start.sh 重启 Shizuku 服务端, 旧用户服务应随服务端死亡退出; (d) 10 秒超时重绑路径: 代码审读 + 能构造则实测 (例如让服务启动变慢), 否则记 "未实测, 由 P1.2 覆盖"; (e) `spawn-orphan`: 脚本 `shizuku.spawn('sleep 600')` 后 force-stop 宿主, 检查 `sleep` / `sh` 是否残留 (预期现状残留, 作 P1.1 的修前基线). 验收: 5 条路径各有结论与数字 (附录 B).
- [ ] P0.4 用户服务进程内存画像 (瘦身基线): `dumpsys meminfo <pid>` 的 PSS / Java 堆 / native / code, 线程数 (`/proc/<pid>/task`), logcat 里 `App.onCreate` 各项在该进程的执行痕迹 (哪些跑了); 写附录 B "瘦身前" 列. 验收: 数字齐全, 并列出建议跳过的初始化项供 P1.4 用.

### P1 修复与加固 (D5 / D6, 代码改动; 验证需 XQ-AT72)

- [ ] P1.1 destroy / exit 前清理子进程 (T3): `UserService` 登记存活的 `RemoteShellProcessHolder` (holder destroy 时注销) 与在途 `execCommand` 进程, `shutdown(reason)` 统一出口. 验收: P0.3(e) 探针残留 0 (sleep / sh 均无), JVM 单测覆盖清理顺序.
- [ ] P1.2 用户服务自守护 (D5 / T4 / T7): AIDL `attachHost(IBinder)`, 宿主 `onServiceConnected` 后附着, 宿主死亡 -> `shutdown(host-died)`; 30 秒无附着 -> `shutdown(no-host-attached)`; `version(+ 6)`. 验收: JVM 单测 4 例通过; XQ-AT72 `force-stop` 20 轮与 `bind-kill` 各档残留 0, logcat 退出原因为 host-died 或 destroy (竞态任一即可); (d) 路径如 P0.3 能实测则残留 0.
- [ ] P1.3 宿主侧 10 秒超时重绑 (`connectedService`): 按 P0.3(d) 结论处置; 默认不改代码 (P1.2 的看门狗已覆盖被提前移除记录的进程), 在附录 A 记理由.
- [ ] P1.4 用户服务进程瘦身 (D6 / T5, 不在发布门内): `App.onCreate` 增加用户服务进程分支. 验收: XQ-AT72 上 `dumpsys meminfo` RSS < 40 MB (附录 B "瘦身后" 列), T6 七项回归通过; 达不到 40 MB 时登记实测值到附录 C 请维护者再定.

### P2 回归, changelog 与发布门 (D7 / D9)

- [ ] P2.1 T6 七项回归在 XQ-AT72 (shell) 与 G8441 (root) 各跑一遍, 结果表入 `docs/shizuku-oom/evidence/regression-<日期>.md`. 验收: (1)-(7) 的 14 格与 (8) 冒烟全部通过.
- [ ] P2.2 changelog: `.changelog/lang_zh-Hans.json` 6.8.0 段加 `fix` 1 条 (用户服务进程随宿主退出并清理其启动的子进程, 引用 issue #474) 与 `improvement` 1 条 (用户服务进程不再初始化宿主主进程组件, 内存占用下降, 仅在 P1.4 完成时), 用 `.python/generate_markdown.py` 生成 zh-Hans 的 markdown; 其他语言由维护者同步. 验收: JSON 解析通过, 生成文件只含本条变化.
- [ ] P2.3 发布门: `docs/dev/6.8.0-release-gate.md` 追加一行 (来源本文 D9, 复核方法 = P0.1 探针 + P2.1 回归, 证据 = evidence 报告). 验收: 该行存在且状态写明.
- [ ] P2.4 收尾: 本文勾选与附录回填, 本地提交 (不推送), 看板 `../.claude/boards/shizuku-oom.md` 子会话结果区追加结论; issue 回复由维护者处理 (D7).

---

## 附录 A 决策记录

| 编号 | 时间 | 事项 | 结果 |
| --- | --- | --- | --- |
| D1-D4 | 2026-10-22 第 1 轮 | 位置, 范围, 负对照, 设备 | 见第 2 节 |
| D5-D8 | 2026-10-22 第 2 轮 | 自守护设计, 瘦身程度, issue 处理, 首批分配 | 见第 2 节 |
| D9 | 2026-10-22 第 3 轮 | 6.8.0 发布门 | 列入 (P0 + P1.1 / P1.2 + P2.1) |

## 附录 B 证据与数字 (P0 回填, 之后每阶段追加一列)

### B.1 报告者场景 (force-stop 循环)

| 构建 | 设备 | 轮数 | 残留进程数 | RSS 合计 | 报告 |
| --- | --- | --- | --- | --- | --- |
| 报告者截图 (版本未知, 2025-12) | 未知 (11.3 GB, 8 核) | - | 22 | 约 2.5 GB (114-115 MB 每个) | issue #474 截图 |
| v6.6.4 (修复前, P0.2) | XQ-AT72 | 10 | | | |
| HEAD debug (P0.1) | XQ-AT72 | 20 | | | |
| HEAD debug 5935 (P0.1) | G8441 (root) | 10 | 0/10 (每轮退出来源 destroy) | 0 (单个服务约 65.5 MB) | `probe-G8441-5935-force-stop-20261022` |
| P1.2 后 | XQ-AT72 | 20 | | | |

### B.2 边界路径 (P0.3, HEAD 构建; P1 后复测同表追加列)

| 路径 | 轮数 | 残留 | logcat 退出来源 | 结论 |
| --- | --- | --- | --- | --- |
| (a) bind-kill 0.2 / 0.5 / 1 / 2 s | 各 5 | | | |
| (b) update (version 替换) | 3 | | | |
| (c) server-restart | 3 | | | |
| (d) 10 秒超时重绑 | 1 (G8441 自然发生) | 0 | 旧进程: 交付 binder 失败, 启动器 `System.exit(1)` (`VM exiting with result code 1`); 新进程正常服务 | 代码审读 + G8441 装包后首次启动自然复现 (冷加载约 19 秒 > 10 秒): 无永久残留, 约 19 秒两个服务进程并存; 支持 P1.3 默认不改代码 (1.5) |
| (e) spawn-orphan (sleep 600) | 5 | | | |

### B.3 用户服务进程内存画像 (P0.4 / P1.4)

| 阶段 | 设备 | RSS | PSS | Java 堆 | native | 线程数 | 跑了哪些初始化 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 瘦身前 | XQ-AT72 | | | | | | |
| 瘦身后 | XQ-AT72 | | | | | | |

## 附录 C 待决事项

格式: `| 编号 | 问题与选项 (标出推荐项与是否已先行) | 影响条目 | 状态 |`. 立项时的 9 项已全部拍板 (附录 A); 子会话在此登记新事项, 不自行采用默认值 (不阻塞的按推荐项先行并标明).

| 编号 | 问题与选项 | 影响条目 | 状态 |
| --- | --- | --- | --- |
| Q1 | 宿主启动即绑定与在途 getevent 孤儿 (1.5 第 3, 4 条) 怎么纳入 P1? A (推荐): P1.1 照原文清理在途 `execCommand` 进程, 验收追加 "开启音量键控制时 force-stop 20 轮 stray 为 0" (XQ-AT72 由探针核对, G8441 root 身份在 P2.1 复核), 不改绑定时机; 返工: 无. B: A 之外把 `InputEventObserver` 的 Shizuku 分支改为一个常驻 `newShellProcess` (client 令牌 linkToDeath, 宿主死即结束), 不再每个按键事件起一对 sh + getevent; 多改 `InputEventObserver` 并在 T6 加 "音量键控制" 1 项回归, 约多半个会话. C: 不处理, stray 只报告 | P1.1, P2.1, T6 | 需拍板 (不阻塞 P0, 未先行) |

## 附录 D 文件地图

| 路径 | 作用 | 条目 |
| --- | --- | --- |
| `docs/shizuku-oom/ROADMAP.md` | 本文 | 立项 |
| `docs/shizuku-oom/evidence/` | 探针与回归报告 | P0-P2 |
| `tools/shizuku-oom/zombie_probe.py`, `tools/shizuku-oom/README.md` | 残留探针 | P0.1 |
| `app/src/main/aidl/org/autojs/autojs/core/shizuku/IUserService.aidl` | `attachHost` | P1.2 |
| `app/src/main/java/org/autojs/autojs/core/shizuku/UserService.kt` | shutdown 统一出口, 子进程登记, 看门狗 | P1.1 / P1.2 |
| `app/src/main/java/org/autojs/autojs/core/shizuku/RemoteShellProcessHolder.kt` | 向 `UserService` 登记 / 注销 | P1.1 |
| `app/src/main/java/org/autojs/autojs/runtime/api/WrappedShizuku.kt` | 宿主令牌, `attachHost` 调用, `version(+ 6)`, 后缀常量 | P1.2 / P1.4 |
| `app/src/main/java/org/autojs/autojs/App.kt` | 用户服务进程分支 | P1.4 |
| `app/src/test/java/org/autojs/autojs/core/shizuku/` | T7 单测 | P1.1 / P1.2 |
| `.changelog/lang_zh-Hans.json` | changelog | P2.2 |
| `docs/dev/6.8.0-release-gate.md` | 发布门一行 | P2.3 |
| `docs/memory/ROADMAP.md` 附录 C Q4 / Q52 | 去向指向本文 | 立项 |
