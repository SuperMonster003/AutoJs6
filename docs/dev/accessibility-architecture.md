# AutoJs6 无障碍自动化架构 (Accessibility Automation Architecture)

本文是无障碍 (a11y) 自动化体系的架构说明, 与 [accessibility-automation-roadmap.md](accessibility-automation-roadmap.md) 配套:
Roadmap 负责 "做什么, 做到哪了", 本文负责 "现在长什么样, 目标长什么样, 为什么".

维护约定:

1. "现状" 章节描述当前 `master` 的真实结构, 每个阶段 (P0-P6) 结束时更新, 以符号名 (类 / 方法) 而非行号为锚点.
2. "目标" 章节与 Roadmap 第 4 节保持一致; 若两者冲突, 以 Roadmap 的固定决策为准并回改本文.
3. "评估记录" 章节保存需要跨会话记忆的技术评估结论 (如 builder 代码生成方案), 避免重复评估.
4. 只用半角标点.

---

## 1. 现状 (2026-09-05, P1.1 / P1.2 / P1.3 / P1.7 完成后)

### 1.1 分层与类映射

| 层 | 职责 | 现有类 (均位于 `app/src/main/java/org/autojs/autojs/`) |
| --- | --- | --- |
| JS 表面 | 参数解析, 返回值适配, 注册全局函数 / 命名空间 | `runtime/api/augment/automator/Auto.kt` (`auto` 对象与 `auto()` 调用), `runtime/api/augment/automator/Automator.kt` (`automator` 与全局动作 `click` / `swipe` / `back` 等), `runtime/api/augment/selector/Selector.kt` (`selector` 与反射注册的 `text()` / `id()` 等全局选择器函数, `pickup` / `detect` / `existsAll` / `existsOne`), `runtime/api/augment/global/Global.kt` (`wait` / `waitForActivity` / `waitForPackage` / `sleep`), `runtime/api/augment/global/Classes.kt` (把 `UiSelector` / `UiObject` / `UiObjectCollection` / `AccessibilityBridge` 作为 JS 类暴露) |
| 服务与桥接 | 服务生命周期, 状态, 启停, 窗口根节点, 事件分发 | `core/accessibility/registry/A11yServiceRegistry.kt` (P1.1 新增, Android-free: 绑定实例, 3 态状态, 状态监听者, `await`; 进程单例为 `AccessibilityService.registry`), `core/accessibility/registry/ConnectionListeners.kt` (旧 `AccessibilityServiceCallback` 形态的适配器), `core/accessibility/AccessibilityService.kt` (服务本体; 伴生对象持有注册表的兼容外壳, 三个事件总线实例 `eventBus` / `keyBus` / `gestureBus` 及其上的兼容外壳 (`addDelegate` / `removeDelegate`, `addAccessibilityEventCallback` 等脚本回调槽, `addEventObserver`, `keyObserver` / `keyInterceptors`), 默认 bridge, 实例级事件计数), `core/accessibility/event/A11yEventBus.kt` (P1.3 新增, Android-free 泛型: owner 键, 类型集合, 优先级, 可消费, 去重 / 去抖选项, 时间戳), `core/accessibility/event/A11yEvent.kt` (总线上的信封: 原始事件 + 按需的 `wrapper` / `record`, `seal` / `copied`), `core/accessibility/event/AccessibilityDelegate.kt` (框架内部消费方接口, P1.3 迁入), `core/accessibility/A11yEventRecord.kt` (P1.7 新增, 事件字段的普通拷贝), `core/accessibility/AccessibilityServiceUsher.kt` (清单中注册的子类, 负责 `serviceInfo` 标志), `core/accessibility/control/A11yServiceController.kt` (P1.2 新增, Android-free 泛型: 谓词, `start` / `stop` / `restart` / `ensure(level, timeout, launchSettings, forceRestart)` / `restoreIfMalfunctioning`, 失败原因 `FailureReason`; 进程单例为 `AccessibilityTool.controller`), `core/accessibility/control/ServiceStartStrategy.kt` (策略接口, `EnabledServicesStore`, `SettingsStoreStartStrategy`), `core/accessibility/control/EnabledServicesStores.kt` (Root / Secure / Shizuku 三种 store), `core/accessibility/control/EnabledA11yServices.kt` (启用列表的纯操作), `core/accessibility/AccessibilityTool.kt` (控制器的 Android 门面: `launchSettings` / `clearCache` / 错误文案 / UI 触发点用的 `ensureServiceAsync` + `explainFailure`, 其余全部委托; 共享实例 `AbstractAutoJs.accessibilityTool`, 需要 Activity context 做 toast 的 UI 类各自持有一个无状态实例), `core/accessibility/AccessibilityBridge.java` + `AccessibilityBridgeImpl.java` (每个 `ScriptRuntime` 一个, 持有模式 / 标志 / 窗口过滤器, 提供 `windowRoots()`; 构造函数已无全局副作用), `core/accessibility/AccessibilityServiceAutoStart.kt` (Activity 创建时的自动恢复), `service/AccessibilityServiceItemHelper.kt` (抽屉开关辅助类, D8 已重命名; 开关反映 `controller.isRunning()`, 故障服务显示为关闭并带副标题) |
| 诊断与管理 (P1.7 新增) | 状态行, 诊断文本, 自检, 服务管理器 UI | `core/accessibility/diagnostics/A11yServiceDiagnostics.kt` (派生状态 `NOT_ENABLED / MALFUNCTIONING / CONNECTED / OPERATIONAL`, 16 个状态行, `Report.toText()`), `core/accessibility/diagnostics/A11yServiceSelfCheck.kt` (有界节点遍历 + 等待事件), `core/accessibility/diagnostics/A11yServiceInfoFormat.kt` (Android-free 位掩码与时长格式化), `ui/accessibility/A11yServiceManagerDialog.kt` (抽屉 "无障碍服务" 条目的管理器对话框: 状态 / 控制 / 设置; 状态与控制区各有持久化的详略等级 `StatusLevel` (4 级) / `ControlLevel` (3 级), 由标题行图标按钮切换; 状态行按字段固定行数以免高度抖动), `ui/accessibility/A11yEventMonitorDialog.kt` (实时事件监视器, 消费伴生级事件观察者) |
| 动作 | 节点动作, 全局动作, 手势, 截图 | `core/accessibility/SimpleActionAutomator.kt` (每个 `ScriptRuntime` 一个; 含 `registerEvent` / `removeEvent` 的 ownerId 语义与 `captureScreen`), `core/automator/GlobalActionAutomator.kt` (手势与全局动作, 使用 servant Looper), `core/automator/UiObjectActions.kt`, `core/automator/action/**` (滚动等复合动作) |
| 查询 | 选择器构建, 搜索, 拾取, 导航 | `core/accessibility/UiSelector.kt` (builder + `find*` + `pickup` 的 `Picker`), `core/automator/UiObject.kt` (`AccessibilityNodeInfoCompat` 包装 + 导航 + `detect` 的 `Detector` / `Invoker` / `Interceptor`), `core/automator/UiObjectCollection.kt`, `core/automator/filter/**` (39 个过滤器), `core/automator/search/{DFS,BFS}.kt`, `core/automator/pick/CompassParser.kt` (Android-free, P0 新增) |
| 事件消费方 (框架内部) | 以 `AccessibilityDelegate` 形式挂在服务上 | `core/activity/ActivityInfoProvider` (优先级 100, 维护当前包名 / Activity), `core/accessibility/AccessibilityNotificationObserver` (200, 通知与 Toast), `core/record/accessibility/AccessibilityActionRecorder` (300, 录制), 注册点 `AbstractAutoJs.kt` |
| 事件包装 | 投递给 JS 回调的事件对象 | `core/automator/AccessibilityEventWrapper.kt` (`source` 首次访问时解析, `seal()` 后 API < 33 上未解析的为 null, 见 1.3) |
| 节点生命周期 | 回收策略 | `core/accessibility/AccessibilityNodeInfoAllocator.kt` (生产路径未使用, 见 1.6), DFS / BFS / Scroll* 内联 `recycle()` |
| 布局分析器与代码生成 (非目标, 消费方) | | `core/accessibility/LayoutInspector.kt`, `core/accessibility/NodeInfo.kt`, `codegeneration/{ReadOnlyUiObject,CodeGenerator}.java` |
| 冻结 | 二进制兼容包, 不修改 | `com/stardust/**` |

