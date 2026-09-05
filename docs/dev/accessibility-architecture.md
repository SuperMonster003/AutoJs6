# AutoJs6 无障碍自动化架构 (Accessibility Automation Architecture)

本文是无障碍 (a11y) 自动化体系的架构说明, 与 [accessibility-automation-roadmap.md](accessibility-automation-roadmap.md) 配套:
Roadmap 负责 "做什么, 做到哪了", 本文负责 "现在长什么样, 目标长什么样, 为什么".

维护约定:

1. "现状" 章节描述当前 `master` 的真实结构, 每个阶段 (P0-P6) 结束时更新, 以符号名 (类 / 方法) 而非行号为锚点.
2. "目标" 章节与 Roadmap 第 4 节保持一致; 若两者冲突, 以 Roadmap 的固定决策为准并回改本文.
3. "评估记录" 章节保存需要跨会话记忆的技术评估结论 (如 builder 代码生成方案), 避免重复评估.
4. 只用半角标点.

---

## 1. 现状 (2026-09-05, P1.1 ~ P1.7 与 P2.1 完成后)

### 1.1 分层与类映射

| 层 | 职责 | 现有类 (均位于 `app/src/main/java/org/autojs/autojs/`) |
| --- | --- | --- |
| JS 表面 | 参数解析, 返回值适配, 注册全局函数 / 命名空间 | `runtime/api/augment/automator/Auto.kt` (`auto` 对象与 `auto()` 调用), `runtime/api/augment/automator/Automator.kt` (`automator` 与全局动作 `click` / `swipe` / `back` 等), `runtime/api/augment/selector/Selector.kt` (`selector` 与反射注册的 `text()` / `id()` 等全局选择器函数, `pickup` / `detect` / `existsAll` / `existsOne`), `runtime/api/augment/global/Global.kt` (`wait` / `waitForActivity` / `waitForPackage` / `sleep`), `runtime/api/augment/global/Classes.kt` (把 `UiSelector` / `UiObject` / `UiObjectCollection` / `AccessibilityBridge` 作为 JS 类暴露) |
| 服务与桥接 | 服务生命周期, 状态, 启停, 窗口根节点, 事件分发 | `core/accessibility/registry/A11yServiceRegistry.kt` (P1.1 新增, Android-free: 绑定实例, 3 态状态, 状态监听者, `await`; 进程单例为 `AccessibilityService.registry`), `core/accessibility/registry/ConnectionListeners.kt` (旧 `AccessibilityServiceCallback` 形态的适配器), `core/accessibility/AccessibilityService.kt` (服务本体; 伴生对象持有注册表的兼容外壳, 三个事件总线实例 `eventBus` / `keyBus` / `gestureBus` 及其上的兼容外壳 (`addDelegate` / `removeDelegate`, `addAccessibilityEventCallback` 等脚本回调槽, `addEventObserver`, `keyObserver` / `keyInterceptors`), 默认 bridge, 实例级事件计数), `core/accessibility/event/A11yEventBus.kt` (P1.3 新增, Android-free 泛型: owner 键, 类型集合, 优先级, 可消费, 去重 / 去抖选项, 时间戳), `core/accessibility/event/A11yEvent.kt` (总线上的信封: 原始事件 + 按需的 `wrapper` / `record`, `seal` / `copied`), `core/accessibility/event/AccessibilityDelegate.kt` (框架内部消费方接口, P1.3 迁入), `core/accessibility/A11yEventRecord.kt` (P1.7 新增, 事件字段的普通拷贝), `core/accessibility/AccessibilityServiceUsher.kt` (清单中注册的子类, 负责 `serviceInfo` 标志), `core/accessibility/control/A11yServiceController.kt` (P1.2 新增, Android-free 泛型: 谓词, `start` / `stop` / `restart` / `ensure(level, timeout, launchSettings, forceRestart)` / `restoreIfMalfunctioning`, 失败原因 `FailureReason`; 进程单例为 `AccessibilityTool.controller`), `core/accessibility/control/ServiceStartStrategy.kt` (策略接口, `EnabledServicesStore`, `SettingsStoreStartStrategy`), `core/accessibility/control/EnabledServicesStores.kt` (Root / Secure / Shizuku 三种 store), `core/accessibility/control/EnabledA11yServices.kt` (启用列表的纯操作), `core/accessibility/AccessibilityTool.kt` (控制器的 Android 门面: `launchSettings` / `clearCache` / 错误文案 / UI 触发点用的 `ensureServiceAsync` + `explainFailure`, 其余全部委托; 共享实例 `AbstractAutoJs.accessibilityTool`, 需要 Activity context 做 toast 的 UI 类各自持有一个无状态实例), `core/accessibility/AccessibilityBridge.kt` + `AccessibilityBridgeImpl.kt` (P1.4 转为 Kotlin, FQN 与 public 签名不变; 每个 `ScriptRuntime` 一个, 持有单个不可变 `windowRootPolicy` (模式 / 过滤器 / 兜底, 由 `setMode` / `setWindowFilter` / `flags` 重建) 与标志, 三个根节点查询全部委托 provider; 构造函数已无全局副作用), `core/accessibility/window/WindowRootPolicy.kt` (P1.4 新增, Android-free 数据类), `core/accessibility/window/WindowRootResolver.kt` (P1.4 新增, Android-free 唯一实现: `roots` / `currentRoot` / `activeRoot` / `windows`, 访问器经 `Source` 注入并逐个隔离异常), `core/accessibility/window/WindowRootProvider.kt` (Android 绑定, 每次查询取当前服务实例; `WindowRootProvider.default`, 布局分析器使用), `core/accessibility/AccessibilityServiceAutoStart.kt` (Activity 创建时的自动恢复), `service/AccessibilityServiceItemHelper.kt` (抽屉开关辅助类, D8 已重命名; 开关反映 `controller.isRunning()`, 故障服务显示为关闭并带副标题) |
| 诊断与管理 (P1.7 新增) | 状态行, 诊断文本, 自检, 服务管理器 UI | `core/accessibility/diagnostics/A11yServiceDiagnostics.kt` (派生状态 `NOT_ENABLED / MALFUNCTIONING / CONNECTED / OPERATIONAL`, 16 个状态行, `Report.toText()`), `core/accessibility/diagnostics/A11yServiceSelfCheck.kt` (有界节点遍历 + 等待事件), `core/accessibility/diagnostics/A11yServiceInfoFormat.kt` (Android-free 位掩码与时长格式化), `ui/accessibility/A11yServiceManagerDialog.kt` (抽屉 "无障碍服务" 条目的管理器对话框: 状态 / 控制 / 设置; 状态与控制区各有持久化的详略等级 `StatusLevel` (4 级) / `ControlLevel` (3 级), 由标题行图标按钮切换; 状态行按字段固定行数以免高度抖动), `ui/accessibility/A11yEventMonitorDialog.kt` (实时事件监视器, 消费伴生级事件观察者) |
| 动作 | 节点动作, 全局动作, 手势, 截图 | `core/accessibility/SimpleActionAutomator.kt` (每个 `ScriptRuntime` 一个; 含 `registerEvent` / `removeEvent` 的 ownerId 语义与 `captureScreen`), `core/accessibility/A11yScreenshotter.kt` (P1.6 新增, API 30+: 单次 `take` 转软件位图并关闭硬件缓冲, `takeWithRetry` 对限频错误重试至多 60 x 50 ms; `captureScreen` 消费它, Python Host 待 P5.4), `core/automator/GlobalActionAutomator.kt` (手势与全局动作, 使用 servant Looper), `core/automator/UiObjectActions.kt`, `core/automator/action/**` (滚动等复合动作) |
| 查询 | 选择器构建, 搜索, 拾取, 导航 | `core/accessibility/UiSelector.kt` (builder + `find*` + `pickup` 的 `Picker`), `core/automator/UiNode.kt` (P2.1 新增, Android-free 的节点只读视图, 自类型泛型 `UiNode<N>`; 过滤器 / 搜索 / 罗盘只依赖它) + `core/automator/UiNodeActions.kt` (动作符号名与 `hasAction` 匹配), `core/automator/UiObject.kt` (`AccessibilityNodeInfoCompat` 包装, 实现 `UiNode<UiObject>`, 含 `detect` 的 `Detector` / `Invoker` / `Interceptor`), `core/automator/UiObjectCollection.kt`, `core/automator/filter/**` (39 个过滤器, 面向 `UiNode<*>`), `core/automator/search/{DFS,BFS}.kt` (泛型于 `UiNode<N>`), `core/automator/pick/{CompassParser,Compass}.kt` (Android-free: 解析 P0 新增, 导航 P2.1 迁出) |
| 事件消费方 (框架内部) | 以 `AccessibilityDelegate` 形式挂在服务上 | `core/activity/ActivityInfoProvider` (优先级 100, 维护当前包名 / Activity), `core/accessibility/AccessibilityNotificationObserver` (200, 通知与 Toast), `core/record/accessibility/AccessibilityActionRecorder` (300, 录制), 注册点 `AbstractAutoJs.kt` |
| 事件包装 | 投递给 JS 回调的事件对象 | `core/automator/AccessibilityEventWrapper.kt` (`source` 首次访问时解析, `seal()` 后 API < 33 上未解析的为 null, 见 1.3) |
| 节点生命周期 (P1.5) | 回收策略, 登记与批量释放 | `core/accessibility/node/NodeRecyclePolicy.kt` (Android-free: `RECYCLE` / `NO_OP`, `forSdk`), `core/accessibility/node/NodeLifecycle.kt` (Android-free: `release` / `releaseAll`, 异常隔离), `core/accessibility/node/NodeLifecycles.kt` (Android 侧 `NodeLifecycle.default` 与框架节点类型的扩展), `core/accessibility/node/NodeRegistry.kt` (Android-free 同一性登记表), `core/accessibility/AccessibilityNodeInfoAllocator.kt` (每个 `ScriptRuntime` 一个, 见 1.6) |
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
        -> searchAlgorithm.search(UiObject.createRoot(root), selector, max)   DFS (默认) 或 BFS, 泛型于 UiNode<N>, 结果为 ArrayList<UiObject>
