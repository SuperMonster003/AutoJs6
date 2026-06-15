# AutoJs6 Embedded Node.js Integration Plan

This document is the engineering map for the AutoJs6 Safe Node Profile. It records the current integration boundary and the order in which future work should land.

## Product Goal

AutoJs6 embeds Node.js as a controlled Android script runtime:

- real Node/V8/libuv runtime through `libnode.so`
- AutoJs6-owned bootstrap and CommonJS loader
- scoped filesystem access
- restricted builtin surface
- isolated `:nodejs_embedded` service process
- packaged APK support through native library injection and manifest synthesis
- diagnostics and release gates
- native crash diagnostics through native dependency and symbol archive reports

The target is not unrestricted desktop Node.js. The target is a stable AutoJs6 Safe Node Profile that can grow deliberately.

## Current Runtime Chain

```text
AutoJs6 app process
  -> ScriptEngineManager
  -> Node backend selector
  -> NativeNodeEmbeddedJavaScriptEngine
  -> NativeNodeEmbeddedScriptService in :nodejs_embedded
  -> NativeNodeEmbeddedRuntimeBridge
  -> libautojs6-node.so
  -> libnode.so
  -> AutoJs6 bootstrap and CommonJS loader
```

Packaged APKs use the same runtime shape and must carry:

- `lib/<abi>/libnode.so`
- `lib/<abi>/libautojs6-node.so`
- `lib/<abi>/libc++_shared.so`
- `NativeNodeEmbeddedScriptService` manifest entry
- foreground service permissions and `specialUse` type for the embedded service
- Node project metadata and module sources when required

## Backend Switches

The Gradle property `autojs.nodejs.backend` selects the runtime backend.

Supported values:

- `embedded`: default; runs scripts through the embedded Node service.
- `native`: keeps the native one-shot backend available as a regression oracle.
- `rhino`: routes Node-capable launches back to Rhino where explicitly requested.
- `auto`: reserved switch for backend selection policy.

Related properties:

- `autojs.nodejs.embedded.script.enabled=true`
- `autojs.nodejs.releaseFlavor=nodeFull`
- optional `autojs.nodejs.releaseAbis` for `nodeMini`
- `autojs.nodejs.embedded.probe.level=0`
- `autojs.nodejs.embedded.probe.timeout.ms=3000`
- `autojs.nodejs.embedded.probe.lifecycle=false`
- `autojs.nodejs.esm.experimental=false`
- `autojs.nodejs.dynamicImport.experimental=false`
- `autojs.nodejs.network.experimental=false`
- `autojs.nodejs.worker_threads.experimental=false`
- optional Embedded Node performance benchmark: `NodeEmbeddedPerformanceInstrumentationTest`
- extended Embedded Node memory leak stress gate: `NodeEmbeddedMemoryLeakStressInstrumentationTest`
- native crash symbol archive gate: `nativeNodeSymbolArchiveReport`
- checked-in example project smoke: `NodeExampleProjectInstrumentationTest`

Do not remove the non-embedded paths while embedded lifecycle stress coverage is still evolving.

## Example Projects

Checked-in Node examples live under `sample/nodejs/`. See [EXAMPLES.md](EXAMPLES.md) for the project list, expected output files, and smoke-test command.

## Migration Guide

Rhino users should start with [MIGRATION_FROM_RHINO.md](MIGRATION_FROM_RHINO.md). The guide covers Node entry points, global API differences, Promise-based bridge calls, scoped filesystem behavior, and packaged APK differences.

Users adapting Auto.js Pro 9 or AutoX.js v7 scripts should also read [COMPATIBILITY_NOTES_PRO_AUTOX.md](COMPATIBILITY_NOTES_PRO_AUTOX.md), which documents behavior-level differences without claiming implementation equivalence.

## Routing Rules

Node routing is enabled by:

- `"nodejs";` or `"node";` directive
- `.node.js`
- `.cjs`
- `.mjs`
- nearest `project.json` with `type` or `projectType` set to `node`
- encrypted script headers carrying the Node execution flag

Important: `.mjs` currently routes to the Node backend, but ESM execution is still a controlled/unsupported path unless an explicit future ESM feature gate enables it.

## Phase Order

### P0 Baseline and Code Map

- Keep this document set current.
- Keep `NodeRuntimeCapabilities` as the Kotlin-side capability registry.
- Keep `require("autojs6:profile")` as the JavaScript-side read-only profile snapshot.
- Record supported, limited, disabled, and planned capabilities.
- Keep current test commands and packaged APK checks discoverable.

### P1 Embedded Lifecycle Hardening

- Introduce explicit runtime state transitions.
- Unify success, error, timeout, stop, service destroy, and native crash cleanup.
- Track per-execution identifiers and native handles.
- Add lifecycle stress instrumentation tests.

### P2 Errors and Diagnostics

