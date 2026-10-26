# Augment 重构工具

配合 `docs/augment/ROADMAP.md` 使用. 这些工具都只读宿主源码或已安装的宿主, 不修改设备上的宿主数据; 真机脚本通过 `RunIntentActivity` 启动, 结果从 logcat 的 `GlobalConsole` 行读取 (每个脚本启动前 `logcat -c`, 每次轮询只读最后 50000 行 (`device_bench.LOG_TAIL`) 并累积; XQ-AT72 的 thermal HAL 每秒约 860 行, 全量 `logcat -d` 曾需约 40 s 且丢行).

## device_bench.py: 真机计时

```
py -3.12 -I tools/augment/device_bench.py -s SERIAL [--runs 5] [--skip-calls] [--skip-errors] [--calls NAME ...] [--skip-init]
```

1. `bench/aj6_init_timing.js` 跑 `--runs` 次, 读取 debug 宿主打印的 `runtime-init-prologue` / `script-init` / `runtime-init-epilogue` (毫秒) 并给出最小 / 中位 / 最大值. release 宿主不打印这些行, 只会得到总用时.
2. (P5.2 删除) `bench/obsolete_compile_timing.js` 曾把 `assets/modules/obsolete/` 推到设备, 在脚本里用 `new Function` 编译 45 个旧 JS 模块各 3 遍; 旧模块随 P5.2 删除, 脚本与 `--skip-compile` 一并删除.
3. `bench/call_overhead.js` 对比普通 JS 函数, Rhino 内置函数, 脚本模块层的函数 / getter / 全局函数 (P5.2 之前开关关时为 Augment 的), 以及同一 Java 方法经 Rhino 自带桥接的单次耗时 (20 万次), 最后测 1000 次失败调用的异常路径.
4. `bench/error_path.js` 跑 `--runs` 次, 给出每种失败调用的最小 / 中位 / 最大值 (微秒; 脚本内每项先预热至多 200 次, 再计至多 2000 次, 至少 100 次, 超过 3 秒即停, 每次是一个箭头函数包着的 try / catch): Rhino 自身的错误 (`undefined.x`, 内置函数抛出的 TypeError / RangeError, 经 Rhino 自带桥接调用的 Java 方法抛出的异常 `java.lang.Integer.parseInt("x")`) 与脚本 `throw` 作参照, `base64` 的成功调用与四种失败调用 (参数个数, null 参数, 未知字符集, 实现内的 Java 异常; 走脚本模块层, P5.2 之前开关关时走旧 augment), 读 `e.message` / `e.stack` 的附加开销, 以及经 Rhino 自带 Java 桥接单独测得的各步骤 (`part` 开头: 资源查找, `String.format`, `ScriptMessages` 两条消息, 构造 EcmaError / Java 异常; 减去 `part bridge` 即为该步骤本身). 用于 ROADMAP R2 与 P1.4 的分解.

基线数值见 ROADMAP 附录 A. 设备上的临时目录为 `/sdcard/Download/aj6-augment-bench`, 可随时删除.

脚本是否结束按控制台的结束行判断, 应用语言为中文 (含 "运行结束" 与 "用时 N 秒") 或英文 ("finished in N seconds") 都可识别 (`device_surface_snapshot.py` 同). 数值随设备状态变化很大 (P1.2 与 P1.4 在同一台 XQ-AT72 上相差 4-6 倍, Rhino 自身的错误同比例变化, 见 ROADMAP 附录 A), 比较前后版本 (P5.2 之前还有开关开 / 关) 时在同一状态下交替运行, 并看与同一次运行中 Rhino 参照项的比值.

P5.2 删除了新脚本模块层的隐藏开关 (ROADMAP T8, 偏好设置键 `key_$_new_script_module_layer`) 与 `assets/modules/obsolete/`, 本目录各工具的 `--new-modules on|off` (计时或快照前写入开关, 结束后恢复) 一并删除, 只剩新层一种状态; 两态对照的旧结果见 ROADMAP 附录 A / B / E. 设备上残留的开关存储值无害 (P5.2 起没有构建读取它), `device_matrix.py` 的 `layer` 项会报告它. `device_bench.pref_value_js` 只用于其他布尔偏好, 未存储时为 false.

