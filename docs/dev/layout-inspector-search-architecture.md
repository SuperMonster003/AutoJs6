# AutoJs6 布局分析搜索与筛选架构 (Layout Inspector Search Architecture)

本文是布局分析 (Layout Inspector) 搜索与筛选子系统的架构说明, 与 [layout-inspector-search-roadmap.md](layout-inspector-search-roadmap.md) 配套:
Roadmap 负责 "做什么, 做到哪了" (含固定决策 D1-D20 与证据), 本文负责 "现在长什么样, 为什么这样, 改动时要守住什么".

维护约定:

1. 本文描述当前 `master` 的真实结构, 以符号名 (类 / 方法 / 常量) 为锚点, 不引用行号.
2. 与 Roadmap 决策表冲突时以 Roadmap 为准并回改本文.
3. 只用半角标点.

---

## 1. 一句话模型

用户在布局分析窗口顶部的搜索栏输入查询; 查询 (三种模式之一) 与筛选配置, 区域条件一起编译为脚本选择器引擎的 `Filter` 列表; `TreeMatch` 在已捕获的节点树上一次性求值, 得到 "匹配集合 + 保留集合 (匹配节点及其祖先)"; 层次窗口据此裁剪并高亮, 范围窗口据此只绘制匹配节点. 整个求值链不依赖 Android (只有 `InspectorNode` 包装 `NodeInfo`), 因此在 JVM 上可以用假节点树完整测试.

```
InspectorSearchBar (输入)                       LayoutHierarchyView / LayoutBoundsView (呈现)
        |  setText / setRegex / setMode / ...            ^  isKept / isMatched / highlightRange
        v                                                |  (InspectorMatchView)
InspectorSearchSession  ---- evaluate() ---->  TreeMatch<InspectorNode>
        |                                                ^
        |  InspectorQuery.compile()  +  FilterProfile.toFilters()  +  BoundsFilter(region)
        v                                                |
   List<Filter>  (关键词 KeywordFilter / 字段 SelectorStringParser / 选择器 SelectorCallParser)
```

## 2. 引擎分层 (`core/accessibility/inspector/`)

引擎包除 `InspectorNode` 外不引用 Android 类型 (Roadmap D13). 自下而上:

### 2.1 节点适配: `InspectorNode`

`InspectorNode(info: NodeInfo) : UiNode<InspectorNode>`. 布局分析捕获的树由 `NodeInfo` 构成 (与实时的 `UiObject` 无关), `InspectorNode` 把它按 `UiNode` 接口暴露, 使脚本选择器的所有 `Filter` 不加改动地对快照求值 (Roadmap D10). 约定:

- 包装对象按需创建 (`parent()` / `child(i)` 每次 `new`), 以所包装的 `NodeInfo` 判等, 因此 `Set<InspectorNode>` 可以用新建的包装对象探查.
- `id()` 返回完整资源 id (`package:id/entry`), 同 `UiNode.id` 的约定; 关键词模式对 id 做子串匹配, 简单 id 自然被覆盖.
- `NodeInfo` 未捕获 `password` / `contentInvalid` / `multiLine`, 读为 `false`; `performAction` 恒为 `false`, `recycle` 为空操作.

### 2.2 条件编译: `InspectorQuery` 与三种模式

`InspectorQuery(mode, text, regex)` 是不可变数据类, `compile()` 返回 `Compiled.Filters(filters)` 或 `Compiled.Error(InspectorQueryError)`; 空白文本编译为空列表 (即 "没有查询").

| 模式 (`Mode`) | 语法入口 | 产物 | 失败形态 |
| --- | --- | --- | --- |
| `KEYWORD` | `KeywordFilter.of(text, regex)` | 单个 `KeywordFilter`: OR 跨 text / desc / id / className (`KeywordProperty`), 大小写不敏感; `regex` 开启时按 JS 字面量 `/re/flags` 或普通模式编译 (`JsRegexUtils`) | `PatternSyntaxException` -> `Kind.INVALID_REGEX` (带出错偏移) |
| `FIELD` | `SelectorStringParser.parse(text, UiSelector(null))`, 即脚本 `select("...")` 的字符串语法 (Roadmap D11) | 该 `UiSelector` 的 `selector.filters` | `SelectorStringException` -> `Kind.SYNTAX` (位置, 出错条件, 候选属性名) |
| `SELECTOR` | `SelectorCallParser.parse(text, UiSelector(null))`, 自研链式调用解析器 (Roadmap D12) | 同上 | `SelectorCallException` -> `Kind.SYNTAX`; `incomplete = true` (表达式停在调用中途) -> `Kind.INCOMPLETE` |

