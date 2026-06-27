# P13-53 Pro Parity Examples

P13-53 records Pro parity examples as partial. The landing adds
`sample/nodejs/pro-parity-suite` as a machine-verifiable catalog for Auto.js
Pro-style migration snippets without promoting the underlying high-risk
providers. It also includes a focused runtime smoke subset for the already
covered Activity-owned UI layout and WorkManager one-shot schedule/cancel
paths.

## Scope

The catalog covers:

- `$autojs.java` / `require("java")` allowlist interop.
- `rhino.install({ experimental: true })` Java proxy globals.
- Activity-owned UI layout.
- Floaty-style overlay permission and settings flow.
- WorkManager one-shot task migration.
- Screenshot plus OCR migration.
- Accessibility selector migration.

Each entry in `sample/nodejs/pro-parity-suite/examples.json` declares:

- `requiredCapabilities`
- `targetProfile`
- `expectedOutput`
- `packagedBehavior`
- `skipReason`

The default runner `main.cjs` prints the catalog-backed expected markers listed
in `expected-output.txt`. The runnable snippets under `snippets/` keep guarded
try/catch paths and print explicit skip markers when a provider, permission, or
profile is unavailable.

The static catalog remains available for migration before runtime promotion:
`main.cjs` is intentionally a catalog runner, and the root example manifest
keeps `pro-parity-suite` out of the default app smoke path. High-risk snippets
stay tied to `pro_compat_opt_in` metadata and remain outside the focused runtime
smoke subset unless the relevant provider/profile gates are explicitly opened.

Prompt/disclosure UI references and denial paths remain metadata-only. The
catalog links packaged-behavior rows to future disclosure, permission, service,
and foreground-ownership review while guarded snippet skip paths keep provider,
permission, and profile denials explicit.

`smoke.cjs` is the focused runtime smoke subset. It executes only the
Activity-owned JSON UI path represented by `snippets/ui-layout.cjs` and the
WorkManager one-shot schedule/cancel path represented by
`snippets/tasks-work-manager.cjs`. `smoke-output.txt` records the expected
output. Java interop, Rhino global install, overlay, screenshot/OCR, and
accessibility authority examples remain static until their providers and
policy gates are promoted.

## Gate

Host verifier:

```powershell
node tools/nodejs/phase13/verify-pro-parity-examples.js --repo-root . --report-dir build\reports\nodejs
```

Gradle gate:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePhase13ProParityExamples
```

Reports:

- `build/reports/nodejs/phase13-pro-parity-examples.json`
- `build/reports/nodejs/phase13-pro-parity-examples.md`

## Current Decision

Status is `partial`.

This gate proves the example catalog, metadata, documentation, skip contract,
and the focused runtime smoke subset for already-covered UI and WorkManager
paths. It does not grant `pro_compat_opt_in` authority, does not add high-risk
snippets to default runtime smoke, and does not claim packaged Pro execution is
ready.

Remaining blocker:

- Live packaged Pro examples need reviewed prompt/disclosure UI, provider
  promotion, packaged APK evidence, device matrix coverage, and
  permission-state smoke.
