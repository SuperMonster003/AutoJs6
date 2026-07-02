# P15-23 Security Regression Corpus

P15-23 deploys the v1.3 security regression corpus gate. The current decision
updates the corpus from P14-33 and P15-04 through P15-22 without promoting
runtime authority.

P15-23 decision: `security_corpus_updated_no_authority_promoted`.

## Evidence

- Plan: `docs/nodejs/phase15/security-regression-corpus-plan.json`
- Corpus: `docs/nodejs/security/NODE_PROFILE_V1_3_SECURITY_REGRESSION_CORPUS.json`
- Documentation: `docs/nodejs/phase15/PHASE15_SECURITY_REGRESSION_CORPUS.md`
- Gate: `:app:verifyNodePhase15SecurityRegressionCorpus`
- Report JSON: `build/reports/nodejs/phase15-security-regression-corpus.json`
- Report Markdown: `build/reports/nodejs/phase15-security-regression-corpus.md`

## Decision

- P15-04 through P15-20 remain `partial` and unpromoted.
- P15-11 and P15-22 remain docs/type/release sync gates only.
- P15-21 remains `stable_denied` for MediaStore/playback.
- P15-23 remains `planned` in `phase15-surface-manifest.json` by critical-risk
  policy.
- Runtime authority changed: `false`.

## Verification

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15SecurityRegressionCorpus
```
