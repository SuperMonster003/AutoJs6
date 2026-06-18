# Node Profile v1.1 Beta Gate

This report defines the stabilization gate for Node Profile v1.1 beta. It is
not a release candidate, tag, or production-identity gate.

Run it on the unattended Sony device:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11BetaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

The gate writes:

```text
build/reports/nodejs/v1.1-beta-summary.json
app/build/reports/nodejs/v1.1-beta-blockers.json
```

Required coverage:

- v1.1 alpha capability coverage for ESM, real npm corpus, controlled network,
  WebSocket, expanded bridge modules, packaged v1.1 bridge smoke, security, and
  resource stress.
- Lifecycle regressions through `NodeEmbeddedLifecycleStressInstrumentationTest`.
- Scoped fs and resolver fuzzers.
- Native addon denial.
- fs promises compatibility.
- Packaged host verification through `verifyEmbeddedNodePackagedDebug`.
- Error catalog, project wizard, and TypeScript declaration checks.
- Beta blocker register verification through `verifyNodeV11BetaBlockerRegister`.
- Worker design gate by default; worker runtime stress only when
  `autojs.nodejs.worker_threads.experimental=true`.

Default beta stress arguments are higher than alpha but lower than a full
nightly:

```text
autojs.nodejs.leak.iterations=20
autojs.nodejs.leak.imageIterations=8
autojs.nodejs.leak.faultIterations=5
autojs.nodejs.bridgeStress.iterations=12
```

Override those Gradle properties for local probes.

The beta profile keeps the default `normal` fuzzer budget. Run
`-Pautojs.nodejs.fuzzerBudget=extended` only as a separate stress subgate so a
new extended-fuzzer seed does not block the normal beta stabilization signal.

The task fails unless `autojs.nodejs.test.deviceProfile=v1_1_beta` is explicit.
Keep `ANDROID_SERIAL` pointed at Sony XQ-AT72 `QV710AF65F` when multiple devices
are attached.

## Packaged ESM Focused Gate

Packaged `.mjs`, package `type=module`, and dynamic import install-and-run
smokes now run against the default production inrt template:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.identity=production" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

This focused gate keeps the beta gate fast while making packaged ESM and local
dynamic import independently runnable before RC readiness.

## Pass Meaning

A passing beta gate means the v1.1 alpha surface is stable enough to consider
release-candidate planning. It does not promote controlled network live
providers, long-running mode, worker_threads, floaty/overlay, native addons,
raw process, or raw network modules.

Before starting RC gate planning, run the v1.1 RC readiness check:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11RcReadiness "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

It writes `app/build/reports/nodejs/v1.1-rc-readiness.json` and requires every
beta blocker to be `deferred` or `closed`.

## Initial Validation

Validated on 2026-06-18 Asia/Shanghai against Sony XQ-AT72 `QV710AF65F`:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11BetaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

Result:

- Build: passed.
- Connected instrumentation: 146 tests, 0 failures, 0 errors, 0 skipped.
- Fuzzer seeds: 0 discovered, 0 written.
- Summary JSON: `build/reports/nodejs/v1.1-beta-summary.json`.
- Blocker register report: `app/build/reports/nodejs/v1.1-beta-blockers.json`.
