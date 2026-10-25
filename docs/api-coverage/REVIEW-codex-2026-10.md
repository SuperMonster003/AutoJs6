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

### 第 3 组 (附录 A 第 13-18 份)

| 报告 | 宣称 | 矩阵 (行: Node main / Worker / Bun 覆盖) | 文档 / d.ts / Ace | 结论 |
|---|---|---|---|---|
| runtime-webview-files-2026-10-18 | WebView 相机全尺寸结果, SAF 保存 / 目录, 文档读取, blob / data 下载 | 无矩阵行 | - | 超出分母 |
| runtime-webview-media-2026-10-18 | WebView 系统录像与前台 AAC 录音 | 无矩阵行 | - | 超出分母 |
| runtime-webview-streams-2026-10-18 | 页面 Promise 求值, getUserMedia / MediaRecorder 分片传输 | 无矩阵行 | - | 超出分母 |
| runtime-webview-display-2026-10-18 | getDisplayMedia 适配, 前台服务网页音频, Camera2 后台录像, View 事件订阅 | 无矩阵行 | - | 超出分母 |
| bun-automation-2026-10-19 | Bun host.accessibility 16 个原生操作 + 16 个本地选择器构造, host.app 13 条路由 + Node 本地 Intent 助手, host.clipboard getText / setText / hasText, 4 个授权 | automator 54: 13 / 0 / 7; 全局无障碍 7 行三列 (Worker 除外) 覆盖; app 43: 38 / 0 / 38 (app.control); auto 37: 0 / 0 / 0 (1 行 denied) | 全部有标题 / 声明 / 补全 (automator.headsethook 只有提及) | 有差异: R8 (Worker), R9 (auto / automator), R10 (app 5 行), R12 (Bun getClip / setClip 别名), R13 (显式拒绝的入口记为覆盖) |
| bun-storage-2026-10-19 | host.files 15 个 Node files 名称 (12 异步 + 3 同步), host.storage / storages 6 条路由 | files 21 (宿主显式导出只有 join / open / path / toFile 与 17 个 batchRename; 其余 files.* 在 @ScriptPrototype 原型上, 不在分母, 见 Q18): 4 / 0 / 4; storages 5: 5 / 0 / 5 | 全部有 | 有差异: R8 (Worker), R11 (files.batchRename 17 行), R13 (files.open / toFile, storages.names / all 只显式拒绝) |

### 第 4 组 (附录 A 第 19-24 份)