`InspectorQueryError(kind, detail, position, term, candidates)` 是界面层渲染错误的全部输入; `INCOMPLETE` 不是错误, 只是用户仍在输入. 两个解析器都是 `companion` 内的惰性单例, 解析结果是全新的 `UiSelector` (无 bridge 构造, 只用来收集过滤器, 不会触发查找).

`SelectorCallParser` 的要点 (P5.1): 按需词法分析 (IDENT / DOT / LPAREN / RPAREN / COMMA / STRING / NUMBER / BOOLEAN / REGEX / END), 字符串支持单 / 双引号与转义 (`\n` `\t` `\uXXXX` 等), 方法名在读到 `(` 时即对照 `SelectorRegistry` 校验, 可选前缀 `selector().` / `$().` 被跳过; 正则字面量只能出现在脚本接受 `RegExp` 的位置 (`name` / `nameContains` / `nameMatch` / `nameMatches`), 落地为对应 `Match` / `Matches` 方法的 JS 字面量字符串重载 (注册表的 `REGEX` 参数类型需要 `NativeRegExp`, 布局分析不经 Rhino). 未知方法 / 重载不匹配经 `SelectorStringParser.suggest` / `signatures` 给出候选与签名.

### 2.3 筛选配置: `FilterProfile` -> `List<Filter>`

`FilterProfile` 是不可变数据类 (Roadmap D7 的全部维度), `toFilters(scale)` 按固定顺序映射到过滤器, 其中注册表能表达的直接复用脚本过滤器, 不能表达的以引擎包内的少量自定义 `Filter` 实现; 每个自定义过滤器的 `toString()` 必须是合法选择器片段或 `/* ... */` 注释 (供导出, 见 2.6):

| 配置维度 | 过滤器 | `toString()` |
| --- | --- | --- |
| 布尔三态 (14 项) | `BooleanFilter(property.supplier, true/false)` | `clickable(true)` 等 |
| 有 id / text / desc | `StringPropertyFilter(ID/TEXT/DESC, StringMatcher.match("."))` | `idMatch(".")` 等 |
| 类名预设 (多选 OR) | `ClassPresetFilter(presets)` (`ClassPreset.kt`, Roadmap D18 的后缀规则表) | 等价的 `classNameMatches("...")` |
| depth / childCount / indexInParent 区间 | `IntFilter(property, Min/Max)` (Roadmap D19 新增的注册表属性) | `minDepth(3)` / `maxDepth(8)` 等 |
| 最小宽 / 高 | `MetricsFilter(WIDTH/HEIGHT, Min, scale)` | `minWidth(2)` 等 |
| 仅屏幕内 | `ScreenIntersectFilter(scale)` (`FilterProfile.kt`) | `/* on screen */` 注释 |
| 包名 (多选 OR) | 单个: `StringPropertyFilter(PACKAGE_NAME, equals)`; 多个: `AnyOfFilter(...)` (`FilterProfile.kt`) | `packageName("...")` / `/* packageName in (...) */` |
| 动作 (全部包含 AND) | `ActionFilter(actions)` | `action("ACTION_CLICK", ...)` |

`ScreenScale.Source` 是屏幕尺寸的注入点: 真机为 `ScreenScale.Source.default`, JVM 测试安装 `ScreenScale.Source.fixed(w, h)`.

`FilterProfileCatalog` 持有用户配置列表与 `selectedId`, 实现 D6 的全部操作 (新建 / 另存为 / 重命名 / 删除 / 选择 / 名称校验); 默认配置 `FilterProfile.DEFAULT` (id `default`, 无条件, 名称来自字符串资源) 永远存在且不持久化.

