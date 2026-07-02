# Phase 16 Node v1.4 Provider Promotion Tasks

Status: initial executable plan for `feature/nodejs-runtime-v1.4`.

Phase 16 starts from the Phase 15 v1.3 summary handoff. Phase 15 closed the
v1.3 summary handoff with zero policy violations and zero release-blocking
reports. The remaining provider POC and stable-denied work is imported here as
v1.4 future promotion backlog, not as a v1.3 regression.

Primary input:

- [`PHASE15_SUMMARY.md`](../phase15/PHASE15_SUMMARY.md)
- [`PHASE15_FUTURE_BACKLOG.json`](../phase15/PHASE15_FUTURE_BACKLOG.json)
- [`PHASE15_NEXT_BRANCH_HANDOFF.md`](../phase15/PHASE15_NEXT_BRANCH_HANDOFF.md)

## v1.4 Goal

Node Profile v1.4 should convert selected v1.3 provider POC reports into real
opt-in Pro/Desktop provider promotion only when implementation, connected
device evidence, packaged APK behavior, user-visible disclosure, rollback,
security regression coverage, TypeScript declarations, migration docs, and
release notes all agree.

The Safe profile remains conservative. Provider promotion must stay opt-in,
diagnosable, reversible, and scoped to JSON-safe or opaque-handle boundaries.

## Imported v1.4 Backlog

| Phase 15 task | Phase 16 lane | v1.4 direction |
| --- | --- | --- |
| `P15-04` | Pro UI | Close packaged live UI relaunch, crash recovery, and disclosure evidence. |
| `P15-05` | Pro UI | Promote Android overlay provider permission-granted path. |
| `P15-06` | Pro UI | Close overlay permission and OEM matrix archives. |
| `P15-07` | Pro UI | Add packaged overlay positive install-run smoke and stress evidence. |
| `P15-08` | Pro automation | Close packaged accessibility lifecycle and selector/action matrix evidence. |
| `P15-09` | Pro media | Promote Activity-owned MediaProjection provider. |
| `P15-10` | Pro media | Promote advanced image/OCR provider and packaged evidence. |
| `P15-12` | Pro tasks | Promote JSON-safe task database mutations. |
| `P15-13` | Pro tasks | Close task schema migration and rollback evidence. |
| `P15-14` | Pro tasks | Add packaged task scheduling install-run smoke. |
| `P15-15` | Pro tasks | Promote JSON-safe IntentTask/broadcast provider. |
| `P15-16` | Pro tasks | Add packaged broadcast trigger/remove smoke. |
| `P15-17` | Pro device | Promote notification ownership and foreground disclosure behavior. |
| `P15-18` | Pro device | Close settings/power OEM and background-launch matrix. |
| `P15-19` | Pro media | Promote real recorder session provider. |
| `P15-20` | Pro media | Add packaged recorder/media stress evidence. |
| `P15-21` | Pro media | Revisit scoped MediaStore/playback while preserving stable denial by default. |

## Completion Policy

A Phase 16 task can be marked complete only when all applicable items are true:

- The Gradle gate listed for that task exists and passes.
- Generated JSON and Markdown reports are committed or reproducible.
- Runtime authority changes are backed by implementation, device, packaged,
  disclosure, rollback, security, docs, type, and release-note evidence.
- Stable denial branches stay tested for every authority that remains
  unsupported.
- Docs, examples, TypeScript declarations, security corpus, CI docs,
  migration docs, and release notes are synchronized.

## Task Checklist

### P0 Baseline And Promotion Contract Refresh

- [x] P16-00: Record Phase 16 baseline from the Phase 15 summary handoff.
  - Inputs: Phase 15 summary JSON, future backlog export, next-branch handoff,
    current branch name.
  - Output: `build/reports/nodejs/phase16-baseline-verification.json`.
  - Gate: `:app:verifyNodePhase16Baseline`.
  - Acceptance: confirms Phase 15 summary handoff remains `ready`, imports the
    v1.4 future backlog, and fails if release-blocking reports reappear.
  - Landed:
    - `docs/nodejs/phase16/node-v1-4-baseline.json`
    - `docs/nodejs/phase16/PHASE16_NODE_V1_4_BASELINE.md`
    - `tools/nodejs/phase16/verify-node-phase16-baseline.js`
    - `app/node-phase16-gate.gradle.kts`
    - `build/reports/nodejs/phase16-baseline-verification.json`
    - `build/reports/nodejs/phase16-baseline-verification.md`