### 1.2 服务生命周期与状态

```
系统设置启用服务 ──> 进程内 onServiceConnected ──> 首个 onAccessibilityEvent
   (hasService)          (hasInstance / isRunning)        (isOperational)
   [Settings.Secure]     [registry: CONNECTED]            [registry: OPERATIONAL]
```

进程内状态 (P1.1 起) 由 `A11yServiceRegistry` 统一持有, 3 态: `UNBOUND` (无绑定实例) / `CONNECTED` (已绑定, 未收到事件) / `OPERATIONAL` (已收到事件). "系统设置中已启用" 不是进程内可观测的状态 (只能读 `Settings.Secure`), 因此不并入枚举, 仍由 `AccessibilityTool.hasService()` 提供; P1.2 的控制器负责把两者组合成失败原因.

迁移入口: `onServiceConnected -> registry.onConnected(this)` (置 `CONNECTED`, 重置操作态); `onAccessibilityEvent -> registry.onEvent(this)` (置 `OPERATIONAL`, 并在必要时采纳实例); `onUnbind / onDestroy / stop() -> disposeService -> registry.onDisposed(this)` (仅当它是当前绑定实例时置 `UNBOUND`). 状态监听者按 owner 注册: 抽屉状态事件 (`AccessibilityServiceStateChangedEvent`, 仅绑定关系变化时投递), 每个脚本运行时的 `auto.stateListener` (owner = 其 `AccessibilityBridge`, 脚本退出时注销), Node Bridge 的断开回调.

