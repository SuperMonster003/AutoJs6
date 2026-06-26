# Worker Threads Design Gate v1

Status: experimental design gate, blocked for Node Profile v1.1 runnable execution. `autojs.nodejs.worker_threads.experimental=false` is the default, and runnable `worker_threads` authority remains disabled in normal AutoJs6 Safe Node Profile runs. The default profile exposes only a `node:worker_threads` shape facade for npm feature detection. Task 122 found that the current Embedded Node build does not expose a usable native `worker_threads` module even when the experimental flag is enabled. Task 170 rechecked the v1.1 partial target on 2026-06-17 and kept the runnable worker surface blocked for the same native availability reason. S7-24 adds a focused bootstrap/preflight gate so default denial and experimental native availability are verified without claiming runnable worker support. S7-26 prepares limited MessageChannel/transferList coverage for native-worker-capable builds. S7-29 adds stress and packaged `.mjs` worker smoke coverage to the same internal gate, but it does not promote workers to a user-visible release surface. P12-20 rechecked this as part of Rhino `threads` migration and kept worker_threads out of the default migration path. P12-22 defers a compute/process-worker replacement API and requires separate naming plus permissions, scoped path, message, cleanup, nested-worker denial, bridge-isolation, stress, packaged, and `second_execution_slot` gates before any future script-author surface.

## Goal

AutoJs6 keeps an experimental worker_threads design for bounded pure JavaScript computation, but the default runtime must not expose desktop Node worker authority.

Workers are high risk in AutoJs6 because they multiply:

- V8 isolates and event loops
- native handle lifetime
- memory pressure in the `:nodejs_embedded` process
- callback and shutdown paths
- access-control surfaces for AutoJs bridge modules
- packaged APK compatibility state

Task 42 froze the boundary. Task 63 added a request-local experimental path intended to run local CommonJS worker scripts with a smaller capability set than the parent script. Task 122 blocked publishing that path as runnable for GA because native `worker_threads` is unavailable in the evaluated Embedded Node build. Task 170 kept the same decision for v1.1: the default denial and design gates remain valid, but runnable worker partial support cannot be claimed until a libnode build exposes native workers and the focused execution and stress suites pass. S7-24 keeps that decision and adds an explicit native-availability preflight in `verifyNodeWorkerGate`. S7-26 extends the native-available target with bounded cross-thread messaging and port cleanup proof.

## Feature Flag

The gate flag is:

```properties
autojs.nodejs.worker_threads.experimental=false
```

Current wiring:

- Gradle property default: `false`
- BuildConfig field: `NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED`
- Kotlin capability registry: `NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY`
- JavaScript profile snapshot: `require("autojs6:profile").experimentalFlags.workerThreads === false`

The flag remains off by default. Instrumentation can enable it per embedded script request for focused experimental coverage.

## Current Default Behavior

With the flag off:

- `require("worker_threads")` rejects with the stable disabled-builtin error shape
- `require("node:worker_threads")` exposes a shape-only facade for npm feature
  detection; `Worker` construction returns an inert disabled worker object, but
  `postMessage()` and real worker execution reject with
  `ERR_AUTOJS6_WORKER_DISABLED`
- `require.resolve("worker_threads")` rejects before resolving a local shadow package
- `require("autojs6:profile").workerThreads === false`
- `require("autojs6:profile").highRiskCapabilities.workerThreads === false`
- raw Android, AutoJs, bridge, Context, UI, accessibility, image, shell, and process authority remains unavailable to workers because workers do not exist

`node_modules/worker_threads` must not shadow the disabled builtin.

When the request-local experimental flag is enabled but the current libnode
build still does not expose native `worker_threads`, both `require("worker_threads")`
and `require("node:worker_threads")` return the same shape-only facade with
`nativeAvailable=false`. This keeps package initialization paths such as
`jsdom` top-level feature detection moving, while inert `Worker` instances,
`postMessage()`, and real execution remain disabled with
`ERR_AUTOJS6_WORKER_DISABLED`.

