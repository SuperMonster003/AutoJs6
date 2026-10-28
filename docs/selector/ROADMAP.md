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
| P2 | 搜索效率: 节点读取与预取, 过滤器排序, 框架快路径, 基准复测 | P1 (共改 UiSelector / UiObject) | P3 可与 P2 并行 (文件分工见 P3 说明); 2026-10-25 维护者定为顺序执行, 并行描述只作文件分工参考 | 23046RP50C, 22120RN86C, OPD2413 |
| P3 | 准确率: 事件辅助默认开, 命中后校验, normalize, 多候选消歧, 诊断 | P1 | 见上; 2026-10-25 维护者定为顺序执行, 并行描述只作文件分工参考 | 同上 |
| P4 | changelog, 文档, d.ts, 示例 | P2, P3 | 子项可并行 | - |
| P5 | 发布门禁 | P4 | - | 一台 API >= 33 加 G8441 或 AVD_API_24 |

每张卡片 (子会话) 对应一个阶段; 阶段内条目顺序执行. 共享宿主工作区: 只提交自己的文件, 不推送 (宿主只本地提交), 不切分支, 不碰其他会话在改的文件; 宿主构建与设备使用先在 `locks.md` 登记; `.changelog/lang_zh-Hans.json` 有他人未提交改动时只写草稿交维护者.

## 5. 阶段与条目

### P0 基线与安全网

- [x] P0.1 基准脚本 `tools/selector/bench.js` + `tools/selector/device_bench.py` (做法同 `tools/augment/device_bench.py`, `py -3.12 -I` 运行, adb 推送脚本经 RunIntentActivity 执行, 读 logcat 取结果): 三个场景 (系统设置首页, AutoJs6 主界面, 桌面之上 300 个 TextView 的合成悬浮窗), 指标 `full` (`selector().find().size()`), `miss` (`text('__none__').findOnce()`), `hit` (早命中 `findOnce()`), `findOne(600)` 未命中的尝试次数, 轮询 10 s 的进程 CPU (安静屏幕, 以及 "事件风暴" 场景: 悬浮窗内一个持续更新的进度条), 每项 10 轮, 输出通过 / 失败与中位数 (不打印节点全文). 验收: 在 23046RP50C 与 22120RN86C 上各跑一次, 修前基线写入附录 A. (DEVICE) (`DEVICE` 2026-10-25, build 4283, 新层开关 on: 23046RP50C / 22120RN86C 各一次, 三场景共 36 项全部 PASS, 修前基线见附录 A P0.1; 脚本 ec389609c, 基线 895070a9f)
- [x] P0.2 结果对照脚本 `tools/selector/parity.js` + `device_parity.py`: 固定屏幕 (AutoJs6 主界面与设置首页) 上一组选择器 (精确 id / 短 id / 精确 text / textContains / 正则 / className + clickable / bounds / 对象选择器 / 混合数组 / `select('...')` 字符串语法 / BFS) 的 `find()` 结果序列按节点指纹 (`fingerprint()` + 深度 + indexInParent) 快照, 比对两次运行一致 (稳定屏幕上应 100% 一致), 作为 P2 快路径与排序优化 "顺序与数量不变" 的判据. 验收: 两台设备各一次, 快照文件进 `tools/selector/baseline/`. (DEVICE) (`DEVICE` 2026-10-25: 两台 x 2 块屏幕 x 23 个用例, 两次运行逐节点 100% 一致, 23046RP50C 另以 `--check` 复核 100%; 快照 `baseline/parity-<model>-<screen>.json`, 见附录 A P0.2; 脚本 ec389609c, 快照 895070a9f)
- [x] P0.3 pickup / detect 黄金表 `tools/selector/pick_table.js`: 枚举结果类型全部 43 种拼写 (`PickResultType` 的 spellings) x (缺省 / 带罗盘 `p` / `c0` / `s>1` / `k2`) x (有命中 / 无命中) x (有 / 无回调), 选择器参数的各类形式 (字符串, 正则, UiSelector, 对象, 数组, 数字, root), 以及拦截表内外的参化调用 (`['child', 0]`, `['setText', 'x']`, `['bounds']`, `['hintText']`, 未知名字); 输出每条的结果形状 (类型名, 数组长度, 字符串值) 与错误类型, 在 23046RP50C 上记录为 `tools/selector/baseline/pick_table-<device>.json`. 这是 P1 重构 "行为不变" 的判据 (D6 (b) 允许的差异除外). (DEVICE) (`DEVICE` 2026-10-25: 23046RP50C 新层 on / off 各一张 3778 行, 两次运行逐行一致, `--check` 复核一致; 22120RN86C 同样两张作参照; 拼写实为 47 种; 记录了附录 C Q8 (showTooltip 崩溃) 与 Q9 (集合拼写的类初始化缺陷), 见附录 A P0.3; 脚本 ec389609c, 黄金表 895070a9f)
- [x] P0.4 JVM 安全网: 全量 `:app:testAppDebugUnitTest` 通过作为起点 (记录用例数); `CompassTest` / `CompassParserTest` / `PickResultTypeTest` / `SelectorRegistryTest` / `SelectorChecksTest` / `UiSelectorBuildersTest` / `SelectorExplainerTest` 为 P1 的回归集. (JVM) (`JVM` 2026-10-25, HEAD 25996d7d2: 949 个测试类 5808 例, 0 失败, 27 跳过; 7 个回归类均存在且全部通过, 共 54 例, 明细见附录 A)

### P1 PickRequest 模型 (D5, D6 (b), D11, D1 的预检合并)

- [x] P1.1 `core/automator/pick/PickRequest.kt` (Android-free 数据类: `root: UiNode?`, `selector: UiSelector`, `compass: List<CompassStep>`, `result: PickResultType`, `callback`) 与归一化器 `PickArguments`: 从 `pickup` (0-5 个参数, root 规则同现有: 两个参数起首个为 UiObject 即 root, 五个参数时首个总是 root) 与 `detect` (2-4 个) 的参数序列识别各位置 (与 `SelectorModule` 现有的下标法等价), 校验选择器形式 (字符串 / 正则 / UiSelector / 对象 / 数组 / 数字 / root; 对象键经 `SelectorRegistry.entry` 与 `SelectorEntry.resolve` 先判定), 罗盘 (`CompassParser.tryParse`), 结果类型 (符号或注册表方法, `detect` 无复数形式, `@` 不能带罗盘), 回调; 全部 "先判定再抛", 错误为带函数名与参数名的 TypeError / RangeError (附录 C Q5 已决: 新增 3-5 条专用资源, 10 种语言, 不复用 `ScriptMessages`), 途中不抛 Java 异常 (前序 R2). 旧入口 `UiSelector.pickup(scriptRuntime, root, selector, compass, resultType, callback)` 与 `UiObject.detect(...)` 保留签名, 内部构造 PickRequest. (SOURCE; JVM: `PickArgumentsTest` 覆盖 pickup 17 种与 detect 7 种重载形式及每类错误) (完成 2026-10-25, P1 子会话 local_dc7f552d: `PickRequest.kt` / `PickArguments.kt`, 新层严格模式在搜索前判定全部错误, 旧入口 `legacyPickup` / `legacyDetect` 保持原时机; `SelectorEntry.resolve`; `PickArgumentsTest` 8 例覆盖 17 + 7 种形式与各类错误; 提交 ff082119c)
- [x] P1.2 `core/automator/pick/NodeMethodRegistry.kt`: 按 D11 由反射一次生成 (`UiObject` / `UiObjectActions` 公开方法 + Compat 无参 getter, 排除 `equals` / `hashCode` / `toString` / `wait` / `notify*` / `getClass` / `recycle` 与 internal 混淆名), 每个条目: 名称与别名拼写, 重载的参数类型 (`int` / `float` / `boolean` / `String` / `CharSequence` / `Int`...), 调用器; 参数转换沿用 `JsArgumentCoercion` (NaN 取整先判定); 未知名字的 RangeError 附候选 (同名异大小写 / 前缀 / 编辑距离, 至多 5 个, 同 `SelectorStringParser`). 漂移测试: 注册表与 `UiObject::class.java.methods` 的差集只允许白名单. 现有拦截表 `Invoker.Interceptor.interceptedMap` 删除 (其 60 个名字全部在注册表内, 由测试断言). (SOURCE; JVM: `NodeMethodRegistryTest`) (完成 2026-10-25: 注册表 204 个名字 / 216 个方法 (自有 151 + compat getter 65); 原拦截表实为 49 个名字, 7 个在注册表中, 42 个 compat 方法按 D11 排除, 见附录 C Q10 (已按推荐先行待确认); Kotlin 把接口默认方法的转发方法标为 bridge, 只有协变桥接算 internal; 5 条 `error_pick_*` 资源 x 11 个目录 (Q5 (2)); `NodeMethodRegistryTest` / `PickMessagesTest` / `CompassTryParseTest`; 提交 d963bea6a)
- [x] P1.3 `core/automator/pick/PickExecutor.kt`: 执行 PickRequest: 搜索 (root 之下或窗口根, 单个 / 全部), 罗盘, 结果整形 (`PickResultType` 的符号表保留; `Invocation` 走注册表), 回调 (返回 `undefined` 则保留原结果), `javaToJS`. `Picker` / `Detector` / `Invoker` 删除, `UiSelector.pickup` / `UiObject.detect` / `A11yDiagnosticsApi` (`auto.explain` 的 selector 构建) / `RuntimeFlowPick` / `ToolkitBindings` / `FlowCondition` 改调 PickExecutor 或 PickArguments. Rhino 相关部分 (回调调用, `javaToJS`) 收在一个薄适配 (`PickResultAdapter`), 使整形逻辑在 JVM 上以 `FakeUiNode` 测试. (SOURCE; JVM: `PickExecutorTest` 在 FakeUiNode 树上覆盖全部结果类型 x 罗盘 x 空结果) (完成 2026-10-25: `PickExecutor.kt` / `PickResultAdapter.kt`, `Picker` / `Detector` / `Invoker` 删除, `RuntimeFlowPick` 只用 `PickResultType` / `Compass` 未改; `PickExecutorTest` 4 例; 与 P1.4 同一提交 065570b7c, 因副本依赖 `Invoker`)
- [x] P1.4 删副本 (D1): `SelectorChecks` 的重载匹配 / 取整 / 算法名 / 罗盘扫描并入 `SelectorEntry.resolve` (返回判定结果而非抛出), `UiSelector.useAlgorithm` (先查表), `CompassParser.tryParse`; `SelectorPatterns` 的正则语法预检迁入 `core/automator/filter/` (保留 "只拒绝 JVM 与 ICU 两种引擎都拒绝的写法" 的界限, `SelectorPatternsTest` 与 `tools/augment/device_patterns.py` 的误拒对照照跑); `SelectorForms` 的 `INVOKED` 表由 `NodeMethodRegistry` 取代; 新层 `SelectorModule` / `AutoModule.explain` / `AutoOptions` / `FlowArgs` / `FlowOptions` 改调 `PickArguments` 的判定; 三个副本文件删除, `SelectorChecksTest` 改为对 core 判定的直接测试. 完成后在 augment 看板待决区把跨主题通知行改为 "已完成 (提交 hash)". (SOURCE; JVM) (完成 2026-10-25: `SelectorForms` / `SelectorChecks` 删除, `SelectorPatterns` 迁为 `core/automator/filter/PatternPrecheck.kt` (ff082119c); 新层五处改调 `PickArguments`; `SelectorChecksTest` 改写为 `core/automator/selector/SelectorResolutionTest` 与 `pick/CompassTryParseTest`; `ScriptModuleArityTest` 改为 JVM 抽样 pickup / detect; augment 看板相应待决行已改为已完成; 提交 065570b7c)
- [x] P1.5 复核: 全量单测通过 (用例数不少于 P0.4); 23046RP50C 上 `pick_table.js` 与 P0.3 基线 (Q8 / Q9 修复后重录的两张表, 见附录 A P0.3 "基线重录"; 集合拼写不再是允许差异) 逐条一致, 允许的差异只有 D6 (b) (注册表外名字由 "未知结果类型" 的 IllegalArgumentException 改为 RangeError 并列候选) 与 P1.1 的错误类型 (IllegalArgumentException -> TypeError / RangeError), 差异逐条列入附录 A; augment 的 `tools/augment/device_eval.py --cases app/build/script-modules-device --new-modules on --strict-errors` 中 selector / auto / flow 的用例仍一致 (新层行为不变). 架构文档 1.4 的 "拾取" 段与 2.2 迁移映射更新. (DEVICE; DOCS) (完成 2026-10-25: 全量单测 5832 例 0 失败 (27 跳过); 23046RP50C 黄金表 on / off 的差异逐类见附录 A P1.5, 除允许的两类外只有 Q10 (D11 排除) 与 2 行修复; parity 100%; augment device_eval 383 例一致; 架构文档 1.1 / 1.2 / 1.4 / 2.2 已更新)

### P2 搜索效率 (D3)

- [x] P2.1 节点读取与预取: `UiObject` 每节点 bounds 只读一次 (四个 int 字段, `refresh()` / `snapshot()` 后失效), `left()` 等 8 个度量不再分配 `Rect`; `content` 条件读 desc / text 各一次; `A11yStats.recordSearch` 改为传入选择器对象或惰性字符串 (渲染报告时再 `toString()`, 或 `Selector` 缓存字符串到 filters 变化); API 33+ 取根节点与 `getChild` 使用深度优先预取策略 (`FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST | FLAG_PREFETCH_UNINTERRUPTIBLE`, BFS 时用 BREADTH_FIRST), API < 33 不变; `MODE_FAST` 下是否启用见附录 C Q6. 验收: `parity.js` 100% 一致; 基准 `full` / `miss` 在 API 35 设备上有可测的下降 (写入附录 A, 不设硬门槛, 但下降不足 10% 时在附录 A 说明原因). (SOURCE; JVM: UiObject 字段缓存无法在 JVM 构造, 用 FakeUiNode 覆盖 content 单次读取; DEVICE) (`DEVICE` 2026-10-25: 71b77597c; bounds 缓存, 选择器计划与文本缓存, 每次搜索取一次过滤器快照; 显式预取按附录 C Q12 (已先行) 不采用 (实测根节点每次慢 5-12 ms); JVM `ContentReadTest`; parity: 两台小米两块屏幕与 OPD2413 主界面对修前快照 100% (OPD2413 设置首页为 WLAN 扫描列表, 跨构建不可比, 见附录 A (7)); `full` / `miss` 变化在噪声内, 下降不足 10% 的原因见附录 A "P2 修后")
- [x] P2.2 过滤器排序与短路: `Filter` 增加 `cost` (布尔 / int / depth / className 等值 < 字符串等值 / 前后缀 / 子串 < bounds 与度量 < 正则 < `filter(fn)` 脚本函数 < `action` 列表), `Selector.filter(node)` 按 cost 稳定排序后的快照求值 (声明顺序不变: `toString()`, `auto.explain` 的逐过滤器计数与 "失败步骤" 仍按声明顺序统计); `BooleanFilter` / `IntFilter` 不变. 验收: `parity.js` 一致; `SelectorExplainerTest` 不变; 基准 `miss` (含正则条件的场景) 下降写入附录 A. (SOURCE; JVM: `SelectorCostOrderTest`; DEVICE) (`DEVICE` 2026-10-25: b0aac70f1; `SelectorCostOrderTest`, `SelectorExplainerTest` 未改并通过; `missMixed` 主界面 23046RP50C -37%, 22120RN86C -34%, OPD2413 -33%, 见附录 A "P2 修后")
- [x] P2.3 框架快路径: 选择器含精确 `id` (全名或短 id, 短 id 以根节点 packageName 拼全名) 或精确 `text` 条件时, 先用根节点的 `findAccessibilityNodeInfosByViewId` / `findAccessibilityNodeInfosByText` 取候选 (一次 IPC): 候选为空即判定未命中 (不遍历); 候选恰好 1 个且通过其余过滤器时直接返回 (单结果入口); 其余情况 (多候选, `find()` 全量, BFS, 带 `max` 的负数索引) 走完整 DFS 以保持顺序. 可靠性保障 (附录 C Q1): 提供方可能对文本搜索返回空 (自定义 `AccessibilityNodeProvider`), 因此未命中判定只用于轮询的中间尝试, 查询的最后一次尝试 (超时前) 与单次 `find()` / `findOnce()` 仍走完整 DFS 校正; `auto.setFlags(['noFastPath'])` 可关 (名字见 Q2). 验收: `parity.js` 一致; 基准 `miss` 场景 (轮询中间尝试) 的耗时与 `findOne(600)` 的 CPU 下降写入附录 A; 在 OPD2413 (ColorOS) 上补跑一次 (不同 ROM 的提供方行为). (SOURCE; JVM: FakeUiNode 增加候选查询接口以测试分派; DEVICE) (`DEVICE` 2026-10-25: cde3fee03 (快路径, `auto.clearFlags` / `'!name'`, `auto.stats.fastPath`) + 51ec78df2 (附录 C Q11 先行: 精确 `text` 不走快路径); 校正范围见 Q14 (已先行), `setFlags` 语义见 Q13 (已先行); JVM `FastPathTest`, `A11yStatsTest`, `AutomatorModulesTest`; parity 三台一致, 轮询入口的 7 个用例都等于同次 `find()`, 快路径不一致计数三台均为 0; `findOne600Id` (id 未命中的轮询) CPU 23046RP50C 设置首页 -37% / 主界面 -39%, 22120RN86C -26% / -47%, OPD2413 设置首页 -30% / 主界面 -34% (悬浮窗 +29%, Q15); `text` 的 `findOne600` 不变 (Q11); OPD2413 (ColorOS 15) 已补跑, 设置首页的根节点属于 `com.oplus.wirelesssettings`, 用新参数 `--settings-package` 测量)
- [x] P2.4 基准复测: `tools/selector/device_bench.py` 在 23046RP50C / 22120RN86C / OPD2413 各一次, 修前后对照表写入附录 A (中位数, 百分比); `auto.stats` 增加快路径命中 / 未命中 / 回退计数. 架构文档 1.9 追加本期基准. (DEVICE; DOCS) (2026-10-25: 三台修前后对照见附录 A "P2 修后" (修前为 af3987235 的包, 修后为 51ec78df2 的包, 同一命令同一开关); `auto.stats.fastPath` 的命中 / 未命中 / 回退 / 不一致计数 (cde3fee03); 架构文档 1.4 / 1.9 追加; 全量 `:app:testAppDebugUnitTest` 959 个类 5855 例 0 失败)

