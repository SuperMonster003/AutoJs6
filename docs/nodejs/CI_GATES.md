# Embedded Node CI Gates

This document separates fast host checks from heavy device and release checks for Node Profile v1.

## PR Gate

Use `verifyNodePrGate` for ordinary pull requests and local pre-submit checks:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePrGate
```

Scope:

- app and androidTest Kotlin compilation
- compatibility profile generation and docs/registry verification
- resolver visualizer fixtures
- checked-in Node example manifest validation
- dependency bundler safe/unsafe fixtures
- main APK packaging preflight
- template reproducibility
- packaged app integration dry run

Non-goals:

- no connected-device instrumentation
- no packaged APK install/launch smoke
- no memory/lifecycle stress
- no desktop Node differential comparison

## Device PR Gate

When a device is available, use the shortest matching device profile from [DEVICE_MATRIX.md](DEVICE_MATRIX.md).

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=smoke"
```

Scope:

- embedded smoke
- bridge smoke
- packaged toast bridge smoke

Use explicit single-class or single-method commands for focused fixes. Passing `android.testInstrumentationRunnerArguments.class` overrides device profile class selection.

## Nightly Gate

Use `verifyNodeNightlyGate` only with an explicit heavy device profile:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeNightlyGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

Scope:

- PR gate
- debug host gate
- packaged host gate
- desktop Node differential harness
- connected device instrumentation selected by `sony` or `full`
- lifecycle and memory stress through the selected device profile
- fuzzers and real npm corpus through the selected device profile

The task fails immediately if `autojs.nodejs.test.deviceProfile` is not `sony` or `full`.
When the requested task is `verifyNodeNightlyGate` or `verifyNodeReleaseGate`, fuzzer instrumentation defaults to
`autojs.nodejs.fuzzerBudget=extended` unless the property is explicitly overridden.

After nightly or release gates finish, `generateNodeNightlyReport` writes:

```text
build/reports/nodejs/nightly-report.json
build/reports/nodejs/nightly-report.md
```

The report summarizes commit state, device/profile, latest connected-test XML counts and failures, perf and memory reports, fuzzer seeds, packaged smoke readiness, native hashes, and experimental feature flags.
Fuzzer failure seeds are collected from connected-test XML into `build/reports/nodejs/fuzz-seeds/` and can be replayed with `:app:replayNodeFuzzerSeed`.

## v1.1 Alpha Gate

Use `verifyNodeV11AlphaGate` for the v1.1 functional milestone. It measures feature progress without creating a release tag:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11AlphaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_alpha"
```

Scope:

- PR host gate and debug host gate
- desktop Node differential harness
- v1.1 ESM, npm, controlled network, WebSocket, expanded bridge, security, resource stress, memory stress, worker design-gate, and packaged v1.1 smoke instrumentation
- JSON summary at `build/reports/nodejs/v1.1-alpha-summary.json`

The `v1_1_alpha` profile uses shorter default stress budgets than nightly: `autojs.nodejs.leak.iterations=12`,
`autojs.nodejs.leak.imageIterations=6`, `autojs.nodejs.leak.faultIterations=3`, and
`autojs.nodejs.bridgeStress.iterations=8`. Override those Gradle properties for local probes.

The task fails unless `autojs.nodejs.test.deviceProfile=v1_1_alpha` is explicit. Keep `ANDROID_SERIAL` pointed at the Sony unattended device when multiple devices are attached.

## v1.1 Beta Gate

Use `verifyNodeV11BetaGate` after the alpha milestone when stabilizing v1.1 for
release-candidate planning:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11BetaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

Scope:

- all v1.1 alpha capability coverage
- lifecycle, fs promises, native-addon denial, resolver fuzzer, and scoped-fs
  fuzzer regressions
- packaged host verification
- error catalog, project wizard, and TypeScript declaration checks
- beta blocker register verification
- JSON summary at `build/reports/nodejs/v1.1-beta-summary.json`

Packaged `.mjs` and local dynamic import install-and-run smokes now have a
focused gate that uses the default production packaged template:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.identity=production" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

The focused gate validates the packaged runtime compatibility descriptor version
vector and reports packaged fixture cache hit/miss lines while still installing
and launching the generated APKs on device.

The beta gate fails unless `autojs.nodejs.test.deviceProfile=v1_1_beta` is explicit.
Keep `ANDROID_SERIAL` pointed at the Sony unattended device when multiple
devices are attached.

The beta profile keeps the default normal fuzzer budget. Use
`-Pautojs.nodejs.fuzzerBudget=extended` only as a separate stress subgate.

## v1.1 RC Readiness Gate

Use `verifyNodeV11RcReadiness` after a passing v1.1 beta gate and before
production release-candidate gate planning:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11RcReadiness "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

Scope:

- regenerates the v1.1 beta summary from current host reports,
- verifies the beta summary schema, clean git state, and `v1_1_beta` profile,
- verifies the latest beta connected-test XML has zero failures, errors, and
  skips,
- verifies required beta reports and packaged smoke aggregate readiness,
- verifies all beta blocker statuses are `deferred` or `closed`,
- writes `app/build/reports/nodejs/v1.1-rc-readiness.json`.

This gate does not create a tag and does not replace `verifyNodeReleaseGate`.
See [reports/v1.1-rc/README.md](reports/v1.1-rc/README.md) and
[releases/NODE_PROFILE_V1_1_RC_PLAN.md](releases/NODE_PROFILE_V1_1_RC_PLAN.md).

## Phase 7 Focused Gates

Phase 7 uses focused feature gates instead of expanding every iteration to the
full release gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveProviderGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeLiveFetchGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.network.experimental=true"
.\gradlew.bat --console=plain :app:verifyNodeLongRunningGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeWorkerGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeModernCoreGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

`verifyNodePhase7Gate` combines the current focused gate set. These gates select
existing focused instrumentation classes automatically unless
`android.testInstrumentationRunnerArguments.class` is supplied explicitly.
The live-provider gate includes the provider availability registry, Doctor
provider-health reporting, and current fail-closed/fake-provider classifications;
it does not promote future Android providers by itself.
`verifyNodeLiveFetchGate` is an explicit experimental subgate: it fails unless
`autojs.nodejs.network.experimental=true` is set, then runs loopback
controlled-fetch/axios Android I/O plus provider destroy cancellation coverage.

## Release Gate

Use `verifyNodeReleaseGate` for release candidates:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

Scope:

- release manifest generation
- desktop Node differential harness
- `verifyEmbeddedNodeReleaseCandidate`
- connected device instrumentation selected by `sony` or `full`

The release gate still relies on packaged smoke APK artifacts being current when packaged host verification is part of the selected path. See [TESTING.md](TESTING.md) for packaged smoke artifact collection.

## Manual Heavy Checks

Some checks remain explicit because they need a prepared device, a recently exported APK, or a failure-specific budget:

- reduced/full `NodeEmbeddedMemoryLeakStressInstrumentationTest`
- worker resource stress
- packaged APK output verifier with `-Pautojs.embeddedNode.packagedApk=<apk>`
- `verifyRhinoOnlyBuild` rollback verification
- template refresh through `embeddedNodeRefreshInrtTemplateApk`

Do not add retries around security assertions, resource leak assertions, or packaged smoke script failures. Use [FLAKE_POLICY.md](FLAKE_POLICY.md) for classification.
