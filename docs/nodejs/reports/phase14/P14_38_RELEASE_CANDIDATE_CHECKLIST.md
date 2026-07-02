# P14-38 Release Candidate Checklist

Status: ready

P14-38 creates the Node Profile v1.2 release-candidate checklist gate.

## Deployed

- Added `docs/nodejs/phase14/node-v1-2-rc-checklist.json`.
- Added `docs/nodejs/phase14/PHASE14_NODE_V1_2_RC_CHECKLIST.md`.
- Added `docs/nodejs/releases/NODE_PROFILE_V1_2_RC_CHECKLIST.md`.
- Added `docs/nodejs/releases/NODE_PROFILE_V1_2_RELEASE_NOTES_DRAFT.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-release-candidate-checklist.js`.
- Added Gradle entry `:app:verifyNodePhase14ReleaseCandidateChecklist`.
- Imports the P14-37 summary, security corpus, packaged aggregate smoke, type
  declaration profile report, migration comparison report, and docs sync
  reports.
- Separates required v1.2 RC blockers from the v1.3 future promotion backlog.

## Decision

- P14-38 decision: `release_candidate_checklist_ready`.
- Summary gate ready: `true`.
- Required v1.2 blockers separated: `true`.
- Future backlog separated: `true`.
- Release tag approval: `false`.
- Runtime authority changed: `false`.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-release-candidate-checklist.js
node tools/nodejs/phase14/verify-node-phase14-release-candidate-checklist.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14ReleaseCandidateChecklist
```
