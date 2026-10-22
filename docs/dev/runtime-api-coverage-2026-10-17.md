# Node.js and Bun API repair and device validation

Date: 2026-10-17 (Asia/Taipei).

This delivery addresses the audit's prioritized Pinyin, console controls, type declarations and coordinate conversion gaps. Bun receives an explicit client for the same host capabilities. It does not claim full Rhino API equivalence for either runtime.

## Runtime behavior

- Node `pinyin` / `pinyin4j` now call the installed providers asynchronously, including `pinyin4j.readings`. The small built-in dictionary is removed. Callers must await results; `compact` uses the host's matrix semantics.
- Node console window and styling operations are asynchronous host calls. Controls target the console receiving that execution's output; `console.tail` remains a global-log operation. Global file-log configuration retains its explicitly global meaning. Showing a window requires Android overlay permission.
- `screen_metrics` and the Node compatibility facade expose `cX`, `cY`, `cXy`, `cYx` and the existing `getScaleBases` / `setScaleBases` family. Each execution owns a fresh `Scale`; each axis can be assigned once. Getters and setters are asynchronous.
- Node and Bun use `RuntimeCoordinateBridge`, which delegates to the same `CoordinateScale` and `Scale` used by Rhino. A display snapshot reads rotation once for both dimensions. Explicit bases, ratio mode, `width:height` literals, negative rounding and invalid inputs retain the shared rules.
- Bun supplies an explicit SDK through `AUTOJS6_HOST_CLIENT_MODULE`. `createHostClient()` negotiates grants. `text.pinyin` (6 methods), `text.pinyin4j` (3), `screen.metrics` (11) and `console.control` (31) have independent Plugin Center switches. The existing toast and device-info calls remain available. Console control defaults off.
- Both clients reject non-finite numeric arguments and explicitly undefined coordinate values before JSON can turn them into null. Zero-argument `cX()` / `cY()` and optional undefined base/mode values remain valid. Numeric coordinate strings are declared and supported.
- Bun waits for the finished callback before closing the output receiver after a successful synchronous result. Failed results preserve their original error if callback delivery fails. Cancellation remains bounded.

Node CJS and ESM share these public modules. Workers retain their generic parent-routed bridge; native CLI processes do not automatically receive the script bootstrap. Bun does not inject Rhino globals.

## Declarations, documentation and completion

The Node-specific index now includes `input_observer` and `screen_metrics`. Missing `accessibility.swipe` / `gesture`, `fetch.request`, `axios.AxiosError` and profile members are declared. Coordinate zero-argument overloads are separated from overloads requiring a value, so strict typing rejects `cX(undefined)`.

The companion declaration project supplies isolated Node and Bun entry points and reproducibly synchronizes 68 runtime declaration files into Ace. Ace selects the runtime from supported directives, extensions and Node project metadata. It excludes Rhino globals and prototype extensions from Node/Bun, while retaining explicit Node JSOX module completion. Long signatures wrap within the viewport without covering code or the completion list.

The complete host check also found new inline nested mounts and Speech documentation/types missing since the earlier audit. The extractor now understands `.also` mounts, including named lambda parameters, and extracts `plugins.tools`, `files.batchRename` and `speech.models`. Unknown forms still fail. Speech's 19 explicit members and session/model types are documented and typed; they remain excluded from Node/Bun globals.

## Verification

Device: Sony XQ-DQ72, serial `QV770340J7`, Android 13 / API 33, native arm64-v8a, 4096-byte pages. Physical portrait dimensions were 1096 x 2560; landscape dimensions were 2560 x 1096.