| 谓词 (`AccessibilityTool`) | 含义 | 数据来源 |
| --- | --- | --- |
| `hasService()` | 系统 `ENABLED_ACCESSIBILITY_SERVICES` 中包含本服务 | `Settings.Secure` |
| `hasInstance()` | 进程内存在服务实例 | `registry.isBound` |
| `isRunning()` | `hasService() && hasInstance()` | 组合 |
| `isMalfunctioning()` | `hasService() && !hasInstance()` (已启用但进程内无实例, 常见于进程被杀后) | 组合 |
| `isOperational()` | `isRunning()` 且已收到首个事件 | 组合 + `registry.isOperational` |

等待: `AccessibilityService.waitForStarted(timeout)` = `registry.await(CONNECTED, timeout)`, `waitForOperational(timeout)` = `registry.await(OPERATIONAL, timeout)`; 单一 `Condition`, 任何迁移都会 `signalAll`, 负超时无限等待, 中断时返回 `false` 并保留中断标志.

启停 (P1.2 起由 `A11yServiceController` 统一, `AccessibilityTool` 只是门面):

- `start(launchSettingsIfNeeded)`: 已运行直接返回 (不重启); 否则按顺序尝试 `ServiceStartStrategy` (Root: `settings put secure` 经 root shell, 值加单引号以允许空列表; SecureSettings: `Settings.Secure` 直写, 需 `WRITE_SECURE_SETTINGS`; Shizuku: 同样的 `settings` 命令经 Shizuku 用户服务执行, 需 Shizuku 运行且已授权, 排在最后因为经另一进程往返), 每个策略只改写 `enabled_accessibility_services` 列表 (不再写 `accessibility_enabled`, Android 7.0 起由系统自行维护), 结果以回读为准; 已列出但未绑定的服务先移出再写回以触发重新绑定; 策略均不可用或失败时按需打开系统设置页.
- `stop(launchSettingsIfNeeded)`: 策略 -> `disableSelf()` 兜底 (仅当回读已不在列表中才算成功) -> 按需设置页. 系统的解绑是异步的, `waitForStopped()` (注册表 `awaitUnbound`, 2000 ms) 等待它.
- `restart()`: `stop` -> 等待解绑 -> `start`; 不等待会被部分系统 (AVD API 26 实测) 合并为 "仍然启用". `auto(true)` 的强制重启走 `ensure(forceRestart = true)`, 同样等待解绑.
- `ensure(level, timeoutMillis, launchSettingsIfNeeded, forceRestart): EnsureResult`: 已满足直接返回; 已绑定只差首个事件时只等待; 否则策略 (每个给 2 s 完成绑定) -> 设置页, 然后等待 `level` 至 `timeoutMillis` (负数无限). 失败原因 `NOT_ENABLED` / `ENABLED_BUT_UNBOUND` / `BOUND_BUT_NOT_OPERATIONAL` = "设置中是否启用" x 注册表状态. `AccessibilityTool` 的 `ensureService()` / `ensureServiceOperational()` 失败抛带原因文案的 `ScriptException`, `startServiceAndWaitFor()` / `startServiceAndWaitForOperational()` (`auto.waitFor(timeout)`) 失败抛 `ScriptInterruptedException`.
- `restoreIfMalfunctioning()` (`AccessibilityServiceAutoStart`, 每个 `BaseActivity` 创建时): 只恢复 `isMalfunctioning()` 且等 2 s 后仍未绑定的服务, 不会重新启用用户显式关闭的服务.
- 策略写入串行化 (`writeLock`), 等待在锁外, 因此等待用户的脚本不会阻塞其它地方的停止请求.
- UI 触发点 (悬浮菜单的布局分析, 快捷设置磁贴, `AutoJs.kt` 的布局分析广播) 走 `AccessibilityTool.ensureServiceAsync(level, timeout, launchSettingsIfNeeded, onResult)`: `ensure` 在 `Schedulers.io()` 执行 (root shell 与等待绑定不阻塞主线程), 结果回主线程; 失败交给 `explainFailure(reason)` (未启用: 提示 + 设置页, 其它原因: 按原因 toast); 成功后立即接续自身功能 (`LayoutInspector.captureCurrentWindowWithRetry`, 10 x 300 ms, 服务刚绑定后活动窗口根节点可能短暂为 `null`), 因此服务未启用但可自动启动时一次触发即可.

