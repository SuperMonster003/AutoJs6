# P4.2 `promise.js` 两条路评估

ROADMAP P4.2 (第 323 行): 对比 "Kotlin 重写定制 Promise" (路线 A) 与 "Rhino fork 的 NativePromise 适配协程" (路线 B), 以 `示例代码/协程` 与 Flow 的 `promise.await()` 为验收. 本文是评估结论, 不含实施. 原型只在隔离克隆 (HEAD `2f0663ba8`) 中, 补丁与原始数据在共享笔记 `augment-p42-files/`.

## 1. 现状

- 全局 `Promise` 两态都是 `assets/modules/promise.js` (319 行, taylorhakes/promise-polyfill 的 AutoJs6 改版): `RhinoJavaScriptEngine.init` 以 `ScriptRuntime.js_Promise` 定义为 READONLY / PERMANENT; 旧包 `Dialogs` / `Images` 直接用 `js_Promise`, 新层 `ResultAdapterFunction.promise` / `wait` 按全局名 `Promise` 取.
- 选它而不用 Rhino 自带 Promise 的原因 (`ScriptRuntime.js_Promise` 的注释 "与协程不兼容, 且会造成假死"), 本次在 fork 源码中确认: NativePromise 的反应进 `Context` 的微任务队列, 只有 `JSScript.exec` / `evaluate*` 结束时 `processMicrotasks`; 宿主以 `executeScriptWithContinuations` 运行脚本, timer 回调与续体恢复也都不排空这个队列, 反应永远不运行.
- 续体捕获 (`Context.captureContinuation`) 要求从最内层解释帧沿 `parentFrame` 到最外层都是解释帧, 且最外层是 `executeScriptWithContinuations` / `callFunctionWithContinuations` 的顶层帧. Kotlin 经 `callMethod` 调用 JS 函数会新开一个没有父帧的解释帧.

## 2. 语义对照表

"现状" 列为 `promise.js`; A / B 列为原型的 JVM 矩阵结果 (§4.1, 23 个场景), "同" 表示与现状逐字相同.

