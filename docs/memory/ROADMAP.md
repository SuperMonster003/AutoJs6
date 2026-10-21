# 内存泄漏排查与根治路线图

更新日期: 2026-10-21

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
- 真机证据 (P0.2, 2026-10-20, XQ-AT72 API 31, versionCode 5620): 门禁 S2 正常结束模式 20 次全部残留. shark 给出的最短链经表的键 (已结束的线程对象): `LooperHelper.sLoopers (static) -> ConcurrentHashMap 节点 -> TimerThread.target -> RhinoUtils 回调 -> ScriptRuntime`; 忽略 `Thread.target` 的第二遍正是上面预测的链: `LooperHelper.sLoopers (static) -> ConcurrentHashMap 节点 -> Looper.mQueue -> MessageQueue.mIdleHandlers -> Loopers.scriptRuntime -> ScriptRuntime`. 报告见附录 A 的 S2 行.

### 1.3 可疑点清单 (待门禁验证)

以下各项由代码阅读得出, 没有真机证据, 按 "门禁报出 -> shark 引用链 -> 修复" 的流程逐个确认或排除; 编号在第 5 节与附录 A 中引用. P0.5 修前基线 (附录 A) 的结论: 证实 H2 (S2 第二遍链经 `MessageQueue.mIdleHandlers`), H8 (S12), H15 (S2 F); 本轮未复现 H6 (S9), H7 (S11), H9 / H16 (S7, 宿主无障碍服务运行中), H5 (S4a), H10 / H13 (S5 只有 H17 / H18 的单 runtime 残留), H3 (S8 无额外 runtime 残留, 生成类数量留给 P4.4); 新增 H17-H21 (门禁发现).

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
| H17 (门禁发现) | `core/accessibility/SimpleActionAutomator.kt` init + `AccessibilityService.defaultBridge` | 每个 runtime 创建 `SimpleActionAutomator` 时执行 `accessibilityBridge.tap = Tap { ... }`, lambda 捕获自身 (持 `scriptRuntime`), 而无障碍服务未运行时 bridge 是进程级静态 `defaultBridge` | `AccessibilityService.defaultBridge (static) -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator 回调 -> SimpleActionAutomator.scriptRuntime -> ScriptRuntime`, 始终钉住最近启动的一个 runtime (约 1.5 MB); P0.5 四台全部场景都出现 | 全部场景 (N / F) |
| H18 (门禁发现) | `runtime/api/augment/util/VersionCodesList.kt` (`VersionCodesInfo.obj = VersionCodesInfoGenerated.obj`) | 进程级 `NativeObject` 在第一个用到它的脚本中创建, 父作用域是该脚本的 TopLevelScope | `VersionCodesInfo.obj (static) -> NativeObject.parentScopeObject -> TopLevelScope.associatedValues -> ComposeJsErrors$Localizer -> Compose -> AndroidComposeScriptPlatform.runtime -> ScriptRuntime`, 钉住进程内第一个 runtime | 全部场景 (落在预热) |
| H19 (门禁发现) | `core/web/WebSocket.kt` + okhttp `RealWebSocket` | 强制停止时 `CoreWebSocket.onExit` 未让读线程结束 (或未关闭连接), 读线程栈上的 `RealWebSocket` 仍持监听器 | `Thread<local> -> RealWebSocket.listener -> WebSocket$WebSocketMessageListener.this$0 -> WebSocket.mBridges -> ScriptBridges.engine -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime`; S10 F 在四台残留 16-20 / 20, 正常关闭 (N) 时不出现 | S10 F |
| H20 (门禁发现, 崩溃) | `ScriptRuntime.onExit()` 释放顺序 (附录 B.3) | `ImageWrapper.recycleAll(this)` 在 `threads.shutDownAll()` 之前执行, 强制停止时子线程仍在用图像 | 不是泄漏而是 R2 所说的 "更早释放换更早崩溃": S4e F (4 个 `threads.start` 线程循环 `images.copy` + `findImage`) 在 XQ-AT72 上 5 次有 4 次 `SIGABRT: 'Error, cannot access an invalid/free'd bitmap here!'`, 栈为 `Spawn-N -> Images.copy -> ImageWrapper.clone -> Bitmap.copy` (`docs/memory/evidence/crash-XQ-AT72-5620-S4e-force-20261020.txt`), issue #200 的 "停止 / 启动后崩溃" 可能与此有关 | S4e F |
| H21 (门禁发现, P1 证实) | 线程栈局部变量 + 未回收的 Message | S4b / S4c / S4d 的 F 模式在 23046RP50C 与 AVD 上偶有第 2 个残留, 根为某个 `TermSession$2` 线程的栈局部变量 | `TermSession$2<local> -> Message.callback -> LoopBasedJavaScriptEngine 回调 -> mRuntime -> ScriptRuntime`; P1 复核 (23046RP50C, 修完 H1 / H17 / H18 后) 不是暂态: S4a / S4d / S4e F 各残留 1 个, 链还有 `Message.callback -> RhinoUtils lambda (TimerThread.target) -> ScriptRuntime`. 机理: 脚本线程投递的 Runnable 在强制停止时抛出, `Looper.loop` 不回收已分发的 Message (callback 仍在); Message 来自共享池, 另一个 Looper 线程 (`TermSession$2`) 栈帧里仍留着对同一 Message 对象的陈旧引用. 修复见 P1.3.7 | S4a-S4e F |
| H22 (P1 门禁发现) | Application 的 `ContextImpl.mServiceCache` 中的 `AudioManager` | 进程内第一次取得 AudioManager 的线程决定其事件 Handler 的 Looper; `device.getMusicVolume` 等在脚本线程上首次取得 | `Toaster.sApplication -> App.mBase -> ContextImpl.mServiceCache -> AudioManager.mServiceEventHandlerDelegate -> Handler.mLooper -> Looper.mThread -> ThreadCompat.target -> LoopedBasedJavaScriptExecution.mScriptEngine -> LoopBasedJavaScriptEngine.mRuntime -> ScriptRuntime`, 钉住进程内第一个读音量的脚本; 修完 H17 / H18 后在 AVD API 24 的 S11 预热中出现 (其他设备上 AudioManager 多半已由别处在主线程取得). 修复见 P1.3.8 | S11 |
| H23 (P1.4 门禁发现) | `core/plugin/speech/SpeechSessionProxy.kt` 的 `SpeechEventGate` | 语音插件进程持有宿主的回调 Binder (`ISpeechSessionCallback.Stub`), 其生命由远端决定; 闸门在通道结束后仍持有 sink | `JNI_GLOBAL: SpeechSessionCallbackBinder.gate -> SpeechEventGate.sink -> SpeechSessionRunner.controller -> SpeechSession$Controller.session -> SpeechSession.scriptRuntime -> ScriptRuntime`; XQ-AT72 最终矩阵第一次跑 S4e N 时 1/20 (S4e 脚本不用语音, 推测是维护者当时操作手机, 3-Macaw 按键触发把一次语音会话开在了运行中的门禁脚本里), 同一构建复跑 0/20. 修复见 P1.3.10 | 任意场景 (外部触发的语音会话) |

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

