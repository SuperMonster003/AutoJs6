# SDK 37 migration checks

See [the Roadmap](../../docs/dev/android-sdk-37-roadmap.md) for migration status and [the physical-device checklist](../../docs/dev/android-sdk-37-physical-device-tests.md) for device assignments, test methods, expected results and all 42 plugin rows.

## Physical devices

`physical_device.py` uses only the Python standard library. `inventory` queries online devices and records offline entries without querying them. `snapshot` saves read-only package, service, app-op, compat, audio, display and kernel-page information. Unsupported device commands retain their exit codes. Neither action asserts a functional pass. `checklist` exports every case in the physical-device document to a UTF-8 CSV with result and evidence columns, initially `NOT_RUN`. Use a new output directory each time:

```powershell
$Sdk37Adb = 'E:/.android/sdk/platform-tools/adb.exe'
$Sdk37Serial = 'QV770340J7' # Replace with the selected test device.
py tools/sdk37/physical_device.py inventory --adb $Sdk37Adb --output build/sdk37-physical/inventory
py tools/sdk37/physical_device.py checklist --output build/sdk37-physical/checklist
py tools/sdk37/physical_device.py snapshot --adb $Sdk37Adb --serial $Sdk37Serial --output build/sdk37-physical/before
```

After manual testing, take another snapshot with `--include-logs` in a different directory. The log is a bounded historical tail, not evidence that a particular case ran. Save observed behavior, APK hashes / targets, timestamps and relevant screenshots separately.

The `instrument` action runs only the named suite against an already installed, debuggable app and matching test APK. It verifies the actual `targetSdk` and each method's completion identity, rejecting missing, duplicate, skipped, failed or crashed tests. Instrumentation restarts the app and interrupts scripts. This helper does not install APKs, grant permissions, change settings, rebind accessibility, force-stop, clear data or reboot:

```powershell
py tools/sdk37/physical_device.py instrument --adb $Sdk37Adb --serial $Sdk37Serial --expected-target 37 --suite dex --output build/sdk37-physical/dex
```

Suites are `dex` (1 method), `native` (3; compatible OpenCV and Image Quantization plugins required), `install-state` (2; state storage and callback identity only), and `looper-platform` (2). Looper tests require explicit `--clear-interrupt` and `--pool-starts`; use the platform expectation table below. Pass `--package` for a matching inrt test build. The helper never substitutes host tests for a separately generated user APK. The default instrumentation timeout is 300 seconds and accepts 30-900 seconds; an adb timeout may leave device instrumentation running, so inspect it before launching another suite.

The complete 14-test `run_looper_tests.py`, `run_audio_probe.py`, and local-network permission-dialog suites remain emulator-only because they manipulate services, volume, reboot state or system dialogs. Physical audibility, OEM permission pages, touch / keyboard behavior, actual capture pixels and external hardware need the checklist's manual observations.

Host-only tests for result parsing and LAN peer filtering, with no adb calls:

```powershell
py -m unittest discover -s tools/sdk37 -p test_physical_device.py -v
```

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

## Looper and thread compatibility

Build the host and instrumentation APKs with `:app:assembleAppDebug :app:assembleAppDebugAndroidTest`. Install the matching ABI APK and `app-app-debug-androidTest.apk` on a disposable emulator. Enable AutoJs6 accessibility, allow overlay windows, unlock the screen and disable guard mode. The full legacy accessibility smoke also needs an unattended service restart strategy, such as the configured secure-settings strategy with `WRITE_SECURE_SETTINGS` granted.

The two device test classes under `app/src/androidTest/java/org/autojs/autojs/core/looper` run 14 probes, including 12 actual Rhino scripts in `app/src/androidTest/assets/sdk37/looper`. The helper requires an explicit emulator serial and expected platform behavior; it restarts the app through instrumentation and rebinds only the already-enabled AutoJs6 accessibility service while preserving other enabled services. It does not install APKs, grant permissions or change compat flags. Use a new output directory for each run:

