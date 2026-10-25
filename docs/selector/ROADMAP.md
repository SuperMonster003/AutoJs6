# AutoJs6 选择器机制优化与 pickup / detect 重构 Roadmap (主题 selector)

- 看板: `D:/idea-projects/.claude/boards/selector.md` (当前批次, 子会话, 待决事项以看板为准).
- 前序: `docs/dev/accessibility-automation-roadmap.md` (2026-09-04 至 09-07 的 a11y 重构, P0-P6.4 已完成, 其固定决策 D1-D16 本 Roadmap 全部沿用, 除下文 D2 明确放宽的部分) 与 `docs/dev/accessibility-architecture.md` (分层, 查找与拾取调用链 1.4, 基准数据 1.9).
- 立项: 2026-10-25, 立项会话 local_888c36fd-e681-4cd1-964a-9a8f801ff630 (Fable 5.1), 三轮 11 题选择题 (附录 B).
- 证据等级与勾选格式沿用前序 Roadmap 附录 D: `SOURCE` / `JVM` / `ANDROID_BUILD` / `DEVICE` / `DOCS`, 勾选时在条目后追加证据与提交 hash.

## 1. 目标与范围

目标:

1. 选择器搜索的效率: 未命中 (轮询中最常见的情形) 与命中的耗时, 轮询期间的 CPU, 以不改变 `find()` 结果 (顺序与数量) 为前提.
2. 选择器命中的准确率: 不再把过期节点交给脚本; 文本因空白 / 全角 / 大小写差异未命中有规范化手段; 多个同名候选可按偏好取最优; 失败点可诊断.
3. `pickup` / `detect` 的实现: 四层 (Picker.Builder / Detector.Source / Invoker.Interceptor / PickResultType) 加新层一份校验副本 (SelectorChecks / SelectorForms / SelectorPatterns 约 1180 行), 改为一个 Android-free 的 PickRequest 模型: 单一归一化器, 显式的节点方法注册表, 一个执行器; 先判定再抛, JVM 可全量测试.

范围内 (宿主 `AutoJs6`):

- `app/src/main/java/org/autojs/autojs/core/automator/pick/**`, `core/automator/{filter,search,query,selector,diagnostics}/**`, `core/accessibility/{UiSelector,UiSelectorQuery,UiSelectorBuilders}.kt`, `core/automator/{UiObject,UiObjectCollection}.kt`, `core/accessibility/event/A11yEventPulse.kt`, `core/automator/event/EventGate.kt`, `core/accessibility/window/**` (预取).
- 新层接入点 `runtime/api/modules/selector/**` (`SelectorModule`, 删副本), `modules/auto/{AutoModule,AutoOptions}.kt`, `modules/flow/{FlowArgs,FlowOptions}.kt` (改调 core 归一化器).
- 其它调用方只做 "改为消费新入口" 的最小改动: `runtime/api/augment/automator/A11yDiagnosticsApi.kt`, `augment/flow/{FlowCondition,RuntimeFlowBindings}.kt`, `augment/toolkit/RuntimeToolkit.kt`, `core/automator/toolkit/ToolkitBindings.kt`, `engine/RuntimeFlowPick.kt`, `ui/floating/layoutinspector/**` (复制为选择器), `rhino/JsArgumentCoercion.kt`.
- 工具 `tools/selector/**` (新建), 文档仓库与 d.ts 仓库 (P4).

非目标:

- 旧 augment 层 `runtime/api/augment/selector/Selector.kt` (pickup 15 分支 / detect 7 分支) 冻结不改, 由 augment 主题 P5.2 删除; 它继续经 `UiSelector.pickup` / `UiObject.detect` 兼容入口工作.
- Node Bridge 的冻结协议 (`core/automator/bridge/BridgeSelector`, `engine/NodeBridge*`) 与 Python Host (`core/plugin/python/PythonHostSelector.kt`) 的选择器语义不变; 它们经 `UiSelector` 搜索, 因此自动获得 P2 的效率与 P3.2 的新鲜度, 不获得 P3.3 / P3.4 的新属性.
- 轮询间隔的自适应退避与多窗口根节点去重 (附录 B Q3 未选), 可见优先的默认开 (Q6 未选, 以选项提供).
- 布局分析器 UI, `RootAutomator`, `com.stardust` 兼容别名.

## 2. 固定决策

