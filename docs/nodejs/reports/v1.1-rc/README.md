# Node Profile v1.1 RC Readiness

This report track verifies whether the v1.1 beta evidence is ready to enter
release-candidate planning. It is not a release tag, publication approval, or
replacement for the production release gate.

Run after the Sony beta gate has passed:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11RcReadiness "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

The task writes:

```text
app/build/reports/nodejs/v1.1-rc-readiness.json
```

Required inputs:

- `docs/nodejs/releases/NODE_PROFILE_V1_1_RC_PLAN.md`
- `docs/nodejs/releases/NODE_PROFILE_V1_1_BETA_BLOCKERS.md`
- `build/reports/nodejs/v1.1-beta-summary.json`
- `app/build/reports/nodejs/v1.1-beta-blockers.json`

Required signal:

- beta summary is generated for `v1_1_beta`,
- beta summary records a clean git state,
- latest beta connected-test XML has zero failures, errors, and skips,
- compatibility, doctor, differential, npm, packaged smoke, error catalog, and
  blocker reports are available,
- packaged smoke aggregate readiness fields are true, and
- every beta blocker has status `deferred` or `closed`.

After this readiness gate passes, run the production release gate before any RC
tag or publication decision:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.test.deviceProfile=sony"
```

Keep OnePlus OPD2413 out of unattended RC gates because APK installation on
that device still requires manual confirmation.
