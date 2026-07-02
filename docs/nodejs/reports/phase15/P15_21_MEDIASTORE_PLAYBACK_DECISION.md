# P15-21 MediaStore Playback Decision

Status: ready.

P15-21 closes the scoped MediaStore and playback promotion boundary for v1.3
by choosing stable denial. It imports the P14-26 boundary report, the P15-20
packaged recorder dependency gate, and the Phase 15 promotion/disclosure
contracts, then verifies that runtime authority remains unchanged.

The current media surface stays limited to read-only audio-stream status and
scoped `mediainfo` metadata. Playback lifecycle, playlist/queue APIs,
`scanFile`, MediaStore query/insert/update/delete/open-file, broad
media-library permissions, mutable media collection access, raw Android media
objects, raw file descriptors, `Uri`, `Context`, and Java objects remain
denied unless a future scoped provider gate is opened and fully evidenced.

## Deployed

- Added `docs/nodejs/phase15/mediastore-playback-decision-plan.json`.
- Added `docs/nodejs/phase15/PHASE15_MEDIASTORE_PLAYBACK_DECISION.md`.
- Added
  `tools/nodejs/phase15/verify-node-phase15-mediastore-playback-decision.js`.
- Added Gradle gate `:app:verifyNodePhase15MediaStorePlaybackDecision`.
- Imported `build/reports/nodejs/phase14-media-playback-mediastore-boundary.json`.
- Imported `build/reports/nodejs/phase15-packaged-recorder-smoke.json`.
- Recorded `mediastore_playback` as `stable_denied` with no runtime authority
  change.

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
