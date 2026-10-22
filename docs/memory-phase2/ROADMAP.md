# 内存路线图第二期: 插件引擎桥接对象

更新日期: 2026-10-22 (立项, P0.1; P0.5; P4.1)

本文是第一期 `docs/memory/ROADMAP.md` (宿主 Rhino 全形态, P0-P6 已于 2026-10-22 完成) 的续篇, 覆盖第一期 D2 明确排除的部分: Node / Bun / Lua / Python / QuickJS 五种插件引擎在宿主进程内的桥接对象 (第一期附录 C Q5, 去向 Q53). 它以 2026-10-22 维护者经三轮选择题确认的 11 项决策 (第 2 节) 为依据, 记录现状 (第 1 节), 派生技术决策 (第 3 节), 红线 (第 4 节), 阶段任务 (第 5 节) 与附录 (基线数据, 桥接注册表与配对清单, 待决事项, 插件仓库改动记录, 证据等级, 文件地图).

核心目标只有一句话: 任何经插件引擎执行的脚本以任何方式结束 (正常, 异常, 强制停止, 插件进程被杀, 绑定被拒) 之后, 宿主进程里为这次执行创建的桥接对象 (引擎实例, broker / 会话 / 绑定 / 租约, UI / Web / 截屏 / 悬浮窗 / 对话框 / 图像句柄 / 录音等会话对象, Node 的 `ScriptRuntime`) 全部可被 GC 回收, 进程级连接池与注册表回到执行前的状态, 并且这一点由第一期同款的自动化门禁持续证明.

范围说明: 只覆盖宿主进程. 插件进程自身的内存 (Node 插件已有 `NodeEmbeddedMemoryLeakStressInstrumentationTest` 一类压测, Lua / QuickJS 有运行时内存上限) 不在本文判定内, 门禁只记录插件进程 PSS 作参考 (T1 (i)).

使用方式: 与第一期相同 (条目完成后勾选并追加证据; 证据等级附录 E; 每个阶段结束跑完整矩阵并更新附录 A; 工程约定以 `AGENTS.md` 与 `../COMMON_AGENTS.md` 为准; 真机规则见 `../.claude/rules/device-testing.md`). 第一期的第 1.4 节 (诊断与测试基础设施), 第 3 节 T1 / T3 / T4 / T12 与第 4 节 R1-R6 在本文中直接引用, 不重复抄写.

---

## 0. 阶段总览

| 阶段 | 目标 | 完成条件 | 预计子会话数 |
| --- | --- | --- | --- |
| P0 门禁骨架与 Node 基线 | 第二期门禁能抓到桥接对象残留, Node 的 13 个场景在三台门禁设备上有修前基线, 桥接目录的静态审计报告 | 测试类与 `run_gate.py --suite bridge` 跑通, 附录 A "修前" 列填满, 附录 B 初稿 | 2 (门禁 + 基线 1, 审计 1, 可并行) |
| P1 Node 修复与会话登记 | 基线报出的 Node 桥接残留逐个清零, Node 桥接会话对象登记到 RuntimeResourceScope | 附录 A Node 行 "残留" 全部为 0, 绑定计数回到场景前 | 2-3 |
| P2 Bun + QuickJS | 两家各 4 个基础场景与各 1 个 ColorOS 被拒专场 (Q2) 的基线与修复, `AidlPluginHost` 连接池与租约核对 | 附录 A BN / QJ 行全部通过 | 1-2 |
| P3 Lua + Python | 两家各 4 个基础场景的基线与修复, 专用绑定传输核对 | 附录 A LU / PY 行全部通过 | 2 |
| P4 守卫与规范 | 源码守卫与静态审计覆盖桥接目录, 非 Node 引擎的执行作用域规范, LeakCanary 巡检, 诊断页面与 memory API 展示桥接会话 (Q1) | 守卫进入全量单测且白名单行有理由, 五个引擎巡检无报告, bridgeSessions 字段上线 | 1-2 |
| P5 CI 与收尾 | `memory-gate.yml` 的 bridge 套件, 全设备终表, 本文收尾 | CI 本机同款跑通一次, 附录 A 终表, 第一期 Q53 行回填 | 1-2 |

P0 的两个子会话可并行 (门禁 + 基线不碰守卫文件, 审计只读源码); P1 依赖 P0 的基线; P2 与 P3 都只依赖 P0 的骨架, 可以与 P1 并行, 但门禁设备只有两台真机, 默认按 D2 的顺序串行; P4 的守卫部分 (P4.1) 可在 P0 之后随时做; P5 最后.

---

## 1. 现状与证据 (基线 `master@a6c61241e`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=4283`)

### 1.1 来源

- 第一期 D2 / 附录 C Q5: 插件引擎在宿主进程内的桥接对象列为第二期; Q53 (p6d 2026-10-22 登记) 记去向, 维护者 2026-10-22 19:40 选 (b) 现在起草骨架.
- 第一期已经触及的桥接问题: 1.3 H28 / 附录 C Q46 (`NodeJsRuntimePluginHost.startBind` 被拒不解绑, 已修 `383dc55a0`, 只有 JVM 单测与 OPD2413 功能复核, 没有真机残留计数); Q42 (`RuntimeViewComponents.RuntimeWebView` 由同文件 `release()` 销毁, 白名单 `paired`, 无门禁场景); P4.2 把 `RuntimeWebActionActivity.actions / grants / captures`, `RuntimeWebLocalDownloads.active`, `RuntimeWebMediaService.entries` 标为 `owner` (按请求 id 清理, 门禁不覆盖).
- 引擎文档里的内存条目: `docs/nodejs/PERFORMANCE.md` 第 6 与 17-18 行 (内存在持有它的进程里测, 宿主原生堆不算插件的), `docs/nodejs/FLAKE_POLICY.md` 第 8 行 (资源泄漏算回归不算偶发); `docs/lua/ROADMAP.md` 第 23 / 47 / 65 / 105 / 106 行 (cancel / close 幂等, 内存上限已验证, OS 级 FD 泄漏与 callback peer death 未覆盖); `docs/quickjs/README.md` 第 40-41 行 (运行时内存默认 64 MiB, 协议上限 256 MiB); `docs/bun/` 与 `docs/python/` 没有相关条目.

### 1.2 桥接架构 (2026-10-22 只读盘点, 路径相对 `app/src/main/java/org/autojs/autojs/`)

| 引擎 | 宿主侧代码 | 宿主类 | 绑定方式 | 每次执行的宿主侧会话对象 | 释放入口 |
| --- | --- | --- | --- | --- | --- |
| Node | `engine/Node*.kt` 78 个文件约 31800 行 (其中 `NodeBridge*` 29 个 18237 行, `NodeBridgeProtocol.kt` 9056 行), `engine/Runtime*.kt` 47 个约 8450 行 (`RuntimeWeb*` 11 个), `engine/ScreenRecording*` 3 个; 合计约 128 个文件 4.07 万行 | `NodeJsRuntimePluginHost` (object, 自建连接池) | AIDL, 池化 (`connectionPool`, 不按空闲解绑; `closeSession` 没有生产调用) | `NodeJsRuntimePluginBridge.runScript` 每次新建 `NodeJsHostCapabilityBroker` 并挂 `AndroidNodeBridgeCapabilityProvider`; 引擎继承 Rhino, 有 `ScriptRuntime` | `runScript` 的 finally: `broker.destroy` -> `provider.destroy`, 清理 `activeModuleSourceProviders / activeExecutionIds / cancelledExecutionIds / backendExecutionIds`; `executionRegistry` 的 `reserveExecution / releaseExecution`; `invalidateEntry / retireEntry / DeathRecipient / onServiceDisconnected / onBindingDied` |
| Bun | `engine/Bun*.kt` 6 个 + `core/plugin/bun/` 3 个, 约 2330 行; 经 `imagesProvider` 复用 Node 的 `AndroidNodeBridgeCapabilityProvider` 与协议类型 | `BunRuntimePluginClient` (object, 内含 `AidlPluginHost`) | AIDL, `AidlPluginHost.connectionPool` 空闲 30 s 解绑, 另有 `callWithDedicatedBindingLease` 专用租约 | `BunHostCapabilityBroker` (持 `callExecutor`, automation, imageProvider) + callback | `BunPluginScriptEngine` 关闭 broker 与 callback, `forceStop` / `destroy`; `BunHostCapabilityBroker.close` |
| QuickJS | `engine/QuickJsPluginScriptEngine.kt` + `core/plugin/quickjs/` 2 个, 约 950 行 | `QuickJsRuntimePluginClient` (object, 内含 `AidlPluginHost`) | 同 Bun (共用 `AidlPluginHost` 1046 行) | 没有 broker, 只有 `AidlPluginHost` 调用 | 引擎 `destroy` |
| Lua | `engine/Lua*.kt` 11 个 + `core/plugin/lua/` 7 个, 约 3720 行 | `LuaRuntimePluginHost` (class, 由 `LuaRuntimePluginHostFactory` 构造) | AIDL, 每次执行专用绑定 (`ExactComponentBinderLease`, `AndroidLuaRuntimeProviderTransport` close -> unbindService) | `LuaPluginScriptEngine.currentConnection / currentSession`; `LuaEngineRuntimeBackend` 的 `BinderConnection` / `SessionHostBroker` / `BinderRemoteSession` | 引擎 `forceStop` / `destroy`, `BinderConnection.close` |
| Python | `engine/PythonPluginScriptEngine.kt` + `core/plugin/python/` 23 个, 约 8590 行 | `PythonRuntimeClient` + `PythonRuntimeOfficialTransport` | AIDL, 每次执行专用绑定 (`Transport.Binding` close -> unbindService) | `PythonRuntimeClient.Execution` (binding, registry, hostCapabilityBroker) | `cancel`, `retireRemoteSession`, `closeHostCapabilityBroker` |

