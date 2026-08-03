# DEX Compiler Protocol V1

This document records the frozen V1 contract and the first implementation
boundary. The protocol AAR is a build-time SDK. The fake provider is a
conformance fixture, not a production D8 distribution. The host still keeps
its current local compiler and does not route `runtime.loadJar` through the
plugin by default.

## Modules

| Module | Responsibility |
| --- | --- |
| `plugin-api:protocol-wire-api` | Versioned field-tagged binary envelope |
| `plugin-api:dex-compiler-api` | AIDL, models, codecs, negotiation, and quotas |
| `plugins:dex-compiler-fake` | Deterministic normal and hostile conformance services |
| `app/.../core/plugin/dex` | Experimental host policy, lifecycle, cache key, and output validation |

The API AAR contains no D8, R8, dx, archive implementation, network stack, UI,
or native library. V1 is ABI independent.

## Scope

V1 supports one bounded operation:

- Input: one read-only JAR snapshot.
- Compiler family: D8 only.
- Modes: `DEBUG` and `RELEASE`.
- Output: one write-only DEX indexed ZIP containing contiguous
  `classes.dex`, `classes2.dex`, and later entries.
- Runtime library model: `DEVICE_RUNTIME_BOOTCLASSPATH_V1`.
- Concurrency: one session per provider process.

V1 does not support AAR normalization, R8 shrinking, keep rules, mapping or
retrace data, caller supplied classpaths, desugared-library configuration,
network compilation, loading the result in the provider, or deterministic byte
identity claims.

## Binder Surface

`IDexCompilerProvider` exposes `getCompilerInfo`, `getCapabilities`, and
`openSession`. `openSession` receives one encoded request, one program
descriptor, one output descriptor, and one callback. It returns an
`IDexCompilerSession` with idempotent `cancel` and `close` methods.

The callback lane contains `onStarted`, `onProgress`, and exactly one of
`onCompleted`, `onFailed`, or `onCancelled`. Callback methods are one-way and
must be serialized by the provider. Completion is invalid before start,
progress is invalid before start, progress sequence numbers must increase, and
no event is accepted after a terminal event.

The wire payloads use [Plugin Protocol Wire V1](plugin-protocol-wire-v1.md).
Program and output bytes never enter a Binder byte array.

## Discovery And Trust

The service action is `org.autojs.plugin.DEX_COMPILER`. A candidate must be
enabled, exported, and protected by `org.autojs.permission.PLUGIN`.

Compiler output is later executed with host privileges, so general plugin
authorization is not sufficient. Selection accepts only an official provider
or a provider signed by the same signer as the host. User-granted and generally
trusted third-party providers are not eligible for automatic compiler
selection in either debug or release builds.

Discovery freezes an exact component and its package name, UID, complete
current signer set, version code, and last-update time. The host rechecks that
identity before dispatch. Every callback must also originate from the pinned
UID. A changed package is rediscovered as a new candidate rather than silently
continuing an old session.

## Descriptor Ownership

The host snapshots mutable or non-seekable input into host-private storage and
computes its size and SHA-256 before dispatch. The provider duplicates both
descriptors before asynchronous use and closes the received arguments. Only
the provider reads the program descriptor and only one provider worker writes
the output descriptor.

The output points at a host-owned staging transaction. A successful callback
does not publish it. The provider must close its producer before the callback;
the host reads the staging artifact, verifies reported size and SHA-256,
validates the DEX indexed ZIP, and only then allows cache publication. Failure,
timeout, cancellation, Binder death, identity drift, or a late callback
observed before the publication commit point rolls the transaction back.

The disconnected Android artifact store creates a reliable pipe and starts a
dedicated bounded drain before dispatch. The drain consumes at most the
negotiated byte ceiling, reports peer errors after EOF, and writes only into a
host-private staging inode. Provider completion metadata and the independently
drained size and SHA-256 must match before publication can be claimed. Drain
failure, timeout, cancellation, or a summary mismatch claims rollback instead.

