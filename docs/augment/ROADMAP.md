# Augment 重构路线图

更新日期: 2026-10-20

本文是 AutoJs6 宿主 "Augment 机制" (`app/src/main/java/org/autojs/autojs/runtime/api/augment/`, 以 Kotlin 实现并挂载到 Rhino 全局作用域的脚本 API 模块层) 深度重构的可执行状态表. 它以 2026-10-20 维护者经三轮选择题确认的 12 项决策 (第 2 节) 为依据, 记录现状证据 (第 1 节), 派生技术决策 (第 3 节), 阶段任务 (第 5 节) 与基线数据 (附录).

使用方式:

1. 每次会话开始时, 从第 5 节选取未完成条目, 按阶段顺序推进; 条目完成后勾选 `[x]` 并追加证据 (提交 hash / 测试类名 / 设备型号与 API / 计时数值).
2. 第 2 节固定决策不再重新讨论; 第 3 节派生决策可在标注的推翻点之前推翻, 之后视同固定; 附录 C 待决事项在进入对应阶段前由维护者拍板, 结果回填到第 2 节或第 3 节.
3. 工程约定以仓库 `AGENTS.md` 与 `../COMMON_AGENTS.md` 为准 (ASCII 标点, 构建号, 提交身份, 共享工作区). 本文只记录 "改什么" 与证据.
4. 脚本 API 变化须同步文档仓库与 d.ts 仓库 (见 `AGENTS.md` > 关联项目); 新实现切换默认值与删除旧实现各记一条 `.changelog` (10 种语言).

---

## 1. 现状与证据

### 1.1 机制由来

- 6.6.0 之前, 脚本 API 模块以 `assets/modules/__xxx__.js` 存在, 每个脚本启动时由 `init.js` 逐个 `require` 并执行工厂函数 (`module.exports = function (scriptRuntime, scope) {...}`), 没有跨脚本的编译缓存 (当时的 `SoftCachingModuleScriptProvider` 按引擎实例缓存).
- 提交 `153f642f29` (2024-12-02, 6.6.0 "内置模块重写") 一次性删除 45 个 JS 模块 (11384 行, 476 KB), 新增 90 个 Kotlin 文件 (19244 行), 基类 `Augmentable` 诞生. 当时的 changelog hint 写明 "内置模块使用 Kotlin 重新编写以提升脚本运行效率但可能需要多次迭代逐步完善". 旧 JS 保留在 `assets/modules/obsolete/` (已从 APK 排除), 是本次重构的差分基准.
- 2026-10-20 规模: 92 个 `Augmentable` 子类 (含 2 个 `AugmentableProxy`), 782 个 `@RhinoRuntimeFunctionInterface` 函数, 285 个 `@RhinoFunctionBody` 辅助函数, 221 个文件约 49600 行 (一半以上是 6.6.0 之后新增的 compose / flow / ai / mail / epub / gallery / speech 等模块). 仍以 JS 加载的只剩 promise, result-adapter, jvm-npm, continuation, internal, object-observe, array-observe, structured-clone, ui-ext (2026-07 起有进程级编译缓存).

### 1.2 当前机制要点

- 挂载: `ScriptRuntime.augment()` 在每个脚本的 `initPrologue` 中按固定顺序急切挂载全部 92 个模块, 每个函数创建一个 `LambdaFunction` (约 800 个函数对象外加 getter 与别名).
- 声明: 子类覆盖 `selfAssignmentFunctions` / `globalAssignmentFunctions` / `selfAssignmentGetters` 等 `List<Any>` 属性, 用 `Pair` 嵌套表达 名称 / 别名 / 标志; `Augmentable.normalizeFunctionAssignments` 在运行时解析 (约 100 行), 并用 `FUNCTION_ASSIGNMENT_CACHE` 快照比对缓存解析结果.
- 分发: 函数实现必须是 companion 中签名为 `(ScriptRuntime, Array<out Any?>)` 的 `@JvmStatic` 方法, 按名反射查找 (`Method` 已缓存), 调用链 `LambdaFunction -> Kotlin lambda -> Method.invoke -> RhinoUtils.toJsValue`.
- 值转换: 782 个函数各自手写 `coerceXxx` / `isJsNullish` / `NativeArray` 判断, 没有统一的参数规格.
- 异常: `installFunctions` 捕获全部 `Throwable`, 先 `printStackTrace`, 再拼接多语言消息包装为 `WrappedRuntimeException` (字符串), 原始类型丢失.
- Context: `withRhinoContext` 在非脚本线程以 `Context.enter()` 兜底, 不带引擎 `WrapFactory` (源码注释自认).
- 原型链: `augmentPrototypesAt` 硬编码最多 5 层, 直接改写传入 proto 对象的 `prototype`.