```

- `bridge.windowRoots()` = `WindowRootResolver.roots(policy)` (P1.4 起唯一实现): 有窗口过滤器时为通过过滤的 `windows[*].root` (与模式无关); 否则 `MODE_FAST` 取 `fastRootInActiveWindow`, `MODE_NORMAL` 取 `rootInActiveWindow`; 该根节点为 `null` 时按 `policy.fallback` 处理: 默认 `NONE` 返回空列表 (2026-09-05 起, 原先返回 `[null]`), `auto.setFlags(['appWindowsFallback'])` 时改为窗口列表中应用窗口的根节点 (焦点 > 活动 > 列表顺序). `auto.root` = `currentRoot(policy)` (有过滤器时首个通过过滤且有根节点的窗口, 惰性), `auto.rootInActiveWindow` = `activeRoot(policy)` (忽略过滤器). 三者都不应用黑名单.
- 黑名单 = 保护模式 (设置 > 脚本运行 > 保护模式, `Pref.isGuardModeEnabled`): `AccessibilityConfig` 构造时把自身包名加入黑名单并封印, 每个 bridge 一个实例 (脚本启动时读取, 运行中改设置不影响已启动的脚本). 开启后 AutoJs6 自身前置时 `find*` / `pickup` 一律为空, `SimpleActionAutomator.performAction` (`scrollDown()` 等节点动作) 直接返回 `false`; 全局动作 (`home()` / `back()`) 不受影响. 这是按设计的行为, 冒烟脚本以它为前置检查.

- `Selector` (`core/automator/filter/Selector.kt`) 是过滤器列表的 AND 组合; `UiSelector.append` 原地追加 (因此 `Selector.kt` 的全局函数每次都新建选择器, 见其 `@Caution` 注释).
- `pickup(root?, selector, compass?, resultType?, callback?)`: `selector` 可为字符串 / 正则 (content 匹配), `UiSelector`, 对象 (键 = builder 方法名, 由 `Picker.Builder.checkSelectorMap` 反射转换), 数组 (组合); 找到后按 `compass` 导航, 按 `resultType` (`#` widget, `{}` collection, `[]` widgets, `$` content, `.` point, `@` selector, `?` existence) 转换.
- `detect(w, compass?, resultType?, callback?)` 是 `pickup` 的节点版.
- 罗盘 (compass) 语法由 `CompassParser` 定义: `p[p\d]*` (上溯, 多段求和), `c-?\d+([>c]?-?\d+)*` (逐级子节点, 负数从末尾计), `s[<>]?-?\d+` (兄弟: 绝对 / 相对), `k[k\d]*` (自身及至多 n 级祖先中首个可点击节点, 无则 `null`, D6). `%` 表示透传. 行走由 `Compass.navigate(start, compass | steps)` 在任意 `UiNode<N>` 上完成 (P2.1 起), `UiObject.compass()` 只补充本地化错误信息.
- `UiNode<N : UiNode<N>>` (P2.1): 过滤器, 搜索算法与罗盘所面向的只读视图, 成员即 `UiObject` 面向脚本的方法 (`text()` / `desc()` / `content()` / `id()` / `idHex()` / `className()` / `packageName()`, 八个边界标量, 17 个布尔属性, 集合信息, `depth()` / `indexInParent()` / `childCount()` / `child(i)` / `parent()`, `actionNames()` / `hasAction()` / `performAction()`, `recycle()`); 自类型参数使 `parent()` / `child()` 保持调用方的静态类型. 不提供 Android-free 的边界值对象 (脚本可见的 `bounds()` 返回 `Rect`, 同名冲突).

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

