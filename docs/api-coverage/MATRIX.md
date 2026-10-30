<!-- runtime-matrix: schema=autojs6-runtime-matrix-v1 dataSource=ksp rows=3470 -->
# AutoJs6 脚本 API 运行时覆盖矩阵

由 `tools/api-coverage/runtime_matrix.py` 生成 (`python tools/api-coverage/check.py --matrix`), 不要手改. 定义见 `docs/api-coverage/ROADMAP.md` 第 4 节, 机读版本是 CI 产物 `build/reports/api-coverage/runtime-matrix.json`, 人工标注 (别名映射, 排除理由, 等价写法) 只在 `tools/api-coverage/runtime-policy.json`; 排除规则已拍板 (D12 / Q1, ROADMAP 附录 D), 新增排除先登记待决; pending 只能减少 (`tools/api-coverage/pending-baseline.json`).

数据源: ksp, 3470 行 (显式导出 1962, 原型成员 205, 返回对象成员 1303).

## 状态汇总

| 列 | bridged | local-shim | capability-gated | not-portable | denied | pending |
|---|---:|---:|---:|---:|---:|---:|
| Node main | 1262 | 348 | 0 | 1046 | 0 | 814 |
| Node Worker | 1209 | 345 | 0 | 1049 | 0 | 867 |
| Bun | 0 | 326 | 1266 | 1043 | 0 | 835 |

| 列 | 取值 |
|---|---|
| doc | none 532 / mention 268 / heading 2670 / signature 0 / example 0 |
| dts | none 533 / name 1774 / arity 1163 / typed 0 |
| ace | none 938 / semantic 898 / static 1634 |

排除理由缺失: 0. Bun 缺能力名: 0. 策略冲突: 无.

拒绝入口 (运行时声明只返回 never / Promise<never>, 记 pending, 排除须由规则给理由; Q26): Node main 5 / Node Worker 5 / Bun 2. 句柄类型 (runtime-policy.json returnedObjects, Q27) 的成员按运行时类型逐个比对.

名称门禁暂缓 (policy.json deferredNameGate; doc / d.ts / Ace 列照实记录, 缺口不计入 check.py 失败): imgui 524 行 (Q115 (a): 文档 / d.ts / Ace 由 P5.2f-2 补, 待 imgui R 系列稳定).

## 排除规则

| 规则 | 状态 / 理由 | Node main | Node Worker | Bun | 等价写法 |
|---|---|---:|---:|---:|---|
| http-java-objects | not-portable: java-prototype | 2 | 2 | 2 | fetch / Node autojs6:fetch request options (method, headers, body) instead of the OkHttp client and request objects |
| internal-exports | not-portable: rhino-only | 10 | 10 | 10 |  |
| java-introspection | not-portable: java-prototype | 3 | 3 | 3 | java module handles (java.callInstance(handle, "getClass")) |
| java-prototype | not-portable: java-prototype | 5 | 5 | 5 | java module handles (java.create / java.callInstance / java.callStatic) |
| java-prototype-objects | not-portable: java-prototype | 985 | 985 | 985 |  |
| native-base64 | not-portable: runtime-native | 0 | 2 | 2 | Buffer.from(text).toString("base64") / Buffer.from(data, "base64"), atob / btoa |
| native-crypto | not-portable: runtime-native | 8 | 8 | 8 | node:crypto KeyObject / generateKeyPair, WebCrypto (globalThis.crypto.subtle) |
| native-http | not-portable: runtime-native | 16 | 16 | 18 | global fetch (Node / Bun), node:http; autojs6:fetch for host proxy / certificate policy |
| native-memory-watch | not-portable: runtime-native | 1 | 1 | 1 | FinalizationRegistry (a callback once the object is collected), node:v8 writeHeapSnapshot / getHeapStatistics; in Bun bun:jsc heapStats / generateHeapSnapshot |
| native-timers | not-portable: runtime-native | 0 | 0 | 6 | global setTimeout / setInterval / setImmediate and their clear functions |
| obsoleted-functions | not-portable: obsoleted | 2 | 2 | 2 |  |
| rhino-engine | not-portable: rhino-only | 0 | 1 | 1 |  |

## 按模块汇总

运行时列为 覆盖 (bridged / local-shim / capability-gated) / 排除 (not-portable / denied) / pending; doc 为有标题的行数, d.ts 为声明可解析的行数, Ace 为默认与全组补全都命中的行数.

