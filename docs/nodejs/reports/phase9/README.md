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

Node 24.17 is not the default runtime. The current default remains
`node24_5`, and raw native addons, unrestricted process spawning, raw sockets,
production Inspector, and private runtime entry points remain disabled.
