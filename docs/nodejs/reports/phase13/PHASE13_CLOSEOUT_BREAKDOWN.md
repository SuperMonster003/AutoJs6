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
| `second_execution_slot` | `P13-06`, `P13-08` | `evidence` | Two-slot scheduling has landed; close after pool-busy evidence is current and P13-08 soak evidence is archived. |
| `packaged_long_running` | `P13-07`, `P13-45`, `P13-52` | `evidence`, `packaging` | Add packaged lifecycle restore/cleanup, notification/background matrix, metadata, and disclosure evidence. |
| `full_heavy_resource_stress` | `P13-08` | `evidence`, `external` | Archive 8-hour quick soak and 24-hour final soak with comparable metrics. |
| `android_side_package_installation` | `P13-09`, `P13-42`, `P13-48` | `implementation`, `policy`, `packaging` | Move from guarded local store to registry/tarball/install authority with integrity, rollback, and distribution policy. |
| `worker_threads` | `P13-34`, `P13-35` | `external`, `policy` | Prove native worker availability on target ABIs or keep process-worker replacement as the supported path. |
| `controlled_wasi` | `P13-38` | `implementation`, `policy` | Either implement a controlled WASI facade or keep raw WASI denied with pure WebAssembly as the supported path. |
| `raw_network` | `P13-29`, `P13-30`, `P13-50` to `P13-56` | `implementation`, `policy`, `packaging` | Keep Safe profile denied; promote only scoped Desktop/relaxed-capability tracks with network budgets, privacy, rollback, and packaged evidence. |
| `node_24_17_community_fork` | future runtime lane | `external` | Track as future runtime maintenance; do not block current Node 24.5 Phase 13 work unless upgrade artifacts become available. |

## Task Closeout Checklist

### Runtime Graduation

#### P13-06 second execution slot

Reason open: mostly `evidence`.

- [x] Refresh or replace the `defaultProcessPoolBusyResult` evidence token in the verifier/report.
- [x] Attach current focused primary/overflow shared-resource stress evidence.
- [ ] Depend on completed P13-08 soak evidence before marking slot promotion complete.
- [ ] Re-run `verifyNodePhase13SecondExecutionSlotScheduler` and summary generation with zero blockers.

#### P13-07 packaged long-running service

Reason open: `evidence`, `packaging`.

- [ ] Add packaged `autojs6:lifecycle` restore and cleanup evidence.
- [ ] Add Android version, notification permission, and background-start denial matrix.
- [ ] Verify foreground notification, stop action, checkpoint, task-removal cleanup, relaunch, and crash recovery.
- [ ] Record packaged metadata and disclosure denial paths before promotion.

#### P13-08 full heavy resource stress

Reason open: `evidence`, `external`.

- [ ] Produce `build/reports/nodejs/final-soak/quick-soak-8h.json`.
- [ ] Produce `build/reports/nodejs/final-soak/final-soak-24h.json`.
- [ ] Archive comparable memory, fd, thread, runtime, and orphan-process metrics.
- [ ] Re-run `verifyNodePhase13FullHeavyResourceStress` and update the final summary.

#### P13-09 Android-side package installation manager

Reason open: `implementation`, `policy`, `packaging`.

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

Reason open: `implementation`, `policy`, `evidence`.

- [ ] Implement a bounded proxy-interface or `java.defineClass()` provider.
- [ ] Add listener/interface examples backed by connected-device instrumentation.
- [ ] Prove callback cleanup, thread dispatch, timeout, leak diagnostics, and packaged metadata.
- [ ] Keep global `JavaAdapter` unsupported unless a secure compatibility shape is explicitly accepted.

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

#### P13-19 accessibility/selector parity

Reason open: `implementation`, `evidence`, `policy`.

- [ ] Add Rhino selector aliases and chain helpers beyond the current descriptor subset.
- [ ] Decide exact `waitFor`/`pickup`/`detect` semantics or keep stable denials.
- [ ] Add packaged accessibility-service lifecycle evidence.
- [ ] Add real-device matrix for selector actions, Back/Home/Recents, and denial paths.
- [ ] Split gesture and root/Shizuku input automation into explicit future capabilities.

#### P13-20 screenshot/image/OCR parity

Reason open: `implementation`, `evidence`, `packaging`.

