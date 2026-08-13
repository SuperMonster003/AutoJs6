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
| AI host model list and plugin selection | Default-off exact-component coordination, same-Binder metadata pinning, per-dispatch and callback reinspection, UID/size-before-copy ingress, bounded flood admission, per-provider token isolation, single-flight/fuse/deadline ownership, terminal cleanup, and explicit persistent model selection | Standalone K2/JVM 17 compilation plus JUnit model-list coordinator corpus, 15/15 tests; D3#3 plugin commits `6489a59` / `af0c5fe` and host exact-test commit `b695c303`; D3#4 plugin production/tested-source commits `98d438e` / `a6bc0e3` and host exact-test commit `6495079ea` | PASS, JVM; PARTIAL, one API 35/arm64-v8a real-plugin listing-only Binder method plus one API 31/arm64-v8a real-plugin manager-selection/Binder method |
| AI host session transcript | Default-off plan/request admission, provisional callbacks around synchronous open, UID/size-before-retention ingress, bounded credits and callback buffering, descriptor ownership, two-stage terminal and late-remote cleanup, cancel/timeout/death races, executor failure, fatal drain, and summary-only output | Commit `325577c82`; standalone Kotlin K2/JVM 40/40, 30 repeated rounds 1200/1200; focused `:app:testAppDebugUnitTest` XML: 40 tests, 0 skipped, 0 failures, 0 errors | PASS, JVM/host unit only |
| AI host session coordinator | Default-off exact-component coordination, four exact inspections, same-Binder bounded metadata revalidation and open, callback-before-return commit, absolute deadline, operation leases, component fuse, gate/terminal settlement, connected-disconnect death classification, and no retry after dispatch | Commits `4f22dc854`, `038728f03`, H1 instrumentation `06ebc788c`, and H2 production/test commits `e5bd92b16` / `0b9a94742`; focused `AiTextProviderSessionCoordinatorTest`: 18 tests, 0 skipped, 0 failures, 0 errors | PARTIAL, JVM/host unit and Android build plus one API 31 normal, seven H1, and two H2 method runs |
| AI explicit ask/chat/stream routes | Present-key fail-closed routing, exact component/provider/model pins, strict prompt-only mapping, no vault/HTTP/fallback, process-shared admission, per-script lifecycle ownership, normalized unary/chat/stream results, and stable terminal codes | Earlier ask/stream focused Gradle 25/25; chat tested-source commits `d480b6918` / `b0aa67684` focused Gradle 14/14 (3 mapper + 4 runtime + 7 service), 0 skipped/failures/errors; one real-provider ask plus fake-provider stream and chat public methods on API 31 | PARTIAL, JVM/source plus three narrow positive-path device smokes |
| AI conformance app | Echo, stream, reasoning, tools, usage, pagination, hostile hidden models including isolated callback UID and provider process death, bounded executors, FD cleanup, and caller authentication | H1 fixture commit `edd10008f` and H2 fixture commit `10dad3e39`; conformance text provider unit corpus: 31 tests, 0 skipped, 0 failures, 0 errors | PASS, JVM; Android hostile results remain PARTIAL below |
| AI conformance app | Android service source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |

### Explicit `ai.ask`, `ai.chat`, And `ai.stream` Plugin Route Evidence

The production `AiService` chooses the plugin path for `ai.ask`, `ai.chat`, and
`ai.stream` only when the request options contain the `plugin` key. A missing
key leaves legacy cloud ask/chat/stream behavior unchanged. A present key,
including a null or malformed value, never consults the vault or HTTP path and
never falls back. Admission requires one exact component package/class,
provider ID, model ID, and an optional integer timeout from 1,000 through
600,000 milliseconds; the default is 120,000. Cloud control aliases, an
explicit `stream` option, and unknown fields fail closed.

