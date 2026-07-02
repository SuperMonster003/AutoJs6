# Phase 15 Node v1.3 Disclosure And Rollback Audit

This audit defines the proof paths that must exist before any v1.3 provider
can move from `provider_poc`, `future_only`, or `stable_denied` into
`promoted`. It does not change runtime authority.

Machine-readable source:

- `docs/nodejs/phase15/node-v1-3-disclosure-rollback-audit.json`

## Disclosure Fields

Every promotable high-privilege provider must provide:

- `highPrivilegePrompt`
- `manifestMetadata`
- `installReviewCopy`
- `runReviewCopy`
- `foregroundNotificationOwnership`
- `stopAction`
- `permissionDeniedDiagnostic`
- `undeclaredCapabilityDiagnostic`
- `privacyDisclosure`
- `taskRemovalCleanup`
- `relaunchBehavior`
- `crashRecoveryBehavior`
- `safeDowngradeDisclosure`

## Rollback Fields

Every promotable provider must provide:

- `disableFlag`
- `disableScope`
- `diagnosticCode`
- `userVisibleMessage`
- `safeFallback`
- `packagedDowngradeBehavior`
- `dataRetention`
- `reEnablePath`
- `nodeDoctorSignal`
- `crashCircuitBreakerMarker`

The crash circuit breaker threshold is `3 provider crashes within 24h per
package/profile/capability`. Required marker fields are `providerId`,
`profileId`, `capabilityId`, `packageIdentity`, `crashCount`, `firstCrashAt`,
`lastCrashAt`, and `disableReason`.

## Provider Audit Matrix

| Provider | Current state | Tasks | Disclosure proof | Rollback |
| --- | --- | --- | --- | --- |
| `live_ui` | `provider_poc` | `P15-04` | `phase15-packaged-live-ui-promotion.json` | `node.provider.live_ui.enabled` |
| `overlay` | `provider_poc` | `P15-05` to `P15-07` | `phase15-overlay-provider-promotion.json` | `node.provider.overlay.enabled` |
| `accessibility` | `provider_poc` | `P15-08` | `phase15-accessibility-promotion.json` | `node.provider.accessibility.enabled` |
| `media_projection` | `provider_poc` | `P15-09` | `phase15-media-projection-promotion.json` | `node.provider.media_projection.enabled` |
| `image_ocr` | `provider_poc` | `P15-10` | `phase15-image-ocr-promotion.json` | `node.provider.image_ocr.enabled` |
| `task_database` | `provider_poc` | `P15-12` to `P15-14` | `phase15-task-database-promotion.json` | `node.provider.task_database.enabled` |
| `intent_broadcast` | `provider_poc` | `P15-15`, `P15-16` | `phase15-intent-broadcast-promotion.json` | `node.provider.intent_broadcast.enabled` |
| `notifications` | `provider_poc` | `P15-17` | `phase15-notification-promotion.json` | `node.provider.notifications.enabled` |
| `settings_power` | `provider_poc` | `P15-18` | `phase15-settings-power-promotion.json` | `node.provider.settings_power.enabled` |
| `recorder` | `provider_poc` | `P15-19`, `P15-20` | `phase15-recorder-promotion.json` | `node.provider.recorder.enabled` |
| `mediastore_playback` | `stable_denied` | `P15-21` | `phase15-mediastore-playback-decision.json` | `node.provider.mediastore_playback.enabled` |

## Rules

- Safe remains the default profile.
- Provider disable flags are per provider and per packaged project.
- Foreground notification ownership and stop action proof are required before
  packaged high-privilege promotion.
- Safe downgrade behavior must be user-visible and testable.
- Missing disclosure or rollback evidence keeps the provider unpromoted.

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-disclosure-rollback-audit.js`
- Gradle gate: `:app:verifyNodePhase15DisclosureRollbackAudit`
- Reports:
  - `build/reports/nodejs/phase15-disclosure-rollback-audit.json`
  - `build/reports/nodejs/phase15-disclosure-rollback-audit.md`
