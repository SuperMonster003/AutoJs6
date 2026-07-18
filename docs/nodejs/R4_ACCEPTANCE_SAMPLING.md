# R4 On-Demand Module Preparation Acceptance Sampling

This note defines the commands and report shape for R4 device acceptance. It
does not contain device results. Compile success, a focused instrumentation
pass, and an unfilled JSON record are not device acceptance evidence.

## Scope

R4 acceptance uses the primary Sony device and the real file-engine path. It
must prove that source shape and unrelated working-directory contents no longer
select a project-wide encrypted-module scan, while plain and encrypted
CommonJS/ESM resolution remains compatible and scoped.

The acceptance run requires:

- one representative non-trivial module-free entry containing `const`/`let`,
  expressions, functions, timers, multiple console calls, and non-loading async
  work;
- one single-module static CommonJS entry;
- one single-module static ESM entry;
- plain and encrypted static CommonJS, dynamic CommonJS, static ESM, dynamic
  import, missing-module, and package-resolution checks;
- a working directory containing at least 4000 unrelated files, including
  JavaScript-like candidates;
- a paired baseline fixture with the same entries and resolved modules but
  without the unrelated-file corpus.

Do not include the cold primer in runtime-warm distributions. A sample is
runtime-warm only when PID, process-pool slot, session generation, and process
runtime generation match the preceding accepted sample and execution sequence
increases by one.

## Build And Focused Regression Commands

Select the device and sibling runtime artifacts:

```powershell
$env:ANDROID_SERIAL = "<primary-sony-serial>"
$nodeRuntimeProject = (Resolve-Path ..\AutoJs6-Plugin-NodeJs-Runtime).Path
$nodeBuildArgs = @(
    '-Pautojs.nodejs.releaseFlavor=nodeMini'
    '-Pautojs.nodejs.releaseAbis=arm64-v8a'
    "-Pautojs.nodejs.pluginProjectDir=$nodeRuntimeProject"
    "-Pautojs.nodejs.runtimeJniLibs=$nodeRuntimeProject\app\src\main\jniLibs"
    '-Pksp.incremental=false'
    '--console=plain'
)
```

Compile the app and instrumentation sources:

```powershell
& .\gradlew.bat :app:compileAppDebugKotlin @nodeBuildArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& .\gradlew.bat :app:compileAppDebugAndroidTestKotlin @nodeBuildArgs
exit $LASTEXITCODE
```

Run the focused real-file R4 corpus:

```powershell
$gradleArgs = @(
    ':app:connectedAppDebugAndroidTest'
    '-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedEngineRequestPreparationInstrumentationTest'
) + $nodeBuildArgs

& .\gradlew.bat @gradleArgs
exit $LASTEXITCODE
```

This command proves the focused correctness contract, including its generated
4000-file fixture. It does not produce the 30-sample launcher distributions.

## Real-File Sampling Commands

Record immutable device and build identity before sampling:

```powershell
adb -s $env:ANDROID_SERIAL shell getprop ro.product.manufacturer
adb -s $env:ANDROID_SERIAL shell getprop ro.product.model
adb -s $env:ANDROID_SERIAL shell getprop ro.build.version.release
adb -s $env:ANDROID_SERIAL shell getprop ro.build.version.sdk
adb -s $env:ANDROID_SERIAL shell getprop ro.product.cpu.abi
git rev-parse HEAD
git status --short
```

Clear and capture logcat around the real launcher runs:

```powershell
$artifactDir = Join-Path (Get-Location) 'app\build\reports\nodejs\r4-acceptance'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
adb -s $env:ANDROID_SERIAL logcat -c
adb -s $env:ANDROID_SERIAL logcat -v epoch > (Join-Path $artifactDir 'r4-real-file-logcat.txt')
```

While logcat capture is active, use the normal AutoJs6 file launcher:

1. Run one cold primer for each entry and exclude it from distributions.
2. Run 30 consecutive runtime-warm samples for module-free, static CommonJS,
   and static ESM entries in the 4000-file fixture.
3. Repeat the same 30-sample sequence against the paired fixture without the
   unrelated files.
4. Run the plain/encrypted, static/dynamic, missing, and package compatibility
   cases and retain their provider diagnostics.
5. Stop logcat capture after every required terminal result has been written.

The launcher workflow may be automated by a reviewed device-side harness, but
the harness must still use the real file engine and preserve the warm
qualification fields below. Direct service calls are not substitutes because
they bypass engine request preparation.

## Required Diagnostics

Every recorded real-file sample must include:

- `timing.engine_request_preparation.ms`;
- end-to-end launcher elapsed time;
- encrypted scan decision `on_demand_provider` and status `skipped`;
- zero examined paths, walked files, candidate files, header probes, and
  full-file reads in the legacy scan diagnostics;
