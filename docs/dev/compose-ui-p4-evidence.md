# P4 examples, accessibility and guard evidence

Date: 2026-10-03. Plugin baseline a4e9e66 (build16), host baseline 8e6b8b7e9a
(6.8.0 / 5316). Committed host master d9dd9529c0 was merged as c6ef588315, retaining
both Compose history and the mainline code-generation/icon changes. Host build is now 5317.
The original host worktree's uncommitted dataset work was not edited or staged.

## P4.1 packaged examples

The plugin now contains assets/examples/index.json and five real scripts: counter.js (state/render),
form.js (retained TextField/Switch/Slider handles and validation), list.js (1000 keys, reordering,
ref and scrollTo), floaty-hud.js (normal script, worker posts, resizable window and owned cleanup),
and theme.js (seed/dark/dynamic color). Headers state the matching local host/plugin and permission
requirements. Four scripts use the UI directive; the HUD uses a normal script and overlay permission.

The host copies live in app/src/main/assets-app/sample/Compose UI/, with their manifest in
assets-app/indices/compose-examples.json. WorkspaceFileProvider and NodeBridgeSampleCatalog already
enumerate AssetManager directories, so no second category registry was introduced. The explicit
.python/sync_examples.py --host <checkout> command maintains the copies and has a no-write --check.
Plugin builds remain independent of any sibling checkout.

ComposeExamplesDeviceTest reads and executes the actual host APK assets and first compares every
script and manifest byte with the installed plugin APK. The five cases exercise user actions,
including native editor input, slider progress, key identity after reorder, actual dark Surface
pixels, HUD worker updates and natural engine termination after closing its window. They do not
substitute handwritten lookalike scripts for the delivered examples.

Two form issues were corrected before completion. Validation feedback originally followed the
Save button and was clipped by the IME-resized viewport on API35; it now sits immediately below
the editor, and the test requires the message to be visible. On API24, a trace proved Save wrote
the correct result before same-text valueChange sequences 3/4 (text Ada, selection 3/3) overwrote
it with Ready to save. The sample now compares previousNameText and marks the form dirty only
when text actually changes. Selection/composition notifications remain unchanged. Temporary trace
observers were removed; the final failure diagnostic retains visible text and owned handle state.

## P4.2 actual host accessibility

ComposeAccessibilityDeviceTest uses the real built-in AccessibilityService and non-UI Rhino
selectors, including id('start_button').findOnce().click() reaching the page callback. UiAutomation
only keeps the host service unsuppressed and manages test setup; it does not supply nodes/actions
for these two cases. Guard Mode is saved/disabled/restored for the owned host, and Stable Mode is
the default off. The selector script first waits for its own page to become the current window.

The exact ID is start_button, without an added package:id/ prefix; packageName remains the host.
id, idContains and whole-value idMatches (string and regex) pass. A fake package prefix does not.
Standalone text and description queries pass. For the tested Button with one Text and a description,
the clickable parent has empty text, no description, class android.view.View and three children.
The Text child has ID start_label; the description child has no ID or action. Both parent IDs are
start_button. This differs from ComposeTestRule's default merged semantics. No production selector
or renderer mapping change was needed; a raw-tag FilterTest regression was added.

AutoJs6-Documentation api/compose.md now contains these rules, runnable two-script examples and
the limits of the verified tree. Navigation/progress and the local HTML/JSON/search artifacts are
updated. Full generation and freshness checks pass for 145 modules, with 6300 search entries.
The rest of the API reference, declarations and Offline Docs synchronization remain the planned P6
work. This is local preview documentation, not a remote publication or full TalkBack certification.

Instrumentation force-stops its target process. Repeated runs can leave that package's previously
enabled accessibility service in a dead Binding/Crashed state. The fixture only rebinds its already
enabled own component, preserving other entries and the global setting; Android29+ temporarily
adopts WRITE_SECURE_SETTINGS and always drops it, while older systems use the public test shell
API with validated single-token values and exact read-back. A reboot may still be needed to clear
Android24 system_server's accumulated dead bindings. API35 setup also allowed ACCESS_RESTRICTED_SETTINGS
for the isolated test package. None of these changes authorize a new service in the test itself.

The existing shared API24 SDK ramdisk was found to be Magisk-patched. Rebooting caused its manager
stub to install/start and crash above the test UI. The task neither installed nor authorized that
manager and did not change the SDK or its settings. A task-local copy of the original ramdisk backup
is used with an explicit emulator -ramdisk override for the final host run. The backup SHA-256 is
fead89a8139f00d29c9db1ea467d39fb00cdb9d7851c15c2345ab9e796ea9dfd; its decompressed payload has
no Magisk marker. The shared patched file remains untouched.

## P4.3 catalog and instrumentation guards

The API test-source ComposeCatalogExporter reads actual ComponentCatalog.V1 objects and emits
deterministic JSON through :plugin-api:compose-ui-api:exportComposeUiCatalog. No API main source,
wire version, AAR or consumer rule changed. The 47781-byte inventory contains 30 entries (29 NODE
plus Snackbar COMMAND), 114 canonical properties and 12 aliases, including constraints/defaults,
slots and event payloads.

