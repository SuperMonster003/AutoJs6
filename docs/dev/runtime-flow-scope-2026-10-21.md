# Runtime Flow scopes and gesture overloads - 2026-10-21

Implementation, documentation/declaration/completion synchronization, physical and actual packaged validation, and restoration are complete locally. Integration and temporary-workspace cleanup are recorded separately in the final evidence. No push, tag or publication was performed.

## Delivered behavior

- Six Flow instance methods are supplied in Node main threads, CommonJS/ESM/nested Workers and Bun: within, scope, press, swipe, gesture and gestures. Registered root-name coverage stays at 52/52; these are prototype methods, not six new registered roots.
- within waits for a node snapshot and installs it as the root for later selector waits and tools. scope preserves the incoming value and merges non-null root/compass/resultType fields. Scope is inherited after parent settlement, captured against later caller mutation, and isolated between sibling branches. Per-wait overrides do not leak to following steps. Predicate and Promise conditions retain their own value rules.
- Scoped selectors reuse the core compass parser and result alias table. Node/content/point/collection/existence/selector forms and documented read-only property/navigation projections return JSON values. Collection forms are arrays; array node actions operate on all targets. Empty selector projections keep waiting, while numeric zero is found. content follows the host's description-first rule.
- Scope roots are relocated on each read. Layout motion can refresh a search root's geometry, while window/package/class/ID/text/description/depth/index identity must remain unique and matching. Action snapshots retain strict geometry validation. Identity changes, ambiguous matches and changed windows fail instead of silently searching globally.
- Gestures support the documented press defaults and duration-first/last forms, seven swipe forms, variadic/grouped/flat gesture paths, and multiple strokes with optional start delays. Global gestures preserve chain input and support callbacks, strict failure behavior, deadlines and cancellation. Coordinates are physical pixels; Android validates paths, stroke count and duration. Cancellation cannot retract a gesture already accepted by Android.
- Scope reads retain the ordinary accessibility query permission. Gestures require accessibility.gesture, and Bun additionally requires its existing default-off flow.control grant. Node's existing 20-query/s accessibility budget and Bun's ordinary transport limits remain. Native payloads and ABI/Android compatibility matrices were not expanded.
- Flow<T, S> declarations carry current value and default projection type separately. Literal projections, root/chain polling helpers, direct snapshots, gesture callbacks and readable concrete gesture signatures are synchronized across 78 runtime declaration files and Ace.

Signatures, examples, defaults and limits are documented in Documentation api/runtimeApis.md, under Flow. Synchronous Rhino sync/await, implicit global aliases, arbitrary Java UiObject/prototype behavior and arbitrary reflective resultType invocations remain outside the portable contract. AI and other audit gaps remain separate work.

## Source and artifacts

| Component | Version / Android build | Source |
| --- | --- | --- |
| Host / Runtime Kit | 6.8.0 / 5788 | `b2eccefd8d3545485139f0cb91bc810261759b1e` |
| Instrumentation fixture | paired host 5788 | `64cb9782e8841ea534694bdcb29f616bf6a60dc9` |
| Node.js Runtime APK | 1.30.0 / 286 | `8f4b5cffdb218a863ad6cab85894ee177acd1a60` |
| Bun Runtime final APK | 0.19.0 / 288 | `3438ee3052e4ae5face554b02b8fec1ae4aa890a` |
| Ace Editor final APK | 2.40.0 / 321 | `ebb893b44a1ed92582df10229e0cb4dd5e0916e2` |
| Offline Docs | 6.8.38 / 198 | `ace66e7f7c3780cb3577fb84b955a8d4bb708670` |
| APK Builder Template | 1.2.24+autojs6-6.8.0 / 578801 | `f4aba9ccc586275e2c831f78da71ea68b42f78e6` |
| TypeScript Declarations | 4.78.0 | `c645705b0b3352d0879bf6e81253c68a802e588a` |
| Documentation | revision 161 | `763172aecf64a250aea07915965124c5d249d168` |

