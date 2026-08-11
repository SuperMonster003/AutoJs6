# Compiler And AI Protocol Conformance

This matrix separates checks that are complete in the repository from checks
that require an installed provider, multiple Android API levels, or a real
production engine. A static pass is not a device-runtime claim.

The production AI and DEX providers are maintained as independent sibling
projects and are not source dependencies of this repository. The applications
under `test-apps` deliberately retain fake package identities and hostile
behavior only for reproducible host conformance testing.

## Current Repository Gates

| Area | Gate | Evidence | Status |
| --- | --- | --- | --- |
| Shared wire | Canonical order, positive wire types, strict UTF-8 and scalar shapes, limits, optional skip, required reject | `TaggedWireTest`, 14 tests | PASS, JVM |
| DEX API | Codec round trips, golden bytes, minor evolution, incompatible major, malformed and quota cases | DEX API unit corpus, 24 tests | PASS, JVM |
| DEX host | Exactly-once callback lifecycle and cancellation rules | `DexCompilerSessionLifecycleTest` | PASS, JVM |
| DEX host | At most one plugin dispatch and one local fallback, with no cancellation fallback | `DexCompilerFallbackControllerTest` | PASS, JVM |
| DEX host | Semantic cache identity includes policy, compiler, runtime libraries, options, and provider provenance | `DexCompilerSemanticCacheKeyTest` | PASS, JVM |
| DEX host | ZIP and DEX structural validation, including target-aware DEX versions, pre-inflate declared quotas and ratios, and cross-DEX duplicate classes | `DexIndexedZipValidatorTest`, 12 tests | PASS, JVM |
| DEX host | Exact component/UID/full-signer policy, identity drift, bounded ingress, reliable-pipe transfer, dual summaries, abort/publication commit arbitration, no retry, rollback, cleanup, and late callback handling | Experimental adapter corpus, 94 tests total across host policies | PASS, JVM |
| DEX host | Android transport and artifact-store source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |
| DEX conformance app | Deterministic fixed DEX ZIP, strict bounded JAR framing/hash validation, process-wide single-session gate, explicit hostile services, and single-writer behavior | Conformance compiler unit corpus, 25 tests | PASS, JVM |
| DEX conformance app | Android service source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |
| AI common | Provider, error, usage, payload reference, version negotiation, malformed and evolution corpus | `AiCommonCodecTest` | PASS, JVM |
| AI text | Capabilities, requests, chunks, reasoning, usage, tools, pages, malformed and evolution corpus | `AiTextCodecTest` | PASS, JVM |
| AI lifecycle | Credits, contiguous sequence, single terminal, cancellation, declared tools, verified JSON, aggregate stream digests, tool exactly-once, and cumulative quotas | Lifecycle and quota unit corpus | PASS, JVM |
| AI models | Page token replay, scope, cycle, generation drift, and cross-page duplicate model IDs | `AiModelPageTokenPolicyTest` | PASS, JVM |
| AI protocol | Shared wire, common values, immutable snapshots, redacted summaries, golden wire, text codecs, lifecycle, JSON, quotas, usage, and pagination | Combined protocol/API corpus, 103 tests | PASS, JVM |
| AI host | Default-off trust selection, ambiguity rejection, identity revalidation, capability planning, credential isolation, collision-resistant binding keys, safe diagnostics, and terminal race ownership | Pure host policy corpus, 31 tests | PASS, JVM |
| AI host model list | Default-off exact-component coordination, same-Binder metadata pinning, per-dispatch and callback reinspection, UID/size-before-copy ingress, bounded flood admission, per-provider token isolation, single-flight/fuse/deadline ownership, and terminal cleanup | Standalone K2/JVM 17 compilation plus JUnit model-list coordinator corpus, 15/15 tests | PASS, JVM |
| AI host session transcript | Default-off plan/request admission, provisional callbacks around synchronous open, UID/size-before-retention ingress, bounded credits and callback buffering, descriptor ownership, two-stage terminal and late-remote cleanup, cancel/timeout/death races, executor failure, fatal drain, and summary-only output | Commit `325577c82`; standalone Kotlin K2/JVM 40/40, 30 repeated rounds 1200/1200; focused `:app:testAppDebugUnitTest` XML: 40 tests, 0 skipped, 0 failures, 0 errors | PASS, JVM/host unit only |
| AI host session coordinator | Default-off exact-component coordination, four exact inspections, same-Binder bounded metadata revalidation and open, callback-before-return commit, absolute deadline, operation leases, component fuse, gate/terminal settlement, connected-disconnect death classification, and no retry after dispatch | Commits `4f22dc854`, `038728f03`, H1 instrumentation `06ebc788c`, and H2 production/test commits `e5bd92b16` / `0b9a94742`; focused `AiTextProviderSessionCoordinatorTest`: 18 tests, 0 skipped, 0 failures, 0 errors | PARTIAL, JVM/host unit and Android build plus one API 31 normal, seven H1, and two H2 method runs |
| AI explicit ask route | Present-key fail-closed routing, exact component/provider/model pins, strict prompt-only mapping, no vault/HTTP/fallback, process/per-script lifecycle ownership, and stable terminal codes | Standalone K2/JUnit 15/15; focused Gradle 19/19 (8 parser + 3 mapper + 4 runtime + 4 service), 0 skipped/failures/errors; Android main source compiled | PASS, JVM/source only; no production device result |
| AI conformance app | Echo, stream, reasoning, tools, usage, pagination, hostile hidden models including isolated callback UID and provider process death, bounded executors, FD cleanup, and caller authentication | H1 fixture commit `edd10008f` and H2 fixture commit `10dad3e39`; conformance text provider unit corpus: 31 tests, 0 skipped, 0 failures, 0 errors | PASS, JVM; Android hostile results remain PARTIAL below |
| AI conformance app | Android service source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |

