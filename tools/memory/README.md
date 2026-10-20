# 内存门禁工具

配合 `docs/memory/ROADMAP.md` 使用. 门禁本体是仪器测试 `app/src/androidTest/java/org/autojs/autojs/memory/RuntimeLeakGateDeviceTest.kt` (场景脚本在同目录 `scenarios/GateScenarios.kt`), 本目录是驱动与审计脚本. 脚本不安装 APK, 不点屏幕, 不改设置; 需要人工确认的对话框 (S4a 的截屏授权) 由维护者在手机上操作.

## run_gate.py: 跑门禁并生成证据

```
py -3.12 -I tools/memory/run_gate.py -s SERIAL [--scenarios S1,S2] [--mode normal|force|both] [--hprof] [--label baseline] [--out docs/memory/evidence]
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

单元格 (ROADMAP 附录 A): `残留 / Java 堆增量 MB / 原生堆增量 MB / 脚本线程增量 / Rhino Context 增量 / 存活引擎数`. 阈值 (T1): 残留 0, Java <= 2 MB, 原生 <= 4 MB (截屏场景再加一帧), 线程与 Context 回到开始前, 引擎回到开始前.

其他参数: `--capture on` 运行 S4a (每次运行都会请求截屏授权, 需维护者确认授权方式后再开), `--wake` 每个场景前发送 `KEYCODE_WAKEUP` (只点亮屏幕, 不点击), `--timeout` 单个场景与模式的上限秒数 (默认 1500).

场景表 (T2): S1 空脚本, S2 `threads.start` x 10 + `threads.pool`, S3 定时器嵌套, S4a 截屏 (需授权), S4b `images.read` + `findImage`, S4c `copy` / `clip` / `resize`, S4d `matchTemplate` + `oneShot`, S4e 多线程图像 (#372 / #200 的有限版本), S5 UI 脚本 + webview (经 `ScriptExecuteActivity`, 等 `onDestroy`), S6 `floaty.window` + `floaty.rawWindow`, S7 事件 / 按键 / 传感器 / 无障碍事件 (按键与无障碍部分需宿主无障碍服务, 未开启时只跑其余部分并在报告中记录), S8 `JavaAdapter` + `runtime.loadDex`, S9 `engines.execScriptFile` 自我重启 3 层, S10 `http.get` + WebSocket (测试进程内的回环服务, 不需要网络), S11 `device.*` 读取 / 振动 / `keepScreenOn`, S12 `plugins.load` (3-ImGui, 未安装时跳过).

度量目前是测试内的临时实现 (`GateMetrics.kt`; Rhino Context 计数经反射读取 `AndroidContextFactory.mContextCount`), ROADMAP P2.1 的 `MemoryDiagnostics` 落地后切换.

## static_audit.py: 源码静态审计

```
py -3.12 -I tools/memory/static_audit.py [--out docs/memory/evidence] [--json]
```

扫描 `app/src/main/java`, 输出 `static-audit-<yyyyMMdd>.md`: (a) 进程级字段 (Java `static`, Kotlin `object` / `companion object` / 顶层属性) 中类型为 ScriptRuntime / ScriptEngine / Scriptable / Rhino Context / Activity / View / Fragment / Dialog 的清单 (Android Context 单列备查); (c) 脚本相关包内或元素为线程 / Looper / 监听器 / 窗口 / 图像 / 引擎 / 类加载器的进程级注册表 (附录 B.1 候选); (b) `registerReceiver` / `bindService` / `.subscribe(` / 创建 WebView / `addIdleHandler` / `Looper.prepare` / `ThreadLocal` 的文件及同文件内对应释放调用的次数. 这是文本扫描 (先抹掉注释与字符串), 归属列按包路径猜测, 结论须人工复核后再写进附录 B; 也是 ROADMAP T8 源码守卫白名单的来源.
