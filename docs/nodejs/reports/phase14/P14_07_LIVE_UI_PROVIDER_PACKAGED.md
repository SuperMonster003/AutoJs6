# P14-07 Packaged Live UI Provider

Status: partial

promotionState: provider_poc

P14-07 carries forward the `P13-17` live `require("ui")` provider evidence into
the Node Profile v1.2 promotion lane. The provider has packaged APK smoke
coverage for capability denial and entry/update/close behavior, but it is not
promoted to `pro_compat_opt_in` because packaged task relaunch, crash recovery,
and user-visible disclosure evidence are still open.

## Imported Evidence

- Phase 13 report:
  `docs/nodejs/reports/phase13/P13_17_LIVE_UI_PROVIDER_PROMOTION.md`.
- Phase 13 gate:
  `:app:verifyNodePhase13LiveUiProviderPromotion`.
- Packaged capability boundary:
  `EmbeddedNodePackagedApkSmokeTest#installAndRunUiCapabilityBoundaryEmbeddedNodePackagedApk`
  verifies a packaged APK without `node.permissions: ["ui"]` rejects
  `ui.showLayout` with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`, reports the
  missing capability, keeps raw Android UI globals absent, and leaves pending
  bridge dispatch at zero.
- Packaged entry/close smoke:
  `EmbeddedNodePackagedApkSmokeTest#installAndRunUiBridgeEmbeddedNodePackagedApk`
  verifies a packaged APK with `node.permissions: ["ui"]` can open a JSON UI,
  receive the `shown` lifecycle event, apply `update` and `batchUpdate`, close
  the handle, keep raw Android UI globals absent, and leave pending bridge
  dispatch at zero.

## Phase 14 Contract

P14-07 uses the shared `P14-02` to `P14-06` promotion contract:

- report status remains `partial`;
- blocker classification remains `future_promotion`;
- packaged high-privilege disclosure fields include `taskRemovalCleanup`,
  `relaunchBehavior`, `crashRecoveryBehavior`, `privacyDisclosure`, and
  `rollbackBehavior`;
- provider rollback must fall back to `safe_default` or stable denial behavior;
- Android 12 and Android 15+ matrix evidence is required before promotion.

## Open Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_task_relaunch` | `future_promotion` | Packaged APK task relaunch smoke with the UI handle closed or restored intentionally. |
| `packaged_crash_recovery` | `future_promotion` | Packaged provider crash recovery and circuit-breaker evidence with no leaked UI handles. |
| `packaged_user_visible_disclosure` | `future_promotion` | Reviewed packaged disclosure copy for live UI launch, stop, relaunch, and cleanup behavior. |

## Decision

The `pro.ui_automation_media` surface may move from `planned` to
`provider_poc` because live provider and packaged smoke evidence exist, but it
must not move to `opt_in_promoted` until the three open blockers above are
closed with real packaged device evidence.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase14/verify-node-phase14-live-ui-packaged.js --repo-root D:\idea-projects\AutoJs6-nodejs --report-dir D:\idea-projects\AutoJs6-nodejs\build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14LiveUiProviderPackaged
```

Expected reports:

```text
build/reports/nodejs/phase14-live-ui-provider-packaged.json
build/reports/nodejs/phase14-live-ui-provider-packaged.md
```
