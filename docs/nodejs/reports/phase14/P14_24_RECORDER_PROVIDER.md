# P14-24 Recorder Provider

Status: partial.

P14-24 deploys the real recorder session promotion gate. It imports P13-26
recorder status and fail-closed start/stop evidence, then defines the runtime
permission, foreground disclosure, session ownership, temporary output, crash
cleanup, packaged stress, and rollback evidence required before real microphone
capture can be promoted.

The current runtime evidence covers `recorder.getStatus()`, exact
`media.recording` capability denial, Android permission metadata, packaged
status/denial smoke, and raw object denial. `recorder.start()` and
`recorder.stop()` remain denied until the future archives are produced.

## Deployed

- Added `docs/nodejs/phase14/recorder-provider-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_RECORDER_PROVIDER.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-recorder-provider.js`.
- Added Gradle gate `:app:verifyNodePhase14RecorderProvider`.
- Imported P13-26 `recorder.getStatus()` evidence,
  `autojs6-node-recorder-status-v1` status snapshots, packaged
  media/recorder boundary smoke, and exact `media.recording` denial.
- Recorded real session requirements for runtime `RECORD_AUDIO` prompt,
  foreground microphone disclosure, stop action, ResourceRegistry session
  ownership, scoped temporary output ownership, provider destroy cleanup, crash
  cleanup, task removal cleanup, Safe downgrade, and packaged stress.
- Kept `recorder.start()`, `recorder.stop()`, pause/resume, screen recording,
  internal/playback audio capture, raw `MediaRecorder`, raw `AudioRecord`,
  raw `AudioPlaybackCaptureConfiguration`, `MediaProjection`,
  `ParcelFileDescriptor`, raw file descriptors, `Uri`, `Context`, foreground
  service handles, and Java objects unpromoted.

## Open Future-Promotion Archives

- `recorder_runtime_permission_prompt_archive`
- `recorder_foreground_disclosure_stop_action_archive`
- `recorder_temp_output_ownership_cleanup_archive`
- `recorder_crash_cleanup_session_resource_archive`
- `recorder_packaged_start_stop_stress_archive`

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-recorder-provider.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14RecorderProvider
```

Expected reports:

```text
build/reports/nodejs/phase14-recorder-provider.json
build/reports/nodejs/phase14-recorder-provider.md
```
