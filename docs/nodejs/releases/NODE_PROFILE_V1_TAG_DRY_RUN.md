# AutoJs6 Node Profile v1 Tag Dry Run

Status: dry run only. Do not create a tag until the maintainer approves the final package identity, version/build state,
commit, and tag name.

Suggested tag placeholder: `<approved-tag>`

Suggested tag message placeholder: `AutoJs6 Node Profile v1 GA`

## Preconditions

Before creating the tag:

```powershell
git status --short --branch
git rev-parse HEAD
git log --oneline -5
```

Required state:

- worktree is clean;
- `NODE_PROFILE_V1_GA.md` has no `TBD` final commit/tag placeholders;
- `NODE_PROFILE_V1_RELEASE_IDENTITY.md` records the approved identity decision;
- `app/build/reports/nodejs/release-manifest.json` was regenerated from the approved final commit and version state;
- the release manifest `git.commit` equals `git rev-parse HEAD`;
- the release manifest `release.tag` equals `<approved-tag>`;
- the release manifest `app.identityProfile` equals `production`;
- production-identity Sony gate evidence is recorded because the public release identity differs from the Task 128 test
  identity.

## Dry-Run Checks

Inspect the final commit:

```powershell
git rev-parse HEAD
git show --stat --oneline --summary HEAD
```

Inspect the release manifest:

```powershell
Get-Content app\build\reports\nodejs\release-manifest.json | ConvertFrom-Json | Select-Object -ExpandProperty git
Get-Content app\build\reports\nodejs\release-manifest.json | ConvertFrom-Json | Select-Object -ExpandProperty app
Get-Content app\build\reports\nodejs\release-manifest.json | ConvertFrom-Json | Select-Object -ExpandProperty release
```

Regenerate and collect final artifacts after the approved identity and version state are committed:

```powershell
.\gradlew.bat --console=plain :app:generateNodeCompatibilityProfile :app:generateDesktopNodeDifferentialReport :app:nativeNodeDependencyReportAppDebug :app:nativeNodeSymbolArchiveReport :app:embeddedNodeDoctorReport :app:embeddedNodePackagingPreflightAppDebug :app:verifyNodeTemplateReproducibility :app:embeddedNodePackagedSmokeHostVerify :app:nativeNodeAbiSizeReport :app:pullNodeEmbeddedPerformanceReports :app:generateNodeProfileReleaseManifest :app:generateNodeNightlyReport "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.releaseTag=<approved-tag>" "-Pautojs.nodejs.releaseIdentityStatus=<approved-status>" "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=normal"
.\gradlew.bat --console=plain :app:collectNodeProfileGaArtifacts
```

If `assembleAppDebug` auto-increments `version.properties`, either restore that local churn or commit it deliberately
before tagging.

## Tag Commands

Only run these after maintainer approval:

```powershell
git tag -a <approved-tag> -m "AutoJs6 Node Profile v1 GA"
git show --stat --oneline --summary <approved-tag>
```

Do not push automatically. Push only after the post-tag verification script passes:

```powershell
.\tools\nodejs\verify-node-profile-tag.ps1 -Tag <approved-tag>
```

Optional static checks:

```powershell
.\tools\nodejs\verify-node-profile-tag.ps1 -Tag <approved-tag> -RunStaticChecks
```

The script defaults to `-ExpectedIdentityProfile production`, `-ExpectedApplicationId org.autojs.autojs6`, and
`-ExpectedAppLabel AutoJs6`. Override those only for an explicitly approved preview/test tag.

Optional device smoke:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\tools\nodejs\verify-node-profile-tag.ps1 -Tag <approved-tag> -RunStaticChecks -RunDeviceSmoke -AndroidSerial QV710AF65F
```

## Failure Policy

Do not tag if:

- release identity is still pending or the manifest identity profile is not `production`;
- release notes still contain final `TBD` placeholders;
- release manifest commit differs from `HEAD`;
- release manifest tag differs from the approved tag;
- package identity in the manifest differs from the approved identity;
- final identity changed after Task 128 and no final-identity Sony gate was recorded;
- Gradle report generation changed `version.properties` unintentionally.