- 策略 (P1.5 起唯一出处): `NodeRecyclePolicy.forSdk(Build.VERSION.SDK_INT)`, API >= 33 为 `NO_OP` (`AccessibilityNodeInfo.recycle()` 本就是空操作, 现在连调用都省去), API < 33 为 `RECYCLE`. `NodeLifecycle.default` 是进程级实例; `release` 只在策略要求时执行回收, 抛出的异常 (节点已回收等) 记 `Log.w` 并吞掉, 不再打印堆栈也不中断查找.
- 回收点: DFS / BFS 对非命中且非根的节点 `lifecycle.release`, 命中上限后对已获取未遍历的节点也释放 (P0 加入, P1.5 改为经生命周期对象; `SearchAlgorithm.search` 的末位参数 `lifecycle` 供测试注入); `ScrollAction` / `ScrollMaxAction` / `UiObject.isShifted` 的探针 / `AccessibilityService.recycleFastRootInActiveWindow` / 诊断 (`A11yServiceDiagnostics`, `A11yServiceSelfCheck`) 同样经此. `UiObject.recycle()` 重写是回收本身 (有分配器时转 `allocator.recycle(this)`, 顺带从登记表移除).
- `AccessibilityNodeInfoAllocator`: 仍不接入生产路径 (`ScriptRuntime.selector()` 与 `auto.root` 等的 `allocator` 恒为 `null`), 但已是可用的参考实现: 以 `NodeLifecycle` 决定回收, 登记表 `NodeRegistry` 按对象同一性 (原 `HashMap` 按节点 id 相等去重, 指向同一视图的两个实例只回收其一), 无需回收的平台上不登记; compat 重载改在 `unwrap()` 后的原始节点上操作 (原先调用包装对象自身的访问器, 与 `UiObject` 的重写互相回调而无限递归, 这也是它此前无人使用的原因); `global` 已删除, `NONE` 保留 (不登记). 不接入的原因 (P1.5 评估, 见 KDoc): API < 33 节点按进程池化, 脚本自行回收过的实例可能已被其它脚本的 `obtain()` 复用, 退出时的批量回收会回收到别人的活节点 (API 24 模拟器实测); 不回收只是失去池化, 回收错误会跨脚本损坏状态.

