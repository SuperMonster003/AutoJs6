# AutoJs6 Node Profile v1 Release Identity

Status: production identity selected; final identity gate passed on Sony XQ-AT72.

This document makes the Node Profile v1 release identity explicit. The public AutoJs6 GA identity is the production
AutoJs6 identity. A separate `nodejsTest` Gradle identity profile remains available for side-by-side device gates.

## Current Tested Identity

The valid Phase 4 GA candidate gate was run on Sony XQ-AT72 `QV710AF65F` with the temporary current-branch identity:

- app package: `org.autojs.autojs6.nodejs`
- app label: `AutoJs6 Node`
- test package: `org.autojs.autojs6.nodejs.test`
- inrt package: `org.autojs.autojs6.nodejs.inrt`
- namespace: `org.autojs.autojs6`
- gate evidence: [sony-ga-candidate](../reports/phase4/sony-ga-candidate/README.md)

This remains valid runtime evidence for the Node implementation, but it is now classified as test-identity GA candidate
evidence rather than final public release evidence.

## Production Identity

Maintainer decision: restore production AutoJs6 identity for public GA.

Default Gradle identity:

```properties
autojs.nodejs.identity=production
```

Effective production identity:

- app package: `org.autojs.autojs6`
- app label: `AutoJs6`
- test package: `org.autojs.autojs6.test`
- inrt package: `org.autojs.autojs6.inrt`
- namespace: `org.autojs.autojs6`

The default `app` and `inrt` debug/release tasks now build this production identity. The source namespace remains
`org.autojs.autojs6`.

## Side-by-side Test Identity

For Sony or local side-by-side gates where another AutoJs6 branch is installed, pass:

```powershell
.\gradlew.bat --console=plain :app:processAppDebugManifestForPackage "-Pautojs.nodejs.identity=nodejsTest"
```

Effective `nodejsTest` identity:

- app package: `org.autojs.autojs6.nodejs`
- app label: `AutoJs6 Node`
- test package: `org.autojs.autojs6.nodejs.test`
- inrt package: `org.autojs.autojs6.nodejs.inrt`
- namespace: `org.autojs.autojs6`

This profile appends `.nodejs` through Gradle configuration. It keeps existing task names such as `appDebug` and
`inrtDebug`, so release/test scripts do not need a second product-flavor matrix.

## Gate Implications

Because the public release identity is now `org.autojs.autojs6`, the Task 128 Sony gate must be treated as a
test-identity GA candidate. The required final-identity validation gate passed on 2026-06-17 from commit `857e6e82`:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"
```

Evidence:

- production gate record: [sony-production-identity-final-gate](../reports/phase4/sony-production-identity-final-gate/README.md)
- identity under test: `org.autojs.autojs6` / `AutoJs6`
- duration: `2h 2m 10s`
- Android test XML: `310` tests, `0` failures, `0` errors, `2` expected skips
- Gradle result: `BUILD SUCCESSFUL`

The final-identity gate confirmed:

- main APK installs with `org.autojs.autojs6`;
- packaged APK full smoke passes;
- Node Doctor reports the selected package identity;
- release manifest reports the selected package identity, label, version, commit, and tag;
- crash buffer remains clean for foreground-service startup, fatal exception, request spool, and embedded service crash
  markers.

## Release Manifest Identity Fields

`generateNodeProfileReleaseManifest` records the current build identity under `app`:

- `applicationId`
- `baseApplicationId`
- `identityProfile`
- `applicationIdSuffix`
- `namespace`
- `label`
- `testApplicationId`
- `inrtApplicationId`
- `inrtApplicationIdSuffix`
- `inrtLabel`
- `versionName`
- `versionCode`
- `buildTime`

It also records release sign-off fields under `release`:

- `tag`, defaulting to `TBD`
- `identityStatus`, defaulting to `production-identity-pending-final-gate` for production identity
- `identityDoc`

For the final public tag, run manifest generation from a clean worktree with explicit properties, for example:

```powershell
.\gradlew.bat --console=plain :app:generateNodeProfileReleaseManifest "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.releaseTag=node-profile-v1.0.0" "-Pautojs.nodejs.releaseIdentityStatus=production-identity-final-gate-passed"
```

Allowed `identityStatus` values are documentation-level labels. Suggested values:

- `test-identity-ga-candidate`
- `production-identity-pending-final-gate`
- `production-identity-final-gate-passed`
- `preview-identity-approved`

Production-identity gate evidence now exists. Do not create a GA tag until the release manifest is regenerated from the
approved final commit, version state, release tag, and `production-identity-final-gate-passed` status.