`--calls NAME ...` (P2.1 起) 把 `bench/calls_prelude.js` 与 `bench/calls/NAME.js` 合成一个脚本运行 `--runs` 次, 给出每项的最小 / 中位 / 最大值 (微秒, 计时规则同 `error_path.js`). 前导部分带有 Rhino 参照项 (`ref` 开头: 空循环, `undefined.x`, 桥接 Java 异常, `Math.max`), 每个模块组的成功与失败调用写在自己的 `bench/calls/<组>.js` 中 (如 `jsox.js`), 各 P2 / P3 会话只新增自己的文件, 不改共享脚本. `--skip-init` 跳过启动计时, 只测调用.

## device_r2.py: 一条命令跑完附录 A 规程的一轮 R2 计时 (P2.4)

```
py -3.12 -I tools/augment/device_r2.py -s SERIAL --restart [--warmup 100] [--rounds 2] [--runs 5] --errors --calls pure jsox colors util --init
```

`--restart` 先 force-stop 宿主 (新进程), 再以启动计时脚本暖机 `--warmup` 次, 然后 `--rounds` 轮: 每轮 `bench/error_path.js` (`--errors`), `bench/calls/<组>.js` (`--calls`) 与启动计时 (`--init`) 各跑 `--runs` 次. 前后打印宿主进程的调度组 (PCY), 中途变化则数据不可比 (R2). 输出每项在各轮的中位数 (列 `r1` .. `rN`, us, 启动计时为 ms), 失败调用后附对同次运行中 Rhino 参照项的比值 (参数错误对 `undefined.x`, Java 异常对经 Rhino 桥接抛出的 Java 异常, 读 `e.stack` 对 `undefined.x` 后读 `e.stack`), 即 R2 的判定值. P5.2 之前每轮先关后开 (列 `rN off` / `rN on`, 只有开态附比值), 暖机每 20 次切换一次开关. 收到的次数少于 `--runs` 时 (logcat 丢行) 标出 n. 注意: force-stop 会让系统把宿主的无障碍服务移出已启用列表 (G8441 上 2026-10-22 实测, 宿主之外一个插件的无障碍服务也随之解除绑定), 测前开着宿主无障碍的设备, 测后由维护者在手机上重新开启.

在 XQ-AT72 上判定 R2 时加 `--verify` (ROADMAP 附录 C Q76, 隐含 `--restart`): 可调试构建的宿主有时让新层的 `ScriptMessages` 方法停在 ART 解释执行 (P2.4 待决 (a) 的状态), 新层每个失败调用多约 30-50 us, 比值从 0.87-1.20 升到 1.13-1.45; 新进程加 `--warmup 100` 也排除不了, 触发条件未查明 (附录 A "P3.6"). `--verify` 下每个脚本 (先 error_path, 再各调用文件, 最后启动计时) 单独跑完全部轮次, 前后各用 device_profile 剖析一次 `base64.encode()`: 每次 <= 225 us 且 `ScriptMessages` 解释帧 <= 5% 才算干净. 不干净时换新进程 (force-stop, 同样暖机, 再跑一次 error_path) 重测该脚本, 至多 `--verify-retries` 次 (默认 2). 作废的计时也打印, 行名为 "<脚本> (discarded N)", 重测的为 "<脚本> (attempt N)"; 末尾 `verify:` 表列出每次尝试所在进程 (pid 与 cpuset) 与前后核验数值. 判定只用干净的行. 每个脚本多两次剖析 (各约 15 s), 需要设备上有 simpleperf (G8441 没有, 只在 XQ-AT72 上用).

```
py -3.12 -I -u tools/augment/device_r2.py -s QV710AF65F --verify --warmup 100 --rounds 2 --runs 5 --errors --calls pure jsox --init
```

## device_profile.py: 采样确认调用路径已 JIT (P2.4)

```
py -3.12 -I tools/augment/device_profile.py -s SERIAL [--seconds 8] "base64.encode()" "isNullish(null)"
```