公共部分: `core/plugin/hostbroker/` (4 个文件 723 行) 是 broker 底座; AIDL 五家各自独立 (plugin-api 下 nodejs 5 / bun 4 / lua 5 / python 5 / quickjs 2 个); 五家都经 `bindService` + Binder, 源码用 `ParcelFileDescriptor` 传, 没有 socket. 只有 Node 引擎继承 Rhino 并有 `ScriptRuntime`, 其余四家继承 `AbstractScriptEngine`, 所以第一期门禁 (a) 项的 `ScriptRuntime` 弱引用对它们不适用 (D5).

脚本如何走到插件引擎: JS 源首行指令 `"nodejs"` / `"bun"` / `"quickjs"` (`JavaScriptSource.ENGINE_*`, `NodeEntryDirective`), 或文件后缀 (`NodeRuntimeCapabilities.nodeRouteFileSuffixes`: `.node.js`, `.cjs`, `.mjs`, `.ts`, `.mts`, `.cts`, `.tsx`); Lua / Python 用 `LuaScriptSource` / `PythonScriptSource` (`.lua` / `.py`). 门禁脚本源的具体写法由 P0.2 确定并写进附录 F.

### 1.3 候选疑点清单 (立项时只读盘点, P0.4 基线与 P1-P3 逐项处置, "处置" 列回填)

| 编号 | 位置 | 疑点 | 处置 |
| --- | --- | --- | --- |
| C1 | `NodeJsRuntimePluginHost.connectionPool / executionRegistry / observerContext` | 进程级连接池不按空闲解绑是设计; `executionRegistry` 的 reserve / release 是否在所有结束路径 (异常, 强制停止, binderDied) 配对 | |
| C2 | `NodeJsRuntimePluginBridge` (object) 的 `activeModuleSourceProviders / activeExecutionIds / cancelledExecutionIds / backendExecutionIds` | 不在守卫白名单; `cancelledExecutionIds` 是否只增不减 | |
| C3 | `NodeAudioRecordingService.instance / entries`, `RuntimeWebMediaService.instance / entries` | Service 单例与会话表, 执行结束是否移除; `NodeAudioRecordingService` 不在白名单 | |
| C4 | `RuntimeWebCallbacks.actions / cameraRecording`, `RuntimeWebCameraRecording` (HandlerThread, CameraDevice) | 回调表与相机线程随执行结束释放 | |
| C5 | `NodeBridgeProtocol.imageHandles` (`ControlledImageHandle.bitmap`, `ControlledOcrInput.bitmap`), `NodeAdvancedImages.NativeImage` | 执行结束时未被脚本释放的句柄是否随 `provider.destroy` 回收 (对应第一期 T1 (g)) | |
| C6 | `NodeBridgeProtocol.screenCapture` (`NodeScreenCaptureSession.capturer / requester`), `ControlledOverlaySession` (`windowManager / layoutParams / addView`), `RuntimeComposeBridge` overlay, `NodeScreenRecording`, `RuntimeWebDisplayCapture` | 强制停止与 binderDied 路径是否 `removeView` / 释放 MediaProjection | |
| C7 | `NodeDialogs.sessions` (`NodeDialogSession.activity` 强引用), `NodeBridgeUiProvider.sessions`, `RuntimeComposeActivity.Session.activity / renderOwner / images`, `RuntimeInteropActivity.Window.activity`, `RuntimeWebActionActivity.Action.activity` (弱) | Activity 强引用表, 会话结束与 Activity 销毁两条路径是否都移除 | |
| C8 | `ScreenRecordingFrames` HandlerThread, `ScreenRecordingAudio.records` | 线程与录音记录释放 | |
| C9 | 直接引用 `AutoJsAccessibilityService.instance` 的位置: `NodeBridgeProtocol` 3 处, `NodeBridgeScreenCapture`, `PythonHostCapabilityBroker` 2 处, `RuntimeFlowEvents` | 是否缓存到会话对象 (服务重启后持旧实例) | |
| C10 | `BunHostCapabilityBroker.close` (callExecutor / automation / imageProvider), `AidlPluginHost.connectionPool` (挂在 Bun / QuickJS 的 object 上, 不在白名单), `callWithDedicatedBindingLease` / `ServiceBindingLease` | 租约配对, 空闲解绑后的回调表 | |
| C11 | `LuaEngineRuntimeBackend.BinderConnection / SessionHostBroker / BinderRemoteSession`, `ExactComponentBinderLease`, `LuaHostStorage` companion `LOCKS` | 专用绑定的 close 在异常路径是否执行; `LOCKS` 是否按会话移除 | |
| C12 | `PythonRuntimeClient.Execution`, `PythonRuntimeExecutionQueue.queue`, `PythonRuntimeProviderSelectionPolicy.evaluations`, `PythonHostImages` (TemplateScope / ScreenImageScope), `PythonHostSelectorScope.retainedNodes`, `PROVIDER_RPC_EXECUTOR / INPUT_EXECUTOR`, `PythonInteractiveInput.ForegroundGrant.activity` (弱) 与 `PythonMaterialDialogInputController(activity)` | 队列与评估表按执行清理; 图像与无障碍节点作用域释放 (对应第一期 P3.4 的节点回收) | |
| C13 | 主线程 `Handler`: `NodeJsRuntimePluginHost`, `BunHostCapabilityBroker`, `LuaHostUi`, `PythonHostCapabilityBroker`, `PythonInteractiveInput` | 投递的 Runnable 是否捕获会话对象且在结束时 `removeCallbacks` | |
| C14 | `*BoundedBinderCallLane` (Bun / Lua / QuickJS 的 object) | 调用通道的静态状态是否持会话对象 | |
| C15 | `NodeBridgeEngineDispatchService.registeredExecutions`, `NodeBridgeUiDispatchService` | 分发服务的执行表 | |
| C16 | H28 / Q46 (`NodeRuntimePluginBindCall`) | 已修, 本期 ND13 在 OPD2413 上补真机残留计数 (第一期只有 JVM 单测) | |
| C17 | `RuntimeComposeActivity.sessions` (`RuntimeComposeBridge.kt`), `RuntimeInteropActivity.windows` (`RuntimeUiInterop.kt`) | P0.5 发现: 写在单行 `companion object { ... }` 里的进程级强表 (id -> `Session` / `Window`, 持 Activity 与视图树), 第一期扫描漏读, 不在白名单 (P4.1 记 `p1`); 只在 `Session.close` / `Window.close` 移除, 强制停止, 插件进程被杀与 Activity 被系统销毁时是否都走到 close (与 C7 一起查) | |
| C18 | Node 会话与服务里同文件没有 `removeCallbacks` 的 `Handler(` / `postDelayed(`: `NodeAudioRecorder`, `NodeBridgeEngineDispatchService`, `NodeBridgeFilesBridge`, `NodeBridgeRhinoExecutionService`, `NodeBridgeUiProvider`, `NodeScreenCaptureSession` (超时 `postDelayed`), `RuntimeAiBridge`, `RuntimeFlowToolkit`, `RuntimeUiInterop` (回复超时与 2 s 墓碑 `postDelayed`), `RuntimeWebCameraRecording`, `RuntimeWebFileChooser`, `RuntimeWebMediaService` (行号见 P0.5 报告) | P0.5 发现, C13 只列了五家宿主类: 延时 Runnable 在执行结束后是否仍捕获会话对象 (多数是一次性 `post`, 风险低, 门禁的强制停止场景顺带核对) | |
| C19 | `PluginAutoWake.locks / blockingLocks / wakeCounts` | P0.5 发现: 包名 -> `Mutex` / 锁对象 / 计数, 只增不删 (卸载插件后也保留); 只有字符串键与锁, 不持会话, 风险低 (P4.1 记 `process` 并在注释写 C19) | |
| C20 | `RuntimeJavaSession.nativeDepth` (`ThreadLocal`) | P0.5 发现: 实例级 `ThreadLocal.withInitial { 0 }`, 全仓库没有 `remove()`; 值只是 `Int`, 会话被回收后键随之失效, 风险低, 核对 JVM 交互线程复用时是否残留 | |

