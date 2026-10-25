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
| 5d6c2aa91 | 宿主 | R12 / R15 / R17: runtime-policy.json `bun.memberAliases` (getClip -> clipboard.getText, setClip -> clipboard.setText, device.density / height / width -> device.getDensity / getHeight / getWidth), `bun.namespaceAliases` notice -> notifications, `bun.memberCapabilities` 补 3 个 device 读取的能力名; MATRIX.md 重新生成, Bun pending 948 -> 942 |
| 2cad31444 | 宿主 | (本条目第一步) Q17 java-prototype-objects 规则与 Q12 的 28 行 Bun 能力名, 缺能力名成为 `check.py --matrix` 失败项 |

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

## 6. 附录 D 草案核对版 (按理由分类, 供 P1.4 整理成选择题; 不代替维护者拍板, D12 / Q1)

行数按当前矩阵 (2727 行, 宿主 5d6c2aa91) 的显式导出行计; "现状" 是三列 Node main / Node Worker / Bun 的当前状态. "已在规则" = runtime-policy.json 已有规则 (草案, 待 Q1); "候选" = 本次核对新增, 标 "Codex 报告来源" 的是报告写明的边界 (已补进 ROADMAP 附录 D 来源 4).

| 分组 | 理由 | 条目 (代表项) | 行数 | 现状 | 来源 |
|---|---|---|---:|---|---|
| D-1 无障碍同步与原始节点 | denied: sync-only / security | auto.waitFor (powerDialog 经 keys 覆盖, rawNode 无显式导出) | 1 | 已在规则 accessibility-sync-wait | 附录 D 来源 1 |
| D-2 硬件标识符 | security | device 的 IMEI / 序列号 / MAC 类; 本次核对: device.getSharedDeviceId 待 P3.1 判定 | 0 (+1 待定) | 无规则 | 来源 1 / R18 |
| D-3 原始输入通道 | security | RootAutomator / Shizuku sendEvent 类 (宿主无显式导出行) | 0 | 说明项 | 来源 1 |
| D-4 特权与双开应用操作 | security (需 Root / Shizuku) | app.isDualInstalled / launchDual* / openDual* / startDualActivity / uninstallDual / killDual / kill 及其全局放置 | 25 | Node main bridged 25 / Worker pending 25 / Bun capability-gated 25, 但运行时声明只返回 never (显式拒绝) | 候选, Codex 报告来源 (bun-automation); 与 Q26 同组 |
| D-5 存储枚举 | security (待 P1.4 定理由) | storages.names, storages.all | 2 | Node / Bun 记覆盖但显式不支持 | 候选, Codex 报告来源 (bun-storage); Q26 |
| D-6 同步线程与延续 | sync-only | threads.*, sync, continuation.* | 7 | 已在规则 sync-threads (5), sync-continuation (2) | 来源 2 |
| D-7 控制台同步输入 | sync-only | console.input, console.rawInput, console.build | 3 | Node 记 bridged (声明返回 never), Bun pending | 候选, Codex 报告来源 (runtime-video-worker); Q26 |
| D-8 sleep 与保活 | runtime-native | sleep, keepAlive, loop, timers.keepAlive | 4 | 已在规则 sync-sleep (Node main 经 compat 覆盖, shadowed 1), sync-keep-alive (3) | 来源 2 |
| D-9 运行时自带网络 / 加密 / 编码 / 定时器 | runtime-native | http.* (18 / 18 / 20), crypto.* (2), base64.* (Worker / Bun 2), Bun 全局 setTimeout 等 (6); Node nativeModules (timers / http / util ...) 自动判定 | 28+ | 已在规则 native-http / native-crypto / native-base64 / native-timers | 来源 3; http 的宿主 cookie / 代理逐函数复核留 P1.4 |
| D-10 Bun 控制台输出 | runtime-native | console.assert / error / info / log / print / printAllStackTrace / time / timeEnd / trace / verbose / warn (等价: Bun 全局 console) | 11 | Bun pending (Node 覆盖) | 候选 (本次核对, R4) |
| D-11 Java 对象与原型 | java-prototype | unwrapJavaObject, isJavaObject / isJavaClass / isJavaPackage (5); kotlin-class 返回对象成员 (UiObject / ImageWrapper / Dialog / Database / TimedTask ... 32 个对象 1069 成员, Q17 (a) 已拍板) | 5 + 1069 | 已在规则 java-prototype, java-prototype-objects | 来源 2 / Q17 |
| D-12 Java 句柄与构造器 | java-prototype | files.open / toFile / 全局 open (3, 声明返回 never), s13n.color / point / throwable / time (4, 声明返回 never), dialogs.build, notice.builder / getBuilder, shell.fromIntent, util.java.mapToObject / objectToMap (6) | 13 | files / s13n 在 Node 记 local-shim 覆盖, 其余 pending | 候选, Codex 报告来源 (bun-storage, runtime-device-dialogs, runtime-events, runtime-shell); Q26 |
| D-13 JSOX 扩展 | rhino-only | Arrayx / Mathx / Numberx / jsox.* | 54 (Worker / Bun) | 已在规则 jsox-extensions (Node main 有显式 jsox 函数, shadowed 54) | 来源 2 |
| D-14 Rhino 引擎操作 | rhino-only | engines.execScript / execScriptFile / execAutoFile, plugins.load | 4 | 已在规则 rhino-engine (Node main 1, shadowed 3) | 来源 3 |
| D-15 内部 / 标准导出 | rhino-only | __autojs6Tsx, ui.__inflate__, http.__okhttp__, toString 等 | 9 | 已在规则 internal-exports (Q13 (a) 已拍板) | policy.json |
| D-16 Rhino 语言层 | rhino-only | E4X 语法, Rhino 包全局 (Packages) 与函数自动转接口, Rhino EventEmitter 扩展 | 0 (宿主无显式导出, 说明项) | - | 候选说明项, Codex 报告来源 (runtime-ui-interop, runtime-java-interop / callbacks, runtime-events) |
| D-17 UI 线程与 Activity 内容视图 | ui-thread | ui.layout / layoutFile / setContentView / run / post / isUiThread / finish / R / Widget / registerWidget / emitter / root / useAndroidLayout / findById / findByStringId | 15 | 三列 pending | 候选 (本次核对, R7) |
| D-18 完整 Rhino XML 与表达式绑定, 悬浮窗调整外框, ActionBar 自动集成, 同步 WebView 客户端子类化 | ui-thread / sync-only | 宿主无显式导出行, 说明项; 可移植契约现为 12 控件 + 58 可写 / 11 只读属性 + 12 组件命令 + 40 个 WebView 回调 | 0 | - | 来源 2 + 候选说明项, Codex 报告来源 (runtime-ui-*, runtime-xml-views, runtime-view-components, runtime-webview-callbacks) |
| D-19 Node 可遮蔽 shim | runtime-builtin | BUILTIN_DISABLED 的 6 个 npm shim (宿主无对应显式导出) | 0 | 说明项 | 来源 1 |
| D-20 非本主题 | - | Bun 32 位 ABI, native addon, 子进程沙箱 | 0 | 不进矩阵 | 来源 3 |

