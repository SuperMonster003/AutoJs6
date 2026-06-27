# P13-54 Desktop Node Parity Examples

P13-54 records desktop Node parity examples as partial. The landing adds
`sample/nodejs/desktop-parity-suite` as a static, machine-verifiable catalog
for desktop-style Node migration snippets without promoting the underlying
high-risk desktop providers.

## Scope

The catalog covers:

- Managed local ESM loader migration.
- `worker_threads` opt-in migration.
- Controlled HTTP/HTTPS client migration.
- Raw native Node network module opt-in migration.
- Scoped advanced filesystem migration.
- Controlled WASI profile migration.
- Debug inspector profile migration.
- Sanitized pure JavaScript npm dependency migration.

Each entry in `sample/nodejs/desktop-parity-suite/examples.json` declares:

- `targetProfile`
- `requiredCapabilities`
- `expectedOutput`
- `safeProfile.expectedOutput`
- `safeProfile.behavior`
- `packagedBehavior`
- `skipReason`

The default runner `main.cjs` prints the catalog-backed desktop markers and
the Safe-profile denial markers listed in `expected-output.txt`.
`snippets/safe-denials.cjs` centralizes the Safe-profile rejection examples.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-desktop-parity-examples.js --repo-root . --report-dir build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13DesktopParityExamples
```

Reports:

- `build/reports/nodejs/phase13-desktop-parity-examples.json`
- `build/reports/nodejs/phase13-desktop-parity-examples.md`

## Current Decision

Status is `partial`.

This gate proves the example catalog, target-profile metadata, Safe-profile
denial markers, documentation, and skip contract. It does not grant
`desktop_compat_opt_in` authority, does not add the desktop snippets to
default runtime smoke, and does not claim packaged desktop parity is ready.

Remaining blockers:

- Desktop profile metadata is still diagnostic-only.
- Default runtime smoke does not execute worker/network/WASI/inspector/package
  manager snippets.
- Packaged desktop parity needs provider promotion, integrity policy, and
  packaged APK evidence.
