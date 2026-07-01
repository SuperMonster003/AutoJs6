# P14-23 Settings/Power OEM Background Matrix

Status: partial.

P14-23 deploys the settings/power OEM and background-launch matrix gate. It
imports P13-25 `device.power` battery optimization status/settings evidence and
the Phase 14 shared device evidence matrix, then keeps all direct power-manager
mutation authority unpromoted.

The current runtime evidence covers `device.isIgnoringBatteryOptimizations()`,
`device.openBatteryOptimizationSettings({ dryRun: true })`, exact
`device.power` capability denial, raw object denial, and sensitive device-field
omission. Android 12/15+ OEM rows, exact-alarm state, background-launch
restriction evidence, and reviewed prompt/rollback archives remain future
promotion work.

## Deployed

- Added `docs/nodejs/phase14/settings-power-oem-background-matrix.json`.
- Added `docs/nodejs/phase14/PHASE14_SETTINGS_POWER_OEM_BACKGROUND_MATRIX.md`.
- Added
  `tools/nodejs/phase14/verify-node-phase14-settings-power-oem-background-matrix.js`.
- Added Gradle gate
  `:app:verifyNodePhase14SettingsPowerOemBackgroundMatrix`.
- Imported P13-25 `device.power` status/settings dry-run evidence and
  `autojs6-node-battery-optimization-settings-v1` snapshots.
- Recorded Android 12 class and Android 15+ class matrix requirements for
  battery optimization state/settings, exact-alarm state, background-launch
  behavior, OEM notes, task removal, crash recovery, disclosure handoff, and
  rollback behavior.
- Recorded stable-denial policy for request-ignore-battery-optimizations
  mutation, direct Doze mutation, wake locks, exact-alarm scheduling,
  background-launch bypasses, raw `PowerManager`, raw `WakeLock`, raw
  `AlarmManager`, `PendingIntent`, `Intent`, `Context`, OEM manager handles,
  and Java objects.

## Open Future-Promotion Archives

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
