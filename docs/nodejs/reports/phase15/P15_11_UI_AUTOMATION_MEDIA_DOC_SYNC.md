# P15-11 UI Automation Media Doc Sync

Status: ready

Promotion state: `provider_poc_not_promoted`

P15-11 synchronizes the v1.3 UI/automation/media docs after P15-04 through
P15-10. The source gates are executable but partial, so the synced docs record
their evidence without claiming provider promotion or new runtime authority.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase15/ui-automation-media-doc-sync.json`.
- Release-facing sync:
  `docs/nodejs/releases/NODE_PROFILE_V1_3_UI_AUTOMATION_MEDIA_SYNC.md`.
- Declaration sync:
  `docs/nodejs/types/autojs6-node/README.md` records the P15-11 v1.3 state for
  UI, overlay, accessibility, MediaProjection, image/OCR, and media/recorder
  declarations.
- Example sync:
  `docs/nodejs/EXAMPLES.md` keeps only `ui-layout` and `tasks-work-manager` in
  the focused Pro parity smoke subset; overlay, media/recorder, screenshot/OCR,
  and accessibility examples remain static, guarded, or denial-oriented.
- Compatibility/security/error sync:
  `COMPATIBILITY_PROFILE.md`, `SECURITY_MODEL.md`, and `ERROR_CATALOG.md`
  record the same `provider_poc_not_promoted` state and denial guidance.
- Gate wiring:
  `:app:verifyNodePhase15UiAutomationMediaDocSync` imports P15-04 through
  P15-10 generated reports and the P14-16 documentation sync report.

## Decision

The gate reports `ready` with zero policy violations. P15-04 through P15-10
remain partial provider promotion work, and P15-11 adds no runtime authority.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15UiAutomationMediaDocSync
```