### 1.7 已知平台行为与已知问题

| 现象 | 性质 | 处理 |
| --- | --- | --- |
| 一次窗口切换收到 2 个以上 `TYPE_WINDOW_STATE_CHANGED`, `eventTime` 相差几十毫秒, 包名 / 类名相同 | 平台行为 (窗口添加与获得焦点 / 标题变化各发一次); 服务配置 `notificationTimeout="0"`, 系统不合并 | `auto.registerEvent(name, cb, { distinct: 'window', within: 500 })` 或 `{ debounce: ms }` (P1.3); 脚本侧手工去重示例仍在 `sample/accessibility/smoke-legacy-api.js` |
| `AccessibilityEventWrapper` 的 `source` (父链求 depth + 扫描兄弟求 index) 成本高 | 成本问题; API < 33 事件在回调返回后被回收 | P0 给索引查找加界并捕获异常; P1.3 改为首次访问时解析, `seal()` 后 API < 33 上未解析的为 null (回调内读取不受影响) |
| `onServiceConnected` 偶尔不触发 | 平台问题 | 事件到达即视为已连接 (现有兜底), P1.1 纳入状态表 |
| 服务重启后脚本的事件回调 / 按键监听失效 | 原实现把回调存在服务实例上 | P1.3 起订阅位于伴生对象的总线, 跨重启存活 |
| 服务重绑后 `rootInActiveWindow` 持续为 `null` 直到下一次窗口变化 (AVD API 26 于 2026-09-05 13:16 实测 > 11 s, `windows` 仍有 3 项; 同日晚间 12 轮重启探针未能复现, 重绑后 24 ~ 52 ms 即可用) | 平台行为 (AOSP 8.0 `AccessibilityManagerService`: 重算窗口列表时丢弃尚未重新注册连接的应用窗口, 焦点窗口缺失则活动窗口保持无效, 且带 `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` 时 `TYPE_WINDOW_STATE_CHANGED` 不更新活动窗口); 有其它无障碍服务常驻的设备与现代系统未见; 出现条件未明 (偶发) | 此状态下 `find*` / `pickup` 默认为空 (`windowRoots()` 返回空列表而非 `[null]`); P1.4 起脚本可 `auto.setFlags(['appWindowsFallback'])` 以窗口列表中的应用窗口代替 (默认关闭, 因未能复现而不改默认行为); 布局分析器固定使用 活动窗口 -> fast root -> 应用窗口 的顺序, 且 UI 触发点在服务刚启动后经 `captureCurrentWindowWithRetry` 重试 3 s, 耗尽时 toast `error_active_window_root_unavailable`; 脚本侧兜底策略 (回落 `windows` / 主动制造窗口变化) 由 P1.4 `WindowRootPolicy` 决定, 冒烟脚本在失败时打印窗口列表并制造一次窗口变化以收集数据 |
| API <= 28 上 `home()` 之后 5 s 内, 后台脚本的 `app.launch()` 等活动启动被系统挂起, 到 5 s 期满才执行 (logcat `ActivityManager: Activity start request from <uid> stopped`; 实测 AVD 24 / 25 / 26 与 G8441 上目标窗口的 `WINDOW_STATE_CHANGED` 一律在 `home()` 后 +5.1 ~ 5.3 s 到达) | 平台行为 (`home()` 注入 HOME 键 -> `PhoneWindowManager.launchHomeFromHotKey` -> `stopAppSwitches()`, 无 `STOP_APP_SWITCHES` 权限的后台调用方在 `APP_SWITCH_DELAY_TIME` 内的启动进入 `mPendingActivityLaunches`); API 29+ 未观察到 | 脚本侧在 `home()` 后等待目标包名前置而非固定 sleep (冒烟脚本 `bringAutoJs6ToFront()`); 运行时不做处理 (P6 文档标注 `app.launch` / `home()`) |
| 保护模式开启且 AutoJs6 前置时选择器全部为空 | 按设计 (黑名单, 见 1.4) | 文档化 (P1.4); 冒烟脚本前置检查 `guard mode is off` |
| 强制停止 AutoJs6 后服务变为 "未启用" (抽屉开关关闭, 无 "故障" 副标题) | 平台行为 (`AccessibilityManagerService` 的 `PackageMonitor.onHandleForceStop` 把被强制停止的包的服务移出启用列表) | 与用户显式关闭同等对待, 不自动重新启用 (Roadmap Q8 待决, 建议保持); 偶见的一次性 "故障" 副标题是抽屉同步读到系统尚未更新的列表 |
| 由 application / service context 构建的悬浮 UI (悬浮按钮的菜单 / 对话框 / toast, 布局分析器窗口) 语言与夜间模式跟随系统 | 平台行为 (AppCompat 的应用内语言与夜间模式只应用于其 Activity) | `ContextUtils.withHostConfiguration()`: 空 `Configuration` 仅覆盖 `uiMode` 夜间位 (按 `AppCompatDelegate.getDefaultNightMode()`) 与 locale (`Language` 偏好非 AUTO 时), 其余字段跟随 base; 接入 `CircularMenu`, `LayoutFloatyWindow.onCreate`, `AutoJs.kt` 与磁贴传给布局分析窗口的 context. 应用内切换语言 / 夜间模式时 `MainActivity` 重建并重建悬浮菜单 |
| `find()` 的黑名单只检查窗口根节点的包名 | 设计限制 | P2.4 可配置 |
| API < 33 上 `AccessibilityNodeInfo` 按进程池化: `recycle()` 过的实例会被后续任何 `obtain()` 复用 (AutoJs6 全部脚本同进程), 因此 "记住实例, 稍后批量回收" 的方案会回收到已被复用的活节点 (AVD API 24 实测: 脚本自行回收后, 退出时的第二次回收不报 "已回收" 而是成功) | 平台行为 | 不做退出时的批量回收 (P1.5 决定); 回收只在明确拥有节点的位置进行 (DFS / BFS 的非命中节点, 滚动动作的临时节点, 探针) 且经 `NodeLifecycle` |
| AVD API 24 上对已启用本服务的应用再次 `adb install -r` 后, 无障碍管理器不再绑定本服务 (`dumpsys accessibility` 为 `services:{}`, 设置项仍列出本服务, 应用报 "已启用但未运行"); 改写 / 删除并重写 `enabled_accessibility_services`, 启动应用, `am force-stop` 均无效 | 平台行为 (2026-09-05 观察到一次, 同一天更早的一次重装未触发; 原因未查) | 重启模拟器; 真机未见. 注意 API 24 的 `settings put secure ... ""` 会报用法错误 (空值), 清空须用 `settings delete` (与 P1.2 root store 的引号处理是同一类问题) |
| 无参 `findOne()` / `untilFind()` / `UiSelector.performAction` 无界阻塞 | 既有 API 语义 (D2 保持) | 文档标注; P3 提供带超时的链式替代 |

