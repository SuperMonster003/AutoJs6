# AI Agent model broker checks

The P1.2 broker uses the existing Android AI Provider catalog, ask and stream runners. Its JVM
checks cover strict request admission, byte limits, rolling call limits, token reservations,
fallback usage accounting, stable error mapping and terminal event ordering:

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests '*AiAgentModelProtocolTest'
```

Device checks use the same-signature `test-apps:ai-provider-conformance` APK. They are opt-in and
do not contact a real model or network. Install the host, host androidTest and fake Provider APKs
from the same build, then run on the intended device:

```powershell
adb -s <serial> shell am instrument -w -r -e class org.autojs.autojs.core.plugin.agent.AiAgentModelBrokerAndroidTest -e autojs.aiAgent.model.conformance.enabled true org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
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

The existing provider runner exposes `AiPluginAskFailure` without backend response bodies. Thus
the Agent can forward its stable enum but cannot distinguish an HTTP schema rejection from other
`PROVIDER_FAILED` errors until the upstream provider error contract is extended.

## Agent caller grant matrix

`AiAgentGrantConformanceTest` uses the independent
[`ai-agent-conformance` APK](../../test-apps/ai-agent-conformance/README.md) as
the actual caller. Unlike the provider integration tests above, it injects
deterministic catalog/generation runners into the production model broker, so
input, call-count and token limits can be reached without a real Provider,
account or network. Both request and callback Binder transactions cross UIDs.
The suite also verifies capability grants, all eight broker UID guards, host
log exclusion of private bodies and non-Agent attach broadcasts.
