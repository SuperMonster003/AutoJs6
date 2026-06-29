# Final Integration Definition

Phase 11 is the AutoJs6 Node Platform completion stage. It ends only when the
project can move from Node.js integration work to runtime maintenance,
compatibility expansion, provider expansion, security maintenance, and
performance work.

The final integration status is one of:

- `completed`
- `blocked`

`completed_with_recorded_blockers` is not a valid final status for required
internal capabilities. Intentional non-goals and external runtime maintenance
lanes may remain recorded without blocking completion.

## Required Completion Criteria

Stable runtime surface:

- CommonJS, partial ESM, local dynamic import, scoped fs, package exports, pure
  JS npm packages, moduleSources, encrypted moduleSources, and packaged runtime
  descriptors have positive and negative tests.
- Stable APIs publish frozen module names, export names, error codes,
  capability names, cancellation semantics, and resource ownership rules.
- Rhino fallback, backend switches, native one-shot execution, scoped fs, and
  capability manifest enforcement remain intact.

Packaged parity:

- Every stable main-app Node capability has a packaged APK decision.
- Packaged APKs use the same safe profile and deny the same unsafe modules.
- Packaged dependency graphs are locked, verified, budget checked, and reject
  native addons and package scripts.

Native adapter:

- `AutoJsNodeRuntimeApiV1` is a versioned, size-aware C ABI.
- The public ABI uses opaque handles, primitive values, byte views, and UTF-8
  JSON descriptors only.
- JNI/public bridge code no longer depends on direct Node, V8, libuv, or
  runtime-private types.
- `adapter_v1` creates, executes, polls, stops, diagnoses, and destroys the
  Node 24.5 runtime.
- `legacy_jni` remains available as a selectable diagnostic fallback.

Crash diagnostics:

- Fatal crash marker writes are async-signal-safe.
- The next launch collects a crash bundle with marker, tombstone reference,
  logcat tail, native hashes, Build IDs, runtime descriptor, package identity,
  ABI, feature flags, timeline tail, and resource snapshot.
- Symbolization tooling can verify and render the crash bundle.
- Crash/chaos injection proves the main app can recover.

Package manager:

- Android-side pure-JS package install/update/remove/list/verify/prune is
  cancellable, transactional, locked, integrity checked, and lifecycle-script
  free.
- Registry access is allowlisted, bounded, timeout controlled, and redirect
  limited.
- Tarball extraction denies path traversal, absolute paths, NUL bytes, links,
  device/FIFO entries, oversized files, oversized trees, and conflicting
  duplicates.

Long-running and multi-instance:

- Interactive and packaged long-running modes have foreground notification,
  visible stop, budget, watchdog, checkpoint, cleanup, and process-death
  evidence.
- At least two isolated Node execution slots are available for the main app.
- Each slot has independent process, runtime, working directory, capability
  set, ResourceRegistry, and crash evidence.
- Stopped or crashed execution leaves no owned resource alive.

Provider stability:

- Stable providers are not fake-provider-only and not host-only.
- Stable providers have real provider tests, capability denial tests,
  cancellation evidence, cleanup evidence, and packaged behavior.

ABI and device matrix:

- `arm64-v8a`, `armeabi-v7a`, and `x86_64` validate native load, adapter
  export, embedded smoke, CommonJS, ESM, scoped fs, and packaged static checks.
- Sony remains the primary real-device gate, with a second OEM device and
  x86_64 emulator evidence before final completion.

Ecosystem, security, and soak:

- Real npm corpus reaches at least 100 fixtures.
- Real project corpus reaches at least 10 complete projects.
- Security/fuzz v2 covers resolver, ESM/dynamic import, scoped fs, package tar,
  lockfile, bridge RPC, provider quotas, slot isolation, checkpoint, worker
  messages, WASI preopens when present, plugin capabilities, and adapter
  malformed input.
- Final soak records 24-hour RSS, native heap, Java heap, thread, fd, libuv
  handle, bridge resource, slot restart, network reconnect, and scheduled-run
  evidence without monotonic leaks or orphaned resources.

## Permanent Safe Profile Boundaries

The following remain intentional non-goals when they are denied with stable
diagnostics:

- native addon `.node`
- unrestricted `child_process`
- raw `net`, `tls`, `dns`, and `dgram`
- private Node bindings and `process.dlopen`
- raw Java or Android object exposure
- unrestricted `vm`
- production Inspector
- raw `node:wasi`

## External Runtime Maintenance

Node 24.17 stays outside final integration completion until Android
`libnode.so` artifacts and headers exist for the required ABIs. The default
runtime remains `node24_5` in Phase 11.

For the v1.1 closeout, required ABI completion means the checked-in Node 24.5
runtime libraries for `arm64-v8a`, `armeabi-v7a`, and `x86_64` are present and
the adapter ABI/export reports are ready. Runtime artifact build-plan,
alignment, manifest, integrity, and distribution reports may remain
`bootstrap_only` only as recorded external runtime maintenance for the future
Node 24.17 lane.
