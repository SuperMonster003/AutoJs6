# Node.js files and data family: Rhino comparison and device validation

Date: 2026-10-29 (Asia/Taipei). Batch: api-coverage P3.2b (colors / Color, files, zip, storages, mediainfo, EpubBook#toString, mime, recorder; Q61 (b)).

## Runtime behavior

The Node.js Runtime plugin (b92e7a99) ports colors (with Color), files (with the Q18 prototype members), zip, mime and the timing recorder into the shared JS tools core of `node_bridge_sources.cpp`, next to the P3.2a modules. Each module is created on first use in the thread that asks for it; the main thread and Workers (CJS, ESM, nested) use the same code, and the Bun runtime syncs it (P4.2b). Argument counts, conversions and error classes follow the host modules (`runtime/api/modules/**`, ScriptArgs).

- Entries: `colors` / `autojs6:colors`, `files`, `mime`, `recorder`, `autojs6:zip` (bare `zip` stays free for the npm package) and `$autojs.zip`; Rhino's global `open` and `Color`, and the libraries dayjs / pangu / cheerio / i18n, are on `autojs6:global`.
- dayjs / pangu / cheerio / i18n (Q61 (b)) are the host's builds, shipped in the plugin APK (`assets/nodejs/libraries`, 716 KB, about 196 KB compressed) and compiled on first access in each thread. The Worker's axios sends its requests through the main thread's axios.
- Members that need Java objects go through the Java facade (`java_interop`) and return Promises; the caller releases the handles: files.open / toFile / readAssets and the global open, colors.toColorStateList / setPaintColor / extract / themeColor, and every zip4j call (zip runs in the host process). Zip's zipFile / zipParameters / unzipParameters are async methods (D-12).
- storages.names / all list the stores Node scripts wrote in the same package and working directory, through a registry store (Q79 (a)); Storage#size is a method returning a Promise. Workers gain storages and mediainfo. EpubBook#toString matches Rhino.
- Known differences are recorded as Q83 (files.readBytes returns Uint8Array, the 2540 mime constants are not enumerable as in Rhino, recorder(fn) errors are not wrapped, colors.extract also takes Bitmap handles and `images.readPixels` pixels, relative paths of zip and files.open / toFile resolve against Node's working directory). colors.theme, gallery and files.batchRename wait for host bridge modules (Q80 / Q81 / Q82, P3.5).

The APK grows by about 0.3 MB per ABI (0.5 MB universal): the four library files and about 333 KB of embedded script in `libautojs6-node.so`.

## Rhino values and cases

`tools/nodejs/api/files-data` in the Node plugin repository holds 1535 cases: colors 633, files 487, mime 158 and recorder 73, zip 171, storages / mediainfo / global 13. `device_files_data.py rhino` ran them in Rhino on the device below; `rhino-expected.json` records the values (352 throw).

For 15 cases the Rhino interpreter itself fails: a Color member proxied by `ColorNativeObject` (toInt, toHex, alpha, toHsv, toHsva, isSimilar, isEqual, blend, contrast, build, oklab, oklch, extract) that throws reaches Rhino as an unwrapped `InvocationTargetException` (`ColorNativeObject.kt`, `method.invoke` in the member function), and `Interpreter.interpretLoop` fails with `ClassCastException: InvocationTargetException cannot be cast to java.lang.Error`, past the script's try / catch, which ends the script. The recorder runs each case in a Runnable so that such a failure stays within the case; these 15 cases only require Node to throw. This is a host defect, reported separately.

Three case expressions were adjusted to what Rhino does on the device: Rhino picks `Bitmap.eraseColor(long)` (a ColorLong, API 29+) for a number, so the test image sets every pixel with setPixel; Rhino's storages.names lists non-empty preference files, which SharedPreferences writes in the background, so the Rhino expressions wait one second before listing; `colors.themeColor.colorPrimary` is not compared (Rhino boxes the Java field, and `Number()` of it fails).

Desktop comparison (`node --test tools/nodejs/api/test-files-data.js`, CI step added to the plugin's build workflow with the files-data unit tests): 830 colors / mime / recorder cases that need no host, on the main thread and in a nested Worker started with the real Worker preload. 157 Node tests of the build workflow and 96 JVM unit tests passed.

## Verification

Device: Xiaomi 23046RP50C, serial `968e9f18`, Android 15 / API 35, arm64-v8a.

| Package | Before | During | After |
|---|---|---|---|
| org.autojs.autojs6 | 6.8.0 (5935), base.apk md5 `1de778e01894e156b113dd3fbf1e7714` | unchanged | unchanged |
| Node.js Runtime plugin | 1.32.0 (292) debug, md5 `399e7a359fea55557a2415801b3b6b3d` | 1.32.0 (299) debug arm64, md5 `48f0ab9f0751f3999640744af80283c6` | 1.32.0 (292) reinstalled from the saved APK, md5 verified |
| Node.js Runtime test package | md5 `d9657ce5d549706b4b5a3731f49b8585` | debug androidTest, md5 `554242b12a3016d9ce2217a1d23116b6` | reinstalled, md5 verified |
| Bun runtime plugin | 0.2.5 (118), md5 `555d45afcde4c611cb7665909313152b` | unchanged | unchanged |

`device_js_tools.py verify` against the recorded state reported "same as recorded". The run directories (`/sdcard/Download/aj6-p32a`, `/sdcard/Download/aj6-p32b`) were removed; the plugin keeps its all-files access as before.

The debug build was made from an export of Node e1cdf4da with the P3.2b files: the Node HEAD at the time (3f620da0) carries the Node 26.13 bridge headers of M25.2b, which do not link against the Node 24.21 `libnode.so` in LFS (Node roadmap Q7). `node_bridge_sources.cpp`, `NodePluginScriptExecution.java`, `NodeJsLibraries.java` and the library assets of the build are byte-identical to b92e7a99.

| Check | Result |
|---|---|
| Node main thread (CJS `require`) | 1535 passed, 0 failed; 216 results were Promises |
| Node ESM (`import` from a data: URL) | 1535 passed, 0 failed |
| Nested Worker (Worker started by a Worker, CJS) | 1535 passed, 0 failed |
| P3.2a js-tools cases (`device_js_tools.py node`) | main 512 / ESM 513 / Worker 513 passed; every Q61 case passes (dayjs, pangu, cheerio, i18n, Worker axios); 8 differences are the device's density 2.5 and API 35 against Rhino values recorded on a Redmi (density 2, API 33); 1 result line lost on the main run |
| NpmEcosystemSmokeTest and NodeRuntimePluginAndroidConformanceTest | 34 passed (43 s) |

Found and fixed on the device before these runs: a decimal that rounds to zero prints as "0" (`BigDecimal.stripTrailingZeros`, also checked on a Redmi API 33); the mime separators and the identifier check follow Android's ICU regex, whose `\s` is the Unicode White_Space set (U+00A0 included) and `\w` covers all scripts; JsMime#toString shows a null field as undefined; charset names are looked up the ICU way first (`"utf 8"` is UTF-8) and checked only when not found; Kotlin `require` failures of Zip members are IllegalArgumentException (not InternalError); `getFileHeader` of a missing entry rejects with NullPointerException as in Rhino; colors.extract takes Bitmap handles and pixels, since Node scripts cannot create an ImageWrapper (it needs a host ScriptRuntime).

Two properties of the setup matter for repeating the runs. The host gives a Node project run a wall-clock budget of a few seconds unless `project.json` sets `node.timeoutMs` (`ERR_AUTOJS6_SCRIPT_TIMEOUT`; the stdout relay delivers results after the stop, which looks like a hang); the files-data project sets 900 s. Node's working directory is the plugin's private copy of the project (`.../cache/node-plugin-workspaces/<id>/sandbox`), which the host process cannot write, so the zip cases use absolute paths and `zip.unzip(path, {})` (destination = working directory) is compared by exception class only.

## Repeating the checks

```powershell
node --test tools/nodejs/api/test-files-data.js tools/nodejs/api/files-data/test-colors.js tools/nodejs/api/files-data/test-files.js tools/nodejs/api/files-data/test-mime-recorder.js tools/nodejs/api/files-data/test-zip.js
py tools/nodejs/api/js-tools/device_js_tools.py state -s <serial> --state state.json
py tools/nodejs/api/files-data/device_files_data.py rhino -s <serial>
py tools/nodejs/api/files-data/device_files_data.py node -s <serial> --report report.json
py tools/nodejs/api/js-tools/device_js_tools.py verify -s <serial> --state state.json
node tools/api-coverage/check-runtime-files-data.mjs
```

The first five run in the Node.js Runtime plugin repository (the device steps with the plugin debug build installed and all-files access granted to the host and the plugin); the last runs in this repository.