D1-D11 由维护者 2026-10-25 经选择题确认 (题面与选项见附录 B), 后续阶段不再重新讨论:

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 范围: core 加接管预检合并 | 只改 core 与新层接入点; 旧 `augment/selector/Selector.kt` 不动; 本主题接管 augment P5.2 中 "SelectorChecks / SelectorForms / SelectorPatterns 的预检并入 core 后删副本" 一项 (P1.4), 已在 augment 看板待决区登记跨主题通知, augment P5.2 发卡时从清单划出; `modules/selector` 四个文件只由本主题改, 与 augment 排好先后. |
| D2 | 兼容尺度: 6.8.0 允许破坏性调整 | 前序 D2 (既有 JS API 完全兼容) 放宽为: 列于 D6 的三项允许改变默认行为, 各记 changelog 并保留回退开关 (`auto.setFlags` 系列); 未列入的行为仍按前序 D2 保持, 明确 bug 修复记 `fix`; 新能力默认关. Node Bridge / MCP 的冻结协议与 Python Host 的选择器语义不受 D6 影响 (见第 1 节非目标). |
| D3 | 效率杠杆 | 做: 框架快路径 (P2.3), 预取与节点读取优化 (P2.1), 过滤器排序与短路 (P2.2). 不做: 轮询自适应退避 (事件辅助代替), 多窗口根节点去重. |
| D4 | 准确率杠杆 | 做: 命中后校验与过期重找 (P3.2), 文本匹配规范化 (P3.3), 多候选消歧 (P3.4), 诊断补全 (P3.5). |
| D5 | PickRequest 统一模型 | 一个 Android-free 的 `PickRequest` (root / selector 形式 / 罗盘步骤 / 结果类型 / 回调) 由单一归一化器 `PickArguments` 从任意参数序列解析并校验 (先判定再抛, 带参数名的 TypeError / RangeError); 结果整形用显式的节点方法注册表 `NodeMethodRegistry` 替代 `getMethod` 反射; Picker / Detector / Invoker 合并为 `PickExecutor`; 新层 SelectorModule, AutoModule.explain, Flow / toolkit 都调同一入口. |
| D6 | 破坏性调整清单 (6.8.0) | (a) 事件辅助轮询默认开, 带 50 ms 下限与事件合并 (D9); (b) `pickup` / `detect` 的参化调用只接受注册表方法 (范围见 D11), 不再反射任意 `getXxx` / `setXxx` 名称, 被拒的名字按 RangeError 报错并列候选; (c) 单结果入口 (`pickup` 缺省, `findOnce`, `findOne(timeout)`, `untilFindOne`) 命中后校验默认开: 返回前 `refresh()` 命中节点, 过期则在剩余超时内重找 (最多 2 次). 三项各留回退开关, 各记 changelog. 多候选可见优先不默认开 (P3.4 以选项提供). |
| D7 | 验证方式与设备 | JVM: `FakeUiNode` 全量单测; 真机: `tools/selector/` 基准脚本 (前序 P5.5 的三个场景, 每项 10 轮, 输出只保留通过 / 失败与中位数) 与结果对照脚本 (快路径 / 排序优化前后 `find()` 逐节点一致). 设备: 先用空闲的 23046RP50C (API 35) 与 22120RN86C (API 33); API 28 的 G8441 与预置 AVD_API_24 在释放后补跑节点回收分支; 预取 (API 33+) 用 OPD2413 / 23046RP50C. 设备与宿主 Gradle 使用登记 `D:/idea-projects/locks.md`. |
| D8 | 落盘与调度 | 本 Roadmap 位于 `docs/selector/ROADMAP.md`, 看板 `.claude/boards/selector.md`, 主题名 `selector`; 调度会话由维护者从 dispatch 环境新建后输入 `/dispatch selector`, 立项会话不发卡片. |
| D9 | 事件辅助轮询: 默认开 + 50 ms 下限 | `PollPolicy.assisted()` 成为重复查询的默认计划; 事件唤醒后距上次遍历不足 50 ms 则补睡到 50 ms (最差等于现状的固定 50 ms), 连发事件合并为一次唤醒 (`EventGate` 现无最小间隔), 安静屏幕兜底 200 ms; 关闭方式见附录 C Q2. 依据: 服务配置已是 `typeAllMask` + `notificationTimeout=0`, 事件流与开关无关; 脉冲 (`A11yEventPulse`) 每脚本一个, 首次用到才订阅, 退出即取消, 处理器只计数 / 记时 / 触发闩; 前序基准: 持续轮询 CPU 平板 30-37% 降到 10-15%, AVD 5-10% 降到 2-4%, `findOne(600)` 遍历次数 9-13 降到 5. |
| D10 | 文本规范化: 选择器级 `normalize` | 新增注册表属性 `normalize(options | true)`, 作用于同一选择器的全部字符串条件 (两侧同时规范化, 正则只规范化被匹配值); 对象选择器键 `normalize`, 字符串语法 `normalize=...` 由注册表自动获得; 默认关; 不加脚本级开关. 选项集合见附录 C Q4. |
| D11 | 注册表范围: 自有方法 + Compat 只读 getter | `NodeMethodRegistry` = `UiObject` / `UiObjectActions` 的公开方法 (约 150 个) + `AccessibilityNodeInfoCompat` 的无参 `getXxx` / `isXxx` (hintText, inputType, tooltipText, paneTitle, error, liveRegion, maxTextLength, actionList 等), 每个 getter 接受 `hintText` / `getHintText` / `isChecked` 三种拼写; 不含 setter 与带参数的 Compat 方法 (`findAccessibilityNodeInfosByText`, `getChild` 改用 `child`). 由反射一次生成并缓存 (与 `SelectorRegistry` 一样有漂移测试), 可派生 d.ts 的 `PickupResult` 联合类型. |

由前序沿用且与本主题直接相关的约束: 前序 D2 的派生约束 (`Classes.kt` 暴露的 Java 类全限定名不可变; `UiSelector` 新增 public 方法会成为全局函数, 内部方法须 `internal` / `private`), D6 (罗盘 `k` 段无可点击节点返回 null), D13 (字符串选择器语法), D15 (`contains` 系列字面子串).

## 3. 现状诊断 (2026-10-25)