### 2.4 求值: `TreeMatch` 与 `InspectorNodes.preOrder`

`TreeMatch.evaluate(root, filters)` 对 `InspectorNodes.preOrder(root)` (显式栈的先序遍历, 父在子前, 兄弟按索引) 做一次线性扫描: 节点通过全部过滤器即进入 `matches` (先序有序列表) 与 `matched` 集合, 并把自身及全部祖先加入 `kept` 集合. 结果不可变, 提供:

- `isMatched(node)` / `isKept(node)`: 层次窗口裁剪 (Roadmap D3) 与范围窗口绘制 (D4) 的唯一依据.
- `next(current)` / `previous(current)` / `indexOf(current)`: 匹配项间的循环导航.
- `TreeMatch.unfiltered()`: "没有查询" 的哨兵, 所有节点均保留且匹配, `count` 为 0, 导航不返回节点.

`TreeCandidates.collect(root)` 单独扫描一遍, 得到配置对话框的候选: 排序去重的包名 / 动作名, 类名计数, 节点总数 (Roadmap D16: 候选取自树本身, 离线布局同样可用).

### 2.5 查询历史: `QueryHistory`

有序去重列表 (最多 `MAX_SIZE = 20`, 最新在前), 键为 `(mode, text)`, 正则开关随最新一次记录; `record` / `remove` / `clear` 返回是否发生变化, 便于调用方决定是否落盘.

### 2.6 导出: `SelectorExport`

`render(query, queryFilters, hit, extraFilters)` 把生效中的条件渲染为脚本选择器 (Roadmap D8): 关键词模式按当前匹配实际命中的属性输出 `textContains("...")` / `textMatch(/.../)` 系列 (`hit` 为 null 时回退到 text), 字段 / 选择器模式直接打印过滤器, 配置与区域过滤器按顺序追加; 每个过滤器的 `toString()` 以 `/*` 开头者归入 `comments` 附在末尾, 其余按 `.` 连接. `Rendered.text` 即写入剪贴板的内容, 空链时为 `selector()`.

## 3. 会话状态与窗口生命周期 (`ui/floating/layoutinspector/search/`)

### 3.1 `InspectorSearchSession`

一次布局分析的全部搜索状态 (Roadmap D5), 构造参数为捕获的根 `NodeInfo`:

| 成员 | 作用 | 生命周期 |
| --- | --- | --- |
| `query: InspectorQuery` | 文本 / 模式 / 正则开关; 模式初值来自 `QueryModeStore` | 会话内; 模式变化时经 `QueryModeStore.save` 跨会话 |
| `error`, `match`, `filters` | 最近一次求值的产物 | 会话内 |
| `profile` / `catalog` / `isProfileDirty` | 正在编辑的配置 (可能未保存) 与持久化的目录; `profile != catalog.selected` 即 "已修改" | 目录经 `FilterProfileStore` 跨会话 |
| `region: Rect?` | 范围窗口拖出的矩形 (屏幕坐标), 编译为 `BoundsFilter(..., TYPE_INSIDE)` | 会话内 (与屏幕绑定) |
| `currentMatch`, `currentIndex` | 导航游标; 求值后若游标不再匹配则清空 | 会话内 |
| `isBarExpandedInHierarchy` / `isBarExpandedInBounds` | 各类窗口搜索栏的展开状态 (Roadmap D9) | 会话内 |
| `history` | `QueryHistoryStore` 惰性加载; `commitQuery` (回车 / 选择结果) 时记录并落盘 | 跨会话 |
| `candidates` | `TreeCandidates` 的缓存, `setRoot` 时失效 | 会话内 |

求值规则 (`evaluate()`): 查询过滤器在前 (关键词成为 `SelectorExplainer` 的第一步), 然后配置, 最后区域; 查询编译失败时即使设置了配置也不筛选 (`match = unfiltered`), 让用户看到错误而不是悄然变窄的结果. 文本变化经 `setText` 走 `DEBOUNCE_MILLIS = 150` 的主线程去抖 (Roadmap D17), 正则开关 / 模式 / 配置 / 区域变化立即求值.

