# P15-24 Node v1.3 Packaged Aggregate Smoke

Status: ready

P15-24 deploys the deterministic packaged aggregate smoke entry and report
contract for Node Profile v1.3.

## Deployed

- Added `docs/nodejs/phase15/node-v1-3-packaged-aggregate-smoke-plan.json`.
- Added `docs/nodejs/phase15/PHASE15_NODE_V1_3_PACKAGED_AGGREGATE_SMOKE.md`.
- Added `tools/nodejs/phase15/install-and-run-node-v13-packaged-aggregate-smoke.js`.
- Added Gradle entry `:app:installAndRunNodeV13PackagedAggregateSmoke`.
- Imported P15-04, P15-07, P15-08, P15-09, P15-10, P15-14, P15-16, P15-17,
  P15-18, P15-20, P15-21, and P15-23 generated reports.
- Required every aggregate suite to define a declared positive path or
  conditional skip, undeclared capability denial, packaged metadata mismatch
  denial, Safe downgrade behavior, cleanup, stable-denied boundary preservation
  where applicable, and report artifact coverage.

## Decision

- P15-24 decision: `packaged_aggregate_smoke_contract_ready`.
- Run mode: `host_contract_ready`.
- Runtime authority changed: `false`.
- Real device packaged install-run archive attached: `false`.
- Surface manifest state: `planned` by critical-risk policy.

## Verification

```powershell
node --check tools/nodejs/phase15/install-and-run-node-v13-packaged-aggregate-smoke.js
node tools/nodejs/phase15/install-and-run-node-v13-packaged-aggregate-smoke.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:installAndRunNodeV13PackagedAggregateSmoke
```
