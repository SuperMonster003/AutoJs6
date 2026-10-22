# Augment 重构工具

配合 `docs/augment/ROADMAP.md` 使用. 这些工具都只读宿主源码或已安装的宿主, 不修改设备上的宿主数据; 真机脚本通过 `RunIntentActivity` 启动, 结果从 logcat 的 `GlobalConsole` 行读取 (轮询累积, 从不清空 logcat).

## device_bench.py: 真机计时

```
py -3.12 -I tools/augment/device_bench.py -s SERIAL [--runs 5] [--skip-compile] [--skip-calls] [--skip-errors] [--new-modules on|off]
```

1. `bench/aj6_init_timing.js` 跑 `--runs` 次, 读取 debug 宿主打印的 `runtime-init-prologue` / `script-init` / `runtime-init-epilogue` (毫秒) 并给出最小 / 中位 / 最大值. release 宿主不打印这些行, 只会得到总用时.
2. `bench/obsolete_compile_timing.js` 把 `assets/modules/obsolete/` 推到设备, 在脚本里用 `new Function` 编译 45 个旧 JS 模块各 3 遍, 打印每遍总耗时与最慢的 8 个文件 (只编译不执行).
3. `bench/call_overhead.js` 对比普通 JS 函数, Rhino 内置函数, Augment 函数 / getter / 全局函数, 以及同一 Java 方法经 Rhino 自带桥接的单次耗时 (20 万次), 最后测 1000 次失败调用的异常路径.
4. `bench/error_path.js` 跑 `--runs` 次, 给出每种失败调用的最小 / 中位 / 最大值 (微秒; 脚本内每项先预热至多 200 次, 再计至多 2000 次, 至少 100 次, 超过 3 秒即停, 每次是一个箭头函数包着的 try / catch): Rhino 自身的错误 (`undefined.x`, 内置函数抛出的 TypeError / RangeError, 经 Rhino 自带桥接调用的 Java 方法抛出的异常 `java.lang.Integer.parseInt("x")`) 与脚本 `throw` 作参照, `base64` 的成功调用与四种失败调用 (参数个数, null 参数, 未知字符集, 实现内的 Java 异常; 开关开时走新脚本模块层, 关时走旧 augment), 读 `e.message` / `e.stack` 的附加开销, 以及经 Rhino 自带 Java 桥接单独测得的各步骤 (`part` 开头: 资源查找, `String.format`, `ScriptMessages` 两条消息, 构造 EcmaError / Java 异常; 减去 `part bridge` 即为该步骤本身). 用于 ROADMAP R2 与 P1.4 的分解.

基线数值见 ROADMAP 附录 A. 设备上的临时目录为 `/sdcard/Download/aj6-augment-bench`, 可随时删除.

脚本是否结束按控制台的结束行判断, 应用语言为中文 (含 "运行结束" 与 "用时 N 秒") 或英文 ("finished in N seconds") 都可识别 (`device_surface_snapshot.py` 同). 数值随设备状态变化很大 (P1.2 与 P1.4 在同一台 XQ-AT72 上相差 4-6 倍, Rhino 自身的错误同比例变化, 见 ROADMAP 附录 A), 比较开关开 / 关或前后版本时在同一状态下交替运行, 并看与同一次运行中 Rhino 参照项的比值.

`--new-modules on|off` 在计时前用一个小脚本设置新脚本模块层的隐藏开关 (ROADMAP T8, 偏好设置键 `key_$_new_script_module_layer`, 经 `Pref.putBooleanSync` 写入), 结束后恢复原值; 开关只影响之后启动的脚本.

`--calls NAME ...` (P2.1 起) 把 `bench/calls_prelude.js` 与 `bench/calls/NAME.js` 合成一个脚本运行 `--runs` 次, 给出每项的最小 / 中位 / 最大值 (微秒, 计时规则同 `error_path.js`). 前导部分带有 Rhino 参照项 (`ref` 开头: 空循环, `undefined.x`, 桥接 Java 异常, `Math.max`), 每个模块组的成功与失败调用写在自己的 `bench/calls/<组>.js` 中 (如 `jsox.js`), 各 P2 / P3 会话只新增自己的文件, 不改共享脚本. `--skip-init` 跳过启动计时, 只测调用.

## device_r2.py: 一条命令跑完附录 A 规程的一轮 R2 计时 (P2.4)

```
py -3.12 -I tools/augment/device_r2.py -s SERIAL --restart [--warmup 100] [--rounds 2] [--runs 5] --errors --calls pure jsox colors util --init
```

