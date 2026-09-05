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
| D9 | `UiSelector` builder 方法先手写委托, 但自动生成是强烈期望 | P2.2 的 `SelectorRegistry` 必须以 "可生成" 为设计目标 (方法名, 别名, 参数类型, 过滤器工厂全部为数据); P2 结束时必须给出生成方案评估 (候选: KSP 处理器读取注册表生成 `UiSelector` 的 builder 与 d.ts 签名清单; 或 Gradle 任务 + 模板生成 Kotlin 源码), 评估通过即实施, 而非仅停留在评估. (原 Q6) 已实施 (2026-09-05, P2.2): 生成器位于测试源码集, 生成物 `UiSelectorBuilders.kt` 提交到仓库, 漂移由 `UiSelectorBuildersTest` 守住; 评估与理由见架构文档 3.1. |
| D10 | toolkit 的同步表面提升为全局函数 | 除 `automator.*` 外, `smartClick` / `clickIfExists` / `clickAny` / `findAny` / `scrollUntil` / `typeInto` / `dismissPopups` / `collectList` / `launchAndWait` / `backUntil` / `toggle` / `retry` 等同时注册为全局函数; 便捷性优先于命名空间膨胀. 落地前用当前全局名快照核对冲突 (如 `click` / `toggle` 等已存在或语义相近的名字须避让或统一). (原 Q3) |
| D11 | `flow` 命名空间提供 `$flow` 别名 | 与其它 `Augmentable` 模块的 `$` 前缀别名保持一致. (原 Q2) |
| D12 | Flow 动作步骤默认 `strict = true` | 动作返回 `false` 即 reject `ACTION_FAILED`; 可用 `flow.defaults({ strictActions: false })` 全局关闭. (原 Q1) |
| D13 | 引入字符串选择器语法, 入口复用 `select` | 采用附录 C 的方案 B: `UiSelector` 新增 `select(String)` 重载 (解析字符串并追加过滤器, 返回自身), 因反射注册而自动成为全局函数 `select('...')` (返回 `UiSelector`, 可继续 `.findOne()` 等); 既有无参 `select()` (对找到的节点执行 `ACTION_SELECT`, 含反射出的全局 `select(): boolean`) 保持不变, 两者按参数类型区分. 语法: 空格分隔的条件, `key=value` (值含空格时用引号), `key~=regex` (或 `key=/regex/flags`), 布尔键 `clickable` / `!clickable`, 键名与别名来自 P2.2 的 `SelectorRegistry`; 不引入 `\|` 或层级组合子, 组合需求用 `pickup([...])` / `existsAll` / compass 表达. 落地于 P5.3, 依赖 P2.2. (原 Q4, 2026-09-05) |
| D14 | 无障碍服务管理器以对话框实现, 范围为 "标准版 + 事件监视器" | 抽屉 "无障碍服务" 条目的 "管理器" 按钮 (及长按条目) 打开 `MaterialDialog` 形式的管理器 (非 Activity), 三个区块: 状态 (逐行可复制, 1 s 刷新 + 注册表监听), 控制 (启动 / 停止 / 重启 / 清除节点缓存 / 自检 / 事件监视器), 设置 (root 与安全设置自动启用开关); 完整诊断文本可复制, 作为本 Roadmap 各阶段的真机调试面板. 事件监视器为子对话框, 数据源是 `AccessibilityService` 伴生对象的轻量事件观察者 (`addEventObserver` / `removeEventObserver`, 跨服务重启存活), 即 P1.3 事件总线 Kotlin 侧订阅 API 的雏形. 文案与 UI 与既有对话框一致. 落地于 P1.7. (2026-09-05) |

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
5. 兼容外壳: 被 JS 暴露的类 (`UiSelector`, `UiObject`, `UiObjectCollection`, `AccessibilityBridge`) 保留原包名与 public 签名, 内部委托到新组件; `AccessibilityTool` 保留为薄门面 (P1.2 决定不标记 `@Deprecated`: 它承担控制器的 Android 触点, 见 P1.2 条目).

### 4.2 目标包结构 (新增部分)

```
core/accessibility/
  registry/    A11yServiceRegistry (含嵌套 State / Snapshot / StateListener), ConnectionListeners  [P1.1 已落地]
  diagnostics/ A11yServiceDiagnostics (状态行与诊断文本), A11yServiceSelfCheck, A11yServiceInfoFormat (Android-free)  [P1.7 已落地]
  control/     A11yServiceController (含嵌套 FailureReason / EnsureResult), ServiceStartStrategy + SettingsStoreStartStrategy, EnabledServicesStore (Root / Secure 实现), EnabledA11yServices (Android-free 列表操作)  [P1.2 已落地; 设置页为控制器兜底而非策略]
  event/       A11yEventBus (含嵌套 Subscription / Options / Handler / DeferredScheduler), A11yEvent (信封), AccessibilityDelegate (已迁入)  [P1.3 已落地]
  window/      WindowRootPolicy (mode / filter / fallback, Android-free), WindowRootResolver (Android-free 唯一实现), WindowRootProvider (Android 绑定)  [P1.4 已落地]
  node/        NodeRecyclePolicy, NodeLifecycle (Android-free), NodeLifecycles.kt (Android 侧 default 与扩展), NodeRegistry (Android-free 登记表); AccessibilityNodeInfoAllocator 留在 core/accessibility/ 并以 NodeLifecycle + NodeRegistry 实现  [P1.5 已落地]
  (原文件保留原位置: AccessibilityService, AccessibilityServiceUsher, AccessibilityBridge, UiSelector, ...)
core/automator/
  selector/    SelectorRegistry, SelectorMapParser, PollPolicy, UiSelectorQuery
  pick/        Picker, Detector, Compass (从 UiSelector / UiObject 迁出)
  flow/        FlowStep, FlowScheduler, FlowExecutor, Stabilizer, FlowErrors
  toolkit/     SmartClick, ScrollSearch, TextEntry, PopupDismisser, ListCollector, AppLauncher
runtime/api/augment/
  flow/        Flow (命名空间 `flow`), FlowNativeObject (链式对象), 全局函数 waitAsync / waitThenClick / ...
ui/accessibility/
  A11yServiceManagerDialog, A11yEventMonitorDialog (服务管理器与事件监视器对话框)  [P1.7 已落地]
```

---

## 5. 阶段总览

| 阶段 | 主题 | 依赖 | 对应用户目标 | 状态 |
| --- | --- | --- | --- | --- |
| P0 | 基线, 安全网与低风险修复 | 无 | 1, 2 | 完成 (2026-09-05, JVM + ANDROID_BUILD + DEVICE x5) |
| P1 | 无障碍服务层重构 | P0 | 1 | 代码完成 (P1.1 ~ P1.7 全部落地 2026-09-05); 待维护者以最终构建重跑冒烟脚本与手动验证清单后关闭 |
| P2 | UiSelector / UiObject / UiObjectCollection 重构 | P0 (P1 的 registry 与 window 子项) | 2 | 进行中 (P2.1 / P2.2 完成 2026-09-05, P2.5 罗盘条目提前完成; 下一项 P2.3) |
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
  - AVD API 26: WSS 34/36 (`stateListener` 未见 disconnected -> connected; `pickup` 重启后单次未命中), Root 35/36 (`stateListener`). 原因: 脚本原先调用 `restartService(false)` = 禁用后立即启用, API 26 的设置观察者把两次写入合并为 "仍然启用", 实际未重启 (Root 路径还叠加了下述 "空参数" bug); `pickup` 的单次尝试落在重新绑定后的瞬时空窗. 处理 (见 P1.2 提前落地项): `AccessibilityTool.restartService` 改为等待解绑后再启用; 脚本改为 stop -> 观测解绑 (5 s) -> start, 重启后的查找改为最长 5 s 轮询. 复跑 (2026-09-05 13:16) 40/43: 停止 / 解绑 / 启动 / 新实例 / `stateListener` disconnected -> connected 全部通过 (真正的重启已发生); 剩余 3 项失败均为重绑后 `getRootInActiveWindow()` 持续为 `null` (运行结束时快照 `rootInActiveWindow=false`, `windows=3`, 至少 11 s 未恢复) 所致. 归因 (AOSP 8.0 `AccessibilityManagerService`): 服务重绑后系统重新计算窗口列表时, 尚未重新注册无障碍连接的应用窗口被丢弃, 焦点窗口不在列表中则 `mActiveWindowId` 保持无效, 且带 `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` 的服务不会因 `TYPE_WINDOW_STATE_CHANGED` 更新活动窗口, 直到下一次窗口变化 (`updateWindowsLocked`). 现代系统与有其它无障碍服务常驻的真机不受影响 (连接不会断). 顺带发现 `AccessibilityBridge.windowRoots()` 在此状态下返回 `[null]`, `auto.windowRoots` 因而得到 `[UiObject(null)]`, 使脚本的 "窗口根节点已恢复" 检查误判为通过: 已改为返回空列表 (P1.4 第二条提前落地, 记入 changelog `fix`). 脚本改为等待 `auto.rootInActiveWindow !== null`, 失败时打印窗口列表 (类型 / 包名 / active / focused) 并 `app.launch` 制造一次窗口变化后再探测, 以验证上述归因; 兜底策略留给 P1.4. 维护者复跑 (2026-09-05, 45 项版本): 普通 / Root / WSS 三种配置均全部通过, 重绑后根节点为 `null` 的现象本次未复现 (间歇性, 归因未被证伪也未被证实); P1.4 的兜底评估保留, 以后续出现时的 "windows after restart" 输出为准. 关闭.
  - Sony XQ-DQ72 (QV770340J7): 21/30, 全部 "屏幕上能找到内容" 的检查失败 (`auto.service` 与事件投递正常), 无权限 / WSS / Root 三种配置结果一致; P0 阶段同一设备为 30/30. 尚无该设备的控制台输出, 暂不能定位. 候选原因: (a) 运行时熄屏或锁屏 (远程运行脚本不会点亮屏幕, `home()` / `app.launch` 无可见效果, 但事件仍会投递); (b) 该设备为 root 设备, 若此前经历过 "root 关闭失效" (见 P1.2 提前落地项) 而处于 `accessibility_enabled=0` 但仍在启用列表的不一致状态, 部分系统在该状态下不提供窗口信息. 脚本已增加环境快照 (屏幕 / 锁屏 / 当前包名 / 窗口根节点数 / `accessibility_enabled` / 启用列表) 与两项前置检查 (`screen is on and unlocked`, `auto.windowRoots is not empty`), 并在失败时把每项的 `returned` / `threw` 细节写进最终错误. 复跑 (2026-09-05 12:25, 31/43, 重启段 8/11) 排除了 (a) 与 (b): 快照显示亮屏未锁, 四个谓词全 true, `rootInActiveWindow` 可用, `windows=3`, 当前包名为 AutoJs6 自身 (脚本刻意 `app.launch` 自身以取得稳定锚点), 而连空选择器 `textMatches(/.+/).find(1)` 也为空. 空选择器匹配根节点自身, 因此根节点只能是在 `UiSelector.findImpl` 被黑名单跳过, 即 **保护模式** (设置 > 脚本运行 > 保护模式, `AccessibilityConfig` 把自身包名加入黑名单) 在该设备上开启; 其它设备通过是因为保护模式关闭. 属按设计行为, 非缺陷 (P0 阶段 30/30 时该设备的保护模式应为关闭). 脚本已增加 `guardMode=` 快照字段与前置检查 `guard mode is off`, 失败时打印提示. 维护者确认 (2026-09-05): 关闭保护模式后 普通 / Root / WSS 三种配置均全部通过. 关闭.
- `DOCS`: changelog `fix` 3 条 (10 语言) 已写入并重新生成 README / CHANGELOG. JS 签名无变化, 无需同步 d.ts / 文档项目.

P1.1 结论: 代码与 JVM 证据齐备, 真机基础项全部通过; 重启段的两处例外已归因并处理, XQ-DQ72 定位为保护模式 (关闭后全部通过). 关闭.

### P1.2 服务控制器 `A11yServiceController` (完成 2026-09-05)

提前落地 (2026-09-05, 因维护者真机反馈; 同日迁入控制器, 语义保持不变并补了单测, 见各条末尾的 JVM 标注):

