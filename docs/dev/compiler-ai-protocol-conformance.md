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
| AI conformance provider normal and hostile Binder corpus | API 24, 28, 34, 36 | PENDING |
| AI streaming backpressure and tool turns under provider/host death | API 24, 28, 34, 36 | PENDING |
| Provider-native response compatibility | Each proposed migrated provider | PENDING, no V1 claim |

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

The AI protocol remains disconnected from the public `ai` module until an
Android discovery and Binder adapter, explicit user selection, and the
installed-provider matrix are implemented. The pure host policy and credential
boundary are default-off foundations, not production activation. Image, audio,
and video require separate protocol reviews and cannot be added to the text
plugin to satisfy this matrix.
