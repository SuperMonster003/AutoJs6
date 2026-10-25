# Codex 成果静态审查 (api-coverage P1.1)

日期: 2026-10-25. 会话: api-cov-p1-1 (local_09a9e38f). 范围: ROADMAP 附录 A 的 36 份报告 (`docs/dev/`), 全部静态, 不连设备 (真机抽样是 P1.2 / P1.3).

## 0. 方法与数据

- 矩阵: `python tools/api-coverage/check.py --matrix --write-matrix` (含 Ace, dataSource ksp), 宿主 2cad31444 起的 `docs/api-coverage/MATRIX.md` 与 `build/reports/api-coverage/runtime-matrix.json`; 2724 行 = 显式导出 1368 + 返回对象成员 1356. Q17 (a) 已落地: kotlin-class 返回对象成员 (1066) 三列记 not-portable / java-prototype.
- 四处一致性: Node d.ts (`docs/nodejs/types/autojs6-node/*.d.ts`, 经矩阵 Node 列) / `docs/HOST-API.md` 状态表 (矩阵 `hostApi` 字段与 `parse_host_api`) / `node_bridge_sources.cpp` facade (Codex 的结构检查脚本 `tools/api-coverage/check-runtime-*.mjs` / `check-bun-*.mjs` 比对 facade 导出与宿主方法集) / Bun `host-client.d.mts` (矩阵 Bun 列, 能力名经 HOST_API_METHODS 与 `bun.memberCapabilities`).
- 文档 / d.ts / Ace: 矩阵 doc / dts / ace 列 (来自 check.py 的 documentation.json 与 surfaces.json).
- 运行时仓库版本: Node 990f9e35, Bun 3056b94, 宿主 HEAD 见各节提交.
- 报告只读与核对相关的段落 (交付 / 覆盖 / 剩余边界), 不复述.

Codex 的 25 个静态检查脚本在当前 HEAD 全部重跑 (2026-10-25): 24 个通过; `check-runtime-tts.mjs` 失败 (断言旧源码文本 `session.pending.values.toList()`, 已被内存门禁 C36 的 1183d201db 改为 `concurrentSnapshot()`), 该脚本在 CI "Script API coverage" 中, 已直接修 (6260c9788). CI 只跑其中 8 个 (java-callbacks / tts / speech / epub / mail / flow / ai / ai-agent), 其余 17 个 (bridges, events, device-dialogs, android-tasks, shell, shell-contract, java-interop, views, ui-compose, webview-callbacks, bun-automation / storage / database / sensors, test-web-*) 只在本地运行, 见 R1.

## 1. 逐份核对结论

状态: 一致 = 报告宣称的名称数与矩阵覆盖行数相符且四处一致; 有差异 = 见第 2 节编号; 超出分母 = 报告交付的是运行时专有扩展, 宿主没有对应显式导出 (见第 4 节).

### 第 1 组 (附录 A 第 1-6 份)

| 报告 | 宣称 | 矩阵 (行: Node main / Worker / Bun 覆盖) | 文档 / d.ts / Ace | 结论 |
|---|---|---|---|---|
| runtime-api-coverage-2026-10-17 (总审计, 已封存) | pinyin (6) / pinyin4j (3) / screen.metrics (11) / console.control (31) 的 Bun 授权; Node console 窗口控制与坐标 | pinyin 16: 16 / 16 / 6; pinyin4j 3: 3 / 3 / 3; console 50: 45 / 45 / 31; 坐标全局 10 行三列覆盖 | 全部有标题 / 声明 / 补全 | 有差异: R2 (console 5 个设置方法), R3 (Bun pinyin 10 个常量), R4 (Bun console 输出函数), R5 (HOST-API 状态表) |
| runtime-opencc-images-2026-10-17 | OpenCC convert + 32 转换; Node 41 / Bun 30 个常用图像函数 (后被 image-parity 取代) | opencc 33: 33 / 33 / 33 (text.opencc) | 33 / 33 / 33 | 一致 |
| runtime-image-parity-2026-10-17 | images 81 函数 + ObjectFrame + readImage = 83 成员 | images 82 (81 + ObjectFrame): 82 / 82 / 82; 加全局放置 (captureScreen 等 9 行) 共 91 行全覆盖; Bun 能力 image.process 76 / image.files 7 / screen.capture 8 (28 行缺能力名已由 Q12 补齐) | 82 / 82 / 82 (d.ts arity 79, name 3) | 一致 (readImage 是运行时多出的旧桥名, 不在宿主导出) |
| runtime-capture-worker-2026-10-17 | Bun requestScreenCapture / captureScreen / stopScreenCapture (screen.capture); Worker 可导入 opencc / images | 已计入 images 行; Worker 列 images / opencc 全覆盖 | 同上 | 一致 |
| runtime-node-recording-2026-10-17 | Node screen_recorder 4 方法 (main + Worker), Worker media_projection / recorder | 无矩阵行 (宿主无 screenRecorder 显式导出; media / recorder 属 @ScriptPrototype 模块) | - | 超出分母 (第 4 节) |
| runtime-video-worker-2026-10-17 | Bun screenRecorder 4 方法 (screen.record); Worker 导入 pinyin / pinyin4j / console / screen_metrics | Worker 列 pinyin 16 / pinyin4j 3 / console 45 覆盖; screenRecorder 无矩阵行 | - | 一致 (screenRecorder 超出分母); Worker 侧 input / rawInput / build 报告写明显式失败, 矩阵 Bun 列记 pending, 归 R4 |