### 1.4 可复用的第一期基础设施

第一期 1.4 节全部适用. 本期直接复用: `RuntimeLeakGateDeviceTest` 的结构 (预热 / 执行 / 回收轮询 / JSON 报告 / `EngineTracker` 弱引用跟踪), `GateMetrics` / `MemoryDiagnostics` 的六项度量, `GateHeapPathAnalyzer` (shark 最短引用链), `GateNativeProbe` (图像 / WebView / 类加载器计数), `StaticRegistries` (注册表计数), `tools/memory/run_gate.py` (驱动, `--capture` 的 appops 先例, `--merge`, 崩溃日志), `ci_gate.py` (模拟器安装与重跑, `--plugin` 可多次), `static_audit.py` / `StaticReferenceGuardTest` (守卫), `ui_leak_walk.py` / `leakcanary_probe.py` (巡检). 已有的桥接 androidTest 以 `NodeRuntimePluginBridgeInstrumentationTest` 一类为主, 修复后须保持通过.

### 1.5 规模数字

五家桥接合计约 135 个 Kotlin 文件约 4.9 万行, 其中 Node 系约 4.07 万行, 其余四家约 1.56 万行. 守卫白名单 (`app/src/test/resources/memory/static-reference-whitelist.txt`) 已登记的桥接静态表 13 处 (附录 B.1); 不在白名单的疑似静态表约 10 处 (附录 B.1 第二段), 原因是 `static_audit.py` 的 `SCRIPT_PATHS` 只含 `engine/Runtime`, 不含 `engine/Node|Bun|Lua|Python|QuickJs*` 与 `core/plugin/`, 单行 `companion object` 也疑似未被扫到. 桥接代码目前完全没有使用 `RuntimeResourceScope`.

---

## 2. 固定决策 (2026-10-22 三轮选择题)

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 位置 = `docs/memory-phase2/ROADMAP.md` | 证据 `docs/memory-phase2/evidence/` (报告名与第一期同款, 不加前缀); 工具仍在 `tools/memory/` (新脚本或 `run_gate.py` 子命令); 第一期文件只回填 Q53 行; 看板 `../.claude/boards/memory-phase2.md` |
| D2 | 顺序 = Node, 再 Bun + QuickJS, 后 Lua + Python | 三批串行 (P1 -> P2 -> P3); Node 与第一期门禁同口径且已知问题都在它; 第二批共用 `AidlPluginHost`; 第三批各自专用绑定 |
| D3 | 不进 6.8.0 发布门 | 6.8.0 按第一期 P6.3 发布; 本期修复若在 6.8.0 发布前合入 master, changelog 按事实记在 6.8.0 但不作发布门条件 (R8 要求不使第一期门禁倒退); 发布后合入的记入下一版 (6.8.1 或 6.9.0, 届时由维护者定); P0 基线若发现每次执行残留递增的线性泄漏, 登记待决问是否提前修, 不自动阻断 |
| D4 | 门禁设备 = 23046RP50C (API 35, 主机) + OPD2413 (API 35, ColorOS, 绑定被拒 / 唤醒专场) + AVD API 35 x86_64 (CI 同款配方) | XQ-AT72 / G8441 / AVD API 24 留给 memory 主题; Node / Bun / Python 插件只有 arm64 + x86_64, 所以不用 x86 的 API 24 镜像; 两期子会话共用设备占用登记表 (T10), 不同时占用同一台 |
| D5 | 判定 = engine + 会话对象弱引用 + 绑定计数 | 每次执行跟踪 engine 实例与宿主侧会话对象 (broker / session / binding / Execution, Node 另含 `ScriptRuntime`), 结束后弱引用全部被清; 第一期 T1 其余各项沿用; 新增 (h) 绑定计数 (T1); 插件进程 PSS 只记录不判定 |
| D6 | 场景 = 每引擎 4 个基础 + Node 8 个专项 + ColorOS 被拒 3 个 (ND13, 及 Q2 已决后的 BN5 / QJ5) | 共 31 个场景, 每个分正常结束与强制停止两种模式 (T2); 子会话读代码后可增补, 增补写附录 A 并在本表下方登记 |
| D7 | 允许改插件仓库并本地提交, 门禁用本地构建 | 五个插件仓库 (`../AutoJs6-Plugin-{NodeJs,Bun,Lua,Python,QuickJS}-Runtime`) 可改, 只本地提交, 推送与发版由维护者逐次指示; 门禁装插件仓库本地构建 (T5), 报告记录插件 versionCode 与提交 hash; 每处改动记附录 D; 宿主侧能绕过的先绕 |
| D8 | 守卫扩范围 + 会话登记规范 + CI 手动输入 | (1) `static_audit.py` 与 `StaticReferenceGuardTest` 扫描范围扩到桥接目录; (2) Node 桥接会话对象接 `RuntimeResourceScope`, 其余四家用等价的执行作用域 (T6); (3) `memory-gate.yml` 加 `suite` 输入跑 bridge 套件, 下载五个插件 x86_64 包校验 sha256, tag 推送时不跑 (T8) |
| D9 | 绑定计数的系统侧探针经 `hidden_api_policy` 临时放行 | `run_gate.py --suite bridge` 在每个第二期进程前 `settings put global hidden_api_policy 1`, 结束 (含失败 / 中断路径) 后 `settings delete global hidden_api_policy`; 与第一期 `--capture` 的 PROJECT_MEDIA 做法同款; 宿主侧注册表计数照做, 系统表作交叉验证 |
| D10 | AVD API 35 由子会话自建自删 | 每个用到 AVD 的子会话按 `memory-gate.yml` 的配方临时建 `aj6mp2_api35_<子会话名>` (google_apis x86_64, pixel_7, 4096M), 结束时按 `session-cleanup.md` 删除; 不预置 |
| D11 | ND12 的录音权限经 `pm grant / revoke` | `run_gate.py` 在 ND12 进程前 `pm grant org.autojs.autojs6 android.permission.RECORD_AUDIO`, 结束后 `pm revoke`; 模拟器上宿主以 `-g` 安装已含该权限, 不另处理 |

第一期的 D3 (门禁 = 自动化仪器测试), D6 (门禁先行, 不在没有基线的情况下修), D9 (Rhino fork 最小改动; 本期预计不需要) 继续适用.

---

## 3. 派生技术决策