- [x] P16-01: Create Phase 16 surface manifest.
  - Scope: map every P16 task to owner surface, source backlog task, profile,
    risk, evidence class, and rollback hook.
  - Gate: `:app:verifyNodePhase16SurfaceManifest`.
  - Acceptance: P16-00 through P16-27 map exactly once and every imported P15
    backlog task is owned.
  - Landed:
    - `docs/nodejs/phase16/phase16-surface-manifest.json`
    - `docs/nodejs/phase16/PHASE16_SURFACE_MANIFEST.md`
    - `docs/nodejs/schemas/phase16_surface_manifest.schema.json`
    - `tools/nodejs/phase16/verify-node-phase16-surface-manifest.js`
    - `build/reports/nodejs/phase16-surface-manifest-verification.json`
    - `build/reports/nodejs/phase16-surface-manifest-verification.md`

- [x] P16-02: Refresh v1.4 opt-in promotion bars.
  - Gate: `:app:verifyNodePhase16PromotionContracts`.
  - Acceptance: no provider can become opt-in promoted without device,
    packaged, disclosure, rollback, security, docs, and type evidence.
  - Landed:
    - `docs/nodejs/phase16/node-v1-4-promotion-contracts.json`
    - `docs/nodejs/phase16/PHASE16_NODE_V1_4_PROMOTION_CONTRACTS.md`
    - `tools/nodejs/phase16/verify-node-phase16-promotion-contracts.js`
    - `docs/nodejs/reports/phase16/P16_02_PROMOTION_CONTRACTS.md`
    - `build/reports/nodejs/phase16-promotion-contracts.json`
    - `build/reports/nodejs/phase16-promotion-contracts.md`

- [x] P16-03: Add v1.4 disclosure and rollback audit gate.
  - Gate: `:app:verifyNodePhase16DisclosureRollbackAudit`.
  - Acceptance: every promotable provider declares rollback, disclosure, Safe
    downgrade, and crash-breaker proof paths before runtime authority changes.
  - Landed:
    - `docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json`
    - `docs/nodejs/phase16/PHASE16_NODE_V1_4_DISCLOSURE_ROLLBACK_AUDIT.md`
    - `tools/nodejs/phase16/verify-node-phase16-disclosure-rollback-audit.js`
    - `docs/nodejs/reports/phase16/P16_03_DISCLOSURE_ROLLBACK_AUDIT.md`
    - `build/reports/nodejs/phase16-disclosure-rollback-audit.json`
    - `build/reports/nodejs/phase16-disclosure-rollback-audit.md`

### P1 Pro UI, Overlay, Automation, Screenshot, OCR

- [ ] P16-04: Close packaged live UI relaunch and crash recovery.
  - Source backlog: `P15-04`.
  - Gate: `:app:verifyNodePhase16PackagedLiveUiPromotion`.
  - Partial gate deployed:
    - `docs/nodejs/phase16/packaged-live-ui-promotion-plan.json`
    - `docs/nodejs/phase16/PHASE16_PACKAGED_LIVE_UI_PROMOTION.md`
    - `tools/nodejs/phase16/verify-node-phase16-packaged-live-ui-promotion.js`
    - `docs/nodejs/reports/phase16/P16_04_PACKAGED_LIVE_UI_PROMOTION.md`
    - `build/reports/nodejs/phase16-packaged-live-ui-promotion.json`
    - `build/reports/nodejs/phase16-packaged-live-ui-promotion.md`
  - Remaining: packaged relaunch, task-removal cleanup, crash recovery,
    reviewed disclosure, Android 15+, and P16 security/type/docs sync evidence.

- [ ] P16-05: Promote Android overlay provider permission-granted path.
  - Source backlog: `P15-05`.
  - Gate: `:app:verifyNodePhase16OverlayProviderPromotion`.
  - Partial gate deployed:
    - `docs/nodejs/phase16/overlay-provider-promotion-plan.json`
    - `docs/nodejs/phase16/PHASE16_OVERLAY_PROVIDER_PROMOTION.md`
    - `tools/nodejs/phase16/verify-node-phase16-overlay-provider-promotion.js`
    - `docs/nodejs/reports/phase16/P16_05_OVERLAY_PROVIDER_PROMOTION.md`
    - `build/reports/nodejs/phase16-overlay-provider-promotion.json`
    - `build/reports/nodejs/phase16-overlay-provider-promotion.md`
  - Remaining: Android 12/15+ permission-granted archives, foreground
    disclosure/stop action, owner cleanup stress, packaged positive smoke,
    OEM matrix, and P16 security/type/docs sync evidence.
  - Open blockers: `overlay_permission_granted_android12_archive`,
    `overlay_permission_granted_android15_archive`,
    `overlay_foreground_disclosure_stop_action_archive`,
    `overlay_owner_cleanup_leak_stress_archive`,
    `packaged_overlay_positive_smoke_archive`, `overlay_oem_matrix_archive`,
    and `phase16_security_type_doc_sync`.

- [ ] P16-06: Close overlay permission and OEM matrix.
  - Source backlog: `P15-06`.
  - Gate: proposed `:app:verifyNodePhase16OverlayOemMatrix`.

- [ ] P16-07: Add packaged overlay positive smoke and stress.
  - Source backlog: `P15-07`.
  - Gate: proposed `:app:installAndRunNodeV14PackagedOverlaySmoke`.

