# Embedded Node Example Projects

Example projects live under:

```text
sample/nodejs/
```

Each example contains:

- `README.md`
- `project.json`
- `package.json`
- the `project.json.main` entry, usually `main.cjs`, `main.cts`, or `main.mts`
- `expected-output.txt`

The suite manifest lives at `sample/nodejs/examples.json`. It records each example status, tags, whether it is included in
main-app smoke, and whether it is packaged-compatible.

For Rhino-to-Node migration guidance, see [MIGRATION_FROM_RHINO.md](MIGRATION_FROM_RHINO.md). For behavior differences from Auto.js Pro 9 and AutoX.js v7, see [COMPATIBILITY_NOTES_PRO_AUTOX.md](COMPATIBILITY_NOTES_PRO_AUTOX.md).

For the smallest stable entry-point shape, see [QUICK_START.md](QUICK_START.md). For the current v1.1 feature draft, see
[NODE_PROFILE_V1_1_USER_DRAFT.md](NODE_PROFILE_V1_1_USER_DRAFT.md). For current release limitations and workarounds, see
[releases/NODE_PROFILE_V1_KNOWN_ISSUES.md](releases/NODE_PROFILE_V1_KNOWN_ISSUES.md).

## Rhino Compat Migration Examples

S11-27 adds static Rhino compatibility migration examples under
`docs/nodejs/examples/rhino-compat`. They pair `*.rhino.js` legacy snippets with
`*.node.cjs` `require("autojs6:compat")` rewrites for
toast/files/storage/selector/image/ocr/app/device/shell. P12-19 extends the
same static suite with tasks migration buckets for disposable-task async support
and stable daily/weekly/intent-task rejections. P12-30 adds barcode/qrcode examples for path input, image-handle input, QR-only filtering, and explicit temporary image recycle. They are separate from the
runnable `sample/nodejs` project catalog because they are source migration pairs
rather than full project smoke tests.

Verify them with:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatExamples
```

To create a new user project from a checked template or validate an existing
project directory, use [PROJECT_WIZARD.md](PROJECT_WIZARD.md):

```powershell
node tools/nodejs/project/autojs6-node-project.js create --template commonjs-app --out D:\work\my-node-app
node tools/nodejs/project/autojs6-node-project.js validate D:\work\my-node-app
```

## Examples

- `hello-node`: minimal Node entry script and version smoke.
- `cjs-package`: local CommonJS helper plus a checked-in `node_modules` package.
- `fs-scoped`: synchronous scoped filesystem read/write.
- `fs-promises`: partial scoped `fs/promises` read/write.
- `npm-pure-js-cjs`: pure JavaScript CommonJS npm-style packages without `npm install`.
- `toast-app`: `toast` and `app` AutoJs bridge modules.
- `accessibility-click-text`: accessibility status and text click flow.
- `screenshot-find-image`: screenshot/image flow that exits cleanly when screen capture permission is unavailable.
- `packaged-node-apk`: packaged APK smoke entry point.
- `disabled-features-demo`: Safe Node Profile denied-capability examples.
- `packaged-esm`: packaged local ESM module graph.
- `packaged-dynamic-import`: packaged local dynamic import graph.
- `live-fetch`: controlled live fetch provider shape.
- `live-websocket`: controlled WebSocket provider shape.
- `ocr-automation`: OCR/image opaque-handle boundary.
- `long-running-service`: interactive long-running lifecycle checkpoint pattern.
- `scheduled-node-task`: WorkManager scheduled Node task pattern.
- `worker-cpu`: worker CPU task with Safe Node fallback documentation.
- `node-test-project`: small `node:test` style project.
- `database`: scoped SQLite bridge pattern.
- `js-plugin-ui`: local JS-only plugin that returns a UI layout descriptor.
- `typescript-cjs`: lightweight `.cts` CommonJS runtime smoke.
- `typescript-esm`: lightweight `.mts` ESM runtime smoke.
- `packaged-typescript`: packaged-compatible `.mts`/`.ts` graph with local dynamic import.
- `require-esm`: CommonJS entry that loads a local ESM module with `require()`.
- `http-client-compat`: controlled HTTP client shape and default-denied network behavior.
- `filehandle-advanced`: scoped `fs/promises.open()` FileHandle lifecycle.
- `fs-watch`: scoped watcher creation, update, and close lifecycle.
- `compile-cache`: CommonJS module cache and optional compile-cache probe.
- `inspector-debug`: release inspector disabled-state probe.
- `cpu-profile`: design-gated CPU profiling placeholder.
- `hardened-sandbox`: design-gated isolated-process sandbox placeholder.
- `node-runtime-info`: runtime version and descriptor-shape reporting.
- `startup-snapshot`: design-gated startup snapshot policy placeholder.
- `heap-snapshot`: design-gated heap snapshot diagnostics placeholder.
- `multi-instance`: design-gated multi-process runtime pool policy placeholder.
- `sandboxed-script`: design-gated brokered sandbox execution placeholder.
- `wasm-basic`: WebAssembly compile/instantiate compute example.
- `wasi-scoped-fs`: controlled WASI facade and scoped-fs policy placeholder.
- `wasm-worker`: design-gated WASM worker integration placeholder.
- `wasm-plugin`: JS plus WASM plugin package policy placeholder.
- `pro-parity-suite`: Phase 13 Pro parity examples for Java interop, Rhino proxy install, UI, overlay, tasks, screenshot/OCR, and accessibility migration.
- `desktop-parity-suite`: Phase 13 desktop Node parity examples for ESM, worker_threads, HTTP/HTTPS, raw network, advanced fs, WASI, inspector, and npm dependency migration.
- `typescript-smoke`: compile-time smoke for the checked-in AutoJs6 Node bridge declarations.

## Phase 13 Pro Parity Examples

`sample/nodejs/pro-parity-suite` is a migration catalog for high-value
Auto.js Pro-style APIs. It includes guarded snippets for `$autojs.java`,
`rhino.install`, UI, floaty/overlay, tasks, screenshot/OCR, and accessibility.
Each catalog entry declares required capabilities, expected output, packaged
behavior, and the skip reason used when a provider, permission, or profile is
unavailable.

Verify the catalog with:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13ProParityExamples
```