已知平台问题: `onServiceConnected` 偶尔不回调但事件照常到达; 注册表的 `onEvent` 会直接采纳实例并置 `OPERATIONAL`, 等待方与状态监听者 (含 `auto.stateListener` 的 `onConnected`) 在这条路径上同样被唤醒 / 通知.

默认 bridge: `AccessibilityService.bridge` 是伴生对象级的 `defaultBridge`, 由 `AbstractAutoJs.createRuntime()` 在创建每个脚本运行时时登记为最近一次创建的 bridge, 供无参 `UiSelector()` (脚本中的 `new UiSelector()`) 使用, 与服务实例的生死无关. `pickup` / `detect` 的 `Picker.Builder` 则直接绑定调用方 `scriptRuntime.accessibilityBridge`.

### 1.3 事件流

`AccessibilityService.onAccessibilityEvent(event)` 在服务主线程 (= 应用主线程) 串行执行 (P1.3 起):

1. `registry.onEvent(this)`: 首个事件把状态置为 `OPERATIONAL` (并在 `onServiceConnected` 未回调时采纳实例); 已是 `OPERATIONAL` 的同一实例不产生迁移. 实例计数 (`eventCount` 等) 更新.
2. `TYPE_WINDOW_STATE_CHANGED` / `TYPE_VIEW_FOCUSED` 时无条件更新 `fastRootInActiveWindow` (不再取决于是否有 delegate 关心, 且先于分发, 使 MODE_FAST 的回调已能看到新窗口).
3. `eventBus.hasSubscribers(type)` 为假则返回; 否则构造 `A11yEvent` 信封并 `eventBus.dispatch(它)`, 返回前 `seal()` (API < 33 框架随后回收原始事件).

`eventBus` (`A11yEventBus<A11yEvent>`, 伴生对象持有, 跨服务重启存活) 按优先级升序分发, 相同优先级按注册顺序, 处理器异常隔离并记录, 可消费订阅项返回 `true` 终止链:

| 优先级 | 订阅项 | 载荷 | 可消费 |
| --- | --- | --- | --- |
| `PRIORITY_OBSERVER` = -100 | 事件监视器等轻量观察者 (`addEventObserver(owner)`, 同 owner 替换) | `A11yEvent.record` (字段拷贝) | 否 |
| `PRIORITY_SCRIPT` = 0 | 脚本回调 (`auto.registerEvent`), 每个 (ownerId, 类型) 一槽, 重注册即替换 | `A11yEvent.wrapper` (每个事件至多构建一次) | 否 |
| 100 / 200 / 300 | `ActivityInfoProvider` / `AccessibilityNotificationObserver` / `AccessibilityActionRecorder` (`addDelegate(priority, delegate)`) | `A11yEvent.raw` + `service` | 是 |

订阅项选项 (`A11yEventBus.Options`): `distinctKey` + `distinctWithinMillis` (前沿去重: 键与上次投递相同且在窗口内则丢弃; 无键为纯节流, 无窗口则相同键持续丢弃), `debounceMillis` (后沿: 安静该时长后只投递这一串的最后一个; 投递物为 `A11yEvent.copied()` 即原始事件的拷贝, 在主 looper 上执行, 不能消费). `auto.registerEvent(name, callback, options?)` 的 `{ distinct, within, debounce }` 直接映射到它. 安静窗口检测: `eventBus.lastDispatchAtMillis`, `Subscription.lastMatchedAtMillis` (匹配类型即记录, 在去重之前), 供 P3 的 `waitForIdle` / `waitForEvent` 使用.

按键 (`onKeyEvent`) 走 `keyBus` (`A11yEventBus<KeyEvent>`, 类型 = keyCode), 同步分发: 观察者 (`OnKeyListener.Observer`, 优先级 0, 非消费; `events.observeKey()`, Node Bridge 输入观察) 先于拦截器 (`KeyInterceptor.Observer`, 100, 消费; `events.setKeyInterceptionEnabled`), 返回值即是否被拦截; 两个 `Observer` 类是总线上的兼容外壳 (方法名不变), 实例属性 `onKeyObserver` / `keyInterrupterObserver` 委托到伴生对象, 因此监听跨服务重启存活. 手势 (`onGesture`, 已弃用回调) 走 `gestureBus` (`A11yEventBus<Int>`), 目前无订阅者. 原 `eventExecutor` 已删除.