### Explicit `ai.ask` Plugin Route Source Evidence

The production `AiService` now chooses the plugin path only when the request
options contain the `plugin` key. A missing key leaves legacy cloud ask/chat/
stream behavior unchanged. A present key, including a null or malformed value,
never consults the vault or HTTP path and never falls back. Admission requires
one exact component package/class, provider ID, model ID, and an optional
integer timeout from 1,000 through 600,000 milliseconds; the default is
120,000. Cloud control aliases, explicit streaming, and unknown fields fail
closed.

The request mapping admits exactly one plain-text user message. It creates one
non-streaming inline request with zero request descriptors and no reasoning,
structured output, usage, tools, or tool rounds. The composed Android runner
reuses the exact-component discovery, handshake, model-list, and session
coordinators, requires an `ON_DEVICE`/`NONE` provider and the exact planned
model, and applies one outer absolute deadline from discovery through the active
session terminal. Lower handles and coordinators retain settled cleanup
ownership. A process-shared backend owns one ask admission gate, serialized
orchestration/control/terminal/deadline lanes, a
bounded three-operation blocking pool, and the existing coordinator process
owners. Each script runtime owns cancellation and handle close; terminal and
late-callback races have one visible winner and no post-dispatch retry.

The standalone K2/JUnit gate passed 15/15 tests: 8 option-parser, 3 mapper, and
4 runtime lifecycle cases. A later focused Gradle run passed the final 19/19
corpus--those 15 plus 4 `AiService` integration cases--with 0 skipped, 0
failures, and 0 errors, while also compiling Android main source. That compile
is an Android source-compatibility gate only, not Binder, APK, native, device,
or production-model evidence.

No real plugin/model, install, ADB/device execution, UI selector,
plugin-backed `ai.chat` or `ai.stream`, remote catalog-generation proof, or
package update, disable, or uninstall during an active routed request was
exercised. Earlier fake-provider Android cells below remain separate evidence;
they cannot be promoted to this production route by inference. This narrow
source gate closes no broad Android or production promotion criterion.

### Pure Kotlin AI Session Transcript Evidence

Commit `325577c82` adds `AiTextProviderSessionPolicy` and its 40-test unit
corpus. The policy is default-off, transport-neutral, and unwired. Its abstract
descriptor and callback interfaces are not an Android `ParcelFileDescriptor`
or generated `IAiTextCallback` adapter, and it does not invoke
`IAiTextProvider.openSession`. The standalone Kotlin K2/JVM gate passed 40/40;
30 repeated rounds passed 1200/1200. A focused Gradle
`:app:testAppDebugUnitTest` run for `AiTextProviderSessionTest` recorded
40 tests, 0 skipped, 0 failures, and 0 errors in its XML.