### P3 准确率 (D4, D6 (a) (c), D9, D10)

P3 与 P2 可由两张卡片并行: P2 只改 `UiObject.kt` (读取缓存), `search/**`, `filter/Selector.kt` 与各 Filter 的 cost, `UiSelector.findImpl` 快路径, `window/**`, `A11yStats`; P3 只改 `query/{PollPolicy,Poller}.kt`, `event/EventGate.kt`, `A11yEventPulse.kt`, `UiSelectorQuery.kt`, `UiSelector.query / findOnce`, `filter/{StringMatcher,StringProperty}.kt` 与 `SelectorRegistry` 的新条目, `pick/PickExecutor` 的候选偏好, `diagnostics/SelectorExplainer`. 共同文件 (`UiSelector.kt`, `AccessibilityBridge.kt` 的标志) 由先开始的卡片改, 后者等前者提交后再碰; 宿主构建在 `locks.md` 排队, 不同时编译 (Kotlin 守护进程并行编译会 GC 抖动). 2026-10-25 维护者定为顺序执行 (附录 C Q7), 并行描述只作文件分工参考.

- [x] P3.1 事件辅助轮询默认开 (D9): `PollPolicy.assisted()` 增加 `minSpacing = 50` (事件唤醒后距上次尝试不足 50 ms 则补睡), `EventGate` 合并连发信号 (已有 pending 位, 补 "一次 await 只消费一次"), `UiSelector.query` 与 `UiObject.waitUntilGone` / `waitForStable` / Flow 等待默认走 `bridge.pulse.poller` (服务未就绪或无 bridge 时退回固定间隔); 关闭方式按附录 C Q2 实现; `auto.setFlags(['eventAssistedPolling'])` 仍接受 (无操作). 验收: `EventAssistedPollingTest` 补最小间隔与合并用例; 基准 "事件风暴" 场景的遍历次数不多于固定 50 ms 的轮次, 安静屏幕 CPU 下降写入附录 A; `findOne(600)` 的尝试次数记录. changelog `improvement`. (SOURCE; JVM; DEVICE) (`DEVICE` 2026-10-26: 05c8264cb; `EventAssistedPollingTest` 补最小间隔与合并用例; `AccessibilityBridge.DEFAULT_FLAGS` 含 `eventAssistedPolling`, 关闭用 `clearFlags` / `'!eventAssistedPolling'` (Q2 / Q13); 真机改用 22120RN86C 与 OPD2413 (23046RP50C 被 api-coverage 占用): 事件风暴的尝试次数与固定 50 ms 持平 (三次运行 110-112 对 108.5-112, 121.5-122.5 对 124-125; P3.1 前事件辅助 186.5 / 182.5 次), 安静主界面 CPU 固定 50 ms 70.4-73.9% / 27.6-32% -> 默认 28.05-30.7% / 12.45-15.6%, `findOne(600)` 默认尝试 4 / 5-6 次; 见附录 A "P3 修后")
- [x] P3.2 命中后校验与过期重找默认开 (D6 (c)): 单结果入口 (`PickExecutor` 的单个结果, `UiSelector.findOnce` / `findOne(timeout)` / `untilFindOne`, `UiSelectorQuery.one`) 返回前对命中节点 `refresh()`; 失败 (节点已离开窗口) 则在剩余超时内重新搜索, 最多 2 次, 仍失败按未命中处理; `find()` / `findOnce(index)` 的全量结果不校验; `auto.setFlags(['noRefreshOnHit'])` 可关 (名字见 Q2). Node Bridge / Python Host 经 `UiSelector` 自动获得. 验收: FakeUiNode 增加 "刷新失败" 开关的用例; 真机用 "命中后立即关闭对话框" 的脚本验证重找 (23046RP50C); 基准 `hit` 的额外耗时 (预期 0.5-1 ms) 写入附录 A. changelog `improvement`. (SOURCE; JVM; DEVICE) (`DEVICE` 2026-10-26: 4acdced3d; `HitVerifierTest`, `A11yStatsTest`, FakeUiNode 的 "刷新失败" 开关; `exists()` 不校验 (Q19), 重找不等待 (Q20); 真机 `tools/selector/device_accuracy.py` 的 7 个 refind 用例 (过滤器在搜索中途把被问到的节点移出窗口, 代替 "命中后立即关闭对话框") 在 22120RN86C / OPD2413 新层开关 on / off 全部 PASS; `refresh()` 一次 22120RN86C 1.2-4.1 ms, OPD2413 0.16-0.44 ms (三次运行), 见附录 A "P3 修后")
- [x] P3.3 `normalize` (D10): `SelectorRegistry` 新增属性 `normalize` (重载: 无参 = 全部; 布尔; 选项对象 `{ trim, caseInsensitive, fullWidth, whitespace, nfkc }`, 选项集合与适用属性见附录 C Q4), 生成 `UiSelectorBuilders` (运行 `UiSelectorBuildersTest` 复制渲染), `StringMatcher` 增加 `Normalizer` (两侧规范化; 正则只规范化被匹配值), `StringProperty.test` 传入; 对象选择器与 `select('normalize=...')` 自动获得; `toString()` 输出 `normalize(...)`; `auto.explain` 的逐过滤器计数照常. 验收: JVM 用例覆盖五个选项与组合 (含 `textContains` / `textMatch` / `content`), 真机在设置首页用全角与大小写差异的文本命中一次. changelog `feature`. (SOURCE; JVM; DEVICE) (`DEVICE` 2026-10-26: 094f4b084; `NormalizeTest`, `SelectorRegistryTest`, `UiSelectorBuildersTest` 与 `selector-global-functions.txt`; 正则条件的大小写按 Q18 先行; 真机 `device_accuracy.py` 的 normalize 用例在设置首页取一段含 ASCII 字母的文本, 改写大小写, 首字母改全角并加空格, 不加 normalize 未命中, 加 `normalize()` 命中同一节点, normalizeObject 用例覆盖选项对象; 两台 PASS)
- [x] P3.4 多候选消歧 (默认关): `SelectorRegistry` 新增属性 `prefer(order...)` (键: `visible` / `clickable` / `enabled` / `center` (距屏幕中心近) / `area` (面积大) / `top` (更靠上), 顺序即优先级, 计分规则见附录 C Q3); 设置后单结果入口收集全部命中 (不再止于首个) 并按偏好排序取首个; 罗盘 `k` 段在 `prefer('visible')` 下遇不可见的可点击节点继续上溯. 验收: FakeUiNode 用例覆盖每个键与组合; 真机用两个同名按钮 (一个被遮挡) 的合成悬浮窗验证. changelog `feature`. (SOURCE; JVM; DEVICE) (`DEVICE` 2026-10-26: 094f4b084; `PreferenceTest` 覆盖每个键, 组合与平局保持找到的顺序; 真机 `device_accuracy.py` 的 5 个 prefer 用例 (合成悬浮窗中两个同名 TextView, 第一个移出窗口) 覆盖 findOnce / findOne / pickup / 罗盘 `k` 与对象选择器, 两台 PASS)
- [x] P3.5 诊断补全: `auto.explain(selector, compass?, resultType?)` 扩展: 命中节点后的罗盘每一步到达的节点 (或失败的步骤), 结果类型方法的解析 (注册表条目与参数转换), 快路径 / 重找 / 偏好排序是否生效; `auto.stats` 的查询项记录重找次数; 布局分析器 "复制为选择器" 增加 pickup 形式 (`pickup({...}, 'compass', 'result')`) 的输出选项. 验收: JVM 用例; 真机运行示例 "选择器诊断与节点树导出" 并核对输出. (SOURCE; JVM; DEVICE) (`DEVICE` 2026-10-26: 8bcb9969f; `PickExplainerTest` 5 例, `SelectorExportTest` 加 3 例, `AutomatorModulesTest` 的 explain 参数用例随之调整 (第二个参数不是 `UiObject` 时按罗盘 / 结果类型读取); 重找次数 `auto.stats` 的 `queries.refinds` 在 P3.2 完成; 示例 "选择器诊断与节点树导出" 增加第 4 节, `tools/selector/device_sample_explain.py` 运行示例并核对 19 项输出, 22120RN86C / OPD2413 PASS (22120RN86C 的无障碍树省略了 `vertical` LinearLayout, `p1` 到达窗口的 FrameLayout))

### P4 changelog, 文档, 声明, 示例

- [x] P4.1 changelog: `.changelog/lang_zh-Hans.json` 补 `feature` (normalize, prefer, 诊断), `improvement` (事件辅助默认开, 命中后校验, 快路径, 预取), `fix` (Invoker 参数类型), 生成 README; 其他语言由维护者同步 (记忆: 6.8.0 仅重写 zh-Hans); 该文件有他人未提交改动时只写草稿交维护者. (DOCS) (DOCS 2026-10-29: `.changelog/lang_zh-Hans.json`, `README.md`, `.readme/README-zh-Hans.md` 与 `CHANGELOG*.md` 仍有他人未提交的改动, 未改; zh-Hans 终稿 (新增 3 feature + 2 fix + 5 improvement, 改写 6.8.0 已有的 2 feature + 1 improvement) 写在会话记录 2026-10-29 本会话条目 "P4.1 changelog 终稿 (待维护者写入)"; 预取 (Q12 未采用) 不列)
- [x] P4.2 文档仓库 `AutoJs6-Documentation`: `api/uiSelectorType.md` 的 pickup / normalize / prefer 节, `api/uiObjectType.md` 的 detect 节, `api/dataTypes.md` 的 PickupResult (按注册表列出可用方法名) / DetectCompass, `api/automator.md` 的 setFlags 新标志与 explain 扩展; 运行 `generator/auto-generate-for-autojs6.bat`, 提交文档仓库与 Offline Docs 插件仓库 (按惯例). (DOCS) (DOCS 2026-10-29: 宿主 1842d7408 (注册表快照 `NodeMethodRegistrySnapshotTest` + `app/src/test/resources/node-method-registry.txt`, 206 个名称 / 218 个方法, 导出脚本 `tools/selector/registry_export.py`); 文档 d163293 (normalize / prefer 两节, findOnce / findOne / untilFindOne / pickup 的命中刷新, exists / find / findOnce(index) 不刷新 (Q19), 刷新失败立即重找 (Q20), 正则在 normalize 下的大小写 (Q18), 快路径在查找更贵的界面改遍历 (Q15 / Q17), pickup / detect 先查参数与只调注册表方法, `UiObjectInvokable` 的可调用方法清单由脚本生成, PickupResult 补 `##` / `$$` / `..` 与数组形式, setFlags 的 `!` 与默认标志, noFastPath / noRefreshOnHit, clearFlags, explain 新签名与字段, stats 的 refinds / fastPath) + 83c5368 (版本 169); 离线文档插件 774fb55 (`--verify-offline` 单测与 debug / release APK 门禁通过, VERSION_BUILD 207, 同时带上此前未打包的运行时支持表 / memory / speech 页); 字符串选择器 `prefer=a,b` 报错见附录 C Q21)
- [x] P4.3 d.ts 仓库 `AutoJs6-TypeScript-Declarations`: `aj6-int-selector.d.ts` 的 `Pickup.ResultType` 由 `any` 改为注册表派生的联合类型 (由 `NodeMethodRegistry` 导出清单, 生成脚本或测试输出), `normalize` / `prefer` 重载, `Auto.Flags` 新项; 同步 Ace Editor 插件的 `aj6-int-*.d.ts` 并运行 `:app:generateAutoJs6LspDeclarations`; 两个仓库版本号加一. (DOCS) (DOCS 2026-10-29: 声明 0c4b188 (4.84.0: `normalize` / `prefer` 的全局函数, 链式方法与对象选择器键, `Selector.NormalizeOptions` / `Selector.PreferKey`; `Detect.ResultTypeFuncWithoutParams` / `ResultTypeFuncWithParams` 由 `tools/selector/registry_export.py` 从注册表快照生成 (206 个名称, 每个带参重载一个元组), `Pickup.ResultType` 补 `'##'` / `'$$'` / `'..'` 与数组形式; `auto.setFlags` 的 `!` 前缀, `noFastPath` / `noRefreshOnHit`, `clearFlags`, `explain` 两个重载与新字段, `stats` 的 `queries.refinds` / `fastPath`; 新 fixture `docs/smoke/selector-smoke.ts`; changelog 10 种语言); Ace 5847c8b (2.46.0, VERSION_BUILD 336: 三份 `aj6-int-*.d.ts` 副本, 补全索引与 `lib.autojs6.d.ts` 重生成, `:app:generateAutoJs6LspDeclarations` + `:app:verifyAutoJs6LspRuntime` 通过); 宿主 d8941cada (补全源 `tools/ace-completion/autojs6_indices.source.json`) 与 ffdd8c25c (生成块的换行格式); `check.py` 里 `auto.clearFlags` 的文档 / 声明 / Ace 缺口已消除, 剩余失败只有 `speech.speaker.*` (3-Macaw 主题); `MATRIX.md` 重生成未丢行, 已提交 a709e83a7 (2729 -> 2739 行: `UiSelector#` 186 -> 188, auto 行 Ace 列 37 -> 38, 另含已入库的 `speech.speaker` 8 行))
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

### P0.1 修前基准 (2026-10-25)

- 构建: 源码 HEAD 25996d7d2 的 `:app:assembleAppDebug` (宿主 VERSION_BUILD 4283, versionCode 5935), `adb install -r` 到两台设备; 新脚本模块层开关 on (构建默认值, 运行时设置, 结束后恢复设备原值 off). 命令 `py -3.12 -I tools/selector/device_bench.py -s SERIAL --new-modules on --save`, 两台各一次, 三场景共 36 项全部 PASS; 汇总与各轮样本在 `tools/selector/baseline/bench-<model>.json`.
- 口径: 计时项每项预热 5 次后计 10 轮, 取中位数 (ms); `findOne(600)` 为 `text('__aj6sel_none__').findOne(600)` 的尝试次数 (`auto.stats.queries.attempts`) 与耗时; CPU 为 10 s 轮询 (`findOne(10000)`, 10 轮, 空闲下限 3 轮) 期间宿主进程的 CPU 占一个核的百分比 (`Process.getElapsedCpuTime`), 括号内为 10 s 内的尝试次数. 悬浮窗场景经窗口过滤器 `{ packageName: AutoJs6 }` 搜索, 每次查询都枚举窗口, 因此连 `hit` 也在 10 ms 以上. 调度组 (PCY) 在每个场景前后不变: 设置首页 fg, 主界面 ta, 悬浮窗 fg.

| 设备 | 场景 (节点数) | PCY | full / miss / missId / missMixed / hit / late (ms) | findOne(600) 尝试 / ms | CPU 固定 50 ms (尝试) | CPU eventAssistedPolling (尝试) | CPU 空闲 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 23046RP50C API 35 | 设置首页 (66) | fg | 8.69 / 5.96 / 5.81 / 7.55 / 4.74 / 6.00 | 12 / 630 | 16.4% (166) | 5.5% (53) | 0.2% |
| 23046RP50C API 35 | AutoJs6 主界面 (710) | ta | 9.48 / 2.57 / 2.94 / 5.91 / 0.78 / 2.90 | 11 / 650 | 47.9% (144.5) | 19.1% (47) | 0.4% |
| 23046RP50C API 35 | 悬浮窗 300 TextView (303) | fg | 17.64 / 19.82 / 19.99 / 24.28 / 20.10 / 17.31 | 9 / 647 | 33.5% (116) | 12.7% (44) | 0.2% |
| 23046RP50C API 35 | 悬浮窗 + 事件风暴 (57.7 次更新/s) | fg | - | - | 90.2% (137) | 91.8% (192) | 81.2% |
| 22120RN86C API 33 | 设置首页 (42) | fg | 5.38 / 2.74 / 3.19 / 3.73 / 2.31 / 2.76 | 12 / 653 | 16.0% (174) | 4.7% (50) | 0.1% |
| 22120RN86C API 33 | AutoJs6 主界面 (628) | ta | 12.92 / 5.89 / 6.91 / 14.86 / 2.68 / 7.57 | 9 / 642 | 74.4% (122) | 31.3% (43) | 0.3% |
| 22120RN86C API 33 | 悬浮窗 300 TextView (303) | fg | 23.75 / 12.93 / 10.34 / 13.43 / 12.08 / 11.10 | 8 / 639 | 30.9% (120) | 11.4% (45) | 0.2% |
| 22120RN86C API 33 | 悬浮窗 + 事件风暴 (57.9 次更新/s) | fg | - | - | 109.9% (110.5) | 120.7% (187) | 92.3% |

