# Rhino Compatibility Shim Design Gate

Status: design gate plus disabled runtime facade and P12-25 JSON-only execution
adapter POC. Embedded Node scripts can `require("rhino")` to receive the P12-24
disabled facade; only calls with `experimental: true` can enter the P12-25 POC.
They still cannot install Rhino globals by default, use Rhino `importClass` /
`importPackage` semantics, use `JavaAdapter`, or access Rhino Java bridge
objects from Node. P13-14 adds an explicit experimental Java proxy installer for
`Packages` / `java` / `android` that routes through `JAVA_INTEROP.md` instead
of exposing Rhino Java objects. P13-15 exposes explicit
`rhino.importClass()` / `rhino.importPackage()` helpers only as stable
`ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED` denials with assignment-style migration
guidance. S11-26 fixes the concrete JSON-only run shape in
[RHINO_JSON_ONLY_SHIM.md](RHINO_JSON_ONLY_SHIM.md), P12-23 records the
default-disabled Phase 12 POC gate in
`docs/nodejs/reports/phase12/RHINO_JSON_ONLY_SHIM_POC_GATE.md`, P12-24 reserves
the default denial path, P12-25 implements the explicit adapter POC, and P12-26
records the packaged metadata gate as fail-closed.
P13-16 adds an explicit `rhino.JavaAdapter()` denial with
`ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED` and reserves
`java.defineClass()` / `$autojs.java.defineClass()` as the future replacement
name, but does not implement proxy-interface callbacks yet.

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

Do not implement default Rhino execution in the Embedded Node runtime.

The following remain unavailable in Node scripts:

- Rhino execution through `rhino.run()` without `experimental: true`
- `require("rhino").install()` without `experimental: true`
- global `importClass`
- global `importPackage`
- global `Packages` unless installed by the P13-14 explicit Java proxy POC
- global `java.*` unless installed by the P13-14 explicit Java proxy POC
- global `android.*` unless installed by the P13-14 explicit Java proxy POC
- `JavaAdapter`
- old AutoJs globals installed into Node

Users should migrate Rhino scripts to explicit Node bridge modules where possible.

P12-24 reserves `require("rhino")` as a controlled disabled facade. The facade is
frozen, reports `enabled: false`, returns `ERR_AUTOJS6_RHINO_SHIM_DISABLED` from
`rhino.run()` calls that lack `experimental: true`, throws
`ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED` from default `rhino.install()` calls,
and does not install globals unless P13-14's explicit Java proxy experiment is
requested and Java interop is enabled.

## S11-26 JSON-Only Module Candidate

A future module, if implemented, should be explicit and JSON-only:

```js
const rhino = require("rhino");
```

The module is present by default as the P12-24 disabled facade. P12-25 defines
the explicitly gated execution behavior and tests.

Possible future shape:

```js
const rhino = require("rhino");

const result = await rhino.run({
  experimental: true,
  source: "({ ok: true })",
  timeoutMs: 5000,
  args: []
});
```

This shape is intentionally different from installing globals into Node. It
makes the engine boundary visible and keeps legacy execution isolated. The
current profile exposes the disabled `require("rhino")` facade plus the P12-25
`experimental: true` JSON-only execution adapter POC; default
`rhino.install()` remains unsupported, while P13-14 allows only an explicit
experimental Java proxy install for `Packages`, `java`, and `android` when the
Java interop gate is enabled. It still does not expose `compat.rhino` or install
old Rhino globals. S11-26 records the API and gate plan, P12-24 records the
stable default denial path, and P12-25 records the POC evidence in
`docs/nodejs/reports/phase12/RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md`.

The detailed request/response shape, JSON-safe value definition, timeout and cancellation requirements, packaging policy, and POC gate plan live in [RHINO_JSON_ONLY_SHIM.md](RHINO_JSON_ONLY_SHIM.md).

## API Decisions