脚本退出时 `SimpleActionAutomator.removeAllEventsForThisRuntime()` = `eventBus.unsubscribeAll(ownerId)`, 只清理本脚本的回调.

### 1.4 查找与拾取

`text('登录').findOne(3000)` 的调用链:

```
UiSelector.findOne(timeout)        主线程禁止; 循环 find(1), 间隔 intermission() = 50 ms
  -> UiSelector.find(1)            mA11yTool.ensureService()
    -> findInternal(1)             若 FLAG_FIND_ON_UI_THREAD: post 到 UI 线程并 blockedGet (无超时)
      -> findImpl(roots, 1)        roots = bridge.windowRoots() (受 mode / flags / 窗口过滤器影响), 跳过黑名单包名
        -> searchAlgorithm.search(UiObject.createRoot(root), selector, max)   DFS (默认) 或 BFS
```

- `bridge.windowRoots()`: 有窗口过滤器时为通过过滤的 `windows[*].root`; 否则 `MODE_FAST` 取 `fastRootInActiveWindow`, `MODE_NORMAL` 取 `rootInActiveWindow`; 根节点为 `null` 时返回空列表 (2026-09-05 起, 原先返回 `[null]`, `auto.windowRoots` 因而得到 `UiObject(null)`).
- 黑名单 = 保护模式 (设置 > 脚本运行 > 保护模式, `Pref.isGuardModeEnabled`): `AccessibilityConfig` 构造时把自身包名加入黑名单并封印, 每个 bridge 一个实例 (脚本启动时读取, 运行中改设置不影响已启动的脚本). 开启后 AutoJs6 自身前置时 `find*` / `pickup` 一律为空, `SimpleActionAutomator.performAction` (`scrollDown()` 等节点动作) 直接返回 `false`; 全局动作 (`home()` / `back()`) 不受影响. 这是按设计的行为, 冒烟脚本以它为前置检查.

- `Selector` (`core/automator/filter/Selector.kt`) 是过滤器列表的 AND 组合; `UiSelector.append` 原地追加 (因此 `Selector.kt` 的全局函数每次都新建选择器, 见其 `@Caution` 注释).
- `pickup(root?, selector, compass?, resultType?, callback?)`: `selector` 可为字符串 / 正则 (content 匹配), `UiSelector`, 对象 (键 = builder 方法名, 由 `Picker.Builder.checkSelectorMap` 反射转换), 数组 (组合); 找到后按 `compass` 导航, 按 `resultType` (`#` widget, `{}` collection, `[]` widgets, `$` content, `.` point, `@` selector, `?` existence) 转换.
- `detect(w, compass?, resultType?, callback?)` 是 `pickup` 的节点版.
- 罗盘 (compass) 语法由 `CompassParser` 定义: `p[p\d]*` (上溯, 多段求和), `c-?\d+([>c]?-?\d+)*` (逐级子节点, 负数从末尾计), `s[<>]?-?\d+` (兄弟: 绝对 / 相对), `k[k\d]*` (自身及至多 n 级祖先中首个可点击节点, 无则 `null`, D6). `%` 表示透传.

`wait(cond, limit?, interval?, callback?)` (`Global.kt`): `cond` 为函数或 `pickup` 选择器; `limit < 100` 视为次数, 否则毫秒超时 (默认 10000); 间隔默认 200; `interval >= timeout` 时折叠为单次; 真值规则 `isWaitConditionSatisfied` (仅 nullish / `false` / `NaN` 不满足). 这些规则已抽为纯函数, 供 P3 的 Flow 复用.

### 1.5 线程模型

| 线程 | 承担 | 约束 |
| --- | --- | --- |
| 脚本线程 (每个 `ScriptRuntime` 一个 Looper 线程) | 执行脚本, `wait` / `findOne` / `sleep` 在此阻塞 | UI 模式脚本的主逻辑跑在主线程, 因此 `sleep` / `findOne` / `waitForActivity` 等有 UI 线程守卫 (`wait` 目前没有) |
| Android 主线程 | Activity / 悬浮窗 UI; `FLAG_FIND_ON_UI_THREAD` 时的查找 | `findInternal` 的跨线程 `blockedGet()` 无超时 |
| 无障碍服务主线程 | `onAccessibilityEvent` 与 JS 事件回调 | JS 回调在此同步执行, 回调越重, 事件积压越多 |
| `eventExecutor` (服务内单线程池) | 按键与手势观察者 | 服务销毁时 `shutdownNow` |
| servant Looper (`Loopers.servantLooper`) | `GlobalActionAutomator` 的手势派发与全局动作回调 | 首次使用时惰性初始化 |
| Timer / `setImmediate` | Promise polyfill 的 `then` 调度, `ScriptAsyncDispatcher` 的回投 | P3 的回调回投复用 |

