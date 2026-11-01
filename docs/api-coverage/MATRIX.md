<!-- runtime-matrix: schema=autojs6-runtime-matrix-v1 dataSource=ksp rows=3582 -->
# AutoJs6 脚本 API 运行时覆盖矩阵

由 `tools/api-coverage/runtime_matrix.py` 生成 (`python tools/api-coverage/check.py --matrix`), 不要手改. 定义见 `docs/api-coverage/ROADMAP.md` 第 4 节, 机读版本是 CI 产物 `build/reports/api-coverage/runtime-matrix.json`, 人工标注 (别名映射, 排除理由, 等价写法) 只在 `tools/api-coverage/runtime-policy.json`; 排除规则已拍板 (D12 / Q1, ROADMAP 附录 D), 新增排除先登记待决; pending 只能减少 (`tools/api-coverage/pending-baseline.json`).

数据源: ksp, 3582 行 (显式导出 2026, 原型成员 205, 返回对象成员 1351).

## 状态汇总

| 列 | bridged | local-shim | capability-gated | not-portable | denied | pending |
|---|---:|---:|---:|---:|---:|---:|
| Node main | 1685 | 369 | 0 | 1528 | 0 | 0 |
| Node Worker | 1685 | 366 | 0 | 1531 | 0 | 0 |
| Bun | 0 | 377 | 1674 | 1531 | 0 | 0 |

| 列 | 取值 |
|---|---|
| doc | none 592 / mention 278 / heading 2712 / signature 0 / example 0 |
| dts | none 597 / name 1738 / arity 314 / typed 933 |
| ace | none 1002 / semantic 946 / static 1634 |

类型比对 (P2.4, ksp: script-modules.json 的参数 / 返回类型与 d.ts, 见 tools/api-coverage/type-map.json; 一致的行 dts 记 typed): typed 1016 / differs 0 / unmapped 252 / no-dts 0 行; 其余行没有 Kotlin 类型 (@ScriptInstall 名称, 原型成员, 返回对象成员).

排除理由缺失: 0. Bun 缺能力名: 0. 策略冲突: 无.

拒绝入口 (运行时声明只返回 never / Promise<never>, 记 pending, 排除须由规则给理由; Q26): Node main 2 / Node Worker 2 / Bun 2. 句柄类型 (runtime-policy.json returnedObjects, Q27) 的成员按运行时类型逐个比对.

名称门禁暂缓 (policy.json deferredNameGate; doc / d.ts / Ace 列照实记录, 缺口不计入 check.py 失败): imgui 588 行 (Q115 (a): 文档 / d.ts / Ace 由 P5.2f-2 补, 待 imgui R 系列稳定).

## 排除规则

| 规则 | 状态 / 理由 | Node main | Node Worker | Bun | 等价写法 |
|---|---|---:|---:|---:|---|
| http-java-objects | not-portable: java-prototype | 2 | 2 | 2 | fetch / Node autojs6:fetch request options (method, headers, body) instead of the OkHttp client and request objects |
| imgui-rhino-only | not-portable: rhino-only | 587 | 587 | 587 | engines.execScript of a Rhino script that opens the window, events.broadcast between the scripts |
| internal-exports | not-portable: rhino-only | 10 | 10 | 10 |  |
| java-introspection | not-portable: java-prototype | 3 | 3 | 3 | java module handles (java.callInstance(handle, "getClass")) |
| java-prototype | not-portable: java-prototype | 5 | 5 | 5 | java module handles (java.create / java.callInstance / java.callStatic) |
| java-prototype-layout-inflater | not-portable: java-prototype | 1 | 1 | 1 | ui.inflate / ui.createView |
| java-prototype-objects | not-portable: java-prototype | 879 | 879 | 879 |  |
| native-base64 | not-portable: runtime-native | 0 | 2 | 2 | Buffer.from(text).toString("base64") / Buffer.from(data, "base64"), atob / btoa |
| native-crypto | not-portable: runtime-native | 8 | 8 | 8 | node:crypto KeyObject / generateKeyPair, WebCrypto (globalThis.crypto.subtle) |
| native-http | not-portable: runtime-native | 16 | 16 | 18 | global fetch (Node / Bun), node:http; autojs6:fetch for host proxy / certificate policy |
| native-memory-watch | not-portable: runtime-native | 1 | 1 | 1 | FinalizationRegistry (a callback once the object is collected), node:v8 writeHeapSnapshot / getHeapStatistics; in Bun bun:jsc heapStats / generateHeapSnapshot |
| native-timers | not-portable: runtime-native | 0 | 0 | 12 | global setTimeout / setInterval / setImmediate and their clear functions |
| obsoleted-functions | not-portable: obsoleted | 2 | 2 | 2 |  |
| rhino-engine | not-portable: rhino-only | 0 | 1 | 1 |  |

## 最低 API 标注

状态不变, 不算 pending 也不算排除: 该入口在标注的 API 级别以下不可用 (P6.1, runtime-policy.json minApi).

| 规则 | minApi | 匹配 | Node main | Node Worker | Bun | 理由 |
|---|---:|---|---:|---:|---:|---|
| bun-plugin-min-sdk | 33 | `*` | 0 | 0 | 2051 | Bun 插件 minSdk 33: 官方二进制调用 close_range, Android 12L 及更早系统的应用 seccomp 以 SIGSYS 结束进程, API 28 实测 INSTALL_FAILED_OLDER_SDK |

## 按模块汇总

运行时列为 覆盖 (bridged / local-shim / capability-gated) / 排除 (not-portable / denied) / pending; doc 为有标题的行数, d.ts 为声明可解析的行数, 类型 (ksp) 为参数 / 返回类型与 script-modules.json 一致的行数 / 有可比类型的行数 (P2.4), Ace 为默认与全组补全都命中的行数.