- [x] Root 关闭失效: `stopServiceWithRoot` 以 `settings put secure enabled_accessibility_services $services` 拼接命令, 当 AutoJs6 是唯一启用的服务时 `$services` 为空, shell 丢弃空参数, `settings put` 报 "Bad arguments" 而不写入; 同时 `runCatching {}.isSuccess` 把命令失败当作成功. 表现为 "root 设备上显式关闭后服务总是自动恢复" (实际从未关闭; 540 ms 后抽屉 `sync()` 读回仍在运行). 修复: 值以单引号包裹 (现为 `RootEnabledServicesStore.write`), root / 安全设置的启停一律以 `hasService()` 回读作为结果 (`SettingsStoreStartStrategy` 的 `readBack`), 失败时回落到 `disableSelf()`. 记入 changelog `fix`. (SOURCE; JVM: `SettingsStoreStartStrategyTest.resultsComeFromReadingBackNotFromTheWrites` / `disableRemovesTheServiceEvenWhenItIsTheOnlyOne`)
- [x] 尊重显式关闭: `AccessibilityServiceAutoStart` (每个 `BaseActivity` 创建时) 原先 `!isRunning()` 即通过 root / 安全设置启动, 会重新启用用户显式关闭的服务. 改为仅在 `isMalfunctioning()` (设置中已启用但进程内未绑定) 且等待 `DEFAULT_A11Y_SERVICE_START_TIMEOUT` 后仍未绑定时恢复; 未启用的服务由脚本 / 布局分析器 / 悬浮菜单 / Tile 等显式入口按需启动. 记入 changelog `fix`. (SOURCE: `A11yServiceController.restoreIfMalfunctioning`; JVM: `A11yServiceControllerTest.restoreOnlyTouchesAMalfunctioningService` / `restoreGivesTheSystemAMomentToBindByItself`)
- [x] `startService()` 在服务已运行时直接返回, 不再先走 root / 安全设置的 "禁用 + 启用" (原先 `auto.start()` 会重启正在运行的服务). 记入 changelog `fix`. (SOURCE: `A11yServiceController.start`; JVM: `A11yServiceControllerTest.startLeavesARunningServiceAlone`)
- [x] `restartService()` 在 `stopService(false)` 之后等待 `AccessibilityService.waitForStopped()` (注册表 `awaitUnbound`, 默认 `DEFAULT_A11Y_SERVICE_STOP_TIMEOUT = 2000 ms`) 再启动; 否则部分系统 (AVD API 26 实测) 把 "禁用后立即启用" 合并为无变化, 实际不重启. 记入 changelog `fix`. (SOURCE: `A11yServiceController.restart`, `auto(true)` 的强制重启同样等待解绑; JVM: `A11yServiceRegistryTest.awaitUnboundReturnsOnceTheInstanceIsGone`, `A11yServiceControllerTest.restartWaitsForTheUnbindBeforeEnablingAgain` / `ensureWithForceRestartStopsFirstAndWaitsForTheUnbind`)
- [x] 遗留 (已决): 不再写 `accessibility_enabled`. 依据: minSdk 24, 而 Android 7.0 起 `AccessibilityManagerService.updateAccessibilityEnabledSetting` 根据已绑定 / 绑定中的服务自行维护该值 (N 同时移除了设置里的无障碍总开关), 且 `readConfigurationForUserStateLocked` 不再读取它; 原先关闭本服务时写入的 0 会在其它服务仍启用时残留 (系统只在下一次用户状态变化时重算). 记入 changelog `improvement`. 风险: 仅影响 root / 安全设置的启停路径, 由冒烟脚本第 7 节在 AVD 24 / 25 上覆盖 (2026-09-05 复跑确认: AVD 24 WSS 45/45, AVD 25 WSS 与 Root 各 45/45). (SOURCE: `SettingsStoreStartStrategy` 只写服务列表; DEVICE)

原计划条目:

- [x] `core/accessibility/control/A11yServiceController<S>` (Android-free, 泛型同注册表): 谓词 (`hasService` / `hasInstance` / `isRunning` / `isOperational` / `isMalfunctioning` / `isSatisfied(level)` / `failureReason()`), `start` / `stop` / `restart` / `ensure` / `restoreIfMalfunctioning` / `waitFor*`; 依赖全部注入: 注册表, 启用列表读取 (`Settings.Secure`, 无需权限), `ServiceStartStrategy` 列表, `disableSelf`, 设置页启动器, 日志. 策略: `SettingsStoreStartStrategy(name, serviceName, store, available, readBack)` 以 `EnabledServicesStore` (Root: `settings get / put secure` 经 `ProcessShell`; Secure: `Settings.Secure` 直写) 改写列表, 结果以回读为准; 已列出但未绑定的服务先移出再写回以触发重新绑定. 偏离: 设置页不是策略 (无法报告成功), 而是控制器的最终兜底 `settingsUi`; 策略只有 Root 与 SecureSettings 两个. 纯列表操作抽为 `EnabledA11yServices` (`parse` / `contains` / `attach` / `detach`, 忽略空白与 "null"). (SOURCE; JVM: `A11yServiceControllerTest` 18 用例, `SettingsStoreStartStrategyTest` 7 用例, `EnabledA11yServicesTest` 4 用例)
- [x] `ensure(level, timeoutMillis, launchSettingsIfNeeded, forceRestart): EnsureResult` (`Satisfied(via)` / `Failed(reason)`): 已满足直接返回; 已绑定只差状态 (首个事件) 时只等待, 不经策略重启; 否则按顺序尝试策略 (每个给 `startTimeout` = 2 s 完成绑定, 调用方超时更短时取其值), 绑定后再等 `level` 至 `timeoutMillis` (负数无限); 策略均失败且允许时打开设置页并等待用户. 失败原因 `NOT_ENABLED` / `ENABLED_BUT_UNBOUND` / `BOUND_BUT_NOT_OPERATIONAL` 由 `hasService()` 与注册表组合得出. `AccessibilityTool` 的 4 个变体全部改为 `ensure` 的薄封装: `ensureService()` / `ensureServiceOperational()` 失败抛 `ScriptException` (文案按原因: `error_no_accessibility_permission` / `text_a11y_service_enabled_but_not_running` / 新增 `error_a11y_service_not_operational`), `startServiceAndWaitFor*()` 失败抛 `ScriptInterruptedException` (原语义). 顺带修复原 `ensureService` 的文案颠倒 (未启用时报 "已启用但未运行", 已启用未绑定时报 "服务已禁用"), 记入 changelog `fix`. (SOURCE; JVM: `ensure*` 8 用例)
- [x] `AccessibilityTool` 改为薄门面: 全部谓词与启停 / 等待方法委托到进程级 `AccessibilityTool.controller` (伴生对象惰性装配), 自身只保留需要 `Context` 的 `launchSettings` / `clearCache` / 错误文案. 偏离: 未标记 `@Deprecated` (它是控制器的 Android 门面而非过渡外壳; 实例除文案 context 外无状态). 实例化 12 处 -> 7 处: 共享实例 `AbstractAutoJs.accessibilityTool` 供 `AutoJs` (布局分析事件), `AccessibilityBridgeImpl`, `LayoutInspector` (构造注入), `Auto.accessibilityTool` (改为读取共享实例) 使用; `UiSelector` / `AccessibilityServiceAutoStart` / `SimpleActionAutomator` 不再持有工具 (分别经 bridge / 控制器); 保留自建实例的是需要 Activity / Service context 做 toast 的 `MainActivity`, `DrawerFragment`, `CircularMenu`, `LayoutInspectTileService`, `AccessibilityServiceItemHelper`. (SOURCE)
- [x] `ensureServiceStarted` 的调用点全部经 `AccessibilityBridgeImpl` -> `AccessibilityTool.ensureService(isForcibleRestart)` -> 控制器; `UiSelector.find` 改为 `mAccessibilityBridge?.ensureServiceStarted()` (无 bridge 时无从查找, 不再单独确保); `AutoJs.kt` 的布局分析事件改用共享实例. `auto(true)` 的强制重启改为 `ensure(forceRestart = true)`: 先 `stop` 并等待解绑再启用 (原先立即 `ensureService`, 与 `restartService` 原来的合并问题相同). (SOURCE)
- [x] `service/AccessibilityService.kt` -> `service/AccessibilityServiceItemHelper.kt` (`git mv`, D8). `isRunning` 改为 `controller.isRunning()` (原 `hasService() || isRunning()` 等价于 `hasService()`, 使已启用但未运行的服务显示为开启): 抽屉开关现在反映真实运行状态, 故障服务显示为关闭并带 "故障" 副标题 (`DrawerFragment.refreshSubtitle` 改为只看 `isMalfunctioning()`), 打开开关即经策略重新启用; `start()` 在 `startService(true)` 后等待 `waitForStarted()` (最多 2 s), 避免抽屉 540 ms 后的 `sync()` 在系统尚未绑定时把开关跳回. `restart()` 改为 `restartService(true)`. `AccessibilityServiceAutoStart` 改为 `controller.restoreIfMalfunctioning()`. 记入 changelog `fix` (开关状态). (SOURCE)
- [x] `SimpleActionAutomator.isServiceRunning` 改为 `AccessibilityTool.controller.isRunning()` (D5), 记入 changelog `fix`. 顺带: `auto.services` (`AccessibilityTool.getServices`) 改为直接读取 `Settings.Secure` 的启用列表, 不再要求 root / 安全设置方式可用 (原先无这两种方式时恒为空数组), 记入 changelog `fix`. 文档 / d.ts: 签名均无变化, 无需同步; `automator.isServiceRunning()` 的语义变化待 P6 在 API 文档中标注.

P1.2 证据:

- `JVM`: 新增 `A11yServiceControllerTest` (18) / `SettingsStoreStartStrategyTest` (7) / `EnabledA11yServicesTest` (4), 连同 `A11yServiceRegistryTest` (15) 与 `AccessibilityServiceAutoStartCoordinatorTest` (3) 共 47 / 0 失败; 全量 `:app:testAppDebugUnitTest` 1930 tests / 0 failures (2026-09-05).
- `ANDROID_BUILD`: `:app:assembleAppDebug` 通过, 产出 `app/build/outputs/apk/app/debug/autojs6-v6.8.0-*.apk` (2026-09-05).
- `DOCS`: 字符串 `error_a11y_service_not_operational` x 11 文件 (经 `build/check-strings.py` 校验); changelog `fix` 4 条 + `improvement` 1 条 (10 语言), README / CHANGELOG 已重新生成.
- `DEVICE` (2026-09-05, 维护者): 冒烟脚本 AVD API 24 普通 34/34 + WSS 45/45, AVD API 25 普通 34/34 + WSS 45/45 + Root 45/45 (重启段全部通过, 不写 `accessibility_enabled` 的决定成立). 强制停止实验 (全部实体设备与 AVD: 先启用服务, 强制停止后冷启动): 抽屉开关始终为关闭, 与 root / WSS 是否具备及自动启用开关无关, 仅 QV770340J7 出现过一次 "故障" 副标题且不可复现. 解读: 强制停止时系统自行把本服务移出 `enabled_accessibility_services` (`AccessibilityManagerService` 的 `PackageMonitor.onHandleForceStop`), 冷启动后服务处于 "未启用" 而非 "故障", `restoreIfMalfunctioning` 按设计不介入 (等同用户显式关闭, 见 "尊重显式关闭" 条); 一次性的 "故障" 副标题是抽屉 `sync()` 在系统尚未完成列表更新时读到旧值 (瞬态, 下一次同步即消失). 结论: 现行为符合预期, 强制停止后是否自动重新启用留作待决 (附录 C, 建议维持不自动启用).

跟进 (2026-09-05, 维护者提出的两处细节, 同属服务启动的 UI 触发点):

- [x] 触发点接续: 悬浮菜单 "布局范围分析" / "布局层次分析" 在服务未启用但可自动启动 (root / WSS) 时需要触发两次 (`CircularMenu.inspectLayout` 在 `startService(false)` 成功后直接返回). 新增 `AccessibilityTool.ensureServiceAsync(level, timeout, launchSettingsIfNeeded, onResult)` (`Schedulers.io()` 执行 `ensure`, 主线程回调; root shell 与等待绑定不再阻塞主线程) 与 `explainFailure(reason)` (未启用: 提示 + 设置页; 其它原因: 按原因 toast), `LayoutInspector.captureCurrentWindowWithRetry(attempts = 10, interval = 300 ms, onUnavailable)` (服务刚绑定后活动窗口根节点可能短暂为 `null`); `CircularMenu` / `LayoutInspectTileService` / `AutoJs.kt` 的布局分析广播三处改为 确保 (异步) -> 绑定后立即捕获 (带重试) -> 显示; 重试耗尽时 toast 新增字符串 `error_active_window_root_unavailable`. 记入 changelog `fix`. (SOURCE)
- [x] 悬浮 UI 跟随宿主设置: 悬浮按钮的菜单 / 对话框 / toast 与布局分析器窗口由 application / service context 构建, 语言与夜间模式跟随系统而非 AutoJs6 自身设置 (AppCompat 只对 Activity 应用这两项). 新增 `ContextUtils.withHostConfiguration()`: 以空 `Configuration` 仅覆盖 `uiMode` 夜间位 (按 `AppCompatDelegate.getDefaultNightMode()` 解析, 跟随系统时取 base 的值) 与 locale (`Language` 偏好非 AUTO 时), 其余字段继续跟随 base (旋转 / 字体缩放不受影响); 接入点 `CircularMenu` (mContext 与两处 inflate), `LayoutFloatyWindow.onCreate` (service context), `AutoJs.kt` 与 `LayoutInspectTileService` 传给布局分析窗口的 context. 主题 / 语言在应用内切换时 `MainActivity` 重建并 `refreshCircularMenuIfNeeded` 重建悬浮菜单, 因此无需监听变化. 记入 changelog `improvement`. (SOURCE)

