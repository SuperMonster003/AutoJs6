# ESM Experimental Evaluation

Date: 2026-06-16

Device: Sony XQ-AT72 (`QV710AF65F`), Android 12

Branch/package identity: `feature/nodejs-runtime`, `org.autojs.autojs6.nodejs`

## Recommendation

Keep ESM experimental for Node Profile v1 GA.

Do not promote it to partial support yet, and do not hide it. The current subset is useful and has real-device coverage, but the remaining gaps are still too large for a GA support contract:

- no broad real ESM npm corpus is admitted yet
- packaged APK support is limited to focused diagnostics/single-file smoke paths
- encrypted packaged ESM graphs remain design-gated
- dynamic import is a separate experiment and intentionally narrower than static ESM
- `import.meta`, loader hooks, `node:module` loader authority, URL/network imports, and CJS `require()` of ESM remain unsupported
- service cancellation/destroy behavior while ESM linking or evaluation is pending still needs a dedicated gate

## Commands Run

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest#experimentalNestedPackageImportAndSelfReferenceUseEsmConditions"
```

Result: passed, 1/1 tests.

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest,org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest"
```

Result: passed, 19/19 tests.

The combined run covers 17 ESM experimental tests plus 2 fixed real npm corpus tests. The real npm corpus remains CommonJS-only by policy.

## Supported Cases

The experimental ESM suite passed these cases on the Sony device:

- default `.mjs` entry rejection when ESM is disabled
- `.mjs` entry execution when the internal experimental request flag is enabled
- local static `.mjs` imports
- top-level await
- package `type=module` entry routing under the ESM experiment
- scoped relative import escape denial
- ESM default import from CommonJS
- supported builtin import from ESM
- disabled builtin rejection from ESM
- CJS `require()` of ESM rejection with `ERR_AUTOJS6_REQUIRE_ESM_UNSUPPORTED`
- package `exports` condition selection for ESM and CJS importers
- package `imports` condition selection for ESM and CJS importers
- `.mjs` package export targets
- `default` condition fallback
- nested package imports
- package self-reference through ESM condition targets
- null export blocking and unsafe package target denial

## Failing Cases

No supported semantic case failed after the evaluation fix.

An initial focused run exposed a brittle resolver trace assertion in `NodeEsmExperimentalInstrumentationTest#experimentalNestedPackageImportAndSelfReferenceUseEsmConditions`. Execution succeeded, but the test expected a late `self-reference matched` trace line after several long absolute-path trace lines. The captured stderr was truncated before that late line. The assertion now checks stable visible evidence instead:

- stdout includes `esm.nested.self=inner-esm/outer/self-feature-esm/self`
- trace includes `exports matched request="./feature"`
- trace includes the `self-pkg` package directory and `target="./feature.mjs"`
- trace includes `selectedCondition="import"`

This keeps the same self-reference/condition-selection coverage without depending on stderr capture length.

## Dynamic Import Status

Dynamic import should remain experimental and separate from static ESM.

- With only ESM enabled, `import()` stays disabled.
- With both ESM and dynamic import request flags enabled, local relative ESM modules inside `workingDirectory` pass.
- Scoped escapes are denied with path escape errors.
- Package specifiers are rejected.
- URL/protocol imports are rejected; network URLs remain disabled.

This is not sufficient for partial ESM promotion because users reasonably expect package dynamic imports and URL-like loader behavior to be either supported or clearly out of the stable contract. For Node Profile v1 GA, the stable contract should keep dynamic import disabled by default.

## Package Conditions Status

Package condition selection is strong enough to keep testing as an experiment:

- ESM importers select `import`.
- CommonJS importers select `require`.
- `default` fallback works.
- package `exports` and `imports` are both covered.
- nested imports and self-reference paths are covered.
- null targets and unsafe targets are denied.

This is still not enough for promotion because package-condition coverage is synthetic. There is no ESM-focused real package corpus yet, and packaged encrypted module graphs are not covered.

## Real Package Corpus Status

The available real npm corpus has no ESM subset.

`NodeRealNpmCompatInstrumentationTest#fixtureMetadataIsCompleteAndSafe` enforces:

- fixture count is 4
- no native `.node` addons
- no install/postinstall scripts
- no package with `type=module`

The corpus passed in the combined run, so existing CommonJS real package compatibility was not regressed by the ESM evaluation. It cannot be used as evidence that real ESM packages are ready.

## Packaged APK Status

Packaged APK support should remain experimental for ESM.

Current packaged coverage is intentionally split:

- default packaged ESM diagnostics assert that packaged `.mjs` remains rejected with `ERR_AUTOJS6_EMBEDDED_NODE_ESM_UNSUPPORTED`
- default packaged dynamic import diagnostics assert `ERR_AUTOJS6_EMBEDDED_NODE_DYNAMIC_IMPORT_UNSUPPORTED`
- focused experimental packaged `.mjs` smoke exists, but it requires `-Pautojs.nodejs.esm.experimental=true` and a refreshed inrt template override built with the same flag

This is the correct shape for an experiment, but not for GA partial support. The packaged smoke is single-file oriented; encrypted packaged ESM graphs and dynamic import graphs are still design-gated.

## Release Decision

Node Profile v1 GA should document ESM as:

- disabled by default
- available only through explicit experimental flags/request settings
- not part of the stable Node Profile v1 surface
- safe to keep visible for controlled testing

The next promotion gate should add a small real ESM package corpus, packaged ESM graph smoke, encrypted packaged ESM graph denial/pass criteria, cancellation during ESM linking/evaluation, and a clearer source map/stack trace policy for ESM modules.
