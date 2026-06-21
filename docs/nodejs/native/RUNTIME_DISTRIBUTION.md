# Runtime Distribution Policy

S9-13 defines build-time runtime distribution modes without enabling arbitrary
remote native runtime downloads.

Host-side gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeRuntimeDistributionPolicy
```

Generated reports:

- `build/reports/nodejs/runtime-distribution-policy.json`
- `build/reports/nodejs/runtime-distribution-policy.md`

## Modes

- `bundled`: current supported mode. Runtime libraries are packaged in the base
  APK under `app/src/main/jniLibs`.
- `split-by-abi`: planned. Must use the same runtime artifact manifest before
  promotion.
- `app-bundle-dynamic-feature`: planned. Runtime remains application-delivered
  code.
- `localDeveloperRuntime`: planned debug-only mode for app-private, signed, and
  read-only runtime artifacts.
- `remoteNativeRuntimeDownload`: denied.

## Current Decision

Bundled Node 24.5 runtime coverage exists for `arm64-v8a`, `armeabi-v7a`, and
`x86_64`. Other distribution modes are intentionally `planned`, so this gate is
`bootstrap_only` until the runtime artifact manifest is consumed by each
distribution path.
