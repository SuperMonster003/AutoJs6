# Phase 9 Reports

Phase 9 covers native runtime modernization, production performance paths, and
safe extension platform work while preserving the Safe Node Profile boundary.

## Batch A Gates

Current Batch A host-side tasks:

```powershell
.\gradlew.bat --console=plain :app:generateNodeNativeSymbolUsageReport
.\gradlew.bat --console=plain :app:verifyNodeRuntimeAdapterAbi
.\gradlew.bat --console=plain :app:verifyNodeRuntimeAdapterExport
.\gradlew.bat --console=plain :app:verifyNodeRuntimeBuildPlan
.\gradlew.bat --console=plain :app:verifyNode16KbNativeAlignment
.\gradlew.bat --console=plain :app:generateNodeRuntimeArtifactManifest
.\gradlew.bat --console=plain :app:verifyNodeRuntimeArtifactIntegrity
.\gradlew.bat --console=plain :app:verifyNodeRuntimeDistributionPolicy
.\gradlew.bat --console=plain :app:verifyNodeRuntimeArtifactGate
.\gradlew.bat --console=plain :app:verifyNodePhase9PerformancePolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9InspectorPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9IsolationPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9PluginPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9WasmWasiPolicy
.\gradlew.bat --console=plain :app:verifyNodePhase9NativeCrashPipeline
.\gradlew.bat --console=plain :app:verifyNodePhase9NativeModernizationPlan
.\gradlew.bat --console=plain :app:verifyNodeWasmWasiGate "-Pautojs.nodejs.test.deviceProfile=sony"
```

Generated outputs:

- `build/reports/nodejs/native-symbol-usage.json`
- `build/reports/nodejs/native-symbol-usage.md`
- `build/reports/nodejs/runtime-adapter-abi-v1.json`
- `build/reports/nodejs/runtime-adapter-abi-v1.md`
- `build/reports/nodejs/runtime-adapter-export.json`
- `build/reports/nodejs/runtime-adapter-export.md`
- `build/reports/nodejs/runtime-build-plan.json`
- `build/reports/nodejs/runtime-build-plan.md`
- `build/reports/nodejs/native-16kb-alignment.json`
- `build/reports/nodejs/native-16kb-alignment.md`
- `build/reports/nodejs/runtime-artifact-manifest.json`
- `build/reports/nodejs/runtime-artifact-manifest.md`
- `build/reports/nodejs/runtime-artifact-integrity.json`
- `build/reports/nodejs/runtime-artifact-integrity.md`
- `build/reports/nodejs/runtime-distribution-policy.json`
- `build/reports/nodejs/runtime-distribution-policy.md`
- `build/reports/nodejs/runtime-artifact-gate.json`
- `build/reports/nodejs/runtime-artifact-gate.md`
- `build/reports/nodejs/phase9-performance-policy.json`
- `build/reports/nodejs/phase9-performance-policy.md`
- `build/reports/nodejs/phase9-inspector-policy.json`
- `build/reports/nodejs/phase9-inspector-policy.md`
- `build/reports/nodejs/phase9-isolation-policy.json`
- `build/reports/nodejs/phase9-isolation-policy.md`
- `build/reports/nodejs/phase9-plugin-policy.json`
- `build/reports/nodejs/phase9-plugin-policy.md`
- `build/reports/nodejs/phase9-wasm-wasi-policy.json`
- `build/reports/nodejs/phase9-wasm-wasi-policy.md`
- `build/reports/nodejs/phase9-native-crash-pipeline.json`
- `build/reports/nodejs/phase9-native-crash-pipeline.md`

## Current Status

The Batch A start is intentionally conservative:

- S9-01 documents and reports the existing direct native boundary.
- S9-02 defines the versioned C ABI for runtime adapters.
- S9-03 has a first native surface increment: the Node 24.5 adapter source now
  exports `AutoJsNodeRuntimeApiV1`, but execution still routes through the
  legacy JNI path until the lifecycle implementation is migrated.
- S9-04 records the reproducible build skeleton for future Node 24.17 Android
  artifacts and locks the current upstream/fork provenance. See
  [../../native/RUNTIME_PROVENANCE.md](../../native/RUNTIME_PROVENANCE.md).
- S9-09/S9-11/S9-12 now have host-side current-runtime checks for 16 KB ELF
  alignment, runtime artifact manifests, and integrity metadata. See
  [../../native/ANDROID_NATIVE_COMPAT.md](../../native/ANDROID_NATIVE_COMPAT.md).
- S9-13/S9-14 now have host-side runtime distribution policy and runtime
  artifact aggregate gate coverage. See
  [../../native/RUNTIME_DISTRIBUTION.md](../../native/RUNTIME_DISTRIBUTION.md).
- S9-15/S9-20 now have a host-side performance policy gate that verifies
  compile-cache production evidence, keeps startup snapshots explicitly
  deferred, records process-only prewarm and memory-pressure coverage, and
  keeps the current performance matrix partial until packaged/SLO metrics exist.
  See [PERFORMANCE_POLICY.md](PERFORMANCE_POLICY.md).
- S9-21/S9-26 now have a host-side inspector policy gate that preserves the
  disabled runtime boundary, records DevTools/profiling/heap work as deferred,
  marks controlled diagnostic reports partial, and keeps the disabled-state
  inspector security evidence ready. See [INSPECTOR_POLICY.md](INSPECTOR_POLICY.md).
- S9-27/S9-33 now have a host-side isolation policy gate that records hardened
  isolated runtime, capability broker, and process-pool evidence as partial,
  keeps crash containment, per-runtime resource limits, and multi-instance
  consistency deferred, and keeps worker partial promotion blocked on native
  worker availability. See [ISOLATION_POLICY.md](ISOLATION_POLICY.md).
- S9-34/S9-40 now have a focused WebAssembly/WASI starting gate: pure
  WebAssembly compile/instantiate works through scoped `.wasm` bytes, while raw
  `wasi` / `node:wasi` remains disabled and unshadowable. See
  [WASM_WASI.md](WASM_WASI.md).
- S9-34/S9-40 also have a host-side WASM/WASI policy gate that records the
  current split between ready WebAssembly loading, deferred WASM compile cache,
  deferred `autojs6:wasi` facade/resource/worker/build workflow work, and the
  partial security corpus.
- S9-41/S9-43 now have a host-side plugin policy gate that keeps WASM plugin
  packages and native service plugins deferred, records the JS-only manager ABI
  and lifecycle state as partial, and preserves the native addon/plugin runtime
  denial. See [PLUGIN_POLICY.md](PLUGIN_POLICY.md).
- S9-44 now has a host-side native crash pipeline gate that verifies runtime
  artifact evidence, symbol archive/dependency reports, logcat/tombstone
  capture instructions, execution descriptor requirements, and offline
  symbolication instructions. See [NATIVE_CRASH_PIPELINE.md](NATIVE_CRASH_PIPELINE.md).

Node 24.17 is not the default runtime. The current default remains
`node24_5`, and `promotionDecision.node2417Promotion` is `deferred` because
Node 24.17 Android `libnode.so` artifacts are not currently producible. Raw
native addons, unrestricted process spawning, raw sockets, production
Inspector, and private runtime entry points remain disabled.