| Check | Result |
|---|---|
| Shared coordinate math and screen-cache JVM tests | 18 passed |
| Host JSON adapter, Bun output ordering and real API integration | 10 passed |
| Coordinate matrix | 36 executions: 3 runtimes/import modes x 4 rotations x 3 base pairs |
| Numeric coordinate assertions | 684 passed, including numeric strings |
| Invalid coordinate/base calls | 216 rejected as expected |
| Same-execution rotation | Node CJS and Bun each observed 0 -> 90 -> 270 -> 0, 8 observations total |
| Node npm ecosystem and complete Android conformance classes | 34 passed |
| Bun SDK Binder round trip | Passed, including real SDK loading, grant checks and cleanup |
| Pinyin provider Binder/service lifecycle class | 3 passed; includes 6 real service recreations and repeated dictionary calls |
| Ace default APIs, runtime isolation and JSOX activation/removal | 3 passed; wrapped signatures also checked on the device |
| Runtime client/host contract comparison | 4 groups / 51 methods matched |
| Host mount/parser tests | 8 passed |
| Complete host API coverage | 91 mounts, 1351 explicit exports, 1522 TypeScript/Ace paths, no failing paths |
| Runtime declaration sync | 68 files matched their Node/Bun sources |
| Independent declaration checking | TypeScript 5.1.3 and 6.0.3, strict mode and full declaration checking |

The real-provider assertions include `pinyin.simple('鍖椾含涓婃捣') === 'beijingshanghai'`, Pinyin4j readings, actual console title/color state and output ownership. Bun revocation was exercised across executions: successful conversion, revoked capability with SDK rejection and raw HTTP 403, unaffected coordinate/Pinyin4j calls, then successful conversion after restoration.

An initial repeated-provider run used a stale July APK from the Pinyin repository's `app/release` cache and failed with an already-closed SQLite database. Current Pinyin source had already fixed that lifecycle in `6d217e95` on 2026-09-01. Validation was repeated with a current 1.0.3 / build 33 APK, and a service-recreation regression was added. This is not claimed as a new database implementation fix.

The device checks exercise current compiled code and actual Binder/providers. The local wrapper checks use controlled transports and are reported separately. No new speech audio/model download tests, historical Node build-slot acceptance or additional device/ABI acceptance are implied.

## Repeating the checks

```powershell
python tools/api-coverage/check.py
python -m unittest discover -s tools/api-coverage -p 'test_*.py'
node tools/api-coverage/check-runtime-bridges.mjs
.\gradlew.bat --offline :app:testAppDebugUnitTest --tests org.autojs.autojs.runtime.api.ScreenMetricsCacheTest --tests org.autojs.autojs.runtime.api.augment.global.CoordinateScaleTest
```

The Android classes are `RuntimeCoordinateBridgeTest`, `BunOutputCallbackDeviceTest` and `RuntimeApiBridgeDeviceTest` in `org.autojs.autojs.engine`. The last class requires the current Node, Bun, Pinyin and Pinyin4j plugins. It restores Bun grants and screen rotation settings and stops only its owned UI fixture.

Detailed local build logs, APK identities and device JSON/screenshots are retained under ignored `.bak/runtime-api-20261017/`. Generated README/changelog files match their sources. The host's repository-wide translation-count check still reports pre-existing differences: before this work, the nine non-Simplified-Chinese files already lacked 12 feature, 8 fix and 6 improvement entries relative to the Simplified Chinese draft. This delivery adds the same six entries to all ten languages and leaves the unrelated draft/order changes untouched.

## Remaining audit boundaries

