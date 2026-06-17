# AutoJs6 Node Profile v1 Maintenance Plan

This plan starts after Node Profile v1 GA. It keeps the Safe Node Profile publishable without expanding the default
authority surface by accident.

## Operational Runbooks

- [POST_GA_SMOKE.md](POST_GA_SMOKE.md) defines daily, weekly, nightly, and monthly smoke cadence.
- [CRASH_INTAKE.md](CRASH_INTAKE.md) defines required crash attachments, labels, severity, and closure criteria.
- [releases/V1_0_X_HOTFIX_POLICY.md](releases/V1_0_X_HOTFIX_POLICY.md) defines patch-release scope and minimum gates.
- [tools/nodejs/bisect-node-smoke.ps1](../../tools/nodejs/bisect-node-smoke.ps1) provides a small git-bisect-compatible
  Node smoke helper.
- [.github/ISSUE_TEMPLATE/nodejs_bug_report.yml](../../.github/ISSUE_TEMPLATE/nodejs_bug_report.yml) is the user-facing
  Node bug report template.

## Maintenance Invariants

- Rhino fallback remains available.
- `rhino`, `native`, `embedded`, and `auto` backend switches remain available.
- Native one-shot remains a regression oracle.
- Scoped fs policy is not loosened in patch releases.
- Native addons, unrestricted `child_process`, raw network, inspector, unrestricted Java/Android interop, unrestricted
  `worker_threads`, and unrestricted `vm` remain default-off or disabled unless a documented promotion gate passes.
- Packaged APK support remains a first-class release target.

## Weekly Smoke

Run once per active development week, and before any Node-related patch branch is handed to QA:

```powershell
.\gradlew.bat --console=plain :app:verifyNodePrGate
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:connectedAppDebugAndroidTest "-Pautojs.nodejs.test.deviceProfile=smoke" "-Pautojs.nodejs.fuzzerBudget=quick"
```

Record failures with the failing task, class, method, device serial, package identity, and whether the failure is
reproducible. Do not retry security, scoped fs, packaged script, bridge capability, or leak assertions to make a smoke
pass.

See [POST_GA_SMOKE.md](POST_GA_SMOKE.md) for the full post-GA cadence.

## Nightly Extended Gate