- 项目含义 (详见 `tools/selector/README.md`): `miss` = `text` 未命中, `missId` = `id` 未命中 (P2.3 快路径的入口), `missMixed` = 正则声明在 `clickable(true)` 之前的未命中 (P2.2 排序), `hit` = 根节点早命中, `late` = DFS 顺序中最后一个唯一文本 (单候选晚命中). P2.1 / P2.2 / P2.3 / P2.4 的修后数据按同一命令, 同一开关状态与同一场景追加在本节之后.
- 读数: (1) 固定 50 ms 轮询在静态屏幕上 10 s 做 116-174 次全树遍历, 宿主 CPU 16-74%; 事件辅助轮询降到 4.7-31%, 尝试 43-53 次. (2) 事件风暴下 (进度条与标签每 16 ms 更新) 事件辅助轮询的尝试反而多于固定间隔 (192 对 137, 187 对 110.5), CPU 也更高, 这正是 D9 的 50 ms 下限与事件合并 (P3.1) 要消除的; 风暴场景的 CPU 大部分是悬浮窗自身的绘制 (空闲下限 81-92%), 比较时看与 `stormIdle` 的差值与尝试次数. (3) 主界面 `miss` 低于 `full`: `findOnce` 不构造集合, 且两台设备主界面的子树多由客户端缓存供给. (4) 与架构文档 1.9 (2026-09-06, P5.5 探针) 的数值不可直接比较 (构建, 探针与调度组不同), P2 / P3 只与本节比较.
- 设备状态: 23046RP50C 的宿主无障碍服务原本未开启, 22120RN86C 上在 P0.3 调试中宿主崩溃后被系统移出已启用列表 (见 P0.3), 两台均由维护者在手机上开启后测量.

### P0.2 结果对照快照 (2026-10-25)

- 同一构建与开关状态 (build 4283, 新层 on), `py -3.12 -I tools/selector/device_parity.py -s SERIAL --new-modules on --save` 两台各一次, 每台 2 块屏幕 x 2 次运行: 23 个用例逐节点一致率 100% (两台 4 块屏幕全部 PASS); 23046RP50C 另以 `--runs 1 --check` 对快照复核一次, 100%.
- 快照: `tools/selector/baseline/parity-23046RP50C-main.json` / `-settings.json`, `parity-22120RN86C-main.json` / `-settings.json` (节点记为 `[fingerprint(), depth(), indexInParent()]`, 从屏幕推出的字面值只存哈希, 不含屏幕文本). P2.1-P2.3 的验收命令: 同一设备, 同一开关状态下 `device_parity.py -s SERIAL --new-modules on --runs 1 --check`; 屏幕内容变了 (主界面文件列表, 设置首页的账号或电量文字) 时字面值哈希会报 "the screen changed", 需在修前构建上重新 `--save` 后再比.
- 各用例结果条数 (23046RP50C 主界面 710 节点 / 设置首页 66 节点; 22120RN86C 628 / 42): `full` 与 `fullBfs` 等于节点数; `idExact` = `idShort` 16 / 19, 5 / 12; `idMatch` 49 / 20, 20 / 15; `textExact` 136 / 1, 136 / 1; `textContains` 136 / 1, 136 / 2; `textMatch` 159 / 14, 146 / 2; `descMatch` 176 / 1, 155 / 1; `classClickable` = `objectPickup` = `selectSyntax` 138 / 21, 138 / 12; `boundsInside` 45 / 22, 34 / 12; `depthRange` 44 / 60, 33 / 38; `leaves` (`filter` 函数) 379 / 42, 315 / 26; `objectPickupArray` = `classBfs` = `untilFind` 418 / 21, 418 / 12; `mixedArray` 197 / 26, 161 / 18; `contentPickup` 136 / 1, 136 / 1; `findOnceIndex` / `findOnce` / `pickupOne` 各 1.
- 用例 `objectPickup` 用 `'[]'` 而非 `'{}'`: 集合拼写的解析取决于进程里谁先用到 `PickResultType` (附录 C Q9), 对照只关心搜索顺序, 不依赖结果类型表.

### P0.3 pickup / detect 黄金表 (2026-10-25)

- `py -3.12 -I tools/selector/device_pick_table.py -s 968e9f18 --new-modules on|off --runs 2 --save`: 在脚本自建的合成悬浮窗 (13 个节点, 文本以 `aj6pt` 开头) 上每张表 3778 行, 两次运行逐行一致 (开关 on / off 各一张). 基线 (P1.5 的判据): `tools/selector/baseline/pick_table-23046RP50C.json` (新层 on, 6.8.0 默认) 与 `pick_table-23046RP50C-legacy.json` (开关 off, 旧 augment 层); 参照: 22120RN86C 的同样两张表 (`pick_table-22120RN86C.json` / `-legacy.json`, 同样稳定), 其进程的 `PickResultType` 初始化正常 (见下).
- 行数 (分节: 行数, 新层 on 的错误行数): `types` 1770 行, 372 (47 种拼写 + 12 种非拼写参数 = 59 x 罗盘 5 x 选择器 3 (一个命中 / 三个命中 / 无命中) x 回调 2); `detect` 1180, 848 (59 x 5 x (行节点 / null) x 2); `forms` 280, 88 (40 种选择器形式 x 7: 6 种结果类型与单参数); `roots` 21, 5; `arity` 64, 25; `callbacks` 10, 2; `invoke` 80, 24 (拦截表内外的参化调用, 含 EditText 上的 `setText` / `setSelection`); `sweep` 365, 0 (`UiObject` 公开无参方法 187 个, 命中 178 个 + 无命中 187 个, 命中时跳过 9 个危险动作, 见下); `exists` 8, 1. 开关 off 的错误行数: `types` 232, `detect` 708, `forms` 81, 其余同上 (旧层的错误均为 InternalError 包装, 新层为 TypeError / RangeError).
- 拼写数: `PickResultType` 的别名表实为 47 种拼写 (`#` 3, `{}` 8, `[]` 8, `$` 3, `contents` 8, `.` 3, `points` 9, `@` 3, `?` 2), 本 Roadmap P0.3 原文的 "43 种" 有误, 以 47 为准.
- 进程状态 (附录 C Q9): 黄金表首行之前记录 `PickResultType.Companion.of('{}')` 的解析结果 (`meta.collectionSpelling`). 23046RP50C 的宿主进程在本次测量中先由新层 `SelectorForms` 用到该类 (P0.2 的首轮 `pickup(..., '{}')`), 8 种集合拼写在整个进程内解析为 `Invocation`: 两张基线表里这些拼写的 `types` / `detect` 行 (400 行) 与 `forms` 的 `'{}'` 列都是错误 (新层 RangeError "需要 PickupResult", 旧层 "未知结果类型"); 22120RN86C 的进程先经旧层用到该类, 解析正常 (`WidgetCollection`), 集合拼写返回集合. 两台之间的其余差异是设备几何 (坐标, bounds 246 行), 错误消息的语言 (平板中文, Redmi 英文) 与无命中时 `'{}'` 的 null / 空数组 (同为 Q9). Q9 已由 0fb69ecbe 修复, 23046RP50C 的两张基线已按修复后的状态重录 (见下方 "基线重录").
- 发现 (附录 C Q8): 首次运行时 `sweep` 在命中节点上调用了 `showTooltip`, 框架 `View.showLongClickTooltip` 在没有 tooltip 的 View 上空指针, 在主线程抛出使 AutoJs6 自身崩溃 (22120RN86C, API 33, 显示 "AutoJs6 crashed" 页), MIUI 随后把宿主与两个插件 (accessibilitycompat, three.adapt.a11y) 的无障碍服务移出已启用列表, 由维护者在手机上恢复. 脚本此后在命中节点上跳过 `showTooltip` / `hideTooltip` / `dragStart` / `dragDrop` / `dragCancel` / `pressAndHold` / `showTextSuggestions` / `imeEnter` / `paste` (只在无命中时按名称调用). `getWindowId` 每次新建悬浮窗都变, 表中只记类型; 错误消息只留首行并把对象哈希改为 `@*`.
- P1.5 的验收命令: `device_pick_table.py -s 968e9f18 --new-modules on --check` 与 `--new-modules off --check`, 输出按分节的差异行数与行 id; 允许的差异逐条列入本附录 (D6 (b), P1.1 的错误类型). 集合拼写已在 P1 前修复并重录基线 (见下), 不再是允许差异.

#### 基线重录 (2026-10-25, Q8 / Q9 修复后)

- 构建: 修复提交 0fb69ecbe 的 `:app:assembleAppDebug` (宿主 VERSION_BUILD 4283, versionCode 5935, 构建号与 P0 相同), `adb install -r` 到 23046RP50C; 22120RN86C 未重装 (其参照表的初始化本来正常).
- 对照 (重录前, `--check`, 两次运行均逐行一致): 新层 on 3506 / 3778 行与原基线一致, 272 行不同, 全部是 8 种集合拼写的行: `types` 240 (8 x 罗盘 5 x 选择器 3 x 回调 2), `forms` 31 (`'{}'` 列), `roots` 1, 原为 RangeError "需要 PickupResult", 现为数组 (`types` 错误行 372 -> 132, `forms` 88 -> 57); 开关 off 3346 / 3778 行一致, 432 行不同, 同样全部是集合拼写行: `types` 240 / `forms` 31 / `roots` 1 由 InternalError "未知结果类型" 或 null 变为数组, `detect` 160 (单个节点没有复数结果) 由 "未知结果类型" (行节点) / null (null 节点) 变为 "结果类型 不适用于 UiObject" 的 InternalError. Q8 相关行无差异 (`sweep` 在命中节点上仍跳过 `showTooltip` 等 9 个动作, 无命中行不变); 其余行全部一致. `meta.collectionSpelling` 由 `Invocation` 变为 `WidgetCollection`.
- 与参照核对: 两张新表中集合拼写的 441 行 (含 `detect` 的复数拒绝行) 分别与 `pick_table-22120RN86C.json` / `-legacy.json` 逐行一致 (错误行只比错误类型, 两台设备的消息语言不同).
- 重录: `--new-modules on --save` 与 `--new-modules off --save` (各两次运行逐行一致), 覆盖 `tools/selector/baseline/pick_table-23046RP50C.json` 与 `-legacy.json`; 这两张表是 P1.5 的判据. 设备上的新层开关结束后恢复为原值 off.
- Q8 真机确认 (一次性探针, 见会话记录): 无 tooltip 的 Button 上 pickup / detect / 节点 / 选择器 / 集合 7 条路径的 `showTooltip` / `hideTooltip` 都返回 false, 宿主进程不变; 有 tooltip 的 Button 上照常显示与隐藏.

### P0.4 JVM 起点 (2026-10-25)

- 源码 HEAD 25996d7d2 (宿主 VERSION_BUILD 4283, versionCode 5935), `.\gradlew.bat :app:testAppDebugUnitTest` 通过 (1 min 56 s): 949 个测试类, 5808 例, 0 失败 / 0 错误, 27 跳过. P1.5 的 "用例数不少于 P0.4" 以 5808 为准.
- P1 回归集 (类, 用例数): `pick.CompassTest` 9, `pick.CompassParserTest` 8, `pick.PickResultTypeTest` 6, `selector.SelectorRegistryTest` 14, `modules.selector.SelectorChecksTest` 7, `selector.UiSelectorBuildersTest` 3, `diagnostics.SelectorExplainerTest` 7, 共 54 例, 全部通过.

### P1.5 复核 (2026-10-25, P1 子会话 local_dc7f552d)

- 单测: `:app:testAppDebugUnitTest` 全量 5832 例 (P0.4 的 5808 + Q8 / Q9 小卡 6 + P1 新增), 27 跳过; 首次全量只有 `ScriptModuleArityTest` 1 例失败 (pickup / detect 的个数检查移入归一化器, 源码扫描找不到), 改为 JVM 抽样后该类 2 例通过, 其余 0 失败. P0.4 的回归集: `CompassTest` 9, `CompassParserTest` 8, `PickResultTypeTest` 8, `SelectorRegistryTest` 14, `UiSelectorBuildersTest` 3, `SelectorExplainerTest` 7 通过; `SelectorChecksTest` 7 例由 `SelectorResolutionTest` 4 例与 `CompassTryParseTest` 2 例取代 (逐串对照的范围不变). Node Bridge / Python Host 的选择器用例期望值未改, 全部通过.
- 构建: `:app:assembleAppDebug` (宿主 VERSION_BUILD 仍为 4283, versionCode 5935; 源码即提交 d963bea6a / ff082119c / 065570b7c), `adb install -r` 到 23046RP50C (968e9f18), 安装时间 14:55.
- 黄金表, 新层 on (`device_pick_table.py --new-modules on --check`): 两次运行 3778 / 3778 行一致; 对基线 `pick_table-23046RP50C.json` 2677 / 3778 行一致, 1101 行差异, 逐类如下 (分类脚本: 调度会话共享笔记目录 `selector-p1/classify_p1.py`, 两次运行的原始结果也在该目录):
  - (a) 1086 行错误名相同, 只有消息不同: 新层消息改为 `error_pick_*` 专用资源 (Q5 (2)), 带函数名与参数名, 未知结果类型列候选. 分节: types 132, detect 848, forms 57, roots 4, arity 25, invoke 19, exists 1.
  - (b) 1 行错误类型 (P1.1): `invoke|38|one|['compass', 'zz']` JavaException (`Invoker$IllegalResultTypeException`) -> RangeError (注册表在搜索前判定罗盘参数).
  - (c) 8 行 Q10 (D11 排除的 compat 方法, 改为 RangeError 并列候选): `invoke|31` `['getChild', 0]` (原返回 compat 节点), `invoke|44` `['findAccessibilityNodeInfosByText', 'aj6pt']` (原返回列表), `invoke|45` `['setChecked', true]` (原 JavaException), `invoke|46` `['setClickable', 'x']` (原 RangeError, 消息不同), `sweep|one|refresh`, `sweep|one|unwrap`, `sweep|none|refresh`, `sweep|none|unwrap`.
  - (d) 4 行 Q10 (D11 只收 compat 的 `getXxx` / `isXxx`): `sweep|one|canOpenPopup`, `sweep|one|hasRequestInitialAccessibilityFocus` (原 boolean), `sweep|none|` 两者 (原 null), 现为 RangeError. 已恢复 (Q10 选 (2), af3987235; 见下方 "P2 基线").
  - (e) 2 行修复: `invoke|54|one|['sibling', 1]`, `invoke|55|one|['offset', 1]` 由 RangeError 变为返回控件 (原 `Invoker` 对拦截表外的带参方法用错参数类型, 注册表按重载取参数类型).
- 黄金表, 新层 off (`--new-modules off --check`, 对基线 `pick_table-23046RP50C-legacy.json`): 两次运行一致; 2784 / 3778 行一致, 994 行差异:
  - (a) 984 行仍为 InternalError, 只有包装内的消息不同: 旧为 IllegalArgumentException 的消息, 新为 TypeError / RangeError 的消息 (同新层, 并带脚本位置), 即 P1.1 错误类型改变在旧层的表现. 分节: types 104, detect 788, forms 57, roots 3, arity 13, invoke 18, exists 1.
  - (c) 6 行 Q10: `invoke|31` / `|44` / `|45` / `|46` 与 `sweep|one|refresh` / `unwrap`; 无命中的 `sweep|none|` 行不变 (仍为 null, 旧入口在找到节点时才拒绝方法).
  - (d) 2 行 Q10: `sweep|one|canOpenPopup` / `hasRequestInitialAccessibilityFocus`. 已恢复 (Q10 选 (2), af3987235).
  - (e) 2 行修复: `['sibling', 1]` / `['offset', 1]`.
- 结论: 允许的差异 (D6 (b), P1.1 错误类型, 含 Q5 (2) 带来的消息变化) 之外, 只有 Q10 (D11 的排除范围, 已按推荐先行待确认; 若改选会使 (c) / (d) 恢复) 与 (e) 修复; 没有其他差异. 基线未重录 (P2 起对照时以本节分类为准, 或由调度会话决定是否用 P1 后的结果重录).
- `device_parity.py --new-modules on --runs 1 --check`: main / settings 两个场景节点匹配 100%.
- augment `device_eval.py --new-modules on --strict-errors`, 用例文件 `SelectorModulesTest` / `SelectorFormsTest` / `FlowModulesTest` / `FlowOptionFieldsTest` / `AutomatorModulesTest` / `AutomatorFieldsTest` (selector / auto / flow 相关的 6 个, 由本次全量单测导出): 383 例与 JVM 一致, 0 不同.
- `tools/augment/device_patterns.py -s 968e9f18 --samples 20000` (正则预检迁到 `PatternPrecheck` 后): corpus 1538 / strings 20000 / built 20000 三组 0 误拒.

### P2 基线 (2026-10-25, P2 子会话 local_6fb6e128)

