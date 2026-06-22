# Rhino Compatibility Loader

## Decision

Phase 11 Rhino compatibility uses one explicit Node entry:

```js
const compat = require("autojs6:compat");
```

The loader is an AutoJs6 controlled module, not an npm package name and not a script directive. It is resolved by the embedded Safe Node Profile loader in the same family as `autojs6:bridge` and `autojs6:lifecycle`.

## Rejected Entrypoints

| Entrypoint | Decision | Reason |
| --- | --- | --- |
| `"nodejs compat"` directive | Rejected for Phase 11A | Directives are too implicit and make source scanning/package behavior harder to audit. |
| `require("rhino-compat")` | Reserved | The bare name can collide with user dependencies and suggests ordinary npm resolution. |
| default Rhino globals | Rejected | It would silently change Node global state and make migration failures harder to diagnose. |
| `require("autojs6:compat")` | Selected | The `autojs6:` namespace is already reserved for controlled runtime modules and makes opt-in explicit. |

## Runtime Contract

S11-08 only installs the loader stub. It does not expose Rhino globals, raw Java objects, Rhino `Scriptable`, Android `View`, `AccessibilityNodeInfo`, or Pro 9 `require("rhino").install()` behavior.

The first runtime shape is:

```js
const compat = require("autojs6:compat");

compat.status();
compat.install({ globals: false });
compat.unsupported("files.read");
compat.blockedByDesign("importClass");
compat.assertSupported("toast");
```

`compat.install()` is intentionally explicit. In S11-08 it returns an install result with `installed: false` when no globals are requested. `compat.install({ globals: true })` throws until later facade tasks implement a concrete set of globals.

Future tasks should extend the module in small commits:

- S11-09: `toast`, `toastLog`, `sleep`, `setClip`, `getClip`, and basic globals.
- S11-10: scoped `files` facade.
- S11-11 to S11-14: pure utilities, text utilities, storage/sqlite, console/timers/error compatibility.

## Error Codes

The loader owns a stable Rhino compat error-code namespace:

| Code | Use |
| --- | --- |
| `ERR_AUTOJS6_RHINO_COMPAT_INVALID_OPTION` | Loader or facade options are malformed. |
| `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` | A Rhino-style API is not implemented yet in Node compat mode. |
| `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN` | The requested API violates the Safe Node Profile boundary. |

All errors should include `module: "autojs6:compat"`. API-specific errors should also include `api`.

## Global Policy

Loading the module must not mutate `globalThis`. A script must make a second explicit call before any Rhino-style global can be installed. S11-08 verifies this by scanning the runtime stub for default global writes.

When later tasks add globals, they must keep these rules:

- no global install during `require("autojs6:compat")`;
- no raw Java/Rhino/Android object exposure;
- no aliases for `importClass`, `importPackage`, `JavaAdapter`, or unrestricted Java interop;
- unsupported or blocked APIs return stable `ERR_AUTOJS6_RHINO_COMPAT_*` errors with migration hints.

## Type Surface

The checked-in type declaration is `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`. It declares only the loader and error helpers. Real facade methods should be added with the Task that implements them.
