# Ace 升级评估 (1.4.12 -> 1.44.x)

对应 `ace-smart-hints-roadmap.md` 的 P5. 结论先行: **暂缓升级**. 智能提示路线图所需能力均已在 1.4.12 上由桥接层自实现 (P1 / P2), 升级的直接收益 (内联幽灵文本, triggerCharacters, selectOnHover) 目前没有对应的产品需求, 而文本输入层与补全弹窗的重写会波及已经稳定的移动端交互 (IME 选区窗口, 选区菜单抑制, 弹窗触控滚动). 当出现需要新版能力的需求, 或 1.4.12 暴露无法绕过的缺陷时再按第 4 节的试水计划启动.

快照: 2026-10-07, 插件 `AutoJs6-Plugin-Ace-Editor` 1.26.0, Ace 1.4.12 (2020-07-06), 官方最新 1.44.0 (2026-05-11).

## 1. 桥接层对 Ace 1.4.12 的依赖清单

以 `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/` 下的脚本为对象 (grep 统计, 2026-10-07).

### 1.1 私有成员 (`$` 前缀, 升级时无兼容承诺)

| 访问 | 次数 | 用途 | 升级替代 |
| --- | --- | --- | --- |
| `session.$toggleFoldWidget` | 4 | 点按折叠占位符展开 / 折叠 (UX-1) | 1.1x+ 仍存在, 但需复核签名; 可改用 `session.toggleFold` / `onFoldWidgetClick` 公开路径 |
| `editor.$mouseHandler` (`.isMousePressed`) | 3 | 触摸期间抑制光标通知 | 改用 `editor.on("mousedown")` / `mouseup` 自行维护状态 |
| `renderer.$textLayer` | 2 | 长行安全模式下对文本层设置 `aria-hidden` | 改用 `container.querySelector(".ace_text-layer")` |
| `renderer.$gutterLayer` | 1 | 折叠部件命中测试 | 同上, 用 DOM 查询 |
| `renderer.$padding` | 1 | 坐标换算 | `renderer.getPadding()` 公开方法 |
| `renderer.$isMousePressed` | 1 | 同 `$mouseHandler` | 同上 |
| `session.$autojs6LongLineSafetyMode`, `editor.$autojs6*` | 4 | 桥接层自有标记 | 无风险 |

### 1.2 半公开对象与 DOM 结构

- `editor.completer`: `detach` (10), `popup` (2, 含 `popup.isOpen` / `popup.container`), `insertMatch` (2), `goTo` (2), `activated` (1). 1.2x 起 `Autocomplete` 的 `popup` 改为惰性创建, `activated` 语义保持; `goTo` / `insertMatch` 仍为公开方法.
- `editor.textInput.getElement` / `resetSelection` (IME 选区窗口 `installImeTextInputSelectionWindow`). 1.5.x 重写了 `textinput.js` (组合输入, Android 退格, 选区同步), 这是升级风险最高的区域.
- DOM 类名: `.ace_scroller`, `.ace_text-layer`, `.ace_gutter`, `.ace_fold-widget`, `.ace_autocomplete`, `.ace_tooltip`, `.error_widget`, `.ace_mobile-menu` (宿主以 `ActionMode` 替代, 需保持抑制逻辑有效).
- 模块: `ace/autocomplete`, `ace/autocomplete/util` (`getCompletionPrefix`), `ace/ext/beautify`, `ace/ext/error_marker`, `ace/ext/language_tools`, `ace/range`, `ace/snippets`, `ace/mode/javascript/jshint`.
- 自实现并绕开 Ace 默认行为的部分: 实时补全调度 (`scheduleLiveCompletion`, P2), "." 后重启补全, 补全弹窗触控滚动补丁 (`installCompletionPopupTouchScrollPatch`), 参数提示 / 悬停框 (完全自实现, 不依赖 Ace 内部), 键盘命令覆盖 (Ctrl-Space / Alt-Up / Alt-Down).

## 2. 1.44.x 相对 1.4.12 的收益

