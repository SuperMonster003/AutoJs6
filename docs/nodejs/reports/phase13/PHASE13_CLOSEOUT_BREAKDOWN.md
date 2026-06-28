# Phase 13 Closeout Breakdown

Status: living closeout plan for the remaining open Phase 13 tasks.

Shared Node.js deployment constraints, timeout defaults, and device-selection
rules are recorded in
[`NODE_DEPLOYMENT_SHARED_CONTEXT.md`](../../NODE_DEPLOYMENT_SHARED_CONTEXT.md).

Phase 13 intentionally keeps many parent tasks open after partial landings.
Most open items are not regressions; they are promotion gates that require one
or more of implementation, connected-device evidence, packaged APK evidence,
security/product decisions, or external runtime availability before the parent
task can be marked complete.

## Open Reason Taxonomy

| Type | Meaning | Typical close path |
| --- | --- | --- |
| `implementation` | Runtime/provider/API behavior does not exist yet, or only exists as a stable denial/diagnostic surface. | Implement the provider or facade, add focused instrumentation, update docs/types, rerun the task verifier. |
| `evidence` | Runtime code is mostly present, but connected-device, packaged APK, long-soak, or matrix evidence is missing. | Run and archive the required evidence, wire it into the verifier, update reports. |
| `policy` | The project has not decided whether the capability should be promoted, remain opt-in, or stay denied. | Record an explicit decision, threat model, rollback/privacy behavior, and stable error surface. |
| `packaging` | Behavior depends on packaged APK metadata, installer/build synthesis, prompt/disclosure UI, or app-private assets. | Add packaged metadata, install/run smoke, denial paths, and rollback/disclosure evidence. |
| `external` | Closure depends on native/runtime artifacts or long-running environment availability. | Keep the task open or deferred until the external condition is available, then attach evidence. |

## Parent Closure Rule

A Phase 13 parent task can close only when all of the following are true:

- Its host verifier reports `ready` or `passed` with zero policy violations and
  zero task blockers.
- Required connected-device, packaged APK, stress, or matrix evidence is
  archived under deterministic report paths.
- Profile, capability, rollback, privacy, TypeScript, migration, and release
  docs are synchronized for the promoted or rejected behavior.
- Stable-denial branches remain covered for any authority that is not promoted.
- `generateNodePhase13Summary` reflects the task as completed without hiding
  open final blockers.

## Priority Lanes

| Lane | Tasks | Why first |
| --- | --- | --- |
| P0 runtime graduation | `P13-06` to `P13-09` | These map to required final blockers and gate later profile promotion. |
| P1 profile/security controls | `P13-45`, `P13-49` to `P13-52` | Pro/Desktop promotion needs effective profile selection, packaged metadata, rollback, and disclosure. |
| P2 Pro provider parity | `P13-11`, `P13-13`, `P13-14`, `P13-16` to `P13-27` | These are the Auto.js Pro-facing compatibility gaps. |
| P3 Desktop Node parity | `P13-29` to `P13-44` | These expand npm/desktop behavior while keeping risky authority gated. |
| P4 docs/examples/final gate | `P13-53` to `P13-57` | These should close after the runtime/security evidence they document is real. |

## Final Blocker Map

| Final blocker | Phase 13 tasks | Main reason open | Closeout direction |
| --- | --- | --- | --- |
| `second_execution_slot` | `P13-06`, `P13-08` | `evidence` | Two-slot scheduling has landed; P13-06 closes on current scheduler/stress evidence, while P13-08 owns long-duration soak evidence. |
| `packaged_long_running` | `P13-07`, `P13-45`, `P13-52` | `evidence`, `packaging` | P13-45/P13-52 close metadata and policy diagnostics; P13-07/future packaged promotion owns lifecycle, disclosure UI, and final evidence. |
| `full_heavy_resource_stress` | `P13-08` | `evidence`, `external` | Archive 8-hour quick soak and 24-hour final soak with comparable metrics. |
| `android_side_package_installation` | `P13-09`, `P13-42`, `P13-48` | `implementation`, `policy`, `packaging` | P13-42 closes the v1.1 guarded local-store package-manager decision; registry/tarball/native/distribution promotion remains future work. |
| `worker_threads` | `P13-34`, `P13-35` | `external`, `policy` | P13-35 records the v1.1 no-facade replacement decision; P13-34 owns native WorkerPool promotion evidence on target ABIs. |
| `controlled_wasi` | `P13-38` | `implementation`, `policy` | Either implement a controlled WASI facade or keep raw WASI denied with pure WebAssembly as the supported path. |
| `raw_network` | `P13-29`, `P13-30`, `P13-50` to `P13-56` | `implementation`, `policy`, `packaging` | Keep Safe profile denied; promote only scoped Desktop/relaxed-capability tracks with network budgets, privacy, rollback, and packaged evidence. |
| `node_24_17_community_fork` | future runtime lane | `external` | Track as future runtime maintenance; do not block current Node 24.5 Phase 13 work unless upgrade artifacts become available. |

