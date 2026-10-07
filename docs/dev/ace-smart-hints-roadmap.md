# AutoJs6 Ace 编辑器智能提示 (Smart Hints) Roadmap

本文是 Ace 编辑器 "智能提示" (代码补全 / 参数提示 / 符号文档) 的可执行状态表.
以 2026-10-07 的本地代码快照为起点: 宿主 `master@9c8c736271` (`VERSION_NAME=6.8.0`, `VERSION_BUILD=5319`), 插件 `AutoJs6-Plugin-Ace-Editor` `1.23.0` (编辑器契约 5, Ace 1.4.12, 捆绑 TypeScript 6.0.3).
每个条目均可独立 Check 并落地, 后续会话按阶段逐步推进.

设计输入: 维护者 2026-10-07 的体验反馈 (提示 "不尽人意", 自动出现时机不可控, 参数提示缺少重载, 触屏下无法手动唤出) 与同日的两轮问答结论 (已固化为第 1 节的固定决策).

使用方式:

1. 每次会话开始时, 从 "阶段总览" 选取一个或多个未完成条目, 优先级按阶段顺序; 同一阶段内按编号顺序, 除非条目注明可并行.
2. 条目完成后勾选 `[x]`, 并在条目后追加证据 (提交 hash / 测试脚本 / 设备型号与 API level), 证据等级见附录 B.
3. 涉及字符串资源的条目必须同步 11 个 `strings.xml`; 涉及 feature / fix / improvement 的提交必须同步 10 个 `.changelog/lang_*.json` 并离线重新生成 markdown (D8). 阶段不绑定版本号, 日志写入落地时的开发版本.
4. 宿主与插件分属两个仓库, 涉及 `EditorPluginSession` 接口变化的条目必须同时提升契约版本并重新交付 `libs/editor-api.aar` (D6).
5. 附录 A 的 "待决事项" 在进入对应阶段前由维护者拍板, 拍板结果回填到 "固定决策".

---

## 1. 固定决策与硬约束

