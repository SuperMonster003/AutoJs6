# P14-25 Packaged Recorder Media Stress

Status: partial.

P14-25 deploys the packaged recorder and media stress evidence gate. It imports
P13-26 packaged media/recorder boundary smoke and P14-24 recorder provider
requirements, then keeps positive packaged microphone start/stop promotion
blocked until stress archives exist.

The current evidence covers packaged module resolution, recorder status,
stable start denial, and denial without exact `media.recording` capability.
Positive packaged start/stop, foreground disclosure, task removal, relaunch,
crash recovery, quota, temporary output cleanup, and Android permission denial
archives remain future-promotion work.

## Deployed

- Added `docs/nodejs/phase14/packaged-recorder-media-stress-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_PACKAGED_RECORDER_MEDIA_STRESS.md`.
- Added
  `tools/nodejs/phase14/verify-node-phase14-packaged-recorder-media-stress.js`.
- Added Gradle gate `:app:verifyNodePhase14PackagedRecorderMediaStress`.
- Imported packaged media/recorder boundary evidence for module resolution,
  read-only media snapshots, scoped mediainfo, recorder status, start denial,
  and missing `media.recording` capability denial.
- Reserved future stress rows for positive packaged start/stop, foreground
  microphone disclosure, stop action, task removal, relaunch/crash recovery,
  quota enforcement, temporary output cleanup, missing Android permission,
  denied prompt, metadata mismatch, and Safe downgrade.

## Open Future-Promotion Archives

- `packaged_recorder_start_stop_positive_archive`
- `packaged_recorder_task_removal_relaunch_archive`
- `packaged_recorder_quota_temp_cleanup_archive`
- `packaged_recorder_denial_without_capability_archive`
- `packaged_recorder_foreground_disclosure_stop_archive`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-packaged-recorder-media-stress.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14PackagedRecorderMediaStress
```

Expected reports:

```text
build/reports/nodejs/phase14-packaged-recorder-media-stress.json
build/reports/nodejs/phase14-packaged-recorder-media-stress.md
```
