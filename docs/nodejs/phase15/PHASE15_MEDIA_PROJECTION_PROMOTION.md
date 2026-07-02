# Phase 15 MediaProjection Promotion

Machine-readable source:

- `docs/nodejs/phase15/media-projection-promotion-plan.json`

P15-09 imports the P14-13 Activity-owned MediaProjection provider contract into
the v1.3 promotion lane. It keeps `media_projection` at
`provider_poc_not_promoted` while Android 12/15+ prompt/capture archives,
foreground disclosure/stop action, task removal/relaunch/crash evidence,
packaged APK evidence, Safe downgrade proof, and downstream security/type/docs
sync are still absent.

The gate is a promotion evidence contract, not a runtime authority change.
MediaProjection prompt ownership must remain Activity-owned; the embedded Node
service must not prompt directly before promotion.

## Ready Evidence

- `phase14_media_projection_provider_contract`
- `phase14_activity_owned_prompt_capture_smoke`
- `phase14_media_projection_raw_object_boundary`
- `phase13_screenshot_image_ocr_import`
- `phase15_accessibility_dependency_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `media_projection_android12_prompt_capture_archive` | `future_promotion` | Android 12 prompt grant/denial, capture, stop, and raw-object archive. |
| `media_projection_android15_plus_prompt_capture_archive` | `future_promotion` | Android 15+ prompt grant/denial, capture, stop, and raw-object archive. |
| `media_projection_foreground_disclosure_stop_action_archive` | `future_promotion` | Foreground disclosure ownership and stop action behavior. |
| `media_projection_task_removal_relaunch_crash_archive` | `future_promotion` | Task removal cleanup, relaunch, and crash recovery behavior. |
| `media_projection_packaged_apk_evidence_archive` | `future_promotion` | Packaged APK capability/denial evidence. |
| `media_projection_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof. |
| `phase15_security_type_doc_sync` | `future_promotion` | P15-11, P15-23, P15-25, and P15-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.media_projection.enabled`
- Diagnostic code: `NODE_PROVIDER_MEDIA_PROJECTION_DISABLED`
- Safe fallback: revoke capture session handles and return screen capture denial.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-media-projection-promotion.js`
- Gradle gate: `:app:verifyNodePhase15MediaProjectionPromotion`
- Reports:
  - `build/reports/nodejs/phase15-media-projection-promotion.json`
  - `build/reports/nodejs/phase15-media-projection-promotion.md`
