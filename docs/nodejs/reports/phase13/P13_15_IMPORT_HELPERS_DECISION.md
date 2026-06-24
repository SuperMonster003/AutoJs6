# P13-15 importClass / importPackage Decision

- Status: ready
- Gate: `:app:verifyNodePhase13RhinoImportHelpersDecision`
- Decision: direct Rhino `importClass` / `importPackage` compatibility remains
  unsupported in Node; use explicit assignment from the P13-14 Java proxy
  target instead.

## Implemented

`require("rhino")` now exposes explicit `rhino.importClass()` and
`rhino.importPackage()` helpers, but both throw the stable error code
`ERR_AUTOJS6_RHINO_IMPORT_UNSUPPORTED`. They are intentionally not installed on
`globalThis`, are not enabled by `rhino.install({ experimental: true })`, and do
not dispatch to the Java bridge.

This closes the P13-15 decision without widening Java authority. Rhino-style
dynamic class/package importing would imply broad class traversal, implicit
global mutation, and ambiguous module ownership in V8. The supported migration
shape is assignment-style and explicit:

```js
const rhino = require("rhino");
const scope = {};

rhino.install({ experimental: true, target: scope });

const Rect = scope.android.graphics.Rect;
const MathClass = scope.Packages.java.lang.Math;

const rect = await new Rect(1, 2, 6, 8);
const max = await MathClass.max(6, 9);
```

The Java interop gate and P13-13 allowlist still apply. The example above works
only when Java interop is enabled, and only for allowlisted classes and methods.

## Boundaries

- No global `importClass`.
- No global `importPackage`.
- No `compat.rhino` import helper.
- No package-wide wildcard traversal outside the P13-14 Java proxy.
- No raw Java `Class`, class loader, reflection, native library loading, process
  authority, Android object graph, or Rhino `Scriptable` exposure.
- Packaged APK metadata remains fail-closed.

## Evidence

- Runtime JS facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Focused test:
  `NodeRhinoShimDesignGateInstrumentationTest#rhinoShimFacadeIsDisabledByDefault`
- Type declarations: `docs/nodejs/types/autojs6-node/rhino.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Host verifier: `tools/nodejs/phase13/verify-rhino-import-helpers-decision.js`
- Gradle gate: `:app:verifyNodePhase13RhinoImportHelpersDecision`
- Report artifacts:
  - `build/reports/nodejs/phase13-rhino-import-helpers-decision.json`
  - `build/reports/nodejs/phase13-rhino-import-helpers-decision.md`

## Validation

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13RhinoImportHelpersDecision
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRhinoShimDesignGateInstrumentationTest"
```
