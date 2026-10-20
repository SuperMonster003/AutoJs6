# Runtime EPUB books and reader sessions

The EPUB batch supplies all 20 registered root method names, book operations and reader sessions in Node main threads, CommonJS/ESM/nested Workers and Bun. Host implementation, Runtime clients, Documentation, TypeScript declarations, Ace hints, Offline Docs and the paired Builder are synchronized. Final physical acceptance covers 185 distinct cases on Android 13/15, including actual independently signed applications. Both devices are restored. This is method-name coverage with the portable contracts below, not complete Rhino parity.

## Public contract

Node provides `epub` / `autojs6:epub` and the explicit main-thread `$autojs.epub` facade. Bun provides `host.epub` under the separate, default-off `epub.control` grant.

The ten root operations are `open`, `metadata`, `toc`, `readingOrder`, `text`, `cover`, `search`, `annotations`, `read` and `isAvailable`; each has its registered `Async` counterpart. All remote calls return Promises, and the `Async` names alias the same functions. `EpubError` retains structured error codes.

- `open` resolves after metadata, toc, readingOrder and positions are available. Book handles support text/textAll, cover/resource export, search, annotations and explicit closure.
- A cover read without an output path returns encoded `Uint8Array`/Node Buffer bytes. It does not return a Rhino `ImageWrapper`; callers can decode bytes through the separately granted image API.
- Reader sessions expose controls, local state and event listeners on their owning JS thread. Event fields preserve the EPUB contract, including `totalProgression`, `chapterTitle` and flattened bookmark/highlight changes. Startup events are buffered for listeners added after `read` resolves.
- Reader controls return Promises; navigation completion is observed through progress events. Session closure normally leaves the reader visible; `finish: true` also closes its Activity.
- Operations use a default 60000 ms deadline and optional `AbortSignal`. Large positive deadlines avoid JavaScript timer overflow, and an elapsed deadline detaches its listener before creating a client. Cancellation, late replies, Worker exit and execution teardown release the client's own resources.

The host reuses `EpubScriptArguments`, `EpubBookOperations` and the existing provider implementation. Fragmented replies preserve long Unicode text and binary results. Source paths follow the existing EPUB path rules; arbitrary URI support is not added. The provider's single visible scripted-reader policy and shared preferences/book data remain explicit boundaries.

## Independently signed applications

3-Folio EPUB adds a separate service and reader Activity alias protected by the normal `org.autojs.plugin.epub.permission.RUNTIME` permission. The actions are `org.autojs.plugin.EPUB_PACKAGED` and `org.autojs.plugin.EPUB_PACKAGED_READER_OPEN`. Original signature-protected host entries remain available.

Both routes share the implementation. Returned book and reader-control Binders check the creating UID, and service teardown closes only its own reader sessions. The host selects the route from the calling application context. Node/Bun package preparation derives the permission from declared EPUB capability; neither generated application requests microphone permission.

The EPUB AIDL transaction order, protocol versions, Readium dependencies and user-data schema are unchanged. Each provider service instance retains its eight-book limit and idle cleanup. Bun EPUB fragments/events are exempt from the ordinary 1024-call execution lifetime quota; grant, payload, concurrency and individual RPC deadlines still apply. Native Bun bytes, its historical compatibility matrix and native Node engine binaries are unchanged.

## Tested source and artifacts

These versions identify the tested implementation, rather than an arbitrary later workspace HEAD.

| Component | Version | Implementation commit |
|---|---|---|
| AutoJs6 host and Runtime Kit | 6.8.0 / Android build 5679 | `f58d824ad8fc310e6d631a14010d23699303acdb` |
| Node Runtime | 1.26.0 / 275 | `0d491413acabea0f2622ac72f45eb12967c90b6a` |
| Bun Runtime | 0.15.0 / 275 | `a5d98cc16cab6305db4b72fa556815c5a3ee722f` |
| 3-Folio EPUB | 2.1.0 / 178 | `12328cb492f18600fc86371ed2c47a2314ec957b` |
| Documentation | revision 153 | `7b8645ccf4b1d2c0fef1259b21247975f569721b` |
| TypeScript Declarations | 4.73.0 | `c0ed978bd12b054372484a073827de346c126cb3` |
| Ace Editor | 2.35.0 / 315 | `0cfd2859c878bfeb38e1a9f8edfee47a4ca892b6` |
| Offline Docs | 6.8.33 / 191 | `6fe0171b02481366d0006d0fd0b3fb1b0ceb70a1` |
| APK Builder Template | 1.2.20 / repository build 95, Android code 567902 | `ad7e54bd9ee7366a484761e91b4aab1148bb0629` |

