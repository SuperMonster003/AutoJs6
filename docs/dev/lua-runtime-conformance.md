# Lua Runtime Conformance Status

This matrix prevents local contract work from being reported as Android or
native runtime acceptance. `PASS` applies only to the evidence named in its
row. `IMPLEMENTED, NOT RUN` records present source without claiming compilation
or test success. `PENDING` is expected until the corresponding roadmap phase
exists.

| Layer | Status | Current evidence | Not established |
| --- | --- | --- | --- |
| TaggedWire and value JVM tests | PASS, JVM | Round trip, canonical map order, golden bytes, malformed fields, limits, defensive copies | Binder framing, PFD I/O |
| Negotiation and lifecycle JVM tests | PASS, JVM | Capability/limit intersection, credits, sequence, terminal, cancel/close, host-call replay | Android scheduling and races |
| Public AIDL compilation | PASS, GRADLE | R0 Gradle API gate, 14+50 JVM tests, `lua-runtime-api` debug AAR assembly | Cross-package calls, caller UID, Binder death |
| Host proxy and `.lua` routing | PASS, LOCAL R1 + R2 DEVICE | App compilation plus 66/66 focused JVM tests after Explorer executable admission; pinned selection, explicit transport, effective component-state handling, process-wide bounded Binder lane, and API 31 fixture cleanup policies | Production provider/native Lua execution, Android API/ABI matrix |
| Hostile Android provider | PASS, DEVICE PARTIAL | R2 debug-only/testOnly fixture 2/2 JVM build plus 19/19 isolated methods on `QV710AF65F` API 31/arm64-v8a | Provider death, wrong UID/capability attacks, update/uninstall during a session, oversized source, OS-level FD evidence |
| PUC Lua JNI provider | PASS, BUILD/ARTIFACT; NOT ANDROID-RUN | Clean independent revision; immutable protocol/Lua intake; Gradle 9.6.1 wrapper; native-enabled/provider-disabled Kotlin/Java/JNI build; 30/30 JVM tests; arm64-v8a/x86_64/universal APKs with exact ABI inventory, 16 KiB ELF/ZIP alignment, and a common debug signer | Android INFO/RUNTIME bind, native Lua execution, PFD ownership, allocator/hooks/OOM/cancel under execution, crash/wedge recovery, release signing, production enablement |
| Device/API/ABI acceptance | PASS, SINGLE DEVICE | API 31, arm64-v8a, 4 KiB, exact serial/artifact/restore evidence | API 24-36, x86_64, 16 KiB environments |
| Release evidence | PENDING | R6 | Signed independent plugin APK and production soak |

## R0 Local Gate

Run from the AutoJs6 host checkout without ADB or connected tasks:

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

Run the diff check after every intended new file is staged or otherwise
tracked, because plain `git diff` does not inspect untracked files. The gate is
invalid if generated version/build-time files changed. A successful gate means
the SDK and AIDL compile and their JVM policies pass; it does not mean a Lua
script ran.

## R1 Host Gate

Status on 2026-08-10: `PASS, LOCAL R1`. Gradle 9.6.1 compiled the App and its
complete unit-test source set, then ran the four Lua selectors: 10 classes and
66/66 tests with zero failures, errors, or skips. After `.lua` changed from
editable-only to executable plus editable, the same App/focused gate passed
again in 1 minute 36 seconds. No ADB, connected task, Binder-device test, or
Lua execution was part of the R1 result; the separate R2 run below occurred
after R1 closed.

A standalone Kotlin 2.3.0 compile completed with exit code 0. Direct JUnitCore
execution on JDK 25 then reported 30/30 in 1.699 seconds for the selected pure
host tests: 8 provider-policy, 14 host/deadline, and 8 Binder-lane tests, with
zero failures and zero skips. The older 18/18 diagnostic is superseded. A JDK
8 launch that could not load Java 21 class version 65 was an incompatible
runner diagnostic, not acceptance evidence. None of these standalone results
is Gradle App
compilation, an Android adapter compile, Binder/PFD instrumentation, or a
device gate.

The current four CI selectors contain 66 passing App JVM tests. Thirty are
the unchanged pure-policy/host/lane tests named by the standalone diagnostic;
the remaining routing, engine, snapshot, file-type, restart, and effective-
component-state tests are included in the Gradle pass. In particular, restart recovery now
preserves `.lua` instead of coercing it to `.lua.js`, and runtime discovery
combines the manifest default with PackageManager's component override.

