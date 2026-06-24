# AutoJs6 Node TypeScript Declarations

These declarations describe the AutoJs6 Safe Node Profile bridge modules exposed through CommonJS `require()`:

- `toast`
- `app`
- `dialogs`
- `clipboard`
- `device`
- `shell`
- `engines`
- `accessibility`
- `media_projection`
- `image`
- `images`
- `ocr`
- `barcode`
- `media`
- `mediainfo`
- `recorder`
- `storage`
- `storages`
- `database`
- `sqlite`
- `console` / `node:console`
- `timers` / `node:timers`
- `notifications`
- `sensors`
- `ui`
- `ui.overlay`
- `input_observer`
- `fetch`
- `http` / `node:http`
- `https` / `node:https`
- `net` / `node:net`
- `tls` / `node:tls`
- `dns` / `node:dns`
- `dns/promises` / `node:dns/promises`
- `axios`
- `websocket`
- `work_manager`
- `autojs6:lifecycle`
- `autojs6:compat`
- `files`
- `base64`
- `colors`
- `formatter` / `fmt`
- `converter` / `cvt`
- `s13n`
- `mime`
- `nanoid`
- `util` / `node:util`
- `opencc`
- `pinyin`
- `pinyin4j`
- `jsox`
- `jsox.mathx`
- `rhino`
- `plugins`
- `java` (experimental allowlist gate only)
- global `$autojs`
- `autojs6:profile`

The `autojs6:lifecycle` module is available only inside the explicit interactive long-running runtime. It stores JSON-only project-scoped checkpoints for explicit restore and does not enable automatic restart.

P13-12 adds the read-only `$autojs` global v1 and the matching
`autojs6:profile` declaration. `$autojs.version`, `$autojs.profile`, and the
`$autojs.androidContext` descriptor are diagnostics-only in the Safe Node
Profile; `$autojs.java` is a gated facade that reports the Java interop policy
and rejects by default with the runtime Java interop error code.

P13-13 extends the declarations for the experimental `java` module and
`$autojs.java` facade with allowlist-only Java object handles. The declared
handle helpers cover `call`, `callInstance`, `getField`, `release`, and
`dispose`; default builds still reject Java interop unless the build-time
`autojs.nodejs.javaInterop.experimental` gate is enabled.

P13-32 extends the `autojs6:profile` declaration with
`filesystemProfile`. The field is diagnostic-only in the current Safe profile:
`mode` is `scoped_working_directory` and `additionalRoots` is empty until a
future explicit filesystem root provider is promoted.

P13-33 extends the `autojs6:profile.filesystemProfile` declaration with
`advancedApis` and `limits`. These fields report scoped advanced fs state for
streams, `FileHandle`, fd APIs, `opendir`, watchers, and `realpath`; they do
not expose raw native fds or expanded filesystem roots.

P13-34 extends the `autojs6:profile` declaration with `workerThreadsProfile`.
The field reports default denial, request-local experimental preflight, bounded
worker policy, bridge denial, WorkerPool, transfer-list, and packaged behavior
diagnostics; it does not make `worker_threads` a default, project, or packaged
capability.

P13-35 extends the `autojs6:profile` declaration with
`processWorkerReplacementProfile`. The field reports that the current
process-worker replacement path is `worker_threads.WorkerPool`, while the
callable `process_worker`/`compute_worker` facade, raw `child_process`, raw
process handles, and packaged process-worker behavior remain unintroduced.

P13-36 extends the `autojs6:profile` declaration with `vmProfile`. The field
reports default VM denial and future Desktop/Debug enablement requirements for
`vm.Script`, contexts, timeout cancellation, private binding denial, bridge and
host object denial, scoped-fs preservation, memory limits, and packaged
behavior. It does not declare `require("vm")` or make VM APIs callable.

P13-37 extends the `autojs6:profile` declaration with `inspectorProfile`. The
field reports default inspector denial and future Debug enablement requirements
for explicit user action, DevTools attach, loopback ADB forwarding, CPU/heap
artifact redaction, and packaged behavior. It does not declare
`require("inspector")` or make inspector APIs callable.

P13-38 extends the `autojs6:profile` declaration with `wasiProfile`. The field
reports pure WebAssembly availability, raw WASI denial, and future controlled
WASI enablement requirements for scoped preopens, authorized roots, virtual fd
tables, explicit args/env policy, bounded stdio, limited clock/random, resource
budgets, worker integration, and packaged behavior. It does not declare
`require("wasi")` or make WASI APIs callable.

P13-39 extends the `autojs6:profile` declaration with `nativeAddonProfile`. The
field reports default native-addon denial and future Desktop enablement
requirements for signed packaged-only artifacts, ABI and hash allowlists,
dependency scanning, crash isolation, native-build denial, and download-exec
denial. It does not declare `.node` modules or make native addon loading
callable.