据第 2 节派生, 进入对应阶段前可推翻, 之后视同固定.

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 判定: 每个场景预热 2 次不计, 再执行 N = 20 次 (与第一期 T1 相同的回收轮询与阈值). (a) 残留: 本次执行创建的 engine 实例, 宿主侧会话对象 (按引擎: Node `NodeJsHostCapabilityBroker` + `AndroidNodeBridgeCapabilityProvider` + `ScriptRuntime`; Bun `BunHostCapabilityBroker` + callback; QuickJS engine; Lua `BinderConnection` + `SessionHostBroker` + `BinderRemoteSession`; Python `Execution` + binding + broker) 的弱引用在 "GC 3 次 + `runFinalization` + 最长 10 s 轮询" 内全部被清, 残留数必须为 0; 会话对象的弱引用经测试侧挂点取得 (P0.2 决定: 引擎回调, 或宿主侧新增的 debug 可见的 `BridgeExecutionObserver`, 不改生产行为); (b) Java 堆 <= 2 MB, (c) 原生堆 <= 4 MB (截屏类放宽一帧), (d) 线程数 (名含第一期清单外再加桥接线程名前缀, P0.2 盘点后写进附录 F), (e) Rhino Context 计数 (只对 Node), (f) 存活引擎数回到 0, (g) 图像 / WebView / 类加载器计数回到场景前 (沿用 `GateNativeProbe`), 全部沿用第一期; 新增 (h) 绑定计数: 宿主侧桥接注册表计数 (`StaticRegistries` 扩展: `NodeJsRuntimePluginHost.connectionPool / executionRegistry`, `AidlPluginHost.connectionPool`, Lua / Python 专用绑定租约数, `NodeBridgeUiProvider.sessions`, `NodeDialogs.sessions`, `RuntimeWeb*` 三表, `NodeAudioRecordingService.entries`, `RuntimeWebMediaService.entries`, `PythonRuntimeExecutionQueue.queue`) 回到场景前, 池化引擎允许保留 1 个空闲连接 (Node 池不解绑, `AidlPluginHost` 30 s 后解绑, 场景结束前不等它); 系统侧 `LoadedApk.mServices` 中指向插件组件的连接数 (反射, D9) 回到场景前, 读取失败时记 `bindingProbe: unavailable` 并只用宿主侧计数; (i) 插件进程 PSS (`dumpsys meminfo <插件包>`) 第 20 次减第 2 次只记录. (a)-(h) 任一失败即场景失败 | P0.4 第一次基线之后 (阈值可按噪声调整, (a) 的 0 残留与 (h) 的回到场景前不放宽) |
| T2 | 场景矩阵 (id 前缀按引擎: ND Node, BN Bun, QJ QuickJS, LU Lua, PY Python; 每个场景两种模式: 正常结束 N, 强制停止 F 在脚本运行 1 s 后 `forceStop`). 基础场景 X1-X4 五家都做: X1 空脚本 (打印一行后结束, 绑定建立与释放的最小路径, 噪声基线); X2 宿主能力调用 (经该引擎的 broker 调用 2-3 个宿主能力, 至少一次带图像或文件数据的往返, 例如 device 信息 + toast + 读一张小图, 由各批次按 broker 能力表选定并写进附录 A 行备注); X3 回调型 (注册宿主推送的回调: 定时器 / 事件监听 / 选择器等待 / 输入对话框, 各引擎取其支持的一种; N 模式等 3 次回调后自行结束, F 模式在回调在途时强制停止); X4 插件进程被杀 (脚本运行中 sleep 2 s 期间, 测试进程经 `UiAutomation.executeShellCommand("am force-stop <插件包名>")` 杀掉插件进程, 宿主走 binderDied / onBindingDied, 执行以异常结束; F 模式在杀后 1 s 再 `forceStop`). Node 专项: ND5 UI 会话 (`NodeBridgeUiProvider` / `NodeBridgeUiActivity` 显示布局后 finish); ND6 Compose (`RuntimeComposeBridge` / `RuntimeComposeActivity` 渲染后关闭); ND7 Web 运行时 (`RuntimeWebView` 加载本地页 + 一次 action (`RuntimeWebActionActivity`) + 一次本地下载 (`RuntimeWebLocalDownloads`) + 一次媒体会话 (`RuntimeWebMediaService`) 后关闭, 实现时可拆为 ND7a-c); ND8 截屏 (`NodeScreenCaptureSession` 截 5 帧后释放, `--capture on` 放行 PROJECT_MEDIA); ND9 悬浮窗 (`ControlledOverlaySession` 显示后关闭, 悬浮窗权限同第一期 S6); ND10 对话框 (`NodeDialogs` 弹出后程序化关闭); ND11 图像句柄 (`imageHandles` 创建 20 个句柄含一次 OCR 输入, 一半释放一半不释放, 验证执行结束时回收); ND12 音频录制 (`NodeAudioRecordingService` 录 1 s 后停止, D11); ND13 ColorOS 绑定被拒循环 (OPD2413 专场: 每次运行前 `am force-stop` Node 插件, 让首次 `bindService` 被拒再由 `PluginAutoWake` 重绑, 第一期 Q46 的复核方法; 其他设备跳过并记录); BN5 / QJ5 ColorOS 绑定被拒循环 (Q2 已决 2026-10-22: 与 ND13 同法, 覆盖 AidlPluginHost 的池与专用租约, OPD2413 专场, 每次运行前 am force-stop 对应插件, 其他设备跳过并记录). 共 31 个场景 | P0.3 (Node), P2.1 (BN / QJ), P3.1 (LU / PY) |
| T3 | 执行方式: 新测试类 `app/src/androidTest/java/org/autojs/autojs/memory/bridge/BridgeLeakGateDeviceTest.kt` (与第一期同结构, 每个场景一个 `@Test`, 用 `AutoJs.instance.scriptEngineService.execute(...)` 同进程启动), 场景脚本 `memory/bridge/BridgeScenarios.kt` (JS 源首行引擎指令, Lua / Python 用对应 ScriptSource), 跟踪器 `BridgeTracker` (弱引用集合 + 绑定计数); 复用 `GateMetrics` / `GateHeapPathAnalyzer` / `GateNativeProbe` (抽公共部分时只加不改第一期行为). 驱动: `tools/memory/run_gate.py --suite bridge` (默认 `runtime` 即第一期; 场景表, 测试类, hprof 查找类名列表, `--capture` / D9 / D11 的放行步骤按 suite 切换; `--out` 默认按 suite 指向 `docs/memory-phase2/evidence`), 报告名 `gate-<model>-<versionCode>-<label>-<yyyyMMdd>.md` / `.json`, 标签如 `mp2-baseline`, `mp2-p1`; 插件 versionCode 与提交 hash 写进报告头 | P0.2 |
| T4 | hprof: 同第一期 T4 (失败时设备端 shark 解析), 查找对象改为 T1 (a) 的类名列表, 已结束的会话以 `BridgeTracker` 记录的执行 id 识别 (对象上若无 id 字段, 以 "测试进程内本场景创建的实例" 为准) | P0.2 |
| T5 | 插件安装 (D7): 每批次开始时在该引擎插件仓库构建调试包 (该仓库惯例任务, 沿用仓库签名配置), 以 `adb install -r` 覆盖设备上的发布版 (签名不同时先卸载), 模拟器装 x86_64 变体; 报告记录插件 versionCode 与仓库提交 hash; 测试机上不必装回发布版 (记忆 `test-devices-plugin-install-authorized`); 插件未安装 / 未启用时场景跳过并记录 (与第一期 S12 相同) | P0.3 |
| T6 | 会话登记规范 (D8 (2)): Node: 每次执行的桥接会话对象 (broker, provider, UI / Compose / Web 会话, 截屏, 悬浮窗, 对话框, 图像句柄, 录音) 经该执行 `ScriptRuntime` 的 `RuntimeResourceScope` 登记 (`own` 在结束时按阶段释放, `track` 作结束后弱引用复查), 释放顺序以现有 finally 顺序为准; 其余四家: 每次执行一个 `BridgeExecutionScope` (新类, 包 `org.autojs.autojs.core.plugin.scope`, 形状同 `RuntimeResourceScope` 的 own / track / 阶段, 由引擎的 `destroy` / `forceStop` 与 binderDied 路径释放); 进程级表 (连接池, 执行注册表, 会话表) 按第一期 T7 弱引用或按执行 id 清理且有场景覆盖; `AGENTS.md` "脚本资源与静态引用" 一节补一句 (该文件在 `.gitignore` 中, 只改本地) | P1.2 (Node), P2.2 / P3.2 (其余) |
| T7 | 守卫 (D8 (1)): `static_audit.py` 的 `SCRIPT_PATHS` 加 `engine/` 全部与 `core/plugin/`, 修正单行 `companion object` 的扫描; `StaticReferenceGuardTest` / `StaticReferenceScanner` 同步; (a) 类型清单加 `ServiceConnection` 与 Binder 回调接口 (静态持有 Binder 代理或连接的表); 白名单新增行逐条写理由 (`paired` / `owner` / `weak` / `p1` 等), 处置后 `py -3.12 -I tools/memory/static_audit.py --guard` 更新生成列; 不碰其他会话在改的白名单行 | P4.1 |
| T8 | CI (D8 (3)): `memory-gate.yml` 加 `workflow_dispatch` 输入 `suite` (`runtime` 默认 / `bridge`), `bridge` 时下载五个插件最近发布的 x86_64 包 (tag 与 sha256 固定在 env, 用 `gh release download`, 与 OpenCV / 3-ImGui 同款) 并经 `ci_gate.py --plugin` 安装, 跑 bridge 套件正常结束模式, ND13 跳过 (模拟器没有 ColorOS); tag 推送只跑 `runtime`; 估计 40-60 分钟 | P5.1 |
| T9 | 报告与基线: 附录 A 按场景记录 "残留 / Java 堆 / 原生堆 / 线程 / 绑定计数 / 引擎数 / 插件 PSS", 每格写数值与报告文件名; 修前基线一列永久保留; 每批次结束跑该批次引擎的全部场景两种模式于三台门禁设备 | 无 |
| T10 | 设备纪律: 跑前在设备占用登记表 `C:/Users/wu2du/AppData/Roaming/Claude/side-session-notes/local_8ab23cdc-d795-4973-9379-2554a76c2967/locks.md` 登记一行 (设备, 子会话名, 时间, 用途, 预计时长), 跑后追加释放行; 与 memory 主题的子会话不同时占用同一台; 本期不用 XQ-AT72 / G8441 / AVD API 24; 其余同第一期 R6 | 无 |

