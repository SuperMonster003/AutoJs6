# Phase 15 Accessibility Promotion

Machine-readable source:

- `docs/nodejs/phase15/accessibility-promotion-plan.json`

P15-08 imports P14-11 packaged accessibility lifecycle evidence and P14-12
selector/action matrix evidence into the v1.3 promotion lane. It keeps the
`accessibility` provider at `provider_poc_not_promoted` while the required
Android 12 class and Android 15+ class device archives are still absent.

The gate is a promotion evidence contract, not a runtime authority change. It
requires lifecycle denial, privacy/settings, selector/action, global-action,
unsupported-tier, raw-object boundary, disclosure, and rollback contracts to
stay aligned before accessibility can move beyond POC.

## Ready Evidence

- `phase14_accessibility_lifecycle_fixture`
- `phase14_accessibility_disabled_denial_boundary`
- `phase14_accessibility_selector_action_matrix`
- `phase13_accessibility_selector_bridge`
- `phase13_shell_root_shizuku_separation`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `accessibility_packaged_service_enabled_archive` | `future_promotion` | User-enabled packaged service archive on Android 12/15+. |
| `accessibility_settings_round_trip_archive` | `future_promotion` | Settings launch/return notes without auto-enabling service. |
| `accessibility_task_removal_relaunch_archive` | `future_promotion` | Task removal cleanup and relaunch behavior. |
| `accessibility_disconnect_missing_capability_denial_archive` | `future_promotion` | Disconnect cleanup and missing-capability denial. |
| `accessibility_selector_android12_action_archive` | `future_promotion` | Android 12 selector/action archive. |
| `accessibility_selector_android15_plus_action_archive` | `future_promotion` | Android 15+ selector/action archive. |
| `accessibility_selector_global_actions_recents_archive` | `future_promotion` | Safe Back/Home/Recents global-action archive. |
| `accessibility_selector_unsupported_tier_diagnostics_archive` | `future_promotion` | Gesture/root/Shizuku separation diagnostics. |
| `phase15_security_type_doc_sync` | `future_promotion` | P15-11, P15-23, P15-25, and P15-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.accessibility.enabled`
- Diagnostic code: `NODE_PROVIDER_ACCESSIBILITY_DISABLED`
- Safe fallback: return stable denial for selector and action authority.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-accessibility-promotion.js`
- Gradle gate: `:app:verifyNodePhase15AccessibilityPromotion`
- Reports:
  - `build/reports/nodejs/phase15-accessibility-promotion.json`
  - `build/reports/nodejs/phase15-accessibility-promotion.md`
