# AutoJs6 无障碍自动化 (Accessibility Automation) 重构与功能 Roadmap

本文是无障碍服务 (a11y) 自动化体系 "重构 + 功能" 两条主线的可执行状态表.
以 2026-09-04 的本地代码快照 (`master@d38871887`, `VERSION_NAME=6.8.0`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号), 证据等级见附录 D.
3. 涉及 JS 公开 API 的条目, 完成后必须同步 `AGENTS.md` 中列出的关联仓库 (文档 / d.ts / Ace LSP 声明) 与 `.changelog`.
4. 附录 C 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".

---

## 1. 固定决策

以下决策已由维护者确认 (D1-D5 于 2026-09-04, D6-D12 于 2026-09-05), 后续阶段不再重新讨论:

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 全局 `wait()` 保持同步语义, 新增异步链式入口 | `wait()` 的签名, 返回值, `wait(cond, {then, else})` 回调对象重载, `limit < 100` 视为次数等既有行为全部不变. 新增全局 `waitAsync()` (别名 `wait.async()` 与 `auto.wait()`) 作为链式起点, `waitThenClick` / `waitForStable` 等全新函数默认返回链式对象, 链式对象提供 `.sync()` / `.await()` 同步桥接. 注意 `auto.waitFor()` (既有, 同步等待服务就绪) 与 `auto.wait()` (新增, 异步等待条件) 相邻, 文档与 d.ts 必须明确区分. |
| D2 | 既有 JS 公开 API 完全兼容 | 所有现有 JS 函数 / 方法 / 属性 (含 `@Deprecated` 的 `idMatches` 系列, 无参 `findOne()`, `waitFor()`, `brother()` 等) 的签名与行为保持不变. 重构只发生在 Kotlin 内部, 或以新增 API 的形式出现. 明确的 bug (如 BFS 多返回一个结果) 可修复并记入 changelog `fix` 分类. |
| D3 | Roadmap 位于 `docs/dev/` | 与仓库内既有 roadmap (如 `yolo-plugin-roadmap.md`) 的位置与风格一致. |
| D4 | 2026-09-04 会话先产出本 Roadmap, 随后从 P0 开始落地 | 代码改动按阶段推进, 每阶段开始前先拍板附录 C 中对应的待决事项. |
| D5 | `automator.isServiceRunning()` 的判定允许修正 | 现实现 (`SimpleActionAutomator.kt:216`) 只检查 `hasInstance()`, 与 `auto.service` / 抽屉开关等处的 "运行中" 语义不一致; P1.2 中统一为注册表的 `isRunning` 语义, 记入 changelog `fix`. |
| D6 | 罗盘 `k` 段范围内无可点击节点时返回 `null` | 此前返回节点自身 (仅当向上越过根节点时才返回 `null`). 已于 2026-09-05 随 P0 落地并记入 changelog `fix`. 与 D5 一样是 D2 之外被明确批准的既有行为调整. |
| D7 | 建立独立的 `docs/dev/accessibility-architecture.md` | 承载分层图, 现有类映射, 线程模型, 事件流, 基准数据与已知平台行为; 每阶段结束时更新其 "现状" 与 "目标" 章节, 避免本 Roadmap 膨胀. (原 Q7) |
| D8 | 核心 `org.autojs.autojs.core.accessibility.AccessibilityService` 不重命名 | `auto.service` 返回其实例, 脚本可能通过 FQN 引用; 仅重命名抽屉辅助类 `service/AccessibilityService.kt`. (原 Q5) |
| D9 | `UiSelector` builder 方法先手写委托, 但自动生成是强烈期望 | P2.2 的 `SelectorRegistry` 必须以 "可生成" 为设计目标 (方法名, 别名, 参数类型, 过滤器工厂全部为数据); P2 结束时必须给出生成方案评估 (候选: KSP 处理器读取注册表生成 `UiSelector` 的 builder 与 d.ts 签名清单; 或 Gradle 任务 + 模板生成 Kotlin 源码), 评估通过即实施, 而非仅停留在评估. (原 Q6) |
| D10 | toolkit 的同步表面提升为全局函数 | 除 `automator.*` 外, `smartClick` / `clickIfExists` / `clickAny` / `findAny` / `scrollUntil` / `typeInto` / `dismissPopups` / `collectList` / `launchAndWait` / `backUntil` / `toggle` / `retry` 等同时注册为全局函数; 便捷性优先于命名空间膨胀. 落地前用当前全局名快照核对冲突 (如 `click` / `toggle` 等已存在或语义相近的名字须避让或统一). (原 Q3) |
| D11 | `flow` 命名空间提供 `$flow` 别名 | 与其它 `Augmentable` 模块的 `$` 前缀别名保持一致. (原 Q2) |
| D12 | Flow 动作步骤默认 `strict = true` | 动作返回 `false` 即 reject `ACTION_FAILED`; 可用 `flow.defaults({ strictActions: false })` 全局关闭. (原 Q1) |
| D13 | 引入字符串选择器语法, 入口复用 `select` | 采用附录 C 的方案 B: `UiSelector` 新增 `select(String)` 重载 (解析字符串并追加过滤器, 返回自身), 因反射注册而自动成为全局函数 `select('...')` (返回 `UiSelector`, 可继续 `.findOne()` 等); 既有无参 `select()` (对找到的节点执行 `ACTION_SELECT`, 含反射出的全局 `select(): boolean`) 保持不变, 两者按参数类型区分. 语法: 空格分隔的条件, `key=value` (值含空格时用引号), `key~=regex` (或 `key=/regex/flags`), 布尔键 `clickable` / `!clickable`, 键名与别名来自 P2.2 的 `SelectorRegistry`; 不引入 `\|` 或层级组合子, 组合需求用 `pickup([...])` / `existsAll` / compass 表达. 落地于 P5.3, 依赖 P2.2. (原 Q4, 2026-09-05) |

由 D2 派生的硬约束:

- 由 `runtime/api/augment/global/Classes.kt` 暴露给 JS 的 Java 类 (`AccessibilityBridge`, `UiSelector`, `UiObject`, `UiObjectCollection`, `GestureResultCallback`) 的全限定类名不可变更, 因为脚本可能通过 `importClass` / `Packages.*` 引用.
- `runtime/api/augment/selector/Selector.kt:45-95` 会反射 `UiSelector` 的全部 public 方法并注册为全局函数, 因此 `UiSelector` 新增的任何 public 方法都会自动成为全局函数. 重构期间新增的内部方法必须为 `internal` / `private`, 或改为显式注册表 (见 P2).
- `app/src/main/java/com/stardust/**` 是 2025-01-15 (`8b0c731d9`) 有意恢复的二进制兼容包, 与本 Roadmap 无关, 全程冻结, 不修改, 不引用, 不删除.

---

## 2. 范围与非目标

范围内:

- `app/src/main/java/org/autojs/autojs/core/accessibility/**` (3,456 行, 22 个文件)
- `app/src/main/java/org/autojs/autojs/core/automator/**` (3,847 行, 59 个文件)
- `app/src/main/java/org/autojs/autojs/codegeneration/{ReadOnlyUiObject,CodeGenerator}.java` (`UiObject` 的直接消费方)
- `app/src/main/java/org/autojs/autojs/runtime/accessibility/AccessibilityConfig.kt`
- `app/src/main/java/org/autojs/autojs/runtime/api/augment/automator/{Auto,Automator}.kt`
- `app/src/main/java/org/autojs/autojs/runtime/api/augment/selector/Selector.kt`
- `app/src/main/java/org/autojs/autojs/runtime/api/augment/global/Global.kt` 中的 `wait` / `waitForActivity` / `waitForPackage`
- 调用上述核心的散落调用点 (见第 4 节 "现状诊断"), 仅做 "改为消费新核心" 的最小改动

非目标 (本 Roadmap 不处理, 但会预留接口):

- `RootAutomator` (`core/inputevent/RootAutomator.java`), 基于 `/dev/input` 而非无障碍服务, 仅同名.
- 布局分析器 UI (`ui/floating/layoutinspector/**`) 与代码生成器 UI (`ui/floating/CodeGenerateDialog.java`). 其依赖的 `LayoutInspector` / `NodeInfo` / `Capture` 保留原接口.
- Python Host (`core/plugin/python/PythonHostSelector.kt` 等) 与 Node Bridge (`engine/NodeBridgeProtocol.kt`) 各自独立实现的选择器与动作层. 它们是 "消费方", 在 P5 中作为可选项改为复用新核心, 不在 P1/P2 中同步改造.
- `com.stardust.**` 兼容包 (见第 1 节).

---

## 3. 现状诊断

以下是 2026-09-04 探查得到的事实, 是各阶段条目的直接依据. 行号以快照 `d38871887` 为准.

### 3.1 无障碍服务层: 状态与生命周期分散在 5 处以上

| 问题 | 锚点 |
| --- | --- |
| 进程级可变静态状态集中在 `AccessibilityService` 伴生对象: `instance`, `hasOperationalState`, `bridge` (读写代理), `delegates` (只增不减, 无 `removeDelegate`), `LOCK/ENABLED/OPERATIONAL`, 单槽 `callback` 与 `disconnectCallbacks` 两套并存的回调注册, 静态 `stickOnKeyObserver` 与实例 `onKeyObserver` 并存 | `core/accessibility/AccessibilityService.kt:299-329, 354` |
| `AccessibilityBridge` 构造函数带全局副作用 (`setBridge(this)`), 而 `UiObject` 每个实例都会惰性创建一个新 bridge, 每次创建都覆盖全局 | `core/accessibility/AccessibilityBridge.java:53`, `core/automator/UiObject.kt:47` |
| `AccessibilityTool` 被实例化 12 次, 无共享实例, 两处以 `null` context 构造 | `AutoJs.kt:59`, `AccessibilityBridgeImpl.java:15`, `AccessibilityServiceAutoStart.kt:31`, `LayoutInspector.kt:21`, `UiSelector.kt:75`, `external/tile/LayoutInspectTileService.java:26`, `augment/automator/Auto.kt:123`, `service/AccessibilityService.kt:11`, `ui/floating/CircularMenu.java:133`, `ui/main/drawer/DrawerFragment.kt:181`, `ui/main/MainActivity.kt:131` |
| "确保服务可用" 有 4 个近似实现 (`startServiceAndWaitFor`, `startServiceAndWaitForOperational`, `ensureService`, `ensureServiceOperational`), 3 个分支产出同一条错误文案; `ensureServiceStarted` 有 9 个调用点, 其中 `UiSelector.kt:715` 与 `AutoJs.kt:89,95` 绕过 bridge 直接使用自有 `AccessibilityTool` | `core/accessibility/AccessibilityTool.kt:137-199`, `core/accessibility/UiSelector.kt:715`, `AutoJs.kt:89,95` |
| "服务是否运行" 判定不一致: `service/AccessibilityService.kt:14` 用 `hasService() \|\| isRunning()` (或), `SimpleActionAutomator.kt:216` 只看 `hasInstance()`, Node Bridge 与 Python Host 各自复制三元表达式 | `service/AccessibilityService.kt:14`, `core/accessibility/SimpleActionAutomator.kt:216`, `engine/NodeBridgeProtocol.kt:1519,1525,3418`, `engine/NodeBridgeProviderRegistry.kt:774`, `core/plugin/python/PythonHostCapabilityBroker.kt:948` |
| 生命周期入口 5 处: `AccessibilityService` 本体, `AccessibilityServiceUsher` (清单注册), `LayoutInspectService` (未注册, 死代码, 含永远为 null 的 `instance`), `service/AccessibilityService.kt` (抽屉开关辅助类, 与核心类同名), `AccessibilityServiceAutoStart` (每个 Activity 创建时触发) | `core/accessibility/LayoutInspectService.kt:19`, `service/AccessibilityService.kt:16-40`, `ui/BaseActivity.kt:71` |
| `onAccessibilityEvent` 内串行跑 4 条流水线 (操作态标记, JS 回调盒, delegate 链, `fastRootInActiveWindow` 更新), 且 `fastRootInActiveWindow` 只在有 delegate 命中时才更新; 按键事件走静态 + 实例两个观察者, 手势走第三套 `EventDispatcher` | `core/accessibility/AccessibilityService.kt:106-164, 170-212` |
| 窗口根节点获取逻辑在同一文件内重复 3 次, 且 `getRootInActiveWindow()` 静默丢弃了窗口过滤器分支; `windowRoots()` 可能返回含 `null` 的 `singletonList`, 迫使 Kotlin 调用方各自 `filterNotNull()` | `core/accessibility/AccessibilityBridge.java:98-150` |
| 节点回收策略 4 套互不一致: 分配器仅在 API < 33 回收, 而 `recycleFastRootInActiveWindow` 与 DFS/BFS/Scroll* 内联 `.recycle()` 无 API 判断; `add()` 无条件为每个节点捕获完整线程堆栈字符串; 分配器使用非线程安全 `HashMap`. 另外, 分配器在生产路径中恒为 `null` (`ScriptRuntime.kt:552` -> `UiSelector(bridge)` -> `allocator = null`), `global` 与 `NONE` 无引用, 即分配器实际上是死代码, P1.5 可直接以 `NodeLifecycle` 取代而非适配 | `core/accessibility/AccessibilityNodeInfoAllocator.kt:16,41,54,65,115`, `core/accessibility/AccessibilityService.kt:56-61`, `core/automator/search/{DFS,BFS}.kt` |
| `CloseableManager` 类的文件名拼写错误 (`CloaseableManager.kt`), 类本身被 `ScriptRuntime.closeableManager` 使用并有测试 | `core/accessibility/monitor/CloaseableManager.kt`, `runtime/ScriptRuntime.kt:212` |
| `AccessibilityEventWrapper` 为投递给 JS 回调的每个事件急切解析 `source` 节点 (向上遍历整条父链求 depth, 再扫描父节点全部子节点求 index), 即使回调不使用 `source`; 索引查找原本无界 (节点树变化时会越界抛出或死循环, 2026-09-05 已加界). 急切解析的原因是 API < 33 上原始事件在回调返回后可能被框架回收 | `core/automator/AccessibilityEventWrapper.kt` |
| 平台行为 (非 bug): 一次窗口切换通常收到 2 个以上 `TYPE_WINDOW_STATE_CHANGED` (eventTime 相差几十毫秒), 服务配置 `notificationTimeout="0"` 因此系统不合并; `auto.registerEvent` 目前不提供去重 / 去抖 | `res/xml/accessibility_service_config.xml`, `sample/accessibility/smoke-legacy-api.js` (含去重示例) |
| 无障碍截图有 2 套实现 (Rhino 与 Python Host) | `core/accessibility/SimpleActionAutomator.kt:269-321`, `core/plugin/python/PythonHostImages.kt:875` |
| 核心路径及其直接消费方中仍有 3 个承担关键角色的 Java 文件 | `core/accessibility/AccessibilityBridge.java` (189), `core/accessibility/AccessibilityBridgeImpl.java` (55, 其中 `:32-33` 重复声明基类已有的 `ensureServiceStarted()`), `codegeneration/ReadOnlyUiObject.java` (333, 从 Java 继承 960 行的 Kotlin `UiObject`, 覆盖约 50 个方法) |
| 已知 Android 问题: `onServiceConnected` 有时不触发, 仅以 FIXME 注释记录 | `core/accessibility/AccessibilityService.kt:241-247` |

