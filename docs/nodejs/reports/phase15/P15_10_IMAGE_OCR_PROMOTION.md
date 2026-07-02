# P15-10 Image OCR Promotion

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-10 imports the P14-14 advanced image operation contract and P14-15 packaged
image/OCR evidence contract into the v1.3 promotion lane. The gate is
executable and keeps the provider unpromoted while live advanced transform and
matching/color archives, packaged scoped output, packaged OCR model/provider
mode archives, quota/leak stress, capture-to-OCR disclosure evidence, Safe
downgrade proof, and downstream security/type/docs sync are still absent.

## Imported Evidence

- P14-14 advanced image API contract, fake-provider coverage, invalid-input
  denial, quota/recycle boundary, and raw object denial.
- P14-15 packaged image/OCR model policy, packaged fake-provider smoke,
  quota/unknown-handle boundary, and raw OCR/image/model object denial.
- P13-20 scoped `readImage`/`recycle` and live ML Kit OCR import.
- P15-09 MediaProjection dependency gate.
- Phase 15 provider promotion contracts.
- Phase 15 disclosure/rollback requirements for image/OCR.
- P15 surface manifest mapping for `packaged_media_archive` evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `image_advanced_transform_provider_archive` | `future_promotion` |
| `image_matching_color_provider_archive` | `future_promotion` |
| `image_temp_handle_quota_leak_stress_archive` | `future_promotion` |
| `image_packaged_scoped_output_archive` | `future_promotion` |
| `packaged_ocr_model_absence_archive` | `future_promotion` |
| `packaged_ocr_explicit_provider_mode_archive` | `future_promotion` |
| `packaged_image_ocr_quota_leak_stress_archive` | `future_promotion` |
| `packaged_capture_ocr_disclosure_archive` | `future_promotion` |
| `image_ocr_safe_downgrade_archive` | `future_promotion` |
| `phase15_security_type_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `image_ocr` remains
unpromoted and no runtime authority changes in P15-10.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15ImageOcrPromotion
```