- [x] P0.1 (文档) 本文: 现状证据, 12 项决策, 派生决策, 红线, 阶段任务, 附录模板. 证据: 本文件 (`fc5d4ef3b`).
- [x] P0.2 (测试) `RuntimeLeakGateDeviceTest` 骨架 (T3): 场景 S1 跑通 "预热 2 + 执行 20 -> GC -> WeakReference 断言 + 六项度量 (T1) -> JSON 报告"; `shark-android` 依赖与失败时的 hprof dump + 最短引用链解析 (T4); 用 S2 证明门禁有效 (修前预期失败, 报告中引用链应指向 `LooperHelper.sLoopers` -> `Loopers`, 作为 H1 的真机证据). 度量先以测试内的临时实现提供, P2.1 落地后切换到 `MemoryDiagnostics`.
  - 证据 (E3): `b6f4763a6`, `app/src/androidTest/java/org/autojs/autojs/memory/` (`RuntimeLeakGateDeviceTest` 每个场景一个 `@Test`, `GateMetrics` 六项度量的临时实现, `GateHeapPathAnalyzer` shark 分析, `GateLoopbackServer` S10 回环服务, `scenarios/GateScenarios.kt` 场景脚本); `androidTestImplementation(libs.leakcanary.shark.android)` (2.14). XQ-AT72 API 31, versionCode 5620: S1 正常结束 20 次残留 1 (Java 堆 +0.04 MB, 原生 +0.03 MB, 线程 / Context / 引擎 0; 这 1 个不是噪声, 是静态 `defaultBridge` 钉住的最近一个 runtime, 见 1.3 H17, 因此 S1 在修 H17 / H18 之前不会通过), S2 正常结束 20/20 残留, 引用链见 1.2 H1 的真机证据; 报告 `docs/memory/evidence/gate-XQ-AT72-5620-baseline-20261020.md`.
  - 实现要点: runtime 的弱引用在 `EngineLifecycleCallback.onEngineRemove` 取得 (经 `ScriptEngineService.registerEngineLifecycleCallback`, 本次运行创建的每个引擎都跟踪, S9 的子引擎也算); 已退出的 runtime 在 hprof 中以 `isExiting == true` 识别, 不需要对象身份映射; 最短链经 `Thread.target` 时再做一遍忽略该字段的分析, 给出第二条链. 设备端转储约 2 s, shark 分析约 8-15 s (XQ-AT72), 远低于 T4 的 60 s 推翻点, 因此保持设备端解析. Rhino Context 计数用反射读取 `AndroidContextFactory.mContextCount`, 未改宿主源码 (没有新增只读计数器). 每个场景与模式各用一个进程 (`run_gate.py`), 某个场景的泄漏不影响下一个场景的数值.
  - 口径补充: 每次运行后最长轮询 10 s, 连续 3 次未回收后降为 1 s (控制时长), 场景结束时再统一回收一次判定 "残留"; Java / 原生堆增量为 "预热第 2 次结束" 到 "第 20 次结束" (均在强制回收后); 线程只数名含 `ScriptThread-` / `Spawn-` / `Pool-` / `Timer` / `ScreenCapturer` / `LooperThread` 的线程, 全部线程数另记不作判定. 脚本以 `exit()` 或被中断结束不算脚本错误; 强制停止触发后抛出的异常另记为 `stopErrors` (例: S3 F 偶发 `TimerCallbackRejectedException: ... its looper is exiting`), 不使场景变为 error.
- [x] P0.3 (测试) 场景矩阵 S1-S12 (T2) 全部实现, 每个场景两种模式; UI 脚本场景经 `ScriptExecuteActivity` 并等待 `onDestroy`; 截屏场景在首次运行时等待维护者授权, 之后复用.
  - 进度 (2026-10-20, `b6f4763a6`): 16 个场景 (S4 按附录 A 拆为 S4a-S4e) 全部实现于 `scenarios/GateScenarios.kt`, 每个场景一个 `@Test`, 两种模式; S5 经 `ScriptExecuteActivity` (`ExecutionConfig.intentFlags = NEW_TASK`), 以引擎在 `onDestroy` 中移除为结束; S9 跟踪 4 个引擎 (根 + 3 层 `engines.execScriptFile`); S10 用测试进程内的回环 HTTP / WebSocket 服务; S12 用 3-ImGui (唯一保留 `plugins.load` 注册表的官方插件), 未安装 / 未启用 / 未授权时跳过; S7 按宿主无障碍服务是否运行决定是否加入 `auto.registerEvent` / `events.observeKey`. 除 S4a 外的 15 个场景已在四台设备上跑完 (P0.5).
  - S4a (2026-10-20 补齐): 现有代码每次运行都会重新申请 MediaProjection, "首次授权后复用" 不成立; 按附录 C Q3 的拍板 (方案 (a)), `run_gate.py --capture on` 在每个 S4a 进程前把 `PROJECT_MEDIA` 设为 `allow`, 进程结束 (含失败 / 超时 / 异常路径) 后恢复为 `default`. 四台 (XQ-AT72, G8441, 23046RP50C, AVD API 24) 两种模式都已跑完, 均未弹授权框, 结果见附录 A. 证据 (E3): 测试代码 `b6f4763a6`, 驱动 `d4e5b94e6`, 报告同 P0.5.
- [x] P0.4 (工具) `tools/memory/run_gate.py` + `tools/memory/README.md` (T3): `am instrument` 驱动, 拉取 JSON, 生成 evidence markdown / json, `--hprof` 拉回; 复用 `tools/sdk37/physical_device.py`; 输出只保留每场景通过 / 失败与六项数值 (遵守 `device-testing.md`).
  - 证据 (E0): `8e7de1f71`, `b12bd2609` (设备占用防护, `--merge`, 崩溃日志保存); `tools/memory/run_gate.py` (每个场景与模式一个 `am instrument` 进程, 结果依次从 instrumentation status `aj6mem_json`, 设备上的 JSON 文件, logcat `AJ6MEM` 分段行读取; 报告名 `gate-<model>-<versionCode>[-<label>]-<yyyyMMdd>.md` / `.json`; `--hprof` 拉回 `docs/memory/evidence/hprof/` (目录 `.gitignore` 忽略全部内容); `--label`, `--capture`, `--wake`, `--no-analyze`), `tools/memory/test_run_gate.py` (解析, 合并与渲染, 6 项通过), `tools/memory/README.md`. `.gitignore` 的 `/tools/*` 增加 `tools/memory` 放行. 脚本不安装 APK, 不点屏幕, 不改设置.
- [x] P0.5 (基线) XQ-AT72, G8441, 23046RP50C 三台 + 本机 AVD API 24 跑完整矩阵, 结果填附录 A "修前基线" 列 (预期多个场景残留 > 0); 报告存 `docs/memory/evidence/gate-*-baseline-*.md`. 这一列永久保留.
  - 进度 (2026-10-20, 宿主 debug versionCode 5620, 证据 `7b06b5f45`): 四台都跑完 S1-S12 (S4a 除外) 两种模式, 报告 `gate-XQ-AT72-5620-baseline-20261020`, `gate-G8441-5620-baseline-20261020`, `gate-23046RP50C-5620-baseline-20261020`, `gate-Android_SDK_built_for_x86-5620-baseline-20261020` (`.md` / `.json`), 附录 A "修前基线" 列已按 XQ-AT72 填写并附四台差异. 结果: 30 个已运行的场景模式全部失败 (预期内), 线性泄漏 S2 / S3 / S4e / S12 / S10 F, 其余场景各残留 1 个 (H17 / H18); 新发现 H17-H21 写入 1.3. AVD 补装了官方 OpenCV 插件 v1.1.4 x86 (`images.findImage` 依赖它) 后重跑.
  - 补跑 (2026-10-20 18:06-18:36, 同一构建 5620, `--merge`): (1) S4a 四台两种模式已补 (XQ-AT72 / G8441 / 23046RP50C 的宿主先从 23046RP50C 取回 5620 的 APK 装回, 因为工作区产物已被其他会话的构建覆盖); (2) 23046RP50C 的 S12 F 已补 (残留 20/20, 与其他真机一致).
  - S7 无障碍部分 (19:01-19:40, 附录 C Q9): XQ-AT72 装回 5620 后由维护者开启宿主无障碍服务. instrumentation 在启动和结束时都会杀掉宿主进程, 系统随即把该服务列入 "Crashed services" 且不再绑定, 所以测试进程里拿不到服务 (设置中仍显示开启); 改为 S7 两种模式在同一进程里跑 (`run_gate.py --keep-process --one-process --a11y-wait 600`), 测试等待期间在手机上弹 Toast, 由维护者把服务关掉再开启一次, 服务即绑定到正在运行的测试进程 (测试本身不改设置). 结果 `accessibilityRunning: true`, `auto.registerEvent` / `events.observeKey` / `events.onKeyDown` 均已注册, 两种模式都只有 H17 / H18 的单 runtime 残留, 见附录 A. 测试代码的等待与提示逻辑见 `RuntimeLeakGateDeviceTest.accessibilityRunning` (参数 `memoryGateAccessibilityWaitMs`, 默认 0 即不等待).
- [x] P0.6 (工具) `tools/memory/static_audit.py`: 扫描 `app/src/main/java`, 输出 (a) 静态字段类型为 ScriptRuntime / ScriptEngine / Scriptable / Context / Activity / View / Fragment / Dialog 的清单, (b) `registerReceiver` / `bindService` / `.subscribe(` / `WebView(` / `addIdleHandler` / `Looper.prepare` / `ThreadLocal` 的文件及同文件是否出现对应释放调用, 写入 `docs/memory/evidence/static-audit-<date>.md`; 作为附录 B 初稿与 T8 白名单来源. 证据: 报告文件 + 本文附录 B 回填.
  - 证据 (E0): `8e7de1f71` (`tools/memory/static_audit.py`), `7b06b5f45` (报告), 报告 `docs/memory/evidence/static-audit-20261020.md` (2642 个文件, HEAD `94c0b0d1e`). 结果: (a) 类型命中八类的进程级字段 12 个强引用 / 5 个弱引用, 其中真正持有脚本对象的只有 `RhinoUtils` 的三个原型 (进程级标准作用域, 白名单候选), 其余为按 `Class` 或函数类型的表; (c) 进程级注册表 42 个; (b) 未在同文件释放: `registerReceiver` 12 / 21 个文件, `bindService` 3 / 9, `.subscribe(` 42 / 55, WebView 创建 8 / 9, `ThreadLocal` 4 / 6, `addIdleHandler` 与 `Looper.prepare` 均成对 (但 H1 / H2 说明 "同文件有释放" 不等于 "每条路径都释放"). 摘要回填附录 B.1 / B.2. 门禁比静态审计多找到两处单 runtime 钉住 (H17 / H18, 见 1.3), 都不在八类类型清单内 (经 lambda 与 `NativeObject` 间接持有), 说明 T8 的源码守卫只能作补充.

