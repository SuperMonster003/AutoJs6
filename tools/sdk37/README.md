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

### WebView User-Agent reduction and rendering

`run_webview_probe.py` drives actual Rhino `ui.webview` layouts, the host's
`DocumentationActivity`, or the independently installed HTML Previewer. It uses
only the Python standard library, including a small local DevTools client. See
[the P2.11 evidence](../../docs/dev/evidence/android-sdk-37-p211-20260913.md).

Use a disposable emulator and a fresh output directory. The helper stops the host
and HTML Previewer and starts a new host task, interrupting their current work. It
creates uniquely named test receipts / HTML files, starts a PC loopback HTTP
fixture for the UI route, and removes its temporary adb DevTools forward when
finished. It does not install APKs, alter compatibility flags, grant permissions,
enable plugins, change documentation preferences or dismiss system dialogs.

Install a debuggable host first. For API 37 / host target 37, grant the test host
`android.permission.ACCESS_LOCAL_NETWORK` before the HTTP UI fixture. The two
additional routes require the matching debuggable official plugins: Offline Docs
must be available and selected as the documentation source; HTML Previewer uses
its audited v2 Explorer Action entry with a read grant limited to the fresh test
directory. The HTML fixture is static; DevTools observations do not enable the
previewer's normally disabled document JavaScript.

```powershell
py tools/sdk37/run_webview_probe.py --serial emulator-5584 --expected-target 37 --expected-webview-target 37 --expected-reduced true --output build/sdk37-webview/default
py tools/sdk37/run_webview_probe.py --serial emulator-5584 --expected-target 37 --expected-webview-target 37 --expected-reduced true --ua-mode custom --output build/sdk37-webview/custom
py tools/sdk37/run_webview_probe.py --serial emulator-5584 --expected-target 37 --expected-webview-target 37 --expected-reduced true --route docs --docs-page app.html --output build/sdk37-webview/docs-app
# HTML Previewer has its own target and package-specific compatibility state.
py tools/sdk37/run_webview_probe.py --serial emulator-5584 --expected-target 37 --expected-webview-target 36 --expected-reduced false --route html --output build/sdk37-webview/html-default
```

`--ua-mode` also accepts `suffix` and `reset`. The UI route compares the actual
HTTP User-Agent header, `WebSettings.userAgentString` and `navigator.userAgent`.
Every route checks fresh / expected content, document dimensions, the focused
window and a screenshot; UI / HTML additionally check CSS and local navigation.
Secure document origins also record available User-Agent Client Hints. An
insecure HTTP origin returning no Client Hints does not establish whether the
provider supports them. File System Access feature detection is recorded as an
observation only; it does not verify chooser behavior or file access grants.

Before toggling `ENABLE_USER_AGENT_REDUCTION`, save its existing overrides.
For this API 37.0 image, host target 36 defaults to unreduced UA and target 37 to
reduced UA. Run each override separately and restore the initial state. For HTML,
apply the override to `io.github.supermonster003.autojs6.plugin.htmlpreviewer`;
the host's override does not cover that process. Actual targets, APK hashes,
WebView provider version, device identity and raw UA compat state accompany each
result. Missing receipts, skipped navigation, wrong headers or an obscuring
system dialog cannot count as a successful run. Review the saved screenshots
before accepting visual results.

```powershell
py -m unittest discover -s tools/sdk37 -p test_webview_probe.py -v
py -m py_compile tools/sdk37/run_webview_probe.py
```

### TLS trust, CT, ECH and request-scoped insecure options

The [P2.6 evidence](../../docs/dev/evidence/android-sdk-37-p26-20260913.md)
records actual HTTPS requests before and after the fix. Use a disposable emulator:
instrumentation restarts the host and runs Rhino scripts. The helper refuses phone
serials and does not install APKs, grant permissions or change compatibility flags.
For physical network / enterprise proxy acceptance, keep case O02 in the
[physical-device checklist](../../docs/dev/android-sdk-37-physical-device-tests.md) open.