线程模型 (Roadmap D17 的 P6.1 修订): 编译在主线程完成 (错误即时可见, `error` / `filters` 立即更新), `TreeMatch.evaluate` 在单个共享的工作线程 (`EXECUTOR`, 空闲 10 s 退出) 执行, 结果经 `apply()` 回到主线程; 每次请求递增 `sequence`, 工作线程开始前与结果回传时都核对序号, 过期的遍历直接跳过或丢弃, 在新结果应用前上一次 `match` 继续生效. 需要立即拿到结果的调用 (导航 `next` / `previous`, `exportSelector`, `commitQuery`) 经 `flush()` 在调用线程同步求值并使在途遍历作废. 每次应用结果与游标移动后通知全部 `Listener` (`CopyOnWriteArrayList`, 搜索栏与窗口各注册一个). 求值耗时以 `Log.d("InspectorSearchSession", "evaluate: N matches, X ms, ..., walk W ms on the worker")` 记录, 真机基准据此读取 (见第 6 节).

会话同时实现 `InspectorMatchView` (`isUnfiltered` / `isKept` / `isMatched` / `highlightRange`), 这是两个视图类与搜索状态之间唯一的接口: `LayoutHierarchyView` (Java, Roadmap D14) 与 `LayoutBoundsView` 只依赖该接口, 不认识会话.

### 3.2 窗口之间的传递

`LayoutFloatyWindow` (层次与范围窗口的共同基类) 持有 `searchSession`:

1. `onCreate` 时若为 null 则以 `capture.root` 新建 (首个窗口); `CircularMenu` / `LayoutSnapshotViewer` 不感知会话.
2. `showLayoutBounds` / `showLayoutHierarchy` 互切时把 `searchSession` 连同 `isOffline` / 选中节点一起赋给新窗口, 因此查询, 配置修改, 区域与游标在切换中保持.
3. "切换窗口" 对话框换根时调用 `searchSession.setRoot(newRoot)`: 候选缓存失效并立即重新求值.
4. 每个窗口在 `onCreateView` 中经 `createSearchBar(defaultExpanded, expanded, host)` 创建自己的 `InspectorSearchBar` 并 `bind` 到会话; `close()` 时注销监听. 最后一个窗口关闭后会话随之被回收, 没有静态持有.

`defaultExpanded` 决定两件事: 该类窗口的初始展开状态 (层次窗口展开, 范围窗口收起为浮标, Roadmap D9) 与返回键是否收起空的搜索栏 (`collapsesOnBack`).

### 3.3 搜索栏与对话框

- `InspectorSearchBar`: 输入框, 正则开关 (仅关键词模式可见), 模式开关 (Aa -> k=v -> $() 循环, 首次进入字段 / 选择器模式各弹一次语法提示, 标记存于 `QueryModeStore`), 筛选按钮 (带条件数角标与 "已修改" 标记), 匹配计数 (错误时 "!", 未结束表达式时 "..."), 上一个 / 下一个, 溢出菜单 (以列表查看 / 复制为选择器 / 清除历史). 输入框获得焦点时显示窗口内的提示条 (`renderHint`: 字段 / 选择器模式的语法速查, 或当前错误); 空输入时弹出历史列表 (点击填入, 长按删除). 宽度限制为 `MAX_BAR_WIDTH_DP` 并居中 (Roadmap D20 / Q7). 窗口通过 `Host` 接口接收 "当前匹配变化 / 显示节点信息 / 请求框选区域 / 展开状态变化" 回调.
- `SearchResultsDialog`: 扁平列表 + 节点路径, 点击跳转并选中.
- `FilterProfileDialog`: D6 / D7 的全部编辑操作, 候选来自 `session.candidates`; 关闭或切换配置时若有未保存修改则询问 "保存 / 另存为 / 放弃".
- `SelectorClipboard.copy(context, session, describeError)`: 写入剪贴板并以 `Log.d("SelectorClipboard", "copy as selector: ...")` 记录 (冒烟脚本据此读取).

### 3.4 错误渲染

`InspectorSearchBar.describe(error)` 是 `InspectorQueryError` 到用户文本的唯一映射:

