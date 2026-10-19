# 3-Stove Agent model broker checks

The P1.2 broker uses the existing Android AI Provider catalog, ask and stream runners. Its JVM
checks cover strict request admission, byte limits, rolling call limits, token reservations,
fallback usage accounting, stable error mapping and terminal event ordering:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests '*ThreeStoveAgentModelProtocolTest'
```

P9.2 adds image admission, independent quotas, capability/version checks, native
image-result ownership and image-token accounting. Run `*ThreeStoveAgentModelVisionTest`,
`*AiProviderVisionSelectionTest` and the Provider API suite alongside the existing
tests. The Android broker class includes synthetic JPEG/PNG round trips and
negative/cancellation cases. `AiProviderVisionCompatibilityAndroidTest` additionally
requires `-e autojs.aiProvider.expectedMinor 0` or `1`, matching the explicitly
installed old/new fake APK. Use a fresh host process for fault cases. Actual
2026-09-26 outcomes and fixture boundaries are recorded in the
[P9.2 host receipt](evidence/ai-agent-p92-host-20260926.md).

Device checks use the same-signature `test-apps:ai-provider-conformance` APK. They are opt-in and
do not contact a real model or network. Install the host, host androidTest and fake Provider APKs
from the same build, then run on the intended device:

```powershell
adb -s <serial> shell am instrument -w -r -e class org.autojs.autojs.core.plugin.agent.ThreeStoveAgentModelBrokerAndroidTest -e autojs.threeStoveAgent.model.conformance.enabled true org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

The public fake targets `local:echo` and `local:stream` pass normal catalog validation. Two exact
prompt fixtures exercise failure paths that the older, unlisted hostile targets cannot reach
through the catalog-validating production ask runner:

- `local:stream`, stream enabled, `agent-conformance:stall`: emit a readiness chunk, then wait
  for cancellation or the request deadline. Tests cancel only after observing that chunk.
- `local:echo`, `agent-conformance:fail`: fail with provider-private diagnostic text. The Agent
  must receive only `MODEL_FAILED` and the stable `PROVIDER_FAILED` reason enum.

Other prompts retain their existing fake-provider behavior. The checks also cover a structured
JSON round trip, catalog field filtering, unsupported target capabilities, caller UID rejection,
and a closed link. Payload-pipe checks verify that cancellation bypasses an unrelated blocked
reader, and that a request can be cancelled before any payload bytes arrive. Descriptor requests
carry an inline `modelRefJson` request ID (at most 512 bytes), checked against the eventual body;
missing or mismatched references are rejected. A checked-in test is not device execution evidence; record the actual device,
API level and result in the Agent roadmap only after running it.

### Android 7-10 payload compatibility, 2026-09-25

`Os.fcntlInt` is a public SDK API only from API 30. Older supported devices now
open the accepted file/pipe through their own `/proc/self/fd` with `O_NONBLOCK`;
regular-file offsets are preserved, and reopen is serialized with descriptor
cancellation to avoid numeric-fd reuse. API 30+ keeps the existing public fcntl
path. A failed reopen is rejected through the existing bounded error path; there
is no hidden-API reflection or blocking-read fallback. The close-on-exec flag
uses the shared `OsConstantsCompat.O_CLOEXEC`: its Linux UAPI value is `0x80000`
(NDK `asm-generic/fcntl.h`: octal `02000000`), while Android's SDK field is public
only from API 27. This preserves the flag atomically in `open` on API 24-26.

`fileAndPipePayloadsCompleteAndPreserveTheFileOffset` covers 40 KiB input through
both transports, including a file whose current offset is after a binary prefix.
Existing tests cover stalled pipes, cancellation before body bytes, unrelated
active-request cancellation, mismatched references and descriptor cleanup.

On 2026-09-25, the model broker suite passed 9/9 on API 24 x86 (8.170s) and
API 37.1 x86_64 / 16 KiB pages (9.885s). No real Provider/model/network was used.
The fake Provider's teardown now iterates its concurrent session set directly;
the previous `toList()` single-element shortcut could throw when a completing
session removed itself concurrently. The Provider JVM suite passed 32/32.

Keep test-environment failures distinct: the first run had a debug/host signing
mismatch, so its six identity failures did not enter model execution. Use a
private same-signature copy of the fake APK when the host has a custom signing
key, and verify signatures before execution. API 24 then exposed the teardown
race above; after its fix, an additional transient failed completion was retained
separately and the complete unchanged rerun passed. The fixture is debug/testOnly
and was installed only on the two test AVDs.

The initial complete strict host lint report (9m 24s) contained 552 errors,
including two `NewApi` reports on the Agent fcntl call. The post-fix lint attempt
stopped in Kotlin compilation when deleting a classes directory, alongside other
workspace changes. These device checks and the successful host/test APK build
do not assert that whole-repository lint is now clean. No unrelated lint finding
or concurrent workspace modification is included in this payload change.

