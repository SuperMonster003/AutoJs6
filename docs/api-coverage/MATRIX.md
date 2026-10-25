<!-- runtime-matrix: schema=autojs6-runtime-matrix-v1 dataSource=ksp rows=2724 -->
# AutoJs6 脚本 API 运行时覆盖矩阵

由 `tools/api-coverage/runtime_matrix.py` 生成 (`python tools/api-coverage/check.py --matrix`), 不要手改. 定义见 `docs/api-coverage/ROADMAP.md` 第 4 节, 机读版本是 CI 产物 `build/reports/api-coverage/runtime-matrix.json`, 人工标注 (别名映射, 排除理由, 等价写法) 只在 `tools/api-coverage/runtime-policy.json`; 排除规则在 D12 / Q1 拍板前是草案.

数据源: ksp, 2724 行 (显式导出 1368, 返回对象成员 1356).

## 状态汇总

| 列 | bridged | local-shim | capability-gated | not-portable | denied | pending |
|---|---:|---:|---:|---:|---:|---:|
| Node main | 616 | 143 | 0 | 54 | 1 | 1910 |
| Node Worker | 482 | 62 | 0 | 114 | 1 | 2065 |
| Bun | 0 | 62 | 534 | 113 | 1 | 2014 |

| 列 | 取值 |
|---|---|
| doc | none 331 / mention 230 / heading 2163 / signature 0 / example 0 |
| dts | none 79 / name 1507 / arity 1138 / typed 0 |
| ace | none 540 / semantic 825 / static 1359 |

排除理由缺失: 0. 策略冲突: jsox-extensions shadowed (node_main, 54), rhino-engine shadowed (node_main, 3), sync-sleep shadowed (node_main, 1).

## 排除规则 (草案)

| 规则 | 状态 / 理由 | Node main | Node Worker | Bun | 等价写法 |
|---|---|---:|---:|---:|---|
| accessibility-sync-wait | denied: sync-only | 1 | 1 | 1 | await accessibility.ensureEnabled(); flow waitFor* with node snapshots |
| internal-exports | not-portable: rhino-only | 9 | 9 | 9 |  |
| java-prototype | not-portable: java-prototype | 5 | 5 | 5 | java module handles (java.create / java.callInstance / java.callStatic) |
| jsox-extensions | not-portable: rhino-only | 0 | 54 | 54 |  |
| native-base64 | not-portable: runtime-native | 0 | 2 | 2 | Buffer.from(text).toString("base64") / Buffer.from(data, "base64"), atob / btoa |
| native-crypto | not-portable: runtime-native | 2 | 2 | 2 | node:crypto KeyObject / generateKeyPair, WebCrypto (globalThis.crypto.subtle) |
| native-http | not-portable: runtime-native | 18 | 18 | 20 | global fetch (Node / Bun), node:http; autojs6:fetch for host proxy / certificate policy |
| native-timers | not-portable: runtime-native | 0 | 0 | 6 | global setTimeout / setInterval / setImmediate and their clear functions |
| rhino-engine | not-portable: rhino-only | 1 | 4 | 4 |  |
| sync-continuation | not-portable: sync-only | 2 | 2 | 2 | await on the Promise returned by the asynchronous entry |
| sync-keep-alive | not-portable: runtime-native | 3 | 3 | 3 | the runtime event loop stays alive while timers, listeners or pending Promises exist |
| sync-sleep | not-portable: runtime-native | 0 | 1 | 1 | await new Promise(resolve => setTimeout(resolve, ms)) (Node timers/promises setTimeout, Bun.sleep) |
| sync-threads | not-portable: sync-only | 5 | 5 | 5 | Node worker_threads / Bun Worker with message passing |

## 按模块汇总

运行时列为 覆盖 (bridged / local-shim / capability-gated) / 排除 (not-portable / denied) / pending; doc 为有标题的行数, d.ts 为声明可解析的行数, Ace 为默认与全组补全都命中的行数.

| 模块 | 行 | doc 标题 | d.ts | Ace | Node main | Node Worker | Bun |
|---|---:|---:|---:|---:|---|---|---|
| (global) | 208 | 194 | 205 | 205 | 93 / 16 / 99 | 47 / 17 / 144 | 73 / 17 / 118 |
| AgentRun# | 14 | 7 | 14 | 14 | 0 / 0 / 14 | 0 / 0 / 14 | 0 / 0 / 14 |
| AiSession# | 11 | 11 | 0 | 0 | 0 / 0 / 11 | 0 / 0 / 11 | 0 / 0 / 11 |
| App# | 10 | 10 | 0 | 0 | 0 / 0 / 10 | 0 / 0 / 10 | 0 / 0 / 10 |
| Arrayx | 15 | 15 | 15 | 15 | 15 / 0 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| Canvas# | 54 | 54 | 54 | 0 | 0 / 0 / 54 | 0 / 0 / 54 | 0 / 0 / 54 |
| Color# | 29 | 21 | 28 | 28 | 0 / 0 / 29 | 0 / 0 / 29 | 0 / 0 / 29 |
| ConsoleConfigurator# | 53 | 50 | 53 | 0 | 0 / 0 / 53 | 0 / 0 / 53 | 0 / 0 / 53 |
| ContinuationCreator# | 3 | 3 | 3 | 3 | 0 / 0 / 3 | 0 / 0 / 3 | 0 / 0 / 3 |
| Database# | 51 | 51 | 51 | 2 | 0 / 0 / 51 | 0 / 0 / 51 | 0 / 0 / 51 |
| Dialog# | 140 | 6 | 139 | 3 | 0 / 0 / 140 | 0 / 0 / 140 | 0 / 0 / 140 |
| EpubBook# | 14 | 14 | 14 | 14 | 0 / 0 / 14 | 0 / 0 / 14 | 0 / 0 / 14 |
| EpubReaderSession# | 35 | 20 | 34 | 32 | 0 / 0 / 35 | 0 / 0 / 35 | 0 / 0 / 35 |
| FloatyWindow# | 12 | 9 | 12 | 12 | 0 / 0 / 12 | 0 / 0 / 12 | 0 / 0 / 12 |
| Flow# | 106 | 25 | 106 | 106 | 0 / 0 / 106 | 0 / 0 / 106 | 0 / 0 / 106 |
| ImageFeatures# | 12 | 10 | 12 | 12 | 0 / 0 / 12 | 0 / 0 / 12 | 0 / 0 / 12 |
| ImageWrapper# | 16 | 15 | 16 | 6 | 0 / 0 / 16 | 0 / 0 / 16 | 0 / 0 / 16 |
| InjectableWebClient# | 3 | 2 | 3 | 0 | 0 / 0 / 3 | 0 / 0 / 3 | 0 / 0 / 3 |
| InjectableWebView# | 1 | 1 | 1 | 0 | 0 / 0 / 1 | 0 / 0 / 1 | 0 / 0 / 1 |
| IntentTask# | 21 | 0 | 20 | 0 | 0 / 0 / 21 | 0 / 0 / 21 | 0 / 0 / 21 |
| MailClient# | 53 | 53 | 53 | 53 | 0 / 0 / 53 | 0 / 0 / 53 | 0 / 0 / 53 |
| Mat# | 9 | 0 | 3 | 0 | 0 / 0 / 9 | 0 / 0 / 9 | 0 / 0 / 9 |
| MatchingResult# | 16 | 15 | 16 | 16 | 0 / 0 / 16 | 0 / 0 / 16 | 0 / 0 / 16 |
| Mathx | 23 | 21 | 23 | 23 | 23 / 0 / 0 | 0 / 23 / 0 | 0 / 23 / 0 |
| Numberx | 14 | 14 | 14 | 14 | 14 / 0 / 0 | 0 / 14 / 0 | 0 / 14 / 0 |
| ObjectFrame# | 15 | 15 | 15 | 15 | 0 / 0 / 15 | 0 / 0 / 15 | 0 / 0 / 15 |
| PReadableTextFile# | 5 | 4 | 5 | 0 | 0 / 0 / 5 | 0 / 0 / 5 | 0 / 0 / 5 |
| PWritableTextFile# | 7 | 6 | 2 | 0 | 0 / 0 / 7 | 0 / 0 / 7 | 0 / 0 / 7 |
| PngQuantizationFileResult# | 5 | 3 | 5 | 5 | 0 / 0 / 5 | 0 / 0 / 5 | 0 / 0 / 5 |
| PngQuantizationResult# | 5 | 3 | 5 | 5 | 0 / 0 / 5 | 0 / 0 / 5 | 0 / 0 / 5 |
| RawWindow# | 12 | 9 | 12 | 12 | 0 / 0 / 12 | 0 / 0 / 12 | 0 / 0 / 12 |
| ScreenCaptureOptions# | 5 | 5 | 5 | 0 | 0 / 0 / 5 | 0 / 0 / 5 | 0 / 0 / 5 |
| ScriptEngine# | 29 | 12 | 26 | 0 | 0 / 0 / 29 | 0 / 0 / 29 | 0 / 0 / 29 |
| ScriptExecution# | 13 | 5 | 12 | 0 | 0 / 0 / 13 | 0 / 0 / 13 | 0 / 0 / 13 |
| ShellProcess# | 25 | 12 | 25 | 25 | 0 / 0 / 25 | 0 / 0 / 25 | 0 / 0 / 25 |
| ShellResult# | 5 | 5 | 5 | 5 | 0 / 0 / 5 | 0 / 0 / 5 | 0 / 0 / 5 |
| Storage# | 12 | 12 | 12 | 12 | 0 / 0 / 12 | 0 / 0 / 12 | 0 / 0 / 12 |
| ThemeColor# | 10 | 0 | 10 | 0 | 0 / 0 / 10 | 0 / 0 / 10 | 0 / 0 / 10 |
| TimedTask# | 69 | 0 | 44 | 0 | 0 / 0 / 69 | 0 / 0 / 69 | 0 / 0 / 69 |
| TtsUtterance# | 25 | 11 | 23 | 21 | 0 / 0 / 25 | 0 / 0 / 25 | 0 / 0 / 25 |
| UiObject# | 145 | 136 | 145 | 145 | 0 / 0 / 145 | 0 / 0 / 145 | 0 / 0 / 145 |
| UiObjectCollection# | 70 | 66 | 70 | 70 | 0 / 0 / 70 | 0 / 0 / 70 | 0 / 0 / 70 |
| UiSelector# | 185 | 182 | 184 | 184 | 0 / 0 / 185 | 0 / 0 / 185 | 0 / 0 / 185 |
| VolatileDispose# | 3 | 3 | 3 | 3 | 0 / 0 / 3 | 0 / 0 / 3 | 0 / 0 / 3 |
| WebSocket# | 25 | 10 | 24 | 0 | 0 / 0 / 25 | 0 / 0 / 25 | 0 / 0 / 25 |
| YoloDetector# | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| Zip# | 21 | 20 | 20 | 20 | 0 / 0 / 21 | 0 / 0 / 21 | 0 / 0 / 21 |
| ai | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| app | 43 | 43 | 43 | 43 | 38 / 0 / 5 | 0 / 0 / 43 | 38 / 0 / 5 |
| auto | 37 | 37 | 37 | 37 | 0 / 1 / 36 | 0 / 1 / 36 | 0 / 1 / 36 |
| autojs | 21 | 21 | 21 | 21 | 0 / 0 / 21 | 0 / 0 / 21 | 0 / 0 / 21 |
| autojs.version | 7 | 7 | 7 | 7 | 0 / 0 / 7 | 0 / 0 / 7 | 0 / 0 / 7 |
| automator | 54 | 53 | 54 | 54 | 13 / 0 / 41 | 0 / 0 / 54 | 7 / 0 / 47 |
| barcode | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 0 / 0 / 4 | 0 / 0 / 4 |
| base64 | 2 | 2 | 2 | 2 | 2 / 0 / 0 | 0 / 2 / 0 | 0 / 2 / 0 |
| colors | 81 | 81 | 81 | 81 | 62 / 0 / 19 | 62 / 0 / 19 | 62 / 0 / 19 |
| colors.theme | 9 | 9 | 9 | 9 | 0 / 0 / 9 | 0 / 0 / 9 | 0 / 0 / 9 |
| compose | 16 | 16 | 16 | 16 | 15 / 0 / 1 | 15 / 0 / 1 | 15 / 0 / 1 |
| console | 50 | 50 | 50 | 50 | 45 / 0 / 5 | 45 / 0 / 5 | 31 / 0 / 19 |
| continuation | 2 | 2 | 2 | 2 | 0 / 2 / 0 | 0 / 2 / 0 | 0 / 2 / 0 |
| crypto | 2 | 2 | 2 | 2 | 0 / 2 / 0 | 0 / 2 / 0 | 0 / 2 / 0 |
| cvt.bytes | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 0 / 0 / 6 | 0 / 0 / 6 |
| device | 25 | 25 | 25 | 25 | 4 / 0 / 21 | 4 / 0 / 21 | 1 / 0 / 24 |
| dialogs | 9 | 9 | 9 | 9 | 8 / 0 / 1 | 8 / 0 / 1 | 8 / 0 / 1 |
| engines | 8 | 8 | 8 | 8 | 5 / 1 / 2 | 0 / 3 / 5 | 0 / 3 / 5 |
| epub | 21 | 21 | 21 | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| events | 1 | 0 | 0 | 0 | 0 / 1 / 0 | 0 / 1 / 0 | 0 / 1 / 0 |
| files | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 0 / 0 / 4 | 4 / 0 / 0 |
| files.batchRename | 17 | 17 | 17 | 17 | 0 / 0 / 17 | 0 / 0 / 17 | 0 / 0 / 17 |
| floaty | 8 | 8 | 8 | 8 | 0 / 0 / 8 | 0 / 0 / 8 | 0 / 0 / 8 |
| flow | 55 | 30 | 55 | 55 | 55 / 0 / 0 | 55 / 0 / 0 | 55 / 0 / 0 |
| fmt.bytes | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 0 / 0 / 6 | 0 / 0 / 6 |
| gallery | 11 | 11 | 11 | 11 | 0 / 0 / 11 | 0 / 0 / 11 | 0 / 0 / 11 |
| http | 21 | 20 | 20 | 20 | 0 / 21 / 0 | 0 / 21 / 0 | 0 / 21 / 0 |
| images | 82 | 82 | 82 | 82 | 82 / 0 / 0 | 82 / 0 / 0 | 82 / 0 / 0 |
| installer | 16 | 16 | 16 | 16 | 0 / 0 / 16 | 0 / 0 / 16 | 0 / 0 / 16 |
| jsox | 2 | 2 | 2 | 2 | 2 / 0 / 0 | 0 / 2 / 0 | 0 / 2 / 0 |
| keys | 12 | 12 | 12 | 12 | 12 / 0 / 0 | 0 / 0 / 12 | 0 / 0 / 12 |
| mail | 56 | 9 | 56 | 56 | 56 / 0 / 0 | 56 / 0 / 0 | 56 / 0 / 0 |
| mediainfo | 7 | 7 | 7 | 7 | 4 / 0 / 3 | 0 / 0 / 7 | 0 / 0 / 7 |
| memory | 5 | 5 | 5 | 5 | 0 / 0 / 5 | 0 / 0 / 5 | 0 / 0 / 5 |
| notice | 7 | 7 | 7 | 7 | 1 / 0 / 6 | 1 / 0 / 6 | 0 / 0 / 7 |
| notice.channel | 6 | 6 | 6 | 6 | 0 / 0 / 6 | 0 / 0 / 6 | 0 / 0 / 6 |
| ocr | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 0 / 0 / 6 | 0 / 0 / 6 |
| ocr.mlkit | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| ocr.paddle | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| ocr.rapid | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| opencc | 33 | 33 | 33 | 33 | 33 / 0 / 0 | 33 / 0 / 0 | 33 / 0 / 0 |
| pinyin | 16 | 16 | 16 | 16 | 16 / 0 / 0 | 16 / 0 / 0 | 6 / 0 / 10 |
| pinyin4j | 3 | 3 | 3 | 3 | 3 / 0 / 0 | 3 / 0 / 0 | 3 / 0 / 0 |
| plugins | 4 | 4 | 4 | 4 | 1 / 0 / 3 | 0 / 1 / 3 | 0 / 1 / 3 |
| plugins.tools | 4 | 4 | 4 | 4 | 0 / 0 / 4 | 0 / 0 / 4 | 0 / 0 / 4 |
| powerManager | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| qrcode | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 0 / 0 / 4 | 0 / 0 / 4 |
| s13n | 5 | 5 | 5 | 5 | 5 / 0 / 0 | 0 / 0 / 5 | 0 / 0 / 5 |
| settings | 4 | 4 | 4 | 4 | 0 / 0 / 4 | 0 / 0 / 4 | 0 / 0 / 4 |
| shell | 11 | 11 | 11 | 11 | 2 / 0 / 9 | 2 / 0 / 9 | 2 / 0 / 9 |
| shizuku | 8 | 8 | 8 | 8 | 1 / 0 / 7 | 1 / 0 / 7 | 1 / 0 / 7 |
| species | 37 | 37 | 37 | 37 | 0 / 0 / 37 | 0 / 0 / 37 | 0 / 0 / 37 |
| speech | 11 | 11 | 11 | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| speech.models | 8 | 8 | 8 | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| sqlite | 1 | 1 | 1 | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| storages | 5 | 5 | 5 | 5 | 5 / 0 / 0 | 0 / 0 / 5 | 5 / 0 / 0 |
| sysprops | 4 | 4 | 4 | 4 | 0 / 0 / 4 | 0 / 0 / 4 | 0 / 0 / 4 |
| tasks | 20 | 20 | 20 | 20 | 20 / 0 / 0 | 20 / 0 / 0 | 20 / 0 / 0 |
| terminal | 20 | 20 | 20 | 20 | 0 / 0 / 20 | 0 / 0 / 20 | 0 / 0 / 20 |
| threads | 4 | 4 | 4 | 4 | 0 / 4 / 0 | 0 / 4 / 0 | 0 / 4 / 0 |
| timers | 12 | 12 | 12 | 12 | 10 / 1 / 1 | 10 / 1 / 1 | 10 / 1 / 1 |
| toast | 1 | 1 | 1 | 1 | 0 / 0 / 1 | 0 / 0 / 1 | 0 / 0 / 1 |
| tts | 35 | 28 | 35 | 35 | 35 / 0 / 0 | 35 / 0 / 0 | 35 / 0 / 0 |
| ui | 37 | 33 | 33 | 33 | 2 / 4 / 31 | 2 / 4 / 31 | 2 / 4 / 31 |
| util | 51 | 50 | 51 | 51 | 0 / 2 / 49 | 0 / 2 / 49 | 0 / 1 / 50 |
| util.inspect | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| util.java | 6 | 6 | 6 | 6 | 0 / 0 / 6 | 0 / 0 / 6 | 0 / 0 / 6 |
| util.morseCode | 3 | 3 | 3 | 3 | 0 / 0 / 3 | 0 / 0 / 3 | 0 / 0 / 3 |
| util.version | 1 | 1 | 1 | 1 | 0 / 0 / 1 | 0 / 0 / 1 | 0 / 0 / 1 |
| util.versionCodes | 4 | 4 | 4 | 4 | 0 / 0 / 4 | 0 / 0 / 4 | 0 / 0 / 4 |
| web | 4 | 4 | 4 | 4 | 0 / 0 / 4 | 0 / 0 / 4 | 0 / 0 / 4 |
| workManager | 11 | 11 | 11 | 11 | 0 / 0 / 11 | 0 / 0 / 11 | 0 / 0 / 11 |
| yolo | 1 | 1 | 1 | 1 | 0 / 0 / 1 | 0 / 0 / 1 | 0 / 0 / 1 |
| zip | 5 | 5 | 5 | 5 | 0 / 0 / 5 | 0 / 0 / 5 | 0 / 0 / 5 |

