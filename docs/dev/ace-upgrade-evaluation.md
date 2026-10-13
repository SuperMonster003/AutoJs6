# Ace 升级评估 (1.4.12 -> 1.44.0)

## 2026-10-13 复评

**具备开展受控迁移验证的工程条件, 尚不具备直接替换后发布的验证条件.** 建议把核心升级作为插件的独立迁移任务, 先整理现有移动端补丁, 再验证 1.44.0. 本次评估没有替换 Ace 核心, 也没有宣称新版已经通过 Android 兼容性验收.

下方 2026-10-07 的 "暂缓升级" 是当时针对智能提示路线图 P5 的历史决定. 本节针对本次新提出的升级评估, 其中涉及当前状态的结论优先于历史段落.

### A. 版本与来源核实

本次复评起始基线为 `AutoJs6-Plugin-Ace-Editor` 1.31.0, Ace 核心仍为 1.4.12. JS/TS 语言服务已经使用 TypeScript 6.0.3, Python 使用 Pyright, Lua 使用 LuaLS, Java 提供 ECJ 单文件诊断, Kotlin 提供 P2+ 补全. 这些能力由项目自己的桥接和 provider 实现, 不能把 Ace 核心版本等同于语言服务版本, 也不能把核心升级描述为自动获得完整 Java/Kotlin 语义服务.

