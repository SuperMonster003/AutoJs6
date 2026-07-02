# Phase 14 Promotion Contracts

This document defines the shared Node Profile v1.2 promotion contracts for
Phase 14. These contracts do not grant runtime authority by themselves. They
define the minimum evidence and policy vocabulary that later provider tasks
must satisfy before a Pro/Desktop capability can move from POC or future-only
state into opt-in promotion.

Machine-readable source:

- `docs/nodejs/phase14/phase14-promotion-contracts.json`

## Capability Promotion States

| State | Meaning | Release use |
| --- | --- | --- |
| `implemented` | Runtime/provider code exists behind an explicit capability or profile gate. | Not enough for release promotion without policy, device, packaged, rollback, and docs evidence. |
| `packaged_ready` | Packaged APK install/run, metadata, relaunch, task-removal, denial, and cleanup evidence exists where the capability can run packaged. | Required for packaged-capable high-privilege providers before v1.2 promotion. |
| `device_matrix_ready` | Required Android device matrix evidence exists, including permission and OEM behavior. | Required for high-privilege Android providers before promotion beyond host-only gates. |
| `policy_ready` | Threat model, privacy disclosure, rollback, stable errors, TypeScript, migration, and release docs are synchronized. | Required before a capability can move from future-only or POC into opt-in promotion. |
| `stable_denied` | The capability or authority is intentionally denied with a stable diagnostic surface and regression coverage. | Acceptable v1.2 final state when promotion requirements are not met. |
| `future_only` | Tracked as future work and excluded from v1.2 release-blocking counts. | Must remain visible in summary reports without granting runtime authority. |

## Report Conventions

Phase 14 generated reports use deterministic report names and machine-readable
JSON with markdown mirrors:

- Report name pattern: `phase14-<surface-or-task>.json`
- Markdown mirror: required
- Schema pattern: `autojs6-node-phase14-<surface-or-task>-v1`
- Required report fields: `schema`, `generatedAt`, `gate`, `task`, `status`,
  `checks`, `policyViolations`, `blockers`, and `decision`
- Status values: `passed`, `ready`, `partial`, `failed`, `blocked`, and
  `archived`

Blocker classifications:

| Classification | Meaning |
| --- | --- |
| `release_blocking` | Must be closed before v1.2 closeout can report ready. |
| `future_promotion` | Visible backlog for v1.3+ or later v1.2 follow-up, excluded from release-blocking counts. |
| `external` | Depends on unavailable native/runtime/device artifacts or long-running environment availability. |
| `intentional_non_goal` | Rejected or intentionally unsupported behavior with stable denial requirements. |

## Packaged High-Privilege Disclosure

The disclosure contract applies to these surfaces when they are promoted for
packaged APK execution:

- `live_ui`
- `overlay`
- `accessibility`
- `media_projection`
- `recorder`
- `task_database`
- `intent_broadcast`
- `notifications`
- `power_settings`
- `shell_root`
- `profile_switching`

Required disclosure fields:

- `manifestMetadata`
- `installReviewCopy`
- `runReviewCopy`
- `foregroundNotificationOwnership`
- `stopAction`
- `permissionDeniedDiagnostic`
- `undeclaredCapabilityDiagnostic`
- `taskRemovalCleanup`
- `relaunchBehavior`
- `crashRecoveryBehavior`
- `privacyDisclosure`
- `rollbackBehavior`

Raw Android object denials must remain explicit for `Context`, `Activity`,
`View`, `Window`, `Bitmap`, `ImageReader`, `MediaProjection`, `MediaRecorder`,
`Intent`, `BroadcastReceiver`, `PendingIntent`, `Notification`, `RemoteViews`,
`WakeLock`, `TimedTask`, and database cursor handles.

## Rollback And Crash Circuit Breaker

Provider rollback is scoped per provider and per packaged project. A promoted
provider must have Safe fallback behavior, Node Doctor diagnostics, and a
stable disable path.

Default crash circuit breaker:

- Threshold: `3 provider crashes within 24h per package/profile/capability`
- Action: disable the promoted provider and fall back to stable denial or
  Safe-profile behavior until user or developer review
- Required evidence: crash marker, provider id, profile id, capability id,
  package identity, last crash timestamp, and disable reason

Rollback fields required for promoted providers:

- `disableFlag`
- `diagnosticCode`
- `userVisibleMessage`
- `safeFallback`
- `dataRetention`
- `packagedDowngradeBehavior`
- `reEnablePath`

## Device Evidence Matrix

High-privilege Android providers need at least one Android 12 class device and
one Android 15+ class device before v1.2 promotion.

Required matrix fields:

- `deviceSerial`
- `deviceModel`
- `androidVersion`
- `apiLevel`
- `packageIdentity`
- `profile`
- `capabilities`
- `androidPermissionsRequested`
- `androidPermissionsGranted`
- `notificationPermissionState`
- `backgroundStartBehavior`
- `settingsRoundTripBehavior`
- `taskRemovalBehavior`
- `relaunchBehavior`
- `crashRecoveryBehavior`
- `foregroundDisclosureBehavior`
- `stopActionBehavior`
- `leakOrResourceSummary`
- `oemQuirks`
- `reportPath`

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-promotion-contracts.js`
- Gradle gate: `:app:verifyNodePhase14PromotionContracts`
- Reports:
  - `build/reports/nodejs/phase14-promotion-contracts.json`
  - `build/reports/nodejs/phase14-promotion-contracts.md`
