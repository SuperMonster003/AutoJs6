# P13-27 Utility-Heavy Rhino Modules

Status: partial.

P13-27 is intentionally split. This landing promotes the pure JavaScript
`jsox.mathx` subset first and keeps resource-backed or raw Android object
surfaces behind later gates.

## Promoted Surface

- `require("jsox")`
- `require("jsox.mathx")`
- `require("autojs6:compat").jsox`
- optional `compat.install({ globals: true })` installs `globalThis.jsox`

The promoted `Mathx` helper set includes:

- random helpers: `randInt`, `randomInt`, `randFloat`, `randomFloat`, `random`
- statistics helpers: `sum`, `mean`, `avg`, `median`, `var`, `std`, `cv`,
  `mode`
- geometry helper: `dist`
- logarithm/power helpers: `logMn`, `floorLog`, `ceilLog`, `roundLog`,
  `floorPow`, `ceilPow`, `roundPow`
- min/max helpers: `max`, `maxi`, `min`, `mini`

## Compatibility Boundary

`jsox.mathx` is synchronous and provider-free. It accepts JSON-safe numbers,
arrays, and `{ x, y }` point objects. Raw Android/OpenCV `Point` and `Rect`
objects do not cross into Node.

Rhino `jsox.extend()` mutates JavaScript built-ins. The Node facade does not
perform default global `Math` or prototype mutation. Instead:

- `jsox.extend(target, "Mathx")` copies Mathx helpers to an explicit target.
- `jsox.extendAll(target)` copies the currently promoted Mathx helper set.
- `jsox.extend("Mathx")` and `jsox.extendAll()` reject with
  `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`.

This keeps migration ergonomic while avoiding hidden global changes in the
Safe Node Profile.

## Evidence

- Runtime facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Type declarations: `docs/nodejs/types/autojs6-node/jsox.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Conformance: `NodeBridgeConformanceInstrumentationTest`
- Focused instrumentation: `NodeUtilityBridgeInstrumentationTest`
- Rhino matrix: `docs/nodejs/RHINO_AUGMENT_COMPATIBILITY_MATRIX.md`
- Status manifest: `docs/nodejs/rhino-compat/status-manifest.json`
- Host verifier: `tools/nodejs/phase13/verify-utility-heavy-rhino-modules.js`
- Gradle gate: `:app:verifyNodePhase13UtilityHeavyRhinoModules`
- Surface evidence: `phase13-utility-heavy-rhino-modules`

## Remaining Work

- `zip`: needs scoped archive paths, zip-slip denial, size and compression
  budgets, overwrite policy, cleanup, and packaged smoke.
- `canvas`: needs a JSON drawing provider and a separate raw Canvas/Paint
  profile decision.
- `opencc`: packaged dictionary lookup and missing-resource diagnostics remain
  required before real conversion promotion.
- `pinyin`: full dictionary and phrase resources need packaged lookup,
  diagnostics, and corpus evidence.
- Rhino `util`: still needs smaller pure/type/density/logging split batches.
- `sysprops`: needs Android system-property policy and privacy review.
- `jsox.arrayx` and `jsox.numberx`: remain unsupported until separate pure JS
  batches define exact semantics and mutation boundaries.
- Raw Android/OpenCV point/rect parity for `Mathx.dist` remains out of this
  gate.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13UtilityHeavyRhinoModules
```