每个表达式单独一个脚本, 在 try / catch 循环中运行 `--seconds` 秒以上, 期间以 `simpleperf record --app` 采样宿主进程 (debug 宿主可调试, 解释执行与 JIT 编译的 Java 帧都带方法名). 每个表达式只打印两项: 脚本自己测得的每次耗时, 以及仍在 ART 解释器中执行 (帧在 `base.apk!classesNN.dex` 中) 的模块层 (`org.autojs.autojs.runtime.api.modules`) 与 Rhino (`org.mozilla.javascript`) 方法及其包含占比. 按附录 A 暖机后的进程中模块层应当一项都没有. ROADMAP P2.4 发现过一个长时间运行的宿主进程, 其中模块层热路径上的方法一直停在解释器, 失败调用因此为 Rhino 自身错误的 1.13-1.46 倍; 计时 (R2) 前后各跑一次, 可排除这种状态. 设备上只临时写 `/data/local/tmp/aj6_profile.data`, 用完即删. 需要系统自带的 simpleperf: XQ-AT72 (Android 12) 有, G8441 (Android 9) 没有, NDK r26 的 simpleperf 在 G8441 上无法链接.

## device_surface_snapshot.py: 真机 API 表面快照

```
py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL [--out tools/augment/baseline]
```

在设备上运行 `surface_snapshot.js`, 枚举 `global` 的全部自有属性, 对模块对象再向下两层记录每个属性的特性 (可枚举 / 可写 / 可配置 / 访问器), 值的种类 (函数名与参数个数, 对象标签, Java 类名, 原始值) 与原型链, 并记录 `Object.keys(global)` 的顺序和 `$` 别名的同一性. 结果转换为按路径排序的文本 `surface-<model>-<versionCode>.txt` (一行一个属性, 便于 diff), 原始 JSON 放在同名 `.json`. 访问器属性只记录存在性, 不求值; 数据属性的值会被读取, 因此快照过程会触发 Java 侧 getter.

新实现切换前, 对同一设备再生成一份并与基线 diff, 只允许出现 ROADMAP 红线 R1 允许的差异.

P2.1 增补: `--extend-builtins on|off` 在快照前设置 "JavaScript 内置对象扩展" 设置 (键 `key_$_extending_js_build_in_objects`), 结束后恢复原值 (P5.2 之前 `--new-modules on|off` 同样设置新脚本模块层开关); `--roots Array.prototype Number.prototype` 另外记录这些对象的自有属性 (对 `global` 的遍历把 `Array.prototype` 当作数组, 不展开), 输出为排在全局各行之后的 `@<路径>.` 前缀行; `--tag NAME` 给输出文件名加后缀, 避免两份互相覆盖. 例如比对两个构建 (P2.4 比对的是开关两态, 两份快照分别加 `--new-modules off` / `on`):

```
py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL --out OUT --tag before --extend-builtins on --roots Array.prototype Number.prototype
py -3.12 -I tools/augment/device_surface_snapshot.py -s SERIAL --out OUT --tag after --extend-builtins on --roots Array.prototype Number.prototype
py -3.12 -I tools/augment/surface_compare.py OUT/surface-<model>-<versionCode>-before.txt OUT/surface-<model>-<versionCode>-after.txt --only Arrayx Mathx Numberx jsox randomInt randomFloat parseFloat Array Math Number @Array.prototype @Number.prototype --allow-length
```

P3.0 增补: `--global-classes` 另外记录 global 经原型解析的 Java 类名 (`modules/global/GlobalJavaClasses.kt` 的 `NAMES`, P5.2 之前为 `augment/global/Classes.kt` 的字段; 不是全局自有属性, 对象枚举看不到), 每个名称一行 `@lookup.<名称>`: `in` 与自有属性两项, 裸名称的 `typeof`, 以及从 global 读取的值的 `typeof` 与字符串形式; 比对时用 `--only @lookup`.

## surface_compare.py: 比对两份表面快照

```
py -3.12 -I tools/augment/surface_compare.py BEFORE.txt AFTER.txt [--only NAME ...] [--allow-length]
```

逐行比对 `device_surface_snapshot.py` 写出的两份文本: 只在一侧出现的属性, 特性或值不同的属性, 全局键顺序与 `$` 别名的差异 (版本号, 设备与计数等头部信息不比较). `--only` 只比较给定的顶层名称 (含其 `$` 别名与下级路径; `@Array.prototype` 表示 `--roots` 记录的对象) 以及这些名称在全局键中的相对顺序; `--allow-length` 接受只有函数 `length` 不同的差异 (函数行的 `length=N`, 以及 `.length` / `.arity` 行的数值, 即 ROADMAP 附录 C Q9 允许的差异). 有未被接受的差异时退出码为 1.

