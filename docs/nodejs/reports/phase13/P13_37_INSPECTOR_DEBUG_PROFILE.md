# P13-37 Inspector Debug Profile Decision

P13-37 records inspector/devtools debug profile as partial. The current Phase
13 decision is to expose diagnostics through `autojs6:profile.inspectorProfile`
while keeping `require("inspector")`, `require("node:inspector")`,
`require.resolve("inspector")`, and `require.resolve("node:inspector")`
denied by default.

## Decision

- Runtime module: denied.
- Target profile: future `debug_unsafe_lab` only.
- DevTools attach and inspector protocol: deferred.
- ADB forwarding and explicit user action: required before enablement.
- Release build behavior: denied.
- Packaged behavior: not promoted.
- Port binding and remote connections: denied.
- Bridge modules, process access, raw sockets, CPU profiler, and heap snapshot:
  denied.
- Sensitive path/user-data redaction: not proven.

This report does not add a callable inspector session, debugger endpoint, port
listener, profiler API, or script-visible `inspector` module. It records the
contract a future debug-only implementation must satisfy before any DevTools or
profiling surface can be promoted.

## Evidence

- `NodeInspectorPhase13DecisionInstrumentationTest` verifies the frozen
  `inspectorProfile`, default denial, `node:inspector` denial, resolve denial,
  feature-flag boundary, process debug feature state, and local shadow-package
  protection.
- `NodeInspectorDesignGateInstrumentationTest` continues to verify the disabled
  design gate, inert process debug APIs, startup/debugger env filtering, and
  `process.getBuiltinModule("inspector")` denial.
- `NodeInspectorSecurityGateInstrumentationTest` continues to verify hostile
  `NODE_OPTIONS=--inspect` inputs, release/project/package metadata denial,
  port cleanup, and absence of profiler artifacts or sensitive marker leaks.
- `docs/nodejs/design/INSPECTOR.md` remains the source of the future debug-only
  enablement acceptance criteria.
- `verifyNodePhase13InspectorDebugProfileDecision` records the host gate and
  writes `phase13-inspector-debug-profile-decision.json` and `.md`.

## Remaining Blockers

- Implement trusted debug UI or host command session start/revoke with visible
  execution state.
- Add loopback-only listener descriptors, ADB forward cleanup instructions, and
  public-interface denial proof.
- Add positive debug-build DevTools attach coverage for breakpoints,
  pause/resume, console, async stacks, source maps, and ESM source names.
- Add CPU/heap profile lifecycle, app-private artifact export, redaction,
  budget, and cleanup evidence.
- Add packaged release denial plus explicit debug-packaged metadata, manifest,
  and diagnostics evidence.

The docs, release matrix, desktop matrix, surface manifest, and Gradle gate are
evidence-only for this partial decision. They do not add a separate P13-37
blocker beyond the debug session, loopback/ADB, DevTools attach, profiler, and
packaged metadata evidence items above.

## Rollback

Remove `inspectorProfile`, the focused instrumentation test, the P13-37
verifier, and the Phase 13 docs/report references. Runtime behavior falls back
to the existing inspector design and security gates: `inspector` stays disabled
with stable denial, no debug endpoint, and shadowing protection.
