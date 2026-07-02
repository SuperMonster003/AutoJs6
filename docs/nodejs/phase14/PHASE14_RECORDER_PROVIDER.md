# Phase 14 Recorder Provider

Status: partial.

P14-24 records the real recorder session promotion gate for Node Profile v1.2.
It imports P13-26 media/recorder/mediainfo evidence: `recorder.getStatus()` is
available behind exact `media.recording`, while `recorder.start()` and
`recorder.stop()` remain fail-closed with
`ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`.

This gate does not promote real microphone capture yet. Recorder start/stop
requires a runtime `RECORD_AUDIO` prompt, foreground microphone disclosure and
stop action, ResourceRegistry-backed session ownership, scoped temporary output
ownership, provider-destroy cleanup, crash cleanup, packaged stress evidence,
stable denial without capability or Android permission, and rollback behavior.

## Imported Evidence

- P13-26 records `recorder.getStatus()` with the
  `autojs6-node-recorder-status-v1` schema and fail-closed `start` / `stop`.
- `NodeMediaBridgeInstrumentationTest` verifies exact `media.recording`
  capability requirements, status snapshots, and start denial.
- Packaged media/recorder smoke verifies module resolution, status snapshots,
  start denial, and exact-capability denial for missing `media.recording`.
- Type declarations keep `recorder.start()` and `recorder.stop()` returning
  `Promise<never>` until the real provider is promoted.

## Promotion Policy

Future promotion must own recorder sessions as opaque bridge resources. A
session may expose only JSON-safe state and owned output descriptors. Raw
`MediaRecorder`, `AudioRecord`, `AudioManager`,
`AudioPlaybackCaptureConfiguration`, `MediaProjection`, `ParcelFileDescriptor`,
`Uri`, `Context`, foreground service handles, file descriptors, and Java
objects must not cross into Node.

## Open Archives

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
