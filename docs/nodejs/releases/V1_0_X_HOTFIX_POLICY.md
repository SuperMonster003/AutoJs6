# Node Profile v1.0.x Hotfix Policy

This policy defines what can enter Node Profile v1.0.x after
`node-profile-v1.0.0`. Patch releases keep the GA contract stable; they are not a
vehicle for new default-on runtime authority.

## Allowed Changes

- release-build crashes and native abort fixes.
- packaged APK build, verifier, launch, template, or native library fixes.
- scoped fs security fixes and stricter path diagnostics.
- resolver, CommonJS, package metadata, and stable builtin compatibility fixes
  that preserve documented behavior.
- bridge resource leak, lifecycle cleanup, timeout, and cancellation fixes.
- Node Doctor, release manifest, crash intake, error catalog, and known-issues
  updates.
- compatibility profile corrections when the correction matches the shipped
  runtime behavior.
- test flake fixes that preserve assertion strength.

## Not Allowed In v1.0.x

- default-on experimental features.
- broad builtin expansion without a documented promotion gate.
- native addon enablement.
- unrestricted `child_process`.
- raw Node network modules such as `http`, `https`, `net`, `tls`, `dns`, or
  `dgram`.
- unrestricted Java/Android interop.
- unrestricted workers or `vm`.
- scoped fs relaxation or package containment relaxation.
- removal of Rhino fallback, backend switches, or native one-shot diagnostics.
- stable error-code reuse for a different meaning.

## Minimum Hotfix Gate

Every v1.0.x hotfix candidate must run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePrGate `
  "-Pautojs.nodejs.identity=production"
```

If the change touches packaged APKs, native libs, template reproducibility, or
release flavor policy, also run:

```powershell
.\gradlew.bat --console=plain :app:verifyEmbeddedNodePackagedDebug `
  "-Pautojs.nodejs.identity=production"
```

If the change touches runtime execution, bridge providers, scoped fs, lifecycle,
memory, or Android service behavior, also run on Sony XQ-AT72:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.test.deviceProfile=smoke" `
  "-Pautojs.nodejs.fuzzerBudget=quick"
```

If the fix is P0/P1, packaged-only, native, or release-risky, rerun the release
gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate `
  "-Pautojs.nodejs.identity=production" `
  "-Pautojs.nodejs.test.deviceProfile=sony" `
  "-Pautojs.nodejs.fuzzerBudget=normal"
```

## Backport Decision Record

Each v1.0.x hotfix PR or release note must record:

- bug class and severity.
- first known bad commit or version.
- affected identities: production, `nodejsTest`, packaged APK, or all.
- affected ABI and Android version if relevant.
- why the fix is allowed under this policy.
- gate commands and result.
- whether Node Profile v1.1 also needs the fix.

Use [CRASH_INTAKE.md](../CRASH_INTAKE.md) for crash-specific evidence and
[POST_GA_SMOKE.md](../POST_GA_SMOKE.md) for recurring gate cadence.