## Intended Experimental Scope

When the request-local experimental flag is enabled and the Embedded Node build exposes native `worker_threads`, the first allowed scope remains:

- pure JavaScript CPU work
- JSON-serializable `workerData`
- `parentPort.postMessage(...)`
- `worker.postMessage(value, transferList?)`
- limited `MessageChannel` and `MessagePort`
- `MessagePort.postMessage(value, transferList?)`
- ArrayBuffer transfer detachment, typed array delivery, and structured-clone-safe values
- DataCloneError-compatible failures for unsupported clone and transfer values
- scoped worker `fs` and `fs/promises` read/write helpers rooted at `workingDirectory`
- AutoJs6 `WorkerPool` extension for explicit task-message workers
- `worker.terminate()`
- `threadId`
- safe pure-JS builtins such as `buffer`, `path`, `util`, `events`, `assert`, `querystring`, `string_decoder`, `timers`, and `url`
- explicit startup timeout and worker count limits
- parent-owned lifecycle and cancellation through `terminate()`

Workers are loaded by the parent scoped resolver and executed from a generated native Worker `eval` wrapper. Worker scripts do not get the parent AutoJs6 loader or bridge. S7-27 adds a separate worker profile: workers get only scoped filesystem read/write wrappers and do not inherit main-script bridge, network, shell, Java, or inspector authority.

S7-28 adds a controlled WorkerPool extension. It is intentionally not presented
as a Node standard API. Pool workers receive `{ __autojs6PoolTaskId, value }`
messages and must reply with `{ __autojs6PoolTaskId, value }` or
`{ __autojs6PoolTaskId, error }`. The pool owns queueing, cancellation, task
timeouts, idle termination, and shutdown.

The first version does not allow workers to spawn more workers.

## Forbidden Worker Access

Workers must not access:

- AutoJs API bridge modules
- `autojs6:bridge`
- Android `Context`
- Accessibility service objects or selectors backed by live Android state
- MediaProjection or image handles
- UI/dialog capability providers
- `shell`
- `engines`
- clipboard/device privileged providers
- native addons
- child processes
- raw network modules
- inspector/debugger server
- unrestricted filesystem paths

Bridge modules are intentionally parent-thread capabilities. A worker may send pure data to the parent, and the parent may choose to call bridge APIs.

## Builtin Policy

Worker builtins start from a separate worker-safe allowlist, not the parent profile by default.

Current first allowlist:

- `assert`
- `buffer`
- `events`
- `fs` as an AutoJs6 scoped worker wrapper
- `fs/promises` as an AutoJs6 scoped worker wrapper
- `path`
- `querystring`
- `string_decoder`
- `timers`
- `url`
- `util`

Denied in workers:

- `autojs6:bridge`
- all AutoJs bridge modules
- `child_process`
- `cluster`
- `dgram`
- `dns`
- `http`
- `https`
- `inspector`
- `module` loader mutation APIs
- `net`
- `tls`
- `vm`
- `worker_threads` nested spawning
- native `.node` addons

Worker `fs` is not native Node `fs`. It exposes a minimal read/write subset
rooted at `workingDirectory`; every path is checked lexically and, for existing
paths or parents, by realpath before the native operation runs. Worker source
loading remains parent-owned and scoped to the working directory.

## Limits

Current defaults:

- `maxWorkers`: 1 per script execution
- default worker startup timeout: 5000 ms
- maximum message size: 65536 bytes per worker/port message estimate
- maximum queued messages: 32 per parent/port sender before the next event-loop turn
- worker filesystem profile: read/write limited to `workingDirectory`; no inherited main-thread filesystem expansion
- worker pool default policy: `minWorkers=0`, `maxWorkers=1`, `idleTimeoutMs=1000`, `taskTimeoutMs=5000`, `queueLimit=8`
- worker `resourceLimits` caps: old generation 64 MB, young generation 16 MB, code range 32 MB, stack 8 MB
- default worker run timeout: parent script timeout remains the outer guard; per-worker execution timeout is Task 64 work
- per-worker memory budget: design before implementation; do not rely on V8 defaults alone
- maximum worker source size: reuse module source budgets unless a lower worker-specific budget is set

