# Script API coverage checks

`RuntimeWebMediaDeviceTest` covers actual video/audio file capture across Node
main-thread imports, CJS/ESM/nested Workers and Bun. It verifies browser uploads,
media digests, video-frame/AAC decoding, native audio limits, rotation/background
stop, picker timeout and Worker exit while a parent View remains open. Run
`RuntimeWebMediaPermissionDeviceTest` separately with RECORD_AUDIO revoked, then
restore the original permission flags; revocation kills the host process.
`RuntimeWebMediaPackagedDeviceTest` produces and exercises genuine Node, nested
Worker and Bun APKs. Only numeric media evidence is retained. Preserve original
APKs, permission flags, volume and rotation before running these device suites.

`extract_exports.py` reads `script-modules.json`, the metadata the KSP processor
of the script module layer writes (`app/build/generated/ksp/appDebug/resources/autojs6/`,
built by `gradlew :app:kspAppDebugKotlin`; `--metadata` picks another file):
modules and their aliases, functions with aliases and placement, getters,
properties and Java classes. Names defined by `@ScriptInstall` hooks come from the
module's `installs` list, or until the processor writes it, from the Kotlin
registrations of the current `augment` package (rows marked `"metadata": "kotlin"`).
Without the file (no KSP build) it falls back to the `ScriptRuntime` mounts and
explicit `Augmentable` assignments and warns; `--source ksp|kotlin` forces one,
and `--compare-kotlin` writes the differences of the two sources (augment roadmap
T10). `check.py` compares those names with the associated
documentation pages, resolves their real TypeScript symbols, and queries the
plugin's actual semantic and static completion providers.

`node tools/api-coverage/check-runtime-bridges.mjs` compares the real Node client
exports and Bun SDK method table with the host's Pinyin, Pinyin4j, coordinate and
console control routes, all OpenCC names and image routes. OpenCC checks invoke
every real client alias and require an asynchronous host dispatch; image checks
also match the development catalog and explicitly account for client helpers.
It uses sibling Node/Bun repositories by default; two
optional positional paths select other checkouts. It also guards the Node type
index entries. This focused cross-repository check does not treat unsupported
stubs, no-ops, native alternatives or Rhino delegation as full API coverage.
Run the Node wrapper regression, Bun client tests and the host's
`RuntimeApiBridgeDeviceTest` separately for argument, error and device behavior.
`OpenccImageBridgeDeviceTest` additionally covers real CJS/ESM/Bun conversions,
ARGB pixels and alpha, transforms, encoded and PFD byte round trips, bounded JSON
results, revoked grants and execution-owned handles. Workers use their documented
`autojs6:bridge` entry in that baseline suite. `CaptureWorkerBridgeDeviceTest`
adds the real CJS/ESM/nested Worker facades, large byte transfers, Bun consent
and actual pixels across rotation, fixed dimensions, denial, timeout, cancellation
and grant checks. The screen tests use an owned color fixture and real Android UI.
The same suite checks Worker pinyin constants and compact results, native console
output plus host window controls, and parent-owned coordinate bases in both
orientations. Bun recording checks inspect actual H.264 tracks, MP4 duration,
sample timestamps and decoded fixture pixels, then verify capture restoration,
existing-file rejection and partial-file cleanup. Video sample presentation times
may be reordered by B frames; the test does not confuse decode order with display
order. `PinyinOptionsTest` covers integer-valued JSON/Rhino number representations.
`RecordingAudioDeviceTest` decodes real AAC, measures an owned tone against a
video pulse, checks microphone/playback/mixed modes and source opt-out policy,
and takes four-quadrant screenshots across rotation while recording. It also
checks notification stop, execution cancellation, independent grants and cleanup.
Run `RecordingAudioPermissionDeviceTest` separately with RECORD_AUDIO denied;
revoking that Android permission kills the host process. Restore the previous
permission and media volume after testing. The fixture discards all recorded
files and preserves only numeric evidence, not microphone recordings.

`NodeScreenRecordingDeviceTest` reuses the AAC/rotation fixtures on the Node main
thread and exercises all four audio modes in real CJS/ESM/nested Workers. It checks
concurrent parent/Worker screenshots, audio-only recorder forwarding, cross-thread
stop, stale-ID protection, Worker exit cleanup and raw capability rejection.
`NodeScreenRecordingPermissionDeviceTest` runs separately with Android RECORD_AUDIO
denied, checking that silent recording and screenshots remain usable. Run the Node
`test-screen-recording.js` and `test-opencc-images.js` wrapper regressions as well:
they cover abort/startup races and keep consent options out of image.captureScreen.
The structural bridge check includes all ten current Worker modules and compares
screen_recorder methods and catalog records with the host and Bun method sets.

