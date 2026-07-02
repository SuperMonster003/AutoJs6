# Phase 16 Node v1.4 Baseline

P16-00 records the Phase 16 baseline from the Phase 15 summary handoff. The
Gradle gate is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase16Baseline
```

The gate verifies:

- current branch is `feature/nodejs-runtime-v1.4`;
- Phase 15 summary report is `ready`;
- Phase 15 release-blocking reports and policy violations remain zero;
- `PHASE15_FUTURE_BACKLOG.json` exports 17 future-promotion reports and the
  v1.4 next branch target;
- the Phase 16 task checklist imports that backlog and enumerates P16-00
  through P16-27 exactly once.

P16-00 does not change runtime authority.
