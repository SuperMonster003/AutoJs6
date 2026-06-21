# Android Native Compatibility

Phase 9 keeps Node 24.5 as the default runtime while adding native artifact
checks required for future Android runtime upgrades.

Host-side gates:

```powershell
.\gradlew.bat --console=plain :app:verifyNode16KbNativeAlignment
.\gradlew.bat --console=plain :app:generateNodeRuntimeArtifactManifest
.\gradlew.bat --console=plain :app:verifyNodeRuntimeArtifactIntegrity
```

Generated reports:

- `build/reports/nodejs/native-16kb-alignment.json`
- `build/reports/nodejs/native-16kb-alignment.md`
- `build/reports/nodejs/runtime-artifact-manifest.json`
- `build/reports/nodejs/runtime-artifact-manifest.md`
- `build/reports/nodejs/runtime-artifact-integrity.json`
- `build/reports/nodejs/runtime-artifact-integrity.md`

## Current Coverage

The host checks cover the current Node 24.5 runtime artifacts for:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

Checked libraries:

- `libnode.so`
- `libc++_shared.so`
- `libautojs6-node.so`

The 16 KB alignment gate verifies ELF `LOAD` segment alignment, `TEXTREL`
absence, and public Android dependency usage. The runtime artifact manifest
records file size, SHA-256, Build ID when available, dependency list, and page
size compatibility.

## Current Decision

The existing Node 24.5 `libnode.so` artifacts are 16 KB aligned. Current
`libc++_shared.so` artifacts are still 4 KB aligned, and the current
`armeabi-v7a` `libautojs6-node.so` build output is also 4 KB aligned. The
current Node 24.5 `libnode.so` artifacts also do not carry GNU Build IDs.

These gaps are recorded as `bootstrap_only` so Phase 9 can continue while the
native artifact baseline remains honest. Future promoted artifacts must include
16 KB-compatible alignment, per-ABI SHA-256 values, and Build IDs before default
runtime promotion.