---

## 4. 红线

第一期 R1-R6 全部适用 (不改脚本可见行为; 不以更早释放换更早崩溃, 自动回收只作用于已结束的执行; 门禁不倒退; 共享工作区与提交纪律, 新建 Kotlin 文件编译通过前不放入 `app/src`; ASCII 标点与 10 种语言字符串资源; 设备纪律). 本期追加:

- R7 插件改动纪律: 插件仓库只本地提交, 不推送, 不发版, 不改版本号 (构建号自增除外); 每处改动记附录 D (仓库, 提交 hash, versionCode, 用途); 门禁报告必须写明所用插件的 versionCode; 宿主侧能修的不改插件.
- R8 不使第一期倒退: 本期任何宿主改动在提交前至少在 23046RP50C 跑一次第一期 S1-S16 两种模式 (`run_gate.py` 默认套件), 结果不得出现新的失败格; 6.8.0 发布前合入的改动另在 AVD API 35 跑一次第一期 CI 同款 (`ci_gate.py`).
- R9 不动插件进程的判定: 插件进程 PSS 只记录; 不把插件侧内存问题当宿主问题修, 发现时登记到对应引擎文档并在附录 C 记去向.

---

## 5. 阶段任务

### P0 门禁骨架与 Node 基线 (D5 / D6 / 第一期 D6)

- [x] P0.1 (文档) 本文: 现状盘点, 11 项决策, 派生决策, 红线, 阶段任务, 附录模板; 第一期附录 C Q53 行回填本文位置; 看板 `../.claude/boards/memory-phase2.md`. 证据: 本文件.
- [ ] P0.2 (测试 + 工具) `BridgeLeakGateDeviceTest` 骨架 (T3): `BridgeTracker` (engine + 会话对象弱引用, 绑定计数 (h) 的宿主侧注册表与系统侧探针), 场景 ND1 跑通 "预热 2 + 执行 20 -> GC -> 弱引用断言 + 度量 -> JSON 报告", 失败时 hprof + shark 链 (T4); `StaticRegistries` 扩展桥接注册表计数; `run_gate.py --suite bridge` (场景表 / 测试类 / 输出目录按 suite, D9 的 `hidden_api_policy` 与 D11 的 `pm grant` 放行步骤, `test_run_gate.py` 补用例); `tools/memory/README.md` 补 bridge 一节. 验收 (E3): ND1 两种模式在 23046RP50C 跑通并产出报告; 第一期 `run_gate.py` 默认套件行为不变 (`test_run_gate.py` 全部通过).
- [ ] P0.3 (测试) Node 场景 ND1-ND13 (T2) 全部实现; 插件安装按 T5 (Node 插件仓库本地构建); 需要维护者确认的授权 (OPD2413 的悬浮窗权限若未授过) 停下来请维护者在手机上操作. 验收 (E3): 13 个场景两种模式在 23046RP50C 全部能运行 (失败是预期的, 不能是 error), ND13 在 OPD2413 运行.
- [ ] P0.4 (基线) 23046RP50C, OPD2413 (ND13 + 全部场景各一次), AVD API 35 (D10 自建) 跑 ND1-ND13 两种模式, 结果填附录 A "修前" 列 (预期多个场景残留 > 0); 报告 `docs/memory-phase2/evidence/gate-*-mp2-baseline-*.md`; 发现的残留按 1.3 的编号登记 (新发现追加 C17 起), 引用链写进 1.3 对应行; 线性泄漏 (每次执行残留递增) 按 D3 登记附录 C 问是否提前修. 验收: 三份报告 + 附录 A 修前列 + 1.3 处置列初稿.
- [x] P0.5 (工具) 静态审计扩范围 (T7 的 `static_audit.py` 部分, 不改守卫测试): 输出 `docs/memory-phase2/evidence/static-audit-bridge-<date>.md` (桥接目录内的静态字段, 注册表, `bindService` / `registerReceiver` / `WebView(` / `Handler` / `ThreadLocal` 配对情况); 回填附录 B.1 / B.2 初稿. 验收 (E0): 报告文件 + 附录 B; `static_audit.py` 对第一期范围的输出不变 (`--guard` 生成列与现有白名单一致).
  - 证据 (2026-10-22, mp2-guard, E0): `static_audit.py` 的 `SCRIPT_PATHS` 加 `engine/` 与 `core/plugin/`; 单行 `object X { ... }` / `companion object { ... }` 的体也读; 新增 Binder 类别 (`ServiceConnection` / `IBinder` / `Binder` / `IInterface` / `DeathRecipient` 与 `app/src/main/aidl`, `plugin-api` 里 AIDL 声明的接口); 新增 `--bridge` 模式输出 `evidence/static-audit-bridge-20261022.md`: 647 个文件按引擎分组, 进程级字段 421 个 (报告 265 个, 略去 156 个常量), 类型推断不出的声明 54 个, 有登记调用的文件 76 个; 配对表覆盖 `bindService` / `registerReceiver` / `linkToDeath` / `WebView(` / `WindowManager.addView` / `Handler(` / `postDelayed(` / `HandlerThread(` / `ThreadLocal`, 粘性广播读取 2 处单列. 第一期对照: `--guard` 原有 118 行全部保留, 0 行删除; 生成列有变化的 20 行都在新纳入的两个目录 (19 个调用行的归属 `process` -> `script`, `RuntimeAgentProvider.sessions` 识别为 Binder 强集合); 两个目录之外只多了 Binder 规则带来的 `WrappedShizuku.mUserServiceConnection / service` 2 行; 共新增 41 行, 理由由 P4.1 写. 附录 B.1 (21 行) / B.2 (补 8 行) 已回填, 新疑点 C17-C20 登记在 1.3.

### P1 Node 修复与会话登记 (D2 / D5 / T6)

- [ ] P1.1 (修复) P0.4 报出的 Node 残留逐个清零: 每个 C 编号一次提交, 提交信息引用编号; 优先宿主侧; 需要改 Node 插件的按 D7 / R7 记附录 D. 验收 (E3): 每修一项在 23046RP50C 重跑对应场景两种模式残留 0, 全部修完后跑 ND1-ND13 全矩阵; R8 的第一期回归一次.
- [ ] P1.2 (结构) Node 桥接会话对象接 `RuntimeResourceScope` (T6): `runScript` 的 finally 释放改为经作用域阶段释放 (顺序不变), UI / Compose / Web / 截屏 / 悬浮窗 / 对话框 / 图像句柄 / 录音会话在创建时 `own` 或 `track`; 进程级表按 T7 规范 (弱引用或按执行 id 清理); 现有 Node 桥接 androidTest 保持通过. 验收 (E3): 全矩阵通过 + `NodeRuntimePluginBridgeInstrumentationTest` 等现有用例通过 + JVM 单测覆盖作用域释放顺序.
- [ ] P1.3 (基线) 三台设备跑 ND1-ND13 两种模式, 附录 A "P1 后" 列; 1.3 的 Node 行处置列定稿.

