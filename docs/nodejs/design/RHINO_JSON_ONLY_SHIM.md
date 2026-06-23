# Rhino JSON-Only Shim

Status: S11-26 design and POC gate plan. No runtime module is enabled by this
document.

## Decision

The only acceptable future Rhino shim shape is an explicit, isolated,
JSON-only run API. The preferred module name for a future implementation is:

```js
const rhino = require("rhino");

const result = await rhino.run({
  source: "var input = arguments[0]; ({ value: input.value + 1 })",
  args: [{ value: 41 }],
  timeoutMs: 5000
});
```

Current Node Profile v1.1 behavior remains unchanged:

- no `require("rhino")`;
- no `rhino.run()`;
- no `rhino.install()`;
- no `compat.rhino`;
- no Rhino globals installed into V8;
- no `importClass`, `importPackage`, `Packages`, `java.*`, `android.*`, or
  `JavaAdapter` in Node.

This shim is a migration escape hatch for legacy snippets that cannot yet be
rewritten to Node bridge modules. It is not a way to make Node behave like
Rhino.

## API Shape

Future POC API:

```ts
type JsonValue =
  | null
  | boolean
  | number
  | string
  | readonly JsonValue[]
  | { readonly [key: string]: JsonValue };

interface RhinoRunRequest {
  source?: string;
  path?: string;
  args?: JsonValue | readonly JsonValue[];
  timeoutMs: number;
  cwd?: string;
  permissions?: readonly string[];
  captureStdout?: boolean;
  captureStderr?: boolean;
}

interface RhinoRunResult {
  schema: "autojs6-rhino-run-result-v1";
  engine: "rhino";
  ok: true;
  executionId: string;
  value: JsonValue | null;
  stdout?: string;
  stderr?: string;
  durationMs: number;
}
```

Exactly one of `source` or `path` must be provided. `source` is inline legacy
Rhino JavaScript. `path` is a scoped project-relative path. `timeoutMs` is required;
the POC must reject missing, infinite, zero, negative, or excessive timeouts
before dispatch.

`args` must be a JSON-safe snapshot. The POC must reject:

- `undefined`;
- functions;
- symbols;
- `bigint`;
- `Date`, `RegExp`, `Map`, `Set`, class instances, and prototype-bearing
  objects;
- `ArrayBuffer`, typed arrays, `Buffer`, streams, file handles, image handles,
  UI handles, accessibility node handles, and other opaque Node bridge handles;
- Java objects, Rhino `Scriptable`, Android objects, native pointers, threads,
  locks, and process handles;
- cycles, excessive depth, excessive array length, excessive object key count,
  and oversized serialized payloads.

The success value returned from Rhino must also be JSON-safe. Non-JSON return values fail
with a stable error instead of becoming live wrappers. A Rhino snippet
may create Java or Android objects internally only if the ordinary Rhino engine
would allow it, but those objects must not cross back to Node.

## Error Shape

Future denials should use stable codes:

| Code | Use |
| --- | --- |
| `ERR_AUTOJS6_RHINO_SHIM_DISABLED` | `require("rhino")` or `rhino.run()` is not enabled in the current profile. |
| `ERR_AUTOJS6_RHINO_SHIM_INVALID_REQUEST` | Request shape, `source`/`path`, `args`, timeout, or payload limits are invalid. |
| `ERR_AUTOJS6_RHINO_SHIM_TIMEOUT` | Rhino execution exceeded `timeoutMs` and was cancelled. |
| `ERR_AUTOJS6_RHINO_SHIM_CANCELLED` | Parent Node execution, service destroy, user stop, or provider cleanup cancelled the run. |
| `ERR_AUTOJS6_RHINO_SHIM_JSON_DENIED` | Input or output is not JSON-safe. |
| `ERR_AUTOJS6_RHINO_SHIM_JAVA_OBJECT_DENIED` | Rhino tried to return a Java, Android, Rhino `Scriptable`, native, thread, lock, or handle object. |
| `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED` | `rhino.install()` is requested. |

Errors visible to Node must be JSON-safe objects. Stack traces may be included
only as strings and must identify the Rhino execution id. The Node stack and
Rhino stack must not expose live engine objects.

## Engine Boundary

`rhino.run()` must never execute Rhino inside the V8 isolate. The script manager owns engine creation,
cancellation, logging, and lifecycle. A dedicated compatibility process remains
the preferred containment model.

