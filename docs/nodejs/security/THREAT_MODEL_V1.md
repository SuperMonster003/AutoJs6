# AutoJs6 Node Profile v1 Threat Model

Status: Node Profile v1.2 GA baseline.

Scope: the external runtime plugin, self-contained Embedded Node flavors, and
packaged APKs. Node scripts and vendored npm packages are untrusted even when a
runtime capability is stable.

## Stable Capability Baseline

ESM, dynamic import, controlled network, WebSocket, native network builtins,
`worker_threads`, `child_process`, and allowlisted Java interop are enabled in
every Node build. They have no capability-specific Gradle properties,
release-only opt-ins, or project/package overrides.

Stable availability does not mean unrestricted authority:

- controlled bridge calls still require declared permissions when manifest
  enforcement is active;
- Android manifest permissions and application sandboxing still apply;
- project paths remain scoped;
- provider inputs, outputs, timeouts, concurrency, and resources remain bounded;
- Java access remains an exact allowlist with opaque handles;
- workers receive less authority than their parent;
- native network and process APIs require caller-managed limits and cleanup;
- Inspector is disabled, while native addons and raw WASI are unsupported.

## Security Goals

- Preserve Rhino routing and Android permission behavior.
- Keep privileged Android/AutoJs objects behind versioned providers.
- Prevent resolver, file URL, package target, symlink, and source-map escapes.
- Prevent runtime plugin impersonation, replay after dispatch, and cross-UID
  workspace overreach.
- Keep stable capabilities visible and testable in Debug, Release, packaged
  runtime, Doctor, and compatibility reports.
- Make policy denial diagnostic through stable `ERR_AUTOJS6_*` codes.
- Close processes, workers, sockets, bridge calls, files, and Android handles on
  success, failure, timeout, cancellation, process death, and service destroy.

## Trust Boundaries

| Boundary | Required controls |
| --- | --- |
| Host to external runtime plugin | signature permission, enabled/authorized state, signer/UID/component identity, contract/capability/ABI negotiation, Binder generation, no replay after possible dispatch |
| Host workspace to plugin-private workspace | explicit-input archive v2, owned PFDs, no host-directory scan, path/count/byte budgets, symlink/special-file denial, TOCTOU checks, final tombstone allowlist |
| Main app to `:nodejs_embedded` | non-exported service, process lifecycle ownership, bounded requests/results, stop/destroy cleanup |
| Bridge provider to JavaScript | strict JSON contract, capability manifest, provider registry, execution ownership, timeout, at-most-once completion |
| Project filesystem | lexical and canonical scope checks, realpath/symlink checks, owned descriptors, cleanup |
| APK builder to generated APK | entry/module graph validation, manifest permission mapping, ABI policy, native/runtime-kit hash verification |

## Surface Matrix

### Resolver And ESM

Risk: package `exports`/`imports`, CommonJS, ESM, dynamic import, file URLs, or
source maps escape the project, shadow reserved builtins, or load a native
addon.

Controls: AutoJs6 owns scoped resolution and package-target validation; builtin
precedence wins over `node_modules`; remote module URLs and native `.node`
targets reject; encrypted/on-demand sources use versioned, bounded provider
contracts.

Evidence: ESM stable instrumentation, resolver/fs fuzzers, npm corpus,
source-map tests, encrypted graph tests, and packaged ESM smoke.

### Scoped Filesystem

Risk: absolute paths, parent traversal, NUL, `/proc`, `/sys`, `/dev`,
symlinks, realpath changes, or descriptor lifetime expose files outside the
execution root.

Controls: validate before and after canonicalization/open, deny unsafe roots,
own descriptors by execution, limit source/file counts and bytes, and close
watchers/streams/descriptors on every terminal path.

Evidence: `NodeSecurityRegressionInstrumentationTest`, scoped-fs fuzzer,
`fs/promises`, stream/watch tests, and packaged diagnostics.

### Runtime Plugin And Cross-UID Workspace

Risk: a replaced service impersonates the runtime; Binder loss causes replay;
archive transport discloses unrequested host files, traverses paths, races file
identity, publishes partial output, or deletes unrelated files.

Controls: pin component/version/update-time/UID/signer and negotiated contract;
invalidate caches on package/host/trust/Binder changes; allow fallback only
before dispatch; never replay after possible side effects; enumerate explicit
inputs only; extract privately; verify file identity; commit outputs and
tombstones atomically after complete validation.

Evidence: `NodeRuntimePluginR5InstrumentationTest` on all supported ABIs.

### Controlled Bridge RPC

Risk: malformed payloads, missing capabilities, duplicate callbacks, unbounded
pending work, stale handles, or service destruction leak privilege or hang Node.

Controls: strict request/response schemas, pre-dispatch capability audit,
execution-owned resources, hard limits, at-most-once completion, cancellation,
destroy cleanup, and fake/live provider parity tests.

Evidence: bridge conformance, RPC fuzzer, permission-manifest, concurrency,
resource-registry, and runtime-budget tests.

### Controlled Network And WebSocket

