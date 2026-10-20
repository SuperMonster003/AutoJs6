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
| 按需第三方库 | `axios/` (11 文件), `axios.js`, `cheerio.js` (611 KB), `dayjs/`, `dayjs.js`, `i18n.js` + `banana-i18n.js`, `pangu.min.js` + `pangu.js` + LICENSE, `lodash.js` | `Global.kt` 的 Supplier getter 首次访问或 `require` 时 | 不 Kotlin 化; 不影响启动, 只影响 APK 体积与目录数量; 迁入 Bundled Libraries 插件 (P4.3, 附录 C Q5 已拍板) |
| Node 兼容垫片 | `buffer.js`, `events.js`, `fs.js`, `os.js`, `path.js`, `process.js`, `nodejs.js` | `jvm-npm.js` 为 npm 包解析核心模块名时 | 不 Kotlin 化; 随第三方库迁入 Bundled Libraries 插件 (P4.3, 附录 C Q6 已拍板) |

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
| T1 | 新包名 `org.autojs.autojs.runtime.api.modules` (生成代码在同包 `generated` 子包). 切换完成后旧 `augment` 包整体删除, 不改回旧名, `AGENTS.md` 中的路径同步更新. | 已拍板 (2026-10-20, 附录 C Q1): 采用 `runtime.api.modules`, 不改回. |
| T2 | KSP 处理器放 `build-logic/ksp-script-modules-processor` (与现有 `ksp-version-codes-processor` 同级), 注解放同级的 `build-logic/ksp-script-modules-annotations` (纯 Kotlin, 包 `org.autojs.autojs.runtime.api.modules`; app 以 `compileOnly` 引用, 运行时不需要; 处理器以注解类取限定名, 两边不会漂移). 注解集 (P1.1 原型评审后的形状): `@ScriptModule(name, aliases, dollarPrefix, eager, parent)`, `@ScriptFunction(name, aliases, global, attributes)` (同名多个函数即重载), `@ScriptGetter(name, global, attributes)` (val / var / 无参函数, var 生成 setter), `@ScriptProperty(name, global, attributes)` (挂载时读一次的数据属性), `@ScriptJavaClass(name, type, global, attributes)` (可重复, 标在类上); 评审新增 `@ScriptInvoke` / `@ScriptConstruct` (模块对象可调用 / 可构造, 现有 43 个文件用到 Invokable / Constructable / Versatile) 与 `@ScriptPrototype` (模块对象的原型, 如 util / mime 以 Java 对象为原型), `@ScriptModule.parent` (子模块如 `util.inspect`). `global` 改为枚举 `ScriptGlobal { NONE, ALSO, ONLY }`, 对应现有的仅模块 / `AS_GLOBAL` / `globalAssignment*`; 属性特性用 `ScriptAttributes` 常量 (与 Rhino 位值一致, 测试校验). 处理器输出: 每模块一个 `generated.<类名>Binding` (挂载表 `ScriptModuleSpec` + `call(module, id, ...)` 的 `when(id)` 分发 + 参数转换, 不用反射) 与一份 `autojs6/script-modules.json` (名称, 别名, 挂载位置, 特性, 参数规格, 返回类型, 源码位置; APK 打包时排除). 签名错误 (不支持的参数类型, 非尾部默认值, 不可区分的重载, 非 public, 泛型参数, 重名) 编译期报错. P1.2 增补 (2026-10-20): `@ScriptModule(attributes, emitter)` (`attributes` 是模块对象各名称在目标上的属性特性, 默认 PERMANENT, 对应现有 `augment(target, additionalAttributes = READONLY)`; `emitter = true` 在原型链末尾附加事件发射器对象, 对应 `AsEmitter`), `@ScriptFunction(toStringRole = ScriptToString.NONE / LITERAL / FUNCTIONAL)` (对应 `AS_LITERAL_TO_STRING` / `AS_FUNCTIONAL_TO_STRING`, 只能用于名为或别名为 toString 的函数; FUNCTIONAL 在模块对象上附加 DONTENUM 的 `__augmented_custom_to_string__` 标记函数, 使 util.inspect 不把它当作自定义 toString, LITERAL 无运行时效果, 只进元数据), `@ScriptPrototype` 可返回 List 表示原型链 (近者在前, 对应 colors 的颜色表列表). 处理器另为每个编译单元生成注册表 `generated.<名称>` (实现 `ScriptModuleRegistry`: 顶层模块按名排序, 其后是子模块; KSP 选项 `scriptModules.registry`, 主源码默认 `ScriptModules`, 单元测试由 `app/build.gradle.kts` 为 UnitTest 的 KSP 任务设为 `TestScriptModules`), 生成的绑定或注册表已存在于 classpath (另一编译单元有同名类) 时编译期报错; 子模块的 parent 须在同一编译单元, 子模块不能 eager; 元数据增加 `attributes` / `emitter` / `toStringRole`. | P1.1 已评审 (2026-10-20), 之后增减注解参数在本行记录. |
| T3 | 按需挂载 (P1.2 实现, `ScriptModuleMounter`): 顶层模块的每个名称 (名称, 别名, `$` 形式) 以及模块挂到全局对象上的函数 / 数据属性 / Java 类, 在全局对象上定义为惰性数据槽 `org.mozilla.javascript.LazyDataSlot` (宿主 `app/src/main/java/org/mozilla/javascript/`, 因槽表是 Rhino 包内可见的而放在该包, 先例 `ContextWrapper`): 属性从一开始就是带最终特性的数据属性, `Object.keys` / `hasOwnProperty` / `in` 不触发构建, 读取值或描述符时构建一次并缓存在槽内 (此后与普通数据属性同价, 所有名称指向同一对象), 描述符返回构建后的值而不是访问器; 首次读取前对可写名称赋值则不再构建. 未采用 accessor 加 `defineProperty` 替换: 描述符在首次读取前会显示访问器, 且 PERMANENT 属性不能再重定义; 未采用 Rhino 自带的 `LazyLoadSlot`: 其描述符会暴露待构建的 `LazilyLoadedCtor` (JSON / Math 同样如此, JVM 实测), 初始化还要求目标是 VarScope. 全局 getter 是只创建 Kotlin 模块实例的访问器式属性. 子模块 (`parent`) 随父模块对象一起构建并挂到父对象上, 其全局成员随顶层模块一起安装. `@ScriptModule(eager = true)` 在安装时构建, 用于有启动副作用或被宿主内部直接持有的模块 (如 events, timers, continuation, console 代理), P2-P4 迁移时逐个标注. `$` 前缀别名 (`dollarPrefix`) 为 Auto.js Pro 兼容而保留 (附录 C Q2), 与主名同样按需定义, 属性特性与主名相同 (`@ScriptModule.attributes`, 基线: 可枚举, 可写, 不可配置). 函数对象的 `length` 取签名的必需参数个数 (现有 Kotlin 为 0), 不属于 E/W/C 位, 见附录 C Q9. | P1.4 计时不达标 (R2) 时改为预生成挂载表 + 对象池. |
| T4 | 参数规格来自实现方法的真实 Kotlin 签名: 重载按 (参数个数, 类型) 生成分发; 需要原始 JS 值的参数声明为 `Any?` / `Scriptable`; `vararg` 表示可变参; 可选参数用 Kotlin 默认值; 返回 `Unit` 映射为 `undefined`, 其余经统一的 `toJsValue`. 类型转换表 (JS 值 -> Kotlin 类型) 只在生成层一处实现, 含 ConsString, Java 包装数值, NativeArray -> List / Array, NativeObject -> Map 等. P1.1 原型 (`ScriptArgs`) 的规则: 计数时尾部多余的 `undefined` 视为省略 (不少于最少必需个数, 因此 `inspect(undefined)` 仍是一个参数); 按个数选重载, 个数相同时按参数类型判别 (数值类型共用一个判别, 无法区分时编译期报错; 全部参数为原始值的候选排最后); 类型为 `Context` / `VarScope` 的参数由生成层注入, 须在脚本参数之前; 字符串参数按 JS ToString (修正附录 E 中 base64 的 Kotlin toString 偏差); 数值参数接受数值, Java 包装数值与数值字符串 (保留现有 Kotlin 的宽松); Int / Long 要求整数, 越界或带小数抛 RangeError; 布尔参数不做真值转换; 多余参数按签名拒绝并抛 TypeError, 需要宽松的函数以 `vararg` 声明 (附录 C Q7 已拍板, P1.2 已实现); 错误消息经 `ScriptMessages` 取字符串资源并以函数名为前缀, 期望类型写作脚本类型名 (string, number, boolean, Array, Object, byte[] / number[]) 或 Java 类名 (T5). | P1.2 原型评审. |
| T5 | 错误模型 (D10, P1.2 实现): `ScriptErrorMapper` 作用于模块函数, getter, setter, 构造函数与原型读取抛出的一切: Rhino 异常原样透传 (脚本抛出的 `JavaScriptException`, 包括模块调用的回调; 生成层参数转换的 `TypeError` / `RangeError` 等 `EcmaError`); `ContinuationPending`, `ScriptInterruptedException` (原因链中有中断时转为它) 与 `VirtualMachineError` 原样; 其他 Java 异常包装为 `ScriptCallException` (`WrappedException` 子类), 脚本以 `JavaException` 捕获, `e.javaException` 是原始异常 (类型保留), "方法 x 调用失败" 前缀 (`error_failed_to_call_method_with_cause`) 写在 Rhino 异常的 details, 未捕获时由控制台显示 (Rhino 把 WrappedException 的 JS message 固定为 "类名: 消息", 见附录 C Q10). 消息经 `ScriptMessages` 取字符串资源: 复用 `error_failed_to_call_method_with_cause` 与参数个数的三个 plurals / range 字符串, 新增 `error_script_argument_cannot_be`, `error_script_argument_must_be_of_type`, `error_script_argument_must_be_integer`, `error_script_no_overload_accepts_arguments`, `error_unknown_charset` (10 种语言, ASCII 标点); 没有 Android Context 时 (JVM 测试) 用与默认资源一致的英文回退 (`ScriptMessagesTest` 校验回退与默认资源一致, 新资源 10 种语言齐全且占位符一致). 不默认打印堆栈; 调试打印按附录 C Q4: `ScriptErrorMapper.debugPrinting` 策略, 总开关 (`key_$_script_module_debug_print`, 默认关) 加不打印的顶层模块列表 (`key_$_script_module_debug_print_disabled_modules`, 默认空即全部开启, 模块名取自注册表 `moduleNames`, 子模块随顶层模块), 两项暂为隐藏偏好设置, 开发者选项界面见 P1.3. 开关开时的输出 (Q4 第二轮拍板): 映射 Java 异常时向脚本控制台打一条 D 级调试条目, 内容为 `[modules] 模块.函数: 异常类名: 消息` 加裁剪到 `org.autojs.autojs.runtime.api.modules` 包内的 Kotlin 帧 (其余帧折叠为 `... N more`), 不打印参数值; 脚本捕获了异常也照样打印; 同时写 logcat. 开关关时任何地方都不打印 (未捕获异常由引擎服务原有的 printStackTrace 进 logcat, 不受影响). 参数转换的 TypeError / RangeError 与脚本自身抛出的错误在两种状态下都不打印堆栈. 实现 (提交 `3c2f6ac85`): `ScriptErrorMapper.debugEntry` 生成条目, `ScriptModuleMounter` 把运行时的 `console` 作为 `DebugConsole` 传入, 以 `Log.DEBUG` 写入 (经全局控制台也进 logcat 的 GlobalConsole), 另以 `Log.d("ScriptModules", ...)` 写完整堆栈; 控制台或 logcat 出错不会替换原错误. 证据: `ScriptModuleMounterTest.debugPrintingWritesConsoleEntriesPerModule` (开关关无条目; 开时脚本捕获的 Java 异常恰好一条, 级别 D, 首行 `[modules] probe.fail: java.lang.IllegalStateException: boom`, 其余为模块包内帧与 `... N more`; TypeError 与脚本错误无条目; 被排除的模块无条目), 真机原文见附录 A. | P1.2 原型评审. |
| T6 | Context 纪律: 生成的调用代码直接接收 Rhino `Context` 与 `Scriptable scope`, 模块函数只在持有 Context 的脚本线程执行; 需要跨线程回调的模块经 `ScriptRuntime` 的派发器回到脚本线程; 新实现禁止 `withRhinoContext` 兜底进入 Context. | P3 迁移 floaty / dialogs / ui 时若发现必须在 UI 线程直接求值的场景, 为这些场景单独定义带 WrapFactory 的 Context 进入点. |
| T7 | 差分测试基础设施在 `app/src/test/java/org/autojs/autojs/runtime/api/augment/differential/`: `DifferentialRhino` (一个 Context 两个独立顶层作用域, 假 `GlobalAppContext`), `ObsoleteModuleHost` (在 JVM Rhino 中执行旧 JS 工厂函数并提供 `util` / `species` / `isNullish` / `android` 等 stub), `SurfaceDiff` (含原型链的成员表比对), `DifferentialCases` (同一组表达式两侧求值, 已记录的偏差按记录断言, 两侧都抛错时 P0 暂不比较错误类型). 现有 Kotlin 的错误包装会调用 `Context.getString`, 纯 JVM 的 android 桩拒绝它, 因此真实异常从 `System.err` 捕获; 触及 Android 静态初始化的函数 (如经 `StringUtils`) 标为 `unavailable(jvm)` 跳过, 由 `tools/augment` 真机复核. P0 以 "旧 JS 对 现有 Kotlin" 记录既有偏差 (附录 E), P2 起改为 "旧 JS 对 新实现" 并关闭错误类型容忍. P0.5 增补: (1) 单元测试 classpath 上的 Android 框架类 JVM fake (`app/src/test/java/android/util/Base64.java`, `android/graphics/Point.java`, 行为照 AOSP), 测试类排在 mockable android.jar 之前, 两侧共用同一实现, 差异只可能来自模块代码 (只用 JS 对象遮蔽只能覆盖旧 JS 一侧, Kotlin 一侧仍会 not mocked); 没有 fake 的类 (如 `android.graphics.Color`) 照旧跳过. (2) Context 的 WrapFactory 照引擎 (`AndroidContextFactory.WrapFactory`) 把 Java String 交给脚本为 JS 字符串. (3) 渲染时 not mocked 的 Android 调用记为 `unavailable(jvm)`, 只为构造错误消息而查资源失败的记为 `throws MessageLookup`. (4) `SurfaceDiff` 计入原型链上 Java 对象的成员 (util / mime 以 Java 对象为原型). (5) `DifferentialCases` 把已记录偏差与跳过的表达式导出到 `app/build/augment-differential/*.json`, `tools/augment/device_eval.py` 用已安装的宿主在真机上求值同一批表达式, 确认 JVM 上记录的 Kotlin 值. | 无. |
| T8 | 开关 (D8, P1.2 实现): `Pref.isNewScriptModuleLayerEnabled` (键 `key_$_new_script_module_layer`, 默认关); `ScriptRuntime.augment()` (initPrologue 内) 每个运行时读取一次: 关闭时 mounter 为 null, 原 augment 调用不变; 打开时 `ScriptModuleLayer.MIGRATED` 中的模块 (P1.2 只有 base64) 在原调用位置由 `ScriptModuleMounter` 从 `generated.ScriptModules` 按需挂载 (`Object.keys(global)` 顺序不变), 其余模块仍走旧 augment. 开发者选项 "使用新的脚本模块实现" 的界面入口在 P1.3, 此前为隐藏开关 (`tools/augment` 的 `--new-modules on|off` 经 `Pref.putBooleanSync` 临时设置并恢复). 不提供脚本级指令, 避免两套实现在同一进程混用. | P5 切换默认值时可删除开关. |
| T9 | 工具与基线: `tools/augment/` (真机计时, 表面快照, 清单比对, 用法见其 README), 基线文件放 `tools/augment/baseline/` 并入库. | 无. |
| T10 | 文档与 d.ts: P5 起 `tools/api-coverage/extract_exports.py` 与 android-dts-generator 改读 KSP 产出的 `script-modules.json`, 不再解析 Kotlin 源码. | P5 评审. |
| T11 | 插件提供 JS 模块源 (附录 C Q5 / Q6): 新的宿主能力, 插件在清单中声明 `scriptModules` (模块名, 别名, 文件, 分组), 宿主的 `require` 解析与 `Global` 的按需 getter 在本地未命中时查询已启用插件 (经插件中心已缓存的清单, 不唤醒插件进程; 文件经 ContentProvider 读取一次后按插件版本缓存), 插件设置页按模块启用 / 禁用 (默认启用); 未安装或已禁用时 `require` 抛出带安装指引的 Error (指向插件中心条目). 与 P3.7 的 Kotlin `Require` 共用同一解析器. | P4.3 设计评审. |

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
- [x] P0.5 (测试) 第一批其余有旧 JS 的模块 (base64 / s13n / mime / util 的纯逻辑部分: format, inspect, 类型判断与字符串辅助) 的旧 JS 在 JVM 中跑通并记录既有偏差; nanoid / cvt / fmt 是 6.6.0 之后新增, 没有旧 JS, 不做差分 (P2.2 以现有单元测试对比新实现). 证据: `Base64DifferentialTest` / `S13nDifferentialTest` / `MimeDifferentialTest` / `UtilDifferentialTest` (连同 P0.4 共 7 个测试类 14 个用例, `:app:testAppDebugUnitTest`), harness 增补见 T7; 全部已记录的 Kotlin 值 (含 P0.4) 经 `tools/augment/device_eval.py` 在 XQ-AT72 (debug 宿主 5586) 复核, 73 条一致; 偏差见附录 E, 跨模块的多余参数问题见附录 C Q7. 提交 `5e72169ec`.