## 差异行

只列至少一个运行时列不是覆盖状态的行.

### (global)

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Back` | pending | pending | pending |
| `Camera` | pending | pending | pending |
| `ComposeError` | pending | pending | pending |
| `Down` | pending | pending | pending |
| `HEIGHT` | pending | pending | pending |
| `Home` | pending | pending | pending |
| `Input` | pending | pending | pending |
| `KeyCode` | pending | pending | pending |
| `Left` | pending | pending | pending |
| `Menu` | pending | pending | pending |
| `OK` | pending | pending | pending |
| `Power` | pending | pending | pending |
| `R` | pending | pending | pending |
| `Right` | pending | pending | pending |
| `Screencap` | pending | pending | pending |
| `SetScreenMetrics` | pending | pending | pending |
| `Swipe` | pending | pending | pending |
| `TODO` | pending | pending | pending |
| `Tap` | pending | pending | pending |
| `Text` | pending | pending | pending |
| `Up` | pending | pending | pending |
| `VolumeDown` | pending | pending | pending |
| `VolumeUp` | pending | pending | pending |
| `WIDTH` | pending | pending | pending |
| `__autojs6Tsx` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `__autojs6TsxFragment` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `axios` | pending | pending | pending |
| `back` | bridged | pending | capability-gated |
| `backToApp` | pending | pending | pending |
| `backUntil` | pending | pending | pending |
| `cheerio` | pending | pending | pending |
| `clearConsole` | bridged | bridged | pending |
| `clearImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `clearInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `clearTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `click` | bridged | pending | capability-gated |
| `clickAny` | pending | pending | pending |
| `clickBoundsAny` | pending | pending | pending |
| `clickBoundsIfExists` | pending | pending | pending |
| `clickIfExists` | pending | pending | pending |
| `collectList` | pending | pending | pending |
| `currentActivity` | pending | pending | pending |
| `currentComponent` | pending | pending | pending |
| `currentPackage` | pending | pending | pending |
| `dayjs` | pending | pending | pending |
| `detect` | pending | pending | pending |
| `dismissPopups` | pending | pending | pending |
| `err` | bridged | bridged | pending |
| `existsAll` | pending | pending | pending |
| `existsOne` | pending | pending | pending |
| `exit` | pending | pending | pending |
| `findAny` | pending | pending | pending |
| `gesture` | bridged | pending | capability-gated |
| `gestureAsync` | pending | pending | pending |
| `gestures` | pending | pending | pending |
| `gesturesAsync` | pending | pending | pending |
| `getAppName` | bridged | pending | capability-gated |
| `getClip` | bridged | pending | pending |
| `getPackageName` | bridged | pending | capability-gated |
| `home` | bridged | pending | capability-gated |
| `i18n` | pending | pending | pending |
| `input` | pending | pending | pending |
| `isAutoJs6` | pending | pending | pending |
| `isBigInt` | pending | pending | pending |
| `isDualInstalled` | bridged | pending | capability-gated |
| `isEmptyObject` | pending | pending | pending |
| `isInstalled` | bridged | pending | capability-gated |
| `isInteger` | pending | pending | pending |
| `isJavaClass` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isJavaPackage` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isObject` | pending | pending | pending |
| `isObjectSpecies` | pending | pending | pending |
| `isPrimitive` | pending | pending | pending |
| `isReference` | pending | pending | pending |
| `isRunning` | pending | pending | pending |
| `isShuttingDown` | pending | pending | pending |
| `isStopped` | pending | pending | pending |
| `isUiThread` | pending | pending | pending |
| `keepAlive` | not-portable: runtime-native (sync-keep-alive) | not-portable: runtime-native (sync-keep-alive) | not-portable: runtime-native (sync-keep-alive) |
| `kill` | bridged | pending | capability-gated |
| `killDual` | bridged | pending | capability-gated |
| `launch` | bridged | pending | capability-gated |
| `launchAndWait` | pending | pending | pending |
| `launchApp` | bridged | pending | capability-gated |
| `launchAppDetailsSettings` | bridged | pending | capability-gated |
| `launchConsole` | bridged | bridged | pending |
| `launchDual` | bridged | pending | capability-gated |
| `launchDualApp` | bridged | pending | capability-gated |
| `launchDualAppDetailsSettings` | bridged | pending | capability-gated |
| `launchDualPackage` | bridged | pending | capability-gated |
| `launchDualSettings` | bridged | pending | capability-gated |
| `launchPackage` | bridged | pending | capability-gated |
| `launchSettings` | bridged | pending | capability-gated |
| `log` | bridged | bridged | pending |
| `longClick` | bridged | pending | capability-gated |
| `loop` | not-portable: runtime-native (sync-keep-alive) | not-portable: runtime-native (sync-keep-alive) | not-portable: runtime-native (sync-keep-alive) |
| `newInjectableWebClient` | pending | pending | pending |
| `newInjectableWebView` | pending | pending | pending |
| `newWebSocket` | pending | pending | pending |
| `notStopped` | pending | pending | pending |
| `notifications` | bridged | pending | pending |
| `open` | local-shim | pending | capability-gated |
| `openAppSetting` | bridged | pending | capability-gated |
| `openAppSettings` | bridged | pending | capability-gated |
| `openConsole` | bridged | bridged | pending |
| `openDualAppSetting` | bridged | pending | capability-gated |
| `openDualAppSettings` | bridged | pending | capability-gated |
| `pangu` | pending | pending | pending |
| `pickup` | pending | pending | pending |
| `powerDialog` | bridged | pending | pending |
| `press` | pending | pending | pending |
| `print` | bridged | bridged | pending |
| `quickSettings` | bridged | pending | pending |
| `random` | pending | pending | pending |
| `randomFloat` | local-shim | pending | pending |
| `randomInt` | local-shim | pending | pending |
| `recents` | bridged | pending | pending |
| `requiresApi` | pending | pending | pending |
| `requiresAutojsVersion` | pending | pending | pending |
| `retry` | pending | pending | pending |
| `scrollDown` | pending | pending | pending |
| `scrollUntil` | pending | pending | pending |
| `scrollUp` | pending | pending | pending |
| `sendBroadcast` | bridged | pending | capability-gated |
| `sendEmail` | bridged | pending | capability-gated |
| `sendLocalBroadcastSync` | pending | pending | pending |
| `setClip` | bridged | pending | pending |
| `setImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `setInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `setScreenMetrics` | pending | pending | pending |
| `setText` | bridged | pending | capability-gated |
| `setTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `showConsole` | bridged | bridged | pending |
| `sleep` | bridged | not-portable: runtime-native (sync-sleep) | not-portable: runtime-native (sync-sleep) |
| `smartClick` | pending | pending | pending |
| `smartClickBounds` | pending | pending | pending |
| `splitScreen` | bridged | pending | pending |
| `startActivity` | bridged | pending | capability-gated |
| `startDualActivity` | bridged | pending | capability-gated |
| `startService` | bridged | pending | capability-gated |
| `stop` | pending | pending | pending |
| `swipe` | bridged | pending | capability-gated |
| `sync` | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) |
| `toString` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `toastError` | pending | pending | pending |
| `toastInfo` | pending | pending | pending |
| `toastLog` | bridged | pending | pending |
| `toastVerbose` | pending | pending | pending |
| `toastWarn` | pending | pending | pending |
| `toasterror` | pending | pending | pending |
| `toastinfo` | pending | pending | pending |
| `toastlog` | pending | pending | pending |
| `toastverbose` | pending | pending | pending |
| `toastwarn` | pending | pending | pending |
| `toggle` | pending | pending | pending |
| `typeInto` | pending | pending | pending |
| `uninstall` | bridged | pending | capability-gated |
| `uninstallDual` | bridged | pending | capability-gated |
| `unwrapJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `verbose` | bridged | bridged | pending |
| `wait` | pending | pending | pending |
| `wait.async` | pending | pending | pending |
| `waitForActivity` | pending | pending | pending |
| `waitForEvent` | pending | pending | pending |
| `waitForIdle` | pending | pending | pending |
| `waitForNotification` | pending | pending | pending |
| `waitForPackage` | pending | pending | pending |
| `waitForToast` | pending | pending | pending |
| `warn` | bridged | bridged | pending |

### AgentRun#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `AgentRun#cancel` | pending | pending | pending |
| `AgentRun#confirm` | pending | pending | pending |
| `AgentRun#detached` | pending | pending | pending |
| `AgentRun#error` | pending | pending | pending |
| `AgentRun#goal` | pending | pending | pending |
| `AgentRun#id` | pending | pending | pending |
| `AgentRun#join` | pending | pending | pending |
| `AgentRun#off` | pending | pending | pending |
| `AgentRun#on` | pending | pending | pending |
| `AgentRun#once` | pending | pending | pending |
| `AgentRun#respond` | pending | pending | pending |
| `AgentRun#result` | pending | pending | pending |
| `AgentRun#startedAt` | pending | pending | pending |
| `AgentRun#state` | pending | pending | pending |

### AiSession#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `AiSession#ask` | pending | pending | pending |
| `AiSession#backend` | pending | pending | pending |
| `AiSession#chat` | pending | pending | pending |
| `AiSession#close` | pending | pending | pending |
| `AiSession#isClosed` | pending | pending | pending |
| `AiSession#model` | pending | pending | pending |
| `AiSession#profile` | pending | pending | pending |
| `AiSession#provider` | pending | pending | pending |
| `AiSession#state` | pending | pending | pending |
| `AiSession#stream` | pending | pending | pending |
| `AiSession#target` | pending | pending | pending |

### App#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `App#alias` | pending | pending | pending |
| `App#ensureInstalled` | pending | pending | pending |
| `App#getAppName` | pending | pending | pending |
| `App#getAppNameEn` | pending | pending | pending |
| `App#getAppNameZh` | pending | pending | pending |
| `App#isInstalled` | pending | pending | pending |
| `App#launch` | pending | pending | pending |
| `App#launchSettings` | pending | pending | pending |
| `App#packageName` | pending | pending | pending |
| `App#uninstall` | pending | pending | pending |

### Arrayx

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Arrayx.differ` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.distinct` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.distinctBy` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.ensureArray` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.intersect` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.shuffle` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.sortBy` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.sortByDescending` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.sortDescending` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.sorted` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.sortedBy` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.sortedByDescending` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.sortedDescending` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.subtract` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Arrayx.union` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |

### Canvas#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Canvas#clipPath` | pending | pending | pending |
| `Canvas#clipRect` | pending | pending | pending |
| `Canvas#concat` | pending | pending | pending |
| `Canvas#drawARGB` | pending | pending | pending |
| `Canvas#drawArc` | pending | pending | pending |
| `Canvas#drawBitmap` | pending | pending | pending |
| `Canvas#drawBitmapMesh` | pending | pending | pending |
| `Canvas#drawCircle` | pending | pending | pending |
| `Canvas#drawColor` | pending | pending | pending |
| `Canvas#drawImage` | pending | pending | pending |
| `Canvas#drawLine` | pending | pending | pending |
| `Canvas#drawLines` | pending | pending | pending |
| `Canvas#drawOval` | pending | pending | pending |
| `Canvas#drawPaint` | pending | pending | pending |
| `Canvas#drawPath` | pending | pending | pending |
| `Canvas#drawPicture` | pending | pending | pending |
| `Canvas#drawPoint` | pending | pending | pending |
| `Canvas#drawPoints` | pending | pending | pending |
| `Canvas#drawPosText` | pending | pending | pending |
| `Canvas#drawRGB` | pending | pending | pending |
| `Canvas#drawRect` | pending | pending | pending |
| `Canvas#drawRoundRect` | pending | pending | pending |
| `Canvas#drawText` | pending | pending | pending |
| `Canvas#drawTextOnPath` | pending | pending | pending |
| `Canvas#drawTextRun` | pending | pending | pending |
| `Canvas#drawVertices` | pending | pending | pending |
| `Canvas#getAndroidCanvas` | pending | pending | pending |
| `Canvas#getClipBounds` | pending | pending | pending |
| `Canvas#getDensity` | pending | pending | pending |
| `Canvas#getDrawFilter` | pending | pending | pending |
| `Canvas#getHeight` | pending | pending | pending |
| `Canvas#getMatrix` | pending | pending | pending |
| `Canvas#getMaximumBitmapHeight` | pending | pending | pending |
| `Canvas#getMaximumBitmapWidth` | pending | pending | pending |
| `Canvas#getSaveCount` | pending | pending | pending |
| `Canvas#getWidth` | pending | pending | pending |
| `Canvas#isHardwareAccelerated` | pending | pending | pending |
| `Canvas#isOpaque` | pending | pending | pending |
| `Canvas#quickReject` | pending | pending | pending |
| `Canvas#restore` | pending | pending | pending |
| `Canvas#restoreToCount` | pending | pending | pending |
| `Canvas#rotate` | pending | pending | pending |
| `Canvas#save` | pending | pending | pending |
| `Canvas#saveLayer` | pending | pending | pending |
| `Canvas#saveLayerAlpha` | pending | pending | pending |
| `Canvas#scale` | pending | pending | pending |
| `Canvas#setBitmap` | pending | pending | pending |
| `Canvas#setCanvas` | pending | pending | pending |
| `Canvas#setDensity` | pending | pending | pending |
| `Canvas#setDrawFilter` | pending | pending | pending |
| `Canvas#setMatrix` | pending | pending | pending |
| `Canvas#skew` | pending | pending | pending |
| `Canvas#toImage` | pending | pending | pending |
| `Canvas#translate` | pending | pending | pending |

### Color#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Color#argb` | pending | pending | pending |
| `Color#equals` | pending | pending | pending |
| `Color#hsl` | pending | pending | pending |
| `Color#hsla` | pending | pending | pending |
| `Color#hsv` | pending | pending | pending |
| `Color#hsva` | pending | pending | pending |
| `Color#removeAlpha` | pending | pending | pending |
| `Color#removeBlue` | pending | pending | pending |
| `Color#removeGreen` | pending | pending | pending |
| `Color#removeRed` | pending | pending | pending |
| `Color#rgb` | pending | pending | pending |
| `Color#rgba` | pending | pending | pending |
| `Color#setAlpha` | pending | pending | pending |
| `Color#setAlphaRelative` | pending | pending | pending |
| `Color#setArgb` | pending | pending | pending |
| `Color#setBlue` | pending | pending | pending |
| `Color#setBlueRelative` | pending | pending | pending |
| `Color#setGreen` | pending | pending | pending |
| `Color#setGreenRelative` | pending | pending | pending |
| `Color#setHsl` | pending | pending | pending |
| `Color#setHsla` | pending | pending | pending |
| `Color#setHsv` | pending | pending | pending |
| `Color#setHsva` | pending | pending | pending |
| `Color#setPaintColor` | pending | pending | pending |
| `Color#setRed` | pending | pending | pending |
| `Color#setRedRelative` | pending | pending | pending |
| `Color#setRgb` | pending | pending | pending |
| `Color#setRgba` | pending | pending | pending |
| `Color#toStringReadable` | pending | pending | pending |

### ConsoleConfigurator#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ConsoleConfigurator#clearStates` | pending | pending | pending |
| `ConsoleConfigurator#contentBackgroundAlpha` | pending | pending | pending |
| `ConsoleConfigurator#contentBackgroundColor` | pending | pending | pending |
| `ConsoleConfigurator#contentBackgroundTint` | pending | pending | pending |
| `ConsoleConfigurator#contentTextColors` | pending | pending | pending |
| `ConsoleConfigurator#contentTextSize` | pending | pending | pending |
| `ConsoleConfigurator#exitOnCloseTimeout` | pending | pending | pending |
| `ConsoleConfigurator#gravity` | pending | pending | pending |
| `ConsoleConfigurator#isAvoidStatusBar` | pending | pending | pending |
| `ConsoleConfigurator#isColorful` | pending | pending | pending |
| `ConsoleConfigurator#isExitOnClose` | pending | pending | pending |
| `ConsoleConfigurator#isInputVisible` | pending | pending | pending |
| `ConsoleConfigurator#isTimeVisible` | pending | pending | pending |
| `ConsoleConfigurator#isTouchable` | pending | pending | pending |
| `ConsoleConfigurator#position` | pending | pending | pending |
| `ConsoleConfigurator#setAvoidStatusBar` | pending | pending | pending |
| `ConsoleConfigurator#setBackgroundAlpha` | pending | pending | pending |
| `ConsoleConfigurator#setBackgroundColor` | pending | pending | pending |
| `ConsoleConfigurator#setBackgroundTint` | pending | pending | pending |
| `ConsoleConfigurator#setColorful` | pending | pending | pending |
| `ConsoleConfigurator#setContentBackgroundAlpha` | pending | pending | pending |
| `ConsoleConfigurator#setContentBackgroundColor` | pending | pending | pending |
| `ConsoleConfigurator#setContentBackgroundTint` | pending | pending | pending |
| `ConsoleConfigurator#setContentTextColors` | pending | pending | pending |
| `ConsoleConfigurator#setContentTextSize` | pending | pending | pending |
| `ConsoleConfigurator#setExitOnClose` | pending | pending | pending |
| `ConsoleConfigurator#setGravity` | pending | pending | pending |
| `ConsoleConfigurator#setInputVisible` | pending | pending | pending |
| `ConsoleConfigurator#setPosition` | pending | pending | pending |
| `ConsoleConfigurator#setSize` | pending | pending | pending |
| `ConsoleConfigurator#setTextColor` | pending | pending | pending |
| `ConsoleConfigurator#setTextSize` | pending | pending | pending |
| `ConsoleConfigurator#setTimeFormat` | pending | pending | pending |
| `ConsoleConfigurator#setTimeVisible` | pending | pending | pending |
| `ConsoleConfigurator#setTitle` | pending | pending | pending |
| `ConsoleConfigurator#setTitleBackgroundAlpha` | pending | pending | pending |
| `ConsoleConfigurator#setTitleBackgroundColor` | pending | pending | pending |
| `ConsoleConfigurator#setTitleBackgroundTint` | pending | pending | pending |
| `ConsoleConfigurator#setTitleIconsTint` | pending | pending | pending |
| `ConsoleConfigurator#setTitleTextColor` | pending | pending | pending |
| `ConsoleConfigurator#setTitleTextSize` | pending | pending | pending |
| `ConsoleConfigurator#setTouchThrough` | pending | pending | pending |
| `ConsoleConfigurator#setTouchable` | pending | pending | pending |
| `ConsoleConfigurator#show` | pending | pending | pending |
| `ConsoleConfigurator#size` | pending | pending | pending |
| `ConsoleConfigurator#timeFormat` | pending | pending | pending |
| `ConsoleConfigurator#title` | pending | pending | pending |
| `ConsoleConfigurator#titleBackgroundAlpha` | pending | pending | pending |
| `ConsoleConfigurator#titleBackgroundColor` | pending | pending | pending |
| `ConsoleConfigurator#titleBackgroundTint` | pending | pending | pending |
| `ConsoleConfigurator#titleIconsTint` | pending | pending | pending |
| `ConsoleConfigurator#titleTextColor` | pending | pending | pending |
| `ConsoleConfigurator#titleTextSize` | pending | pending | pending |

