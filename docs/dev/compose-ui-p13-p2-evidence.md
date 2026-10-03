# P1.3 registration and P2 renderer evidence

Date: 2026-10-03. Starting plugin d383be0 (build 10), isolated host 9c139db474 (build 5309).
Host master 2082edef14 (build 5315) was merged without conflicts as 8372f53eee. The untracked
layout-code-generation-model-roadmap.md in the original host worktree was left untouched.

## P1.3

The wizard pre-registers Compose UI as an optional official UI plugin alongside ImGui. The existing
INFO-only discovery, signer authorization, enablement policy and numeric minimum-host presentation
are reused; no Binder capability endpoint, additional permission or package query was introduced.
The remote-index filter is unchanged: pre-registration does not publish a remote downloadable APK.
The changelog announces installed-plugin management and the development preview in all 10 languages,
without claiming the P3 script global. This is a necessary wording correction to the roadmap's early
script-API announcement, not an added or split implementation stage.

The confirmed minimum host is AutoJs6 6.8.0 / 5316. Its version, ComposeUiIds constant, reviewed API
snapshot, plugin Manifest/constants/tests and all generated minimum-version documentation agree.
The staged release API AAR is 254418 bytes, SHA-256
3ba7c215262e889034eef61e6ba0d5414839712a03284e3d009a96696cce5266 (MPL 2.0).
Only deployment metadata changes; V1 wire/model/catalog semantics and the 51-component shared
fingerprint f3042acc624d499feea9907a20257b62debaa6c523f40bfce19b084274334576 remain unchanged.

Host verification: 25 relevant JVM tests (4 registration, 5 wizard catalog, 16 Compose session/
selection/classpath), plus 13 API JVM tests pass. app debug, instrumentation and production app
release APKs assemble; both host native alignment gates pass. Existing Compose host integration
and the new controlled-control cases pass on a clean API 24 x86 AVD and Xiaomi API 35 arm64 (9 each).
The protocol document now states the confirmed deployment floor and the difference from ImGui.
Generated host changelog/README history was rendered locally through existing generator functions;
no online metadata was refreshed. No public compose JS entry is delivered by this milestone.

## P2.1, P2.2, P2.3 and P2.6

The renderer delivers the 20 basic/interactive types and full 20-operation modifier vocabulary in
the unchanged roadmap sections. The modifier work was needed by the basic components and was
completed alongside them; TextField/lazy/scaffold/dialog sections remain unchecked. Capabilities
and dispatch share one table, with a compiler-exhaustive RenderKind and an independent coverage test.
The icon table contains 280 explicit core names (49 glyphs, five styles, plus auto-mirrored forms).
Theme mapping implements 48 color roles, 15 public typography roles, seed/night/dynamic colors and
font scale. Seed colors use an independent CIELAB/LCh palette; no HCT or new runtime dependency is
claimed. Dynamic colors use the public Android 12+ API and fall back to seed on older devices.

Controlled values are host-owned proposals until a patch accepts them. The host device suite checks
Switch/Checkbox/RadioButton and Slider payloads, ordering, generation, and non-inline callback delivery.
Modifier tests cover ordered pixels, scopes, actual long-press click suppression, independent nested
clickable enablement, keyboard focus observation and scroll-state retention after styling changes.
Scroll state uses a key around the entire loop item: keying only the composable when branch loses
state when a preceding operation is inserted. Commands reject removed nodes immediately, including
the interval before Compose disposes the old command handle. Host error dispatch preserves nodeId/prop.

NodeStore now accepts first generation 0 and an initial empty batch while rejecting replays. A flag
tracks acceptance without exposing a negative UiEvent generation. Slider's derived tick array is
bounded by MAX_VALUE_ITEMS, and float-collapsed/infinite ranges are rejected before composition.
Borrowed bitmap painters resolve a weak handle per draw and tolerate recycle without retaining pixels.
A FrameLayout guard contains runtime/linkage failures in measure/layout/draw, reports once and can
retry a subsequent frame. Fatal VM errors are not caught. This is bounded failure handling, not a
claim of general heap-leak or resource-exhaustion immunity.

