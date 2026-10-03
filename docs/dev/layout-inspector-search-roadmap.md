# AutoJs6 布局分析 (Layout Inspector) 搜索与筛选 Roadmap

本文是布局分析工具 (布局范围分析 / 布局层次分析两个悬浮窗) "搜索与筛选" 能力的可执行状态表.
以 2026-10-02 的本地代码快照 (`master@77b5a3b0c5`, `VERSION_NAME=6.8.0`) 为起点,
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试类名 / 设备型号), 证据等级见附录 C.
3. 涉及字符串资源的条目必须同步 11 个 `strings.xml` (`values` + 10 个语言目录, `values` 与 `values-en` 内容一致); 涉及 feature / fix / improvement 的提交必须同步 `.changelog/lang_*.json` (10 个语言) 并重新生成 markdown.
4. 附录 A 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".
5. 本功能不改动脚本公开 API, 因此不涉及文档 / d.ts 仓库同步; 若某阶段决定新增选择器注册表属性 (见 Q1), 该阶段必须同步关联仓库.

---

## 1. 固定决策与硬约束

以下决策已由维护者于 2026-10-02 确认 (D1-D9 为两轮选择题的结果, D10-D17 为据此推导的实现约束), 后续阶段不再重新讨论:

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 界面形态: 窗口内置搜索栏 + 结果列表对话框 | 层次窗口与范围窗口各自顶部放置一条搜索栏 (输入框, 正则开关, 模式开关, 筛选按钮, 匹配计数, 上一个 / 下一个, 溢出菜单); 搜索栏菜单另提供 "以列表查看匹配结果" 对话框 (扁平列表 + 节点路径), 点击结果跳转并选中. 两者共享同一份会话状态 (D5). |
| D2 | 查询模式三种, 分阶段落地, 显式切换 | 单一输入框, 搜索栏上的模式开关在 "关键词" (P1) / "字段" (P4) / "选择器" (P5) 之间显式切换, 不做自动识别 (避免 `a:b` 这类关键词被误判). 默认关键词; 正则开关只在关键词模式有效 (字段模式经 `key~=` / `key=/re/flags` 表达正则, 选择器模式经正则字面量表达). 模式选择跨会话记忆. |
| D3 | 层次窗口裁剪树 | 筛选生效时只保留匹配节点及其祖先: 祖先行淡化, 匹配行高亮 (关键词模式同时标出命中子串), 自动展开到匹配节点; 清空查询后恢复完整树与筛选前的展开状态. |
| D4 | 范围窗口只绘制匹配节点 | 未匹配节点既不绘制边框也不参与点选命中; `hidden` (用户 "隐藏此节点 / 隐藏同框节点") 仍优先, 匹配但 hidden 的节点不绘制; 清空查询恢复全部. |
| D5 | 会话状态跨窗口共享, 退出即弃; 历史与配置跨会话 | 新增 `InspectorSearchSession` 保存查询文本 / 模式 / 正则开关 / 当前配置及其未保存修改 / 区域条件 / 当前匹配序号, 在同一次布局分析内随层次与范围窗口的互相切换传递 (与现有 `setLayoutSelectedNode` / `isOffline` 同一传递路径), 退出布局分析即丢弃. 跨会话持久化 (Pref) 的只有: 查询历史 (最多 20 条, 去重, 最新在前, 可逐条删除与清空), 查询模式, 筛选配置列表与已选配置 id. 区域条件与屏幕绑定, 只在会话内. |
| D6 | 筛选配置机制 | 内置 "默认" 配置 (id `default`, 不含任何条件, 不可修改 / 删除 / 重命名); 用户配置可新建 / 另存为 / 重命名 / 删除. 修改任一配置的条件后搜索栏显示 "已修改" 标记; 关闭筛选面板或切换配置时若有未保存修改, 提示 "保存 / 另存为 / 放弃"; 对默认配置选择 "保存" 实际执行 "另存为" 并明确提示 "已建立新配置 <名称>". 已选配置 id 跨会话记忆, 启动时若该 id 已不存在则回退默认. |
| D7 | 筛选维度全选 | (a) 布尔三态 (不限 / 是 / 否): clickable, longClickable, scrollable, editable, checkable, checked, selected, enabled, focusable, focused, visibleToUser, dismissable, contextClickable, accessibilityFocused; (b) 有值快捷项: 有 id / 有 text / 有 desc; (c) 类名预设多选 (OR, 见 Q5) 与数值闭区间 (depth / childCount / indexInParent, 任一端可空); (d) 几何: 最小宽 / 最小高 (排除 1 px 占位), 仅屏幕内 (边界与屏幕相交), 范围窗口拖拽矩形 (`boundsInside`); (e) 包名多选 (候选取自当前树, OR) 与动作多选 (候选取自当前树, 全部包含即 AND, 与 `action(...)` 语义一致). |
| D8 | 选择器导出随字段模式 (P4) 落地 | 搜索栏菜单 "复制为选择器": 字段 / 选择器模式直接输出链式选择器; 关键词模式按 "当前匹配节点" 实际命中的第一个属性输出 (顺序 text > desc > id > className, 分别为 `textContains` / `descContains` / `idContains` / `classNameContains`, 正则开关开启时为 `textMatch` 系列). 配置与区域条件追加为链式调用; 注册表不能表达的条件 (数值区间, 多个类名预设, 多个包名) 以 `/* ... */` 注释附在末尾, 不静默丢弃. |
| D9 | 搜索栏可收起为可拖动浮标 | 浮标显示匹配计数 (有活动查询时) 或放大镜图标; 点击展开为顶部整条. 范围窗口默认收起 (避免遮挡被分析界面), 层次窗口默认展开. 收起状态不持久化. |
| D10 | 匹配引擎复用脚本选择器基础设施 | 新增 `InspectorNode : UiNode<InspectorNode>` 适配 `NodeInfo` (`id()` 返回 `fullId`, `performAction` 恒为 false, `recycle` 空实现). 一切条件编译为 `Filter` 列表 (`Selector`), 直接复用 `StringMatcher` / `SelectorRegistry` / `SelectorStringParser` / `SelectorExplainer`. 关键词模式使用新增 `KeywordFilter` (OR 跨 text / desc / id / className, 大小写不敏感; 正则按 JS 字面量 `/re/flags` 或普通模式编译, 非法时报错; 其 `toString()` 仅为 `keyword(...)` 标签, 导出按 D8 另行映射). 注册表不能表达的配置条件以少量自定义 Filter 实现 (`IntRangeFilter`, `AnyOfFilter`, `ScreenIntersectFilter`), 其 `toString()` 必须给出合法选择器片段或 `/* ... */` 注释 (供 D8). 收益: 与脚本语义一致, 导出直接取 `Selector.toString()`, 零匹配时可用 explainer 逐过滤器解释. |
| D11 | 字段模式语法 = `SelectorStringParser` 既有语法 | 即 `select("...")` 的字符串语法: `key=value`, `key~=regex`, `key=/re/flags`, `key`, `!key`, 值含空格用引号. 不另造语法, 用户在布局分析中学到的写法可直接用于脚本. 错误信息沿用解析器的位置 / 出错条件 / 候选属性名. 需要的扩展 (比较运算) 见 Q1. |
| D12 | 选择器模式 (P5) 使用自研链式调用解析器 | `SelectorCallParser` 解析 `text("确定").clickable().depth(3)`: 方法名 + 字面量参数 (单 / 双引号字符串含转义, 整数 / 小数, true / false, 正则字面量 `/re/flags`), 允许可选前缀 `selector().` / `$().`; 每个调用经 `SelectorRegistry.apply` 落到过滤器, 未知方法 / 重载不匹配给出位置与候选. 不以 Rhino 求值: 每次击键都要求值, 需要可预测且无副作用 (备选方案见 Q2). |
| D13 | 可测逻辑与 Android 解耦 | 引擎包 `core/accessibility/inspector/**` 中除 `InspectorNode` 外不引用 Android 类型; 单测以既有 `FakeUiNode` 构树; 配置 / 历史编解码用 Gson (JVM 测试中 org.json 为 stub, Gson 可用); 屏幕相关过滤器在测试中安装 `ScreenScale.Source.fixed`. `NodeInfo` 构造器私有且含 `Rect` / `Point`, 不在 JVM 上构造. |
| D14 | `LayoutHierarchyView` 保持 Java | 新能力以 Kotlin 协作类注入 (匹配集合 / 高亮), 行状态继续在 `getViewForObject` 中由数据推导, 复用行不残留旧状态. |
| D15 | Roadmap 位于 `docs/dev/`; 每个子条目一个提交 | 与仓库既有 roadmap 的位置与风格一致. 提交只暂存本功能的文件 (工作区可能有其他会话的未提交改动). |
| D16 | 离线布局享有同样能力 | 从文件打开的布局 (`LayoutSnapshotViewer`, `isOffline = true`) 与实时捕获走同一 `NodeInfo` 树; 包名 / 动作候选取自树本身, 不依赖实时窗口列表. |
| D17 | 性能预算 (P6.1 修订, 2026-10-02) | 输入去抖 150 ms; 查询在主线程编译 (错误即时可见), 树遍历在单个共享的工作线程执行, 结果回主线程应用, 期间若有更新的请求则按序号丢弃; 导航 / 导出 / 提交经 `InspectorSearchSession.flush()` 同步求值. 原约定 (主线程求值, 10,000 节点 <= 50 ms, API 33 中端机) 在 Redmi 22120RN86C 的 5,883 节点浏览器页面上被正则关键词查询突破 (78-103 ms), 故按原约定迁至后台线程 (P6.1). |
| D18 | 类名预设按简化类名后缀匹配 (Q5 拍板, 2026-10-02) | 预设 = 后缀模式集合 + 显式排除, 匹配对象为去掉包名的简化类名: 按钮 (`*Button`), 文本 (`*TextView`, 排除 `*EditText`), 输入 (`*EditText`, `*AutoCompleteTextView`), 图片 (`*ImageView`, 排除 `*ImageButton`), 复选与开关 (`*CheckBox`, `*Switch*`, `*RadioButton`, `*ToggleButton`), 列表与滚动 (`*RecyclerView`, `*ListView`, `*ScrollView`, `*ViewPager*`, `*GridView`), 容器 (`*Layout`, `*ViewGroup`, `*Container`). 规则表位于引擎包, 可单测; 多个预设之间为 OR. |
| D20 | 附录 A 其余待决事项全部采用建议方案 (Q2-Q4, Q6, Q7 拍板, 2026-10-02) | Q2: 选择器模式维持 D12 自研解析器, 不以 Rhino 求值 (P5 已按此落地); Q3: 关键词模式维持 text / desc / id / className 四项; Q4: 不向 MCP Server 插件暴露搜索能力; Q6: 层次窗口隐藏 `hidden` 节点一事不在本 Roadmap 处理, 另行记录为 improvement; Q7: 搜索栏限制最大宽度并居中 (P1.5 已按此落地, `InspectorSearchBar` 的 `maxBarWidth`). |
| D19 | 字段模式比较运算经注册表新增 `min*` / `max*` 属性 (Q1 拍板, 2026-10-02) | P4 为选择器注册表新增 `minDepth` / `maxDepth` / `minChildCount` / `maxChildCount` / `minIndexInParent` / `maxIndexInParent`, 字段模式写法为 `minDepth=3`, 脚本 `select()` 与链式选择器同步可用; 作为脚本公开 API 变更, 该阶段必须同步文档 / d.ts / Ace 声明三个关联仓库. |
| D21 | 浮窗外观与节点信息交互追加需求 (2026-10-02) | 所有布局分析浮窗按背景调整主题色对比度; 节点多选用主题色背景替代复选框并保持行高, 按钮为 "设置 / 返回 / 复制"; 复制方式记忆为 "总是询问 / 复制对象选择器 / 复制链式选择器", 默认为询问; 搜索栏模式及相邻图标增大. 属于恢复 P6.3 时追加的界面任务, 不改变搜索语法和脚本 API. 提交 `3316bd49f3`. |

