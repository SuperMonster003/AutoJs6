# P14-11 Accessibility Lifecycle

Status: partial

P14-11 deploys the packaged accessibility-service lifecycle fixture and host
gate. The fixture builds a packaged APK with explicit `accessibility`
capability metadata and `android.permission.BIND_ACCESSIBILITY_SERVICE`, checks
the privacy disclosure/settings contract exposed through `autojs6:profile`,
and records the fail-closed selector action behavior when the accessibility
service is disabled or unavailable.

The task remains unchecked because packaged install-run archives for
user-enabled accessibility service state, settings round trip, task removal,
relaunch, service disconnect, and packaged missing-capability denial are not
present yet.

## Implemented Evidence

- Packaged fixture:
  `EmbeddedNodePackagedApkSmokeTest#buildAccessibilityLifecycleEmbeddedNodePackagedApk`
  builds `PackagedNodeAccessibilityLifecycleSmoke_v1.0.0.apk`.
- Capability metadata: the fixture declares the `accessibility` Node
  capability and `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- Privacy/settings contract: the fixture verifies
  `android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS`,
  `accessibility_service_description`, and
  `deny_without_reviewed_disclosure_manifest`.
- Disabled-state denial: the fixture checks `isEnabled=false`,
  `ensureEnabled({ prompt: false })=false`, and stable
  `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED` errors for selector/action methods.
- Boundary contract: raw service/node globals and unsupported helper methods
  stay absent, and pending bridge dispatch drains to zero.
- Gate wiring: `:app:verifyNodePhase14AccessibilityLifecycle` depends on
  `:app:verifyNodePhase14PackagedOverlayPositiveSmoke` and
  `:app:verifyNodePhase13AccessibilitySelectorPromotion`, then writes
  deterministic JSON/MD reports.

## Remaining Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `accessibility_packaged_service_enabled_archive` | `future_promotion` | Archived packaged install-run with user-enabled accessibility service and provider availability on Android 12 and Android 15+. |
| `accessibility_settings_round_trip_archive` | `future_promotion` | Accessibility settings launch guidance and return-to-app state recorded without auto-enabling the service. |
| `accessibility_task_removal_relaunch_archive` | `future_promotion` | Task removal cleanup and package relaunch behavior for the packaged accessibility lifecycle. |
| `accessibility_disconnect_missing_capability_denial_archive` | `future_promotion` | Service disconnect cleanup and packaged missing-capability denial with raw selector/service handles absent. |

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-accessibility-lifecycle.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14AccessibilityLifecycle
```

Expected reports:

```text
build/reports/nodejs/phase14-accessibility-lifecycle.json
build/reports/nodejs/phase14-accessibility-lifecycle.md
```