| 模块 | 行 | doc 标题 | d.ts | Ace | Node main | Node Worker | Bun |
|---|---:|---:|---:|---:|---|---|---|
| (global) | 208 | 206 | 205 | 205 | 163 / 13 / 32 | 149 / 13 / 46 | 167 / 13 / 28 |
| AgentRun# | 14 | 7 | 14 | 14 | 14 / 0 / 0 | 14 / 0 / 0 | 14 / 0 / 0 |
| AiSession# | 11 | 11 | 11 | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| App# | 10 | 10 | 10 | 10 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| Arrayx | 15 | 15 | 15 | 15 | 15 / 0 / 0 | 15 / 0 / 0 | 15 / 0 / 0 |
| Canvas# | 54 | 54 | 54 | 0 | 0 / 54 / 0 | 0 / 54 / 0 | 0 / 54 / 0 |
| Color# | 29 | 29 | 29 | 29 | 29 / 0 / 0 | 29 / 0 / 0 | 29 / 0 / 0 |
| ComposeModifier# | 20 | 20 | 20 | 20 | 20 / 0 / 0 | 20 / 0 / 0 | 20 / 0 / 0 |
| ComposeNode# | 8 | 8 | 8 | 8 | 0 / 0 / 8 | 0 / 0 / 8 | 0 / 0 / 8 |
| ConsoleConfigurator# | 52 | 50 | 52 | 0 | 0 / 52 / 0 | 0 / 52 / 0 | 0 / 52 / 0 |
| ContinuationCreator# | 3 | 3 | 3 | 3 | 0 / 3 / 0 | 0 / 3 / 0 | 0 / 3 / 0 |
| Database# | 51 | 51 | 51 | 2 | 0 / 51 / 0 | 0 / 51 / 0 | 0 / 51 / 0 |
| Dialog# | 96 | 19 | 96 | 3 | 0 / 96 / 0 | 0 / 96 / 0 | 0 / 96 / 0 |
| EpubBook# | 14 | 14 | 14 | 14 | 14 / 0 / 0 | 14 / 0 / 0 | 14 / 0 / 0 |
| EpubReaderSession# | 34 | 34 | 34 | 32 | 0 / 34 / 0 | 0 / 34 / 0 | 0 / 34 / 0 |
| FloatyWindow# | 12 | 12 | 12 | 12 | 0 / 12 / 0 | 0 / 12 / 0 | 0 / 12 / 0 |
| Flow# | 106 | 25 | 106 | 106 | 104 / 0 / 2 | 104 / 0 / 2 | 104 / 0 / 2 |
| ImageFeatures# | 10 | 10 | 10 | 10 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| ImageWrapper# | 15 | 15 | 15 | 6 | 0 / 15 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| InjectableWebClient# | 2 | 2 | 2 | 0 | 0 / 2 / 0 | 0 / 2 / 0 | 0 / 2 / 0 |
| InjectableWebView# | 1 | 1 | 1 | 0 | 0 / 1 / 0 | 0 / 1 / 0 | 0 / 1 / 0 |
| IntentTask# | 19 | 0 | 19 | 0 | 0 / 19 / 0 | 0 / 19 / 0 | 0 / 19 / 0 |
| MailClient# | 53 | 53 | 53 | 53 | 53 / 0 / 0 | 53 / 0 / 0 | 53 / 0 / 0 |
| Mat# | 3 | 0 | 3 | 0 | 0 / 3 / 0 | 0 / 3 / 0 | 0 / 3 / 0 |
| MatchingResult# | 16 | 16 | 16 | 16 | 0 / 16 / 0 | 0 / 16 / 0 | 0 / 16 / 0 |
| Mathx | 23 | 23 | 23 | 23 | 23 / 0 / 0 | 23 / 0 / 0 | 23 / 0 / 0 |
| Numberx | 14 | 14 | 14 | 14 | 14 / 0 / 0 | 14 / 0 / 0 | 14 / 0 / 0 |
| ObjectFrame# | 15 | 15 | 15 | 15 | 0 / 15 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| OcrResult# | 5 | 5 | 5 | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| PReadableTextFile# | 5 | 5 | 5 | 0 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| PWritableTextFile# | 6 | 6 | 6 | 0 | 0 / 6 / 0 | 0 / 6 / 0 | 0 / 6 / 0 |
| PngQuantizationFileResult# | 5 | 3 | 5 | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| PngQuantizationResult# | 5 | 3 | 5 | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| RawWindow# | 12 | 12 | 12 | 12 | 0 / 12 / 0 | 0 / 12 / 0 | 0 / 12 / 0 |
| ScreenCaptureOptions# | 5 | 5 | 5 | 0 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| ScriptEngine# | 16 | 16 | 16 | 0 | 0 / 16 / 0 | 0 / 16 / 0 | 0 / 16 / 0 |
| ScriptExecution# | 12 | 12 | 12 | 0 | 0 / 12 / 0 | 0 / 12 / 0 | 0 / 12 / 0 |
| ShellProcess# | 25 | 12 | 25 | 25 | 25 / 0 / 0 | 25 / 0 / 0 | 25 / 0 / 0 |
| ShellResult# | 5 | 5 | 5 | 5 | 0 / 5 / 0 | 0 / 5 / 0 | 0 / 5 / 0 |
| Storage# | 12 | 12 | 12 | 12 | 12 / 0 / 0 | 12 / 0 / 0 | 12 / 0 / 0 |
| ThemeColor# | 10 | 0 | 10 | 0 | 0 / 10 / 0 | 0 / 10 / 0 | 0 / 10 / 0 |
| TimedTask# | 40 | 0 | 40 | 0 | 0 / 40 / 0 | 0 / 40 / 0 | 0 / 40 / 0 |
| TtsUtterance# | 23 | 23 | 23 | 21 | 0 / 23 / 0 | 0 / 23 / 0 | 0 / 23 / 0 |
| UiObject# | 141 | 139 | 141 | 140 | 0 / 141 / 0 | 0 / 141 / 0 | 0 / 141 / 0 |
| UiObjectCollection# | 70 | 69 | 70 | 70 | 0 / 70 / 0 | 0 / 70 / 0 | 0 / 70 / 0 |
| UiSelector# | 186 | 186 | 186 | 186 | 0 / 186 / 0 | 0 / 186 / 0 | 0 / 186 / 0 |
| VolatileDispose# | 3 | 3 | 3 | 3 | 0 / 0 / 3 | 0 / 0 / 3 | 0 / 0 / 3 |
| WebSocket# | 24 | 24 | 24 | 0 | 0 / 24 / 0 | 0 / 24 / 0 | 0 / 24 / 0 |
| WrappedBarcode# | 22 | 22 | 22 | 22 | 0 / 22 / 0 | 0 / 22 / 0 | 0 / 22 / 0 |
| YoloDetector# | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| Zip# | 21 | 21 | 21 | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| ai | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| app | 43 | 43 | 43 | 43 | 43 / 0 / 0 | 43 / 0 / 0 | 43 / 0 / 0 |
| auto | 38 | 38 | 38 | 38 | 0 / 0 / 38 | 0 / 0 / 38 | 0 / 0 / 38 |
| autojs | 21 | 21 | 21 | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| autojs.version | 7 | 7 | 7 | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| automator | 54 | 54 | 54 | 54 | 32 / 0 / 22 | 19 / 0 / 35 | 26 / 0 / 28 |
| barcode | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 0 / 0 / 4 | 0 / 0 / 4 |
| base64 | 2 | 2 | 2 | 2 | 2 / 0 / 0 | 0 / 2 / 0 | 0 / 2 / 0 |
| colors | 82 | 82 | 82 | 82 | 82 / 0 / 0 | 82 / 0 / 0 | 82 / 0 / 0 |
| colors.theme | 9 | 9 | 9 | 9 | 0 / 0 / 9 | 0 / 0 / 9 | 0 / 0 / 9 |
| compose | 71 | 71 | 71 | 71 | 70 / 0 / 1 | 70 / 0 / 1 | 70 / 0 / 1 |
| console | 56 | 56 | 56 | 56 | 42 / 0 / 14 | 42 / 0 / 14 | 42 / 0 / 14 |
| continuation | 4 | 3 | 4 | 4 | 0 / 0 / 4 | 0 / 0 / 4 | 0 / 0 / 4 |
| crypto | 8 | 7 | 8 | 8 | 0 / 8 / 0 | 0 / 8 / 0 | 0 / 8 / 0 |
| cvt.bytes | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| device | 86 | 86 | 86 | 86 | 86 / 0 / 0 | 86 / 0 / 0 | 86 / 0 / 0 |
| dialogs | 9 | 9 | 9 | 9 | 9 / 0 / 0 | 9 / 0 / 0 | 9 / 0 / 0 |
| engines | 8 | 8 | 8 | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| epub | 21 | 21 | 21 | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| events | 37 | 34 | 36 | 36 | 5 / 1 / 31 | 5 / 1 / 31 | 5 / 1 / 31 |
| files | 34 | 34 | 34 | 34 | 34 / 0 / 0 | 34 / 0 / 0 | 34 / 0 / 0 |
| files.batchRename | 17 | 17 | 17 | 17 | 0 / 0 / 17 | 0 / 0 / 17 | 0 / 0 / 17 |
| floaty | 8 | 8 | 8 | 8 | 0 / 0 / 8 | 0 / 0 / 8 | 0 / 0 / 8 |
| flow | 55 | 55 | 55 | 55 | 55 / 0 / 0 | 55 / 0 / 0 | 55 / 0 / 0 |
| fmt.bytes | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| gallery | 11 | 11 | 11 | 11 | 0 / 0 / 11 | 0 / 0 / 11 | 0 / 0 / 11 |
| http | 21 | 20 | 20 | 20 | 0 / 21 / 0 | 0 / 21 / 0 | 0 / 21 / 0 |
| images | 82 | 82 | 82 | 82 | 82 / 0 / 0 | 82 / 0 / 0 | 82 / 0 / 0 |
| imgui | 524 | 0 | 0 | 0 | 0 / 1 / 523 | 0 / 1 / 523 | 0 / 1 / 523 |
| installer | 16 | 16 | 16 | 16 | 16 / 0 / 0 | 16 / 0 / 0 | 16 / 0 / 0 |
| jsox | 2 | 2 | 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| keys | 12 | 12 | 12 | 12 | 12 / 0 / 0 | 0 / 0 / 12 | 12 / 0 / 0 |
| mail | 56 | 56 | 56 | 56 | 56 / 0 / 0 | 56 / 0 / 0 | 56 / 0 / 0 |
| media | 9 | 9 | 9 | 9 | 9 / 0 / 0 | 9 / 0 / 0 | 9 / 0 / 0 |
| mediainfo | 7 | 7 | 7 | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| memory | 5 | 5 | 5 | 5 | 4 / 1 / 0 | 4 / 1 / 0 | 4 / 1 / 0 |
| mime | 5 | 5 | 5 | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |
| notice | 7 | 7 | 7 | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| notice.channel | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| ocr | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 0 / 0 / 6 | 0 / 0 / 6 |
| ocr.mlkit | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| ocr.paddle | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| ocr.rapid | 2 | 2 | 2 | 2 | 0 / 0 / 2 | 0 / 0 / 2 | 0 / 0 / 2 |
| opencc | 33 | 33 | 33 | 33 | 33 / 0 / 0 | 33 / 0 / 0 | 33 / 0 / 0 |
| pinyin | 16 | 16 | 16 | 16 | 16 / 0 / 0 | 16 / 0 / 0 | 16 / 0 / 0 |
| pinyin4j | 3 | 3 | 3 | 3 | 3 / 0 / 0 | 3 / 0 / 0 | 3 / 0 / 0 |
| plugins | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 3 / 1 / 0 | 3 / 1 / 0 |
| plugins.tools | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| powerManager | 2 | 2 | 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| qrcode | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 0 / 0 / 4 | 0 / 0 / 4 |
| recorder | 7 | 7 | 7 | 7 | 7 / 0 / 0 | 7 / 0 / 0 | 7 / 0 / 0 |
| s13n | 5 | 5 | 5 | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |
| sensors | 21 | 21 | 21 | 21 | 21 / 0 / 0 | 21 / 0 / 0 | 21 / 0 / 0 |
| settings | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| shell | 11 | 11 | 11 | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| shizuku | 13 | 8 | 13 | 13 | 13 / 0 / 0 | 13 / 0 / 0 | 13 / 0 / 0 |
| species | 37 | 37 | 37 | 37 | 37 / 0 / 0 | 37 / 0 / 0 | 37 / 0 / 0 |
| speech | 17 | 17 | 17 | 17 | 11 / 0 / 6 | 11 / 0 / 6 | 11 / 0 / 6 |
| speech.models | 8 | 8 | 8 | 8 | 8 / 0 / 0 | 8 / 0 / 0 | 8 / 0 / 0 |
| speech.speaker | 8 | 8 | 8 | 8 | 0 / 0 / 8 | 0 / 0 / 8 | 0 / 0 / 8 |
| sqlite | 1 | 1 | 1 | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| storages | 5 | 5 | 5 | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |
| sysprops | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| tasks | 20 | 20 | 20 | 20 | 20 / 0 / 0 | 20 / 0 / 0 | 20 / 0 / 0 |
| terminal | 20 | 20 | 20 | 20 | 0 / 0 / 20 | 0 / 0 / 20 | 0 / 0 / 20 |
| threads | 8 | 8 | 8 | 8 | 0 / 0 / 8 | 0 / 0 / 8 | 0 / 0 / 8 |
| timers | 18 | 18 | 18 | 18 | 10 / 6 / 2 | 10 / 6 / 2 | 10 / 0 / 8 |
| toast | 1 | 1 | 1 | 1 | 0 / 0 / 1 | 0 / 0 / 1 | 1 / 0 / 0 |
| tts | 35 | 35 | 35 | 35 | 35 / 0 / 0 | 35 / 0 / 0 | 35 / 0 / 0 |
| ui | 40 | 36 | 36 | 36 | 3 / 4 / 33 | 3 / 4 / 33 | 3 / 4 / 33 |
| util | 54 | 54 | 54 | 54 | 48 / 6 / 0 | 48 / 6 / 0 | 48 / 6 / 0 |
| util.inspect | 2 | 2 | 2 | 2 | 2 / 0 / 0 | 2 / 0 / 0 | 2 / 0 / 0 |
| util.java | 6 | 6 | 6 | 6 | 6 / 0 / 0 | 6 / 0 / 0 | 6 / 0 / 0 |
| util.morseCode | 3 | 3 | 3 | 3 | 3 / 0 / 0 | 3 / 0 / 0 | 3 / 0 / 0 |
| util.version | 1 | 1 | 1 | 1 | 1 / 0 / 0 | 1 / 0 / 0 | 1 / 0 / 0 |
| util.versionCodes | 4 | 4 | 4 | 4 | 4 / 0 / 0 | 4 / 0 / 0 | 4 / 0 / 0 |
| web | 4 | 4 | 4 | 4 | 0 / 0 / 4 | 0 / 0 / 4 | 0 / 0 / 4 |
| workManager | 11 | 11 | 11 | 11 | 11 / 0 / 0 | 11 / 0 / 0 | 11 / 0 / 0 |
| yolo | 1 | 1 | 1 | 1 | 0 / 0 / 1 | 0 / 0 / 1 | 0 / 0 / 1 |
| zip | 5 | 5 | 5 | 5 | 5 / 0 / 0 | 5 / 0 / 0 | 5 / 0 / 0 |

