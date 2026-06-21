# Runtime Adapter ABI

S9-02 defines a versioned C ABI between the AutoJs6 host bridge and
Node-version-specific runtime adapters.

Host-side verification:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRuntimeAdapterAbi
.\gradlew.bat --console=plain :app:verifyNodeRuntimeAdapterExport
```

Outputs:

- `build/reports/nodejs/runtime-adapter-abi-v1.json`
- `build/reports/nodejs/runtime-adapter-abi-v1.md`
- `build/reports/nodejs/runtime-adapter-export.json`
- `build/reports/nodejs/runtime-adapter-export.md`

## ABI v1

Header:

- `app/src/main/cpp/node_runtime_api_v1.h`

Current adapter surface:

- `app/src/main/cpp/node_runtime_adapter_24_5.cpp`

Export:

- `autojs_node_get_runtime_api_v1`

Core table:

- `AutoJsNodeRuntimeApiV1`
- `feature_bits`
- `get_node_version`
- `get_runtime_descriptor`
- `create_runtime`
- `execute_script`
- `execute_commonjs`
- `execute_module`
- `poll_runtime`
- `request_stop`
- `force_stop`
- `get_state`
- `get_diagnostics`
- `destroy_runtime`
- `post_bridge_result`

The ABI carries primitive values, opaque handles, UTF-8 JSON descriptors, and
byte views only. It intentionally excludes C++ standard library types, JNI
objects, and runtime-specific embedder handles from the host/adapter boundary.

Every public structure starts with `struct_size` so later revisions can add
fields without breaking older hosts.

Phase 11 freezes the final v1 operation names while leaving legacy alias slots
at the end of the table for source compatibility during migration. New host
code should use the final names above. The adapter must not become the default
runtime path until script, CommonJS, ESM, moduleSources, packaged execution,
poll, stop, diagnostics, crash injection, memory, and legacy/adapter A/B gates
all pass.

## Current Status

The ABI header and verifier are checked in first. Existing Node 24.5 bridge
code has not yet been migrated behind this adapter, and Node 24.17 is not
promoted. The current Node 24.5 adapter source exports the final ABI table and
runtime descriptor with unavailable execution stubs so the native export can be
compiled and verified before the legacy JNI execution path is moved behind it.
`verifyNodeRuntimeAdapterExport` builds the app debug native outputs and records
the adapter export, SHA-256, and Build ID for `arm64-v8a`, `armeabi-v7a`, and
`x86_64`.