### 第 2 组 (附录 A 第 7-12 份)

| 报告 | 宣称 | 矩阵 (行: Node main / Worker / Bun 覆盖) | 文档 / d.ts / Ace | 结论 |
|---|---|---|---|---|
| runtime-audio-capture-2026-10-17 | Bun screenRecorder 音频 (screen.record.microphone / playback) 与录屏中截图 | 无矩阵行 | - | 超出分母 |
| runtime-ui-compose-2026-10-18 | Node / Bun ui (JSON 布局) 与 compose (54 个组件工厂, modifiers, state, mount / dialog / floaty) | compose 16: 15 / 15 / 15 (ui.compose), 54 个组件工厂属 installComputed 目录, 不在分母 (check.py scope 已注明); ui 37: 2 / 2 / 2 (ui.findView, ui.inflate), 4 行 internal-exports, 31 行 pending | compose 16 / 16 / 16; ui 33 / 33 / 33 (4 个内部导出无) | 有差异: R6 (compose.ComposeError), R7 (ui 31 行) |
| runtime-ui-interop-2026-10-18 | ui.context / inflate / createView / findView / view 不透明句柄, compose.AndroidView / attach | ui.inflate / ui.findView 已计入; context / createView / view / bind / state / showLayout 是运行时专有成员 | - | 一致 (其余超出分母) |
| runtime-xml-views-2026-10-18 | 12 个原生表单 / 布局控件, 46 可写 + 6 只读 View 属性 | 无矩阵行 (属性契约在运行时句柄上) | - | 超出分母 |
| runtime-view-components-2026-10-18 | WebView / Drawer / Toolbar / ViewPager, 属性契约 58 可写 + 11 只读, 12 个组件命令 | 无矩阵行 | - | 超出分母 |
| runtime-webview-callbacks-2026-10-18 | 40 个 client / download 回调, 10 个公开方法 (结构检查 19 个 web 方法) | 无矩阵行; 宿主 InjectableWebView# / InjectableWebClient# / WebSocket# 返回对象记 java-prototype (Q17) | - | 超出分母 |

## 2. 不一致清单

列: 编号 | 模块 | 名称 | Node d.ts | HOST-API | facade | Bun | 文档 / d.ts / Ace | 建议归属.

| 编号 | 模块 | 名称 | Node d.ts | HOST-API | facade | Bun | 文档 / d.ts / Ace | 建议归属 |
|---|---|---|---|---|---|---|---|---|
| R1 | (门禁) | 17 个 Codex 结构检查脚本未进 CI | - | - | 本地全部通过 | - | - | P0.6 (api-coverage.yml 加步骤; 需 Node / Bun 仓库 checkout, 与 Q14 同一处) |
| R2 | console | setAvoidStatusBar, setColorful, setInputVisible, setTimeFormat, setTimeVisible (宿主 2026-09-08 85dcf99e3a 已有, 早于 Codex 批次) | 无 | 无 | 无 | 无 (console.control 31 个方法不含) | 有 | P3 / P4 控制台批次 (随 console.control 补 5 个方法) |
| R3 | pinyin | MODE_NORMAL, MODE_PLACENAME, MODE_PLACE_NAME, MODE_SURNAME, STYLE_FIRST_LETTER, STYLE_INITIALS, STYLE_NORMAL, STYLE_TO3NE, STYLE_TONE, STYLE_TONE2 | 有 (数值常量) | - | 有 | 无 (text.pinyin 只有 6 个方法) | 有 | P4 (Bun 本地常量, local-shim) |
| R4 | console | assert, build, error, info, input, log, print, printAllStackTrace, rawInput, time, timeEnd, trace, verbose, warn (14 行) | 有 (autojs6:console) | 未列 | 有 | 无 (Bun 有原生全局 console) | 有 | 附录 D 候选: 输出类 runtime-native (等价: Bun 全局 console), input / rawInput / build sync-only (Node Worker 也显式失败); P1.4 拍板 |
| R5 | (19 个模块) | HOST-API 一至四节状态表未列: ai, android, bridge, compat, compose, console, epub, flow, host, mail, opencc, pinyin, pinyin4j, profile, screen_metrics, screen_recorder, speech, tasks, tts (Codex 各批只在文末追加英文小节) | 有 | 缺表格行 (矩阵按 "未列即 bridged" 推导) | 有 | - | - | P3.6 (HOST-API 与 COMPATIBILITY_PROFILE 同步时补状态行); 矩阵结论不受影响 |
| R6 | compose | ComposeError (类导出) | 无 | 未列 | 无 | 无 | 有 | P3.3 / P4 UI 族 (Node / Bun 的 compose 错误类型; 也可记 local-shim 错误类) |
| R7 | ui | 31 行: layout, layoutFile, setContentView, findById, findByStringId, run, post, isUiThread, finish, R, Widget, registerWidget, emitter, root, useAndroidLayout, keepScreenOn, backgroundColor, statusBar* / navigationBar* 颜色, 高度与图标 (14 行) | 无 (Codex 的可移植 ui 是 JSON 布局契约, 只同名 inflate / findView) | 未列 | 无 | 无 | 有 | 拆两类交 P1.4: 依赖 Rhino UI 线程与 Activity 内容视图的 (layout, layoutFile, setContentView, run, post, isUiThread, finish, R, Widget, registerWidget, emitter, root, useAndroidLayout, findById, findByStringId) 附录 D 候选 ui-thread; 只读系统栏高度 / 颜色与 keepScreenOn 等可桥接的归 P3.3 / P4 UI 族 |