| `Kind` | 文本 | 计数位 |
| --- | --- | --- |
| `INVALID_REGEX` | `text_inspector_invalid_regex` + 解析器 detail | `!` |
| `SYNTAX` | 有位置时 `text_inspector_syntax_error_at(position, detail)`, 否则 detail; 有候选时追加一行 `text_inspector_did_you_mean` | `!` |
| `INCOMPLETE` | `text_inspector_expression_unfinished` (提示色而非错误色) | `...` |

字符串资源中的字面引号必须写成 `\"` (aapt 会丢弃裸 `"`), 语法速查 `text_inspector_field_syntax_hint` / `text_inspector_selector_syntax_hint` 即是例子.

## 4. 持久化格式与兼容规则

全部存于应用偏好 (`Pref`), 键名在 `strings_donottranslate.xml`. 三个 `*Store` 对象是界面层与引擎编解码之间的薄壳, 读取一律容忍缺失或损坏的值 (返回空目录 / 空历史 / 关键词模式), 不抛异常.

| 偏好键 | 内容 | 编解码 |
| --- | --- | --- |
| `key_layout_inspector_filter_profiles` | 用户配置列表的 JSON 数组 (`FilterProfileCatalog.toJson`); 没有用户配置时写 null | Gson, `ProfileJson` |
| `key_layout_inspector_filter_profile_selected` | 已选配置 id; 选中默认配置时写 null | 纯字符串, 启动时 id 不存在则回退默认 |
| `key_layout_inspector_query_history` | 历史的 JSON 数组 (`QueryHistory.toJson`), 最新在前; 为空时写 null | Gson, `Entry(mode, text, regex)` |
| `key_layout_inspector_query_mode` | 模式枚举名; 关键词模式写 null | 未知名称回退关键词 |
| `key_layout_inspector_field_mode_introduced` / `key_layout_inspector_selector_mode_introduced` | 一次性语法提示已展示 | Boolean |

配置条目的 JSON 形态 (字段全部可空, 缺省即 "不限"):

```json
[
  {
    "id": "p1abc2xyz", "name": "可点按钮",
    "booleans": { "CLICKABLE": "YES", "ENABLED": "YES" },
    "requireId": false, "requireText": true, "requireDesc": false,
    "classPresets": ["BUTTON"],
    "depth": { "min": 3, "max": 8 }, "childCount": null, "indexInParent": null,
    "minWidth": 2, "minHeight": 2, "onScreenOnly": true,
    "packages": ["com.android.settings"], "actions": ["ACTION_CLICK"]
  }
]
```

兼容规则 (`ProfileJson.toProfile` / `FilterProfileCatalog.fromJson`):

1. 整体不可解析 -> 空目录; 单个条目不可读 (`null`, 无 id, id 为 `default`) -> 跳过; 重复 id 只保留第一个.
2. 枚举按名称解码 (`BooleanProperty` / `Tri` / `ClassPreset`), 未知名称静默丢弃, 因此将来删除某个预设或布尔维度不会破坏旧数据; 新增字段时保持可空并给出 "不限" 的默认值即可向后兼容.
3. `Tri.ANY` 不写入 (`booleans` 只含 YES / NO); 区间 `isAny` 时写 null; `minWidth` / `minHeight` 非正数视为未设置; 空白包名 / 动作名丢弃.
4. 新 id 由 `timeId()` 生成 (`p` + 36 进制时间戳 + 纳秒低位), 测试传入确定性的 `newId`.

历史条目的 `mode` 无法识别或 `text` 缺失时整条跳过, `regex` 缺失视为 false; 解码时逆序 `record` 以复现 "最新在前" 与去重.

## 5. 与脚本选择器引擎的依赖面

引擎包只读复用以下类, 不修改其行为; 改动这些类时要跑布局分析的单测:

| 用途 | 类 |
| --- | --- |
| 节点接口 | `core/automator/UiNode` |
| 过滤器 | `core/automator/filter/{Filter, Selector, StringMatcher, StringProperty, StringPropertyFilter, BooleanFilter, IntFilter, MetricsFilter, BoundsFilter, ActionFilter, ScreenScale, JsRegexUtils}` |
| 注册表与解析 | `core/automator/selector/{SelectorRegistry, SelectorEntry, SelectorStringParser, SelectorStringException}` |
| 零匹配解释 | `core/automator/diagnostics/SelectorExplainer` |
| 无 bridge 的选择器容器 | `core/accessibility/UiSelector(null as AccessibilityBridge?)` |