### 1.3 正确性证据

静态比对 (`tools/augment/inventory_diff.py`: 45 个旧 JS 的成员名 对 Kotlin 注册名, 再以 d.ts 公开声明过滤) 没有发现确定的整个方法被漏掉, 可疑项经核对都落在 Java 原型链或子模块上. 偏差集中在行为层, 6.6.0 之后的 314 条 fix 中 106 条直接点名被重写的模块, 其中可归因于手工翻译的典型模式 (issue 编号见 GitHub):

| 模式 | 实例 |
| --- | --- |
| 返回值转换错误 | #322 / #518 `dialogs.multiChoice` 返回字符串而非数组 (6.6.1 报告, 6.8.0 修复); #274 `ocr.detect` 返回 Java 对象; #435 `threads.disposable()` 取值被装箱 |
| 参数类型判断过严或错误 | #275 `click("文本", 1)` 失效; #310 shell / shizuku 不接受字符串数组; #508 `device.vibrate(300)` 失效; #462 `notice(title, content, options)` 拒绝普通对象; #402 Canvas 构造参数; 6.7.0 "images 各方法相似度 1 被当成百分比", "util.java.array 不接受 Boolean" |
| 成员或原型丢失 | #301 `notice.getBuilder` 缺失; #364 dialogs customView 失效; #399 `setOnTouchListener` 丢失; 6.6.2 "ui.inflate 返回值丢失 attr / on / click 等原型方法" |
| 线程与 Context 假设 | #295 "No Context associated with current Thread"; 6.6.2 "floaty.window 无法在子线程执行"; #345 "部分方法异常无法被 try/catch 捕获" |

augment 目录下现有 90 个单元测试文件 485 个用例, 几乎都属于 6.6.0 之后新增的模块; 6.6.0 迁移的老模块没有差分测试.

### 1.4 性能证据

实测见附录 A. 结论: Kotlin 路线本身是对的 (热态初始化 211 ms, 旧 JS 仅编译就要 510-590 ms, 还不含执行工厂函数; 单次调用开销与 Rhino 自带的 Java 桥接持平甚至更低), 真正的浪费在 "全量急切挂载" (老机型 324-461 ms, 进程冷启动首次 2.4 s) 与 "异常路径" (单次 4.9 ms, 对用 try/catch 探测的脚本是灾难).

### 1.5 仍以 JS 形式存在的模块 (`assets/modules`)

2026-10-20 盘点 (不含已从 APK 排除的 `obsolete/`). 只有第一类在脚本启动路径上, 它们的编译结果已有进程级缓存, 但每个脚本仍要执行一遍, 对应附录 A 的 `runtime-init-epilogue` 39-46 ms (G8441 58-80 ms).

