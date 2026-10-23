# Shizuku 用户服务进程残留与 OOM 加固路线图 (issue #474)

更新日期: 2026-10-23 (P3 立项: Q5 / Q6 / Q7 拍板为 D14 / D15 / D16; 2026-10-22 立项, 同日 P0 完成, Q1 / Q2 拍板为 D10 / D11)

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
| P0 复现, 负对照与边界探针 | 探针能检出堆积, 报告者场景在当前构建上残留为 0, 5 条边界路径与内存画像有数字 | P0.1-P0.4 全部有报告, 附录 B 填满 (2026-10-22 完成) | 1 |
| P1 修复与加固 | 子进程清理, 用户服务自守护, getevent 常驻进程 (P1.5); 进程内存 (P1.4) 已取消 (D12) | P1.1 / P1.2 / P1.5 真机探针残留 0 (含 stray 0) 并有 JVM 单测 (2026-10-23 完成) | 1-2 |
| P2 回归, changelog 与发布门 | T6 的 Shizuku 功能回归 (9 项) 通过, changelog 条目, 6.8.0 发布门一行 | P2.1-P2.4 勾选, 本地提交 (2026-10-23 完成, 与 P1 同一会话; Q5 已决为 D14, 补跑在 P3.3) | 1 (可与 P1 同一会话) |
| P3 附带发现与 T6 (8) 补跑 (D14 / D15 / D17 / D18) | "音量键控制" 开关运行中改开即生效, `TouchRecorder` 自建的观察器随录制结束回收, 另一台设备补跑 T6 (8) 的 RootAutomator 冒烟; P3.6 修触摸屏节点解析 (`getevent -pl` 回退) 与主线程等待用户服务 | P3.1-P3.5 勾选, 本地提交; P3.3 无合适设备时登记 Q8 (2026-10-23 完成; (8) 在 AVD 补跑仍失败, 原因为既有的节点解析缺陷, Q8 已决为 D17 / D18); P3.6 AVD 不预设缓存 (8) PASS, 主线程不阻塞超过 1 s, XQ-AT72 无回归 | 1 (P3.6 与 P3.1-P3.5 同一会话) |

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
- 用户服务进程的内存构成 (P0.4, 附录 B.3, XQ-AT72 shell 身份, 单位 MB): (1) HEAD debug 包 RSS 209.8 / PSS 131.2, 其中 82.5 是 APK 里 38 个压缩存放的 `classesN.dex` 被运行时 "extracted in memory" 到匿名内存. 原因是 Android 12 上 `adb install` 的 debug 包安装时没有 dexopt (`dumpsys package` 为 `status=run-from-apk`, 原包 5835 同样); 正式版 v6.6.4 用同一命令安装则立即 dexopt (`status=verify, reason=install`). (2) 对 debug 包执行 `cmd package compile -m speed-profile -f org.autojs.autojs6` (没有 profile, 实得 `verify`; vdex 80.7, odex 0.5, shell 按文件名可读, 不必删除) 后 RSS 143.6 / PSS 49.6, `.dex mmap` 私有脏页 82.9 -> 0.4, 解压的 dex 为 0; 余下的 RSS 多为 vdex 的干净文件页 (可共享, 可回收). (3) v6.6.4 正式版的服务进程 RSS 130.1 / PSS 39.5 (10 轮里每个约 126, 与报告者截图的 114-115 同量级). (4) 三者都有 20.0 的 boot image 私有脏页 (`.art mmap`, dumpsys 把它计入 Java 堆; app_process 不经 zygote 启动, 不能共享), 真正的 Java 堆与 native 合计不到 5. G8441 (root, Android 9) 上 debug 5835 RSS 约 65 / PSS 约 22, `.dex mmap` 约 5 且几乎全为干净页 (安装时已 dexopt). 结论: 正式版用户的服务进程已没有可由宿主削减的大块, 报告者的问题来自进程数量 (P1.1 / P1.2); P1.4 的形态见附录 C Q3.
- 宿主启动即绑定用户服务 (更正 1.3 第 2 条): 开启 "音量键控制" 时 `App.onCreate` 的 `GlobalKeyObserver.initIfNeeded` 在 1200 ms 后调用 `InputEventObserver.ensureObservedAsync`, Shizuku 可用时进 `observeByShizuku`, 线程 "InputEventObserver-Shizuku" 循环执行 `WrappedShizuku.execCommand("getevent -t -c 1")`. G8441 日志: 宿主 `App onCreate` 后 1.26 秒即新建服务记录. 所以报告者场景 (Tasker 定时唤醒宿主) 即使脚本不用 Shizuku, 每次唤醒也会拉起用户服务.
- 第二类残留 (P1.1 范围): 宿主被强停时, 用户服务正在执行的 `getevent -t -c 1` (`ProcessShell.execCommand` 的 `Runtime.exec("sh")` 及其子进程) 随服务死亡被 init 收养, 直到下一个实体输入事件才退出 (`input` 注入的事件不经 evdev, 不能让它退出). G8441 (root) 静置一晚累积 32 对 `sh` + `getevent`, RSS 合计约 115 MB (多为共享页). 探针把它记为 stray (不参与判定), shell 身份的会被探针结束.
- 10 秒超时重绑 (P0.3(d)) 在 G8441 装包后首次启动时自然发生: 新装 APK 冷加载 dex, 第一个服务进程从启动到构造约 19 秒, 宿主 10 秒后 `unbindUserService(remove = true)` 再绑, 第二个进程启动; 两者同时构造完成后, 记录已被移除的旧进程交付 binder 失败, 启动器 `System.exit(1)` 退出, 新进程正常服务; Shizuku 30 秒后报旧记录 "is not started in 30000 ms". 结果: 无永久残留, 约 19 秒内两个服务进程 (各约 65 MB) 并存. 日志摘录在共享笔记 `shizuku-oom-p0-g8441-rebind.md`.
- 负对照里宿主崩溃 (P0.2, 附录 C Q4): v6.6.4 覆盖装在 6.8.0 的数据上, 宿主初始化时抛 `SQLiteException: Can't downgrade database from version 5 to 3` 崩溃, 系统随即重启宿主 (每轮最多 3 次, 之后不再重启, 直到探针强停). v6.6.4 在宿主进程初始化 (`handleBindApplication`) 期间同步调用 `WrappedShizuku.execCommand` 等待用户服务 (日志 "Unable to use Shizuku service", 5 秒), 所以每个宿主进程都拉起一个服务, 崩溃与强停后都不退出: 每轮残留 +1 到 +3, 测试脚本的标记文件一轮也没写出. 堆积机制与报告者相同 (服务不随宿主死亡退出), 堆积速度等于宿主进程的启动次数. 没有在干净数据上重跑 (需要清宿主数据).
- 打包应用的降级崩溃循环 (P0.3(c) 附带发现, 附录 C Q7, D16 不在本主题处理): XQ-AT72 上他人安装的 `org.autojs.autojs6.inrt` 在 Shizuku 服务端重启后被唤醒, 初始化时数据库 4 -> 3 降级崩溃, 系统反复重启 (P0 期间 123 次, `am force-stop` 止住). 与上一条的宿主崩溃同类 (旧版本装在新版本数据上). 维护者另立主题 db-downgrade 排查; 该机上的 inrt 包保留作复现样本, 不要卸载. 摘录在共享笔记 `shizuku-oom-p0-xq-at72-findings.md`.
- P1 附带发现, 修前即有 (附录 C Q6, D15 在 P3 修复): (1) 宿主启动时 "音量键控制" (运行与录制两项) 都关闭, `App.onCreate` 的 `GlobalKeyObserver.initIfNeeded` 不创建单例, 设置变化的监听又挂在单例上, 之后打开开关无人响应, 只有悬浮按钮菜单 (`CircularMenu`) 会创建单例, 否则要重启宿主; (2) `TouchRecorder(Context)` 在构造时自建 `InputEventObserver` 并立即 `observe()`, 而 `GlobalActionRecorder` 单例复用该 `TouchRecorder`, 所以自第一次录制起, 它的 getevent 进程 (Shizuku 或 Root) 一直运行到宿主死亡 (自 6.6.0; P1.1 之后由用户服务在宿主死亡时结束).

