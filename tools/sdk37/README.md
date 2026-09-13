# SDK 37 migration checks

See [the Roadmap](../../docs/dev/android-sdk-37-roadmap.md) for the acceptance matrix.

## Build configuration

The normal targets come from `version.properties`. To build both app and inrt debug APKs with a temporary target:

```powershell
./gradlew '-Pautojs.sdk.target.override=37' :app:assembleAppDebug :app:assembleInrtDebug
```

The override accepts an integer between the project's minSdk and the greater of compileSdk / 37 (allowing the initial compile 36 / target 37 probe). It does not write the version file. Release tasks and their dependencies, Runtime Kit, and APK template distribution depend on a task which fails when the override is present. This also covers abbreviated and aggregate Gradle requests, and does not rely on a task-graph callback that configuration caching could skip.

For the normal distribution matrix, use `:app:assembleAppDebug :app:assembleAppRelease :app:generateRuntimeKit :assembleApkBuilderTemplateDistribution`. The template distribution task belongs to the root project. Runtime Kit builds inrt release through its dependencies. Run `:app:assembleInrtDebug` separately: an explicit `assembleInrt*` request makes the existing app script disable ABI splits for that invocation, producing only universal APKs, while Runtime Kit needs all five ABI / universal outputs.

To run the normally disabled release vital lint checks as a separate verification pass:

```powershell
./gradlew --continue --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx12g -XX:+UseG1GC -Dfile.encoding=UTF-8' '-Pautojs.sdk.lint.verify=true' :app:lintAppDebug :app:lintVitalAppRelease :app:lintVitalInrtRelease
```

This mode enables vital analysis, bypasses the template-distribution lint skip, and fails on lint errors. Preserve the fresh debug XML / HTML reports in `app/build/reports` and the release vital text reports under `app/build/intermediates/lint_vital_intermediate_text_report`, together with the command log. AGP 9.4 emits text reports for vital checks; they are not normal debug XML reports. A failed baseline can still provide useful reports; record existing errors and compare issue identities and counts after the SDK change. A skipped task or an old report is not evidence that lint passed. Do not combine this release check with the debug target override.

Full app / inrt analysis can exceed the project's default 4 GB Gradle heap. The example uses a per-invocation heap override and limits concurrency; use a machine with sufficient available memory. These options do not edit the project's normal Gradle settings.

Copy the fresh reports for all three requested variants into each baseline directory as `lint-results-appDebug.xml`, `lint-results-vital-appRelease.txt` and `lint-results-vital-inrtRelease.txt` (rename the vital files when copying). Then run:

```powershell
python tools/sdk37/compare_lint.py build/sdk37-lint-36 build/sdk37-lint-37 --output build/sdk37-p0-p1/lint-diff.json
```

The comparison matches issue ID, severity, message and location files, ignoring line-number shifts and retaining duplicate counts. Vital text diagnostics count as Fatal because those tasks run only fatal checks. The parser verifies the text summary count to detect unparsed diagnostics. It fails on new Error / Fatal occurrences or missing / mismatched report sets. Existing errors are still reported; a successful diff means no new errors, not a clean lint report. Verify that both directories were generated with the same lint configuration and dependency versions.

## Device compatibility changes

Use Git Bash on Windows. Set `ADB` to the SDK executable when it is not on PATH. The scripts pass an explicit serial on every adb invocation and disable MSYS argument conversion.

```bash
export ADB='E:/.android/sdk/platform-tools/adb.exe' # Replace with the actual SDK path.
bash tools/sdk37/dump_compat.sh emulator-5560
bash tools/sdk37/compat.sh emulator-5560 org.autojs.autojs6 enable USE_NEW_MESSAGEQUEUE LOOPER_CLEARS_THREAD_INTERRUPTED
# Run and record the test cases, then restore each original override.
bash tools/sdk37/compat.sh emulator-5560 org.autojs.autojs6 reset USE_NEW_MESSAGEQUEUE LOOPER_CLEARS_THREAD_INTERRUPTED
```

Use the actual applicationId for inrt and plugins. `compat.sh` requires an installed debuggable package and validates all requested changes against the device's dump before changing any. Each call records the original platform configuration, device / SDK / page size, audio and memory status, package flags, command results and final configuration under `build/sdk37-compat`. `SDK37_EVIDENCE_DIR` can choose a separate directory for a named run; use a fresh directory for each invocation so that the original snapshot is retained.

`reset` removes an override and restores the platform default. If an override already existed, restore its original enable / disable state instead. A failure can leave earlier changes in a batch applied; the action log and before / after dumps identify them. The helper does not reset all changes or clear application data.

## Device-wide diagnostic controls

Audio hardening and memory limiting are device / process controls, not package compat changes. Use a dedicated recoverable AVD, snapshot the original state with `dump_compat.sh`, and run default-state acceptance separately from forced diagnostics.