No Android instrumentation, device, or ADB run was performed for this slice.
There is no production provider/model session, UI/runtime adapter, or `ai.*`
entry point in this path. The standalone and Gradle results are therefore
JVM/host-unit evidence only; they do not advance Generated AIDL transact/PFD,
AI Binder corpus, streaming death, or public AI integration acceptance.

### Default-Off Android AI Session Transport

Commits `4f22dc854`, `038728f03`, and `a22ed8b75` add the default-off session
coordinator, Android Binder/PFD adapter, and opt-in instrumentation. There is no
production call site. An explicitly enabled run checks the locally pinned plan
and package, performs four exact package inspections, binds one actionless exact
component, verifies `IAiTextProvider`, and reads bounded provider info and
capabilities from the same Binder later used for one synchronous
`openSession`. The final exact inspection occurs immediately before dispatch.
The returned `IAiTextSession` descriptor and death recipient are verified
before the remote is committed; callback-before-return remains provisional.

The generated callback stub captures the Binder calling UID before forwarding.
The transcript policy applies pinned-UID, event-specific envelope, descriptor
count, and strict typed bounds before decode. Request PFDs remain host-owned and
are borrowed only for synchronous open. Callback PFDs cross the async boundary
through exactly-once transfer of the complete `ParcelFileDescriptor` objects,
not a data-FD-only `dup()`, preserving the reliable-pipe communication FD.
Null elements close all valid peers and become an invalid-count policy failure.
Exact reads require declared length, EOF, and reliable producer-error checking.

Eight credits are granted after open commit and replenished one at a time only
after delivery. Cancel, timeout, Binder death, control failure, callback
terminal, and completion have one terminal owner. Binding, descriptors, remote
controls, death recipients, policy settlement, and the process gate release
before publication. A blocking Binder/PFD/control operation that crosses the
absolute deadline fuses the component and keeps the gate busy until unwind. No
dispatch or control is automatically retried.

The locally pinned plan includes `listingGeneration`, but provider metadata and
`openSession` expose no current catalog-generation proof. Same-Binder metadata
and final package inspection cannot remotely revalidate that value or the model
record. This remains a promotion blocker.

The focused coordinator Gradle run recorded 18 tests, 0 skipped, 0 failures,
and 0 errors. The host App Debug, App Debug instrumentation, and fake-provider
Debug APKs also assembled. This build/JVM evidence remains separate from the
API 31 device evidence below.

H1 commits `edd10008f` and `06ebc788c` add the hostile provider modes and their
opt-in Android instrumentation. The H1 build evidence independently retained
the coordinator 18/0 result, recorded fake-provider unit 31/0, and assembled all
three APKs. The method-level API 31 evidence and its limits are recorded below.

H2 production transport commit `e5bd92b16`, fake-fixture commit `10dad3e39`,
and host-test commit `0b9a94742` add connected-disconnect death classification,
an exact-bound isolated-UID callback relay, a causally acknowledged
provider-process-death fixture, and final-reinspection JVM cases. The focused
H2 Gradle XML again recorded coordinator 18/0 and fake-provider unit 31/0 and
assembled all three APKs. The two method-level API 31 results and their narrow
evidence boundary are recorded separately below.

## Android Runtime Gates

These gates intentionally remain open. They must not be inferred from a JVM or
APK assembly pass.