### 3.2 UiSelector / UiObject / UiObjectCollection: 巨型文件与三套反射分发

| 问题 | 锚点 |
| --- | --- |
| `UiSelector.kt` 1,225 行: `:87-686` 约 200 个一行式 builder 方法, `:899-996` 手工维护的类型表与 `:1041-1185` 145 行的 map 选择器反射转换 (自带 FIXME "需要更优雅的方式"), 两者是同一签名集合的重复描述, 新增一个选择器方法需同步改 3 处 | `core/accessibility/UiSelector.kt` |
| `UiObject.kt` 960 行: 节点包装 + 导航 (`compass` DSL 每次调用编译 4 个正则) + `Detector/Invoker/Interceptor` 反射机制 (含约 60 个方法名的手工表) + `summary` / `isSimilar` (44 项 `&&`, 会遍历兄弟与子节点) | `core/automator/UiObject.kt:329-409, 459-562, 713-939` |
| 三套独立的反射分发, 各自有一套数值强制转换规则: `SelectorMethodResolver`, `Picker.Builder.checkSelectorMap`, `UiObject.Invoker/Interceptor` | `augment/selector/Selector.kt:465-542`, `UiSelector.kt:1041-1185`, `UiObject.kt:812-939` |
| 过滤器族复制粘贴: `TextFilter` / `DescFilter` / `ContentFilter` / `PackageNameFilter` 仅 getter 不同; `MetricsFilter` / `MetricsMin` / `MetricsMax` / `MetricsRange` 四胞胎共 421 行; `String*Filter` 与 `StringList*Filter` 成对出现 | `core/automator/filter/**` (39 个文件, 1,636 行) |
| BFS 越界: `result.size > limit` (DFS 为 `>=`), `find(1).algorithm("BFS")` 返回 2 个结果; DFS 命中 limit 后已压栈的子节点被整体丢弃且不回收 | `core/automator/search/BFS.kt:20`, `core/automator/search/DFS.kt:21-28` |
| 无界阻塞: 无参 `findOne()` / `untilFind()` 无限轮询, `UiSelector.performAction` (即 `selector.click()`) 继承该行为; 轮询间隔硬编码 `Thread.sleep(50)`, 每次迭代全窗口全树遍历; `FLAG_FIND_ON_UI_THREAD` 下的跨线程 `blockedGet()` 无超时 | `core/accessibility/UiSelector.kt:741-790, 730-738, 1212-1221` |
| `UiSelector` 无参构造绑定到进程级静态 `AccessibilityService.bridge`; `Selector.filters` 为可变 `LinkedList`, `append` 原地修改 (`Selector.kt:74-79` 记录了由此引发过的 bug); `BooleanFilter.cache` 为非线程安全 `HashMap` | `UiSelector.kt:77,823`, `core/automator/filter/Selector.kt:9`, `core/automator/filter/BooleanFilter.kt:23` |
| `UiObject.parent()` 通过扫描祖父节点的所有子节点反推 `indexInParent` (代码自注 "可能导致性能不佳"); `clickBounds()` 每次调用新建一个 `GlobalActionAutomator` 且不含脚本的 `ScreenMetrics` | `core/automator/UiObject.kt:62-74, 205-217` |
| `UiObjectCollection` 缺少 `filter` / `map` / `at` / 迭代协议; `performAction` 对全部节点取与, 无法得知哪个节点失败 | `core/automator/UiObjectCollection.kt` |
| 分配器的 `Compat` 重载 `getParent` 调用 `unwrap()` 后丢弃结果, 从未 `add()` 登记 | `core/accessibility/AccessibilityNodeInfoAllocator.kt:24` |

### 3.3 等待与计时: 只有同步原语, 无稳定检测

| 事实 | 锚点 |
| --- | --- |
| `wait()` 完全同步阻塞 (`Thread.sleep`), 无 UI 线程守卫 (`sleep` / `waitForActivity` / `waitForPackage` 有), `limit < 100` 视为次数, `{then, else}` 回调对象与真正的 Promise 概念冲突, 且是重载判别依据 (`limitArg is NativeObject`) | `augment/global/Global.kt:386-481` |
| Rhino `2.0.0-SNAPSHOT`, `VERSION_ES6`, 解释模式, **不支持 `async/await` 语法** (支持 generator); `Promise` 为 JS polyfill (`assets/modules/promise.js`), 通过 `setImmediate` 驱动, 提供非标准 `.wait()` (阻塞) 与 `.await()` (continuation) | `engine/RhinoJavaScriptEngine.kt:189,302-303`, `ScriptRuntime.kt:420-447` |
| 从 Kotlin 返回 Promise 的现成通道: `ScriptPromiseAdapter` + `toJsPromise()` + `ScriptAsyncDispatcher` (跨线程回投到脚本线程, 有界队列, 终态释放 `waitWhenIdleToken`) | `runtime/api/augment/{PromiseInterop,ScriptAsyncDispatcher}.kt`, `runtime/api/ScriptPromiseAdapter.kt`, `augment/ai/AiSessionNativeObject.kt` (Kotlin `NativeObject` 返回 Promise 的先例) |
| 不存在 `waitForStable` / `waitForVisible` / `waitForHidden` / `waitThenClick` / `retry` / `poll` 等任何原语; `timers.setIntervalExt` 是唯一的 "轮询直到超时" 工具 | `augment/timers/SetIntervalExt.kt` |
| `UiSelector.findOne(timeout)` 有独立的轮询循环, 与 `wait()` 的配置不共享 | `UiSelector.kt:741-753` |

### 3.4 高阶封装: 动作层完整但缺少 "组合"

`automator` 已提供完整的原子动作 (`click` 的坐标 / 点 / 矩形 / UiObject / 文本等多种重载, `press`, `swipe`, `gesture(s)(Async)`, `scrollUp/Down`, `input`, `setText`, 全局动作 `back/home/recents/...`, `captureScreen`), 但没有 "查找 + 等待 + 动作 + 校验" 的组合原语, 脚本作者反复手写: 滚动查找, 弹窗关闭, 智能点击 (不可点击时上溯到可点击祖先或改为手势点击), 输入前清空, 列表采集, 应用启动并等待包名切换等.

---

## 4. 目标架构

### 4.1 分层

```
JS 表面层 (runtime/api/augment/**)         auto / automator / selector / flow / global(wait*)
    |  仅做参数解析与返回值适配, 不含业务逻辑
核心层 (core/accessibility, core/automator)  Android 相关, 但业务逻辑依赖抽象接口
    |  A11yServiceRegistry / A11yServiceController / A11yEventBus / WindowRootProvider / NodeLifecycle
    |  UiSelector / UiObject / UiObjectCollection / filter / search / pick / flow / toolkit
纯 Kotlin 逻辑 (Android-free)                 PollPolicy / Stabilizer / FlowScheduler / SelectorRegistry / 过滤器谓词
    |  可用 JUnit4 直接测试 (本项目 JVM 单元测试不含 Robolectric, Android 类不可构造)
```

设计原则:

1. 单一事实来源: 服务实例, 连接态, 操作态, 回调注册只存在于 `A11yServiceRegistry`, 其它地方只读或订阅.
2. 依赖注入而非静态查找: 每个 `ScriptRuntime` 持有自己的 `AccessibilityBridge` (已有), bridge 从 registry 读取; `UiSelector` / `UiObject` 通过构造参数或 `ScriptRuntime` 获取 `WindowRootProvider` 与手势分发器, 不再自行 `new` 工具类.
3. Android-free 核心: 过滤器, 搜索, 轮询策略, 稳定检测, Flow 调度器面向接口 (`UiNode` 抽象或纯数据快照) 编程, 保证 JVM 单元测试可覆盖.
4. 显式注册表替代反射表: 选择器方法由 `SelectorRegistry` 描述一次, 派生出 builder 分发, map 选择器解析, 全局函数注册与 d.ts 生成所需的签名清单.
5. 兼容外壳: 被 JS 暴露的类 (`UiSelector`, `UiObject`, `UiObjectCollection`, `AccessibilityBridge`) 保留原包名与 public 签名, 内部委托到新组件; `AccessibilityTool` 保留为薄外壳并标记 `@Deprecated`.

### 4.2 目标包结构 (新增部分)

```
core/accessibility/
  registry/    A11yServiceRegistry (含嵌套 State / Snapshot / StateListener), ConnectionListeners  [P1.1 已落地]
  control/     A11yServiceController, ServiceStartStrategy (Root / SecureSettings / SettingsUi)
  event/       A11yEventBus, A11yEventSubscription, AccessibilityDelegate (迁入)
  window/      WindowRootProvider, WindowRootPolicy (mode / flags / filter)
  node/        NodeLifecycle, NodeRecyclePolicy, AccessibilityNodeInfoAllocator (适配为 NodeLifecycle 的实现)
  (原文件保留原位置: AccessibilityService, AccessibilityServiceUsher, AccessibilityBridge, UiSelector, ...)
core/automator/
  selector/    SelectorRegistry, SelectorMapParser, PollPolicy, UiSelectorQuery
  pick/        Picker, Detector, Compass (从 UiSelector / UiObject 迁出)
  flow/        FlowStep, FlowScheduler, FlowExecutor, Stabilizer, FlowErrors
  toolkit/     SmartClick, ScrollSearch, TextEntry, PopupDismisser, ListCollector, AppLauncher
runtime/api/augment/
  flow/        Flow (命名空间 `flow`), FlowNativeObject (链式对象), 全局函数 waitAsync / waitThenClick / ...
```

