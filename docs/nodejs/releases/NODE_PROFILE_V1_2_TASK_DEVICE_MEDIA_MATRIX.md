# Node Profile v1.2 Task Device Media Matrix

This matrix records the Phase 14 `pro.tasks_device_media` documentation sync
state. It is evidence for P14-27 docs sync only; it does not promote any
provider beyond the gates listed here.

| Area | Source gate | Current v1.2 state | Runtime availability | Example policy | Remaining promotion evidence |
| --- | --- | --- | --- | --- | --- |
| Task database mutation | P14-17, P14-18, P14-19 | `provider_poc` / partial | JSON-safe task database mutation descriptors, schema migration/rollback contracts, and packaged scheduling smoke are documented. Live `require("tasks")` mutation remains unpromoted. | `tasks-work-manager` remains the only task/device/media focused smoke path. | Live task database provider, packaged row ownership, task removal cleanup, disclosure, Safe downgrade, and schema migration archives. |
| IntentTask/broadcast | P14-20, P14-21 | `provider_poc` / partial | Future `tasks.broadcast` descriptors, exported receiver policy, bounded payloads, and packaged trigger/remove contracts are documented. Runtime broadcast task authority remains unpromoted. | Broadcast examples remain static-or-denied. | Positive packaged trigger/remove, manifest synthesis, exported receiver policy, relaunch/crash, undeclared-capability, and cleanup archives. |
| Notifications | P14-22 | `provider_poc` / partial | Live owned `notify`/`cancel`/`cancelAll`, permission status, and settings handoff are documented. Packaged post/cancel and foreground disclosure remain unpromoted. | `notifications-power` remains migration evidence and guarded/denial-oriented. | Packaged post/cancel, foreground disclosure/stop action, Android 12/15+ permission matrix, advanced notification decision, and rollback cleanup archives. |
| Settings/power | P14-23 | `provider_poc` / partial | `device.power` battery optimization status/settings dry-run is documented. Direct power mutation, exact-alarm scheduling, wake locks, and background-launch bypass remain denied. | `notifications-power` may show status/settings migration but not OEM mutation. | Android 12/15+ OEM matrix, exact-alarm state, background-launch restrictions, reviewed prompt, and rollback archives. |
| Recorder sessions | P14-24, P14-25 | `provider_poc` / partial | `recorder.getStatus()` and packaged status/denial boundary smoke are documented. Real start/stop, pause/resume, and packaged microphone recording remain unpromoted. | `media-recorder` remains migration evidence and denial-oriented. | Runtime microphone prompt, foreground disclosure/stop action, session resources, temp output cleanup, crash cleanup, packaged start/stop stress, and Safe downgrade archives. |
| Playback and MediaStore | P14-26 | stable-denied / future-only | Read-only audio stream status and scoped `mediainfo` remain documented. Playback, playlists, scanFile, MediaStore query/mutation/open-file, broad media permissions, and raw media handles remain denied. | Playback and MediaStore examples are not runnable until a scoped provider gate exists. | Scoped playback/provider decision, playlist/queue decision, MediaStore permission matrix, packaged denial, privacy, and rollback archives. |

P14-27 docs sync verifies that this matrix, TypeScript declaration references,
Pro examples, migration cookbook, compatibility profile, security model, and
error catalog all use the same `provider_poc_not_promoted` decision for these
high-risk task/device/media surfaces.