| 项 | 现状 (`promise.js`) | A (Kotlin) | B (NativePromise + fork 补丁) |
|---|---|---|---|
| then / catch 反应的调度 | `Promise._immediateFn` -> 全局 `setImmediate` (宏任务, 落在结算线程的 timer, 线程没有 timer 时落在脚本主 timer); 与 `setTimeout(fn, 0)` 按投递顺序交错 (A2: `timeout0, p1, immediate, p2`) | 同 (同样经 `_immediateFn` / `setImmediate`) | 宿主调度器同样投递到 timer: A1 / A2 / A3 同; 原生微任务语义 (参照列) 则为规范顺序 `p1, p2, timeout0, ...` |
| 采纳本实现的 Promise | 不经任务, 直接采纳 (状态 3): A4 `t1, adopted, t2` | 同 | 规范的两次任务: `t1, t2, t3, adopted` (晚 2 tick) |
| 外来 thenable | `then` 在 resolve 时同步调用 (A5 `then called, after resolve`) | 同 | 在任务中调用 (`after resolve, then called`) |
| finally | `constructor.resolve(cb()).then(...)`, 回调抛出覆盖原值 (B1) | 同 | 同 |
| executor / 反应抛出 | 脚本 catch 能捕获的异常 -> 拒绝; ContinuationPending 与非 Rhino 运行时异常穿过 (B2) | 同 (按 `Interpreter` 的同一判定) | 同 |
| Java 异常作为拒绝值 | JS catch 看到的包装对象 (`JavaException: java.lang.NumberFormatException ...`, B3) | 同 (`ScriptRuntime.wrapException`) | `Error: Wrapped java.lang.NumberFormatException ...` |
| 自解析 | TypeError "A promise cannot be resolved with itself." | 同 | TypeError "No promise self-resolution" |
| 未处理的拒绝 | 拒绝后一个 tick 仍无处理者时 `console.warn('Possible Unhandled Promise Rejection:', err)`, 之后再挂 catch 仍会运行 (B5) | 同 (`_unhandledRejectionFn` 可替换) | 无警告 (fork 有 `UnhandledRejectionTracker`, 需宿主另行轮询上报) |
| 非函数的 then 参数 | 透传 (B6) | 同 | 同 |
| 构造器表面 | `typeof` function, name Promise, length 1; 原型链不是 `Function.prototype` (没有 call / apply / bind); 静态方法与 `_immediateFn` / `_unhandledRejectionFn` 在构造器的原型对象上且可枚举; `Promise.prototype` 的 catch / then / finally / await / wait 可枚举; 无 `any` / `withResolvers` / `try`; 不带 new 调用 "Promises must be constructed via new", `new Promise(1)` "not a function" (C1) | 同 | 继承 `Function.prototype`, 方法不可枚举, 多出 `any` / `withResolvers` / `try` 与 `Symbol.species`, 没有 `_immediateFn` / `_unhandledRejectionFn`, 两条错误消息为 Rhino 的 |
| 实例表面 | 自有可枚举属性 `_state` / `_handled` / `_value` / `_deferreds`, `Object.prototype.toString` 为 `[object Object]` (C2) | 没有这 4 个自有属性 (状态在 Kotlin 字段); 其余同 | 无自有属性, `[object Promise]` (`Symbol.toStringTag`) |
| all / allSettled / race | 参数只要有 `length` 即按数组处理 (字符串可用), 否则以固定消息拒绝; `Promise.resolve(p)` 对本实现的实例返回原对象 (C3) | 同 | 走可迭代协议: 非可迭代参数的消息不同, 完成顺序随 thenable 的任务变化 |
| `await()` (协程) | `continuation.await(this)`: 在 `await` 处挂起, 结算后恢复; 拒绝时 popException (toast + 控制台) 后以错误值作为返回值, 不抛出 (K1, 既有行为) | 同 | 同 |
| `await()` 在循环中 / `continuation.await` / `delay` (K2) | 正常 | 同 | 同 |
| 其他线程结算 (K3, T1) | `setImmediate` 在没有 timer 的线程上落到主 timer, 回调都在脚本线程运行 | 同 | 同 (fork 补丁: 任务带作用域, 运行时取当前 Context; 无补丁时任务绑定结算线程的 Context, 只能在该线程运行) |
| Kotlin 调用 `await()` (K4, 即 Flow 的 `await()`) | **失败**: `FlowNativeObject.await` 经 `callMethod` 调 JS 的 `await`, 新开的解释帧不是续体顶层帧, 抛 IllegalStateException "Cannot capture continuation ...", 随后 `resume called without suspend` 的 popException 与一次未处理拒绝警告 | 正常挂起并返回值 (`await` 是 Kotlin, 从调用方帧捕获) | 正常 (同一 `await` 实现) |
| `示例代码/协程/协程HelloWorld` 的流程 (K5) | delay / setTimeout / `add(1, 2).await()` 依次输出 | 同 | 同 |
| then 回调内 `await()` (K6) | 失败 (回调经 timer 调用, 不是续体顶层), 既有限制 | 同 | 同 (消息文本不同) |
| `wait()` (W1 / W2) | `runtime.loopers.newSettlementLatch()` (Q26): 等待期间运行本线程 timer 上已到期的回调, UI 线程上仍未结算则 TypeError (Q50) | 同 | 同; 若改用原生微任务, latch 还须排空微任务, 否则永久阻塞 (原型首轮即卡死) |
| http Async 结算线程 (Q25) | 请求线程读完响应体后在 UI 线程结算, 反应落到 UI 线程的 timer | 同 (调度路径相同) | 同 (需 fork 补丁, 否则任务绑定 UI 线程上临时进入的 Context) |

## 3. 两条路的原型

接线 (只在克隆中, `runtime/api/modules/promise/P42PromiseRoute.kt`): 新层开关开时 `ScriptRuntime.js_Promise` 取原型, 关时仍为 `promise.js`, 因此旧包的 `Dialogs` / `Images` 与新层的 `ResultAdapter` 在同一状态下用同一个 Promise. 两条路共用 `PromiseSuspension.kt` (29 行): `await()` 把 `continuation.await(this)` 内联为 Kotlin, 从调用 `await()` 的帧捕获续体; `wait()` 照搬 Q26 的 latch 写法.

