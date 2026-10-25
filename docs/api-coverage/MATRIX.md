<!-- runtime-matrix: schema=autojs6-runtime-matrix-v1 dataSource=ksp rows=1368 -->
# AutoJs6 脚本 API 运行时覆盖矩阵

由 `tools/api-coverage/runtime_matrix.py` 生成 (`python tools/api-coverage/check.py --matrix`), 不要手改. 定义见 `docs/api-coverage/ROADMAP.md` 第 4 节, 机读版本是 CI 产物 `build/reports/api-coverage/runtime-matrix.json`, 人工标注 (别名映射, 排除理由, 等价写法) 只在 `tools/api-coverage/runtime-policy.json`; 排除规则在 D12 / Q1 拍板前是草案.

数据源: ksp, 1368 行 (显式导出 1368, 返回对象成员 0, exports.json 尚无 returnedObjects 段).

## 状态汇总

| 列 | bridged | local-shim | capability-gated | not-portable | denied | pending |
|---|---:|---:|---:|---:|---:|---:|
| Node main | 616 | 143 | 0 | 54 | 1 | 554 |
| Node Worker | 482 | 62 | 0 | 114 | 1 | 709 |
| Bun | 0 | 62 | 534 | 113 | 1 | 658 |

| 列 | 取值 |
|---|---|
| doc | none 8 / mention 95 / heading 1265 / signature 0 / example 0 |
| dts | none 9 / name 221 / arity 1138 / typed 0 |
| ace | none 9 / semantic 0 / static 1359 |

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
| Arrayx | 15 | 15 | 15 | 15 | 15 / 0 / 0 | 0 / 15 / 0 | 0 / 15 / 0 |
| Mathx | 23 | 21 | 23 | 23 | 23 / 0 / 0 | 0 / 23 / 0 | 0 / 23 / 0 |
| Numberx | 14 | 14 | 14 | 14 | 14 / 0 / 0 | 0 / 14 / 0 | 0 / 14 / 0 |
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
