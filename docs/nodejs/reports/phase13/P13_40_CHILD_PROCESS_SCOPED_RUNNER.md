# P13-40 Child Process Scoped Runner Decision

P13-40 records child_process scoped runner as partial. The current Phase 13
decision is to expose diagnostics through `autojs6:profile.childProcessProfile`
while keeping `require("child_process")`, `require("node:child_process")`,
`require.resolve("child_process")`, and
`require.resolve("node:child_process")` denied by default. A debug/build-time
Desktop compatibility experiment can route those specifiers to Node's native
builtin when `autojs.nodejs.child_process.experimental=true`; this opt-in is
release-forbidden and is not the stable Safe-profile command runner.

## Decision

- Runtime `child_process` module: denied by default.
- `node:child_process`: denied by default.
- Experimental native opt-in: available only when
  `autojs.nodejs.child_process.experimental=true`.
- Scoped runner: deferred for release/profile promotion.
- Current migration path: the separate `shell` bridge, not Node
  `child_process`.
- Android shell provider: available only through explicit `shell` capability.
- App-private binaries: required before stable child-process-style enablement.
- Allowlisted binaries and integrity metadata: required before stable
  enablement.
- Arbitrary paths, detached processes, shell operators, raw process handles,
  raw file descriptors, streams, and signals: not promoted in the Safe profile.
- `cwd`: scoped to the working directory.
- `env`: JSON string map only.
- stdout/stderr: must remain bounded.
- stdin, cancellation, kill, cleanup, and packaged behavior: not promoted.

This report adds a debug-only native builtin opt-in for desktop package
compatibility, but it does not add a release callable `child_process` module,
scoped runner, spawn/fork facade, stream-backed stdio, signal handle, or process
handle. It records the contract a future Desktop-profile scoped runner must
satisfy before any child-process-like authority can be promoted.

## Evidence

- `NodeChildProcessPhase13DecisionInstrumentationTest` verifies the frozen
  `childProcessProfile`, default feature-flag denial, `child_process` /
  `node:child_process` require and resolve denial, shadow-package protection,
  and the separate `shell` bridge path through a fake transport.
- The same focused instrumentation test verifies the experimental opt-in path:
  bare and `node:`-prefixed `child_process` require/import resolve to the native
  builtin before local shadow packages, `module.isBuiltin()` reports true, and
  `execFileSync()` can run a simple Android shell command.
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
- Decide whether native `child_process` remains a debug-only Desktop
  compatibility escape hatch or is replaced by a safer scoped runner for any
  release profile.

## Rollback

Set `autojs.nodejs.child_process.experimental=false` or remove the BuildConfig
flag propagation to return to default denial. Full rollback removes
`childProcessProfile`, the focused instrumentation test, the P13-40 verifier,
and the Phase 13 docs/report references. Runtime behavior falls back to the
existing Safe Node Profile boundary: `child_process` remains disabled, `shell`
remains a separate scoped bridge capability, and no raw process handle, stdio
stream, signal, or arbitrary executable path crosses into JavaScript.
