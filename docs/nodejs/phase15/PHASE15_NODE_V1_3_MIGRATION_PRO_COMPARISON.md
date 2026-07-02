# Phase 15 Node v1.3 Migration Pro Comparison

P15-26 updates migration, Pro comparison, compatibility, examples, and release
notes for Node Profile v1.3. The Gradle entry is:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase15MigrationProComparison
```

P15-26 records v1.3 migration and Pro comparison docs as ready. The docs must
state that claims match executable evidence, do not imply raw Android object
parity, and keep runtime authority unchanged.

The gate imports P14-36 plus P15-11, P15-22, P15-23, P15-24, and P15-25.
Provider rows remain `provider_poc_not_promoted`, MediaStore/playback remains
`stable_denied`, packaged aggregate remains `host_contract_ready`, and
`NodeProfileV13CapabilityCatalog` remains metadata-only.

Expected reports:

```text
build/reports/nodejs/phase15-migration-pro-comparison.json
build/reports/nodejs/phase15-migration-pro-comparison.md
```
