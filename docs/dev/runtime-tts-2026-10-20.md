# Node.js, Bun and Worker TTS, 2026-10-20

This batch adds tts/autojs6:tts, the explicit $autojs.tts compatibility member and Bun host.tts. All 31 registered Rhino TTS method names and four constants have counterparts. The complete shared facade has 40 members including client closure and listener helpers. This is not a claim of full inherited Rhino Java/EventEmitter behavior or complete runtime parity.

## Delivered behavior

The clients reuse TtsService's Android backend, queue, splitting, deadlines and atomic file publication. Each client owns its defaults and services. Observable operations return a Promise of a task with result, state, isDone, stop/cancel and typed events. Audio data is Uint8Array. Event drains select only handles already received by JavaScript, retaining replies for concurrent creations. Results settle before terminal listeners run, so closing from a done listener preserves success. Worker exit closes its clients while parent tasks remain usable.

An internal adapter preserves Rhino's public object shape. A regression test reproduces and prevents a synthetic interface getter turning utterance.isDone from a boolean into a function. Engine enumeration now reads the installed Android engine directory without initializing a default engine; explicit ready/voice/synthesis calls still validate their selected engine. Relative output paths use the host execution directory, falling back to application files when it is empty.

Bun uses a separate default-off tts.control grant and packaged-project declarations; Node projects declare tts. No microphone or audio-settings permission is added. Existing Bun call/concurrency/JSON budgets remain unchanged. Event storage is bounded, with AUDIO_BACKPRESSURE for stalled audio consumption.

## Verification checkpoint

Redmi 22120RN86C (Android 13, bek749scrwv4wo8h) and Xiaomi 23046RP50C (Android 15, 968e9f18) both pass all six RuntimeTtsDeviceTest cases. Accepted receipts use com.xiaomi.mibrain.speech, voice zh, and real 68822-byte WAV files. Checks include RIFF/WAVE headers, relative output, typed audio, silent speech, cancellation, reset defaults, grant denial, delayed-handle events and CJS/ESM/nested Worker cleanup while parent silence completes promptly. The installed 3-Macaw Speech provider is discoverable but is not counted as synthesis acceptance.

The isolated host suite discovers 4762 JVM tests, with zero failures/errors and 26 existing skips. XML reports are archived. The host coverage gate reports 1356 explicit exports and 1589 paths with no failures; these are documentation/type/completion counts, not implemented Node/Bun API totals. Runtime declaration synchronization covers 74 files.

Retained failures include missing ignored obsolete-module fixtures in the fresh checkout, the reproduced Rhino property regression, default-engine discovery failures, an incorrect test assumption that reset retained its engine, and a Node prewarm bind timeout during APK replacement. The fixtures were copied while remaining excluded from APK assets; reset now explicitly asserts the existing defaults, and runtime plugins install before the host. Failed classes are not counted as passes. Standalone desktop Node programs run directly because node --test injects invalid Worker flags into those existing fixtures.

Runtime/plugin regression, final Ace popup checks, paired Runtime Kit generation, packaged-app verification, device restoration and cleanup remain pending at this checkpoint. Final receipts and counts will replace this checkpoint after validation.

## Source and remaining scope

Prepared versions: host 6.8.0/build 5603, Node 1.24.0/build 272, Bun 0.13.0/build 272, declarations 4.71.0, Ace 2.33.0/build 312, Offline Docs 6.8.31/build 188, Builder 1.2.18/build 93 and Documentation revision 151. Evidence is retained in the primary host's ignored .bak/runtime-next-20261020 directory. An isolated AutoJs6-runtime-tts worktree preserves concurrent augment edits.

Speech tests use volume zero and media usage or file synthesis without playback. No sustained tone is used. AI, flow, mail, EPUB, speech recognition/model management and additional specialized APIs remain separate audit work. Model installation and new Bun native/ABI acceptance are outside this batch.
