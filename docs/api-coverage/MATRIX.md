<!-- runtime-matrix: schema=autojs6-runtime-matrix-v1 dataSource=ksp rows=2724 -->
# AutoJs6 脚本 API 运行时覆盖矩阵

由 `tools/api-coverage/runtime_matrix.py` 生成 (`python tools/api-coverage/check.py --matrix`), 不要手改. 定义见 `docs/api-coverage/ROADMAP.md` 第 4 节, 机读版本是 CI 产物 `build/reports/api-coverage/runtime-matrix.json`, 人工标注 (别名映射, 排除理由, 等价写法) 只在 `tools/api-coverage/runtime-policy.json`; 排除规则在 D12 / Q1 拍板前是草案.

数据源: ksp, 2724 行 (显式导出 1368, 返回对象成员 1356).

## 状态汇总

| 列 | bridged | local-shim | capability-gated | not-portable | denied | pending |
|---|---:|---:|---:|---:|---:|---:|
| Node main | 616 | 143 | 0 | 1120 | 1 | 844 |
| Node Worker | 482 | 62 | 0 | 1180 | 1 | 999 |
| Bun | 0 | 62 | 534 | 1179 | 1 | 948 |

| 列 | 取值 |
|---|---|
| doc | none 331 / mention 230 / heading 2163 / signature 0 / example 0 |
| dts | none 79 / name 1507 / arity 1138 / typed 0 |
| ace | none 540 / semantic 825 / static 1359 |

排除理由缺失: 0. Bun 缺能力名: 0. 策略冲突: jsox-extensions shadowed (node_main, 54), rhino-engine shadowed (node_main, 3), sync-sleep shadowed (node_main, 1).

## 排除规则 (草案)