P13-40 extends the `autojs6:profile` declaration with `childProcessProfile`.
The field reports default `child_process` denial, separate `shell` bridge
status, and future Desktop scoped-runner requirements for app-private
allowlisted binaries, bounded stdio, timeout, cancellation, kill, cleanup,
packaged behavior, and raw-handle denial. It does not declare
`require("child_process")` or make child_process APIs callable.

P13-41 extends the `autojs6:profile` declaration with `esmLoaderProfile`. The
field reports the current partial AutoJs6-managed ESM loader contract for
`.mjs`, `type=module`, TLA, local static/dynamic import, package
`exports`/`imports`, JSON import attributes, scoped `import.meta`, CJS interop,
source-name stack behavior, and focused packaged ESM evidence. It also records
raw loader hooks, raw `node:module` loader authority, network/file/data URL
imports, working-directory escapes, and raw Node loader handles as denied.

P13-42 extends the `autojs6:profile` declaration with
`packageManagerProfile`. The field reports the current partial Android package
manager boundary: host-managed package validation is available, the Android
runtime has a local app-private store substrate for already-unpacked packages,
and npm CLI, registry download, lifecycle scripts, bin links, symlinks, native
builds, native payloads, postinstall downloads, script-visible package-manager
facades, raw executables, and raw registry access remain denied.

P13-43 extends the `autojs6:profile` declaration with `stdlibProfile`. The
field reports the current partial Safe Node stdlib conformance subset for
`crypto`, streams, zlib, Buffer, URL, OS, readline, TTY, `perf_hooks`, and
`async_hooks`; it also records stable Android differences and keeps raw desktop
parity, raw native handles, unrestricted network behavior, broad terminal
control, `process.getBuiltinModule`, and `zlib/promises` denied.

P13-44 extends the `autojs6:profile` declaration with `processParityProfile`.
The field reports the current partial process/env/argv subset: controlled
`process.env`, script-local env mutation, scoped cwd/chdir, `process.argv`,
`exitCode`, versions, timing, resource snapshots, permission/report
diagnostics, bounded stdio, and stable denials for raw exit, signals, native
bindings, raw inherited env, and full desktop process parity.

P13-45 extends the `autojs6:profile` declaration with
`packagedCapabilityProfile` and makes `bridgePermissions.packagedMetadata`
typed. The fields report packaged profile, builtin, native asset, filesystem
root, execution-mode, network, and Android permission metadata as
diagnostic-only; authority still comes from declared bridge permissions and
future reviewed packaged profile gates.

The `autojs6:compat` module is the explicit Rhino compatibility loader. It does not install Rhino globals by default; scripts must opt in before later Phase 11 facade modules can expose Rhino-style helpers. S11-11 adds the pure utility subset for `base64`, `colors`, `fmt.bytes`, `cvt.bytes`, `mime`, `nanoid`, and Node `util.inspect` migration. S11-12 adds text utility facades for `opencc`, `pinyin`, and `pinyin4j`; resource-backed paths return stable unsupported errors when dictionaries are unavailable. S11-13 adds async-compatible `storages` and callable `sqlite` facades over the controlled `storage/storages` and `database/sqlite` bridge modules. S11-14 adds readable debugging compatibility for `console`, Node timer globals, and source-map-aware error stack output; Android floating-console controls are represented as no-op output-capture shims or stable unsupported calls. S11-16 adds the first selector/accessibility compatibility surface: `compat.text("OK").findOne(1000)`, `find()`, selector `click()`, and coordinate `click/back/home` wrappers are Promise-returning APIs over the checked `accessibility` bridge. S11-17 exposes `compat.app`, `compat.device`, and callable `compat.shell(command, optionsOrRoot)` over the controlled app/device/shell bridges; shell root remains opt-in via `true` or `{ root: true }` and Shizuku remains unsupported. S11-18 exposes `compat.images` with Rhino-style `read`, `save`, region/size overloads, image matching, color search, and explicit `recycle()` over opaque image handles. S11-19 adds Promise-based `requestScreenCapture()` and `captureScreen()` compatibility wrappers over `media_projection`/`images`; permission denial remains a bridge error and packaged fallback does not prompt directly. S11-20 adds callable `compat.ocr` declarations for ML Kit-only OCR over path/handle/capture image inputs with JSON-safe results. P12-28 adds the direct controlled `barcode` provider over ML Kit barcode scanning with JSON-safe snapshots and provider-owned image handles; P12-29 adds Rhino-style `compat.barcode` and QR-only `compat.qrcode` facades over that provider. P12-32 adds the low-risk pure JS `s13n.bytes` pilot through `require("s13n")` and `compat.s13n.bytes`, while `s13n.color`, `point`, `throwable`, and `time` stay stable unsupported until separate JSON-safe contracts exist. P12-03 adds pure Rhino `events.keys` constants through `compat.keys` and `compat.events.keys`; explicit global installation may expose `keys`, but does not replace Node's core `events` module or add Android key observers.