| 编号 | 决策 | 含义 |
| --- | --- | --- |
| D1 | 统一的 "智能提示" 触发动作, 由光标上下文分派 | 分派顺序: (1) 光标前有标识符前缀或处于 `xxx.` 之后且光标不在单词内部 -> 代码补全 (若同时位于调用括号内则一并显示参数提示); (2) 参数提示已显示且重载数 > 1 -> 切换到下一重载; (3) 光标在标识符内部 (只读模式下也含标识符末尾) -> 符号文档 (悬停信息); (4) 光标处于调用括号内 -> 参数提示 (强制显示, 忽略自动策略); (5) 其他 -> 代码补全 (全局符号 / 片段). 任一步骤无结果则落到下一步; 只读模式跳过补全分支. |
| D2 | TAB 键标签与点按行为不变, 长按 = 智能提示 | 不新增按键, 不做随上下文变化标签的动态按键. TAB 点按仍为 "接受补全 / 缩进"; 原长按功能 "跳转到上一个光标位置" 移入编辑器菜单 "跳转" 分组 (Ace 引擎可见, 无返回点时禁用), 并继续可被 ☆ 动作收藏记录与重放. |
| D3 | 硬件快捷键 | Ctrl+Space / Alt+Space = 智能提示 (覆盖 Ace 默认 `startAutocomplete` 绑定, 使其遵循 D1); Ctrl+Shift+Space = 参数提示 (已显示且有多重载时切换到下一重载, 与 VS Code 一致); Ctrl+Q = 符号文档. 不占用 Alt+Up / Alt+Down (Ace 默认为移动行). |
| D4 | 参数提示的重载数据与交互 | 数据层返回 `signatures[]` + `activeSignature` + `activeParameter` (+ `parameterDocs`, `isVariadic`, `applicableSpanStart`), 顶层同时保留当前签名的 `caption / signature / parameters / activeParameter / docText` 以兼容既有消费者与测试; 单签名来源 (静态索引, 内置签名, Python, Lua) 由 `normalizeSignatureHelp` 包装. UI 在重载数 > 1 时于提示窗口右上角显示 `▴ i/n ▾` 可点按切换 (P1 为 `‹ i/n ›`, 按 D13 改为上下按钮); 手动选择在同一调用内保持 (键为 `applicableSpanStart`, 否则为被调用者名称), 调用位置变化后重置; 实参超出形参数时高亮末位 rest 参数; 摘要优先显示当前参数的文档. |
| D5 | Ace 保持 1.4.12 | 新增实现只使用 Ace 公开 API (`execCommand("startAutocomplete")`, `completer.detach / goTo / insertMatch`, `commands.addCommand`), 不新增对 Ace 内部结构的依赖; 升级评估单列 P5, 不阻塞其他阶段. |
| D6 | 编辑器契约 5 -> 6, 宿主按契约门控 | `EditorPluginSession.triggerSmartHint()` 为契约 6 新增方法. 宿主 `AceScriptEditorEngine` 记录已加载插件的契约版本, `supportsSmartHint` 为 false 时 TAB 长按退回旧行为 (跳转到上一个光标位置), 不调用新方法; 收藏中的 "智能提示" 动作在旧插件上视为未处理. 插件 `AceEditorMetadata.CONTRACT_VERSION` 与清单 meta-data 同步为 6. |
| D7 | 自动出现策略做成设置项, 默认值与现状一致 | P2 在 "代码编辑器设置 > 编辑辅助" 下新增 "智能提示" 分组: 自动补全触发 (输入标识符时 [默认] / 仅输入 "." 后 / 关闭), 最少触发字符数 (1 [默认] / 2 / 3), 触发延迟 (0 [默认] / 100 / 250 ms), 参数提示触发 (光标进入调用时 [默认] / 仅输入 "(" "," 时 / 关闭). 插件直接读取宿主默认 SharedPreferences (沿用 `AceEditorDisplayPreferences` 模式), 通过 `invokeAce("setSmartHintOptions", json)` 热更新; 手动触发 (D1) 不受自动策略限制. |
| D8 | 资源, 日志与仓库约定 | 字符串遵循 `AGENTS.md` (11 个 `strings.xml`, `values` 与 `values-en` 一致, 按 name 升序); 用户可见变化同步 10 个 `.changelog/lang_*.json` 并按 memory 中的离线方式重新生成 markdown (回退 README 被重盖的日期行); 插件仓库同样维护 10 个 `.changelog/lang_*.json` 并按插件版本登记; 两个仓库均仅本地提交, 不推送远端. |
| D9 | P2 默认最少触发字符数保持 1 (Q1, 2026-10-07 拍板) | 与现状一致, 由设置项开放 1 / 2 / 3. |
| D10 | 参数提示在滚动后不自动恢复 (Q2, 2026-10-07 拍板) | 滚动即隐藏; 恢复只靠光标变化, 点按 (touchend 且期间未滚动) 或手动触发. |
| D11 | Native 引擎不提供智能提示能力 (Q3, 2026-10-07 拍板) | 所有智能提示设置与动作仅在 Ace 引擎下可见可用. |
| D12 | fx 键保留 (Q4, 2026-10-07 拍板) | Ace 模式下多功能键盘区布局不变. |
| D13 | 重载切换的三条通路 (Q5, 2026-10-07 拍板) | (1) 硬件键盘 Alt+Up / Alt+Down: 参数提示可见且重载数 > 1 时切换重载, 否则沿用 Ace 默认的上下移动行; (2) 多功能键盘的 Up / Down 键: 补全弹窗未打开, 参数提示可见且重载数 > 1 时切换重载而非移动光标, 可在 "智能提示" 设置中关闭 (默认开启); (3) 提示窗口右上角显示上 / 下两个按钮 (仅重载数 > 1 时显示, 附当前序号 i/n). D1 中 "再次触发智能提示 = 切换重载" 保留. |

