# Embedded Node Execution Queue

Status: implemented as the default bounded queue for ordinary script starts, with an explicit reject mode retained for diagnostics.

## Policy

The default embedded execution policy remains:

```text
one active embedded Node execution per :nodejs_embedded process
```

Normal script starts use `enqueue_when_busy`. A concurrent start that reaches an occupied `:nodejs_embedded` process waits in a bounded execution queue instead of immediately failing with:

```text
ERR_AUTOJS6_NODE_ENGINE_BUSY
```

Callers that need the old fail-fast behavior can explicitly request `reject_when_busy`. That mode still returns `ERR_AUTOJS6_NODE_ENGINE_BUSY` when another execution owns the target process slot.

The queue does not reuse a completed embedded Node instance. Each dequeued item starts a normal one-shot `NativeNodeEmbeddedScriptService` run, then waits briefly for the `:nodejs_embedded` process to settle before releasing the next queued execution. This avoids a second `libnode`/V8/libuv startup inside the same service process.

## Queue Limits

Current limits:

- max queued executions: `16`
- default queue wait timeout: `300000ms`
- max queue wait timeout: `300000ms`

The service queue serializes executions that target the same embedded service process. The default process-pool scheduler also waits for an available `primary` / `overflow_1` slot when both eligible default slots are busy. The embedded service itself still enforces one active execution per process slot.

## API Shape

Kotlin callers can use:

```kotlin
NativeNodeEmbeddedScriptService.enqueueScript(context, request)
NativeNodeEmbeddedScriptService.cancelQueuedExecution(context, executionId)
NativeNodeEmbeddedScriptService.queueStatus(context)
```

`EmbeddedScriptRequest` includes:

- `executionId`
- `queueMode`
- `queueTimeoutMs`

Callers that need cancellation should provide a stable `executionId` before enqueuing. `queueMode` controls whether a direct `runScript(...)` start waits (`enqueue_when_busy`) or fails fast (`reject_when_busy`).

## Error Codes

Queue-specific failures are stable:

- `ERR_AUTOJS6_NODE_ENGINE_BUSY`: explicit fail-fast path when another execution is active
- `ERR_AUTOJS6_NODE_QUEUE_FULL`: enqueue requested but max queued executions is reached
- `ERR_AUTOJS6_NODE_QUEUE_TIMEOUT`: queued execution did not acquire the active slot before its queue timeout
- `ERR_AUTOJS6_NODE_QUEUE_CANCELLED`: queued execution was cancelled before it started

Queue failures are not reported as script timeouts because the user script did not start.

## Diagnostics

Results include queue metadata in `nativeValues`:

- `embedded_script.queue_mode`
- `embedded_script.queue_status`
- `embedded_script.queue_queued`
- `embedded_script.queue_wait_ms`
- `embedded_script.queue_position`
- `embedded_script.queue_max_queued_executions`

The explicit busy path also preserves:

- `embedded_script.engine_busy=true`
- `embedded_script.concurrent_policy=single-active-execution-per-process`
- `embedded_script.active_execution_id`
- `embedded_script.rejected_execution_id`

## Lifecycle

When an enqueued execution exits, the caller-side queue waits for the service process to settle, releases the queue slot, and wakes the next queued request. The next request starts a fresh normal embedded service execution.

Queued cancellation, queue full, and queue timeout failures are returned before a service execution starts. They do not stop or mutate the active embedded service process.

## Tests

Focused lifecycle stress coverage verifies:

- default concurrent starts queue and run after the active execution releases the process slot
- explicit `reject_when_busy` starts still reject with `ERR_AUTOJS6_NODE_ENGINE_BUSY`
- an explicitly queued second script runs after the first finishes
- a queued script can be cancelled by execution id
- a queued script times out with `ERR_AUTOJS6_NODE_QUEUE_TIMEOUT` before user code runs
- a third enqueue fails with `ERR_AUTOJS6_NODE_QUEUE_FULL` when the queue is full