## 差异行

只列至少一个运行时列不是覆盖状态的行.

### (global)

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ComposeError` | pending | pending | pending |
| `TODO` | pending | pending | pending |
| `__autojs6Tsx` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `__autojs6TsxFragment` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `back` | bridged | pending | capability-gated |
| `clearImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `clearInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `clearTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `click` | bridged | pending | capability-gated |
| `detect` | pending | pending | pending |
| `existsAll` | pending | pending | pending |
| `existsOne` | pending | pending | pending |
| `gesture` | bridged | pending | capability-gated |
| `gestureAsync` | pending | pending | pending |
| `gestures` | pending | pending | pending |
| `gesturesAsync` | pending | pending | pending |
| `home` | bridged | pending | capability-gated |
| `input` | pending | pending | pending |
| `isJavaClass` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isJavaPackage` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `isUiThread` | pending | pending | local-shim |
| `keepAlive` | pending | pending | pending |
| `longClick` | bridged | pending | capability-gated |
| `loop` | pending | pending | pending |
| `newInjectableWebClient` | pending | pending | pending |
| `newInjectableWebView` | pending | pending | pending |
| `newWebSocket` | pending | pending | pending |
| `notifications` | bridged | pending | pending |
| `pickup` | pending | pending | pending |
| `powerDialog` | bridged | pending | pending |
| `press` | pending | pending | pending |
| `quickSettings` | bridged | pending | pending |
| `recents` | bridged | pending | pending |
| `retry` | pending | pending | pending |
| `scrollDown` | pending | pending | pending |
| `scrollUp` | pending | pending | pending |
| `setImmediate` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `setInterval` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `setText` | bridged | pending | capability-gated |
| `setTimeout` | not-portable: runtime-native | not-portable: runtime-native | not-portable: runtime-native (native-timers) |
| `sleep` | bridged | pending | pending |
| `splitScreen` | bridged | pending | pending |
| `swipe` | bridged | pending | capability-gated |
| `sync` | pending | pending | pending |
| `toString` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `toastError` | pending | pending | capability-gated |
| `toastInfo` | pending | pending | capability-gated |
| `toastLog` | bridged | pending | capability-gated |
| `toastVerbose` | pending | pending | capability-gated |
| `toastWarn` | pending | pending | capability-gated |
| `toasterror` | pending | pending | capability-gated |
| `toastinfo` | pending | pending | capability-gated |
| `toastlog` | pending | pending | capability-gated |
| `toastverbose` | pending | pending | capability-gated |
| `toastwarn` | pending | pending | capability-gated |
| `unwrapJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |
| `wait` | pending | pending | pending |
| `wait.async` | pending | pending | pending |

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

### ComposeNode#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `ComposeNode#alpha` | pending | pending | pending |
| `ComposeNode#bg` | pending | pending | pending |
| `ComposeNode#contentDescription` | pending | pending | pending |
| `ComposeNode#h` | pending | pending | pending |
| `ComposeNode#padding` | pending | pending | pending |
| `ComposeNode#testTag` | pending | pending | pending |
| `ComposeNode#w` | pending | pending | pending |
| `ComposeNode#weight` | pending | pending | pending |

### ConsoleConfigurator#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
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
| `Flow#await` | pending | pending | pending |
| `Flow#sync` | pending | pending | pending |

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

### InjectableWebClient#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `InjectableWebClient#inject` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `InjectableWebClient#injectAndWait` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

### InjectableWebView#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `InjectableWebView#inject` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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
| `OcrResult#bounds` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `OcrResult#compareTo` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `OcrResult#confidence` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `OcrResult#label` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |
| `OcrResult#text` | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) | not-portable: java-prototype (java-prototype-objects) |

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

### YoloDetector#

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `YoloDetector#close` | pending | pending | pending |
| `YoloDetector#detect` | pending | pending | pending |

### auto

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `auto.clearCache` | pending | pending | pending |
| `auto.clearFlags` | pending | pending | pending |
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
| `auto.waitFor` | pending | pending | pending |
| `auto.windowRoots` | pending | pending | pending |
| `auto.windows` | pending | pending | pending |

