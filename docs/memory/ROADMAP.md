# 内存泄漏排查与根治路线图

更新日期: 2026-10-20

本文是 AutoJs6 宿主 "脚本结束后内存不释放 / 反复运行后内存持续增长" 这一长期难题的可执行状态表. 它以 2026-10-20 维护者经三轮选择题确认的 12 项决策 (第 2 节) 为依据, 记录现状证据 (第 1 节), 派生技术决策 (第 3 节), 红线 (第 4 节), 阶段任务 (第 5 节) 与附录 (基线数据, 静态注册表清单, 待决事项, Rhino fork 改动记录, 证据等级, 文件地图).

核心目标只有一句话: 任何脚本 (普通 / UI / 悬浮窗 / 无障碍 / 截屏 / 多线程) 以任何方式结束 (正常, 异常, 强制停止) 之后, 它的 `ScriptRuntime` 整条对象链能被 GC 回收, 原生内存 (Bitmap / Mat / media Image) 回到脚本启动前的水平, 并且这一点由自动化门禁持续证明, 而不是靠 "LeakCanary 报一个修一个".

使用方式:

1. 每次会话开始时, 从第 5 节选取未完成条目, 按阶段顺序推进; 条目完成后勾选 `[x]` 并追加证据 (提交 hash / 测试类名 / 设备型号与 API / 门禁报告文件名 / 关键数值). 证据等级见附录 E.
2. 第 2 节固定决策不再重新讨论; 第 3 节派生决策可在标注的推翻点之前推翻, 之后视同固定; 附录 C 待决事项在进入对应阶段前由维护者拍板, 结果回填到第 2 节或第 3 节.
3. 工程约定以仓库 `AGENTS.md` 与 `../COMMON_AGENTS.md` 为准 (ASCII 标点, 字符串资源 10 种语言, 构建号, 共享工作区, 宿主只本地提交). 本文只记录 "改什么" 与证据.
4. 每个阶段结束都要用 `tools/memory/run_gate.py` 跑完整场景矩阵 (T2), 报告存 `docs/memory/evidence/`, 并更新附录 A; 门禁不允许倒退 (R3).
5. 涉及 feature / fix / improvement 的条目须同步 `.changelog/lang_*.json` 并重新生成 markdown; 脚本 API (P5) 须同步文档仓库与 d.ts 仓库 (见 `AGENTS.md` > 关联项目).
6. 真机规则见 `../.claude/rules/device-testing.md`: 需要人工确认的对话框由维护者在手机上操作; 多步验证写成仓库内可复用脚本, 输出只保留通过 / 失败与关键数值.

---

## 0. 阶段总览

| 阶段 | 目标 | 完成条件 | 预计会话数 |
| --- | --- | --- | --- |
| P0 门禁与基线 | 自动化门禁能抓到泄漏, 三台真机 + AVD 有修前基线 | S1-S12 场景跑通, 附录 A "修前" 列填满, S2 预期失败 (证明门禁有效) | 2 |
| P1 已确认根因与 LeakCanary | H1 修复, LeakCanary debug 常驻, 门禁报出的 Java 堆残留逐个清零 | 附录 A 全部场景 "残留 runtime" 为 0 | 2-4 |
| P2 开发者选项 "内存诊断" | 用户可见的诊断页面与未回收警告 | 页面真机截图, changelog 条目 | 1-2 |
| P3 原生内存专项 | images / 截屏 / 无障碍节点的原生资源回到基线 | S4 系列原生增量达标, issue #372 / #200 复现脚本转绿 | 2-3 |
| P4 统一所有权与源码守卫 | 结构性防复发: RuntimeResourceScope, 静态注册表规范, JVM 守卫测试, Rhino 侧 Context / 生成类 | 守卫测试进入全量单测, 门禁不倒退 | 3-4 |
| P5 脚本 API memory 模块 | `memory.snapshot()` 等, 文档与 d.ts 同步 | 真机验证 + 文档站 + d.ts 发布 | 1-2 |
| P6 CI 与封版 | GitHub Actions 门禁, 全设备终表, 6.8.0 发布门 | CI 工作流通过一次, 附录 A 终表, 发布门条目 | 1-2 |

---

## 1. 现状与证据 (基线 `master@c35f603be`, `VERSION_NAME=6.8.0`, `VERSION_BUILD=3951`)

### 1.1 用户反馈与历史修复

| issue | 状态 | 现象 | 已知处置 |
| --- | --- | --- | --- |
| #548 (2026-05) | open | 带 UI 线程, 悬浮窗, 找图的脚本每次重跑后总内存与已用内存固定增加几 MB | 主干 `bcfb9467b4` (2026-06-20) 试修: `ImageWrapper.bgrMat` 缓存复用与中间 bitmap 回收, `ScriptEngineService` 不再记录未真正启动的 Activity 执行, `ScriptExecuteActivity.onDestroy` 断开 runtime / engine / listener / source; 6.8.0 changelog 条目带 "(试修)". 评论者 (2026-10-13) 点名 `LooperHelper.sLoopers` 静态表, 本地修复后连跑 3 天无崩溃; 该修复未进主干 (见 1.2 H1) |
| #403 (2025-05) | open | 执行多个任务后 App 内存一直上升, 问能否在脚本结束后清空引擎内存 | 维护者回复: 引擎是纯 Java 对象, 只有强引用全部断开才会回收; 可能原因是静态变量持有 Context / Scriptable, Activity Context 误用; 建议脚本显式 `exit()` |
| #372 (2025-04) | open | 多线程高频 `images.findImage`, RAM 7 GB, 结束脚本后仍占用, 只能在应用详情页停止 | 根因 (维护者 2025-07-01): 6.6.0 参考 Pro 引入的 MonitorResource / Cleaner 用 ConcurrentHashMap 强引用 ImageWrapper; 6.7.0 Alpha2 `3483784eb` 修, 现为 `WeakHashMap<ScriptRuntime, MutableList<WeakReference<ImageWrapper>>>` 注册表 + `oneShot()` |
| #200 (2023-12) | open | 多线程截图找色用 `images.copy` 并手动回收, 反复停止 / 启动后 `Bitmap.copy` OOM (6.5.0) | 无定位; 评论 2025-06 仍有人复现 "重复执行脚本内存一直在增加" |
| #110 (2023-07) | closed | 通过 javaAdapter 实现接口内存泄漏 | 已关闭, 现行代码未复核 (见 1.3 H3) |
| #452 (2025-09) | open | 怎么获得 AutoJs6 剩余内存 | 对应 P5 的 `memory` 模块 |
| #474 (2025-12) | open | shizuku-service-autojs6 僵尸进程导致 OOM | 进程外问题, 不在本文范围 (附录 C Q4) |
| #204 (2023-12) | open | `captureScreen` / `ocr.detect` 卡住不返回 (6.5.0 起) | 与 ImageReader 未关闭的 media Image 相关的嫌疑 (1.3 H14), P3.3 顺带验证 |