The shared request mapping admits exactly one plain-text user message and sends
one inline request with zero request descriptors and no reasoning, structured
output, usage, tools, or tool rounds. `ai.ask` and `ai.chat` are non-streaming;
chat completion maps to exactly 12 public keys: text, empty reasoning, empty
toolCalls, null usage/finishReason/message/error/raw/profile, route `plugin`,
and exact provider/model pins. `ai.stream` requires the planned model's
`STREAMING` capability and maps `Started` to `open`, every nonempty text chunk
to `chunk` then `delta` in one dispatcher action, and the aggregate completion
to `done`. The composed Android runner
reuses the exact-component discovery, handshake, model-list, and session
coordinators, requires an `ON_DEVICE`/`NONE` provider and the exact planned
model, and applies one outer absolute deadline from discovery through the active
session terminal. Lower handles and coordinators retain settled cleanup
ownership. One process-shared backend and admission gate serialize ask, chat,
and stream attempts over the same orchestration/control/terminal/deadline lanes,
bounded three-operation blocking pool, and coordinator process owners. Each
script runtime owns cancellation and handle close; terminal and late-callback
races have one visible winner and no post-dispatch retry.

The earlier standalone K2/JUnit ask gate passed 15/15 tests: 8 option-parser,
3 mapper, and 4 runtime lifecycle cases. After production commit `2ea3360a2`
and test commit `50b43d00c`, the focused Gradle XML recorded 25/25 tests across
`AiPluginAskInputMapperTest` (3), `AiPluginAskRuntimeTest` (4),
`AiPluginStreamRuntimeTest` (4), `AiServicePluginRouteTest` (5), and
`AiStreamTest` (9), with 0 skipped, 0 failures, and 0 errors. The production
plugin separately retained its completed `testDebugUnitTest`, `lintDebug`,
`assembleDebug`, and `assembleRelease` evidence.

The chat change's exact feature/test commits were `d480b6918` / `b0aa67684`.
Focused Gradle XML at tested source
`b0aa6768484d6046551264f69ecc84b527bbb442` recorded 14/14 tests across
`AiPluginAskInputMapperTest` (3), `AiPluginAskRuntimeTest` (4), and
`AiServicePluginRouteTest` (7), with 0 skipped, 0 failures, and 0 errors. Main
integrates those scoped changes as production commit `2d256b99f` and test commit
`c35f199b8`; the integration commits are traceability references and were not
the APK build/test source.

The 2026-08-11 production positive-path smoke ran the exact method
`AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin`
on `QV710AF65F` (API 31, arm64-v8a). It traversed the real Rhino
`ScriptEngineService`, public global `ai.ask` with an explicit selector, the
release plugin service/provider, and a real 2,583,085,056-byte model whose
SHA-256 was
`ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`
and imported ID was
`litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. The public Promise settled with a
nonempty result; the complete instrumentation method took 7.071 seconds and
reported `OK (1 test)` with `INSTRUMENTATION_CODE: -1`.

The host App Debug, instrumentation, and release plugin v32 APK SHA-256 values
were, respectively,
`b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`,
`09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf`,
and `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`.
All three used APK Signature Scheme v2 with certificate SHA-256
`31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`,
and their installed `base.apk` files matched the local candidates byte for
byte. Exact pre/post checks for users 0 and 10 found the host, instrumentation,
and plugin packages absent, and the three transaction-reserved external paths
were absent at both boundaries. The detailed receipt remains local only.

This one method is not a release or matrix gate. It covers one physical device,
one real model, one non-streaming one-shot ask, and an App Debug host. That ask
method does not cover a release host, other APIs/ABIs, plugin-backed chat or
stream, tools, structured output, usage, clipboard behavior, active-request
update/uninstall, performance, or soak. DocumentsUI import can leave
last-location UI state whose exact prior value cannot be snapshotted and
restored losslessly. Earlier fake-
provider Android cells remain separate evidence, remote catalog-generation and
automatic UI selection remain open, and no broad promotion criterion is closed.

#### Reproducible Local Public-Route Entry

After the exact host, instrumentation, and plugin artifacts are installed and
the exact model above has been imported, the durable method-level entry is the
direct instrumentation command below. It deliberately names the device and all
four opt-in arguments; a temporary transaction script is not a protocol or
conformance entry point.

```powershell
adb -s QV710AF65F shell am instrument -w -r -e autojs.aiText.publicAskSmoke.enabled true -e autojs.aiText.publicAskSmoke.serial QV710AF65F -e autojs.aiText.publicAskSmoke.modelId litertlm.ab7838cdfc8f77e54d8ca45eadceb204 -e autojs.aiText.publicAskSmoke.timeoutMillis 600000 -e class 'org.autojs.autojs.core.plugin.ai.AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin' org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