`--restart` 先 force-stop 宿主 (新进程), 再以启动计时脚本暖机 `--warmup` 次 (每 20 次切换一次新模块层开关), 然后 `--rounds` 轮 "关, 开": 每种状态下 `bench/error_path.js` (`--errors`), `bench/calls/<组>.js` (`--calls`) 与启动计时 (`--init`) 各跑 `--runs` 次. 前后打印宿主进程的调度组 (PCY), 中途变化则数据不可比 (R2). 输出每项在各轮各状态的中位数 (us, 启动计时为 ms), 开关开的失败调用后附对同次运行中 Rhino 参照项的比值 (参数错误对 `undefined.x`, Java 异常对经 Rhino 桥接抛出的 Java 异常, 读 `e.stack` 对 `undefined.x` 后读 `e.stack`), 即 R2 的判定值; 结束时恢复开关原值. 收到的次数少于 `--runs` 时 (logcat 丢行) 标出 n. 注意: force-stop 会让系统把宿主的无障碍服务移出已启用列表 (G8441 上 2026-10-22 实测, 宿主之外一个插件的无障碍服务也随之解除绑定), 测前开着宿主无障碍的设备, 测后由维护者在手机上重新开启.

## device_profile.py: 采样确认调用路径已 JIT (P2.4)

```
py -3.12 -I tools/augment/device_profile.py -s SERIAL [--new-modules on|off] [--seconds 8] "base64.encode()" "isNullish(null)"
```

每个表达式单独一个脚本, 在 try / catch 循环中运行 `--seconds` 秒以上, 期间以 `simpleperf record --app` 采样宿主进程 (debug 宿主可调试, 解释执行与 JIT 编译的 Java 帧都带方法名). 每个表达式只打印两项: 脚本自己测得的每次耗时, 以及仍在 ART 解释器中执行 (帧在 `base.apk!classesNN.dex` 中) 的模块层 (`org.autojs.autojs.runtime.api.modules`) 与 Rhino (`org.mozilla.javascript`) 方法及其包含占比. 按附录 A 暖机后的进程中模块层应当一项都没有. ROADMAP P2.4 发现过一个长时间运行的宿主进程, 其中模块层热路径上的方法一直停在解释器, 失败调用因此为 Rhino 自身错误的 1.13-1.46 倍; 计时 (R2) 前后各跑一次, 可排除这种状态. 设备上只临时写 `/data/local/tmp/aj6_profile.data`, 用完即删. 需要系统自带的 simpleperf: XQ-AT72 (Android 12) 有, G8441 (Android 9) 没有, NDK r26 的 simpleperf 在 G8441 上无法链接.

## device_surface_snapshot.py: 真机 API 表面快照

```
py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL [--out tools/augment/baseline]
```

在设备上运行 `surface_snapshot.js`, 枚举 `global` 的全部自有属性, 对模块对象再向下两层记录每个属性的特性 (可枚举 / 可写 / 可配置 / 访问器), 值的种类 (函数名与参数个数, 对象标签, Java 类名, 原始值) 与原型链, 并记录 `Object.keys(global)` 的顺序和 `$` 别名的同一性. 结果转换为按路径排序的文本 `surface-<model>-<versionCode>.txt` (一行一个属性, 便于 diff), 原始 JSON 放在同名 `.json`. 访问器属性只记录存在性, 不求值; 数据属性的值会被读取, 因此快照过程会触发 Java 侧 getter.

新实现切换前, 对同一设备再生成一份并与基线 diff, 只允许出现 ROADMAP 红线 R1 允许的差异.

P2.1 增补: `--new-modules on|off` 与 `--extend-builtins on|off` 在快照前分别设置新脚本模块层开关与 "JavaScript 内置对象扩展" 设置 (键 `key_$_extending_js_build_in_objects`), 结束后恢复原值; `--roots Array.prototype Number.prototype` 另外记录这些对象的自有属性 (对 `global` 的遍历把 `Array.prototype` 当作数组, 不展开), 输出为排在全局各行之后的 `@<路径>.` 前缀行; `--tag on` 等给输出文件名加后缀, 避免开 / 关两份互相覆盖. 例如 P2.4 的比对:

```
py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL --out OUT --tag off --new-modules off --extend-builtins on --roots Array.prototype Number.prototype
py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL --out OUT --tag on --new-modules on --extend-builtins on --roots Array.prototype Number.prototype
py -3.12 -I tools/augment/surface_compare.py OUT/surface-<model>-<versionCode>-off.txt OUT/surface-<model>-<versionCode>-on.txt --only Arrayx Mathx Numberx jsox randomInt randomFloat parseFloat Array Math Number @Array.prototype @Number.prototype --allow-length
```

P3.0 增补: `--global-classes` 另外记录 global 经原型解析的 Java 类名 (`augment/global/Classes.kt` 的字段, 不是全局自有属性, 对象枚举看不到), 每个名称一行 `@lookup.<名称>`: `in` 与自有属性两项, 裸名称的 `typeof`, 以及从 global 读取的值的 `typeof` 与字符串形式; 比对时用 `--only @lookup`.

