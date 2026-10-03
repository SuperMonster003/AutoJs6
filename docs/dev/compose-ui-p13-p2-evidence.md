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