- module-provider request count, resolved-source count, decrypted-source count,
  source bytes, elapsed time, and denial reason;
- `embedded_script.module_provider.legacy_directory_scan_ran=false`;
- PID, pool slot, session generation, process-runtime generation, and execution
  sequence used to qualify the sample as runtime-warm.

On-demand encrypted single-module cases must show that only the actually
resolved candidate was requested and decrypted. A statically resolved encrypted
CommonJS candidate may instead be decrypted through the exact preload closure
with zero provider requests; its preload diagnostics must name only that
closure. Diagnostics and artifacts must never contain decrypted source,
encryption keys, or private cache paths.

## Acceptance Record Schema

Store a completed record as JSON using schema
`autojs6-node-r4-acceptance-v1`. The following is an intentionally unfilled
shape, not a result:

```json
{
  "schema": "autojs6-node-r4-acceptance-v1",
  "status": "pending",
  "generatedAt": null,
  "device": {
    "serial": null,
    "manufacturer": null,
    "model": null,
    "androidRelease": null,
    "sdk": null,
    "abi": null
  },
  "build": {
    "gitCommit": null,
    "worktreeStatus": null,
    "versionName": null,
    "releaseFlavor": "nodeMini",
    "runtimeBackend": null
  },
  "fixture": {
    "pairedFixtureId": null,
    "unrelatedFileCount": null,
    "javascriptLikeUnrelatedFileCount": null,
    "resolvedModuleCount": null,
    "entryHashes": {}
  },
  "percentileMethod": "nearest-rank",
  "distributions": {
    "moduleFree": {
      "runtimeWarmSamples": 0,
      "requestPreparationMs": { "p50": null, "p95": null, "max": null },
      "endToEndMs": { "p50": null, "p95": null, "max": null }
    },
    "staticCommonJs": {
      "runtimeWarmSamples": 0,
      "requestPreparationMs": { "p50": null, "p95": null, "max": null },
      "endToEndMs": { "p50": null, "p95": null, "max": null }
    },
    "staticEsm": {
      "runtimeWarmSamples": 0,
      "requestPreparationMs": { "p50": null, "p95": null, "max": null },
      "endToEndMs": { "p50": null, "p95": null, "max": null }
    }
  },
  "pairedUnrelatedFileDeltaMs": {
    "moduleFreeRequestPreparationP95": null,
    "staticCommonJsRequestPreparationP95": null,
    "staticEsmRequestPreparationP95": null
  },
  "moduleProviderCases": [
    {
      "case": null,
      "plainOrEncrypted": null,
      "staticOrDynamic": null,
      "resolutionPath": null,
      "preloadedSourceCount": null,
      "requestCount": null,
      "resolvedSourceCount": null,
      "decryptedSourceCount": null,
      "sourceBytes": null,
      "elapsedMs": null,
      "denialReason": null,
      "legacyDirectoryScanRan": null,
      "terminalErrorCode": null
    }
  ],
  "criteria": {
    "allRuntimeWarmSampleCountsAtLeast30": null,
    "allLegacyScanCandidateCountsZero": null,
    "moduleFreeRequestPreparationP95AtMost50Ms": null,
    "singleModuleRequestPreparationP95AtMost50Ms": null,
    "unrelatedFileRequestPreparationP95DeltaAtMost20Ms": null,
    "runtimeWarmEndToEndP95AtMost400Ms": null,
    "encryptedCasesTouchOnlyResolvedCandidates": null,
    "moduleFreeShapeCorpusPassed": null
  },
  "artifacts": {
    "focusedInstrumentationLog": null,
    "realFileLogcat": null,
    "rawSamples": null,
    "fixtureManifest": null
  }
}
```

Each raw sample referenced by the record must preserve the entry/corpus id,
sample index, all required diagnostics, warm-qualification fields, and terminal
result. Keep raw samples rather than only the calculated percentiles.

## Device Acceptance Checklist

Leave these items unchecked until a completed record and its raw artifacts are
reviewed:

- [ ] Primary Sony identity, build identity, thermal state, and fixture manifest are recorded.
- [ ] Module-free, static CommonJS, and static ESM each have at least 30 qualified runtime-warm samples.
- [ ] Every normal sample has zero project-wide scan candidates and reports `legacy_directory_scan_ran=false`.
- [ ] Module-free and single-module request-preparation p95 are at most 50 ms.
- [ ] The 4000-file corpus changes request-preparation p95 by no more than 20 ms.
- [ ] Runtime-warm end-to-end p95 is at most 400 ms.
- [ ] Static/dynamic encrypted cases touch only resolved candidates and preserve compatibility/security behavior.
- [ ] Non-trivial module-free source shapes pass without a source-pattern performance allowlist.
