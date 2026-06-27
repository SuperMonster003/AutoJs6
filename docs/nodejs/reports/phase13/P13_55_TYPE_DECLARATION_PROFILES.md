# P13-55 Type Declaration Profiles

P13-55 records TypeScript profile declarations as partial. The landing adds
`docs/nodejs/types/autojs6-node/profile_capabilities.d.ts` as a
declaration-only matrix for Safe/Pro/Desktop/Debug profile differences.

## Scope

The declaration matrix covers the P13-53 and P13-54 example surfaces:

- `$autojs.java`, `java`, and `rhino.install`.
- UI, overlay, WorkManager tasks, screenshot/image/OCR, and accessibility.
- ESM, `worker_threads`, HTTP/HTTPS, raw network, advanced fs, WASI,
  inspector, package manager, and sanitized pure JavaScript npm dependency
  usage.

The file provides:

- `RuntimeProfileId`
- `ProfileModuleCatalog`
- `ProfileModuleModeFor`
- `ProfileRequiredFor`
- `SafeDefaultModeFor`
- `PackagedBehaviorFor`
- `ProfileModuleRequiresDeclaration`
- `ProfileDeclarationMatrix`

These types document profile requirements, Safe-profile denial behavior, and
packaged behavior. They do not declare denied desktop modules as callable
modules and do not grant runtime authority.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-type-declaration-profiles.js --repo-root . --report-dir build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13TypeDeclarationProfiles
```

Reports:

- `build/reports/nodejs/phase13-type-declaration-profiles.json`
- `build/reports/nodejs/phase13-type-declaration-profiles.md`

## Current Decision

Status is `partial`.

This gate proves the declaration matrix, package index wiring, README
documentation, `typescript-smoke` Safe/Pro/Desktop coverage, and Phase 13 docs.
Runtime profile promotion remains diagnostic-only; denied desktop modules are
type-described as gated surfaces instead of callable module declarations.