### Verification

| Check | Result |
| --- | --- |
| Plugin JVM | 45 pass: existing identity/resource/AAR guards plus NodeStore 8, ValueMapper 5, ThemeMapper 5, ModifierMapper 5, CatalogCoverage 3 |
| Host JVM/API | 25 relevant host tests and 13 frozen API tests pass |
| Plugin instrumentation | API24 x86 and API35 arm64: 17/17 each, comprising 12 renderer semantics/pixel/lifecycle cases and 5 activation/discovery/native inventory cases |
| Cross-APK debug integration | Same two devices: 9/9 each, including shared class identity, native Path iteration, owners, resource configuration, counter, theme/event/close handling and controlled inputs |
| Minified plugin integration | Same two devices: 4/4 each, counter/dispose, standalone INFO and both controlled-input cases |
| Assemblies | Plugin debug/androidTest/release and host app debug/androidTest/release succeed |
| Dependency gates | 51 host-shared components aligned in four host variants and both plugin variants; no host/API/Compose ownership drift |
| Lint | Plugin debug clean; release 0 errors with 3 existing unused identity-resource warnings |
| Generated assets | 36 Markdown artifacts consistent; icon check passes; CI comments now include renderer-local tests |
| Final local release | autojs6-plugin-compose-ui-v1.0.0-6b41b408.apk, build 12, 1958242 bytes, CRC32 6b41b408 |
| DEX boundary | Debug 9344644 bytes / 13064 classes / 90360 method references; release 5808 classes / 37805 references; no bundled host API/shared class definitions |
| Signing/native gate | Signed single APK, exact four graphics-path ABI entries, uncompressed and ELF/ZIP 16 KB aligned |

Devices: clean compose_ui_p2_api24 AVD (emulator-5560, API24, x86) and Xiaomi Pad 6 Pro
968e9f18 (API35, arm64). The first AVD start lacked room for its default 12 GB userdata image;
only its own config was reduced to 2 GB and it then booted successfully. Existing AVDs were not
changed. Test packages use the dedicated org.autojs.autojs6.compose.spike host and the plugin/test
packages, whose absence was checked before installation.

The initial long-click test advanced synthetic event time but the renderer owns a real Android
recomposer clock. It now sends down, waits for the actual long-click event, then sends up. Physical
key injection similarly takes the real view out of touch mode for focus tests. The current v2
Compose test rule avoids the obsolete unconfined test scheduler. API24 lacks the Window PixelCopy
overload used by captureToImage: the tests draw the actual view hierarchy to a software bitmap on
that API, while API26+ uses PixelCopy. Both paths verify the same modifier-order pixel behavior.
A test-only Startup provider was removed from the instrumentation manifest because a standalone
test-package process cannot see Kotlin supplied by the target APK; production manifests are unchanged.

P2 verification adds only a non-exported debug test Activity and a BOM-managed ui-test-junit4 test
dependency. Neither enters the release plugin. No runtime dependency/native inventory changed.
No public compose JS global, TextField/IME, Lazy/Scaffold/dialog/progress implementation, inrt APK
execution, full-host regression, TalkBack fleet, 16 KB page runtime or long-term performance/memory
gate is claimed here. Those remain in the existing roadmap. No push, Release or index publication.

P1.3 commits: host 0ee2953425, plugin 4b89901 (build 11), preceded by host master sync 8372f53eee.
P2 source/tests/evidence are the following local integration commits; plugin build is 12.

## Cleanup and handoff

The four temporary packages on Xiaomi were uninstalled successfully. The owned API24 AVD was
identified by adb, shut down, and deleted with avdmanager after checking its registration against
the app/build-owned path. Both its data directory and registration are absent. The original AVD
and user-installed apps/data were preserved; no /sdcard content was removed.

The original host worktree gained unrelated uncommitted Screen Color Picker/Manifest/wizard/version
changes during this session. They were not overwritten, staged or merged. Completed host work stays
on spike/compose-ui-p0 for integration, with all Compose changes committed separately from that work.
Next implementation section is P2.4, followed by P2.5; all remaining stages keep their original scope.
