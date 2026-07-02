# Phase 15 Task Device Media Doc Sync

Status: ready.

P15-22 synchronizes the v1.3 task/device/media lane after P15-12 through
P15-21. The source gates are executable but mostly partial; P15-21 is a
stable-denied MediaStore/playback decision. This sync does not promote runtime
authority.

## Imported Evidence

- P15-12 task database promotion remains partial and unpromoted.
- P15-13 task schema rollback remains partial and unpromoted.
- P15-14 packaged task smoke remains partial and unpromoted.
- P15-15 IntentTask/broadcast promotion remains partial and unpromoted.
- P15-16 packaged broadcast smoke remains partial and unpromoted.
- P15-17 notification promotion remains partial and unpromoted.
- P15-18 settings/power promotion remains partial and unpromoted.
- P15-19 recorder promotion remains partial and unpromoted.
- P15-20 packaged recorder smoke remains partial and unpromoted.
- P15-21 MediaStore/playback decision is ready and stable-denied.

## Synchronized State

- `tasks-work-manager` remains the only task/device/media focused Pro smoke
  path.
- `notifications-power` and `media-recorder` remain static, guarded, or
  denial-oriented migration evidence.
- TypeScript declaration notes keep task database mutation, IntentTask,
  broadcast registration, notification foreground ownership, OEM power
  mutation, real recorder sessions, packaged recorder start/stop, playback,
  MediaStore, and broad media-library permissions future-only or
  stable-denied.
- Compatibility, security, error, testing, CI, and release-facing text all use
  the same `provider_poc_not_promoted` plus `mediastore_playback`
  `stable_denied` state.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase15/verify-node-phase15-task-device-media-doc-sync.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15TaskDeviceMediaDocSync
```

Expected reports:

```text
build/reports/nodejs/phase15-task-device-media-doc-sync.json
build/reports/nodejs/phase15-task-device-media-doc-sync.md
```
