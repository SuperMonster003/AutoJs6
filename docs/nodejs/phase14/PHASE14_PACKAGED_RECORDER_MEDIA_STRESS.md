# Phase 14 Packaged Recorder Media Stress

Status: partial.

P14-25 records the packaged recorder and media stress evidence gate. It imports
the P13-26 packaged media/recorder boundary smoke and the P14-24 recorder
provider promotion contract. Current packaged evidence proves module
resolution, recorder status, start denial, and exact capability denial. It does
not prove positive packaged microphone recording.

This gate does not promote packaged recorder start/stop. Positive promotion
still requires visible microphone foreground disclosure, deterministic stop behavior,
task removal cleanup, relaunch/crash recovery, quota enforcement, scoped
temporary output cleanup, denial without capability, Android permission denial,
and rollback behavior.

## Imported Evidence

- Packaged media/recorder smoke resolves `media`, `mediainfo`, and `recorder`
  modules without shadowing.
- Packaged smoke verifies `recorder.getStatus()` returns
  `autojs6-node-recorder-status-v1`.
- Packaged smoke verifies `recorder.start()` remains denied and missing
  `media.recording` rejects before bridge dispatch.
- P14-24 records the session, foreground disclosure, temporary output, crash
  cleanup, and raw object denial requirements for future real recording.

## Open Archives

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