Future runs must retain the following fixed record field names so that a result
can be compared without depending on a local script or prose-only receipt. The
values below are the 2026-08-11 record; a rerun replaces values, not field names.
The built and device-tested APKs came from `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`.
Commit `7ce26ceea3f5458e347cb46078005885d1e8f807` integrates the same test content
onto the newer parent `9bbf7ffc2`. The app-tree diff between those commits is
empty, but the integration commit was not itself the artifact build source.

```text
evidence_schema=ai-public-ask-smoke-v1
tested_source_commit=ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd
integration_commit=7ce26ceea3f5458e347cb46078005885d1e8f807
tested_and_integration_app_tree_byte_identical=true
test_class_method=org.autojs.autojs.core.plugin.ai.AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin
device_serial=QV710AF65F
device_api=31
device_abi=arm64-v8a
host_variant=appDebug
host_apk_sha256=b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561
android_test_apk_sha256=09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf
plugin_version_code=32
plugin_apk_sha256=ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb
apk_v2_signer_sha256=31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213
installed_base_apk_matches_candidates=true
model_id=litertlm.ab7838cdfc8f77e54d8ca45eadceb204
model_bytes=2583085056
model_sha256=ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42
instrumentation_time_seconds=7.071
instrumentation_summary=OK (1 test)
instrumentation_code=-1
users_0_10_packages_pre=all-three-absent
users_0_10_packages_post=all-three-absent
reserved_external_paths_pre=all-three-absent
reserved_external_paths_post=all-three-absent
documentsui_last_location_restore=not-lossless
receipt_scope=local-only
```

#### Public Fake-Provider Stream Route

On 2026-08-11, the exact opt-in method
`AiTextPluginPublicStreamSmokeTest#publicRhinoAiStreamCompletesThroughExactFakeProvider`
returned `OK (1 test)` in 1.645 seconds with instrumentation code `-1` on
`QV710AF65F` (API 31, arm64-v8a, user 0). Source HEAD was
`50b43d00cc634f8a220856bf68f936fc800442a4`. The method used the real
`ScriptEngineService` and public Rhino `ai.stream` with the exact test-only
package `org.autojs.plugin.ai.text.fake`, service class
`org.autojs.plugin.ai.text.fake.service.FakeAiTextProviderService`, provider
`fake.local`, and model `fake.stream`.

The UTF-8 prompt produced one `open` and more than eight `chunk` events, crossing
the initial credit window, plus the same number of normalized `delta` events
and one `done`. Both
aggregates and `done.text` exactly equalled the prompt; the route/provider/model
metadata matched the selector, and usage, tool-call, error, and cancellation
counts were zero. The final public stream state was `done` with `isDone == true`.

The candidate SHA-256 values were:

- Host arm64 App Debug:
  `10782233667a77c2df5c02e1299dd0d6d4194cc188d4f586f8d23257dc5cc951`.
- App Debug instrumentation:
  `6025b35860be52b0477013327f7c458e12ce5751f568dfacf1ab299ae074d309`.
- Test-only fake provider:
  `6c7319a6682b08cab2e3c610d6db0919c7c71bc034c2cec8b46eb23623d55a18`.

Exact final checks found the host, instrumentation, and fake-provider packages
absent for users 0 and 10 and all nine reserved external data/media/obb paths
absent.

This is one **PARTIAL** public-route conformance cell, not production-model
stream acceptance. The stream completed naturally. It did not exercise public
`stream.cancel()`, force-stop the engine, or run a real production provider or
model. Exactly-once public cancellation, the deterministic cancel/completion
race, silent runtime close, synchronous terminal-before-handle behavior, and
late-event suppression remain JVM evidence in `AiStreamTest` and
`AiPluginStreamRuntimeTest`. Public active cancellation, engine-stop
propagation on Android, a release host, and the API/ABI matrix remain open. This
result closes no broad R1 checkbox or protocol exit criterion.