| Surface | Decision | Reason |
| --- | --- | --- |
| `require("rhino")` | Disabled facade | Reserved by P12-24 so scripts get stable denial instead of npm shadowing. |
| `rhino.install()` | Unsupported by default; P13-14 explicit Java proxy POC | Installing old globals into Node hides migration work and can bypass bridge policy, but the P13-14 proxy installs only `Packages` / `java` / `android` wrappers that route through the Java allowlist experiment. |
| Old global AutoJs APIs | Unsupported by default | Node code should use explicit bridge modules such as `toast`, `app`, `image`, and `accessibility`. |
| `importClass` / `importPackage` | Stable unsupported helpers in Node | P13-15 keeps global helpers absent and exposes only `rhino.importClass()` / `rhino.importPackage()` denials with `ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`; assignment from the P13-14 proxy target is the migration path. |
| `Packages`, `java.*`, `android.*` | Explicit Java proxy POC only | P13-14 may install JavaScript proxies for allowlisted Java bridge calls; raw object graphs and class-loader surfaces remain denied. |
| `JavaAdapter` | Stable unsupported helper in Node | P13-16 exposes `rhino.JavaAdapter()` only as `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`; the future replacement must be a bounded `defineClass` or proxy-interface provider, not raw Rhino adapter parity. |
| Running a legacy Rhino script from Node | Future candidate | Must be explicit, serialized, timeout-bounded, and owned by the script manager. |
| `rhino.run({ experimental: true, source, timeoutMs, args })` | P12-25 provider POC | JSON-only request/response adapter; no V8 global installation; default runtime rejects calls without `experimental: true` with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`. |

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
disabled facade with `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`. P13-14 adds a
separate explicit `rhino.install({ experimental: true })` Java proxy POC that
requires the Java interop experiment.

Reasons:

- It would make Node scripts look like Rhino scripts while still running inside V8/libuv.
- It encourages implicit globals instead of explicit bridge imports.
- It can obscure which engine owns permissions and lifecycle.
- It creates confusing behavior for npm packages that expect normal Node globals.
- It risks exposing Java/Android objects that the Safe Node Profile deliberately denies.

`install()` must not become available merely because `rhino.run()` is enabled.
The P13-14 install-like helper is separately reviewed and must not install
`importClass`, `importPackage`, `JavaAdapter`, selector globals, image globals,
filesystem globals, or unrestricted Java bridge globals. Its `Packages`,
`java`, and `android` values are proxies over the existing Java allowlist.

## `importClass()` / `importPackage()` Policy

P13-15 decides not to emulate Rhino's dynamic import helpers inside Node.
`require("rhino").importClass()` and `require("rhino").importPackage()` are
present only so scripts receive a stable
`ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED` error and migration text. They are never
installed on `globalThis`, even after `rhino.install({ experimental: true })`.

Use explicit assignment from a proxy target instead:

```js
const rhino = require("rhino");
const scope = {};

rhino.install({ experimental: true, target: scope });

const Rect = scope.android.graphics.Rect;
const MathClass = scope.Packages.java.lang.Math;
```

This keeps the imported names local to the current module and preserves the
P13-13 Java allowlist checks for every constructor or method call.

## `JavaAdapter` / `defineClass` Policy

P13-16 decides not to emulate Rhino `JavaAdapter` inside V8. `rhino.JavaAdapter()`
is present only so migrated scripts receive
`ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED` instead of an accidental missing
property or npm shadow. It is never installed on `globalThis`, even after
`rhino.install({ experimental: true })`.

The reserved migration direction is `java.defineClass()` /
`$autojs.java.defineClass()` or a proxy-interface API owned by the Java bridge
provider. In the current phase those methods throw `ERR_AUTOJS6_JAVA_METHOD_DENIED`
and do not dispatch to the bridge. A future implementation must prove bounded
callback lifecycle, UI/main-thread dispatch where needed, no raw Java callback
handle leakage, no unbounded inheritance, and no class-loader or dex/jar loading.

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
- unrestricted `Packages`
- unrestricted `java.*`
- unrestricted `android.*`
- `JavaAdapter`
- raw Java object returns
- Android `Context`, `Activity`, `Service`, Binder, `AccessibilityService`, `MediaProjection`, or `View` returns

If a legacy Rhino snippet needs Java access, the snippet may use it inside Rhino only if the existing Rhino engine allows it. The result returned to Node must still be serialized JSON and must not contain Java object references.

## Packaged APK Policy

Packaged APKs must not gain a Rhino shim merely because they include Embedded Node.

P12-26 records the current packaged metadata gate as fail-closed. No packaged
metadata shape is accepted in Phase 12, and the `rhino` provider is published
with `packagedSupport.status = unsupported`. Packaged APK execution remains
rejected until a later phase proves metadata preflight, packaged timeout,
packaged crash containment, and destroy evidence.

Future packaged support must declare:

- whether Rhino runtime classes are packaged
- whether a Rhino compatibility service is present
- whether the shim can run in `rhinoOnly`, `nodeFull`, and `nodeMini` release flavors
- how legacy source files are included or encrypted
- which bridge permissions are required
- how diagnostics report mixed Rhino/Node executions

No packaged manifest entry, permission, or receiver should be injected only because a project uses `require("rhino")` in source text. The packaged metadata must opt in explicitly.
That future metadata may authorize only JSON-only run; it must not authorize
unrestricted `rhino.install()`, `compat.rhino`, or Rhino globals. P13-14's Java
proxy form still requires separate packaged metadata and remains fail-closed in
packaged APKs.

## Errors

Future denials should use stable errors:

- `ERR_AUTOJS6_RHINO_SHIM_DISABLED`
- `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`
- `ERR_AUTOJS6_RHINO_GLOBAL_UNSUPPORTED`
- `ERR_AUTOJS6_RHINO_JAVA_OBJECT_DENIED`
- `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`

P12-24 current behavior is a disabled facade for `require("rhino")`;
`rhino.run()` without `experimental: true` rejects with
`ERR_AUTOJS6_RHINO_SHIM_DISABLED`, `rhino.install()` throws
`ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`, and `require("node:rhino")` remains
unavailable. P12-25 implements the JSON-only execution adapter POC for explicit
`experimental: true` requests only. P12-26 keeps packaged APK support
fail-closed.

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