```powershell
python tools/sdk37/run_looper_tests.py emulator-5560 --adb E:/.android/sdk/platform-tools/adb.exe --output build/sdk37-looper-baseline --clear-interrupt false --pool-starts 1
```

Use `compat.sh` above to enable one change at a time, restore its original state, then test all three together. On API 37, the expected arguments are:

| Installed target / overrides | `--clear-interrupt` | `--pool-starts` |
| --- | --- | --- |
| target 36, defaults | `false` | `1` |
| target 36, `USE_NEW_MESSAGEQUEUE` | `false` | `1` |
| target 36, `LOOPER_CLEARS_THREAD_INTERRUPTED` | `true` | `1` |
| target 36, `OVERRIDDEN_THREAD_START_METHOD` | `false` | `0` |
| target 36, all three | `true` | `0` |
| target 37, defaults | `true` | `0` |
| API 24, target 36 | `false` | `1` |

For the combined and actual target 37 runs, add `--soak-ms 120000`. Build the actual target 37 APK and its test APK with `-Pautojs.sdk.target.override=37`, install both, reset the three overrides to their recorded defaults and run the helper with `--clear-interrupt true --pool-starts 0`. The override remains debug-only; do not edit the normal targets for this probe.

The pool value counts calls to an overridden `Thread.start()` for one `ThreadPoolExecutor` worker and one `ForkJoinPool` worker. Both must still execute `run()`, and an ordinary direct `thread.start()` must still call its override. The interrupt probe separately checks the native flag, the sticky `ThreadCompat` cancellation flag and explicit clearing. A successful adb process exit alone is insufficient: the helper checks all 14 instrumentation results and rejects failures, skips or missing tests.

Run `sample/accessibility/smoke-legacy-api.js` separately after instrumentation exits; a full pass includes service stop/start, a new service instance and selectors after the rebind. See [the P2.4 evidence](../../docs/dev/evidence/android-sdk-37-p24-20260913.md) for exact coverage and APK hashes.

## Local network permission

`ACCESS_LOCAL_NETWORK` is included through the `src/target37/AndroidManifest.xml` flavor overlay only when that flavor actually targets API 37 or later. Normal host target 36 / inrt target 29 builds must not declare or request it. Use `-Pautojs.sdk.target.override=37` for debug experiments, and inspect the APK with `aapt2 dump badging` before granting a permission. Keep the host and its matching instrumentation APK together. `assembleInrtDebug` disables ABI splits for that invocation, including a host built in the same invocation; use the filenames in `output-metadata.json` instead of picking an older APK left in the directory.

On a disposable emulator, start `python tools/sdk37/local_network_fixture.py` on the host. By default it binds only `127.0.0.1`: HTTP/WebSocket on 17637, UDP echo on 17638, a minimal MQTT CONNECT responder on 17639 and a VS Code hello responder on 17640. It neither executes scripts nor provides a general MQTT broker. Configure emulator network redirection for the two inbound probes:

```powershell
$env:ADB = 'E:/.android/sdk/platform-tools/adb.exe'
$Sdk37Serial = 'emulator-5560'
& $env:ADB -s $Sdk37Serial emu redir add tcp:17645:17643
& $env:ADB -s $Sdk37Serial emu redir add tcp:17646:7347
python tools/sdk37/run_local_network_tests.py --serial $Sdk37Serial --expect denied --output build/sdk37-lan/denied
& $env:ADB -s $Sdk37Serial shell pm grant org.autojs.autojs6 android.permission.ACCESS_LOCAL_NETWORK
python tools/sdk37/run_local_network_tests.py --serial $Sdk37Serial --expect granted --output build/sdk37-lan/granted
& $env:ADB -s $Sdk37Serial shell pm revoke org.autojs.autojs6 android.permission.ACCESS_LOCAL_NETWORK
python tools/sdk37/run_local_network_tests.py --serial $Sdk37Serial --expect denied --output build/sdk37-lan/revoked
```