Static source now routes synchronous plugin calls through one process-wide
daemon worker with capacity one and no backlog. Each complete preparation or
acquisition phase shares one monotonic host budget and a 16-candidate cap.
Timeout or interruption bounds the host caller but cannot terminate the remote
Binder transaction. An abandoned creation revokes its callback/broker and
permanently poisons later Lua calls
until host-process restart; the worker cancels and closes a late session and
never starts or replays it. The R1 local gate and phase-specific JVM
force-stop/cleanup coverage pass. Real Binder scheduling, death, callback
races, and PFD ownership remain R2 Android evidence.

The lane covers untrusted plugin Binder calls. PackageManager/system-server
queries are checked against the remaining phase budget before and after each
call, but an individual platform Binder transaction is not made cancellable.

## R2 Hostile Fixture Gate

The current checkout contains `test-apps:lua-runtime-conformance`, registered
as a debug-only, `android:testOnly="true"` application. Its INFO, runtime, and
test-control services share `:lua_runtime`; hostile components are disabled by
default and the control service can select only allowlisted components in its
own package. The manifest declares two INFO services, one control service, and
fourteen runtime scenarios, with exactly one normal INFO/runtime pair enabled
by default. Source includes explicit release gates and 24 counters for plugin
INFO, runtime info, execution creation, callbacks, source descriptors,
read-only checks, exact-content failures, null binding, sessions, start,
cancel, and close events. The fixture advertises synthetic
`Build.SUPPORTED_ABIS` metadata only; it contains no Lua/native runtime and
establishes no native ABI support.

The host contains 19 opt-in Android tests covering normal execution, malformed
runtime info, deterministic null binding, provider ambiguity,
INFO/runtime/create stalls, callback before the create reply, late-session
cleanup, zero credits, sequence gap/replay,
wrong request ID, duplicate terminal containment, ignored cancellation, and a
sequential second-create BUSY case after the first session is active. This does
not prove atomic exclusion of truly concurrent creates. Three additional tests
are authored to exact-bind the normal runtime and send read-only PFDs containing
truncated, trailing, or same-length digest-mismatched bytes against independently
encoded metadata. Fixture source accepts only an `EBADF` result from a tracked,
zero-length provider-side `pwrite` duplicate as read-only confirmation; the
probe cannot change source bytes. It records one exact failure reason, closes
every provider-owned descriptor, and creates no session;
host test source also requires the caller-owned descriptor to survive the
remote rejection and close only under caller ownership. These cases are
`PASS` on the named API 31 fixture, not OS-level FD leak evidence.

The default-disabled null-binding component is reachable only through the
fixture control allowlist. It records a call only for an actionless Intent that
explicitly names itself and then returns `null`; it does not terminate the
shared process. Its fresh-process host test requires a `BIND_FAILED` evaluation
and zero runtime-info, execution, session, callback, and source-FD activity.
API 28+ can report `onNullBinding` immediately; API 24-27 may reach the same
bounded rejection through bind timeout because that callback is unavailable.
The API 31 immediate-null branch passed; the API 24-27 timeout branch remains
untested.

Status: `PASS, DEVICE PARTIAL`. Gradle generated fixture AIDL, compiled Kotlin,
merged the manifest, ran 2/2 fixture JVM tests with zero failures/errors/skips,
and assembled the debug APK. The host `LuaRuntimeAndroidConformanceTest` plus
its control AIDL also compile successfully as part of
`:app:compileAppDebugAndroidTestKotlin`. On explicit serial `QV710AF65F`, all
19 methods then passed individually against the final reviewed arm64 host,
androidTest, and fixture APKs. Each installed base APK matched its local digest,
and all three originally absent packages were absent again after teardown.
The full package/signature/UID/device matrix, method scenarios, correction
chain, and evidence limits are in
[the dated device report](lua-runtime-device-evidence-2026-08-10.md). Fixture
counters describe logical fixture ownership and do not replace OS-level FD
leak evidence.

A standalone fixture diagnostic generated the control AIDL with build-tools
36.1 (exit 0), compiled the generated Java with `javac` (exit 0), compiled all
fixture Kotlin sources with Kotlin 2.3.0 (exit 0), and ran the scenario-mapping
JUnit tests 2/2 with zero failures and zero skips. Exact source verification,
additional hostile scenarios, counters, and the host Android harness were added
after that run, so the standalone diagnostic is stale for this snapshot. It is
not a Gradle, manifest-merge, APK, or Android gate and does not change R2.

Repeat the fixture build gate without ADB or connected tasks as follows:

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

Compile the authored host Android harness separately with
`:app:compileAppDebugAndroidTestKotlin`. Create-abandon cases permanently poison
the host lane, so each such device case requires an isolated instrumentation
process plus explicit fixture release/cleanup. Skipped cases are not passes.

