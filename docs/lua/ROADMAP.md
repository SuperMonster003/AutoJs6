# AutoJs6 Lua Runtime Integration Roadmap

Last updated: 2026-08-10

This checklist tracks a first-class `.lua` script engine backed by an
independently released runtime plugin. A checked item means its named evidence
exists in the current checkout. JVM or assembly evidence never substitutes for
Android Binder, native-runtime, device, ABI, or release evidence.

## Current Verified Checkpoint

- [x] `.lua` is executable and text-editable in `FileUtils.TYPE.LUA`; after the
  R1 gate passed, Explorer now enters the fail-closed Lua proxy run path.
- [x] R0 adds the `plugin-api:lua-runtime-api` contract module, and R1 host
  proxy, routing, selection, transport, and engine sources now exist locally.
- [x] V1 uses TaggedWire, a read-only source PFD, bounded values, output
  credits, explicit start, one terminal event, and a typed host broker.
- [x] The R0 API gate, R1 App compile plus 66 focused JVM tests, R2 fixture
  2-test/assembly gate, and host Android-test harness compilation pass locally.
- [x] The V1 synchronous plugin calls now pass through one process-wide daemon
  lane with capacity one and no backlog. Each complete preparation or
  acquisition phase propagates one monotonic budget and rejects inventories
  above 16 candidates.
- [x] The protected `QV710AF65F` soak was explicitly released by the user on
  2026-08-10. Every Gradle invocation waited for two idle samples and ran
  serially. ADB was used only after release and the audited packages were
  restored to their original absent state after R2.
- [x] The no-Gradle/no-ADB static roadmap guard passes 155 source and
  configuration checks across the host, R2 fixture, and R3 sibling scaffold.
- [x] R2 has a compiled opt-in host Android harness, an assembled hostile
  fixture, and a 19/19 API 31 arm64-v8a device checkpoint. R3 remains a
  separate, default-disabled source scaffold without Android/native
  compilation.
- [x] The current boundary is explicit: Explorer can dispatch `.lua` to the
  proxy and the fake provider has Android Binder/PFD acceptance, but no
  compiled/runnable production provider, vendored Lua, JNI runtime, or native
  Lua execution claim exists yet.

## Deferred Verification Queue After The Protected Soak

Do not execute this queue merely because the estimated soak duration elapsed.
First confirm that the owning soak has completed and released `QV710AF65F` and
the shared Gradle/ADB resources.

- [x] Record the user's 2026-08-10 soak completion/release decision; its
  canonical reports were preserved and this checkout did not query, stop,
  restart, or install to the device before that release.
- [x] Run the R0/R1 API gate, then the focused App compile/JVM gate, using the
  separate invocations recorded under R1. Stop on the first nonzero exit code.
- [x] Run the R2 fixture JVM/assembly gate and
  `:app:compileAppDebugAndroidTestKotlin` without ADB.
- [x] Review all new compile/test failures and refresh source hashes before any
  installation; a skipped test is not a pass.
- [x] Select one explicit Android serial, record package/signature/version and
  device-state preflight, then install only the reviewed host/fixture artifacts.
- [x] Run each hostile R2 method with its exact scenario argument; methods that
  require `freshProcess=true` must execute alone in a fresh target process.
- [x] Record Binder/PFD/UID/process evidence per scenario, restore the audited
  pre-test device state, and keep failed evidence instead of relabeling it.
- [ ] Stage R3 protocol AARs and vendor Lua only from immutable, digest-verified
  inputs; then compile/JNI/ABI/16 KiB gates in the sibling repository.
- [x] Keep R3 provider/native enablement disabled. Explorer now enters the
  fail-closed proxy after R1, while successful provider-backed execution stays
  gated by R2, R3, and R6 evidence.

## R0 - Freeze The Protocol And Safety Baseline

- [x] Separate the identities `lua-runtime` (plugin ID), `lua` (engine),
  `org.autojs.plugin.lua.RUNTIME` (service action), and `lua54` (runtime slot).
- [x] Define INFO discovery plus an explicit runtime service contract.
- [x] Define version negotiation, provider limits, source length/SHA-256,
  required capabilities, memory/output/deadline limits, and bounded errors.
- [x] Define the V1 `LuaValue` set and canonical UTF-8 map-key ordering.
- [x] Reject cyclic/unrepresentable Lua values and reject nil inside arrays or
  maps so a value can round-trip through a dense Lua table.
