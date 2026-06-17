# Rhino Compatibility Shim Design Gate

Status: design gate only. No runtime behavior is enabled by this document. Embedded Node scripts cannot `require("rhino")`, install Rhino globals, call `importClass`, call `importPackage`, use `JavaAdapter`, or access Rhino Java bridge objects.

## Goal

Rhino compatibility for Node must be a migration aid, not a hidden compatibility layer that changes the Safe Node Profile.

Any future shim must preserve:

- Rhino scripts continue to run through the existing Rhino engine path.
- Node scripts do not get Rhino globals by default.
- Java and Android object access does not leak from Rhino into Node.
- Legacy APIs are exposed only through explicit migration metadata or an explicit module.
- Results crossing from Rhino to Node are serialized JSON snapshots.
- Script lifecycles, timeouts, logs, and cancellation remain attributable to the correct engine.

## Current Decision

Do not implement a Rhino shim in the Embedded Node runtime yet.

The following remain unavailable in Node scripts:

- `require("rhino")`
- `require("rhino").install()`
- global `importClass`
- global `importPackage`
- global `Packages`
- global `java.*`
- global `android.*`
- `JavaAdapter`
- old AutoJs globals installed into Node

Users should migrate Rhino scripts to explicit Node bridge modules where possible.

## Possible Module

A future module, if implemented, could be named:

```js
const rhino = require("rhino");
```

The module must not be present by default until a later implementation task defines the exact API and tests.

Possible future shape:

```js
const rhino = require("rhino");

const result = await rhino.run({
  source: "toast('legacy')",
  timeoutMs: 5000,
  args: []
});
```

This shape is intentionally different from installing globals into Node. It makes the engine boundary visible and keeps legacy execution isolated.

## API Decisions

| Surface | Decision | Reason |
| --- | --- | --- |
| `require("rhino")` | Deferred | Needs lifecycle, permission, and serialization design. |
| `rhino.install()` | Unsupported by default | Installing old globals into Node hides migration work and can bypass bridge policy. |
| Old global AutoJs APIs | Unsupported by default | Node code should use explicit bridge modules such as `toast`, `app`, `image`, and `accessibility`. |
| `importClass` / `importPackage` | Unsupported in Node | They imply broad Java class access and conflict with `JAVA_INTEROP.md`. |
| `Packages`, `java.*`, `android.*` | Unsupported in Node | They expose raw object graphs and class-loader surfaces. |
| `JavaAdapter` | Unsupported | It creates cross-runtime proxy objects with complex lifetime and threading semantics. |
| Running a legacy Rhino script from Node | Future candidate | Must be explicit, serialized, timeout-bounded, and owned by the script manager. |

## Recommended Direction

Do not install old Rhino globals into the Node global object.

Prefer this migration order:

1. Rewrite legacy script entry points to Node modules and bridge APIs.
2. Use the existing `engines` bridge for explicit script dispatch where a separate Rhino script must remain.
3. Consider a future `require("rhino")` only as an explicit compatibility module that runs isolated Rhino snippets or files.
4. Keep Java interop separate from Rhino shim behavior; see `docs/nodejs/design/JAVA_INTEROP.md`.

## Isolation Model

If a Rhino shim is implemented later, it should not execute inside the Node V8 isolate.

Required boundaries:

- Rhino execution uses the existing Rhino engine or a dedicated compatibility process.
- Node sends a JSON request describing source, file path, args, cwd, timeout, and permissions.
- The script manager owns engine creation, cancellation, logs, and lifecycle.
- Node receives only JSON-compatible results, stdout/stderr summaries, and stable error objects.
- Java objects, Rhino `Scriptable`, Android objects, functions, threads, and native handles never cross into Node.

If a separate process is used, it should have its own service lifecycle and crash containment. If the existing main-process Rhino engine is reused first, that must be documented as a transitional implementation with strict timeout and cancellation coverage.

## `install()` Policy

`rhino.install()` should remain unsupported by default.

Reasons:

- It would make Node scripts look like Rhino scripts while still running inside V8/libuv.
- It encourages implicit globals instead of explicit bridge imports.
- It can obscure which engine owns permissions and lifecycle.
- It creates confusing behavior for npm packages that expect normal Node globals.
- It risks exposing Java/Android objects that the Safe Node Profile deliberately denies.

If `install()` is ever added, it must require an explicit experimental flag and should install only harmless compatibility aliases. It must not install Java bridge globals, `importClass`, `Packages`, selector globals, image globals, or filesystem globals.

## Legacy Global Policy

Old AutoJs globals should map to explicit modules instead of being installed:

| Rhino global | Node migration |
| --- | --- |
| `toast()` | `require("toast").showToast()` |
| `app` | `require("app")` |
| `dialogs` | `require("dialogs")` |
| `files` | scoped `require("fs")` or `require("fs/promises")` |
| `images` | `require("image")` or `require("images")` |
| `requestScreenCapture()` | `require("media_projection")` |
| selector globals such as `text()` | `require("accessibility")` selector descriptors |
| `threads` | Promise-based Node flow or experimental `worker_threads` when explicitly enabled |
| `shell()` | controlled `require("shell").exec()` |
| Java globals | no default migration; see `JAVA_INTEROP.md` |

## Java Boundary

Rhino Java access must not be used as a shortcut around Node Java interop policy.

Denied by default:

- `importClass`
- `importPackage`
- `Packages`
- `java.*`
- `android.*`
- `JavaAdapter`
- raw Java object returns
- Android `Context`, `Activity`, `Service`, Binder, `AccessibilityService`, `MediaProjection`, or `View` returns

If a legacy Rhino snippet needs Java access, the snippet may use it inside Rhino only if the existing Rhino engine allows it. The result returned to Node must still be serialized JSON and must not contain Java object references.

## Packaged APK Policy

Packaged APKs must not gain a Rhino shim merely because they include Embedded Node.

Future packaged support must declare:

- whether Rhino runtime classes are packaged
- whether a Rhino compatibility service is present
- whether the shim can run in `rhinoOnly`, `nodeFull`, and `nodeMini` release flavors
- how legacy source files are included or encrypted
- which bridge permissions are required
- how diagnostics report mixed Rhino/Node executions

No packaged manifest entry, permission, or receiver should be injected only because a project uses `require("rhino")` in source text. The packaged metadata must opt in explicitly.

## Errors

Future denials should use stable errors:

- `ERR_AUTOJS6_RHINO_SHIM_DISABLED`
- `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`
- `ERR_AUTOJS6_RHINO_GLOBAL_UNSUPPORTED`
- `ERR_AUTOJS6_RHINO_JAVA_OBJECT_DENIED`
- `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`

Current behavior should be a normal unavailable module error for `require("rhino")` until a runtime task deliberately reserves the module name.

## Test Gate For Future Implementation

Any implementation task must include:

- default `require("rhino")` denial or disabled error
- `node_modules/rhino` shadowing decision documented and tested
- `rhino.install()` default rejection
- no old globals installed by default
- `importClass`, `importPackage`, `Packages`, `JavaAdapter`, `java.*`, and `android.*` unavailable in Node by default
- explicit Rhino run success for a safe snippet if `rhino.run()` is implemented
- timeout and cancellation of Rhino executions launched from Node
- no Java object returned from Rhino to Node
- packaged APK verifier behavior for any shim metadata
- migration docs updated with examples

## Acceptance Gate

The Rhino shim may move beyond this design only when:

- the module name and API are fixed
- the security model describes the engine and process boundary
- the migration guide explains when to rewrite versus when to shim
- tests prove old globals are not installed by default
- Java object leakage is denied
- packaged APK behavior is fail-closed