```powershell
$Sdk37Serial = 'emulator-5560'
& 'E:/.android/sdk/platform-tools/adb.exe' -s $Sdk37Serial shell cmd audio set-enable-hardening throw
& 'E:/.android/sdk/platform-tools/adb.exe' -s $Sdk37Serial shell am memory-limiter status
# For a specific test process: am memory-limiter manual <pid> <MB>
# Restore that manual limit: am memory-limiter manual <pid> none
```

Audio `enable` / `throw` override target-dependent WIU and alarm exceptions. `disable` turns restrictions off and is not a generic restoration of the platform default. Consult [the audio testing guide](https://developer.android.com/about/versions/17/changes/bg-audio) and restore the recorded device state after diagnosis. The package helper deliberately does not apply these global controls.

## Basic device smoke

Run `sample/accessibility/smoke-legacy-api.js` with accessibility enabled, the screen unlocked and guard mode off. Then run [smoke-ui.js](smoke-ui.js) from AutoJs6 with overlay access enabled. It opens a script Activity, requests keyboard focus, displays an overlay and requests one MediaProjection capture. Accept the system dialog for the test activity; it checks the returned image dimensions without saving a screenshot. Console messages start with `[SDK37]`, and an unattended capture dialog times out after 60 seconds.

Also open Symbols Settings, edit a symbol and verify that the keyboard appears and dismisses on Back on the old-API devices. The generic script UI probe does not exercise that activity's keyboard helper. Record the installed APK hash, compile / target SDK, device fingerprint, build type and test results together.

## Dynamic loading

Build the two small fixtures with the installed JDK and Android Build Tools. The Java sources contain no native code or Android API calls. Use JDK 8 to produce a Java 6 class file for the JAR fixture: the built-in legacy dx fallback on API 24 / 25 rejects Java 8 class files, and local D8 uses `java.nio.file.Paths`, which is unavailable there. This fixture tests loading within that existing compiler boundary. Replace the example paths for your machine:

```powershell
$Sdk37Jdk = 'E:/.java/jdk-21.0.1'
$Sdk37LegacyJdk = 'E:/.java/jdk-1.8.0_312'
$Sdk37Sdk = 'E:/.android/sdk'
New-Item -ItemType Directory -Force -Path build/sdk37-fixtures/classes,build/sdk37-fixtures/legacy-classes,build/sdk37-fixtures/dex | Out-Null
& "$Sdk37Jdk/bin/javac.exe" --release 8 -d build/sdk37-fixtures/classes tools/sdk37/fixtures/DexProbe.java
& "$Sdk37LegacyJdk/bin/javac.exe" -source 6 -target 6 -d build/sdk37-fixtures/legacy-classes tools/sdk37/fixtures/JarProbe.java
$env:JAVA_HOME = $Sdk37Jdk
& "$Sdk37Sdk/build-tools/37.0.0/d8.bat" --min-api 24 --output build/sdk37-fixtures/dex build/sdk37-fixtures/classes/org/autojs/sdk37/fixture/DexProbe.class
Copy-Item build/sdk37-fixtures/dex/classes.dex build/sdk37-fixtures/sdk37-probe.dex
& "$Sdk37Jdk/bin/jar.exe" --create --file build/sdk37-fixtures/sdk37-probe.jar -C build/sdk37-fixtures/legacy-classes org/autojs/sdk37/fixture/JarProbe.class
```

Put `sdk37-probe.dex`, `sdk37-probe.jar` and [smoke-dynamic-loading.js](smoke-dynamic-loading.js) in the same directory accessible to AutoJs6. The script calls the actual `runtime.loadDex` / `runtime.loadJar` APIs twice in one process and checks a value returned by each loaded class, covering initial loading and read-only cache reuse. Success messages begin with `[SDK37-DCL]`. On API 37, test both target 36 with `THROW_ERROR_FOR_WRITABLE_DCL` enabled and a target 37 debug APK with the override reset to its default. Repeat on an old API device.

`AndroidClassLoaderRawDexDeviceTest` separately verifies that loading and reusing a raw DEX preserves both the original bytes and the read-only staged copy. Its embedded DEX comes from `fixtures/DexProbe.java` with the commands above. On API 24 / 25 the optimized output directory must differ from the input directory; ART can otherwise overwrite a raw `.dex` with its optimized output. The optimized-directory argument is unused starting with API 26.

For native libraries, install compatible OpenCV and Image Quantization plugins and run `OpenCvPluginHostDeviceTest` plus `ImageQuantPluginHostDeviceTest#installedPluginLoadsAndProducesIndexedPng`. These tests exercise JNI and verify the staged file has no write mode bits. To test legacy cache repair, use a temporary AVD: record hash / size / mtime / mode of the two staged libraries, make only those files owner-writable, rerun in a fresh process, then confirm the same bytes and timestamps with read-only mode restored. Do not change cached library permissions on a user's active device.