In a separate terminal, start the Python standard-library fixture:

```powershell
py tools/sdk37/tls_fixture.py
```

It binds only to the PC's `127.0.0.1:17647`; the emulator connects using
`10.0.2.2`. Its committed server key is a disposable identity for reserved `.test`
names. The matching CA is trusted only by three debug domains. Never install it
in a device's certificate store. `trusted.sdk37.test` proves chain trust,
`mismatch.sdk37.test` has the same trusted CA but is absent from the certificate
SANs, `ct.sdk37.test` requires CT explicitly, and `untrusted.sdk37.test` retains
normal system-only trust. The leaf has no SCTs. Release resources contain no test CA.

Build and install matching appDebug / appDebugAndroidTest APKs, preserving a separate
copy for each target. On API 37 with target 37, explicitly grant the test host
`android.permission.ACCESS_LOCAL_NETWORK` before using the host alias. Then run:

```powershell
py tools/sdk37/run_tls_tests.py --adb E:/.android/sdk/platform-tools/adb.exe --serial emulator-5572 --expected-target 37 --default-ct true --ech-mode 2 --output build/sdk37-tls/target37-default
```

Choose the explicit emulator serial and a new output directory. The six cases
cover certificate-chain rejection, domain-limited CA trust, explicit CT rejection,
policy queries, and both `isInsecure` / `insecure` through synchronous, traditional
callback and Promise requests. Normal requests after every insecure request must
reject the unknown CA; the hostname check and trusted-domain access must also
remain intact. Promise response bodies are consumed on the script thread.

The API 37.0 image tested here uses the following defaults and isolated overrides:

| Actual target | Override | `--default-ct` | `--ech-mode` |
| --- | --- | --- | --- |
| 37 | None | true | 2 |
| 37 | Disable `DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY` | false | 2 |
| 37 | Disable `ENABLE_DEFAULT_ENCRYPTED_CLIENT_HELLO` | true | 1 |
| 36 | None | false | 1 |
| 36 | Enable `DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY` | true | 1 |
| 36 | Enable `ENABLE_DEFAULT_ENCRYPTED_CLIENT_HELLO` | false | 2 |

For example, on an exclusively owned emulator with no initial overrides:

```powershell
$Sdk37Adb = 'E:/.android/sdk/platform-tools/adb.exe'
$Sdk37Serial = 'emulator-5572'
& $Sdk37Adb -s $Sdk37Serial shell am compat disable DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY org.autojs.autojs6
try {
    py tools/sdk37/run_tls_tests.py --adb $Sdk37Adb --serial $Sdk37Serial --expected-target 37 --default-ct false --ech-mode 2 --output build/sdk37-tls/target37-ct-disabled
    if ($LASTEXITCODE -ne 0) { throw 'TLS suite failed; inspect result.json and instrumentation.txt' }
} finally {
    & $Sdk37Adb -s $Sdk37Serial shell am compat reset DEFAULT_ENABLE_CERTIFICATE_TRANSPARENCY org.autojs.autojs6
}
```

Use one override at a time and restore the recorded initial state. `reset` restores
the target-dependent default, so it is not a substitute for restoring a preexisting
explicit override. `before` / `after` snapshots include the package and compatibility
state; results also record exact test names, exit status and timeout. `--suite
policies`, `transport` or `rhino` selects a smaller group. Before API 36, CT is
unsupported; before API 37, ECH policy queries are unsupported. On API 24 the same
suite verifies TLS / Rhino behavior and that the newer CT element is ignored.

ECH mode values must stay explicit: UNKNOWN=0, DISABLED=1, OPPORTUNISTIC=2 and
ENABLED=3 in API 37.0. Online reference pages already contain 37.2 additions and
deprecations; do not copy their later default assumptions into a 37.0 assertion.
The fixture records ClientHello extension types, SNI and TLS version. It cannot
negotiate ECH. Even the presence of extension `0xfe0d` could be GREASE; successful
ECH requires evidence from a compatible library and server. The recorded OkHttp
4.12.0 fixture connections carried cleartext SNI and no ECH extension.

