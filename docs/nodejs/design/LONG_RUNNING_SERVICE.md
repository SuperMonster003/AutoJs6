# Long-Running Node Service Mode

Status: Phase 7 S7-18 controlled interactive foreground MVP. Normal script,
project, package, and WorkManager launches remain one-shot; only the explicit
interactive session launch surface can run `interactive_long_running`.

Task 179 adds the first runtime contract guard: embedded script requests now
carry an `executionMode` field through the service request spool. `one_shot` is
the only supported runtime mode. Explicit `long_running` or `service` requests
are rejected before user JavaScript runs with
`ERR_AUTOJS6_EMBEDDED_NODE_UNSUPPORTED_OPTION`, and pending timers still time
out as normal one-shot scripts.

Task S7-17 expands that guard into a versioned execution-mode state machine:

- `one_shot`: implemented; requires a positive script timeout.
- `interactive_long_running`: partial; supported only by the explicit
  interactive session launch surface with a persistent foreground notification,
  Stop action, status file, duration/resource summary, and restart policy
  `never`.
- `scheduled`: reserved; WorkManager currently launches bounded one-shot runs
  until the dedicated scheduled task runner gate exists.
- `worker_computation`: reserved; blocked behind worker_threads capability
  isolation and worker resource gates.

Each mode definition records timeout policy, foreground requirement,
notification requirement, allowed provider set, restart policy, quota profile,
and stop semantics. Unsupported mode requests are rejected before user code and
return the same policy metadata in native values.

Task S7-18 adds the first runtime slice for user-visible long-running Node
automation. `NativeNodeEmbeddedScriptService.startInteractiveLongRunning(...)`
starts an explicitly requested `interactive_long_running` session in the
embedded Node process, promotes the service to foreground with a persistent
notification, writes JSON status snapshots under app-private storage, exposes
duration and active resource summary fields, and supports a notification/client
Stop path that schedules clean Node process teardown. It deliberately does not
enable implicit service mode for ordinary script execution.

## Goal

AutoJs6 currently treats Embedded Node scripts as bounded one-shot automation executions. Long-running Node scripts need a separate product and lifecycle model so they do not weaken the Safe Node Profile.

This design separates:

- one-shot scripts: timeout-controlled automation runs
- scheduled/background tasks: AutoJs6 task-owned launches
- long-running services: explicit foreground, user-visible Node service sessions

Normal scripts must remain timeout-controlled. Long-running mode must be
explicit and must not be enabled by a file suffix, `"nodejs";` directive,
package metadata guess, or accidental pending timer.

## Non-Goals

Long-running service mode is not:

- desktop Node daemon support
- unrestricted network/socket support
- a replacement for Android foreground service policy
- a way to bypass script timeout for normal automation
- a way to keep hidden background work alive
- an implicit behavior when a script has `setInterval()` or open bridge resources

## Mode Split

| Mode | Current status | Timeout | User visibility | Entry |
| --- | --- | --- | --- | --- |
| `one_shot` | Implemented | Required positive script timeout | Console/notification while running | directive, file route, project route, packaged route |
| Execution queue | Implemented helper, not a mode | Queue timeout plus per-script timeout | Caller-owned diagnostics | `enqueueScript(...)` |
| `scheduled` | Reserved; WorkManager still launches bounded one-shot executions | Required task timeout | Future task UI/notification | future scheduled runner |
| `interactive_long_running` | Partial explicit foreground MVP | Startup timeout plus stop grace and watchdog policy | Persistent foreground notification with Stop | explicit interactive session API |
| `worker_computation` | Reserved behind worker_threads gates | Startup/task timeout | None | future worker runtime |

## Explicit Opt-In

Broader project/package service mode must require a dedicated descriptor, for
example:

```json
{
  "type": "node",
  "main": "service.cjs",
  "node": {
    "mode": "service",
    "service": {
      "name": "Sync listener",
      "foreground": true,
      "startupTimeoutMs": 10000,
      "stopGraceMs": 5000
    }
  }
}
```

The exact schema is deferred, but required properties should include:

- service name and user-visible label
- entry path or packaged entry id
- foreground notification policy
- startup timeout
- stop grace timeout
- resource limits
- restart policy
- required bridge/network permissions

The default for all existing scripts remains one-shot mode.

The current runtime accepts `executionMode: "one_shot"` for normal script
launches. The aliases `"interactiveLongRunning"`, `"longRunning"`,
`"long_running"`, and `"service"` normalize to `interactive_long_running`, but
direct script launches still reject before user JavaScript runs and do not
convert `timeoutMs: 0` into an unbounded run. `interactive_long_running` is
accepted only when the service request is created through the explicit
interactive session launch surface. `scheduled` and `workerComputation` are
also recognized as reserved modes and rejected before user code until their
dedicated gates are implemented.

