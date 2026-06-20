# S8-44 Worker Partial Promotion Recheck

Status: rechecked, not promoted.

## Decision

S8-44 cannot promote `worker_threads` from disabled to project opt-in partial in
the current v1.1 branch. The focused worker gate still reports native Worker
execution as unavailable, so runnable worker, stress, and packaged worker smoke
coverage remains skipped by design.

The release-facing policy is unchanged:

- `autojs.nodejs.worker_threads.experimental=false` by default;
- no project or packaged metadata opt-in;
- no user-visible worker runtime surface;
- experimental worker code remains an internal unblock/debug gate only.

## Evidence

Default Sony worker gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result:

```text
3 tests passed, 0 failed
```

Experimental Sony worker gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.worker_threads.experimental=true" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result:

```text
30 worker-gate tests selected
0 failed
11 skipped
```

Skipped coverage included runnable worker creation, CJS/ESM worker entries,
MessageChannel/transferList, WorkerPool, worker resource stress, and the
packaged `.mjs` worker smoke. These skips are expected while the native
availability preflight reports the worker surface unavailable.

## Promotion Blockers

The following S8-44 promotion requirements remain unmet:

- native `worker_threads` availability in the embedded libnode build;
- successful pure-JS Worker creation;
- `workerData`, `parentPort`, `terminate()`, and entry-graph coverage;
- SharedArrayBuffer/Atomics and BroadcastChannel safety review;
- environmentData, heap statistics, and resource-limit proof;
- worker stress without skipped runnable cases;
- packaged worker smoke that reaches APK construction and execution.

## Next Gate

Do not revisit promotion until a candidate libnode exposes usable native
`worker_threads`. At that point, rerun:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.worker_threads.experimental=true" "-Pautojs.nodejs.test.deviceProfile=sony"
```

The gate must complete runnable and stress cases without native-availability
skips before `worker_threads` can become project opt-in partial.