### P1 新基座

- [x] P1.1 (宿主) KSP 处理器模块 `build-logic/ksp-script-modules-processor` 与注解模块 `build-logic/ksp-script-modules-annotations` (T2, 最终形状见第 3 节; 包名按 T1, 附录 C Q1 未推翻); app 接入 (`compileOnly` 注解, `ksp` / `kspTest` 处理器, `scriptModules.projectDir` 参数, APK 排除元数据); 最小运行时契约 `org.autojs.autojs.runtime.api.modules` (`ScriptModuleBinding` / `ScriptModuleSpec`, 转换表原型 `ScriptArgs`, 急切挂载原型 `ScriptModuleMounter`), 未接入 ScriptRuntime, 无开关. 示例: 主源码的 `modules/base64/Base64Module` (新实现, 已修正附录 E 的两处 base64 偏差) 与测试夹具 `modules/sample/SampleModule` (覆盖全部注解与参数种类). 生成文件示例: `app/build/generated/ksp/appDebug/kotlin/org/autojs/autojs/runtime/api/modules/generated/Base64ModuleBinding.kt`, `app/build/generated/ksp/appDebug/resources/autojs6/script-modules.json`, 测试侧 `app/build/generated/ksp/appDebugUnitTest/kotlin/.../generated/SampleModuleBinding.kt`. 证据: `ScriptModuleBindingPrototypeTest` (5 个用例), `:app:assembleAppDebug` 通过; 编译期报错 (不支持的类型, 非尾部默认值, 不可区分的重载, 非 public, 泛型参数, 重名) 在 scratch 工程以同一处理器验证. 留给 P1.2: 按需挂载 (T3) 与 ScriptRuntime 接入; 全部绑定的索引 (按需挂载需要遍历; 主源码与测试各自生成时要避免同名, 原型未生成); 子模块挂载 (`parent` 已写入元数据, 挂载器未用); `AS_LITERAL_TO_STRING` / `AS_FUNCTIONAL_TO_STRING` 与 AsEmitter 原型链的注解表达; 错误消息资源化. 提交 `68a401d00`.
- [x] P1.2 (宿主) 运行时基座 `org.autojs.autojs.runtime.api.modules` 与 ScriptRuntime 接入 (范围含原 P1.3 的开关本体与 initPrologue 分支): 按需挂载 (T3, `ScriptModuleMounter` + 宿主侧 `org.mozilla.javascript.LazyDataSlot`); 注册表 (处理器为主源码 / 单元测试分别生成 `generated.ScriptModules` / `generated.TestScriptModules`, 同名类编译期报错); 子模块挂到父模块对象; `@ScriptModule(attributes, emitter)` 与 `@ScriptFunction(toStringRole)` (T2); 错误消息资源化 (`ScriptMessages`, 10 种语言 5 条新资源) 与 `ScriptErrorMapper` / `ScriptCallException` (T5); 调试打印策略 (附录 C Q4, 隐藏偏好设置); 隐藏开关与 `ScriptRuntime.augment()` 分支 (T8, `ScriptModuleLayer`, 开启时只有 base64 走新层, 旧 augment 包未改动, R5). Context 纪律 (T6): 生成层只在 Rhino 调用传入的 Context 中执行, 新包内没有 `withRhinoContext`; 模块对象创建不依赖当前 Context. base64 新模块的字符集表改为进程内建一次 (原先每次调用枚举 `Charset.availableCharsets()`, XQ-AT72 上约 16 ms). 证据: `ScriptModuleMounterTest` (11 个用例: 首次读取前名称可见且不构建, 首次读取只构建一次且各名称同一对象, 描述符特性, 全局 getter 只建实例, 子模块, toString 角色与原型链 / emitter, eager, 错误模型, 按模块调试打印, 映射规则, base64 对照真机表面基线 `surface-XQ-AT72-5586.txt` 的 E/W/C 位, 种类与成员), `ScriptMessagesTest` (3 个: 英文回退, 回退与默认资源一致, 10 种语言齐全), `ScriptModuleBindingPrototypeTest` (6 个, 改用注册表挂载, 导出 base64 用例到 `app/build/script-modules-device/`); `:app:testAppDebugUnitTest --tests "org.autojs.autojs.runtime.api.modules.*" --tests "org.autojs.autojs.runtime.api.augment.differential.*"` 20 + 14 个用例通过, `:app:assembleAppDebug` 通过, 全量 `:app:testAppDebugUnitTest` 774 个类 4775 个用例 0 失败 (26 个跳过). 真机 XQ-AT72 (debug 宿主 5592): `device_eval.py --cases app/build/script-modules-device --new-modules on --strict-errors` 15 条一致 (Q4 补做 `3c2f6ac85` 后新增一条 Java 异常用例, 复核 16 条一致; 开关关闭时同一批 10 条不同, 即附录 E 的 base64 偏差与 D10 错误类型, 证明开关生效); 开关开 / 关时 `Object.keys(global)` 的数量, 顺序 (哈希) 与 base64 的位置, 描述符, 原型, 成员一致, 只有 `base64.encode.length` 0 -> 1 (附录 C Q9); 计时见附录 A 的 P1.2 复测 (开关开 prologue 中位 179 ms, 关 190 ms, 均为本机同一安装暖机后; R2 的 60 ms 目标在全部按需挂载后才适用). 留给 P1.3 / P1.4: 开发者选项界面 (见 P1.3); 异常路径 0.54 ms 仍高于 R2 的 0.2 ms (同机 Rhino 自身 TypeError 约 0.22-0.30 ms, 见附录 A), P1.4 评估; 附录 C Q8-Q10 待拍板. 提交 `a0a1d1d52`.
- [ ] P1.3 (宿主) 开发者选项界面 (开关本体与 initPrologue 分支已在 P1.2 完成): "使用新的脚本模块实现" (T8, 键 `key_$_new_script_module_layer`), 调试打印总开关与按模块列表 (附录 C Q4, 键 `key_$_script_module_debug_print` / `key_$_script_module_debug_print_disabled_modules`, 模块名取自 `ScriptModules.moduleNames`); 两项默认关 / 全开, 切换后对新启动的脚本生效.
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
- [ ] P3.7 `jvm-npm.js` Kotlin 化: 基于 Rhino commonjs `Require` 实现 AutoJs6 的路径解析 (相对路径, npm 目录, 核心模块名), 模块缓存与函数包装隔离, 修复 issue #564; 核心模块名与第三方库名的解析交给 T11 的插件模块源 (附录 C Q6 已拍板: 垫片随 P4.3 迁入插件).

