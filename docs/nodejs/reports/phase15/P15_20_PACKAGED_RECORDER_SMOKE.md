# P15-20 Packaged Recorder Smoke

Status: partial.

P15-20 deploys the Phase 15 packaged recorder/media stress gate. It imports
P14-25 packaged boundary evidence, the P15-19 recorder dependency gate, and
the Phase 15 promotion/disclosure contracts, then keeps packaged recorder
start/stop at `provider_poc_not_promoted`.

Current packaged evidence covers module resolution, `recorder.getStatus()`,
stable `recorder.start()` denial, and missing `media.recording` capability
denial. Positive packaged microphone start/stop, foreground disclosure, task
removal, relaunch, crash recovery, quota, temporary output cleanup, Android
permission denial, Safe downgrade, and raw-object security archives remain
future-promotion work.

## Deployed

- Added `docs/nodejs/phase15/packaged-recorder-smoke-plan.json`.
- Added `docs/nodejs/phase15/PHASE15_PACKAGED_RECORDER_SMOKE.md`.
- Added
  `tools/nodejs/phase15/verify-node-phase15-packaged-recorder-smoke.js`.
- Added Gradle gate `:app:installAndRunNodeV13PackagedRecorderSmoke`.
- Imported `build/reports/nodejs/phase14-packaged-recorder-media-stress.json`.
- Imported `build/reports/nodejs/phase15-recorder-promotion.json`.
- Recorded positive packaged start/stop, task removal/relaunch, quota/temp
  cleanup, denial, foreground disclosure, Safe downgrade, raw-object security,
  and docs sync archives that still block promotion.

## Open Future-Promotion Archives

- `packaged_recorder_start_stop_positive_archive`
- `packaged_recorder_task_removal_relaunch_archive`
- `packaged_recorder_quota_temp_cleanup_archive`
- `packaged_recorder_denial_without_capability_archive`
- `packaged_recorder_foreground_disclosure_stop_archive`
- `packaged_recorder_safe_downgrade_archive`
- `packaged_recorder_raw_object_denial_security_archive`
- `phase15_task_device_media_doc_sync`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-packaged-recorder-smoke.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:installAndRunNodeV13PackagedRecorderSmoke
```

Expected reports:

```text
build/reports/nodejs/phase15-packaged-recorder-smoke.json
build/reports/nodejs/phase15-packaged-recorder-smoke.md
```
