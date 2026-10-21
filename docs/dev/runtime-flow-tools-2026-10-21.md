# Runtime Flow tools and event waits - 2026-10-21

Status: implementation, companion synchronization, local checks, physical host and actual packaged-app validation, and device restoration are complete locally. Source integration and temporary checkout cleanup are recorded separately in the final evidence. Nothing was pushed, tagged or published.

## Delivered contract

This batch supplies the remaining 30 registered Flow root names, reaching **52 of 52 registered root names** across Node main threads, CommonJS/ESM/nested Workers and Bun. This is a name count, not complete Rhino overload, prototype or scheduler parity.

- Advanced tools: smartClick/smartClickBounds, clickIfExists/clickBoundsIfExists, clickAny/clickBoundsAny, findAny, scrollUntil, typeInto, dismissPopups, collectList, launchAndWait, backUntil, backToApp and toggle.
- Wait/action compositions: waitThenClick/clickWait, waitThenClickBounds/clickBoundsWait, waitThenLongClick, waitForStableThenClick/clickWhenStable, waitForStableThenClickBounds/clickBoundsWhenStable, clickWhenStableAfter and clickBoundsWhenStableAfter.
- Real event waits: waitForIdle, waitForEvent, waitForToast and waitForNotification. EventFlow.ready resolves after native listener installation. Filters may be asynchronous and execute on the owning JS thread, not the Android service thread.
- Chains expose 25 node action names, 10 basic global actions and the clickAfter/clickBoundsAfter/longClickAfter helpers. Strict action failures, humanization, typed results and root/chain positional differences are synchronized.
- Native Toolkit algorithms are reused with operation-private UiNode adapters. NodeSnapshot remains data; a subsequent action relocates a unique matching target and rejects stale or ambiguous snapshots. Inputs and toggles return refreshed state. No live UiObject is retained by the JS facade.
- Native operations support asynchronous callbacks, reentry, original Error and primitive rejection values, cancellation before delayed start acknowledgement, UTF-8 result fragments and per-facade cleanup. Worker termination closes only its own native clients, including late open replies.
- Bun has an independent default-off flow.control grant. Accessibility, gesture, app, clipboard and event prerequisites are derived before effects. Only flow.control streaming is exempt from the ordinary 1024-call budget. Four transport calls, size/deadline limits and existing ordinary-call budgets remain. UI long polls leave capacity for triggering actions and callbacks. Native Bun payloads and the Android/ABI compatibility matrix are unchanged.

The full signatures, defaults and resource limits are in Documentation api/runtimeApis.md, section "Flow 异步链与等待核心". within/scope, synchronous Rhino sync/await terminals, implicit global aliases, arbitrary Java UiObject/prototype behavior and full Flow press/swipe/gesture/gestures overloads remain outside this portable contract. Flow popup watch:true is not supported, matching the Rhino Flow form's restriction. AI and other remaining audit differences remain separate work.

## Accepted source and artifacts

| Component | Version / Android build | Source commit |
| --- | --- | --- |
| Host / Runtime Kit | 6.8.0 / 5757 | `8717dc91604aec4f7d108088c37dc914cd293187` |
| Node.js Runtime | 1.29.0 / 285 | `f13f66290ee19ff9599058e899d7063a0ff63a2c` |
| Bun Runtime | 0.18.0 / 287 | `12ec437ef3663cd1b472634ae193f56d74217112` |
| Ace Editor tested APK | 2.38.0 / 319 | `6202885c89ea49fa150c5e5645a482d6f495dd5c` |
| Offline Docs | 6.8.37 / 199 | `7f007752df62a0873a157a94650c859636b3fe73` |
| APK Builder Template | 1.2.23+autojs6-6.8.0 / 575701 | `761afee64c0e97c145c0e3267a0199645168ed96` |
| Flow declarations | 4.76.0 | `73cc97542e6105bd61ccea091a83a51f02bbff5c` |
| Documentation snapshot | revision 158 | `2a2f1474295be3a9d21ce25850840c8555c59233` |

Runtime Kit:

