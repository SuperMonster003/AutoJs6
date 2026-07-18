# Node.js Runtime Plugin R5 Acceptance Sampling

Status: accepted
Review date: 2026-07-18
Time zone: Asia/Shanghai

## Accepted Revisions

- AutoJs6 implementation: `f8ec4bf681a0e73e6fd2deddb93e411b1cb55a35`
- AutoJs6 Node.js runtime plugin implementation:
  `bd52e4b6db1963efae800972d88418daf2d50e01`
- The plugin repository was clean after its implementation commit. The AutoJs6
  implementation paths were clean after the implementation commit; the
  documentation-only changes containing this report were committed separately.

The live gates used AutoJs6 `6.8.0 Alpha7`, build code `5221`, and runtime
plugin `1.0.0`, build code `35`. A later default-distribution preflight advanced
the checked-in AutoJs6 build counter to `5222`, as expected from the repository
build policy; it did not change the accepted runtime implementation.

## Distribution Decision

`nodePlugin` with backend `plugin` is the default main-application release
path. The compatible, enabled, authorized plugin is required and the host APK
does not carry `libnode.so`, `libautojs6-node.so`, or `libc++_shared.so` as a
Node runtime payload.

The live R5 gate deliberately uses `nodeMini/pluginOptional`. That topology is
not the default release policy. It keeps an embedded runtime in the test host
only so the corpus can prove that one pre-dispatch failure may fall back once,
while a request that may have dispatched is never replayed or routed to another
backend. `pluginOptional` remains invalid for `nodePlugin` and `rhinoOnly`.

The default-path proof was generated with:

```powershell
.\gradlew.bat :app:embeddedNodePackagingPreflightAppDebug `
  :app:nativeNodeDependencyReportAppDebug --console=plain `
  "-Pautojs.nodejs.releaseFlavor=nodePlugin" `
  "-Pautojs.nodejs.backend=plugin" `
  "-Pautojs.nodejs.releaseAbis=arm64-v8a" `
  "-Pksp.incremental=false"
```

Both tasks passed. The resulting reports contain `native runtime requested:
false`, `release flavor expected ABIs: none`, and no Node runtime libraries in
the universal host APK.

## Artifact Identity

All APKs were signed by certificate SHA-256
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.
The host therefore classified the tested plugin as `official`. The runtime
service is
`io.github.supermonster003.autojs6.plugin.nodejs.NodeJsRuntimePluginService`,
runs in `:nodejs_runtime`, is protected by `org.autojs.permission.PLUGIN`, and
is hosted by package `io.github.supermonster003.autojs6.plugin.nodejs`.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Default `nodePlugin/plugin` arm64 host APK | 58,270,518 | `E223AB6412B27E4CBDE090B175D5BEB71AA308D83D060B386A1F3F69FCBF62E1` |
| R5 `nodeMini/pluginOptional` arm64 host APK recorded before default preflight; artifact subsequently overwritten | 97,761,579 | `798BF3A8040635563AAE3C1B3F751301974EDD1CA0C3F38BEB3B732058EE66A3` |
| R5 Android test APK | 2,915,237 | `227952ABF3544B5A81B3FE757CE9ACA4F8CE25FDDE3587624036D2A32EA850B8` |
| Plugin arm64-v8a APK | 31,330,680 | `134A7805B24F03B05ED54287589C0C588B6FCC9415684CF831D6F3F91E3142AA` |
| Plugin armeabi-v7a APK | 30,198,070 | `CD5CF2B43650CBF31A5AB5EDE8F4D0CE0BD5974696C893A48233E116E925AFD9` |
| Plugin x86_64 APK | 33,653,599 | `F9062F0518447A29DBDDEDCAB96D6E1C5120D77927C99544901044BD89EE4767` |

The x86_64 host APK was rebuilt and installed by its gate, then overwritten by
the required final Sony reruns. Its immutable gate log and UTP result hashes are
retained below rather than claiming a post-run APK hash.

## Device Matrix And Commands

| Plugin ABI | Device | Android | Device ABI list | Plugin process ABI | Role |
| --- | --- | --- | --- | --- | --- |
| arm64-v8a | Sony XQ-AT72, `QV710AF65F` | 12 / API 31 | arm64-v8a, armeabi-v7a, armeabi | arm64-v8a | Primary physical release device |
| armeabi-v7a | Sony XQ-AT72, `QV710AF65F` | 12 / API 31 | arm64-v8a, armeabi-v7a, armeabi | armeabi-v7a | Required 32-bit plugin-process contract |
| x86_64 | `AVD_API_36.1`, `emulator-5556` | API 36 | x86_64, arm64-v8a | x86_64 | Required ABI-contract emulator evidence |

The Sony fingerprint was
`Sony/XQ-AT72_CN/XQ-AT72:12/58.2.A.10.44A/058002A0100044A0891821322:user/release-keys`.
The final recorded battery level was 100%, temperature 34.7 C, low-power mode
off, and thermal status 0. The AVD fingerprint was
`google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:userdebug/dev-keys`;
its recorded temperature was 25.0 C. The AVD is ABI-contract evidence, not an
OEM or packaged-release substitute, and was shut down after collection.

Before each run the ABI-specific plugin split was installed with `adb install
-r`, and `dumpsys package` confirmed `primaryCpuAbi`. The gate command was:

```powershell
$env:ANDROID_SERIAL='<serial>'
.\gradlew.bat :app:verifyNodeR5RuntimePluginGate --console=plain `
  "-Pautojs.nodejs.test.deviceProfile=<sony-or-full>" `
  "-Pautojs.nodejs.releaseFlavor=nodeMini" `
  "-Pautojs.nodejs.backend=pluginOptional" `
  "-Pautojs.nodejs.embedded.script.enabled=true" `
  "-Pautojs.nodejs.embedded.persistentRuntime=true" `
  "-Pautojs.nodejs.releaseAbis=<arm64-v8a-or-x86_64>" `
  "-Pautojs.nodejs.pluginProjectDir=D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime" `
  "-Pautojs.nodejs.runtimeJniLibs=D:\idea-projects\AutoJs6-Plugin-NodeJs-Runtime\app\src\main\jniLibs" `
  "-Pandroid.testInstrumentationRunnerArguments.autojs.nodejs.r5.pluginWarmSamples=30" `
  "-Pksp.incremental=false"