## 3. 已直接修

| 提交 | 仓库 | 内容 |
|---|---|---|
| 6260c9788 | 宿主 | `check-runtime-tts.mjs` 断言改为 `session.pending.values.concurrentSnapshot()` (C36 后的源码), CI 中该步骤恢复通过 |

## 4. 超出分母的运行时扩展

运行时插件提供而宿主没有对应显式导出的入口, 矩阵无法也不需要为其设行 (分母是宿主导出, D1); 文档在 `runtimeApis.md` 与插件文档. 如需纳入 (例如 @ScriptPrototype 的 media / recorder) 见 Q18.

- 录屏: Node `screen_recorder` / `autojs6:screen_recorder` (start / stop / cancel / getStatus, main + Worker), Bun `screenRecorder` (screen.record), Worker `media_projection` / `recorder` (runtime-node-recording, runtime-video-worker).
- UI 扩展: ui.context / createView / view / bind / state / showLayout, compose.AndroidView / attach, 12 个 XML 控件与 58 + 11 个 View 属性契约, WebView / Drawer / Toolbar / ViewPager 组件命令, 40 个 WebView 回调 (runtime-ui-interop, runtime-xml-views, runtime-view-components, runtime-webview-callbacks).
- 录屏音频: Bun screen.record.microphone / screen.record.playback (runtime-audio-capture).

## 5. 报告边界摘录 (附录 D 对照用, 逐组累积)

每条: 报告 | 报告写明的边界 | 附录 D 草案现状 (来源 1 / 2 / 3 或 "无") | 处理.

| 报告 | 边界 | 附录 D 现状 | 处理 |
|---|---|---|---|
| runtime-api-coverage (总审计) | Java 对象 / 原型行为与未写明的类型转换不在计数内; Node / Bun 补全排除 Rhino 全局与原型扩展 | 来源 2 (任意 Java 对象 / 原型; JSOX 原型扩展) | 已覆盖 |
| runtime-image-parity | Java Bitmap / Mat 方法, 任意 Java 枚举, Rhino 类型转换与按比例区域重载不随 81 函数计数 | 来源 2 (任意 Java 对象 / 原型) 只覆盖前两项 | 重载 / 类型转换属签名语义, 不是排除项 (矩阵按名称); 补进草案作说明 "Codex 报告来源" |
| runtime-video-worker | Worker 中 Rhino console input / rawInput / build 显式失败 | 无 | 补进草案: console.input / rawInput / build, sync-only (R4) |
| runtime-ui-compose, runtime-ui-interop | Java View / Context 对象, 任意 Java 类构造, E4X 语法, 原生属性代理, 原生悬浮窗调整外框 | 来源 2 (任意 Java 对象 / 原型) 部分; E4X / 属性代理 / 悬浮窗外框无 | 补进草案: E4X 语法 rhino-only; 原生属性代理 java-prototype; 悬浮窗调整外框 ui-thread |
| runtime-ui-interop, runtime-xml-views, runtime-view-components | XML 词汇只是受支持控件集, 不是完整 Rhino XML inflater 与表达式绑定 | 来源 2 (完整 Rhino XML 控件与表达式绑定面) | 已覆盖; 契约数字更新为 12 控件 + 58 可写 / 11 只读属性 + 12 组件命令 |
| runtime-view-components, runtime-webview-callbacks | 自定义同步 WebViewClient / WebChromeClient 回调与任意 Java 子类化, 原始 View / Bitmap 返回值, 自动 Activity ActionBar 集成, 已弃用的 database / quota 钩子 | 无 | 补进草案: 同步 WebView 客户端子类化 sync-only / java-prototype; ActionBar 自动集成 ui-thread; 弃用钩子不进矩阵 (宿主无导出) |