```text
autojs6-runtime-v6.8.0+5757-8717dc91604aec4f7d108088c37dc914cd293187-arm64-v8a
```

The retained kit directory is `D:/idea-projects/AutoJs6-Plugin-APK-Builder-Template/.bak/runtime-flow-tools-20261021/kit-5757`. Builder repository build 104 pairs host 5757 and uses Android versionCode 575701. All generated app DEX hashes match this exact template, and their signing certificates differ from the host certificate.

The final device cohort uses the same production and instrumentation source. Earlier Redmi acceptance on build 5752 is retained separately; after Xiaomi exposed a coordinate issue, Redmi was backed up again in redmi-final and repeated with the final cohort. The stricter fixture establishes checkbox state and checks all 18 target clicks.

Concurrent memory work advanced Documentation and Offline Docs, and added TypeScript/Ace commits `9a139285b739b640436c3f03dae8d887031b6906` and `1f4e0590719719128ec5f690d210f9e05aec6b2d`. Those inherit the Flow declarations but are not this batch's tested Ace APK. Documentation revision 158 and Offline Docs 6.8.37/build 199 include those separately owned memory documentation changes. Their authorship is not attributed to Flow. Uncommitted continuation/augmentation work in the primary repositories is preserved; rebuilding the mutable primary workspace does not reproduce the accepted kit automatically.

## Corrections found during validation

Every failed attempt is retained and excluded from accepted counts.

1. Static capability catalogue construction calls permission resolvers without concrete arguments. Flow initially parsed a start payload unconditionally, failing bridge initialization. Optional catalogue parsing and a catalogue regression now cover this path without weakening actual dispatch validation.
2. A root null result was serialized twice; JSONObject.NULL was rejected by the second pass. The wire encoder now preserves already encoded null and has a focused regression.
3. Bun UI waits blocked its oneway Binder entry, delaying Flow verification callbacks. Dispatch now leaves the Binder entry and runs under the existing four-call concurrency contract. The SDK also reserves control/callback capacity beside UI event readers. The blocking behavior follows Android's documented [serialization of asynchronous Binder transactions](https://source.android.com/docs/core/architecture/ipc/binder-threading#serialization-of-asynchronous-transactions). Native executor and SDK scheduling regressions exercise blocked readers, callback progress and shutdown.
4. A late foreign FrameLayout event from a disappearing Toast window overwrote a correct foreground transition after Back. Portable identity now rejects unresolved foreign non-Activity events while retaining valid Activity and focused-window updates.
5. An actual packaged first launch bound its service after the earlier Activity event and received no replay. Foreground queries now also read the current application window and the app's own focused Activity, without Root/Shizuku or extra package visibility access.
6. Xiaomi exposed stale geometry during window entrance. Coordinate actions now reuse the standard host tap and refresh node geometry; snapshot relocation refreshes and revalidates its match, and stability probes refresh/recheck their matched node. The owned fixture waits for 500 ms of actual stability before coordinate assertions; ordinary tools do not add this implicit delay. The full suite retains exact click delivery assertions.
7. Test fixture corrections explicitly mark the clickable ancestor, control checkbox state, hold the external Toast Activity until one Back action, and release UI handles/channels even after a failure. These establish the intended preconditions; the resulting action, event, identity and restart assertions remain checked.

One host JVM attempt was invalidated by archiving its obsolete differential fixtures before the task finished. Its 16 missing-fixture failures are retained and excluded. The full suite was rerun with all 45 hash-checked fixtures present, and they were archived only after confirmed completion before APK/kit builds. One Redmi screenshot captured the editor before its popup was painted; that visual attempt is retained separately, and the unchanged Ace APK was rerun to obtain and inspect all eight replacement screenshots.

## Local checks

