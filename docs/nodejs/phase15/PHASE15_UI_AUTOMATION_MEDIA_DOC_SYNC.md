# Phase 15 UI Automation Media Doc Sync

Machine-readable source:

- `docs/nodejs/phase15/ui-automation-media-doc-sync.json`

P15-11 synchronizes the v1.3 UI/automation/media documentation layer after
P15-04 through P15-10. It imports those gates as executable partial evidence,
keeps `pro.ui_overlay_automation_media` at `provider_poc_not_promoted`, and
verifies that examples, TypeScript declaration notes, release-facing text,
compatibility notes, security notes, and error guidance describe the same
future-only boundary.

The gate does not change runtime authority. Packaged live UI, overlay,
accessibility, MediaProjection, advanced image, packaged OCR/model, and
recorder-session paths remain static, guarded, or denial-oriented until their
provider-specific archive gates are promoted.

## Synced Inputs

- `build/reports/nodejs/phase15-packaged-live-ui-promotion.json`
- `build/reports/nodejs/phase15-overlay-provider-promotion.json`
- `build/reports/nodejs/phase15-overlay-oem-matrix.json`
- `build/reports/nodejs/phase15-packaged-overlay-smoke.json`
- `build/reports/nodejs/phase15-accessibility-promotion.json`
- `build/reports/nodejs/phase15-media-projection-promotion.json`
- `build/reports/nodejs/phase15-image-ocr-promotion.json`
- `build/reports/nodejs/phase14-ui-automation-media-doc-sync.json`

## Synced Outputs

- `docs/nodejs/types/autojs6-node/README.md`
- `docs/nodejs/EXAMPLES.md`
- `docs/nodejs/releases/NODE_PROFILE_V1_3_UI_AUTOMATION_MEDIA_SYNC.md`
- `docs/nodejs/COMPATIBILITY_PROFILE.md`
- `docs/nodejs/SECURITY_MODEL.md`
- `docs/nodejs/ERROR_CATALOG.md`
- `docs/nodejs/TESTING.md`
- `docs/nodejs/CI_GATES.md`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-ui-automation-media-doc-sync.js`
- Gradle gate: `:app:verifyNodePhase15UiAutomationMediaDocSync`
- Reports:
  - `build/reports/nodejs/phase15-ui-automation-media-doc-sync.json`
  - `build/reports/nodejs/phase15-ui-automation-media-doc-sync.md`
