# P14-33 Security Regression Corpus

Status: ready

P14-33 deploys the v1.2 security regression corpus expansion gate. The current
decision is `security_corpus_expanded_no_authority_promoted`: security coverage
requirements are now enumerated for Phase 14 high-risk provider families, but
no runtime authority is promoted by this task.

## Deployed

- Added `docs/nodejs/phase14/security-regression-corpus-plan.json`.
- Added `docs/nodejs/phase14/PHASE14_SECURITY_REGRESSION_CORPUS.md`.
- Added `docs/nodejs/security/NODE_PROFILE_V1_2_SECURITY_REGRESSION_CORPUS.json`.
- Added `tools/nodejs/phase14/verify-node-phase14-security-regression-corpus.js`.
- Added Gradle gate `:app:verifyNodePhase14SecurityRegressionCorpus`.
- Imported P14-07 through P14-32 generated reports.
- Required every tracked v1.2 high-risk capability family to include positive,
  negative, abuse, runtime guard, promotion precondition, and rollback entries.
- Covered raw object denial, path escape, permission bypass, lifecycle cleanup,
  packaged metadata mismatch, and Safe downgrade categories.

## Decision

- P14-33 decision: `security_corpus_expanded_no_authority_promoted`.
- Runtime authority changed: `false`.
- Release-blocking security corpus violations: `0`.
- P14-34 packaged aggregate smoke remains the next packaged execution gate.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-security-regression-corpus.js
node tools/nodejs/phase14/verify-node-phase14-security-regression-corpus.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14SecurityRegressionCorpus
```
