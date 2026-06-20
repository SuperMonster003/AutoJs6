# Phase 8 Inspector Profiling Gate

Status: deferred by disabled inspector gate

S8-39 requires a trusted inspector/profiler control channel. The current v1.1
runtime keeps that channel disabled, so AutoJs6 does not generate CPU profiles,
heap snapshots, allocation samples, or inspector-owned profiling exports.

## Current Behavior

The current Safe Node Profile exposes no profiler artifact path:

- no `inspector` or `node:inspector` builtin;
- no DevTools Protocol `Profiler`, `HeapProfiler`, or `Runtime` channel;
- no `.cpuprofile` file;
- no `.heapsnapshot` file;
- no allocation sampling export;
- no debugger-side event-loop utilization report;
- no project profile metadata export;
- no profile artifact under app-private diagnostics or public storage.

Existing runtime diagnostics such as `process.resourceUsage()`,
`process.memoryUsage()`, `perf_hooks.performance`, bridge resource counters,
and Phase 7/8 performance reports remain available, but they are not inspector
CPU or heap profile artifacts.

## Future Artifact Policy

If a later debug-only inspector implementation adds profiling, it must store
raw profiler artifacts under app-private diagnostics first. Export to shared
storage or a host pull command must be explicit.

Required future outputs:

- `.cpuprofile`;
- `.heapsnapshot`;
- allocation sampling report;
- JSON summary that correlates execution id, profile duration, event-loop
  utilization, bridge resource registry counts, memory/RSS samples, and
  cleanup status.

The JSON summary must avoid embedding project source text, raw bridge handles,
Android object references, environment values, credentials, or unredacted
absolute external paths.

## Acceptance Gate

Future S8-39 promotion must include:

- positive debug-build CPU profile start/stop smoke;
- heap snapshot smoke with explicit user action;
- allocation sampling smoke;
- app-private artifact storage verification;
- explicit export/pull path verification;
- service-stop and timeout cleanup;
- redaction checks for project-sensitive metadata;
- packaged release denial;
- rollback to the S8-37 disabled gate.

Until those gates exist, S8-39 is intentionally documentation-only and
deferred.
