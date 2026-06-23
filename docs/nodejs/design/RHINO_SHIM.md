# Rhino Compatibility Shim Design Gate

Status: design gate plus disabled runtime facade. Embedded Node scripts can `require("rhino")` only to receive the P12-24 disabled facade; they still cannot execute Rhino, install Rhino globals, call `importClass`, call `importPackage`, use `JavaAdapter`, or access Rhino Java bridge objects. S11-26 fixes the concrete future JSON-only run shape in [RHINO_JSON_ONLY_SHIM.md](RHINO_JSON_ONLY_SHIM.md), P12-23 records the default-disabled Phase 12 POC gate in `docs/nodejs/reports/phase12/RHINO_JSON_ONLY_SHIM_POC_GATE.md`, and P12-24 reserves the default denial path.

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

Do not implement Rhino execution in the Embedded Node runtime yet.

The following remain unavailable in Node scripts:

- Rhino execution through `rhino.run()`
- `require("rhino").install()`
- global `importClass`
- global `importPackage`
- global `Packages`
- global `java.*`
- global `android.*`
- `JavaAdapter`
- old AutoJs globals installed into Node

Users should migrate Rhino scripts to explicit Node bridge modules where possible.

P12-24 reserves `require("rhino")` as a controlled disabled facade. The facade is
frozen, reports `enabled: false`, returns `ERR_AUTOJS6_RHINO_SHIM_DISABLED` from
`rhino.run()`, throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED` from
`rhino.install()`, and does not install globals.

## S11-26 JSON-Only Module Candidate

A future module, if implemented, should be explicit and JSON-only:

```js
const rhino = require("rhino");
```

The module is present by default only as the P12-24 disabled facade until a
later adapter task defines execution behavior and tests.

Possible future shape:

```js
const rhino = require("rhino");

const result = await rhino.run({
  source: "toast('legacy')",
  timeoutMs: 5000,
  args: []
});
```

This shape is intentionally different from installing globals into Node. It makes the engine boundary visible and keeps legacy execution isolated. The current profile exposes only the disabled `require("rhino")` facade; it still does not execute `rhino.run()`, support `rhino.install()`, expose `compat.rhino`, or install Rhino globals. S11-26 records the future API and gate plan, while P12-24 records the stable default denial path.

The detailed request/response shape, JSON-safe value definition, timeout and cancellation requirements, packaging policy, and POC gate plan live in [RHINO_JSON_ONLY_SHIM.md](RHINO_JSON_ONLY_SHIM.md).

## API Decisions

| Surface | Decision | Reason |
| --- | --- | --- |
| `require("rhino")` | Disabled facade | Reserved by P12-24 so scripts get stable denial instead of npm shadowing. |
| `rhino.install()` | Unsupported by default | Installing old globals into Node hides migration work and can bypass bridge policy. |
| Old global AutoJs APIs | Unsupported by default | Node code should use explicit bridge modules such as `toast`, `app`, `image`, and `accessibility`. |
| `importClass` / `importPackage` | Unsupported in Node | They imply broad Java class access and conflict with `JAVA_INTEROP.md`. |
| `Packages`, `java.*`, `android.*` | Unsupported in Node | They expose raw object graphs and class-loader surfaces. |
| `JavaAdapter` | Unsupported | It creates cross-runtime proxy objects with complex lifetime and threading semantics. |
| Running a legacy Rhino script from Node | Future candidate | Must be explicit, serialized, timeout-bounded, and owned by the script manager. |
| `rhino.run({ source, timeoutMs, args })` | S11-26 candidate, P12-24 disabled | JSON-only request/response candidate; no V8 global installation; default runtime rejects execution with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`. |

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

`rhino.install()` remains unsupported by default and is rejected by the P12-24
disabled facade with `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`.

Reasons:

- It would make Node scripts look like Rhino scripts while still running inside V8/libuv.
- It encourages implicit globals instead of explicit bridge imports.
- It can obscure which engine owns permissions and lifecycle.
- It creates confusing behavior for npm packages that expect normal Node globals.
- It risks exposing Java/Android objects that the Safe Node Profile deliberately denies.

`install()` must not become available merely because a future experimental
`rhino.run()` adapter is enabled. If a future phase proposes any install-like
helper, it must be a separately reviewed API and must not install Java bridge
globals, `importClass`, `Packages`, selector globals, image globals, or
filesystem globals.

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

P12-24 current behavior is a disabled facade for `require("rhino")`;
`rhino.run()` rejects with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`, `rhino.install()`
throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`, and `require("node:rhino")`
remains unavailable.

## Test Gate For Future Implementation

Any implementation task must include:

- default `require("rhino")` disabled facade and `rhino.run()` denial
- `node_modules/rhino` shadowing decision documented and tested
- `rhino.install()` default rejection
- no old globals installed by default
- `importClass`, `importPackage`, `Packages`, `JavaAdapter`, `java.*`, and `android.*` unavailable in Node by default
- explicit Rhino run success for a safe snippet if `rhino.run()` is implemented
- JSON-only request validation for `source`, `path`, `args`, and result values
- `rhino.install()` rejection and no `compat.rhino`
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
