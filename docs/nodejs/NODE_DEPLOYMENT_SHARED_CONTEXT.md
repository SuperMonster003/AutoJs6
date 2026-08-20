# AutoJs6 Embedded Node.js Deployment Shared Context

This document records shared constraints that apply across Embedded Node.js
deployment, Phase 13 closeout, connected-device validation, packaged APK smoke,
and long-running stress work.

Keep this file current when repeated deployment facts, device rules, timeout
budgets, or local-worktree constraints would save future retries.

## Worktree And Commit Hygiene

- Node.js integration work should use `D:\idea-projects\AutoJs6-nodejs`.
- `D:\idea-projects\AutoJs6` is the master worktree and should not receive
  Node.js feature-branch changes.
- `gradle.properties` must not contain capability opt-ins for ESM, dynamic
  import, network, raw network builtins, workers, child processes, or Java
  interop. Those capabilities are compiled in and enabled.
- Device/profile, runtime backend, ABI, probe, and test-fixture properties remain
  valid orchestration inputs, but they must not alter the stable capability set.

## Timeout Defaults

Gradle and connected instrumentation tasks have repeatedly exceeded short tool
timeouts even when the underlying task later passed. Prefer a large timeout on
the first run for expensive gates instead of retrying after a harness timeout.

Suggested minimum command timeouts:

| Gate type | Suggested timeout |
| --- | --- |
| Small host checks such as `node --check`, `rg`, JSON parsing, or report token scans | 10000 ms |
| Host verifiers and summary/report generation | 120000 ms to 240000 ms |
| Kotlin compile, native build, packaging, or APK metadata tasks | 420000 ms to 600000 ms |
| Connected instrumentation and packaged APK smoke tests | 600000 ms or more |
| Native/runtime artifact aggregate checks | 900000 ms or more |
| Long-running soak or stress tests | Expected runtime plus a clear margin |

When a command is killed only by the outer tool timeout, do not record it as a
test failure. Rerun once with a larger timeout and record the successful or real
Gradle/test result.

Observed examples:

- `:app:compileAppDebugAndroidTestKotlin` timed out around 184 seconds during
  Phase 13 utility validation, then passed with a 420000 ms timeout.
- Focused connected instrumentation gates have passed with a 600000 ms timeout
  on the primary device.
- `verifyNodePhase7ResourceStressGate` with native rebuilds and live network
  coverage has passed with an 1800000 ms timeout on the primary device.
- The P13-07 packaged APK crash/lifecycle/Stop/task-removal focused smoke has
  passed with an 1800000 ms timeout on the primary device.

## Device Selection

Preferred connected-test device serial: `QV710AF65F`.

Secondary low-performance matrix device: `bek749scrwv4wo8h` (Xiaomi 12C /
22120RN86C). Use it for non-destructive cross-device matrix evidence unless
the user explicitly approves destructive install cleanup for that device.

Before connected Android tests, inspect device state:

```powershell
adb devices
```

When `QV710AF65F` is online, select it explicitly, especially when multiple
devices are attached:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:printNodeTestDeviceProfile "-Pautojs.nodejs.test.deviceProfile=sony"
```

Use the Sony profile for this primary device unless a task specifically requires
another profile:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.SomeNodeInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

If `QV710AF65F` is offline or unavailable, another connected device may be used
for non-destructive validation. On fallback devices, do not proactively uninstall
or wipe `org.autojs.autojs6` unless the user explicitly approves that device for
destructive install cleanup. The primary device `QV710AF65F` is allowed to be
uninstalled, reinstalled, and reset as needed for Node.js validation.

## Evidence Notes

- Prefer focused instrumentation for the changed surface before broader
  conformance runs.
- Record the exact device/profile used in task summaries and final handoff
  notes.
- Keep generated Phase 13 summaries and task reports synchronized when a host
  verifier, report token, or evidence path changes.
- Stable-denial evidence should remain explicit when a capability is gated by
  policy, profile, packaging metadata, or missing native/runtime support.
- Network and WebSocket stress cases always use the stable network build. Select
  fake versus live providers through the test fixture/device profile; do not use
  a capability property. Denial evidence should exercise permission, URL,
  timeout, quota, provider-availability, or Android manifest policy directly.
- P13-08 has verifier, real-soak runner, instrumentation, and archive-helper
  coverage. Use `tools/nodejs/phase13/run-full-heavy-resource-soak.js` for the
  8h/24h runs. Do not synthesize `quick-soak-8h.json` or
  `final-soak-24h.json`; archive only completed real soak reports through
  `tools/nodejs/phase13/archive-full-heavy-resource-soak.js`. Leave
  `--archive` off when using `--duration-ms` for under-duration smoke tests.
- For ABI status triage, use `:app:generateNodeFinalAbiStatus` when a
  non-failing report is needed. `:app:verifyNodeFinalAbiGate` is the strict
  gate for required ABI libraries and adapter ABI/export evidence. Runtime
  artifact aggregate `bootstrap_only` is treated as deferred external runtime
  maintenance only while the registry keeps `node_24_17_community_fork` in
  `waiting_for_android_artifacts` and the default runtime remains `node24_5`.
