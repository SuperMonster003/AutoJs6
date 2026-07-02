# P15-19 Recorder Promotion

Status: partial.

P15-19 deploys the Phase 15 real recorder session promotion gate. It imports
P14-24 recorder provider evidence, the P15-18 settings/power dependency gate,
and the Phase 15 promotion/disclosure contracts, then keeps `recorder` at
`provider_poc_not_promoted`.

The current runtime authority remains unchanged. `recorder.getStatus()` stays
available through JSON-safe status snapshots, while `recorder.start()` and
`recorder.stop()` continue to deny with stable diagnostics until microphone
prompt, foreground disclosure, session resource, temporary output, crash
cleanup, packaged stress, Safe downgrade, and raw-object security evidence are
archived.

## Deployed

- Added `docs/nodejs/phase15/recorder-promotion-plan.json`.
- Added `docs/nodejs/phase15/PHASE15_RECORDER_PROMOTION.md`.
- Added `tools/nodejs/phase15/verify-node-phase15-recorder-promotion.js`.
- Added Gradle gate `:app:verifyNodePhase15RecorderPromotion`.
- Imported `build/reports/nodejs/phase14-recorder-provider.json`.
- Imported `build/reports/nodejs/phase15-settings-power-promotion.json`.
- Recorded runtime prompt, foreground disclosure/stop action, scoped temporary
  output, crash cleanup, packaged start/stop stress, Safe downgrade,
  raw-object security, and docs sync archives that still block promotion.

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