- [x] Define start, credits, ordered output, cancellation ownership, unique
  terminal events, idempotent cancel/close, and no automatic redispatch.
- [x] Define request-bound host capability calls with replay and outstanding
  call limits.
- [x] Freeze golden wire fixtures and cover malformed, limit, compatibility,
  lifecycle, defensive-copy, and sensitive-summary cases with JVM tests.
- [x] Document the protocol and the evidence boundary.

Android/Binder/FD conformance is intentionally outside the R0 completion
claim and remains tracked by R2 and R6.

R0 exit gate:

```powershell
$gradleArgs = @(
    ':plugin-api:protocol-wire-api:testDebugUnitTest'
    ':plugin-api:lua-runtime-api:testDebugUnitTest'
    ':plugin-api:lua-runtime-api:assembleDebug'
    '-Pautojs.gradle.build.number.auto.increment.enabled=false'
    '-Pautojs.gradle.build.time.update.enabled=false'
    '--no-daemon'
    '--console=plain'
)
& .\gradlew.bat @gradleArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --check HEAD --
exit $LASTEXITCODE
```

R0 gate evidence on 2026-08-10:

- Gradle 9.6.1 completed the separate API invocation successfully in 1 minute
  30 seconds. Current XML reports contain 14 protocol-wire plus 50 Lua-runtime
  tests with zero failures, errors, or skips.
- `lua-runtime-api-debug.aar` exists at 146,134 bytes with SHA-256
  `f26f19886682a635edf6e1a64b61bda0166e1f45ebf65bd49fd45980a2b77a6b`.
  The Gradle tasks were `UP-TO-DATE`; this proves input/output consistency for
  this checkout, not an immutable clean-revision rebuild for R3 intake.

Run the diff check after every intended new file is staged or otherwise
tracked; plain `git diff` does not inspect untracked files.

## R1 - Add The Fail-Closed Host Proxy

- [x] Make `ScriptSource` expose a language-owned extension, then add
  `LuaScriptSource` and `LuaFileSource` with extension/engine `lua`.
- [x] Add the App dependency on `lua-runtime-api` and register the local proxy
  engine in `AbstractAutoJs`; engine construction must remain non-throwing.
- [x] Route both file and raw-path launches to Lua; reject Lua combined with a
  JavaScript `preExecuteScript`.
- [ ] Audit and test every existing consumer of the shared file/path factories,
  including Engines, Node bridge dispatch, ScriptIntents, timed receivers,
  existing shortcuts/widgets, and task-list launches; none may bypass Lua's
  unavailable/error boundary or fall back to another engine.
- [x] Add a locally constructible `LuaPluginScriptEngine` that does not create
  or serialize `ScriptRuntime`, `Context`, or Java objects.
- [x] Perform discovery and negotiation inside protected engine initialization,
  not in the engine constructor or `put()`.
- [x] Add a host selection policy that pins component, UID, complete signer
  set, package version, protocol, ABI, runtime slot, and capabilities, and
  rejects INFO/package/runtime version or ABI-inventory drift.
- [x] Require the initial `lua54` slot, intersect the provider process ABI with
  the provider-declared packaged ABI inventory and device ABIs, and bind
  started slot/language identity to the pinned runtime info. Inspection of the
  APK's actual native inventory remains an R3/R6 artifact gate.
- [ ] Snapshot source into a read-only PFD with exact length/SHA-256 and close
  every host/provider descriptor on all terminal and pre-dispatch paths.
- [x] Use explicit-component bind timeout/death/null-binding handling and
  validate callback/broker caller UID against the pinned provider.
- [x] Bound the synchronous Binder handshakes used by
  `IPluginInfoProvider.info` and `ILuaRuntimeProvider.getRuntimeInfo()` /
  `createExecution()` with one process-wide, capacity-one worker and no
  backlog. Give each complete preparation/acquisition phase one absolute
  budget, cap discovery at 16 candidates, revoke callbacks after abandonment,
  dispose a late session on the worker, and permanently poison the lane after
  an uncertain creation dispatch until the host process restarts.
- [x] Reject zero providers with `LUA_RUNTIME_UNAVAILABLE`, reject ambiguous
  providers, and never fall back to Rhino, Node.js, or RootAutomator.