| Gate | Required matrix | Current status |
| --- | --- | --- |
| Generated AIDL transact behavior and descriptor ownership | API 24, 25, 26, 28, 34, 36 | PARTIAL: API 25 DEX descriptor handoff/rollback plus API 31 AI cross-process request PFD, ordinary-pipe completion-PFD materialization, and tool-PFD handoff/rejection cleanup; reliable callback-PFD status is process-local only |
| Caller UID, signature permission, same-signer policy, and exact component revalidation | Debug conformance install on isolated devices | PARTIAL: positive paths on API 25 and API 31 plus one API 31 exact-bound isolated-process callback rejected as `CALLBACK_UID_MISMATCH`; no multi-API or installed-package identity-drift matrix |
| Cancellation, timeout, callback death, provider death, host death, update, disable, and uninstall cleanup | API 24, 28, 34, 36 | PARTIAL: API 31 cancel-after-`Started` released the owner and one causally acknowledged fake-provider kill published one `BinderDied`; host death, timeout, update, disable, and uninstall-during-session remain pending; final-reinspection drift/missing-package cases are JVM only, and API 25 stall timeout/rollback are supplemental |
| DEX normal and hostile services against the real host adapter | API 24, 25, 26, 28, 34, 36 | PARTIAL, API 25 only: normal, progress-before-start, duplicate-terminal, malformed output, unnegotiated DEX version, and stall-timeout services |
| Validated DEX execution through the real `DexClassLoader` | Java and Kotlin corpus on API 24 through 36 | PARTIAL, API 25 fixed DEX 037 fixture only: class load and `answer() == 42` execution |
| D8 parity for default/static interface methods, missing libraries, and device boot libraries | Current local compiler versus production plugin | PENDING |
| AAR normalization and resource behavior | Current local path versus production plugin | PENDING |
| Cold/warm latency, host/provider RSS, output size, FDs, and temporary storage | Representative physical devices | PENDING |
| AI exact-component metadata, model-list, and session positive path | Dedicated debug conformance install with explicit serial, signer/hash preflight, per-method result, and reversible cleanup | PARTIAL, API 31/arm64-v8a metadata, paged model list, one normal `fake.stream` session, and ordinary-pipe completion materialization |
| AI conformance provider normal and hostile Binder corpus | API 24, 28, 34, 36 | PARTIAL, API 31 only: seven H1 methods plus two H2 methods cover completion/tool descriptors, ordering, sequence, descriptor reference, bounded duplicate terminal, cancel-after-start, one isolated wrong UID, and one provider death; deterministic Android no-credit and update/disable/uninstall lifecycle remain pending |
| AI streaming backpressure and tool turns under provider/host death | API 24, 28, 34, 36 | PARTIAL, API 31 normal stream crossed 8 initial credits, a tool callback was rejected without execution, and one provider kill followed a chunk whose credit was causally replenished; real tool turns, host death, and the matrix remain pending |
| Provider-native response compatibility | Each proposed migrated provider | PENDING, no V1 claim |

### API 31 AI Partial Evidence

#### Metadata And Model-List Run

The 2026-08-10 positive-path run used `QV710AF65F` (API 31,
`arm64-v8a`). Its source was built in an isolated worktree from baseline
`54ae482cb2654734b850926e7fca5252901eab3e` plus the four-file model-list
patch. That exact content was subsequently committed without source changes as
`19a82f22ff071a1d013be63f8f0c9f8c8a6a570d` (`19a82f22f`). The four source
file SHA-256 values were:

- `b74c59d9f0cefabeda2ad99fbc42117525ba090fa3ead9ed6ae7f1bcc0df05cc` --
  `AiTextProviderModelListingCoordinator.kt`.
- `cd014fc96419613cac1b52e8cb5272dff99241eb7e7a6caa055ba62243154f52` --
  `AndroidAiTextProviderModelListing.kt`.
- `28204fee38366966442beee3b4b1d6f362c19f5d7785240bd2b33b354e63394f` --
  `AiTextProviderModelListingCoordinatorTest.kt`.
- `d344e235e884e66894b2d1a3b1a202d6b3cbf465109de98def62889d9c276750` --
  `AiTextProviderModelListingAndroidConformanceTest.kt`.

After an exclusive-slot preflight observed 30 continuous seconds with no other
Gradle client, one isolated `--no-daemon --no-parallel --max-workers=2`
invocation ran the focused `testAppDebugUnitTest` class and assembled the host
App Debug, App Debug androidTest, and fake-provider Debug APKs. It exited 0;
the Gradle XML recorded 15 tests with no skipped tests, failures, or errors,
and `VERSION_BUILD`/`BUILD_TIME` remained unchanged. An unrelated Gradle client
started later while this invocation was already running, which produced Kotlin
daemon connection-retry noise; Gradle recovered, and the XML and three APK
artifacts below were all published by the successful invocation.

Two separate exact `Class#method` instrumentation invocations returned
`OK (1 test)`:

| Method | Time | Asserted positive path |
| --- | ---: | --- |
| `AiTextProviderAndroidConformanceTest#exactComponentHandshakeDecodesAndPinsFakeProviderMetadata` | 0.575 s | Existing exact-component metadata handshake decoded and pinned the same-signer on-device fake provider; completion was off the main thread, terminal was unique, and exact package identity was unchanged |
| `AiTextProviderModelListingAndroidConformanceTest#exactComponentModelListingRevalidatesAndCollectsAllFakeModels` | 0.524 s | With `pageSize = 1`, collected generation `fake-models-v1` and model IDs `fake.echo`, `fake.stream`, `fake.reasoning`, and `fake.tools`; the catalog retained the pinned provider, completion was off the main thread, terminal was unique, and exact package identity was unchanged |

