# Worker Threads Experimental Evaluation

Date: 2026-06-16

Latest v1.1 recheck: 2026-06-17

Latest S7-24 bootstrap recheck: 2026-06-19

Latest P12-22 compute/process-worker decision: deferred for Phase 12

Device: Sony XQ-AT72 (`QV710AF65F`), Android 12

Branch/package identity: initial GA evaluation on `feature/nodejs-runtime`, `org.autojs.autojs6.nodejs`; v1.1 recheck on `feature/nodejs-runtime-v1.1`.

## Recommendation

Do not promote or expose `worker_threads` as a runnable experiment for Node Profile v1 GA or v1.1 partial support.

Keep `worker_threads` disabled by default, and document the current experimental path as blocked in this Embedded Node
build. The JavaScript/Kotlin gate behaves correctly, but enabling the experimental flag reaches a native availability
failure before any Worker can be created:

```text
ERR_AUTOJS6_WORKER_DISABLED
AutoJs6 experimental worker_threads is unavailable in this Embedded Node build.
```

Until a libnode build exposes a usable native `worker_threads` module and the focused worker suites pass on the Sony gate
device, the release contract should treat workers as unavailable, not as a supported or advertised experimental runtime
surface.

P12-22 keeps the same conclusion for Rhino `threads` replacement: AutoJs6 does
not add a Phase 12 compute/process-worker runtime API, and any future
separately named API must pass worker/process permissions, scoped script path,
message-size, cleanup, nested worker denial, bridge module isolation, resource
stress, packaged behavior, and `second_execution_slot` gates.

S7-24 does not change that release decision. It adds an explicit
`NodeWorkerThreadsBootstrapInstrumentationTest` and a native-availability
preflight to `verifyNodeWorkerGate`: normal builds verify default denial and
shadowing protection, while experimental builds skip runnable creation/stress
promotion tests when the preflight reports `ERR_AUTOJS6_WORKER_DISABLED`.

For Task 170, the v1.1 target remains blocked rather than downgraded to a same-thread fallback. Worker semantics require
real native Worker isolation, and packaged workers must not silently fall back to Rhino, parent-thread execution, or
unrestricted Node behavior.

## Commands Run

Default design gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest"
```

Result: passed, 1/1 tests.

v1.1 recheck on 2026-06-17:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result: passed, 1/1 tests.

Focused experimental execution with the Gradle flag enabled:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.worker_threads.experimental=true" "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest#experimentalWorkerThreadsRunPureJsWorkersOnly"
```

Result: failed, 1/1 tests. The request reached `embedded_script.request.worker_threads_experimental_enabled=true`, then
failed with `ERR_AUTOJS6_WORKER_DISABLED` because native `worker_threads` was unavailable.

v1.1 recheck on 2026-06-17:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result: failed, 1/1 tests. The pure-JS worker test still reaches
`embedded_script.request.worker_threads_experimental_enabled=true`, then fails with `ERR_AUTOJS6_WORKER_DISABLED` because
native `worker_threads` is unavailable in this Embedded Node build.

P13-34 recheck on 2026-06-28:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.worker_threads.experimental=true" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Result: passed on QV710AF65F / XQ-AT72 / Android 12 with 16 selected tests,
0 failures, and 14 native-unavailable skips. The skipped runnable,
resource-stress, and packaged worker paths reported
`worker.preflight.status=unavailable` and `ERR_AUTOJS6_WORKER_DISABLED`.

Earlier exploratory runs also checked the full worker group:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest,org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest,org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest"
```

Result: failed, 5/7 tests. The default-off design/default tests passed; all worker creation/stress paths failed with the
same native availability error.

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.worker_threads.experimental=true" "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeWorkerThreadsDesignGateInstrumentationTest,org.autojs.autojs.engine.NodeWorkerThreadsExperimentalInstrumentationTest,org.autojs.autojs.engine.NodeWorkerThreadsResourceStressInstrumentationTest"
```

Result: failed, 7/7 tests. The default-off assertions failed by construction because the build flag changed
`BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED` to `true`; all worker creation/stress paths still failed with the
same native availability error.

## Supported Cases

The default-disabled gate is supported and passed on the Sony device:

- `BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED=false` in normal builds
- Kotlin capability registry reports the flag disabled by default
- JavaScript profile reports `workerThreads=false`
- `require("worker_threads")` rejects
- `require("node:worker_threads")` rejects
- `require.resolve("worker_threads")` rejects before local package shadowing
- a local `node_modules/worker_threads` package cannot override the builtin denial
- S7-24 default `verifyNodeWorkerGate` passes with bootstrap/design coverage
- S7-24 experimental `verifyNodeWorkerGate` passes with runnable/stress
  promotion tests skipped because native workers are unavailable
- P13-34 QV710AF65F experimental `verifyNodeWorkerGate` passes with the same
  native-unavailable preflight and no runnable worker promotion

No worker execution case was proven supported in this Embedded Node build.

## Blocked Cases

The following intended experimental cases could not be validated because execution fails before Worker construction:

- local CommonJS worker loading
- `workerData`
- `parentPort.postMessage(...)`
- `worker.postMessage(...)`
- `worker.terminate()`
- max-worker rejection
- scoped worker path escape denial
- worker diagnostics counters
- worker-local denial of AutoJs bridge modules
- worker-local denial of `fs`
- worker-local denial of raw `net`
- nested worker denial inside worker scope

## Resource Stress Status

The Task 122 stress requirements remain blocked:

- sequential worker create/terminate cycles: not validated
- worker exception recovery: not validated
- worker terminate behavior: not validated
- parent stop/completion cleanup: not validated
- memory and thread count bounds: not validated

`NodeWorkerThreadsResourceStressInstrumentationTest` contains the right gates for these behaviors, but every method reaches
the same native availability failure before the stress condition begins.

## Bridge Isolation Status

The default profile still prevents worker access to AutoJs bridge authority because workers cannot be created and
`worker_threads` is denied.

The stronger worker-local isolation test, where a running worker attempts to require bridge modules, could not execute in
this build. It must pass before any future worker experiment can be shown to users.

## Release Decision

Node Profile v1 GA and v1.1 should document `worker_threads` as:

- disabled by default
- unavailable in the current Embedded Node build even when the experimental flag is enabled
- not part of the stable Node Profile v1 surface
- not a runnable v1.1 partial capability until native availability changes
- not an advertised runnable experiment for GA or v1.1

The next unblock gate should:

- make native `process.getBuiltinModule("worker_threads")` availability explicit in the worker preflight
- provide or select a libnode build with usable native `worker_threads`
- rerun `NodeWorkerThreadsExperimentalInstrumentationTest` on Sony
- rerun `NodeWorkerThreadsResourceStressInstrumentationTest` on Sony
- keep worker-local AutoJs bridge denial mandatory
- keep packaged worker source graphs out of scope until the live worker path passes