| 类别 | 文件 | 加载时机 | 处置 |
| --- | --- | --- | --- |
| 启动必载 | `promise.js` (317 行, 兼容协程的定制 Promise) | `RhinoJavaScriptEngine.init` 定义全局 `Promise` | 值得 Kotlin 化但风险最高 (协程语义与微任务顺序), 单列 P4.2 评估; 也可考虑在 Rhino fork 的 NativePromise 上适配协程 |
| 启动必载 | `jvm-npm.js` (386 行, `Module` / `require` 实现) | `initEpilogue` | Kotlin 化 (基于 Rhino commonjs `Require` 定制路径解析, 缓存与模块隔离), 顺带修 issue #564 的 `const` / `let` 重复声明问题; P3.7 |
| 启动必载 | `internal.js` (`__asGlobal__`, `__exitIfError__`) | `initPrologue` 合入 global | 并入新基座, P2.5; 两者在 d.ts 中有声明 (`aj6-int-init.d.ts`), 名称保留 |
| 启动必载 | `continuation.js` (`continuation.await` / `delay`) | `initPrologue` 作为 Continuation 的原型 | 并入 Kotlin Continuation 模块, P2.5 |
| 启动必载 | `result-adapter.js` + `result_adapter.js` (旧名转发) | `RhinoJavaScriptEngine.init` 定义全局 `ResultAdapter`; images / automator / dialogs / UI 的 Kotlin 侧反向调用 | Kotlin 化 (已有 `ScriptPromiseAdapter` / `PromiseInterop` 可复用), P2.5; 旧名转发文件删除前核对无脚本 `require("result_adapter")` |
| 启动必载 | `object-observe-lite.min.js`, `array-observe.min.js`, `structured-clone.min.js` (polyfill, d.ts 有公开声明) | `initEpilogue` | 改为首次访问 `Object.observe` / `Array.observe` / `structuredClone` 时安装 (accessor), 或 Kotlin 实现; P2.5 |
| UI 时注入 | `ui-ext.js` (WebView JsBridge, 源自 Pro `__ui__.js`) | `UI.kt` 注入 WebView 时 | 可 Kotlin 化, 不在启动路径, 优先级低, P3.2 顺带 |
| 按需第三方库 | `axios/` (11 文件), `axios.js`, `cheerio.js` (611 KB), `dayjs/`, `dayjs.js`, `i18n.js` + `banana-i18n.js`, `pangu.min.js` + `pangu.js` + LICENSE, `lodash.js` | `Global.kt` 的 Supplier getter 首次访问或 `require` 时 | 不 Kotlin 化; 不影响启动, 只影响 APK 体积与目录数量; 是否迁入插件见附录 C Q5 |
| Node 兼容垫片 | `buffer.js`, `events.js`, `fs.js`, `os.js`, `path.js`, `process.js`, `nodejs.js` | `jvm-npm.js` 为 npm 包解析核心模块名时 | 不 Kotlin 化; 随 P3.7 决定去留 (附录 C Q6) |

---

## 2. 固定决策

以下决策由维护者于 2026-10-20 经三轮选择题确认, 后续阶段不再重新讨论.

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 平行新建后一次切换 | 保留 Kotlin 路线. 新实现在独立包中从零搭建, 旧 `augment` 包在切换完成前零改动; 两套实现并存期间由开关选择 (D8). |
| D2 | 先搭安全网再动基类 | 顺序: P0 安全网 (API 清单基线, 真机表面快照, JVM 差分测试, 真机计时基线) -> P1 新基座 -> P2-P4 逐模块迁移并修 bug. |
| D3 | 名称与行为全兼容 | 保留全部公开名称 (含 `$` 前缀别名与 legacy 名), 可枚举性与原型链形状不变; 行为以文档与 d.ts 为准修正偏差; 只允许错误消息文案变化. |
| D4 | 全部按需挂载 | 92 个模块在首次访问时才构建; `Object.keys(global)` / `hasOwnProperty` / `in` 对模块名的可见性与现在一致. 有副作用的模块按 T3 标注例外. |
| D5 | 注解 + KSP 生成 | 模块类用注解声明函数 / 属性 / 别名 / 全局标志, KSP 在编译期生成挂载表与按编号分发的调用代码, 零反射, 签名错误编译期报错. |
| D6 | 集中式参数规格 | 实现方法声明真实参数类型 (含重载), 生成层统一做强制转换, 可选值, 默认值与错误消息; 返回值统一归一化. 同一份元数据供 d.ts 与文档工具使用. |
| D7 | JVM 差分为主, 真机 smoke | 单元测试里在同一 Rhino 环境加载 obsolete JS 与新实现, 比对属性表 (名称 / 类型 / 可枚举 / 原型链) 与纯逻辑函数的输入输出; Android 依赖用 fake. 真机只跑启动计时, 表面快照与关键路径. |
| D8 | 隐藏开关逐步放量 | 新实现由设置项选择, 先默认关; 差分与真机通过后默认开; 下一个大版本删除旧实现. |
| D9 | 先纯逻辑, 再重型, 最后新模块 | 第一批 Arrayx / Mathx / Numberx / base64 / s13n / mime / colors / util 子项等可全 JVM 验证的模块; 第二批 images / dialogs / notice / automator / http / ui / app 等 bug 密集模块; 第三批 compose / flow / ai / mail / epub 等 6.6.0 后新增模块 (已有测试, 只换挂载层). |
| D10 | 保留原始错误类型 | 参数错误抛 JS TypeError / RangeError, 脚本抛出的错误原样透传, Java 异常保留类型只加 "方法 x 调用失败" 前缀; 不再默认 printStackTrace (调试开关可开); 异常路径目标 < 0.2 ms. |
| D11 | 记录位置 | 决策与进度记录在本文 (`docs/augment/ROADMAP.md`), D 编号决策与 P 编号阶段沿用插件仓库惯例. |
| D12 | 首个会话范围 | 2026-10-20 的会话落地 P0 安全网, 不改动现有 augment 代码. |

