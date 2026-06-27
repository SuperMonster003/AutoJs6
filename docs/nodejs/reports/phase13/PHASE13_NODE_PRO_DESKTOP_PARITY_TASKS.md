# Phase 13: Auto.js Pro And Desktop Node Parity Expansion Tasks

## Closeout Breakdown

- Detailed closeout decomposition:
  `docs/nodejs/reports/phase13/PHASE13_CLOSEOUT_BREAKDOWN.md`.
- Open Phase 13 parent tasks generally remain open because their partial
  landings still need one or more of runtime implementation, connected-device
  evidence, packaged APK evidence, security/product policy, rollback/privacy
  behavior, or external native/runtime availability.
- Parent tasks should not be checked complete until their verifier reports zero
  blockers, required evidence is archived, profile/capability docs are synced,
  stable-denial paths remain covered, and `generateNodePhase13Summary` no
  longer reports the task as partial.

## P13-20 Partial Landing Note

- P13-20 partial screenshot/image/OCR promotion gate records direct
  `media_projection`, `image`/`images`, `ocr`, and explicit
  `autojs6:compat` screenshot/image/OCR surfaces.
- Host verifier:
  `tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js`.
- Gradle gate: `:app:verifyNodePhase13ScreenshotImageOcrPromotion`.
- Report:
  `docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md`.
- Surface manifest evidence: `phase13-screenshot-image-ocr-promotion`.
- Remaining P13-20 blockers: Activity-owned MediaProjection prompting,
  foreground disclosure, capturer cleanup, advanced image processing,
  and packaged capture/OCR behavior.
- Paddle/Rapid/plugin OCR provider selection and raw Bitmap/OpenCV/
  MediaProjection object parity stay stable-denied for P13-20 v1.1.

## P13-21 Partial Landing Note

- P13-21 partial task database provider gate records the current
  `work_manager`, explicit `autojs6:compat.tasks`, and Android/Rhino
  `TimedTask` database substrate.
- Host verifier: `tools/nodejs/phase13/verify-task-database-provider.js`.
- Gradle gate: `:app:verifyNodePhase13TaskDatabaseProvider`.
- Report: `docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md`.
- Surface manifest evidence: `phase13-task-database-provider`.
- Remaining P13-21 blockers: JSON task database schema, main-app vs packaged
  ownership, add/query/update/remove daily/weekly operations, wall-clock
  scheduler semantics, packaged metadata/manifest behavior, and raw
  TimedTask/IntentTask profile decisions.

## P13-22 Partial Landing Note

- P13-22 partial IntentTask/broadcast provider gate records the current
  Android/Rhino `IntentTask`, `IntentTaskDatabase`, `BaseBroadcastReceiver`,
  `StaticBroadcastReceiver`, and `DynamicBroadcastReceivers` substrate plus the
  existing `autojs6:compat.tasks` IntentTask rejection stubs.
- Host verifier: `tools/nodejs/phase13/verify-intent-broadcast-provider.js`.
- Gradle gate: `:app:verifyNodePhase13IntentBroadcastProvider`.
- Report: `docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md`.
- Surface manifest evidence: `phase13-intent-broadcast-provider`.
- For P13-22 v1.1, `compat.tasks` IntentTask helpers remain
  Promise-returning stable rejection stubs until a dedicated JSON-safe broadcast
  provider owns the future surface.
- Remaining P13-22 blockers: capability split, receiver authority,
  exported/non-exported policy, trigger payload serialization, packaged
  manifest synthesis, install/trigger/remove smoke, user disclosure, and raw
  Intent/BroadcastReceiver profile decisions.

## P13-23 Partial Landing Note

- P13-23 partial app/intent/broadcast Pro parity gate records JSON-safe
  `app.intent()` descriptors, stricter `app.startActivity()` descriptor
  parsing, and stable `app.sendBroadcast()` rejection.
- Follow-up helper parity records short `action`/`category` normalization,
  `url` descriptor aliases, URL string `app.startActivity()` input,
  pure `app.intentToShell()` shell-argument serialization, `app.openUrl()`,
  `app.sendEmail()`, `app.uninstall()`, exact `app.query` installed-app lookup
  helpers, scoped `app.viewFile()` / `app.editFile()` Activity helpers,
  settings aliases, `app.launch()` as a `launchPackage()` alias, JSON-safe
  `app.parseUri()`, and stable fail-closed stubs for raw URI, kill, and
  dual-user app helpers backed by the existing scoped activity/settings bridge.
- P13-23 keeps `app.sendBroadcast()`, raw Android `Uri` / `FileProvider`
  handles, dual-user helpers, and root/Shizuku app tiers stable-denied for
  v1.1; future promotion belongs under P13-22/P13-24 gates.
- Host verifier:
  `tools/nodejs/phase13/verify-app-intent-broadcast-parity.js`.
- Gradle gate: `:app:verifyNodePhase13AppIntentBroadcastParity`.
- Report:
  `docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md`.
- Surface manifest evidence: `phase13-app-intent-broadcast-parity`.
- Remaining P13-23 blockers: raw URI/FileProvider promotion, raw installed-app
  list APIs, broadcast send promotion, root/Shizuku/dual-user routing through
  explicit capabilities, packaged smoke, negative device corpus, preset alias
  mapping, and migration examples.

## P13-24 Partial Landing Note

- P13-24 partial shell/root/Shizuku capability gate records explicit
  `shell.root` and `shell.shizuku` capability vocabulary, controlled
  `shell.exec()` resource ownership, `shell.execRoot()` routing through root
  capability, and stable `shell.execShizuku()` rejection.
- Host verifier:
  `tools/nodejs/phase13/verify-shell-root-shizuku-capability.js`.
- Gradle gate: `:app:verifyNodePhase13ShellRootShizukuCapability`.
- Report:
  `docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md`.
- Surface manifest evidence: `phase13-shell-root-shizuku-capability`.
- For P13-24 v1.1, `shell.execShizuku()` and `shell.exec(..., { shizuku: true })`
  remain stable-denied; `shell.shizuku` is auditable vocabulary only until a
  dedicated Shizuku provider owns authorization, lifecycle, cleanup, packaged
  disclosure, and rollback evidence.
- Remaining P13-24 blockers: root device matrix, AbortSignal cancellation
  instrumentation, packaged disclosure/failure diagnostics, expanded audit
  taxonomy, and migration examples.

## P13-25 Partial Landing Note

- P13-25 partial notifications/settings/power manager gate records
  `notifications.settings` and `device.power` as explicit capability tokens.
  Node now exposes notification permission/settings snapshots plus battery
  optimization status/settings dry-run helpers without exposing raw Android
  notification or power manager objects.
- Host verifier:
  `tools/nodejs/phase13/verify-notifications-settings-power-manager.js`.
- Gradle gate: `:app:verifyNodePhase13NotificationsSettingsPowerManager`.
- Report:
  `docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md`.
- Surface manifest evidence: `phase13-notifications-settings-power-manager`.
- Remaining P13-25 blockers: notification actions/listeners/rich layouts,
  Do-Not-Disturb/exact-alarm policy, direct wakelock/power-manager APIs, OEM
  power behavior matrix, packaged disclosure/run evidence, and migration
  examples.

## P13-26 Partial Landing Note

- P13-26 partial media/recorder/mediainfo provider gate records `media`,
  `media.audio`, `media.metadata`, and `media.recording` as explicit
  capability tokens. Node now exposes read-only audio stream snapshots,
  scoped `mediainfo` JSON-safe metadata, and recorder status while
  `recorder.start()`/`stop()` remain fail-closed.
- Host verifier:
  `tools/nodejs/phase13/verify-media-recorder-mediainfo-provider.js`.
- Gradle gate: `:app:verifyNodePhase13MediaRecorderMediainfoProvider`.
- Report:
  `docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md`.
- Surface manifest evidence: `phase13-media-recorder-mediainfo-provider`.
- Remaining P13-26 blockers: audio playback, real recorder sessions,
  foreground notification disclosure, MediaStore permissions/policy, packaged
  media asset lookup, corpus evidence, and migration examples.

## P13-27 Partial Landing Note

- P13-27 v1.1 pure utility helper subset gate records the pure
  `jsox.mathx`, `jsox.arrayx`, and `jsox.numberx` facade subsets through
  `require("jsox")`, direct submodule requires, and explicit
  `autojs6:compat.jsox`.
- Host verifier:
  `tools/nodejs/phase13/verify-utility-heavy-rhino-modules.js`.
- Gradle gate: `:app:verifyNodePhase13UtilityHeavyRhinoModules`.
- Report:
  `docs/nodejs/reports/phase13/P13_27_UTILITY_HEAVY_RHINO_MODULES.md`.
- Surface manifest evidence: `phase13-utility-heavy-rhino-modules`.
- Raw Android/OpenCV point/rect object parity is stable-denied for P13-27.
- Future P13-27 resource-backed utility work: `zip`, `canvas` JSON drawing provider/raw
  Canvas/Paint decision, OpenCC packaged resources, pinyin full dictionaries,
  Rhino `util` split batches, and `sysprops`.

## P13-29 Partial Landing Note

- P13-29 partial HTTP/HTTPS client parity gate records the existing
  controlled `http`/`https` client facade as a Desktop-profile compatibility
  subset, backed only by AutoJs6 controlled fetch.
- Follow-up runtime policy propagation now treats
  `autojs.nodejs.network.experimental=true` as an explicit AutoJs6 network
  policy for the controlled `http`/`https` facade, and merges package manifests
  from both runtime working directory and sandbox root for subdirectory `.mjs`
  utilities.
- Follow-up compatibility pass raises the controlled fetch concurrency budget
  to 16 active requests by default (hard max 32) and the default response-body
  budget to 32 MiB (hard max 64 MiB), while preserving explicit per-request
  lower `maxResponseBytes` denials.
- Follow-up corpus expansion grows the offline HTTP npm corpus from six to
  nine fixtures with follow-redirects-style redirect handling, TLS provider
  failure handling, and per-request proxy configuration denial.
- Host verifier: `tools/nodejs/phase13/verify-http-https-client-parity.js`.
- Gradle gate: `:app:verifyNodePhase13HttpHttpsClientParity`.
- Report:
  `docs/nodejs/reports/phase13/P13_29_HTTP_HTTPS_CLIENT_PARITY.md`.
- Surface manifest evidence: `phase13-http-https-client-parity`.
- Remaining P13-29 blockers: packaged INTERNET permission evidence, live
  provider HTTP smoke, raw socket/server/listen policy, DNS/TLS session
  parity, and proxy support decision.

## P13-30 Partial Landing Note

- P13-30 partial net/tls/dns staged compatibility gate records the existing
  limited `net`, `tls`, `dns`, and `dns/promises` builtins as Desktop-profile
  shape and policy evidence, not as raw network promotion.
- Follow-up DNS parity adds Node v24 `resolveTlsa`, `resolve(hostname,
  rrtype)`, TTL resolve options, typed DNS record declarations, and exposed DNS
  error constants while keeping all network DNS requests fail-closed.
- Raw native Node network modules now have a debug/build-time opt-in:
  `autojs.nodejs.rawNetworkModules.experimental=true`. With this flag, CommonJS
  `dns`, `dns/promises`, `http`, `https`, `net`, and `tls` route to Node's
  native builtins; Safe default remains limited and fail-closed.
- Host verifier:
  `tools/nodejs/phase13/verify-net-tls-dns-staged-compatibility.js`.
- Gradle gate: `:app:verifyNodePhase13NetTlsDnsStagedCompatibility`.
- Report:
  `docs/nodejs/reports/phase13/P13_30_NET_TLS_DNS_STAGED_COMPATIBILITY.md`.
- Surface manifest evidence: `phase13-net-tls-dns-staged-compatibility`.
- Remaining P13-30 blockers: real client socket authority, server socket
  decision, connection/byte/timeout/background policy, DNS privacy review,
  TLS session/certificate policy, fd leak instrumentation, packaged INTERNET
  proof, and live Android network evidence.

## P13-31 Stable-Denied Landing Note

- P13-31 stable-denied landing records `dgram` / `node:dgram` as not entering
  the current Node Profile v1.1 Desktop track.
- Host verifier: `tools/nodejs/phase13/verify-dgram-udp-decision.js`.
- Gradle gate: `:app:verifyNodePhase13DgramUdpDecision`.
- Report: `docs/nodejs/reports/phase13/P13_31_DGRAM_UDP_DECISION.md`.
- Surface manifest evidence: `phase13-dgram-udp-decision`.
- Remaining P13-31 blockers: future UDP provider design, unicast client
  policy, server bind/listen policy, multicast/broadcast capability split,
  packaged INTERNET/multicast permission proof, fd leak instrumentation, and
  live Android UDP evidence.

## P13-32 Safe Scoped Filesystem Note

- P13-32 v1.1 Safe scoped filesystem profile gate records the current Safe profile as
  keeping `process.cwd()` at the script working directory while allowing a
  distinct scoped `sandboxRoot` for the containing AutoJs6 workspace/project
  root, and exposes a frozen diagnostic `filesystemProfile` snapshot without
  adding any external root authority.
- Scoped absolute paths under `workingDirectory` are now allowed for Node/npm
  package resource loading patterns such as `path.join(__dirname, "file.css")`;
  absolute paths outside the sandbox remain rejected.
- Scripts launched from a subdirectory of the AutoJs6 working directory can now
  read parent files that remain inside the workspace, for example
  `../version.properties` from `$cwd/utils`.
- Host verifier:
  `tools/nodejs/phase13/verify-filesystem-profile-decision.js`.
- Gradle gate: `:app:verifyNodePhase13FilesystemProfileDecision`.
- Report:
  `docs/nodejs/reports/phase13/P13_32_FILESYSTEM_PROFILE_DECISION.md`.
- Surface manifest evidence: `phase13-filesystem-profile-decision`.
- Future P13-32 expanded-root work: explicit root authority provider,
  user-authorized directory grants, SAF/document roots, app-private roots,
  `/sdcard` expansion, grant persistence/revocation diagnostics, packaged
  expanded-root smoke, and per-root symlink/realpath/sensitive-path audit.

## P13-33 Scoped Advanced Filesystem Note

- P13-33 v1.1 scoped advanced filesystem API gate records scoped streams,
  `FileHandle`, fd APIs, `opendir`, watchers, and `realpath` as current partial
  evidence without expanding roots or exposing raw native fds.
- Host verifier: `tools/nodejs/phase13/verify-filesystem-advanced-apis.js`.
- Gradle gate: `:app:verifyNodePhase13FilesystemAdvancedApis`.
- Report: `docs/nodejs/reports/phase13/P13_33_FILESYSTEM_ADVANCED_APIS.md`.
- Surface manifest evidence: `phase13-filesystem-advanced-apis`.
- Android compatibility notes now document watcher coalescing,
  recursive-watch denial, and realpath differences.
- Future P13-33 packaged/expanded-root work: packaged watcher/fd/FileHandle/
  stream lifecycle smoke, device-exported packaged realpath/opendir evidence,
  expanded-root reruns, and final full desktop fs semantics.

## P13-34 Partial Landing Note

- P13-34 partial worker_threads opt-in promotion gate records default denial,
  `workerThreadsProfile` diagnostics, request-local experimental preflight, and
  existing native-available worker/MessageChannel/WorkerPool/resource/packaged
  evidence without promoting workers by default.
  The default Safe profile exposes a `node:worker_threads` shape-only facade for
  npm feature detection (`jsdom` top-level `require("node:worker_threads")`);
  `Worker` construction returns an inert disabled worker for package
  initialization paths, while `postMessage()` / real worker execution still
  reject with `ERR_AUTOJS6_WORKER_DISABLED` by default. If an experimental
  request enables worker_threads on a native-worker-unavailable build, the
  require path now falls back to the same facade with `nativeAvailable=false`
  rather than aborting npm package initialization.
- Host verifier:
  `tools/nodejs/phase13/verify-worker-threads-opt-in-promotion.js`.
- Gradle gate: `:app:verifyNodePhase13WorkerThreadsOptInPromotion`.
- Report:
  `docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md`.
- Surface manifest evidence: `phase13-worker-threads-opt-in-promotion`.
- Remaining P13-34 blockers: native worker availability on target ABIs,
  archived experimental worker gate evidence, packaged `.mjs` worker smoke,
  worker crash evidence, nested-worker policy, and Android resource ceilings.

## P13-35 Partial Landing Note

- P13-35 partial process-worker replacement gate records
  `processWorkerReplacementProfile` diagnostics and decides the current
  replacement path as request-local `worker_threads.WorkerPool`, not a new
  `process_worker` or `compute_worker` facade.
- The gate keeps `child_process` denied, keeps `shell` as a separate scoped
  command bridge, and keeps `worker_computation` plus `second_execution_slot`
  from being promoted by this decision.
- For P13-35 v1.1, no user-facing process-worker facade or UX is introduced;
  P13-40 remains the separate track for any future scoped `child_process`
  runner.
- Host verifier:
  `tools/nodejs/phase13/verify-process-worker-replacement-decision.js`.
- Gradle gate: `:app:verifyNodePhase13ProcessWorkerReplacementDecision`.
- Report:
  `docs/nodejs/reports/phase13/P13_35_PROCESS_WORKER_REPLACEMENT.md`.
- Surface manifest evidence: `phase13-process-worker-replacement-decision`.
- Future promotion work outside P13-35 closure: native WorkerPool evidence on
  target ABIs, process-worker packaging behavior, slot accounting, cleanup
  stress, and launch-surface evidence for any future promoted replacement.

## P13-36 Partial Landing Note

- P13-36 partial `vm` module gate records `vmProfile` diagnostics and routes
  `vm`/`node:vm` to Node's native builtin by default.
- The gate documents the native `vm.Script`, context, `compileFunction`, VM
  module APIs, native timeout options, shadowing protection, and non-sandbox
  boundary for desktop npm compatibility.
