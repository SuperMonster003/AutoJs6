# Node main-thread and Worker screen recording

Date: 2026-10-17 (Asia/Taipei). This coordinated source update closes the Node
recording gap left after the Bun audio/concurrent-capture delivery. It does not
claim full Rhino API parity or publication of a new runtime release.

## Delivered behavior

- `screen_recorder` and `autojs6:screen_recorder` expose start(path, options?),
  stop(options?), cancel(options?) and getStatus(options?) in the main thread and
  real CJS/ESM/nested Workers. Video is H.264 MP4; audio is none (default, also
  false), microphone, playback or mixed. AAC uses 48 kHz mono and a 128 kbit/s
  target. Width/height are fixed for the video while screenshots follow capture
  orientation and dimensions. Start rejects existing output files, stop finalizes
  output, and cancel/exit discard partial files.
- The host checks screen_capture for all recording operations. Audio start also
  requires media and media.recording plus Android RECORD_AUDIO; raw bridge calls
  cannot omit the capability requirements. Android MediaProjection consent,
  playback capture policy and microphone foreground-service requirements remain
  authoritative. Playback/mixed require Android 10+.
- Main and Workers share the execution's recording. Optional stop/cancel {id}
  returns null/false for a stale ID. Worker exit cancels its and its descendants'
  unsaved screen recordings without cancelling a later replacement. An aborted
  public start receives the native recording ID before discarding the result.
- Worker media_projection returns a local callable ScreenCapturer with nextImage
  and stop, avoiding function values in structured clone. Worker recorder exposes
  the pre-existing audio-only start/getStatus/stop API. Audio-only recording keeps
  its existing execution/max-duration lifetime; it does not adopt screen recording's
  Worker-exit rule. No simultaneous microphone-input guarantee is added.
- `images.captureScreen` now sends no consent-only options to the screenshot
  encoder. Both devices reproduced the old invalid-option error for
  requireExistingPermission. Request-time orientation still configures the
  session; legacy capture-time fields remain accepted without changing it.

The implementation reuses the existing host projection, GL frame fan-out, AVC/AAC
encoders and muxer. Bun's runtime/SDK source and native runtime binaries are unchanged.

## Physical validation

| Device / suite | Passed | Test time |
| --- | --- | --- |
| Redmi 22120RN86C, Android 13/API 33, arm64, 4096-byte pages: Node recording suite | 6 | 113.087 s |
| Xiaomi 23046RP50C, Android 15/API 35, arm64, 4096-byte pages: Node recording suite | 6 | 77.474 s |
| Redmi: separate RECORD_AUDIO-denied suite | 1 | 6.584 s |
| Xiaomi: separate RECORD_AUDIO-denied suite | 1 | 4.439 s |
| Redmi: complete NpmEcosystemSmokeTest, NodeRuntimePluginAndroidConformanceTest, WorkerDesktopSmokeTest | 44 | 138.629 s |
| Xiaomi: same complete Node runtime classes | 44 | 50.688 s |
| Redmi: existing Bun recording/audio suite | 4 | 46.924 s |
| Xiaomi: Ace default APIs, runtime popups and JSOX activation/removal | 4 | 60.499 s |
| Final Node metadata APK: main ESM export and raw capability rejection, one case on each device | 2 | 1.811 / 1.026 s |

All **112 tests** pass. Diagnostic failures and superseded runs are excluded.
The final runtime-kit refresh changes packaged metadata and its generated
BuildConfig; all native library entries remain byte-identical to those exercised
by the full device matrix. The final APK is installed, hash-verified and checked
by the last two cases. Release bridge payload metadata uses freshly stripped
outputs for all three ABIs; published release snapshots are untouched.

Each device records 12 Worker movies: CJS/ESM/nested times four audio sources.
The Worker takes 36 screenshots; the main thread concurrently takes 109 on Redmi
and 96 on Xiaomi. The matrix crosses portrait and landscape. Checks inspect video
frames, track count, dimensions and decoded AAC tone data, and also exercise a
Worker-started audio-only recording stopped by the parent.

