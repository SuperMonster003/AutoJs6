# P14-32 Desktop Runtime Doc Sync

Status: ready

P14-32 deploys the v1.2 desktop runtime documentation and release-policy sync
gate. The current decision is to keep worker runtime support intentionally
unsupported for v1.2 and document that process-worker replacement remains a
no-facade decision.

## Deployed

- Added `docs/nodejs/phase14/desktop-runtime-doc-sync.json`.
- Added `docs/nodejs/phase14/PHASE14_DESKTOP_RUNTIME_DOC_SYNC.md`.
- Added `docs/nodejs/phase14/PHASE14_DESKTOP_RUNTIME_MIGRATION_COOKBOOK.md`.
- Added `docs/nodejs/releases/NODE_PROFILE_V1_2_DESKTOP_RUNTIME_MATRIX.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-desktop-runtime-doc-sync.js`.
- Added Gradle gate `:app:verifyNodePhase14DesktopRuntimeDocSync`.
- Imported P14-28, P14-29, P14-30, and P14-31 generated reports.
- Synchronized TypeScript declaration references, worker design docs, desktop
  examples, release matrix, migration cookbook, compatibility profile, security
  model, security corpus, error catalog, Pro/AutoX migration notes, testing,
  and CI docs.

## Decision

- P14-32 release decision: `worker_intentionally_unsupported_v1_2`.
- Runtime authority changed: `false`.
- Runnable worker support promoted: `false`.
- Packaged worker support promoted: `false`.
- Process-worker facade promoted: `false`.
- Raw process authority promoted: `false`.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-desktop-runtime-doc-sync.js
node tools/nodejs/phase14/verify-node-phase14-desktop-runtime-doc-sync.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14DesktopRuntimeDocSync
```
