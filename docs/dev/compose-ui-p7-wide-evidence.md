# P7 F.4 host integration evidence

This records the F.4 contract V2 and wide-component host integration on the
`spike/compose-ui-p0` worktree. The original host working tree was not used as an
input or changed. The implementation starts from host `8cba2ce0e3` (build 5321);
the integrated host is AutoJs6 6.8.0 / build 5322. Final commit and artifact
identities are recorded when the complete stage gate finishes.

## Implementation

- Contract version 2 adds 25 nodes across the 19 planned component families. The
  base union has 55 entries, including the retained command-only Snackbar, and
  176 properties. Negotiated AndroidView makes 55 callable node factories.
- V1 and optional AndroidView/dialog interfaces remain supported. The loader
  selects the component vocabulary by the provider's actual contract version;
  metadata rejects an insufficient host before loading renderer classes.
- The same V2 validator runs before host graph publication, core staging and
  renderer commit. It enforces scoped item parents, primary-child ownership,
  selection uniqueness, dependent indices, date/time ranges and grid dimensions.
- V2 callbacks retain node receivers and the existing script scheduler. Optional
  date payloads become explicit null, and malformed scalar payloads are rejected.
- Native grid/pager scrolling reuses the existing bounded command path. Catalog
  factories, string/namespaced TSX factories and handle properties use the V2
  union without changing the classic TSX ABI.
- Eleven native interactive controls retain focus/blur parity with V1 buttons.
  Floating Drawer and SearchBar compositions receive an independent public Back
  dispatcher owner and scoped legacy/API 33+ event routing; closing or detaching
  removes the registration without replacing borrowed native View listeners.
- A host getter bug exposed by DatePicker was fixed: explicit/null-default
  UiValue.Null now remains script null; only absent values return undefined.
  Existing V1 progress defaults and explicit color null values are covered too.
  The correction is included in all ten host changelogs and generated histories.

Protocol details, including the 1583..9999 date range and the frozen 2000-child
wire limit for new lazy containers, are in [compose-ui-contract-v2.md](compose-ui-contract-v2.md).

## Contract artifact

Release AAR SHA-256:
`3ebe1f887b7e3a9a98277bd0436e83e8e808b380cbf050d37da42975ea125351`.
The prior F.3 AAR was
`e3a4f2003bb0336338e229cfc9220c2296a5de462957fd540a20c37d86996fe8`.

Of 209 prior classes, 208 are byte-identical and none were removed. The only
changed class is ComposeUiContract, whose version is now 2 and whose supported
range is 1 through 2. Six new ComposeUiV2 vocabulary/catalog classes bring the
total to 215. Original models, parcel codec, loading interfaces, V1 catalog,
AndroidView interfaces and dialog interfaces are byte-identical.

The shared dependency lock remains the same 51 artifacts. V1 and AndroidView
catalog exports retain contractVersion 1; the separate V2 projection supplies
declaration/documentation parity checks. The reviewed original V1 snapshot is
unchanged and its test permits only the specific version-literal transition.

## Completed host and compatibility checks

| Check | Observed result |
| --- | --- |
| Shared API JVM | 29 tests, zero failures |
| Selected host JVM | 224 tests, zero failures and skips |
| Host debug and androidTest assembly | Passed |
| Actual host 5321 with signed V2 candidate | 1 test passed: minimum host 5322 causes PLUGIN_INCOMPATIBLE before loading |
| Host 5322 with archived actual V1 renderer 31 | 6 tests passed: new nodes reject with UNKNOWN_COMPONENT while the retained callback and all five original examples work |
| API 35 first signed V2 candidate, public script scenarios | 5 tests passed in 13.582 s |
| API 35 final focused overlay Drawer/SearchBar Back case | 1 test passed in 4.987 s after the owner-focus and controlled Drawer fixes |
| Final signed renderer, API 24 x86 host matrix | 27 pass: seven F.4 cases plus the existing 20 F.2/F.3/floaty regressions |
| Final signed renderer, API 35 x86_64 host matrix | The same 27 pass |
| Actual V2 TSX with installed local Engine 0.6.7 | The new mixed dialog/AndroidView/navigation/pager/chip case passes |

The first API 35 scenarios verify controlled navigation/tab/chip/segment changes,
seven invalid updates preserving the tree and callbacks, DropdownMenu/Tooltip and
ModalBottomSheet on an Activity and a real overlay, and a sheet nested inside an
overlay dialog. The sheet assertions inspect a borrowed native view's actual root
window type. These measurements describe the first candidate; subsequent renderer
edge-case corrections and the additional nested-popup case require the final gate.

Ignored local evidence is under `app/build/compose-f4`: `api-gate.log`,
`host-gate.log`, `host-wide-api35-first.log`, `old-host-v2-gate.log` and
`new-host-old-v1.log`. Archived original host APKs, renderer 31 and the F.3 AAR
are under `app/build/compose-f4/baseline`.

The Back integration needed an actual native focus chain, not just an attached
View. Initial diagnostics showed a lifecycle-active handler with no focused View
and no delivered key events. The final owned wrapper focuses itself as a fallback,
preserves an existing focused descendant, and rechecks after layout/window focus.
The API 35 trace then recorded real Back DOWN/UP reaching its dispatcher. The
host's predictive-Back manifest policy stayed unchanged; Android's compatibility
key route handled this device. Temporary diagnostic logging was removed.

The same fixture immediately expands SearchBar after the script accepts drawer
closure. This exposed a separate Material DrawerState confirmation during old
animation cancellation; the renderer now distinguishes accepted-model convergence
from a user change request. The fixture retains that immediate transition and
does not wait for the drawer animation to hide the defect. Its final focused
result is in `app/build/compose-f4/host-back-drawer-final-api35.log`.

## Final host gate

The final host matrix ran the signed renderer
`autojs6-plugin-compose-ui-v1.1.0-d4f266d2.apk` on both owned AVDs. All 27 cases
passed on each device: seven F.4 cases, six F.2 interop cases, six F.3 dialog
cases and eight existing floaty cases. API 24 took 67.494 s, API 35 took 71.770 s;
these are suite durations, not performance benchmarks. Logs are
`app/build/compose-f4/host-final-api24.log` and `host-final-api35.log`.
Renderer SHA-256 is
`957ae7e988d18d5116c39e23986ae442f4813528f3e9dc9c6794977e9a05da35`.

The final cases include sheet-inside-overlay-popup window inheritance, real Back
through the owned floating Drawer/SearchBar dispatcher, immediate content changes
while the drawer closes, invalid input rollback, real input/IME and old XML
containers. A transient accessibility snapshot after a state-label update required
fresh visible/enabled/clickable-node lookup before ACTION_CLICK. The fixture uses
the same 25 s observation budget and returns after the first accepted native
click; subsequent callback and state assertions remain unchanged.

The new actual TSX fixture also passes with the installed local TypeScript Engine
0.6.7 debug candidate. Final committed-source generator/Ace/Engine packaging and
the complete TSX regression are coordinated in the Compose UI plugin's
`docs/dev/p7-wide-evidence.md`, together with Q5 acceptance and owned-device cleanup.
The original host checkout and its unrelated pending work were not edited.

No new full-host JVM/lint run, full six-device matrix, packaged-app V2 run,
comparable timing/PSS/scroll measurement or remote CI run is claimed here. Original
performance observations and review thresholds are retained. Publishing and remote
repository operations are outside this local integration evidence.