The exact commands were:

```text
adb -s QV710AF65F shell am instrument -w -r -e autojs.aiText.conformance.enabled true -e class 'org.autojs.autojs.core.plugin.ai.AiTextProviderAndroidConformanceTest#exactComponentHandshakeDecodesAndPinsFakeProviderMetadata' org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
adb -s QV710AF65F shell am instrument -w -r -e autojs.aiText.modelListing.conformance.enabled true -e class 'org.autojs.autojs.core.plugin.ai.AiTextProviderModelListingAndroidConformanceTest#exactComponentModelListingRevalidatesAndCollectsAllFakeModels' org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

The host, instrumentation, and fake-provider APKs all verified with APK
Signature Scheme v2 and the same test certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Their artifact SHA-256 values were:

- Host: `f43fbdb8ecb8ce37a38df9ffebbe06733de481bbeaa5bec7de07c2e0184337d2`.
- Instrumentation: `6cef35988b5f2497654051aeb872a9236a22eeaba2c5cf92c9da8ca5bea52b5f`.
- Fake provider: `045ba4955b8eaaf68068c62f8740ca236298fa2489b9f37adddb96a4acf06a12`.

The SHA-256 of each installed device `base.apk` matched its corresponding
artifact exactly. Before installation, all three packages --
`org.autojs.autojs6`, `org.autojs.autojs6.test`, and
`org.autojs.plugin.ai.text.fake` -- were absent. The run uninstalled only those
temporary packages, and all three were confirmed absent again afterward.

This earlier model-list run is positive-path PARTIAL evidence only. It covers
no API other than 31 and
does not cover hostile callbacks, negative signer/UID/identity drift, package
update/disable/uninstall during a transcript, timeout or callback/provider/host
death, a real production plugin/model, session opening, PFD ownership, credit
flow, generation, UI/runtime selection, or any `ai.*` integration. The
standalone K2/JUnit 15/15 result remains separate JVM evidence rather than an
additional Android matrix cell.

#### Session Transport Run

The later 2026-08-10 run used the same explicit device,
`QV710AF65F` (API 31, `arm64-v8a`), against the default-off coordinator,
Android `IAiTextCallback`/PFD transport, and opt-in instrumentation in commits
`4f22dc854`, `038728f03`, and `a22ed8b75`. A focused Gradle invocation recorded
18 `AiTextProviderSessionCoordinatorTest` tests with 0 skipped, 0 failures, and
0 errors and assembled the host App Debug, App Debug instrumentation, and
fake-provider Debug APKs.

Two separate exact `Class#method` instrumentation invocations each returned
`OK (1 test)`:

| Method | Asserted positive path |
| --- | --- |
| `AiTextProviderSessionAndroidConformanceTest#exactComponentSessionStreamsPastInitialCreditsFromRequestDescriptor` | Re-handshook and pinned the exact fake provider, sent the prompt through request descriptor index 0 with exact length/SHA-256/EOF, opened `fake.stream`, received `Started`, approximately 18 contiguous chunks (more than the fixed initial 8 credits), one usage event, and one matching completion off the main thread; request ownership was closed after settlement |
| `AiTextProviderSessionAndroidConformanceTest#callbackDescriptorOwnerEnforcesExactLengthProducerErrorsAndIdempotentClose` | In the host instrumentation process, preserved reliable-PFD error state across async ownership transfer, accepted exact bytes, rejected short and trailing data and `closeWithError()` producer failure, balanced four operation leases, and tolerated repeated close |

The invocation shape was:

