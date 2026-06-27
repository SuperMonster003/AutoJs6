# Phase 8 Real npm Corpus Expansion

S8-46 expands the fixed offline real npm corpus from the Phase 7 baseline of
13 fixtures to 20 fixtures. The pass-rate stays at 100%: Phase 7 recorded
13/13, and the Phase 8 Sony gate records 20/20, for a `+7` fixture delta.

Focused gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

## Added Fixtures

| Fixture | Coverage |
| --- | --- |
| `typescript-source-package` | Native TypeScript source project shape with checked `.cts` source metadata and runtime-safe CommonJS fallback. |
| `require-esm-hybrid` | CommonJS `require()` of a synchronous ESM package namespace plus a CJS bridge package. |
| `http-client-package` | Package using `node:http`/`http` client and safe `Agent` shape while default raw network remains denied. |
| `formdata-fetch-package` | Package using Web Platform `FormData` plus default controlled `fetch` denial. |
| `filehandle-package` | Package using `fs.promises.open()` and managed scoped `FileHandle` read/write/close flow. |
| `fs-watch-package` | Package using `fs.watch()` lifecycle and cleanup; focused watcher event semantics remain covered by S8-30. |
| `compile-cache-package` | Package that benefits from repeat module loads and reusable module graph caching. |

## Safety Boundary

- fixtures stay checked-in, offline, and license-noted
- fixture roots keep CommonJS `test.cjs` runners
- no install, postinstall, prepare, or other lifecycle scripts are allowed
- no native `.node` addons are vendored
- ESM package metadata remains allowed only below fixture `node_modules`
- raw network authority remains denied by default; HTTP/fetch fixtures verify
  denial or controlled facade behavior instead of performing live I/O
- watcher coverage is compatibility-oriented; Android file-event delivery can be
  coalesced, so the real npm fixture requires creation and cleanup while the
  focused watcher gate owns event semantics

`NodeRealNpmCompatInstrumentationTest` now records the v1.0 baseline, the Phase
7 baseline, total fixture count, overall delta, and Phase 8 delta in corpus
stdout. Per-fixture differential JSON remains available as
`realnpm.differential.result=...` records in connected-test diagnostics/logcat.
