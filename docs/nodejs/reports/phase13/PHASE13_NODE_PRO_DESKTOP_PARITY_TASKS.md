# Phase 13: Auto.js Pro And Desktop Node Parity Expansion Tasks

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
  packaged capture/OCR behavior, Paddle/Rapid fallback, and raw
  Bitmap/OpenCV profile decisions.

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
- Remaining P13-22 blockers: capability split, receiver authority,
  exported/non-exported policy, trigger payload serialization, packaged
  manifest synthesis, install/trigger/remove smoke, user disclosure, and raw
  Intent/BroadcastReceiver profile decisions.

## P13-23 Partial Landing Note

- P13-23 partial app/intent/broadcast Pro parity gate records JSON-safe
  `app.intent()` descriptors, stricter `app.startActivity()` descriptor
  parsing, and stable `app.sendBroadcast()` rejection.
- Host verifier:
  `tools/nodejs/phase13/verify-app-intent-broadcast-parity.js`.
- Gradle gate: `:app:verifyNodePhase13AppIntentBroadcastParity`.
- Report:
  `docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md`.
- Surface manifest evidence: `phase13-app-intent-broadcast-parity`.
- Remaining P13-23 blockers: Rhino app helper aliases, URI/email/settings
  helpers, installed-app lookup privacy policy, broadcast send promotion,
  root/Shizuku/dual-user routing through explicit capabilities, packaged smoke,
  negative device corpus, and migration examples.

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
- Remaining P13-24 blockers: root device matrix, Shizuku authorization and
  service lifecycle, AbortSignal cancellation instrumentation, packaged
  disclosure/failure diagnostics, expanded audit taxonomy, and migration
  examples.

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

- P13-27 partial utility-heavy Rhino modules gate records the pure
  `jsox.mathx` facade subset through `require("jsox")`,
  `require("jsox.mathx")`, and explicit `autojs6:compat.jsox`.
- Host verifier:
  `tools/nodejs/phase13/verify-utility-heavy-rhino-modules.js`.
- Gradle gate: `:app:verifyNodePhase13UtilityHeavyRhinoModules`.
- Report:
  `docs/nodejs/reports/phase13/P13_27_UTILITY_HEAVY_RHINO_MODULES.md`.
- Surface manifest evidence: `phase13-utility-heavy-rhino-modules`.
- Remaining P13-27 blockers: `zip`, `canvas`, OpenCC packaged resources,
  pinyin full dictionaries, Rhino `util` split batches, `sysprops`, `Arrayx`,
  `Numberx`, and raw Android Canvas/Paint or Android/OpenCV point/rect parity
  profile gates.

## P13-29 Partial Landing Note

- P13-29 partial HTTP/HTTPS client parity gate records the existing
  controlled `http`/`https` client facade as a Desktop-profile compatibility
  subset, backed only by AutoJs6 controlled fetch.
- Host verifier: `tools/nodejs/phase13/verify-http-https-client-parity.js`.
- Gradle gate: `:app:verifyNodePhase13HttpHttpsClientParity`.
- Report:
  `docs/nodejs/reports/phase13/P13_29_HTTP_HTTPS_CLIENT_PARITY.md`.
- Surface manifest evidence: `phase13-http-https-client-parity`.
- Remaining P13-29 blockers: packaged INTERNET permission evidence, live
  provider HTTP smoke, raw socket/server/listen policy, DNS/TLS session
  parity, proxy support decision, richer redirect/TLS failure corpus, and
  desktop client package corpus expansion.

## P13-30 Partial Landing Note

- P13-30 partial net/tls/dns staged compatibility gate records the existing
  limited `net`, `tls`, `dns`, and `dns/promises` builtins as Desktop-profile
  shape and policy evidence, not as raw network promotion.
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

## P13-32 Partial Landing Note

- P13-32 partial filesystem profile gate records the current Safe profile as
  scoped to `workingDirectory` and exposes a frozen diagnostic
  `filesystemProfile` snapshot without adding any extra root authority.
- Host verifier:
  `tools/nodejs/phase13/verify-filesystem-profile-decision.js`.