- Host verifier: `tools/nodejs/phase13/verify-vm-module-decision.js`.
- Gradle gate: `:app:verifyNodePhase13VmModuleDecision`.
- Report: `docs/nodejs/reports/phase13/P13_36_VM_MODULE_DECISION.md`.
- Surface manifest evidence: `phase13-vm-module-decision`.
- Remaining P13-36 blockers: memory/compiled-script budgets, packaged APK
  smoke evidence, bridge/process/global leakage probes, and timeout/lifecycle
  stress evidence.

## P13-38 Stable-Denied v1.1 Note

- P13-38 records `wasiProfile` diagnostics, pure WebAssembly availability, raw
  `wasi` / `node:wasi` require and resolve denial, and local shadow-package
  protection.
- For P13-38 v1.1, pure WebAssembly is the supported runtime path; raw WASI,
  callable `autojs6:wasi`, and limited `node:wasi` profile APIs stay
  stable-denied until a future Desktop-profile provider owns preopen, fd/env/
  args, resource, worker, hostile-corpus, and packaged behavior evidence.
- Host verifier:
  `tools/nodejs/phase13/verify-controlled-wasi-profile-decision.js`.
- Gradle gate: `:app:verifyNodePhase13ControlledWasiProfileDecision`.
- Report: `docs/nodejs/reports/phase13/P13_38_CONTROLLED_WASI_PROFILE.md`.
- Surface manifest evidence: `phase13-controlled-wasi-profile-decision`.
- Remaining P13-38 blockers: scoped/authorized preopen broker, virtual fd/env/
  args/stdio/clock/random policy, resource budgets, worker integration, hostile
  WASI corpus, and packaged `.wasm`/WASI behavior.

## P13-39 Stable-Denied v1.1 Note

- P13-39 records `nativeAddonProfile` diagnostics, `.node` require/resolve
  denial, private process native API denial, and dependency-tooling native
  payload rejection.
- For P13-39 v1.1, native addon artifacts stay packaged-only future inputs;
  runtime download, npm native build, lifecycle-script execution, postinstall
  download/exec, and unrestricted native payload extraction stay stable-denied.
- Host verifier:
  `tools/nodejs/phase13/verify-native-addon-loading-decision.js`.
- Gradle gate: `:app:verifyNodePhase13NativeAddonLoadingDecision`.
- Report: `docs/nodejs/reports/phase13/P13_39_NATIVE_ADDON_LOADING.md`.
- Surface manifest evidence: `phase13-native-addon-loading-decision`.
- Remaining P13-39 blockers: signed packaged native-addon manifest, ABI and
  Node-API/version allowlists, hash/signature verification, dependency scan and
  APK-builder preflight, crash isolation, and packaged positive/hostile-corpus
  evidence.

## P13-40 Stable-Denied v1.1 Note

- P13-40 records `childProcessProfile` diagnostics, default
  `child_process`/`node:child_process` denial, debug/build-time native opt-in
  diagnostics, and the separate `shell` bridge alternative.
- For P13-40 v1.1, arbitrary executable paths, package-managed downloaded
  binaries, shell operators, detached processes, inherited handles, raw process
  handles, file descriptors, streams, and signals stay stable-denied in the
  Safe/default profile.
- Host verifier:
  `tools/nodejs/phase13/verify-child-process-scoped-runner-decision.js`.
- Gradle gate: `:app:verifyNodePhase13ChildProcessScopedRunnerDecision`.
- Report: `docs/nodejs/reports/phase13/P13_40_CHILD_PROCESS_SCOPED_RUNNER.md`.
- Surface manifest evidence: `phase13-child-process-scoped-runner-decision`.
- Remaining P13-40 blockers: scoped API shape, app-private binary packaging,
  allowlist metadata, bounded stdio, timeout/cancellation/kill cleanup,
  process accounting, packaged metadata/disclosure/smoke, hostile corpus, and
  rollback controls.

## P13-45 Diagnostic-Only v1.1 Note

- P13-45 records `packagedCapabilityProfile`, structured packaged metadata,
  `node.permissions` manifest enforcement, Android permission synthesis
  evidence, and stable denial for undeclared bridge capabilities.
- For P13-45 v1.1, packaged profile, builtin, native asset, and filesystem-root
  metadata stay diagnostic-only; they must not select runtime profiles, widen
  builtin allowlists, load native assets, expand filesystem roots, or grant
  bridge authority.
- Host verifier:
  `tools/nodejs/phase13/verify-packaged-capability-metadata-decision.js`.
- Gradle gate: `:app:verifyNodePhase13PackagedCapabilityMetadataDecision`.
- Report:
  `docs/nodejs/reports/phase13/P13_45_PACKAGED_CAPABILITY_METADATA.md`.
- Surface manifest evidence: `phase13-packaged-capability-metadata-decision`.
- Future promotion work outside P13-45 metadata closure: packaged install/run
  synthesis, user disclosure, rollback, profile-specific runtime promotion, and
  device matrix evidence.

## P13-19 Partial Landing Note

- P13-19 partial accessibility selector promotion gate records the current
  direct `accessibility` and explicit `autojs6:compat` selector surfaces.
- Host verifier:
  `tools/nodejs/phase13/verify-accessibility-selector-promotion.js`.
- Gradle gate: `:app:verifyNodePhase13AccessibilitySelectorPromotion`.
- Report:
  `docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md`.
- Surface manifest evidence: `phase13-accessibility-selector-promotion`.
- Direct `waitFor`, `pickup`, selector-style `detect`, `existsAll`, and
  `existsOne` stay stable-denied for P13-19 v1.1.
- Remaining P13-19 blockers: `swipe`/`gesture`/`powerDialog`, broader Rhino
  selector aliases, packaged accessibility service/settings lifecycle,
  real-device matrix expansion, and root/Shizuku gesture tiers.

盘点日期：2026-06-24

当前版本基线来自根目录 `version.properties`：

- `VERSION_NAME=6.7.1 Alpha4`
- `VERSION_BUILD=3923`
- `MIN_SDK_VERSION=24`
- `TARGET_SDK_VERSION=36`
- `COMPILE_SDK_VERSION=36`

本文件是 Phase 12 之后的新阶段任务清单。Phase 12 已完成 Rhino
compatibility runtime promotion 的第一轮推进：`events.keys`、`input_observer`、
`compat.tasks`、`barcode/qrcode`、`s13n.bytes`、dual-engine evidence 和
文档同步已经落地，同时保留了 `ui.compat`、`floaty.overlay`、
`threads.replacement`、`rhino.json_only_shim` 等延后 surface。

Phase 13 的产品方向从单纯的 Safe Node Profile 收敛，调整为长期接近：

1. Auto.js Pro 能力：Node.js 引擎中可使用 Android/Java、Auto.js 模块、
   UI、悬浮窗、无障碍、截图、任务、插件和打包能力。
2. 桌面版 Node.js 能力：CommonJS/ESM、标准库、npm 生态、worker、WASI、
   inspector、子进程、网络、文件系统、native addon 等能力尽量补齐。

这不代表一次性无边界开放。Phase 13 的核心方法是把当前 `disabled`、
`experimental`、`blocked_by_design` 中可以重新评估的能力拆成可审计、
可回滚、可设备验证的 profile promotion 任务。

## Phase 12 基线

权威输入：

