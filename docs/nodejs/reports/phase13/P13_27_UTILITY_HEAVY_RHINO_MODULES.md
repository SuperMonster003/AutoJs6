# P13-27 Utility-Heavy Rhino Modules

Status: partial.

P13-27 is intentionally split. This landing promotes the pure JavaScript
`jsox.mathx`, `jsox.arrayx`, and `jsox.numberx` subsets and keeps
resource-backed or raw Android object surfaces behind later gates.

## Promoted Surface

- `require("jsox")`
- `require("jsox.mathx")`
- `require("jsox.arrayx")`
- `require("jsox.numberx")`
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

The promoted `Arrayx` helper set includes:

- validation and set helpers: `ensureArray`, `distinct`, `distinctBy`,
  `union`, `intersect`, `subtract`, `differ`
- sorting helpers: `sortBy`, `sortByDescending`, `sortDescending`, `sorted`,
  `sortedBy`, `sortedByDescending`, `sortedDescending`
- mutation helper: `shuffle`

The promoted `Numberx` helper set includes:

- validation and comparison helpers: `ensureNumber`, `ensureNumberLike`,
  `ensureNumbersLike`, `check`
- numeric helpers: `clamp`, `clampTo`, `toFixedNum`
- string/parse helpers: `padStart`, `padEnd`, `parseFloat`, `parsePercent`,
  `parseRatio`, `parseAny`
- constant: `ICU`

## Compatibility Boundary

`jsox.mathx`, `jsox.arrayx`, and `jsox.numberx` are synchronous and
provider-free. They accept JSON-safe numbers, arrays, and `{ x, y }` point
objects where applicable. Raw Android/OpenCV `Point` and `Rect` objects do not
cross into Node.

Rhino `jsox.extend()` mutates JavaScript built-ins. The Node facade does not
perform default global `Math` or prototype mutation. Instead:

- `jsox.extend(target, "Mathx")` copies Mathx helpers to an explicit target.
- `jsox.extend(target, "Arrayx")` copies Arrayx helpers to an explicit target.
- `jsox.extend(target, "Numberx")` copies Numberx helpers to an explicit
  target.
- `jsox.extendAll(target)` copies the currently promoted pure helper set.
- `jsox.extend("Mathx")` and `jsox.extendAll()` reject with
  `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`.

This keeps migration ergonomic while avoiding hidden global changes in the
Safe Node Profile.

## Raw Object Boundary

Raw Android/OpenCV `Point` and `Rect` object parity is stable-denied for
P13-27. `Mathx.dist()` remains limited to numbers, arrays, and plain `{ x, y }`
objects that can cross the Node bridge as JSON-safe values. Any future raw
object compatibility must land under a separate opt-in profile gate with
dedicated ownership, lifecycle, and permission evidence.

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

The docs, status manifest, surface manifest, and Gradle gate are evidence
checks only. They do not add a second blocker for the same resource-backed
utility module promotion work.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13UtilityHeavyRhinoModules
```