The Provider runner exposes fixed error categories without backend response bodies. Since the
2026-09-26 follow-up, a PROVIDER_FAILED envelope may retain a host-allowlisted ONLINE_*
category from the existing optional providerCode field. Unknown codes still produce the generic
PROVIDER_FAILED reason. ONLINE_REQUEST_REJECTED identifies a request rejection, not proof of a
schema error; it does not trigger the Agent's bare REQUEST_REJECTED fallback.

## Safe online failure-category follow-up, 2026-09-26

The decoded onFailed envelope retains only AiPluginProviderFailureReason, never the raw
providerCode or message. AiProviderSessionErrorSummary carries this typed value to the ordinary
Attempt path; ask, stream and native-tool dispatch preserve it through ThreeStoveAgentModelBroker.
Persistent sessions use the same allowlist. Each path retains its historical TIMEOUT mapping,
while local deadlines and cancellation remain distinct. Public JS ask errors, model error codes,
retry behavior and published AIDL/SDK contracts are unchanged.

Related JVM checks passed 72/72: AiPluginProviderFailureReasonTest, AiProviderSessionTest,
ThreeStoveAgentModelProtocolTest and AiPluginAskRuntimeTest. The encoded callback test exercises all 17
known codes plus unknown codes with text/stream/tools combinations. On QV770340J7 / XQ-DQ72 /
Android 13 / API 33, AiPluginAskFailureDispatchAndroidTest passed both methods against the
matching host Debug and androidTest APKs. It uses the real private Attempt dispatch without
starting discovery, binding or model execution, covering ASK, STREAM, TOOLS and STREAM_TOOLS,
duplicate terminals and resource release. Run it with:

```powershell
adb -s <serial> shell am instrument -w -r -e class org.autojs.autojs.core.plugin.ai.AiPluginAskFailureDispatchAndroidTest org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

A separate bounded real AiGoCode Wi-Fi diagnostic then returned MODEL_FAILED with the wire
reason ONLINE_NETWORK_UNAVAILABLE after the UI action enabled Wi-Fi. Its 4 model calls,
3 tools, 16974 input / 174 output tokens and 24053 ms are failure evidence, not task acceptance.
The existing Agent final-result schema remains unchanged; use the E4 model events to inspect
this diagnostic category. The code alone does not distinguish a transient network-policy failure
from an IOException or establish a VPN or connection-pool root cause.

## Agent caller grant matrix

`ThreeStoveAgentGrantConformanceTest` uses the independent
[`three-stove-agent-conformance` APK](../../test-apps/three-stove-agent-conformance/README.md) as
the actual caller. Unlike the provider integration tests above, it injects
deterministic catalog/generation runners into the production model broker, so
input, call-count and token limits can be reached without a real Provider,
account or network. Both request and callback Binder transactions cross UIDs.
The suite also verifies capability grants, all eight broker UID guards, host
log exclusion of private bodies and non-Agent attach broadcasts.

## P9.1 real Agent plus fake Provider

`ThreeStoveAgentNativeProviderRoundTripTest` binds the installed, independently built
Agent plugin from the actual host UID. Its model broker is the production
`ThreeStoveAgentModelBroker`, using the separate same-test-signer fake Provider; device
observations use the production capability broker. Four cases verify a two-call
native batch, whole-batch argument rejection before dispatch, refused clipboard
confirmation without a write, and cancellation while the Provider waits for
confirmation. Request/round/call IDs, same-session continuation, two model-call
admissions and native journal entries are asserted. No network is used.

Build the real host, its androidTest APK and the fake Provider from an isolated
checkout with a test signer. Use an independently test-signed Agent APK (the R8
release is supported by this external host instrumentation). Never sign a fake
Provider with a production key or install it on a physical device. Run:

```powershell
py test-apps/three-stove-agent-conformance/run_native_provider_matrix.py --adb <adb.exe> --serial <emulator-serial> --output <new-private-evidence-directory>
```

The driver accepts only a disposable `AI_Agent_Conformance_*` AVD containing the
real host and runs each of the four integration and fourteen model-broker cases
in a fresh host instrumentation process. Some deadline/hostile cases can fuse
the Provider for the life of the host process. Keeping healthy cases in that
process creates order-dependent `RATE_LIMITED / FUSED` failures; do not disable
or reset production fuse checks to make a combined run pass. Logs are retained
per case and a zero adb exit code alone is never treated as success.

On 2026-09-25, these 18 cases passed on API 24 x86 and API 37.1 x86_64 / 16 KiB
using the actual R8 Agent build 82. API 24 also passed a combined invocation in
15.912 s. API 37's combined invocations exposed the process-fuse interaction;
its final per-case matrix passed 18/18. Those earlier failures remain in private
evidence, separately from successful execution. This is deterministic contract
evidence, not a successful cloud Wi-Fi or calculator task.

`ThreeStoveAgentRealModelE4Test` also accepts an opt-in evidence configuration field
`modelPath: "json"`. It hides only native-extension negotiation from that test
link, allowing the same installed host, Agent, Provider, target, tool grants,
goal and budgets to exercise JSON decisions. `"auto"` keeps production
negotiation. Production settings and public JS APIs have no new mode switch.
Native tool events and continuation timestamps are retained in the private E4
evidence alongside outcomes; failed tasks must not be counted as successes.