- Gradle gate: `:app:verifyNodePhase13FilesystemProfileDecision`.
- Report:
  `docs/nodejs/reports/phase13/P13_32_FILESYSTEM_PROFILE_DECISION.md`.
- Surface manifest evidence: `phase13-filesystem-profile-decision`.
- Remaining P13-32 blockers: explicit root authority provider,
  user-authorized directory grants, SAF/document roots, app-private roots,
  `/sdcard` expansion, grant persistence/revocation diagnostics, packaged
  expanded-root smoke, and per-root symlink/realpath/sensitive-path audit.

## P13-33 Partial Landing Note

- P13-33 partial desktop fs advanced API gate records scoped streams,
  `FileHandle`, fd APIs, `opendir`, watchers, and `realpath` as current partial
  evidence without expanding roots or exposing raw native fds.
- Host verifier: `tools/nodejs/phase13/verify-filesystem-advanced-apis.js`.
- Gradle gate: `:app:verifyNodePhase13FilesystemAdvancedApis`.
- Report: `docs/nodejs/reports/phase13/P13_33_FILESYSTEM_ADVANCED_APIS.md`.
- Surface manifest evidence: `phase13-filesystem-advanced-apis`.
- Remaining P13-33 blockers: packaged watcher/fd/FileHandle/stream lifecycle
  smoke, pending watcher/watchFile Kotlin diagnostics, expanded-root reruns,
  Android unsupported behavior notes, and final full desktop fs semantics.

## P13-34 Partial Landing Note

- P13-34 partial worker_threads opt-in promotion gate records default denial,
  `workerThreadsProfile` diagnostics, request-local experimental preflight, and
  existing native-available worker/MessageChannel/WorkerPool/resource/packaged
  evidence without promoting workers by default.
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
- Host verifier:
  `tools/nodejs/phase13/verify-process-worker-replacement-decision.js`.
- Gradle gate: `:app:verifyNodePhase13ProcessWorkerReplacementDecision`.
- Report:
  `docs/nodejs/reports/phase13/P13_35_PROCESS_WORKER_REPLACEMENT.md`.
- Surface manifest evidence: `phase13-process-worker-replacement-decision`.
- Remaining P13-35 blockers: native WorkerPool evidence on target ABIs,
  process-worker packaging behavior, slot accounting, cleanup stress,
  user-facing API/UX, and any future P13-40 scoped `child_process` runner.

## P13-36 Partial Landing Note

- P13-36 partial `vm` module gate records `vmProfile` diagnostics and keeps
  `vm`/`node:vm` denied by default.
- The gate documents the future enablement contract for `vm.Script`, context,
  timeout, private binding denial, host object denial, bridge denial, scoped fs
  preservation, memory limits, and packaged behavior.
- Host verifier: `tools/nodejs/phase13/verify-vm-module-decision.js`.
- Gradle gate: `:app:verifyNodePhase13VmModuleDecision`.
- Report: `docs/nodejs/reports/phase13/P13_36_VM_MODULE_DECISION.md`.
- Surface manifest evidence: `phase13-vm-module-decision`.
- Remaining P13-36 blockers: limited VM API design, context isolation, timeout
  cancellation, memory/compiled-script budgets, disabled-builtin escape tests,
  bridge/process/global leakage tests, and packaged APK behavior.

## P13-19 Partial Landing Note

- P13-19 partial accessibility selector promotion gate records the current
  direct `accessibility` and explicit `autojs6:compat` selector surfaces.
- Host verifier:
  `tools/nodejs/phase13/verify-accessibility-selector-promotion.js`.
- Gradle gate: `:app:verifyNodePhase13AccessibilitySelectorPromotion`.
- Report:
  `docs/nodejs/reports/phase13/P13_19_ACCESSIBILITY_SELECTOR_PROMOTION.md`.
- Surface manifest evidence: `phase13-accessibility-selector-promotion`.
- Remaining P13-19 blockers: `swipe`/`gesture`/`powerDialog`, direct
  `waitFor`, broader Rhino selector aliases, packaged accessibility
  service/settings lifecycle, real-device matrix expansion, and root/Shizuku
  gesture tiers.

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

