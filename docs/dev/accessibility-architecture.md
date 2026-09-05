# AutoJs6 无障碍自动化架构 (Accessibility Automation Architecture)

本文是无障碍 (a11y) 自动化体系的架构说明, 与 [accessibility-automation-roadmap.md](accessibility-automation-roadmap.md) 配套:
Roadmap 负责 "做什么, 做到哪了", 本文负责 "现在长什么样, 目标长什么样, 为什么".

维护约定:

1. "现状" 章节描述当前 `master` 的真实结构, 每个阶段 (P0-P6) 结束时更新, 以符号名 (类 / 方法) 而非行号为锚点.
2. "目标" 章节与 Roadmap 第 4 节保持一致; 若两者冲突, 以 Roadmap 的固定决策为准并回改本文.
3. "评估记录" 章节保存需要跨会话记忆的技术评估结论 (如 builder 代码生成方案), 避免重复评估.
4. 只用半角标点.

---

## 1. 现状 (2026-09-05, P1.1 完成后)

### 1.1 分层与类映射

| 层 | 职责 | 现有类 (均位于 `app/src/main/java/org/autojs/autojs/`) |
| --- | --- | --- |
| JS 表面 | 参数解析, 返回值适配, 注册全局函数 / 命名空间 | `runtime/api/augment/automator/Auto.kt` (`auto` 对象与 `auto()` 调用), `runtime/api/augment/automator/Automator.kt` (`automator` 与全局动作 `click` / `swipe` / `back` 等), `runtime/api/augment/selector/Selector.kt` (`selector` 与反射注册的 `text()` / `id()` 等全局选择器函数, `pickup` / `detect` / `existsAll` / `existsOne`), `runtime/api/augment/global/Global.kt` (`wait` / `waitForActivity` / `waitForPackage` / `sleep`), `runtime/api/augment/global/Classes.kt` (把 `UiSelector` / `UiObject` / `UiObjectCollection` / `AccessibilityBridge` 作为 JS 类暴露) |
| 服务与桥接 | 服务生命周期, 状态, 启停, 窗口根节点, 事件分发 | `core/accessibility/registry/A11yServiceRegistry.kt` (P1.1 新增, Android-free: 绑定实例, 3 态状态, 状态监听者, `await`; 进程单例为 `AccessibilityService.registry`), `core/accessibility/registry/ConnectionListeners.kt` (旧 `AccessibilityServiceCallback` 形态的适配器), `core/accessibility/AccessibilityService.kt` (服务本体; 伴生对象只剩对注册表的兼容外壳, delegate 表, 默认 bridge, 静态按键观察者, P1.7 起另有实例级事件计数与伴生级轻量事件观察者 `addEventObserver` / `removeEventObserver`), `core/accessibility/A11yEventRecord.kt` (P1.7 新增, 事件字段的普通拷贝), `core/accessibility/AccessibilityServiceUsher.kt` (清单中注册的子类, 负责 `serviceInfo` 标志), `core/accessibility/AccessibilityTool.kt` (启停与谓词, 12 处各自实例化), `core/accessibility/AccessibilityBridge.java` + `AccessibilityBridgeImpl.java` (每个 `ScriptRuntime` 一个, 持有模式 / 标志 / 窗口过滤器, 提供 `windowRoots()`; 构造函数已无全局副作用), `core/accessibility/AccessibilityServiceAutoStart.kt` (Activity 创建时的自动恢复), `service/AccessibilityService.kt` (抽屉开关辅助类, 待按 D8 重命名) |
| 诊断与管理 (P1.7 新增) | 状态行, 诊断文本, 自检, 服务管理器 UI | `core/accessibility/diagnostics/A11yServiceDiagnostics.kt` (派生状态 `NOT_ENABLED / MALFUNCTIONING / CONNECTED / OPERATIONAL`, 15 个状态行, `Report.toText()`), `core/accessibility/diagnostics/A11yServiceSelfCheck.kt` (有界节点遍历 + 等待事件), `core/accessibility/diagnostics/A11yServiceInfoFormat.kt` (Android-free 位掩码与时长格式化), `ui/accessibility/A11yServiceManagerDialog.kt` (抽屉 "无障碍服务" 条目的管理器对话框: 状态 / 控制 / 设置), `ui/accessibility/A11yEventMonitorDialog.kt` (实时事件监视器, 消费伴生级事件观察者) |
| 动作 | 节点动作, 全局动作, 手势, 截图 | `core/accessibility/SimpleActionAutomator.kt` (每个 `ScriptRuntime` 一个; 含 `registerEvent` / `removeEvent` 的 ownerId 语义与 `captureScreen`), `core/automator/GlobalActionAutomator.kt` (手势与全局动作, 使用 servant Looper), `core/automator/UiObjectActions.kt`, `core/automator/action/**` (滚动等复合动作) |
| 查询 | 选择器构建, 搜索, 拾取, 导航 | `core/accessibility/UiSelector.kt` (builder + `find*` + `pickup` 的 `Picker`), `core/automator/UiObject.kt` (`AccessibilityNodeInfoCompat` 包装 + 导航 + `detect` 的 `Detector` / `Invoker` / `Interceptor`), `core/automator/UiObjectCollection.kt`, `core/automator/filter/**` (39 个过滤器), `core/automator/search/{DFS,BFS}.kt`, `core/automator/pick/CompassParser.kt` (Android-free, P0 新增) |
| 事件消费方 (框架内部) | 以 `AccessibilityDelegate` 形式挂在服务上 | `core/activity/ActivityInfoProvider` (优先级 100, 维护当前包名 / Activity), `core/accessibility/AccessibilityNotificationObserver` (200, 通知与 Toast), `core/record/accessibility/AccessibilityActionRecorder` (300, 录制), 注册点 `AbstractAutoJs.kt` |
| 事件包装 | 投递给 JS 回调的事件对象 | `core/automator/AccessibilityEventWrapper.kt` (急切解析 `source`, 见 1.7) |
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

