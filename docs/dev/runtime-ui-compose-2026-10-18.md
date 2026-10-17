# Node.js / Bun UI and Compose coverage

Date: 2026-10-18. This batch continues the runtime API audit and covers the
portable UI/Compose data contract. It does not claim complete Rhino object or
Android View interoperability.

## Delivered behavior

- Node main, CJS Workers, ESM Workers and nested Workers expose `ui` and `compose`,
  including `autojs6:ui` / `autojs6:compose`. Bun exposes `host.ui` and
  `host.compose` through its existing SDK.
- UI provides native JSON layouts, local state/bind, update/batchUpdate,
  on/once/off and close/closed. Layout windows have independent Activity tasks.
- Compose exposes all 54 portable V1/V2 component factories, modifiers,
  state/ref/batch/memo, theme, mount/dialog/floaty, node mutation and commands,
  frame flushing, Snackbar action/dismiss callbacks and floating-window
  position/size/focus/touch controls. Effects return Promises; JS callbacks stay
  in their creating runtime and thread.
- The host uses the real 3-Block Compose renderer, its negotiated component
  catalog, native text editing and Activity/window lifecycle owners. No Rhino
  script is evaluated to implement these calls. Node and Bun use byte-identical
  portable JS facades, checked against a shared source file.
- Native input, state, references and callbacks survive Activity recreation.
  Activity content uses the host's Compose system-bar/IME inset handling.
  Images passed to Compose use independent pixel copies so recycling a script
  image cannot invalidate a frame still being drawn.
- Worker termination and canceled creation close owned windows, including late
  creation replies. Other owners remain usable. Execution teardown releases its
  windows, renderer subscriptions, callbacks and borrowed resources.
- Node requires the `ui` manifest capability. Bun has independent, default-off
  `ui.layout` and `ui.compose` settings, translated into all ten languages.
  Compose floaty still requires Android overlay permission. Bun schedules its
  SDK requests within the existing four-request transport bound; the existing
  64 KiB request, 256 KiB response and 1024-call execution limits remain.
- Host discovery advertises Compose alongside UI and counts both kinds of
  active windows. The development Node catalog/runtime kit, declarations,
  online/offline documentation and Ace declarations/popups are synchronized.

## Problems found and corrected

1. Worker UI previously lacked a facade. Its new facade must use its own event
   reader instead of opening a main-thread-only push subscription. Creation
   replies remain observable during cancellation so their windows can be closed.
2. Repeated Activity launches could reuse an existing task instead of opening
   the new session. Each owned window now receives an independent task.
3. Native text fields reject direct text replacement through generic SetProps.
   Controlled text updates now use the native edit sequence and edit command.
4. Input immediately followed by a click could cross render generations and
   lose a callback. Live-node events use the accepted tree's handler; callback
   maps are retained until event-drain frame receipts allow their release.
5. Activity lifecycle destruction was closing sessions during rotation. The
   bridge now distinguishes configuration recreation from terminal destruction,
   restores text and theme, and rejects callbacks from detached renderer epochs.
6. A floating-session getter was evaluated during object spread before the
   handle existed. The getter is now evaluated on access. Desktop and physical
   tests cover floating creation and geometry.
7. Qualified `autojs6:ui` / `autojs6:compose` declaration aliases were missing.
   They now resolve in CJS/ESM and Ace. Named Compose exports include `sessions`.
8. UI argument errors were mislabeled as permission denial. Invalid requests
   now use the invalid-request code. Old tests asserting that Worker UI was
   absent or expecting the old error code were updated to the new contract.

Earlier failed diagnostic runs are retained separately. They are not counted as
accepted tests and their failures were not converted into skips.

## Accepted physical validation

Both devices are physical ARM64 devices with 4096-byte pages. Installed APKs were
hashed on-device and compared with the exact current build outputs before
acceptance. The existing 3-Block Compose installation was used without replacing
its APK.