- `docs/nodejs/reports/phase12/PHASE12_SUMMARY.md`
- `docs/nodejs/reports/phase12/RHINO_AUGMENT_COMPATIBILITY_TASKS.md`
- `docs/nodejs/rhino-compat/phase12-surface-manifest.json`
- `docs/nodejs/rhino-compat/PHASE12_SURFACE_MANIFEST.md`
- `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- `docs/nodejs/rhino-compat/status-manifest.json`
- `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json`
- `docs/nodejs/COMPATIBILITY_PROFILE.md`
- `docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md`
- `docs/nodejs/INTEGRATION_STATUS_PRO9_COMPARISON.md`

Phase 12 结束时的关键事实：

- Rhino augment modules：73。
- Rhino API entries：879。
- API 状态总计：`supported` 149、`supported_async` 66、`partial` 320、
  `renamed` 1、`unsupported` 371、`blocked_by_design` 45。
- Node bridge/type surface 已覆盖一批 AutoJs 能力，但还有大量 API 是 partial
  或 provider gated。
- 默认运行时仍是 `AutoJs6 Safe Node Profile`，Node baseline 是 `v24.5.0`。
- `nodeFull` 发布形态包含 `arm64-v8a`、`armeabi-v7a`、`x86_64` 三个 ABI。
- `x86` 仍是 optional，不是当前必需 ABI。
- `network`、`worker_threads`、`java_interop` 仍默认关闭。
- `child_process`、native addon、private Node bindings、raw network、production
  inspector 在 Phase 12 仍按安全边界拒绝。
- Phase 12 remaining blockers 中仍 open 的 required internal 项：
  `crash_evidence_automation`、`android_side_package_installation`、
  `packaged_long_running`、`second_execution_slot`、`full_heavy_resource_stress`。

## Phase 13 目标

1. 把 Phase 12 的 deferred surface 推进到可用、可拒绝或可明确排期：
   `ui.compat`、`floaty.overlay`、`threads.replacement`、`rhino.json_only_shim`。
2. 将 Auto.js Pro 9 关键能力拆成 Node 侧兼容目标：`$autojs`、`$autojs.java`、
   `require("rhino").install()`、UI 线程语义、插件、任务、打包和 Java/Android
   interop。
3. 将桌面 Node.js 能力拆成 profile promotion 目标：raw network、fs 放宽、
   worker、WASI、inspector、`vm`、`child_process`、native addon、完整 ESM、
   npm/package manager。
4. 把安全边界从“永久拒绝”改成“分级 profile + capability + permission +
   verification”的可放开模型。默认可以继续安全，但 Pro/Desktop profile 可以
   通过显式配置逐步开放。
5. 每个能力从 design gate 到 promoted 必须包含：
   - runtime/module/provider 实现；
   - capability/permission manifest；
   - TypeScript declaration；
   - host verifier；
   - fake provider 或 unit/conformance；
   - device instrumentation；
   - packaged APK smoke；
   - negative/security corpus；
   - migration/user docs；
   - rollback path。

## Profile Promotion Model

Phase 13 新增以下分层语义，后续任务应逐步让代码和文档同步这些词汇：

- `safe_default`：当前默认 Safe Node Profile。普通脚本和 release 默认走这里。
- `pro_compat_opt_in`：面向 Auto.js Pro 行为的显式兼容 profile。可以要求
  `project.json`、用户设置或打包 metadata 中显式声明。
- `desktop_compat_opt_in`：面向桌面 Node.js 行为的显式兼容 profile。允许更宽
  的 Node 标准库和 npm 能力，但必须有 Android 侧约束。
- `debug_unsafe_lab`：只允许 debug/开发构建使用，用于验证高风险能力。
- `permanently_unsupported`：只有在 Android 上不可实现、风险不可控或与产品目标
  冲突时才保留永久拒绝。

Phase 13 中允许重新审视 Phase 12 的部分 `blocked_by_design` 决策，但不能直接
删除拒绝逻辑。必须先新增受控 profile、负向测试和回滚开关，再逐项 promotion。

## 提交规则

每个 P13 task 应独立提交。提交只覆盖该 task 的代码、测试、文档和报告产物。

推荐提交信息格式：

```text
nodejs phase13 P13-17: promote live UI provider
```

每个 task 完成时需要：

- 将本文件对应 checkbox 改为 `[x]`。
- 在 task 下方补充 Evidence，列出路径、命令、设备验证摘要或明确 skip reason。
- 如果新增 runtime surface，必须同步更新 types、docs、profile、status manifest、
  compat matrix 和 gate。
- 如果放开安全边界，必须增加 negative/security tests、capability gate 和 rollback
  path。
- 如果 task 需要设备、权限、root、Shizuku、外部模型、网络或 Android 版本条件，
  必须记录条件、命令和不可用时的稳定错误码。
- 不把多个 P13 task 混入同一个提交。

## 验收状态词汇

P13 task 本身使用以下执行状态：

- `planned`：只在本文件中列出，尚未实现。
- `design_locked`：profile、边界、错误码、权限和 gate 计划已冻结。
- `debug_poc`：只在 debug/实验构建可用。
- `provider_poc`：已有 fake 或 live provider POC，但不代表正式支持。
- `opt_in_promoted`：在 `pro_compat_opt_in` 或 `desktop_compat_opt_in` 中可用。
- `default_promoted`：进入默认 profile。
- `rejected`：明确成为永久非目标，并有稳定拒绝和迁移建议。

## 总体 Checklist

### Track 0: Phase bootstrap and governance

- [x] P13-00: 创建 Phase 13 Pro/Desktop parity expansion 任务文档。
  - 范围：基于 Phase 12 summary、surface manifest 和 final blocker registry
    拆分新阶段任务。
  - 产物：`docs/nodejs/reports/phase13/PHASE13_NODE_PRO_DESKTOP_PARITY_TASKS.md`
  - 验收：文档包含目标、profile promotion model、任务拆分、提交规则、验收标准
    和执行顺序。
  - Evidence：本文件。

- [x] P13-01: 建立 Phase 13 baseline verification gate。
  - 范围：新增 Phase 13 专用 host verifier，锁定 Phase 12 completion 和当前
    `version.properties` 基线。
  - 建议产物：
    - `tools/nodejs/phase13/verify-node-phase13-baseline.js`
    - Gradle task：`:app:verifyNodePhase13Baseline`
    - `build/reports/nodejs/phase13-baseline-verification.json`
    - `build/reports/nodejs/phase13-baseline-verification.md`
  - 验收：
    - P12-00 到 P12-35 状态正确。
    - Phase 12 promoted/deferred surface 计数与 summary 一致。
    - `FINAL_BLOCKER_REGISTRY.json` 中 open blocker 被完整导入 P13。
    - `version.properties` 的版本、SDK 和 build number 与报告一致。
  - Evidence：
    - Host verifier：`tools/nodejs/phase13/verify-node-phase13-baseline.js`
    - Gradle gate：`:app:verifyNodePhase13Baseline`
    - Gradle wiring：`app/node-phase13-gate.gradle.kts`、`app/build.gradle.kts`
    - 报告产物：`build/reports/nodejs/phase13-baseline-verification.json`
    - 报告产物：`build/reports/nodejs/phase13-baseline-verification.md`

- [x] P13-02: 定义 Phase 13 surface manifest。
  - 范围：新增 machine-readable manifest，记录每个 P13 surface 的 owner、
    profile、status、risk、evidence 和 rollback。
  - 建议产物：
    - `docs/nodejs/phase13/phase13-surface-manifest.json`
    - `docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md`
    - verifier 和 schema
  - 验收：
    - 每个 P13 task 映射到一个 surface。
    - `opt_in_promoted` 或 `default_promoted` surface 必须列出 docs/types/tests。
    - 高风险 feature 没有 rollback 或 negative tests 时 gate 失败。
  - Evidence：
    - Manifest：`docs/nodejs/phase13/phase13-surface-manifest.json`
    - 文档：`docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md`
    - Schema：`docs/nodejs/schemas/phase13_surface_manifest.schema.json`
    - Host verifier：`tools/nodejs/phase13/verify-node-phase13-surface-manifest.js`
    - Gradle gate：`:app:verifyNodePhase13SurfaceManifest`
    - 报告产物：`build/reports/nodejs/phase13-surface-manifest-verification.json`
    - 报告产物：`build/reports/nodejs/phase13-surface-manifest-verification.md`

- [x] P13-03: 将 profile promotion model 写入正式兼容文档。
  - 范围：更新 `COMPATIBILITY_PROFILE.md`、`NODE_PROFILE_V1_1_USER_DRAFT.md`、
    `INTEGRATION_PLAN.md`，把长期目标从单一 Safe Node Profile 扩展为
    Safe/Pro/Desktop/Debug 四层模型。
  - 验收：
    - 默认 profile 仍清晰。
    - Pro/Desktop profile 的启用入口、风险提示和不可用错误码清晰。
    - 文档不再把所有桌面 Node 能力描述为永久非目标。
  - Evidence：
    - `docs/nodejs/INTEGRATION_PLAN.md`
    - `docs/nodejs/COMPATIBILITY_PROFILE.md`
    - `docs/nodejs/NODE_PROFILE_V1_1_USER_DRAFT.md`
    - `docs/nodejs/phase13/phase13-surface-manifest.json`
    - `docs/nodejs/phase13/PHASE13_SURFACE_MANIFEST.md`

- [x] P13-04: 建立安全边界放开 rubric。
  - 范围：为 raw network、fs 放宽、Java interop、worker、WASI、inspector、
    `child_process`、native addon 等能力定义统一 promotion checklist。
  - 建议产物：`docs/nodejs/security/CAPABILITY_RELAXATION_RUBRIC.md`
  - 验收：
    - 每个能力至少经过 design、fake、debug live、packaged smoke、opt-in release
      五个状态之一。
    - 明确哪些能力允许 project/package opt-in，哪些只能 build-time opt-in。
    - 明确 release build 中的用户提示、manifest permission 和回滚开关。
  - Evidence：
    - Rubric：`docs/nodejs/security/CAPABILITY_RELAXATION_RUBRIC.md`
    - Surface manifest：`docs/nodejs/phase13/phase13-surface-manifest.json`
    - Surface verifier：`tools/nodejs/phase13/verify-node-phase13-surface-manifest.js`

### Track A: Required internal blockers and runtime graduation

- [x] P13-05: 关闭 crash evidence automation blocker。
  - 范围：自动收集 fatal marker、next-launch crash bundle、resource snapshot、
    timeline tail、symbolization 和 chaos injection evidence。
  - 建议产物：
    - crash marker writer/reader
    - next-launch collector
    - `tools/nodejs/phase13/collect-crash-evidence.js`
    - focused instrumentation chaos test
  - 验收：
    - native crash、bridge provider crash、timeout kill、service destroy 都能留下
      可诊断 bundle。
    - bundle 包含 execution id、runtime slot、ABI、profile、feature flags。
    - packaged APK 路径有同等证据或明确 skip reason。
  - Progress Evidence (2026-06-25, completed):
    - Runtime marker store: `app/src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt`
    - Next-launch collector: `App.onCreate()` -> `NodeCrashEvidenceStore.collectPending(...)`
    - Embedded service marker writes: `app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt`
    - Native process marker writes: `app/src/main/java/org/autojs/autojs/engine/NativeNodeProcessService.kt`
    - Bridge crash marker assertion: `app/src/androidTest/java/org/autojs/autojs/engine/NodeCrashEvidenceStoreInstrumentationTest.kt`
    - Simulated native fatal running-marker assertion: `collectsRunningMarkerAfterSimulatedNativeFatalProcessDeath`.
    - Real fatal native chaos assertion: `collectsRunningMarkerAfterRealFatalNativeChaosProbeDeath` writes a marker in `:nodejs_embedded`, injects `SIGKILL`, and verifies next-launch collection.
    - Packaged launch-surface marker contract: `markerRecordsPackagedLaunchSurfaceForFutureCrashSmoke`.
    - Packaged APK crash evidence smoke: `installAndRunCrashEvidenceEmbeddedNodePackagedApk` installs a packaged `interactive_long_running` APK with a fresh inrt template override, force-stops it, relaunches it, and verifies marker collection logs.
    - Signal-safe fixed-size native marker assertion: `markerWritesSignalSafeFixedSizeNativeRecord` writes a 256-byte append-only sidecar record through the native `open|write|close` core and records `safeSignalMarker` proof.
    - Host collector: `tools/nodejs/phase13/collect-crash-evidence.js`
    - Gradle gate: `:app:collectNodePhase13CrashEvidence`
    - Report: `docs/nodejs/reports/phase13/P13_05_CRASH_EVIDENCE_AUTOMATION.md`
    - Closure: `collectNodePhase13CrashEvidence` reports `ready` with zero blockers and zero policy violations.

- [x] P13-06: 推进 second execution slot 到可调度状态。
  - 范围：从 Phase 12 的 explicit slot evidence 推进到默认 scheduler 可选择
    primary/overflow slot。
  - 验收：
    - 至少两个 Node execution slot 可并发运行独立脚本。
    - ResourceRegistry、provider handles、stdout/stderr、crash evidence 相互隔离。
    - 默认调度、显式 slot、slot busy、slot crash fallback 均有测试。
  - Progress Evidence (2026-06-28, ready):
    - Default one-shot scheduler policy: `NodeEmbeddedProcessPoolPolicy.STATUS_DEFAULT_SCHEDULER_ENABLED` / `MODE_DEFAULT_TWO_SLOT`.
    - Client-side slot lease: `NativeNodeEmbeddedScriptService.acquireDefaultProcessPoolSlot(...)`.
    - Slot source propagation: `NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest.processPoolSlotSource`.
    - Default full-pool queue/dequeue:
      `defaultSchedulerQueuesWhenBothDefaultSlotsAreBusy` with
      `embedded_script.process_pool.queue_queued=true`,
      `embedded_script.process_pool.queue_status=dequeued`, and
      `embedded_script.process_pool.queue_wait_ms`.
    - Internal descriptor process-pool slot metadata:
      `processPoolSlot=primary|overflow_1` and `processPoolSlotSource=explicit_request|default_scheduler`.
    - Embedded script process guard now accepts every declared script slot suffix
      via `NativeNodeEmbeddedRuntimeBridge.isEmbeddedScriptProcess(...)`.
    - Packaged process-pool slot launch evidence:
      `installAndRunProcessPoolSlotEmbeddedNodePackagedApk`.
    - Explicit no-restart slot crash fallback diagnostics:
      `slot_crash_fallback_mode=no_restart_collect_marker`,
      `failed_slot_replacement_enabled=false`, and
      `slotCrashNoRestartFallbackCollectsOverflowMarkerAndPrimarySurvives`.
    - Focused instrumentation: `defaultSchedulerRoutesConcurrentOneShotScriptsToDifferentProcesses`.
    - Focused two-slot resource stress:
      `concurrentSlotsStressSharedResourcesRemainIsolated` covers concurrent
      primary/overflow execution with stdout/stderr capture, cwd/env isolation,
      CommonJS module cache isolation, globals, timers, file handles,
      `fs.watch` lifecycle cleanup, storage bridge pending drain, bridge
      resource cleanup, and crash marker `runtimeSlotId` evidence.
    - Host verifier: `tools/nodejs/phase13/verify-second-execution-slot-scheduler.js`.
    - Gradle gate: `:app:verifyNodePhase13SecondExecutionSlotScheduler`.
    - Report: `docs/nodejs/reports/phase13/P13_06_SECOND_EXECUTION_SLOT_SCHEDULER.md`.
    - Remaining outside P13-06 closure: P13-08 owns long-duration quick/final
      soak evidence across both active slots; future automatic slot
      replacement/quarantine stays disabled until a pool coordinator promotion.

- [ ] P13-07: 推进 packaged long-running service mode。
  - 范围：打包 APK 中支持 foreground long-running Node runtime。
  - 验收：
    - foreground notification、Stop action、checkpoint、quota、destroy cleanup 可用。
    - `autojs6:lifecycle` 在 packaged 模式有恢复/清理证据。
    - Android 版本、权限、后台限制不满足时稳定拒绝。
  - Progress Evidence (2026-06-25, partial; checkbox remains open):
    - Packaged launch surface: `NodeExecutionModePolicy.LAUNCH_SURFACE_PACKAGED_LONG_RUNNING`.
    - Project metadata parsing: `ProjectConfig.NodeConfig.executionMode` and `NodeProjectLaunchDescriptor.executionMode`.
    - Request propagation: `NodeProjectLaunchSource.nodeProjectExecutionMode`, `NativeNodeEmbeddedProjectRunner.ProjectRequest.executionMode`, and `EmbeddedScriptRequest.executionMode`.
    - Packaged inrt dispatch: `AssetsProjectLauncher.startPackagedLongRunningIfRequested(...)`.
    - Foreground service entry: `NativeNodeEmbeddedScriptService.startPackagedLongRunning(...)`.
    - Status evidence: `NodeInteractiveLongRunningStatusStore.launchSurface=packaged_long_running`.
    - Host verifier: `tools/nodejs/phase13/verify-packaged-long-running-service.js`.
    - Gradle gate: `:app:verifyNodePhase13PackagedLongRunningService`.
    - Report: `docs/nodejs/reports/phase13/P13_07_PACKAGED_LONG_RUNNING_SERVICE.md`.
    - Open before closure: install-and-run packaged APK smoke, packaged `autojs6:lifecycle` restore/cleanup evidence, Android permission/background-start matrix, quota/stress evidence.

- [ ] P13-08: 完成 full heavy resource stress。
  - 范围：补齐 8 小时 quick soak 和 24 小时 final soak。
  - 验收：
    - 无 monotonic RSS/native handle/image/socket/provider leak。
    - service restart、slot failover、provider destroy 后无 orphan process。
    - 输出可比较的 stress report。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Phase13 verifier: `tools/nodejs/phase13/verify-full-heavy-resource-stress.js`.
    - Contract fixtures:
      `tools/nodejs/phase13/fixtures/full-heavy-resource-stress/` validate
      accepted 8h/24h report shapes and fail-closed under-duration,
      missing-metric, leak/orphan/runtime failure shapes.
    - Archive helper:
      `tools/nodejs/phase13/archive-full-heavy-resource-soak.js` validates a
      completed soak JSON before copying it into the canonical final-soak path.
    - Gradle gate: `:app:verifyNodePhase13FullHeavyResourceStress`.
    - Report: `docs/nodejs/reports/phase13/P13_08_FULL_HEAVY_RESOURCE_STRESS.md`.
    - Expected quick soak evidence: `build/reports/nodejs/final-soak/quick-soak-8h.json`.
    - Expected final soak evidence: `build/reports/nodejs/final-soak/final-soak-24h.json`.
    - Reused anchors: `NodeEmbeddedMemoryLeakStressInstrumentationTest`, `NodePhase7ResourceStressInstrumentationTest`, `NodeBridgeResourceLeakStressInstrumentationTest`, `NodeLongRunningStressInstrumentationTest`, `NodeWorkerThreadsResourceStressInstrumentationTest`, `NodeResourceStressSnapshot`, and `verifyNodeFinalSoakGate`.
    - Focused two-slot resource stress anchor:
      `NodeEmbeddedProcessPoolPolicyInstrumentationTest.concurrentSlotsStressSharedResourcesRemainIsolated`
      verifies stdout/stderr, cwd/env/module/global isolation, timers,
      file-handle and watcher cleanup, storage bridge pending drain, bridge
      resource cleanup, and crash marker slot metadata across `primary` and
      `overflow_1`.
    - Open before closure: real 8h/24h device soak reports with comparable RSS/native heap/Java heap/thread/fd/bridge diagnostics and no leak/orphan/runtime failure flags.

- [ ] P13-09: 实现 Android-side package installation manager。
  - 范围：Node 项目/插件/依赖包在 Android 侧的 install/update/remove/list/verify/prune 与 dry-run plan。
  - 边界：不运行 npm lifecycle scripts；不允许未签名 native payload 直接执行。
  - 验收：
    - transactional、cancellable、locked、integrity checked。
    - 能服务后续 npm/package manager 和 plugin distribution。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Android store: `NodeAndroidPackageInstallManager`.
    - Operations: `install`, `update`, `remove`, `list`, `verify`, `prune`,
      plus dry-run `planInstall`, `planUpdate`, and `planRemove`.
    - Boundaries: lifecycle scripts, `bin`, native payloads, symlinks, and path escapes are rejected.
    - Integrity: SHA-512 expected integrity and installed-record verification.
    - Transaction model: store lock, transaction staging, atomic move/restore, cancellation token.
    - Guarded local mutation JS facade:
      `require("package_manager").status()/policy()/sourcePolicy()` reports
      the guarded source authority, `planInstall/planUpdate/planRemove`
      returns dry-run integrity, and `install/update/remove/list/verify/prune`
      dispatch to the Android app-private store; mutation calls require
      `package_manager.mutate`, `allowMutation: true`, and matching
      `expectedIntegrity`.
    - Focused instrumentation: `NodeAndroidPackageInstallManagerInstrumentationTest`.
    - Host verifier: `tools/nodejs/phase13/verify-android-package-install-manager.js`.
    - Gradle gate: `:app:verifyNodePhase13AndroidPackageInstallManager`.
    - Report: `docs/nodejs/reports/phase13/P13_09_ANDROID_PACKAGE_INSTALL_MANAGER.md`.
    - Open before closure: registry/download policy, npm CLI decision,
      signed native-payload decision, broader device/distribution evidence,
      and plugin/package distribution integration.

- [x] P13-10: 发布 Phase 13 capability truth report。
  - 范围：生成 Safe/Pro/Desktop/Debug 四个 profile 的能力真值表。
  - 验收：
    - Kotlin registry、JS bootstrap allowlist、docs、types、Gradle flags 一致。
    - 报告明确每个 Node builtin 和 AutoJs bridge module 的 profile 状态。
  - Evidence：
    - Generator：`tools/nodejs/phase13/generate-node-phase13-capability-truth.js`
    - Gradle task：`:app:generateNodePhase13CapabilityTruth`
    - Surface manifest：`docs/nodejs/phase13/phase13-surface-manifest.json`
    - 报告产物：`build/reports/nodejs/phase13-capability-truth.json`
    - 报告产物：`build/reports/nodejs/phase13-capability-truth.md`

### Track B: Auto.js Pro capability parity

- [ ] P13-11: 实现 Pro 风格 Node entry and UI-thread directives。
  - 范围：对齐 Pro 9 的 `"nodejs ui"`、`"nodejs ui-thread"` 语义。
  - 验收：
    - UI Activity 启动、非 UI 线程脚本、UI-thread dispatch 三者行为清晰。
    - 与当前 `require("ui")` provider 不冲突。
    - packaged APK 有 Activity lifecycle 和 Back/Recents evidence。
  - 进度：
    - 已落地 `NodeEntryDirective`，规范化 `"nodejs ui"` / `"nodejs ui-thread"`。
    - 已将 `entryDirective` 接入 launch descriptor、project config、Node project
      request、debug descriptor、native values 和 failure marker。
    - 已增加 focused instrumentation 与 Phase13 host verifier。
  - 证据：
    - Runtime parser: `app/src/main/java/org/autojs/autojs/script/NodeEntryDirective.kt`.
    - Focused test:
      `EmbeddedScriptMvpInstrumentationTest#proStyleNodeEntryDirectivesParseToStableMetadata`.
    - Host verifier: `tools/nodejs/phase13/verify-pro-entry-directives.js`.
    - Gradle gate: `:app:verifyNodePhase13ProEntryDirectives`.
    - Report: `docs/nodejs/reports/phase13/P13_11_PRO_ENTRY_DIRECTIVES.md`.
  - 未关闭原因：Activity lifecycle、Back/Recents 和实际 UI-thread dispatch
    evidence 尚未完成，因此保持 `partial`。

- [x] P13-12: 定义并实现 `$autojs` global v1。
  - 范围：提供 Pro 风格 `$autojs` 入口，但分层暴露能力。
  - 建议子项：
    - `$autojs.version`
    - `$autojs.androidContext` read-only/proxy
    - `$autojs.profile`
    - `$autojs.java` gated entry
  - 验收：
    - Safe profile 中只暴露只读诊断或稳定拒绝。
    - Pro profile 中可进入受控 Java interop。
    - 不泄漏 raw Context 到默认 Node userland。
  - 进度：
    - 已在 Node bridge 启动用户代码前安装冻结的 `globalThis.$autojs`。
    - `$autojs.profile` 与 `require("autojs6:profile")` 保持同一对象。
    - `$autojs.androidContext` 仅暴露冻结诊断 descriptor，不暴露 raw
      `Context`。
    - `$autojs.java` 复用现有 Java interop allowlist gate；默认稳定拒绝为
      `ERR_AUTOJS6_JAVA_INTEROP_DISABLED`。
    - 已补 TypeScript declaration 与 type-smoke。
  - 证据：
    - Runtime：`app/src/main/cpp/node_bridge_sources.cpp`.
    - Focused test：
      `EmbeddedScriptMvpInstrumentationTest#autojsGlobalV1ExposesFrozenDiagnosticsAndGatedJavaFacade`.
    - Types：`docs/nodejs/types/autojs6-node/autojs_global.d.ts`.
    - Host verifier：`tools/nodejs/phase13/verify-autojs-global-v1.js`.
    - Gradle gate：`:app:verifyNodePhase13AutoJsGlobalV1`.
    - Report：`docs/nodejs/reports/phase13/P13_12_AUTOJS_GLOBAL_V1.md`.
  - 边界：完整 Java object/thread parity 仍归 P13-13；`rhino.install`、
    `importClass`/`importPackage` 和 `JavaAdapter` 仍归 P13-14 到 P13-16。

- [ ] P13-13: 推进 `$autojs.java` / Java interop allowlist。
  - 范围：从默认关闭的 `java_interop` 实验推进到 Pro profile opt-in。
  - 验收：
    - `findClass`、构造、静态方法、实例方法、字段读取、线程 dispatch 可用。
    - 类/包 allowlist、denylist、method budget、timeout、thread mode 明确。
    - 禁止 classloader、reflection escape、native library loading、process authority。
    - Java object lifetime 和 destroy cleanup 有 evidence。
  - 2026-06-24 partial landing:
    - `require("java")` / `$autojs.java` now expose allowlist-only
      `call` / `callInstance` / `getField` / `release` / `dispose` helpers.
    - `android.graphics.Rect` handles support selected instance methods,
      field reads, explicit release, and provider destroy cleanup.
    - Host verifier: `tools/nodejs/phase13/verify-java-interop-allowlist.js`.
    - Gradle gate: `:app:verifyNodePhase13JavaInteropAllowlist`.
    - Report: `docs/nodejs/reports/phase13/P13_13_JAVA_INTEROP_ALLOWLIST.md`.
  - Remaining: thread dispatch / method budget / Pro profile promotion still
    block full P13-13 closure.

- [ ] P13-14: 推进 `require("rhino").install()` partial Pro compatibility。
  - 范围：在 Pro profile 中安装 `Packages`、`java`、`android` 等 Rhino 风格入口。
  - 边界：先不承诺完整 Rhino object parity；基于 `$autojs.java` proxy。
  - 验收：
    - 默认 Safe profile 中继续拒绝。
    - Pro profile opt-in 后可运行 Pro 9 文档中的基础 Java interop 示例。
    - `node_modules/rhino` shadowing 仍不可绕过受控模块。
  - 2026-06-24 partial landing:
    - `rhino.install()` without explicit experimental options still rejects
      with `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`.
    - `rhino.install({ experimental: true, globals: true })` can install
      `Packages` / `java` / `android` proxies when Java interop is enabled.
    - Installed proxies route static calls and constructors through the P13-13
      Java allowlist bridge; `importClass` / `importPackage` / `JavaAdapter`
      remain absent.
    - Host verifier: `tools/nodejs/phase13/verify-rhino-install-java-proxy.js`.
    - Gradle gate: `:app:verifyNodePhase13RhinoInstallJavaProxy`.
    - Report: `docs/nodejs/reports/phase13/P13_14_RHINO_INSTALL_JAVA_PROXY.md`.
  - Remaining: real Pro profile selector, packaged policy, and actual P13-16
    `JavaAdapter` replacement implementation still block full P13-14 closure.

