# Bun recording audio and concurrent screenshots

Date: 2026-10-17 (Asia/Taipei). This batch supersedes the silent-only and
no-concurrent-screenshot limitations recorded in the previous video batch.
It is coordinated development source, not a published release or full Rhino parity.

## Public behavior

`host.screenRecorder.start(path, options)` accepts `audio: "none"`, `"microphone"`,
`"playback"` or `"mixed"`. Omission and false keep silent recording. The microphone
and playback modes require separate, default-off `screen.record.microphone` and
`screen.record.playback` grants; mixed requires both. All audio modes also require
Android RECORD_AUDIO, in addition to the existing screen.capture/screen.record
grants and actual MediaProjection consent. Missing grants/permission fail explicitly.

Audio uses AAC-LC, 48 kHz mono and a 128 kbit/s target; mixed uses equal gain for
each PCM source. Video uses an AVC surface encoder. Both streams use a monotonic
clock and one streaming MediaMuxer, without a file-sized remux on stop. Results
include audio/audioSource and nullable audioCodec/audioSampleRate/audioChannels.
The audio boolean describes track presence, not whether Android supplied audible
content. Playback capture includes eligible MEDIA/GAME/UNKNOWN playback, respects
the source application's policy and user-profile boundary, and does not bypass
privacy controls. See [Android audio capture](https://developer.android.com/media/platform/av-capture).

Microphone modes use the existing microphone foreground service and its stop
action, with an engine-neutral localized script-recording notification. Android's
[while-in-use foreground-service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone)
still apply. The user grants RECORD_AUDIO in Android app permissions; this API does
not silently grant it or fall back to a different audio source.

`host.images.captureScreen()` remains available during recording. One projection
input is rendered to the fixed-size video surface and read back for independent
image handles. Screenshot dimensions follow the capture session rather than the
video size. Rotation replaces the input SurfaceTexture/buffer queue on the same
VirtualDisplay, preserving the recording and consent. Existing file protection,
explicit stop/publish, cancellation and execution-owned cleanup remain in place.
Large/frequent screenshot readbacks consume graphics time; frameRate is a target,
not a constant-rate guarantee. Audio/video format customization beyond the
documented options and protected audio capture are not claimed.

## Corrections found during verification

- Reusing a resized SurfaceTexture retained cropped input on the Redmi and stopped
  producing screenshot frames on the Xiaomi. Detaching and attaching the same
  Surface did not fix it. A fresh consumer buffer queue on the existing display
  resolves both cases; four-quadrant pixels and dimensions pass after rotation.
- A plain screenshot regression observed transparent pixels after rotation. The
  capture path now skips fully cleared transparent buffers within its existing
  bounded retry budget. A device bitmap test verifies that opaque black frames
  and partially transparent frames are retained.
- Ace completed the new audio property but showed only `declare,optional` in its
  popup. Completion details now resolve property types as well as callable
  signatures. Property completion does not insert function-call syntax, and the
  operation budgets remain unchanged.

## Physical validation

| Device / suite | Result | Time |
| --- | --- | --- |
| Redmi 22120RN86C, API 33, arm64, 4096-byte pages: capture/Worker and recording-audio suites | 13/13 | 121.877 s |
| Xiaomi 23046RP50C, API 35, arm64, 4096-byte pages: same host suites | 13/13 | 90.381 s |
| Redmi: separate RECORD_AUDIO-denied suite | 1/1 | 4.273 s |
| Xiaomi: separate RECORD_AUDIO-denied suite | 1/1 | 5.567 s |
| Redmi: Bun runtime/Binder/lifecycle suite | 13/13 | 29.001 s |
| Xiaomi: Ace default API, runtime popups and JSOX suite | 4/4 | 63.034 s |

The main matrix passes **45 tests**. Diagnostic failures and superseded runs are
not included. A notification-label-only rebuild additionally passes the existing
microphone/mixed/notification test on both devices (16.004/13.260 s). The final
calibrated A/V test is also run separately on both devices (22.753/15.834 s).

Each parallel-capture case checks ten full-size screenshots, including rotation,
four distinct corner colors, a changing center pulse and subsequent MP4 decoding.
Microphone and mixed recordings contain a real 660 Hz fixture signal; notification
stop and execution cancellation remove temporary files, foreground disclosure,
projection ownership and the three recording threads. Raw calls cannot bypass
audio grants. Android permission rejection leaves screenshots usable. Source
ALLOW_CAPTURE_BY_NONE yields a valid AAC track with decoded RMS and tone amplitude
both zero on both devices.

The test tone's AudioTrack has its own output buffering. Initial uncalibrated
control-to-recording observations included that delay. The final fixture uses
AudioTrack presentation timestamps before changing the visual pulse, following
the [AudioTrack timestamp contract](https://developer.android.com/reference/android/media/AudioTrack#getTimestamp(android.media.AudioTimestamp)).
No production audio timestamp was shifted to make the fixture pass.

| Device | Observed fixture output buffer | Decoded video onset minus audio onset |
| --- | --- | --- |
| Redmi | 106.4 ms | -5.3 ms |
| Xiaomi | 257.9 ms | +13.3 ms |

These are fixture observations under a 150 ms acceptance bound, not a high-precision
latency benchmark: video inspection uses 40 ms steps and audio uses AAC blocks.
Only numeric evidence and owned UI screenshots are retained. All recorded video,
microphone data and partial files are deleted after validation.

## Local checks and delivery

Android host/Bun/Ace builds pass, as do the PCM mixing regression, 59 Bun and 187
Ace JVM tests, 19 Bun SDK tests, TypeScript 5.1.3 and 6.0.3 checks and 68 copied
declaration checks. Ace's complete LSP runtime verifier passes, including 202
runtime API queries and 226 Rhino/JSOX queries. The host audit still reports 91
mounts, 1351 exports and 1522 queried paths with zero failures. Documentation
generation checks all 163 modules, and Offline Docs normalization and 3 Python
tests pass. These source/type/completion counts do not claim full runtime parity.

Documentation is `5b4a454`, Bun is `ed97f5c`, TypeScript Declarations is `db103ac`
(4.49.0), Ace is `29c46ae` (2.11.0), and Offline Docs is `6f1aab8`. Bun stays on
development 0.2.6/build 251; the tested host is 6.8.0/versionCode 5557. Native Bun
bytes and bridge budgets are unchanged. Node Runtime source is unchanged.

The host was built from `bd522f8a7` plus the scoped files in the existing ignored
`.bak/oi-check`. Logs, per-device JSON, installed-APK SHA-256 checks, screenshots
and restoration receipts are under `.bak/audio-capture-20261017`. The validation
APK and matching test APK are retained separately from restoration backups.
Both devices' changed APKs, recording permission/flags, media volume and rotation
settings are restored and verified. Backup APK copies are removed after checks.
The existing build directory is retained; no other device or emulator is operated.

The host's pre-existing cross-language changelog count differences remain. This
batch's entries cover all ten languages, and the 22 generated host Markdown files
match their current sources. Original unrelated draft edits are preserved. No
push, npm publication, tag or release is performed.
