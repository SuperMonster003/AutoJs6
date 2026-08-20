# Rhino JSON-Only Execution Boundary

Status: implemented explicit compatibility boundary.

`require("rhino").run()` is an opt-in cross-engine call expressed by the
request field `explicit: true`. It is not controlled by Gradle, build type,
project metadata, or packaged feature flags.

## Request

```ts
{
  explicit: true;
  source?: string;
  path?: string;
  timeoutMs: number;
  args?: JsonValue;
}
```

Exactly one of `source` or `path` is required. `path` is resolved inside the
execution sandbox. Missing `explicit: true`, ambiguous source selection,
invalid JSON values, path escape, oversized input, or invalid timeout rejects
before Rhino execution.

## Result

The adapter returns JSON-safe result data and bounded console output. It never
returns a Rhino `Scriptable`, Java/Android object, function, native handle,
thread/process object, file descriptor, cyclic graph, symbol, bigint, undefined
value, or prototype-bearing object.

## Lifecycle

The host creates an isolated Rhino engine for the request, enforces the deadline,
captures bounded diagnostics, serializes the result, and destroys the engine.
Timeout, cancellation, Node execution stop, service destruction, serialization
failure, and provider failure all close the request without retaining Rhino
runtime state.

## Security Invariants

- no automatic Rhino globals in V8;
- no shared Node/Rhino mutable object graph;
- no Java or Android object transfer;
- no unscoped filesystem access;
- no native pointer, process, thread, lock, or descriptor transfer;
- no packaged authorization inferred from a source file alone;
- stable error codes for disabled, invalid, timeout, provider, and
  serialization failures.

Java proxy installation is a separate explicit operation described in
[RHINO_SHIM.md](RHINO_SHIM.md) and remains bound to the stable Java allowlist.