### ContinuationCreator#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ContinuationCreator#await` | pending | pending | pending |
| `ContinuationCreator#resume` | pending | pending | pending |
| `ContinuationCreator#resumeError` | pending | pending | pending |

### Database#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Database#acquireReference` | pending | pending | pending |
| `Database#beginTransaction` | pending | pending | pending |
| `Database#beginTransactionNonExclusive` | pending | pending | pending |
| `Database#beginTransactionWithListener` | pending | pending | pending |
| `Database#beginTransactionWithListenerNonExclusive` | pending | pending | pending |
| `Database#close` | pending | pending | pending |
| `Database#compileStatement` | pending | pending | pending |
| `Database#delete` | pending | pending | pending |
| `Database#disableWriteAheadLogging` | pending | pending | pending |
| `Database#enableWriteAheadLogging` | pending | pending | pending |
| `Database#endTransaction` | pending | pending | pending |
| `Database#execSQL` | pending | pending | pending |
| `Database#getAttachedDbs` | pending | pending | pending |
| `Database#getMaximumSize` | pending | pending | pending |
| `Database#getPageSize` | pending | pending | pending |
| `Database#getPath` | pending | pending | pending |
| `Database#getTypeAdapter` | pending | pending | pending |
| `Database#getVersion` | pending | pending | pending |
| `Database#inTransaction` | pending | pending | pending |
| `Database#insert` | pending | pending | pending |
| `Database#insertOrThrow` | pending | pending | pending |
| `Database#insertWithOnConflict` | pending | pending | pending |
| `Database#isDatabaseIntegrityOk` | pending | pending | pending |
| `Database#isDbLockedByCurrentThread` | pending | pending | pending |
| `Database#isOpen` | pending | pending | pending |
| `Database#isReadOnly` | pending | pending | pending |
| `Database#isWriteAheadLoggingEnabled` | pending | pending | pending |
| `Database#needUpgrade` | pending | pending | pending |
| `Database#onCreate` | pending | pending | pending |
| `Database#onDowngrade` | pending | pending | pending |
| `Database#onOpen` | pending | pending | pending |
| `Database#onUpgrade` | pending | pending | pending |
| `Database#query` | pending | pending | pending |
| `Database#queryWithFactory` | pending | pending | pending |
| `Database#rawQuery` | pending | pending | pending |
| `Database#rawQueryWithFactory` | pending | pending | pending |
| `Database#releaseReference` | pending | pending | pending |
| `Database#replace` | pending | pending | pending |
| `Database#replaceOrThrow` | pending | pending | pending |
| `Database#setForeignKeyConstraintsEnabled` | pending | pending | pending |
| `Database#setLocale` | pending | pending | pending |
| `Database#setMaxSqlCacheSize` | pending | pending | pending |
| `Database#setMaximumSize` | pending | pending | pending |
| `Database#setPageSize` | pending | pending | pending |
| `Database#setTransactionSuccessful` | pending | pending | pending |
| `Database#setVersion` | pending | pending | pending |
| `Database#transaction` | pending | pending | pending |
| `Database#update` | pending | pending | pending |
| `Database#updateWithOnConflict` | pending | pending | pending |
| `Database#validateSql` | pending | pending | pending |
| `Database#yieldIfContendedSafely` | pending | pending | pending |

### Dialog#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Dialog#addContentView` | pending | pending | pending |
| `Dialog#addListener` | pending | pending | pending |
| `Dialog#cancel` | pending | pending | pending |
| `Dialog#clearSelectedIndices` | pending | pending | pending |
| `Dialog#closeOptionsMenu` | pending | pending | pending |
| `Dialog#create` | pending | pending | pending |
| `Dialog#defaultMaxListeners` | pending | pending | pending |
| `Dialog#dismiss` | pending | pending | pending |
| `Dialog#dispatchGenericMotionEvent` | pending | pending | pending |
| `Dialog#dispatchKeyEvent` | pending | pending | pending |
| `Dialog#dispatchKeyShortcutEvent` | pending | pending | pending |
| `Dialog#dispatchPopulateAccessibilityEvent` | pending | pending | pending |
| `Dialog#dispatchTouchEvent` | pending | pending | pending |
| `Dialog#dispatchTrackballEvent` | pending | pending | pending |
| `Dialog#emit` | pending | pending | pending |
| `Dialog#eventNames` | pending | pending | pending |
| `Dialog#findViewById` | pending | pending | pending |
| `Dialog#getActionBar` | pending | pending | pending |
| `Dialog#getActionButton` | pending | pending | pending |
| `Dialog#getBuilder` | pending | pending | pending |
| `Dialog#getContentView` | pending | pending | pending |
| `Dialog#getContext` | pending | pending | pending |
| `Dialog#getCurrentFocus` | pending | pending | pending |
| `Dialog#getCurrentProgress` | pending | pending | pending |
| `Dialog#getCustomView` | pending | pending | pending |
| `Dialog#getIconView` | pending | pending | pending |
| `Dialog#getInputEditText` | pending | pending | pending |
| `Dialog#getItems` | pending | pending | pending |
| `Dialog#getLayoutInflater` | pending | pending | pending |
| `Dialog#getMaxListeners` | pending | pending | pending |
| `Dialog#getMaxProgress` | pending | pending | pending |
| `Dialog#getOwnerActivity` | pending | pending | pending |
| `Dialog#getProgress` | pending | pending | pending |
| `Dialog#getProgressBar` | pending | pending | pending |
| `Dialog#getRecyclerView` | pending | pending | pending |
| `Dialog#getSearchEvent` | pending | pending | pending |
| `Dialog#getSelectedIndex` | pending | pending | pending |
| `Dialog#getSelectedIndices` | pending | pending | pending |
| `Dialog#getTag` | pending | pending | pending |
| `Dialog#getTitleView` | pending | pending | pending |
| `Dialog#getView` | pending | pending | pending |
| `Dialog#getVolumeControlStream` | pending | pending | pending |
| `Dialog#getWindow` | pending | pending | pending |
| `Dialog#hasActionButtons` | pending | pending | pending |
| `Dialog#hide` | pending | pending | pending |
| `Dialog#incrementProgress` | pending | pending | pending |
| `Dialog#invalidateOptionsMenu` | pending | pending | pending |
| `Dialog#isCancelled` | pending | pending | pending |
| `Dialog#isIndeterminateProgress` | pending | pending | pending |
| `Dialog#isPromptCheckBoxChecked` | pending | pending | pending |
| `Dialog#isShowing` | pending | pending | pending |
| `Dialog#listenerCount` | pending | pending | pending |
| `Dialog#listeners` | pending | pending | pending |
| `Dialog#notifyItemChanged` | pending | pending | pending |
| `Dialog#notifyItemInserted` | pending | pending | pending |
| `Dialog#notifyItemsChanged` | pending | pending | pending |
| `Dialog#numberOfActionButtons` | pending | pending | pending |
| `Dialog#on` | pending | pending | pending |
| `Dialog#onActionModeFinished` | pending | pending | pending |
| `Dialog#onActionModeStarted` | pending | pending | pending |
| `Dialog#onAttachedToWindow` | pending | pending | pending |
| `Dialog#onBackPressed` | pending | pending | pending |
| `Dialog#onClick` | pending | pending | pending |
| `Dialog#onContentChanged` | pending | pending | pending |
| `Dialog#onContextItemSelected` | pending | pending | pending |
| `Dialog#onContextMenuClosed` | pending | pending | pending |
| `Dialog#onCreateContextMenu` | pending | pending | pending |
| `Dialog#onCreateOptionsMenu` | pending | pending | pending |
| `Dialog#onCreatePanelMenu` | pending | pending | pending |
| `Dialog#onCreatePanelView` | pending | pending | pending |
| `Dialog#onDetachedFromWindow` | pending | pending | pending |
| `Dialog#onGenericMotionEvent` | pending | pending | pending |
| `Dialog#onItemSelected` | pending | pending | pending |
| `Dialog#onKeyDown` | pending | pending | pending |
| `Dialog#onKeyLongPress` | pending | pending | pending |
| `Dialog#onKeyMultiple` | pending | pending | pending |
| `Dialog#onKeyShortcut` | pending | pending | pending |
| `Dialog#onKeyUp` | pending | pending | pending |
| `Dialog#onMenuItemSelected` | pending | pending | pending |
| `Dialog#onMenuOpened` | pending | pending | pending |
| `Dialog#onOptionsItemSelected` | pending | pending | pending |
| `Dialog#onOptionsMenuClosed` | pending | pending | pending |
| `Dialog#onPanelClosed` | pending | pending | pending |
| `Dialog#onPrepareOptionsMenu` | pending | pending | pending |
| `Dialog#onPreparePanel` | pending | pending | pending |
| `Dialog#onProvideKeyboardShortcuts` | pending | pending | pending |
| `Dialog#onRestoreInstanceState` | pending | pending | pending |
| `Dialog#onSaveInstanceState` | pending | pending | pending |
| `Dialog#onSearchRequested` | pending | pending | pending |
| `Dialog#onShow` | pending | pending | pending |
| `Dialog#onTouchEvent` | pending | pending | pending |
| `Dialog#onTrackballEvent` | pending | pending | pending |
| `Dialog#onWindowAttributesChanged` | pending | pending | pending |
| `Dialog#onWindowFocusChanged` | pending | pending | pending |
| `Dialog#onWindowStartingActionMode` | pending | pending | pending |
| `Dialog#once` | pending | pending | pending |
| `Dialog#openContextMenu` | pending | pending | pending |
| `Dialog#openOptionsMenu` | pending | pending | pending |
| `Dialog#prependListener` | pending | pending | pending |
| `Dialog#prependOnceListener` | pending | pending | pending |
| `Dialog#registerForContextMenu` | pending | pending | pending |
| `Dialog#removeAllListeners` | pending | pending | pending |
| `Dialog#removeListener` | pending | pending | pending |
| `Dialog#requestWindowFeature` | pending | pending | pending |
| `Dialog#selectAllIndices` | pending | pending | pending |
| `Dialog#setActionButton` | pending | pending | pending |
| `Dialog#setCancelMessage` | pending | pending | pending |
| `Dialog#setCancelable` | pending | pending | pending |
| `Dialog#setCanceledOnTouchOutside` | pending | pending | pending |
| `Dialog#setContent` | pending | pending | pending |
| `Dialog#setContentView` | pending | pending | pending |
| `Dialog#setDismissMessage` | pending | pending | pending |
| `Dialog#setFeatureDrawable` | pending | pending | pending |
| `Dialog#setFeatureDrawableAlpha` | pending | pending | pending |
| `Dialog#setFeatureDrawableResource` | pending | pending | pending |
| `Dialog#setFeatureDrawableUri` | pending | pending | pending |
| `Dialog#setIcon` | pending | pending | pending |
| `Dialog#setIconAttribute` | pending | pending | pending |
| `Dialog#setItems` | pending | pending | pending |
| `Dialog#setMaxListeners` | pending | pending | pending |
| `Dialog#setMaxProgress` | pending | pending | pending |
| `Dialog#setOnBackPressedFromJs` | pending | pending | pending |
| `Dialog#setOnCancelListener` | pending | pending | pending |
| `Dialog#setOnDismissListener` | pending | pending | pending |
| `Dialog#setOnKeyListener` | pending | pending | pending |
| `Dialog#setOnShowListener` | pending | pending | pending |
| `Dialog#setOwnerActivity` | pending | pending | pending |
| `Dialog#setProgress` | pending | pending | pending |
| `Dialog#setProgressNumberFormat` | pending | pending | pending |
| `Dialog#setProgressPercentFormat` | pending | pending | pending |
| `Dialog#setPromptCheckBoxChecked` | pending | pending | pending |
| `Dialog#setSelectedIndex` | pending | pending | pending |
| `Dialog#setSelectedIndices` | pending | pending | pending |
| `Dialog#setTitle` | pending | pending | pending |
| `Dialog#setTypeface` | pending | pending | pending |
| `Dialog#setVolumeControlStream` | pending | pending | pending |
| `Dialog#show` | pending | pending | pending |
| `Dialog#showSnack` | pending | pending | pending |
| `Dialog#takeKeyEvents` | pending | pending | pending |
| `Dialog#unregisterForContextMenu` | pending | pending | pending |

### EpubBook#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `EpubBook#annotations` | pending | pending | pending |
| `EpubBook#close` | pending | pending | pending |
| `EpubBook#cover` | pending | pending | pending |
| `EpubBook#isClosed` | pending | pending | pending |
| `EpubBook#metadata` | pending | pending | pending |
| `EpubBook#path` | pending | pending | pending |
| `EpubBook#positions` | pending | pending | pending |
| `EpubBook#readingOrder` | pending | pending | pending |
| `EpubBook#resource` | pending | pending | pending |
| `EpubBook#search` | pending | pending | pending |
| `EpubBook#text` | pending | pending | pending |
| `EpubBook#textAll` | pending | pending | pending |
| `EpubBook#toString` | pending | pending | pending |
| `EpubBook#toc` | pending | pending | pending |

### EpubReaderSession#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `EpubReaderSession#addListener` | pending | pending | pending |
| `EpubReaderSession#bookmarks` | pending | pending | pending |
| `EpubReaderSession#chapterTitle` | pending | pending | pending |
| `EpubReaderSession#close` | pending | pending | pending |
| `EpubReaderSession#defaultMaxListeners` | pending | pending | pending |
| `EpubReaderSession#emit` | pending | pending | pending |
| `EpubReaderSession#emitSticky` | pending | pending | pending |
| `EpubReaderSession#eventNames` | pending | pending | pending |
| `EpubReaderSession#getMaxListeners` | pending | pending | pending |
| `EpubReaderSession#getTimer` | pending | pending | pending |
| `EpubReaderSession#goTo` | pending | pending | pending |
| `EpubReaderSession#href` | pending | pending | pending |
| `EpubReaderSession#isClosed` | pending | pending | pending |
| `EpubReaderSession#isOpen` | pending | pending | pending |
| `EpubReaderSession#listenerCount` | pending | pending | pending |
| `EpubReaderSession#listeners` | pending | pending | pending |
| `EpubReaderSession#locator` | pending | pending | pending |
| `EpubReaderSession#next` | pending | pending | pending |
| `EpubReaderSession#nextChapter` | pending | pending | pending |
| `EpubReaderSession#on` | pending | pending | pending |
| `EpubReaderSession#once` | pending | pending | pending |
| `EpubReaderSession#path` | pending | pending | pending |
| `EpubReaderSession#positions` | pending | pending | pending |
| `EpubReaderSession#prependListener` | pending | pending | pending |
| `EpubReaderSession#prependOnceListener` | pending | pending | pending |
| `EpubReaderSession#prev` | pending | pending | pending |
| `EpubReaderSession#prevChapter` | pending | pending | pending |
| `EpubReaderSession#progress` | pending | pending | pending |
| `EpubReaderSession#reason` | pending | pending | pending |
| `EpubReaderSession#removeAllListeners` | pending | pending | pending |
| `EpubReaderSession#removeListener` | pending | pending | pending |
| `EpubReaderSession#setMaxListeners` | pending | pending | pending |
| `EpubReaderSession#setPreferences` | pending | pending | pending |
| `EpubReaderSession#state` | pending | pending | pending |
| `EpubReaderSession#title` | pending | pending | pending |

### FloatyWindow#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `FloatyWindow#close` | pending | pending | pending |
| `FloatyWindow#disableFocus` | pending | pending | pending |
| `FloatyWindow#exitOnClose` | pending | pending | pending |
| `FloatyWindow#findView` | pending | pending | pending |
| `FloatyWindow#height` | pending | pending | pending |
| `FloatyWindow#isAdjustEnabled` | pending | pending | pending |
| `FloatyWindow#requestFocus` | pending | pending | pending |
| `FloatyWindow#setPosition` | pending | pending | pending |
| `FloatyWindow#setSize` | pending | pending | pending |
| `FloatyWindow#width` | pending | pending | pending |
| `FloatyWindow#x` | pending | pending | pending |
| `FloatyWindow#y` | pending | pending | pending |