| 规则 | 状态 / 理由 | Node main | Node Worker | Bun | 等价写法 |
|---|---|---:|---:|---:|---|
| accessibility-sync-wait | denied: sync-only | 1 | 1 | 1 | await accessibility.ensureEnabled(); flow waitFor* with node snapshots |
| internal-exports | not-portable: rhino-only | 9 | 9 | 9 |  |
| java-prototype | not-portable: java-prototype | 5 | 5 | 5 | java module handles (java.create / java.callInstance / java.callStatic) |
| java-prototype-objects | not-portable: java-prototype | 1066 | 1066 | 1066 |  |
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
| App# | 10 | 10 | 0 | 0 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| Arrayx | 15 | 15 | 15 | 15 | 15 / 0 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| Canvas# | 54 | 54 | 54 | 0 | 0 / 54 / 0 | 0 / 54 / 0 | 0 / 54 / 0 |
| Color# | 29 | 21 | 28 | 28 | 0 / 0 / 29 | 0 / 0 / 29 | 0 / 0 / 29 |
| ConsoleConfigurator# | 53 | 50 | 53 | 0 | 0 / 53 / 0 | 0 / 53 / 0 | 0 / 53 / 0 |
| ContinuationCreator# | 3 | 3 | 3 | 3 | 0 / 3 / 0 | 0 / 3 / 0 | 0 / 3 / 0 |
| Database# | 51 | 51 | 51 | 2 | 0 / 51 / 0 | 0 / 51 / 0 | 0 / 51 / 0 |
| Dialog# | 140 | 6 | 139 | 3 | 0 / 140 / 0 | 0 / 140 / 0 | 0 / 140 / 0 |
| EpubBook# | 14 | 14 | 14 | 14 | 0 / 0 / 14 | 0 / 0 / 14 | 0 / 0 / 14 |
| EpubReaderSession# | 35 | 20 | 34 | 32 | 0 / 35 / 0 | 0 / 35 / 0 | 0 / 35 / 0 |
| FloatyWindow# | 12 | 9 | 12 | 12 | 0 / 12 / 0 | 0 / 12 / 0 | 0 / 12 / 0 |
| Flow# | 106 | 25 | 106 | 106 | 0 / 0 / 106 | 0 / 0 / 106 | 0 / 0 / 106 |
| ImageFeatures# | 12 | 10 | 12 | 12 | 0 / 12 / 0 | 0 / 12 / 0 | 0 / 12 / 0 |
| ImageWrapper# | 16 | 15 | 16 | 6 | 0 / 16 / 0 | 0 / 16 / 0 | 0 / 16 / 0 |
| InjectableWebClient# | 3 | 2 | 3 | 0 | 0 / 3 / 0 | 0 / 3 / 0 | 0 / 3 / 0 |
| InjectableWebView# | 1 | 1 | 1 | 0 | 0 / 1 / 0 | 0 / 1 / 0 | 0 / 1 / 0 |
| IntentTask# | 21 | 0 | 20 | 0 | 0 / 21 / 0 | 0 / 21 / 0 | 0 / 21 / 0 |
| MailClient# | 53 | 53 | 53 | 53 | 0 / 0 / 53 | 0 / 0 / 53 | 0 / 0 / 53 |
| Mat# | 9 | 0 | 3 | 0 | 0 / 9 / 0 | 0 / 9 / 0 | 0 / 9 / 0 |
| MatchingResult# | 16 | 15 | 16 | 16 | 0 / 16 / 0 | 0 / 16 / 0 | 0 / 16 / 0 |
| Mathx | 23 | 21 | 23 | 23 | 23 / 0 / 0 | 0 / 23 / 0 | 0 / 23 / 0 |
| Numberx | 14 | 14 | 14 | 14 | 14 / 0 / 0 | 0 / 14 / 0 | 0 / 14 / 0 |
| ObjectFrame# | 15 | 15 | 15 | 15 | 0 / 15 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| PReadableTextFile# | 5 | 4 | 5 | 0 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| PWritableTextFile# | 7 | 6 | 2 | 0 | 0 / 7 / 0 | 0 / 7 / 0 | 0 / 7 / 0 |
| PngQuantizationFileResult# | 5 | 3 | 5 | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| PngQuantizationResult# | 5 | 3 | 5 | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| RawWindow# | 12 | 9 | 12 | 12 | 0 / 12 / 0 | 0 / 12 / 0 | 0 / 12 / 0 |
| ScreenCaptureOptions# | 5 | 5 | 5 | 0 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| ScriptEngine# | 29 | 12 | 26 | 0 | 0 / 29 / 0 | 0 / 29 / 0 | 0 / 29 / 0 |
| ScriptExecution# | 13 | 5 | 12 | 0 | 0 / 13 / 0 | 0 / 13 / 0 | 0 / 13 / 0 |
| ShellProcess# | 25 | 12 | 25 | 25 | 0 / 0 / 25 | 0 / 0 / 25 | 0 / 0 / 25 |
| ShellResult# | 5 | 5 | 5 | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| Storage# | 12 | 12 | 12 | 12 | 0 / 0 / 12 | 0 / 0 / 12 | 0 / 0 / 12 |
| ThemeColor# | 10 | 0 | 10 | 0 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| TimedTask# | 69 | 0 | 44 | 0 | 0 / 69 / 0 | 0 / 69 / 0 | 0 / 69 / 0 |
| TtsUtterance# | 25 | 11 | 23 | 21 | 0 / 25 / 0 | 0 / 25 / 0 | 0 / 25 / 0 |
| UiObject# | 145 | 136 | 145 | 145 | 0 / 145 / 0 | 0 / 145 / 0 | 0 / 145 / 0 |
| UiObjectCollection# | 70 | 66 | 70 | 70 | 0 / 70 / 0 | 0 / 70 / 0 | 0 / 70 / 0 |
| UiSelector# | 185 | 182 | 184 | 184 | 0 / 185 / 0 | 0 / 185 / 0 | 0 / 185 / 0 |
| VolatileDispose# | 3 | 3 | 3 | 3 | 0 / 0 / 3 | 0 / 0 / 3 | 0 / 0 / 3 |
| WebSocket# | 25 | 10 | 24 | 0 | 0 / 25 / 0 | 0 / 25 / 0 | 0 / 25 / 0 |
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
| `ConsoleConfigurator#clearStates` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#contentBackgroundAlpha` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#contentBackgroundColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#contentBackgroundTint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#contentTextColors` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#contentTextSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#exitOnCloseTimeout` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#gravity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#isAvoidStatusBar` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#isColorful` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#isExitOnClose` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#isInputVisible` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#isTimeVisible` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#isTouchable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#position` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setAvoidStatusBar` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setBackgroundAlpha` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setBackgroundColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setBackgroundTint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setColorful` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setContentBackgroundAlpha` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setContentBackgroundColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setContentBackgroundTint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setContentTextColors` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setContentTextSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setExitOnClose` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setGravity` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setInputVisible` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setPosition` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTextColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTextSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTimeFormat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTimeVisible` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTitle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTitleBackgroundAlpha` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTitleBackgroundColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTitleBackgroundTint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTitleIconsTint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTitleTextColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTitleTextSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTouchThrough` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#setTouchable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#show` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#timeFormat` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#title` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#titleBackgroundAlpha` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#titleBackgroundColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#titleBackgroundTint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#titleIconsTint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#titleTextColor` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ConsoleConfigurator#titleTextSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
| `Dialog#defaultMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dismiss` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dispatchGenericMotionEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dispatchKeyEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dispatchKeyShortcutEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dispatchPopulateAccessibilityEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dispatchTouchEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#dispatchTrackballEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `Dialog#onActionModeFinished` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onActionModeStarted` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onAttachedToWindow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onBackPressed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onClick` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onContentChanged` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onContextItemSelected` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onContextMenuClosed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onCreateContextMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onCreateOptionsMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onCreatePanelMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onCreatePanelView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onDetachedFromWindow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onGenericMotionEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onItemSelected` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onKeyDown` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onKeyLongPress` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onKeyMultiple` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onKeyShortcut` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onKeyUp` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onMenuItemSelected` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onMenuOpened` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onOptionsItemSelected` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onOptionsMenuClosed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onPanelClosed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onPrepareOptionsMenu` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onPreparePanel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onProvideKeyboardShortcuts` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onRestoreInstanceState` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onSaveInstanceState` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onSearchRequested` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onShow` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onTouchEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onTrackballEvent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onWindowAttributesChanged` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onWindowFocusChanged` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Dialog#onWindowStartingActionMode` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `Dialog#setOnBackPressedFromJs` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `EpubReaderSession#addListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#bookmarks` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#chapterTitle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `EpubReaderSession#defaultMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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

