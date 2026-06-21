# Final Node Platform Reports

Phase 11 tracks AutoJs6 Node Platform graduation. The final stage is stricter
than previous phases: required internal blockers must be closed or the final
decision remains `blocked`.

Current entry points:

- [FINAL_INTEGRATION_DEFINITION.md](FINAL_INTEGRATION_DEFINITION.md)
- [FINAL_BLOCKER_REGISTRY.md](FINAL_BLOCKER_REGISTRY.md)
- [FINAL_BLOCKER_REGISTRY.json](FINAL_BLOCKER_REGISTRY.json)

Host report tasks:

```powershell
.\gradlew.bat --console=plain :app:generateNodeFinalCapabilityTruth
.\gradlew.bat --console=plain :app:verifyNodeFinalFocusedGateTopology
```

Strict final gates such as `verifyNodeFinalCapabilityTruth`,
`verifyNodeFinalAdapterGate`, `verifyNodeFinalCrashGate`, and
`verifyNodeFinalIntegrationGate` are expected to remain `blocked` until the
required internal blockers are resolved.
