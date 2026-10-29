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

## 7. 矩阵 pending 分布 (P1.4 定稿, 2026-10-29; 作 P3 / P4 分批依据)

拍板后的 HEAD 矩阵 (2739 行, 来源与命令见 BASELINE 第 6 节): Node main 643, Node Worker 832, Bun 791, 与 `tools/api-coverage/pending-baseline.json` 一致. 演变: P1.1 初稿 (2727 行) 844 / 999 / 942 -> P1.5 修正 622 / 754 / 713 -> 拍板前 HEAD (2739 行) 631 / 763 / 722 -> 拍板后 643 / 832 / 791 (各组增量见 BASELINE 6.3). P2.3 登记原型成员后分母会增加数百行 (Q18), 届时按同一方法再定一次.

下表按 P3 / P4 模块族列各模块 (返回对象以 `#` 结尾, 全局函数按用途拆入各族) 的 Node main / Node Worker / Bun pending 行数. 族名与条目见 ROADMAP 第 5 节; 拍板后保持 pending 的附录 D 组标在 "说明" 列.

| 族 (条目) | 模块 | Node main | Node Worker | Bun | 说明 |
|---|---|---:|---:|---:|---|
| 系统与设备 (P3.1 / P4.1) | app | 31 | 71 | 31 | D-4 特权与双开 25 行 (Root / Shizuku 真机); Worker 缺 app 模块 (R8) |
| | autojs (+ autojs.version) | 29 | 29 | 29 | |
| | shell | 28 | 28 | 28 | R14; D-12 shell.fromIntent |
| | device | 21 | 21 | 21 | R18; D-2 getSharedDeviceId |
| | 全局 (currentPackage / currentActivity / currentComponent, exit / stop / isRunning 等, requiresApi, waitForActivity / waitForPackage, setScreenMetrics, WIDTH / HEIGHT, Worker 的 getClip / setClip) | 17 | 19 | 17 | |
| | installer | 16 | 16 | 16 | |
| | notice | 12 | 12 | 12 | R16; D-12 notice.builder / getBuilder |
| | workManager | 11 | 11 | 11 | |
| | ShellProcess# | 10 | 10 | 10 | Q31 三个 Java 流 getter, EventEmitter 别名 5, isAlive, toString |
| | engines | 3 | 8 | 8 | D-14 execScript / execScriptFile / execAutoFile |
| | plugins / shizuku / memory / settings / sysprops / powerManager / dialogs | 30 | 30 | 30 | 各 7 / 7 / 5 / 4 / 4 / 2 / 1; D-12 dialogs.build |
| | 小计 | 208 | 255 | 213 | |
| 文件与数据 (P3.2 / P4.2) | util (+ util.java 等子模块) | 65 | 65 | 66 | D-12 util.java.* |
| | species | 38 | 38 | 38 | 预期 local-shim (Q5 (a)) |
| | Color# / colors (+ colors.theme) | 57 | 57 | 57 | R21 |
| | Mathx / Arrayx / Numberx / jsox | 0 | 56 | 56 | D-13 (Worker 导入 jsox 本地模块, Bun 新增 jsox 命名空间, local-shim) |
| | files | 20 | 22 | 20 | R11 (batchRename); D-12 files.open / toFile |
| | Zip# / zip | 26 | 26 | 26 | |
| | 全局 (axios / cheerio / dayjs / pangu / i18n, random, isInteger / isPrimitive 等) | 12 | 12 | 12 | D-13 全局 randomInt / randomFloat 在 Mathx 行 |
| | Storage# / storages | 3 | 17 | 3 | D-5 storages.names / all; Worker 缺 storages 模块 (R8) |
| | gallery | 11 | 11 | 11 | |
| | pinyin | 0 | 0 | 10 | R3 (Bun 常量) |
| | mediainfo / cvt / fmt / s13n / EpubBook# | 8 | 25 | 25 | 各 3-7 / 0-6 / 0-6 / 4-5 / 1; D-12 s13n.* |
| | 小计 | 240 | 329 | 324 | |
| UI (P3.3 / P4.3) | ui | 31 | 31 | 31 | R7, D-17 Activity 内容视图 15 行 |
| | console | 8 | 8 | 28 | R2 / R4, D-7 同步输入 3 行, D-10 Bun 输出 11 行 |
| | terminal | 20 | 20 | 20 | |
| | 全局 (toast* 10 个, Node main 的 toastLog 已覆盖; isUiThread) / toast | 11 | 12 | 12 | |
| | floaty / web / compose | 17 | 17 | 17 | 各 8 / 7 / 2; R6 compose.ComposeError |
| | 小计 | 87 | 88 | 108 | |
| 自动化 (P3.4 / P4.4) | automator | 30 | 55 | 41 | R9 |
| | auto | 38 | 38 | 38 | R9; D-1 auto.waitFor |
| | keys | 0 | 12 | 12 | R8 |
| | ocr / barcode / qrcode / YoloDetector# / yolo | 9 | 23 | 23 | 各 6-12 / 0-4 / 0-4 / 2 / 1 |
| | threads / continuation / timers / VolatileDispose# / 全局 sleep / wait | 15 | 16 | 16 | D-6 (7 行), D-8 (sleep, keepAlive, loop, timers.keepAlive) |
| | selector / Flow# / flow | 7 | 7 | 7 | Q31 Flow#await / sync |
| | 小计 | 99 | 151 | 137 | |
| 收口 (P3.5 / P4.5) | speech | 8 | 8 | 8 | speech.speaker 8 个函数: 运行时侧随 3-Macaw Speech 1.3.0 (路线图 P8.3, 已暂停) 补入口 |
| | 全局 TODO | 1 | 1 | 1 | |
| | 小计 | 9 | 9 | 9 | |
| 合计 | | 643 | 832 | 791 | |

### 7.1 P3 / P4 建议分批

ROADMAP 约定每批 3 ~ 5 个模块, 1 个子会话 (宿主桥接 + facade + d.ts + HOST-API + 文档运行时表 + 真机验证). 系统与数据两族超过 200 行, 各拆两批; P4 同批次与 P3 对应 (Bun 列), 并行时占另一台主力设备 (D15).

