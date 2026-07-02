# Phase 14 Task Device Media Migration Cookbook

This cookbook records the v1.2 migration posture for task/device/media APIs. It
is documentation evidence for P14-27 and does not promote additional runtime
authority.

## Runnable Path

`sample/nodejs/pro-parity-suite:tasks-work-manager` remains the focused
task/device/media smoke path. It exercises `work_manager.scheduleOnce()`,
`work_manager.list()`, and `work_manager.cancel()` for one-shot WorkManager
metadata. Daily/weekly/user task database mutation, IntentTask/broadcast
registration, packaged task rows, and `require("tasks")` remain gated by
P14-17 through P14-21 evidence.

## Guarded Migration Paths

`sample/nodejs/pro-parity-suite:notifications-power` documents JSON-safe
notification permission/settings and `device.power` battery optimization
status/settings dry-run. It must stay guarded until notification packaged
post/cancel, foreground disclosure/stop action, Android permission matrices,
OEM power matrices, exact-alarm state, background-launch behavior, and rollback
archives are promoted.

`sample/nodejs/pro-parity-suite:media-recorder` documents read-only
`media.getAudioStreamInfo()`, scoped `mediainfo.read()`, `recorder.getStatus()`,
and fail-closed `recorder.start()`. It must stay denial-oriented until runtime
microphone prompt, foreground disclosure, session handles, temporary output
cleanup, crash cleanup, packaged start/stop stress, playback, and MediaStore
provider evidence are promoted.

## Stable Denials

- `require("tasks")`, global `tasks`, daily/weekly mutation, and IntentTask
  mutation remain unpromoted unless a future task provider gate supplies owner
  scoped descriptors, packaged evidence, rollback, and stable errors.
- Notification actions, listeners, custom layouts, foreground disclosure stop
  action, advanced styles, and packaged post/cancel remain future-promotion
  work.
- Direct power-manager mutation, wake locks, exact-alarm scheduling, and
  background-launch bypass remain denied.
- Real recorder sessions, packaged recorder start/stop, playback, playlists,
  `scanFile`, MediaStore query/mutation/open-file, broad media-library
  permissions, raw media handles, raw file descriptors, and Java objects remain
  denied unless a full scoped provider gate is opened.

## TypeScript Declarations

The checked-in declarations are useful for migration code and denial-oriented
tests. They should be read with the v1.2 matrix: typed status/settings helpers
do not imply packaged or mutation promotion, and typed stable-denial methods
such as recorder start/stop still return `Promise<never>` until their provider
gates are promoted.