- [ ] Implement Activity-owned MediaProjection request/capture/stop provider.
- [ ] Add foreground disclosure, stop action, task-removal cleanup, relaunch, crash, and denial evidence.
- [ ] Promote image operations such as save, clip, resize, grayscale, threshold, findImage, matchTemplate, and color matching.
- [ ] Define OCR fallback/model lookup policy for ML Kit, Paddle, Rapid, and plugin providers.
- [ ] Add packaged image/OCR smoke, quota, temp-handle, and leak/stress evidence.

### Pro Tasks, App, Shell, Media, Utilities

#### P13-21 task database provider

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Decide whether daily/weekly/user tasks live in `tasks`, `work_manager`, or a Pro-only provider.
- [ ] Define JSON task schema, row ownership, id mapping, migrations, downgrade behavior, and legacy visibility.
- [ ] Implement add/query/update/remove over frozen JSON snapshots.
- [ ] Add capability metadata, disclosure, rollback, and packaged task smoke.

#### P13-22 IntentTask/broadcast provider

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Replace stable rejection stubs only after a JSON-safe broadcast provider exists.
- [ ] Define ownership between legacy Rhino rows, Node descriptors, and packaged records.
- [ ] Add query/get snapshots without exposing raw IntentTask or receiver handles.
- [ ] Add manifest synthesis, exported/non-exported policy, trigger payloads, and install/trigger/remove packaged smoke.

#### P13-23 app/intent/broadcast parity

Reason open: `implementation`, `policy`, `evidence`, `packaging`.

- [ ] Add a descriptor alias matrix for Rhino activity short forms.
- [ ] Add safe Activity short-form device smoke and invalid component/flag/extras negative tests.
- [ ] Define redacted installed-app list schemas and privacy disclosure policy.
- [ ] Add packaged scoped file view/edit evidence.
- [ ] Decide `sendBroadcast`, raw `Uri`/`FileProvider`, dual-user, and root/Shizuku app tiers.

#### P13-24 shell/root/Shizuku

Reason open: `implementation`, `evidence`, `policy`.

- [x] Split `shell.root` and `shell.shizuku` capability checks and diagnostics in runtime/verifier evidence.
- [x] Add project/package metadata examples for both capabilities.
- [ ] Add root-available and root-unavailable device matrix.
- [ ] Add AbortSignal cancellation for in-flight shell commands.
- [ ] Implement Shizuku only after service binding, permission, lifecycle, cleanup, packaged disclosure, and rollback evidence exist.

#### P13-25 notifications/settings/power manager

Reason open: `implementation`, `evidence`, `packaging`.

- [ ] Add notification actions, listeners, groups, styles, remote input, and richer layouts.
- [ ] Add DND, exact alarm, foreground disclosure, and notification action packaged evidence.
- [ ] Add wakelocks, request-ignore-battery-optimizations flow, callbacks, and direct mutation policy.
- [ ] Add OEM matrix, packaged disclosure, and background-launch behavior evidence.

#### P13-26 media/recorder/mediainfo

Reason open: `implementation`, `evidence`, `packaging`.

- [ ] Add audio playback and MediaPlayer lifecycle with focus policy.
- [ ] Decide and implement MediaStore query/insert/update/delete boundaries.
- [ ] Add real recording start/stop/session handles, permission flow, foreground disclosure, and cleanup.
- [ ] Decide screen/internal audio recording policy.
- [ ] Add packaged media assets, recorder smoke, denial matrix, and privacy copy.

#### P13-27 utility-heavy Rhino modules

Reason open: `implementation`, `packaging`, `policy`.

- [x] Add separate pure helper gates for `Arrayx` and `Numberx`.
- [ ] Add `zip`, `canvas`, OpenCC, and full pinyin resource providers.
- [ ] Add packaged resource smoke and resource lookup diagnostics.
- [ ] Decide raw Android/OpenCV object parity or keep stable denials.

### Desktop Network And Filesystem

#### P13-29 HTTP/HTTPS client parity

Reason open: `evidence`, `policy`, `packaging`.

- [ ] Attach live Android/OkHttp HTTP and HTTPS request evidence.
- [ ] Attach packaged `network` to `android.permission.INTERNET` proof for this path.
- [ ] Keep full raw socket, DNS lookup, TLS session, and backpressure semantics scoped to P13-30 or later.
- [ ] Decide whether any per-request proxy support is provider-owned or stays denied.

#### P13-30 `net` / `tls` / `dns`

Reason open: `implementation`, `policy`, `evidence`, `packaging`.