- [x] Bridge the asynchronous Binder session to synchronous engine completion;
  make `forceStop()` cross-thread safe and `destroy()` idempotent.
- [x] Cover fast failure, Binder death after dispatch, loop execution, stop,
  and running-task cleanup without automatic execution replay.
- [x] Only after the proxy passes its gate, mark `.lua` executable and expose
  the existing Explorer run path.

R1 exit requires focused host JVM tests, an in-process transport fake, and
static App compilation. Cross-package fake-provider Binder smoke belongs to R2.

R1 evidence boundary on 2026-08-10:

- Static source review confirms the checked implementation items above and the
  four focused CI selectors. It also confirms the single-worker/no-backlog
  lane, per-phase monotonic host budget, 16-candidate cap, callback revocation,
  worker-owned late-session cancel/close, and create-abandon poison policy.
- The four selectors contain 66 App JVM tests. New coverage
  includes stop/destroy races in prepare, acquire, and create; task/engine/
  restart routing; partial snapshot cleanup; and effective service enablement.
  A persisted `foo.lua` is no longer rewritten as `foo.lua.js`, and discovery
  now combines the manifest default with PackageManager's component override.
  Gradle reports 10 classes and 66/66 tests with zero failures, errors, or
  skips. The initial App gate succeeded in 7 minutes 5 seconds.
- A standalone Kotlin 2.3.0 compile previously completed with exit code 0,
  followed on JDK
  25 by a direct JUnitCore diagnostic reporting 30/30 (policy 8, host 14, lane
  8) in 1.699 seconds, with zero failures and zero skips. It supersedes the
  older 18/18 diagnostic. This is not Gradle App compilation, Android Binder/PFD,
  instrumentation, or device evidence; the Gradle result above supersedes it
  for the R1 local gate.
- After that gate, `.lua` gained an executable plus editable identity and the
  existing Explorer run path. The resulting production/test delta was compiled
  and the same 66/66 focused tests passed again in 1 minute 36 seconds.
- The unchecked cross-surface consumer audit and real descriptor-path item
  remain open until their named evidence exists. Cross-thread lifecycle,
  failure/cleanup coverage, and Explorer admission now have local gate evidence.
- The lane bounds the host caller; it does not cancel a wedged remote Binder
  transaction. A timed-out read keeps the lane saturated until it drains. An
  abandoned `createExecution()` revokes its callback/broker and permanently
  poisons later Lua handshakes until the host process restarts; a late session
  is cancelled and closed on the worker and is never started or replayed.
  Android Binder scheduling, provider death, callback races, real PFD ownership,
  and bounded `forceStop()` against real Binder stalls remain unproved. The R1
  local exit is closed; cross-package acceptance remains R2/R6 work.
- No ADB command, connected task, Binder-device test, or Lua execution was used
  to close the R1 local checkpoint. R2 device evidence was collected only
  afterward, and each Gradle invocation waited for an exclusive window.

Run the deferred local gate only after two consecutive preflight samples show
no other Codex Gradle client. The API and App invocations stay separate so
App-only `--tests` filters cannot suppress API-module tests:

```powershell
$apiGradleArgs = @(
    ':plugin-api:protocol-wire-api:testDebugUnitTest'
    ':plugin-api:lua-runtime-api:testDebugUnitTest'
    ':plugin-api:lua-runtime-api:assembleDebug'
    '-Pautojs.gradle.build.number.auto.increment.enabled=false'
    '-Pautojs.gradle.build.time.update.enabled=false'
    '--no-daemon'
    '--console=plain'
)
& .\gradlew.bat @apiGradleArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$appGradleArgs = @(
    ':app:compileAppDebugKotlin'
    ':app:testAppDebugUnitTest'
    '--tests'
    'org.autojs.autojs.core.plugin.lua.*'
    '--tests'
    'org.autojs.autojs.engine.Lua*'
    '--tests'
    'org.autojs.autojs.script.Lua*'
    '--tests'
    'org.autojs.autojs.util.Lua*'
    '-Pautojs.gradle.build.number.auto.increment.enabled=false'
    '-Pautojs.gradle.build.time.update.enabled=false'
    '--no-daemon'
    '--console=plain'
)
& .\gradlew.bat @appGradleArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

git diff --check HEAD --
exit $LASTEXITCODE
```

