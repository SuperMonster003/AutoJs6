# P13-47 Dependency Compatibility Corpus

P13-47 records dependency compatibility corpus as partial. The current landing
adds Phase 13 classification metadata to the fixed real npm corpus, keeps the
offline fixture count at 50, extends the desktop differential report with
category pass-rate rows, and adds a host verifier plus Gradle gate.

## Covered

- `app/src/androidTest/assets/npm_compat_real/manifest.json` now carries
  `phase13Compatibility` metadata with schema
  `autojs6-phase13-dependency-compatibility-corpus-v1`.
- The taxonomy covers the required P13-47 reason kinds:
  `missing_builtin`, `native_addon`, `postinstall`, `network`, and `fs`.
- Each category records fixture references plus package/reason rows so reports
  can explain which package class is blocked and why.
- `NodeRealNpmCompatInstrumentationTest.fixtureMetadataIsCompleteAndSafe`
  validates the classification schema, fixture references, and reason rows.
- `tools/nodejs/differential/run-differential-tests.js` now emits
  `phase13Compatibility` JSON and a "Phase 13 Dependency Compatibility
  Categories" Markdown section with desktop and AutoJs6 pass-rate columns when
  differential artifacts are collected. Desktop-baseline-only runs can still
  return non-zero for fixtures that intentionally assert AutoJs6 policy
  denials.

## Remaining Work

- CI still needs a direct import path from connected AutoJs6 real npm
  differential logs into the host differential report.
- The corpus remains fixed and offline; live registry drift testing is still a
  separate security and reproducibility problem.
- Unsafe package execution remains denied. Native addons, lifecycle scripts,
  postinstall downloads, and executable extraction are represented as metadata
  blocker categories, not vendored runnable payloads.
- Future capability relaxations should update the same category rows so
  pass-rate changes are visible by reason kind.

## Evidence

- Manifest taxonomy:
  `app/src/androidTest/assets/npm_compat_real/manifest.json`
- Metadata instrumentation:
  `NodeRealNpmCompatInstrumentationTest.fixtureMetadataIsCompleteAndSafe`
- Differential report:
  `tools/nodejs/differential/run-differential-tests.js`
- Host verifier:
  `tools/nodejs/phase13/verify-dependency-compatibility-corpus.js`
- Gradle gate:
  `:app:verifyNodePhase13DependencyCompatibilityCorpus`

## Verification

```powershell
node tools\nodejs\phase13\verify-dependency-compatibility-corpus.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13DependencyCompatibilityCorpus
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest#fixtureMetadataIsCompleteAndSafe"
```
