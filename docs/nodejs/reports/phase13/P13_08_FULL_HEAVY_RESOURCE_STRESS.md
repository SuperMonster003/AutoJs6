# P13-08 Full Heavy Resource Stress

Status: partial

P13-08 tracks the final internal blocker for long-duration runtime stability.
The existing Phase 7 and Phase 11 gates already cover the short stress anchors:
memory leak stress, bridge/provider resource stress, long-running service
stress, worker resource stress, and final-soak report consumption. This Phase
13 item adds a dedicated host verifier and a stable report contract for the
required 8 hour quick soak and 24 hour final soak.

## Landed

- Host verifier:
  `tools/nodejs/phase13/verify-full-heavy-resource-stress.js`
- Gradle gate:
  `:app:verifyNodePhase13FullHeavyResourceStress`
- Output:
  `build/reports/nodejs/phase13-full-heavy-resource-stress.json`
- Output:
  `build/reports/nodejs/phase13-full-heavy-resource-stress.md`
- Required input reports:
  `build/reports/nodejs/final-soak/quick-soak-8h.json`
- Required input reports:
  `build/reports/nodejs/final-soak/final-soak-24h.json`

The gate is intentionally non-closing while either long-duration report is
missing, under-duration, missing comparable metrics, or reporting leaks,
orphan processes, or unrecoverable runtime failures.

## Comparable Report Contract

Each soak report must expose duration through `durationHours`, `durationMs`, or
parseable start/end timestamps. Each report must also expose samples or nested
diagnostics with these keys:

- `rssKb`
- `javaHeapKb`
- `nativeHeapKb`
- `threadCount`
- `fdCount`
- `bridgeDiagnostics`

The verifier fails closed for `failed`, `blocked`, `error`, `crashed`, or
`timeout` status values, non-empty `failures`, and true leak/orphan/runtime
flags such as `monotonicLeaks`, `leakDetected`, `orphanProcess`, or
`unrecoverableRuntime`.

Minimal accepted shape:

```json
{
  "schema": "autojs6-node-final-soak-run-v1",
  "status": "passed",
  "durationHours": 8.1,
  "monotonicLeaks": false,
  "orphanProcess": false,
  "unrecoverableRuntime": false,
  "samples": [
    {
      "rssKb": 123456,
      "javaHeapKb": 12345,
      "nativeHeapKb": 12345,
      "threadCount": 42,
      "fdCount": 128,
      "bridgeDiagnostics": {
        "embedded_script.bridge_resource.active_count": "0"
      }
    }
  ],
  "failures": []
}
```

## Open Before Closure

- Run and archive the 8 hour quick soak report.
- Run and archive the 24 hour final soak report.
- Verify no monotonic RSS/native-handle/image/socket/provider leak.
- Verify service restart, slot failover, provider destroy, worker cleanup, and
  packaged long-running cleanup leave no orphan process or owned resource.
- Close `full_heavy_resource_stress` in
  `docs/nodejs/final/FINAL_BLOCKER_REGISTRY.json` only after both reports are
  available and ready.