`AdvancedImageBridgeDeviceTest` validates advanced images through Node CJS/main ESM,
CJS/ESM/nested Workers and Bun, using owned images and a local HTTP fixture. It checks
filters and straight-alpha pixels, HSV channel round trips, circles, multi-color
searches, similarity metrics, SIFT/ORB ROI/scale coordinates, no-match nulls, opaque
Mat/feature lifetimes, PNG bytes/quality/resource metrics and descriptor-backed file
output. It also verifies file/network/processing grants, shared image/Mat/feature
quotas, execution isolation and the async capture alias/options. Inputs, outputs
and temporary quantization files are removed; only numeric evidence is retained.
The bridge check compares all 81 host image function registrations with the Node,
Bun and Worker exports, and checks the shared portable adapter bodies for drift.
The Node test-advanced-images.js and Bun host-client tests exercise public dispatch,
Frame reconstruction, Buffer restoration and client cleanup separately from Android.

The extractor also resolves Mail's explicit default-client forwarding table,
including its synchronous-only methods and generated Async names. List suffixes,
mutation chains and dynamic aliases that are not understood must be reported as
unresolved, including changes to referenced forwarding constants.

Keep the host, documentation, declarations and Ace plugin checkouts beside one
another, or supply their locations explicitly:

```powershell
python tools/api-coverage/check.py `
  --documentation ../AutoJs6-Documentation `
  --declarations ../AutoJs6-TypeScript-Declarations `
  --ace ../AutoJs6-Plugin-Ace-Editor