### 1.8 测试基础设施

- JVM 单元测试为纯 JUnit4 (无 Robolectric). 任务名 `:app:testAppDebugUnitTest`.
- `UiObject(null)` 可在 JVM 中构造 (androidx `AccessibilityNodeInfoCompat` 是真实库代码, 仅调用 Android 桩方法时失败), 但 P2.1 起夹具不再依赖这一点: `app/src/test/java/org/autojs/autojs/core/automator/FakeUiNode.kt` 是纯 `UiNode<FakeUiNode>` 实现, 以 `FakeUiNode.root { child("a") { text = "Login"; clickable = true; bounds(0, 0, 100, 50) } }` 构建确定性树, 每个属性都是可赋值字段, 记录 `performedActions` 与 `isRecycled`; 用于 `SearchAlgorithmTest`, `CompassTest`, `FilterTest`, 也是 P2.3 / P3 / P4 测试的统一夹具. 依赖设备尺寸换算 (`DisplayUtils` / `ScreenMetrics`) 的过滤器暂不能在 JVM 上求值 (P2.3 处理).
- Android-free 的纯逻辑及其测试: `CompassParser` / `Compass` (`CompassParserTest`, `CompassTest`), 过滤器与 `Selector` (`FilterTest`), `Global.normalizeWaitArguments` / `resolveWaitPolicy` / `isWaitConditionSatisfied` (`GlobalWaitCompatibilityTest`), `SelectorMethodResolver` (`SelectorMethodResolverTest`), `A11yServiceRegistry` (`A11yServiceRegistryTest`), `A11yServiceController` 及策略 (`A11yServiceControllerTest` 以假策略模拟系统的绑定 / 解绑, `SettingsStoreStartStrategyTest` 以内存 store, `EnabledA11yServicesTest`), `A11yEventBus` (`A11yEventBusTest`: 假时钟 + 假延迟调度器, 含锁定服务分发顺序的用例), `A11yServiceInfoFormat` (`A11yServiceInfoFormatTest`), `WindowRootResolver` (`WindowRootResolverTest`: 以可脚本化的假 `Source` 覆盖模式 / 过滤器 / 兜底 / 异常隔离), `NodeRecyclePolicy` / `NodeLifecycle` / `NodeRegistry` (`NodeLifecycleTest`, `NodeRegistryTest`; `SearchAlgorithmTest` 显式注入 `NodeLifecycle(RECYCLE / NO_OP)`).
- 真机冒烟: `sample/accessibility/smoke-legacy-api.js` (53 项, 无无人值守启动方式时 42 项). 通过 adb 运行时控制台不可见, 可用一个包装脚本在同一引擎内 `eval` 冒烟脚本并把 `console` 输出镜像到 `/sdcard` 的文件 (2026-09-05 用此方式在 8 台设备上复跑); 注意 Rhino 把循环体内直接声明的 `const` 视为只赋值一次, 探针脚本的逐轮读取应放在函数里.