## R2 - Build The Hostile Conformance App

- [x] Add `test-apps:lua-runtime-conformance` as `testOnly`, debug-only test
  infrastructure, not a production plugin.
- [x] Implement and compile coverage for early output, zero-credit output,
  sequence gaps/replay, duplicate
  terminals, wrong request IDs, stall, busy, and ignored cancellation.
- [ ] Cover truncated/oversized source PFDs, digest mismatch, trailing bytes,
  descriptor leaks, provider death, null binding, update, and uninstall.
- [ ] Cover undeclared/denied/replayed host capability calls and wrong caller
  UID.
- [x] Record Android API and ABI evidence separately from JVM evidence.

R2 source checkpoint on 2026-08-10:

- `test-apps:lua-runtime-conformance` is present in settings as a debug-only,
  `testOnly` Android application, and CI names its JVM-test and debug-assembly
  tasks. Seventeen declared services share one `:lua_runtime` process: sixteen
  selectable INFO/runtime components (two INFO plus fourteen runtime) and one
  fixed control service. Exactly one normal INFO and runtime service are
  enabled by default.
- The fixture-owned control plane selects only its own allowlisted components;
  the host now evaluates their effective component state instead of trusting
  only the manifest default. Twenty-four counters observe INFO/runtime/create,
  callback, session, source-descriptor ownership, read-only mode, and exact
  source-content rejection reasons plus exact actionless null binding within
  the fixture.
- Nineteen opt-in host Android tests are authored for normal execution,
  malformed runtime info, null binding, ambiguity, INFO/runtime/create stalls,
  callback before the create reply, late sessions, zero credits, sequence gap/replay,
  wrong request ID, duplicate terminal, ignored cancellation, and a sequential
  second-create BUSY case after the first session is active. Three of those
  tests exact-bind the normal runtime and send raw read-only PFDs whose bytes
  are truncated, trailing, or same-length digest-mismatched. Each requires a
  provider rejection before session/start, zero callbacks/broker calls, one
  reason counter, a still-open caller FD until caller close, and balanced
  provider-owned FD counters.
- The default-disabled null-binding runtime is selected only through the
  fixture allowlist. Its fresh-process test requires one actionless exact bind,
  a `BIND_FAILED` host evaluation, and zero runtime-info/create/start/session/FD
  activity; the service returns `null` without killing the shared control plane.
- The fixture Gradle gate is `COMPILED, NOT DEVICE-RUN`: AIDL, Kotlin, manifest
  merge, two JVM tests, and debug APK assembly completed successfully in 42
  seconds. The XML report is 2/2 with zero failures, errors, or skips. The
  1,034,673-byte fixture APK currently has SHA-256
  `47eba9655c427ae6c9a445d0be8d32119b4fa8b9584dc67c28a41a191ba77aa7`.
- `:app:compileAppDebugAndroidTestKotlin` completed successfully in 1 minute 20
  seconds with in-process Kotlin compilation. This compiles the 19 opt-in Lua
  methods and their test-control AIDL, but does not install, bind, execute, or
  prove Android Binder/PFD behavior by itself. The first two R2 source/build
  checkboxes are closed; the separate device outcomes follow below.
- The final arm64 host, androidTest, and fixture APKs were reviewed with Build
  Tools 37, installed only on explicit serial `QV710AF65F`, pulled back, and
  matched against their local SHA-256 digests. On the API 31/arm64-v8a/4 KiB
  device, all 19 methods passed individually with `OK (1 test)` and
  instrumentation code `-1`. Eight hostile methods used their required fresh
  target process, and the two create-abandonment poison cases ran last with
  post-test process cleanup. Full identities, digests, method-to-scenario
  results, the null-binding correction chain, and restore evidence are recorded
  in [the R2 device evidence report](../dev/lua-runtime-device-evidence-2026-08-10.md).
- The test control plane and counters are fixture-local observability. They do
  not establish OS-level FD counts, cross-process component switching, Binder
  callback ordering, wrong-UID rejection, provider death, update/uninstall, or
  native ABI support. `Build.SUPPORTED_ABIS` is synthetic fixture metadata, not
  proof that the APK packages native code.
