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
| AI conformance app | Echo, stream, reasoning, tools, usage, pagination, hostile hidden models, bounded executors, FD cleanup, and caller authentication | Conformance text provider unit corpus, 31 tests | PASS, JVM |
| AI conformance app | Android service source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |

## Android Runtime Gates

These gates intentionally remain open. They must not be inferred from a JVM or
APK assembly pass.

| Gate | Required matrix | Current status |
| --- | --- | --- |
| Generated AIDL transact behavior and descriptor ownership | API 24, 25, 26, 28, 34, 36 | PARTIAL, API 25 only: cross-process generated AIDL calls, descriptor handoff, reliable-pipe EOF, and rollback exercised |
| Caller UID, signature permission, same-signer policy, and exact component revalidation | Debug conformance install on isolated devices | PARTIAL, API 25 positive path only: signature permission, same test signer, callback UID pinning, and explicit-component discovery/reinspection |
| Cancellation, timeout, callback death, provider death, host death, update, disable, and uninstall cleanup | API 24, 28, 34, 36 | PENDING; API 25 stall timeout and rollback are supplemental evidence only |
| DEX normal and hostile services against the real host adapter | API 24, 25, 26, 28, 34, 36 | PARTIAL, API 25 only: normal, progress-before-start, duplicate-terminal, malformed output, unnegotiated DEX version, and stall-timeout services |
| Validated DEX execution through the real `DexClassLoader` | Java and Kotlin corpus on API 24 through 36 | PARTIAL, API 25 fixed DEX 037 fixture only: class load and `answer() == 42` execution |
| D8 parity for default/static interface methods, missing libraries, and device boot libraries | Current local compiler versus production plugin | PENDING |
| AAR normalization and resource behavior | Current local path versus production plugin | PENDING |
| Cold/warm latency, host/provider RSS, output size, FDs, and temporary storage | Representative physical devices | PENDING |
| AI exact-component metadata and model-list positive path | Dedicated debug conformance install with explicit serial, signer/hash preflight, per-method result, and reversible cleanup | PARTIAL, API 31/arm64-v8a positive metadata-handshake and paged model-list paths only |
| AI conformance provider normal and hostile Binder corpus | API 24, 28, 34, 36 | PENDING |
| AI streaming backpressure and tool turns under provider/host death | API 24, 28, 34, 36 | PENDING |
| Provider-native response compatibility | Each proposed migrated provider | PENDING, no V1 claim |

### API 31 AI Partial Evidence

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

This is positive-path PARTIAL evidence only. It covers no API other than 31 and
does not cover hostile callbacks, negative signer/UID/identity drift, package
update/disable/uninstall during a transcript, timeout or callback/provider/host
death, a real production plugin/model, session opening, PFD ownership, credit
flow, generation, UI/runtime selection, or any `ai.*` integration. The
standalone K2/JUnit 15/15 result remains separate JVM evidence rather than an
additional Android matrix cell.

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

The AI protocol remains disconnected from the public `ai` module. Default-off
Android discovery, metadata-handshake, and model-list adapters now exist but
have no production call site. Promotion still requires the installed-provider
matrix, explicit user selection, session/PFD and `IAiTextCallback` transport,
and UI/runtime wiring. The pure host policy and credential boundary are
default-off foundations, not production activation. Image, audio, and video
require separate protocol reviews and cannot be added to the text plugin to
satisfy this matrix.