## Foreground Notification

Long-running service mode must run with an explicit foreground notification while active.

The S7-18 MVP notification uses the dedicated Embedded Node foreground service
channel, a persistent service notification, and a Stop action bound to the
current execution id. The notification content identifies the interactive Node
runtime and source name. Rich diagnostics/open-console actions remain future
work.

Required notification properties:

- user-visible service name
- package/project identity
- current state: starting, running, stopping, crashed, stopped
- elapsed run time
- primary action: Stop
- secondary action: Open console or diagnostics
- optional action: Restart, only when restart policy allows it

The notification must not be reused as a generic script notification. Service mode needs a distinct channel/category so users can identify persistent Node work.

Android 14+ foreground service type requirements still apply. The current
`specialUse` service type can cover only valid special-use cases and must keep
its subtype property and Play review explanation accurate. S7-19 adds an
Android runtime policy diagnostic that verifies the embedded Node service
manifest, foreground-service permissions, requested service type, and API-level
policy flags before focused gates promote a long-running change.

Foreground service start failures must be visible. Android background-start
denials are reported as `ERR_AUTOJS6_FOREGROUND_SERVICE_START_NOT_ALLOWED`;
missing or mismatched foreground-service types are reported as
`ERR_AUTOJS6_FOREGROUND_SERVICE_TYPE_INVALID`.

## Timeout And Watchdog Policy

One-shot scripts keep their current `timeoutMs` behavior:

```text
timeout -> TIMED_OUT -> STOPPING -> DESTROYED
```

Long-running services should not use the same per-script completion timeout because successful service behavior is to remain alive. They still need bounded phases:

- startup timeout: service must signal ready or fail
- stop grace timeout: service must stop after user/system stop
- bridge call timeout: every bridge request remains bounded
- network/WebSocket timeout: every connect/request remains bounded
- watchdog heartbeat: service health must be observable

If startup does not complete before `startupTimeoutMs`, the service should fail as a service-start error, not as a normal script timeout.

If stop grace expires, the service process should be force-stopped and diagnostics should record leaked resources.

Android 15+ can call `Service.onTimeout(int, int)` for timed-out foreground
service types. The S7-19 runtime handles both service timeout overloads,
records foreground timeout diagnostics in the interactive status snapshot,
marks the runtime timed out, calls `stopSelf()` immediately, stops foreground
notification ownership, and schedules the isolated Node process to exit. This
path exists to avoid platform `RemoteServiceException` when Android grants only
a short timeout grace window.

Android 16+ WorkManager long-running workers can consume JobScheduler quota.
The controlled `work_manager` provider remains bounded one-shot/periodic task
execution, not arbitrary service mode, and now records quota diagnostics in task
records and last-run metadata.

## User Stop Controls

Long-running service mode must provide multiple stop paths:

- foreground notification Stop action
- AutoJs6 console/runtime UI stop button
- `engines.stopSelf()` from the service script
- `engines.stopAll()` from AutoJs6-owned control surfaces
- packaged app stop control when running in packaged APK

The S7-18 MVP implements the foreground notification Stop action and the
programmatic client stop path
`NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context,
executionId)`. Stop requests mark the interactive status as stopping/stopped and
schedule the isolated embedded Node process to exit. Console UI, script-level
cooperative stop callbacks, and packaged app stop controls remain future gates.

Stop must route through the same lifecycle controller. JavaScript should
eventually receive a cooperative shutdown signal before force stop, but force
stop must remain available if the service does not drain.

Suggested future JavaScript shape:

```js
"nodejs";

const { onStopRequested, signalReady } = require("service");

await signalReady();

onStopRequested(async () => {
  // close controlled resources here
});
```

This `service` module is a future design item. It must not appear in the Safe Node Profile until implementation and tests exist.

## Resource Quotas

Long-running service mode must have stricter and more visible quotas than one-shot scripts because leaks can accumulate over time.

Required quota categories:

- max runtime memory or coarse RSS budget
- max bridge pending calls
- max bridge-owned resources
- max image handles and screen capturers
- max shell commands
- max controlled fetch requests
- max WebSocket connections
- max queued WebSocket events/messages
- max worker count if workers are enabled
- max file descriptors or open handles if exposed
- max log buffer size

Quota failures should use stable `ERR_AUTOJS6_*` or bridge error codes and should be visible in diagnostics.

Quota policy must have hard maximums that project metadata cannot raise.

## Network And WebSocket Behavior

Long-running service mode does not imply raw network support.

Network behavior must continue to use AutoJs6-controlled providers:

