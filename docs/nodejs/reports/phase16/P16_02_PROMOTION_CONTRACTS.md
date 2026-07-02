# P16-02 Promotion Contracts

Status: ready.

Gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase16PromotionContracts
```

Artifacts:

- `docs/nodejs/phase16/node-v1-4-promotion-contracts.json`
- `docs/nodejs/phase16/PHASE16_NODE_V1_4_PROMOTION_CONTRACTS.md`
- `tools/nodejs/phase16/verify-node-phase16-promotion-contracts.js`
- `build/reports/nodejs/phase16-promotion-contracts.json`
- `build/reports/nodejs/phase16-promotion-contracts.md`

Acceptance:

- The promotion state vocabulary is restricted to `promoted`, `provider_poc`,
  `future_only`, `stable_denied`, and `rejected`.
- No provider can become opt-in promoted without implementation, connected
  device, packaged APK, user-visible disclosure, rollback, security corpus,
  TypeScript declaration, migration/release docs, Safe downgrade, and
  raw-object denial regression evidence.
- The provider bars map P16 provider work to imported P15 backlog tasks and
  keep `mediastore_playback` `stable_denied`.
- Phase 16 docs and Gradle wiring expose the promotion contract gate.
