# Node Profile v1.1 Release Candidate Plan

Target branch: `feature/nodejs-runtime-v1.1`

Status: RC route opened; no release-candidate tag or publication approval yet.

Node Profile v1.1 RC work starts from the beta stabilization evidence. The goal
is to freeze the v1.1 surface, keep deferred features out of release notes as
promoted behavior, and run the production release gate only after the beta
summary and blocker decisions are machine-verifiable.

## Entry Evidence

- Node Profile v1.1 beta gate has passed on Sony XQ-AT72 `QV710AF65F`.
- `build/reports/nodejs/v1.1-beta-summary.json` records a clean git state for
  the validated commit.
- `app/build/reports/nodejs/v1.1-beta-blockers.json` reports all required beta
  blockers.
- All beta blocker entries are `resolved`, `deferred`, or `closed`.
- Packaged host aggregate reports success, diagnostics, template, and
  moduleSources readiness.
- `:app:verifyNodePackagedEsmGate` passes on Sony against the production
  packaged template.
- No release-forbidden experimental flag is required by the default beta gate.

## RC Surface

The RC surface is the v1.1 beta surface after blocker decisions:

- ESM and dynamic import remain `partial`.
- Pure-JS npm compatibility, Buffer/core builtins, streams, zlib streams, Web
  Streams, source maps, and package resolution remain the promoted v1.1
  compatibility surface.
- AutoJs bridge modules remain capability-gated and resource-managed.
- Controlled fetch, Axios, and WebSocket remain `experimental` without a default
  live Android provider.
- UI remains protocol-gated partial with Android provider fail-closed.
- Work Manager remains metadata registration partial only.

## Deferred Surfaces

The following surfaces are intentionally outside the v1.1 RC promoted surface:

- controlled network and WebSocket live providers,
- Activity-owned UI provider,
- floaty/overlay,
- long-running and service execution modes,
- runnable `worker_threads`,
- Java/Android interop,
- general plugin runtime, installers, signatures, and native plugins,
- scheduled Work Manager execution.

## Readiness Gate

Run the readiness gate after a passing beta gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11RcReadiness "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

The gate writes:

```text
app/build/reports/nodejs/v1.1-rc-readiness.json
```

It verifies:

- RC plan and report docs exist,
- beta summary schema and device profile are correct,
- beta summary records a clean git state,
- latest beta connected-test XML has tests and zero failures, errors, or
  skips,
- required beta reports are available,
- packaged smoke aggregate readiness is true,
- beta blocker report is ready, and
- all beta blocker statuses are `deferred` or `closed`.

## Production RC Gate

The readiness gate does not replace the production release gate. Before an RC
tag or publication decision, run the production identity release gate on Sony:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeReleaseGate "-Pautojs.nodejs.identity=production" "-Pautojs.nodejs.test.deviceProfile=sony"
```

If the gate is run in a multi-device environment, keep `ANDROID_SERIAL` pointed
at Sony `QV710AF65F`. Do not use OnePlus OPD2413 for unattended RC gates.

## Exit Criteria

- `:app:verifyNodeV11RcReadiness` passes.
- `:app:verifyNodeReleaseGate` passes on Sony with production identity.
- Release notes state every deferred surface accurately.
- Compatibility profile, user docs, and security docs agree on the v1.1 status
  labels.
- No new broad feature expansion is added between RC readiness and RC tag.
- `version.properties` changes are included only when the release process
  intentionally bumps release metadata.

## RC Decision

v1.1 may enter release-candidate packaging only after the readiness gate is
green and the production release gate is rerun for the exact commit being
considered. A green readiness gate is evidence that RC planning may start; it is
not evidence that a tag should be published.