### P4 第三批: 6.6.0 之后新增模块

- [ ] P4.1 compose / flow 已在 P3.3 / ai / mail / installer / epub / gallery / terminal / speech (含 models) / opencc / pinyin / pinyin4j / mediainfo: 只换挂载层, 复用现有测试.
- [ ] P4.2 `promise.js` 评估: 对比 "Kotlin 重写定制 Promise" 与 "Rhino fork 的 NativePromise 适配协程" 两条路, 以协程示例 (`示例代码/协程`) 与 Flow 的 `promise.await()` 为验收; 结论回填本文.
- [ ] P4.3 (双仓) Bundled Libraries 插件 (附录 C Q5 / Q6 已拍板): 新建 `AutoJs6-Plugin-Bundled-Libraries` (官方插件 id `bundled.libraries`, 分类按插件中心惯例), 收纳 axios / cheerio / dayjs / i18n (banana-i18n) / pangu / lodash 与 Node 兼容垫片 (buffer / events / fs / os / path / process / nodejs) 两个分组, 设置页逐库开关 (默认启用); 宿主按 T11 接入, `assets/modules` 中对应文件在插件发布并登记索引后于 P5.2 删除; 验收: 插件安装 / 启用 / 禁用 / 卸载四态下 `require('axios')` 与全局 `axios` getter 的行为, 以及 `obsolete` 以外的 `assets/modules` 只剩启动必载文件.

