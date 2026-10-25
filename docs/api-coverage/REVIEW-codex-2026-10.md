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

## 2. 不一致清单

列: 编号 | 模块 | 名称 | Node d.ts | HOST-API | facade | Bun | 文档 / d.ts / Ace | 建议归属.

| 编号 | 模块 | 名称 | Node d.ts | HOST-API | facade | Bun | 文档 / d.ts / Ace | 建议归属 |
|---|---|---|---|---|---|---|---|---|
| R1 | (门禁) | 17 个 Codex 结构检查脚本未进 CI | - | - | 本地全部通过 | - | - | P0.6 (api-coverage.yml 加步骤; 需 Node / Bun 仓库 checkout, 与 Q14 同一处) |
| R2 | console | setAvoidStatusBar, setColorful, setInputVisible, setTimeFormat, setTimeVisible (宿主 2026-09-08 85dcf99e3a 已有, 早于 Codex 批次) | 无 | 无 | 无 | 无 (console.control 31 个方法不含) | 有 | P3 / P4 控制台批次 (随 console.control 补 5 个方法) |
| R3 | pinyin | MODE_NORMAL, MODE_PLACENAME, MODE_PLACE_NAME, MODE_SURNAME, STYLE_FIRST_LETTER, STYLE_INITIALS, STYLE_NORMAL, STYLE_TO3NE, STYLE_TONE, STYLE_TONE2 | 有 (数值常量) | - | 有 | 无 (text.pinyin 只有 6 个方法) | 有 | P4 (Bun 本地常量, local-shim) |
| R4 | console | assert, build, error, info, input, log, print, printAllStackTrace, rawInput, time, timeEnd, trace, verbose, warn (14 行) | 有 (autojs6:console) | 未列 | 有 | 无 (Bun 有原生全局 console) | 有 | 附录 D 候选: 输出类 runtime-native (等价: Bun 全局 console), input / rawInput / build sync-only (Node Worker 也显式失败); P1.4 拍板 |
| R5 | (19 个模块) | HOST-API 一至四节状态表未列: ai, android, bridge, compat, compose, console, epub, flow, host, mail, opencc, pinyin, pinyin4j, profile, screen_metrics, screen_recorder, speech, tasks, tts (Codex 各批只在文末追加英文小节) | 有 | 缺表格行 (矩阵按 "未列即 bridged" 推导) | 有 | - | - | P3.6 (HOST-API 与 COMPATIBILITY_PROFILE 同步时补状态行); 矩阵结论不受影响 |

## 3. 已直接修

| 提交 | 仓库 | 内容 |
|---|---|---|
| 6260c9788 | 宿主 | `check-runtime-tts.mjs` 断言改为 `session.pending.values.concurrentSnapshot()` (C36 后的源码), CI 中该步骤恢复通过 |

## 4. 超出分母的运行时扩展

运行时插件提供而宿主没有对应显式导出的入口, 矩阵无法也不需要为其设行 (分母是宿主导出, D1); 文档在 `runtimeApis.md` 与插件文档. 如需纳入 (例如 @ScriptPrototype 的 media / recorder) 见 Q18.

- 录屏: Node `screen_recorder` / `autojs6:screen_recorder` (start / stop / cancel / getStatus, main + Worker), Bun `screenRecorder` (screen.record), Worker `media_projection` / `recorder` (runtime-node-recording, runtime-video-worker).