The full host coverage gate measures documentation, types and completion against host exports. It must not be read as 1351 implemented Node/Bun APIs. The subsequent [OpenCC and common image delivery](runtime-opencc-images-2026-10-17.md) closes the OpenCC facade gap and adds common image operations. The [advanced image delivery](runtime-image-parity-2026-10-17.md) supplies portable counterparts of all 81 registered image functions in Node/Bun/Workers, plus ObjectFrame and readImage. Java object/prototype behavior and undocumented coercions are not implied by that count. The 2026-10-18 [UI/Compose delivery](runtime-ui-compose-2026-10-18.md) adds portable layouts and the 54-component Compose data contract to Node/Bun/Workers, with 129 accepted physical tests. The subsequent [UI interop delivery](runtime-ui-interop-2026-10-18.md) adds execution-owned View/context tokens, asynchronous AndroidView factories and XML Compose attachment, including Node Workers and actual packaged Node/Bun applications. The [common XML and property delivery](runtime-xml-views-2026-10-18.md) adds 12 native form/layout widgets, 46 writable and six read-only asynchronous View property accessors, including Worker and packaged-app usage. The [WebView and navigation component delivery](runtime-view-components-2026-10-18.md) adds WebView, Drawer, Toolbar and ViewPager, bringing the property contract to 58 writable and 11 read-only names plus 12 component commands. The [WebView client delivery](runtime-webview-callbacks-2026-10-18.md) adds HTTP(S) downloads, system file selection, 40 client/download callbacks and 10 public methods, including asynchronous decisions and preconfigured synchronous navigation/resource policies. The [WebView file delivery](runtime-webview-files-2026-10-18.md) adds full-size camera results, SAF save/tree selection, document reads and blob/data transfers, including selected output destinations and opaque download IDs. The [WebView media delivery](runtime-webview-media-2026-10-18.md) adds system video capture and foreground AAC/M4A audio capture, configurable chooser waits and Worker cleanup, including packaged Node/Bun applications. The [WebView streaming media delivery](runtime-webview-streams-2026-10-18.md) adds awaited page Promise evaluation, explicit Android media permission requests, actual getUserMedia/MediaRecorder chunk transfer and media release on presentation close, including Node Workers and packaged applications. The [display/background delivery](runtime-webview-display-2026-10-18.md) adds an opt-in MediaProjection-to-canvas getDisplayMedia adapter, foreground-service web audio, native Camera2/MediaRecorder background files and independent parent/Worker View event subscriptions. The [Bun automation delivery](bun-automation-2026-10-19.md) adds the public Node-compatible accessibility, application and clipboard facades, with four independent grants, bounded asynchronous operations and real packaged-app validation. Device-specific service binding remains a prerequisite, and unsupported privileged/Java helpers are not counted as implemented. The [Bun file/storage delivery](bun-storage-2026-10-19.md) adds all 15 usable Node file names with asynchronous host I/O and the shared Node storage/storages facade, with separate grants, cross-runtime persistence and packaged-app force-stop/restart validation. File transport budgets and the existing Node storage-name normalization remain explicit compatibility boundaries. The [Bun/Worker database delivery](bun-database-2026-10-19.md) supplies the shared database/sqlite facade, typed query binding, correct insert counts and owner-thread transaction cleanup, including CJS/ESM/nested Workers and real packaged Bun/Worker applications. The [Bun/Worker sensor delivery](bun-sensors-2026-10-19.md) adds shared accelerometer/gyroscope/light inventory, samples and subscriptions with cancellation, Worker termination cleanup and real packaged-app verification. Device sensor inventories can include vendor-restricted entries; Bun retains its bounded bridge-call budget. Arbitrary Java View/Context objects/property reflection, arbitrary synchronous JavaScript WebView callbacks, the full Rhino XML widget and expression-binding surface, and adjustment chrome remain outside the portable contract. Android-object helpers, calendar/IntentTask parity, and dedicated AI/flow/TTS/mail/EPUB and other large runtime surfaces still require separate implementations or explicit compatibility decisions. Native libraries and `rhino.run` are not counted as direct parity.


The sensor delivery's 2026-10-19 broader rescan identified five declaration/Ace gaps: shell.checkAccess, shell.isRootAvailable, shell.setDefaultOptions, shell.spawn and shizuku.spawn. The subsequent [shell declaration/completion delivery](shell-api-coverage-2026-10-19.md) closes all five, adds process/constructor/result contracts and fixes default-editor Context/result dependencies. Its expanded gate covers 1356 explicit exports and 1589 paths with zero failures, plus actual shell and Ace device checks. These are Rhino declaration/completion results; Bun's host shell capability and Node streaming/Shizuku/Worker integration remain runtime audit items.


