# Registered-script execution checks (AI Agent P1.4 / P3.3)

`AgentRegisteredScriptExecutionTest` exercises the host `AndroidNodeBridgeCapabilityProvider`
`agent` module through catalog discovery, manifest/default-argument validation, main-process
service dispatch, a real script engine, completion and result serialization. Test scripts report
JSON through the public `ai.agent.result(value)` helper and read independent context snapshots
through `ai.agent.context()`. P3.3 connects only these execution-side helpers; task creation and
the `AgentRun` interface remain P5 work.

The host passes scalar parameters through the existing `execArgv`. Its separate
`ExecutionConfig.agentExecutionState` carries the run ID, parameter snapshot, preset name and
64 KiB result slot. Ordinary executions have no state, cannot report a result through these
hooks, and cannot create a marker by supplying an `agentRunId` argument. Results are valid JSON,
last-write-wins until termination, and immutable afterwards.

The device checks cover:

- Directory listing and manifest lookup, a default parameter, structured result and context.
- A script that reports no result, with `null` result and console-tail fallback.
- An actually started script stopped by its deadline.
- A running script stopped when its owning capability provider is destroyed.
- Missing/incorrect parameters and an unregistered file rejected before execution.
- A synchronous response consumer throwing without receiving a second response.
- Cancellation requested before the main-process service can evaluate script source.
- A project launch factory resolving a different entry than the already authorized file.
- Explicitly reporting null, distinguished from no report by `resultReported`.
- An invocation stop arriving before exec, and changed registration after confirmation.
- Both Agent samples visible through `app.listSamples`.

Since host build 5287, `agent.execRegistered(path, parameters, options)` accepts a lowercase UUID
`agentInvocationId` and an optional `expectedManifest` catalog snapshot. A differing current
registration is rejected with `SCRIPT_NOT_REGISTERED`. The shared broker accepts
`engines.stop({agentInvocationId})` on the same link; its bounded registry remembers early stops
for 310 seconds, including the maximum 300-second execution deadline. It cannot address another
link's invocation. Numeric execution-ID stop calls keep their existing behavior. No AIDL
transaction or contract version changes are needed, but the plugin requires host build 5287.

`AiAgentPluginRoundTripTest`, enabled with `-e autojs.agent.plugin true`, also exercises the real
installed plugin with a scripted model: missing parameter feedback, a question, per-call
confirmation, file/project execution, model `done`, the retained script result, timeout and
task cancellation. The cleanup project deletes only the fixture's own old installer and keeps
other/new files. API 24 emulated storage may reject mtime changes; that case verifies retention
of the still-new installer, while API 37 verifies actual old-file deletion. These are E1 checks,
not an online-model or physical-device E4 acceptance.

The cancellation handle marks an internal Binder token immediately. Both service execution and
the script's `onStart` gate observe it, so cancellation is retained while the start Intent is
still queued. Closing a completed handle does not look up or stop an execution by reusable ID.
Timeout/cancellation calls `forceStop` on the owned engine and allows up to 500 ms for its
completion notification. The response's `finished` flag reports whether termination was
observed. The bridge reserves dispatch/cleanup time within the original request deadline.

Console capture uses the existing process-wide id window and returns up to 40 text lines within
64 KiB. `consoleCaptureMode: "global-window"` identifies this behavior; simultaneous scripts may
contribute lines to the same window.

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests '*AgentScriptExecutionStateTest'
adb -s <serial> shell am instrument -w -r -e class org.autojs.autojs.engine.AgentRegisteredScriptExecutionTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

The fixture uses host cache directories and removes them afterwards. It does not require a real
model, accessibility permission or a Node.js plugin. The project-entry mismatch check rejects
before any plugin engine is launched. Execution through other supported script engines follows
the existing source factory and is not established by the Rhino device fixture. A test file by
itself is not execution evidence; record the actual build/device results separately.
