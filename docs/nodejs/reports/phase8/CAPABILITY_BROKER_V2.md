# Phase 8 Capability Broker v2

Status: provider-side policy and audit MVP complete

S8-42 adds a versioned broker contract for bridge operation declarations,
preflight validation, size limits, cancellation metadata, execution ownership,
and per-call security audit rows.

## Focused Gate

Run the focused gate on the Sony device:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeCapabilityBrokerV2InstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

`NodeCapabilityBrokerV2InstrumentationTest` verifies:

- the broker declaration count matches the supported bridge module/method
  registry plus the internal lifecycle checkpoint module;
- declared operations are parameter-validated, size-limited, cancellable,
  audited, and execution-owned;
- required capabilities are attached to declared operations such as
  `fetch.request`;
- undeclared, invalid-args, and oversized requests fail in preflight with
  stable bridge error categories;
- the audit log is execution-owned and quota-bound;
- Android provider diagnostics expose broker schema, counts, and the last audit
  row after a rejected request.

## Runtime Change

`AndroidNodeBridgeCapabilityProvider` wraps every dispatch callback with
`NodeCapabilityBrokerAuditLog`. Preflight runs before the existing capability
manifest audit. Supported requests continue into the existing provider logic;
rejected requests return through the same bridge response path and still record
an audit row.

New diagnostics:

```text
embedded_script.capability_broker.schema
embedded_script.capability_broker.version
embedded_script.capability_broker.owner_execution_id
embedded_script.capability_broker.declared_operation_count
embedded_script.capability_broker.max_audit_rows
embedded_script.capability_broker.max_request_bytes
embedded_script.capability_broker.audit_total_count
embedded_script.capability_broker.audit_retained_count
embedded_script.capability_broker.audit_dropped_count
embedded_script.capability_broker.audit_rejected_count
embedded_script.capability_broker.audit_failed_count
embedded_script.capability_broker.audit_last_json
embedded_script.capability_broker.audit_log_json
```

`audit_log_json` is debug-build-only.

## Boundary

This is not yet the isolated-process Binder broker. It does not route
`node_sandboxed` scripts and does not grant new bridge capabilities. It gives
the current app-owned Android provider a shared broker contract that the future
isolated runtime can reuse.
