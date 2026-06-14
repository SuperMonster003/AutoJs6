# Worker Threads Design Gate v1

Status: design gate only. `autojs.nodejs.worker_threads.experimental=false` is the default, and `worker_threads` remains disabled in AutoJs6 Safe Node Profile.

## Goal

AutoJs6 may eventually support worker threads for bounded pure JavaScript computation, but the default runtime must not expose desktop Node worker authority.

Workers are high risk in AutoJs6 because they multiply:

- V8 isolates and event loops
- native handle lifetime
- memory pressure in the `:nodejs_embedded` process
- callback and shutdown paths
- access-control surfaces for AutoJs bridge modules
- packaged APK compatibility state

Task 42 freezes the boundary. It does not create workers, load worker scripts, or enable `require("worker_threads")`.

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

The flag is a design anchor only. A later implementation task must still add a separate runtime gate, tests, lifecycle ownership, and rollback path before any worker can run.

## Current Default Behavior

With the flag off:

- `require("worker_threads")` rejects with the stable disabled-builtin error shape
- `require("node:worker_threads")` rejects with the stable disabled-builtin error shape
- `require.resolve("worker_threads")` rejects before resolving a local shadow package
- `require("autojs6:profile").workerThreads === false`
- `require("autojs6:profile").highRiskCapabilities.workerThreads === false`
- raw Android, AutoJs, bridge, Context, UI, accessibility, image, shell, and process authority remains unavailable to workers because workers do not exist

`node_modules/worker_threads` must not shadow the disabled builtin.

## Future Worker Scope

If worker support is ever enabled, the first allowed scope should be:

- pure JavaScript CPU work
- JSON-serializable `workerData`
- structured-clone-compatible primitive values and ArrayBuffers only where safe
- safe limited builtins such as `buffer`, `path`, `util`, `events`, `assert`, `querystring`, `string_decoder`, `crypto` subsets, and privacy-safe `os` subsets
- explicit timeout and worker count limits
- parent-owned lifecycle and cancellation

The first version should not allow workers to spawn more workers.

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

Future worker builtins should start from a separate worker-safe allowlist, not the parent profile by default.

Suggested first allowlist:

- `assert`
- `buffer`
- `events`
- `path`
- `querystring`
- `string_decoder`
- `timers`
- `url` without file URL escape
- `util`
- safe crypto subsets
- privacy-safe `os` subset

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

`fs` should remain denied in the first worker version unless a scoped, parent-owned policy is designed for worker filesystem access.

## Limits

Recommended defaults for a future experimental implementation:

- `maxWorkers`: 1 per script execution
- hard upper bound: 2 workers per execution
- default worker startup timeout: 5000 ms
- default worker run timeout: parent script timeout remaining time
- per-worker memory budget: design before implementation; do not rely on V8 defaults alone
- maximum worker source size: reuse module source budgets unless a lower worker-specific budget is set

Workers must count as pending lifecycle work. The parent script cannot complete successfully while a worker is still running unless that worker has been explicitly detached by a separate, reviewed policy. Detached workers are out of scope for the MVP.

## Lifecycle Policy

A future worker implementation must define:

- creation from the Node event loop thread
- callback marshaling back to the parent event loop
- parent timeout cancels all workers
- service destroy terminates all workers
- uncaught worker exception rejects or emits through the parent according to documented Node-compatible behavior
- unhandled worker rejection is surfaced to parent diagnostics
- worker termination is idempotent
- native handles are enumerable in debug diagnostics

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

Future worker-specific errors should be added before implementation, for example:

- `ERR_AUTOJS6_WORKER_DISABLED`
- `ERR_AUTOJS6_WORKER_LIMIT_EXCEEDED`
- `ERR_AUTOJS6_WORKER_BRIDGE_DENIED`
- `ERR_AUTOJS6_WORKER_TIMEOUT`
- `ERR_AUTOJS6_WORKER_TERMINATED`

## Test Gate

Current gate:

- `NodeWorkerThreadsDesignGateInstrumentationTest` verifies the BuildConfig flag, Kotlin registry, JavaScript profile, bare and `node:` require denial, `require.resolve` denial, and shadowing protection.
- `NodeSecurityRegressionInstrumentationTest` keeps `worker_threads` in the dangerous builtin denial corpus.

Before future worker support is enabled, tests must cover:

- default flag off keeps workers denied
- flag on still denies AutoJs bridge modules inside workers
- max worker count
- timeout termination
- service destroy termination
- uncaught exception propagation
- unhandled rejection propagation
- nested worker denial
- worker cannot access raw network, child process, native addon, vm, inspector, or unrestricted fs
- packaged APK worker source graph and verifier behavior

## Non-Goals

Task 42 does not enable:

- `require("worker_threads")`
- `new Worker(...)`
- nested workers
- shared memory or `SharedArrayBuffer`
- `MessageChannel` compatibility beyond what already exists elsewhere
- AutoJs bridge access from workers
- Android Context access from workers
- packaged worker source graphs