## R3 Independent Provider Build And Artifact Checkpoint

`E:\AutoJs6-Plugin-Lua-Runtime` is clean at independent revision
`3be87d6c7f354649cded9b580fa53eceeb849791`, with `VERSION_BUILD=12` matching
its 12-commit history. The repository-owned Gradle 9.6.1 wrapper pins
distribution SHA-256
`9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14`
and wrapper-JAR SHA-256
`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`.

The staged AARs are pinned to clean AutoJs6 revision
`ae422391759810ce9dbbf0bc119bb47834c66b85`:

- `common-plugin-api.aar`:
  `d745bb24d6a6995e68ebea27d592faec162f0bad4f9bf70cb038a480ad8c6df2`
- `protocol-wire-api.aar`:
  `6c597ec095852eac7e6277574191141100dc96b6c5a803725ed540f42aa7d871`
- `lua-runtime-api.aar`:
  `a40a0fff299f60520d07a2739a8f0c9ab004d3f307ff016bab35dc902635b851`

The official PUC Lua 5.4.8 archive SHA-256 is
`4f18ddae154e793e46eeab727c59ef1c0c0c2b744e7b94219710d76f530629ae`;
the canonical 63-file vendored tree SHA-256 is
`abc2321841ec25281797b4b3bb855d6ad1caa8ddea265ef0d47844cf959dcda8`.
The fail-closed verifier reports
`STATIC_SCAFFOLD_OK protocol=ready lua=ready build_ready=true`.

A clean native-enabled/provider-disabled build executed all 50 requested tasks.
Eight JVM suites report 30/30, with no failure, error, or skip. The generated
and packaged flags are `LUA_NATIVE_ENABLED=true` and
`LUA_PROVIDER_ENABLED=false`, and both exported services remain resource-gated
off. INFO/RUNTIME sources compile in `:lua_runtime`, accept actionless explicit
binding, and select the native runner only in the native-enabled variant. Those
facts are compile/manifest/JVM evidence, not an Android bind or execution pass.

The artifact gate found exactly three APKs under one debug signer SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`:

- universal:
  `d5089e7e5c78a87e8c8f6dccdcec499127aec3358f04dc20accfbd05ae9311e2`
- arm64-v8a:
  `b5fc7ce3d6633e5f8a13ae92c78828e7ba6ce42e06a4a43c232f42147865251a`
- x86_64:
  `09efce09ba7e6b3dccbc408d8f8fca86a4717f58f5972712e3e58c31c2952b79`

The ABI inventories are exact, all ELF LOAD alignments are `0x4000`, and every
APK passes `zipalign -c -P 16 4`. This is native build and artifact evidence;
the APKs were not installed and no ADB transaction was used for this checkpoint.
A detached clean checkout of the same revision repeated 50/50 Gradle tasks,
30/30 JVM tests, and the full artifact gate with a fresh Gradle user home. Its
three APK digests differ from the first build, so the checkpoint establishes
clean-checkout input rebuildability, not byte-identical APK reproducibility.
Actionless cross-process binding, actual native Lua results, dedicated-process
session behavior, PFD accounting, allocator/OOM, hook cancellation, safe-library
denial, native crash or wedged-call containment, process kill/rebuild, clean
release signing, and production enablement all remain unestablished.

Recheck the existing artifacts without ADB or rebuilding:

```powershell
$sibling = 'E:\AutoJs6-Plugin-Lua-Runtime'
git -C $sibling rev-parse HEAD
python "$sibling\tools\verify_repository.py" --require-build-ready
& "$sibling\tools\verify_debug_artifacts.ps1" `
    -SdkRoot 'E:\.android\sdk' `
    -BuildToolsVersion '37.0.0' `
    -NdkVersion '28.2.13676358' `
    -ExpectedTests 30
exit $LASTEXITCODE
```

## Required Android Evidence

The future R2/R6 reports must retain package/component/UID/signer identities,
host and provider versions, protocol/runtime slot, ABI/API/page-size, exact
test selector, artifact digest, start/end time, and pass/fail/skip counts.
Failures remain evidence and cannot be overwritten by a smoke, alternate
device, JVM fixture, or manually relabeled report.

At minimum the hostile provider must exercise source truncation/trailing data,
digest mismatch, source/output quotas, zero credits, output replay/gap,
duplicate terminal, cancellation races, bind timeout/null binding, Binder
death, update/uninstall, wrong UID, ambiguous providers, capability replay,
and descriptor cleanup. The native provider additionally covers infinite-loop
stop, allocator OOM, native crash, wedged C call, process rebuild, and repeated
execution resource growth.