The production host and kit are source b2eccefd8d3545485139f0cb91bc810261759b1e. The later 64cb9782e8841ea534694bdcb29f616bf6a60dc9 fixture commit changes only Android tests. Main APK bytes are unchanged by that fixture update. Node's later declaration commits likewise leave its tested APK bytes unchanged.

Runtime Kit: `autojs6-runtime-v6.8.0+5788-b2eccefd8d3545485139f0cb91bc810261759b1e-arm64-v8a`.

Retained directory: `D:/idea-projects/AutoJs6-Plugin-APK-Builder-Template/.bak/runtime-flow-scope-20261021/kit-5788`.

Builder repository build 106 uses Android code 578801 and pairs host 5788 exactly. Independently signed generated applications have different certificates from the host and DEX hashes equal to the retained template.

### Declaration-only follow-up cohorts

After Redmi was restored, the final type review added root whenPresent/repeatUntil projection inference and direct snapshot condition signatures. Later Bun/Ace APKs were exercised on Xiaomi. ZIP-entry comparison proves that only declaration assets changed: Bun JavaScript/DEX/native bytes and Ace JavaScript/DEX are unchanged. Redmi's exact tested cohort remains independently recorded; its results are not relabeled with a different APK hash.

| Component | Redmi APK SHA-256 | Xiaomi final APK SHA-256 |
| --- | --- | --- |
| bun | `30b337d0efc07670331ff31b0e9892910ea644690dbeb78cf3d3954508f2dfc8` | `58da949a2c2be22e30a05b88fc6a85b77ea6f3568577f232fe75472d7669e783` |
| ace | `b8125c1666b96f678fa184ca3d9b97dc7b97dda17da89777b5b179951aeac496` | `0440a94c984ded7e33656564e5b5568e18061270a38c3190305a2479d7e7bc25` |

The per-device accepted-component-artifacts.json and type-only-apk-followup.json preserve the distinction. Mutable primary workspaces contain other sessions' later work and must not be treated as the accepted kit automatically.

## Validation and corrections

Both Redmi/API 33 and Xiaomi/API 35 pass 56 physical cases: six host cases, 34 Node npm/conformance cases, 13 Bun cases, one Ace popup case, actual Builder production and packaged relaunch validation. Each host mode (Node/Bun/Worker) completes 129 checks, including 82 scope/gesture checks. Each packaged program completes 128 checks. Each device produces four actual launches and six program results; total acceptance is 112 physical cases, eight launches and twelve program results.

Sixteen gesture call forms are checked against actual touchstart/touchmove/touchend events in an owned WebView, including simultaneous pointers and a delayed second stroke. Scope tests cover duplicate text inside/outside the root, overrides, parent/child/sibling compass navigation, scalar/array projections, array actions, stale identity refusal, cancellation and callback propagation. CJS/ESM/nested Workers perform native scope queries and an actual gesture, then preserve an independent parent listener and release owned resources on termination.

Eight final Ace screenshots per device were visually inspected for within, concrete press signatures, string projection methods and point coordinates. The snippets intentionally stop at an incomplete expression to display completion; syntax markers in those screenshots are not compilation failures in a complete program.

All failed attempts are excluded from accepted totals and their logs are retained:

1. Window entrance changed a root's bounds after within, exposing over-strict root relocation. The production fix permits geometry refresh only for lookup roots; new identity regressions retain strict action bounds and reject identity/window changes.
2. The first fixture expected text for content, although the core picker is description-first. Expectations now match the existing core semantics. Query pacing observes Node's existing 20/s budget without changing it.
3. Starting instrumentation while its target process owned a bound accessibility service delayed the first binding. The driver now removes only the host entry before host instrumentation and restores the exact prior setting afterwards; operational service assertions remain required.
4. WebView's virtual accessibility tree did not expose its assigned description. The fixture uses an explicit outer Frame for touch coordinates. Worker preservation listeners now filter an untouched sentinel, since the newly added actual click correctly completed an unfiltered listener.
5. An intersection-based gesture declaration produced an internal GestureOverload popup. Concrete method overloads now expose readable signatures and contextually typed callbacks. Root projected polling and direct snapshot signatures were completed during final type review.