---

## 2. 范围与非目标

范围内:

- `app/src/main/java/org/autojs/autojs/ui/floating/LayoutFloatyWindow.kt` (基类: 气泡菜单, 窗口切换时的状态传递, 节点信息对话框)
- `app/src/main/java/org/autojs/autojs/ui/floating/layoutinspector/**` (两个窗口, 两个视图, 节点信息视图, 选择器片段, 离线布局打开)
- `app/src/main/java/org/autojs/autojs/core/accessibility/NodeInfo.kt` (只读消费, 不改字段)
- 新增引擎包 `app/src/main/java/org/autojs/autojs/core/accessibility/inspector/**`, 新增界面包 `app/src/main/java/org/autojs/autojs/ui/floating/layoutinspector/search/**`
- 布局 `res/layout/layout_inspector_search_bar.xml` 等, 字符串 11 个 `strings.xml`, 偏好键 `strings_donottranslate.xml`
- 单测 `app/src/test/java/org/autojs/autojs/core/accessibility/inspector/**`, `app/src/test/java/org/autojs/autojs/ui/floating/layoutinspector/**`

非目标 (本 Roadmap 不处理):

- 选择器引擎本身 (`core/automator/**`) 的语义或 API 变更; 只读复用. Q1 若拍板为新增注册表属性, 作为独立条目并同步关联仓库.
- `com.stardust.**` 兼容包中的旧 `NodeInfo` / 布局分析类.
- MCP Server 插件与远程布局检查对搜索能力的暴露 (Q4).
- "生成代码" 对话框 (`CodeGenerateDialog`), 窗口切换对话框, 节点信息对话框的既有行为.
- 层次窗口对 `hidden` 节点的显示策略 (现状: 层次窗口仍显示, 范围窗口不绘制; 见 Q6).

---

## 3. 现状诊断 (2026-10-02)

行号以快照 `77b5a3b0c5` 为准.

| 编号 | 问题 / 事实 | 锚点 |
| --- | --- | --- |
| F1 | 没有任何搜索或筛选能力; 唯一的 "排除" 手段是范围窗口的 "隐藏此节点 / 隐藏同框节点" (`NodeInfo.hidden`), 且只影响范围窗口绘制 | `LayoutFloatyWindow.kt:268`, `LayoutBoundsView.kt:66, 118, 152` |
| F2 | 层次与范围窗口互切时销毁重建, 只传递选中节点与离线标记; 搜索状态必须沿同一路径传递, 否则切换即丢失 | `LayoutFloatyWindow.kt:110, 179-199` |
| F3 | 层次窗口根视图是 `HorizontalScrollView` (键事件处理也在其上), 范围窗口根视图就是 `LayoutBoundsView`; 放入搜索栏需要各自包一层容器并把键事件处理移到容器 | `LayoutHierarchyFloatyWindow.kt:50-65`, `LayoutBoundsFloatyWindow.kt:43-52` |
| F4 | 全屏悬浮窗 flags 只有 `FLAG_LAYOUT_NO_LIMITS`, 窗口可获焦 (已处理返回键 / 音量键), 因此窗口内 EditText 可以拉起软键盘; 但项目内没有悬浮窗内放输入框的先例, IME 行为 (API 24 的 `TYPE_PHONE`, 返回键先关 IME) 需要真机验证 | `FullScreenFloatyWindow.java:19`, `FloatyWindow.java:138` |
| F5 | 层次树的子节点来自 `getSubObjects` 直接返回 `children`, 展开状态由 `mInitiallyExpandedNodes` + `reloadData` 恢复; 裁剪树可在 `getSubObjects` 层过滤, 展开状态可快照 / 还原 | `LayoutHierarchyView.java:94, 274-289, 393-405` |
| F6 | 行文本是 `summarize()` 的纯字符串, 颜色只区分选中 / 祖先 / 普通; 命中子串高亮需要 Spannable 与第三种状态 | `LayoutHierarchyView.java:324-352, 409-440` |
| F7 | 范围窗口的绘制与命中都遍历整棵树且只看 `hidden`; 匹配集合需要进入 `draw` 与 `findNodeAt` 两处 | `LayoutBoundsView.kt:116-124, 149-160` |
| F8 | `NodeInfo` 构造器私有, 字段含 `Rect` / `Point`, JVM 单测不能构造; 但其属性集合与 `UiNode` 接口 (49 个成员) 几乎一一对应 | `NodeInfo.kt:32-41`, `UiNode.kt:24` |
| F9 | 脚本选择器基础设施可直接复用: `SelectorRegistry` (属性名 -> 重载 -> 过滤器工厂), `SelectorStringParser` (字符串语法, 带位置与候选的错误), `SelectorExplainer` (逐过滤器命中计数), `Selector.toString()` (链式选择器源码), 测试用 `FakeUiNode` DSL, 且无 bridge 的 `UiSelector(null)` 可在 JVM 构造 | `SelectorRegistry.kt:45, 66`, `SelectorStringParser.kt:31, 45`, `SelectorExplainer.kt:106-111`, `Selector.kt:27`, `app/src/test/.../FakeUiNode.kt:23, 210` |
| F10 | 过滤器类除 `ScreenScale` 的默认源 (读 `ScreenMetrics`) 与 `AppFilter` (`util.App`) 外不依赖 Android; 测试已有 `ScreenScale.Source.fixed` 的用法 | `ScreenScale.kt:57-70`, `SelectorRegistryTest.kt` |
| F11 | 字符串资源中没有搜索 / 筛选相关条目; 偏好键只有 `key_dialog_layout_inspector_exit_tips` | `values/strings.xml`, `strings_donottranslate.xml:135` |
| F12 | 可用图标: `ic_filter.webp`, `ic_clear.xml`, `ic_close_*`, `ic_expand_less/more_*`, `arrow_up/down.webp`; 缺放大镜 / 正则 / 历史图标, 需补矢量图 | `res/drawable*` |

