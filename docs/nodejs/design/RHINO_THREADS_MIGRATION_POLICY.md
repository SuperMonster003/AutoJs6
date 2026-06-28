# Rhino Threads Migration Policy

Status: S11-25 design and policy gate; P12-20 maturity review keeps the same
blocked-by-design decision; P12-21 publishes the migration cookbook; P12-22
defers a compute/process-worker replacement API without changing the runtime
surface. This document records how Rhino `threads` scripts should migrate to
Embedded Node without adding a Rhino-style thread facade.

## Decision

Do not add `require("threads")`, `compat.threads`, a global `threads`, a
global `sync`, or default `worker_threads` as the migration target.

Rhino `threads` is blocked as an exact Node compatibility surface because it
depends on:

- a shared `ScriptRuntime` and shared Rhino scope objects;
- `TimerThread` instances that run callbacks against the same runtime;
- raw Java `Thread`, `ThreadPoolExecutor`, `AtomicLong`, and `ReentrantLock`
  objects;
- Rhino `Synchronizer` for `sync(fn, lock)`;
- `VolatileDisposeNativeObject` with blocking `blockedGet()`,
  `blockedGetOrThrow()`, and `setAndNotify()`;
- interruption and shutdown semantics tied to Rhino script lifecycle.

The Safe Node Profile uses a single Node event loop plus explicit bridge
providers. Node code must not block that event loop waiting for Android,
AutoJs, Java locks, shared objects, or cross-thread callbacks.

## Migration Model

Use these replacements by intent:

| Rhino intent | Node migration target | Notes |
| --- | --- | --- |
| Run asynchronous AutoJs work | `Promise`, `async` / `await`, `timers/promises`, bridge APIs with `timeoutMs` | Preferred default. Keep work on the Node event loop and await provider calls. |
| Cancel or time-limit work | `AbortController`, explicit timeout options, script stop/provider cleanup | Do not expose raw `Thread.interrupt()`. |
| Exchange one result later | `Promise`, `EventEmitter`, `MessageChannel`, explicit callback registration | Do not use blocking `blockedGet()`. |
| Serialize access to shared state | one owner object, async queue, immutable JSON snapshots | Do not expose Rhino `Synchronizer` or Java locks. |
| Run a separate bounded script | `engines.execScriptFile()` / `engines.execScript()` with JSON arguments, or `work_manager` for scheduled bounded work | This is a separate execution slot, not a same-runtime thread. |
| Long running user-visible work | explicit interactive long-running service mode | Must remain foreground/user-visible and lifecycle-audited. |
| Pure compute worker | reserved `worker_computation` / internal `worker_threads` policy path | Not a default or release migration target in v1.1. |

If a future process-worker API is added while native `worker_threads` remains
blocked, it must be explicitly named and must not claim exact Rhino `threads`
or desktop Node `worker_threads` semantics.

## API Mapping

| Rhino API | S11-25 status | Node direction |
| --- | --- | --- |
| `threads.__module` | `blocked_by_design` | No `require("threads")`, `compat.threads`, global `threads`, or type surface. |
| `threads.start(fn)` | `blocked_by_design` | Use `async` / `await` for provider work, or an explicit separate script/process slot for bounded work. It is not a same-runtime `TimerThread`. |
| `threads.pool(options)` | `blocked_by_design` | No Java `ThreadPoolExecutor` or shared runtime pool. Future worker pools must be pure compute, policy-gated, and not default. |
| `threads.interrupt(thread)` | `blocked_by_design` | Use `AbortController`, bridge `timeoutMs`, script stop, and provider cleanup. Raw thread handles are not exposed. |
| `threads.disposable()` | `blocked_by_design` | Use `Promise`, `EventEmitter`, or `MessageChannel`. Blocking `blockedGet()` is not allowed on the Node event loop. |
| `sync(fn, lock)` / `threads.sync` | `blocked_by_design` | Use an async queue or one-owner state model. Rhino `Synchronizer`, shared locks, and mutable shared objects are not exposed. |

## Examples

### `threads.start(fn)`

Rhino:

```js
threads.start(function () {
  toast("background");
});
```

Node:

```js
"nodejs";

(async function main() {
  const { showToast } = require("toast");
  await showToast("background");
})();
```

For a bounded separate script, pass JSON arguments and treat the other script
as a separate execution:

```js
"nodejs";

(async function main() {
  const engines = require("engines");
  const execution = await engines.execScriptFile("jobs/worker.cjs", {
    args: { id: "cleanup-1" },
    timeoutMs: 30000
  });
  console.log(execution.id);
})();
```

### `threads.disposable()`

Rhino:

```js
const box = threads.disposable();
threads.start(function () {
  box.setAndNotify(files.read("./value.txt"));
});
log(box.blockedGet(5000));
```

Node:

```js
"nodejs";

(async function main() {
  const fs = require("fs/promises");
  const value = await Promise.race([
    fs.readFile("value.txt", "utf8"),
    new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), 5000))
  ]);
  console.log(value);
})();
```

### `sync(fn, lock)`

Rhino:

```js
let count = 0;
const inc = sync(function () {
  count += 1;
  return count;
});
threads.start(inc);
threads.start(inc);
```

Node:

```js
"nodejs";

function createAsyncCounter() {
  let count = 0;
  let tail = Promise.resolve();
  return function increment() {
    tail = tail.then(() => {
      count += 1;
      return count;
    });
    return tail;
  };
}

(async function main() {
  const inc = createAsyncCounter();
  const values = await Promise.all([inc(), inc()]);
  console.log(values.join(","));
})();
```

### `threads.pool(options)`

Do not translate `threads.pool()` to default `worker_threads`. For scheduled or
deferred work, use `work_manager`:

```js
"nodejs";

(async function main() {
  const workManager = require("work_manager");
  const taskId = await workManager.scheduleOnce({
    scriptPath: "jobs/cleanup.cjs",
    backend: "embedded",
    delayMs: 1000,
    taskTimeoutMs: 30000
  });
  console.log(taskId);
})();
```

## Worker Boundary

`worker_threads` remains disabled by default. The internal experimental flag
`autojs.nodejs.worker_threads.experimental=false` is not a user-visible release
opt-in. When the flag is off, `require("worker_threads")`,
`require("node:worker_threads")`, and shadowing attempts fail closed. When the
flag is on, current builds still perform native availability preflight and may
fail with `ERR_AUTOJS6_WORKER_DISABLED`.

If native workers become available later, they are still not a Rhino `threads`
replacement. The allowed worker shape is pure JavaScript computation with
restricted module loading, scoped filesystem wrappers, bounded messages,
structured-clone-safe data, no AutoJs bridge modules, no Java/Android objects,
no raw shared mutable objects, no same-runtime callbacks, and no nested worker
authority unless a separate gate approves it.

## P12-20 Maturity Review

P12-20 rechecked `worker_threads`, process slot, and execution-mode maturity
for Phase 12 and kept the S11-25 conclusion:

- default `worker_threads` remains closed;
- the experimental worker path is an internal native-availability/preflight
  gate, not a user-facing migration target;
- runnable worker creation, stress, and packaged worker source graph evidence
  remain blocked until native workers are available and the focused suites pass;
- `worker_computation` remains reserved and is not a `threads` replacement;
- P13-06 closes `second_execution_slot` for scheduler/stress evidence, while
  P13-08 still owns long-duration soak through `full_heavy_resource_stress`;
- P12-21 cookbook examples must avoid default workers and use `async` /
  `await`, `AbortController`, explicit `engines` execution slots, or
  `work_manager`;
- P12-22 may evaluate a separately named compute/process-worker API, but it
  must not reuse `threads` or claim same-runtime Rhino parity.

See `docs/nodejs/reports/phase12/RHINO_THREADS_MATURITY_REVIEW.md`.

## P12-21 Cookbook

P12-21 turns the policy into script-author cookbook guidance in
`docs/nodejs/reports/phase12/RHINO_THREADS_MIGRATION_COOKBOOK.md` and extends
the dual-engine corpus with `threads-disposable` and `threads-sync-queue`.
The cookbook keeps the same `blocked_by_design` status for every `threads.*`
entry and shows replacements based on `async` / `await`, `AbortController`,
explicit `engines` slots, `work_manager`, bounded async queues, Promise queues,
and MessageChannel-style handoffs.

## P12-22 Compute/Process Worker Decision

P12-22 records that Phase 12 does not promote a compute/process-worker
replacement API. `worker_computation` remains `reserved`,
P13-06 closes `second_execution_slot`, and `worker_threads` remains default-off
and native-availability gated. Any future API must be separately named, must
not reuse `threads` or `worker_threads`, and must pass worker/process
permissions, scoped script path, message size, cleanup, nested worker denial,
bridge module isolation, resource stress, packaged behavior, and final blocker
gates before becoming a script-author surface.

See `docs/nodejs/reports/phase12/RHINO_COMPUTE_PROCESS_WORKER_DECISION.md`.

## Acceptance

- No default `worker_threads`.
- No `require("threads")`, `compat.threads`, global `threads`, or global
  `sync`.
- No shared Rhino object model, `ScriptRuntime`, `Thread`, `ThreadPoolExecutor`,
  `Synchronizer`, `ReentrantLock`, `AtomicLong`, or `VolatileDispose` exposure.
- Status manifest keeps all Rhino `threads` entries `blocked_by_design`.
- Migration examples point to `async` / `await`, timeout/cancellation, explicit
  script/process slots, `work_manager`, and future policy-gated compute workers.