### Flow#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Flow#appendText` | pending | pending | pending |
| `Flow#await` | pending | pending | pending |
| `Flow#back` | pending | pending | pending |
| `Flow#backToApp` | pending | pending | pending |
| `Flow#backUntil` | pending | pending | pending |
| `Flow#cancel` | pending | pending | pending |
| `Flow#catch` | pending | pending | pending |
| `Flow#clear` | pending | pending | pending |
| `Flow#clearFocus` | pending | pending | pending |
| `Flow#click` | pending | pending | pending |
| `Flow#clickAfter` | pending | pending | pending |
| `Flow#clickAny` | pending | pending | pending |
| `Flow#clickBounds` | pending | pending | pending |
| `Flow#clickBoundsAfter` | pending | pending | pending |
| `Flow#clickBoundsAny` | pending | pending | pending |
| `Flow#clickBoundsIfExists` | pending | pending | pending |
| `Flow#clickBoundsWait` | pending | pending | pending |
| `Flow#clickBoundsWhenStable` | pending | pending | pending |
| `Flow#clickBoundsWhenStableAfter` | pending | pending | pending |
| `Flow#clickIfExists` | pending | pending | pending |
| `Flow#clickWait` | pending | pending | pending |
| `Flow#clickWhenStable` | pending | pending | pending |
| `Flow#clickWhenStableAfter` | pending | pending | pending |
| `Flow#collapse` | pending | pending | pending |
| `Flow#collectList` | pending | pending | pending |
| `Flow#contextClick` | pending | pending | pending |
| `Flow#copy` | pending | pending | pending |
| `Flow#cut` | pending | pending | pending |
| `Flow#delay` | pending | pending | pending |
| `Flow#dismiss` | pending | pending | pending |
| `Flow#dismissNotificationShade` | pending | pending | pending |
| `Flow#dismissPopups` | pending | pending | pending |
| `Flow#else` | pending | pending | pending |
| `Flow#error` | pending | pending | pending |
| `Flow#expand` | pending | pending | pending |
| `Flow#filter` | pending | pending | pending |
| `Flow#finally` | pending | pending | pending |
| `Flow#findAny` | pending | pending | pending |
| `Flow#focus` | pending | pending | pending |
| `Flow#gesture` | pending | pending | pending |
| `Flow#gestures` | pending | pending | pending |
| `Flow#home` | pending | pending | pending |
| `Flow#imeEnter` | pending | pending | pending |
| `Flow#launchAndWait` | pending | pending | pending |
| `Flow#lockScreen` | pending | pending | pending |
| `Flow#longClick` | pending | pending | pending |
| `Flow#longClickAfter` | pending | pending | pending |
| `Flow#map` | pending | pending | pending |
| `Flow#notifications` | pending | pending | pending |
| `Flow#paste` | pending | pending | pending |
| `Flow#peek` | pending | pending | pending |
| `Flow#performAction` | pending | pending | pending |
| `Flow#powerDialog` | pending | pending | pending |
| `Flow#press` | pending | pending | pending |
| `Flow#quickSettings` | pending | pending | pending |
| `Flow#recents` | pending | pending | pending |
| `Flow#repeatUntil` | pending | pending | pending |
| `Flow#retry` | pending | pending | pending |
| `Flow#run` | pending | pending | pending |
| `Flow#scope` | pending | pending | pending |
| `Flow#scrollBackward` | pending | pending | pending |
| `Flow#scrollDown` | pending | pending | pending |
| `Flow#scrollForward` | pending | pending | pending |
| `Flow#scrollLeft` | pending | pending | pending |
| `Flow#scrollRight` | pending | pending | pending |
| `Flow#scrollUntil` | pending | pending | pending |
| `Flow#scrollUp` | pending | pending | pending |
| `Flow#select` | pending | pending | pending |
| `Flow#setText` | pending | pending | pending |
| `Flow#show` | pending | pending | pending |
| `Flow#sleep` | pending | pending | pending |
| `Flow#smartClick` | pending | pending | pending |
| `Flow#smartClickBounds` | pending | pending | pending |
| `Flow#splitScreen` | pending | pending | pending |
| `Flow#state` | pending | pending | pending |
| `Flow#step` | pending | pending | pending |
| `Flow#swipe` | pending | pending | pending |
| `Flow#sync` | pending | pending | pending |
| `Flow#takeScreenshot` | pending | pending | pending |
| `Flow#then` | pending | pending | pending |
| `Flow#timeout` | pending | pending | pending |
| `Flow#toPromise` | pending | pending | pending |
| `Flow#toString` | pending | pending | pending |
| `Flow#toggle` | pending | pending | pending |
| `Flow#typeInto` | pending | pending | pending |
| `Flow#value` | pending | pending | pending |
| `Flow#wait` | pending | pending | pending |
| `Flow#waitForActivity` | pending | pending | pending |
| `Flow#waitForEvent` | pending | pending | pending |
| `Flow#waitForGone` | pending | pending | pending |
| `Flow#waitForHidden` | pending | pending | pending |
| `Flow#waitForIdle` | pending | pending | pending |
| `Flow#waitForNotification` | pending | pending | pending |
| `Flow#waitForPackage` | pending | pending | pending |
| `Flow#waitForStable` | pending | pending | pending |
| `Flow#waitForStableThenClick` | pending | pending | pending |
| `Flow#waitForStableThenClickBounds` | pending | pending | pending |
| `Flow#waitForToast` | pending | pending | pending |
| `Flow#waitForVisible` | pending | pending | pending |
| `Flow#waitThenClick` | pending | pending | pending |
| `Flow#waitThenClickBounds` | pending | pending | pending |
| `Flow#waitThenLongClick` | pending | pending | pending |
| `Flow#waitUntil` | pending | pending | pending |
| `Flow#waitWhile` | pending | pending | pending |
| `Flow#whenPresent` | pending | pending | pending |
| `Flow#within` | pending | pending | pending |

### ImageFeatures#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ImageFeatures#count` | pending | pending | pending |
| `ImageFeatures#isRecycled` | pending | pending | pending |
| `ImageFeatures#javaObject` | pending | pending | pending |
| `ImageFeatures#method` | pending | pending | pending |
| `ImageFeatures#onRecycled` | pending | pending | pending |
| `ImageFeatures#oneShot` | pending | pending | pending |
| `ImageFeatures#recycle` | pending | pending | pending |
| `ImageFeatures#recycled` | pending | pending | pending |
| `ImageFeatures#region` | pending | pending | pending |
| `ImageFeatures#scale` | pending | pending | pending |
| `ImageFeatures#setOneShot` | pending | pending | pending |
| `ImageFeatures#shoot` | pending | pending | pending |

### ImageWrapper#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ImageWrapper#bgrMat` | pending | pending | pending |
| `ImageWrapper#bitmap` | pending | pending | pending |
| `ImageWrapper#ensureNotRecycled` | pending | pending | pending |
| `ImageWrapper#height` | pending | pending | pending |
| `ImageWrapper#isRecycled` | pending | pending | pending |
| `ImageWrapper#mat` | pending | pending | pending |
| `ImageWrapper#oneShot` | pending | pending | pending |
| `ImageWrapper#pixel` | pending | pending | pending |
| `ImageWrapper#plane` | pending | pending | pending |
| `ImageWrapper#recycle` | pending | pending | pending |
| `ImageWrapper#saveTo` | pending | pending | pending |
| `ImageWrapper#scriptRuntime` | pending | pending | pending |
| `ImageWrapper#setOneShot` | pending | pending | pending |
| `ImageWrapper#shoot` | pending | pending | pending |
| `ImageWrapper#size` | pending | pending | pending |
| `ImageWrapper#width` | pending | pending | pending |

### InjectableWebClient#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `InjectableWebClient#inject` | pending | pending | pending |
| `InjectableWebClient#injectAndWait` | pending | pending | pending |
| `InjectableWebClient#onPageFinished` | pending | pending | pending |

### InjectableWebView#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `InjectableWebView#inject` | pending | pending | pending |

### IntentTask#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `IntentTask#TABLE` | pending | pending | pending |
| `IntentTask#getAction` | pending | pending | pending |
| `IntentTask#getCategory` | pending | pending | pending |
| `IntentTask#getDataType` | pending | pending | pending |
| `IntentTask#getExtras` | pending | pending | pending |
| `IntentTask#getId` | pending | pending | pending |
| `IntentTask#getIntentFilter` | pending | pending | pending |
| `IntentTask#getProjectRoot` | pending | pending | pending |
| `IntentTask#getScriptPath` | pending | pending | pending |
| `IntentTask#getSourceKind` | pending | pending | pending |
| `IntentTask#isLocal` | pending | pending | pending |
| `IntentTask#setAction` | pending | pending | pending |
| `IntentTask#setCategory` | pending | pending | pending |
| `IntentTask#setDataType` | pending | pending | pending |
| `IntentTask#setExtras` | pending | pending | pending |
| `IntentTask#setId` | pending | pending | pending |
| `IntentTask#setLocal` | pending | pending | pending |
| `IntentTask#setProjectRoot` | pending | pending | pending |
| `IntentTask#setScriptPath` | pending | pending | pending |
| `IntentTask#setScriptPathAndCaptureContext` | pending | pending | pending |
| `IntentTask#setSourceKind` | pending | pending | pending |

### MailClient#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `MailClient#account` | pending | pending | pending |
| `MailClient#append` | pending | pending | pending |
| `MailClient#appendAsync` | pending | pending | pending |
| `MailClient#close` | pending | pending | pending |
| `MailClient#copy` | pending | pending | pending |
| `MailClient#copyAsync` | pending | pending | pending |
| `MailClient#createFolder` | pending | pending | pending |
| `MailClient#createFolderAsync` | pending | pending | pending |
| `MailClient#delete` | pending | pending | pending |
| `MailClient#deleteAsync` | pending | pending | pending |
| `MailClient#deleteFolder` | pending | pending | pending |
| `MailClient#deleteFolderAsync` | pending | pending | pending |
| `MailClient#download` | pending | pending | pending |
| `MailClient#downloadAsync` | pending | pending | pending |
| `MailClient#expunge` | pending | pending | pending |
| `MailClient#expungeAsync` | pending | pending | pending |
| `MailClient#fetch` | pending | pending | pending |
| `MailClient#fetchAsync` | pending | pending | pending |
| `MailClient#fetchBody` | pending | pending | pending |
| `MailClient#fetchBodyAsync` | pending | pending | pending |
| `MailClient#flag` | pending | pending | pending |
| `MailClient#flagAsync` | pending | pending | pending |
| `MailClient#folder` | pending | pending | pending |
| `MailClient#folderStatus` | pending | pending | pending |
| `MailClient#folderStatusAsync` | pending | pending | pending |
| `MailClient#folders` | pending | pending | pending |
| `MailClient#foldersAsync` | pending | pending | pending |
| `MailClient#get` | pending | pending | pending |
| `MailClient#getAsync` | pending | pending | pending |
| `MailClient#isClosed` | pending | pending | pending |
| `MailClient#isConnected` | pending | pending | pending |
| `MailClient#markRead` | pending | pending | pending |
| `MailClient#markReadAsync` | pending | pending | pending |
| `MailClient#markUnread` | pending | pending | pending |
| `MailClient#markUnreadAsync` | pending | pending | pending |
| `MailClient#move` | pending | pending | pending |
| `MailClient#moveAsync` | pending | pending | pending |
| `MailClient#raw` | pending | pending | pending |
| `MailClient#rawAsync` | pending | pending | pending |
| `MailClient#renameFolder` | pending | pending | pending |
| `MailClient#renameFolderAsync` | pending | pending | pending |
| `MailClient#search` | pending | pending | pending |
| `MailClient#searchAsync` | pending | pending | pending |
| `MailClient#send` | pending | pending | pending |
| `MailClient#sendAsync` | pending | pending | pending |
| `MailClient#setFlags` | pending | pending | pending |
| `MailClient#setFlagsAsync` | pending | pending | pending |
| `MailClient#test` | pending | pending | pending |
| `MailClient#testAsync` | pending | pending | pending |
| `MailClient#toString` | pending | pending | pending |
| `MailClient#unflag` | pending | pending | pending |
| `MailClient#unflagAsync` | pending | pending | pending |
| `MailClient#watch` | pending | pending | pending |

### Mat#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Mat#convertTo` | pending | pending | pending |
| `Mat#from` | pending | pending | pending |
| `Mat#getCreatedCount` | pending | pending | pending |
| `Mat#getFinalizedCount` | pending | pending | pending |
| `Mat#getLiveCount` | pending | pending | pending |
| `Mat#getReleasedCount` | pending | pending | pending |
| `Mat#isReleased` | pending | pending | pending |
| `Mat#ones` | pending | pending | pending |
| `Mat#release` | pending | pending | pending |

### MatchingResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `MatchingResult#best` | pending | pending | pending |
| `MatchingResult#bottommost` | pending | pending | pending |
| `MatchingResult#filter` | pending | pending | pending |
| `MatchingResult#findMax` | pending | pending | pending |
| `MatchingResult#first` | pending | pending | pending |
| `MatchingResult#isEmpty` | pending | pending | pending |
| `MatchingResult#isNotEmpty` | pending | pending | pending |
| `MatchingResult#last` | pending | pending | pending |
| `MatchingResult#leftmost` | pending | pending | pending |
| `MatchingResult#matches` | pending | pending | pending |
| `MatchingResult#points` | pending | pending | pending |
| `MatchingResult#rightmost` | pending | pending | pending |
| `MatchingResult#size` | pending | pending | pending |
| `MatchingResult#sortBy` | pending | pending | pending |
| `MatchingResult#topmost` | pending | pending | pending |
| `MatchingResult#worst` | pending | pending | pending |

### Mathx

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Mathx.avg` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.ceilLog` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.ceilPow` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.cv` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.dist` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.floorLog` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.floorPow` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.logMn` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.max` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.mean` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.median` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.min` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.mode` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.randFloat` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.randInt` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.random` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.randomFloat` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.randomInt` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.roundLog` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.roundPow` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.std` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.sum` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Mathx.var` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |

### Numberx

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Numberx.ICU` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.check` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.clamp` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.clampTo` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.ensureNumber` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.ensureNumberLike` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.ensureNumbersLike` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.padEnd` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.padStart` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.parseAny` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.parseFloat` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.parsePercent` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.parseRatio` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `Numberx.toFixedNum` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |

### ObjectFrame#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ObjectFrame#angle` | pending | pending | pending |
| `ObjectFrame#bottomLeft` | pending | pending | pending |
| `ObjectFrame#bottomRight` | pending | pending | pending |
| `ObjectFrame#bounds` | pending | pending | pending |
| `ObjectFrame#center` | pending | pending | pending |
| `ObjectFrame#centerX` | pending | pending | pending |
| `ObjectFrame#centerY` | pending | pending | pending |
| `ObjectFrame#height` | pending | pending | pending |
| `ObjectFrame#inliers` | pending | pending | pending |
| `ObjectFrame#matches` | pending | pending | pending |
| `ObjectFrame#points` | pending | pending | pending |
| `ObjectFrame#summary` | pending | pending | pending |
| `ObjectFrame#topLeft` | pending | pending | pending |
| `ObjectFrame#topRight` | pending | pending | pending |
| `ObjectFrame#width` | pending | pending | pending |

### PReadableTextFile#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PReadableTextFile#close` | pending | pending | pending |
| `PReadableTextFile#getPath` | pending | pending | pending |
| `PReadableTextFile#read` | pending | pending | pending |
| `PReadableTextFile#readline` | pending | pending | pending |
| `PReadableTextFile#readlines` | pending | pending | pending |

### PWritableTextFile#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PWritableTextFile#close` | pending | pending | pending |
| `PWritableTextFile#flush` | pending | pending | pending |
| `PWritableTextFile#getPath` | pending | pending | pending |
| `PWritableTextFile#open` | pending | pending | pending |
| `PWritableTextFile#write` | pending | pending | pending |
| `PWritableTextFile#writeline` | pending | pending | pending |
| `PWritableTextFile#writelines` | pending | pending | pending |

### PngQuantizationFileResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PngQuantizationFileResult#path` | pending | pending | pending |
| `PngQuantizationFileResult#peakWorkingMemoryBytes` | pending | pending | pending |
| `PngQuantizationFileResult#quality` | pending | pending | pending |
| `PngQuantizationFileResult#quantizationError` | pending | pending | pending |
| `PngQuantizationFileResult#size` | pending | pending | pending |

### PngQuantizationResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `PngQuantizationResult#bytes` | pending | pending | pending |
| `PngQuantizationResult#peakWorkingMemoryBytes` | pending | pending | pending |
| `PngQuantizationResult#quality` | pending | pending | pending |
| `PngQuantizationResult#quantizationError` | pending | pending | pending |
| `PngQuantizationResult#size` | pending | pending | pending |

### RawWindow#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `RawWindow#close` | pending | pending | pending |
| `RawWindow#disableFocus` | pending | pending | pending |
| `RawWindow#exitOnClose` | pending | pending | pending |
| `RawWindow#findView` | pending | pending | pending |
| `RawWindow#height` | pending | pending | pending |
| `RawWindow#requestFocus` | pending | pending | pending |
| `RawWindow#setPosition` | pending | pending | pending |
| `RawWindow#setSize` | pending | pending | pending |
| `RawWindow#setTouchable` | pending | pending | pending |
| `RawWindow#width` | pending | pending | pending |
| `RawWindow#x` | pending | pending | pending |
| `RawWindow#y` | pending | pending | pending |

### ScreenCaptureOptions#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ScreenCaptureOptions#density` | pending | pending | pending |
| `ScreenCaptureOptions#height` | pending | pending | pending |
| `ScreenCaptureOptions#isAsync` | pending | pending | pending |
| `ScreenCaptureOptions#orientation` | pending | pending | pending |
| `ScreenCaptureOptions#width` | pending | pending | pending |

### ScriptEngine#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ScriptEngine#TAG_ENV_PATH` | pending | pending | pending |
| `ScriptEngine#TAG_SOURCE` | pending | pending | pending |
| `ScriptEngine#TAG_WORKING_DIRECTORY` | pending | pending | pending |
| `ScriptEngine#cwd` | pending | pending | pending |
| `ScriptEngine#destroy` | pending | pending | pending |
| `ScriptEngine#emit` | pending | pending | pending |
| `ScriptEngine#execute` | pending | pending | pending |
| `ScriptEngine#forceStop` | pending | pending | pending |
| `ScriptEngine#getBackingSourcePath` | pending | pending | pending |
| `ScriptEngine#getExecArgv` | pending | pending | pending |
| `ScriptEngine#getId` | pending | pending | pending |
| `ScriptEngine#getRuntime` | pending | pending | pending |
| `ScriptEngine#getSource` | pending | pending | pending |
| `ScriptEngine#getSourceDirectory` | pending | pending | pending |
| `ScriptEngine#getSourcePath` | pending | pending | pending |
| `ScriptEngine#getSourceUri` | pending | pending | pending |
| `ScriptEngine#getStartTime` | pending | pending | pending |
| `ScriptEngine#getTag` | pending | pending | pending |
| `ScriptEngine#getUncaughtException` | pending | pending | pending |
| `ScriptEngine#hasFeature` | pending | pending | pending |
| `ScriptEngine#init` | pending | pending | pending |
| `ScriptEngine#isDestroyed` | pending | pending | pending |
| `ScriptEngine#put` | pending | pending | pending |
| `ScriptEngine#setExecArgv` | pending | pending | pending |
| `ScriptEngine#setId` | pending | pending | pending |
| `ScriptEngine#setOnDestroyListener` | pending | pending | pending |
| `ScriptEngine#setRuntime` | pending | pending | pending |
| `ScriptEngine#setTag` | pending | pending | pending |
| `ScriptEngine#uncaughtException` | pending | pending | pending |

