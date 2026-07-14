# Fleet / Headless Configuration Profiles

AutoJs6 6.8+ supports applying a JSON configuration profile without opening the
app UI. This is intended for fleet/headless deployments (e.g. Termux + Shizuku +
wireless ADB) where the initial drawer toggles and settings would otherwise
require fragile UI automation.

## Quick start

1. Push a profile JSON to the device:

   ```bash
   adb push fleet_profile.json /sdcard/Download/autojs6-fleet.json
   ```

2. Apply it via `am start`:

   ```bash
   adb shell am start -a org.autojs.autojs6.action.APPLY_FLEET_PROFILE \
       -e profile_path /sdcard/Download/autojs6-fleet.json \
       org.autojs.autojs.core.pref.fleet.FleetProfileActivity
   ```

   Or with a content URI:

   ```bash
   adb shell am start -a org.autojs.autojs6.action.APPLY_FLEET_PROFILE \
       -d file:///sdcard/Download/autojs6-fleet.json \
       org.autojs.autojs.core.pref.fleet.FleetProfileActivity
   ```

3. The activity applies the preferences silently (no Toast) and exits.
   Result is delivered four ways (all fire on every invocation):

   **a) Activity result intent** — for `startActivityForResult` callers:

   | Extra | Type | Description |
   |-------|------|-------------|
   | `result_success` | `boolean` | Whether all keys were applied |
   | `result_applied_count` | `int` | Number of keys successfully written |
   | `result_skipped_count` | `int` | Number of keys skipped (unknown or error) |
   | `result_applied_keys` | `String[]` | Key aliases that were written |
   | `result_failed_keys` | `String[]` | Key aliases that could not be written |
   | `result_errors` | `String[]` | Human-readable error messages |
   | `result_message` | `String` | Summary string |

   **b) Broadcast** — action `org.autojs.autojs6.action.FLEET_PROFILE_RESULT`
   with the same extras as above. Any app with a registered receiver can
   listen.

   **c) JSON result file** — written to the path specified by `-e result_path`,
   or by default alongside the profile file (e.g.
   `/sdcard/Download/autojs6-fleet-result.json`), or falling back to
   `/sdcard/autojs6-fleet-result.json`. The file contains:

   ```json
   {
     "success": true,
     "applied_count": 12,
     "skipped_count": 0,
     "applied_keys": ["foreground_service", "stable_mode", ...],
     "failed_keys": [],
     "errors": [],
     "message": "Applied 12 preferences, skipped 0"
   }
   ```

   **d) Daily rotating log** — each invocation appends a JSON line to
   `/sdcard/autojs6-fleet-YYYY-MM-DD.log`. The date in the filename changes
   at midnight, so the log restarts each day naturally. Each line is a
   Unix-style log entry:

   ```
   2026-07-11T22:15:30Z INFO  fleet_profile: applied=12 skipped=0 keys=foreground_service,stable_mode errors=0 message="Applied 12 preferences, skipped 0"
   2026-07-11T22:16:00Z ERROR fleet_profile: applied=0 skipped=1 failed=bad_key errors=1 message="Applied 0 preferences, skipped 1 (1 errors)"
   ```

   Level is `INFO` (all success), `WARN` (partial), or `ERROR` (failure).

## Profile format

Profiles are plain JSON objects. Each key maps to an AutoJs6 preference. The
`_meta` section is optional and controls apply behavior.

```json
{
  "_meta": {
    "name": "My fleet profile",
    "version": 1,
    "clear_existing": false
  },
  "foreground_service": true,
  "floating_menu_shown": false,
  "enable_a11y_service_with_secure_settings": true,
  "stable_mode": true,
  "guard_mode": true,
  "restart_strategy": "quick",
  "auto_check_for_updates": false
}
```

### Supported value types

| Type   | SharedPreferences method | Example |
|--------|--------------------------|---------|
| bool   | `putBoolean`             | `true` |
| string | `putString`              | `"quick"` |
| int    | `putInt`                 | `350` |
| long   | `putLong`                | `1234567890` |
| float  | `putFloat`               | `0.5` |
| array  | `putStringSet`           | `["a", "b"]` |

### Key aliases

You can use short aliases instead of the raw `key_$_...` preference keys.
Common aliases:

| Alias | Maps to |
|-------|---------|
| `foreground_service` | `key_$_foreground_service` |
| `floating_menu_shown` | `key_$_floating_menu_shown` |
| `enable_a11y_service_with_secure_settings` | `key_$_enable_a11y_service_with_secure_settings` |
| `enable_a11y_service_with_root_access` | `key_$_enable_a11y_service_with_root_access` |
| `stable_mode` | `key_$_stable_mode` |
| `guard_mode` | `key_$_guard_mode` |
| `use_volume_control_running` | `key_$_use_volume_control_running` |
| `use_volume_control_record` | `key_$_use_volume_control_record` |
| `record_toast` | `key_$_record_toast` |
| `auto_check_for_updates` | `key_$_auto_check_for_updates` |
| `restart_strategy` | `key_$_restart_strategy` |
| `scheduled_restart_backend` | `key_$_scheduled_restart_backend` |
| `timed_task_backend` | `key_$_timed_task_backend` |
| `night_mode` | `key_$_night_mode` |
| `root_mode` | `key_$_root_mode` |
| `file_extensions` | `key_$_file_extensions` |
| `hidden_files` | `key_$_hidden_files` |
| `display_over_other_apps` | `key_$_display_over_other_apps` |
| `post_notifications_permission` | `key_$_post_notifications_permission` |
| `all_files_access` | `key_$_all_files_access` |
| `keep_screen_on_when_in_foreground` | `key_$_keep_screen_on_when_in_foreground` |

Raw keys (`key_$_...`) are also accepted for keys not in the alias table.

## Security notes

- `FleetProfileActivity` is exported because provisioning tools run outside the
  app. Any app with `START_FOREGROUND_SERVICES_FROM_BACKGROUND` or that can
  start activities can trigger it.
- Profiles should only be placed in locations your provisioning tooling
  controls.
- This API only writes AutoJs6's own SharedPreferences; it does **not** grant
  Android runtime permissions. Use `pm grant` / Shizuku / `appops` for those.

## Default profile

A reference profile for unattended watchdog use is bundled at
`app/src/main/assets/fleet_profile_default.json`.

## Limitations

- The profile only writes preferences. Some settings still require a one-time
  Android permission grant (e.g. `WRITE_SECURE_SETTINGS` for accessibility
  via `secure settings`, `MANAGE_EXTERNAL_STORAGE`, `BIND_NOTIFICATION_LISTENER_SERVICE`).
- Runtime service effects are triggered on the next natural lifecycle event.
  Use `am startservice` or `am start` on AutoJs6 if you need an immediate restart.

## See also

- [Issue #553](https://github.com/SuperMonster003/AutoJs6/issues/553)
- [stayturgid](https://github.com/djbclark/stayturgid) — example fleet orchestration