| 问题 | 锚点 |
| --- | --- |
| `Invoker.Interceptor` 对未登记的方法取参数类型时写成 `params.map { params.javaClass }` (取的是数组自身的类 `Object[]`), 因此 `pickup(sel, ['方法', 参数])` 只对拦截表里约 60 个名字有效, 其余一律报 "未知结果类型"; 失败时还要再试 `getXxx`, 两次 `NoSuchMethodException` (augment 附录 C Q69 测得未知结果类型为 Rhino `undefined.x` 的 4.35 倍) | `core/automator/pick/Invoker.kt` |
| 新层为了 "先判定再抛" 复制了 core 读取方的校验: `SelectorChecks` (重载匹配与取整, 算法名, 罗盘 ASCII 扫描, 拦截表取整) / `SelectorForms` (选择器形式, 结果类型, `INVOKED` 方法表) / `SelectorPatterns` (正则语法预检, 653 行), 以 `SelectorChecksTest` 对照 core; augment P5.2 要求合并后删除 | `runtime/api/modules/selector/{SelectorChecks,SelectorForms,SelectorPatterns}.kt` |
| `Detector.Source` (byOne / byAll) 与 `Picker.Builder` 的 setter 链只是为了把 5 个参数绕到 `Detector` 里; `UiSelector.pickup` / `UiObject.detect` 两个伴生入口签名相同只差 root 来源 | `core/automator/pick/{Picker,Detector}.kt`, `UiSelector.kt:365`, `UiObject.kt:629` |
| 每次 `findWithin` 的 `finally` 都调用 `toString()` 拼接选择器字符串给 `A11yStats` (统计无开关, 恒开) | `UiSelector.kt` `findWithin`, `diagnostics/A11yStats.kt` |
| 每个 bounds 度量读取 (`left()` 等 8 个) 都 `Rect()` 分配一次并复制; `MetricsFilter` / `BoundsFilter` 链上同一节点重复读取; `content` 条件读 desc 与 text 各一次 | `UiObject.kt` `bounds()`, `AccessibilityNodeInfoHelper.kt`, `filter/{MetricsFilter,BoundsFilter,StringProperty}.kt` |
| 过滤器按声明顺序求值, 正则 / bounds / 脚本函数可能排在布尔条件之前; `Selector.filters` 为 `CopyOnWriteArrayList`, `all {}` 每节点一个迭代器 | `filter/Selector.kt` |
| 每个节点一次 `getChild` IPC, 未用 API 33+ 的预取策略 (`getRootInActiveWindow(strategy)`, `getChild(index, strategy)`, `FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST` / `FLAG_PREFETCH_UNINTERRUPTIBLE`); 精确 `id()` / `text()` 条件未用框架的 `findAccessibilityNodeInfosByViewId` / `ByText` | `search/DFS.kt`, `UiObject.kt`, `window/WindowRootResolver.kt` |
| 事件辅助轮询默认关; `EventGate` 没有最小间隔, 事件风暴下遍历会比 50 ms 更密 | `core/automator/event/EventGate.kt`, `query/PollPolicy.kt`, `AccessibilityBridge.kt` |
| 单结果入口把 DFS 首个命中直接交给脚本: 不校验新鲜度 (过期节点动作失败), 不区分可见性 (同名不可见节点) | `UiSelector.findOnce`, `UiSelectorQuery.one`, `pick/Picker.kt` |
| 字符串条件只有字面 / 正则两种比较, 无空白 / 全角 / 大小写规范化 | `filter/StringMatcher.kt` |
| 基准 (架构文档 1.9, 2026-09-06): 百级节点屏幕 `findOnce` 未命中 3-5 ms (AVD) / 5-17 ms (平板), 千级节点 6-15 ms; 持续轮询 CPU 平板 30-37% | `docs/dev/accessibility-architecture.md` 1.9 |

## 4. 阶段总览

| 阶段 | 内容 | 依赖 | 并行 | 设备 |
| --- | --- | --- | --- | --- |
| P0 | 基线与安全网: 基准脚本, 结果对照脚本, pickup / detect 黄金表 | - | - | 23046RP50C, 22120RN86C |
| P1 | PickRequest 模型: 归一化器, 节点方法注册表, 执行器, 删副本 | P0 | - | 23046RP50C (黄金表复核) |
| P2 | 搜索效率: 节点读取与预取, 过滤器排序, 框架快路径, 基准复测 | P1 (共改 UiSelector / UiObject) | P3 可与 P2 并行 (文件分工见 P3 说明) | 23046RP50C, 22120RN86C, OPD2413 |
| P3 | 准确率: 事件辅助默认开, 命中后校验, normalize, 多候选消歧, 诊断 | P1 | 见上 | 同上 |
| P4 | changelog, 文档, d.ts, 示例 | P2, P3 | 子项可并行 | - |
| P5 | 发布门禁 | P4 | - | 一台 API >= 33 加 G8441 或 AVD_API_24 |

每张卡片 (子会话) 对应一个阶段; 阶段内条目顺序执行. 共享宿主工作区: 只提交自己的文件, 不推送 (宿主只本地提交), 不切分支, 不碰其他会话在改的文件; 宿主构建与设备使用先在 `locks.md` 登记; `.changelog/lang_zh-Hans.json` 有他人未提交改动时只写草稿交维护者.

## 5. 阶段与条目

### P0 基线与安全网

- [ ] P0.1 基准脚本 `tools/selector/bench.js` + `tools/selector/device_bench.py` (做法同 `tools/augment/device_bench.py`, `py -3.12 -I` 运行, adb 推送脚本经 RunIntentActivity 执行, 读 logcat 取结果): 三个场景 (系统设置首页, AutoJs6 主界面, 桌面之上 300 个 TextView 的合成悬浮窗), 指标 `full` (`selector().find().size()`), `miss` (`text('__none__').findOnce()`), `hit` (早命中 `findOnce()`), `findOne(600)` 未命中的尝试次数, 轮询 10 s 的进程 CPU (安静屏幕, 以及 "事件风暴" 场景: 悬浮窗内一个持续更新的进度条), 每项 10 轮, 输出通过 / 失败与中位数 (不打印节点全文). 验收: 在 23046RP50C 与 22120RN86C 上各跑一次, 修前基线写入附录 A. (DEVICE)
- [ ] P0.2 结果对照脚本 `tools/selector/parity.js` + `device_parity.py`: 固定屏幕 (AutoJs6 主界面与设置首页) 上一组选择器 (精确 id / 短 id / 精确 text / textContains / 正则 / className + clickable / bounds / 对象选择器 / 混合数组 / `select('...')` 字符串语法 / BFS) 的 `find()` 结果序列按节点指纹 (`fingerprint()` + 深度 + indexInParent) 快照, 比对两次运行一致 (稳定屏幕上应 100% 一致), 作为 P2 快路径与排序优化 "顺序与数量不变" 的判据. 验收: 两台设备各一次, 快照文件进 `tools/selector/baseline/`. (DEVICE)
- [ ] P0.3 pickup / detect 黄金表 `tools/selector/pick_table.js`: 枚举结果类型全部 43 种拼写 (`PickResultType` 的 spellings) x (缺省 / 带罗盘 `p` / `c0` / `s>1` / `k2`) x (有命中 / 无命中) x (有 / 无回调), 选择器参数的各类形式 (字符串, 正则, UiSelector, 对象, 数组, 数字, root), 以及拦截表内外的参化调用 (`['child', 0]`, `['setText', 'x']`, `['bounds']`, `['hintText']`, 未知名字); 输出每条的结果形状 (类型名, 数组长度, 字符串值) 与错误类型, 在 23046RP50C 上记录为 `tools/selector/baseline/pick_table-<device>.json`. 这是 P1 重构 "行为不变" 的判据 (D6 (b) 允许的差异除外). (DEVICE)
- [ ] P0.4 JVM 安全网: 全量 `:app:testAppDebugUnitTest` 通过作为起点 (记录用例数); `CompassTest` / `CompassParserTest` / `PickResultTypeTest` / `SelectorRegistryTest` / `SelectorChecksTest` / `UiSelectorBuildersTest` / `SelectorExplainerTest` 为 P1 的回归集. (JVM)

