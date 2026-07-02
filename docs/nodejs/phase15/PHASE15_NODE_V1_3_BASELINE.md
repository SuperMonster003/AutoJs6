# Phase 15 Node v1.3 Baseline

P15-00 records the Phase 15 baseline from the Phase 14 closeout handoff. The
Gradle gate is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15Baseline
```

The gate verifies:

- current branch is `feature/nodejs-runtime-v1.3`;
- Phase 14 closeout handoff report is `ready`;
- Phase 14 final blockers remain closed or intentionally deferred/rejected;
- `PHASE14_FUTURE_BACKLOG.json` exports 19 provider promotion backlog tasks;
- the Phase 15 task checklist imports that backlog and enumerates P15-00
  through P15-27 exactly once.

P15-00 does not change runtime authority.