- 构建: HEAD af3987235 (P1 + Q10 返工) 的 `:app:assembleAppDebug` (宿主 VERSION_BUILD 仍为 4283, versionCode 5935), `adb install -r` 到 23046RP50C 与 OPD2413 (18:20); 22120RN86C 当时在 `locks.md` 中被 api-coverage P1.2 占用, 未重装. 修前包另存一份供 OPD2413 / 22120RN86C 的修前测量.
- 黄金表重录 (P2 / P3 "行为不变" 的新基线): `device_pick_table.py -s 968e9f18 --new-modules on --check --save` 与 `off --check --save`, 各两次运行逐行一致. 与 P1 前的基线相比: 新层 on 2681 / 3778 行一致, 1097 行不同 = P1.5 的 (a) 1086 + (b) 1 + (c) 8 + (e) 2, (d) 类 4 行 (`canOpenPopup` / `hasRequestInitialAccessibilityFocus`) 已与旧表一致; 开关 off 2786 / 3778 行一致, 992 行不同 = (a) 984 + (c) 6 + (e) 2, (d) 类 2 行已一致. 没有其他差异. 覆盖保存 `baseline/pick_table-23046RP50C.json` / `-legacy.json`; P2 / P3 的 `--check` 以这两张为准, 差异即为回归.
- 基准与对照的修前数据 (计划): 23046RP50C / 22120RN86C 沿用 P0.1 / P0.2 (build 4283, 源码 25996d7d2). 实际做法: 三台都用本节的修前包重测 (bench 新增 `findOne600Id` / `lateOne` 与每次尝试的 CPU, P0 没有), 见下节 "P2 修后". P1 与 Q10 只改 pick 包, 新层的参数判定与注册表, 不改搜索路径 (`UiSelector.findImpl`, `search/**`, `filter/**`, `UiObject` 的读取), P1.5 的 parity `--check` 也是 100%, 因此 P2 的修后数据与 P0.1 / P0.2 可比 (构建号相同, 源码不同). OPD2413 无 P0 数据, 用本节的修前包补测 (见下节).

### P2 修后 (2026-10-25, P2 子会话 local_6fb6e128)

- 构建: 修前为 af3987235 的 `:app:assembleAppDebug` (见上节), 修后为 51ec78df2 (P2.1-P2.3 + Q11 先行) 的 `:app:assembleAppDebug`; 两者 VERSION_BUILD 都是 4283 (versionCode 5935), `adb install -r` 交替安装. 命令与口径同 P0.1 (`device_bench.py -s SERIAL --new-modules on --save --label p2pre` / `p2`), 文件 `baseline/bench-<model>-p2pre.json` / `-p2.json`; OPD2413 加 `--settings-package com.oplus.wirelesssettings` (ColorOS 15 设置首页的活动窗口根节点属于该包), 设置首页另以 `--settle 20` 重测一对存 `-p2pre-settings.json` / `-p2-settings.json`, 表中 OPD2413 的设置首页取这一对 (见 (7)). 修前测量: 23046RP50C 18:34, 22120RN86C 18:53, OPD2413 20:21 (设置首页 `--settle` 一对 20:59); 修后: 19:30, 19:31, 20:38 (21:04).
- 新项: `findOne600Id` = `id('aj6sel_none').findOne(600)` (id 未命中的轮询, P2.3 快路径的入口), `lateOne` = `text(<late 的文本>).findOne(600)`; 两者与 `findOne600` 记录 10 轮的 CPU 中位数 (ms) 与每次尝试的 CPU. 最后一列是修后 `findOne600Id` 10 轮累计的 `auto.stats.fastPath` (未命中 = 中间尝试不遍历; 回退 = 第一次 / 最后一次 / 校验的完整遍历; 不一致 = 查找为空而遍历找到).

| 设备 | 场景 (节点) | PCY | full / miss / missId / missMixed / hit / late (ms): 修前 -> 修后 | `findOne600` (text) CPU ms | `findOne600Id` CPU ms (每次尝试 ms) | `lateOne` 耗时 ms | CPU 固定 50 ms / 事件辅助 | `findOne600Id` 快路径 未命中 / 回退 / 不一致 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 23046RP50C API 35 | 设置首页 (66) | fg | 5.736 / 2.375 / 3.588 / 4.626 / 1.708 / 2.047 -> 5.477 / 3.003 / 3.142 / 4.596 / 1.455 / 3.226 | 65.5 -> 64.5 (-2%) | 118 (10.727) -> 74 (7.2) (-37%) | 4.758 -> 3.652 | 16.3% -> 16.35% / 5.4% -> 5.3% | 76 / 25 / 0 |
| 23046RP50C API 35 | AutoJs6 主界面 (710) | ta | 4.979 / 3.104 / 2.655 / 6.645 / 0.481 / 2.8 -> 4.82 / 2.815 / 2.565 / 4.169 / 0.517 / 2.596 | 247 -> 235.5 (-5%) | 264.5 (24.046) -> 161 (15.759) (-39%) | 7.276 -> 5.974 | 49% -> 50.7% / 18.4% -> 18.9% | 79 / 26 / 0 |
| 23046RP50C API 35 | 悬浮窗 300 TextView (303) | fg | 16.959 / 18.996 / 18.955 / 22.758 / 18.306 / 16.423 -> 17.56 / 17.005 / 16.101 / 20.901 / 19.961 / 15.298 | 175.5 -> 179.5 (+2%) | 197.5 (23.612) -> 194 (21.556) (-2%) | 18.068 -> 14.423 | 31.65% -> 33.05% / 11.95% -> 13% | 66 / 24 / 0 |
| 22120RN86C API 33 | 设置首页 (42) | fg | 6.105 / 3.157 / 3.967 / 4.868 / 2.957 / 3.125 -> 6.072 / 3.484 / 3.188 / 3.947 / 2.811 / 7.504 | 137.5 -> 135 (-2%) | 126.5 (11.008) -> 93.5 (10.535) (-26%) | 6.675 -> 7.217 | 17.1% -> 17.4% / 4.85% -> 4.75% | 63 / 26 / 0 |
| 22120RN86C API 33 | AutoJs6 主界面 (628) | ta | 10.468 / 6.776 / 7.152 / 15.586 / 2.329 / 6.541 -> 26.002 / 7.446 / 7.89 / 10.228 / 2.716 / 6.272 | 506.5 -> 456 (-10%) | 447 (49.667) -> 235 (26.111) (-47%) | 10.747 -> 10.588 | 77.8% -> 70.4% / 32.3% -> 28.75% | 71 / 20 / 0 |
| 22120RN86C API 33 | 悬浮窗 300 TextView (303) | fg | 14.808 / 11.721 / 12.917 / 14.585 / 11.588 / 11.233 -> 17.826 / 11.997 / 11.337 / 11.378 / 11.497 / 11.943 | 223.5 -> 223.5 (+0%) | 209 (26) -> 245 (34.215) (+17%) | 28.201 -> 28.216 | 32.3% -> 31.8% / 12.2% -> 11.95% | 50 / 21 / 0 |
| OPD2413 API 35 | 设置首页 (122) | fg | 3.574 / 1.341 / 1.369 / 1.056 / 0.388 / 1.006 -> 3.631 / 1.328 / 1.096 / 0.873 / 0.5 / 1.107 | 84.5 -> 92.5 (+9%) | 76.5 (6.375) -> 53.5 (4.864) (-30%) | 1.656 -> - | 14.25% -> 14.35% / 5.55% -> 5.4% | 90 / 25 / 0 |
| OPD2413 API 35 | AutoJs6 主界面 (151) | ta | 2.883 / 0.68 / 0.889 / 1.64 / 0.277 / 0.781 -> 3.111 / 0.749 / 1.055 / 1.091 / 0.269 / 0.647 | 176 -> 187 (+6%) | 178.5 (14.603) -> 118.5 (9.875) (-34%) | 1.687 -> 1.503 | 28.05% -> 27.6% / 12.9% -> 12.7% | 90 / 30 / 0 |
| OPD2413 API 35 | 悬浮窗 300 TextView (305) | fg | 5.003 / 1.221 / 1.237 / 3.04 / 0.827 / 1.562 -> 4.967 / 1.71 / 1.35 / 1.837 / 0.94 / 1.624 | 84 -> 85 (+1%) | 75 (6.727) -> 97 (9.141) (+29%) | 4.34 -> 2.989 | 18.1% -> 18.25% / 7.35% -> 7.05% | 83 / 26 / 0 |

- 读数:
  - (1) P2.3: id 未命中的轮询 (`findOne600Id`) 每次查询的宿主 CPU 在设置首页与主界面下降 26-47% (OPD2413 -30% / -34%), 中间尝试以一次 `findAccessibilityNodeInfosByViewId` 代替全树遍历, 第一次与最后一次尝试仍遍历; 悬浮窗场景无收益或更费 (23046RP50C -2%, 22120RN86C +17%, OPD2413 +29%, 原因见 Q15): 宿主自己的悬浮窗上两次 id 查找比一次遍历贵约 4 倍 (OPD2413 探针 7.5 vs 1.8 ms, 见 (7) (c)), 登记 Q15. 三台 parity 的轮询用例与 bench 中快路径不一致计数均为 0.
  - (2) Q11: `text` 条件 (`findOne600`, `cpuFixed` / `cpuAssisted` 的轮询都用 `text`) 不走快路径, 修前后在噪声内. 含 `text` 快路径与 Q12 原预取的中间构建 (23046RP50C, 19:00, 输出在调度会话共享笔记目录 `selector-p2/`) 上 `cpuFixed` 主界面 49% -> 36.4%, 设置首页 16.3% -> 10.45%, 悬浮窗 31.65% -> 28.6% (该构建的根节点预取每次多 5-12 ms, 去掉后降幅会更大); 这是 Q11 改选 (2) 的收益.
  - (3) P2.2: `missMixed` (正则声明在 `clickable(true)` 之前) 主界面 -37% (23046RP50C) / -34% (22120RN86C), 22120RN86C 设置首页 -19%, 悬浮窗 -8% / -22%; 23046RP50C 设置首页 -1% (66 个节点, 正则本身便宜). OPD2413: 主界面 -33%, 设置首页 -17% (未加 `--settle` 的一对 -44%), 悬浮窗 -40%.
  - (4) P2.1: `full` / `miss` / `hit` / `late` 在噪声内, 下降不足 10%. 原因: 子节点多由服务端缓存供给 (由事件维持), 遍历的瓶颈不是读取次数; bounds 缓存只在 bounds 条件与度量中起作用 (bench 无此项), 选择器文本缓存只省微秒; 原方案的显式预取反而更慢 (Q12). 22120RN86C 主界面 `full` 10.5 -> 26.0 ms 与两台设置首页 `late` 变慢 (23046RP50C 2.0 -> 3.2 ms, 22120RN86C 3.1 -> 7.5 ms) 经 A/B 复测 (同屏同脚本交替安装修前 / 修后包, 各 20 轮): `full` 修后 11.1 / 14.9 ms, 修前 15.4 ms; `late` 修后 3.8 / 9.5 ms, 修前 9.8 / 7.9 ms, 波动大于版本差, 判为设备状态, 不是回归.
  - (5) 框架查找的覆盖 (parity 的 `coverage`, 修前修后相同): 主界面 23046RP50C / 22120RN86C 文本漏查 143 / 191, 143 / 164 (漏查节点按类名 TextView 140, WebView / Button / View 各 1, 即文档 WebView 的内容), 设置首页各漏 1 个普通 TextView; 完整 id 与短 id 0 漏查 (27 + 9, 22 + 8 个), 主界面另有 6 个不含 `:id/` 的 id (WebView 的 HTML id, 见 Q14). OPD2413: 主界面文本 39 个 / id 40 个, 设置首页文本 19-20 个 / id 23 个, 全部 0 漏查 (OPD2413 的主界面未停在文档页). 据此登记 Q11.
  - (6) parity: 两台小米两块屏幕与 OPD2413 主界面对快照逐节点 100% (OPD2413 主界面为本期修前包存的快照, 设置首页见 (7)); 黄金表 23046RP50C on / off `--check` 各两次运行与 P2 基线 3778 / 3778 行一致 (含预取的中间构建有一次 on 运行的第二遍在 EditText `setText` 后读 `text` 的 1 行不同, 修后构建未再出现).
  - (7) OPD2413 (ColorOS 15, API 35): 设置首页是平板双栏, 活动窗口为右栏的 WLAN 列表 (id 有 `signal` / `ll_title` 各 11 个, `tv_frequency`, `scan_entry`), 条目随扫描增减与重排 (探针: 打开后 10-15 s 插入 1 个节点, DFS 顺序约从第 96 个节点起变化). 因此: (a) 对修前快照的 parity 无效 (修后 91.2% / 86.2%, 节点数相同或差 1, 工具判 "屏幕上取的字面量不同"), 同一构建两次运行 100%, 轮询用例 7 个都等于同次 `find()`, 不存设置首页快照; 跨构建的顺序与数量以主界面 (100%) 与两台小米为准. (b) 修后两次 bench 的 `lateOne` 失败 (修前两次通过): 最后一个唯一文本是列表底部的某个网络名, 测量中途从扫描结果中消失; 修后包上的探针在同一屏幕连续 46 次 `text(<该文本>).findOne(600)` 全部命中 (含与 bench 相同位置与长度的文本), `text` 条件也不经快路径 (计数为 0), 判为屏幕变化. (c) 同一修后包上快路径开 / 关 (`noFastPath`) 交替各 7 次 (探针, 每次尝试的宿主 CPU 中位数 ms): 主界面 9.75 / 15.9, 设置首页 5.0 / 4.7, 悬浮窗 8.8 / 6.5; 两个短 id 查找一次的耗时 主界面 0.95 ms (CPU 1.4 ms), 设置首页 1.16 ms, 悬浮窗 7.46 ms (CPU 5.4 ms), 遍历一次未命中 1.0 / 0.8 / 1.8 ms: 宿主自己的悬浮窗上查找比遍历贵约 4 倍, 快路径反而更费 (登记 Q15). 设置首页 bench 的 -30% / -35% 大部分不是快路径的作用 (同包开关差在噪声内).

### Q16 (2026-10-26, P3 子会话 local_ee86efc6)

- 设备: Sony G8441 (API 28). 23046RP50C 被 api-coverage 占用, Q15 / P3 的真机改用 22120RN86C 与 OPD2413, API 24-29 的验证用 G8441 (locks.md 登记, 用后释放).
- 修前 (原装 versionCode 5835 与 10-25 23:39 构建的修前调试包 5935): `tools/selector/device_registry.py -s BH900ASK9E` (不需要无障碍服务, 在宿主进程里建立注册表) 报 NoClassDefFoundError (`AccessibilityNodeInfo.ExtraRenderingInfo`); augment Q155 记录的 `pickup` / `detect` 现象 (新层 NoClassDefFoundError, 旧层 InternalError "Failed to call method selector.pickup") 由此而来.
- 修后 (b3c6b5f10 的调试包, versionCode 5935): `device_registry.py` PASS, 注册表 205 个名称. 黄金表 `device_pick_table.py -s BH900ASK9E --runs 1 --new-modules on --shape-against pick_table-23046RP50C.json`: 3778 行, 3775 行形状与 23046RP50C (API 35) 一致; `off --shape-against pick_table-23046RP50C-legacy.json`: 3776 / 3778 行一致. 不同的行都是平台差异: `getExtraRenderingInfo` 在 API 28 上不进注册表 (按 Q16 的修复), 作为结果类型改报错 (新层 RangeError 2 行, 旧层 InternalError 1 行); `getAvailableExtraData` 在 API 28 返回空列表 (1 行). 其余各段 (types / detect / forms / roots / arity / callbacks / invoke / exists) 全部一致.
- 收尾: G8441 用 `adb install -r -d` 恢复原装 5835, 无障碍服务随后重新绑定.

### Q15 返工 (2026-10-26, P3 子会话 local_ee86efc6)

- 设备与口径: 22120RN86C / OPD2413 (23046RP50C 在 `locks.md` 中被 api-coverage 占用). `device_bench.py -s SERIAL --new-modules on --save --label <标签>`, OPD2413 加 `--settings-package com.oplus.wirelesssettings` (不加 `--settle`); 各版本都是 VERSION_BUILD 4283 (versionCode 5935) 的 `:app:assembleAppDebug`, `adb install -r` 交替安装. 3b (37b93d893) 之后 `--new-modules` 只写一个不再被读取的偏好, 照传 `on` 只为与新层的基线文件名对应. 版本与标签 (文件 `baseline/bench-<model>-<标签>.json`):
  - P2: 不自适应 (51ec78df2, 上节 `-p2`).
  - 墙钟: Q15 原方案, 比较查找与遍历的墙钟耗时 (未提交, 00:21 运行; 文件被下一版覆盖, 数字取自运行日志).
  - 尝试内 CPU: 比较尝试内的进程 CPU, 相等也遍历 (c4415d424, `-q15`).
  - 尝试内 CPU 严格: 查找严格更贵才遍历 (5c044d5ca; `-q17`, 以及 P3.2-P3.5 之后同一份快路径代码的 `-p3`).
  - 整周期 (采用): 从一次尝试开始到下一次尝试开始的进程 CPU, 每次完整尝试之后一次不查找的遍历与一次查找各测一个周期 (e8fe4494e, `-p3b`).
  - 整周期, 完整减查找 (未采用): 不另做测量遍历, 以完整尝试 (查找后遍历) 的周期减去查找周期作为遍历 (未提交, `-p3c`).