Use emulator `redir`, not `adb forward` or `adb reverse`, for the inbound evidence: an adb tunnel would test on-device loopback instead of the emulator's network interface. The fixture waits for the guest before closing each inbound probe, avoiding data loss from a prematurely closed redirection. Stop the fixture after testing and remove only these redirections with `emu redir del tcp:17645` / `tcp:17646`. Do not change host firewall rules to make a failing permission test pass.

The helper records device identity, package permission states, the exact command, instrumentation results and logcat. It requires an explicit serial, a debuggable installed app and an expected access state. It does not install APKs or modify permissions. The denied suite runs nine test methods and deliberately skips the two VS Code handshake success cases; the granted suite runs all eleven. Original Java Socket / Paho exceptions remain visible; HTTP, WebSocket and the Node controlled fetch path also verify recovery hints. The MQTT fixture covers only Paho MqttClient CONNECT, not Android MqttService, message delivery or reconnection. The tests separately exercise IPv4 and IPv6 loopback. A protocol error or assertion failure does not count as permission enforcement. Logcat is a cumulative tag-filtered buffer and may contain previous runs; use instrumentation and each package snapshot for per-run conclusions.

For permission UI tests, first revoke ACCESS_LOCAL_NETWORK and clear its `user-set` / `user-fixed` flags on the disposable emulator, preserving the dump from the previous scenario. `--suite retry --expect denied` clicks the first denial and then grants the retry. `--suite denial --expect denied` denies twice and checks that the app offers settings without opening them automatically. To test an existing Nearby Devices grant, prepare BLUETOOTH_CONNECT as granted and run `--suite nearby`, passing the actual starting local-network state. These suites operate system dialogs and are restricted to emulator serials. The retry suite also supports `--package org.autojs.autojs6.inrt` and opens the inrt settings Activity.

For upgrade coverage, install a normal target 36 APK, record its implicit permission state and successful traffic, then install the target 37 APK with `-r` and record the resulting state before any grant or revoke. Test legacy target APKs with `--expect granted`. Keep fresh installs, upgrades and existing Nearby Devices grants distinct. Actual Wi-Fi / Ethernet, VPN, managed-profile and separate plugin-UID tests remain separate device matrix rows; the emulator fixture does not claim those environments.

For a physical IPv4 LAN, choose the PC interface and phone peer explicitly. The fixture listens only on that PC address, accepts only the selected phone source address, and connects directly from that PC interface to the phone's ports 17643 / 7347. It does not configure routing, firewall rules or permissions. Replace both example addresses:

```powershell
# Terminal 1; stop with Ctrl+C.
py tools/sdk37/local_network_fixture.py --bind 192.168.1.10 --device 192.168.1.20
# Terminal 2, after checking permission state on the phone.
py tools/sdk37/run_local_network_tests.py --adb $Sdk37Adb --serial $Sdk37Serial --host 192.168.1.10 --expect granted --output build/sdk37-physical/lan-granted
```

Physical serials require an explicit IPv4 `--host`; emulator alias `10.0.2.2` is rejected. Do not use adb tunnels for these runs. On API 37 / target 37, prove a granted connection first, deny in the device UI and run `--expect denied`, then grant and verify recovery in a third directory. A TCP timeout alone cannot distinguish a permission denial from a firewall or routing problem. IPv6 LAN, multicast / mDNS, profiles, full MQTT message delivery and real VS Code execution remain separate checklist cases; IPv6 loopback does not cover an IPv6 LAN.

## Background Activity launches

Use a disposable API 37 emulator, or an explicitly selected API 37 test phone with `--physical-device`, with the host debug APK and the independent BAL fixture. The fixture is intentionally debuggable, has no network access, accepts provider calls only from the debuggable host / inrt, and creates tokens for its own fixed Activity or receiver. Its Activity tokens explicitly deny creator-side delegation, so sender tests cannot pass by borrowing creator privileges. Build it offline with the installed API 37 SDK and Build Tools:

