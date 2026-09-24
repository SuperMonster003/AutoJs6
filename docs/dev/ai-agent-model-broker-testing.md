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