---

## 4. 阶段总览

### P0 引擎基座 (JVM, 无界面)

- [x] P0.1 `InspectorNode` (`core/accessibility/inspector/InspectorNode.kt`): `NodeInfo` 到 `UiNode<InspectorNode>` 的适配 (D10), `parent()` / `child(i)` 按需包装, 身份以 `info` 判等; 附 `InspectorNodes.preOrder(root)` 先序遍历工具. 单测用 `FakeUiNode` 覆盖遍历工具, 适配器本身只经编译与 P1 冒烟验证 (D13). 证据 E3: 提交 `c55d35e0d5`; `TreeMatchTest` 覆盖 `InspectorNodes.preOrder`, 适配器经 P1 三台设备冒烟.
- [x] P0.2 `KeywordFilter` + `InspectorQuery`: `KeywordFilter(matcher, properties)` 对 text / desc / id / className 任一命中即匹配, 记录 "命中属性" 供高亮与导出; `InspectorQuery(mode, text, regex)` 编译为 `Selector` 或带消息的错误 (`InspectorQueryError`): 关键词模式空文本 = 无条件, 普通文本大小写不敏感子串, 正则开关下按 `JsRegexUtils.compileJsOrPlainRegex` 编译 (非法模式报错); 字段 / 选择器模式在 P4 / P5 接入, 此时返回 "暂不支持" 错误. 证据 E1: 提交 `c55d35e0d5`; `KeywordFilterTest` (8) / `InspectorQueryTest` (5). 实现差异: 入口为 `KeywordFilter.of(keyword, regex, properties)`, 命中属性经 `hit(node)` / `firstMatchRange(value)` 返回; `InspectorQuery.compile()` 返回 `Compiled.Filters(List<Filter>)` 或 `Compiled.Error`, 不组装 `Selector` (`TreeMatch` 直接接收过滤器列表).
- [x] P0.3 `TreeMatch`: `TreeMatch.evaluate(root, filter)` 返回先序排列的匹配列表, 匹配集合, 保留集合 (匹配 + 祖先), 以及 `next(current)` / `previous(current)` (循环) / `indexOf`; 空过滤器返回 `TreeMatch.ALL` 哨兵 (不裁剪). 泛型于 `UiNode<N>`, 单测用 `FakeUiNode`: 多层命中, 根命中, 零命中, 循环导航. 证据 E1: 提交 `c55d35e0d5`; `TreeMatchTest` (6). 哨兵为 `TreeMatch.unfiltered()` (`isUnfiltered`), 未命名为 `ALL`.
- [x] P0.4 编译 (`:app:compileAppDebugKotlin`) 与单测 (`:app:testAppDebugUnitTest --tests "org.autojs.autojs.core.accessibility.inspector.*"`) 通过, 提交. 本阶段无用户可见变化, 不写 changelog. 证据 E1: 提交 `c55d35e0d5`; 19 个 JVM 单测通过.

### P1 关键词搜索栏 (两个窗口)

- [x] P1.1 `InspectorSearchSession` (`ui/floating/layoutinspector/search/`): D5 的会话状态持有者, 持有 `TreeMatch` 结果与监听者; `LayoutFloatyWindow` 新增 `searchSession` 属性, `showLayoutBounds` / `showLayoutHierarchy` 随选中节点一起传递; `CircularMenu` / `LayoutSnapshotViewer` 创建窗口时新建会话. 去抖 150 ms 后求值 (D17), 求值耗时以 `Log.d` 记录供 P6.1. 证据 E3: 提交 `f7e7b4b46a`; `CircularMenu` / `LayoutSnapshotViewer` 不改动, 会话在 `LayoutFloatyWindow.onCreate` 按需新建并随 `showLayoutBounds` / `showLayoutHierarchy` 传递. 求值耗时 (系统设置首页): Redmi API 33 14-20 ms, Sony API 28 14 ms, AVD API 24 3 ms.
- [x] P1.2 `InspectorSearchBar` (自定义视图 + `res/layout/layout_inspector_search_bar.xml`): 输入框, 正则开关, 筛选按钮 (P3 前隐藏), 计数 `3/17`, 上一个 / 下一个, 溢出菜单 (收起; P2 起增加结果列表 / 历史; P4 起增加复制为选择器), 以及收起态浮标 `InspectorSearchPill` (可拖动, 显示计数) (D9). 正则非法时计数位显示错误图标, 长按 / 点击显示错误消息, 树与边框恢复为无筛选状态. 新增图标: 放大镜, 正则 (`.*`), 上一个 / 下一个复用 `ic_expand_less/more`. 证据 E3: 提交 `f7e7b4b46a`; 实现差异: 暂无溢出菜单与筛选按钮 (P2 / P3 接入时再加); 收起按钮复用放大镜图标; 正则非法时计数位显示红色 `!`, 点击 toast 错误消息 (`text_inspector_invalid_regex`).
- [x] P1.3 层次窗口接入: 根视图改为垂直容器 (搜索栏 + 既有横向滚动容器), 键事件处理移到容器 (F3); `LayoutHierarchyView.setMatch(TreeMatch?)`: `getSubObjects` 只返回保留集合中的子节点, 筛选期间保留节点一律 `isInitiallyExpanded`, 首次进入筛选时快照展开集合, 清空时还原 (F5); 行渲染增加 "匹配 / 祖先 (淡化) / 普通" 三态与命中子串 Spannable 高亮 (F6, D14); 当前匹配序号变化时选中并滚动到该行. 证据 E3: 提交 `f7e7b4b46a`; 修正: `setMatch` 以 `mFiltered` 字段跟踪筛选态转换 (传入的是恒为同一引用的会话对象, 不能用 `mMatch` 判断), 清空时还原快照并补上选中节点路径; Redmi 上清空前后 uiautomator 行集完全一致.
- [x] P1.4 范围窗口接入: 根视图改为 `FrameLayout` (边框视图 + 顶部搜索栏 / 浮标), 键事件处理移到容器; `LayoutBoundsView.setMatch(TreeMatch?)`: `draw` 与 `findNodeAt` 只看匹配且非 hidden 的节点 (D4, F7); 当前匹配序号变化时勾出该节点边框 (不自动弹菜单, 点击再弹). 证据 E3: 提交 `f7e7b4b46a`; 修正: `LayoutBoundsView.onTouchEvent` 对全部事件返回 true (嵌入 `FrameLayout` 后若 ACTION_DOWN 不认领手势, ACTION_UP 不再送达, 节点菜单打不开).
- [x] P1.5 交互细节: 返回键两段式 (搜索栏展开且有文本 -> 先清空; 展开无文本 -> 收起; 否则关闭窗口), IME 打开时由 IME 先消费返回键; 音量减键保持直接退出; 输入框获焦自动弹出 IME, 收起时收回; 搜索栏在横屏与平板下占满宽度 (Q7). 范围窗口 `FLAG_LAYOUT_NO_LIMITS` 下 IME 不挤压布局, 搜索栏在顶部不受影响. 证据 E3: 提交 `f7e7b4b46a`; 细化: "展开无文本 -> 收起" 仅对默认收起的范围窗口生效 (`collapsesOnBack`), 层次窗口默认展开, 无文本时返回键直接关闭窗口. IME: `mInputShown=true` 于 API 24 / 28 / 33.
- [x] P1.6 11 个 `strings.xml` 字符串 (搜索提示, 正则, 上一个 / 下一个, 收起 / 展开, 无匹配, 正则无效, 复制为选择器占位不加), changelog feature 1 条 x 10 语言并重新生成 markdown; 真机冒烟 (附录 B 第 1-6 项) 至少覆盖 API 28 (Sony G8441) 与 API 33 (Redmi 22120RN86C), AVD API 24 验证 `TYPE_PHONE` 下的 IME; 提交. 证据 E3: 提交 `f7e7b4b46a`; 附录 B 第 1-5 项于 Redmi 22120RN86C (API 33), 第 6 项于 Sony G8441 (API 28) 与 AVD (API 24, x86). 备注: 单字母关键词经 className (`android.widget.*`) 命中几乎全部节点, 精确度留给 P4 字段模式.

