# P16-03 Disclosure And Rollback Audit

Status: ready.

Gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase16DisclosureRollbackAudit
```

Artifacts:

- `docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json`
- `docs/nodejs/phase16/PHASE16_NODE_V1_4_DISCLOSURE_ROLLBACK_AUDIT.md`
- `tools/nodejs/phase16/verify-node-phase16-disclosure-rollback-audit.js`
- `build/reports/nodejs/phase16-disclosure-rollback-audit.json`
- `build/reports/nodejs/phase16-disclosure-rollback-audit.md`

Acceptance:

- P16-02 promotion contracts pass before the audit runs.
- Every promotable provider declares disclosure fields, rollback fields,
  disable flag, diagnostic code, Safe fallback, foreground notification
  ownership, Stop action, and crash circuit breaker requirements.
- The audit maps every provider to its P16 task and imported P15 backlog task.
- `mediastore_playback` remains `stable_denied`.