### 1.6 节点生命周期

- API >= 33: `AccessibilityNodeInfo.recycle()` 为 no-op, 一切回收调用无效果.
- API < 33: DFS / BFS 对非命中且非根的节点内联 `recycle()`, 命中上限后对已获取未遍历的节点也回收 (P0); `ScrollAction` / `ScrollMaxAction` / `UiObject.recycle` / `recycleFastRootInActiveWindow` 各自内联回收.
- `AccessibilityNodeInfoAllocator` 设计为 "登记 + 批量回收" 的分配器, 但生产路径中 `UiSelector` 的 `allocator` 恒为 `null` (`ScriptRuntime.selector()` -> `UiSelector(bridge)`), `global` / `NONE` 无引用. P0 修正了它的 `getParent(Compat)` 登记与线程安全问题, 并把堆栈捕获改为 `isTracingOrigin` 开关, 仅作为 P1.5 `NodeLifecycle` 的参考实现.

### 1.7 已知平台行为与已知问题

| 现象 | 性质 | 处理 |
| --- | --- | --- |
| 一次窗口切换收到 2 个以上 `TYPE_WINDOW_STATE_CHANGED`, `eventTime` 相差几十毫秒, 包名 / 类名相同 | 平台行为 (窗口添加与获得焦点 / 标题变化各发一次); 服务配置 `notificationTimeout="0"`, 系统不合并 | `auto.registerEvent(name, cb, { distinct: 'window', within: 500 })` 或 `{ debounce: ms }` (P1.3); 脚本侧手工去重示例仍在 `sample/accessibility/smoke-legacy-api.js` |
| `AccessibilityEventWrapper` 的 `source` (父链求 depth + 扫描兄弟求 index) 成本高 | 成本问题; API < 33 事件在回调返回后被回收 | P0 给索引查找加界并捕获异常; P1.3 改为首次访问时解析, `seal()` 后 API < 33 上未解析的为 null (回调内读取不受影响) |
| `onServiceConnected` 偶尔不触发 | 平台问题 | 事件到达即视为已连接 (现有兜底), P1.1 纳入状态表 |
| 服务重启后脚本的事件回调 / 按键监听失效 | 原实现把回调存在服务实例上 | P1.3 起订阅位于伴生对象的总线, 跨重启存活 |
| 服务重绑后 `rootInActiveWindow` 持续为 `null` 直到下一次窗口变化 (AVD API 26 实测 > 11 s; `windows` 仍有 3 项) | 平台行为 (AOSP 8.0 `AccessibilityManagerService`: 重算窗口列表时丢弃尚未重新注册连接的应用窗口, 焦点窗口缺失则活动窗口保持无效, 且带 `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` 时 `TYPE_WINDOW_STATE_CHANGED` 不更新活动窗口); 有其它无障碍服务常驻的设备与现代系统未见 | 此状态下 `find*` / `pickup` 为空; `windowRoots()` 已改为返回空列表而非 `[null]`; 布局分析的 UI 触发点在服务刚启动后经 `captureCurrentWindowWithRetry` 重试 3 s, 耗尽时 toast `error_active_window_root_unavailable`; 脚本侧兜底策略 (回落 `windows` / 主动制造窗口变化) 由 P1.4 `WindowRootPolicy` 决定, 冒烟脚本在失败时打印窗口列表并制造一次窗口变化以收集数据 |
| API <= 28 上 `home()` 之后 5 s 内, 后台脚本的 `app.launch()` 等活动启动被系统挂起, 到 5 s 期满才执行 (logcat `ActivityManager: Activity start request from <uid> stopped`; 实测 AVD 24 / 25 / 26 与 G8441 上目标窗口的 `WINDOW_STATE_CHANGED` 一律在 `home()` 后 +5.1 ~ 5.3 s 到达) | 平台行为 (`home()` 注入 HOME 键 -> `PhoneWindowManager.launchHomeFromHotKey` -> `stopAppSwitches()`, 无 `STOP_APP_SWITCHES` 权限的后台调用方在 `APP_SWITCH_DELAY_TIME` 内的启动进入 `mPendingActivityLaunches`); API 29+ 未观察到 | 脚本侧在 `home()` 后等待目标包名前置而非固定 sleep (冒烟脚本 `bringAutoJs6ToFront()`); 运行时不做处理 (P6 文档标注 `app.launch` / `home()`) |
| 保护模式开启且 AutoJs6 前置时选择器全部为空 | 按设计 (黑名单, 见 1.4) | 文档化 (P1.4); 冒烟脚本前置检查 `guard mode is off` |
| 强制停止 AutoJs6 后服务变为 "未启用" (抽屉开关关闭, 无 "故障" 副标题) | 平台行为 (`AccessibilityManagerService` 的 `PackageMonitor.onHandleForceStop` 把被强制停止的包的服务移出启用列表) | 与用户显式关闭同等对待, 不自动重新启用 (Roadmap Q8 待决, 建议保持); 偶见的一次性 "故障" 副标题是抽屉同步读到系统尚未更新的列表 |
| 由 application / service context 构建的悬浮 UI (悬浮按钮的菜单 / 对话框 / toast, 布局分析器窗口) 语言与夜间模式跟随系统 | 平台行为 (AppCompat 的应用内语言与夜间模式只应用于其 Activity) | `ContextUtils.withHostConfiguration()`: 空 `Configuration` 仅覆盖 `uiMode` 夜间位 (按 `AppCompatDelegate.getDefaultNightMode()`) 与 locale (`Language` 偏好非 AUTO 时), 其余字段跟随 base; 接入 `CircularMenu`, `LayoutFloatyWindow.onCreate`, `AutoJs.kt` 与磁贴传给布局分析窗口的 context. 应用内切换语言 / 夜间模式时 `MainActivity` 重建并重建悬浮菜单 |
| `find()` 的黑名单只检查窗口根节点的包名 | 设计限制 | P2.4 可配置 |
| 无参 `findOne()` / `untilFind()` / `UiSelector.performAction` 无界阻塞 | 既有 API 语义 (D2 保持) | 文档标注; P3 提供带超时的链式替代 |