## Task Closeout Checklist

### Runtime Graduation

#### P13-06 second execution slot

Reason open: mostly `evidence`.

- [x] Refresh or replace the `defaultProcessPoolBusyResult` evidence token in the verifier/report.
- [x] Attach current focused primary/overflow shared-resource stress evidence.
- [x] Keep P13-08 long-duration soak evidence as the P13-08/final-promotion closure signal instead of a P13-06 blocker.
- [x] Re-run `verifyNodePhase13SecondExecutionSlotScheduler` and summary generation with zero blockers.

#### P13-07 packaged long-running service

Reason open: `evidence`, `packaging`.

- [x] Add packaged `autojs6:lifecycle` checkpoint/read/clear smoke evidence
  with QV710AF65F focused packaged smoke coverage.
- [ ] Add Android version, notification permission, and background-start denial matrix.
- [ ] Verify foreground notification, stop action, checkpoint, task-removal cleanup, relaunch, and crash recovery.
- [ ] Record packaged metadata and disclosure denial paths before promotion.

#### P13-08 full heavy resource stress

Reason open: `evidence`, `external`.

- [ ] Produce `build/reports/nodejs/final-soak/quick-soak-8h.json`.
- [ ] Produce `build/reports/nodejs/final-soak/final-soak-24h.json`.
- [ ] Archive comparable memory, fd, thread, runtime, and orphan-process metrics.
- [ ] Re-run `verifyNodePhase13FullHeavyResourceStress` and update the final summary.
- [x] Keep final blocker registry prose from double-counting the canonical quick/final soak blockers while evidence files are absent.

#### P13-09 Android-side package installation manager

Reason open: decision gate drift cleanup; future promotion remains `implementation`, `policy`, `packaging`.

- [ ] Implement registry metadata and tarball download into an app-private cache.
- [ ] Extend transactional install/update/remove/list/verify/prune beyond local fixtures.
- [ ] Keep cancellation, store locking, SHA-512/integrity, and path-escape denial for all sources.
- [ ] Decide npm CLI, lifecycle scripts, bin shims, signed native payloads, and unrestricted install authority.
- [ ] Integrate with packaged distribution and rollback diagnostics.

### Pro Entry, Java, And Rhino

#### P13-11 Pro-style entry directives

Reason open: `implementation`, `evidence`.

- [ ] Add full Activity lifecycle launch evidence for Pro-style Node UI entries.
- [ ] Cover Back/Recents, close, relaunch, and task recovery behavior.
- [ ] Define concrete UI-thread dispatch semantics beyond directive metadata.
- [ ] Add packaged entry denial and diagnostic cases.

#### P13-13 Java interop allowlist

Reason open: `implementation`, `policy`.

- [ ] Implement thread dispatch and `setThreadMode` semantics.
- [ ] Add a generic method-budget policy beyond the explicit allowlist and bridge timeout.
- [ ] Extend allowlist coverage only with lifetime, cleanup, and denial instrumentation.
- [ ] Keep Pro profile promotion blocked until Java/Rhino surface security is complete.

#### P13-14 `rhino.install()` compatibility

Reason open: `implementation`, `packaging`.

- [ ] Add a real Pro profile runtime selector that can grant the experimental Java proxy path.
- [ ] Depend on the P13-16 proxy-interface or `defineClass` replacement decision.
- [ ] Add packaged policy, metadata, and denial behavior.
- [ ] Keep default `rhino.install()` fail-closed until all above evidence exists.

#### P13-16 `JavaAdapter` / `defineClass` replacement

Reason open: stable-denial decision drift cleanup; future proxy-interface promotion remains `implementation`, `policy`, `evidence`.

- [x] Keep bounded proxy-interface or `java.defineClass()` provider implementation as future provider work rather than a P13-16 v1.1 decision blocker.
- [x] Keep listener/interface examples backed by connected-device instrumentation as future provider promotion work.
- [x] Keep callback cleanup, thread dispatch, timeout, leak diagnostics, and packaged metadata as future provider promotion work.
- [x] Keep global `JavaAdapter` unsupported unless a secure compatibility shape is explicitly accepted.