- [x] Shizuku 作为第三种无人值守启用方式 (维护者 2026-09-05 提出): `ShizukuEnabledServicesStore` 经 `WrappedShizuku.execCommand` (Shizuku 用户服务, shell 或 root 身份) 执行 `settings get / put secure`, 空列表的引号处理与 root store 相同, Shizuku 的任何问题以异常抛出并被策略计为失败; 策略 `"shizuku"` 排在 root 与安全设置之后 (经另一进程与一次 shell 往返, 首次绑定用户服务可能耗时数秒), 可用条件 = 偏好开关 `key_enable_a11y_service_with_shizuku` (默认开启, 设置页与管理器设置区各一个开关) 且 `WrappedShizuku.isOperational()` (binder 存活且已授权). 管理器状态区新增 `Shizuku 权限` 行 (未安装 / 未运行 / 未授予 / 可用); 冒烟脚本的 `canRestartServiceUnattended()` 纳入 Shizuku. 新增字符串 `text_enable_a11y_service_with_shizuku_automatically` / `summary_enable_a11y_service_with_shizuku` / `text_not_running` x 11. 记入 changelog `feature`. (SOURCE: `c8fa189f2`; DEVICE: 维护者 2026-09-05 真机验证 "Shizuku 策略功能逻辑正常")

跟进证据: `JVM` 无新增纯逻辑 (均为 Android 侧), 无障碍相关 58 用例 0 失败; `ANDROID_BUILD` `:app:assembleAppDebug` 通过 (2026-09-05); `DOCS` 字符串 `error_active_window_root_unavailable` x 11 (经 `build/check-strings.py`), changelog `fix` 1 条 + `improvement` 1 条 (10 语言), README / CHANGELOG 重新生成; `DEVICE` 维护者确认 (2026-09-05): 服务自动启动后一次触发即完成布局分析 "正常", 悬浮 UI 跟随宿主语言 / 夜间模式 "正常".

### P1.3 事件总线 `A11yEventBus` (完成 2026-09-05)

- [x] `core/accessibility/event/A11yEventBus<E>` (Android-free 泛型, 时钟 / 延迟调度器 / 事件保留方式均注入): 订阅项 = (owner, 事件类型集合或 null, 优先级, 是否可消费, 选项, 处理器), 按优先级升序 (相同优先级按注册顺序) 分发, 可消费订阅项返回 `true` 终止链, 处理器异常隔离并记录, 订阅表写时复制 (处理器内订阅 / 退订安全). `AccessibilityService` 伴生对象持有三个实例: `eventBus` (`A11yEvent` 信封: 原始事件 + 按需构建的 `wrapper` / `record`), `keyBus` (`KeyEvent`, 类型 = keyCode), `gestureBus` (手势 id). 原 `eventBox` (JS 回调) = `PRIORITY_SCRIPT` (0) 的非消费订阅, 每个 (ownerId, 类型) 一槽 (重注册即替换); 原 `delegates` = 各自优先级 (100 / 200 / 300) 的消费订阅; P1.7 的事件观察者 = `PRIORITY_OBSERVER` (-100); 按键观察者 (`OnKeyListener.Observer`, 0) 与拦截器 (`KeyInterceptor.Observer`, 100, 消费) 成为 `keyBus` 上的兼容外壳 (方法名不变), 实例属性 `onKeyObserver` / `keyInterrupterObserver` 委托到伴生对象, 因此订阅在服务重启后依然存在 (原先随实例丢失: `auto.registerEvent` 回调与 `events.observeKey()` 均失效, 记入 changelog `fix`); 原 `stickOnKeyObserver` 为 `keyObserver` 的别名; 原手势 `EventDispatcher` (私有且从未有监听者) 以 `gestureBus` 替代; `eventExecutor` 删除 (按键 / 手势改为同步分发, 现有消费方均只做转投). 派发顺序 观察者 -> 脚本回调 -> delegate (按优先级, 可消费) 由 `A11yEventBusTest.serviceDispatchOrderIsObserversThenScriptsThenDelegates` 锁定. 偏离: 订阅项为总线的嵌套类 `Subscription` 而非独立文件 `A11yEventSubscription`; 按键 / 手势不与无障碍事件共用一个实例 (载荷类型不同), 共用的是同一个类与语义. (SOURCE; JVM: `A11yEventBusTest` 16 用例)
- [x] `AccessibilityService.addDelegate(priority, delegate)` 即 `eventBus.subscribe(owner = delegate, consumable = true)`, 新增 `removeDelegate(delegate)`; `removeAllAccessibilityEventCallbacks(ownerId)` = `eventBus.unsubscribeAll(ownerId)`; `AbstractAutoJs` 的三个注册未改调用方式 (经 `addDelegate` 即经总线). `AccessibilityDelegate` 迁入 `core/accessibility/event/` (`git mv`, 3 个实现类改 import). 删除无调用方的 `clearAccessibilityEventCallback()`. (SOURCE)
- [x] `fastRootInActiveWindow` 在 `TYPE_WINDOW_STATE_CHANGED` / `TYPE_VIEW_FOCUSED` 时无条件更新, 且移到分发之前 (MODE_FAST 的回调已能看到新窗口; 原先在 JS 回调之后, delegate 之前). 回收策略纳入 `NodeLifecycle` 留给 P1.5. (SOURCE)
- [x] Kotlin 侧订阅 API 即 `AccessibilityService.eventBus.subscribe(owner, types, priority, consumable, options, handler)` 返回 `Subscription` 句柄 (`unsubscribe` / `unsubscribeAll(owner)`); 安静窗口检测: `eventBus.lastDispatchAtMillis` 与 `Subscription.lastMatchedAtMillis` (匹配类型即记录, 在去重之前); P1.7 的 `addEventObserver` 改为其薄封装. `A11yEvent.copied()` (拷贝原始事件) 供延迟投递. (SOURCE; JVM: `timestampsRecordMatchesBeforeFiltering`)
- [x] `auto.registerEvent(name, callback, options?)`: `{ distinct: 'window' | 'source' | true | (event) => key, within?: 500 }` (前沿去重: 键与上次投递相同且在窗口内则丢弃; 无键函数为纯节流, 无窗口则相同键持续丢弃) 与 `{ debounce: ms }` (后沿: 安静该时长后只投递这一串的最后一个, 投递内容为原始事件的拷贝, 在主 looper 上执行), 默认关闭. 偏离: `distinct` 的函数形态为键函数 `(event) => key` 而非比较器 `fn(prev, next)` (可与 `within` 正交组合, 实现与测试更简单). 同一能力即总线的 `Options(distinctKey, distinctWithinMillis, debounceMillis)`. `'source'` 需读取来源节点 (每个事件一次节点查询), 文档需标注. 冒烟脚本新增 1b 节 (3 项检查, 现在可重启设备 48 项, 其余 37 项). 记入 changelog `feature`. (SOURCE; JVM: `distinct*` / `debounce*` 6 用例; DEVICE 待维护者)
- [x] `AccessibilityEventWrapper.source` 改为首次访问时解析并缓存; `A11yEvent.seal()` 在 `onAccessibilityEvent` 返回前调用, 此后 API < 33 上未解析的 `source` 为 null (API >= 33 事件不回收, 仍可解析). 去抖投递使用事件拷贝, 不受影响. 记入 changelog `improvement`. `depth` / `indexInParent` 经 `NodeLifecycle` 管理父链留给 P1.5. (SOURCE)
- [x] JUnit4: `A11yEventBusTest` 16 用例 (优先级与注册顺序, 类型过滤与 null, 消费中断仅对可消费项生效, 句柄 / owner 注销, 异常隔离, 处理器内改订阅表, 去重的窗口 / 键变化 / 纯节流 / 无窗口, 键函数异常放行, 去抖只投递最后一个且可取消, 无调度器时拒绝去抖, 时间戳, 保留函数默认恒等, 服务分发顺序). (JVM)

P1.3 证据:

- `JVM`: `A11yEventBusTest` 16 / 0 失败; 全量 `:app:testAppDebugUnitTest` 见会话记录.
- `ANDROID_BUILD`: 见会话记录.
- `DOCS`: changelog `fix` 1 条 + `feature` 1 条 + `improvement` 1 条 (10 语言), README / CHANGELOG 重新生成; 无新增字符串; d.ts / API 文档的 `auto.registerEvent` 第三参数待 P6 同步.
- `DEVICE` (2026-09-05): 维护者复跑 48 项脚本, API >= 31 的设备全部通过, G8441 (API 28) 与 AVD API 24 / 25 / 26 恰好只有 1b 节的两项失败 (`distinct` 与 `debounce` 各 "delivered events" 为 false). 用本机连接的 AVD 24 复现并归因 (探针脚本经 `RunIntentActivity` 运行, 结果写入 `/sdcard`): 不是总线或选项的问题, 而是 `home()` 注入的 HOME 键在 API <= 28 上触发系统的 5 s 应用切换锁 (`ActivityManager: Activity start request from <uid> stopped`, `APP_SWITCH_DELAY_TIME`), 后台应用在此窗口内的 `app.launch(autojs.packageName)` 被系统挂起到锁过期才执行 (4 台设备上 Splash 的 `WINDOW_STATE_CHANGED` 一律在 `home()` 后 +5.1 ~ 5.3 s 到达), 而脚本的 `transitionWindows()` 只等 1.5 s, 检查时窗口切换尚未发生. 处理: 脚本新增 `bringAutoJs6ToFront()` (`home()` -> `app.launch` -> 轮询 `currentPackage()` 最长 8 s -> 稳定 1 s), 第 1 节与 1b 节共用; 无需改动运行时 (`app.launch` 的语义正确, 只是被系统延迟). 复跑 (本机 adb, 2026-09-05 20:00): AVD API 24 / 25 / 26 / 36, G8441 (API 28), XQ-AT72 (API 31), 22120RN86C (API 33), 23046RP50C (API 35) 全部 `48/48 checks passed`. 服务重启后回调仍触发: 维护者用 `auto.registerEvent` + `keepAlive(20000)` 的脚本确认 关闭服务 -> 日志停止, 再开启 -> 日志继续 (至脚本结束).

### P1.4 窗口根节点 `WindowRootProvider` (完成 2026-09-05)