| 模块 | 行 | doc 标题 | d.ts | 类型 (ksp) | Ace | Node main | Node Worker | Bun |
|---|---:|---:|---:|---:|---:|---|---|---|
| (global) | 208 | 206 | 205 | 154 / 154 | 205 | 195 / 13 / 0 | 195 / 13 / 0 | 195 / 13 / 0 |
| AgentRun# | 14 | 7 | 14 | - | 14 | 14 / 0 / 0 | 14 / 0 / 0 | 14 / 0 / 0 |
| AiSession# | 11 | 11 | 11 | - | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| App# | 10 | 10 | 10 | - | 10 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| Arrayx | 15 | 15 | 15 | 15 / 15 | 15 | 15 / 0 / 0 | 15 / 0 / 0 | 15 / 0 / 0 |
| Canvas# | 54 | 54 | 54 | - | 0 | 0 / 54 / 0 | 0 / 54 / 0 | 0 / 54 / 0 |
| Color# | 29 | 29 | 29 | - | 29 | 29 / 0 / 0 | 29 / 0 / 0 | 29 / 0 / 0 |
| ComposeFloatyWindow# | 18 | 13 | 18 | - | 18 | 18 / 0 / 0 | 18 / 0 / 0 | 18 / 0 / 0 |
| ComposeModifier# | 20 | 20 | 20 | - | 20 | 20 / 0 / 0 | 20 / 0 / 0 | 20 / 0 / 0 |
| ComposeNode# | 28 | 28 | 28 | - | 28 | 28 / 0 / 0 | 28 / 0 / 0 | 28 / 0 / 0 |
| ComposeRef# | 1 | 0 | 1 | - | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| ComposeSession# | 7 | 7 | 7 | - | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| ComposeState# | 1 | 1 | 1 | - | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| ConsoleConfigurator# | 52 | 50 | 52 | - | 0 | 52 / 0 / 0 | 52 / 0 / 0 | 52 / 0 / 0 |
| ContinuationCreator# | 3 | 3 | 3 | - | 3 | 0 / 3 / 0 | 0 / 3 / 0 | 0 / 3 / 0 |
| Database# | 51 | 51 | 51 | - | 2 | 0 / 51 / 0 | 0 / 51 / 0 | 0 / 51 / 0 |
| Dialog# | 96 | 19 | 96 | - | 3 | 0 / 96 / 0 | 0 / 96 / 0 | 0 / 96 / 0 |
| EpubBook# | 14 | 14 | 14 | - | 14 | 14 / 0 / 0 | 14 / 0 / 0 | 14 / 0 / 0 |
| EpubReaderSession# | 34 | 34 | 34 | - | 32 | 0 / 34 / 0 | 0 / 34 / 0 | 0 / 34 / 0 |
| FloatyWindow# | 12 | 12 | 12 | - | 12 | 12 / 0 / 0 | 12 / 0 / 0 | 12 / 0 / 0 |
| Flow# | 106 | 25 | 106 | - | 106 | 106 / 0 / 0 | 106 / 0 / 0 | 106 / 0 / 0 |
| ImageFeatures# | 10 | 10 | 10 | - | 10 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| ImageWrapper# | 15 | 15 | 15 | - | 6 | 0 / 15 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| InjectableWebClient# | 2 | 2 | 2 | - | 0 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| InjectableWebView# | 1 | 1 | 1 | - | 0 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| IntentTask# | 19 | 0 | 19 | - | 0 | 0 / 19 / 0 | 0 / 19 / 0 | 0 / 19 / 0 |
| MailClient# | 53 | 53 | 53 | - | 53 | 53 / 0 / 0 | 53 / 0 / 0 | 53 / 0 / 0 |
| Mat# | 3 | 0 | 3 | - | 0 | 0 / 3 / 0 | 0 / 3 / 0 | 0 / 3 / 0 |
| MatchingResult# | 16 | 16 | 16 | - | 16 | 0 / 16 / 0 | 0 / 16 / 0 | 0 / 16 / 0 |
| Mathx | 23 | 23 | 23 | 23 / 23 | 23 | 23 / 0 / 0 | 23 / 0 / 0 | 23 / 0 / 0 |
| Numberx | 14 | 14 | 14 | 14 / 14 | 14 | 14 / 0 / 0 | 14 / 0 / 0 | 14 / 0 / 0 |
| ObjectFrame# | 15 | 15 | 15 | - | 15 | 0 / 15 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| OcrResult# | 6 | 6 | 6 | - | 6 | 4 / 2 / 0 | 4 / 2 / 0 | 4 / 2 / 0 |
| PReadableTextFile# | 5 | 5 | 5 | - | 0 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| PWritableTextFile# | 6 | 6 | 6 | - | 0 | 0 / 6 / 0 | 0 / 6 / 0 | 0 / 6 / 0 |
| PngQuantizationFileResult# | 5 | 3 | 5 | - | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| PngQuantizationResult# | 5 | 3 | 5 | - | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| RawWindow# | 12 | 12 | 12 | - | 12 | 12 / 0 / 0 | 12 / 0 / 0 | 12 / 0 / 0 |
| ScreenCaptureOptions# | 5 | 5 | 5 | - | 0 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| ScriptEngine# | 16 | 16 | 16 | - | 0 | 0 / 16 / 0 | 0 / 16 / 0 | 0 / 16 / 0 |
| ScriptExecution# | 12 | 12 | 12 | - | 0 | 0 / 12 / 0 | 0 / 12 / 0 | 0 / 12 / 0 |
| ShellProcess# | 25 | 12 | 25 | - | 25 | 25 / 0 / 0 | 25 / 0 / 0 | 25 / 0 / 0 |
| ShellResult# | 5 | 5 | 5 | - | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| Storage# | 12 | 12 | 12 | - | 12 | 12 / 0 / 0 | 12 / 0 / 0 | 12 / 0 / 0 |
| ThemeColor# | 10 | 0 | 10 | - | 0 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| TimedTask# | 40 | 0 | 40 | - | 0 | 0 / 40 / 0 | 0 / 40 / 0 | 0 / 40 / 0 |
| TtsUtterance# | 23 | 23 | 23 | - | 21 | 0 / 23 / 0 | 0 / 23 / 0 | 0 / 23 / 0 |
| UiObject# | 141 | 139 | 141 | - | 140 | 0 / 141 / 0 | 0 / 141 / 0 | 0 / 141 / 0 |
| UiObjectCollection# | 70 | 69 | 70 | - | 70 | 0 / 70 / 0 | 0 / 70 / 0 | 0 / 70 / 0 |
| UiSelector# | 186 | 186 | 186 | - | 186 | 0 / 186 / 0 | 0 / 186 / 0 | 0 / 186 / 0 |
| VolatileDispose# | 3 | 3 | 3 | - | 3 | 3 / 0 / 0 | 3 / 0 / 0 | 3 / 0 / 0 |
| WebSocket# | 24 | 24 | 24 | - | 0 | 24 / 0 / 0 | 24 / 0 / 0 | 24 / 0 / 0 |
| WrappedBarcode# | 22 | 22 | 22 | - | 22 | 0 / 22 / 0 | 0 / 22 / 0 | 0 / 22 / 0 |
| YoloDetector# | 2 | 2 | 2 | - | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| Zip# | 21 | 21 | 21 | - | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| ai | 6 | 6 | 6 | 5 / 5 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| app | 43 | 43 | 43 | 41 / 41 | 43 | 43 / 0 / 0 | 43 / 0 / 0 | 43 / 0 / 0 |
| auto | 38 | 38 | 38 | 35 / 35 | 38 | 38 / 0 / 0 | 38 / 0 / 0 | 38 / 0 / 0 |
| autojs | 21 | 21 | 21 | 21 / 21 | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| autojs.version | 7 | 7 | 7 | 7 / 7 | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| automator | 54 | 54 | 54 | 34 / 34 | 54 | 54 / 0 / 0 | 54 / 0 / 0 | 54 / 0 / 0 |
| barcode | 4 | 4 | 4 | 2 / 2 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| base64 | 2 | 2 | 2 | 2 / 2 | 2 | 2 / 0 / 0 | 0 / 2 / 0 | 0 / 2 / 0 |
| colors | 82 | 82 | 82 | 81 / 81 | 82 | 82 / 0 / 0 | 82 / 0 / 0 | 82 / 0 / 0 |
| colors.theme | 9 | 9 | 9 | 9 / 9 | 9 | 9 / 0 / 0 | 9 / 0 / 0 | 9 / 0 / 0 |
| compose | 71 | 71 | 71 | - | 71 | 71 / 0 / 0 | 71 / 0 / 0 | 71 / 0 / 0 |
| console | 56 | 56 | 56 | 15 / 15 | 56 | 56 / 0 / 0 | 56 / 0 / 0 | 56 / 0 / 0 |
| continuation | 4 | 3 | 4 | 2 / 2 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| crypto | 8 | 7 | 8 | - | 8 | 0 / 8 / 0 | 0 / 8 / 0 | 0 / 8 / 0 |
| cvt.bytes | 6 | 6 | 6 | 6 / 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| device | 86 | 86 | 86 | 25 / 25 | 86 | 86 / 0 / 0 | 86 / 0 / 0 | 86 / 0 / 0 |
| dialogs | 9 | 9 | 9 | 1 / 1 | 9 | 9 / 0 / 0 | 9 / 0 / 0 | 9 / 0 / 0 |
| engines | 8 | 8 | 8 | 7 / 7 | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| epub | 21 | 21 | 21 | - | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| events | 37 | 34 | 36 | - | 36 | 36 / 1 / 0 | 36 / 1 / 0 | 36 / 1 / 0 |
| files | 34 | 34 | 34 | 4 / 4 | 34 | 34 / 0 / 0 | 34 / 0 / 0 | 34 / 0 / 0 |
| files.batchRename | 17 | 17 | 17 | 7 / 7 | 17 | 17 / 0 / 0 | 17 / 0 / 0 | 17 / 0 / 0 |
| floaty | 8 | 8 | 8 | 6 / 6 | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| flow | 55 | 55 | 55 | 55 / 55 | 55 | 55 / 0 / 0 | 55 / 0 / 0 | 55 / 0 / 0 |
| fmt.bytes | 6 | 6 | 6 | 6 / 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| gallery | 11 | 11 | 11 | - | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| http | 21 | 20 | 20 | 20 / 20 | 20 | 0 / 21 / 0 | 0 / 21 / 0 | 0 / 21 / 0 |
| images | 82 | 82 | 82 | 79 / 79 | 82 | 82 / 0 / 0 | 82 / 0 / 0 | 82 / 0 / 0 |
| imgui | 588 | 0 | 0 | - | 0 | 0 / 588 / 0 | 0 / 588 / 0 | 0 / 588 / 0 |
| installer | 16 | 16 | 16 | - | 16 | 16 / 0 / 0 | 16 / 0 / 0 | 16 / 0 / 0 |
| jsox | 2 | 2 | 2 | 2 / 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| keys | 12 | 12 | 12 | 12 / 12 | 12 | 12 / 0 / 0 | 12 / 0 / 0 | 12 / 0 / 0 |
| mail | 56 | 56 | 56 | - | 56 | 56 / 0 / 0 | 56 / 0 / 0 | 56 / 0 / 0 |
| media | 9 | 9 | 9 | - | 9 | 9 / 0 / 0 | 9 / 0 / 0 | 9 / 0 / 0 |
| mediainfo | 7 | 7 | 7 | 3 / 3 | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| memory | 5 | 5 | 5 | 5 / 5 | 5 | 4 / 1 / 0 | 4 / 1 / 0 | 4 / 1 / 0 |
| mime | 5 | 5 | 5 | - | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |
| notice | 7 | 7 | 7 | 7 / 7 | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| notice.channel | 6 | 6 | 6 | 6 / 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| ocr | 6 | 6 | 6 | 5 / 5 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| ocr.mlkit | 2 | 2 | 2 | 2 / 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| ocr.paddle | 2 | 2 | 2 | 2 / 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| ocr.rapid | 2 | 2 | 2 | 2 / 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| opencc | 33 | 33 | 33 | 33 / 33 | 33 | 33 / 0 / 0 | 33 / 0 / 0 | 33 / 0 / 0 |
| pinyin | 16 | 16 | 16 | 5 / 5 | 16 | 16 / 0 / 0 | 16 / 0 / 0 | 16 / 0 / 0 |
| pinyin4j | 3 | 3 | 3 | 3 / 3 | 3 | 3 / 0 / 0 | 3 / 0 / 0 | 3 / 0 / 0 |
| plugins | 4 | 4 | 4 | 4 / 4 | 4 | 4 / 0 / 0 | 3 / 1 / 0 | 3 / 1 / 0 |
| plugins.tools | 4 | 4 | 4 | 1 / 1 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| powerManager | 2 | 2 | 2 | 2 / 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| qrcode | 4 | 4 | 4 | 2 / 2 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| recorder | 7 | 7 | 7 | - | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| s13n | 5 | 5 | 5 | 4 / 4 | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |
| sensors | 21 | 21 | 21 | - | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| settings | 4 | 4 | 4 | 4 / 4 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| shell | 11 | 11 | 11 | 11 / 11 | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| shizuku | 13 | 8 | 13 | 8 / 8 | 13 | 13 / 0 / 0 | 13 / 0 / 0 | 13 / 0 / 0 |
| species | 37 | 37 | 37 | 37 / 37 | 37 | 37 / 0 / 0 | 37 / 0 / 0 | 37 / 0 / 0 |
| speech | 17 | 17 | 17 | - | 17 | 17 / 0 / 0 | 17 / 0 / 0 | 17 / 0 / 0 |
| speech.models | 8 | 8 | 8 | - | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| speech.speaker | 8 | 8 | 8 | - | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| sqlite | 1 | 1 | 1 | 1 / 1 | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| storages | 5 | 5 | 5 | 5 / 5 | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |
| sysprops | 4 | 4 | 4 | 4 / 4 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| tasks | 20 | 20 | 20 | 17 / 17 | 20 | 20 / 0 / 0 | 20 / 0 / 0 | 20 / 0 / 0 |
| terminal | 20 | 20 | 20 | - | 20 | 20 / 0 / 0 | 20 / 0 / 0 | 20 / 0 / 0 |
| threads | 8 | 8 | 8 | 4 / 4 | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| timers | 18 | 18 | 18 | 10 / 10 | 18 | 12 / 6 / 0 | 12 / 6 / 0 | 12 / 6 / 0 |
| toast | 1 | 1 | 1 | 1 / 1 | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| tts | 35 | 35 | 35 | 35 / 35 | 35 | 35 / 0 / 0 | 35 / 0 / 0 | 35 / 0 / 0 |
| ui | 40 | 36 | 36 | 25 / 25 | 36 | 35 / 5 / 0 | 35 / 5 / 0 | 35 / 5 / 0 |
| util | 54 | 54 | 54 | 47 / 47 | 54 | 48 / 6 / 0 | 48 / 6 / 0 | 48 / 6 / 0 |
| util.inspect | 2 | 2 | 2 | 2 / 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| util.java | 6 | 6 | 6 | 3 / 3 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| util.morseCode | 3 | 3 | 3 | 3 / 3 | 3 | 3 / 0 / 0 | 3 / 0 / 0 | 3 / 0 / 0 |
| util.version | 1 | 1 | 1 | 1 / 1 | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| util.versionCodes | 4 | 4 | 4 | 4 / 4 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| web | 4 | 4 | 4 | 3 / 3 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| workManager | 11 | 11 | 11 | 9 / 9 | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| yolo | 1 | 1 | 1 | 1 / 1 | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| zip | 5 | 5 | 5 | 5 / 5 | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |

## 差异行

只列至少一个运行时列不是覆盖状态的行.

### (global)

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `__autojs6Tsx` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `__autojs6TsxFragment` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `clearImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `clearInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `clearTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `isJavaClass` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isJavaPackage` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `setImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `setInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `setTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `toString` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `unwrapJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |

### App#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `App#alias` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#ensureInstalled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#getAppName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#getAppNameEn` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#getAppNameZh` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#isInstalled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#launch` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#launchSettings` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#packageName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `App#uninstall` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### Canvas#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Canvas#clipPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#clipRect` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#concat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawARGB` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawArc` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawBitmap` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawBitmapMesh` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawCircle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawImage` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawLine` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawLines` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawOval` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawPaint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawPicture` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawPoint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawPoints` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawPosText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawRGB` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawRect` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawRoundRect` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawTextOnPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawTextRun` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#drawVertices` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getAndroidCanvas` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getClipBounds` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getDensity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getDrawFilter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getMatrix` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getMaximumBitmapHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getMaximumBitmapWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getSaveCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#getWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#isHardwareAccelerated` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#isOpaque` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#quickReject` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#restore` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#restoreToCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#rotate` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#save` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#saveLayer` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#saveLayerAlpha` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#scale` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#setBitmap` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#setCanvas` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#setDensity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#setDrawFilter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#setMatrix` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#skew` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#toImage` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Canvas#translate` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ContinuationCreator#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ContinuationCreator#await` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ContinuationCreator#resume` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ContinuationCreator#resumeError` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### Database#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Database#acquireReference` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#beginTransaction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#beginTransactionNonExclusive` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#beginTransactionWithListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#beginTransactionWithListenerNonExclusive` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#compileStatement` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#delete` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#disableWriteAheadLogging` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#enableWriteAheadLogging` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#endTransaction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#execSQL` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#getAttachedDbs` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#getMaximumSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#getPageSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#getPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#getTypeAdapter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#getVersion` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#inTransaction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#insert` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#insertOrThrow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#insertWithOnConflict` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#isDatabaseIntegrityOk` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#isDbLockedByCurrentThread` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#isOpen` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#isReadOnly` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#isWriteAheadLoggingEnabled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#needUpgrade` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#onCreate` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#onDowngrade` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#onOpen` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#onUpgrade` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#query` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#queryWithFactory` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#rawQuery` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#rawQueryWithFactory` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#releaseReference` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#replace` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#replaceOrThrow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#setForeignKeyConstraintsEnabled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#setLocale` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#setMaxSqlCacheSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#setMaximumSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#setPageSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#setTransactionSuccessful` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#setVersion` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#transaction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#update` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#updateWithOnConflict` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#validateSql` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Database#yieldIfContendedSafely` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### Dialog#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Dialog#addContentView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#addListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#cancel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#clearSelectedIndices` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#closeOptionsMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#create` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dismiss` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#emit` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#eventNames` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#findViewById` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getActionBar` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getActionButton` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getBuilder` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getContentView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getContext` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getCurrentFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getCurrentProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getCustomView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getIconView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getInputEditText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getItems` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getLayoutInflater` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getMaxProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getOwnerActivity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getProgressBar` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getRecyclerView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getSearchEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getSelectedIndex` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getSelectedIndices` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getTag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getTitleView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getVolumeControlStream` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#getWindow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#hasActionButtons` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#hide` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#incrementProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#invalidateOptionsMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#isCancelled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#isIndeterminateProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#isPromptCheckBoxChecked` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#isShowing` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#listenerCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#listeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#notifyItemChanged` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#notifyItemInserted` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#notifyItemsChanged` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#numberOfActionButtons` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#on` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onBackPressed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#once` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#openContextMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#openOptionsMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#prependListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#prependOnceListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#registerForContextMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#removeAllListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#removeListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#requestWindowFeature` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#selectAllIndices` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setActionButton` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setCancelMessage` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setCancelable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setCanceledOnTouchOutside` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setContent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setContentView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setDismissMessage` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setFeatureDrawable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setFeatureDrawableAlpha` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setFeatureDrawableResource` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setFeatureDrawableUri` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setIcon` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setIconAttribute` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setItems` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setMaxProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setOnCancelListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setOnDismissListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setOnKeyListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setOnShowListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setOwnerActivity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setProgressNumberFormat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setProgressPercentFormat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setPromptCheckBoxChecked` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setSelectedIndex` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setSelectedIndices` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setTitle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setTypeface` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#setVolumeControlStream` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#show` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#showSnack` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#takeKeyEvents` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#unregisterForContextMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### EpubReaderSession#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `EpubReaderSession#addListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#bookmarks` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#chapterTitle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#emit` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#emitSticky` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#eventNames` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#getMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#getTimer` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#goTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#href` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#isClosed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#isOpen` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#listenerCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#listeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#locator` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#next` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#nextChapter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#on` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#once` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#path` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#positions` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#prependListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#prependOnceListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#prev` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#prevChapter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#progress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#reason` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#removeAllListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#removeListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#setMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#setPreferences` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#state` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#title` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ImageFeatures#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ImageFeatures#count` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#isRecycled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#method` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#oneShot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#recycle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#recycled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#region` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#scale` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#setOneShot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#shoot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ImageWrapper#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ImageWrapper#bgrMat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#bitmap` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#ensureNotRecycled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#height` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#isRecycled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#mat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#oneShot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#pixel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#plane` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#recycle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#saveTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#setOneShot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#shoot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### IntentTask#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `IntentTask#getAction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getCategory` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getDataType` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getExtras` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getIntentFilter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getProjectRoot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getScriptPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#getSourceKind` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#isLocal` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setAction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setCategory` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setDataType` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setExtras` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setLocal` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setProjectRoot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setScriptPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setSourceKind` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### Mat#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Mat#convertTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#isReleased` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#release` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### MatchingResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `MatchingResult#best` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#bottommost` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#filter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#findMax` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#first` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#isEmpty` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#isNotEmpty` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#last` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#leftmost` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#matches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#points` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#rightmost` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#sortBy` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#topmost` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `MatchingResult#worst` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ObjectFrame#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ObjectFrame#angle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#bottomLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#bottomRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#bounds` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#center` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#centerX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#centerY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#height` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#inliers` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#matches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#points` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#summary` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#topLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#topRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ObjectFrame#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### OcrResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `OcrResult#compareTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `OcrResult#label` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### PReadableTextFile#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PReadableTextFile#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PReadableTextFile#getPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PReadableTextFile#read` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PReadableTextFile#readline` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PReadableTextFile#readlines` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### PWritableTextFile#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PWritableTextFile#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PWritableTextFile#flush` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PWritableTextFile#getPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PWritableTextFile#write` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PWritableTextFile#writeline` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PWritableTextFile#writelines` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### PngQuantizationFileResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PngQuantizationFileResult#path` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationFileResult#peakWorkingMemoryBytes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationFileResult#quality` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationFileResult#quantizationError` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationFileResult#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### PngQuantizationResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PngQuantizationResult#bytes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationResult#peakWorkingMemoryBytes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationResult#quality` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationResult#quantizationError` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `PngQuantizationResult#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ScreenCaptureOptions#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ScreenCaptureOptions#density` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScreenCaptureOptions#height` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScreenCaptureOptions#isAsync` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScreenCaptureOptions#orientation` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScreenCaptureOptions#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ScriptEngine#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ScriptEngine#cwd` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#emit` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#forceStop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getBackingSourcePath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getExecArgv` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSource` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSourceDirectory` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSourcePath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSourceUri` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getStartTime` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getTag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getUncaughtException` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#hasFeature` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#isDestroyed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#setTag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ScriptExecution#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ScriptExecution#getAgentContext` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getAgentParametersJson` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getAgentPresetName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getAgentResult` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getAgentRunId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getConfig` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getEngine` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#getSource` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#isFinished` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptExecution#setAgentResult` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ShellResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ShellResult#code` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#error` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#result` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#throwIfError` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#toJson` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ThemeColor#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ThemeColor#colorAccent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#colorPrimary` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#colorPrimaryDark` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#getColorAccent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#getColorPrimary` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#getColorPrimaryDark` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#isLuminanceDark` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#isLuminanceLight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#readFrom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ThemeColor#saveIn` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### TimedTask#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `TimedTask#getDayOfMonth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getDelay` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getEndMode` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getEndValue` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getFinishedTimes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getInterval` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getLoopTimes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getMillis` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getMonthOfYear` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getNextTime` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getProjectRoot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getRepeatMode` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getScriptPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getSourceKind` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getTimeFlag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#hasDayOfWeek` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#isCountdown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#isDaily` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#isDisposable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#isMonthly` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#isScheduled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#isWeekly` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#isYearly` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setDayOfMonth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setDelay` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setEndMode` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setEndValue` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setFinishedTimes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setInterval` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setLoopTimes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setMillis` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setMonthOfYear` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setProjectRoot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setRepeatMode` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setScheduled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setScriptPath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setSourceKind` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#setTimeFlag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### TtsUtterance#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `TtsUtterance#addListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#cancel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#emit` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#emitSticky` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#eventNames` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#getMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#getTimer` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#id` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#isDone` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#listenerCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#listeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#on` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#once` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#operation` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#prependListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#prependOnceListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#removeAllListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#removeListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#result` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#setMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#state` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#stop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#text` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### UiObject#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `UiObject#accessibilityFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#accessibilityFocused` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#actionNames` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#bottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#bounds` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsBottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsExactCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsExactCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsInParent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsInScreen` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsTop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#boundsWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#brother` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#center` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#centerX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#centerY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#checkable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#checked` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#child` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#childCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#children` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#className` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#clearAccessibilityFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#clearFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#clearSelection` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#click` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#clickBounds` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#clickable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#collapse` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#column` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#columnCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#columnSpan` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#compass` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#content` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#contentInvalid` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#contextClick` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#contextClickable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#copy` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#cut` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#depth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#desc` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#dismiss` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#dismissable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#dragCancel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#dragDrop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#dragStart` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#drawingOrder` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#dumpSubtree` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#editable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#enabled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#exactCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#exactCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#expand` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#find` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#findOne` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#fingerprint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#firstChild` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#firstSibling` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#focus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#focusable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#focused` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#fullId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#getText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#hasAction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#hasChildren` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#height` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#hideTooltip` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#id` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#idEntry` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#idHex` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#imeEnter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#indexInParent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#isShifted` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#isSimilar` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#isSingleton` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#isStale` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#lastChild` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#lastSibling` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#left` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#longClick` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#longClickable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#moveWindow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#multiLine` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#nextAtMovementGranularity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#nextHtmlElement` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#nextSibling` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#offset` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#packageName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#pageDown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#pageLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#pageRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#pageUp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#parent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#password` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#paste` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#performAction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#point` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#pressAndHold` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#previousAtMovementGranularity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#previousHtmlElement` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#previousSibling` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#refresh` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#right` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#row` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#rowCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#rowSpan` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollBackward` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollDown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollForward` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollUp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#scrollable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#select` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#selected` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#setProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#setSelection` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#setText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#show` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#showTextSuggestions` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#showTooltip` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#sibling` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#siblingCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#siblings` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#simpleId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#snapshot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#summary` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#text` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#toJSON` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#top` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#visibleToUser` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#waitForStable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#waitUntilGone` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### UiObjectCollection#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `UiObjectCollection#accessibilityFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#at` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#clearAccessibilityFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#clearFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#clearSelection` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#click` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#clickEach` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#collapse` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#contains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#contextClick` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#copy` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#cut` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#dismiss` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#dragCancel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#dragDrop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#dragStart` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#each` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#empty` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#expand` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#find` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#findOne` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#first` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#focus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#get` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#hideTooltip` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#imeEnter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#isEmpty` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#isNotEmpty` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#isSimilar` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#iterator` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#last` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#longClick` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#longClickEach` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#moveWindow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#nextAtMovementGranularity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#nextHtmlElement` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#nodes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#nonEmpty` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#nonNull` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#pageDown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#pageLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#pageRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#pageUp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#paste` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#performAction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#performActionEach` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#pressAndHold` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#previousAtMovementGranularity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#previousHtmlElement` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollBackward` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollBackwardEach` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollDown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollForward` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollForwardEach` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#scrollUp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#select` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#setProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#setSelection` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#setText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#setTextEach` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#show` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#showTextSuggestions` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#showTooltip` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#slice` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#toArray` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObjectCollection#toList` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### UiSelector#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `UiSelector#accessibilityFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#accessibilityFocused` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#action` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#algorithm` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#append` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#bottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#bounds` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsBottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsContains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsInside` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxBottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxTop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMaxWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinBottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinTop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsMinWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsTop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#boundsWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#centerX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#centerY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#checkable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#checked` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#childCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#className` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#classNameContains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#classNameEndsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#classNameMatch` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#classNameMatches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#classNameStartsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#clearAccessibilityFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#clearFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#clearSelection` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#click` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#clickable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#collapse` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#column` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#columnCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#columnSpan` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#content` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contentContains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contentEndsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contentInvalid` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contentMatch` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contentMatches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contentStartsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contextClick` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#contextClickable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#copy` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#currentApp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#cut` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#depth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#desc` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#descContains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#descEndsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#descMatch` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#descMatches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#descStartsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#dismiss` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#dismissable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#dragCancel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#dragDrop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#dragStart` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#drawingOrder` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#editable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#enabled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#exists` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#expand` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#filter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#find` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#findIterator` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#findOnce` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#findOne` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#focus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#focusable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#focused` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#hasChildren` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#height` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#hideTooltip` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#id` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#idContains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#idEndsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#idHex` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#idMatch` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#idMatches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#idStartsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#imeEnter` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#indexInParent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#left` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#longClick` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#longClickable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxBottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxChildCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxDepth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxIndexInParent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxTop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#maxWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minBottom` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minChildCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minDepth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minHeight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minIndexInParent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minTop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#minWidth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#moveWindow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#multiLine` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#nextAtMovementGranularity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#nextHtmlElement` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#normalize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#packageName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#packageNameContains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#packageNameEndsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#packageNameMatch` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#packageNameMatches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#packageNameStartsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#pageDown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#pageLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#pageRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#pageUp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#password` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#paste` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#performAction` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#plus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#prefer` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#pressAndHold` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#previousAtMovementGranularity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#previousHtmlElement` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#right` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#row` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#rowCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#rowSpan` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#screenCenterX` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#screenCenterY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#screenCoverage` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollBackward` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollDown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollForward` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollLeft` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollRight` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollUp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#scrollable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#select` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#selected` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#setProgress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#setSelection` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#setText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#show` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#showTextSuggestions` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#showTooltip` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#text` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#textContains` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#textEndsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#textMatch` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#textMatches` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#textStartsWith` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#toStringReadable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#top` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#untilFind` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#untilFindOne` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#visibleToUser` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#waitFor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiSelector#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### WrappedBarcode#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `WrappedBarcode#barcode` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getBoundingBox` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getCalendarEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getContactInfo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getCornerPoints` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getDisplayValue` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getDriverLicense` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getEmail` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getFormat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getFormatName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getGeoPoint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getPhone` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getRawBytes` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getRawValue` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getSms` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getType` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getTypeName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getUrl` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getValueType` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getValueTypeName` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#getWifi` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WrappedBarcode#value` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### base64

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `base64.decode` | local-shim | not-portable: runtime-native (native-base64) | not-portable: runtime-native (native-base64) |
| `base64.encode` | local-shim | not-portable: runtime-native (native-base64) | not-portable: runtime-native (native-base64) |