---

## 3. 派生技术决策

据第 2 节派生, 进入对应阶段前可推翻, 之后视同固定.

| 编号 | 决策 | 推翻点 |
| --- | --- | --- |
| T1 | 新包名 `org.autojs.autojs.runtime.api.modules` (生成代码在同包 `generated` 子包). 切换完成后旧 `augment` 包整体删除, 不改回旧名, `AGENTS.md` 中的路径同步更新. | P1.1 开始前 (附录 C Q1). |
| T2 | KSP 处理器放 `build-logic/ksp-script-modules-processor` (与现有 `ksp-version-codes-processor` 同级). 注解集: `@ScriptModule(name, aliases, dollarPrefix, eager)`, `@ScriptFunction(name, aliases, global, attributes)`, `@ScriptGetter`, `@ScriptProperty`, `@ScriptJavaClass`; 处理器输出: 每模块一个 `XxxBinding` (挂载表 + `when(id)` 分发 + 参数转换) 与一份 `script-modules.json` 元数据 (名称, 别名, 参数规格, 返回类型, 源位置). | P1.1 原型评审时可调整注解形状. |
| T3 | 按需挂载: 全局对象上以 accessor 定义每个模块名及其 `$` 别名, 首次访问构建模块对象并以 `defineProperty` 替换为 PERMANENT 数据属性 (两个名字指向同一对象); 可枚举性与现在一致. `@ScriptModule(eager = true)` 用于有启动副作用或被宿主内部直接持有的模块 (如 events, timers, continuation, console 代理), P2-P4 迁移时逐个标注. | P1.4 计时不达标 (R2) 时改为预生成挂载表 + 对象池. |
| T4 | 参数规格来自实现方法的真实 Kotlin 签名: 重载按 (参数个数, 类型) 生成分发; 需要原始 JS 值的参数声明为 `Any?` / `Scriptable`; `vararg` 表示可变参; 可选参数用 Kotlin 默认值; 返回 `Unit` 映射为 `undefined`, 其余经统一的 `toJsValue`. 类型转换表 (JS 值 -> Kotlin 类型) 只在生成层一处实现, 含 ConsString, Java 包装数值, NativeArray -> List / Array, NativeObject -> Map 等. | P1.2 原型评审. |
| T5 | 错误模型 (D10): 生成层把转换失败抛为 Rhino `TypeError` / `RangeError` (消息沿用现有多语言资源), 实现方法抛出的 `JavaScriptException` / `EcmaError` 原样透传, 其他 Java 异常保留类型, 由统一的 `ScriptErrorMapper` 加前缀; `ScriptInterruptedException` 原样; 调试打印由开发者选项控制. | P1.2 原型评审. |
| T6 | Context 纪律: 生成的调用代码直接接收 Rhino `Context` 与 `Scriptable scope`, 模块函数只在持有 Context 的脚本线程执行; 需要跨线程回调的模块经 `ScriptRuntime` 的派发器回到脚本线程; 新实现禁止 `withRhinoContext` 兜底进入 Context. | P3 迁移 floaty / dialogs / ui 时若发现必须在 UI 线程直接求值的场景, 为这些场景单独定义带 WrapFactory 的 Context 进入点. |
| T7 | 差分测试基础设施在 `app/src/test/java/org/autojs/autojs/runtime/api/augment/differential/`: `DifferentialRhino` (一个 Context 两个独立顶层作用域, 假 `GlobalAppContext`), `ObsoleteModuleHost` (在 JVM Rhino 中执行旧 JS 工厂函数并提供 `util` / `species` / `isNullish` / `android` 等 stub), `SurfaceDiff` (含原型链的成员表比对), `DifferentialCases` (同一组表达式两侧求值, 已记录的偏差按记录断言, 两侧都抛错时 P0 暂不比较错误类型). 现有 Kotlin 的错误包装会调用 `Context.getString`, 纯 JVM 的 android 桩拒绝它, 因此真实异常从 `System.err` 捕获; 触及 Android 静态初始化的函数 (如经 `StringUtils`) 标为 `unavailable(jvm)` 跳过, 由 `tools/augment` 真机复核. P0 以 "旧 JS 对 现有 Kotlin" 记录既有偏差 (附录 E), P2 起改为 "旧 JS 对 新实现" 并关闭错误类型容忍. | 无. |
| T8 | 开关 (D8): `Pref` 布尔项 (开发者选项 "使用新的脚本模块实现"), 默认关; `ScriptRuntime.initPrologue` 按开关二选一挂载. 不提供脚本级指令, 避免两套实现在同一进程混用. | P5 切换默认值时可删除开关. |
| T9 | 工具与基线: `tools/augment/` (真机计时, 表面快照, 清单比对, 用法见其 README), 基线文件放 `tools/augment/baseline/` 并入库. | 无. |
| T10 | 文档与 d.ts: P5 起 `tools/api-coverage/extract_exports.py` 与 android-dts-generator 改读 KSP 产出的 `script-modules.json`, 不再解析 Kotlin 源码. | P5 评审. |