Host-side checks require no Android device:

```powershell
py -m unittest discover -s tools/sdk37 -p test_tls_fixture.py -v
py -m py_compile tools/sdk37/tls_fixture.py tools/sdk37/run_tls_tests.py
```

`--wait-for-debugger` starts the selected instrumentation methods with
`-e debug true` and allows 900 seconds for an explicit JDWP attachment. It does not
attach a debugger or create port forwards. A stalled / aborted debugger run is
recorded as failure, not accepted as functional evidence.

### General compatibility controls

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

## Parcel and plugin IPC

`run_parcel_tests.py` runs on an explicitly selected Android 17 device with the normal debug host and matching androidTest APK already installed. Instrumentation restarts the host and interrupts scripts. The helper does not install packages, clear data, grant permissions or change compat settings. Use a disposable device profile for the real plugin suites, which start native runtimes and services. Each invocation includes two deliberate negative controls, checks every selected test's completion identity, rejects skips, records installed APK hashes and snapshots the host and selected plugins before and after the run.

Use a new output directory for every run. If instrumentation times out, inspect and stop that selected test before starting another suite; an adb client timeout does not prove that the device-side test has exited. `logcatExitCode` records diagnostic collection separately from the functional assertions.

The negative fixture writes two integers and reads only one from a length-prefixed `Parcel.writeValue` payload. With hardening disabled, both the host and a private debug receiver process must accept it. With hardening enabled, both must throw `BadParcelableException` with the length-mismatch diagnostic. The test checks that the receiver PID differs from the host PID. Normal `ExecutionConfig`, nested `PluginInfo`, Tasker JSON and shortcut extras must still round-trip unchanged. Tasker and shortcut tests cover their serialized payloads; they do not launch the external Tasker app or verify a launcher shortcut UI.

On the tested API 37.0 image the effective gate is `PARCEL_HARDENING` / `416031865`, enabled since target 37. The executor checks `packageOverrides` and each package's actual target; a host override cannot stand in for an independent plugin's override. A `rawOverrides` entry alone is not counted as effective. The platform feature flag is also necessary; the negative controls detect an image on which changing compat state has no semantic effect. This test concerns length-prefixed reads; it does not claim that every typed AIDL read, native Binder implementation or recycled-Parcel check uses this gate.

Record existing overrides before changing them. For a target 36 control, set the selected host and plugin to the same state, then run with the corresponding expectation:

```powershell
$Sdk37Adb = 'E:/.android/sdk/platform-tools/adb.exe'
$Sdk37Serial = 'emulator-5572' # Replace with the selected test device.
$Sdk37Host = 'org.autojs.autojs6'
$Sdk37Plugin = 'io.github.supermonster003.autojs6.plugin.quickjs.runtime'

& $Sdk37Adb -s $Sdk37Serial shell am compat disable PARCEL_HARDENING $Sdk37Host
& $Sdk37Adb -s $Sdk37Serial shell am compat disable PARCEL_HARDENING $Sdk37Plugin
py tools/sdk37/run_parcel_tests.py --adb $Sdk37Adb --serial $Sdk37Serial --expected-target 36 --hardening false --suite quickjs --output build/sdk37-parcel/t36-disabled-quickjs

& $Sdk37Adb -s $Sdk37Serial shell am compat enable PARCEL_HARDENING $Sdk37Host
& $Sdk37Adb -s $Sdk37Serial shell am compat enable PARCEL_HARDENING $Sdk37Plugin
py tools/sdk37/run_parcel_tests.py --adb $Sdk37Adb --serial $Sdk37Serial --expected-target 36 --hardening true --suite quickjs --output build/sdk37-parcel/t36-enabled-quickjs
```