```

The 32-bit plugin run used an arm64 host and `releaseAbis=arm64-v8a`; only the
external runtime process was replaced by the armeabi-v7a split. The x86_64 run
used `deviceProfile=full` and `releaseAbis=x86_64`.

## Live Gate Results

| Plugin ABI | Tests | Failures | Errors | Skipped | XML wall time | XML SHA-256 |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| arm64-v8a | 8 | 0 | 0 | 0 | 26.486 s | `442D494287F313475115D3FFB23C56A304D9C8B23FB305D16F9668FE2C940542` |
| armeabi-v7a | 8 | 0 | 0 | 0 | 27.274 s | `D4B0BF6F72A444544BD4B46386641AD1B9D0C073068D61626A93F80AEB7B9BED` |
| x86_64 | 8 | 0 | 0 | 0 | 21.341 s | `C57EBCA97A2E96D0CBACF792D0559C29EDCB32C67A6A3840ED49CDFFE43F9864` |

The ordered corpus proved:

1. trusted candidate identity, signer, UID, enable/authorization state,
   contract, mandatory capabilities, and process ABI;
2. candidate and Binder-session cache reuse with one persistent process runtime,
   fresh globals/env/timers for every execution, and workspace v2 read, write,
   delete, and output synchronization;
3. embedded/plugin stdout, stderr, exit, typed-error, lifecycle, shared native
   diagnostics, active cancellation, process restart, and recovery parity;
4. exactly one admitted execution, zero queue capacity, and immediate busy
   rejection for competing requests;
5. required-plugin failure without fallback, and exactly one optional
   pre-dispatch fallback marker without double dispatch;
6. contract-version and mandatory-capability mismatch rejection;
7. active Binder death attributed as
   `ERR_AUTOJS6_NODE_PLUGIN_EXECUTION_LOST`, with no replay or fallback, then one
   atomic reconnect shared by concurrent callers; and
8. 30 runtime-warm samples against the R2 p50/p95 SLO.

The workspace assertion required all of these final diagnostics:

```text
contract_version=2
mapped=true
status=committed
legacy_directory_scan_ran=false
input_directory_scan_count=0
output_private_workspace_scan_count=1
deleted_input_files=1
```

The archive contained only request-named inputs. The plugin executed below its
own private mirror, produced one validated output snapshot plus a final
tombstone manifest, and the host applied the result without enumerating its
sandbox. Invalid or incomplete commit/application after dispatch was treated as
execution loss and was never replayed.

## Runtime Contract

- Binder contract version: 1.
- Workspace archive transport version: 2.
- Runtime slot: `node24_5`; Node version: `24.5.0`.
- Mandatory capabilities: synchronous Bundle execution, native embedded
  runtime, host capability broker, live bridge, on-demand module provider,
  scoped workspace archive transport, persistent process runtime, single-active
  backpressure, and process-restart cancellation.
- Process model: dedicated persistent `:nodejs_runtime` process, one active
  execution, zero queued executions, and fresh isolate/Context/Environment,
  cwd/env, timers, providers, and module state per request.
- Cancellation model: active cancellation retires the dedicated plugin process;
  completion waiters terminate and the next request binds a new PID/session.
- Response-loss model: after Binder dispatch, execution may already have
  happened, so fallback and replay are forbidden.

## Performance Sampling

| Plugin ABI | Samples | Primer wall/discovery/bind | Warm wall p50/p95/max | Native execution p50/p95 | Service pre/native-call/workspace/bridge-stop/diagnostics p50 | Warm discovery p50/p95 | Warm bind p50/p95 |
| --- | ---: | --- | --- | --- | --- | --- | --- |
| arm64-v8a | 30 | 267 / 6 / 11 ms | 231 / 254 / 265 ms | 147 / 157 ms | 26 / 157 / 1 / 1 / 9 ms | 0 / 0 ms | 0 / 0 ms |
| armeabi-v7a | 30 | 306 / 5 / 11 ms | 240 / 265 / 266 ms | 162 / 174 ms | 23 / 171 / 1 / 1 / 9 ms | 0 / 0 ms | 0 / 0 ms |
| x86_64 | 30 | 249 / 1 / 5 ms | 182 / 212 / 215 ms | 151 / 173 ms | 14 / 157 / 0 / 1 / 2 ms | 0 / 0 ms | 0 / 0 ms |

All runs kept one plugin PID, process-runtime generation `1`, and host-session
generation `12` through the warm sequence. Every ABI met p50 <= 250 ms and p95
<= 400 ms, and every warm bind measurement was 0 ms. Empty live-bridge sessions
wake their polling thread immediately; executions with bridge traffic still
take a post-dispatch diagnostics snapshot and retain the bounded late-response
drain window.

## Focused Build And JVM Verification

- Plugin: `:app:testDebugUnitTest :app:assembleDebug` passed, producing arm64,
  armeabi-v7a, x86_64, and universal APKs.
- Host: the new backend, binding identity, execution registry, and RPC deadline
  tests passed under `:app:testAppDebugUnitTest`.
- Host: Kotlin/main and Android-test compilation passed as part of the live
  gates; every gate preflight rejected skips.
- Default `nodePlugin/plugin` packaging preflight and native-dependency report
  passed after the live corpus.

A full app unit suite was not used as the R5 acceptance signal. An earlier full
suite run in this work session still had three unrelated pre-existing
`PluginIndexRepositoryTest` `NoSuchMethodException` failures; the R5-focused
tests above passed.

## Evidence Inventory

Raw evidence remains under the ignored build tree:

```text
build/reports/nodejs/r5-acceptance/arm64-v8a/
build/reports/nodejs/r5-acceptance/armeabi-v7a/
build/reports/nodejs/r5-acceptance/x86_64/
```

Each final directory contains the Gradle gate log, UTP XML, textproto/protobuf
result, per-test logcat, device information, and performance JSON in the
`r5_08` logcat. Final gate log SHA-256 values are:

- arm64-v8a: `9AFB1F6A1FED47DF26D04DF652D3356C3C0E5931908BC4FCFE74D1A8EEFD4425`
- armeabi-v7a: `1F60F0F211C34A2696FC0BE1C0B8EEE4AF85E4DD7FB8FBBC510FF345FDD74CB8`
- x86_64: `A7DB8E539794DF86164ED4C971DA138FFDB4B54D4CDB5A2979DDEBCD8927D1FA`

Default-distribution report hashes are:

- packaging preflight:
  `702E0DCC0DA0D61105A413FC8A237578BAC563F9DB8E9E5259DFBE37AF3BCE70`
- native dependency report:
  `2FD24BF260EC321664D579757F99381DF49AD1B32FD41F1B8CBEBF11E4E99583`

The fixtures contain only synthetic script text and temporary workspace files.
No user project or credential data was collected. The headless AVD was shut
down after evidence capture.

## Roadmap Mapping

- [x] Default release flavor and backend documented and packaging-verified.
- [x] Persistent process runtime reuses process-global Node/V8/libuv state while
  recreating all execution-scoped state.
- [x] Single-active, zero-queue backpressure and active cancellation recovery
  verified.
- [x] Binder death, generation-safe invalidation, atomic reconnect, and
  post-dispatch no-replay semantics verified.
- [x] Candidate/trust and Binder-session cache invalidation verified.
- [x] Scoped workspace archive v2 preserves the zero-host-scan boundary.
- [x] arm64-v8a, armeabi-v7a, and x86_64 passed 8/8 with skipped=0.
- [x] All ABI samples met the R2 runtime-warm SLO.

This closes R5 only. R6 and the overall Node.js integration roadmap remain open.
