# Phase 9 Isolation And Process Policy

Status: bootstrap-only policy gate.

S9-27 through S9-33 collect the isolation, broker, process-pool, crash,
resource-limit, multi-instance, and worker-promotion state for the current
Node 24.5 runtime. This report does not enable a new execution surface.

## S9-27 Hardened Isolated Runtime

Current state: partial.

Phase 8 added the manifest and policy proof of concept for
`node_sandboxed`. The mode is recognized, but remains `design_gated`.
`NativeNodeSandboxedScriptService` is declared as a non-exported
`android:isolatedProcess=true` service in the `:nodejs_sandboxed` process.
Direct script requests reject before user code can run.

The formal execution mode is still deferred until a Binder capability broker
owns source handoff, module handoff, capability declaration, validation,
timeouts, cancellation, audit rows, and cleanup.

## S9-28 Capability Broker v3

Current state: partial.

`NodeCapabilityBrokerPolicy` provides the provider-side v2 contract:
operation declarations, required capabilities, parameter validation, request
size limits, positive timeout validation, cancellation metadata, execution
ownership, and quota-bound audit rows.

Capability Broker v3 remains future work. The v3 gate must move this contract
to the isolated-process Binder boundary, add project/package ownership checks,
rate limits, resource quota decisions, provider availability checks, background
policy enforcement, cancellation tokens, response-size limits, and audit IDs.

## S9-29 Multi-process Runtime Pool

Current state: partial.

`NodeEmbeddedProcessPoolPolicy` declares the primary slot, a reserved
long-running slot, and a reserved overflow slot. The effective mode remains
`primary_only`, with `effectiveMaxActiveSlots=1`,
`queueWhenPoolFullEnabled=false`, and one active Node Environment per slot.

The future pool coordinator must own cross-process slot acquisition, release,
queueing, replacement, foreground policy, lifecycle accounting, and low-memory
slot reduction before multi-process execution is enabled.

## S9-30 Crash Containment And Recovery

Current state: deferred.

The current process-pool policy records `crashIsolationPolicy`, but it does not
yet implement native crash containment. Future work must record tombstone/log
context, mark the execution as crashed, close broker-owned resources,
quarantine the failed slot, replace only the crashed slot, and only retry jobs
that are explicitly retryable scheduled executions. Interactive scripts must
never silently restart.

## S9-31 Per-runtime Resource Limits

Current state: deferred.

Existing gates cover bridge request size limits, audit row quotas, worker
experimental policy limits, script timeouts, and process-only memory-pressure
expiry. Phase 9 still needs per-runtime limits for RSS soft/hard caps, V8
old-space, ArrayBuffer, threads, file descriptors, timers, bridge calls,
sockets, workers, images, UI handles, and CPU duration.

## S9-32 Multi-instance Consistency Tests

Current state: deferred.

The current focused gate proves primary-only diagnostics and reserved manifest
slots, but does not run independent concurrent Node environments. Future
multi-instance consistency tests must cover two short scripts, short plus
long-running, sandboxed plus normal, worker-heavy plus network-heavy, one
native crash while another execution continues, queue cancellation, process
replacement, and memory pressure.

## S9-33 Worker Partial Promotion

Current state: blocked.

`worker_threads` stays disabled by default. The request-local experimental
path remains an internal gate only because the current embedded libnode build
does not expose usable native worker execution. Runnable CJS/ESM workers,
MessageChannel, ArrayBuffer transfer, resourceLimits, parent-stop cleanup,
bridge isolation, and packaged worker smoke must complete without
native-availability skips before any partial promotion.

If native `worker_threads` remains blocked, a future process-worker API must be
named explicitly and must not claim exact `worker_threads` semantics.