### P1 已确认根因与 LeakCanary (D5 / D6)

- [x] P1.1 (宿主) H1 修复: `TimerThread.run()` 以 `finally` 调 `LooperHelper.quitForThread(currentThread())`, `interrupt()` 改走 `quitForThread`; `LooperHelper.sLoopers` 在 `quit(looper)` 时按值移除, 并改为弱引用键 (T7) 兜底; `Loopers.recycle()` 对所有登记过 IdleHandler 的线程队列移除 (登记时记录队列的弱引用), 或 IdleHandler 改为只持 `WeakReference<Loopers>` 的静态内部类 (二选一, 以门禁 S2 / S3 / S9 两种模式转绿为准). changelog: 改写 6.8.0 "(试修)" 条目 (R5). 证据: S2 / S3 / S9 修前修后报告, LeakCanary 截图或文本.
  - 证据 (E3): `a2ee7eb03` (`core/looper/LooperHelper.java`, `Loopers.kt`, `TimerThread.kt`): `TimerThread.run` 的 finally 调 `quitForThread(this)`, `interrupt()` 先 `quitForThread(this)`; `sLoopers` 改为键与值都弱引用的 `WeakHashMap<Thread, WeakReference<Looper>>` (Looper 引用自己的线程, 只有弱键永远清不掉), `quit(looper)` 同时移除该 Looper 的条目; IdleHandler 选第二种做法: `Loopers` 不再实现 `IdleHandler`, 改为登记只持 `WeakReference<Loopers>` 的静态 `WeakIdleHandler` (H2). H15 (强制停止后池线程) 由 P1.3.1 的 `3d47b6c02` 处理. 修前 (附录 A 修前基线, XQ-AT72): S2 N `20 / +30.18`, S2 F `20 / +30.21` (线程 / Context +44), S3 N `20 / +29.23`, S3 F `20 / +29.32`, S9 两种模式各 1 (H17 / H18). 修后 (`8dcba0d67` 的构建, 2026-10-20 22:18 起, 报告 `gate-XQ-AT72-5621-p1-20261020.md`, `gate-23046RP50C-5621-p1-20261020.md`, `gate-Android_SDK_built_for_x86-5621-p1-20261020.md`): 三台 S2 / S3 / S9 两种模式全部 PASS, 残留 0, Java 堆 +0.05 至 +0.30 MB, 线程 / Context 增量 0 (XQ-AT72: S2 N `0 / +0.17 / +0.07`, S2 F `0 / +0.07 / +0.04`, S3 N `0 / +0.15`, S3 F `0 / +0.07`, S9 N `0 / +0.14`, S9 F `0 / +0.10`). LeakCanary 文本见 P1.2. 最终构建的全矩阵见 P1.4.
- [x] P1.2 (宿主) LeakCanary debug 默认启用 + ObjectWatcher 挂点 + 开发者选项开关 (T5); `LeakCanarySetup` 直接依赖; debug StrictMode 增加泄漏检测项 (1.4); 验证: 用 P1.1 之前的构建跑 S2, LeakCanary 报出 `Loopers` 引用链 (证据截图), P1.1 之后不报. 同时核对 inrt debug 变体的行为 (附录 C Q6).
  - 证据 (E3): `8e52a7bcb` (宿主), `a199a97f7` (`tools/memory/leakcanary_probe.py`, README). 改动: `gradle.properties` 与 `app/build.gradle.kts` 的 `autojs.leakcanary.enabled` 默认 true, 为 false 时改为 `debugCompileOnly` (debug 的 `LeakCanarySetup` 仍可编译, 运行时空操作); `app/src/debug/.../leakcanary/LeakCanarySetup.kt` 直接调用 `LeakCanary.config` / `AppWatcher.objectWatcher`, 提供 `apply(enabled)` / `watch(obj, description)` / `watchEngine(engine)`, release 版同名空实现; 挂点: `ScriptEngineService` 的 `onEngineRemove` -> `watchEngine` (引擎 + `ScriptRuntime`), `TimerThread.run` 的 finally 与 `Floaty.removeWindow` 只在所属 runtime `isExiting` 时 watch (脚本运行中结束的线程 / 关闭的窗口仍可能被脚本变量引用, 一律 watch 会误报, 这是对 T5 的收窄, 推翻点 P1.2 内决定); 开发者选项 "调试" 分类下的 "LeakCanary" 开关 (`key_$_leakcanary_enabled`, 默认开, 仅 debug 显示, 关闭时不再 watch 且 `dumpHeap = false`; P2.2 建 "内存诊断" 分类时移过去), 字符串 `summary_leakcanary` 10 种语言, `text_leakcanary` 不翻译; 键的设置层登记 (`classification.json` / `DeveloperKeys`) 由设置层 P2 会话代办. StrictMode (`detectLeakedClosableObjects` / `detectActivityLeaks` / `detectLeakedRegistrationObjects` + `penaltyLog`) 放在 debug 的 `LeakCanarySetup.setup` 中, 未改 `App.kt` (设置层会话正在改).
  - 验证 (XQ-AT72 API 31, `leakcanary_probe.py --scenario S2 --runs 6`, 以普通脚本经 `RunIntentActivity` 运行, 因为 LeakCanary 在 instrumentation 进程里不转储堆): 修 H1 之前的构建 (工作区回退 `a2ee7eb03`, 其余同 `8e52a7bcb`) 报出 1 个应用泄漏, 链 `LooperHelper.sLoopers -> ConcurrentHashMap[key()] -> Threads$Companion$TimerThread.target -> RhinoUtils lambda -> ScriptRuntime` (LeakCanary 界面文本 `docs/memory/evidence/leakcanary-XQ-AT72-5621-preH1-S2-20261020.txt`); 全部 P1 修复后 (`a1e3ab9d9` 的构建) 6 次运行共 watch 41 个对象 (引擎, runtime, TimerThread, Activity), 最大残留 0, 300 s 内无分析 (`leakcanary-XQ-AT72-5621-p1-S2-20261020.txt`). 文本代替截图.
  - Q6 核对: `:app:dependencies` 显示 `inrtDebugRuntimeClasspath` 含 `leakcanary-android:2.14`, `inrtReleaseRuntimeClasspath` 不含; debug APK 因此多一个 LeakCanary "Leaks" 启动器图标 (开关关闭时隐藏).
  - 注意: 宿主有全部文件访问权限时 LeakCanary 把 hprof 写到 `/sdcard/Download/leakcanary-org.autojs.autojs6/`; 分析经 WorkManager 进行, 重装后新进程会补完旧进程的分析 (`leakcanary_probe.py` 只认本次开始之后的转储).
  - 补充 (2026-10-21, 维护者要求, `4be14ab38`): LeakCanary 自身的 "Dump heap automatically" (Leaks 应用 > About) 默认开启, 调试时少量残留就会自动转储让 AutoJs6 短暂冻结. `LeakCanarySetup` 首次运行时若该开关从未被设置过, 就把它设为关闭 (LeakCanary 2.14 的 `LeakCanaryHeapDumpPrefs` / `AboutScreenDumpEnabled`), 之后在该页面的选择保持不变. 关闭时 LeakCanary 走 `NotifyingNope`: 仍 watch 并以通知显示残留对象数, "Dump Heap Now" 可按需转储; 门禁自己用 `Debug.dumpHprofData`, 不受影响. `leakcanary_probe.py --expect-leaks yes` 在自动转储关闭且没有分析时以退出码 2 报 INCONCLUSIVE (上面的修前证据是在默认值改变之前取得的).