---

## 2. 范围与非目标

范围内:

- 插件 Web 资源 `app/src/main/assets/editor/ace-builds-1.4.12/autojs6/`: `autojs6_ace_bridge.js` (触发分派, 快捷键, 状态, `window.AutoJsAce` API), `autojs6_signature_help.js` (参数提示控制器), `autojs6_ts_language_service.js` (TS 签名数据), `autojs6_tooltip.js` (悬停文档), `autojs6_completer.js` / `autojs6_lsp_client.js` (补全与回退链), `autojs6.css`
- 插件 Kotlin: `core/AceCodeEditor.kt`, `AceEditorPluginSession.kt`, `core/AceBridge.kt`, `AceEditorMetadata.kt`, 偏好读取类 (`core/AceEditorDisplayPreferences.kt` 同模式)
- 宿主 `plugin-api/editor-api` (`EditorPluginSession`, `EditorPluginContract`), `ui/edit/editor/engine/AceScriptEditorEngine.kt`, `ui/edit/EditorView.kt`, `ui/edit/EditorMenu.java`, `ui/edit/keyboard/*`, `res/menu/menu_editor.xml`, `res/xml/fragment_code_editor_settings.xml`
- 测试工具: 宿主 `tools/ace-signature`, `tools/ace-tooltip`, `tools/ace-lsp`, `tools/ace-completion`; 插件 `tools/ace-lsp/verify-runtime.mjs` (Gradle `:app:verifyAutoJs6LspRuntime`)

非目标 (本 Roadmap 不处理):

- Native 引擎的补全条与符号栏行为 (保持现状).
- Ace 升级本身 (仅在 P5 评估).
- 外部 LSP 服务器, 项目级索引等 `docs/dev/ace-lsp-design.md` 已划出的范围.
- 多功能键盘区的布局重排或新增按键 (D2).

---

## 3. 阶段总览

### P0 基线与测试基座

- [x] P0.1 记录现状基线 (附录 D). (证据: 2026-10-07 代码核对, 本文附录 D)
- [x] P0.2 宿主 `tools/ace-signature/test-signature-help.mjs`, `tools/ace-tooltip/test-tooltip.mjs`, `tools/ace-lsp/test-lsp-client.mjs` 支持 `AUTOJS6_ACE_EDITOR_ROOT` (与 `tools/ace-completion/indices-utils.mjs` 一致), 使其能针对插件仓库的资产运行. (证据: `AUTOJS6_ACE_EDITOR_ROOT=D:\idea-projects\AutoJs6-Plugin-Ace-Editor node tools/ace-signature/test-signature-help.mjs` 与 `tools/ace-tooltip/test-tooltip.mjs` 于 2026-10-07 通过)

验收: 三个脚本在设置环境变量后均能加载插件资产并通过.

### P1 手动触发与参数提示重载 (2026-10-07 落地)

提交: 宿主 `f9094f9b35` (roadmap), `36584f5088` (功能), 本节日志提交 (见 git log); 插件 `77fd25b` (`feat(editor): smart hint trigger and signature help overloads`, 1.24.0, 契约 6).

