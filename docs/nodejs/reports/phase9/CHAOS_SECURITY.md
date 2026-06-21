# Phase 9 Chaos And Security Corpus Gate

Status: bootstrap-only policy gate.

S9-45 and S9-47 define the failure-injection and hostile-input matrix that
must protect the Phase 9 native runtime platform. This report records the
current evidence without pretending that Node 24.17, process-pool crash
containment, WASI, workers, or signed native service plugins are production
ready.

## S9-45 Chaos Tests

Current state: partial.

The current chaos corpus is split across host artifact gates, device policy
gates, and documented deferred work:

- adapter load failure is covered by the runtime adapter export and ABI gates;
- wrong runtime hash is covered by runtime artifact manifest and integrity
  reports with SHA-256 evidence;
- runtime process crash is covered by the native crash pipeline evidence
  bundle and crash intake workflow, but automated injection is deferred;
- Binder death and provider process death are tracked by the isolated runtime
  and process-pool policy gates, but the Binder broker does not execute user
  scripts yet;
- compile cache corruption is fail-soft by policy through stale or corrupt
  entry fallback, while deeper corruption injection remains deferred;
- snapshot corruption is deferred because startup snapshots remain disabled;
- worker crash is blocked while native `worker_threads` remains unavailable;
- WASM timeout is deferred until WASM-specific resource limits exist;
- low-memory callback coverage exists through process-only prewarm expiry and
  `TRIM_MEMORY_RUNNING_LOW`;
- 16 KB-only environment coverage exists as a host-side alignment and artifact
  readiness gate until a 16 KB Android 15+ device/emulator gate is available.

## S9-47 Security Corpus

Current state: partial.

The security corpus currently verifies or records:

- runtime artifact substitution through manifest/hash/integrity evidence;
- C ABI version mismatch through `AutoJsNodeRuntimeApiV1` and adapter export
  checks;
- 16 KB alignment rejection through ELF `LOAD` segment alignment checks;
- dynamic native file not read-only as a required distribution policy for
  localDeveloperRuntime and future dynamic runtime loading;
- isolated broker capability bypass as deferred until Broker v3 owns the
  isolated Binder boundary;
- worker capability inheritance as blocked by disabled native workers;
- WASI preopen escape as deferred because raw `node:wasi` is disabled and the
  controlled `autojs6:wasi` facade is not implemented;
- plugin signature mismatch as deferred because signed plugin archives and
  native service plugins are not enabled;
- inspector release bypass through the disabled-state inspector security gate.

## Required Promotion Rule

This gate may be `bootstrap_only` while it has no missing evidence. Promotion
to `ready` requires runnable injection coverage for adapter-load failure, hash
mismatch, process crash, Binder death, provider death, cache corruption,
snapshot corruption, worker crash, WASM timeout, low-memory callback, and a
16 KB-only device or emulator. It also requires runnable hostile-input coverage
for runtime artifact substitution, C ABI mismatch, 16 KB rejection, dynamic
native file mutability, isolated broker bypass, worker capability inheritance,
WASI preopen escape, plugin signature mismatch, and inspector release bypass.

## Safe Boundary

This corpus does not enable arbitrary `.node` addons, unrestricted
`child_process`, raw network modules, production Inspector, raw `node:wasi`,
`process.binding`, `process.dlopen`, signed native plugin archives, or remote
native runtime download.