| 批次 | 模块 | Node main / Worker / Bun | 依赖与前置 |
|---|---|---|---|
| P3.2a / P4.2a 纯 JS 工具 | util, species, cvt, fmt, s13n, jsox (Mathx / Arrayx / Numberx), 全局数据函数, pinyin (Bun) | 119 / 188 / 199 | 无宿主桥接依赖, 可最先启动; local-shim 须与 Rhino 行为一致且有对照测试 (Q5 (a)); util.java.* / s13n.* 句柄入口 (D-12) 需先定句柄形式 |
| P3.2b / P4.2b 文件与数据 | colors / Color#, files, zip / Zip#, storages / Storage#, gallery, mediainfo, EpubBook# | 121 / 141 / 125 | Worker 补 storages 模块 (R8); EpubBook# 的设备验证依赖 Q42 (check-runtime-epub 失效) |
| P3.1a / P4.1a 应用与 shell | app (D-4 特权), shell / shizuku, ShellProcess# | 76 / 116 / 76 | Root / Shizuku 真机; D-4 的授权方式 (复用 shell.root / shell.shizuku 或新授权) 可能需新待决 |
| P3.1b / P4.1b 系统信息与引擎 | autojs, device, installer, notice, workManager, engines (D-14), plugins, memory, settings, sysprops, powerManager, dialogs, 全局系统函数 | 132 / 139 / 137 | engines.execScript* 的跨运行时语义需设计; 与 P3.1a 共用宿主 host-client 文件时串行 |
| P3.3 / P4.3 UI | ui, console, terminal, floaty, web, compose, toast | 87 / 88 / 108 | D-17 Activity 内容视图桥接与 D-7 异步 console.input 先出设计 (可能登记待决); D-10 只在 P4.3 |
| P3.4 / P4.4 自动化 | automator, auto, keys, ocr / barcode / qrcode / yolo, threads / continuation / timers, selector / Flow# | 99 / 151 / 137 | D-1 / D-6 / D-8 的异步替代入口先出设计; selector 路线图 (P4) 与本批改同一批文件时先协调 |
| P3.5 / P4.5 收口 | speech.speaker, 全局 TODO, 以及前几批遗留 | 9 / 9 / 9 | speech.speaker 等 3-Macaw Speech 1.3.0 插件实现 (P8.3 暂停中); 设备验证依赖 Q42 (check-runtime-speech) |

建议启动顺序: P3.2a 与 P4.2a 先行 (无设计前置, 行数最多); 其后 P3.1a / P3.2b 与对应 P4 批次; 需要设计的 UI 与自动化批次 (D-1 / D-6 / D-7 / D-8 / D-17) 在设计待决拍板后启动. 每批结束用 `check.py --write-pending-baseline` 收缩基线.

P3.2a 落点 (2026-10-29, api-cov-p3-2a; 宿主 3ebafb50e): 本批 Node main 122 -> 9, Worker 191 -> 10 (含 Q18 新增的 util 3 行; 全列 776 / 987 -> 663 / 806). 遗留:

| 行 | Node main | Node Worker | 待决 |
|---|---|---|---|
| dayjs, pangu, cheerio, i18n (全局库 getter) | pending | pending | P3.2a 遗留 (待 Q61) |
| axios (全局 getter) | local-shim (getter 本身, 返回现有 axios facade; axios 模块成员另计) | pending | P3.2a 遗留 (待 Q61) |
| util.class, util.className, util.getClassName | pending | pending | P3.2a 遗留 (待 Q54 (b), Java 内省) |
| util.toRegularAndCall, util.toRegularAndApply | pending (rejects) | pending (rejects) | P3.2a 遗留 (待 Q63; Rhino 中同样总是抛错) |

另: util.unwrapJavaObject 保持 not-portable (D-11); 全局 isJavaClass / isJavaPackage 的属主模块 species 在 Node 已覆盖, java-prototype 规则加 override 按 D-11 保持 not-portable (否则报 shadowed). 已按推荐先行的设计: Q59 (jsox.extend 改内建), Q60 (dp / sp 快照线性), Q62 (与 Rhino 的剩余差异写进 HOST-API).

P4.2a 落点 (2026-10-29, api-cov-p4-2a; 宿主 10c844a1f): 本批 Bun 202 -> 10 (含 Q18 新增的 util 3 行); Bun 全列 949 -> 757, 其中 949 -> 818 是 3ebafb50e 按 Bun 976312d 用当时的工具先行重算, 818 -> 757 来自本批的带点别名 (Mathx / Arrayx / Numberx), 全局命名空间 global 与成员级能力. 本批 Bun 单元格: local-shim 173, capability-gated 19 (pinyin 常量 10 随 text.pinyin 命名空间, 常量本身无需授权即可读取; util.java.* 6, s13n.point / throwable, util.morseCode.vibrate 随 java.interop). 遗留:

| 行 | Bun | 待决 |
|---|---|---|
| dayjs, pangu, cheerio, i18n (全局库 getter) | pending | P4.2a 遗留 (待 Q64, 随 Q61) |
| axios (全局 getter) | pending | P4.2a 遗留 (待 Q64; Bun 客户端没有 axios facade) |
| util.class, util.className, util.getClassName | pending | P4.2a 遗留 (待 Q54 (b), Java 内省) |
| util.toRegularAndCall, util.toRegularAndApply | pending (rejects) | P4.2a 遗留 (待 Q63; Rhino 中同样总是抛错) |

另: util.unwrapJavaObject 与全局 isJavaClass / isJavaPackage 同 Node 保持 not-portable (D-11, java-prototype 规则的 override 对 Bun 同样适用). 新命名空间都在客户端本地执行, 不经宿主桥接, 按 Q3 (a) 不新增 `.control` 授权; 需要 Java 对象的成员沿用 java.interop. Q59 / Q60 / Q62 在 Bun 上与 Node 相同 (共享核心).