- 表中为 `findOne600Id` / `findOne600` 每次查询的 CPU 中位数 (ms) 与两者之比, 括号内为 10 轮累计的 `auto.stats.fastPath.walks`. 取同一次运行的两项之比以抵消设备状态 (同一份代码两次运行的 `findOne600Id` 可差 15-20%, 例如 22120RN86C 主界面 `-q17` 281.5 对 `-p3` 241); 比值越低越好, 1.0 即与不走快路径的 `text` 轮询相同.

| 设备 | 场景 (节点) | P2 | 墙钟 | 尝试内 CPU | 尝试内 CPU 严格 (`-q17`; `-p3`) | 整周期 (采用) | 整周期, 完整减查找 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 22120RN86C API 33 | AutoJs6 主界面 (628) | 235 / 456 = 0.52 | 258.5 / 531.5 = 0.49 (0) | 275 / 561 = 0.49 (21) | 281.5 / 540.5 = 0.52 (21); 241 / 489 = 0.49 (0) | 294.5 / 470.5 = 0.63 (10) | 248.5 / 487 = 0.51 (7) |
| 22120RN86C API 33 | 悬浮窗 300 TextView (303) | 245 / 223.5 = 1.10 | 232.5 / 224.5 = 1.04 (50) | 233 / 225 = 1.04 (48) | 231 / 225.5 = 1.02 (48); 211.5 / 205.5 = 1.03 (55) | 218 / 216.5 = 1.01 (61) | 224.5 / 220 = 1.02 (60) |
| 22120RN86C API 33 | 设置首页 (42) | 93.5 / 135 = 0.69 | - | - | -; 109 / 116.5 = 0.94 (90) | 98 / 121.5 = 0.81 (34) | 121.5 / 123.5 = 0.98 (90) |
| OPD2413 API 35 | AutoJs6 主界面 (151) | 118.5 / 187 = 0.63 | 169.5 / 220.5 = 0.77 (72) | 174 / 219 = 0.79 (100) | 175.5 / 224.5 = 0.78 (89); 175 / 182.5 = 0.96 (100) | 124 / 183.5 = 0.68 (10) | 168.5 / 188 = 0.90 (100) |
| OPD2413 API 35 | 悬浮窗 300 TextView (305) | 97 / 85 = 1.14 | 86.5 / 80.5 = 1.07 (78) | 81 / 108 = 0.75 (92) | 78.5 / 82.5 = 0.95 (63); 75 / 75 = 1.00 (83) | 77.5 / 81 = 0.96 (92) | 77.5 / 83 = 0.93 (83) |
| OPD2413 API 35 | 设置首页 (121-122) | 52.5 / 85 = 0.62 | - | - | -; 60.5 / 60.5 = 1.00 (30) | 61 / 86.5 = 0.71 (28) | 72 / 99.5 = 0.72 (70) |

- 读数:
  - (1) 墙钟分不开两类屏幕 (Q17 的起因): 22120RN86C 主界面与悬浮窗上查找与遍历的墙钟耗时接近, 主界面一次也不改遍历, 悬浮窗改了 50 次比值仍为 1.04; 单独测量每次的进程 CPU, 主界面查找 7.0-8.7 ms 对遍历 11.9-12.9 ms, 悬浮窗 9.3-10.3 ms 对 8.3-8.8 ms.
  - (2) 尝试内 CPU: 悬浮窗回到 1.0 左右, 但 OPD2413 主界面与两台设置首页几乎全部改为遍历 (主界面 100 / 89 次, 设置首页 90 / 30 次), 丢掉了 P2 在那里的收益 (OPD2413 主界面 0.63 -> 0.96). 原因是只计调用本身: OPD2413 主界面单独测量的查找 0.8 ms 对遍历 1.4 ms, 毫秒精度下常相等或颠倒, 而遍历留下的开销 (垃圾回收, 以及宿主自身窗口上 UI 线程对无障碍请求的应答) 落在调用之后; P2 探针每次尝试的宿主 CPU 为快路径开 / 关 9.75 / 15.9 ms (上节 (7) (c)), 远大于调用内的差别.
  - (3) 整周期 (采用) 比较的正是 bench 所报的量 (每次尝试的进程 CPU): OPD2413 主界面 0.68 (只剩每次完整尝试后的 1 次测量遍历), 两台设置首页 0.81 / 0.71, 悬浮窗 1.01 / 0.96 (不再比不走快路径更费). 代价是每个校验窗口 (1000 ms) 一次测量遍历, 在查找省的屏幕上比 P2 多遍历一次: 22120RN86C 主界面一次遍历约 50 ms CPU (`findOne600` 每次尝试 52 ms), 比值 0.52 -> 0.63. 六处比值的平均: P2 0.78, 尝试内 CPU 严格 (`-p3`) 0.90, 整周期 0.80, 完整减查找 0.84; 悬浮窗两处 P2 1.10 / 1.14 -> 整周期 1.01 / 0.96.
  - (4) 完整减查找 (未采用): 省掉测量遍历, 但完整尝试的遍历留下的开销落进紧随其后的查找周期, 查找被高估, OPD2413 主界面与 22120RN86C 设置首页又全部改为遍历 (100 / 90 次).
  - (5) 自适应只影响 CPU, 不影响结果: `-p3` / `-p3b` / `-p3c` 三个包上 parity `--check` (22120RN86C 主界面与设置首页, OPD2413 主界面) 100%, `device_accuracy.py` 14 例两台 PASS, bench 的快路径不一致计数为 0.

### P3 修后 (2026-10-26, P3 子会话 local_ee86efc6)

- 构建: e8fe4494e (P3.1-P3.5 与 Q17 整周期) 的 `:app:assembleAppDebug` (VERSION_BUILD 4283, versionCode 5935), 即上节 `-p3b`; `-p3` (P3.5 之后, Q17 改动之前) 与 `-p3c` 作为同一遍历路径的重复运行, 下文 "三次运行" 指这三份. 设备 22120RN86C / OPD2413 (代替 23046RP50C), 命令同上节. 新项: `refresh` = 对一个命中节点 `refresh()` 一次 (P3.2 的额外开销); `findOne600Default` = 默认标志 (P3.1 起为事件辅助) 下的 `findOne(600)`; 原有轮询项 (`findOne600` / `findOne600Id` / `cpuFixed` / `stormFixed`) 以 `'!eventAssistedPolling'` 按 P3.1 之前的固定 50 ms 运行, 与 P2 可比 (5c044d5ca).

| 设备 | 场景 (节点) | full / miss / missId / missMixed / hit / late (ms) | `refresh` ms | `findOne600` 尝试 / CPU ms | `findOne600Default` 尝试 / CPU ms | CPU 固定 50 ms / 默认 (尝试) | 事件风暴 CPU 默认 / 固定 50 ms (尝试), 空闲 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 22120RN86C API 33 | 设置首页 (42) | 5.91 / 3.231 / 3.379 / 3.862 / 5.697 / 4.942 | 4.051 | 11 / 121.5 | 4 / 42 | 17.15% (172) / 4.8% (50) | - |
| 22120RN86C API 33 | AutoJs6 主界面 (628) | 11.606 / 6.55 / 6.082 / 20.871 / 4.471 / 17.457 | 2.218 | 9 / 470.5 | 4 / 209 | 72.85% (128.5) / 28.05% (45) | - |
| 22120RN86C API 33 | 悬浮窗 300 TextView (303) | 14.368 / 11.193 / 10.883 / 15.657 / 15.359 / 13.192 | 1.677 | 8 / 216.5 | 4 / 111 | 30.15% (122) / 11.2% (45) | 107.65% (112) / 107.8% (112), 96.9% |
| OPD2413 API 35 | 设置首页 (122) | 3.479 / 1.208 / 1.296 / 1.227 / 1.033 / 1.259 | 0.202 | 12 / 86.5 | 5.5 / 33 | 14.6% (181) / 5.4% (64.5) | - |
| OPD2413 API 35 | AutoJs6 主界面 (151) | 2.955 / 0.647 / 0.958 / 1.185 / 0.437 / 0.809 | 0.17 | 13 / 183.5 | 6 / 101 | 27.65% (186) / 12.45% (62) | - |
| OPD2413 API 35 | 悬浮窗 300 TextView (305) | 5.09 / 1.343 / 1.838 / 3.518 / 2.489 / 3.159 | 0.441 | 11 / 81 | 5 / 45 | 17.95% (168) / 7.15% (63) | 39.85% (121.5) / 40.9% (124), 32% |

