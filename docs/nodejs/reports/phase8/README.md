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
