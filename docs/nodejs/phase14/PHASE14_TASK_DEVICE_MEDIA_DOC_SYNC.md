# Phase 14 Task Device Media Doc Sync

P14-27 is a documentation, example, declaration, release-matrix, and migration
sync gate for the `pro.tasks_device_media` surface. It does not promote
additional runtime authority. It records the P14-17 through P14-26 provider POC
and partial gates as the current v1.2 state, then verifies that public docs,
examples, TypeScript declarations, release matrix rows, security text,
compatibility text, and error guidance do not overstate availability.

## Synced State

| Area | Current state | Sync rule |
| --- | --- | --- |
| Task database | P14-17 to P14-19 partial/provider POC | JSON-safe mutation contracts, schema rollback, and packaged scheduling evidence are documented; live `require("tasks")` mutation stays unpromoted. |
| IntentTask/broadcast | P14-20 to P14-21 partial/provider POC | Future `tasks.broadcast` descriptors and packaged trigger/remove contracts are documented; broadcast task authority remains future-only. |
| Notifications | P14-22 partial/provider POC | Owned notification ids and permission/settings handoff are documented; packaged post/cancel, foreground disclosure, advanced actions, and rollback archives stay gated. |
| Settings/power | P14-23 partial/provider POC | `device.power` status/settings dry-run is documented; OEM/background-launch/exact-alarm mutation archives stay gated. |
| Recorder/media | P14-24 to P14-26 partial/provider POC | Recorder status, packaged boundary smoke, and playback/MediaStore stable-denial are documented; real recording, packaged start/stop, playback, and media-library mutation stay unpromoted. |

## Example Policy

The only task/device/media Pro parity runtime smoke path in the focused subset is
`sample/nodejs/pro-parity-suite:tasks-work-manager`. The
`notifications-power` and `media-recorder` snippets remain migration evidence
and denial-oriented checks until notification disclosure, OEM power, real
recorder, packaged recorder stress, playback, and MediaStore gates are promoted.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14TaskDeviceMediaDocSync
```

Expected reports:

```text
build/reports/nodejs/phase14-task-device-media-doc-sync.json
build/reports/nodejs/phase14-task-device-media-doc-sync.md
```
