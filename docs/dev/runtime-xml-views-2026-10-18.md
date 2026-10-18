# Common XML widgets and native View properties - 2026-10-18

This development batch extends the portable UI contract used by Node main,
CommonJS/ESM/nested Workers and Bun. The implementations, declarations, online
and offline documentation, Ace completion and packaged Runtime Kit are aligned.
No release, registry update or remote push was performed.

## Delivered behavior

- Twelve additional native widget factories: HorizontalScrollView, Space,
  RelativeLayout, CardView, Spinner, SeekBar, RatingBar, RadioGroup, RadioButton,
  ToggleButton, CheckedTextView and NumberPicker. XML aliases include hscroll,
  relative, card, spinner, seekbar, ratingbar, radiogroup, radio, toggle,
  checkedtext and numberpicker; img and linear alias existing components.
- The native renderer preserves child width/height, margins, weight and layout
  gravity. Relative rules refer to unique sibling XML IDs and accept layout_
  aliases. Duplicate IDs, invalid anchors and invalid property/widget combinations
  fail rather than being converted into successful no-ops.
- Forty-six writable View property accessors and six read-only accessors share
  the same Node/Bun implementation. Reads return Promises. Assignments are queued
  in order per local wrapper, snapshot array/object values, and expose deferred
  failures through a read or flush(). A reported failure permits later recovery;
  dispose still attempts native cleanup after a queued assignment fails.
- Handles retain plain, serializable identities. Accessors are non-enumerable,
  and mutating a caller's original token cannot retarget a wrapped handle. Worker
  ownership, event-reader coordination and execution cleanup remain explicit.
- ImageView.src accepts execution-owned image handles or drawable resources and
  retains copied pixels after the original image is recycled. Non-image objects
  cannot be used to open host file paths through the UI capability.
- Spinner, SeekBar, RatingBar, RadioGroup, ToggleButton, CheckedTextView and
  NumberPicker expose native state/events. ListView events use the current items
  after an items assignment, rather than the constructor's obsolete array.
- Ace provides getter/setter Promise signatures and XML tag/attribute completion
  in typed runtime ui.inflate literal strings and non-interpolated templates.
  Unrelated methods named inflate do not enable these suggestions.

## Coordinated development versions

| Repository | Version |
| --- | --- |
| AutoJs6 | 6.8.0, build 3911, Android versionCode 5563 |
| Node.js Runtime | 1.9.0, build 257 |
| Bun Runtime | 0.2.8, build 255 |
| TypeScript Declarations | 4.54.0 |
| Ace Editor | 2.16.0, build 291 |
| Documentation | 6.8.0, revision 131 |
| Offline Docs | 6.8.11, build 162 |
| APK Builder | 1.2.1, build 76, paired host 5563 |

## Physical acceptance

Both devices are physical ARM64 devices with 4096-byte pages. Each of the six
main XML tests contains the relevant runtime-mode loops; image ownership is a
separate test. Repeat runs are not added to these counts.

| Device | Android | XML/properties + image | Earlier interop | UI/Compose | Legacy UI | Node | Bun | Ace | APK production + execution |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | 13 / API 33 | 6 + 1 | 4 | 7 | 9 | 44 | 13 | - | 2 + 2 |
| Xiaomi 23046RP50C, 968e9f18 | 15 / API 35 | 6 + 1 | 4 | 7 | 9 | 44 | 13 | 5 | 2 + 2 |

The total is 181 accepted JUnit executions across the two devices. The tests
check actual native classes, ordered reads/writes, recovery/disposal, native
selection and accessibility progress actions, relative geometry and weight,
rotation, and the pixels of the ImageView's surviving bitmap copy.

The production APK Builder generates real independently signed Node and Bun
applications from the genuine 6.8.0+5563 Runtime Kit. Application versionCode 1
and runtimeVersionCode 5563 are verified from each application's own context.
Binary Manifest permissions and certificates are checked. Card/Spinner/SeekBar,
property assignments, AndroidView, Compose callbacks, rotation and window
closure pass in both generated applications on both devices. Bun loads the real
SDK through its encrypted project dependency. No synthetic APK substitutes for
the production builder.

## Verification and failures resolved

- Host Debug/test APKs and the full Inrt Runtime Kit matrix build successfully.
  Host JVM results: 4717 collected, 4691 passed, 26 existing skips, zero failures.
- Plugin JVM suites: Node 96, Bun 59, Ace 187, Builder 39, Offline Docs 2 pass.
- Node's current NDK syntax check, examples/declarations/TypeScript smoke tasks
  and actual desktop CJS/ESM/Worker tests pass. Bun client tests: 26 passed.
  The frozen official Bun native payloads are unchanged and integrity checks pass.
- TypeScript 5.1.3 and 6.0.3 validate all 70 copied runtime declaration files with
  zero diagnostics. Ace's runtime gate passes 337 queries across 11 routes and
  its real default-setting popup suite passes, including JSOX regression checks.
- The new native-view gate compares all 46 writable properties against handlers,
  getters, JS accessors and declarations, plus the 12 native factories and plain
  serialized identities. Existing checks cover 55 Compose constructors, 12
  Compose operations, seven View operations and 12 Worker modules.
- The broader host gate reports 91 registration routes, 1351 explicit exports
  and 1522 completion queries with zero failures. These are documentation/type/
  completion checks, not a claim of complete Rhino implementation in Node/Bun.
- Documentation generation checks 163 modules. Coverage Python tests (8), Bun
  generators (38), Offline Docs generators (3) and ten-language generation pass.

The initial physical run found missing normalization of layout_below and the
ToggleButton theme's all-caps transformation. Both are fixed with the original
geometry/text assertions retained. Inspection also found stale ListView callback
data after changing items; the current array is now used and all runtime modes
verify the updated text/value. Native property signatures originally omitted
getter/setter completion details; the editor now displays their Promise types.

Two test-fixture issues were corrected without weakening runtime protections:
the image fixture first chose a nonexistent harness field instead of the actual
runtime import, and the old interop rotation helper queried an accessibility
node separately from acting on it. The latter could abort the running Bun call
and correctly poison its uncertain Binder lane for that process. The fixture
now waits for an accepted action on the current node, keeps exact callback/state
assertions and passes in a fresh test process. Failed logs remain archived;
these attempts are not counted as accepted tests.

## Pause, restoration and evidence

At the user's pause request, testing stopped and both devices were restored and
hash-checked. On resume, the eight workspaces matched the saved source hashes.
A fresh Redmi baseline was taken before the remaining packaged-app tests; the
device was restored to that baseline afterwards. Original APK SHA-256 values,
microphone permission flags, volume, rotation and projection release were
verified. Existing app data and the five original host draft changes are retained.

Ignored .bak/xml-view-20261018 contains the logs, pause/resume snapshots,
evidence.json, certificate comparisons, actual tested APKs and their hash
manifest, native result JSON, Ace screenshots and restoration receipts. Source
hashes bind the pre-pause code to the resumed work. Original APK backup copies
are removed only after successful restoration. The existing .bak/oi-check
checkout and its ignored signing configuration are preserved.

## Remaining boundaries

This is a finite widget/property contract. Arbitrary Java object/property
reflection, WebView, Drawer/Toolbar/ViewPager and the full Rhino XML/expression
binding surface remain outside this batch. Dimensions write dp or explicit px;
native geometry/inset/card reads use pixels, while textSize reads sp. flush()
confirms property submission, not completion of Android's next layout/draw.
Widget-specific values and bridge budgets remain documented in runtimeApis.
Other ABIs/OEMs, physical 16 KB execution and release publication are not claimed.