- [x] P1.3 (宿主) P0.5 基线中其余残留场景逐个修复, 每个修复一条子项 `P1.3.x`, 记录: 场景, shark 引用链摘要 (类名.字段 链), 改动文件, 提交 hash, 修后报告. 预计涉及 H2 (IdleHandler 异常路径), H5 (ScreenCapturer 线程与回调), H6 (全局执行监听器), H10 (UI 脚本 WebView `destroy()`), H13 (UI 脚本作用域), H16 (无障碍总线). P0.5 后确定要做的: H17 (`defaultBridge.tap`), H18 (`VersionCodesInfo.obj`), H8 (`plugins.load` 的绑定在 onExit 解绑), H15 (强制停止时关闭 `threads.pool`), H19 (强制停止时关闭 WebSocket 并结束读线程), H20 (onExit 先停子线程再回收图像, 或回收前等待子线程结束; 属于崩溃修复, 优先级最高), H21 (先复核). 不在此处做结构性改动 (留给 P4), 只断链.
  - 修后数值一律见附录 A "P1 后" 列 (P1.4 的全矩阵报告 `gate-*-5621-p1-20261020.md`); 下面只记每项的链, 改动与专项证据. H2 随 P1.1 处理; H5 / H6 / H10 / H13 / H16 在修完下列各项后的全矩阵中没有残留, 不单独改.
  - [x] P1.3.1 H20 (崩溃) + H15: S4e F / S2 F. `ScriptRuntime.onExit` 改为先 `threads.shutDownAllAndAwait` (中断 `threads.start` 线程, `shutdownNow` 脚本未关闭的线程池, 在脚本线程上最多等 2 s 让这些线程与脚本已关闭的线程池的工作线程结束; UI 线程不等), 再 `ImageWrapper.recycleAll`; 仍有线程存活时由辅助线程在它们结束后回收 (最多 60 s, 超时放弃回收并记 warn, R2). 线程池随之在强制停止时关闭, 池线程退出并 `Context.exit` (H15). 改动 `runtime/ScriptRuntime.kt`, `runtime/api/Threads.kt`; 提交 `3d47b6c02`; 新顺序见附录 B.3. 证据 (E3): XQ-AT72 API 31 上 S4e F 连续 5 个进程共 110 次强制停止, 0 次崩溃 (修前 5 次 4 崩), 每次残留 0 (`docs/memory/evidence/h20-XQ-AT72-5621-S4e-force-20261020.md`).
  - [x] P1.3.2 H17: 全部场景. 链 `AccessibilityService.defaultBridge (static) -> AccessibilityBridgeImpl.tap -> SimpleActionAutomator 回调 -> scriptRuntime`. `onExit` 在 `loopers.recycle` (servant Looper 已退出) 之后置 `accessibilityBridge.tap = null` (每个 runtime 一个 bridge, 只动自己的, R2); 之后经该 bridge 找到的节点 `clickBounds` 走独立 automator. 改动 `runtime/ScriptRuntime.kt`; 提交 `6670730d4`.
  - [x] P1.3.3 H18: 全部场景 (预热). 链 `VersionCodesInfo.obj -> NativeObject.parentScopeObject -> TopLevelScope -> ... -> ScriptRuntime`. 生成的进程级对象在 `VersionCodesInfo` 初始化时断开父作用域与原型, 每个 runtime 以 `VersionCodesInfo.newObj(topLevelScope)` 建自己的 `util.versionCodes` 原型; `UtilDifferentialTest` 同步改调用. 改动 `runtime/api/augment/util/VersionCodesList.kt`, `runtime/ScriptRuntime.kt`, `app/src/test/.../UtilDifferentialTest.kt`; 提交 `6670730d4`. 未改 KSP 生成器 (曾改过一次, 生成器 jar 重建会让已加载旧 jar 的 Gradle 守护进程在 `kspAppDebugKotlin` 报 `ZipFile invalid LOC header`, 已撤回). 另一种初版做法 (把对象改挂到 `RhinoUtils.standardObjects`) 使后续脚本初始化全部失败, 见附录 C Q11. 新模块层的 `UtilVersionCodesModule` 直接读 `VersionCodesInfoGenerated.list`, 开启新模块层时会绕过本修复, 见附录 C Q12.
  - [x] P1.3.4 H19: S10 F. 链 `Thread<local> -> RealWebSocket.listener -> WebSocketMessageListener.this$0 -> WebSocket.mBridges -> ... -> ScriptRuntime`. `CoreWebSocket.onExit(runtime, reason)` 只处理所属 runtime 的连接 (原来对全部脚本的连接生效, 违反 R2): 监听器解除对 `WebSocket` 的引用, 设置了 exitOnClose 的连接按原超时正常关闭, 其余连接 `cancel()` (okhttp 读线程随之结束). 未设置 exitOnClose 的连接原来在脚本结束后保持打开, 现在断开, 属于行为变化, 已记 changelog, 见附录 C Q10. 改动 `core/web/WebSocket.kt`, `runtime/ScriptRuntime.kt`; 提交 `6670730d4`.
  - [x] P1.3.5 H8: S12 N / F. 链 `LoadedApk$ServiceDispatcher.mConnection -> Plugin.pluginInstance -> ImGuiPluginConnection.runtime -> ...`. `Plugins` 记录传给 `bindService` 的连接 (返回 false 也记), `onExit` 在回收图像之后 `plugins.unbindAll()`. 改动 `runtime/api/Plugins.kt`, `runtime/ScriptRuntime.kt`; 提交 `6670730d4`.
  - [x] P1.3.6 H15: 由 P1.3.1 一并处理 (S2 F 修前线程 +44 / Context +44).
  - [x] P1.3.7 H21: S4a / S4d / S4e F (23046RP50C). 链 `TermSession$2<local> -> Message.callback -> LoopBasedJavaScriptEngine lambda -> mRuntime` 与 `... -> Message.callback -> RhinoUtils lambda (TimerThread.target) -> ScriptRuntime`, 机理见 1.3 H21. 引擎 `execute` 的 Runnable 与 `TimerThread` 的 target 改经 `TimerThread.ReleasingRunnable` 投递, 一开始运行就丢弃委托, Message 即使未被回收也不再通向 runtime. 改动 `core/looper/TimerThread.kt`, `engine/LoopBasedJavaScriptEngine.java`; 提交 `ca95c8744`. 修完这一步 (`8dcba0d67` 的构建) 23046RP50C 的 S4a / S4d / S4e F 仍各残留 1 个, 链变为 `TermSession$2<local> -> Message.target -> Handler.mLooper -> Looper.mThread -> ThreadCompat.target (或 TimerThread.target) -> 脚本执行 / RhinoUtils lambda -> ScriptRuntime`: ART 在线程结束时不清 `Thread.target`. 第二步 `f7c7ee309`: `ThreadCompat` 经运行完即丢弃的持有者把 Runnable 交给 `Thread`, `TimerThread` 不再把 target 交给 `Thread` 而由自己在 run() 结束时清空. 改动 `lang/ThreadCompat.java`, `core/looper/TimerThread.kt`. 证据: 23046RP50C 第一步之前 (`a1e3ab9d9` 的构建, 报告已被覆盖, 数值记在此处) S4a F 1/20 (Java +57.59 MB), S4d F 1/20, S4e F 1/20; 第一步之后 (`gate-23046RP50C-5621-p1-20261020.md`) S4a F 1/20 (Java +1.87 MB), S4d F 1/20, S4e F 1/20; 两步之后见附录 A.
  - [x] P1.3.8 H22 (P1 新发现): S11 (AVD API 24 预热). 链见 1.3 H22. `Device` 在自带 Looper 的线程上首次取 `AudioManager` 时改在不带 Looper 的辅助线程上取得并缓存 (Handler 落到主 Looper). 改动 `runtime/api/Device.java`; 提交 `a1e3ab9d9`.
  - [x] P1.3.9 (非泄漏, 门禁稳定性) `threads.start` 线程退出竞态: 线程先唤醒主 Looper 再移出线程集合, 主线程恰好在两者之间检查时会再次空闲且不再被唤醒, 脚本不结束 (AVD API 24 的 S4e N 22 次中 1 次挂到 60 s 超时). 改为先移出再唤醒. 改动 `runtime/api/Threads.kt`; 提交 `8dcba0d67`.
  - [x] P1.3.10 H23 (P1.4 新发现): 链见 1.3 H23. `SpeechEventGate` 在通道结束 (收到不带轮次的 `end`, 或 `closeLocally` 合成 `end`) 后丢弃 sink, 远端插件继续持有回调 Binder 时也不再通向脚本; 结束后的事件原本就丢弃, 行为不变. 改动 `core/plugin/speech/SpeechSessionProxy.kt`; 提交 `cff2d1fab`; JVM 单测 `SpeechEventGateTest` 3/3 通过 (E1). 门禁场景里没有语音会话, 设备侧只记录发现时的链 (外部触发, 不可稳定复现); 语音场景的门禁覆盖留给 P4.1 (附录 C Q13).
  - changelog (zh-Hans, `ff16eab0d`): 改写 #548 "(试修)" 条目 (R5), 新增 H20 崩溃, WebSocket 结束处理, LeakCanary 三条; 其他语言待维护者同步. P1.3.7-P1.3.9 未另加条目 (同属 #548 条目与 "子线程 / 定时器导致脚本不结束" 的既有范围).
