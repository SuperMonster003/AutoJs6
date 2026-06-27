# Rhino `s13n.bytes` Promotion

Status: P12-32 `s13n.bytes` pure utility pilot landed.

P12-32 promotes the smallest P12-31 utility candidate that can be implemented
without a new Android provider, permission boundary, resource bundle, or raw
Rhino/Android object contract.

## Runtime Surface

Supported:

| Rhino API | Node API | Status | Notes |
| --- | --- | --- | --- |
| `s13n.bytes(source)` | `require("s13n").bytes(source)` | `supported` | Converts string or numeric byte values to bytes. |
| `s13n.bytes(source, fromUnit)` | `require("s13n").bytes(source, fromUnit)` | `supported` | Uses the existing byte parser and returns a number in `B`. |
| `s13n.bytes(...)` | `require("autojs6:compat").s13n.bytes(...)` | `supported` | Same implementation exposed through the Rhino compatibility facade. |

The implementation is equivalent to:

```js
converter.bytes(source, fromUnit, "B", { fractionDigits: 0 });
```

Explicit global installation through `compat.install({ globals: true })` can
install `s13n`, but `require("autojs6:compat")` still does not install Rhino
globals by default.

## Stable Unsupported Siblings

The sibling helpers remain available only as stable unsupported facades:

| API | Status | Reason |
| --- | --- | --- |
| `s13n.color(value)` | `unsupported` | Needs a JSON-safe color contract before migration guidance can claim parity. |
| `s13n.point(value)` | `unsupported` | Needs a JSON-safe point contract and overload policy. |
| `s13n.throwable(value)` | `unsupported` | Must not expose raw Java/Rhino exception objects. |
| `s13n.time(value, fromUnit, toUnit)` | `unsupported` | Needs a separate time-unit and formatting contract. |

Each rejected sibling throws `ERR_AUTOJS6_RHINO_COMPAT_UNSUPPORTED` instead of
silently returning a partial Rhino object.

## Evidence

- Runtime facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Direct types: `docs/nodejs/types/autojs6-node/s13n.d.ts`
- Compat types: `docs/nodejs/types/autojs6-node/rhino_compat.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Status manifest: `docs/nodejs/rhino-compat/status-manifest.json`
- Matrix: `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- Migration guide: `docs/nodejs/MIGRATION_FROM_RHINO.md`
- Surface manifest: `docs/nodejs/rhino-compat/phase12-surface-manifest.json`
- Verifier: `tools/nodejs/phase12/verify-rhino-compat-s13n-bytes-promotion.js`
- Gradle gate: `:app:verifyNodeRhinoCompatS13nBytesPromotion`

## Non-Goals

- No whole-module `s13n` parity claim.
- No raw Java, Rhino, Android, color, point, throwable, or time objects.
- No provider authority is added for this pure helper.
- No change to the default no-global-injection behavior of `autojs6:compat`.