- [x] `AccessibilityBridge.java` / `AccessibilityBridgeImpl.java` 转为 Kotlin (FQN, public 签名, `@ScriptInterface` 与 `MODE_*` / `FLAG_*` 常量不变; `getService` / `getInfoProvider` / `getNotificationObserver` 成为抽象属性, Java 调用方 `Events.java` 不受影响), 三处根节点逻辑合一: `core/accessibility/window/WindowRootPolicy<W>` (Android-free 数据类: `mode` NORMAL / FAST, `filter` 谓词, `fallback`) + `WindowRootResolver<W, R>` (Android-free 唯一实现: `roots` / `currentRoot` / `activeRoot` / `windows`, 访问器全部经 `Source` 注入, 每个访问器抛出即视为不可用并记日志) + `WindowRootProvider` (Android 绑定: 每次查询经 lambda 取当前服务实例, 进程级 `WindowRootProvider.default`). bridge 持有单个不可变 `windowRootPolicy` (`@Volatile`, 由 `setMode` / `setWindowFilter` / `flags` 以 `copy` 重建). 偏离: 策略不含 `flags` 中与根节点无关的位 (`findOnUiThread` / `useUsageStats` / `useShell` 仍由 bridge 与 `UiSelector` 处理), 只把新的兜底位翻译为 `fallback`. (SOURCE; JVM: `WindowRootResolverTest` 13 用例)
- [x] 返回值改为非空列表 (内部过滤 `null`), 调用方 `UiSelector.kt:810` 与 `SimpleActionAutomator.kt:259` 移除各自的 `filterNotNull()`. 提前落地 (2026-09-05): `AccessibilityBridge.windowRoots()` 的 `MODE_NORMAL` / `MODE_FAST` 分支已改为根节点为 `null` 时返回空列表 (`singletonOrEmpty`), `auto.windowRoots` 不再得到 `UiObject(null)`; 两处 `filterNotNull()` 待本项迁移时移除. (SOURCE; changelog `fix`) 本项完成: `windowRoots(): List<AccessibilityNodeInfo>` 非空类型, `UiSelector.findImpl(roots: List<AccessibilityNodeInfo>, max)` 与 `SimpleActionAutomator.performAction` 的 `filterNotNull()` 已移除. 顺带 (记入 changelog `improvement`): 有窗口过滤器时 `getRootInCurrentWindow()` (`auto.root`) 在首个通过过滤的窗口没有根节点时继续看下一个窗口 (原先直接返回 `null`), 且惰性解析 (只查询到首个有根节点的窗口). (SOURCE; JVM: `currentRootWithFilterIsTheFirstPassingWindowThatHasARootResolvedLazily`)
- [x] 活动窗口未知时的兜底: API 26 上服务重绑后 `getRootInActiveWindow()` 可持续为 `null` 直到下一次窗口变化 (归因见 P1.1 证据的 AVD API 26 条目), 此时 `find*` / `pickup` 全部为空. 候选: (a) 回落到 `windows` 中 `TYPE_APPLICATION` 窗口的根节点 (需确认该状态下 `windows` 是否含焦点窗口, 以冒烟脚本新增的 "windows after restart" 输出为依据); (b) `fastRootInActiveWindow` (同样为 `null`, 无效); (c) 服务连接后若根节点在 N ms 内不可用, 由应用侧制造一次窗口变化 (如添加并移除 1 px 不可见窗口) 促使系统重算. 以 `WindowRootPolicy` 的一个选项实现, 默认行为经真机数据决定. 落地: 方案 (a) 为 `WindowRootPolicy.Fallback.APPLICATION_WINDOWS` (窗口列表中 `TYPE_APPLICATION` 窗口的根节点, 焦点窗口优先, 其次活动窗口, 其余按列表顺序; 对 NORMAL 与 FAST 两种模式都在该模式的根节点为 `null` 时生效; 有过滤器时不适用), JS 侧为 `auto.setFlags(['appWindowsFallback'])` (`FLAG_APP_WINDOWS_FALLBACK = 8`); 布局分析器固定使用 活动窗口 -> fast root -> 应用窗口 的顺序 (原先只有前两级). 默认 `NONE` (行为不变, D2), 依据: 2026-09-05 在 AVD API 26 上以探针脚本 (`build/smoke/root-after-rebind-probe.js`) 做 12 轮重启 (停止 -> 观测解绑 -> 延迟 0 / 300 / 2000 / 5000 ms -> 启动, AutoJs6 或桌面前置交替), 重绑后首个采样 (24 ~ 52 ms) `rootInActiveWindow` 均已可用, 未能复现 P1.1 时观察到的持续 `null` 状态 (同一探针还观察到重绑后 `windows` 可为空而活动根节点可用, 说明两者独立), 因此不把兜底设为默认; 该状态再次出现时, 脚本可用标志打开, `windows` 中的应用窗口 (P1.1 快照显示为 3 项) 即成为查询对象. 方案 (c) 未实现 (需要悬浮窗权限且会产生真实窗口事件, 副作用大于收益). (SOURCE; JVM: `fallback*` 4 用例; DEVICE: 探针见上)
- [x] 明确并文档化 `getRootInActiveWindow()` 是否应用窗口过滤器 (现状不应用, 与 `getRootInCurrentWindow()` 不同); 保持现状 (D2), 在 KDoc 与 API 文档中标注差异. 同时文档化保护模式 (`AccessibilityConfig` 黑名单) 对 `find*` / `pickup` / 节点动作的影响: AutoJs6 自身前置时一律为空 / `false`, 这是按设计的行为 (冒烟脚本已把它作为前置检查). 落地: `AccessibilityBridge` 类级 KDoc 列出三个查询各自遵循的设置 (`windowRoots` / `getRootInCurrentWindow` 遵循过滤器, `getRootInActiveWindow` 忽略; 均不受黑名单影响, 黑名单由 `UiSelector.findImpl` 与 `SimpleActionAutomator.performAction` 应用, `findImpl` 处加了注释), 架构文档 1.4 已有保护模式说明; `WindowRootResolver.activeRootIgnoresTheFilter` 锁定该差异. API 文档 (`automator.md` 的 `auto.root` / `auto.rootInActiveWindow` / `auto.setFlags`) 与 d.ts 的同步归入 P6 (新增标志 `appWindowsFallback` 也在其中). (SOURCE; DOCS)
- [x] `LayoutInspector.kt:42` 改为经由 `WindowRootProvider` 获取窗口列表 (可选: 若布局分析器希望忽略脚本过滤器, 传入 `filter = null`). 落地: `LayoutInspector` 持有 `WindowRootProvider.default`, 根节点为 `activeRoot(NORMAL)` ?: `activeRoot(FAST + APPLICATION_WINDOWS)`, 窗口列表为 `provider.windows()`; 不使用任何脚本的过滤器 (布局分析器看的是屏幕上的一切). (SOURCE)

P1.4 证据:

- `JVM`: `WindowRootResolverTest` 13 用例 (模式选择, 未知活动窗口默认为空, 访问器抛出视为不可用, 兜底顺序 焦点 > 活动 > 列表顺序 且排除非应用窗口与无根节点窗口, 兜底对 FAST 模式生效, 活动根节点可用时不触发兜底, 无窗口列表时兜底为空, 过滤器与模式无关, `currentRoot` 惰性且跳过无根节点的窗口, `activeRoot` 忽略过滤器, 过滤器 / 根节点异常隔离并记日志, 旧模式值映射); 全量 `:app:testAppDebugUnitTest` 1959 tests / 0 failures (2026-09-05).
- `ANDROID_BUILD`: `:app:assembleAppDebug` 通过 (2026-09-05 22:03).
- `DOCS`: changelog `feature` 1 条 (`appWindowsFallback`) + `improvement` 1 条 (根节点逻辑合一, `auto.root` 继续看后续窗口) (10 语言), README / CHANGELOG 重新生成; 无新增字符串; API 文档 / d.ts 待 P6.
- `DEVICE`: 冒烟脚本第 1 节新增 5 项 (拒绝一切的过滤器使 `auto.windowRoots` 为空且 `auto.root` 为 `null`; `auto.rootInActiveWindow` 忽略过滤器; 按包名过滤只得到该包的根节点; `setMode('fast')` 有根节点; `appWindowsFallback` 在活动窗口已知时不改变结果), 现共 53 项; 恢复 "仅活动窗口" 状态经 `runtime.accessibilityBridge.setWindowFilter(null)` (`auto.setWindowFilter(null)` 装的是接受全部窗口的过滤器, 既有语义, D2 保持). 结果见会话记录.

### P1.5 节点生命周期 `NodeLifecycle` (完成 2026-09-05)

- [x] 定义 `NodeRecyclePolicy`: API >= 33 全部 no-op; API < 33 按现有分配器策略. 所有内联 `.recycle()` (`AccessibilityService.kt:56-61`, `DFS.kt:32`, `BFS.kt:28`, `action/ScrollAction.kt:23,49`, `action/ScrollMaxAction.kt:31,51,62`, `UiObject.kt:453,597`) 改为调用策略. 落地: `core/accessibility/node/NodeRecyclePolicy` (Android-free 枚举 `RECYCLE` / `NO_OP`, `forSdk(sdkInt)` 以 33 为界) + `NodeLifecycle(policy, log)` (Android-free: `release(recycle)` 仅在策略要求时执行且隔离异常, `releaseAll(nodes, except, recycle)` 按同一性跳过根节点并计数) + `NodeLifecycles.kt` (Android 侧: `NodeLifecycle.default` 取 `Build.VERSION.SDK_INT`, `release(AccessibilityNodeInfo?)` / `release(AccessibilityNodeInfoCompat?)` / `releaseAll(compat 列表)` 扩展). 内联回收全部改为经生命周期对象: `AccessibilityService.recycleFastRootInActiveWindow`, `DFS` / `BFS` (`SearchAlgorithm.search` 新增末位参数 `lifecycle = NodeLifecycle.default`, 供测试注入), `ScrollAction` / `ScrollMaxAction`, `UiObject.isShifted` 的探针; P1.7 诊断 (`A11yServiceDiagnostics` / `A11yServiceSelfCheck`) 的私有 `recycle` 同样改为经此. `UiObject.recycle()` 重写保留 (它就是回收本身; 有分配器时改为 `allocator.recycle(this)`, 使脚本自行回收的节点从分配器中移除). 回收失败 (节点已回收等) 只记 `Log.w`, 不再 `printStackTrace` 也不中断查找. (SOURCE; JVM: `NodeLifecycleTest` 5 用例, `SearchAlgorithmTest` 新增 `nothingIsRecycledOnPlatformsWithoutNodeRecycling` / `aFailingRecycleDoesNotAbortTheSearch`)
- [x] `AccessibilityNodeInfoAllocator` 保留类名 (内部使用), 内部实现替换为线程安全容器 + 可选调试登记; `global` 单例改为按 `ScriptRuntime` 作用域 (脚本退出时 `recycleAll`). 落地: 类名与包名不变 (`core/accessibility/`, 未迁入 `node/`, 以免改动 `UiSelector` 构造函数所暴露的 FQN), 构造参数 `lifecycle = NodeLifecycle.default`; 容器为 Android-free 的 `node/NodeRegistry<N>` (`Collections.synchronizedMap(IdentityHashMap)`, `register` / `unregister` / `drain` 快照), 按对象同一性登记 (原 `HashMap` 按 `AccessibilityNodeInfo.equals` 即节点 id 去重, 指向同一视图的两个实例只回收其一); 无需回收的平台上不登记. 顺带修复 (记入 changelog `fix`): compat 重载 `getChild(AccessibilityNodeInfoCompat, i)` / `getParent(compat)` / `findAccessibilityNodeInfosBy*(compat)` 原先调用包装对象自身的访问器, 而 `UiObject` 重写了这些访问器并回调分配器, 任何带分配器的 `UiObject` 调用 `getChild()` 都会无限递归 (这也是分配器此前在生产路径中无人使用的原因); 现在在 `unwrap()` 后的原始节点上操作并以 `AccessibilityNodeInfoCompat.wrap` 包回. `NoOpAllocator` 只重写 `register` (不记录) 与 `recycleAll` (-1); `global` 删除 (无引用); `UiObject.child()` / `parent()` 创建的节点继承分配器, 有分配器时 `UiObject.recycle()` 转 `allocator.recycle(this)` 以从登记表移除. **偏离: "按 `ScriptRuntime` 作用域并在退出时 `recycleAll`" 评估后不接入.** 曾实现并在真机验证 (`ScriptRuntime.nodeAllocator` 交给 `selector()` 与 `auto.root` 等), 但 AVD API 24 的探针暴露了一个根本问题: API 33 以下 `AccessibilityNodeInfo` 按进程池化 (`obtain()` 复用 `recycle()` 过的实例, AutoJs6 的全部脚本共用一个进程), 脚本经 compat 访问器取得的普通 compat 节点若被脚本自行 `recycle()`, 登记表里的同一实例可能已被另一个脚本 (或同一脚本后续的查找) 的 `obtain()` 复用, 退出时的 `recycleAll` 会 "成功" 回收别人的活节点 (探针: 脚本对 `getChild(0)` 的结果 `recycle()` 后, 退出时 logcat 仍报 `Registered: 1; released now: 1`, 即第二次回收没有抛 "已回收" 而是命中了复用后的实例). 不回收的代价只是失去池化 (对象可被 GC), 回收错误的代价是跨脚本的状态损坏, 风险不对称, 故运行时不带分配器 (`ScriptRuntime.selector()` 与 `auto.root` 等保持 `allocator = null`), 分配器作为参考实现保留 (KDoc 记录原因); 若 P2.5 引入 `UiNode` 抽象使 compat 访问器也返回可追踪的包装对象, 可再评估. (SOURCE; JVM: `NodeRegistryTest` 2 用例; DEVICE: 探针见 P1.5 证据)
- [x] 补测试: API 级别分支 (以注入的 `sdkInt` 替代 `Build.VERSION.SDK_INT`), 登记 / 回收计数. 落地: `NodeLifecycleTest` (32 / 33 边界, `release` 仅在 RECYCLE 下执行, 失败记日志不抛出, `releaseAll` 按同一性跳过根节点 (相等但不同一的节点照常释放) 并只计成功, NO_OP 下 `releaseAll` 为 0), `NodeRegistryTest` (同一性登记, `unregister` 返回值, `drain` 清空), `SearchAlgorithmTest` 改为显式传入 `NodeLifecycle(RECYCLE)` 并新增 NO_OP 与回收失败两个用例. 分配器本身 (`AccessibilityNodeInfo` 类型) 不在 JVM 上测, 由真机探针覆盖. (JVM)

P1.5 证据:

- `JVM`: `NodeLifecycleTest` 5 + `NodeRegistryTest` 2 + `SearchAlgorithmTest` 9 (含新增 2) 全部通过; 全量 `:app:testAppDebugUnitTest` 1968 tests / 0 failures (2026-09-05).
- `ANDROID_BUILD`: `:app:assembleAppDebug` 通过 (2026-09-05 22:28).
- `DOCS`: changelog `improvement` 1 条 (回收统一) + `fix` 1 条 (分配器递归) (10 语言), README / CHANGELOG 重新生成; 无新增字符串; JS API 无变化.
- `DEVICE` (本机 adb): 冒烟脚本 AVD API 24 `53/53 checks passed` (回收平台, 覆盖 DFS / BFS / 滚动动作). 分配器探针 `build/smoke/allocator-probe.js` (以临时接入运行时的构建, 对 `auto.root` 与 `findOne` 结果调用 4 个 compat 访问器, 再 `recycle()`): API 24 与 Xiaomi 23046RP50C (API 35) 均无递归 (递归修复有效); API 35 登记数恒为 0 (NO_OP 平台不记录); API 24 上暴露了上述池化复用问题, 据此决定不接入运行时 (最终构建中 `runtime.nodeAllocator` 不存在, compat 访问器行为与重构前相同). 另: AVD API 24 上第二次 `adb install -r` 后无障碍管理器不再绑定本服务 (`dumpsys accessibility` 的 `services:{}` 为空, 设置中仍列出, 应用报 "已启用但未运行"), 改写 / 删除并重写设置项与启动应用均无效, 需重启模拟器; 与本项无关, 记入架构文档 1.7.

### P1.6 收尾 (完成 2026-09-05)

- [x] 无障碍截图: 抽出 `A11yScreenshotter` (含 `ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT` 重试), `SimpleActionAutomator.captureScreen` 改为消费; Python Host 的 `PythonHostAccessibilityScreenCapture` 是否切换见 P5.4. 落地: `core/accessibility/A11yScreenshotter` (API 30+): `take(displayId, mutable, executor, callback)` 单次尝试并把硬件缓冲转为软件 ARGB_8888 位图 (转换异常记为 `ERROR_INTERNAL = -1`), `takeWithRetry(..., maxAttempts = 60, retryDelayMillis = 50)` 只对限频错误在主 looper 上重试; `Result.Success(bitmap)` / `Failure(errorCode)`. `captureScreen` 改为消费, 并发调用仍共享同一 promise. 两处顺带改进 (记入 changelog `improvement`): 硬件缓冲 (`ScreenshotResult.hardwareBuffer`) 现在总会被 `close()` (原先只回收了包装位图, 缓冲泄漏到 GC); 限频重试从无上限改为最多 60 次 (3 s), 之后与其它失败一样以 `null` 兑现 (原先系统持续限频时 promise 永远不兑现). Python Host 未切换 (其同步 + 截止时间的版本可在 P5.4 改为消费 `take()`). (SOURCE)
- [x] Node Bridge / Python Host 中 3 处 `hasOperationalState` 三元表达式改为 `registry.state >= OPERATIONAL` (仅替换判定, 不改其它逻辑). 落地: 实际 5 处 (`NodeBridgeProtocol` 3 处, `NodeBridgeProviderRegistry` 1 处, `PythonHostCapabilityBroker` 1 处) 均改为 `AccessibilityService.registry.isOperational` (`OPERATIONAL` 蕴含已绑定且实例非空, 因此 `hasInstance() && hasOperationalState && instance != null` 三段合一); `hasOperationalState` 伴生属性保留为兼容外壳. (SOURCE)
- [x] 更新 `docs/dev/accessibility-architecture.md` (若 P0.3 已建立) 的 "目标" 章节. 落地: 第 1 章标题与 1.1 表更新为 P1 完成后的现状 (bridge / window / node / 截图), 2.1 分层图标注 P1 已落地的组件, 2.2 迁移映射的 P1 各行标记完成日期 (P1.4 / P1.5 已在各自提交中更新), 新增 P1.6 行. (DOCS)

P1.6 证据:

- `JVM`: 无新增纯逻辑 (截图与判定替换均为 Android 侧); 全量 `:app:testAppDebugUnitTest` 1968 / 0 失败 (5 skipped).
- `ANDROID_BUILD`: `:app:assembleAppDebug` 通过 (2026-09-05).
- `DOCS`: changelog `improvement` 1 条 (10 语言), README / CHANGELOG 重新生成; 无新增字符串; JS API 无变化 (`automator.captureScreen()` 签名与成功路径不变).
- `DEVICE`: `build/smoke/capture-probe.js` (三次 `automator.captureScreen()`: 连续两次以触发限频重试, 停 1 s 后第三次) 在 Xiaomi 23046RP50C (API 35) 全部得到 2880x1800 位图, 耗时 138 / 286 / 68 ms (第二次的 286 ms 即限频重试所致, 原先此路径同样重试但无上限); 同机冒烟脚本 `42/42` (无人值守偏好均关闭, 重启段按设计跳过). API < 30 的设备 `captureScreen` 本就返回 `null` (`@RequiresApi(R)` 路径不变), 探针在 AVD API 24 按设计跳过.

### P1.7 服务管理器对话框 (D14, 完成 2026-09-05)

抽屉 "无障碍服务" 条目的 "管理器" 按钮此前只弹 "开发中"; 现为对话框形式的服务管理器, 同时是本 Roadmap 后续阶段 (P1.2 控制器, P1.3 事件总线, P1.4 窗口根节点) 的真机调试面板: 状态行与诊断文本直接给出注册表状态, 事件计数, 窗口与根节点, `serviceInfo` 标志, 监听者数量与自检结果, 可复制后回填到各阶段的 `DEVICE` 证据.

- [x] `ui/accessibility/A11yServiceManagerDialog`: `MaterialDialog` + 自定义 `ThemeColorScrollView` (不用 `wrapInScrollView`, 以保留整行 ripple), 三个区块标题按 `ThemeColorPreferenceCategory` 的对比度规则着色. 状态区 15 行 (服务状态 / 系统设置 / 进程内实例 / 运行时长 / 事件 / 最近事件 / 窗口 / 活动窗口根节点 / 最近包名 / 最近活动 / 配置 / root 权限 / 修改安全设置 / 监听者 / 自检), 点击复制 `label: value`; 每 1 s 刷新并监听注册表迁移 (回调统一切回主线程, 检查 `isShowing`), 收集在 `Schedulers.io()` 进行且不重叠. 控制区 6 行 (`operation_dialog_item`): 启动 / 停止 / 重启 走 `AccessibilityTool`, 与抽屉开关共用 `Schedulers.single()` 避免交错; 服务未启用时跳系统设置, 已启用未绑定时报告失败 (提示用重启); 清除节点缓存; 自检 (`Schedulers.io()`); 事件监视器. 执行期间全部动作行禁用 (alpha 0.58, 副标题 "处理中"), 完成后 snack 结果, 刷新并同步抽屉条目. 设置区 2 行复用 `PrefSwitch` 绑定 `key_enable_a11y_service_with_{root_access,secure_settings}` (`PrefSwitch.setPrefKey(key, default)` 新重载, 否则 `@bool` 默认值为 true 的偏好会显示为关闭). 按钮: 关闭 / 复制 (头部 + `== 状态 ==` 各行) / 系统设置; overflow: 刷新. 生命周期: `DrawerFragment` 弱引用持有, 打开前关闭说明对话框, `onDestroy` 时 dismiss; dismiss 时移除定时器, 注册表监听者与 disposables; root 可用性只在 worker 线程探测一次并缓存. (SOURCE)
- [x] `core/accessibility/diagnostics/A11yServiceDiagnostics`: 派生状态枚举 `NOT_ENABLED` (`!hasService()`) / `MALFUNCTIONING` (`hasService && !bound`) / `CONNECTED` / `OPERATIONAL`; `collect(probes)` 每次重新读取 `registry.snapshot` 与实例 (不缓存), `serviceInfo` / `windows` / `rootInActiveWindow` 全部 `runCatching`, API < 33 回收取得的根节点; `Report.toText()` 为 UI 语言的诊断文本 (应用版本, 机型, API, 时间 + 各行). `A11yServiceInfoFormat` (Android-free): `bits` / `format(mask, prefix, nameOf)` (去 `FLAG_` / `CAPABILITY_` / `TYPE_` / `FEEDBACK_` 前缀, 未知位 `0x..`) / `formatElapsed`. `A11yServiceSelfCheck`: 以 `windows[*].root` (兜底 `rootInActiveWindow`) 为根做原生节点迭代 DFS, 上限 1000 节点 / 1500 ms, API < 33 按 `DFS.kt` 规则回收 (子节点入栈后回收父节点, 中止时回收剩余, 根各回收一次, 不碰 `fastRootInActiveWindow`), 随后轮询 `eventCount` 最多 2000 ms; 结果 `windows N, roots N, nodes N(+), walk N ms, event N ms | none, total N s`, 无事件不算失败. (SOURCE)
- [x] `AccessibilityService`: 实例级 `eventCount` / `lastEventType` / `lastEventElapsedRealtime` / `boundAtElapsedRealtime` (`onServiceConnected` 设置, 首个事件补设) / `eventCallbackCount`; 伴生 `delegateCount` 与事件观察者 API `addEventObserver(owner, observer)` / `removeEventObserver(owner)` / `eventObserverCount` (写时复制的 volatile 映射, 无锁读; 在 `registry.onEvent` 之后, 脚本回调之前逐个调用, 异常隔离). `A11yEventRecord` 只复制事件字段 (type / eventTime / receivedAt / packageName / className / windowId / contentChangeTypes), 事件对象不外泄. 未改 registry `Snapshot` (加时间戳会破坏 `transition` 的相等短路). (SOURCE)
- [x] `ui/accessibility/A11yEventMonitorDialog`: 以自身为 owner 注册观察者 (回调在服务主线程 = 应用主线程, 其它线程 post), 每 100 ms 批量 `notifyItemRangeInserted`, 最多保留 500 条 (环形丢弃, 过滤后的显示列表同步裁剪), 在底部时自动滚动 (用户上滑时不强制); 行显示与上一条所列事件的 `eventTime` 间隔 (`+48 ms  WINDOW_STATE_CHANGED`) 与 `包名 / 类名 | window N [| changes 0x..]`, 点击复制一行; 按钮 关闭 / 暂停-继续 (暂停期间丢弃并计数, 状态行显示) / 复制全部; overflow 清空 与 "仅窗口状态变化" 开关 (只显示 `TYPE_WINDOW_STATE_CHANGED`, 用于排查一次切换多次投递). (SOURCE)
- [x] JUnit4: `A11yServiceInfoFormatTest` 9 用例 (位拆分含符号位, 前缀剥离, 未知位十六进制, 空白名视为未知, 空掩码, 时长单位). (JVM)
- [x] 字符串 32 条 x 11 个 `strings.xml` (按 name 升序插入, 无全角标点; `values` 与 `values-en` 一致), changelog `feature` 1 条 (10 语言), README / CHANGELOG 重新生成. (DOCS)
- [x] 真机 (维护者; 一台 API < 33, 一台 >= 33): 说明对话框 "管理器" 与长按条目均能打开; 状态行在系统设置 / 抽屉开关切换后 1 s 内更新; 启动 / 停止 / 重启 (root 或 WSS 可用时) 的 snack 与状态迁移 (重启后实例 hash 变化, 事件计数归零), 无 root / WSS 时 "启动" 跳系统设置; 清除缓存与自检结果合理 (windows >= 1, nodes > 0); 复制文本完整; 设置开关与设置页互通; 事件监视器: 切换窗口能看到多条 `WINDOW_STATE_CHANGED` 及间隔, 暂停 / 继续 / 清空 / 仅窗口状态变化 / 复制可用; 夜间模式与主题色下可读; 关闭后 `registry.listenerCount` 回落 (状态区 "监听者" 行的 `state` 计数), 旋转与销毁 MainActivity 时无 WindowLeaked; 最后重跑 `sample/accessibility/smoke-legacy-api.js`. (DEVICE 2026-09-05, 维护者反馈 "测试管理器对话框, 全部正常"; 冒烟脚本复跑结果见 P1.1 证据)

跟进 (2026-09-05, 维护者提出的两处细节):