Workers must count as pending lifecycle work. The parent script cannot complete successfully while a worker is still running unless that worker has been explicitly detached by a separate, reviewed policy. Detached workers are out of scope for the MVP.

## Lifecycle Policy

Current MVP defines:

- creation from the Node event loop thread
- callback delivery through Node `worker_threads` events
- explicit parent-side `worker.terminate()`
- uncaught worker exception rejects or emits through the parent according to documented Node-compatible behavior
- worker termination is idempotent
- diagnostics for active, max active, created, terminated, startup timeout, policy rejection, MessagePort lifecycle, and message rejection counts

Task 64 hardens the MVP with:

- a per-execution worker registry in the JavaScript bootstrap
- automatic best-effort termination of active workers during parent success or failure cleanup
- cleanup diagnostics for requested worker termination, termination errors, and rejected pending worker-message listeners
- pending-worker fields in the Embedded Node result envelope
- focused instrumentation for 50 sequential worker create/terminate cycles, worker exceptions, infinite-loop worker timeout recovery, and parent-completion cleanup

No callback from a worker may re-enter a destroyed parent environment.

## Packaged APK Policy

Packaged worker support must be opt-in and versioned.

Before enabling packaged workers, define:

- whether worker entry scripts are stored as moduleSources, assets, or generated blobs
- encrypted worker source graph format
- sourceName and stack trace mapping
- package.json resolution rules inside workers
- ABI/native library requirements
- verifier output for worker opt-in
- failure behavior for older inrt templates

Packaged workers must not silently fall back to Rhino, parent-thread execution, or unrestricted Node behavior.

## Error Shape

Current default denial:

```text
code=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED
autojs6Code=ERR_AUTOJS6_BUILTIN_DISABLED
legacyCode=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED
```

Experimental worker-specific errors:

- `ERR_AUTOJS6_WORKER_DISABLED`
- `ERR_AUTOJS6_WORKER_LIMIT_EXCEEDED`
- `ERR_AUTOJS6_WORKER_BRIDGE_DENIED`
- `ERR_AUTOJS6_WORKER_TIMEOUT`
- `ERR_AUTOJS6_WORKER_TERMINATED`
- `ERR_AUTOJS6_WORKER_POOL_CLOSED`
- `ERR_AUTOJS6_WORKER_POOL_QUEUE_FULL`
- `ERR_AUTOJS6_WORKER_POOL_TASK_TIMEOUT`
- `ERR_AUTOJS6_WORKER_POOL_TASK_CANCELLED`
- `ERR_AUTOJS6_WORKER_POOL_TASK_FAILED`
- `DataCloneError` for clone/transfer-list/message-budget failures, with `autojs6Code=ERR_AUTOJS6_WORKER_BRIDGE_DENIED` when the error is raised by the AutoJs6 worker wrapper

## Test Gate

Current gates:

- `NodeWorkerThreadsBootstrapInstrumentationTest` verifies request-local default
  denial before shadowing and experimental native-availability preflight. The
  preflight requires either a usable limited `worker_threads` surface
  (`Worker`, `WorkerPool`, `MessageChannel`, `MessagePort`, `isMainThread`, `parentPort`,
  `workerData`, `threadId`, policy, and diagnostics) or stable
  `ERR_AUTOJS6_WORKER_DISABLED` diagnostics with zero active workers.
