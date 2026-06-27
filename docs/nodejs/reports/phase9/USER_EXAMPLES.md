# Phase 9 User Examples

S9-49 adds checked-in user-facing examples under `sample/nodejs/` for the
native modernization, diagnostics, isolation, and safe extension platform
themes. They are static-verified by `:app:verifyNodeExamples`.

## Added Examples

| Example | Status | Coverage |
| --- | --- | --- |
| `node-runtime-info` | partial | Runtime version and descriptor-shape reporting without user-selected native runtimes. |
| `startup-snapshot` | experimental | Startup snapshot policy markers and state that must never be snapshotted. |
| `heap-snapshot` | experimental | Heap snapshot/allocation diagnostics policy markers while production inspector remains disabled. |
| `multi-instance` | experimental | Process-pool and independent execution policy markers. |
| `sandboxed-script` | experimental | Brokered sandbox execution policy and direct-capability denial shape. |
| `wasm-basic` | partial | Real `WebAssembly.compile()` / `instantiate()` add-module compute. |
| `wasi-scoped-fs` | experimental | Controlled `autojs6:wasi` facade policy and raw `node:wasi` safe probing. |
| `wasm-worker` | experimental | WASM worker integration policy while `worker_threads` remains default-disabled. |
| `wasm-plugin` | experimental | JS plus WASM plugin package shape without native addons or remote plugin loading. |

Existing Phase 8 examples continue to cover `compile-cache`, `inspector-debug`,
and `cpu-profile`, so S9-49 does not duplicate those directories.

## Safety Boundary

- Examples do not enable `.node` native addons.
- Raw `node:wasi` is not presented as a security sandbox.
- Worker, snapshot, heap snapshot, multi-instance, sandbox, and plugin examples
  print explicit design-gated policy markers when the runtime surface remains
  deferred.
- `wasm-basic` uses inline deterministic WASM bytes and no external downloads.
- `wasm-plugin` uses a checked-in local JS plugin with WASM metadata; native
  service plugins and native addons remain disabled.