For the target 37 default control, build with `'-Pautojs.sdk.target.override=37'`, install the corresponding host and test APKs, and reset only the host override. The still-target-36 plugins must retain their explicit enable override until their own P5 migration. Run with `--expected-target 37 --hardening true`. After testing, restore each recorded original override, or `am compat reset PARCEL_HARDENING <package>` if that package originally had no override. Do not reset unrelated changes.

| Suite | Required provider or fixture | Expected result beyond the two negative controls |
| --- | --- | --- |
| `controls` | Debug-only private receiver included in the host | Both local and remote mismatched reads follow the selected policy |
| `wire` | Same receiver | Existing six-field `ExecutionConfig` consumes exactly its written bytes; nullable / populated `PluginInfo` and nested capabilities, Tasker JSON and Lua shortcut extras retain their values |
| `metadata` | Repeat `--plugin-package <applicationId>` for every package to check | Production `InstalledPluginRepository` finds each selected package in its package-manager snapshot and complete discovery, with a title, version and no binding error |
| `opencv` | `.plugin.opencv`, matching device ABI | Host loads the installed JNI implementation and obtains the expected matrix mean; this is a host-process load, not a separate-provider Binder execution |
| `imagequant` | `.plugin.imagequant`, matching device ABI | Host loads the JNI implementation and produces a valid indexed PNG |
| `barcode` | `.plugin.mlkit.barcode` | Image PFD goes to the provider; structured result decodes the fixed EAN-8 `96385074`, format, bounds and corner points |
| `node` | `.plugin.nodejs`, matching signer / ABI | Real provider executes the source and returns the exact console marker |
| `lua` | `.plugin.lua.runtime`, runnable provider with matching signer / ABI | Real provider executes sequential sessions, including host callbacks and module loading; it is not the fake conformance service |
| `quickjs` | `.plugin.quickjs.runtime`, matching signer / ABI | Source PFD executes with exit code 0 and a returned console line; callback text can include line terminators |
| `python` | `.plugin.python.runtime`, matching signer / ABI | Current production host engine negotiates the protocol, executes a temporary `.py` file, returns exit code 0, exact stdout byte count, no stderr and the expected console line |
| `yolo` | `.plugin.yolo.ncnn`, matching signer / ABI, local model fixtures | Existing R2 acceptance validates remote Binder, owned PFDs, reference detections, idempotent close and reopen |
| `mcp` | `.plugin.mcp.server` | Real server reaches RUNNING on loopback with the host attached, reports endpoints and stops cleanly |

All `.plugin.*` suffixes in the table have prefix `io.github.supermonster003.autojs6`. The YOLO case additionally requires `model-manifest-v1.json`, `model.ncnn.param`, `model.ncnn.bin`, `bus.jpg` and `expected-bus-detections.json` in the host-private `files/yolo-r2-fixture` directory. Verify the files against the existing provider repository's `fixtures/yolo11n/fixture.lock.json` before staging; untracked model/image bytes can be under `fixtures/local/yolo11n`. See `tools/yolo/verify-yolo-r2.ps1` for that fixture contract. A missing fixture is a failure, not a skip or pass. No model is bundled by this SDK check.

Paddle v4 and Rapid v6 reuse the public-script runner. Enable `PARCEL_HARDENING` separately for the host, `io.github.supermonster003.autojs6.plugin.paddleocr.v4.mobile` and `io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv6`, retain their package/compat snapshots, then run:

```powershell
py tools/16kb/ocr_provider_smoke.py $Sdk37Serial --adb $Sdk37Adb --jobs paddle:v4 rapid:v6 --image generated --enable-packages io.github.supermonster003.autojs6.plugin.paddleocr.v4.mobile io.github.supermonster003.autojs6.plugin.rapidocr.ppocrv6 --label sdk37-parcel-ocr --out build/sdk37-parcel/ocr
```

