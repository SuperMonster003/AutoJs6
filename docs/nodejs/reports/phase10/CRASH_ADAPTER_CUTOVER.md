# Phase 10 Crash / Adapter Cutover

S10-47 through S10-52 define the crash evidence bundle and adapter cutover
gate for the current Node 24.5 runtime. This batch does not switch execution to
`adapter_v1`; the default runtime path remains `legacy_jni`.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10CrashAdapterCutover
.\gradlew.bat --console=plain :app:verifyNodeCrashAdapterCutoverGate
```

`verifyNodePhase10CrashAdapterCutover` writes:

- `build/reports/nodejs/phase10-crash-adapter-cutover.json`
- `build/reports/nodejs/phase10-crash-adapter-cutover.md`
- `build/reports/nodejs/phase10-crash-evidence-bundle/manifest.json`

`verifyNodeCrashAdapterCutoverGate` also runs the Phase 9 native crash pipeline
and the adapter ABI/export checks.

## S10-47 Crash Evidence Bundle

The Phase 10 verifier generates a synthetic crash evidence bundle manifest with
the required archive schema:

- logcat reference;
- tombstone or bugreport reference;
- native library hashes;
- Build ID best-effort metadata;
- runtime descriptor;
- capability flags;
- execution request placeholder;
- last error;
- package identity;
- ABI list.

The verifier does not inject a native crash and does not require root access.
Automatic crash-time capture remains future work.

## S10-48 Resource Snapshot And Timeline Tail

The gate records current runtime evidence for:

- timeline trace fields;
- resource registry presence;
- scoped watcher diagnostics;
- pending `node:test` callback diagnostics;
- provider/resource categories that must be included in future crash snapshots.

Crash-time snapshots are not yet implemented. The report fixes the target
timeline tail size at 256 events.

## S10-49 Adapter Operation Inventory

The adapter inventory maps `AutoJsNodeRuntimeApiV1` to the operations required
for a future cutover:

- create runtime;
- start runtime;
- execute source;
- execute module;
- poll/drain diagnostics;
- request stop;
- force stop;
- get state;
- get diagnostics;
- destroy runtime;
- post bridge result.

The public ABI remains C-only and does not expose `node::`, `v8::`, `uv_`, JNI
types, or `std::string`.

## S10-50 Through S10-52 Cutover Decision

The current `node_runtime_adapter_24_5.cpp` descriptor is still
`surface_only`, and execution still reports `legacy_jni_path`. The adapter ABI
and export gates are ready, but lifecycle execution is not migrated:

- `create` / `start` / `execute` return unavailable;
- `ERR_AUTOJS6_NODE_ADAPTER_NOT_MIGRATED` remains the adapter diagnostic;
- legacy-vs-adapter A/B execution is blocked;
- `adapter_v1` is not allowed to become default.

This keeps Phase 10 safe while preserving the ABI surface required for a future
native lifecycle migration.
