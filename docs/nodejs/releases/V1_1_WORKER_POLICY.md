# Node Profile v1.1 Worker Threads Policy

Date: 2026-06-17

Latest S7-27 capability-isolation gate update: 2026-06-19

Branch: `feature/nodejs-runtime-v1.1`

Device: Sony XQ-AT72 `QV710AF65F`, Android 12

Package identity: production, `org.autojs.autojs6`

## Recommendation

Keep `worker_threads` disabled by default and do not expose it as a release
opt-in experiment for Node Profile v1.1.

The default-denial and design-gate behavior remains healthy under production
identity, but the explicitly enabled worker path is still blocked by native
availability in the current Embedded Node build:

```text
ERR_AUTOJS6_WORKER_DISABLED
AutoJs6 experimental worker_threads is unavailable in this Embedded Node build.
```

This means `worker_threads` should remain an internal unblock/debug gate, not a
user-visible experimental runtime surface. A future promotion can reconsider the
policy only after a libnode build exposes usable native `worker_threads` and the
worker creation, isolation, lifecycle, and resource stress suites pass on the
Sony gate device.

S7-24 adds `NodeWorkerThreadsBootstrapInstrumentationTest` and changes the
focused worker gate contract: normal builds verify default denial and
request-local native availability preflight; experimental builds skip runnable
creation/stress promotion tests when that preflight reports
`ERR_AUTOJS6_WORKER_DISABLED`, instead of treating a native-unavailable build as
failed runnable support.

S7-26 extends the native-available promotion target with limited
`MessageChannel`/`MessagePort`, ArrayBuffer transfer detachment, transfer-list
validation, maximum message-size and queued-message limits, DataCloneError
compatible failures, and parent-side MessagePort cleanup diagnostics. This does
not change the release recommendation while the current Embedded Node build
still lacks usable native workers.

S7-27 defines a separate worker capability profile. Native-available worker
tests now expect scoped `fs`/`fs.promises` read/write helpers rooted at the
worker working directory, plus denial of AutoJs bridge modules, `toast`,
`accessibility`, `image`, `shell`, Java interop, inspector, raw network, and
nested workers. Workers do not inherit main-script capabilities.

Normal builds must continue to keep:

```properties
autojs.nodejs.worker_threads.experimental=false
```

## Commands Run

