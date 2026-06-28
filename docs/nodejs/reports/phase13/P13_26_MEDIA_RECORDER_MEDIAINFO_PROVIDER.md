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
- Screen recording and internal/playback audio capture are stable-denied for
  P13-26 v1.1. They require a separate MediaProjection and Android playback
  capture policy gate with foreground disclosure, stop action, capture config,
  app opt-out handling, packaged metadata, and denial evidence before any
  runtime API is accepted.
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

## Remaining Blockers

- Audio playback, MediaPlayer lifecycle, playlists, and audio-focus behavior.
- Real recorder sessions with runtime permission flow, foreground disclosure,
  resource handles, cleanup, cancellation, and packaged smoke.
- MediaStore query/insert/update/delete policy and Android 13+ read media
  permission matrix.
- Packaged media asset lookup, privacy copy, migration examples, and broader
  media corpus evidence.

The compat/provider registry, process permission, docs, project tooling,
surface manifest, and Gradle gate are evidence-only for this partial decision.
They do not add separate P13-26 blockers beyond playback lifecycle, MediaStore
policy, real recording, packaged media/recorder smoke, privacy copy, migration
examples, and broader media corpus evidence above.
