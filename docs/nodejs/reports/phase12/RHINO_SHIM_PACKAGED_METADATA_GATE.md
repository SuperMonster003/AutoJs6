# Rhino Shim Packaged Metadata Gate

Status: P12-26 packaged metadata gate recorded as fail-closed.

P12-25 added a live embedded Rhino JSON-only execution adapter POC for explicit
one-shot requests. P12-26 does not promote that path to packaged APKs. Instead,
the Rhino bridge provider now publishes packaged metadata that keeps packaged
execution rejected:

```text
packagedSupport.status = unsupported
```

No packaged metadata shape is accepted in Phase 12. APKs without explicit shim metadata cannot execute the shim, and because no accepted metadata exists yet, all packaged Rhino JSON-only execution remains fail-closed.

## Metadata Scope

Future metadata, if designed in a later phase, may authorize only JSON-only run.
It must not authorize broader Rhino interop:

- metadata may authorize only JSON-only run;
- metadata never authorizes install or globals;
- `rhino.install()` remains permanently unsupported in the current profile;
- `compat.rhino`, global `rhino`, `importClass`, `importPackage`, `Packages`,
  `java`, `android`, and `JavaAdapter` remain absent from V8;
- source text containing `require("rhino")` must not inject Android manifest
  entries, receivers, providers, services, or permissions.

The current `rhino` bridge capability covers only `rhino.run`. There is no
separate packaged Rhino capability until a concrete metadata schema, package
builder preflight, and device/packaged evidence exist.

## Runtime State

Default and package-like fallback behavior remains denied:

- `rhino.run({ source, timeoutMs, args })` without `experimental: true` rejects
  with `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- queued-host fallback resolves a stable provider-unavailable `RunResult` with
  provider `queued-host`, `category: "provider-unavailable"`, and
  `ERR_AUTOJS6_RHINO_SHIM_DISABLED`;
- queued-host fallback reports `engineCreated: false` and `destroyed: true`;
- `rhino.install()` throws `ERR_AUTOJS6_RHINO_INSTALL_UNSUPPORTED`;
- no `compat.rhino` or Rhino globals are installed.

The live embedded POC has timeout and destroy evidence through
`NodeBridgeRhinoExecutionService` and the P12-25 instrumentation tests; packaged timeout, crash, and destroy evidence is intentionally absent; packaged crash containment evidence does not exist for this shim, therefore packaged execution remains rejected until a later phase proves that behavior inside packaged APKs.

## Provider Metadata

`NodeBridgeProviderRegistry` now declares a `rhino` provider with:

- module: `NodeBridgeModules.RHINO`;
- capability: `NodeBridgePermissionManifest.RHINO`;
- packaged support: `unsupported`;
- details documenting the default disabled policy, install denial, globals
  denial, timeout policy, and P12-26 packaged rejection.

`NodeExecutionModePolicy` lists `rhino` only in one-shot provider diagnostics.
Scheduled/packaged promotion is not inferred from the live POC.

## Evidence

- Packaged gate report:
  `docs/nodejs/reports/phase12/RHINO_SHIM_PACKAGED_METADATA_GATE.md`
- Provider registry:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
- Execution mode diagnostics:
  `app/src/main/java/org/autojs/autojs/engine/NodeExecutionModePolicy.kt`
- P12-25 live adapter report:
  `docs/nodejs/reports/phase12/RHINO_JSON_ONLY_EXECUTION_ADAPTER_POC.md`
- Runtime facade:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Android Rhino provider:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeRhinoExecutionService.kt`
- Instrumentation evidence:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeRhinoShimDesignGateInstrumentationTest.kt`
- Host verifier:
  `tools/nodejs/phase12/verify-rhino-compat-rhino-shim-packaged-metadata-gate.js`
- Gradle gate:
  `:app:verifyNodeRhinoCompatRhinoShimPackagedMetadataGate`

## Next Work

P12-27 starts the barcode/qrcode bridge schema track. Any future Rhino packaged
promotion must be a separate phase with metadata schema, package-builder
preflight, packaged APK smoke, timeout/crash/destroy evidence, and unchanged
denials for `install()` and globals.
