# P15-22 Task Device Media Doc Sync

Status: ready

Promotion state: `provider_poc_not_promoted`

P15-22 synchronizes the v1.3 task/device/media docs after P15-12 through
P15-21. P15-12 through P15-20 remain executable but partial; P15-21 is a
ready stable-denied MediaStore/playback decision. The synced docs record that
evidence without claiming provider promotion or new runtime authority.

## Implemented Evidence

- Plan source:
  `docs/nodejs/phase15/task-device-media-doc-sync.json`.
- Release-facing sync:
  `docs/nodejs/releases/NODE_PROFILE_V1_3_TASK_DEVICE_MEDIA_SYNC.md`.
- Declaration sync:
  `docs/nodejs/types/autojs6-node/README.md` records the P15-22 v1.3 state for
  WorkManager, task compatibility, notifications, device/power, media,
  mediainfo, recorder, and stable-denied MediaStore/playback declarations.
- Example sync:
  `docs/nodejs/EXAMPLES.md` keeps `tasks-work-manager` as the only
  task/device/media focused smoke path; `notifications-power` and
  `media-recorder` remain static, guarded, or denial-oriented.
- Compatibility/security/error sync:
  `COMPATIBILITY_PROFILE.md`, `SECURITY_MODEL.md`, and `ERROR_CATALOG.md`
  record the same `provider_poc_not_promoted` plus `mediastore_playback`
  `stable_denied` state.
- Gate wiring:
  `:app:verifyNodePhase15TaskDeviceMediaDocSync` imports P15-12 through
  P15-21 generated reports and the P14-27 documentation sync report.

## Decision

The gate reports `ready` with zero policy violations. P15-12 through P15-20
remain partial provider promotion work, P15-21 remains stable-denied, and
P15-22 adds no runtime authority.

## Gate

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15TaskDeviceMediaDocSync
```