The [host Shell delivery](runtime-shell-2026-10-19.md) adds Bun shell/shizuku and Node main/Worker streaming, explicit Shizuku app authorization, PTY and owned-process cleanup. It verifies actual shell UID execution and real packaged Bun/Worker applications on Android 13/15. Bun transport quotas, Root availability and the broader runtime gaps remain explicit; the Redmi cold-binding retry is retained separately.

The [event/notification delivery](runtime-events-2026-10-19.md) adds Bun and Node Worker facades, independent subscriptions, scoped notifications, cancellation/overflow cleanup and actual host/packaged validation on Android 13/15. Android service authorization and Bun transport budgets remain explicit; it does not imply full Rhino parity.

The [device/dialog delivery](runtime-device-dialogs-2026-10-19.md) adds Bun and Node Worker device/dialog facades, scoped wake locks, precise cancellation, live progress handles, four independent Bun grants, derived APK permissions and Android 13/15 host/packaged validation. The original Bun device.info format remains compatible; full snapshots use getInfo.

The [Android object and persistent task delivery](runtime-android-tasks-2026-10-19.md) adds Node/Bun/Worker native object helpers, calendar/IntentTask APIs, scoped object cleanup, persistent registrations, DST-correct recurrence, filtered receiver lifecycle, queued Bun host calls and actual host/packaged validation on Android 13/15.


## 2026-10-20 Java/Android interop follow-up

Node.js main threads, Bun and CJS/ESM/nested Workers now share explicit public Java object access, application Context handles, typed arrays and overload selection. The host no longer limits these calls to nine predefined classes. Exact long values and reserved ESM names are supported; cancellation retains late object replies for cleanup. Bun uses its independent java.interop grant and unchanged native runtime. See [this batch](runtime-java-interop-2026-10-20.md) for final verification and restoration. JavaScript callback/subclass generation, dedicated loadDex/loadJar helpers and the remaining specialized plugin surfaces remain outside this batch; this is not complete Rhino parity.


## 2026-10-20 Java callback and loading follow-up

Node/Bun and Worker now provide proxy/wrap, generated subclasses, callSuper, callback argument retention and loadJar/loadDex. Return-valued and Promise callbacks stay in the owning JS runtime; Java calls can reenter the waiting native thread. Read-only class copies and class loaders are execution-scoped. See [the callback batch](runtime-java-callbacks-2026-10-20.md) for verification, limits and restoration. This does not add synchronous Rhino package globals, automatic function-to-interface conversion, AAR resources or JNI deployment.


## 2026-10-20 TTS follow-up

The [TTS delivery](runtime-tts-2026-10-20.md) adds Node/Bun/Worker engine and voice queries, speech, silence, atomic file synthesis and task events through the shared host service. All 31 registered TTS method names have counterparts, with explicit async task handles and isolated client cleanup. AI/flow/mail/EPUB, speech recognition/model management and other specialized modules remain separate audit items. See the delivery report for current verification status; name coverage does not imply inherited Rhino object or prototype parity.

## 2026-10-20 Speech follow-up

The [speech delivery](runtime-speech-2026-10-20.md) adds all 18 registered speech/models method names to Node main threads, Bun and CJS/ESM/nested Workers, including recognition, VAD, microphone/external PCM sessions, wake events, model management and synthesis. A separate permissioned provider entry enables independently signed generated applications while retaining caller microphone checks and UID-scoped cancellation. Redmi and the final Xiaomi host/packaged suites each pass 56 cases; the earlier Xiaomi Ace run adds 17, for 129 accepted physical cases. Xiaomi's initially deferred verification was completed after the user released the device and the other session's driver exited. Its restoration preserves the takeover host/test APKs and Speech build 74, restores the other original packages and settings, and removes this batch's test packages and files. AI/flow/mail/EPUB and other specialized surfaces remain separate audit work; this method-name coverage does not establish complete Rhino parity.