| Suite | Redmi 22120RN86C, Android 13 / API 33 | Xiaomi 23046RP50C, Android 15 / API 35 |
| --- | --- | --- |
| RuntimeUiComposeDeviceTest | 7 passed, 72.035 s | 7 passed, 50.853 s |
| Node npm/conformance/desktop Worker suites | 44 passed, 139.049 s | 44 passed, 51.908 s |
| Existing Node UI regression | Not rerun on this device | 9 passed, 14.059 s |
| Bun runtime instrumentation | 13 passed, 29.131 s | Not rerun on this device |
| Ace API coverage instrumentation | Not rerun on this device | 5 passed, 77.475 s |

Total: 129 accepted instrumentation tests on the final APKs.

The seven new tests exercise real layout clicks, Compose clicks and text input,
batched state changes, refs, dialog validation, grant denial, Worker teardown,
floating window geometry, Snackbar actions and rotation. The core matrices use
Node CJS, Node ESM, CJS Worker, ESM Worker, nested Worker and Bun. Rotation checks
Node main, Worker and Bun, explicitly sets portrait/landscape and observes a new
Activity instance before checking preserved text/state. Floating coordinates are
checked at x=40, y=140, width=400 and height=180 physical pixels. This is not a
claim that every one of the 54 component variants received an individual visual
test; the full factory/property catalog has separate structural/type checks.

Ace checks real popups under normal provider budgets, including Promise return
types, session members and runtime isolation. Existing Rhino/JSOX coverage stays
in the same regression suite. Popup screenshots and preference/rotation restore
assertions are retained.

## Build and automated coverage

- Host debug and instrumentation APKs built from the isolated `.bak/oi-check`
  checkout plus an explicit source whitelist. The new host contract test passes.
- Node NDK syntax check uses the active compile_commands database. Release bridge
  payloads are stripped before runtime-kit hashes/build IDs are regenerated.
  Debug/instrumentation assembly, 96 JVM tests, example verification and the
  plugin TypeScript smoke checks pass. Existing libnode payloads are unchanged.
- Bun: 59 JVM tests, 24 SDK tests, 38 Python tests, strict SDK type checking,
  runtime/supervisor locks and APK integrity checks pass. Bun native bytes remain
  the official 1.4.0 payloads; this work makes no new native compatibility claim.
- Ace: 187 JVM tests, complete LSP verification, 302 runtime completion queries
  over 11 routing cases, declaration generation and debug/instrumentation builds
  pass. Node/Bun completions exclude Rhino `ui`/`compose` globals and JSOX globals.
- TypeScript 5.1.3 and 6.0.3 both report zero diagnostics. The 70 copied runtime
  declaration assets match their canonical hashes. Strict smoke examples cover
  state, component properties, sessions, floaty controls and invalid native View
  factories/property types.
- Cross-runtime coverage checks verify 54 factories, 11 Compose host operations,
  shared UI/Compose sources and 12 Worker modules. Native desktop Worker tests
  also cover late replies after exit and cancellation while the Worker remains
  alive.
- The broader augment check still reports 91 registration routes, 1351 explicit
  exports and 1522 queries with zero failures; its eight Python tests pass. These
  counts describe documentation/declaration/completion coverage, not full runtime
  implementation parity.
- Documentation generation/check covers 163 modules. Offline normalization,
  three Python tests, two JVM tests and APK assembly pass. Offline provenance
  names Documentation commit `7e4cef59327a3534e9f3f1dc16de3b3d23059edd`.

## Deliberate boundaries and evidence

AndroidView, Java View/Context factories, XML compose-container attach, native
property proxies and native overlay adjustment chrome remain outside this data
contract. Packaged-application Compose, other Android versions/ABIs and 16 KiB
devices have not received new acceptance in this batch. AI/flow/TTS/mail/EPUB and
other unrelated audit gaps remain separate work.

Evidence is under `.bak/ui-compose-20261017/`: `evidence.json`, per-device final
logs and result JSON, Ace screenshots, installation receipts and
`artifacts/manifest.json`. Eight exact tested APKs are retained in `artifacts/`.
The folder name records the batch start date; final acceptance is 2026-10-18.
Original APK backups are removed only after restoration is hash-verified.
Restoration receipts record original package hashes, recording-permission flags,
media volume, rotation and absence of active projection. Test-created packages
are removed; original application data and the host's five pre-existing draft
files are preserved.