---

## 5. 阶段总览

| 阶段 | 主题 | 依赖 | 对应用户目标 | 状态 |
| --- | --- | --- | --- | --- |
| P0 | 基线, 安全网与低风险修复 | 无 | 1, 2 | 完成 (2026-09-05, JVM + ANDROID_BUILD + DEVICE x5) |
| P1 | 无障碍服务层重构 | P0 | 1 | 进行中 (P1.1 完成 2026-09-05, 真机复跑待回填) |
| P2 | UiSelector / UiObject / UiObjectCollection 重构 | P0 (P1 的 registry 与 window 子项) | 2 | 未开始 |
| P3 | 异步链式 `waitAsync` (Flow) 与便携函数 | P1 (registry, event bus), P2 (PollPolicy) | 3 | 未开始 |
| P4 | 高阶自动化封装 (toolkit) | P2, P3 | 4 | 未开始 |
| P5 | 能力扩展 (诊断, 事件驱动等待, 跨运行时复用) | P1-P4 | PS | 未开始 |
| P6 | 文档, 声明, 示例与发布 gate | 各阶段 | 全部 | 未开始 |

阶段内条目可并行; 阶段间的依赖只针对被明确标注 "依赖" 的条目. P3 的调度器 (`FlowScheduler`) 是 Android-free 的, 可在 P1/P2 未完成时先行实现并以适配器接入现有代码, 但正式发布前必须切换到 P1/P2 的新组件.

---

## P0: 基线, 安全网与低风险修复

目标: 在不改变任何行为的前提下, 为后续重构建立回归基线, 并修复探查中确认的明确 bug.

### P0.1 回归安全网

- [x] 建立确定性的节点树测试夹具. 结论修正: `UiObject(null)` 在 JVM 测试中可以构造 (androidx `AccessibilityNodeInfoCompat` 是真实库代码, 仅在调用 Android 桩方法时失败), 因此夹具 `app/src/test/.../core/automator/FakeUiObject.kt` 直接继承 `UiObject` 并只覆盖搜索与导航触及的成员 (`getChildCount` / `childCount` / `child` / `parent` / `clickable` / `recycle`), 以 `FakeUiObject.root { child("a") { ... } }` 构建树. 它已可驱动 `DFS` / `BFS` / `UiObject.compass` 的测试; P2.1 引入 `UiNode` 后再扩展为带属性的夹具. 随机行为的 `core/automator/test/TestUiObject.kt` (主源码集, 无引用) 留待 P2 清理. (JVM: `SearchAlgorithmTest`, `UiObjectCompassTest`)
- [x] 为 `SelectorMethodResolver` 现有测试补充边界用例: `null` 到原始类型返回 `null` 而到对象类型可通过, `Double -> Long/Float/Double` 强制转换, 字符串不会被强制为数字. (JVM: `SelectorMethodResolverTest`, 8 用例)
- [x] 为 `Global.wait` 抽出纯函数 `normalizeWaitArguments` / `resolveWaitPolicy` / `isWaitConditionSatisfied` (`internal`, 供 P3 Flow 复用同一真值规则与 limit 语义), `wait` 本体改为调用它们, 行为不变. (JVM: `GlobalWaitCompatibilityTest`, 8 用例, 含 `limit < 100` 视为次数, `interval >= timeout` 折叠为单次, `{then, else}` 重载识别)
- [x] 抽出纯解析器 `core/automator/pick/CompassParser.kt` (`CompassStep` 密封类: `Up` / `Children` / `Sibling` / `Clickable`, 预编译正则), `UiObject.compass()` 改为 "解析后按步骤导航". 顺带修复了多段连写 (`p4ppp12p`, `k2k3`) 仅计入首段的 bug (原因: `findAll` 配合 `^` 锚点只能命中首段), 记入 changelog. 语法错误现在在导航前整体抛出 (原先是边导航边解析, 前置步骤返回 `null` 时可能吞掉后段的语法错误). 2026-09-05 按 D6 把 `k` 段 "范围内无可点击节点时返回自身" 改为返回 `null`, 记入 changelog. (JVM: `CompassParserTest` 8 用例, `UiObjectCompassTest` 6 用例)
- [x] 编写设备冒烟脚本 `sample/accessibility/smoke-legacy-api.js` (基于维护者 2026-09-05 提供的草稿, 改为 `check()` 汇总并修正了未被调用的箭头函数断言), 覆盖 `text().findOne(timeout)`, `find(n)` 精确数量 (DFS / BFS / 0), `pickup`, `detect`, `compass` (`p` / `k9` / `c0`), `wait` 四种形态, `click(UiObject)`, `scrollDown()`, `auto.registerEvent` (含 `WINDOW_STATE_CHANGED` 多次投递的计数与去重示例). (SOURCE; DEVICE: 5 台设备 30/30, 见 P0 证据)

### P0.2 低风险修复 (记入 changelog `fix`)

- [x] 修复 BFS 越界: `BFS.kt` `>` 改为 `>=`; 同时两种算法在 `limit <= 0` 时直接返回空集合 (原先 `find(0)` 会返回 1 个结果, 因为 limit 检查发生在 `add` 之后). (JVM: `SearchAlgorithmTest.limitIsRespectedExactly` / `zeroLimitFindsNothing`; changelog `fix` 已写入)
- [x] 命中 limit 后回收已获取但未遍历的节点: 选择 "在 P0 直接修复" 而非等待 P1.5, 因为改动只有两处 `recycleRemaining` 且与 `NodeLifecycle` 的后续替换不冲突 (届时把内联 `recycle()` 改为策略调用即可). (JVM: `SearchAlgorithmTest.nodesAlreadyFetchedWhenLimitIsReachedAreRecycled`, 精确断言 DFS 回收 `a0/a1/b`, BFS 回收 `b`)
- [x] `AccessibilityNodeInfoAllocator`: 堆栈捕获改为 `isTracingOrigin` 开关 (默认关闭), 容器改为 `Collections.synchronizedMap`, `recycleAll` 在快照上遍历, `Compat` 版 `getParent` 补上 `add()` 登记并改为可空返回. 注意分配器在生产路径中未被使用 (见第 3 节), 此项只是为 P1.5 保留一个正确的参考实现. (SOURCE; ANDROID_BUILD: 随单元测试任务编译通过)
- [x] `CloaseableManager.kt` 重命名为 `CloseableManager.kt` (`git mv`); 删除 `LayoutInspectService.kt` (全仓库仅 `assets-app/sample/.idea/workspace.xml` 与冻结的 `com.stardust` 副本提及). (SOURCE)
- [x] `AccessibilityBridgeImpl.java` 移除重复的 `ensureServiceStarted()`. (SOURCE)
- [x] `AccessibilityService.kt` 的 `eventExecutor` 获取与销毁改为同一把锁下的操作. 说明: 该访问器目前只在主线程回调 (`onKeyEvent` / `onGesture` / `onUnbind`) 中使用, 竞态是理论上的, 改动仅为消除隐患. (SOURCE)
- [x] `UiSelector.kt` 4 处注解缩进修正. (SOURCE)

### P0.3 基线文档

- [x] 新增 `docs/dev/accessibility-architecture.md` (D7): "现状" 章节记录分层与类映射, 服务生命周期与谓词, 事件流, 查找 / 拾取调用链, 线程模型, 节点生命周期, 已知平台行为, 测试基础设施, 基准数据占位; "目标" 章节含迁移映射表; "评估记录" 章节预留 D9 与 Q4. 每阶段结束时更新. (SOURCE, 2026-09-05)

P0 验收: `:app:testAppDebugUnitTest` 通过 (注意任务名带 `App` 风味, `testDebugUnitTest` 会因 `Inrt` 风味而报 ambiguous); `sample/accessibility/smoke-legacy-api.js` 在至少一台真机上通过; changelog `fix` 条目已写入.

P0 证据:

- `JVM`: 全量 `:app:testAppDebugUnitTest` 1877 tests / 0 failures (含本阶段新增 29 个用例).
- `ANDROID_BUILD`: 单元测试任务已编译 app 模块 (commit `48406313f` 及之前).
- `DEVICE` (2026-09-05, 维护者执行 `sample/accessibility/smoke-legacy-api.js`, 5 台设备结果一致 `30/30 checks passed`): Xiaomi 23046RP50C (`968e9f18`, API 35), Sony G8441 (`BH900ASK9E`), Sony XQ-AT72 (`QV710AF65F`, API 31), Sony XQ-DQ72 (`QV770340J7`), Redmi 22120RN86C (`bek749scrwv4wo8h`). API 级别取自 `yolo-plugin-roadmap.md` 中已记录的设备, 其余待补.
- `DOCS`: changelog `fix` 4 条已写入 10 种语言并重新生成 README / CHANGELOG. 本阶段无 JS API 变更, 无需同步 d.ts / 文档项目.

P0 结论: 关闭. 冒烟脚本作为后续每阶段的回归清单继续使用.

---

## P1: 无障碍服务层重构

目标: 服务状态与生命周期收敛为单一事实来源, 消除 12 处工具实例, 4 套 "确保服务" 实现与 5 种 "是否运行" 判定, 事件分发与窗口根节点获取各只剩一套实现. JS API 不变.

### P1.1 状态注册表 `A11yServiceRegistry` (完成, 2026-09-05)

