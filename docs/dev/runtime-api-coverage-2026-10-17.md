# Node.js and Bun API repair and device validation

Date: 2026-10-17 (Asia/Taipei).

This delivery addresses the audit's prioritized Pinyin, console controls, type declarations and coordinate conversion gaps. Bun receives an explicit client for the same host capabilities. It does not claim full Rhino API equivalence for either runtime.

## Runtime behavior

- Node `pinyin` / `pinyin4j` now call the installed providers asynchronously, including `pinyin4j.readings`. The small built-in dictionary is removed. Callers must await results; `compact` uses the host's matrix semantics.
- Node console window and styling operations are asynchronous host calls. Controls target the console receiving that execution's output; `console.tail` remains a global-log operation. Global file-log configuration retains its explicitly global meaning. Showing a window requires Android overlay permission.
- `screen_metrics` and the Node compatibility facade expose `cX`, `cY`, `cXy`, `cYx` and the existing `getScaleBases` / `setScaleBases` family. Each execution owns a fresh `Scale`; each axis can be assigned once. Getters and setters are asynchronous.
- Node and Bun use `RuntimeCoordinateBridge`, which delegates to the same `CoordinateScale` and `Scale` used by Rhino. A display snapshot reads rotation once for both dimensions. Explicit bases, ratio mode, `width:height` literals, negative rounding and invalid inputs retain the shared rules.
- Bun supplies an explicit SDK through `AUTOJS6_HOST_CLIENT_MODULE`. `createHostClient()` negotiates grants. `text.pinyin` (6 methods), `text.pinyin4j` (3), `screen.metrics` (11) and `console.control` (31) have independent Plugin Center switches. The existing toast and device-info calls remain available. Console control defaults off.
- Both clients reject non-finite numeric arguments and explicitly undefined coordinate values before JSON can turn them into null. Zero-argument `cX()` / `cY()` and optional undefined base/mode values remain valid. Numeric coordinate strings are declared and supported.
- Bun waits for the finished callback before closing the output receiver after a successful synchronous result. Failed results preserve their original error if callback delivery fails. Cancellation remains bounded.

Node CJS and ESM share these public modules. Workers retain their generic parent-routed bridge; native CLI processes do not automatically receive the script bootstrap. Bun does not inject Rhino globals.

## Declarations, documentation and completion

The Node-specific index now includes `input_observer` and `screen_metrics`. Missing `accessibility.swipe` / `gesture`, `fetch.request`, `axios.AxiosError` and profile members are declared. Coordinate zero-argument overloads are separated from overloads requiring a value, so strict typing rejects `cX(undefined)`.

The companion declaration project supplies isolated Node and Bun entry points and reproducibly synchronizes 68 runtime declaration files into Ace. Ace selects the runtime from supported directives, extensions and Node project metadata. It excludes Rhino globals and prototype extensions from Node/Bun, while retaining explicit Node JSOX module completion. Long signatures wrap within the viewport without covering code or the completion list.

The complete host check also found new inline nested mounts and Speech documentation/types missing since the earlier audit. The extractor now understands `.also` mounts, including named lambda parameters, and extracts `plugins.tools`, `files.batchRename` and `speech.models`. Unknown forms still fail. Speech's 19 explicit members and session/model types are documented and typed; they remain excluded from Node/Bun globals.

## Verification

Device: Sony XQ-DQ72, serial `QV770340J7`, Android 13 / API 33, native arm64-v8a, 4096-byte pages. Physical portrait dimensions were 1096 x 2560; landscape dimensions were 2560 x 1096.

| Check | Result |
|---|---|
| Shared coordinate math and screen-cache JVM tests | 18 passed |
| Host JSON adapter, Bun output ordering and real API integration | 10 passed |
| Coordinate matrix | 36 executions: 3 runtimes/import modes x 4 rotations x 3 base pairs |
| Numeric coordinate assertions | 684 passed, including numeric strings |
| Invalid coordinate/base calls | 216 rejected as expected |
| Same-execution rotation | Node CJS and Bun each observed 0 -> 90 -> 270 -> 0, 8 observations total |
| Node npm ecosystem and complete Android conformance classes | 34 passed |
| Bun SDK Binder round trip | Passed, including real SDK loading, grant checks and cleanup |
| Pinyin provider Binder/service lifecycle class | 3 passed; includes 6 real service recreations and repeated dictionary calls |
| Ace default APIs, runtime isolation and JSOX activation/removal | 3 passed; wrapped signatures also checked on the device |
| Runtime client/host contract comparison | 4 groups / 51 methods matched |
| Host mount/parser tests | 8 passed |
| Complete host API coverage | 91 mounts, 1351 explicit exports, 1522 TypeScript/Ace paths, no failing paths |
| Runtime declaration sync | 68 files matched their Node/Bun sources |
| Independent declaration checking | TypeScript 5.1.3 and 6.0.3, strict mode and full declaration checking |

The real-provider assertions include `pinyin.simple('北京上海') === 'beijingshanghai'`, Pinyin4j readings, actual console title/color state and output ownership. Bun revocation was exercised across executions: successful conversion, revoked capability with SDK rejection and raw HTTP 403, unaffected coordinate/Pinyin4j calls, then successful conversion after restoration.

An initial repeated-provider run used a stale July APK from the Pinyin repository's `app/release` cache and failed with an already-closed SQLite database. Current Pinyin source had already fixed that lifecycle in `6d217e95` on 2026-09-01. Validation was repeated with a current 1.0.3 / build 33 APK, and a service-recreation regression was added. This is not claimed as a new database implementation fix.

The device checks exercise current compiled code and actual Binder/providers. The local wrapper checks use controlled transports and are reported separately. No new speech audio/model download tests, historical Node build-slot acceptance or additional device/ABI acceptance are implied.

## Repeating the checks

```powershell
python tools/api-coverage/check.py
python -m unittest discover -s tools/api-coverage -p 'test_*.py'
node tools/api-coverage/check-runtime-bridges.mjs
.\gradlew.bat --offline :app:testAppDebugUnitTest --tests org.autojs.autojs.runtime.api.ScreenMetricsCacheTest --tests org.autojs.autojs.runtime.api.augment.global.CoordinateScaleTest
```

The Android classes are `RuntimeCoordinateBridgeTest`, `BunOutputCallbackDeviceTest` and `RuntimeApiBridgeDeviceTest` in `org.autojs.autojs.engine`. The last class requires the current Node, Bun, Pinyin and Pinyin4j plugins. It restores Bun grants and screen rotation settings and stops only its owned UI fixture.

Detailed local build logs, APK identities and device JSON/screenshots are retained under ignored `.bak/runtime-api-20261017/`. Generated README/changelog files match their sources. The host's repository-wide translation-count check still reports pre-existing differences: before this work, the nine non-Simplified-Chinese files already lacked 12 feature, 8 fix and 6 improvement entries relative to the Simplified Chinese draft. This delivery adds the same six entries to all ten languages and leaves the unrelated draft/order changes untouched.

## Remaining audit boundaries

The full host coverage gate measures documentation, types and completion against host exports. It must not be read as 1351 implemented Node/Bun APIs. The subsequent [OpenCC and common image delivery](runtime-opencc-images-2026-10-17.md) closes the OpenCC facade gap and adds common image operations. Android-object helpers, calendar/IntentTask parity, complete image/UI APIs, and dedicated AI/flow/Compose/TTS/mail/EPUB and other large runtime surfaces still require separate implementations or explicit compatibility decisions. Native libraries and `rhino.run` are not counted as direct parity.
