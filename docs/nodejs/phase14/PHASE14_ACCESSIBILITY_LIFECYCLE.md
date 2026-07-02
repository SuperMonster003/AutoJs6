# Phase 14 Accessibility Lifecycle

Machine-readable source:

- `docs/nodejs/phase14/accessibility-lifecycle-plan.json`

## Status

Status: partial

P14-11 defines the packaged accessibility-service lifecycle fixture and the
archive requirements needed before accessibility selector promotion can advance.
The fixture builds a packaged APK that declares the `accessibility` Node
capability and `android.permission.BIND_ACCESSIBILITY_SERVICE`, then emits
deterministic markers for metadata, privacy disclosure policy, accessibility
settings contract, packaged policy, disabled-state denial, raw global absence,
unsupported helper absence, and zero pending dispatch.

This is not a promotion claim. Android accessibility services can only be
enabled by the user, so the task remains open until install-run archives record
the settings round trip, user-enabled service state, task removal, relaunch,
service disconnect, and missing-capability denial paths on the device matrix.

Manual archive flags:

- `requiresUserEnabledAccessibilityService`: `true`
- `requiresSettingsRoundTrip`: `true`
- `mustNotAutoEnableService`: `true`

## Fixture

| Field | Value |
| --- | --- |
| Android test class | `EmbeddedNodePackagedApkSmokeTest` |
| Build method | `buildAccessibilityLifecycleEmbeddedNodePackagedApk` |
| Builder method | `buildPackagedAccessibilityLifecycleApk` |
| Project | `PackagedNodeAccessibilityLifecycleSmoke` |
| Package | `org.autojs6.packaged.node.accessibility.lifecycle.smoke` |
| APK | `PackagedNodeAccessibilityLifecycleSmoke_v1.0.0.apk` |

Required Node permission:

- `accessibility`

Required Android permission:

- `android.permission.BIND_ACCESSIBILITY_SERVICE`

Denied methods covered while the service is disabled or unavailable:

- `findOne`
- `findAll`
- `click`
- `longClick`
- `setText`
- `scrollForward`
- `scrollBackward`

## Required Runtime Markers

- `packaged.accessibility_lifecycle.ready=true`
- `packaged.accessibility_lifecycle.metadata=true`
- `packaged.accessibility_lifecycle.privacy_policy=true`
- `packaged.accessibility_lifecycle.settings_contract=true`
- `packaged.accessibility_lifecycle.packaged_policy=true`
- `packaged.accessibility_lifecycle.disabled_state=true`
- `packaged.accessibility_lifecycle.denial_paths=true`
- `packaged.accessibility_lifecycle.settings_intent=android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS`
- `packaged.accessibility_lifecycle.raw_globals_absent=true`
- `packaged.accessibility_lifecycle.unsupported_helpers_absent=true`
- `packaged.accessibility_lifecycle.pending=0`

## Open Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `accessibility_packaged_service_enabled_archive` | `future_promotion` | Archived packaged install-run with user-enabled accessibility service and provider availability on Android 12 and Android 15+. |
| `accessibility_settings_round_trip_archive` | `future_promotion` | Accessibility settings launch guidance and return-to-app state recorded without auto-enabling the service. |
| `accessibility_task_removal_relaunch_archive` | `future_promotion` | Task removal cleanup and package relaunch behavior for the packaged accessibility lifecycle. |
| `accessibility_disconnect_missing_capability_denial_archive` | `future_promotion` | Service disconnect cleanup and packaged missing-capability denial with raw selector/service handles absent. |

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-accessibility-lifecycle.js`
- Gradle gate: `:app:verifyNodePhase14AccessibilityLifecycle`
- Reports:
  - `build/reports/nodejs/phase14-accessibility-lifecycle.json`
  - `build/reports/nodejs/phase14-accessibility-lifecycle.md`