- [x] P1.4 (基线) 三台真机 + AVD 复测, 附录 A 新增 "P1 后" 列; 完成条件: 全部场景两种模式 "残留 runtime" 为 0, Java 堆增量达标; 原生列允许仍有失败 (留给 P3) 但要记编号.
  - 证据 (E3): 宿主 debug versionCode 5679 (`4be14ab38` 的构建, 2026-10-21 08:2x 安装; 工作区当时另含其他会话未提交的改动, augment P2.x 与设置层 P2.x), 预热 2 + 计数 20, 两种模式, S4a `--capture on`, XQ-AT72 的 S7 在宿主无障碍服务运行中跑 (维护者开关一次). 报告 `gate-XQ-AT72-5679-p1-20261021`, `gate-G8441-5679-p1-20261021`, `gate-23046RP50C-5679-p1-20261021`, `gate-Android_SDK_built_for_x86-5679-p1-20261021` (`.md` / `.json`). 结果: 四台 128 个场景模式的 "残留 runtime" 全部为 0 (预热也没有残留, 没有脚本错误或超时), Java 堆增量 -0.04 至 +0.27 MB (阈值 2 MB); XQ-AT72, G8441, 23046RP50C 各 32/32 通过; AVD 28/32, 失败的 4 格 (S1 / S3 / S8 / S12 的 F) 都是原生堆在某一次运行中出现约 +10 MB 的一次性台阶后持平, 不是线性增长, 记为 P3 N1. 线程与 Rhino Context 增量全部为 0 (含 23046RP50C 的 S4a / S5 / S6, `SurfaceSyncGroupTimer` 已不计). XQ-AT72 的 S4e N 第一次跑时 1/20 (H23), 修复前用同一构建以 `--merge` 复跑, 0/20, 表中为复跑结果.

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
| S1 空脚本 | N / F | N `1 / +0.04 / +0.03 / +0 / +0 / 0`; F `1 / +0.04 / +0.02 / +0 / +0 / 0` (单 runtime 钉住 (H17 最近一个 + H18 第一个, 预热里那次计入 retainedWarmups)) | N `0 / +0.11 / +0.07 / +0 / +0 / 0`; F `0 / +0.03 / +0.03 / +0 / +0 / 0` (AVD F 原生堆一次性台阶 +9.59 MB (P3 N1)) | | | |
| S2 threads.start + pool | N / F | N `20 / +30.18 / +0.44 / +0 / +0 / 0`; F `20 / +30.21 / +0.88 / +44 / +44 / 0` (H1 (N, 第二遍链经 Looper.mQueue -> mIdleHandlers -> Loopers); F 另有 44 个池线程与 Rhino Context 存活 (H15)) | N `0 / +0.17 / +0.07 / +0 / +0 / 0`; F `0 / +0.08 / +0.04 / +0 / +0 / 0` | | | |
| S3 timers 嵌套 | N / F | N `20 / +29.23 / +0.36 / +0 / +0 / 0`; F `20 / +29.32 / +0.39 / +0 / +0 / 0` (H1; F 偶发 stopErrors `TimerCallbackRejectedException`) | N `0 / +0.15 / +0.05 / +0 / +0 / 0`; F `0 / +0.07 / +0.05 / +0 / +0 / 0` (AVD F 原生堆一次性台阶 +9.82 MB (P3 N1)) | | | |
| S4a captureScreen 连续 | N / F | N `1 / -0.05 / -0.11 / +0 / +0 / 0`; F `1 / +0.07 / +0.00 / +0 / +0 / 0` (只有 H17 / H18; 截屏线程与原生堆都回到基线, H5 本轮未复现; 门禁期间 `PROJECT_MEDIA` 为 `allow`, 见附录 C Q3) | N `0 / +0.05 / -0.04 / +0 / +0 / 0`; F `0 / +0.08 / -0.02 / +0 / +0 / 0` | | | |
| S4b read + findImage | N / F | N `1 / +0.04 / -0.67 / +0 / +0 / 0`; F `1 / +0.04 / -2.47 / +0 / +0 / 0` | N `0 / +0.11 / -0.65 / +0 / +0 / 0`; F `0 / +0.04 / -2.43 / +0 / +0 / 0` | | | |
| S4c copy / clip / resize | N / F | N `1 / +0.04 / -0.81 / +0 / +0 / 0`; F `1 / +0.04 / -0.31 / +0 / +0 / 0` | N `0 / +0.11 / -0.77 / +0 / +0 / 0`; F `0 / +0.04 / -0.19 / +0 / +0 / 0` | | | |
| S4d matchTemplate + oneShot | N / F | N `1 / +0.04 / -1.29 / +0 / +0 / 0`; F `1 / +0.03 / -5.93 / +0 / +0 / 0` | N `0 / +0.13 / -1.25 / +0 / +0 / 0`; F `0 / +0.06 / -5.88 / +0 / +0 / 0` | | | |
| S4e #372 / #200 复现 | N / F | N `20 / +29.59 / -5.80 / +0 / +0 / 0`; F `20 / +29.82 / -3.48 / +0 / +0 / 0` (H1; F 5 次中 4 次原生崩溃 (H20, `crash-XQ-AT72-5620-S4e-force-20261020.txt`), 表中为第 5 次的完整结果) | N `0 / +0.09 / -5.85 / +0 / +0 / 0`; F `0 / +0.06 / -3.30 / +0 / +0 / 0` (S4e F 另做 5 个进程 110 次强制停止, 0 次崩溃 (H20, `h20-XQ-AT72-5621-S4e-force-20261020.md`)) | | | |
| S5 UI 脚本 + webview | N / F | N `1 / +0.06 / -0.20 / +0 / +0 / 0`; F `1 / +0.05 / -0.20 / +0 / +0 / 0` (只有 H17 / H18) | N `0 / +0.06 / -0.28 / +0 / +0 / 0`; F `0 / +0.04 / -0.32 / +0 / +0 / 0` | | | |
| S6 floaty | N / F | N `1 / +0.06 / +0.09 / +0 / +0 / 0`; F `1 / +0.06 / +0.09 / +0 / +0 / 0` | N `0 / +0.12 / +0.00 / +0 / +0 / 0`; F `0 / +0.07 / +0.02 / +0 / +0 / 0` | | | |
| S7 事件 / 按键 / 传感器 | N / F | N `1 / +0.02 / -0.06 / +0 / +0 / 0`; F `1 / +0.04 / +0.15 / +0 / +0 / 0` (宿主无障碍服务运行中, 含 `auto.registerEvent` / `events.observeKey`; 只有 H17 / H18, H9 / H16 本轮未复现; 两种模式同一进程, 附录 C Q9) | N `0 / +0.08 / -0.03 / +0 / +0 / 0`; F `0 / +0.03 / -0.03 / +0 / +0 / 0` (宿主无障碍服务运行中 (`accessibilityRunning: true`, 维护者开关一次, Q9 做法)) | | | |
| S8 JavaAdapter + loadDex | N / F | N `1 / +0.04 / -0.37 / +0 / +0 / 0`; F `1 / +0.04 / -0.45 / +0 / +0 / 0` (无额外 runtime 残留; 生成类数量留给 P4.4 看 hprof) | N `0 / +0.10 / -0.41 / +0 / +0 / 0`; F `0 / +0.06 / -0.29 / +0 / +0 / 0` (AVD F 原生堆一次性台阶 +10.32 MB (P3 N1)) | | | |
| S9 execScriptFile 重启 | N / F | N `1 / +0.08 / +0.01 / +0 / +0 / 0`; F `1 / +0.08 / +0.03 / +0 / +0 / 0` (无额外残留 (H6 未复现)) | N `0 / +0.14 / +0.01 / +0 / +0 / 0`; F `0 / +0.09 / +0.02 / +0 / +0 / 0` | | | |
| S10 http + websocket | N / F | N `1 / +0.08 / +0.07 / +0 / +0 / 0`; F `19 / +26.90 / +0.69 / +0 / +0 / 0` (F: 19/20 经 okhttp `RealWebSocket` 读线程局部变量钉住 (H19)) | N `0 / +0.10 / +0.06 / +0 / +0 / 0`; F `0 / +0.10 / +0.19 / +0 / +0 / 0` | | | |
| S11 device 接收器 | N / F | N `1 / +0.10 / +0.03 / +0 / +0 / 0`; F `1 / +0.09 / +0.04 / +0 / +0 / 0` (无额外残留 (H7 未复现)) | N `0 / +0.17 / +0.07 / +0 / +0 / 0`; F `0 / +0.10 / +0.03 / +0 / +0 / 0` | | | |
| S12 plugins 绑定 | N / F | N `20 / +53.47 / +0.34 / +0 / +0 / 0`; F `20 / +53.56 / +0.35 / +0 / +0 / 0` (H8: `LoadedApk$ServiceDispatcher.mConnection -> Plugin.pluginInstance -> ImGuiPluginConnection.runtime`, 每次约 2.7 MB) | N `0 / +0.03 / -0.03 / +0 / +0 / 0`; F `0 / +0.02 / -0.02 / +0 / +0 / 0` (AVD F 原生堆一次性台阶 +9.83 MB (P3 N1)) | | | |