### automator

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `automator.accessibilityAllApps` | pending | pending | pending |
| `automator.accessibilityButton` | pending | pending | pending |
| `automator.accessibilityButtonChooser` | pending | pending | pending |
| `automator.accessibilityShortcut` | pending | pending | pending |
| `automator.back` | bridged | pending | capability-gated |
| `automator.captureScreen` | pending | pending | pending |
| `automator.click` | bridged | pending | capability-gated |
| `automator.dismissNotificationShade` | pending | pending | pending |
| `automator.ensureService` | pending | pending | pending |
| `automator.gesture` | bridged | pending | capability-gated |
| `automator.gestureAsync` | pending | pending | pending |
| `automator.gestures` | pending | pending | pending |
| `automator.gesturesAsync` | pending | pending | pending |
| `automator.headsetHook` | pending | pending | pending |
| `automator.headsethook` | pending | pending | pending |
| `automator.home` | bridged | pending | capability-gated |
| `automator.input` | pending | pending | pending |
| `automator.isServiceRunning` | pending | pending | pending |
| `automator.lockScreen` | bridged | pending | pending |
| `automator.longClick` | bridged | pending | capability-gated |
| `automator.notifications` | bridged | pending | pending |
| `automator.powerDialog` | bridged | pending | pending |
| `automator.press` | pending | pending | pending |
| `automator.quickSettings` | bridged | pending | pending |
| `automator.recents` | pending | pending | pending |
| `automator.retry` | pending | pending | pending |
| `automator.scrollDown` | pending | pending | pending |
| `automator.scrollUp` | pending | pending | pending |
| `automator.setText` | bridged | pending | capability-gated |
| `automator.splitScreen` | bridged | pending | pending |
| `automator.swipe` | bridged | pending | capability-gated |
| `automator.switchToInputMethod` | pending | pending | pending |
| `automator.switchToInputMethodWithId` | pending | pending | pending |
| `automator.takeScreenshot` | bridged | pending | pending |
| `automator.waitForService` | pending | pending | pending |

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
| `console.build` | pending (rejects) | pending (rejects) | pending |
| `console.input` | pending (rejects) | pending (rejects) | pending |
| `console.isShowing` | pending | pending | pending |
| `console.rawInput` | pending (rejects) | pending (rejects) | pending |
| `console.resetBackgroundAlpha` | pending | pending | pending |
| `console.resetContentBackgroundAlpha` | pending | pending | pending |
| `console.resetTitleBackgroundAlpha` | pending | pending | pending |
| `console.setAvoidStatusBar` | pending | pending | pending |
| `console.setColorful` | pending | pending | pending |
| `console.setGravity` | pending | pending | pending |
| `console.setInputVisible` | pending | pending | pending |
| `console.setTimeFormat` | pending | pending | pending |
| `console.setTimeVisible` | pending | pending | pending |
| `console.setTouchThrough` | pending | pending | pending |

