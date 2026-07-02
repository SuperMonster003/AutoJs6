# Phase 15 Packaged Recorder Smoke

Status: partial.

P15-20 imports the P14-25 packaged recorder/media stress evidence into the
Phase 15 provider promotion lane. The gate records that current packaged APK
coverage proves module resolution, recorder status, stable start denial, and
missing `media.recording` capability denial. It does not promote packaged
microphone recording.

The accepted packaged surface remains a boundary smoke: `media`, `mediainfo`,
and `recorder` resolve without shadowing, `recorder.getStatus()` returns the
`autojs6-node-recorder-status-v1` JSON-safe snapshot, and `recorder.start()`
continues to deny until positive packaged start/stop evidence exists.

## Imported Evidence

- `phase14_packaged_recorder_media_stress_contract`: P14-25 imports packaged
  media/recorder status and denial boundary smoke while reserving positive
  packaged start/stop stress rows.
- `phase15_recorder_dependency_gate`: P15-19 keeps `recorder` partial and
  unpromoted before packaged recorder stress can proceed.
- `phase15_promotion_contracts`: P15 contracts keep `recorder` at
  `provider_poc` until implementation, connected device, packaged APK,
  disclosure, rollback, security, TypeScript, docs, Safe downgrade, and raw
  object denial evidence all pass.
- `phase15_disclosure_rollback_contract`: recorder declares foreground
  disclosure, stop action, provider disable, Safe downgrade, and crash circuit
  breaker requirements before runtime authority can change.
- `phase15_surface_manifest_mapping`: P15-20 is owned by
  `pro.tasks_device_media` with the `disable_recorder_provider` rollback hook.

## Packaged Boundary

Positive packaged recorder promotion still requires visible microphone
foreground disclosure, deterministic stop behavior, task removal cleanup,
relaunch/crash recovery, quota enforcement, scoped temporary output cleanup,
missing capability denial, missing Android permission denial, denied prompt
behavior, metadata mismatch diagnostics, Safe downgrade, and raw-object
security coverage.

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
