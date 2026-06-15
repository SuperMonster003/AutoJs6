# Embedded Node Multi-Instance Policy

Status: enforced single active execution per `:nodejs_embedded` process, with an explicit opt-in queue documented in [EXECUTION_QUEUE.md](EXECUTION_QUEUE.md).

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

## Rationale

Embedded Node execution owns process-local native state:

- `v8::Isolate`
- `uv_loop_t`
- `node::Environment`
- stdout/stderr capture
- bridge transport directories
- Android capability providers
- service lifecycle and foreground notification state

The current service process is intentionally short-lived and schedules process exit after an execution finishes. Allowing overlapping executions in the same process would make stop, timeout, native handle cleanup, bridge callbacks, and stdout capture ambiguous.

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

## Future Process-Per-Execution Option

A future implementation could spawn separate service processes for concurrent scripts, but Android process names must be statically declared. That design would need:

- a finite process pool
- explicit service components per slot
- ABI/native library diagnostics per slot
- bridge provider isolation per process
- foreground notification policy per execution
- packaged manifest synthesis for every service component

This is outside the current Safe Node Profile baseline.

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

Release candidates should also verify packaged smoke still uses one embedded service process and does not treat queue support as concurrent execution support.