- [ ] Implement or reject real TCP/TLS client socket authority with budgets and lifecycle cleanup.
- [ ] Decide server socket/listen policy, bind address policy, and foreground/background behavior.
- [ ] Complete DNS privacy review so resolver servers, search domains, VPN state, and topology do not leak.
- [ ] Add fd/socket leak instrumentation and packaged INTERNET proof.
- [ ] Decide debug raw-network opt-in release posture, limits, and rollback.

#### P13-31 UDP / `dgram`

Reason open: `policy`, `implementation`.

- [x] Decide whether `dgram` remains stable-denied for v1.1 or starts a future controlled provider.
- [ ] If promoted later, split unicast, server bind/listen, broadcast, multicast, and source-specific multicast capabilities.
- [ ] Prove permission mapping, packet budgets, lifecycle cleanup, and packaged behavior.
- [ ] Keep local shadow bypass and raw socket authority denied until the provider exists.

#### P13-32 filesystem profile relaxation

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Implement user-authorized roots for SAF/document roots and app-private roots.
- [ ] Define manifest metadata, grant persistence, revocation, diagnostics, and rollback.
- [ ] Re-run symlink, realpath, `..`, NUL, `/proc`, `/sys`, and `/dev` audits for each root class.
- [ ] Add packaged expanded-root smoke before promotion.

#### P13-33 desktop fs advanced APIs

Reason open: `evidence`, `packaging`.

- [ ] Expose pending watcher/watchFile counts through diagnostics or report fields.
- [ ] Add packaged watcher, fd, FileHandle, and stream close-on-destroy evidence.
- [ ] Promote packaged realpath and opendir evidence from source markers to device-exported reports.
- [ ] Document Android watcher coalescing, recursive-watch denial, and realpath differences.
- [ ] Re-run advanced APIs against any P13-32 expanded-root classes.

### Desktop Execution Runtime

#### P13-34 `worker_threads`

Reason open: `external`, `evidence`, `policy`, `packaging`.

- [ ] Prove native worker availability on the target promotion ABIs.
- [ ] Run and archive the experimental worker gate with `autojs.nodejs.worker_threads.experimental=true`.
- [ ] Add packaged worker smoke evidence.
- [ ] Add crash/resource evidence for startup failure, exception, timeout, parent cleanup, and packaged failure modes.
- [ ] Decide nested worker policy and document SharedArrayBuffer, Atomics, resourceLimits, thread, and RSS ceilings.

#### P13-35 process-worker replacement

Reason open: `implementation`, `external`, `policy`.

- [ ] Archive WorkerPool evidence on target ABIs.
- [ ] Decide whether a user-facing process-worker surface exists.
- [ ] Add slot accounting, cleanup, packaging, and UX for any promoted replacement.
- [ ] Keep raw spawn/fork/stdio/signals/process handles delegated to P13-40.

#### P13-36 `vm`

Reason open: `evidence`, `packaging`.

- [ ] Add focused memory and compiled-script budget evidence.
- [ ] Add timeout/lifecycle stress and leakage probes.
- [ ] Add packaged smoke for packages using `vm.Script` and contexts.
- [x] Keep VM documented as compatibility, not a security boundary.

#### P13-37 inspector/devtools

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Implement trusted debug UI or host command start/revoke flow.
- [ ] Add loopback-only listener descriptors, ADB forward setup/cleanup, and public-interface denial proof.
- [ ] Add debug-build DevTools attach coverage for breakpoints, pause/resume, console, async stacks, source maps, and ESM names.
- [ ] Add CPU/heap profile artifact export, redaction, budget, and cleanup.
- [ ] Add packaged release denial and debug-packaged metadata evidence.

#### P13-38 controlled WASI

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Implement a controlled `autojs6:wasi` facade or formally keep only pure WebAssembly.
- [ ] Implement scoped/authorized preopen mapping with lexical and realpath escape denial.
- [ ] Define args, env, virtual fd table, stdio, clock, random, process-exit, and network-denial policies.
- [ ] Add module/memory/stdout/stderr/timeout/concurrency budgets.
- [ ] Add packaged `.wasm` assets, hostile WASI corpus, and worker/WASM integration evidence.

#### P13-39 native addon loading

Reason open: `policy`, `implementation`, `packaging`.

