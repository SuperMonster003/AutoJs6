# Rhino JSON-Only Shim POC Gate

Status: P12-23 POC gate established.

P12-23 turns the S11-26 Rhino JSON-only shim design into a Phase 12 POC gate.
It does not enable Rhino execution by itself. P12-24 follows by reserving
`require("rhino")` as a disabled facade; default and release Safe Node Profile
behavior still has no executable `rhino.run()` without an explicit
`experimental: true` request, no supported `rhino.install()`, no
`compat.rhino`, and no Rhino globals installed into V8. P12-25 consumes this
gate by adding the explicit JSON-only execution adapter POC.

## Gate Decision

The POC API shape remains explicit and JSON-only:

```js
const rhino = require("rhino");

const result = await rhino.run({
  source: "var input = arguments[0]; ({ value: input.value + 1 })",
  args: [{ value: 41 }],
  timeoutMs: 5000,
});
```

The scoped file variant remains:

```js
const result = await rhino.run({
  path: "legacy/snippet.js",
  args: [{ value: 41 }],
  timeoutMs: 5000,
});
```

This was a future POC target in P12-23. P12-24 owns the default-disabled
runtime denial path, P12-25 now owns the execution adapter POC, and P12-26 owns
packaged metadata behavior.

## Default State

- Release/default Safe Node Profile must reject the shim unless a request
  carries `experimental: true`.
- POC execution can only be considered when an explicit experimental flag or
  test metadata opts in. This is the explicit experimental flag or test metadata
  rule. P12-25 implements the runtime side of that rule with
  `rhino.run({ experimental: true, source|path, timeoutMs, args })`.
- Source text containing `require("rhino")` must not implicitly reserve Android
  manifest entries, receivers, services, permissions, or providers.
- P12-24 reserves `require("rhino")` as a controlled module name so a local
  `node_modules/rhino` package cannot shadow the disabled facade.
- `require("node:rhino")` remains unavailable.

## Required Denials

`rhino.install()` is never part of the JSON-only POC. It must reject with:

```text
ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED
```

The POC must not install or expose these V8 globals:

- no `rhino.install()`
- `importClass`
- `importPackage`
- `Packages`
- `java`
- `android`
- `JavaAdapter`
- old AutoJs globals such as `toast`, `files`, `images`, selector globals, or
  `threads`

The POC must not expose raw Java, Android, Rhino, thread, lock, image, UI,
accessibility, process, native pointer, file descriptor, or bridge handle
objects to Node.

## JSON Validator Gate

Before dispatch, the POC validator must accept only JSON-safe snapshots and
must reject:

| Denied value | Required behavior |
| --- | --- |
| `undefined` | `ERR_AUTOJS6_RHINO_SHIM_JSON_DENIED` or invalid-request denial. |
| function | Stable JSON-denied error; no source dispatch. |
| symbol | Stable JSON-denied error; no source dispatch. |
| `bigint` | Stable JSON-denied error; no source dispatch. |
| cycle | Stable JSON-denied error; no source dispatch. |
| prototype object or class instance | Stable JSON-denied error; no source dispatch. |
| Java object marker | Stable Java-object-denied error; no source dispatch. |
| Rhino `Scriptable` marker | Stable Java-object-denied error; no source dispatch. |
| Android object marker | Stable Java-object-denied error; no source dispatch. |
| image/UI/accessibility handle marker | Stable Java-object-denied error; no source dispatch. |
| thread/lock/process/native pointer/file descriptor marker | Stable Java-object-denied error; no source dispatch. |
| oversized payload, excessive depth, array length, or object key count | Stable invalid-request or JSON-denied error; no source dispatch. |

Returned values from Rhino must pass the same JSON-safe validator before they
are visible to Node.

## Execution Boundary Gate

Any POC execution adapter must prove:

- `timeoutMs` is required, finite, positive, and capped before dispatch;
- exactly one of `source` or `path` is provided;
- `path` is scoped project-relative and rejects absolute paths, `..`, NUL, and
  symlink/realpath escapes;
- Rhino execution is owned by the script manager or a dedicated compatibility
  process, not by the V8 isolate. This is the script manager or a dedicated compatibility process boundary;
- parent Node stop, service destroy, user stop, timeout, and provider cleanup
  cancel active Rhino runs;
- stdout/stderr capture is bounded and attributed to the Rhino execution id;
- Rhino exceptions become stable JSON-safe Node errors;
- concurrent runs are bounded and overflow rejects before creating engines;
- diagnostics record mixed-engine run count, denial count, timeout count,
  cancellation count, cleanup count, and active-run count.

## Packaged Gate

Packaged support remains fail-closed until P12-26:

- APKs without explicit shim metadata cannot execute the shim.
- Metadata may authorize only JSON-only `run`, not `install()` or globals.
- Packaged timeout, crash, cancellation, and destroy behavior require evidence
  before promotion.

## Current P12-24/P12-25 Exposure

After P12-25 the runtime and type boundary is:

- runtime `require("rhino")` returns only a frozen disabled facade;
- runtime `rhino.run()` without `experimental: true` rejects with
  `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- runtime `rhino.run({ experimental: true, source|path, timeoutMs, args })`
  dispatches to the Android Rhino JSON-only execution adapter POC;
- runtime `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`;
- `require.resolve("rhino")` resolves to the controlled module name `rhino`;
- local `node_modules/rhino` cannot shadow the controlled module;
- `require("node:rhino")` remains unavailable;
- no runtime `compat.rhino`;
- TypeScript declares the disabled `rhino` facade plus explicit P12-25
  `RunRequest` / `RunResult` POC types;
- no `compat.rhino` type surface.

The P12-25 implementation report is
`docs/nodejs/reports/phase12/RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md`.

## Verification

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoCompatRhinoJsonOnlyShimPocGate
```

The gate checks this report, the S11-26 design docs, migration/security docs,
the P12-24 disabled facade boundary, Phase 12 surface manifest evidence, and
the P12-23 checklist entry.
