# Phase 14 Settings/Power OEM Background Matrix

Status: partial.

P14-23 records the settings/power OEM and background-launch matrix gate for
Node Profile v1.2. It imports the P13-25 `device.power` status/settings
evidence and the P14-22 shared packaged-disclosure posture. The current live
surface supports `device.isIgnoringBatteryOptimizations()` and
`device.openBatteryOptimizationSettings({ dryRun: true })` through JSON-safe
snapshots and explicit `device.power` capability checks.

This gate does not promote direct power-manager mutation. Request-ignore-
battery-optimizations mutation, direct Doze mutation, wake locks, exact-alarm
scheduling, background-launch bypasses, raw Android objects, and OEM manager
handles remain denied until reviewed prompt copy, rollback behavior, and device
matrix archives exist.

## Imported Evidence

- P13-25 records `device.isIgnoringBatteryOptimizations()` and
  `device.openBatteryOptimizationSettings()` with
  `autojs6-node-battery-optimization-settings-v1` snapshots.
- `NodeDeviceBridgeInstrumentationTest` verifies `device.power` is required for
  battery optimization APIs and missing `device.power` rejects before bridge
  dispatch with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`.
- The provider registry keeps raw `PowerManager`, `WakeLock`, `Intent`,
  `Context`, and sensitive hardware identifiers out of Node.
- The shared Phase 14 device evidence matrix requires Android 12 class and
  Android 15+ class rows before high-privilege Android promotion.

## Matrix Policy

The current accepted behavior is read/status plus settings dry-run evidence
only. Promotion beyond that requires Android 12 class and Android 15+ class
archives for battery optimization state, settings round-trip behavior,
exact-alarm state, background launch restrictions, OEM notes, task removal,
crash recovery, foreground disclosure handoff, and rollback cleanup.

## Open Archives

- `settings_power_android12_oem_matrix_archive`
- `settings_power_android15_plus_oem_matrix_archive`
- `settings_power_exact_alarm_state_archive`
- `settings_power_background_launch_restriction_archive`
- `settings_power_mutation_prompt_rollback_archive`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-settings-power-oem-background-matrix.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14SettingsPowerOemBackgroundMatrix
```

Expected reports:

```text
build/reports/nodejs/phase14-settings-power-oem-background-matrix.json
build/reports/nodejs/phase14-settings-power-oem-background-matrix.md
```