- [ ] P13-05: 关闭 crash evidence automation blocker。
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
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Runtime marker store: `app/src/main/java/org/autojs/autojs/engine/NodeCrashEvidenceStore.kt`
    - Next-launch collector: `App.onCreate()` -> `NodeCrashEvidenceStore.collectPending(...)`
    - Embedded service marker writes: `app/src/main/java/org/autojs/autojs/engine/NativeNodeEmbeddedScriptService.kt`
    - Native process marker writes: `app/src/main/java/org/autojs/autojs/engine/NativeNodeProcessService.kt`
    - Bridge crash marker assertion: `app/src/androidTest/java/org/autojs/autojs/engine/NodeCrashEvidenceStoreInstrumentationTest.kt`
    - Host collector: `tools/nodejs/phase13/collect-crash-evidence.js`
    - Gradle gate: `:app:collectNodePhase13CrashEvidence`
    - Report: `docs/nodejs/reports/phase13/P13_05_CRASH_EVIDENCE_AUTOMATION.md`
    - Open before closure: fatal native chaos injection, packaged APK crash smoke, signal-safe native marker proof.

- [ ] P13-06: 推进 second execution slot 到可调度状态。
  - 范围：从 Phase 12 的 explicit slot evidence 推进到默认 scheduler 可选择
    primary/overflow slot。
  - 验收：
    - 至少两个 Node execution slot 可并发运行独立脚本。
    - ResourceRegistry、provider handles、stdout/stderr、crash evidence 相互隔离。
    - 默认调度、显式 slot、slot busy、slot crash fallback 均有测试。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Default one-shot scheduler policy: `NodeEmbeddedProcessPoolPolicy.STATUS_DEFAULT_SCHEDULER_ENABLED` / `MODE_DEFAULT_TWO_SLOT`.
    - Client-side slot lease: `NativeNodeEmbeddedScriptService.acquireDefaultProcessPoolSlot(...)`.
    - Slot source propagation: `NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest.processPoolSlotSource`.
    - Default busy rejection: `EmbeddedNodeProcessPoolBusyError` with `embedded_script.process_pool.busy=true`.
    - Focused instrumentation: `defaultSchedulerRoutesConcurrentOneShotScriptsToDifferentProcesses`.
    - Host verifier: `tools/nodejs/phase13/verify-second-execution-slot-scheduler.js`.
    - Gradle gate: `:app:verifyNodePhase13SecondExecutionSlotScheduler`.
    - Report: `docs/nodejs/reports/phase13/P13_06_SECOND_EXECUTION_SLOT_SCHEDULER.md`.
    - Open before closure: packaged scheduler smoke, fatal slot-crash fallback/quarantine, full stress promotion.

- [ ] P13-07: 推进 packaged long-running service mode。
  - 范围：打包 APK 中支持 foreground long-running Node runtime。
  - 验收：
    - foreground notification、Stop action、checkpoint、quota、destroy cleanup 可用。
    - `autojs6:lifecycle` 在 packaged 模式有恢复/清理证据。
    - Android 版本、权限、后台限制不满足时稳定拒绝。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
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
    - Open before closure: real 8h/24h device soak reports with comparable RSS/native heap/Java heap/thread/fd/bridge diagnostics and no leak/orphan/runtime failure flags.

