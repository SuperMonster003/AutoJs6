# P13-56 Migration And Pro9 Comparison Docs

P13-56 records migration and Pro9 comparison docs as partial. The landing
updates `MIGRATION_FROM_RHINO.md`,
`INTEGRATION_STATUS_PRO9_COMPARISON.md`, and
`COMPATIBILITY_NOTES_PRO_AUTOX.md` so script developers see the same
classification vocabulary across Rhino migration, Auto.js Pro 9 comparison, and
AutoX compatibility notes.

## Scope

The synchronized labels are:

- Pro-compatible: Safe-profile bridge APIs or documented Pro-profile migration
  paths for Auto.js Pro/Rhino-style scripts.
- Desktop-compatible: local desktop Node.js patterns that AutoJs6 can support
  under scoped Android limits, static examples, or explicit capability gates.
- gated/diagnostic-only: debug-only diagnostics, provider POCs, static example
  catalogs, TypeScript metadata rows, or stable-denied surfaces that are not
  callable by normal scripts.

debug-only/provider POC is not formal availability. The docs explicitly keep
`sample/nodejs/pro-parity-suite`, `sample/nodejs/desktop-parity-suite`, and
`profile_capabilities.d.ts` as evidence or metadata only. Runtime authority for
Java/Rhino proxy globals, raw floaty, unrestricted UI-thread execution,
daily/weekly task rows, raw network sockets, expanded filesystem roots,
`worker_threads`, `vm`, `inspector`, WASI, native addons, `child_process`,
Android npm CLI, registry install, lifecycle scripts, and native payloads
remains gated.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-migration-pro9-comparison.js --repo-root . --report-dir build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13MigrationPro9Comparison
```

Reports:

- `build/reports/nodejs/phase13-migration-pro9-comparison.json`
- `build/reports/nodejs/phase13-migration-pro9-comparison.md`

## Current Decision

Status is `partial`.

This gate proves the migration and comparison docs use the same
Pro-compatible, Desktop-compatible, and gated/diagnostic-only taxonomy without
turning debug-only/provider POC evidence into formal availability. Runtime
profile promotion, packaged Pro/Desktop migration cookbooks, prompt/disclosure
UX, device permission matrices, and the P13-57 final summary gate remain open.
