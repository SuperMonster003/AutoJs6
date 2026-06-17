# Worker Threads Design Gate v1

Status: experimental design gate, blocked for Node Profile v1.1 runnable execution. `autojs.nodejs.worker_threads.experimental=false` is the default, and `worker_threads` remains disabled in normal AutoJs6 Safe Node Profile runs. Task 122 found that the current Embedded Node build does not expose a usable native `worker_threads` module even when the experimental flag is enabled. Task 170 rechecked the v1.1 partial target on 2026-06-17 and kept the runnable worker surface blocked for the same native availability reason.

## Goal

AutoJs6 keeps an experimental worker_threads design for bounded pure JavaScript computation, but the default runtime must not expose desktop Node worker authority.

Workers are high risk in AutoJs6 because they multiply:

- V8 isolates and event loops
- native handle lifetime
- memory pressure in the `:nodejs_embedded` process
- callback and shutdown paths
- access-control surfaces for AutoJs bridge modules
- packaged APK compatibility state

Task 42 froze the boundary. Task 63 added a request-local experimental path intended to run local CommonJS worker scripts with a smaller capability set than the parent script. Task 122 blocked publishing that path as runnable for GA because native `worker_threads` is unavailable in the evaluated Embedded Node build. Task 170 kept the same decision for v1.1: the default denial and design gates remain valid, but runnable worker partial support cannot be claimed until a libnode build exposes native workers and the focused execution and stress suites pass.

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
- `require("node:worker_threads")` rejects with the stable disabled-builtin error shape
- `require.resolve("worker_threads")` rejects before resolving a local shadow package
- `require("autojs6:profile").workerThreads === false`
- `require("autojs6:profile").highRiskCapabilities.workerThreads === false`
- raw Android, AutoJs, bridge, Context, UI, accessibility, image, shell, and process authority remains unavailable to workers because workers do not exist

`node_modules/worker_threads` must not shadow the disabled builtin.

## Intended Experimental Scope

When the request-local experimental flag is enabled and the Embedded Node build exposes native `worker_threads`, the first allowed scope remains:

- pure JavaScript CPU work
- JSON-serializable `workerData`
- `parentPort.postMessage(...)`
- `worker.postMessage(...)`
- `worker.terminate()`
- safe pure-JS builtins such as `buffer`, `path`, `util`, `events`, `assert`, `querystring`, `string_decoder`, `timers`, and `url`
- explicit startup timeout and worker count limits
- parent-owned lifecycle and cancellation through `terminate()`

Workers are loaded by the parent scoped resolver and executed from a generated native Worker `eval` wrapper. Worker scripts do not get the parent AutoJs6 loader or bridge.

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

`fs` remains denied in the first worker version. Worker source loading is parent-owned and scoped to the working directory.

## Limits

Current defaults:

- `maxWorkers`: 1 per script execution
- default worker startup timeout: 5000 ms
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
- diagnostics for active, max active, created, terminated, startup timeout, and policy rejection counts

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

## Test Gate

Current gates:

- `NodeWorkerThreadsDesignGateInstrumentationTest` verifies the BuildConfig flag, Kotlin registry, JavaScript profile, bare and `node:` require denial, `require.resolve` denial, and shadowing protection.
- `NodeWorkerThreadsExperimentalInstrumentationTest` verifies the default disabled path and is the promotion gate for request-local experimental enablement, local worker script loading, `workerData`, `parentPort.postMessage`, `terminate()`, max worker rejection, scoped path escape denial, Worker diagnostics, and denial of AutoJs bridge modules, `fs`, raw `net`, and nested workers inside worker scope.
- `NodeWorkerThreadsResourceStressInstrumentationTest` is the promotion gate for sequential create/terminate behavior, worker exception recovery, infinite-loop timeout recovery, parent-completion cleanup, cleanup diagnostics, and coarse RSS/thread snapshots.
- `NodeSecurityRegressionInstrumentationTest` keeps `worker_threads` in the dangerous builtin denial corpus.

Current evaluation: [../reports/WORKER_EXPERIMENTAL_EVALUATION.md](../reports/WORKER_EXPERIMENTAL_EVALUATION.md). The default design gate passes, but worker execution/stress suites are blocked by native module availability. The 2026-06-17 v1.1 recheck confirmed that Task 170 cannot graduate workers to runnable partial support without changing the Embedded Node native build.

Follow-up gates should add:

- packaged APK worker source graph and verifier behavior
- stronger native handle/thread leak diagnostics once worker support graduates beyond experimental mode

## Non-Goals

The MVP does not enable:

- nested workers
- shared memory or `SharedArrayBuffer`
- `MessageChannel` compatibility beyond what already exists elsewhere
- AutoJs bridge access from workers
- Android Context access from workers
- packaged worker source graphs
- worker access to `fs`
- worker access to raw network
- worker access to full parent Safe Node builtins
- unrestricted service-destroy worker cleanup guarantees beyond the current process-isolation and focused timeout recovery gates