### P2 结果列表与查询历史

- [x] P2.1 `SearchResultsDialog` (`AppLevelThemeDialogBuilder` + RecyclerView, 窗口类型同节点信息对话框): 每行主文本 `LayoutHierarchyView.summarize(node)`, 副文本为路径 (`FrameLayout > LinearLayout > Button`, 以简化类名拼接, 过长时省略中段) 与 `depth` / `bounds`; 点击 -> 关闭对话框, 设为当前匹配并在当前窗口跳转选中; 长按 -> 打开节点信息对话框. 零匹配时标题区显示 `SelectorExplainer` 的逐过滤器计数摘要 (如 `text 命中 12 -> clickable 后 0`), 关键词模式只有一个过滤器时显示 "无节点命中关键词". 证据 E3: 提交 `067717680f`; 入口为点击搜索栏计数位 (D1 所述溢出菜单尚未存在; 零匹配时计数位为红色 `0`, 同样可点); 行副文本两行 (路径 / `depth` 与边界); 零匹配解释: 单个过滤器显示 `text_inspector_no_node_matches_keyword`, 多个过滤器走 `SelectorExplainer` 逐过滤器计数 (`text_inspector_filter_tally`, P3 起才会出现). Redmi 22120RN86C (API 33) 与 AVD (API 24): 对话框, 点击跳转选中, 长按打开节点信息, 零匹配说明均通过.
- [x] P2.2 `QueryHistory` (引擎包, 纯逻辑: 追加去重, 上限 20, 最新在前, 删除单条, 清空; Gson 编解码) + Pref 适配 (`key_layout_inspector_query_history`); 搜索栏输入框获焦且为空时弹出历史下拉 (`ListPopupWindow`), 点击填入, 长按删除, 末项 "清空历史". 查询在 "提交" (回车 / 点击下一个 / 打开结果列表) 时写入历史, 不在每次击键时写入. 单测: `QueryHistoryTest`. 证据 E3: 提交 `067717680f`; `QueryHistoryTest` (7). 实现差异: 下拉为窗口内嵌的 `ListView` (`InspectorSearchBar.historyList`) 而非 `ListPopupWindow`: 悬浮窗之上的弹出窗口 surface 在 Redmi 22120RN86C (API 33) 上必现 HWUI 中止 (`Failed to set damage region on surface ... EGL_BAD_ACCESS`, 进程被杀); 为此层次窗口根视图改为 `FrameLayout` (树以搜索栏高度为顶部边距, 搜索栏视图铺满窗口以便下拉叠在树上), 下拉列表不可获焦以免收起 IME. 去重键为模式 + 去空白文本, 重复提交采用较新的正则开关. Redmi (API 33) 与 AVD (API 24): 获焦且为空时下拉, 点击填入 (IME 保持), 长按删除, 末行清空, 外部点击收起, 退出重进后仍在.
- [x] P2.3 字符串 + changelog (feature 1 条) + 冒烟 (附录 B 第 7-8 项) + 提交. 证据 E3: 提交 `067717680f`; 6 条字符串 x 11 文件, changelog feature 1 条 x 10 语言; 附录 B 第 7-8 项于 Redmi 22120RN86C (API 33) 与 AVD (API 24, x86). Sony G8441 (API 28) 同样通过 (重装后无障碍服务未重新绑定, 需重启设备; 设备状态问题, 与本功能无关).

### P3 筛选配置