---

## 4. 红线 (所有阶段必须遵守)

- R1 兼容 (D3): 公开名称, 别名, 可枚举性, 原型链形状不得改变; 任何模块在旧实现删除前都必须通过 JVM 差分与真机表面快照比对.
- R2 性能: XQ-AT72 热态 `runtime-init-prologue` <= 60 ms, 失败调用的异常路径 <= 0.2 ms, 成功调用不劣于附录 A 基线; 每个阶段结束用 `tools/augment/device_bench.py` 复测并记录.
- R3 共享工作区: 新建的 Kotlin 文件在编译通过前不放入 `app/src`; 放回前通知并立即编译.
- R4 文案: 代码注释, 文档, 错误消息只用 ASCII 标点; 错误消息继续走 `strings.xml` 多语言资源.
- R5 不动旧实现: P5 之前不修改 `augment` 包 (紧急 bug 修复除外, 且须同步到新实现).

---

## 5. 阶段任务

### P0 安全网

- [x] P0.1 (文档) 本文: 现状证据, 12 项决策, 派生决策, 红线, 阶段任务, 基线. 证据: 本文件.
- [x] P0.2 (工具) `tools/augment/`: `device_bench.py` (启动计时 + 旧 JS 编译计时 + 单次调用开销), `device_surface_snapshot.py` + `surface_snapshot.js` (真机 API 表面快照), `inventory_diff.py` (旧 JS 成员名 对 Kotlin 注册名, d.ts 过滤). 证据: 本次提交.
- [x] P0.3 (基线) `tools/augment/baseline/registrations.json` (Kotlin 注册清单, 由 `tools/api-coverage/extract_exports.py` 生成), `surface-<model>-<versionCode>.txt` (真机表面快照), 附录 A 计时表. 证据: 本次提交与附录 A.
- [x] P0.4 (测试) JVM 差分骨架 `ObsoleteModuleHost` + `SurfaceDiff` + `DifferentialCases`, 并让第一批纯逻辑模块的旧 JS (Arrayx / Mathx / Numberx) 在 JVM 中跑通, 记录旧 JS 对现有 Kotlin 的既有偏差. 证据: `app/src/test/java/org/autojs/autojs/runtime/api/augment/differential/` 的 `ArrayxDifferentialTest` / `NumberxDifferentialTest` / `MathxDifferentialTest` (6 个用例, `:app:testAppDebugUnitTest`), 既有偏差见附录 E, 其中 Mathx 的 fraction 折叠与 `median([])` 已在 XQ-AT72 复核.
- [ ] P0.5 (测试) 第一批其余模块 (base64 / s13n / mime / nanoid / converter / formatter) 的旧 JS 在 JVM 中跑通 (Android 依赖以 JS stub 遮蔽).