### ScriptExecution#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ScriptExecution#NO_ID` | pending | pending | pending |
| `ScriptExecution#getAgentContext` | pending | pending | pending |
| `ScriptExecution#getAgentParametersJson` | pending | pending | pending |
| `ScriptExecution#getAgentPresetName` | pending | pending | pending |
| `ScriptExecution#getAgentResult` | pending | pending | pending |
| `ScriptExecution#getAgentRunId` | pending | pending | pending |
| `ScriptExecution#getConfig` | pending | pending | pending |
| `ScriptExecution#getEngine` | pending | pending | pending |
| `ScriptExecution#getId` | pending | pending | pending |
| `ScriptExecution#getListener` | pending | pending | pending |
| `ScriptExecution#getSource` | pending | pending | pending |
| `ScriptExecution#isFinished` | pending | pending | pending |
| `ScriptExecution#setAgentResult` | pending | pending | pending |

### ShellProcess#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ShellProcess#addListener` | pending | pending | pending |
| `ShellProcess#closeInput` | pending | pending | pending |
| `ShellProcess#command` | pending | pending | pending |
| `ShellProcess#exitCode` | pending | pending | pending |
| `ShellProcess#getErrorStream` | pending | pending | pending |
| `ShellProcess#getInputStream` | pending | pending | pending |
| `ShellProcess#getOutputStream` | pending | pending | pending |
| `ShellProcess#isAlive` | pending | pending | pending |
| `ShellProcess#kill` | pending | pending | pending |
| `ShellProcess#listenerCount` | pending | pending | pending |
| `ShellProcess#mode` | pending | pending | pending |
| `ShellProcess#off` | pending | pending | pending |
| `ShellProcess#on` | pending | pending | pending |
| `ShellProcess#once` | pending | pending | pending |
| `ShellProcess#pid` | pending | pending | pending |
| `ShellProcess#prependListener` | pending | pending | pending |
| `ShellProcess#prependOnceListener` | pending | pending | pending |
| `ShellProcess#readErrorLine` | pending | pending | pending |
| `ShellProcess#readLine` | pending | pending | pending |
| `ShellProcess#removeAllListeners` | pending | pending | pending |
| `ShellProcess#removeListener` | pending | pending | pending |
| `ShellProcess#toString` | pending | pending | pending |
| `ShellProcess#waitFor` | pending | pending | pending |
| `ShellProcess#write` | pending | pending | pending |
| `ShellProcess#writeLine` | pending | pending | pending |

### ShellResult#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ShellResult#code` | pending | pending | pending |
| `ShellResult#error` | pending | pending | pending |
| `ShellResult#result` | pending | pending | pending |
| `ShellResult#throwIfError` | pending | pending | pending |
| `ShellResult#toJson` | pending | pending | pending |

### Storage#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Storage#clear` | pending | pending | pending |
| `Storage#clearSync` | pending | pending | pending |
| `Storage#contains` | pending | pending | pending |
| `Storage#get` | pending | pending | pending |
| `Storage#name` | pending | pending | pending |
| `Storage#put` | pending | pending | pending |
| `Storage#putSync` | pending | pending | pending |
| `Storage#remove` | pending | pending | pending |
| `Storage#removeSync` | pending | pending | pending |
| `Storage#selfRemove` | pending | pending | pending |
| `Storage#selfRemoveSync` | pending | pending | pending |
| `Storage#size` | pending | pending | pending |

### ThemeColor#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ThemeColor#colorAccent` | pending | pending | pending |
| `ThemeColor#colorPrimary` | pending | pending | pending |
| `ThemeColor#colorPrimaryDark` | pending | pending | pending |
| `ThemeColor#getColorAccent` | pending | pending | pending |
| `ThemeColor#getColorPrimary` | pending | pending | pending |
| `ThemeColor#getColorPrimaryDark` | pending | pending | pending |
| `ThemeColor#isLuminanceDark` | pending | pending | pending |
| `ThemeColor#isLuminanceLight` | pending | pending | pending |
| `ThemeColor#readFrom` | pending | pending | pending |
| `ThemeColor#saveIn` | pending | pending | pending |

### TimedTask#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `TimedTask#END_MODE_COUNT` | pending | pending | pending |
| `TimedTask#END_MODE_FOREVER` | pending | pending | pending |
| `TimedTask#END_MODE_UNTIL_DATE` | pending | pending | pending |
| `TimedTask#FLAG_FRIDAY` | pending | pending | pending |
| `TimedTask#FLAG_MONDAY` | pending | pending | pending |
| `TimedTask#FLAG_SATURDAY` | pending | pending | pending |
| `TimedTask#FLAG_SUNDAY` | pending | pending | pending |
| `TimedTask#FLAG_THURSDAY` | pending | pending | pending |
| `TimedTask#FLAG_TUESDAY` | pending | pending | pending |
| `TimedTask#FLAG_WEDNESDAY` | pending | pending | pending |
| `TimedTask#REPEAT_MODE_COUNTDOWN` | pending | pending | pending |
| `TimedTask#REPEAT_MODE_DAILY` | pending | pending | pending |
| `TimedTask#REPEAT_MODE_DISPOSABLE` | pending | pending | pending |
| `TimedTask#REPEAT_MODE_MONTHLY` | pending | pending | pending |
| `TimedTask#REPEAT_MODE_UNKNOWN` | pending | pending | pending |
| `TimedTask#REPEAT_MODE_WEEKLY` | pending | pending | pending |
| `TimedTask#REPEAT_MODE_YEARLY` | pending | pending | pending |
| `TimedTask#TABLE` | pending | pending | pending |
| `TimedTask#captureLaunchContext` | pending | pending | pending |
| `TimedTask#countdownTask` | pending | pending | pending |
| `TimedTask#createIntent` | pending | pending | pending |
| `TimedTask#createPendingIntent` | pending | pending | pending |
| `TimedTask#dailyTask` | pending | pending | pending |
| `TimedTask#disposableTask` | pending | pending | pending |
| `TimedTask#getDayOfMonth` | pending | pending | pending |
| `TimedTask#getDayOfWeekTimeFlag` | pending | pending | pending |
| `TimedTask#getDelay` | pending | pending | pending |
| `TimedTask#getEndMode` | pending | pending | pending |
| `TimedTask#getEndValue` | pending | pending | pending |
| `TimedTask#getFinishedTimes` | pending | pending | pending |
| `TimedTask#getId` | pending | pending | pending |
| `TimedTask#getInterval` | pending | pending | pending |
| `TimedTask#getLoopTimes` | pending | pending | pending |
| `TimedTask#getMillis` | pending | pending | pending |
| `TimedTask#getMonthOfYear` | pending | pending | pending |
| `TimedTask#getNextTime` | pending | pending | pending |
| `TimedTask#getProjectRoot` | pending | pending | pending |
| `TimedTask#getRepeatMode` | pending | pending | pending |
| `TimedTask#getScriptPath` | pending | pending | pending |
| `TimedTask#getSourceKind` | pending | pending | pending |
| `TimedTask#getTimeFlag` | pending | pending | pending |
| `TimedTask#hasDayOfWeek` | pending | pending | pending |
| `TimedTask#isCountdown` | pending | pending | pending |
| `TimedTask#isDaily` | pending | pending | pending |
| `TimedTask#isDisposable` | pending | pending | pending |
| `TimedTask#isMonthly` | pending | pending | pending |
| `TimedTask#isScheduled` | pending | pending | pending |
| `TimedTask#isWeekly` | pending | pending | pending |
| `TimedTask#isYearly` | pending | pending | pending |
| `TimedTask#monthlyTask` | pending | pending | pending |
| `TimedTask#notifyFinished` | pending | pending | pending |
| `TimedTask#setDayOfMonth` | pending | pending | pending |
| `TimedTask#setDelay` | pending | pending | pending |
| `TimedTask#setEndMode` | pending | pending | pending |
| `TimedTask#setEndValue` | pending | pending | pending |
| `TimedTask#setFinishedTimes` | pending | pending | pending |
| `TimedTask#setId` | pending | pending | pending |
| `TimedTask#setInterval` | pending | pending | pending |
| `TimedTask#setLoopTimes` | pending | pending | pending |
| `TimedTask#setMillis` | pending | pending | pending |
| `TimedTask#setMonthOfYear` | pending | pending | pending |
| `TimedTask#setProjectRoot` | pending | pending | pending |
| `TimedTask#setRepeatMode` | pending | pending | pending |
| `TimedTask#setScheduled` | pending | pending | pending |
| `TimedTask#setScriptPath` | pending | pending | pending |
| `TimedTask#setSourceKind` | pending | pending | pending |
| `TimedTask#setTimeFlag` | pending | pending | pending |
| `TimedTask#weeklyTask` | pending | pending | pending |
| `TimedTask#yearlyTask` | pending | pending | pending |

### TtsUtterance#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `TtsUtterance#addListener` | pending | pending | pending |
| `TtsUtterance#cancel` | pending | pending | pending |
| `TtsUtterance#defaultMaxListeners` | pending | pending | pending |
| `TtsUtterance#emit` | pending | pending | pending |
| `TtsUtterance#emitSticky` | pending | pending | pending |
| `TtsUtterance#eventNames` | pending | pending | pending |
| `TtsUtterance#getMaxListeners` | pending | pending | pending |
| `TtsUtterance#getTimer` | pending | pending | pending |
| `TtsUtterance#id` | pending | pending | pending |
| `TtsUtterance#isDone` | pending | pending | pending |
| `TtsUtterance#listenerCount` | pending | pending | pending |
| `TtsUtterance#listeners` | pending | pending | pending |
| `TtsUtterance#on` | pending | pending | pending |
| `TtsUtterance#once` | pending | pending | pending |
| `TtsUtterance#operation` | pending | pending | pending |
| `TtsUtterance#prependListener` | pending | pending | pending |
| `TtsUtterance#prependOnceListener` | pending | pending | pending |
| `TtsUtterance#removeAllListeners` | pending | pending | pending |
| `TtsUtterance#removeListener` | pending | pending | pending |
| `TtsUtterance#result` | pending | pending | pending |
| `TtsUtterance#setMaxListeners` | pending | pending | pending |
| `TtsUtterance#state` | pending | pending | pending |
| `TtsUtterance#stop` | pending | pending | pending |
| `TtsUtterance#text` | pending | pending | pending |
| `TtsUtterance#utterance` | pending | pending | pending |

### UiObject#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `UiObject#accessibilityFocus` | pending | pending | pending |
| `UiObject#accessibilityFocused` | pending | pending | pending |
| `UiObject#actionNames` | pending | pending | pending |
| `UiObject#bottom` | pending | pending | pending |
| `UiObject#bounds` | pending | pending | pending |
| `UiObject#boundsBottom` | pending | pending | pending |
| `UiObject#boundsCenterX` | pending | pending | pending |
| `UiObject#boundsCenterY` | pending | pending | pending |
| `UiObject#boundsExactCenterX` | pending | pending | pending |
| `UiObject#boundsExactCenterY` | pending | pending | pending |
| `UiObject#boundsHeight` | pending | pending | pending |
| `UiObject#boundsInParent` | pending | pending | pending |
| `UiObject#boundsInScreen` | pending | pending | pending |
| `UiObject#boundsLeft` | pending | pending | pending |
| `UiObject#boundsRight` | pending | pending | pending |
| `UiObject#boundsTop` | pending | pending | pending |
| `UiObject#boundsWidth` | pending | pending | pending |
| `UiObject#brother` | pending | pending | pending |
| `UiObject#center` | pending | pending | pending |
| `UiObject#centerX` | pending | pending | pending |
| `UiObject#centerY` | pending | pending | pending |
| `UiObject#checkable` | pending | pending | pending |
| `UiObject#checked` | pending | pending | pending |
| `UiObject#child` | pending | pending | pending |
| `UiObject#childCount` | pending | pending | pending |
| `UiObject#children` | pending | pending | pending |
| `UiObject#className` | pending | pending | pending |
| `UiObject#clearAccessibilityFocus` | pending | pending | pending |
| `UiObject#clearFocus` | pending | pending | pending |
| `UiObject#clearSelection` | pending | pending | pending |
| `UiObject#click` | pending | pending | pending |
| `UiObject#clickBounds` | pending | pending | pending |
| `UiObject#clickable` | pending | pending | pending |
| `UiObject#collapse` | pending | pending | pending |
| `UiObject#column` | pending | pending | pending |
| `UiObject#columnCount` | pending | pending | pending |
| `UiObject#columnSpan` | pending | pending | pending |
| `UiObject#compass` | pending | pending | pending |
| `UiObject#content` | pending | pending | pending |
| `UiObject#contentInvalid` | pending | pending | pending |
| `UiObject#contextClick` | pending | pending | pending |
| `UiObject#contextClickable` | pending | pending | pending |
| `UiObject#copy` | pending | pending | pending |
| `UiObject#cut` | pending | pending | pending |
| `UiObject#depth` | pending | pending | pending |
| `UiObject#desc` | pending | pending | pending |
| `UiObject#dismiss` | pending | pending | pending |
| `UiObject#dismissable` | pending | pending | pending |
| `UiObject#dragCancel` | pending | pending | pending |
| `UiObject#dragDrop` | pending | pending | pending |
| `UiObject#dragStart` | pending | pending | pending |
| `UiObject#drawingOrder` | pending | pending | pending |
| `UiObject#dumpSubtree` | pending | pending | pending |
| `UiObject#editable` | pending | pending | pending |
| `UiObject#enabled` | pending | pending | pending |
| `UiObject#exactCenterX` | pending | pending | pending |
| `UiObject#exactCenterY` | pending | pending | pending |
| `UiObject#expand` | pending | pending | pending |
| `UiObject#find` | pending | pending | pending |
| `UiObject#findAccessibilityNodeInfosByText` | pending | pending | pending |
| `UiObject#findAccessibilityNodeInfosByViewId` | pending | pending | pending |
| `UiObject#findOne` | pending | pending | pending |
| `UiObject#fingerprint` | pending | pending | pending |
| `UiObject#firstChild` | pending | pending | pending |
| `UiObject#firstSibling` | pending | pending | pending |
| `UiObject#focus` | pending | pending | pending |
| `UiObject#focusable` | pending | pending | pending |
| `UiObject#focused` | pending | pending | pending |
| `UiObject#fullId` | pending | pending | pending |
| `UiObject#getChild` | pending | pending | pending |
| `UiObject#getParent` | pending | pending | pending |
| `UiObject#getText` | pending | pending | pending |
| `UiObject#hasAction` | pending | pending | pending |
| `UiObject#hasChildren` | pending | pending | pending |
| `UiObject#height` | pending | pending | pending |
| `UiObject#hideTooltip` | pending | pending | pending |
| `UiObject#id` | pending | pending | pending |
| `UiObject#idEntry` | pending | pending | pending |
| `UiObject#idHex` | pending | pending | pending |
| `UiObject#imeEnter` | pending | pending | pending |
| `UiObject#indexInParent` | pending | pending | pending |
| `UiObject#isShifted` | pending | pending | pending |
| `UiObject#isSimilar` | pending | pending | pending |
| `UiObject#isSingleton` | pending | pending | pending |
| `UiObject#isStale` | pending | pending | pending |
| `UiObject#lastChild` | pending | pending | pending |
| `UiObject#lastSibling` | pending | pending | pending |
| `UiObject#left` | pending | pending | pending |
| `UiObject#longClick` | pending | pending | pending |
| `UiObject#longClickable` | pending | pending | pending |
| `UiObject#moveWindow` | pending | pending | pending |
| `UiObject#multiLine` | pending | pending | pending |
| `UiObject#nextAtMovementGranularity` | pending | pending | pending |
| `UiObject#nextHtmlElement` | pending | pending | pending |
| `UiObject#nextSibling` | pending | pending | pending |
| `UiObject#offset` | pending | pending | pending |
| `UiObject#packageName` | pending | pending | pending |
| `UiObject#pageDown` | pending | pending | pending |
| `UiObject#pageLeft` | pending | pending | pending |
| `UiObject#pageRight` | pending | pending | pending |
| `UiObject#pageUp` | pending | pending | pending |
| `UiObject#parent` | pending | pending | pending |
| `UiObject#password` | pending | pending | pending |
| `UiObject#paste` | pending | pending | pending |
| `UiObject#performAction` | pending | pending | pending |
| `UiObject#point` | pending | pending | pending |
| `UiObject#pressAndHold` | pending | pending | pending |
| `UiObject#previousAtMovementGranularity` | pending | pending | pending |
| `UiObject#previousHtmlElement` | pending | pending | pending |
| `UiObject#previousSibling` | pending | pending | pending |
| `UiObject#recycle` | pending | pending | pending |
| `UiObject#right` | pending | pending | pending |
| `UiObject#row` | pending | pending | pending |
| `UiObject#rowCount` | pending | pending | pending |
| `UiObject#rowSpan` | pending | pending | pending |
| `UiObject#scrollBackward` | pending | pending | pending |
| `UiObject#scrollDown` | pending | pending | pending |
| `UiObject#scrollForward` | pending | pending | pending |
| `UiObject#scrollLeft` | pending | pending | pending |
| `UiObject#scrollRight` | pending | pending | pending |
| `UiObject#scrollTo` | pending | pending | pending |
| `UiObject#scrollUp` | pending | pending | pending |
| `UiObject#scrollable` | pending | pending | pending |
| `UiObject#select` | pending | pending | pending |
| `UiObject#selected` | pending | pending | pending |
| `UiObject#setProgress` | pending | pending | pending |
| `UiObject#setSelection` | pending | pending | pending |
| `UiObject#setText` | pending | pending | pending |
| `UiObject#show` | pending | pending | pending |
| `UiObject#showTextSuggestions` | pending | pending | pending |
| `UiObject#showTooltip` | pending | pending | pending |
| `UiObject#sibling` | pending | pending | pending |
| `UiObject#siblingCount` | pending | pending | pending |
| `UiObject#siblings` | pending | pending | pending |
| `UiObject#simpleId` | pending | pending | pending |
| `UiObject#size` | pending | pending | pending |
| `UiObject#snapshot` | pending | pending | pending |
| `UiObject#summary` | pending | pending | pending |
| `UiObject#text` | pending | pending | pending |
| `UiObject#toJSON` | pending | pending | pending |
| `UiObject#top` | pending | pending | pending |
| `UiObject#visibleToUser` | pending | pending | pending |
| `UiObject#waitForStable` | pending | pending | pending |
| `UiObject#waitUntilGone` | pending | pending | pending |
| `UiObject#width` | pending | pending | pending |

