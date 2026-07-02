# P15-25 Type Declaration Profiles

Status: ready

P15-25 updates Node Profile v1.3 TypeScript declaration profiles.

## Deployed

- Added `docs/nodejs/phase15/node-v1-3-type-declaration-profiles.json`.
- Added `docs/nodejs/phase15/PHASE15_NODE_V1_3_TYPE_DECLARATION_PROFILES.md`.
- Added `tools/nodejs/phase15/verify-node-phase15-type-declaration-profiles.js`.
- Added Gradle entry `:app:verifyNodePhase15TypeDeclarationProfiles`.
- Extended `docs/nodejs/types/autojs6-node/profile_capabilities.d.ts` with
  `NodeProfileV13CapabilityCatalog`, v1.3 capability state helpers, packaged
  evidence helpers, future-only helpers, stable-denied helpers, and
  `NodeProfileV13DeclarationMatrix`.
- Extended `sample/nodejs/typescript-smoke/src/main.ts` with v1.3 profile
  matrix assertions for provider POC rows, docs sync metadata, security corpus,
  stable-denied rows, and the packaged aggregate smoke row.

## Decision

- P15-25 decision: `type_declaration_profiles_v1_3_ready`.
- TypeScript smoke covers v1.3 profile helpers: `true`.
- Future-only and stable-denied methods visibly gated: `true`.
- Runtime authority changed: `false`.

## Verification

```powershell
node --check tools/nodejs/phase15/verify-node-phase15-type-declaration-profiles.js
node tools/nodejs/phase15/verify-node-phase15-type-declaration-profiles.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase15TypeDeclarationProfiles
```
