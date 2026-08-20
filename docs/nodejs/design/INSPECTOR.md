# Embedded Node Inspector Design Gate

Status: disabled. This document records the policy boundary for Node/V8 inspector support and does not enable any runtime capability.

## Goal

Decide whether AutoJs6 can ever expose Node inspector debugging without weakening the Safe Node Profile. The current answer is:

- no inspector in normal builds
- no inspector in release packaged APKs
- no automatic port binding
- no project-level opt-in that silently opens a debugger

Any future implementation must be debug-build-only and must require an explicit user action for each debugging session.

## Current Behavior

The current runtime keeps inspector disabled:

- `require("inspector")` and `require("node:inspector")` reject with the disabled-builtin error shape.
- `require.resolve("inspector")` rejects before `node_modules/inspector` can shadow the disabled builtin.
- `process._debugProcess` is disabled.
- `process.execArgv` is empty, `process.allowedNodeEnvironmentFlags` is frozen and empty, and `process.features.inspector` / `process.features.debug` are false.
- Request startup env strips Node debugger/cache keys before native Node initialization, and script-visible `process.env` filters the same startup/debugger keys.
- `NodeRuntimeCapabilities.featureFlagsById["inspector"]` is `status=disabled`, `securityLevel=high`, and has no build, project, or packaged APK override.
- No embedded Node service binds an inspector TCP or WebSocket port.
- Packaged APK verification does not accept inspector metadata.

## S8-37 Design Gate

S8-37 adds `NodeInspectorDesignGateInstrumentationTest` as a focused negative gate for the disabled policy. It verifies the registry-disabled feature flag, build/project/package override denial, script-visible builtin denial, local `node_modules/inspector` shadowing denial, inert process debugging APIs, startup/debugger env filtering, and absence of inspector diagnostics while disabled.

The gate does not enable inspector support. A future task that opens a debug endpoint must add positive attach tests and the lifecycle/security coverage listed under Future Acceptance Gate.

## S8-40 Security Gate

S8-40 adds `NodeInspectorSecurityGateInstrumentationTest` as a focused hardening gate for hostile startup input while the inspector remains disabled. It verifies release/project/package metadata denial, non-loopback and loopback `NODE_OPTIONS=--inspect` attempts, port cleanup after normal script stop and timeout, and absence of inspector profile artifacts or sensitive marker leaks.

The runtime also strips `NODE_OPTIONS`, `NODE_INSPECT_RESUME_ON_START`, `NODE_COMPILE_CACHE`, and `NODE_DISABLE_COMPILE_CACHE` at the embedded request boundary before native startup. Native initialization disables `NODE_OPTIONS` parsing and global debug variable parsing. These changes preserve the disabled result: no inspector endpoint, no active session, no profiler channel, and no script-visible authority.

## Risk Model

Node inspector is not a log viewer. It is a debugging control channel for a live V8/Node environment. A connected debugger can normally inspect globals, evaluate JavaScript, set breakpoints, pause execution, collect heap snapshots, and observe source text.

In AutoJs6 this must be treated as full control of the running script because inspected code may:

- access allowed AutoJs bridge modules
- read scoped project files
- inspect in-memory secrets, bridge handles, and request payloads
- alter lifecycle timing by pausing the Node event loop
- create denial-of-service conditions through breakpoints or heap snapshots
- expose script sources from packaged projects

The existing Safe Node Profile restrictions, such as disabled `process.binding`, native addon denial, and scoped filesystem policy, must still apply after a debugger attaches. However, debugger-side `Runtime.evaluate` is still powerful enough to invoke any capability the current script could invoke.

## Android Port Binding

Inspector must not bind a public interface.

If a future task implements inspector support, the minimum binding policy is:

- bind only to loopback, preferably `127.0.0.1`
- never bind `0.0.0.0`, Wi-Fi, cellular, or LAN interfaces
- choose a random ephemeral port unless the user explicitly selects one
- prefer `adb forward` or `adb reverse` for desktop access
- display the active endpoint and execution id in a visible debug UI or notification
- close the listener on script stop, timeout, service destroy, or user revoke
- allow at most one inspector session per execution unless a later design proves otherwise

On Android, opening a socket may require `android.permission.INTERNET`. That permission is broad, so inspector support must not add it to normal main-app or packaged APK release manifests merely for debugging. Debug variants may opt in explicitly.

## ADB Forward And DevTools Workflow

S8-38 remains deferred while S8-37 keeps inspector disabled. There is currently
no device endpoint, no port, no ADB forward target, and no Chrome DevTools URL.