- Duplicate terminal currently proves first-result containment and cleanup,
  not a host diagnostic. Callback-before-return and zero-credit cases remain
  ordering diagnostics because oneway send order is not Binder receipt order.
  The three raw hostile PFD tests and the runtime null-binding test are
  `PASS, API 31 ARM64 FIXTURE`; oversized-source, OS-level FD-count,
  provider-death, update-during-session, and uninstall-during-session work
  remains open, so the third R2 checkbox also remains open.
- Stall/create scenarios depend on the R1 bounded-lane gate. Because an
  uncertain creation permanently poisons the host lane, each such Android case
  ran in an isolated host instrumentation process with explicit fixture
  cleanup; skips were not accepted as passes.

Repeat the fixture module gate without ADB as follows:

```powershell
$r2FixtureGradleArgs = @(
    ':test-apps:lua-runtime-conformance:testDebugUnitTest'
    ':test-apps:lua-runtime-conformance:assembleDebug'
    '-Pautojs.gradle.build.number.auto.increment.enabled=false'
    '-Pautojs.gradle.build.time.update.enabled=false'
    '--no-daemon'
    '--console=plain'
)
& .\gradlew.bat @r2FixtureGradleArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
git diff --check HEAD --
exit $LASTEXITCODE
```

The host harness is compiled separately with
`:app:compileAppDebugAndroidTestKotlin`. Installation and device execution
remain explicitly selected R2 evidence rather than part of this fixture gate.

The source-only roadmap guard is safe to run during the protected soak because
it does not invoke Gradle, ADB, Binder, installation, or a device:

```powershell
& .\tools\lua\verify-lua-static-roadmap.ps1 -RequireR3
exit $LASTEXITCODE
```

## R3 - Create The Independent Runtime Plugin

- [ ] Create sibling repository `AutoJs6-Plugin-Lua-Runtime` with its own
  application ID, icon, version, signing, release history, and CI.
- [ ] Implement INFO and RUNTIME services; runtime binding must support an
  explicit component-only Intent whose action is null.
- [ ] Run executions in a dedicated `:lua_runtime` process with one active
  execution and no hidden queue.
- [ ] Vendor and pin PUC Lua 5.4 source, license notices, reproducible native
  build inputs, and runtime slot `lua54`.
- [ ] Use a bounded `lua_newstate` allocator, `lua_sethook` cancellation and
  deadline checks, protected calls, and one serialized worker per state.
- [ ] Open only the approved standard libraries; keep `debug`, unrestricted
  `io`, `os.execute`, dynamic C modules, Java reflection, and `loadlib` closed.
- [ ] Build and verify arm64-v8a and x86_64 artifacts, including 16 KiB ELF and
  APK alignment.

R3 source checkpoint on 2026-08-10:

- `E:\AutoJs6-Plugin-Lua-Runtime` now contains a 52-file independent Android
  source scaffold with its own application ID, version/signing contract, icon,
  CI source, and plugin roadmap. It has deliberately not been initialized as a
  Git repository, so the first R3 checkbox and release-history claim remain
  open.
- INFO and RUNTIME service sources share `:lua_runtime`, require the plugin
  permission, accept actionless explicit binding, and are controlled by two
  build flags that default off. The runtime service now owns a source-only
  session manager, but deliberately injects `DisabledLuaExecutionRunner`; the
  uncompiled JNI adapter is not connected.
- The Kotlin skeleton has a process-wide single worker with no queue, one
  active execution, a two-session retained ceiling, a bounded start lease,
  exact source length/EOF/SHA-256/UTF-8 verification, remaining-deadline
  propagation, output-credit accounting, unique terminal delivery, and a
  callback-close gate that holds no monitor while invoking Binder. A standalone
  Kotlin 2.3.20/JDK 21 diagnostic compiled these Android-free sources and ran
  23/23 JUnit tests with zero failures. The first launcher attempt stopped
  before source compilation because its ad-hoc compiler classpath omitted the
  cached JetBrains annotations JAR; the corrected invocation supplied that
  compiler dependency. This is not an AIDL, Android, PFD, or native gate.
- The JNI source creates one bounded `lua_State`, uses a text-only loader,
  installs cancellation/deadline hooks, performs protected execution, and
  admits only bounded scalar results. Its exact library allowlist is base,
  math, string, table, and UTF-8; coroutine, debug, io, os, package, dynamic
  loading, Lua protected calls, metatable installation, print/warn, and binary
  dump entrypoints remain unavailable. Six native-boundary tests are authored
  but not run.
