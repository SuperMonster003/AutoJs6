# R4 On-Demand Module Preparation Acceptance Sampling

> [!WARNING]
> Historical R4 evidence for the retired nodeMini/embedded path. The provider design
> remains useful context, but current execution is plugin-only. See
> [INTEGRATION_PLAN.md](INTEGRATION_PLAN.md).

This note records the commands, report shape, and completed primary-device
results for R4 acceptance. Compile success, a focused instrumentation pass,
and an unfilled JSON record alone are not device acceptance evidence.

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

Run the reviewed real-file harness and take a finite logcat snapshot after it
returns:

```powershell
$artifactDir = Join-Path (Get-Location) 'app\build\reports\nodejs\r4-acceptance\final-run'
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
adb -s $env:ANDROID_SERIAL logcat -c

adb -s $env:ANDROID_SERIAL shell am instrument -w -r `
    -e class org.autojs.autojs.engine.NodeR4AcceptanceInstrumentationTest#pairedBaselineAndLargeCorpusMeetR4WarmSlo `
    -e autojs.nodejs.r4.acceptance true `
    org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

adb -s $env:ANDROID_SERIAL logcat -d -v epoch |
    Set-Content -Encoding utf8 (Join-Path $artifactDir 'r4-final-logcat.txt')

$deviceReport = '/storage/emulated/0/Android/data/org.autojs.autojs6/files/nodejs/r4-acceptance/node-r4-acceptance-<timestamp>.json'
adb -s $env:ANDROID_SERIAL pull $deviceReport `
    (Join-Path $artifactDir 'node-r4-acceptance-final.json')