- [x] P1.1 插件 `autojs6_ts_language_service.js` 返回全部重载 (`signatures[]`, `activeSignature`, `parameterDocs`, `isVariadic`, `applicableSpanStart`), 顶层兼容字段镜像当前签名 (D4). (证据: 插件 `tools/ace-lsp/verify-runtime.mjs` 新增 `Array#splice` 重载断言, Gradle `:app:verifyAutoJs6LspRuntime`)
- [x] P1.2 插件 `autojs6_signature_help.js`: `normalizeSignatureHelp`, `isInsideCallArguments`, 控制器的 `‹ i/n ›` 导航行 (点按切换, 阻止焦点转移; P2 按 D13 改为 `▴ i/n ▾`), `cycleSignature`, `isVisible`, `getSignatureCount`, 手动选择在同一调用内保持, rest 参数高亮, 当前参数文档优先; `autojs6.css` 导航行样式 (点按目标 32px). (证据: 宿主 `tools/ace-signature/test-signature-help.mjs` 新增重载用例, 2026-10-07 通过)
- [x] P1.3 插件 `autojs6_ace_bridge.js`: `triggerSmartHint` 按 D1 分派, `triggerSignatureHelp`, `triggerSymbolDocumentation`; 命令 `autojs6SmartHint` (Ctrl-Space|Alt-Space), `autojs6SignatureHelp` (Ctrl-Shift-Space), `autojs6ShowHover` (Ctrl-Q); 状态 JSON 增加 `signatureHelpVisible` / `signatureCount`; `window.AutoJsAce` 增加 `triggerSmartHint`, `triggerSignatureHelp`, `triggerSymbolDocumentation`, `cycleSignatureHelp`. (证据: `test-signature-help.mjs` 的桥接层字符串断言; 设备手测见 P1.8)
- [x] P1.4 宿主 `plugin-api/editor-api`: `EditorPluginSession.triggerSmartHint()`, `EditorPluginContract.VERSION = 6`, `EditorPluginSmartHintContractTest`; 重新构建 `editor-api-release.aar` 并交付到插件 `libs/editor-api.aar`. (证据: `:plugin-api:editor-api:assembleRelease :plugin-api:editor-api:testDebugUnitTest` 于 2026-10-07 通过)
- [x] P1.5 插件 Kotlin: `AceCodeEditor.triggerSmartHint()`, `AceEditorPluginSession.triggerSmartHint()`, `AceEditorMetadata.CONTRACT_VERSION = 6`, 清单 meta-data 6, `VERSION_NAME` 1.23.0 -> 1.24.0. (证据: 插件 `:app:testDebugUnitTest` 含 `AceEditorMetadataTest`)
- [x] P1.6 宿主 `AceScriptEditorEngine`: 记录插件契约版本, `supportsSmartHint`, `triggerSmartHint()`; `EditorView`: TAB 长按分派到智能提示并记录动作 `editor:smart_hint` (旧契约退回原行为), 收藏重放 `ACTION_SMART_HINT`; 菜单 "跳转 > 跳转到上一个光标位置" (`menu_editor.xml`, `EditorMenu.java`, `actionForMenuItem`), Ace 引擎可见且无返回点时禁用 (D2, D6). (证据: 宿主 `:app:assembleAppDebug`; 设备手测见 P1.8)
- [x] P1.7 字符串 `text_smart_hint` (11 个 `strings.xml`); `.changelog/lang_*.json` v6.8.0 新增 1 条 feature 与 2 条 improvement (10 种语言) 并离线重新生成 CHANGELOG / README (回退日期重盖行); `docs/dev/ace-editor-ux-roadmap.md` UX-5 追加指向说明. (证据: 本次提交)
- [x] P1.8 设备手测 (附录 B 的 E3, 2026-10-07, Xiaomi 968e9f18 / API 35, 宿主 6.8.0 build 5319 + 插件 1.24.0): 空行与 `Obj|` 长按 TAB 弹出补全 (后者为语义补全 Object / isObject...); `arr.splice(|0, 1)` 点按进入后自动显示参数提示 `1/2` (当前参数 start 加粗, 摘要为该参数文档), 长按 TAB 切到 `2/2` (rest 签名), 点按提示框 `›` 在 1/2 与 2/2 间往返且提示框不消失, 光标焦点保持; `console.lo|g` 长按 TAB 显示 console.log 的符号文档; 菜单 "Jump > Jump to previous cursor" 可见可用, 点按后光标 3:10 -> 4:4 并记录动作 `editor:jump_to_previous_cursor`; 动作收藏记录 `editor:smart_hint`; 只读模式下键盘区整体隐藏 (TAB 不可达, 符合既有设计), 自动参数提示仍显示且保留手动选择的重载. 未覆盖 (待后续补测): 硬件键盘 Ctrl+Space / Ctrl+Shift+Space / Ctrl+Q (设备无外接键盘), 旧契约 (5) 插件 APK 的退回行为 (仅代码级门控), 暗色主题下导航行观感, Native 引擎下菜单项隐藏.
- 备注: 无扩展名的文档 (如内容型草稿 "smoke-hints") 以纯文本模式打开, 语义服务不参与, 此时智能提示只有 Ace 本地词条补全与内置签名; 文件名带 `.js` 后全部功能可用.

