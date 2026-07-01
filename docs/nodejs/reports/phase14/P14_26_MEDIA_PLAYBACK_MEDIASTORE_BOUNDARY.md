# P14-26 Media Playback MediaStore Boundary

Status: partial.

P14-26 deploys the media playback and MediaStore boundary decision gate. It
imports P13-26 playback/MediaStore stable-denial evidence and P14-25 packaged
recorder/media stress evidence, then locks playback, playlists, scanFile,
MediaStore, broad media-library permissions, and raw media handles as
stable-denied/future-only for v1.2.

The current promoted media surface remains `media.getAudioStreamVolume()`,
`media.getAudioStreamMaxVolume()`, `media.getAudioStreamInfo()`,
`mediainfo.read()`, and `mediainfo.get()`. A future playback or MediaStore
provider must open a new scoped provider gate with exact capabilities,
ownership, permissions, privacy behavior, packaged evidence, rollback, and raw
object denial.

## Deployed

- Added `docs/nodejs/phase14/media-playback-mediastore-boundary-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_MEDIA_PLAYBACK_MEDIASTORE_BOUNDARY.md`.
- Added
  `tools/nodejs/phase14/verify-node-phase14-media-playback-mediastore-boundary.js`.
- Added Gradle gate `:app:verifyNodePhase14MediaPlaybackMediaStoreBoundary`.
- Imported P13-26 stable denial for audio playback, `MediaPlayer` lifecycle,
  playlists, playback focus, external MediaStore query/insert/update/delete,
  broad media-library permissions, mutable media collection access, and raw
  media objects.
- Recorded future-provider requirements for exact `media.playback`,
  `media.library`, and `media.library.mutate` style capabilities,
  owner-scoped handles, Android permission matrix, packaged evidence,
  Safe downgrade, and raw object denial.

## Open Future-Promotion Archives

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
