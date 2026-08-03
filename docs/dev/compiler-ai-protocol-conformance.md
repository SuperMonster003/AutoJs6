# Compiler And AI Protocol Conformance

This matrix separates checks that are complete in the repository from checks
that require an installed provider, multiple Android API levels, or a real
production engine. A static pass is not a device-runtime claim.

## Current Repository Gates

| Area | Gate | Evidence | Status |
| --- | --- | --- | --- |
| Shared wire | Canonical order, positive wire types, strict UTF-8 and scalar shapes, limits, optional skip, required reject | `TaggedWireTest`, 14 tests | PASS, JVM |
| DEX API | Codec round trips, golden bytes, minor evolution, incompatible major, malformed and quota cases | DEX API unit corpus, 24 tests | PASS, JVM |
| DEX host | Exactly-once callback lifecycle and cancellation rules | `DexCompilerSessionLifecycleTest` | PASS, JVM |
| DEX host | At most one plugin dispatch and one local fallback, with no cancellation fallback | `DexCompilerFallbackControllerTest` | PASS, JVM |
| DEX host | Semantic cache identity includes policy, compiler, runtime libraries, options, and provider provenance | `DexCompilerSemanticCacheKeyTest` | PASS, JVM |
| DEX host | ZIP and DEX structural validation, including cross-DEX duplicate classes | `DexIndexedZipValidatorTest` plus a D8 8.13.19 DEX 037 fixture | PASS, JVM |
| DEX host | Exact component/UID/full-signer policy, identity drift, bounded ingress, reliable-pipe transfer, dual summaries, abort/publication commit arbitration, no retry, rollback, cleanup, and late callback handling | Experimental adapter corpus, 90 tests total across host policies | PASS, JVM |
| DEX host | Android transport and artifact-store source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |
| DEX fake | Deterministic fixed DEX ZIP, strict bounded JAR framing/hash validation, process-wide single-session gate, explicit hostile services, and single-writer behavior | Fake compiler unit corpus, 24 tests | PASS, JVM |
| DEX fake | Android service source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |
| AI common | Provider, error, usage, payload reference, version negotiation, malformed and evolution corpus | `AiCommonCodecTest` | PASS, JVM |
| AI text | Capabilities, requests, chunks, reasoning, usage, tools, pages, malformed and evolution corpus | `AiTextCodecTest` | PASS, JVM |
| AI lifecycle | Credits, contiguous sequence, single terminal, cancellation, declared tools, verified JSON, aggregate stream digests, tool exactly-once, and cumulative quotas | Lifecycle and quota unit corpus | PASS, JVM |
| AI models | Page token replay, scope, cycle, generation drift, and cross-page duplicate model IDs | `AiModelPageTokenPolicyTest` | PASS, JVM |
| AI protocol | Shared wire, common values, immutable snapshots, redacted summaries, golden wire, text codecs, lifecycle, JSON, quotas, usage, and pagination | Combined protocol/API corpus, 103 tests | PASS, JVM |
| AI host | Default-off trust selection, ambiguity rejection, identity revalidation, capability planning, credential isolation, collision-resistant binding keys, safe diagnostics, and terminal race ownership | Pure host policy corpus, 31 tests | PASS, JVM |
| AI fake | Echo, stream, reasoning, tools, usage, pagination, hostile hidden models, bounded executors, FD cleanup, and caller authentication | Fake text provider unit corpus, 31 tests | PASS, JVM |
| AI fake | Android service source compatibility against generated AIDL and Android 36 | Offline Kotlin/AIDL static compilation | PASS, static only |

## Android Runtime Gates

These gates intentionally remain open. They must not be inferred from a JVM or
APK assembly pass.

| Gate | Required matrix | Current status |
| --- | --- | --- |
| Generated AIDL transact behavior and descriptor ownership | API 24, 25, 26, 28, 34, 36 | PENDING |
| Caller UID, signature permission, same-signer policy, and exact component revalidation | Debug conformance install on isolated devices | PENDING |
| Cancellation, timeout, callback death, provider death, host death, update, disable, and uninstall cleanup | API 24, 28, 34, 36 | PENDING |
| DEX normal and hostile services against the real host adapter | API 24, 25, 26, 28, 34, 36 | PENDING |
| Validated DEX execution through the real `DexClassLoader` | Java and Kotlin corpus on API 24 through 36 | PENDING |
| D8 parity for default/static interface methods, missing libraries, and device boot libraries | Current local compiler versus production plugin | PENDING |
| AAR normalization and resource behavior | Current local path versus production plugin | PENDING |
| Cold/warm latency, host/provider RSS, output size, FDs, and temporary storage | Representative physical devices | PENDING |
| AI fake provider normal and hostile Binder corpus | API 24, 28, 34, 36 | PENDING |
| AI streaming backpressure and tool turns under provider/host death | API 24, 28, 34, 36 | PENDING |
| Provider-native response compatibility | Each proposed migrated provider | PENDING, no V1 claim |

No runtime gate may use a developer device that is reserved for another soak
or endurance test. An explicit serial and a dedicated conformance run are
required before any install, uninstall, process kill, or connected Gradle task.

Both fake APK modules disable their release variants and use only Android's
debug/test signing configuration. Same-signer conformance must build the host
and fixture together in an isolated checkout with the same default or dedicated
test key. Production `sign.properties` and production signing keys are outside
this matrix.

## Promotion Rules

The DEX adapter remains experimental and disconnected from
`AndroidClassLoader` until every applicable Android gate passes. A production
compiler must contain the real D8 implementation; the fixed-output fake can
never be selected for production compilation.

The AI protocol remains disconnected from the public `ai` module until an
Android discovery and Binder adapter, explicit user selection, and the
installed-provider matrix are implemented. The pure host policy and credential
boundary are default-off foundations, not production activation. Image, audio,
and video require separate protocol reviews and cannot be added to the text
plugin to satisfy this matrix.