Required POC boundary:

- Node submits a JSON request envelope to an engine owner.
- The engine owner creates an isolated Rhino execution or a dedicated
  compatibility process.
- The run has its own execution id, timeout, log capture, and cleanup hooks.
- Parent Node stop, service destroy, timeout, and explicit cancellation must
  cancel the Rhino run.
- The Node side receives only JSON snapshots and stable error objects.
- No Java object, Rhino `Scriptable`, Android object, function, closure,
  thread, lock, native pointer, image handle, UI handle, accessibility node
  handle, process handle, or file descriptor crosses into Node.

The first implementation may reuse the existing in-process Rhino engine only as
a transitional POC. If it does, the gate must explicitly prove timeout,
cancellation, and cleanup behavior. A future dedicated process is preferred for
crash containment and clearer lifecycle ownership.

## Permission And Packaging Policy

`rhino.run()` must not grant new authority merely because Node source imports
the module. The POC must choose one of these explicit permission models before
runtime enablement:

- inherit only the parent Node execution capabilities and reject Rhino APIs
  outside that capability set; or
- require a separate packaged/project metadata opt-in listing allowed Rhino
  bridge families.

In either model:

- packaged APKs must opt in through explicit metadata;
- missing metadata fails closed;
- no Android manifest entry, receiver, service, permission, or provider is
  injected only because source text contains `require("rhino")`;
- package diagnostics must report mixed Rhino/Node execution support and
  denials;
- raw Java/Android object return remains denied even when the Rhino snippet has
  internal Java access.

## Module Resolution Policy

Until a runtime implementation task reserves the name, `require("rhino")` remains unavailable
in the Safe Node Profile. When implemented, it must be a
controlled AutoJs module, not an npm package compatibility alias.

The implementation task must decide and test shadowing. The recommended policy
is that the controlled `rhino` module is reserved and a local
`node_modules/rhino` package cannot shadow it. This prevents scripts from
depending on different behavior between packaged and live runs.

There is no `node:rhino` alias.

## Relationship To Existing APIs

Prefer migration in this order:

1. Rewrite legacy code to explicit Node bridge modules.
2. Use `require("autojs6:compat")` for implemented low-risk Rhino API facades.
3. Use `engines.execScriptFile()` for whole-script dispatch when a legacy Rhino
   script should remain a separate script.
4. Consider future `rhino.run()` only for small, timeout-bounded snippets with
   JSON input and output.

`rhino.run()` must not expose or reuse `autojs6:compat` globals. It does not
install `toast`, `files`, `images`, selector globals, `threads`, Java bridge
globals, or any old AutoJs global into Node.

## POC Gate Plan

A future implementation gate must prove:

- default Safe Node Profile still denies `require("rhino")` or reports
  `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- `require("node:rhino")` is unavailable;
- local `node_modules/rhino` shadowing behavior is documented and tested;
- `rhino.install()` rejects with `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`;
- requiring the module does not mutate `globalThis`;
- `importClass`, `importPackage`, `Packages`, `java.*`, `android.*`, and
  `JavaAdapter` remain unavailable in V8;
- `rhino.run({ source, timeoutMs, args })` accepts only JSON-safe requests;
- `rhino.run({ path, timeoutMs, args })` validates scoped project-relative
  paths;
- success returns a JSON-safe `RhinoRunResult`;
- Rhino exceptions become stable JSON-safe Node errors;
- timeouts cancel Rhino execution and clean resources;
- parent Node stop, service destroy, and user stop cancel active runs;
- Java, Android, Rhino `Scriptable`, function, thread, lock, image, UI,
  accessibility, process, native pointer, and file descriptor returns are
  denied;
- stdout/stderr capture is bounded and attributed to the Rhino execution id;
- concurrent runs are bounded, and overflow rejects before creating engines;
- packaged APKs without explicit shim metadata fail closed;
- diagnostics record mixed-engine runs, denials, timeout, cancellation, and
  cleanup counts.

## S11-26 Acceptance

- The design fixes a concrete explicit API candidate:
  `rhino.run({ source, timeoutMs, args })` plus a scoped `path` variant.
- All input and output is JSON-safe.
- Rhino globals are not installed into V8.
- Current runtime and type declarations still do not expose `require("rhino")`,
  `rhino.run()`, `rhino.install()`, or `compat.rhino`.
- Follow-up implementation work has a POC gate plan before any runtime enablement.