### P2 Bun + QuickJS (D2)

- [ ] P2.1 (测试 + 基线) BN1-BN5 / QJ1-QJ5 场景 (T2; BN5 / QJ5 为 OPD2413 专场, Q2 已决), 两家插件按 T5 安装, 三台设备修前基线填附录 A.
- [ ] P2.2 (修复 + 结构) 残留清零; `AidlPluginHost` 连接池与专用租约核对 (C10); `BridgeExecutionScope` 落地并由两家引擎使用 (T6); R8 回归.
- [ ] P2.3 (基线) 附录 A "P2 后" 列, 1.3 的 C10 / C13 / C14 处置.

### P3 Lua + Python (D2)

- [ ] P3.1 (测试 + 基线) LU1-LU4 / PY1-PY4 场景, 两家插件按 T5 安装, 三台设备修前基线.
- [ ] P3.2 (修复 + 结构) 残留清零; 专用绑定传输的异常路径 close 核对 (C11 / C12); 两家引擎接 `BridgeExecutionScope`; Python 的图像与无障碍节点作用域对照第一期 P3.4; R8 回归.
- [ ] P3.3 (基线) 附录 A "P3 后" 列, 1.3 的 C11 / C12 处置.

### P4 守卫与规范 (D8)

- [x] P4.1 (守卫) `StaticReferenceGuardTest` 与 `static_audit.py --guard` 扫描范围扩到桥接目录 (T7), 白名单新增行写理由, 进入全量单测 (`:app:testAppDebugUnitTest` 全部通过, 他人在途文件的失败另记). 可在 P0.5 之后随时做, 与 P1-P3 并行. 验收 (E1).
  - 证据 (2026-10-22, mp2-guard, E1): `StaticReferenceScanner` 同步 P0.5 的规则 (`engine/` 与 `core/plugin/` 算脚本包, 单行 object 体, Binder 类别, AIDL 接口取自 `app/src/main/aidl` 与 `plugin-api`); `StaticReferenceGuardTest` 加过渡理由 `p1` / `p2` / `p3` (说明须含 C 编号) 与桥接样例 (单行 `companion object`, `ServiceConnection`, AIDL 接口); 白名单头部说明同步. `--guard`: 159 个生成行 (116 字段, 43 调用) + 8 个手写行, 新增 41 行全部写了理由: `process` 18, `table` 9, `exempt` 5, `page` 4, `p1` 4 (C2 `NodeJsRuntimePluginBridge.activeModuleSourceProviders`, C3 `NodeAudioRecordingService.entries`, C17 `RuntimeComposeActivity.sessions` / `RuntimeInteropActivity.windows`), `p3` 1 (C12 `PythonLongRunningExecutionRegistry.records`); `PluginAutoWake.locks / wakeCounts` 按 C19 记 `process` 并在说明写 C19; 5 行 `exempt` 是扫描误报 (`AiProvider*` 的 3 行是无体 `object X : Y()` 之后的类体属性, `PluginToolRegistry.providers / tools` 是没有幕后字段的 getter). `--guard --check` 为 current; 按 HEAD 源码重算与工作区 (含他人在途的 `engine/` 改动) 生成行相同. 测试: 单类 `StaticReferenceGuardTest` 9/9 通过; 全量 `:app:testAppDebugUnitTest` 913 个类 5553 个用例, 0 失败 0 错误 (26 跳过), 无他人在途文件的失败. 过渡理由由对应阶段 (P1 / P2 / P3) 核对疑点后改成最终理由, P4.2 定稿时白名单应不再有 `p1` / `p2` / `p3`.
- [ ] P4.2 (规范) T6 的规范定稿: `AGENTS.md` 补句 (本地), 本文附录 B 的 "处置" 列全部回填, 五家引擎的会话作用域在附录 F 有路径. 验收 (E0).
- [ ] P4.3 (巡检) LeakCanary 巡检: 五个引擎各以 `leakcanary_probe.py` 的方式多次运行一个基础场景与一个专项场景 (经 `RunIntentActivity`, 不在 instrumentation 内), 无新报告; 诊断页面 (第一期 P2) 的 retained 列表在桥接脚本结束后为空. 验收 (E4).
- [ ] P4.4 (诊断) Q1 已决 (2026-10-22, 维护者选 (a)): `MemoryDiagnostics` 加 "未回收桥接会话数" 与 retained 列表的引擎列 (按 T1 (a) 的会话对象类名识别), 脚本 API `memory.snapshot()` 加 `bridgeSessions` 字段 (每引擎的未回收会话数), 开发者选项 "内存诊断" 页面同步显示; 文档站与 d.ts 同步 (AGENTS.md "关联项目" 一节); 可与 P4.3 同一子会话. 验收 (E3): 桥接脚本结束后字段为 0, 测试中人为持有会话对象时非 0; JVM 单测覆盖字段.

### P5 CI 与收尾 (D8 / D1)

- [ ] P5.1 (CI) `memory-gate.yml` 的 `suite` 输入与插件下载 (T8), `ci_gate.py` 的 bridge 分支与 `test_ci_gate.py` 用例; 本机同款 AVD 跑一次 bridge 套件通过, 报告 `gate-*-mp2-ci-local-*`; actionlint 无错. 真正的 run 链接等维护者推送后手动触发再回填.
- [ ] P5.2 (终表) 三台门禁设备跑五家全部场景两种模式, 附录 A 终表; XQ-DQ72 / 22120RN86C 各跑一次 ND1 + ND5 冒烟 (不作门禁).
- [ ] P5.3 (收尾) 本文附录全部回填; 未决项转附录 C 或 issue; 第一期附录 C Q53 行补 "已完成" 与终表位置; changelog 条目核对 (按 D3 的版本归属); 插件仓库的本地提交清单交维护者决定推送与发版 (附录 D).

---

## 附录 A 基线数据 (P0.4 回填 "修前" 列, 之后每阶段追加一列)

每格: 残留 / Java 堆 MB / 原生堆 MB / 线程 / 绑定计数 / 引擎数 / 插件 PSS MB, 两种模式分行 (N / F); 报告文件名写在列头下方说明. 场景 id 与定义见 T2.

| 场景 | 修前 (23046RP50C) | 修前 (OPD2413) | 修前 (AVD API 35) | P1 后 | P2 后 | P3 后 | 终表 (P5.2) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ND1 空脚本 | | | | | | | |
| ND2 宿主能力调用 | | | | | | | |
| ND3 回调型 | | | | | | | |
| ND4 插件进程被杀 | | | | | | | |
| ND5 UI 会话 | | | | | | | |
| ND6 Compose | | | | | | | |
| ND7 Web 运行时 | | | | | | | |
| ND8 截屏 | | | | | | | |
| ND9 悬浮窗 | | | | | | | |
| ND10 对话框 | | | | | | | |
| ND11 图像句柄 | | | | | | | |
| ND12 音频录制 | | | | | | | |
| ND13 ColorOS 被拒循环 | (跳过) | | (跳过) | | | | |
| BN1-BN5 (BN5 仅 OPD2413) | (P2.1) | | | | | | |
| QJ1-QJ5 (QJ5 仅 OPD2413) | (P2.1) | | | | | | |
| LU1-LU4 | (P3.1) | | | | | | |
| PY1-PY4 | (P3.1) | | | | | | |

---

## 附录 B 桥接注册表与配对清单 (立项初稿, P0.5 审计回填, P1-P3 回填处置)

### B.1 进程级静态表

已在守卫白名单 (`static-reference-whitelist.txt`) 的 13 处: `NodeJsRuntimePluginHost.connectionPool / executionRegistry / observerContext`, `NodeBridgeEngineDispatchService.registeredExecutions`, `NodeBridgeUiProvider.sessions`, `NodeDialogs.sessions`, `RuntimeWebActionActivity.actions / captures / grants`, `RuntimeWebLocalDownloads.active`, `RuntimeWebMediaService.entries`, `PythonRuntimeExecutionQueue.queue`, `PythonRuntimeProviderSelectionPolicy.evaluations`.