合计: 已在规则的显式导出行约 120 (另 1069 个返回对象成员); 候选新增约 70 行 (D-4 25, D-5 2, D-7 3, D-10 11, D-12 13, D-17 15, D-2 1 待定), 其中 D-4 / D-5 / D-7 与 D-12 的 files / s13n 共 37 行目前被记为覆盖, 处理方式取决于 Q26.

## 7. 矩阵 pending 分布 (初稿, P1.4 定稿; 作 P3 / P4 分批依据)

总数 (2727 行): Node main 844, Node Worker 999, Bun 942. 下表按模块 (返回对象以 `#` 结尾) 列 Node main / Node Worker / Bun 的 pending 行数; 未列的 34 个小模块 (memory, zip, engines, ocr.*, plugins, sysprops, settings, web, util.* 子模块等) 合计 Node main 76 / Worker 123 / Bun 114.

| 模块 | Node main | Node Worker | Bun | 对应条目 |
|---|---:|---:|---:|---|
| (global) | 99 | 144 | 116 | 拆入各族 (RootAutomator 简写 Back / Home / Tap ..., species is*, 全局 Toolkit 助手 Q28) |
| Flow# | 106 | 106 | 106 | R20 / Q27 (运行时已有链对象) |
| MailClient# | 53 | 53 | 53 | R20 / Q27 |
| util | 49 | 49 | 50 | P3.2 / P4.2 |
| automator | 41 | 54 | 47 | R9 / Q28, P3.4 / P4.4 |
| species | 37 | 37 | 37 | P3.2 / P4.2 (预期 local-shim, Q5) |
| auto | 36 | 36 | 36 | R9, P3.4 / P4.4 |
| ui | 31 | 31 | 31 | R7, D-17 / P3.3 / P4.3 |
| Color# | 29 | 29 | 29 | P3.2 / P4.2 |
| ShellProcess# | 25 | 25 | 25 | R20 / Q27 |
| Zip# | 21 | 21 | 21 | P3.2 / P4.2 |
| autojs (+ autojs.version 7) | 28 | 28 | 28 | P3.1 / P4.1 |
| device | 21 | 21 | 21 | R18, P3.1 / P4.1 |
| terminal | 20 | 20 | 20 | P3.3 / P4.3 |
| colors (+ colors.theme 9) | 28 | 28 | 28 | R21, P3.2 / P4.2 |
| app | 5 | 43 | 5 | R10 / R8 |
| files.batchRename | 17 | 17 | 17 | R11 |
| installer | 16 | 16 | 16 | P3.1 / P4.1 |
| AgentRun# / EpubBook# / Storage# / AiSession# | 51 | 51 | 51 | R20 / Q27 |
| gallery / workManager | 22 | 22 | 22 | P3.2 / P3.1 |
| console | 5 | 5 | 19 | R2 / R4 |
| shell / shizuku | 16 | 16 | 16 | R14 |
| keys | 0 | 12 | 12 | R8 (Worker), P4.4 |
| notice / notice.channel | 12 | 12 | 12 | R16 |
| pinyin | 0 | 0 | 10 | R3 |

## 8. 新待决 (Q26-Q28, 已登记看板与附录 C)

- Q26 声明只返回 never / Promise<never> 的显式拒绝入口怎么记 (R13, 约 Node main 37 / Bun 27 行): (a) list-runtime-surfaces.mjs 标记 rejects, 矩阵记为 pending 并要求排除规则给理由, 由 P1.4 归入 D-4 / D-5 / D-7 / D-12 (推荐) / (b) 保持覆盖, 只在 MATRIX.md 差异行加 rejects 标注. 未先行.
- Q27 运行时已有句柄类型的 augment-prototype 返回对象 (R20, 235 行) 是否由生成器按句柄类型比对成员: (a) 新增条目, list-runtime-surfaces.mjs 收集句柄接口成员, runtime-policy.json returnedObjects 写 "宿主对象 -> 运行时类型" 映射 (推荐) / (b) 保持 pending, 由 P3 / P4 各批人工核对后逐条写 returnedObjects 映射. 未先行.
- Q28 automator 与全局的 Toolkit 助手 (19 个名称, 38 行, 如 clickAny / scrollUntil / waitForToast) 与 flow 同名同实现, Node / Bun 只有 flow.* 异步版本: (a) runtime-policy.json memberAliases 对到 flow.* 计为覆盖 (via member-alias, 文档表注明 "经 flow") (推荐) / (b) 保持 pending, P3.4 / P4.4 在 accessibility / automator 模块补同名入口. 未先行.

## 9. 后续条目映射

| 归属 | 不一致编号 |
|---|---|
| 已直接修 | R12, R15, R17 (5d6c2aa91); TTS 检查脚本 (6260c9788) |
| P0.6 (CI) | R1 |
| P1.4 (白名单与待决) | R4, R7 (ui-thread 部分), R13 (Q26), R20 (Q27), Q28; 第 6 节分组 |
| P2.3 (返回对象与嵌套对象) | R22 |
| P3 / P4 补缺批次 | R2 (控制台), R3 (Bun 拼音常量, P4), R6 (compose.ComposeError, UI 族), R7 (可桥接部分, UI 族), R8 (Worker, 各族), R9 (自动化族), R10 / R14 / R16 / R18 (系统与设备族), R11 / R19 / R21 (文件与数据族) |
| P3.6 (HOST-API 同步) | R5 |
| P1.2 真机抽样 A (Redmi) | 无失败项 (41 个用例全部通过, 见 "P1.2 真机抽样 A (Redmi)" 节); 设备证据补强 R20 / Q27 与 R15; 待决 Q34 (自动点击系统授权界面的用例) |
| P1.5 矩阵修正 (Q26 / Q27 / Q28) | R13 改记 pending (rejects), 分组见 "P1.5 拒绝入口分组" 节, 白名单仍由 P1.4 拍板; R20 按句柄类型逐成员比对 (BASELINE 第 5 节); R9 中 Toolkit 助手 38 行经 flow 计为覆盖 |

## P1.2 真机抽样 A (Redmi)

日期: 2026-10-25. 会话: api-cov-p1-2 (local_a5def8d0). 设备: Redmi 22120RN86C (bek749scrwv4wo8h), Android 13 / API 33, arm64-v8a, 4096 字节页, 系统语言 en-US. 不重跑打包应用 (D11).

