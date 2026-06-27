# P13-47 Dependency Compatibility Corpus

P13-47 records the v1.1 fixed dependency compatibility corpus as ready while
live registry and connected-CI expansion remain future work. The current
landing adds Phase 13 classification metadata to the fixed real npm corpus,
raises the offline fixture count to 55, extends the desktop differential report
with category pass-rate rows, and adds a host verifier plus Gradle gate.

## Covered

- `app/src/androidTest/assets/npm_compat_real/manifest.json` now carries
  `phase13Compatibility` metadata with schema
  `autojs6-phase13-dependency-compatibility-corpus-v1`.
- The taxonomy covers the required P13-47 reason kinds:
  `missing_builtin`, `native_addon`, `postinstall`, `network`, and `fs`.
- `targetedFixtureDeltas` records the Phase 13 stdlib +5 pass delta for
  `stdlib-digest-stream-package`, `perf-async-hooks-package`,
  `url-buffer-codec-package`, `util-events-package`, and
  `path-querystring-package`.
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
- `PHASE13_SUMMARY.md` records the 55 fixed offline fixtures, stdlib
  `targetedFixtureDeltas` +5 pass delta, and differential pass-rate rows.
- The differential runner can import connected AutoJs6 artifacts directly with
  `--autojs-connected-results <dir|file>`, scanning Gradle connected test
  result directories, XML reports, logcat text, and test logs for
  `realnpm.differential.result=` JSON lines.
- Unsafe package execution remains denied in default CI. Native addons,
  lifecycle scripts, postinstall downloads, and executable extraction are
  represented as metadata blocker categories, not vendored runnable payloads.

## Closure

The v1.1 corpus gate closes because the fixed 55-fixture manifest taxonomy,
required reason kinds, targeted stdlib pass-rate delta, metadata
instrumentation, differential category reporting, summary rows, unsafe payload
denials, docs, surface manifest, and Gradle gate are synchronized.

## Future Work

- CI still needs to schedule the connected AutoJs6 real npm corpus and pass its
  connected-results artifact directory into the host differential report.
- The corpus remains fixed and offline after the 55-fixture expansion; live
  registry drift testing is still a separate security and reproducibility
  problem.
- Future capability relaxations should update the same category rows so
  pass-rate changes are visible by reason kind.

## Evidence

- Manifest taxonomy:
  `app/src/androidTest/assets/npm_compat_real/manifest.json`
- Added fixed package fixtures:
  `stdlib-digest-stream-package`, `perf-async-hooks-package`, and
  `url-buffer-codec-package`, `util-events-package`, and
  `path-querystring-package`
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
