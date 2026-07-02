# Phase 14 Node v1.2 Migration And Pro Comparison

P14-36 updates migration and Pro comparison docs for Node Profile v1.2. The
Gradle gate is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14MigrationProComparison
```

## Claim Policy

P14-36 records v1.2 migration and Pro comparison docs as ready. The docs must
say that claims match executable evidence and do not imply raw Android object
parity.

The synced v1.2 areas are:

- Pro UI and floaty;
- accessibility;
- screenshot/OCR;
- tasks;
- notifications;
- recorder;
- worker policy;
- packaged behavior;
- rollback.

The current v1.2 docs must keep `provider_poc_not_promoted`,
`worker_intentionally_unsupported_v1_2`, `host_contract_ready`, and
`NodeProfileV12CapabilityCatalog` as evidence labels, not runtime grants.
Runtime authority remains unchanged.

## Raw Object Boundary

The migration docs must not imply direct parity for raw Android objects,
including raw `Context`, `Activity`, `View`, `Bitmap`, `Intent`,
`MediaProjection`, `MediaRecorder`, `TimedTask`, `BroadcastReceiver`,
`Notification`, or `WakeLock` objects.

## Reports

Expected report artifacts:

```text
build/reports/nodejs/phase14-migration-pro-comparison.json
build/reports/nodejs/phase14-migration-pro-comparison.md
```
