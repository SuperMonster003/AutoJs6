# Phase 14 Node v1.2 RC Checklist Gate

P14-38 adds the release-candidate checklist gate for Node Profile v1.2. The
Gradle gate is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14ReleaseCandidateChecklist
```

## Policy

The gate records `release_candidate_checklist_ready`. It verifies that the
Phase 14 summary is ready, required v1.2 RC gates are listed, release notes
draft boundaries are explicit, and v1.3 future promotion backlog is separated
from required v1.2 blockers.

This gate is not release tag approval. Before an actual RC tag, the production
release gate and packaged install-run evidence must be rerun for the exact
commit and selected device.

## Reports

Expected report artifacts:

```text
build/reports/nodejs/phase14-release-candidate-checklist.json
build/reports/nodejs/phase14-release-candidate-checklist.md
```
