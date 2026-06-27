# Phase 9 Inspector Policy Gate

S9-21 through S9-26 cover inspector sessions, DevTools forwarding, CPU
profiles, heap snapshots, diagnostic reports, and inspector security. The
current Phase 9 gate records the safe state without opening a debugger endpoint.

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase9InspectorPolicy
```

Generated outputs:

- `build/reports/nodejs/phase9-inspector-policy.json`
- `build/reports/nodejs/phase9-inspector-policy.md`

## Current Decision

The expected decision is `bootstrap_only`.

This is intentional:

- the disabled-state inspector security gate is ready;
- `process.report.getReport()` and Node Doctor provide partial diagnostics;
- debug attach, ADB forwarding, CPU profile, heap snapshot, and allocation
  sampling remain deferred until a runtime-owned inspector endpoint exists.

## Current Safe Boundary

The gate verifies that:

- the `inspector` feature flag is disabled, high-risk, release-forbidden, and
  has no Gradle, project, or packaged APK override path;
- `NODE_OPTIONS` and `NODE_INSPECT_RESUME_ON_START` are filtered before native
  startup and from script-visible `process.env`;
- embedded Node initialization disables `NODE_OPTIONS` parsing and global debug
  variable parsing;
- `require("inspector")`, `require("node:inspector")`,
  `process.getBuiltinModule("inspector")`, `process._debugProcess`,
  `process.execArgv`, `process.allowedNodeEnvironmentFlags`, and
  `process.features.inspector` remain inert or denied;
- hostile `--inspect` attempts do not leave loopback or non-loopback listeners;
- profiler artifacts such as `.cpuprofile`, `.heapsnapshot`, allocation
  samples, and profile summaries remain absent;
- sensitive marker data is not leaked through disabled inspector attempts.

## Deferred Positive Work

Future promotion of S9-21 through S9-24 requires a new debug-only
implementation with:

- explicit user action per session;
- loopback-only random device port;
- visible and revocable active-session state;
- deterministic close on stop, timeout, crash, and service destroy;
- host-assisted `adb forward` setup and cleanup;
- DevTools attach smoke for CJS, ESM, lightweight TypeScript, and source maps;
- CPU profile, heap snapshot, and allocation sampling stored app-private first;
- explicit export/pull workflow;
- packaged release denial and rollback to the disabled gate.

## Diagnostic Report Status

S9-25 is partial. AutoJs6 currently has a controlled `process.report.getReport()`
surface and privacy-redacted Node Doctor text/JSON reports. Full S9-25 still
needs native Build ID merging, active libuv handle inventory, inspector/profiler
state, execution/resource correlation, and broader redaction tests.