#### Public Fake-Provider Chat Route

On 2026-08-11, the exact opt-in method
`AiTextPluginPublicChatSmokeTest#publicRhinoAiChatReturnsNormalizedResponseThroughExactFakeProvider`
reported `OK (1 test)`, `Time: 1.54`, and instrumentation code `-1` on
`QV710AF65F` (API 31, arm64-v8a, user 0). The APK-tested source commit was
`b0aa6768484d6046551264f69ecc84b527bbb442`, which contains feature commit
`d480b6918`. The matching production/test changes were later integrated to main
as `2d256b99f` / `c35f199b8`; those integration commits were not the artifact
source.

The method traversed the real `ScriptEngineService` and public Rhino `ai.chat`
with the exact test-only package `org.autojs.plugin.ai.text.fake`, service class
`org.autojs.plugin.ai.text.fake.service.FakeAiTextProviderService`, provider
`fake.local`, and model `fake.echo`. One naturally completed Promise terminal
returned the exact 12-key set `text`, `reasoning`, `toolCalls`, `usage`,
`finishReason`, `message`, `error`, `raw`, `profile`, `route`, `provider`, and
`model`. Text exactly equalled the UTF-8 prompt, reasoning was empty, toolCalls
was an empty array, usage/finishReason/message/error/raw/profile were null, and
route/provider/model exactly equalled `plugin`, `fake.local`, and `fake.echo`.

The candidate SHA-256 values were:

- Host arm64 App Debug:
  `2d3dfb9c16ad91ccb73c6a969db7dcc9b10046df14f716c82d9e61f07faaf1d3`.
- App Debug instrumentation:
  `9caa1cc6936d98c380c9c9ffe81178a0e81e667207da9623fa95c19bf3a11166`.
- Test-only fake provider:
  `6c7319a6682b08cab2e3c610d6db0919c7c71bc034c2cec8b46eb23623d55a18`.

Exact pre/post checks found the host, instrumentation, and fake-provider
packages absent for users 0 and 10 and all nine reserved external
data/media/obb paths absent.

This is one **PARTIAL** fake-provider public-route cell. Natural completion does
not prove a real provider/model, a release host, another API/ABI, any device-side
failure or Binder-death path, or forced engine/runtime cancellation. Failure
mapping and silent runtime-close coverage remain JVM-only. This result promotes
no broad R1 checkbox or protocol exit criterion.

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
| AI exact-component metadata, model-list, selection, and session positive path | Dedicated debug conformance install with explicit serial, signer/hash preflight, per-method result, and reversible cleanup | PARTIAL: API 31/arm64-v8a fake-provider metadata, paged model list, one normal `fake.stream` session, ordinary-pipe completion materialization, and real-plugin explicit A-to-B manager selection with restart persistence; API 35/arm64-v8a real-plugin three-entry paged listing only |
| AI public production `ai.ask` positive path | Real signed provider release, real model, public Rhino entry, exact component/provider/model selector, artifact/device hash match, reversible cleanup | PARTIAL, one API 31/arm64-v8a App Debug non-streaming one-shot method; no release-host or API matrix |
| AI public conformance `ai.stream` positive path | Public Rhino entry, exact test-only fake component/provider/model selector, normalized chunk/delta/done aggregation beyond eight initial credits, and reversible cleanup | PARTIAL, one naturally completed API 31/arm64-v8a App Debug method; public active cancel/engine stop remain JVM-only and no real provider/model stream ran |
| AI public conformance `ai.chat` positive path | Public Rhino entry, exact test-only fake component/provider/model selector, exact normalized 12-key response, one terminal, and reversible cleanup | PARTIAL, one naturally completed API 31/arm64-v8a App Debug method; no real provider/model, device failure/cancel path, release host, or matrix |
| AI conformance provider normal and hostile Binder corpus | API 24, 28, 34, 36 | PARTIAL, API 31 only: seven H1 methods plus two H2 methods cover completion/tool descriptors, ordering, sequence, descriptor reference, bounded duplicate terminal, cancel-after-start, one isolated wrong UID, and one provider death; deterministic Android no-credit and update/disable/uninstall lifecycle remain pending |
| AI streaming backpressure and tool turns under provider/host death | API 24, 28, 34, 36 | PARTIAL, API 31 normal session and public Rhino fake stream crossed 8 initial credits, a tool callback was rejected without execution, and one provider kill followed a chunk whose credit was causally replenished; real tool turns, host death, and the matrix remain pending |
| Provider-native response compatibility | Each proposed migrated provider | PENDING, no V1 claim |