```text
adb -s QV710AF65F shell am instrument -w -r -e autojs.aiText.session.conformance.enabled true -e class 'org.autojs.autojs.core.plugin.ai.AiTextProviderSessionAndroidConformanceTest#<method>' org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

Before installation, all three packages--`org.autojs.autojs6`,
`org.autojs.autojs6.test`, and `org.autojs.plugin.ai.text.fake`--were absent.
Only those temporary packages were installed for the run, and all three were
confirmed absent again after cleanup.

This baseline result was **PARTIAL**: the stream's normal completion used an
empty callback descriptor array, and the exact/error descriptor method was
process-local. H1 closes only the narrow callback-PFD and hostile cases recorded
next; the other promotion gaps remain open.

#### Hostile Session H1 Run

H1 commits `edd10008f` and `06ebc788c` added hidden fake-provider modes and
opt-in method-level instrumentation. The focused Gradle run recorded 18
coordinator tests and 31 fake-provider unit tests, each with 0 skipped, 0
failures, and 0 errors. It also assembled the host App Debug, App Debug
instrumentation, and final fake-provider Debug APKs.

All three APKs verified with APK Signature Scheme v2 and the same test
certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Their H1 artifact SHA-256 values were:

- Host: `0ec03495cdd096fa812dd64641c07a484bdd2231df777427f55e8dd9a96e2634`.
- Instrumentation: `68e9a08522c03e2daa2575a0a087403372adf0e6eefee452fcce66263ecdaa59`.
- Final fake provider: `8c9c3b3d0eca3e67c41a2c563e975d927e5422018c5a790330c10b164303b521`.

Seven separate exact `Class#method` invocations on `QV710AF65F` (API 31,
`arm64-v8a`) each returned `OK (1 test)`:

| Method | Asserted H1 boundary |
| --- | --- |
| `AiTextProviderSessionAndroidConformanceTest#completionDescriptorIsMaterializedAcrossProcess` | After initial host credit/open commit, materialized one ordinary-pipe completion descriptor across Binder and accepted exact EOF, declared length, SHA-256, UTF-8 text `descriptor completion across Binder`, descriptor count 1, and terminal cleanup |
| `AiTextProviderSessionAndroidConformanceTest#toolDescriptorIsOwnedThenRejectedWithoutExecution` | After `Started`, owned a descriptor-backed tool batch, rejected it as `TOOLS_UNSUPPORTED` without execution, and cleaned up the callback descriptor |
| `AiTextProviderSessionAndroidConformanceTest#chunkBeforeStartFailsClosedAfterInitialCreditCommit` | Emitted a chunk before `Started` only after initial host credit and rejected it once as `CALLBACK_MALFORMED` |
| `AiTextProviderSessionAndroidConformanceTest#sequenceGapFailsClosedOnce` | Rejected a non-contiguous chunk sequence once as `CALLBACK_MALFORMED` |
| `AiTextProviderSessionAndroidConformanceTest#invalidCompletionDescriptorReferenceFailsClosedOnce` | Rejected a completion referring to an absent descriptor once as `CALLBACK_MALFORMED` |
| `AiTextProviderSessionAndroidConformanceTest#duplicateTerminalPublishesOnlyOneSettledOutcome` | Published one settled host outcome while observing late callbacks for a bounded 250 ms smoke window |
| `AiTextProviderSessionAndroidConformanceTest#stalledSessionCanBeCancelledAfterStartAndReleasesOwner` | Accepted host cancel after one `Started`, published `Cancelled`, and released the process owner |

Every exercised H1 fixture waited for initial host credit before its action,
keeping the callbacks after synchronous open commit rather than in the
provisional callback-before-return window. Every method asserted one published
terminal, off-main-thread delivery, stable package identity, rejected cancel
after settlement, and reusable process ownership. Preflight found
`org.autojs.autojs6`, `org.autojs.autojs6.test`, and
`org.autojs.plugin.ai.text.fake` absent; post-cleanup found the same three
packages absent again.

This H1 result remains **PARTIAL**. The completion fixture deliberately used an
ordinary pipe. It proves cross-process callback-PFD ownership, exact
EOF/length/SHA-256/UTF-8 materialization, and cleanup, but it does not prove
cross-process reliable-pipe producer status. Reliable `closeWithError()` /
`checkError()` behavior remains covered only by the process-local descriptor
method. The descriptor-backed tool case proves ownership and fail-closed
cleanup, not tool execution or reliable-status propagation. The duplicate case
is only a 250 ms bounded late-callback smoke test, not an exhaustive race proof.

The H1 run itself included no Android wrong-callback-UID or deterministic
no-credit result; no provider death, update, disable, or
uninstall-during-session result; no host death or other Android API/ABI cell;
and no real production plugin/model/runtime, remote `listingGeneration` proof,
UI/runtime selector, or `ai.*` route. Package
absence before and after this run is cleanup evidence, not uninstall-during-
session evidence.

