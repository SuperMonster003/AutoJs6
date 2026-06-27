# P13-16 JavaAdapter / defineClass Replacement Decision

- Status: ready
- Gate: `:app:verifyNodePhase13RhinoJavaAdapterDecision`
- Decision: Rhino `JavaAdapter` stays unsupported in Node. The replacement name
  is reserved as `java.defineClass()` / `$autojs.java.defineClass()` for a
  future bounded proxy-interface provider.

## Implemented

`require("rhino")` now exposes `rhino.JavaAdapter()`, but only as an explicit
stable denial. It throws `ERR_AUTOJS6_RHINO_JAVA_ADAPTER_UNSUPPORTED`, is not
installed on `globalThis`, and is not enabled by
`rhino.install({ experimental: true })`.

The experimental Java facade also reserves `defineClass()`:

```js
const java = require("java");

java.defineClass({
  name: "org.autojs6.node.GeneratedClickListener",
  interfaces: ["android.view.View.OnClickListener"],
  methods: {
    onClick(event) {
      // Future provider-owned callback body.
    },
  },
});
```

Today this throws `ERR_AUTOJS6_JAVA_METHOD_DENIED`. The method does not dispatch
to the bridge, does not load bytecode, does not create class loaders, and does
not keep callback handles alive.

## Replacement Boundary

The future provider should be a proxy-interface or `$java.defineClass` design,
not a Rhino `JavaAdapter` clone. Required boundaries:

- no unbounded dynamic inheritance
- no arbitrary class loader or dex/jar loading
- no raw Java callback handle exposed to JavaScript
- callbacks scoped to one execution id with explicit close/release behavior
- provider-owned thread dispatch for Android listener callbacks
- leak diagnostics for pending callback handles on script stop, timeout, crash,
  and service destroy
- packaged metadata that stays fail-closed until callback cleanup evidence lands

Common listener/interface examples remain future provider work. P13-16 closes
the v1.1 decision by fixing the stable error surface, bounded lifecycle
requirements, and the reserved migration name without adding callback authority.

P13-16 v1.1 closure is a stable-denial decision, not a proxy-interface
implementation.

## Evidence

- Runtime JS facade: `app/src/main/cpp/node_bridge_sources.cpp`
- Focused Rhino test:
  `NodeRhinoShimDesignGateInstrumentationTest#rhinoShimFacadeIsDisabledByDefault`
- Focused Java test:
  `NodeJavaInteropBridgeInstrumentationTest#jsJavaInteropExperimentalModuleUsesBridgeAndEnforcesAllowlist`
- Type declarations:
  - `docs/nodejs/types/autojs6-node/rhino.d.ts`
  - `docs/nodejs/types/autojs6-node/autojs_global.d.ts`
- Type smoke: `sample/nodejs/typescript-smoke/src/main.ts`
- Host verifier: `tools/nodejs/phase13/verify-rhino-java-adapter-decision.js`
- Gradle gate: `:app:verifyNodePhase13RhinoJavaAdapterDecision`
- Report artifacts:
  - `build/reports/nodejs/phase13-rhino-java-adapter-decision.json`
  - `build/reports/nodejs/phase13-rhino-java-adapter-decision.md`

## Closure

The v1.1 gate closes because `rhino.JavaAdapter()` has a stable explicit
unsupported error, global `JavaAdapter` remains absent, `java.defineClass()` and
`$autojs.java.defineClass()` are reserved stable denials that do not dispatch to
the bridge, type declarations and smoke samples expose denial-only signatures,
and docs/security guidance keep actual proxy-interface callback authority out of
the Node surface.

## Future Provider Work

- Implement a bounded proxy-interface / `defineClass` provider in a future task.
- Cover common listener use cases such as click, attach-state, runnable, and
  simple callback interfaces.
- Add thread dispatch, callback cleanup, packaged metadata, and leak stress
  evidence before promoting Pro parity.

## Validation

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13RhinoJavaAdapterDecision
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRhinoShimDesignGateInstrumentationTest,org.autojs.autojs.engine.NodeJavaInteropBridgeInstrumentationTest"
```
