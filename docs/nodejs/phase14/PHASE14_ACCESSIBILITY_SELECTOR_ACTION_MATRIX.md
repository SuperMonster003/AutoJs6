# Phase 14 Accessibility Selector Action Matrix

Machine-readable source:

- `docs/nodejs/phase14/accessibility-selector-action-matrix.json`

## Status

Status: partial

P14-12 defines the selector/action real-device matrix for accessibility
promotion. It imports the P13-19 selector facade, Rhino compat aliases,
timeout/cancellation, missing-service denial, raw object boundary, and P14-11
packaged disabled-state lifecycle evidence. It also extends the optional
`accessibility` device profile smoke to cover `longClick` and optional
`recentApps` global action coverage.

This is not a promotion claim. The task remains open until Android 12 class and
Android 15+ class device archives record user-enabled accessibility service
state, controlled selector/action success, missing service, missing capability,
timeout behavior, safe Back/Home/Recents behavior, and unsupported
gesture/root/Shizuku diagnostics.

## Required Device Classes

- `android_12_class`
- `android_15_plus_class`

## Required Scenarios

- Direct selector builders and action facade.
- Rhino compat selector aliases and chain helpers.
- Service-enabled `findByText`, `findOne`, `click`, and `longClick` over the
  controlled UI.
- Back/Home/Recents optional global actions when safe to trigger.
- Timeout and cancellation cleanup.
- Missing service denial.
- Missing capability denial.
- Gesture/root/Shizuku unsupported tier diagnostics.
- Raw `AccessibilityService`, `AccessibilityNodeInfo`, `UiObject`, and live
  selector absence.

## Manual Device Commands

The service must be enabled manually in Android settings before running the
profile. The gate must not enable accessibility service state by itself.

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=accessibility"
```

Use global actions only when Back/Home/Recents are safe on the test device:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=accessibility" "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.accessibilitySmoke.globalActions=true"
```

## Open Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `accessibility_selector_android12_action_archive` | `future_promotion` | Archived Android 12 class accessibility profile run with user-enabled service covering findByText, findOne, click, longClick, timeout, missing service, and missing capability behavior. |
| `accessibility_selector_android15_plus_action_archive` | `future_promotion` | Archived Android 15+ class accessibility profile run with user-enabled service covering findByText, findOne, click, longClick, timeout, missing service, and missing capability behavior. |
| `accessibility_selector_global_actions_recents_archive` | `future_promotion` | Back/Home/Recents global action archive recorded only on devices where those actions are safe to trigger. |
| `accessibility_selector_unsupported_tier_diagnostics_archive` | `future_promotion` | Archived diagnostics proving gesture/root/Shizuku tiers remain separate, unsupported or explicitly denied, and do not leak raw accessibility objects. |

## Verification

- Host verifier: `tools/nodejs/phase14/verify-node-phase14-accessibility-selector-action-matrix.js`
- Gradle gate: `:app:verifyNodePhase14AccessibilitySelectorActionMatrix`
- Reports:
  - `build/reports/nodejs/phase14-accessibility-selector-action-matrix.json`
  - `build/reports/nodejs/phase14-accessibility-selector-action-matrix.md`
