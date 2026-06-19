# Node Profile v1.1 Beta Blockers

Target branch: `feature/nodejs-runtime-v1.1`

This register records the v1.1 beta surfaces that must be explicitly accepted,
deferred, or resolved before release-candidate planning starts. It is scoped to
features that are `experimental`, `protocol-gated`, `blocked`, or outside the
default beta gate even though they affect a promoted v1.1 capability.

Default policy remains unchanged: no native addons, unrestricted child process,
raw network modules, unrestricted Java/Android interop, loader hooks, inspector,
or scoped-fs relaxation.

## V11-BETA-PACKAGED-ESM-TEMPLATE

Status: resolved

RC decision: proceed; the refreshed production inrt template is the default
packaged template for this focused gate.

Surface: packaged `.mjs` entry and local dynamic import install-and-run smokes.

Current state: packaged `.mjs`, package `type=module`, local dynamic import,
source-name stack, disabled builtin denial, scoped escape denial, and runtime
compatibility descriptor version-vector mismatch are covered by the focused
production-template gate.

Release impact: v1.1 may document packaged ESM and local dynamic import as
partial capabilities guarded by `:app:verifyNodePackagedEsmGate`.

Unblock condition:

- refreshed inrt template is default and packaged ESM/dynamic import focused
  gate passes without a packaged smoke template override.

Validation:

- `:app:verifyNodePackagedEsmGate`
- `EmbeddedNodePackagedApkSmokeTest#installAndRunExperimentalEsmEmbeddedNodePackagedApk`
- `EmbeddedNodePackagedApkSmokeTest#installAndRunDynamicImportLocalEmbeddedNodePackagedApk`
- `EmbeddedNodePackagedApkSmokeTest#installAndRunRuntimeCompatibilityDescriptorMismatchEmbeddedNodePackagedApk`

## V11-BETA-NETWORK-LIVE-PROVIDER

Status: deferred

RC decision: keep controlled `fetch` and `axios` experimental unless a separate
network live-provider promotion gate passes.

Surface: controlled HTTP requests through `fetch` and the AutoJs6-owned
`axios` adapter.

Current state: fake-provider coverage, raw-network denial, capability manifest
mapping, response budgets, timeout, abort, and default Android provider denial
are covered. Live Android provider behavior remains default-off.

Release impact: no v1.1 RC may present controlled HTTP as stable or partial
live Android network support by default.

Unblock condition:

- live-provider gate runs with `autojs.nodejs.network.experimental=true`,
  bounded external request policy is accepted, packaged INTERNET permission
  behavior is verified, and `V1_1_NETWORK_PROMOTION.md` is updated.

Validation:

- `NodeFetchBridgeInstrumentationTest`
- `NodeBridgePermissionManifestInstrumentationTest`
- `docs/nodejs/releases/V1_1_NETWORK_PROMOTION.md`

## V11-BETA-WEBSOCKET-LIVE-PROVIDER

Status: deferred

RC decision: keep WebSocket experimental unless a separate WebSocket
live-provider promotion gate passes.

Surface: controlled `require("websocket")` bridge.

Current state: fake-provider connect/send/receive/close, event shape, resource
limits, service-destroy cleanup, and raw socket denial are covered. Live Android
provider behavior remains default-off.

Release impact: no v1.1 RC may imply raw socket, HTTP upgrade, or default live
WebSocket authority.

Unblock condition:

- live provider gate verifies bounded connect/send/receive/close behavior,
  packaged INTERNET permission behavior, cleanup, and failure modes under
  `autojs.nodejs.network.experimental=true`.

Validation:

- `NodeWebSocketBridgeInstrumentationTest`
- `NodeBridgeResourceLeakStressInstrumentationTest`
- `docs/nodejs/releases/V1_1_NETWORK_PROMOTION.md`

## V11-BETA-UI-LIVE-PROVIDER

Status: deferred

RC decision: keep UI as protocol-gated partial with fail-closed Android provider.

Surface: `require("ui")` JSON layout protocol.

Current state: fake-provider layout, event drain, update, close, descriptor
denial, shadowing protection, TypeScript declarations, and raw View/Activity
denial are covered. Activity-owned live provider ownership is not accepted.

Release impact: v1.1 docs must say normal scripts should handle provider
denial and must not rely on raw Android `View`, `Activity`, or `Context`
objects.

Unblock condition:

- Activity-owned provider design is accepted, lifecycle/permission behavior is
  implemented, live provider device smoke passes, and packaged behavior is
  explicitly decided.

Validation:

- `NodeUiBridgeInstrumentationTest`
- `docs/nodejs/design/UI_BRIDGE.md`

## V11-BETA-FLOATY-OVERLAY

Status: deferred

RC decision: keep floaty/overlay blocked for v1.1.