---

## 2. 固定决策 (2026-10-22 三轮选择题, 维护者确认)

| 编号 | 决策 | 来源 |
| --- | --- | --- |
| D1 | 路线图位于 `docs/shizuku-oom/ROADMAP.md`; 证据 `docs/shizuku-oom/evidence/`; 探针脚本 `tools/shizuku-oom/` | Q1 |
| D2 | 范围: 验证已发布修复 + 边界路径探针 + destroy 前清理子进程 + 用户服务自守护 + 用户服务进程瘦身 | Q2 |
| D3 | 负对照用 GitHub Release v6.6.4 APK (维护者签名, 修复前最后一个正式版) 跑同一探针, 证明探针能检出堆积; 测后装回原包 | Q3 |
| D4 | 设备: XQ-AT72 (shell 身份, Android 12) 主测跑全部探针; G8441 (root 身份, Android 9) 复核 P0.1 与 P2.1; 按锁表排队 | Q4 |
| D5 | 自守护: `IUserService` 新增 `attachHost(IBinder)`, 宿主连上后立即附着, 服务对其 `linkToDeath`, 宿主死亡即清理子进程并退出; 启动后 30 秒无宿主附着也退出; `version(VERSION_CODE + 5)` 改为 `+ 6` 让旧服务被替换 | Q5 |
| D6 | (2026-10-22 Q2: App.onCreate 不在用户服务进程运行, 40 MB 目标作废; 已被 D11 取代) 瘦身: `App.onCreate` 仿照 `:crash_report` 增加用户服务进程分支, 只做 `UserService` 需要的初始化; 验收 RSS 从约 115 MB 降到 40 MB 以下且 7 项回归通过; 降不到就登记实测值请维护者再定 | Q6 |
| D7 | issue #474 的回复与关闭由维护者自行处理, 子会话不写回复草稿; 本文与证据是回复素材 | Q7 |
| D8 | 第一批只发一个子会话做完 P0; P1 按 P0 结果发 | Q8 |
| D9 | 列入 6.8.0 发布门 (`docs/dev/6.8.0-release-gate.md`): P0 + P1.1 / P1.2 完成并 P2.1 回归通过前不发 6.8.0; P1.4 瘦身不在门内 | Q9 |
| D10 | 宿主启动即绑定与在途 getevent 孤儿: P1.1 清理在途 `execCommand` 进程, 验收加 "开启音量键控制时 force-stop 20 轮 stray 为 0"; 另新增 P1.5, 把 `InputEventObserver` 的 Shizuku 分支改为一个常驻 `newShellProcess` (client 令牌 linkToDeath, 宿主死即结束), T6 加 "音量键控制" 回归 | 附录 C Q1 (维护者选 B) |
| D11 | P1.4 前提不成立 (宿主 `App` 不在用户服务进程运行): D6 的 "`App.onCreate` 分支" 与 40 MB 目标作废; P0.4 加 oat 实验 (`cmd package compile` 后重测, 量 v6.6.4 正式包), P1.4 的形态按数字另出题 (附录 C Q3) | 附录 C Q2 (维护者选 A) |
| D12 | P1.4 取消代码改动, 结论留在 1.5 与 B.3, P2.2 不写 improvement 条目 | 附录 C Q3 (维护者选 A) |
| D13 | 负对照接受 v6.6.4 在 6.8.0 数据上崩溃的结果, B.1 保留崩溃注记 | 附录 C Q4 (维护者选 A) |
| D14 | T6 (8) 的 RootAutomator 冒烟换一台 "Shizuku 以 shell 身份运行且 shell 可写 `/dev/input`" 的设备补跑 (P3.3); 不改 SELinux, 不 root; 没有合适设备时不绕过, 登记待决 | 附录 C Q5 (维护者选 B, 非推荐项) |
| D15 | P1 的两处附带发现 (1.5 末条) 在本主题修掉: P3.1 "音量键控制" 运行中改开即创建 `GlobalKeyObserver`, P3.2 `TouchRecorder` 自建的 `InputEventObserver` 随录制结束回收; changelog 各加 fix (P3.4) | 附录 C Q6 (维护者选 B, 非推荐项) |
| D16 | inrt 数据库降级崩溃循环 (1.5) 转独立主题 db-downgrade (维护者另开 `/dispatch db-downgrade`), 本主题只记录不处理; XQ-AT72 上的 inrt 包保留作复现样本 | 附录 C Q7 (维护者选 C, 非推荐项) |
| D17 | 触摸屏节点解析: `/proc/bus/input/devices` 读不到 (Shizuku 以 shell 身份运行) 时改用 `getevent -pl` 按设备名找节点, 解析成功即缓存, 失败不缓存 (P3.6) | 附录 C Q8 (维护者选 A) |
| D18 | 主线程上不等待 Shizuku 用户服务: 解析移出主线程, 或用户服务未连上时不阻塞等待, 失败快速返回并提示 (P3.6) | 附录 C Q8b (与 Q8 同源, 维护者选 A) |

---