设备: XQ-AT72 (API 31) 主表; G8441 (API 28), 23046RP50C (API 35), AVD API 24 各一份同结构表 (放在 evidence 报告中, 本表只记 XQ-AT72 与差异备注).

修前基线 (P0.5, 2026-10-20, 宿主 debug versionCode 5620, 预热 2 + 计数 20, 两种模式): 主表报告 `gate-XQ-AT72-5620-baseline-20261020.md` / `.json`; 其余 `gate-G8441-5620-baseline-20261020.*`, `gate-23046RP50C-5620-baseline-20261020.*`, `gate-Android_SDK_built_for_x86-5620-baseline-20261020.*` (AVD_API_24, x86). 四台共同结论:

- 没有一个场景通过: 每个场景至少残留 1 个 runtime (H17 钉住最近一个, H18 钉住第一个, 后者落在预热里), Java 堆增量 0.03-0.17 MB, 说明单 runtime 钉住之外的噪声很小, T1 的 2 MB 阈值不需要调.
- 线性泄漏 (20/20, 每个 runtime 约 1.5 MB Java 堆, 20 次 +29 至 +31 MB): S2 / S3 / S4e 两种模式 (H1), S12 两种模式 (H8, 约 2.7 MB / 次, 20 次 +53 MB; AVD 上只有 1 个残留, 该机 3-ImGui 未走 `bindService` 路径, 待 P1.3 核对), S10 强制停止 (H19, XQ-AT72 19/20, G8441 17/20, 23046RP50C 19/20, AVD 20/20).
- S2 强制停止四台都是线程 +44 / Rhino Context +44 (每次 2 个池核心线程留在 `sleep` 中且已进入 Context, H15); 其余场景线程与 Context 回到开始前. 23046RP50C (API 35) 的 S4a / S5 / S6 "脚本线程 +1" 是误计: 多出的是 Android 14 起框架自带的 `SurfaceSyncGroupTimer` 线程, 被线程名标记 `Timer` 命中; P1 重建测试 APK 时把 `GateMetrics.SCRIPT_THREAD_MARKERS` 的 `Timer` 收紧 (排除 `SurfaceSyncGroupTimer`), 修前基线保留原口径.
- 原生堆: XQ-AT72 与 23046RP50C 在 +-1 MB 内 (图像场景常为负, 位图在原生堆释放), G8441 (低内存) 0.7-3.4 MB 正向波动, AVD S12 F +9.99 MB 为唯一超 4 MB 的格; 附录 C Q1 维持 "不放宽".
- 强制停止的副作用: S3 / S6 / S8 / S12 的 F 模式偶发 `TimerCallbackRejectedException: Unable to schedule a timer callback because its looper is exiting` (记为 stopErrors, 不影响判定); S4b / S4c / S4d 的 F 模式在 23046RP50C 与 AVD 上偶有第 2 个残留, 链为 `TermSession$2<local> -> Message.callback -> LoopBasedJavaScriptEngine 回调 -> mRuntime` (线程栈局部变量, 待复核是否只是暂态); S4e F 在 XQ-AT72 上 5 次有 4 次进程原生崩溃 (H20), 其余三台本次未崩.
- S4a (18:06-18:36 补跑): 四台两种模式都只有 H17 / H18 的单 runtime 残留 (AVD F 另有 1 个, 同 S4b-S4d F 的 H21), Java 堆 +0.16 MB 以内; 原生堆 XQ-AT72 / 23046RP50C 在 +-0.5 MB 内, G8441 +2.3 MB, AVD +4.5 MB (截屏场景阈值为 4 MB + 一帧, 未超). 23046RP50C 第一次跑正常模式时有 2/22 次 `requestScreenCapture` 返回 false (系统拒绝, 其中 1 次等待 89 s), 复跑 22 次全部成功, 表中为复跑结果; 复跑期间 (约 18:31) 维护者在几台手机上按过音量键, S4a 不监听按键, 判断无影响. 该机 `PROJECT_MEDIA` 原为 `ignore` (1 天前被拒的记录), 按拍板恢复为 `default`; 其余三台原本即为默认.
- S7: XQ-AT72 的 S7 是开启宿主无障碍服务后的结果 (Q9); 其余三台的 S7 未启用无障碍部分 (按键与无障碍事件的覆盖以 XQ-AT72 为准, G8441 留给 P3.4).
- 设备占用: 23046RP50C 在基线期间 2 次被其他会话重装宿主或测试 APK (16:53, 17:19), 第 3 次 (约 17:48) 实为 adb 断开, 当时的防护把读不到包信息误判为重装, 已改为单独报 "设备离线"; S6-S12 以 `--merge` 补跑合并, S12 F 于 18:24 补齐. `run_gate.py` 的设备占用防护: 开始前有其他 `am instrument` 就等待, 运行中宿主或测试 APK 被替换就停止并标记 `invalid`.

P1 后 (P1.4, 2026-10-21, 宿主 debug versionCode 5679, 即 `4be14ab38` 的构建): 主表按 XQ-AT72 填写, 报告 `gate-XQ-AT72-5679-p1-20261021.md` / `.json`; 其余 `gate-G8441-5679-p1-20261021.*`, `gate-23046RP50C-5679-p1-20261021.*`, `gate-Android_SDK_built_for_x86-5679-p1-20261021.*`. 四台共同结论:

- 残留 runtime: 128 个场景模式全部为 0, 预热中也没有 (修前每个场景至少 1 个). Java 堆增量 -0.04 至 +0.27 MB. 线程 / Context / 引擎增量全部为 0 (修前 S2 F 线程与 Context 各 +44).
- 原生堆: XQ-AT72 与 23046RP50C 在 -5.9 至 +0.5 MB (图像场景为负); G8441 (低内存) +0.6 至 +2.9 MB, 全部在阈值内; AVD API 24 的 S1 / S3 / S8 / S12 F 各有一次约 +10 MB 的台阶 (第 7 至 19 次之间出现一次, 之后持平; 修前基线 AVD S12 F 的 +9.99 MB 也是同一现象), 编号 P3 N1, 留给 P3 判断是否为 x86 模拟器上 jemalloc 新开 arena 的一次性占用.
- 过程中的中间报告: `gate-*-5621-p1-20261020.*` 为 `8dcba0d67` 的构建 (XQ-AT72 32/32, AVD 31/32, 23046RP50C 29/32: H21 的第二条路径, 已由 `f7c7ee309` 修复), 保留作 P1.3.7 的证据.

---

## 附录 B 静态注册表与配对清单 (P0.6 生成初稿, P4.2 回填处置)

### B.1 进程级静态注册表