### Pro UI, Automation, Media

#### P13-17 live UI provider

Reason open: `evidence`, `packaging`.

- [ ] Add Back/Recents lifecycle instrumentation.
- [ ] Add orientation/configuration-change policy evidence.
- [ ] Run packaged UI smoke for entry, close, relaunch, and crash behavior.
- [ ] Record packaged capability, metadata denial, and user-visible disclosure behavior.

#### P13-18 floaty/overlay provider

Reason open: `implementation`, `evidence`, `packaging`.

- [ ] Implement actual Android overlay show/update/drainEvents/close provider.
- [ ] Record granted/denied `SYSTEM_ALERT_WINDOW` device matrix, including OEM settings behavior.
- [ ] Add foreground disclosure ownership and stop action.
- [ ] Add packaged install/run smoke, denial paths, and leak/cleanup stress.
- [x] Keep docs/design evidence gate from counting live overlay provider, permission matrix, foreground disclosure, packaged, and leak evidence twice.

#### P13-19 accessibility/selector parity

Reason open: `implementation`, `evidence`.

- [ ] Add Rhino selector aliases and chain helpers beyond the current descriptor subset.
- [x] Decide exact `waitFor`/`pickup`/`detect` semantics or keep stable denials.
- [ ] Add packaged accessibility-service lifecycle evidence.
- [ ] Add real-device matrix for selector actions, Back/Home/Recents, and denial paths.
- [ ] Split gesture and root/Shizuku input automation into explicit future capabilities.
- [x] Keep docs/device-matrix evidence gate from counting selector aliases, packaged service, action matrix, gesture, and root/Shizuku evidence twice.

#### P13-20 screenshot/image/OCR parity

Reason open: `implementation`, `evidence`, `packaging`.

- [ ] Implement Activity-owned MediaProjection request/capture/stop provider.
- [ ] Add foreground disclosure, stop action, task-removal cleanup, relaunch, crash, and denial evidence.
- [ ] Promote image operations such as save, clip, resize, grayscale, threshold, findImage, matchTemplate, and color matching.
- [x] Define OCR fallback/model lookup policy for ML Kit, Paddle, Rapid, and plugin providers.
- [ ] Add packaged image/OCR smoke, quota, temp-handle, and leak/stress evidence.
- [x] Align OCR compat `tap("mlkit")` verifier token with the current optional-mode declaration.
- [x] Keep docs/device-matrix evidence gate from counting MediaProjection, advanced image, packaged OCR/image, and disclosure evidence twice.

### Pro Tasks, App, Shell, Media, Utilities

#### P13-21 task database provider

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Decide whether daily/weekly/user tasks live in `tasks`, `work_manager`, or a Pro-only provider.
- [ ] Define JSON task schema, row ownership, id mapping, migrations, downgrade behavior, and legacy visibility.
- [ ] Implement add/query/update/remove over frozen JSON snapshots.
- [ ] Add capability metadata, disclosure, rollback, and packaged task smoke.
- [x] Keep docs/surface evidence gate from counting task schema, ownership, JSON operations, scheduler semantics, and packaged evidence twice.

#### P13-22 IntentTask/broadcast provider

Reason open: process gate drift cleanup; packaged evidence remains `packaging`.

- [x] Replace stable rejection stubs only after a JSON-safe broadcast provider exists.
- [ ] Define ownership between legacy Rhino rows, Node descriptors, and packaged records.
- [ ] Add query/get snapshots without exposing raw IntentTask or receiver handles.
- [ ] Add manifest synthesis, exported/non-exported policy, trigger payloads, and install/trigger/remove packaged smoke.
- [x] Keep docs/surface evidence gate from counting receiver ownership, JSON snapshots, manifest/export policy, trigger payloads, packaged trigger/remove, and consent evidence twice.

#### P13-23 app/intent/broadcast parity

Reason closed for bounded v1.1 app/intent scope. Future broadcast provider,
dual-user, and root/Shizuku work remains under P13-22/P13-24.

- [x] Add a descriptor alias matrix for Rhino activity short forms.
- [x] Add safe Activity short-form device smoke and invalid component/flag/extras negative tests.
- [x] Define redacted installed-app list schemas and privacy disclosure policy.
- [x] Add packaged scoped file view/edit evidence.
- [x] Decide `sendBroadcast`, raw `Uri`/`FileProvider`, dual-user, and root/Shizuku app tiers.
- [x] Keep docs/surface evidence gate from counting alias matrix, device smokes, installed-app privacy, packaged scoped-file, negative corpus, and migration evidence twice.

#### P13-24 shell/root/Shizuku