启动方式 (`AccessibilityTool.startService`): 已运行则直接返回; 否则 Root (`settings put secure ...`, 值加单引号以允许空列表), `WRITE_SECURE_SETTINGS` 权限, 否则跳转系统设置页由用户手动开启. root / 安全设置的启停以 `hasService()` 回读为准. `restartService` 在禁用后先 `waitForStopped()` (注册表 `awaitUnbound`, 2000 ms) 再启用, 避免被系统合并为 "仍然启用". `AccessibilityServiceAutoStart` (每个 `BaseActivity` 创建时) 只恢复 `isMalfunctioning()` 的服务, 不会重新启用用户显式关闭的服务. "确保可用" 有 4 个近似变体 (`startServiceAndWaitFor` / `startServiceAndWaitForOperational` / `ensureService` / `ensureServiceOperational`), 默认等待 `DEFAULT_A11Y_SERVICE_START_TIMEOUT = 2000 ms`. (P1.2 合并)

已知平台问题: `onServiceConnected` 偶尔不回调但事件照常到达; 注册表的 `onEvent` 会直接采纳实例并置 `OPERATIONAL`, 等待方与状态监听者 (含 `auto.stateListener` 的 `onConnected`) 在这条路径上同样被唤醒 / 通知.

默认 bridge: `AccessibilityService.bridge` 是伴生对象级的 `defaultBridge`, 由 `AbstractAutoJs.createRuntime()` 在创建每个脚本运行时时登记为最近一次创建的 bridge, 供无参 `UiSelector()` (脚本中的 `new UiSelector()`) 使用, 与服务实例的生死无关. `pickup` / `detect` 的 `Picker.Builder` 则直接绑定调用方 `scriptRuntime.accessibilityBridge`.

### 1.3 事件流

`AccessibilityService.onAccessibilityEvent(event)` 在服务主线程串行执行 4 步:

1. `registry.onEvent(this)`: 首个事件把状态置为 `OPERATIONAL` (并在 `onServiceConnected` 未回调时采纳实例); 已是 `OPERATIONAL` 的同一实例不产生迁移.
2. JS 回调盒 `eventBox[type][ownerId]`: 每个脚本 (`ownerId` = `SimpleActionAutomator.mA11yEventOwnerId`) 每种事件类型一个槽; 有回调时创建 `AccessibilityEventWrapper` 并逐个调用, 异常被捕获记录.
3. `delegates` (按优先级排序的 `AccessibilityDelegate`): 仅当有 delegate 关心该事件类型时执行; 先在 `TYPE_WINDOW_STATE_CHANGED` / `TYPE_VIEW_FOCUSED` 时更新 `fastRootInActiveWindow`, 然后按优先级调用, 返回 `true` 的 delegate 会中断链.
4. (无第 4 步; `fastRootInActiveWindow` 的更新与第 3 步耦合是 Roadmap P1.3 要解耦的点.)