```

The check uses Ace's bundled TypeScript compiler. `--typescript` can select a
different compiler for the canonical declarations. `--skip-ace` explicitly
limits a run to documentation and canonical types; it is not a complete pass.

Outputs are written under `build/reports/api-coverage` by default:

- `exports.json`: registered names, module aliases and unparsed expressions.
- `documentation.json`: name evidence from the associated source pages.
- `surfaces.json`: real type resolution, default/all-group semantic completion
  and receiver-aware static completion for each queried path.
- `summary.json`: scope, counts and failures.

LSP bundles are generated into an owned temporary directory for each check;
the check does not rewrite plugin source or build assets. The plugin's selected
declarations and static index must already have been synchronized. See the
commands in `tools/ace-completion/README.md` for synchronization and freshness.

Run the extractor's regression tests with:

```text
python -m unittest discover -s tools/api-coverage -p "test_*.py"
```

The tests cover nested expressions, comments and strings, aliases, ignored
exports, global assignment, unknown registration forms and scoped document
lookup; `test_script_modules.py` covers the `script-modules.json` reader
(placements, aliases, install hooks and their Kotlin fallback, the automatic
source) and, with a KSP build present, that every Kotlin registration is still
exported. Newly unparsed registration expressions fail the full gate instead of
silently reducing its denominator. `policy.json` contains narrowly named
internal/standard exceptions, explanations for dynamic registrations, page
mappings and selected returned-object probes. Public API gaps must be fixed at
their source rather than added to the internal exceptions.

This is a name and type-resolution gate, not a proof of every runtime overload.
Java prototype inventories, color tables, generated Compose catalogues and
conditional JSOX extensions have dedicated checks. The declarations repository's
`tools/verify-api-coverage.mjs` also tests invalid calls, all handwritten
declaration bodies, and isolated opt-in JSOX augmentation. The Ace repository's
`tools/ace-lsp/verify-api-coverage.mjs` tests default returned-object inference,
JSOX activation and false positives. Coordinate behavior is tested in the host's
`CoordinateScaleTest` against portrait/landscape dimensions, explicit modes,
partial base configuration and numeric boundaries.

The `Script API coverage` workflow checks related repository heads by default.
Its manual inputs can select coordinated branch names or commits when reviewing
a change spanning all four repositories. A pass against one set of revisions
does not certify a different set of published artifacts.

## Physical-device checks

Build and install matching current host and instrumentation APKs with `adb install
-r -t`, plus the current Ace plugin APK. Select APKs using `output-metadata.json`
and record their hashes; an unchanged version name does not prove the installed
bytes match the current checkout. Do not clear application data.

The existing device runner checks every selected test's completion identity and
rejects failures, skips and missing results even when `am instrument` exits zero:

```text
python tools/sdk37/physical_device.py instrument --serial SERIAL --suite coordinate-api --expected-target 37 --output build/reports/device-coordinates
python tools/sdk37/physical_device.py instrument --serial SERIAL --suite ace-api --expected-target 37 --output build/reports/device-ace-api
```

Each output directory must be new. Coordinate tests temporarily rotate an owned
UI script through 0/90/180/270/0, measure Android's actual display independently,
and restore the original rotation settings. They use a fresh Rhino runtime per
base configuration and also retain one UI runtime throughout the rotations.
Setter failure recovery and the established integer-conversion behavior are
separate cases. Detailed results are written to the host's external-files
`coordinate-scale-device.json`.

The host Ace test loads the installed plugin through the production ABI, restores
the previous settings after testing the genuine defaults, and queries the public
LSP completer with its normal budgets. It checks returned-object completion and
JSOX activation/removal, saving `ace-api-host-device.json` in the same directory.
The plugin's `AceApiCoverageDeviceTest` additionally checks actual popup geometry
and captures screenshots, so a correct candidate list cannot hide an overlapping
documentation tooltip. These are physical-device results only when run on an
identified physical serial; desktop jsdom checks are complementary.

The runtime bridge check also runs `check-ui-compose.mjs`. It compares the 54
portable Compose factories plus AndroidView, the 12
host operations with the development catalog/provider discovery, Node/Bun facade
bytes and Worker window cleanup. Node's `tools/nodejs/api/test-ui-compose.js`
executes real desktop CJS/ESM Workers and checks state/ref/memo, bindings, late
creation replies and cancellation. `RuntimeUiComposeDeviceTest` covers native
interaction, dialogs, Snackbar, floating geometry, grants, Worker cleanup and
Activity recreation in the real runtime processes. Full evidence and the native
interop boundaries are in `docs/dev/runtime-ui-compose-2026-10-18.md`.
The subsequent `RuntimeUiInteropDeviceTest` checks XML attachments, native Views,
factory retention, execution ownership, detached-container cleanup and rotation.
`RuntimeInteropPackagedDeviceTest` builds actual Node/Bun project APKs, verifies
their binary Manifest permissions and exercises the installed outputs. The eight
native View operations are checked against the permission catalog as well.
See `docs/dev/runtime-ui-interop-2026-10-18.md` for this batch's evidence.

`check-runtime-views.mjs` compares all 58 writable View properties across native
handlers, getters, shared Node/Bun accessors and declarations. It also checks 16
additional XML widget factories and that property proxies stay out of serialized
View identities. The desktop tests exercise ordered assignments, flush/recovery,
array snapshots and protection against retargeting a wrapped token. Ace checks
XML literals only for the typed runtime inflater, including Promise signatures.
`RuntimeXmlViewDeviceTest` exercises native controls, actual layout geometry,
selection/progress callbacks, updated list items, error recovery, rotation and
copied image pixels. Packaged tests mix Card/Spinner/SeekBar/property assignments
with Compose. See `docs/dev/runtime-xml-views-2026-10-18.md` for accepted results.

The gate also checks all 12 WebView/Drawer/Toolbar/ViewPager commands against
native handlers and both declaration sets. Desktop tests verify commands and
assignments share a queue, including CJS/ESM Workers and Bun's granted transport.
`RuntimeViewComponentsDeviceTest` exercises real HTML/URL loading, decoded page
JavaScript results, navigation history, Drawer/Toolbar events, offscreen page
lookup, Activity recreation and WebView disposal in all six runtime modes.
The packaged fixture exercises the same four components before its Compose
rotation flow. See `docs/dev/runtime-view-components-2026-10-18.md` for results.


`check-webview-callbacks.mjs` compares 40 callback names and 10 public methods
across native handlers, shared clients and Node/Bun declarations. It executes
both facades against controlled transport, including callback replacement,
asynchronous evaluation, early file results and download bytes. Desktop Workers
exercise termination and conditional client cleanup. `RuntimeWebViewDeviceTest`
uses actual WebViews, DocumentsUI, a standalone document provider, HTTP/TLS and
DownloadManager; it does not substitute native callback results. Evidence and
limits are recorded in `docs/dev/runtime-webview-callbacks-2026-10-18.md`.

The file extension gate also checks all four chooser modes, readDocument and opaque
download identifiers. `test-web-download-stream.mjs` executes the real page helper
with binary data, backpressure, cancellation and failure cases. Native
`RuntimeWebFilesDeviceTest` exercises real system camera capture, SAF creation/tree
grants, blob/data link notifications, byte integrity, cancellation and navigation.
The packaged fixture saves and hashes an actual camera upload, creates a tree child,
and verifies the embedded page helper with a binary blob. See
`docs/dev/runtime-webview-files-2026-10-18.md` for accepted results and limits.

WebView streaming media uses the same `check-webview-callbacks.mjs` shared-facade check, strict Node/Bun declaration smoke files and Ace semantic queries. On supported devices, `RuntimeWebStreamsDeviceTest` covers awaited results, rejection/timeouts/navigation, real audio/video chunks, pause/resume, encoded output, and nested Worker microphone release. `RuntimeWebStreamsPermissionDeviceTest` runs separately with media permissions revoked. `RuntimeWebStreamsPackagedDeviceTest` builds and executes actual Node, nested Worker and Bun APKs. Evidence: `docs/dev/runtime-webview-streams-2026-10-18.md`.

Display/background coverage: `test-web-display-stream.mjs` executes the actual page adapter contract; `check-webview-callbacks.mjs` checks the 19 public View media methods. Device suites are `RuntimeWebDisplayDeviceTest`, `RuntimeWebDisplayLifecycleDeviceTest`, `RuntimeWebNativePermissionDeviceTest` (fresh Android grants/denials) and `RuntimeWebDisplayPackagedDeviceTest`. They validate real changing screen frames, background media in a distinct foreground UID, native file output, clone/cancellation lifetimes and genuine APK Builder output. Evidence: `docs/dev/runtime-webview-display-2026-10-18.md`.

The display/background audio fixtures play a bounded 700 ms, 660 Hz pulse only after the recording app is covered by the test activity. The pulse uses approximately 1/15 of the device's media-volume scale, restores the previous volume on completion/failure, and is absent from display/Compose checks. The microphone decoding assertions remain enabled. `-e proofSuffix quiet` stores targeted acoustic retests separately from the full runtime matrix; `-e webMode node` selects one runtime for host tests. Older playback latency/policy fixtures retain their separately calibrated tone behavior.

Bun automation coverage: `check-bun-automation.mjs` compares 32 public native routes with the Node provider and checks reproducible public adapters/declarations using `tools/host-client/sync-automation.mjs` in Bun Runtime. Bun's 35 client tests include grants, selector/Intent normalization, bounded concurrency and cancellation. `BunAutomationDeviceTest` compares actual Node/Bun host operations; `BunAutomationPackagedDeviceTest` builds and runs genuine APKs. `BunAutomationUnavailableDeviceTest` requires `-e expectedServiceState unavailable` and verifies an unavailable service explicitly; it is not a successful action gate. These tests do not play audio. Device scope and limitations are recorded in `docs/dev/bun-automation-2026-10-19.md`.


Bun file/storage coverage: `check-bun-storage.mjs` compares all 15 usable Node file names, the six native storage routes and reproducible declarations. `BunStorageDeviceTest` compares actual Node/Bun encoding and file semantics, cross-execution storage persistence, cross-runtime sharing, directory isolation, grants and payload rejection. `BunStoragePackagedDeviceTest` produces a genuine APK and verifies file/storage persistence after a force-stop and relaunch. Ace checks actual default-setting file/store completion popups. These tests do not play audio.


Database coverage: `check-bun-database.mjs` checks the common main/Worker/Bun factory, its declarations and seven native methods. Node has facade and real Worker lifecycle desktop tests; Bun includes database SDK tests in CI. `BunDatabaseDeviceTest` checks real SQL types, counts, rollback, scope, grants, cancelled opens and Worker cleanup. `BunDatabasePackagedDeviceTest` produces actual Bun and Node Worker APKs and verifies persistence after force-stop/relaunch. These tests do not play audio.


Sensors coverage: `check-bun-sensors.mjs` checks five native routes and the shared main/Worker/Bun factory and declarations. Node facade and real Worker tests cover cancellation, timeouts, late registration and termination. Bun SDK tests cover negotiated grants and lifecycle. `BunSensorsDeviceTest` and `BunSensorsPackagedDeviceTest` check real sensor data, cleanup and packaged runtime identity without audio.


Shell process coverage: `check-shell-contract.mjs` compares all 25 script-visible
ShellProcess properties/methods and the three event names with the native object,
checks constructor/spawn/default option keys and verifies canonical/Ace declaration
copies. `check.py` includes it and follows process and shell-result return chains.
The declarations repository's `shell-process-smoke.ts` checks event payloads,
nullable results, constructor compatibility and invalid arguments with TypeScript
5.1 and 6. Ace checks default and optional declaration groups, constructor options,
result fields and actual popup signatures. `tools/shell-stream/device_shell_stream_test.py`
executes owned commands with no audio; it reports Root/Shizuku modes only when they
are available and checks explicit Shizuku denial otherwise. Preserve device APKs
and settings, use an isolated fixture path, and clean only owned files.


Host Shell coverage: `check-runtime-shell.mjs` verifies ten native routes and the shared Node/Worker/Bun factory and declarations. Node and Bun SDK tests cover streams, backpressure, binary chunk limits and cancellation. `NodeShellBridgeInstrumentationTest` checks raw routes, privilege requirements, quota cleanup and PTY EOF. `RuntimeShellDeviceTest` checks ordinary/Shizuku main and Worker execution; `RuntimeShellPackagedDeviceTest` produces and verifies real Bun/Worker APKs. All commands operate on owned processes and use no acoustic signal.

Run `node tools/api-coverage/check-runtime-events.mjs` for event/notification host routes and the shared Node/Worker/Bun factories and types. Behavior is covered by the client/Worker suites and RuntimeEventsNativeTest/RuntimeEventsDeviceTest/RuntimeEventsPackagedDeviceTest.

## Runtime device controls and dialogs

Run `node tools/api-coverage/check-runtime-device-dialogs.mjs` to compare the native device/dialog routes and shared Node/Bun factories and types. Behavior is covered by Node facade/real Worker tests, Bun device-dialogs.test.mjs, declaration smoke programs, Ace default popup checks and RuntimeDeviceDialogsNativeTest/DeviceTest/PackagedDeviceTest. The fixtures keep media volume unchanged and play no audio. Full host API counts describe documentation/type/completion coverage, not complete Node/Bun implementation parity.

## Runtime Android objects and calendar tasks

Run `node tools/api-coverage/check-runtime-android-tasks.mjs` to compare the native object/task routes with the shared Node/Bun factories and declarations. Node real Worker tests, Bun client tests, type smoke fixtures and Ace default popup tests cover the public surface. CalendarTaskTimesTest checks short/long days and gap/overlap rules. RuntimeAndroidTasksNativeTest/DeviceTest/PackagedDeviceTest verify real Android objects, persistent task CRUD, filtered broadcasts, actual child execution and packaged relaunch. Fixtures use owned task sources and verify task removal without playing test audio.


## Java/Android object interop

Run `node tools/api-coverage/check-runtime-java-interop.mjs` for the 19 native Java routes, the shared Node/Bun facade and synchronized types. Node uses `test-java-interop.js` and `test-worker-java-interop.js`; Bun uses `java-interop.test.mjs`. These verify transport serialization, exact long values, reserved ESM exports, cancellation and Worker ownership. The full Android contract is exercised by `RuntimeJavaInteropNativeTest`, `RuntimeJavaInteropDeviceTest`, `NodeJavaInteropBridgeInstrumentationTest` and `RuntimeJavaInteropPackagedDeviceTest`. Packaged checks use a genuine Runtime Kit and run the generated Bun and Node/Worker applications before and after relaunch. No audio stimulus is used. The coverage counts describe explicit routes and declared facade members, not all possible Java classes, all overloads or Rhino parity.


## Java callbacks and class loading

Run `node tools/api-coverage/check-runtime-java-callbacks.mjs` for the shared callback/load routes and class-generation wiring. Node uses test-java-callbacks.js/test-worker-java-callbacks.js and Bun uses java-callbacks.test.mjs. RuntimeJavaSubclassGeneratorTest validates generated JVM bytecode, constructor callbacks, protected methods, superclass calls, inherited default methods and valid class names. Build owned JAR/DEX inputs with `python tools/api-coverage/build-java-callback-fixtures.py --output <ignored-directory> --java-home <JDK-17-or-newer>`; --sdk can select the installed Android SDK. Physical suites are RuntimeJavaCallbacksNativeTest, RuntimeJavaCallbacksDeviceTest and RuntimeJavaCallbacksPackagedDeviceTest, alongside the prior Java interop regression. Tests use sound-disabled Android clicks and contain no audio stimulus. Code-loading and return-valued callbacks are verified inside genuine generated applications as well as the host.


Run `node tools/api-coverage/check-runtime-tts.mjs` to compare all 31 registered Rhino TTS method names with the shared Node/Bun/Worker facade, declarations and documentation. Node uses test-tts.js and test-worker-tts.js, while Bun uses tts.test.mjs. RuntimeTtsDeviceTest verifies real engines, WAV output, task events, grants and Worker ownership; RuntimeTtsPackagedDeviceTest uses genuine Runtime Kit applications. Playback uses volume zero, and file synthesis produces no speaker output. The TtsUtteranceTest regression keeps Rhino isDone a boolean. See docs/dev/runtime-tts-2026-10-20.md for accepted evidence and remaining checks.