- `NodeWorkerThreadsDesignGateInstrumentationTest` verifies the BuildConfig flag, Kotlin registry, JavaScript profile, bare and `node:` require denial, `require.resolve` denial, and shadowing protection in normal builds.
- `NodeWorkerThreadsExperimentalInstrumentationTest` verifies the default disabled path and is the promotion gate for request-local experimental enablement, local worker script loading, `workerData`, `parentPort.postMessage`, `terminate()`, max worker rejection, scoped path escape denial, Worker diagnostics, limited `MessageChannel`/`MessagePort`, ArrayBuffer transfer detachment, transfer-list validation, message size/queue limits, DataCloneError-compatible failures, MessagePort cleanup counters, scoped worker `fs`/`fs.promises`, controlled `WorkerPool` reuse/queue/cancel/timeout/shutdown behavior, and denial of AutoJs bridge modules, `toast`, `accessibility`, `image`, `shell`, `java`, `inspector`, raw `net`, and nested workers inside worker scope. Runnable coverage is skipped when the preflight reports native workers unavailable.
- `NodeWorkerThreadsResourceStressInstrumentationTest` is the promotion gate for 100 sequential create/terminate jobs, WorkerPool 100-job reuse, worker and pool exception recovery, queued cancellation, ArrayBuffer transfer, infinite-loop and pool task timeout recovery, parent-completion cleanup, cleanup diagnostics, and coarse RSS/thread snapshots. Stress coverage is skipped when the preflight reports native workers unavailable.
- `EmbeddedNodePackagedApkSmokeTest#installAndRunWorkerThreadsEsmEmbeddedNodePackagedApk` is selected by the experimental `verifyNodeWorkerGate` as the packaged `.mjs` worker smoke. It skips before APK construction when native worker preflight is unavailable.
- `NodeSecurityRegressionInstrumentationTest` keeps `worker_threads` in the dangerous builtin denial corpus.

Current evaluation: [../reports/WORKER_EXPERIMENTAL_EVALUATION.md](../reports/WORKER_EXPERIMENTAL_EVALUATION.md). The default design gate passes, but worker execution/stress suites are blocked by native module availability. The 2026-06-17 v1.1 recheck confirmed that Task 170 cannot graduate workers to runnable partial support without changing the Embedded Node native build. The 2026-06-19 S7-24 recheck added bootstrap/preflight coverage and verified the experimental gate fails closed with skips, not false runnable support, on the current native build.

P12-20 records the same maturity state for Rhino migration in
[../reports/phase12/RHINO_THREADS_MATURITY_REVIEW.md](../reports/phase12/RHINO_THREADS_MATURITY_REVIEW.md):
`worker_threads` is not a default replacement for Rhino `threads`, the
experimental path remains native-availability gated, and future compute/process
workers must be separately named and must not claim same-runtime Rhino parity.

P12-22 records the current compute/process-worker decision in
[../reports/phase12/RHINO_COMPUTE_PROCESS_WORKER_DECISION.md](../reports/phase12/RHINO_COMPUTE_PROCESS_WORKER_DECISION.md):
Phase 12 does not add a new worker/process runtime API. `worker_computation`
remains reserved, `second_execution_slot` remains open, and any future
script-author API must pass worker/process permissions, scoped script path,
message-size, cleanup, nested worker denial, bridge module isolation, resource
stress, packaged behavior, and final blocker gates before promotion.

Follow-up gates should add stronger native handle/thread leak diagnostics once
worker support graduates beyond experimental mode.

## Non-Goals

The MVP does not enable:

- nested workers
- shared memory or `SharedArrayBuffer`
- unrestricted native-handle transfer or `MessageChannel` behavior beyond the bounded S7-26 native-available gate
- AutoJs bridge access from workers
- Android Context access from workers
- packaged worker source graphs outside the bounded S7-29 experimental smoke
- worker access to unrestricted native `fs`
- unbounded worker pools or background detached pool workers
- worker access to raw network
- worker access to full parent Safe Node builtins
- unrestricted service-destroy worker cleanup guarantees beyond the current process-isolation and focused timeout recovery gates
