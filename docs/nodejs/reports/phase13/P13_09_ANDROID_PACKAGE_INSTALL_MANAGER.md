# P13-09 Android Package Install Manager

Status: partial

P13-09 starts the Android-side package installation manager without promoting a
user-facing npm or registry installer. The landed pieces are a local,
app-private package store that can install already-unpacked package directories
after policy validation, plus a guarded `require("package_manager")` facade
that reports policy, can dry-run install/update/remove planning, can perform
explicit local install/update/remove mutations, and can list, verify, and prune
the app-private store. It is intended as the Android runtime substrate for
future dependency, plugin, and package distribution work.

## Landed

- Runtime class:
  `app/src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt`
- Focused instrumentation:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt`
- Guarded package-manager facade instrumentation:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodePackageManagerPhase13DecisionInstrumentationTest.kt`
- Type declaration:
  `docs/nodejs/types/autojs6-node/package_manager.d.ts`
- Host verifier:
  `tools/nodejs/phase13/verify-android-package-install-manager.js`
- Gradle gate:
  `:app:verifyNodePhase13AndroidPackageInstallManager`
- Output:
  `build/reports/nodejs/phase13-android-package-install-manager.json`
- Output:
  `build/reports/nodejs/phase13-android-package-install-manager.md`

## Current Capability

`NodeAndroidPackageInstallManager` supports:

- `install`
- `update`
- `remove`
- `planInstall` (dry-run only)
- `planUpdate` (dry-run only)
- `planRemove` (dry-run only)
- `list`
- `verify`
- `prune`

The store is deliberately local-only and app-private. It uses a store lock,
transaction staging directories, atomic move/restore behavior, cancellable
operation checks, and SHA-512 integrity. It rejects:

- `preinstall`, `install`, `postinstall`, and `prepare`
- package `bin` entries
- `.node`, `binding.gyp`, and `build/Release` or `build/Debug` native payloads
- symlinks and path escapes

Records include dependency/plugin/project package kinds so future plugin
distribution and package-manager work can build on the same store.

`require("package_manager")` is now script-visible in guarded mutation mode:

- `status()` returns a frozen runtime policy snapshot.
- `policy()` returns a frozen fail-closed package-manager policy.
- `sourcePolicy()` returns a frozen source-authority snapshot. The only
  allowed substrate is a host-managed local unpacked directory; raw registry,
  remote tarball, URL/git, workspace symlink, and unverified-cache sources
  remain denied.
- `planInstall({ name, version, sourceDir, expectedIntegrity?, kind? })` and
  `planUpdate(...)` validate a relative, project-scoped local unpacked source
  and return a frozen `autojs6-node-android-package-plan-v1` dry-run result.
- `planRemove({ name, version })` reports whether an installed package would be
  removed. Plan results set `dryRun: true` and `grantsInstallAuthority: false`.
- `install({ name, version, sourceDir, expectedIntegrity, allowMutation: true })`
  and `update(...)` mutate the app-private store only for relative,
  project-scoped local unpacked sources. They require the `package_manager`
  and `package_manager.mutate` capabilities plus SHA-512 integrity from a
  dry-run plan.
- `remove({ name, version, expectedIntegrity, allowMutation: true })` removes
  an installed record only after matching the planned/recorded integrity.
- `list()` returns frozen Android package records from the app-private store.
- `verify({ name, version })` verifies an installed record or returns
  `missing` for absent packages.
- `prune({ maxTransactionAgeMs })` removes stale transaction directories.
- Mutating operations reject without `allowMutation: true` using
  `ERR_AUTOJS6_PACKAGE_MANAGER_MUTATION_NOT_CONFIRMED`.

## Not Promoted Yet

- No Android registry downloader is enabled.
- No Android remote tarball installer or unverified cache reuse is enabled.
- No npm CLI install is enabled on Android.
- No unrestricted script-visible package-manager operation is promoted; guarded
  mutation remains local, project-scoped, integrity-checked, and capability
  gated.
- No lifecycle scripts are executed.
- No signed native payload verification exists yet; native payload execution
  remains disabled.
- The final blocker `android_side_package_installation` remains open.

## Verification Notes

The focused instrumentation compiles and is selected by:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeAndroidPackageInstallManagerInstrumentationTest"
```

On 2026-06-25 the focused connected suite passed on `XQ-AT72 - 12` with the
Android package manager, bridge conformance, provider registry, and
guarded mutation/read-maintenance facade tests selected.