### 1.8 测试基础设施

- JVM 单元测试为纯 JUnit4 (无 Robolectric). 任务名 `:app:testAppDebugUnitTest`.
- `UiObject(null)` 可在 JVM 中构造 (androidx `AccessibilityNodeInfoCompat` 是真实库代码, 仅调用 Android 桩方法时失败). 夹具 `app/src/test/java/org/autojs/autojs/core/automator/FakeUiObject.kt` 以 `FakeUiObject.root { child("a", clickable = true) { child("a0") } }` 构建确定性树, 覆盖 `getChildCount` / `childCount` / `child` / `parent` / `clickable` / `recycle`, 用于 `SearchAlgorithmTest` 与 `UiObjectCompassTest`.
- Android-free 的纯逻辑及其测试: `CompassParser` (`CompassParserTest`), `Global.normalizeWaitArguments` / `resolveWaitPolicy` / `isWaitConditionSatisfied` (`GlobalWaitCompatibilityTest`), `SelectorMethodResolver` (`SelectorMethodResolverTest`), `A11yServiceRegistry` (`A11yServiceRegistryTest`), `A11yServiceController` 及策略 (`A11yServiceControllerTest` 以假策略模拟系统的绑定 / 解绑, `SettingsStoreStartStrategyTest` 以内存 store, `EnabledA11yServicesTest`), `A11yEventBus` (`A11yEventBusTest`: 假时钟 + 假延迟调度器, 含锁定服务分发顺序的用例), `A11yServiceInfoFormat` (`A11yServiceInfoFormatTest`).
- 真机冒烟: `sample/accessibility/smoke-legacy-api.js` (48 项, 无无人值守启动方式时 37 项). 通过 adb 运行时控制台不可见, 可用一个包装脚本在同一引擎内 `eval` 冒烟脚本并把 `console` 输出镜像到 `/sdcard` 的文件 (2026-09-05 用此方式在 8 台设备上复跑); 注意 Rhino 把循环体内直接声明的 `const` 视为只赋值一次, 探针脚本的逐轮读取应放在函数里.

### 1.9 基准数据

尚无. P2.4 / P5.5 补充: 全树遍历一次的耗时 (按节点数分档), `FLAG_FIND_ON_UI_THREAD` 与 `MODE_FAST` 的影响, `findOne` 轮询 50 ms 的 CPU 占用.

---

## 2. 目标

### 2.1 分层

```
JS 表面层 (runtime/api/augment/**)         auto / automator / selector / flow / global(wait*)
    |  仅做参数解析与返回值适配, 不含业务逻辑
核心层 (core/accessibility, core/automator)  Android 相关, 但业务逻辑依赖抽象接口
    |  A11yServiceRegistry / A11yServiceController / A11yEventBus / WindowRootProvider / NodeLifecycle
    |  UiSelector / UiObject / UiObjectCollection / filter / search / pick / flow / toolkit
纯 Kotlin 逻辑 (Android-free)                 PollPolicy / Stabilizer / FlowScheduler / SelectorRegistry / 过滤器谓词 / CompassParser
    |  JUnit4 直接测试
```

