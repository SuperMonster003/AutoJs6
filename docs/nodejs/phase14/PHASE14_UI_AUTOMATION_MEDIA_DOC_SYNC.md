# Phase 14 UI Automation Media Doc Sync

P14-16 is a documentation, example, and declaration synchronization gate for
the `pro.ui_automation_media` surface. It does not promote additional runtime
authority. It records the P14-07 through P14-15 provider POC and partial gates
as the current v1.2 state, then verifies that public docs, migration examples,
release matrix rows, security text, compatibility text, and error guidance do
not overstate availability.

## Synced State

| Area | Current state | Sync rule |
| --- | --- | --- |
| Packaged UI | P14-07 partial/provider POC | Docs may mention entry/update/close evidence, but packaged relaunch/crash/disclosure remain gated. |
| Overlay | P14-08 to P14-10 partial/provider POC | Examples stay guarded; packaged/foreground/permission archives remain future-promotion evidence. |
| Accessibility | P14-11 to P14-12 partial | Selector APIs are typed, but packaged service lifecycle and real-device matrix archives remain required. |
| MediaProjection | P14-13 partial | `media_projection` stays fail-closed until Activity-owned prompt/capture archives are promoted. |
| Image/OCR | P14-14 to P14-15 partial | Scoped `readImage`/`recycle` and ML Kit OCR evidence are documented; advanced image operations and packaged OCR/model behavior stay gated. |
| Media/recorder | P13-26 partial import | Read-only media status remains documented; real recorder sessions move to P14-24/P14-25. |

## Example Policy

The only Pro parity runtime smoke subset remains:

- `sample/nodejs/pro-parity-suite:ui-layout`
- `sample/nodejs/pro-parity-suite:tasks-work-manager`

High-risk snippets such as overlay, media/recorder, screenshot/OCR, and
accessibility remain static, guarded, or denial-oriented until their provider,
permission, disclosure, packaged, and device-matrix archives are promoted.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14UiAutomationMediaDocSync
```

Expected reports:

```text
build/reports/nodejs/phase14-ui-automation-media-doc-sync.json
build/reports/nodejs/phase14-ui-automation-media-doc-sync.md
```
