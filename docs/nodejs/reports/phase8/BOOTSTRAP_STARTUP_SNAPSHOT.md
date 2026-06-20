# S8-34 AutoJs6 Bootstrap Startup Snapshot

Status: design-gated, runtime enablement deferred

S8-34 evaluated startup snapshot support for AutoJs6 Embedded Node. The result
is a design gate rather than a runtime feature: the v1.1 path keeps normal
bootstrap as the only active path, and no snapshot blob is generated, packaged,
loaded, or requested.

## Findings

The current native lifecycle creates a fresh runtime with no snapshot data:

- `node::NewIsolate(..., nullptr, isolateSettings)`
- `node::CreateIsolateData(..., nullptr)`
- `node::NewContext(isolate, ...)`
- `node::CreateEnvironment(...)`
- `node::LoadEnvironment(environment, generatedSource, ...)`

The generated source is produced by `buildEmbeddedScriptExecutionSource()` and
contains request-specific values:

- entry source and source name
- working directory and sandbox root
- `moduleSources` and runtime module sources
- controlled environment values
- ESM, dynamic import, worker, and Java interop feature flags
- request-local policy and diagnostic records

Those values are explicitly forbidden from a startup snapshot. Snapshotting the
current generated source would capture project state, paths, env, and mutable
loader records instead of just immutable Safe Node helpers.

The bundled Node embedder headers also require a specific snapshot lifecycle:
the same `EmbedderSnapshotData` must be passed to isolate and isolate-data
creation, the first environment must use the snapshot context path, the blob
must match the exact Node/V8 flags, and the blob must stay alive for the
isolate lifetime. AutoJs6 does not yet have a generator, manifest, asset,
loader, invalidation key, or diagnostics for that lifecycle.

## Decision

Do not enable startup snapshots in v1.1.

The accepted S8-34 output is:

- a documented design gate in
  `docs/nodejs/design/BOOTSTRAP_STARTUP_SNAPSHOT.md`;
- an explicit capability-matrix row marking the feature as design-gated;
- testing, compatibility, security, and performance docs that state normal
  bootstrap remains the fallback/current path;
- no production behavior change and no packaged APK behavior change.

## Future Acceptance

Future implementation must first split bootstrap into:

- an immutable prelude that can be snapshotted;
- a request install layer that receives cwd, env, source, module graphs,
  capabilities, provider handles, execution ids, and timeout policy only after
  a clean environment is created.

Promotion requires focused proof for:

- normal bootstrap fallback on missing, corrupted, stale, wrong-version,
  wrong-ABI, wrong-flag, wrong-schema, or wrong-loader-hash snapshots;
- no cwd/env/source/module/provider/capability leakage across consecutive
  scripts;
- CJS, ESM, TypeScript-after-stripping, dynamic import, and packaged graph
  smoke coverage;
- bridge denial and provider cleanup with snapshot enabled;
- compile-cache interaction;
- startup and RSS measurements for the S8-36 four-mode matrix.

## Security Boundary

Startup snapshots must never contain Android `Context`, Binder, providers,
permissions, user paths, project roots, execution ids, open resources, module
cache entries from a project, request env, or any mutable per-execution state.

Scripts must not select a snapshot blob or turn fallback into a hard failure.
If a snapshot load attempt ever fails, script correctness and Safe Node policy
must match the normal bootstrap path.

## Current Gate

Current validation for this design gate is static and host-side:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeCompatibilityProfile
git diff --check
git diff -- version.properties
```

No device gate is added for S8-34 because no runtime behavior is enabled.