Reason open: `implementation`, `evidence`, `packaging`.

- [x] Split `shell.root` and `shell.shizuku` capability checks and diagnostics in runtime/verifier evidence.
- [x] Add project/package metadata examples for both capabilities.
- [ ] Add root-available/root-unavailable device matrix, packaged shell/root disclosure, rollback, audit taxonomy, and migration evidence.
- [x] Add AbortSignal cancellation for in-flight shell commands.
- [x] Keep docs/release-matrix evidence gate from counting the same root/package shell evidence twice.
- [x] Keep Shizuku execution stable-denied for v1.1; implement it only after service binding, permission, lifecycle, cleanup, packaged disclosure, and rollback evidence exist.

#### P13-25 notifications/settings/power manager

Reason open: `implementation`, `evidence`, `packaging`.

- [ ] Add notification actions, listeners, groups, styles, remote input, and richer layouts.
- [ ] Add DND, exact alarm, foreground disclosure, and notification action packaged evidence.
- [ ] Add wakelocks, request-ignore-battery-optimizations flow, callbacks, and direct mutation policy.
- [ ] Add OEM matrix, packaged disclosure, and background-launch behavior evidence.
- [x] Keep docs/surface evidence gate from counting notification actions, settings helpers, wakelock/power APIs, OEM matrix, and packaged evidence twice.

#### P13-26 media/recorder/mediainfo

Reason open: `implementation`, `evidence`, `packaging`.

- [ ] Add audio playback and MediaPlayer lifecycle with focus policy.
- [ ] Decide and implement MediaStore query/insert/update/delete boundaries.
- [ ] Add real recording start/stop/session handles, permission flow, foreground disclosure, and cleanup.
- [ ] Decide screen/internal audio recording policy.
- [ ] Add packaged media assets, recorder smoke, denial matrix, and privacy copy.
- [x] Keep docs/provider evidence gate from counting playback, MediaStore, recording, packaged smoke, privacy, and migration evidence twice.

#### P13-27 utility-heavy Rhino modules

Reason open: pure utility helper gate drift cleanup; future resource-backed utility promotion remains `implementation`, `packaging`.

- [x] Add separate pure helper gates for `Arrayx` and `Numberx`.
- [x] Keep `zip`, `canvas`, OpenCC, and full pinyin resource providers as future resource-backed utility promotion work.
- [x] Keep packaged resource smoke and resource lookup diagnostics as future resource-backed utility promotion work.
- [x] Decide raw Android/OpenCV object parity or keep stable denials.
- [x] Keep docs/status-manifest evidence gate from counting resource-backed utility promotion work twice.

### Desktop Network And Filesystem

#### P13-29 HTTP/HTTPS client parity

Reason open: controlled client evidence drift cleanup; future raw-network and
packaged distribution promotion remains `implementation`, `policy`,
`packaging`.

- [x] Attach live Android/OkHttp HTTP and HTTPS request evidence through `verifyNodeLiveFetchGate`.
- [x] Attach packaged `network` to `android.permission.INTERNET` proof through P13-45 packaged capability metadata.
- [x] Keep full raw socket, DNS lookup, TLS session, and backpressure semantics scoped to P13-30 or later.
- [x] Decide whether any per-request proxy support is provider-owned or stays denied.
- [x] Keep future P13-30 raw `net` / `tls` / `dns` authority decisions from counting as P13-29 blockers.

#### P13-30 `net` / `tls` / `dns`

Reason open: `implementation`, `policy`, `evidence`, `packaging`.

- [ ] Implement or reject real TCP/TLS client socket authority with budgets and lifecycle cleanup.
- [ ] Decide server socket/listen policy, bind address policy, and foreground/background behavior.
- [ ] Complete DNS privacy review so resolver servers, search domains, VPN state, and topology do not leak.
- [ ] Add fd/socket leak instrumentation and packaged INTERNET proof.
- [ ] Decide debug raw-network opt-in release posture, limits, and rollback.
- [x] Keep docs/type/matrix evidence gate from counting client/server socket, DNS privacy, TLS policy, packaged INTERNET, and fd leak evidence twice.

#### P13-31 UDP / `dgram`

Reason open: decision gate drift cleanup; future UDP promotion remains `policy`, `implementation`, `evidence`, `packaging`.

