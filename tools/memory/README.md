# 内存门禁工具

配合 `docs/memory/ROADMAP.md` 使用. 门禁本体是仪器测试 `app/src/androidTest/java/org/autojs/autojs/memory/RuntimeLeakGateDeviceTest.kt` (场景脚本在同目录 `scenarios/GateScenarios.kt`), 本目录是驱动与审计脚本. 脚本不安装 APK, 不点屏幕, 不改设置 (只用于模拟器的 ci_gate.py 除外, 见下文); 需要人工确认的对话框 (S4a 的截屏授权) 由维护者在手机上操作.

## run_gate.py: 跑门禁并生成证据

```
py -3.12 -I tools/memory/run_gate.py -s SERIAL [--scenarios S1,S2] [--mode normal|force|both] [--hprof] [--label baseline] [--meminfo off|phases|runs] [--out docs/memory/evidence]
```

准备 (一次): 构建并安装 debug 宿主与它的测试 APK, 两者须来自同一次构建 (构建前看一眼是否有其他会话在编译宿主):

```
.\gradlew.bat :app:assembleAppDebug :app:assembleAppDebugAndroidTest
adb -s SERIAL install -r -t app/build/outputs/apk/app/debug/<宿主 APK>
adb -s SERIAL install -r -t app/build/outputs/apk/androidTest/app/debug/<测试 APK>
```

运行方式:

1. 每个场景与模式各起一个 instrumentation 进程 (`am instrument` 会重启宿主, 运行前还会 `am force-stop`), 某个场景的泄漏不会影响下一个场景的数值. 一个进程内: 预热 2 次, 再执行 20 次 (`--warmups` / `--iterations`).
2. 每次运行只通过 `EngineLifecycleCallback` 持有引擎与 `ScriptRuntime` 的弱引用; 运行结束后 `Runtime.gc` + `System.runFinalization` 3 次, 最长轮询 `--poll-ms` (默认 10 s, 连续 3 次未回收后降为 1 s 以控制时长), 最后再统一回收一次判定残留.
3. 强制停止模式 (F): 脚本调用 `gateReady()` 表示工作负载已开始 (S9 还要等 4 个引擎都创建), 之后等 `--force-delay-ms` (默认 1 s), 在主线程对本次运行的每个存活引擎调用 `forceStop()` (与任务列表的停止按钮相同).
4. 有 runtime 残留时, 设备端 `Debug.dumpHprofData` 后用 shark (LeakCanary 2.14 的分析库) 找每个已退出 (`isExiting == true`) 的 `ScriptRuntime` 的最短强引用链, 写进报告. `--hprof` 保留并把 hprof 拉回 `<out>/hprof/` (不提交), `--no-analyze` 跳过设备端分析 (ROADMAP T4 的退路).
5. 结果从 instrumentation status (`aj6mem_json`) 读取; 取不到时依次退回到设备上的 JSON 文件 (`/sdcard/Android/data/org.autojs.autojs6/files/memory-gate/`) 与 logcat `AJ6MEM` 分段行.

输出: `<out>/gate-<model>-<versionCode>[-<label>]-<yyyyMMdd>.md` 与同名 `.json` (`results` 下一行一个场景报告, 含每次运行的六项度量). 终端每个场景一行:

```
S2   N FAIL  retained=20/20 java=+3.10MB native=+0.12MB threads=+0 contexts=+0 engines=0
     20 runtime(s), root STATIC_FIELD: LooperHelper.sLoopers -> ...
```

单元格 (ROADMAP 附录 A): `残留 / Java 堆增量 MB / 原生堆增量 MB / 脚本线程增量 / Rhino Context 增量 / 存活引擎数`. 阈值 (T1): 残留 0, Java <= 2 MB, 原生 <= 4 MB (截屏场景再加一帧), 线程与 Context 回到开始前, 引擎回到开始前; P3.1 起另有 (g): 未回收的图像 (`ImageWrapper.liveCount`) 与未关闭的 media Image (`ImageWrapper.openMediaImageCount`) 回到开始前, 存活 Mat (`core/opencv/Mat.getLiveCount`) 回到预热结束时. P3.4 起, 宿主无障碍服务运行时的 S7 与 S7b 另判 AccessibilityNodeInfo 的可达实例数 (VMDebug) 相对预热结束时不超过 +50 (框架的 AccessibilityCache 会随前台窗口重新填充, 节点泄漏则每次运行增长数百个; ROADMAP 附录 C Q33). P4.2 起另判进程级注册表 (`runtime/memory/StaticRegistries.counts()`: LooperHelper, FloatyService 窗口, 显示中的控制台, WebSocket, Broadcast, ScriptEngineService 全局监听器, 无障碍四条总线) 在场景结束时与预热结束时相同 (报告的 `registries` / `registriesDelta`, 任一项增长即不通过).

原生明细 (ROADMAP P3.1, 报告的 "Native detail" 表与终端行尾的 `peak= images= media= mats=`):