## device_eval.py: 真机复核脚本模块测试记录的 JVM 值

```
py -3.12 -I tools/augment/device_eval.py -s SERIAL [--cases DIR_OR_FILE ...] [--keep] [--strict-errors] [--debug-print on|off] [--console]
```

脚本模块层的 JVM 测试 (`app/src/test/java/org/autojs/autojs/runtime/api/modules/`) 把用例导出到 `app/build/script-modules-device/<测试类>.json` (默认的 `--cases`): JVM 上的值在 `kt`, JVM 上求不了值 (只能跳过) 的表达式没有期望值. 本脚本把这些表达式合成一个脚本在设备上运行, 按 `DifferentialRhino.render` 的规则渲染结果, 与 JVM 上的值比对 (两侧都抛出时视为一致, 除非 `--strict-errors`); 没有期望值的表达式只打印设备结果. P5.2 之前默认用例是 P0 差分测试 (旧 JS 对当时的 Kotlin) 导出的 `app/build/augment-differential`, 复核新模块时要加 `--new-modules on`. 收到结束标记, 宿主的 "[<路径>] ..." 脚本结束行 (脚本捕获不到的错误, 如旧实现经反射抛出的 InvocationTargetException 或 java.lang.Error, 会在结束标记之前结束脚本) 或等到 `--wait` 秒时停止读取. 输出只有不一致的条目与汇总行. 先运行差分测试生成 JSON; 设备上的临时目录 `/sdcard/Download/aj6-augment-bench` 用完即删 (`--keep` 保留).

复核时比较错误类型: `--strict-errors` 让两侧都抛出时也要求错误名一致. `--debug-print on|off` 临时设置新模块层调试打印的总开关 (附录 C Q4, 键 `key_$_script_module_debug_print`), `--console` 打印本次运行期间 logcat 中 `GlobalConsole` (脚本控制台) 与 `ScriptModules` 标签的行, 用于查看调试条目原文.

设备结果取决于设备本身 (TTS 引擎, 传感器) 的用例在 JSON 中 `kt` 为 null, 只打印设备结果, 人工核对 (P5.2 之前在开关两态之间比较). 等待 Promise 时不要在脚本线程上 `wait()` (Promise 回调排在被阻塞的线程上, 会一直等到超时), 用 `HostModuleTestSupport.settled` 的写法在 `threads.start` 的线程上结算. 本目录的工具都以 `logcat -d` 轮询结果, 不要为了防丢行调大 logcat 缓冲区: 2026-10-22 XQ-AT72 改为 `logcat -G 16M` 后每次转储约 300 万行, 耗时约 1 分钟 (按 tag 过滤更慢), 计时脚本几乎停滞; 默认 256 KiB 在该机 ThermalHAL 刷屏 (约 310 行/秒) 时仍能保留约 50 秒, 足够.

## device_patterns.py: 选择器正则预检对照设备引擎 (附录 C Q70 (2))

```
py -3.12 -I tools/augment/device_patterns.py -s SERIAL [--samples 100000] [--seed 70] [--verdicts FILE]
```

在宿主进程里运行 `bench/patterns_check.js` (宿主须含 Q70 (2) 的正则预检, selector P1.4 起为 `core/automator/filter/PatternPrecheck.kt`): 预检拒绝的正则都不得能被 JsRegexUtils.compileJsOrPlainRegex (Android 上为 ICU 的 java.util.regex.Pattern) 编译. 正则来自 JVM 用例的语料 (`app/src/test/resources/augment-q70/patterns.json`), `--samples` 条随机正则字符串与 `--samples` 条按语法构造的正则 (半数再变异). 每组打印条数, 不能编译, 被拒绝, 误拒 (为 0 时 PASS) 与交给编译的条数, 另记只有 Rhino `RegExp` 或只有 Pattern 接受的条数; `--verdicts` 保存语料每条的结果 (能编译, 被拒绝, RegExp 接受, 各 0 / 1) 供与 JVM 比较. 不需要开关, 不点按; XQ-AT72 上 10 万条约 45 秒, G8441 约 6 分钟.