Main-thread tests separately check ten four-quadrant screenshots across rotation,
microphone/mixed recordings, notification stop, source opt-out and file cleanup.
Decoded playback under ALLOW_CAPTURE_BY_NONE has RMS and 660 Hz amplitude zero
on both devices. The calibrated video-minus-audio onset is +32.0 ms on Redmi and
+55.6 ms on Xiaomi, within the existing 150 ms bound. This is an owned test fixture
with 40 ms video inspection steps, not a precision latency benchmark.

The Worker matrix fixture was adjusted to allow time for six real recordings
before its rotation checkpoint and to keep result JSON below the console's
per-line limit. The raw-permission case now expects the existing
ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED code. Ace's assertion accepts the
qualified ActiveRecording return type actually displayed by TypeScript. These
fixture corrections did not relax media, image or capability assertions.

## Coverage and local checks

- Actual embedded Node wrapper tests pass for recording options, main/CJS/ESM/
  nested modules, callable capture handles, stale IDs, abort/startup/exit cleanup,
  image dispatch, OpenCC, pinyin, console and coordinates.
- The cross-repository bridge check compares all ten Worker modules and checks
  screen_recorder exports/catalog records against the host and Bun method tables.
- Node's current NDK compile command passes -fsyntax-only. Android builds and
  relevant JVM tests pass: Node 96, Ace 187, Offline Docs 2, host PCM mixing 1.
  Node example and TypeScript smoke Gradle tasks pass. The 3 ABI release bridge
  payload sizes, SHA-256 values and build IDs are refreshed from current outputs.
- All 69 copied declaration files match canonical sources. TypeScript 5.1.3 and
  6.0.3 pass strict checks, including recording result types, capture handles,
  canonical aliases and negative audio/ID cases.
- Ace's full runtime verifier passes, including 213 runtime API queries. Physical
  popups show start's Promise return type, audio source options and typed movie
  fields with normal provider budgets and default settings.
- Host coverage still passes 91 mounts, 1351 explicit exports and 1522 queried
  paths, plus 8 extractor tests. These counts describe docs/types/completion,
  not implementation of every Rhino API in Node or Bun.
- Documentation generation checks all 163 modules. Offline normalization, its
  3 Python tests, Node's 2 Python tests and generated Markdown checks pass.
  All new release entries cover ten languages. The host's 22 generated Markdown
  files match their current sources; the pre-existing translation-count gaps
  and five unrelated Simplified Chinese draft files are preserved.

## Delivery and restoration

| Repository | Version / commit |
| --- | --- |
| Host | 6.8.0, build 3906, versionCode 5558, this commit |
| Node Runtime | development 1.7.0/build 253, `2cfb0fb3` |
| Documentation | 6.8.0/code 127, `bd95e54` |
| TypeScript Declarations | 4.50.0, `b2124ce` |
| Ace Editor | 2.12.0/build 287, `2c2a095` |
| Offline Docs | 6.8.7/build 158, `8eba678` |

The host is built from e8f8edb7a plus explicit owned source/test files in the
existing ignored `.bak/oi-check`. Logs, numeric evidence, Ace popup screenshots,
installed APK hashes and restoration receipts are under
`.bak/node-recording-20261017`. Only the two named devices are operated. Their
changed APKs, RECORD_AUDIO permission and flags, media volume and rotation
settings are restored and verified; no MediaProjection remains. Test recordings
and partial files are deleted by the fixtures. No microphone recordings are
retained. Temporary restoration APK copies are removed after verification;
validation artifacts and the existing build directory remain under `.bak`.

No push, tag, npm publication or release is performed. Remaining audit areas
include Android-object helpers, calendar/IntentTask parity, complete image/UI
surfaces and large AI/flow/Compose/TTS/mail/EPUB modules. They still require their
own implementation and verification; this batch does not mark them complete.