- Peak: 每次运行期间每 20 ms 采样 `Debug.getNativeHeapAllocatedSize`, 取高于运行开始时的最大值 (在运行后的强制回收之前, 能看到只等 finalizer 释放的原生内存, issue #372 的形态); 表中为计数运行中的最大值, 只作观察, 不参与判定.
- Meminfo: `--meminfo phases` (默认) 在场景开始, 预热结束与场景结束时取本进程的 `dumpsys meminfo <pid>` (经 UiAutomation 的 shell 身份在测试进程内执行, 时间点与六项度量的采样一致, 比驱动脚本在进程外取更准), 记录 Native Heap 的 Pss / Heap Alloc, App Summary 的 Graphics, Unknown 的 Pss 等, 表中为预热结束到场景结束的增量; `--meminfo runs` 每次运行后都取 (每次约 0.3-1 s, 用于定位原生台阶出现在哪一次); `off` 不取. 需要宿主无障碍服务的 S7 / S7b 不取 (不连接 UiAutomation, 以免影响服务绑定).
- Counters / Instances: 宿主计数器每次运行后都读; `dalvik.system.VMDebug` 的实例计数 (Bitmap, `org.opencv.core.Mat`, AccessibilityNodeInfo 等, 会先触发一次 GC) 在开始, 预热结束与结束时读, 隐藏 API 不可用时记 `-`.
- Script stats: 场景脚本经 `gateStat(name, value)` 报告的数值 (S4f 的帧数, 空帧数, 最慢一次截图耗时, 截图后最多同时打开的 media Image 数; S7b 的节点数), 表中为计数运行中的最大值.
- Classes (ROADMAP P4.4): 预热结束到场景结束的增量: 已加载类数 (`Debug.getLoadedClassCount`, 公开 API, 各 API 级别都有; ART 卸载类加载器时一并减去其中的类) / VMDebug 的 `java.lang.Class` 可达实例数 (即 hprof 中的类记录数) / VMDebug 的 `DexClassLoader` / 宿主应用类加载器中由 `runtime.loadDex` 等登记的加载器数 (`AndroidClassLoader.mDexClassLoaders`, 经反射读取, 修前的宿主也能读) / VMDebug 的 `WebView`. 场景可声明其中某几项的上限 (`instanceLimits` / `counterLimits` 为预热结束到场景结束的增量, `perRunCounterLimits` 为每次计数运行增量的中位数, 超过即不通过; VMDebug 项只在 VMDebug 可用的 API 24 / 28 上判定), 也可声明必须为 0 的脚本统计 (`failingStats`; 正常结束模式下每次运行都必须报告该统计). 已加载类与 `Class` 实例会在某一次运行中出现一次性台阶 (进程内首次用到的平台, LeakCanary 或宿主代码, 实测 +17 至 +62 个, 出现在哪一次运行不固定), 因此只按中位数判定: 每次运行都多留类时中位数大于 0, 一次性台阶不影响中位数. P4.4 起: S8 判定 `DexClassLoader` 与宿主登记的加载器 +0, 已加载类每次运行增量的中位数为 0; S15 判定 `WebView` +0; S16 判定 `innerRetainedWhileOuterRuns` 为 0.

其他参数: `--capture on` 运行 S4a 与 S4f, `--wake` 每个场景前发送 `KEYCODE_WAKEUP` (只点亮屏幕, 不点击), `--timeout` 单个场景与模式的上限秒数 (默认 1500), `--merge` 只替换同名报告中本次重跑的场景与模式 (补跑被打断的部分).

S4a / S4f 的截屏授权 (ROADMAP 附录 C Q3, 维护者 2026-10-20 拍板方案 (a)): 每次运行都是新的 runtime, 脚本结束时会释放 MediaProjection, 因此每次都会重新申请授权. `--capture on` 时脚本在每个 S4a / S4f 进程开始前执行 `adb shell appops set org.autojs.autojs6 PROJECT_MEDIA allow`, 系统据此跳过授权框; 该进程结束后 (包括失败, 超时与异常路径) 立即执行 `appops set ... PROJECT_MEDIA default` 恢复. 报告头部记录设置前的模式与恢复后的模式. 脚本被强行结束 (例如直接杀掉 Python 进程) 时来不及恢复, 需手动执行 `adb -s SERIAL shell appops set org.autojs.autojs6 PROJECT_MEDIA default`.

S7 的无障碍部分与 S7b (ROADMAP 附录 C Q9): instrumentation 在启动和结束时都会杀掉宿主进程, 系统随即把宿主无障碍服务列入 "Crashed services" 并不再绑定, 测试进程里因此拿不到服务 (设置里仍显示开启). 做法:

```
py -3.12 -I -u tools/memory/run_gate.py -s SERIAL --scenarios S7,S7b --keep-process --one-process --a11y-wait 600 --timeout 3600 --merge --label <名>
```

`--one-process` 让所选的全部场景与模式在同一个进程里跑 (服务只需重新开启一次), `--keep-process` 跳过场景前后的 `am force-stop` (它会把服务从已开启列表中移除), `--a11y-wait` 让 S7 / S7b 最长等待该秒数 (0-900, 测试端的上限) 并在手机上反复弹出 Toast; 看到提示后由维护者在系统无障碍设置里把 AutoJs6 关掉再开启一次, 服务即绑定到正在运行的测试进程. 脚本不代点, 也不改设置; 跑完后服务仍会落回 "Crashed" 状态, 维护者可直接关回. 注意: S7 开始运行后, 场景脚本里的 `auto.registerEvent` 会调用 `ensureService()`, 宿主有安全设置 (或 root / Shizuku) 权限时会自动重新启用无障碍服务, 所以 S7 运行期间手动关闭的服务会立即被打开 (2026-10-21 维护者在 XQ-AT72 上观察到); 要关回请等 S7 结束.

设备占用防护 (ROADMAP R6): 每个场景开始前若设备上已有其他 `am instrument` 在运行就等待 (最长 10 分钟); 运行中宿主或测试 APK 被替换则停止并把该场景标为 `invalid`; 设备离线 (adb 断开) 时停止, 已读到的报告保留.

场景表 (T2): S1 空脚本, S2 `threads.start` x 10 + `threads.pool`, S3 定时器嵌套, S4a 截屏 (需授权), S4b `images.read` + `findImage`, S4c `copy` / `clip` / `resize`, S4d `matchTemplate` + `oneShot`, S4e 多线程图像 (#372 / #200 的有限版本), S4f 连续 200 次 `captureScreen` 不显式回收 (issue #204 的 "卡住不返回"; 每个进程固定预热 1 + 计数 3, 单次截图超过 15 s 或截图后打开的 media Image 超过 1 个即脚本失败; 需 `--capture on`), S5 UI 脚本 + webview (经 `ScriptExecuteActivity`, 等 `onDestroy`), S6 `floaty.window` + `floaty.rawWindow`, S7 事件 / 按键 / 传感器 / 无障碍事件 (按键与无障碍部分需宿主无障碍服务, 未开启时只跑其余部分并在报告中记录), S7b `className(...).find()` / `findOnce` / `children` / `parent` 循环 (需宿主无障碍服务, 未绑定时跳过), S8 `JavaAdapter` + `runtime.loadDex`, S9 `engines.execScriptFile` 自我重启 3 层, S10 `http.get` + WebSocket (测试进程内的回环服务, 不需要网络), S11 `device.*` 读取 / 振动 / `keepScreenOn`, S12 `plugins.load` (3-ImGui, 未安装时跳过), S13 `speech.listen({ source: 'external' })` 开着识别会话时结束 (只喂静音帧, 不发声不录音; 3-Macaw Speech 与 3-Stone AI 都未安装时跳过, ROADMAP 附录 C Q13), S14 控制台悬浮窗显示中结束 (`console.setExitOnClose(500)`, 未授悬浮窗权限时与 S6 一样跳过), S15 `web.newInjectableWebView` 在主线程 (`ui.run`) 创建且未挂到窗口上时脚本结束 (ROADMAP 附录 C Q42), S16 UI 脚本启动另一个 UI 脚本, 后者先结束 (两者共用主线程的 Rhino Context; 外层脚本在后者结束后用 `memory.collect()` 与 `memory.retained()` 检查它是否已被回收, 报告为 `innerRetainedWhileOuterRuns`; ROADMAP H27). P4.4 起 S8 每次运行加载 DEX 的一份新副本 (新路径, 与反复加载变化的文件的脚本相同), 以便看出按路径登记的加载器是否随脚本释放.

度量自 P2.1 起全部取自宿主的 `org.autojs.autojs.runtime.memory.MemoryDiagnostics` (`GateMetrics.kt` 只做转接; Rhino Context 计数为 `AndroidContextFactory.activeContextCount`, 图像数为 `ImageWrapper.liveCount`), 与开发者选项 "内存诊断" 页面同源 (ROADMAP T3). 脚本线程按线程名标记计数 (`ScriptThread-` / `Spawn-` / `Pool-` / `Timer` / `ScreenCapturer` / `LooperThread`), Android 14 起框架自带的 `SurfaceSyncGroupTimer` 不计 (P1 起).

### --suite bridge: 桥接泄漏门禁 (docs/memory-phase2)

```
py -3.12 -I -u tools/memory/run_gate.py -s SERIAL --suite bridge [--scenarios ND1,ND7a | BN1,QJ1 | LU1,PY1] [--plugin-commit <提交> | bun=<提交>,quickjs=<提交> | lua=<提交>,python=<提交>] [--capture on] [--dump-after-last] [--label mp2-baseline]
```

第二期 (`docs/memory-phase2/ROADMAP.md` T1-T4) 用同一套驱动跑插件引擎脚本. 门禁本体是 `app/src/androidTest/java/org/autojs/autojs/memory/bridge/BridgeLeakGateDeviceTest.kt` (场景脚本在同目录 `BridgeScenarios.kt`, 弱引用跟踪与绑定 / PSS 读取在 `BridgeTracker.kt`), 证据默认写到 `docs/memory-phase2/evidence/`, 报告头部按引擎列出本次场景用到的插件 (包名, versionCode, `--plugin-commit` 给出的提交; 只给一个提交时用于全部引擎, `--merge` 时保留之前运行的插件行). 默认 `--suite runtime` 的行为与上文完全相同.

- 场景 (T2): ND1 空脚本, ND2 宿主能力 (`device.info`, Toast, 图像往返), ND3 回调 (电量事件 + 传感器订阅), ND4 插件在运行中被 `am force-stop` (预期每次都以错误结束, 只看宿主侧是否回收), ND5 `ui.showLayout`, ND6 Compose (不可用时脚本自报跳过), ND7a WebView + 文件选择, ND7b WebView 下载, ND7c 后台录像 (WebRTC 媒体服务), ND8 截屏 (需 `--capture on`, 与 S4a 相同的 PROJECT_MEDIA 处理), ND9 悬浮层 (宿主无悬浮窗权限时跳过), ND10 进度对话框, ND11 20 个图像句柄 + OCR 并只回收一半, ND12 录音, ND13 ColorOS 重绑 (只在 OPPO / OnePlus / realme 上运行, 其他设备跳过). Node 插件未安装, 未启用, 未授权或探测失败时整组跳过并在报告中写明. P2.1 起另有 Bun (BN) 与 QuickJS (QJ) 两组, 场景按 T2 的 X1-X4 对应: BN1 / QJ1 空脚本; BN2 宿主客户端 (`device.info`, Toast, 图像 `fromBase64` / `readPixels` / `toBytes` 往返); BN3 传感器订阅等 3 次回调 (运行期间临时打开 Bun 的 `sensors.control` 授权, 结束后还原); QJ2 64 KiB `.quickjs.mjs` 文件源 (经宿主快照) + 分块 stdout / stderr; QJ3 Promise 任务链 3 次输出回调; BN4 / QJ4 插件在运行中被 `am force-stop` (同 ND4); BN5 / QJ5 ColorOS 绑定被拒专场 (每次运行前 force-stop 插件, 同 ND13, 只在 OPPO / OnePlus / realme 上运行). F 模式在回调或输出在途时强停. 插件未安装或预热连接失败时整组跳过. P3.1 起另有 Lua (LU) 与 Python (PY) 两组, 脚本都以工作目录中的 `.lua` / `.py` 文件源运行: LU1 / PY1 空脚本; LU2 `device.info`, `storage` put / get / remove, `ui.toast` 与 `require` 模块文件 (`<入口>.modules/`, 宿主快照); PY2 `device.info`, `files.write_text` / `read_text` 8 KiB 往返与 `toast`; LU3 / PY3 三次输出回调 (两者都没有不需前台授权的宿主推送回调, 以输出回调代替; Lua 没有定时器, 以 `autojs.now()` 自旋); LU4 / PY4 插件在运行中被 `am force-stop`; LU5 / PY5 ColorOS 绑定被拒专场.
- 跟踪 (T1 (a)): 每次运行经 `EngineLifecycleCallback` 弱引用引擎与 `ScriptRuntime`, 经 `runtime/memory/BridgeExecutionObserver` 弱引用桥接会话对象 (目前为 Node 的 broker 与 provider; 生产环境没有监听器, 只是一次空判断). Bun / QuickJS 在 P2.2 接入观察者之前按类计数: 每次运行后用 VMDebug (先 GC, 只计可达实例) 数 broker / 输出回调 / 自动化桥 / 调用执行器 / provider (QuickJS 为输出回调; Lua 为 broker / 执行回调 / 远端会话 / 已取得的 provider; Python 为 broker / 执行回调 / 执行 / 绑定), 等到不高于首次运行前的值, 结束时仍高出的部分计为残留 (`metrics.sessionInstances`). 四者的输出进全局控制台, 门禁只读带本次运行标记前缀的行.
- 判定: 第一期的六项与原生, 注册表各项照旧; 另判宿主桥接注册表 (`StaticRegistries.bridgeCounts()`) 回到首次运行前 (Node / Bun / QuickJS 的连接池各可保留 1 个空闲连接; 后两者在 P2.2 登记进 `StaticRegistries` 之前经反射读 `AidlPluginHost.connectionPool`; P3.1 起另计 Lua 的 Binder 调用通道在途调用, Python 的准入队列 active / pending 与长时运行登记表), 系统侧到插件包的连接数 (经反射读 `LoadedApk.mServices`, 读不到记 `unavailable` 且不判定) 不超过开始时 + 1, 桥接线程 (`BridgeTracker.BRIDGE_THREAD_MARKERS`) 回到预热结束时. 插件进程 PSS 在首次运行前, 第 2 次计数运行后与结束时记录, 只记录不判定 (R9); Bun 另计同 UID 的 bun 子进程.
- 设置与权限: 每个 instrumentation 进程前执行 `settings put global hidden_api_policy 1` (D9, 供绑定探针反射), 结束后 (包括失败, 超时与异常路径) 恢复为原值 (原来未设置则 `settings delete global hidden_api_policy`). 宿主缺少场景所需权限时运行前 `pm grant`, 运行后 `pm revoke` (ND12 RECORD_AUDIO, D11; ND7c CAMERA, 附录 C Q4); 原本已有的权限不动. 报告头部记录设置前后的值与授予 / 收回情况. 脚本被强行结束时需手动执行 `adb -s SERIAL shell settings delete global hidden_api_policy` 并检查上述权限.
- 单元格 (第二期附录 A): `残留 / Java MB / 原生 MB / 脚本线程增量 b桥接线程增量 / h宿主注册表增量 s系统连接增量 / 存活引擎 / 插件 PSS MB (第 2 次计数运行后至结束的变化)`. 报告另有 "Bridge detail" 表 (注册表明细, 绑定前后, 新增线程, 三个时点的 PSS, 脚本 `gateStat` 最大值, Bun / QuickJS 的会话对象残留与运行后回到起点的最长等待). 残留时 shark 分析 broker / provider / `NodePluginJavaScriptEngine` / 已退出的 `ScriptRuntime` 的最短强引用链.
- `--dump-after-last` (ROADMAP C31): 每个场景最后一次运行后, 在门禁等待回收之前再转储一次堆, 报告 `heapAfterLast` 记录 broker / provider / 引擎 / `ScriptRuntime` 的实例数与引用链, 以及整份转储按类的浅大小前 15 名 (含 zygote 与 image 堆), Markdown 另起一节. 用于区分 "插件进程尚未回收代理而暂时钉住" 与真正的宿主侧增长; 配合 `--hprof` 时这份转储同样拉回 `<out>/hprof`.
- `LooperHelper.loopers` 未回到预热结束时的值时, JSON 的 `metrics.looperThreads` 列出预热结束与结束时各条目的线程名与状态, 用于定位是哪个线程留下了 Looper.
- 构建与安装同上 (宿主与测试 APK 来自同一次构建); 插件另行安装, 版本以设备上的 versionCode 为准 (Bun / QuickJS / Lua / Python 按第二期 T5 从插件仓库本地构建 debug 包, 真机 arm64-v8a, AVD x86_64; Lua 的 debug 包须用官方签名重签, 否则宿主不授权).

## ci_gate.py: CI 门禁 (模拟器)

```
py -3.12 -I -u tools/memory/ci_gate.py -s SERIAL [--adb ADB] [--suite runtime|bridge] [--plugin APK]... [--scenarios S1,S2] [--allow-skip S7b,S13] [--label ci] [--out build/memory-gate/evidence] [--work build/memory-gate] [--relaunch "EMULATOR 命令"] [--restart-adb-server] [--no-retry]
```

`.github/workflows/memory-gate.yml` (ROADMAP P6.1, `workflow_dispatch` 与推送 `v*` 标签时运行) 只调用本脚本; 手动运行时输入 `suite` 选 runtime (默认, S1-S12) 或 bridge (第二期插件引擎桥接门禁, docs/memory-phase2 P5.1), 推送标签只跑 runtime; 本机复现用同款 AVD (API 35, google_apis, x86_64, pixel_7, 内存 4096M), 步骤与 CI 相同. 本脚本会安装 APK 并修改设置, 所以只接受模拟器, 真机仍走 run_gate.py.

1. 等待框架就绪: 开机完成, 开机动画停止, activity 与 package 服务应答, 并稳定 10 s.
2. 准备: 按设备 ABI 从 `--apk-dir` (默认 `app/build/outputs/apk`, 依据同一次构建的 output-metadata.json) 选 debug 宿主, 以 `-t -g` 安装 (授予运行时权限), 再装测试 APK 与各个 `--plugin` (S4b / S4d / S4e 需要官方 OpenCV 插件, 宿主 APK 不含 OpenCV; S12 需要 3-ImGui; CI 从两个插件仓库的 Release 下载 x86_64 包并校验 sha256, 官方插件凭签名证书受信且默认启用, 装上即可); bridge 套件需要五个运行时插件 (Node / Bun / QuickJS / Lua / Python) 与 3-Block Compose (ND6), CI 从各插件仓库的 Release 下载 (Bun 无 x86_64 分包, 用 universal 包; Lua 只有预发布版; 3-Block Compose 是单一 APK), tag 与 sha256 固定在 workflow 的 env 里; 必须通过的场景所需插件未安装时直接报错退出, 不进入运行; 核对已安装的 versionCode (bridge 还在 `CI_GATE_PREPARED` 行列出各插件的 versionCode); 解锁, 关闭动画, 屏幕常亮, `appops set org.autojs.autojs6 SYSTEM_ALERT_WINDOW allow` (否则 S6 / ND9 跳过).
3. 以 `--suite SUITE --mode normal --capture on --wake --hprof` 运行 run_gate.py (S4a / S4f / ND8 的截屏授权按上文用 PROJECT_MEDIA app-op 放行), logcat 全程写入 `<work>/logcat.txt`, 输出转录写入 `<work>/run-gate-attempt-N.txt`. 运行中每 30 s 探测一次 adb (超时 15 s), 连续 3 次无应答判定 adb 丢失并停止 run_gate.py (参照 3-Stove Agent 的 run_main_suite.py).
4. 只有出现 "System has crashed", system_server 的 pid 变化或 adb 丢失时才重跑一次: adb 丢失时先重启 adb server (`--restart-adb-server`), 仍无应答再用 `--relaunch` 的命令重启模拟器; 重新准备后只重跑未通过的场景, 用 `--merge` 并入同一份报告, 第一次的报告复制到 `<work>/attempt-1`.
5. 判定: 选中的场景 (默认 S1-S12, 强制停止模式不进 CI) 全部通过, 或因模拟器无法避免的原因跳过 (`--allow-skip`, 默认 S7b / S12 / S13: S7b 需维护者重新开启无障碍服务, 见上文; S12 未装 3-ImGui; S13 无语音插件; CI 装了 3-ImGui, 传 `--allow-skip S7b,S13`, S12 必须通过; bridge 默认 ND13 / BN5 / QJ5 / LU5 / PY5 / ND6: 前五个只在 ColorOS (OPD2413) 上运行, ND6 未装 3-Block Compose; CI 装了 3-Block Compose, 传 `--allow-skip ND13,BN5,QJ5,LU5,PY5`, ND6 必须通过). 其他跳过, 失败或缺报告都判 FAIL, 以免权限丢失后静默通过. S7 不带无障碍服务运行, 按键与无障碍事件部分记录在报告中.

输出 `CI_GATE_RESULT PASS|FAIL passed=N skipped=N (...) problems=N attempts=N versionCode=N` 与逐条 `CI_GATE_PROBLEM`; 在 GitHub Actions 中还把结果与报告表写入 job summary. 退出码: PASS 0, FAIL 1, 准备或运行出错 2 (`CI_GATE_ERROR`). `--restart-adb-server` 会断开本机所有设备, 只在 CI 上使用. 本机复现时把报告写进证据目录, 临时文件放在仓库外:

```
py -3.12 -I -u tools/memory/ci_gate.py -s emulator-5610 --adb ADB --plugin OPENCV_X86_64_APK --plugin IMGUI_X86_64_APK --allow-skip S7b,S13 --label ci-local --out docs/memory/evidence --work WORK_DIR --relaunch "EMULATOR -port 5610 -avd AutoJs6_Memory_Gate_CI_35 -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim"
```

bridge 套件的本机复现 (默认 label `mp2-ci`, 本机用 `mp2-ci-local`; 插件 APK 用 workflow env 里固定的 Release 包):

```
py -3.12 -I -u tools/memory/ci_gate.py -s emulator-5654 --adb ADB --suite bridge --plugin NODEJS_APK --plugin BUN_APK --plugin QUICKJS_APK --plugin LUA_APK --plugin PYTHON_APK --plugin BLOCK_COMPOSE_APK --allow-skip ND13,BN5,QJ5,LU5,PY5 --label mp2-ci-local --out docs/memory-phase2/evidence --work WORK_DIR --relaunch "EMULATOR -port 5654 -avd AVD_NAME -no-window -gpu swiftshader_indirect -no-snapshot -noaudio -no-boot-anim"
```

纯函数 (APK 选择, 重跑范围, 判定, bridge 所需插件) 的单测在 `test_ci_gate.py`, 同时核对 workflow 为每个所需插件固定了 tag 与 sha256, 且两个套件的 `--allow-skip` 不超出本脚本的默认值.

## leakcanary_probe.py: LeakCanary 报告核对

```
py -3.12 -I -u tools/memory/leakcanary_probe.py -s SERIAL [--scenario S2] [--runs 6] [--wait 300] [--expect Loopers] [--expect-leaks yes|no] [--out FILE]
```

LeakCanary 在 instrumentation 测试进程里不转储堆, 所以门禁看不到它的报告 (ROADMAP P1.2 / T5). 本脚本把场景脚本 (门禁正常结束模式的版本, 目前 S1 / S2) 推到 `/sdcard/Download/aj6-memory-probe/`, 经 `RunIntentActivity` 以普通脚本运行 `--runs` 次, 然后在 `--wait` 秒内从 logcat (`LeakCanary` 标签) 收集 LeakCanary 的堆分析结果. 输出: 每次启动一行, LeakCanary 记录的监视对象与最大残留数, 是否出现分析, 应用泄漏数, 引用链是否包含 `--expect`; `--out` 把分析文本 (与 LeakCanary 泄漏列表中显示的文本相同) 写入文件. `--expect-leaks yes` 时出现含 `--expect` 的应用泄漏即 PASS (修前构建), `no` 时等待期内没有应用泄漏即 PASS (修后构建). 需要 debug 构建且开发者选项中的 LeakCanary 开关为开 (默认). LeakCanary 自身的 "Dump heap automatically" (Leaks 应用 > About) 自 P1.2 起默认关闭 (维护者要求, 避免调试时自动转储造成短暂冻结), 此时 LeakCanary 只计数残留对象并发通知, 不做分析; `--expect-leaks yes` 需要分析时先在该页面打开它 (或点通知中的 "Dump Heap Now"), 否则脚本以退出码 2 报 INCONCLUSIVE. 脚本不点屏幕, 不改设置.

## device_diagnostics_page.py: 内存诊断页面截图与未回收警告核对

```
py -3.12 -I -u tools/memory/device_diagnostics_page.py -s SERIAL [--shots developer,page,actions] [--out DIR]
py -3.12 -I -u tools/memory/device_diagnostics_page.py -s SERIAL --warning-check [--pin 60] [--quiet 15] [--out DIR]
```

ROADMAP P2.2 / P2.3. 开发者选项与内存诊断页面都未导出, 与 `tools/augment/device_developer_options.py` 一样经 `RunIntentActivity` 运行一个小脚本在宿主进程内打开页面, 滚动也在进程内完成 (`scrollToPreference` / `scrollToPosition`), 不点屏幕, 不滑动. `--shots`: `developer` 打开开发者选项并滚到 "内存诊断" 类别, `page` 打开内存诊断页面, `actions` 滚到该页末尾 (未回收列表与操作). 截图存 `--out` (默认 `tools/memory/__pycache__/`, 不入库), 人工核对后把结论写进路线图.

`--warning-check` (P2.3): 先单独运行脚本 B (自行结束), 之后 `--quiet` 秒内不应出现警告 (对照); 再由脚本 A 启动 B 并持有 `engines.all()` 返回的引擎对象 `--pin` 秒, B 的 runtime 因此无法回收, B 结束约 10 s 后宿主应输出警告 (从 logcat 的 `MemoryDiagnostics` 标签读取 id, 结束方式与已过时长, 并核对全局控制台的 W 行); A 持有期间对页面截图一次; A 结束后探针脚本调用 `MemoryDiagnostics.collect()`, 列表中不应再有 A / B. 需要开发者选项 "脚本结束后未回收时在控制台警告" 为开 (默认). 屏幕须点亮并解锁 (脚本只检查不操作). 输出每项一行 PASS / FAIL 与关键数值.

## device_memory_api.py: 脚本 API memory 模块真机核对

```
py -3.12 -I -u tools/memory/device_memory_api.py -s SERIAL [--keep] [--wait 120]
py -3.12 -I -u -X utf8 tools/memory/device_memory_api.py -s SERIAL --sample [PATH]
```

ROADMAP P5.1. 经 `RunIntentActivity` 在已安装的 debug 宿主中运行一个探针脚本: `memory.snapshot()` 的字段与类型 (T9) 及单次耗时; 第二个脚本把自身 `runtime` 存进静态 Java 表 (System properties) 后结束, `memory.collect(5000)` 后 `memory.retained()` 应列出它 (S2 修前的类比), 探针移除引用后 `memory.collect()` 应回收它 (修后的类比); `memory.dumpHeap()` 写出 hprof 并核对大小后删除 (debug 构建始终允许); `memory.watch()` 的返回值; 各参数错误能以脚本错误抛出. 探针把结果写到设备上的 `result.json` 而不是 logcat (参数错误会打印堆栈, ColorOS 会丢弃超出配额的日志行). 推送的文件默认在结束后删除. 屏幕须点亮, 脚本不点屏幕, 不改设置. 输出每项一行 PASS / FAIL 与关键数值.

`--sample` (P5.2): 改为运行样例 "工具/内存诊断" (默认取 `app/src/main/assets-app/sample` 中的文件; 宿主须已含 memory 模块, debug 与 release 构建均可), 从 logcat 的 `GlobalConsole` 标签读取样例的输出, 核对回收前后两行读数与未回收列表都已打印, 并原样列出这几行. 样例最后打开控制台页面.

## ui_leak_walk.py: LeakCanary 主界面巡检

```
py -3.12 -I -u tools/memory/ui_leak_walk.py -s SERIAL [--pages drawer,explorer,...] [--reps 3] [--tag TAG] [--shots] [--analysis-wait 300] [--workdir /sdcard/Scripts] [--docs-plugin on|as-is] [--keep-dump-setting] [--list]
```

ROADMAP P4.5, 不走门禁. 经 adb 驱动宿主主界面 (维护者 2026-10-21 授权): 可导出的入口用 `am start`, 其余用 `uiautomator dump` 定位控件后 `input tap / keyevent / swipe`; 控件文字按 `app/src/main/res/values*/strings.xml` 的全部译文匹配, 与应用语言无关. `--list` 列出页面: 抽屉, 文件页 (进出 `<workdir>/aj6-ui-walk` 及其子目录, 行菜单), 打开文件, 编辑器 + 函数面板 + 手册对话框 + 文档面板, 文档页, 文档 Activity, 插件页 (详情对话框, 插件设置页), 插件中心, 日志页, 任务页 (运行一个 4 s 脚本, H25), 设置页及其各子页 (主题色, 快捷方式备份, 管理 AutoJs6 设置, 回收站保留策略, 编辑器全部设置, 代码生成模型, 发行历史, 关于, 开发者选项, 内存诊断). 每页进出 `--reps` 次后, 用 `run-as <包名> kill -USR1 <pid>` 让 ART 做两次 GC, 再按 LeakCanary 的日志 (`LeakCanary` 标签) 统计该页被监视的对象 (已销毁的 Activity / Fragment / View / 根视图 / Service 与已结束脚本的 runtime) 与残留数; 有残留时把应用切到后台, 让 LeakCanary 转储并分析堆, 分析结果计入该页.

LeakCanary 自身的 "Dump heap automatically" 默认关闭 (P1.2), 脚本在巡检期间经 `run-as` 改应用的 SharedPreferences 打开它 (先停止应用), 结束时恢复原值 (`--keep-dump-setting` 不恢复); 离线文档插件在插件中心关闭时同样临时打开 (`--docs-plugin as-is` 不动), 使文档页与编辑器文档面板加载页面. 开发者选项中的 LeakCanary 开关须为开 (默认), 脚本按 `LeakCanarySetup` 的启动日志核对. 需要人工确认的系统对话框 (权限申请等) 不代点: 其他应用的窗口到前台时脚本打印要确认的内容并等待维护者处理. 屏幕须点亮并解锁.

输出: 每页一行 PASS / LEAK / FAIL, 监视与残留的对象数, 每条泄漏一行摘要 (泄漏对象的类, 第一个可疑引用, 签名前 8 位). 完整分析文本 (与 LeakCanary 泄漏列表中显示的文本相同) 与每页的错误写入 `docs/memory/evidence/ui-walk-<机型>-<versionCode>[-<tag>]-<日期>.md`; `--shots` 另把每页结束时的截图与最后 LeakCanary 泄漏列表的截图 (`leakcanary-leaks.png`, 每组泄漏显示最近一次出现的时间; 宽 540) 存到同名目录. 退出码: 全部 PASS 为 0, 有 LEAK 或 FAIL 为 2.

## plugin_center_heap_check.py: 插件标签页 Java 堆核对

```
py -3.12 -I -u tools/memory/plugin_center_heap_check.py -s SERIAL [--apk FILE] [--seconds 60] [--interval 2] [--max-growth-mb 64] [--max-tail-growth-mb 16] [--tab-index 2]
```

不走门禁 (2026-10-22 API 24 AVD 上打开插件标签页后 Java 堆数秒内涨到 512 MB 上限的复测). 原因: 插件中心按 Resources 解码的原尺寸保留已安装插件图标, res/mipmap 中 432 px 的 PNG 在 560 dpi 上放大为 1512 px (9 MB), 每个插件日夜各一份, 快照与完整发现又各一份, Android 8 之前位图像素计入 Java 堆; 修复见 `PluginInstalledIconResolver` / `PluginIconRasterPolicy`. 脚本停止宿主 (`--apk` 时先安装), 从启动图标别名启动, 记录主页的 Dalvik 堆, 点插件标签 (按 `text_plugins` 的全部译文在 `uiautomator dump` 中查找; API 24 主页始终不空闲, uiautomator 读不到窗口时改从 `dumpsys activity top` 取第 `--tab-index` 个标签), 之后每 `--interval` 秒读一次 `dumpsys meminfo`, 共 `--seconds` 秒. 只点这一个标签, 不改设置. 输出每项一行 PASS / FAIL: 进程存活 (pid 不变), 日志中无 OutOfMemoryError, 无 ANR, 打开后堆增长不超过 `--max-growth-mb`, 窗口最后三分之一内增长不超过 `--max-tail-growth-mb`. 全部 PASS 时退出码为 0.

## inrt_smoke.py: 打包应用 (inrt) 冒烟

```
py -3.12 -I -u tools/memory/inrt_smoke.py -s SERIAL [--template app/build/outputs/apk/inrt/debug/inrt-v6.8.0-universal.apk] [--signing-properties sign.properties] [--sdk ANDROID_HOME] [--jdk JAVA_HOME] [--runs 10] [--timeout 120] [--settle 3] [--pss-tolerance-mb 4] [--thread-tolerance 2] [--label LABEL]
```

ROADMAP P6.3, 不走门禁. 先 `./gradlew :app:assembleInrtDebug` 生成 inrt 模板. 脚本按 `tools/sdk37/build_inrt_storage_fixtures.py` 的打包方式, 把 S2 (`threads.start` x 10 + `threads.pool`) 与 S5 (`ui.layout` 含按钮, 文本与 webview, 随后 `activity.finish()`) 的正常结束脚本合成一个 inrt 项目, 只替换模板的 `assets/project`, 对齐后用宿主自己的签名 (`sign.properties`, 密码经环境变量传给 apksigner) 签名, inrt 代码不改. 设备上已有的 inrt 应用先拉回本机, 结束后装回 (原本没有则卸载冒烟包). 每次运行: 强制停止, `am start -W` 启动, 等脚本的 exit 监听写入结束标记 (应用私有目录, `run-as` 读取), 再等 `--settle` 秒, 读进程的 `dumpsys meminfo` TOTAL PSS 与 `/proc/<pid>/status` 的线程数, 然后强制停止. 不点屏幕, 不改设置.

判定: 每次都按时结束且读到数值; PSS 与线程数都不单调增长, 且 Theil-Sen 趋势 (两两斜率的中位数乘以次数减 1) 不超过 `--pss-tolerance-mb` / `--thread-tolerance`. 不用 "最后 3 次与最初 3 次的中位数之差": 新进程的 PSS 落在相距约 8 MB 的两个水平之一 (23046RP50C, 2026-10-22), 两端各 3 次就可能差一整级而并无趋势. 输出每次一行 (PSS, 线程) 与最终判定, 报告写入 `docs/memory/evidence/inrt-smoke-<机型>-<模板 versionCode>[-<label>]-<日期>.md` 与同名 `.json`. 退出码: PASS 为 0, FAIL 为 2.

## static_audit.py: 源码静态审计

```
py -3.12 -I tools/memory/static_audit.py [--out docs/memory/evidence] [--json]
```

扫描 `app/src/main/java`, 输出 `static-audit-<yyyyMMdd>.md`: (a) 进程级字段 (Java `static`, Kotlin `object` / `companion object` / 顶层属性) 中类型为 ScriptRuntime / ScriptEngine / Scriptable / Rhino Context / Activity / View / Fragment / Dialog 的清单 (Android Context 单列备查); (c) 脚本相关包内或元素为线程 / Looper / 监听器 / 窗口 / 图像 / 引擎 / 类加载器的进程级注册表 (附录 B.1 候选); (b) `registerReceiver` / `bindService` / `.subscribe(` / 创建 WebView / `addIdleHandler` / `Looper.prepare` / `ThreadLocal` 的文件及同文件内对应释放调用的次数. 这是文本扫描 (先抹掉注释与字符串), 归属列按包路径猜测, 结论须人工复核后再写进附录 B; 也是 ROADMAP T8 源码守卫白名单的来源.

### Q11 探针: 标准对象在脚本线程上首次创建 (ROADMAP 附录 C Q11, P4.4)

```
adb -s SERIAL shell am instrument -w -r -e class org.autojs.autojs.memory.RhinoStandardObjectsDeviceTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

`RhinoStandardObjectsDeviceTest` 在一个尚未创建 `RhinoUtils.standardObjects` 的进程中, 先由一个脚本在其线程上首次读取它, 再连续运行 3 个脚本 (observe polyfill, `util.inspect`, JSON, Promise, `util.versionCodes`), 任一失败即不通过; 进程里已有代码先创建了标准对象时跳过 (该情形无法再现). 每次单独起一个 instrumentation 进程运行, 运行前 `am force-stop org.autojs.autojs6`.

### 源码守卫白名单 (ROADMAP T8 / P4.3)

```
py -3.12 -I tools/memory/static_audit.py --guard [--check]
```

`--guard` 不写报告, 改为更新 `app/src/test/resources/memory/static-reference-whitelist.txt`: 每行 `kind | 路径 | 名称 | 明细 | 理由 | 说明`, 前四列由脚本生成, 后两列手写. 生成的行有两类: `field` 为 (a) 中除弱引用以外的字段 (含 Android Context) 与 (c) 的注册表, `call` 为 (b) 中调用 `registerReceiver` / `bindService` / `addIdleHandler` / `Looper.prepare` 或创建 WebView 的文件; `manual` 行是附录 B.1 中扫描看不到的项 (进程级单例的实例字段, 事件总线), 手写, 脚本原样保留. 重新生成时已有键的理由与说明保留, 已消失的键删除, 新键的理由写成 `TODO`. `--check` 不写文件, 白名单过期时退出码 1. 理由的取值与含义在文件头部.

守卫测试 `app/src/test/java/org/autojs/autojs/memory/StaticReferenceGuardTest.kt` 属于全量 `:app:testAppDebugUnitTest` (CI 也跑), 用同目录的 `StaticReferenceScanner.kt` (本脚本规则的 Kotlin 移植) 直接扫描源码, 不依赖 Python: 新增进程级静态引用或注册调用而白名单没有对应行, 白名单行已不对应任何引用, 理由无效或仍为 `TODO`, `manual` 行指向的声明已不存在, 都会失败并给出文件, 类名, 字段名与登记方法; 另有一条用例核对白名单的生成列与扫描器的输出逐行相同, 证明两份实现的规则一致. 改了规则就两边一起改, 然后重新生成. 登记流程: 运行 `--guard`, 把新行的 `TODO` 换成理由与说明 (持有什么, 谁释放); 能改成弱引用或按 runtime / owner id 清理 (ROADMAP T7) 的优先改代码.
