# P13-42 Android Package Manager

P13-42 records Android package manager parity as partial.

The current target is "Auto.js Pro capability + desktop Node.js capability",
but package installation is still split between a host-managed npm-compatible
validator and an Android app-private local package store substrate. This phase
does not promote the npm CLI, registry download, lifecycle script execution, bin
shim creation, native build execution, or a JavaScript package-manager facade on
Android.

## Landed

- `autojs6:profile.packageManagerProfile` reports the Android package-manager
  boundary:
  - `status`: `android_substrate_partial`
  - host package manager: `available`
  - Android store: `local_app_private_partial`
  - Android runtime install: `local_unpacked_only`
  - npm CLI: `denied`
  - equivalent install path: `host_managed_plus_android_store_substrate`
  - registry download: `deferred`
  - tarball install: `host_side_only`
  - lockfile: `autojs6_lock_host_managed`
  - integrity: `sha512_required`
  - lifecycle scripts, bin links, symlinks, native build/native payloads, and
    postinstall downloads: `denied`
  - JS bridge: `not_exposed`
  - raw executable and raw registry authority: `false`
- `NodeAndroidPackageInstallManager.nativeValues()` now exposes the same
  Android-side policy details for native test reports.
- `NodePackageManagerPhase13DecisionInstrumentationTest` verifies the runtime
  profile, denied script-visible npm/package-manager globals, and the local
  Android store substrate.
- Type declarations and TypeScript smoke now include
  `PackageManagerProfile`.
- Host verifier and Gradle gate:
  - `tools/nodejs/phase13/verify-package-manager-android-decision.js`
  - `:app:verifyNodePhase13PackageManagerAndroidDecision`

## Existing Substrate

P13-09 already added `NodeAndroidPackageInstallManager`, which supports local
already-unpacked package directory operations inside an app-private store:

- install
- update
- remove
- list
- verify
- prune

The store is transactional, process-locked, cancellable, and SHA-512 checked.
It rejects lifecycle scripts, `bin`, native payloads (`.node`, `binding.gyp`,
`build/Release`, `build/Debug`), symlinks, and path escapes.

Phase 10 remains the host-managed package manager boundary. It provides local
registry metadata fixtures, safe `.tgz` extraction, `autojs6-lock.json`,
integrity checks, dependency compatibility analysis, and host-managed
install/update/remove/verify/prune flows. Android runtime installation remains
constrained to the local store substrate.

## Not Promoted

- Running `npm install` or `npm i --no-bin-links` directly on Android.
- Android registry metadata/tarball download and cache management.
- A script-visible `package_manager` or `npm` module.
- Lifecycle script execution, including `preinstall`, `install`,
  `postinstall`, and `prepare`.
- Debug-only lifecycle execution.
- Bin shim creation or arbitrary executable launch.
- Symlink materialization.
- Native addon build execution or downloaded native artifacts.
- Signed packaged native payload policy.
- Raw `node_modules` mutation APIs from scripts.
- Packaged APK dependency install/distribution evidence.

## Verification

```powershell
node tools\nodejs\phase13\verify-package-manager-android-decision.js --repo-root . --report-dir build\reports\nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase13PackageManagerAndroidDecision
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodePackageManagerPhase13DecisionInstrumentationTest"
```

## Rollback

Remove `packageManagerProfile`, the P13-42 instrumentation test, the verifier,
the Gradle gate, and this report. Keep the P13-09 Android local store and Phase
10 host-managed package manager boundary in place.
