# Hardened Isolated Runtime

Status: Phase 8 manifest and policy POC; not executable

S8-41 records the first Android `isolatedProcess` proof of concept for a
future hardened Node runtime. It does not route scripts to the isolated service
yet.

## Current POC

The current implementation has two pieces:

- `NativeNodeSandboxedScriptService` is declared as `android:isolatedProcess`
  and `android:exported=false` under the `:nodejs_sandboxed` process suffix.
- `executionMode: "nodeSandboxed"` normalizes to `node_sandboxed`, but the mode
  is `design_gated`, has no supported launch surfaces, has no bridge
  providers, and rejects before user JavaScript runs.

This keeps the existing `NativeNodeEmbeddedScriptService` behavior unchanged.
Normal scripts still run only through the current bounded `one_shot`,
explicit `interactive_long_running`, or WorkManager-owned `scheduled` surfaces.

## Target Boundary

A future executable `node_sandboxed` runtime must satisfy all of these before
promotion:

- the Node process runs with `android:isolatedProcess=true`;
- the isolated process receives no app permissions directly;
- the isolated process does not read app-private project files by path;
- source, module data, and scoped file access are delivered through brokered
  descriptors or copied payloads;
- every bridge capability call crosses a Binder broker with explicit
  declaration, validation, size limits, cancellation, audit metadata, and
  execution ownership;
- Java/Android interop remains unavailable;
- `shell`, `shell.root`, native addons, raw process APIs, and unrestricted
  filesystem APIs remain unavailable;
- controlled network, if supported, dispatches only through the broker and the
  existing project `network` capability policy.

## Why It Is Not Executable Yet

The current bridge providers assume the normal app-owned embedded service can
access app-private cache directories, runtime module sources, provider state,
foreground notifications, and Android APIs through the app process authority.
An isolated process has a different UID and should not inherit that authority.

Executing real scripts in the isolated service before the broker exists would
either fail unpredictably on app-private resources or require widening the
isolated process boundary. Both outcomes are worse than a deliberate
design-gated POC.

## Required Broker

S8-42 owns the broker design. The minimum broker contract is:

- request and response envelopes are JSON or typed parcelables with schema
  versions;
- every operation maps to an explicit capability and effective runtime budget;
- source and file payloads are size-limited;
- all pending broker calls are cancellable on script stop, timeout, crash, or
  service destroy;
- audit rows include execution id, capability, operation, size, duration,
  result code, and cleanup status;
- no raw Android `Context`, provider object, file descriptor, native handle, or
  bridge resource object is exposed to JavaScript.

## Promotion Gate

Future promotion from POC to executable runtime must add:

- a positive isolated-process script smoke;
- UID/process diagnostics that prove isolation;
- brokered scoped source/module/file handoff;
- brokered bridge capability calls with denial-before-dispatch paths;
- controlled network through the broker only;
- Java interop, shell/root, native addon, raw process, and raw network denial;
- service stop, timeout, crash, and provider-destroy cleanup;
- packaged APK policy and manifest verifier coverage;
- rollback to the current design-gated rejection.
