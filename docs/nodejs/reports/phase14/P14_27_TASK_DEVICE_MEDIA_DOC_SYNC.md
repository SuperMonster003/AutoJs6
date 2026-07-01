# P14-27 Task Device Media Doc Sync

Status: ready.

P14-27 synchronizes the task/device/media documentation layer for Phase 14. It
does not promote additional runtime authority. It imports P14-17 through P14-26
provider POC and partial gates as the current v1.2 state, then verifies docs,
examples, TypeScript declarations, release matrix rows, migration cookbook,
security text, compatibility text, error guidance, testing docs, and CI gates.

## Deployed

- Added `docs/nodejs/phase14/task-device-media-doc-sync.json`.
- Added `docs/nodejs/phase14/PHASE14_TASK_DEVICE_MEDIA_DOC_SYNC.md`.
- Added
  `docs/nodejs/releases/NODE_PROFILE_V1_2_TASK_DEVICE_MEDIA_MATRIX.md`.
- Added
  `docs/nodejs/phase14/PHASE14_TASK_DEVICE_MEDIA_MIGRATION_COOKBOOK.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-task-device-media-doc-sync.js`.
- Added Gradle gate `:app:verifyNodePhase14TaskDeviceMediaDocSync`.
- Verified P14-17 through P14-26 generated reports, declarations,
  examples, release matrix, migration cookbook, compatibility profile, security
  model, error catalog, testing docs, and CI docs.
- Kept runtime authority unchanged and kept the surface state as
  `provider_poc_not_promoted`.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-task-device-media-doc-sync.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14TaskDeviceMediaDocSync
```

Expected reports:

```text
build/reports/nodejs/phase14-task-device-media-doc-sync.json
build/reports/nodejs/phase14-task-device-media-doc-sync.md
```
