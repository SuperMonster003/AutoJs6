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
- `package_manager` (guarded mutation partial)
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

P13-55 adds `profile_capabilities.d.ts`, a declaration-only matrix for
Safe/Pro/Desktop/Debug profile differences. The matrix covers the P13-53 and
P13-54 example surfaces including `$autojs.java`, `java`,
`rhino.install`, UI, overlay, tasks, screenshot/OCR, accessibility, ESM,
`worker_threads`, HTTP/HTTPS, raw network, advanced fs, WASI, inspector, and
npm dependency usage. It provides `RuntimeProfileId`, `ProfileModuleCatalog`,
`ProfileModuleModeFor`, `ProfileRequiredFor`, and related conditional helpers.
These types document profile requirements and Safe-profile denial behavior;
they do not declare denied desktop modules as callable modules or grant
runtime authority.
The profile declaration matrix does not declare denied desktop modules as callable modules.

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
reports native VM availability, non-sandbox boundaries, and the focused
Desktop/Debug evidence for repeated compiled scripts, native timeout
interruption, bridge and host object non-injection, scoped-fs preservation, and
packaged fixture behavior. Runtime now routes `require("vm")` and
`require("node:vm")` to Node's native VM builtin.

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
`require("wasi")` or make WASI APIs callable. For P13-38 v1.1, pure
WebAssembly is the supported runtime path and raw/controlled WASI APIs stay
stable-denied.

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
controlled JavaScript/JSON `data:` URL imports, source-name stack behavior, and
focused packaged ESM evidence. It also records raw loader hooks, raw
`node:module` loader authority, network/file/unrestricted URL imports,
working-directory escapes, and raw Node loader handles as denied.

P13-42 extends the `autojs6:profile` declaration with
`packageManagerProfile`. The field reports the current partial Android package
manager boundary: host-managed package validation is available, the Android
runtime has a local app-private store substrate for already-unpacked packages,
and the `package_manager` module exposes status/policy/sourcePolicy,
dry-run `planInstall`/`planUpdate`/`planRemove`, policy-only
`planRegistryInstall`/`planTarballInstall` returning `RemotePackagePlan` with
frozen remote plan dist-tag/semver/tarball/integrity resolution diagnostics,
guarded `install`/`update`/`remove`, plus `list`, `verify`, and `prune` for
the app-private store. The `npm` module is also declared as a guarded
package-manager alias facade; it exposes the same local-store plan/mutation,
remote source-boundary plan, and maintenance calls while keeping
CLI/registry/lifecycle APIs denied. The source policy records
`local_unpacked_host_managed` as the only allowed substrate.
npm CLI, registry download, remote tarball install, lifecycle scripts, bin
links, symlinks, native builds, native payloads, postinstall downloads,
unrestricted mutation, raw executables, and raw registry access remain denied.

P13-43 extends the `autojs6:profile` declaration with `stdlibProfile`. The
field reports the current partial Safe Node stdlib conformance subset for
`crypto`, streams, zlib, Buffer, URL, OS, readline, TTY, `perf_hooks`, and
`async_hooks`; it also records stable Android differences and keeps raw desktop
parity, raw native handles, unrestricted network behavior, broad terminal
control, raw `process.getBuiltinModule` bypass behavior, and `zlib/promises`
denied. Controlled `process.getBuiltinModule` returns the same limited wrappers
as `require()`.

P13-44 extends the `autojs6:profile` declaration with `processParityProfile`.
The field reports the current partial process/env/argv subset: controlled
`process.env`, script-local env mutation, scoped cwd/chdir, `process.argv`,
`exitCode`, versions, timing, resource snapshots, permission/report
diagnostics, bounded stdio, lifecycle-mapped `process.exit(code)`, and stable
denials for native process exit, signals, native bindings, raw inherited env,
and full desktop process parity.

P13-45 extends the `autojs6:profile` declaration with
`packagedCapabilityProfile` and makes `bridgePermissions.packagedMetadata`
typed. The fields report packaged profile, builtin, native asset, filesystem
root, execution-mode, network, and Android permission metadata as
diagnostic-only; authority still comes from declared bridge permissions and
future reviewed packaged profile gates.

P13-46 extends the `autojs6:profile` declaration with `pluginSystemProfile`.
The field reports the current JS-only local plugin manager, tracked operations,
host capability ceiling, packaged local-file status, debug-only reload, and
stable denied/deferred Android provider plugin, native plugin, remote
marketplace, signed archive, and installer paths.

P13-48 extends the `autojs6:profile` declaration with
`packageIntegrityProfile`. The field reports the shared package lock and
integrity policy for Android package manager, plugin packages, and native addon
artifacts: `autojs6-lock.json`, SHA-512 integrity, signature requirements,
rollback/cache cleanup/corruption recovery semantics, and fail-closed unsigned
plugin/native install, raw registry install, lifecycle script, postinstall
download, and unverified cache reuse decisions.

