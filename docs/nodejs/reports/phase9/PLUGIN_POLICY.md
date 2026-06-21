# Phase 9 Plugin Policy Gate

Status: bootstrap-only policy gate.

S9-41 through S9-43 define the next plugin-system boundary after the existing
project-local JS-only manager. This report does not enable plugin downloads,
signed archives, native provider plugins, Android plugin adapters, or raw
`.node` addons.

## S9-41 WASM Plugin Package

Current state: deferred.

The current plugin manager can load only project-local `.js` and `.cjs`
entries from `./plugins/<id>`. A future WASM plugin package must extend that
model with a declared WASM entry, explicit capability list, memory/time limits,
dependency manifest, package integrity metadata, and deterministic packaged
asset handling.

WASM plugin execution must continue to use scoped file/package resolution.
Raw `node:wasi` stays disabled, and a future controlled WASI facade must be
capability-gated before any WASI-oriented plugin format can run.

## S9-42 Native Service Plugin Design Gate

Current state: deferred.

Native service plugins are a possible future safe alternative to in-process
Node native addons. They must be separately signed Android plugin packages or
host-owned services with an explicit protocol, signature permission,
AIDL/Parcelable boundary, capability-scoped RPC, crash isolation, and no native
code loaded into the embedded Node process.

The current v1.1 manager rejects `.node` files inside plugin directories and
does not enable native plugin archives, Android provider plugin adapters,
marketplace/runtime plugin distribution, or direct `process.dlopen` authority.

## S9-43 Plugin ABI And Lifecycle

Current state: partial.

The JS-only manager already provides a local manager-v1 ABI surface:
`install`, `manifest`, `resolve`, `load`, `start`, `stop`, `enable`,
`disable`, `configure`, `unload`, `reload`, `status`, `capabilities`, version,
dependencies, named exports, per-plugin namespace metadata, effective
capability subsets, debug-only reload, and plugin-owned resource cleanup.

A future signed-plugin ABI still needs an explicit protocol version,
capability negotiation record, health check, timeout, cancellation, resource
ownership transfer rules, compatibility matrix, package integrity verification,
update/signature policy, packaged verifier behavior, and native crash
attribution if native service plugins are ever approved.