### P1 新基座

- [ ] P1.1 (宿主) KSP 处理器模块 `build-logic/ksp-script-modules-processor` 与注解集 (T2); 用一个示例模块验证生成的挂载表, 分发与元数据.
- [ ] P1.2 (宿主) 运行时基座 `org.autojs.autojs.runtime.api.modules`: 模块定义与按需挂载 (T3), 参数转换表 (T4), `ScriptErrorMapper` (T5), Context 纪律 (T6).
- [ ] P1.3 (宿主) `Pref` 开关与 `ScriptRuntime.initPrologue` 分支 (T8), 默认关.
- [ ] P1.4 (测试) 基座的 JVM 测试; 真机计时达到 R2 (prologue <= 60 ms, 异常路径 <= 0.2 ms), 否则按 T3 推翻点调整.

### P2 第一批: 纯逻辑模块

- [ ] P2.1 Arrayx / Mathx / Numberx / Jsox (含 Array / Math / Number 内置对象扩展).
- [ ] P2.2 base64 / s13n / mime / nanoid / cvt / fmt / isNullish / species / global 纯逻辑部分.
- [ ] P2.3 colors / Color / colors.theme; util 及 util.java / util.version / util.versionCodes / util.inspect / util.morseCode.
- [ ] P2.4 每个模块: 差分测试通过 (旧 JS 对 新实现), 表面快照一致, 既有 issue 中与该模块相关的偏差逐条修复并写测试.
- [ ] P2.5 启动必载 JS 收编 (第 1.5 节): `internal.js`, `continuation.js`, `result-adapter.js` (+ 旧名转发) 并入 Kotlin; 三个 polyfill 改为首次访问时安装或 Kotlin 实现; 目标 `runtime-init-epilogue` 只剩 `jvm-npm` 与 `promise`.

### P3 第二批: 重型模块

- [ ] P3.1 images / canvas / ocr (含 mlkit / paddle / rapid) / barcode / qrcode / yolo.
- [ ] P3.2 dialogs / notice (含 channel) / toast / console / floaty / ui.
- [ ] P3.3 automator / auto / selector / RootAutomator / events / keys / flow / toolkit.
- [ ] P3.4 app / autojs / device / engines / files (含 batchRename) / storages / shell / shizuku / sysprops / settings / power / tasks / WorkManager / threads / timers / continuation / plugins.
- [ ] P3.5 http / web / WebSocket / zip / sqlite / media / sensors / recorder / crypto / tts.
- [ ] P3.6 每个模块同 P2.4; 第二批额外要求真机 smoke (XQ-AT72 与 G8441 各一次).
- [ ] P3.7 `jvm-npm.js` Kotlin 化: 基于 Rhino commonjs `Require` 实现 AutoJs6 的路径解析 (相对路径, npm 目录, 核心模块名), 模块缓存与函数包装隔离, 修复 issue #564; 同时决定 Node 兼容垫片的去留 (附录 C Q6).

### P4 第三批: 6.6.0 之后新增模块

- [ ] P4.1 compose / flow 已在 P3.3 / ai / mail / installer / epub / gallery / terminal / speech (含 models) / opencc / pinyin / pinyin4j / mediainfo: 只换挂载层, 复用现有测试.
- [ ] P4.2 `promise.js` 评估: 对比 "Kotlin 重写定制 Promise" 与 "Rhino fork 的 NativePromise 适配协程" 两条路, 以协程示例 (`示例代码/协程`) 与 Flow 的 `promise.await()` 为验收; 结论回填本文.

### P5 切换与清理

- [ ] P5.1 开关默认开; 真机矩阵 (附录 B 设备) 跑 `tools/augment` 全套与现有设备测试; `.changelog` 记录.
- [ ] P5.2 删除旧 `augment` 包与 `assets/modules/obsolete/`; 删除开关; `AGENTS.md` 路径更新; `.changelog` 记录.
- [ ] P5.3 (文档) `tools/api-coverage` 与 android-dts-generator 改读 `script-modules.json` (T10); 文档仓库与 d.ts 仓库同步.

### P6 门禁

- [ ] P6.1 CI 增加启动计时与异常路径计时的回归门禁 (模拟器基线另测).
- [ ] P6.2 表面快照比对纳入 `prepush_check.py` 的快速检查.

