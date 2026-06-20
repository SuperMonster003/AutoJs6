# Phase 8 Hardened Isolated Runtime POC

Status: manifest and policy POC complete

S8-41 adds a non-executable proof of concept for a future hardened
`node_sandboxed` runtime. It intentionally stops at the Android manifest and
execution-mode boundary until S8-42 defines the Binder capability broker.

## Focused Gate

Run the focused gate on the Sony device:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeHardenedIsolatedRuntimePocInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

`NodeHardenedIsolatedRuntimePocInstrumentationTest` verifies:

- `nodeSandboxed`, `node_sandboxed`, and `sandbox` normalize to
  `node_sandboxed`;
- the mode is `design_gated`, has no supported launch surfaces, and exposes no
  bridge providers;
- policy metadata reports broker-owned timeout, visibility, notification,
  quota, and stop semantics;
- `NativeNodeSandboxedScriptService` is present in the manifest, not exported,
  uses `android:isolatedProcess=true`, and uses the `:nodejs_sandboxed`
  process suffix;
- direct script requests for `executionMode: "nodeSandboxed"` reject with
  `ERR_AUTOJS6_EMBEDDED_NODE_UNSUPPORTED_OPTION` before user code can create a
  marker file.

## Runtime Impact

There is no positive runtime route to the isolated service yet. The existing
embedded service continues to own normal one-shot, interactive foreground, and
scheduled-runner execution. The isolated service stub logs and stops if it is
started accidentally.

The accepted S8-41 result is:

- no script execution in the isolated process;
- no bridge provider availability in `node_sandboxed`;
- no direct app permission inheritance claim;
- no shell/root, Java interop, native addon, raw process, raw network, or raw
  filesystem expansion;
- a stable rejection shape that future promotion can replace only after the
  broker gate exists.

## Next Gate

S8-42 must define and test the Binder capability broker before any executable
isolated runtime path is enabled. The broker must own capability declaration,
parameter validation, size limits, cancellation, audit rows, resource cleanup,
and execution ownership.
