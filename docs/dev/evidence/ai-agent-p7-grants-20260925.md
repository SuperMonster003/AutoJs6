# AI Agent P7 host grant matrix

Scope: the original P7 host grant boundary item. The test driver is a separate debug-only APK, under a separate UID and `:agent` process. It is protected by the host's signature permission and a package/signer check on each driver call. Its worker calls the real production capability and model Binder stubs; the host checks the driver's UID again on relayed callbacks. This avoids counting local Binder calls as cross-process evidence.

The capability provider is real for permission enforcement, accessibility query rate limiting and toast logging. A counting/stalled provider makes pre-dispatch rejection and concurrency deterministic. Model catalog/generation runners are injected into the real model broker, with controlled usage, so quota cases require no model download, account, credentials or carrier network. The original public JS/AIDL contracts and API AARs are unchanged.

## Matrix

| Case | Required result |
| --- | --- |
| Method outside grant | `ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED`, category `capability-denied`; provider not called |
| Extra permission token | `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED`; provider not called |
| Omitting permission declarations | Actual Android provider rejects with `ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED` |
| Grant byte ceiling / multibyte UTF-8 / 512 KiB global ceiling | `ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`; provider not called; valid subsequent request succeeds |
| Two accessibility queries within a one-query/second grant | `ERR_AUTOJS6_BRIDGE_RATE_LIMITED`; a query after the window succeeds |
| Pending-call concurrency ceiling | `ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT`; revocation terminates the pending request and later calls with `ERR_AUTOJS6_BRIDGE_PROCESS_DEAD` |
| Foreign UID for capability info/dispatch/destroy and model info/list/generate/cancel/destroy | All eight throw `SecurityException`; brokers stay usable by the correct owner |
| Model input UTF-8 bytes exceed the grant | `QUOTA_EXCEEDED` before catalog/generation; exactly bounded input succeeds |
| Model calls/minute exceeded | `QUOTA_EXCEEDED`; no extra generation |
| Token reservation exceeds remaining budget | `QUOTA_EXCEEDED` before generation |
| Usage exactly consumes the token grant | Accepted completion; later generation rejected |
| Provider-reported usage exceeds the grant | `QUOTA_EXCEEDED`; output text withheld |
| Successful and rejected model/capability bodies | Private test marker absent from host logcat |
| Same-signer non-Agent broadcast with missing, mistyped or foreign-created identity | Delivered to the production manifest receiver and rejected; no extra/body text in the rejection log |

## Reproduced defect and fix

The first device run executed nine tests in 2.063 s. Eight passed; the log-privacy case failed because a granted `toast.showToast` request with `log: true` copied its body into `NodeBridgeProtocol`'s host log. This is an actual pre-fix failure, not a skipped check.

The Android provider now uses the host-owned `engineInfo.backend == "plugin"` context to log only UTF-8 byte count and duration for plugin toast requests. Toast display is unchanged, and direct Node.js scripts retain explicit text logging. The attach receiver emits a fixed identity-rejection diagnostic without extras or exception text. This also lets the ordered-broadcast test verify the real receiver's rejection branch rather than treating delivery completion alone as proof.

Host version: 6.8.0 / 5295. The fix and its 10-language changelog belong to the host. The Agent repository only records this roadmap result; the host Rhino synchronization work is untouched.

## Verification

- JVM: 23/23 passed, no failures/errors/skips: `HostCapabilityGrantTest` (2), `HostCapabilityDispatchQueueTest` (4), `AiAgentGrantTest` (3), `AiAgentModelProtocolTest` (14).
- Fixed privacy case: 1/1 passed in 0.353 s on the same emulator after installing host 5295.
- Final Android suite: 14/14 passed in 1.986 s on API 37.1 / 16 KiB pages: nine grant-matrix cases plus five existing `HostCapabilityBrokerStubTest` cases. The final matrix checks exact capability error codes and categories, not just failed responses.
- Host debug APK, instrumentation APK and conformance APK assembled. Host native libraries passed 16 KiB alignment verification. No runtime dependency or release/R8 change was needed.
- Conformance APK lint: 0 errors, 2 warnings (`DataExtractionRules`, `MissingApplicationIcon`, for the test-only application). The first full host lint attempt had an Android-test analysis failure and its other analyses were still active after nearly 10 minutes; this session's single-use daemon was stopped. An isolated serial Android-test analysis then passed in 1m 27s, without source suppressions. The full serial host lint result is recorded below.
- Both copies of the test control AIDL are byte-identical. The host changelog renderer was reused without refreshing unrelated online metadata. The public production contract modules and Agent API AARs are untouched.
- AVD `AVD_API_37.1_16K` was started with no window or snapshots, without wiping user data. Before closing it, the test driver APK was uninstalled and all three captured settings were restored and verified: screen timeout 2147483647, the original AutoJs6 accessibility component, and accessibility_enabled=1. No real device was operated on. Host 5295 and its test APK were installed only in this AVD.

Full serial host lint remains incomplete. A second, single-worker invocation was still in `:app:lintAnalyzeAppDebug` after 10.52 minutes, without a complete report, and this session stopped its own single-use daemon. The resulting "daemon disappeared" message records that deliberate stop, not an Android app crash. No lint rule, source set or source warning was suppressed. The isolated Android-test analysis passed, but that does not establish a clean whole-host lint report or pass the P7/P8 gate. Final packaging is checked separately from this unfinished broader analysis.

Ignored local reports: `build/p7-grant-initial-avd37.log`, `build/p7-grant-privacy-fixed-avd37.log`, `build/p7-grant-final-avd37.log`, `build/p7-grant-fixture-build.log`, `build/p7-grant-verified-build.log`, `build/p7-grant-lint-isolated.log`, `build/p7-grant-serial-final.log`, `build/p7-grant-packaging-final.log`, and `build/p7-grant-avd-baseline.json`. Manifest inspection confirms the fixture is `testOnly`, has only the PLUGIN permission, and contributes no component to the host APK. The pre-device fixture compilation corrections (test-only AIDL Binder handles and explicit nullable usage fields) and the corrected Kotlin charset reference are not counted as passing builds.

The original P7 full fake Agent attach/startRun/hostile-callback scenario and plugin-side fake host APK remain open. This module currently supplies the independent grant driver; its existence alone does not complete that later item or P1.3's outstanding attach/detach/death evidence. P7 lifecycle, performance, power, six-device compatibility and release gates are separate. No E4 model acceptance, real order, payment, push or publication is part of this matrix.