Host raw `VERSION_BUILD` is 4027 with `VERSION_CODE_OFFSET` 1652. The later host commits `5b6291bdd` and `ea59a84de` change only device fixtures, leaving the tested production code unchanged. `ce884c909` separately adapts the existing console device fixture to typed settings keys.

The arm64 Runtime Kit is `autojs6-runtime-v6.8.0+5679-f58d824ad8fc310e6d631a14010d23699303acdb-arm64-v8a`, retained in the Builder repository at `.bak/runtime-epub-20261020/kit-5679`. Its template SHA-256 is `66448b3f51b90a2700bcd1632c60172853f82d6552e52486be7dcbb80bb2e71c`. Generated applications have matching Runtime Kit DEX hashes and certificates distinct from the host.

The sealed host APK SHA-256 starts `f0af21690f2a`; final host-test starts `703c648a0c7f`; Node starts `358dc9a4de7e`; Bun starts `078cfc6a9f38`; 3-Folio release starts `e275013df53f`; Ace starts `9d28fec87360`; Builder starts `643a56dc4db4`. Full SHA-256 values, certificates, source commits and immutable APK copies are in the evidence directory. Provider instrumentation uses its debug build; host and packaged EPUB checks use its signed release build.

## Verification

| Check | Accepted result |
|---|---|
| Node clients/Workers | 87 tests plus seven legacy standalone programs |
| Bun SDK | 70 tests |
| JVM suites | 5677 discovered, 5651 passed, 26 skipped, zero failures/errors |
| Native Node bridge | Syntax check with the actual NDK compilation configuration |
| Runtime declarations | 76 synchronized files; TypeScript 5.1.3 and 6.0.3, no diagnostics |
| Ace local coverage | 831 runtime completion queries; language-service lifecycle regression passes |
| Focused EPUB gate | 20 registered names, shared factory/types and both provider routes |
| Full augment gate | 91 routes, 1356 explicit exports, 1589 documentation/type/completion paths, zero failing paths |
| Offline documentation APKs | Debug/release validated, 224 files / 14712355 bytes |
| Android builds | Host/instrumentation, Runtime Kit, Node, Bun, EPUB debug/release/test, Ace, Offline Docs and Builder pass relevant assembly/checks |

The augment totals measure documentation, declarations and completion against host exports. They are not a count of implemented Node/Bun runtime APIs. JVM counts are host 5014 (26 skipped), EPUB API 9, Node 96, Bun 60, provider 269, Ace 187, Offline Docs 2 and Builder 40. The Offline Docs content digest is `58dada23bb0e542832bf75b89f26cb4ebdcc1c9451c39d856f98e6e7045852a3`, pinned to Documentation revision 153. Relevant Python, Markdown-generation, unchanged Bun matrix and repository checks also pass.

| Physical suite | Redmi 22120RN86C / API 33 | Xiaomi 23046RP50C / API 35 |
|---|---:|---:|
| EPUB provider contract/service/reader | 19 | 19 |
| Host native EPUB transport | 1 | 1 |
| Host Node/Bun/Worker EPUB integration | 5 | 5 |
| Node ecosystem/conformance | 34 | 34 |
| Bun Binder/runtime | 13 | 13 |
| Real APK production | 1 | 1 |
| Installed applications, first launch and relaunch | 1 | 1 |
| Ace real completion popups, final build 315 | 18 | 18 |
| Console typed-settings fixture compatibility | 0 | 1 |
| Total distinct accepted cases | 92 | 93 |

Both devices use arm64-v8a with 4096-byte pages. These checks do not extend ABI or 16 KB runtime acceptance. The Redmi serial is `bek749scrwv4wo8h`; Xiaomi is `968e9f18`.

Each device builds and freshly installs a Bun application and a Node/Worker application. Both launch once, are force-stopped, and launch again; the EPUB provider is force-stopped before every launch to prove cold binding. Across two devices this produces eight launches and twelve successful main/Worker program results. Both generated applications report their own package/application context and Runtime Kit build 5679.

Real operations cover a synthetic EPUB with three reading-order items, a long chapter containing 10000 Unicode markers, cover/resource export, search, annotations, reader navigation/events/closure, capability denial, Worker parent isolation and recovered resource capacity. Bun also executes more than 1024 fragment/event calls. Test content is synthetic; no user books or downloaded models are used.

