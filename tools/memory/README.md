# 内存门禁工具

配合 `docs/memory/ROADMAP.md` 使用. 门禁本体是仪器测试 `app/src/androidTest/java/org/autojs/autojs/memory/RuntimeLeakGateDeviceTest.kt` (场景脚本在同目录 `scenarios/GateScenarios.kt`), 本目录是驱动与审计脚本. 脚本不安装 APK, 不点屏幕, 不改设置; 需要人工确认的对话框 (S4a 的截屏授权) 由维护者在手机上操作.

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

单元格 (ROADMAP 附录 A): `残留 / Java 堆增量 MB / 原生堆增量 MB / 脚本线程增量 / Rhino Context 增量 / 存活引擎数`. 阈值 (T1): 残留 0, Java <= 2 MB, 原生 <= 4 MB (截屏场景再加一帧), 线程与 Context 回到开始前, 引擎回到开始前; P3.1 起另有 (g): 未回收的图像 (`ImageWrapper.liveCount`) 与未关闭的 media Image (`ImageWrapper.openMediaImageCount`) 回到开始前, 存活 Mat (`core/opencv/Mat.getLiveCount`) 回到预热结束时. P3.4 起, 宿主无障碍服务运行时的 S7 与 S7b 另判 AccessibilityNodeInfo 的可达实例数 (VMDebug) 相对预热结束时不超过 +50 (框架的 AccessibilityCache 会随前台窗口重新填充, 节点泄漏则每次运行增长数百个; ROADMAP 附录 C Q33).

原生明细 (ROADMAP P3.1, 报告的 "Native detail" 表与终端行尾的 `peak= images= media= mats=`):