### P1 PickRequest 模型 (D5, D6 (b), D11, D1 的预检合并)

- [ ] P1.1 `core/automator/pick/PickRequest.kt` (Android-free 数据类: `root: UiNode?`, `selector: UiSelector`, `compass: List<CompassStep>`, `result: PickResultType`, `callback`) 与归一化器 `PickArguments`: 从 `pickup` (0-5 个参数, root 规则同现有: 两个参数起首个为 UiObject 即 root, 五个参数时首个总是 root) 与 `detect` (2-4 个) 的参数序列识别各位置 (与 `SelectorModule` 现有的下标法等价), 校验选择器形式 (字符串 / 正则 / UiSelector / 对象 / 数组 / 数字 / root; 对象键经 `SelectorRegistry.entry` 与 `SelectorEntry.resolve` 先判定), 罗盘 (`CompassParser.tryParse`), 结果类型 (符号或注册表方法, `detect` 无复数形式, `@` 不能带罗盘), 回调; 全部 "先判定再抛", 错误为带函数名与参数名的 TypeError / RangeError (复用 `ScriptMessages`, 附录 C Q5), 途中不抛 Java 异常 (前序 R2). 旧入口 `UiSelector.pickup(scriptRuntime, root, selector, compass, resultType, callback)` 与 `UiObject.detect(...)` 保留签名, 内部构造 PickRequest. (SOURCE; JVM: `PickArgumentsTest` 覆盖 pickup 17 种与 detect 7 种重载形式及每类错误)
- [ ] P1.2 `core/automator/pick/NodeMethodRegistry.kt`: 按 D11 由反射一次生成 (`UiObject` / `UiObjectActions` 公开方法 + Compat 无参 getter, 排除 `equals` / `hashCode` / `toString` / `wait` / `notify*` / `getClass` / `recycle` 与 internal 混淆名), 每个条目: 名称与别名拼写, 重载的参数类型 (`int` / `float` / `boolean` / `String` / `CharSequence` / `Int`...), 调用器; 参数转换沿用 `JsArgumentCoercion` (NaN 取整先判定); 未知名字的 RangeError 附候选 (同名异大小写 / 前缀 / 编辑距离, 至多 5 个, 同 `SelectorStringParser`). 漂移测试: 注册表与 `UiObject::class.java.methods` 的差集只允许白名单. 现有拦截表 `Invoker.Interceptor.interceptedMap` 删除 (其 60 个名字全部在注册表内, 由测试断言). (SOURCE; JVM: `NodeMethodRegistryTest`)
- [ ] P1.3 `core/automator/pick/PickExecutor.kt`: 执行 PickRequest: 搜索 (root 之下或窗口根, 单个 / 全部), 罗盘, 结果整形 (`PickResultType` 的符号表保留; `Invocation` 走注册表), 回调 (返回 `undefined` 则保留原结果), `javaToJS`. `Picker` / `Detector` / `Invoker` 删除, `UiSelector.pickup` / `UiObject.detect` / `A11yDiagnosticsApi` (`auto.explain` 的 selector 构建) / `RuntimeFlowPick` / `ToolkitBindings` / `FlowCondition` 改调 PickExecutor 或 PickArguments. Rhino 相关部分 (回调调用, `javaToJS`) 收在一个薄适配 (`PickResultAdapter`), 使整形逻辑在 JVM 上以 `FakeUiNode` 测试. (SOURCE; JVM: `PickExecutorTest` 在 FakeUiNode 树上覆盖全部结果类型 x 罗盘 x 空结果)
- [ ] P1.4 删副本 (D1): `SelectorChecks` 的重载匹配 / 取整 / 算法名 / 罗盘扫描并入 `SelectorEntry.resolve` (返回判定结果而非抛出), `UiSelector.useAlgorithm` (先查表), `CompassParser.tryParse`; `SelectorPatterns` 的正则语法预检迁入 `core/automator/filter/` (保留 "只拒绝 JVM 与 ICU 两种引擎都拒绝的写法" 的界限, `SelectorPatternsTest` 与 `tools/augment/device_patterns.py` 的误拒对照照跑); `SelectorForms` 的 `INVOKED` 表由 `NodeMethodRegistry` 取代; 新层 `SelectorModule` / `AutoModule.explain` / `AutoOptions` / `FlowArgs` / `FlowOptions` 改调 `PickArguments` 的判定; 三个副本文件删除, `SelectorChecksTest` 改为对 core 判定的直接测试. 完成后在 augment 看板待决区把跨主题通知行改为 "已完成 (提交 hash)". (SOURCE; JVM)
- [ ] P1.5 复核: 全量单测通过 (用例数不少于 P0.4); 23046RP50C 上 `pick_table.js` 与 P0.3 基线逐条一致, 允许的差异只有 D6 (b) (注册表外名字由 "未知结果类型" 的 IllegalArgumentException 改为 RangeError 并列候选) 与 P1.1 的错误类型 (IllegalArgumentException -> TypeError / RangeError), 差异逐条列入附录 A; augment 的 `tools/augment/device_eval.py --cases app/build/script-modules-device --new-modules on --strict-errors` 中 selector / auto / flow 的用例仍一致 (新层行为不变). 架构文档 1.4 的 "拾取" 段与 2.2 迁移映射更新. (DEVICE; DOCS)