本功能对脚本公开 API 的唯一改动是 Roadmap D19 为注册表新增的 `minDepth` / `maxDepth` / `minChildCount` / `maxChildCount` / `minIndexInParent` / `maxIndexInParent` (P4), 已同步文档 / d.ts / Ace 声明三个仓库. 其余一切 (关键词过滤器, 类名预设, 区间 / 任一 / 屏幕相交过滤器, 链式调用解析器) 都在引擎包内, 对脚本不可见.

## 6. 性能 (Roadmap D17, P6.1)

预算 (原约定): 输入去抖 150 ms 后在主线程求值, 10,000 节点的树单次求值 <= 50 ms (API 33 中端机); 超出则迁至后台线程并以序号丢弃过期结果. P6.1 的真机测量突破了预算, 现状即第 3.1 节的线程模型.

JVM 基准 `TreeMatchBenchmarkTest` (10,000 节点合成树: 4 层外壳 + 2,000 行, 每行容器 > 标题 + 副标题 + 按钮 + 图片; 3 次预热后取 7 次的中位数, 只打印不断言, 2026-10-02 开发机):

| 场景 | 中位数 |
| --- | --- |
| `InspectorNodes.preOrder` | 0.5 ms |
| 关键词 `item 7` | 3.6 ms |
| 关键词正则 `/item \d*5$/i` | 5.9 ms |
| 字段 `clickable minDepth=6 text~=Open` | 3.1 ms |
| 选择器 `textContains("Item").clickable(false)` | 2.6 ms |
| 完整配置 (11 个过滤器) | 2.2 ms |
| 关键词 + 完整配置 | 3.2 ms |
| `TreeCandidates.collect` | 3.8 ms |

真机对照 (Redmi 22120RN86C, API 33, `InspectorSearchSession` 的 evaluate 日志行, 迁移前主线程求值):

| 页面 | 节点数 | 关键词 | 关键词正则 | 字段 | 选择器 |
| --- | --- | --- | --- | --- | --- |
| 系统设置首页 | 126 | 6-11 ms | 5-18 ms | 4-7 ms | 2 ms |
| 原生浏览器, Wikipedia 条目 | 681 | 12-44 ms | 15-17 ms | 3-17 ms | 5-7 ms |
| 原生浏览器, 2,500 行 HTML 页面 | 5,883 | 35-57 ms | 78-103 ms | 11-55 ms | 13-25 ms |

结论: 真机耗时比 JVM 高一个数量级 (ART 上的 `Filter` 虚调用与正则), 5,883 节点页面的正则关键词已突破 50 ms, 因此按 D17 的原约定把树遍历迁到工作线程 (第 3.1 节); 迁移后同一页面的遍历 15-107 ms 全部发生在工作线程, 主线程只承担编译与应用. 剩余的主线程成本是视图重建 (层次窗口 `reloadData` 与范围窗口重绘), 与节点数线性相关, 不在本功能范围内优化. 真机脚本 `build/inspector-smoke/p6_bench.sh settings|browser|http` (gitignored).

## 7. 可测性