### P5 切换与清理

- [ ] P5.1 开关默认开; 真机矩阵 (附录 B 设备) 跑 `tools/augment` 全套与现有设备测试; `.changelog` 记录.
- [ ] P5.2 删除旧 `augment` 包与 `assets/modules/obsolete/`; 删除开关; `AGENTS.md` 路径更新; `.changelog` 记录.
- [ ] P5.3 (文档) `tools/api-coverage` 与 android-dts-generator 改读 `script-modules.json` (T10); 文档仓库与 d.ts 仓库同步. 附录 C Q3 已拍板的两项: 生成 "桥接覆盖报告" (参数与返回全为原始值 / JSON 的函数中尚无 Node 入口者, 与 `NodeBridgeProtocol` 的方法名表比对, 产物放 `tools/augment/`), NodeJs Runtime 插件的 d.ts 生成 (`node-types.gradle.kts`) 改读 `script-modules.json` 的参数规格; 不生成桥接分发代码.

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

P1.2 复测 (2026-10-20, XQ-AT72, debug 宿主 5592, 安装后先暖机, 每组 8 次; 失败与成功调用为脚本内循环, 前 200 次预热):

| 指标 | 开关关 (旧 augment) | 开关开 (base64 走新模块层) |
| --- | --- | --- |
| runtime-init-prologue | 181-197 ms, 中位 190 | 169-199 ms, 中位 179 |
| runtime-init-epilogue | 35-45 ms, 中位 36 | 34-42 ms, 中位 36 |
| `base64.encode()` 失败调用 (参数个数) | 4512 us | 543 us |
| `base64.encode('a', 'no-such-charset')` 失败调用 | 93125 us | 483 us (字符集表缓存前 15911 us) |
| `base64.encode('hello')` 成功调用 | 37.9 us | 22.8 us |
| 参照: Rhino 自身 TypeError (`undefined.x`) / 脚本 `throw new TypeError` | 304 us / 115 us | 217 us / 105 us |