### P2 搜索效率 (D3)

- [ ] P2.1 节点读取与预取: `UiObject` 每节点 bounds 只读一次 (四个 int 字段, `refresh()` / `snapshot()` 后失效), `left()` 等 8 个度量不再分配 `Rect`; `content` 条件读 desc / text 各一次; `A11yStats.recordSearch` 改为传入选择器对象或惰性字符串 (渲染报告时再 `toString()`, 或 `Selector` 缓存字符串到 filters 变化); API 33+ 取根节点与 `getChild` 使用深度优先预取策略 (`FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST | FLAG_PREFETCH_UNINTERRUPTIBLE`, BFS 时用 BREADTH_FIRST), API < 33 不变; `MODE_FAST` 下是否启用见附录 C Q6. 验收: `parity.js` 100% 一致; 基准 `full` / `miss` 在 API 35 设备上有可测的下降 (写入附录 A, 不设硬门槛, 但下降不足 10% 时在附录 A 说明原因). (SOURCE; JVM: UiObject 字段缓存无法在 JVM 构造, 用 FakeUiNode 覆盖 content 单次读取; DEVICE)
- [ ] P2.2 过滤器排序与短路: `Filter` 增加 `cost` (布尔 / int / depth / className 等值 < 字符串等值 / 前后缀 / 子串 < bounds 与度量 < 正则 < `filter(fn)` 脚本函数 < `action` 列表), `Selector.filter(node)` 按 cost 稳定排序后的快照求值 (声明顺序不变: `toString()`, `auto.explain` 的逐过滤器计数与 "失败步骤" 仍按声明顺序统计); `BooleanFilter` / `IntFilter` 不变. 验收: `parity.js` 一致; `SelectorExplainerTest` 不变; 基准 `miss` (含正则条件的场景) 下降写入附录 A. (SOURCE; JVM: `SelectorCostOrderTest`; DEVICE)
- [ ] P2.3 框架快路径: 选择器含精确 `id` (全名或短 id, 短 id 以根节点 packageName 拼全名) 或精确 `text` 条件时, 先用根节点的 `findAccessibilityNodeInfosByViewId` / `findAccessibilityNodeInfosByText` 取候选 (一次 IPC): 候选为空即判定未命中 (不遍历); 候选恰好 1 个且通过其余过滤器时直接返回 (单结果入口); 其余情况 (多候选, `find()` 全量, BFS, 带 `max` 的负数索引) 走完整 DFS 以保持顺序. 可靠性保障 (附录 C Q1): 提供方可能对文本搜索返回空 (自定义 `AccessibilityNodeProvider`), 因此未命中判定只用于轮询的中间尝试, 查询的最后一次尝试 (超时前) 与单次 `find()` / `findOnce()` 仍走完整 DFS 校正; `auto.setFlags(['noFastPath'])` 可关 (名字见 Q2). 验收: `parity.js` 一致; 基准 `miss` 场景 (轮询中间尝试) 的耗时与 `findOne(600)` 的 CPU 下降写入附录 A; 在 OPD2413 (ColorOS) 上补跑一次 (不同 ROM 的提供方行为). (SOURCE; JVM: FakeUiNode 增加候选查询接口以测试分派; DEVICE)
- [ ] P2.4 基准复测: `tools/selector/device_bench.py` 在 23046RP50C / 22120RN86C / OPD2413 各一次, 修前后对照表写入附录 A (中位数, 百分比); `auto.stats` 增加快路径命中 / 未命中 / 回退计数. 架构文档 1.9 追加本期基准. (DEVICE; DOCS)

### P3 准确率 (D4, D6 (a) (c), D9, D10)

P3 与 P2 可由两张卡片并行: P2 只改 `UiObject.kt` (读取缓存), `search/**`, `filter/Selector.kt` 与各 Filter 的 cost, `UiSelector.findImpl` 快路径, `window/**`, `A11yStats`; P3 只改 `query/{PollPolicy,Poller}.kt`, `event/EventGate.kt`, `A11yEventPulse.kt`, `UiSelectorQuery.kt`, `UiSelector.query / findOnce`, `filter/{StringMatcher,StringProperty}.kt` 与 `SelectorRegistry` 的新条目, `pick/PickExecutor` 的候选偏好, `diagnostics/SelectorExplainer`. 共同文件 (`UiSelector.kt`, `AccessibilityBridge.kt` 的标志) 由先开始的卡片改, 后者等前者提交后再碰; 宿主构建在 `locks.md` 排队, 不同时编译 (Kotlin 守护进程并行编译会 GC 抖动).

