# Phase 15 Overlay Provider Promotion

Machine-readable source:

- `docs/nodejs/phase15/overlay-provider-promotion-plan.json`

P15-05 imports the P14-08 controlled Android overlay provider POC into the v1.3
promotion lane. The runtime provider, permission-denied boundary, raw object
denial, owner cleanup hooks, TypeScript surface, packaged missing-capability
denial, and conditional positive fixture exist.

The provider is not promoted. Permission-granted Android archives, Android 15+
coverage, foreground disclosure/stop action, owner cleanup stress, packaged
positive smoke, OEM matrix, and downstream security/type/docs sync remain open.

## Ready Evidence

- `phase14_runtime_overlay_provider_poc`
- `permission_denied_and_raw_boundary`
- `conditional_permission_granted_fixture`
- `packaged_overlay_boundary`
- `phase15_disclosure_rollback_contract`
- `phase15_live_ui_foundation_gate`

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `overlay_permission_granted_android12_archive` | `future_promotion` | Android 12 granted-permission show/update/drain/close archive. |
| `overlay_permission_granted_android15_archive` | `future_promotion` | Android 15+ grant/denial/settings round-trip archive. |
| `overlay_foreground_disclosure_stop_action_archive` | `future_promotion` | Foreground disclosure ownership and stop action behavior. |
| `overlay_owner_cleanup_leak_stress_archive` | `future_promotion` | Owner cleanup and leak/stress proof. |
| `packaged_overlay_positive_smoke_archive` | `future_promotion` | P15-07 packaged positive smoke and stress archive. |
| `overlay_oem_matrix_archive` | `future_promotion` | P15-06 Android 12/15+ OEM permission matrix. |
| `phase15_security_type_doc_sync` | `future_promotion` | P15-11, P15-23, P15-25, and P15-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.overlay.enabled`
- Diagnostic code: `NODE_PROVIDER_OVERLAY_DISABLED`
- Safe fallback: deny overlay creation and clean owner-scoped overlay handles.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-overlay-provider-promotion.js`
- Gradle gate: `:app:verifyNodePhase15OverlayProviderPromotion`
- Reports:
  - `build/reports/nodejs/phase15-overlay-provider-promotion.json`
  - `build/reports/nodejs/phase15-overlay-provider-promotion.md`
