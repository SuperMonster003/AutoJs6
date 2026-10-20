# Runtime speech recognition and model management

This batch implements the speech facade for Node main threads, CJS/ESM/nested Workers and Bun. The 18 Rhino speech/models methods are covered by the shared facade, native plugin routes, runtime declarations and documentation. It uses the existing speech plugin for recognition, VAD, dictation, wake sessions, model management and synthesis.

Node declares speech; Bun grants speech.control independently and defaults it off. PCM views retain their visible byte range, use chunked transport and become host-owned temporary WAV files. Session events and model progress return to the originating JS thread. Operations support cancellation and deadlines; client/Worker/execution cleanup releases jobs, uploads and plugin bindings. Wake rounds buffer early events until the wake listener can attach its round listeners.

Bun speech streaming is exempt from the ordinary 1024-call lifetime quota. Grant, request/result size and concurrency checks remain. The native Bun binaries and compatibility matrix are unchanged. Models are not downloaded or licenses accepted implicitly.

## Validation checkpoint

Both Redmi bek749scrwv4wo8h (Android 13/API 33) and Xiaomi 968e9f18 (Android 15/API 35), arm64 with 4096-byte pages, pass the five RuntimeSpeechDeviceTest cases and two RuntimeSpeechNativeTest cases. File/PCM recognition and external dictation use the 6.625-second LibriSpeech fixture. Model tests import an owned archive, observe progress, change and restore the ASR default and remove the archive's model. Installation verifies already installed model files; no new model download or license acceptance is claimed.

Microphone and wake open/stop, CJS/ESM/nested Worker cleanup, independent Bun denial and over 1024 actual Bun streaming calls pass. Plugin synthesis writes validated WAV files, and playback uses volume zero/media usage. No sustained tone is played. Imported management fixtures are not inference models.

The initial full host JVM run failed only because the explicit automation route count still expected 150 instead of the eight new speech transport routes plus those 150. The updated 158-route expectation and full rerun pass. The first Xiaomi integrated class failed because the test treated the bundled VAD as an ordinary installed/default-change model; a missing custom-import languages field and cleanup ordering were also corrected. All failed logs remain separate; the full class passes after correction.

TypeScript 5.1.3 runtime checking and 743 Ace runtime queries pass. Final paired Runtime Kit, generated-application runs, plugin regressions, offline documentation, artifact-bound receipts and device restoration are still being completed. Evidence is retained locally under .bak/runtime-speech-20261020; this checkpoint does not claim those pending results or a public release.
