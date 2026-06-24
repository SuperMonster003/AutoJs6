# P13-40 Child Process Scoped Runner Decision

P13-40 records child_process scoped runner as partial. The current Phase 13
decision is to expose diagnostics through `autojs6:profile.childProcessProfile`
while keeping `require("child_process")`, `require("node:child_process")`,
`require.resolve("child_process")`, and
`require.resolve("node:child_process")` denied by default.

## Decision

- Runtime `child_process` module: denied.
- `node:child_process`: denied.
- Scoped runner: deferred.
- Current migration path: the separate `shell` bridge, not Node
  `child_process`.
- Android shell provider: available only through explicit `shell` capability.
- App-private binaries: required before child_process-style enablement.
- Allowlisted binaries and integrity metadata: required before enablement.
- Arbitrary paths, detached processes, shell operators, raw process handles,
  raw file descriptors, streams, and signals: denied.
- `cwd`: scoped to the working directory.
- `env`: JSON string map only.
- stdout/stderr: must remain bounded.
- stdin, cancellation, kill, cleanup, and packaged behavior: not promoted.

This report does not add a callable `child_process` module, scoped runner,
spawn/fork facade, stream-backed stdio, signal handle, or process handle. It
records the contract a future Desktop-profile scoped runner must satisfy before
any child-process-like authority can be promoted.

## Evidence

- `NodeChildProcessPhase13DecisionInstrumentationTest` verifies the frozen
  `childProcessProfile`, default feature-flag denial, `child_process` /
  `node:child_process` require and resolve denial, shadow-package protection,
  and the separate `shell` bridge path through a fake transport.
- `NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest` continues
  to verify that P13-35 did not introduce a process-worker facade and did not
  promote raw `child_process`.
- `NodeShellBridgeInstrumentationTest` continues to verify the controlled
  shell bridge, scoped `execFile()`, manifest capability enforcement, root
  capability split, unsupported Shizuku path, and absence of `spawn`/`open`.
- Security and packaged smoke coverage keep `child_process` denied from
  CommonJS, dynamic import, local `createRequire()`, and packaged graphs.
- `verifyNodePhase13ChildProcessScopedRunnerDecision` records the host gate and
  writes `phase13-child-process-scoped-runner-decision.json` and `.md`.

## Remaining Blockers

- Design the scoped child_process API shape, including which
  exec/execFile/spawn-style calls are allowed.
- Define app-private binary packaging, allowlist metadata, hash/integrity
  checks, and path resolution.
- Reject arbitrary paths, package-managed downloaded binaries, shell operators,
  detached processes, and inherited handles.
- Implement bounded stdin/stdout/stderr, timeout, cancellation, kill, and
  resource cleanup semantics.
- Add provider-side cleanup and process accounting evidence for normal exit,
  timeout, cancellation, app stop, and crash.
- Add packaged APK metadata, disclosure, install/run smoke, hostile corpus, and
  rollback controls before promotion.

## Rollback

Remove `childProcessProfile`, the focused instrumentation test, the P13-40
verifier, and the Phase 13 docs/report references. Runtime behavior falls back
to the existing Safe Node Profile boundary: `child_process` remains disabled,
`shell` remains a separate scoped bridge capability, and no raw process handle,
stdio stream, signal, or arbitrary executable path crosses into JavaScript.
