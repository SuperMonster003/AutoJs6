# P13-39 Native Addon Loading Decision

P13-39 records native addon loading as partial. The current Phase 13 decision
is to expose diagnostics through `autojs6:profile.nativeAddonProfile` while
keeping arbitrary `.node` loading, `process.dlopen`, `process.binding`, and
`process._linkedBinding` denied by default.

## Decision

- Runtime `.node` module loading: denied.
- Direct `.node` require and resolve: denied.
- `process.dlopen`, `process.binding`, and `process._linkedBinding`: denied.
- Target profile: future `desktop_compat_opt_in` only.
- Signed packaged-only artifacts: required before enablement.
- ABI allowlist: required before enablement.
- Hash/signature allowlist: required before enablement.
- Dependency scan: required before enablement.
- Runtime downloads, npm native builds, lifecycle scripts, and download-exec:
  denied.
- Crash isolation: required before enablement.
- JNI bridge, raw `dlopen`, native memory access, and raw native handles:
  denied.
- Packaged behavior: not promoted.

This report does not add a callable native addon loader, a `.node` TypeScript
module declaration, or any script-visible way to bypass the existing Safe Node
Profile resolver and process API denials. It records the contract a future
Desktop native addon implementation must satisfy before any signed packaged
addon can be promoted.

For P13-39 v1.1, native addon artifacts stay packaged-only future inputs.
Runtime download, npm native build, lifecycle-script execution, postinstall
download/exec, and unrestricted native payload extraction remain stable-denied
and are not acceptable enablement paths for native addon promotion.
For downstream integrity gates, the same boundary is summarized as: runtime download, npm build, lifecycle script, and postinstall execution remain denied.

## Evidence

- `NodeNativeAddonPhase13DecisionInstrumentationTest` verifies the frozen
  `nativeAddonProfile`, default feature-flag denial, direct `.node`
  `require()`/`require.resolve()` denial, package `main` denial,
  `process.dlopen`/`binding`/`_linkedBinding` denial, and cache non-loading.
- `NodeNativeAddonPolicyInstrumentationTest` continues to verify `.node`
  denial through direct relative imports, `module.createRequire()`, package
  `main`, package `exports`, package `imports`, and package subpath imports.
- `NodeProcessSweepInstrumentationTest`,
  `NodeSecurityRegressionInstrumentationTest`, and packaged smoke coverage keep
  dangerous process APIs denied.
- Host dependency tooling still rejects `.node` payloads, native library/object
  files, `binding.gyp`, native build artifacts, and process native API usage.
- `verifyNodePhase13NativeAddonLoadingDecision` records the host gate and
  writes `phase13-native-addon-loading-decision.json` and `.md`.

## Remaining Blockers

- Define a signed packaged native-addon manifest with per-ABI artifact
  declarations.
- Tie ABI and Node-API/version allowlists to the embedded Node runtime,
  Android ABI, NDK, and release channel.
- Verify hashes and signatures before resolution and reject tampered or
  unlisted artifacts.
- Add dependency scan and APK-builder preflight evidence for every native
  payload and node-gyp trace.
- Add crash isolation or a host-owned native service boundary before allowing
  code into, or beside, the embedded runtime.
- Add packaged positive and hostile-corpus evidence before promotion.

The docs, release matrix, surface manifest, and Gradle gate are evidence-only
for this partial decision. They do not add a separate P13-39 blocker beyond the
signed packaged manifest, ABI/version allowlist, integrity, dependency scan,
crash isolation, and packaged hostile-corpus evidence items above.

## Rollback

Remove `nativeAddonProfile`, the focused instrumentation test, the P13-39
verifier, and the Phase 13 docs/report references. Runtime behavior falls back
to the existing Safe Node Profile native-code policy: `.node` targets stay
disabled with `ERR_AUTOJS6_NATIVE_ADDON_DISABLED`, private process native APIs
stay disabled with `ERR_AUTOJS6_PROCESS_API_DISABLED`, and dependency/package
tooling continues to reject native payloads before packaging.