- 路线 A (`ScriptPromise.kt`, 353 行代码, 原文 319 行 JS): 逐步改写 `promise.js`. 状态在 Kotlin 字段, 反应经 `Promise._immediateFn` (默认直接调全局 `setImmediate`, 被脚本替换时调用替换值), 未处理拒绝经 `_unhandledRejectionFn`; 构造器的原型对象, 方法的可枚举性, length, 错误消息都按原模块. 全局名 `setImmediate` / `console` / `continuation` / `runtime` 在使用时解析, 同原模块. 不依赖 fork 的 NativePromise, 只用宿主已在用的 Rhino API (`BaseFunction`, `LambdaFunction`, `ScriptRuntime.newObject` / `wrapException`).
- 路线 B (`NativePromiseSupport.kt` 27 行 + 接线约 15 行 + fork 补丁 19 行): fork 补丁 (`augment-p42-files/fork-nativepromise.diff`) 给 `Context` 增加 `enqueueMicrotask(VarScope, Runnable)` 重载 (默认转到原方法), NativePromise 的 5 处入队改为带作用域, 任务运行时取 `Context.getCurrentContext()` 而不是入队时的 cx. 宿主 `AutoJsContext` 覆盖该重载, 按作用域找到脚本, 像 `setImmediate` 一样投递到当前线程的 timer (没有则主 timer), 在其中进入 Context 运行, ContinuationPending 不算失败 (Q23). `await` / `wait` 以不可枚举方法加到 `Promise.prototype`. 补丁是必需的: 没有它, 在 binder 线程 (tts 的 `toJsPromise`), UI 线程 (http Async, Q25) 或 Java 线程上结算时, 任务捕获的是结算线程上临时进入的 Context, 无法改到脚本线程运行, 只能留在结算线程, then 回调就会与脚本线程并发.
- B 的参照变体 (只在 JVM): 保留原生微任务, 在每次顶层调用结束时排空. 顺序符合规范, 但与现状的差异更多 (§4.1), 且 `wait()` 的 latch 也要排空微任务.

JVM 框架 (克隆 `app/src/test/.../modules/promise/PromiseEvalHarness.kt`): 一个脚本线程, 虚拟时间的 timer (setImmediate / setTimeout 按到期时间再按投递顺序, 同 `core/looper/Timer`), 真实 Rhino 续体 (`executeScriptWithContinuations`, 宿主的 `ContinuationPrototype`, resumeError 同 `Continuation.Result.handle`), 真实 `SettlementLatch`, `otherThread(fn)` 模拟没有 timer 的结算线程. `PromiseEvalMatrixTest` 跑 23 个场景, `PromiseEvalBenchTest` 是微基准.

## 4. 结果

### 4.1 JVM 语义矩阵 (23 个场景, `augment-p42-files/jvm-matrix.md`)

| | 与 `promise.js` 相同 | 不同 |
|---|---|---|
| A | 21 | 2: C2 (实例的 4 个自有属性), K4 (Kotlin 调用 `await()`: 现状失败, A 正常) |
| B | 13 | 10: A4, A5, B3, B4, B5, C1, C2, C3, K4 (B 正常), K6 (消息文本); 其中 A4 / A5 / C3 是反应时序, B5 是未处理拒绝的警告 |
| B 参照变体 (原生微任务) | 8 | 15: 另加 A2, A3, B1, W2, T1 (顺序与回调线程) |

协程验收项在三者上都通过: K1 (await 已结算 / 稍后结算 / 拒绝), K2 (循环中 await, `continuation.await`, `delay`), K3 (其他线程结算后 await), K5 (`协程HelloWorld` 的流程), W1 / W2 (`wait()`). Flow 的 `promise.await()` 对应 K4: 现状在 Kotlin 经 `callMethod` 调用 JS 的 `await` 时抛 "Cannot capture continuation ...", 两条路都修好了 (`await` 在 Kotlin 中直接从调用方帧捕获).

