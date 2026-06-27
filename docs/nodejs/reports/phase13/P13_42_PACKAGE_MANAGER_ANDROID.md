# P13-42 Android Package Manager

P13-42 records Android package manager parity as partial.

The current target is "Auto.js Pro capability + desktop Node.js capability",
but package installation is still split between a host-managed npm-compatible
validator and an Android app-private local package store substrate. This phase
does not promote the npm CLI, registry download, remote tarball download,
lifecycle script execution, bin shim creation, native build execution, or
unrestricted JavaScript package-manager mutation on Android. A script-visible
`npm` facade is present, but it is only a guarded alias over the local
`package_manager` facade.

## Landed

- `autojs6:profile.packageManagerProfile` reports the Android package-manager
  boundary:
  - `status`: `android_substrate_partial`
  - host package manager: `available`
  - Android store: `local_app_private_partial`
  - Android runtime install: `local_unpacked_only`
  - npm CLI: `denied`
  - npm facade: `guarded_package_manager_alias`
  - `require("npm")`: `guarded_package_manager_alias`
  - equivalent install path: `host_managed_plus_android_store_substrate`
  - registry download: `deferred`
  - tarball install: `host_side_only`
  - lockfile: `autojs6_lock_host_managed`
  - integrity: `sha512_required`
  - lifecycle scripts, bin links, symlinks, native build/native payloads, and
    postinstall downloads: `denied`
  - JS bridge: `guarded_mutation_partial`
  - `require("package_manager")`: `guarded_mutation_partial`
  - mutation capability: `package_manager.mutate`
  - mutation confirmation: `allowMutation_required`
  - raw executable and raw registry authority: `false`
- `require("package_manager")` exposes a frozen guarded mutation facade:
  - `status()` and `policy()` return frozen policy snapshots.
  - `sourcePolicy()` returns a frozen source-authority snapshot. The only
    allowed substrate is `local_unpacked_host_managed`; raw registry, remote
    tarball, URL/git, workspace symlink, and unverified-cache sources remain
    denied.
  - `planInstall(...)`, `planUpdate(...)`, and `planRemove(...)` dispatch to
    the Android store as dry-run planning operations. They validate the same
    local unpacked source/remove targets, return frozen
    `autojs6-node-android-package-plan-v1` results, and never grant install
    authority.
  - `planRegistryInstall(...)` and `planTarballInstall(...)` are plan-only
    source-boundary APIs. They return frozen
    `autojs6-node-package-manager-remote-plan-v1` results with
    `blocked_by_policy`, `networkAccess: false`, `cacheAccess: false`, and no
    install authority. They do not dispatch to the Android bridge, download
    registry metadata, fetch tarballs, extract archives, or mutate the local
    store.
  - `planRegistryInstall(...)` can parse caller-provided npm
    `metadata`/`packument` objects for dist-tags, exact versions, common
    semver ranges (`^`, `~`, comparators, and `*`), tarball URL, integrity,
    expected-integrity verdict, and shasum, exposing a frozen
    `autojs6-node-package-manager-registry-resolution-v1` diagnostic under
    `resolution`. This is metadata-only planning; Android registry download,
    cache reuse, extraction, install, and authority promotion remain blocked.
  - `planTarballInstall(...)` exposes a frozen
    `autojs6-node-package-manager-tarball-resolution-v1` diagnostic under
    `resolution`, including the requested tarball URL, URL protocol/host/path
    shape, `.tgz` archive verdict, inferred npm package name/version,
    request-vs-inferred name/version match flags, expected-integrity
    algorithm, and SHA-512 allowlist verdict.
  - `install(...)` and `update(...)` require `package_manager.mutate`,
    `allowMutation: true`, a relative project-scoped local unpacked source, and
    `expectedIntegrity` from a dry-run plan before mutating the app-private
    store.
  - `remove(...)` requires `package_manager.mutate`, `allowMutation: true`, and
    expected integrity from `planRemove()` before deleting an installed record.
  - `list()` dispatches to the Android app-private store and returns frozen
    installed package records.
  - `verify({ name, version })` verifies installed package integrity or returns
    `missing` for absent packages.
  - `prune({ maxTransactionAgeMs })` removes stale transaction directories.
- `require("npm")` exposes a frozen guarded facade:
  - `status()`, `policy()`, and `sourcePolicy()` report the same local-store
    boundaries as `package_manager`.
  - `planInstall`/`planUpdate`/`planRemove`/`planRegistryInstall`/
    `planTarballInstall`/`install`/`update`/`remove`/`list`/`verify`/`prune`
    delegate to the guarded `package_manager` facade.
  - `cli()`, `exec()`, `runScript()`, registry install, publish, and pack
    entry points reject with stable npm policy errors.
- `NodeAndroidPackageInstallManager.nativeValues()` now exposes the same
  Android-side policy details for native test reports.
- `NodePackageManagerPhase13DecisionInstrumentationTest` verifies the runtime
  profile, guarded npm facade, denied npm CLI/registry calls, plan-only
  registry/tarball boundaries, guarded package-manager facade, and the
  local Android store substrate.
- Type declarations and TypeScript smoke now include
  `PackageManagerProfile`, source policy fields, and the guarded
  `package_manager` / `npm` modules, including packument input and remote
  plan semver/tarball/integrity resolution diagnostics.
- Host verifier and Gradle gate:
  - `tools/nodejs/phase13/verify-package-manager-android-decision.js`
  - `:app:verifyNodePhase13PackageManagerAndroidDecision`

## Policy Decision

- npm CLI, `npm.exec()`, `npm.runScript()`, registry install, publish, and pack
  entry points stay denied on Android.
- Lifecycle scripts, debug lifecycle execution, bin links/shims, symlink
  materialization, native build/native payload execution, postinstall downloads,
  and arbitrary executable launch stay denied.
- Raw `node_modules` mutation is not a script authority. Android mutation stays
  limited to the guarded local-unpacked app-private store path, requires
  `package_manager.mutate`, `allowMutation: true`, and expected integrity from
  a dry-run plan, and never grants registry/tarball install authority.
- Android registry downloader, cache management, tarball extraction, and
  integrity-lock install authority stay stable-denied as runtime user APIs for
  P13-42 v1.1. Future promotion requires a reviewed mobile install authority;
  Phase 10 host-managed validation remains the equivalent install path.

## Existing Substrate

P13-09 already added `NodeAndroidPackageInstallManager`, which supports local
already-unpacked package directory operations inside an app-private store:

- install
- update
- remove
- planInstall (dry-run)
- planUpdate (dry-run)
- planRemove (dry-run)
- planRegistryInstall (blocked policy dry-run)
- planTarballInstall (blocked policy dry-run)
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
- Android registry metadata/tarball download and cache management; these stay
  stable-denied runtime user APIs for P13-42 v1.1.
- `planRegistryInstall()` / `planTarballInstall()` beyond policy-only dry-run
  diagnostics; registry semver/integrity resolution uses caller-provided
  metadata only, tarball URL parsing is local-only, and the APIs do not
  download, cache, extract, install, or grant authority.
- npm CLI/registry/lifecycle behavior behind the script-visible `npm` facade.
- Unrestricted or registry-backed `package_manager.install/update/remove`
  operations.
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
