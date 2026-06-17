# Embedded Node Execution Queue

Status: implemented as an explicit opt-in caller-side queue for `NativeNodeEmbeddedScriptService`.

## Policy

The default embedded execution policy remains:

```text
one active embedded Node execution per :nodejs_embedded process
```

Normal script starts use `reject_when_busy`. A concurrent start is rejected with:

```text
ERR_AUTOJS6_NODE_ENGINE_BUSY
```

Queueing is available only when the caller explicitly uses the enqueue API. This keeps existing script startup behavior deterministic and prevents silent queueing of user-triggered runs.

The queue does not reuse a completed embedded Node instance. Each dequeued item starts a normal one-shot `NativeNodeEmbeddedScriptService` run, then waits briefly for the `:nodejs_embedded` process to settle before releasing the next queued execution. This avoids a second `libnode`/V8/libuv startup inside the same service process.

## Queue Limits

Current limits:

- max queued executions: `1`
- default queue wait timeout: `30000ms`
- max queue wait timeout: `300000ms`

The queue is local to the caller process that owns `enqueueScript(...)`. It serializes queue participants before they reach the embedded service. The embedded service itself still enforces one active execution per `:nodejs_embedded` process and receives queued executions as normal `reject_when_busy` service starts.

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

Callers that need cancellation should provide a stable `executionId` before enqueuing. `queueMode` is diagnostic/request metadata; the public queue API is `enqueueScript(...)`, and direct `runScript(...)` continues to use the default reject-when-busy service path.

## Error Codes

Queue-specific failures are stable:

- `ERR_AUTOJS6_NODE_ENGINE_BUSY`: default reject path when another execution is active
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

The default busy path also preserves:

- `embedded_script.engine_busy=true`
- `embedded_script.concurrent_policy=single-active-execution-per-process`
- `embedded_script.active_execution_id`
- `embedded_script.rejected_execution_id`

## Lifecycle

When an enqueued execution exits, the caller-side queue waits for the service process to settle, releases the queue slot, and wakes the next queued request. The next request starts a fresh normal embedded service execution.

Queued cancellation, queue full, and queue timeout failures are returned before a service execution starts. They do not stop or mutate the active embedded service process.

## Tests

Focused lifecycle stress coverage verifies:

- default concurrent starts still reject with `ERR_AUTOJS6_NODE_ENGINE_BUSY`
- an explicitly queued second script runs after the first finishes
- a queued script can be cancelled by execution id
- a third enqueue fails with `ERR_AUTOJS6_NODE_QUEUE_FULL` when the queue is full
