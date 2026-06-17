# Embedded Node Rollback Playbook

This playbook is for maintainers rolling back Node Profile v1 without breaking legacy Rhino scripts.

Rollback is a release operation, not a silent runtime fallback. Node-marked scripts must fail clearly when Node is disabled or missing, while ordinary Rhino scripts continue to use the Rhino engine.

## Fast Triage

Collect the current diagnostic bundle first:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodeDoctorReport
.\gradlew.bat --console=plain :app:nativeNodeDependencyReportAppDebug
.\gradlew.bat --console=plain :app:embeddedNodePackagingPreflightAppDebug
```

Attach the generated reports from:

```text
app/build/reports/nodejs/
app/build/reports/embedded-node/
```

Record the failing device, ABI, release flavor, app version, git commit, and whether the failure is in the main app, packaged APK, or both.

## Disable Embedded Execution

Use this when the native libraries can still be packaged but embedded script execution should be blocked while the issue is investigated:

```powershell
.\gradlew.bat --console=plain :app:assembleAppDebug `
  "-Pautojs.nodejs.embedded.script.enabled=false"
```

Expected behavior:

- embedded script execution fails before native entry
- `errorCode=ERR_AUTOJS6_NODE_DISABLED`
- the message names `-Pautojs.nodejs.embedded.script.enabled=true`
- legacy Rhino scripts still run through the normal Rhino engine

Do not treat this as a compatibility mode for Node Profile v1 scripts.

## Native One-shot Diagnostic Mode

Use this only to compare embedded lifecycle behavior against the older native one-shot backend:

```powershell
.\gradlew.bat --console=plain :app:assembleAppDebug `
  "-Pautojs.nodejs.backend=native"
```

Expected behavior:

- `ENGINE_NODEJS` uses the native one-shot process backend
- embedded lifecycle, bridge, and service-process behavior are bypassed
- the run is recorded as diagnostic-only in release notes

Do not promote a release based solely on native one-shot success. The embedded backend remains the product runtime for Node Profile v1.

## Publish Rhino-only Flavor

Use this when a release must preserve legacy Rhino scripts and remove Embedded Node native libraries from the distributed APK:

```powershell
.\gradlew.bat --console=plain :app:verifyRhinoOnlyBuild `
  "-Pautojs.nodejs.releaseFlavor=rhinoOnly"
```

Expected behavior:

- the app APK does not package `libnode.so`
- the app APK does not package `libautojs6-node.so`
- `embeddedNodePackagingPreflightAppDebug` reports `release flavor policy ready: true`
- Node-marked scripts fail clearly instead of silently running as ordinary Rhino scripts

Use `nodeFull` or `nodeMini` again before advertising Node Profile v1 support.

## Packaged Template Rollback

If packaged APK generation is affected:

1. Stop publishing packaged Node APKs from the affected template.
2. Keep packaged verifier output for the failing artifact:

```powershell
.\gradlew.bat --console=plain :app:embeddedNodePackagedApkOutputVerify `
  "-Pautojs.embeddedNode.packagedApk=<path-to-apk>"
.\gradlew.bat --console=plain :app:embeddedNodePackagedDiagnosticReport `
  "-Pautojs.embeddedNode.packagedApk=<path-to-apk>"
```

3. Refresh or replace `app/src/main/assets-app/template.apk` only through the documented template refresh gate.
4. Re-run packaged smoke on the primary device before restoring packaged Node release channels.

Do not manually delete native libraries from a packaged APK and publish it as a Node-enabled build. Use `rhinoOnly` only for a deliberate non-Node rollback artifact.

## Compatibility Profile Rollback

When reverting profile behavior:

1. Revert only the profile entries that match the code rollback.
2. Update `COMPATIBILITY_PROFILE.md`, `NODE_PROFILE_V1_STABLE_SURFACE.md`, and `NODE_PROFILE_V1_KNOWN_ISSUES.md`.
3. Re-run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
.\gradlew.bat --console=plain :app:generateNodeProfileReleaseManifest
```

The release manifest must identify the rollback commit and the effective feature flags.

## Restore Node Profile v1

Before ending rollback:

```powershell
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeDebug
.\gradlew.bat --console=plain :app:verifyEmbeddedNodePackagedDebug
.\gradlew.bat --console=plain :app:verifyEmbeddedNodeReleaseCandidate
```

On the primary device, rerun the current release gate and store the output under `docs/nodejs/reports/phase4/`.

## Release Notes

Every rollback must record:

- rollback reason and first bad version
- selected fallback path: disabled embedded execution, native one-shot diagnostic, or `rhinoOnly`
- affected ABIs and devices
- Node Doctor report path
- packaged APK verifier path if packaged output was affected
- whether Node Profile v1 scripts are supported in the rollback artifact
- restore criteria and owner