- 引擎包的单测 (`app/src/test/java/org/autojs/autojs/core/accessibility/inspector/`) 以既有 `FakeUiNode` 构树 (`FakeUiNode.root { child("a") { ... } }`, 深度与索引由构建器推导), 覆盖: `KeywordFilterTest`, `InspectorQueryTest`, `TreeMatchTest`, `TreeCandidatesTest`, `QueryHistoryTest`, `FilterProfileTest`, `FilterProfileCatalogTest`, `ClassPresetTest`, `SelectorExportTest`, `SelectorCallParserTest`, `TreeMatchBenchmarkTest`; 注册表变化由 `core/automator/selector/` 下的 `SelectorRegistryTest` (条目 / 重载计数) 与 `SelectorStringParserTest` 守护.
- JVM 约束: 纯 JUnit4; org.json 为 stub, 编解码用 Gson; `NodeInfo` 构造器私有且含 `Rect` / `Point`, 不在 JVM 上构造 (所以 `InspectorSearchSession` 与视图类只能真机验证); 屏幕相关过滤器安装 `ScreenScale.Source.fixed`; `bounds(...)` 这类会触发 `GlobalAppContext` 的 `toString()` 在测试里改断言 `rendered()`.
- 真机冒烟按 Roadmap 附录 B 的清单, 脚本在 `build/inspector-smoke/` (gitignored): `drive.py` (坐标 / 截图 / 运行脚本), `p2_*.sh` / `p3_*.sh` / `p4_*.sh` / `p5_*.sh` / `p6_bench.sh` 各覆盖一个阶段, 以 uiautomator dump 定位控件, 以 `InspectorSearchSession` / `SelectorClipboard` 的日志行读取结果, 以 `export_check.js` 把导出的选择器作为脚本重跑并比对计数.

## 8. 改动时要守住的约定

1. 新条件一律编译为 `Filter`, 并保证 `toString()` 是合法选择器片段或 `/* ... */` 注释; 不要在视图层另写匹配逻辑.
2. 引擎包不引入 Android 类型 (`InspectorNode` 除外); 需要 Android 的东西放在 `ui/floating/layoutinspector/search/`.
3. 配置 JSON 新增字段必须可空且有 "不限" 默认值; 删除字段 / 枚举值时依赖 "未知即丢弃" 的解码规则, 不写迁移代码.
4. 查询编译失败时保持树不筛选 (错误可见优先于结果变窄).
5. 搜索栏与窗口只通过 `InspectorSearchSession.Listener` / `InspectorSearchBar.Host` / `InspectorMatchView` 三个接口交互; `LayoutHierarchyView` 保持 Java (Roadmap D14), 行状态在 `getViewForObject` 中由数据推导.
6. 脚本公开 API 的任何变更 (注册表新增属性) 必须同步文档 / d.ts / Ace 三个仓库 (Roadmap D19 的先例).

## 9. 浮窗主题与节点信息多选 (2026-10-02)

`InspectorTheme` 按控件实际所在的对话框 / 搜索栏 / 层次窗口背景计算主题色, 复用宿主 `ColorUtils.adjustColorForContrast`. 普通文字与主题色文字的目标对比度为 4.5, 控件为 3.0; 半透明窗口分别检查纯黑与纯白底图合成后的背景. 多选行与命中子串使用不透明的主题色混合背景, 文字按该背景单独计算, 避免高亮背景与主题色文字相互抵消. 层次搜索的祖先行使用符合对比度要求的次要文字色, 不再通过降低整段文字透明度来淡化.

`InspectorDialogBuilder` 为节点信息, 搜索结果, 筛选配置及其子对话框, 窗口切换和代码生成提供相同的宿主语言 / 明暗配置及主题色策略. 构建器 context 本身也包装 `AppTheme`, 因此 MaterialDialog 的列表行与单选按钮能够解析主题属性. 范围框选的主题色虚线额外绘制对比色轮廓, 以适应下方任意应用内容.

`NodeInfoDialog` 管理节点信息的按钮与复制方式, `NodeInfoView` 只管理属性和选中行. 长按进入多选, 单击切换行背景; 不插入复选框, 不改变文字列宽或行高. 取消最后一项后保留多选模式并禁用复制, "返回" 清空选择并退出多选. 多选按钮为 "设置 / 返回 / 复制". 设置中的复制方式按 `ASK` / `OBJECT` / `CHAINED` 存入 `key_layout_inspector_node_copy_mode`, 缺省或未知值按 `ASK` 处理; 只在确认设置后写入, 临时的格式选择不改变偏好.

设备测试 `InspectorUiDeviceTest` 覆盖明暗模式和多种主题色的对比度, 小宽度与字体放大时多选前后的行坐标, 以及复制偏好的默认值 / 持久化 / 未知值回退. `tools/layout-inspector/smoke-region.py` 使用实际节点边界补测实时与离线区域筛选, 跨窗口传递及清除, 避免不同设备的固定点击坐标造成误判.
