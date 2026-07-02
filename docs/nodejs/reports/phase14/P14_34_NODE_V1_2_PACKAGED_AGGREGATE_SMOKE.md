# P14-34 Node v1.2 Packaged Aggregate Smoke

Status: ready

P14-34 deploys the deterministic packaged aggregate smoke entry and report
contract for Node Profile v1.2.

## Deployed

- Added `docs/nodejs/phase14/node-v1-2-packaged-aggregate-smoke-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_NODE_V1_2_PACKAGED_AGGREGATE_SMOKE.md`.
- Added `tools/nodejs/phase14/install-and-run-node-v12-packaged-smoke.js`.
- Added Gradle entry `:app:installAndRunNodeV12PackagedSmoke`.
- Imported P14-07, P14-10, P14-11, P14-15, P14-19, P14-21, P14-22,
  P14-23, P14-25, P14-29, and P14-33 generated reports.
- Required every aggregate suite to define a declared positive path or
  conditional skip, undeclared capability denial, packaged metadata mismatch
  denial, Safe downgrade behavior, cleanup, and report artifact coverage.

## Decision

- P14-34 decision: `packaged_aggregate_smoke_contract_ready`.
- Run mode: `host_contract_ready`.
- Runtime authority changed: `false`.
- Real device packaged install-run archive attached: `false`.

## Verification

```powershell
node --check tools/nodejs/phase14/install-and-run-node-v12-packaged-smoke.js
node tools/nodejs/phase14/install-and-run-node-v12-packaged-smoke.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:installAndRunNodeV12PackagedSmoke
```
