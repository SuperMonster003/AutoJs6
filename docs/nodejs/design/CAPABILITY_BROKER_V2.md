# Capability Broker v2

Status: Phase 8 provider-side policy and audit MVP

S8-42 adds the first versioned capability broker contract for AutoJs6 Node
bridge calls. This is not the future cross-process Binder broker yet; it is the
shared declaration, preflight, size limit, cancellation metadata, audit log,
and diagnostic layer that the Binder broker must reuse before
`node_sandboxed` can execute code.

## Current Runtime Shape

`NodeCapabilityBrokerPolicy` declares every supported bridge operation by
module and method. Each declaration records:

- required capabilities;
- maximum argument payload bytes;
- parameter validation requirement;
- size-limit requirement;
- cancellation requirement;
- audit requirement;
- execution ownership requirement.

`AndroidNodeBridgeCapabilityProvider` now runs every provider request through
the broker preflight before the existing capability audit and provider dispatch.
The preflight verifies that the operation is declared, that arguments are a
JSON array, that the timeout is positive, and that argument/request payload
sizes stay inside broker limits.

The existing per-module providers still own their detailed parameter parsing,
Android provider calls, resource registration, and cleanup. The broker v2 layer
is the common security envelope, not a replacement for module-specific policy.

## Audit Log

`NodeCapabilityBrokerAuditLog` records one audit row per bridge request. Each
row includes:

- schema name `autojs6-node-capability-broker-v2`;
- owner execution id;
- request id;
- module and method;
- declaration status;
- required capabilities;
- argument and request byte sizes;
- start and completion timestamps;
- duration;
- outcome: `succeeded`, `failed`, or `rejected`;
- result code and category.

The retained audit log is quota-bound. The current default retains 64 rows per
provider execution and drops the oldest rows when the quota is exceeded.
Diagnostics expose total, retained, dropped, rejected, failed, and last-row
counts under `embedded_script.capability_broker.*`. Debug builds also expose
the retained JSON audit log.

## Current Limits

Current provider-side broker defaults:

- max retained audit rows: 64;
- max request JSON bytes: 65536;
- max argument JSON bytes per declared operation: 32768.

Oversized broker payloads reject with `ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`.
Malformed argument payloads reject with `ERR_AUTOJS6_BRIDGE_INVALID_REQUEST`.
Undeclared module/method pairs keep the existing unavailable bridge error
shape.

## Binder Broker Requirements

A future S8-42+ Binder broker must use the same declaration and audit schema
while adding IPC ownership:

- every request crosses a Binder endpoint owned by the app process;
- the isolated process receives only copied payloads or brokered descriptors;
- cancellation maps to script stop, timeout, crash, and service destroy;
- resource handles are execution-owned and cannot be reused across executions;
- audit rows include IPC result state and cleanup state;
- bridge capability checks run before provider dispatch.

Until that Binder gate exists, `node_sandboxed` stays design-gated and direct
script requests continue to reject before user code.