方法: `tools/api-coverage/device_sample_a.py` (record / backup / install / run / restore / verify; `list` 打印抽样清单) 逐个运行 Codex 宿主设备套件 (`app/src/androidTest/.../engine/`) 里的单个测试方法, 每行输出通过 / 失败, 耗时与证据文件中的关键数值. 一个用例 (测试方法) 覆盖多个运行时列: main = Node 主线程 (CJS / ESM), worker = Node Worker (CJS / ESM / 嵌套), bun = Bun host client. "项" 指用例实际调用的 API 行 (矩阵 id) 或矩阵分母外的运行时句柄成员 (第 4 节). 选择依据: 第 9 节归 P3 / P4 的不一致所在模块 (R6 compose, R7 ui, R14 shell / shizuku, R16 notice, R20 ShellProcess#) 与各族 bridged 行数最多的模块 (images 82 行三列全覆盖, compose 15, dialogs 8). 系统授权对话框 (MediaProjection, 运行时权限, Shizuku, SAF) 由维护者在手机上确认 (规则 device-testing); Codex 用例中由测试代码自动点击系统对话框的方法不选, 或在维护者事先授权后再跑 (见各族).

### 版本与安装包

| 包 | 抽样前 (2026-10-25 15:2x 记录) | 抽样使用 | 来源 |
|---|---|---|---|
| org.autojs.autojs6 | 5935 (6.8.0) md5 c8207da2f01c5f1bf9c112b5738d0fec (selector P0 10:28 安装的 debug 包) | 5935 (6.8.0) md5 5c28c473251579b38441f9e422e0deb0 | 主工作区 HEAD 98f03b1f7 的 :app:assembleAppDebug; 构建时工作区另有他人未提交的 5 个文件 (.changelog/lang_zh-Hans.json, README.md, .readme/README-zh-Hans.md, assets-app/doc/CHANGELOG*.md), 无代码改动 |
| org.autojs.autojs6.test | md5 ce520c9dc09990b44baf42e049e749d4 | md5 aaed28a1d82c9754926fdf63b1133383 | 同一次构建的 :app:assembleAppDebugAndroidTest |
| ...plugin.nodejs | 291 (1.31.0) md5 a8811d17a2d96bd7cb2c4bb0a103e9a1 | 292 (1.32.0) md5 c240d6cd49d72363fbbdd9cdda790cdf | Node 仓库 HEAD 990f9e35 (APK 由 133812df 构建, 其后只有 docs/nodejs/history 一个文档提交, 工作区干净) |
| ...plugin.bun.runtime | 293 (0.20.0) md5 bbfca1f67936a60a0700ec1a12e216eb | 296 (0.21.0) md5 900edd665ecf82c35db76d25dc603b27 | Bun 仓库 HEAD 3056b94 (APK 构建晚于该提交, 工作区干净) |
| ...plugin.opencv / imagequant / opencc / pinyin / pinyin4j | 23 (1.1.4) 113bf168... / 25 (1.0.3) 91f06810... / 107 (1.5.0) 9538b79a... / 19 (1.0.3) 8ef7f61b... / 26 (1.3.2) b016924d... | 未替换 | 设备原有 |
| moe.shizuku.privileged.api | 1049 (13.5.4) md5 d2db6e7df106b8fd119646374fe9b42c | 未替换 | 设备原有 |

四个包签名证书相同 (SHA-256 31a681fc...), 本地 Node 292 为 debuggable, 恢复时可降级回 291. 记录的设置项: 旋转 (user_rotation 0 / accelerometer_rotation 1), screen_off_timeout, stay_on_while_plugged_in, 亮度与亮度模式, 无障碍服务列表 (含 AutoJs6), 通知使用权列表 (不含 AutoJs6), appops PROJECT_MEDIA (ignore) / SYSTEM_ALERT_WINDOW (allow) / POST_NOTIFICATION (allow), RECORD_AUDIO / CAMERA / POST_NOTIFICATIONS 已授予.

### 图像 (images 含截图)

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| AdvancedImageBridgeDeviceTest#filtersColorsMetricsAndEncodingAcrossAllExecutionModes | main / worker / bun (6 种执行方式) | 通过 | 22.1 | 6 种方式一致: psnr 361.2, ssim 1, mse 0, ncc 1, pointCount 1200, 半透明像素 alpha 保留 |
| ...#featuresMatricesFilesAndUrlsAcrossAllExecutionModes | main / worker / bun (6) | 通过 | 20.8 | SIFT 内点 203, ORB 内点 181 (6 种方式一致), 左上角 (60, 50) 最大误差 0.831 px, 与 Codex Redmi 数值相同 |
| ...#circleDetectionAcrossAllExecutionModes | main / worker / bun (6) | 通过 | 13.4 | 圆心 (79.5, 69.5) 半径 33.5, 6 种方式一致 |
| ...#imageMatAndFeatureHandlesShareTheExistingQuota | main / bun | 通过 | 10.7 | 32 句柄共享配额, 回收后恢复 |
| ...#featureHandlesCrossWorkersButNeverCrossExecutions | main / worker | 通过 | 8.3 | Worker 特征句柄归属执行, 跨执行拒绝 |
| ...#bunFileAndProcessingGrantsCannotBeBypassedByRawImageCalls | bun | 通过 | 7.9 | 关闭 image.files / image.process 后原始调用 NOT_GRANTED |
| OpenccImageBridgeDeviceTest#publicImagesPreservePixelsDimensionsAndOwnership | main (CJS / ESM) / bun | 通过 | 11.9 | 3 种方式 passed |
| ...#binaryTransportJsonBoundsAndExecutionOwnership | main / bun | 通过 | 12.7 | 257x257 大图句柄, 过期句柄拒绝, JSON 有界, 授权拒绝 |
| ...#nodeWorkerCanUseOpenccAndImagesThroughItsDocumentedBridge | worker | 通过 | 9.5 | 像素 0x80ff0000 保留 |
| NodeScreenCaptureInstrumentationTest#captureWithoutConsentReturnsPermissionDeniedAndImageSaveWorks | main | 通过 | 7.1 | 未授权截图返回 permission-denied, saveImage 正常 |
| NodeScreenCaptureInstrumentationTest#approvedCaptureSavesScreenSizedPngAndStops (`-e captureConsent manual -e imageAnalysis true`) | main | 通过 (第 2 次) | 29.9 | 维护者点 Start now; 截图尺寸等于屏幕, clip + findColor 找到已知像素, findImage 模板命中, 停止后再截图被拒. 第 1 次 120 s 超时 (bridge call timed out): 维护者未看到弹框, 未点击; 重跑时记录前台窗口, MediaProjectionPermissionActivity 显示约 15 s 后被点击, 不计为缺陷 |
| AdvancedImageBridgeDeviceTest#asyncCaptureAliasAndOptionsUseTheRealCaptureSession | main / bun | 通过 | 13.9 | requestScreenCaptureAsync 后选项与截图尺寸一致, 竖屏, 停止后选项为 null, MediaProjection 已释放 |
| CaptureWorkerBridgeDeviceTest#workerCjsEsmAndNestedFacadesUseRealProvidersAndBinaryTransport | worker (CJS / ESM / 嵌套) | 通过 | 12.0 | 三种 Worker 真实截图与二进制传输 |
| ...#bunConsentOutlivesOneBridgeCallCapturesBothRotationsAndRequestsAgain | bun | 通过 | 25.2 | 竖屏 720x1650, 横屏 1650x720, 指定尺寸 320x180, 同意耗时 11858 ms, 新执行需重新同意 |

后三条由测试代码自动点击 MediaProjection 的 Start now; 维护者 2026-10-25 对本轮一次性允许 (只针对测试自建画面), 见待决 Q34.

项 (矩阵行, 均为三列 bridged / capability-gated): images.cvtColor, threshold, adaptiveThreshold, bilateralFilter, inRange, getMeanColor, isGrayscale, compress, compressToBytes, downsample, pixel, getSize, detectAndComputeFeatures, matchFeatures, matToImage, load, loadAsync, imread, ObjectFrame, findCircles, readImage, saveImage, clip, rotate, scale, flip, findColor, findMultiColors, findAllPointsForColor, readPixels, toBytes, fromBytes, toBase64, fromBase64, resize, recycle, isRecycled, captureScreen, requestScreenCapture, requestScreenCaptureAsync, getScreenCaptureOptions, stopScreenCapture, findImage; 共 14 个用例 43 项, 全部通过 (Node main / Worker / Bun 三列均有截图实拍).

### UI / Compose 与 View 互操作

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeUiComposeDeviceTest#layoutsBindingsAndClicksAcrossRuntimeModes | main / worker / bun (6) | 通过 | 18.9 | 6 种方式各 1 次真实点击, 窗口关闭 |
| ...#composeClicksTextInputReactiveFramesAndRefsAcrossRuntimeModes | main / worker / bun (6) | 通过 | 18.5 | count 2, 文本编辑 1, compose.version plugin 219 / contract 2 |
| ...#dialogsValidationAndOwnershipRemainExecutionScoped | main / worker / bun (6) | 通过 | 16.0 | 结束后会话数 0 |
| ...#terminatingWorkerClosesItsWindowWithoutEndingMain | main / worker | 通过 | 9.0 | |
| ...#floatingComposeWindowsRenderAndCloseAcrossRuntimeModes | main / worker / bun (6) | 通过 | 18.6 | 悬浮窗点击 1, 关闭 |
| ...#snackbarActionsReachTheCreatingRuntime | main / worker / bun (6) | 通过 | 18.3 | snackbar 动作回到创建方 1 次 |
| RuntimeUiInteropDeviceTest#xmlAttachAndNativeClickAcrossRuntimeModes | main / worker / bun (6) | 通过 | 16.6 | |
| ...#factoriesReplacementAndOwnershipAcrossRuntimeModes | main / worker / bun (6) | 通过 | 19.1 | |
| RuntimeXmlViewDeviceTest#commonXmlWidgetsAndOrderedPropertiesAcrossRuntimes | main / worker / bun (6) | 通过 | 20.1 | 12 种控件, 属性按序, 选中项 4, 宽 480 |
| ...#nativeSelectionAndProgressEventsReachTheCreatingRuntime | main / worker / bun (6) | 通过 | 18.8 | selected / progress / radio / checked 事件均回到创建方 |
| RuntimeViewComponentsDeviceTest#drawerToolbarPagerClicksAndRotationAcrossRuntimes | main / worker / bun (6) | 通过 | 25.3 | 抽屉开 / 关各 1, 菜单 1, 翻页 1, 旋转后离屏页保留 |

项: 矩阵行 compose.isAvailable, version, mount, state, ref, batch, dialog, floaty, attach (compose 15 个 bridged 行中的 9 个), ui.inflate, ui.findView (ui 仅有的 2 个 bridged 行); 运行时扩展 (第 4 节, 分母外) ui.showLayout, ui.createView, ui.bind, ui.state, ui.view, ui.context, compose.Button / Column / Text / TextField / Scaffold / AndroidView, Window#on / close / flush / setPosition / setSize / setTouchable / disableFocus / showSnackbar / findView, View#findView / dispose / on; 共 11 + 9 组, 全部通过. 未抽: compose.createElement / memo / modifier / post / sessions / theme (矩阵 bridged, 本轮用例未直接调用); R6 compose.ComposeError 与 R7 ui 的 31 个 pending 行在运行时没有入口, 无从抽样, 仍归第 9 节原归属.

### WebView (文件 / 下载批)

选 Codex runtime-webview-files 批的下载部分, 加 runtime-view-components 的两个 WebView 页面用例. 同批的 realSaveAndTreeTargetsPreserveExistingContentsAcrossRuntimes (系统文档选择器与目录 "Allow" 授权) 与 realCameraCaptureAndCancellationAcrossRuntimes (相机权限与系统相机) 由测试代码自动操作系统界面, 本轮不抽.

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeWebFilesDeviceTest#dataAndBlobBytesCancellationAndNavigationAcrossRuntimes | main / worker / bun (6) | 通过 | 62.7 | 6 种方式各下载 blob 2097289 字节并逐字节校验, percent / base64 data URL, 撤销的 blob 与非法 data URL 失败, 取消, 导航中断, 源 WebView 释放后仍可读 |
| ...#blobAndDataLinksReachTheDownloadListener | main / worker (嵌套) / bun | 通过 | 16.0 | blob / data 链接各 1 次真实点击 (isTrusted), 下载监听收到并读回 4 字节 |
| ...#pickerModesAndDataDecodingRejectInvalidInputs | (宿主侧) | 通过 | 5.4 | 选择器模式与 data 解码拒绝非法输入 |
| RuntimeViewComponentsDeviceTest#webPagesJavaScriptAndHistoryAcrossRuntimes | main / worker / bun (6) | 通过 | 19.6 | evaluateJavascript 返回 {answer: 42, array}, 页面完成 6 次, 标题 5 次, 历史前进 / 后退正确 |
| ...#webViewDisposalReleasesAttachedAndOffscreenPages | main / worker / bun (6) | 通过 | 6.2 | 已附着与离屏页面释放 |

项 (运行时 WebView 句柄成员, 第 4 节分母外): WebView#loadHtml, loadUrl, evaluateJavascript, download, getDownload, readDownload, cancelDownload, setDownloadListener, goBack, goForward, reload, stopLoading, on, dispose; 共 14 项, 全部通过. 矩阵 web 模块 4 行 (Rhino 的 web 工具函数) 三列 pending, 不在本批范围.

### shell + Shizuku

Shizuku 抽样前未运行: 维护者同意后由本会话经 adb 运行设备上已有的 start.sh (shizuku_server 以 shell 身份运行), 维护者在 Shizuku 中确认 AutoJs6 已授权 (测试的授权框监视器因此无需点击). 抽样后停止服务.

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeShellDeviceTest#bunGrantAndNodePrivilegeDenialsDoNotStartProcesses | main / bun | 通过 | 8.5 | Bun 无 shell.control 时 NOT_GRANTED; Node 未声明 shell.shizuku / root 时 shizuku.exec 与 shell.execRoot 拒绝 |
| ...#mainNodeAndBunStreamOrdinaryAndShizukuProcesses | main / bun, 普通与 Shizuku 各一轮 | 通过 | 32.8 | 普通进程 uid 10778 (= 宿主), Shizuku 进程 uid 2000; 70000 字节二进制 stdin / stdout 一致; 流式两行间隔 373-404 ms (退出前到达); PTY, 超时码 124, AbortSignal, close 均通过 |
| ...#workersCjsEsmAndNestedReleaseShellsWithoutTouchingParents | main / worker (CJS / ESM / 嵌套) | 通过 | 38.3 | 三种 Worker 普通 uid 10778 / Shizuku uid 2000, 流式间隔 365-387 ms; Worker 结束释放自己的进程, 父执行的进程存活 |

项: 矩阵行 shell.spawn, shell.checkAccess (运行时 getAccess) 三列 bridged / capability-gated, 均通过 (shizuku.spawn 未直接调用, 用例经 shell.spawn / exec / open 的 `shizuku: true` 选项走同一 Shizuku 进程路径); 运行时扩展 shell.exec, shell.execFile, shell.open, shizuku.requestPermission, shizuku.exec / shell.execRoot (拒绝路径); 运行时 ShellProcess 句柄成员 write, writeLine, readLine, closeInput, waitFor, close, on (output / errorOutput), ready / exited / closed, stdout / stderr 流; 共 3 个用例 18 项, 全部通过. 与 R20 / Q27 相关: 矩阵 ShellProcess# 25 行三列 pending, 其中 write / writeLine / readLine / closeInput / waitFor / on 在 Node main / Worker / Bun 的运行时句柄上已实测可用 (同名), Rhino 的 kill 对应运行时 close; 支持 Q27 (a) 按句柄类型映射. R14 的 shell / shizuku pending 行 (currentActivity, execCommand, isRootAvailable, setDefaultOptions, state 等) 运行时无入口, 无从抽样.

### 事件 / 通知 (含 device / dialogs)

AutoJs6 的通知使用权抽样前未开启: 维护者在手机上开启, 抽样后关闭. 测试在宿主进程被 instrumentation 重启后重绑已授权的 AutoJs6 无障碍服务与通知监听 (只针对已授权组件), F8 按键经 uinput 测试工具的临时虚拟键盘注入.

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeEventsDeviceTest#missingBunGrantsAndNodeSubGrantsRejectWithoutChangingAndroidSettings | main / bun | 通过 | 8.4 | Bun 无授权时 events / notifications NOT_GRANTED; Node 未声明子能力时 observeNotification / observeToast / observeKey / openSettings 拒绝 |
| ...#mainNodeAndBunObserveRealSourcesAndPostSilentNotifications | main / bun | 通过 | 56.3 | 4 种来源 (通知 id 7, 外部测试包 Toast, F8 keyCode 138, 电量广播 100%) 均收到, 静默通知发出后取消 |
| ...#cjsEsmAndNestedWorkersReleaseOwnedObserversAndNotifications | main / worker (CJS / ESM / 嵌套) | 通过 | 31.7 | 三种 Worker 4 种来源均收到; Worker 结束后其通知被撤销, 父执行的通知与订阅保留 |
| RuntimeDeviceDialogsDeviceTest#nodeMainDeviceAndDialogs | main | 通过 | 16.7 | 720x1650, SDK 33, 12 个对话框交互 (alert / confirm x2 / input / rawInput / prompt / select / singleChoice / multiChoice / progress / abort / timeout), 音量未变, 亮度 permission-denied (无 WRITE_SETTINGS, 未写) |
| ...#nodeWorkerDeviceAndDialogs | worker | 通过 | 15.8 | 同上 |
| ...#bunDeviceAndDialogs | bun | 通过 | 16.6 | 同上 |
| ...#bunMissingGrantsRejectDevicePowerAudioAndDialogs | bun | 通过 | 6.6 | 4 个授权缺失均 NOT_GRANTED, 无对话框残留 |
| ...#esmAndNestedWorkerExitReleaseOwnedWindowsAndLocks | main / worker (3 种) | 通过 | 12.7 | Worker 结束释放自己的对话框与唤醒锁, 父进度框存活 |

项: 矩阵行 dialogs.alert, confirm, input, rawInput, prompt, select, singleChoice, multiChoice (dialogs 8 个 bridged 行全部), device.vibrate, device.width / height / density (经 getWidth / getHeight / getDensity), notice.cancel (经 notifications.cancel, R15 别名) 三列均通过; 运行时扩展 events.observeNotification, observeToast, observeKey, observeBroadcasts, on, once, close, notifications.notify, cancelAll, getPermissionStatus, openNotificationSettings, openSettings (拒绝路径), device.getInfo, keepScreenOn, cancelKeepingAwake, wakeUp, getMusicVolume / setMusicVolume, isIgnoringBatteryOptimizations, openBatteryOptimizationSettings (dryRun), dialogs.progress; 共 8 个用例 36 项, 全部通过. R16 的 notice 12 行与 R18 的 device 21 行 pending 运行时无入口, 无从抽样.

### 汇总, 未抽项与恢复

| 族 | 用例 | 项 | 通过 / 失败 | 列 |
|---|---:|---:|---|---|
| 图像 (含截图) | 14 | 43 | 14 / 0 | main / worker / bun (截图三列均实拍) |
| UI / Compose 与 View 互操作 | 11 | 20 组 (矩阵行 11) | 11 / 0 | main / worker / bun |
| WebView (文件 / 下载批) | 5 | 14 | 5 / 0 | main / worker / bun |
| shell + Shizuku | 3 | 18 | 3 / 0 | main / worker / bun, 普通与 Shizuku (uid 2000) |
| 事件 / 通知, device / dialogs | 8 | 36 | 8 / 0 | main / worker / bun |
| 合计 | 41 | | 41 / 0 | |

结论: 抽样范围内 Codex 宣称的设备行为在当前主工作区构建 (宿主 98f03b1f7, Node 1.32.0 / 292, Bun 0.21.0 / 296) 上全部复现, 数值与 Codex 的 Redmi 记录一致 (SIFT 203 / ORB 181 / 0.831 px, Shizuku uid 2000), 无失败项与新增不一致, 第 9 节归属表只加一行指向本节. 设备证据补强两条待决: R20 / Q27 (ShellProcess 运行时句柄的同名成员三列实测可用), R15 别名 (notice.cancel 经 notifications.cancel 三列可用).

未抽 (原因): 打包应用用例 (D11); Codex 各批的 Node / Bun 插件自身测试 APK (nodejs.test / bun.runtime.test, 非宿主族用例); WebView 的 SAF 与相机用例, WebMedia 批 (测试自动操作系统文档选择器 / 相机 / 麦克风权限界面, 见 Q34); 录屏与音频 (CaptureWorker 录屏 4 个方法, RecordingAudio, 不在本族清单); 矩阵 pending 行 (运行时无入口, 无从抽样).

授权与人工操作: Shizuku 由本会话经 adb 启动, 维护者确认 AutoJs6 已授权; 通知使用权由维护者开启并在结束后关闭; Node main 截图由维护者点 Start now; Worker / Bun 及 asyncCapture 的 3 个截图用例由测试代码自动点击 MediaProjection, 维护者对本轮一次性允许 (Q34). 测试在宿主重启后重绑已授权的无障碍服务与通知监听, 只针对已授权组件.

恢复与核对 (`device_sample_a.py restore` / `verify`): 宿主, 宿主测试包, Node, Bun 四个包装回抽样前 APK, 十个记录包的 versionCode 与 md5 全部与抽样前一致; 旋转, 熄屏时间, 充电常亮, 亮度与亮度模式, 无障碍服务列表 (集合), 通知使用权列表, appops PROJECT_MEDIA / SYSTEM_ALERT_WINDOW / POST_NOTIFICATION 模式, RECORD_AUDIO / CAMERA / POST_NOTIFICATIONS 授予状态均一致 (verify 退出码 0); 亮度, 无障碍与通知监听三项的抽样前值是首次安装前用 adb 读取后补入记录的. Shizuku 服务已停止 (管理器界面进程由维护者打开, 保留). 维护者在结束后重开了 AutoJs6 无障碍服务; dumpsys 的已绑定服务仍只有系统 Mi AI, 另 3 个插件的已启用服务同样未绑定, 抽样前未记录绑定状态. 证据 JSON 每条用例后即从设备删除; Shizuku 启动器自身在 /data/local/tmp 写入的 shizuku / shizuku_starter 保留 (启动器每次运行都会重写).

## P1.5 拒绝入口分组 (供 P1.4 出选择题; 不代替维护者拍板, D12 / Q1)

P1.5 (Q26 (a), 宿主 a1873159b) 起, 运行时声明只返回 `never` / `Promise<never>` 的入口不再算覆盖: 该行记 `pending`, 单元格带 `rejects: true` 并保留入口 (MATRIX.md 写 `pending (rejects)`); 同一行若另有不拒绝的入口 (例如 `autojs6:compat`) 则取后者. 要变 not-portable / denied, 必须在 `runtime-policy.json` 写排除规则并给理由 (缺理由计入 missingReasons). 拒绝入口单元格: Node main 37, Node Worker 3, Bun 30, 涉及 40 个显式导出行, 按第 6 节分组如下. 三列写 "rejects" 的是拒绝入口, 写 "pending" 的是该列本无入口.

| 分组 | 草案理由 | 行 (显式导出) | 行数 | Node main | Node Worker | Bun | 运行时声明 |
|---|---|---|---:|---|---|---|---|
| D-4 特权与双开应用操作 | security (需 Root / Shizuku 特权) | app.isDualInstalled / launchDual / launchDualApp / launchDualAppDetailsSettings / launchDualPackage / launchDualSettings / openDualAppSetting / openDualAppSettings / openDualUrl / startDualActivity / uninstallDual / killDual / kill (13), 及除 openDualUrl 外的 12 个全局放置 | 25 | rejects 25 | pending 25 (Worker 无 app, R8) | rejects 25 | Node app.d.ts 与 Bun app 命名空间均为 `Promise<never>` |
| D-5 存储枚举 | security (理由待 P1.4 定) | storages.names, storages.all | 2 | rejects 2 | pending 2 (Worker 无 storages) | rejects 2 | 两端 `never` |
| D-7 控制台同步输入 | sync-only | console.input, console.rawInput, console.build | 3 | rejects 3 | rejects 3 | pending 3 (Bun 用全局 console, 无同名入口; 输出类见 D-10) | Node console.d.ts `never` |
| D-12 Java 句柄与构造器 (拒绝部分) | java-prototype | files.open, files.toFile, open (全局) | 3 | rejects 3 (原记 local-shim) | pending 3 | rejects 3 | 两端 `never` |
| D-12 同上 | java-prototype | s13n.color, s13n.point, s13n.throwable, s13n.time | 4 | rejects 4 (原记 local-shim) | pending 4 | pending 4 (Bun 无 s13n) | Node s13n.d.ts `never` |

出题材料 (每组一题; 选项只列方向, 推荐项由 P1.4 结合 P1.2 / P1.3 结论标出):

- 每组可选: 按草案理由写排除规则 (三列都会由 pending 变 not-portable / denied, 包括本无入口的列), 或保持 pending 由 P3 / P4 补真正的入口.
- 五组全部按草案排除时, 三列 pending 各 -37 (Node main 37 个拒绝入口; Worker 3 个拒绝入口 + 34 个无入口; Bun 30 个拒绝入口 + 7 个无入口).
- D-4 若补入口, 需要特权授权模型 (Codex bun-automation 报告把双开 / kill 归为 Root / Shizuku 特权); D-5 的理由 (security 或 not-portable) 在出题时一并定; D-7 Worker 也显式失败 (runtime-video-worker), 与主线程同组; D-12 其余 6 行 (dialogs.build, notice.builder / getBuilder, shell.fromIntent, util.java.mapToObject / objectToMap) 运行时没有同名声明, 不是拒绝入口, 仍按第 6 节与本组同题.
- `autojs6:compat` 兼容门面里同名的拒绝入口 (compat.app.*, compat.files.open 等) 同样返回 never, 不会让这些行变为覆盖.

## P1.3 真机抽样 B (Xiaomi)

日期: 2026-10-25 / 26. 会话: api-cov-p1-3 (local_a6954132). 设备: Xiaomi 23046RP50C (968e9f18), Android 15 / API 35, arm64-v8a, 4096 字节页. 不重跑打包应用 (D11).

方法: `tools/api-coverage/device_sample_b.py` (record / backup / install / prepare / run / cleanup / restore / verify; `list` 打印抽样清单, 通用函数取自 `device_sample_a.py`) 逐个运行 Codex 宿主设备套件 (`app/src/androidTest/.../engine/`) 里的单个测试方法, 每行输出通过 / 失败, 耗时与证据文件中的关键数值. 列与 "项" 的含义同 P1.2 节, 另加 bridge = 不经 JavaScript 程序, 直接调用宿主原生桥接的用例 (三列共用). 选择依据: 第 9 节归 P3 / P4 的不一致所在模块 (R19 util.java) 与 R20 / Q27 的句柄 (Flow# / MailClient# / AiSession# / AgentRun#, P1.5 后按句柄类型比对), 以及各族 bridged 行数最多的模块 (mail 56, flow 55, speech + speech.models 19, ai 6). 本族 7 个 Codex 静态检查脚本 (`check-runtime-java-interop / java-callbacks / flow / mail / speech / ai / ai-agent.mjs`) 在抽样前重跑, 7 / 7 通过. 维护者 2026-10-25 23:5x 选择题: flow 套件夹具经 UiAutomation shell 临时移除并重新加入已授权的 AutoJs6 无障碍服务 (结束写回), 允许; mail 装本地 3-Stamp Mail 与其测试包写入合成别名账户, 结束后卸载, 同意.

### 版本与安装包

| 包 | 抽样前 (2026-10-25 23:4x 记录) | 抽样使用 | 来源 |
|---|---|---|---|
| org.autojs.autojs6 | 5935 (6.8.0) md5 c8cd5a710095314bb85e24b2ea6cd621, debuggable | 5935 (6.8.0) md5 7654596c2766bcf1ab3d700c51aacc3c | 主工作区 635c6c420 的 :app:assembleAppDebug (构建期间落地的 bcd3a2fa9 / 05cfae5df 只改 docs / tools, app 代码与 05cfae5df 相同); 工作区另有他人未提交的 changelog / README / docs / tools/selector 文件, 无 app 代码改动 |
| org.autojs.autojs6.test | md5 a45fbf5b21dc20f3fcef2c057b24588f | md5 d46ff56d8b5a4cad9b67e4d85b8777e2 | 同一次构建的 :app:assembleAppDebugAndroidTest |
| ...plugin.nodejs | 292 (1.32.0) md5 399e7a359fea55557a2415801b3b6b3d, debuggable | 292 (1.32.0) md5 c240d6cd49d72363fbbdd9cdda790cdf | Node 仓库 HEAD 990f9e35, 与 P1.2 所用 APK 相同 |
| ...plugin.bun.runtime | 118 (0.2.5) md5 555d45afcde4c611cb7665909313152b | 296 (0.21.0) md5 900edd665ecf82c35db76d25dc603b27 | Bun 仓库 HEAD 3056b94, 与 P1.2 所用 APK 相同 |
| ...plugin.three.macaw.speech | 81 (1.1.0) md5 76954b4bc439c05e1980789abb6940ba | 未替换 | 设备原有 (release, run-as 不可用) |
| ...plugin.threestoneai | 282 (1.3.0) md5 4ae10171f31f2673189982de8f47118c, debuggable | 未替换 | 设备原有 |
| ...plugin.three.stove.agent | 422 (1.3.0) md5 cf01262bfcb25a3d7f853ae3646fe45d, debuggable | 未替换 | 设备原有 |
| ...plugin.three.stamp.mail / .test | 未安装 | 见 mail 族 | |

记录的设置项: 旋转, 熄屏时间 120000, 充电常亮, 亮度与亮度模式, 无障碍服务列表 (含 AutoJs6 / AutoJs4 Runtime / 3-Macaw), 通知使用权列表, 语音交互与识别服务 (3-Macaw 为数字助理), appops PROJECT_MEDIA / SYSTEM_ALERT_WINDOW / RECORD_AUDIO (宿主与 3-Macaw), RECORD_AUDIO / POST_NOTIFICATIONS 授予状态, 四个音量流 (音乐 0, 铃声 0, 闹钟 12, 通知 0), 宿主 / 3-Stone AI / 3-Stove Agent 私有文件 md5 (3-Stone AI 与 3-Stove Agent 另以 run-as 备份 64 MiB 以下的私有文件, 模型文件只比对 md5).

### Java 互操作与回调

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeJavaInteropDeviceTest#nodePublicJavaAndAndroidObjects | main | 通过 | 5.3 | longExact, objectGraph (Rect / StringBuilder / Bundle / Intent / ArrayList), arrays, mainThread, packageName = 宿主 |
| ...#workerPublicJavaAndAndroidObjects | worker | 通过 | 6.8 | 同上 |
| ...#bunPublicJavaAndAndroidObjects | bun | 通过 | 5.8 | 同上 |
| ...#cjsEsmAndNestedWorkersReleaseOnlyTheirOwnHandles | main / worker (3 种) | 通过 | 7.6 | Worker 结束只释放自己的句柄, 父句柄可用 |
| RuntimeJavaInteropNativeTest#publicClassMetadataAndBunDefaultGrant | bridge | 通过 | 2.1 | 类描述, Bun java.interop 默认关闭 |
| ...#explicitDisableExecutionOwnershipAndDestruction | bridge | 通过 | 2.2 | 显式禁用, 执行归属, provider 销毁 |
| RuntimeJavaCallbacksDeviceTest#nodeCallbacksSubclassJarAndDex | main | 通过 | 8.9 | callbacks, superCall, reentry, constructorOnMain, androidListener, jar, dex, readOnlyCode, borrowedRetained, longExact |
| ...#workerCallbacksSubclassJarAndDex | worker | 通过 | 9.2 | 同上 |
| ...#bunCallbacksSubclassJarAndDex | bun | 通过 | 9.8 | 同上 |
| ...#esmAndNestedWorkersKeepCallbacksInTheirOwnerAndReleaseThemOnExit | main / worker | 通过 | 9.9 | 回调留在注册方, Worker 退出释放 |
| ...#snapshotsAvoidUserCodeAndNestedSuperCallbacksReturn | main / worker / bun | 通过 | 10.7 | 三种方式快照不调用 toString / hashCode 覆写 (各 1 次显式调用), 嵌套 callSuper 返回 |
| RuntimeJavaCallbacksNativeTest#loadersAreExecutionLocalAndOwnedReadOnlyCacheIsRemoved | bridge | 通过 | 2.5 | 加载器按执行隔离, 只读代码缓存在结束后删除 |

JAR / DEX 输入由 `build-java-callback-fixtures.py` 现场构建 (callback.jar sha256 1f8a2185..., fixture.dex 591f4228...), 推到宿主外部目录 `java-callback-fixtures/`, 结束时删除.

项 (运行时 java 门面, 第 4 节分母外; 宿主 Java 访问是 Rhino 语言层, 无显式导出行): java.type, describe, new, callStatic, getStatic, setStatic, cast, newArray, instanceOf, getContext, release, 实例调用与字段读写, proxy, wrap, defineClass, loadJar, loadDex, retain, closeCallbacks, 生成类 callSuper; 共 12 个用例 20 项, 全部通过. 与 R19 相关: 矩阵 util.java 6 行 (array, instanceOf, instanceof, mapToObject, objectToMap, toJsArray) 三列 pending, 运行时同义入口 java.instanceOf 与 java.newArray 在 Node main / Worker / Bun 实测可用 (java.toArray 本轮用例未直接调用), 支持 R19 在 P3.2 / P4.2 用 memberAliases 对到 java.instanceOf / newArray 的方向, 归属不变.

### flow

套件夹具 (Codex 原样) 在每个用例前经 UiAutomation shell 先移除再重新加入已授权的 AutoJs6 无障碍服务, 等服务可用后在宿主测试页上运行, 用例结束写回原值 (维护者同意, 见本节开头); 抽样后 enabled_accessibility_services 与抽样前逐字相同.

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeFlowDeviceTest#nodeChainsAndQueriesUseRealHost | main | 通过 | 6.5 | 核心 25 项检查, pending 0, 真实查询节点 1800x2880, visibleToUser |
| ...#bunChainsAndQueriesUseRealHost | bun | 通过 | 10.8 | 同上 |
| ...#workerChainsAndQueriesUseRealHost | worker | 通过 | 7.9 | 同上 |
| ...#unavailableServiceIsNotAHiddenTarget | main / bun | 通过 | 7.9 | 服务断开时查询报不可用, 不落到隐藏目标 |
| ...#bunLocalChainsNeedNoGrantButRawAndFacadeQueriesDo | bun | 通过 | 15.3 | 本地链无需授权, 原始调用与门面查询需 flow.control |
| ...#cjsEsmAndNestedWorkersReleaseTimersAndPreserveParentDefaults | main / worker (3 种) | 通过 | 7.8 | 3 种 Worker 退出释放计时器, 父 defaults 保留, pending 0 |
| RuntimeFlowToolsDeviceTest#nodeAdvancedToolsAndEvents | main | 通过 | 16.9 | 47 项检查, 18 次真实点击, 祖先上溯 1, 收集 4 项 35240 字符 |
| ...#bunAdvancedToolsAndEvents | bun | 通过 | 20.0 | 同上 |
| ...#workerAdvancedToolsAndEvents | worker | 通过 | 15.3 | 同上 |
| ...#bunFlowGrantIsSeparateFromAccessibility | bun | 通过 | 8.7 | 关闭 flow.control 后拒绝, 与无障碍授权独立 |
| RuntimeFlowScopeDeviceTest#nodeAdvancedToolsAndEvents | main | 通过 | 27.7 | 129 项检查, 其中范围 / 手势 82 项, 手势重载 16, within 两侧各 1 次点击 |
| ...#bunAdvancedToolsAndEvents | bun | 通过 | 34.0 | 同上 |
| ...#workerAdvancedToolsAndEvents | worker | 通过 | 27.4 | 同上 |

数值与 Codex 报告一致 (flow-tools 每种方式 47 项, flow-scope 129 项含 82 项范围 / 手势检查).

项: 矩阵行 flow.of, run, all, any, race, delay, sleep, wait, waitAsync, waitUntil, waitWhile, repeatUntil, defaults, pending, workers, cancelAll, waitForActivity, waitForPackage, waitForVisible, waitForGone, waitForHidden, waitForStable, clickAny, clickIfExists, clickBoundsAny, clickBoundsIfExists, findAny, collectList, scrollUntil, smartClick, smartClickBounds, toggle, typeInto, waitThenLongClick, backToApp, backUntil, launchAndWait, waitForEvent, waitForIdle, waitForNotification, waitForToast (41 / 55 行, 三列 bridged / capability-gated); 运行时链对象成员 (矩阵 Flow# 行, P1.5 后除 await / sync 外按句柄类型计为覆盖) run, map, filter, retry, timeout, catch, whenPresent, click, setText, appendText, clear, collectList, dismissPopups, press, swipe, waitForIdle, toString; 共 13 个用例 58 项, 全部通过. 与 R20 / Q27 相关: Flow# 这 17 个成员在 Node main / Worker / Bun 的链对象上实测可用, 支持 P1.5 的句柄类型比对结论.

### mail

沿用 Codex 批次的账户配置: 本机启动 3-Stamp Mail 仓库 mail-core 测试类路径中的合成回环 IMAP / SMTP / POP3 服务器 (只监听 127.0.0.1), 经 adb reverse 映射到设备, `runtime-mail-fixture.json` 推到宿主外部目录; 设备原本未安装 3-Stamp Mail, 抽样时安装本地 2.1.0 debug 包 (versionCode 162, md5 3bea2e2ae024fa654ac6955ecb469f2e, Mail 仓库 HEAD 59b8b37, APK 构建于 276eecb 之后, 其后两笔只改文档) 与其测试包 (md5 46b4e8b7df1b485a54440f53d134f011), 由测试包 RuntimeMailAccountFixtureTest 写入合成别名账户 runtime-mail-fixture (合成地址与密码, 无真实凭据). 抽样后服务器停止, 自建的 3 条反向映射移除, 两个包卸载 (别名随之删除), 设备回到 Mail 未安装状态.

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeMailDeviceTest#nodeUsesRealMailProtocolsAndAttachments | main | 通过 | 14.8 | 正文 5000 个 Unicode 标记, 附件 98304 字节 (sha256 一致), 下载进度, IDLE 监听收到 1 封并关闭, 就绪探针 1, 凭据脱敏, 已签名宿主可用保存的别名 |
| ...#bunUsesRealMailProtocolsAndAttachments | bun | 通过 | 15.2 | 同上 |
| ...#workerUsesRealMailProtocolsAndAttachments | worker | 通过 | 14.0 | 同上 |
| ...#asynchronousTokenProvidersRefreshThroughTheRealPlugin | main / worker / bun | 通过 | 8.7 | xoauth2 令牌回调在三种方式中各刷新一次 |
| ...#bunDeniesMissingGrantAndKeepsStreamingBeyond1024Calls | bun | 通过 | 12.1 | 无 mail.control 时 NOT_GRANTED; 1044 次流式调用不受 1024 次上限, 预设 10 个 |
| ...#cjsEsmNestedWorkerExitPreservesTheParentMailClient | main / worker (3 种) | 通过 | 7.7 | 3 种 Worker 退出, 父客户端保留 |
| RuntimeMailNativeTest#cancelledTokenRequestsLeaveNoUndrainableTerminalPackets | bridge | 通过 | 2.0 | 取消的令牌请求不留未取走的终止包 |
| ...#invalidAccountsAndClosedClientHandlesRetainTheMailErrorCode | bridge | 通过 | 2.0 | 非法账户与已关闭句柄保留 MailError 码 |

项: 矩阵行 mail.providers, accounts, connect, connectAsync, setDefault, default, fetch, foldersAsync, searchAsync, sendAsync, flagAsync, rawAsync, close, MailError (14 / 56 行, 三列 bridged / capability-gated); 运行时客户端句柄成员 (矩阵 MailClient# 行, P1.5 后按句柄类型计为覆盖) test, testAsync, folder, folderStatus, createFolder, deleteFolder, append, appendAsync, fetch, fetchAsync, fetchBodyAsync, get, search, markRead, markUnreadAsync, setFlagsAsync, unflag, copyAsync, move, delete, deleteAsync, expungeAsync, watch, close (24 项); 共 8 个用例 38 项, 全部通过. 与 R20 / R22 相关: MailClient# 24 个成员与 mail.accounts / providers 的嵌套方法 (has, list, resolve, get) 在三列实测可用, R22 归属 (P2.3 登记嵌套对象) 不变.