### 1.9 基准数据

尚无. P2.4 / P5.5 补充: 全树遍历一次的耗时 (按节点数分档), `FLAG_FIND_ON_UI_THREAD` 与 `MODE_FAST` 的影响, `findOne` 轮询 50 ms 的 CPU 占用.

---

## 2. 目标

### 2.1 分层

```
JS 表面层 (runtime/api/augment/**)         auto / automator / selector / flow / global(wait*)
    |  仅做参数解析与返回值适配, 不含业务逻辑
核心层 (core/accessibility, core/automator)  Android 相关, 但业务逻辑依赖抽象接口
    |  A11yServiceRegistry / A11yServiceController / A11yEventBus / WindowRootProvider / NodeLifecycle / A11yScreenshotter  [P1 已落地]
    |  UiSelector / UiObject (实现 UiNode) / UiObjectCollection / filter / search / pick / flow / toolkit
纯 Kotlin 逻辑 (Android-free)                 A11yServiceRegistry / A11yServiceController / A11yEventBus / WindowRootPolicy + WindowRootResolver / NodeRecyclePolicy + NodeLifecycle + NodeRegistry (P1, 已落地) / UiNode + 过滤器谓词 + DFS / BFS + Compass (P2.1, 已落地) / PollPolicy / Stabilizer / FlowScheduler / SelectorRegistry / CompassParser
    |  JUnit4 直接测试
```