- [x] Decide whether `dgram` remains stable-denied for v1.1 or starts a future controlled provider.
- [x] Keep unicast, server bind/listen, broadcast, multicast, and source-specific multicast capability splits as future UDP promotion work.
- [x] Keep permission mapping, packet budgets, lifecycle cleanup, and packaged behavior as future UDP promotion work.
- [x] Keep local shadow bypass and raw socket authority denied until the provider exists.
- [x] Keep the stable-denied no-shape-facade boundary from counting as separate provider evidence.
- [x] Keep docs/matrix evidence gate from counting UDP permission, packaged, fd-leak, and lifecycle evidence twice.

#### P13-32 filesystem profile relaxation

Reason open: Safe scoped filesystem profile gate drift cleanup; future expanded-root promotion remains `implementation`, `policy`, `packaging`.

- [x] Keep user-authorized roots for SAF/document roots and app-private roots as future expanded-root promotion work.
- [x] Keep manifest metadata, grant persistence, revocation, diagnostics, rollback, and packaged expanded-root smoke as future expanded-root promotion work.
- [x] Keep symlink, realpath, `..`, NUL, `/proc`, `/sys`, and `/dev` audits for each root class as future expanded-root promotion work.
- [x] Keep docs/matrix evidence gate and P13-33 advanced fs APIs from counting as separate P13-32 blockers.

#### P13-33 desktop fs advanced APIs

Reason open: scoped advanced filesystem API gate drift cleanup; future packaged lifecycle and expanded-root rerun evidence remains `evidence`, `packaging`.

- [x] Expose pending watcher/watchFile counts through diagnostics or report fields.
- [x] Keep packaged watcher, fd, FileHandle, and stream close-on-destroy evidence as future packaged lifecycle work.
- [x] Keep packaged realpath and opendir evidence export from source markers to device reports as future packaged lifecycle work.
- [x] Document Android watcher coalescing, recursive-watch denial, and realpath differences.
- [x] Keep advanced API reruns against any P13-32 expanded-root classes as future expanded-root promotion work.
- [x] Keep docs/matrix evidence gate from counting packaged lifecycle and expanded-root rerun evidence twice.

### Desktop Execution Runtime

#### P13-34 `worker_threads`

Reason open: `external`, `evidence`, `policy`, `packaging`.

- [ ] Prove native worker availability on the target promotion ABIs.
- [x] Run and archive the current QV710AF65F experimental worker gate with
  `autojs.nodejs.worker_threads.experimental=true`; runnable/stress/packaged
  cases skipped because native preflight reported `ERR_AUTOJS6_WORKER_DISABLED`.
- [ ] Add native-available packaged worker smoke evidence.
- [ ] Add crash/resource evidence for startup failure, exception, timeout, parent cleanup, and packaged failure modes.
- [x] Decide nested worker policy and document SharedArrayBuffer, Atomics, resourceLimits, thread, and RSS ceilings.
- [x] Keep docs/matrix evidence gate from counting native availability, packaged smoke, and crash/resource evidence twice.

#### P13-35 process-worker replacement

Reason open: decision drift cleanup; future promotion remains `implementation`, `external`, `packaging`.

- [x] Keep WorkerPool evidence on target ABIs owned by P13-34/future promotion rather than P13-35 decision closure.
- [x] Keep P13-35 v1.1 without a user-facing process-worker facade or UX.
- [x] Keep docs/release-matrix evidence gate from counting WorkerPool promotion and process-worker packaging twice.
- [x] Keep slot accounting, cleanup, packaging, and UX as future requirements for any promoted replacement.
- [x] Keep raw spawn/fork/stdio/signals/process handles delegated to P13-40.

#### P13-36 `vm`

Reason open: `evidence`, `packaging`.

- [x] Add focused memory and compiled-script budget evidence.
- [x] Add timeout/lifecycle stress and leakage probes.
- [x] Add packaged smoke for packages using `vm.Script` and contexts.
- [x] Keep VM documented as compatibility, not a security boundary.
- [x] Keep docs/matrix evidence gate from counting VM memory, lifecycle, leakage, and packaged evidence twice.

#### P13-37 inspector/devtools

Reason open: stable-denial gate drift cleanup; future debug-profile promotion remains `implementation`, `policy`, `packaging`.

- [x] Keep trusted debug UI or host command start/revoke flow as future debug-profile promotion work.
- [x] Keep loopback-only listener descriptors, ADB forward setup/cleanup, and public-interface denial proof as future debug-profile promotion work.
- [x] Keep debug-build DevTools attach coverage for breakpoints, pause/resume, console, async stacks, source maps, and ESM names as future debug-profile promotion work.
- [x] Keep CPU/heap profile artifact export, redaction, budget, and cleanup as future debug-profile promotion work.
- [x] Keep packaged release denial and debug-packaged metadata evidence as future debug-profile promotion work.
- [x] Keep docs/matrix evidence gate from counting debug session, DevTools, profiler, and packaged metadata evidence twice.