The OCR runner restarts the host, enables those plugins in Plugin Center, clears logcat and writes a test receipt. Run it after instrumentation has finished. Expected receipt: `complete=true`, both rows `ok=true`, and recognized text containing `TEST 16 KB` and `Hello World` (word segmentation may differ). These results cover the selected ABI and plugin artifacts; they do not close the 42-plugin P5 release or physical-device gates. The [P2.7 evidence](../../docs/dev/evidence/android-sdk-37-p27-20260913.md) also retains unsuccessful exploratory tests and their explanations.

Host-side checks, without adb:

```powershell
py -m unittest discover -s tools/sdk37 -p test_parcel_evidence.py -v
```

## Storage, widgets and notification views

`run_framework_probe.py` runs real Rhino scripts against the installed host on an **exclusively owned API 37 emulator**. It stops host work and removes simple tasks rooted in that host, including external pickers left in those tasks by failed probes. It creates fresh fixtures and output directories. It never installs APKs, changes compat overrides, grants permissions or clears preexisting app data. Do not use this tool on a shared emulator.

Build normal and target 37 debug APKs separately and archive each before another build overwrites the outputs. Install the desired APK yourself. Select the emulator explicitly. If using a private ADB server on Windows, set `ANDROID_ADB_SERVER_PORT` in the same shell as Python. The server must have a stable connection to that emulator.

```powershell
python tools/sdk37/run_framework_probe.py --serial emulator-5586 --expected-target 36 --suite storage --expect-trash-directory false --output build/sdk37-storage36
python tools/sdk37/run_framework_probe.py --serial emulator-5586 --expected-target 37 --suite storage --expect-trash-directory true --expect-compat TRASH_BEHAVIOR_CHANGE=true --output build/sdk37-storage37
python tools/sdk37/run_framework_probe.py --serial emulator-5586 --expected-target 37 --suite pickers --output build/sdk37-pickers37
python tools/sdk37/run_framework_probe.py --serial emulator-5586 --expected-target 37 --suite allfiles --expect-all-files false --output build/sdk37-allfiles-denied37
python tools/sdk37/run_framework_probe.py --serial emulator-5586 --expected-target 37 --suite widgets --expect-widget-icon-rejected false --output build/sdk37-widgets37
python tools/sdk37/run_framework_probe.py --serial emulator-5586 --expected-target 37 --suite notifications --expect-compat CHECK_SIZE_OF_INFLATED_CUSTOM_VIEWS=true --output build/sdk37-notifications37
```

Replace the serial, paths and platform expectations with those of your disposable device. Storage / widget / picker fixtures require all-files access. Notifications require POST_NOTIFICATIONS. Widget hosting requires an explicit test-only grant on the selected emulator:

```powershell
adb -s emulator-5586 shell appwidget grantbind --package org.autojs.autojs6 --user 0
```

The suites check the following:

- `storage`: real `files` operations, ExplorerFileProvider / PathChecker, mounted volumes and removable-volume read/write, production history and trash repositories, `images.save` / `media.scanFile`, actual MediaStore trash / restore paths, the ordinary Contacts Directory permission baseline, and the all-files Settings entry. A visible script Activity prevents cached-process freezing while a worker runs the checks. Each run touches only its newly created files and rows. A normal SD card does not exercise unreliable USB volume filtering.
- `pickers`: system document selection and cancellation, persistable read grant / release, Photo Picker selection and cancellation. The driver targets the English API 37 DocumentsUI / Photo Picker layout, verifies the foreground package, handles directory navigation and the explicit photo Done button, and checks returned document bytes or this run's unique photo pixels. It does not test WebView File System Access. Other languages / OEM pickers require their own driver and must not be silently accepted.
- `allfiles`: check the explicit expected state through the production permission helper and platform, request Settings from the foreground Activity and verify that the app-specific page becomes foreground. A successful start request alone is insufficient. Toggle the host's switch in Settings between runs. Revocation can kill the host process, so the denied and granted observations run in separate processes. Restore the initial permission state when finished.
- `widgets`: actual AppWidgetHost registration, multi-digit mapping retention and obsolete-key removal, first click and click after changing the script, plus separate oversized Bitmap and Icon controls. On CE2A.260420.019, the Bitmap control throws for both targets, but oversized Icons only warn at target 36 / 37. `--expect-widget-icon-rejected false` requires that actual warning. This is an observed image / official-documentation discrepancy, not evidence that all API 37 devices accept oversized Icons.
- `notifications`: production `notice` / BigText, actual AppForegroundService notification, small custom views and a custom ImageView hierarchy exceeding the device's resource limit. The runner matches SystemUI's warning / rejection to this run's notification ID, captures the notification shade and requires cleanup. An active notification or successful `notify()` alone does not prove custom content rendered.