---

## 附录 A 基线数据 (2026-10-20)

设备上安装的是 debug 宿主 (versionCode 5586, 6.8.0), 数值为 debug 包 + Rhino 解释模式; release 包预计更快, 但相对关系不变.

启动计时 (`runtime-init-prologue` = Kotlin 挂载 92 个模块与运行时初始化; 热态取 5 次):

| 指标 | XQ-AT72 (Snapdragon 865, Android 12) | G8441 (Snapdragon 835, Android 9) |
| --- | --- | --- |
| runtime-init-prologue (热) | 207-220 ms, 中位 211 | 324-461 ms, 中位 391; 进程冷启动首次 2385 ms |
| script-init | 0-1 ms | 0-1 ms (冷启动 10 ms) |
| runtime-init-epilogue (observe, structured-clone, jvm-npm) | 39-46 ms | 58-80 ms (冷启动 436 ms) |
| 旧 JS 模块仅编译 (45 文件 11384 行, 不执行工厂函数) | 冷 592 ms, 热 510-538 ms | 冷 1196 ms, 热 675-726 ms |

单次调用开销 (XQ-AT72, 20 万次均值, `tools/augment/bench/call_overhead.js`):

| 调用形式 | 每次 |
| --- | --- |
| 普通 JS 函数 | 11.5 us |
| Rhino 内置 Math.max | 10.9 us |
| Augment 全局函数 isNullish | 12.6 us |
| Augment getter device.width | 17.8 us |
| Augment 函数 device.isScreenPortrait() | 34.5 us |
| 同一 Java 方法经 Rhino 自带桥接 (原型链直接调用) | 42.5 us |
| 失败调用 device.vibrate() 无参 (抛出并捕获) | 4926 us |

## 附录 B 设备矩阵

| 序列号 | 设备 | Android / API | 用途 |
| --- | --- | --- | --- |
| QV710AF65F | Sony XQ-AT72 | 12 / 31 | 主计时基线, 表面快照 |
| BH900ASK9E | Sony G8441 | 9 / 28 | 低端机计时 |
| QV770340J7 | Sony XQ-DQ72 | 13 / 33 | smoke (debug 宿主在该机有 ART JIT 问题, 不做计时) |
| 968e9f18 | Xiaomi 23046RP50C | 15 / 35 | smoke |
| bek749scrwv4wo8h | Redmi 22120RN86C | 15 / 35 | smoke |

## 附录 C 待决事项

| 编号 | 事项 | 拍板时机 |
| --- | --- | --- |
| Q1 | 新包名是否采用 T1 的 `runtime.api.modules`, 还是切换完成后改回 `augment`. | P1.1 开始前 |
| Q2 | `$` 前缀别名在新实现中是否继续可枚举 (现状: 与主名同属性特性). | P1.2 |
| Q3 | Node / Bun 桥接 (`NodeBridgeProtocol` 等) 是否复用 `script-modules.json` 的参数规格生成客户端存根. | P5.3 |
| Q4 | 开发者选项中调试打印开关 (T5) 的粒度: 全局一个, 还是按模块. | P1.2 |
| Q5 | 按需第三方库 (axios, cheerio, dayjs, i18n, pangu, lodash, 约 850 KB) 是否迁出宿主 APK 到插件 (如 "Bundled Libraries" 插件), 或保留在 `assets/modules`. 不影响启动耗时. | P5 之前 |
| Q6 | Node 兼容垫片 (buffer, events, fs, os, path, process, nodejs) 在 NodeJs Runtime 插件已覆盖 npm 场景的前提下是否下线. | P3.7 |

## 附录 D 工具用法

见 `tools/augment/README.md`.

## 附录 E P0 差分测试发现的既有偏差 (旧 JS 对 现有 Kotlin, 2026-10-20)

第一批三个模块的差分测试 (`ArrayxDifferentialTest`, `NumberxDifferentialTest`, `MathxDifferentialTest`) 首轮结果, 标 "真机" 的已在 XQ-AT72 (debug 宿主 5586) 复核. 判定依据是 d.ts 与文档: 与文档一致的一方为准.