#### P13-38 controlled WASI

Reason open: stable-denial gate drift cleanup; future controlled WASI promotion remains `implementation`, `policy`, `packaging`.

- [x] Formally keep only pure WebAssembly for P13-38 v1.1; raw `wasi`, raw `node:wasi`, and callable controlled WASI facades stay stable-denied.
- [x] Keep scoped/authorized preopen mapping with lexical and realpath escape denial as future controlled WASI promotion work.
- [x] Keep args, env, virtual fd table, stdio, clock, random, process-exit, and network-denial policies as future controlled WASI promotion work.
- [x] Keep module/memory/stdout/stderr/timeout/concurrency budgets as future controlled WASI promotion work.
- [x] Keep packaged `.wasm` assets, hostile WASI corpus, and worker/WASM integration evidence as future controlled WASI promotion work.
- [x] Keep docs/matrix evidence gate from counting controlled facade, preopen, resource, packaged, hostile corpus, and worker/WASM evidence twice.

#### P13-39 native addon loading

Reason open: stable-denial gate drift cleanup; future signed packaged native addon promotion remains `policy`, `implementation`, `packaging`.

- [x] Keep signed packaged native-addon manifest with per-ABI artifacts as future native addon promotion work.
- [x] Keep ABI and Node-API/version allowlists for embedded Node, Android ABI, NDK, and release channel as future native addon promotion work.
- [x] Keep hash/signature verification as future native addon promotion work.
- [x] Keep native addon artifacts packaged-only and keep runtime download, npm build, lifecycle scripts, and postinstall execution denied.
- [x] Keep dependency scan, APK-builder preflight, crash isolation, positive packaged smoke, and hostile corpus as future native addon promotion work.
- [x] Keep docs/release-matrix evidence gate from counting signed packaged native addon, integrity, dependency scan, and crash isolation work twice.

#### P13-40 `child_process` scoped runner

Reason open: denial/shell-bridge decision gate drift cleanup; future scoped runner promotion remains `implementation`, `policy`, `packaging`.

- [x] Keep scoped exec/execFile/spawn-style API shape as future scoped runner promotion work.
- [x] Keep app-private binary packaging, allowlist metadata, hash checks, and path resolution as future scoped runner promotion work.
- [x] Reject arbitrary paths, downloaded binaries, shell operators, detached processes, and inherited handles.
- [x] Keep bounded stdio, timeout, cancellation, kill, and cleanup as future scoped runner promotion work.
- [x] Keep process accounting, packaged metadata, disclosure, hostile corpus, and rollback controls as future scoped runner promotion work.
- [x] Keep docs/matrix evidence gate from counting scoped runner allowlist, stdio/cleanup, packaged evidence, and hostile corpus twice.

### Desktop Modules, npm, Process

#### P13-41 ESM loader parity

Reason open: controlled ESM subset gate drift cleanup; future full desktop loader parity remains `implementation`, `packaging`, `evidence`.

- [x] Decide native loader hooks and raw `node:module` authority.
- [x] Decide custom condition sets, import maps, and loader registration boundaries.
- [x] Keep packaged encrypted package exports/imports graphs and broader dynamic import corpus as future full loader parity work.
- [x] Keep top-level-await/linking cancellation and service-destroy cleanup stress as future full loader parity work.
- [x] Keep source-map parity for ESM and DevTools/debug reporting as future full loader parity work.
- [x] Keep docs/release-matrix evidence gate from counting packaged graph, pending-evaluation cleanup, and source-map parity twice.

#### P13-42 npm/package manager on Android

Reason open: `implementation`, `policy`, `packaging`.

- [x] Fix current verifier/source evidence for local-store `remove(name, version)`.
- [x] Keep Android registry downloader, cache management, tarball extraction, and integrity-lock authority stable-denied as runtime user APIs for v1.1; implement only after a reviewed mobile install authority exists.
- [x] Decide npm CLI, lifecycle, bin, native execution, and raw node_modules mutation policy.
- [x] Keep host-managed install equivalent as the safe default until mobile install authority is reviewed.
- [x] Keep docs/release-matrix evidence gate from counting the same signed native payload and packaged distribution work twice.
- [x] Keep packaged distribution evidence as future Android package-manager promotion work rather than a P13-42 v1.1 decision blocker.

#### P13-43 stdlib conformance

Reason open: representative conformance gate drift cleanup; future full stdlib promotion remains `evidence`, `implementation`, `packaging`.

