# P14-28 Worker Native Availability

Status: ready

P14-28 audits native `worker_threads` availability for Node Profile v1.2. The
gate keeps runnable workers intentionally unsupported because the current
target evidence still reports `worker.preflight.status=unavailable` with
`ERR_AUTOJS6_WORKER_DISABLED`.

## Deployed

- Added `docs/nodejs/phase14/worker-native-availability-audit.json`.
- Added `docs/nodejs/phase14/PHASE14_WORKER_NATIVE_AVAILABILITY.md`.
- Added `tools/nodejs/phase14/verify-node-phase14-worker-native-availability.js`.
- Added Gradle gate `:app:verifyNodePhase14WorkerNativeAvailability`.
- Imported P13-34 machine report evidence from
  `phase13-worker-threads-opt-in-promotion.json`.
- Verified Node 24.5 target ABI artifacts for `arm64-v8a`, `armeabi-v7a`, and
  `x86_64`, with `x86` still fail-fast.
- Verified Android loader and crash-signature classification anchors for
  `libnode.so`, `libc++_shared.so`, ABI mismatch, missing dependencies, and
  missing symbols.
- Updated Phase 14 surface manifest, testing, CI, compatibility, security, and
  error docs.

## Decision

- v1.2 worker decision: `intentional_non_support_refreshed`.
- Runtime authority changed: `false`.
- Default `worker_threads`: not promoted.
- Project/package worker opt-in: not promoted.
- `process_worker` / `compute_worker` facade: not promoted by this gate.
- P14-29 and P14-30 remain conditional on a future
  `worker.preflight.status=available` result.

## Verification

```powershell
node --check tools/nodejs/phase14/verify-node-phase14-worker-native-availability.js
node tools/nodejs/phase14/verify-node-phase14-worker-native-availability.js --repo-root . --report-dir build/reports/nodejs
.\gradlew.bat --console=plain :app:verifyNodePhase14WorkerNativeAvailability
```