#### Lifecycle Session H2 Run

H2 is integrated in production transport commit `e5bd92b16`, fake-fixture
commit `10dad3e39`, and host-test commit `0b9a94742`. The production transport
classifies `onServiceDisconnected` as binder death after Android has delivered
the exact service connection, while preserving the typed `DISCONNECTED` result
for a disconnect before that callback. The existing coordinator/policy
single-terminal gate arbitrates duplicate death-recipient,
`onBindingDied`, and service-disconnect notifications.

The H2 fixture adds two hidden exact scenarios. The wrong-UID path exact-binds
an unexported `isolatedProcess` service in the conformance APK, which invokes a
valid `Started` callback under its distinct isolated UID without using
`startService` or another APK. The process-death path first waits for initial
host credit, emits `Started`, reserves one credit for sequence 0 and emits text
`hostile`, then waits until a host `grantCredits` restores the captured initial
window before calling `Process.killProcess`. That restored window is the causal
acknowledgement that the host handled the chunk; it is not a sleep-based race.

The 2026-08-11 focused H2 Gradle XML recorded:

- `AiTextProviderSessionCoordinatorTest`: 18 tests, 0 skipped, 0 failures,
  0 errors.
- Conformance fake-provider unit corpus: 31 tests, 0 skipped, 0 failures,
  0 errors.
- Successful host App Debug, App Debug instrumentation, and fake-provider Debug
  APK assembly.

All three APKs verified with APK Signature Scheme v2 and the same test
certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Their H2 local artifact SHA-256 values were:

- Host arm64-v8a:
  `0685ce99c0f9e8f9056be5f3a8eebc2c7ea5ffca2d422e21eac69d0cb3364629`.
- Instrumentation:
  `7c91a0af651e2098ad124ff8a89ae3ac3018e0f1d0dec068367595e964739178`.
- Fake provider:
  `6c7319a6682b08cab2e3c610d6db0919c7c71bc034c2cec8b46eb23623d55a18`.

Each installed device `base.apk` SHA-256 matched the corresponding local
artifact. On `QV710AF65F` (API 31, `arm64-v8a`), two separate exact
`Class#method` invocations returned `OK (1 test)`:

| Method | Time | Asserted H2 boundary |
| --- | ---: | --- |
| `AiTextProviderSessionAndroidConformanceTest#callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner` | 0.852 s | The isolated relay's distinct Binder caller UID was rejected as `CALLBACK_UID_MISMATCH` before any transcript event was published; exactly one terminal settled and the process owner was reusable. |
| `AiTextProviderSessionAndroidConformanceTest#providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner` | 0.544 s | After one `Started`, the sequence-0 `hostile` chunk, and causal host credit replenishment, killing the fake provider process published exactly one `BinderDied` terminal and released the process owner. |

Preflight found `org.autojs.autojs6`, `org.autojs.autojs6.test`, and
`org.autojs.plugin.ai.text.fake` absent. Post-cleanup found the same three
packages absent again.

These are two narrow **PARTIAL** Android results, not a lifecycle exit gate.
The wrong-UID method proves rejection of one real isolated Android Binder UID;
it does not complete signer, permission, package-update, or multi-API hostile
identity coverage. The provider-death method proves one fake-provider death
after an acknowledged callback/credit cycle on one API 31 arm64 device; it does
not cover host death, timeout, repeated crash/rebind, or other APIs/ABIs.

Deterministic no-credit remains existing JVM policy evidence only through
`AiTextProviderSessionTest#ninthChunkBeforeAnyReplenishmentFailsClosedForNoCredit`;
there is no H2 Android no-credit method. Commit `0b9a94742` also exercises two
coordinator JVM final-reinspection failures before `openSession`: changed
`lastUpdateTime` and an empty exact discovery result. Those cases prove
fail-closed pre-dispatch identity reinspection, not an installed-package update
or uninstall during an Android session. Pre/post package absence above is only
cleanup evidence. Provider disable, active-session update/uninstall, host death,
the Android version/ABI matrix, real production plugin/model/runtime, remote
`listingGeneration`, UI/runtime selection, and `ai.*` wiring remain open. No
broad R1 checkbox or protocol exit criterion is closed by H2.

### API 25 Partial Evidence