验收:

- [x] 一次长按 TAB 在五种上下文 (`Obj|`, `foo.|`, `[].splice(|)`, `foo(ba|`, `cli|ck`) 下分别得到补全 / 成员补全 / 参数提示 / 补全 + 参数提示 / 符号文档 (Node 层按 `isInsideCallArguments` 与控制器状态验证; 设备层见 P1.8).
- [x] 参数提示框在 TS 返回多重载时显示序号与可点按箭头, 单签名来源不显示导航行.
- [x] 契约 5 插件在契约 6 宿主上不会调用 `triggerSmartHint` (`supportsSmartHint` 门控).

### P2 自动出现策略与 "智能提示" 设置组 (2026-10-07 落地)

- [x] P2.1 宿主 `fragment_code_editor_settings.xml` 新增 "智能提示" 分类 (标题复用 `text_smart_hint`): 自动补全 (输入标识符时 / 仅输入 "." 后 / 关闭), 补全最少触发字符数 (1 / 2 / 3), 补全触发延迟 (无延迟 / 100 ms / 250 ms), 参数提示 (光标进入调用时 / 仅输入 "(" 或 "," 时 / 关闭), 方向键切换重载 (开关, D13). 键与默认值入 `strings_donottranslate.xml` / `bools.xml`, 数组入 `arrays.xml`, 11 个 `strings.xml` 补齐标题 / 摘要 / 条目 / 长按说明 (说明写明 TAB 长按, Ctrl+Space, Ctrl+Shift+Space, Ctrl+Q, Alt+Up / Alt+Down). `CodeEditorSettingsFragment.syncEditorEnginePreferenceAvailability` 在 Native 下标记为不可用 (D11), `EditorSettingActionRegistry` 登记 5 个键, `EditorView.mEditorDisplayPreferenceKeys` 纳入 5 个键以触发 `notifyDisplayPreferencesChanged`. (证据: 宿主 `:app:assembleAppDebug`; 设备手测见 P2.6)
- [x] P2.2 插件 `core/AceEditorSmartHintPreferences.kt` 读取宿主偏好并序列化为 JSON, `AceBridge.getSmartHintOptions()` 提供初始值, `AceCodeEditor.notifyDisplayPreferencesChanged()` 追加 `invokeAce("setSmartHintOptions", json)`; 桥接层 `applySmartHintOptions` 归一化非法值并热更新. (证据: 插件 `:app:testDebugUnitTest`)
- [x] P2.3 桥接层固定 `enableLiveAutocompletion: false` (含长行安全模式切换路径), 由 `scheduleLiveCompletion` 在 afterExec `insertstring` 后按模式 / 最少字符数 (`ace/autocomplete/util.getCompletionPrefix`) / 延迟调度 `startAutocomplete`, backspace 清空前缀时关闭弹窗; "." 触发在 "关闭" 模式下不再重启补全; 手动触发 (D1) 不受限制. (证据: `test-signature-help.mjs` 桥接层字符串断言; 设备手测见 P2.6)
- [x] P2.4 参数提示控制器 `autoTrigger` (cursor / typing / off): cursor 模式沿用现状; typing 模式仅在输入 "(" "," 后自动显示, 显示期间仍跟随光标; off 模式只响应强制显示. touchend 且期间未滚动时重新评估光标位置 (同一位置点按可恢复), 滚动后不恢复 (D10); ESC (`handleEscapeKey`) 同时隐藏参数提示与悬停框. (证据: `test-signature-help.mjs` 新增策略与触控用例, 2026-10-07 通过)
- [x] P2.5 重载切换三条通路 (D13): `autojs6OverloadUp` / `autojs6OverloadDown` (Alt-Up / Alt-Down, 无多重载提示时执行 Ace 的 `movelinesup` / `movelinesdown`); `moveCompletionSelectionOrCursor` 在补全弹窗未打开, 参数提示可见且重载数 > 1 时切换重载 (受 `arrowOverloadCycling` 开关控制); 提示窗口右上角 `▴ i/n ▾`. (证据: `test-signature-help.mjs` 导航按钮断言; 设备手测见 P2.6)
- [x] P2.6 设备手测 (2026-10-07, Xiaomi 968e9f18 / API 35, 宿主 6.8.0 build 5319 + 插件 1.26.0): 编辑器内设置对话框出现 "Smart hints" 分类及 5 个条目 (默认值 While typing identifiers / 1 / No delay / When the cursor enters a call / 开关开启); 通过默认 SharedPreferences 写入 `key_$_ace_smart_hint_completion_trigger = off` 后 (宿主监听 -> `notifyDisplayPreferencesChanged` -> `setSmartHintOptions`) 在 `Obj` 后输入 e 不弹窗, TAB 长按仍弹出 Object 补全; 提示窗口右上角显示 `▴ 1/2 ▾`, 多功能键盘 "向下" 键切到 `2/2` 且光标不动, 关闭 "方向键切换重载" 后 "向下" 键改为移动光标 (2:12 -> 3:13); 同一位置再次点按后参数提示恢复并保留 `2/2`; ESC 隐藏参数提示; 参数提示 "typing" 模式下点按进入 `splice(` 不显示, 输入 "," 后显示 (deleteCount 加粗); 测试结束后已移除写入的偏好键. 未覆盖: 最少字符数 2 / 延迟 100 ms 的设备表现 (逻辑与 "关闭" 共用 `scheduleLiveCompletion`), Alt+Up / Alt+Down (无外接键盘), 设置对话框内通过列表项修改并持久化的路径 (adb 点按列表项未命中, 需人工复核), Native 引擎下的不可用标记.