## device_net.py: 需要网络对端的真机用例 (P3.5)

```
py -3.12 -I tools/augment/device_net.py -s SERIAL [--port 18735] [--cases DIR_OR_FILE ...] [device_eval.py 的其他参数]
```

在 PC 的 127.0.0.1:PORT 上启动 HTTP 与 WebSocket 服务, 用 `adb reverse` 把设备的 127.0.0.1:PORT 转到它, 以给定用例 (默认 `app/build/script-modules-device-net`, 由 `HttpModuleTest` 与 `WebModulesTest` 写出) 与其余参数 (如 `--strict-errors`) 运行 `device_eval.py`, 结束后撤销转发并停止服务. 端点: `/echo` (任何方法, 以 JSON 返回 method / path / query / contentType / body 与请求头 X-A, 响应头 X-Echo: head), `/status/404` (404, 正文 "nope"), `/slow` (3 秒后应答, 用于超时), `/ws` (WebSocket, 文本消息原样加 "echo:" 前缀返回, 应答 ping 与 close).

## device_developer_options.py: 打开开发者选项页并截图

```
py -3.12 -I tools/augment/device_developer_options.py -s SERIAL [--out DIR] [--debug-print on|off]
```

`DeveloperOptionsActivity` 未导出, adb 的 `am start` 打不开; 本脚本经 RunIntentActivity 运行一个小脚本, 在应用进程内以 `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP` 打开它 (已打开的页面会重建, 显示刚写入的偏好), 等待后 `screencap` 拉回到 `--out` (默认 `tools/augment/__pycache__/`), 不点击屏幕. `--debug-print` 先写入该偏好 (ROADMAP P1.3 的 "脚本模块" 类别; P5.2 之前还有 `--new-modules`), 不自动恢复, 需要时以原值再运行一次. 屏幕须已点亮并解锁, 否则脚本只报告并退出.

## inventory_diff.py: 旧 JS 成员名 对 Kotlin 注册名

```
py -3.12 -I tools/augment/inventory_diff.py [--host D:/idea-projects/AutoJs6] [--dts D:/idea-projects/AutoJs6-TypeScript-Declarations/declarations/autojs6] [--output report.json]
```

用正则从 `assets/modules/obsolete/__xxx__.js` 收集方法, getter 与对象字面量键, 与 `tools/api-coverage/extract_exports.py` 解析出的 Kotlin 注册名比对, 再用 d.ts 公开声明过滤. 这是启发式的: Java 原型链成员, 子模块 (如 `util.java`), 返回对象的成员与 JS 内部辅助函数都会被列为 "仅 JS", 需要人工核对. 它只用于初筛, 正式的兼容性证据来自 JVM 差分测试与真机表面快照. P5.2 删除了 `assets/modules/obsolete/`, 运行时 `--host` 须指向 P5.2 之前的宿主源码 (例如用 `git archive` 导出删除提交的父提交).

## device_bootstrap_check.py: 启动必载对象的真机探针与 smoke (P2.5)

```
py -3.12 -I tools/augment/device_bootstrap_check.py -s SERIAL --probe [--save FILE] [--compare FILE]
py -3.12 -I tools/augment/device_bootstrap_check.py -s SERIAL --smoke coroutine listener suspend images automator ui [--wait 60] [--ui-wait 180]
```

`--probe` 运行 `bench/bootstrap_probe.js`, 逐项打印 ROADMAP P2.5 收编的对象 (`__exitIfError__`, `__asGlobal__`, `continuation.await` / `delay`, `ResultAdapter`) 与首次访问时安装的 polyfill (`Object.observe` / `Array.observe` / `structuredClone`) 的表面与行为; 前几项在读取任何 polyfill 名称之前记录数组方法与键顺序. `--save` 把结果写入 JSON, `--compare` 列出与保存的一次运行 (如改动前的构建) 不同的项.