- Peak: 每次运行期间每 20 ms 采样 `Debug.getNativeHeapAllocatedSize`, 取高于运行开始时的最大值 (在运行后的强制回收之前, 能看到只等 finalizer 释放的原生内存, issue #372 的形态); 表中为计数运行中的最大值, 只作观察, 不参与判定.
- Meminfo: `--meminfo phases` (默认) 在场景开始, 预热结束与场景结束时取本进程的 `dumpsys meminfo <pid>` (经 UiAutomation 的 shell 身份在测试进程内执行, 时间点与六项度量的采样一致, 比驱动脚本在进程外取更准), 记录 Native Heap 的 Pss / Heap Alloc, App Summary 的 Graphics, Unknown 的 Pss 等, 表中为预热结束到场景结束的增量; `--meminfo runs` 每次运行后都取 (每次约 0.3-1 s, 用于定位原生台阶出现在哪一次); `off` 不取. 需要宿主无障碍服务的 S7 / S7b 不取 (不连接 UiAutomation, 以免影响服务绑定).
- Counters / Instances: 宿主计数器每次运行后都读; `dalvik.system.VMDebug` 的实例计数 (Bitmap, `org.opencv.core.Mat`, AccessibilityNodeInfo 等, 会先触发一次 GC) 在开始, 预热结束与结束时读, 隐藏 API 不可用时记 `-`.
- Script stats: 场景脚本经 `gateStat(name, value)` 报告的数值 (S4f 的帧数, 空帧数, 最慢一次截图耗时, 截图后最多同时打开的 media Image 数; S7b 的节点数), 表中为计数运行中的最大值.

其他参数: `--capture on` 运行 S4a 与 S4f, `--wake` 每个场景前发送 `KEYCODE_WAKEUP` (只点亮屏幕, 不点击), `--timeout` 单个场景与模式的上限秒数 (默认 1500), `--merge` 只替换同名报告中本次重跑的场景与模式 (补跑被打断的部分).

S4a / S4f 的截屏授权 (ROADMAP 附录 C Q3, 维护者 2026-10-20 拍板方案 (a)): 每次运行都是新的 runtime, 脚本结束时会释放 MediaProjection, 因此每次都会重新申请授权. `--capture on` 时脚本在每个 S4a / S4f 进程开始前执行 `adb shell appops set org.autojs.autojs6 PROJECT_MEDIA allow`, 系统据此跳过授权框; 该进程结束后 (包括失败, 超时与异常路径) 立即执行 `appops set ... PROJECT_MEDIA default` 恢复. 报告头部记录设置前的模式与恢复后的模式. 脚本被强行结束 (例如直接杀掉 Python 进程) 时来不及恢复, 需手动执行 `adb -s SERIAL shell appops set org.autojs.autojs6 PROJECT_MEDIA default`.

S7 的无障碍部分与 S7b (ROADMAP 附录 C Q9): instrumentation 在启动和结束时都会杀掉宿主进程, 系统随即把宿主无障碍服务列入 "Crashed services" 并不再绑定, 测试进程里因此拿不到服务 (设置里仍显示开启). 做法:

```
py -3.12 -I -u tools/memory/run_gate.py -s SERIAL --scenarios S7,S7b --keep-process --one-process --a11y-wait 600 --timeout 3600 --merge --label <名>
```

`--one-process` 让所选的全部场景与模式在同一个进程里跑 (服务只需重新开启一次), `--keep-process` 跳过场景前后的 `am force-stop` (它会把服务从已开启列表中移除), `--a11y-wait` 让 S7 / S7b 最长等待该秒数 (0-900, 测试端的上限) 并在手机上反复弹出 Toast; 看到提示后由维护者在系统无障碍设置里把 AutoJs6 关掉再开启一次, 服务即绑定到正在运行的测试进程. 脚本不代点, 也不改设置; 跑完后服务仍会落回 "Crashed" 状态, 维护者可直接关回. 注意: S7 开始运行后, 场景脚本里的 `auto.registerEvent` 会调用 `ensureService()`, 宿主有安全设置 (或 root / Shizuku) 权限时会自动重新启用无障碍服务, 所以 S7 运行期间手动关闭的服务会立即被打开 (2026-10-21 维护者在 XQ-AT72 上观察到); 要关回请等 S7 结束.

设备占用防护 (ROADMAP R6): 每个场景开始前若设备上已有其他 `am instrument` 在运行就等待 (最长 10 分钟); 运行中宿主或测试 APK 被替换则停止并把该场景标为 `invalid`; 设备离线 (adb 断开) 时停止, 已读到的报告保留.

场景表 (T2): S1 空脚本, S2 `threads.start` x 10 + `threads.pool`, S3 定时器嵌套, S4a 截屏 (需授权), S4b `images.read` + `findImage`, S4c `copy` / `clip` / `resize`, S4d `matchTemplate` + `oneShot`, S4e 多线程图像 (#372 / #200 的有限版本), S4f 连续 200 次 `captureScreen` 不显式回收 (issue #204 的 "卡住不返回"; 每个进程固定预热 1 + 计数 3, 单次截图超过 15 s 或截图后打开的 media Image 超过 1 个即脚本失败; 需 `--capture on`), S5 UI 脚本 + webview (经 `ScriptExecuteActivity`, 等 `onDestroy`), S6 `floaty.window` + `floaty.rawWindow`, S7 事件 / 按键 / 传感器 / 无障碍事件 (按键与无障碍部分需宿主无障碍服务, 未开启时只跑其余部分并在报告中记录), S7b `className(...).find()` / `findOnce` / `children` / `parent` 循环 (需宿主无障碍服务, 未绑定时跳过), S8 `JavaAdapter` + `runtime.loadDex`, S9 `engines.execScriptFile` 自我重启 3 层, S10 `http.get` + WebSocket (测试进程内的回环服务, 不需要网络), S11 `device.*` 读取 / 振动 / `keepScreenOn`, S12 `plugins.load` (3-ImGui, 未安装时跳过).

度量自 P2.1 起全部取自宿主的 `org.autojs.autojs.runtime.memory.MemoryDiagnostics` (`GateMetrics.kt` 只做转接; Rhino Context 计数为 `AndroidContextFactory.activeContextCount`, 图像数为 `ImageWrapper.liveCount`), 与开发者选项 "内存诊断" 页面同源 (ROADMAP T3). 脚本线程按线程名标记计数 (`ScriptThread-` / `Spawn-` / `Pool-` / `Timer` / `ScreenCapturer` / `LooperThread`), Android 14 起框架自带的 `SurfaceSyncGroupTimer` 不计 (P1 起).

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

## static_audit.py: 源码静态审计

```
py -3.12 -I tools/memory/static_audit.py [--out docs/memory/evidence] [--json]
```

扫描 `app/src/main/java`, 输出 `static-audit-<yyyyMMdd>.md`: (a) 进程级字段 (Java `static`, Kotlin `object` / `companion object` / 顶层属性) 中类型为 ScriptRuntime / ScriptEngine / Scriptable / Rhino Context / Activity / View / Fragment / Dialog 的清单 (Android Context 单列备查); (c) 脚本相关包内或元素为线程 / Looper / 监听器 / 窗口 / 图像 / 引擎 / 类加载器的进程级注册表 (附录 B.1 候选); (b) `registerReceiver` / `bindService` / `.subscribe(` / 创建 WebView / `addIdleHandler` / `Looper.prepare` / `ThreadLocal` 的文件及同文件内对应释放调用的次数. 这是文本扫描 (先抹掉注释与字符串), 归属列按包路径猜测, 结论须人工复核后再写进附录 B; 也是 ROADMAP T8 源码守卫白名单的来源.