验收:

- [x] 默认值下行为与 P1 完成时逐项一致 (Node 层: cursor 模式用例; 设备层: 默认值下 `1/2` 自动显示, 点按恢复, ESC 隐藏).
- [x] 关闭自动补全后仅 TAB 长按 / Ctrl+Space 可弹出 (设备层验证 TAB 长按); 参数提示 "仅输入 ( , 时" 模式下点按进入调用不再自动弹出, 输入 "," 后显示 (设备层验证).

### P3 触控下的文档阅读

- [ ] P3.1 参数提示与悬停框支持点按固定 (不随下一次 touchstart 隐藏), 长文档可滚动, 文档中的项目文档链接可跳转 (复用 `showManual` 通路).
- [ ] P3.2 补全弹窗条目的文档说明在触屏可达 (Ace 1.4.12 的 docTooltip 仅随 hover 显示): 评估在弹窗条目右侧增加 "ⓘ" 点按区或长按条目显示文档.
- [ ] P3.3 Python / Lua 异步参数提示在 D1 分派中的处理: `showAtPosition` 对异步来源返回 "已请求" 状态, 避免落到补全兜底.

验收: 触屏下无需硬件鼠标即可阅读参数文档, 条目文档与悬停文档.

### P4 数据层重载与质量

- [ ] P4.1 静态索引生成链 (`tools/ace-completion`, `autojs6_indices.source.json` schema) 支持每个符号多条签名; `autojs6_completer.js` `findSignatureHelp` 返回 `signatures[]`.
- [ ] P4.2 `BUILTIN_SIGNATURES` 扩展为常用 ECMAScript 内置方法的多重载 (如 `Array.prototype.splice`, `String.prototype.replace`), 仅在 TS 服务不可用时生效.
- [ ] P4.3 Python / Lua provider 返回多签名形状 (Pyright / LuaLS 的 `signatures[]` 直通).
- [ ] P4.4 修正 `docs/dev/ace-lsp-design.md` 中 "TypeScript 4.2.4" 的描述为实际捆绑版本 (6.0.3), 并补充重载数据形状.