| 模块 | 表达式 | 旧 JS | 现有 Kotlin | 判定 |
| --- | --- | --- | --- | --- |
| Mathx | `sum([1.111, 2.222], 1)` (d.ts: `sum([1.01, 2.02], 2) // 3.03`) | 3.3 | 4.333 (真机) | Kotlin bug: `calcBy(f, args)` 把 `args` 整体当作一个可变参元素传入 (缺 `*` 展开), `args.size >= 2` 分支永远不会命中, fraction 被当作数值求和. 同样影响 avg / mean / var / std / cv / max / min (`avg([1, 2], 3)` 得 2, `var([1, 2, 3, 4], 2)` 得 1.04, `max([1.26, 5.55], 1)` 得 5.55, 均真机复核). P2.1 修复并补用例. |
| Mathx | `median([])` | NaN | 抛 InternalError "Empty list doesn't contain element at index -1" (真机) | Kotlin bug: 空数组未处理. P2.1. |
| Mathx | `sum([])` / `min([])` / `max([])` | NaN / Infinity / -Infinity | 0 / NaN / NaN | 文档写 `Mathx.sum(); // 0`, Kotlin 的 sum 与文档一致; min / max 的空数组语义文档未定义, 新实现沿用 Kotlin 并补文档. |
| Mathx | `logMn(10, 1000)`, `logMn(2, 10, 3)` | 2.9999999999999996, 3.3219280948873626 | 3, 3.322 | 旧 JS 的 fraction 判断写反 (只有 -1 才取整), 文档 (`logMn(3, 2187) // 7`, 默认 13 位) 与 Kotlin 一致. 保留 Kotlin 行为. |
| Numberx | `parsePercent(' 5 0 % ')` | 0.5 | NaN (真机) | 旧 JS 先去掉所有空白再匹配; Kotlin 只 trim 两端. 文档未定义内部空白, 按兼容承诺 D3 恢复旧行为 (P2.2). |
| Numberx | `clamp(5, 'x', 3)` | 3 (忽略非数值边界) | 抛 InternalError (真机) | 旧 JS 过滤 NaN 边界; Kotlin 拒绝. 文档未定义; 按 D3 恢复旧行为并在文档注明 (P2.2). |
| Numberx | `clamp('5', 1, 3)`, `toFixedNum('1.5', 0)` | 抛 TypeError | 3, 2 | Kotlin 对数值字符串做了强制转换, 比旧 JS 宽松; 保留 (对脚本无害), 文档注明. |
| Numberx | `check(1, '==', 1)`, `check(1, '<', '2')` | 抛 Error | true | Kotlin 多接受 `==` 运算符与数值字符串; 保留, 文档注明. |
| Numberx | `parseFloat(1.25, 2)` | 抛 TypeError (`in` 作用于非对象) | 0.25 | 旧 JS bug, Kotlin 正确. |
| Arrayx | `sortedByDescending(a, f)` 对输入的影响 | 原数组被就地排序 (调用了 `sortByDescending(arr)` 而非副本) | 原数组不变 | 旧 JS bug, Kotlin 符合 `sorted*` 系列的文档语义. |
| Arrayx / Numberx | `typeof Arrayx`, `Numberx('3')` | "function" (`Array.bind(Array)` / `Number.bind(Number)` 上挂成员, 可调用) | "object", 调用抛 TypeError (真机) | d.ts 的 `interface Arrayx` / `Numberx` 无调用签名, 可调用形态从未文档化; 新实现保持对象形态 (附录 C 不另立项). |
| 全部 | 参数错误的错误类型 | TypeError / RangeError / Error, `e instanceof TypeError` 成立 | 统一 InternalError (`WrappedRuntimeException`), `instanceof TypeError` 为 false (真机) | 即 D10 要解决的问题; P0 差分以 `tolerateErrorTypes = true` 暂不比较错误类型, P2 起关闭. |
| 全部 | 实现中触及 Android 静态初始化的函数 (如 `Numberx.padStart` / `parseRatio` 经 `StringUtils`) | 可在 JVM 比较 | JVM 中 `ExceptionInInitializerError`, 真机正常 | 差分 harness 以 `unavailable(jvm)` 跳过并提示用 `tools/augment` 真机复核; 新实现的纯逻辑模块必须 Android-free (与 toolkit core 同一做法), 这类跳过应归零. |