Default design and denial gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest,org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest#workerThreadsRemainDisabledByDefault"
```

Result:

```text
2 tests, 0 failures, 0 errors, 0 skipped
```

Focused experimental Worker creation gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.worker_threads.experimental=true" `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest#experimentalWorkerThreadsRunPureJsWorkersOnly"
```

Result:

```text
1 test, 1 failure, 0 errors, 0 skipped
errorCode=ERR_AUTOJS6_WORKER_DISABLED
embedded_script.request.worker_threads_experimental_enabled=true
embedded_script.worker_threads_enabled=true
embedded_script.worker_active_count=0
embedded_script.worker_created_count=0
```

Experimental resource stress gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.worker_threads.experimental=true" `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest"
```

Result:

```text
4 tests, 4 failures, 0 errors, 0 skipped
all failures: ERR_AUTOJS6_WORKER_DISABLED
```

The failed stress cases were:

- `createAndTerminateWorkersSequentiallyDoesNotGrowResourceCounters`
- `workerExceptionDoesNotPoisonNextRun`
- `infiniteLoopWorkerTimeoutDoesNotPoisonNextRun`
- `pendingWorkerMessageIsRejectedDuringParentCompletionCleanup`

## Evidence Matrix

| Gate | Status | Notes |
| --- | --- | --- |
| S7-24 default `verifyNodeWorkerGate` | Pass | Sony default gate ran bootstrap/design coverage, 3 tests, 0 failures. |
| S7-24 experimental `verifyNodeWorkerGate` | Pass with skips | Sony experimental gate ran bootstrap/preflight and skipped runnable/stress promotion tests because native workers remain unavailable, 0 failures. |
| S7-25 CJS/ESM worker entry gate | Prepared, skipped on Sony | Worker loader now has promotion coverage for `worker.cjs`, `worker.mjs`, `type=module` `.js`, local CJS/JSON and partial-ESM dependencies, allowed builtins, path escape denial, raw network denial, bridge denial, and native addon denial. Sony still skips runnable smoke because native workers remain unavailable. |
| S7-26 MessageChannel/transferList gate | Prepared, skipped on Sony | Limited `MessageChannel`, `MessagePort`, ArrayBuffer transfer detachment, typed array delivery, structured-clone-safe value checks, DataCloneError-compatible failures, message-size/queue limits, and MessagePort cleanup counters are covered when the native preflight is available. Sony still skips runnable smoke because native workers remain unavailable. |
| S7-27 worker capability isolation gate | Prepared, skipped on Sony | Worker profile now separates scoped worker `fs`/`fs.promises` from main-script capabilities and verifies denial of AutoJs bridge modules, `toast`, `accessibility`, `image`, `shell`, Java interop, inspector, raw network, and nested workers. Sony still skips runnable smoke because native workers remain unavailable. |
| default build flag | Pass | `BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED=false` in normal builds. |
| capability registry default | Pass | Kotlin and JavaScript profiles report worker threads disabled. |
| bare `require("worker_threads")` denial | Pass | Default path rejects before local shadowing. |
| `require("node:worker_threads")` denial | Pass | `node:` alias is denied in the default profile. |
| `require.resolve("worker_threads")` denial | Pass | Resolution rejects before local `node_modules` shadowing. |
| profile immutability | Pass | `profile.experimentalFlags` remains frozen. |
| experimental flag delivery | Pass | Native diagnostics report the request flag and worker gate as enabled. |
| native Worker availability | Blocked | Bootstrap preflight reports `ERR_AUTOJS6_WORKER_DISABLED` and zero active workers. |
| pure JS Worker creation | Blocked | No Worker is constructed; created count remains `0`. |
| worker data and parentPort messaging | Blocked | Cannot run because Worker creation fails. |
| worker-local AutoJs bridge denial | Prepared, skipped on Sony | Native-available gate denies bridge modules and user-facing AutoJs modules in worker scope. |
| worker-local Android Context / Java denial | Prepared, skipped on Sony | Worker profile denies Java interop and exposes no Android Context. |
| worker scoped fs escape denial | Prepared, skipped on Sony | Worker `fs`/`fs.promises` wrappers validate lexical paths and real paths against `workingDirectory`. |
| nested worker denial | Prepared, skipped on Sony | Worker-local nested `Worker` construction is denied by the limited module when native workers are available. |
| parent stop/completion cleanup | Blocked | Stress cleanup tests fail before worker creation. |
| sequential create/terminate stress | Blocked | 50-cycle stress cannot start. |
| exception and timeout recovery | Blocked | Recovery tests fail before worker creation. |

## Policy Decision

For Node Profile v1.1:

- keep `worker_threads` outside the stable surface;
- keep `autojs.nodejs.worker_threads.experimental=false` by default;
- do not expose a release opt-in switch to users;
- keep the experimental flag available only as an internal unblock/debug gate;
- do not advertise pure-JS workers until native availability and resource stress
  gates pass;
- do not add packaged worker graph support yet.

## Required Promotion Evidence

Before reconsidering release opt-in status, add or prove:

- a libnode build with native `process.getBuiltinModule("worker_threads")`
  availability;
- focused Worker creation success for local CommonJS and partial-ESM worker
  scripts;
- `worker.cjs`, `worker.mjs`, and `package.json` `type=module` `.js` entry
  smoke on a native-worker-capable Embedded Node build;
- local worker dependency smoke for CJS/JSON and partial ESM graphs;
- `workerData`, `parentPort.postMessage`, `worker.postMessage`, and
  `worker.terminate()` behavior;
- `MessageChannel`, `MessagePort`, ArrayBuffer transfer detachment,
  transfer-list validation, DataCloneError-compatible message failures, and
  MessagePort cleanup counters;
- worker-local scoped `fs`/`fs.promises` read/write behavior and path escape
  denial;
- worker-local denial of AutoJs bridge modules, Android/Java interop, raw
  network, `shell`, `child_process`, native addons, `vm`, inspector, and nested
  workers;
- parent stop and script completion cleanup for active workers;
- bounded thread/RSS growth in repeated create/terminate stress;
- packaged APK policy for worker source graphs, if packaged workers are ever in
  scope.
