# P15-07 Packaged Overlay Smoke

Status: partial

Promotion state: `provider_poc_not_promoted`

P15-07 imports the P14-10 packaged overlay positive smoke/stress fixture into
the v1.3 promotion lane. The gate is executable and keeps the provider
unpromoted while the packaged APK install-run archives, Android 12/15+
coverage, disclosure/stop action review, task removal/relaunch/crash behavior,
stress/leak proof, and downstream security/type/docs sync are still absent.

## Imported Evidence

- P14-10 packaged overlay positive fixture and marker contract.
- P14-10 packaged show/update/drainEvents/close/closeAll and stress marker
  expectations.
- P15-05 overlay provider promotion gate and packaged positive smoke blocker.
- P15-06 overlay permission/OEM matrix gate.
- Phase 15 provider promotion contracts.
- Phase 15 disclosure/rollback requirements for overlay.
- P15 surface manifest mapping for `packaged_smoke_archive` evidence.

## Open Blockers

| Blocker | Classification |
| --- | --- |
| `packaged_overlay_positive_smoke_archive` | `future_promotion` |
| `packaged_overlay_positive_device_archive` | `future_promotion` |
| `packaged_overlay_foreground_disclosure_stop_action` | `future_promotion` |
| `packaged_overlay_task_removal_relaunch_crash` | `future_promotion` |
| `packaged_overlay_event_drain_leak_stress` | `future_promotion` |
| `phase15_security_type_doc_sync` | `future_promotion` |

## Decision

The gate reports `partial` with zero policy violations. `overlay` remains
unpromoted and no runtime authority changes in P15-07.

## Gate

```powershell
.\gradlew.bat --console=plain :app:installAndRunNodeV13PackagedOverlaySmoke
```