- [ ] P13-09: 实现 Android-side package installation manager。
  - 范围：Node 项目/插件/依赖包在 Android 侧的 install/update/remove/list/verify/prune。
  - 边界：不运行 npm lifecycle scripts；不允许未签名 native payload 直接执行。
  - 验收：
    - transactional、cancellable、locked、integrity checked。
    - 能服务后续 npm/package manager 和 plugin distribution。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
    - Android store: `NodeAndroidPackageInstallManager`.
    - Operations: `install`, `update`, `remove`, `list`, `verify`, `prune`.
    - Boundaries: lifecycle scripts, `bin`, native payloads, symlinks, and path escapes are rejected.
    - Integrity: SHA-512 expected integrity and installed-record verification.
    - Transaction model: store lock, transaction staging, atomic move/restore, cancellation token.
    - Focused instrumentation: `NodeAndroidPackageInstallManagerInstrumentationTest`.
    - Host verifier: `tools/nodejs/phase13/verify-android-package-install-manager.js`.
    - Gradle gate: `:app:verifyNodePhase13AndroidPackageInstallManager`.
    - Report: `docs/nodejs/reports/phase13/P13_09_ANDROID_PACKAGE_INSTALL_MANAGER.md`.
    - Open before closure: JS/runtime API exposure, registry/download policy, signed native-payload decision, prepared-device instrumentation run, and plugin/package distribution integration.

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
    - Paddle/Rapid/plugin fallback, packaged OCR model lookup, and raw Bitmap/OpenCV object profile decisions remain open.

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
    - Type declarations: `docs/nodejs/types/autojs6-node/app.d.ts`.
    - Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`.
    - Focused conformance:
      `NodeBridgeConformanceInstrumentationTest#jsStableBridgeModuleExportsAndPromiseContractsAreStable`.
    - Host verifier:
      `tools/nodejs/phase13/verify-app-intent-broadcast-parity.js`.
    - Gradle gate: `:app:verifyNodePhase13AppIntentBroadcastParity`.
    - Report:
      `docs/nodejs/reports/phase13/P13_23_APP_INTENT_BROADCAST_PARITY.md`.
    - Open before closure: Rhino app helper aliases, URI/email/settings
      helpers, broadcast send promotion, root/Shizuku/dual-user routing through
      P13-24 capabilities, installed-app lookup privacy policy, packaged smoke,
      negative device corpus, and migration examples.

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
    - Controlled execution evidence: scoped `cwd`, string-only `env`, bounded
      output, timeout code `124`, `ResourceRegistry` process cleanup, and
      active shell command limits.
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

- [ ] P13-27: 扩展 utility-heavy Rhino modules。
  - 范围：`zip`、`canvas`、`opencc`、`pinyin` full dictionary、`jsox`、
    `util`、`sysprops` 等。
  - 验收：
    - 纯 JS 能力优先 promotion。
    - 资源型能力必须有 packaged resource lookup 和 missing-resource diagnostics。
    - raw Android Canvas/Paint parity 如需开放必须单独 profile gate。
  - Progress Evidence (2026-06-24, partial; checkbox remains open):
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
    - Open before closure: `zip`, `canvas`, OpenCC/pinyin packaged resources,
      Rhino `util` helper split, `sysprops`, `Arrayx`, `Numberx`, and raw
      Canvas/Paint or Android/OpenCV object parity profile gates.

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

- [ ] P13-31: 决策 `dgram` UDP capability。
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

- [ ] P13-32: 放宽 filesystem profile。
  - 范围：从 scoped project fs 扩展到用户显式授权目录、`/sdcard`、SAF 或 app
    私有目录 profile。
  - 验收：
    - Safe profile 仍保持 scoped fs。
    - Pro/Desktop profile 可通过 manifest/用户授权获得额外根。
    - symlink、realpath、`..`、NUL、`/proc`、`/sys`、`/dev` 边界重新审计。
  - Decision progress:
    - 当前 Safe profile 明确保持 `workingDirectory` scoped fs，不在 P13-32
      中静默开放 `/sdcard`、SAF、app-private 或用户授权根。
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

- [ ] P13-33: 推进 desktop fs advanced APIs。
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

- [ ] P13-35: 决策 process-worker replacement。
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