- [x] 高度抖动: 动态字段 (最近事件, 最近活动等) 的值长度变化会改变行高, 控制行随之上下移动, 点击时会误触. 处理: 状态行按字段固定行数 (每次刷新都可能变长短的字段固定 1 或 2 行并 `ellipsize`, 复制内容仍是全文; 很少变化的 root / 安全设置 / Shizuku 行只设 2 行上限; 配置与自检不限制), 控制行执行中的 "进行中" 标记从副标题位置移到行尾 (`usage_count`), 行高不再变化. (SOURCE; DEVICE AVD API 24, 见会话记录)
- [x] 标题行按钮与详略等级: "状态" 标题右侧为 刷新 / 显示更少 / 显示更多 三个纯图标按钮 (着色同标题, 样式 `A11yServiceManagerHeaderButton`), 4 级 `StatusLevel` NONE / BASIC / IMPORTANT / ALL (默认 BASIC = 服务状态, 系统设置, 进程内实例, 事件, 自检; IMPORTANT 加 运行时长, 最近事件, 窗口, 活动窗口根节点, 最近包名 / 活动, root / 安全设置 / Shizuku; ALL 加 配置, 监听者), NONE 时标题为 `状态 [服务状态]`; 刷新按钮取代标题溢出菜单的 "刷新" 项. "控制" 标题右侧为 显示更少 / 显示更多, 3 级 `ControlLevel` NONE / START_STOP / ALL (默认 START_STOP), NONE 时 启动 / 停止 / 重启 三个图标按钮出现在等级按钮左侧并以一条淡竖线分组. 到达边界的等级按钮 alpha 0.3 且禁用. 等级以序号存于 `key_a11y_service_manager_status_level` / `key_a11y_service_manager_control_level`. 新增字符串 `text_show_less` / `text_show_more` x 11. changelog 的管理器 `feature` 条目已补充这两点. (SOURCE; DEVICE AVD API 24, 见会话记录)

P1.7 证据:

- `JVM`: `A11yServiceInfoFormatTest` 9/9, `A11yServiceRegistryTest` 15/15; 全量 `:app:testAppDebugUnitTest` 1901 tests / 0 failures (2026-09-05).
- `ANDROID_BUILD`: `:app:assembleAppDebug` 通过, 产出 `app/build/outputs/apk/app/debug/autojs6-v6.8.0-*.apk` (2026-09-05).
- `DOCS`: 32 条字符串 x 11 文件经 `build/check-strings.py` 校验 (全部在位, 相对邻居有序, `values` 与 `values-en` 一致, 无全角标点); changelog `feature` 1 条 (10 语言, 位于抽屉屏幕取色条目之后), README / CHANGELOG 已重新生成.
- `DEVICE`: 维护者 2026-09-05 反馈管理器对话框 "全部正常" (上方最后一条); 同日确认 root 设备显式关闭后不再自动启动 (P1.2 提前落地项的真机证据).

P1 验收: JVM 单元测试 (registry / controller / event bus / window policy / node lifecycle) 全部通过; `sample/accessibility/smoke-legacy-api.js` 真机通过; 手动验证: 抽屉开关, 悬浮菜单状态文案, Tile, 布局分析器, 脚本内 `auto()` / `auto.waitFor()` / `auto.stateListener` / `auto.registerEvent` 行为与重构前一致; changelog `improvement` 条目已写入.

P1 验收状态 (2026-09-05): JVM 五组测试均在 (`A11yServiceRegistryTest` / `A11yServiceControllerTest` / `A11yEventBusTest` / `WindowRootResolverTest` / `NodeLifecycleTest`), 全量通过; 冒烟脚本 (53 项) 本机 adb 在 AVD API 24 与 Xiaomi API 35 通过, 维护者最近一次 8 设备复跑为 48 项版本 (P1.3 关闭时); changelog 条目齐备. **待维护者**: 以 P1.6 后的构建重跑 53 项冒烟脚本 (含 root / WSS / Shizuku 的重启段) 并做上述手动验证清单, 通过后 P1 关闭.

---

## P2: UiSelector / UiObject / UiObjectCollection 重构

目标: 三个类的 public JS 表面不变, 内部由 "注册表 + 组合" 替代 "手工表 + 反射", 巨型文件拆分, 过滤器去重, 查询获得统一的轮询策略与线程安全保证.

### P2.1 `UiNode` 抽象与夹具 (完成 2026-09-05)

- [x] 定义 Android-free 接口 `UiNode` (只读属性: text / desc / content / id / className / packageName / bounds / 布尔属性集合 / 集合信息 / depth / indexInParent / childCount / `child(i)` / `parent()`; 动作: `performAction(id, args)`), `UiObject` 实现它; 过滤器 (`Filter.filter(node: UiNode)`), 搜索算法, `Compass`, `Stabilizer` 一律面向 `UiNode`. 落地: `core/automator/UiNode.kt`, 自类型泛型 `UiNode<N : UiNode<N>>` (`parent()` / `child(i)` 返回 `N?`, 因此以 `UiObject` 为根的搜索与罗盘导航静态地得到 `UiObject`, 无需强制转换); 成员一律采用 `UiObject` 面向脚本的方法名与语义 (`text()` 无文本为空串, `id()` 为完整资源名, `content()` 为 desc 或 text, 边界为 `left()` / `top()` / `right()` / `bottom()` / `width()` / `height()` / `centerX()` / `centerY()` 八个标量而非平台 `Rect`, `hasAction` 的 id / 名称匹配规则抽为 Android-free 的 `UiNodeActions`), 另含 `actionNames()` / `performAction(id, args)` 与生命周期用的 `recycle()`. `UiObject : UiNode<UiObject>` 只需给既有方法加 `override`, 并新增 4 个此前只有 compat 形式的布尔方法 `contentInvalid()` / `contextClickable()` / `multiLine()` / `dismissable()` (对脚本为新增 API, 记入 changelog `feature`, d.ts 见 P6). 过滤器 (`Filter` / `KeyGetter` / `KeysGetter` / `BooleanFilter.BooleanSupplier` / 各 `*Property` 接口, 共 36 个文件) 全部改为 `UiNode<*>`, 取值改走接口方法 (`text()` / `desc()` / `className()` / `packageName()` / `id()` 与原 compat 读法值相同); `BoundsFilter` 不再依赖 `Rect`; `SearchAlgorithm.search` 改为 `<N : UiNode<N>> search(root: N, ...): ArrayList<N>`, DFS / BFS 经 `lifecycle.release { node.recycle() }` 释放. 偏离 / 未做: 边界不提供 Android-free 的聚合值对象 (`UiObject.bounds()` 仍返回 `Rect` 供脚本使用, 同名冲突, 标量足够 P2.3 / P3 使用); `Stabilizer` 尚不存在 (P3 建立时直接面向 `UiNode`); 依赖屏幕尺寸换算的过滤器 (`BoundsFilter` / `Metrics*` / `ToleranceFilter` / `DoubleMinFilter`) 虽已面向 `UiNode`, 但仍经 `DisplayUtils` 读设备尺寸, JVM 测试留待 P2.3. 顺带修复 `BooleanFilter.CONTEXT_CLICKABLE` 的 `toString` 误为 `checkable` (changelog `fix`). (SOURCE; JVM: `FilterTest` 11, `CompassTest` 9, `SearchAlgorithmTest` 9)
- [x] `FakeUiNode` (P0.1) 实现 `UiNode`, 成为过滤器 / 搜索 / pick / flow 单元测试的统一夹具. 落地: `app/src/test/.../core/automator/FakeUiNode.kt` 取代 P0.1 的 `FakeUiObject` (后者继承 `UiObject`, 只能覆盖搜索触及的成员): 纯 `UiNode<FakeUiNode>` 实现, 不涉及任何 Android 类, 每个属性都是带中性默认值的字段 (`child("a") { text = "Login"; clickable = true; bounds(0, 0, 100, 50); actions = listOf("ACTION_CLICK") }`), 记录 `performedActions` 与 `isRecycled`. 主源码集里无引用的随机夹具 `core/automator/test/TestUiObject.kt` 已删除. 新增 `FilterTest` (文本 / 描述 / 内容 / id / 类名 / 包名 / 布尔 / 整数 / 动作过滤器与 `Selector` 组合, 含 JS 正则字面量字符串形式), `CompassTest` 取代 `UiObjectCompassTest` (同一组用例改在 `Compass.navigate` 上运行, 另加解析步骤直走, 语法错误与 `isCompass`). (JVM)

P2.1 证据:

- `JVM`: `FilterTest` 11 / `CompassTest` 9 / `SearchAlgorithmTest` 9 / `CompassParserTest` 8 全部通过; 全量 `:app:testAppDebugUnitTest` 1982 / 0 失败 (5 skipped; 较 P1.6 净增 14).
- `ANDROID_BUILD`: `:app:assembleAppDebug` 通过 (2026-09-05).
- `DOCS`: changelog `feature` 1 + `improvement` 1 + `fix` 1 (10 语言), README / CHANGELOG 重新生成; 无新增字符串; 架构文档 1.1 / 1.4 / 1.8 / 2.1 / 2.2 更新.
- `DEVICE` (本机 adb, P2.1 APK): 冒烟脚本 AVD API 24 `53/53`, Xiaomi 23046RP50C (API 35) `42/42`; 探针 `build/smoke/uinode-probe.js` (新增 4 个布尔方法与 compat 访问器一致, `selector().filter(fn)` 回调仍收到可调用 `UiObject` 方法的节点且计数与 `clickable()` 相同, `compass('p')` / `'%'` / `'k'` 与 `parent()` / 自身 / 可点击性一致, `hasAction('CLICK')` / `hasAction(16)` 与 `actionNames()` 一致, 非法罗盘仍抛本地化 `IllegalArgumentException`, `contextClickable()` 的字符串表示已正确, BFS / DFS 计数一致) 在 API 24 与 API 35 均 `16/16`.

### P2.2 `SelectorRegistry`: 一处描述, 多处派生 (完成 2026-09-05)

