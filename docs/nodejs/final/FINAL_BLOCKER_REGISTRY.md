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
  runtime resource snapshots and timeline tails as first-class evidence, while
  safe fatal markers, automatic next-launch collection, and chaos injection
  remain open.

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