## 2026-10-21 EPUB follow-up

The [EPUB batch](runtime-epub-2026-10-21.md) implements all 20 registered root method names, book operations and reader sessions for Node/Bun/Workers. It adds a separate permissioned provider service/reader entry for independently signed applications. Host/client/type/completion checks pass, with 185 accepted physical cases on Redmi/API 33 and Xiaomi/API 35. Real independently signed Bun and Node/Worker applications complete eight fresh/restart launches and twelve program results. The provider's pending-reader recreation and packaged alias validation were corrected; Ace now treats a runtime-directive switch as a fresh compiler initialization without relaxing ordinary operation budgets. Both devices are restored and exact source/APK/Runtime Kit identities are recorded in the batch report. AI/flow/mail and the other remaining runtime differences are still separate work.


## 2026-10-21 Mail follow-up

The [Mail batch](runtime-mail-2026-10-21.md) implements all 57 registered root/providers/accounts method names and client, folder, message, attachment and watch facades for Node/Bun/Workers. A separate normal-permission provider entry supports independently signed apps using their own credentials; saved accounts remain behind the original signature entry. Actual transfers corrected debug-only progress resets and repeated serialization of large message bodies. Both devices pass 96 checks each, with eight fresh/restart packaged launches and twelve program results, and are restored to their recorded baselines. Xiaomi uses its fresh build 5679 takeover baseline after the original P1 fleet finished. Exact source/APK/Runtime Kit identities and failure corrections are in the batch report. AI, flow and other remaining differences still require separate audit; these counts do not establish complete Rhino parity.


## 2026-10-21 Flow core follow-up

The [Flow core batch](runtime-flow-core-2026-10-21.md) implements 22 of the 52 registered Flow root method names for Node/Bun/Workers, including asynchronous chains, predicate/selector/stability/component waits, Promise combinators, branch cancellation, deadlines and single-step retry. Stateless host snapshots use the existing accessibility grants. Async context retains child ownership after await, and Activity identity survives container/dialog events. Redmi/API 33 and Xiaomi/API 35 each pass 56 final physical cases; independently signed Bun and Node/Worker apps complete eight fresh/restart launches and twelve program results. Both devices are restored to fresh recorded baselines. Documentation, 78 runtime declarations, Ace completion and paired Runtime Kit build 5741 are synchronized. Thirty Flow toolkit/event root names, within/scope, synchronous Rhino terminals, AI and other previously identified differences remain separate audit work; these counts do not establish full Flow or Rhino parity.

## 2026-10-21 Flow tools and event waits follow-up

The [Flow tools/event batch](runtime-flow-tools-2026-10-21.md) closes the remaining 30 registered Flow root-name gaps, reaching 52 names, with 25 node action chains, native Toolkit algorithms, real event waits and EventFlow.ready. It adds snapshot relocation, asynchronous native callback reentry, independent Bun flow.control and cancellation/Worker cleanup. Actual tests corrected capability catalogue initialization, null results, Bun Binder/UI scheduling, stale foreign window events and foreground discovery immediately after packaged service binding. Redmi/API33 and Xiaomi/API35 each pass 56 physical cases, with eight fresh/restart packaged launches and twelve program results across the two devices. Both fresh baselines are restored. The accepted kit is host5757/source8717dc916, separate from mutable primary/parallel work. within/scope, synchronous Rhino terminals, arbitrary Java prototypes, full Flow gesture overloads, AI and other audit differences remain; registered-name coverage does not establish full Flow or Rhino parity.

## 2026-10-21 Flow scopes and gesture overloads