验收: LSP 关闭时 `[].splice(|)` 仍能显示两条重载; Python 文件内重载切换可用.

### P5 Ace 升级评估 (独立, 不阻塞)

- [ ] P5.1 盘点 `autojs6_ace_bridge.js` 对 Ace 1.4.12 内部结构的依赖: `installCompletionPopupTouchScrollPatch`, `.ace_mobile-menu` 抑制, IME 选区窗口 (`installImeTextInputSelectionWindow`), `completer.popup.isOpen`, `completer.activated`, `renderer.textToScreenCoordinates` 等, 形成清单.
- [ ] P5.2 列出目标版本 (1.44.x, 2026-05-11) 的收益: `triggerCharacters` (1.21), `liveAutocompletionDelay` / `liveAutocompletionThreshold`, 内联幽灵文本 (1.16 / 1.20), `selectOnHover` (1.26), 弹窗与触控改进; 评估能否替代 P2.3 的自实现.
- [ ] P5.3 在插件分支上试水升级, 跑通 `verifyAutoJs6LspRuntime`, 宿主 Node 测试与设备手测矩阵 (`docs/dev/ace-editor-test-plan.md`), 记录回退标准.

验收: 形成 "升级 / 暂缓" 的书面结论与风险清单, 由维护者拍板.

---

## 附录 A: 待决事项

2026-10-07 已全部拍板并回填为 D9 ~ D13:

- Q1 P2 的默认最少触发字符数: 保持 1 (D9).
- Q2 参数提示是否在滚动结束后自动恢复: 否 (D10).
- Q3 Native 引擎是否需要对应能力: 否 (D11).
- Q4 fx 键在 Ace 模式下的去留: 保留 (D12).
- Q5 重载切换: 需要. Alt+Up / Alt+Down, 多功能键盘 Up / Down (可在设置中关闭), 提示窗口右上角上下按钮 (D13).

当前无待决事项.

## 附录 B: 证据等级

- E0 静态核对: 代码阅读与字符串断言 (如 `test-signature-help.mjs` 对桥接层的 `includes` 检查).
- E1 Node 测试: 宿主 `tools/ace-*/test-*.mjs` 在 `AUTOJS6_ACE_EDITOR_ROOT` 指向插件仓库时通过.
- E2 Gradle 校验: 插件 `:app:verifyAutoJs6LspRuntime`, `:app:testDebugUnitTest`; 宿主 `:plugin-api:editor-api:testDebugUnitTest`, `:app:assembleAppDebug`.
- E3 设备手测: 记录设备型号与 API level (可用设备见 memory `avd-smoke-from-worktree`), 宿主与插件 APK 同时安装.

## 附录 C: 关键文件索引

