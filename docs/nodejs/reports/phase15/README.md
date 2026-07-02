# Phase 15 Reports

Phase 15 tracks Node Profile v1.3 provider promotion work after the Phase 14
v1.2 closeout handoff.

Primary checklist:

- `PHASE15_NODE_V1_3_PROVIDER_PROMOTION_TASKS.md`

Foundation gates:

- `:app:verifyNodePhase15Baseline`
- `:app:verifyNodePhase15SurfaceManifest`
- `:app:verifyNodePhase15PromotionContracts`
- `:app:verifyNodePhase15DisclosureRollbackAudit`

Phase 15 imports the Phase 14 future backlog for live UI, overlay,
accessibility, screenshot/OCR, task providers, notifications/power,
media/recorder, and MediaStore/playback. These items are v1.3 promotion work,
not v1.2 release blockers.

`P15-00` records the v1.3 baseline as `passed` when the Phase 14 closeout
handoff remains ready, the current branch is `feature/nodejs-runtime-v1.3`, and
the 19 provider backlog tasks are imported from
`PHASE14_FUTURE_BACKLOG.json`.

`P15-01` records the v1.3 surface manifest as `passed` when
`phase15-surface-manifest.json` maps `P15-00` through `P15-27` exactly once,
owns every imported Phase 14 backlog task, and declares profile, risk,
evidence class, gate, and rollback hook metadata for each task.

`P15-02` records the v1.3 promotion contracts as `ready` when the `promoted`,
`provider_poc`, `future_only`, `stable_denied`, and `rejected` vocabulary is
current and every promotable provider requires implementation, connected
device, packaged APK, disclosure, rollback, security, TypeScript, docs, Safe
downgrade, and raw-object denial evidence before opt-in promotion.

`P15-03` records the v1.3 disclosure and rollback audit as `ready` when every
promotable provider declares prompt, manifest metadata, install/run copy,
foreground notification ownership, stop action, privacy, Safe downgrade,
provider disable flag, diagnostic code, rollback proof path, and crash circuit
breaker marker requirements before runtime authority changes.

Provider promotion gates:

- `:app:verifyNodePhase15PackagedLiveUiPromotion`

`P15-04` currently reports `partial`: it imports P13/P14 packaged live UI
evidence and P15 disclosure/rollback requirements, keeps `live_ui` at
`provider_poc_not_promoted`, and lists the remaining packaged relaunch, task
removal cleanup, crash recovery, disclosure, Android 15+, and downstream
security/type/docs sync archives required before promotion.