- [x] Keep broader module-by-module hostile input coverage as future full stdlib promotion work.
- [x] Keep Android terminal/OS edge cases and lifecycle evidence as future full stdlib promotion work.
- [x] Grow stdlib-dependent npm corpus and record pass-rate deltas.
- [x] Document per-module Android differences and stable denials.
- [x] Keep docs/release-matrix evidence gate from counting broader stdlib conformance and packaged lifecycle work twice.

#### P13-44 process/env/argv parity

Reason open: `packaging`.

- [x] Decide full desktop process parity boundaries.
- [x] Add Android signal delivery and native process termination evidence or stable denials.
- [x] Finalize inherited environment and process exit policy.
- [x] Keep packaged lifecycle evidence as future packaged profile evidence rather than a P13-44 Safe process subset blocker.

### Packaging, Plugins, Distribution

#### P13-45 packaged APK capability metadata

Reason open: metadata gate drift cleanup; future promotion remains `implementation`, `packaging`.

- [x] Ensure declared `node.permissions` evidence is present in verifier inputs.
- [x] Keep packaged profile, builtin, native asset, and filesystem-root metadata diagnostic-only for v1.1.
- [x] Keep install/run synthesis, disclosure UX, and profile-specific runtime promotion evidence as future packaged promotion work rather than P13-45 metadata blockers.
- [x] Keep undeclared bridge capability denial covered.

#### P13-46 plugin system parity

Reason open: decision gate drift cleanup; future promotion remains `implementation`, `policy`, `packaging`.

- [x] Keep signed plugin archive install/update policy as future plugin distribution work.
- [x] Keep Android provider plugin adapter design as future plugin distribution work.
- [x] Keep plugin-declared permissions bounded by host manifest capability ceilings.
- [x] Keep docs/release-matrix evidence gate from counting the same plugin promotion and packaged distribution work twice.
- [x] Keep native plugin isolation and loading policy denied/deferred until a future promotion gate exists.
- [x] Keep remote marketplace cache/update/rollback behavior as future marketplace promotion work.
- [x] Keep packaged install/run evidence as future packaged plugin distribution work rather than a P13-46 v1.1 decision blocker.

#### P13-47 dependency compatibility corpus

Reason open: corpus gate drift cleanup; future CI scheduling remains `evidence`.

- [x] Keep connected AutoJs6 real npm corpus artifact scheduling as future CI workflow work.
- [x] Add targeted packages for stdlib, ESM, network, fs, and package-manager deltas.
- [x] Keep live registry drift and unsafe package execution out of default CI.
- [x] Publish pass-rate deltas in the summary gate.

#### P13-48 package lock and integrity policy

Reason open: shared integrity policy gate drift cleanup; future plugin/marketplace distribution remains `implementation`, `packaging`.

- [x] Keep signed plugin archive extraction and update rollback as future plugin distribution work.
- [x] Keep marketplace cache pruning and user-facing corruption recovery as future marketplace distribution work.
- [x] Delegate signed native addon artifact verification to the P13-39 packaged native path; keep P13-48 as shared integrity policy and fail-closed diagnostics until that path exists.
- [x] Unify integrity policy across package manager, plugins, and native payloads.

### Profile, Security, Rollback, Privacy

#### P13-49 profile selection UX and diagnostics

Reason open: diagnostics gate drift cleanup; future UX remains `implementation`, `policy`, `packaging`.

- [x] Keep project settings UI for requested profiles and capabilities as future UX implementation work rather than a P13-49 diagnostics blocker.
- [x] Keep packaged builder UI/metadata review for profile requests as future packaged UX work.
- [x] Keep actual effective Pro/Desktop/Debug profile switching stable-denied for v1.1; enable only after P13-50 to P13-52 and later promotion gates are satisfied.
- [x] Keep diagnostics explicit for denied or undeclared capabilities.

#### P13-50 relaxed capability security corpus

Reason open: security-corpus checklist drift cleanup; future runtime
relaxed-capability promotion remains gated by rollout, rollback, privacy, and
packaged evidence.

- [x] Convert descriptors into runnable promotion guards for each relaxed capability.
- [x] Keep Safe-default regression coverage for raw network, expanded fs roots, Java interop, child_process, native addon, inspector, and WASI.
- [x] Link every positive promotion path to rollback and privacy disclosure requirements.
- [x] Keep release Java interop and raw authority unpromoted until prompts and packaged behavior exist.

#### P13-51 rollback playbook

Reason open: playbook gate drift cleanup; future implementation remains `implementation`, `policy`, `packaging`.

