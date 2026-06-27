# Rhino Threads Migration Cookbook

Status: P12-21 migration cookbook.

This cookbook is the script-author migration guide for Rhino `threads`
patterns. It does not promote Rhino thread parity in Node.
It does not depend on default `worker_threads`.

It does not introduce `require("threads")`, `compat.threads`, a global
`threads`, or a global `sync`.

The compatibility status remains intentionally narrow:

| Rhino API | Node status | Migration target |
| --- | --- | --- |
| `threads.__module` | `blocked_by_design` | Use explicit Node modules by intent. |
| `threads.start(fn)` | `blocked_by_design` | Use `async` / `await`, bounded `engines` slots, or `work_manager`. |
| `threads.interrupt()` | `blocked_by_design` | Use `AbortController`, `timeoutMs`, script stop, or provider cleanup. |
| `threads.pool()` | `blocked_by_design` | Use a bounded async queue now; P12-22 may evaluate a separately named compute/process-worker API. |
| `threads.disposable()` | `blocked_by_design` | Use `Promise`, `EventEmitter`, or a MessageChannel-style handoff. |
| `sync(fn, lock)` | `blocked_by_design` | Use single-owner state, a Promise queue, or immutable message passing. |

## Ground Rules

- Node scripts must not expect `require("threads")` to exist.
- Node scripts must not expect `require("autojs6:compat").threads` to exist.
- Node scripts must not expect a global `threads` or global `sync`.
- Node scripts must not depend on default `worker_threads`.
- Node scripts must not share mutable Rhino `ScriptRuntime`, Java locks, raw
  `Thread`, `ThreadPoolExecutor`, or `VolatileDispose` objects across slots.

## `threads.start(fn)`

Use plain async flow when the old thread only wrapped provider work:

```js
"nodejs";

async function main() {
  const { showToast } = require("toast");
  await showToast("background");
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
```

Use an explicit engine slot when the work must run as a separate script. Pass
JSON-safe arguments and set a deadline:

```js
"nodejs";

async function main() {
  const engines = require("engines");
  await engines.execScriptFile("jobs/cleanup.cjs", {
    args: { reason: "manual" },
    timeoutMs: 30000,
  });
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
```

Use scheduled bounded work when the old thread represented deferred work:

```js
"nodejs";

async function main() {
  const workManager = require("work_manager");
  await workManager.scheduleOnce({
    scriptPath: "jobs/cleanup.cjs",
    backend: "embedded",
    delayMs: 1000,
    taskTimeoutMs: 30000,
  });
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
```

## `threads.interrupt()`

Use cancellation tokens and provider deadlines rather than raw thread handles:

```js
"nodejs";

async function withTimeout(task, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await task(controller.signal);
  } finally {
    clearTimeout(timer);
  }
}

async function main() {
  const fs = require("fs/promises");
  const text = await withTimeout((signal) => fs.readFile("input.txt", { encoding: "utf8", signal }), 5000);
  console.log(text);
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
```

For bridge providers, prefer an API-level `timeoutMs` option. For independent
scripts, stop or cancel the explicit execution slot rather than interrupting a
shared runtime thread.

## `threads.pool()`

Use a bounded async queue for I/O and provider tasks:

```js
"nodejs";

function createQueue(concurrency) {
  let active = 0;
  const pending = [];

  function drain() {
    while (active < concurrency && pending.length > 0) {
      const item = pending.shift();
      active += 1;
      Promise.resolve()
        .then(item.task)
        .then(item.resolve, item.reject)
        .finally(() => {
          active -= 1;
          drain();
        });
    }
  }

  return function enqueue(task) {
    return new Promise((resolve, reject) => {
      pending.push({ task, resolve, reject });
      drain();
    });
  };
}

async function main() {
  const enqueue = createQueue(2);
  await Promise.all([
    enqueue(() => Promise.resolve("a")),
    enqueue(() => Promise.resolve("b")),
    enqueue(() => Promise.resolve("c")),
  ]);
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
```

P12-22 may evaluate a separately named compute/process-worker replacement API.
That future API must not reuse the `threads` name and must not claim Rhino
same-runtime parity.

## `threads.disposable()`

Replace blocking `blockedGet()` / `setAndNotify()` handoffs with promises:

```js
"nodejs";

function withDeadline(promise, timeoutMs) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), timeoutMs)),
  ]);
}

async function main() {
  const fs = require("fs/promises");
  const value = await withDeadline(fs.readFile("value.txt", "utf8"), 5000);
  console.log(value);
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
```

For repeated notifications, prefer `EventEmitter` or a MessageChannel-style
handoff that passes immutable JSON-safe values. Do not block the event loop to
wait for shared mutable state.

## `sync(fn, lock)`

Move shared mutable state behind one owner and serialize writes with a queue:

```js
"nodejs";

function createSerialQueue() {
  let tail = Promise.resolve();
  return function enqueue(task) {
    const next = tail.then(task, task);
    tail = next.catch(() => {});
    return next;
  };
}

async function main() {
  let count = 0;
  const enqueue = createSerialQueue();
  await Promise.all([
    enqueue(() => { count += 1; return count; }),
    enqueue(() => { count += 1; return count; }),
  ]);
  console.log(count);
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exitCode = 1;
});
```

When the state belongs to a separate process or execution slot, pass immutable
messages and let that owner apply updates. Avoid Java locks and Rhino
`Synchronizer` semantics at the V8 boundary.

## Dual-Engine Corpus

The Phase 12 cookbook is backed by the static dual-engine corpus:

- `threads-start` documents legacy `threads.start(fn)` and the Node
  `blocked_by_design` marker.
- `threads-disposable` documents `threads.disposable().blockedGet()` and the
  Node `blocked_by_design` marker.
- `threads-sync-queue` documents `sync(fn, lock)` and the Node
  `blocked_by_design` marker.

The Node snippets intentionally do not expose a runtime `threads` object. They
only assert the stable compatibility marker that script authors can detect
while migrating to the patterns above.

## Verification

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatThreadsMigrationCookbook
```

The gate checks this cookbook, the dual-engine fixtures, the status manifest,
the generated matrix, the surface manifest, and the Phase 12 task checklist.