The host .python/compose_catalog_check.py generates real TypeScript property interfaces and Markdown
tables, then checks explicit marked regions against a fresh export. Missing files/markers, components,
properties, aliases, wrong types and stale metadata are failures. Fourteen Python tests and three
exporter JVM tests pass. Generated TypeScript passes tsc 5.1.3 --strict --noEmit --skipLibCheck false
with minimal input-helper stubs and five expected negative cases. Markdown union separators do not
split table cells. The tool does not claim the not-yet-delivered P6 files passed: the missing formal
d.ts and current documentation without the full component table both correctly fail the check.
P6 must supply the actual helper types and factory signatures, plus handwritten API validation.

All 31 plugin instrumentation cases pass on API24 x86 and API35 x86_64: activation/discovery 5,
basic/modifier/theme renderer 12, advanced renderer 5, and native text fields 9. Debug test coverage
is opt-in with -PcomposeUiCoverage=true, using JaCoCo 0.8.14. Release contains no coverage runtime.
CI now invokes the full suite with coverage and preserves its reports; remote CI was not run (D7).
Coverage setup follows the [Android Gradle coverage guide](https://developer.android.com/studio/test/coverage-report).

The combined report counts plugin-source classes and excludes dependency classes and generated
BuildConfig. It records line coverage 1545/1798 = 85.93%, branch coverage 869/1458 = 59.60%, and
instruction coverage 15505/20047 = 77.34%. These are observed metrics, not a new acceptance threshold.
The report and execution data remain ignored build artifacts:

- app/build/reports/compose-coverage/html/index.html and coverage.xml / coverage.csv.
- app/build/outputs/compose-coverage/api24.ec and api35.ec.
- app/build/outputs/compose-coverage/classes-build16.jar preserves matching original compiler output.

The suite used coverage debug build16 with unchanged renderer sources. Final host example/accessibility
runs use the minified release plugin. The first manual coverage attempt passed tests but could not
write its explicitly selected files/ path because this UI-less plugin had never created that directory.
After creating only that owned package directory, both suites were rerun and produced valid execution
data (API24 203303 bytes, API35 209551 bytes). No test failure was hidden by the coverage retry.

## Verification and local completion

| Check | Result |
| --- | --- |
| Host selected JVM | 175/175: previous Compose/augmentation 154, example assets 2, selector filters 19 |
| API JVM | 16/16: frozen contract 13 and exporter 3 |
| Plugin JVM | 63/63 |
| Python inventory tests | 14/14; generated TypeScript including five negative cases compiles |
| API24 plugin suite | 31/31 in 41.009 s, with valid coverage |
| API35 plugin suite | 31/31 in 57.843 s, with valid coverage |
| API24 final host matrix | 7/7 in 22.407 s: five actual examples and two actual-service selector cases, release18 |
| API35 final host matrix | 7/7 in 23.352 s: the same final example/selector suite, release18 |
| Builds | Host app debug/androidTest/release; plugin debug/androidTest/release; native alignment and signing pass |
| Lint | Plugin debug: 0 issues; release: 0 errors, the same 3 unused identity-resource warnings |
| Docs | Plugin 36 generated files and icons match; examples match; external docs 145 modules are fresh |

Implementation commits: plugin 168268f (examples); host c6ef588315 (mainline merge), d86d7a28a4
(catalog exporter/checker), 24eb7ca2dc (example integration), 214f8eedae (actual host accessibility
tests); documentation ac56189 (accessibility chapter and local generated site). Closing evidence
is committed separately.

Final plugin: releases/autojs6-plugin-compose-ui-v1.0.0-3fc65a76.apk, version1.0.0 / build18,
2373541 bytes, CRC32 3fc65a76, SHA-256
9a5bf132177d48e2030848a1934f301ab0ab982aa9508c0578e0ae007e237396.
Release DEX remains 7142 classes / 44715 method references; no renderer production source changed.
Frozen AAR and the 51-entry shared dependency lock remain unchanged; the 5316 deployment floor still
requires the matching local host implementation, not an older unpublished artifact with that number.

All four owned packages (plugin, plugin test, isolated host, host test) were uninstalled on both
AVDs and absence verified. After checking each emulator identity and resolved data/metadata paths,
SDK avdmanager removed compose_p4_api24 and compose_p4_api35; both data directories and registrations
are absent. Other AVDs, physical devices, the original host's unrelated work, and shared SDK images
were not modified. Ignored build reports, logs and the private stock-ramdisk copy remain for reproduction.
The plugin, host task worktree and documentation repository are clean after the closing commits.
P4 is complete; the next starting point is P5.1. All commits remain local, with no push, official
index update or public Release (D7).
Full host JVM/lint, inrt execution, the wider D28 matrix, hostile inputs, performance and long-running
memory measurements remain in their existing P5/P6 stages. No roadmap stage was added, split or discarded.