- [ ] Define signed packaged native-addon manifest with per-ABI artifacts.
- [ ] Tie ABI and Node-API/version allowlists to embedded Node, Android ABI, NDK, and release channel.
- [ ] Verify hashes and signatures before resolution.
- [ ] Keep runtime download, npm build, lifecycle scripts, and postinstall execution denied.
- [ ] Add dependency scan, APK-builder preflight, crash isolation, positive packaged smoke, and hostile corpus.

#### P13-40 `child_process` scoped runner

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Design scoped exec/execFile/spawn-style API shape.
- [ ] Define app-private binary packaging, allowlist metadata, hash checks, and path resolution.
- [ ] Reject arbitrary paths, downloaded binaries, shell operators, detached processes, and inherited handles.
- [ ] Implement bounded stdio, timeout, cancellation, kill, and cleanup.
- [ ] Add process accounting, packaged metadata, disclosure, hostile corpus, and rollback controls.

### Desktop Modules, npm, Process

#### P13-41 ESM loader parity

Reason open: `implementation`, `policy`, `packaging`, `evidence`.

- [ ] Decide native loader hooks and raw `node:module` authority.
- [ ] Decide custom condition sets, import maps, and loader registration boundaries.
- [ ] Add packaged encrypted package exports/imports graphs and broader dynamic import corpus.
- [ ] Add top-level-await/linking cancellation and service-destroy cleanup stress.
- [ ] Improve source-map parity for ESM and DevTools/debug reporting.

#### P13-42 npm/package manager on Android

Reason open: `implementation`, `policy`, `packaging`.

- [x] Fix current verifier/source evidence for local-store `remove(name, version)`.
- [ ] Implement Android registry downloader, cache management, tarball extraction, and integrity-lock authority.
- [ ] Decide npm CLI, lifecycle, bin, native execution, and raw node_modules mutation policy.
- [ ] Keep host-managed install equivalent as the safe default until mobile install authority is reviewed.
- [ ] Add packaged distribution evidence.

#### P13-43 stdlib conformance

Reason open: `evidence`, `implementation`.

- [ ] Expand module-by-module hostile input coverage.
- [ ] Add Android terminal/OS edge cases and lifecycle evidence.
- [x] Grow stdlib-dependent npm corpus and record pass-rate deltas.
- [ ] Document per-module Android differences and stable denials.

#### P13-44 process/env/argv parity

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Decide full desktop process parity boundaries.
- [ ] Add Android signal delivery and native process termination evidence or stable denials.
- [ ] Finalize inherited environment and process exit policy.
- [ ] Add packaged lifecycle evidence.

### Packaging, Plugins, Distribution

#### P13-45 packaged APK capability metadata

Reason open: `implementation`, `packaging`.

- [x] Ensure declared `node.permissions` evidence is present in verifier inputs.
- [ ] Make packaged profile, builtin, native asset, and filesystem-root metadata drive reviewed synthesis rather than diagnostics only.
- [ ] Add install/run synthesis, disclosure UX, and profile-specific runtime promotion evidence.
- [x] Keep undeclared bridge capability denial covered.

#### P13-46 plugin system parity

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Define signed plugin archive install/update policy.
- [ ] Add Android provider plugin adapter design and host capability ceilings.
- [ ] Decide native plugin isolation and loading policy.
- [ ] Add remote marketplace cache/update/rollback behavior.
- [ ] Add packaged install/run evidence.

#### P13-47 dependency compatibility corpus

Reason open: `evidence`, `policy`.

- [ ] Schedule connected AutoJs6 real npm corpus artifacts before host differential comparison.
- [x] Add targeted packages for stdlib, ESM, network, fs, and package-manager deltas.
- [ ] Keep live registry drift and unsafe package execution out of default CI.
- [ ] Publish pass-rate deltas in the summary gate.

#### P13-48 package lock and integrity policy

Reason open: `implementation`, `packaging`.

- [ ] Implement signed plugin archive extraction and update rollback.
- [ ] Implement marketplace cache pruning and user-facing corruption recovery.
- [ ] Implement signed native addon artifact verification once P13-39 has a packaged path.
- [ ] Unify integrity policy across package manager, plugins, and native payloads.

### Profile, Security, Rollback, Privacy

#### P13-49 profile selection UX and diagnostics

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Add project settings UI for requested profiles and capabilities.
- [ ] Add packaged builder UI/metadata review for profile requests.
- [ ] Implement actual effective Pro/Desktop/Debug profile switching only after P13-50 to P13-52 are satisfied.
- [ ] Keep diagnostics explicit for denied or undeclared capabilities.