- [x] P3.1 `FilterProfile` 模型 (引擎包): D7 全部维度的数据类 (`id`, `name`, `booleans: Map<String, Tri>`, `requireId/Text/Desc`, `classPresets`, `depth/childCount/indexInParent: IntRangeSpec`, `minWidth/minHeight`, `onScreenOnly`, `packages`, `actions`), `toFilters(): List<Filter>` (注册表可表达的经 `SelectorRegistry.apply` 产生, 其余用 `IntRangeFilter` / `AnyOfFilter` / `ScreenIntersectFilter`, D10), `isEmpty`, Gson 编解码 (未知字段忽略, 缺失字段取默认), `FilterProfileCatalog` (默认配置不可变; `save` 对默认配置返回 `CreatedNew`, 对用户配置返回 `Updated`; `rename` / `delete` 拒绝默认; 选中 id 不存在时回退默认). 单测: `FilterProfileTest` (过滤语义, 编解码往返, 旧 JSON 兼容), `FilterProfileCatalogTest` (D6 规则). 证据 E1: 提交 `8a34e16119`; `FilterProfileTest` (9) / `FilterProfileCatalogTest` (7) / `ClassPresetTest` (5), inspector 包共 47 个单测. 实现差异: 数值区间直接复用 `IntFilter.Comparison.Min` / `Max` (渲染为 `minDepth(n)` 等, D19 落地后即为合法选择器), 不另设 `IntRangeFilter`; 有值快捷项渲染为 `idMatch(".")` / `textMatch(".")` / `descMatch(".")`; 类名预设为 `ClassPresetFilter` (D18 规则 + 等价正则导出 `classNameMatches("...")`, 容器规则为 `*Layout` / `*ViewGroup` / `*Container*`); 最小宽高复用 `MetricsFilter` (像素); 屏幕内为 `ScreenIntersectFilter` (`/* onScreen */`); 多包名为 `AnyOfFilter` (注释); 动作复用 `ActionFilter` (AND 语义; 其 `toString()` 不加引号, P4.2 导出需补引号). 目录: `saveAs(profile, name)` 是唯一建立入口 (对默认配置 "保存" 即走它), `canUpdate` / `update` / `rename` / `delete` 拒绝默认配置, `select` 对未知 id 回退默认; JSON 只存用户配置, 已选 id 由界面层另存.
- [x] P3.2 `FilterProfileDialog`: 顶部配置下拉 (默认 + 用户配置) 与 "已修改" 标记; 分区: 布尔三态行 (三段选择), 有值快捷项 (三个开关), 类名预设 (Chip 多选, 另附 "当前树中的类名" 可展开列表), 数值区间 (两个数字输入), 几何 (最小宽 / 高, 仅屏幕内开关, 区域条件只读显示与清除), 包名 (Chip 多选, 候选来自当前树), 动作 (Chip 多选, 候选来自当前树); 底部 "应用" / "保存" / "另存为" / "重置". 候选统计在求值线程完成 (D16). 证据 E3: 提交 `d0094ff4fd`; 入口为搜索栏新增的筛选按钮 (角标 = 条件数, 含区域条件, 超过 9 显示 "9+"); `FilterProfileDialog` + `layout_inspector_filter_dialog.xml` (`AppLevelThemeDialogBuilder` 自定义视图, 可滚动): 配置头部 (选择器 / "已修改" / 改名 / 删除, 默认配置显示只读提示并禁用改名删除), 布尔三态为每行 3 个 RadioButton, 有值快捷项与预设为 CheckBox (Flexbox 换行), "当前树中的类名 (N)" 可展开为等宽文本, 区间与几何为数字输入框, 区域行 (显示 / 框选 / 清除), 包名与动作候选 = 当前树候选 ∪ 配置已有值 (树中不存在的加 "*"). 实现差异: 修改即时应用, 因而无 "应用" 按钮 (正按钮 = 保存 / 完成, 中性 = 另存为, 负按钮 = 重置); 配置选择器为自建主题化列表而非 MaterialDialog `items()` (后者用 application context 加载列表行, 无法解析 `?selectableItemBackground`, Redmi API 33 必现 InflateException); 候选统计 `TreeCandidates` 在主线程按需计算并缓存 (Settings 首页 126 节点 < 1 ms, D16 的求值线程留待 P6 性能项), 单测 `TreeCandidatesTest` (1), inspector 包共 48 个单测. Redmi 22120RN86C (API 33) 与 AVD (API 24): 对话框打开, 各分区渲染, 勾选 clickable = 是后角标 "1" 与计数 17 / 11 即时出现.
- [x] P3.3 保存流程 (D6): 修改即标记 dirty 并实时应用到会话; 关闭面板 / 切换配置时的 "保存 / 另存为 / 放弃" 提示; 默认配置的 "保存" 走另存为并提示 "已建立新配置"; 名称输入对话框校验非空与不重名; Pref 持久化 (`key_layout_inspector_filter_profiles`, `key_layout_inspector_filter_profile_selected`); 搜索栏筛选按钮在配置非空时显示角标. 证据 E3: 提交 `d0094ff4fd`; `FilterProfileStore` (`key_layout_inspector_filter_profiles` 仅在有用户配置时写入, `..._selected` 仅在非默认时写入); 会话 `setProfile` / `saveProfile` / `saveProfileAs` / `discardProfileEdits` / `renameProfile` / `deleteProfile`; 名称对话框实时校验 (空 -> 禁用确定, 重名 -> 输入框错误提示). Redmi (API 33) 与 AVD (API 24): 默认配置 "保存" -> 名称 -> "已建立新配置", 自动选中且无 "已修改"; 完成后角标保留, Pref 已写入; 退出重开仍选中并生效 (计数 17 / 11); 选择器切回默认; 修改默认后返回键 -> "未保存的修改" (保存 / 另存为 / 放弃), 放弃后角标消失; 重命名 Clickables -> Clickables2; 删除后回退默认且两个 Pref 键均被移除 (附录 B 第 9-10 项).
- [x] P3.4 区域拖拽 (范围窗口): 搜索栏菜单 "框选区域" 进入一次性拖拽模式 (边框视图不再点选节点, 绘制半透明矩形), 抬手后写入会话的 `boundsInside(l, t, r, b)` 条件并在搜索栏显示可清除的区域 Chip; 切到层次窗口同样生效 (D5); 再次框选覆盖旧区域. 证据 E3: 提交 `d0094ff4fd`; `LayoutBoundsView.startRegionSelection` (一次性手势, 拖拽中绘制主题色虚线框 + 12% 填充, 小于触摸阈值取消, 返回键取消), 范围窗口 Host `onSelectRegionRequested` (收起搜索栏 + Toast 提示), 会话 `setRegion` -> `BoundsFilter(..., TYPE_INSIDE)`. 实现差异: 入口为筛选对话框的 "框选区域" 按钮而非搜索栏菜单 (菜单尚不存在, 见 D8 / P4); 区域不以 Chip 显示而计入筛选角标, 在对话框区域行查看与清除; 层次窗口的 Host 返回 false 并提示到范围窗口操作. Redmi (API 33): 拖出 (60,820)-(700,1050) 后 10 匹配, 角标 1, 对话框显示 "区域: [60,820][700,1050]", 经长按节点菜单切到层次窗口后计数 2/10 保持, 清除后恢复空计数 (附录 B 第 12 项); AVD (API 24): 全屏带状区域 4 匹配, 清除后恢复. 注意: 从屏幕边缘 (x 约 < 30 px) 起手的拖拽会被系统手势导航截获 (视图收到 ACTION_CANCEL), 属平台行为.
- [x] P3.5 字符串 (预计 40+ 条) + changelog (feature 2 条: 配置机制, 区域框选) + 冒烟 (附录 B 第 9-13 项) + 提交. 本阶段提交可按 P3.1 / P3.2-3.3 / P3.4 拆为三个. 证据 E3: 字符串 44 条 x 11 文件 (提交 `d0094ff4fd`), changelog feature 2 条 x 10 语言 + README / CHANGELOG 再生成 (提交 `d19e06b364`). 附录 B 第 11 项 (Redmi, API 33, Settings 首页 126 节点) 与脚本选择器参照计数逐项一致: clickable 否 109 / 有 text 18 / 预设按钮 0 (该页无 `*Button` 类) / depth 3..5 共 3 / 最小宽高 2 共 101 / 仅屏幕内 107 / 包名 com.android.settings 126 / ACTION_SCROLL_FORWARD 1; AVD (API 24) 核对了 clickable 否 11 / 有 text 2 / 按钮 5 / depth 3..5 13 / 最小宽高 19 / ACTION_CLICK 6 (该树无 scroll-forward; 仅屏幕内与包名两项因 uiautomator 在 IME 收起后定位不到复选框而未自动化, 逻辑已在 Redmi 核对). 第 13 项 (离线布局) 未冒烟, 留待 P6. Sony G8441 (API 28) 本轮未覆盖: 重装后需重启才能重新绑定无障碍服务, 而重启后要求图案解锁. 本阶段按 P3.1 / P3.2-3.4 / P3.5 (changelog) 三个提交落地; 区域条件与对话框 / 搜索栏接口耦合, 未再单独拆出 P3.4.

### P4 字段模式与选择器导出

- [x] P4.1 模式开关接入 "字段" 模式: `InspectorQuery` 字段模式经 `SelectorStringParser.parse(text, UiSelector(null))` 得到过滤器 (D11); 解析错误显示位置, 出错条件与候选属性名 (复用 `SelectorStringException` 的渲染); 输入框下方 (展开态) 显示一行语法速查提示, 首次切换时 toast 指向附录 E 的语法. 证据 E3: 提交 `9d53d9fa5a`; `InspectorQueryTest` (字段模式解析, 语法错误携带 position / term / candidates). 实现差异: 模式开关为搜索栏上的 `Aa` / `k=v` 文字按钮 (选择器模式待 P5 加入循环), 非关键词模式下隐藏正则开关; 语法速查与错误共用搜索栏下方的窗口内提示条 (与历史下拉同理, 不在悬浮窗上开弹窗), 字段模式输入框获焦时显示速查, 查询无法编译时显示红色错误 (`第 N 位: ...` + `是否想输入: ...`), 计数位同时显示 `!` 并把错误放入 contentDescription; 历史行带 `k=v` 标记; 首次切到字段模式 toast 一次 (`key_layout_inspector_field_mode_introduced`), 模式经 `key_layout_inspector_query_mode` 跨会话记忆 (`QueryModeStore`). Redmi 22120RN86C (API 33) 与 AVD (API 24): `text=WLAN clickable` / `id~=title` / `!enabled` / `text=WLAN` 的计数 (0 / 13 / 2 / 1) 与脚本选择器参照 (`counts_p4.js`) 一致; `text=ok clikable` 显示 `At 8: unknown property "clikable" / Did you mean: clickable`; 退出再打开分析窗口后仍为字段模式.
- [x] P4.2 "复制为选择器" (D8): 会话当前条件 (查询 + 配置 + 区域) 合成 `Selector.toString()`; 关键词模式按当前匹配节点命中的属性输出; 不可表达条件以注释附加; 复制后 toast "已复制". 单测: `SelectorExportTest` (关键词命中属性映射, 注释附加, 正则开关). 证据 E3: 提交 `9d53d9fa5a`; `SelectorExportTest` (8). 实现差异: `SelectorExport.render(query, queryFilters, hit, extraFilters)` 逐过滤器取 `toString()` 而非组装 `Selector` (关键词按当前匹配命中的属性映射, 无当前匹配时取首个匹配, 再回退 text); `ActionFilter` 重新以带引号名称渲染; 以 `/*` 开头的片段归入尾随注释; 入口为结果对话框的中性按钮 "复制为选择器" 与长按计数位 (搜索栏仍无溢出菜单), 复制文本同时写入 logcat (`SelectorClipboard`); D19 落地后 depth 区间输出为合法调用而非注释. Redmi (API 33) 与 AVD (API 24): 字段查询经对话框按钮得到 `text("WLAN").clickable()`, 关键词得到 `textContains("WLAN")`, 正则开关得到 `textMatch(/^WL/)`, 加 clickable = 是 + depth 3..6 (另存为 P4Smoke) 得到 `textContains("WLAN").clickable(true).minDepth(3).maxDepth(6)`; 四段文本经 `export_check.js` 以脚本运行, 计数与分析窗口一致 (Redmi 0 / 1 / 1 / 0); 查询有错时 toast 错误且不复制.
- [x] P4.3 Q1 落地 (D19): 选择器注册表新增 `minDepth` / `maxDepth` / `minChildCount` / `maxChildCount` / `minIndexInParent` / `maxIndexInParent` (脚本 `select()` 与字段模式同步可用, 单测覆盖边界与非法值), 并同步文档 / d.ts / Ace 声明三个关联仓库 (使用方式第 5 条). 证据 E1: 宿主提交 `46e0692558` (`minChildCount` / `maxChildCount` 自 6.2.0 已存在, 实际新增四个; `Entries.intBounds` 统一派生三对, 表 129 项 / 196 重载; `SelectorRegistryTest.integerBoundsFilterInclusively`, `SelectorStringParserTest.integerBoundsReadAsMinAndMaxKeys`; `UiSelectorBuilders` 新增四个 `@ScriptInterface` 方法, `UiSelectorBuildersTest` 通过); 文档 `de25422` (`api/uiSelectorType.md` 四节 + `select()` 示例, 页面已重新生成); d.ts `0bceb07` (4.29.0, 另补上 int 声明缺失的 `childCount` / `minChildCount` / `maxChildCount`, `aj6dts -Publish` 更新 aj6-main-app / aj6-resources, tsc 对 index.d.ts 与新调用无错误); Ace `ed62609` (1.21.0, build 123, 内置类型 4.29.0, LSP 声明重新生成). 三个仓库均仅本地提交.
- [x] P4.4 字符串 + changelog (feature 2 条) + 冒烟 (附录 B 第 14-15 项) + 提交. 证据 E3: 字符串 10 键 x 11 文件随 `9d53d9fa5a`; changelog feature 3 条 x 10 语言 (字段模式, 复制为选择器, 选择器 `min*` / `max*` 方法) `ec8dbfc9b3`; 附录 B 第 14-15 项于 Redmi 22120RN86C (API 33) 与 AVD (API 24, x86; 其 Settings 无 WLAN 文本故计数为 0, 导出与脚本复跑仍一致), 脚本 `build/inspector-smoke/p4_redmi.sh` (阶段 7-8, 设备感知) + `counts_p4.js` + `export_check.js`; Sony G8441 (API 28) 仍因重装后重启的图案锁未覆盖.

