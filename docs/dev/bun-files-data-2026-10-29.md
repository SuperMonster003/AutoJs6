# Bun files and data family: Rhino comparison and device validation

Date: 2026-10-29. Batch: api-coverage P4.2b (the Bun column of the files and data family and Q64 (b)): the Rhino files prototype members, files / open / toFile, colors / Color, zip / Zip, storages names / all and Storage#size, mime, the timing recorder, mediainfo, EpubBook#toString and the global library getters axios / dayjs / pangu / cheerio / i18n. colors.theme, gallery and files.batchRename wait for appendix C Q80 / Q81 / Q82.

## Runtime behavior

- The Bun host client (Bun Runtime f40ecbe, 91ab205) carries the Node.js Runtime's shared JS tools core of b92e7a99 (P3.2b, see `runtime-files-data-2026-10-29.md`) unchanged through `tools/host-client/sync-js-tools.mjs`: `host.colors`, `host.global.Color`, `host.zip`, `host.mime` and `host.recorder` (without Node's audio members) are created on first use and computed in the client. Members that need Java objects (zip4j, java.io.File, Paint / ColorStateList, float[], the theme color, colors.extract) go through `host.java` and the default-off `java.interop` grant.
- `host.files`: the string helpers (join, getName, getExtension, getNameWithoutExtension, getHumanReadableSize, formatSizeWithUnit, getSdcardPath) are the core's, with Rhino results; the Rhino prototype members (cwd, create, createIfNotExists, createWithDirs, readBytes, writeBytes, appendBytes, rename, renameWithoutExtension, removeDir, isEmptyDir, readAssets, getSimplifiedPath) run in the host through `files.control` (Q85 (a), host 922673186); `open` / `toFile` (and `host.global.open`, the same function) build Rhino handles over the Java facade. `readAssets` takes Java charset names (Bun 91ab205).
- Relative paths of open / toFile / zip resolve against the host's script working directory: with `files.control` the client asks `files.cwd()` once, before the first of those calls; without it they resolve against the Bun working directory, which the host process cannot read. On the device `files.toFile('z/a.txt')` and `zip.zipFile('z/a.txt', 'z/rel.zip')` gave the paths Rhino gave.
- zip: every module function and Zip member returns a Promise, argument errors included (Rhino throws them synchronously). The core asks zip4j work without a time limit (2147483647 ms, Node); a Bun host call lasts at most 120 s, so the client holds longer Java timeouts to 120000 ms (Bun 91ab205; before that every zip call failed on the device with "Automation timeoutMs must be in 1..120000").
- storages / storage: names / all from the per-scope registry of the shared Node facade (Q79 (a)), Storage#size is a method returning a Promise.
- mediainfo (`mediainfo.control`, host 922673186 / Bun 622ea27): Node's module with Rhino's snapshot and the two schema constants.
- The global libraries: axios 1.1.2 is embedded in the client (Q64 (b), Bun ddf607b; requests run on Bun's fetch); dayjs, pangu.js 10.1.0, cheerio and banana-i18n ship in the APK (`assets/autojs6/libraries`), are copied next to the client at the start of an execution and compiled on first read; i18n reads `./i18n` of the Bun working directory. Since host 67ef4c1a5 Rhino gets these libraries from the Bundled Libraries plugin; the Bun client does not need that plugin.
- Known differences besides those of the Node runtime: a Color member whose Java call is refused (no `java.interop`) throws InvocationTargetException with the NOT_GRANTED HostCallError as its cause, as the core wraps Color member failures like Rhino's host does; host call failures are HostCallError where Rhino reports InternalError "Failed to call method ...".

## Rhino values and cases

| Cases | Source | Rhino values |
|---|---|---|
| files prototype members, files paths, zip / Zip, open / toFile / global open, mediainfo (37) | `tools/api-coverage/device_bun_files_data_cases.js` | recorded on the device below (two runs merged; the 22 cases both runs returned were identical) |
| colors / Color 633, mime / recorder 231, storages / mediainfo / global 13 (877) | the Node runtime's `tools/nodejs/api/files-data/cases-{colors,mime-recorder,data}.js` (`--node-groups`) | the Node runtime's `rhino-expected.json` (Xiaomi 23046RP50C, host 5935); 471 of the 877 recorded again on this device were identical, the others were lost on the way to logcat |
| shared JS tools cases, with axios (2) and dayjs / pangu / cheerio / i18n (9) (523) | the Bun checkout's `tools/host-client/js-tools/cases.js` | recorded on the device below |

The Node files and zip groups use the Node runtime's synchronous files and are not run in Bun; the zip, open and toFile cases of the first row replace them. Desktop: `node --test tools/host-client/files-data.test.mjs` (the colors / mime / recorder / global cases without any grant against the Rhino values, the libraries, the directory handling, the zip timeout and the readAssets charsets) and `js-tools.test.mjs` (the library cases included); all 107 Bun host client tests, `types.mts` (tsc strict), the sync checks and `BunHostClientModuleTest` (4) pass.

## Verification

Device: OnePlus OPD2413, serial `63751f63`, ColorOS, Android 15 / API 35, arm64-v8a.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935) debug, base.apk md5 `853467b5b2e0c30a806a810c5919c154` | 6.8.0 (5935) debug arm64 built at the P4.2b host commits (922673186 / 3e87123fd), md5 `12501d918651e7bcbd8b146e5e1c3fa9`, installed over it with the same signature (data kept) | reinstalled from the recorded copy, md5 `853467b5b2e0c30a806a810c5919c154` verified |
| Bun runtime plugin | not installed | 0.21.0 (296) debug arm64: f40ecbe (md5 `4111519f5cae06b6a15ce4b6d694ad35`), then 91ab205 (md5 `db2b3e7d173b0214e3a3779f4ea85fa0`) | uninstalled, verified "not installed" |
| MediaInfo plugin | 1.0.0 (8), md5 `a7be98b6e42d7dba288fb20769ca190f`, off in the Plugin Center | switched on by the maintainer for the run | md5 unchanged |

