# AutoJs6 Bootstrap Startup Snapshot Design Gate

Status: design gate only. AutoJs6 does not enable a bootstrap startup snapshot
in the v1.1 runtime path. Embedded Node continues to create a fresh isolate,
context, environment, and request-local bootstrap source for each execution.

## Goal

S8-34 evaluates whether AutoJs6 can snapshot immutable Safe Node bootstrap
setup to reduce startup latency without snapshotting request state or Android
capability state.

The only snapshot candidates are immutable setup records:

- safe global installers
- builtin wrapper factories
- error factories
- loader helper code
- capability schema definitions
- static policy constants that do not depend on the request

## Current Decision

Keep startup snapshots disabled for v1.1 until the bootstrap is split into a
snapshot-safe immutable prelude and a request-specific install layer.

The current Android embedder path is not snapshot-ready:

- `node::NewIsolate()` and `node::CreateIsolateData()` are both called with
  `snapshot_data=nullptr`.
- `node::NewContext()` creates a fresh context before
  `node::CreateEnvironment()`.
- `node::LoadEnvironment()` receives one generated JavaScript source per
  request.
- `buildEmbeddedScriptExecutionSource()` embeds the entry source, source name,
  working directory, `moduleSources`, runtime module sources, controlled
  environment values, and feature flags into that generated source.
- There is no snapshot generator, snapshot blob asset, native loading path,
  invalidation key, or diagnostics envelope.

The local Node embedder headers also make the native contract stricter than a
plain JavaScript `v8.startupSnapshot` feature check:

- the same `EmbedderSnapshotData` instance must be passed to
  `NewIsolate()` and `CreateIsolateData()`;
- the first environment created from a snapshot expects the snapshot context
  flow rather than the current explicit `NewContext()` path;
- the snapshot must match the exact Node version and V8 flags;
- the snapshot data must outlive the isolate that uses it;
- the upstream embedder snapshot API is unstable and may change across
  Node versions;
- `CanUseCustomSnapshotPerIsolate()` is currently documented by the bundled
  Node headers as unavailable when V8 shared readonly heap constraints apply.

Those requirements conflict with enabling a small runtime patch safely. The
normal bootstrap path is therefore the fallback and the only active v1.1 path.

## Non-Goals

Startup snapshot support must not snapshot or preserve:

- V8 `Context` objects created for a user execution
- Android `Context`, Binder objects, providers, callbacks, or Java references
- user source, source names, working directories, or project roots
- permission grants or project capability manifests
- execution ids, launch surfaces, timeout policies, or runtime slots
- `moduleSources`, encrypted packaged graph contents, or request env values
- open file descriptors, libuv handles, bridge resources, timers, watchers, or
  pending calls
- module cache entries loaded from a user project
- `process.env`, `process.cwd()`, or mutable globals from a previous run

It is also not a user-visible API. Scripts must not be able to choose a
snapshot blob, disable fallback, or observe host paths embedded in a snapshot.

## Required Architecture

Future startup snapshot support needs these steps before any promotion:

1. Split the generated bootstrap into two layers:
   - an immutable prelude containing only pure helper functions and schemas;
   - a request install layer that receives source, cwd, env, module records,
     capabilities, providers, and execution ids after environment creation.
2. Add a host/native snapshot generator that builds the immutable prelude only.
   The generator must use anonymized source names and must not include host
   build paths.
3. Package the snapshot blob with a manifest containing schema version, Node
   version, V8 version, ABI, V8 flags, AutoJs6 runtime profile, loader version,
   and prelude hash.
4. Add a native loader that validates the manifest and snapshot data before
   passing `EmbedderSnapshotData` to Node.
5. Keep a single fail-soft fallback path: any missing, stale, incompatible, or
   rejected snapshot must run the normal bootstrap without changing script
   correctness.
6. Add `embedded_script.startup_snapshot.*` diagnostics and Doctor reporting
   for requested, used, fallback reason, manifest key, schema, and timing.
7. Prove request isolation with focused tests before measuring performance.

## Security Requirements

Startup snapshots are allowed only if the following remain true:

- scoped filesystem checks run after request state is installed;
- bridge providers are attached after request capability validation;
- `require("autojs6:profile")` reports request-local state only;
- `process.env` filtering still happens per request;
- module cache entries from one project cannot appear in another project;
- package condition, builtin, TypeScript, dynamic import, and compile-cache
  policies use the current request flags;
- stale snapshot data cannot bypass a newer loader or capability schema;
- snapshot load failure cannot silently skip security checks.

## Required Tests

Promotion from this design gate requires at least:

- normal bootstrap fallback when no snapshot exists;
- fallback when the blob is missing, corrupted, stale, wrong ABI, wrong Node
  version, wrong V8 version, wrong loader hash, or wrong schema;
- CJS, ESM, TypeScript-after-stripping, dynamic import, and packaged
  `moduleSources` smoke tests with snapshot enabled;
- two consecutive scripts with different cwd, env, capability manifest, source
  name, and module graph proving no leakage;
- bridge provider denial and cleanup tests with snapshot enabled;
- compile-cache plus snapshot interaction tests;
- startup/RSS performance comparison for no cache/no snapshot, compile cache
  only, snapshot only, and compile cache plus snapshot;
- packaged APK smoke proving descriptor freshness and fallback behavior.

## Packaged APK Policy

Packaged APKs may use only a runtime-owned snapshot blob that matches the
packaged runtime descriptor. A project must not inject an arbitrary snapshot.
If the packaged descriptor and snapshot manifest disagree, the runtime must
fall back to normal bootstrap and report the mismatch.

## Relationship To S8-33 And S8-36

S8-33 module compile cache is already enabled as a fail-soft internal
optimization because it does not replace isolate, context, or environment
creation. Startup snapshot support changes the native environment creation path
and therefore remains behind this design gate.

S8-36 should keep reporting the current modes as:

- no cache / no snapshot: unavailable while S8-33 compile cache remains an
  internal default with no disable switch
- compile cache only
- snapshot only: unavailable until this gate is implemented
- compile cache plus snapshot: unavailable until this gate is implemented
- compile cache plus process prewarm: measured as a separate S8-35 axis, not
  as a snapshot substitute