### P5 选择器模式

- [x] P5.1 `SelectorCallParser` (引擎包, D12): 词法 (标识符, `.`, `(`, `)`, `,`, 字符串, 数字, 布尔, 正则字面量, 空白), 语法 (可选 `selector()` / `$()` 前缀, 一个或多个 `name(args)` 以 `.` 相连, 允许末尾不完整以便边输入边求值时给出 "表达式未结束" 而非误报), 每个调用经 `SelectorRegistry.apply(selector, name, args)`; 错误带位置与候选 (复用 `SelectorStringParser.suggest`). 正则字面量转为 Rhino `NativeRegExp`? 否: 注册表 REGEX 参数类型要求 `NativeRegExp`, 解析器改走字符串重载的 JS 字面量形式 (`textMatches("/re/i")`), 由 `StringMatcher` 的 `compileJsOrPlainRegex` 保留标志 (验证 `textMatches` / `textMatch` 支持该形式; `text(/re/)` 这类 equals 形式的正则在字符串重载中不保留整体匹配语义, 需在解析器中映射为 `textMatches("/^(?:re)$/flags")`). 单测: `SelectorCallParserTest`. 证据 E1: 提交 `f17be99dab`; `SelectorCallParserTest` (10). 实现差异: 正则字面量交给同名 (`...Match` / `...Matches`) 或改名后 (`...Contains` -> `...Match`, 等值形式 `name` -> `nameMatches`) 方法的字符串重载, 由 `StringMatcher` 的 `compileJsOrPlainRegex` 保留标志; 等值形式无需 `^(?:re)$` 锚定, 因为 `matches(s)` 本就是整体匹配 (与 `equals(NativeRegExp)` 语义相同); 脚本不接受 `RegExp` 的位置 (如 `textStartsWith(/x/)`, `depth(/3/)`) 报错. 方法名在读到其 `(` 时即对照注册表 (`clikable(` 为未知, `clikable` 仍为未结束); 词法按需产出, 错误报告在读取实际停下的位置; 字符串转义支持 `\n \t \r \b \f \0 \uXXXX` 与引号 / 反斜杠.
- [x] P5.2 模式开关接入 "选择器" 模式, 错误显示同 P4.1; "复制为选择器" 在此模式下原样输出规范化后的表达式. 证据 E3: 提交 `f17be99dab`; `InspectorQueryTest` (选择器模式编译, 语法错误与未结束分开报告). 实现差异: 模式开关循环 `Aa` -> `k=v` -> `$()`, 首次切到选择器模式 toast 一次 (`key_layout_inspector_selector_mode_introduced`); 新增 `InspectorQueryError.Kind.INCOMPLETE` 取代 P1 的 `UNSUPPORTED_MODE` 占位 (字符串 `text_inspector_query_mode_unavailable` 已删除): 未结束时计数位显示 `…` (普通色), 提示条以提示色显示速查 (获焦) 或 "表达式未结束", 不显示红色错误; 历史行带 `$()` 标记; 导出即过滤器的 `toString()` 链 (如 `text(/wlan/i).clickable(false)` 导出为 `textMatches("/wlan/i").clickable(false)`). Redmi 22120RN86C (API 33): `text("WLAN")` 1 / `textMatches(/WLAN|Bluetooth/i)` 2 / `clickable().minDepth(3).maxDepth(6)` 0 / `selector().enabled(false)` 2 / `$().idMatch(/title/)` 13, 与脚本参照一致; `text("` 与 `text("WLAN").cli` 显示 `…`; `text("WLAN").clikable()` 显示 `At 13: unknown selector method "clikable" / Did you mean: clickable`, `textMatch(/(/)` 与 `depth("x")` 显示 `!`; 两段导出文本以脚本复跑计数一致; 退出再打开仍为选择器模式. AVD (API 24, x86) 通过同一脚本: 计数为 0 (其 Settings 无 WLAN 文本) 但 `$().idMatch(/title/)` 1, 未结束 / 错误 / 候选 / 导出 / 历史标记 / 持久化行为与 Redmi 一致 (需先 `settings put secure spell_checker_enabled 0`, 否则 AOSP 键盘的拼写弹窗吞掉输入).
- [x] P5.3 字符串 + changelog (feature 1 条) + 冒烟 (附录 B 第 16 项) + 提交. 证据 E3: 字符串 4 键 x 11 文件 (含 `\"` 转义, 否则 Android 资源会吞掉 `name("str")` 中的引号) 随 `f17be99dab`; changelog feature 1 条 x 10 语言 `4458baa4b9`; 附录 B 第 16 项于 Redmi 22120RN86C (API 33) 与 AVD (API 24, x86), 脚本 `build/inspector-smoke/p5_redmi.sh 9` (复用 `counts_p4.js` / `export_check.js`); Sony G8441 (API 28) 仍因图案锁未覆盖.

### P6 质量, 性能与文档