`--smoke` 运行 `bench/bootstrap_smoke/`: `coroutine` 与 `ui` 是启用 continuation 特性的项目, 经 `ProjectLauncher` 按应用运行项目的方式启动 (`project.json` 的 `useFeatures` 生效); `ui` 显示 `dialogs.confirm` 并等待在手机上点按 (`--ui-wait` 秒), 需要维护者操作; 恢复后再次挂起是附录 C Q23 的回归: `coroutine` 由 setInterval 回调连续恢复三次 (原先第二次挂起后定时器被取消, 脚本卡住), `ui` 在对话框之前先由其他线程恢复一次再 `continuation.delay` (原先应用崩溃), `dialogs.confirm().await()` 是由 delay 的定时器恢复后的再次挂起 (原先脚本被结束); `listener` (附录 C Q23 的后续) 在 `ui.emitter` 的 back_pressed 监听器与自身全屏视图的 click 监听器中挂起, 脚本以 adb 发送 KEYCODE_BACK 并点按屏幕中央 (不涉及对话框), 原先挂起会抛出 "Cannot capture continuation ..." 并结束脚本; `suspend` (P3.2, 附录 C Q23 的 UI 模式情形, 无需点按) 在 UI 模式下依次由 continuation.delay 的定时器, 定时器中结算的 Promise, setInterval 回调与其他线程恢复后再次挂起, 并在 delay 之后等待自己关闭的对话框的 dismiss 事件 (非 UI 模式的 setInterval 与跨线程两种情形在 `coroutine` 中), 每行末尾为 true 即该情形通过; `images` 运行 `images.loadAsync` (经 `ResultAdapter.promise`); `automator` 运行 `automator.captureScreen` (经 `ResultAdapter.wait`, 需要 AutoJs6 无障碍服务与 Android 11+, 服务未开启时跳过). 只打印带标记的控制台行. 设备上的临时目录 `/sdcard/Download/aj6-augment-bench/p25` 用完即删.

`device_bench.py --calls bootstrap` (`bench/calls/bootstrap.js`) 计时数组 push / pop 与 splice (在读取 observe 名称之前与之后各一次), `__exitIfError__`, `structuredClone` 与 `ResultAdapter.promise`.

## device_promise_check.py: 全局 Promise 的真机核对 (P4.2, 附录 C Q95)

```
py -3.12 -I tools/augment/device_promise_check.py -s SERIAL --sample --scenarios --bench [--wait 90]
```

全局 Promise 是新层的 `PromiseConstructor` (`runtime/api/modules/promise/`); P5.2 之前开关关时是 `assets/modules/promise.js`, 已随开关删除, 两态以 `--new-modules off` / `on` 各运行一次对照. 三个项目都按应用运行项目的方式启动 (经 `bench/bootstrap_smoke/launch_project.js`, 启用 continuation 特性):

- `--sample`: 运行应用自带的 `示例代码/协程/协程HelloWorld`, 按顺序核对 5 行控制台输出 (最后一行读取 `hello.txt`); 其中由 `threads.start` 的线程 `cont.resume(...)` 是附录 C Q98 的回归 (原先该线程先于脚本挂起时报 "Method resume() should be called after suspend()"). 失败时列出缺少的行与错误行.
- `--scenarios`: 运行 `bench/promise_smoke/scenarios`, 打印 19 项 (反应与 timers 的先后, thenable 采纳, 各种 await, 其他线程与 Java 线程结算后回调所在线程, `ScriptPromiseAdapter`, Flow `await()` (Q97), `wait()`, 未处理拒绝警告), 逐项打印供人工核对 (P5.2 之前两态逐项相同即通过). 其中被拒绝的 await 会弹出错误 toast (popException 的既有行为), 不需点按.
- `--bench`: 运行 `bench/promise_smoke/bench`, 打印每次操作的微秒数 (预热 3 次后 7 次的中位数). 除 `wait-resolved` 与 `await-resolved` 外, 各用例先排入 n 个操作, 再挂起等待它们跑完 (由 Looper 运行); `queued+wait` / `queued1200+wait` 改由一次 `wait()` 运行排着的 300 / 1200 个反应, 每个反应的耗时应大致相同 (Q105 之前 `wait()` 是平方级). 早于附录 C Q26 修复的构建 (如 debug 5835) 在脚本线程上 `wait()` 会一直阻塞, 跑不完 `wait-resolved` 之后的用例, 也不能作为 Q105 之前的对照.