### 4.2 JVM 微基准 (us / op, 中位数, 解释模式 Rhino, HotSpot; `augment-p42-files/jvm-bench.md`)

| 用例 | `promise.js` | A | B |
|---|---|---|---|
| `Promise.resolve(i).then(f)` (成功路径) | 30.7 | 2.7 | 4.4 |
| `new Promise` + 3 次 then | 75.3 | 7.4 | 10.4 |
| `Promise.reject(e).catch(f)` (失败路径) | 39.6 | 9.4 | 8.5 |
| then 中抛出 + catch (失败路径) | 87.5 | 24.5 | 35.6 |
| `Promise.all` 10 个 | 721 | 21.0 | 61.6 |
| `wait()` 已结算 | 48.5 | 3.7 | 4.6 |
| 协程中 `await()` (挂起 + 恢复) | 217 | 63.3 | 25.7 |

两条路都比现状快一个数量级左右 (解释执行的 JS 换成 Kotlin); A 与 B 的差别在多数用例里不到 2 倍, 方向不一致.

### 4.3 真机 (G8441, Android 9, 2026-10-24 15:17-16:3x; 原始输出 `augment-p42-files/device-G8441-*.log`)

工具 `tools/augment/device_promise_check.py` (`--sample` 运行 `示例代码/协程/协程HelloWorld`, `--scenarios` 运行 `bench/promise_smoke/scenarios` 的 19 项, `--bench` 运行 `bench/promise_smoke/bench`), 关态即现状 `promise.js`, 开态即原型. 被测构建为克隆的 debug 5935: A (`6d300c33`), A2 = A 加下述 Flow 修复 (`1f824094`), B2 = B 加同一修复 (`09054c58`); 测后装回原包 (md5 `5e836c4d`).

- 场景: A / A2 开态 19 项与关态逐项相同 (A2 只有 K4 由失败变为通过); B2 开态与关态不同的是 A4 (`t1,t2,t3,adopted,t4`), A5 (`after,then`), 表面一行与未处理拒绝警告 (无), 与 JVM 矩阵一致. 跨线程结算 (threads 线程, Java 线程, `ScriptPromiseAdapter` 在 Java 线程 resolve) 的回调都回到脚本线程, 三者相同.
- Flow `promise.await()`: 现状 `flow.of(5).await()` 抛 "Cannot capture continuation ..." (`flow.of(6).toPromise().await()` 正常, 因为是脚本直接调用 `await`). A 的开态捕获成功, 但又暴露第二处缺陷: `augment/flow/FlowNativeObject` 的方法包装 `guarded` 把 ContinuationPending 包成 WrappedRuntimeException, 脚本的 catch 接住 InternalError 继续执行, 续体之后被恢复又从 `await` 处重跑一遍 (输出重复). A2 / B2 让 `guarded` 放过 ContinuationPending (1 行) 后通过. 因此 Flow `await()` 需要两处: Kotlin 中直接捕获的 `await` (A 或 B 都有), 以及旧包 `FlowNativeObject.guarded` 的 1 行修复 (见 Q97).
- `协程HelloWorld`: 开态 3 次通过 (A 两次, B2 一次); 关态 2 次失败, 都是最后一行 `read()` 报 "Method resume() should be called after suspend()": 示例在 `threads.start` 的线程里调用 `cont.resume(...)`, `rhino/continuation/Continuation.resumeWith` 在调用线程上先取 `pending`, 工作线程比主线程先到 `cont.await()` 时即抛出. 这是示例与续体的既有竞态, 与 Promise 无关 (开态的新层 threads / files 更快, 改变了竞态的输赢), 见 Q98.
- 计时 (us / op, 中位数, 协程项目中以 `await()` 等待队列里的反应跑完; 开态同时换了 timers 等新层模块, 差值不全来自 Promise):

