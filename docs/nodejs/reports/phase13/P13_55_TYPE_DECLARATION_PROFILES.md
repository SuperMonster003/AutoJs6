# P13-55 Type Declaration Profiles

P13-55 records TypeScript profile declarations as partial capability evidence.
The P13-55 declaration gate is ready: the landing adds
`docs/nodejs/types/autojs6-node/profile_capabilities.d.ts` as a declaration-only
matrix for Safe/Pro/Desktop/Debug profile differences.

## Scope

The declaration matrix covers the P13-53 and P13-54 example surfaces:

- `$autojs.java`, `java`, and `rhino.install`.
- UI, overlay, WorkManager tasks, screenshot/image/OCR, and accessibility.
- ESM, `worker_threads`, HTTP/HTTPS, raw network, advanced fs, WASI,
  inspector, package manager, and sanitized pure JavaScript npm dependency
  usage.

The gate also checks that profile declarations stay aligned with P13-49 profile
selection diagnostics, P13-51 rollback policy, and P13-52 privacy/disclosure
policy metadata.

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

Conditional declarations remain tied to runtime profile promotion: the matrix
records `targetProfile` and `targetMode` metadata plus no-authority flags, while
the effective runtime profile stays `safe_default` until the P13-49/P13-52
promotion, rollback, and disclosure gates are implemented.

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

Status is `ready` for the declaration gate.

This gate proves the declaration matrix, package index wiring, README
documentation, `typescript-smoke` Safe/Pro/Desktop coverage, P13-49/P13-51/P13-52
profile policy type alignment, and Phase 13 docs. Denied desktop modules are
enforced as type-described gated surfaces instead of callable module
declarations.

Runtime profile promotion remains owned by P13-49/P13-52 and later promotion
gates; P13-55 keeps declarations metadata-only until then. Generated
declaration package release checks remain future release-packaging work after
profile promotion.
