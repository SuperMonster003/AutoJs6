# P13-26 Media/Recorder/Mediainfo Provider

- Task: P13-26
- Status: partial
- Gate: `verifyNodePhase13MediaRecorderMediainfoProvider`
- Date: 2026-06-24

## Landed

- Added explicit media capability vocabulary:
  - `media`
  - `media.audio`
  - `media.metadata`
  - `media.recording`
- Added Node modules:
  - `require("media")`
  - `require("mediainfo")`
  - `require("recorder")`
- Added `autojs6:compat` aliases:
  - `compat.media`
  - `compat.mediainfo`
  - `compat.recorder`
- Added JSON-safe Android bridge methods:
  - `media.getAudioStreamVolume()`
  - `media.getAudioStreamMaxVolume()`
  - `media.getAudioStreamInfo()`
  - `mediainfo.read()`
  - `mediainfo.get()`
  - `recorder.getStatus()`
- `recorder.start()` and `recorder.stop()` stay fail-closed until a
  privacy-reviewed recorder provider exists.

## Boundary

- `mediainfo` accepts only relative paths inside the scoped Node working
  directory.
- `media` is read-only for this partial gate and exposes only AudioManager
  volume snapshots.
- `recorder` exposes status plus explicit capability and Android permission
  vocabulary, but does not start microphone capture.
- Packaged APK smoke coverage now exercises declared `media`, `media.audio`,
  `media.metadata`, and `media.recording` metadata; bundled media asset lookup
  through scoped `mediainfo`; read-only audio stream snapshots; recorder status;
  stable recorder start denial; and packaged exact-capability denial for
  missing `media.audio` / `media.metadata` / `media.recording`.
- Screen recording and internal/playback audio capture are stable-denied for
  P13-26 v1.1. They require a separate MediaProjection and Android playback
  capture policy gate with foreground disclosure, stop action, capture config,
  app opt-out handling, packaged metadata, and denial evidence before any
  runtime API is accepted.
- P13-26 v1.1 stable-denies audio playback, `MediaPlayer` lifecycle,
  playlists, and playback focus. The current `media` surface is read-only
  stream state, not a player provider.
- P13-26 v1.1 stable-denies external MediaStore query/insert/update/delete,
  broad media-library permissions, and mutable media collection access.
- No raw `MediaPlayer`, `AudioManager`, `MediaRecorder`,
  `MediaMetadataRetriever`, `MediaInfo`, `MediaStore` cursor, `Uri`, file
  descriptor, or `Context` object crosses the bridge.

## Evidence

- Runtime protocol:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProtocol.kt`
- Permission manifest:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgePermissionManifest.kt`
- Provider registry:
  `app/src/main/java/org/autojs/autojs/engine/NodeBridgeProviderRegistry.kt`
- Embedded JS facade:
  `app/src/main/cpp/node_bridge_sources.cpp`
- Instrumentation:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeMediaBridgeInstrumentationTest.kt`
- Packaged APK smoke:
  `EmbeddedNodePackagedApkSmokeTest#installAndRunMediaRecorderBridgeEmbeddedNodePackagedApks`
- Conformance:
  `app/src/androidTest/java/org/autojs/autojs/engine/NodeBridgeConformanceInstrumentationTest.kt`
- Types:
  `docs/nodejs/types/autojs6-node/media.d.ts`,
  `docs/nodejs/types/autojs6-node/mediainfo.d.ts`,
  `docs/nodejs/types/autojs6-node/recorder.d.ts`
- Host verifier:
  `tools/nodejs/phase13/verify-media-recorder-mediainfo-provider.js`
- Gradle gate:
  `:app:verifyNodePhase13MediaRecorderMediainfoProvider`

## Privacy And Migration Evidence

- `docs/nodejs/security/PRIVACY_DISCLOSURE_POLICY.md` records
  `media_recording` / `media.recording` as a sensitive Pro surface requiring
  user prompt, foreground disclosure, `RECORD_AUDIO`, and microphone foreground
  service review before any real recording provider is promoted.
- `sample/nodejs/pro-parity-suite/snippets/media-recorder.cjs` documents the
  migration path from Rhino-style media/recorder usage to exact-capability Node
  calls: read-only `media.getAudioStreamInfo()`, scoped `mediainfo.read()`,
  `recorder.getStatus()`, and fail-closed `recorder.start()`.
- The Pro example catalog keeps this snippet outside the default runtime smoke
  subset, so it remains migration evidence and does not imply microphone
  permission, foreground disclosure, or recorder session promotion.

## Remaining Blockers

- Real recorder sessions with runtime permission flow, foreground disclosure,
  resource handles, cleanup, and cancellation.
- Broader real audio/video corpus evidence.

The compat/provider registry, process permission, docs, project tooling,
surface manifest, and Gradle gate are evidence-only for this partial decision.
They do not add separate P13-26 blockers beyond real recording,
foreground disclosure, cleanup, and broader media corpus evidence above.