### UiObjectCollection#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `UiObjectCollection#accessibilityFocus` | pending | pending | pending |
| `UiObjectCollection#at` | pending | pending | pending |
| `UiObjectCollection#clearAccessibilityFocus` | pending | pending | pending |
| `UiObjectCollection#clearFocus` | pending | pending | pending |
| `UiObjectCollection#clearSelection` | pending | pending | pending |
| `UiObjectCollection#click` | pending | pending | pending |
| `UiObjectCollection#clickEach` | pending | pending | pending |
| `UiObjectCollection#collapse` | pending | pending | pending |
| `UiObjectCollection#contains` | pending | pending | pending |
| `UiObjectCollection#contextClick` | pending | pending | pending |
| `UiObjectCollection#copy` | pending | pending | pending |
| `UiObjectCollection#cut` | pending | pending | pending |
| `UiObjectCollection#dismiss` | pending | pending | pending |
| `UiObjectCollection#dragCancel` | pending | pending | pending |
| `UiObjectCollection#dragDrop` | pending | pending | pending |
| `UiObjectCollection#dragStart` | pending | pending | pending |
| `UiObjectCollection#each` | pending | pending | pending |
| `UiObjectCollection#empty` | pending | pending | pending |
| `UiObjectCollection#expand` | pending | pending | pending |
| `UiObjectCollection#find` | pending | pending | pending |
| `UiObjectCollection#findOne` | pending | pending | pending |
| `UiObjectCollection#first` | pending | pending | pending |
| `UiObjectCollection#focus` | pending | pending | pending |
| `UiObjectCollection#get` | pending | pending | pending |
| `UiObjectCollection#hideTooltip` | pending | pending | pending |
| `UiObjectCollection#imeEnter` | pending | pending | pending |
| `UiObjectCollection#isEmpty` | pending | pending | pending |
| `UiObjectCollection#isNotEmpty` | pending | pending | pending |
| `UiObjectCollection#isSimilar` | pending | pending | pending |
| `UiObjectCollection#iterator` | pending | pending | pending |
| `UiObjectCollection#last` | pending | pending | pending |
| `UiObjectCollection#longClick` | pending | pending | pending |
| `UiObjectCollection#longClickEach` | pending | pending | pending |
| `UiObjectCollection#moveWindow` | pending | pending | pending |
| `UiObjectCollection#nextAtMovementGranularity` | pending | pending | pending |
| `UiObjectCollection#nextHtmlElement` | pending | pending | pending |
| `UiObjectCollection#nodes` | pending | pending | pending |
| `UiObjectCollection#nonEmpty` | pending | pending | pending |
| `UiObjectCollection#nonNull` | pending | pending | pending |
| `UiObjectCollection#pageDown` | pending | pending | pending |
| `UiObjectCollection#pageLeft` | pending | pending | pending |
| `UiObjectCollection#pageRight` | pending | pending | pending |
| `UiObjectCollection#pageUp` | pending | pending | pending |
| `UiObjectCollection#paste` | pending | pending | pending |
| `UiObjectCollection#performAction` | pending | pending | pending |
| `UiObjectCollection#performActionEach` | pending | pending | pending |
| `UiObjectCollection#pressAndHold` | pending | pending | pending |
| `UiObjectCollection#previousAtMovementGranularity` | pending | pending | pending |
| `UiObjectCollection#previousHtmlElement` | pending | pending | pending |
| `UiObjectCollection#scrollBackward` | pending | pending | pending |
| `UiObjectCollection#scrollBackwardEach` | pending | pending | pending |
| `UiObjectCollection#scrollDown` | pending | pending | pending |
| `UiObjectCollection#scrollForward` | pending | pending | pending |
| `UiObjectCollection#scrollForwardEach` | pending | pending | pending |
| `UiObjectCollection#scrollLeft` | pending | pending | pending |
| `UiObjectCollection#scrollRight` | pending | pending | pending |
| `UiObjectCollection#scrollTo` | pending | pending | pending |
| `UiObjectCollection#scrollUp` | pending | pending | pending |
| `UiObjectCollection#select` | pending | pending | pending |
| `UiObjectCollection#setProgress` | pending | pending | pending |
| `UiObjectCollection#setSelection` | pending | pending | pending |
| `UiObjectCollection#setText` | pending | pending | pending |
| `UiObjectCollection#setTextEach` | pending | pending | pending |
| `UiObjectCollection#show` | pending | pending | pending |
| `UiObjectCollection#showTextSuggestions` | pending | pending | pending |
| `UiObjectCollection#showTooltip` | pending | pending | pending |
| `UiObjectCollection#size` | pending | pending | pending |
| `UiObjectCollection#slice` | pending | pending | pending |
| `UiObjectCollection#toArray` | pending | pending | pending |
| `UiObjectCollection#toList` | pending | pending | pending |

### UiSelector#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `UiSelector#accessibilityFocus` | pending | pending | pending |
| `UiSelector#accessibilityFocused` | pending | pending | pending |
| `UiSelector#action` | pending | pending | pending |
| `UiSelector#algorithm` | pending | pending | pending |
| `UiSelector#append` | pending | pending | pending |
| `UiSelector#applyEntry` | pending | pending | pending |
| `UiSelector#bottom` | pending | pending | pending |
| `UiSelector#bounds` | pending | pending | pending |
| `UiSelector#boundsBottom` | pending | pending | pending |
| `UiSelector#boundsCenterX` | pending | pending | pending |
| `UiSelector#boundsCenterY` | pending | pending | pending |
| `UiSelector#boundsContains` | pending | pending | pending |
| `UiSelector#boundsHeight` | pending | pending | pending |
| `UiSelector#boundsInside` | pending | pending | pending |
| `UiSelector#boundsLeft` | pending | pending | pending |
| `UiSelector#boundsMaxBottom` | pending | pending | pending |
| `UiSelector#boundsMaxCenterX` | pending | pending | pending |
| `UiSelector#boundsMaxCenterY` | pending | pending | pending |
| `UiSelector#boundsMaxHeight` | pending | pending | pending |
| `UiSelector#boundsMaxLeft` | pending | pending | pending |
| `UiSelector#boundsMaxRight` | pending | pending | pending |
| `UiSelector#boundsMaxTop` | pending | pending | pending |
| `UiSelector#boundsMaxWidth` | pending | pending | pending |
| `UiSelector#boundsMinBottom` | pending | pending | pending |
| `UiSelector#boundsMinCenterX` | pending | pending | pending |
| `UiSelector#boundsMinCenterY` | pending | pending | pending |
| `UiSelector#boundsMinHeight` | pending | pending | pending |
| `UiSelector#boundsMinLeft` | pending | pending | pending |
| `UiSelector#boundsMinRight` | pending | pending | pending |
| `UiSelector#boundsMinTop` | pending | pending | pending |
| `UiSelector#boundsMinWidth` | pending | pending | pending |
| `UiSelector#boundsRight` | pending | pending | pending |
| `UiSelector#boundsTop` | pending | pending | pending |
| `UiSelector#boundsWidth` | pending | pending | pending |
| `UiSelector#centerX` | pending | pending | pending |
| `UiSelector#centerY` | pending | pending | pending |
| `UiSelector#checkable` | pending | pending | pending |
| `UiSelector#checked` | pending | pending | pending |
| `UiSelector#childCount` | pending | pending | pending |
| `UiSelector#className` | pending | pending | pending |
| `UiSelector#classNameContains` | pending | pending | pending |
| `UiSelector#classNameEndsWith` | pending | pending | pending |
| `UiSelector#classNameMatch` | pending | pending | pending |
| `UiSelector#classNameMatches` | pending | pending | pending |
| `UiSelector#classNameStartsWith` | pending | pending | pending |
| `UiSelector#clearAccessibilityFocus` | pending | pending | pending |
| `UiSelector#clearFocus` | pending | pending | pending |
| `UiSelector#clearSelection` | pending | pending | pending |
| `UiSelector#click` | pending | pending | pending |
| `UiSelector#clickable` | pending | pending | pending |
| `UiSelector#collapse` | pending | pending | pending |
| `UiSelector#column` | pending | pending | pending |
| `UiSelector#columnCount` | pending | pending | pending |
| `UiSelector#columnSpan` | pending | pending | pending |
| `UiSelector#content` | pending | pending | pending |
| `UiSelector#contentContains` | pending | pending | pending |
| `UiSelector#contentEndsWith` | pending | pending | pending |
| `UiSelector#contentInvalid` | pending | pending | pending |
| `UiSelector#contentMatch` | pending | pending | pending |
| `UiSelector#contentMatches` | pending | pending | pending |
| `UiSelector#contentStartsWith` | pending | pending | pending |
| `UiSelector#contextClick` | pending | pending | pending |
| `UiSelector#contextClickable` | pending | pending | pending |
| `UiSelector#copy` | pending | pending | pending |
| `UiSelector#currentApp` | pending | pending | pending |
| `UiSelector#cut` | pending | pending | pending |
| `UiSelector#depth` | pending | pending | pending |
| `UiSelector#desc` | pending | pending | pending |
| `UiSelector#descContains` | pending | pending | pending |
| `UiSelector#descEndsWith` | pending | pending | pending |
| `UiSelector#descMatch` | pending | pending | pending |
| `UiSelector#descMatches` | pending | pending | pending |
| `UiSelector#descStartsWith` | pending | pending | pending |
| `UiSelector#dismiss` | pending | pending | pending |
| `UiSelector#dismissable` | pending | pending | pending |
| `UiSelector#dragCancel` | pending | pending | pending |
| `UiSelector#dragDrop` | pending | pending | pending |
| `UiSelector#dragStart` | pending | pending | pending |
| `UiSelector#drawingOrder` | pending | pending | pending |
| `UiSelector#editable` | pending | pending | pending |
| `UiSelector#enabled` | pending | pending | pending |
| `UiSelector#exists` | pending | pending | pending |
| `UiSelector#expand` | pending | pending | pending |
| `UiSelector#filter` | pending | pending | pending |
| `UiSelector#find` | pending | pending | pending |
| `UiSelector#findIterator` | pending | pending | pending |
| `UiSelector#findOnce` | pending | pending | pending |
| `UiSelector#findOne` | pending | pending | pending |
| `UiSelector#focus` | pending | pending | pending |
| `UiSelector#focusable` | pending | pending | pending |
| `UiSelector#focused` | pending | pending | pending |
| `UiSelector#hasChildren` | pending | pending | pending |
| `UiSelector#height` | pending | pending | pending |
| `UiSelector#hideTooltip` | pending | pending | pending |
| `UiSelector#id` | pending | pending | pending |
| `UiSelector#idContains` | pending | pending | pending |
| `UiSelector#idEndsWith` | pending | pending | pending |
| `UiSelector#idHex` | pending | pending | pending |
| `UiSelector#idMatch` | pending | pending | pending |
| `UiSelector#idMatches` | pending | pending | pending |
| `UiSelector#idStartsWith` | pending | pending | pending |
| `UiSelector#imeEnter` | pending | pending | pending |
| `UiSelector#indexInParent` | pending | pending | pending |
| `UiSelector#left` | pending | pending | pending |
| `UiSelector#longClick` | pending | pending | pending |
| `UiSelector#longClickable` | pending | pending | pending |
| `UiSelector#maxBottom` | pending | pending | pending |
| `UiSelector#maxCenterX` | pending | pending | pending |
| `UiSelector#maxCenterY` | pending | pending | pending |
| `UiSelector#maxChildCount` | pending | pending | pending |
| `UiSelector#maxDepth` | pending | pending | pending |
| `UiSelector#maxHeight` | pending | pending | pending |
| `UiSelector#maxIndexInParent` | pending | pending | pending |
| `UiSelector#maxLeft` | pending | pending | pending |
| `UiSelector#maxRight` | pending | pending | pending |
| `UiSelector#maxTop` | pending | pending | pending |
| `UiSelector#maxWidth` | pending | pending | pending |
| `UiSelector#minBottom` | pending | pending | pending |
| `UiSelector#minCenterX` | pending | pending | pending |
| `UiSelector#minCenterY` | pending | pending | pending |
| `UiSelector#minChildCount` | pending | pending | pending |
| `UiSelector#minDepth` | pending | pending | pending |
| `UiSelector#minHeight` | pending | pending | pending |
| `UiSelector#minIndexInParent` | pending | pending | pending |
| `UiSelector#minLeft` | pending | pending | pending |
| `UiSelector#minRight` | pending | pending | pending |
| `UiSelector#minTop` | pending | pending | pending |
| `UiSelector#minWidth` | pending | pending | pending |
| `UiSelector#moveWindow` | pending | pending | pending |
| `UiSelector#multiLine` | pending | pending | pending |
| `UiSelector#nextAtMovementGranularity` | pending | pending | pending |
| `UiSelector#nextHtmlElement` | pending | pending | pending |
| `UiSelector#packageName` | pending | pending | pending |
| `UiSelector#packageNameContains` | pending | pending | pending |
| `UiSelector#packageNameEndsWith` | pending | pending | pending |
| `UiSelector#packageNameMatch` | pending | pending | pending |
| `UiSelector#packageNameMatches` | pending | pending | pending |
| `UiSelector#packageNameStartsWith` | pending | pending | pending |
| `UiSelector#pageDown` | pending | pending | pending |
| `UiSelector#pageLeft` | pending | pending | pending |
| `UiSelector#pageRight` | pending | pending | pending |
| `UiSelector#pageUp` | pending | pending | pending |
| `UiSelector#password` | pending | pending | pending |
| `UiSelector#paste` | pending | pending | pending |
| `UiSelector#performAction` | pending | pending | pending |
| `UiSelector#plus` | pending | pending | pending |
| `UiSelector#pressAndHold` | pending | pending | pending |
| `UiSelector#previousAtMovementGranularity` | pending | pending | pending |
| `UiSelector#previousHtmlElement` | pending | pending | pending |
| `UiSelector#right` | pending | pending | pending |
| `UiSelector#row` | pending | pending | pending |
| `UiSelector#rowCount` | pending | pending | pending |
| `UiSelector#rowSpan` | pending | pending | pending |
| `UiSelector#screenCenterX` | pending | pending | pending |
| `UiSelector#screenCenterY` | pending | pending | pending |
| `UiSelector#screenCoverage` | pending | pending | pending |
| `UiSelector#scrollBackward` | pending | pending | pending |
| `UiSelector#scrollDown` | pending | pending | pending |
| `UiSelector#scrollForward` | pending | pending | pending |
| `UiSelector#scrollLeft` | pending | pending | pending |
| `UiSelector#scrollRight` | pending | pending | pending |
| `UiSelector#scrollTo` | pending | pending | pending |
| `UiSelector#scrollUp` | pending | pending | pending |
| `UiSelector#scrollable` | pending | pending | pending |
| `UiSelector#select` | pending | pending | pending |
| `UiSelector#selected` | pending | pending | pending |
| `UiSelector#setProgress` | pending | pending | pending |
| `UiSelector#setSelection` | pending | pending | pending |
| `UiSelector#setText` | pending | pending | pending |
| `UiSelector#show` | pending | pending | pending |
| `UiSelector#showTextSuggestions` | pending | pending | pending |
| `UiSelector#showTooltip` | pending | pending | pending |
| `UiSelector#text` | pending | pending | pending |
| `UiSelector#textContains` | pending | pending | pending |
| `UiSelector#textEndsWith` | pending | pending | pending |
| `UiSelector#textMatch` | pending | pending | pending |
| `UiSelector#textMatches` | pending | pending | pending |
| `UiSelector#textStartsWith` | pending | pending | pending |
| `UiSelector#toStringReadable` | pending | pending | pending |
| `UiSelector#top` | pending | pending | pending |
| `UiSelector#untilFind` | pending | pending | pending |
| `UiSelector#untilFindOne` | pending | pending | pending |
| `UiSelector#visibleToUser` | pending | pending | pending |
| `UiSelector#waitFor` | pending | pending | pending |
| `UiSelector#width` | pending | pending | pending |

### VolatileDispose#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `VolatileDispose#blockedGet` | pending | pending | pending |
| `VolatileDispose#blockedGetOrThrow` | pending | pending | pending |
| `VolatileDispose#setAndNotify` | pending | pending | pending |

### WebSocket#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `WebSocket#addListener` | pending | pending | pending |
| `WebSocket#cancel` | pending | pending | pending |
| `WebSocket#client` | pending | pending | pending |
| `WebSocket#close` | pending | pending | pending |
| `WebSocket#defaultMaxListeners` | pending | pending | pending |
| `WebSocket#emit` | pending | pending | pending |
| `WebSocket#emitSticky` | pending | pending | pending |
| `WebSocket#eventNames` | pending | pending | pending |
| `WebSocket#exitOnClose` | pending | pending | pending |
| `WebSocket#getMaxListeners` | pending | pending | pending |
| `WebSocket#getTimer` | pending | pending | pending |
| `WebSocket#listenerCount` | pending | pending | pending |
| `WebSocket#listeners` | pending | pending | pending |
| `WebSocket#on` | pending | pending | pending |
| `WebSocket#once` | pending | pending | pending |
| `WebSocket#prependListener` | pending | pending | pending |
| `WebSocket#prependOnceListener` | pending | pending | pending |
| `WebSocket#queueSize` | pending | pending | pending |
| `WebSocket#rebuild` | pending | pending | pending |
| `WebSocket#removeAllListeners` | pending | pending | pending |
| `WebSocket#removeListener` | pending | pending | pending |
| `WebSocket#request` | pending | pending | pending |
| `WebSocket#send` | pending | pending | pending |
| `WebSocket#setMaxListeners` | pending | pending | pending |
| `WebSocket#url` | pending | pending | pending |

### YoloDetector#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `YoloDetector#close` | pending | pending | pending |
| `YoloDetector#detect` | pending | pending | pending |