按键事件 (`onKeyEvent`) 与手势 (`onGesture`) 各自投递到 `eventExecutor` (单线程池, 服务销毁时关闭), 分别走静态 `stickOnKeyObserver` + 实例 `onKeyObserver`, 以及 `gestureEventDispatcher`.

脚本退出时 `SimpleActionAutomator.removeAllEventsForThisRuntime()` 只清理本脚本 ownerId 的回调.

### 1.4 查找与拾取

`text('登录').findOne(3000)` 的调用链:

```
UiSelector.findOne(timeout)        主线程禁止; 循环 find(1), 间隔 intermission() = 50 ms
  -> UiSelector.find(1)            mA11yTool.ensureService()
    -> findInternal(1)             若 FLAG_FIND_ON_UI_THREAD: post 到 UI 线程并 blockedGet (无超时)
      -> findImpl(roots, 1)        roots = bridge.windowRoots() (受 mode / flags / 窗口过滤器影响), 跳过黑名单包名
        -> searchAlgorithm.search(UiObject.createRoot(root), selector, max)   DFS (默认) 或 BFS
```

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
| 一次窗口切换收到 2 个以上 `TYPE_WINDOW_STATE_CHANGED`, `eventTime` 相差几十毫秒, 包名 / 类名相同 | 平台行为 (窗口添加与获得焦点 / 标题变化各发一次); 服务配置 `notificationTimeout="0"`, 系统不合并 | 脚本侧按 (包名, 类名, 时间窗口) 去重 (见 `sample/accessibility/smoke-legacy-api.js`); P1.3 为 `auto.registerEvent` 增加可选去重 / 去抖 |
| `AccessibilityEventWrapper` 急切解析 `source` (父链求 depth + 扫描兄弟求 index) | 成本问题; 急切是因为 API < 33 事件在回调返回后可能被回收 | P0 已给索引查找加界并捕获异常; P1.3 改为按需解析 |
| `onServiceConnected` 偶尔不触发 | 平台问题 | 事件到达即视为已连接 (现有兜底), P1.1 纳入状态表 |
| `find()` 的黑名单只检查窗口根节点的包名 | 设计限制 | P2.4 可配置 |
| 无参 `findOne()` / `untilFind()` / `UiSelector.performAction` 无界阻塞 | 既有 API 语义 (D2 保持) | 文档标注; P3 提供带超时的链式替代 |

### 1.8 测试基础设施

- JVM 单元测试为纯 JUnit4 (无 Robolectric). 任务名 `:app:testAppDebugUnitTest`.
- `UiObject(null)` 可在 JVM 中构造 (androidx `AccessibilityNodeInfoCompat` 是真实库代码, 仅调用 Android 桩方法时失败). 夹具 `app/src/test/java/org/autojs/autojs/core/automator/FakeUiObject.kt` 以 `FakeUiObject.root { child("a", clickable = true) { child("a0") } }` 构建确定性树, 覆盖 `getChildCount` / `childCount` / `child` / `parent` / `clickable` / `recycle`, 用于 `SearchAlgorithmTest` 与 `UiObjectCompassTest`.
- Android-free 的纯逻辑及其测试: `CompassParser` (`CompassParserTest`), `Global.normalizeWaitArguments` / `resolveWaitPolicy` / `isWaitConditionSatisfied` (`GlobalWaitCompatibilityTest`), `SelectorMethodResolver` (`SelectorMethodResolverTest`).
- 真机冒烟: `sample/accessibility/smoke-legacy-api.js`.

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
| `AccessibilityTool` (12 处实例) | `A11yServiceController` 单例 + `ServiceStartStrategy`; `AccessibilityTool` 变薄并 `@Deprecated` | P1.2 |
| `eventBox` / `delegates` / 按键观察者 / 手势分发 | `A11yEventBus` (ownerId, 类型集合, 优先级, 可消费, 去重 / 去抖选项) | P1.3 |
| `AccessibilityBridge.java` 的 3 处根节点逻辑 | `WindowRootProvider` + `WindowRootPolicy`, `AccessibilityBridge` 转 Kotlin 保留 FQN | P1.4 |
| 内联 `recycle()` 与 `AccessibilityNodeInfoAllocator` | `NodeLifecycle` / `NodeRecyclePolicy` (API >= 33 全 no-op) | P1.5 |
| `service/AccessibilityService.kt` | `AccessibilityServiceItemHelper` (仅抽屉使用) | P1.2 (D8) |
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