P3.2b 落点 (2026-10-29, api-cov-p3-2b; 宿主 0a39eeb3f): 本族 Node main 160 -> 40, Worker 194 -> 40 (含 P3.2a 遗留的 Q61 / Q63 / Q54 行; 全列 663 / 806 -> 549 / 658, 其中各 6 格是 3-Macaw P8.4.1 新增的 speech.diarize / detectLanguage / tag 与 Async, 以 --allow-new-pending 登记, 随 speech.speaker 归 P3.5 / P4.5). 本族 256 行 (colors / Color#, files 与原型成员, zip / Zip#, storages / Storage#, gallery, mediainfo, mime, recorder, EpubBook#toString, 全局 open 与 Q61 的五项, util.class 等 3 行, util.toRegularAndCall / Apply), Node main 与 Worker 相同: local-shim 156 (共享 JS 核心: colors / Color#, files, mime, 全局 open 与 Q61 库), bridged 58 (zip / Zip#, recorder, storages / Storage#, mediainfo, EpubBook#toString), not-portable 2 (Q63 (a), obsoleted), pending 40. 遗留:

| 行 | Node main | Node Worker | 待决 |
|---|---|---|---|
| colors.theme.* (9) | pending | pending | P3.2b 遗留 (待 Q80 (a), 宿主桥接新模块, P3.5) |
| gallery.* (11) | pending | pending | P3.2b 遗留 (待 Q81 (a), 宿主桥接新模块, P3.5) |
| files.batchRename.* (17) | pending | pending | P3.2b 遗留 (待 Q82 (a), 宿主桥接新模块, P3.5) |
| util.class, util.className, util.getClassName | pending | pending | P3.2a 遗留 (待 Q54 (b), Java 内省; P3.2b 未补) |

另: Q61 (b) 的 dayjs / pangu / cheerio / i18n 用宿主的构建随 Node 插件 APK (每 ABI 约 0.3 MB), 与 augment P4.3 / P5.2 把同一批库移进 Bundled Libraries 插件的方向不同, 交调度会话决定是否改为从插件读取; Q79 (a) 的 storages.names / all 只列 Node 自记过的存储; Q83 的五处差异已按推荐先行, 写进 HOST-API 与 d.ts. Rhino 对照中 15 例因宿主 ColorNativeObject 抛出未解包的 InvocationTargetException 而使 Rhino 解释器失败 (try / catch 拦不住), 属宿主缺陷, 另行报告.

### 7.2 Q18 原型成员增量 (P2.3, 2026-10-29)

P2.3 第 1 步把 22 个 `@ScriptPrototype` 模块的原型成员纳入分母 (Q18 (a), 宿主 b027777bc / d41ebd872): 矩阵 2739 -> 2943 行 (显式导出 1377, 原型成员 205, 返回对象成员 1362 -> 1361, 少的 1 行是嵌套类构造参数误计的 `TtsUtterance#utterance`). 原型成员行 id 与显式导出同形 (`files.isFile`), 运行时三列按显式导出的方式查找; 被模块自身导出遮蔽的 65 个名字不计, 98 个 Java 内部成员 exclude (Q56), 常量表与 global 的 Java 类代理不逐个计入 (Q55). 新增 pending Node main 133 / Node Worker 155 / Bun 158 (643 / 832 / 791 -> 776 / 987 / 949), 已登记看板待决 Q54 后写入 `pending-baseline.json`. 原型成员中已有入口的: Node main 60 / Worker 38 / Bun 41 (device 31 行: Build 字段, 音量, 亮度, 电量与内存, 唤醒; events 的 observeKey / observeNotification / observeToast / on / once; sensors.once; ui.view; files 13 行在 Node main 与 Bun 记 local-shim; media 9 行只在 Node main); 由已决规则排除的: crypto 6 行 (native-crypto), timers 的 Node 6 行 (nativeModules 记 runtime-native).

| 族 (条目) | 模块 | Node main | Node Worker | Bun | 说明 |
|---|---|---:|---:|---:|---|
| 系统与设备 (P3.1b / P4.1b) | device | 30 | 30 | 40 | baseOS / codename / serial / imei 等字段, 权限 / 电源 / 方向查询, brands / manufacturers / roms 对象; Bun 比 Node 多 10 行 (Build 字段 board / brand / model 等 Node 已有) |
| | sensors | 20 | 20 | 20 | EventEmitter 成员 (sensors.once 已有) 与 register / unregister / getSensor / delay |
| | media | 0 | 9 | 9 | 音乐播放与 scanFile; Node main 已有, Worker 与 Bun 缺 |
| 系统与设备 (P3.1a / P4.1a) | shizuku | 5 | 5 | 5 | hasPermission / hasService / isInstalled / isOperational / isRunning |
| | 小计 | 55 | 64 | 74 | |
| 文件与数据 (P3.2b / P4.2b) | files | 17 | 30 | 17 | 原型上的 exists / isFile / read / write 等 30 个; Node main 与 Bun 各有 13 个 (local-shim), Worker 全缺 |
| | recorder | 7 | 7 | 7 | save / load / has / clear / remove / isGreaterThan / isLessThan |
| | mime | 5 | 5 | 5 | fromFile / fromFileOr / fromFileOrWildcard / getMediaType / parseMediaType (MIME 常量不计, Q55) |
| | colors | 1 | 1 | 1 | parseColor (色表不计, Q55) |
| 文件与数据 (P3.2a / P4.2a) | util | 3 | 3 | 3 | class / className / getClassName: Java 类内省, 与 D-11 同类, 可在 P3.2a 登记排除候选 (见 Q54 选项 b) |
| | 小计 | 33 | 46 | 33 | |
| UI (P3.3 / P4.3) | console | 6 | 6 | 6 | isShowing, setGravity, setTouchThrough, reset*BackgroundAlpha 3 个 |
| | ui | 2 | 2 | 2 | bindingContext, layoutInflater (ui.view 已有入口) |
| | 小计 | 8 | 8 | 8 | |
| 自动化 (P3.4 / P4.4) | events | 31 | 31 | 31 | EventEmitter 成员, broadcast / emitter, onKeyDown / onKeyUp / onTouch / onceKey* / removeAll*Listeners, onNotification / onToast 与 remove*Observer |
| | timers | 0 | 0 | 6 | timers.setTimeout 等 6 个: Node 经 nativeModules 已记 runtime-native; Bun 的 native-timers 规则只匹配全局名, 可作排除候选 (见 Q54 选项 b) |
| | threads | 4 | 4 | 4 | atomic, currentThread, lock, shutDownAll |
| | continuation | 2 | 2 | 2 | await, delay |
| | 小计 | 37 | 37 | 43 | |
| 合计 | | 133 | 155 | 158 | |

7.1 的分批行数加上本表后: P3.2a / P4.2a 122 / 191 / 202, P3.2b / P4.2b 151 / 184 / 155, P3.1a / P4.1a 81 / 121 / 81, P3.1b / P4.1b 182 / 198 / 206, P3.3 / P4.3 95 / 96 / 116, P3.4 / P4.4 136 / 188 / 180, P3.5 / P4.5 9 / 9 / 9 (合计 776 / 987 / 949). P3.1b 超过 150 行, 可把 device / sensors / media (50 / 59 / 69) 拆为单独批次.

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
| P1.3 真机抽样 B (Xiaomi) | 无失败项 (53 个用例全部通过, 见 "P1.3 真机抽样 B (Xiaomi)" 节); 设备证据补强 R20 (Flow# / MailClient# / AiSession# / AgentRun# 句柄成员) 与 R19 的别名方向; 未新增待决 |
| P1.5 矩阵修正 (Q26 / Q27 / Q28) | R13 改记 pending (rejects), 分组见 "P1.5 拒绝入口分组" 节, 白名单仍由 P1.4 拍板; R20 按句柄类型逐成员比对 (BASELINE 第 5 节); R9 中 Toolkit 助手 38 行经 flow 计为覆盖 |
| P1.4 拍板 (2026-10-29) | R4 / R7 (ui-thread) / R13 / Q31 由附录 D 结论表收口; 保持排除: D-9 (runtime-native, http.client / buildRequest 改 java-prototype), D-11, D-14 的 plugins.load (Worker / Bun), D-15 |
| P3.1 / P4.1 (保持 pending 的组) | D-2 device.getSharedDeviceId, D-4 特权与双开 (25 行), D-12 的 dialogs.build / notice.builder / getBuilder / shell.fromIntent, D-14 的 engines.execScript / execScriptFile / execAutoFile, Q31 ShellProcess 三个 Java 流 getter |
| P3.2 / P4.2 (保持 pending 的组) | D-5 storages.names / all, D-12 的 files.open / toFile / open, s13n.*, util.java.*, D-13 JSOX 扩展 54 行与全局 randomInt / randomFloat (local-shim) |
| P3.3 / P4.3 (保持 pending 的组) | D-7 console.input / rawInput / build, D-17 Activity 内容视图 (15 行); D-10 Bun 控制台输出 11 行只在 P4.3 |
| P3.4 / P4.4 (保持 pending 的组) | D-1 auto.waitFor, D-6 threads / sync / continuation (7 行), D-8 sleep (Worker / Bun) / keepAlive / loop / timers.keepAlive, Q31 Flow#await / sync |
| P3.5 / P4.5 | speech.speaker 8 行的运行时入口 (文档 / d.ts / Ace 已由 P1.4 代补, Q41); 前几批遗留 |
| 待决 | Q40 pending 基线只减 (已先行); Q42 7 个 Codex 设备脚本失效 (未先行, 影响 P3 / P4 涉及 ai / bridges / epub / mail / speech / tts 的设备验证) |

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

### speech

3-Macaw Speech 用设备原有的 1.1.0 (versionCode 81, release), 未替换. 识别用测试包内置的 6.625 秒 LibriSpeech WAV 与设备上已装的流式 ASR 模型; 合成以音量 0 播放 (抽样前后音乐音量均为 0); 麦克风与唤醒会话只开启即停止, 不发声, 与相邻手机的声学用例无冲突. 程序导入一个管理用模型包 (非推理模型), 临时改 ASR 默认后在 finally 中还原默认并移除导入的模型.

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeSpeechDeviceTest#nodeRecognitionSessionsAndModels | main | 通过 | 17.5 | 文件与偏移 PCM 视图各识别 104 字符 (6625 ms, 1 段), VAD 1 段, 外部会话 104 字符, 目录 11 个模型, 导入进度 2 次, 合成 13096 字节 (静音) |
| ...#bunRecognitionSessionsAndModels | bun | 通过 | 19.1 | 同上, 合成 14666 字节 |
| ...#workerRecognitionSessionsAndModels | worker | 通过 | 15.8 | 同上, 合成 13804 字节 |
| ...#bunDeniedGrantAndMoreThan1024StreamingCalls | bun | 通过 | 11.8 | 无 speech.control 时 NOT_GRANTED; 1044 次流式调用不受 1024 次上限 |
| ...#microphoneWakeAndEsmNestedWorkerCleanup | main / worker (3 种) | 通过 | 9.4 | 麦克风听写与唤醒会话开启并停止 (reason stopped), 3 种 Worker 清理 |
| RuntimeSpeechNativeTest#pcmUploadsStayWithTheirClientAndDisappearOnClose | bridge | 通过 | 2.1 | PCM 上传归属客户端, 关闭后删除 |
| ...#nativeArgumentFailuresKeepTheirSpeechCodeAcrossFragmentedPolling | bridge | 通过 | 2.2 | 分片轮询中参数错误保留 SpeechError 码 |

项: 矩阵行 speech.isAvailable, capabilities, recognize, recognizeAsync, detectVoice, listen, wake, synthesize, speak, SpeechError, speech.models.list, getDefault, setDefault, importFile, install, installAsync, remove (17 / 19 行, 三列 bridged / capability-gated; 未直接调用 speech.synthesizeAsync 与 speech.models.refreshCatalog); 共 7 个用例 17 项, 全部通过.

### ai (core 与 Agent)

3-Stone AI 用设备原有的 1.3.0 (versionCode 282), 3-Stove Agent 用设备原有的 1.3.0 (versionCode 422), 均未替换 (Codex 验收时 3-Stone AI 为 1.5.0 / 299); 推理只用设备上已装的本地模型 (目录中优先 E2B), 不调用在线账户, 不下载模型. 宿主经签名保护的原入口访问, 无需应用授权对话框; Agent 用例的目标以本次抽样的批次标记开头, 3-Stove Agent 因此新增的 11 条任务记录在恢复时随私有数据备份一并移除.

| 用例 | 列 | 结果 | 秒 | 关键数值 |
|---|---|---|---:|---|
| RuntimeAiDeviceTest#nodeCoreUsesRealProvider | main | 通过 | 14.9 | 22 项检查 (Codex 门槛 >= 20), chat 输入 20 / 输出 2 token, 2054 ms; stream 1 次 open / chunk / usage / done |
| ...#bunCoreUsesRealProvider | bun | 通过 | 13.5 | 同上, chat 1075 ms |
| ...#workerCoreUsesRealProvider | worker | 通过 | 13.1 | 同上, chat 1122 ms |
| ...#workerModesCloseSessionsAndKeepParentClientUsable | main / worker (3 种) | 通过 | 8.2 | Worker 结束关闭其会话, 父客户端与 provider 准入释放 |
| ...#bunGrantAndNodeDeclarationRejectBeforeProviderAccess | main / bun | 通过 | 7.1 | Bun 无 ai.control 时 NOT_GRANTED, Node 未声明能力时拒绝, 均不触达 provider |
| ...#terminalPacketsKeepCancellationOwnershipUntilAcknowledged | bridge | 通过 | 2.0 | 终止包在确认前保留取消归属 |
| RuntimeAgentDeviceTest#nodeAgentUsesRealPluginAndModel | main | 通过 | 29.6 | 27 项检查 (与 Codex 相同), 模型调用 1 次, 输入 1013 / 输出 64 token (非估算), state 事件 2, step 1, done 1 |
| ...#bunAgentUsesIndependentGrantWithRealPluginAndModel | bun | 通过 | 19.1 | 同上, 输出 32 token |
| ...#workerAgentUsesRealPluginAndModel | worker | 通过 | 18.3 | 同上, 输出 30 token |
| ...#agentDiscoveryUsesTheOriginalProtectedService | bridge | 通过 | 6.0 | 发现走原有受保护服务 |
| ...#coreAiCapabilityDoesNotAuthorizeAgentTasks | main / bun | 通过 | 7.2 | 只有 ai / ai.control 时 Agent 任务被拒 |
| ...#nativeTaskAcknowledgmentAndExplicitDetachedCancellationRetainOwnership | bridge | 通过 | 6.7 | detached 任务, 准入阶段显式取消 |
| ...#workerModesCancelOwnedRunsButRetainDetachedRunsAndParentClient | main / worker (3 种) | 通过 | 8.5 | 3 种 Worker 退出取消自己的非 detached 任务, detached 任务与父客户端保留 |

项: 矩阵行 ai.ask, chat, stream, session, catalog, agent (6 / 6 行, 三列 bridged / capability-gated); 运行时会话句柄成员 (矩阵 AiSession# 行) ask, chat, stream, close, isClosed, state, target; ai.agent 嵌套根 hasAccess, requestAccess, status, presets, catalog, context, result, create, run, get, list; 任务句柄成员 (矩阵 AgentRun# 行) id, goal, startedAt, detached, state, on, join, result, error, cancel, respond, confirm; 共 13 个用例 36 项, 全部通过. 与 R20 / R22 相关: AiSession# 7 个与 AgentRun# 12 个成员在三列实测可用, 支持 P1.5 的句柄类型比对; ai.agent 嵌套根在分母外, R22 归属 (P2.3) 不变.

### 汇总, 未抽项与恢复

| 族 | 用例 | 项 | 通过 / 失败 | 列 |
|---|---:|---:|---|---|
| Java 互操作与回调 | 12 | 20 | 12 / 0 | main / worker / bun, bridge 3 |
| flow | 13 | 58 | 13 / 0 | main / worker / bun |
| mail | 8 | 38 | 8 / 0 | main / worker / bun, bridge 2 |
| speech | 7 | 17 | 7 / 0 | main / worker / bun, bridge 2 |
| ai (core 与 Agent) | 13 | 36 | 13 / 0 | main / worker / bun, bridge 3 |
| 合计 | 53 | 169 | 53 / 0 | |

结论: 抽样范围内 Codex 宣称的设备行为在当前主工作区构建 (宿主 635c6c420 / app 代码同 05cfae5df, Node 1.32.0 / 292, Bun 0.21.0 / 296, 设备原有的 3-Macaw 81 / 3-Stone AI 282 / 3-Stove Agent 422, 本地 3-Stamp Mail 162) 上全部复现, 关键数值与 Codex 的 Xiaomi 记录一致 (flow-tools 47 项 / flow-scope 129 项, mail 5000 个标记与 98304 字节附件, Agent 27 项), 无失败项与新增不一致, 未新增待决 (Q37-Q39 未用). 设备证据补强 R20 (Flow# / MailClient# / AiSession# / AgentRun# 的运行时句柄成员三列可用, 与 P1.5 句柄类型比对一致) 与 R19 的别名方向 (java.instanceOf / newArray).

未抽 (原因): 打包应用用例 (D11; 各族的 *PackagedDeviceTest); Codex 各批的 Node / Bun 插件自身测试 APK (nodejs.test / bun.runtime.test) 与 Ace 弹窗用例 (非宿主族用例); 3-Stamp Mail 插件自身的 provider / TLS / mail-core 设备套件 (插件内部, 非运行时列); Agent 的已注册脚本上下文用例 (registeredNodeBunAndWorkerScriptsShareOnlyTheirExecutionContext, 本轮按每族 10-20 项的规模未选); 矩阵 pending 行 (util.java 6 行等, 运行时无入口).

授权与人工操作: 维护者选择题同意 flow 夹具临时重绑已授权的 AutoJs6 无障碍服务 (经 UiAutomation shell 写 secure 设置并写回) 与 mail 合成别名账户的写入及卸载删除; 本轮无系统授权对话框, 测试代码未自动点击任何系统界面.

恢复与核对 (`device_sample_b.py cleanup` / `restore` / `verify`): 宿主, 宿主测试包, Node, Bun 装回抽样前 APK (Bun 由 296 降回 118), 3-Stamp Mail 与其测试包卸载, 九个记录包 (含未安装的两个) 的 versionCode 与 md5 全部与抽样前一致; 旋转, 熄屏时间, 充电常亮, 亮度与亮度模式, 无障碍服务列表, 通知使用权, 语音交互与识别服务, 数字助理, appops 四项模式, 四个音量流均一致; 3-Stone AI 私有文件 (含两个模型文件 md5) 未变, 3-Stove Agent 私有数据用抽样前的 run-as 备份还原 (11 条批次任务记录移除) 后与抽样前一致; 宿主外部目录中本次创建的文件 (证据 JSON, JAR / DEX fixture, 邮件 fixture JSON, 语音清单) 全部删除, 自建的 3 条 adb reverse 映射移除, 合成邮件服务器已停止. 唯一差异: 宿主私有文件 files/profileInstalled (24 字节, androidx.profileinstaller 记录的安装时间戳, 重装 APK 后应用启动即重写, 不是用户数据, 无法还原到原字节).

## P1.4 白名单拍板材料 (供 Q1 / Q5 选择题; 2026-10-29, 子会话 api-cov-p1-4 local_7dd24704)

数据: 宿主 HEAD ffd0b4a58 的导出副本 (副本内单独运行 `:app:kspAppDebugKotlin`), Node 990f9e35, Bun 3056b94, 排除规则为草案. 门禁修复 (0671ffb36): augment P5.2 (37b93d893) 删除 `runtime/api/augment` 后, `returned-objects.json` 的 19 个对象 (Flow, MailClient, ShellProcess, Color, Zip ...) 与 `check-shell-contract.mjs` 仍指向旧包, 返回对象成员从 1362 掉到 956, check.py 因 shell 契约脚本找不到文件而失败; 改为 `runtime/api/modules` 后矩阵 2739 行 (显式导出 1377, 返回对象成员 1362), 与入库 MATRIX.md (2729 行) 相比只多 `speech.speaker.*` 8 行 (3-Macaw P8.3, c31ea30ee) 与 UiSelector# 2 行 (selector), 两边共有行的状态 0 差异. 此时 pending: Node main 631 / Node Worker 763 / Bun 722 (下称 "现状"). 另有 7 个 Codex 结构检查脚本 (`check-runtime-ai / ai-agent / bridges / epub / mail / speech / tts.mjs`, CI 跑的 8 个中占 6 个) 解析旧包的注册写法, 新层没有这种写法, 只改路径仍失败, 见本节末 "门禁现状与题 12".

每组: 行 (显式导出, 返回对象成员另计) | 现状三列 (Node main / Worker / Bun) | 真机抽样 (P1.2 Redmi / P1.3 Xiaomi) 是否触及 | 选项与 pending 变化 (相对现状) | 改选返工. 推荐项都写了等价写法 (not-portable 的 runtime-native 项必填).

| 题 | 分组 | 理由 | 显式行 | 现状 main / Worker / Bun | 推荐 | 推荐后 pending 变化 |
|---|---|---|---:|---|---|---|
| 1 | Q5 local-shim | - | - | local-shim 格 136 / 62 / 62 | 允许, 须与 Rhino 一致且有测试 | 0 |
| 2 | D-9 运行时自带网络 / 加密 / 编码 / 定时器 | runtime-native | 30 | 排除 28 (另 2 local-shim) / 30 / 30 | 保持排除, http.client / buildRequest 改记 java-prototype | 0 |
| 3 | D-4 特权与双开应用操作 | security | 25 | pending (拒绝) 25 / pending 25 / pending (拒绝) 25 | denied | -25 / -25 / -25 |
| 4 | D-17 UI 线程与 Activity 内容视图 | ui-thread | 15 | pending 15 / 15 / 15 | 排除 | -15 / -15 / -15 |
| 5 | D-11 + D-12 Java 对象与句柄 | java-prototype | 5 + 13 | 排除 5 + pending 13 (拒绝 7) / 排除 5 + 13 / 排除 5 + 13 (拒绝 3) | 18 行排除 | -13 / -13 / -13 |
| 6 | Q31 句柄成员 5 行 | sync-only / java-prototype | 返回对象 5 | pending 5 / 5 / 5 | 排除 | -5 / -5 / -5 |
| 7 | D-13 JSOX 扩展 | rhino-only | 54 + 全局 2 | local-shim 56 / 排除 54 + pending 2 / 同 Worker | Worker / Bun 保持排除, 全局 randomInt / randomFloat 并入 | 0 / -2 / -2 |
| 8 | D-14 Rhino 引擎操作 | rhino-only | 4 | 覆盖 3 + 排除 1 / 排除 4 / 排除 4 | 拆分: engines.exec* 3 行不排除 | +1 / +3 / +3 |
| 9 | D-1 + D-6 + D-7 同步终端 | sync-only (console.build: java-prototype) | 1 + 7 + 3 | 排除 8 + pending 3 (拒绝 3) / 排除 8 + 3 (拒绝 3) / 排除 8 + 3 | 11 行排除 | -3 / -3 / -3 |
| 10 | D-8 + D-10 运行时自带 sleep / console | runtime-native | 4 + 11 | 覆盖 1 + 排除 3 / 排除 4 / 排除 4 + pending 11 | D-8 保持, D-10 Bun 排除 | 0 / 0 / -11 |
| 11 | D-2 + D-5 标识符与存储枚举 | security | 1 + 2 | pending 3 (拒绝 2) / 3 / 3 (拒绝 2) | denied | -3 / -3 / -3 |

全部按推荐: pending Node main 631 -> 568, Worker 763 -> 701, Bun 722 -> 646 (题 1 不改变现状数字). 只列不问: D-15 内部 / 标准导出 9 行 (Q13 (a) 已拍板, 保持); D-3 原始输入通道, D-16 Rhino 语言层, D-18 完整 XML 绑定 / 悬浮窗外框 / ActionBar / 同步 WebView 子类化, D-19 Node 可遮蔽 shim, D-20 非本主题 (宿主无显式导出, 0 行, 保留为说明项, 不进矩阵). 题 12 不属于白名单, 见本节末.

### 题 1 Q5: pending 项可否用 local-shim (运行时侧纯 JS 实现) 满足

- 现状 local-shim 格 Node main 136 / Worker 62 / Bun 62: colors 62 (三列, 经 `autojs6:colors`), 只在 Node main 的 jsox / Arrayx / Mathx / Numberx 56, cvt.bytes / fmt.bytes 12, base64 2, files 2, plugins 1, s13n 1.
- 后续 P3 / P4 可走 local-shim 的 pending: species 37, util 约 45 (ensure* / is* / 字符串与单位助手), colors 19 个纯颜色函数 (R21) 与 Color# 29, Bun pinyin 常量 10 (R3), cvt.bytes / fmt.bytes 在 Worker / Bun 各 6, util.morseCode / util.version* 等.
- 真机: P1.2 / P1.3 未专门抽 local-shim 行.
- (a) 允许, 须与 Rhino 行为一致且有对照测试, 矩阵标 local-shim (推荐): 现状不变; 纯 JS 行在 P3 / P4 不经桥接.
- (b) 只有 bridged 计为覆盖, local-shim 限 cvt / fmt / species / s13n: 这 4 个模块以外的现有 local-shim 改回 pending, Node main +123 / Worker +62 / Bun +62 (colors 62 x 3, jsox 系 56, base64 2, files 2, plugins 1); 这些行与 util / colors / Color# / pinyin 常量须改为宿主桥接 (每次调用一次跨进程), P3.2 / P4.2 返工大.

### 题 2 D-9 运行时自带网络 / 加密 / 编码 / 定时器 (runtime-native)

- 行 30: http 20 (get / post / postJson / postMultipart / put / del / delete / head / request 及其 Async, client, buildRequest; http.__okhttp__ 属 D-15), crypto.Key / KeyPair, base64.encode / decode, 全局 setTimeout / setInterval / setImmediate 及三个 clear. 现状: Node main 排除 28 (规则 native-http 18, http.get / request 与 6 个定时器由 `runtime-policy.json` node.nativeModules 自动判定为 node:http / node:timers), base64 2 行 Node main 为 local-shim; Worker / Bun 排除 30. 自动判定还有 util.format (Node main / Worker; Bun pending, 归 util 批次).
- 代表: http.get, http.postJsonAsync, crypto.Key, setTimeout. 真机: 未触及.
- (a) 保持排除 runtime-native (推荐): 等价写法 全局 fetch (Node / Bun), Node `autojs6:fetch` (宿主 OkHttp, 代理 / 证书 / 网络策略随宿主, 只有 GET / POST), node:crypto / WebCrypto, Buffer / atob / btoa, 全局定时器; http.client / http.buildRequest 返回 OkHttp 对象, 理由改记 java-prototype (状态不变). pending 0.
- (b) http 补入口: Node / Bun 按 Rhino 函数名桥接宿主 OkHttp: pending +20 / +20 / +20, P3.2 / P4.2 新增网络批次 (宿主 provider 要支持 PUT / DELETE / HEAD 与 multipart).

### 题 3 D-4 特权与双开应用操作 (security)

- 行 25: app.isDualInstalled / launchDual / launchDualApp / launchDualAppDetailsSettings / launchDualPackage / launchDualSettings / openDualAppSetting / openDualAppSettings / openDualUrl / startDualActivity / uninstallDual / killDual / kill (13), 及除 openDualUrl 外的 12 个全局放置. 现状: Node main 与 Bun 只有拒绝入口 (声明 `Promise<never>`, Node app 选项的 root / shizuku / dual 为 never), Worker 无 app 模块 (R8).
- 依据: 宿主经 Shizuku / Root / 回退 shell 执行 `am force-stop` 与双开用户操作 (App.kt); Codex bun-automation 报告写明特权 / 双开操作显式拒绝. 真机: 未触及 (拒绝入口).
- (a) denied / security (推荐): 等价写法 已授权的 shell (Node 能力 shell.root / shell.shizuku, Bun 授权 shell.root / shell.shizuku) 执行 `am force-stop <包名>` 等命令; pending -25 / -25 / -25.
- (b) 保持 pending, P3.1 / P4.1 在 app 模块实现特权入口 (复用 shell.root / shell.shizuku 或新增授权): 0; 13 个路由 x 2 运行时 + Worker app 模块, 需 Root / Shizuku 真机验证.
- (c) 拆分: app.kill / killDual 与全局 kill / killDual (4 行) 补入口 (经 Shizuku / Root 授权), 其余 21 行排除: -21 / -21 / -21.

### 题 4 D-17 UI 线程与 Activity 内容视图 (ui-thread)

- 行 15: ui.layout / layoutFile / setContentView / run / post / isUiThread / finish / R / Widget / registerWidget / emitter / root / useAndroidLayout / findById / findByStringId; 现状三列 pending.
- 依据: 依赖 Rhino UI 线程与脚本 Activity 的内容视图; 运行时的可移植 ui 是 JSON 布局契约 (ui.showLayout / inflate / findView / createView / bind / state, compose.mount), UI 线程由运行时自动切换. 真机: P1.2 UI 族 11 个用例覆盖 ui.inflate / findView 与上述运行时扩展, 三列通过; 本组行运行时无入口.
- (a) not-portable / ui-thread (推荐), 等价写法如上: -15 / -15 / -15.
- (b) 拆分: findById / findByStringId 归 P3.3 / P4.3, 核对语义后用 memberAliases 对到 ui.findView, 其余 13 行排除: -13 / -13 / -13.
- (c) 保持 pending, P3.3 / P4.3 设计 Activity 内容视图桥接: 0; 新增 UI 批次.
- R7 的另外 16 行 (keepScreenOn, backgroundColor, statusBar* / navigationBar* 颜色 / 高度 / 图标) 不在本组, 仍归 P3.3 / P4.3 补入口.

### 题 5 D-11 + D-12 Java 对象与句柄 (java-prototype)

- D-11 5 行: unwrapJavaObject, util.unwrapJavaObject, isJavaObject, isJavaClass, isJavaPackage (已在规则 java-prototype, 三列排除). kotlin-class 返回对象成员 1070 行已由 Q17 (a) 拍板, 不再问.
- D-12 13 行: files.open / files.toFile / 全局 open (Node main / Bun 拒绝入口), s13n.color / point / throwable / time (Node main 拒绝入口), dialogs.build, notice.builder / getBuilder, shell.fromIntent, util.java.mapToObject / objectToMap (无入口); 现状三列 pending.
- 依据: 返回或接收 Java 对象 (文件句柄, Color / Point / Throwable / TimeUnit, 对话框与通知构造器, Intent, Java Map). 真机: P1.3 Java 互操作 12 个用例实测运行时 java 门面 (java.type / new / instanceOf / newArray ...) 三列可用, 即本组的等价写法.
- 等价写法: files.read / write 与运行时 fs 流; 颜色数值 (colors.*), {x, y}, Error, 毫秒数; dialogs.alert / select / progress; notifications.notify(options); app.startActivity 或 shell 执行 `am start`; java 模块句柄.
- (a) 18 行全部 not-portable / java-prototype (推荐): -13 / -13 / -13.
- (b) 16 行排除, util.java.mapToObject / objectToMap 保持 pending 归 P3.2 / P4.2 (同 R19 用 java 模块句柄做 JSON 互转): -11 / -11 / -11.
- (c) 只排除 D-11 5 行 (现状), D-12 13 行由 P3 / P4 各族补句柄式入口: 0.

### 题 6 Q31 句柄成员 5 行 (返回对象)

- Flow#await / Flow#sync (阻塞到流程结束, sync-only; 等价写法: 运行时 Flow 是 thenable, `await flow` 或 flow.run()), ShellProcess#getErrorStream / getInputStream / getOutputStream (返回 Java 流, java-prototype; 等价写法: 运行时句柄的 stdout / stderr 流与 write / writeLine / closeInput). 现状三列 pending.
- 真机: P1.2 shell 族实测运行时 ShellProcess 的 write / writeLine / readLine / closeInput / stdout / stderr 三列可用; P1.3 flow 族实测 Flow 17 个成员三列可用 (运行时没有 await / sync).
- (a) 5 行排除 (推荐; Q31 (a) 已定为附录 D 候选): -5 / -5 / -5.
- (b) 保持 pending, P3 / P4 在运行时句柄补同名成员 (await 返回 Promise, 三个 getter 返回流句柄): 0.
- Q31 其余 9 行 (EventEmitter 别名 5 个, isAlive, ShellProcess / EpubBook#toString, Storage#size) 按 Q31 (a) 保持 pending 归 P3 / P4, 不在本题.

### 题 7 D-13 JSOX 扩展 (rhino-only)

- 行 54: Arrayx 15, Mathx 23, Numberx 14, jsox.extend / extendAll; 另全局 randomInt / randomFloat (Mathx 的全局放置) 2 行. 现状: Node main 56 行 local-shim (`jsox` 显式函数模块); Worker / Bun 54 行规则排除, 全局 2 行 pending.
- 依据: Rhino 内置对象原型扩展, 不是宿主 API; Node 主线程的 jsox 是显式函数形式的便利模块, Worker 只解析宿主模块与 Node 内建模块. 真机: 未触及.
- (a) Worker / Bun 保持排除 rhino-only, 规则并入 randomInt / randomFloat (推荐), 等价写法: 标准 Math / Array / Number 方法, Node 主线程 `require("jsox")`: 0 / -2 / -2.
- (b) Worker 补入口 (Worker 可导入 jsox 本地模块), Bun 排除: 0 / +54 / -2.
- (c) Worker 与 Bun 都补入口 (Bun 新增 jsox 本地命名空间, 依题 1): 0 / +54 / +54.

### 题 8 D-14 Rhino 引擎操作 (rhino-only)

- 行 4: engines.execScript / execScriptFile / execAutoFile, plugins.load. 现状: Node main 的 execScript / execScriptFile 已桥接 (返回 EngineSnapshot), plugins.load 为 local-shim (本地 ./plugins 目录扫描), execAutoFile 排除; Worker / Bun 4 行规则排除.
- 依据: 草案理由 "操作 Rhino 引擎本身" 与 Node 主线程已桥接 execScript 的事实不符; 宿主启动脚本是宿主能力, 参数与返回可序列化. 真机: 未触及.
- (a) 拆分 (推荐): engines.execScript / execScriptFile / execAutoFile 不再排除, 归 P3.1 / P4.1 与 engines 其余 pending 行 (all / myEngine / stopAll 等) 同批; plugins.load 在 Worker / Bun 保持 rhino-only (等价写法: Node 主线程 plugins 本地扫描, Bun 运行时自有模块): +1 / +3 / +3.
- (b) 保持草案: 0.
- (c) 4 行都不排除: +1 / +4 / +4.

### 题 9 D-1 + D-6 + D-7 同步终端 (sync-only)

- D-1 auto.waitFor (denied / sync-only, 已在规则), D-6 threads.disposable / interrupt / pool / start, sync, continuation.create / enabled (not-portable / sync-only, 已在规则), D-7 console.input / rawInput / build (Node main / Worker 拒绝入口, Bun 无入口; 新候选, console.build 返回 Java 配置器, 理由改记 java-prototype).
- 等价写法: flow waitFor* 与 accessibility.ensureEnabled; worker_threads / Bun Worker; await; dialogs.input / rawInput (三列已桥接), 运行时控制台窗口控制 (Node console.* / Bun console.control). 真机: P1.2 dialogs 族 8 个桥接行 (含 input / rawInput) 三列通过.
- (a) 11 行全部排除 (推荐): -3 / -3 / -3.
- (b) D-1 / D-6 排除, D-7 保持 pending, P3 / P4 在 console 模块补异步 input / rawInput (读控制台窗口的输入框): 0.

### 题 10 D-8 + D-10 运行时自带 sleep 与 console 输出 (runtime-native)

- D-8 sleep / keepAlive / loop / timers.keepAlive (已在规则; Node main 的 sleep 经 `autojs6:compat` 已覆盖, 规则被遮蔽).
- D-10 Bun 的 console.assert / error / info / log / print / printAllStackTrace / time / timeEnd / trace / verbose / warn 11 行 (Node main / Worker 已桥接, Bun pending; Bun 文档写明原生 console 与 host-client 分开使用). 真机: 未触及.
- (a) D-8 保持, D-10 Bun 11 行排除 runtime-native (推荐), 等价写法: Bun 全局 console (print 用 log, verbose 用 debug, printAllStackTrace 用 error(err.stack)): 0 / 0 / -11.
- (b) D-8 保持, D-10 保持 pending, P4 在 console.control 补 11 个输出函数 (写入本执行的控制台窗口): 0.

### 题 11 D-2 + D-5 标识符与存储枚举 (security)

- D-2 device.getSharedDeviceId (经伴生 deviceid provider 读跨应用共享的设备 ID; 三列 pending). D-5 storages.names / all (Node main / Bun 拒绝入口, Worker 无 storages; Bun 文档: 存储按执行应用包名与脚本目录分组, names / all 不提供全局命名空间枚举). 真机: 未触及.
- (a) 3 行 denied / security (推荐), 等价写法: 设备标识符无 (附录 D 来源 1); 存储按名称 create / open: -3 / -3 / -3.
- (b) D-5 排除, D-2 保持 pending 归 P3.1 / P4.1 桥接: -2 / -2 / -2.
- (c) 3 行保持 pending (D-5 在 P3.2 / P4.2 补只列当前分组的 names / all): 0.

### 门禁现状与题 12

- HEAD 上 `check.py --matrix --strict-arity --docs-support` (含 Ace) 在门禁修复后仍退出 1, 原因都是其他主题新增的导出缺文档 / d.ts / Ace: auto.clearFlags (selector, cde3fee03; selector P4 写声明与文档, 进行中) 与 speech.speaker 及 enroll / enrollAsync / identify / identifyAsync / list / remove / verify / verifyAsync (3-Macaw P8.3, c31ea30ee; 该子会话暂停): 文档名缺 7, 表面检查失败 8 条. 题 12 问 P1.4 验收如何对待它们.
- 7 个 Codex 结构检查脚本 (check-runtime-ai / ai-agent / bridges / epub / mail / speech / tts, CI 跑的 8 个中占 6 个; 其余 17 个与 2 个 test-web 脚本通过) 用正则读旧包的 `selfAssignmentFunctions` / `FUNCTION_ASSIGNMENTS` / OpenCC 注册等写法, 新层是 `@ScriptFunction` 注解, 只改路径仍失败 (已试); 宜改读 exports.json (同一次运行的显式导出). 不在 P1.4 验收内, 登记待决 (见附录 C) 由调度会话安排.
