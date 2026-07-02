# P14-36 Migration And Pro Comparison

Status: ready

P14-36 updates migration and Pro comparison docs for Node Profile v1.2.

## Deployed

- Added `docs/nodejs/phase14/node-v1-2-migration-pro-comparison.json`.
- Added `docs/nodejs/phase14/PHASE14_NODE_V1_2_MIGRATION_PRO_COMPARISON.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-migration-pro-comparison.js`.
- Added Gradle entry `:app:verifyNodePhase14MigrationProComparison`.
- Synced v1.2 migration claims in `MIGRATION_FROM_RHINO.md`,
  `INTEGRATION_STATUS_PRO9_COMPARISON.md`,
  `COMPATIBILITY_NOTES_PRO_AUTOX.md`, `COMPATIBILITY_PROFILE.md`, and
  `EXAMPLES.md`.
- Imported P13-56 plus P14-16, P14-27, P14-32, P14-33, P14-34, and P14-35
  generated reports.
- Required Pro UI/floaty, accessibility, screenshot/OCR, tasks,
  notifications, recorder, worker policy, packaged behavior, and rollback
  claims to match executable evidence.

## Decision

- P14-36 decision: `migration_pro_comparison_v1_2_ready`.
- Claims match executable evidence: `true`.
- Docs imply raw Android object parity: `false`.
- Runtime authority changed: `false`.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-migration-pro-comparison.js
node tools/nodejs/phase14/verify-node-phase14-migration-pro-comparison.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14MigrationProComparison
```
