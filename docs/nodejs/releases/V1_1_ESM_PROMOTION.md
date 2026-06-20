# Node Profile v1.1 ESM Promotion Evaluation

Date: 2026-06-17

Branch: `feature/nodejs-runtime-v1.1`

Device: Sony XQ-AT72 `QV710AF65F`, Android 12

Package identity: production, `org.autojs.autojs6`

## Recommendation

Promote the controlled ESM subset to Node Profile v1.1 partial support.

This document originally recorded a conservative hold. Task 153 supersedes that
hold by narrowing the support contract to the exact behavior already covered by
focused gates: `.mjs` and `type=module` entries, local static imports, selected
builtin imports, ESM-to-CJS interop, package condition selection, and stable
denial of unsupported boundaries. The promotion is intentionally partial
because these gaps remain:

- there is no real ESM npm package corpus;
- packaged APK coverage is a focused `.mjs` entry plus local `.mjs`/CommonJS
  graph smoke, not broad package/export graph support;
- dynamic import is local-only partial support; broad package graphs, file URLs,
  and loader hooks remain out of scope;
- `import.meta` is limited to scoped metadata and `resolve()`; loader hooks,
  raw `node:module` loader authority, and URL/network imports remain
  unsupported;
- CJS `require()` is limited to synchronous partial ESM graphs and rejects ESM
  graphs containing `await` or dynamic `import()` with `ERR_REQUIRE_ASYNC_MODULE`;
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

Packaged ESM and dynamic import local focused gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.identity=production" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

Result:

```text
4 tests, 0 failures, 0 errors, 0 skipped
```

The packaged focused gate also checks runtime compatibility descriptor
version-vector drift before script execution and reports APK fixture cache
hit/miss status.

## Evidence Matrix

| Gate | Status | Notes |
| --- | --- | --- |
| `.mjs` entry | Pass as partial | Default profile runs `.mjs`; explicit ESM-off requests still reject. |
| local static import | Pass as partial | Scoped local `.mjs` imports are covered. |
| package `type=module` | Pass as partial | Entry routing works by default under the v1.1 profile. |
| package `exports` import condition | Pass as partial | Synthetic package tests cover importer-sensitive condition matching. Phase 8 S8-18 adds package key order preservation and the `autojs6` condition. |
| package `imports` import condition | Pass as partial | Synthetic package tests cover importer-sensitive conditions. |
| ESM import of CJS | Pass as partial | Default/namespace-style access stays scoped through existing CJS loader cache. |
| supported builtin import | Pass as partial | Uses the Safe Node builtin allowlist. |
| disabled builtin import | Pass | Denied through existing policy. |
| CJS `require()` of ESM | Pass as partial | Synchronous graphs return the ESM namespace object; async graphs fail with `ERR_REQUIRE_ASYNC_MODULE`. |
| top-level await | Pass as partial | Covered in focused ESM tests. |
| dynamic import | Pass as partial | Local `.mjs`/CJS, allowed builtin, and scoped package imports pass; disabled builtin, URL, absolute, escape, eval, and Function paths remain denied. |
| `import.meta` | Pass as partial | S8-17 covers `url`, `filename`, `dirname`, `main`, scoped `resolve()` for local/package/allowed-builtin specifiers, and disabled builtin denial. |
| source maps | Pass after fix | Inline and scoped external maps now map result `errorStack`; external escapes remain denied. |
| real npm corpus | Insufficient | Existing corpus is CommonJS-only and explicitly excludes `type=module`. |
| packaged APK | Partial | `.mjs` entry plus local `.mjs`/CommonJS graph smoke is required; packaged dynamic import covers local `.mjs` and CJS targets, while broad package graphs are not supported. |

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

Packaged APK behavior remains conservative but no longer treats all ESM as
default-denied:

- packaged CommonJS requiring synchronous ESM returns the ESM namespace object;
- packaged CommonJS requiring async ESM returns `ERR_REQUIRE_ASYNC_MODULE`;
- packaged local dynamic import smoke succeeds for `.mjs` and CommonJS targets;
- packaged `.mjs` graph smoke is part of the v1.1 partial gate, but it is not
  enough for broader support because package/export graphs, richer module source
  metadata, and broad package/dynamic import graphs are still design-gated.

## v1.1 Decision

For Node Profile v1.1:

- set `autojs.nodejs.esm.experimental=true` by default as the legacy ESM switch;
- set `autojs.nodejs.dynamicImport.local=true` by default for local-only dynamic
  import and keep `autojs.nodejs.dynamicImport.experimental=false`
  release-forbidden;
- document `.mjs` and `type=module` as partial ESM support;
- keep real npm compatibility claims CommonJS-only until an ESM npm corpus is
  admitted;
- do not update `NODE_PROFILE_V1_STABLE_SURFACE.md` to include ESM.

## Next Promotion Gate

Before reconsidering partial promotion, add:

- a small real ESM npm package corpus with license notes and no native addons;
- broader packaged ESM graph smoke for package exports/imports and nested
  package graphs;
- packaged verifier metadata for ESM graph source count, source kinds, and
  package condition context;
- service destroy/cancellation tests during ESM linking and top-level await;
- broader `import.meta` packaged and encrypted-source URL policy tests;
- source-map stack tests for ESM module frames, not only main CommonJS frames.