## surface_compare.py: 比对两份表面快照

```
py -3.12 -I tools/augment/surface_compare.py BEFORE.txt AFTER.txt [--only NAME ...] [--allow-length]
```

逐行比对 `device_surface_snapshot.py` 写出的两份文本: 只在一侧出现的属性, 特性或值不同的属性, 全局键顺序与 `$` 别名的差异 (版本号, 设备与计数等头部信息不比较). `--only` 只比较给定的顶层名称 (含其 `$` 别名与下级路径; `@Array.prototype` 表示 `--roots` 记录的对象) 以及这些名称在全局键中的相对顺序; `--allow-length` 接受只有函数 `length` 不同的差异 (函数行的 `length=N`, 以及 `.length` / `.arity` 行的数值, 即 ROADMAP 附录 C Q9 允许的差异). 有未被接受的差异时退出码为 1.

## device_eval.py: 真机复核差分测试记录的 Kotlin 值

```
py -3.12 -I tools/augment/device_eval.py -s SERIAL [--cases DIR_OR_FILE ...] [--keep] [--new-modules on|off] [--strict-errors]
```

JVM 差分测试 (`app/src/test/java/org/autojs/autojs/runtime/api/augment/differential/`) 运行时把已记录的偏差与 JVM 上只能跳过的表达式导出到 `app/build/augment-differential/<测试类>.json`. 本脚本把这些表达式合成一个脚本在设备上运行, 按 `DifferentialRhino.render` 的规则渲染结果, 与 JVM 上记录的 Kotlin 值比对 (两侧都抛出时视为一致, JVM 构造不出 Kotlin 的错误消息); 跳过的表达式没有期望值, 只打印设备结果. 输出只有不一致的条目与汇总行. 先运行差分测试生成 JSON; 设备上的临时目录 `/sdcard/Download/aj6-augment-bench` 用完即删 (`--keep` 保留).

新脚本模块层的 JVM 测试 (`app/src/test/java/org/autojs/autojs/runtime/api/modules/`) 把新模块的期望值导出到 `app/build/script-modules-device/*.json` (同一格式, 期望值在 `kt`). 复核新模块时打开开关并比较错误类型: `--cases app/build/script-modules-device --new-modules on --strict-errors` (`--new-modules` 与 device_bench.py 相同, 结束后恢复原值; `--strict-errors` 让两侧都抛出时也要求错误名一致). `--debug-print on|off` 同样临时设置新模块层调试打印的总开关 (附录 C Q4, 键 `key_$_script_module_debug_print`), `--console` 打印本次运行期间 logcat 中 `GlobalConsole` (脚本控制台) 与 `ScriptModules` 标签的行, 用于查看调试条目原文.

设备结果取决于设备本身 (TTS 引擎, 传感器) 的用例在 JSON 中 `kt` 为 null, 只打印设备结果, 在开关两态之间人工比较. 等待 Promise 时不要在脚本线程上 `wait()` (Promise 回调排在被阻塞的线程上, 会一直等到超时), 用 `HostModuleTestSupport.settled` 的写法在 `threads.start` 的线程上结算. 本目录的工具都以 `logcat -d` 轮询结果, 不要为了防丢行调大 logcat 缓冲区: 2026-10-22 XQ-AT72 改为 `logcat -G 16M` 后每次转储约 300 万行, 耗时约 1 分钟 (按 tag 过滤更慢), 计时脚本几乎停滞; 默认 256 KiB 在该机 ThermalHAL 刷屏 (约 310 行/秒) 时仍能保留约 50 秒, 足够.

## device_net.py: 需要网络对端的真机用例 (P3.5)

```
py -3.12 -I tools/augment/device_net.py -s SERIAL [--port 18735] [--cases DIR_OR_FILE ...] [device_eval.py 的其他参数]
```

在 PC 的 127.0.0.1:PORT 上启动 HTTP 与 WebSocket 服务, 用 `adb reverse` 把设备的 127.0.0.1:PORT 转到它, 以给定用例 (默认 `app/build/script-modules-device-net`, 由 `HttpModuleTest` 与 `WebModulesTest` 写出) 与其余参数 (如 `--new-modules on --strict-errors`) 运行 `device_eval.py`, 结束后撤销转发并停止服务. 端点: `/echo` (任何方法, 以 JSON 返回 method / path / query / contentType / body 与请求头 X-A, 响应头 X-Echo: head), `/status/404` (404, 正文 "nope"), `/slow` (3 秒后应答, 用于超时), `/ws` (WebSocket, 文本消息原样加 "echo:" 前缀返回, 应答 ping 与 close).

## device_developer_options.py: 打开开发者选项页并截图