## 3. 派生技术决策 (可在推翻点之前推翻)

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 探针 `tools/shizuku-oom/zombie_probe.py` (py -3.12 -I): 参数 `--serial`, `--rounds N`, `--mode {force-stop, kill9, bind-kill:<秒>, update, server-restart, spawn-orphan}`, `--report-dir`; 每轮: 启动宿主并运行一个只调用 `shizuku('id')` 后 `exit()` 的最小脚本, 等服务进程出现, 按模式结束宿主, 等 3 秒, 统计 `ps -A -o PID,USER,RSS,NAME` 里 `shizuku-service-for-autojs6` 的进程数与 RSS 合计, 以及 `spawn-orphan` 模式下的 `sleep` 子进程; 输出 JSON + md, 每轮一行 (轮次, 残留数, RSS 合计), 末尾 PASS / FAIL | P0.1 完成 |
| T2 | 残留定义: 宿主被结束后 3 秒, 用户服务进程数应为 0 (daemon(false) 的服务随宿主退出); 负对照预期每轮 +1. 启动脚本的方式复用 `tools/memory/run_gate.py` 已验证的宿主脚本启动途径 (子会话核对), 不自造 | P0.1 完成 |
| T3 | 退出原因统一出口: `UserService.shutdown(reason)` 先结束登记的 `RemoteShellProcessHolder` 与在途 `execCommand` 进程 (`ProcessTree.terminate`), 再 `killProcess` + `exitProcess`; 打一行 `UserService exit: <reason>` (destroy / exit / host-died / no-host-attached), 探针用 logcat 核对原因 | P1.1 实现时 |
| T4 | `attachHost` 的宿主令牌由 `WrappedShizuku` 持有一个进程级 `Binder` (静态, 与宿主进程同生命周期); `onServiceConnected` 后立即调用; 旧宿主连新服务与新宿主连旧服务都被 version 替换排除, 不需要兼容分支. 30 秒看门狗用服务进程主线程 Handler 实现, 附着后取消 | P1.2 实现时 |
| T5 | (已推翻 (Q2), P1.4 形态待 Q3; 见 D11) 瘦身分支按进程名后缀前缀 `shizuku-service-for-` 判断 (常量从 `WrappedShizuku` 抽出, 打包应用 inrt 的后缀也以它开头); 分支里保留的初始化由子会话按 `UserService` 调用图确定 (至少: 字符串资源可用, `PrivilegedFileNative` 按请求加载, termexec 可加载), 其余全部跳过; 不改 `Application` 类 (Shizuku 固定用宿主的 `Application`) | P1.4 实现时 |
| T6 | 回归清单 (P2.1, 原 7 项, D10 加第 9 项): (1) `shizuku('id')` / `shizuku.execCommand`; (2) `shizuku.spawn` 常驻进程 + 脚本结束自动结束; (3) `new Shell({ adb: true })` 与 `pty: true`; (4) 文件管理器经 Shizuku 浏览受限目录并保存文本文件; (5) 无障碍服务用 Shizuku 自动启用; (6) 浮动按钮 "更多" 经 Shizuku 取 currentPackage / currentActivity; (7) KeyCode 经 Shizuku 发送键码 (issue #492); (8) 其余调用点各跑一次冒烟: RootAutomator 的 adb 模式, 指针位置工具, 插件中心启动器图标, AndroidIdReader, augment app 的 Shizuku 分支 (立项时 grep `WrappedShizuku.` 共 16 个文件, 子会话核对清单); (9) 音量键控制 (InputEventObserver 经 Shizuku 的 getevent; D10, P1.5 改动后: 开启后按音量键能停止脚本, 强停宿主后无 getevent 孤儿). (1)-(7) 与 (9) 在 XQ-AT72 (shell) 与 G8441 (root) 各跑一次, (8) 只在 XQ-AT72. P3 (D15) 加 (10) 运行中打开音量键控制 (P3.1) 与 (11) 录制后回收 getevent (P3.2), 在 XQ-AT72 跑 | P2.1 开始前 |
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

- [x] P0.1 探针脚本 `tools/shizuku-oom/zombie_probe.py` + `tools/shizuku-oom/README.md` (T1 / T2). 验收: 当前 HEAD 的 debug 构建在 XQ-AT72 跑 `--mode force-stop --rounds 20` 残留 0/20, G8441 跑 10 轮残留 0/10; 报告入 `docs/shizuku-oom/evidence/`, 数字入附录 B. 完成 (2026-10-22): XQ-AT72 HEAD debug 5935 20 轮残留 0/20 (`probe-XQ-AT72-5935-force-stop-20261022`), G8441 10 轮 0/10 (`probe-G8441-5935-force-stop-20261022`), 退出来源全部 destroy; 开启 "音量键控制" 的 XQ-AT72 每轮另有一对 getevent / sh 孤儿 (约 5 MB, 20/20 轮, 由 D10 / P1.1 / P1.5 处理).
- [x] P0.2 负对照 (D3): v6.6.4 发布包在 XQ-AT72 跑 `--mode force-stop --rounds 10`, 预期残留逐轮累加到约 10, 记录 RSS 合计; 测后装回原包. 验收: 报告显示堆积 (证明探针有效), 附录 B 有 "修复前" 列. 完成 (2026-10-22): GitHub Release v6.6.4 正式版 (versionCode 3274, 证书与设备上的宿主一致) 10 轮残留 2 -> 25 逐轮增长 (10/10 轮), RSS 合计 3144 MB, 每个约 126 MB (`probe-XQ-AT72-3274-force-stop-20261022-v664`); 结束时已结束这 25 个进程并装回原包 debug 5835 (md5 已核对). 宿主在 6.8.0 的数据上启动即因数据库降级崩溃, 每轮残留 +1 到 +3, 见 1.5 与附录 C Q4.
- [x] P0.3 边界路径探针 (HEAD 构建, XQ-AT72), 每条路径写残留数与 logcat 里的退出来源: (a) `bind-kill:0.2 / 0.5 / 1 / 2` 各 5 轮 (宿主在服务启动完成前后被杀); (b) `update`: 两个构建号不同的 debug 包先后覆盖安装, 旧服务应因 version 不同被替换并退出; (c) `server-restart`: 用 start.sh 重启 Shizuku 服务端, 旧用户服务应随服务端死亡退出; (d) 10 秒超时重绑路径: 代码审读 + 能构造则实测 (例如让服务启动变慢), 否则记 "未实测, 由 P1.2 覆盖"; (e) `spawn-orphan`: 脚本 `shizuku.spawn('sleep 600')` 后 force-stop 宿主, 检查 `sleep` / `sh` 是否残留 (预期现状残留, 作 P1.1 的修前基线). 验收: 5 条路径各有结论与数字 (附录 B). 完成 (2026-10-22, XQ-AT72 HEAD debug 5935 / 5936): (a) / (b) / (c) 用户服务残留均为 0, (d) 无永久残留, (e) 服务残留 0 但每轮留下 `sleep 600` 1 个 (修前基线, P1.1 对照); 详见附录 B.2.
- [x] P0.4 用户服务进程内存画像 (瘦身基线): `dumpsys meminfo <pid>` 的 PSS / Java 堆 / native / code, 线程数 (`/proc/<pid>/task`), logcat 里 `App.onCreate` 各项在该进程的执行痕迹 (哪些跑了); 写附录 B "瘦身前" 列. 验收: 数字齐全, 并列出建议跳过的初始化项供 P1.4 用. 完成 (2026-10-22): 数字见附录 B.3 (debug 无 oat, debug 编译后, v6.6.4 正式版, G8441 参照) 与 1.5; 建议跳过的初始化项: 无, 宿主 `App` 不在该进程运行 (1.5), 原 P1.4 作废 (D11), 形态待附录 C Q3.

### P1 修复与加固 (D5 / D10 / D11, 代码改动; 验证需 XQ-AT72)

- [x] P1.1 destroy / exit 前清理子进程 (T3): `UserService` 登记存活的 `RemoteShellProcessHolder` (holder destroy 时注销) 与在途 `execCommand` 进程, `shutdown(reason)` 统一出口. 验收: P0.3(e) 探针残留 0 (sleep / sh 均无); 开启 "音量键控制" 时 XQ-AT72 `force-stop` 20 轮探针 stray 为 0 (D10, G8441 root 身份在 P2.1 复核); JVM 单测覆盖清理顺序. 进度 (2026-10-23, shizuku-oom-p1): 代码完成 (`UserService.shutdown`, `UserServiceLifecycle`, holder 登记 / 注销, `UserServiceLifecycleTest` 9 例); G8441 (root) 首轮 spawn-orphan 暴露竞争: 宿主死亡时 client 死亡回调, `onHostDied` 与 Shizuku 的 destroy 同时结束 holder, 原 `compareAndSet` 让后到的调用不等进程树结束就返回, 服务先退出, root 身份的 `sleep 600` 被留下; 改为 `UserServiceLifecycle.Once` (后到的调用等第一次结束完成) 并补单测后 spawn-orphan 5/5 PASS (子进程与 `sleep 600` 均 0), force-stop 20 轮 stray 0. 完成 (2026-10-23 06:30): XQ-AT72 (shell) spawn-orphan 5/5 PASS (服务与子进程均 0, 退出原因 host-died 5), 开启 "音量键控制" 的 `force-stop --rounds 20 --stray-fails` 残留 0/20, stray 0 (`probe-XQ-AT72-5935-*-20261023-p1`).
- [x] P1.2 用户服务自守护 (D5 / T4 / T7): AIDL `attachHost(IBinder)`, 宿主 `onServiceConnected` 后附着, 宿主死亡 -> `shutdown(host-died)`; 30 秒无附着 -> `shutdown(no-host-attached)`; `version(+ 6)`. 验收: JVM 单测 4 例通过; XQ-AT72 `force-stop` 20 轮与 `bind-kill` 各档残留 0, logcat 退出原因为 host-died 或 destroy (竞态任一即可); (d) 路径如 P0.3 能实测则残留 0. 进度 (2026-10-23): 代码完成 (`attachHost`, 30 秒看门狗, `version` 5935 + 6); G8441 (root) force-stop 20 轮残留 0/20, 退出原因 host-died 18 / destroy 2. 完成 (2026-10-23 06:30): XQ-AT72 (shell) force-stop 20 轮残留 0/20, 退出原因 host-died 20, 单个服务 RSS 约 211 MB (adb 安装的 debug 包未 dexopt, 见 B.3); bind-kill 0.2 / 0.5 / 1 / 2 s 各 5 轮残留 0, stray 0; 宿主在服务交付 binder 前被杀的轮次 (0.2 / 0.5 / 1 s 全部, 2 s 3 轮) 记 exited, 即启动器交付失败后 `System.exit`, 与 P0.3(a) 相同, 服务从未附着, 不需要看门狗; 其余 2 轮为 destroy. (d) 路径见 B.2.
- [x] P1.3 宿主侧 10 秒超时重绑 (`connectedService`): 按 P0.3(d) 结论处置; 默认不改代码 (P1.2 的看门狗已覆盖被提前移除记录的进程), 在附录 A 记理由. 完成 (2026-10-23): 不改代码, 理由见附录 A "P1.3 处置" 行.
- [x] P1.4 用户服务进程内存 (D11 / D12, 不在发布门内): 已取消 (D12). 正式版安装时即 dexopt, 用户服务 RSS 约 130 / PSS 约 40 MB, 其中 20 MB 为宿主无法消除的 boot image 私有页; 只有安装时跳过 dexopt 的 adb debug 包是 RSS 210 / PSS 131 MB. 结论留在 1.5 与附录 B.3, 不改代码, P2.2 不写 improvement 条目.
- [x] P1.5 `InputEventObserver` 的 Shizuku 分支改为常驻进程 (D10): 不再每个按键事件经 `execCommand` 起一对 `sh` + `getevent -t -c 1`, 改为一个常驻 `newShellProcess` 持续读 `getevent -t` 的输出, 宿主侧 client 令牌 linkToDeath (宿主死即由 P1.1 的登记结束该进程); 关闭 "音量键控制" 或脚本引擎退出时结束. 验收: 开启 "音量键控制" 时按键事件仍被观察到, 音量键能停止脚本; XQ-AT72 `force-stop` 20 轮 stray 为 0; T6 (9) 通过. 进度 (2026-10-23): 代码完成 (`getevent -t` 经 pty 常驻, 因为输出到管道时块缓冲, 按键事件会滞留; 关闭 "音量键控制" 时不再启动); G8441 (root) volume-key 5/5 PASS (服务下 getevent 1, 音量加键 0.3-0.6 s 停止脚本, stray 0, 退出原因 host-died), T6 (9) 通过. 完成 (2026-10-23 06:38): XQ-AT72 (shell) volume-key 5/5 PASS (服务下 getevent 1, 脚本均停止, 用时上限约 0.6 s, 残留 0, stray 0, 退出原因 host-died 5), force-stop 20 轮 stray 0 (见 P1.1), T6 (9) 通过 (0.66 s). 该机 SELinux 不允许 shell 写 `/dev/input` (`sendevent` 报 Permission denied), 探针改为经系统 `hid` 工具 (`/dev/uhid`) 注册虚拟消费类控制设备发送音量加 (报告 `keyInjection` = `uhid`), 宿主的 getevent 同样从新节点读到 KEY_VOLUMEUP; 因为该方式在 `hid` 命令结束后才开始计时, 用时是上限.

### P2 回归, changelog 与发布门 (D7 / D9)

- [x] P2.1 T6 回归在 XQ-AT72 (shell) 与 G8441 (root) 各跑一遍, 结果表入 `docs/shizuku-oom/evidence/regression-<日期>.md`. 验收: (1)-(7) 与 (9) 的 16 格与 (8) 冒烟全部通过. 进度 (2026-10-23): 脚本 `tools/shizuku-oom/regression.py`; G8441 (root) (1)-(7) 与 (9) 8 格通过, 结束后用户服务与 stray 均 0 (`regression-20261023-p1`); (4) 的 `PrivilegedEditorSaveDeviceTest` 需 API 29, 在 Android 9 上被 assumption 跳过, 其余 3 例通过; (7) 该机静音加勿扰, 音量不变, 以 `dumpsys audio` 音量日志里的 `ADJUST_LOWER` 请求判定送达. 完成 (2026-10-23 06:38): XQ-AT72 (shell) (1)-(9) 全部 PASS, 结束后用户服务与 stray 均 0; 两台合计 (1)-(7) 与 (9) 16 格与 (8) 冒烟通过, 汇总 `evidence/regression-20261023-p1.md`. (8) 中 RootAutomator 记为不适用: 该机 SELinux 不允许 shell 身份写任何 `/dev/input` 节点 (`test -w` 失败), RootAutomator (自 6.7.0 未改) 如实报告 "cannot access a writable input device", 其余 4 个调用点 (指针位置, 启动器图标命令, AndroidIdReader, app 模块 Shizuku 分支) 通过; 另找设备覆盖与否见附录 C Q5.
- [x] P2.2 changelog: `.changelog/lang_zh-Hans.json` 6.8.0 段加 `fix` 1 条 (用户服务进程随宿主退出并清理其启动的子进程, 引用 issue #474) 与 `improvement` 1 条 (内容按 Q3 拍板的 P1.4 形态, 仅在 P1.4 有改动时), 用 `.python/generate_markdown.py` 生成 zh-Hans 的 markdown; 其他语言由维护者同步. 验收: JSON 解析通过, 生成文件只含本条变化. 进度 (2026-10-23): fix 1 条已加 (无 improvement, D12); 生成脚本没有按语言生成的选项, 用其渲染函数算出 zh-Hans 的 4 个产物 (`README.md`, `.readme/README-zh-Hans.md`, `app/src/main/assets-app/doc/CHANGELOG.md` 与 `CHANGELOG-zh-Hans.md`), 只把本条一行写入 (这些文件另有其他会话 JSON 已改但尚未生成的条目, 不代为生成). 完成: 随 `7512f559e` 提交 (只暂存本条 hunk).
- [x] P2.3 发布门: `docs/dev/6.8.0-release-gate.md` 追加一行 (来源本文 D9, 复核方法 = P0.1 探针 + P2.1 回归, 证据 = evidence 报告). 验收: 该行存在且状态写明. 完成 (2026-10-23): 状态为 "已满足 (2026-10-23, 宿主 debug 5935 + P1)", 剩余为发布候选复核.
- [x] P2.4 收尾: 本文勾选与附录回填, 本地提交 (不推送), 看板 `../.claude/boards/shizuku-oom.md` 子会话结果区追加结论; issue 回复由维护者处理 (D7). 完成 (2026-10-23, shizuku-oom-p1): 提交 `7512f559e` (代码与 changelog), `4d67b9074` (回归脚本与 G8441 证据), 以及其后一笔 XQ-AT72 证据与本文回填 (hash 见看板).

### P3 附带发现与 T6 (8) 补跑 (D14 / D15 / D16 / D17 / D18; 子会话 shizuku-oom-p3, 2026-10-23)

- [x] P3.1 "音量键控制" 开关在宿主运行中从关改开时即创建 `GlobalKeyObserver` (不再要求重启宿主): `initIfNeeded` 在两项设置都关闭时登记一次性的设置监听, 任一项打开即创建单例 (此时不再等 1.2 秒的冷启动延迟); 改关时由单例自己的监听停止 shell 兜底监听 (与 P1.5 的启停一致). 验收: XQ-AT72 上宿主启动时两项开关为关, 打开后不重启即可用音量键停止脚本 (`regression.py` 新增 (10)), 关闭后服务下的 getevent 进程结束; JVM 单测覆盖创建条件. (进度 2026-10-23: 代码 (`VolumeControlSettings`, `GlobalKeyObserver.initIfNeeded`) 与 `VolumeControlSettingsTest` 7 例已完成并本地提交, `:app:assembleAppDebug` 通过. 2026-10-23 验收: XQ-AT72 (10) PASS, 两项都关时冷启动服务下 getevent 0, 打开 "运行时音量键控制" 后 2.0 s 出现 1 个 (日志确认由开关触发创建单例), 音量加键 (uhid) 0.62 s 停止脚本, 关闭后 0.1 s 内回到 0; 附录 B.5.)
- [x] P3.2 `TouchRecorder(Context)` 自建的 `InputEventObserver` 在录制开始时启动, 录制停止时回收 (结束其 getevent 进程, 不再等宿主死亡); 复用同一 `TouchRecorder` 的下一次录制重新启动. `TouchRecorder` 由 `GlobalActionRecorder` 进程级单例持有, 不归属任何 `ScriptRuntime`, 不登记 `RuntimeResourceScope`. 验收: 录制一次并结束后服务下无 getevent, `ps -A` 无残留 getevent / sh (`regression.py` 新增 (11)); `StaticReferenceGuardTest` 通过; 不改录制功能的行为. (进度 2026-10-23: 代码已完成并本地提交, `StaticReferenceGuardTest` / `UserServiceLifecycleTest` 通过. 2026-10-23 验收: XQ-AT72 (11) PASS, 两轮录制中服务下 getevent 为基线 + 1 (1 -> 2, 基线是音量键控制自己的那个), 丢弃录制后 0.1-0.2 s 回到 1, 结束后用户服务 0, stray 0; 附录 B.5.)
- [x] P3.3 Q5 补跑 (D14): 在 `../COMMON_AGENTS.md` 可用测试设备里找 Shizuku 以 shell 身份运行且 shell 能写 `/dev/input` 的设备 (先只读检查 `getenforce` 与 `ls -l /dev/input`, 以 shell 身份 `test -w` 试探; 不改 SELinux, 不 root), 按锁表登记后装当前 HEAD 的 debug 构建跑 `regression.py --items 8`, 结果入 `evidence/` 并回填附录 B; 测后装回原包. 没有任何设备满足时不绕过, 登记待决 Q8 (选项: 回到 Q5 的 A / 维护者另备设备) 并汇报. (2026-10-23 完成: 6 台真机都不满足 (附录 B.4), 改用临时 AVD (Android 9, Shizuku 以 shell 身份, shell 可写 2 个节点) 补跑: (8) FAIL, 根因是 `InputDevices.touchDeviceId` 经 Shizuku 读 `/proc/bus/input/devices`, 而 shell 身份读不了它, 触摸屏节点为空; 预设节点缓存的对照为 PASS. 6.7.0 起即有的缺陷, 登记 Q8.)
- [x] P3.4 changelog: `.changelog/lang_zh-Hans.json` 6.8.0 段 fix 为 P3.1 / P3.2 各加 1 条 (或按 AGENTS.md 粒度合并), 只生成 zh-Hans 的 markdown 且只写入本条; 该文件有他人未提交改动, 只暂存自己的 hunk. (2026-10-23 完成: 两条独立的 fix 紧接 issue #474 那条; 不带 issue 引用, 两处都不是 #474 报告的现象. 只重渲染 zh-Hans 的 CHANGELOG-zh-Hans.md / CHANGELOG.md / README-zh-Hans.md / README.md, 其他 9 种语言未写入, 待维护者同步翻译.)
- [x] P3.5 回归与收尾: XQ-AT72 跑 `zombie_probe.py --mode force-stop --rounds 20` 与 `regression.py` 的 (9) 及新用例 (10) / (11), 全部 0 残留 / PASS; G8441 只在改动触及 root 路径时复跑; 本文勾选与附录回填, 发布门行按需更新 (只追加自己的 hunk), 本地提交, 看板追加结论. (2026-10-23 完成: XQ-AT72 装本主题 P3 构建 debug 5935, force-stop 20 轮残留 0/20 (host-died 20, stray 0), (9) / (10) / (11) 全部 PASS (附录 B.1 / B.5); 测后装回原包 5835 (md5 d4994f77). G8441 不复跑: P3.1 只改单例的创建时机, P3.2 只在录制停止时调用已有的 `InputEventObserver.recycle`, Root `Shell` 路径的代码未改, 且 G8441 的 Shizuku 为 root 身份, 观察器走 Shizuku 分支, 复跑也覆盖不到 Root `Shell`. AVD 预检时 (11) 暴露两处脚本问题并已修正: 宿主刚启动时开始录制在主线程上等待用户服务超过 10 s (见 Q8), 基线在音量键控制的观察器启动前取到; 现在脚本先等用户服务, 取稳定后的基线, 主线程每步最多等 30 s 并记录耗时.)
- [ ] P3.6 触摸屏节点解析与主线程等待 (D17 / D18, Q8 / Q8b): `InputDevices.touchDeviceId` 读不到 `/proc/bus/input/devices` 时改用 `getevent -pl` 按设备名找节点, 解析成功即缓存, 两者都失败时返回不可用且不缓存; 主线程上不等待 Shizuku 用户服务 (未连上时快速失败并提示, 不再等 10 s + 重绑 10 s). 验收: JVM 单测覆盖解析三种情况 (`/proc` 成功; `/proc` 被拒 + `getevent -pl` 成功; 两者都失败); AVD `shizukuoom_p33_api28` 清掉预设缓存后不预设复跑 `regression.py --items 8` PASS; 宿主启动后立即开始录制时主线程不阻塞超过 1 s (`regression.py` 新用例); changelog zh-Hans 6.8.0 段 fix 1 条; XQ-AT72 复跑 T6 (9) / (10) / (11) 与 force-stop 20 轮无回归; 发布门 Shizuku 行与附录 B.4 追加结果; 完成后删除临时 AVD. (进度 2026-10-23: 代码 (`InputDevices.touchDeviceId` 的 `getevent -pl` 回退, `RootAutomatorEngine` 只缓存解析成功的节点, `WrappedShizuku` 主线程上不等待也不重绑) 与 `InputDevicesTouchDeviceTest` 4 例已完成, `:app:assembleAppDebug` 通过; `regression.py` 新增 (12). 实现中的两处发现: Shizuku 13.1.5 的 `ShizukuServiceConnection.connected` 把 `onServiceConnected` 投递到主线程, 所以主线程上等待用户服务连上必然等满超时, 只能不等; 经用户服务执行命令时 `AbstractShell.Result` 在标准错误非空时把退出码 0 改为 1, 而 `getevent -p` 总会为 `/dev/input/mice` 输出一行错误, 所以按标准输出是否为空判断 (AVD 首跑因按退出码判断而 (8) / (12) FAIL, 改后 PASS). AVD 验收: 缓存为 -1 时 (8) PASS (节点 event1 由 `getevent -pl` 解析并缓存), (12) PASS (用户服务未连上时主线程每步 [462, 23] ms, 之后在非主线程 443 ms 解析出 event1); 附录 B.4.)

---

## 附录 A 决策记录

| 编号 | 时间 | 事项 | 结果 |
| --- | --- | --- | --- |
| D1-D4 | 2026-10-22 第 1 轮 | 位置, 范围, 负对照, 设备 | 见第 2 节 |
| D5-D8 | 2026-10-22 第 2 轮 | 自守护设计, 瘦身程度, issue 处理, 首批分配 | 见第 2 节 |
| D9 | 2026-10-22 第 3 轮 | 6.8.0 发布门 | 列入 (P0 + P1.1 / P1.2 + P2.1) |
| D10 | 2026-10-22 22:40 (附录 C Q1) | 启动即绑定与在途 getevent 孤儿 | 选 B: P1.1 清理在途进程 + stray 0 验收, 新增 P1.5, T6 加第 9 项 |
| D11 | 2026-10-22 22:40 (附录 C Q2) | P1.4 前提不成立 | 选 A: P0.4 加 oat 实验与 v6.6.4 测量, D6 的 40 MB 目标作废, 形态待 Q3 |
| D12 | 2026-10-23 00:20 (附录 C Q3) | P1.4 的最终形态 | 选 A: 取消 P1.4 的代码改动, 结论留在 1.5 与 B.3, P2.2 不写 improvement 条目 |
| D13 | 2026-10-23 00:20 (附录 C Q4) | 负对照里宿主崩溃 | 选 A: 保持现状, 接受 P0.2 结果, B.1 保留崩溃注记 |
| D14 | 2026-10-23 06:50 (附录 C Q5) | T6 (8) RootAutomator 在 XQ-AT72 不可测 | 选 B: 换机补跑 (P3.3) |
| D15 | 2026-10-23 06:50 (附录 C Q6) | P1 的两处附带发现 | 选 B: 本主题新增 P3.1 / P3.2 修掉 |
| D16 | 2026-10-23 06:50 (附录 C Q7) | inrt 数据库降级崩溃循环 | 选 C: 转独立主题 db-downgrade, 本主题只在 1.5 记录 |
| D17 | 2026-10-23 10:45 (附录 C Q8) | RootAutomator 的 Shizuku 后端找不到触摸屏节点 | 选 A: 新增 P3.6, `/proc` 读不到时改用 `getevent -pl`, 成功即缓存 |
| D18 | 2026-10-23 10:45 (附录 C Q8b) | 开始录制时主线程等待用户服务 (ANR 风险) | 选 A: 并入 P3.6, 主线程上不阻塞等待, 失败快速返回并提示 |
| P1.3 处置 | 2026-10-23 (子会话 shizuku-oom-p1) | 宿主侧 10 秒超时重绑 (`connectedService`) | 不改代码. 理由: (1) P0.3(d) 在 G8441 自然复现, 被移除记录的旧进程交付 binder 失败后由启动器 `System.exit(1)` 退出, 无永久残留 (B.2); (2) 若旧进程已交付 binder 但宿主已换绑新服务, 它收不到宿主附着, 由 P1.2 的 30 秒看门狗 (`no-host-attached`) 结束; 已附着的则随宿主死亡 (`host-died`) 结束; (3) 改为更长的等待会让首次冷加载慢于 10 秒的设备 (G8441 约 19 秒) 多等, 收益只是少一个短暂并存的进程 |

## 附录 B 证据与数字 (P0 回填, 之后每阶段追加一列)

### B.1 报告者场景 (force-stop 循环)

| 构建 | 设备 | 轮数 | 残留进程数 | RSS 合计 | 报告 |
| --- | --- | --- | --- | --- | --- |
| 报告者截图 (版本未知, 2025-12) | 未知 (11.3 GB, 8 核) | - | 22 | 约 2.5 GB (114-115 MB 每个) | issue #474 截图 |
| v6.6.4 正式版 3274 (修复前, P0.2) | XQ-AT72 | 10 | 逐轮 2 / 5 / 8 / 9 / 12 / 15 / 18 / 21 / 22 / 25 (宿主在 6.8.0 数据上启动即崩溃, 每轮被系统重启最多 3 次, 见 1.5) | 3144 MB (每个约 126 MB) | `probe-XQ-AT72-3274-force-stop-20261022-v664` |
| HEAD debug 5935 (P0.1) | XQ-AT72 | 20 | 0/20 (每轮退出来源 destroy) | 0 (单个服务约 208 MB; 另每轮 getevent / sh 孤儿一对约 5 MB, 探针以 shell 身份结束) | `probe-XQ-AT72-5935-force-stop-20261022` |
| HEAD debug 5935 (P0.1) | G8441 (root) | 10 | 0/10 (每轮退出来源 destroy) | 0 (单个服务约 65.5 MB) | `probe-G8441-5935-force-stop-20261022` |
| P1 后 debug 5935 (本会话构建) | XQ-AT72 | 20 | 0/20 (退出原因 host-died 20), 开启音量键控制, stray 0 | 0 (单个服务约 211 MB, adb 安装未 dexopt) | `probe-XQ-AT72-5935-force-stop-20261023-p1` |
| P1 后 debug 5935 (本会话构建) | G8441 (root) | 20 | 0/20 (退出原因 host-died 18 / destroy 2), stray 0 | 0 (单个服务约 67.6 MB) | `probe-G8441-5935-force-stop-20261023-p1` |
| P3 后 debug 5935 (含 `61dc4f7cf`) | XQ-AT72 | 20 | 0/20 (退出原因 host-died 20), stray 0 | 0 | `probe-XQ-AT72-5935-force-stop-20261023-p3` |

### B.2 边界路径 (P0.3, HEAD 构建; P1 后复测同表追加列)

| 路径 | 轮数 | 残留 | logcat 退出来源 | 结论 | P1 后 |
| --- | --- | --- | --- | --- | --- |
| (a) bind-kill 0.2 / 0.5 / 1 / 2 s | 各 5 | 0/20 | 0.2 / 0.5 / 1 s 共 15 轮均为 exited (宿主被杀时都未收到 `onServiceConnected`, 无 destroy 行); 2 s 为 destroy 3 / exited 2; 缓冲区内可见的 exited 为启动器交付 binder 失败后 `System.exit(1)` (`VM exiting with result code 1`) | 无残留: 服务交付 binder 前被杀走启动器退出, 交付后被杀走 destroy (`probe-XQ-AT72-5935-bind-kill-{0.2,0.5,1,2}-20261022`) | XQ-AT72: 各档 0/5, stray 0; 0.2 / 0.5 / 1 s 全部 exited, 2 s 为 destroy 2 / exited 3, 与修前相同 (`probe-XQ-AT72-5935-bind-kill-{0.2,0.5,1,2}-20261023-p1`) |
| (b) update (version 替换) | 3 | 0/3 (覆盖安装后 0, 强停后 0) | 旧服务 destroy (覆盖安装强停宿主, 连接死亡) | 无残留; 每轮另有 getevent / sh 两对 (安装与最后强停各一对, 见 D10) (`probe-XQ-AT72-5936-update-20261022`, 5935 -> 5936) | 不复测 (P1 验收不含) |
| (c) server-restart | 3 | 0/3 (重启服务端后 0, 强停后 0) | exited: 服务端死亡后启动器 `System.exit(0)` (`VM exiting with result code 0`) | 无残留 (`probe-XQ-AT72-5935-server-restart-20261022`) | 不复测 (P1 验收不含; 会唤醒 inrt 进入崩溃循环) |
| (d) 10 秒超时重绑 | 1 (G8441 自然发生) | 0 | 旧进程: 交付 binder 失败, 启动器 `System.exit(1)` (`VM exiting with result code 1`); 新进程正常服务 | 代码审读 + G8441 装包后首次启动自然复现 (冷加载约 19 秒 > 10 秒): 无永久残留, 约 19 秒两个服务进程并存; 支持 P1.3 默认不改代码 (1.5) | G8441 装 P1 构建后首次启动同样拉起多个服务进程, 未附着的由启动器退出 (spawn-orphan 第 1 轮记 exited), 无残留 |
| (e) spawn-orphan (sleep 600) | 5 | 服务 0/5; 子进程每轮 3 个 (`sleep 600` 1 个, getevent / sh 一对), 约 8 MB | 服务 destroy; 它启动的子进程不随之结束, 被 init 收养 | 修前基线 FAIL (预期), 作 P1.1 对照; 探针以 shell 身份结束了这些子进程 (`probe-XQ-AT72-5935-spawn-orphan-20261022`) | G8441 (root): 5/5 PASS, 服务 0, 子进程与 `sleep 600` 均 0, 退出原因 host-died 4 / exited 1 (`probe-G8441-5935-spawn-orphan-20261023-p1`); XQ-AT72 (shell): 5/5 PASS, 服务与子进程均 0, 退出原因 host-died 5 (`probe-XQ-AT72-5935-spawn-orphan-20261023-p1`) |

### B.3 用户服务进程内存画像 (P0.4 / P1.4)

单位 MB; 数字取探针 `--profile` 第 1 轮 (结束宿主前) 的 `dumpsys meminfo`. Java 堆含 `.art mmap` 的 boot image 私有脏页 20.0 (XQ-AT72 三次测量相同).

| 构建与 oat 状态 | 设备 (身份) | RSS | PSS | Java 堆 | native | `.dex mmap` PSS / 私有脏页 | 线程数 | 跑了哪些初始化 | 报告 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| HEAD debug 5935, 安装时未 dexopt (`run-from-apk`) | XQ-AT72 (shell) | 209.8 | 131.2 | 20.4 | 4.8 | 83.6 / 82.9 (38 个 dex 解压到匿名内存, 共 82.5) | 18 | 无 (`App attachBaseContext` / `onCreate` 日志 0 行) | `probe-XQ-AT72-5935-force-stop-20261022` |
| HEAD debug 5935, `cmd package compile -m speed-profile -f` 后 (`verify`, vdex 80.7, shell 可读) | XQ-AT72 (shell) | 143.6 | 49.6 | 20.3 | 3.6 | 10.2 / 0.4 (解压 0) | 18 | 无 | `probe-XQ-AT72-5935-force-stop-20261022-oat-speed-profile` |
| v6.6.4 正式版 3274, 安装时 dexopt (`verify`, reason install) | XQ-AT72 (shell) | 130.1 | 39.5 | 20.1 | 3.5 | 2.1 / 0.4 (解压 0) | 16 | 无 | `probe-XQ-AT72-3274-force-stop-20261022-v664` |
| debug 5835 (参照, 只读测量) | G8441 (root) | 约 65 | 约 22 | - | - | 约 5 (几乎全为干净页) | - | 无 | 1.5; 5935 每轮服务 RSS 65.5 见 `probe-G8441-5935-force-stop-20261022` |
| P1.4 后 | - | 已取消 (D12), 不复测 | | | | | | | |

### B.4 T6 (8) RootAutomator 补跑 (P3.3, D14)

设备普查 (2026-10-23, 只读; `test -w` 以 shell 身份经 adb 执行, 与 Shizuku shell 身份的 SELinux 域相同):

| 设备 | Android | Shizuku 服务端身份 | shell 可写的 `/dev/input` 节点 | 结论 |
| --- | --- | --- | --- | --- |
| XQ-AT72 (QV710AF65F) | 12 | shell | 0 | 不满足 (Q5) |
| QV770340J7 | 13 | - | - | 离线 |
| G8441 (BH900ASK9E) | 9 | root | 10 | 不满足 (root 身份走 Root 路径) |
| 968e9f18 | 15 | shell | 0 | 不满足 |
| bek749scrwv4wo8h | 13 | 未运行 | 0 | 不满足 |
| 63751f63 | 15 | 未运行 | 0 | 不满足 |
| 临时 AVD (API 29, Android 10) | 10 | - | 0 | 不满足, 已删除 |
| 临时 AVD `shizukuoom_p33_api28` (Android 9 AOSP userdebug, x86_64) | 9 | shell (Shizuku 13.5.4, adb 启动) | 2 (`event0` Power Button, `event1` qwerty2 多点触控) | 满足, 用于补跑 |

补跑 (HEAD debug 5935, x86_64):

| 运行 | 预置 | (8) 结果 | 结束后残留 | 报告 |
| --- | --- | --- | --- | --- |
| p3-avd | 无 (缓存 `ROOT_AUTOMATOR_TOUCH_DEVICE` 为默认 -1) | FAIL: RootAutomator 报 "Shizuku is available but cannot access a writable input device"; 指针位置, 启动器图标命令, AndroidIdReader, 应用 Shizuku 分支 4 项 ok | 用户服务 0, stray 0 | `regression-20261023-p3-avd` |
| p3-avd-control | 缓存设为 1 (`event1`), 测后还原为 -1 | PASS: RootAutomator ok (Shizuku 后端, sendSync + exit), 其余 4 项 ok | 用户服务 0, stray 0 | `regression-20261023-p3-avd-control` |

根因: `RootAutomator` 的设备路径经 `RootAutomatorEngine.getDeviceNameOrPath` / `InputDevices.touchDeviceId` 解析, 后者经 Root 或 Shizuku 执行 `cat /proc/bus/input/devices` 按 `InputDevice` 名称找 `eventN`. shell 身份读不了该文件 (6 台真机与 AVD 均为 Permission denied), 而 `getevent -pl` 在这些设备上都能列出节点与名称; 解析结果为空时 `canUseShizukuBackend` 直接判定不可用. 该路径自 6.6.0 (读取 `/proc`) / 6.7.0 (`62235ba07` 加入 Shizuku 后端) 未改, 与本主题的改动无关. 处理方式见附录 C Q8.

P3.6 复跑 (D17 / D18, debug 5935 含 P3.6 改动; 报告 `regression-20261023-p3-6-avd`):

| 运行 | 预置 | 结果 | 结束后残留 |
| --- | --- | --- | --- |
| p3-6-avd (8) | 无 (运行前核对缓存为 -1) | PASS: RootAutomator ok (Shizuku 后端, sendSync + exit), 节点 event1 由 `getevent -pl` 解析并写入缓存; 其余 4 项 ok | 用户服务 0, stray 0 |
| p3-6-avd (12) | 缓存临时设为 -1, 测后还原 | PASS: 强停后冷启动, 用户服务未连上时 `recorder.start` / `discard` 在主线程上各 462 / 23 ms (上限 1000 ms, 修复前为 10 s + 重绑 10 s), 录制状态 RECORDING -> STOPPED; 用户服务连上后在非主线程 443 ms 解析出 event1 并缓存 | 同上 |

### B.5 T6 (9) / (10) / (11) (P3.5, debug 5935 含 `61dc4f7cf`)

报告 `regression-20261023-p3` (两列). AVD 一列是预检, 不在验收要求内; 两台的两项音量键设置原本都为开, 脚本在 (10) 中临时改关并还原.

| 项 | XQ-AT72 (Android 12, Shizuku shell) | AVD `shizukuoom_p33_api28` (Android 9, Shizuku shell) |
| --- | --- | --- |
| (9) 音量键控制 | PASS: 服务下 getevent 1, 音量加键 0.63 s 停止脚本, 强停后残留 0 (host-died), stray 0 | PASS: 0.27 s, 其余相同 |
| (10) 运行中打开 | PASS: 都关时 0, 打开后 2.0 s 内 1 (由开关触发创建), 音量加键 (uhid) 0.62 s 停止脚本, 关闭后 0.1 s 内 0 | PASS: 打开后 0.1 s 内 1, 音量加键 (sendevent) 0.37 s, 关闭后 0.1 s 内 0 |
| (11) 录制后回收 | PASS: 基线 1, 两轮录制中 2 / 丢弃后 1 (0.1-0.2 s), 主线程每步最多 529 ms | PASS: 基线 1, 两轮 2 / 1 (0.1 s), 主线程每步最多 267 ms |
| 结束后残留 | PASS: 用户服务 0, stray 0 | PASS: 用户服务 0, stray 0 |

## 附录 C 待决事项

格式: `| 编号 | 问题与选项 (标出推荐项与是否已先行) | 影响条目 | 状态 |`. 立项时的 9 项已全部拍板 (附录 A); 子会话在此登记新事项, 不自行采用默认值 (不阻塞的按推荐项先行并标明).

| 编号 | 问题与选项 | 影响条目 | 状态 |
| --- | --- | --- | --- |
| Q1 | 宿主启动即绑定与在途 getevent 孤儿 (1.5 第 3, 4 条) 怎么纳入 P1? A (推荐): P1.1 照原文清理在途 `execCommand` 进程, 验收追加 "开启音量键控制时 force-stop 20 轮 stray 为 0" (XQ-AT72 由探针核对, G8441 root 身份在 P2.1 复核), 不改绑定时机; 返工: 无. B: A 之外把 `InputEventObserver` 的 Shizuku 分支改为一个常驻 `newShellProcess` (client 令牌 linkToDeath, 宿主死即结束), 不再每个按键事件起一对 sh + getevent; 多改 `InputEventObserver` 并在 T6 加 "音量键控制" 1 项回归, 约多半个会话. C: 不处理, stray 只报告 | P1.1, P1.5, P2.1, T6 | 已决 (2026-10-22 22:40, 维护者选 B, 记为 D10) |
| Q2 | P1.4 瘦身前提不成立 (1.5 第 1, 2 条): 宿主 `App` 不在用户服务进程运行, 体积来自 dex 解压进内存与 boot image 私有页. A (推荐): P0.4 加 oat 实验 (`cmd package compile` 后重测, 并量 v6.6.4 正式包) 再定 P1.4 形态, D6 的 40 MB 目标作废. B: 取消 P1.4 (返工: 删条目). C: 保留 40 MB 另找途径 (返工: 1-2 个会话) | D6, T5, P1.4, P2.2 | 已决 (2026-10-22 22:40, 维护者选 A, 记为 D11) |
| Q3 | P1.4 的最终形态 (D11; 数字见 1.5 与 B.3): 正式版安装时即 dexopt, 用户服务 RSS 约 130 / PSS 约 40 MB, 其中 20 MB 是 boot image 私有页 (app_process 不经 zygote, 宿主无法消除), 解压 dex 为 0; 只有安装时跳过 dexopt 的包 (实测为 Android 12 上 adb 安装的 debug 包) 是 RSS 210 / PSS 131 MB, 手动编译后 PSS 降到 50 MB. A (推荐): 取消 P1.4 的代码改动, 结论留在 1.5 与 B.3, P2.2 不写 improvement 条目; 内存: 正式版保持 RSS 约 130 / PSS 约 40 MB, adb 安装的 debug 包保持 210 / 131 MB; 返工: 无. B: 只作文档提示, A 之外在 `tools/shizuku-oom/README.md` 加一句给开发者的提示 (debug 包的用户服务约 210 MB, 可执行 `adb shell cmd package compile -m speed-profile -f org.autojs.autojs6`); 内存: 正式版不变, debug 包在开发者手动执行后降到 RSS 144 / PSS 50 MB (实测 143.6 / 49.6); 约 0.1 个会话. C: 宿主经 Shizuku 在安装 / 更新后检测到自身为 `run-from-apk` 时自动触发一次 dexopt (`cmd package compile -m verify`, 实测约 8 秒, vdex 约 80 MB 存储); 内存: 正式版不变 (安装时已 dexopt), debug 包自动降到约 144 / 50 MB; 约 0.5-1 个会话, 主要只惠及 debug 构建, 需处理与安装并发及失败回退, 进 changelog `improvement` | P1.4, P2.2 | 已决 (2026-10-23 00:20, 维护者选 A, 记为 D12) |
| Q4 | P0.2 负对照不是 "健康宿主被强停": v6.6.4 在 6.8.0 数据上启动即崩溃 (数据库 5 -> 3 降级), 每轮残留 +1 到 +3 (1.5). A (推荐, 已先行): 接受现结果, 堆积已证明探针有效且机制与报告者相同, B.1 注明崩溃; 返工: 无. B: 维护者提供一台没有 6.8.0 数据的设备 (或同意在某台测试机上备份后清空宿主数据, 测后还原) 再跑 10 轮, 预期每轮 +1; 约 20 分钟, 需维护者在手机上授权 Shizuku. C: 改用临时 AVD 装 Shizuku 与 v6.6.4 重跑; 约半个会话, Shizuku 授权同样要维护者点按 | P0.2, 附录 B.1 | 已决 (2026-10-23 00:20, 维护者选 A, 记为 D13) |
| Q5 | T6 (8) 的 RootAutomator 冒烟在 XQ-AT72 上不可测: 该机 (Android 12) SELinux 不允许 shell 身份写任何 `/dev/input` 节点, RootAutomator 的 Shizuku 后端 (自 6.7.0 未改, P1 未触及) 如实报告 "cannot access a writable input device"; G8441 为 root 身份, 不跑 (8). A (推荐, 已先行): 记为不适用, P2.1 视为通过 ((8) 其余 4 个调用点已通过); 返工: 无. B: 另找一台 Shizuku 以 shell 身份运行且 shell 可写 `/dev/input` 的设备 (多为 Android 9-11 的非 root 机) 补跑 `regression.py --items 8`; 约 20 分钟, 需维护者在手机上授权 Shizuku | P2.1, 发布门 | 已决 (2026-10-23 06:50, 维护者选 B, 记为 D14, 由 P3.3 执行) |
| Q6 | (调度会话出题) P1 附带发现两处 (1.5 末条) 怎么处理: 宿主启动后才打开 "音量键控制" 时无人创建 `GlobalKeyObserver` (要重启宿主); `TouchRecorder(Context)` 自建的 `InputEventObserver` 自 6.6.0 起从不回收. A (推荐): 只记录. B: 本主题新增 P3 修掉. C: 转 memory 主题 | 新增 P3.1 / P3.2 / P3.4 / P3.5, changelog fix | 已决 (2026-10-23 06:50, 维护者选 B, 记为 D15) |
| Q7 | (调度会话出题) P0 附带发现 `org.autojs.autojs6.inrt` 被 Shizuku 服务端重启唤醒后数据库 4 -> 3 降级崩溃循环 (1.5). A (推荐): 不处理只记录. B: 卸掉 XQ-AT72 上的 inrt. C: 立项排查降级崩溃 | 本主题只记录 | 已决 (2026-10-23 06:50, 维护者选 C, 记为 D16; 新主题 db-downgrade 由维护者立项) |
| Q8 | (shizuku-oom-p3 登记) P3.3 补跑结果 (附录 B.4): 真机都不满足, 临时 AVD (Android 9, Shizuku shell 身份, shell 可写触摸屏节点) 上 (8) 仍 FAIL. 原因不在 Shizuku 后端: `InputDevices.touchDeviceId` 读不到 `/proc/bus/input/devices` (shell 身份 Permission denied), 触摸屏节点为空; 预设节点缓存后 (8) PASS. 所以 6.7.0 起 RootAutomator 的 Shizuku 后端只在缓存已有节点号 (例如曾经用 Root 解析过) 时可用, 只用 Shizuku 的用户 (Android 9 及以下, 或 SELinux 放开 `/dev/input` 的机型) 实例化总是失败. 同一缺陷的另一面: 解析失败不写缓存, 所以每次开始录制 (`InputEventToAutoFileRecorder` / `InputEventToRootAutomatorRecorder` 的构造函数) 都在主线程上经 Shizuku 再执行一次该命令, 宿主刚启动, 用户服务未连上时主线程等待 10 s (重绑后再 10 s); AVD 上 (11) 首跑即因此超过 10 s (`tools/shizuku-oom/README.md` (11)), 用户可能遇到 ANR. A (推荐, 未先行: 新增条目与源码改动需拍板, 同 Q6): 本主题新增 P3.6, `/proc` 读不到时改用 `getevent -pl` 按设备名找节点 (只改 `InputDevices.touchDeviceId` 的解析, 解析成功即缓存, 之后开始录制不再在主线程上执行命令; JVM 单测覆盖输出解析; 验收: AVD 不预设缓存跑 (8) PASS, XQ-AT72 仍如实报告不可写, G8441 Root 路径不变), changelog fix 1 条; 约 0.5 个会话, 本会话的 AVD (已授权 Shizuku) 保留到拍板, 可直接复测. B: 只记录, P3.3 以对照实验为结论 (后端可用, 节点解析为既有缺陷), 修复转后续版本或独立主题; 返工: 无, 选 B 后删除 AVD. C: 维护者另备真机: 修复前真机结果与 AVD 相同 (FAIL), 只在 A 之后作为补充覆盖才有意义; 约 20 分钟, 需维护者在手机上授权 Shizuku | P3.3, (新) P3.6, 发布门, changelog | 已决 (2026-10-23 10:45, 维护者选 A, 记为 D17; 同源的 Q8b (主线程等待用户服务的 ANR 风险) 也选 A, 记为 D18, 并入 P3.6) |

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
| `app/src/main/java/org/autojs/autojs/core/inputevent/InputEventObserver.java` | Shizuku 分支改常驻 `newShellProcess` | P1.5 |
| `app/src/main/java/org/autojs/autojs/App.kt` | (原 P1.4 用户服务进程分支, 已因 D11 作废) | - |
| `app/src/test/java/org/autojs/autojs/core/shizuku/` | T7 单测 | P1.1 / P1.2 |
| `.changelog/lang_zh-Hans.json` | changelog | P2.2 |
| `docs/dev/6.8.0-release-gate.md` | 发布门一行 | P2.3 |
| `docs/memory/ROADMAP.md` 附录 C Q4 / Q52 | 去向指向本文 | 立项 |
| `app/src/main/java/org/autojs/autojs/event/VolumeControlSettings.kt` | 两项音量键设置的读取与监听, 一次性 "开启即创建" 订阅 | P3.1 |
| `app/src/main/java/org/autojs/autojs/event/GlobalKeyObserver.java` | `initIfNeeded` 在两项都关闭时登记订阅 | P3.1 |
| `app/src/main/java/org/autojs/autojs/core/record/inputevent/TouchRecorder.java` | 自建观察器随录制开始 / 结束启停 | P3.2 |
| `tools/shizuku-oom/regression.py` (10) / (11) | 运行中开启音量键控制, 录制后回收 getevent | P3.1 / P3.2 |
| `app/src/main/java/org/autojs/autojs/core/inputevent/InputDevices.kt` | 触摸屏节点解析: `/proc` 读不到时用 `getevent -pl`, 按标准输出判断 | P3.6 (D17) |
| `app/src/main/java/org/autojs/autojs/engine/RootAutomatorEngine.kt` | 只缓存解析成功的触摸屏节点 | P3.6 (D17) |
| `app/src/main/java/org/autojs/autojs/runtime/api/WrappedShizuku.kt` | 主线程上不等待用户服务, 也不重绑 | P3.6 (D18) |
| `app/src/test/java/org/autojs/autojs/core/inputevent/InputDevicesTouchDeviceTest.kt` | 解析三种情况与缓存 | P3.6 |
| `tools/shizuku-oom/regression.py` (12) | 冷启动后立即录制的主线程耗时, 用户服务连上后解析并缓存节点 | P3.6 |