- [ ] P3.1 事件辅助轮询默认开 (D9): `PollPolicy.assisted()` 增加 `minSpacing = 50` (事件唤醒后距上次尝试不足 50 ms 则补睡), `EventGate` 合并连发信号 (已有 pending 位, 补 "一次 await 只消费一次"), `UiSelector.query` 与 `UiObject.waitUntilGone` / `waitForStable` / Flow 等待默认走 `bridge.pulse.poller` (服务未就绪或无 bridge 时退回固定间隔); 关闭方式按附录 C Q2 实现; `auto.setFlags(['eventAssistedPolling'])` 仍接受 (无操作). 验收: `EventAssistedPollingTest` 补最小间隔与合并用例; 基准 "事件风暴" 场景的遍历次数不多于固定 50 ms 的轮次, 安静屏幕 CPU 下降写入附录 A; `findOne(600)` 的尝试次数记录. changelog `improvement`. (SOURCE; JVM; DEVICE)
- [ ] P3.2 命中后校验与过期重找默认开 (D6 (c)): 单结果入口 (`PickExecutor` 的单个结果, `UiSelector.findOnce` / `findOne(timeout)` / `untilFindOne`, `UiSelectorQuery.one`) 返回前对命中节点 `refresh()`; 失败 (节点已离开窗口) 则在剩余超时内重新搜索, 最多 2 次, 仍失败按未命中处理; `find()` / `findOnce(index)` 的全量结果不校验; `auto.setFlags(['noRefreshOnHit'])` 可关 (名字见 Q2). Node Bridge / Python Host 经 `UiSelector` 自动获得. 验收: FakeUiNode 增加 "刷新失败" 开关的用例; 真机用 "命中后立即关闭对话框" 的脚本验证重找 (23046RP50C); 基准 `hit` 的额外耗时 (预期 0.5-1 ms) 写入附录 A. changelog `improvement`. (SOURCE; JVM; DEVICE)
- [ ] P3.3 `normalize` (D10): `SelectorRegistry` 新增属性 `normalize` (重载: 无参 = 全部; 布尔; 选项对象 `{ trim, caseInsensitive, fullWidth, whitespace, nfkc }`, 选项集合与适用属性见附录 C Q4), 生成 `UiSelectorBuilders` (运行 `UiSelectorBuildersTest` 复制渲染), `StringMatcher` 增加 `Normalizer` (两侧规范化; 正则只规范化被匹配值), `StringProperty.test` 传入; 对象选择器与 `select('normalize=...')` 自动获得; `toString()` 输出 `normalize(...)`; `auto.explain` 的逐过滤器计数照常. 验收: JVM 用例覆盖五个选项与组合 (含 `textContains` / `textMatch` / `content`), 真机在设置首页用全角与大小写差异的文本命中一次. changelog `feature`. (SOURCE; JVM; DEVICE)
- [ ] P3.4 多候选消歧 (默认关): `SelectorRegistry` 新增属性 `prefer(order...)` (键: `visible` / `clickable` / `enabled` / `center` (距屏幕中心近) / `area` (面积大) / `top` (更靠上), 顺序即优先级, 计分规则见附录 C Q3); 设置后单结果入口收集全部命中 (不再止于首个) 并按偏好排序取首个; 罗盘 `k` 段在 `prefer('visible')` 下遇不可见的可点击节点继续上溯. 验收: FakeUiNode 用例覆盖每个键与组合; 真机用两个同名按钮 (一个被遮挡) 的合成悬浮窗验证. changelog `feature`. (SOURCE; JVM; DEVICE)
- [ ] P3.5 诊断补全: `auto.explain(selector, compass?, resultType?)` 扩展: 命中节点后的罗盘每一步到达的节点 (或失败的步骤), 结果类型方法的解析 (注册表条目与参数转换), 快路径 / 重找 / 偏好排序是否生效; `auto.stats` 的查询项记录重找次数; 布局分析器 "复制为选择器" 增加 pickup 形式 (`pickup({...}, 'compass', 'result')`) 的输出选项. 验收: JVM 用例; 真机运行示例 "选择器诊断与节点树导出" 并核对输出. (SOURCE; JVM; DEVICE)

### P4 changelog, 文档, 声明, 示例

- [ ] P4.1 changelog: `.changelog/lang_zh-Hans.json` 补 `feature` (normalize, prefer, 诊断), `improvement` (事件辅助默认开, 命中后校验, 快路径, 预取), `fix` (Invoker 参数类型), 生成 README; 其他语言由维护者同步 (记忆: 6.8.0 仅重写 zh-Hans); 该文件有他人未提交改动时只写草稿交维护者. (DOCS)
- [ ] P4.2 文档仓库 `AutoJs6-Documentation`: `api/uiSelectorType.md` 的 pickup / normalize / prefer 节, `api/uiObjectType.md` 的 detect 节, `api/dataTypes.md` 的 PickupResult (按注册表列出可用方法名) / DetectCompass, `api/automator.md` 的 setFlags 新标志与 explain 扩展; 运行 `generator/auto-generate-for-autojs6.bat`, 提交文档仓库与 Offline Docs 插件仓库 (按惯例). (DOCS)
- [ ] P4.3 d.ts 仓库 `AutoJs6-TypeScript-Declarations`: `aj6-int-selector.d.ts` 的 `Pickup.ResultType` 由 `any` 改为注册表派生的联合类型 (由 `NodeMethodRegistry` 导出清单, 生成脚本或测试输出), `normalize` / `prefer` 重载, `Auto.Flags` 新项; 同步 Ace Editor 插件的 `aj6-int-*.d.ts` 并运行 `:app:generateAutoJs6LspDeclarations`; 两个仓库版本号加一. (DOCS)
- [ ] P4.4 示例: `app/src/main/assets-app/sample/无障碍服务/` 新增 "选择器规范化与候选偏好 [v6.8.0+].js", 更新 "选择器诊断与节点树导出 [v6.8.0+].js" 的 explain 用法; 真机运行一次. (DEVICE)
- [ ] P4.5 通知 api-coverage 主题 (看板 `.claude/boards/api-coverage.md` 待决区追加一行): 本主题新增 / 变更的脚本 API 清单 (normalize, prefer, setFlags 新标志, explain 扩展, PickupResult 类型), 供其 P3.4 (automator / selector / UiObject 函数级差异) 对照. (DOCS)

### P5 发布门禁