P12-15 locks the `compat.tasks` v1 scope to disposable one-shot add/remove/query wrappers over `work_manager`; P12-16 exposes that module-only facade as `require("autojs6:compat").tasks`. P12-17 adds typed stable rejection stubs for `addDailyTask()`, `addWeeklyTask()`, and `updateTask()`, plus `queryTimedTasks()`, `getTimedTask(id)`, and `removeTimedTask(id)` aliases for Node-owned WorkManager one-shot snapshots and ids only. P12-18 adds typed stable rejection stubs for `addIntentTask()`, `queryIntentTasks()`, `getIntentTask(id)`, and `removeIntentTask(id)`. This declaration package still does not expose `require("tasks")` or a global `tasks`.

P13-21 records the partial task database provider gate for these declarations.
The TypeScript smoke now keeps `work_manager` scheduling and `compat.tasks`
one-shot aliases type-checked while daily/weekly/update/IntentTask helpers stay
typed as stable rejecting `Promise<never>` APIs until a dedicated task database
provider lands.

P13-22 records the partial IntentTask/broadcast provider gate. The TypeScript
smoke keeps `addIntentTask()`, `queryIntentTasks()`, `getIntentTask(id)`, and
`removeIntentTask(id)` typed as stable rejecting `Promise<never>` APIs while
receiver registration, trigger payloads, manifest mutation, packaged behavior,
and raw Android broadcast objects remain unavailable.

P13-23 records the partial app/intent/broadcast parity gate. `app.intent()`
returns frozen JSON-safe descriptors for `app.startActivity()`, descriptor
types cover `category`/`categories`, numeric flag arrays, and primitive extras,
and `app.sendBroadcast()` is typed as `Promise<never>` until broadcast authority,
root/Shizuku separation, packaged smoke, and migration examples land.

P13-24 records the partial shell/root/Shizuku capability gate. `shell.execRoot()`
and `compat.shell.execRoot()` are typed over the existing controlled
`shell.exec(..., { root: true })` path, while `shell.execShizuku()` and
`compat.shell.execShizuku()` are stable rejecting `Promise<never>` APIs until a
real Shizuku provider has authorization, lifecycle, cleanup, and packaged
evidence.

P13-25 records the partial notifications/settings/power manager gate.
`notifications.getPermissionStatus()`, `notifications.openSettings()`,
`notifications.openNotificationSettings()`,
`device.isIgnoringBatteryOptimizations()`, and
`device.openBatteryOptimizationSettings()` are typed with JSON-safe result
schemas. Exact `notifications.settings` and `device.power` capabilities are
required for settings/power helpers; notification actions/listeners, wakelocks,
direct power-manager control, and packaged disclosure remain future work.

P13-26 records the partial media/recorder/mediainfo provider gate.
`media.getAudioStreamVolume()`, `media.getAudioStreamMaxVolume()`,
`media.getAudioStreamInfo()`, `mediainfo.read()`, `mediainfo.get()`, and
`recorder.getStatus()` are typed with JSON-safe result schemas. Exact
`media.audio`, `media.metadata`, and `media.recording` capabilities are
required for the promoted helpers; playback, real recorder sessions, MediaStore,
packaged media assets, and foreground microphone disclosure remain future work.

P13-27 records the partial utility-heavy Rhino modules gate. `jsox.mathx` is
typed as a pure JavaScript `Mathx` facade through `require("jsox")`,
`require("jsox.mathx")`, and `autojs6:compat.jsox`. `jsox.extend(target,
"Mathx")` and `jsox.extendAll(target)` require explicit targets; default global
`Math`/prototype mutation, `zip`, `canvas`, OpenCC/pinyin packaged resources,
`sysprops`, `Arrayx`, `Numberx`, and raw Android/OpenCV object parity remain
future work.

P13-29 records the partial HTTP/HTTPS client parity gate. `http`/`node:http`
and `https`/`node:https` are typed as controlled client facades over AutoJs6
fetch: `request()`, `get()`, `ClientRequest`, `IncomingMessage`, and safe
shape-only `Agent` fields are declared. Server/listen, raw sockets, proxy and
tunnel options, custom TLS/session control, and packaged live-network behavior
remain future work.

