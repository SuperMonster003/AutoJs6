# Final Blocker Registry

Machine-readable source:

```text
docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json
```

The registry separates required internal blockers from conditional decisions,
intentional Safe Node Profile non-goals, and external runtime maintenance.

## Current Registry And R6 Revalidation

The machine-readable registry records zero unresolved required internal
blockers. All six historical Phase 11/13 capability blockers remain `closed`,
and the conditional decisions are resolved for the current Safe profile.

R6 production-evidence revalidation is a separate Core completion gate. The
persistent process-global runtime lifecycle and required external `plugin`
delivery path were introduced after the historical closure evidence. Current
lifecycle, crash, memory, ABI, packaged, multi-device, and 8-hour/24-hour soak
reports must therefore be regenerated and pass before Core completion can be
declared. This freshness requirement does not rewrite the historical blocker
statuses or imply that R6 has already passed.

## Historical Phase 14 Closeout Update

Phase 14 did not reopen final blockers. P14-39 records
`phase14_closeout_handoff_ready` with zero open final blockers, zero
release-blocking reports, and a v1.3 future promotion backlog exported at
`docs/nodejs/reports/phase14/PHASE14_FUTURE_BACKLOG.json`.
This is a historical closeout record, not the current R6 evidence decision.

## Required Internal

- `adapter_v1_real_execution_path`: `closed`
- `crash_evidence_automation`: `closed`
- `android_side_package_installation`: `closed`
- `packaged_long_running`: `closed`
- `second_execution_slot`: `closed`
- `full_heavy_resource_stress`: `closed`

These historical capability items must all remain closed before Phase 11 can
report `completed`. R6 additionally requires fresh production evidence as
described above.

Current crash evidence status:

- `crash_evidence_automation`: closed by P13-05. Phase 11 crash bundle tooling
  can import
  runtime resource snapshots and timeline tails as first-class evidence. Phase
  13 adds a runtime JSON marker store, next-launch collector, embedded script
  service marker writes, native process service marker writes, a host collector,
  simulated native fatal running-marker collection, real fatal native SIGKILL
  chaos collection, packaged launch-surface marker contract, passing packaged
  APK crash smoke, and signal-safe fixed-size native marker proof.

Current multi-instance progress:

- `second_execution_slot`: closed by P13-06. Explicit one-shot routing can
  select `primary` or `overflow_1`; focused instrumentation covers
  `overflow_1` diagnostics and concurrent primary/overflow execution in
  different Android service processes. Phase 13 adds a client-side default
  one-shot scheduler that can lease `primary` / `overflow_1` on eligible
  devices, preserves `processPoolSlotSource` diagnostics, reports full-pool
  queue/dequeue diagnostics, propagates packaged process-pool slot metadata,
  records no-restart crash marker fallback evidence, and adds focused
  two-slot shared-resource stress. P13-08 still owns long-duration quick/final
  soak evidence through the separate `full_heavy_resource_stress` blocker, and
  automatic slot replacement/quarantine remains disabled until a future
  pool-coordinator promotion.

Current packaged long-running progress:

- `packaged_long_running`: closed by P13-07. Interactive long-running works,
  and Phase 13 closes packaged service mode through
  `node.executionMode=interactive_long_running`, the `packaged_long_running`
  launch surface, inrt foreground-service routing, packaged lifecycle
  checkpoint/read/clear, foreground Stop clean-exit/relaunch/crash-recovery,
  task-removal clean-exit/relaunch, and running-service force-stop
  next-launch crash collection. Focused evidence passes on QV710AF65F / Sony
  XQ-AT72 / Android 12 and Xiaomi 23046RP50C / Android 15. Android 15
  notification/background-start behavior is covered by requested/granted
  `POST_NOTIFICATIONS` plus a non-exported broadcast Stop receiver instead of
  background `startService`. P13-45/P13-52 keep packaged metadata and
  disclosure policy diagnostics fail-closed for future high-privilege packaged
  surface promotion.

Current full heavy resource stress progress:

- `full_heavy_resource_stress`: closed by P13-08. Xiaomi 23046RP50C /
  Android 15 produced clean canonical 8 hour quick and 24 hour final soak
  reports with comparable RSS, Java heap, native heap, thread count, fd count,
  and bridge diagnostics, and with no monotonic leaks, orphan process, or
  unrecoverable runtime.

Current Android package installation progress:

- `android_side_package_installation`: closed by P13-09/P13-42/P13-48 for the
  v1.1 local-store requirement. Phase 13 adds
  `NodeAndroidPackageInstallManager`, an app-private local package store with
  transactional install/update/remove/list/verify/prune, cancellation, store
  locking, SHA-512 integrity, lifecycle/bin/native/path-escape denial, and
  focused instrumentation. It also exposes source-authority diagnostics and
  `package_manager` guarded local mutation plus read-maintenance and
  policy-only source planning
  status/policy/sourcePolicy/planInstall/planUpdate/planRemove/planRegistryInstall/planTarballInstall/install/update/remove/list/verify/prune
  plus guarded `npm` alias facade while npm CLI, registry, lifecycle, bin,
  native payload, and unrestricted install authority remain denied. Registry
  download, npm CLI, signed/native payloads, plugin/package distribution, and
  packaged dependency distribution remain future promotion gates requiring
  reviewed authority, rollback, disclosure, and packaged evidence.

Current adapter v1 status:

- `adapter_v1_real_execution_path`: `AutoJsNodeRuntimeApiV1` is now a
  selectable execution path for Node 24.5 while `legacy_jni` remains the
  default fallback pending A/B, packaged, memory, and crash-injection promotion
  gates.

## Conditional

- `worker_threads`: `intentionally_unsupported`
- `controlled_wasi`: `closed`
- `x86`: `optional`

These decisions are resolved for the current Safe profile and do not block
Core completion. A future capability promotion may reopen its own evidence
gate without changing the current decision.

Current controlled WASI decision:

- `controlled_wasi`: closed by P13-38 for v1.1. Pure WebAssembly remains the
  supported runtime path, while raw `wasi` / `node:wasi`, callable controlled
  WASI facades, raw host paths, and raw fd/env/args surfaces stay denied. A
  future `autojs6:wasi` provider remains separate Desktop-profile promotion
  work requiring scoped preopens, virtual fd/env/args, resource budgets,
  packaged evidence, hostile corpus, and worker/WASM integration evidence.

## Intentional Non-Goals

- `native_addon`
- `unrestricted_child_process`
- `raw_network`
- `private_node_bindings`
- `production_inspector`

These are security boundaries, not completion blockers, when denial remains
stable and tested.

## External Runtime Maintenance

- `node_24_17_community_fork`

Node 24.17 stays outside Phase 11 completion until Android shared-libnode
artifacts exist for the required ABI set. Phase 11 continues on `node24_5`.
While this lane is `waiting_for_android_artifacts`, the final ABI gate may
record runtime artifact aggregate `bootstrap_only` as deferred external runtime
maintenance after required `arm64-v8a`, `armeabi-v7a`, and `x86_64`
`libnode.so` libraries plus adapter ABI/export evidence are ready.
