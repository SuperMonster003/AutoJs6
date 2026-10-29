# Bun JS tools family: Rhino comparison and device validation

Date: 2026-10-29. Batch: api-coverage P4.2a (the Bun column of util, species, the global data functions, s13n, cvt / fmt, jsox and the pinyin constants).

## Runtime behavior

The Bun host client (Bun Runtime 976312d) carries the Node.js Runtime's shared JS tools core (748ba995, see `runtime-js-tools-2026-10-29.md`) unchanged: `tools/host-client/sync-js-tools.mjs` copies the "Shared JS tools core" section of `node_bridge_sources.cpp` and the matching Node declarations into the GENERATED JS TOOLS sections of `host-client.mjs` / `host-client.d.mts`, and `--check` fails when they drift. The tools run in the client, synchronously and without bridge calls or grants, on the main thread and in every Worker (each imports its own client).

- Entries: `host.util`, `host.species`, `host.global` (the global data functions `random`, `randomInt`, `randomFloat`, `isInteger`, `isPrimitive`, `isObject`, `isReference`, `isBigInt`, `isEmptyObject`, `isObjectSpecies`), `host.s13n`, `host.cvt` / `host.fmt` (converter / formatter), `host.jsox` with `mathx` / `arrayx` / `numberx` (Mathx / Arrayx / Numberx). Nothing is injected as a Bun global.
- `util.java.*`, `s13n.point` / `s13n.throwable` and `util.morseCode.vibrate` go through `host.java`, so they need the default-off `java.interop` grant and return Promises; argument errors are thrown before the host is called, as in Rhino.
- `util.dpToPx` / `spToPx` / `pxToDp` / `pxToSp` and `util.version.sdkInt` read `device-info.json`, which `BunRuntimeService` writes next to the client from the display metrics (density, densityDpi, scaledDensity) and the SDK level at the start of each execution; sp conversion is linear (Q60).
- `host.jsox.extend` / `extendAll` change the built-ins of the current thread, as Rhino does (Q59).
- `host.pinyin` carries the ten STYLE_* / MODE_* constants with the numbers of the Node runtime's pinyin module (both convert through the host's shared pinyin bridge).
- Not provided yet: the library getters of the Rhino global object, `axios`, `dayjs`, `pangu`, `cheerio`, `i18n` (Q61 / Q64). The remaining known differences are those of the Node runtime (Q62): Java handles are inspected as plain objects, `util.toRegularAndCall` / `toRegularAndApply` throw `ObsoletedRhinoFunctionException` where Rhino wraps it as `JavaException`, `species.isJavaPackage` is always false.
- The host client exists only when the host offers the bridge, that is with at least one Bun grant on (eight grants are on by default), so the local tools disappear with it when every grant is revoked.

## Rhino values and cases

The cases are the shared ones of the Node runtime (`tools/nodejs/api/js-tools/cases.js`, synchronized into the Bun checkout as `tools/host-client/js-tools/cases.js`): 523 cases, util 183, util.java 22, util.morseCode 17, util.versionCodes 17, util.inspect 2, util.version 1, species 85, global 27, Mathx 61, Arrayx 28, Numberx 32, cvt 11, fmt 7, s13n 25, jsox 4, probe 1. `tools/api-coverage/device_bun_js_tools.py rhino` ran them in Rhino on the device below through RunIntentActivity (81 cases throw); only the display metric and SDK cases differ from the Redmi values the Node batch recorded.

Desktop comparison in the Bun checkout (`node --test tools/host-client/js-tools.test.mjs`, 4 tests): the shared cases without any grant against the recorded Rhino values (device-dependent cases and the library getters skipped; Java cases must reject with NOT_GRANTED or fail the argument check Rhino fails first), the Java facade payloads under `java.interop`, `device-info.json` and the pinyin constants. All 90 Bun host client tests, `types.mts` (tsc 5.1.3 strict) and `BunHostClientModuleTest` pass.

## Verification

Device: Xiaomi 23046RP50C, serial `968e9f18`, Android 15 / API 35, arm64-v8a, time zone Asia/Shanghai.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935) debug, base.apk md5 `1de778e01894e156b113dd3fbf1e7714` | unchanged | unchanged |
| Bun runtime plugin | 0.2.5 (118), base.apk md5 `555d45afcde4c611cb7665909313152b` | 0.21.0 (296) debug arm64 built from the tree committed as 976312d (only `docs/host-api.md` changed after the build), APK md5 `3c4360ceebf1e4907009283d43d3a0b7` | 0.2.5 (118) reinstalled from the recorded copy, md5 `555d45afcde4c611cb7665909313152b` verified |

Host Bun grants: `java.interop` and `text.pinyin` were not stored (defaults: off / on). The runs set them through a host script and the `grant --set restore` phase removed both stored values again. `device_bun_js_tools.py verify` reported both packages "same" as recorded; the pushed scripts (`/sdcard/Download/aj6-bun-js-tools`) were removed after each run. No dialog was answered and nothing was tapped.

| Check | Main script | Worker |
|---|---|---|
| `java.interop` on | 510 passed, 0 failed, 2 skipped, 11 open; 30 results were Promises (Java facade) | 510 passed, 0 failed, 2 skipped, 11 open |
| `java.interop` off (denial path) | 510 passed (the 41 Java facade cases rejected with NOT_GRANTED), 0 failed, 2 skipped, 11 open | same as the main script |

The 2 skipped cases are `species(java.lang)` and `species.isJavaPackage(java.lang)` (no Java package objects in Bun, Q62). The 11 open cases are the library getters: `global.dayjs` (4), `global.pangu` (1), `global.cheerio` (2), `global.i18n` (2), `global.axios` (2) (Q61 / Q64). No result was missing in the last runs.

The host logs Bun stdout as it reads it, so one console line can arrive as several GlobalConsole entries, the later ones with their own time / level prefix. The runner emits results in chunks of at most 1000 characters ending with ` $`, and the collector strips the prefix and joins URI-encoded continuation entries up to that mark.

## Repeating the checks

```powershell
py -3 -I tools/api-coverage/device_bun_js_tools.py record --serial <serial> --state <dir>
py -3 -I tools/api-coverage/device_bun_js_tools.py install --serial <serial> --state <dir> --apk io.github.supermonster003.autojs6.plugin.bun.runtime=<debug apk>
py -3 -I tools/api-coverage/device_bun_js_tools.py rhino --serial <serial> --state <dir>
py -3 -I tools/api-coverage/device_bun_js_tools.py grant --serial <serial> --state <dir> --set on --only java.interop text.pinyin
py -3 -I tools/api-coverage/device_bun_js_tools.py bun --serial <serial> --expected <dir>/rhino-<serial>.json --report granted.json
py -3 -I tools/api-coverage/device_bun_js_tools.py grant --serial <serial> --state <dir> --set off --only java.interop
py -3 -I tools/api-coverage/device_bun_js_tools.py bun --serial <serial> --expected <dir>/rhino-<serial>.json --denied java.interop --report denied.json
py -3 -I tools/api-coverage/device_bun_js_tools.py grant --serial <serial> --state <dir> --set restore --only java.interop text.pinyin
py -3 -I tools/api-coverage/device_bun_js_tools.py restore --serial <serial> --state <dir>
node tools/api-coverage/check-bun-js-tools.mjs
```

The device phases run in this repository with the Bun Runtime checkout next to it (`--bun` overrides the path); `check-bun-js-tools.mjs` needs the `exports.json` of a `check.py` run.