| 用例 | 关态 `promise.js` | 开态 A | 开态 B2 |
|---|---|---|---|
| `Promise.resolve(i).then(f)` | 875 | 126 | 94 |
| `new Promise` + 3 次 then | 1991 | 468 | 219 |
| `Promise.reject(e).catch(f)` | 1079 | 327 | 109 |
| then 中抛出 + catch | 1544 | 454 | 196 |
| `Promise.all` 10 个 | 11380 | 1504 | 1042 |
| `wait()` 已结算 | 1647 | 321 | 403 |
| 协程中 `await()` 已结算 | 1741 | 676 | 830 |

  A 的原型每个反应都经脚本全局 `setImmediate` (新层 timers 的参数检查, bridges 调用), B 直接向 timer 投递 Runnable, 所以反应密集的用例 B 快 1.3-2.9 倍. A 在 `_immediateFn` 与全局 `setImmediate` 都未被脚本替换时同样可以直接投递 (替换后仍走原路径, 行为不变), 预计与 B 持平; 实施时作为 A 的一部分.
- 首版计时脚本用 `wait()` 等待, 关态每次操作约 1.1 ms: `SettlementLatch.takeDueCallback` 每取一个回调都扫描全部待运行回调 (`earliestPendingCallback` 的 filter + min), 几百个回调排队时是平方级. 普通脚本很少在排着几百个回调时调用 `wait()`, 记为观察, 不单列待决.

## 5. 影响与风险

| 项 | A | B |
|---|---|---|
| 代码量 | 新层约 380 行 Kotlin (原型 353 + 共用 29; 正式实现加注释与消息资源约 450 行), 测试约 400 行 (本次的 23 个场景转为差分测试, 加真机用例) | 宿主约 70 行 + fork 补丁 19 行; 若要补齐差异 (未处理拒绝上报, `_immediateFn`, 表面) 另需约 150 行, 时序差异 (A4 / A5 / C3) 无法在宿主侧补齐 |
| prologue (R2) | 无影响: `js_Promise` 在 `RhinoJavaScriptEngine.init` 中 `runtime-init-prologue` 计时块之后, `script-init` 之前创建, 不在任何计时段内; 原 `promise.js` 模块体每个引擎约 0.33 ms (附录 A P2.5), A 的构造器与 14 个方法对象估计 < 0.1 ms | 无影响 (原生 Promise 已由 `initStandardObjects` 建好, 只加 2 个方法) |
| 成功 / 失败路径 | JVM 比现状快 4-34 倍 (§4.2); 真机 (开态整体) 快 2.6-7.6 倍, 反应改为直接投递 timer 后预计与 B 持平 | JVM 快 2.5-12 倍; 真机快 4-11 倍 |
| 两态一致性 (开关开 / 关) | 21 / 23 项相同; 不同的 2 项是内部字段 (无文档) 与 Flow `await()` (开态修好, 关态仍失败, 见 Q97) | 13 / 23 项相同; 时序, 消息, 表面与未处理拒绝警告都随开关变化, 需逐项登记附录 E 并在 P5.1 切换时作为行为变化记入 changelog |
| R5 (不改旧包) | 不改; `js_Promise` 在 `ScriptRuntime` (宿主), 旧包 `Dialogs` / `Images` 经它自动用上新实现 | 同 |
| P5.2 (删旧包) | 删 `assets/modules/promise.js`, `ProcessWideAssetModuleScriptProvider` 强缓存集合中的 "promise.js" 与 `js_Promise` 的关态分支; 之后 `assets/modules` 启动必载只剩 `jvm-npm.js` (P3.7 交接清单也删它) | 同上, 另外 fork 补丁永久保留 |
| 与 Rhino fork 升级的耦合 | 低: 只用宿主已在多处使用的公开 API | 高: NativePromise 今年已随上游合并改动 3 次 (`0c5df6d08` 7 月, `d49b1f6bd` 8 月 new.target, `adc9958c1` 9 月), 每次合并都要重新应用补丁并复核时序; 上游对 Promise 的规范修正会直接改变 AutoJs6 脚本的行为 |
| 未来的 async / await 语法 | fork 目前不支持 (`docs/dev/accessibility-automation-roadmap.md`); 若将来支持, async 函数返回的是 NativePromise, 与全局 `Promise` (A) 并存, 需再评估 (thenable 互通不受影响) | 天然一致 |
| 规范符合度 | 维持现状的非规范行为 (宏任务, 同步 thenable, 缺 any / withResolvers) | 接近规范, 但宏任务调度仍非规范 (要规范顺序就得改 `wait()` 与跨线程行为, 差异再多 5 项) |