- [x] P13-15: 决策并实现 `importClass` / `importPackage` compatibility。
  - 范围：评估是否在 Pro profile 中提供 Rhino 迁移 helper。
  - 验收：
    - 如果实现，必须只绑定到显式 scope，不默认污染所有模块。
    - 如果拒绝，文档给出赋值式迁移方案和稳定错误码。
  - 2026-06-24 decision landing:
    - Direct Rhino `importClass` / `importPackage` compatibility remains
      unsupported in Node and uses stable
      `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`.
    - `require("rhino").importClass()` and `.importPackage()` are explicit
      module helpers only; no global helpers are installed.
    - Migration path is explicit assignment from
      `rhino.install({ experimental: true, target })` Java proxies.
    - Host verifier:
      `tools/nodejs/phase13/verify-rhino-import-helpers-decision.js`.
    - Gradle gate: `:app:verifyNodePhase13RhinoImportHelpersDecision`.
    - Report: `docs/nodejs/reports/phase13/P13_15_IMPORT_HELPERS_DECISION.md`.

- [ ] P13-16: 决策 `JavaAdapter` / `$java.defineClass` replacement。
  - 范围：提供 Java interface implementation 的迁移路径。
  - 验收：
    - 优先设计 `$java.defineClass` 或 proxy-interface API。
    - 禁止无界动态继承、跨线程泄漏和 raw callback handle 泄漏。
    - 至少覆盖常见 listener/interface 用例。
  - 2026-06-24 decision landing:
    - `require("rhino").JavaAdapter()` is an explicit stable denial with
      `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`; no global `JavaAdapter` is
      installed.
    - `java.defineClass()` and `$autojs.java.defineClass()` are reserved as the
      migration names but currently throw `ERR_AUTOJS6_JAVA_METHOD_DENIED`
      without bridge dispatch.
    - Host verifier:
      `tools/nodejs/phase13/verify-rhino-java-adapter-decision.js`.
    - Gradle gate: `:app:verifyNodePhase13RhinoJavaAdapterDecision`.
    - Report: `docs/nodejs/reports/phase13/P13_16_JAVA_ADAPTER_DECISION.md`.
  - Remaining: actual proxy-interface / `defineClass` provider, listener
    examples, callback cleanup, thread dispatch, packaged metadata, and leak
    diagnostics still block full P13-16 closure.

- [ ] P13-17: 推进 live UI provider 到 opt-in promoted。
  - 范围：基于 P12 `ui.compat` provider POC，补齐 live packaged UI。
  - 验收：
    - JSON layout subset、update、event drain、close、Activity lifecycle 可用。
    - Back/Recents、orientation、destroy cleanup、permission denial 有设备证据。
    - Rhino `ui` 兼容 wrapper 和 Pro 风格 `require("ui")` 文档同步。

  - 2026-06-24 partial landing:
    - Live `require("ui")` Activity-owned provider evidence is recorded for
      `showLayout`, `update`, `batchUpdate`, `drainEvents`, and `close`.
    - `NodeBridgeUiActivity` covers JSON descriptors for Column/Row/Text/Button/Input/Image/ScrollView/List/Switch/Checkbox/Progress/Tabs.
    - `NodeUiBridgeInstrumentationTest` covers fake transport, live Android
      Button/Input/List/Switch/Checkbox/Tabs events, state snapshots, update,
      close, provider destroy cleanup, unsupported descriptor denial, WebView
      gate denial, foreign-handle denial, and active UI session limits.
    - `compat.ui` and direct `ui` type smoke remain aligned with the same
      JSON provider.
    - Provider registry now marks packaged UI support as provider-dependent
      until live packaged Back/Recents/orientation/relaunch/crash evidence
      lands.
    - Host verifier: `tools/nodejs/phase13/verify-live-ui-provider-promotion.js`.
    - Gradle gate: `:app:verifyNodePhase13LiveUiProviderPromotion`.
    - Report: `docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md`.
  - Remaining: Back/Recents lifecycle evidence, orientation/configuration
    policy evidence, live packaged APK UI smoke, crash/relaunch evidence, and
    packaged capability/disclosure denial evidence still block opt-in
    promotion.

- [ ] P13-18: 推进 live floaty/overlay provider 到 opt-in promoted。
  - 范围：基于 P12 overlay provider POC，开启 live show/update/drainEvents/close。
  - 验收：
    - SYSTEM_ALERT_WINDOW、foreground disclosure、permission settings flow 完整。
    - packaged metadata、closeAll cleanup、leak stress 有 evidence。
    - raw `floaty.window/rawWindow` View parity 继续单独决策，不混入本任务。
  - 2026-06-24 partial landing:
    - Host verifier: `tools/nodejs/phase13/verify-overlay-provider-promotion.js`.
    - Gradle gate: `:app:verifyNodePhase13OverlayProviderPromotion`.
    - Report: `docs/nodejs/reports/phase13/P13_18_OVERLAY_PROVIDER_PROMOTION.md`.
    - Surface manifest evidence: `phase13-overlay-provider-promotion`.
    - Current state: `require("ui.overlay")` has JS facade/types plus the
      P12-14 Android permission/settings/closeAll live gate; live
      `show`/`update`/`drainEvents`/per-handle `close` remains fail-closed.
    - Raw `floaty.window()` / `floaty.rawWindow()` View parity remains outside
      this task and stays blocked by design.
  - Remaining: promoted Android overlay provider, permission granted/denied
    device matrix, foreground notification/disclosure ownership, packaged
    install-run smoke, task-removal cleanup, relaunch/crash evidence, and real
    overlay leak/stress evidence.

- [ ] P13-19: 扩展 accessibility/selector Pro parity。
  - 范围：补齐常用全局自动化函数和 selector DSL。
  - 建议覆盖：
    - click、longClick、swipe、gesture、back、home、recents、powerDialog
    - `text()/id()/desc()/className()` 等 selector chain
    - `findOne`、`waitFor`、bounds/clickable/scrollable helpers
  - 验收：
    - JSON selector descriptor 和 opaque node handle 生命周期明确。
    - 无障碍服务不可用时稳定拒绝。
    - gesture/root/Shizuku 路径分层，不隐式提权。

- [ ] P13-20: 推进 screenshot/image/OCR Pro parity。
  - 范围：补齐截图权限、屏幕捕获、图片读写、找图、找色、OCR 的 packaged/live
    行为。
  - 验收：
    - permission prompt、foreground disclosure、capturer stop、image recycle 完整。
    - ML Kit/Paddle/Rapid provider 的可用性和 fallback 策略明确。
    - raw Bitmap/OpenCV object 是否开放另起 profile 决策。

- P13-20 partial screenshot/image/OCR promotion gate (2026-06-24):
  - Evidence:
    - Report: `docs/nodejs/reports/phase13/P13_20_SCREENSHOT_IMAGE_OCR_PROMOTION.md`
    - Verifier: `tools/nodejs/phase13/verify-screenshot-image-ocr-promotion.js`
    - Gradle gate: `:app:verifyNodePhase13ScreenshotImageOcrPromotion`
    - Surface manifest evidence: `phase13-screenshot-image-ocr-promotion`
  - Current ready slice:
    - `image`/`images` direct API shape and TypeScript declarations cover screenshot, read/write, transforms, matching, color search, size, and recycle.
    - Android live provider supports scoped `readImage` and explicit `recycle` through provider-owned image handles.
    - `ocr` direct API and live ML Kit provider consume provider-owned image handles and reject unknown/recycled handles without resource leaks.
    - `autojs6:compat` exposes Rhino-style screenshot/image/OCR wrappers over the same controlled providers.
  - Remaining blockers:
    - MediaProjection bridge provider still needs Activity-owned prompt, denial, foreground disclosure, `nextImage`, `stop`, cleanup, and packaged evidence.
    - Real image save/transform/match/color providers and packaged scoped path/quota evidence remain unpromoted.
    - Paddle/Rapid/plugin OCR provider selection and raw Bitmap/OpenCV/
      MediaProjection object parity stay stable-denied for P13-20 v1.1.

- [ ] P13-21: 实现 tasks daily/weekly/user database provider。
  - 范围：将 P12 中 rejected 的 daily/weekly/user task database 重新作为
    Pro profile opt-in 设计。
  - 验收：
    - add/query/update/remove daily/weekly task 可用。
    - 数据库 schema、迁移、权限、packaged 行为有 gate。
    - raw task object mutation 仍需单独决策。
  - P13-21 partial task database provider gate (2026-06-24):
    - Evidence:
      - Report: `docs/nodejs/reports/phase13/P13_21_TASK_DATABASE_PROVIDER.md`
      - Verifier: `tools/nodejs/phase13/verify-task-database-provider.js`
      - Gradle gate: `:app:verifyNodePhase13TaskDatabaseProvider`
      - Surface manifest evidence: `phase13-task-database-provider`
    - Current ready slice:
      - `work_manager.scheduleOnce/schedulePeriodic/cancel/list` remains the live Node-owned scheduled-work provider.
      - `compat.tasks.addDisposableTask/queryTasks/queryTimedTasks/getTimedTask/removeTask/removeTimedTask` covers one-shot migration over WorkManager snapshots.
      - `compat.tasks.addDailyTask/addWeeklyTask/updateTask` and IntentTask methods still reject before bridge dispatch.
      - Android/Rhino `TimedTaskDatabase`, `TimedTaskManager`, and daily/weekly factory methods exist as substrate, but raw rows/objects do not cross into Node.
    - Remaining blockers:
      - JSON task database descriptor schema, migration/downgrade policy, and main-app vs packaged ownership.
      - Daily/weekly add/query/get/update/remove provider operations over frozen JSON snapshots.
      - Wall-clock scheduler semantics, exact-alarm/WorkManager/JobScheduler fallback policy, packaged manifest/disclosure, and raw task object profile decision.

- [ ] P13-22: 实现 IntentTask/broadcast task provider。
  - 范围：提供受控广播/Intent 触发任务能力。
  - 验收：
    - manifest synthesis、receiver authority、exported policy、package signing 清晰。
    - 不允许任意外部广播静默唤起高权限脚本。
    - packaged APK smoke 覆盖 install/trigger/remove。
  - P13-22 partial IntentTask/broadcast provider gate (2026-06-24):
    - Evidence:
      - Report: `docs/nodejs/reports/phase13/P13_22_INTENT_BROADCAST_PROVIDER.md`
      - Verifier: `tools/nodejs/phase13/verify-intent-broadcast-provider.js`
      - Gradle gate: `:app:verifyNodePhase13IntentBroadcastProvider`
      - Surface manifest evidence: `phase13-intent-broadcast-provider`
    - Current ready slice:
      - Android/Rhino `IntentTask`, `IntentTaskDatabase`, `TimedTaskManager`, `BaseBroadcastReceiver`, `StaticBroadcastReceiver`, and `DynamicBroadcastReceivers` exist as substrate.
      - `compat.tasks.addIntentTask/queryIntentTasks/getIntentTask/removeIntentTask` still reject with `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` before bridge dispatch.
      - Node still cannot register Android receivers, mutate manifests, receive external broadcast wakeups, or access raw `Intent` / `BroadcastReceiver` / `IntentTask` objects.
    - Remaining blockers:
      - Capability split for app-local custom broadcasts, exported broadcasts, boot/user-unlocked, package events, startup, and exact-alarm triggers.
      - Receiver ownership, exported policy, duplicate suppression, unregister/delete cleanup, and process-death recovery.
      - Trigger payload JSON snapshots, packaged manifest synthesis, install/trigger/remove smokes, user disclosure, and rate-limit diagnostics.

- [ ] P13-23: 扩展 app/intent/broadcast Pro parity。
  - 范围：补齐 `app` 模块启动应用、intent、URI、邮件、设置页、广播等能力。
  - 验收：
    - Intent JSON schema、flag/category/extras 类型、安全过滤明确。
    - root 选项和 Shizuku 路径不混入普通 app API。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - JSON-safe descriptor helper: `require("app").intent(request)`.
    - `startActivity()` descriptor normalization: `category`/`categories`,
      numeric flag arrays, package alias normalization, and primitive extras.
    - Android parser coverage:
      `NodeBridgeProtocol.parseIntentCategories`,
      `NodeBridgeProtocol.parseIntentFlags`, and primitive array extras.
    - Stable blocked boundary: `app.sendBroadcast()` rejects before bridge
      dispatch, and `root`/`shizuku`/`dual`/`user` options are rejected by the
      JS facade.
    - Follow-up helper parity: JSON-safe `app.parseUri()` descriptors plus
      stable fail-closed `app.getUriForFile()`, `app.kill()`, `app.killDual()`,
      `app.startDualActivity()`, `app.launchDual*()`, `app.openDual*()`,
      `app.uninstallDual()`, and `app.isDualInstalled()` stubs.
    - Type declarations: `docs/nodejs/types/autojs6-node/app.d.ts`.
    - Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`.
    - Focused conformance:
      `NodeBridgeConformanceInstrumentationTest#jsStableBridgeModuleExportsAndPromiseContractsAreStable`.
    - Host verifier:
      `tools/nodejs/phase13/verify-app-intent-broadcast-parity.js`.
    - Gradle gate: `:app:verifyNodePhase13AppIntentBroadcastParity`.
    - Report:
      `docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md`.
    - Open before closure: raw URI/FileProvider promotion, broadcast send
      promotion, root/Shizuku/dual-user routing through P13-24 capabilities,
      raw installed-app list APIs, packaged smoke, negative device corpus,
      preset alias mapping, and migration examples.

- [ ] P13-24: 扩展 shell/root/Shizuku 能力。
  - 范围：将 shell、root shell、Shizuku 拆成独立 capability。
  - 验收：
    - root 和 Shizuku 必须显式声明、显式授权、可审计。
    - stdout/stderr/exitCode/timeout/cancel/resource cleanup 完整。
    - packaged APK 权限提示和失败路径清晰。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Capability vocabulary: `shell`, `shell.root`, and `shell.shizuku`.
    - Root facade: `shell.execRoot()` and `compat.shell.execRoot()` route
      through the existing controlled `shell.exec()` bridge with `root: true`.
    - Shizuku stable denial: `shell.execShizuku()` and
      `shell.exec(..., { shizuku: true })` reject with
      `ERR_AUTOJS6_SHELL_SHIZUKU_UNSUPPORTED` before bridge dispatch.
    - Split diagnostics:
      `NodeBridgePermissionManifestInstrumentationTest` asserts undeclared
      root requests report `shell.root`, `NodeShellBridgeInstrumentationTest`
      asserts combined root/Shizuku access reports
      `shell.root|shell.shizuku`, and
      `NodeBridgeConformanceInstrumentationTest` asserts Shizuku-only access
      reports `shell|shell.shizuku`.
    - Controlled execution evidence: scoped `cwd`, string-only `env`, bounded
      output, timeout code `124`, `ResourceRegistry` process cleanup, and
      active shell command limits.
    - Metadata examples now show separate project/package declarations for
      `shell.root` and vocabulary-only `shell.shizuku` without promoting
      Shizuku execution.
    - Type declarations:
      `docs/nodejs/types/autojs6-node/shell.d.ts` and
      `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`.
    - Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`.
    - Focused conformance:
      `NodeBridgeConformanceInstrumentationTest#jsStableBridgeModuleExportsAndPromiseContractsAreStable`.
    - Host verifier:
      `tools/nodejs/phase13/verify-shell-root-shizuku-capability.js`.
    - Gradle gate: `:app:verifyNodePhase13ShellRootShizukuCapability`.
    - Report:
      `docs/nodejs/reports/phase13/P13_24_SHELL_ROOT_SHIZUKU_CAPABILITY.md`.
    - Open before closure: root available/unavailable device matrix, Shizuku
      service authorization and lifecycle, AbortSignal cancellation
      instrumentation, packaged disclosure/failure diagnostics, expanded audit
      taxonomy, and migration examples.

- [ ] P13-25: 扩展 notifications/settings/power manager。
  - 范围：补齐通知渠道、设置、电池优化、唤醒/亮屏等 Pro 能力。
  - 验收：
    - Android 版本差异和 runtime permission 有稳定错误。
    - channel lifecycle、cancel ownership、settings launch 有测试。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Notifications: `notify/cancel/cancelAll` remain owned-resource APIs, and
      `getPermissionStatus()`, `openSettings()`, and
      `openNotificationSettings()` now return JSON-safe permission/settings
      snapshots.
    - Power manager: `device.isIgnoringBatteryOptimizations()` and
      `device.openBatteryOptimizationSettings({ dryRun: true })` are available
      through explicit `device.power`.
    - Capability gate: `notifications.settings` and `device.power` are exact
      declarations, not implied by base `notifications` or `device`.
    - Host verifier:
      `tools/nodejs/phase13/verify-notifications-settings-power-manager.js`.
    - Gradle gate: `:app:verifyNodePhase13NotificationsSettingsPowerManager`.
    - Report:
      `docs/nodejs/reports/phase13/P13_25_NOTIFICATIONS_SETTINGS_POWER_MANAGER.md`.
    - Open before closure: notification actions/listeners/rich layouts,
      Do-Not-Disturb/exact-alarm policy, direct wakelock/power-manager APIs,
      OEM/device matrix, packaged disclosure/run evidence, and migration
      examples.