- Stabilize `ERR_AUTOJS6_*` error codes.
- Add resolver trace for package and builtin resolution.
- Expand packaged APK diagnostic reporting.

### P3 Compatibility Expansion

- Expand only low-risk builtins by default.
- Keep high-risk features behind design gates and feature flags.

### P4 AutoJs API Bridge

- Add an asynchronous bridge protocol before exposing user modules.
- Route Android and AutoJs API calls through capability providers.
- Reject pending bridge calls when the script service stops.
- Current user-facing MVP modules: `toast`, `app`, `engines`, `clipboard`, `device`, `shell`, `work_manager` metadata registration, protocol-gated `dialogs`, selector-shaped disabled-path `accessibility`, and protocol-gated `media_projection`/`image`.

### P5 Release Gates

- Aggregate local release verification tasks.
- Keep device instrumentation and packaged smoke as first-class gates.
- Keep `nodeFull`, `nodeMini`, and `rhinoOnly` release packaging policy explicit and verified. See `docs/nodejs/design/RELEASE_FLAVORS.md`.
- Keep optional startup/RSS benchmarks available for lifecycle and native-load changes without making noisy timing data a default release failure.
- Keep extended memory leak stress available for lifecycle, fs, bridge, and image-handle changes.

### P6-P8 Longer-Term Compatibility

- Evaluate `fs.promises`, stream, zlib, ESM, network, workers, and native addons one at a time.
- Keep scheduled/background task execution design-gated; Task 65 permits only controlled `work_manager` metadata registration, and Node must not get raw Android scheduler or broadcast authority.
- Treat controlled `fetch` as the only approved network MVP; live provider enablement remains behind `autojs.nodejs.network.experimental=false` by default.
- Keep `worker_threads` behind `docs/nodejs/design/WORKER_THREADS.md` and `autojs.nodejs.worker_threads.experimental=false`; workers must not access AutoJs bridge modules or Android objects.
- Each high-risk feature needs a design document, flag, tests, packaged smoke, and rollback path.
- Keep current design gates in `docs/nodejs/design/` synchronized when broad compatibility surfaces change.

## Global Invariants

1. Preserve Rhino compatibility.
2. Keep `embedded` as the default backend while retaining `rhino`, `native`, and `auto` switches.
3. Keep the native one-shot backend until embedded lifecycle stress coverage is mature.
4. Support both the main app and packaged APKs.
5. Treat the exposed runtime as AutoJs6 Safe Node Profile, not desktop Node.js.
6. Do not open high-risk capabilities without an explicit task and gate.
7. Android and AutoJs API calls from Node must be asynchronous from the JavaScript perspective.
8. Callbacks into JavaScript must be marshalled onto the Node event loop thread.
9. Scoped filesystem policy must remain enforced.
10. Feature work must include positive tests, negative/security tests, packaged impact checks when relevant, and documentation updates.

## Immediate Next Tasks

1. Harden the private live bridge transport and refresh packaged templates so packaged apps can use the same live path.
2. Keep `toast`, `app`, `engines`, `clipboard`, `device`, `shell`, protocol-gated `dialogs`, selector-shaped disabled-path `accessibility`, and protocol-gated `media_projection`/`image` stable while designing real capability providers.
3. Design the real accessibility and MediaProjection/image providers before enabling Android-backed actions, screenshot capture, image decoding, or live node/image handles. Accessibility selector descriptors and node snapshots must remain JSON-only.
4. Keep scheduled/background task execution behind `docs/nodejs/design/WORK_MANAGER.md`; `work_manager` currently registers controlled metadata only, and future timed execution should use a bridge provider, persistent user-visible descriptors, and packaged manifest gates.
5. Keep ESM behind `docs/nodejs/design/ESM.md` and `autojs.nodejs.esm.experimental=false`; `.mjs` remains a Node route with default rejection. Keep dynamic import behind the separate `autojs.nodejs.dynamicImport.experimental=false` gate.
6. Keep network behind `docs/nodejs/design/NETWORK.md` and `autojs.nodejs.network.experimental=false`; Task 41 provides a controlled fetch wrapper and fake-provider coverage, while live Android network I/O remains disabled in normal builds and raw Node network modules stay denied.
7. Keep `worker_threads` behind `docs/nodejs/design/WORKER_THREADS.md` and `autojs.nodejs.worker_threads.experimental=false`; Task 42 freezes the worker boundary without enabling worker execution.
8. Use `docs/nodejs/PERFORMANCE.md`, `NodeEmbeddedPerformanceInstrumentationTest`, and `NodeEmbeddedMemoryLeakStressInstrumentationTest` when changing embedded startup, native load, service lifecycle, bridge, fs, or image-handle behavior.
9. Refresh the packaged inrt template so `app.versionName` and `app.versionCode` can become exact packaged gates.
10. Keep packaged APK verification in the loop for every bridge-facing module.