- [x] 用声明式表 `SelectorRegistry` 描述每个选择器属性: 名称, 别名 (含 `@Deprecated` 的 `xMatches`), 参数类型集合 (string / regex / bool / int / double / double-range / bool+tolerance / app / action / filter), 对应的 `UiNode` 取值器, 过滤器工厂. 落地: `core/automator/selector/{SelectorEntry,SelectorRegistry}.kt` (Android-free). `SelectorEntry(name, overloads, deprecatedFor, getter)`, 每个 `SelectorOverload` 是参数列表 (`SelectorParam(name, type, range)`) 加一个作用于 `UiSelector` 的闭包; 参数类型为枚举 `SelectorArgType` (STRING / REGEX / BOOLEAN / INT / DOUBLE / APP / FILTER / ACTIONS), 各自定义严格与宽松 (`lenient`, 供集合选择器的数组元素使用: 字符串可作数字 / 布尔) 两种转换, 重载按 "参数个数相同且逐个可转换" 解析 (`ACTIONS` 为可变参数). 表以小型 DSL 写成 (`stringFamily` 生成 6 个字符串族 x 10 个变体, `metrics()` 生成 8 个度量 x 6 种形态, `booleanProperty` / `intProperty`), 共 125 个属性 / 192 个重载, 与重构前 `UiSelector` 的 public builder 集合一一对应 (含 `algorithm` / `action` / `filter` / `currentApp` / `packageName(App)`). 取值器 (`getter`) 只给单值属性 (`bounds` / `filter` / `action` 之类为 `null`), 供 P2.3 / P5.3 (`auto.explain`, 字符串选择器) 使用. (SOURCE; JVM: `SelectorRegistryTest` 12 用例, 含表大小断言 125 / 192)
- [x] `UiSelector` 的约 200 个 builder 方法改为一行委托 `registry.apply(this, "text", args)` (方法本身保留, 以维持反射注册与 d.ts 生成). 落地: builder 方法不再手写, 而是生成为抽象基类 `core/accessibility/UiSelectorBuilders.kt` (192 个方法, 每个重载一个, 一律 `= applyEntry("text", s)`; 12 个 `xMatches` 重载带 `@Deprecated(ReplaceWith("xMatch(...)"))`, `screenCenterX` / `screenCoverage` 的容差与下限带 `@FloatRange`), `UiSelector : UiSelectorBuilders` 只实现 `applyEntry` (未注册的名称抛 `IllegalStateException`, 参数不合抛 `IllegalArgumentException`) 并保留 `find*` / `pickup` / 动作等非 builder 成员; `UiSelector.kt` 由 1231 行减为 385 行. 脚本 (Rhino), d.ts 生成器 (P6) 与 Kotlin 调用方看到的 public 签名与从前相同 (`UiSelectorBuildersTest` 以反射逐个核对). (SOURCE)
- [x] 按 D9 评估并实施 builder 方法的自动生成: 候选方案 (a) KSP 处理器读取 registry 注解 / 常量表, 生成 `UiSelectorGenerated.kt` (builder 方法) 与 `selector-signatures.json` (供 d.ts 生成器与文档使用); (b) Gradle 任务 + Kotlin 模板生成源码并提交到仓库 (可读, 可 diff, 无编译期插件依赖); (c) 保持手写但用单元测试断言 registry 与手写方法集合一致 (兜底). 评估维度: 构建时间, IDE 索引与跳转, `@Deprecated` 别名与 KDoc 的表达, 与 `android-dts-generator` 的衔接. 结论与选择记录到 `docs/dev/accessibility-architecture.md`. 结论 (详见架构文档 3.1): 取 (b) 的变体, 生成器 `SelectorBuilderGenerator` 放在测试源码集 (不进入 APK, 不需要 Gradle 任务或插件), 生成物提交到仓库, `UiSelectorBuildersTest` 每次运行都重新渲染并与提交文件逐字比较 (不一致即失败, 并把新渲染写到 `app/build/generated-selector-builders/` 供复制), 另以反射核对每个注册表重载在 `UiSelector` 上恰有一个签名一致的 public 方法. `selector-signatures.json` 不再需要: d.ts 签名 (P6) 可由同一注册表派生. (SOURCE; JVM: `UiSelectorBuildersTest` 3 用例)
- [x] `Picker.Builder.checkSelectorMap` (`UiSelector.kt:1041-1185`) 与 `doubleTypes` 等 6 张手工表由 `SelectorMapParser` + registry 替代, 删除反射调用; 行为差异用测试锁定 (map 值为 `List` 时的各分支, `filter` / `action` 键, 未知键抛 `IllegalMapSelectorException`). 落地: `core/automator/selector/SelectorMapParser.kt` (Android-free; 脚本函数经注入的 `callables` 适配器转为 `BooleanFilter.BooleanSupplier`, 返回值按 JS 真值规则转换), 规则: 非字符串键忽略; 值为 `null` 取无参重载 (`action: null` 跳过); 值为数组则展开为参数并宽松转换, 其余值作单个参数严格转换; 未知键或标量不合抛 `SelectorMapException.NoSuchProperty`, 数组不合抛 `SelectorMapException.IllegalValue`, `Picker.Builder` 把两者映射为原有的本地化 `IllegalArgumentException` (`error_no_such_selector_method`) 与 `IllegalMapSelectorException`. 行为变化 (均记入 changelog): 数组中的正则现在被接受 (`{text: [/re/]}` 原先因 `getMethod(key, String)` 抛类型不匹配), 数值统一四舍五入 (原先 `toInt()` 截断), `ConsString` 作为字符串接受. (SOURCE; JVM: `SelectorMapParserTest` 8 用例)
- [x] `augment/selector/Selector.kt:45-95` 的 "反射全部 public 方法" 改为 "反射 registry 声明的方法 + 显式白名单 (`find*`, `exists`, `untilFind*`, `algorithm`, `filter`, `action`, `performAction` 等)", 并保留黑名单 (`plus`, `append`); 用测试断言注册的全局函数集合与重构前完全一致 (以快照文件固化). 落地: `runtime/api/augment/selector/SelectorGlobals.kt` = 注册表全部属性 (含 `algorithm` / `filter` / `action`) + 8 个查询方法 (`find` / `findOnce` / `findOne` / `untilFind` / `untilFindOne` / `waitFor` / `exists` / `performAction`) + `UiObjectActions` 的 43 个动作方法 (反射取自接口, 过滤合成与 `access$...$jd` 访问器), 黑名单 `plus` / `append` / `toStringReadable`; 共 176 个名字. 注册表属性的全局函数直接走 `SelectorRegistry.apply` (脚本函数转为 `BooleanSupplier`), 白名单方法仍按反射解析 (`SelectorMethodResolver`). 与重构前的差异是有意的: 重构前导出了 `internal` 成员的混淆名与 `toStringReadable` 等无用名字 (`plus` / `append` 原已在黑名单), 现已不导出; 全局 `filter(fn)` 原先因反射解析器不能把脚本函数当 `BooleanSupplier` 而一律失败, 现在可用 (changelog `fix`). 快照 `app/src/test/resources/selector-global-functions.txt` (176 行) 由 `SelectorGlobalsTest` 守住, 变化即脚本表面的变化. (SOURCE; JVM: `SelectorGlobalsTest` 3 用例)
- [x] `SelectorMethodResolver` 与 `UiObject.Invoker/Interceptor` 的数值强制转换合并为一个 `JsArgumentCoercion` 工具, 三处共用. 落地: `rhino/JsArgumentCoercion.kt` (`coerce(parameterType, value)` / `coerceNumber` / `toInt` / `toLong`, 整型一律四舍五入, `Float` 缩窄, 其余原样); `SelectorMethodResolver.convertArgument`, `UiObject.Interceptor` (原先截断) 与注册表的 `INT` 类型共用. 注意 Rhino 直接调用 Java 方法 (`selector().depth(1.6)`) 仍按引擎规则截断, 不在本项范围. (SOURCE; JVM: `JsArgumentCoercionTest` 3 用例)

P2.2 证据:

- `JVM`: `SelectorRegistryTest` 12 / `SelectorMapParserTest` 8 / `UiSelectorBuildersTest` 3 / `SelectorGlobalsTest` 3 / `JsArgumentCoercionTest` 3 / `SelectorMethodResolverTest` 8 全部通过; 全量 `:app:testAppDebugUnitTest` 2011 / 0 失败 (5 skipped; 较 P2.1 净增 29).
- `ANDROID_BUILD`: `:app:assembleAppDebug` 通过 (2026-09-05).
- `DOCS`: changelog `improvement` 1 + `fix` 1 (10 语言), README / CHANGELOG 重新生成; 无新增字符串; 架构文档 1.1 / 1.4 / 1.8 / 2.1 / 2.2 更新, 3.1 记录 D9 评估与选择.
- `DEVICE` (本机 adb, P2.2 APK): 冒烟脚本 AVD API 24 `53/53`, Xiaomi 23046RP50C (API 35) `42/42`; P2.1 探针 `uinode-probe.js` API 24 `16/16`; 新探针 `build/smoke/selector-probe.js` (生成的 builder 的方法形式与字符串表示, 弃用别名, 全局函数的四舍五入 / `textMatches` / `filter(fn)` (含真值转换) / `hasChildren` 划分 / `screenCenterX` 三种形态 / `algorithm('BFS')` / `action` 名称与数值 / 不合参数报错, `toStringReadable` / `append` 不再是全局函数, 新布尔属性是全局函数; 集合选择器的标量 / `null` / 空数组 / 数组展开 / 宽松元素 / 数组内正则 / 四舍五入 / 函数值 / `action` 数组与 `null` / 拼接字符串 / 选择器列表内的对象, 未知键与不合值的三种错误都点名键名, `existsAll` 对象形式) 在 API 24 与 API 35 均 `33/33` (计数类断言带重试, 因活动界面的节点数会在两次查找之间漂移).

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

- [x] `compass` DSL 迁出为 `core/automator/pick/Compass.kt` (预编译正则, `CompassParser` 见 P0.1), `UiObject.compass()` 委托. 提前在 P2.1 落地 (统一夹具需要罗盘导航面向 `UiNode`): `Compass.navigate(start, compass | steps)` 泛型于 `UiNode<N>`, `PASS_ON` / `isCompass` 随之迁入; `UiObject.compass()` 只保留本地化错误信息 (`ensureCompass`, 解析失败的 `error_invalid_rest_compass`) 后委托. (SOURCE; JVM: `CompassTest`)
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

### Q8 (P1.2 后续): 强制停止后是否自动重新启用无障碍服务

背景 (2026-09-05 强制停止实验). 用户强制停止 AutoJs6 时, 系统会把本服务移出 `enabled_accessibility_services` (`AccessibilityManagerService` 的 `PackageMonitor.onHandleForceStop`); 冷启动后服务为 "未启用" 而非 "故障", 现行为与用户显式关闭同等对待, 不自动重新启用 (`restoreIfMalfunctioning` 只处理 "已启用但未绑定").