- [ ] P13-26: 扩展 media/recorder/mediainfo provider。
  - 范围：音频播放、录音、媒体元数据、MediaStore 相关能力。
  - 验收：
    - 录音/媒体读取权限、foreground disclosure、resource cleanup 完整。
    - 不泄漏 raw MediaPlayer/MediaRecorder/MediaMetadataRetriever。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Capability gate: `media`, `media.audio`, `media.metadata`, and
      `media.recording` are explicit bridge capabilities; the three
      specialized tokens are exact and are not implied by `media`.
    - Runtime bridge: `media.getAudioStreamVolume()`,
      `media.getAudioStreamMaxVolume()`, `media.getAudioStreamInfo()`,
      `mediainfo.read()`, `mediainfo.get()`, and `recorder.getStatus()`.
    - Fail-closed recorder boundary: `recorder.start()` and
      `recorder.stop()` require `media.recording` and return a stable provider
      missing error until foreground disclosure, permission flow, cleanup, and
      packaged smoke exist.
    - Compat aliases: `autojs6:compat.media`, `autojs6:compat.mediainfo`,
      and `autojs6:compat.recorder`.
    - Types: `docs/nodejs/types/autojs6-node/media.d.ts`,
      `docs/nodejs/types/autojs6-node/mediainfo.d.ts`, and
      `docs/nodejs/types/autojs6-node/recorder.d.ts`.
    - Instrumentation:
      `app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt`.
    - Host verifier:
      `tools/nodejs/phase13/verify-media-recorder-mediainfo-provider.js`.
    - Gradle gate: `:app:verifyNodePhase13MediaRecorderMediainfoProvider`.
    - Report:
      `docs/nodejs/reports/phase13/P13_26_MEDIA_RECORDER_MEDIAINFO_PROVIDER.md`.
    - Open before closure: playback provider, real recorder sessions,
      MediaStore policy, external media permission matrix, packaged media
      asset lookup, privacy copy, corpus evidence, and migration examples.

- [x] P13-27: 扩展 utility-heavy Rhino modules。
  - 范围：`zip`、`canvas`、`opencc`、`pinyin` full dictionary、`jsox`、
    `util`、`sysprops` 等。
  - 验收：
    - 纯 JS 能力优先 promotion。
    - 资源型能力必须有 packaged resource lookup 和 missing-resource diagnostics。
    - raw Android Canvas/Paint parity 如需开放必须单独 profile gate。
  - Progress Evidence (2026-06-24, pure helper subset):
    - Pure JS facade: `require("jsox")` and `require("jsox.mathx")`.
    - Compat alias: `require("autojs6:compat").jsox`.
    - Promoted subset: `Mathx` random/statistics/distance/log/pow/min/max helpers.
    - Mutation policy: `jsox.extend(target, "Mathx")` and
      `jsox.extendAll(target)` copy helpers only to explicit targets; default
      global Math/prototype mutation returns
      `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`.
    - Types: `docs/nodejs/types/autojs6-node/jsox.d.ts`.
    - Instrumentation:
      `app/src/androidTest/java/org/autojs/autojs/engine/NodeUtilityBridgeInstrumentationTest.kt`.
    - Host verifier:
      `tools/nodejs/phase13/verify-utility-heavy-rhino-modules.js`.
    - Gradle gate: `:app:verifyNodePhase13UtilityHeavyRhinoModules`.
    - Report:
      `docs/nodejs/reports/phase13/P13_27_UTILITY_HEAVY_RHINO_MODULES.md`.
    - Raw Android/OpenCV point/rect object parity is stable-denied for P13-27.
    - Closure: P13-27 v1.1 completes the provider-free `jsox`,
      `jsox.mathx`, `jsox.arrayx`, `jsox.numberx`, and
      `autojs6:compat.jsox` helper subset with explicit-target mutation only,
      TypeScript declarations, conformance coverage, focused runtime
      instrumentation, status manifest entries, stable raw Android/OpenCV
      object denials, docs, surface manifest, and Gradle gate.
    - Future resource-backed utility work: `zip`, `canvas` JSON drawing provider/raw
      Canvas/Paint decision, OpenCC/pinyin packaged resources, Rhino `util`
      helper split, and `sysprops`.

### Track C: Desktop Node.js parity

- [x] P13-28: 建立 Node builtin desktop parity matrix。
  - 范围：以当前 Node 24.5.0 runtime 为基线，列出每个 builtin 的 Safe/Pro/Desktop
    状态。
  - 建议产物：
    - `docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md`
    - `build/reports/nodejs/node-builtin-parity.json`
  - 验收：
    - 每个 builtin 至少是 `supported`、`partial`、`opt_in`、`debug_only`、
      `unsupported` 或 `android_not_applicable`。
    - matrix 由 runtime allowlist/denylist 和 tests 生成或校验。
  - Evidence：
    - Generator：`tools/nodejs/phase13/generate-node-builtin-parity-matrix.js`
    - Gradle task：`:app:generateNodeBuiltinDesktopParityMatrix`
    - Matrix：`docs/nodejs/desktop-parity/NODE_BUILTIN_PARITY_MATRIX.md`
    - 报告产物：`build/reports/nodejs/node-builtin-parity.json`
    - 报告产物：`build/reports/nodejs/node-builtin-parity.md`

- [ ] P13-29: 推进 raw `http` / `https` client compatibility。
  - 范围：在 Desktop profile 中逐步允许 Node core `http`/`https` client。
  - 验收：
    - client request、stream response、timeout、redirect、TLS error、proxy denial
      行为明确。
    - server/listen 能力不与 client 一起默认开放。
    - 与现有 `fetch`/`axios` bridge-backed provider 的边界清晰。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Runtime boundary: `app/src/main/cpp/node_bridge_sources.cpp`
    - Focused instrumentation:
      `app/src/androidTest/java/org/autojs/autojs/engine/NodeHttpDesktopParityInstrumentationTest.kt`
    - Existing facade tests: `NodeHttpClientFacadeInstrumentationTest`,
      `NodeHttpStreamSemanticsInstrumentationTest`,
      `NodeHttpsClientFacadeInstrumentationTest`,
      `NodeHttpAgentFacadeInstrumentationTest`,
      `NodeRawNetworkPolicyInstrumentationTest`
    - Runtime permission manifest propagation:
      `NodeBridgePermissionManifest.runtimeModuleSourceForWorkingDirectory(...)`
      now merges working-directory and sandbox-root manifests, and the embedded
      service injects the build-time network opt-in as controlled-client policy.
    - Controlled fetch policy now allows 16 active requests by default
      (hard max 32) and a 32 MiB default response body budget (hard max 64 MiB)
      for real utility scripts with paginated HTTP calls and large HTML pages.
    - HTTP npm corpus now includes nine fixtures, adding
      `redirect-follow-client`, `tls-failure-client`, and
      `proxy-denial-client` for follow-redirects-style packages, provider TLS
      errors, and per-request proxy denial without dispatch.
    - Types: `docs/nodejs/types/autojs6-node/http.d.ts`,
      `docs/nodejs/types/autojs6-node/https.d.ts`
    - Host verifier:
      `tools/nodejs/phase13/verify-http-https-client-parity.js`
    - Gradle gate: `:app:verifyNodePhase13HttpHttpsClientParity`
    - Report:
      `docs/nodejs/reports/phase13/P13_29_HTTP_HTTPS_CLIENT_PARITY.md`
    - Surface manifest evidence: `phase13-http-https-client-parity`

- [ ] P13-30: 推进 `net` / `tls` / `dns` staged compatibility。
  - 范围：先客户端 socket，再评估 server socket。
  - 验收：
    - connection count、bytes、timeout、host allow/deny、background policy 明确。
    - DNS API 不泄漏 Android 私有网络状态。
    - socket close/destroy 后无 fd leak。
  - Partial progress:
    - `NodeNetTlsDnsStagedParityInstrumentationTest` 覆盖 reserved builtin
      解析、shadow denial、`net`/`tls`/`dns` 当前有限 facade 形状、DNS
      loopback/IP-literal-only 行为、server/listen/connect/TLS session
      denial，以及 socket close/destroy 无公开 fd/bytes 逃逸的当前证据。
    - 新增 `autojs.nodejs.rawNetworkModules.experimental=true` debug/build-time
      opt-in 通路；CommonJS `dns`、`dns/promises`、`http`、`https`、`net`、
      `tls` 会路由到 Node 原生 builtin，并包含用户侧
      `dns.resolve("www.github.com")` 样例路径的 focused instrumentation。
    - Type declarations:
      `docs/nodejs/types/autojs6-node/net.d.ts`,
      `docs/nodejs/types/autojs6-node/tls.d.ts`,
      `docs/nodejs/types/autojs6-node/dns.d.ts`.
    - Host verifier:
      `tools/nodejs/phase13/verify-net-tls-dns-staged-compatibility.js`.
    - Gradle gate: `:app:verifyNodePhase13NetTlsDnsStagedCompatibility`.
    - Report:
      `docs/nodejs/reports/phase13/P13_30_NET_TLS_DNS_STAGED_COMPATIBILITY.md`.
    - Surface manifest evidence: `phase13-net-tls-dns-staged-compatibility`.

- [x] P13-31: 决策 `dgram` UDP capability。
  - 范围：评估 UDP client/server 是否进入 Desktop profile。
  - 验收：
    - multicast/broadcast 默认拒绝或独立 capability。
    - packaged INTERNET permission、network policy 和 fd cleanup 完整。
  - Decision progress:
    - `dgram` / `node:dgram` 在当前 Node Profile v1.1 Desktop track 中保持
      stable-denied，不提供 shape-only facade，也不开放 UDP client/server。
    - `NodeDgramUdpDecisionInstrumentationTest` 覆盖 direct require、`node:`
      alias、`require.resolve()`、transitive package require、local
      `node_modules/dgram` shadow denial。
    - Host verifier: `tools/nodejs/phase13/verify-dgram-udp-decision.js`.
    - Gradle gate: `:app:verifyNodePhase13DgramUdpDecision`.
    - Report: `docs/nodejs/reports/phase13/P13_31_DGRAM_UDP_DECISION.md`.
    - Surface manifest evidence: `phase13-dgram-udp-decision`.
    - Closure: current v1.1 keeps `dgram` / `node:dgram` stable-denied with no
      shape-only facade, no local shadow bypass, no raw UDP socket, synced docs,
      desktop matrix, release matrix, surface manifest, and Gradle gate.
    - Future UDP promotion work: unicast client, server bind/listen, broadcast,
      multicast, permission mapping, packet budgets, lifecycle cleanup,
      fd/socket leak instrumentation, packaged permission proof, and live
      Android UDP evidence remain future gates.

- [x] P13-32: 放宽 filesystem profile。
  - 范围：从 scoped project fs 扩展到用户显式授权目录、`/sdcard`、SAF 或 app
    私有目录 profile。
  - 验收：
    - Safe profile 仍保持 scoped fs。
    - Pro/Desktop profile 可通过 manifest/用户授权获得额外根。
    - symlink、realpath、`..`、NUL、`/proc`、`/sys`、`/dev` 边界重新审计。
  - Decision progress:
    - 当前 Safe profile 明确保持 `workingDirectory` scoped fs，不在 P13-32
      中静默开放 `/sdcard`、SAF、app-private 或用户授权根。
    - scoped fs 现在允许解析后仍位于 `workingDirectory` / `sandboxRoot`
      内的绝对路径，用于兼容 `jsdom` 等 npm package 通过 `__dirname`
      读取包内 CSS/fixture 资源；当脚本位于 AutoJs6 工作目录子目录时，
      `process.cwd()` 保持脚本目录，但 `sandboxRoot` 可提升到工作目录根，
      因而 `../version.properties` 这类仍在工作目录内的读取可通过；
      越界绝对路径仍返回绝对路径拒绝策略。
    - `autojs6:profile.filesystemProfile` 暴露冻结诊断：
      `mode="scoped_working_directory"` 且 `additionalRoots=[]`。
    - `NodeFilesystemProfileDecisionInstrumentationTest` 覆盖 scoped 读写、
      `realpath`、绝对 outside path、`/sdcard`、`/proc`、`/sys`、`/dev`、
      NUL、`..`、promise escape、symlink/realpath escape。
    - Host verifier:
      `tools/nodejs/phase13/verify-filesystem-profile-decision.js`.
    - Gradle gate: `:app:verifyNodePhase13FilesystemProfileDecision`.
    - Report:
      `docs/nodejs/reports/phase13/P13_32_FILESYSTEM_PROFILE_DECISION.md`.
    - Surface manifest evidence: `phase13-filesystem-profile-decision`.
    - Closure: P13-32 v1.1 keeps Safe profile scoped to
      `workingDirectory` / `sandboxRoot`, keeps `additionalRoots=[]`, proves
      scoped absolute path behavior, subdirectory workspace parent reads,
      `/sdcard` and sensitive path denials, NUL/`..`/promise/symlink/realpath
      escape denial, TypeScript declarations, docs, desktop matrix, surface
      manifest, and Gradle gate without promoting expanded roots.
    - Future expanded-root work: user-authorized directory grants,
      SAF/document roots, app-private roots, `/sdcard` expansion, manifest
      metadata, grant persistence/revocation diagnostics, packaged
      expanded-root smoke, and per-root symlink/realpath/sensitive-path audits.

- [x] P13-33: 推进 desktop fs advanced APIs。
  - 范围：`fs.watch`、`fs.opendir`、FileHandle、fd API、stream、realpath 等。
  - 验收：
    - watcher 数量、fd 数量、close-on-destroy、packaged behavior 完整。
    - Android 文件系统不支持的行为有兼容说明。
  - 进展：
    - P13-33 partial landing 已添加 `filesystemProfile.advancedApis` 与
      `filesystemProfile.limits` 诊断，保持 `rawFd=false` 且不扩展 roots。
    - 新增 focused gate:
      `NodeFilesystemAdvancedDecisionInstrumentationTest`。
    - Host verifier:
      `tools/nodejs/phase13/verify-filesystem-advanced-apis.js`。
    - Gradle gate: `:app:verifyNodePhase13FilesystemAdvancedApis`。
    - Report:
      `docs/nodejs/reports/phase13/P13_33_FILESYSTEM_ADVANCED_APIS.md`。
    - Surface manifest evidence: `phase13-filesystem-advanced-apis`。
    - Closure: P13-33 v1.1 completes the scoped advanced filesystem API gate
      with `filesystemProfile.advancedApis`, watcher limits, scoped streams,
      `FileHandle`, fd compatibility tokens, `opendir`, `Dirent`, `fs.watch`,
      `fs.watchFile`, `fs.promises.watch`, `realpath`, pending watcher/
      watchFile diagnostics, Android compatibility notes, packaged realpath/
      opendir markers, TypeScript declarations, docs, desktop matrix, surface
      manifest, and Gradle gate without raw native fd exposure or expanded
      filesystem roots.
    - Future packaged/expanded-root work: packaged watcher/fd/FileHandle/
      stream lifecycle smoke, device-exported packaged realpath/opendir
      evidence, expanded-root reruns, and final full desktop fs semantics.

- [ ] P13-34: 推进 `worker_threads` 到 opt-in promoted。
  - 范围：启用真实 worker runtime，并定义 AutoJs bridge module 隔离规则。
  - 验收：
    - worker 可运行纯 JS、Node builtin、MessageChannel/TransferList 基础能力。
    - worker 默认不能访问 AutoJs bridge，除非后续单独 provider。
    - nested worker、resource stress、crash evidence、packaged behavior 完整。
  - 进展：
    - P13-34 partial landing 已添加 `workerThreadsProfile` 诊断，保持默认拒绝。
    - 新增 focused gate:
      `NodeWorkerThreadsPhase13DecisionInstrumentationTest`。
    - Host verifier:
      `tools/nodejs/phase13/verify-worker-threads-opt-in-promotion.js`。
    - Gradle gate: `:app:verifyNodePhase13WorkerThreadsOptInPromotion`。
    - Report:
      `docs/nodejs/reports/phase13/P13_34_WORKER_THREADS_OPT_IN_PROMOTION.md`。
    - Surface manifest evidence: `phase13-worker-threads-opt-in-promotion`。
    - Worker policy boundary: nested workers stay denied; SharedArrayBuffer /
      Atomics are not promoted as cross-worker guarantees; resource limits,
      one-worker thread ceiling, and RSS evidence gating are documented.

- [x] P13-35: 决策 process-worker replacement。
  - 范围：如果 `worker_threads` 无法满足 Android 稳定性，提供 AutoJs6
    process-worker API。
  - 验收：
    - API 与 `worker_threads` 关系清晰。
    - scoped script path、message size、cleanup、slot accounting 完整。
  - Partial landing:
    - `autojs6:profile.processWorkerReplacementProfile` 已暴露 P13-35
      决策快照。
    - 当前替代路径明确为 request-local
      `worker_threads.WorkerPool`，不新增 `process_worker` 或
      `compute_worker` callable facade。
    - `child_process` 保持默认拒绝；`shell` 继续作为独立 scoped command
      bridge，不获得 raw process/spawn/fork/stdio/signal 权限。
    - `worker_computation` 与 `second_execution_slot` 仅作为未来 gate
      依赖，不由 P13-35 关闭。
    - Device test:
      `NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest`。
    - Host verifier:
      `tools/nodejs/phase13/verify-process-worker-replacement-decision.js`。
    - Gradle gate:
      `:app:verifyNodePhase13ProcessWorkerReplacementDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_35_PROCESS_WORKER_REPLACEMENT.md`。
    - Surface manifest evidence:
      `phase13-process-worker-replacement-decision`。
    - Closure: P13-35 v1.1 does not introduce a user-facing process-worker
      facade or UX; future WorkerPool promotion evidence remains owned by
      P13-34/future provider work.

- [ ] P13-36: 推进 `vm` module。
  - 范围：为 Desktop/npm 兼容开放原生 `vm.Script`、context 和 timeout。
  - 验收：
    - 禁止逃逸到 private bindings、bridge globals 或 host objects。
    - context resource cleanup 和 memory budget 有测试。
  - Partial landing:
    - `autojs6:profile.vmProfile` 已暴露 P13-36 原生可用快照。
    - `vm` / `node:vm` 默认路由到 Node 原生 builtin，`node_modules/vm`
      不能 shadow。
    - `vm.Script`、context、`compileFunction`、ES module VM APIs、
      dynamic import hooks 使用 Node 原生 runtime surface。
    - follow-up gate 明确要求 context cleanup、memory budget、
      private binding/bridge/host object/process/global leakage probes
      和 packaged APK behavior 证据。
    - Device test: `NodeVmPhase13DecisionInstrumentationTest`。
    - Existing design gate: `NodeVmDesignGateInstrumentationTest`。
    - Host verifier: `tools/nodejs/phase13/verify-vm-module-decision.js`。
    - Gradle gate: `:app:verifyNodePhase13VmModuleDecision`。
    - Report: `docs/nodejs/reports/phase13/P13_36_VM_MODULE_DECISION.md`。
    - Surface manifest evidence: `phase13-vm-module-decision`。

