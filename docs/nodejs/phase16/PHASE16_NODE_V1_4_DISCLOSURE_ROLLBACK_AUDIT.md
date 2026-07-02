# Phase 16 Node v1.4 Disclosure And Rollback Audit

This audit defines the proof paths that must exist before any v1.4 provider
can move from `provider_poc`, `future_only`, or `stable_denied` into
`promoted`. It does not change runtime authority.

Machine-readable source:

- `docs/nodejs/phase16/node-v1-4-disclosure-rollback-audit.json`

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
| `live_ui` | `provider_poc` | `P16-04` | `phase16-packaged-live-ui-promotion.json` | `node.provider.live_ui.enabled` |
| `overlay` | `provider_poc` | `P16-05` to `P16-07` | `phase16-overlay-provider-promotion.json` | `node.provider.overlay.enabled` |
| `accessibility` | `provider_poc` | `P16-08` | `phase16-accessibility-promotion.json` | `node.provider.accessibility.enabled` |
| `media_projection` | `provider_poc` | `P16-09` | `phase16-media-projection-promotion.json` | `node.provider.media_projection.enabled` |
| `image_ocr` | `provider_poc` | `P16-10` | `phase16-image-ocr-promotion.json` | `node.provider.image_ocr.enabled` |
| `task_database` | `provider_poc` | `P16-12` to `P16-14` | `phase16-task-database-promotion.json` | `node.provider.task_database.enabled` |
| `intent_broadcast` | `provider_poc` | `P16-15`, `P16-16` | `phase16-intent-broadcast-promotion.json` | `node.provider.intent_broadcast.enabled` |
| `notifications` | `provider_poc` | `P16-17` | `phase16-notification-promotion.json` | `node.provider.notifications.enabled` |
| `settings_power` | `provider_poc` | `P16-18` | `phase16-settings-power-promotion.json` | `node.provider.settings_power.enabled` |
| `recorder` | `provider_poc` | `P16-19`, `P16-20` | `phase16-recorder-promotion.json` | `node.provider.recorder.enabled` |
| `mediastore_playback` | `stable_denied` | `P16-21` | `phase16-mediastore-playback-scope-decision.json` | `node.provider.mediastore_playback.enabled` |

## Rules

- Safe remains the default profile.
- Provider disable flags are per provider and per packaged project.
- Foreground notification ownership and stop action proof are required before
  packaged high-privilege promotion.
- Safe downgrade behavior must be user-visible and testable.
- Missing disclosure or rollback evidence keeps the provider unpromoted.
- Crash circuit breaker marker fields must be written before provider
  authority can be enabled.

## Verification

- Host verifier: `tools/nodejs/phase16/verify-node-phase16-disclosure-rollback-audit.js`
- Gradle gate: `:app:verifyNodePhase16DisclosureRollbackAudit`
- Reports:
  - `build/reports/nodejs/phase16-disclosure-rollback-audit.json`
  - `build/reports/nodejs/phase16-disclosure-rollback-audit.md`
