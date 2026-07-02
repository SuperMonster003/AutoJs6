# Phase 14 Node v1.2 Summary Gate

P14-37 adds the generated summary for Phase 14. The Gradle gate is:

```powershell
.\gradlew.bat --console=plain :app:generateNodePhase14Summary
```

## Closeout Policy

P14-37 records the summary decision as `phase14_summary_gate_ready`. The
summary gate imports the Phase 14 task checklist, Phase 14 surface manifest,
final blocker registry, Phase 13 capability truth report, P14-35 type
declaration profile report, P14-36 migration comparison report, and all current
Phase 14 generated reports.

The gate requires:

- zero open final blockers;
- zero policy violations;
- zero release-blocking reports.

Reports with `future_promotion`, `external`, or `intentional_non_goal`
blockers stay visible as backlog and are not counted as release-blocking
reports. P14-38 and P14-39 remain open closeout handoff tasks until they are
deployed.

## Reports

Expected report artifacts:

```text
build/reports/nodejs/phase14-summary.json
build/reports/nodejs/phase14-summary.md
docs/nodejs/reports/phase14/PHASE14_SUMMARY.md
```
