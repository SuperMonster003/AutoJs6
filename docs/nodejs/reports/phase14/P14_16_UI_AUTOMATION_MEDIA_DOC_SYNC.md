# P14-16 UI Automation Media Doc Sync

Status: ready

P14-16 synchronizes the UI/automation/media documentation layer for Phase 14.
It records P14-07 through P14-15 as deployed provider POC or partial evidence,
keeps `pro.ui_automation_media` unpromoted, and verifies that examples,
declarations, release matrix rows, compatibility notes, security notes, and
error guidance all describe the same boundary.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase14/ui-automation-media-doc-sync.json`.
- Release matrix:
  `docs/nodejs/releases/NODE_PROFILE_V1_2_UI_AUTOMATION_MEDIA_MATRIX.md`.
- Declaration sync:
  `docs/nodejs/types/autojs6-node/README.md` now records the P14-16 docs sync
  state for UI, overlay, accessibility, MediaProjection, image/OCR, and
  media/recorder declarations.
- Example sync:
  `docs/nodejs/EXAMPLES.md` keeps only `ui-layout` and `tasks-work-manager`
  in the Pro parity runtime smoke subset; overlay, media/recorder,
  screenshot/OCR, and accessibility examples stay static-or-denied.
- Compatibility/security/error sync:
  `COMPATIBILITY_PROFILE.md`, `SECURITY_MODEL.md`, and `ERROR_CATALOG.md`
  record the same `provider_poc_not_promoted` state and denial guidance.
- Gate wiring:
  `:app:verifyNodePhase14UiAutomationMediaDocSync` imports P14-07 through
  P14-15 generated reports and the Phase 13 declaration/example/migration
  gates.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-ui-automation-media-doc-sync.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14UiAutomationMediaDocSync
```
