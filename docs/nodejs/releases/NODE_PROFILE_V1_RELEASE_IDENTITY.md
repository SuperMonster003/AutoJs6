# AutoJs6 Node Profile v1 Release Identity

Status: pending maintainer sign-off.

This document makes the Node Profile v1 release identity explicit. It separates the identity that has already passed the
GA candidate gate from the identity that will be used for a public AutoJs6 release.

## Current Tested Identity

The valid Phase 4 GA candidate gate was run on Sony XQ-AT72 `QV710AF65F` with the temporary current-branch identity:

- app package: `org.autojs.autojs6.nodejs`
- app label: `AutoJs6 Node`
- test package: `org.autojs.autojs6.nodejs.test`
- inrt package: `org.autojs.autojs6.nodejs.inrt`
- namespace: `org.autojs.autojs6`
- gate evidence: [sony-ga-candidate](../reports/phase4/sony-ga-candidate/README.md)

This identity allowed side-by-side testing on Sony while other AutoJs6 branches were installed. It is valid GA-candidate
runtime evidence, but it is not automatically a public release identity decision.

## Production Identity Decision

Maintainer decision: `pending`.

Known production identity candidate:

- app package: `org.autojs.autojs6`
- app label: `AutoJs6`
- inrt package: `org.autojs.autojs6.inrt`
- namespace: `org.autojs.autojs6`

Decision options:

- restore production identity for public GA;
- keep `org.autojs.autojs6.nodejs` only for a preview or test tag;
- add a durable `nodejsTest` or equivalent flavor so production and test identities are both reproducible.

## Gate Implications

If the public release identity remains `org.autojs.autojs6.nodejs`, the Task 128 Sony gate is identity-matched.

If the public release identity changes back to `org.autojs.autojs6`, the current Sony gate must be treated as a
test-identity GA candidate. A final-identity validation gate is required before public GA:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"
```

At minimum, the final-identity gate must confirm:

- main APK installs with the selected package identity;
- packaged APK full smoke passes;
- Node Doctor reports the selected package identity;
- release manifest reports the selected package identity, label, version, commit, and tag;
- crash buffer remains clean for foreground-service startup, fatal exception, request spool, and embedded service crash
  markers.

## Release Manifest Identity Fields

`generateNodeProfileReleaseManifest` records the current build identity under `app`:

- `applicationId`
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
- `identityStatus`, defaulting to `test-identity-ga-candidate`
- `identityDoc`

For the final public tag, run manifest generation from a clean worktree with explicit properties, for example:

```powershell
.\gradlew.bat --console=plain :app:generateNodeProfileReleaseManifest "-Pautojs.nodejs.releaseTag=<approved-tag>" "-Pautojs.nodejs.releaseIdentityStatus=<approved-status>"
```

Allowed `identityStatus` values are documentation-level labels. Suggested values:

- `test-identity-ga-candidate`
- `production-identity-pending-gate`
- `production-identity-gate-passed`
- `preview-identity-approved`

## Information Needed For Task 132

Task 132 needs the maintainer to choose one release identity path:

- restore production identity for GA;
- keep the Node test identity for a non-production preview tag;
- add/keep both production and test identities through a reproducible flavor strategy.

Do not create a GA tag until this decision is recorded and the required identity-matched gate evidence exists.
