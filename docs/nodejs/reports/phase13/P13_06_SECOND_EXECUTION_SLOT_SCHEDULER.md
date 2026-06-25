# P13-06 Second Execution Slot Scheduler

Status: partial runtime scheduler and focused stress promotion landed; long-duration soak remains open.

## Landed In Phase 13

- `NodeEmbeddedProcessPoolPolicy` now records default scheduler diagnostics with
  `default_scheduler_enabled`, `default_two_slot`, `slot_selection_source`, and
  default scheduler slot ids.
- `NativeNodeEmbeddedScriptService.runScript(...)` now acquires a client-side
  default process-pool lease for ordinary one-shot script launches when the
  device profile recommends at least two active slots.
- The default scheduler first selects `primary`; while that slot is occupied,
  it can select `overflow_1`, starting
  `NativeNodeEmbeddedScriptServiceSlot2` in `:nodejs_embedded_2`.
- `NativeNodeEmbeddedRuntimeBridge.runEmbeddedScriptInCurrentProcess(...)` now
  accepts all declared process-pool script slot suffixes, so `overflow_1`
  execution is not rejected by the old primary-only `:nodejs_embedded` guard.
- Explicit `primary` / `overflow_1` routing remains supported and distinct from
  default scheduler routing through `processPoolSlotSource`.
- When both default scheduler slots are occupied, the client path returns
  `EmbeddedNodeProcessPoolBusyError` with
  `ERR_AUTOJS6_NODE_ENGINE_BUSY` and `embedded_script.process_pool.busy=true`.
- Request spool and Intent metadata now preserve `processPoolSlotSource`, so the
  target service process can publish default-scheduler diagnostics.
- Crash evidence markers use `processPoolPlan.selectedSlot.id`, so normal
  marker evidence is slot-labelled for `primary` and `overflow_1`.
- Internal project/package launch descriptors now accept
  `processPoolSlot=primary|overflow_1` plus
  `processPoolSlotSource=explicit_request|default_scheduler`, and
  `NativeNodeEmbeddedProjectRunner` propagates that metadata into request,
  debug descriptor, native values, and packaged launch diagnostics.
- Packaged APK launch-source markers now include `processPoolSlot` and
  `processPoolSlotSource`, allowing focused packaged smokes to prove explicit
  `overflow_1` routing without relying on host-only launch paths.
- Slot crash fallback semantics are explicit and fail-closed:
  `slot_crash_fallback_mode=no_restart_collect_marker`,
  `failed_slot_replacement_enabled=false`, and
  `failed_slot_quarantine_policy=no_quarantine_until_pool_coordinator`.
  This records crash markers and allows surviving slots to continue, but does
  not yet promise automatic slot replacement.
- Embedded script results now expose crash evidence native values on normal and
  failure exits, so tests can assert the actual marker path, clean-exit state,
  execution id, and selected runtime slot id.
- `concurrentSlotsStressSharedResourcesRemainIsolated` now runs `primary` and
  `overflow_1` concurrently while stressing stdout/stderr capture, cwd/env
  isolation, CommonJS module cache isolation, global isolation, timers, file
  handles, `fs.watch` lifecycle cleanup, storage bridge dispatch drain, bridge
  resource cleanup, and crash marker slot metadata.

## Test Evidence

Instrumentation coverage in
`NodeEmbeddedProcessPoolPolicyInstrumentationTest` now includes:

- default scheduler plan diagnostics for `default_two_slot`;
- ordinary default one-shot execution diagnostics, with a primary-only fallback
  branch for devices that recommend only one slot;
- explicit primary/overflow concurrent execution in separate service processes;
- default scheduler concurrent one-shot execution in separate service processes
  on eligible devices;
- overflow-slot no-restart crash marker collection with a subsequent explicit
  primary-slot survivor execution;
- focused two-slot shared-resource stress, including
  `p13.pool.resource.raw_stdout.*`, `p13.pool.resource.raw_stderr.*`,
  `p13.pool.resource.summary.*`, `slot-state.cjs`, `fs.watch`,
  `bridge.__test.pendingCount()`, and
  `embedded_script.crash_evidence.marker_path` evidence;
- launch descriptor, file source, project request, debug descriptor, and native
  value alignment for explicit process-pool slot metadata.

`EmbeddedNodePackagedApkSmokeTest` also adds a focused packaged process-pool
slot smoke for descriptor-driven `overflow_1` routing.

`NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()` now stops every
declared embedded service slot, not just the primary service.

## Host Gate

Gradle task:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13SecondExecutionSlotScheduler
```

Node script:

```text
tools/nodejs/phase13/verify-second-execution-slot-scheduler.js
```

Generated reports:

```text
build/reports/nodejs/phase13-second-execution-slot-scheduler.json
build/reports/nodejs/phase13-second-execution-slot-scheduler.md
```

The gate is expected to report `partial` with one remaining blocker until the
P13-08 quick/final long-duration soak evidence lands.

## Remaining Work

- Keep `defaultSchedulerFallbackEnabled=false` until a future pool coordinator
  implements and tests automatic slot replacement/quarantine.
- Reuse P13-08 long-duration quick/final soak evidence across both active slots
  as the closing signal before marking `second_execution_slot` complete.

P13-06 remains unchecked until these final acceptance items pass.