| 注册表 | 键 / 值 | 现状 | 规范 (T7) 处置 | 阶段 |
| --- | --- | --- | --- | --- |
| `core/looper/LooperHelper.sLoopers` | Thread -> Looper (强) | 子线程不移除 (H1) | 线程退出 finally 移除 + 弱引用键; P1.1 已改为键值都弱引用 (`a2ee7eb03`) | P1.1 |
| `core/looper/TimerThread.sTimerMap` | Thread -> WeakReference<Timer> (WeakHashMap) | 已弱 | 保持 | - |
| `core/image/ImageWrapper.registry` | ScriptRuntime -> List<WeakReference<ImageWrapper>> (WeakHashMap) | 已弱, onExit `recycleAll(runtime)` | 保持; 核对构造入口登记 | P3.2 |
| `ui/enhancedfloaty/FloatyService.windows` | CopyOnWriteArraySet<FloatyWindow> (强) | `Floaty.closeAll()` 移除 | 门禁 S6 覆盖; 异常路径核对 | P1.3 / P4.2 |
| `core/accessibility/AccessibilityService` companion 总线 | 回调 / 委托 / 观察者 / 按键监听 (强) | 回调按 owner id 清理; 其余依赖各模块 recycle | 全部按 owner id, 计数可读 | P4.2 |
| `rhino/AndroidClassLoader.mDexClassLoaders` | path -> DexClassLoader (强, 进程级) | 两处 remove | 按 runtime 作用域化 | P4.4 |
| `engine/ScriptEngineService` 全局执行监听器 | List (强) | 10 注册 / 4 注销 | 成对或执行级 | P4.2 |
| `engine/ScriptEngineManager.mGlobalVariableMap` | name -> Object | 只有 "context" (Application) | 保持 | - |
| `app/GlobalAppContext` | Application + 主线程 Handler | 进程级 | 白名单 | - |
| `core/accessibility/AccessibilityService.defaultBridge` (P0.5 门禁发现) | 进程级默认 `AccessibilityBridgeImpl`, 其 `tap` 由每个脚本的 `SimpleActionAutomator` 初始化时写入 | 强引用最近一个 runtime (H17), 每个场景残留 1 | `tap` 改为按 runtime 登记并在 onExit 撤销, 或只持弱引用; P1.3.2 已在 onExit 撤销 (`6670730d4`) | P1.3 |
| `runtime/api/augment/util/VersionCodesInfo.obj` (P0.5 门禁发现) | 进程级 `NativeObject`, 父作用域为首次访问它的脚本的 TopLevelScope | 强引用第一个 runtime (H18) | 在进程级标准作用域中创建, 或按 runtime 缓存; P1.3.3 已断开生成对象并按 runtime 新建 (`6670730d4`) | P1.3 |
| Application `ContextImpl.mServiceCache` 中的 `AudioManager` (P1 门禁发现) | 系统服务缓存, 进程级 | 首次在脚本线程取得时其事件 Handler 绑定脚本线程的 Looper, 钉住该脚本 (H22) | P1.3.8 已改为在无 Looper 的线程上首次取得 (`a1e3ab9d9`); 其他按需取系统服务的模块 P4.2 复核 | P1.3 / P4.2 |
| `core/console/ConsoleImpl.sShowingConsoles` | `CopyOnWriteArraySet<ConsoleImpl>` (强) | 显示中加入, 隐藏时移除 | 门禁加 "控制台悬浮窗显示后结束" 场景再定 | P4.2 |
| `core/web/WebSocket.instances` | `ArrayList<WeakReference<WebSocket>>` | 值为弱引用, 列表本身只增不减 (留下已清除的弱引用) | onExit 时顺带清理已清除的项 | P4.2 |
| `runtime/api/augment/shell/Shell.runtimeDefaults` | `synchronizedMap(WeakHashMap<ScriptRuntime, ...>)` | 已弱 | 保持 | - |
| `runtime/api/ScriptToast.queue` | `ArrayDeque<ToastTask>` (只含消息与 owner id 字符串) | 不持 runtime | 保持 | - |
| `runtime/api/Plugins.packages` | packageName -> 插件 `Package` (进程级) | 不持 runtime; 脚本侧绑定见 H8 | 保持 | - |
| `engine/RuntimeWebActionActivity.actions / grants / captures`, `RuntimeWebLocalDownloads.active`, `RuntimeWebMediaService.entries` | 字符串 id -> 会话对象 (强) | 未核对按脚本清理 | 逐个核对, 必要时按 owner id 清理 | P4.2 |
| `util/RhinoUtils.objectPrototype / arrayPrototype / functionPrototype` | 进程级标准作用域的原型 (强 `Scriptable`) | 不属于任何脚本 | 白名单 (T8) | - |

### B.2 配对清单 (登记 -> 释放)

由 `static_audit.py` 生成 (完整表见 `docs/memory/evidence/static-audit-20261020.md`), 这里只列按脚本创建或门禁已证实的项; 归属按包路径推断, 已人工复核过 "处置" 一列.

| 文件 | 登记 -> 同文件释放 | 归属 | 门禁 / 复核结论 | 处置阶段 |
| --- | --- | --- | --- | --- |
| `core/looper/LooperHelper.java` | `Looper.prepare` 1 -> quit 3 | 按脚本 | 释放路径不覆盖 `threads.start` 子线程 (H1, S2 / S3 / S4e 证实) | P1.1 |
| `core/looper/Loopers.kt` | `addIdleHandler` 1 -> `removeIdleHandler` 1; `ThreadLocal` 6 -> `remove()` 0 | 按脚本 | IdleHandler 只从主脚本线程队列移除 (H2, S2 第二遍链经 `MessageQueue.mIdleHandlers`); 6 个 ThreadLocal 由 Loopers 实例持有, UI 脚本的值落在主线程的 ThreadLocalMap 中, 待门禁确认 | P1.1 / P1.3 |
| `runtime/api/Plugins.kt` | `bindService` 3 -> `unbindService` 0 | 按脚本 | H8 证实: S12 经 `LoadedApk$ServiceDispatcher.mConnection -> Plugin.pluginInstance -> ImGuiPluginConnection.runtime -> PluginRuntime.topLevelScope` 钉住 runtime | P1.3 |
| `runtime/api/tts/AndroidTtsBackend.kt` | `bindService` 2 -> 0 | 按脚本 | 实为 `TextToSpeech` 构造与 `shutdown()` (由 TextToSpeech 内部解绑), 文本扫描误报, 待 TTS 场景确认 | P4.2 |
| `core/plugin/PluginAutoWake.kt` | `bindService` 2 -> 0 | 进程级 | 唤醒用的短绑定, 进程级可接受, P4.2 复核 | P4.2 |
| `runtime/api/Device.java` | `registerReceiver` 1 -> 0 | 按脚本 | `registerReceiver(null, ...)` 读粘性广播, 排除 (H7) | - |
| 其余 `registerReceiver` 11 个文件 (`AutoJs.kt`, `McpServerTool`, `ThreeStoveAgentConnectionOwner`, `PluginLauncherIconController`, 4 个 `*PluginStateMonitor`, `PythonHostCapabilityBroker`, `NodeBridgeDeviceInfo`, `NodeJsRuntimePluginHost`, `DeviceUtils`) | -> 0 | 进程级 | 与 H7 清单一致, 均为进程级单例; S11 未见接收器相关残留 | P4.2 复核 |
| `core/accessibility/AccessibilityService.kt`, `AccessibilityTool.kt`, `KeyInterceptor.kt`, `OnKeyListener.kt`, `event/A11yEventPulse.kt`, `core/record/GlobalActionRecorder.java`, `runtime/api/augment/terminal/TerminalSessionNativeObject.kt`, `runtime/api/augment/toolkit/RuntimeToolkit.kt` | `.subscribe(` -> dispose 0 | 按脚本 | H9 / H16; S7 在 XQ-AT72 上开启宿主无障碍服务后 (含 `auto.registerEvent` / `events.observeKey`) 未见额外残留 | P1.3 / P4.2 |
| `core/web/InjectableWebClient.java`, `engine/RuntimeViewComponents.kt`, `runtime/api/augment/web/Web.kt`, `core/ui/inflater/inflaters/JsWebViewInflater.kt` / `WebViewInflater.kt`, `core/ui/widget/EventWebView.kt` | WebView 创建 -> `destroy()` 0 | 按脚本 | H10; S5 (UI 脚本 + webview) 的结果见附录 A | P1.3 |
| `ui/doc/DocumentationPageController.kt`, `ui/widget/NestedWebView.java` | WebView 创建 -> `destroy()` 0 | 按页面 | 非脚本路径, 留给 P4.5 LeakCanary 巡检 | P4.5 |
| 其余 `.subscribe(` 34 个文件 (`ui/`, `timing/TimedTaskManager.kt`, `network/UpdateChecker.java`, `pluginclient/` 等) | -> dispose 0 | 按页面 / 进程级 | 大多是一次性 `Observable` (完成即释放), P4.5 巡检时逐个确认 | P4.5 |
| `engine/RuntimeJavaSession.kt`, `runtime/api/augment/installer/InstallerPromises.kt`, `runtime/api/augment/terminal/TerminalPromises.kt` | `ThreadLocal` -> `remove()` 0 | 按脚本 | 待对应模块的门禁场景 | P4.2 |

### B.3 `ScriptRuntime.onExit()` 现有释放顺序 (P4.1 迁移时保持)

P1 之后 (P1.3.1 H20, P1.3.2 H17, P1.3.4 H19, P1.3.5 H8):

images.cancelPendingQuantizations -> console.onRuntimeExit -> CoreWebSocket.onExit(this) (只处理本 runtime 的连接: 监听器解除对脚本的引用, exitOnClose 的连接超时后正常关闭, 其余取消) -> automator.removeAllEventsForThisRuntime -> accessibilityBridge.releasePulse / setAccessibilityListener(null) -> RootUtils.resetRuntimeOverriddenRootModeState -> compose.close -> ThreadCompat.interrupted -> floaty.closeAll -> events.emit("exit") -> mail / installer / epub / gallery / terminal / speech / ai / tts .close -> threads.shutDownAllAndAwait (中断 threads.start 线程, shutdownNow 脚本未关闭的线程池, 等待这些线程与脚本已关闭的线程池的工作线程结束, 脚本线程上最多 2 s, UI 线程不等待) -> ImageWrapper.recycleAll(this) (仍有线程存活时改由辅助线程在它们结束后回收, 最多等 60 s, 超时则放弃回收并记 warn) -> plugins.unbindAll -> events.recycle -> media.recycle -> loopers.recycle -> accessibilityBridge.tap = null -> recycleShell -> images.releaseScreenCapturer / stopScreenCapturerForegroundService -> sensors.unregisterAll -> timers.recycle -> ui.recycle -> closeableManager.recycleAll.