| 报告 | 宣称 | 矩阵 (行: Node main / Worker / Bun 覆盖) | 文档 / d.ts / Ace | 结论 |
|---|---|---|---|---|
| bun-database-2026-10-19 | Bun database.open + 可调用 sqlite / sqlite.open, Worker 导入 database / sqlite, 7 条原生路由 (open / exec / run / get / all / transaction / close) | sqlite 1: 1 / 1 / 1 (database.control); 宿主 database 模块成员在 @ScriptPrototype 上 (Q18); Database# 51 成员按 Q17 记 java-prototype (运行时给的是 7 方法的可移植句柄) | 有 | 一致 |
| bun-sensors-2026-10-19 | 共享 sensors 工厂 (getAvailableSensors / once / subscribe / policy), Bun sensors.control, Worker 导入 | 无显式导出行 (sensors 在原型上, Q18) | - | 一致 (超出显式分母); 报告记录的 5 个 shell 声明缺口已由 shell-api-coverage 关闭 |
| shell-api-coverage-2026-10-19 | shell.checkAccess / isRootAvailable / setDefaultOptions / spawn, shizuku.spawn 的声明与补全; Shell.Process 25 个成员 | 5 个名称 doc / d.ts / Ace 全有; ShellProcess# 25 成员 d.ts 25, Ace 25, 文档标题 12 (提及 13) | 见左 | 一致 (Rhino 面); ShellProcess# 文档 13 行只有提及归 P2.3 (BASELINE 第 3 节已计) |
| runtime-shell-2026-10-19 | Node / Bun / Worker 共享 shell 工厂: exec / execRoot / execShizuku / execFile / open / spawn / 流 / waitFor / kill / close, checkAccess, getAccess, requestShizukuPermission; Bun shell.control / shell.root / shell.shizuku | shell 11: 2 / 2 / 2 (checkAccess, spawn); shizuku 8: 1 / 1 / 1; exec 系列在 Shell 原型上 (不在显式分母); ShellProcess# 25 成员三列 pending (augment-prototype) | 有 | 有差异: R14 (shell / shizuku 16 行), ShellProcess# 归 P3 / P4 句柄补缺 (Q17 (a)) |
| runtime-events-2026-10-19 | Bun host.events (observeBroadcasts / Notification / Toast / Key) 与 host.notifications (notify / cancel / cancelAll / getPermissionStatus / openSettings / openNotificationSettings), 6 个授权, Worker 同 | events 只有 internal __asEmitter__ 1 行 (其余在原型上); notice 13: 1 / 1 / 0 | notice 13 / 13 / 13 | 有差异: R15 (Bun notice 别名), R16 (notice 12 行) |
| runtime-device-dialogs-2026-10-19 | Bun host.device (device.control / device.power / device.audio) 与 host.dialogs (alert / confirm / input / rawInput / select / singleChoice / multiChoice / progress) | device 25: 4 / 4 / 1; dialogs 9: 8 / 8 / 8 (dialogs.control); 全局 alert / confirm 等 4 行覆盖 | 全部有 | 有差异: R17 (Bun device.density / height / width 别名), R18 (device 21 行), dialogs.build 归附录 D 候选 |

### 第 5 组 (附录 A 第 25-30 份)

| 报告 | 宣称 | 矩阵 (行: Node main / Worker / Bun 覆盖) | 文档 / d.ts / Ace | 结论 |
|---|---|---|---|---|
| runtime-android-tasks-2026-10-19 | Paint / ColorStateList / 主题色 / URI 原生句柄 (10 条路由), TimedTask / IntentTask 持久任务 (10 条路由), Bun 拿到 Node 的纯颜色转换 facade, android.objects / tasks.control | tasks 20: 20 / 20 / 20 (tasks.control); colors 90: 62 / 62 / 62 (local-shim), 28 pending; TimedTask# / IntentTask# / ThemeColor# 按 Q17 记 java-prototype (运行时返回只读快照) | tasks 20 / 20 / 20 (d.ts arity 11, name 9); colors 90 / 90 / 90 | 有差异: R21 (colors 28 行, 宿主 2026-07-27 / 07-28 已有) |
| runtime-java-interop-2026-10-20 | java / autojs6:java 与 Bun host.java (java.interop): 类描述, 构造, 静态 / 实例调用, 字段, 数组, Context, 引用释放 (28 条路由, 31 个共享成员) | 运行时 java 模块不对应宿主显式导出 (宿主 Java 访问是 Rhino 语言层); util.java 6: 0 / 0 / 0; isJavaObject / isJavaClass / isJavaPackage / unwrapJavaObject 已按附录 D 来源 2 排除 | util.java 6 / 6 / 6 | 有差异: R19 (util.java 6 行) |
| runtime-java-callbacks-2026-10-20 | proxy / wrap, 生成子类, callSuper, 回调参数保留, loadJar / loadDex | 无显式导出行 | - | 超出分母 |
| runtime-tts-2026-10-20 | 31 个 TTS 方法名全部有对应 | tts 35 (31 函数 + QUEUE_ADD / QUEUE_FLUSH / maxInputLength / maxSynthesisFileSize): 35 / 35 / 35 (tts.control); TtsUtterance# 按 Q17 记 java-prototype | doc 标题 28 + 提及 7 (config, enqueueTask, speakTask, stopAll, synthesisTask, synthesizeTask, synthesizeToFile, 已在 P2.1 的 tts 7 项); d.ts / Ace 35 | 一致; 静态检查脚本断言过期已修 (6260c9788) |
| runtime-speech-2026-10-20 | speech / models 18 个方法名 | speech 11 (10 函数 + SpeechError) + speech.models 8 = 19 行: 19 / 19 / 19 (speech.control) | 19 / 19 / 19 | 一致 |
| runtime-epub-2026-10-21 | 20 个根方法, 书籍操作与阅读会话 | epub 21 (20 函数 + EpubError): 21 / 21 / 21 (epub.control); EpubBook# 14 成员 pending (augment-prototype, 运行时已有书籍句柄), EpubReaderSession# 35 按 Q17 记 java-prototype | 21 / 21 / 21 | 一致 (根名称); 句柄成员见 R20 |

