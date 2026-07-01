# Phase 14 Worker Native Availability

Status: ready

P14-28 refreshes the v1.2 `worker_threads` availability decision from the
current Embedded Node 24.5 artifacts, Android loader path, and P13-34 native
preflight evidence. The result is intentional non-support for v1.2 runnable
workers until a target ABI reports `worker.preflight.status=available`.

## Decision

| Area | v1.2 state |
| --- | --- |
| Runtime slot | `node24_5` / Node `24.5.0` |
| Target ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Unsupported ABI | `x86` remains fail-fast |
| Current preflight | `worker.preflight.status=unavailable` |
| Current error | `ERR_AUTOJS6_WORKER_DISABLED` |
| Default authority | Not promoted |
| Project/package opt-in | Not promoted |
| Process-worker facade | Not promoted |

This is an audit closure, not a runtime promotion. `require("worker_threads")`
still rejects in the Safe/default profile, and `require("node:worker_threads")`
keeps the shape-only compatibility facade. The request-local experimental path
may run only far enough to report native availability; it must not substitute a
same-thread fallback for real Worker execution.

## ABI And Loader Boundary

- `app/src/main/jniLibs/arm64-v8a/libnode.so`,
  `app/src/main/jniLibs/armeabi-v7a/libnode.so`, and
  `app/src/main/jniLibs/x86_64/libnode.so` are the audited Node 24.5
  artifacts.
- `app/build.gradle.kts` keeps `x86` unsupported and fails fast if an
  unexpected `x86/libnode.so` appears.
- `app/src/main/cpp/CMakeLists.txt` requires matching Node headers and
  ABI-scoped `libnode.so` before the native bridge can build.
- `NativeNodeEmbeddedRuntimeBridge` loads `libc++_shared.so`, `libnode.so`,
  then `libautojs6-node.so`, and reports loader failure details.
- `node_bridge_core.cpp` classifies ABI mismatch, missing `libnode.so`,
  missing `libc++_shared.so`, missing dependent libraries, and missing symbols.

## Current Evidence

P13-34 archives the latest Sony gate run:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.worker_threads.experimental=true" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result: `tests=16`, `failures=0`, `skipped=14`. Runnable, stress, and
packaged worker cases skipped through `worker.preflight.status=unavailable`
with `ERR_AUTOJS6_WORKER_DISABLED`.

## Promotion Requirements

Before P14-29 or P14-30 can claim runnable worker support, a future target must
archive all of the following:

- native preflight reports `worker.preflight.status=available` on the selected
  promotion ABI/device set;
- runnable worker smoke covers basic Worker lifecycle, message passing,
  termination, timeout, exception recovery, parent cleanup, nested-worker
  denial, and packaged behavior;
- resource/RSS stress covers repeated create/terminate, WorkerPool
  failure/cancel/timeout/transfer, fd/thread/RSS counters, cleanup, and crash
  recovery;
- release policy decides whether the surface belongs to `desktop_compat_opt_in`
  or stays confined to `debug_unsafe_lab`.

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-worker-native-availability.js`
- Gradle gate: `:app:verifyNodePhase14WorkerNativeAvailability`
- Generated report: `build/reports/nodejs/phase14-worker-native-availability.json`