- [x] P13-37: 推进 inspector/devtools debug profile。
  - 范围：允许 debug build 通过 adb forward 使用 inspector/profiling。
  - 验收：
    - release build 稳定拒绝。
    - debug profile 有 explicit opt-in、端口绑定、ADB-only 策略。
    - CPU/heap profile 不泄漏敏感脚本路径或用户数据。
  - 进展：
    - P13-37 partial landing 已添加 `inspectorProfile` 诊断，保持
      `inspector` / `node:inspector` 默认与 release 稳定拒绝。
    - 新增 focused gate:
      `NodeInspectorPhase13DecisionInstrumentationTest`。
    - Existing design/security gates:
      `NodeInspectorDesignGateInstrumentationTest` /
      `NodeInspectorSecurityGateInstrumentationTest`。
    - Host verifier:
      `tools/nodejs/phase13/verify-inspector-debug-profile-decision.js`。
    - Gradle gate: `:app:verifyNodePhase13InspectorDebugProfileDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_37_INSPECTOR_DEBUG_PROFILE.md`。
    - Surface manifest evidence:
      `phase13-inspector-debug-profile-decision`。
  - Remaining: debug session UI/host command、loopback + ADB forward、
    DevTools attach、CPU/heap artifact redaction、packaged behavior 仍未完成。
  - Closure: P13-37 v1.1 keeps `inspector` / `node:inspector` denied by
    default and in release, keeps process debug APIs inert, rejects hostile
    `NODE_OPTIONS=--inspect` and project/package metadata, keeps profiler
    artifacts absent, and keeps local shadow packages denied with synced docs,
    desktop matrix, TypeScript declarations, surface manifest, and Gradle gate.
  - Future debug-profile work: trusted debug UI or host command start/revoke,
    loopback-only listener descriptors, ADB forward setup/cleanup,
    public-interface denial proof, DevTools attach coverage, CPU/heap artifact
    export/redaction/budget/cleanup, and debug-packaged metadata remain future
    gates.

- [x] P13-38: 推进 controlled WASI。
  - 范围：从 pure WebAssembly 扩展到 `autojs:wasi` 或受控 `node:wasi` profile。
  - 验收：
    - preopen 目录受 scoped/authorized roots 控制。
    - fd、env、args、clock/random、network 权限明确。
    - raw unrestricted WASI 仍拒绝。
  - 进展：
    - P13-38 partial landing 已添加 `wasiProfile` 诊断，保持 pure
      WebAssembly 可用、raw `wasi` / `node:wasi` 默认稳定拒绝。
    - 新增 focused gate:
      `NodeWasiPhase13DecisionInstrumentationTest`。
    - Existing WASM/WASI gate:
      `NodeWasmWasiInstrumentationTest`。
    - Host verifier:
      `tools/nodejs/phase13/verify-controlled-wasi-profile-decision.js`。
    - Gradle gate: `:app:verifyNodePhase13ControlledWasiProfileDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_38_CONTROLLED_WASI_PROFILE.md`。
    - Surface manifest evidence:
      `phase13-controlled-wasi-profile-decision`。
  - Remaining: controlled facade、scoped/authorized preopen broker、
    fd/env/args/stdio/clock/random/resource budget、worker integration、
    packaged `.wasm`/WASI behavior 仍未完成。
  - Closure: P13-38 v1.1 keeps pure WebAssembly as the supported path while
    raw `wasi` / `node:wasi`, callable `autojs6:wasi`, local shadow packages,
    and raw preopen/fd/env/process authority stay stable-denied with synced
    docs, desktop matrix, TypeScript declarations, surface manifest, and Gradle
    gate.
  - Future controlled WASI work: scoped/authorized preopen broker, fd/env/args/
    stdio/clock/random/resource budgets, worker integration, packaged `.wasm`
    behavior, hostile corpus, and packaged evidence remain future gates.

- [x] P13-39: 决策 native addon `.node` loading。
  - 范围：为 Desktop profile 评估 signed/packaged native addon。
  - 验收：
    - 默认仍拒绝任意 `.node`。
    - 若开放，必须有 ABI allowlist、签名/哈希、packaged-only、no download-exec、
      dependency scan、crash isolation。
    - `process.dlopen` 和 addon resolve 不能绕过 policy。
  - Progress:
    - P13-39 partial landing 已添加 `nativeAddonProfile` 诊断，保持
      `.node`、`process.dlopen`、`process.binding`、`process._linkedBinding`
      默认拒绝。
    - 新增 focused gate:
      `NodeNativeAddonPhase13DecisionInstrumentationTest`。
    - Existing native addon policy gate:
      `NodeNativeAddonPolicyInstrumentationTest`。
    - Host verifier:
      `tools/nodejs/phase13/verify-native-addon-loading-decision.js`。
    - Gradle gate: `:app:verifyNodePhase13NativeAddonLoadingDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_39_NATIVE_ADDON_LOADING.md`。
    - Surface manifest evidence:
      `phase13-native-addon-loading-decision`。
  - Remaining: signed packaged manifest、ABI allowlist、hash/signature
    verification、packaged-only/no download-exec、dependency scan、crash
    isolation、packaged positive/hostile corpus 仍未完成。
  - Closure: P13-39 v1.1 keeps `.node`, package entry resolution,
    `process.dlopen`, `process.binding`, `process._linkedBinding`, runtime
    download/build/lifecycle/postinstall execution, and unrestricted native
    extraction stable-denied with synced docs, TypeScript declarations, surface
    manifest, dependency-tooling checks, and Gradle gate.
  - Future native addon work: signed packaged manifest, ABI/Node-API/version
    allowlists, hash/signature verification, APK-builder preflight, crash
    isolation, packaged positive smoke, and hostile corpus remain future gates.

- [x] P13-40: 推进 `child_process` scoped runner。
  - 范围：评估桌面 Node 生态需要的子进程能力在 Android 上的替代实现。
  - 验收：
    - Safe profile 拒绝。
    - Desktop profile 只能执行 allowlisted app-private binaries 或 Android shell
      provider，不允许任意路径执行。
    - stdout/stderr/input/timeout/cancel/kill/process cleanup 完整。
  - Progress:
    - P13-40 partial landing 已添加 `childProcessProfile` 诊断，保持
      `child_process` / `node:child_process` 默认拒绝。
    - Debug/Desktop 兼容实验开关
      `autojs.nodejs.child_process.experimental=true` 已可将
      `child_process` / `node:child_process` 的 CommonJS 与 ESM 入口路由到
      Node 原生 builtin，并在 local `node_modules/child_process` shadow
      package 之前生效；该开关 release-forbidden，默认仍关闭。
    - 当前替代路径仍是单独 capability 的 `shell` bridge，不暴露
      `spawn` / `fork` / stdio stream / signal / process handle。
    - 新增 focused gate:
      `NodeChildProcessPhase13DecisionInstrumentationTest`。
    - Existing shell/process gates:
      `NodeShellBridgeInstrumentationTest` /
      `NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest`。
    - Host verifier:
      `tools/nodejs/phase13/verify-child-process-scoped-runner-decision.js`。
    - Gradle gate:
      `:app:verifyNodePhase13ChildProcessScopedRunnerDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_40_CHILD_PROCESS_SCOPED_RUNNER.md`。
    - Surface manifest evidence:
      `phase13-child-process-scoped-runner-decision`。
  - Remaining: scoped API shape、app-private binary packaging、allowlist/hash
    integrity、bounded stdin/stdout/stderr、timeout/cancel/kill/cleanup、
    provider process accounting、packaged smoke、hostile corpus 仍未完成。
  - Closure: P13-40 v1.1 keeps `child_process` / `node:child_process`
    denied by default, keeps the native opt-in debug/build-time and
    release-forbidden, keeps `shell` as the separate scoped command bridge, and
    keeps arbitrary paths, downloaded binaries, shell operators, detached
    processes, inherited handles, raw process handles, fd streams, and signals
    stable-denied with synced docs, desktop matrix, TypeScript declarations,
    surface manifest, and Gradle gate.
  - Future scoped runner work: API shape, app-private binary packaging,
    allowlist/hash metadata, bounded stdin/stdout/stderr, timeout/cancel/kill/
    cleanup, process accounting, packaged metadata/disclosure, hostile corpus,
    rollback controls, and release profile policy remain future gates.

- [x] P13-41: 推进完整 ESM loader parity。
  - 范围：扩展 `.mjs`、`type=module`、TLA、dynamic import、package exports/imports、
    import attributes、CJS interop。
  - 验收：
    - local/package ESM 与桌面 Node 行为差距有 matrix。
    - loader hooks、network import 是否支持有明确决策。
    - source map 和 stack trace 与 CJS 一致。
  - Progress:
    - P13-41 partial landing 已添加 `esmLoaderProfile` 诊断，记录当前
      AutoJs6-managed partial ESM loader，而不是提升为 raw Node loader。
    - 已覆盖 `.mjs`、`type=module`、`.js` 入口源码级 ESM syntax routing、
      TLA、local static import（含常见 multiline/compact import
      declarations）、`$remote/...` 入口 `import.meta` metadata、local dynamic import、package
      `exports` / `imports`、JSON import attributes、simple re-export、scoped
      `import.meta`、ESM/CJS interop 和 sync `require(esm)`。
    - declaration export 已覆盖 identifier declaration 以及对象/数组
      destructuring declaration export，用于兼容 `css-tree` / `jsdom` 这类
      npm package 入口常见形态。
    - partial ESM wrapper 已兼容 ESM package 中
      `import { createRequire } from "module"; const require = ...` 的局部
      `require` 声明，避免与 AutoJs6 wrapper 参数同名冲突。
    - partial ESM residual syntax validation 现在仅检查顶层
      `import`/`export` token，允许 `css-tree` 这类 package 在
      `export default { import: ... }` 对象字面量中使用关键字属性名。
    - named import/export/re-export specifier list 现在允许普通 block/line
      comments，用于兼容 `parse5` / `jsdom` 等真实 npm package 输出中的
      `export { /** @internal */ Parser } from ...` 形态。
    - partial ESM loader 在 no-ICU Embedded Node 上会将 regex literal 中的
      `\p{ID_Start}` / `\p{ID_Continue}` 降级为 ASCII identifier fallback，
      用于兼容 `puppeteer-core` QueryHandler 等真实 npm package；字符串、
      注释和 template literal 内容保持不改写。
    - no-ICU regex literal 降级已扩展到 `\p{ASCII}` / `\P{ASCII}`，
      用于兼容 `puppeteer-core` PSelectorParser 中的 attribute selector
      parser regex。
    - partial ESM transform 现在支持 `export function*` 和
      `export async function*`，用于兼容 `puppeteer-core` HandleIterator 等
      真实 npm package 的 generator helper exports。
    - controlled `data:` URL import 现在支持 inline JavaScript 与 JSON data
      modules；JSON data URL 需要 import attributes，unsupported MIME 与
      data-module relative/package import 仍拒绝。
    - loader hooks、raw `node:module` loader authority、network import、
      `file:` URL import、unrestricted URL import、absolute path import、workingDirectory
      escape、disabled builtin import 继续拒绝。
    - 新增 focused gate:
      `NodeEsmLoaderPhase13DecisionInstrumentationTest`。
    - Existing ESM gates:
      `NodeEsmExperimentalInstrumentationTest` /
      `NodeSourceMapStackTraceInstrumentationTest` /
      `:app:verifyNodePackagedEsmGate`。
    - Host verifier:
      `tools/nodejs/phase13/verify-esm-loader-parity-decision.js`。
    - Gradle gate:
      `:app:verifyNodePhase13EsmLoaderParityDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_41_ESM_LOADER_PARITY.md`。
    - Surface manifest evidence:
      `phase13-esm-loader-parity-decision`。
  - Boundary decision: native loader hooks、raw `node:module` loader authority、
    network/file/unrestricted URL import、custom condition sets/import maps 和
    loader registration stay stable-denied for P13-41 v1.1.
  - Remaining: packaged encrypted package exports/imports graph、broader
    dynamic-import package graph、pending TLA/linking cancellation cleanup、
    full desktop source-map parity 仍未完成。
  - Closure: P13-41 v1.1 completes the controlled ESM loader subset with
    `esmLoaderProfile`, default-on partial ESM/local dynamic import flags,
    `.mjs` / `type=module` entries, local static imports, TLA, JSON import
    attributes, `import.meta`, package `exports` / `imports`, simple re-export,
    sync CJS `require(esm)`, controlled JS/JSON `data:` URL imports, packaged
    ESM/dynamic import smokes, docs, surface manifest, TypeScript declarations,
    and Gradle gate while raw loader hooks, network/file/unrestricted URL
    imports, custom conditions, import maps, and loader registration remain
    stable-denied.
  - Future full-loader work: packaged encrypted package exports/imports graphs,
    broader dynamic-import package graphs, pending TLA/linking cancellation
    cleanup stress, and full desktop DevTools/source-map parity remain future
    gates.

- [x] P13-42: 推进 npm/package manager on Android。
  - 范围：从 host-side bundler/validator 推进到 Android 侧 npm 包安装/管理能力。
  - 验收：
    - `npm i --no-bin-links` 或等价安装流程可用。
    - lifecycle scripts 默认拒绝或 debug-only。
    - bin shim、symlink、native build、postinstall 下载、integrity lock 有策略。
  - Progress:
    - P13-42 partial landing 已添加 `packageManagerProfile` 诊断，记录当前
      host-managed package manager + Android app-private local store substrate
      的边界。
    - Android runtime install 当前限定为 `local_unpacked_only`：可对已解包包目录执行
      install/update/remove/list/verify/prune，并可从 JS 侧 dry-run plan
      install/update/remove。
    - `NodeAndroidPackageInstallManager.nativeValues()` 现在输出 npm CLI、
      registry download、lockfile、lifecycle、bin link、symlink、native payload、
      postinstall download、network install、source authority、read-maintenance
      JS bridge 等策略。
    - `require("package_manager")` 已作为 guarded local mutation facade 暴露：
      `status()`/`policy()`/`sourcePolicy()` 可读，`list/verify/prune` 经
      Android app-private store bridge 执行，`planInstall/planUpdate/planRemove`
      返回 frozen dry-run `PackagePlan`，`install/update/remove` 已提升为受控
      local unpacked mutation，需要 `package_manager.mutate`、`allowMutation: true`
      和对应 dry-run plan 的 `expectedIntegrity`。
    - `planRegistryInstall()` / `planTarballInstall()` 已作为 plan-only source
      boundary API 暴露，返回 frozen
      `autojs6-node-package-manager-remote-plan-v1`，状态为
      `blocked_by_policy`，不触发 Android bridge、network/cache、下载、解包或
      store mutation，也不授予 install authority；其中 `planRegistryInstall()`
      现在可解析调用方提供的 npm `metadata`/`packument`，输出 frozen
      `autojs6-node-package-manager-registry-resolution-v1`，覆盖 dist-tag、
      exact version、常见 semver range、tarball URL、integrity、
      expected-integrity verdict、shasum 诊断。
      `planTarballInstall()` 也输出 frozen
      `autojs6-node-package-manager-tarball-resolution-v1`，覆盖 tarball URL
      protocol/host/path、`.tgz` archive、推断 npm name/version、
      request-vs-inferred match、expected-integrity algorithm 和 sha512 allowlist
      verdict。
    - `require("npm")` 已作为 guarded package-manager alias facade 暴露：
      `status()`/`policy()`/`sourcePolicy()` 可读，本地和 remote policy
      plan/mutation/list/verify/prune 代理到 `package_manager`，但
      `cli()`/`exec()`/`runScript()` 和 registry
      install/publish/pack 仍以稳定 npm policy error 拒绝。
    - 新增 focused instrumentation:
      `NodePackageManagerPhase13DecisionInstrumentationTest`。
    - Existing Android store gate:
      `NodeAndroidPackageInstallManagerInstrumentationTest` /
      `:app:verifyNodePhase13AndroidPackageInstallManager`。
    - Host package manager boundary:
      `tools/nodejs/phase10/autojs6-pm.js` /
      `docs/nodejs/reports/phase10/PACKAGE_MANAGER_BOUNDARY.md`。
    - Host verifier:
      `tools/nodejs/phase13/verify-package-manager-android-decision.js`。
    - Gradle gate:
      `:app:verifyNodePhase13PackageManagerAndroidDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_42_PACKAGE_MANAGER_ANDROID.md`。
    - P13-42 v1.1 policy: Android registry downloader, cache management,
      tarball extraction, and integrity-lock install authority stay
      stable-denied as runtime user APIs; Phase 10 host-managed validation
      remains the equivalent install path.
    - Surface manifest evidence:
      `phase13-package-manager-android-decision`。
  - Closure: host-managed package validation plus Android local-store substrate
    is the accepted v1.1 equivalent install path; npm CLI, registry/tarball
    install authority, lifecycle/bin/native behavior, and raw mutation remain
    fail-closed with policy diagnostics.
  - Future promotion work: Android 侧 registry metadata/tarball 下载、npm CLI、
    unrestricted package-manager install authority、debug-only lifecycle runner、bin shim、signed
    native payload policy、native build strategy、packaged dependency install/distribution
    evidence、真实 npm corpus 通过率提升仍未完成。

