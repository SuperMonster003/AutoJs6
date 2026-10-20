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

其他参数: `--capture on` 运行 S4a, `--wake` 每个场景前发送 `KEYCODE_WAKEUP` (只点亮屏幕, 不点击), `--timeout` 单个场景与模式的上限秒数 (默认 1500), `--merge` 只替换同名报告中本次重跑的场景与模式 (补跑被打断的部分).

S4a 的截屏授权 (ROADMAP 附录 C Q3, 维护者 2026-10-20 拍板方案 (a)): 每次运行都是新的 runtime, 脚本结束时会释放 MediaProjection, 因此每次都会重新申请授权. `--capture on` 时脚本在每个 S4a 进程开始前执行 `adb shell appops set org.autojs.autojs6 PROJECT_MEDIA allow`, 系统据此跳过授权框; 该进程结束后 (包括失败, 超时与异常路径) 立即执行 `appops set ... PROJECT_MEDIA default` 恢复. 报告头部记录设置前的模式与恢复后的模式. 脚本被强行结束 (例如直接杀掉 Python 进程) 时来不及恢复, 需手动执行 `adb -s SERIAL shell appops set org.autojs.autojs6 PROJECT_MEDIA default`.

S7 的无障碍部分 (ROADMAP 附录 C Q9): instrumentation 在启动和结束时都会杀掉宿主进程, 系统随即把宿主无障碍服务列入 "Crashed services" 并不再绑定, 测试进程里因此拿不到服务 (设置里仍显示开启). 做法:

```
py -3.12 -I -u tools/memory/run_gate.py -s SERIAL --scenarios S7 --keep-process --one-process --a11y-wait 600 --timeout 2400 --merge --label <名>
```

`--one-process` 让两种模式在同一个进程里跑, `--keep-process` 跳过场景前后的 `am force-stop` (它会把服务从已开启列表中移除), `--a11y-wait` 让 S7 最长等待该秒数并在手机上反复弹出 Toast; 看到提示后由维护者在系统无障碍设置里把 AutoJs6 关掉再开启一次, 服务即绑定到正在运行的测试进程. 脚本不代点, 也不改设置; 跑完后服务仍会落回 "Crashed" 状态, 维护者可直接关回.

设备占用防护 (ROADMAP R6): 每个场景开始前若设备上已有其他 `am instrument` 在运行就等待 (最长 10 分钟); 运行中宿主或测试 APK 被替换则停止并把该场景标为 `invalid`; 设备离线 (adb 断开) 时停止, 已读到的报告保留.

场景表 (T2): S1 空脚本, S2 `threads.start` x 10 + `threads.pool`, S3 定时器嵌套, S4a 截屏 (需授权), S4b `images.read` + `findImage`, S4c `copy` / `clip` / `resize`, S4d `matchTemplate` + `oneShot`, S4e 多线程图像 (#372 / #200 的有限版本), S5 UI 脚本 + webview (经 `ScriptExecuteActivity`, 等 `onDestroy`), S6 `floaty.window` + `floaty.rawWindow`, S7 事件 / 按键 / 传感器 / 无障碍事件 (按键与无障碍部分需宿主无障碍服务, 未开启时只跑其余部分并在报告中记录), S8 `JavaAdapter` + `runtime.loadDex`, S9 `engines.execScriptFile` 自我重启 3 层, S10 `http.get` + WebSocket (测试进程内的回环服务, 不需要网络), S11 `device.*` 读取 / 振动 / `keepScreenOn`, S12 `plugins.load` (3-ImGui, 未安装时跳过).

度量目前是测试内的临时实现 (`GateMetrics.kt`; Rhino Context 计数经反射读取 `AndroidContextFactory.mContextCount`), ROADMAP P2.1 的 `MemoryDiagnostics` 落地后切换. 脚本线程按线程名标记计数 (`ScriptThread-` / `Spawn-` / `Pool-` / `Timer` / `ScreenCapturer` / `LooperThread`), Android 14 起框架自带的 `SurfaceSyncGroupTimer` 不计 (P1 起).

## leakcanary_probe.py: LeakCanary 报告核对

```
py -3.12 -I -u tools/memory/leakcanary_probe.py -s SERIAL [--scenario S2] [--runs 6] [--wait 300] [--expect Loopers] [--expect-leaks yes|no] [--out FILE]
```

LeakCanary 在 instrumentation 测试进程里不转储堆, 所以门禁看不到它的报告 (ROADMAP P1.2 / T5). 本脚本把场景脚本 (门禁正常结束模式的版本, 目前 S1 / S2) 推到 `/sdcard/Download/aj6-memory-probe/`, 经 `RunIntentActivity` 以普通脚本运行 `--runs` 次, 然后在 `--wait` 秒内从 logcat (`LeakCanary` 标签) 收集 LeakCanary 的堆分析结果. 输出: 每次启动一行, LeakCanary 记录的监视对象与最大残留数, 是否出现分析, 应用泄漏数, 引用链是否包含 `--expect`; `--out` 把分析文本 (与 LeakCanary 泄漏列表中显示的文本相同) 写入文件. `--expect-leaks yes` 时出现含 `--expect` 的应用泄漏即 PASS (修前构建), `no` 时等待期内没有应用泄漏即 PASS (修后构建). 需要 debug 构建且开发者选项中的 LeakCanary 开关为开 (默认). 脚本不点屏幕, 不改设置.

## static_audit.py: 源码静态审计

```
py -3.12 -I tools/memory/static_audit.py [--out docs/memory/evidence] [--json]
```

扫描 `app/src/main/java`, 输出 `static-audit-<yyyyMMdd>.md`: (a) 进程级字段 (Java `static`, Kotlin `object` / `companion object` / 顶层属性) 中类型为 ScriptRuntime / ScriptEngine / Scriptable / Rhino Context / Activity / View / Fragment / Dialog 的清单 (Android Context 单列备查); (c) 脚本相关包内或元素为线程 / Looper / 监听器 / 窗口 / 图像 / 引擎 / 类加载器的进程级注册表 (附录 B.1 候选); (b) `registerReceiver` / `bindService` / `.subscribe(` / 创建 WebView / `addIdleHandler` / `Looper.prepare` / `ThreadLocal` 的文件及同文件内对应释放调用的次数. 这是文本扫描 (先抹掉注释与字符串), 归属列按包路径猜测, 结论须人工复核后再写进附录 B; 也是 ROADMAP T8 源码守卫白名单的来源.
