# Embedded Node Multi-Instance Policy

Status: enforced single active execution per embedded service process. Phase 11
adds an internal explicit `primary` / `overflow_1` process-slot selector for
focused diagnostics. Phase 13 adds a default one-shot scheduler that can route
eligible concurrent ordinary scripts to `overflow_1`; packaged execution still
uses the same explicit slot metadata path for focused process-pool smoke.

## Decision

AutoJs6 Safe Node Profile uses this default policy:

```text
one active embedded Node execution per :nodejs_embedded process
```

When a second execution request reaches the same embedded service process while another execution is active, AutoJs6 rejects the second request with:

```text
ERR_AUTOJS6_NODE_ENGINE_BUSY
```

The rejected request is not queued silently.

Callers that deliberately want queueing must use the controlled caller-side enqueue API. Queueing is not the default startup behavior.

Focused Phase 11 diagnostics may explicitly route a one-shot execution to
`overflow_1`, which runs in `:nodejs_embedded_2`. Phase 13 default scheduling
can also route ordinary one-shot scripts to `overflow_1` when `primary` is
leased and the device profile recommends at least two active slots. This is not
same-process concurrency. Embedded script execution accepts all declared script
slot process suffixes; native probes remain restricted to the primary probe
process.

## Rationale

Embedded Node execution owns process-local native state:

- `v8::Isolate`
- `uv_loop_t`
- `node::Environment`
- stdout/stderr capture
- bridge transport directories
- Android capability providers
- service lifecycle and foreground notification state

Each service process is intentionally short-lived and schedules process exit
after an execution finishes. Allowing overlapping executions in the same process
would make stop, timeout, native handle cleanup, bridge callbacks, and stdout
capture ambiguous.

Rejecting concurrent starts by default is safer than silent queueing. The controlled queue keeps max queued executions, timeout ownership, cancellation, status, and service-exit behavior explicit.

## Current Runtime Behavior

The embedded service keeps a process-local active execution id.

Normal execution:

```text
start request
  -> acquire active execution slot
  -> run script
  -> stop bridge transport
  -> release active execution slot
  -> stop service / schedule process exit
```

Concurrent execution:

```text
start request
  -> active execution slot already owned
  -> return EmbeddedNodeEngineBusyError
  -> errorCode=ERR_AUTOJS6_NODE_ENGINE_BUSY
  -> nativeValues.embedded_script.engine_busy=true
  -> nativeValues.embedded_script.concurrent_policy=single-active-execution-per-process
```

Explicit queued execution:

```text
enqueue request
  -> caller-side queue slot already owned
  -> enter bounded caller-side execution queue
  -> previous service execution exits and process settles
  -> start a normal one-shot service execution
  -> return queue diagnostics in nativeValues
```

Timeout or process crash still follows the existing timeout/crash diagnostics. If the client times out and stops the service, the process exits rather than admitting another execution into an uncertain native lifecycle.

## Packaged APK Behavior

Packaged APKs use the same `NativeNodeEmbeddedScriptService` and `:nodejs_embedded` process policy. A packaged app must not start overlapping embedded Node executions in the same process and expect implicit queuing.

Packaged behavior is:

- first active execution owns the process
- concurrent starts reject with `ERR_AUTOJS6_NODE_ENGINE_BUSY`
- after the first execution exits and the service settles, a later execution may run normally
- packaged Node launch descriptors may include `processPoolSlot=primary|overflow_1`
  and `processPoolSlotSource=explicit_request` for internal process-pool smoke
- explicit queue metadata is caller-local and must remain visible through diagnostics when packaged callers opt in

## Queue Boundary

The execution queue is intentionally small and explicit. It does not make embedded Node multi-instance:

- still one active V8/libuv/Node environment per `:nodejs_embedded` process
- queued executions are serialized before service start and run as separate one-shot service executions
- max queued executions is currently `1`
- queue cancellation works only before the queued script starts
- queue timeout is separate from script execution timeout
- queue failures return stable `ERR_AUTOJS6_NODE_QUEUE_*` codes

See [EXECUTION_QUEUE.md](EXECUTION_QUEUE.md).

## Process Pool Policy Gate

S8-43 declares the first process-pool policy gate. Android process names remain
static, so the manifest includes embedded script service components for
`:nodejs_embedded_1` and `:nodejs_embedded_2`. Phase 11 promotes the
`overflow_1` component to an internal explicit one-shot diagnostic route.

Current S8-43 behavior:

- default `NodeEmbeddedProcessPoolPolicy` status is `design_gated`;
- default runtime mode is `primary_only`;
- default effective active slot count is `1`;
- explicit `overflow_1` diagnostics use `explicit_slot_enabled` /
  `explicit_two_slot` with effective active slot count `2`;
- Phase 13 default scheduler diagnostics use `default_scheduler_enabled` /
  `default_two_slot` with effective active slot count `2`;
- packaged launch descriptor diagnostics can preserve `processPoolSlot` and
  route focused one-shot smokes to `overflow_1`;
- slot crash fallback is explicitly `no_restart_collect_marker` with no failed
  slot replacement until a real coordinator lands;
- device-memory-aware recommended slot count is diagnostic only;
- the long-running dedicated slot is reserved but disabled;
- queue-when-full remains disabled until a real pool coordinator exists.

Future promotion still needs:

- crash detection that removes only the failed slot;
- foreground notification policy per execution;
- packaged manifest synthesis for every service component;
- packaged scheduler / full pool coordinator routing beyond explicit smoke
  metadata;
- stress tests proving no cwd, env, module cache, bridge transport, provider,
  fd, watcher, timer, or handle crosses execution or slot boundaries.

See [PROCESS_POOL.md](PROCESS_POOL.md).

## Tests

Current focused coverage:

- start a long-running embedded script
- start a second embedded script while the first is active
- assert the second returns `ERR_AUTOJS6_NODE_ENGINE_BUSY`
- assert the first completes normally
- assert a later script runs after the active slot is released
- enqueue a second script explicitly and assert it runs after the first
- cancel a queued script by execution id
- assert a third enqueue returns `ERR_AUTOJS6_NODE_QUEUE_FULL` when the queue is full
- explicitly run `overflow_1` and assert `:nodejs_embedded_2` diagnostics
- explicitly run `primary` and `overflow_1` concurrently and assert different
  service-process pids
- run two default one-shot scripts concurrently on an eligible device and assert
  the scheduler selects `primary` plus `overflow_1`
- read and run packaged/project launch descriptor `processPoolSlot` metadata and
  assert explicit `overflow_1` diagnostics
- assert descriptor-driven `overflow_1` execution is accepted by the embedded
  script process guard
- collect an `overflow_1` running marker under the explicit no-restart fallback
  policy, then assert `primary` can still run

Release candidates should also verify packaged smoke does not treat queue support
as same-process concurrent execution support.
