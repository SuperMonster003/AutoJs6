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
.\gradlew.bat --console=plain :app:verifyNodePhase9NativeModernizationPlan
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

Node 24.17 is not the default runtime. The current default remains
`node24_5`, and `promotionDecision.node2417Promotion` is `deferred` because
Node 24.17 Android `libnode.so` artifacts are not currently producible. Raw
native addons, unrestricted process spawning, raw sockets, production
Inspector, and private runtime entry points remain disabled.
