# Node Profile v1.1 Beta Stabilization Plan

Target branch: `feature/nodejs-runtime-v1.1`

Node Profile v1.1 beta starts after the Phase 5 alpha capability expansion.
The goal is not to add another broad feature batch. The goal is to stabilize
the alpha surface, decide which features may stay `partial`, and keep
experimental or blocked features from leaking into a release candidate.

## Entry State

- Node Profile v1.0 is already GA locally.
- Node Profile v1.1 alpha gate exists and has passed on Sony XQ-AT72
  `QV710AF65F`.
- ESM, npm corpus, controlled network, WebSocket, bridge expansion, packaged
  v1.1 smoke, security corpus, and bridge resource stress have alpha coverage.
- Packaged `.mjs`, package `type=module`, and local dynamic import install-and-run
  smokes have a focused production-template gate.

## Beta Objectives

- Freeze v1.1 feature scope.
- Keep ESM and pure-JS npm compatibility as the primary promoted surface.
- Harden controlled network without promoting live network provider behavior.
- Keep bridge modules capability-gated and resource-managed.
- Convert alpha-only progress into repeatable beta gates and reports.
- Record all blockers before any release-candidate or tag workflow starts.

## Non-Objectives

- No native addon `.node` support.
- No unrestricted `child_process`.
- No raw `net`, `tls`, `dns`, `dgram`, `http`, or `https`.
- No unrestricted Java or Android object interop.
- No worker_threads promotion while the embedded build lacks runnable native
  worker support.
- No long-running service mode promotion until foreground lifecycle, stop
  controls, quotas, and packaged behavior are implemented.
- No UI or floaty live Android provider promotion before Activity/permission
  ownership is accepted.

## Stabilization Tracks

| Track | Beta target | Required evidence |
| --- | --- | --- |
| ESM and dynamic import | stay `partial` | default `.mjs`, `type=module`, static import, local dynamic import, builtin allowlist, resolver denial paths, packaged focused gate |
| npm/builtins | stay `partial` | real npm corpus, desktop differential report, Buffer/core/stream/zlib/Web Streams gates |
| controlled network | stay `experimental` | fake-provider fetch/axios/WebSocket coverage, raw network denial, manifest mapping, default Android provider denial |
| AutoJs bridge modules | stay `partial` or `protocol-gated partial` | capability denial, fake provider behavior, Android fail-closed behavior, TypeScript declarations, resource cleanup |
| packaged APK | first-class beta gate | packaged v1.1 bridge smoke, host aggregate verification, focused packaged ESM/dynamic import gate |
| security corpus | required | no scoped fs, native addon, raw process, raw network, plugin, UI, worker, or bridge bypass |
| DX/tooling | required | error catalog, project wizard, TypeScript declarations, doctor/compatibility reports |
| blocker register | required | [NODE_PROFILE_V1_1_BETA_BLOCKERS.md](NODE_PROFILE_V1_1_BETA_BLOCKERS.md), verified by `:app:verifyNodeV11BetaBlockerRegister` |

## Beta Gate

Run on the unattended Sony device:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11BetaGate "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

The gate writes:

```text
build/reports/nodejs/v1.1-beta-summary.json
```

The `v1_1_beta` device profile includes the alpha capability set plus lifecycle,
fs promises, native-addon denial, resolver fuzzer, and scoped-fs fuzzer
coverage. It keeps the default normal fuzzer budget; extended fuzzer budget runs
remain separate stress subgates. The beta aggregate also runs packaged host
verification and tooling checks through task dependencies.

The beta gate also verifies
[NODE_PROFILE_V1_1_BETA_BLOCKERS.md](NODE_PROFILE_V1_1_BETA_BLOCKERS.md) so
experimental, protocol-gated, blocked, and explicit-subgate surfaces have an RC
decision entry before release-candidate planning.

## Explicit Subgates

The packaged ESM/dynamic import smokes remain outside the default beta profile
for feedback-time reasons, but they now run as a focused production-template
gate:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodePackagedEsmGate "-Pautojs.nodejs.test.deviceProfile=sony" "-Pautojs.nodejs.identity=production" "-Pautojs.embeddedNode.expectedAbis=arm64-v8a"
```

A passing beta gate without this focused gate is a stabilization pass, not a
packaged ESM release decision. The focused gate validates the packaged runtime
compatibility descriptor version vector and may reuse unchanged APK fixtures by
normalized cache key; cached fixtures are still installed and launched on the
device.

## Exit Criteria

- `:app:verifyNodeV11BetaGate` passes on Sony with `v1_1_beta`.
- `:app:verifyNodePackagedEsmGate` passes on Sony with the production template.
- `build/reports/nodejs/v1.1-beta-summary.json` records a clean git state for
  the validated commit.
- No beta gate failures are classified as product bugs.
- Packaged host aggregate is ready.
- Error catalog, project wizard, and TypeScript declaration checks pass.
- All promoted v1.1 `partial` features have docs and security negative tests.
- All `experimental`, `protocol-gated`, and `blocked` features have explicit
  release notes or blocker entries.
- `:app:verifyNodeV11BetaBlockerRegister` passes.
- No release-forbidden experimental flag is needed for the default beta gate.

## RC Readiness Decision

After beta passes, a release-candidate plan may start only if:

- packaged ESM/dynamic import template status is accepted as default-ready,
- controlled network live-provider status remains clearly experimental or is
  separately promoted through a new design gate,
- long-running mode and worker_threads remain blocked in release docs, and
- no new broad feature expansion is added between beta and RC.

The RC readiness path is defined in
[NODE_PROFILE_V1_1_RC_PLAN.md](NODE_PROFILE_V1_1_RC_PLAN.md). Run:

```powershell
$env:ANDROID_SERIAL="QV710AF65F"
.\gradlew.bat --console=plain :app:verifyNodeV11RcReadiness "-Pautojs.nodejs.test.deviceProfile=v1_1_beta"
```

This task verifies the beta summary, packaged aggregate readiness, and blocker
decisions before production RC gate planning starts.
