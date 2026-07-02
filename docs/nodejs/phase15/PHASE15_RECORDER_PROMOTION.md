# Phase 15 Recorder Promotion

Status: partial.

P15-19 imports the P14-24 real recorder session promotion gate into the Phase
15 provider promotion lane. The gate records that `recorder.getStatus()` is
available behind exact `media.recording`, while `recorder.start()` and
`recorder.stop()` remain fail-closed until runtime permission, foreground
disclosure, session ownership, temporary output cleanup, packaged stress, Safe
downgrade, and raw-object security evidence exist.

No runtime recorder authority changes in this task. The current accepted
surface is the JSON-safe `autojs6-node-recorder-status-v1` status snapshot and
stable denial for start/stop. Raw `MediaRecorder`, `AudioRecord`,
`AudioPlaybackCaptureConfiguration`, `MediaProjection`,
`ParcelFileDescriptor`, `Uri`, `Context`, foreground service handles, file
descriptors, and Java objects remain denied.

## Imported Evidence

- `phase14_recorder_provider_contract`: P14-24 imports P13-26
  `recorder.getStatus()` evidence, packaged media/recorder boundary smoke, and
  fail-closed start/stop behavior.
- `phase15_settings_power_dependency_gate`: P15-18 keeps settings/power
  high-privilege behavior unpromoted before recorder promotion proceeds.
- `phase15_promotion_contracts`: P15 contracts keep `recorder` at
  `provider_poc` until implementation, connected device, packaged APK,
  disclosure, rollback, security, TypeScript, docs, Safe downgrade, and raw
  object denial evidence all pass.
- `phase15_disclosure_rollback_contract`: recorder declares
  `node.provider.recorder.enabled`, `NODE_PROVIDER_RECORDER_DISABLED`, and
  Safe downgrade behavior before runtime authority can change.
- `phase15_surface_manifest_mapping`: P15-19 is owned by
  `pro.tasks_device_media` with the `disable_recorder_provider` rollback hook.

## Session Boundary

Future recorder promotion must own all recording sessions as scoped bridge
resources. A promoted session may expose only JSON-safe state and owned output
descriptors. The gate requires runtime microphone prompt ownership, foreground
microphone disclosure, stop action, provider-destroy cleanup, process crash
cleanup, task-removal cleanup, scoped temporary output cleanup, packaged
start/stop stress, Safe downgrade, and stable denial when `media.recording` or
Android microphone permission is missing.

## Open Future-Promotion Archives

- `recorder_runtime_permission_prompt_archive`
- `recorder_foreground_disclosure_stop_action_archive`
- `recorder_temp_output_ownership_cleanup_archive`
- `recorder_crash_cleanup_session_resource_archive`
- `recorder_packaged_start_stop_stress_archive`
- `recorder_safe_downgrade_archive`
- `recorder_raw_object_denial_security_archive`
- `phase15_task_device_media_doc_sync`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-recorder-promotion.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15RecorderPromotion
```

Expected reports:

```text
build/reports/nodejs/phase15-recorder-promotion.json
build/reports/nodejs/phase15-recorder-promotion.md
```
