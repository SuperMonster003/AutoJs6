# Node Profile v1.1 Worker Threads Policy

Date: 2026-06-17

Latest S7-24 bootstrap recheck: 2026-06-19

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
| worker-local AutoJs bridge denial | Blocked | Intended test cannot execute inside a running worker. |
| worker-local Android Context denial | Blocked by design | No Context is exposed, but the stronger running-worker proof cannot execute yet. |
| nested worker denial | Blocked | Intended worker-local nested Worker denial cannot execute. |
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
- focused Worker creation success for local CommonJS worker scripts;
- `workerData`, `parentPort.postMessage`, `worker.postMessage`, and
  `worker.terminate()` behavior;
- worker-local denial of AutoJs bridge modules, Android/Java interop, `fs`, raw
  network, `shell`, `child_process`, native addons, `vm`, inspector, and nested
  workers;
- parent stop and script completion cleanup for active workers;
- bounded thread/RSS growth in repeated create/terminate stress;
- packaged APK policy for worker source graphs, if packaged workers are ever in
  scope.