- [ ] P5.1 两台真机 (一台 API >= 33: 23046RP50C 或 OPD2413; 一台 API < 33: G8441 或预置 AVD_API_24, 覆盖节点回收分支) 上: `sample/accessibility/smoke-legacy-api.js`, P6.4 与 P4.4 的示例, `tools/selector/parity.js`, `pick_table.js` 全部通过; `device_bench.py` 一次; `:app:testAppDebugUnitTest` 通过; `:app:assembleAppDebug` 通过 (构建可长达 9 分钟, 按 AGENTS.md 等待策略); API < 33 设备上 `AccessibilityNodeInfo` 实例数在 S7b 式循环后不增长 (沿用 memory 门禁的 VMDebug 口径, +50 以内). 结果写入附录 A, 架构文档 1.7 / 1.9 更新. (DEVICE; ANDROID_BUILD)

## 6. 红线 (全程遵守)

1. `find()` / `findOnce(index)` / `untilFind()` 的结果顺序与数量不变 (P0.2 对照为准); 快路径只用于判定未命中与单候选.
2. API < 33 上 `AccessibilityNodeInfo` 按进程池化: 不缓存节点对象跨查询复用, 不做退出时批量回收 (前序 P1.5); 节点字段缓存只在 `UiObject` 实例内, 随 `refresh()` 失效.
3. 新资源类型 (若有) 登记 `RuntimeResourceScope`; 进程级静态表的键或值为弱引用 (AGENTS.md "脚本资源与静态引用", `StaticReferenceGuardTest`).
4. 新层 (`modules/**`) 不使用 `withRhinoContext`, 只在传入的 Context 中执行 (augment T6); core 的归一化器不依赖 Rhino 之外的 Android 类, 可在 JVM 构造.
5. 旧 augment 层 `augment/selector/Selector.kt` 不改; `Classes.kt` 暴露的 Java 类全限定名不变; `UiSelector` 新增成员为 `internal` / `private`, 公开的新 builder 只经 `SelectorRegistry` 生成.
6. Node Bridge 冻结协议与 Python Host 选择器语义不变 (`BridgeSelectorFiltersTest`, `PythonHostSelector*Test` 不改期望).
7. 共享宿主工作区: 只提交自己的文件, 不推送, 不切分支; 宿主构建与设备登记 `D:/idea-projects/locks.md`; 需要人工确认的手机对话框由维护者点.
8. 文案 ASCII 标点; 字符串资源按 AGENTS.md 六原则; 注释里不写 `/*` 嵌套.

## 附录 A: 基线与复测数据

P0.1 / P0.2 / P0.3 落地时填写: 设备, 宿主构建号, 日期, 三场景 `full` / `miss` / `hit` 中位数, `findOne(600)` 尝试次数, 轮询 CPU (安静 / 事件风暴), parity 快照文件, pick_table 条数. P1.5 / P2.4 / P3 各条 / P5.1 追加修后数据与差异说明.

- (待 P0 填写)

## 附录 B: 立项选择题记录 (2026-10-25)

| 题 | 问题 | 维护者选择 | 落入 |
| --- | --- | --- | --- |
| Q1 | 范围与 augment 协同: 只改 core 并接管预检合并 / 只改 core / 等 augment P5.1-P5.2 之后 | core + 接管预检合并 | D1, P1.4 |
| Q2 | 兼容性红线: 沿用前序 D2 / 允许少量默认值调整逐项拍板 / 6.8.0 允许破坏性调整 | 6.8.0 允许破坏性调整 | D2 |
| Q3 | 效率杠杆 (多选): 框架快路径 / 预取与读取优化 / 过滤器排序与短路 / 轮询与多窗口 | 前三项 | D3, P2 |
| Q4 | 准确率杠杆 (多选): 命中后校验 / 文本规范化 / 多候选消歧 / 诊断补全 | 全部四项 | D4, P3 |
| Q5 | 重构形态: 统一 PickRequest 模型 / 局部清理 | 统一 PickRequest 模型 | D5, P1 |
| Q6 | 破坏性清单 (多选): 事件辅助默认开 / 参化调用只接受注册表 / 命中后校验默认开 / 可见优先默认开 | 前三项 (维护者就事件辅助的后台耗电提问, 解答后以 Q9 确认) | D6 |
| Q7 | 验证与设备: JVM + 基准 + 空闲设备先跑 / JVM + 一台真机 / 等 XQ-AT72 与 G8441 释放 | JVM + 基准 + 空闲设备先跑 | D7 |
| Q8 | 落盘: 新建 docs/selector 与看板, 维护者新建调度会话 / 本会话直接发卡 / 并入旧 roadmap 作 P7 | 新建 docs/selector 与看板 | D8 |
| Q9 | 事件辅助轮询: 默认开 + 50 ms 下限 / 默认开不加下限 / 保持默认关 | 默认开 + 50 ms 下限 | D9, P3.1 |
| Q10 | 规范化入口: 选择器级 normalize / 选择器级 + 脚本级默认值 / 只脚本级 | 选择器级 normalize | D10, P3.3 |
| Q11 | 注册表范围: 自有方法 + Compat 只读 getter / 只自有方法 | 自有方法 + Compat 只读 getter | D11, P1.2 |

## 附录 C: 待决事项

格式与看板一致; 由调度会话在相应阶段开始前用选择题请维护者拍板, 子会话不自行采用默认值 (不阻塞的可按推荐先行并登记 "已按推荐先行待确认").