`--expect-compat NAME=true|false` may be repeated for the four known storage / notification changes. It checks **effective host package overrides**, never pending raw overrides. It does not imply that a provider-owned gate or platform feature flag was exercised. See the [P2.8 provider scope](../../docs/dev/evidence/android-sdk-37-p28-20260913.md) and [P2.9 image-specific limits](../../docs/dev/evidence/android-sdk-37-p29-20260913.md) before choosing expectations. Reset only the overrides and test permissions you changed; never apply device-wide policy changes to make a test pass.

Every output includes the installed target / APK hash, exact launched script and hash, case-bound per-row receipt, before / after state, focused window, screenshot and bounded-time system logs. Missing / duplicate / malformed rows, wrong target, assertion failure, changed compat state or missing negative-control logs fail the run. Failed UI attempts can leave an external picker in the test host's task; the next run removes that simple host task without stopping unrelated system applications. Preserve failed output directories as diagnostics.

```powershell
python -m unittest discover -s tools/sdk37 -p test_framework_probe.py -v
```

## SMS and notification listeners

`run_sms_probe.py` injects three fictional incoming messages with `adb emu sms send`
on an **exclusively owned API 37 emulator**. Each run reserves a new sender number.
The host must already be debuggable, have `READ_SMS` / `RECEIVE_SMS` and notification
listener access, and must not hold the default SMS role. Use an English, unlocked
AVD with a working default SMS app and notification assistant. This tool stops host
work but does not install, grant permissions, switch roles, change clocks or delete
SMS. Dispose of the read-only AVD overlay after the session.

```powershell
python tools/sdk37/run_sms_probe.py --adb "$Sdk37Adb" --serial emulator-5590 --expected-target 37 --output build/sdk37-sms37-default
```

Use the same code with target 36 default / `FILTER_GENERIC_OTP` enabled and target
37 default / disabled. Change only this host override between runs. The runner
checks the effective override, platform classification (`0`, `1`, `513`), actual
app-UID query and `SMS_RECEIVED` visibility, ordinary-notification delivery, and
English OTP redaction callbacks. A missing callback fails the run even if the SMS
query / broadcast subset passed. Preserve the receipt and system notification dump
to distinguish platform publication from delivery to `events.observeNotification`.
The 45-second observation after injection does **not** verify three-hour expiry,
delayed broadcast release, SMS Retriever or any exemption. System-side queries are
fixture delivery controls, not proof that scripts can read those rows.

## WebView file access

`run_web_files_probe.py` uses the production `ui.webview` chooser implementation,
with observation-only `show_file_chooser` / `file_chooser_result` handlers. It uses
locally supplied HTML with an HTTPS base origin, actual taps and system DocumentsUI.
Use an exclusively owned, unlocked English API 37 emulator with an installed
debuggable host of the expected target. The runner interrupts that host's work;
preflight rejection does not stop it or create files on the device.
It creates and removes one unique Download document via adb, without giving the
host broad storage access. An isolated DevTools forward reads DOM results and is
removed after the run. It also verifies root Activity association and ordinary
parent theme inheritance.

```powershell
python tools/sdk37/run_web_files_probe.py --adb "$Sdk37Adb" --serial emulator-5590 --expected-target 37 --output build/sdk37-web-files37-default
```