P1 之前 (基线 `master@c35f603be`):

images.cancelPendingQuantizations -> console.onRuntimeExit -> CoreWebSocket.onExit -> automator.removeAllEventsForThisRuntime -> accessibilityBridge.releasePulse / setAccessibilityListener(null) -> RootUtils.resetRuntimeOverriddenRootModeState -> compose.close -> ImageWrapper.recycleAll(this) -> ThreadCompat.interrupted -> floaty.closeAll -> events.emit("exit") -> mail / installer / epub / gallery / terminal / speech / ai / tts .close -> threads.shutDownAll -> events.recycle -> media.recycle -> loopers.recycle -> recycleShell -> images.releaseScreenCapturer / stopScreenCapturerForegroundService -> sensors.unregisterAll -> timers.recycle -> ui.recycle -> closeableManager.recycleAll.

---

## 附录 C 待决事项

| 编号 | 问题 | 需要拍板的阶段 | 默认倾向 |
| --- | --- | --- | --- |
| Q1 | G8441 (API 28, 低内存) 上 T1 的 Java 堆 / 原生堆阈值是否放宽 | P0.5 之后 | 不放宽 (a) 的 0 残留, 其余按噪声 +50% |
| Q2 | `memory` 模块写旧 augment 包 (`augment/memory/Memory.kt`) 还是直接写新脚本模块层 (`runtime/api/modules`, augment 路线图 P1.2 的 KSP 层, 当前隐藏开关默认关) | P5.1 之前 | 旧包新文件 (开关关时也可用); 若届时新层已默认开则写新层 |
| Q3 | 门禁场景 S4 的截屏授权: debug 构建是否加一个 "测试用授权缓存" 开发者选项, 避免每次人工点 | P0.3 | 已拍板 2026-10-20: 方案 (a), 不加宿主授权缓存 |
| | P0.3 补充 (2026-10-20): S4a 每次运行都是新的 runtime, 脚本结束时 `images.releaseScreenCapturer()` 释放 MediaProjection, 所以 "授权一次后复用" 在现有代码里不成立 (每次运行都会弹系统授权框, 一台设备 44 次); API 34 起系统也禁止重用同一授权结果, 宿主侧缓存只对 API 33 及以下有效. 可选: (a) 门禁期间用 `adb shell appops set org.autojs.autojs6 PROJECT_MEDIA allow` 让系统跳过授权框, 跑完恢复为 `default` (不改宿主代码, 对 API 24-35 都有效, 但这是设备上的授权设置, 须维护者同意); (b) 按原倾向加 debug 授权缓存 (宿主改动, 只覆盖 API <= 33); (c) 维护者逐次点击. S4a 已实现, 默认跳过 (`--capture on` 才运行) | P0.3 | 已拍板 2026-10-20 (维护者经调度会话转达): 方案 (a). `run_gate.py --capture on` 在每个 S4a 进程前设 `allow`, 结束 (含失败 / 中断路径) 后恢复 `default`, 写在 `tools/memory/README.md`; 四台已补跑 (附录 A) |
| Q9 | S7 的按键监听 (`events.observeKey`) 与无障碍事件 (`auto.registerEvent`) 需要宿主自身的无障碍服务; 四台门禁设备上都只开着 AutoJs4 Runtime 插件的无障碍服务, 宿主的未开启, 因此基线 S7 只覆盖 `events.on` / `events.emitter` / `sensors`. 是否请维护者在 XQ-AT72 上开启宿主无障碍服务后补跑 S7 | P0.5 | 已拍板 2026-10-20 (维护者经调度会话转达): 在 XQ-AT72 上开启宿主无障碍服务 (维护者手动开启, 不代点) 后补跑 S7 两种模式, 跑完提醒维护者可关回; G8441 留给 P3.4. 已执行 (19:01-19:40): instrumentation 会把宿主无障碍服务置于 "Crashed services", 改为同一进程跑两种模式并由维护者在测试等待期间关掉再开启一次服务; 结果见附录 A S7 行. 维护者可随时关回 |
| Q4 | issue #474 (Shizuku 僵尸进程 OOM) 是否纳入本路线图 | P6 | 不纳入, 单独立项 |
| Q10 | WebSocket 在脚本结束时的处理是可见行为变化 (R1, P1.3.4): 未设置 exitOnClose 的连接原来在脚本结束后保持打开 (事件已无法派发, 读线程与连接一直存在), 现在断开; 设置了 exitOnClose 的连接原来会在任意脚本结束时被关闭, 现在只在所属脚本结束时关闭. 是否接受, 以及文档 `websocket` 页是否需要补一句 | 已拍板 2026-10-21: 接受 (P1.3 已实施) | 接受 (changelog 已写, `ff16eab0d`); 文档在 P5.2 同步文档仓库时补 "脚本结束时未设置 exitOnClose 的连接会被断开" |
| Q11 | 在脚本线程内首次初始化 `RhinoUtils.standardObjects` (惰性属性, 内部 `Context.enter` + `initStandardObjects`) 后, 该进程之后的脚本全部在 `ScriptRuntime.initEpilogue` 失败 (`TypeError: Cannot read property "getNotifier" from undefined`, `array-observe.min.js`); P1.3.3 的初版做法触发过 (2026-10-20 20:39, XQ-AT72), 已改用不触发它的做法, 原因未分析. 现有代码里 `getDeclarationScope` 等兜底路径同样可能在脚本线程首次初始化它 | P4.4 之前 | 列入 P4.4 排查 (与 Context / 标准对象的归属一并处理), 不阻塞 P1 |
| Q12 | 新脚本模块层 (augment 路线图) 的 `UtilVersionCodesModule` 直接读 `VersionCodesInfoGenerated.list`, 开启新模块层时 `VersionCodesInfo` 不初始化, 进程级生成对象会钉住第一个脚本 (H18 复发) | P2 (新层默认开启之前) | 已协调: augment P2.3 会话 2026-10-21 改为读 `VersionCodesInfo.list` (该会话的提交); 新层默认开启前门禁在新层开关打开时补跑一次 S1 |
| Q13 | 门禁没有语音场景: H23 由外部触发的语音会话暴露, 无法在门禁中稳定复现. 是否在 P4.1 增加 "speech 会话打开后脚本结束 (正常 / 强制停止)" 场景 (需要设备装有 3-Macaw Speech 或 3-Stone AI 语音插件, 不发声的识别会话) | P4.1 之前 | 增加, 插件未安装时跳过 (同 S12) |
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
| `tools/memory/run_gate.py`, `test_run_gate.py`, `README.md` | 门禁驱动与其单元测试 | P0.4 |
| `tools/memory/static_audit.py` | 源码静态审计 | P0.6 |
| `app/src/androidTest/java/org/autojs/autojs/memory/GateMetrics.kt` | 六项度量的临时实现 (P2.1 后改用 `MemoryDiagnostics`) | P0.2 |
| `app/src/androidTest/java/org/autojs/autojs/memory/GateHeapPathAnalyzer.kt` | shark 最短引用链 (含忽略 `Thread.target` 的第二遍) | P0.2 |
| `app/src/androidTest/java/org/autojs/autojs/memory/GateLoopbackServer.kt` | S10 的回环 HTTP / WebSocket 服务 | P0.3 |
| `tools/memory/device_diagnostics_page.py` | 诊断页面截图 | P2.2 |
| `app/src/androidTest/java/org/autojs/autojs/memory/RuntimeLeakGateDeviceTest.kt` | 门禁测试 | P0.2 |
| `app/src/androidTest/java/org/autojs/autojs/memory/scenarios/GateScenarios.kt` | 场景脚本 (Kotlin 字符串, ES5) | P0.3 |
| `app/src/main/java/org/autojs/autojs/runtime/memory/MemoryDiagnostics.kt` | 诊断数据源 | P2.1 |
| `app/src/main/java/org/autojs/autojs/ui/settings/memory/MemoryDiagnosticsActivity.kt` | 诊断页面 | P2.2 |
| `app/src/main/java/org/autojs/autojs/runtime/resource/RuntimeResourceScope.kt` | 统一所有权 | P4.1 |
| `app/src/test/java/org/autojs/autojs/memory/StaticReferenceGuardTest.kt` | 源码守卫 | P4.3 |
| `app/src/main/java/org/autojs/autojs/runtime/api/augment/memory/Memory.kt` | 脚本 API | P5.1 |
| `.github/workflows/memory-gate.yml` | CI | P6.1 |
