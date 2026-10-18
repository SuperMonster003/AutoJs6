# WebView video and foreground audio capture

Date: 2026-10-18 (Asia/Taipei). Local development candidates, not a release.

This batch closes the audited WebView video/audio file-capture gap in Node.js,
Bun and Node CJS/ESM/nested Workers. It extends the existing chooseFiles bridge;
the generic portable facades already forward these options, so no duplicate
runtime implementation or Rhino delegation is introduced.

## Behavior

- captureEnabled with a single image/video/audio MIME family selects capture.
  captureKind explicitly selects image, video or audio for mixed/wildcard MIME
  preferences. Save and directory modes continue to use SAF.
- Video uses ACTION_VIDEO_CAPTURE and a private output URI. videoQuality accepts
  0/1; durationLimitSeconds and sizeLimitBytes are sent as Android camera hints.
  Cameras may interpret hints differently. A readable result URI is copied when
  a camera does not write the provided destination; its external original is
  retained. Media validation and fallback copying run off the UI thread.
- Neither physical device offers the standard RECORD_SOUND action. Audio uses
  an explicit native foreground UI with Start, Stop, Use recording and Cancel.
  It produces AAC/M4A, 48 kHz mono at the requested 128 kbit/s. acceptTypes does
  not select a different audio encoder. The microphone opens only after Start.
  The application must declare RECORD_AUDIO; the UI requests a missing grant,
  and denial returns null. No background recording service is started.
- Rotation reattaches the same recorder UI. Leaving the foreground stops the
  recorder and retains the unconfirmed result for Use/Cancel after returning.
  Native duration/file-size notifications stop recording without silently
  accepting it. Limits can include final frame/container overhead.
- Cancellation, navigation, window closure, timeout and Worker exit release
  pending capture resources. Confirmed URIs use the existing View/transfer
  reference counts and can be read with readDocument or copied with download.
  A parent-owned View remains alive during the Worker-exit cleanup test.
- chooseFiles.timeoutMs accepts 100..2147483647 ms and defaults to 120000.
  callbackTimeoutMs now accepts that range too; its existing 15000 default, or
  120000 for file selection, is preserved. The configured file-callback timeout
  also reaches the default native picker and the event's timeoutMs field.
  Overall execution and bridge budgets remain independent.
- A null native filenameHint now correctly falls back to download in save mode,
  rather than relying on platform JSONObject string coercion.

## Documentation, types and completion

Both runtime declaration sources, the distributed declarations and all 70 Ace
assets are synchronized. New option types include captureKind, videoQuality,
durationLimitSeconds, sizeLimitBytes and chooser timeoutMs. Invalid capture kinds
and quality values are rejected by the strict smoke fixtures. Real Node/Bun
facade tests verify exact option forwarding, including CJS/ESM/nested Workers
and pending-picker cancellation.

The host's new capture UI has matching default/explicit English and all ten
languages, sorted resource names and ASCII punctuation. Documentation, offline
HTML/search assets, runtime guides and release notes are updated. Offline source
provenance points to `19213eb5aeb3845a65f6bcb7c3e352b24022ac97`.

## Physical validation

| Device | Android/API | ABI | Page size |
|---|---|---|---|
| Redmi 22120RN86C | 13 / 33 | arm64-v8a | 4096 bytes |
| Xiaomi 23046RP50C | 15 / 35 | arm64-v8a | 4096 bytes |

171 selected passing instrumentation cases were executed. Per device: 7 new media
cases, 1 microphone-denial case, 5 prior file-capture/download cases, 7 prior
WebView client cases, 44 Node runtime cases, 13 Bun runtime cases, and 6 APK
production/execution cases. Xiaomi additionally runs 5 Ace cases. Failed
development attempts and repeats are not added to that selected count.

The media suites exercise six import/Worker paths for video and audio. They
operate the real system camera and native recording controls, upload actual
media through HTML file inputs, compare complete payload SHA-256, check header
and tail reads, inspect media tracks and decode real video frames/AAC samples.
Audio is checked against a short owned 660 Hz test tone, with original media
volume restored afterward. Only numeric decoding evidence is retained.