Host Bun grants: `files.control`, `mediainfo.control`, `java.interop`, `storage.control` and `text.pinyin` were not stored. The runs set them through a host script and `grant --set restore` removed every stored value again. The pushed scripts and fixtures (`/sdcard/Download/aj6-bun-js-tools`) were removed after each run, and the empty `/sdcard/Download/aj6-p32b` left by the Node script's Rhino run was removed. The maintainer confirmed the ColorOS installation dialogs; nothing was tapped by the scripts.

| Check | Main script | Worker |
|---|---|---|
| 37 cases, four grants on (Bun 91ab205) | 37 passed | 37 passed |
| 37 cases, the four grants off | 37 passed (36 reach a denied grant and reject with NOT_GRANTED; `files.sdcardPath` needs none) | 37 passed |
| colors / mime / data 877, grants on (Bun f40ecbe) | 877 passed (838 in the first run, the 39 mime / recorder results lost there passed in a mime-only run) | 877 passed (861 in the first run, whose other 16 hit the call limit of 1024 per execution, shared with the main script; a Worker-only run of colors / data passed them) |
| colors 633 with `java.interop` off | 632 passed (28 reach the denied grant), 1 differs: `Color.toColorStateList` throws InvocationTargetException with the NOT_GRANTED cause | 561 passed, 72 results lost on the way to logcat |
| data 13 with the four grants off | 13 passed (10 reach a denied grant) | 13 passed |
| shared JS tools 523 with `java.interop` / `text.pinyin` on (Bun f40ecbe) | 521 passed, 2 skipped (Java packages, Q62) | 521 passed, 2 skipped |

mediainfo: the MediaInfo plugin installed on this device answers "MediaInfo library is not available" to get / read / snapshot and does not count streams (capabilities: schema v1, no stream counting). Rhino and Bun report the same failures (Rhino InternalError "Failed to call method ...", Bun HostCallError with the host's message, or the client's MEDIAINFO_QUERY_UNSUPPORTED check before the call), and the comparison counts those as equal; the constants and capabilities match. A successful MediaInfo report was therefore not compared on this device.

## ColorOS findings

These are properties of the device, not case failures:

- ColorOS freezes the host and the plugin processes (Bun runtime, MediaInfo, Node) once the host is in the background ("freezing ... org.autojs.autojs6" in the ActivityManager log), so a long Bun run stopped after 14 cases. `device_bun_js_tools.py --foreground` brings the host's launcher activity to the front first; with it every run finished.
- `adb install` waits for the ColorOS installation dialog (four installations, confirmed by the maintainer).
- Bursts of console lines lose entries on the way to logcat more often than on the Xiaomi / Redmi devices; both runners now pause every 20 results and the lost results were completed by reruns.
- One Rhino run of the MediaInfo report cases kept waiting on the plugin (Rhino has no timeout there); the host was force-stopped and the cases moved to the end of the file. The Bun runner reports a case that does not settle in 30 s as CaseTimeout.

## Repeating the checks

```powershell
py -3 -I tools/api-coverage/device_bun_js_tools.py record --serial <serial> --state <dir>
py -3 -I tools/api-coverage/device_bun_js_tools.py install --serial <serial> --state <dir> --apk io.github.supermonster003.autojs6.plugin.bun.runtime=<debug apk>
py -3 -I tools/api-coverage/device_bun_js_tools.py rhino --foreground --serial <serial> --state <dir> --cases tools/api-coverage/device_bun_files_data_cases.js --out <dir>/rhino-files-data.json
py -3 -I tools/api-coverage/device_bun_js_tools.py grant --serial <serial> --state <dir> --set on --only files.control mediainfo.control java.interop storage.control text.pinyin
py -3 -I tools/api-coverage/device_bun_js_tools.py bun --foreground --serial <serial> --state <dir> --cases tools/api-coverage/device_bun_files_data_cases.js --expected <dir>/rhino-files-data.json
py -3 -I tools/api-coverage/device_bun_js_tools.py bun --foreground --serial <serial> --state <dir> --node-groups colors,mime,data --expected ../AutoJs6-Plugin-NodeJs-Runtime/tools/nodejs/api/files-data/rhino-expected.json
py -3 -I tools/api-coverage/device_bun_js_tools.py grant --serial <serial> --state <dir> --set off --only files.control mediainfo.control java.interop storage.control
py -3 -I tools/api-coverage/device_bun_js_tools.py bun --foreground --serial <serial> --state <dir> --cases tools/api-coverage/device_bun_files_data_cases.js --expected <dir>/rhino-files-data.json --denied files.control mediainfo.control java.interop storage.control
py -3 -I tools/api-coverage/device_bun_js_tools.py grant --serial <serial> --state <dir> --set restore --only files.control mediainfo.control java.interop storage.control text.pinyin
py -3 -I tools/api-coverage/device_bun_js_tools.py restore --serial <serial> --state <dir>
node tools/api-coverage/check-bun-files-data.mjs
```

The device phases run in this repository with the Bun and Node.js Runtime checkouts next to it (`--bun` / `--node` override the paths); `check-bun-files-data.mjs` also needs the Bundled Libraries plugin checkout and the `exports.json` of a `check.py` run. When one run exceeds the call limit of 1024 per execution, run the main script and the Worker separately (`--modes`).
