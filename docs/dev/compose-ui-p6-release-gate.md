# P6 documentation, declarations and local release gate

Dates: 2026-10-03 to 2026-10-04. Starting plugin commit dbf2c26 (build21), host implementation
ef707fd667 (6.8.0 / 5317). Maintainer decisions Q2-Q7 are recorded in plugin
27be441 (build22) and the approved performance evidence in host d29eb47782.
The production renderer and frozen V1 AAR/shared dependency locks remain unchanged.

## Maintainer decisions and performance review

Q2 keeps icons-core plus caller-supplied images. Q3 retains index matching and one
warning per session for missing keys. Q4 uses only compose.floaty. Q5 adopts the
existing P5 p90 + approximately50% time budgets and +10% APK/DEX budgets as review
triggers. Exceedances require a rerun or explanation and explicit maintainer
acceptance; cold/PSS/all scrolling metrics continue as observations. The original
236 observations, sampling parameters and raw-record digests are unchanged.
The exact values and procedure are in AGENTS.md section14.

Q6 confirms only light/dark mipmap ic_launcher.png assets, including plugin-center
and README display. The current artwork remains the existing placeholder pending
the maintainer's formal source artwork. Q7 fixes the post-1.0 order F.1 through F.6,
with the existing evidence condition for fine-grained updates. No stage was added,
split or discarded, and D7 continues to prohibit remote publication.

## P6.1 API reference and Offline Docs

Documentation commit 2832a4d delivers api/compose.md and the seven planned type and
component pages, plus sidebar/toc/dataTypes/progress. It documents all module
members, node/state/modifier/theme/session/window behavior, precise callback
receivers, 18 error codes, limits, selectors and packaged applications. Important
implementation details were checked against the actual runtime rather than the
old draft: update returns the callback result, window.on/showSnackbar return its
session, batch does not undo ordinary JS/state side effects, and close is not a
native-disposal completion acknowledgement. Ordinary rotation preserves the engine;
actual Activity recreation destroys it. Container nullish handling does not relax
the validation of individual option members.

The Components page contains the exact P4-generated catalog region: 30 entries,
114 canonical properties. It explains the 29 node factories and Snackbar command,
slots, event parameter order, input helpers, 20 modifiers and eight shortcuts.
Icons list 49 ordinary core names across five styles and 35 AutoMirrored forms.
Both delivered declaration copies pass the same catalog projection check.

Full generation/freshness passes for 152 modules and 6543 offline-search entries.
Markdown normalization, source links/anchors, search-index JavaScript syntax and
the new examples were checked. The required wrapper was run with --dry-run before
--verify-offline. Offline Docs build71/content6.8.0 retains plugin version6.8.6;
the documentation counter advances89 -> 90. Its JVM2 and Python4 checks pass,
debug/release APK-content verification passes, and all208 site assets match after
the five existing text files' documented LF normalization. Provenance pins the
clean source commit2832a4d, not an unrelated host commit or a dirty working tree.

## P6.2 declarations and actual editor diagnostics

Declarations4.30.0 (commit43b29d7) adds Internal.Compose, the callable globals and
ComposeError, 29 factories and typed Node/State/Ref/Modifier/Theme/Session/Floaty
interfaces. The generated30/114 region is used in the actual factory signatures.
Normalized read types differ from accepted write types where required. The local
aj6dts -Publish flow used the explicit task-host root/appDebug, generated real
Java/resource/library declarations and mirrored them to Ace. The host/generator
input commits are d29eb47782 / 525bebdaa1. No npm or remote Git action occurred.

Both hand-maintained copies and their catalog regions match. Compose declaration
SHA-256: b71832f8b4a62a386934555c2aa3f8dadc118b36bad50b7553b804ed98ac5d6a.
TypeScript5.1.3 strict/exactOptionalPropertyTypes passes39 negative and full API
positive examples, plus2 separate exact-nullish cases. The new declaration itself
has zero semantic diagnostics without skipping its checking in the real graph.

Actual Ace TypeScript6.0.3 and the real generated core/browser language service
pass default and fully enabled group checks. A direct reference to an unloaded
native type originally degraded to error-any and admitted primitive image/color
inputs. The final boxed public-member mapping keeps these branches object-shaped,
rejects that misuse and preserves full Bitmap/ImageWrapper/ThemeColor assignability
when native groups are enabled. The existing default group policy is unchanged.
Complete native members still require optional groups; distinguishing explicit
duration:undefined statically requires exactOptionalPropertyTypes.

Ace1.22.0/build124 incorporates this into its regular verify-runtime.mjs alongside
18 existing verification groups. The generated-core task, actual language-service
regression, JVM171/32 classes, debug APK and native16 KB alignment all pass.
The pre-existing untracked releases directory is not modified or committed.

## P6.3 README and final integration history