开关只替换了 base64, prologue 的差值在两次测量的波动范围内, 不代表按需挂载的收益; 首轮 (安装后未暖机) 开关关为 249-355 ms. 失败调用的主要成本在 Rhino 构造 EcmaError 与脚本侧错误对象 (同机 Rhino 自身 TypeError 已约 0.2-0.3 ms), 资源查找与包装次之.

P1.2 调试打印 (附录 C Q4 第二轮, 提交 `3c2f6ac85`; XQ-AT72, debug 宿主 5592, 新模块层开关开; 用 `device_eval.py --new-modules on --debug-print on|off --console` 运行两条脚本: `try { base64.decode('='); } catch (e) { ... }` (实现内 `android.util.Base64` 抛 IllegalArgumentException, 脚本捕获, 得到 `JavaException: java.lang.IllegalArgumentException: bad base-64`) 与 `try { base64.encode(); } catch (e) { ... }` (参数个数 TypeError)). 调试打印开时的脚本控制台原文 (每行前缀为控制台的时间与级别, TypeError 没有条目):

```text
11:29:19.194/D: [modules] base64.decode: java.lang.IllegalArgumentException: bad base-64
    at org.autojs.autojs.runtime.api.modules.base64.Base64Module.decode(Base64Module.kt:40)
    at org.autojs.autojs.runtime.api.modules.base64.Base64Module.decode$default(Base64Module.kt:38)
    at org.autojs.autojs.runtime.api.modules.generated.Base64ModuleBinding.f1(Base64ModuleBinding.kt:72)
    at org.autojs.autojs.runtime.api.modules.generated.Base64ModuleBinding.call(Base64ModuleBinding.kt:42)
    at org.autojs.autojs.runtime.api.modules.generated.Base64ModuleBinding.call(Base64ModuleBinding.kt:17)
    at org.autojs.autojs.runtime.api.modules.ScriptModuleMounter$Node.function$lambda$0(ScriptModuleMounter.kt:212)
    at org.autojs.autojs.runtime.api.modules.ScriptModuleMounter$Node$$ExternalSyntheticLambda10.call(D8$$SyntheticClass:0)
    ... 37 more
```