P1 的组件均为 "Android-free 核心 + 薄 Android 绑定" 的形态: 注册表 / 控制器 / 总线 / 解析器 / 生命周期对象是泛型或以注入的访问器工作, `AccessibilityService` 伴生对象, `AccessibilityTool`, `WindowRootProvider`, `NodeLifecycles.kt` 是它们的 Android 绑定.

设计原则 (与 Roadmap 第 4.1 节一致): 单一事实来源; 依赖注入而非静态查找; Android-free 核心; 显式注册表替代反射表 (且注册表必须可驱动代码生成, D9); 兼容外壳 (JS 可见类保留 FQN 与 public 签名).

### 2.2 迁移映射

| 现有 | 目标 | 阶段 |
| --- | --- | --- |
| `AccessibilityService` 伴生对象的静态状态 | `A11yServiceRegistry` (3 态 `State` + 多监听者 + `await`), 伴生对象保留同名成员作为外壳 | P1.1 (已完成 2026-09-05) |
| `AccessibilityTool` (12 处实例) | `A11yServiceController` 单例 + `ServiceStartStrategy`; `AccessibilityTool` 变薄为 Android 门面 (未弃用, 实例 7 处) | P1.2 (已完成 2026-09-05) |
| `eventBox` / `delegates` / 按键观察者 / 手势分发 | `A11yEventBus` (ownerId, 类型集合, 优先级, 可消费, 去重 / 去抖选项); 三个实例 `eventBus` / `keyBus` / `gestureBus` | P1.3 (已完成 2026-09-05) |
| `AccessibilityBridge.java` 的 3 处根节点逻辑 | `WindowRootPolicy` (数据) + `WindowRootResolver` (Android-free 实现) + `WindowRootProvider` (Android 绑定), `AccessibilityBridge` 转 Kotlin 保留 FQN | P1.4 (已完成 2026-09-05) |
| 内联 `recycle()` 与 `AccessibilityNodeInfoAllocator` | `NodeLifecycle` / `NodeRecyclePolicy` (API >= 33 全 no-op), 分配器按 `ScriptRuntime` 作用域并以 `NodeRegistry` 登记 | P1.5 (已完成 2026-09-05) |
| `service/AccessibilityService.kt` | `AccessibilityServiceItemHelper` (仅抽屉使用) | P1.2 (D8, 已完成 2026-09-05) |
| `UiSelector` 手写 builder + `checkSelectorMap` 手工表 + `Selector.kt` 反射注册 | `SelectorRegistry` (数据) -> 生成 builder (D9) + `SelectorMapParser` + 白名单注册 | P2.2 |
| 复制粘贴的过滤器族 | `StringPropertyFilter` / `MetricsFilter` 泛化 | P2.3 |
| `findOne` / `untilFind*` 各自的轮询 | `PollPolicy` + `UiSelectorQuery`, 有界 UI 线程跳转 | P2.4 |
| 过滤器 / 搜索 / 罗盘直接依赖 `UiObject` (`AccessibilityNodeInfoCompat`) | `UiNode<N>` 接口 + `FakeUiNode` 夹具, `UiObject : UiNode<UiObject>` | P2.1 (已完成 2026-09-05) |
| `UiObject` 中的 `compass` / `Detector` / `summary` | `core/automator/pick/**` (P0 已迁出解析器, P2.1 已迁出 `Compass` 导航) + `UiObjectSummary.kt` | P2.5 (罗盘部分已完成 2026-09-05) |
| 无 | `core/automator/flow/**` (`FlowScheduler` / `Stabilizer` / `FlowExecutor`) + `runtime/api/augment/flow/**` (`waitAsync` / `flow` / `$flow` / `auto.wait`) | P3 |
| 无 | `core/automator/toolkit/**` + `automator.*` 与全局函数 (D10) + Flow 步骤 | P4 |
| `SimpleActionAutomator.captureScreen` 内联的截图回调; Node Bridge / Python Host 各自的 `hasOperationalState` 三元判定 | `A11yScreenshotter`; `registry.isOperational` | P1.6 (已完成 2026-09-05) |

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
| bridge | `AccessibilityBridge`, 每个脚本运行时一个, 持有模式 / 标志 / 窗口过滤器 (合为一个 `WindowRootPolicy`), 根节点获取委托 `WindowRootProvider` |
| 拾取 (pickup) | 以多种形态的选择器一次性完成 "查找 + 导航 + 结果转换" |
| 探测 (detect) | 对已有节点执行 "导航 + 结果转换" |
| 罗盘 (compass) | 描述相对导航的紧凑字符串 (`p` / `c` / `s` / `k`) |
| ownerId | 脚本运行时在事件回调盒中的标识, 用于按脚本注销 |
| Flow | P3 引入的 Promise 支撑的链式异步等待 / 动作对象 |
| toolkit | P4 引入的高阶组合动作 (智能点击, 滚动查找, 弹窗关闭等) |