### Zip#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `Zip#addFile` | pending | pending | pending |
| `Zip#addFiles` | pending | pending | pending |
| `Zip#addFolder` | pending | pending | pending |
| `Zip#extractAll` | pending | pending | pending |
| `Zip#extractFile` | pending | pending | pending |
| `Zip#getFileHeader` | pending | pending | pending |
| `Zip#getFileHeaders` | pending | pending | pending |
| `Zip#getPath` | pending | pending | pending |
| `Zip#getZipFile` | pending | pending | pending |
| `Zip#isEncrypted` | pending | pending | pending |
| `Zip#isValidZipFile` | pending | pending | pending |
| `Zip#name` | pending | pending | pending |
| `Zip#options` | pending | pending | pending |
| `Zip#path` | pending | pending | pending |
| `Zip#removeFile` | pending | pending | pending |
| `Zip#setPassword` | pending | pending | pending |
| `Zip#toString` | pending | pending | pending |
| `Zip#toStringReadable` | pending | pending | pending |
| `Zip#unzipParameters` | pending | pending | pending |
| `Zip#zipFile` | pending | pending | pending |
| `Zip#zipParameters` | pending | pending | pending |

### app

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `app.autojs` | pending | pending | pending |
| `app.editFile` | bridged | pending | capability-gated |
| `app.getApkInfo` | pending | pending | pending |
| `app.getAppByAlias` | pending | pending | pending |
| `app.getAppName` | bridged | pending | capability-gated |
| `app.getInstalledApps` | bridged | pending | capability-gated |
| `app.getInstalledPackages` | pending | pending | pending |
| `app.getPackageName` | bridged | pending | capability-gated |
| `app.getUriForFile` | bridged | pending | capability-gated |
| `app.intent` | bridged | pending | capability-gated |
| `app.intentToShell` | bridged | pending | capability-gated |
| `app.isDualInstalled` | bridged | pending | capability-gated |
| `app.isInstalled` | bridged | pending | capability-gated |
| `app.kill` | bridged | pending | capability-gated |
| `app.killDual` | bridged | pending | capability-gated |
| `app.launch` | bridged | pending | capability-gated |
| `app.launchApp` | bridged | pending | capability-gated |
| `app.launchAppDetailsSettings` | bridged | pending | capability-gated |
| `app.launchDual` | bridged | pending | capability-gated |
| `app.launchDualApp` | bridged | pending | capability-gated |
| `app.launchDualAppDetailsSettings` | bridged | pending | capability-gated |
| `app.launchDualPackage` | bridged | pending | capability-gated |
| `app.launchDualSettings` | bridged | pending | capability-gated |
| `app.launchPackage` | bridged | pending | capability-gated |
| `app.launchSettings` | bridged | pending | capability-gated |
| `app.openAppSetting` | bridged | pending | capability-gated |
| `app.openAppSettings` | bridged | pending | capability-gated |
| `app.openDualAppSetting` | bridged | pending | capability-gated |
| `app.openDualAppSettings` | bridged | pending | capability-gated |
| `app.openDualUrl` | bridged | pending | capability-gated |
| `app.openUrl` | bridged | pending | capability-gated |
| `app.parseUri` | bridged | pending | capability-gated |
| `app.sendBroadcast` | bridged | pending | capability-gated |
| `app.sendEmail` | bridged | pending | capability-gated |
| `app.sendLocalBroadcastSync` | pending | pending | pending |
| `app.startActivity` | bridged | pending | capability-gated |
| `app.startDualActivity` | bridged | pending | capability-gated |
| `app.startService` | bridged | pending | capability-gated |
| `app.uninstall` | bridged | pending | capability-gated |
| `app.uninstallDual` | bridged | pending | capability-gated |
| `app.versionCode` | bridged | pending | capability-gated |
| `app.versionName` | bridged | pending | capability-gated |
| `app.viewFile` | bridged | pending | capability-gated |

### auto

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `auto.clearCache` | pending | pending | pending |
| `auto.currentActivity` | pending | pending | pending |
| `auto.currentComponent` | pending | pending | pending |
| `auto.currentPackage` | pending | pending | pending |
| `auto.disable` | pending | pending | pending |
| `auto.dump` | pending | pending | pending |
| `auto.enable` | pending | pending | pending |
| `auto.exists` | pending | pending | pending |
| `auto.explain` | pending | pending | pending |
| `auto.findWindowRoots` | pending | pending | pending |
| `auto.findWindows` | pending | pending | pending |
| `auto.getWindowRoot` | pending | pending | pending |
| `auto.hasInstance` | pending | pending | pending |
| `auto.hasService` | pending | pending | pending |
| `auto.isOperational` | pending | pending | pending |
| `auto.isRunning` | pending | pending | pending |
| `auto.launchSettings` | pending | pending | pending |
| `auto.registerEvent` | pending | pending | pending |
| `auto.registerEvents` | pending | pending | pending |
| `auto.removeEvent` | pending | pending | pending |
| `auto.removeEvents` | pending | pending | pending |
| `auto.root` | pending | pending | pending |
| `auto.rootInActiveWindow` | pending | pending | pending |
| `auto.service` | pending | pending | pending |
| `auto.services` | pending | pending | pending |
| `auto.setFlags` | pending | pending | pending |
| `auto.setMode` | pending | pending | pending |
| `auto.setWindowFilter` | pending | pending | pending |
| `auto.start` | pending | pending | pending |
| `auto.state` | pending | pending | pending |
| `auto.stateListener` | pending | pending | pending |
| `auto.stats` | pending | pending | pending |
| `auto.stop` | pending | pending | pending |
| `auto.wait` | pending | pending | pending |
| `auto.waitFor` | denied: sync-only (accessibility-sync-wait) | denied: sync-only (accessibility-sync-wait) | denied: sync-only (accessibility-sync-wait) |
| `auto.windowRoots` | pending | pending | pending |
| `auto.windows` | pending | pending | pending |

### autojs

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `autojs.R` | pending | pending | pending |
| `autojs.canDisplayOverOtherApps` | pending | pending | pending |
| `autojs.canModifySystemSettings` | pending | pending | pending |
| `autojs.canWriteSecureSettings` | pending | pending | pending |
| `autojs.exit` | pending | pending | pending |
| `autojs.getLanguage` | pending | pending | pending |
| `autojs.getLanguageTag` | pending | pending | pending |
| `autojs.getRootMode` | pending | pending | pending |
| `autojs.isRootAvailable` | pending | pending | pending |
| `autojs.isScreenLandscape` | pending | pending | pending |
| `autojs.isScreenPortrait` | pending | pending | pending |
| `autojs.name` | pending | pending | pending |
| `autojs.orientation` | pending | pending | pending |
| `autojs.packageName` | pending | pending | pending |
| `autojs.restart` | pending | pending | pending |
| `autojs.rotation` | pending | pending | pending |
| `autojs.setRootMode` | pending | pending | pending |
| `autojs.themeColor` | pending | pending | pending |
| `autojs.versionCode` | pending | pending | pending |
| `autojs.versionDate` | pending | pending | pending |
| `autojs.versionName` | pending | pending | pending |

### autojs.version

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `autojs.version.code` | pending | pending | pending |
| `autojs.version.date` | pending | pending | pending |
| `autojs.version.isAtLeast` | pending | pending | pending |
| `autojs.version.isEqual` | pending | pending | pending |
| `autojs.version.isHigherThan` | pending | pending | pending |
| `autojs.version.isLowerThan` | pending | pending | pending |
| `autojs.version.name` | pending | pending | pending |

### automator

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `automator.accessibilityAllApps` | pending | pending | pending |
| `automator.accessibilityButton` | pending | pending | pending |
| `automator.accessibilityButtonChooser` | pending | pending | pending |
| `automator.accessibilityShortcut` | pending | pending | pending |
| `automator.back` | bridged | pending | capability-gated |
| `automator.backToApp` | pending | pending | pending |
| `automator.backUntil` | pending | pending | pending |
| `automator.captureScreen` | pending | pending | pending |
| `automator.click` | bridged | pending | capability-gated |
| `automator.clickAny` | pending | pending | pending |
| `automator.clickBoundsAny` | pending | pending | pending |
| `automator.clickBoundsIfExists` | pending | pending | pending |
| `automator.clickIfExists` | pending | pending | pending |
| `automator.collectList` | pending | pending | pending |
| `automator.dismissNotificationShade` | pending | pending | pending |
| `automator.dismissPopups` | pending | pending | pending |
| `automator.ensureService` | pending | pending | pending |
| `automator.findAny` | pending | pending | pending |
| `automator.gesture` | bridged | pending | capability-gated |
| `automator.gestureAsync` | pending | pending | pending |
| `automator.gestures` | pending | pending | pending |
| `automator.gesturesAsync` | pending | pending | pending |
| `automator.headsetHook` | pending | pending | pending |
| `automator.headsethook` | pending | pending | pending |
| `automator.home` | bridged | pending | capability-gated |
| `automator.input` | pending | pending | pending |
| `automator.isServiceRunning` | pending | pending | pending |
| `automator.launchAndWait` | pending | pending | pending |
| `automator.lockScreen` | bridged | pending | pending |
| `automator.longClick` | bridged | pending | capability-gated |
| `automator.notifications` | bridged | pending | pending |
| `automator.powerDialog` | bridged | pending | pending |
| `automator.press` | pending | pending | pending |
| `automator.quickSettings` | bridged | pending | pending |
| `automator.recents` | pending | pending | pending |
| `automator.retry` | pending | pending | pending |
| `automator.scrollDown` | pending | pending | pending |
| `automator.scrollUntil` | pending | pending | pending |
| `automator.scrollUp` | pending | pending | pending |
| `automator.setText` | bridged | pending | capability-gated |
| `automator.smartClick` | pending | pending | pending |
| `automator.smartClickBounds` | pending | pending | pending |
| `automator.splitScreen` | bridged | pending | pending |
| `automator.swipe` | bridged | pending | capability-gated |
| `automator.switchToInputMethod` | pending | pending | pending |
| `automator.switchToInputMethodWithId` | pending | pending | pending |
| `automator.takeScreenshot` | bridged | pending | pending |
| `automator.toggle` | pending | pending | pending |
| `automator.typeInto` | pending | pending | pending |
| `automator.waitForEvent` | pending | pending | pending |
| `automator.waitForIdle` | pending | pending | pending |
| `automator.waitForNotification` | pending | pending | pending |
| `automator.waitForService` | pending | pending | pending |
| `automator.waitForToast` | pending | pending | pending |

### barcode

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `barcode.detect` | bridged | pending | pending |
| `barcode.detectAll` | bridged | pending | pending |
| `barcode.recognizeText` | bridged | pending | pending |
| `barcode.recognizeTexts` | bridged | pending | pending |

### base64

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `base64.decode` | local-shim | not-portable: runtime-native (native-base64) | not-portable: runtime-native (native-base64) |
| `base64.encode` | local-shim | not-portable: runtime-native (native-base64) | not-portable: runtime-native (native-base64) |

### colors

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `colors.android` | pending | pending | pending |
| `colors.blend` | pending | pending | pending |
| `colors.contrast` | pending | pending | pending |
| `colors.css` | pending | pending | pending |
| `colors.derive` | pending | pending | pending |
| `colors.distance` | pending | pending | pending |
| `colors.ensureContrast` | pending | pending | pending |
| `colors.extract` | pending | pending | pending |
| `colors.harmony` | pending | pending | pending |
| `colors.invert` | pending | pending | pending |
| `colors.material` | pending | pending | pending |
| `colors.nightVariant` | pending | pending | pending |
| `colors.oklab` | pending | pending | pending |
| `colors.oklch` | pending | pending | pending |
| `colors.simulate` | pending | pending | pending |
| `colors.toOklab` | pending | pending | pending |
| `colors.toOklch` | pending | pending | pending |
| `colors.tones` | pending | pending | pending |
| `colors.web` | pending | pending | pending |

### colors.theme

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `colors.theme.activate` | pending | pending | pending |
| `colors.theme.current` | pending | pending | pending |
| `colors.theme.deactivate` | pending | pending | pending |
| `colors.theme.get` | pending | pending | pending |
| `colors.theme.libraries` | pending | pending | pending |
| `colors.theme.next` | pending | pending | pending |
| `colors.theme.reset` | pending | pending | pending |
| `colors.theme.set` | pending | pending | pending |
| `colors.theme.state` | pending | pending | pending |

### compose

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `compose.ComposeError` | pending | pending | pending |

### console

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `console.assert` | bridged | bridged | pending |
| `console.build` | bridged | bridged | pending |
| `console.error` | bridged | bridged | pending |
| `console.info` | bridged | bridged | pending |
| `console.input` | bridged | bridged | pending |
| `console.log` | bridged | bridged | pending |
| `console.print` | bridged | bridged | pending |
| `console.printAllStackTrace` | bridged | bridged | pending |
| `console.rawInput` | bridged | bridged | pending |
| `console.setAvoidStatusBar` | pending | pending | pending |
| `console.setColorful` | pending | pending | pending |
| `console.setInputVisible` | pending | pending | pending |
| `console.setTimeFormat` | pending | pending | pending |
| `console.setTimeVisible` | pending | pending | pending |
| `console.time` | bridged | bridged | pending |
| `console.timeEnd` | bridged | bridged | pending |
| `console.trace` | bridged | bridged | pending |
| `console.verbose` | bridged | bridged | pending |
| `console.warn` | bridged | bridged | pending |

### continuation

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `continuation.create` | not-portable: sync-only (sync-continuation) | not-portable: sync-only (sync-continuation) | not-portable: sync-only (sync-continuation) |
| `continuation.enabled` | not-portable: sync-only (sync-continuation) | not-portable: sync-only (sync-continuation) | not-portable: sync-only (sync-continuation) |

### crypto

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `crypto.Key` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |
| `crypto.KeyPair` | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) | not-portable: runtime-native (native-crypto) |

### cvt.bytes

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `cvt.bytes.AUTO` | local-shim | pending | pending |
| `cvt.bytes.IEC_DIV` | local-shim | pending | pending |
| `cvt.bytes.SI_DIV` | local-shim | pending | pending |
| `cvt.bytes.UNITS` | local-shim | pending | pending |
| `cvt.bytes.loose` | local-shim | pending | pending |
| `cvt.bytes.strict` | local-shim | pending | pending |

### device

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `device.density` | bridged | bridged | pending |
| `device.digest` | pending | pending | pending |
| `device.getGatewayAddress` | pending | pending | pending |
| `device.getIpAddress` | pending | pending | pending |
| `device.getIpv6Address` | pending | pending | pending |
| `device.getSharedDeviceId` | pending | pending | pending |
| `device.height` | bridged | bridged | pending |
| `device.isActiveNetworkMetered` | pending | pending | pending |
| `device.isConnectedOrConnecting` | pending | pending | pending |
| `device.isPointerLocationDisabled` | pending | pending | pending |
| `device.isPointerLocationEnabled` | pending | pending | pending |
| `device.isScreenLandscape` | pending | pending | pending |
| `device.isScreenOff` | pending | pending | pending |
| `device.isScreenPortrait` | pending | pending | pending |
| `device.isWifiAvailable` | pending | pending | pending |
| `device.orientation` | pending | pending | pending |
| `device.pageSize` | pending | pending | pending |
| `device.rotation` | pending | pending | pending |
| `device.setPointerLocation` | pending | pending | pending |
| `device.setPointerLocationDisabled` | pending | pending | pending |
| `device.setPointerLocationEnabled` | pending | pending | pending |
| `device.summary` | pending | pending | pending |
| `device.togglePointerLocation` | pending | pending | pending |
| `device.width` | bridged | bridged | pending |

### dialogs

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `dialogs.build` | pending | pending | pending |

### engines

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `engines.all` | bridged | pending | pending |
| `engines.execAutoFile` | not-portable: rhino-only (rhino-engine) | not-portable: rhino-only (rhino-engine) | not-portable: rhino-only (rhino-engine) |
| `engines.execScript` | bridged | not-portable: rhino-only (rhino-engine) | not-portable: rhino-only (rhino-engine) |
| `engines.execScriptFile` | bridged | not-portable: rhino-only (rhino-engine) | not-portable: rhino-only (rhino-engine) |
| `engines.getEngines` | pending | pending | pending |
| `engines.myEngine` | bridged | pending | pending |
| `engines.stopAll` | bridged | pending | pending |
| `engines.stopAllAndToast` | pending | pending | pending |

### events

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `events.__asEmitter__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |

### files

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `files.join` | local-shim | pending | capability-gated |
| `files.open` | local-shim | pending | capability-gated |
| `files.path` | local-shim | pending | capability-gated |
| `files.toFile` | local-shim | pending | capability-gated |

### files.batchRename

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `files.batchRename.BatchRenameError` | pending | pending | pending |
| `files.batchRename.apply` | pending | pending | pending |
| `files.batchRename.applyAsync` | pending | pending | pending |
| `files.batchRename.clearHistory` | pending | pending | pending |
| `files.batchRename.deleteHistory` | pending | pending | pending |
| `files.batchRename.history` | pending | pending | pending |
| `files.batchRename.importPresets` | pending | pending | pending |
| `files.batchRename.importPresetsAsync` | pending | pending | pending |
| `files.batchRename.isAvailable` | pending | pending | pending |
| `files.batchRename.plan` | pending | pending | pending |
| `files.batchRename.planAsync` | pending | pending | pending |
| `files.batchRename.presets` | pending | pending | pending |
| `files.batchRename.presetsAsync` | pending | pending | pending |
| `files.batchRename.preview` | pending | pending | pending |
| `files.batchRename.previewAsync` | pending | pending | pending |
| `files.batchRename.undo` | pending | pending | pending |
| `files.batchRename.undoAsync` | pending | pending | pending |

### floaty

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `floaty.checkPermission` | pending | pending | pending |
| `floaty.closeAll` | pending | pending | pending |
| `floaty.ensurePermission` | pending | pending | pending |
| `floaty.getClip` | pending | pending | pending |
| `floaty.hasPermission` | pending | pending | pending |
| `floaty.rawWindow` | pending | pending | pending |
| `floaty.requestPermission` | pending | pending | pending |
| `floaty.window` | pending | pending | pending |

### fmt.bytes

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `fmt.bytes.AUTO` | local-shim | pending | pending |
| `fmt.bytes.IEC_DIV` | local-shim | pending | pending |
| `fmt.bytes.SI_DIV` | local-shim | pending | pending |
| `fmt.bytes.UNITS` | local-shim | pending | pending |
| `fmt.bytes.loose` | local-shim | pending | pending |
| `fmt.bytes.strict` | local-shim | pending | pending |

### gallery

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `gallery.GalleryError` | pending | pending | pending |
| `gallery.convert` | pending | pending | pending |
| `gallery.convertAsync` | pending | pending | pending |
| `gallery.edit` | pending | pending | pending |
| `gallery.editAsync` | pending | pending | pending |
| `gallery.isAvailable` | pending | pending | pending |
| `gallery.isAvailableAsync` | pending | pending | pending |
| `gallery.pick` | pending | pending | pending |
| `gallery.pickAsync` | pending | pending | pending |
| `gallery.view` | pending | pending | pending |
| `gallery.viewAsync` | pending | pending | pending |

### http

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `http.__okhttp__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `http.buildRequest` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
| `http.client` | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) | not-portable: runtime-native (native-http) |
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