## Local checks and limits

- Node: 134 modern tests plus seven independent legacy programs pass. Bun: 81 SDK tests pass. Actual NDK syntax checking, example/type smoke checks and package integrity/alignment checks pass.
- Host JVM: 5126 cases, 5100 passed and 26 skipped. Node/Bun/Ace/Builder/Offline JVM cases pass 96/60/187/40/2 respectively. The complete final host XML set is retained. All 45 obsolete differential oracle files were archived after tests completed and before APK/kit builds.
- Official Documentation and declaration publishing workflows ran. TypeScript 5.1.3 and 6.0.3 report no diagnostics for 78 runtime declaration assets. Ace runtime coverage checks 1051 queries. Appropriate debug/instrumentation, lint and signed release collection checks completed.
- The Flow gate covers 52 registered roots, 25 node actions and the six new scope/gesture instance names. The general augment scan covers 1361 explicit exports and 1596 paths, but retains seven pre-existing static fallback gaps for memory/$memory and five memory members. Their declarations and LSP routes pass; the static assets are byte-identical to the prior Ace memory commit. See preexisting-augment-boundary.json. This is not a claim that the entire augment gate or all Rhino runtime parity is complete.
- Shared Kotlin compilation exhausted its heap and repeatedly collected garbage. The shared process was preserved; only owned build clients/daemons were stopped. An isolated kit build later completed. After the user reported severe CPU contention, subsequent builds were serialized with in-process Kotlin, a two-processor JVM limit, below-normal priority and one-core affinity where applicable. Owned idle daemons were closed. Windows APK handle conflicts were resolved only by stopping verified owned idle collectors, never with global Gradle or ADB shutdown.

## Device coordination, restoration and evidence

Redmi's six original APKs, including host 5352, are restored and SHA-256 verified; its originally enabled host accessibility service is rebound. Xiaomi was not modified while the other memory P4 fleet was active. Its original snapshot in xiaomi/ became historical when another session installed host 5781. After the full fleet and cold follow-ups finished, xiaomi-final/ captured the new baseline; all eight original APKs and that baseline's permissions/settings were restored. Xiaomi host SHA-256 is aa0c57beb0c5448c9e43309a3102c8e12894c0e8c619cee35770178353a0fcaa. The earlier host 5741 backup was not used to overwrite the takeover state.

Generated applications, test-only installations and owned fixture files are removed. Permission flags, settings, volumes, app-ops, original Ace evidence, Bun grants and plugin selections are verified. Original APK backups remain retained. Device helpers are historical evidence; do not rerun them on restored devices without reviewing their state assumptions.

Evidence root: D:/idea-projects/AutoJs6/.bak/runtime-flow-scope-20261021.

- artifact-map.json, per-device accepted-component-artifacts.json and runtime-kit.json bind immutable APKs, certificates and source identities.
- redmi/accepted-evidence.json and xiaomi-final/accepted-evidence.json contain accepted results and signatures/DEX checks.
- Per-device state.json, owned-file-cleanup.json, restoration.json, finalization.json and ace-visual-review.json record fresh baselines, cleanup and restoration.
- local-checks-final.json, junit-final/, build/test logs, external-p4-fleet-completed.json and type-only-apk-followup.json preserve verification boundaries.
- final-commits.json, integration.json and cleanup.json record scoped commits, preservation of parallel drafts and temporary checkout/signing-material cleanup.

No additional ABI or hardware 16-KiB execution is claimed by local alignment checks.