Surface: floating window / overlay support for Node.

Current state: design gate exists; no default runtime support is enabled.

Release impact: v1.1 RC must not expose overlay permission prompts, raw
WindowManager handles, global input capture, or packaged overlay behavior.

Unblock condition:

- overlay permission, foreground disclosure, lifecycle, touch events, packaged
  manifest verification, and service-stop cleanup are implemented and covered.

Validation:

- `docs/nodejs/design/FLOATY_NODE.md`
- `docs/nodejs/design/UI_BRIDGE.md`

## V11-BETA-LONG-RUNNING

Status: deferred

RC decision: keep long-running/service execution blocked for v1.1.

Surface: declared long-running Node scripts and service execution mode.

Current state: request protocol carries `executionMode` through the Phase 7
state machine, but only `one_shot` is executable. `interactive_long_running`,
legacy `long_running`/`service` aliases, `scheduled`, and `worker_computation`
requests reject before user code runs with policy metadata.

Release impact: v1.1 RC must keep normal scripts timeout-bounded and must not
infer service mode from timers, sockets, bridge resources, or `timeoutMs=0`.

Unblock condition:

- async service lifecycle, foreground notification, user stop, quotas,
  packaged behavior, restart policy, and lifecycle stress gates are implemented.

Validation:

- `docs/nodejs/design/LONG_RUNNING_SERVICE.md`
- `docs/nodejs/releases/NODE_PROFILE_V1_1_CAPABILITY_MATRIX.md`

## V11-BETA-WORKER-THREADS

Status: deferred

RC decision: keep `worker_threads` blocked and default-off.

Surface: Node `worker_threads`.

Current state: design gate and default-off denial are covered. The current
Embedded Node build still lacks runnable native `worker_threads` support even
when the experimental flag is enabled.

Release impact: v1.1 RC must not advertise runnable worker support or provide a
same-thread fallback.

Unblock condition:

- native worker module is available, pure-JS worker tests pass, worker-local
  bridge/network/fs restrictions pass, resource stress passes, and packaged
  source graph behavior is accepted.

Validation:

- `NodeWorkerThreadsDesignGateInstrumentationTest`
- `docs/nodejs/releases/V1_1_WORKER_POLICY.md`

## V11-BETA-JAVA-INTEROP

Status: deferred

RC decision: keep Java/Android interop experimental and default-off.

Surface: `require("java")` allowlist bridge.

Current state: focused tests cover allowlisted experimental behavior and denial
paths, but normal builds keep `autojs.nodejs.javaInterop.experimental=false`.

Release impact: v1.1 RC must not expose unrestricted Android objects,
reflection, class loaders, raw `Context`, raw process execution, filesystem,
network, or native library loading through Java interop.

Unblock condition:

- security signoff, release-safe allowlist, packaged policy, docs, and
  negative tests are accepted for a future release.

Validation:

- `NodeJavaInteropBridgeInstrumentationTest`
- `docs/nodejs/reports/JAVA_INTEROP_EVALUATION.md`

## V11-BETA-PLUGIN-RUNTIME

Status: deferred

RC decision: keep plugin support as JS-only local prototype.

Surface: `require("plugins")`.

Current state: project-local CommonJS plugins load from `./plugins/<id>` only
with exact `plugin.<id>` opt-in. Already-packaged project-local JS-only plugin
files are covered by the packaged APK smoke with explicit bridge capabilities.
Native `.node`, installers, signatures, Android plugin adapters, archives,
updates, and marketplace/runtime distribution are not enabled.

Release impact: v1.1 RC must not imply a general plugin marketplace or native
plugin runtime.

Unblock condition:

- installer/signature/update model, packaged distribution behavior beyond
  project-local assets, Android adapter policy, and native plugin
  denial/allowlist policy are designed and gated.

Validation:

- `NodePluginSystemInstrumentationTest`
- `docs/nodejs/design/PLUGIN_SYSTEM.md`

## V11-BETA-WORK-MANAGER-EXECUTION

Status: deferred

RC decision: keep `work_manager` at metadata registration partial support.

Surface: scheduled Node task execution.

Current state: `require("work_manager")` records schedule/cancel/list metadata
with scoped entry descriptors. It does not launch persistent background Node
executions.

Release impact: v1.1 RC must not advertise scheduled execution, boot receivers,
periodic background launches, or raw Android scheduler authority.

Unblock condition:

- scheduled execution lifecycle, persistence, packaged receivers/permissions,
  foreground-service policy, and user stop/cancel behavior are implemented and
  gated.

Validation:

- `NodeWorkManagerBridgeInstrumentationTest`
- `EmbeddedNodePackagedApkSmokeTest#installAndRunV11BridgeEmbeddedNodePackagedApk`
- `docs/nodejs/design/WORK_MANAGER.md`
