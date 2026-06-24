# P13-09 Android Package Install Manager

Status: partial

P13-09 starts the Android-side package installation manager without promoting a
user-facing npm or registry installer. The first landed piece is a local,
app-private package store that can install already-unpacked package directories
after policy validation. It is intended as the Android runtime substrate for
future dependency, plugin, and package distribution work.

## Landed

- Runtime class:
  `app/src/main/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManager.kt`
- Focused instrumentation:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeAndroidPackageInstallManagerInstrumentationTest.kt`
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

## Not Promoted Yet

- No JavaScript bridge or `require("package_manager")` surface is enabled.
- No Android registry downloader is enabled.
- No npm CLI install is enabled on Android.
- No lifecycle scripts are executed.
- No signed native payload verification exists yet; native payload execution
  remains disabled.
- The final blocker `android_side_package_installation` remains open.

## Verification Notes

The focused instrumentation compiles and is selected by:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeAndroidPackageInstallManagerInstrumentationTest"
```

On 2026-06-24 the connected Sony device had an existing `org.autojs.autojs6`
install with a different signature, so Gradle could not install the debug APK
without uninstalling the user's app. The code and test sources compile; device
execution remains to be rerun on a prepared test device.