```powershell
python tools/sdk37/build_bal_fixture.py --sdk E:/.android/sdk --jdk E:/.java/jdk-21.0.1 --output build/sdk37-bal-fixture
adb -s emulator-5566 install -t build/sdk37-bal-fixture/bal-fixture.apk
./gradlew :app:assembleAppDebug '-Pautojs.sdk.target.override=37'
# Install the APK named in output-metadata.json; keep its SHA-256 with the results.
python tools/sdk37/run_bal_probe.py --serial emulator-5566 --route result --sender-mode visible --expect result --output build/sdk37-bal/visible-result
python tools/sdk37/run_bal_probe.py --serial emulator-5566 --route sender --sender-mode none --expect blocked --output build/sdk37-bal/sender-no-opt-in
python tools/sdk37/run_bal_probe.py --serial emulator-5566 --route broadcast --background --sender-mode none --expect broadcast --output build/sdk37-bal/broadcast
```

Pass `--adb` if adb is not on PATH. The helper rejects non-emulator serials unless `--physical-device` is supplied. It does not install packages or modify permissions / compat overrides. It force-stops the selected probe package and the disposable fixture, interrupting their work, uses a fresh case ID and output directory, and records actual target creation and result delivery separately from the return of the send call. `--reuse-process` retains the host process after removing only tasks belonging to the host / fixture; the fixture is still force-stopped so an old Activity cannot be uncovered during a background case. `--timeout` sets the completion deadline (120 seconds by default). Default and explicit enable / disable runs must use separate output directories. Snapshot the existing `COVER_INTENT_SENDER` and `ASM_RESTRICTIONS` overrides before changing them; restore each original override, or reset it if none existed.

`--background` moves the probe task to the background and waits 12 seconds before sending. Before interpreting a blocked / allowed result, inspect the saved app-ops, process state and system BAL log for overlay / accessibility / recent-launch exceptions. Granting options does not itself grant an app background-launch eligibility. A blocked assertion requires a platform BAL block / StrictMode violation, not just a missing target.

`--route asm` places a different UID's Activity above the probe, waits for the lower Activity's `onStop()` plus 12 seconds, then asks it to start a same-UID landing Activity. `--allow-below true|false` calls the fixture Activity's `setAllowCrossUidActivitySwitchFromBelow`; omitting it leaves the Activity default. The application-level manifest setting is a separate condition. Build a protected fixture with:

```powershell
python tools/sdk37/build_bal_fixture.py --sdk E:/.android/sdk --jdk E:/.java/jdk-21.0.1 --allow-cross-uid-from-below false --output build/sdk37-bal/fixture-protected
```

The builder saves the generated manifest and `build.json` alongside the APK. `false` opts the top app into ASM protection; `true` permits switches; `default` omits the manifest attribute. Each build creates its own disposable signing key, so uninstall only the dedicated fixture package on the selected test device before installing a differently signed variant. Do not replace the fixture during an installation-recovery test.

On the tested API 37 image, a target 37 caller is blocked only when the different-UID top app is also subject to `ASM_RESTRICTIONS`, its application manifest opts into protection, and the Activity has not granted the `true` exception. Calling the Activity setter with `false` alone did not enable application protection. Test the caller and top package compat overrides independently, plus the manifest-default and Activity-exception controls. Record `default / enable / disable` separately; `overridable` in a compat dump does not mean disabled. See the [ASM evidence](../../docs/dev/evidence/android-sdk-37-p25-asm-20260913.md) for this image's results and documentation differences.

ASM assertions require different actual UIDs, matching task IDs, a resumed fixture snapshot before the lower launch, and the lifecycle delay. `--expect no-landing` additionally requires a fresh system `[ASM] Abort Launching` diagnostic for the probe task and landing Activity. `--expect landing` requires an actual landing event in that task. A returned `startActivity` call or an absent Activity alone is not a passing assertion.

