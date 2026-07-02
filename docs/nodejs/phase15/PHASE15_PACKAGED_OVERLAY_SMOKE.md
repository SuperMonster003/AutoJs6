# Phase 15 Packaged Overlay Smoke

Machine-readable source:

- `docs/nodejs/phase15/packaged-overlay-smoke-plan.json`

P15-07 imports the P14-10 packaged overlay positive smoke/stress fixture into
the v1.3 promotion lane. The fixture can build the packaged APK contract and
declares overlay Node capabilities, `android.permission.SYSTEM_ALERT_WINDOW`,
runtime markers, raw global denial, event-drain cleanup, and three-session
stress expectations.

The gate is not a promotion claim. The install-run archive, Android 12/15+
device coverage, foreground disclosure/stop action review, task
removal/relaunch/crash behavior, and stress/leak evidence are still open.

## Ready Evidence

- `phase14_packaged_overlay_fixture_contract`
- `phase14_packaged_overlay_runtime_marker_contract`
- `phase15_overlay_provider_promotion_gate`
- `phase15_overlay_oem_matrix_gate`
- `phase15_promotion_contracts`
- `phase15_disclosure_rollback_contract`
- `phase15_surface_manifest_mapping`

## Fixture

| Field | Value |
| --- | --- |
| Android test class | `EmbeddedNodePackagedApkSmokeTest` |
| Build method | `buildOverlayPositiveEmbeddedNodePackagedApk` |
| Builder method | `buildPackagedOverlayPositiveSmokeApk` |
| Project | `PackagedNodeOverlayPositiveSmoke` |
| Package | `org.autojs6.packaged.node.overlay.positive.smoke` |
| APK | `PackagedNodeOverlayPositiveSmoke_v1.0.0.apk` |

## Open Promotion Blockers

| Blocker | Classification | Required evidence |
| --- | --- | --- |
| `packaged_overlay_positive_smoke_archive` | `future_promotion` | P15-07 packaged positive smoke archive. |
| `packaged_overlay_positive_device_archive` | `future_promotion` | Android 12/15+ install-run logs for the packaged APK. |
| `packaged_overlay_foreground_disclosure_stop_action` | `future_promotion` | Foreground disclosure ownership and stop-action behavior. |
| `packaged_overlay_task_removal_relaunch_crash` | `future_promotion` | Task removal cleanup, relaunch, and crash recovery behavior. |
| `packaged_overlay_event_drain_leak_stress` | `future_promotion` | Event drain and three-session stress/leak proof. |
| `phase15_security_type_doc_sync` | `future_promotion` | P15-11, P15-23, P15-25, and P15-26 sync before promotion. |

## Rollback

- Disable flag: `node.provider.overlay.enabled`
- Diagnostic code: `NODE_PROVIDER_OVERLAY_DISABLED`
- Safe fallback: deny overlay creation and clean owner-scoped overlay handles.
- Crash circuit breaker: `3 provider crashes within 24h per package/profile/capability`

## Verification

- Host verifier: `tools/nodejs/phase15/verify-node-phase15-packaged-overlay-smoke.js`
- Gradle gate: `:app:installAndRunNodeV13PackagedOverlaySmoke`
- Reports:
  - `build/reports/nodejs/phase15-packaged-overlay-smoke.json`
  - `build/reports/nodejs/phase15-packaged-overlay-smoke.md`