设计原则 (与 Roadmap 第 4.1 节一致): 单一事实来源; 依赖注入而非静态查找; Android-free 核心; 显式注册表替代反射表 (且注册表必须可驱动代码生成, D9); 兼容外壳 (JS 可见类保留 FQN 与 public 签名).

### 2.2 迁移映射

| 现有 | 目标 | 阶段 |
| --- | --- | --- |
| `AccessibilityService` 伴生对象的静态状态 | `A11yServiceRegistry` (3 态 `State` + 多监听者 + `await`), 伴生对象保留同名成员作为外壳 | P1.1 (已完成 2026-09-05) |
| `AccessibilityTool` (12 处实例) | `A11yServiceController` 单例 + `ServiceStartStrategy`; `AccessibilityTool` 变薄为 Android 门面 (未弃用, 实例 7 处) | P1.2 (已完成 2026-09-05) |
| `eventBox` / `delegates` / 按键观察者 / 手势分发 | `A11yEventBus` (ownerId, 类型集合, 优先级, 可消费, 去重 / 去抖选项); 三个实例 `eventBus` / `keyBus` / `gestureBus` | P1.3 (已完成 2026-09-05) |
| `AccessibilityBridge.java` 的 3 处根节点逻辑 | `WindowRootProvider` + `WindowRootPolicy`, `AccessibilityBridge` 转 Kotlin 保留 FQN | P1.4 |
| 内联 `recycle()` 与 `AccessibilityNodeInfoAllocator` | `NodeLifecycle` / `NodeRecyclePolicy` (API >= 33 全 no-op) | P1.5 |
| `service/AccessibilityService.kt` | `AccessibilityServiceItemHelper` (仅抽屉使用) | P1.2 (D8, 已完成 2026-09-05) |
| `UiSelector` 手写 builder + `checkSelectorMap` 手工表 + `Selector.kt` 反射注册 | `SelectorRegistry` (数据) -> 生成 builder (D9) + `SelectorMapParser` + 白名单注册 | P2.2 |
| 复制粘贴的过滤器族 | `StringPropertyFilter` / `MetricsFilter` 泛化 | P2.3 |
| `findOne` / `untilFind*` 各自的轮询 | `PollPolicy` + `UiSelectorQuery`, 有界 UI 线程跳转 | P2.4 |
| `UiObject` 中的 `compass` / `Detector` / `summary` | `core/automator/pick/**` (P0 已迁出解析器) + `UiObjectSummary.kt` | P2.5 |
| 无 | `core/automator/flow/**` (`FlowScheduler` / `Stabilizer` / `FlowExecutor`) + `runtime/api/augment/flow/**` (`waitAsync` / `flow` / `$flow` / `auto.wait`) | P3 |
| 无 | `core/automator/toolkit/**` + `automator.*` 与全局函数 (D10) + Flow 步骤 | P4 |

---

## 3. 评估记录

### 3.1 builder 代码生成 (D9, 待 P2)

待评估. 候选方案与评估维度见 Roadmap P2.2. 结论回填于此.

### 3.2 字符串选择器语法 (Q4 -> D13, 2026-09-05)

已决: 方案 B, 入口复用 `select`. 全局 `select('text=登录 clickable')` 返回 `UiSelector`; `UiSelector.select(String)` 追加解析出的过滤器; 无参 `select()` 保持 `ACTION_SELECT` 动作语义. 语法与约束见 Roadmap D13, 实现于 P5.3 (依赖 P2.2 的 `SelectorRegistry` 提供键名与别名).

---

## 4. 术语

| 术语 | 含义 |
| --- | --- |
| a11y | accessibility (无障碍) 的缩写 |
| 服务实例 (instance) | 进程内 `AccessibilityService` 对象, 由系统绑定后创建 |
| operational (可操作态) | 服务已收到首个事件, 可以正常查询与执行动作 |
| bridge | `AccessibilityBridge`, 每个脚本运行时一个, 封装模式 / 标志 / 窗口过滤器与根节点获取 |
| 拾取 (pickup) | 以多种形态的选择器一次性完成 "查找 + 导航 + 结果转换" |
| 探测 (detect) | 对已有节点执行 "导航 + 结果转换" |
| 罗盘 (compass) | 描述相对导航的紧凑字符串 (`p` / `c` / `s` / `k`) |
| ownerId | 脚本运行时在事件回调盒中的标识, 用于按脚本注销 |
| Flow | P3 引入的 Promise 支撑的链式异步等待 / 动作对象 |
| toolkit | P4 引入的高阶组合动作 (智能点击, 滚动查找, 弹窗关闭等) |