### continuation

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `continuation.await` | pending | pending | pending |
| `continuation.create` | pending | pending | pending |
| `continuation.delay` | pending | pending | pending |
| `continuation.enabled` | pending | pending | pending |

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
| `events.addListener` | pending | pending | pending |
| `events.broadcast` | pending | pending | pending |
| `events.emit` | pending | pending | pending |
| `events.emitSticky` | pending | pending | pending |
| `events.emitter` | pending | pending | pending |
| `events.eventNames` | pending | pending | pending |
| `events.getMaxListeners` | pending | pending | pending |
| `events.getTimer` | pending | pending | pending |
| `events.getTouchEventTimeout` | pending | pending | pending |
| `events.listenerCount` | pending | pending | pending |
| `events.listeners` | pending | pending | pending |
| `events.observeTouch` | pending | pending | pending |
| `events.onKeyDown` | pending | pending | pending |
| `events.onKeyUp` | pending | pending | pending |
| `events.onNotification` | pending | pending | pending |
| `events.onToast` | pending | pending | pending |
| `events.onTouch` | pending | pending | pending |
| `events.onceKeyDown` | pending | pending | pending |
| `events.onceKeyUp` | pending | pending | pending |
| `events.prependListener` | pending | pending | pending |
| `events.prependOnceListener` | pending | pending | pending |
| `events.removeAllKeyDownListeners` | pending | pending | pending |
| `events.removeAllKeyUpListeners` | pending | pending | pending |
| `events.removeAllListeners` | pending | pending | pending |
| `events.removeAllTouchListeners` | pending | pending | pending |
| `events.removeListener` | pending | pending | pending |
| `events.removeNotificationObserver` | pending | pending | pending |
| `events.removeToastObserver` | pending | pending | pending |
| `events.setKeyInterceptionEnabled` | pending | pending | pending |
| `events.setMaxListeners` | pending | pending | pending |
| `events.setTouchEventTimeout` | pending | pending | pending |

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
| `imgui.AddButtonEx` | pending | pending | pending |
| `imgui.Alert` | pending | pending | pending |
| `imgui.AlignTextToFramePadding` | pending | pending | pending |
| `imgui.Appearance` | pending | pending | pending |
| `imgui.ArrowButton` | pending | pending | pending |
| `imgui.BackendStatus` | pending | pending | pending |
| `imgui.Begin` | pending | pending | pending |
| `imgui.BeginChild` | pending | pending | pending |
| `imgui.BeginCombo` | pending | pending | pending |
| `imgui.BeginDisabled` | pending | pending | pending |
| `imgui.BeginGroup` | pending | pending | pending |
| `imgui.BeginItemTooltip` | pending | pending | pending |
| `imgui.BeginListBox` | pending | pending | pending |
| `imgui.BeginMenu` | pending | pending | pending |
| `imgui.BeginMenuBar` | pending | pending | pending |
| `imgui.BeginPopup` | pending | pending | pending |
| `imgui.BeginPopupContextItem` | pending | pending | pending |
| `imgui.BeginPopupContextWindow` | pending | pending | pending |
| `imgui.BeginPopupModal` | pending | pending | pending |
| `imgui.BeginTabBar` | pending | pending | pending |
| `imgui.BeginTabItem` | pending | pending | pending |
| `imgui.BeginTable` | pending | pending | pending |
| `imgui.BeginTooltip` | pending | pending | pending |
| `imgui.Bullet` | pending | pending | pending |
| `imgui.BulletText` | pending | pending | pending |
| `imgui.Button` | pending | pending | pending |
| `imgui.ButtonFlags` | pending | pending | pending |
| `imgui.CalcTextSize` | pending | pending | pending |
| `imgui.Checkbox` | pending | pending | pending |
| `imgui.ChildFlags` | pending | pending | pending |
| `imgui.CloseCurrentPopup` | pending | pending | pending |
| `imgui.CloseWindow` | pending | pending | pending |
| `imgui.Col` | pending | pending | pending |
| `imgui.CollapseWindow` | pending | pending | pending |
| `imgui.CollapsingHeader` | pending | pending | pending |
| `imgui.ColorButton` | pending | pending | pending |
| `imgui.ColorEdit3` | pending | pending | pending |
| `imgui.ColorEdit4` | pending | pending | pending |
| `imgui.ColorEditFlags` | pending | pending | pending |
| `imgui.ColorPicker3` | pending | pending | pending |
| `imgui.ColorPicker4` | pending | pending | pending |
| `imgui.ColorU32` | pending | pending | pending |
| `imgui.Combo` | pending | pending | pending |
| `imgui.ComboFlags` | pending | pending | pending |
| `imgui.Cond` | pending | pending | pending |
| `imgui.Confirm` | pending | pending | pending |
| `imgui.CreateTexture` | pending | pending | pending |
| `imgui.CreateWindow` | pending | pending | pending |
| `imgui.DataTable` | pending | pending | pending |
| `imgui.DestroyTexture` | pending | pending | pending |
| `imgui.Dir` | pending | pending | pending |
| `imgui.Dp` | pending | pending | pending |
| `imgui.DragFloat` | pending | pending | pending |
| `imgui.DragFloat2` | pending | pending | pending |
| `imgui.DragFloat3` | pending | pending | pending |
| `imgui.DragFloat4` | pending | pending | pending |
| `imgui.DragFloatRange2` | pending | pending | pending |
| `imgui.DragInt` | pending | pending | pending |
| `imgui.DragInt2` | pending | pending | pending |
| `imgui.DragInt3` | pending | pending | pending |
| `imgui.DragInt4` | pending | pending | pending |
| `imgui.DragIntRange2` | pending | pending | pending |
| `imgui.DrawFlags` | pending | pending | pending |
| `imgui.DrawListLayer` | pending | pending | pending |
| `imgui.Dummy` | pending | pending | pending |
| `imgui.End` | pending | pending | pending |
| `imgui.EndChild` | pending | pending | pending |
| `imgui.EndCombo` | pending | pending | pending |
| `imgui.EndDisabled` | pending | pending | pending |
| `imgui.EndGroup` | pending | pending | pending |
| `imgui.EndListBox` | pending | pending | pending |
| `imgui.EndMenu` | pending | pending | pending |
| `imgui.EndMenuBar` | pending | pending | pending |
| `imgui.EndPopup` | pending | pending | pending |
| `imgui.EndTabBar` | pending | pending | pending |
| `imgui.EndTabItem` | pending | pending | pending |
| `imgui.EndTable` | pending | pending | pending |
| `imgui.EndTooltip` | pending | pending | pending |
| `imgui.ExpandWindow` | pending | pending | pending |
| `imgui.FocusedFlags` | pending | pending | pending |
| `imgui.Fonts` | pending | pending | pending |
| `imgui.GetBackendCapabilities` | pending | pending | pending |
| `imgui.GetBackgroundDrawList` | pending | pending | pending |
| `imgui.GetComponent` | pending | pending | pending |
| `imgui.GetContentRegionAvail` | pending | pending | pending |
| `imgui.GetCursorPos` | pending | pending | pending |
| `imgui.GetExampleSource` | pending | pending | pending |
| `imgui.GetForegroundDrawList` | pending | pending | pending |
| `imgui.GetFrameHeight` | pending | pending | pending |
| `imgui.GetFrameHeightWithSpacing` | pending | pending | pending |
| `imgui.GetMouseDragDelta` | pending | pending | pending |
| `imgui.GetMousePos` | pending | pending | pending |
| `imgui.GetScrollMaxX` | pending | pending | pending |
| `imgui.GetScrollMaxY` | pending | pending | pending |
| `imgui.GetScrollX` | pending | pending | pending |
| `imgui.GetScrollY` | pending | pending | pending |
| `imgui.GetTextLineHeight` | pending | pending | pending |
| `imgui.GetTextLineHeightWithSpacing` | pending | pending | pending |
| `imgui.GetWindowDrawList` | pending | pending | pending |
| `imgui.GetWindowPos` | pending | pending | pending |
| `imgui.GetWindowSize` | pending | pending | pending |
| `imgui.HoveredFlags` | pending | pending | pending |
| `imgui.Icon` | pending | pending | pending |
| `imgui.Icons` | pending | pending | pending |
| `imgui.Image` | pending | pending | pending |
| `imgui.ImageButton` | pending | pending | pending |
| `imgui.Indent` | pending | pending | pending |
| `imgui.InputFloat` | pending | pending | pending |
| `imgui.InputFloat2` | pending | pending | pending |
| `imgui.InputFloat3` | pending | pending | pending |
| `imgui.InputFloat4` | pending | pending | pending |
| `imgui.InputInt` | pending | pending | pending |
| `imgui.InputInt2` | pending | pending | pending |
| `imgui.InputInt3` | pending | pending | pending |
| `imgui.InputInt4` | pending | pending | pending |
| `imgui.InputText` | pending | pending | pending |
| `imgui.InputTextFlags` | pending | pending | pending |
| `imgui.InputTextMultiline` | pending | pending | pending |
| `imgui.InvisibleButton` | pending | pending | pending |
| `imgui.IsAnyItemActive` | pending | pending | pending |
| `imgui.IsAnyItemHovered` | pending | pending | pending |
| `imgui.IsItemActivated` | pending | pending | pending |
| `imgui.IsItemActive` | pending | pending | pending |
| `imgui.IsItemClicked` | pending | pending | pending |
| `imgui.IsItemDeactivated` | pending | pending | pending |
| `imgui.IsItemDeactivatedAfterEdit` | pending | pending | pending |
| `imgui.IsItemEdited` | pending | pending | pending |
| `imgui.IsItemFocused` | pending | pending | pending |
| `imgui.IsItemHovered` | pending | pending | pending |
| `imgui.IsItemVisible` | pending | pending | pending |
| `imgui.IsMouseClicked` | pending | pending | pending |
| `imgui.IsMouseDoubleClicked` | pending | pending | pending |
| `imgui.IsMouseDown` | pending | pending | pending |
| `imgui.IsMouseDragging` | pending | pending | pending |
| `imgui.IsMouseReleased` | pending | pending | pending |
| `imgui.IsPopupOpen` | pending | pending | pending |
| `imgui.IsWindowAppearing` | pending | pending | pending |
| `imgui.IsWindowCollapsed` | pending | pending | pending |
| `imgui.IsWindowFocused` | pending | pending | pending |
| `imgui.IsWindowHovered` | pending | pending | pending |
| `imgui.Key` | pending | pending | pending |
| `imgui.LabelText` | pending | pending | pending |
| `imgui.Launch` | pending | pending | pending |
| `imgui.LaunchFloaty` | pending | pending | pending |
| `imgui.LaunchPrivileged` | pending | pending | pending |
| `imgui.Limits` | pending | pending | pending |
| `imgui.ListBox` | pending | pending | pending |
| `imgui.ListClipper` | pending | pending | pending |
| `imgui.ListExamples` | pending | pending | pending |
| `imgui.LogLevel` | pending | pending | pending |
| `imgui.LogWindow` | pending | pending | pending |
| `imgui.MenuItem` | pending | pending | pending |
| `imgui.MouseButton` | pending | pending | pending |
| `imgui.MultiChoice` | pending | pending | pending |
| `imgui.NewLine` | pending | pending | pending |
| `imgui.OpenPopup` | pending | pending | pending |
| `imgui.PlotHistogram` | pending | pending | pending |
| `imgui.PlotLines` | pending | pending | pending |
| `imgui.PopFont` | pending | pending | pending |
| `imgui.PopID` | pending | pending | pending |
| `imgui.PopItemWidth` | pending | pending | pending |
| `imgui.PopStyleColor` | pending | pending | pending |
| `imgui.PopStyleVar` | pending | pending | pending |
| `imgui.PopupFlags` | pending | pending | pending |
| `imgui.ProgressBar` | pending | pending | pending |
| `imgui.PushFont` | pending | pending | pending |
| `imgui.PushID` | pending | pending | pending |
| `imgui.PushItemWidth` | pending | pending | pending |
| `imgui.PushStyleColor` | pending | pending | pending |
| `imgui.PushStyleVar` | pending | pending | pending |
| `imgui.Px` | pending | pending | pending |
| `imgui.RadioButton` | pending | pending | pending |
| `imgui.Ref` | pending | pending | pending |
| `imgui.ResetMouseDragDelta` | pending | pending | pending |
| `imgui.RunExample` | pending | pending | pending |
| `imgui.SameLine` | pending | pending | pending |
| `imgui.Selectable` | pending | pending | pending |
| `imgui.SelectableFlags` | pending | pending | pending |
| `imgui.Separator` | pending | pending | pending |
| `imgui.SeparatorText` | pending | pending | pending |
| `imgui.SetCursorPos` | pending | pending | pending |
| `imgui.SetGlobalAlpha` | pending | pending | pending |
| `imgui.SetItemTooltip` | pending | pending | pending |
| `imgui.SetKeyboardFocusHere` | pending | pending | pending |
| `imgui.SetMoreTouch` | pending | pending | pending |
| `imgui.SetNextItemWidth` | pending | pending | pending |
| `imgui.SetNextWindowBgAlpha` | pending | pending | pending |
| `imgui.SetNextWindowPos` | pending | pending | pending |
| `imgui.SetNextWindowSize` | pending | pending | pending |
| `imgui.SetNextWindowSizeConstraints` | pending | pending | pending |
| `imgui.SetScrollHereX` | pending | pending | pending |
| `imgui.SetScrollHereY` | pending | pending | pending |
| `imgui.SetScrollX` | pending | pending | pending |
| `imgui.SetScrollY` | pending | pending | pending |
| `imgui.SetSecure` | pending | pending | pending |
| `imgui.SetStyleColor` | pending | pending | pending |
| `imgui.SetStyleProperties` | pending | pending | pending |
| `imgui.SetTheme` | pending | pending | pending |
| `imgui.SetTooltip` | pending | pending | pending |
| `imgui.SetTouchable` | pending | pending | pending |
| `imgui.SetWindowFontScale` | pending | pending | pending |
| `imgui.ShowAboutWindow` | pending | pending | pending |
| `imgui.ShowDebugLogWindow` | pending | pending | pending |
| `imgui.ShowDemoWindow` | pending | pending | pending |
| `imgui.ShowIDStackToolWindow` | pending | pending | pending |
| `imgui.ShowMetricsWindow` | pending | pending | pending |
| `imgui.ShowStyleEditor` | pending | pending | pending |
| `imgui.ShowUserGuide` | pending | pending | pending |
| `imgui.SingleChoice` | pending | pending | pending |
| `imgui.SliderAngle` | pending | pending | pending |
| `imgui.SliderFlags` | pending | pending | pending |
| `imgui.SliderFloat` | pending | pending | pending |
| `imgui.SliderFloat2` | pending | pending | pending |
| `imgui.SliderFloat3` | pending | pending | pending |
| `imgui.SliderFloat4` | pending | pending | pending |
| `imgui.SliderInt` | pending | pending | pending |
| `imgui.SliderInt2` | pending | pending | pending |
| `imgui.SliderInt3` | pending | pending | pending |
| `imgui.SliderInt4` | pending | pending | pending |
| `imgui.SmallButton` | pending | pending | pending |
| `imgui.SortDirection` | pending | pending | pending |
| `imgui.Spacing` | pending | pending | pending |
| `imgui.StatsOverlay` | pending | pending | pending |
| `imgui.StyleColorsClassic` | pending | pending | pending |
| `imgui.StyleColorsDark` | pending | pending | pending |
| `imgui.StyleColorsLight` | pending | pending | pending |
| `imgui.StyleVar` | pending | pending | pending |
| `imgui.TabBarFlags` | pending | pending | pending |
| `imgui.TabItemFlags` | pending | pending | pending |
| `imgui.TableBgTarget` | pending | pending | pending |
| `imgui.TableColumnFlags` | pending | pending | pending |
| `imgui.TableFlags` | pending | pending | pending |
| `imgui.TableGetSortSpecs` | pending | pending | pending |
| `imgui.TableHeadersRow` | pending | pending | pending |
| `imgui.TableNextColumn` | pending | pending | pending |
| `imgui.TableNextRow` | pending | pending | pending |
| `imgui.TableRowFlags` | pending | pending | pending |
| `imgui.TableSetBgColor` | pending | pending | pending |
| `imgui.TableSetColumnIndex` | pending | pending | pending |
| `imgui.TableSetupColumn` | pending | pending | pending |
| `imgui.TableSetupScrollFreeze` | pending | pending | pending |
| `imgui.Text` | pending | pending | pending |
| `imgui.TextColored` | pending | pending | pending |
| `imgui.TextDisabled` | pending | pending | pending |
| `imgui.TextWrapped` | pending | pending | pending |
| `imgui.TextureFlags` | pending | pending | pending |
| `imgui.Themes` | pending | pending | pending |
| `imgui.Toast` | pending | pending | pending |
| `imgui.ToastPosition` | pending | pending | pending |
| `imgui.Toggle` | pending | pending | pending |
| `imgui.ToggleSwitch` | pending | pending | pending |
| `imgui.TouchMode` | pending | pending | pending |
| `imgui.TreeNode` | pending | pending | pending |
| `imgui.TreeNodeEx` | pending | pending | pending |
| `imgui.TreeNodeFlags` | pending | pending | pending |
| `imgui.TreePop` | pending | pending | pending |
| `imgui.Unindent` | pending | pending | pending |
| `imgui.VSliderFloat` | pending | pending | pending |
| `imgui.VSliderInt` | pending | pending | pending |
| `imgui.WantCaptureMouse` | pending | pending | pending |
| `imgui.WindowFlags` | pending | pending | pending |
| `imgui.WindowMode` | pending | pending | pending |
| `imgui.WithFont` | pending | pending | pending |
| `imgui.__pluginId` | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) | not-portable: rhino-only (internal-exports) |
| `imgui.addButtonEx` | pending | pending | pending |
| `imgui.addInputCharacters` | pending | pending | pending |
| `imgui.addKeyEvent` | pending | pending | pending |
| `imgui.addMouseWheel` | pending | pending | pending |
| `imgui.alert` | pending | pending | pending |
| `imgui.alignTextToFramePadding` | pending | pending | pending |
| `imgui.arrowButton` | pending | pending | pending |
| `imgui.begin` | pending | pending | pending |
| `imgui.beginChild` | pending | pending | pending |
| `imgui.beginCombo` | pending | pending | pending |
| `imgui.beginDisabled` | pending | pending | pending |
| `imgui.beginGroup` | pending | pending | pending |
| `imgui.beginItemTooltip` | pending | pending | pending |
| `imgui.beginListBox` | pending | pending | pending |
| `imgui.beginMenu` | pending | pending | pending |
| `imgui.beginMenuBar` | pending | pending | pending |
| `imgui.beginPopup` | pending | pending | pending |
| `imgui.beginPopupContextItem` | pending | pending | pending |
| `imgui.beginPopupContextWindow` | pending | pending | pending |
| `imgui.beginPopupModal` | pending | pending | pending |
| `imgui.beginTabBar` | pending | pending | pending |
| `imgui.beginTabItem` | pending | pending | pending |
| `imgui.beginTable` | pending | pending | pending |
| `imgui.beginTooltip` | pending | pending | pending |
| `imgui.bool` | pending | pending | pending |
| `imgui.bullet` | pending | pending | pending |
| `imgui.bulletText` | pending | pending | pending |
| `imgui.button` | pending | pending | pending |
| `imgui.bytes` | pending | pending | pending |
| `imgui.calcTextSize` | pending | pending | pending |
| `imgui.checkbox` | pending | pending | pending |
| `imgui.closeCurrentPopup` | pending | pending | pending |
| `imgui.closeWindow` | pending | pending | pending |
| `imgui.collapseWindow` | pending | pending | pending |
| `imgui.collapsingHeader` | pending | pending | pending |
| `imgui.colorButton` | pending | pending | pending |
| `imgui.colorEdit3` | pending | pending | pending |
| `imgui.colorEdit4` | pending | pending | pending |
| `imgui.colorPicker3` | pending | pending | pending |
| `imgui.colorPicker4` | pending | pending | pending |
| `imgui.colorU32` | pending | pending | pending |
| `imgui.combo` | pending | pending | pending |
| `imgui.confirm` | pending | pending | pending |
| `imgui.createTexture` | pending | pending | pending |
| `imgui.createWindow` | pending | pending | pending |
| `imgui.dataTable` | pending | pending | pending |
| `imgui.density` | pending | pending | pending |
| `imgui.destroyTexture` | pending | pending | pending |
| `imgui.dp` | pending | pending | pending |
| `imgui.dragFloat` | pending | pending | pending |
| `imgui.dragFloat2` | pending | pending | pending |
| `imgui.dragFloat3` | pending | pending | pending |
| `imgui.dragFloat4` | pending | pending | pending |
| `imgui.dragFloatRange2` | pending | pending | pending |
| `imgui.dragInt` | pending | pending | pending |
| `imgui.dragInt2` | pending | pending | pending |
| `imgui.dragInt3` | pending | pending | pending |
| `imgui.dragInt4` | pending | pending | pending |
| `imgui.dragIntRange2` | pending | pending | pending |
| `imgui.dummy` | pending | pending | pending |
| `imgui.end` | pending | pending | pending |
| `imgui.endChild` | pending | pending | pending |
| `imgui.endCombo` | pending | pending | pending |
| `imgui.endDisabled` | pending | pending | pending |
| `imgui.endGroup` | pending | pending | pending |
| `imgui.endListBox` | pending | pending | pending |
| `imgui.endMenu` | pending | pending | pending |
| `imgui.endMenuBar` | pending | pending | pending |
| `imgui.endPopup` | pending | pending | pending |
| `imgui.endTabBar` | pending | pending | pending |
| `imgui.endTabItem` | pending | pending | pending |
| `imgui.endTable` | pending | pending | pending |
| `imgui.endTooltip` | pending | pending | pending |
| `imgui.expandWindow` | pending | pending | pending |
| `imgui.float` | pending | pending | pending |
| `imgui.floats` | pending | pending | pending |
| `imgui.framerate` | pending | pending | pending |
| `imgui.getBackendCapabilities` | pending | pending | pending |
| `imgui.getBackgroundDrawList` | pending | pending | pending |
| `imgui.getComponent` | pending | pending | pending |
| `imgui.getContentRegionAvail` | pending | pending | pending |
| `imgui.getCursorPos` | pending | pending | pending |
| `imgui.getExampleSource` | pending | pending | pending |
| `imgui.getForegroundDrawList` | pending | pending | pending |
| `imgui.getFrameHeight` | pending | pending | pending |
| `imgui.getFrameHeightWithSpacing` | pending | pending | pending |
| `imgui.getMouseDragDelta` | pending | pending | pending |
| `imgui.getMousePos` | pending | pending | pending |
| `imgui.getScrollMaxX` | pending | pending | pending |
| `imgui.getScrollMaxY` | pending | pending | pending |
| `imgui.getScrollX` | pending | pending | pending |
| `imgui.getScrollY` | pending | pending | pending |
| `imgui.getTextLineHeight` | pending | pending | pending |
| `imgui.getTextLineHeightWithSpacing` | pending | pending | pending |
| `imgui.getWindowDrawList` | pending | pending | pending |
| `imgui.getWindowPos` | pending | pending | pending |
| `imgui.getWindowSize` | pending | pending | pending |
| `imgui.icon` | pending | pending | pending |
| `imgui.image` | pending | pending | pending |
| `imgui.imageButton` | pending | pending | pending |
| `imgui.indent` | pending | pending | pending |
| `imgui.inputFloat` | pending | pending | pending |
| `imgui.inputFloat2` | pending | pending | pending |
| `imgui.inputFloat3` | pending | pending | pending |
| `imgui.inputFloat4` | pending | pending | pending |
| `imgui.inputInt` | pending | pending | pending |
| `imgui.inputInt2` | pending | pending | pending |
| `imgui.inputInt3` | pending | pending | pending |
| `imgui.inputInt4` | pending | pending | pending |
| `imgui.inputText` | pending | pending | pending |
| `imgui.inputTextMultiline` | pending | pending | pending |
| `imgui.int` | pending | pending | pending |
| `imgui.ints` | pending | pending | pending |
| `imgui.invisibleButton` | pending | pending | pending |
| `imgui.isAnyItemActive` | pending | pending | pending |
| `imgui.isAnyItemHovered` | pending | pending | pending |
| `imgui.isItemActivated` | pending | pending | pending |
| `imgui.isItemActive` | pending | pending | pending |
| `imgui.isItemClicked` | pending | pending | pending |
| `imgui.isItemDeactivated` | pending | pending | pending |
| `imgui.isItemDeactivatedAfterEdit` | pending | pending | pending |
| `imgui.isItemEdited` | pending | pending | pending |
| `imgui.isItemFocused` | pending | pending | pending |
| `imgui.isItemHovered` | pending | pending | pending |
| `imgui.isItemVisible` | pending | pending | pending |
| `imgui.isMouseClicked` | pending | pending | pending |
| `imgui.isMouseDoubleClicked` | pending | pending | pending |
| `imgui.isMouseDown` | pending | pending | pending |
| `imgui.isMouseDragging` | pending | pending | pending |
| `imgui.isMouseReleased` | pending | pending | pending |
| `imgui.isPopupOpen` | pending | pending | pending |
| `imgui.isWindowAppearing` | pending | pending | pending |
| `imgui.isWindowCollapsed` | pending | pending | pending |
| `imgui.isWindowFocused` | pending | pending | pending |
| `imgui.isWindowHovered` | pending | pending | pending |
| `imgui.labelText` | pending | pending | pending |
| `imgui.launch` | pending | pending | pending |
| `imgui.launchFloaty` | pending | pending | pending |
| `imgui.launchPrivileged` | pending | pending | pending |
| `imgui.listBox` | pending | pending | pending |
| `imgui.listClipper` | pending | pending | pending |
| `imgui.listExamples` | pending | pending | pending |
| `imgui.logWindow` | pending | pending | pending |
| `imgui.menuItem` | pending | pending | pending |
| `imgui.multiChoice` | pending | pending | pending |
| `imgui.newLine` | pending | pending | pending |
| `imgui.openPopup` | pending | pending | pending |
| `imgui.plotHistogram` | pending | pending | pending |
| `imgui.plotLines` | pending | pending | pending |
| `imgui.popFont` | pending | pending | pending |
| `imgui.popId` | pending | pending | pending |
| `imgui.popItemWidth` | pending | pending | pending |
| `imgui.popStyleColor` | pending | pending | pending |
| `imgui.popStyleVar` | pending | pending | pending |
| `imgui.progressBar` | pending | pending | pending |
| `imgui.pushFont` | pending | pending | pending |
| `imgui.pushId` | pending | pending | pending |
| `imgui.pushItemWidth` | pending | pending | pending |
| `imgui.pushStyleColor` | pending | pending | pending |
| `imgui.pushStyleVar` | pending | pending | pending |
| `imgui.px` | pending | pending | pending |
| `imgui.radioButton` | pending | pending | pending |
| `imgui.ref` | pending | pending | pending |
| `imgui.resetMouseDragDelta` | pending | pending | pending |
| `imgui.runExample` | pending | pending | pending |
| `imgui.sameLine` | pending | pending | pending |
| `imgui.selectable` | pending | pending | pending |
| `imgui.sendKey` | pending | pending | pending |
| `imgui.separator` | pending | pending | pending |
| `imgui.separatorText` | pending | pending | pending |
| `imgui.setCursorPos` | pending | pending | pending |
| `imgui.setGlobalAlpha` | pending | pending | pending |
| `imgui.setItemTooltip` | pending | pending | pending |
| `imgui.setKeyboardFocusHere` | pending | pending | pending |
| `imgui.setMoreTouch` | pending | pending | pending |
| `imgui.setNextItemWidth` | pending | pending | pending |
| `imgui.setNextWindowBgAlpha` | pending | pending | pending |
| `imgui.setNextWindowPos` | pending | pending | pending |
| `imgui.setNextWindowSize` | pending | pending | pending |
| `imgui.setNextWindowSizeConstraints` | pending | pending | pending |
| `imgui.setScrollHereX` | pending | pending | pending |
| `imgui.setScrollHereY` | pending | pending | pending |
| `imgui.setScrollX` | pending | pending | pending |
| `imgui.setScrollY` | pending | pending | pending |
| `imgui.setSecure` | pending | pending | pending |
| `imgui.setStyleColor` | pending | pending | pending |
| `imgui.setStyleProperties` | pending | pending | pending |
| `imgui.setTheme` | pending | pending | pending |
| `imgui.setTooltip` | pending | pending | pending |
| `imgui.setTouchable` | pending | pending | pending |
| `imgui.setWindowFontScale` | pending | pending | pending |
| `imgui.showAboutWindow` | pending | pending | pending |
| `imgui.showDebugLogWindow` | pending | pending | pending |
| `imgui.showDemoWindow` | pending | pending | pending |
| `imgui.showIdStackToolWindow` | pending | pending | pending |
| `imgui.showMetricsWindow` | pending | pending | pending |
| `imgui.showStyleEditor` | pending | pending | pending |
| `imgui.showUserGuide` | pending | pending | pending |
| `imgui.singleChoice` | pending | pending | pending |
| `imgui.sliderAngle` | pending | pending | pending |
| `imgui.sliderFloat` | pending | pending | pending |
| `imgui.sliderFloat2` | pending | pending | pending |
| `imgui.sliderFloat3` | pending | pending | pending |
| `imgui.sliderFloat4` | pending | pending | pending |
| `imgui.sliderInt` | pending | pending | pending |
| `imgui.sliderInt2` | pending | pending | pending |
| `imgui.sliderInt3` | pending | pending | pending |
| `imgui.sliderInt4` | pending | pending | pending |
| `imgui.smallButton` | pending | pending | pending |
| `imgui.spacing` | pending | pending | pending |
| `imgui.statsOverlay` | pending | pending | pending |
| `imgui.string` | pending | pending | pending |
| `imgui.strings` | pending | pending | pending |
| `imgui.styleColorsClassic` | pending | pending | pending |
| `imgui.styleColorsDark` | pending | pending | pending |
| `imgui.styleColorsLight` | pending | pending | pending |
| `imgui.tableGetSortSpecs` | pending | pending | pending |
| `imgui.tableHeadersRow` | pending | pending | pending |
| `imgui.tableNextColumn` | pending | pending | pending |
| `imgui.tableNextRow` | pending | pending | pending |
| `imgui.tableSetBgColor` | pending | pending | pending |
| `imgui.tableSetColumnIndex` | pending | pending | pending |
| `imgui.tableSetupColumn` | pending | pending | pending |
| `imgui.tableSetupScrollFreeze` | pending | pending | pending |
| `imgui.text` | pending | pending | pending |
| `imgui.textColored` | pending | pending | pending |
| `imgui.textDisabled` | pending | pending | pending |
| `imgui.textWrapped` | pending | pending | pending |
| `imgui.toast` | pending | pending | pending |
| `imgui.toggle` | pending | pending | pending |
| `imgui.toggleSwitch` | pending | pending | pending |
| `imgui.treeNode` | pending | pending | pending |
| `imgui.treeNodeEx` | pending | pending | pending |
| `imgui.treePop` | pending | pending | pending |
| `imgui.unindent` | pending | pending | pending |
| `imgui.vSliderFloat` | pending | pending | pending |
| `imgui.vSliderInt` | pending | pending | pending |
| `imgui.version` | pending | pending | pending |
| `imgui.wantCaptureMouse` | pending | pending | pending |
| `imgui.wantTextInput` | pending | pending | pending |
| `imgui.withChild` | pending | pending | pending |
| `imgui.withCombo` | pending | pending | pending |
| `imgui.withContextItem` | pending | pending | pending |
| `imgui.withContextWindow` | pending | pending | pending |
| `imgui.withDisabled` | pending | pending | pending |
| `imgui.withFont` | pending | pending | pending |
| `imgui.withGroup` | pending | pending | pending |
| `imgui.withId` | pending | pending | pending |
| `imgui.withItemTooltip` | pending | pending | pending |
| `imgui.withListBox` | pending | pending | pending |
| `imgui.withMenu` | pending | pending | pending |
| `imgui.withMenuBar` | pending | pending | pending |
| `imgui.withPopup` | pending | pending | pending |
| `imgui.withPopupModal` | pending | pending | pending |
| `imgui.withTabBar` | pending | pending | pending |
| `imgui.withTabItem` | pending | pending | pending |
| `imgui.withTable` | pending | pending | pending |
| `imgui.withTooltip` | pending | pending | pending |
| `imgui.withTreeNode` | pending | pending | pending |
| `imgui.withWindow` | pending | pending | pending |