P13-49 extends the `autojs6:profile` declaration with `profileSelection` and
adds the `AutoJsBridgeCapabilityNotDeclaredError` shape. These diagnostics
report requested Safe/Pro/Desktop/Debug profiles, declared Node permissions,
Android permissions, high-risk capabilities, blocked promotions, and
`profileSelectionHint` details for missing capability failures, including
typed `declarationExamples` snippets for `node.permissions`, project
`node.permissions`, and package `autojs6.node.permissions`. They do not promote
any effective profile beyond `safe_default`.

P13-51 extends the `autojs6:profile` declaration with `profileRollbackPolicy`.
The field reports per-profile kill switches, per-capability rollback actions,
crash/stress/security/package triggers, and packaged APK downgrade guidance.
It remains `playbook_partial` and `metadata_only_no_authority`; scripts can
inspect the rollback plan, but it does not promote Pro/Desktop/Debug authority.

P13-52 extends the `autojs6:profile` declaration with
`privacyDisclosurePolicy`. The field reports sensitive surface prompt
requirements, foreground disclosure/notification requirements, Android
permission/settings metadata, packaged disclosure manifest requirements, and
Doctor privacy redaction metadata. It remains `policy_partial` and
`metadata_only_no_authority`; scripts can inspect disclosure requirements, but
it does not grant implicit high privilege.

The `autojs6:compat` module is the explicit Rhino compatibility loader. It does not install Rhino globals by default; scripts must opt in before later Phase 11 facade modules can expose Rhino-style helpers. S11-11 adds the pure utility subset for `base64`, `colors`, `fmt.bytes`, `cvt.bytes`, `mime`, `nanoid`, and Node `util.inspect` migration. S11-12 adds text utility facades for `opencc`, `pinyin`, and `pinyin4j`; resource-backed paths return stable unsupported errors when dictionaries are unavailable. S11-13 adds async-compatible `storages` and callable `sqlite` facades over the controlled `storage/storages` and `database/sqlite` bridge modules. S11-14 adds readable debugging compatibility for `console`, Node timer globals, and source-map-aware error stack output; Android floating-console controls are represented as no-op output-capture shims or stable unsupported calls. S11-16 adds the first selector/accessibility compatibility surface: `compat.text("OK").findOne(1000)`, `find()`, selector `click()`, and coordinate `click/back/home` wrappers are Promise-returning APIs over the checked `accessibility` bridge. S11-17 exposes `compat.app`, `compat.device`, and callable `compat.shell(command, optionsOrRoot)` over the controlled app/device/shell bridges; shell root remains opt-in via `true` or `{ root: true }` and Shizuku remains unsupported. P13-23 adds URL/email/settings app helpers through the existing scoped activity bridge plus pure shell serialization and exact installed-app lookup for migration scripts: `url` descriptors, short `VIEW`/`BROWSABLE` constants, `app.intentToShell()`, `app.openUrl()`, `app.sendEmail()`, `app.uninstall()`, `app.getPackageName()`, `app.getAppName()`, `app.isInstalled()`, JSON-safe `app.parseUri()`, and settings aliases. Raw URI/FileProvider, kill, and dual-user app helpers are typed as stable fail-closed stubs until explicit capability routing exists. S11-18 exposes `compat.images` with Rhino-style `read`, `save`, region/size overloads, image matching, color search, and explicit `recycle()` over opaque image handles. S11-19 adds Promise-based `requestScreenCapture()` and `captureScreen()` compatibility wrappers over `media_projection`/`images`; permission denial remains a bridge error and packaged fallback does not prompt directly. S11-20 adds callable `compat.ocr` declarations for ML Kit-only OCR over path/handle/capture image inputs with JSON-safe results. P12-28 adds the direct controlled `barcode` provider over ML Kit barcode scanning with JSON-safe snapshots and provider-owned image handles; P12-29 adds Rhino-style `compat.barcode` and QR-only `compat.qrcode` facades over that provider. P12-32 adds the low-risk pure JS `s13n.bytes` pilot through `require("s13n")` and `compat.s13n.bytes`, while `s13n.color`, `point`, `throwable`, and `time` stay stable unsupported until separate JSON-safe contracts exist. P12-03 adds pure Rhino `events.keys` constants through `compat.keys` and `compat.events.keys`; explicit global installation may expose `keys`, but does not replace Node's core `events` module or add Android key observers.

P13-23 also exposes scoped `app.viewFile()` / `app.editFile()` helpers for relative files or scoped absolute files inside the Node working directory, plus `app.launch()` as a `launchPackage()` alias and `app.parseUri()` as a JSON-safe URI descriptor helper.

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

