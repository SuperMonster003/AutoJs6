# Phase 15 MediaStore Playback Decision

Status: ready.

P15-21 records the v1.3 decision for scoped MediaStore and playback promotion:
keep the surface stable-denied. The current allowed media surface remains
read-only audio-stream status plus scoped `mediainfo` metadata. This gate does
not promote playback, playlists, `scanFile`, MediaStore query/mutation, broad
media-library permissions, or raw media handles.

## Decision

- Result: `keep_stable_denied`.
- Current allowed calls: `media.getAudioStreamVolume()`,
  `media.getAudioStreamMaxVolume()`, `media.getAudioStreamInfo()`,
  `mediainfo.read()`, and `mediainfo.get()`.
- Denied surfaces: playback lifecycle, playlists/queues, `media.scanFile`,
  MediaStore query/insert/update/delete/open-file, broad `READ_MEDIA_*`,
  `MANAGE_EXTERNAL_STORAGE`, broad shared-storage roots, and raw media handles.
- Future promotion rule: any playback or MediaStore provider must open a new
  scoped provider gate with exact capabilities, ownership, Android permission
  matrix, user-visible privacy behavior, packaged positive/denial evidence,
  rollback, Safe downgrade, and raw-object denial coverage.

## Imported Evidence

- `phase14_mediastore_playback_boundary`: P14-26 imports P13-26 playback and
  MediaStore stable-denial evidence and keeps the media surface read-only.
- `phase15_packaged_recorder_dependency_gate`: P15-20 keeps packaged
  recorder/media stress partial and unpromoted before this boundary decision.
- `phase15_promotion_contracts`: `mediastore_playback` is `stable_denied`
  until all promotion evidence bars are satisfied by a future scoped provider.
- `phase15_disclosure_rollback_contract`: `mediastore_playback` declares
  denial diagnostics, provider-disable behavior, disclosure, rollback, Safe
  downgrade, and crash circuit breaker requirements.
- `phase15_surface_manifest_mapping`: P15-21 is owned by
  `pro.tasks_device_media` with `boundary_decision_archive` evidence.

## Future Scoped-Provider Archives

- `media_playback_scoped_provider_decision_archive`
- `media_playlist_queue_denial_archive`
- `mediastore_scoped_query_mutation_decision_archive`
- `broad_media_library_permission_denial_archive`
- `packaged_playback_mediastore_denial_archive`
- `mediastore_playback_safe_downgrade_archive`
- `mediastore_playback_raw_object_denial_security_archive`
- `phase15_task_device_media_doc_sync`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-mediastore-playback-decision.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15MediaStorePlaybackDecision
```

Expected reports:

```text
build/reports/nodejs/phase15-mediastore-playback-decision.json
build/reports/nodejs/phase15-mediastore-playback-decision.md
```
