# P13-54 Desktop Node Parity Examples

P13-54 records the v1.1 desktop Node parity example catalog as ready while
desktop runtime parity remains partial. The landing adds
`sample/nodejs/desktop-parity-suite` as a machine-verifiable catalog for
desktop-style Node migration snippets without promoting the underlying
high-risk desktop providers. It also includes a focused runtime smoke subset for
already-promoted managed local ESM, controlled HTTP/HTTPS facade shape,
checked-in pure JavaScript dependency, and scoped advanced filesystem behavior.

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

`smoke.cjs` is the focused runtime smoke subset. It executes only the managed
local ESM path represented by `snippets/esm-loader.cjs`, the controlled
HTTP/HTTPS facade shape represented by `snippets/http-https.cjs`, the
checked-in `vendor/pure-math` dependency path represented by
`snippets/npm-dependency.cjs`, and the scoped FileHandle plus non-persistent
watcher path represented by `snippets/fs-advanced.cjs`. `smoke-output.txt`
records the expected output. The HTTP/HTTPS smoke checks facade shape only and
does not dispatch live network I/O. Worker, raw network, expanded
filesystem-root, WASI, inspector, and package-manager authority examples remain
static until their providers and policy gates are promoted.

Each example remains linked to profile metadata, capability requirements,
integrity policy, and rollback guidance through `examples.json`, `project.json`,
README packaged-behavior rows, and Safe-profile denial markers. Non-promoted
snippets stay static/catalog-backed and clearly marked outside the Safe smoke
subset.

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

Status is `ready` for the current v1.1 example catalog gate.

This gate proves the example catalog, target-profile metadata, Safe-profile
denial markers, documentation, skip contract, and the focused runtime smoke
subset for already-promoted desktop-safe examples. It does not grant
`desktop_compat_opt_in` authority, does not add high-risk desktop snippets to
default runtime smoke, and does not claim packaged desktop parity is ready.

No current v1.1 example-catalog blockers remain.

Future promotion work:

- Packaged desktop parity examples need provider promotion, integrity policy,
  packaged APK evidence, and device evidence for promoted desktop profile
  examples.