| 能力 | 引入版本 | 与路线图的关系 |
| --- | --- | --- |
| `completer.triggerCharacters` | 1.21.0 | 可替代桥接层的 "." 触发, 但现有实现已满足需求 |
| `liveAutocompletionDelay` / `liveAutocompletionThreshold` | 1.2x (API 文档收录, 变更日志未单列) | P2 已自实现并与宿主设置打通, 升级后仍需保留自实现以维持统一的设置语义 |
| 内联幽灵文本 (`InlineAutocomplete`) | 1.16.0 / 1.20.0 | 新能力, 目前无需求; 若引入需要配套设置与触屏接受手势 |
| `selectOnHover` | 1.26.0 | 鼠标场景, 触屏无收益 |
| 补全弹窗 / 文档提示改进 | 1.2x ~ 1.4x | 可能移除对 `installCompletionPopupTouchScrollPatch` 的需要, 需实测 |
| 移动端 textinput 修复 | 1.5.x ~ 1.1x | 可能改善 IME 兼容, 但与现有 IME 选区窗口补丁互斥, 需重新评估 UX-3 / UX-7 的 Gboard 问题 |
| 安全修复与 bug 修复累计 | 1.4.13 ~ 1.44.0 | 编辑器为本地资源, 攻击面有限 |

## 3. 风险

1. 文本输入层重写: `installImeTextInputSelectionWindow`, 触摸光标保护 (`installTouchCursorSelectionGuard`) 与选区菜单抑制均建立在 1.4.12 的事件时序上, 升级后需逐项重测 (`docs/dev/ace-editor-test-plan.md`, `ace-keyboard-viewport-test-plan.md`).
2. 补全弹窗: DOM / CSS 变化影响参数提示框的避让定位 (`autocompletePopupRect`), 以及弹窗触控滚动补丁.
3. `ext-language_tools`: completer 接口新增字段 (`triggerCharacters`, `hideInlinePreview`), 现有 `StaticIndexCompleter` / LSP completer 需复核 `getCompletions` 调用时序 (新版在 `prefix` 为空时的行为不同).
4. 资产体积与清单: `AceEditorAssets.requiredAssetPaths` 固定列出 `src-min-noconflict/*` 文件, 升级需同步; 主题与 `autojs6.css` 中覆盖的类名需复核.
5. jshint worker 与 mode 文件版本同步 (诊断提供方 `DIAGNOSTIC_PROVIDER_ACE_JSHINT`).

## 4. 试水计划 (启动条件满足后执行)

1. 插件分支 `spike/ace-1.44`: 替换 `src-min-noconflict/`, 更新 `AceEditorAssets`, 不改桥接层, 先跑不依赖 Ace 内部的门: `:app:verifyAutoJs6LspRuntime`, 宿主 `tools/ace-signature`, `tools/ace-tooltip`, `tools/ace-lsp` Node 测试.
2. 逐项处理第 1 节清单: 私有成员改公开路径; 补全弹窗相关代码以 1.44 的 `Autocomplete` API 重写; 删除被新版覆盖的补丁并记录.
3. 设备矩阵 (`avd-smoke-from-worktree` 记忆中的 4 台实体机 + AVD): IME 输入 / 组合输入 / 退格, 选区菜单与手柄, 补全接受与触控滚动, 参数提示避让, 长按手势, 捏合缩放, 长行安全模式.
4. 验收: 以 `ace-editor-ux-roadmap.md` 的 UX-1 ~ UX-7 验收项为回归基线, 全部通过方可合并.

回退标准: 任一 P0 级交互 (IME 输入丢失, 选区菜单错位, 补全无法接受) 在两个工作日内无法修复, 即放弃本次升级并保留评估记录.

## 5. 决定

- 2026-10-07: 暂缓升级 (维护者拍板 "不升级, 另立评估条目", roadmap D5). 本文作为 P5.1 / P5.2 的产出; P5.3 试水待启动条件出现后执行.