- controlled `fetch`
- controlled `websocket`
- raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` remain denied unless a separate design gate changes that boundary

Long-running network/WebSocket usage additionally needs:

- explicit project/package permission declaration
- Android `INTERNET` manifest check for packaged apps
- foreground notification disclosure
- connect/request timeouts
- idle timeout or heartbeat policy
- max active connection/request limits
- service-destroy cancellation
- diagnostics counters

Background network persistence must be a product decision, not an npm compatibility side effect.

## Packaged APK Behavior

Packaged apps must not gain long-running service behavior implicitly.

Future packaged service mode requires:

- packaged project metadata marking service mode explicitly
- manifest synthesis for the service entry
- foreground service permissions and type checks
- notification channel setup
- packaged stop control
- packaged diagnostic report entries
- ABI/native library verification like normal embedded Node
- release checklist coverage

If a packaged APK does not include service-mode metadata, all Node entries remain one-shot timeout-controlled entries.

## Crash And Restart Policy

Default restart policy should be:

```text
restart: never
```

Automatic restart is high risk because native crashes, permission errors, network failures, or infinite loops can create crash loops.

If automatic restart is added later, it must be explicit and bounded:

- max restarts in a window
- exponential backoff
- crash-loop quarantine
- user-visible notification state
- last error retained
- manual stop disables auto restart
- native crash and policy denial may have different restart eligibility

Suggested future descriptor:

```json
{
  "restart": {
    "policy": "on-failure",
    "maxRestarts": 3,
    "windowMs": 600000,
    "backoffMs": 30000
  }
}
```

## Process And Lifecycle Ownership

Long-running services should not reuse the current one-shot lifecycle assumptions blindly.

The S7-18 MVP still runs in `:nodejs_embedded` and keeps the existing
single-active Node environment invariant. It is an interactive runtime surface,
not a generic service scheduler. No automatic restart is attempted after stop,
timeout, process exit, or failure.

Open design choices:

- reuse `:nodejs_embedded` with a dedicated service component
- use a separate `:nodejs_service` process
- use a finite statically declared process pool

Minimum requirement:

- one active Node environment per service process
- no overlapping V8/libuv lifecycle ownership
- user stop and system destroy use one lifecycle controller
- resource cleanup is deterministic
- process exit is deliberate, not scheduled immediately after script success

The current execution queue remains for ordered one-shot execution. It is not service scheduling and not service restart.

## Diagnostics

Long-running service diagnostics should include:

- service id
- execution id
- package/project identity
- process name and pid
- entry path or packaged entry id
- start time and elapsed time
- ready time
- stop reason
- restart count and last restart reason
- active resource counts
- quota warnings/failures
- network/WebSocket counters
- last bridge error
- last uncaught exception or unhandled rejection
- native library hashes

Diagnostics should be attachable through the existing Embedded Node doctor report once a runtime provider exists.

The S7-18 MVP writes app-private JSON status snapshots containing schema
version, execution id, execution mode, launch surface, source name, working
directory, state, start/update timestamps, duration, stop-request flag, restart
policy `never`, `automaticRestart=false`, stop action name, active resource
summary, policy metadata, and native values.

## Tests

The current S7-18 gate covers:

- normal scripts still time out
- notification stop action
- explicit interactive session status: starting/running/stopping/stopped
- runtime duration and active resource summary fields
- app UI leaving foreground while the script remains alive
- notification/client Stop path and recovery after process teardown
- Android runtime policy diagnostics for foreground service manifest/type,
  background-start denial classification, Android 15 timeout cleanup, and
  Android 16 WorkManager quota reporting
- direct script `interactiveLongRunning`/legacy `long_running` rejection before
  user code
- one-shot interval timeout regression

Before broader service mode promotion, add focused tests for startup ready
signals, console stop, cooperative stop drain, force stop after grace timeout,
service destroy rejection for pending bridge calls, controlled fetch/WebSocket
cleanup, quota exceeded paths, packaged manifest verification, packaged service
smoke, crash without restart, and bounded restart policy if implemented.

## Release Gate

Any release with long-running service mode enabled must verify:

- normal one-shot timeout tests still pass
- `NodeEmbeddedLifecycleStressInstrumentationTest` still passes
- `NodeInteractiveLongRunningInstrumentationTest` passes on the target device
- `NodeAndroidRuntimePolicyInstrumentationTest` passes on the target device
- foreground service permissions and type are correct
- status snapshots include duration, restart policy `never`, stop action, and
  active resource summary
- raw network and dangerous process APIs remain denied
- service mode is documented as explicit and user-visible

Packaged service mode still remains disabled until packaged manifest, template,
diagnostic, and stop-control gates exist.