- [ ] P16-08: Close accessibility lifecycle and selector/action matrix.
  - Source backlog: `P15-08`.
  - Gate: proposed `:app:verifyNodePhase16AccessibilityPromotion`.

- [ ] P16-09: Promote Activity-owned MediaProjection provider.
  - Source backlog: `P15-09`.
  - Gate: proposed `:app:verifyNodePhase16MediaProjectionPromotion`.

- [ ] P16-10: Promote advanced image and OCR packaged evidence.
  - Source backlog: `P15-10`.
  - Gate: proposed `:app:verifyNodePhase16ImageOcrPromotion`.

- [ ] P16-11: Sync UI/automation/media docs, examples, declarations, and release notes.
  - Gate: proposed `:app:verifyNodePhase16UiAutomationMediaDocSync`.

### P2 Pro Task, Device, And Media Providers

- [ ] P16-12: Promote JSON-safe task database mutations.
  - Source backlog: `P15-12`.
  - Gate: proposed `:app:verifyNodePhase16TaskDatabasePromotion`.

- [ ] P16-13: Close task schema migration and rollback evidence.
  - Source backlog: `P15-13`.
  - Gate: proposed `:app:verifyNodePhase16TaskSchemaRollback`.

- [ ] P16-14: Add packaged task scheduling smoke.
  - Source backlog: `P15-14`.
  - Gate: proposed `:app:installAndRunNodeV14PackagedTaskSmoke`.

- [ ] P16-15: Promote JSON-safe IntentTask and broadcast provider.
  - Source backlog: `P15-15`.
  - Gate: proposed `:app:verifyNodePhase16IntentBroadcastPromotion`.

- [ ] P16-16: Add packaged broadcast trigger/remove smoke.
  - Source backlog: `P15-16`.
  - Gate: proposed `:app:installAndRunNodeV14PackagedBroadcastSmoke`.

- [ ] P16-17: Promote notification ownership and foreground disclosure behavior.
  - Source backlog: `P15-17`.
  - Gate: proposed `:app:verifyNodePhase16NotificationPromotion`.

- [ ] P16-18: Close settings/power OEM and background-launch matrix.
  - Source backlog: `P15-18`.
  - Gate: proposed `:app:verifyNodePhase16SettingsPowerPromotion`.

- [ ] P16-19: Promote real recorder session provider.
  - Source backlog: `P15-19`.
  - Gate: proposed `:app:verifyNodePhase16RecorderPromotion`.

- [ ] P16-20: Add packaged recorder and media stress evidence.
  - Source backlog: `P15-20`.
  - Gate: proposed `:app:installAndRunNodeV14PackagedRecorderSmoke`.

- [ ] P16-21: Revisit scoped MediaStore and playback provider boundary.
  - Source backlog: `P15-21`.
  - Gate: proposed `:app:verifyNodePhase16MediaStorePlaybackScopeDecision`.
  - Acceptance: preserve `stable_denied` unless scoped provider evidence is
    complete.

- [ ] P16-22: Sync task/device/media docs, examples, declarations, and release notes.
  - Gate: proposed `:app:verifyNodePhase16TaskDeviceMediaDocSync`.

### P3 Security, Types, Packaged Aggregate, And Migration

- [ ] P16-23: Update v1.4 security regression corpus.
  - Gate: proposed `:app:verifyNodePhase16SecurityRegressionCorpus`.

- [ ] P16-24: Add v1.4 packaged aggregate smoke.
  - Gate: proposed `:app:installAndRunNodeV14PackagedAggregateSmoke`.

- [ ] P16-25: Update TypeScript declaration authority profile for v1.4.
  - Gate: proposed `:app:verifyNodePhase16TypeDeclarationProfiles`.

- [ ] P16-26: Update migration, Pro comparison, compatibility, and release notes.
  - Gate: proposed `:app:verifyNodePhase16MigrationProComparison`.

### P4 Summary And Handoff

- [ ] P16-27: Generate Phase 16 summary and next branch handoff.
  - Gate: proposed `:app:generateNodePhase16Summary`.
  - Acceptance: zero open v1.4 release blockers, future backlog visible, and
    next branch handoff documented.

## Suggested Initial Execution Order

1. Run P16-00 through P16-03 first to lock ownership, promotion bars, and
   rollback/disclosure rules.
2. Start with P16-04 through P16-07 because packaged live UI and overlay have
   the clearest v1.4 user-facing payoff.
3. Run P16-08 through P16-10 only after disclosure and resource cleanup are
   stable for UI/overlay.
4. Promote task database before IntentTask/broadcast because broadcast rows
   depend on task ownership and rollback conventions.
5. Keep P16-21 stable-denied until a scoped MediaStore/playback provider has
   complete permission, privacy, packaged, rollback, and raw-object denial
   evidence.