历史上与内存泄漏相关的提交 (`git log -i --grep`): `27ae94df4` fix memory leak, `989fa455d` simple action leak, `36356e4b2` selector leak, `5d672281e`, `cc15c92c4` remove leak canary, `f0bfbef31` app memory leak + 前台服务, `4d2047474` sensors 回调留在 loopers ThreadLocal 导致 ScriptExecuteActivity 泄漏, `5e9d3bde8` 多线程未调用 `Context.exit()`, `7eabcb5d1` 多点找色, `9139cbd37` opencv, `3483784eb` images (#372), `8f8c63c27` toast 强引用, 6.6.x 的 DrawerFragment / ExplorerFragment View Binding 泄漏, ScreenCapture / ThemeColorPreference 实例泄漏, TemplateMatching Mat 泄漏, `bcfb9467b4` (#548 试修). 共同点: 全部是 "某处报出一条引用链, 修一处", 没有一个可重复的门禁证明修复有效, 也没有防止下一个版本再引入. 这正是 "随版本迭代越来越严重" 的原因: 6.6.0 之后新增的模块 (compose / flow / ai / mail / epub / gallery / speech / terminal / 插件桥接) 各自实现 `close()`, 由 `ScriptRuntime.onExit` 手写调用 (当前 30 余处 `ignoresException`), 任何一个模块漏登记就是一条新的泄漏链.

### 1.2 已确认根因 (2026-10-20 代码阅读)

H1 `LooperHelper.sLoopers` 永久钉住子线程所属的 ScriptRuntime:

- `core/looper/LooperHelper.java`: `private static volatile ConcurrentHashMap<Thread, Looper> sLoopers`; `prepare()` 把当前线程与 Looper 放入表, 只有 `quitForThread(thread)` 会 `remove`, `quit(looper)` 只 `quitSafely()` 不移除. 文件自 6.6.0 (`153f642f29`) 未改.
- `core/looper/TimerThread.kt` (`threads.start()` 创建的线程): `run()` 开头 `scriptRuntime.loopers.prepare()` (间接 `LooperHelper.prepare()`, 并把 `Loopers` 作为 IdleHandler 加到该线程的 MessageQueue), 结束时只做 `mTimer?.removeAllCallbacks()`, `onExit()`, `Context.exit()`, `sTimerMap.remove`, 没有 `LooperHelper.quitForThread(currentThread())`; `interrupt()` 走 `LooperHelper.quit(looper)`, 同样不移除.
- 只有主脚本线程经 `LoopBasedJavaScriptEngine.destroy()` -> `LooperHelper.quitForThread(thread)` 正确移除.
- 引用链: `LooperHelper.sLoopers` (静态) -> `Looper` -> `MessageQueue` -> `mIdleHandlers` -> `Loopers` -> `Loopers.scriptRuntime` -> `ScriptRuntime` 的全部 64 个字段 (引擎, 作用域, 定时器, 图像, UI, 插件桥接...). 对 UI 脚本还会经 `ScriptExecuteActivity` 钉住整个 Activity 与 View 树.
- 结论: 每个用过 `threads.start()` 的脚本, 无论子线程正常结束还是被中断, 都整份留在堆里; 高频起线程 + `engines.execScriptFile` 重启自身的脚本 (issue #548 的用法) 线性累积直到 OOM. 修复在 P1.1.

### 1.3 可疑点清单 (待门禁验证)

以下各项由代码阅读得出, 没有真机证据, 按 "门禁报出 -> shark 引用链 -> 修复" 的流程逐个确认或排除; 编号在第 5 节与附录 A 中引用.

| 编号 | 位置 | 证据 | 假设的引用链或后果 | 验证场景 (T2) |
| --- | --- | --- | --- | --- |
| H2 | `Loopers` IdleHandler | `Loopers.prepare()` 对每个调用线程 `addIdleHandler(this)` (全库唯一的 addIdleHandler), `recycle()` 只从主脚本线程队列移除 | 子线程队列上的 IdleHandler 与 H1 同链; 异常路径未走到 `recycle()` 时主线程队列也残留 | S2 / S3 / S5 强制停止模式 |
| H3 | `rhino/AndroidClassLoader` (进程级 `ContextFactory.getGlobal()` 的应用类加载器, 实现 `GeneratedClassLoader`) | `mDexClassLoaders: ConcurrentHashMap<String, DexClassLoader>` 只在两处 `remove`; `JavaAdapter` / `defineClass` 生成的类进入进程级加载器, 类对象不可卸载 | 每次 `new JavaAdapter(...)` / 扩展 Java 类都留下一个 Class 与其静态字段; `runtime.loadDex` / `loadJar` 的加载器与其 dex 文件映射永不释放 (issue #110 的方向) | S8 |
| H4 | Rhino `Context` 线程簿记 | `RhinoUtils.withRhinoContext` 在非脚本线程以 `Context.enter()` 兜底 (augment 路线图 1.2 自认), 线程池 / 主线程 / servant looper 上的 Context 不一定 `exit`; `AndroidContextFactory.onContextCreated / onContextReleased` 有计数日志 | 未退出的 Context 持 `lastInterpreterFrame`, 顶层作用域缓存, 把作用域钉在长寿线程上 | 全部场景的 "Context 计数" 度量 (T1 e) |
| H5 | `core/image/capture/ScreenCapturer.java` | `HandlerThread("ScreenCapturer-ImageReader")`, `DisplayManager.registerDisplayListener`, `MediaProjection.registerCallback`, `finalize()` 兜底 | 任一未释放即 VirtualDisplay / ImageReader / 线程残留, 并经 handler 钉住 runtime; 6.6.x 修过一次实例泄漏 | S4 两种模式, 线程度量 |
| H6 | `ScriptEngineService` 全局执行监听器 | 10 处注册 / 4 处注销: `external/open/ProjectArchive.kt:132` 每次归档运行注册匿名监听器 (注销在 `finished` 内, 需核对异常路径), `ui/floating/controller/ScriptExecutionController`, `ui/main/task/TaskListRecyclerView`, `NodeBridgeEngineDispatchService` 成对; `AutoJs.kt` 进程级 | 监听器持 UI 对象 (View / Activity) 或 File 句柄, 随脚本数累积 | P4.5 人工巡检 + S9 |
| H7 | 注册广播接收器未见注销 (13 个文件) | `AutoJs.kt`, `app/tool/McpServerTool.kt`, `core/plugin/agent/ThreeStoveAgentConnectionOwner.kt`, `core/plugin/center/PluginLauncherIconController.kt`, 4 个 `*PluginStateMonitor.kt`, `core/plugin/python/PythonHostCapabilityBroker.kt`, `engine/NodeBridgeDeviceInfo.kt`, `engine/NodeJsRuntimePluginHost.kt`, `util/DeviceUtils.kt` (`runtime/api/Device.java:214` 是 `registerReceiver(null, ...)` 读粘性广播, 排除) | 进程级单例可接受; 若其中有按脚本 / 按会话创建的接收器则随脚本累积 | P0.6 静态审计 + S11 |
| H8 | `bindService` 未见 `unbindService` (3 个文件) | `core/plugin/PluginAutoWake.kt`, `runtime/api/Plugins.kt` (脚本 `plugins` 模块, 以 `applicationContext.bindService(intent, plugin, BIND_AUTO_CREATE)` 绑定, `Plugin` 即 ServiceConnection), `runtime/api/tts/AndroidTtsBackend.kt` | 脚本结束后 ServiceConnection 仍被 ActivityManager 持有, 连同其持有的 runtime | S12 |
| H9 | RxJava `subscribe` 无 dispose (30 个文件) | 按脚本创建的: `core/accessibility/KeyInterceptor.kt`, `OnKeyListener.kt`, `AccessibilityTool.kt`, `core/record/GlobalActionRecorder.java`, `runtime/api/augment/terminal/TerminalSessionNativeObject.kt`; UI 侧: `ui/edit/EditActivity.kt`, `ui/explorer/ExplorerPageViewHolder.kt`, `ui/main/plugin/PluginFragment.kt`, `ui/main/drawer/DrawerMenuToggleableItem.kt` 等 | 订阅链持有 runtime 或 Activity | S7 (按键监听), P4.5 巡检 |
| H10 | WebView 创建 12 个文件, 全库只有 1 处 `destroy()` | `core/ui/inflater/inflaters/JsWebViewInflater.kt` / `WebViewInflater.kt`, `core/ui/widget/EventWebView.kt`, `core/web/InjectableWebView.kt`, `engine/RuntimeViewComponents.kt`, `runtime/api/augment/ui/UI.kt`, `runtime/api/augment/web/Web.kt`, `ui/doc/DocumentationPageController.kt`, `ui/edit/EditorDocumentationPaneController.kt`, `ui/widget/EWebView.java` / `NestedWebView.java` | 未 `destroy()` 的 WebView 持 Activity Context 与渲染进程资源, 是 Android 最经典的泄漏 | S5 (UI 脚本含 webview), P4.5 (文档页) |
| H11 | `core/image/ImageWrapper.kt` 持 `mScriptRuntime` 强引用 | 注册表键为弱引用, 但任何逃逸的 ImageWrapper (被静态缓存或其他脚本持有) 会反向钉住整个 runtime; 构造入口是否全部登记到注册表未核对 | 图像对象寿命 = runtime 寿命 | S4 |
| H12 | 引擎登记表 | `ScriptEngineManager.mEngines` (HashSet) 经 `OnDestroyListener` 移除, `ScriptEngineService.mScriptExecutions` 经 `onEngineRemove` 移除; `JavaScriptEngine.destroy()` 先 `mRuntime.onExit()` 再 `super.destroy()` | 若 `destroy()` 在 `super.destroy()` 之前抛异常 (`onExit` 内部已 `ignoresException`, 但 `RhinoJavaScriptEngine.destroy()` 先关闭 TypeScript 工程) 引擎留在表中 | 全部场景的 "存活引擎数" 度量 |
| H13 | UI 脚本 `ScriptExecuteActivity` | Activity 持引擎, 引擎 tag "activity", JS 全局 `activity` 与 `ui.xxx` 视图引用都在 Rhino 作用域里; `bcfb9467b4` 已在 `onDestroy` 置空宿主侧字段 | 只要作用域被任何一条链 (H1 / H3 / H4) 钉住, 整个 Activity 与 View 树一起泄漏, 这是 #548 "几 MB" 的来源 | S5 |
| H14 | 原生资源 | Bitmap (API 26+ 像素在原生堆, 随 Java 对象 GC 经 NativeAllocationRegistry 释放, 因此 Java 泄漏直接表现为原生增长), `core/opencv/Mat` (`finalize` 删头部, 数据引用计数), media `Image` (必须 `close()`, 否则 ImageReader 的 maxImages 用尽后 `captureScreen` 卡住, 即 #204 的现象), `AccessibilityNodeInfo` (API < 33 需 recycle), MediaProjection | 7 GB (#372) 与 `Bitmap.copy` OOM (#200) 都在这一层; `largeHeap=true` 只推迟 Java 堆 OOM | S4 系列, P3 |
| H15 | `runtime/api/Threads.kt` | `mThreads` 为 WeakHashMap 键集, `threadPools: HashSet<ThreadPoolExecutor>` 由 `shutDownAll` 关闭; 池线程若进入过 Rhino Context 而未退出则回到 H4 | 池线程残留 | S2 (含 `threads.pool`) |
| H16 | `core/accessibility/AccessibilityService` companion 总线 | 脚本回调按 owner id 由 `automator.removeAllEventsForThisRuntime()` 移除; 委托 / 观察者 / 按键监听 "故意比 service 实例活得久", 依赖 `events.recycle()` 等逐个清理; `scriptCallbackCount` 可作度量 | 任一类型漏清即钉住 runtime | S7, 度量总线计数回基线 |

### 1.4 现有诊断与测试基础设施 (可复用)

| 关注点 | 现状 | 本文用法 |
| --- | --- | --- |
| LeakCanary | `libs.versions.toml` 2.14; `app/build.gradle.kts` 以 gradle 属性 `autojs.leakcanary.enabled` (默认 false) 决定 debug 是否包含; `app/src/debug/.../leakcanary/LeakCanarySetup.kt` 经反射配置; release 自 6.5.x 排除 | P1.2 默认启用并加自定义 watch (D5 / T5); 门禁复用其 shark 库解析 hprof (T4) |
| StrictMode | `App.setUpDebugEnvironment` 仅在 API 36+ debug 开 `detectBlockedBackgroundActivityLaunch` | P1.2 debug 增加 `detectLeakedClosableObjects` / `detectActivityLeaks` / `detectLeakedRegistrationObjects` + `penaltyLog`, 作为门禁之外的日志线索 |
| 进程退出历史 | `tool/ProcessExitHistory.kt` + 开发者选项 "进程退出历史" (ApplicationExitInfo, 分类含 LOW_MEMORY / MEMORY_LIMITER / CRASH) | 现场证据: 用户报告 OOM 时先看这里; P2 页面链接到它 |
| 无障碍节点分配器 | `core/accessibility/AccessibilityNodeInfoAllocator.kt`: `registeredCount`, `recycleAll()`, 可选记录每个节点的分配栈 | P3.4 的度量来源 |
| Rhino Context 计数 | `rhino/AndroidContextFactory.onContextCreated / onContextReleased` 打印计数 | T1 (e) 度量: 改为可读计数器而非只打日志 |
| 同进程设备测试 | `androidTest/.../compose/ComposePerformanceDeviceTest.kt` (`AutoJs.instance.scriptEngineService.execute(StringScriptSource(...))`, `Debug.getMemoryInfo`, JSON 行输出, `.python/compose_performance.py` 收集), `NodeEmbeddedMemoryLeakStressInstrumentationTest.kt` (Node 插件侧压测门禁), `NodeR6PersistentPluginSoakInstrumentationTest.kt`, `DexCompilerR5PerformanceAndroidTest.kt` | 门禁测试类 (T3) 照此结构; androidTest 现有 385 个文件, 运行器 AndroidJUnitRunner |
| 真机驱动脚本 | `tools/augment/device_bench.py` (RunIntentActivity + logcat `GlobalConsole` 行), `tools/augment/device_developer_options.py` (进程内打开未导出的设置页并截图), `tools/sdk37/physical_device.py` (`Device` / `write_json`), `tools/sdk37/run_exit_history_probe.py` | `tools/memory/run_gate.py` 复用 `physical_device.py`; P2.2 截图复用开页方式 |
| 控制台日志上限 | `ConsoleImpl.MAX_LOG_ENTRIES = 4096` | 不是增长源, 排除 |
| 可用设备 | `../COMMON_AGENTS.md` > 可用测试设备: XQ-AT72 (API 31), XQ-DQ72 (API 33), G8441 (API 28, 低内存), 23046RP50C (API 35), 22120RN86C (API 33, Redmi), OPD2413 (API 35, ColorOS); 可租用 SM-A566B (API 36, 16 KB); 本机 AVD | 门禁设备选择见 T2; XQ-DQ72 debug 宿主有 ART JIT ANR 缺陷 (记忆 `qv770340j7-art-jit-anr`), 不作门禁主机 |

### 1.5 规模数字 (用于估计审计工作量)

- `app/src/main/java` 内 `object` / `companion object` 共 1230 处; `ScriptRuntime.kt` 1255 行, 64 个属性字段, `onExit()` 30 余处 `ignoresException` 清理调用.
- `ThreadLocal` 10 个文件; `Handler(Looper.getMainLooper())` 116 处; `EventEmitter` 34 个文件.
- `registerReceiver(` 23 个文件 对 `unregisterReceiver(` 10 个; `bindService(` 22 对 `unbindService(` 19; `.subscribe(` 55 对 dispose 类 33; WebView 创建 12 个文件 对 `destroy()` 1 处.
- 引擎形态: `RunnableScriptExecution` (普通脚本, 引擎在脚本线程 `finally` 中 `destroy()`), `ScriptExecuteActivity.ActivityScriptExecution` (UI 脚本, 引擎在主线程 `onDestroy` 中 `destroy()`); `LoopBasedJavaScriptEngine` 主线程 Looper, `TimerThread` 子线程 Looper.

---

## 2. 固定决策 (2026-10-20 三轮选择题)

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 范围 = Java 堆 + 原生内存 | 核心验收: 脚本结束后 `ScriptRuntime` / `ScriptEngine` / `ScriptExecuteActivity` 可被 GC 回收; 原生堆 (Bitmap / Mat / media Image) 在 N 次反复执行后不单调增长. #548 / #403 / #372 / #200 都在范围内 |
| D2 | 形态 = 宿主 Rhino 全形态 | 普通脚本, UI 脚本, 悬浮窗, 无障碍事件, 截屏, 多线程 / 定时器; inrt 打包应用共用引擎代码, 只在 P6 做一次冒烟; Node / Bun / Lua / Python / QuickJS 插件引擎在宿主进程内的桥接对象列为第二期 (附录 C Q5) |
| D3 | 门禁 = 自动化仪器测试 | androidTest 反复执行脚本 N 次 -> 强制 GC -> 断言 `ScriptRuntime` 的 WeakReference 全部被清 + Java 堆 / 原生堆增量低于阈值; 泄漏时自动 dump hprof 并用 shark 解析最短引用链写入报告. 人工 Memory Profiler 只作辅助, 不作验收 |
| D4 | 位置 = `docs/memory/ROADMAP.md` | 工具脚本 `tools/memory/`, 证据 `docs/memory/evidence/`; 结构与 `docs/augment/ROADMAP.md`, `docs/settings/ROADMAP.md` 同款 |
| D5 | LeakCanary debug 默认启用 + 自定义 watch | gradle 属性默认值改为 true (release 仍排除); `ScriptRuntime` / `ScriptEngine` / `ScriptExecution` / `JsWindow` / `TimerThread` 等在结束时交给 ObjectWatcher; 开发者选项留开关; 门禁复用 shark 解析 hprof; 代价 debug 包约 +2 MB |
| D6 | 门禁先行, 已确认根因紧跟 | P0 先落地门禁与修前基线; P1 立即修 H1 并用门禁证明; 之后按 "门禁报一个修一个" 迭代. 不在没有基线的情况下修 |
| D7 | 统一所有权 + 源码守卫 + CI | 每个 `ScriptRuntime` 一个 RuntimeResourceScope 统一登记与释放; 静态注册表必须弱引用或按 runtime 清理; JVM 单测扫描源码禁止静态强引用 ScriptRuntime / Context / Scriptable / View; 门禁最终接入 CI |
| D8 | 诊断入口 = 开发者选项页面 + 脚本 API | 设置 > 关于 > 开发者选项 新增 "内存诊断" 页面 (存活引擎数, 已结束未回收 runtime 数, Java 堆, 原生堆, 未释放图像数, 强制 GC 复查, 导出 hprof); 脚本 API 见 D11 |
| D9 | Rhino fork 允许泄漏相关最小改动 | 宿主侧能绕过就先绕; 确需改引擎时在 `D:/idea-projects/Rhino-For-AutoJs6` 单独提交并重建 `libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar`, 附录 D 记录 fork 提交 hash 与 jar 替换的构建号; 不做与泄漏无关的引擎改动 |
| D10 | CI 放最后阶段 | P0-P5 的门禁在本地真机 (XQ-AT72 为主, G8441 低内存机, 23046RP50C API 35) 与本机 AVD 跑; 门禁稳定后 P6 接 GitHub Actions (只跑 API 35 模拟器, 允许系统崩溃重跑一次) |
| D11 | 脚本 API = 独立全局模块 `memory` | 与 `images` / `threads` 同级的全局对象 (形状见 T9); 只读诊断, 不提供释放其他脚本资源的能力; 文档与 d.ts 同步一次 |
| D12 | 全部进 6.8.0 | 6.8.0 等本路线图 P0-P6 全部完成再发布, 其他等 6.8.0 的插件一并延后; 6.8.0 发布门新增 "内存门禁全绿 + LeakCanary 巡检无新报告" (P6.3) |

---

## 3. 派生技术决策

据第 2 节派生, 进入对应阶段前可推翻, 之后视同固定.

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 门禁判定: 每个场景预热 2 次不计, 再执行 N = 20 次; 每次结束后 (a) 对该次 `ScriptRuntime` 的 WeakReference 在 "GC 3 次 + `runFinalization` + 最长 10 s 轮询" 内被清, 残留数必须为 0; 整个场景结束后 (b) Java 堆 (`Runtime.totalMemory() - freeMemory()`, GC 后) 第 20 次减第 2 次 <= 2 MB; (c) 原生堆 (`Debug.getNativeHeapAllocatedSize()`) 同口径 <= 4 MB, 截屏类场景放宽为 4 MB + 一帧 (width * height * 4); (d) 线程数 (`Thread.getAllStackTraces()` 中名含 `Spawn-` / `Timer` / `ScreenCapturer` / `LooperThread` 的) 回到场景开始前; (e) Rhino Context 计数 (`AndroidContextFactory` created - released) 回到场景开始前; (f) 存活引擎数 (`ScriptEngineManager.engines.size`) 回到 0. 五项任一失败即场景失败 | P0.5 第一次真机基线之后 (阈值可按基线噪声调整, 但 (a) 的 0 残留不放宽) |
| T2 | 场景矩阵 S1-S12, 每个场景分 "正常结束" 与 "强制停止" (`ScriptExecution.engine.forceStop()` 在脚本运行 1 s 后触发) 两种模式: S1 空脚本 (噪声基线); S2 `threads.start()` x 10 各 sleep 后返回 + `threads.pool` (H1 / H2 / H15); S3 `setInterval` 主线程 + 子线程 `setTimeout` 嵌套 (H1); S4 `images.captureScreen()` x 20 + `findImage` / `matchTemplate` / `images.copy` / `images.clip` / `images.read` (H5 / H11 / H14; 需维护者在手机上授一次截屏权限, debug 构建可选择 `requestScreenCapture` 后缓存授权); S5 UI 脚本 `ui.layout` (按钮 + 文本 + `webview`) 启动后 `activity.finish()` 并等待 `onDestroy` (H10 / H13); S6 `floaty.window` + `floaty.rawWindow` 显示后关闭; S7 `auto.registerEvent` + `events.observeKey` + `events.on("exit")` + `sensors.register` (H9 / H16); S8 `new JavaAdapter(...)` / `new java.lang.Runnable({...})` x 100 + `runtime.loadDex` 一个小 dex (H3); S9 `engines.execScriptFile` 重启自身 3 层 (#548 用法, H6); S10 `http.get` + `websocket` 连接后关闭; S11 `device.*` 读取与 `device.vibrate` 等会注册接收器的调用 (H7); S12 `plugins.*` 绑定一个已安装官方插件后结束 (H8, 设备上须装一个官方插件). 门禁设备: XQ-AT72 (API 31) 为主, G8441 (API 28, 低内存与 API < 33 节点回收), 23046RP50C (API 35), 本机 AVD API 24; 每台一份报告 | P0.3 |
| T3 | 执行方式: 门禁测试类 `app/src/androidTest/java/org/autojs/autojs/memory/RuntimeLeakGateDeviceTest.kt`, 每个场景一个 `@Test`, 用 `AutoJs.instance.scriptEngineService.execute(StringScriptSource(...))` 同进程启动 (UI 脚本经 `ScriptExecuteActivity`), 在 `EngineLifecycleCallback.onEngineRemove` 取得 runtime 的 WeakReference; 度量全部取自 P2.1 的 `MemoryDiagnostics` (门禁与页面, 脚本 API 共用一份实现); 结果 JSON 经 logcat `AJ6MEM` 标记行与 `getExternalFilesDir("memory-gate")` 文件两路输出. 驱动脚本 `tools/memory/run_gate.py -s SERIAL [--scenarios S1,S2] [--mode normal / force / both] [--hprof] [--out docs/memory/evidence]` 用 `am instrument` 启动, 拉取 JSON, 生成 `gate-<model>-<versionCode>-<yyyyMMdd>.md` 与同名 `.json` | P0.2 |
| T4 | hprof 解析: 场景失败时 `Debug.dumpHprofData` 到 `memory-gate/` 目录, 设备端用 `com.squareup.leakcanary:shark-android` (与 2.14 同版, `androidTestImplementation`) 找 `ScriptRuntime` 实例的最短强引用链写入报告 (每条链只保留类名 + 字段名, 最多 20 层); `--hprof` 另把文件拉回本机 `docs/memory/evidence/hprof/` (不提交 git, 目录加 `.gitignore`) 供 Android Studio 打开 | P0.2 若设备端解析超过 60 s 则改为只拉回本机解析 |
| T5 | LeakCanary 接入: `autojs.leakcanary.enabled` 默认 true; `LeakCanarySetup` 改为直接引用 (debug source set 已隔离, 不必反射); 挂点: `EngineLifecycleCallback.onEngineRemove` -> `AppWatcher.objectWatcher.expectWeaklyReachable(engine / runtime)`, `Threads` 线程结束 -> watch `TimerThread`, `Floaty.removeWindow` -> watch `JsWindow`, `ScriptExecuteActivity` 由自带 Activity watcher 覆盖; 开发者选项开关 `key_$_leakcanary_enabled` (默认开, 仅 debug 显示, 关闭时 `LeakCanary.config.dumpHeap = false`); LeakCanary 自身的通知 / 图标行为沿用现有 `LeakCanarySetup` | P1.2 |
| T6 | RuntimeResourceScope: 新包 `org.autojs.autojs.runtime.resource`, 每个 `ScriptRuntime` 一个实例; 两种登记: `own(closeable)` (强引用, onExit 时逆序释放) 与 `track(obj, name)` (弱引用, 只用于 onExit 后复查未回收项并写诊断); 现有 `closeableManager` 并入; `ScriptRuntime.onExit()` 的手写调用逐模块改为各模块在创建资源时 `own()`, 释放顺序固定为现有顺序 (附录 B 表); 释放失败与未登记的已知资源类型 (线程 / Looper / 窗口 / 图像 / 接收器 / 绑定服务) 记 warn | P4.1 开始前 |
| T7 | 静态注册表规范: 进程级静态表必须满足二者之一: 键或值为弱引用; 或按 runtime / owner id 在 onExit 清理且有门禁场景覆盖. 已知清单 (附录 B): `LooperHelper.sLoopers`, `FloatyService.windows`, `AccessibilityService` companion 总线, `AndroidClassLoader.mDexClassLoaders`, `ImageWrapper.registry` (已弱), `TimerThread.sTimerMap` (已弱), `ScriptEngineService` 全局监听器, `ScriptEngineManager.mGlobalVariableMap` (只有 "context") | P4.2 |
| T8 | 源码守卫 `app/src/test/java/org/autojs/autojs/memory/StaticReferenceGuardTest.kt` (JVM, 读源码文件, 同 `ScriptMessagesTest` 的做法): (a) `object` / `companion object` / Java `static` 字段类型为 `ScriptRuntime` / `ScriptEngine` / `Scriptable` / Rhino `Context` / `Activity` / `View` / `Fragment` / `Dialog` 且非 `WeakReference` 的 -> 失败, 白名单写在测试内并带理由 (如 `GlobalAppContext` 的 Application); (b) 出现 `registerReceiver(` / `bindService(` / `addIdleHandler(` / `Looper.prepare(` / `WebView(` 的文件必须在附录 B 清单 (测试内的列表) 中, 新增文件未登记即失败, 逼迫新代码说明释放路径 | P4.3 |
| T9 | `memory` 模块形状: `memory.snapshot()` -> `{ javaHeapUsed, javaHeapTotal, javaHeapMax, nativeHeapAllocated, nativeHeapSize, pss, engines, retainedRuntimes, images, threads, contexts, timestamp }` (字节与计数, 全部 number); `memory.collect()` -> 强制 GC (3 次 + `runFinalization`) 后等待最多 10 s 复查, 返回新的 snapshot; `memory.retained()` -> 已结束但未回收的 runtime 描述数组 `[{ id, source, endedAt, elapsedMs, exitKind }]`; `memory.dumpHeap(path?)` -> 写 hprof 并返回路径 (debug 或开发者选项 "允许脚本导出 hprof" 开启时, 否则抛错); `memory.watch(obj, name?)` 仅 debug 且 LeakCanary 开启时有效, 否则空操作. 实现 `runtime/api/augment/memory/Memory.kt` (新文件, 不改旧模块, 与 augment 路线图 R5 不冲突, 第三批迁移时一并迁), 数据源复用 P2.1; `device` 模块保持系统级内存信息不变 | P5.1 开始前 (附录 C Q2) |
| T10 | 开发者选项页面: 关于 > 开发者选项 新增类别 "内存诊断" 与动作条目 "打开内存诊断" -> `ui/settings/memory/MemoryDiagnosticsActivity` (独立页面, `MaterialPreference` 风格列表): snapshot 各项 (每 2 s 刷新), retained 列表 (脚本名, 结束方式, 已过时长), 按钮 "强制 GC 并复查", "导出 hprof" (SAF `CreateDocument`, 文件名 `AutoJs6-heap-<yyyyMMdd-HHmmss>.hprof`), "复制诊断文本"; 同类别内开关 "LeakCanary" (debug) 与 "脚本结束后未回收时在控制台警告" (默认开); 字符串资源 10 种语言 | P2.2 |
| T11 | Rhino fork 改动范围: 仅限 Context / ContextFactory 生命周期 (未退出 Context 的线程结束回收), `ClassCache` 与生成类加载器的作用域化 (生成类加载器按顶层作用域创建, 作用域回收即可卸载); 不改语言语义, 不动解释器; 每处改动 fork 单独提交, 本文附录 D 记录 hash, jar 替换的构建号与门禁证据 | P4.4 |
| T12 | 报告与基线: 每个阶段结束跑完整矩阵 (两种模式, 全部门禁设备), 附录 A 按场景记录 "残留 runtime / Java 堆增量 / 原生堆增量 / 线程增量 / Context 增量 / 引擎数", 每个单元格写数值与报告文件名; 修前基线一列永久保留以证明收益 | 无 |

---

## 4. 红线 (所有阶段必须遵守)

- R1 不改脚本可见行为: 修复只改引用持有方式与释放时机, 不得改变 `threads` / `timers` / `floaty` / `events` / `images` 的公开语义 (例如脚本结束的判定条件, `oneShot` 含义); 确需改变的行为单独记 changelog 并同步文档.
- R2 不以 "更早释放" 换 "更早崩溃": 自动回收只作用于已结束 (`isExiting`) 的 runtime; 不允许在脚本仍可能使用资源时提前 recycle (6.6.x MonitorResource 的教训, #372 根因之一); 仍在运行的其他脚本的资源绝不能被本脚本的退出清理触及 (现有 `recycleAll(scriptRuntime)` / `removeAllEventsForThisRuntime` 的按 runtime 原则).
- R3 门禁不倒退: 每个阶段结束门禁矩阵全部通过, 或把失败记录为 "已知待修 + 编号 + 负责阶段"; 不允许新引入残留; 任何新模块的 `close()` 必须有场景覆盖或登记到 RuntimeResourceScope.
- R4 共享工作区与提交: 新建 Kotlin 文件在编译通过前不放入 `app/src`; 放回前通知并立即编译; 宿主只本地提交, 推送等维护者逐次指示; Rhino fork 改动与 jar 替换在同一次会话内完成并记录.
- R5 文案: 代码注释, 文档, 错误消息只用 ASCII 标点; 字符串资源 10 种语言; changelog 不写 "(试修)", P1.1 完成后把 6.8.0 的 "脚本反复运行后内存占用可能持续增长的问题 (试修)" 条目改写为经门禁确认的事实描述 (保留 issue #548 引用).
- R6 设备纪律: 门禁在同一设备的同一状态下运行 (充电, 亮屏, 不与其他会话的真机任务并行), 测前 `am force-stop` 冷启动宿主, 报告记录 `versionCode` 与设备; 需要人工确认的授权 (截屏, 无障碍, 悬浮窗) 由维护者在手机上操作, 脚本不代点.

---

## 5. 阶段任务

### P0 门禁与基线 (D3 / D6)

- [ ] P0.1 (文档) 本文: 现状证据, 12 项决策, 派生决策, 红线, 阶段任务, 附录模板. 证据: 本文件.
- [ ] P0.2 (测试) `RuntimeLeakGateDeviceTest` 骨架 (T3): 场景 S1 跑通 "预热 2 + 执行 20 -> GC -> WeakReference 断言 + 六项度量 (T1) -> JSON 报告"; `shark-android` 依赖与失败时的 hprof dump + 最短引用链解析 (T4); 用 S2 证明门禁有效 (修前预期失败, 报告中引用链应指向 `LooperHelper.sLoopers` -> `Loopers`, 作为 H1 的真机证据). 度量先以测试内的临时实现提供, P2.1 落地后切换到 `MemoryDiagnostics`.
- [ ] P0.3 (测试) 场景矩阵 S1-S12 (T2) 全部实现, 每个场景两种模式; UI 脚本场景经 `ScriptExecuteActivity` 并等待 `onDestroy`; 截屏场景在首次运行时等待维护者授权, 之后复用.
- [ ] P0.4 (工具) `tools/memory/run_gate.py` + `tools/memory/README.md` (T3): `am instrument` 驱动, 拉取 JSON, 生成 evidence markdown / json, `--hprof` 拉回; 复用 `tools/sdk37/physical_device.py`; 输出只保留每场景通过 / 失败与六项数值 (遵守 `device-testing.md`).
- [ ] P0.5 (基线) XQ-AT72, G8441, 23046RP50C 三台 + 本机 AVD API 24 跑完整矩阵, 结果填附录 A "修前基线" 列 (预期多个场景残留 > 0); 报告存 `docs/memory/evidence/gate-*-baseline-*.md`. 这一列永久保留.
- [ ] P0.6 (工具) `tools/memory/static_audit.py`: 扫描 `app/src/main/java`, 输出 (a) 静态字段类型为 ScriptRuntime / ScriptEngine / Scriptable / Context / Activity / View / Fragment / Dialog 的清单, (b) `registerReceiver` / `bindService` / `.subscribe(` / `WebView(` / `addIdleHandler` / `Looper.prepare` / `ThreadLocal` 的文件及同文件是否出现对应释放调用, 写入 `docs/memory/evidence/static-audit-<date>.md`; 作为附录 B 初稿与 T8 白名单来源. 证据: 报告文件 + 本文附录 B 回填.

### P1 已确认根因与 LeakCanary (D5 / D6)

- [ ] P1.1 (宿主) H1 修复: `TimerThread.run()` 以 `finally` 调 `LooperHelper.quitForThread(currentThread())`, `interrupt()` 改走 `quitForThread`; `LooperHelper.sLoopers` 在 `quit(looper)` 时按值移除, 并改为弱引用键 (T7) 兜底; `Loopers.recycle()` 对所有登记过 IdleHandler 的线程队列移除 (登记时记录队列的弱引用), 或 IdleHandler 改为只持 `WeakReference<Loopers>` 的静态内部类 (二选一, 以门禁 S2 / S3 / S9 两种模式转绿为准). changelog: 改写 6.8.0 "(试修)" 条目 (R5). 证据: S2 / S3 / S9 修前修后报告, LeakCanary 截图或文本.
- [ ] P1.2 (宿主) LeakCanary debug 默认启用 + ObjectWatcher 挂点 + 开发者选项开关 (T5); `LeakCanarySetup` 直接依赖; debug StrictMode 增加泄漏检测项 (1.4); 验证: 用 P1.1 之前的构建跑 S2, LeakCanary 报出 `Loopers` 引用链 (证据截图), P1.1 之后不报. 同时核对 inrt debug 变体的行为 (附录 C Q6).
- [ ] P1.3 (宿主) P0.5 基线中其余残留场景逐个修复, 每个修复一条子项 `P1.3.x`, 记录: 场景, shark 引用链摘要 (类名.字段 链), 改动文件, 提交 hash, 修后报告. 预计涉及 H2 (IdleHandler 异常路径), H5 (ScreenCapturer 线程与回调), H6 (全局执行监听器), H10 (UI 脚本 WebView `destroy()`), H13 (UI 脚本作用域), H16 (无障碍总线). 不在此处做结构性改动 (留给 P4), 只断链.
- [ ] P1.4 (基线) 三台真机 + AVD 复测, 附录 A 新增 "P1 后" 列; 完成条件: 全部场景两种模式 "残留 runtime" 为 0, Java 堆增量达标; 原生列允许仍有失败 (留给 P3) 但要记编号.

### P2 开发者选项 "内存诊断" 页面 (D8 / T10)

- [ ] P2.1 (宿主) 数据源 `org.autojs.autojs.runtime.memory.MemoryDiagnostics` (进程级单例): snapshot / collect / retained / dumpHeap; 已结束的 runtime 由 `EngineLifecycleCallback.onEngineRemove` 以 WeakReference 登记 (含脚本来源, 结束时间, 结束方式), 被回收后自动出列; Rhino Context 计数器由 `AndroidContextFactory` 提供; 图像计数由 `ImageWrapper` 注册表提供. 门禁测试 (P0.2) 切换到这里取度量. 证据: JVM 单测 (登记 / 出列 / 快照字段) + 门禁复跑一致.
- [ ] P2.2 (宿主) 页面与字符串 (T10, 10 种语言, 默认与 values-en 一致, 按 name 升序, ASCII 标点); 入口 关于 > 开发者选项; 真机截图 (用 `tools/augment/device_developer_options.py` 的开页方式另写 `tools/memory/device_diagnostics_page.py`). 证据: 截图 + 资源一致性测试.
- [ ] P2.3 (宿主) 未回收警告: 脚本结束 10 s 后复查 (collect 一次), 仍未回收则向全局控制台打一条 warn: "脚本 <名> 结束后 runtime 未被回收, 见 开发者选项 > 内存诊断", debug 下附 LeakCanary 是否已在分析; 开关在 T10 页面 (默认开). 证据: S2 修前构建上触发一次 (截图), 修后不触发.
- [ ] P2.4 (changelog) `feature` 条目 (zh-Hans 先行, 其他语言由维护者同步): 开发者选项 "内存诊断" 页面与未回收警告.

### P3 原生内存专项 (D1)

- [ ] P3.1 (测试) 原生度量细化: S4 拆为 S4a `captureScreen` 连续 20 帧不显式回收, S4b `images.read` + `findImage`, S4c `images.copy` / `clip` / `resize` 链式, S4d `matchTemplate` + `oneShot`, S4e issue #372 与 #200 评论中的复现脚本 (改为有限次); 度量增加 `dumpsys meminfo` 的 Graphics / Native / Unknown (由 `run_gate.py` 在场景前后采集), `Mat` 存活计数 (在 `core/opencv/Mat` 加构造 / release / finalize 计数器, 常驻但只做原子加减), media Image 未关闭计数 (`ImageWrapper` 注册表中 `mMediaImage != null` 的数量).
- [ ] P3.2 (宿主) `ImageWrapper` 生命周期审计: `recycle()` 覆盖 bitmap / mat / bgrMat / mediaImage / plane; 所有构造入口 (`ofBitmap` / `ofMat` / `ofImage` / `Mat.from` 等) 都登记到注册表; `oneShot` 与 `clone` 语义文档化; 修复后 S4 系列原生增量达到 T1 (c). 证据: 修前修后报告, 文档仓库 `images.md` 若有措辞变化同步.
- [ ] P3.3 (宿主) `ScreenCapturer`: HandlerThread / VirtualDisplay / MediaProjection / ImageReader 的释放时序与前台服务停止在 `release()` 中一次完成, `finalize()` 只作兜底并记 warn; 连续 `captureScreen` 不关闭 Image 时 ImageReader 不耗尽 (`maxImages` 与 `acquireLatestImage` 策略); 验证 issue #204 的 "卡住不返回" 在 S4a 不复现. 证据: 报告 + 线程度量.
- [ ] P3.4 (宿主) `AccessibilityNodeInfo`: G8441 (API 28) 上 `AccessibilityNodeInfoAllocator.registeredCount` 在 S7 与一个 `className("...").find()` 循环场景 (S7b) 结束后归零; 不归零的入口修复. 证据: 报告.
- [ ] P3.5 (基线) 附录 A 新增 "P3 后" 列, 原生列全部达标; issue #372 / #200 的复现脚本 (S4e) 转绿.

### P4 统一所有权与源码守卫 (D7)

- [ ] P4.1 (宿主) `RuntimeResourceScope` (T6) 落地: `closeableManager` 并入; `ScriptRuntime.onExit()` 改为 "scope 逆序释放 + 未迁移模块保留原调用", 逐模块迁移 threads / timers / loopers / floaty / images / events / sensors / media / ui / shell / websocket / compose / mail / installer / epub / gallery / terminal / speech / ai / tts; 释放顺序与现有顺序一致 (附录 B 顺序表); 未回收项复查写入 `MemoryDiagnostics`. 证据: 门禁全矩阵不倒退, JVM 单测 (登记顺序, 释放异常隔离, 重复释放幂等).
- [ ] P4.2 (宿主) 静态注册表规范化 (T7, 附录 B): `LooperHelper` (若 P1.1 未彻底), `FloatyService.windows`, `AccessibilityService` companion 总线按 owner id 清理并有计数, `AndroidClassLoader.mDexClassLoaders` 按脚本卸载或弱引用, `ScriptEngineService` 全局监听器 (`ProjectArchive` 等改为执行级监听器或成对注销), H7 / H8 清单中按脚本创建的接收器与绑定服务改经 scope 登记. 证据: 每项一条子项, 门禁场景与引用链.
- [ ] P4.3 (测试) `StaticReferenceGuardTest` (T8) 进入全量 `:app:testAppDebugUnitTest`; 白名单与理由; 与 P0.6 的 `static_audit.py` 共用解析规则 (python 生成清单, Kotlin 测试校验). 证据: 测试类 + 全量单测 0 失败.
- [ ] P4.4 (宿主 / fork) H3 / H4: Rhino 侧 Context 计数在全部场景回零 (`withRhinoContext` 兜底路径配对 exit, 线程池线程退出时 exit, 主线程 Context 的归属); 生成类加载器按顶层作用域创建 (作用域回收即可卸载), `runtime.loadDex` / `loadJar` 的加载器随 runtime 释放; 确需改 fork 时按 D9 / T11 并在附录 D 记录. 证据: S8 两种模式转绿, Context 度量回零, hprof 中 `Class` 实例数不随次数增长.
- [ ] P4.5 (审计) 非脚本路径的 UI 泄漏: LeakCanary 开启下人工走一遍主界面 (抽屉, 文件管理器, 编辑器 + 文档面板, 插件中心, 设置各页, 日志页, 任务列表), 每条 LeakCanary 报告一条子项修复 (H6 / H9 / H10 的 UI 侧); 证据: LeakCanary 文本 (复制自其界面) 与修后无报告的截图. 这一条由维护者或 Explore 子会话做, 不走门禁.

### P5 脚本 API `memory` 模块 (D11 / T9)

- [ ] P5.1 (宿主) `runtime/api/augment/memory/Memory.kt` (附录 C Q2 若改为新模块层则路径随之调整), 数据源复用 P2.1; JVM 单测 (形状, 参数校验, 非 debug 下 `dumpHeap` / `watch` 行为); 真机脚本验证: `memory.snapshot()` / `collect()` / `retained()` 在 S2 修前构建上能看到残留, 修后为空. 证据: 测试类 + 真机输出.
- [ ] P5.2 (文档) 文档仓库 `memory.md` + 生成 (`auto-generate-for-autojs6.bat`, 同步 Offline Docs 插件); d.ts 仓库 `memory.d.ts` + `aj6-int-*.d.ts` 同步 + Ace 插件 `:app:generateAutoJs6LspDeclarations`; 样例脚本 `sample/` 一个 "内存诊断" (`memory.collect()` 前后对比); 文档站 changelog 10 语言 JSON. 证据: 各仓库提交 hash.
- [ ] P5.3 (changelog) `feature` 条目: memory 模块 (zh-Hans 先行).

### P6 CI 与封版 (D10 / D12)

- [ ] P6.1 (CI) 工作流 `.github/workflows/memory-gate.yml`: API 35 模拟器跑 S1-S12 正常结束模式 (强制停止模式本地跑), 系统崩溃或 adb 丢失重跑一次 (复用 3-Stove Agent `run_main_suite.py` 的探测与重跑经验), 失败上传 hprof 与报告 artifact; 触发方式: 手动 + 发布 tag 前必跑, 不每推必跑 (估计 15-25 分钟). 证据: 一次通过的 run 链接.
- [ ] P6.2 (基线) 三台真机 + AVD API 24 + 可租用 SM-A566B (API 36, 16 KB) 各跑一次完整矩阵, 附录 A 终表; XQ-DQ72 / 22120RN86C / OPD2413 各跑一次 S2 + S5 冒烟 (不作门禁).
- [ ] P6.3 (发布门) 6.8.0 发布门新增 "内存门禁全绿 (附录 A 终表) + LeakCanary 巡检 (P4.5) 无新报告"; inrt 冒烟: 把 S2 + S5 脚本打包为 APK (debug 签名), 在 XQ-AT72 启动 10 次, PSS 与线程数不增长. 证据: 发布门文件的条目 + 冒烟报告.
- [ ] P6.4 (文档) 本文收尾: 附录全部回填, 未决项转附录 C 或新 issue; `AGENTS.md` 若需要增加 "新模块必须登记 RuntimeResourceScope" 一句.

---

## 附录 A 基线数据 (P0.5 回填, 之后每阶段追加一列)

每格: `残留 / Java 堆增量 MB / 原生堆增量 MB / 线程增量 / Context 增量 / 引擎数`, 后接报告文件名. 模式: N = 正常结束, F = 强制停止.

| 场景 | 模式 | 修前基线 (P0.5) | P1 后 | P3 后 | P4 后 | 终表 (P6.2) |
| --- | --- | --- | --- | --- | --- | --- |
| S1 空脚本 | N / F | | | | | |
| S2 threads.start + pool | N / F | | | | | |
| S3 timers 嵌套 | N / F | | | | | |
| S4a captureScreen 连续 | N / F | | | | | |
| S4b read + findImage | N / F | | | | | |
| S4c copy / clip / resize | N / F | | | | | |
| S4d matchTemplate + oneShot | N / F | | | | | |
| S4e #372 / #200 复现 | N / F | | | | | |
| S5 UI 脚本 + webview | N / F | | | | | |
| S6 floaty | N / F | | | | | |
| S7 事件 / 按键 / 传感器 | N / F | | | | | |
| S8 JavaAdapter + loadDex | N / F | | | | | |
| S9 execScriptFile 重启 | N / F | | | | | |
| S10 http + websocket | N / F | | | | | |
| S11 device 接收器 | N / F | | | | | |
| S12 plugins 绑定 | N / F | | | | | |

设备: XQ-AT72 (API 31) 主表; G8441 (API 28), 23046RP50C (API 35), AVD API 24 各一份同结构表 (放在 evidence 报告中, 本表只记 XQ-AT72 与差异备注).

---

## 附录 B 静态注册表与配对清单 (P0.6 生成初稿, P4.2 回填处置)

### B.1 进程级静态注册表

| 注册表 | 键 / 值 | 现状 | 规范 (T7) 处置 | 阶段 |
| --- | --- | --- | --- | --- |
| `core/looper/LooperHelper.sLoopers` | Thread -> Looper (强) | 子线程不移除 (H1) | 线程退出 finally 移除 + 弱引用键 | P1.1 |
| `core/looper/TimerThread.sTimerMap` | Thread -> WeakReference<Timer> (WeakHashMap) | 已弱 | 保持 | - |
| `core/image/ImageWrapper.registry` | ScriptRuntime -> List<WeakReference<ImageWrapper>> (WeakHashMap) | 已弱, onExit `recycleAll(runtime)` | 保持; 核对构造入口登记 | P3.2 |
| `ui/enhancedfloaty/FloatyService.windows` | CopyOnWriteArraySet<FloatyWindow> (强) | `Floaty.closeAll()` 移除 | 门禁 S6 覆盖; 异常路径核对 | P1.3 / P4.2 |
| `core/accessibility/AccessibilityService` companion 总线 | 回调 / 委托 / 观察者 / 按键监听 (强) | 回调按 owner id 清理; 其余依赖各模块 recycle | 全部按 owner id, 计数可读 | P4.2 |
| `rhino/AndroidClassLoader.mDexClassLoaders` | path -> DexClassLoader (强, 进程级) | 两处 remove | 按 runtime 作用域化 | P4.4 |
| `engine/ScriptEngineService` 全局执行监听器 | List (强) | 10 注册 / 4 注销 | 成对或执行级 | P4.2 |
| `engine/ScriptEngineManager.mGlobalVariableMap` | name -> Object | 只有 "context" (Application) | 保持 | - |
| `app/GlobalAppContext` | Application + 主线程 Handler | 进程级 | 白名单 | - |

### B.2 配对清单 (登记 -> 释放)

由 `static_audit.py` 生成, 每行: 文件, 登记调用, 同文件释放调用, 归属 (进程级 / 按脚本 / 按页面), 处置. 初稿见 1.3 的 H7 / H8 / H9 / H10 列表.

### B.3 `ScriptRuntime.onExit()` 现有释放顺序 (P4.1 迁移时保持)

images.cancelPendingQuantizations -> console.onRuntimeExit -> CoreWebSocket.onExit -> automator.removeAllEventsForThisRuntime -> accessibilityBridge.releasePulse / setAccessibilityListener(null) -> RootUtils.resetRuntimeOverriddenRootModeState -> compose.close -> ImageWrapper.recycleAll(this) -> ThreadCompat.interrupted -> floaty.closeAll -> events.emit("exit") -> mail / installer / epub / gallery / terminal / speech / ai / tts .close -> threads.shutDownAll -> events.recycle -> media.recycle -> loopers.recycle -> recycleShell -> images.releaseScreenCapturer / stopScreenCapturerForegroundService -> sensors.unregisterAll -> timers.recycle -> ui.recycle -> closeableManager.recycleAll.

---

## 附录 C 待决事项

| 编号 | 问题 | 需要拍板的阶段 | 默认倾向 |
| --- | --- | --- | --- |
| Q1 | G8441 (API 28, 低内存) 上 T1 的 Java 堆 / 原生堆阈值是否放宽 | P0.5 之后 | 不放宽 (a) 的 0 残留, 其余按噪声 +50% |
| Q2 | `memory` 模块写旧 augment 包 (`augment/memory/Memory.kt`) 还是直接写新脚本模块层 (`runtime/api/modules`, augment 路线图 P1.2 的 KSP 层, 当前隐藏开关默认关) | P5.1 之前 | 旧包新文件 (开关关时也可用); 若届时新层已默认开则写新层 |
| Q3 | 门禁场景 S4 的截屏授权: debug 构建是否加一个 "测试用授权缓存" 开发者选项, 避免每次人工点 | P0.3 | 加 (仅 debug, 默认关) |
| Q4 | issue #474 (Shizuku 僵尸进程 OOM) 是否纳入本路线图 | P6 | 不纳入, 单独立项 |
| Q5 | 第二期: Node / Bun / Lua / Python / QuickJS 插件引擎在宿主进程内的桥接对象 (`engine/NodeBridge*` 约 60 个文件) 的门禁场景 | P6.4 | 另开路线图, 复用本文门禁框架 |
| Q6 | inrt debug 变体是否也默认包含 LeakCanary | P1.2 | 包含 (同一 gradle 属性) |
| Q7 | CI 门禁是否接入 release 工作流作为硬门禁 (失败阻断发布) | P6.1 | 接入 tag 构建, 失败阻断 |
| Q8 | `memory.dumpHeap` 在 release 非 debug 下是否允许 (隐私: hprof 含脚本变量) | P5.1 | 只在开发者选项 "允许脚本导出 hprof" 开启时允许 |

---

## 附录 D Rhino fork 改动记录 (D9 / T11)

| 日期 | fork 提交 | 改动 | jar 替换的宿主构建号 | 门禁证据 |
| --- | --- | --- | --- | --- |
| | | | | |

fork 位置 `D:/idea-projects/Rhino-For-AutoJs6` (基线 `90693268c`, 2026-10-20), 产物 `libs/org-mozilla-rhino-2_0_0-SNAPSHOT.jar`. 宿主侧自有的 `app/src/main/java/org/mozilla/javascript/` (`ContextWrapper`, `LazyDataSlot`, `VMBridge_custom`) 不算 fork 改动.

---

## 附录 E 证据等级

| 等级 | 含义 |
| --- | --- |
| E0 | 代码已提交, 编译通过 |
| E1 | E0 + JVM 单测通过 |
| E2 | E1 + 门禁在 AVD 通过 (记录 API level 与报告文件) |
| E3 | E2 + 门禁在真机通过 (记录机型, API, versionCode 与报告文件) |
| E4 | E3 + LeakCanary 人工巡检无报告 (截图或文本) |

第 5 节条目的默认要求: 宿主代码改动 E3; 工具与文档 E0; P4.5 为 E4.

---

## 附录 F 文件地图 (计划, 阶段完成后回填实际路径)

| 路径 | 内容 | 阶段 |
| --- | --- | --- |
| `docs/memory/ROADMAP.md` | 本文 | P0.1 |
| `docs/memory/evidence/gate-<model>-<versionCode>-<date>.md` / `.json` | 门禁报告 | P0.5 起 |
| `docs/memory/evidence/static-audit-<date>.md` | 静态审计报告 | P0.6 |
| `docs/memory/evidence/hprof/` (gitignore) | 拉回的 hprof | P0.2 |
| `tools/memory/run_gate.py`, `README.md` | 门禁驱动 | P0.4 |
| `tools/memory/static_audit.py` | 源码静态审计 | P0.6 |
| `tools/memory/device_diagnostics_page.py` | 诊断页面截图 | P2.2 |
| `app/src/androidTest/java/org/autojs/autojs/memory/RuntimeLeakGateDeviceTest.kt` | 门禁测试 | P0.2 |
| `app/src/androidTest/java/org/autojs/autojs/memory/scenarios/*.js` (或 Kotlin 字符串) | 场景脚本 | P0.3 |
| `app/src/main/java/org/autojs/autojs/runtime/memory/MemoryDiagnostics.kt` | 诊断数据源 | P2.1 |
| `app/src/main/java/org/autojs/autojs/ui/settings/memory/MemoryDiagnosticsActivity.kt` | 诊断页面 | P2.2 |
| `app/src/main/java/org/autojs/autojs/runtime/resource/RuntimeResourceScope.kt` | 统一所有权 | P4.1 |
| `app/src/test/java/org/autojs/autojs/memory/StaticReferenceGuardTest.kt` | 源码守卫 | P4.3 |
| `app/src/main/java/org/autojs/autojs/runtime/api/augment/memory/Memory.kt` | 脚本 API | P5.1 |
| `.github/workflows/memory-gate.yml` | CI | P6.1 |