- [x] P6.1 性能: 用 `FakeUiNode` 构造 10,000 节点合成树的 JVM 基准 (`TreeMatchBenchmarkTest`, 仅记录耗时不断言), 与真机复杂页面 (如系统设置 / 浏览器) 的 `Log.d` 耗时对照; 超出 D17 预算则迁至后台线程 (序号丢弃过期结果) 并更新 D17. 证据 E3: 提交 `daaf12413f`. JVM 基准 `TreeMatchBenchmarkTest` (10,000 节点, 深度 6, 3 次预热后 7 次取中位数, 开发机): 先序遍历 0.5 ms, 关键词 3.6 ms, 关键词正则 5.9 ms, 字段 3.1 ms, 选择器 2.6 ms, 11 个过滤器的配置 2.2 ms, 关键词 + 配置 3.2 ms, 候选扫描 3.8 ms. 真机 (Redmi 22120RN86C, API 33, 改动前主线程求值, `InspectorSearchSession` 的 evaluate 日志): 系统设置首页 126 节点 2-18 ms; 原生浏览器中的 Wikipedia 条目 681 节点 3-17 ms (首次 44 ms); 原生浏览器中 2,500 行 HTML 页面 5,883 节点: 关键词 35-57 ms, 关键词正则 78-103 ms, 字段 11-55 ms, 选择器 13-25 ms. 正则关键词突破 50 ms 预算, 故按 D17 约定把树遍历迁至工作线程 (查询仍在主线程编译, 结果按序号回主线程应用, `flush()` 同步兜底, 单个共享工作线程空闲 10 s 退出); 改动后同一页面遍历 15-107 ms 全部在工作线程 (日志尾部 `walk N ms on the worker`), 系统设置首页 0-1 ms, 主线程只承担编译与应用. 备注: 10,000 个 View 的脚本 UI 页面 (与无障碍服务同进程) 捕获到的树只有 1 个节点, 原因未查, 真机基准改用浏览器页面; MIUI 浏览器拒绝 file:// 且 Windows 防火墙拦截局域网访问, 页面经 `adb reverse tcp:8765` 取自 PC 上的 `python -m http.server`; AOSP HTML Viewer 的 WebView 不暴露 DOM 节点. 脚本 `build/inspector-smoke/p6_bench.sh settings|browser|http` + `big_tree.html`.
- [x] P6.2 架构说明 `docs/dev/layout-inspector-search-architecture.md`: 引擎分层 (`InspectorNode` -> `Selector` -> `TreeMatch`), 会话状态与窗口生命周期, 配置存储格式 (JSON 结构与兼容规则), 三种模式的语法入口与错误渲染, 与脚本选择器引擎的依赖面 (只读复用的类清单), 可测性. 证据 E0: 提交 `1aefdd45a6` (纯文档, 含 P6.1 后的工作线程遍历与 D17 修订).
- [x] P6.3 全机队冒烟 (附录 B 全部): AVD API 24, Sony G8441 API 28, Sony XQ-AT72 API 31, Redmi 22120RN86C / Sony XQ-DQ72 API 33, Xiaomi Pad 23046RP50C API 35; 记录 IME 行为差异与悬浮窗焦点问题. 证据 E3 (2026-10-03): 六台完成主流程及实时 / 离线区域复核, 新界面设备测试 18/18 通过. 获准重启 Sony G8441 和 Redmi 后服务恢复, API 28 补齐附录 B 全部检查, 6 组导出与脚本计数一致, 并验证应用进程重启后的历史 / 模式 / 配置记忆; API 24 保存配置后的 Done 关闭缺项已补齐. 详见 [设备验证记录](layout-inspector-search-device-validation.md), 含 IME / 焦点差异, 测试工具修正和重启后的实际服务状态.
- [ ] P6.4 用户文档: 文档项目 (`D:/webstorm-projects/AutoJs6-Documentation`) 目前没有布局分析专页 (只在 `api/app.md` 提及), 本阶段评估是否新增 "布局分析" 使用页 (含搜索语法速查); 若新增则按 `AGENTS.md` 的文档生成流程同步. 可选, 由维护者拍板.

---

## 附录 A. 待决事项

| 编号 | 问题 | 建议 |
| --- | --- | --- |
| Q1 | (已拍板 2026-10-02: 选 (b), 见 D19) 字段模式是否支持比较运算 (`depth>3`, `childCount<=2`)? 注册表对 width / height / left 等有 `min*` / `max*` 属性 (`minWidth=100`), 但 depth / childCount / indexInParent 只有精确匹配. | 三个选项: (a) 解析器层糖 (`key>n` 映射到 `IntRangeFilter`, 仅布局分析可用, 破坏 D11 "一套语法" 承诺); (b) 注册表新增 `minDepth` / `maxDepth` / `minChildCount` / `maxChildCount` / `minIndexInParent` / `maxIndexInParent` (脚本 API 新增, 需同步文档 / d.ts / Ace 声明); (c) 不做, 区间需求走筛选配置的数值区间. 建议 P4 先 (c), 若使用中确有需要再按 (b) 独立立项. |
| Q2 | (已拍板 2026-10-02: 采用建议, 见 D20) 选择器模式是否改用 Rhino 求值 (在无 bridge 的 `UiSelector` 上执行用户输入)? | 建议维持 D12 的自研解析器: 每次击键求值, 需要无副作用与毫秒级返回; Rhino 方案只在解析器覆盖不了的写法 (模板字符串, 变量) 成为真实诉求时再评估. |
| Q3 | (已拍板 2026-10-02: 采用建议, 见 D20) 关键词模式的匹配字段是否纳入 `packageName` / `idHex` / `fullId` 之外的属性 (如 `actionNames`)? | 建议维持 text / desc / id (完整资源 id, 子串即覆盖简单 id) / className 四项; 包名命中会让 `com` 之类的关键词匹配整棵树, 走筛选配置的包名维度更合适. |
| Q4 | (已拍板 2026-10-02: 采用建议, 见 D20) 是否把搜索能力暴露给 MCP Server 插件的远程布局检查? | 建议不做: 远程侧已有 `auto.dump` / 选择器查询, 本功能面向手机端交互. |
| Q5 | (已拍板 2026-10-02: 后缀启发式 + 例外表, 见 D18) 类名预设如何匹配: 固定类名清单, 还是按简化类名后缀的启发式 (如 "按钮" = 以 `Button` 结尾, 含 `ImageButton` / `MaterialButton` / `AppCompatButton`)? | 建议后缀启发式 + 显式例外表: 按钮 (`*Button`), 文本 (`*TextView`, 不含 `*EditText`), 输入 (`*EditText`, `*AutoCompleteTextView`), 图片 (`*ImageView`, 不含 `*ImageButton`), 复选与开关 (`*CheckBox`, `*Switch*`, `*RadioButton`, `*ToggleButton`), 列表与滚动 (`*RecyclerView`, `*ListView`, `*ScrollView`, `*ViewPager*`, `*GridView`), 容器 (`*Layout`, `*ViewGroup`, `*Container`), Web (`*WebView`), 其他 (不属于以上任一). 清单在 P3.2 落地时固定并写入附录 E. |
| Q6 | (已拍板 2026-10-02: 采用建议, 见 D20) 层次窗口是否也应隐藏 `hidden` 节点 (现状与范围窗口不一致)? | 本 Roadmap 不处理; 建议另行记录为 improvement, 因为它改变既有行为. |
| Q7 | (已拍板 2026-10-02: 采用建议, 见 D20) 搜索栏在横屏 / 平板的宽度策略: 占满宽度, 还是限制最大宽度 (如 480 dp) 并居中? | 建议限制最大宽度并居中, 与节点信息对话框的观感一致; P1.5 落地时按此实现, 若维护者另有偏好在此回填. |

## 附录 B. 冒烟清单

