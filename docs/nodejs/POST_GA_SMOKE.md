# Node Profile Post-GA Smoke Schedule

This runbook starts after `node-profile-v1.0.0` is signed off. It turns the
maintenance cadence from [MAINTENANCE_PLAN.md](MAINTENANCE_PLAN.md) into
commands that can be copied into local runs, CI jobs, or nightly device jobs.

## Invariants

- Keep Rhino fallback and the `rhino`, `native`, `embedded`, and `auto` backend
  switches available.
- Keep native one-shot as a regression oracle.
- Do not loosen scoped fs policy in v1.0.x.
- Do not default-enable native addons, unrestricted `child_process`, raw
  network, inspector, unrestricted Java/Android interop, unrestricted workers,
  or unrestricted `vm`.
- Treat packaged APK smoke as part of every release-quality check, not as an
  optional demo path.

## Daily Local Smoke

Run after Node-related code, docs, packaging, or Gradle task changes:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePrGate
```

Scope:

- Kotlin compilation for app and androidTest sources.
- compatibility profile and error catalog verification.
- resolver visualizer fixtures.
- dependency bundler safe/unsafe fixtures.
- packaging preflight and template reproducibility.

Escalate to a device smoke when a change touches embedded lifecycle, bridge
providers, scoped fs runtime behavior, packaged launch, or native libraries.

## Device Smoke

Use Sony XQ-AT72 `QV710AF65F` for unattended device smoke:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.test.deviceProfile=smoke" `
  "-Pautojs.nodejs.fuzzerBudget=quick"
```

Record the test XML, device serial, package identity, commit, and any skipped
tests. Do not retry scoped fs, security, bridge capability, packaged script, or
resource leak failures to make a smoke pass.

## Weekly Packaged APK Smoke

Run at least once per active Node development week and before handing a patch
branch to QA:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.test.deviceProfile=packaged" `
  "-Pautojs.nodejs.fuzzerBudget=quick"

.\gradlew.bat --console=plain :app:embeddedNodePackagedSmokeHostVerify `
  "-Pautojs.nodejs.identity=production"
```

If the packaged APK smoke APKs are not present in the host export directory,
rerun the packaged instrumentation smoke first. Store host verifier output from:

```text
build/reports/embedded-node/packaged-smoke-aggregate.txt
build/reports/embedded-node/packaged-apk-output-verify-success.txt
build/reports/embedded-node/packaged-apk-output-verify-diagnostics.txt
```

## Nightly Extended Gate

Use for active Node development and before any release candidate branch:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeNightlyGate `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.test.deviceProfile=sony" `
  "-Pautojs.nodejs.fuzzerBudget=extended"

.\gradlew.bat --console=plain :app:generateNodeNightlyReport `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.test.deviceProfile=sony" `
  "-Pautojs.nodejs.fuzzerBudget=extended"
```

Attach:

- `build/reports/nodejs/nightly-report.md`
- fuzzer seeds from `build/reports/nodejs/fuzz-seeds/`
- packaged smoke reports
- memory and performance reports
- crash buffer excerpts

## Monthly Extended Fuzzer Run

Run when resolver, scoped fs, bridge protocol, CommonJS resolution, or package
metadata handling changes:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.test.deviceProfile=sony" `
  "-Pautojs.nodejs.fuzzerBudget=extended"
```

Keep failing seeds. Do not discard a seed unless the failure is proven to be a
test infrastructure issue and documented in [FLAKE_POLICY.md](FLAKE_POLICY.md).

## Regression Bisect

Use [tools/nodejs/bisect-node-smoke.ps1](../../tools/nodejs/bisect-node-smoke.ps1)
when a regression first appears between two commits:

```powershell
git bisect start <bad> <good>
git bisect run powershell -NoProfile -ExecutionPolicy Bypass -File tools/nodejs/bisect-node-smoke.ps1
```

Add `-RunDeviceSmoke -AndroidSerial QV710AF65F` only when the regression is not
visible in host/static checks.