- [ ] P13-36: 推进 `vm` module。
  - 范围：在 Desktop/Debug profile 中支持受控 `vm.Script`、context 和 timeout。
  - 验收：
    - 禁止逃逸到 private bindings、bridge globals 或 host objects。
    - context resource cleanup 和 memory budget 有测试。
  - Partial landing:
    - `autojs6:profile.vmProfile` 已暴露 P13-36 决策快照。
    - `vm` / `node:vm` 保持默认拒绝，`node_modules/vm` 不能 shadow。
    - `vm.Script`、context、`compileFunction`、ES module VM APIs、
      dynamic import hooks 均未引入 runtime surface。
    - future enablement gate 明确要求 timeout、context cleanup、memory
      budget、private binding/bridge/host object/process/global leakage denial
      和 packaged APK behavior 证据。
    - Device test: `NodeVmPhase13DecisionInstrumentationTest`。
    - Existing design gate: `NodeVmDesignGateInstrumentationTest`。
    - Host verifier: `tools/nodejs/phase13/verify-vm-module-decision.js`。
    - Gradle gate: `:app:verifyNodePhase13VmModuleDecision`。
    - Report: `docs/nodejs/reports/phase13/P13_36_VM_MODULE_DECISION.md`。
    - Surface manifest evidence: `phase13-vm-module-decision`。

- [ ] P13-37: 推进 inspector/devtools debug profile。
  - 范围：允许 debug build 通过 adb forward 使用 inspector/profiling。
  - 验收：
    - release build 稳定拒绝。
    - debug profile 有 explicit opt-in、端口绑定、ADB-only 策略。
    - CPU/heap profile 不泄漏敏感脚本路径或用户数据。

- [ ] P13-38: 推进 controlled WASI。
  - 范围：从 pure WebAssembly 扩展到 `autojs:wasi` 或受控 `node:wasi` profile。
  - 验收：
    - preopen 目录受 scoped/authorized roots 控制。
    - fd、env、args、clock/random、network 权限明确。
    - raw unrestricted WASI 仍拒绝。

- [ ] P13-39: 决策 native addon `.node` loading。
  - 范围：为 Desktop profile 评估 signed/packaged native addon。
  - 验收：
    - 默认仍拒绝任意 `.node`。
    - 若开放，必须有 ABI allowlist、签名/哈希、packaged-only、no download-exec、
      dependency scan、crash isolation。
    - `process.dlopen` 和 addon resolve 不能绕过 policy。

- [ ] P13-40: 推进 `child_process` scoped runner。
  - 范围：评估桌面 Node 生态需要的子进程能力在 Android 上的替代实现。
  - 验收：
    - Safe profile 拒绝。
    - Desktop profile 只能执行 allowlisted app-private binaries 或 Android shell
      provider，不允许任意路径执行。
    - stdout/stderr/input/timeout/cancel/kill/process cleanup 完整。

- [ ] P13-41: 推进完整 ESM loader parity。
  - 范围：扩展 `.mjs`、`type=module`、TLA、dynamic import、package exports/imports、
    import attributes、CJS interop。
  - 验收：
    - local/package ESM 与桌面 Node 行为差距有 matrix。
    - loader hooks、network import 是否支持有明确决策。
    - source map 和 stack trace 与 CJS 一致。

- [ ] P13-42: 推进 npm/package manager on Android。
  - 范围：从 host-side bundler/validator 推进到 Android 侧 npm 包安装/管理能力。
  - 验收：
    - `npm i --no-bin-links` 或等价安装流程可用。
    - lifecycle scripts 默认拒绝或 debug-only。
    - bin shim、symlink、native build、postinstall 下载、integrity lock 有策略。

- [ ] P13-43: 扩展 Node stdlib conformance。
  - 范围：`crypto`、`stream`、`zlib`、`buffer`、`url`、`os`、`readline`、
    `tty`、`perf_hooks`、`async_hooks` 等。
  - 验收：
    - 每个模块有 conformance subset。
    - Android 不适用 API 有稳定差异说明。
    - npm corpus 中真实依赖通过率提升。

- [ ] P13-44: 推进 process/env/argv parity。
  - 范围：补齐 `process.argv`、`process.env`、cwd、exit、signals、versions、
    resourceUsage 等。
  - 验收：
    - env 写入、敏感变量、Android process signal 差异有 policy。
    - `process.exit()` 与 AutoJs script lifecycle 协调。

### Track D: Packaging, plugins, and distribution

