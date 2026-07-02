# Phase 15 Settings/Power Promotion

Status: partial.

P15-18 imports the P14-23 settings/power OEM and background-launch matrix into
the Phase 15 provider promotion lane. The gate records that the current
runtime can read battery optimization status and perform a settings dry-run
through `device.power`, but it keeps all power mutation authority unpromoted.

The accepted live surface is still limited to
`device.isIgnoringBatteryOptimizations()` and
`device.openBatteryOptimizationSettings({ dryRun: true })` with the
`autojs6-node-battery-optimization-settings-v1` JSON-safe result schema.
Direct request-ignore-battery-optimizations mutation, direct Doze mutation,
wake locks, exact-alarm scheduling, background-launch bypasses, raw Android
objects, OEM manager handles, and Java objects remain denied.

## Imported Evidence

- `phase14_settings_power_matrix_contract`: P14-23 imports P13-25
  `device.power` status/settings dry-run evidence and records Android 12,
  Android 15+, exact-alarm, background-launch, OEM, prompt, and rollback
  archive requirements.
- `phase15_notification_dependency_gate`: P15-17 notification ownership remains
  partial and unpromoted; foreground disclosure and stop-action evidence still
  gate high-privilege packaged behavior.
- `phase15_promotion_contracts`: P15 promotion contracts keep
  `settings_power` at `provider_poc` until implementation, connected device,
  packaged APK, disclosure, rollback, security, TypeScript, docs, Safe
  downgrade, and raw-object denial evidence all pass.
- `phase15_disclosure_rollback_contract`: settings/power declares
  `node.provider.settings_power.enabled`,
  `NODE_PROVIDER_SETTINGS_POWER_DISABLED`, and Safe downgrade behavior before
  runtime authority can change.
- `phase15_surface_manifest_mapping`: P15-18 is owned by
  `pro.tasks_device_media` with the `disable_settings_power_provider`
  rollback hook.

## Matrix Boundary

The Phase 15 promotion gate preserves the P14-23 matrix boundary:

- Android 12 class and Android 15+ class device rows are required before
  promotion.
- Exact-alarm state remains observation-or-denial evidence only; no
  `AlarmManager` or `PendingIntent` scheduling authority is exposed.
- Background-launch behavior remains a device matrix requirement for packaged
  and interactive Node entry points.
- OEM power-manager notes, reviewed prompt copy, task removal, crash recovery,
  foreground disclosure handoff, and rollback cleanup remain required before
  any mutation flow can be promoted.

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
