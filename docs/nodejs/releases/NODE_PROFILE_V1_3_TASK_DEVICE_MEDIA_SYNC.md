# Node Profile v1.3 Task Device Media Sync

Status: future-only and stable-denied documentation sync

P15-22 records the v1.3 release-facing state for the
`pro.tasks_device_media` lane. No P15-12 through P15-20 provider is promoted
by this file; those gates remain partial evidence with
`provider_poc_not_promoted` wording. P15-21 records `mediastore_playback` as
`stable_denied`.

| Task | Provider area | Release-facing state |
| --- | --- | --- |
| `P15-12` | task database mutation | Partial evidence only; live mutation, ownership/id mapping, schema migration, packaged mutation smoke, cross-package denial, disclosure, and Safe downgrade archives remain future work. |
| `P15-13` | task schema rollback | Partial evidence only; live migration tests, corruption recovery, packaged rollback, backup/restore, Safe downgrade, and cross-package rollback archives remain future work. |
| `P15-14` | packaged task smoke | Partial evidence only; packaged positive task database smoke, relaunch, task-removal cleanup, disclosure, cross-package denial, and Safe downgrade archives remain future work. |
| `P15-15` | IntentTask/broadcast provider | Partial evidence only; live provider registration, manifest synthesis, exported policy, payload validation, cleanup, packaged trigger/remove, cross-package denial, and Safe downgrade archives remain future work. |
| `P15-16` | packaged broadcast smoke | Partial evidence only; positive trigger delivery, remove cleanup, relaunch/crash recovery, undeclared capability denial, exported receiver policy, metadata mismatch, and Safe downgrade archives remain future work. |
| `P15-17` | notifications | Partial evidence only; packaged post/cancel, foreground disclosure, Android permission matrix, advanced action decision, rollback, Safe downgrade, and raw-object security archives remain future work. |
| `P15-18` | settings/power | Partial evidence only; Android 12/15+ OEM matrix, exact-alarm state, background-launch restrictions, prompt/rollback evidence, packaged disclosure, Safe downgrade, and raw-object security archives remain future work. |
| `P15-19` | recorder provider | Partial evidence only; microphone prompt ownership, foreground disclosure/stop action, temp output cleanup, crash/task-removal cleanup, packaged start/stop stress, Safe downgrade, and raw-object security archives remain future work. |
| `P15-20` | packaged recorder smoke | Partial evidence only; positive packaged start/stop, lifecycle, quota/temp cleanup, denial, foreground disclosure, Safe downgrade, and raw-object security archives remain future work. |
| `P15-21` | MediaStore/playback | Ready stable-denied decision; playback, playlists, `scanFile`, MediaStore query/mutation, broad media-library permissions, and raw media handles stay denied until a future scoped provider gate passes. |

Release notes must keep `tasks-work-manager` as the only task/device/media
focused smoke path. `notifications-power` and `media-recorder` remain static,
guarded, or denial-oriented migration evidence until the matching provider
gate reports promoted evidence and the downstream security, type, migration,
and release sync gates also pass.