G8441 上 `logcat -c` 有时清不掉上一次运行的行, 所以只读取每个脚本启动之后记录的控制台行 (2026-10-24 改; 改前读到上一次的结束行会提前收尾并删除设备目录, 正在运行的样例随后读不到 `hello.txt`). 设备目录 `/sdcard/Download/aj6-augment-bench/p42` 用完即删.

`device_r2.py --calls promise` (`bench/calls/promise.js`) 计时 `new Promise`, `Promise.resolve`, 待定与已结算的 then (只计同步部分), `Promise.all`, 已结算的 `wait()`, 以及构造器的两种 TypeError (参数不是函数, 不用 new 调用; R2 的失败调用).

## device_plugin_modules_check.py: Bundled Libraries 插件的四态 (P4.3)

```
py -3.12 -I tools/augment/device_plugin_modules_check.py -s SERIAL --plugin-apk PLUGIN.apk [--wait 60]
py -3.12 -I tools/augment/device_plugin_modules_check.py -s SERIAL --local-first
```

宿主须是去掉 `assets/modules` 中 14 个库模块文件的构建 (设计 `docs/augment/P4-3-bundled-libraries-design.md` (g), 附录 C Q102), `--plugin-apk` 是 AutoJs6-Plugin-Bundled-Libraries 的 debug 包. 在同一宿主进程中依次经过 未安装 (先卸载插件), 已安装并启用 (`adb install`), 插件中心关闭 (脚本经 `PluginEnableStore` 关闭再打开), 插件页关闭 axios 与 buffer (经插件 debug 构建的 `DebugLibrarySwitchReceiver` 切换对应的 activity-alias, 之后打开; Android 9 起 shell 不能 `pm disable` 非 test-only 应用的组件), 卸载后. 每一态运行一次 `bench/plugin_modules_smoke/main.js` (13 个库共 15 个 require 项, axios / cheerio / dayjs / i18n / pangu 的全局 getter, `require('axios') === axios`, 指引错误的消息与位置), 输出须符合该态的预期 (错误为 `code` = MODULE_NOT_FOUND, `library`, `reason`; 可用的库的 getter 为 "ok"; 位置只登记; P5.2 之前开关关与开各运行一次, 两次须一致). `--local-first` 用常规构建 (资产还在), 未安装插件时全部模块照常加载. 结束时卸载插件, 删除 `/sdcard/Download/aj6-augment-bench/p43`. 插件状态的变化由宿主的包广播与插件中心监听器生效, 不点按屏幕.

## device_matrix.py: 附录 B 一台设备的真机矩阵 (P5.1 / P5.2)

```
py -3.12 -I -u tools/augment/device_matrix.py -s SERIAL --out DIR --items --backup ORIG --install APK --test-apk TEST_APK
py -3.12 -I -u tools/augment/device_matrix.py -s SERIAL --out DIR [--items ITEM ...] [--cases DIR ...] [--surface-baseline TXT] [--probe-baseline JSON]
py -3.12 -I -u tools/augment/device_matrix.py -s SERIAL --out DIR --items --restore ORIG
```

依次运行各项, 每项一行通过 / 失败与关键数字, 各步骤的日志留在 DIR. `--backup` 拉取设备上的宿主与测试包并记录 md5, `--install` / `--test-apk` 再以 `install -r -d -t` 装上被测构建 (保留数据); `--restore` 装回备份并核对 md5, 删除设备上的 bench 目录. 项目:

- `layer`: `base64.encode()` 为脚本模块层参数检查的 TypeError (D10), 并报告设备上是否还存着 P5.2 删除的开关的值 (无害).
- `eval`: 每个用例文件 (默认 `app/build/script-modules-device`) 一个脚本, `device_eval.py --strict-errors`.
- `surface`: 一份表面快照 (`--extend-builtins on --roots Array.prototype Number.prototype --global-classes`, 写在 `DIR/surface/`); 给出 `--surface-baseline` (之前的快照 .txt) 时以 `surface_compare.py --allow-length` 对照, `KNOWN_SURFACE` 中的差异 (auto.stats 计时值) 单独计数, 其余为失败.
- `bootstrap`: `device_bootstrap_check.py --probe` (结果存为 `DIR/bootstrap-probe.json`, 给出 `--probe-baseline` 时与之对照), 以及 `--smoke coroutine listener suspend images automator`.
- `instrumented`: `tools/rhino/device_rhino_check.py` 的插桩用例加 `EXTRA_CLASSES` (须是同一构建的测试包).

