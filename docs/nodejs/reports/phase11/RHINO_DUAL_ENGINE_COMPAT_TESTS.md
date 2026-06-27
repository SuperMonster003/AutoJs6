# Rhino Dual-Engine Compatibility Tests

Status: S11-28 host gate and device instrumentation plan.

This report defines the first dual-engine compatibility corpus for Rhino augment migration. Each case models the same user-facing use case twice:

- a Rhino source file that uses the legacy global or module shape;
- a Node compat-mode source file that either awaits the supported async facade or records a deliberate blocked-by-design marker.

The checked fixtures live under `docs/nodejs/dual-engine-compat/`. The authoritative case list is `docs/nodejs/dual-engine-compat/manifest.json`.

The sections below define the S11-28 host tests and device instrumentation tests contract.

## Host Tests

The host gate is `verifyNodeRhinoDualEngineCompatTests`.

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRhinoDualEngineCompatTests
```

The gate verifies:

- manifest schema `autojs6-rhino-dual-engine-compat-tests-v1`;
- one Rhino snippet and one Node compat snippet for every case;
- static syntax for both snippets with `node --check`;
- status-manifest evidence for every referenced API;
- report-level separation between `supported_async` and `blocked_by_design`;
- documentation, task-list, and Gradle registration evidence.

The generated host report is written to:

- `build/reports/nodejs/rhino-dual-engine-compat-tests-verification.json`;
- `build/reports/nodejs/rhino-dual-engine-compat-tests-verification.md`.

The JSON report uses schema `autojs6-node-rhino-dual-engine-compat-verification-v1` and includes `statusCounts.supported_async` plus `statusCounts.blocked_by_design` so the two compatibility classes stay visible in CI output.

## Device Instrumentation Tests

S11-28 intentionally left device instrumentation planned. P12-33 adds the
first fake device evidence contract and the planned class now exists:

```text
org.autojs.autojs.engine.RhinoNodeDualEngineCompatInstrumentationTest
```

The device runner reports:

- `caseId`;
- `engine` as `rhino` or `node`;
- `compatStatus`;
- `providerMode`;
- `resourceCleanupStatus`;
- `skipReason`.

The P12-33 fixture lives at
`docs/nodejs/dual-engine-compat/device-evidence/p12-33-fake-device-results.json`
and is verified by `:app:verifyNodeRhinoCompatDualEngineDeviceEvidence`.

Supported async cases may use fake providers where the provider already has a stable fake transport. Policy-only cases must not open a live UI, overlay, or thread surface in Node; they should assert that the compat marker remains `ERR_AUTOJS6_RHINO_COMPAT_BLOCKED_BY_DESIGN`.

## Current Cases

| Case | Status | Rhino API | Node compat expectation |
| --- | --- | --- | --- |
| `toast-log` | `supported_async` | `toastLog(...)` | `await compat.toastLog(...)` resolves |
| `clipboard-roundtrip` | `supported_async` | `setClip(...)`, `getClip()` | `await compat.setClip(...)`, `await compat.getClip()` resolve |
| `sleep-yield` | `supported_async` | `sleep(...)` | `await compat.sleep(...)` resolves |
| `threads-start` | `blocked_by_design` | `threads.start(...)` | `compat.blockedByDesign("threads.start", ...)` |
| `ui-run` | `blocked_by_design` | `ui.run(...)` | `compat.blockedByDesign("ui.run", ...)` |
| `floaty-window` | `blocked_by_design` | `floaty.window(...)` | `compat.blockedByDesign("floaty.window", ...)` |

## Boundary

S11-28 does not enable new runtime APIs. It records the dual-engine compatibility corpus, verifies the static host assets, and defines the instrumentation result contract. Any future device implementation must continue to keep `supported_async` and `blocked_by_design` as separate report buckets.