- [ ] P13-45: 扩展 packaged APK capability metadata。
  - 范围：让 packaged APK 能表达 Pro/Desktop profile、permissions、Node builtins、
    native assets、long-running、network、filesystem roots。
  - 验收：
    - manifest synthesis 与 project/package metadata 一致。
    - 未声明能力在 packaged runtime 中拒绝。

- [ ] P13-46: 推进 plugin system toward Pro/Desktop parity。
  - 范围：从 JS-only local plugin 扩展到安装、更新、签名、权限、资源、packaged
    distribution。
  - 验收：
    - Android provider plugin、native plugin、remote marketplace 分阶段决策。
    - 插件能力不能超出 host project/package 声明。

- [ ] P13-47: 增加 dependency compatibility corpus。
  - 范围：扩大真实 npm 包测试，按缺失能力分类。
  - 验收：
    - 报告给出 package、missing builtin、native addon、postinstall、network、fs
      等原因。
    - P13 每次放开桌面能力都能看到 corpus 通过率变化。

- [ ] P13-48: 实现 package lock and integrity policy。
  - 范围：为 Android-side package manager、plugins、native addon 做统一完整性策略。
  - 验收：
    - lockfile、hash、签名、回滚、缓存清理、损坏恢复完整。

### Track E: Security, UX, and rollback

- [ ] P13-49: 实现 profile selection UX and diagnostics。
  - 范围：为 Safe/Pro/Desktop/Debug profile 提供项目设置、打包设置和诊断输出。
  - 验收：
    - 用户能清楚看到启用了哪些高风险能力。
    - 失败时错误消息能指出需要哪个 profile/capability/permission。

- [ ] P13-50: 扩展 security corpus for relaxed capabilities。
  - 范围：为 raw network、fs 放宽、Java interop、child_process、native addon、
    inspector、WASI 增加攻击语料。
  - 验收：
    - 每个放开能力都有正向和负向测试。
    - 旧 Safe profile 不因新能力产生 regression。

- [ ] P13-51: 增加 per-profile rollback playbook。
  - 范围：更新 rollback 文档和 Gradle/property 开关。
  - 验收：
    - 可以快速禁用单个 capability、整个 Pro profile、整个 Desktop profile。
    - packaged APK 已发布场景有降级策略。

- [ ] P13-52: 增加 privacy and disclosure policy。
  - 范围：截图、录音、悬浮窗、无障碍、网络、文件系统、Java interop、子进程等
    能力的用户提示和前台披露。
  - 验收：
    - Android 权限、前台服务、通知和 UI 提示一致。
    - packaged APK 不因缺少披露进入隐式高权限状态。

### Track F: Docs, examples, and final gate

- [ ] P13-53: 发布 Pro parity examples。
  - 范围：新增示例项目覆盖 `$autojs.java`、`rhino.install`、UI、floaty、tasks、
    screenshot/OCR、accessibility。
  - 验收：
    - 每个示例有 expected output、required capabilities、packaged behavior。
    - 不可用条件写入 README 和 smoke skip reason。

- [ ] P13-54: 发布 desktop Node parity examples。
  - 范围：新增示例覆盖 ESM、worker、http/https、fs advanced、WASI、inspector、
    npm dependency。
  - 验收：
    - 每个示例标注所需 profile。
    - Safe profile 下的拒绝路径也有示例或测试。

- [ ] P13-55: 更新 TypeScript declaration package。
  - 范围：为 Pro/Desktop profile 增加条件类型或文档注释。
  - 验收：
    - `docs/nodejs/types/autojs6-node` 覆盖新增模块。
    - type smoke 覆盖 Safe/Pro/Desktop 差异。

- [ ] P13-56: 更新 migration and Pro9 comparison docs。
  - 范围：同步 `MIGRATION_FROM_RHINO.md`、`INTEGRATION_STATUS_PRO9_COMPARISON.md`、
    `COMPATIBILITY_NOTES_PRO_AUTOX.md`。
  - 验收：
    - 明确哪些能力已经 Pro-compatible，哪些是 Desktop-compatible，哪些仍 gated。
    - 不把 debug-only 或 provider POC 写成正式可用。

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
