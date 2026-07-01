# Node Profile v1.2 UI Automation Media Matrix

This matrix records the Phase 14 `pro.ui_automation_media` documentation sync
state. It is evidence for P14-16 docs sync only; it does not promote any
provider beyond the gates listed here.

| Area | Source gate | Current v1.2 state | Runtime availability | Example policy | Remaining promotion evidence |
| --- | --- | --- | --- | --- | --- |
| Packaged live UI | P14-07 | `provider_poc` / partial | Activity-owned JSON UI shape and packaged entry/update/close evidence are recorded, but packaged relaunch, crash, and disclosure remain gated. | `ui-layout` is in the focused Pro smoke subset. | Packaged task relaunch, crash recovery, and user-visible disclosure archive. |
| Overlay provider | P14-08, P14-09, P14-10 | `provider_poc` / partial | Permission-aware overlay POC and packaged positive fixture are documented; promotion still needs archive-backed permission, disclosure, task, and leak evidence. | `overlay-floaty` remains static-or-denied. | Android 12/15+ install-run, foreground disclosure/stop action, task removal/relaunch/crash, and event-drain/leak-stress archives. |
| Accessibility selector/lifecycle | P14-11, P14-12 | partial | Selector/action types and lifecycle fixture exist; packaged service enablement and real-device matrix archives are not promoted. | `accessibility-selector` remains static-or-denied. | User-enabled service, settings round-trip, disconnect, task relaunch, Android 12/15+, global-action, and unsupported-tier archives. |
| MediaProjection | P14-13 | partial | JavaScript API and Activity-owned provider contract are documented, while live Node bridge capture remains fail-closed. | Screenshot/capture snippets remain static-or-denied unless a provider/permission path is explicitly available. | Prompt/capture, foreground disclosure/stop action, task removal/relaunch/crash archives. |
| Image operations | P14-14 | partial | Scoped `readImage`/`recycle` and fake-provider API shape are documented; save/transform/matching/color live operations remain fail-closed. | Image transform snippets remain static-or-denied. | Live transform provider, matching/color provider, temp-handle quota/leak stress, packaged scoped-output archives. |
| Packaged image/OCR | P14-15 | partial | Live ML Kit OCR over provider-owned image handles and packaged fake-provider OCR smoke are documented; packaged model/provider archives remain open. | `screenshot-ocr` remains static-or-denied. | Model absence, explicit provider mode, quota/leak stress, capture/disclosure archives. |
| Media/recorder import | P13-26, P14-24, P14-25 | partial import | Read-only media status and scoped metadata are documented; real recorder sessions are not promoted in P14-16. | `media-recorder` remains static-or-denied. | Privacy-reviewed recorder provider, foreground disclosure, packaged recorder/media stress archives. |

P14-16 docs sync verifies that this matrix, TypeScript declaration references,
Pro examples, compatibility profile, security model, and error catalog all use
the same `provider_poc_not_promoted` decision for these high-risk surfaces.