#### P13-50 relaxed capability security corpus

Reason open: `policy`, `evidence`.

- [ ] Convert descriptors into runnable promotion guards for each relaxed capability.
- [ ] Keep Safe-default regression coverage for raw network, expanded fs roots, Java interop, child_process, native addon, inspector, and WASI.
- [ ] Link every positive promotion path to rollback and privacy disclosure requirements.
- [ ] Keep release Java interop and raw authority unpromoted until prompts and packaged behavior exist.

#### P13-51 rollback playbook

Reason open: `implementation`, `policy`, `packaging`.

- [ ] Implement runtime profile promotion rollback controls.
- [ ] Add automatic crash circuit breaker.
- [ ] Add settings UI and packaged builder rollback controls.
- [ ] Add remote packaged kill-switch policy or explicitly reject it.
- [ ] Add packaged Safe-profile patch/downgrade evidence.

#### P13-52 privacy and disclosure policy

Reason open: `implementation`, `packaging`, `policy`.

- [ ] Add settings UI prompt copy and localization.
- [ ] Add real foreground disclosure ownership and stop behavior.
- [ ] Add packaged disclosure manifest review UI.
- [ ] Add automatic crash circuit breaker and remote packaged kill-switch disclosure implications.
- [x] Ensure TypeScript declaration and verifier evidence stay synchronized.

### Docs, Examples, And Final Gate

#### P13-53 Pro parity examples

Reason open: `evidence`, `packaging`.

- [x] Add focused runtime smoke for already-covered Activity-owned UI and WorkManager one-shot examples.
- [ ] Convert static examples into live packaged Pro examples after provider promotion.
- [ ] Add prompt/disclosure UI references and denial paths.
- [ ] Execute high-risk snippets only under explicit opt-in profiles.
- [ ] Keep static catalog available for migration before runtime promotion.

#### P13-54 desktop Node parity examples

Reason open: `evidence`, `packaging`.

- [x] Add focused runtime smoke for already-promoted local ESM and checked-in pure JavaScript dependency examples.
- [ ] Convert static desktop examples into packaged smoke where providers are promoted.
- [ ] Link every example to profile metadata, capability requirements, integrity policy, and rollback guidance.
- [ ] Keep non-promoted snippets static and clearly marked.
- [ ] Add device evidence for desktop profile examples once runtime authority exists.

#### P13-55 TypeScript declaration profiles

Reason open: `implementation`, `policy`.

- [ ] Tie conditional declarations to actual runtime profile promotion.
- [x] Keep denied modules type-described as gated surfaces, not silently callable defaults.
- [ ] Add generated declaration package release checks after promotion.
- [x] Sync declarations with P13-49 to P13-52 profile/rollback/privacy behavior.

#### P13-56 migration and Pro9 comparison docs

Reason open: `evidence`, `packaging`.

- [ ] Add packaged Pro/Desktop migration cookbook once provider and profile promotion exists.
- [ ] Add prompt/disclosure UX and device permission matrix.
- [ ] Keep runtime parity claims partial until promoted paths are executable.
- [x] Depend on P13-57 final summary for completed/open/deferred/rejected state.

#### P13-57 Phase 13 summary and final gate

Reason open: `evidence`, `policy`.

- [ ] Require every task verifier to report zero blockers before final completion.
- [x] Keep open/deferred/rejected categories explicit and non-promoted.
- [ ] Convert partial summary into strict release gate after runtime Pro/Desktop promotion.
- [ ] Close only when required final blockers are completed or explicitly deferred/rejected by policy.

## Immediate Low-Friction Follow-Ups

These items look closeable without inventing new high-risk runtime authority:

- Completed verifier/document token drift cleanup for `P13-06`, `P13-18`,
  `P13-23` to `P13-26`, `P13-31` to `P13-36`, `P13-42`, `P13-45`,
  `P13-52`, and the P13-57 summary gate. Current generated reports show no
  missing evidence-token or policy-violation false positives.
- Completed provider-free utility coverage for `P13-27` (`Arrayx`,
  `Numberx`, resource-free helpers). Raw Android object parity and
  resource-backed utilities remain separate promotion gates.
- Grow offline corpora for `P13-43` and `P13-47` with fixed package fixtures,
  because this does not require live registry access or new dangerous runtime
  authority.
- Continue converting static examples in `P13-53` and `P13-54` into smoke tests
  only for already-promoted providers; keep high-risk snippets static until
  profile/security gates are real.
