# Node.js, Bun and Worker TTS, 2026-10-20

The TTS batch is implemented and verified in Node.js main threads, CJS/ESM/nested Workers and Bun. It covers all 31 registered Rhino TTS method names and four constants; the shared facade has 40 members including client closure and listener helpers. Documentation, declarations, Ace and continuous coverage checks are synchronized. This does not imply full inherited Rhino Java/EventEmitter behavior or complete runtime parity.

## Delivered behavior

Node uses tts/autojs6:tts and the explicit $autojs.tts compatibility member; Bun uses host.tts with its independent default-off tts.control grant. Packaged projects declare tts.control for Bun or tts for Node. No microphone or global audio-settings permission is introduced. Constants and getMaxInputLength remain local; native operations and task creation are asynchronous.

All clients reuse TtsService's Android backend, queue, splitting, timeout and staged atomic-file publication. Each client owns its defaults and service instances. Observable tasks provide result, state, isDone, stop/cancel and typed events; audio data is Uint8Array. Event drains select only handles already received by JavaScript, preserving replies during concurrent creation. Terminal state and result settle before listeners run, so closing from a done listener preserves completed work. Closing rejects pending queries; Worker exit releases only the child clients while parent tasks remain usable.

The internal Rhino adapter preserves the original public object. A reproduced regression showed that directly implementing a common interface made utterance.isDone a function through a synthetic JVM getter. The adapter avoids that public interface, and the existing Rhino interoperability test now asserts the boolean property before checking listener behavior.

Engine enumeration reads PackageManager without initializing the default engine. Its ordering/default selection follows the installed Android SDK TtsEngines implementation. This fixes discovery when default initialization fails. Explicit ready/voice/synthesis operations still validate the chosen engine. Relative files resolve against the host execution directory, falling back to application files when that directory is empty. Existing file size, overwrite and atomic-publication rules remain shared with Rhino.

## Accepted verification

| Scope | Result |
| --- | --- |
| Redmi 22120RN86C, bek749scrwv4wo8h | Android 13/API 33, arm64, 4096-byte pages; host 6, Node/npm 34, Bun 13, APK production 1 and packaged execution 1 |
| Xiaomi 23046RP50C, 968e9f18 | Android 15/API 35, arm64, 4096-byte pages; the same 55 cases plus all 16 Ace popup/default/JSOX cases |
| Accepted physical cases | 126, no failed class counted as a pass |
| Generated applications | Four real Builder APKs, eight launches including force-stop/reopen, twelve runtime-program results across Bun, Node main and Worker |
| JVM | 5166 discovered, 5140 passed, 26 existing skips, zero failures/errors; XML archived |
| Node client tests | 68 unit cases and seven standalone API/Worker programs pass |
| Bun SDK tests | 66 cases pass |
| Runtime declarations | 74 synchronized files; TypeScript 5.1.3 and 6.0.3 report no diagnostics |
| Ace local checks | 688 runtime queries and 276 core/default/JSOX queries pass; task and audio popup screenshots visually checked |
| Host coverage gate | 1356 explicit exports and 1589 paths, no failures; documentation/type/completion coverage, not implemented Node/Bun API totals |
| Documentation and Offline Docs | 168 modules and 224 packaged documentation files; debug/release APK checks pass |

Real programs use com.xiaomi.mibrain.speech, voice zh, producing 68822-byte WAV files in the host tests. They inspect RIFF/WAVE headers, relative paths, typed audio events, silent speech, cancellation, reset defaults, capability denial and delayed-handle event delivery. CJS, ESM and nested Workers are terminated while the parent completes its own silence within a fixed deadline. The installed 3-Macaw Speech provider is discoverable but is not counted as synthesis acceptance.

Generated applications report their own package identity and runtime build 5615, read their real WAV files and delete the outputs. APK signatures differ from the host signature, and their runtime DEX files exactly match the paired Kit template. Bun's copied SDK matches the installed plugin bytes. Native syntax, relevant builds/lint, example compilation, Python checks, typed smoke programs and actionlint checks pass. Host CI now checks runtime Java/TTS sources with selectable companion refs; Node and Bun CI include their Java/TTS client and Worker regressions.

## Repaired failures and limits

Initial failures are retained separately: the isolated checkout lacked ignored obsolete-module test fixtures; an early common-interface implementation changed Rhino's isDone property; default-engine initialization blocked discovery; and a test incorrectly assumed reset retained an engine. Fixtures were copied while remaining excluded from APK assets. The regression is fixed, and reset assertions now require its existing null engine/volume-one defaults before selecting a working engine explicitly.

Xiaomi also exposed a Node prewarm bind timeout while plugin or instrumentation APKs were replaced after host startup. The driver installs supporting packages before the final host installation, and failures remain archived. No Node connection repair is claimed. Existing standalone desktop Node programs run directly because node --test injects Worker-incompatible process flags into those older fixtures; their assertions were not relaxed.

TTS remains dependent on installed engine/voice data and Android execution limits. It does not install models or establish a permanent foreground service. Muted playback completion is not acoustic quality/audibility acceptance. Audio progress storage is bounded and stalled consumption can fail with AUDIO_BACKPRESSURE. Bun retains its 1024-call, concurrency and JSON budgets; no new native payload, Android 9-12L or 16 KB device acceptance is claimed. AI, flow, mail, EPUB and speech recognition/model management remain separate audit work.

## Source, versions and restoration

Feature commit: b33f246bc54e7d36e6cfab4f6de0e4cf8010482b. Final paired-build source: 0880884c719d5557a3970ef11777b44ab51fa9e7. API support first appeared in the validated build 5603; concurrent commits subsequently exceeded the repository's build-count tolerance, so the host and Kit were advanced together to 5615. Host and generated-app cases were repeated on both devices after that alignment. Unchanged Node/Bun and Ace acceptance is retained with its exact artifact receipts; repeated rounds are not added to the 126-case count.

Final versions: host 6.8.0/build 5615, Node 1.24.0/build 272, Bun 0.13.0/build 272, declarations 4.71.0, Ace 2.33.0/build 312, Offline Docs 6.8.31/build 188, Builder 1.2.18/repository build 93 and Documentation revision 151. Kit: autojs6-runtime-v6.8.0+5615-0880884c719d5557a3970ef11777b44ab51fa9e7-arm64-v8a. Offline provenance is pinned to Documentation b4fb6998449a86dc32a43191166ce53611999ab0. The prior missing Speech release entry was synchronized into all ten documentation histories; the host's unrelated historical translation differences remain outside this batch.

Both devices' original APK hashes, permissions, volume streams, rotation, screen settings and relevant service settings are verified after restoration. Original media volume was zero. Tests use volume zero/media usage or non-playing file synthesis; no sustained tone was used. Owned installations, SDK copies, WAV outputs and device evidence files are removed, and all 13 original APK backups are deleted only after restored hashes match. Prior cleanup receipts retain the five Ace evidence removals.

9 desktop Worker fixture directories are archived under the Node repository's ignored .bak directory; the pre-existing worker-host-tests directory is preserved. Intermediate APK copies are pruned after their hashes are recorded. The isolated validation worktree is removed after preserving its source and tested artifacts. Primary-checkout release drafts and concurrent augment/memory work are preserved. Evidence, final checks and local commit references are retained under .bak/runtime-next-20261020. Nothing is pushed, tagged or publicly published.
