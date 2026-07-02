# Phase 15 Node v1.3 Type Declaration Profiles

P15-25 updates the TypeScript declaration profiles for Node Profile v1.3.

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15TypeDeclarationProfiles
```

The gate extends `profile_capabilities.d.ts` with
`NodeProfileV13CapabilityCatalog`, v1.3 capability state helpers, packaged
evidence helpers, future-only helpers, stable-denied helpers, and
`NodeProfileV13DeclarationMatrix`.

The v1.3 matrix is metadata-only. It records provider POC, docs sync, security
corpus, packaged aggregate, and stable-denied state, but it does not grant
runtime authority or make future-only methods successful.

Expected reports:

```text
build/reports/nodejs/phase15-type-declaration-profiles.json
build/reports/nodejs/phase15-type-declaration-profiles.md
```