- [x] 状态枚举收敛为 3 态而非原计划的 4 态: `A11yServiceRegistry.State { UNBOUND, CONNECTED, OPERATIONAL }` (嵌套在注册表内, 未单独建 `A11yState` 文件). 原因: "系统设置中已启用" 只能读 `Settings.Secure` 得到, 无法在进程内观测到其变化, 并入枚举会让注册表依赖 Android 且状态可能与设置不同步; 因此它保留在 `AccessibilityTool.hasService()`, 由 P1.2 的控制器把 "已启用" 与注册表状态组合成 `EnsureResult` 的失败原因 (未启用 / 已启用未绑定 / 已绑定未操作). 4 个谓词 (`hasInstance` / `hasService` / `isRunning` / `isOperational`) 定义未变: `hasInstance = registry.isBound`, `isOperational = isRunning && registry.isOperational`. (SOURCE: `core/accessibility/registry/A11yServiceRegistry.kt`)
- [x] `A11yServiceRegistry<S>` 为泛型且 Android-free: `Snapshot(state, instance)` 不可变快照 (`@Volatile`), 迁移入口 `onConnected` / `onEvent` (采纳实例并置 `OPERATIONAL`, 覆盖 `onServiceConnected` 不触发的平台问题) / `onDisposed` (仅当前实例生效, 过期实例的销毁不改变状态) / `markOperational` / `resetOperational`; `await(target, timeout)` 以单一 `Condition` 替代原 `ENABLED` / `OPERATIONAL` 两个条件, 负超时无限等待, 中断时返回 `false` 并保留中断标志; 监听者按 owner 注册 (同 owner 覆盖), 在锁外通知, 异常隔离并交给构造参数 `onListenerError`. `ConnectionListeners.kt` 把旧的 `AccessibilityServiceCallback` (`onConnected` / `onDisconnected`) 适配为状态监听者. 未采用 "接口注入": 注册表本身可构造多个实例用于测试, 生产路径是 `AccessibilityService.registry` 单例. (SOURCE)
- [x] `AccessibilityService` 伴生对象: `instance` / `hasOperationalState` / `hasInstance` / `waitForStarted` / `waitForOperational` / `setCallback` / `addDisconnectCallback` / `removeDisconnectCallback` / `stop` 全部委托到 `registry`; `LOCK` / `ENABLED` / `OPERATIONAL` / `callback` / `disconnectCallbacks` 删除; `onServiceConnected` / `onAccessibilityEvent` / `disposeService` 只调用 `registry.onConnected` / `onEvent` / `onDisposed`; 抽屉状态事件 (`AccessibilityServiceStateChangedEvent`) 改为注册表监听者 (仅绑定关系变化时投递). 顺带修复: 实例被首个事件采纳时 `waitForStarted` 现在也会被唤醒 (原先只有 `onServiceConnected` 会 `signalAll`, 等待方在该平台问题下会一直等到超时), 记入 changelog `fix`. (SOURCE; 调用方 `AccessibilityTool` / `NodeBridgeProtocol` / `PythonHostCapabilityBroker` 无需改动)
- [x] `auto.stateListener`: `AccessibilityBridge.setAccessibilityListener` 以 bridge (每个脚本运行时一个) 为 owner 调用 `setCallback(owner, listener)`; `ScriptRuntime` 退出时 `setAccessibilityListener(null)` 注销 (紧随 `removeAllEventsForThisRuntime`). Kotlin 侧无 owner 的 `setCallback(listener)` 保留为固定 owner 的兼容入口. 记入 changelog `fix`. 行为差异 (有意): 原先 `onConnected` 只在 `onServiceConnected` 时触发, 现在实例被首个事件采纳时也触发, 与 `waitForStarted` 一致. (SOURCE)
- [x] `AccessibilityBridge` 构造函数的 `setBridge(this)` 副作用移除. `AccessibilityService.bridge` 改为伴生对象级 `defaultBridge` (不再是服务实例的字段), 由 `AbstractAutoJs.createRuntime()` 在创建脚本运行时时登记, 因此服务重启后仍然有效; `UiSelector.pickup` 的 `Picker.Builder` 改为绑定调用方 `scriptRuntime.accessibilityBridge` (窗口过滤器与黑名单随之生效), `UiSelector.plus` 沿用自身 bridge 与分配器. 修复了服务重启后 `pickup()` (未指定 root) 与 `new UiSelector()` 静默返回 `null` / 空集合直到新建脚本运行时的问题 (原因: 旧的静态 `bridge` 是服务实例字段, 只在 "服务存活期间构造 bridge" 时被赋值, 重启后的新实例上为 `null`), 记入 changelog `fix`. "无参 `UiSelector` 从 `WindowRootProvider` 取根节点" 推迟到 P1.4 一并处理. (SOURCE)
- [x] JUnit4: `A11yServiceRegistryTest` 14 用例: 初始态; 连接 -> 事件 -> 重复事件不产生迁移; 无 `onConnected` 直接来事件的采纳序列; 重连重置操作态; 实例替换与过期实例销毁; 销毁解绑; `markOperational` / `resetOperational` 仅在绑定态生效; `await` 立即返回 / 超时 / 跨线程唤醒 / 负超时 / 中断; 监听者按 owner 覆盖 / 移除 / 异常隔离; 两种回调适配器的映射. (JVM)

P1.1 证据:

- `JVM`: 全量 `:app:testAppDebugUnitTest` 1891 tests / 0 failures (含新增 14 个用例).
- `ANDROID_BUILD`: 单元测试任务已编译 app 模块.
- `DEVICE` (2026-09-05, 维护者, 脚本含第 7 节 "服务重启"; WSS = `WRITE_SECURE_SETTINGS`): 基础 30 项在 AVD API 24 / 25 / 26 / 36 (16K) 与 Xiaomi 23046RP50C, Sony G8441, Sony XQ-AT72, Redmi 22120RN86C 全部通过; 重启段 36/36: AVD 24 / 25 / 36 (WSS), 23046RP50C (WSS), G8441 (WSS 与 Root), XQ-AT72 (WSS), 22120RN86C (WSS). 两处例外及处理:
  - AVD API 26: WSS 34/36 (`stateListener` 未见 disconnected -> connected; `pickup` 重启后单次未命中), Root 35/36 (`stateListener`). 原因: 脚本原先调用 `restartService(false)` = 禁用后立即启用, API 26 的设置观察者把两次写入合并为 "仍然启用", 实际未重启 (Root 路径还叠加了下述 "空参数" bug); `pickup` 的单次尝试落在重新绑定后的瞬时空窗. 处理 (见 P1.2 提前落地项): `AccessibilityTool.restartService` 改为等待解绑后再启用; 脚本改为 stop -> 观测解绑 (5 s) -> start, 重启后的查找改为最长 5 s 轮询. 预期复跑后 API 26 为 38/38.
  - Sony XQ-DQ72 (QV770340J7): 21/30, 全部 "屏幕上能找到内容" 的检查失败 (`auto.service` 与事件投递正常), 无权限 / WSS / Root 三种配置结果一致; P0 阶段同一设备为 30/30. 尚无该设备的控制台输出, 暂不能定位. 候选原因: (a) 运行时熄屏或锁屏 (远程运行脚本不会点亮屏幕, `home()` / `app.launch` 无可见效果, 但事件仍会投递); (b) 该设备为 root 设备, 若此前经历过 "root 关闭失效" (见 P1.2 提前落地项) 而处于 `accessibility_enabled=0` 但仍在启用列表的不一致状态, 部分系统在该状态下不提供窗口信息. 脚本已增加环境快照 (屏幕 / 锁屏 / 当前包名 / 窗口根节点数 / `accessibility_enabled` / 启用列表) 与两项前置检查 (`screen is on and unlocked`, `auto.windowRoots is not empty`), 并在失败时把每项的 `returned` / `threw` 细节写进最终错误; 待复跑后按输出判定.
- `DOCS`: changelog `fix` 3 条 (10 语言) 已写入并重新生成 README / CHANGELOG. JS 签名无变化, 无需同步 d.ts / 文档项目.

P1.1 结论: 代码与 JVM 证据齐备, 真机基础项全部通过; 重启段的两处例外已归因并处理, XQ-DQ72 待复跑定位. 关闭 (XQ-DQ72 的结果作为 P1.2 复跑的一部分跟踪).

### P1.2 服务控制器 `A11yServiceController`

提前落地 (2026-09-05, 因维护者真机反馈, 仍在 `AccessibilityTool` 内; 迁入控制器时保持这些语义并补单测):

- [x] Root 关闭失效: `stopServiceWithRoot` 以 `settings put secure enabled_accessibility_services $services` 拼接命令, 当 AutoJs6 是唯一启用的服务时 `$services` 为空, shell 丢弃空参数, `settings put` 报 "Bad arguments" 而不写入; 同时 `runCatching {}.isSuccess` 把命令失败当作成功. 表现为 "root 设备上显式关闭后服务总是自动恢复" (实际从未关闭; 540 ms 后抽屉 `sync()` 读回仍在运行). 修复: 值以单引号包裹 (`putSecureSettingWithRoot`), root / 安全设置的启停一律以 `hasService()` 回读作为结果 (`getOrDefault(false)`), 失败时回落到 `disableSelf()`. 记入 changelog `fix`. (SOURCE)
- [x] 尊重显式关闭: `AccessibilityServiceAutoStart` (每个 `BaseActivity` 创建时) 原先 `!isRunning()` 即通过 root / 安全设置启动, 会重新启用用户显式关闭的服务. 改为仅在 `isMalfunctioning()` (设置中已启用但进程内未绑定) 且等待 `DEFAULT_A11Y_SERVICE_START_TIMEOUT` 后仍未绑定时恢复; 未启用的服务由脚本 / 布局分析器 / 悬浮菜单 / Tile 等显式入口按需启动. 记入 changelog `fix`. (SOURCE)
- [x] `startService()` 在服务已运行时直接返回, 不再先走 root / 安全设置的 "禁用 + 启用" (原先 `auto.start()` 会重启正在运行的服务). 记入 changelog `fix`. (SOURCE)
- [x] `restartService()` 在 `stopService(false)` 之后等待 `AccessibilityService.waitForStopped()` (注册表 `awaitUnbound`, 默认 `DEFAULT_A11Y_SERVICE_STOP_TIMEOUT = 2000 ms`) 再启动; 否则部分系统 (AVD API 26 实测) 把 "禁用后立即启用" 合并为无变化, 实际不重启. 记入 changelog `fix`. (SOURCE; JVM: `A11yServiceRegistryTest.awaitUnboundReturnsOnceTheInstanceIsGone`)
- [ ] 遗留: `stopServiceWithSecure` / `stopServiceWithRoot` 会把 `accessibility_enabled` 置 0, 在旧系统上等同于关闭全部无障碍服务 (含第三方); 现代系统由框架自行推导该值. 控制器迁入时评估是否只维护启用列表而不再写 `accessibility_enabled`.

原计划条目:

- [ ] 将 `AccessibilityTool` 的启停逻辑迁入 `A11yServiceController`, 启动方式抽象为 `ServiceStartStrategy` 列表 (Root, SecureSettings, SettingsUi), 按偏好顺序尝试; 每种策略可单测 (以接口替代 `ProcessShell` / `Settings.Secure`).
- [ ] 将 4 个 "确保 / 等待" 变体合并为一个 `ensure(level: A11yServiceRegistry.State, timeout: Long, launchSettingsIfNeeded: Boolean): EnsureResult`, 失败原因由 "设置中是否已启用" (`hasService()`, 见 P1.1 对 3 态枚举的说明) 与注册表状态组合得出 (未启用 / 已启用未绑定 / 已绑定未操作), 错误文案按原因区分, 消除 `AccessibilityTool.kt:182,195,196` 三处同文案.
- [ ] `AccessibilityTool` 改为薄外壳, 全部方法委托到控制器, 标记 `@Deprecated` (仅 Kotlin 内部弃用, 不影响 JS); 12 处实例化改为注入共享实例 (`AutoJs` 持有, `ScriptRuntime` / UI / Tile 通过 `AutoJs.instance` 或构造参数获取).
- [ ] 统一 `ensureServiceStarted` 的 9 个调用点全部经由 `AccessibilityBridge` -> 控制器, 删除 `UiSelector.kt:715` 与 `AutoJs.kt:89,95` 的绕行路径.
- [ ] `service/AccessibilityService.kt` 重命名为 `AccessibilityServiceItemHelper` (内部类, 未暴露给 JS), 其 `isRunning` 改为消费 registry, 修正 `||` 判定; `AccessibilityServiceAutoStart` 改为调用控制器.
- [ ] `SimpleActionAutomator.isServiceRunning` (`automator.isServiceRunning()` 的实现) 改为 registry 的 `isRunning` 语义 (D5 已批准), 记入 changelog `fix` 并在文档标注.

### P1.3 事件总线 `A11yEventBus`

- [ ] 将 `eventBox` (JS 回调, 按 ownerId), `delegates` (框架内部, 按优先级, 可消费), 按键观察者 (静态 + 实例), 手势 `EventDispatcher` 统一为 `A11yEventBus`: 订阅项 = (ownerId, 事件类型集合, 优先级, 是否可消费, 回调). 派发顺序保持现状 (JS 回调先于内部 delegate, delegate 按优先级且可消费中断, `AccessibilityService.kt:117-163`), 并以测试锁定.
- [ ] 提供 `removeDelegate` / 按 ownerId 批量注销; `AbstractAutoJs.kt:143-145` 三个 delegate 注册改为经由总线.
- [ ] `fastRootInActiveWindow` 的更新与 delegate 命中解耦: 在 `TYPE_WINDOW_STATE_CHANGED` / `TYPE_VIEW_FOCUSED` 时无条件更新 (`AccessibilityService.kt:141-143`), 并纳入 `NodeLifecycle` 回收策略.
- [ ] 提供 Kotlin 侧的事件订阅 API (供 P3 的 `waitForIdle` / `waitForEvent` 使用), 支持 "安静窗口" 检测 (最近一次匹配事件时间戳).
- [ ] `auto.registerEvent(name, callback, options?)` 新增可选的去重 / 去抖: `{ distinct: 'window' | 'source' | fn(prev, next), within: 500 }` 或 `{ debounce: 100 }`, 默认关闭以保持既有行为; 用于消除一次窗口切换收到多个 `TYPE_WINDOW_STATE_CHANGED` 的平台行为 (见第 3.1 节). 同一能力作为总线订阅项的选项提供给 Kotlin 侧.
- [ ] `AccessibilityEventWrapper` 的 `source` 解析成本: 保持在回调期间可用的前提下 (API < 33 事件会被回收), 改为 "首次访问时解析, 回调返回后置为不可用" 或 "仅当订阅项声明 `needsSource` 时解析"; `depth` / `indexInParent` 的计算改用 `NodeLifecycle` 管理父链节点.
- [ ] JUnit4 测试: 订阅 / 派发 / 消费中断 / 按 ownerId 注销 / 回调抛异常不影响其它订阅者 / 去重与去抖.