不在白名单的疑似静态表 (P0.5 核实): `NodeJsRuntimePluginBridge` 的 `activeModuleSourceProviders / activeExecutionIds / cancelledExecutionIds / backendExecutionIds`, `NodeAudioRecordingService.instance / entries`, `RuntimeWebMediaService.instance`, `LuaHostStorage` companion `LOCKS`, Bun / Lua / QuickJS 的 `*BoundedBinderCallLane`, `PythonRuntimeClient` 的 `PROVIDER_RPC_EXECUTOR / INPUT_EXECUTOR`, `AidlPluginHost.connectionPool` (挂在 Bun / QuickJS 的 object 上).

P0.5 核实 (2026-10-22, 报告 `evidence/static-audit-bridge-20261022.md`): 扩范围后守卫规则选中而白名单还没有的, 桥接部分是 `activeModuleSourceProviders` (Binder 值), `NodeAudioRecordingService.entries`, `PythonLongRunningExecution` 的 `records`, 以及第一期因单行 `companion object { ... }` 漏读的 `RuntimeComposeActivity.sessions` / `RuntimeInteropActivity.windows` (C17), 由 P4.1 写理由; 其余几处是字符串集合, 单值或线程池, 守卫规则不选, 逐个列在下表. `core/plugin/` 里与脚本执行无关的进程级缓存 (插件中心, 工具注册, 状态监视器等) 不列在这里, 见 P4.1 白名单的 `process` / `page` / `table` 行.

| 表 | 键 -> 值 (强 / 弱) | 清理时机 | 处置 | 阶段 |
| --- | --- | --- | --- | --- |
| `NodeJsRuntimePluginHost.connectionPool` | `ComponentName` -> 池化连接 (强, 持 Binder 与 `ServiceConnection`) | 不按空闲解绑 (设计); `invalidateEntry` / `retireEntry`, `binderDied`, `onServiceDisconnected` / `onBindingDied` 时移除 | | P1 (C1) |
| `NodeJsRuntimePluginHost.executionRegistry` | 执行 -> 登记 (强) | `reserveExecution` / `releaseExecution` 配对 | | P1 (C1) |
| `NodeJsRuntimePluginHost.observerContext` | 单值, Application context | 进程级, 不清理 | | - |
| `NodeJsRuntimePluginBridge.activeModuleSourceProviders` | 执行 id -> `INodeJsModuleSourceProvider` (强, Binder) | `runScript` 请求的 finally 与 `cancelScript` 移除 | | P1 (C2) |
| `NodeJsRuntimePluginBridge.activeExecutionIds / cancelledExecutionIds / backendExecutionIds` | 执行 id 字符串集合 | 请求的 finally 移除; `cancelledExecutionIds` 只在 id 仍活跃时加入, 不会只增不减 | | P1 (C2) |
| `NodeBridgeEngineDispatchService.registeredExecutions` | token -> 分发登记 (强) | 执行结束移除, 上限 `MAX_REGISTERED_DISPATCHES` | | P1 (C15) |
| `NodeBridgeUiProvider.sessions`, `NodeDialogs.sessions` | id -> UI 会话 / `NodeDialogSession` (强, 持 Activity) | 会话关闭或对话框结束移除, `closeAll` / 按作用域取消 | | P1 (C7) |
| `RuntimeComposeActivity.sessions` (`RuntimeComposeBridge.kt`) | id -> `Session` (强, 持 Activity / renderOwner / images) | `Session.close` 移除; 桥接 close 关闭该执行的全部会话 | | P1 (C7 / C17) |
| `RuntimeInteropActivity.windows` (`RuntimeUiInterop.kt`) | id -> `Window` (强, 持 Activity 与根视图) | `Window.close` 移除 (实例级 `windows` 另有 2 s 墓碑, 由 `postDelayed` 移除) | | P1 (C7 / C17) |
| `RuntimeWebActionActivity.actions`, `RuntimeWebFileAccess.grants / captures`, `RuntimeWebLocalDownloads.active` | 请求 id / uri -> 会话, 授权, 文件, 下载 (强) | 操作结束, 授权释放, 下载结束时移除 (第一期 P4.2 `owner`) | | P1 (C4) |
| `RuntimeWebMediaService.entries / instance` | id -> 媒体会话 (强); 单值 Service | 会话的 Closeable 或 owner 移除, 服务销毁清空; `instance` 在 `onDestroy` 置空 (`instance === this`) | | P1 (C3) |
| `NodeAudioRecordingService.entries / instance` (`NodeAudioRecorder.kt`) | id -> 录音 `Entry` (强, 含回调); 单值 Service | release 与失败路径移除, `onDestroy` 清空; `instance` 在 `onDestroy` 置空 | | P1 (C3) |
| `RuntimeAgentProvider.sessions` | 客户端 -> 委托会话 (强, Binder) | dispose, `binderDied`, 撤销授权时移除 (第一期 `owner`) | | - |
| `AidlPluginHost.connectionPool` (经 `BunRuntimePluginClient.host` / `QuickJsRuntimePluginClient.host` 等 object 持有的实例字段) | `ComponentName` -> 池化连接 (强) | 空闲 30 s 解绑 (`idleUnbindMs`), `binderDied`; 专用租约 close | | P2 (C10) |
| `Bun / Lua / QuickJS` 的 `*BinderCallLane` object | 单个 lane, `pending` / `active` 各持一个 `Call` (闭包可能捕获执行对象) | worker 取走 `pending`, 调用结束 release 清 `active`; 超时放弃的调用在 Binder 返回前仍由 worker 持有 | | P2 / P3 (C14) |
| `LuaHostStorage.LOCKS` | 存储主体摘要 -> 锁对象 (强, 字符串键) | `computeIfAbsent` 加入, 从不移除 (按存储主体有界) | | P3 (C11) |
| `PythonRuntimeExecutionQueue.queue` | FIFO 票据, 不持脚本对象 | 出队 | | P3 (C12) |
| `PythonLongRunningExecution` 的 `records` (`PythonLongRunningExecution.kt`) | token -> `Record` (强, 持 controller 与 `stopExecution` 闭包) | unregister 移除 | | P3 (C12) |
| `PythonRuntimeClient.PROVIDER_RPC_EXECUTOR / INPUT_EXECUTOR` | 进程级线程池, 任务结束后不持会话 | 不关闭 (设计) | | P3 (C12) |
| `PythonRuntimeProviderSelectionPolicy.evaluations` | 扫描误读 (实际是 `NoMatch` 的实例字段) | - | | - |
| `PluginAutoWake.locks / blockingLocks / wakeCounts` | 包名 -> `Mutex` / 锁对象 / 计数 | 从不移除 (卸载后也保留); 不持会话 | | P2 (C19) |

### B.2 配对清单 (登记 -> 释放)