```

Replace `<timestamp>` with the report path printed by the completed test. The
harness runs one excluded primer and 30 qualified warm samples for each entry
in both fixtures. It uses `NativeNodeEmbeddedJavaScriptEngine` with
`JavaScriptFileSource`, so engine request preparation and the normal real-file
path remain in scope. A direct service call is not an acceptance substitute.

## Required Diagnostics

Every recorded real-file sample must include:

- `timing.engine_request_preparation.ms`;
- end-to-end launcher elapsed time;
- the raw `clientPhaseTimings` map and
  `wallMinusClientAttemptTotalMs` cross-check;
- encrypted scan decision `on_demand_provider` and status `skipped`;
- zero examined paths, walked files, candidate files, header probes, and
  full-file reads in the legacy scan diagnostics;
- module-provider request count, resolved-source count, decrypted-source count,
  source bytes, elapsed time, and denial reason;
- `embedded_script.module_provider.legacy_directory_scan_ran=false`;
- PID, pool slot, session generation, process-runtime generation, and execution
  sequence used to qualify the sample as runtime-warm.
- native payload compaction and probe-generation mode markers, so omitted
  optional placeholders cannot be mistaken for unavailable runtime phases.

On-demand encrypted single-module cases must show that only the actually
resolved candidate was requested and decrypted. A statically resolved encrypted
CommonJS candidate may instead be decrypted through the exact preload closure
with zero provider requests; its preload diagnostics must name only that
closure. Diagnostics and artifacts must never contain decrypted source,
encryption keys, or private cache paths.

## Final Sony XQ-AT72 Results

The reviewed run completed on 2026-07-18 with the following immutable
identity. The installed external plugin is recorded for reproducibility, but
this R4 run deliberately exercised the embedded `nodeMini` backend; external
plugin connection lifecycle remains R5 scope.

| Item | Recorded value |
| --- | --- |
| Device | Sony XQ-AT72, Android 12 / API 31, arm64-v8a |
| Thermal and power | 100% battery; 34.7 C before acceptance, 35.5 C after the final run |
| AutoJs6 source | `a0dfdce0d16fd264c5428c7a8edecec714402567` |
| Runtime source | `652b3bf32e6d370df888ee734bfc1480853cd6a7` |
| Host artifact | AutoJs6 6.8.0 Alpha7, version code 5221, `nodeMini` |
| Host APK SHA-256 | `ABBB53FFC95AEC4800B3F71FAE719681189244ABB4AABC8731458394C2C9072A` |
| Installed runtime plugin | 1.0.0, version code 33 |
| Runtime plugin APK SHA-256 | `08DB2E3980BD3DB763FA572DE8BE02106DD9E9526F22B6DA5D9B7E6999637AF8` |

The focused Android real-file corpus passed 7 of 7 tests, the host resolver
contract passed 10 of 10 cases, and the runtime project assembled its
arm64-v8a, armeabi-v7a, and x86_64 variants. A separate 10-sample diagnostic
run measured warm wall p50/p95 at 299/314 ms and native p50/p95 at 158/164 ms.
The script-execution fast path reduced generated native diagnostic entries
from 4341 to 437 before final transport compaction, which sent 383 entries.

The formal acceptance test completed 1 of 1 in 62.583 seconds. It ran one
primer plus 30 qualified warm samples for each paired scenario:

| Fixture | Entry | Preparation p50 / p95 | Warm wall p50 / p95 / max |
| --- | --- | ---: | ---: |
| Baseline | Module-free | 3 / 6 ms | 325 / 371 / 373 ms |
| 4000 unrelated files | Module-free | 3 / 5 ms | 320 / 340 / 345 ms |
| Baseline | Static CommonJS | 6 / 8 ms | 310 / 333 / 343 ms |
| 4000 unrelated files | Static CommonJS | 5 / 7 ms | 305 / 322 / 329 ms |
| Baseline | Static ESM | 4 / 5 ms | 338 / 362 / 369 ms |
| 4000 unrelated files | Static ESM | 3 / 4 ms | 324 / 341 / 349 ms |

All 186 executions succeeded and selected the on-demand provider with zero
legacy scan activity. The primary slot, PID, session generation, and process
runtime generation remained stable while execution sequence advanced from 1
through 186. All 14 recorded criteria passed; every paired preparation-p95
delta was 1 ms, below the 20 ms gate.

The completed report is retained at
`app/build/reports/nodejs/r4-acceptance/a0dfdce0/node-r4-acceptance-final.json`
with SHA-256
`48DD22CFE64179BFA1C00BBB986F5B1EE4AE8E73246C4408082AB084C176BA9E`.
The short diagnostic report is retained beside it as
`r4-diagnostic-source-fast-path.json` with SHA-256
`B77FE24C0767A63C820E33096F750BF7FEC6F404B2EA8C069CEB7B03922BBE42`.
Raw logcat is kept locally for debugging but is not a shareable acceptance
artifact because Android runtime messages may contain private cache paths.

## Acceptance Record Schema

The instrumentation emits schema `autojs6-node-r4-acceptance-v1`. Its
top-level contract is:

```json
{
  "schema": "autojs6-node-r4-acceptance-v1",
  "generatedAtEpochMs": 0,
  "device": {
    "manufacturer": "",
    "brand": "",
    "model": "",
    "device": "",
    "product": "",
    "sdkInt": 0,
    "release": "",
    "supportedAbis": []
  },
  "build": {
    "applicationId": "",
    "versionName": "",
    "versionCode": 0,
    "buildType": "",
    "nodeReleaseFlavor": "nodeMini",
    "embeddedScriptExecutionEnabled": true,
    "persistentRuntimeBuildEnabled": true,
    "persistentRuntimeEffectiveEnabled": true,
    "esmEnabled": true,
    "acceptanceArgument": true
  },
  "fixture": {
    "pairedRoots": [],
    "cases": [],
    "unrelatedExtensions": []
  },
  "samplePlan": {
    "primerCountPerScenario": 1,
    "runtimeWarmSampleCountPerScenario": 30,
    "scenarioCount": 6,
    "expectedTotalExecutions": 186,
    "actualTotalExecutions": 0,
    "percentileMethod": "nearest_rank"
  },
  "distributions": {
    "scenarios": [],
    "largeVsBaseline": [],
    "runtimeContinuity": []
  },
  "criteria": {
    "overallPassed": false,
    "checks": []
  },
  "resultKeyAvailability": {
    "requiredIdentityKeys": [],
    "nativePidKey": "",
    "pidSourcePreference": [],
    "pidPresentSampleCount": 0,
    "pidMissingSampleIndices": [],
    "availableRequiredKeys": [],
    "missingRequiredKeys": [],
    "availableNativeValueKeys": []
  },
  "rawSamples": []
}
```

Each embedded raw sample preserves its sequence, fixture and case ids, phase,
ordinal, entry path, wall and preparation timings, terminal result, scan
decision, `clientPhaseTimings`, wall-time cross-check, request-preparation and
legacy-scan diagnostics, runtime identity, and module-provider diagnostics.
Keep these samples rather than only the calculated percentiles.

## Device Acceptance Checklist

The completed record and its raw samples have been reviewed:

- [x] Primary Sony identity, build identity, thermal state, and fixture manifest are recorded.
- [x] Module-free, static CommonJS, and static ESM each have at least 30 qualified runtime-warm samples.
- [x] Every normal sample has zero project-wide scan candidates and reports `legacy_directory_scan_ran=false`.
- [x] Module-free and single-module request-preparation p95 are at most 50 ms.
- [x] The 4000-file corpus changes request-preparation p95 by no more than 20 ms.
- [x] Runtime-warm end-to-end p95 is at most 400 ms.
- [x] Static/dynamic encrypted cases touch only resolved candidates and preserve compatibility/security behavior.
- [x] Non-trivial module-free source shapes pass without a source-pattern performance allowlist.
