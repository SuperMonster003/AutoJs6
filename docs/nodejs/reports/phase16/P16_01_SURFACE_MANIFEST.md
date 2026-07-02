# P16-01 Surface Manifest

Status: ready

P16-01 records the Phase 16 surface manifest and maps P16-00 through P16-27 to
owner surfaces, risks, profiles, evidence classes, rollback hooks, and Gradle
gate names.

## Deployed

- Added `docs/nodejs/phase16/phase16-surface-manifest.json`.
- Added `docs/nodejs/phase16/PHASE16_SURFACE_MANIFEST.md`.
- Added `docs/nodejs/schemas/phase16_surface_manifest.schema.json`.
- Added `tools/nodejs/phase16/verify-node-phase16-surface-manifest.js`.
- Added Gradle entry `:app:verifyNodePhase16SurfaceManifest`.

## Decision

- P16-01 decision: `phase16_surface_manifest_ready`.
- P16 task mappings: `28`.
- Imported Phase 15 backlog tasks: `17`.
- Runtime authority changed: `false`.

## Verification

```powershell
node --check tools/nodejs/phase16/verify-node-phase16-surface-manifest.js
node tools/nodejs/phase16/verify-node-phase16-surface-manifest.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase16SurfaceManifest
```
