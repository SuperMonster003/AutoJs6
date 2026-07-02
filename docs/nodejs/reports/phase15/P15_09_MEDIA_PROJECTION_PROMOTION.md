# P15-09 MediaProjection Promotion

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-09 imports the P14-13 Activity-owned MediaProjection provider contract into
the v1.3 promotion lane. The gate is executable and keeps the provider
unpromoted while Android 12/15+ prompt/capture archives, foreground
disclosure/stop action, task removal/relaunch/crash behavior, packaged APK
evidence, Safe downgrade proof, and downstream security/type/docs sync are
still absent.

## Imported Evidence

- P14-13 MediaProjection provider contract for `requestScreenCapture`,
  `nextImage`, and `stop`.
- Activity-owned `ScreenCaptureRequester` and optional real-device one-frame
  capture smoke.
- Raw MediaProjection/ImageReader/Bitmap/display object denial boundary.
- P13-20 screenshot/image/OCR evidence with MediaProjection provider still
  not ready.
- P15-08 accessibility lane dependency gate.
- Phase 15 provider promotion contracts.
- Phase 15 disclosure/rollback requirements for MediaProjection.
- P15 surface manifest mapping for `permission_lifecycle_archive` evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `media_projection_android12_prompt_capture_archive` | `future_promotion` |
| `media_projection_android15_plus_prompt_capture_archive` | `future_promotion` |
| `media_projection_foreground_disclosure_stop_action_archive` | `future_promotion` |
| `media_projection_task_removal_relaunch_crash_archive` | `future_promotion` |
| `media_projection_packaged_apk_evidence_archive` | `future_promotion` |
| `media_projection_safe_downgrade_archive` | `future_promotion` |
| `phase15_security_type_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `media_projection`
remains unpromoted and no runtime authority changes in P15-09.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15MediaProjectionPromotion
```
