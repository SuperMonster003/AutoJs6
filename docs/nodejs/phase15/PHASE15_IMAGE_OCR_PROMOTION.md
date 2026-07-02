# Phase 15 Image OCR Promotion

Machine-readable source:

- `docs/nodejs/phase15/image-ocr-promotion-plan.json`

P15-10 imports the P14-14 advanced image operation contract and P14-15 packaged
image/OCR evidence contract into the v1.3 promotion lane. It keeps `image_ocr`
at `provider_poc_not_promoted` while live provider archives, packaged model
archives, quota/leak stress, capture-to-OCR disclosure evidence, Safe downgrade
proof, and downstream security/type/docs sync are still absent.

The gate is a promotion evidence contract, not a runtime authority change.
Advanced image and OCR APIs remain constrained to opaque handles, scoped paths,
stable-denied provider modes, and raw-object denial until promotion evidence
closes.

## Ready Evidence

- `phase14_advanced_image_operations_contract`
- `phase14_image_invalid_input_quota_boundary`
- `phase14_packaged_image_ocr_evidence_contract`
- `phase14_packaged_ocr_model_policy`
- `phase13_screenshot_image_ocr_import`
- `phase15_media_projection_dependency_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `image_advanced_transform_provider_archive` | `future_promotion` | Live transform provider archive. |
| `image_matching_color_provider_archive` | `future_promotion` | Live matching/color provider archive. |
| `image_temp_handle_quota_leak_stress_archive` | `future_promotion` | Temporary handle quota and leak/stress archive. |
| `image_packaged_scoped_output_archive` | `future_promotion` | Packaged scoped input/output archive. |
| `packaged_ocr_model_absence_archive` | `future_promotion` | Packaged model-absence denial archive. |
| `packaged_ocr_explicit_provider_mode_archive` | `future_promotion` | Packaged explicit OCR provider-mode archive. |
| `packaged_image_ocr_quota_leak_stress_archive` | `future_promotion` | Packaged image/OCR quota and leak/stress archive. |
| `packaged_capture_ocr_disclosure_archive` | `future_promotion` | Packaged capture-to-OCR disclosure archive. |
| `image_ocr_safe_downgrade_archive` | `future_promotion` | Provider-disable Safe downgrade proof. |
| `phase15_security_type_doc_sync` | `future_promotion` | P15-11, P15-23, P15-25, and P15-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.image_ocr.enabled`
- Diagnostic code: `NODE_PROVIDER_IMAGE_OCR_DISABLED`
- Safe fallback: deny live image/OCR authority and release opaque image handles.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-image-ocr-promotion.js`
- Gradle gate: `:app:verifyNodePhase15ImageOcrPromotion`
- Reports:
  - `build/reports/nodejs/phase15-image-ocr-promotion.json`
  - `build/reports/nodejs/phase15-image-ocr-promotion.md`