- [x] P13-43: 扩展 Node stdlib conformance。
  - 范围：`crypto`、`stream`、`zlib`、`buffer`、`url`、`os`、`readline`、
    `tty`、`perf_hooks`、`async_hooks` 等。
  - 验收：
    - 每个模块有 conformance subset。
    - Android 不适用 API 有稳定差异说明。
    - npm corpus 中真实依赖通过率提升。
  - Progress:
    - P13-43 partial landing 已添加 `stdlibProfile` 诊断，记录当前 Safe
      Node stdlib subset、代表性模块状态、Android 稳定差异、npm corpus
      `real_npm_phase13_53_fixture_corpus` 和 desktop differential 证据，同时保持
      raw desktop parity、
      raw native handles、unrestricted network、broad terminal、raw
      `process.getBuiltinModule` bypass 和 `zlib/promises` 未提升。
    - `process.getBuiltinModule` 现在是 controlled partial：允许的 builtin
      返回与 `require()` 相同的受控 wrapper，未知 builtin 返回
      `undefined`，禁用 builtin 仍走 policy denial。
    - `TextDecoder` 在 no-ICU runtime 下补充 `utf-8` / `utf-16le` /
      `utf-16be` 的 `fatal: true` Unicode fallback，用于 pure-JS npm
      兼容；不声明 full ICU encoding coverage。
    - `require("punycode/")` 和 `require.resolve("punycode/")` 现在作为
      npm package-root trailing slash 请求进入 `node_modules/punycode`，
      保持 `require("punycode")` / `require("node:punycode")` 仍命中受控
      builtin wrapper，用于兼容 `tr46` / `jsdom` 这类真实 npm 依赖。
    - `buffer` wrapper 现在使用冻结的普通对象导出，保留
      `hasOwnProperty()`，用于兼容 `cheerio` / `iconv-lite` /
      `safer-buffer` 这类真实 npm 依赖。
    - `stream` wrapper 现在暴露 callable module shape，并保持 prototype
      指向 native `Stream.prototype`，使 `import Stream from "node:stream"`
      与 `body instanceof Stream` 这类 `node-fetch` 检查正常工作；同时继续
      保持 `stream/promises` 独立导入、`stream.promises` 不提升。
    - `zlib` / Web `CompressionStream` 输入上限提升到 8 MiB，输出上限提升到
      32 MiB，用于兼容真实文档页面和 `node-fetch` 解压响应；超过上限仍以
      `ERR_OUT_OF_RANGE` 稳定拒绝。
    - 普通 one-shot Embedded Node 脚本默认 timeout 从 probe 用的 3000ms
      调整为 300000ms；显式 `timeoutMs` 仍保持原语义，用于降低大 npm
      module graph 启动时的误超时。
    - Embedded Node 结果回传现在会从 `result_native_payload` 移除
      `result_json`、`error_stack`、`stdout/stderr` 和 fd 捕获全文等
      已有专用字段的大块重复内容，并对剩余 native payload 应用
      512 项 / 65536 字符预算，避免 `cheerio` / `jsdom` 这类真实
      npm 依赖的大失败 envelope 触发 Android Binder 事务过大。
    - `require("undici").DecoratorHandler` 现在暴露可继承的 shape-only
      handler delegate，支持 jsdom `decompress-interceptor` 的
      `super.onResponse*()` 调用和旧 `onHeaders` / `onData` /
      `onComplete` 委托，同时保持 raw Undici dispatcher / socket 管线拒绝。
    - 已新增 `NodeStdlibPhase13DecisionInstrumentationTest`，覆盖
      `crypto`、`stream` / `stream/promises` / `stream/consumers` /
      `stream/web`、`zlib`、`buffer`、`url`、`os`、`readline` /
      `readline/promises`、`tty`、`perf_hooks`、`async_hooks` 的代表性
      Safe subset，以及 controlled `process.getBuiltinModule` 与
      `zlib/promises` 的稳定 denial。
    - 已新增 host verifier:
      `tools/nodejs/phase13/verify-stdlib-conformance-decision.js`。
    - real npm corpus 已新增 3 个 stdlib-dependent fixed fixture：
      `stdlib-digest-stream-package`、`perf-async-hooks-package` 和
      `url-buffer-codec-package`，并在 `phase13Compatibility.targetedFixtureDeltas`
      中记录 stdlib +3 pass delta。
    - Gradle gate:
      `:app:verifyNodePhase13StdlibConformanceDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_43_STDLIB_CONFORMANCE.md`。
    - Surface manifest evidence:
      `phase13-stdlib-conformance-decision`。
  - Remaining: full desktop stdlib parity、更多 hostile inputs/edge cases、
    Android terminal/OS 差异矩阵、更多 stdlib-dependent real npm corpus
    dashboard 和 packaged stdlib 行为证据仍未完成。
  - Closure: the v1.1 representative stdlib gate covers `stdlibProfile`,
    representative Safe subset module instrumentation, controlled
    `process.getBuiltinModule`, stable `zlib/promises` denial, Android
    difference docs, builtin matrix rows, real npm +3 stdlib corpus delta,
    modern-core differential evidence, TypeScript declarations, surface
    manifest, and Gradle gate.
  - Future full-stdlib work: broader hostile inputs, Android terminal/OS edge
    cases, packaged lifecycle evidence, additional stdlib-dependent package
    dashboards, raw native handles, broad terminal behavior, unrestricted
    network/OS surfaces, and full desktop parity remain future gates.

- [x] P13-44: 推进 process/env/argv parity。
  - 范围：补齐 `process.argv`、`process.env`、cwd、exit、signals、versions、
    resourceUsage 等。
  - 验收：
    - env 写入、敏感变量、Android process signal 差异有 policy。
    - `process.exit()` 与 AutoJs script lifecycle 协调。
  - Progress:
    - P13-44 partial landing 已添加 `processParityProfile` 诊断，记录当前
      Safe process subset: `argv`、controlled string-coercing mutable `env`、scoped
      cwd/chdir、`exitCode`、versions、timing、cpu/memory/resourceUsage、
      lifecycle-mapped `process.exit(code)`、`process.permission`、
      `process.report.getReport()`、bounded stdio、`process` /
      `node:process` alias，以及 Android signal/native/process handle 差异。
    - Focused instrumentation:
      `NodeProcessParityPhase13DecisionInstrumentationTest` 验证
      `processParityProfile`、env 敏感变量过滤、script-local env 写入字符串化、
      scoped cwd/chdir、`process.exit(code)` lifecycle mapping、signal/native
      API 稳定拒绝、versions/resource/timing/report/permission/stdio 行为。
    - The host verifier now treats undeclared bridge capability denial as
      covered evidence rather than a remaining P13-45 blocker.
    - Existing evidence:
      `NodeProcessSweepInstrumentationTest`、`EmbeddedScriptMvpInstrumentationTest`、
      inspector env filtering tests、compile-cache env filtering tests 和
      modern-core process permission differential fixture 继续覆盖
      process/env/argv 关键行为。
    - Host verifier:
      `tools/nodejs/phase13/verify-process-parity-decision.js` 生成
      `phase13-process-parity-decision`。
    - Gradle gate:
      `:app:verifyNodePhase13ProcessParityDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_44_PROCESS_PARITY.md`。
    - Surface manifest evidence:
      `phase13-process-parity-decision`。
  - Boundary decision: full desktop process parity、raw Android signal delivery、
    native/raw process exit、raw inherited env、raw process handles/native
    bindings、`process.dlopen`/`process.binding` 和 `report.writeReport()`
    stay stable-denied for P13-44 v1.1.
  - Closure: Safe process subset runtime behavior, Android signal/native exit
    stable denial, inherited-env policy, and lifecycle-mapped process exit are
    covered.
  - Future packaged evidence: packaged lifecycle argv/env/cwd/process-exit
    smoke remains gated by packaged profile promotion.

### Track D: Packaging, plugins, and distribution

- [x] P13-45: 扩展 packaged APK capability metadata。
  - 范围：让 packaged APK 能表达 Pro/Desktop profile、permissions、Node builtins、
    native assets、long-running、network、filesystem roots。
  - 验收：
    - manifest synthesis 与 project/package metadata 一致。
    - 未声明能力在 packaged runtime 中拒绝。
  - Progress:
    - P13-45 partial landing 已添加 `packagedCapabilityProfile` 诊断，记录
      packaged profile/builtin/native asset/filesystem root metadata 当前仍是
      diagnostic-only，不会自动提升权限。
    - `NodeBridgePermissionManifest` 现在输出
      `packagedMetadata`，从 `project.json node.*` 和
      `package.json autojs6(.node).*` 汇总 profile、profiles、builtins、
      nativeAssets、filesystemRoots、executionMode、network 和权限映射。
    - Focused instrumentation:
      `NodePackagedCapabilityMetadataPhase13DecisionInstrumentationTest`
      验证 metadata 解析、JS 冻结报告、`network` 到
      `android.permission.INTERNET` 的映射、metadata warning 在
      `packagedMetadata.warnings` / JS profile 中可见、以及未声明
      `accessibility` 在 dispatch 前稳定拒绝。
    - Existing evidence:
      `NodeBridgePermissionManifestInstrumentationTest`、`ApkBuilder`、
      `NodeProjectRuntimeCompatibilityDescriptor`、`NodeProjectLaunchDescriptor`、
      `EmbeddedNodePackagedApkSmokeTest` 和 `NodeWorkManagerBridgeInstrumentationTest`
      继续覆盖 packaged permissions、descriptor 版本和 packaged metadata 序列化。
    - Host verifier:
      `tools/nodejs/phase13/verify-packaged-capability-metadata-decision.js`
      生成 `phase13-packaged-capability-metadata-decision`。
    - Gradle gate:
      `:app:verifyNodePhase13PackagedCapabilityMetadataDecision`。
    - Report:
      `docs/nodejs/reports/phase13/P13_45_PACKAGED_CAPABILITY_METADATA.md`。
    - Surface manifest evidence:
      `phase13-packaged-capability-metadata-decision`。
  - Closure: metadata parsing, manifest synthesis, runtime diagnostics, and
    undeclared-capability denial are covered.
  - Future promotion work: profile/builtin/native asset/filesystem root metadata
    尚不能驱动 reviewed packaged synthesis；完整 install-run packaged APK
    smoke、用户披露、rollback、权限提示和 Pro/Desktop profile device matrix
    仍未完成。

- [x] P13-46: 推进 plugin system toward Pro/Desktop parity。
  - 范围：从 JS-only local plugin 扩展到安装、更新、签名、权限、资源、packaged
    distribution。
  - 验收：
    - Android provider plugin、native plugin、remote marketplace 分阶段决策。
    - 插件能力不能超出 host project/package 声明。
  - Progress:
    - P13-46 partial landing 已添加 `pluginSystemProfile` 诊断，记录
      JS-only local manager v1 是当前唯一 callable plugin surface。
    - Focused instrumentation:
      `NodePluginSystemParityPhase13DecisionInstrumentationTest` 验证
      Android provider plugin、native plugin、remote marketplace 仍为
      staged/denied，并验证 plugin manifest 请求的 `network` 不能越过 host
      `node.permissions`。
    - Existing evidence: `NodePluginSystemInstrumentationTest` 继续覆盖
      install/enable/configure/load/start/stop/unload/debug reload、native
      addon 拒绝、path escape 拒绝、resource cleanup 和 capability subset。
    - Gate:
      `tools/nodejs/phase13/verify-plugin-system-parity-decision.js` 生成
      `phase13-plugin-system-parity-decision` 报告，并接入
      `:app:verifyNodePhase13PluginSystemParityDecision`。
  - Remaining:
    - signed archive、Android provider plugin adapter、native isolation、
      marketplace install/update、packaged plugin distribution 仍需后续 gate。
  - Closure: current JS-only local manager diagnostics, host capability ceiling,
    plugin namespace denial, native payload denial, docs, release matrix, surface
    manifest, and Gradle gate are covered for the v1.1 decision.
  - Future promotion work: signed archives, Android provider plugin adapters,
    native plugin isolation, marketplace install/update, and packaged plugin
    distribution remain staged future gates rather than P13-46 v1.1 blockers.

- [x] P13-47: 增加 dependency compatibility corpus。
  - 范围：扩大真实 npm 包测试，按缺失能力分类。
  - 验收：
    - 报告给出 package、missing builtin、native addon、postinstall、network、fs
      等原因。
    - P13 每次放开桌面能力都能看到 corpus 通过率变化。
  - Progress:
    - P13-47 partial landing 已在 `npm_compat_real/manifest.json` 增加
      `phase13Compatibility` 分类元数据，固定 53 个离线 fixture baseline。
    - `phase13Compatibility.targetedFixtureDeltas` 记录 stdlib +3 pass delta，
      对应 `stdlib-digest-stream-package`、`perf-async-hooks-package` 和
      `url-buffer-codec-package`。
    - 分类覆盖 `missing_builtin`、`native_addon`、`postinstall`、`network`
      和 `fs`，每类包含 package/reason rows 与 fixture 引用。
    - `NodeRealNpmCompatInstrumentationTest.fixtureMetadataIsCompleteAndSafe`
      现在验证 P13 分类 schema、fixture 引用和 reason rows。
    - `tools/nodejs/differential/run-differential-tests.js` 现在输出
      `phase13Compatibility` JSON，并在 v1.1 differential Markdown 中输出
      "Phase 13 Dependency Compatibility Categories" pass-rate 表。
    - `run-differential-tests.js --autojs-connected-results <dir|file>` 可直接
      扫描 Gradle connected test 结果目录、XML、logcat 和 test log 中的
      `realnpm.differential.result=` JSON 行。
    - Gate:
      `tools/nodejs/phase13/verify-dependency-compatibility-corpus.js` 生成
      `phase13-dependency-compatibility-corpus` 报告，并接入
      `:app:verifyNodePhase13DependencyCompatibilityCorpus`。
  - Remaining:
    - CI 仍需调度 connected AutoJs6 real npm corpus，并把 connected-results
      artifact 目录传给 host differential report。
    - 真实 registry drift、unsafe native/postinstall 包执行仍保持拒绝，仅以
      metadata blocker category 表达。
  - Closure: the v1.1 fixed 53-fixture corpus, P13 compatibility taxonomy,
    required reason kinds, stdlib +3 targeted delta, metadata instrumentation,
    differential category reporting, summary rows, docs, surface manifest, and
    Gradle gate are covered.
  - Future CI work: connected AutoJs6 real npm corpus artifact scheduling,
    live registry drift testing, and unsafe native/postinstall package execution
    remain outside the default corpus gate.

- [x] P13-48: 实现 package lock and integrity policy。
  - 范围：为 Android-side package manager、plugins、native addon 做统一完整性策略。
  - 验收：
    - lockfile、hash、签名、回滚、缓存清理、损坏恢复完整。
  - Progress:
    - P13-48 partial landing 已添加 `NodePackageIntegrityPolicy` 与
      `packageIntegrityProfile` 诊断，统一记录 Android package manager、
      plugin packages、native addons 的 lock/hash/signature/rollback/cache
      cleanup/corruption recovery 策略。
    - `packageIntegrityProfile.recoveryActions` 暴露 verify/prune/reinstall/
      transaction restore/disable unsigned subject 的机器可读恢复动作。
    - Android local store 已将 policy 暴露到 `nativeValues()`，并通过 focused
      instrumentation 覆盖 integrity mismatch rollback、stale transaction
      prune、corruption detection 与 reinstall/update recovery。
    - Plugin/native 侧当前仍 fail-closed：unsigned plugin install、unsigned
      native addon load、raw registry install、lifecycle scripts、
      postinstall downloads 和 unverified cache reuse 均未放开。
    - Host verifier:
      `tools/nodejs/phase13/verify-package-lock-integrity-policy.js`。
    - Gradle gate: `:app:verifyNodePhase13PackageLockIntegrityPolicy`。
    - Report:
      `docs/nodejs/reports/phase13/P13_48_PACKAGE_LOCK_INTEGRITY_POLICY.md`。
    - Remaining P13-48 blockers: signed plugin archive extraction/update,
      marketplace cache prune/rollback UI, and user-facing corruption recovery
      diagnostics.
    - Signed native addon artifact verification is delegated to the P13-39
      packaged native path; P13-48 keeps the shared integrity policy and
      unsigned native addon load fail-closed until that path exists.
    - Closure: P13-48 v1.1 keeps `NodePackageIntegrityPolicy`,
      `packageIntegrityProfile`, Android local-store rollback/prune/corruption
      recovery, shared plugin/native integrity vocabulary, P13-39 native-addon
      delegation, and fail-closed unsigned plugin/native install, raw registry
      install, lifecycle scripts, postinstall downloads, and unverified cache
      reuse synchronized with docs, surface manifest, TypeScript declarations,
      and Gradle gate.
    - Future distribution work: signed plugin archive extraction/update,
      marketplace cache prune/recovery UI, plugin rollback UI, runtime registry
      install, mutating package-manager facades, user-facing corruption
      diagnostics, and per-profile rollback UX remain future gates.

### Track E: Security, UX, and rollback

- [x] P13-49: 实现 profile selection UX and diagnostics。
  - 范围：为 Safe/Pro/Desktop/Debug profile 提供项目设置、打包设置和诊断输出。
  - 验收：
    - 用户能清楚看到启用了哪些高风险能力。
    - 失败时错误消息能指出需要哪个 profile/capability/permission。
  - 进展：
    - P13-49 partial landing 已添加 `NodeProfileSelectionDiagnostics`,
      `autojs6:profile.profileSelection`, Doctor Profile Selection 文本/JSON,
      TypeScript declarations, and focused instrumentation.
    - Missing bridge capability errors now expose `requiredProfiles`,
      `requiredNodePermissions`, `requiredAndroidPermissions`, and
      `profileSelectionHint`.
    - Failure hints now include `declarationExamples` for
      `node.permissions`, `project.json` (`node.permissions`), and
      `package.json` (`autojs6.node.permissions`) declarations.
    - Current effective profile remains `safe_default`; requested
      `pro_compat_opt_in`, `desktop_compat_opt_in`, and `debug_unsafe_lab`
      metadata is diagnostic-only and does not grant authority.
    - P13-49 v1.1 policy: effective Pro/Desktop/Debug profile switching stays
      stable-denied; requested profile metadata remains diagnostic-only.
    - Gradle gate: `:app:verifyNodePhase13ProfileSelectionDiagnostics`。
    - Report:
      `docs/nodejs/reports/phase13/P13_49_PROFILE_SELECTION_DIAGNOSTICS.md`。
    - Closure: runtime profile selection diagnostics, Node Doctor profile
      selection output, high-risk capability rows, missing-capability error
      hints, and declaration examples are covered.
    - Future implementation work: user-facing project settings UI and packaged
      builder profile UI.