All eight final EPUB completion screenshots were inspected: Node/Bun `open` displays its Promise<Book> result and progress callbacks display `chapterTitle: string | null`, with wrapped completion documentation. The separate signature-help overlay can clip long lines on narrow screens; this is not claimed as a full signature-help layout redesign. Xiaomi's final Ace check is a scoped follow-up after the other packages had already been restored; its separate receipt identifies the exact Ace APK.

## Failures found and corrected

1. The first host build exposed an existing `ConsoleTimeSettingsDeviceTest` constructor mismatch after the typed-settings migration. Two call sites/imports were adapted in the separate compatibility commit, and the physical console test passes.
2. The isolated worktree initially lacked 45 ignored pre-6.6 module oracle files, causing 30 JVM initialization failures. Copying the existing test inputs resolved those failures without changing production modules. Their hashes are retained; copies were then archived outside production assets before Runtime Kit generation. The stale asset-merge state was cleaned before the final build.
3. Large JavaScript operation deadlines could overflow timers. A regression failed against the previous factory; chunked scheduling and immediate-expiration listener cleanup pass the final Node/Bun suites.
4. Both initial provider suites reported 17 cases with one failure when the reader Activity was recreated during pending adoption. A deterministic held-storage test also failed on the original artifact. The provider now retains the adopted token and accepts Opening/Ready reattachment only for that same token. It also validates the explicit packaged reader alias. Corrected provider suites pass all 19 cases on both devices.
5. Redmi's original selection was the legacy Readium provider. The safety assertion stopped the initial host run before book operations. The device fixture now temporarily selects task-owned 3-Folio and restores exact enable-state keys/cache in `finally`. Legacy Readium installation and book data were not changed.
6. The first packaged-result assertion used an obsolete audio-fixture field. The test now checks `readerClosed`; both task applications were freshly reinstalled and all first/restart launches repeated with valid results.
7. Redmi's first Ace 314 group ran 18 cases with four timeout failures. Isolated image/database/EPUB cases passed; TTS and Java callback cases exposed runtime-switch initialization being charged the normal 2000 ms budget. Prior Ace 313 also reproduced the TTS failure. A virtual-clock regression failed before the fix. Ace 315 replaces the semantic provider when its Node/Bun/Rhino API runtime changes, applies the existing 6000 ms first-operation budget once, retains 2000 ms for ordinary edits, and disposes the old provider. The unchanged final 18-case device group passes on both devices. Original failed logs and baseline comparisons remain separate from accepted receipts.

## Restoration, cleanup and ownership

Fresh backups were taken for this EPUB batch. Redmi's six original APKs and Xiaomi's eight original APKs are restored and matched by full SHA-256; initially absent task packages, generated applications and 3-Folio installs are removed. Settings, permission states, volume and recorded application operations are verified. The host's Bun grant map and plugin enable-state map are restored. Test book/export/result/SDK files and generated APKs on the devices are cleaned, and pre-existing Ace evidence files are restored.

Redmi's originally enabled host accessibility service is rebound. Xiaomi retains its original AutoJs4/Speech accessibility configuration; the host was not originally enabled there. Original backup APKs remain under the ignored evidence directory for recovery. No old speech restoration script was rerun. Redmi's initial permission restoration mistakenly treated duplicate denied records from an uninstalled secondary-user section as primary-user state; the helper was corrected to retain the first primary-user record, and all original grant/flag lines then passed final verification.

The primary host's five staged Chinese release-draft edits and concurrent settings/modules/memory changes were preserved. Other sessions' Speech changes are outside this batch. The EPUB feature was integrated using file-scoped source/index reconciliation; later reports use scoped commits. No push, tag or publication was performed.

The owned temporary host worktree is unregistered and its copied signing inputs are removed after artifacts are archived. Git's directory deletion encountered Windows long paths; automatic approval review rejected the subsequent recursive deletion with "blocked by policy". Remaining files are retained in the `.bak/runtime-epub-20261020/worktree-remainder*` archives, outside the temporary workspace path. Eleven generated Worker test directories are archived in the Node repository's `.bak/runtime-epub-20261020/worker-test-archives`; the pre-existing `build/worker-host-tests` remains. No global ADB/Gradle stop and no new AVD were used.

Evidence is under `.bak/runtime-epub-20261020` in the primary host repository: `final-summary.json`, `active-state.json`, `artifact-map.json`, `feature-commits.json`, final scoped-commit manifests, per-device accepted evidence/restoration receipts, immutable APKs, JUnit XML and screenshots. Helper scripts are historical execution records; they must not be rerun wholesale after device restoration or worktree removal.

AI, flow, mail and other remaining runtime differences still require separate audit/implementation batches. This delivery does not imply synchronous Rhino object/prototype behavior or complete runtime parity.