Ten-language plugin README/instructions/changelog now describe the delivered V1
features and matching local builds, including two API styles, both hosting surfaces,
packaged-app plugin requirements, literal selector tags and real rotation/destruction
semantics. The quick HUD closes its own worker and ignores late posted updates;
the five already tested bundled example assets are unchanged. Twenty localized
README JS blocks passed syntax checks, and all36 generated documents match sources.

The host's ten-language Compose entries are consolidated without rewriting unrelated
history or refreshing online metadata. The protocol status is frozen V1 with host
commit ec34abc00f. Both protocol copies remain identical. Shared AAR/lock values,
native libraries, component catalog and renderer production code do not change.
Local-preview/official-release distinctions and the placeholder artwork status are
retained; no claim of remote deployment is added.

## P6.4 completed local gate, 2026-10-04

The final plugin is1.0.0/build26. It was built with Gradle9.5.0, AGP9.3.2,
Kotlin2.4.0 and the existing Compose BOM2026.09.00. The dedicated host remains
6.8.0/build5317. Local build-number/time auto-increment was disabled.

| Check | Result |
| --- | --- |
| Plugin Markdown/icons/examples | 36 generated documents and two icons match; five scripts/manifest match the host |
| Plugin JVM | 63/63, zero failure/error/skip |
| Plugin assembly/lint | Debug + androidTest + release; debug lint has no issues |
| connectedDebugAndroidTest | 32/32 on the owned API35 x86_64 AVD, zero failure/error/skip |
| Signed release collection | One Compose release; signature, CRC32, DEX ownership and four native helper alignments pass |
| Host selected JVM / frozen API | 200/200 and16/16, zero failure/error/skip |
| Host assembly | App debug + androidTest; native alignment passes |
| Final release through real host | Five actual examples + resource/JNI + standalone INFO:7/7 in18.673 s |
| Documentation / Offline Docs | 152 modules fresh;208 matching assets with documented LF normalization; JVM2/Python4; debug/release payloads and signed release collection pass |
| Declarations / Ace | Catalog30/114; TS39+2 cases; actual default/full-group language-service regression; JVM171 and debug/alignment pass |

The device task used ANDROID_SERIAL=emulator-5584 with --no-daemon. The actual AGP
ConnectedDeviceProvider implementation was inspected to confirm serial filtering;
its result XML names only compose_p6_api35 and32 tests. Other connected devices
were not used. The first JVM gate caught two non-ASCII punctuation characters in
roadmap evidence; they were corrected before the complete gate passed. No assertion
was weakened or failed test omitted.

Final artifact:

- releases/autojs6-plugin-compose-ui-v1.0.0-d9cdec3c.apk
- 2378609 bytes; CRC32 d9cdec3c.
- SHA-256 c2d7ddae7d8311b4b82c991b7fe6f1c92547b4b1df7dfd018f1f43b0be7be8cc.
- One DEX,7143 class definitions and44721 method references.
- Approved review budgets2612576 bytes /49194 references are not exceeded. Size
  increases3540 bytes (about0.15%) from the measured P5 artifact; references are
  unchanged. No new timing/PSS observations are claimed for this artifact.

The Offline Docs signed companion is
autojs6-plugin-offline-docs-v6.8.6-universal-5923dfdb.apk (4003313 bytes). Its208
content files total12399989 bytes, canonical content digest
8d1e785c3ddf186118ccd5de252b1b7140e2f6962be8ed17ea6ed19d058b7bfd.
Older Offline Docs releases were retained. Ace's pre-existing untracked releases
directory was not changed; only its debug output was built.

## Commits, cleanup and limits

Plugin decisions/P6.1/P6.2/P6.3:27be441,27a397a,63b22f0,5cb77ef. The build26 closing
commit contains this final gate record. Host decisions/history:d29eb47782 and
417f469975; the host gate copy is committed separately. Documentation:2832a4d
(complete source/site),2de38ab (counter90). Offline Docs:396f191/build71.
Declarations:43b29d7/version4.30.0. Ace:07a4831/version1.22.0/build124.

The test task cleaned its own plugin instrumentation installation. Subsequent
owned host/test/release-plugin packages were uninstalled and absence checked.
The AVD name, serial and resolved data path were verified before SDK deletion;
compose_p6_api35's data and registration are absent. Physical devices, other AVDs,
shared SDK images and the original host worktree were not changed by this task.

Not performed: full host JVM/lint, another full P5 six-device matrix, release-host
performance, new cold/PSS/scroll stability measurements, long-running heap/leak
certification, remote CI or public publication. Formal artwork remains pending;
the two required PNG variants pass resource checks with the retained placeholder
source. These limits are not converted into passing results.

P0-P6's specified local implementation and gate are complete. P7 is the approved
next sequence beginning with F.1 TSX. D7 still requires an explicit maintainer
change before Git pushes, official-index registration or public releases.