- [x] P13-50: 扩展 security corpus for relaxed capabilities。
  - 范围：为 raw network、fs 放宽、Java interop、child_process、native addon、
    inspector、WASI 增加攻击语料。
  - 验收：
    - 每个放开能力都有正向和负向测试。
    - 旧 Safe profile 不因新能力产生 regression。
  - Progress:
    - P13-50 partial landing 已添加
      `docs/nodejs/security/RELAXED_CAPABILITY_SECURITY_CORPUS.json`,
      覆盖 raw network、filesystem relaxation、Java interop、
      `child_process`、native addon、inspector、WASI 七类 relaxed
      capabilities 的 positive tests、negative tests、abuse cases、
      CI evidence 与 rollback posture。
    - Corpus 现在强制每个 relaxed capability 提供 `runtimeGuards` 和
      `promotionPreconditions`，记录 fail-closed error/flag、rollback、
      privacy disclosure、packaged metadata 与 connected-CI 前置条件。
    - `NodeSecurityRegressionInstrumentationTest` 已新增 focused corpus：
      raw network deny、scoped fs positive/escape、shell bridge 替代
      `child_process`、native addon deny、inspector deny、raw WASI deny、
      Java interop allowlist/denylist。
    - Host verifier:
      `tools/nodejs/phase13/verify-relaxed-capability-security-corpus.js`。
    - Gradle gate:
      `:app:verifyNodePhase13RelaxedCapabilitySecurityCorpus`。
    - Report:
      `docs/nodejs/reports/phase13/P13_50_RELAXED_CAPABILITY_SECURITY_CORPUS.md`。
    - P13-50 closeout: corpus/security evidence is ready; real raw capability
      promotion remains owned by P13-51/P13-52 and later promotion gates.

- [x] P13-51: 增加 per-profile rollback playbook。
  - 范围：更新 rollback 文档和 Gradle/property 开关。
  - 验收：
    - 可以快速禁用单个 capability、整个 Pro profile、整个 Desktop profile。
    - packaged APK 已发布场景有降级策略。
  - Progress:
    - P13-51 partial landing 已添加
      `NodeProfileRollbackPolicy`、`autojs6:profile.profileRollbackPolicy`
      和 Node Doctor Profile Rollback Policy 文本/JSON。
    - Gradle 默认新增 `autojs.nodejs.profile.pro.enabled=false`,
      `autojs.nodejs.profile.desktop.enabled=false`,
      `autojs.nodejs.profile.debugUnsafeLab.enabled=false` 和
      `autojs.nodejs.relaxedCapabilities.enabled=false`，并暴露对应
      `BuildConfig` 字段。
    - Rollback playbook 记录 Safe/Pro/Desktop/Debug profile 独立禁用、
      raw/relaxed capability 降级动作、crash/stress/security/package
      triggers，以及 packaged APK Safe-profile patch 策略。
    - Rollback policy 现在暴露 `raw_network` capability id，与 P13-50 corpus
      对齐，并映射到 `autojs.nodejs.rawNetworkModules.experimental=false`。
    - Host verifier:
      `tools/nodejs/phase13/verify-profile-rollback-playbook.js`。
    - Gradle gate:
      `:app:verifyNodePhase13ProfileRollbackPlaybook`。
    - Report:
      `docs/nodejs/reports/phase13/P13_51_PROFILE_ROLLBACK_PLAYBOOK.md`。
    - P13-51 v1.1 policy: runtime profile promotion rollback wiring and
      automatic crash circuit breaker implementation are deferred until
      effective profile switching is promoted.
    - Closure: default-off profile/relaxed-capability switches, Safe fallback
      policy, Node Doctor diagnostics, TypeScript declarations, raw_network
      rollback id, packaged downgrade strategy, and remote packaged
      kill-switch rejection are covered.
    - Future implementation work: settings UI, packaged builder UI, privacy
      disclosure prompts, and packaged Safe-profile downgrade evidence.

- [x] P13-52: 增加 privacy and disclosure policy。
  - 范围：截图、录音、悬浮窗、无障碍、网络、文件系统、Java interop、子进程等
    能力的用户提示和前台披露。
  - 验收：
    - Android 权限、前台服务、通知和 UI 提示一致。
    - packaged APK 不因缺少披露进入隐式高权限状态。
  - Progress:
    - P13-52 partial landing 已添加
      `NodePrivacyDisclosurePolicy` 和
      `autojs6:profile.privacyDisclosurePolicy`，覆盖截图、录音、
      悬浮窗、无障碍、网络、filesystem relaxation、Java interop、
      root/Shizuku shell、`child_process`、native addon、inspector、
      WASI 的 prompt/disclosure/Android permission/packaged denial 元数据。
    - Privacy policy 现在单独暴露 `raw_network` disclosure surface，与
      P13-50 corpus 和 P13-51 rollback id 对齐。
    - Node Doctor JSON/text 已新增 Privacy Disclosure Policy section；
      TypeScript declarations 和 smoke 已覆盖 `PrivacyDisclosurePolicy`、
      `PrivacyDisclosureSurface`、channel、packaged policy 和 Doctor privacy
      字段。
    - Host verifier:
      `tools/nodejs/phase13/verify-privacy-disclosure-policy.js`。
    - Gradle gate:
      `:app:verifyNodePhase13PrivacyDisclosurePolicy`。
    - Report:
      `docs/nodejs/reports/phase13/P13_52_PRIVACY_DISCLOSURE_POLICY.md`。
    - Closure: policy metadata, runtime diagnostics, Node Doctor output,
      TypeScript declarations, raw_network disclosure id, crash circuit breaker
      privacy implications, and remote packaged kill-switch rejection are
      covered.
    - Future implementation work: settings UI prompt/localization、foreground
      service notification ownership、packaged disclosure manifest review UI、
      installer/runtime enforcement、device matrix 仍未完成；策略仍为
      metadata-only 且不授予 Pro/Desktop/Debug authority。

### Track F: Docs, examples, and final gate

- [x] P13-53: 发布 Pro parity examples。
  - 范围：新增示例项目覆盖 `$autojs.java`、`rhino.install`、UI、floaty、tasks、
    screenshot/OCR、accessibility。
  - 验收：
    - 每个示例有 expected output、required capabilities、packaged behavior。
    - 不可用条件写入 README 和 smoke skip reason。
  - Progress:
    - P13-53 partial landing adds `sample/nodejs/pro-parity-suite` with
      guarded snippets for `$autojs.java`, `rhino.install`, UI,
      floaty/overlay, tasks, screenshot/OCR, and accessibility.
    - The Pro suite now includes `smoke.cjs` plus
      `NodeExampleProjectInstrumentationTest#proParitySafeSubsetExamplesRun`
      as safe subset smoke for Activity-owned UI layout and WorkManager
      one-shot schedule/cancel behavior.
    - Each catalog entry in `sample/nodejs/pro-parity-suite/examples.json`
      records required capabilities, expected output, packaged behavior, and
      skip reason.
    - Host verifier: `tools/nodejs/phase13/verify-pro-parity-examples.js`.
    - Gradle gate: `:app:verifyNodePhase13ProParityExamples`.
    - Report:
      `docs/nodejs/reports/phase13/P13_53_PRO_PARITY_EXAMPLES.md`.
    - Remaining P13-53 blockers: live packaged Pro examples, prompt/disclosure
      UI, provider promotion, permission device matrix, and high-risk Pro
      snippet runtime smoke inclusion remain gated.
    - Closure: the v1.1 Pro example catalog, required-capability metadata,
      packaged-behavior rows, skip reasons, prompt/disclosure denial notes,
      safe subset smoke, docs, surface manifest, and Gradle gate are covered.
    - Future promotion work: live packaged Pro examples, reviewed
      prompt/disclosure UI, provider promotion, permission device matrix, and
      high-risk Pro runtime smoke remain future gates.

- [x] P13-54: 发布 desktop Node parity examples。
  - 范围：新增示例覆盖 ESM、worker、http/https、fs advanced、WASI、inspector、
    npm dependency。
  - 验收：
    - 每个示例标注所需 profile。
    - Safe profile 下的拒绝路径也有示例或测试。
  - Progress:
    - P13-54 partial landing adds `sample/nodejs/desktop-parity-suite` with
      guarded snippets for ESM, `worker_threads`, HTTP/HTTPS, raw network,
      advanced fs, WASI, inspector, and sanitized pure JavaScript npm
      dependency usage.
    - The desktop catalog now includes an explicit `raw_network` example and
      Safe-profile denial marker aligned with the P13-50/P13-51/P13-52
      relaxed-capability id.
    - The desktop suite now includes `smoke.cjs` plus
      `NodeExampleProjectInstrumentationTest#desktopParitySafeSubsetExamplesRun`
      as safe subset smoke for managed local ESM and checked-in pure
      JavaScript dependency behavior.
    - Each catalog entry in `sample/nodejs/desktop-parity-suite/examples.json`
      records `desktop_compat_opt_in`, required capabilities, expected output,
      packaged behavior, skip reason, and Safe-profile denial output.
    - Safe-profile denial examples are centralized in
      `sample/nodejs/desktop-parity-suite/snippets/safe-denials.cjs`.
    - Host verifier: `tools/nodejs/phase13/verify-desktop-parity-examples.js`.
    - Gradle gate: `:app:verifyNodePhase13DesktopParityExamples`.
    - Report:
      `docs/nodejs/reports/phase13/P13_54_DESKTOP_PARITY_EXAMPLES.md`.
    - Remaining P13-54 blockers: packaged desktop parity, provider promotion,
      integrity policy, Android package-manager parity, and high-risk desktop
      snippet runtime smoke inclusion remain gated.
    - Closure: the v1.1 desktop example catalog, target-profile metadata,
      Safe-profile denial markers, packaged-behavior rows, skip reasons, safe
      subset smoke, docs, surface manifest, and Gradle gate are covered.
    - Future promotion work: packaged desktop parity examples, provider
      promotion, integrity policy, Android package-manager parity, device
      evidence, and high-risk desktop runtime smoke remain future gates.

- [x] P13-55: 更新 TypeScript declaration package。
  - 范围：为 Pro/Desktop profile 增加条件类型或文档注释。
  - 验收：
    - `docs/nodejs/types/autojs6-node` 覆盖新增模块。
    - type smoke 覆盖 Safe/Pro/Desktop 差异。
  - Progress:
    - P13-55 partial landing adds
      `docs/nodejs/types/autojs6-node/profile_capabilities.d.ts` with
      `RuntimeProfileId`, `ProfileModuleCatalog`, `ProfileModuleModeFor`,
      `ProfileRequiredFor`, `SafeDefaultModeFor`, `PackagedBehaviorFor`, and
      `ProfileDeclarationMatrix`.
    - The declaration matrix covers P13-53 and P13-54 surfaces including
      `$autojs.java`, `java`, `rhino.install`, UI, overlay, tasks,
      screenshot/OCR, accessibility, ESM, `worker_threads`, HTTP/HTTPS,
      raw network, advanced fs, WASI, inspector, package manager, and pure-JS
      npm usage.
    - The matrix now includes `raw_network` as a metadata-only
      `desktop_compat_opt_in` surface so the P13-54 raw network example has
      matching TypeScript profile evidence.
    - `sample/nodejs/typescript-smoke/src/main.ts` now type-checks
      Safe/Pro/Desktop/Debug conditional profile differences without declaring
      denied desktop modules as callable modules.
    - `verify-type-declaration-profiles` now checks P13-49 profile selection,
      P13-51 rollback, and P13-52 privacy/disclosure type alignment through
      `autojs_global.d.ts`, the type README, and `typescript-smoke`.
    - Host verifier:
      `tools/nodejs/phase13/verify-type-declaration-profiles.js`.
    - Gradle gate: `:app:verifyNodePhase13TypeDeclarationProfiles`.
    - Report:
      `docs/nodejs/reports/phase13/P13_55_TYPE_DECLARATION_PROFILES.md`.
    - Closure: profile declaration matrix, index wiring, README docs,
      TypeScript smoke, Safe/Pro/Desktop/Debug differences, and
      P13-49/P13-51/P13-52 type alignment are covered.
    - Future release work: runtime profile promotion and generated declaration
      package release checks remain gated.

- [x] P13-56: 更新 migration and Pro9 comparison docs。
  - 范围：同步 `MIGRATION_FROM_RHINO.md`、`INTEGRATION_STATUS_PRO9_COMPARISON.md`、
    `COMPATIBILITY_NOTES_PRO_AUTOX.md`。
  - 验收：
    - 明确哪些能力已经 Pro-compatible，哪些是 Desktop-compatible，哪些仍 gated。
    - 不把 debug-only 或 provider POC 写成正式可用。
  - Progress:
    - P13-56 partial landing synchronizes `MIGRATION_FROM_RHINO.md`,
      `INTEGRATION_STATUS_PRO9_COMPARISON.md`, and
      `COMPATIBILITY_NOTES_PRO_AUTOX.md` with shared `Pro-compatible`,
      `Desktop-compatible`, and `gated/diagnostic-only` labels.
    - The docs explicitly state that debug-only/provider POC is not formal
      availability and keep `sample/nodejs/pro-parity-suite`,
      `sample/nodejs/desktop-parity-suite`, and `profile_capabilities.d.ts` as
      evidence or metadata rather than runtime authority.
    - The migration docs now list the `raw_network` desktop marker and raw
      network modules opt-in as evidence/metadata only, matching P13-54 and
      P13-55 without granting default authority.
    - The P13-56 verifier links the docs taxonomy to the P13-57 summary output
      for completed/open/deferred/rejected state.
    - Host verifier:
      `tools/nodejs/phase13/verify-migration-pro9-comparison.js`.
    - Gradle gate: `:app:verifyNodePhase13MigrationPro9Comparison`.
    - Report:
      `docs/nodejs/reports/phase13/P13_56_MIGRATION_PRO9_COMPARISON.md`.
    - Closure: migration, Pro9 comparison, and Pro/AutoX notes share the same
      Pro-compatible/Desktop-compatible/gated taxonomy without treating
      debug-only/provider POC evidence as formal availability.
    - Future documentation work: runtime profile promotion, packaged
      Pro/Desktop migration cookbook, prompt/disclosure UX, and device
      permission matrix remain gated.

- [ ] P13-57: 发布 Phase 13 summary generator and final gate。
  - 范围：新增 summary generator，汇总 task checklist、surface manifest、
    blocker registry、profile truth report 和 conformance reports。
  - 建议产物：
    - `tools/nodejs/phase13/generate-phase13-summary.js`
    - Gradle task：`:app:generateNodePhase13Summary`
    - `docs/nodejs/reports/phase13/PHASE13_SUMMARY.md`
  - 验收：
    - summary 能列出 completed/open/deferred/rejected。
    - 每个 promoted surface 都有 evidence。
    - open blocker 不会被误报为完成。
  - Progress:
    - P13-57 partial landing adds
      `tools/nodejs/phase13/generate-phase13-summary.js`.
    - Gradle gate: `:app:generateNodePhase13Summary`.
    - Canonical docs output:
      `docs/nodejs/reports/phase13/PHASE13_SUMMARY.md`.
    - Build reports: `build/reports/nodejs/phase13-summary.json` and
      `build/reports/nodejs/phase13-summary.md`.
    - The summary aggregates task checklist status, Phase 13 surface manifest
      status, `FINAL_BLOCKER_REGISTRY.json`, `phase13-capability-truth`, and
      generated conformance reports.
    - The summary keeps old registry status for `raw_network` history but
      classifies it as open in Phase 13 because P13-50 through P13-56 reopened
      it as a gated Desktop/relaxed-capability track.
    - It lists completed/open/deferred/rejected categories, verifies promoted
      surfaces have evidence, and keeps open or decision-required blockers out
      of the completed bucket.
    - Remaining P13-57 blockers: runtime Pro/Desktop promotion, required
      internal blocker closure, final soak evidence, packaged Pro/Desktop
      evidence, and strict release completion remain open.

## 推荐执行顺序

1. P13-01 到 P13-04：先建立 Phase 13 gate、manifest 和 profile 词汇。
2. P13-05 到 P13-10：先关闭 runtime graduation blockers，避免后续高风险能力
   缺 crash/stress/slot 证据。
3. P13-11 到 P13-18：先做 Auto.js Pro 用户最直观的入口、Java/UI/floaty 能力。
4. P13-19 到 P13-27：补齐自动化、截图、任务和 Rhino augment 剩余高价值缺口。
5. P13-28 到 P13-44：逐步放开桌面 Node.js 标准库和 npm 能力。
6. P13-45 到 P13-52：同步打包、插件、安全、UX 和 rollback。
7. P13-53 到 P13-57：补示例、类型、迁移文档和 Phase summary。

## Phase 13 完成定义

Phase 13 不要求一次性实现完整 Auto.js Pro + 桌面 Node.js parity，但完成时应至少
达到：

- Safe/Pro/Desktop/Debug profile 模型被代码、文档、测试共同识别。
- Phase 12 remaining blockers 至少关闭 required internal 的核心项，或明确拆出
  后续阻塞原因。
- `$autojs`、Java interop、UI、floaty、accessibility、screenshot/tasks 中至少
  一组 Auto.js Pro 高价值能力进入 `opt_in_promoted`。
- 桌面 Node.js 中至少 worker、network、fs advanced、ESM、WASI、inspector 或
  package manager 中的两组能力进入 `debug_poc` 或更高状态。
- 每个仍拒绝的能力都有明确错误码、迁移建议和下一阶段决策依据。
