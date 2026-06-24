# Final Blocker Registry

Machine-readable source:

```text
docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json
```

The registry separates required internal blockers from conditional decisions,
intentional Safe Node Profile non-goals, and external runtime maintenance.

## Required Internal

- `crash_evidence_automation`
- `android_side_package_installation`
- `packaged_long_running`
- `second_execution_slot`
- `full_heavy_resource_stress`

These must be resolved before Phase 11 can report `completed`.

Current crash evidence progress:

- `crash_evidence_automation`: Phase 11 crash bundle tooling can now import
  runtime resource snapshots and timeline tails as first-class evidence. Phase
  13 adds a runtime JSON marker store, next-launch collector, embedded script
  service marker writes, native process service marker writes, and a host
  collector. Fatal native chaos injection, packaged APK crash smoke, and
  signal-safe native marker proof remain open.

Current multi-instance progress:

- `second_execution_slot`: explicit one-shot routing can now select `primary`
  or `overflow_1`; focused instrumentation covers `overflow_1` diagnostics and
  concurrent primary/overflow execution in different Android service processes.
  Phase 13 adds a client-side default one-shot scheduler that can lease
  `primary` / `overflow_1` on eligible devices, preserves
  `processPoolSlotSource` diagnostics, and reports pool-busy failures. Packaged
  behavior, fatal slot-crash fallback/quarantine, and stress promotion remain
  open.

Current packaged long-running progress:

- `packaged_long_running`: interactive long-running works, and Phase 13 adds a
  partial packaged dispatch path through
  `node.executionMode=interactive_long_running`, the `packaged_long_running`
  launch surface, inrt foreground-service routing, and a host verifier.
  Packaged install/run smoke, `autojs6:lifecycle` restore/cleanup evidence,
  Android permission/background-start policy coverage, and quota/stress
  promotion remain open.

Current full heavy resource stress progress:

- `full_heavy_resource_stress`: core memory leak, resource stress,
  long-running, and worker stress anchors already exist. Phase 13 adds a
  dedicated verifier for `quick-soak-8h.json` and `final-soak-24h.json`, plus
  comparable metric checks for RSS, Java heap, native heap, thread count, fd
  count, and bridge diagnostics. The blocker remains open until real 8 hour
  and 24 hour device reports are available and clean.

Current Android package installation progress:

- `android_side_package_installation`: Phase 13 adds
  `NodeAndroidPackageInstallManager`, an app-private local package store with
  transactional install/update/remove/list/verify/prune, cancellation, store
  locking, SHA-512 integrity, lifecycle/bin/native/path-escape denial, and
  focused instrumentation. The blocker remains open until a prepared-device
  run, runtime/JS API exposure, registry/download policy, signed native-payload
  decision, and plugin/package distribution integration land.

Resolved in Phase 11:

- `adapter_v1_real_execution_path`: `AutoJsNodeRuntimeApiV1` is now a
  selectable execution path for Node 24.5 while `legacy_jni` remains the
  default fallback pending A/B, packaged, memory, and crash-injection promotion
  gates.

## Conditional

- `worker_threads`
- `controlled_wasi`
- `x86`

These require a final product decision, but they may resolve as implemented,
intentionally unsupported, or optional.

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