同时 logcat 的 `ScriptModules` 标签下有一条 `[modules] base64.decode` 及完整堆栈 (含 `android.util.Base64.decode` 与 Rhino 解释器帧). 调试打印关时同一脚本的控制台原文 (两条用例之间没有任何条目, logcat 也没有 `ScriptModules`):

```text
11:29:25.749/V: Running [$sdcard/Download/aj6-augment-bench/aj6_device_eval.js].
11:29:25.788/V: [$sdcard/Download/aj6-augment-bench/aj6_device_eval.js] finished in 0.032 seconds.
```

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
| Q1 | 新包名是否采用 T1 的 `runtime.api.modules`, 还是切换完成后改回 `augment`. P1.1 按 T1 实现 (注解, 运行时契约, 生成代码都在该包); 若改回, 在 P5.2 删除旧包后整体改名即可. | 已拍板 (2026-10-20, 维护者): 采用 `runtime.api.modules`. |
| Q2 | `$` 前缀别名在新实现中是否继续可枚举 (现状: 与主名同属性特性). | 已拍板 (2026-10-20, 维护者): 保留 `$` 前缀别名, 目的是兼容 Auto.js Pro 的 `$files` / `$device` 等写法 (基线 64 个顶层 `$` 别名, 属性特性与主名相同), 新实现照旧. |
| Q3 | Node / Bun 桥接 (`NodeBridgeProtocol` 等) 是否复用 `script-modules.json` 的参数规格生成客户端存根. | 2026-10-20 已向维护者详述现状 (宿主 `NodeBridgeProtocol.kt` 8933 行与 `NodeBridgeProviderRegistry.kt` 27 个 provider 的手写分发, 插件 `node_bridge_sources.cpp` 27918 行内嵌 JS facade, 73 个手写 d.ts; 三处各自维护, 能力面是按使用频度挑选的子集, 参数经 JSON 序列化, Java 对象以句柄传递, 每个 provider 受权限清单约束). 建议: 不用元数据生成桥接分发 (对象句柄, 回调, 权限与异步语义无法从签名推出), P5.3 只生成 "桥接覆盖报告" (参数与返回全为原始值 / JSON 的函数中哪些尚无 Node 入口) 供插件补齐, 并让插件的 d.ts 生成读取 `script-modules.json` 的参数规格以保持签名一致. 已拍板 (2026-10-20, 维护者): 按建议, 不生成桥接分发; P5.3 只出覆盖报告并让插件 d.ts 读参数规格. |
| Q4 | 开发者选项中调试打印开关 (T5) 的粒度: 全局一个, 还是按模块. | 已拍板 (2026-10-20, 维护者, 两轮): 按模块, 同时有全局总开关; 总开关 debug 与 release 构建都默认关, 开启后按模块默认全部开启; 开关开时控制台多打一条 D 级调试条目 (模块.函数, 异常类名与消息, 裁剪到模块包内的 Kotlin 帧, 不打印参数值), 形状见 T5. 现状形态 (旧 augment 把部分 Kotlin 堆栈拼进控制台错误消息, `e.name` 为 InternalError, `e.javaException` 为空) 经 XQ-AT72 实测后向维护者说明. P1.2 实现了运行时策略与两个隐藏偏好设置, 控制台条目已补做 (提交 `3c2f6ac85`, 真机原文见附录 A), 界面在 P1.3. |
| Q5 | 按需第三方库 (axios, cheerio, dayjs, i18n, pangu, lodash, 约 850 KB) 是否迁出宿主 APK 到插件 (如 "Bundled Libraries" 插件), 或保留在 `assets/modules`. 不影响启动耗时. | 已拍板 (2026-10-20, 维护者): 迁出到官方插件 "Bundled Libraries" (仓库 `AutoJs6-Plugin-Bundled-Libraries`), 插件设置中每个库可单独启用 / 禁用, 默认启用; 宿主侧机制见 T11, 任务见 P4.3. |
| Q6 | Node 兼容垫片 (buffer, events, fs, os, path, process, nodejs) 在 NodeJs Runtime 插件已覆盖 npm 场景的前提下是否下线. | 2026-10-20 已向维护者详述现状 (7 个文件共约 44 KB, 2026-06-14 随 `feat(node): add CommonJS compatibility modules` 加入; 只在 `jvm-npm.js` 解析 `require('path')` / `require('node:events')` 等核心模块名时从 classpath 读取, 不在启动路径; 无 d.ts, 无文档, 无示例, 更新日志未单列; 作用是让 Rhino 模式下通过 node_modules 加载的纯 JS npm 包能找到 events / path / buffer 等依赖, fs / process / os 只是部分模拟, 无 stream / child_process; `nodejs.js` 把 process / Buffer / setImmediate 写到全局). 建议: 与 Q5 同机制迁入 Bundled Libraries 插件作为 "Node 兼容垫片" 分组 (默认启用, 可逐个关闭), 宿主 `assets/modules` 不再保留; 不建议直接删除, 因为依赖它们的 Rhino 脚本会在 require 时静默失败. 已拍板 (2026-10-20, 维护者): 按建议, 随 Q5 迁入 Bundled Libraries 插件 (P4.3), 不删除. |
| Q7 | 多余参数: 旧 JS 忽略多传的参数, 现有 Kotlin 以 `ensureArgumentsLength*` 拒绝 (6.6.0 起, P0.5 差分在 base64 / s13n / mime 上均有体现). 新生成层 (T4) 是否继续按签名拒绝. 决定: 继续按签名拒绝 (6.6.0 起已是此行为, 能尽早暴露参数错误), 错误类型改为 TypeError (D10), 个别需要宽松的函数以 `vararg` 声明. | 已拍板 (2026-10-20, 维护者); P1.2 已按此实现 (生成层的参数个数错误为 TypeError, 改回宽松只需改 `BindingGenerator.appendDispatcher` 一处). |
| Q8 | `Object.keys(global)` 的顺序: 现状为 `ScriptRuntime.augment()` 的调用顺序 (基线 keys order 记录在 surface 文件头部), D4 / R1 只要求可见性. P1.2 让已迁移模块在原调用位置挂载, 顺序不变; 全部迁移后注册表按名排序, 要保持现有顺序就需要一份显式顺序表. 建议: 在 `ScriptModuleLayer` 保留一份与现 `augment()` 同序的模块名列表 (迁移期间也由它决定挂载位置), P5.2 删除旧实现时保留. | P2.4 之前 (第一批迁移完成, 表面快照比对前) |
| Q9 | 函数对象的 `length`: 新生成层取签名的必需参数个数 (`base64.encode.length` 为 1), 现有 Kotlin 一律为 0, 旧 JS 为形参个数 (2). 不属于 E/W/C 位, 但表面快照会显示差异. 建议: 采用签名推导的值 (与 d.ts 的必选参数一致, 也更接近 JS 语义), 表面快照比对时列为允许的差异. | P2.4 之前 |
| Q10 | Java 异常的 JS 消息: Rhino 为 `WrappedException` 生成的 JS 错误 message 固定为 "原始类名: 原始消息", 宿主无法加前缀. P1.2 把 "方法 x 调用失败" 前缀放在 Rhino 异常的 details (未捕获时控制台显示), 脚本捕获时 `e.javaException` 保留原始类型, 但 `e.message` 没有前缀. 替代做法: 包装成宿主异常类型 (message 带前缀, 但 `e.javaException` 不再是原始类型, 原始异常在 `getCause()`). 建议: 保持 P1.2 做法 (D10 的 "保留类型" 优先). | P1.4 |

