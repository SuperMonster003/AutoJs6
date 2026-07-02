# Phase 14 Node v1.2 Type Declaration Profiles

P14-35 updates the TypeScript declaration profiles for Node Profile v1.2.

The Gradle gate is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase14TypeDeclarationProfiles
```

## Scope

The gate extends `profile_capabilities.d.ts` with
`NodeProfileV12CapabilityCatalog` and `NodeProfileV12DeclarationMatrix`.
The matrix covers promoted callable declarations, provider POC declarations,
stable-denied surfaces, intentionally unsupported worker/process-worker
surfaces, and the P14-34 packaged aggregate smoke contract.

The declaration matrix is metadata-only:

- `metadataOnly: true`;
- `grantsAuthority: false`;
- future-only methods stay typed as gated, denied, unsupported, or
  denial-oriented declarations;
- raw Android objects and raw desktop process or worker authority are not
  declared as callable authority.

## Type Smoke

`sample/nodejs/typescript-smoke/src/main.ts` now type-checks v1.2 profile
helpers for:

- `ui.overlay`;
- `media_projection`;
- `work_manager`;
- `recorder.session`;
- `worker_threads`;
- `media.playback`;
- `packaged.aggregate`.

## Reports

Expected report artifacts:

```text
build/reports/nodejs/phase14-type-declaration-profiles.json
build/reports/nodejs/phase14-type-declaration-profiles.md
```
