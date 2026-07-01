# P14-12 Accessibility Selector Action Matrix

Status: partial

P14-12 deploys the selector/action real-device matrix and host gate. The matrix
imports P13-19 selector DSL, Rhino compat aliases, timeout/cancellation,
missing-service denial, raw object boundary, and P14-11 packaged disabled-state
lifecycle evidence. It also extends the manual accessibility device smoke to
cover controlled `longClick` and optional `recentApps` global action behavior.

The task remains unchecked because Android 12 class and Android 15+ class
device archives are not present yet.

## Implemented Evidence

- Matrix source:
  `docs/nodejs/phase14/accessibility-selector-action-matrix.json`.
- Manual profile anchor:
  `NodeAccessibilityRealDeviceSmokeInstrumentationTest#providerFindsAndClicksControlledUiWhenAccessibilityServiceIsEnabled`
  now covers `findByText`, `findOne`, selector `click`, selector `longClick`,
  and optional Back/Home/Recents when the global-action flag is set.
- Imported selector evidence: P13-19 direct selector builders, Rhino compat
  aliases, timeout/cancellation cleanup, missing-service denial, and raw object
  absence.
- Imported lifecycle evidence: P14-11 packaged disabled-state denial and
  privacy/settings lifecycle contract.
- Unsupported tiers: gesture/root/Shizuku authority remains separate from
  `accessibility` and must not be implied by selector promotion.
- Gate wiring: `:app:verifyNodePhase14AccessibilitySelectorActionMatrix`
  depends on `:app:verifyNodePhase14AccessibilityLifecycle`,
  `:app:verifyNodePhase13AccessibilitySelectorPromotion`, and
  `:app:verifyNodePhase13ShellRootShizukuCapability`.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `accessibility_selector_android12_action_archive` | `future_promotion` | Archived Android 12 class accessibility profile run with user-enabled service covering findByText, findOne, click, longClick, timeout, missing service, and missing capability behavior. |
| `accessibility_selector_android15_plus_action_archive` | `future_promotion` | Archived Android 15+ class accessibility profile run with user-enabled service covering findByText, findOne, click, longClick, timeout, missing service, and missing capability behavior. |
| `accessibility_selector_global_actions_recents_archive` | `future_promotion` | Back/Home/Recents global action archive recorded only on devices where those actions are safe to trigger. |
| `accessibility_selector_unsupported_tier_diagnostics_archive` | `future_promotion` | Archived diagnostics proving gesture/root/Shizuku tiers remain separate, unsupported or explicitly denied, and do not leak raw accessibility objects. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-accessibility-selector-action-matrix.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14AccessibilitySelectorActionMatrix
```

Expected reports:

```text
build/reports/nodejs/phase14-accessibility-selector-action-matrix.json
build/reports/nodejs/phase14-accessibility-selector-action-matrix.md
```