- The scaffold freezes three repository-local protocol AAR names and a clean-
  revision rebuild/hash intake workflow. All AARs are absent and the lock is
  `not-staged` because the host protocol work has not been committed.
- The sibling input verifier now rejects duplicate JSON members and nested
  Git-ignore overrides, validates the frozen lock schema/repository/inventory,
  and offers a fail-closed `--require-build-ready` mode. Its Python hostile
  suite passes 15/15; normal verification reports `build_ready=false`, while
  the build-required mode exits nonzero as intended. This remains a static
  intake gate: commit existence/AAR provenance, the canonical extracted Lua
  tree, merged-manifest enablement, and CI execution semantics still require
  immutable-input and Gradle evidence.
- PUC Lua 5.4.8 is pinned to its official archive and SHA-256. The source is
  intentionally `not-vendored`; there is no placeholder. A future vendored
  state must come through the offline verified-archive intake script and also
  match a canonical full-source-tree file count and digest.
  CMake admits an exact core/library list, while the JNI probe demonstrates the
  bounded allocator and safe-library baseline. Future execution must use an
  explicit text-only load.
- The sibling static verifier reports
  `STATIC_SCAFFOLD_OK protocol=not-staged lua=not-vendored build_ready=false`.
  The host/R2/R3 static roadmap guard reports 155/155. No wrapper, Gradle/AIDL
  compile, native build, APK, ABI/alignment, signing, install, Binder/PFD, or
  device evidence exists, so every main R3 checkbox remains open. A native
  runner that does not return still requires dedicated-process termination and
  recovery evidence before enablement. Before wiring the adapter, Android tests
  must also resolve or explicitly accept the retained-cap third-create failure,
  tiny-deadline/start-lease race, and blocking non-regular source-FD behavior.

## R4 - Add Language-Neutral Host Capabilities

- [ ] Implement `require("autojs")` over the typed broker.
- [ ] Bind protocol-native arguments, result, and console output into the Lua
  module; start broker calls with timer, read-only device info, controlled
  module loading, and restricted file access. JSON stays provider-local.
- [ ] Enforce declared capabilities, per-call quotas, session-bound handles,
  call replay rejection, and cleanup on every terminal path.
- [ ] Add descriptor-backed module/large-result payloads in a compatible
  protocol revision with unique and exhaustive FD reference accounting.
- [ ] Add file write, network, shell, UI, and accessibility only as separately
  reviewable, default-denied capability families.

## R5 - Complete Product Routing

- [ ] Support editor run/save for saved and unsaved `.lua` files without
  coercing temporary files to `.js`.
- [ ] Add user-facing file-picker admission and Lua shortcut/scheduled-task
  creation UI, plus persisted Lua task/shortcut labels and engine metadata.
- [ ] Add project metadata and controlled `require` snapshots.
- [ ] Add Lua syntax highlighting as an independent editor feature.
- [ ] Keep APK packaging, debugger, UI DSL, Java interop, and Rhino API parity
  out of scope until separately designed and gated.

## R6 - Device And Release Acceptance

- [ ] Pass unavailable, single-provider, explicit-provider, ambiguity, identity
  drift, update, uninstall, and signer/UID rejection cases.
- [ ] Stop an infinite loop within the deadline and recover from OOM, native
  crash, Binder death, and an uninterruptible native call by rebuilding the
  runtime process.
- [ ] Pass slow consumer, zero credits, oversized/truncated/digest-mismatched
  source, output quota, repeated terminal, and cleanup/FD leak cases.
- [ ] Pass supported API 24-36 coverage and the required ARM64/x86_64 16 KiB
  environments.
- [ ] Prove existing Rhino, Node.js, RootAutomator, editor, tasks, and Explorer
  behavior remain unchanged.
- [ ] Produce signed, independently releasable plugin artifacts and preserve
  failed acceptance evidence rather than relabeling diagnostics as canonical.

## R7 - Optional Advanced Work

- [ ] Evaluate a separate `lua55` runtime slot without changing Lua 5.4 bytecode
  compatibility claims.
- [ ] Evaluate LuaJIT only as a different plugin/runtime slot with Lua 5.1,
  FFI, JIT, and security differences made explicit.
- [ ] Design debugger, project packaging, UI, and richer handle families as
  separate protocols and release decisions.
