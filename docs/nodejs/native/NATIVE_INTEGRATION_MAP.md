# Native Integration Map

Phase 9 starts by making the current native boundary explicit before any Node
runtime promotion or adapter migration.

Generated report:

```powershell
.\gradlew.bat --console=plain :app:generateNodeNativeSymbolUsageReport
```

Outputs:

- `build/reports/nodejs/native-symbol-usage.json`
- `build/reports/nodejs/native-symbol-usage.md`

## Current Boundary

The current bridge is still a direct C++ embedder integration in
`app/src/main/cpp`. `node_bridge_internal.h` includes Node, V8, and libuv
headers and defines dynamic symbol pointer types for the embedded runtime.

The boundary map classifies references into:

- public Node embedder API references
- public V8 API references
- public libuv API references
- JNI-only bridge references
- Android dynamic loader references
- Android Bionic/libc and pthread references
- private or unstable Node/V8 boundary references
- binary imports and exports found in built native libraries

## Phase 9 Decision

This report is informational in the first Batch A pass. It may report
`blocked` when direct private or unstable references remain, but the Gradle
task does not fail on that status yet. The blocking status is expected until
S9-02 and S9-03 migrate runtime-specific details behind a versioned adapter.

The default runtime remains `node24_5`; this report does not enable
`node24_current` or Node 24.17.