| 文件 | 职责 |
| --- | --- |
| 插件 `autojs6/autojs6_ace_bridge.js` | `triggerSmartHint` 分派 (D1), 快捷键命令, 状态 JSON, `window.AutoJsAce` API, "." 后补全重启, LSP 补全器安装 |
| 插件 `autojs6/autojs6_signature_help.js` | 参数提示控制器 (重载导航, 定位避让补全弹窗, 自动 schedule 时机), `normalizeSignatureHelp`, `isInsideCallArguments`, 内置签名 |
| 插件 `autojs6/autojs6_ts_language_service.js` | TS 服务: 补全, 悬停, 签名 (多重载), 诊断, 定义 |
| 插件 `autojs6/autojs6_tooltip.js` | 悬停文档框 (`showAtPosition` 可按文本位置显示, 供 Ctrl+Q / 智能提示调用) |
| 插件 `autojs6/autojs6_lsp_client.js` | 多语言分发与回退链 (`getSignatureHelp` / `getSignatureHelpAsync`) |
| 插件 `autojs6/autojs6_completer.js` | 静态索引补全与单签名 `findSignatureHelp` (P4 对象) |
| 插件 `autojs6/autojs6.css` | 参数提示 / 悬停框样式, 导航行样式 |
| 插件 `core/AceCodeEditor.kt`, `AceEditorPluginSession.kt`, `core/AceBridge.kt` | Kotlin 到 JS 的 `invokeAce` 通路, 状态解析, 宿主偏好读取 (`@JavascriptInterface`) |
| 插件 `AceEditorMetadata.kt`, `AndroidManifest.xml`, `libs/editor-api.aar`, `version.properties` | 契约版本与 API 交付 |
| 宿主 `plugin-api/editor-api/.../EditorPluginSession.kt`, `EditorPluginContract.kt` | 宿主与插件的进程内 ABI |
| 宿主 `ui/edit/editor/engine/AceScriptEditorEngine.kt` | 插件会话适配, 契约门控 (`supportsSmartHint`) |
| 宿主 `ui/edit/EditorView.kt` | 快捷面板点按 / 长按分派, 动作收藏记录与重放, 返回点记忆 |
| 宿主 `ui/edit/EditorMenu.java`, `res/menu/menu_editor.xml` | 编辑器菜单 (跳转分组) |
| 宿主 `ui/edit/keyboard/AceShortcutPanel.kt`, `SymbolsConfigStore.kt`, `completion/CodeCompletionBar.java` | 多功能键盘区按键定义与触控手势 |
| 宿主 `res/xml/fragment_code_editor_settings.xml`, `ui/settings/EditorSettingActionRegistry.kt` | 代码编辑器设置 (P2 对象) |
| 宿主 `tools/ace-signature`, `tools/ace-tooltip`, `tools/ace-lsp`, `tools/ace-completion` | Node 测试与索引生成链 |

## 附录 D: 现状基线 (2026-10-07, P1 之前)

- 补全: `enableBasicAutocompletion` / `enableLiveAutocompletion` / `enableSnippets` 固定开启 (长行安全模式下关闭); 每输入一个标识符字符即弹出 (Ace 1.4.12 无最少字符数与延迟); 输入 "." 后由桥接层 30 ms 定时重启补全; 结果上限 300 条, 文档超过 512 KiB 走静态路径. 手动触发只有 Ace 默认的 Ctrl-Space / Ctrl-Shift-Space / Alt-Space (硬件键盘); 宿主只能接受 (TAB), 上下移动 (Up / Down), 关闭 (ESC).
- 参数提示: 光标变化 / 选区变化 / 文档变化后 80 ms 显示, 输入 "(" "," ")" 或 insertMatch / 删除 / 粘贴后也会 schedule; 滚动, 容器 mousedown / touchstart / wheel 时隐藏; 无 ESC 隐藏; TS 多重载只保留 `selectedItemIndex` 一条, 无切换; 静态索引与 11 条内置签名每符号单签名.
- 悬停文档: 仅 mousemove 450 ms 后显示, touchstart 隐藏, 触屏不可达; `AutoJsAce.showLspTooltip` 仅被 androidTest 调用.
- 键盘区: 左侧 2x4 固定块 (fx, ESC, Up, TAB / ☆, Left, Down, Right), 点按与长按均已分配; 右侧符号流 75 项可滚动, 长按映射见 `EditorCommandDispatcher.onCompletionLongClick`.
- 设置: 仅有 LSP 级开关 (`key_$_ace_lsp_enabled`, 文件类型, 语义语言, 声明组), 无自动弹出 / "." 触发 / 参数提示的独立设置.
