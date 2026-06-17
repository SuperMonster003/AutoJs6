# Node Profile v1.1 ESM Promotion Evaluation

Date: 2026-06-17

Branch: `feature/nodejs-runtime-v1.1`

Device: Sony XQ-AT72 `QV710AF65F`, Android 12

Package identity: production, `org.autojs.autojs6`

## Recommendation

Keep ESM experimental for Node Profile v1.1.

Do not promote ESM to partial support yet. The controlled ESM subset is useful
and the focused production-identity tests are green after the source-map offset
fix in `app/src/main/cpp/node_bridge_sources.cpp`, but the support contract would
still be broader than the evidence:

- there is no real ESM npm package corpus;
- packaged APK coverage is still default-denial diagnostics plus focused
  experimental smoke, not encrypted packaged ESM graph support;
- dynamic import remains a separate narrower experiment;
- `import.meta`, loader hooks, raw `node:module` loader authority, URL/network
  imports, and CJS `require()` of ESM remain unsupported;
- service destroy/cancellation while ESM linking or evaluation is pending still
  needs a dedicated gate.

## Commands Run

Focused ESM, source-map, and real npm corpus:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest,org.autojs.autojs.engine.NodeSourceMapStackTraceInstrumentationTest,org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest"
```

Result after source-map offset fix:

```text
22 tests, 0 failures, 0 errors, 0 skipped
```

Packaged default-denial diagnostics:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunEsmDiagnosticsEmbeddedNodePackagedApk,org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest#installAndRunDynamicImportDiagnosticsEmbeddedNodePackagedApk"
```

Result:

```text
2 tests, 0 failures, 0 errors, 0 skipped
```

## Evidence Matrix

| Gate | Status | Notes |
| --- | --- | --- |
| `.mjs` entry | Pass as experiment | Default profile still rejects; internal experimental request can run `.mjs`. |
| local static import | Pass as experiment | Scoped local `.mjs` imports are covered. |
| package `type=module` | Pass as experiment | Entry routing works when the ESM experiment is enabled. |
| package `exports` import condition | Pass as experiment | Synthetic package tests cover `import`, `node`, and `default` condition order. |
| package `imports` import condition | Pass as experiment | Synthetic package tests cover importer-sensitive conditions. |
| ESM import of CJS | Pass as experiment | Default/namespace-style access stays scoped through existing CJS loader cache. |
| supported builtin import | Pass as experiment | Uses the Safe Node builtin allowlist. |
| disabled builtin import | Pass | Denied through existing policy. |
| CJS `require()` of ESM | Pass as denial | Fails with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`. |
| top-level await | Pass as experiment | Covered in focused ESM tests. |
| dynamic import | Experimental only | Local relative imports pass only with the separate dynamic-import experiment; package, URL, absolute, and escape cases remain denied. |
| source maps | Pass after fix | Inline and scoped external maps now map result `errorStack`; external escapes remain denied. |
| real npm corpus | Insufficient | Existing corpus is CommonJS-only and explicitly excludes `type=module`. |
| packaged APK | Insufficient for promotion | Default ESM and dynamic-import diagnostics pass; encrypted packaged ESM graphs are not supported. |

## Source Map Finding

The first v1.1 promotion run exposed a source-map stack mapping regression in
production identity:

- `NodeSourceMapStackTraceInstrumentationTest#inlineSourceMapMapsFailureStack`
- `NodeSourceMapStackTraceInstrumentationTest#externalSourceMapInsideWorkingDirectoryMapsFailureStack`

Both failures kept stack frames on generated bundle paths instead of mapping to
the original source names. The root cause was the main CommonJS source-map
record using `lineOffset=1` even though `Function` constructor frames include
wrapper prologue lines before the user source that already contains an injected
`"use strict"` line. The v1.1 fix sets the main entry source-map line offset to
`3`; the focused source-map suite then passed 3/3 and the combined ESM/source-map
run passed 22/22.

This strengthens diagnostics, but it is also evidence against promotion: source
map behavior is still young enough that it should not be part of a broadened ESM
support contract without more history.

## Packaged APK Decision

Packaged APK behavior remains conservative:

- default packaged `.mjs` diagnostics still return
  `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`;
- default packaged dynamic import diagnostics still return
  `ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED`;
- experimental single-file packaged `.mjs` smoke remains useful, but it is not
  enough for partial support because encrypted packaged ESM graphs, moduleSources
  metadata, and dynamic import graphs are still design-gated.

## v1.1 Decision

For Node Profile v1.1:

- keep `autojs.nodejs.esm.experimental=false` by default;
- keep `autojs.nodejs.dynamicImport.experimental=false` by default and separate
  from static ESM;
- keep documenting `.mjs` as a Node route that is rejected unless an explicit
  experimental build/request enables ESM;
- keep real npm compatibility claims CommonJS-only until an ESM npm corpus is
  admitted;
- do not update `NODE_PROFILE_V1_STABLE_SURFACE.md` to include ESM.

## Next Promotion Gate

Before reconsidering partial promotion, add:

- a small real ESM npm package corpus with license notes and no native addons;
- packaged ESM graph smoke for plain and encrypted module graphs;
- packaged verifier metadata for ESM graph source count, source kinds, and
  package condition context;
- service destroy/cancellation tests during ESM linking and top-level await;
- `import.meta` decision tests;
- source-map stack tests for ESM module frames, not only main CommonJS frames.