### API 35 AI D3#3 Exact Real-Plugin Model-List Evidence

The 2026-08-13 D3#3 run used production plugin commit `6489a59`, focused
plugin-test commit `af0c5fe`, and host exact-test commit `b695c303`. The target
was Xiaomi 23046RP50C with both ADB transport serial and `ro.serialno`
`968e9f18`, API 35, and `arm64-v8a`.

The host App Debug, App Debug instrumentation, and real-plugin Debug APKs all
verified with APK Signature Scheme v2 and the same certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Their artifact and installed `base.apk` SHA-256 values matched exactly:

- Host: `69149b0b2400e8d2069bffc9e495b49f4600c7b2a0a29d26c1c015ff4517f638`.
- Instrumentation: `b9183783eaecd240b3ef517e2c0d4ff3356e654e25d7994a87cc2d2da1d7c0a3`.
- Real plugin: `fffb8c9b02f247a48675892bd3f43c8cca757eac68f4c375ba03b7c68dbbbb94`.

The outer transaction provisioned three eight-byte files solely as catalog
rows. Each file intentionally lacked valid LiteRT-LM content and could not be
used as a model. With `pageSize = 1`, the exact Binder list returned the frozen
B/A/C order:

| Fixture | Expected model ID |
| --- | --- |
| B | `litertlm.808c1f662212f7efb70b16956fb784a7` |
| A | `litertlm.85515105bf0e6b25b0ed8ce1cc3801ab` |
| C | `litertlm.c78909e014ed1db7552f0993df44a860` |

All pages admitted the expected generation
`litertlm-catalog-v2-43e043444d5ca606e2c33805c0d5f656`. The exact method
`AiTextProviderModelListingAndroidConformanceTest#exactRealPluginListsExternallyProvisionedThreeModelCatalogAcrossStablePages`
returned `OK (1 test)` in 0.691 seconds. It used the real exact-component
handshake and model-list transports, completed off the Android main thread,
published one terminal outcome, and retained the initial package identity at
the final inspection.

Preflight and post-cleanup both found zero installed-user rows for the three
exact packages--`org.autojs.autojs6`, `org.autojs.autojs6.test`, and
`io.github.supermonster003.autojs6.plugin.ai.text`--and all nine reserved
external data/media/obb paths absent. The scoped cleanup receipt reported zero
residuals.

This is one narrow **PARTIAL** real-plugin Binder-list cell. The fixture is
listing-only: the run performed no LiteRT-LM inference, never called
`openSession`, and exercised no real model. It was not a soak and supplies no
Android API/ABI matrix. Pre/post absence is scoped rollback evidence, not
package update/uninstall-during-transcript evidence; catalog migration,
management UI, sessions, and native model compatibility remain outside D3#3.

### API 31 AI D3#4 Exact Real-Plugin Model-Selection Evidence

The 2026-08-14 D3#4 run used plugin production commit `98d438e`, plugin tested
source `a6bc0e3`, and host source/exact-test commit `6495079ea`. The plugin gate
recorded 57/57 tests with no failures, errors, or skips, followed by passing
`lintDebug`, Debug APK, and Release APK builds. The host build completed its
622-task gate. The target was `QV710AF65F`, API 31, `arm64-v8a`.

The host App Debug, App Debug instrumentation, and real-plugin Debug APKs all
verified with APK Signature Scheme v2 and certificate SHA-256
`2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`.
Their artifact and installed `base.apk` SHA-256 values matched exactly:

- Host: `c9c04ddb272172632dba2989d57d112f0d39345b815f6c90f646b7f5f1f71c8a`.
- Instrumentation: `1470aea7c6d6c0a992dbf084f626ca263bee993adb5223a65f547b0f74c1d476`.
- Real plugin: `644ce44a08d9579acb6a4749c4f5d6bf557662c741ef32e927d05a5edf17c7f0`.