P13-30 records the partial net/tls/dns staged compatibility gate. `net`,
`node:net`, `tls`, `node:tls`, `dns`, `node:dns`, `dns/promises`, and
`node:dns/promises` are typed as limited builtins for package probing and
deterministic local behavior: socket/server shapes, `SocketAddress`,
`BlockList`, TLS context/socket metadata, DNS loopback/IP-literal lookup, and
DNS constants are declared. Real TCP/TLS sockets, server listeners, network DNS
queries, Android resolver state, TLS sessions, and fd/native handles remain
future work.

P12-24 reserves `require("rhino")` as a disabled JSON-only shim facade. P12-25 adds an explicit `rhino.run({ experimental: true, source|path, timeoutMs, args })` JSON-only POC that returns frozen `RunResult` records and never installs Rhino globals. Calls without `experimental: true` still reject with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`; default `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`, `require("node:rhino")` is not an alias, and no `compat.rhino` surface is declared. P13-14 adds typed `rhino.install({ experimental: true, globals?, target? })` support for the explicit Java proxy POC only: the returned `InstallResult` records `Packages`, `java`, and `android` proxy installation through the Java interop allowlist, while `JavaAdapter` remains unavailable. P13-15 declares `rhino.importClass()` and `rhino.importPackage()` as stable `never` helpers that throw `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`; migration should use assignment from an explicit proxy target. P13-16 declares `rhino.JavaAdapter()` as a stable `never` helper that throws `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`, and reserves `java.defineClass()` / `$autojs.java.defineClass()` as stable-denied replacement names until a bounded proxy-interface provider exists.

`input_observer` is a P12 provider POC. It exposes `observeKeys`, `drainEvents`, `close`, and `getAvailableSources` against fake-source JSON snapshots and the P12-06 `accessibility` live-source gate. Live key observation requires an operational AutoJs6 accessibility service and still remains subject to P12-07 cleanup/security/packaged policy evidence.

P13-17 records the live `require("ui")` provider promotion gate for the
declared `ui` types. Direct `ui.showLayout()` and `compat.ui.showLayout()` are
typed over the same JSON-only Activity provider, but live packaged UI remains
provider-dependent until Back/Recents, orientation, relaunch/crash, and
disclosure evidence lands.

`ui.overlay` is a P12-13 fake-provider POC over the P12-12A overlay contract. It exposes JSON-only `show`, `update`, `drainEvents`, `close`, `closeAll`, `hasPermission`, and `openPermissionSettings` shapes with opaque handles. P12-14 adds the Android live gate for permission snapshots, explicit settings dry-run/launch evidence, packaged `SYSTEM_ALERT_WINDOW` metadata, foreground disclosure requirements, and cleanup summaries; live overlay creation remains fail-closed.

P13-18 records the partial overlay provider promotion gate for these
declarations. The TypeScript smoke sample imports `ui.overlay` and checks
permission, settings, closeAll, show, handle, and event types, while Android
live `show`/`update`/`drainEvents`/per-handle `close` remains fail-closed until
a promoted overlay provider lands.

P13-19 records the partial accessibility selector promotion gate for the
declared `accessibility` and explicit `autojs6:compat` selector types. Direct
selector descriptors, frozen node snapshots, selector find/action calls, Rhino
compat selector handles, and unavailable-provider rejection are covered by
host/device/type evidence. `swipe`, `gesture`, `powerDialog`, direct
`waitFor`, broader Rhino selector aliases, packaged accessibility service
lifecycle, and root/Shizuku tiers remain unpromoted.

P13-20 records the partial screenshot/image/OCR promotion gate for the declared
`media_projection`, `image`/`images`, `ocr`, and explicit `autojs6:compat`
image/OCR types. Direct API shape, scoped image read/recycle, ML Kit OCR over
provider-owned image handles, Rhino-style screenshot/image/OCR wrappers, and
raw object denials are covered by host/device/type evidence. MediaProjection
bridge prompting, advanced image processing, packaged capture/OCR behavior,
Paddle/Rapid fallback, and raw Bitmap/OpenCV profile decisions remain
unpromoted.

P12-34 is a documentation synchronization gate rather than a new declaration
surface. It keeps this README aligned with `MIGRATION_FROM_RHINO.md`, the Pro 9
comparison, `RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`, `SECURITY_MODEL.md`, and
`TESTING.md`: promoted APIs keep examples, stable unsupported calls keep their
error codes, and the P12-33 fake dual-engine device evidence remains testing
evidence only.

They intentionally do not declare unrestricted Java interop, native plugin
loading, Android plugin adapters, or unrestricted desktop Node.js capabilities.
The declarations are checked by `:app:verifyAutoJs6NodeTypeDeclarations`; the
sample project at `sample/nodejs/typescript-smoke` can be type-checked with:

```powershell
tsc -p sample/nodejs/typescript-smoke/tsconfig.json --pretty false
```
