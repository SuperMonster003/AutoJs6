# Phase 9 Real npm Corpus Expansion

S9-46 expands the fixed offline real npm corpus from the Phase 8 baseline of
20 fixtures to 30 fixtures. The added fixtures stay checked in, license-noted,
CommonJS-runner based, and pure JavaScript only.

Focused gate:

```powershell
$env:ANDROID_SERIAL='QV710AF65F'
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=org.autojs.autojs.engine.NodeRealNpmCompatInstrumentationTest" "-Pautojs.nodejs.test.deviceProfile=sony"
```

## Added Fixtures

| Fixture | Coverage |
| --- | --- |
| `wasm-basic-package` | WebAssembly compile/instantiate smoke using deterministic pure bytecode compute. |
| `wasm-buffer-package` | Buffer-backed WASM bytes and repeated instantiation from one compiled module. |
| `wasi-policy-package` | Policy-aware raw WASI detection that remains compatible when desktop Node exposes `node:wasi` and AutoJs6 disables it. |
| `diagnostics-report-package` | Inspector-free diagnostics package shape with graceful `process.report` probing and no DevTools dependency. |
| `concurrency-pool-package` | Promise pool, timers, microtasks, and deterministic concurrent result aggregation. |
| `compile-cache-graph-package` | Compile-cache-sensitive repeat `require()` over a wider reusable CommonJS graph. |
| `typescript-wasm-package` | TypeScript source metadata plus runtime-safe CommonJS/WASM compute fallback. |
| `worker-wasm-fallback-package` | Worker/WASM package shape with graceful `worker_threads` detection and inline WASM fallback. |
| `large-esm-graph-package` | CJS `require(esm)` over a larger ESM dependency fanout. |
| `package-integrity-package` | Package integrity manifest hashing with a pure JS crypto utility. |

## Safety Boundary

- fixtures stay offline and checked in;
- every fixture has a `LICENSE_NOTE.md`;
- fixture roots keep CommonJS `test.cjs` runners;
- no install, postinstall, prepare, or other lifecycle scripts are used;
- no native `.node` addons or native shared libraries are vendored;
- ESM package metadata remains allowed only below fixture `node_modules`;
- the Android connected runner executes fixed 6-fixture batches so the larger
  corpus does not require loosening the existing 64-moduleSources runtime
  budget;
- raw WASI, worker, inspector, and native extension probes normalize to a safe
  compatibility output without enabling those capabilities.

## Verification Notes

The 10 new fixtures were run individually on desktop Node and produced their
expected `realnpm.<fixture>=...` markers. The full desktop differential harness
still contains existing expected AutoJs6 restriction fixtures for HTTP/fetch
denial, so the Android connected gate remains the authoritative 30/30 pass
signal for the fixed corpus.