`smoke.cjs` is the focused runtime smoke subset for Activity-owned UI layout
and WorkManager one-shot schedule/cancel behavior. It is covered by
`NodeExampleProjectInstrumentationTest#proParitySafeSubsetExamplesRun`.

P13-53 keeps high-risk Pro examples out of the default runtime smoke list. The
catalog remains migration evidence for migration shape and docs parity;
packaged Pro execution remains gated by provider promotion, disclosure review,
and device permission evidence.

P14-16 docs sync keeps this policy unchanged for the v1.2
`pro.ui_automation_media` surface. Only the already covered `ui-layout` and
`tasks-work-manager` paths execute in the focused Pro parity smoke subset;
overlay, media/recorder, screenshot/OCR, and accessibility snippets remain
static, guarded, or denial-oriented until their provider, packaged disclosure,
permission, and device-matrix archives are promoted.

P14-27 docs sync keeps the same rule for the v1.2 `pro.tasks_device_media`
surface. `tasks-work-manager` remains the only task/device/media focused smoke
path; `notifications-power` and `media-recorder` stay migration evidence and
guarded/denial-oriented examples until task database mutation, broadcast task
registration, notification disclosure, OEM power matrices, real recorder
sessions, packaged recorder stress, playback, and MediaStore provider gates are
promoted.

## Phase 13 Desktop Node Parity Examples

`sample/nodejs/desktop-parity-suite` is a migration catalog for desktop-style
Node APIs. It includes guarded snippets for ESM, `worker_threads`,
HTTP/HTTPS, raw network, advanced filesystem APIs, WASI, inspector, and
sanitized pure JavaScript npm dependencies. Each catalog entry declares the required
`desktop_compat_opt_in` profile, required capabilities, expected output,
Safe-profile denial output, packaged behavior, and skip reason.

Verify the catalog with:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13DesktopParityExamples
```

`smoke.cjs` is the focused runtime smoke subset for managed local ESM,
controlled HTTP/HTTPS facade shape without live network I/O, the checked-in
pure JavaScript dependency path, and scoped advanced filesystem FileHandle/watch
behavior. It is covered by
`NodeExampleProjectInstrumentationTest#desktopParitySafeSubsetExamplesRun`.

P13-54 keeps high-risk desktop examples out of the default runtime smoke list.
The catalog remains migration evidence for desktop parity planning; packaged
desktop execution, raw worker/raw-network/expanded-root/WASI/inspector authority,
and Android package-manager parity remain gated by future promotion evidence.

