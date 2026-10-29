# Node.js JS tools family: Rhino comparison and device validation

Date: 2026-10-29 (Asia/Taipei). Batch: api-coverage P3.2a (util, species, global data functions, s13n, cvt / fmt, jsox).

## Runtime behavior

The Node.js Runtime plugin (748ba995) ports the host modules util (util.inspect / util.format from the host Inspector, util.java, util.morseCode, util.version, util.versionCodes), species, the global data functions, s13n, the bytes converters of cvt / fmt and jsox (Mathx / Arrayx / Numberx) into one self-contained section of `node_bridge_sources.cpp` ("Shared JS tools core"). The main thread and Workers (CJS, ESM, nested) use the same section; the Bun runtime syncs it (P4.2a). Argument counts follow `ScriptArgs.count` (trailing undefined beyond the required count is omitted), conversions and error classes follow the host modules.

- Entries: `autojs6:util` (bare `util` stays `node:util`), `species` / `autojs6:species`, `autojs6:global` (the main thread also has the `axios` getter), `s13n`, `fmt` / `cvt`, `jsox` and `jsox.mathx` / `jsox.arrayx` / `jsox.numberx`; `$autojs.util` / `species` / `global` / `jsox` / `s13n` / `fmt` / `cvt` on the main thread.
- The previous Node jsox / bytes implementations differed from Rhino in 49 of 146 cases (empty max / min, union / intersect, Numberx.check, padStart, parsePercent, strict units, error classes). They are replaced.
- `jsox.extend` / `extendAll` install the members on the current thread's Math, Array.prototype, Number.prototype and Number, as Rhino does (Q59).
- `util.dpToPx` / `spToPx` / `pxToDp` / `pxToSp` and `util.version.sdkInt` are synchronous and read the plugin process's display snapshot, which now carries `scaledDensity`; sp conversion is linear (Q60).
- `util.java.*`, `s13n.point` / `s13n.throwable` and `util.morseCode.vibrate` run through the Java facade (`java_interop`) and return Promises.
- `dayjs` / `pangu` / `cheerio` / `i18n` and the Worker's `axios` have no entry yet (Q61).

Known differences that remain: Java handles are inspected as plain objects (Rhino prints the Java `toString`), `util.toRegularAndCall` / `toRegularAndApply` throw `ObsoletedRhinoFunctionException` where Rhino wraps it as `JavaException`, and `species.isJavaPackage` is always false (Node has no Java package objects; the two cases are skipped).

## Rhino values and cases

`tools/nodejs/api/js-tools/cases.js` holds 523 cases (util 183, util.java 22, util.morseCode 17, util.versionCodes 17, util.inspect 2, util.version 1, species 85, global 27, Mathx 61, Arrayx 28, Numberx 32, cvt 11, fmt 7, s13n 25, jsox 4, probe 1). `device_js_tools.py rhino` ran them in Rhino on the device below through RunIntentActivity; `rhino-expected.json` records the values (81 cases throw).

Desktop comparison (`node --test tools/nodejs/api/test-js-tools.js`, CI step added to the plugin's build workflow): the 523 cases minus the device-only ones (Java facade, global libraries) on the main thread and in a nested Worker started with the real Worker preload, plus the four jsox.extend cases; 3 tests passed. A cross-check with the P4.2a cases recorded on Xiaomi 23046RP50C (API 35): util 387 of 389 (the two misses are the JavaException name above), jsox all except cases that call bare `randomInt` / `randomFloat` globals, which that harness does not provide.

## Verification

Device: Redmi 22120RN86C, serial `bek749scrwv4wo8h`, Android 13 / API 33, arm64-v8a.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935), base.apk md5 `6624bd371b1b64c1d9189f4c8a5397c9` | unchanged | unchanged |
| Node.js Runtime plugin | 1.31.0 (291), md5 `a8811d17a2d96bd7cb2c4bb0a103e9a1` | 1.32.0 (292) debug arm64 from 748ba995, APK md5 `63237573fd62ddd03f9438cee750f928` | 1.31.0 (291) reinstalled from `app/releases/1.31.0`, md5 `a8811d17a2d96bd7cb2c4bb0a103e9a1` verified |
| Node.js Runtime test package | not installed | debug androidTest, md5 `2cd421490b860e465af4f41e0359ef00` | uninstalled |
| Bun runtime plugin | 0.20.0 (293), md5 `bbfca1f67936a60a0700ec1a12e216eb` | unchanged | unchanged |

`device_js_tools.py verify` against the recorded state reported "same as recorded". The pushed scripts (`/sdcard/Download/aj6-p32a`) were removed.

| Check | Result |
|---|---|
| Node main thread (CJS `require`) | 512 passed, 9 failed, 2 skipped; 30 results were Promises (Java facade) |
| Node ESM (`import` of the same entries) | 512 passed, 9 failed, 2 skipped |
| Nested Worker (Worker started by a Worker, CJS) | 511 passed, 10 failed, 2 skipped |
| NpmEcosystemSmokeTest and NodeRuntimePluginAndroidConformanceTest | 34 passed (118 s) |

The 9 failures on the main thread and in ESM are the Q61 entries: `global.dayjs` (4 cases), `global.pangu` (1), `global.cheerio` (2), `global.i18n` (2). The Worker adds `global.axios` (Q61). The runs declare `node.permissions: ["java_interop"]` in a project.json next to the script.

Two problems were found and fixed on the device before these runs: the facade returns Java arrays from method calls as script arrays (only `newArray` returns a handle), so multidimensional `util.java.array` is built row by row with `newArray` and `toJsArray` / `mapToObject` read the returned arrays; and the Node stdout relay can split one console line into several GlobalConsole entries, so the collector joins lines up to an end mark.

## Repeating the checks

```powershell
node --test tools/nodejs/api/test-js-tools.js
py tools/nodejs/api/js-tools/device_js_tools.py state -s <serial> --state state.json
py tools/nodejs/api/js-tools/device_js_tools.py node -s <serial> --report report.json
py tools/nodejs/api/js-tools/device_js_tools.py verify -s <serial> --state state.json
node tools/api-coverage/check-runtime-js-tools.mjs
```

The first four run in the Node.js Runtime plugin repository with the plugin debug build installed; the last runs in this repository.