1. 范围窗口: 浮标默认收起, 点击展开, 输入关键词后只剩匹配边框, 未匹配区域点击无响应; 上一个 / 下一个在匹配项间循环并勾出边框; 清空后恢复全部 (P1.4).
2. 层次窗口: 搜索栏默认展开, 输入后树只剩匹配节点及祖先, 祖先淡化, 命中子串高亮; 清空后树与展开状态与筛选前一致 (P1.3).
3. 两个窗口互切: 查询文本, 正则开关, 当前匹配序号均保留; 退出布局分析再进入, 查询为空 (P1.1, D5).
4. 正则开关: `/确定|取消/i` 命中; 非法模式 `(` 显示错误图标与消息, 树与边框恢复无筛选 (P1.2).
5. 返回键两段式: 有文本先清空, 无文本收起, 再按关闭窗口; IME 打开时返回键先关 IME; 音量减直接退出 (P1.5).
6. API 24 AVD (`TYPE_PHONE`) 与 API 28 真机: 输入框可获焦并弹出 IME, 输入后 IME 收回不影响窗口 (F4).
7. 结果列表: 行显示摘要与路径, 点击跳转选中并关闭对话框, 长按打开节点信息; 零匹配时显示 explainer 摘要 (P2.1).
8. 查询历史: 回车 / 下一个 / 打开结果列表时写入, 最多 20 条且去重, 下拉可填入 / 长按删除 / 清空; 退出应用重启后仍在 (P2.2).
9. 筛选配置: 默认配置不可改名 / 删除; 勾选 clickable = 是后搜索栏角标出现, "已修改" 标记出现; 关闭面板提示保存, 选 "保存" 后提示已建立新配置并自动选中它 (P3.2, P3.3, D6).
10. 配置跨会话: 退出并重新打开布局分析, 已选配置仍被选中并生效; 删除该配置后再次打开回退默认 (P3.3).
11. 各维度: 布尔三态 "否", 有 text, 类名预设 "按钮", depth 区间 `3..5`, 最小宽高 `2`, 仅屏幕内, 包名多选, 动作 `ACTION_SCROLL_FORWARD` 各一次, 匹配计数与预期一致 (P3.1, P3.2).
12. 区域框选: 范围窗口拖出矩形后只剩区域内节点, 搜索栏出现区域 Chip, 切到层次窗口同样生效, 清除 Chip 后恢复 (P3.4).
13. 离线布局 (从文件打开): 以上搜索 / 配置 / 区域全部可用, 包名与动作候选来自文件内容 (D16).
14. 字段模式: `text=确定 clickable` / `id~=btn_.*` / `!enabled` 各一次; 错误 `foo=1` 显示位置与候选属性名 (P4.1).
15. 复制为选择器: 关键词命中 text 时得到 `textContains("...")`; 加配置 clickable 后得到 `textContains("...").clickable()`; depth 区间以注释附加; 粘贴到脚本编辑器可运行 (P4.2). 备注 (2026-10-02): D19 落地后 depth 区间输出为合法调用 `minDepth(3).maxDepth(6)`, 配置的 clickable 输出为 `clickable(true)`.
16. 选择器模式: `text("确定").clickable()` / `textMatches(/确定|取消/i)` / 输入到 `text("` 为止显示 "表达式未结束" 而非错误 (P5.1, P5.2). 备注 (2026-10-02): 未结束时计数位为 `…`, 提示条在获焦时优先显示语法速查.

## 附录 C. 证据等级

| 等级 | 含义 |
| --- | --- |
| E0 | 代码已提交, 编译通过 |
| E1 | E0 + JVM 单测通过 |
| E2 | E1 + AVD 冒烟通过 (记录 API level) |
| E3 | E2 + 真机冒烟通过 (记录机型) |

## 附录 D. 文件地图

| 关注点 | 文件 |
| --- | --- |
| 引擎 (新增) | `core/accessibility/inspector/InspectorNode.kt` (P0.1), `KeywordFilter.kt` / `InspectorQuery.kt` (P0.2), `TreeMatch.kt` (P0.3), `QueryHistory.kt` (P2.2), `FilterProfile.kt` / `FilterProfileCatalog.kt` / `InspectorFilters.kt` (P3.1), `SelectorExport.kt` (P4.2), `SelectorCallParser.kt` (P5.1, 含 `SelectorCallException`) |
| 引擎 (复用, 只读) | `core/automator/UiNode.kt`, `core/automator/filter/{Filter,Selector,StringMatcher,StringProperty,StringPropertyFilter,BooleanFilter,IntFilter,MetricsFilter,BoundsFilter,ActionFilter,JsRegexUtils}.kt`, `core/automator/selector/{SelectorRegistry,SelectorEntry,SelectorStringParser}.kt`, `core/automator/diagnostics/SelectorExplainer.kt`, `core/accessibility/UiSelector.kt` (无 bridge 构造) |
| 界面 (新增) | `ui/floating/layoutinspector/search/InspectorSearchSession.kt` (P1.1), `InspectorSearchBar.kt` / `InspectorSearchPill.kt` (P1.2), `SearchResultsDialog.kt` (P2.1), `FilterProfileDialog.kt` (P3.2), `QueryModeStore.kt` (P4.1), `SelectorClipboard.kt` (P4.2), `res/layout/layout_inspector_search_bar.xml`, `res/layout/layout_inspector_search_pill.xml`, `res/layout/dialog_inspector_search_results*.xml`, `res/layout/dialog_inspector_filter_profile*.xml`, `res/drawable/ic_inspector_search.xml`, `ic_inspector_regex.xml`, `ic_inspector_history.xml` |
| 界面 (改动) | `ui/floating/LayoutFloatyWindow.kt` (会话传递, 菜单), `ui/floating/layoutinspector/LayoutHierarchyFloatyWindow.kt` / `LayoutHierarchyView.java` (P1.3), `LayoutBoundsFloatyWindow.kt` / `LayoutBoundsView.kt` (P1.4, P3.4), `LayoutSnapshotViewer.kt` (会话创建), `ui/floating/CircularMenu.java` (会话创建) |
| 偏好键 | `res/values/strings_donottranslate.xml`: `key_layout_inspector_query_history`, `key_layout_inspector_query_mode`, `key_layout_inspector_field_mode_introduced`, `key_layout_inspector_selector_mode_introduced`, `key_layout_inspector_filter_profiles`, `key_layout_inspector_filter_profile_selected` |
| 字符串 | `res/values{,-ar,-en,-es,-fr,-ja,-ko,-ru,-zh,-zh-rHK,-zh-rTW}/strings.xml` |
| 单测 | `app/src/test/java/org/autojs/autojs/core/accessibility/inspector/*Test.kt`, 复用 `app/src/test/java/org/autojs/autojs/core/automator/FakeUiNode.kt` |
| 文档 | 本文, `docs/dev/layout-inspector-search-architecture.md` (P6.2) |

## 附录 E. 语法速查

字段模式 (D11, 即 `select("...")` 字符串语法, 以空白分隔多个条件, 全部为 AND):

| 写法 | 含义 | 示例 |
| --- | --- | --- |
| `key=value` | 属性等于 / 单参数重载 | `text=确定`, `depth=3`, `minDepth=3` (D19), `className=Button`, `bounds=0,0,1080,200` |
| `key="a b"` / `key='a b'` | 含空格的值 (转义 `\"` `\\` `\n` `\t`) | `text="Sign in"` |
| `key~=regex` / `key=/re/flags` | 正则搜索 (`keyMatch`), 适用于 text / desc / content / id / className / packageName | `id~=btn_.*`, `text=/确定|取消/i` |
| `key` | 无参重载 (布尔为 true) | `clickable`, `scrollable` |
| `!key` | 布尔为 false | `!enabled` |

选择器模式 (D12) 支持的字面量: `"str"` / `'str'` (含转义), 整数与小数, `true` / `false`, `/re/flags`; 方法名以 `SelectorRegistry.names` 为准 (与脚本 `UiSelector` 一致), 可选前缀 `selector().` / `$().`. 正则字面量只能出现在脚本接受 `RegExp` 的位置 (`name` / `nameContains` / `nameMatch` / `nameMatches`), 落地为对应 `Match` / `Matches` 方法的 JS 字面量字符串重载 (P5.1).

类名预设清单 (D18, `ClassPreset`, P3.2 落地时固定; 规则按简化类名匹配, 导出为等价的 `classNameMatches("...")` 正则):

| 预设 | 包含 (简化类名) | 排除 |
| --- | --- | --- |
| 按钮 `BUTTON` | `*Button` | `*RadioButton`, `*ToggleButton`, `*CompoundButton` (归入复选与开关) |
| 文本 `TEXT` | `*TextView` | `*AutoCompleteTextView` (归入输入框) |
| 输入框 `INPUT` | `*EditText`, `*AutoCompleteTextView` | - |
| 图片 `IMAGE` | `*ImageView` | - (`*ImageButton` 不以 `ImageView` 结尾, 自然归入按钮) |
| 复选与开关 `TOGGLE` | `*CheckBox`, `*Switch*`, `*RadioButton`, `*ToggleButton`, `*CompoundButton` | - |
| 列表与滚动 `SCROLL` | `*RecyclerView`, `*ListView`, `*ScrollView`, `*ViewPager*`, `*GridView` | - |
| 容器 `CONTAINER` | `*Layout`, `*ViewGroup`, `*Container*` | - |

Q5 建议中的 "Web" 与 "其他" 未设为预设: Web 可用字段模式 `className~=WebView` 表达, "其他" 需对全部预设取反, 作为筛选项意义有限.
