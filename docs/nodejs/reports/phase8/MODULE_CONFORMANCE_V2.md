# Module Conformance Corpus v2

Task: S8-19

The module conformance v2 corpus is checked in under
`app/src/androidTest/assets/node_compat_corpus_v2/` and is executed by
`NodeCompatCorpusInstrumentationTest`.

## Device Evidence

Focused Sony gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Latest local result: 4/4 tests passed on Sony XQ-AT72 (`QV710AF65F`).

## Coverage

| Fixture area | AutoJs6 corpus behavior | Node 24.17 comparison target |
| --- | --- | --- |
| `require(esm)` | CommonJS can synchronously `require()` a sync `.mjs` graph and receives the namespace object. | Match for sync ESM graphs. |
| TLA rejection | CommonJS `require()` of an async ESM graph rejects with `ERR_REQUIRE_ASYNC_MODULE`. | Match for the rejection shape where sync `require()` cannot load async ESM. |
| JSON import attributes | Static and dynamic JSON imports require `with { type: "json" }` and share the CommonJS JSON cache object. | Match for attribute-required JSON modules and cache sharing. |
| ESM/CJS cycles | A CJS module can synchronously require an ESM module that imports the in-progress CJS exports object, then complete with stable partial-export observations. | Behavioral match for supported CJS partial-export cycle semantics; full Node live-binding semantics outside the partial ESM subset remain out of scope. |
| `import.meta.resolve` | Resolves local/package targets to `file:` URLs, allowed builtins to `node:` URLs, and denies disabled builtins. | Match for local/package/allowed-builtin resolution; disabled-builtin denial is an intentional AutoJs6 restriction. |
| Package custom condition | The `autojs6` condition is active and package key order is preserved. | Match when desktop Node is launched with `--conditions=autojs6`; AutoJs6 activates this condition by profile policy. |
| TypeScript mixed graph | `.mts` imports `.cts` in the corpus; the packaged moduleSources graph also verifies erasable TypeScript syntax stripping before native execution. | Match for erasable TypeScript source behavior where Node 24.17 accepts the same syntax; TSX and transform-required syntax remain out of scope. |
| Packaged mixed graph | moduleSources cover ESM, CommonJS, JSON attributes, dynamic import, `.mts`, and `.cts` in a packaged-preload shape. | AutoJs6 packaged-runtime evidence; no direct desktop Node equivalent for encrypted/runtime moduleSources. |

## Boundary

The corpus does not enable native addons, unrestricted `child_process`, raw
network modules, loader hooks, unrestricted `node:module`, URL imports, or
imports outside `workingDirectory`. Differences from desktop Node are classified
as Safe Node Profile restrictions unless a fixture marked as exact-match
regresses.