```
py -3.12 -I tools/augment/device_developer_options.py -s SERIAL [--out DIR] [--new-modules on|off] [--debug-print on|off]
```

`DeveloperOptionsActivity` 未导出, adb 的 `am start` 打不开; 本脚本经 RunIntentActivity 运行一个小脚本, 在应用进程内以 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` 打开它 (已打开的页面会重建, 显示刚写入的偏好), 等待后 `screencap` 拉回到 `--out` (默认 `tools/augment/__pycache__/`), 不点击屏幕. `--new-modules` / `--debug-print` 先写入对应的偏好 (ROADMAP P1.3 的 "脚本模块" 类别), 不自动恢复, 需要时以原值再运行一次. 屏幕须已点亮并解锁, 否则脚本只报告并退出.

## inventory_diff.py: 旧 JS 成员名 对 Kotlin 注册名

```
py -3.12 -I tools/augment/inventory_diff.py [--host D:/idea-projects/AutoJs6] [--dts D:/idea-projects/AutoJs6-TypeScript-Declarations/declarations/autojs6] [--output report.json]
```

用正则从 `assets/modules/obsolete/__xxx__.js` 收集方法, getter 与对象字面量键, 与 `tools/api-coverage/extract_exports.py` 解析出的 Kotlin 注册名比对, 再用 d.ts 公开声明过滤. 这是启发式的: Java 原型链成员, 子模块 (如 `util.java`), 返回对象的成员与 JS 内部辅助函数都会被列为 "仅 JS", 需要人工核对. 它只用于初筛, 正式的兼容性证据来自 JVM 差分测试与真机表面快照.

## device_bootstrap_check.py: 启动必载对象的真机探针与 smoke (P2.5)

```
py -3.12 -I tools/augment/device_bootstrap_check.py -s SERIAL --probe [--save FILE] [--compare FILE]
py -3.12 -I tools/augment/device_bootstrap_check.py -s SERIAL --smoke coroutine listener suspend images automator ui [--wait 60] [--ui-wait 180]
```

`--probe` 运行 `bench/bootstrap_probe.js`, 逐项打印 ROADMAP P2.5 收编的对象 (`__exitIfError__`, `__asGlobal__`, `continuation.await` / `delay`, `ResultAdapter`) 与首次访问时安装的 polyfill (`Object.observe` / `Array.observe` / `structuredClone`) 的表面与行为; 前几项在读取任何 polyfill 名称之前记录数组方法与键顺序. `--save` 把结果写入 JSON, `--compare` 列出与保存的一次运行 (如改动前的构建) 不同的项.

`--smoke` 运行 `bench/bootstrap_smoke/`: `coroutine` 与 `ui` 是启用 continuation 特性的项目, 经 `ProjectLauncher` 按应用运行项目的方式启动 (`project.json` 的 `useFeatures` 生效); `ui` 显示 `dialogs.confirm` 并等待在手机上点按 (`--ui-wait` 秒), 需要维护者操作; 恢复后再次挂起是附录 C Q23 的回归: `coroutine` 由 setInterval 回调连续恢复三次 (原先第二次挂起后定时器被取消, 脚本卡住), `ui` 在对话框之前先由其他线程恢复一次再 `continuation.delay` (原先应用崩溃), `dialogs.confirm().await()` 是由 delay 的定时器恢复后的再次挂起 (原先脚本被结束); `listener` (附录 C Q23 的后续) 在 `ui.emitter` 的 back_pressed 监听器与自身全屏视图的 click 监听器中挂起, 脚本以 adb 发送 KEYCODE_BACK 并点按屏幕中央 (不涉及对话框), 原先挂起会抛出 "Cannot capture continuation ..." 并结束脚本; `suspend` (P3.2, 附录 C Q23 的 UI 模式情形, 无需点按) 在 UI 模式下依次由 continuation.delay 的定时器, 定时器中结算的 Promise, setInterval 回调与其他线程恢复后再次挂起, 并在 delay 之后等待自己关闭的对话框的 dismiss 事件 (非 UI 模式的 setInterval 与跨线程两种情形在 `coroutine` 中), 每行末尾为 true 即该情形通过; `images` 运行 `images.loadAsync` (经 `ResultAdapter.promise`); `automator` 运行 `automator.captureScreen` (经 `ResultAdapter.wait`, 需要 AutoJs6 无障碍服务与 Android 11+, 服务未开启时跳过). 只打印带标记的控制台行. 设备上的临时目录 `/sdcard/Download/aj6-augment-bench/p25` 用完即删.

`device_bench.py --calls bootstrap` (`bench/calls/bootstrap.js`) 计时数组 push / pop 与 splice (在读取 observe 名称之前与之后各一次), `__exitIfError__`, `structuredClone` 与 `ResultAdapter.promise`.