### 第 6 组 (附录 A 第 31-36 份)

| 报告 | 宣称 | 矩阵 (行: Node main / Worker / Bun 覆盖) | 文档 / d.ts / Ace | 结论 |
|---|---|---|---|---|
| runtime-mail-2026-10-21 | 52 个根方法名 + providers / accounts 的 5 个嵌套名称 (共 57) | mail 56 (52 函数 + MailError / accounts / default / providers): 56 / 56 / 56 (mail.control); 5 个嵌套名称在嵌套对象上, 不在分母; MailClient# 53 成员 pending (augment-prototype, 运行时有 client 句柄) | doc 标题 9 + 提及 47 (P2.1 的 mail 47 项); d.ts / Ace 56 | 一致 (根名称); 嵌套名称见 R22, 句柄见 R20 |
| runtime-flow-core-2026-10-21 | 52 个根名称中的 22 个 + Flow 构造器 / FlowError / 状态与核心链 | 已被 flow-tools 补齐, 见下 | - | 一致 (中间批次) |
| runtime-flow-tools-2026-10-21 | 补齐其余 30 个根名称, 达到 52; 25 个节点动作链, Toolkit 原生算法, 事件等待, EventFlow.ready | flow 55 (52 函数 + Flow / pending / workers): 55 / 55 / 55 (flow.control) | doc 标题 30 + 提及 25 (P2.1 的 flow 25 项); d.ts / Ace 55 | 一致; automator 与全局的同名 Toolkit 助手见 Q28 |
| runtime-flow-scope-2026-10-21 | within / scope, 继承根, compass / 结果投影, 节点数组动作, press / swipe / gesture / gestures 实例重载; 根名称仍为 52, 原型名 +6 | Flow# 106 成员 pending (augment-prototype; 运行时链对象已有这些方法) | Flow# d.ts 106, Ace 106, 文档标题 25 + 提及 79 | 一致 (根名称); Flow# 见 R20 |
| runtime-ai-core-2026-10-22 | 5 个 AI core 根名称 (ask / chat / stream / session / catalog), 会话 4 方法, 3 个生命周期助手 | ai 6 (5 函数 + agent): 6 / 6 / 6 (ai.control); AiSession# 11 成员 pending, 且宿主 d.ts / Ace 无 (BASELINE 第 3 节) | ai 6 / 6 / 6 | 一致 (根名称); AiSession# 见 R20 |
| runtime-ai-agent-2026-10-22 | ai.agent 9 个根 (run / create / get / list / catalog / presets / status / context / result), AgentRun 7 方法, 3 个助手; Node ai.agent 权限, Bun agent.control | ai.agent 的 9 个根在嵌套对象上, 不在分母; AgentRun# 14 成员 pending | ai.agent 已计入 ai 6 | 一致 (矩阵口径); 嵌套根见 R22, AgentRun# 见 R20 |

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
| R8 | app, files, storages, automator / accessibility, keys, clipboard | Node Worker 列: app 43, files 21, storages 5, automator 54 与对应全局 (click / back / home / getClip / setClip ...) 全部 pending | main 有 | - | Worker hostModuleMethods 无 accessibility / app / clipboard / files / storage(s) / keys | - | - | P3 (Worker 补缺批次, 随 P3.1 / P3.2 / P3.4 各族; Codex 两份 Bun 报告未宣称 Worker, 不算报告错误) |
| R9 | auto, automator | auto 37 行 (服务管理 start / stop / state / windows / registerEvent ...) 三列 0 覆盖 (auto.waitFor 已 denied); automator 41 行 (clickAny / scrollUntil / waitForToast / smartClick / switchToInputMethod ...) Node / Bun pending | 无 (Node accessibility 名称不同: isEnabled / ensureEnabled) | 未列 | 无 | 无 | 有 | P3.4 / P4.4 自动化族; 能按语义对应的 (例如 auto.isRunning -> accessibility.isEnabled) 在 P3.4 前由 runtime-policy.json memberAliases 逐条核对, 不在本条目猜测; automator 41 行中 19 个名称 (clickAny, scrollUntil, waitForToast, smartClick, typeInto ...; 连同全局放置共 38 行) 与 flow 同名且共用 Toolkit 实现, 见 Q28 |
| R10 | app | autojs, getApkInfo, getAppByAlias, getInstalledPackages, sendLocalBroadcastSync | 无 | 未列 | 无 | 无 (app.control 13 条路由不含) | 有 | P3.1 / P4.1 系统族 (sendLocalBroadcastSync 在 Bun tasks.control 有同名路由, P4 时核对能否别名) |
| R11 | files.batchRename | 17 行 (apply / plan / preview / undo / history / presets 及 Async 版本, BatchRenameError) | 无 | 未列 | 无 | 无 | 有 | P3.2 / P4.2 文件族 (依赖 Batch Rename 插件, 纳入方式参考 P5) |
| R12 | (全局) | getClip, setClip: Bun 有 clipboard.getText / setText 但矩阵 pending (名称不同, 缺 memberAliases) | 经 autojs6:compat 覆盖 | - | - | 有同义入口 | 有 | 直接修: runtime-policy.json bun.memberAliases (见第 3 节) |
| R13 | app, files, storages, console, s13n | 声明只返回 never / Promise<never> 的显式拒绝入口被矩阵记为覆盖: Node main 约 37 行 (app 13 个 Dual / kill 系列及全局放置, console.input / rawInput / build, files.open / toFile, s13n.color / point / throwable / time, storages.names / all), Bun 约 27 行 (app Dual 系列, files.open / toFile, storages.names / all) | 有 (返回 never) | 未列 | 有 (抛错) | 有 (返回 never) | 有 | 待决 Q26 (生成器识别 never 返回, 记为待排除而非覆盖); 涉及行在 P1.4 归入白名单分组 |
| R14 | shell, shizuku | shell: currentActivity, currentComponent, currentPackage, execCommand, fromIntent, getCommand, isRootAvailable, kill, setDefaultOptions; shizuku: currentActivity, currentComponent, currentPackage, execCommand, getCommand, kill, state (宿主均早于 Codex 批次, setDefaultOptions 为 2026-10-19) | 无 | 未列 | 无 | 无 | 有 | P3.1 / P4.1 (shell 族): isRootAvailable / setDefaultOptions / state / current* 可桥接; fromIntent 参数是 Java Intent, 附录 D 候选 java-prototype |
| R15 | notice | Bun 有 notifications 命名空间 (notify / cancel / cancelAll ...), runtime-policy.json bun.namespaceAliases 缺 notice -> notifications (Node 已有 moduleAliases), notice.cancel 记 pending | 有 | - | - | 有同名入口 | 有 | 直接修: bun.namespaceAliases (见第 3 节) |
| R16 | notice | builder, getBuilder, config, ensureEnabled, isEnabled, launchSettings, channel.contains / create / createIfNeeded / get / getAll / remove (12 行) | 无 (notifications 只有 notify / cancel / cancelAll / getPermissionStatus / openSettings / openNotificationSettings) | 未列 | 无 | 无 | 有 | P3.1 / P4.1 (通知渠道与权限; isEnabled / launchSettings 与 getPermissionStatus / openSettings 语义是否一致在 P3.1 核对后再定别名); builder / getBuilder 返回 Java 构造器, 附录 D 候选 java-prototype |
| R17 | device | density, height, width: Bun 用异步 getDensity / getHeight / getWidth (报告写明), 缺 bun.memberAliases | 有 | - | - | 有同义入口 | 有 | 直接修: bun.memberAliases (见第 3 节) |
| R18 | device | digest, getGatewayAddress, getIpAddress, getIpv6Address, getSharedDeviceId, isActiveNetworkMetered, isConnectedOrConnecting, isWifiAvailable, isScreenLandscape / Portrait / Off, orientation, rotation, pageSize, summary, isPointerLocationEnabled / Disabled, setPointerLocation / Enabled / Disabled, togglePointerLocation (21 行) | 无 | 未列 | 无 | 无 | 有 | P3.1 / P4.1 系统与设备族; getSharedDeviceId 等标识符类按附录 D 来源 1 (硬件标识符, security) 在 P1.4 判定 |
| R19 | util.java | array, instanceOf, instanceof, mapToObject, objectToMap, toJsArray | 无 (运行时 java 模块有 newArray / instanceOf / toArray, 名称不同) | 未列 | 无 | 无 (host.java 同上) | 有 | P3.2 / P4.2: instanceOf / instanceof / array / toJsArray 核对语义后用 memberAliases 对到 java.instanceOf / newArray / toArray; mapToObject / objectToMap 按 java-prototype 进白名单或 local-shim, P1.4 定 |
| R20 | (返回句柄) | augment-prototype 句柄中运行时已有对应句柄类型的: Flow# 106, MailClient# 53, EpubBook# 14, AiSession# 11, AgentRun# 14, ShellProcess# 25, Storage# 12 (共 235 行) 三列记 pending; runtime-policy.json returnedObjects 为空, list-runtime-surfaces.mjs 不收集句柄类型成员 | 有句柄类型 (如 shell.d.ts ShellProcess, ai.d.ts AgentRun, storage.d.ts StorageStore) | - | 有 | 有 | 见 BASELINE 第 3 节 | 待决 Q27 (生成器按句柄类型比对成员); 未先行, Q17 (a) 的 "P3 / P4 补" 口径下这些行会被重复实现 |
| R21 | colors, colors.theme | android, blend, contrast, css, derive, distance, ensureContrast, extract, harmony, invert, material, nightVariant, oklab, oklch, simulate, toOklab, toOklch, tones, web, theme.activate / current / deactivate / get / libraries / next / reset / set / state (28 行, 宿主 2026-07-27 / 07-28 已有, 早于 Codex 批次) | 无 | 列为 local-shim (只有旧 62 个) | 无 | 无 (Bun colors 同步自 Node, 同缺) | 有 | P3.2 / P4.2: 纯颜色函数 local-shim (需与 Rhino 行为一致的测试, Q5), colors.theme.* 依赖宿主主题状态, 走桥接 |
| R22 | ai.agent, mail.providers / mail.accounts | ai.agent 的 9 个根与 mail 的 5 个嵌套名称不在矩阵分母 (extract_exports 排除嵌套动态对象; returned-objects.json 没有 Agent / 账户对象) | 有 | 有小节 | 有 | 有 | 有 | P2.3 (在 returned-objects.json 登记 ai.agent 与 mail.providers / accounts 对象, 使其进入分母); 运行时已实现, 登记后预计直接覆盖 |

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
| bun-automation | 坐标点击沿用 Node provider 限制 (改用单点 gesture); Java 对象, 特权 / 双开操作显式拒绝 | 来源 1 (原始输入通道) 不含双开 | 补进草案: app 双开系列 (isDualInstalled / launchDual* / killDual / uninstallDual ...) 与 kill, 理由 security (需 Root / Shizuku 特权), 等价: 无; 与 R13 / Q26 同组 |
| bun-storage | files.open / toFile 拒绝 Rhino / Java 句柄; storages.names / all 显式不支持; 单次读写受 64 KiB / 256 KiB 预算 | 无 | 补进草案: files.open / toFile java-prototype (等价: files.read / write 与运行时 fs), storages.names / all security (跨脚本枚举存储, 待 P1.4 定理由) |
| bun-database | 命名数据库而非 Java 对象 / 游标; JSON 原始值绑定参数; 每执行 4 个句柄等预算 | 来源 2 (任意 Java 对象) | 已覆盖 (Database# 按 Q17 记 java-prototype) |
| bun-sensors | 完整 Rhino SensorManager 类型面与 Java 对象不在可移植 API | 来源 2 | 已覆盖 (sensors 在原型上, Q18) |
| runtime-shell | Java 流 / TermSession 不跨桥; Root 授权与守护进程受 Android 约束 | 来源 2 / 来源 1 (原始输入通道) 不含 | 补进草案: Shell 的 Java 流与 TermSession 对象 java-prototype (等价: 运行时可读 / 可写流); shell.fromIntent java-prototype |
| runtime-events | 任意广播, 完整 Rhino EventEmitter 扩展, Java 通知对象 | 来源 2 (events 的同步 loop) 部分 | 补进草案: Rhino EventEmitter 扩展 rhino-only (等价: node:events / 订阅句柄 on / once / off); notice.builder / getBuilder java-prototype |
| runtime-device-dialogs | 不实现所有 Rhino / Java 设备助手与完整 Rhino 对话框构造器对象模型 | 无 | 补进草案: dialogs.build 与 Dialog# 构造器模型 java-prototype (等价: dialogs.alert / select / progress 等); 设备助手逐条见 R18 |
| runtime-android-tasks | 原生对象句柄只在本执行有效; 任务结果是只读快照 | 无 | 不是排除项 (TimedTask# / IntentTask# 已按 Q17 记 java-prototype) |
| runtime-java-interop, runtime-java-callbacks, 总审计 | 同步 Rhino 包全局 (Packages 等), 函数自动转接口, AAR 资源, JNI 部署不支持 | 来源 2 (任意 Java 对象 / 原型) 不含包全局 | 补进草案: Rhino 包全局与函数自动转接口 rhino-only (等价: java.type / java.proxy); 宿主无对应显式导出, 只作说明 |
| runtime-tts, runtime-speech, runtime-epub | 依赖已安装引擎 / 模型 / 插件, 不做常驻前台服务; 不声称 Rhino 原型等价 | 无 | 不是排除项 (运行条件) |
| runtime-flow-core / tools / scope | 同步 Rhino 终端, 注入的全局别名, 实时 Java UiObject 行为不在可移植契约 | 来源 2 (同步 Rhino 终端; 任意 Java 对象) | 已覆盖 (UiObject# 按 Q17 记 java-prototype); 全局别名 (clickAny 等) 见 Q28 |
| runtime-ai-core, runtime-ai-agent | 不模拟 Rhino 同步行为; 不承诺任意 JS / Java 对象或原型等价 | 来源 2 | 已覆盖 |
| runtime-mail | 已保存账户只在原签名入口, 打包应用只用内联凭据 | 无 | 不是排除项 (运行条件) |
| runtime-view-components, runtime-webview-callbacks | 自定义同步 WebViewClient / WebChromeClient 回调与任意 Java 子类化, 原始 View / Bitmap 返回值, 自动 Activity ActionBar 集成, 已弃用的 database / quota 钩子 | 无 | 补进草案: 同步 WebView 客户端子类化 sync-only / java-prototype; ActionBar 自动集成 ui-thread; 弃用钩子不进矩阵 (宿主无导出) |
- WebView 媒体与文件: 相机全尺寸, SAF 保存 / 目录, blob / data 下载, 系统录像 / 前台录音, getUserMedia / MediaRecorder, getDisplayMedia 适配, Camera2 后台录像 (runtime-webview-files / media / streams / display).
- Java 互操作: Node java / autojs6:java 与 Bun host.java 的类描述, 构造, 调用, 字段, 数组, proxy / wrap, 子类生成, loadJar / loadDex (runtime-java-interop, runtime-java-callbacks); 宿主侧是 Rhino 语言层的 Java 访问, 没有显式导出.
