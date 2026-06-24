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
- Archive helper:
  `tools/nodejs/phase13/archive-full-heavy-resource-soak.js`
- Contract fixtures:
  `tools/nodejs/phase13/fixtures/full-heavy-resource-stress/`
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

## Contract Fixtures

P13-08 now includes verifier-owned contract fixtures. They do not count as soak
evidence and never make `quickSoakReady` or `finalSoakReady` true. They prove
that the host gate accepts the intended report shape and rejects unsafe or
incomplete shapes before real long-duration evidence is archived:

- `accepted-quick-soak-8h.json`: accepted `durationHours` shape.
- `accepted-final-soak-24h-timestamps.json`: accepted timestamp-duration shape.
- `rejected-under-duration.json`: rejected below required duration.
- `rejected-missing-bridge-diagnostics.json`: rejected missing comparable
  bridge diagnostics.
- `rejected-leak-orphan-runtime.json`: rejected dirty status, failure count,
  leak, orphan, and unrecoverable runtime flags.

The generated `phase13-full-heavy-resource-stress` report exposes these under
`contractFixtures`; any mismatch is treated as a policy violation in the host
gate.

## Archive Helper

The archive helper validates a completed device soak report with the same
P13-08 contract before copying it into the canonical final-soak path:

```powershell
node tools/nodejs/phase13/archive-full-heavy-resource-soak.js `
  --kind quick `
  --source D:\path\to\quick-soak-report.json

node tools/nodejs/phase13/archive-full-heavy-resource-soak.js `
  --kind final `
  --source D:\path\to\final-soak-report.json
```

Use `--dry-run` to validate without copying, and `--force` only when replacing
an already archived report intentionally. The helper writes
`build/reports/nodejs/phase13-full-heavy-resource-stress-archive.{json,md}`.
It does not create or synthesize soak evidence; invalid or under-duration
reports are blocked before they can overwrite the expected final-soak files.

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