The [Flow scope/gesture batch](runtime-flow-scope-2026-10-21.md) adds within/scope, inherited roots, compass/result projections, node-array actions and the press/swipe/gesture/gestures instance overloads across Node/Bun/Workers. Root-name coverage stays at 52; six prototype names are added. Read-only root relocation permits geometry motion while action snapshots remain strict. Both devices pass 56 physical cases, with eight packaged first/restart launches and twelve results in total; sixteen touch-observed gesture forms and CJS/ESM/nested Worker cleanup are covered. Both devices are restored, including Xiaomi's fresh post-P4 host5781 baseline. The retained kit is host5788/sourceb2eccefd8. A declaration-only Bun/Ace follow-up is separately hash-bound to the Xiaomi cohort, with runtime code unchanged. The Flow gate passes; seven pre-existing memory static fallback gaps remain in the general augment gate. Synchronous Rhino terminals, arbitrary Java object/prototype behavior and other audit gaps remain separate work.

## 2026-10-22 AI core follow-up

The [AI core batch](runtime-ai-core-2026-10-22.md) implements the five registered AI core roots, four session methods and three authorization/lifecycle helpers for Node/Bun/Workers. It uses the native provider with bounded Unicode streaming, cancellation and acknowledged result ownership. Independently signed applications require explicit identity-bound per-app consent in 3-Stone AI. Xiaomi completes final AI 299 acceptance with 60 physical cases, ten packaged launches, six positive main/Worker program results and eight inspected Ace screenshots, then restores its fresh nine-APK baseline, settings, 12 private AI files and both model binaries. Two consecutive final host runs pass. AI 298 fixed queued-cleanup loss; AI 299 additionally lets one successor wait for a closing session's actual native release, with timeout/cancellation retained and no concurrent inference or retry. Redmi's earlier AI 298 cohort also passed 60 cases, ten launches and six program results and completed restoration. Its new AI 299 follow-up hit a low-memory kill, then an external host replacement; at the user's request its current state is preserved and further test/cleanup/restoration is deferred. Complete two-device acceptance of AI 299 is not claimed. Documentation, 79 runtime declaration assets, Ace and Runtime Kit 5835 / source 43da3f164 remain synchronized. Local JVM totals are 6,124 passed and 26 skipped. `ai.agent`, synchronous Rhino behavior and other audit differences remain separate work.

## 2026-10-22 AI Agent follow-up

The [AI Agent batch](runtime-ai-agent-2026-10-22.md) implements nine registered roots, seven AgentRun methods and three access/lifecycle helpers for Node main threads, CommonJS/ESM/nested Workers and Bun. Registered scripts share their actual execution context/result slot with their Workers. Independently signed applications use the original host's Agent brokers after installation-bound app consent, with isolated task history and cancellation on revocation. Node's `ai.agent` permission and Bun's default-off `agent.control` grant are separate from AI core access.

Implementation, documentation, 79 runtime declaration assets, Ace and source-bound host 5850 / Runtime Kit / Builder artifacts are synchronized. Local JVM checks total 6,333 passed and 27 skipped, with zero failures/errors; Node's 159 modern tests plus seven legacy programs, Bun's 86 SDK cases, both TypeScript versions and 1,172 Ace queries pass. The general augment gate still reports seven existing memory/Ace static gaps, and the host multilingual changelog check retains inherited gaps. After explicit user handoff, Xiaomi passes 58 physical cases, 12 independently signed application launches, nine positive main/Worker program results and eight inspected Ace screenshots. An earlier host attempt passed seven of eight cases because the real model omitted completion evidence; the unchanged native rule returned partial. A test-only clarification and evidence assertion are followed by a complete eight-of-eight host pass. All 36 batch-tagged task records, including failed/retried tasks, are reconciled. The new 11-APK baseline, settings, AI/Agent/host private data, both models and original host accessibility binding are verified restored. A preliminary installation before handoff had already been fully backed out without API tests. No Redmi operations were issued. The scheduled handoff monitor is paused at the user's request. Complete Rhino runtime parity remains unclaimed.