A future workflow must be host-assisted but runtime-owned:

- AutoJs6 starts the session only from trusted debug UI or host/debug command.
- The device listener binds loopback only and uses a random ephemeral port.
- A structured session descriptor exposes execution id, loopback host, device
  port, opaque WebSocket path/token, and expiry.
- The host tool creates `adb forward tcp:<host-port> tcp:<device-port>`.
- DevTools attaches through `127.0.0.1:<host-port>`, not a public interface.
- Stop/revoke removes the active session and gives the host deterministic
  cleanup instructions for the ADB forward.

Breakpoints, pause/resume, console, async stacks, source maps, ESM source
names, and lightweight TypeScript source names are acceptance criteria for a
future positive gate, not current runtime behavior.

## WebSocket Transport

The Node inspector protocol uses WebSocket transport, but it is not the same as AutoJs6's controlled `websocket` bridge module.

A future inspector implementation must not:

- enable raw `net`, `tls`, `http`, or `https`
- reuse the user-facing controlled `websocket` module as authority to open debugger ports
- expose inspector transport to scripts
- allow script code to create or manage inspector sessions

The inspector transport belongs to the embedder lifecycle, not the script API surface.

## Relation To V8 Inspector

Embedded Node inspector support sits at the V8 isolate and Node environment boundary. AutoJs6 already owns a `v8::Isolate`, `uv_loop_t`, and `node::Environment` per embedded execution, so any debugger attach/detach must be coordinated with the same lifecycle.

Future design must prove:

- attach and detach happen on the correct Node/V8 event loop thread
- debugger callbacks do not call into JavaScript from arbitrary Java/Kotlin/native threads
- pause/resume does not deadlock bridge RPC, stdout/stderr capture, service stop, or Android UI providers
- `node::Stop()` and service destroy close the inspector session deterministically
- heap snapshot and CPU profile operations are bounded or explicitly user-controlled
- crash diagnostics distinguish inspector crashes from script crashes

## Profiling And Heap Snapshots

S8-39 also remains deferred while inspector is disabled. The current runtime
does not emit `.cpuprofile`, `.heapsnapshot`, allocation sampling, or
inspector-owned event-loop reports.

Future profiling support must:

- require explicit user action for CPU profiles and heap snapshots
- keep raw artifacts under app-private diagnostics until explicit export
- correlate profiles with execution id, event-loop utilization, bridge resource
  counters, memory/RSS samples, and cleanup state
- redact project-sensitive metadata from summaries
- close profiler sessions on script stop, timeout, crash, service destroy, or
  user revoke
- deny packaged release profiling

## Packaged APK Policy

Packaged APKs must not silently inherit inspector capability from a project file.

If inspector is ever implemented, packaged behavior should be:

- release packaged APK: hard disabled
- debug packaged APK: allowed only when the template and build command explicitly enable a debug inspector mode
- manifest verifier reports any added `INTERNET` permission or debugger metadata
- generated diagnostic report labels the APK as debugger-enabled
- user-facing UI makes the debugger session visible and revocable

Do not allow encrypted packaged scripts to open an inspector endpoint by metadata alone. A debugger can reveal live source text and runtime values, so this must be an explicit developer/debug action.

## User Consent

Inspector support requires explicit consent per session:

- the user starts a debug session from a trusted AutoJs6 UI or debug command
- AutoJs6 shows the target script, process, execution id, host, port, and expiry
- the session has a short TTL or closes with the script
- the user can revoke it immediately
- release builds and background starts cannot open an inspector automatically

Optional future hardening:

- random one-time token in the WebSocket URL
- pairing prompt before first attach
- debugger access audit log in `nativeValues`
- deny attach while sensitive permission prompts are active

## Recommendation

Keep inspector disabled by default.

The first acceptable implementation, if ever needed, should be:

```properties
Inspector policy remains disabled
```

with all of these constraints:

- debug builds only
- explicit user action required
- loopback binding only
- no project.json or packaged APK override in release builds
- no raw network module enablement
- no script-visible `inspector` builtin
- deterministic close on stop, timeout, crash, and service destroy
- focused security and lifecycle instrumentation tests

Until that task exists, the disabled builtin and profile registry entries are the intended product behavior.

## Future Acceptance Gate

Any future enablement must include:

- design update and security review
- positive debug-build attach smoke
- negative release-build denial test
- negative packaged release denial test
- loopback-only port binding test
- user-consent and revoke test
- service stop and timeout close tests
- bridge pending-call behavior while paused
- source and heap privacy documentation
- rollback path that returns to current disabled behavior
