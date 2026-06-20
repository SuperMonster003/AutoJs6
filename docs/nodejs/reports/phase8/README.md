# Phase 8 Reports

Phase 8 begins with Node.js `v24.17.0` candidate validation while keeping
`v24.5.0` as the default runtime. The candidate is build-only through
`-Pautojs.nodejs.runtimeSlot=node24_current`; users and packaged projects cannot
select arbitrary native runtimes.

## Batch A Gates

Current Batch A report tasks:

```powershell
.\gradlew.bat --console=plain :app:generateNode2417NativeDiffReport
.\gradlew.bat --console=plain :app:generateNode2417SecurityProbeReport
.\gradlew.bat --console=plain :app:generateNode2417UpgradeGateReport
```

Generated outputs:

- `build/reports/nodejs/node-24.17-native-diff.json`
- `build/reports/nodejs/node-24.17-native-diff.md`
- `build/reports/nodejs/node-24.17-security-probes.json`
- `build/reports/nodejs/node-24.17-security-probes.md`
- `build/reports/nodejs/node-24.17-upgrade-gate.json`
- `build/reports/nodejs/node-24.17-upgrade-gate.md`

The upgrade gate also consumes:

- `build/reports/nodejs/patch-upgrade/node24-patch-upgrade-feasibility.json`
- `build/reports/nodejs/patch-upgrade/node-dual-runtime-ab-gate.json`

## Candidate Inputs

Candidate runs must provide:

- `-Pautojs.nodejs.patchCandidateJniLibs=<dir>` containing
  `arm64-v8a/libnode.so`, `armeabi-v7a/libnode.so`, and `x86_64/libnode.so`
- `-Pautojs.nodejs.patchCandidateHeaders=<dir>` containing Node headers
- matching compatibility, security, packaged, memory, and performance reports
  for the `node24_current` slot

`verifyNode2417UpgradeGate` additionally requires:

- `-Pautojs.nodejs.runtimeSlot=node24_current`
- `-Pautojs.nodejs.test.deviceProfile=sony`
- `-Pautojs.nodejs.network.experimental=true`
- `ANDROID_SERIAL=QV710AF65F`

## Current Status

As of the first Phase 8 Batch A implementation pass, the gate is expected to be
`blocked` because no Node `v24.17.0` Android `libnode.so` artifacts or candidate
device evidence are checked in. This is intentional: the reports define the
intake and promotion boundary without switching the default runtime.

The security probe source plan is present in
`NodeSecurityRegressionInstrumentationTest#phase8Node2417SecurityFixProbesStaySafe`.
It covers NUL hostname rejection, raw TLS/HTTP2 denial, credential redaction on
denied HTTPS URLs, WebCrypto quota rejection, and scoped FileHandle behavior.

## Batch B TypeScript

Current Batch B implementation status:

- `.ts`, `.mts`, and `.cts` route to Embedded Node with host-side lightweight
  type stripping before native execution.
- `.mts` is ESM, `.cts` is CommonJS, and `.ts` follows package `type`.
- `.tsx` is explicitly rejected with
  `ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION`.
- Transform-required TypeScript syntax is rejected before launch with
  `ERR_UNSUPPORTED_TYPESCRIPT_SYNTAX` and diagnostics for source, syntax kind,
  line, and column.
- Injected `moduleSources` and runtime module sources are stripped before the
  request reaches native Node.
- Project wizard templates now cover CommonJS TypeScript, ESM TypeScript,
  automation, OCR, long-running task, and build-time-only TSX.
- Checked examples now include `typescript-cjs`, `typescript-esm`, and
  `packaged-typescript`.

Focused gates:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeTypeScriptGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeProjectWizard
.\gradlew.bat --console=plain :app:verifyNodeExamples
```

The current packaged TypeScript evidence is project/moduleSources compatible.
Full exported APK installation smoke remains part of the broader packaged gate
before TypeScript can be treated as a release-complete Phase 8 capability.

## Batch C CJS require(ESM)

Current Batch C implementation status:

- CommonJS `require()` can synchronously load partial ESM graphs from `.mjs`,
  `.mts`, and `type=module` `.js` targets.
- The returned value is the ESM namespace object.
- CommonJS package resolution keeps the CommonJS condition set
  `require`, `node`, `default`; `require()` does not select `import`
  conditions.
- Sync-required ESM graphs containing `await` or dynamic `import()` reject with
  `ERR_REQUIRE_ASYNC_MODULE`.
- Native addons, raw loader hooks, raw network, disabled builtins, scoped fs
  escapes, and unrestricted `node:module` authority remain denied.

Focused gates:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeEsmExperimentalInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:compileAppDebugKotlin :app:compileAppDebugAndroidTestKotlin
```

Packaged smoke expectations now cover both the positive packaged `.mjs`
`require()` path and the async-graph denial path.