| 登记 | 释放 | 文件 | 门禁场景 | 处置 |
| --- | --- | --- | --- | --- |
| `bindService` (Node 池) | `unbindService` (`retireEntry` / `postUnbind`) | `NodeJsRuntimePluginHost.kt`, `NodeRuntimePluginBindCall.kt` | ND1 / ND4 / ND13 | |
| `bindService` (`AidlPluginHost` 池与租约) | 空闲 30 s 解绑 / 租约 close | `core/plugin/AidlPluginHost.kt` | BN1 / QJ1 / BN4 / QJ4 / BN5 / QJ5 | |
| `bindService` (Lua 专用) | `ExactComponentBinderLease.close` -> `unbindService` | `AndroidLuaRuntimeProviderTransport.kt` | LU1 / LU4 | |
| `bindService` (Python 专用) | `Transport.Binding.close` -> `unbindService` | `PythonRuntimeOfficialTransport.kt` | PY1 / PY4 | |
| `WebView(` | `RuntimeWebView.release()` | `RuntimeViewComponents.kt` | ND7 | 第一期 Q42: `paired` |
| MediaProjection (截屏) | `NodeScreenCaptureSession.close` | `NodeScreenCaptureSession.kt`, `NodeBridgeProtocol.kt` | ND8 | |
| `WindowManager.addView` (悬浮窗) | `removeView` | `NodeBridgeProtocol.kt` (`ControlledOverlaySession`), `RuntimeComposeBridge.kt` | ND9 / ND6 | |
| Activity 会话表 (`NodeDialogs.sessions`, `NodeBridgeUiProvider.sessions`, `RuntimeComposeActivity.Session`) | 会话关闭 / Activity 销毁 | 同名文件 | ND10 / ND5 / ND6 | |
| 录音 (`NodeAudioRecordingService.entries`) | 停止 / 服务销毁 | `NodeAudioRecorder.kt` | ND12 | |
| 相机与线程 (`RuntimeWebCameraRecording`, `ScreenRecordingFrames`) | 释放 / `quitSafely` | 同名文件 | ND7 (可选扩展) | |
| `linkToDeath` (29 个文件 35 处) | `unlinkToDeath` | P0.5 报告各引擎配对表; 全部同文件有释放 | 各引擎的 binderDied / 插件被杀场景 | |
| 主线程 `Handler(` / `postDelayed(` (五家宿主类) | `removeCallbacks` | `NodeJsRuntimePluginHost.kt` (2 处 `postDelayed`), `BunHostCapabilityBroker.kt`, `LuaHostUi.kt`, `PythonHostCapabilityBroker.kt`, `PythonInteractiveInput.kt` (同文件都没有 `removeCallbacks`) | ND1 / BN1 / LU1 / PY1 | C13 |
| 会话与服务的 `Handler(` / `postDelayed(` (Node, 12 个文件) | 一次性投递自然结束; 延时 Runnable 没有 `removeCallbacks` | 见 C18 | ND5 / ND6 / ND8 / ND12 | C18 |
| `ThreadLocal` (`RuntimeJavaSession.nativeDepth`) | `remove()` (没有) | `RuntimeJavaSession.kt` | - | C20 |
| `registerReceiver` (进程级单例: `NodeJsRuntimePluginHost`, `RuntimeAgentProvider`, `ThreeStoveAgentConnectionOwner`, `PluginLauncherIconController`, 四个 `*PluginStateMonitor`) | 不注销 (进程生命周期, 设计) | 同名文件 | - | 第一期白名单 `process` |
| `registerReceiver(null, ...)` 粘性广播读取 (`PythonHostCapabilityBroker.kt`, `NodeBridgeDeviceInfo.kt`) | 不注册接收器, 无需释放 | 同名文件 | - | 第一期白名单按 `process` 记 |
| `bindServiceWaking` (`PluginAutoWake.kt`) | 调用方 unbind (`Plugins.unbindAll`, 各插件宿主会话) | `PluginAutoWake.kt` | ND13 / BN5 / QJ5 | 第一期白名单 `exempt` |
| `HandlerThread(` (2 处), `WindowManager.addView` (2 处) | `quitSafely` / `removeView` | `RuntimeWebCameraRecording.kt`, `ScreenRecordingFrames.kt`; `NodeBridgeProtocol.kt`, `RuntimeComposeBridge.kt` (同文件都有释放, 见上面两行) | 同上 | |

---

## 附录 C 待决事项

格式与第一期相同. 由调度会话每轮用选择题请维护者拍板; 子会话登记时写明编号, 问题, 选项 (标出推荐项与是否已先行), 影响的条目, 改选的返工量.

| 编号 | 问题 | 需要拍板的阶段 | 状态 / 默认倾向 |
| --- | --- | --- | --- |
| Q1 | 诊断页面 (第一期 P2) 与脚本 API `memory` (第一期 P5) 是否展示桥接会话: (a) P4 时在 `MemoryDiagnostics` 加 "未回收桥接会话数" 与 retained 列表的引擎列, `memory.snapshot()` 加 `bridgeSessions` 字段并同步文档与 d.ts (推荐, 未先行, 约 1 个子会话); (b) 不加, 只在门禁与 LeakCanary 可见. 改选 (b) 无返工 | P4.3 之前 | 已决 (2026-10-22 21:10, 维护者选 (a)): 落为 P4.4 |
| Q2 | ND13 的 ColorOS 绑定被拒循环是否也给 `AidlPluginHost` (Bun / QuickJS) 各加一个 OPD2413 专场场景: (a) P2.1 时各加一个 (推荐, 未先行, 每台多约 10 分钟); (b) 只做 Node. 改选 (b) 无返工 | P2.1 之前 | 已决 (2026-10-22 21:10, 维护者选 (a)): BN5 / QJ5 进 T2 与 P2.1 |
| Q3 | P0.4 基线若发现线性泄漏 (每次执行残留递增) 且 6.8.0 尚未发布: 是否提前修 (D3 规定登记后问, 不自动阻断). 选项届时由 P0 子会话按实测数据登记 | P0.4 之后 | 未发生 |

---

## 附录 D 插件仓库改动记录 (D7 / R7)

| 日期 | 仓库 | 提交 hash | versionCode | 用途 | 对应条目 | 推送 / 发版 |
| --- | --- | --- | --- | --- | --- | --- |
| | | | | | | 由维护者逐次指示 |

---

## 附录 E 证据等级

沿用第一期附录 E: E0 代码已提交且编译通过; E1 + JVM 单测; E2 + 门禁在 AVD API 35 通过; E3 + 门禁在真机 (23046RP50C 或 OPD2413) 通过, 记录机型, API, 宿主与插件 versionCode, 报告文件; E4 + LeakCanary 巡检无报告. 第 5 节默认要求: 宿主与插件代码改动 E3, 工具与文档 E0, P4.1 为 E1, P4.3 为 E4.

---

## 附录 F 文件地图 (立项时为计划路径, 各阶段回填实际路径)

| 路径 | 内容 | 阶段 |
| --- | --- | --- |
| `docs/memory-phase2/ROADMAP.md` | 本文 | P0.1 |
| `docs/memory-phase2/evidence/gate-<model>-<versionCode>-<label>-<date>.md` / `.json` | 门禁报告 (`<label>` 如 `mp2-baseline`, `mp2-p1`, `mp2-ci-local`) | P0.4 起 |
| `docs/memory-phase2/evidence/static-audit-bridge-20261022.md` | 桥接目录静态审计报告 (`py -3.12 -I tools/memory/static_audit.py --bridge` 生成) | P0.5 |
| `docs/memory-phase2/evidence/hprof/` (gitignore) | 拉回的 hprof | P0.2 |
| `app/src/androidTest/java/org/autojs/autojs/memory/bridge/BridgeLeakGateDeviceTest.kt`, `BridgeTracker.kt`, `BridgeScenarios.kt` | 第二期门禁测试, 跟踪器, 场景脚本 | P0.2 / P0.3 |
| `app/src/main/java/org/autojs/autojs/runtime/memory/StaticRegistries.kt` (已有) | 桥接注册表计数扩展 | P0.2 |
| `tools/memory/run_gate.py` (`--suite bridge`), `test_run_gate.py`, `README.md` | 驱动扩展 | P0.2 |
| `tools/memory/static_audit.py` | 扫描范围扩展, Binder 类别, 单行 object 体, `--bridge` 报告模式 | P0.5 / P4.1 |
| `app/src/main/java/org/autojs/autojs/core/plugin/scope/BridgeExecutionScope.kt` 与 JVM 单测 | 非 Node 引擎的执行作用域 | P2.2 |
| `app/src/test/java/org/autojs/autojs/memory/StaticReferenceGuardTest.kt`, `StaticReferenceScanner.kt`, `app/src/test/resources/memory/static-reference-whitelist.txt` | 守卫扩范围 | P4.1 |
| `app/src/main/java/org/autojs/autojs/runtime/memory/MemoryDiagnostics.kt`, 脚本 API `memory` 模块, 文档站与 d.ts | 桥接会话展示 (Q1 / P4.4) | P4.4 |
| `.github/workflows/memory-gate.yml`, `tools/memory/ci_gate.py`, `test_ci_gate.py` | CI bridge 套件 | P5.1 |
| `../AutoJs6-Plugin-{NodeJs,Bun,Lua,Python,QuickJS}-Runtime` | 插件仓库 (本地提交, 见附录 D) | P1-P3 |
| `../.claude/boards/memory-phase2.md` | 看板 | P0.1 |