复评时 [Ace 官方发行页](https://github.com/ajaxorg/ace/releases/tag/v1.44.0) 仍将 1.44.0 标为 Latest. GitHub Release API 返回 `published_at = 2026-08-07T11:58:01Z`, `created_at = 2026-05-11T08:05:14Z`. 因此正式发行页的发布日期为 2026-08-07; 旧评估使用的 2026-05-11 是该 release 对象的 `created_at`, 不应继续作为正式发布日期.

下列代码路径均相对于插件仓库. Ace 资产根目录为 `app/src/main/assets/editor/ace-builds-1.4.12/`.

| 对象 | 已核实状态 | 升级含义 |
| --- | --- | --- |
| `src-min-noconflict/ace.js` | 运行时报告 1.4.12, 含本地移动端改动 | 不是可以无损覆盖的上游原包 |
| `src-min-noconflict/ext-language_tools.js` | 与官方 1.4.12 发行文件存在内容差异 | 需审计补全前缀、弹窗、文档提示和 snippet 行为 |
| `src-min-noconflict/mode-kotlin.js` | 可读上游源码加声明关键字、关键字列表、snippet 关联补丁 | 逐项确认新版是否覆盖, 不机械保留或删除 |
| `src-min-noconflict/snippets/python.js`, `snippets/kotlin.js` | 项目维护的 Python 3/Kotlin 片段 | 保留产品行为; 1.44.0 上游 Kotlin snippet 仍为空 |
| 主题 | 现有主题有本地内容差异, 另包含后续补充主题 | 审计 CSS、主题名称、颜色偏好和加载完整性 |
| `AceEditorAssets.kt`, `app/build.gradle.kts`, `tools/ace-lsp/` | 多处写入 `ace-builds-1.4.12` 路径 | 一起处理资源入口、生成路径、清单和验证脚本 |

本次以官方 npm `ace-builds@1.4.12` tarball 为对照, 对当前 `src-min-noconflict/` 文件做 UTF-8 解码并统一 CRLF/LF 后比较: 16 个文件一致, 43 个文件存在差异, 12 个主题文件不在该旧版 tarball 中. 43 个差异文件包括核心、language tools、Kotlin mode、38 个主题和 2 个自维护 snippet. 此统计是资产差异清单, 不是 43 项已证明的行为补丁; 压缩方式和构建包装的变化仍需与实际逻辑变化区分.

项目已有 `tools/ace-lsp/MILESTONE_BASELINES.md` 记录部分 mode/worker 的来源与哈希, 以及 Kotlin 补丁说明. 迁移前还应为整个 Ace 分发集合建立统一的版本、来源、完整性摘要和可重复应用的补丁清单, 避免继续仅依赖压缩产物承载改动.

### B. 关键风险补充

对比官方 1.4.12 后, 当前 `ace.js` 内可确认下列本地逻辑, 不能只审查桥接脚本:

- 增加 `ace/mouse/regression.js`, `velocity-tracker.js`, `scroller.js`, `magnifier.js` 模块, 并改写触摸处理.
- 接入 `autojs.showSoftInput`, `autojs.performLongPressFeedback`, `autojs.startActionMode`, `autojs.finishActionMode`, 服务于软键盘、长按反馈和 Android 原生选区菜单.
- 调整修饰键状态、移动端默认鼠标处理、原生 context menu 抑制与 gutter tooltip 在触摸滚动时的关闭行为.

`core/AceCodeEditor.kt` 的 `JS_BRIDGE_NAME` 仍为 `autojs`, `core/AceBridge.kt` 实现上述接口. 因而这些并非仅存在于旧代码中的失效字符串. 直接替换官方 `ace.js` 会移除这些集成, 必须先确认新版等价行为或迁移实现.

桥接层也存在历史清单未完整描述的依赖:

| 桥接位置或符号 | 依赖 | 需要验证的行为 |
| --- | --- | --- |
| `installImeTextInputSelectionWindow`, 输入选区同步 | `editor.textInput.getElement/resetSelection` 与输入事件时序 | 中文组合输入、退格、连续输入、光标跳转、撤销 |
| `installCompletionPopupTouchScrollPatch` | `ace/autocomplete` 的 `$init`, `editor.completer.popup` | 手指滚动候选、点击接受、关闭和重新打开 |
| `installDocumentLongLineRenderGuard` | `renderer.$textLayer.$renderSimpleLine` | 极长行只裁剪渲染, 文档内容和保存完整性不变 |
| 动态 gutter 宽度补丁 | `$gutterLayer.$updateGutterWidth`, `$useWrapMode`, `$fixedWidth` | 自动换行、行号宽度、折叠命中区域 |
| 折叠与触摸光标处理 | `$toggleFoldWidget`, `$mouseHandler`, `$padding` | 嵌套折叠、占位符展开、选区和滚动位置 |
| 文本替换、编码和换行符历史桥接 | `session.undoChanges/redoChanges`, `markUndoGroup`, `document.$autoNewLine` | 内容与编码/换行符状态同步撤销, 历史分组不丢失 |
| `autojs6.css` 与 tooltip/diagnostic 定位 | Ace DOM 类名与元素布局 | 主题、字体、缩放、软键盘弹出、提示框避让 |

旧评估把 `$textLayer` 的用途主要归为设置 `aria-hidden`, 已不足以描述当前实现: 现在还会包装实际文本行渲染函数. 旧统计中的访问次数也不能作为现行兼容清单.

1.44.0 分发文件仍包含 `$renderSimpleLine`, `$updateGutterWidth`, `$toggleFoldWidget`, `resetSelection`, `markUndoGroup` 等名字, 这有利于迁移, 但方法存在不等于参数、事件顺序和状态语义兼容. 上游 [TextInput 源码](https://github.com/ajaxorg/ace/blob/v1.44.0/src/keyboard/textinput.js) 和 [1.43.0 的 TextInput 改写](https://github.com/ajaxorg/ace/pull/5805) 说明输入层已经历持续调整, 必须在实际 WebView 与输入法上验证.

不能只看到上游源码使用 ES6 class 就断言旧 WebView 一定无法加载. 实际交付目标是 `ace-builds` 的浏览器分发产物, 其构建形式与源码不同. 最低 WebView 能力应以候选分发包实测, 尤其要覆盖项目已经保留降级路径的 Android 10 / WebView 74 环境.

### C. 升级收益与可独立完成的改善

[1.44.0 官方发布说明](https://github.com/ajaxorg/ace/releases/tag/v1.44.0) 包括分数坐标的行号计算修复、历史序列化修复、JavaScript 数字分隔符高亮、选区空白符扩展及查找框 Tab/Shift-Tab 行为修复. 对 AutoJs6 而言, 坐标、历史和高亮修复比新增不常用语言 mode 更值得优先验证. 这些是上游变化, 本次尚未证明 AutoJs6 的现有具体故障均会因此消失.

新版还提供可供产品接入的 [内联补全扩展](https://github.com/ajaxorg/ace/blob/v1.44.0/src/ext/inline_autocomplete.js) 和更完整的 [language tools 配置](https://github.com/ajaxorg/ace/blob/v1.44.0/src/ext/language_tools.js). 如果后续采用内联预览, 需要同步设计触屏接受/取消交互; 自动补全触发、等待时间和阈值已有项目实现, 升级后要避免重复调度.

核心资产体积不是当前首要障碍: 本地 `ace.js` 为 382,307 B, 官方 1.44.0 对应文件为 475,029 B; 本地 `ext-language_tools.js` 为 38,614 B, 官方对应文件为 57,777 B. 两者未压缩增量合计 111,885 B, 约 109.3 KiB. 这不是 APK 的最终增量, 也不包含迁移后补丁、额外扩展、mode 和主题的变化.

**TSX 高亮不必等待核心升级.** 当前 `.tsx` 路由到 `ace/mode/typescript`, 且没有打包 `mode-tsx.js`. 插件 `Roadmap.md` 中 "1.4.12 无独立 tsx mode" 的历史描述不准确: 官方 [Ace 1.4.12 TSX mode](https://github.com/ajaxorg/ace/blob/v1.4.12/lib/ace/mode/tsx.js) 已存在, 通过 `jsx: true` 启用 JSX 规则; [同版 ace-builds 也提供该分发文件](https://github.com/ajaxorg/ace-builds/blob/v1.4.12/src-noconflict/mode-tsx.js).

本次在内存中将官方 1.4.12 的 `mode-tsx.js` 加载到项目当前 `ace.js`, 对 `const view = <Button title={answer} />;` 执行 tokenizer 探针, 成功得到 `meta.tag.tag-name.xml` 和 `entity.other.attribute-name.xml` 等专属 token. 因此可以独立引入同版 mode, 更新 `.tsx` 路由、资产清单和回归测试. 这项探针只证明模块加载与该样例分词, 不代替 `.ts` 泛型、复杂 TSX、折叠与 Android 渲染验收.

"全部展开/全部折叠" 同样已有 Ace session API 支撑, 无需先升级核心. 后续当前代码块折叠、按层级折叠、恢复折叠状态等能力应分别按使用需求评估, 不与核心迁移绑定.

按当前实现而非旧路线图复核后, 建议的能力改进优先级如下. 除本次全部展开/全部折叠外, 下列项目均为建议, 尚未在本任务实现.

| 优先级 | 能力 | 当前缺口与实现方向 |
| --- | --- | --- |
| 高 | TSX 专用高亮 | 如上所述, 打包已有同版 TSX mode 并修正路由, 不需要更换核心 |
| 高 | 诊断问题列表及上一/下一问题 | 桥接已有 `showDiagnosticMarkerAtPosition` 与诊断数据, 宿主菜单尚无集中问题列表和导航入口 |
| 高 | 按语言和选区格式化 | `autojs6_ace_bridge.js` 的 `beautify` 统一调用旧 `ace/ext/beautify`, 应按语言选择格式化器并保持单次撤销、选区与缩进语义 |
| 中高 | 文档符号大纲与查找引用 | `autojs6_semantic_provider.js` 的能力表没有 `documentSymbol` / `references`, 需增加 provider、传输与导航界面, 不能仅加入菜单 |
| 中 | 折叠状态恢复 | `AceEditorPluginSession.createStateSnapshot` 没有折叠范围, 可结合文件身份/内容版本保存与恢复, 避免内容变化后错误折叠 |
| 中 | Python/Lua 重命名与 quick fix | 对应 provider 已有补全、悬停、参数提示、诊断和定义, 尚未暴露 rename/codeActions, 可沿现有语言服务扩展并复用宿主事务校验 |
| 后续专项 | Java/Kotlin 语义能力 | Java 当前侧重 ECJ 单文件诊断, Kotlin 为 P2+ 补全; 更完整的补全、定义导航和重构应作为语言服务专项, 核心升级不会自动补齐 |

输入法、选区、补全触控与长行处理已有较多实现和历史修复, 后续应优先用具体设备复现记录驱动精进, 避免把旧 roadmap 中尚未勾选的验收项直接当成当前仍存在的缺陷.

### D. 建议迁移方案与验收门

1. **先保存可复现基线.** 固定当前插件与候选 `ace-builds@1.44.0` 的版本、许可证、上游完整性摘要, 建立核心、language tools、主题、snippet 的差异清单. 将需要保留的移动端行为整理为可读适配层或可重复应用的源码补丁. 特别记录触摸滚动、放大镜和原生选区菜单的现状.
2. **在独立迁移分支整体更新分发集合.** 核心、已使用的 mode、worker、扩展和主题采用同一候选版本, 避免只替换 `ace.js`. 同步资源入口、`AceEditorAssets.requiredAssetPaths`、Gradle 生成路径、测试夹具和第三方声明. TypeScript/Pyright/LuaLS/ECJ 运行时版本保持为各自已锁定的版本, 让验证能归因到 Ace 迁移.
3. **先跑自动检查再接设备.** 插件运行 `:app:verifyAutoJs6LspRuntime`, `:app:verifyAutoJs6PythonWorker`, `:app:verifyAutoJs6LuaLanguageServer`, `:app:verifyAutoJs6JavaSemanticRuntime`, `:app:verifyAutoJs6KotlinP2Plus` 和 JVM 测试, 再构建 debug/test APK. 宿主补充运行 `tools/ace-signature`, `tools/ace-tooltip`, `tools/ace-lsp` 下相关测试. 这些 Node/JVM 检查覆盖语言服务与桥接逻辑, 不能代替真实 Ace DOM 和输入法验证.
4. **覆盖真实编辑路径.** 使用 `docs/dev/ace-editor-test-plan.md`、`ace-keyboard-viewport-test-plan.md` 与插件 `AceCodeEditorSmokeTest`, `AceLanguageRoutingSmokeTest`, `AcePerformanceBaselineTest` 作为现有基线. 必测中文组合输入和删除、Gboard/现用输入法、选区手柄与原生菜单、触摸滚动与长按、候选接受/滚动、键盘视口、折叠/展开、搜索替换、撤销重做、编码/换行符历史、极长行、主题字体缩放、worker 离线加载与语言服务降级.
5. **发布条件明确.** 对当前维护设备、最低支持环境及一个现代 WebView 记录系统/WebView/输入法版本, 用同一文本样本比较首开、滚动、补全与长行耗时和内存. 不以 Android API level 代替 WebView 版本. 编辑保存正确性和主要交互通过后才合并发布; 发现输入丢失、保存变化、选区失效或不可接受的性能回退时, 保留旧版为发布基线, 在迁移分支继续修复.

现有资产清单、语言服务离线验证、JVM 测试、WebView instrumentation 和性能基线为迁移提供了基础. 插件 `.github/workflows/build.yml` 已有 JVM/lint/APK 构建和 API 35 x86_64 instrumentation, 但这一个模拟器配置不足以代表所有移动端输入法和旧 WebView.

本次复评完成了当前源码阅读、官方指定版本源文件核实、发行资产对比及同版 TSX tokenizer 探针. 未替换核心, 未安装升级候选 APK, 未运行 1.44.0 的完整设备矩阵. 因此只能判定 "可启动迁移验证", 不能判定 "可直接发布".

## 2026-10-07 历史评估

以下保留当时针对 P5 的分析与决定. 版本、日期、私有成员统计和现行建议以本文 2026-10-13 复评为准.

对应 `ace-smart-hints-roadmap.md` 的 P5. 结论先行: **暂缓升级**. 智能提示路线图所需能力均已在 1.4.12 上由桥接层自实现 (P1 / P2), 升级的直接收益 (内联幽灵文本, triggerCharacters, selectOnHover) 目前没有对应的产品需求, 而文本输入层与补全弹窗的重写会波及已经稳定的移动端交互 (IME 选区窗口, 选区菜单抑制, 弹窗触控滚动). 当出现需要新版能力的需求, 或 1.4.12 暴露无法绕过的缺陷时再按第 4 节的试水计划启动.

历史快照: 2026-10-07, 插件 `AutoJs6-Plugin-Ace-Editor` 1.26.0, Ace 1.4.12, 升级评估目标 1.44.0. 1.44.0 的正式发布日期见本次复评更正.

### 1. 桥接层对 Ace 1.4.12 的依赖清单

以 `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/` 下的脚本为对象 (grep 统计, 2026-10-07).

#### 1.1 私有成员 (`$` 前缀, 升级时无兼容承诺)

| 访问 | 次数 | 用途 | 升级替代 |
| --- | --- | --- | --- |
| `session.$toggleFoldWidget` | 4 | 点按折叠占位符展开 / 折叠 (UX-1) | 1.1x+ 仍存在, 但需复核签名; 可改用 `session.toggleFold` / `onFoldWidgetClick` 公开路径 |
| `editor.$mouseHandler` (`.isMousePressed`) | 3 | 触摸期间抑制光标通知 | 改用 `editor.on("mousedown")` / `mouseup` 自行维护状态 |
| `renderer.$textLayer` | 2 | 长行安全模式下对文本层设置 `aria-hidden` | 改用 `container.querySelector(".ace_text-layer")` |
| `renderer.$gutterLayer` | 1 | 折叠部件命中测试 | 同上, 用 DOM 查询 |
| `renderer.$padding` | 1 | 坐标换算 | `renderer.getPadding()` 公开方法 |
| `renderer.$isMousePressed` | 1 | 同 `$mouseHandler` | 同上 |
| `session.$autojs6LongLineSafetyMode`, `editor.$autojs6*` | 4 | 桥接层自有标记 | 无风险 |

#### 1.2 半公开对象与 DOM 结构

- `editor.completer`: `detach` (10), `popup` (2, 含 `popup.isOpen` / `popup.container`), `insertMatch` (2), `goTo` (2), `activated` (1). 1.2x 起 `Autocomplete` 的 `popup` 改为惰性创建, `activated` 语义保持; `goTo` / `insertMatch` 仍为公开方法.
- `editor.textInput.getElement` / `resetSelection` (IME 选区窗口 `installImeTextInputSelectionWindow`). 1.5.x 重写了 `textinput.js` (组合输入, Android 退格, 选区同步), 这是升级风险最高的区域.
- DOM 类名: `.ace_scroller`, `.ace_text-layer`, `.ace_gutter`, `.ace_fold-widget`, `.ace_autocomplete`, `.ace_tooltip`, `.error_widget`, `.ace_mobile-menu` (宿主以 `ActionMode` 替代, 需保持抑制逻辑有效).
- 模块: `ace/autocomplete`, `ace/autocomplete/util` (`getCompletionPrefix`), `ace/ext/beautify`, `ace/ext/error_marker`, `ace/ext/language_tools`, `ace/range`, `ace/snippets`, `ace/mode/javascript/jshint`.
- 自实现并绕开 Ace 默认行为的部分: 实时补全调度 (`scheduleLiveCompletion`, P2), "." 后重启补全, 补全弹窗触控滚动补丁 (`installCompletionPopupTouchScrollPatch`), 参数提示 / 悬停框 (完全自实现, 不依赖 Ace 内部), 键盘命令覆盖 (Ctrl-Space / Alt-Up / Alt-Down).

### 2. 1.44.x 相对 1.4.12 的收益

| 能力 | 引入版本 | 与路线图的关系 |
| --- | --- | --- |
| `completer.triggerCharacters` | 1.21.0 | 可替代桥接层的 "." 触发, 但现有实现已满足需求 |
| `liveAutocompletionDelay` / `liveAutocompletionThreshold` | 1.2x (API 文档收录, 变更日志未单列) | P2 已自实现并与宿主设置打通, 升级后仍需保留自实现以维持统一的设置语义 |
| 内联幽灵文本 (`InlineAutocomplete`) | 1.16.0 / 1.20.0 | 新能力, 目前无需求; 若引入需要配套设置与触屏接受手势 |
| `selectOnHover` | 1.26.0 | 鼠标场景, 触屏无收益 |
| 补全弹窗 / 文档提示改进 | 1.2x ~ 1.4x | 可能移除对 `installCompletionPopupTouchScrollPatch` 的需要, 需实测 |
| 移动端 textinput 修复 | 1.5.x ~ 1.1x | 可能改善 IME 兼容, 但与现有 IME 选区窗口补丁互斥, 需重新评估 UX-3 / UX-7 的 Gboard 问题 |
| 安全修复与 bug 修复累计 | 1.4.13 ~ 1.44.0 | 编辑器为本地资源, 攻击面有限 |

### 3. 风险

1. 文本输入层重写: `installImeTextInputSelectionWindow`, 触摸光标保护 (`installTouchCursorSelectionGuard`) 与选区菜单抑制均建立在 1.4.12 的事件时序上, 升级后需逐项重测 (`docs/dev/ace-editor-test-plan.md`, `ace-keyboard-viewport-test-plan.md`).
2. 补全弹窗: DOM / CSS 变化影响参数提示框的避让定位 (`autocompletePopupRect`), 以及弹窗触控滚动补丁.
3. `ext-language_tools`: completer 接口新增字段 (`triggerCharacters`, `hideInlinePreview`), 现有 `StaticIndexCompleter` / LSP completer 需复核 `getCompletions` 调用时序 (新版在 `prefix` 为空时的行为不同).
4. 资产体积与清单: `AceEditorAssets.requiredAssetPaths` 固定列出 `src-min-noconflict/*` 文件, 升级需同步; 主题与 `autojs6.css` 中覆盖的类名需复核.
5. jshint worker 与 mode 文件版本同步 (诊断提供方 `DIAGNOSTIC_PROVIDER_ACE_JSHINT`).

### 4. 试水计划 (启动条件满足后执行)

1. 插件分支 `spike/ace-1.44`: 替换 `src-min-noconflict/`, 更新 `AceEditorAssets`, 不改桥接层, 先跑不依赖 Ace 内部的门: `:app:verifyAutoJs6LspRuntime`, 宿主 `tools/ace-signature`, `tools/ace-tooltip`, `tools/ace-lsp` Node 测试.
2. 逐项处理第 1 节清单: 私有成员改公开路径; 补全弹窗相关代码以 1.44 的 `Autocomplete` API 重写; 删除被新版覆盖的补丁并记录.
3. 设备矩阵 (`avd-smoke-from-worktree` 记忆中的 4 台实体机 + AVD): IME 输入 / 组合输入 / 退格, 选区菜单与手柄, 补全接受与触控滚动, 参数提示避让, 长按手势, 捏合缩放, 长行安全模式.
4. 验收: 以 `ace-editor-ux-roadmap.md` 的 UX-1 ~ UX-7 验收项为回归基线, 全部通过方可合并.

回退标准: 任一 P0 级交互 (IME 输入丢失, 选区菜单错位, 补全无法接受) 在两个工作日内无法修复, 即放弃本次升级并保留评估记录.

### 5. 决定

- 2026-10-07: 暂缓升级 (维护者拍板 "不升级, 另立评估条目", roadmap D5). 本文作为 P5.1 / P5.2 的产出; P5.3 试水待启动条件出现后执行.
