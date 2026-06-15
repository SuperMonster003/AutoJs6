# Embedded Node Multi-Instance Policy

Status: enforced single active execution per `:nodejs_embedded` process. This task prevents accidental overlapping V8/libuv lifecycle ownership. It does not add an execution queue.

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

Rejecting concurrent starts is safer than queuing until Task 82 defines cancellation, fairness, queue limits, timeout ownership, and user-visible status.

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

Timeout or process crash still follows the existing timeout/crash diagnostics. If the client times out and stops the service, the process exits rather than admitting another execution into an uncertain native lifecycle.

## Packaged APK Behavior

Packaged APKs use the same `NativeNodeEmbeddedScriptService` and `:nodejs_embedded` process policy. A packaged app must not start overlapping embedded Node executions in the same process and expect implicit queuing.

Packaged behavior is:

- first active execution owns the process
- concurrent starts reject with `ERR_AUTOJS6_NODE_ENGINE_BUSY`
- after the first execution exits and the service settles, a later execution may run normally
- future packaged queue metadata must be explicit and verified by the packaged diagnostic report

## Queue Boundary

Task 81 deliberately does not implement a queue.

Task 82 may add a controlled queue only after defining:

- max queued executions
- per-execution timeout ownership
- cancellation semantics
- user stop behavior
- queue status diagnostics
- packaged APK behavior
- interaction with service process exit
- behavior when the active execution crashes or times out

Until then, `ERR_AUTOJS6_NODE_ENGINE_BUSY` is the stable behavior.

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

Release candidates should also verify packaged smoke still uses one embedded service process and does not add queue metadata unless Task 82 implements it.