P14-32 docs sync keeps the v1.2 desktop runtime examples guarded under the
`worker_intentionally_unsupported_v1_2` release decision. `worker_threads`, WorkerPool, process-worker, and raw `child_process` examples remain static, guarded, or denial-oriented; they are not runnable v1.2 release smoke until native worker availability, packaged worker behavior, WorkerPool resource stress, process-worker UX, security corpus, and rollback gates pass.

## Smoke Coverage

Instrumentation smoke currently runs:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeExamples
.\gradlew.bat --console=plain :app:verifyNodeProjectWizard
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeExampleProjectInstrumentationTest"
```

`verifyNodeExamples` validates the checked-in manifest, required files, `project.json`, `package.json`, Node directives,
PASS markers, and Phase 7 example metadata. Phase 7 examples must declare capabilities, expected provider, packaged
support, and security limitations in `project.json`. The task writes:

- `app/build/reports/nodejs/examples.json`
- `app/build/reports/nodejs/examples.md`

`verifyAutoJs6NodeTypeDeclarations` validates the checked-in TypeScript declaration set under
`docs/nodejs/types/autojs6-node/` and the `typescript-smoke` sample wiring. If `tsc` is available locally, run:

```powershell
.\gradlew.bat --console=plain :app:typeCheckNodeTypescriptSmoke
```

The instrumentation smoke copies examples from `sample/nodejs` into the device cache and runs:

- `hello-node`
- `cjs-package`
- `fs-scoped`
- `fs-promises`
- `npm-pure-js-cjs`
- `toast-app`
- `accessibility-click-text`
- `screenshot-find-image`
- `disabled-features-demo`
- `typescript-cjs`
- `typescript-esm`

`typescript-smoke` remains type-check only; runtime TypeScript coverage comes from
`typescript-cjs`, `typescript-esm`, and the focused `:app:verifyNodeTypeScriptGate`
instrumentation suite. `packaged-typescript` documents the packaged-compatible
`.mts`/`.ts` graph shape; injected TypeScript moduleSources are covered by the
focused TypeScript gate.

The Phase 8 examples `require-esm`, `http-client-compat`, `filehandle-advanced`,
`fs-watch`, `compile-cache`, `inspector-debug`, `cpu-profile`, and
`hardened-sandbox` document the expanded v1.1 capability surface and
design-gated boundaries. They are static-verified by `verifyNodeExamples` but
are not part of the default runtime smoke list.

The Phase 9 examples `node-runtime-info`, `startup-snapshot`, `heap-snapshot`,
`multi-instance`, `sandboxed-script`, `wasm-basic`, `wasi-scoped-fs`,
`wasm-worker`, and `wasm-plugin` document the native modernization,
diagnostics, isolation, and safe WASM/WASI/plugin extension direction. They are
static-verified by `verifyNodeExamples`; `wasm-basic` is a real WASM compute
example, while design-gated examples print explicit policy markers instead of
pretending deferred capabilities are available.

The examples are also added to the `androidTest` asset source set so tests exercise the checked-in sample files directly.
`sample/nodejs/npm-ci-pure-js` is intentionally excluded from the examples manifest because it is a host-side dependency
sanitizer fixture, not a user-facing runnable example.

## Runtime Notes

The stable examples target AutoJs6 Safe Node Profile and generally avoid:

- `npm install`
- native addons
- `child_process`
- raw Node network modules
- unrestricted filesystem access

Phase 7 and later examples that use ESM, dynamic import, TypeScript, live providers, workers, long-running mode,
scheduled tasks, database, controlled HTTP, advanced scoped fs, compile-cache, inspector, sandbox, WASM/WASI, multi-instance, snapshot, or plugin/UI surfaces declare those capabilities explicitly and include graceful
unavailable-provider paths where a permission, experimental flag, or hardware provider may be absent.

Examples that use Android capabilities such as accessibility or media projection are written to handle unavailable permissions without crashing.

Node bridge examples may declare the capability surface in `project.json`:

```json
{
  "type": "node",
  "main": "main.cjs",
  "node": {
    "permissions": ["toast", "app.launch", "accessibility", "screen_capture"]
  }
}
```

When `node.permissions` is present, undeclared bridge calls reject before dispatch with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`. Older examples without the field keep compatibility mode.
