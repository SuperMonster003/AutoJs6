# Rhino Dual-Engine Device Evidence

Status: P12-33 fake device instrumentation evidence landed.

P12-33 promotes the Phase 11A dual-engine corpus from a host-only static gate
to a device-result contract. The first implementation uses a fake device
transport so the result schema, case selection, status buckets, and cleanup
fields are stable before P12-34 developer-facing documentation and any broader
connected-device execution.

## Result Shape

Every device result record must include:

| Field | Meaning |
| --- | --- |
| `engine` | `rhino` or `node`. |
| `compatStatus` | Compatibility bucket for this engine result. |
| `providerMode` | `legacy_rhino`, `policy_only`, or a fake/live provider mode. |
| `resourceCleanupStatus` | `closed`, `not_applicable`, or a failure marker. |
| `skipReason` | `null` for executed fake/device records, otherwise a concrete skip reason. |

The checked fixture is
`docs/nodejs/dual-engine-compat/device-evidence/p12-33-fake-device-results.json`.

## Selected Cases

| Case | Node status | Provider mode | Cleanup |
| --- | --- | --- | --- |
| `events-keys` | `supported` | `policy_only` | `not_applicable` |
| `ui-compat-show-update-close` | `supported_async` | `fake_ui_provider` | `closed` |
| `tasks-disposable` | `partial` | `fake_work_manager` | `closed` |
| `barcode-qrcode` | `supported_async` | `fake_barcode_provider` | `closed` |
| `rhino-shim-disabled` | `blocked_by_design` | `policy_only` | `not_applicable` |

`supported_async` and `blocked_by_design` remain explicit buckets in the
fixture and verifier output. P12-33 also allows `supported` and `partial`
because Phase 12 promoted synchronous constants and module-only partial facades.

## Instrumentation Hook

The planned Phase 11A test class now exists:

```text
org.autojs.autojs.engine.RhinoNodeDualEngineCompatInstrumentationTest
```

It validates the same required result fields on device-side fake transport
records and prints a JSON report line prefixed by
`rhino.dual_engine.device_evidence=`.

## Evidence

- Device evidence fixture:
  `docs/nodejs/dual-engine-compat/device-evidence/p12-33-fake-device-results.json`
- Dual-engine manifest:
  `docs/nodejs/dual-engine-compat/manifest.json`
- Instrumentation hook:
  `app/src/androidTest/java/org/autojs/autojs/engine/RhinoNodeDualEngineCompatInstrumentationTest.kt`
- Host verifier:
  `tools/nodejs/phase12/verify-rhino-compat-dual-engine-device-evidence.js`
- Gradle gate:
  `:app:verifyNodeRhinoCompatDualEngineDeviceEvidence`

## Boundary

P12-33 does not add new Rhino compatibility runtime APIs. It records device
evidence for already-promoted or already-denied surfaces and keeps real provider
availability separate from the fake transport contract.
