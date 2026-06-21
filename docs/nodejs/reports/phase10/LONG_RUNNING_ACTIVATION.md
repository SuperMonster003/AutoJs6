# Phase 10 Long-running Activation

## Status

S10-29 through S10-36 are recorded as `ready_with_recorded_blockers`.

Interactive foreground long-running, scheduled runner execution, queueing,
heartbeat/budget diagnostics, checkpoint/restore, and long-running stress
coverage are active through the focused device gate. Multi-instance execution
remains primary-only, and packaged long-running service mode is still blocked.

## Gates

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase10LongRunningActivation
.\gradlew.bat --console=plain :app:verifyNodeLongRunningGate "-Pautojs.nodejs.test.deviceProfile=sony"
.\gradlew.bat --console=plain :app:verifyNodeMultiInstanceGate
```

`verifyNodePhase10LongRunningActivation` writes:

- `build/reports/nodejs/phase10-long-running-activation.json`
- `build/reports/nodejs/phase10-long-running-activation.md`

`verifyNodeLongRunningGate` remains the real device gate. It runs execution
mode, Android runtime policy, interactive long-running, runtime budget,
long-running stress, lifecycle queue/stress, bridge resource registry/leak, and
embedded memory leak stress coverage.

For routine Phase 10 activation evidence, the focused Sony subset used by this
stage covers the S10 policy/runtime path without the heaviest embedded memory
leak stress case:

```powershell
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeExecutionModePolicyInstrumentationTest,org.autojs.autojs.engine.NodeInteractiveLongRunningInstrumentationTest,org.autojs.autojs.engine.NodeRuntimeBudgetPolicyInstrumentationTest,org.autojs.autojs.engine.NodeEmbeddedProcessPoolPolicyInstrumentationTest,org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queuedExecutionRunsAfterActiveExecution,org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queuedExecutionCanBeCancelled,org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queuedExecutionTimeoutReturnsStableError,org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#queueLimitReachedReturnsStableError,org.autojs.autojs.engine.NodeEmbeddedLifecycleStressInstrumentationTest#explicitLongRunningModeIsRejectedBeforeUserScriptRuns" `
  "-Pautojs.nodejs.test.deviceProfile=sony"
```

The focused Sony subset passed 17/17. The full `verifyNodeLongRunningGate`
remains a release/nightly stress gate rather than a Phase 10 aggregate
dependency because it includes long-running memory-leak stress beyond the
routine activation budget.

## Completed Scope

- S10-29 execution mode state machine:
  `one_shot`, `interactive_long_running`, `scheduled`,
  `worker_computation`, and `node_sandboxed` have explicit policy rows.
- S10-30 interactive long-running runtime:
  explicit interactive launch surface, foreground requirement, persistent stop
  action, status snapshots, restart policy `never`, and stop recovery are
  covered.
- S10-31 quota, heartbeat, and watchdog:
  runtime budget parsing, heartbeat stale detection, resource usage snapshots,
  provider quota rejection, and active resource summaries are covered.
- S10-33 execution queue:
  reject-when-busy, enqueue-when-busy, one-entry queue, cancel queued, queue
  timeout, and queue full errors are covered.
- S10-34 scheduled Node task runner:
  WorkManager scheduled runner covers CJS/ESM entries, cancellation,
  persisted run metadata, scoped entry policy, and no implicit retry.
- S10-35 checkpoint/restore:
  `autojs6:lifecycle` supports JSON-only project-scoped checkpoint, explicit
  restore, clear, a 64 KiB limit, and no automatic restart.
- S10-36 stress subset:
  repeated stop/checkpoint/recovery and provider cleanup are in the focused
  long-running gate.

## Recorded Blockers

- S10-32 fixed process slot pool:
  reserved process slots exist, but `effectiveMaxActiveSlots` remains `1`,
  `multiProcessEnabled=false`, and `longRunningDedicatedSlotEnabled=false`.
- Packaged long-running service mode:
  packaged entries remain one-shot or scheduled through controlled surfaces;
  no packaged foreground service mode is promoted.

## Boundary

No automatic restart, raw background service authority, unrestricted
multi-instance execution, or shared V8 environment is enabled. Direct script
requests for interactive long-running still reject before user code; only the
explicit interactive session launch surface can start the foreground runtime.
