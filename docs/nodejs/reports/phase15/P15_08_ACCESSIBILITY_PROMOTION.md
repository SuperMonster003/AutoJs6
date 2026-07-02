# P15-08 Accessibility Promotion

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-08 imports the P14-11 packaged accessibility lifecycle fixture and P14-12
selector/action matrix into the v1.3 promotion lane. The gate is executable and
keeps the provider unpromoted while Android 12/15+ service-enabled archives,
settings round-trip notes, task removal/relaunch evidence, disconnect and
missing-capability denial, global-action diagnostics, unsupported-tier
diagnostics, and downstream security/type/docs sync are still absent.

## Imported Evidence

- P14-11 packaged lifecycle fixture, privacy/settings contract, and
  disabled-state denial boundary.
- P14-12 selector/action matrix with Android 12 and Android 15+ reserved rows.
- P13-19 selector bridge, Rhino compat aliases, timeout/cancellation, and raw
  object absence.
- P13-24 shell/root/Shizuku separation from accessibility authority.
- Phase 15 provider promotion contracts.
- Phase 15 disclosure/rollback requirements for accessibility.
- P15 surface manifest mapping for `device_matrix_archive` evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `accessibility_packaged_service_enabled_archive` | `future_promotion` |
| `accessibility_settings_round_trip_archive` | `future_promotion` |
| `accessibility_task_removal_relaunch_archive` | `future_promotion` |
| `accessibility_disconnect_missing_capability_denial_archive` | `future_promotion` |
| `accessibility_selector_android12_action_archive` | `future_promotion` |
| `accessibility_selector_android15_plus_action_archive` | `future_promotion` |
| `accessibility_selector_global_actions_recents_archive` | `future_promotion` |
| `accessibility_selector_unsupported_tier_diagnostics_archive` | `future_promotion` |
| `phase15_security_type_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `accessibility` remains
unpromoted and no runtime authority changes in P15-08.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15AccessibilityPromotion
```