Risk: unauthorized egress, unsafe schemes, secret-bearing diagnostics,
unbounded redirects/bodies/messages/connections, TLS mistakes, or callbacks
after destroy.

Controls: declared `network` permission when enforced, Android `INTERNET`
manifest policy, allowed schemes, timeout/abort/cancel, redirect and size
limits, connection/queue limits, execution ownership, redaction, and provider
destroy cleanup.

Evidence: fetch/WebSocket fake-provider suites, live on-device loopback gates,
packaged manifest verifier, and long-running resource stress.

### Native Network Builtins

Risk: `dns`, `http`, `https`, `net`, or `tls` bypass controlled-provider
budgets, open servers, retain sockets, or disclose topology.

Controls: Android UID/SELinux/manifest sandbox; builtin shadow protection;
script-owned timeout, buffering, agent/server/socket limits, TLS policy, and
shutdown cleanup; no implicit grant of unrelated bridge capabilities.

Evidence: raw network policy/conformance tests for bare and `node:` imports,
client/server lifecycle, DNS/TLS behavior, packaged manifest, and descriptor
cleanup. `dgram` remains denied.

### Worker Threads

Risk: workers inherit parent privilege, escape source scope, spawn nested
workers, exhaust memory/threads/queues, leak ports, or survive parent shutdown.

Controls: native availability on every published ABI; parent-scoped source
resolution; one-worker default; bounded messages and queues; transfer
validation; no AutoJs bridge, Java, shell, raw network, Inspector, addon, or
nested-worker authority; parent-owned termination and port/pool cleanup.

Evidence: stable worker, bootstrap, MessageChannel/transfer, WorkerPool,
packaged worker, lifecycle, and memory/thread/resource stress tests.

### Child Processes

Risk: untrusted executable paths/arguments, shell injection, unbounded stdio,
orphaned children, signals, and detached processes.

Controls: Android application UID/SELinux/filesystem sandbox; builtin shadow
protection; script validation of executable/arguments; caller-managed timeout,
stdio budgets, cancellation, kill, and reap behavior; explicit separation from
the privileged AutoJs `shell` bridge.

Evidence: native resolution/spawn/exec/stdio/exit/kill/cleanup tests across
supported ABIs and packaged execution. Availability does not grant root or
Shizuku.

### Java Interop

Risk: reflection, class loading, process execution, native loading, raw Android
objects, or stale/cross-execution handles bypass bridge policy.

Controls: exact class/member allowlist; JSON values and execution-owned opaque
handles; deny `Runtime`, `ProcessBuilder`, `ClassLoader`, reflection,
native-library loading, and raw Context/UI/Binder/service objects; idempotent
cleanup; project/package metadata cannot expand the allowlist.

Evidence: `NodeJavaInteropBridgeInstrumentationTest`, Rhino explicit proxy
tests, security corpus, handle cleanup, and denial coverage.

### Dependency Bundling And Native Code

Risk: lifecycle scripts, unchecked binaries, native addons, node-gyp output,
oversized trees, symlinks, or downloaded executable payloads enter an APK.

Controls: host-only sanitizing bundler, no runtime install, lifecycle denial,
path/type/size limits, dependency report and hashes, native addon denial,
packaging preflight, ABI/runtime-kit lock verification.

Evidence: dependency bundler fixtures, native addon policy, package integrity,
and packaged APK verifier.

### Packaged Manifest And Downgrade

Risk: missing runtime service/native payload/permission, stale capability
metadata, unsupported ABI, or downgrade leaves persistent authority alive.

Controls: service/process/FGS verification, ABI allowlist and x86 fail-fast,
runtime-kit and catalog hashes, capability-to-Android-permission mapping,
module-source budgets, metadata consistency, fail-closed downgrade, and
resource cleanup.

Evidence: packaged smoke, output verifier, diagnostic report, template
reproducibility, and security regression corpus.

## Review Rules

- Adding or broadening a stable capability requires a threat-model update,
  positive tests, negative/security tests, packaged evidence, lifecycle/resource
  evidence where applicable, and
  [../CAPABILITY_STABILITY_POLICY.md](../CAPABILITY_STABILITY_POLICY.md) review.
- A new non-stable surface must use `partial`, `disabled`, `unsupported`, or
  `planned`; it must not introduce an undocumented capability property.
- Resolver, filesystem, bridge, bundler, runtime plugin, or packaged manifest
  changes require both supported-path and abuse-path evidence.
- Fuzzer failures must save reproducible seed JSON.
- Release candidates attach Doctor, release manifest, compatibility profile,
  runtime artifact locks, packaged diagnostics, and relevant device reports.

## Open Risks

- Full desktop Node compatibility remains out of scope.
- Native network and process APIs rely on script authors for application-level
  limits beyond the Android sandbox.
- Android platform/permission/foreground-service behavior can change and must be
  revalidated for target SDK updates.
- Some unrelated AutoJs bridge providers remain partial or unavailable on
  specific devices.
- Inspector, native addons, raw WASI, UDP/`dgram`, and unrestricted Java remain
  intentionally unavailable.