### installer

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `installer.InstallerError` | pending | pending | pending |
| `installer.authorizer` | pending | pending | pending |
| `installer.inspect` | pending | pending | pending |
| `installer.inspectAsync` | pending | pending | pending |
| `installer.install` | pending | pending | pending |
| `installer.installAsync` | pending | pending | pending |
| `installer.isAvailable` | pending | pending | pending |
| `installer.isDefault` | pending | pending | pending |
| `installer.session` | pending | pending | pending |
| `installer.setDefault` | pending | pending | pending |
| `installer.setDefaultAsync` | pending | pending | pending |
| `installer.status` | pending | pending | pending |
| `installer.uninstall` | pending | pending | pending |
| `installer.uninstallAsync` | pending | pending | pending |
| `installer.users` | pending | pending | pending |
| `installer.usersAsync` | pending | pending | pending |

### jsox

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `jsox.extend` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |
| `jsox.extendAll` | local-shim | not-portable: rhino-only (jsox-extensions) | not-portable: rhino-only (jsox-extensions) |

### keys

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `keys.BACK` | bridged | pending | pending |
| `keys.HOME` | bridged | pending | pending |
| `keys.MENU` | bridged | pending | pending |
| `keys.VOLUME_DOWN` | bridged | pending | pending |
| `keys.VOLUME_UP` | bridged | pending | pending |
| `keys.back` | bridged | pending | pending |
| `keys.home` | bridged | pending | pending |
| `keys.menu` | bridged | pending | pending |
| `keys.volumeDown` | bridged | pending | pending |
| `keys.volumeUp` | bridged | pending | pending |
| `keys.volume_down` | bridged | pending | pending |
| `keys.volume_up` | bridged | pending | pending |

### mediainfo

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `mediainfo.SNAPSHOT_SCHEMA_V1` | pending | pending | pending |
| `mediainfo.SNAPSHOT_SCHEMA_V2` | pending | pending | pending |
| `mediainfo.capabilities` | bridged | pending | pending |
| `mediainfo.countGet` | bridged | pending | pending |
| `mediainfo.get` | bridged | pending | pending |
| `mediainfo.read` | bridged | pending | pending |
| `mediainfo.snapshot` | pending | pending | pending |

### memory

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `memory.collect` | pending | pending | pending |
| `memory.dumpHeap` | pending | pending | pending |
| `memory.retained` | pending | pending | pending |
| `memory.snapshot` | pending | pending | pending |
| `memory.watch` | pending | pending | pending |

### notice

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `notice.builder` | pending | pending | pending |
| `notice.cancel` | bridged | bridged | pending |
| `notice.config` | pending | pending | pending |
| `notice.ensureEnabled` | pending | pending | pending |
| `notice.getBuilder` | pending | pending | pending |
| `notice.isEnabled` | pending | pending | pending |
| `notice.launchSettings` | pending | pending | pending |

### notice.channel

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `notice.channel.contains` | pending | pending | pending |
| `notice.channel.create` | pending | pending | pending |
| `notice.channel.createIfNeeded` | pending | pending | pending |
| `notice.channel.get` | pending | pending | pending |
| `notice.channel.getAll` | pending | pending | pending |
| `notice.channel.remove` | pending | pending | pending |

### ocr

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ocr.detect` | bridged | pending | pending |
| `ocr.mode` | bridged | pending | pending |
| `ocr.recognizeText` | bridged | pending | pending |
| `ocr.summary` | bridged | pending | pending |
| `ocr.tap` | bridged | pending | pending |
| `ocr.toString` | bridged | pending | pending |

### ocr.mlkit

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ocr.mlkit.detect` | pending | pending | pending |
| `ocr.mlkit.recognizeText` | pending | pending | pending |

### ocr.paddle

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ocr.paddle.detect` | pending | pending | pending |
| `ocr.paddle.recognizeText` | pending | pending | pending |

### ocr.rapid

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ocr.rapid.detect` | pending | pending | pending |
| `ocr.rapid.recognizeText` | pending | pending | pending |

### pinyin

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `pinyin.MODE_NORMAL` | bridged | bridged | pending |
| `pinyin.MODE_PLACENAME` | bridged | bridged | pending |
| `pinyin.MODE_PLACE_NAME` | bridged | bridged | pending |
| `pinyin.MODE_SURNAME` | bridged | bridged | pending |
| `pinyin.STYLE_FIRST_LETTER` | bridged | bridged | pending |
| `pinyin.STYLE_INITIALS` | bridged | bridged | pending |
| `pinyin.STYLE_NORMAL` | bridged | bridged | pending |
| `pinyin.STYLE_TO3NE` | bridged | bridged | pending |
| `pinyin.STYLE_TONE` | bridged | bridged | pending |
| `pinyin.STYLE_TONE2` | bridged | bridged | pending |

### plugins

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `plugins.extend` | pending | pending | pending |
| `plugins.extendAll` | pending | pending | pending |
| `plugins.extendAllBut` | pending | pending | pending |
| `plugins.load` | local-shim | not-portable: rhino-only (rhino-engine) | not-portable: rhino-only (rhino-engine) |

### plugins.tools

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `plugins.tools.call` | pending | pending | pending |
| `plugins.tools.callAsync` | pending | pending | pending |
| `plugins.tools.list` | pending | pending | pending |
| `plugins.tools.providers` | pending | pending | pending |

### powerManager

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `powerManager.isIgnoringBatteryOptimizations` | pending | pending | pending |
| `powerManager.requestIgnoreBatteryOptimizations` | pending | pending | pending |

### qrcode

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `qrcode.detect` | bridged | pending | pending |
| `qrcode.detectAll` | bridged | pending | pending |
| `qrcode.recognizeText` | bridged | pending | pending |
| `qrcode.recognizeTexts` | bridged | pending | pending |

### s13n

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `s13n.bytes` | local-shim | pending | pending |
| `s13n.color` | local-shim | pending | pending |
| `s13n.point` | local-shim | pending | pending |
| `s13n.throwable` | local-shim | pending | pending |
| `s13n.time` | local-shim | pending | pending |

### settings

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `settings.categories` | pending | pending | pending |
| `settings.export` | pending | pending | pending |
| `settings.isEnabled` | pending | pending | pending |
| `settings.setEnabled` | pending | pending | pending |

### shell

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `shell.currentActivity` | pending | pending | pending |
| `shell.currentComponent` | pending | pending | pending |
| `shell.currentPackage` | pending | pending | pending |
| `shell.execCommand` | pending | pending | pending |
| `shell.fromIntent` | pending | pending | pending |
| `shell.getCommand` | pending | pending | pending |
| `shell.isRootAvailable` | pending | pending | pending |
| `shell.kill` | pending | pending | pending |
| `shell.setDefaultOptions` | pending | pending | pending |

### shizuku

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `shizuku.currentActivity` | pending | pending | pending |
| `shizuku.currentComponent` | pending | pending | pending |
| `shizuku.currentPackage` | pending | pending | pending |
| `shizuku.execCommand` | pending | pending | pending |
| `shizuku.getCommand` | pending | pending | pending |
| `shizuku.kill` | pending | pending | pending |
| `shizuku.state` | pending | pending | pending |

### species

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `species.isArray` | pending | pending | pending |
| `species.isArrayBuffer` | pending | pending | pending |
| `species.isBigInt` | pending | pending | pending |
| `species.isBoolean` | pending | pending | pending |
| `species.isContinuation` | pending | pending | pending |
| `species.isDataView` | pending | pending | pending |
| `species.isDate` | pending | pending | pending |
| `species.isError` | pending | pending | pending |
| `species.isFloat32Array` | pending | pending | pending |
| `species.isFloat64Array` | pending | pending | pending |
| `species.isFunction` | pending | pending | pending |
| `species.isHTMLDocument` | pending | pending | pending |
| `species.isInt16Array` | pending | pending | pending |
| `species.isInt32Array` | pending | pending | pending |
| `species.isInt8Array` | pending | pending | pending |
| `species.isJavaClass` | pending | pending | pending |
| `species.isJavaObject` | pending | pending | pending |
| `species.isJavaPackage` | pending | pending | pending |
| `species.isMap` | pending | pending | pending |
| `species.isNamespace` | pending | pending | pending |
| `species.isNull` | pending | pending | pending |
| `species.isNumber` | pending | pending | pending |
| `species.isObject` | pending | pending | pending |
| `species.isQName` | pending | pending | pending |
| `species.isRegExp` | pending | pending | pending |
| `species.isSet` | pending | pending | pending |
| `species.isString` | pending | pending | pending |
| `species.isUint16Array` | pending | pending | pending |
| `species.isUint32Array` | pending | pending | pending |
| `species.isUint8Array` | pending | pending | pending |
| `species.isUint8ClampedArray` | pending | pending | pending |
| `species.isUndefined` | pending | pending | pending |
| `species.isWeakMap` | pending | pending | pending |
| `species.isWeakSet` | pending | pending | pending |
| `species.isWindow` | pending | pending | pending |
| `species.isXML` | pending | pending | pending |
| `species.isXMLList` | pending | pending | pending |

### storages

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `storages.all` | bridged | pending | capability-gated |
| `storages.create` | bridged | pending | capability-gated |
| `storages.names` | bridged | pending | capability-gated |
| `storages.remove` | bridged | pending | capability-gated |
| `storages.removeSync` | bridged | pending | capability-gated |

### sysprops

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `sysprops.get` | pending | pending | pending |
| `sysprops.getAll` | pending | pending | pending |
| `sysprops.getBoolean` | pending | pending | pending |
| `sysprops.getInt` | pending | pending | pending |

### terminal

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `terminal.TerminalError` | pending | pending | pending |
| `terminal.close` | pending | pending | pending |
| `terminal.closeAll` | pending | pending | pending |
| `terminal.closeAllAsync` | pending | pending | pending |
| `terminal.closeAsync` | pending | pending | pending |
| `terminal.env` | pending | pending | pending |
| `terminal.envAsync` | pending | pending | pending |
| `terminal.exec` | pending | pending | pending |
| `terminal.execAsync` | pending | pending | pending |
| `terminal.isAvailable` | pending | pending | pending |
| `terminal.isAvailableAsync` | pending | pending | pending |
| `terminal.npm` | pending | pending | pending |
| `terminal.open` | pending | pending | pending |
| `terminal.openAsync` | pending | pending | pending |
| `terminal.session` | pending | pending | pending |
| `terminal.sessions` | pending | pending | pending |
| `terminal.sessionsAsync` | pending | pending | pending |
| `terminal.show` | pending | pending | pending |
| `terminal.showAsync` | pending | pending | pending |
| `terminal.state` | pending | pending | pending |

### threads

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `threads.disposable` | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) |
| `threads.interrupt` | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) |
| `threads.pool` | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) |
| `threads.start` | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) | not-portable: sync-only (sync-threads) |

### timers

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `timers.keepAlive` | not-portable: runtime-native (sync-keep-alive) | not-portable: runtime-native (sync-keep-alive) | not-portable: runtime-native (sync-keep-alive) |
| `timers.setIntervalExt` | pending | pending | pending |

### toast

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `toast.dismissAll` | pending | pending | pending |

### ui

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ui.R` | pending | pending | pending |
| `ui.Widget` | pending | pending | pending |
| `ui.__autojs6Tsx` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.__autojs6TsxFragment` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.__inflate__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.__widgets__` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `ui.backgroundColor` | pending | pending | pending |
| `ui.emitter` | pending | pending | pending |
| `ui.findById` | pending | pending | pending |
| `ui.findByStringId` | pending | pending | pending |
| `ui.finish` | pending | pending | pending |
| `ui.getNavigationBarHeight` | pending | pending | pending |
| `ui.getStatusBarHeight` | pending | pending | pending |
| `ui.getVisibleNavigationBarHeight` | pending | pending | pending |
| `ui.getVisibleStatusBarHeight` | pending | pending | pending |
| `ui.isUiThread` | pending | pending | pending |
| `ui.keepScreenOn` | pending | pending | pending |
| `ui.layout` | pending | pending | pending |
| `ui.layoutFile` | pending | pending | pending |
| `ui.navigationBarColor` | pending | pending | pending |
| `ui.navigationBarHeight` | pending | pending | pending |
| `ui.navigationBarIconLight` | pending | pending | pending |
| `ui.navigationBarIconLightBy` | pending | pending | pending |
| `ui.post` | pending | pending | pending |
| `ui.registerWidget` | pending | pending | pending |
| `ui.root` | pending | pending | pending |
| `ui.run` | pending | pending | pending |
| `ui.setContentView` | pending | pending | pending |
| `ui.statusBarColor` | pending | pending | pending |
| `ui.statusBarHeight` | pending | pending | pending |
| `ui.statusBarIconLight` | pending | pending | pending |
| `ui.statusBarIconLightBy` | pending | pending | pending |
| `ui.useAndroidLayout` | pending | pending | pending |
| `ui.visibleNavigationBarHeight` | pending | pending | pending |
| `ui.visibleStatusBarHeight` | pending | pending | pending |

### util

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `util.__assignFunctions__` | pending | pending | pending |
| `util.assureStringEndsWith` | pending | pending | pending |
| `util.assureStringStartsWith` | pending | pending | pending |
| `util.assureStringSurroundsWith` | pending | pending | pending |
| `util.checkStringArgument` | pending | pending | pending |
| `util.checkStringParam` | pending | pending | pending |
| `util.debuglog` | pending | pending | pending |
| `util.deprecate` | pending | pending | pending |
| `util.dpToPx` | pending | pending | pending |
| `util.ensureArrayType` | pending | pending | pending |
| `util.ensureBigIntType` | pending | pending | pending |
| `util.ensureBigintType` | pending | pending | pending |
| `util.ensureBooleanType` | pending | pending | pending |
| `util.ensureFunctionType` | pending | pending | pending |
| `util.ensureNonNullObjectType` | pending | pending | pending |
| `util.ensureNumberType` | pending | pending | pending |
| `util.ensureObjectType` | pending | pending | pending |
| `util.ensureStringType` | pending | pending | pending |
| `util.ensureSymbolType` | pending | pending | pending |
| `util.ensureType` | pending | pending | pending |
| `util.ensureUndefinedType` | pending | pending | pending |
| `util.extend` | pending | pending | pending |
| `util.format` | not-portable: runtime-native | not-portable: runtime-native | pending |
| `util.isArray` | pending | pending | pending |
| `util.isBigInt` | pending | pending | pending |
| `util.isBoolean` | pending | pending | pending |
| `util.isDate` | pending | pending | pending |
| `util.isEmptyObject` | pending | pending | pending |
| `util.isError` | pending | pending | pending |
| `util.isFunction` | pending | pending | pending |
| `util.isInteger` | pending | pending | pending |
| `util.isJavaArray` | pending | pending | pending |
| `util.isJavaObject` | pending | pending | pending |
| `util.isNull` | pending | pending | pending |
| `util.isNullOrUndefined` | pending | pending | pending |
| `util.isNumber` | pending | pending | pending |
| `util.isObject` | pending | pending | pending |
| `util.isPrimitive` | pending | pending | pending |
| `util.isReference` | pending | pending | pending |
| `util.isRegExp` | pending | pending | pending |
| `util.isString` | pending | pending | pending |
| `util.isSymbol` | pending | pending | pending |
| `util.isUndefined` | pending | pending | pending |
| `util.log` | pending | pending | pending |
| `util.pxToDp` | pending | pending | pending |
| `util.pxToSp` | pending | pending | pending |
| `util.spToPx` | pending | pending | pending |
| `util.toRegular` | pending | pending | pending |
| `util.toRegularAndApply` | pending | pending | pending |
| `util.toRegularAndCall` | pending | pending | pending |
| `util.unwrapJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |

### util.inspect

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `util.inspect.colors` | pending | pending | pending |
| `util.inspect.styles` | pending | pending | pending |

### util.java

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `util.java.array` | pending | pending | pending |
| `util.java.instanceOf` | pending | pending | pending |
| `util.java.instanceof` | pending | pending | pending |
| `util.java.mapToObject` | pending | pending | pending |
| `util.java.objectToMap` | pending | pending | pending |
| `util.java.toJsArray` | pending | pending | pending |

### util.morseCode

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `util.morseCode.getCode` | pending | pending | pending |
| `util.morseCode.getPattern` | pending | pending | pending |
| `util.morseCode.vibrate` | pending | pending | pending |

### util.version

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `util.version.sdkInt` | pending | pending | pending |

### util.versionCodes

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `util.versionCodes.search` | pending | pending | pending |
| `util.versionCodes.searchAll` | pending | pending | pending |
| `util.versionCodes.summary` | pending | pending | pending |
| `util.versionCodes.toString` | pending | pending | pending |

### web

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `web.ByteString` | pending | pending | pending |
| `web.newInjectableWebClient` | pending | pending | pending |
| `web.newInjectableWebView` | pending | pending | pending |
| `web.newWebSocket` | pending | pending | pending |

### workManager

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `workManager.addBroadcastIntentTask` | pending | pending | pending |
| `workManager.addDailyTask` | pending | pending | pending |
| `workManager.addDisposableTask` | pending | pending | pending |
| `workManager.addIntentTask` | pending | pending | pending |
| `workManager.addWeeklyTask` | pending | pending | pending |
| `workManager.getIntentTask` | pending | pending | pending |
| `workManager.getTimedTask` | pending | pending | pending |
| `workManager.queryIntentTasks` | pending | pending | pending |
| `workManager.queryTimedTasks` | pending | pending | pending |
| `workManager.removeIntentTask` | pending | pending | pending |
| `workManager.removeTimedTask` | pending | pending | pending |

### yolo

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `yolo.load` | pending | pending | pending |

### zip

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `zip.open` | pending | pending | pending |
| `zip.unzip` | pending | pending | pending |
| `zip.zipDir` | pending | pending | pending |
| `zip.zipFile` | pending | pending | pending |
| `zip.zipFiles` | pending | pending | pending |
