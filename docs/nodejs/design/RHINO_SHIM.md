# Explicit Rhino Compatibility Bridge

Status: supported explicit migration surface. It is not installed globally by
default.

The Node backend reserves `require("rhino")` so local packages cannot shadow
the compatibility bridge. Crossing from V8 into Rhino is always explicit:

```js
const rhino = require("rhino");

const result = await rhino.run({
  explicit: true,
  source: "1 + 2",
  timeoutMs: 1_000,
  args: {},
});
```

`rhino.run()` without `explicit: true` rejects with
`ERR_AUTOJS6_RHINO_SHIM_DISABLED`. This is a deliberate cross-engine consent
field, not a build feature gate.

## JSON-Only Execution

The bridge executes the scoped source or file in an Android Rhino engine outside
the V8 isolate. Requests and results must be JSON-safe snapshots. The boundary
rejects Java/Rhino objects, Android objects, functions, handles, native
pointers, processes, threads, locks, file descriptors, cycles, `undefined`,
`bigint`, symbols, and prototype-bearing object graphs. Timeouts, console
capture, source size, scoped paths, cancellation, and engine destruction are
bounded and diagnosed.

Exactly one of `source` or scoped `path` is accepted.

## Explicit Java Proxy Installation

When stable Java interop is available, an explicit installation can create
`Packages`, `java`, and `android` proxies on a caller-provided target:

```js
const scope = {};
require("rhino").install({ explicit: true, target: scope });
```

These proxies route through the same exact Java class/member allowlist and
opaque-handle provider as `require("java")`. They do not expose raw Rhino
objects, unrestricted packages, reflection, class loaders, native loading, or
Android context/UI objects.

AutoJs6 does not install proxies on `globalThis` automatically.
`importClass`, `importPackage`, and `JavaAdapter` remain unsupported with
stable error codes.

## Packaging

Packaged execution remains fail-closed unless the packaging contract explicitly
includes and validates the Rhino bridge metadata and its timeout/resource
policy. Project metadata cannot silently authorize global installation or
unrestricted Java access.

For migration of supported Rhino-style helpers, prefer
`require("autojs6:compat")`; it installs globals only when the script explicitly
calls `compat.install({ globals: true })`.