- 读数:
  - (1) P3.1: 事件风暴下默认 (事件辅助) 的尝试次数与固定 50 ms 持平, 三次运行 22120RN86C 110-112 对 108.5-112, OPD2413 121.5-122.5 对 124-125; P3.1 之前 (上节 `-p2`) 事件辅助为 186.5 / 182.5 次, CPU 123.9% / 44.2% -> 107.65-108.75% / 36.05-40%. 安静屏幕 (主界面) CPU 固定 50 ms 70.4-73.9% / 27.6-32% -> 默认 28.05-30.7% / 12.45-15.6%. `findOne(600)` 默认尝试 4 次 (22120RN86C) / 5-6 次 (OPD2413), 固定 50 ms 为 8-13 次.
  - (2) P3.2: `refresh()` 一次 22120RN86C 1.2-4.1 ms (设置首页最贵, 节点在设置进程), OPD2413 0.16-0.44 ms (三次运行). 单个结果返回前多这一次: `hit` (findOnce 命中) 比 P2 修后 22120RN86C 多 1.8-3.9 ms, OPD2413 多 0.17-1.5 ms.
  - (3) `lateOne`: OPD2413 设置首页 `-p3` 一次失败 (WLAN 列表的最后一个唯一文本中途消失, 同 P2 修后 (7) (b)), `-p3b` / `-p3c` 通过; 其他场景三次全部通过.
  - (4) 其余项 (full / miss / missId / missMixed / late) 与 P2 修后在同一量级, 波动大于版本差 (P3 不改遍历路径).
  - (5) 黄金表 (`device_pick_table.py --runs 2 --new-modules on --shape-against pick_table-23046RP50C.json`, P3.5 的包): 两台 3778 / 3778 行与 23046RP50C 同形状, 两次运行一致; parity `--check` 100% (22120RN86C 主界面与设置首页, OPD2413 主界面; OPD2413 设置首页为 WLAN 列表, 不可比, 同 P2); `device_accuracy.py` 14 例两台 PASS; `device_sample_explain.py` 19 项两台 PASS.

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
| Q1 | 快路径对 `text` 条件的未命中判定是否可靠: 自定义 `AccessibilityNodeProvider` (部分 WebView / 游戏引擎) 的 `findAccessibilityNodeInfosByText` 可能返回空 | (1) 推荐: 中间尝试用快路径判未命中, 查询最后一次尝试与单次 `find*` 走完整 DFS 校正, 并在 `auto.stats` 记录 "快路径未命中但 DFS 命中" 的次数 (真机为 0 才算达标); (2) 快路径只用于 `id` 条件; (3) 不做 `text` 快路径 | P2.3 | (2) / (3) 各删一段分派, 小 | 已决 (2026-10-25, 维护者选: (1) 中间尝试用快路径判未命中, 查询最后一次尝试与单次 find* 走完整 DFS 校正, `auto.stats` 记 "快路径未命中但 DFS 命中" 次数) |
| Q2 | D6 三项默认开后的关闭方式: 现只有 `auto.setFlags(names)` 没有清除 | (1) 推荐: 新增 `auto.clearFlags(names)`, 并让 `setFlags` 接受前缀 `!` 的名字 (`'!eventAssistedPolling'`); 新增标志名 `noFastPath` / `noRefreshOnHit` 不另起反向语义; (2) 只加反向标志 (`fixedIntervalPolling` / `noFastPath` / `noRefreshOnHit`), 不加 clearFlags; (3) 加 `auto.setFlags` 的对象形式 `{ eventAssistedPolling: false }` | P2.3, P3.1, P3.2, P4.2, P4.3 | 小 (名称与解析) | 已决 (2026-10-25, 维护者选: (1) 新增 `auto.clearFlags(names)`, `setFlags` 接受前缀 `!` 的名字, 新标志名 `noFastPath` / `noRefreshOnHit`) |
| Q3 | `prefer` 的键集合与计分: 多键时按顺序逐键比较 (字典序) 还是加权求和 | (1) 推荐: 字典序 (先按第一个键分组, 相同再看下一个), 键 `visible` / `clickable` / `enabled` 为布尔, `center` / `area` / `top` 为数值; (2) 加权求和 (权重随顺序递减); (3) 只提供 `visible` 一个键 | P3.4, P4.2, P4.3 | 小 | 已决 (2026-10-25, 维护者选: (1) `prefer` 字典序逐键比较) |
| Q4 | `normalize` 的选项集合与适用属性 | (1) 推荐: 选项 `trim` / `caseInsensitive` / `fullWidth` (全角转半角, 含全角标点) / `whitespace` (连续空白折叠为一个空格, 含 NBSP / 零宽字符移除) / `nfkc`, 无参或 `true` 为全部; 作用于 `text` / `desc` / `content`, 对 `id` / `className` / `packageName` 只应用 `trim` 与 `caseInsensitive`; (2) 全部属性应用全部选项; (3) 只做 `trim` + `caseInsensitive` + `fullWidth` | P3.3, P4.2, P4.3 | 小 | 已决 (2026-10-25, 维护者选: (1) 五选项 trim / caseInsensitive / fullWidth / whitespace / nfkc, 作用于 text / desc / content, id / className / packageName 只 trim + caseInsensitive) |
| Q5 | PickArguments 的错误消息: 复用新层 `ScriptMessages` (10 种语言, 已有 argumentType / cannotBe / failedToCall) 还是新增专用消息 | (1) 推荐: 复用, 结果类型的候选列表拼在 `cannotBe` 的说明里; (2) 新增 3-5 条专用资源 (10 种语言) | P1.1, P1.2 | 小 | 已决 (2026-10-25, 维护者选: (2) PickArguments 错误消息新增 3-5 条专用资源 (10 种语言), 不复用 ScriptMessages) |
| Q6 | 预取与快路径在 `MODE_FAST` (`fastRootInActiveWindow`, 缓存根节点) 下是否启用 | (1) 推荐: 启用 (根来自缓存不影响 `findAccessibilityNodeInfosByViewId` 的 IPC; 根过期时快路径与 DFS 同样只见过期子树); (2) `MODE_FAST` 下关闭快路径, 只用预取 | P2.1, P2.3 | 小 | 已决 (2026-10-25, 维护者选: (1) MODE_FAST 下预取与快路径启用) |
| Q7 | P2 与 P3 是否并行 | (1) 推荐: 并行两张卡, 按第 5 节 P3 的文件分工, 共同文件由先开始者改, 构建在 locks.md 排队; (2) 顺序 P2 再 P3 | 批次安排 | 无 | 已决 (2026-10-25, 维护者选: (2) P2 与 P3 顺序执行, 不并行) |
| Q8 | 节点动作 `showTooltip` (以及同类的 `hideTooltip`) 在没有 tooltip 的 View 上执行时, 框架 `View.showLongClickTooltip` 空指针, 在目标应用的主线程抛出而使该进程崩溃 (P0.3 黄金表 2026-10-25 在 22120RN86C (API 33) 上对 AutoJs6 自己的悬浮窗执行 `pickup(sel, 'showTooltip')` 时 AutoJs6 崩溃, MIUI 随后把 AutoJs6 与另两个插件的无障碍服务移出已启用列表). 对其他应用的节点调用则是让那个应用崩溃. 是否在宿主侧加防护 | (1) 推荐: `UiObjectActions.showTooltip` / `hideTooltip` 先查节点的 actionList, 不含对应动作 (ACTION_SHOW_TOOLTIP / ACTION_HIDE_TOOLTIP) 时返回 false 不执行, 对 pickup 参化调用, 注册表与直接调用同样生效, changelog `fix`; (2) 只对 AutoJs6 自身包名的节点防护; (3) 不改, 只在文档注明 | P1.2, P1.3 (注册表条目), P4.1 (changelog), P4.2 (文档) | 小 (两个方法加一次 actionList 判断与 JVM 用例) | 已决 (2026-10-25, 维护者选: (1) `UiObjectActions.showTooltip` / `hideTooltip` 先查节点 actionList, 不含 ACTION_SHOW_TOOLTIP / ACTION_HIDE_TOOLTIP 时返回 false 不执行, 选择器与集合逐节点判断, 实施提交 0fb69ecbe) |
| Q9 | `PickResultType` 的类初始化顺序缺陷: 新层 `SelectorForms` 的静态 `PLURAL = setOf(PickResultType.WidgetCollection, ...)` 若是进程里第一个用到该类的地方, JVM 先初始化父类 `PickResultType`, 其伴生对象建别名表时 `WidgetCollection` 尚为 null, 于是 `{}` / `wc` / `collection` / `list` / `#{}` / `{#}` / `w{}` / `{w}` 8 种拼写在整个进程内都解析为 `Invocation`: 新层 `pickup(sel, '{}')` 报 RangeError (需要 PickupResult), 旧层报 "未知结果类型". 开关默认开的 6.8.0 上, 进程里第一次 `pickup` 走新层即触发 (P0.3 在 23046RP50C 上实测, 两层的黄金表都记录了这一状态; 22120RN86C 的进程先经旧层用到该类, 集合拼写正常). 修复与验收方式 | (1) 推荐: P1 修复 (别名表改为首次查表时惰性建立, 或 `PLURAL` 等集合改用 getter), JVM 回归用例在新类加载器中先触达子类对象再查表; P1.5 对照时把黄金表中这 8 种拼写的行列为允许差异 (修复), 并以 22120RN86C 的表 (`tools/selector/baseline/pick_table-22120RN86C*.json`, 初始化正常) 核对修复后的形状; changelog `fix`; (2) 现在单独修 (P1 之前发一张小卡); (3) 不单独修, 由 P1.3 重写自然消除, 只在 P1.5 核对 | P1.1, P1.3, P1.5, P4.1 | 小 (一处初始化方式与一个回归用例) | 已决 (2026-10-25, 维护者选: (2) P1 前单独修: `PickResultType` 伴生对象的别名表 `groups` / `symbols` 改为首次查表时惰性建立 (`SelectorForms.PLURAL` 不改), 隔离类加载器回归用例, 23046RP50C 两张黄金表已重录 (附录 A P0.3 "基线重录"), 实施提交 0fb69ecbe) |
| Q10 | P1.2 的验收文字与 D11 冲突: P1.2 要求原 `Invoker.Interceptor.interceptedMap` 的 "60 个名称" 全部在注册表中, 但该表实为 49 个名称, 其中 42 个是 `AccessibilityNodeInfoCompat` 的 setter 与带参方法 (`getChild` / `findAccessibilityNodeInfosByText` / `ByViewId` / `setChecked` / `setTextSelection` 等), D11 把它们排除在注册表外 (服务给出的节点已密封, 调用 setter 抛 IllegalStateException; `getChild` 有 `child` 替代). D11 只收 compat 的无参 `getXxx` / `isXxx`, 因此 `canOpenPopup` / `hasRequestInitialAccessibilityFocus` (只读布尔) 与 `refresh` / `unwrap` 也不在注册表中 (P1.5 黄金表 sweep 4 + 4 行). 按 D11 实施后, 这些名字作为结果类型时由 "调用 compat 方法" 改为 "未知结果类型" RangeError 并列候选 (新层在搜索前, 旧层在找到节点时) | (1) 推荐 (已先行): 按 D11, `NodeMethodRegistryTest` 断言 49 个名称 = 注册表中的 7 个 (`child` / `compass` / `performAction` / `scrollTo` / `setProgress` / `setSelection` / `setText`) + D11 排除的 42 个 (均为 compat 方法); P1.5 黄金表中涉及这些名称的行列为允许差异 (Q10); (2) 只补收 `canOpenPopup` / `hasRequestInitialAccessibilityFocus` 两个只读布尔方法 (注册表规则加 `can` / `has` 前缀的无参 compat 方法); (3) 把 42 个 compat setter / `getChild` / `findAccessibilityNodeInfosBy*` 加入注册表 (与 D11 的排除项相反, setter 在真机上仍抛 IllegalStateException) | P1.2, P1.5, P4.1, P4.2 | 小 (注册表分类加一类, 测试改断言) | 已决 (2026-10-25, 维护者选: (2) 注册表补收 `can` / `has` 前缀的无参 compat 方法, 恢复 `canOpenPopup` / `hasRequestInitialAccessibilityFocus`, 49 个名称的分类不变, 实施提交 af3987235) |
| Q11 | P2.3 的 `text` 快路径: 框架的文本查找 (`findAccessibilityNodeInfosByText`) 在真机上漏查. AutoJs6 主界面 (含文档 WebView) 23046RP50C 191 个不同文本漏 143 个, 22120RN86C 164 个漏 143 个 (漏查节点按类名: TextView 140, WebView / Button / View 各 1, 即 WebView 内容), 系统设置首页两台各漏 1 个 (普通 TextView), OPD2413 (主界面未停在文档页) 两块屏幕 0 漏查; 按 Q1 "真机为 0 才算达标", `text` 不达标. 同样屏幕上 id 查找 0 漏查 (完整 id 27 + 9 / 22 + 8 个, 短 id 同) | (1) 推荐 (已先行, 51ec78df2): 快路径只用精确 id (完整名与短 id), `text` 条件走完整搜索; 代价: `text` 条件的轮询 CPU 不降 (附录 A P2 的 `findOne600` / `cpuFixed`); (2) 保留 `text` 快路径及 Q14 的防护: 等待期间才出现在 WebView 等提供方中的文本最多晚约 1 s 找到 (每个脚本每个包一次), 同一精确文本同时出现在原生控件与 WebView 时 `findOne` 可能返回与 `find()[0]` 不同的节点; 收益 (23046RP50C, 含 Q12 原方案预取开销的构建): `cpuFixed` 主界面 49% -> 36.4%, 设置页 16.3% -> 10.45%; (3) `text` 只用于单候选命中, 不判未命中 (无 CPU 收益, 仍有 (2) 的第二个风险) | P2.3, P2.4, P4.1 | 小 ((2) revert 51ec78df2 后重跑 parity / bench; (3) 再改 `keyOf` 与一处分派) | 已决 (2026-10-25 23:56, 维护者选 (1) 保持现状, 51ec78df2) |
| Q12 | P2.1 的预取策略: 原方案为 API 33+ 取根节点与 `getChild` 用 `FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST` (BFS 用 BREADTH_FIRST) 加 `FLAG_PREFETCH_UNINTERRUPTIBLE`. 真机探针 (23046RP50C API 35 / 22120RN86C API 33 / OPD2413 API 35, 8 种策略轮流各 15 轮; 脚本与数据在调度会话共享笔记目录 `selector-p2/README.md`): 根节点带 UNINTERRUPTIBLE 每次多 5-12 ms (OPD2413 1-2 ms; 同步预取子树); 子节点已在服务的缓存中 (由事件保持), 显式策略 (DF / BF / HYBRID) 与默认无差别. 按原方案的构建在 bench 中单次搜索全部变慢 (23046RP50C 设置页 `full` 5.7 -> 13.6 ms, `hit` 1.7 -> 11.9 ms) | (1) 推荐 (已先行, 71b77597c): 不显式指定预取, 沿用框架默认; (2) 只对子节点用 DF + UN (与默认持平, 无收益); (3) 按原方案 (每次搜索慢 5-12 ms) | P2.1 | 小 (原方案差异在共享笔记 `selector-p2/p2.1-prefetch-variant.diff`) | 已决 (2026-10-25 23:56, 维护者选 (1) 保持现状, 71b77597c) |
| Q13 | Q2 的 `auto.setFlags` 语义细节: 原 `setFlags` 用给出的标志整体替换, 加入 `!name` 后如何组合 | (1) 推荐 (已先行, cde3fee03): 仍为整体替换, 起点是默认标志 `AccessibilityBridge.DEFAULT_FLAGS` (目前为空; P3 把某项设为默认开时起点随之包含它), 给出的名字设置, `!name` 清除, 同一名字既设置又清除时报错 (新层 TypeError, 旧层 IllegalArgumentException); `clearFlags(names)` 只清除给出的名字, 其余不变; (2) 含 `!` 的调用改为增量 (只改动给出的名字), 不含 `!` 的仍整体替换; (3) `setFlags` 一律增量 | P2.3, P3.1, P3.2, P4.2 | 小 (`Auto.setFlags` 一处与两条用例) | 已决 (2026-10-25 23:56, 维护者选 (1) 保持现状, cde3fee03) |
| Q14 | P2.3 快路径的校正范围: Q1 只要求查询的最后一次尝试完整遍历, 但 `untilFind*` / `waitFor` 等无上限查询没有最后一次, 查找漏掉时会一直找不到; 已知 id 查找的盲区: WebView 的 HTML id (无 `:id/`, 两台主界面各 6 个, `id('<entry>')` 按条目名能匹配它们而查找不能), 其他包的 id (桌面小部件, 通知) | (1) 推荐 (已先行, cde3fee03): 第一次与最后一次尝试完整遍历, 长查询至少每 1000 ms 一次校验 (`FastPath.VERIFY_INTERVAL`); 遍历发现 "查找为空而遍历找到" 时计入 `auto.stats.fastPath.mismatches`, 该脚本此后对该根节点的包不再用快路径 (`FastPath.Ledger`, 每个 bridge 一份); (2) 只按 Q1 字面 (最后一次尝试), 无上限查询另每 N 次校验一次; (3) 同 (1), 校验间隔改为 500 ms 或 2000 ms | P2.3 | 小 (`FastPath.Query` 一处与用例) | 已决 (2026-10-25 23:56, 维护者选 (1) 保持现状, cde3fee03) |
| Q15 | P2.3 快路径在查找比遍历贵的屏幕上更费: 悬浮窗场景 (宿主自己的 300 TextView 悬浮窗, 窗口过滤器) 的 `findOne600Id` 每次查询 CPU 22120RN86C +17%, OPD2413 +29% (23046RP50C -2%). OPD2413 探针 (同一修后包): 该窗口上两个短 id 查找一次 7.46 ms (宿主 CPU 5.4 ms), 遍历一次未命中 1.8 ms, 快路径开 / 关每次尝试 CPU 8.8 / 6.5 ms; 主界面查找 0.95 ms, 遍历 1.0 ms, 开 / 关 9.75 / 15.9 ms; 设置首页 1.16 / 0.8 ms, 开 / 关 5.0 / 4.7 ms. 查找贵的原因未查明 (目标窗口在宿主进程内, 应用侧处理计入宿主 CPU 是候选, 但主界面同在宿主进程内却不贵) | (1) 推荐 (未先行): 按查询自适应: 第一次 (完整) 尝试计一次遍历耗时, 第一次中间尝试计一次查找耗时, 查找不比遍历快时该查询余下的中间尝试改为遍历, 每次校验时重比; 结果与现状相同 (快路径本就只判未命中与单候选命中), 只影响 CPU; (2) 同 (1), 比较结果按包记进 `FastPath.Ledger` (每个 bridge), 后续查询沿用; (3) 保持现状 (这类屏幕上 id 轮询每次尝试多 2-8 ms CPU, 脚本可用 `noFastPath` 关闭); (4) 有窗口过滤器时不走快路径 (只覆盖已知场景) | P2.3, P2.4 | 小到中 ((1) / (2): `FastPath.Query` 记两个耗时, `UiSelector.search` 回传是否走了查找及耗时, 2-3 条 JVM 用例, 三台重测悬浮窗与主界面约 1 h; 需新卡片或并入 P3 卡) | 已决 (2026-10-25, 维护者选 (1) 按查询自适应; P3 子会话实施 c4415d424 / 5c044d5ca / e8fe4494e, 度量与平手规则见 Q17, 复测见附录 A "Q15 返工") |
| Q16 | (augment Q155, 由 augment-q128 小卡转来) API 24-29 上建立节点方法注册表失败: `NodeMethodRegistry` 读取 `UiObject` 全部公开方法的签名, API 30 以前 ART 读到 `getExtraRenderingInfo` 的返回类型 `AccessibilityNodeInfo.ExtraRenderingInfo` (API 30) 时抛 NoClassDefFoundError, 注册表建立失败, 此后每次 `pickup` / `detect` 都抛错 (新层 NoClassDefFoundError, 旧层 InternalError "Failed to call method selector.pickup") | (1) 推荐 (已实施): 签名含平台缺失类 (LinkageError / TypeNotPresentException) 的方法归为 INTERNAL 不登记, compat 签名集合同样跳过; 签名经 `SignatureReader` 读取, JVM 用例可模拟 ART 的读取失败; (2) 注册表改为白名单 (只登记列出的方法名) | P1.2, P4.1 | 小 | 已决 (2026-10-25, 维护者决定由 selector 修复; 修复 b3c6b5f10. 验证设备 Sony G8441 (API 28, 23046RP50C 被 api-coverage 占用, 改用它与 22120RN86C / OPD2413): `tools/selector/device_registry.py` 修前 NoClassDefFoundError, 修后 PASS (205 个名称); 修后黄金表见附录 A "Q16") |
| Q17 | Q15 按查询自适应的度量: 原方案比较墙钟耗时, 真机上分不开两类屏幕 (22120RN86C 主界面查找省 CPU, 悬浮窗查找更费, 两处的墙钟耗时却接近; 单独测量每次的进程 CPU: 主界面查找 7.0-8.7 ms 对遍历 11.9-12.9 ms, 悬浮窗 9.3-10.3 ms 对 8.3-8.8 ms); 改为比较尝试内的进程 CPU 后, OPD2413 主界面与两台设置首页又几乎全部改为遍历 (遍历留下的开销落在调用之后, 见附录 A "Q15 返工" (2)) | (1) 推荐 (已先行, e8fe4494e; 之前的 c4415d424 / 5c044d5ca 为尝试内 CPU): 比较整周期的进程 CPU (`Process.getElapsedCpuTime()`, 从一次尝试开始到下一次尝试开始), 每次完整尝试之后一次不查找的遍历与一次查找各测一个周期, 查找严格更贵才遍历, 遍历模式下最后一次尝试也遍历; 六处 `findOne600Id` / `findOne600` 比值平均 0.80 (P2 0.78), 悬浮窗 1.10 / 1.14 -> 1.01 / 0.96, 代价是每个 1000 ms 校验窗口一次测量遍历 (22120RN86C 主界面 0.52 -> 0.63); (2) 尝试内进程 CPU (5c044d5ca, 平均 0.90, OPD2413 主界面 0.96); (3) 整周期, 以完整尝试减查找代替测量遍历 (平均 0.84, 完整尝试的遍历开销落进随后的查找周期, OPD2413 主界面 0.90); (4) 比较墙钟耗时 (Q15 原方案); (5) 不自适应 (回到 P2, 平均 0.78, 悬浮窗比不走快路径更费) | P2.3, Q15, P4.1 | 小 (`FastPath.Query` 一处与用例; 改选 (2) 即 revert e8fe4494e) | 已决 (2026-10-26 08:48, 维护者选 (1) 保持现状) |
| Q18 | `normalize` 下正则条件 (`textMatch` / `textMatches` / `descMatch` 等) 如何忽略大小写: Q4 只规定了字面条件两侧规范化 | (1) 推荐 (已先行, 094f4b084): 模式不改写, 被匹配的值按选项规范化 (不转小写), `caseInsensitive` 时模式以 `CASE_INSENSITIVE` 与 `UNICODE_CASE` 编译; (2) 模式与值都转为小写 (会改变 `[A-Z]` 等区分大小写的字符类的含义); (3) 正则条件不受 normalize 影响 | P3.3, P4.2 | 小 (`StringMatcher.NormalizedPattern` 一处) | 已决 (2026-10-26 08:48, 维护者选 (1) 保持现状) |
| Q19 | P3.2 的校验范围: `exists()` 是否也刷新校验 | (1) 推荐 (已先行, 4acdced3d): 不校验, `exists()` 即 `findOnce(0) != null`, 与 `find()` / `findOnce(index)` 一样返回搜索所见, 刚离开窗口的节点在服务缓存更新前仍算存在; (2) `exists()` 与 `findOnce()` 一样刷新并重找 (每次多一次 IPC, 节点离开时最多再搜索 2 次) | P3.2, P4.2 | 小 | 已决 (2026-10-26 08:48, 维护者选 (1) 保持现状) |
| Q20 | 命中刷新失败后的重找不等待: OPD2413 (ColorOS 15) 上界面变化后, 服务的节点缓存在一段时间内仍给出已移除的节点 (真机用例在变化后等 300 ms 才稳定), 这段时间内 `findOnce()` 连续重找 2 次都拿到旧节点时返回 null (P3.2 之前返回失效的旧节点) | (1) 推荐 (已先行, 4acdced3d): 保持立即重找; `findOne(timeout)` / `untilFindOne()` 在剩余时间内继续轮询, 能拿到新节点; (2) `findOnce()` 重找前等一个界面事件或 50 ms; (3) 重找前清空服务的节点缓存 | P3.2, P4.2 | 小 | 已决 (2026-10-26 08:48, 维护者选 (1) 保持现状) |
| Q21 | 字符串选择器中可变参数的键 (`prefer`, `action`) 不按逗号拆分多个值: `select('prefer=visible,center')` 抛出 `Unknown key of prefer: "visible,center"` (P4.2 的 JVM 探针, 2026-10-29); 按源码, `action=CLICK,SET_TEXT` 不报错, 而是作为一个名为 `ACTION_CLICK,SET_TEXT` 的行为判断, 不匹配任何控件. 原因: `SelectorStringParser` 只在整值调用得到 NO_MATCHING_OVERLOAD 时才按逗号拆分, 可变参数的键以单个值调用总能匹配重载. `select` 文档写的是 "多参数的键以逗号分隔值" | (1) 推荐 (未先行, 属宿主代码, 超出 P4 文档范围): 条目为可变参数且值未加引号含逗号时先按逗号拆分 (`prefer=visible,center`, `action=CLICK,SET_TEXT`), 加 2 条 JVM 用例, 文档 `select` 与 `prefer` 两节各改一句; (2) 保持现状, 文档写明字符串选择器中 `prefer` 只能给一个键 (已按此写入文档 d163293), `action` 同样补一句 | P4.2 (文档), P5.1 (改代码后重跑单测) | (1): `SelectorStringParser` 一处约 5 行 + 2 条用例 + 文档两句, 小; (2): 文档一句 | 待拍板 |

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
- P0 子会话 local_cb1fa152 (selector-p0-jvm, Opus 5.5): 附录 C Q1-Q7 落盘 (25996d7d2); 构建 HEAD 的调试包 (build 4283) 装到 23046RP50C / 22120RN86C; 新建 `tools/selector/` 三组真机脚本 (ec389609c) 与修前基线 (895070a9f); P0.1-P0.4 全部勾选, 数据见附录 A; 新登记 Q8 (showTooltip 使节点所属进程崩溃) 与 Q9 (PickResultType 类初始化顺序使集合拼写失效), 均未先行改代码. `.gitignore` 增加 `tools/selector` 的放行规则 (同 memory / settings).
- Q8 / Q9 小卡 local_967985b8 (selector-q8q9, Opus 5.5): 修复提交 0fb69ecbe, 基线重录与本 Roadmap 另一笔提交; 进度:
  - (1) 修复与用例 (完成): Q9 `PickResultType` 伴生对象的 `groups` / `symbols` 改为 `by lazy` (首次查表时建立; `SelectorForms.PLURAL` 不改, 根因在别名表); 回归用例 `PickResultTypeTest` 新增 2 例 (在隔离类加载器中先初始化 `WidgetCollection` 再查表, 断言 8 种集合拼写; 9 个符号对象逐个作为 "先触达者", 断言全部 47 种拼写), 修前 2 例均失败 (`{}` 解析为 `Invocation`; 先触达 `Widget` 时其拼写表为空), 修后通过. Q8 `UiObjectActions.showTooltip` / `hideTooltip` 改经私有方法 `performTooltipAction`: 节点 (`UiNode`) 的动作列表不含该动作时返回 false 不执行, `UiSelector` (untilFind) 与 `UiObjectCollection` 逐节点同样判断 (与其 performAction 一样每个节点都尝试, 跳过 null); 动作 id 改用框架常量 `android.R.id.accessibilityActionShowTooltip` / `HideTooltip` (与 `AccessibilityActionCompat` 的 id 相同, JVM 上不触发 compat 类的初始化); 用例 `UiObjectActionsTooltipTest` 4 例 (修前 4 例失败, 修后通过). 全量 `:app:testAppDebugUnitTest` 通过: 950 个类 5814 例 (P0.4 的 5808 + 新增 6), 0 失败, 27 跳过 (首次全量在机器负载下有 3 例计时类用例失败: `A11yServiceControllerTest` / `IdleWaiterTest` / `GalleryLateOpeningTest`, 与本改动无关, 单独重跑 31 例全过, 第二次全量 1 min 33 s 全部通过).
  - (2) 真机 (完成, 汇总见附录 A P0.3 "基线重录"): build 4283 (versionCode 5935, 含两处修复, 已核对 dex) `adb install -r` 到 23046RP50C; 一次性探针 (调度会话共享笔记目录下 `selector-q8q9/q8q9_probe.js` / `.py`, 用法见同目录 README.md) 在新进程中先经新层 `pickup(sel, '{}')`, 8 种集合拼写都返回 `UiObjectCollection`, `PickResultType.Companion.of('{}')` 为 `WidgetCollection`; 无 tooltip 的 Button 上 `pickup(sel, 'showTooltip' / 'hideTooltip' / ['showTooltip'])`, `node.showTooltip()`, `sel.showTooltip()`, `sel.find().showTooltip()`, `detect(w, 'showTooltip')` 全部返回 false, 宿主进程不变; 设了 tooltip 的 Button 动作列表含 ACTION_SHOW_TOOLTIP, 显示与隐藏都执行 (列表随之变为 ACTION_HIDE_TOOLTIP); 新层 on / off 各一次, 全部 PASS. 黄金表 `--new-modules on --check`: 两次运行逐行一致, 对 P0.3 基线 3506 / 3778 行一致, 272 行不同且全部是集合拼写行 (types 240 = 8 种拼写 x 罗盘 5 x 选择器 3 x 回调 2, forms 31 = `'{}'` 列, roots 1), 均由 RangeError 变为数组; tooltip 与其他行无差异; 本次 441 条集合拼写行 (含 detect 的复数拒绝行) 与 22120RN86C 参照表逐行一致 (错误只比类型, 两台语言不同). 开关 off 的 `--check` 432 行不同, 同样全部是集合拼写行 (多出 `detect` 160 行: 旧层对单个节点的集合拼写由 "未知结果类型" / null 变为 "结果类型不适用于 UiObject"), 441 行与参照一致. 核对无误后 on / off 各 `--save` 重录 (两次运行逐行一致), 设备新层开关恢复为原值 off; 全程宿主进程未变 (pid 9993), 无障碍服务保持开启, 未需要维护者操作. 探针的坑: `Button` 默认全大写, 其无障碍文本也是大写, `text('小写')` 找不到时选择器路径的 `showTooltip()` (untilFind) 会一直等.
  - (3) 落盘 (完成): 附录 C Q8 / Q9 状态列改为已决并附 0fb69ecbe; 附录 A P0.3 追加 "基线重录"; P1.5 与 P0.3 验收说明里集合拼写不再列为允许差异. `tools/selector/README.md` 与 `pick_table.js` 的 sweep 仍在命中节点上跳过 `showTooltip` 等 9 个动作 (未改: 改了会增加 sweep 行并再次改动基线; Q8 已由探针在真机确认), P1 若重写 sweep 可一并放开 `showTooltip` / `hideTooltip`.
  - changelog 草稿 (`.changelog/lang_zh-Hans.json` 有他人未提交的改动, 未改; 由维护者同步到 6.8.0 的 `fix` 与其他语言, JSON 中的半角双引号需转义):
    - fix: `控件的 showTooltip / hideTooltip 在没有提示文字 (tooltip) 的控件上执行时 (含选择器, 控件集合与 pickup / detect 的同名调用), 该控件所在的应用 (包括 AutoJs6 自身) 崩溃的问题 (现在控件不支持该动作时不执行并返回 false)`
    - fix: `pickup 的集合结果类型 '{}' / 'wc' / 'collection' / 'list' 等 8 种写法在 AutoJs6 启动后经常整体失效的问题 (报错 "需要 PickupResult" 或 "未知结果类型", 直到 AutoJs6 重启)`
    - 说明: 第二条的缺陷只存在于 v6.8.0-alpha5 (2026-07-14) 之后的开发构建 (`PickResultType` 于 2026-09-06 由 2c7f1af205 引入, 晚于该标签, 未进入任何已发布版本), 按 AGENTS.md 更新日志第 1 条 (同一版本的连续实现合并为最终行为) 维护者可以不单列; 第一条自 6.2.0 起就存在, 应保留.
