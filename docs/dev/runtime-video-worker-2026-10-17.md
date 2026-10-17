# Bun video recording and Node Worker utility APIs

Date: 2026-10-17 (Asia/Taipei). These are coordinated development sources and
locally verified APKs. No release, npm publication or push is part of this batch.

## Delivered behavior

Bun exposes `host.screenRecorder.start(path, options?)`, `stop()`, `cancel()` and
`getStatus()`. The independent `screen.record` grant defaults off and permits MP4
file output in the host UID. It also requires `screen.capture` and actual Android
consent through `host.images.requestScreenCapture()`. Image file permissions do
not bypass either grant.

The recorder produces silent H.264/MP4 with requested width, height, frameRate and
bitRate. Options are validated against the Android encoder. Output dimensions stay
fixed for a recording; Android scales and centers the screen when it rotates.
The existing VirtualDisplay switches between ImageReader and the encoder surface,
so Android 14+ consent is not reused to create another VirtualDisplay. This follows
the [Android MediaProjection contract](https://developer.android.com/media/grow/media-projection).
The producer is detached before the encoder is released, and screenshots resume
after stop/cancel.

Only a successful explicit stop publishes the MP4 from its temporary file. The
result contains the host path, bytes, duration, dimensions, codec and `audio:false`.
Existing destinations are rejected. Duplicate start, invalid options and capture
during recording reject; repeated stop returns null and repeated cancel returns
false. Execution exit, cancellation, capture stop and projection revocation discard
unfinished recordings. Normal bridge quotas/timeouts and Bun's execution timeout
remain unchanged. There is no microphone or system-audio track, simultaneous
screenshot stream, or mid-file resolution change.

Node CJS, ESM and nested Workers now import `pinyin`, `pinyin4j`, `console` and
`screen_metrics`, including their `autojs6:` aliases and `node:console`. Public
asynchronous calls reuse the parent wrappers. Numeric pinyin constants remain
numbers, callable entry points work, text coercion precedes structured clone, and
conversion matrices regain their asynchronous `compact()` method locally.
Coordinate bases belong to the execution and are shared with the parent and all
Workers. Console window controls target that execution, while ordinary output,
timers, custom Console streams and stack printing remain local to the Worker.
Rhino input/rawInput/build still fail explicitly.

Real-device tests found that the common pinyin option parser accepted only Kotlin
Int values. JSON/Rhino numbers represented as Double became the invalid style
name `0.0`. Mode/style now accept supported integer-valued Number representations
without truncating fractions, NaN, infinities or oversized values. Both shared
parser unit tests and the Android bridge regression cover the correction.

## Verification

| Physical device / suite | Final result | Time |
| --- | --- | --- |
| Redmi 22120RN86C, Android 13/API 33, arm64, 4096-byte pages: host capture/recording/Worker + pinyin bridge | 10/10 | 84.322 s |
| Xiaomi 23046RP50C, Android 15/API 35, arm64, 4096-byte pages: same host suites | 10/10 | 62.612 s |
| Redmi: Node npm ecosystem, Android conformance and WorkerDesktop | 44/44 | 140.326 s |
| Xiaomi: Bun runtime/Binder/lifecycle suite | 13/13 | 24.913 s |
| Xiaomi: Ace default API, runtime declarations, popup geometry and JSOX | 4/4 | 62.049 s |

Total: **81 passed physical tests** in the final validation matrix, excluding
diagnostic failures and superseded runs. Each Worker device test covers CJS, ESM
and nested Workers in portrait/landscape with 720/1280, 1080/1920 and 1920/1080
bases: 18 combinations per device. It checks real pinyin providers, numeric
constants, compact results, window controls, native stdout, coordinate results,
invalid numbers and shared once-only setters. Prior OpenCC/image, consent,
timeout, denied/revoked-grant and Worker-termination regressions also pass.

Video validation uses only an owned animated color fixture. It reads actual MP4
tracks and timestamps with MediaExtractor and decodes center pixels with
MediaMetadataRetriever, including a rotation during recording and a subsequent
landscape recording under the same consent. Final observed files:

| Device | Output | Bytes | Duration | Encoded samples |
| --- | --- | --- | --- | --- |
| Redmi | 192x320 portrait | 45,280 | 2,960 ms | 68 |
| Redmi | 320x192 landscape | 10,936 | 1,610 ms | 37 |
| Xiaomi | 192x320 portrait | 59,820 | 2,874 ms | 90 |
| Xiaomi | 320x192 landscape | 12,716 | 1,576 ms | 39 |

These sample counts are observations, not a constant-frame-rate guarantee. The
initial Redmi configuration was rejected by its encoder; final fixtures use
supported dimensions. The timestamp check was corrected to allow B-frame
presentation reordering in file decode order. Neither adjustment hides an encoder
failure or substitutes file existence for playable video.

Local checks: current NDK C++ syntax, Node/Bun/Ace Android builds, 96 Node / 59 Bun /
187 Ace JVM tests, 14 CoordinateScale and 2 pinyin parser JVM tests, actual embedded
Worker wrapper tests, 18 Bun SDK tests, TypeScript 5.1.3 and 6.0.3, 68 synchronized
declarations, 199 runtime Ace queries and 226 Rhino/JSOX queries. The host audit
still covers 91 mounts, 1351 exports and 1522 queried paths with zero failures.
These host counts are documentation/type/completion coverage, not complete
Node/Bun implementation parity. Documentation generation checks all 163 modules;
offline assets and provenance are synchronized and its 3 Python tests pass.

The host's cross-language changelog count check still reports the pre-existing
draft differences (12 feature, 9 fix and 6 improvement entries). This batch's
entries exist in every language, and all 22 generated host Markdown files match
their current sources. Unrelated source and changelog edits are preserved.

## Delivery and evidence

Related commits: Node `685f9342`, Bun `16fdcc2`, Documentation `51d19eb`,
Declarations `1d1a2bb` (4.48.0), Ace `b6cd92d` (2.10.0) and Offline Docs `3d12bb4`.
Node remains development 1.7.0/build 252, Bun 0.2.6/build 250, and the tested host is
6.8.0/versionCode 5555. Published runtime-kit snapshots and native Bun binaries
are unchanged; the development Node kit pins the newly built bridge binaries.

The host build uses the existing ignored `.bak/oi-check`, based on `74c602f62`
plus the exact scoped source/resource files. Concurrent file-manager changes in
the shared checkout are excluded. This directory is retained, consistent with the
previous batch's cleanup decision. Logs, installed-APK SHA-256 checks, per-device
JSON, restoration receipts and the reviewed Ace video popup are under ignored
`.bak/video-worker-20261017` directories. The host APK and matching test APK are
retained there as validation artifacts. Test videos and partial files were removed.
Both devices' original APK bytes and rotation settings are restored and verified;
temporary test-only packages are removed. Restoration APK copies are cleaned after
verification. No emulator or other connected physical device was operated.