| 编号 | 问题 | 选项 (推荐项在前) | 影响 | 改选返工 | 状态 |
| --- | --- | --- | --- | --- | --- |
| Q1 | 快路径对 `text` 条件的未命中判定是否可靠: 自定义 `AccessibilityNodeProvider` (部分 WebView / 游戏引擎) 的 `findAccessibilityNodeInfosByText` 可能返回空 | (1) 推荐: 中间尝试用快路径判未命中, 查询最后一次尝试与单次 `find*` 走完整 DFS 校正, 并在 `auto.stats` 记录 "快路径未命中但 DFS 命中" 的次数 (真机为 0 才算达标); (2) 快路径只用于 `id` 条件; (3) 不做 `text` 快路径 | P2.3 | (2) / (3) 各删一段分派, 小 | 需拍板 (P2 前) |
| Q2 | D6 三项默认开后的关闭方式: 现只有 `auto.setFlags(names)` 没有清除 | (1) 推荐: 新增 `auto.clearFlags(names)`, 并让 `setFlags` 接受前缀 `!` 的名字 (`'!eventAssistedPolling'`); 新增标志名 `noFastPath` / `noRefreshOnHit` 不另起反向语义; (2) 只加反向标志 (`fixedIntervalPolling` / `noFastPath` / `noRefreshOnHit`), 不加 clearFlags; (3) 加 `auto.setFlags` 的对象形式 `{ eventAssistedPolling: false }` | P2.3, P3.1, P3.2, P4.2, P4.3 | 小 (名称与解析) | 需拍板 (P2 / P3 前) |
| Q3 | `prefer` 的键集合与计分: 多键时按顺序逐键比较 (字典序) 还是加权求和 | (1) 推荐: 字典序 (先按第一个键分组, 相同再看下一个), 键 `visible` / `clickable` / `enabled` 为布尔, `center` / `area` / `top` 为数值; (2) 加权求和 (权重随顺序递减); (3) 只提供 `visible` 一个键 | P3.4, P4.2, P4.3 | 小 | 需拍板 (P3 前) |
| Q4 | `normalize` 的选项集合与适用属性 | (1) 推荐: 选项 `trim` / `caseInsensitive` / `fullWidth` (全角转半角, 含全角标点) / `whitespace` (连续空白折叠为一个空格, 含 NBSP / 零宽字符移除) / `nfkc`, 无参或 `true` 为全部; 作用于 `text` / `desc` / `content`, 对 `id` / `className` / `packageName` 只应用 `trim` 与 `caseInsensitive`; (2) 全部属性应用全部选项; (3) 只做 `trim` + `caseInsensitive` + `fullWidth` | P3.3, P4.2, P4.3 | 小 | 需拍板 (P3 前) |
| Q5 | PickArguments 的错误消息: 复用新层 `ScriptMessages` (10 种语言, 已有 argumentType / cannotBe / failedToCall) 还是新增专用消息 | (1) 推荐: 复用, 结果类型的候选列表拼在 `cannotBe` 的说明里; (2) 新增 3-5 条专用资源 (10 种语言) | P1.1, P1.2 | 小 | 需拍板 (P1 前; 不阻塞, 可按推荐先行) |
| Q6 | 预取与快路径在 `MODE_FAST` (`fastRootInActiveWindow`, 缓存根节点) 下是否启用 | (1) 推荐: 启用 (根来自缓存不影响 `findAccessibilityNodeInfosByViewId` 的 IPC; 根过期时快路径与 DFS 同样只见过期子树); (2) `MODE_FAST` 下关闭快路径, 只用预取 | P2.1, P2.3 | 小 | 需拍板 (P2 前; 不阻塞, 可按推荐先行) |
| Q7 | P2 与 P3 是否并行 | (1) 推荐: 并行两张卡, 按第 5 节 P3 的文件分工, 共同文件由先开始者改, 构建在 locks.md 排队; (2) 顺序 P2 再 P3 | 批次安排 | 无 | 需拍板 (P1 完成时) |

## 附录 D: 协同约束 (2026-10-25 登记)

- augment 主题 (看板 `.claude/boards/augment.md`, 调度会话 local_7254cacc): P5.1 (开关默认开 + 真机矩阵) 运行中, 占用 XQ-AT72 / G8441 / XQ-DQ72 约 5 小时并安装宿主测试构建; P5.2 (删旧 augment 包) 未发卡. 本主题接管 P5.2 的 "SelectorChecks / SelectorForms / SelectorPatterns 预检并入 core 后删副本" (P1.4), 已在 augment 看板待决区登记跨主题通知 (未唤醒其调度会话). `modules/selector` 四个文件在 P1 期间只由本主题改; augment 删旧包时 `SelectorMethodResolver` (旧层 `Selector.kt` 内的全局查询方法分派) 随旧包删除, 新层 `SelectorModule.select` 对它的引用由 augment 处理 (本主题不改 `SelectorMethodResolver`).
- memory 主题 (已完成, 调度会话已归档): 节点分配器有意不接入脚本 (前序 P1.5, memory Q33); 门禁以 VMDebug 的 `AccessibilityNodeInfo` 实例数判定, P5.1 沿用该口径. `StaticReferenceGuardTest` 会拦截新静态表.
- api-coverage 主题 (看板 `.claude/boards/api-coverage.md`, 调度会话待维护者启用): 其 P3.4 做 automator / selector / UiObject 的函数级差异, 本主题 P4.5 把新增 API 清单登记到其看板.
- 3-macaw 主题: 占用 XQ-DQ72 (locks.md), 与本主题无文件交集.
- rhino-class 主题: 改 Rhino fork, 与本主题无文件交集; 本主题不依赖 fork 的新语法.
- 设备: 本主题默认用 23046RP50C / 22120RN86C / OPD2413; 需要 XQ-AT72 / G8441 时先看 locks.md 并等其释放; 需要人工确认的对话框 (无障碍授权, 悬浮窗权限) 由维护者操作.

## 会话记录

### 2026-10-25

- 立项会话 local_888c36fd (Fable 5.1): 读前序 Roadmap 与架构文档, pick 包, 新层 selector 模块, augment / memory 看板与 locks.md; 三轮 11 题选择题 (附录 B); 写出本 Roadmap 与看板; 未改代码. 调度会话待维护者从 dispatch 环境新建.
