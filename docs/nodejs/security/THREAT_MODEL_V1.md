# AutoJs6 Node Profile v1 Threat Model

Status: v1 GA hardening baseline.

Scope: AutoJs6 Safe Node Profile running through the default external runtime
plugin, self-contained Embedded Node flavors, and packaged APKs. This model
covers runtime delivery, CommonJS/npm, scoped filesystem and cross-UID
workspace transport, bridge RPC, feature gates, dependency bundling, and
packaged manifest patching. It does not promote any experimental capability to
stable.

## Security Goals

- Preserve Rhino routing and existing Android permission behavior.
- Treat Node scripts and vendored npm packages as untrusted code.
- Keep privileged Android and AutoJs objects behind capability providers.
- Keep filesystem access scoped to the script/project working directory.
- Deny high-risk Node desktop authority by default.
- Make denials diagnostic and reproducible through stable `ERR_AUTOJS6_*` codes.
- Keep packaged APK behavior equivalent to the main app profile unless a package explicitly opts into a narrower or experimental policy.

## Trust Boundaries

| Boundary | Trusted side | Untrusted side | Required control |
| --- | --- | --- | --- |
| Main app to external runtime service | Authorized AutoJs6 host and broker | Runtime package identity, Binder/process lifecycle, Node/V8/libuv execution | signature permission, enable/authorization/trust state, signer/UID/component identity, contract/capability/ABI negotiation, Binder death generation, no replay after dispatch |
| Host workspace to plugin-private workspace | Host-scoped working directory and explicit request inputs | Cross-UID archive contents, plugin output, deletion requests | PFD ownership, zero host sandbox scan, path and size budgets, symlink/special-file denial, TOCTOU identity checks, verified final output and tombstone commit |
| Main app to `:nodejs_embedded` | AutoJs6 app/services | Node/V8/libuv execution | process isolation, lifecycle stop/destroy, stable result channel |
| Bridge provider to JavaScript | Capability providers | JS bridge client and npm code | JSON RPC validation, capability manifest, resource registry, timeout |
| Filesystem root | Working directory/project root | Script paths, package targets, file URLs | lexical and canonical scoped checks |
| Packaged APK host to generated APK | AutoJs6 builder/verifier | project assets and moduleSources | manifest/native-lib verification, ABI pruning, module budget |
| Feature flags | Build-time policy | project/package metadata | deny by default, no project override for critical gates |

## Protected Assets

| Asset | Risk | Required mitigation | Test coverage |
| --- | --- | --- | --- |
| Accessibility service | Silent UI control, data disclosure, gesture abuse | bridge-only API, capability checks, disabled-provider stable errors, no raw `AccessibilityNodeInfo` across process | `NodeAutoJsBridgeInstrumentationTest`, `NodeAccessibilitySelectorBridgeInstrumentationTest`, `NodeBridgePermissionManifestInstrumentationTest` |
| Screen capture | Sensitive screen data capture | user/Android permission flow, foreground-service manifest policy, opaque handles, provider cleanup | `EmbeddedNodePackagedApkSmokeTest`, `NodeImageBridgeExpansionInstrumentationTest`, packaged verifier |
| Shell, Shizuku, root | Command execution and privilege escalation | `shell` bridge alternative, `child_process` denied, explicit `shell.root`, timeout and resource cleanup | `NodeShellBridgeInstrumentationTest`, `NodeSecurityRegressionInstrumentationTest`, `NodeBridgeResourceRegistryInstrumentationTest` |
| Packaged app data | Leaking injected scripts/assets or wrong entry execution | scoped moduleSources, encrypted entry support, package verifier, native ABI checks | `EmbeddedNodePackagedApkSmokeTest`, `embeddedNodePackagedApkOutputVerify`, `embeddedNodePackagedDiagnosticReport` |
| User files | Path traversal, symlink escape, source-map leakage | scoped fs for sync/callback/promises, realpath checks, source-map path policy | `NodeSecurityRegressionInstrumentationTest`, `NodeScopedFsFuzzerInstrumentationTest`, `NodeFsPromisesInstrumentationTest` |
| Cross-UID workspace | Unrequested host-file disclosure, archive traversal, TOCTOU substitution, partial writeback, over-broad deletion, leaked descriptors | explicit-input archive v2, plugin-private extraction, zero host scan, `lstat`/`O_NOFOLLOW`/`fstat` identity checks, bounded archives, final tombstone allowlist, complete-commit validation, close on every exit | `NodeRuntimePluginR5InstrumentationTest` |
| Android permissions | Manifest over-grant or missing permission at runtime | bridge permission manifest, packaged manifest patch verification, stable missing-capability errors | `NodeBridgePermissionManifestInstrumentationTest`, packaged diagnostic reports |
| Bridge resources | Leaked image/screen/network/shell handles after stop | per-execution registry, idempotent close, destroy cleanup diagnostics | `NodeBridgeResourceRegistryInstrumentationTest`, `NodeBridgeConcurrencyLimitsInstrumentationTest` |

