# Phase 9 WebAssembly And WASI Gate

S9-34 through S9-40 introduce the safe extension direction for Phase 9:
WebAssembly is allowed as a pure JavaScript/V8 capability, while raw
`node:wasi` remains disabled until AutoJs6 owns a scoped `autojs6:wasi` facade.

Run:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeWasmWasiGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

## Current Coverage

`NodeWasmWasiInstrumentationTest` verifies:

- `WebAssembly.compile`, `WebAssembly.instantiate`, `WebAssembly.Module`, and
  `WebAssembly.validate` work for a small deterministic module;
- `.wasm` bytes can be read through the existing scoped `fs` path from the
  script working directory and instantiated;
- raw `wasi` and `node:wasi` builtins reject with the disabled-builtin error;
- local `node_modules/wasi` cannot shadow the disabled raw WASI policy;
- `require("autojs6:profile").disabledBuiltins` reports `wasi`.

## Policy

This gate does not expose raw WASI. It is a safe Batch F starting point:

- WebAssembly compute stays inside the existing script execution boundary.
- `.wasm` input is loaded only through existing scoped file APIs.
- Native `.node` addons remain disabled.
- Raw `node:wasi` remains disabled.
- Future `autojs6:wasi` must map preopens through the scoped-fs broker and must
  deny arbitrary host paths, inherited process env, raw fd numbers,
  unrestricted sockets, and process termination.

## Remaining Work

Later Phase 9 WASM/WASI work still needs:

- WASM compile-cache policy and quota;
- packaged binary asset manifest and tamper checks;
- `autojs6:wasi` facade design and implementation;
- resource limits for module size, memory pages, stdout/stderr, timeout, and
  concurrent instances;
- worker/process isolation for heavy modules;
- Rust, AssemblyScript, and WASI build workflow templates;
- WASM/WASI security corpus.