P5.2 之前矩阵跑开关的两态: `default` (删除开关的存储值, 核对默认为开) 与 `eval-default` 即现在的 `layer` 与 `eval`; `eval-off` 以开关关运行同一批用例 (差异对照附录 E, 跳过 `OFF_SKIPPED` 中会操作设备的用例); `surface` 与 `bootstrap` 对照两态 (`KNOWN_SURFACE` 还包括 console 的全局函数名, files.batchRename.apply, @lookup.Image); `--backup` / `--restore` 还保存与恢复开关的存储值.

各工具把临时脚本写在 `tools/augment/__pycache__`, 多台设备同时运行时各用一份 `tools/augment` 与 `tools/rhino` 的副本 (`surface` 还读 `modules/global/GlobalJavaClasses.kt`). 不点按屏幕; 需要无障碍服务的用例 (automator) 在服务未开启时跳过.

## ci_timing_gate.py: 模拟器上的计时回归门禁 (P6.1)

```
py -3.12 -I -u tools/augment/ci_timing_gate.py -s emulator-5554 [--profile local-avd] [--calls NAME ...] [--no-install] [--apk-dir DIR] [--no-rerun]
py -3.12 -I tools/augment/ci_timing_baseline.py --profile local-avd RESULT.json RESULT.json RESULT.json [--write]
```

设计, 阈值的理由与基线的更新步骤见 `docs/augment/P6-1-ci-timing-gate.md`; 工作流 `.github/workflows/augment-timing-gate.yml` 只调用它. 在 API 35 x86_64 的模拟器上 (与 `memory-gate.yml` 同配方, 2 核) 测启动的 `runtime-init-prologue` 与 `error_path.js` / `bench/calls/*.js` 全部失败项对 Rhino 参照的比值, 与 `baseline/timing-gate.json` 中 `--profile` 档案比较: prologue 不超过基线 1.5 倍且不超过 60 ms, 每个比值不超过 max(1.2, 基线 x 1.35). 只测宿主的默认状态, 不读写新脚本模块层开关.

与本目录其他工具不同, 它会安装 debug 宿主 (`install -r -t -g`, 取 `--apk-dir` 下与设备 ABI 对应的分包) 并改设备设置: 放行 "所有文件访问", 关动画, 保持亮屏, 关闭 cached apps freezer (结束时恢复原值), logcat 缓冲设为 16 MiB. 因此只接受模拟器 (`ro.kernel.qemu` 或 `ro.boot.qemu` 为 1), 真机仍用 `device_r2.py`. 每个调用文件与 `calls_prelude.js` 之间插入一段 `GATE_SHIM`, 在每个失败项后紧跟一次 `undefined.x` 作为相邻参照 (不改共享的计时脚本). 一次全套约 17 分钟 (暖机 100 次, 2 轮 x 每个脚本 3 次, 启动计时每轮 10 次); 有检查未通过时只把相关脚本在新进程中重测一次, 每项取两次中较好的值.

输出 `CI_TIMING_*` 行: 每个未通过的检查一行 `CI_TIMING_PROBLEM` (over / missing / timeout, 附值, 基线与上限), 基线 `notes` 中的项各一行 `CI_TIMING_KNOWN`, 最后 `CI_TIMING_RESULT PASS|FAIL`; 退出码 0 / 1, 得出结论前出错为 2. 结果 JSON 写到 `--out` (默认 `build/augment-timing-gate`), 记录每次运行的原始数值, 也是 `ci_timing_baseline.py` 的输入; 其中的 `commit` 取 `--apk-dir` 所在仓库的 HEAD (CI 为 `GITHUB_SHA`). 本机测基线时用同配方的 AVD 跑全套至少 3 次 (`--no-rerun`), 机器同时有其他负载时把 `qemu-system-x86_64-headless` 的优先级调到高于正常.