`sender` tests the API 37 options overload of `IntentSender.sendIntent`; `sender-legacy --sender-mode none` separately tests the old overload with no options parameter. Do not treat them as interchangeable in a `COVER_INTENT_SENDER` comparison. `result` uses the production options adapter through the AndroidX ActivityResult contract. On the tested image, the visible `result` case also succeeds without options; that observation does not remove the need to test direct sends and background sends separately.

`script-launch` and `script-start` execute real Rhino `app.launch` / `app.startActivity` calls. The fixture's launch event must be newer than the probe start; a successful script return does not prove the target opened. `install` reads only `files/sdk37-bal-install.apk` from the probe package and uses the real package archive inspector and installer; `--expect install` asserts session submission only. System confirmation, rejection, final installation and background completion still need separate UI / package-state evidence. The standalone installer fixture and the token fixture can be the same APK, but replacing it changes its process lifetime and must not be interleaved with a token test.

The probe Activity requires `android.permission.DUMP`, and its landing Activity is private. Both exist only in debug source sets. Host debug builds enable `StrictMode.detectBlockedBackgroundActivityLaunch()` on API 36+ without replacing existing VM checks. The installed API 37 SDK exposes the IntentSender options overload as `(Context, int, Intent, String, Bundle, Executor, OnFinished)`; use the compiled SDK signature instead of copying an older online snippet.

## Installation confirmation recovery

Use `run_install_recovery_probe.py` for installation recovery cases. It preserves the application process and pending sessions, does not require the BAL token fixture, and never force-stops either package. It requires an explicit disposable emulator serial, or `--physical-device` with a selected test phone, and a debuggable host or inrt APK. The `install` route submits through the production archive inspector and session installer; `install-ui` opens the production `PackageInstallerActivity`; `install-recovery` records persisted sessions and whether their system confirmation tokens still exist.

Copy a signed, compatible test APK into `files/sdk37-bal-install.apk` in the selected app. For multiple sessions, use distinct filenames and `--apk-name`. For example:

```powershell
$Sdk37Adb = 'E:/.android/sdk/platform-tools/adb.exe'
$Sdk37Serial = 'emulator-5568'
& $Sdk37Adb -s $Sdk37Serial push build/sdk37-fixtures/example.apk /data/local/tmp/sdk37-install-example.apk
& $Sdk37Adb -s $Sdk37Serial shell run-as org.autojs.autojs6 cp /data/local/tmp/sdk37-install-example.apk files/sdk37-bal-install.apk
python tools/sdk37/run_install_recovery_probe.py --adb $Sdk37Adb --serial $Sdk37Serial --route install --background --output build/sdk37-install/background
python tools/sdk37/run_install_recovery_probe.py --adb $Sdk37Adb --serial $Sdk37Serial --route install-recovery --output build/sdk37-install/return
```

`--background` explicitly sends Home before the delayed submission, since moving only the newest task behind other app tasks does not prove the UID is in the background. The helper checks the actual process importance and focus at submission. Use `--package org.autojs.autojs6.inrt` with the matching APK and files directory. Permission setup, UI confirmation/cancellation and final package verification remain explicit test steps. The helper reports `installationSuccess: "not asserted"`; a session ID is only submission evidence.

Check the ledger in `no_backup/package-install-status.json`, callback logs, notification session IDs, system installer UI and final `dumpsys package <installed-package>`. Exercise notifications denied, returning to a visible app, two different sessions, cancellation, signature conflict and rotation while showing a result. Kill only the selected app PID with `run-as <package> kill -9 <pid>` to test ordinary process reconstruction; `am force-stop` additionally invalidates PendingIntents and tests the separate expired-confirmation fallback. Do not run instrumentation or the older BAL helper in the middle of a pending-session recovery case, because those can terminate the app and change the test condition.

The receiver and recovery Activity are private in release manifests. Callback identity, durable multi-session state and terminal-status replay protection are separately tested by `PackageInstallStatusStoreDeviceTest` and `PackageInstallStatusRecordTest`. The [recovery evidence](../../docs/dev/evidence/android-sdk-37-p25-recovery-20260913.md) records exact APKs, outcomes and the distinction between process death and force-stop.

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