- P1 子会话 local_dc7f552d (selector-p1-pickrequest, Opus 5.5): 设计备忘在调度会话共享笔记目录 `selector-p1/design.md`; 进度:
  - (1) 代码 (完成, 提交 d963bea6a P1.2 / ff082119c P1.1 / 065570b7c P1.3 + P1.4; P1.3 与 P1.4 同一提交, 因副本依赖 `Invoker`; 中间两笔未单独编译, 第三笔后全量编译与单测通过): P1.2 `NodeMethodRegistry.kt` (D11 分类 OWN / COMPAT_GETTER 登记, OBJECT / RECYCLE / INTERNAL / COMPAT 排除; `NodeMethodTable` 的查找 `name` 再 `getName`, 判定不调用, NaN 与罗盘先判定), `PickMessages.kt` 与 5 条新资源 x 11 个目录 (Q5 (2)), `CompassParser.tryParse`, `SelectorStringParser.suggest`; P1.1 `PickRequest.kt` / `PickArguments.kt` (新层严格模式在搜索前判定全部错误, 顺序 root / 罗盘 / 回调的类型, 选择器, 罗盘语法, 结果类型; 旧入口按原读取方的时机: 选择器在搜索前拒绝, 罗盘在行走节点时, 方法在找到节点时, `@` 带罗盘与 `detect` 复数总是), `SelectorEntry.resolve` / `SelectorResolution`, `UiSelector.algorithmOf`, `SelectorPatterns` -> `core/automator/filter/PatternPrecheck.kt`; P1.3 `PickExecutor.kt` / `PickResultAdapter.kt`, `UiSelector.pickup` / `UiObject.detect` / `A11yDiagnosticsApi` / `RuntimeToolkit` 改调, `Picker` / `Detector` / `Invoker` 删除 (`RuntimeFlowPick` 只用 `PickResultType` / `Compass`, 未改); P1.4 `SelectorModule` / `AutoModule` / `AutoOptions` / `FlowArgs` / `FlowOptions` / `FlowCondition` 改调 `PickArguments`, `SelectorForms` / `SelectorChecks` 删除.
  - (2) JVM 用例 (完成): `PickArgumentsTest` 8, `NodeMethodRegistryTest` 5, `PickExecutorTest` 4, `PickMessagesTest` 3, `CompassTryParseTest` 2, `core/automator/selector/SelectorResolutionTest` 4 (由 `SelectorChecksTest` 改写); `SelectorModulesTest` / 两个 Callable 兼容测试改用新类; `CurrentModuleResults.ARGUMENT_ERRORS` 加 `EcmaError` (现有模块的包装在 JVM 上把 core 的 TypeError / RangeError 打印为 EcmaError); `ScriptModuleArityTest` 的 pickup / detect 由源码扫描改为 JVM 抽样 (个数检查移入归一化器). 全量 5832 例 0 失败. 新登记附录 C Q10 (拦截表 49 个名称与 D11, 已按推荐先行待确认).
  - (3) 真机 (完成, 汇总与允许差异逐类见附录 A P1.5): 23046RP50C 黄金表 on / off, parity 100%, augment device_eval 6 个用例文件 383 例一致, device_patterns 0 误拒; 两次运行的原始结果与分类脚本在调度会话共享笔记目录 `selector-p1/` (`pick_on.run1.json` / `pick_off.run1.json` / `classify_p1.py`). 设备新层开关恢复为原值 off, 全程未需要维护者操作. 基线未重录.
  - (4) 落盘 (完成): P1.1-P1.5 勾选; 附录 A P1.5; 附录 C Q10; 架构文档 `docs/dev/accessibility-architecture.md` 1.1 类图, 1.2, 1.4 "拾取" 段 (请求模型与两种模式) 与 2.2 迁移映射; augment 看板 "selector 接管 P5.2" 待决行改为已完成.
  - changelog 草稿 (`.changelog/lang_zh-Hans.json` 有他人未提交的改动, 未改; 由维护者同步到 6.8.0 与其他语言, JSON 中的半角双引号需转义):
    - improvement: `pickup / detect 的参数错误消息指明函数名与参数名, 结果类型写错时列出最多 5 个相近的写法 (新脚本模块层开启时, 结果类型, 罗盘与回调的错误都在搜索控件之前报出)`
    - fix: `pickup / detect 以数组形式调用控件的带参方法 (例如 ['sibling', 1] / ['offset', 1]) 时报 "未知结果类型" 的问题`
    - improvement (随 Q10 定稿, 改选 (3) 时删除): `pickup / detect 的结果类型只调用控件 (UiObject) 的方法与只读属性, AccessibilityNodeInfoCompat 的 setter, getChild, findAccessibilityNodeInfosByText 等不再作为结果类型 (改报未知结果类型并列出候选; 获取子控件使用 child)`
    - 说明: 第一条括号内的 "搜索之前报出" 若 6.8.0 已有新脚本模块层参数检查的条目, 合并进该条即可; 第二条的缺陷在已发布的 v6.7.0 中就存在 (`UiObject` 内的 `Invoker` 对拦截表外的带参方法以参数数组的类作为每个参数的类型), 应保留.
- P2 子会话 local_6fb6e128 (selector-p2, Opus 5.5): 进度:
  - (0) 基线 (完成): Q10 返工 af3987235 (注册表补收 `can` / `has` 前缀的无参 compat 方法, 附录 C Q10 已决); 23046RP50C 黄金表 on / off 重录 (ec2dd6cd7); 三台用 af3987235 的修前包测 bench (`--label p2pre`), OPD2413 另存 parity 快照 (主界面与设置首页).
  - (1) P2.1 (完成, 71b77597c): bounds 读一次, 选择器计划与文本缓存, 每次搜索一次快照; 原方案的显式预取在两台真机上更慢, 改为沿用框架默认 (Q12, 已先行; 探针与数据在调度会话共享笔记目录 `selector-p2/`).
  - (2) P2.2 (完成, b0aac70f1): 过滤器开销分级与稳定排序.
  - (3) P2.3 (完成, cde3fee03 + 51ec78df2): 轮询查询的框架快路径与 Q2 的 `auto.clearFlags` / `'!name'` / `noFastPath`, `auto.stats.fastPath`; 第一次 / 最后一次 / 每 1 s 校验的完整遍历与按包停用 (Q14, 已先行); 精确 `text` 的快路径因实测漏查按 Q11 先行去掉 (cde3fee03 保留了带 `text` 的版本, 改选时 revert 51ec78df2).
  - (4) P2.4 (完成): 三台修前后对照见附录 A "P2 修后" (OPD2413 设置首页是 WLAN 扫描列表, 跨构建快照与 `lateOne` 不可比, 见 (7); 悬浮窗场景快路径更费, 登记 Q15); 全量 `:app:testAppDebugUnitTest` 959 个类 5855 例, 0 失败, 27 跳过; `docs/api-coverage/MATRIX.md` 重生成 (64af1735a, `check.py` 仍报 `auto.clearFlags` 缺文档与声明, 由 P4 补); 工具与基线另一笔提交 (`tools/selector`, bcd3a2fa9), 本 roadmap 与架构文档 1.4 / 1.9 另一笔提交.
  - 与文件分工的偏离 (P3 接手时以本期提交为基础): P3 的 `UiSelectorQuery.kt` 改了一处调用 (每次尝试把 `FastPath.Query` 给出的尝试类型传给 `findWithin`; Q1 的 "最后一次尝试" 只有查询循环知道) 与类注释; `filter/StringMatcher.kt` 加两个 internal 属性 (`exact` / `isPattern`, 供开销分级与快路径的键); `SelectorRegistry` 既有的 `filter(fn)` 条目加一行开销 (不是新条目). 轮询策略与默认值未改.
  - 新增 API (P4 写声明与文档): `auto.clearFlags(names)` (清除给出的标志, 名字同 `setFlags`, 其余标志不变); `auto.setFlags` 接受 `'!name'` (清除该标志; 同一名字既设置又清除时报错; 整体替换的起点为默认标志, Q13); 标志 `noFastPath` (关闭轮询查询的框架快路径); `auto.stats()` 的 `fastPath` 对象 `{ hits, misses, fallbacks, mismatches }` (文本报告多一行 `fast path: ...`).
  - 工具 (`tools/selector`): bench 新增 `findOne600Id` / `lateOne` 与每次尝试的 CPU, 轮询项累计 `auto.stats.fastPath`, 不一致大于 0 判失败; parity 新增 7 个轮询用例 (与同次 `find()` 逐节点比较) 与 `coverage` (框架查找对屏幕上文本 / id 的漏查数, 文本漏查按类名); 两个脚本新增 `--settings-package` (ColorOS 15 设置首页) 与 `--settle` (打开屏幕后再等若干秒); OPD2413 设置首页不存 parity 快照.
  - 待决: 新登记 Q11 (text 快路径), Q12 (预取策略), Q13 (`setFlags` 语义), Q14 (快路径的校正范围), 均已按推荐先行待确认; Q15 (查找比遍历贵的屏幕上快路径更费, 推荐按查询自适应) 需拍板, 未先行.
  - 设备: 三台的新层开关恢复为原值 off, 修后包 (51ec78df2, versionCode 5935) 留在三台上; 维护者操作: OPD2413 的无障碍授权.
  - changelog 草稿 (`.changelog/lang_zh-Hans.json` 有他人未提交的改动, 未改; 由维护者同步到 6.8.0 与其他语言, JSON 中的半角双引号需转义):
    - feature: `auto.clearFlags 方法, 用于清除 auto.setFlags 设置的标志; auto.setFlags 支持在标志名前加 ! 以清除该标志 (例如 auto.setFlags(['!noFastPath']))`
    - improvement: `等待控件出现的选择器查询 (findOne / untilFind / untilFindOne / waitFor 等) 含精确 id 条件时, 先经系统按 id 查找判断控件是否已出现, 不再每次遍历整个控件树, 等待期间的 CPU 占用降低 (auto.setFlags(['noFastPath']) 可关闭, auto.stats 的 fastPath 记录命中与回退次数)`
    - improvement: `选择器的各个条件按开销从低到高判断, 含正则或 filter 函数的选择器在其他条件不满足时不再执行正则匹配或脚本函数`
    - 说明: Q11 改选 (2) 时第一条 improvement 的 "含精确 id 条件" 改为 "含精确 id 或 text 条件"; Q13 改选时 feature 条目的 setFlags 说明随之调整.

### 2026-10-26