### P1.4 窗口根节点 `WindowRootProvider`

- [ ] `AccessibilityBridge.java` 转为 Kotlin (保留 FQN, public 签名与 `@ScriptInterface`), 将 `windowRoots` / `getRootInCurrentWindow` / `getRootInActiveWindow` 三处重复逻辑抽为 `WindowRootPolicy.resolve(service, mode, flags, filter)`, 一处实现.
- [ ] 返回值改为非空列表 (内部过滤 `null`), 调用方 `UiSelector.kt:810` 与 `SimpleActionAutomator.kt:259` 移除各自的 `filterNotNull()`; `Auto.kt:88` (`auto.windowRoots()`) 不再可能把 `null` 传入 `UiObject.createRoot`.
- [ ] 明确并文档化 `getRootInActiveWindow()` 是否应用窗口过滤器 (现状不应用, 与 `getRootInCurrentWindow()` 不同); 保持现状 (D2), 在 KDoc 与 API 文档中标注差异.
- [ ] `LayoutInspector.kt:42` 改为经由 `WindowRootProvider` 获取窗口列表 (可选: 若布局分析器希望忽略脚本过滤器, 传入 `filter = null`).

### P1.5 节点生命周期 `NodeLifecycle`

- [ ] 定义 `NodeRecyclePolicy`: API >= 33 全部 no-op; API < 33 按现有分配器策略. 所有内联 `.recycle()` (`AccessibilityService.kt:56-61`, `DFS.kt:32`, `BFS.kt:28`, `action/ScrollAction.kt:23,49`, `action/ScrollMaxAction.kt:31,51,62`, `UiObject.kt:453,597`) 改为调用策略.
- [ ] `AccessibilityNodeInfoAllocator` 保留类名 (内部使用), 内部实现替换为线程安全容器 + 可选调试登记; `global` 单例改为按 `ScriptRuntime` 作用域 (脚本退出时 `recycleAll`).
- [ ] 补测试: API 级别分支 (以注入的 `sdkInt` 替代 `Build.VERSION.SDK_INT`), 登记 / 回收计数.

### P1.6 收尾

- [ ] 无障碍截图: 抽出 `A11yScreenshotter` (含 `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` 重试), `SimpleActionAutomator.captureScreen` 改为消费; Python Host 的 `PythonHostAccessibilityScreenCapture` 是否切换见 P5.4.
- [ ] Node Bridge / Python Host 中 3 处 `hasOperationalState` 三元表达式改为 `registry.state >= OPERATIONAL` (仅替换判定, 不改其它逻辑).
- [ ] 更新 `docs/dev/accessibility-architecture.md` (若 P0.3 已建立) 的 "目标" 章节.

P1 验收: JVM 单元测试 (registry / controller / event bus / window policy / node lifecycle) 全部通过; `sample/accessibility/smoke-legacy-api.js` 真机通过; 手动验证: 抽屉开关, 悬浮菜单状态文案, Tile, 布局分析器, 脚本内 `auto()` / `auto.waitFor()` / `auto.stateListener` / `auto.registerEvent` 行为与重构前一致; changelog `improvement` 条目已写入.

---

## P2: UiSelector / UiObject / UiObjectCollection 重构

目标: 三个类的 public JS 表面不变, 内部由 "注册表 + 组合" 替代 "手工表 + 反射", 巨型文件拆分, 过滤器去重, 查询获得统一的轮询策略与线程安全保证.

### P2.1 `UiNode` 抽象与夹具

- [ ] 定义 Android-free 接口 `UiNode` (只读属性: text / desc / content / id / className / packageName / bounds / 布尔属性集合 / 集合信息 / depth / indexInParent / childCount / `child(i)` / `parent()`; 动作: `performAction(id, args)`), `UiObject` 实现它; 过滤器 (`Filter.filter(node: UiNode)`), 搜索算法, `Compass`, `Stabilizer` 一律面向 `UiNode`.
- [ ] `FakeUiNode` (P0.1) 实现 `UiNode`, 成为过滤器 / 搜索 / pick / flow 单元测试的统一夹具.

### P2.2 `SelectorRegistry`: 一处描述, 多处派生

- [ ] 用声明式表 `SelectorRegistry` 描述每个选择器属性: 名称, 别名 (含 `@Deprecated` 的 `xMatches`), 参数类型集合 (string / regex / bool / int / double / double-range / bool+tolerance / app / action / filter), 对应的 `UiNode` 取值器, 过滤器工厂.
- [ ] `UiSelector` 的约 200 个 builder 方法改为一行委托 `registry.apply(this, "text", args)` (方法本身保留, 以维持反射注册与 d.ts 生成). 按 D9, registry 的每一项都必须是纯数据 (名称, 别名, 参数类型集合, 取值器, 过滤器工厂), 使 builder 方法能够被生成.
- [ ] 按 D9 评估并实施 builder 方法的自动生成: 候选方案 (a) KSP 处理器读取 registry 注解 / 常量表, 生成 `UiSelectorGenerated.kt` (builder 方法) 与 `selector-signatures.json` (供 d.ts 生成器与文档使用); (b) Gradle 任务 + Kotlin 模板生成源码并提交到仓库 (可读, 可 diff, 无编译期插件依赖); (c) 保持手写但用单元测试断言 registry 与手写方法集合一致 (兜底). 评估维度: 构建时间, IDE 索引与跳转, `@Deprecated` 别名与 KDoc 的表达, 与 `android-dts-generator` 的衔接. 结论与选择记录到 `docs/dev/accessibility-architecture.md`.
- [ ] `Picker.Builder.checkSelectorMap` (`UiSelector.kt:1041-1185`) 与 `doubleTypes` 等 6 张手工表由 `SelectorMapParser` + registry 替代, 删除反射调用; 行为差异用测试锁定 (map 值为 `List` 时的各分支, `filter` / `action` 键, 未知键抛 `IllegalMapSelectorException`).
- [ ] `augment/selector/Selector.kt:45-95` 的 "反射全部 public 方法" 改为 "反射 registry 声明的方法 + 显式白名单 (`find*`, `exists`, `untilFind*`, `algorithm`, `filter`, `action`, `performAction` 等)", 并保留黑名单 (`plus`, `append`); 用测试断言注册的全局函数集合与重构前完全一致 (以快照文件固化).
- [ ] `SelectorMethodResolver` 与 `UiObject.Invoker/Interceptor` 的数值强制转换合并为一个 `JsArgumentCoercion` 工具, 三处共用.

### P2.3 过滤器去重

- [ ] `TextFilter` / `DescFilter` / `ContentFilter` / `PackageNameFilter` / `ClassNameFilter` / `IdFilter` 合并为泛型 `StringPropertyFilter(property, matcher)`, `matcher` 为 `Equals / Contains / StartsWith / EndsWith / Matches(regexString) / Match(NativeRegExp)`; `StringList*Filter` 通过 `KeysGetter` 泛化到同一实现.
- [ ] `MetricsFilter` / `MetricsMinFilter` / `MetricsMaxFilter` / `MetricsRangeFilter` 合并为 `MetricsFilter(property, comparison)`; `MinIntFilter` / `MaxIntFilter` / `IntFilter` 同理.
- [ ] `BooleanFilter.cache` 移除或改为线程安全; `Selector.filters` 改为在评估时拷贝快照 (保持 `append` 原地修改语义, 但评估线程安全).
- [ ] JUnit4 测试: 每种 matcher 与 comparison 的真值表 (基于 `FakeUiNode`), 正则 `JsRegexUtils` 转换.

### P2.4 查询与轮询

- [ ] 引入 `PollPolicy(interval, timeout, times, backoff)` 与 `UiSelectorQuery`, `findOne(timeout)` / `untilFind*` / `exists` / `find(max)` 统一走它; 默认值维持现状 (`findOne` 轮询间隔 50ms).
- [ ] `FLAG_FIND_ON_UI_THREAD` 的跨线程 `blockedGet()` 增加有界超时 (默认与 `timeout` 一致, 无限等待时以可中断等待替代), 超时后抛出可读错误.
- [ ] 无界阻塞方法 (无参 `findOne()`, `untilFind()`, `UiSelector.performAction`) 行为不变 (D2), 但在 KDoc 与文档中标注 "可能永久阻塞", 并在 P3 提供带超时的链式替代.
- [ ] `find()` 的黑名单判断由 "仅检查根节点包名" 改为可配置 (`AccessibilityConfig`), 默认维持现状.

### P2.5 `UiObject` 拆分

- [ ] `compass` DSL 迁出为 `core/automator/pick/Compass.kt` (预编译正则, `CompassParser` 见 P0.1), `UiObject.compass()` 委托.
- [ ] `Detector` / `Invoker` / `Interceptor` / `Picker` 迁出为 `core/automator/pick/**`, `UiSelector.pickup` 与 `UiObject.detect` 委托; 结果类型别名表 (`RESULT_GROUP_*`) 集中到 `PickResultType`.
- [ ] `summary()` / `isSimilar()` / `toString()` 迁出为 `UiObjectSummary.kt` 扩展; 新增轻量 `fingerprint()` (bounds + text + desc + className + childCount + 关键布尔属性的哈希, 不遍历兄弟与子节点) 供 P3 `Stabilizer` 使用; `isSimilar` 语义不变.
- [ ] `parent()` 的 `indexInParent` 反推改为惰性计算; `clickBounds()` 改为使用 `ScriptRuntime` 提供的手势分发器与 `ScreenMetrics` (无运行时时回退到现有实现).
- [ ] `codegeneration/ReadOnlyUiObject.java` 与 `codegeneration/CodeGenerator.java` 转为 Kotlin, 前者改为实现 `UiNode` 而非以约 50 个覆盖方法继承 `UiObject` (若布局分析器依赖 `UiObject` 类型, 则保留继承但收敛覆盖面).

### P2.6 `UiObjectCollection` 增强 (新增 API, 不改既有)

- [ ] 新增 `filter(fn)`, `map(fn)`, `at(i)` (支持负索引), `first()`, `last()`, `nonNull()`, `forEach(fn)`, `some(fn)`, `every(fn)`, `indexOf(o)`, `slice(a, b)`; 验证 Rhino 下 `for...of` 对 Java `Iterable` 的支持情况, 必要时定义 `Symbol.iterator`.
- [ ] 新增 `performActionEach(action, ...args): boolean[]` 与 `clickEach()` 等返回逐节点结果的变体; 既有 `performAction` / `click()` 的取与语义不变.
- [ ] d.ts 与文档同步 (P6).

P2 验收: 全局函数集合快照测试通过; `SelectorMapParser` / 过滤器 / 搜索 / `Compass` / `PollPolicy` 单元测试通过; `sample/accessibility/smoke-legacy-api.js` 真机通过; `UiSelector.kt` 与 `UiObject.kt` 各降至 500 行以下; changelog `improvement` + `feature` (P2.6) 条目已写入.

---

## P3: 异步链式 `waitAsync` (Flow) 与便携函数

目标: 实现附录 A 定义的 `Flow` 链式对象, 全局起点函数与 `flow` 命名空间; 同步 `wait()` 不变 (D1).

### P3.1 Android-free 调度核心