## 附录 D 工具用法

见 `tools/augment/README.md`.

## 附录 E P0 差分测试发现的既有偏差 (旧 JS 对 现有 Kotlin, 2026-10-20)

第一批模块的差分测试首轮结果: P0.4 的 `ArrayxDifferentialTest`, `NumberxDifferentialTest`, `MathxDifferentialTest` 与 P0.5 的 `Base64DifferentialTest`, `S13nDifferentialTest`, `MimeDifferentialTest`, `UtilDifferentialTest`. 判定依据是 d.ts 与文档: 与文档一致的一方为准; 文档 (`AutoJs6-Documentation/api`) 与 d.ts 冲突时以文档为准并注明需同步的一方; 两者都未定义时按 D3 恢复旧行为, 除非旧行为本身是 bug 或 Kotlin 只是更宽松且无害. 标 "真机" 的已在 XQ-AT72 (debug 宿主 5586) 复核; P0.5 起全部已记录的 Kotlin 值 (表中 "现有 Kotlin" 列, 含 P0.4) 都经 `tools/augment/device_eval.py` 在同一设备复核 (73 条一致), 因此 P0.5 各行不再逐条标注. 表达式中的 `你好` 等字符在测试代码里以 `\uXXXX` 书写.

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
| base64 | `encode('你好', 'utf_16le')`, `encode('é', 'latin1')`, `encode('你好', 'GBK')`, `decode('xOO6ww==', 'GBK')` | 只认 6 个 StandardCharsets, 去掉 `\W+` 后比较 (下划线不会被去掉), 认不出时静默用默认字符集 (Android 为 UTF-8) | 按全部可用字符集及别名匹配, 忽略大小写与标点 | 文档写明 "字符集名称匹配忽略空白, 标点和大小写"; Kotlin 正确. |
| base64 | `encode('a', 'definitely-not-a-charset')`, `decode('aGk=', 'definitely-not-a-charset')` | 静默用默认字符集 | 抛出参数异常 | 文档写明 "未知字符集会抛出参数异常"; Kotlin 正确. |
| base64 | `encode({ toString() { return 'obj'; } })`, `decode({ toString() { return 'aGk='; } })` | 调用脚本的 toString | 调用 NativeObject 的 Kotlin toString, 实际编码 / 解码的是 "[object Object]" | Kotlin bug (d.ts 输入类型含 `{ toString(): string }`). P2.2 修复. |
| base64 | `encode(123)` | "MTIz" (即 "123") | "MTIzLjA=" (即 "123.0") | Kotlin bug: 装箱 Double 用了 Kotlin toString, 应按 JS ToString. P2.2. |
| s13n | `time(60, java.util.concurrent.TimeUnit.SECONDS, java.util.concurrent.TimeUnit.MINUTES)` | 1 | 抛 ClassCastException | Kotlin bug: 文档写明 fromUnit / toUnit 接受 TimeUnit, Kotlin 用 isJsObject 把 TimeUnit 当成选项对象再强转失败. P2.2. |
| s13n | `time(null)` | 0 (经 `Number(null)`) | 抛出异常 | 文档的 source 类型为 number / string / Date, 不含 null; 保留 Kotlin. |
| s13n | `point(1, null)` | Point(1, 0) | Point(1, 1) | 文档: 省略 y 时两个坐标都取 source; Kotlin 把 nullish 的 y 视为省略, 与其他 API 的 isJsNullish 惯例一致; 保留 Kotlin. |
| s13n | `color('#FF0000')` 等颜色转换 | - | - | 两侧都需要 `android.graphics.Color` (无 JVM fake), Kotlin 还把失败改写为自己的消息, JVM 上无法区分; 差分只覆盖转换前就失败的路径 (null / true / {}), 转换本身留给 P2.2 的真机 smoke. |
| mime | `String(mime('text/html; charset=UTF-8'))` | `parameters: { charset: 'UTF-8' },` | `parameters: '{ charset: 'UTF-8' }',` (多一层引号且不配对) | Kotlin bug, 恢复旧格式. P2.2. |
| mime | `JSON.stringify(mime('text/plain; a=1'))`, `Object.keys(mime('text/plain'))` | 普通 JS 对象, 字段可枚举, 可 JSON 序列化 | Java 对象: JSON.stringify 得到 toString 文本, Object.keys 列出 getClass 等 Java 成员 | d.ts 声明的是只读字段对象; 按 D3 恢复为 JS 对象 (或提供 toJSON 与可枚举字段). P2.2. |
| mime | `mime(123)` | 抛 TypeError | 把 123 转成 "123" 并解析为 "123/*" | d.ts 参数为 string; 恢复 TypeError. P2.2. |
| util | `inspect('a\'b')`, `inspect('a"b')` | `'a\'b'`, `'a"b'` | `'a'b'`, `'a'b'` | Kotlin bug: 单引号不转义, 双引号被替换成单引号 (内容被改). P2.3. |
| util | `inspect({ a: 1, 'b-c': 2, '1': 4 })` | 非标识符键用单引号 `'b-c'` | 双引号 `"b-c"` | 旧格式 (Node 旧版) 为单引号; Kotlin 漏了替换引号一步. P2.3. |
| util | `inspect(o, false, 0)`, `inspect(1, false, 2, true)` | 支持位置参数 (value, showHidden, depth, colors) | 最多 2 个参数且第二个须为选项对象, 否则抛出异常 | d.ts 声明了位置参数形式, 文档只写选项形式; 两种都接受不冲突, 恢复. P2.3. |
| util | `inspect()` | "undefined" | 抛出异常 | d.ts 中 value 可选; 恢复. P2.3. |
| util | `inspect(o, { depth: null })` | 不限深度 | 按默认 2 | 文档未定义, 旧行为同 Node 语义, 按 D3 恢复. P2.3. |
| util | 多行的嵌套值, `inspect(function () { })`, `inspect(Object.assign(/re/, { p: 1 }))` | 嵌套多行值前换行; `[Function]`; `{ /re/ p: 1 }` | 丢失换行; `[Function ()]`; `/re/` | 前两项是 Kotlin bug; 第三项文档未定义, 按 D3 恢复. P2.3. |
| util | `inspect(new Error('boom'))`, `inspect(Object.assign(new Error('e'), { code: 1 }))`, `format('x', new Error('e'))` | `[Error: boom]`, `{ [Error: e] code: 1 }`, `x [Error: e]` | `Error: boom`, `Error: e`, `x Error: e` | Kotlin 的 `formatError` 本有方括号形式, 但 "自定义 toString" 判断先命中; 恢复旧格式. P2.3. |
| util | `inspect({ inspect() { return 'custom'; } })` | 调用对象自身的 inspect | 不调用 | 文档现写 customInspect 默认 false (现有 Kotlin), d.ts 的 util.format 示例依赖旧默认 true; 以文档为准保留 Kotlin, d.ts 示例待同步. |
| util | `inspect(java.lang.Integer.valueOf(7))`, `inspect(Java int[2])` | `[JavaObject: 7]`, `[0, 0]` (Arrays.toString) | `7`, `[ 0, 0 ]` | 文档未定义; 旧格式能区分 Java 值与 JS 值 (typeof / === 不同), 按 D3 恢复. P2.3. |
| util | `inspect(java.lang.String)`, `inspect(new java.util.ArrayList())` | 抛出异常; `{}` | `class java.lang.String`; `[]` | 旧 JS bug (前者在自身的 JavaClass 分支之前读 getClass 抛错, 后者看不到 Java 集合元素); 保留 Kotlin. |
| util | `util.inspect.colors`, `util.inspect.styles` | undefined | 映射对象 | 文档写明的 getter; 旧 JS 把它们挂在未绑定的内部函数上, 脚本拿不到; 保留 Kotlin. |
| util | `format('%d', 1e21)` | "1e+21" | "1000000000000000000000" | %d 按数值转换, 应按 JS 数值转字符串; Kotlin bug. P2.3. |
| util | `isString(new java.lang.String('j'))` | false | true | 文档: "JavaScript 类型是否为 string"; 恢复 typeof 语义. P2.3. |
| util | `isError(Object.create(Error.prototype))` | true | false | d.ts 源码摘要含 `e instanceof Error`, Kotlin 缺这一支; 恢复. P2.3. |
| util | `isEmptyObject(Object.create({ a: 1 }))` | false (for-in 含继承属性) | true | 文档: "不含自有属性"; 保留 Kotlin. |
| util | `checkStringParam('abc', /b/)`, `checkStringParam({ toString() { return 'abc'; } }, 'abc')` | 支持 RegExp (按 source 搜索) 与带 toString 的对象 | 只认 kotlin.text.Regex (脚本无法传入), 抛 java.lang.Error; 对象被拒且消息为 "Param src must be non-nullish" | d.ts 声明 `string \| RegExp` 并有 RegExp 示例, 文档照抄了 Kotlin 的 Regex 与 "仅原始值"; 恢复 RegExp 与 toString 处理 (至少对兼容别名 checkStringParam), 修正文档. P2.3. |
| util | `toRegularAndCall(f, 1, 2)`, `toRegularAndApply(f, [1, 2])` | 可用 | 始终抛 ObsoletedRhinoFunctionException | 文档标为 ABANDONED (始终抛出); 保留 Kotlin; toRegularAndApply 的消息误写为 toRegularAndCall. |
| 全部 | 多余参数: `base64.encode('a', 'UTF-8', 'extra')`, `s13n.time(1, 'ms', 's', 'extra')`, `mime('text/plain', 'extra')` | 忽略 | 抛出参数个数异常 (`ensureArgumentsLength*`) | 跨模块问题, 附录 C Q7 已拍板: 新实现继续按签名拒绝并抛 TypeError. |
| 全部 | 参数错误的错误类型 | TypeError / RangeError / Error, `e instanceof TypeError` 成立 | 统一 InternalError (`WrappedRuntimeException`), `instanceof TypeError` 为 false (真机) | 即 D10 要解决的问题; P0 差分以 `tolerateErrorTypes = true` 暂不比较错误类型, P2 起关闭. |
| 全部 | 实现中触及 Android 静态初始化的函数 (如 `Numberx.padStart` / `parseRatio` 经 `StringUtils`) | 可在 JVM 比较 | JVM 中 `ExceptionInInitializerError`, 真机正常 | 差分 harness 以 `unavailable(jvm)` 跳过并提示用 `tools/augment` 真机复核; 新实现的纯逻辑模块必须 Android-free (与 toolkit core 同一做法), 这类跳过应归零. |