Asynchronous abort ingress and the publication terminal claim share one commit
lock. Cancellation, timeout, Binder death, binding failure, or a transport or
callback violation registered before that commit point prevents publication,
even when validation is occupying the serialized worker lane. A signal that is
registered after the publication terminal CAS is late and cannot create a
published-artifact/local-fallback split brain.

The host never renames the drained staging inode into the cache. It copies the
already validated bytes into a second host-only file, verifies that copy,
fsyncs it, and atomically renames only that file. The bounded transfer and
dual-terminal publication state machine have pure JVM coverage, but the pipe
adapter still requires API 24-36 Binder, peer-error, cancellation, storage
exhaustion, and process-death evidence before default or third-party selection.
The current structural validator also materializes output under a conservative
16 MiB host limit; removing that heap ceiling requires a separately tested
random-access or streaming validator.

## Negotiation And Cache Identity

The host selects the highest overlapping protocol version and intersects the
request with provider capability limits. The runtime-library fingerprint is a
domain-separated SHA-256 over the ordered library identities. Order is part of
the identity.

The semantic cache key includes all inputs that may change executable output:

- Input content SHA-256 and canonicalization policy.
- Protocol version, compiler family and version.
- Minimum API, debug or release mode, and output format.
- Runtime-library model and ordered fingerprint.
- Every semantic compiler option.
- Provider package, signer, version code, and last-update time.

Provider output is never reused under a different provenance identity.

## Resource Ceilings

The protocol ceiling includes 128 MiB compressed input, 20,000 archive
entries, 256 MiB uncompressed input, 128 MiB total class bytes, 8 MiB per class,
256 MiB output, 64 DEX entries, 64 KiB diagnostics, and a 300 second maximum
deadline. The effective value is always the smallest host, provider, request,
and protocol limit.

The experimental host currently rejects requests above a separate 16 MiB
in-memory output ceiling before discovery. Its validator still materializes
the container, inflated DEX entries, and verified publication copy, so the
larger protocol ceiling is not a safe Android heap target. Streaming validation
and publication, or device-qualified heap-specific limits, remain a promotion
gate before this ceiling can be raised.

The fake provider does not make these large values a test allocation target.
Conformance tests use small fixtures and separately verify rejection at the
boundaries. All normal and hostile service components share one process-wide
session gate, matching the advertised single concurrent session even when a
test binds different components explicitly.

## Host Output Validation

Before cache publication, the host validates:

- ZIP framing, flags, methods, CRCs, data descriptors, bounds, and compression
  ratios without trusting `ZipInputStream` alone.
- Exactly the contiguous `classes*.dex` entries and no other entry.
- DEX magic and negotiated version, file and header size, endian tag, Adler-32,
  and SHA-1 signature.
- Fixed-width section bounds, alignment, ordering, map-list consistency, and
  referenced class descriptors.
- Duplicate class definitions across DEX entries.

This is structural validation. It does not replace Android's instruction,
control-flow, encoded-value, annotation, or verifier checks. Loading the
artifact remains a separate trusted-host operation.

The current validator supports fixed 0x70-byte headers for DEX 035, 037, 038,
039, and 040. DEX 041 is rejected until its container/header rules receive a
separate implementation and corpus.

## Failure And Degradation

The experimental adapter performs at most one plugin dispatch and never
retries Binder work after dispatch. It also rejects invocation unless the
caller supplies an explicit selected component; V1 performs no automatic
provider selection. Before the local compiler is removed, an eligible failure
may claim one local fallback after rolling back plugin output. Cancellation
never changes engines and never triggers fallback.

The adapter is not connected to `AndroidClassLoader` in this phase. Therefore:

- Missing, disabled, incompatible, or crashing DEX plugins do not change
  current host behavior.
- Existing local D8 and legacy dx gates remain intact.
- The fake provider cannot cause production scripts to execute its output.

Default selection requires device/API matrix evidence, real
`DexClassLoader` execution, Java and Kotlin corpus parity, cancellation and
process-death evidence, and measured compatibility with the current local
path. Removing host compiler dependencies is a later explicit decision.
