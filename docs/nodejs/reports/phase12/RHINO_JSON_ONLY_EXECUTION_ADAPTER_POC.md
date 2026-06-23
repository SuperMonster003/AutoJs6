# Rhino JSON-Only Execution Adapter POC

Status: P12-25 execution adapter POC implemented behind an explicit gate.

P12-25 keeps the P12-24 default-disabled `require("rhino")` facade, but adds a
small JSON-only execution path for scripts that call:

```js
const rhino = require("rhino");

const result = await rhino.run({
  experimental: true,
  source: "({ sum: args[0] + args[1] })",
  args: [2, 3],
  timeoutMs: 5000,
});
```

The `experimental: true` field is required on every POC request. Calls without
that field still reject with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`. The facade still
reports `mode: "disabled"` and `enabled: false`; this keeps the release/default
Safe Node Profile fail-closed unless a script deliberately uses the POC request
shape.

## Runtime Path

- Node validates the request before bridge dispatch:
  - `experimental: true` is required;
  - exactly one of `source` or `path` is required;
  - `timeoutMs` is finite, positive, and capped at 30000 ms;
  - `cwd` and `path` must be scoped relative paths;
  - `args` must be a JSON array no larger than 16384 bytes;
  - inline `source` and scoped file contents are capped at 65536 bytes.
- The bridge module is `rhino.run` and requires the `rhino` bridge capability.
- Android dispatch is handled by `NodeBridgeRhinoExecutionService`.
- The service creates a dedicated `RhinoJavaScriptEngine`, installs JSON `args`
  as a Rhino global, executes the source or scoped file, and destroys the engine
  in `finally`.
- The POC does not use `ScriptEngineService.execute()` because that path is
  loop-oriented and does not provide the final script result needed for the
  JSON-only adapter.

The POC argument convention is `args`, not Rhino's implicit `arguments` object.
Existing Rhino scripts that rely on `arguments[0]` need a small wrapper before
using this POC.

## Result Contract

Successful and failed Rhino executions both resolve to a frozen JSON-safe
`RunResult`:

```ts
type RunResult<T> =
  | { ok: true; value: T; console: ConsoleSummary; lifecycle: LifecycleSummary }
  | { ok: false; error: RunError; timedOut: boolean; console: ConsoleSummary; lifecycle: LifecycleSummary };
```

The runtime result schema is `autojs6-rhino-json-only-run-result-v1`, with
`mode: "json-only-poc"` and provider `android-rhino-json-only-poc`.

The service converts the Rhino result by running `JSON.stringify()` inside the
Rhino context, then parsing the serialized value through Android JSON APIs
before returning it to Node. Raw Java, Android, Rhino wrapper, and function
results are rejected before Node can observe them.

## Errors

Stable JSON error objects are returned in failed `RunResult` records:

| Code | Category | Meaning |
| --- | --- | --- |
| `ERR_AUTOJS6_RHINO_SHIM_DISABLED` | `disabled` | Missing `experimental: true` gate or queued-host provider unavailable. |
| `ERR_AUTOJS6_RHINO_INVALID_REQUEST` | `invalid-request` | Request shape, path, source, args, or timeout is invalid. |
| `ERR_AUTOJS6_RHINO_EXECUTION_ERROR` | `rhino-exception` | Rhino threw during execution. |
| `ERR_AUTOJS6_RHINO_EXECUTION_TIMEOUT` | `timeout` | Rhino execution exceeded `timeoutMs`; the service requests `forceStop()`. |
| `ERR_AUTOJS6_RHINO_JSON_UNSAFE_RESULT` | `json-unsafe-result` | Rhino returned a function, wrapper, raw object, or value that cannot be serialized as JSON. |
| `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED` | `unsupported` | `rhino.install()` remains permanently unsupported. |

Rhino exception errors include stable JSON fields such as `name`, `message`,
`code`, `category`, optional `sourceName`, optional `lineNumber`, optional
`columnNumber`, and a bounded stack string.

## Timeout And Cleanup

The Android service runs each POC execution on a dedicated single-thread
executor and waits only for the requested `timeoutMs`. On timeout it sets the
stable timeout error, calls `RhinoJavaScriptEngine.forceStop()`, waits a bounded
destroy grace period, then reports lifecycle state:

```json
{
  "engineCreated": true,
  "forceStopRequested": true,
  "destroyed": true
}
```

`destroyed` is expected for interruptible Rhino code and is asserted by the
instrumentation POC. If a future Rhino source cannot be interrupted, that must
be handled by a stronger process-isolation gate before promotion.

## Console Summary

The POC installs a local Rhino `console` object with `log`, `info`, `warn`, and
`error`. The returned summary is bounded:

- maximum 32 lines;
- maximum 8192 UTF-8 bytes;
- maximum 1024 characters per captured line;
- `truncated: true` once a bound is hit.

No V8 global console or Rhino-era global APIs are installed by this adapter.

## Default And Packaged State

Default behavior remains fail-closed:

- `rhino.run({ source, timeoutMs, args })` without `experimental: true` rejects
  with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- `rhino.install()` still throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`;
- `require("node:rhino")` remains unavailable;
- local `node_modules/rhino` cannot shadow the controlled module;
- no `compat.rhino` surface is added;
- no Rhino globals are installed into V8.

P12-26 remains responsible for packaged metadata behavior. Packaged APKs
without explicit shim metadata still cannot claim promoted Rhino execution
support, and the P12-25 POC should be treated as an embedded/live-provider
adapter only.

## Evidence

- Runtime facade and Node-side request/result normalization:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Android bridge dispatch:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Bridge capability policy:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt`
- Stable error codes:
  `app/src/main/java/org/autojs/autojs/engine/NodeRuntimeErrorCodes.kt`
- Android Rhino provider:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeRhinoExecutionService.kt`
- Service registration:
  `app/src/main/AndroidManifest.xml`
- Instrumentation evidence:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt`
- Type declarations:
  `docs/nodejs/types/autojs6-node/rhino.d.ts`
- Type smoke:
  `sample/nodejs/typescript-smoke/src/main.ts`
- Host verifier:
  `tools/nodejs/phase12/verify-rhino-compat-rhino-json-only-execution-adapter-poc.js`
- Gradle gate:
  `:app:verifyNodeRhinoCompatRhinoJsonOnlyExecutionAdapterPoc`