### FloatyWindow#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `FloatyWindow#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#disableFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#exitOnClose` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#findView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#height` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#isAdjustEnabled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#requestFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#setPosition` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#setSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#x` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `FloatyWindow#y` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
| `ImageFeatures#count` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#isRecycled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#javaObject` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#method` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageFeatures#onRecycled` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `ImageWrapper#scriptRuntime` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#setOneShot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#shoot` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#size` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ImageWrapper#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### InjectableWebClient#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `InjectableWebClient#inject` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `InjectableWebClient#injectAndWait` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `InjectableWebClient#onPageFinished` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### InjectableWebView#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `InjectableWebView#inject` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### IntentTask#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `IntentTask#TABLE` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `IntentTask#setScriptPathAndCaptureContext` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `IntentTask#setSourceKind` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
| `Mat#convertTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#from` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#getCreatedCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#getFinalizedCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#getLiveCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#getReleasedCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#isReleased` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `Mat#ones` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `PWritableTextFile#open` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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

### RawWindow#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `RawWindow#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#disableFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#exitOnClose` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#findView` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#height` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#requestFocus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#setPosition` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#setSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#setTouchable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#width` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#x` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `RawWindow#y` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
| `ScriptEngine#TAG_ENV_PATH` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#TAG_SOURCE` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#TAG_WORKING_DIRECTORY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#cwd` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#destroy` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#emit` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#execute` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#forceStop` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getBackingSourcePath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getExecArgv` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getRuntime` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSource` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSourceDirectory` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSourcePath` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getSourceUri` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getStartTime` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getTag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#getUncaughtException` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#hasFeature` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#init` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#isDestroyed` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#put` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#setExecArgv` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#setId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#setOnDestroyListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#setRuntime` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#setTag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ScriptEngine#uncaughtException` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### ScriptExecution#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ScriptExecution#NO_ID` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `ShellResult#code` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#error` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#result` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#throwIfError` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `ShellResult#toJson` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
| `TimedTask#END_MODE_COUNT` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#END_MODE_FOREVER` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#END_MODE_UNTIL_DATE` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#FLAG_FRIDAY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#FLAG_MONDAY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#FLAG_SATURDAY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#FLAG_SUNDAY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#FLAG_THURSDAY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#FLAG_TUESDAY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#FLAG_WEDNESDAY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#REPEAT_MODE_COUNTDOWN` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#REPEAT_MODE_DAILY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#REPEAT_MODE_DISPOSABLE` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#REPEAT_MODE_MONTHLY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#REPEAT_MODE_UNKNOWN` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#REPEAT_MODE_WEEKLY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#REPEAT_MODE_YEARLY` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#TABLE` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#captureLaunchContext` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#countdownTask` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#createIntent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#createPendingIntent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#dailyTask` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#disposableTask` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getDayOfMonth` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#getDayOfWeekTimeFlag` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `TimedTask#monthlyTask` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#notifyFinished` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `TimedTask#weeklyTask` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TimedTask#yearlyTask` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### TtsUtterance#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `TtsUtterance#addListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#cancel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `TtsUtterance#defaultMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `TtsUtterance#utterance` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
| `UiObject#findAccessibilityNodeInfosByText` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#findAccessibilityNodeInfosByViewId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#findOne` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#fingerprint` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#firstChild` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#firstSibling` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#focus` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#focusable` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#focused` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#fullId` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#getChild` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `UiObject#getParent` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `UiObject#recycle` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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
| `UiSelector#applyEntry` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
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

### VolatileDispose#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `VolatileDispose#blockedGet` | pending | pending | pending |
| `VolatileDispose#blockedGetOrThrow` | pending | pending | pending |
| `VolatileDispose#setAndNotify` | pending | pending | pending |

### WebSocket#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `WebSocket#addListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#cancel` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#client` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#close` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#defaultMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#emit` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#emitSticky` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#eventNames` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#exitOnClose` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#getMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#getTimer` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#listenerCount` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#listeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#on` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#once` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#prependListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#prependOnceListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#queueSize` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#rebuild` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#removeAllListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#removeListener` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#request` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#send` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#setMaxListeners` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `WebSocket#url` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