Separate cases cover cancellation, rotation during recording, background stop
and foreground confirmation, automatic duration/size stops, picker timeout,
active nested-Worker capture termination with the parent View still open,
microphone denial through Node/Bun, and temporary-file release. Bun also covers
the default HTML picker without a custom Chrome client. Existing image capture,
SAF, blob/data, authentication/dialog and Worker callback checks still pass.

## Packaged applications

Six genuine APK Builder outputs cover Node main-thread, nested Worker and Bun
on both devices. They use application versionCode 1 and runtimeVersionCode 5567.
Each performs real video/audio capture, native URI header/tail reads, full upload
digest validation, media decoding, content-URI copying to a managed download,
download removal, capture expiry after View disposal, and Compose interaction
across rotation. Packaged audio uses an explicitly declared Android microphone
permission, including its first grant dialog. The Node main and Worker variants
reuse the task-only Node application identity in successive installs.

All six outputs have valid signatures independent of the host certificate, and
their complete runtime DEX sets match the final Runtime Kit template. Installation
receipts bind each APK's actual device hash, including the replaced Node-main APKs.

## Local validation

| JVM suite | Cases | Existing skips |
|---|---:|---:|
| AutoJs6 | 4717 | 26 |
| AutoJs6-Plugin-NodeJs-Runtime | 96 | 0 |
| AutoJs6-Plugin-Bun-Runtime | 59 | 0 |
| AutoJs6-Plugin-Ace-Editor | 187 | 0 |
| AutoJs6-Plugin-APK-Builder-Template | 39 | 0 |
| AutoJs6-Plugin-Offline-Docs | 2 | 0 |

All JVM suites have zero failures/errors. Host app/test APKs and the real Runtime
Kit, Node/Bun/Ace/Builder debug/test APKs, and Offline Docs debug/release content
gates pass. Additional checks include strict TypeScript 5.1.3/6.0.3, the Node
wrapper/Worker checks, 27 Bun JS client tests, 38 Bun Python tests, the unchanged
Bun compatibility matrix, 387 semantic Ace queries, document freshness and
related Markdown generators. The existing full coverage gate passes 1351 explicit
host exports and 1522 queried paths; this is a documentation/type/completion
measurement, not 1351 implemented Node/Bun APIs.

The new Ace device popups check videoQuality and durationLimitSeconds alongside
the prior default-runtime and opt-in JSOX checks. Editor preferences and device
rotation are restored after that suite.

## Acceptance corrections and evidence

The initial Redmi default video run timed out while waiting for confirmation.
The final driver waits for recording/confirmation state instead of assuming its
first tap started recording, and complete reruns pass. MIUI exposes different
English/Chinese denial and while-in-use buttons; the permission fixture now
handles the observed controls. The initial interrupted Bun run and its poisoned
Binder lane are retained as failed development evidence, not treated as passes.
Subsequent complete suites pass in fresh instrumentation processes.

Local evidence is under `.bak/webview-media-20261018`: evidence.json, per-device
results and installation receipts, tested APKs, certificates.json, cleanup and
restoration records, and scoped commits. The Runtime Kit records source base
`d14aa91cd056ee3502dba60eb2ef56fab72f41e1`; candidate source hashes identify the
local changes on top of that base.

Candidate versions: host 6.8.0/build 3915 (Android 5567), Node 1.13.0/build 261,
Bun 0.2.12/build 259, declarations 4.58.0, Ace 2.20.0/build 295, documentation
6.8.0/revision 135, Offline Docs 6.8.15/build 166, and APK Builder 1.2.5/build 80.

No recorded media is exported to the PC. Owned media/document/download fixtures
are removed; original APKs are restored with exact SHA-256 checks. Original
microphone/camera permission flags, volume and rotation are restored; the system
camera application's grants remain unchanged. The five original host prose
drafts remain outside the commits. Temporary local material is kept under each
repository's ignored .bak directory; verified original backup APK copies are
removed after restoration. No push, tag or release is performed.

## Scope limits

These are file-capture APIs, distinct from web-page getUserMedia/MediaRecorder
streaming and screen recording. Camera behavior, exact video codec/resolution,
and interpretation of capture hints remain device-dependent. Arbitrary audio
formats, automatic/background microphone recording, process-death recovery,
all OEMs, 16 KiB devices and the full release matrix are not certified here.
Bun's native payload, experimental freeze and published compatibility status
remain unchanged. The broader runtime audit still has separate remaining gaps.