### crypto

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `crypto.Key` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.KeyPair` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.decrypt` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.digest` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.encrypt` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.fromHex` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.generateKeyPair` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.toHex` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |

### events

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `events.__asEmitter__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |

### http

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `http.__okhttp__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `http.buildRequest` | not-portable: java-prototype (http-java-objects) | not-portable: java-prototype (http-java-objects) | not-portable: java-prototype (http-java-objects) |
| `http.client` | not-portable: java-prototype (http-java-objects) | not-portable: java-prototype (http-java-objects) | not-portable: java-prototype (http-java-objects) |
| `http.del` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.delAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.delete` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.deleteAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.get` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-http) |
| `http.getAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.head` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.headAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.post` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.postAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.postJson` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.postJsonAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.postMultipart` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.postMultipartAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.put` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.putAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.request` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-http) |
| `http.requestAsync` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |

### imgui

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `imgui.AcceptDragDropPayload` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.AddButtonEx` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.AddMouseButtonEvent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.AddMousePosEvent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Alert` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.AlignTextToFramePadding` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Appearance` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ArrowButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BackendStatus` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Begin` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginChild` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginCombo` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginDisabled` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginDragDropSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginDragDropTarget` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginGroup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginItemTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginListBox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginMenu` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginMenuBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginMultiSelect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginPopupContextItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginPopupContextWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginPopupModal` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginTabBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginTabItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginTable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BeginTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Bullet` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.BulletText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Button` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ButtonFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.CalcTextSize` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Checkbox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ChildFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.CloseCurrentPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.CloseWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Col` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.CollapseWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.CollapsingHeader` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ColorButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ColorEdit3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ColorEdit4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ColorEditFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ColorPicker3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ColorPicker4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ColorU32` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Combo` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ComboFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Cond` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Confirm` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.CreateTexture` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.CreateWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DataTable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DestroyTexture` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Dir` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Dp` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragDropFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragFloat2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragFloat3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragFloat4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragFloatRange2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragInt2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragInt3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragInt4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DragIntRange2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DrawFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.DrawListLayer` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Dummy` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.End` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndChild` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndCombo` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndDisabled` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndDragDropSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndDragDropTarget` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndGroup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndListBox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndMenu` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndMenuBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndMultiSelect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndTabBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndTabItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndTable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.EndTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ExpandWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.FocusedFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Fonts` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetBackendCapabilities` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetBackgroundDrawList` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetClipboardText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetComponent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetContentRegionAvail` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetCursorPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetDragDropPayload` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetExampleSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetForegroundDrawList` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetFrameHeight` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetFrameHeightWithSpacing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetKeyName` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetKeyPressedAmount` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetMouseDragDelta` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetMousePos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetScrollMaxX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetScrollMaxY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetScrollX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetScrollY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetTextLineHeight` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetTextLineHeightWithSpacing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetWindowDrawList` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetWindowPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.GetWindowSize` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.HoveredFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Icon` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Icons` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Image` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ImageButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Indent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputFloat2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputFloat3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputFloat4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputInt2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputInt3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputInt4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputTextFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InputTextMultiline` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.InvisibleButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsAnyItemActive` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsAnyItemHovered` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemActivated` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemActive` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemClicked` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemDeactivated` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemDeactivatedAfterEdit` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemEdited` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemFocused` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemHovered` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemToggledSelection` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsItemVisible` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsKeyChordPressed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsKeyDown` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsKeyPressed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsKeyReleased` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsMouseClicked` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsMouseDoubleClicked` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsMouseDown` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsMouseDragging` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsMouseReleased` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsPopupOpen` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsWindowAppearing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsWindowCollapsed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsWindowFocused` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.IsWindowHovered` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Key` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.LabelText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Launch` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.LaunchFloaty` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.LaunchPrivileged` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Limits` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ListBox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ListClipper` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ListExamples` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.LogLevel` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.LogWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.MenuItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Mod` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.MouseButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.MultiChoice` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.MultiSelectFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.NewLine` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.OpenPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PlotHistogram` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PlotLines` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PopClipRect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PopFont` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PopID` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PopItemWidth` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PopStyleColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PopStyleVar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PopupFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ProgressBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PushClipRect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PushFont` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PushID` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PushItemWidth` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PushStyleColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.PushStyleVar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Px` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.RadioButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Ref` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ResetMouseDragDelta` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.RunExample` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SameLine` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Selectable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SelectableFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SelectionStorage` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Separator` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SeparatorText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetClipboardText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetCursorPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetDragDropPayload` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetGlobalAlpha` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetItemTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetKeyboardFocusHere` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetMoreTouch` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextItemSelectionUserData` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextItemShortcut` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextItemWidth` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextWindowBgAlpha` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextWindowCollapsed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextWindowFocus` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextWindowPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextWindowSize` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetNextWindowSizeConstraints` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetScrollHereX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetScrollHereY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetScrollX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetScrollY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetSecure` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetStyleColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetStyleProperties` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetTheme` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetTouchable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SetWindowFontScale` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Shortcut` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ShowAboutWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ShowDebugLogWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ShowDemoWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ShowIDStackToolWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ShowMetricsWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ShowStyleEditor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ShowUserGuide` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SingleChoice` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderAngle` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderFloat2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderFloat3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderFloat4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderInt2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderInt3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SliderInt4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SmallButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.SortDirection` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Spacing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.StatsOverlay` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.StyleColorsClassic` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.StyleColorsDark` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.StyleColorsLight` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.StyleVar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TabBarFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TabItemFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableBgTarget` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableColumnFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableGetSortSpecs` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableHeadersRow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableNextColumn` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableNextRow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableRowFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableSetBgColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableSetColumnIndex` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableSetupColumn` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TableSetupScrollFreeze` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Text` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TextColored` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TextDisabled` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TextWrapped` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TextureFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Themes` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Toast` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ToastPosition` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Toggle` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ToggleSwitch` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TouchMode` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TreeNode` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TreeNodeEx` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TreeNodeFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.TreePop` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.Unindent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.VSliderFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.VSliderInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.WantCaptureMouse` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.WindowFlags` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.WindowMode` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.WithDragDropSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.WithDragDropTarget` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.WithFont` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.__pluginId` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `imgui.acceptDragDropPayload` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.addButtonEx` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.addInputCharacters` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.addKeyEvent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.addMouseButtonEvent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.addMousePosEvent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.addMouseWheel` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.alert` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.alignTextToFramePadding` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.arrowButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.begin` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginChild` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginCombo` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginDisabled` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginDragDropSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginDragDropTarget` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginGroup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginItemTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginListBox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginMenu` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginMenuBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginMultiSelect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginPopupContextItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginPopupContextWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginPopupModal` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginTabBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginTabItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginTable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.beginTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.bool` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.bullet` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.bulletText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.button` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.bytes` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.calcTextSize` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.checkbox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.closeCurrentPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.closeWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.collapseWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.collapsingHeader` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.colorButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.colorEdit3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.colorEdit4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.colorPicker3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.colorPicker4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.colorU32` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.combo` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.confirm` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.createTexture` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.createWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dataTable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.density` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.destroyTexture` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dp` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragFloat2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragFloat3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragFloat4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragFloatRange2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragInt2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragInt3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragInt4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dragIntRange2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.dummy` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.end` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endChild` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endCombo` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endDisabled` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endDragDropSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endDragDropTarget` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endGroup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endListBox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endMenu` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endMenuBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endMultiSelect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endTabBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endTabItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endTable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.endTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.expandWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.float` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.floats` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.framerate` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getBackendCapabilities` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getBackgroundDrawList` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getClipboardText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getComponent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getContentRegionAvail` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getCursorPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getDragDropPayload` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getExampleSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getForegroundDrawList` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getFrameHeight` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getFrameHeightWithSpacing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getKeyName` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getKeyPressedAmount` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getMouseDragDelta` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getMousePos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getScrollMaxX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getScrollMaxY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getScrollX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getScrollY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getTextLineHeight` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getTextLineHeightWithSpacing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getWindowDrawList` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getWindowPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.getWindowSize` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.icon` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.image` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.imageButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.indent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputFloat2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputFloat3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputFloat4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputInt2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputInt3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputInt4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.inputTextMultiline` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.int` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ints` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.invisibleButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isAnyItemActive` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isAnyItemHovered` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemActivated` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemActive` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemClicked` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemDeactivated` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemDeactivatedAfterEdit` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemEdited` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemFocused` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemHovered` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemToggledSelection` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isItemVisible` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isKeyChordPressed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isKeyDown` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isKeyPressed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isKeyReleased` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isMouseClicked` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isMouseDoubleClicked` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isMouseDown` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isMouseDragging` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isMouseReleased` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isPopupOpen` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isWindowAppearing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isWindowCollapsed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isWindowFocused` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.isWindowHovered` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.labelText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.launch` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.launchFloaty` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.launchPrivileged` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.listBox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.listClipper` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.listExamples` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.logWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.menuItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.multiChoice` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.newLine` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.openPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.plotHistogram` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.plotLines` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.popClipRect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.popFont` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.popId` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.popItemWidth` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.popStyleColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.popStyleVar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.progressBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.pushClipRect` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.pushFont` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.pushId` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.pushItemWidth` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.pushStyleColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.pushStyleVar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.px` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.radioButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.ref` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.resetMouseDragDelta` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.runExample` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sameLine` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.selectable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.selectionStorage` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sendKey` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.separator` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.separatorText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setClipboardText` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setCursorPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setDragDropPayload` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setGlobalAlpha` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setItemTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setKeyboardFocusHere` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setMoreTouch` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextItemSelectionUserData` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextItemShortcut` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextItemWidth` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextWindowBgAlpha` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextWindowCollapsed` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextWindowFocus` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextWindowPos` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextWindowSize` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setNextWindowSizeConstraints` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setScrollHereX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setScrollHereY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setScrollX` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setScrollY` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setSecure` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setStyleColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setStyleProperties` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setTheme` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setTouchable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.setWindowFontScale` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.shortcut` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.showAboutWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.showDebugLogWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.showDemoWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.showIdStackToolWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.showMetricsWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.showStyleEditor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.showUserGuide` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.singleChoice` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderAngle` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderFloat2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderFloat3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderFloat4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderInt2` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderInt3` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.sliderInt4` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.smallButton` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.spacing` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.statsOverlay` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.string` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.strings` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.styleColorsClassic` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.styleColorsDark` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.styleColorsLight` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableGetSortSpecs` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableHeadersRow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableNextColumn` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableNextRow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableSetBgColor` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableSetColumnIndex` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableSetupColumn` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.tableSetupScrollFreeze` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.text` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.textColored` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.textDisabled` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.textWrapped` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.toast` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.toggle` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.toggleSwitch` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.treeNode` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.treeNodeEx` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.treePop` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.unindent` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.vSliderFloat` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.vSliderInt` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.version` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.wantCaptureMouse` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.wantTextInput` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withChild` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withCombo` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withContextItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withContextWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withDisabled` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withDragDropSource` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withDragDropTarget` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withFont` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withGroup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withId` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withItemTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withListBox` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withMenu` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withMenuBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withPopup` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withPopupModal` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withTabBar` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withTabItem` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withTable` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withTooltip` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withTreeNode` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |
| `imgui.withWindow` | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) | not-portable: rhino-only (imgui-rhino-only) |

### memory

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `memory.watch` | not-portable: runtime-native (native-memory-watch) | not-portable: runtime-native (native-memory-watch) | not-portable: runtime-native (native-memory-watch) |

### plugins

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `plugins.load` | local-shim | not-portable: rhino-only (rhino-engine) | not-portable: rhino-only (rhino-engine) |

### timers

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `timers.clearImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `timers.clearInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `timers.clearTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `timers.setImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `timers.setInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `timers.setTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |

### ui

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ui.__autojs6Tsx` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.__autojs6TsxFragment` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.__inflate__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.__widgets__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.layoutInflater` | not-portable: java-prototype (java-prototype-layout-inflater) | not-portable: java-prototype (java-prototype-layout-inflater) | not-portable: java-prototype (java-prototype-layout-inflater) |

### util

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `util.class` | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) |
| `util.className` | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) |
| `util.getClassName` | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) |
| `util.toRegularAndApply` | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) |
| `util.toRegularAndCall` | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) |
| `util.unwrapJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