建议: 保持现行为. 强制停止是用户主动的 "全部停下" 操作, 且系统同样撤销了服务, 自动恢复会绕过用户意图; 需要自动化的场景可用脚本 / 悬浮菜单 / 磁贴的显式入口 (它们现在都会自动启动并接续功能). 若日后要做, 只能以 "进程上次是被强制停止且服务此前启用" 的持久化标记为条件, 而非 `isMalfunctioning()`.

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
- 维护者提出: 抽屉 "无障碍服务" 对话框的 "管理器" 按钮尚未实现 (`DrawerFragment.kt:210` 目前弹 "开发中"), 希望做成对话框形式的服务管理器 (信息展示 / 调试 / 测试 / 开关 / 控制), 兼作本 Roadmap 各阶段的真机调试面板, 文案与 UI 风格须与现有对话框一致. 维护者选定范围 "标准版 + 事件监视器" -> D14.
- 落地 P1.7 (D14): `ui/accessibility/{A11yServiceManagerDialog,A11yEventMonitorDialog}.kt`, `core/accessibility/diagnostics/{A11yServiceDiagnostics,A11yServiceSelfCheck,A11yServiceInfoFormat}.kt`, `core/accessibility/A11yEventRecord.kt`, 5 个布局, `AccessibilityService` 事件计数与伴生级事件观察者, `PrefSwitch.setPrefKey(key, default)`, `DrawerFragment` 接入; 字符串 32 条 x 11 文件; changelog `feature` 1 条. 证据: 全量 JVM 1901 / 0, `assembleAppDebug` 通过 (见 P1.7 证据). 待维护者真机验证 (P1.7 最后一条清单) 并顺带回填 P1.1 / P1.2 的复跑结果 (冒烟脚本 38/38 或 32/32, root 显式关闭不再自动恢复, XQ-DQ72 环境快照) — 管理器的 "复制" 文本可直接作为回填内容.
- 维护者反馈 (同日): root 设备显式关闭后不再自动启动 (已修复); 管理器对话框全部正常 (P1.7 `DEVICE` 关闭); 冒烟脚本复跑两份输出: XQ-DQ72 31/43, AVD API 26 40/43. 归因: XQ-DQ72 为保护模式开启 (按设计, 脚本以 AutoJs6 自身为目标时选择器一律为空; 待维护者确认设置项), AVD API 26 为重绑后活动窗口未知 (`rootInActiveWindow` 持续 `null`, 平台行为, 兜底留给 P1.4); 详见 P1.1 `DEVICE` 证据. 顺带修复 `AccessibilityBridge.windowRoots()` 返回 `[null]` (`auto.windowRoots` 得到 `UiObject(null)`), changelog `fix` 1 条. 冒烟脚本: 环境快照增加 `guardMode=` 与每个窗口的 类型 / 包名 / active / focused, 新增前置检查 `guard mode is off` 与 `auto.rootInActiveWindow is available`, 重启段改为等待真实根节点并在失败时制造一次窗口变化以验证归因, 事件间隔为负时不再显示 `+-`; 现在可重启设备 45 项, 其余 34 项.
- 维护者反馈 (同日, 45 项版本脚本): AVD API 26 普通 / Root / WSS 均全部通过; XQ-DQ72 关闭保护模式后 普通 / Root / WSS 均全部通过. P1.1 与 P1.7 的真机证据齐备.
- 落地 P1.2: `core/accessibility/control/{A11yServiceController,ServiceStartStrategy,EnabledServicesStores,EnabledA11yServices}.kt` 与 3 个测试类 (29 用例); `AccessibilityTool` 改为薄门面 (共享控制器在其伴生对象, 共享工具在 `AbstractAutoJs.accessibilityTool`); 4 个 "确保 / 等待" 变体合并为 `ensure`; D5 / D8 落实; 提前落地的 4 项修复迁入并补测; 决定不再写 `accessibility_enabled`; 修复文案颠倒, `auto.services` 空数组, 抽屉开关对故障服务的显示. 偏离两处 (设置页非策略; `AccessibilityTool` 不弃用) 已写入条目. changelog `fix` 4 条 + `improvement` 1 条. 证据见 P1.2 证据.
- 维护者反馈 (同日): AVD API 24 / 25 冒烟脚本全部通过 (P1.2 `DEVICE` 证据); 强制停止实验结果与解读见 P1.2 `DEVICE` 证据, 派生待决 Q8 (附录 C). 维护者提出两处细节: (A) 悬浮按钮的窗口文字与暗色主题跟随系统而非宿主应用; (B) 服务自动启动后触发点应立即接续功能 (布局范围分析需触发两次). 两项均已落地, 见 P1.2 "跟进" 两条: `ContextUtils.withHostConfiguration()`, `AccessibilityTool.ensureServiceAsync` / `explainFailure`, `LayoutInspector.captureCurrentWindowWithRetry`, 字符串 `error_active_window_root_unavailable` x 11; changelog `fix` 1 条 + `improvement` 1 条. 待维护者真机确认: 悬浮菜单 / 磁贴 / `app.startActivity` 布局分析广播在服务关闭 + WSS/Root 可用时一次触发即出结果; 应用切换语言 / 夜间模式后悬浮菜单的对话框与布局分析器随之变化.
- 落地 P1.3: `core/accessibility/event/{A11yEventBus,A11yEvent,AccessibilityDelegate}.kt` 与 `A11yEventBusTest` (16 用例); `AccessibilityService` 伴生对象持有 `eventBus` / `keyBus` / `gestureBus`, 脚本回调 / delegate / 观察者 / 按键观察者与拦截器全部成为订阅项并在服务重启后存活; `auto.registerEvent` 第三参数 `options` (`distinct` / `within` / `debounce`); `AccessibilityEventWrapper.source` 惰性解析 + `seal()`; `fastRootInActiveWindow` 无条件更新并前移. changelog `fix` 1 + `feature` 1 + `improvement` 1. 偏离三处已写入条目 (嵌套 `Subscription`; 按键 / 手势为独立总线实例; `distinct` 为键函数). 证据见 P1.3 证据.
- 维护者反馈 (同日): 48 项脚本在 API >= 31 全部通过, API 24 / 25 / 26 / 28 只有 1b 节两项失败; 一次触发接续与悬浮 UI 跟随宿主设置均 "正常"; `keepAlive` 脚本确认事件回调跨服务重启存活. 1b 失败归因为 API <= 28 的 5 s 应用切换锁 (见 P1.3 `DEVICE` 证据), 脚本改为等待包名前置; 本机 adb 在 8 台设备 (API 24 / 25 / 26 / 28 / 31 / 33 / 35 / 36) 复跑全部 48/48. P1.3 关闭.
- 排查过程中的一个陷阱, 供以后写探针脚本参考: AutoJs6 的 Rhino 把循环体内直接声明的 `const` 当作只赋值一次 (第二轮起表达式照常求值但赋值被忽略, `let` 正常; 已用 `for (let i...) { const v = i * 10 }` 得到 `0, 0, 0` 验证), 因此逐秒轮询 `auto.rootInActiveWindow` 的探针若在循环体里用 `const` 接收结果, 会看到 "活动窗口永远停在桌面" 的假象; 把每轮读取放进函数即可. 冒烟脚本本身不受影响 (其 `const` 均在函数或回调作用域内). 是否写入用户文档由维护者决定 (P6).
- 维护者提出 (同日, 待办, 均在 P1.4 之前处理): (a) 无人值守启动方式增加 Shizuku (与 WSS / Root 并列); (b) 服务管理器对话框因动态字段 (如 "最近事件") 长度变化而整体高度抖动, 在 "控制" 区点击时会误触; (c) "状态" 标题右侧增加 刷新 / 减号 / 加号 三个纯图标按钮 (着色同标题), 状态字段分 4 级 (全部 / 较重要 / 最基本 / 不显示), 默认 "最基本", 逐级切换并记住偏好, "不显示" 时标题后附 `[服务状态]` (如 `状态 [可操作]`), 刷新按钮取代菜单的 "刷新" 项, 到达边界的按钮视觉变淡; "控制" 标题右侧增加 减号 / 加号, 3 级 (全部按钮 / 仅三个启停按钮 / 不显示), 默认 "仅三个启停按钮", 记住偏好, "不显示" 时三个启停按钮移到标题行 (与两个等级按钮之间加一条不显眼的竖线分组).
- 落地 (a) (b) (c): (a) `ShizukuEnabledServicesStore` + 策略 `"shizuku"` (排在 root / 安全设置之后), 偏好 `key_enable_a11y_service_with_shizuku` (设置页 + 管理器), 状态区 `Shizuku 权限` 行, 冒烟脚本纳入 Shizuku; (b) 状态行按字段固定行数, "进行中" 标记移到行尾; (c) 状态 / 控制标题行的图标按钮与持久化的详略等级 (详见 P1.2 / P1.7 跟进). 字符串 5 条 x 11, changelog `feature` 1 条 + 管理器条目补充 (10 语言). 三项分别提交 (Shizuku 为 P1.2 跟进, (b) (c) 合为 P1.7 跟进). 证据: `JVM` 全量 1946/0 (5 skipped); `ANDROID_BUILD` `assembleAppDebug` 通过; `DEVICE` AVD API 24 (x86 APK): 状态 4 级与控制 3 级逐级切换, 边界按钮变淡, NONE 时标题 `State [Operational]` 与标题行的 启动 / 停止 / 重启 + 分隔线均如预期, 从标题行触发重启成功 (snack "Accessibility service restarted") 且执行期间对话框高度不变. Shizuku 策略本机没有装有 Shizuku 的设备, 待维护者真机验证: 安装并授权 Shizuku 后关闭 root / WSS 两个开关, 在管理器点 "启动" (状态区 `Shizuku 权限` 行应显示 "可用", 首次可能等待用户服务绑定数秒). 顺带修复 `A11yServiceControllerTest.restartWaitsForTheUnbindBeforeEnablingAgain` 的偶发失败: 假系统在后台线程先 `onDisposed` 再换 `nextInstance`, 等待解绑的控制器会抢先把旧实例重新绑上; 改为先换实例再报告解绑并把字段设为 volatile, 重跑 5 次稳定.
- 维护者反馈 (同日): Shizuku 策略真机验证 "功能逻辑正常" (P1.2 跟进项 DEVICE 证据). 维护者要求: 单次会话可完成多个 Roadmap 小节 (除非单个小节已足够繁杂), 兼顾质量与进度.
- 落地 P1.4: `core/accessibility/window/{WindowRootPolicy,WindowRootResolver,WindowRootProvider}.kt`, `AccessibilityBridge` / `AccessibilityBridgeImpl` 转 Kotlin (FQN 与签名不变), `UiSelector.findImpl` / `SimpleActionAutomator.performAction` 去掉 `filterNotNull()`, `LayoutInspector` 经 provider, `auto.setFlags(['appWindowsFallback'])` (默认关闭, 依据: AVD API 26 上 12 轮重启探针未能复现持续 `null` 的活动窗口根节点, 详见 P1.4 第 3 条). 冒烟脚本第 1 节新增 5 项 (共 53 项). changelog `feature` 1 + `improvement` 1 (10 语言). 证据: `JVM` `WindowRootResolverTest` 13/13, 全量见下条; `ANDROID_BUILD` `assembleAppDebug` 通过 (22:03); `DEVICE` (本机 adb, 新 APK): AVD API 24 `53/53 checks passed`; Xiaomi 23046RP50C (API 35) `42/42` (该机三种无人值守方式的偏好均关闭, 重启段按设计跳过; 首次运行时服务因重装被系统关闭, `auto()` 报 "无障碍服务未启用" 属预期, 经 adb 写入启用列表后复跑). 未在 P1.4 处理: 布局分析器以外的诊断 (`A11yServiceDiagnostics` / `A11yServiceSelfCheck`) 仍直接读 `rootInActiveWindow`, 因为它们展示的是服务的原始状态.
- 落地 P1.5: `core/accessibility/node/{NodeRecyclePolicy,NodeLifecycle,NodeLifecycles,NodeRegistry}.kt`, 9 处内联 `recycle()` 改为经 `NodeLifecycle` (含 P1.7 诊断的两处), `SearchAlgorithm.search` 增加可注入的 `lifecycle` 参数, `AccessibilityNodeInfoAllocator` 以 lifecycle + 同一性登记表重写并修复 compat 重载的无限递归. 偏离: 分配器不接入 `ScriptRuntime` (原因与探针证据见 P1.5 第 2 条: API < 33 的进程级节点池会让退出时的批量回收命中已被复用的实例). changelog `improvement` 1 + `fix` 1 (10 语言). 证据: `JVM` `NodeLifecycleTest` 5 / `NodeRegistryTest` 2 / `SearchAlgorithmTest` 9, 全量 1968 / 0 (含临时接入运行时的版本); `ANDROID_BUILD` 通过; `DEVICE` AVD API 24 冒烟 53/53, 分配器探针 API 24 / 35 无递归. 顺带记录: AVD API 24 重装后无障碍管理器不再绑定服务的现象与 `settings put` 空值报错 (架构文档 1.7).
- 落地 P1.6: `core/accessibility/A11yScreenshotter.kt` (API 30+; `take` 单次 + `takeWithRetry` 限频重试至多 60 x 50 ms), `SimpleActionAutomator.captureScreen` 改为消费; `NodeBridgeProtocol` (3 处) / `NodeBridgeProviderRegistry` (1 处) / `PythonHostCapabilityBroker` (1 处) 的可操作判定改为 `AccessibilityService.registry.isOperational` (原条目写 3 处, 实为 5 处); 架构文档 "现状" 章节改为 P1 完成后的形态 (1.1 表加截图行, 2.1 分层图标注 P1 已落地组件, 2.2 新增 P1.6 行). 顺带改进两处并记入 changelog `improvement` (10 语言): 截图硬件缓冲总会 `close()` (原先只回收包装位图); 限频重试有上限, 超限以 `null` 兑现 (原先永不兑现). 证据: `JVM` 全量 1968 / 0; `ANDROID_BUILD` `assembleAppDebug` 通过; `DEVICE` 截图探针 Xiaomi API 35 三次成功 (138 / 286 / 68 ms), 同机冒烟 42/42; 详见 P1.6 证据. P1 全部子项 (P1.1 ~ P1.7) 代码完成.
- 落地 P2.1: `core/automator/UiNode.kt` (自类型泛型接口, 成员沿用 `UiObject` 的脚本方法名), `core/automator/UiNodeActions.kt` (动作符号名与 `hasAction` 匹配规则), `core/automator/pick/Compass.kt` (罗盘导航, P2.5 首条提前落地), `UiObject : UiNode<UiObject>` (新增 `contentInvalid()` / `contextClickable()` / `multiLine()` / `dismissable()`), 36 个过滤器文件与 DFS / BFS / `UiSelector.filter` 改为面向 `UiNode`, 删除无引用的 `core/automator/test/TestUiObject.kt`; 测试夹具 `FakeUiNode` 取代 `FakeUiObject`, 新增 `FilterTest`, `CompassTest` 取代 `UiObjectCompassTest`. 设计取舍: 以自类型泛型换取无强制转换的静态类型 (过滤器签名为 `UiNode<*>`); 不引入 Android-free 的边界值对象 (与脚本可见的 `bounds(): Rect` 同名冲突, 八个标量已够用); 屏幕相关过滤器的 JVM 测试留待 P2.3. changelog `feature` 1 + `improvement` 1 + `fix` 1 (10 语言). 证据: `JVM` 全量 1982 / 0; `ANDROID_BUILD` 通过; `DEVICE` 冒烟 AVD API 24 53/53, Xiaomi API 35 42/42, `uinode-probe.js` 两机 16/16; 详见 P2.1 证据.
- 落地 P2.2: `core/automator/selector/{SelectorEntry,SelectorRegistry,SelectorMapParser}.kt` (声明式表 125 属性 / 192 重载, 参数类型与宽松转换, 集合选择器解析), 生成的 `core/accessibility/UiSelectorBuilders.kt` (192 个 builder, `UiSelector` 继承并只实现 `applyEntry`, 文件由 1231 行减为 385 行), `runtime/api/augment/selector/SelectorGlobals.kt` (全局函数 = 注册表 + 8 个查询方法 + 43 个动作方法 - 黑名单, 共 176 个, 快照 `src/test/resources/selector-global-functions.txt`), `rhino/JsArgumentCoercion.kt`; 测试源码集内的生成器 `SelectorBuilderGenerator` 与漂移测试. D9 评估结论记录在架构文档 3.1 (生成器在测试源码集 + 提交生成物 + 测试守漂移; 不用 KSP / Gradle 任务). 行为变化: 全局 `filter(fn)` 可用, 集合选择器接受数组内正则与 `ConsString`, 数值统一四舍五入, 无用的全局名字不再导出; changelog `improvement` 1 + `fix` 1 (10 语言). 陷阱两则: Kotlin 以 `ACC_BRIDGE` 存根实现接口默认方法 (`UiSelector.click()` 等), 反射核对时不能一概排除桥接方法; `UiObjectActions::class.java.methods` 含 `access$...$jd` 合成访问器, 需过滤. 证据: `JVM` 全量 2011 / 0; `ANDROID_BUILD` 通过; `DEVICE` 冒烟 AVD API 24 53/53, Xiaomi API 35 42/42, `selector-probe.js` 两机 33/33; 详见 P2.2 证据.
- 下次起点: P1 验收仍待维护者 (以最新构建重跑 53 项冒烟脚本, 含 root / WSS / Shizuku 设备的重启段; 手动验证清单见 P1 验收段), 通过后 P1 关闭. 代码线索: P2.3 过滤器去重 (`StringPropertyFilter(property, matcher)` 与 `MetricsFilter(property, comparison)` 泛化, 注册表的过滤器工厂随之改写但表本身不变, `UiSelectorBuildersTest` 会确认 builder 无需重生成; `BooleanFilter.cache` 与 `Selector.filters` 的线程安全; 屏幕相关过滤器改为注入尺寸以便 JVM 求值), 其后 P2.4 查询与轮询. P2 期间沿用 P1 的节奏: 每个子项独立提交, 完成后在真机重跑冒烟脚本 (XQ-DQ72 需先关闭保护模式或改在其它应用前置时运行; 本机 adb 可用 `build/smoke/run-smoke.js` 一类的包装脚本把控制台输出镜像到 `/sdcard/smoke-result.txt`).