P13-23 records the bounded app/intent/broadcast parity gate as ready. `app.intent()`
returns frozen JSON-safe descriptors for `app.startActivity()`,
`app.intentToShell()` serializes the same descriptors into `am` intent
arguments without dispatching the Android bridge, descriptor types cover
`category`/`categories`, numeric flag arrays, and primitive extras,
`app.uninstall()` opens the system uninstall flow through the existing activity
bridge, exact `app.getPackageName()`, `app.getAppName()`, and
`app.isInstalled()` lookups require `app.query` and return JSON-safe
`Promise<string | null>` / `Promise<boolean>` values, `app.viewFile()` and
`app.editFile()` resolve relative paths or scoped absolute paths inside the
working directory before launching Android file Activity intents,
`app.launch()` aliases `app.launchPackage()`, `app.parseUri()` returns frozen
JSON-safe URI descriptors, and `app.getUriForFile()` / `app.kill()` /
dual-user helpers / `app.sendBroadcast()` are typed as stable denials until raw
URI/FileProvider policy, broadcast authority, and root/Shizuku separation land
under their dedicated provider gates. Packaged scoped file Activity routing is
covered by the focused packaged app bridge smoke.

P13-24 records the bounded shell/root/Shizuku capability gate. `shell.execRoot()`
and `compat.shell.execRoot()` are typed over the existing controlled
`shell.exec(..., { root: true })` path, while `shell.execShizuku()` and
`compat.shell.execShizuku()` are stable rejecting `Promise<never>` APIs for
v1.1 until a dedicated Shizuku provider owns authorization, lifecycle, cleanup,
packaged disclosure, rollback, and device matrix evidence.

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

P13-27 records the partial utility-heavy Rhino modules gate. `jsox.mathx`,
`jsox.arrayx`, and `jsox.numberx` are typed as pure JavaScript facades through
`require("jsox")`, direct submodule requires, and `autojs6:compat.jsox`.
`jsox.extend(target, "Mathx" | "Arrayx" | "Numberx")` and
`jsox.extendAll(target)` require explicit targets; default global
`Math`/prototype mutation, `zip`, `canvas`, OpenCC/pinyin packaged resources,
`sysprops`, and raw Android/OpenCV object parity remain future work.

P13-29 records the controlled HTTP/HTTPS client subset as ready. `http`/`node:http`
and `https`/`node:https` are typed as controlled client facades over AutoJs6
fetch: `request()`, `get()`, `ClientRequest`, `IncomingMessage`, and safe
shape-only `Agent` fields are declared. Server/listen, raw sockets, proxy and
tunnel options, custom TLS/session control, and broader packaged install/run
network behavior remain future work.

P13-30 records the partial net/tls/dns staged compatibility gate. By default,
`net`, `node:net`, `tls`, `node:tls`, `dns`, `node:dns`, `dns/promises`, and
`node:dns/promises` are typed as limited builtins for package probing and
deterministic local behavior: socket/server shapes, `SocketAddress`,
`BlockList`, TLS context/socket metadata, DNS loopback/IP-literal lookup,
`resolve(hostname, rrtype)`, TTL resolve options, TLSA and other record result
shapes, and DNS constants are declared. Debug/build-time runs can explicitly
enable native Node `dns`, `dns/promises`, `http`, `https`, `net`, and `tls`
with `autojs.nodejs.rawNetworkModules.experimental=true`; broader release
promotion, server listeners, Android resolver policy, fd/native-handle proof,
and packaged live-network behavior remain future work.

P12-24 reserves `require("rhino")` as a disabled JSON-only shim facade. P12-25 adds an explicit `rhino.run({ experimental: true, source|path, timeoutMs, args })` JSON-only POC that returns frozen `RunResult` records and never installs Rhino globals. Calls without `experimental: true` still reject with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`; default `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`, `require("node:rhino")` is not an alias, and no `compat.rhino` surface is declared. P13-14 adds typed `rhino.install({ experimental: true, globals?, target? })` support for the explicit Java proxy POC only: the returned `InstallResult` records `Packages`, `java`, and `android` proxy installation through the Java interop allowlist, while `JavaAdapter` remains unavailable. P13-15 declares `rhino.importClass()` and `rhino.importPackage()` as stable `never` helpers that throw `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`; migration should use assignment from an explicit proxy target. P13-16 declares `rhino.JavaAdapter()` as a stable `never` helper that throws `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`, and reserves `java.defineClass()` / `$autojs.java.defineClass()` as stable-denied replacement names until a bounded proxy-interface provider exists.

`input_observer` is a P12 provider POC. It exposes `observeKeys`, `drainEvents`, `close`, and `getAvailableSources` against fake-source JSON snapshots and the P12-06 `accessibility` live-source gate. Live key observation requires an operational AutoJs6 accessibility service and still remains subject to P12-07 cleanup/security/packaged policy evidence.

P13-17 records the live `require("ui")` provider promotion gate for the
declared `ui` types. Direct `ui.showLayout()` and `compat.ui.showLayout()` are
typed over the same JSON-only Activity provider, but live packaged UI remains
provider-dependent until Back/Recents, relaunch/crash, and disclosure evidence
lands. Configuration changes use an explicit destroy/recreate policy with no
live UI survival claim.

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
`waitFor`, packaged accessibility service lifecycle, and root/Shizuku tiers
remain unpromoted.

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
