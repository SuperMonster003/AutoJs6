# P14-35 Type Declaration Profiles

Status: ready

P14-35 updates Node Profile v1.2 TypeScript declaration profiles.

## Deployed

- Added `docs/nodejs/phase14/node-v1-2-type-declaration-profiles.json`.
- Added `docs/nodejs/phase14/PHASE14_NODE_V1_2_TYPE_DECLARATION_PROFILES.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-type-declaration-profiles.js`.
- Added Gradle entry `:app:verifyNodePhase14TypeDeclarationProfiles`.
- Extended `docs/nodejs/types/autojs6-node/profile_capabilities.d.ts` with
  `NodeProfileV12CapabilityCatalog`, v1.2 capability state helpers, packaged
  evidence helpers, future-only helpers, stable-denied helpers, and
  `NodeProfileV12DeclarationMatrix`.
- Extended `sample/nodejs/typescript-smoke/src/main.ts` with v1.2 profile
  matrix assertions for promoted callable declarations, provider POC rows,
  stable-denied rows, intentionally unsupported worker/process-worker rows,
  and the packaged aggregate smoke row.

## Decision

- P14-35 decision: `type_declaration_profiles_v1_2_ready`.
- TypeScript smoke covers v1.2 profile helpers: `true`.
- Future-only methods visibly gated: `true`.
- Runtime authority changed: `false`.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-type-declaration-profiles.js
node tools/nodejs/phase14/verify-node-phase14-type-declaration-profiles.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14TypeDeclarationProfiles
```
