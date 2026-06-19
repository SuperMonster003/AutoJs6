# Node 24.x Patch Upgrade Feasibility

Generated: 2026-06-19T15:06:07.130Z

## Decision

- Result: **no-go**
- Summary: NO-GO for switching to v24.17.0. Default runtime remains v24.5.0.
- Default runtime switch ready: false
- Current runtime: v24.5.0
- Candidate runtime: v24.17.0
- Candidate source: https://nodejs.org/en/blog/release/v24.17.0

## Blockers

- headers: candidate headers were not supplied
- libraries.arm64-v8a: candidate jniLibs directory was not supplied
- libraries.armeabi-v7a: candidate jniLibs directory was not supplied
- libraries.x86_64: candidate jniLibs directory was not supplied
- processVersions: candidate process.versions evidence was not supplied
- startupAndRss: candidate performance report was not supplied
- cjsEsmBehavior: candidate CJS/ESM differential report was not supplied
- packagedApkBehavior: candidate packaged APK smoke report was not supplied

## Current Baseline

- Runtime constants: app/src/main/java/org/autojs/autojs/engine/NodeRuntimeCapabilities.kt
- Expected process version prefix: v24.5.0
- Header Node module version: 137
- Compatibility profile: app/build/reports/nodejs/compatibility-profile.json
- Performance report: build/reports/nodejs/node-profile-v1-ga/performance/embedded-node-performance-1781429181600.json

| ABI | Size | SHA-256 | Build ID | DT_NEEDED | Defined | Undefined |
| --- | ---: | --- | --- | --- | ---: | ---: |
| arm64-v8a | 109,577,224 | b1f9b6f95be74336 | - | libc++_shared.so, libc.so, libdl.so, liblog.so, libm.so | 102379 | 545 |
| armeabi-v7a | 104,299,492 | ee50698e264ce78a | - | libc++_shared.so, libc.so, libdl.so, liblog.so, libm.so | 101892 | 550 |
| x86_64 | 112,324,176 | a62031b38a467027 | - | libc++_shared.so, libc.so, libdl.so, liblog.so, libm.so | 103989 | 549 |

## Candidate Artifacts

- Headers: not supplied
- jniLibs: not supplied

| ABI | Status | Size | SHA-256 | DT_NEEDED | Defined | Undefined |
| --- | --- | ---: | --- | --- | ---: | ---: |
| arm64-v8a | missing | - | - | - | - | - |
| armeabi-v7a | missing | - | - | - | - | - |
| x86_64 | missing | - | - | - | - | - |

## Comparison Matrix

| Area | Status | Notes |
| --- | --- | --- |
| ABI / headers | blocked | candidate headers were not supplied |
| libnode.so arm64-v8a | blocked | candidate jniLibs directory was not supplied |
| libnode.so armeabi-v7a | blocked | candidate jniLibs directory was not supplied |
| libnode.so x86_64 | blocked | candidate jniLibs directory was not supplied |
| process.versions | blocked | candidate process.versions evidence was not supplied |
| startup / RSS | blocked | candidate performance report was not supplied |
| CJS / ESM behavior | blocked | candidate CJS/ESM differential report was not supplied |
| packaged APK behavior | blocked | candidate packaged APK smoke report was not supplied |

## Required Next Steps

- Build Node v24.17.0 from the same Android fork/toolchain.
- Provide candidate headers and jniLibs for arm64-v8a, armeabi-v7a, and x86_64.
- Run this report with candidate artifact and evidence properties.
- Run the focused modern core, packaged APK, performance, and compatibility gates against the candidate.
- Only consider default runtime changes after a separate dual-runtime A/B gate passes.

## Re-run

```powershell
.\gradlew.bat --console=plain :app:generateNode24PatchUpgradeFeasibilityReport
.\gradlew.bat --console=plain :app:generateNode24PatchUpgradeFeasibilityReport "-Pautojs.nodejs.patchTargetVersion=24.17.0" "-Pautojs.nodejs.patchCandidateJniLibs=D:\\path\\to\\jniLibs" "-Pautojs.nodejs.patchCandidateHeaders=D:\\path\\to\\include\\node"
```

This report is informational by default. It records a no-go decision without failing the Gradle task unless the script is run with `--fail-on-no-go`.