Run on prepared Sony XQ-AT72 `QV710AF65F` for active Node development and before release-candidate branches:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeNightlyGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=extended"
.\gradlew.bat --console=plain :app:generateNodeNightlyReport "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.fuzzerBudget=extended"
```

Attach `build/reports/nodejs/nightly-report.md`, fuzzer seeds, packaged smoke reports, memory reports, and crash excerpts
to the nightly record. If the final tag commit differs from the latest green nightly or GA candidate commit, rerun
`:app:verifyNodeReleaseGate`.

## Monthly Dependency And Bundler Audit

Run monthly when Node bundling or compatibility corpus work is active:

```powershell
.\gradlew.bat --console=plain :app:verifyNodeDependencyBundlerFixtures :app:generateDesktopNodeDifferentialReport
```

Review:

- vendored package license notes;
- native addon and `node-gyp` rejection coverage;
- lifecycle script, postinstall, `.bin`, executable, and npm cache rejection coverage;
- dependency size limits and SBOM-lite output;
- fixed real npm corpus drift against desktop Node.

No runtime `npm install`, lifecycle script execution, unchecked `.bin`, or native build step is allowed in v1.x.

## Quarterly Libnode Upgrade Review

Review the embedded Node version quarterly, or sooner for security advisories:

1. Compare the candidate Node version against current Android ABI support.
2. Rebuild native libraries and regenerate native dependency, native symbol, ABI size, and release manifest reports.
3. Refresh packaged template APK only through the documented reproducibility flow.
4. Run desktop differential, full Sony release gate, memory stress, lifecycle stress, packaged smoke, and native crash
   symbol drill.
5. Update `COMPATIBILITY_PROFILE.md`, `NODE_PROFILE_V1_STABLE_SURFACE.md`, known issues, and migration notes when behavior
   changes.

Patch releases may upgrade libnode only when the compatibility, size, crash, and packaged evidence is attached.

## Experimental Promotion Reviews

Use [EXPERIMENTAL_PROMOTION_POLICY.md](EXPERIMENTAL_PROMOTION_POLICY.md) as the source of truth. Reviews must not promote
an experimental feature by documentation change alone.

ESM review:

- revisit `.mjs`, package `type`, CJS/ESM interop, top-level await, `import.meta`, source maps, dynamic import, packaged
  APK smoke, and scoped file URL denial;
- keep dynamic import local-only after the v1.1 gate, and keep ESM partial
  support under focused regression and packaged smoke coverage. Broader URL,
  loader-hook, and package-graph dynamic import support still needs a future
  gate.

Worker review:

- verify native `worker_threads` availability before advertising runnable support;
- require worker timeout, termination, resource limit, memory/thread stress, and bridge/raw-network denial evidence;
- keep workers outside the default GA profile unless the experimental flag is explicitly enabled.

Network and WebSocket review:

- prove raw `http`, `https`, `net`, `tls`, `dns`, and `dgram` remain denied;
- require Android `INTERNET` manifest checks, timeout/abort/size limits, privacy review, and live-provider smoke on an
  approved device profile;
- keep live providers default-off until approved.

Java interop review:

- audit every allowed class and method;
- deny reflection, class loaders, process execution, native library loading, raw `Context`, and object graph escape;
- require opaque-handle lifecycle tests and security signoff.

## Crash Report Triage

For every Node crash, collect:

- app version, profile flavor, backend, package identity, device model, API level, ABI, and serial;
- script entry mode and whether it is packaged, without collecting user script contents;
- Node Doctor JSON/text report;
- release manifest, native symbol report, ABI size report, and packaged diagnostic report when applicable;
- logcat crash buffer, tombstone or bugreport excerpt, and reproduction command.

Classify the issue:

- P0: security boundary bypass, data exposure, or reproducible release build crash on normal stable APIs;
- P1: packaged APK launch crash, lifecycle crash, memory runaway, or bridge resource leak;
- P2: compatibility regression with stable CommonJS, scoped fs, or documented bridge APIs;
- P3: experimental feature failure, diagnostic polish, or device-specific infrastructure issue.

P0/P1 fixes require a focused regression test, release note, and backport decision. Native crashes require symbolication
against the matching `nativeNodeSymbolArchiveReport` output before closure.

See [CRASH_INTAKE.md](CRASH_INTAKE.md) for the full intake workflow and issue-template fields.

## Versioning Policy

Node Profile v1.x patch releases may include:

- bug fixes for stable behavior;
- stricter diagnostics and stable error-code coverage;
- scoped fs, resolver, packaged APK, bridge, and lifecycle hardening that does not remove documented stable behavior;
- new optional bridge fields or docs when older scripts continue to work;
- experimental feature fixes that remain behind their existing flags.

See [releases/V1_0_X_HOTFIX_POLICY.md](releases/V1_0_X_HOTFIX_POLICY.md) for the v1.0.x hotfix boundary and minimum gate.

Node Profile v2 is required for:

- default-enabling high-risk capabilities;
- breaking stable bridge API shapes;
- relaxing scoped fs or package containment policy;
- removing backend switches, Rhino fallback, or native one-shot diagnostics;
- changing stable error-code meaning;
- changing packaged APK manifest requirements in a way that breaks existing v1 packaged projects without a compatibility
  path.

Breaking changes require a migration document, known-issues update, release-note entry, and at least one release cycle of
warning unless the change fixes an active security boundary bypass.

## Deprecation Policy

Experimental feature removal:

- experimental APIs may be removed or redesigned in v1.x when they are unsafe, unavailable on supported devices, or fail
  resource gates;
- removal must be documented in release notes and known issues;
- stable APIs must not depend on experimental APIs.

Error-code stability:

- `ERR_AUTOJS6_*` codes used in stable or documented denial paths are not reused for a different meaning;
- new denials get new codes or documented aliases;
- deprecated codes can remain as aliases when practical.

Bridge API compatibility:

- stable bridge modules keep Promise-returning method shapes;
- adding optional fields is allowed, removing or renaming stable fields is not a v1.x patch change;
- provider-unavailable and capability-denied errors remain stable and actionable;
- packaged bridge metadata changes must preserve older packaged templates or fail with a precise diagnostic.

## Evidence Retention

Keep release and nightly evidence under `docs/nodejs/reports/` when it explains a release decision. Large transient build
outputs can stay under `build/` or `app/build/`, but the committed report must identify the command, commit, device,
result, skipped gates, and artifact paths.