The 2026-08-08 run used commit `0f67d6eec`, the dedicated
`emulator-5556` AVD (`AVD_API_25`, API 25, x86), and six separate
`Class#method` instrumentation invocations. The fake process was reset between
methods so asynchronous `oneway` session cleanup could not make the next case
observe a stale busy gate. Every invocation returned `OK (1 test)`:

| Method | Fixture | Asserted host outcome |
| --- | --- | --- |
| `normalServicePublishesValidatedDexAndLoadsItThroughDexClassLoader` | Fixed DEX 037 | Published, structurally validated, loaded, and `answer() == 42` |
| `progressBeforeStartServiceFailsClosed` | Progress before `onStarted` | `CALLBACK_VIOLATION`, rollback, no published artifact |
| `duplicateTerminalServiceFailsClosed` | Duplicate terminal callbacks | `CALLBACK_VIOLATION`, rollback, no published artifact |
| `invalidOutputIsRejectedByTheIndexedZipValidator` | Malformed DEX bytes | `OUTPUT_REJECTED`, no published artifact |
| `unnegotiatedDexVersionIsRejectedByTheIndexedZipValidator` | Resealed fixed fixture with a DEX 038 version | `OUTPUT_REJECTED` before publication |
| `stalledServiceTimesOutAndRollsBack` | Open stalled session/pipe | `TIMEOUT`, rollback, no published artifact |

The exact invocation shape was:

```text
adb -s emulator-5556 shell am instrument --user 0 -w -r -e class org.autojs.autojs.core.plugin.dex.DexCompilerAndroidConformanceTest#<method> -e autojs.dexCompiler.conformance.enabled true org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

The host x86 APK, instrumentation APK, and fake APK were built together in an
isolated worktree without `sign.properties`. All three verified with APK
Signature Scheme v2 and the same test certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
The build artifacts and pulled installed copies had the same APK SHA-256
digests:

- Host: `cc2a7cc42f11b11ebebc009806b9bde1448fb6ef0c77a9dc848f1178777321ef`.
- Instrumentation: `ff9405c3b6078b1bc2a597cd750866e2394639552153bfadd70799dff3f0cee3`.
- Fake provider: `08796cf84a11d944f994dec0545ea4d24e1a92b45cada8a997ba1055b259481e`.

The emulator snapshot restored the pre-run host (`versionCode=3923`,
`6.7.1 Alpha4`), removed the temporary instrumentation/fake installs, preserved
the unrelated `org.autojs.autojs6.test_1` package, and was deleted only after
that state was rechecked. No serial-directed command was sent to the
`QV710AF65F` soak device.

This evidence does not cover any API other than 25, including the required API
24/26/28/34/36 matrix cells. It also does not cover a real D8 provider,
structural validator/ART differential corpora, host-request cancellation,
callback/provider/host death, negative signer/UID/identity-drift cases, update,
disable, uninstall, storage exhaustion, performance, or representative Java
and Kotlin source corpora. The DEX 038 case proves only pre-publication version
rejection; it is not a general claim that every validator-accepted DEX will be
accepted by ART.

No runtime gate may use a developer device that is reserved for another soak
or endurance test. An explicit serial and a dedicated conformance run are
required before any install, uninstall, process kill, or connected Gradle task.

Both conformance test-app modules disable their release variants and use only Android's
debug/test signing configuration. Same-signer conformance must build the host
and fixture together in an isolated checkout with the same default or dedicated
test key. Production `sign.properties` and production signing keys are outside
this matrix.

## Promotion Rules

The DEX adapter remains experimental and disconnected from
`AndroidClassLoader` until every applicable Android gate passes. A production
compiler must contain the real D8 implementation; the fixed-output conformance provider can
never be selected for production compilation.

The AI protocol now has one narrow public connection: an exact, explicit,
prompt-only plugin selector on `ai.ask`. Selector absence preserves the built-in
cloud path; selector presence fails closed without vault, HTTP, retry, or
fallback. This source-level integration does not promote the broader protocol.
Promotion still requires the remaining installed-provider/version/hostile
matrix, remote catalog-generation handling, real provider/model and device
evidence, UI selection, plugin-backed chat/stream decisions, and active-request
update/disable/uninstall results. The pure policies and Android adapters remain
fail-closed foundations outside that one composition. Image, audio, and video
require separate protocol reviews and cannot be added to the text plugin to
satisfy this matrix.
