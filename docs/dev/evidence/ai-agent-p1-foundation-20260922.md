# AI Agent P1 host foundation evidence

Date: 2026-09-22. Scope: AI Agent roadmap P1.1, P1.2 and the host infrastructure
in P1.3. This is a development milestone, not the P1 integration gate or a plugin
release. The original roadmap stages and fixed decisions are retained.

## Changes

- Two shared API modules freeze the Agent control/model surface and the shared
  capability data plane. Exported AIDL source packaging is verified in the AAR.
- MCP V2 appends `openServerV2`; V1 transaction numbers, wire keys and V1 request
  envelopes remain compatible. Both capability adapters use one host core.
- The model broker uses existing Provider catalog/ask/stream runners, enforces a
  per-link grant and UID, filters catalog fields and propagates bounded events.
- Shared capability dispatch has bounded concurrency, deadlines, grant evaluation,
  descriptor transport and late-result cleanup. Method/permission violations keep
  `capability-denied`; byte/concurrency limits keep `resource-limit`, preserving
  MCP's established error categories.
- Attachment uses an exact plugin identity, dedicated binding lease, revocable
  model/device brokers, stale-callback rejection and bounded reattachment after
  process death. It never replays a task. Broadcast receiver registration and user
  guidance remain in P1.5.

## Review findings resolved before validation

1. AIDL interfaces must be explicitly exported from the shared AAR for dependent
   AIDL compilation; declaring only a Gradle `api` dependency is insufficient.
2. Updating the MCP control version constant must not send V2 envelopes to a V1
   plugin. Open transactions and envelope versions are selected together.
3. An unread model pipe needs a bounded `modelRefJson` request-ID header so cancel
   can identify it before receiving its body. Reading is cancellable and cannot
   block cancellation of another in-flight operation.
4. Host-unavailable or terminal link callbacks revoke both brokers; old generations
   and wrong UIDs cannot alter a replacement link.
5. Rejected, malformed and callbackless input Bundles close unexpected descriptors.
   Rejected method names are not copied into ordinary logs.

## Validation

The initial complete host build used Temurin 21 and passed
`:app:testAppDebugUnitTest`, `:app:assembleAppDebug`,
`:app:assembleAppDebugAndroidTest` and both conformance APK builds. JVM result:
3,124 tests, 0 failures/errors, 5 conditional skips. The contract modules passed
20 tests in total, including the shared AAR packaging guard.

The final host build is 6.8.0 / 5283. The same complete JVM suite passed again
after review fixes: 3,124 tests, 0 failures/errors, 5 skips. Debug and androidTest
APKs, 16 KiB native alignment, all three contract suites and the shared AAR
packaging check passed. Runtime product code was unchanged after these checks.

Android evidence uses a private read-only `AVD_API_37.1_16K`, Android API 37,
x86_64, serial `emulator-5584`:

| Suite | Passed | Coverage |
| --- | ---: | --- |
| AiAgentModelBrokerAndroidTest | 8 | Real fake-Provider catalog/structured JSON, cancellation, timeout, sanitized failure, UID/destroy guards, unread-pipe cancellation and unexpected FD cleanup |
| AiAgentLinkControllerTest | 8 | Android Binder link lifecycle, stale callbacks, UID, revoke-on-loss and bounded rebind without task replay |
| HostCapabilityBrokerStubTest | 4 | Decode/grant/UID, destroyed broker, bad Bundle/FD cleanup and large JSON descriptor transport |
| McpServerAndroidConformanceTest | 6 | Separate MCP fake APK, metadata, malformed/oversized payloads, duplicate callbacks and process death |
| McpServerPluginRoundTripTest | 2 | Installed MCP 1.0.2 / 67 (contract V1) open/run/stop/close, plus forced AIDL V1/V2 transaction and envelope selection |

All 28 cases passed with no skips. The first four suites ran as one 26-case
invocation (19.244 s); the final suite ran separately (0.951 s).
After extending link callback cleanup to descriptors in unknown or mistyped
fields, the full host JVM suite and debug/androidTest builds passed again.
The eight link lifecycle cases were then repeated against the rebuilt APKs:
8 passed, no skips, 2.362 s. This is a repeat of the link suite above, not eight
additional distinct cases.

The first device attempt correctly rejected the fake APKs because they used the
default test signer while the host used its normal development signer. The MCP
run also required Android 17 local-network permission before enablement. After
granting that permission in the temporary emulator, the real MCP V1 test passed.
For fake-APK conformance, only disposable host/test APK copies were re-signed with
the default debug key to match the fake APKs. No production key was used to sign
the fake apps, and no repository signing configuration was changed. These are
artifact copies, not a separate source checkout. The production-signer and
test-signer results are recorded separately rather than weakening signer checks.

Ignored raw logs: `build/ai-agent-host-tests.log`, `build/ai-agent-host-final.log`,
`build/ai-agent-contract-tests.log`, `build/ai-agent-mcp-v1-device.log` and
`build/ai-agent-device-test-signed.log`, `build/ai-agent-link-fd-final.log` and
`build/ai-agent-link-fd-device.log`. No release/R8 build, full lint run,
physical-device matrix, real-model task or E4 task was performed in this host
foundation milestone. Their original roadmap gates remain open.

## Deliberately outstanding roadmap work

- P1.4 bridge compact/nodeRef, OCR observation, registered script catalog/parser
  and structured script-result channel.
- P1.5 drawer/plugin-center registration, broadcast registration/guidance and
  enablement integration. The receiver class is not currently exported.
- P1.6 final host minimum version and remaining protocol/changelog integration.
- The P7 separate AI Agent conformance APK and its cross-process attachment,
  sender-authentication and process-death matrix. Current lifecycle tests use an
  injected Android Binder link; they are not evidence for that separate APK.
- Plugin Agent loop, real model tasks, UI, JS API and release work remain in P2-P8.

No network model or credentials were used for conformance checks. The existing
Provider runner exposes stable failure enums, not backend HTTP rejection details;
the broker cannot invent a more specific Schema rejection reason. Production
links currently select the official 3-Stone Provider. Broader Provider selection
must be integrated through host configuration without exposing credentials.