Each run covers HTML file selection / cancellation and File System Access open /
cancellation, save cancellation and directory cancellation. It records actual
`FileChooserParams` modes, intents, result URI flags, read / write permission checks,
persisted grants, browser file content or cancellation results, XML and chooser
screenshots. The validator requires the intent and actual grants to match the
observed gate, as well as exact fixture content. HTML input cancellation may produce a `change` with an empty file
list; File System Access cancellation rejects its promise with `AbortError`.
Repeat with `ENABLE_FILE_SYSTEM_ACCESS` default / explicit override for both host
targets. With this gate disabled, save and directory calls are expected to reject
before the native chooser callback; those rows are marked `blocked-before-chooser`,
not counted as successful user cancellation. Save writes, directory selection / traversal, persisted browser handles,
physical input, documentation widgets and the independent HTML plugin need their
own cases. An API name exposed by JavaScript is not sufficient evidence of support.

These probes require the actual host window to be focused. Retain startup ANR or
loading failures as diagnostics and use a new output directory for a clean rerun.
They never dismiss application-error dialogs or count such an attempt as a pass.

## Configuration changes, large screens and IME

`run_configuration_probe.py` observes actual UI-script, Main, Edit and Log
activities through Activity lifecycle and component callbacks. Use an exclusively
owned API 37 emulator with a debuggable host of the expected target, an English
unlocked UI, software IME enabled, and test overlay / all-files / notification
access already configured. The Ace route requires a working installed Ace plugin;
falling back to the native editor fails the run.

```powershell
python tools/sdk37/run_configuration_probe.py --adb "$Sdk37Adb" --serial emulator-5592 --expected-target 37 --route script --form tablet --output build/sdk37-config-script37-tablet
```

Routes are `script`, `main`, `edit-native`, `edit-ace` and `log`; forms are `phone`
(1080x2424, 420 dpi, sw411dp) and `tablet` (1200x1920, 240 dpi, sw800dp).
The runner stops host work, warms its Main Activity, launches a separate observer
script, seeds unique unsaved text and explicitly opens the IME. It rotates the
display, observes Back when the IME is visible, and on tablets enters / leaves
actual split screen with Settings. The UI-script route also requests landscape:
the default API 37 image honors this on the phone and ignores it on the tablet
for both host targets. No compatibility override is changed by this tool.

Every stage records Activity identity, task, window configuration, multi-window
state, IME insets, actual input / editor bounds, text, dirty flag and editor
selection, with a focused-window dump and screenshot. Editor restoration gets a
bounded wait before state is judged. The validator rejects missing lifecycle
evidence, script recreation, changed text / dirty flag / cursor, wrong editor,
obstructing windows and inputs under an inset-bearing IME. Fullscreen IME
extraction can report a zero bottom inset; review its screenshot. Passing these
checks does not establish general layout, font, composing-text or hardware-input
coverage. Main SearchView handling of Back is also recorded and must be interpreted
separately from restoration after a configuration change.

Display size, density and rotation are restored and the host is stopped after
each run. The tool does not install packages, grant permissions, change roles,
clear application data or stop Settings. It changes editor preferences and can
leave uniquely named private observer / draft files; retain the output and then
dispose of the read-only AVD overlay. Never use this on a shared emulator or phone.
`--pause-before-split PATH` is a diagnostic-only rendezvous: after `debug-ready.json`
appears, attach a debugger to the reported PID and create that new marker within
120 seconds. It does not itself create a debugger session or adb forward.

```powershell
python -m unittest discover -s tools/sdk37 -p test_configuration_probe.py -v
```

See the [P2.12 evidence](../../docs/dev/evidence/android-sdk-37-p212-20260914.md)
for the script lifecycle fix, remaining Main / editor / Log observations, exact
APK / plugin versions, and the distinction between an older runner's success flag
and the final validation result. The separate memory-limiter status captured at
preflight is an environment observation, not a memory-pressure workload test.

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
