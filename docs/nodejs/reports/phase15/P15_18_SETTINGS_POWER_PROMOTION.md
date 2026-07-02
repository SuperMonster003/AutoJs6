# P15-18 Settings/Power Promotion

Status: partial.

P15-18 deploys the Phase 15 settings/power OEM and background-launch promotion
gate. It imports P14-23 settings/power matrix evidence, P15-17 notification
dependency evidence, and the Phase 15 promotion/disclosure contracts, then
keeps `settings_power` at `provider_poc_not_promoted`.

The current runtime authority remains unchanged. `device.power` supports
`device.isIgnoringBatteryOptimizations()` and
`device.openBatteryOptimizationSettings({ dryRun: true })` as JSON-safe status
and settings handoff operations. Direct power-manager mutation, wake locks,
exact-alarm scheduling, background-launch bypasses, raw Android object access,
and OEM manager handles remain denied until device archives and reviewed
rollback/disclosure evidence exist.

## Deployed

- Added `docs/nodejs/phase15/settings-power-promotion-plan.json`.
- Added `docs/nodejs/phase15/PHASE15_SETTINGS_POWER_PROMOTION.md`.
- Added
  `tools/nodejs/phase15/verify-node-phase15-settings-power-promotion.js`.
- Added Gradle gate `:app:verifyNodePhase15SettingsPowerPromotion`.
- Imported `build/reports/nodejs/phase14-settings-power-oem-background-matrix.json`.
- Imported `build/reports/nodejs/phase15-notification-promotion.json`.
- Recorded the Android 12, Android 15+, exact-alarm, background-launch, OEM,
  prompt, rollback, packaged disclosure, Safe downgrade, and raw-object
  security archives that still block promotion.

## Open Future-Promotion Archives

- `settings_power_android12_oem_matrix_archive`
- `settings_power_android15_plus_oem_matrix_archive`
- `settings_power_exact_alarm_state_archive`
- `settings_power_background_launch_restriction_archive`
- `settings_power_mutation_prompt_rollback_archive`
- `settings_power_packaged_disclosure_stop_action_archive`
- `settings_power_safe_downgrade_archive`
- `settings_power_raw_object_denial_security_archive`
- `phase15_task_device_media_doc_sync`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-settings-power-promotion.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15SettingsPowerPromotion
```

Expected reports:

```text
build/reports/nodejs/phase15-settings-power-promotion.json
build/reports/nodejs/phase15-settings-power-promotion.md
```
