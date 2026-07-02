# Phase 14 Media Playback MediaStore Boundary

Status: partial.

P14-26 records the v1.2 boundary decision for media playback, playlists,
`scanFile`, MediaStore, and broad media-library permissions. It imports P13-26
media/recorder/mediainfo evidence and the P14-25 packaged recorder/media stress
gate. The current promoted media surface remains read-only audio-stream status
and scoped `mediainfo` metadata only.

This gate keeps playback, playlists, media scanning, external MediaStore access,
MediaStore mutation, raw media handles, raw file descriptors, and broad shared
storage permissions stable-denied for v1.2. A future scoped provider must open a
new gate with exact capabilities, ownership rules, Android permission matrix,
privacy behavior, packaged positive/denial evidence, rollback, and raw object
denial before any of those surfaces can be promoted.

## Current Allowed Surface

- `media.getAudioStreamVolume()`
- `media.getAudioStreamMaxVolume()`
- `media.getAudioStreamInfo()`
- `mediainfo.read()`
- `mediainfo.get()`

## Stable-Denied Boundaries

- Playback lifecycle: `media.play`, `pause`, `resume`, `stop`, `seek`, volume,
  looping, and raw `MediaPlayer`/`AudioTrack` access.
- Playlist and queue APIs.
- `media.scanFile` and media scanner integration.
- MediaStore query/insert/update/delete/open-file access.
- Broad `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE`, and shared-storage roots.
- Raw `ContentResolver`, `MediaStore` cursor, `Uri`, `ParcelFileDescriptor`,
  file descriptor, playlist handle, `Context`, and Java objects.

## Open Archives

- `media_playback_scoped_provider_decision_archive`
- `media_playlist_queue_denial_archive`
- `mediastore_scoped_query_mutation_decision_archive`
- `broad_media_library_permission_denial_archive`
- `packaged_playback_mediastore_denial_archive`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-media-playback-mediastore-boundary.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14MediaPlaybackMediaStoreBoundary
```

Expected reports:

```text
build/reports/nodejs/phase14-media-playback-mediastore-boundary.json
build/reports/nodejs/phase14-media-playback-mediastore-boundary.md
```
