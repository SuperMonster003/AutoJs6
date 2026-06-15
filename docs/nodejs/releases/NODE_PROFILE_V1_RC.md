# AutoJs6 Node Profile v1 Release Candidate

Date: 2026-06-15

Status: release-candidate record created, not yet approved for publication.

This document defines the intended AutoJs6 Safe Node Profile v1 surface and records the current release-candidate gate results. It is not a claim of full desktop Node.js compatibility.

## V1 Stable Surface

Script routing and runtime:

- `"nodejs";` and `"node";` directives
- `.node.js`, `.cjs`, and `.mjs` routing into the Node backend
- `.mjs` route still rejects ESM unless the experimental ESM gate is explicitly enabled
- embedded backend as default
- backend switches preserved: `rhino`, `native`, `embedded`, `auto`
- native one-shot backend retained as regression oracle
- `:nodejs_embedded` script process isolation
- `require("autojs6:profile")` diagnostics

CommonJS and package resolution:

- CommonJS `require()`, `module.exports`, `exports`, and JSON modules
- `node_modules`, scoped packages, package `main`, `exports`, `imports`, and self-reference
- safe partial `module` / `node:module`, including scoped `createRequire()`
- resolver trace and resolver visualizer diagnostics

Filesystem and data APIs:

- scoped `fs` sync/callback/promisify surface
- scoped `fs/promises` limited surface
- scoped file URL helpers
- canonical path, symlink, realpath, absolute path, NUL, `/proc`, `/sys`, and `/dev` denial
- package target containment for `exports` and `imports`

Builtins:

- stable or partial low-risk builtins documented in `docs/nodejs/COMPATIBILITY_PROFILE.md`
- limited compatibility wrappers for raw network-shaped modules that reject I/O
- dangerous process APIs denied with stable errors

AutoJs bridge modules:

- `toast`
- `app`
- `engines`
- `dialogs`
- `accessibility`
- `media_projection`
- `image` / `images`
- `clipboard`
- `device`
- `shell`
- `fetch`
- `websocket`
- `work_manager`

Packaging and release:

- packaged APK native library injection and ABI pruning
- packaged APK diagnostic reports
- packaged host smoke verifier
- release flavor policy: `nodeFull`, `nodeMini`, `rhinoOnly`
- native dependency and symbol archive reports
- doctor report
- compatibility profile v2 generator
- dependency bundler policy report
- checked-in examples and migration docs

## V1 Non-Goals

- full desktop Node.js
- unrestricted npm install or postinstall execution
- unrestricted native `.node` addons
- unrestricted `child_process`
- unrestricted Java or Android object access
- unrestricted raw `http`, `https`, `net`, `tls`, `dns`, or `dgram`
- default ESM execution
- default dynamic import execution
- default live network provider
- default worker threads
- inspector/debugger server
- unrestricted `vm`
- `process.binding`, `process.dlopen`, `_linkedBinding`, or unrestricted process termination APIs

## Generated Artifacts

Current host artifacts generated during this RC pass:

- `app/build/reports/nodejs/compatibility-profile.json`
- `app/build/reports/nodejs/compatibility-profile.md`
- `app/build/reports/nodejs/doctor/node-doctor-report.txt`
- `app/build/reports/nodejs/doctor/node-doctor-report.json`
- `app/build/reports/native-node/native-deps-appDebug.txt`
- `app/build/reports/native-node/native-symbol-archive-appDebug.txt`
- `app/build/reports/embedded-node/packaging-preflight-appDebug.txt`
- `app/build/reports/nodejs/dependencies/dependency-manifest.json`
- `app/build/reports/nodejs/dependencies/dependency-rejections.txt`
- `app/build/reports/nodejs/dependencies/dependency-size-report.txt`

Current compatibility profile identity:

- schema: `autojs6-node-compatibility-profile-v2`
- profile: `AutoJs6 Safe Node Profile`
- engine: `autojs6-node-profile-v1`
- expected Node: `v24.5.0`
- default backend: `embedded`
- release flavor: `nodeFull`
- expected ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`
- unsupported ABI: `x86`

## Gate Results

Host gates:

- `:app:verifyEmbeddedNodeDebug`: passed on 2026-06-15.
- `:app:verifyNodeCompatibilityProfile`: passed as part of `verifyEmbeddedNodeDebug`.
- `:app:nativeNodeDependencyReportAppDebug`: passed as part of `verifyEmbeddedNodeDebug`.
- `:app:nativeNodeSymbolArchiveReport`: passed as part of `verifyEmbeddedNodeDebug`.
- `:app:embeddedNodeDoctorReport`: passed as part of `verifyEmbeddedNodeDebug`.
- `:app:verifyNodeDependencyBundlerFixtures`: passed as part of `verifyEmbeddedNodeDebug`.
- `:app:verifyNodeResolverVisualizerFixtures`: passed as part of `verifyEmbeddedNodeDebug`.

Device gates on Xiaomi `bek749scrwv4wo8h` before the device disconnected:

- `NodeSecurityRegressionInstrumentationTest`: passed in the combined run.
- `NodeNativeAddonPolicyInstrumentationTest`: passed in the combined run.
- `NodeRawNetworkPolicyInstrumentationTest`: passed in the combined run.
- `NodeResolverFuzzerInstrumentationTest`: passed after accepting `ERR_AUTOJS6_NATIVE_ADDON_DISABLED` as a stable resolver rejection code.
- `NodeScopedFsFuzzerInstrumentationTest`: passed in the combined run.
- `NodeCompatCorpusInstrumentationTest`: passed in the combined run.
- `NodeRealNpmCompatInstrumentationTest`: passed in the combined run.

Combined command that passed 10 tests:

```powershell
$env:ANDROID_SERIAL='bek749scrwv4wo8h'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeSecurityRegressionInstrumentationTest,org.autojs.autojs.engine.NodeNativeAddonPolicyInstrumentationTest,org.autojs.autojs.engine.NodeRawNetworkPolicyInstrumentationTest,org.autojs.autojs.engine.NodeResolverFuzzerInstrumentationTest,org.autojs.autojs.engine.NodeScopedFsFuzzerInstrumentationTest,org.autojs.autojs.engine.NodeCompatCorpusInstrumentationTest,org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest"
```

## Known Blockers

The full release-candidate gate is not green yet.

1. `:app:verifyEmbeddedNodeReleaseCandidate` failed without `-Pautojs.embeddedNode.adbSerial` because multiple devices were attached.
2. The same gate failed with the Xiaomi serial because the expected host packaged smoke APK had already been removed from `app/build/embedded-node-packaged-smoke/PackagedNodeSmoke_v1.0.0.apk`.
3. A full `EmbeddedNodePackagedApkSmokeTest` run on Xiaomi exceeded the tool timeout and was stopped after generating nine APKs on the device, but before producing `PackagedNodeSmoke_v1.0.0.apk`.
4. A full `NodeEmbeddedMemoryLeakStressInstrumentationTest` run exceeded 11 minutes and was stopped.
5. A shortened memory stress probe failed due to device disconnection during test teardown: `device 'bek749scrwv4wo8h' not found`.
6. After that teardown failure, Xiaomi was no longer present in `adb devices`; only Sony `QV710AF65F` and an unapproved `OPD2413` device were visible. Per test policy, this RC did not switch to those devices.

These blockers are environmental or gate-completeness blockers, not evidence that raw network, child process, native addon, scoped fs, or resolver policies were loosened.

## Required Rerun Before Publication

Use Xiaomi `bek749scrwv4wo8h` or another explicitly approved device. Do not use Sony `QV710AF65F` for this branch while it is reserved for another AutoJs6 branch.

```powershell
$env:ANDROID_SERIAL='bek749scrwv4wo8h'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.apkbuilder.EmbeddedNodePackagedApkSmokeTest"

$env:ANDROID_SERIAL='bek749scrwv4wo8h'
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate "-Pautojs.embeddedNode.adbSerial=bek749scrwv4wo8h" "-Pautojs.embeddedNode.packagedApk=D:\idea-projects\AutoJs6\app\build\embedded-node-packaged-smoke\PackagedNodeSmoke_v1.0.0.apk" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"

$env:ANDROID_SERIAL='bek749scrwv4wo8h'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest"
.\gradlew.bat --console=plain :app:pullNodeEmbeddedPerformanceReports
```

Optional shorter memory probe for local triage:

```powershell
$env:ANDROID_SERIAL='bek749scrwv4wo8h'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEmbeddedMemoryLeakStressInstrumentationTest" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.iterations=5" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.imageIterations=3" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.leak.faultIterations=2"
```

## Known Issues List

- Packaged smoke generation is too slow for the current tool timeout when run as the full instrumentation class.
- Memory leak stress is too slow for the current tool timeout with default iterations.
- RC aggregate needs a stable way to declare the approved adb serial in multi-device environments.
- `version.properties` is auto-touched by assemble/debug gates and should not be included in Node Profile RC commits unless the release process intentionally bumps it.

## Migration And Reference Links

- `docs/nodejs/COMPATIBILITY_PROFILE.md`
- `docs/nodejs/SECURITY_MODEL.md`
- `docs/nodejs/TESTING.md`
- `docs/nodejs/RELEASE_CHECKLIST.md`
- `docs/nodejs/MIGRATION_FROM_RHINO.md`
- `docs/nodejs/COMPATIBILITY_NOTES_PRO_AUTOX.md`
- `docs/nodejs/EXAMPLES.md`
- `docs/nodejs/PERFORMANCE.md`
- `docs/nodejs/NATIVE_CRASH_DIAGNOSTICS.md`

## RC Decision

AutoJs6 Node Profile v1 has a coherent stable surface and current host/security/compatibility gates are passing after the resolver fuzzer update. Publication should wait until the packaged smoke APK and full memory stress gates complete on an approved connected device and the resulting reports are attached to this RC record.
