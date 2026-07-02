# P15-26 Migration And Pro Comparison

Status: ready

P15-26 updates migration, Pro comparison, compatibility, examples, and release
notes for Node Profile v1.3.

## Deployed

- Added `docs/nodejs/phase15/node-v1-3-migration-pro-comparison.json`.
- Added `docs/nodejs/phase15/PHASE15_NODE_V1_3_MIGRATION_PRO_COMPARISON.md`.
- Added `docs/nodejs/releases/NODE_PROFILE_V1_3_RELEASE_NOTES_DRAFT.md`.
- Added `tools/nodejs/phase15/verify-node-phase15-migration-pro-comparison.js`.
- Added Gradle entry `:app:verifyNodePhase15MigrationProComparison`.
- Updated migration, Pro comparison, Pro/AutoX, compatibility, examples, and
  release-note docs with v1.3 claim boundaries.

## Decision

- P15-26 decision: `migration_pro_comparison_v1_3_ready`.
- Claims match executable evidence: `true`.
- Docs imply raw Android object parity: `false`.
- Runtime authority changed: `false`.
- No newly promoted providers: `true`.

## Verification

```powershell
node --check tools/nodejs/phase15/verify-node-phase15-migration-pro-comparison.js
node tools/nodejs/phase15/verify-node-phase15-migration-pro-comparison.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase15MigrationProComparison
```