The outer transaction provisioned two eight-byte catalog-only fixtures. Model
A was initially selected as
`litertlm.85515105bf0e6b25b0ed8ce1cc3801ab`; the production manager Activity
exposed both rows and accepted an accessibility click selecting model B,
`litertlm.808c1f662212f7efb70b16956fb784a7`. After force-stopping and
restarting only the plugin package, the Activity still showed B selected and A
unselected. Exact Binder listing before and after the switch published the same
A/B model projection and generation
`litertlm-catalog-v2-08e6ec0aff29d234cbdf6acb07be8916`; both model IDs remained
individually resolvable through `planExact()`, and the exact package identity
remained unchanged.

The durable method-level entry is:

```powershell
adb -s QV710AF65F shell am instrument -w -r -e autojs.aiText.realModelSelection.conformance.enabled true -e autojs.aiText.realModelSelection.conformance.serial QV710AF65F -e autojs.aiText.realModelSelection.conformance.initialModelId litertlm.85515105bf0e6b25b0ed8ce1cc3801ab -e autojs.aiText.realModelSelection.conformance.targetModelId litertlm.808c1f662212f7efb70b16956fb784a7 -e autojs.aiText.realModelSelection.conformance.listingGeneration litertlm-catalog-v2-08e6ec0aff29d234cbdf6acb07be8916 -e class 'org.autojs.autojs.core.plugin.ai.AiTextProviderModelListingAndroidConformanceTest#exactRealPluginSelectionPersistsAcrossManagerProcessRestartWithoutChangingBinderCatalog' org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

The final transaction strictly admitted exact `OK (1 test)`, instrumentation
code `-1`, status `0`, and one executed test, then emitted
`MODEL_SELECTION_TEST_PASS`. Its compact stdout retained the pass markers but
not a runner duration. An immediately preceding invocation of the same method
and tested artifacts recorded `Time: 1.99` and `OK (1 test)` with code `-1`;
that time is supporting context, not the final transaction's measured time.

Preflight and post-cleanup both found the three exact packages--
`org.autojs.autojs6`, `org.autojs.autojs6.test`, and
`io.github.supermonster003.autojs6.plugin.ai.text`--absent for users 0 and 10,
and all nine reserved external data/media/obb paths absent.

This is one narrow **PARTIAL** manager-selection and Binder-list cell. Both
eight-byte files intentionally lacked valid LiteRT-LM content. The method did
not invoke native model loading or inference, call `openSession`, run a
generation, or keep an old active session across the selection. It therefore
does not prove old-session survival or active-generation isolation. It was not
a soak, API/ABI matrix, performance gate, or package update/uninstall test, and
it makes no real-model compatibility claim.

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
the Android version/ABI matrix, broader production plugin/model/runtime matrix,
remote `listingGeneration`, UI/runtime selection, and broader `ai.*` wiring
remain open. No broad R1 checkbox or protocol exit criterion is closed by H2.

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

The AI protocol now has three narrow public connections: exact, explicit,
prompt-only plugin selectors on `ai.ask`, `ai.chat`, and `ai.stream`. Selector
absence preserves the built-in cloud path; selector presence fails closed
without vault, HTTP, retry, or fallback. One real release provider/model
completed the ask route on one API 31 device with an App Debug host. Separate
public stream and chat methods naturally completed with the test-only fake
provider, proving a stream beyond eight credits and the normalized 12-key chat
shape. None of these smokes promotes the broader protocol. In particular, they
do not turn JVM failure, public-stream-cancel, or runtime-close coverage into
Android evidence. Promotion still requires the remaining installed-provider/
version/hostile and API matrices, remote catalog-generation handling,
release-host evidence, automatic UI-selection decisions, multi-message and
provider-native chat compatibility, a real provider/model public chat and
stream, active public cancellation, and active-request update/disable/uninstall
results. The pure policies and Android adapters remain fail-closed foundations
outside those three compositions. Image, audio, and video require separate
protocol reviews and cannot be added to the text plugin to satisfy this matrix.