- [ ] `FlowStep` (sealed): `Wait(cond, policy)`, `WaitStable(cond, policy, stability)`, `Action(kind, args, strict)`, `Sleep(range)`, `Callback(kind = then / catch / finally / map / filter / peek / run)`, `Combinator(all / race / any)`, `Scope(root / compass / resultType)`, `Timeout(ms)`, `Retry(times, delay)`.
- [ ] `FlowScheduler`: 接收步骤序列, 在注入的 `Executor` (工作线程) 与 `Marshaller` (回投脚本线程) 之间调度; 定义每步的 resolve 值传递规则 (见附录 A.4); 支持取消 (`FlowHandle.cancel()`), 全链超时, 重试.
- [ ] `Stabilizer`: 基于 `UiNode.fingerprint()` 或自定义比较器的 "连续 N 毫秒无变化" 检测, 支持目标暂时消失时的策略 (`reset` / `fail`); 与 `PollPolicy` 共用时间源 (注入 `Clock`, 便于测试).
- [ ] `FlowErrors`: `FlowError(code, step, selector, elapsed, attempts)` 及子类 `TIMEOUT` / `ACTION_FAILED` / `CANCELLED` / `A11Y_UNAVAILABLE` / `INVALID_TARGET`.
- [ ] JUnit4 测试 (使用 `FakeUiNode` 与手动推进的 `FakeClock` / `DirectExecutor`): 值传递, 超时, 稳定检测 (含目标消失重置), 取消, 重试, 组合器, 错误传播顺序.

### P3.2 运行时接入

- [ ] `FlowExecutor`: 每个 `ScriptRuntime` 一个惰性创建的工作线程 (命名 `flow-<engineId>`), 脚本退出时中断并拒绝所有挂起 Flow (`CANCELLED`); 通过 `ScriptAsyncDispatcher` 回投用户回调; 每条链持有 `Loopers.waitWhenIdleToken` 直到终态, 保证非 UI 模式脚本不会提前退出.
- [ ] `FlowNativeObject` (Kotlin `NativeObject`, 参照 `augment/ai/AiSessionNativeObject.kt`): 内部持有 polyfill `Promise`, 每个链式方法返回新的 `FlowNativeObject`; 实现附录 A.3 的全部方法与别名; `then` / `catch` / `finally` 直接委托内部 Promise 并包装返回值.
- [ ] `augment/flow/Flow.kt` (`Augmentable`, `Invokable`): 注册 `flow` 命名空间 (`flow.wait` / `flow.sleep` / `flow.run` / `flow.of` / `flow.all` / `flow.race` / `flow.any` / `flow.defaults` / `flow.trace` / `flow.Flow`), 全局函数 `waitAsync`, `waitThenClick` (别名 `clickWait`), `waitForStable`, `waitForStableThenClick` (别名 `clickWhenStable`), `waitForVisible`, `waitForHidden` (别名 `waitForGone`); 在 `Global.kt` 的 `wait` 函数对象上挂 `async` 属性指向 `waitAsync`; 在 `augment/automator/Auto.kt` 注册 `auto.wait` 指向同一实现 (D1), 并与既有 `auto.waitFor` 在 KDoc 中互相引用以免混淆.
- [ ] 参数解析复用 `Selector.pickup` 的选择器归一化 (string / regex / UiSelector / map / list / 函数 / UiObject / Flow / Promise), 新 API 使用 options 对象与位置参数 `(cond, timeout, interval)`, 不沿用 `limit < 100` 视为次数的规则 (仅 `times` 选项).
- [ ] UI 线程 (UI 模式) 下可用: 阻塞步骤在工作线程, 回调回投主线程; `.sync()` 在 UI 线程抛出与 `sleep` 一致的错误.
- [ ] 错误对象转换为 JS `Error` 实例 (带 `name` / `code` / `selector` / `elapsed` / `attempts` 属性), 与 `S13n.throwable` 的既有转换路径兼容.

### P3.3 便携函数与别名

- [ ] `sleep(min, max?)` 步骤 (含 `"±delta"` 字符串形式, 与全局 `sleep` 的 `resolveSleepRange` 共用).
- [ ] `clickAfter(min, max?)`, `clickWhenStable(...)`, `clickWhenStableAfter(...)`, `longClickAfter`, `waitThenLongClick`; `else(fn)` 作为 `catch` 别名 (衔接同步 `wait` 的 `{then, else}` 习惯).
- [ ] `flow.defaults({ timeout, interval, stableFor, strictActions, trace })` 每脚本一份, 影响后续新建的链.
- [ ] `flow.trace(true)` 在控制台输出每步的开始 / 结束 / 耗时 / 结果摘要 (用于排查脚本).

### P3.4 示例与测试

- [ ] `sample/accessibility/flow-basics.js` (附录 A.6 的全部示例可运行), `flow-ui-mode.js` (UI 模式下不阻塞主线程), `flow-cancel-and-timeout.js`.
- [ ] 真机验证清单: 找到 / 超时 / 目标消失 / 稳定检测 / 脚本中途停止 (Flow 被取消, 无线程泄漏) / UI 模式.

P3 验收: 调度核心单元测试通过; 三个示例脚本真机通过; 脚本停止后 `flow-*` 线程不存活 (以 `threads` 快照或日志证明); changelog `feature` 条目, 文档与 d.ts 同步 (P6) 完成.

---

## P4: 高阶自动化封装 (toolkit)

目标: 把脚本作者反复手写的 "查找 + 等待 + 动作 + 校验" 组合固化为一处实现 (`core/automator/toolkit/**`), 同时提供同步 (`automator.*`) 与链式 (Flow 步骤 / `flow.*`) 两种表面. 具体签名见附录 B.

### P4.1 核心实现 (Android-free 逻辑 + 薄 Android 适配)

- [ ] `SmartClick`: 目标不可点击时按策略上溯到可点击祖先 (复用 compass `k`), 仍不可点击时回退为手势点击 (`clickBounds`), 可选点击后校验条件; 返回 `ClickOutcome(method = node / ancestor / gesture, target)`.
- [ ] `ScrollSearch`: 在指定可滚动容器 (或自动探测) 内按方向滚动直到目标出现或达到最大次数 / 到达末尾 (连续两次指纹无变化视为到底), 支持 `pageUp / pageDown` 与手势两种滚动方式.
- [ ] `TextEntry`: 定位可编辑控件 (选择器 / 索引 / 当前焦点), 可选先清空, 设置或追加文本, 可选 IME Enter (`imeEnter`) 与结果校验 (`text()` 等于期望).
- [ ] `PopupDismisser`: 给定候选选择器列表 (如 `['允许', '同意', '跳过', '关闭', /^(取消|知道了)$/]`), 存在即点击, 支持一次性与后台守护 (基于 P1.3 事件总线的窗口状态变化触发, 而非轮询).
- [ ] `ListCollector`: 对列表容器按方向滚动并采集每项内容 (`pickup` resultType), 按指纹去重, 直到到底或达到上限.
- [ ] `AppLauncher`: 启动应用 (包名 / 应用名 / `App`) 并等待 `currentPackage()` 切换, 已在前台时可选 `bringToFront`; `backUntil(cond, maxTimes)` 与 `backToApp(pkg)`.
- [ ] `Toggle`: 对 `checkable` 控件按期望状态点击 (状态已一致时不点击), 点击后校验.
- [ ] `Humanize` 选项集: 点击坐标随机偏移, 动作间随机延迟, 供上述工具与 Flow 动作步骤共用.
- [ ] JUnit4 测试: 以 `FakeUiNode` 树 + 可编程的 `FakeActionPerformer` 覆盖每个工具的决策路径.

### P4.2 JS 表面

- [ ] 同步表面同时挂载到 `automator` 模块与全局 (D10): `smartClick` / `scrollUntil` / `typeInto` / `dismissPopups` / `collectList` / `launchAndWait` / `backUntil` / `toggle` / `clickIfExists` / `clickAny` / `findAny` / `retry`. 落地前先在真机上以 `Object.getOwnPropertyNames(global)` 生成全局名快照, 核对冲突与语义相近项 (已知: `click` / `longClick` / `scrollUp` / `scrollDown` 等为既有全局动作, 新名字须与之风格一致; `toggle` 若与既有名字冲突则改为 `setChecked`).
- [ ] Flow 步骤对应项 (`.smartClick()`, `.scrollUntil()`, `.typeInto()`, `.dismissPopups()`, `.collectList()`, `.launchAndWait()`, `.backUntil()`, `.toggle()`) 与 `flow.*` 起点.
- [ ] 每个工具的错误走 `FlowError` 体系 (同步表面抛出同类错误对象).

### P4.3 示例

- [ ] `sample/accessibility/toolkit-*.js`: 登录表单填写, 列表滚动采集, 弹窗守护, 应用切换.

P4 验收: 工具单元测试通过; 示例真机通过; changelog `feature` 条目, 文档与 d.ts 同步.

---

## P5: 能力扩展

以下是探查过程中识别出的 "值得继续扩展和精进" 的能力, 均为新增, 不影响既有 API. 每项独立评估, 可按需跳过.

### P5.1 诊断与可观测性

- [ ] `auto.explain(selector)`: 对选择器逐个过滤器统计命中数 (如 "text 命中 3, 叠加 clickable 后为 0"), 定位 "为什么找不到" 的问题; 依赖 P2.2 registry.
- [ ] `auto.dump(options)`: 输出当前窗口树的可读快照 (缩进文本 / JSON / uiautomator 风格 XML), 支持深度与属性过滤; `UiObject.toJSON()` / `dumpSubtree()`.
- [ ] `auto.stats`: 每脚本的查询次数, 平均耗时, 最慢选择器, Flow 步骤耗时分布 (配合 `flow.trace`).
- [ ] 布局分析器的代码生成器输出 `waitThenClick(...)` / `flow` 风格片段 (可选开关).

### P5.2 事件驱动等待

- [ ] `waitForIdle({ quietFor, timeout, eventTypes })`: 基于 P1.3 事件总线, 在 `quietFor` 毫秒内无窗口内容 / 状态变化事件即视为界面空闲, 替代 "sleep 一段时间再找" 的常见写法.
- [ ] `waitForEvent(type, filter, timeout)` / `waitForToast(text)` / `waitForNotification(filter)` 作为 Flow 步骤 (复用 `events.observeToast` 等现有观察者).
- [ ] 轮询型等待在事件总线可用时改为 "事件触发 + 兜底轮询" 的混合策略 (`PollPolicy.eventAssisted`), 降低全树遍历频率.

### P5.3 选择器与节点能力

- [ ] 字符串选择器语法 (D13): `UiSelector.select(String)` 重载 + 全局 `select('text=登录 clickable')`, Android-free 的 `SelectorStringParser` 基于 P2.2 registry 解析 `key=value` / `key~=regex` / 布尔键, 语法错误给出位置与候选键名; `pickup` / `wait` / Flow 的 `cond` 参数不改变字符串的既有含义 (content 匹配), 需要新语法时显式传入 `select('...')`. 同步 d.ts (`select(selector: string): UiSelector` 重载) 与文档.
- [ ] `UiObject.isStale()` (通过 `refresh()` 判断), `UiObject.waitForStable()` / `waitUntilGone()` 节点级便捷方法 (Flow 的节点级入口).
- [ ] 多显示器 / 多窗口: `auto.windows` 的过滤 builder (按类型 / 标题 / 包名 / 显示器), `WindowRootProvider` 支持指定 `displayId`.
- [ ] `UiObjectCollection` 的惰性查询与分页 (`find(max)` 已有, 补 `findIterator`), 减少大树的一次性遍历.

### P5.4 跨运行时复用

- [ ] Python Host 的 `PythonHostSelector` 改为基于 `UiNode` 快照与 registry 过滤器 (保留其 JSON 线协议), 消除第二套选择器实现.
- [ ] Node Bridge 的 `accessibility` 模块改为消费 `A11yServiceRegistry` / `WindowRootProvider` / toolkit (保留其冻结 JSON 节点协议), 消除第三套实现.
- [ ] Python Host 截图切换到 `A11yScreenshotter` (P1.6).

### P5.5 健壮性

- [ ] 针对 `onServiceConnected` 不触发的 Android 问题, 在 registry 中加入 "收到事件即视为已连接" 的兜底迁移 (P1.1 已覆盖状态表, 此处补真机复现脚本与自动恢复策略).
- [ ] 服务被系统杀死后的自动恢复 (`AccessibilityServiceAutoStart` 已有 Activity 触发路径, 补 Flow 挂起期间的 `A11Y_UNAVAILABLE` 错误与可选自动重试).
- [ ] `FLAG_FIND_ON_UI_THREAD` 与 `MODE_FAST` 的使用指南与基准数据 (记录到 `docs/dev/accessibility-architecture.md`).

---

## P6: 文档, 声明, 示例与发布 gate

每个引入或修改 JS API 的阶段 (P2.6, P3, P4, P5) 结束时执行一次:

- [ ] `.changelog/lang_*.json` 按 `AGENTS.md` 规则补充 `feature` / `fix` / `improvement` 条目 (版本号参考 `version.properties`), 运行 py 脚本更新 README.
- [ ] `D:/webstorm-projects/AutoJs6-Documentation`: 新增 `flow.md` (Flow 对象, 全局起点函数, `flow` 命名空间, 错误类型), 更新 `global.md` (`wait` 章节增加 "异步形式" 指引, 指向 `flow.md`), `automator.md` (toolkit), `uiObjectCollectionType.md` (P2.6), `uiSelectorType.md` / `uiObjectType.md` (标注可能永久阻塞的方法); 执行 `generator/auto-generate-for-autojs6.bat`.
- [ ] `D:/webstorm-projects/AutoJs6-TypeScript-Declarations`: `aj6-int-globals.d.ts` 新增 `waitAsync` 等全局函数与 `Flow` 接口, `aj6-int-automator.d.ts` 新增 toolkit, `UiObjectCollection` 新增方法; 执行 `D:/idea-projects/android-dts-generator/aj6dts.bat -Publish`, 同步 `AutoJs6-Plugin-Ace-Editor` 的 `aj6-int-*.d.ts` 并运行 `:app:generateAutoJs6LspDeclarations`; 两仓库版本号按规则加一.
- [ ] `sample/accessibility/**` 示例脚本随文档一起更新.
- [ ] 发布前 gate: `sample/accessibility/smoke-legacy-api.js` + 各阶段示例在至少 2 台真机 (一台 API < 33, 一台 API >= 33, 覆盖节点回收策略两个分支) 通过; `:app:testDebugUnitTest` 通过; 全量构建通过 (构建可能长达 9 分钟, 按 `AGENTS.md` 的等待策略检查).

---

## 附录 A: `waitAsync` / Flow API 设计草案

本附录是 P3 的实现依据. 标注 "待决" 的点见附录 C.

### A.1 定位

- `Flow` 是对内部 Promise (`assets/modules/promise.js` polyfill) 的封装, 是 thenable: `then` / `catch` / `finally` 与标准 Promise 语义一致, 因此可与 `Promise.all` 等互操作, 也可 `.toPromise()` 取出原始 Promise.
- 每个链式方法都是 "在当前 Promise 上续接一个步骤并返回新的 `Flow`", 因此链是不可变的, 可以分叉: `const found = waitAsync('a'); found.click(); found.longClick();` 是两条独立的续接.
- 阻塞型步骤 (等待, 轮询, 稳定检测, sleep, 滚动查找) 在每个脚本独享的 `flow-<engineId>` 工作线程执行; 用户回调 (`then` / `catch` / `finally` / `map` / `filter` / `peek` / `run` / 动作步骤的 `onOk` / `onErr`) 通过 `ScriptAsyncDispatcher` 回投到创建 Flow 的脚本线程执行 (UI 模式下即主线程). 脚本主线程永远不会因 Flow 被阻塞, 除非显式调用 `.sync()`.
- 链挂起期间脚本保持存活 (`Loopers.waitWhenIdleToken`), 链到达终态 (resolve / reject / cancel) 时释放; 脚本被停止时所有挂起链以 `CANCELLED` 拒绝, 工作线程中断.
- 没有 `async/await` 语法 (Rhino 限制), 同步桥接使用 `.sync(timeout?)` (阻塞当前线程, 拒绝时抛出错误对象, UI 线程禁止) 或 `.await()` (仅 continuation 模式).

### A.2 起点

| 全局函数 | 等价 | 说明 |
| --- | --- | --- |
| `waitAsync(cond, timeout?, interval?)` / `waitAsync(cond, options?)` | `flow.wait(...)`, `wait.async(...)`, `auto.wait(...)` | 轮询直到 `cond` 满足, resolve 为拾取结果 (默认 UiObject), 超时 reject `TIMEOUT` |
| `waitThenClick(cond, timeout?, interval?)` | `clickWait(...)`, `waitAsync(cond, ...).click()` | 找到后点击, resolve 为被点击的 UiObject |
| `waitForStable(cond, timeout?, interval?, options?)` | `flow.waitForStable(...)` | 找到且在 `stableFor` 毫秒内指纹无变化 |
| `waitForStableThenClick(...)` | `clickWhenStable(...)` | 稳定后点击 |
| `waitForVisible(cond, ...)` | `flow.waitForVisible(...)` | 找到且 `visibleToUser()` 为真且 bounds 非空 |
| `waitForHidden(cond, ...)` | `waitForGone(...)`, `flow.waitForHidden(...)` | 找不到, 或找到但不可见, 并保持 `stableFor` 毫秒 |
| `flow.sleep(min, max?)` | — | 仅延迟, resolve 为 `undefined` |
| `flow.run(fn)` | — | 在工作线程执行 `fn` (适合放置同步的 `findOne` / `sleep` 旧式代码), resolve 为返回值 |
| `flow.of(value)` | — | 以给定值起链 |
| `flow.all([...])` / `flow.race([...])` / `flow.any([...])` | — | 组合器, 接受 Flow / Promise / 选择器 (选择器视为 `waitAsync(sel)`) |

`cond` 接受: `Pickup.Selector` 的全部形式 (string, regex, `UiSelector`, map, list), 函数 `() => any` (真值判定与同步 `wait` 一致: `false` / `null` / `undefined` / `NaN` 为不满足, `0` / `""` 满足), `UiObject` (以 `refresh()` 重新校验, 供 `waitForStable(uiObject)` / `waitForHidden(uiObject)` 使用), `Flow` / `Promise` (等待其完成).

`options`: `{ timeout = 10000, interval = 200, times, root, compass, resultType, stableFor = 500, compare = 'fingerprint' | 'bounds' | 'content' | fn(prev, next), missing = 'reset' | 'fail', strict }`. `times` 与 `timeout` 同时给出时任一先到即超时; 新 API 不沿用 `limit < 100` 视为次数的规则.

### A.3 链式方法

| 分类 | 方法 | resolve 值 |
| --- | --- | --- |
| 等待 | `wait(cond, timeout?, interval?)`, `waitForStable(...)`, `waitForVisible(...)`, `waitForHidden(...)`, `waitForActivity(name, ...)`, `waitForPackage(name, ...)`, `waitUntil(fn, ...)`, `waitWhile(fn, ...)`, `waitForIdle(options)` (P5.2), `waitForEvent(...)` (P5.2) | 拾取结果 / `true` |
| 动作 (作用于当前值, 需为 UiObject / UiObjectCollection / 点) | `click(onOk?, onErr?)`, `longClick()`, `clickBounds(offsetX?, offsetY?)`, `smartClick(options)` (P4), `setText(text)`, `appendText(text)`, `clear()`, `paste()`, `focus()`, `scrollForward()` / `scrollBackward()` / `scrollUp()` / `scrollDown()`, `scrollUntil(cond, options)` (P4), `typeInto(text, options)` (P4), `toggle(checked)` (P4), `performAction(id, ...args)` | 动作目标 (UiObject), 便于继续 `.then(o => ...)` |
| 全局动作 (不依赖当前值) | `back()`, `home()`, `recents()`, `press(x, y, duration)`, `swipe(...)`, `gesture(...)`, `launchAndWait(app, options)` (P4), `backUntil(cond, max)` (P4), `dismissPopups(list)` (P4) | 透传上一步的值 (动作失败时 reject) |
| 计时 | `sleep(min, max?)`, `delay(...)` (别名), `clickAfter(min, max?)`, `longClickAfter(...)`, `clickWhenStable(...)`, `clickWhenStableAfter(...)`, `timeout(ms)` (为剩余链设置总超时), `retry(times, delay?)` (失败时重跑上一步骤组) | 透传 / 动作目标 |
| 组合 | `then(onOk, onErr)`, `catch(onErr)`, `else(onErr)` (别名), `finally(fn)`, `map(fn)`, `filter(fn)` (为假时 reject `INVALID_TARGET`), `peek(fn)` (旁路观察, 透传), `run(fn)`, `within(cond)` (后续等待以该节点为根), `scope({ root, compass, resultType })` | 见各项 |
| 终结 | `sync(timeout?)`, `await()`, `toPromise()`, `cancel()` | — |

动作步骤的 `onOk` / `onErr` 参数与 `then(onOk, onErr)` 等价, 即 `.click(ok, err)` 等于 `.click().then(ok, err)`. 这是用户示例中 `waitForStableThenClick(sel, 3000, 200, ok, err)` 这类 "动作 + 回调" 形式的统一解释.

### A.4 值传递与错误规则

1. 等待步骤成功: resolve 为拾取结果; 失败: reject `FlowError{ code: 'TIMEOUT', selector, elapsed, attempts }`.
2. 动作步骤: 当前值不是可作用目标 -> reject `INVALID_TARGET`; 动作返回 `false` 且 `strict` 为真 (默认) -> reject `ACTION_FAILED`; `strict` 为假 -> resolve 为布尔结果. (待决 Q1: 默认是否 strict.)
3. 全局动作步骤透传上一步的值, 使 `waitAsync('a').sleep(300).setText('x')` 中的 `setText` 仍作用于 `a`.
4. 无障碍服务不可用 (registry 状态低于 `CONNECTED` 且 `ensure` 失败) -> reject `A11Y_UNAVAILABLE`.
5. 任何拒绝沿链传播, 与 Promise 一致; `catch` / `else` 之后链恢复为其返回值.
6. `cancel()` 使所有尚未执行的步骤以 `CANCELLED` 拒绝, 正在执行的阻塞步骤被中断 (线程 interrupt 转换为取消).
7. 错误对象是 JS `Error` 实例, `name` 为 `FlowError`, 附加 `code` / `step` / `selector` / `elapsed` / `attempts` 属性.

### A.5 `waitForStable` 算法

1. 以 `interval` 轮询拾取目标; 未找到时: `missing = 'reset'` (默认) 重置稳定计时并继续, `'fail'` 立即 reject.
2. 每次找到后计算指纹 (`compare` 决定: `fingerprint` = bounds + text + desc + className + childCount + 关键布尔属性; `bounds`; `content`; 或自定义 `fn(prev, next) => boolean`).
3. 与上一次指纹相同则累计稳定时长, 不同则重置; 稳定时长 >= `stableFor` 即 resolve.
4. 总时长超过 `timeout` 时 reject `TIMEOUT`, `reason` 为 `unstable` 或 `missing`.
5. `waitForVisible` 在步骤 2 之前额外要求 `visibleToUser()` 且 bounds 非空; `waitForHidden` 的 "稳定" 条件反转 (连续 `stableFor` 毫秒找不到或不可见).

### A.6 示例 (用户给定示例的最终形态)

```js
waitAsync(/something.+/i, 3000, 200)
    .then(o => o.click(), e => console.error('Failed to find element:', e))
    .catch(error => console.error('An error occurred:', error));

waitThenClick(/something.+/i, 3000, 200)
    .then(() => console.log('Element clicked successfully.'), e => console.error('Failed to click element:', e));

waitAsync(/something.+/i, 3000, 200)
    .click()
    .wait(/anotherElement.+/i, 3000, 200)
    .click(o => console.log('Clicked another element:', o), e => console.error('Failed to click another element:', e))
    .waitThenClick(/yetAnotherElement.+/i, 3000, 200)
    .then(() => console.log('Clicked yet another element successfully.'))
    .catch(error => console.error('An error occurred during the sequence:', error));

waitForStable(/something.+/i, 3000, 200)
    .click()
    .waitForStableThenClick(/anotherElement.+/i, 3000, 200, o => console.log('Clicked:', o), e => console.error('Failed:', e))
    .waitThenClick(/yetAnotherElement.+/i, 3000, () => console.log('Clicked yet another element.'), e => console.error('Failed:', e))
    .clickWhenStable(/somethingElse.+/i, 3000, 200)
    .catch(error => console.error('An error occurred during the sequence:', error));

waitForStable(/something.+/i, 3000, 200)
    .sleep(1000)
    .click()
    .catch(error => console.error(error));

/* 等价的便携写法. */
waitForStable(/something.+/i, 3000, 200).clickAfter(1000, 2000);
clickWhenStableAfter(/something.+/i, 3000, 200, 1000);

/* 同步桥接: 在非 UI 线程阻塞等待整条链完成. */
const el = waitAsync('登录', 5000).click().wait('首页', 8000).sync();

/* 与旧式代码混用: 在工作线程执行同步查找. */
flow.run(() => text('设置').findOne(3000))
    .filter(o => o != null)
    .click();

/* 组合: 任一弹窗出现即关闭. */
flow.race(['允许', '同意', '跳过']).click().catch(() => { /* 没有弹窗. */ });
```