- Node: 129 modern tests and seven independent legacy programs pass. Bun: 79 SDK tests pass. Coverage includes callback reentry, Error objects and primitive rejection values, cancellation, delayed open/start races, Unicode fragments, listener readiness and CJS/ESM/nested Worker cleanup.
- Host JVM: 5081 cases, 5055 passed and 26 skipped. Node/Bun/Builder JVM suites pass 96/60/40 cases respectively. Ace/Offline JVM, lint and signed release collection also pass; their build logs are retained.
- Node C++ syntax was checked with the actual configured NDK command. Plugin sample/type smoke checks, debug and instrumentation assembly, plugin lint, signed local release collection and native alignment/integrity checks pass.
- The official Documentation and declaration generation workflows ran. A total of 78 runtime declaration files were synchronized, including the Flow contract. TypeScript 5.1.3 and 6.0.3 report no diagnostics; Ace performs 1018 runtime queries. A read-only check after the foreign memory declaration commit still passes.
- The Flow gate checks all 52 roots and 25 action names. The host augment gate checks 1356 explicit exports and 1589 paths without failure. This is documentation/type/completion coverage, not full implemented Node/Bun coverage.
- The host's pre-existing Chinese/other-language changelog count mismatch still blocks the global translation-count check. This batch's ten-language entries and generated output rendering were separately verified. Other sessions' Chinese release drafts were not translated or rewritten.

## Physical and packaged validation

| Device | Environment | Accepted physical cases | Host program checks (Node/Bun/Worker) | Actual packaged runs | Restored baseline |
| --- | --- | --- | --- | --- | --- |
| Redmi `bek749scrwv4wo8h` | API 33, arm64, 4096-byte pages | 56 | 47/47/47 | 4 launches / 6 results | 6 original APKs |
| Xiaomi `968e9f18` | API 35, arm64, 4096-byte pages | 56 | 47/47/47 | 4 launches / 6 results | 8 original APKs |

Each device runs the six host tests, 34 Node npm/conformance cases, 13 Bun instrumented cases, the Ace popup case, actual Builder production and packaged relaunch validation. Each packaged mode runs once fresh and once after force-stop. Node packaged main/Worker and Bun produce six program results per device. All are independently signed apps using their own accessibility service, package identity and declared capabilities.

Real operations include node/ancestor/bounds clicking, input and verification, changed/unchanged toggles, stale snapshot refusal, popup dismissal, scrolling until visible, large Unicode collection with asynchronous callbacks, current-app launch/back behavior, real accessibility click events, idle waits, silent notifications, external Toasts, cancellation and callback failure identity. CJS/ESM/nested Worker termination preserves a parent listener and releases owned native subscriptions. Eight Ace screenshots per device were visually reviewed for smartClick, ready, toggle and collection result types.

## Restoration and evidence

Both devices were backed up again immediately before this batch. Xiaomi's current original host was build 5741 with SHA-256 `09fa32a58a1259deec7bee17e56b198171465a2a46786fc9db425d12c0b4883c`; older speech/core backups were not used to overwrite that state. APK hashes, runtime permission flags, accessibility configuration, system settings, volumes and app-ops were checked after restoration. Originally enabled host accessibility was rebound. Temporary installations/generated applications and owned fixture files were removed; original Ace evidence and Bun/plugin grants were restored. Original APK backups are retained in this batch's evidence.

Evidence root: `D:/idea-projects/AutoJs6/.bak/runtime-flow-tools-20261021`.

- `artifact-map.json`, `runtime-kit.json`, `runtime-kit-location.json`: immutable component artifacts and accepted kit identity.
- `redmi-final/accepted-evidence.json`, `xiaomi/accepted-evidence.json`: accepted tests, program results, signatures and DEX checks.
- Per-device `state.json`, `owned-file-cleanup.json`, `restoration.json`, `finalization.json`: fresh baseline and verified restoration.
- Per-device `ace-visual-review.json`: all reviewed screenshot hashes.
- `local-checks-final.json`, `junit-final`, build/test logs and coverage outputs: local checks and excluded failures.
- `final-commits.json`, `integration.json`, `cleanup.json`: explicitly owned commits, preservation of parallel work, and temporary-workspace cleanup.

Device helpers are historical evidence, not a request to rerun restored devices. Their old paths/state assumptions must be reviewed before reuse. No new hardware 16-KiB or additional ABI execution is claimed by local alignment checks.