## 6. 推荐

推荐路线 A. 理由: (1) 验收项 (协程示例, Flow `promise.await()`) 两条路都通过 (Flow 另需 Q97 的 1 行修复), A 与现状在 23 个 JVM 场景中 21 个逐字相同, 真机 19 项全部相同, 两态一致性在 P5.2 前几乎不用额外登记, 也不会在 P5.1 切换时带来脚本可见的时序变化; (2) 不碰 fork, 没有随 Rhino 升级重做补丁与复核时序的长期成本; (3) 性能与 B 同一量级, 都比现状快一个数量级; (4) P5.2 只需删文件与关态分支.

B 的优势 (规范符合度, 与可能的 async 语法一致) 在 AutoJs6 现有脚本中用不到, 而它的代价 (fork 补丁, 10 项行为变化) 是确定的. 若将来 fork 支持 async 函数, 再以本文的矩阵为基准评估是否改用 NativePromise.

## 7. 决定 (2026-10-24, 维护者; ROADMAP 附录 C Q95-Q98)

- Q95 路线: A (Kotlin 重写). B 的原型与 fork 补丁只留在共享笔记 `augment-p42-files/`, 不进仓库.
- Q96 实例属性: 不保留 `promise.js` 的 `_state` / `_handled` / `_value` / `_deferreds` (无文档, 矩阵 C2), 登记 ROADMAP 附录 E.
- Q97 Flow `await()`: 本阶段修, 两态都修. 旧包 `FlowNativeObject.guarded` 放过 ContinuationPending; `FlowNativeObject.await` 不再从 Kotlin 调用 JS 的 `promise.await`, 改为直接调用 Kotlin 的挂起实现 (A 的 `PromiseSuspension.await`, 对任意 thenable 有效, 所以关态的 `promise.js` 也修好). 共约 5 行, 按 R5 的 bug 修复例外处理.
- Q98 续体竞态: 本阶段修. `rhino/continuation/Continuation.resumeWith` 在非协程线程上被调用时, 先投递到协程所在线程的 timer, 在脚本线程上再取 `pending`, 消除 `协程HelloWorld` 关态的 "Method resume() should be called after suspend()". 约 5 行加 JVM 用例 (工作线程先于主线程 resume).

实施建议 (由调度会话发卡, 排在 P5.1 之前; Q97 / Q98 选项所说的小修复卡片与路线 A 合为一张, 因为 Q97 的 Kotlin 挂起实现就是 A 的 `PromiseSuspension`):

1. 路线 A 正式实现: `runtime/api/modules/promise/` 下的 `ScriptPromise` 与 `PromiseSuspension` (原型 `0001` 补丁为起点), 加注释与消息资源约 450 行; `ScriptRuntime.js_Promise` 只在开态改用它, 关态仍是 `promise.js`; `_immediateFn` 与全局 `setImmediate` 都未被替换时反应直接投递 timer (§4.3). 测试约 400 行: 本文 23 个场景转为 `promise.js` 对 A 的差分测试 (C2 按 Q96 登记为已知偏差), 加 Kotlin 侧 `await` 用例.
2. Q97 与 Q98 的两处小修复 (各约 5 行) 与 JVM 用例, 两态都生效.
3. 设备: G8441 跑 `device_promise_check.py --sample --scenarios --bench` 两态 (约 30 min), XQ-AT72 复测 R2 prologue 与失败路径 (约 40 min).
4. P5.2 删除 `assets/modules/promise.js`, `ProcessWideAssetModuleScriptProvider` 中的 "promise.js" 与 `js_Promise` 的关态分支.