`waitThenClick(sel, 3000, () => ...)` 这种 "省略 interval, 直接给回调" 的形式通过参数类型识别 (数字 / 函数) 支持, 与同步 `wait` 识别 `{then, else}` 的方式类似, 但基于 `typeof === 'function'`.

---

## 附录 B: 高阶封装 API 草案 (P4)

| 同步 (`automator.*`) | Flow 步骤 | 说明 |
| --- | --- | --- |
| `smartClick(target, options?)` | `.smartClick(options?)` | `options: { climb = true, gestureFallback = true, verify?: cond, offset?, humanize? }`; 返回 `{ ok, method, target }` |
| `clickIfExists(sel, options?)` | `flow.clickIfExists(...)` | 存在即点击, 返回是否点击; 不存在不报错 |
| `clickAny([sels], options?)` | `flow.clickAny(...)` | 按顺序找第一个存在的并点击 (同步) / 任一出现即点击 (链式, `race`) |
| `findAny([sels], timeout?)` | `flow.findAny(...)` | 返回第一个找到的 UiObject 及其索引 |
| `scrollUntil(target, options?)` | `.scrollUntil(target, options?)` | `options: { container?: sel, direction = 'down', maxSteps = 20, method = 'action' \| 'gesture', stopAtEnd = true }`; 返回找到的 UiObject |
| `typeInto(target, text, options?)` | `.typeInto(text, options?)` | `options: { clear = true, submit = false, verify = true }` |
| `dismissPopups([sels], options?)` | `.dismissPopups(...)` / `flow.dismissPopups(...)` | `options: { once = true, watch = false, timeout }`; `watch` 模式返回可 `stop()` 的守护句柄 |
| `collectList(container, item, options?)` | `.collectList(item, options?)` | `options: { direction, maxSteps, resultType = '$', dedupe = true, until?: fn }`; 返回数组 |
| `launchAndWait(app, options?)` | `.launchAndWait(app, options?)` | `options: { timeout = 10000, bringToFront = true }`; 等待 `currentPackage()` 切换 |
| `backUntil(cond, maxTimes?)` | `.backUntil(...)` | 反复 `back()` 直到条件满足 |
| `toggle(target, checked)` | `.toggle(checked)` | 仅在状态不一致时点击, 点击后校验 |
| `retry(fn, options?)` | `.retry(times, delay?)` | `options: { times = 3, delay = 500, backoff = 1 }` |

所有工具接受 `Humanize` 选项 (`{ offset: [dx, dy], delay: [min, max] }`) 与 `root` 作用域.

---

## 附录 C: 待决事项

进入对应阶段前由维护者拍板, 结果回填第 1 节. 已决事项: Q1 -> D12, Q2 -> D11, Q3 -> D10, Q4 -> D13, Q5 -> D8, Q6 -> D9, Q7 -> D7.

### Q4 (P5.3): 是否引入 "字符串选择器语法"

背景. 目前构造选择器有三种方式:

1. 链式 builder: `text('登录').clickable(true).depth(5)`.
2. 对象 (map) 选择器: `pickup({ text: '登录', clickable: true })`, 键名即 builder 方法名, 由 `Picker.Builder.checkSelectorMap` 反射转换.
3. 字符串 / 正则的简写: `pickup('登录')` / `pickup(/登.+/)`, 含义固定为 "content 匹配" (content = text 或 desc). 这是 Python Host, Node Bridge 与 `wait('登录')` 共用的约定.

"字符串选择器语法" 指的是第四种方式: 用一个紧凑的字符串描述多个条件, 例如 (语法仅为示意):

- `sel('text=登录 clickable')`  等价于 `text('登录').clickable(true)`
- `sel('id=btn_ok | desc~=^确.+$')`  用 `~=` 表示正则, `|` 表示 "或"
- `sel('#btn_ok')`, `sel('.android.widget.Button')`  借用 CSS 的 id / class 记法

它的价值在于: 日志与布局分析器可以直接输出可复制粘贴的单行选择器; `auto.explain` / `auto.dump` 等诊断工具可以用同一种记法描述节点; Python / Node 桥接只需传字符串即可复用 Rhino 侧的解析. 代价在于: 需要新增并长期维护一套语法 (解析器, 转义, 错误提示, 文档, d.ts 无法为字符串内容提供类型提示), 且与方式 3 存在歧义风险 (`pickup('text=登录')` 到底是 content 等于 `text=登录` 还是新语法? 因此新语法必须由独立入口触发, 不能复用 `pickup` 的字符串参数).

可选项:

| 选项 | 内容 | 影响 |
| --- | --- | --- |
| A | 不引入 | 零成本; 诊断输出改为输出方式 2 的对象字面量 (如 `{ text: '登录', clickable: true }`), 同样可复制粘贴, 且有 d.ts 提示. |
| B | 引入, 独立入口 `sel('...')` (或 `selector('...')`), 语法为 "空格分隔的 `键=值` / `键~=正则` / 布尔键" | 与现有 API 无歧义; 解析器可基于 P2.2 的 registry 自动获得全部键名; 需要文档与错误提示. |
| C | 引入 CSS 风格 (`#id`, `.className`, `[text=登录]`, 后代 / 子代组合子 `>`) | 表达力最强 (可描述层级关系), 但与 `compass` 的层级语义重叠, 实现成本最高. |

建议: 选 A 或 B. 若维护者更看重 "日志里的选择器能直接粘贴回脚本", 选 A 即可满足 (对象字面量); 若更看重 "一行字符串跨 Rhino / Python / Node 通用", 选 B. 不建议 C.

结论 (2026-09-05): 选 B, 入口复用 `select` -> D13. 当前无待决事项.

## 附录 D: 证据等级与验收方式

| 标签 | 可以证明 | 不能证明 |
| --- | --- | --- |
| `SOURCE` | 源码存在, 结构符合设计 | 编译或行为正确 |
| `JVM` | Android-free 逻辑的单元测试 (JUnit4, 无 Robolectric; Android 类不可构造, `org.json` 为桩) | 与真实无障碍服务的交互 |
| `ANDROID_BUILD` | `:app:assembleDebug` / `testDebugUnitTest` 通过 | 真机行为 |
| `DEVICE` | 指定设备与 API 级别上的示例脚本 / 冒烟脚本通过 | 未列出设备 / API 级别 |
| `DOCS` | 文档, d.ts, changelog, LSP 声明已同步且版本号已更新 | — |

条目勾选时在其后追加证据, 格式示例: `[x] ... (JVM: FlowSchedulerTest; DEVICE: Pixel 7 / API 34, 2026-09-xx; commit abc1234)`.

---

## 会话记录

### 2026-09-04

- 完成: 三条线索 (无障碍服务层, UiSelector / UiObject / UiObjectCollection, 全局 `wait` 与 Promise 基础设施) 的代码探查; 固定决策 D1-D4; 本 Roadmap 初稿 (commit `aafc9d46d`).
- 追加决策: D1 增加 `auto.wait` 别名; D5 批准 `automator.isServiceRunning()` 判定修正.
- 落地 P0.1 (除冒烟脚本) 与 P0.2 全部条目, 详见各条目后的证据标注. 发现并修正了本文档两处诊断错误: `CloseableManager` 并非无引用 (仅文件名拼写错误); `UiObject` 在 JVM 测试中可构造 (夹具因此无需等待 P2.1 的 `UiNode`).
- 新增源码: `core/automator/pick/CompassParser.kt`; 新增测试: `FakeUiObject`, `SearchAlgorithmTest`, `UiObjectCompassTest`, `CompassParserTest`, `GlobalWaitCompatibilityTest`; 扩充 `SelectorMethodResolverTest`.
- 未做: 真机冒烟 (P0.1 末项), `docs/dev/accessibility-architecture.md` (P0.3, 待决 Q7).
- 下次会话建议起点 (已被 2026-09-05 会话取代, 见下).

### 2026-09-05

- 追加决策 D6-D12 (罗盘 `k` 段返回 `null`, 建立架构文档, 核心服务类不重命名, builder 生成为强烈期望, toolkit 提升为全局, `$flow` 别名, 动作默认 strict); Q4 (字符串选择器语法) 改写为可决策的详细说明, 待 P5 前决定.
- 落地: D6 (`compassClickable` 改写, `UiObjectCompassTest` 更新); `AccessibilityEventWrapper.getIndexInParent` 加界并捕获异常 (原实现在节点树变化时会越界抛出或死循环, 且异常会从 `onAccessibilityEvent` 逃逸); `sample/accessibility/smoke-legacy-api.js` (基于维护者草稿); `docs/dev/accessibility-architecture.md`; changelog `fix` 2 条 (10 语言) 并重新生成 README / CHANGELOG.
- 排查结论: `auto.registerEvent("WINDOW_STATE_CHANGED")` 回调两次不是分发 bug (两个事件 `eventTime` 相差 48 ms, 系统各投递一次; `notificationTimeout="0"` 不合并; 注册为单槽). 处理方式: 冒烟脚本内给出去重示例, P1.3 增加 `auto.registerEvent` 的可选去重 / 去抖.
- 未做: 真机运行冒烟脚本 (需要维护者在设备上执行并把结果回填到 P0 验收).
- 维护者反馈: 冒烟脚本在 5 台设备上 `30/30 checks passed` (设备见 P0 证据); Q4 选 B 并复用 `select` 作为入口 -> D13. P0 关闭.
- 落地 P1.1: 新增 `core/accessibility/registry/{A11yServiceRegistry,ConnectionListeners}.kt` 与 `A11yServiceRegistryTest` (14 用例); `AccessibilityService` 生命周期与伴生对象委托到注册表; `auto.stateListener` 按脚本运行时注册并在退出时注销; `AccessibilityBridge` 构造副作用移除, 默认 bridge 改由 `AbstractAutoJs.createRuntime()` 登记, `pickup` 绑定调用方 bridge. 全量 JVM 1891 / 0 失败. changelog `fix` 3 条. 设计偏离: 状态枚举 3 态 (`UNBOUND / CONNECTED / OPERATIONAL`), "设置中已启用" 留给 P1.2 控制器 (理由见 P1.1 首条).
- 冒烟脚本新增第 7 节 (服务重启, 仅 root / 安全设置可用时执行), 维护者在 4 台 AVD 与 5 台实体机上复跑, 结果与归因见 P1.1 `DEVICE` 证据.
- 维护者反馈 "root 设备上显式关闭无障碍服务后总是自动启动, WSS 不会": 定位为 root 关闭命令的空参数 bug (关闭从未生效) 叠加 `AccessibilityServiceAutoStart` 对显式关闭的无差别恢复. 修复 4 项 (见 P1.2 "提前落地"), changelog `fix` 4 条; 冒烟脚本改为 stop -> 观测解绑 -> start 并增加环境快照. 待维护者复跑: 预期可重启设备 38/38, 其余 32/32 (新增 2 项前置检查), 并确认 root 设备显式关闭后不再自动恢复, XQ-DQ72 的环境快照输出.
- 维护者提出: 抽屉 "无障碍服务" 对话框的 "管理器" 按钮尚未实现 (`DrawerFragment.kt:210` 目前弹 "开发中"), 希望做成对话框形式的服务管理器 (信息展示 / 调试 / 测试 / 开关 / 控制), 兼作本 Roadmap 各阶段的真机调试面板, 文案与 UI 风格须与现有对话框一致. 设计待确认后作为 P1 子项加入.
- 下次起点: 管理器对话框 (设计确认后), 然后 P1.2 (`A11yServiceController`, 含 D5 `isServiceRunning` 修正与 D8 `service/AccessibilityService.kt` 重命名). P1 期间保持每个子项独立提交, 每个子项完成后在真机重跑冒烟脚本.