- P3 子会话 local_ee86efc6 (selector P3, Opus 5.5): 本地提交 b3c6b5f10 (Q16), c4415d424 / 5c044d5ca / e8fe4494e (Q15 返工与 Q17), 05c8264cb (P3.1), 4acdced3d (P3.2), 094f4b084 (P3.3 + P3.4), 8bcb9969f (P3.5), 均未推送; 进度:
  - (0) 设备: 23046RP50C 在 `locks.md` 中被 api-coverage 占用, Q15 / P3 的真机改用 22120RN86C (API 33) 与 OPD2413 (API 35), Q16 用 Sony G8441 (API 28); 三台都登记在 `locks.md`, 用后删行.
  - (1) Q16 (完成, b3c6b5f10): API 24-29 上节点方法注册表建立失败的修复, G8441 修前修后与黄金表见附录 A "Q16"; 附录 C Q16 已决, augment 看板 Q155 行与本主题看板待决行已追加结论.
  - (2) Q15 返工 (完成): 按查询自适应, 度量经四版 (墙钟, 尝试内 CPU, 尝试内 CPU 严格, 整周期), 采用整周期 (e8fe4494e); 各版对照见附录 A "Q15 返工", 度量的选择登记为 Q17 (已先行待确认).
  - (3) P3.1-P3.5 (完成, 勾选见第 5 节): 两台的修后数据见附录 A "P3 修后"; 全量 `:app:testAppDebugUnitTest` 950 个类 5851 例, 0 失败, 27 跳过 (e8fe4494e 的源码).
  - (4) 与 augment-p5-2 的交接: 3b (37b93d893) 于 01:55 快进进共享工作区, automator 组移到 `runtime/api/modules/automator`, 新脚本模块层开关删除; P3.5 的 explain 改动在新路径上完成. `tools/selector` 的 `--new-modules` 此后只写一个不再被读取的偏好, 为与新层的基线文件名对应仍传 `on`.
  - (5) api-coverage: `tools/api-coverage/check.py --matrix --write-matrix` 在当前工作区重生成会丢失约 400 行返回对象 (生成器受其他会话的文件搬迁影响, 非本期改动), 未提交 MATRIX.md; 本期应有的变化只是 `UiSelector#` 186 -> 188 (normalize / prefer), 待生成器修好后由 P4 或 api-coverage 重生成.
  - 新增 / 变更的脚本 API (P4 写声明与文档, P4.5 登记到 api-coverage 看板):
    - `UiSelector.normalize()` / `normalize(enabled)` / `normalize({ trim, caseInsensitive, fullWidth, whitespace, nfkc })` (P3.3): 选择器设置, 最后一个生效, 无论写在条件之前还是之后; 全局函数 `normalize(...)` (selector-global-functions.txt), 对象选择器键 `normalize`, 字符串选择器 `normalize` / `!normalize`; `toString()` 输出 `normalize()` / `normalize(false)` / `normalize({trim: true, ...})`. 选项未知或不是布尔时报错 (`Unknown option of normalize` / `The options of normalize take booleans`).
    - `UiSelector.prefer(...keys)` (P3.4), 键 `visible` / `clickable` / `enabled` / `center` / `area` / `top`, 按顺序字典序比较, 相同时保持找到的顺序; 全局函数 `prefer(...)`, 对象选择器键 `prefer` (字符串或数组); 只影响单个结果 (`findOnce()` / `findOne(timeout)` / `untilFindOne()` / `pickup` 的单个控件与其罗盘), 不影响 `find()`; `prefer('visible')` 下罗盘 `k` 越过不可见的可点击祖先. 没有键或键未知时报错.
    - `auto.setFlags` / `auto.clearFlags`: 标志 `eventAssistedPolling` 改为默认开 (`AccessibilityBridge.DEFAULT_FLAGS`), `setFlags(names)` 的起点随之包含它, `setFlags(['eventAssistedPolling'])` 仍接受; 关闭用 `setFlags(['!eventAssistedPolling'])` 或 `clearFlags(['eventAssistedPolling'])` (P3.1). 新标志 `noRefreshOnHit` (P3.2) 关闭单个结果的命中刷新.
    - 行为: `findOnce()` / `findOne(timeout)` / `untilFindOne()` / `UiSelector.pickup` 与 `pickup` / 选择器查询的单个结果返回前 `refresh()`, 节点已离开窗口时在剩余时间内重找 (有上限的查询最多 2 次, 无上限的查询直到节点能刷新); `findOnce(index)` / `find()` / `exists()` 不校验 (附录 C Q19).
    - `auto.stats`: `queries.refinds` (单个结果重找的次数, 文本报告 `refinds N`), `fastPath.walks` (不查找而直接遍历的尝试数: 每次完整尝试之后的一次测量遍历, 以及查找比遍历更费时改为遍历的尝试, Q15 / Q17).
    - `auto.explain(selector, root?, compass?, resultType?)` (P3.5): 第二个参数是 `UiObject` 时为 root (四个参数时该位置必须是 `UiObject` 或空值), 之后的罗盘与结果类型按 `pickup` 的方式区分 (只给一个时, 是罗盘写法则为罗盘), 拒绝不抛出而写进报告. 结果对象新增 `target` / `targetIndex` / `preference` / `compass` / `compassError` / `trail` (`[{ step, node }]`) / `reached` / `result` (`{ given, kind, detail }`, kind 为 symbol / arrayForm / method / empty / nothing / refused) / `fastPath` / `fastPathEnabled` / `refreshOnHit` / `refreshes` / `normalize`; `text` 在逐过滤器计数之后追加这些行. 结果类型的方法只解析不调用.
    - 布局分析器搜索结果对话框: 新增按钮 "复制为 pickup" (字符串资源 `text_inspector_copy_as_pickup`, 11 个目录), 复制 `pickup({...})` 形式; 同一属性出现两次或条件不是 `name(参数)` 形式时复制 `pickup(<链式选择器>)`.
  - changelog 草稿 (`.changelog/lang_zh-Hans.json` 有他人未提交的改动, 未改; 由维护者同步到 6.8.0 与其他语言; 下列文字已按 JSON 转义半角双引号):
    - feature: `选择器 normalize 方法, 用于匹配文本前统一格式: 去除首尾空白, 忽略大小写, 全角转半角, 合并空白字符与 NFKC 兼容字符转换 (作用于 text / desc / content 系列条件; id / className / packageName 只去除首尾空白与忽略大小写; 例如 text('确定').normalize() 也能匹配 \" 确定 \")`
    - feature: `选择器 prefer 方法, 用于多个控件都符合条件时按偏好取单个结果, 偏好可选 visible (对用户可见) / clickable / enabled / center (靠近屏幕中心) / area (面积大) / top (靠上), 按给出的顺序依次比较 (例如 text('确定').prefer('visible', 'clickable').findOne())`
    - improvement: `等待控件出现的选择器查询 (findOne / untilFind / untilFindOne / waitFor 等) 与控件的等待方法默认由界面变化事件唤醒, 两次检查至少间隔 50 ms, 界面静止时 CPU 占用明显降低 (auto.setFlags(['!eventAssistedPolling']) 恢复固定间隔轮询)`
    - improvement: `选择器的单个结果 (findOnce / findOne / untilFindOne 及 pickup 的单个控件结果) 返回前先刷新控件, 控件已从界面消失时重新查找, 不再返回已关闭的对话框中的控件 (auto.setFlags(['noRefreshOnHit']) 可关闭; auto.stats 的 queries.refinds 记录重新查找的次数)`
    - improvement: `auto.explain 支持 auto.explain(selector, compass?, resultType?), 报告还说明单个结果取了哪个控件, 罗盘每一步到达的控件 (或停在哪一步), 结果类型对应的方法与参数转换, 以及快路径, 命中刷新与 normalize 是否生效 (参阅 示例代码 > 无障碍服务 > 选择器诊断与节点树导出)`
    - improvement: `布局分析的搜索结果对话框增加 \"复制为 pickup\" 按钮, 以 pickup({...}) 形式复制当前的筛选条件`
    - 说明: P2 的 improvement 草稿 (含精确 id 条件的等待查询先经系统按 id 查找) 与本期 Q15 / Q17 是同一版本的同一功能, 按 AGENTS.md 更新日志第 1 条合并为最终行为: 在其括号前加 "在按 id 查找比遍历更费的界面上 (例如 AutoJs6 自己的悬浮窗) 自动改为遍历". 第三条的 "50 ms" 是 D9 的最小间隔, 固定间隔轮询的默认间隔也是 50 ms.
  - 待决: Q17 (度量, e8fe4494e), Q18 (normalize 下正则的大小写, 094f4b084), Q19 (`exists()` 不校验, 4acdced3d), Q20 (命中刷新失败后立即重找, 4acdced3d) 均已按推荐先行待确认.
  - 设备: 22120RN86C / OPD2413 留着 e8fe4494e 的调试包 (versionCode 5935), 工具写的新层偏好已恢复; G8441 已恢复原装 5835 并重新绑定无障碍服务.

### 2026-10-29

- P4 文档子会话 local_4368d82c (selector-p4-docs, Opus 5.5): P4.1-P4.3 勾选, 附录 C Q17-Q20 已决; 本地提交均未推送: 宿主 ffd0b4a58 (Q17-Q20 已决), 1842d7408 (注册表快照与导出脚本), a97e9012a (P4.2 勾选与 Q21), d8941cada (Ace 补全源), a709e83a7 (`MATRIX.md`), ffdd8c25c (导出格式), 以及本条所在的 roadmap 提交; 文档 d163293 + 83c5368 (版本 169); 离线文档插件 774fb55 (VERSION_BUILD 207); 声明 0c4b188 (4.84.0); Ace 5847c8b (2.46.0, VERSION_BUILD 336). 未用设备.
  - 注册表导出 (可重复): JVM 用例 `NodeMethodRegistrySnapshotTest` 把 `NodeMethodRegistry.uiObject` 渲染为 `种类 名称(参数类型)` 排序后与 `app/src/test/resources/node-method-registry.txt` 比较 (218 个方法, 206 个名称: 控件自有 139, compat 只读 67), 不一致时写出 `app/build/node-method-registry.txt` 并失败 (有意改动注册表后用它覆盖快照); `py -3.12 -I tools/selector/registry_export.py [--check]` 读快照, 改写文档 `api/dataTypes.md` 与声明, Ace 两份 `aj6-int-selector.d.ts` 的生成块 (`--check` 只比较). 注册表相关源码 (`NodeMethodRegistry.kt` / `UiObject.kt` / `UiObjectActions.kt`) 自 e8fe4494e 未变; JVM 比 G8441 (API 28, 205 个名称) 多 `getExtraRenderingInfo`, 文档标 "(API 30+)" (Q16).
  - 校验: 文档 `normalize-markdown.py --check`, `auto-generate.py --check` 与链接锚点检查; 离线文档 `auto-generate-for-autojs6.bat --verify-offline` (单测与 debug / release APK 门禁); 声明 `npm test`, `verify-runtime-declarations.mjs --ace`, `docs:test`; Ace `generate-editor-assets.mjs --check`, `test-completer`, Gradle 两个任务; 宿主 `tools/api-coverage/check.py` (见 P4.3).
  - `MATRIX.md`: 本次在共享工作区 `check.py --matrix --write-matrix` 重生成未丢行 (2026-10-26 记录的约 400 行返回对象丢失已不再出现), 差异只有本主题的 `UiSelector#normalize` / `prefer` 两行, auto 行 Ace 列与已入库的 `speech.speaker` (c31ea30eeb) 8 行, 已提交 a709e83a7.
  - 待决: 新登记 Q21 (字符串选择器中可变参数的键不按逗号拆分, 未先行; 文档已按 (2) 写明 `prefer` 在字符串选择器中只给一个键).
  - P4.1 changelog 终稿 (待维护者写入): 写入 `.changelog/lang_zh-Hans.json` 的 v6.8.0, 然后按 AGENTS.md 更新日志第 8 条生成 zh-Hans 的 Markdown; 其他语言由维护者同步. 下列字符串已按 JSON 转义半角双引号, 可直接粘贴. 新增条目 (按分类, 插入位置见后):
    ```json
    "feature": [
      "UiSelector#normalize 方法, 用于匹配前统一文本格式: 去除首尾空白, 忽略大小写, 全角转半角, 合并空白字符及 NFKC 兼容字符转换, 可只启用其中几项 (作用于 text/desc/content 系列条件, id/className/packageName 只去除首尾空白与忽略大小写), 并支持全局方法, 对象选择器及选择器字符串语法 (例如 text('确定').normalize() 也能匹配 \" 确定 \")",
      "UiSelector#prefer 方法, 用于多个控件符合条件时按偏好选取单个结果, 偏好可选 visible (对用户可见)/clickable/enabled/center (靠近屏幕中心)/area (面积大)/top (靠上), 按给出的顺序依次比较, 并支持全局方法及对象选择器写法 (例如 text('确定').prefer('visible', 'clickable').findOne())",
      "auto.clearFlags 方法, 用于清除 auto.setFlags 设置的标志; auto.setFlags 支持在标志名前加 `!` 以清除该标志 (例如 auto.setFlags(['!eventAssistedPolling']))"
    ],
    "fix": [
      "UiObject#showTooltip/hideTooltip 在没有提示文字 (tooltip) 的控件上执行时 (含选择器, 控件集合及 pickup/detect 的同名调用), 控件所在的应用 (包括 AutoJs6 自身) 崩溃的问题 (现在控件不支持该动作时不执行并返回 false)",
      "pickup/detect 以数组形式调用控件的带参方法 (例如 ['sibling', 1], ['offset', 1]) 时报 \"未知结果类型\" 的问题"
    ],
    "improvement": [
      "等待控件出现的选择器查询 (findOne/untilFind/untilFindOne/waitFor 等) 含精确 id 条件时, 先经系统按 id 查找判断控件是否已出现, 不再每次遍历整个控件树, 等待期间的 CPU 占用降低; 在按 id 查找比遍历更费的界面上 (例如 AutoJs6 自己的悬浮窗) 自动改为遍历 (auto.setFlags(['noFastPath']) 可关闭, auto.stats 的 fastPath 记录命中与回退次数)",
      "选择器的各个条件按开销从低到高判断, 含正则或 filter 函数的选择器在其他条件不满足时不再执行正则匹配或脚本函数",
      "选择器的单个结果 (findOnce/findOne/untilFindOne 及 pickup 的单个控件结果) 返回前先刷新控件, 控件已从界面消失时重新查找, 不再返回已关闭的对话框中的控件 (find/exists/findOnce(index) 不刷新; auto.setFlags(['noRefreshOnHit']) 可关闭, auto.stats 的 queries.refinds 记录重新查找的次数)",
      "pickup/detect 的参数错误消息指明函数名与参数名, 结果类型写错时列出最多 5 个相近的写法; 全局方法 pickup/detect 在查找控件之前检查全部参数",
      "pickup/detect 的结果类型只调用控件 (UiObject) 的方法与 AccessibilityNodeInfoCompat 的无参只读方法, AccessibilityNodeInfoCompat 的 setter, getChild, findAccessibilityNodeInfosByText 等不再作为结果类型 (改报未知结果类型并列出相近写法; 获取子控件使用 child) (参阅 项目文档 > [控件可调用方法](https://docs.autojs6.com/#/dataTypes?id=uiobjectinvokable))"
    ]
    ```
    - 改写 6.8.0 已有条目 (按原文查找, 同一版本的同一功能合并为最终行为, AGENTS.md 更新日志第 1 条):
      - feature `auto.explain/dump 方法及 auto.stats 对象, 用于选择器诊断, 节点树导出及查询耗时统计` -> `auto.explain/dump 方法及 auto.stats 对象, 用于选择器诊断, 节点树导出及查询耗时统计; auto.explain 可传入罗盘与结果类型, 报告说明单个结果选取的控件, 罗盘每一步到达的控件及结果类型对应的方法 (参阅 示例代码 > 无障碍服务 > 选择器诊断与节点树导出)`
      - feature `布局分析支持 \"复制为选择器\", 将关键词, 字段条件, 筛选配置及框选区域转换为链式选择器` -> `布局分析支持 \"复制为选择器\" 与 \"复制为 pickup\", 将关键词, 字段条件, 筛选配置及框选区域转换为链式选择器或 pickup({...}) 形式` (两个按钮都在搜索结果对话框 `SearchResultsDialog`)
      - improvement `选择器及 Flow 等待支持事件辅助轮询, 无障碍事件到达时提前重试 (通过 auto.setFlags 的 eventAssistedPolling 标志启用)` -> `选择器查询 (findOne/untilFind/untilFindOne/waitFor 等), 控件的等待方法及 Flow 等待默认启用事件辅助轮询, 无障碍事件到达时提前重试, 两次检查至少间隔 50 ms, 界面静止时 CPU 占用明显降低 (auto.setFlags(['!eventAssistedPolling']) 或 auto.clearFlags(['eventAssistedPolling']) 恢复固定间隔轮询)`
    - 插入位置 (AGENTS.md 更新日志第 5 条, 相关条目相邻): normalize / prefer 接在 feature `UiSelector#findIterator 方法, ...` 之后; clearFlags 接在 feature `auto.setFlags 方法支持 appWindowsFallback 标志, ...` 之后; 两条 fix 接在 fix `UiObject#compass 及 pickup/detect 方法的罗盘参数解析与结果返回异常` 之后; 五条 improvement 接在改写后的事件辅助轮询条目之后.
    - 合并与取舍: 会话记录中 P1 / P2 / P3 与 Q8 / Q9 小卡的草稿已合并: P2 快路径与 Q15 / Q17 的自适应合为一条; P3 的事件辅助默认开, explain 扩展与 "复制为 pickup" 并入 6.8.0 已有的同一功能条目; P1 参数错误消息去掉 "新脚本模块层开启时" (开关已删除). 不列: 预取 (Q12 未采用); Q9 (集合写法失效) 与 Q16 (API 24-29 上注册表建立失败) 只出现在 6.8.0 的开发构建 (`PickResultType` 2026-09-06 引入, `NodeMethodRegistry` 本主题 P1 引入); 字符串选择器 `prefer=a,b` 待 Q21 拍板. showTooltip 的缺陷自 6.2.0 起存在, `['sibling', 1]` 的缺陷在 v6.7.0 已存在, 两条 fix 保留.
