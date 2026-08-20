# Node Profile Crash Intake

Use this workflow for crashes, native aborts, packaged launch failures, bridge
deadlocks, and reports where a Node script appears to terminate the app or
embedded service process.

## Required Attachments

Ask for these before triage starts:

- AutoJs6 version name and version code.
- Node Profile version or release tag.
- package identity, for example `org.autojs.autojs6` or a side-by-side test
  identity.
- device model, Android version, API level, ABI, and whether the app is a
  release, debug, or packaged APK build.
- script entry type: editor script, project file, packaged APK entry, `.cjs`,
  `.mjs`, package bin, or `package.json` script entry.
- selected backend, Node identity/profile, stable capability states, and any
  non-stable profile surface involved.
- Node Doctor JSON/text report.
- logcat excerpt covering process start through crash or timeout.
- native tombstone or Android bugreport excerpt when a native crash occurred.
- minimal reproduction project or script, with secrets removed.
- packaged APK verifier report when the failure is packaged-only.

Do not ask users to attach private script repositories or unrestricted logcat
captures. Request the smallest reproduction and the crash window only.

## Local Collection Commands

```powershell
.\gradlew.bat --console=plain :app:embeddedNodeDoctorReport `
  "-Pautojs.nodejs.identity=production"

.\gradlew.bat --console=plain :app:nativeNodeDependencyReportAppDebug `
  "-Pautojs.nodejs.identity=production"

.\gradlew.bat --console=plain :app:nativeNodeSymbolArchiveReport `
  "-Pautojs.nodejs.identity=production"

.\gradlew.bat --console=plain :app:nativeNodeAbiSizeReport `
  "-Pautojs.nodejs.identity=production"

.\gradlew.bat --console=plain :app:collectNodeCrashBundle

.\gradlew.bat --console=plain :app:symbolizeNodeCrash

.\gradlew.bat --console=plain :app:verifyNodeCrashBundle
```

Relevant outputs:

```text
app/build/reports/nodejs/doctor/node-doctor-report.json
app/build/reports/nodejs/doctor/node-doctor-report.txt
app/build/reports/native-node/native-deps-appDebug.txt
app/build/reports/native-node/native-symbol-archive-appDebug.txt
app/build/reports/native-node/native-abi-size-appDebug.txt
build/reports/nodejs/final-crash-evidence-bundle/manifest.json
build/reports/nodejs/final-crash-evidence-bundle/resource-snapshot.json
build/reports/nodejs/final-crash-evidence-bundle/timeline-tail.json
build/reports/nodejs/final-crash-symbolization.json
build/reports/nodejs/final-crash-bundle-verification.json
```

For packaged APK failures with an APK artifact:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedApkOutputVerify `
  "-Pautojs.embeddedNode.packagedApk=<path-to-apk>" `
  "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"

.\gradlew.bat --console=plain :app:embeddedNodePackagedDiagnosticReport `
  "-Pautojs.embeddedNode.packagedApk=<path-to-apk>" `
  "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

## Triage Labels

Use these labels consistently:

- `node-native-crash`: libnode, V8, libuv, JNI, symbol archive, or native ABI
  failures.
- `node-packaged`: packaged APK build, install, launch, template, manifest,
  native lib, or packaged asset failures.
- `node-bridge`: AutoJs bridge provider, bridge protocol, resource handle, or
  capability declaration failures.
- `node-fs-sandbox`: scoped fs, resolver path, symlink, file URL, working
  directory, or package target escape failures.
- `node-resolver`: CommonJS, package exports/imports, dynamic require fallback,
  or resolver trace failures.
- `node-capability`: ESM, worker, controlled/native network, child process,
  Java interop, `vm`, or other capability failures.

Severity:

- P0: security boundary bypass, data exposure, or reproducible release-build
  crash on stable APIs.
- P1: packaged APK launch crash, embedded lifecycle crash, memory runaway, or
  bridge resource leak.
- P2: stable CommonJS, scoped fs, bridge, resolver, or packaged compatibility
  regression.
- P3: partial/non-stable surface failure, diagnostics polish, device infrastructure,
  or documentation-only issue.

## First Response Checklist

1. Confirm whether the report is main app or packaged APK.
2. Confirm production identity versus side-by-side test identity.
3. Check whether the failing API is stable, partial, disabled, unsupported, or
   planned.
4. Check the logcat crash process: main app, `:nodejs_embedded`, packaged app,
   or test instrumentation.
5. Check the release manifest and Node Doctor for feature flags and native
   library readiness.
6. If native, symbolicate against the matching native symbol archive before
   closing or backporting.
7. If packaged-only, run the packaged APK verifier before changing runtime code.

## Closure Requirements

P0/P1 issues require:

- a focused regression test or documented reason the issue is not testable;
- a release note or known-issues update;
- a backport decision for the v1.0.x line;
- updated crash intake attachments in the issue or release evidence directory.

Do not close a native crash as fixed until the symbol archive, ABI size report,
and matching commit are recorded.