### keys

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `keys.BACK` | bridged | pending | local-shim |
| `keys.HOME` | bridged | pending | local-shim |
| `keys.MENU` | bridged | pending | local-shim |
| `keys.VOLUME_DOWN` | bridged | pending | local-shim |
| `keys.VOLUME_UP` | bridged | pending | local-shim |
| `keys.back` | bridged | pending | local-shim |
| `keys.home` | bridged | pending | local-shim |
| `keys.menu` | bridged | pending | local-shim |
| `keys.volumeDown` | bridged | pending | local-shim |
| `keys.volumeUp` | bridged | pending | local-shim |
| `keys.volume_down` | bridged | pending | local-shim |
| `keys.volume_up` | bridged | pending | local-shim |

### memory

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `memory.watch` | not-portable: runtime-native (native-memory-watch) | not-portable: runtime-native (native-memory-watch) | not-portable: runtime-native (native-memory-watch) |

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

### plugins

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `plugins.load` | local-shim | not-portable: rhino-only (rhino-engine) | not-portable: rhino-only (rhino-engine) |

### qrcode

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `qrcode.detect` | bridged | pending | pending |
| `qrcode.detectAll` | bridged | pending | pending |
| `qrcode.recognizeText` | bridged | pending | pending |
| `qrcode.recognizeTexts` | bridged | pending | pending |