## Attack Surface Matrix

Each surface must keep its tests mapped here before Node Profile v1 can be treated as GA-ready.

### TM-CJS-Resolver

Risk: CommonJS resolution can load a package-private file, escape package root through `exports`/`imports`, shadow builtins, or load a native addon.

Mitigation: AutoJs6 resolver owns package traversal, builtin allowlist wins over `node_modules`, package target validation denies root escape, and `.node` targets fail with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED`.

Tests: `EmbeddedScriptMvpInstrumentationTest`, `NodeSecurityRegressionInstrumentationTest`, `NodeNativeAddonPolicyInstrumentationTest`, `NodeResolverFuzzerInstrumentationTest`, `NodeCompatCorpusInstrumentationTest`.

Remaining risk: Resolver behavior is intentionally a safe subset of desktop Node. Compatibility differences must stay documented in `COMPATIBILITY_PROFILE.md` and resolver trace/visualizer reports.

### TM-Scoped-FS

Risk: A script reads or writes outside its project through absolute paths, `..`, NUL, `/proc`, `/sys`, `/dev`, symlink chains, realpath escape, file URLs, or external source maps.

Mitigation: Scoped policy applies before and after canonicalization; file URL and source-map diagnostics do not bypass the same policy; fs callbacks/promises drain before completion.

Tests: `NodeSecurityRegressionInstrumentationTest`, `NodeScopedFsFuzzerInstrumentationTest`, `NodeFsPromisesInstrumentationTest`, source-map instrumentation, release checklist scoped fs negatives.

Remaining risk: Platform-specific path semantics and filesystem permissions vary by Android version. Fuzzer seeds must be saved and replayed when a new edge case appears.

### TM-External-Runtime-Workspace

Risk: A replaced or unauthorized runtime service can impersonate the selected
backend; a Binder failure can cause duplicate side effects; or cross-UID
workspace transport can disclose unrequested host files, traverse archive
paths, race path identity, apply a partial output, delete an unrelated host
file, or leak a file descriptor.

Mitigation: Discovery binds only an enabled, authorized, signature-permission
service whose component/version/update-time/UID/signer identity and negotiated
contract/capabilities/process ABI pass. Package, host state, trust, and Binder
death invalidate the pooled session. The plugin process persists only while
healthy; execution state is fresh, admission is single-active with no queue,
and active cancellation restarts the process. `pluginOptional` falls back only
before dispatch. After dispatch may have started, response loss is terminal and
never replayed. Workspace v2 enumerates explicit inputs only, passes owned PFDs,
extracts into plugin-private storage, enforces path/count/byte and
symlink/special-file rules, verifies file identity before and after open, and
applies output plus deletion tombstones only after a complete zero-scan commit.

Tests: `NodeRuntimePluginR5InstrumentationTest`, including trust and contract
rejection, persistent-process/fresh-execution checks, active cancellation and
new-PID recovery, single-active busy rejection, pre-dispatch-only fallback,
Binder-death/no-replay recovery, workspace writeback/deletion, and warm-cache
sampling. The three-ABI 8/8, zero-skip record is archived in
[`R5_ACCEPTANCE_SAMPLING.md`](../R5_ACCEPTANCE_SAMPLING.md).

Remaining risk: Android package and filesystem behavior varies by platform.
Future transport fields or deletion authority require new negative corpus and
cross-UID device evidence; no caller-provided path may broaden the explicit
input/tombstone allowlist.

### TM-Package-Exports-Imports

Risk: Package `exports` or `imports` targets can hide traversal, null blocking mistakes, wildcard mismatches, unsupported array behavior, or wrong condition selection.

Mitigation: Targets must remain inside package root and working directory; unsupported targets fail closed with stable resolver errors; fuzzers run with deterministic seeds and replay support.

Tests: `NodeResolverFuzzerInstrumentationTest`, `NodeSecurityRegressionInstrumentationTest`, `NodeCompatCorpusInstrumentationTest`, `NodeRealNpmCompatInstrumentationTest`.

Remaining risk: Desktop Node parity is not complete. Any compatibility expansion must include negative root-escape tests.

### TM-Bridge-RPC

Risk: Malformed request/response payloads, duplicate callbacks, missing capability declarations, pending privileged calls, or service destroy can leak privilege or hang Node.

Mitigation: Strict JSON request shape, stable malformed errors, capability manifest, provider-side pre-dispatch capability audit, provider dispatch registry, at-most-once callback completion, timeout, pending-call cleanup, fake providers, service-destroy rejection.

Tests: `NodeAutoJsBridgeInstrumentationTest`, `NodeBridgeRpcFuzzerInstrumentationTest`, `NodeBridgePermissionManifestInstrumentationTest`, `NodeBridgeResourceRegistryInstrumentationTest`, `NodeBridgeConcurrencyLimitsInstrumentationTest`.

Remaining risk: Some Android providers are still fake or disabled in normal builds. Real provider enablement must add provider-specific permission and cleanup tests.

### TM-Feature-Flags

Risk: Project metadata, packaged assets, or release build properties enable experimental or high-risk features without release review.

Mitigation: Critical flags default off, are build-time controlled, and are visible through compatibility/profile reports. Project/package metadata is audited and rejected for flag overrides, including unknown flags. Release builds reject release-forbidden experimental Gradle flags; debug builds remain the experimental validation path.

Tests: `NodeFeatureFlagRegistryInstrumentationTest`, design-gate instrumentation for ESM, worker, Java interop, network, raw network policy tests.

Remaining risk: Experimental implementations can become de facto stable through samples or docs. Promotion requires `EXPERIMENTAL_PROMOTION_POLICY.md` gates.

### TM-Dependency-Bundler

Risk: Vendored npm content brings native addons, lifecycle scripts, node-gyp artifacts, unchecked `.bin`, oversized moduleSources, or hidden unsafe metadata.

Mitigation: Runtime install is unsupported; host bundler sanitizes vendored dependencies; unsafe fixtures fail with explicit reasons; packaged preflight enforces module source budgets.

Tests: `verifyNodeDependencyBundlerFixtures`, `bundleNodeDependencies`, dependency manifest checks, packaged smoke.

Remaining risk: Third-party package updates can change metadata without code changes. Corpus updates need license and security review.

### TM-Java-Interop

Risk: Raw Java/Android access bypasses bridge policy, exposes `Context`, reflection, class loaders, Binder, native loading, or privileged services.

Mitigation: Disabled by default; experimental module has a tiny allowlist and opaque handles; no project/package override for normal builds.

Tests: `NodeJavaInteropBridgeInstrumentationTest`, `NodeFeatureFlagRegistryInstrumentationTest`, security regression denied-feature coverage.

Remaining risk: Any future allowlist entry can become a privilege tunnel. Each entry needs a capability review and negative tests for reflection/raw object escape.

### TM-Worker

Risk: Worker threads bypass parent restrictions, access bridge modules, leak resources, spawn nested workers, or continue after parent cleanup.

Mitigation: Disabled by default; experimental workers use parent-scoped resolution, smaller builtin set, no bridge modules, one-worker limit, startup timeout, nested-worker denial, parent cleanup.

Tests: `NodeWorkerThreadsBootstrapInstrumentationTest`, `NodeWorkerThreadsDesignGateInstrumentationTest`, `NodeWorkerThreadsExperimentalInstrumentationTest`, `NodeWorkerThreadsResourceStressInstrumentationTest`.

Remaining risk: Packaged worker source graphs and stronger memory limits remain follow-up work.

### TM-Controlled-Network

Risk: Network access leaks data, bypasses Android permission policy, opens unbounded sockets, misses TLS/proxy policy, or keeps callbacks after service destroy.

Mitigation: Raw network modules stay denied; controlled fetch/WebSocket are bridge modules, disabled in normal builds, bounded by timeout/size/concurrency, and provider-destroy cancellation.

Tests: `NodeRawNetworkPolicyInstrumentationTest`, `NodeFetchBridgeInstrumentationTest`, `NodeWebSocketBridgeInstrumentationTest`, packaged diagnostics for `INTERNET`.

Remaining risk: Live provider enablement needs privacy, permission, TLS, proxy, and long-running lifecycle review before promotion.

### TM-Packaged-Manifest-Patch

Risk: Packaged APK misses `NativeNodeEmbeddedScriptService`, native libraries, ABI pruning, foreground-service permissions, bridge-required permissions, or encrypted entry metadata.

Mitigation: Packaged preflight and output verifier check service manifest, native libraries per ABI, FGS permissions, x86 fail-fast, moduleSources, encrypted entry, and capability-derived Android permissions from `NodeBridgePermissionManifest`.

Tests: `EmbeddedNodePackagedApkSmokeTest`, `embeddedNodePackagedApkOutputVerify`, `embeddedNodePackagedDiagnosticReport`, `verifyNodeTemplateReproducibility`.

Remaining risk: Android platform policy changes can invalidate foreground-service declarations. Release gates must keep Android SDK behavior and Play policy notes current.

## Security Regression Mapping

| Test or gate | Threat sections |
| --- | --- |
| `NodeSecurityRegressionInstrumentationTest` | TM-CJS-Resolver, TM-Scoped-FS, TM-Package-Exports-Imports, TM-Feature-Flags, TM-Controlled-Network |
| `NodeResolverFuzzerInstrumentationTest` | TM-CJS-Resolver, TM-Package-Exports-Imports |
| `NodeScopedFsFuzzerInstrumentationTest` | TM-Scoped-FS |
| `NodeRuntimePluginR5InstrumentationTest` | TM-External-Runtime-Workspace, cross-UID workspace asset, user files asset |
| `NodeBridgeRpcFuzzerInstrumentationTest` | TM-Bridge-RPC |
| `NodeBridgePermissionManifestInstrumentationTest` | TM-Bridge-RPC, TM-Packaged-Manifest-Patch, Android permissions asset |
| `NodeBridgeResourceRegistryInstrumentationTest` | Bridge resources asset, TM-Bridge-RPC, TM-Controlled-Network |
| `NodeBridgeConcurrencyLimitsInstrumentationTest` | TM-Bridge-RPC, bridge resources asset |
| `NodeNativeAddonPolicyInstrumentationTest` | TM-CJS-Resolver, TM-Dependency-Bundler |
| `NodeRawNetworkPolicyInstrumentationTest` | TM-Controlled-Network |
| `NodeFetchBridgeInstrumentationTest` | TM-Controlled-Network, TM-Bridge-RPC |
| `NodeWebSocketBridgeInstrumentationTest` | TM-Controlled-Network, TM-Bridge-RPC |
| `NodeJavaInteropBridgeInstrumentationTest` | TM-Java-Interop, TM-Feature-Flags |
| `NodeWorkerThreadsBootstrapInstrumentationTest` | TM-Worker, TM-Feature-Flags |
| `NodeWorkerThreadsDesignGateInstrumentationTest` | TM-Worker, TM-Feature-Flags |
| `NodeWorkerThreadsExperimentalInstrumentationTest` | TM-Worker |
| `NodeWorkerThreadsResourceStressInstrumentationTest` | TM-Worker, bridge resources asset |
| `verifyNodeDependencyBundlerFixtures` | TM-Dependency-Bundler |
| `EmbeddedNodePackagedApkSmokeTest` | TM-Packaged-Manifest-Patch, packaged app data asset, screen capture asset |
| `verifyNodeReleaseGate` | Cross-surface release gate |

## Review Rules

- Adding a stable capability requires a new row or update in this threat model.
- Adding an experimental capability requires a design doc, feature flag, denied-by-default test, and remaining-risk entry.
- Changing resolver, fs, bridge, bundler, or packaged manifest behavior requires a positive test and a negative/security test.
- Changing external runtime discovery, dispatch, cancellation, replay, or
  workspace transport requires the focused R5 gate on every supported runtime
  ABI with no skipped tests.
- Fuzzer failures must save seed JSON under `build/reports/nodejs/fuzz-seeds/` and be replayable with `:app:replayNodeFuzzerSeed`.
- Release candidates must attach doctor, release manifest, nightly report, packaged diagnostic, and relevant device reports.

## Open Risks

- Full desktop Node compatibility remains out of scope for v1.
- Android `isolatedProcess=true` is not used; switching to it requires a new Binder and permission design.
- Some bridge modules rely on fake or disabled providers in normal builds.
- Experimental ESM, worker, Java interop, controlled network, and WebSocket remain feature-gated and must not be documented as stable.
- Native addon support is not part of v1 and should remain disabled unless a separate developer-mode plugin model is designed.
- External runtime replacement, signer/UID drift, Binder loss, and cross-UID
  workspace changes remain security-sensitive even when JavaScript-visible
  compatibility is unchanged.