- [x] Defer runtime profile promotion rollback controls and automatic crash circuit breaker wiring until effective profile switching is promoted.
- [x] Keep settings UI and packaged builder rollback controls as future implementation work rather than P13-51 playbook blockers.
- [x] Add remote packaged kill-switch policy or explicitly reject it.
- [x] Keep packaged Safe-profile patch/downgrade evidence as future packaged promotion work.

#### P13-52 privacy and disclosure policy

Reason open: policy gate drift cleanup; future implementation remains `implementation`, `packaging`, `policy`.

- [x] Keep settings UI prompt copy and localization as future implementation work rather than a P13-52 policy blocker.
- [x] Keep real foreground disclosure ownership and stop behavior as future promoted-provider implementation work.
- [x] Keep packaged disclosure manifest review UI as future packaged promotion work.
- [x] Keep docs/surface gate evidence-only so prompt UI, foreground disclosure, and packaged review UI remain future implementation work without blocking the P13-52 policy gate.
- [x] Add automatic crash circuit breaker and remote packaged kill-switch disclosure implications.
- [x] Ensure TypeScript declaration and verifier evidence stay synchronized.

### Docs, Examples, And Final Gate

#### P13-53 Pro parity examples

Reason open: example-catalog gate drift cleanup; future packaged promotion remains `evidence`, `packaging`.

- [x] Add focused runtime smoke for already-covered Activity-owned UI and WorkManager one-shot examples.
- [x] Keep live packaged Pro examples as future work after provider promotion.
- [x] Add prompt/disclosure UI references and denial paths.
- [x] Execute high-risk snippets only under explicit opt-in profiles.
- [x] Keep static catalog available for migration before runtime promotion.

#### P13-54 desktop Node parity examples

Reason open: example-catalog gate drift cleanup; future packaged promotion remains `evidence`, `packaging`.

- [x] Add focused runtime smoke for already-promoted local ESM, checked-in pure JavaScript dependency, and scoped advanced fs examples.
- [x] Keep packaged desktop examples as future work where providers are promoted.
- [x] Link every example to profile metadata, capability requirements, integrity policy, and rollback guidance.
- [x] Keep non-promoted snippets static and clearly marked.
- [x] Keep device evidence for desktop profile examples as future work once runtime authority exists.

#### P13-55 TypeScript declaration profiles

Reason open: declaration gate drift cleanup; release packaging remains `implementation`, `policy`.

- [x] Tie conditional declarations to actual runtime profile promotion.
- [x] Keep denied modules type-described as gated surfaces, not silently callable defaults.
- [x] Keep generated declaration package release checks as future release-packaging work after promotion.
- [x] Sync declarations with P13-49 to P13-52 profile/rollback/privacy behavior.

#### P13-56 migration and Pro9 comparison docs

Reason open: documentation gate drift cleanup; future docs remain `evidence`, `packaging`.

- [x] Keep packaged Pro/Desktop migration cookbook as future documentation work once provider and profile promotion exists.
- [x] Keep prompt/disclosure UX and device permission matrix as future documentation work after promoted runtime evidence exists.
- [x] Keep runtime parity claims partial until promoted paths are executable.
- [x] Depend on P13-57 final summary for completed/open/deferred/rejected state.

#### P13-57 Phase 13 summary and final gate

Reason open: `evidence`, `policy`.

- [x] Require every task verifier to report zero blockers before final completion.
- [x] Keep open/deferred/rejected categories explicit and non-promoted.
- [ ] Convert partial summary into strict release gate after runtime Pro/Desktop promotion.
- [ ] Close only when required final blockers are completed or explicitly deferred/rejected by policy.

## Immediate Low-Friction Follow-Ups

These items look closeable without inventing new high-risk runtime authority:

- Completed verifier/document token drift cleanup for `P13-06`, `P13-18`,
  `P13-23` to `P13-26`, `P13-29`, `P13-31` to `P13-36`, `P13-42`,
  `P13-45`, `P13-50`, `P13-52`, and the P13-57 summary gate. Current
  generated reports show no missing evidence-token or policy-violation false
  positives.
- Completed provider-free utility coverage for `P13-27` (`Arrayx`,
  `Numberx`, resource-free helpers). Raw Android object parity and
  resource-backed utilities remain separate promotion gates.
- Grow offline corpora for `P13-43` and `P13-47` with fixed package fixtures,
  because this does not require live registry access or new dangerous runtime
  authority.
- Continue converting static examples in `P13-53` and `P13-54` into smoke tests
  only for already-promoted providers; keep high-risk snippets static until
  profile/security gates are real.