### speech

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `speech.detectLanguage` | pending | pending | pending |
| `speech.detectLanguageAsync` | pending | pending | pending |
| `speech.diarize` | pending | pending | pending |
| `speech.diarizeAsync` | pending | pending | pending |
| `speech.tag` | pending | pending | pending |
| `speech.tagAsync` | pending | pending | pending |

### speech.speaker

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `speech.speaker.enroll` | pending | pending | pending |
| `speech.speaker.enrollAsync` | pending | pending | pending |
| `speech.speaker.identify` | pending | pending | pending |
| `speech.speaker.identifyAsync` | pending | pending | pending |
| `speech.speaker.list` | pending | pending | pending |
| `speech.speaker.remove` | pending | pending | pending |
| `speech.speaker.verify` | pending | pending | pending |
| `speech.speaker.verifyAsync` | pending | pending | pending |

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
| `threads.atomic` | pending | pending | pending |
| `threads.currentThread` | pending | pending | pending |
| `threads.disposable` | pending | pending | pending |
| `threads.interrupt` | pending | pending | pending |
| `threads.lock` | pending | pending | pending |
| `threads.pool` | pending | pending | pending |
| `threads.shutDownAll` | pending | pending | pending |
| `threads.start` | pending | pending | pending |

### timers

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `timers.clearImmediate` | not-portable: runtime-native | not-portable: runtime-native | pending |
| `timers.clearInterval` | not-portable: runtime-native | not-portable: runtime-native | pending |
| `timers.clearTimeout` | not-portable: runtime-native | not-portable: runtime-native | pending |
| `timers.keepAlive` | pending | pending | pending |
| `timers.setImmediate` | not-portable: runtime-native | not-portable: runtime-native | pending |
| `timers.setInterval` | not-portable: runtime-native | not-portable: runtime-native | pending |
| `timers.setIntervalExt` | pending | pending | pending |
| `timers.setTimeout` | not-portable: runtime-native | not-portable: runtime-native | pending |

### toast

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `toast.dismissAll` | pending | pending | capability-gated |

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
| `ui.bindingContext` | pending | pending | pending |
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
| `ui.layoutInflater` | pending | pending | pending |
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
| `util.class` | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) |
| `util.className` | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) |
| `util.getClassName` | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) | not-portable: java-prototype (java-introspection) |
| `util.toRegularAndApply` | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) |
| `util.toRegularAndCall` | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) | not-portable: obsoleted (obsoleted-functions) |
| `util.unwrapJavaObject` | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) | not-portable: java-prototype (java-prototype) |

### web

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `web.ByteString` | pending | pending | pending |
| `web.newInjectableWebClient` | pending | pending | pending |
| `web.newInjectableWebView` | pending | pending | pending |
| `web.newWebSocket` | pending | pending | pending |

### yolo

| 行 id | Node main | Node Worker | Bun |
|---|---|---|---|
| `yolo.load` | pending | pending | pending |
